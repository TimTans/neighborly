"""
route optimization endpoint.

takes a grocery list (product IDs) and an optional user location, returns
an optimized shopping route grouped by store, minimizing total cost.
ties are broken by proximity to the user.
"""

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services.route_service import optimize_lowest_cost

router = APIRouter(prefix="/routes", tags=["routes"])


class OptimizeRequest(BaseModel):
    product_ids: list[str]
    user_lat: float | None = None
    user_lng: float | None = None


@router.post("/optimize")
async def optimize_route(body: OptimizeRequest):
    """
    optimize a shopping route for a list of products.
    returns stores to visit with items to buy at each, sorted by spend.

    when prices tie between stores, the store closest to
    (user_lat, user_lng) is preferred.
    """
    if not body.product_ids:
        raise HTTPException(status_code=400, detail="product_ids is required")

    result = await optimize_lowest_cost(
        body.product_ids,
        user_lat=body.user_lat,
        user_lng=body.user_lng,
    )
    return result
