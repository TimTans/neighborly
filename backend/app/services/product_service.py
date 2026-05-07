"""
product queries against supabase.

all functions return plain dicts (supabase response data).
the route layer handles serialization to pydantic response models.
"""

import math

from app.core.supabase import get_supabase


def _haversine_miles(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """great-circle distance between two lat/lng points in miles."""
    R = 3958.8  # earth radius in miles
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = (
        math.sin(dlat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlng / 2) ** 2
    )
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


async def search_products(
    query: str | None = None,
    category_slug: str | None = None,
    store_id: str | None = None,
    page: int = 1,
    page_size: int = 50,
) -> dict:
    """
    search products with optional filters.

    returns {"data": [...products...], "count": total_matching}

    when store_id is provided, joins store_products to include price at that store.
    """
    sb = get_supabase()
    offset = (page - 1) * page_size

    q = sb.table("products").select(
        "id, name, brand, image_url, unit_size, upc, "
        "product_categories(id, name, slug), "
        "store_products(price, sale_price, in_stock, store_id, stores(name, chain, store_number)), "
        "product_nutrition(serving_size_g, servings_per_container, calories_kcal, "
        "protein_g, fat_g, carbs_g, fiber_g, sodium_mg, cholesterol_mg, sugar_g, "
        "contains_dairy, contains_peanuts, contains_shellfish, contains_wheat)",
        count="exact",
    )

    if query:
        q = q.ilike("name", f"%{query}%")

    if category_slug:
        cat_result = sb.table("product_categories").select("id").eq("slug", category_slug).execute()
        if cat_result.data:
            q = q.eq("category_id", cat_result.data[0]["id"])

    q = q.range(offset, offset + page_size - 1).order("name")
    result = q.execute()

    return {"data": result.data, "count": result.count}


async def fetch_recipe_catalog(
    limit: int = 2000,
    user_lat: float | None = None,
    user_lng: float | None = None,
    max_radius_miles: float | None = None,
) -> list[dict]:
    """
    fetch a slim view of in-stock products for use as recipe-generation context.

    one row per product, carrying just enough for the LLM to pick sensibly:
    id, name, brand, category slug, best in-stock price, the store with that
    price, and the four allergen flags. the recipe service formats these as
    a compact line per product before injection into the prompt.

    when user_lat/user_lng/max_radius_miles are all provided, products whose
    nearest in-stock store falls outside the radius are excluded — keeps the
    LLM from suggesting items the shopper can't reach.
    """
    sb = get_supabase()

    result = sb.table("products").select(
        "id, name, brand, image_url, "
        "product_categories(slug), "
        "store_products(price, sale_price, in_stock, stores(name, lat, lng)), "
        "product_nutrition(contains_dairy, contains_peanuts, "
        "contains_shellfish, contains_wheat)"
    ).limit(limit).execute()

    catalog: list[dict] = []
    for row in result.data or []:
        slim = _to_slim_row(
            row,
            user_lat=user_lat,
            user_lng=user_lng,
            max_radius_miles=max_radius_miles,
        )
        if slim["best_price"] is None:
            continue  # skip products with no in-radius in-stock offering
        catalog.append(slim)
    return catalog


async def search_products_slim(
    query: str | None = None,
    page: int = 1,
    page_size: int = 50,
) -> dict:
    """
    lightweight search for autocomplete-style use cases.

    returns {"data": [...slim_products...], "count": total_matching}
    where each slim product carries enough fields to render a search row
    (name, brand, image, best price, allergen flags) but no nutrition
    detail or per-store price breakdown — those load on tap via /products/{id}.
    """
    sb = get_supabase()
    offset = (page - 1) * page_size

    q = sb.table("products").select(
        "id, name, brand, image_url, unit_size, upc, "
        "product_categories(slug), "
        "store_products(price, sale_price, in_stock, stores(name)), "
        "product_nutrition(contains_dairy, contains_peanuts, "
        "contains_shellfish, contains_wheat)",
        count="exact",
    )

    if query:
        q = q.ilike("name", f"%{query}%")

    q = q.range(offset, offset + page_size - 1).order("name")
    result = q.execute()

    slim_rows = [_to_slim_row(row) for row in (result.data or [])]
    return {"data": slim_rows, "count": result.count}


def _to_slim_row(
    row: dict,
    user_lat: float | None = None,
    user_lng: float | None = None,
    max_radius_miles: float | None = None,
) -> dict:
    """flatten a supabase row into a slim search result.

    when all three radius args are provided, store_products whose store is
    outside max_radius_miles of (user_lat, user_lng) are skipped for the
    purpose of computing best_price. products with no in-radius offering end
    up with best_price=None and the caller can drop them.
    """
    use_radius = (
        user_lat is not None and user_lng is not None and max_radius_miles is not None
    )

    best_price: float | None = None
    best_store: str | None = None
    for sp in row.get("store_products") or []:
        if not sp.get("in_stock"):
            continue
        stores = sp.get("stores") or {}
        if use_radius:
            slat = stores.get("lat")
            slng = stores.get("lng")
            if slat is None or slng is None:
                continue
            if _haversine_miles(user_lat, user_lng, slat, slng) > max_radius_miles:
                continue
        eff = sp.get("sale_price") if sp.get("sale_price") is not None else sp.get("price")
        if eff is None:
            continue
        if best_price is None or eff < best_price:
            best_price = eff
            best_store = stores.get("name")

    cat = row.get("product_categories") or {}
    nutr = row.get("product_nutrition") or {}
    if isinstance(nutr, list):
        nutr = nutr[0] if nutr else {}

    return {
        "id": row["id"],
        "name": row["name"],
        "brand": row.get("brand"),
        "image_url": row.get("image_url"),
        "unit_size": row.get("unit_size"),
        "upc": row.get("upc"),
        "category_slug": cat.get("slug") if isinstance(cat, dict) else None,
        "best_price": best_price,
        "best_price_store_name": best_store,
        "contains_dairy": nutr.get("contains_dairy"),
        "contains_peanuts": nutr.get("contains_peanuts"),
        "contains_shellfish": nutr.get("contains_shellfish"),
        "contains_wheat": nutr.get("contains_wheat"),
    }


async def get_product(product_id: str) -> dict | None:
    """get a single product with all store prices."""
    sb = get_supabase()

    try:
        result = sb.table("products").select(
            "id, name, brand, image_url, unit_size, upc, "
            "product_categories(id, name, slug), "
            "store_products(price, sale_price, in_stock, store_id, "
            "stores(id, name, chain, store_number, zip_code)), "
            "product_nutrition(serving_size_g, servings_per_container, calories_kcal, "
            "protein_g, fat_g, carbs_g, fiber_g, sodium_mg, cholesterol_mg, sugar_g, "
            "contains_dairy, contains_peanuts, contains_shellfish, contains_wheat)"
        ).eq("id", product_id).execute()

        if not result.data:
            return None
        return result.data[0]
    except Exception:
        return None


async def get_alternatives(product_id: str, limit: int = 10) -> list[dict]:
    """
    find alternative products in the same category, excluding the original.
    returns products with store prices, sorted by cheapest effective price.
    """
    sb = get_supabase()

    # get the original product's category
    original = sb.table("products").select(
        "id, category_id"
    ).eq("id", product_id).execute()

    if not original.data or not original.data[0].get("category_id"):
        return []

    category_id = original.data[0]["category_id"]

    # find other products in the same category
    result = sb.table("products").select(
        "id, name, brand, image_url, unit_size, upc, "
        "product_categories(id, name, slug), "
        "store_products(price, sale_price, in_stock, store_id, "
        "stores(id, name, chain, store_number, zip_code))"
    ).eq(
        "category_id", category_id
    ).neq(
        "id", product_id
    ).limit(limit).order("name").execute()

    return result.data or []


async def get_product_prices(product_id: str) -> list[dict]:
    """get all store prices for a product, sorted cheapest first."""
    sb = get_supabase()

    try:
        result = sb.table("store_products").select(
            "price, sale_price, in_stock, updated_at, "
            "stores(id, name, chain, store_number, zip_code)"
        ).eq("product_id", product_id).order("price").execute()

        return result.data
    except Exception:
        return []
