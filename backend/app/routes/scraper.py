"""
admin endpoint to trigger scraping.

supports both shoprite and keyfood chains. pass chain="keyfood" or
chain="shoprite" to scrape a specific chain, or omit to scrape all.

in production this would use proper role-based auth.
"""

import logging
from pathlib import Path

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.scraper.config import (
    STORES,
    CATEGORIES,
    KEYFOOD_BANNERS,
    KEYFOOD_STORES,
    KEYFOOD_CATEGORIES,
    build_browse_url,
    build_keyfood_url,
    get_keyfood_session_path,
    StoreInfo,
    CategoryConfig,
    KeyFoodCategoryConfig,
)
from app.scraper.shoprite import StoreConfig, scrape_store as scrape_shoprite
from app.scraper.keyfood import scrape_store as scrape_keyfood
from app.scraper.db_writer import ensure_store_exists, ensure_category_exists, upsert_products

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/scraper", tags=["scraper"])

DATA_DIR = Path("data")


class ScrapeRequest(BaseModel):
    store_id: str | None = None
    category_slug: str | None = None
    chain: str | None = None  # "shoprite", "keyfood", "marketplace", or None for all


class ScrapeResult(BaseModel):
    store: str
    category: str
    products: int


async def _run_shoprite_scrape(
    store: StoreInfo, category: CategoryConfig,
) -> ScrapeResult:
    """scrape one shoprite store+category and write to supabase."""
    session_path = DATA_DIR / "shoprite_session.json"
    config = StoreConfig(
        store_id=store.store_id,
        zip_code=store.zip_code,
        browse_url=build_browse_url(store, category),
    )

    products = await scrape_shoprite(
        config,
        session_state=session_path if session_path.exists() else None,
    )

    store_uuid = await ensure_store_exists(store)
    cat_uuid = await ensure_category_exists(category)
    await upsert_products(products, store_uuid, cat_uuid)

    return ScrapeResult(store=store.name, category=category.name, products=len(products))


async def _run_keyfood_scrape(
    store: StoreInfo, category: KeyFoodCategoryConfig,
) -> ScrapeResult:
    """scrape one keyfood store+category and write to supabase."""
    banner = KEYFOOD_BANNERS[store.banner]
    session_path = DATA_DIR / get_keyfood_session_path(store.banner)

    if not session_path.exists():
        raise FileNotFoundError(
            f"no session for banner '{store.banner}' — run: "
            f"PYTHONPATH=. uv run python scripts/save_keyfood_session.py "
            f"--banner {store.banner}"
        )

    category_url = build_keyfood_url(banner, store, category)

    products = await scrape_keyfood(
        store_id=store.store_id,
        store_zip=store.zip_code,
        category_url=category_url,
        base_domain=banner.domain,
        category_name=category.name,
        session_state=session_path,
        headless=True,
    )

    store_uuid = await ensure_store_exists(store)
    cat_uuid = await ensure_category_exists(category)
    await upsert_products(products, store_uuid, cat_uuid)

    return ScrapeResult(store=store.name, category=category.name, products=len(products))


@router.post("/run")
async def trigger_scrape(body: ScrapeRequest = ScrapeRequest()):
    """
    trigger a scrape run. optionally filter by chain, store_id, or category_slug.
    scrapes all matching store x category combinations and writes to supabase.
    """
    results = []

    # determine which chains to scrape
    scrape_shoprite_chain = body.chain in (None, "shoprite")
    scrape_keyfood_chain = body.chain in (None, "keyfood", "marketplace")

    # --- shoprite ---
    if scrape_shoprite_chain:
        stores = STORES
        categories = CATEGORIES

        if body.store_id:
            stores = [s for s in STORES if s.store_id == body.store_id]
        if body.category_slug:
            categories = [c for c in CATEGORIES if c.slug == body.category_slug]

        for store in stores:
            for category in categories:
                try:
                    result = await _run_shoprite_scrape(store, category)
                    results.append(result.model_dump())
                except Exception as e:
                    logger.error("shoprite scrape failed %s/%s: %s", store.name, category.name, e)
                    results.append({
                        "store": store.name,
                        "category": category.name,
                        "products": 0,
                        "error": str(e),
                    })

    # --- keyfood ---
    if scrape_keyfood_chain:
        kf_stores = list(KEYFOOD_STORES)
        kf_categories = list(KEYFOOD_CATEGORIES)

        # when a specific keyfood banner is requested, filter stores to that chain
        if body.chain in ("keyfood", "marketplace"):
            kf_stores = [s for s in kf_stores if s.chain == body.chain]

        if body.store_id:
            kf_stores = [s for s in kf_stores if s.store_id == body.store_id]
        if body.category_slug:
            kf_categories = [c for c in kf_categories if c.slug == body.category_slug]

        for store in kf_stores:
            for category in kf_categories:
                try:
                    result = await _run_keyfood_scrape(store, category)
                    results.append(result.model_dump())
                except Exception as e:
                    logger.error("keyfood scrape failed %s/%s: %s", store.name, category.name, e)
                    results.append({
                        "store": store.name,
                        "category": category.name,
                        "products": 0,
                        "error": str(e),
                    })

    if not results:
        raise HTTPException(
            status_code=400,
            detail=f"no matching stores/categories found for the given filters",
        )

    return {"results": results}
