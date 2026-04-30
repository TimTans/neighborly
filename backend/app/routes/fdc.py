from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel

from app.services import fdc as fdc_service
from app.services.nutrition_service import lookup_by_upc

router = APIRouter(prefix="/fdc", tags=["fdc"])


@router.get("/search")
async def search_foods(
    query: str = Query(..., description="food name or keyword to search for"),
    page_size: int = Query(default=25, ge=1, le=200),
    page_number: int = Query(default=1, ge=1),
    branded_only: bool = Query(default=True, description="limit results to branded/packaged products"),
):
    """search for foods by name. returns matching products with nutrition data."""
    data_types = ["Branded"] if branded_only else None
    return await fdc_service.search_foods(
        query=query,
        page_size=page_size,
        page_number=page_number,
        data_types=data_types,
    )


@router.get("/food/{fdc_id}")
async def get_food(fdc_id: int):
    """get full nutrition details for a specific food by its fdc id."""
    try:
        return await fdc_service.get_food(fdc_id)
    except Exception:
        raise HTTPException(status_code=404, detail=f"food with fdc id {fdc_id} not found")


class BulkFoodRequest(BaseModel):
    fdc_ids: list[int]

    model_config = {
        "json_schema_extra": {
            "example": {"fdc_ids": [167512, 167513, 167514]}
        }
    }


@router.post("/foods/bulk")
async def get_foods_bulk(body: BulkFoodRequest):
    """fetch nutrition details for multiple foods in one request."""
    return await fdc_service.get_foods_bulk(body.fdc_ids)


@router.get("/lookup")
async def lookup_by_upc_route(
    upc: str = Query(..., description="product UPC/GTIN to look up in FDC"),
):
    """
    look up a product's nutrition data from FDC by UPC/GTIN.
    returns normalized nutrition dict or 404 if not found.
    used as a fallback for products not yet enriched in product_nutrition.
    """
    result = await lookup_by_upc(upc)
    if result is None:
        raise HTTPException(status_code=404, detail=f"no FDC match for upc {upc}")
    return result
