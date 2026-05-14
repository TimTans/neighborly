"""
route optimization service.

takes a list of product IDs and returns an optimized shopping route
grouped by store. supports three modes:

- lowest cost: pick the cheapest store for each product, tiebreak by
  proximity to user
- fewest stops: greedy set cover to minimize store visits, tiebreak by
  cost then proximity
- shortest distance: greedy nearest-store selection with 2-opt route
  improvement, tiebreak by cost
"""

import math
from itertools import combinations as _combinations

from app.core.supabase import get_supabase

def km_to_miles(km):
    return km * 0.621371


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


def _filter_by_radius(
    store_offerings: dict[str, list[dict]],
    user_lat: float,
    user_lng: float,
    max_radius_miles: float,
) -> dict[str, list[dict]]:
    """remove stores beyond max_radius_miles from the user."""
    result = {}
    for sid, offerings in store_offerings.items():
        if not offerings:
            continue
        lat = offerings[0]["stores"].get("lat")
        lng = offerings[0]["stores"].get("lng")
        if lat is None or lng is None:
            continue
        if _haversine(user_lat, user_lng, lat, lng) <= max_radius_miles:
            result[sid] = offerings
    return result


def _effective_price(offering: dict) -> float:
    """get the price the customer actually pays."""
    return offering["sale_price"] if offering["sale_price"] else offering["price"]


async def optimize_lowest_cost(
    product_ids: list[str],
    user_lat: float | None = None,
    user_lng: float | None = None,
    max_stops: int | None = None,
    max_radius_miles: float | None = None,
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
        "id, price, sale_price, in_stock, store_id, product_id, "
        "stores(id, name, chain, store_number, zip_code, address, lat, lng), "
        "products(id, name, brand, unit_size, image_url, "
        "product_categories(slug))"
    ).in_("product_id", product_ids).eq("in_stock", True).execute()

    no_route_reason: str | None = None
    rows = result.data

    if max_radius_miles is not None and user_lat is not None and user_lng is not None:
        in_radius_sids = {
            row["store_id"] for row in rows
            if _haversine(
                user_lat, user_lng,
                row["stores"].get("lat") or 0,
                row["stores"].get("lng") or 0,
            ) <= max_radius_miles
        }
        if rows and not in_radius_sids:
            no_route_reason = "max_radius"
        rows = [row for row in rows if row["store_id"] in in_radius_sids]

    # group by product_id → list of store offerings
    offerings: dict[str, list[dict]] = {}
    for row in rows:
        pid = row["product_id"]
        offerings.setdefault(pid, []).append(row)

    # two-pass assignment:
    # pass 1: assign items where there's a single cheapest store (no tie)
    # pass 2: assign tied items, preferring stores already in the route

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
            # none in route yet, pick closest to user
            best = _pick_closest(cheapest, user_lat, user_lng)

        _assign_to_stop(stops, best)

    if max_stops is not None and len(stops) > max_stops:
        return {
            "total_cost": 0.0,
            "total_distance": 0.0,
            "stops": [],
            "items_not_found": list(product_ids),
            "no_route": True,
            "no_route_reason": "max_stops",
        }

    # order stops by nearest-neighbor from user location
    sorted_stops = _order_stops_nearest_neighbor(
        list(stops.values()), user_lat, user_lng,
    )

    total_cost = 0.0
    for stop in sorted_stops:
        stop["subtotal"] = round(stop["subtotal"], 2)
        total_cost += stop["subtotal"]

    return {
        "total_cost": round(total_cost, 2),
        "total_distance": _compute_route_distance(sorted_stops, user_lat, user_lng),
        "stops": sorted_stops,
        "items_not_found": items_not_found,
        "no_route": len(sorted_stops) == 0,
        "no_route_reason": no_route_reason if len(sorted_stops) == 0 else None,
    }


async def optimize_fewest_stops(
    product_ids: list[str],
    user_lat: float | None = None,
    user_lng: float | None = None,
    max_stops: int | None = None,
    max_radius_miles: float | None = None,
) -> dict:
    """
    optimize a grocery list for the fewest number of store visits.

    uses greedy set cover: repeatedly pick the store that covers the most
    uncovered products. tiebreak on lowest cost, then closest to user.
    """
    sb = get_supabase()

    result = sb.table("store_products").select(
        "id, price, sale_price, in_stock, store_id, product_id, "
        "stores(id, name, chain, store_number, zip_code, address, lat, lng), "
        "products(id, name, brand, unit_size, image_url, "
        "product_categories(slug))"
    ).in_("product_id", product_ids).eq("in_stock", True).execute()

    # group offerings by store_id and by product_id
    store_offerings: dict[str, list[dict]] = {}
    product_available: dict[str, list[dict]] = {}
    for row in result.data:
        store_offerings.setdefault(row["store_id"], []).append(row)
        product_available.setdefault(row["product_id"], []).append(row)

    no_route_reason: str | None = None

    # apply radius filter
    if max_radius_miles is not None and user_lat is not None and user_lng is not None:
        pre_count = len(store_offerings)
        store_offerings = _filter_by_radius(store_offerings, user_lat, user_lng, max_radius_miles)
        if pre_count > 0 and not store_offerings:
            no_route_reason = "max_radius"
        in_radius_sids = set(store_offerings.keys())
        product_available = {
            pid: [o for o in offs if o["store_id"] in in_radius_sids]
            for pid, offs in product_available.items()
        }
        product_available = {pid: offs for pid, offs in product_available.items() if offs}

    items_not_found = [pid for pid in product_ids if pid not in product_available]
    uncovered = set(product_available.keys())
    chosen_store_ids: list[str] = []

    # greedy set cover
    while uncovered:
        if max_stops is not None and len(chosen_store_ids) >= max_stops:
            items_not_found.extend(uncovered)
            uncovered = set()
            no_route_reason = "max_stops"
            break

        best_sid = None
        best_count = 0
        best_cost = float("inf")
        best_dist = float("inf")

        for sid, offerings in store_offerings.items():
            if sid in chosen_store_ids:
                continue

            covered_pids = {o["product_id"] for o in offerings} & uncovered
            count = len(covered_pids)
            if count == 0:
                continue

            # cost of the cheapest option for each covered product at this store
            cost = sum(
                min(_effective_price(o) for o in offerings if o["product_id"] == pid)
                for pid in covered_pids
            )

            dist = float("inf")
            if user_lat is not None and user_lng is not None:
                store = offerings[0]["stores"]
                dist = _haversine(
                    user_lat, user_lng,
                    store.get("lat") or 0,
                    store.get("lng") or 0,
                )

            # pick store covering the most items, tiebreak cheapest, then closest
            if (count > best_count
                or (count == best_count and cost < best_cost)
                or (count == best_count and cost == best_cost and dist < best_dist)):
                best_sid = sid
                best_count = count
                best_cost = cost
                best_dist = dist

        if best_sid is None:
            # remaining products can't be found at any store
            items_not_found.extend(uncovered)
            break

        chosen_store_ids.append(best_sid)
        covered = {o["product_id"] for o in store_offerings[best_sid]} & uncovered
        uncovered -= covered

    # build stops with cheapest price per product at each chosen store
    stops: dict[str, dict] = {}
    assigned: set[str] = set()

    for sid in chosen_store_ids:
        for offering in store_offerings[sid]:
            pid = offering["product_id"]
            if pid in assigned or pid in items_not_found:
                continue
            # pick cheapest offering at this store for this product
            candidates = [o for o in store_offerings[sid] if o["product_id"] == pid]
            best = min(candidates, key=_effective_price)
            _assign_to_stop(stops, best)
            assigned.add(pid)

    sorted_stops = _order_stops_nearest_neighbor(
        list(stops.values()), user_lat, user_lng,
    )

    total_cost = 0.0
    for stop in sorted_stops:
        stop["subtotal"] = round(stop["subtotal"], 2)
        total_cost += stop["subtotal"]

    return {
        "total_cost": round(total_cost, 2),
        "total_distance": _compute_route_distance(sorted_stops, user_lat, user_lng),
        "stops": sorted_stops,
        "items_not_found": items_not_found,
        "no_route": len(sorted_stops) == 0,
        "no_route_reason": no_route_reason if len(sorted_stops) == 0 else None,
    }


async def optimize_shortest_distance(
    product_ids: list[str],
    user_lat: float | None = None,
    user_lng: float | None = None,
    max_stops: int | None = None,
    max_radius_miles: float | None = None,
) -> dict:
    """
    optimize a grocery list for shortest total travel distance.

    tries all store combinations of increasing size to find the set of
    stores that covers all products with the minimum total route distance.
    uses 2-opt to optimize stop ordering within each candidate set.

    tiebreaks on cost when distances are equal.
    """
    sb = get_supabase()

    result = sb.table("store_products").select(
        "id, price, sale_price, in_stock, store_id, product_id, "
        "stores(id, name, chain, store_number, zip_code, address, lat, lng), "
        "products(id, name, brand, unit_size, image_url, "
        "product_categories(slug))"
    ).in_("product_id", product_ids).eq("in_stock", True).execute()

    store_offerings: dict[str, list[dict]] = {}
    product_available: dict[str, list[dict]] = {}
    for row in result.data:
        store_offerings.setdefault(row["store_id"], []).append(row)
        product_available.setdefault(row["product_id"], []).append(row)

    no_route_reason: str | None = None

    if max_radius_miles is not None and user_lat is not None and user_lng is not None:
        pre_count = len(store_offerings)
        store_offerings = _filter_by_radius(store_offerings, user_lat, user_lng, max_radius_miles)
        if pre_count > 0 and not store_offerings:
            no_route_reason = "max_radius"
        in_radius_sids = set(store_offerings.keys())
        product_available = {
            pid: [o for o in offs if o["store_id"] in in_radius_sids]
            for pid, offs in product_available.items()
        }
        product_available = {pid: offs for pid, offs in product_available.items() if offs}

    items_not_found = [pid for pid in product_ids if pid not in product_available]
    needed = set(product_available.keys())

    # build a quick lookup: store_id -> set of product_ids it carries
    store_coverage: dict[str, set[str]] = {}
    for sid, offerings in store_offerings.items():
        store_coverage[sid] = {o["product_id"] for o in offerings} & needed

    # filter to stores that carry at least one needed product
    candidate_sids = [sid for sid, pids in store_coverage.items() if pids]

    # try combinations of increasing size to find the shortest route.
    # cap at 5 stores max to keep it fast. for typical grocery runs with
    # 5-15 candidate stores this checks at most a few thousand combos.
    best_combo = None
    best_distance = float("inf")
    best_cost = float("inf")
    max_k = min(len(candidate_sids), max_stops if max_stops is not None else 5)

    for k in range(1, max_k + 1):
        for combo in _combinations(candidate_sids, k):
            # check if this combo covers all needed products
            covered = set()
            for sid in combo:
                covered |= store_coverage[sid]
            if not covered >= needed:
                continue

            # build temporary stops to measure route distance
            temp_stops = _build_temp_stops(combo, store_offerings)
            ordered = _two_opt(
                _order_stops_nearest_neighbor(temp_stops, user_lat, user_lng),
                user_lat, user_lng,
            )
            dist = _compute_route_distance(ordered, user_lat, user_lng)

            # compute cost for this combo
            cost = _combo_cost(combo, needed, store_offerings)

            if (dist < best_distance
                or (dist == best_distance and cost < best_cost)):
                best_distance = dist
                best_cost = cost
                best_combo = combo

        # if we found a valid combo at this size, also check the next size
        # in case an extra stop actually shortens the route. but don't keep
        # searching beyond that.
        if best_combo is not None and k >= len(best_combo) + 1:
            break

    chosen_store_ids = list(best_combo) if best_combo else []

    # build stops
    stops: dict[str, dict] = {}
    assigned: set[str] = set()

    for sid in chosen_store_ids:
        for offering in store_offerings[sid]:
            pid = offering["product_id"]
            if pid in assigned or pid in items_not_found:
                continue
            if pid not in needed:
                continue
            candidates = [o for o in store_offerings[sid] if o["product_id"] == pid]
            best = min(candidates, key=_effective_price)
            _assign_to_stop(stops, best)
            assigned.add(pid)

    # apply 2-opt to improve the route ordering
    sorted_stops = _two_opt(
        list(stops.values()), user_lat, user_lng,
    )

    total_cost = 0.0
    for stop in sorted_stops:
        stop["subtotal"] = round(stop["subtotal"], 2)
        total_cost += stop["subtotal"]

    return {
        "total_cost": round(total_cost, 2),
        "total_distance": _compute_route_distance(sorted_stops, user_lat, user_lng),
        "stops": sorted_stops,
        "items_not_found": items_not_found,
        "no_route": len(sorted_stops) == 0,
        "no_route_reason": no_route_reason if len(sorted_stops) == 0 else None,
    }


def _assign_to_stop(stops: dict, offering: dict) -> None:
    """add a product offering to its store's stop."""
    store_id = offering["store_id"]
    product = offering["products"]
    category = product.get("product_categories") or {}

    item = {
        "store_product_id": offering["id"],
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


def _compute_route_distance(
    stops: list[dict],
    user_lat: float | None,
    user_lng: float | None,
) -> float:
    """total distance in miles from user through each stop in order."""
    if user_lat is None or user_lng is None:
        return 0.0
    if not stops:
        return 0.0

    total = 0.0
    prev_lat, prev_lng = user_lat, user_lng
    for stop in stops:
        store = stop["store"]
        slat = store.get("lat") or 0
        slng = store.get("lng") or 0
        total += _haversine(prev_lat, prev_lng, slat, slng)
        prev_lat, prev_lng = slat, slng
    return round(total, 2)


def _order_stops_nearest_neighbor(
    stops: list[dict],
    user_lat: float | None,
    user_lng: float | None,
) -> list[dict]:
    """order stops by nearest-neighbor starting from user location."""
    if user_lat is None or user_lng is None or len(stops) <= 1:
        return stops

    remaining = list(stops)
    ordered = []
    cur_lat, cur_lng = user_lat, user_lng

    while remaining:
        nearest = min(
            remaining,
            key=lambda s: _haversine(
                cur_lat, cur_lng,
                s["store"].get("lat") or 0,
                s["store"].get("lng") or 0,
            ),
        )
        ordered.append(nearest)
        cur_lat = nearest["store"].get("lat") or 0
        cur_lng = nearest["store"].get("lng") or 0
        remaining.remove(nearest)

    return ordered


def _two_opt(
    stops: list[dict],
    user_lat: float | None,
    user_lng: float | None,
) -> list[dict]:
    """improve stop ordering with 2-opt swaps to reduce total distance."""
    if user_lat is None or user_lng is None or len(stops) <= 2:
        return stops

    improved = list(stops)
    best_dist = _compute_route_distance(improved, user_lat, user_lng)
    changed = True

    while changed:
        changed = False
        for i in range(len(improved) - 1):
            for j in range(i + 1, len(improved)):
                candidate = improved[:i] + improved[i:j + 1][::-1] + improved[j + 1:]
                d = _compute_route_distance(candidate, user_lat, user_lng)
                if d < best_dist:
                    improved = candidate
                    best_dist = d
                    changed = True

    return improved


def _build_temp_stops(
    store_ids: tuple[str, ...],
    store_offerings: dict[str, list[dict]],
) -> list[dict]:
    """build minimal stop dicts for route distance calculation."""
    stops = []
    for sid in store_ids:
        offerings = store_offerings[sid]
        store = offerings[0]["stores"]
        stops.append({"store": store, "items": [], "subtotal": 0.0})
    return stops


def _combo_cost(
    store_ids: tuple[str, ...],
    needed: set[str],
    store_offerings: dict[str, list[dict]],
) -> float:
    """total cheapest cost of needed products across the given stores."""
    total = 0.0
    assigned: set[str] = set()
    for sid in store_ids:
        for pid in needed:
            if pid in assigned:
                continue
            candidates = [
                o for o in store_offerings[sid] if o["product_id"] == pid
            ]
            if candidates:
                total += min(_effective_price(o) for o in candidates)
                assigned.add(pid)
    return total
