"""
keyfood cooperative scraper runner - scrapes all configured stores across
all banners (marketplace, key food, superfresh, etc.) in one command.

usage (from the backend/ directory):
    # scrape everything:
    PYTHONPATH=. uv run python scripts/scrape_keyfood.py

    # single banner:
    PYTHONPATH=. uv run python scripts/scrape_keyfood.py --banner keyfood

    # single store:
    PYTHONPATH=. uv run python scripts/scrape_keyfood.py --store 2138

    # single category:
    PYTHONPATH=. uv run python scripts/scrape_keyfood.py --category refrigerated

    # write to supabase:
    PYTHONPATH=. uv run python scripts/scrape_keyfood.py --db

    # headed mode for debugging:
    PYTHONPATH=. uv run python scripts/scrape_keyfood.py --headed

output:
    - file:   data/{chain}_{store_id}_{category}_{timestamp}.json (default)
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

from app.scraper.keyfood import scrape_store
from app.scraper.config import (
    KEYFOOD_BANNERS,
    KEYFOOD_STORES,
    KEYFOOD_CATEGORIES,
    build_keyfood_url,
    get_keyfood_session_path,
    StoreInfo,
    KeyFoodBanner,
    KeyFoodCategoryConfig,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

OUTPUT_DIR = Path(__file__).parent.parent / "data"


async def scrape_category(
    banner: KeyFoodBanner,
    store: StoreInfo,
    category: KeyFoodCategoryConfig,
    session_path: Path,
    write_db: bool = False,
    headless: bool = True,
) -> dict:
    """scrape one store + category combination."""
    category_url = build_keyfood_url(banner, store, category)
    start = time.monotonic()

    products = await scrape_store(
        store_id=store.store_id,
        store_zip=store.zip_code,
        category_url=category_url,
        base_domain=banner.domain,
        category_name=category.name,
        session_state=session_path if session_path.exists() else None,
        headless=headless,
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
        output_path = (
            OUTPUT_DIR
            / f"{banner.chain}_{store.store_id}_{category.slug}_{timestamp}.json"
        )
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


async def main() -> int:
    banner_names = ", ".join(KEYFOOD_BANNERS.keys())
    parser = argparse.ArgumentParser(
        description="Scrape products from KeyFood cooperative stores",
    )
    parser.add_argument(
        "--banner", type=str,
        help=f"scrape only this banner ({banner_names}). default: all with sessions",
    )
    parser.add_argument(
        "--store", type=str,
        help="scrape only this store_id (e.g. 2138)",
    )
    parser.add_argument(
        "--category", type=str,
        help="scrape only this category slug (e.g. refrigerated)",
    )
    parser.add_argument(
        "--db", action="store_true",
        help="write to supabase instead of JSON files",
    )
    parser.add_argument(
        "--headed", action="store_true",
        help="run browser in headed (visible) mode for debugging",
    )
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(exist_ok=True)

    # determine which stores to scrape
    stores = list(KEYFOOD_STORES)

    if args.banner:
        if args.banner not in KEYFOOD_BANNERS:
            logger.error("unknown banner: %s (available: %s)", args.banner, banner_names)
            return 1
        stores = [s for s in stores if s.banner == args.banner]

    if args.store:
        stores = [s for s in stores if s.store_id == args.store]
        if not stores:
            logger.error("unknown store_id: %s", args.store)
            return 1

    # check which banners have sessions
    banners_needed = {s.banner for s in stores}
    missing_sessions = []
    for banner_key in banners_needed:
        session_path = OUTPUT_DIR / get_keyfood_session_path(banner_key)
        if not session_path.exists():
            missing_sessions.append(banner_key)

    if missing_sessions:
        for b in missing_sessions:
            logger.error(
                "no session for '%s' — run: "
                "PYTHONPATH=. uv run python scripts/save_keyfood_session.py --banner %s",
                b, b,
            )
        # still scrape banners that DO have sessions
        stores = [s for s in stores if s.banner not in missing_sessions]
        if not stores:
            return 1

    categories = list(KEYFOOD_CATEGORIES)
    if args.category:
        categories = [c for c in KEYFOOD_CATEGORIES if c.slug == args.category]
        if not categories:
            logger.error("unknown category slug: %s", args.category)
            return 1

    total_start = time.monotonic()
    results = []

    for store in stores:
        banner = KEYFOOD_BANNERS[store.banner]
        session_path = OUTPUT_DIR / get_keyfood_session_path(store.banner)

        for category in categories:
            logger.info("scraping %s / %s ...", store.name, category.name)
            try:
                result = await scrape_category(
                    banner, store, category, session_path,
                    write_db=args.db,
                    headless=not args.headed,
                )
                results.append(result)
            except Exception as e:
                logger.error("FAILED %s / %s: %s", store.name, category.name, e)
                results.append({
                    "store": store.name,
                    "category": category.name,
                    "products": 0,
                    "duration": 0,
                    "error": str(e),
                })

    total_elapsed = time.monotonic() - total_start

    print(f"\n{'=' * 60}")
    print("KeyFood Scrape Complete")
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

    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
