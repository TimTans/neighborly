from fastapi import APIRouter, HTTPException, Query

from app.services import product_service

router = APIRouter(prefix="/products", tags=["products"])


@router.get("")
async def list_products(
    q: str | None = Query(default=None, description="search by product name"),
    category: str | None = Query(default=None, description="filter by category slug"),
    store: str | None = Query(default=None, description="filter by store uuid"),
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=200),
):
    """search and filter products. returns paginated results with store prices."""
    return await product_service.search_products(
        query=q,
        category_slug=category,
        store_id=store,
        page=page,
        page_size=page_size,
    )


@router.get("/{product_id}")
async def get_product(product_id: str):
    """get product detail with prices at all stores."""
    product = await product_service.get_product(product_id)
    if not product:
        raise HTTPException(status_code=404, detail="product not found")
    return product


@router.get("/{product_id}/alternatives")
async def get_alternatives(
    product_id: str,
    limit: int = Query(default=10, ge=1, le=50),
):
    """find alternative products in the same category."""
    return await product_service.get_alternatives(product_id, limit=limit)


@router.get("/{product_id}/prices")
async def get_product_prices(product_id: str):
    """get price comparison across all stores for a product, sorted cheapest first."""
    return await product_service.get_product_prices(product_id)
