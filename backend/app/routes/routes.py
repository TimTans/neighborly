"""
route optimization endpoint.

takes a grocery list (product IDs), an optional user location, and an
optimization mode. returns an optimized shopping route grouped by store.
"""

from typing import Literal

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.services import google_routes
from app.services.google_routes import GoogleRoutesError
from app.services.route_service import (
    optimize_fewest_stops,
    optimize_lowest_cost,
    optimize_shortest_distance,
)

router = APIRouter(prefix="/routes", tags=["routes"])


class OptimizeRequest(BaseModel):
    product_ids: list[str]
    user_lat: float | None = None
    user_lng: float | None = None
    mode: Literal["cost", "stops", "distance"] = "cost"
    max_stops: int | None = None
    max_radius_miles: float | None = None


class Waypoint(BaseModel):
    lat: float
    lng: float


class DirectionsRequest(BaseModel):
    waypoints: list[Waypoint]
    mode: Literal["walking", "driving", "transit"] = "walking"


@router.post("/optimize")
async def optimize_route(body: OptimizeRequest):
    """
    optimize a shopping route for a list of products.

    modes:
    - cost: minimize total spend (default)
    - stops: minimize number of stores visited
    - distance: minimize total travel distance
    """
    if not body.product_ids:
        raise HTTPException(status_code=400, detail="product_ids is required")

    if body.mode == "stops":
        return await optimize_fewest_stops(
            body.product_ids,
            user_lat=body.user_lat,
            user_lng=body.user_lng,
            max_stops=body.max_stops,
            max_radius_miles=body.max_radius_miles,
        )
    elif body.mode == "distance":
        return await optimize_shortest_distance(
            body.product_ids,
            user_lat=body.user_lat,
            user_lng=body.user_lng,
            max_stops=body.max_stops,
            max_radius_miles=body.max_radius_miles,
        )
    else:
        return await optimize_lowest_cost(
            body.product_ids,
            user_lat=body.user_lat,
            user_lng=body.user_lng,
            max_stops=body.max_stops,
            max_radius_miles=body.max_radius_miles,
        )


@router.post("/directions")
async def get_directions(body: DirectionsRequest):
    """
    fetch a route through the given waypoints from google routes api.

    modes:
    - walking: WALK profile, all waypoints in one call
    - driving: DRIVE profile with traffic-aware routing
    - transit: TRANSIT profile, stitched per-leg (transit doesn't accept
      intermediate waypoints in a single request)

    returns a normalized shape:
    { coordinates: [[lng, lat], ...], legs: [...], total_distance_m, total_duration_s }
    """
    if len(body.waypoints) < 2:
        raise HTTPException(status_code=400, detail="at least two waypoints required")

    pts = [(wp.lat, wp.lng) for wp in body.waypoints]
    try:
        return await google_routes.compute_route(pts, body.mode)
    except GoogleRoutesError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
