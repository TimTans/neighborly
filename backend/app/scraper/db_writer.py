"""
writes scraped products to supabase.

handles the full pipeline:
1. upsert product_categories (from scraped category names)
2. upsert products (match on upc, fall back to name+brand)
3. upsert store_products (current price at store)
4. insert store_product_price_history (track price changes)

uses the supabase service key for full DB access (bypasses RLS).
"""

import logging
from datetime import datetime, timezone
from typing import Protocol

from app.core.supabase import get_supabase
from app.scraper.config import StoreInfo, CategoryConfig, KeyFoodCategoryConfig

logger = logging.getLogger(__name__)


class ScrapedProduct(Protocol):
    """common interface for products from any scraper."""
    name: str
    price: float
    unit_size: str
    upc: str | None
    brand: str | None
    image_url: str | None
    sale_price: float | None


async def ensure_store_exists(store: StoreInfo) -> str:
    """
    ensure the store exists in the stores table. returns the store uuid.
    matches on chain + store_number to avoid duplicates.
    """
    sb = get_supabase()

    result = sb.table("stores").select("id").eq(
        "store_number", store.store_id
    ).eq("chain", store.chain).execute()

    if result.data:
        store_uuid = result.data[0]["id"]
        # update location fields if config has real values
        updates = {}
        if store.lat or store.lng:
            updates["lat"] = store.lat
            updates["lng"] = store.lng
        if store.address:
            updates["address"] = store.address
        if updates:
            sb.table("stores").update(updates).eq("id", store_uuid).execute()
        return store_uuid

    result = sb.table("stores").insert({
        "name": store.name,
        "chain": store.chain,
        "store_number": store.store_id,
        "zip_code": store.zip_code,
        "address": store.address or f"{store.name}, {store.zip_code}",
        "lat": store.lat,
        "lng": store.lng,
    }).execute()

    store_uuid = result.data[0]["id"]
    logger.info("created store %s with uuid %s", store.name, store_uuid)
    return store_uuid


async def ensure_category_exists(
    category: CategoryConfig | KeyFoodCategoryConfig,
) -> str:
    """
    ensure the category exists in product_categories. returns the category uuid.
    matches on slug to avoid duplicates.
    """
    sb = get_supabase()

    result = sb.table("product_categories").select("id").eq(
        "slug", category.slug
    ).execute()

    if result.data:
        return result.data[0]["id"]

    result = sb.table("product_categories").insert({
        "name": category.name,
        "slug": category.slug,
    }).execute()

    cat_uuid = result.data[0]["id"]
    logger.info("created category %s with uuid %s", category.name, cat_uuid)
    return cat_uuid


async def upsert_products(
    products: list[ScrapedProduct],
    store_uuid: str,
    category_uuid: str,
) -> dict:
    """
    upsert a batch of scraped products into supabase.

    returns summary: {"products_upserted": int, "prices_upserted": int}

    strategy:
    - products with UPC: upsert matching on upc
    - products without UPC: upsert matching on name (less reliable)
    - store_products: upsert matching on (store_id, product_id)
    - price_history: always insert (append-only log)
    """
    sb = get_supabase()
    products_upserted = 0
    prices_upserted = 0

    for product in products:
        try:
            # 1. upsert into products table
            product_data = {
                "name": product.name,
                "brand": product.brand,
                "image_url": product.image_url,
                "unit_size": product.unit_size,
                "category_id": category_uuid,
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }
            if product.upc:
                product_data["upc"] = product.upc

            # try to find existing product by UPC first, then by name+brand
            existing = None
            if product.upc:
                result = sb.table("products").select("id").eq("upc", product.upc).execute()
                if result.data:
                    existing = result.data[0]

            if not existing:
                query = sb.table("products").select("id").eq("name", product.name)
                if product.brand:
                    query = query.eq("brand", product.brand)
                result = query.execute()
                if result.data:
                    existing = result.data[0]

            if existing:
                product_uuid = existing["id"]
                sb.table("products").update(product_data).eq("id", product_uuid).execute()
            else:
                product_data["created_at"] = datetime.now(timezone.utc).isoformat()
                result = sb.table("products").insert(product_data).execute()
                product_uuid = result.data[0]["id"]

            products_upserted += 1

            # 2. upsert into store_products (current price at this store)
            store_product_data = {
                "store_id": store_uuid,
                "product_id": product_uuid,
                "price": product.price,
                "sale_price": product.sale_price,
                "in_stock": True,
                "data_source": "scraper",
                "updated_at": datetime.now(timezone.utc).isoformat(),
            }

            sp_result = sb.table("store_products").select("id").eq(
                "store_id", store_uuid
            ).eq("product_id", product_uuid).execute()

            if sp_result.data:
                store_product_id = sp_result.data[0]["id"]
                sb.table("store_products").update(store_product_data).eq(
                    "id", store_product_id
                ).execute()
            else:
                sp_insert = sb.table("store_products").insert(store_product_data).execute()
                store_product_id = sp_insert.data[0]["id"]

            prices_upserted += 1

            # 3. insert price history (append-only)
            sb.table("store_product_price_history").insert({
                "store_product_id": store_product_id,
                "price": product.price,
                "sale_price": product.sale_price,
            }).execute()

        except Exception as e:
            logger.error("failed to upsert product %s: %s", product.name, e)
            continue

    return {"products_upserted": products_upserted, "prices_upserted": prices_upserted}
