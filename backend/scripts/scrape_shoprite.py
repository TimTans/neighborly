"""
multi-store, multi-category shoprite scraper runner.

usage (from the backend/ directory):
    PYTHONPATH=. uv run python scripts/scrape_shoprite.py

    # scrape a single store:
    PYTHONPATH=. uv run python scripts/scrape_shoprite.py --store 218

    # scrape a single category:
    PYTHONPATH=. uv run python scripts/scrape_shoprite.py --category milk

    # write to supabase (default writes to JSON files):
    PYTHONPATH=. uv run python scripts/scrape_shoprite.py --db

output:
    - stdout: summary per store/category
    - file:   data/shoprite_<store_id>_<category>_<timestamp>.json (unless --db)
    - or:     supabase upserts (with --db flag)
"""

import argparse
import asyncio
import json
import logging
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from app.scraper.shoprite import StoreConfig, scrape_store, CloudflareBlockedError, USER_AGENT
from app.scraper.config import STORES, CATEGORIES, build_browse_url, StoreInfo, CategoryConfig
from app.services.nutrition_service import enrich_batch

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

OUTPUT_DIR = Path(__file__).parent.parent / "data"
SESSION_PATH = OUTPUT_DIR / "shoprite_session.json"
LANDING_URL = "https://www.shoprite.com/sm/pickup/rsid/204/categories"


async def refresh_session() -> None:
    """
    open a visible browser so the user can solve the cloudflare challenge.
    saves the new session to SESSION_PATH, then the scraper resumes.
    """
    from playwright.async_api import async_playwright

    print("\n" + "=" * 60)
    print("SESSION EXPIRED - opening browser for Cloudflare verification.")
    print("Please solve the challenge, then the scrape will resume.")
    print("=" * 60 + "\n")

    async with async_playwright() as p:
        browser = await p.chromium.launch(
            headless=False,
            args=["--disable-blink-features=AutomationControlled"],
        )
        context = await browser.new_context(user_agent=USER_AGENT)
        page = await context.new_page()

        await page.goto(LANDING_URL, wait_until="domcontentloaded")

        # wait up to 120 seconds for the user to solve the challenge
        for _ in range(120):
            title = await page.title()
            if "just a moment" not in title.lower():
                break
            await page.wait_for_timeout(1000)
        else:
            print("ERROR: timed out waiting for challenge. Aborting.")
            await browser.close()
            raise RuntimeError("cloudflare challenge timed out")

        await context.storage_state(path=str(SESSION_PATH))
        print(f"\nSession refreshed and saved to: {SESSION_PATH}")
        print("Resuming scrape...\n")
        await browser.close()


async def scrape_category(
    store: StoreInfo,
    category: CategoryConfig,
    write_db: bool = False,
) -> dict:
    """
    scrape one store + category combination.
    returns summary dict with product count and timing.
    """
    config = StoreConfig(
        store_id=store.store_id,
        zip_code=store.zip_code,
        browse_url=build_browse_url(store, category),
    )

    start = time.monotonic()

    # headed mode (headless=False) avoids cloudflare blocking on pagination.
    # to switch to headless, pass headless=True - but cloudflare may block
    # page 2+ requests. see scrape_store() docstring for details.
    products = await scrape_store(
        config,
        session_state=SESSION_PATH if SESSION_PATH.exists() else None,
        headless=False,
    )

    elapsed = time.monotonic() - start

    if write_db:
        from app.scraper.db_writer import (
            ensure_store_exists,
            ensure_category_exists,
            upsert_products,
        )
        store_uuid = await ensure_store_exists(store)
        cat_uuid = await ensure_category_exists(category)
        db_result = await upsert_products(products, store_uuid, cat_uuid)
        logger.info(
            "DB: %d products upserted, %d prices upserted for %s/%s",
            db_result["products_upserted"],
            db_result["prices_upserted"],
            store.name,
            category.name,
        )
    else:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        output_path = OUTPUT_DIR / f"shoprite_{store.store_id}_{category.slug}_{timestamp}.json"
        output_path.write_text(
            json.dumps([p.to_dict() for p in products], indent=2),
            encoding="utf-8",
        )
        logger.info("wrote %d products to %s", len(products), output_path)

    return {
        "store": store.name,
        "category": category.name,
        "products": len(products),
        "duration": round(elapsed, 1),
    }


async def enrich_scraped_products() -> None:
    """find all products with a upc but no nutrition row and enrich from fdc."""
    from app.services.nutrition_service import get_unenriched_pairs

    pairs = await get_unenriched_pairs()
    if not pairs:
        logger.info("no products need enrichment")
        return

    logger.info("enriching %d products from fdc...", len(pairs))
    summary = await enrich_batch(pairs)
    logger.info("enrichment done: %s", summary)


async def main() -> int:
    parser = argparse.ArgumentParser(description="Scrape ShopRite products")
    parser.add_argument("--store", type=str, help="scrape only this store_id (e.g. 218)")
    parser.add_argument("--category", type=str, help="scrape only this category slug (e.g. milk)")
    parser.add_argument("--db", action="store_true", help="write to supabase instead of JSON files")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(exist_ok=True)

    if not SESSION_PATH.exists():
        logger.warning(
            "no session file found at %s - cloudflare may block this request. "
            "run `PYTHONPATH=. uv run python scripts/save_session.py` to create one.",
            SESSION_PATH,
        )

    # filter stores/categories if requested
    stores = STORES
    categories = CATEGORIES

    if args.store:
        stores = [s for s in STORES if s.store_id == args.store]
        if not stores:
            logger.error("unknown store_id: %s", args.store)
            return 1

    if args.category:
        categories = [c for c in CATEGORIES if c.slug == args.category]
        if not categories:
            logger.error("unknown category slug: %s", args.category)
            return 1

    total_start = time.monotonic()

    # run all store x category combinations concurrently, capped by semaphore.
    # each scrape_category() opens its own browser so tasks are independent.
    # semaphore=2 keeps RAM reasonable (~400-800 MB peak with 2 headed browsers).
    semaphore = asyncio.Semaphore(2)
    cloudflare_lock = asyncio.Lock()

    async def bounded_scrape(store: StoreInfo, category: CategoryConfig) -> dict:
        async with semaphore:
            logger.info("scraping %s / %s ...", store.name, category.name)
            try:
                return await scrape_category(store, category, write_db=args.db)
            except CloudflareBlockedError:
                logger.warning("cloudflare blocked %s / %s - refreshing session...", store.name, category.name)
                async with cloudflare_lock:
                    try:
                        await refresh_session()
                    except Exception:
                        pass
                try:
                    return await scrape_category(store, category, write_db=args.db)
                except Exception as retry_err:
                    logger.error("retry FAILED %s / %s: %s", store.name, category.name, retry_err)
                    return {
                        "store": store.name,
                        "category": category.name,
                        "products": 0,
                        "duration": 0,
                        "error": str(retry_err),
                    }
            except Exception as e:
                logger.error("FAILED %s / %s: %s", store.name, category.name, e)
                return {
                    "store": store.name,
                    "category": category.name,
                    "products": 0,
                    "duration": 0,
                    "error": str(e),
                }

    tasks = [
        bounded_scrape(store, category)
        for store in stores
        for category in categories
    ]
    results = list(await asyncio.gather(*tasks))

    total_elapsed = time.monotonic() - total_start

    # print summary
    print(f"\n{'=' * 60}")
    print("ShopRite Multi-Store Scrape Complete")
    print(f"{'=' * 60}")
    total_products = 0
    for r in results:
        status = f"{r['products']} products in {r['duration']}s"
        if "error" in r:
            status = f"FAILED: {r['error']}"
        print(f"  {r['store']:30s} | {r['category']:25s} | {status}")
        total_products += r["products"]
    print(f"{'─' * 60}")
    print(f"  Total: {total_products} products | {total_elapsed:.1f}s")
    print(f"  Output: {'Supabase' if args.db else 'JSON files in data/'}")
    print(f"{'=' * 60}\n")

    if args.db:
        enrich_start = time.monotonic()
        logger.info("starting fdc nutrition enrichment...")
        await enrich_scraped_products()
        enrich_elapsed = time.monotonic() - enrich_start
        print(f"\n  Enrichment: {enrich_elapsed:.1f}s")
        print(f"  Total (scrape + enrich): {time.monotonic() - total_start:.1f}s")

    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
