"""
utilities for the usda fooddata central (fdc) api.

docs:    https://fdc.nal.usda.gov/api-guide/
openapi: https://fdc.nal.usda.gov/api-spec/fdc_api.html

free key at: https://fdc.nal.usda.gov/api-key-signup/
"""

from contextlib import nullcontext

import httpx

from app.core.config import settings

FDC_BASE_URL = "https://api.nal.usda.gov/fdc/v1"

# nutrient ids we care about for neighborly's dietary filtering and display.
# full list: https://fdc.nal.usda.gov/api-spec/fdc_api.html
NUTRIENT_IDS = {
    1008: "calories_kcal",
    1003: "protein_g",
    1004: "fat_g",
    1005: "carbs_g",
    1079: "fiber_g",
    1093: "sodium_mg",
    1253: "cholesterol_mg",
    2000: "sugar_g",
}


async def search_foods(
    query: str,
    page_size: int = 25,
    page_number: int = 1,
    data_types: list[str] | None = None,
    client: httpx.AsyncClient | None = None,
) -> dict:
    """
    search fdc for foods matching a keyword query.

    use this when a user types an item into their grocery list to find
    matching products along with their nutrition data.

    data_types filters the source dataset. useful values:
    "Branded" - packaged/branded grocery products (probably most useful for us)
    "SR Legacy" - usda standard reference (raw ingredients)
    "Foundation" - foundation foods (detailed research data)

    pass a shared httpx.AsyncClient via client to reuse connections
    across many calls (avoids a new TCP handshake per request).
    """
    params: dict = {
        "api_key": settings.FDC_API_KEY,
        "query": query,
        "pageSize": page_size,
        "pageNumber": page_number,
    }
    if data_types:
        params["dataType"] = ",".join(data_types)

    cm = nullcontext(client) if client else httpx.AsyncClient()
    async with cm as c:
        response = await c.get(f"{FDC_BASE_URL}/foods/search", params=params)
        response.raise_for_status()
        return response.json()


async def get_food(fdc_id: int, nutrients: list[int] | None = None) -> dict:
    """
    get full nutrient details for a specific food by fdc id.

    pass a list of nutrient ids to get only the nutrients you care about,
    which reduces response size. pass None to get all nutrients.

    example - only fetch the nutrients neighborly uses:
    get_food(167512, nutrients=list(NUTRIENT_IDS.keys()))
    """
    params: dict = {"api_key": settings.FDC_API_KEY}
    if nutrients:
        params["nutrients"] = ",".join(str(n) for n in nutrients)

    async with httpx.AsyncClient() as client:
        response = await client.get(f"{FDC_BASE_URL}/food/{fdc_id}", params=params)
        response.raise_for_status()
        return response.json()


async def get_foods_bulk(fdc_ids: list[int]) -> list[dict]:
    """
    batch fetch details for multiple foods in one request.

    use this when enriching a batch of products (e.g. after scraping a store)
    rather than calling get_food() in a loop.
    """
    async with httpx.AsyncClient() as client:
        response = await client.post(
            f"{FDC_BASE_URL}/foods",
            params={"api_key": settings.FDC_API_KEY},
            json={"fdcIds": fdc_ids},
        )
        response.raise_for_status()
        return response.json()

