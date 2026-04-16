"""
nutrition enrichment service.

fetches nutrition data from the usda fooddata central (fdc) api by upc/gtin
and stores it in the product_nutrition table.
"""

import asyncio
import logging
import time as _time

import httpx

from app.core.supabase import get_supabase
from app.services import fdc
from app.services.fdc import NUTRIENT_IDS as _FDC_NUTRIENT_IDS

logger = logging.getLogger(__name__)

# invert fdc's id→name map to id→column mapping (same keys, values are already column names)
_NUTRIENT_MAP = dict(_FDC_NUTRIENT_IDS)

_ALLERGEN_KEYWORDS: dict[str, list[str]] = {
    "contains_dairy": ["milk", "cream", "butter", "cheese", "whey", "lactose", "casein"],
    "contains_peanuts": ["peanut"],
    "contains_shellfish": ["shellfish", "shrimp", "crab", "lobster", "clam", "oyster", "scallop", "mussel"],
    "contains_wheat": ["wheat", "gluten", "barley", "rye", "triticale"],
}


def _parse_allergens(ingredients: str | None) -> dict[str, bool | None]:
    """
    detect allergens from ingredients text via keyword matching.
    returns None for each flag when ingredients is None (unknown, not safe).
    """
    if ingredients is None:
        return {key: None for key in _ALLERGEN_KEYWORDS}

    lower = ingredients.lower()
    return {
        key: any(kw in lower for kw in keywords)
        for key, keywords in _ALLERGEN_KEYWORDS.items()
    }


def _serving_size_g(size: float, unit: str) -> float | None:
    """convert fdc serving size to grams. returns None for unmappable units."""
    unit = (unit or "").lower().strip()
    if unit in ("g", "gram", "grams"):
        return size
    if unit in ("ml", "milliliter", "milliliters"):
        return size  # ≈ g for water-based foods
    if unit in ("oz", "ounce", "ounces"):
        return round(size * 28.3495, 2)
    return None  # cup, tbsp, tsp, etc. — not reliably convertible


def _parse_nutrients(food_nutrients: list[dict]) -> dict[str, float | None]:
    """extract the nutrient values we care about from an fdc foodNutrients list."""
    lookup = {n["nutrientId"]: n.get("value") for n in food_nutrients if "nutrientId" in n}
    return {col: lookup.get(nid) for nid, col in _NUTRIENT_MAP.items()}


async def lookup_by_upc(
    upc: str,
    client: httpx.AsyncClient | None = None,
    _retries: int = 3,
) -> dict | None:
    """
    search fdc for a branded product matching the given upc/gtin.
    returns a normalized nutrition dict ready for upsert, or None if not found.
    retries on 429 (rate limited) with exponential backoff.
    """
    for attempt in range(_retries):
        try:
            response = await fdc.search_foods(
                query=upc, data_types=["Branded"], page_size=10, client=client,
            )
            break
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 429 and attempt < _retries - 1:
                wait = 2 ** attempt * 5
                logger.warning("fdc rate limited, waiting %ds before retry...", wait)
                await asyncio.sleep(wait)
            else:
                raise
    else:
        return None
    foods = response.get("foods") or []

    # normalize upc to 13-digit GTIN for comparison (UPC-A 12→EAN-13 with leading 0)
    normalized = upc.zfill(13)
    match = next(
        (f for f in foods if (f.get("gtinUpc") or "").zfill(13) == normalized),
        None,
    )
    if not match:
        return None

    nutrients = _parse_nutrients(match.get("foodNutrients") or [])
    allergens = _parse_allergens(match.get("ingredients"))

    raw_size = match.get("servingSize")
    raw_unit = match.get("servingSizeUnit") or ""
    try:
        serving_g = _serving_size_g(float(raw_size), raw_unit) if raw_size else None
    except (ValueError, TypeError):
        serving_g = None

    return {
        "fdc_id": match.get("fdcId"),
        "serving_size_g": serving_g,
        "servings_per_container": match.get("servingsPerContainer"),
        "ingredients": match.get("ingredients"),
        **nutrients,
        **allergens,
    }


async def enrich_product(product_id: str, upc: str) -> bool:
    """
    fetch fdc data for upc and upsert into product_nutrition.
    returns True if enriched, False if upc not found in fdc.
    """
    data = await lookup_by_upc(upc)
    if data is None:
        return False

    sb = get_supabase()
    result = sb.table("product_nutrition").upsert(
        {"product_id": product_id, **data},
        on_conflict="product_id",
    ).execute()
    if not result.data:
        raise RuntimeError(f"upsert returned no data for product_id={product_id}")
    return True


async def get_unenriched_pairs() -> list[tuple[str, str]]:
    """
    fetch all (product_id, upc) pairs for products that have a upc
    but no nutrition row yet. paginates through supabase's 1000-row limit.
    """
    sb = get_supabase()
    page_size = 1000

    # collect all products with a upc
    all_products: list[dict] = []
    offset = 0
    while True:
        result = (
            sb.table("products")
            .select("id, upc")
            .not_.is_("upc", None)
            .range(offset, offset + page_size - 1)
            .execute()
        )
        batch = result.data or []
        all_products.extend(batch)
        if len(batch) < page_size:
            break
        offset += page_size

    # collect all already-enriched product ids
    enriched_ids: set[str] = set()
    offset = 0
    while True:
        result = (
            sb.table("product_nutrition")
            .select("product_id")
            .range(offset, offset + page_size - 1)
            .execute()
        )
        batch = result.data or []
        enriched_ids.update(row["product_id"] for row in batch)
        if len(batch) < page_size:
            break
        offset += page_size

    return [
        (p["id"], p["upc"])
        for p in all_products
        if p["id"] not in enriched_ids and p["upc"]
    ]


async def enrich_batch(
    pairs: list[tuple[str, str]],
    concurrency: int = 5,
    req_delay: float = 0.25,
) -> dict:
    """
    enrich a list of (product_id, upc) pairs concurrently.
    concurrency controls max simultaneous fdc requests (default 5).
    req_delay adds a small sleep per request to respect fdc rate limits.
    returns {"enriched": int, "not_found": int, "errors": int, "elapsed_seconds": float}
    """
    start = _time.monotonic()
    semaphore = asyncio.Semaphore(concurrency)
    total = len(pairs)
    enriched = not_found = errors = 0
    processed = 0
    pending_upserts: list[dict] = []

    async with httpx.AsyncClient(timeout=30) as client:
        async def process(product_id: str, upc: str) -> None:
            nonlocal enriched, not_found, errors, processed
            async with semaphore:
                try:
                    data = await lookup_by_upc(upc, client=client)
                    if data is None:
                        not_found += 1
                    else:
                        enriched += 1
                        pending_upserts.append({"product_id": product_id, **data})
                except Exception as e:
                    logger.error("enrichment failed for %s (upc=%s): %s", product_id, upc, e)
                    errors += 1

                processed += 1
                if processed % 200 == 0 or processed == total:
                    elapsed = _time.monotonic() - start
                    logger.info(
                        "progress: %d/%d (enriched=%d, not_found=%d, errors=%d) %.1fs",
                        processed, total, enriched, not_found, errors, elapsed,
                    )

                # small delay to stay under fdc free-tier rate limits
                await asyncio.sleep(req_delay)

        await asyncio.gather(*(process(pid, upc) for pid, upc in pairs))

    # batch upsert to supabase in chunks
    if pending_upserts:
        sb = get_supabase()
        for i in range(0, len(pending_upserts), 500):
            chunk = pending_upserts[i : i + 500]
            sb.table("product_nutrition").upsert(
                chunk, on_conflict="product_id",
            ).execute()
        logger.info("upserted %d nutrition records to supabase", len(pending_upserts))

    elapsed = _time.monotonic() - start
    logger.info(
        "enrichment complete: %d enriched, %d not found, %d errors (%.1fs)",
        enriched, not_found, errors, elapsed,
    )
    return {
        "enriched": enriched,
        "not_found": not_found,
        "errors": errors,
        "elapsed_seconds": round(elapsed, 1),
    }
