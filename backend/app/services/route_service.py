"""
route optimization service.

takes a list of product IDs and returns the cheapest way to buy them,
grouped by store.

lowest_cost strategy: for each product, pick the store with the cheapest
effective price (sale_price if available, otherwise regular price). when
prices tie, pick the store closest to the user's current position (or
the store they'd already be visiting, to minimize stops).
"""

import math

from app.core.supabase import get_supabase


def _haversine(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """distance in miles between two lat/lng points."""
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


def _effective_price(offering: dict) -> float:
    """get the price the customer actually pays."""
    return offering["sale_price"] if offering["sale_price"] else offering["price"]


async def optimize_lowest_cost(
    product_ids: list[str],
    user_lat: float | None = None,
    user_lng: float | None = None,
) -> dict:
    """
    optimize a grocery list for lowest total cost.

    tiebreaking when two stores have the same price:
    1. prefer a store already in the route (reduces total stops)
    2. if still tied, prefer the store closest to user_lat/user_lng

    returns:
    {
        "total_cost": float,
        "stops": [
            {
                "store": { "id", "name", "chain", "store_number", "zip_code", "lat", "lng" },
                "items": [ { "product_id", "name", ... } ],
                "subtotal": float,
            },
        ],
        "items_not_found": [str],
    }
    """
    sb = get_supabase()

    # fetch all store_products for the requested product IDs in one query
    result = sb.table("store_products").select(
        "price, sale_price, in_stock, store_id, product_id, "
        "stores(id, name, chain, store_number, zip_code, address, lat, lng), "
        "products(id, name, brand, unit_size, image_url, "
        "product_categories(slug))"
    ).in_("product_id", product_ids).eq("in_stock", True).execute()

    # group by product_id → list of store offerings
    offerings: dict[str, list[dict]] = {}
    for row in result.data:
        pid = row["product_id"]
        offerings.setdefault(pid, []).append(row)

    # two-pass assignment:
    # pass 1 — assign items where there's a single cheapest store (no tie)
    # pass 2 — assign tied items, preferring stores already in the route

    stops: dict[str, dict] = {}  # store_id → { store, items, subtotal }
    items_not_found: list[str] = []
    tied_items: list[tuple[str, list[dict]]] = []  # (product_id, tied_options)

    # pass 1: unambiguous assignments
    for pid in product_ids:
        options = offerings.get(pid)
        if not options:
            items_not_found.append(pid)
            continue

        min_price = min(_effective_price(o) for o in options)
        cheapest = [o for o in options if _effective_price(o) == min_price]

        if len(cheapest) == 1:
            _assign_to_stop(stops, cheapest[0])
        else:
            tied_items.append((pid, cheapest))

    # pass 2: resolve ties
    for pid, cheapest in tied_items:
        # prefer store already in the route
        in_route = [o for o in cheapest if o["store_id"] in stops]
        if in_route:
            # if multiple in-route stores tie, pick closest to user
            best = _pick_closest(in_route, user_lat, user_lng)
        else:
            # none in route yet — pick closest to user
            best = _pick_closest(cheapest, user_lat, user_lng)

        _assign_to_stop(stops, best)

    # sort stops by subtotal descending (biggest spend first)
    sorted_stops = sorted(stops.values(), key=lambda s: s["subtotal"], reverse=True)

    total_cost = 0.0
    for stop in sorted_stops:
        stop["subtotal"] = round(stop["subtotal"], 2)
        total_cost += stop["subtotal"]

    return {
        "total_cost": round(total_cost, 2),
        "stops": sorted_stops,
        "items_not_found": items_not_found,
    }


def _assign_to_stop(stops: dict, offering: dict) -> None:
    """add a product offering to its store's stop."""
    store_id = offering["store_id"]
    product = offering["products"]
    category = product.get("product_categories") or {}

    item = {
        "product_id": product["id"],
        "name": product["name"],
        "brand": product.get("brand"),
        "unit_size": product["unit_size"],
        "image_url": product.get("image_url"),
        "category_slug": category.get("slug"),
        "price": offering["price"],
        "sale_price": offering["sale_price"],
    }

    if store_id not in stops:
        stops[store_id] = {
            "store": offering["stores"],
            "items": [],
            "subtotal": 0.0,
        }

    stops[store_id]["items"].append(item)
    stops[store_id]["subtotal"] += _effective_price(offering)


def _pick_closest(
    options: list[dict],
    user_lat: float | None,
    user_lng: float | None,
) -> dict:
    """pick the store closest to the user. falls back to first option."""
    if user_lat is None or user_lng is None:
        return options[0]

    return min(
        options,
        key=lambda o: _haversine(
            user_lat, user_lng,
            o["stores"].get("lat") or 0,
            o["stores"].get("lng") or 0,
        ),
    )
