"""tests for route optimization helpers and algorithms."""

import pytest

from app.services.route_service import (
    _compute_route_distance,
    _filter_by_radius,
    _haversine,
    _order_stops_nearest_neighbor,
    _two_opt,
)


# -- haversine --

def test_haversine_same_point():
    assert _haversine(40.0, -74.0, 40.0, -74.0) == 0.0


def test_haversine_known_distance():
    # nyc to los angeles is roughly 2451 miles
    dist = _haversine(40.7128, -74.0060, 34.0522, -118.2437)
    assert 2440 < dist < 2470


# -- compute route distance --

def _make_stop(lat, lng):
    return {"store": {"lat": lat, "lng": lng}, "items": [], "subtotal": 0.0}


def test_route_distance_no_location():
    stops = [_make_stop(40.0, -74.0)]
    assert _compute_route_distance(stops, None, None) == 0.0


def test_route_distance_empty_stops():
    assert _compute_route_distance([], 40.0, -74.0) == 0.0


def test_route_distance_single_stop():
    stops = [_make_stop(40.0, -74.0)]
    dist = _compute_route_distance(stops, 40.0, -74.0)
    assert dist == 0.0


def test_route_distance_two_stops():
    stops = [_make_stop(40.0, -74.0), _make_stop(41.0, -74.0)]
    dist = _compute_route_distance(stops, 40.0, -74.0)
    # user at stop 1, then ~69 miles to stop 2
    assert 68 < dist < 70


# -- nearest neighbor ordering --

def test_nearest_neighbor_orders_by_proximity():
    far = _make_stop(42.0, -74.0)
    near = _make_stop(40.1, -74.0)
    mid = _make_stop(40.5, -74.0)
    result = _order_stops_nearest_neighbor([far, near, mid], 40.0, -74.0)
    assert result[0] is near
    assert result[1] is mid
    assert result[2] is far


def test_nearest_neighbor_no_location():
    stops = [_make_stop(42.0, -74.0), _make_stop(40.0, -74.0)]
    result = _order_stops_nearest_neighbor(stops, None, None)
    # returns original order when no user location
    assert result == stops


# -- two opt --

def test_two_opt_improves_or_maintains():
    # a route that's clearly suboptimal: zigzag pattern
    s1 = _make_stop(40.0, -74.0)
    s2 = _make_stop(42.0, -74.0)
    s3 = _make_stop(40.5, -74.0)
    original = [s1, s2, s3]
    original_dist = _compute_route_distance(original, 40.0, -74.0)
    improved = _two_opt(original, 40.0, -74.0)
    improved_dist = _compute_route_distance(improved, 40.0, -74.0)
    assert improved_dist <= original_dist


def test_two_opt_single_stop():
    stops = [_make_stop(40.0, -74.0)]
    result = _two_opt(stops, 40.0, -74.0)
    assert result == stops


# ========================================================================
# optimization mode tests with mocked supabase
# ========================================================================

from unittest.mock import MagicMock, patch

from app.services.route_service import (
    optimize_fewest_stops,
    optimize_lowest_cost,
    optimize_shortest_distance,
)


def _make_offering(store_id, product_id, price, sale_price, lat, lng,
                   product_name="item", store_name="store"):
    """helper to build a fake store_products row."""
    return {
        "store_id": store_id,
        "product_id": product_id,
        "price": price,
        "sale_price": sale_price,
        "in_stock": True,
        "stores": {
            "id": store_id,
            "name": store_name,
            "chain": None,
            "store_number": None,
            "zip_code": None,
            "address": None,
            "lat": lat,
            "lng": lng,
        },
        "products": {
            "id": product_id,
            "name": product_name,
            "brand": None,
            "unit_size": "1 ct",
            "image_url": None,
            "product_categories": {"slug": "grocery"},
        },
    }


def _mock_supabase(offerings):
    """patch get_supabase to return the given offerings."""
    mock_sb = MagicMock()
    mock_result = MagicMock()
    mock_result.data = offerings
    mock_sb.table.return_value.select.return_value.in_.return_value.eq.return_value.execute.return_value = mock_result
    return patch("app.services.route_service.get_supabase", return_value=mock_sb)


# -- lowest cost mode --

@pytest.mark.asyncio
async def test_lowest_cost_picks_cheapest():
    offerings = [
        _make_offering("s1", "p1", 5.00, None, 40.0, -74.0, "milk", "store a"),
        _make_offering("s2", "p1", 3.00, None, 41.0, -74.0, "milk", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_lowest_cost(["p1"], 40.0, -74.0)
    assert result["total_cost"] == 3.00
    assert len(result["stops"]) == 1
    assert result["stops"][0]["store"]["id"] == "s2"
    assert "total_distance" in result


# -- fewest stops mode --

@pytest.mark.asyncio
async def test_fewest_stops_prefers_fewer_stores():
    # store a has both products, store b has only p1 cheaper
    offerings = [
        _make_offering("s1", "p1", 5.00, None, 40.0, -74.0, "milk", "store a"),
        _make_offering("s1", "p2", 4.00, None, 40.0, -74.0, "bread", "store a"),
        _make_offering("s2", "p1", 2.00, None, 41.0, -74.0, "milk", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_fewest_stops(["p1", "p2"], 40.0, -74.0)
    # should pick store a (covers both) even though store b has cheaper milk
    assert len(result["stops"]) == 1
    assert result["stops"][0]["store"]["id"] == "s1"
    assert "total_distance" in result


@pytest.mark.asyncio
async def test_fewest_stops_tiebreaks_on_cost():
    # both stores cover both products, but store a is cheaper total
    offerings = [
        _make_offering("s1", "p1", 3.00, None, 40.0, -74.0, "milk", "store a"),
        _make_offering("s1", "p2", 2.00, None, 40.0, -74.0, "bread", "store a"),
        _make_offering("s2", "p1", 4.00, None, 40.1, -74.0, "milk", "store b"),
        _make_offering("s2", "p2", 3.00, None, 40.1, -74.0, "bread", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_fewest_stops(["p1", "p2"], 40.0, -74.0)
    assert len(result["stops"]) == 1
    assert result["stops"][0]["store"]["id"] == "s1"


# -- shortest distance mode --

@pytest.mark.asyncio
async def test_shortest_distance_picks_nearest():
    # store a is closer, store b is farther but cheaper
    offerings = [
        _make_offering("s1", "p1", 5.00, None, 40.01, -74.0, "milk", "store a"),
        _make_offering("s2", "p1", 3.00, None, 42.0, -74.0, "milk", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_shortest_distance(["p1"], 40.0, -74.0)
    # should pick store a (closer) even though store b is cheaper
    assert len(result["stops"]) == 1
    assert result["stops"][0]["store"]["id"] == "s1"
    assert "total_distance" in result


@pytest.mark.asyncio
async def test_shortest_distance_assigns_all_products_at_visited_store():
    # store a is close and has both, store b is far and has only p1
    offerings = [
        _make_offering("s1", "p1", 5.00, None, 40.01, -74.0, "milk", "store a"),
        _make_offering("s1", "p2", 4.00, None, 40.01, -74.0, "bread", "store a"),
        _make_offering("s2", "p1", 3.00, None, 42.0, -74.0, "milk", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_shortest_distance(["p1", "p2"], 40.0, -74.0)
    assert len(result["stops"]) == 1
    assert result["stops"][0]["store"]["id"] == "s1"


# -- items not found --

@pytest.mark.asyncio
async def test_items_not_found_across_modes():
    offerings = [
        _make_offering("s1", "p1", 5.00, None, 40.0, -74.0, "milk", "store a"),
    ]
    for optimize_fn in [optimize_lowest_cost, optimize_fewest_stops, optimize_shortest_distance]:
        with _mock_supabase(offerings):
            result = await optimize_fn(["p1", "p_missing"], 40.0, -74.0)
        assert "p_missing" in result["items_not_found"]
        assert result["total_cost"] == 5.00


# -- filter by radius --

def test_filter_by_radius_excludes_distant_store():
    near = [{"stores": {"lat": 40.1, "lng": -74.0}}]
    far  = [{"stores": {"lat": 42.0, "lng": -74.0}}]
    result = _filter_by_radius({"near": near, "far": far}, 40.0, -74.0, 20.0)
    assert "near" in result
    assert "far" not in result


def test_filter_by_radius_empty_when_all_beyond():
    far = [{"stores": {"lat": 45.0, "lng": -74.0}}]
    assert _filter_by_radius({"s1": far}, 40.0, -74.0, 5.0) == {}


def test_filter_by_radius_keeps_store_within_limit():
    store = [{"stores": {"lat": 40.05, "lng": -74.0}}]
    result = _filter_by_radius({"s1": store}, 40.0, -74.0, 10.0)
    assert "s1" in result


@pytest.mark.asyncio
async def test_fewest_stops_caps_at_max_stops():
    # s1 has p1 only, s2 has p2 only — need 2 stores; max_stops=1 forces 1
    offerings = [
        _make_offering("s1", "p1", 2.00, None, 40.0, -74.0, "milk",  "store a"),
        _make_offering("s2", "p2", 2.00, None, 40.1, -74.0, "bread", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_fewest_stops(["p1", "p2"], 40.0, -74.0, max_stops=1)
    assert len(result["stops"]) == 1
    assert len(result["items_not_found"]) == 1
    assert result["no_route"] is False


@pytest.mark.asyncio
async def test_fewest_stops_no_route_when_all_stores_beyond_radius():
    offerings = [_make_offering("s1", "p1", 2.00, None, 45.0, -74.0, "milk", "store a")]
    with _mock_supabase(offerings):
        result = await optimize_fewest_stops(
            ["p1"], user_lat=40.0, user_lng=-74.0, max_radius_miles=5.0
        )
    assert result["no_route"] is True
    assert result["no_route_reason"] == "max_radius"
    assert result["stops"] == []


@pytest.mark.asyncio
async def test_fewest_stops_no_route_when_max_stops_is_zero():
    offerings = [_make_offering("s1", "p1", 2.00, None, 40.0, -74.0, "milk", "store a")]
    with _mock_supabase(offerings):
        result = await optimize_fewest_stops(["p1"], 40.0, -74.0, max_stops=0)
    assert result["no_route"] is True
    assert result["no_route_reason"] == "max_stops"
    assert result["stops"] == []
    assert "p1" in result["items_not_found"]


@pytest.mark.asyncio
async def test_shortest_distance_caps_at_max_stops():
    # s1 has p1 only, s2 has p2 only — max_stops=1 means no valid combo
    offerings = [
        _make_offering("s1", "p1", 2.00, None, 40.0, -74.0, "milk",  "store a"),
        _make_offering("s2", "p2", 2.00, None, 40.1, -74.0, "bread", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_shortest_distance(["p1", "p2"], 40.0, -74.0, max_stops=1)
    # max_k=1, neither single store covers both products → no valid combo
    assert result["no_route"] is True
    assert result["stops"] == []


@pytest.mark.asyncio
async def test_shortest_distance_no_route_when_all_stores_beyond_radius():
    offerings = [_make_offering("s1", "p1", 2.00, None, 45.0, -74.0, "milk", "store a")]
    with _mock_supabase(offerings):
        result = await optimize_shortest_distance(
            ["p1"], user_lat=40.0, user_lng=-74.0, max_radius_miles=5.0
        )
    assert result["no_route"] is True
    assert result["no_route_reason"] == "max_radius"


@pytest.mark.asyncio
async def test_lowest_cost_no_route_when_too_many_stops():
    # p1 cheapest at s1, p2 cheapest at s2 → 2 stops needed; max_stops=1
    offerings = [
        _make_offering("s1", "p1", 2.00, None, 40.0, -74.0, "milk",  "store a"),
        _make_offering("s2", "p2", 2.00, None, 40.1, -74.0, "bread", "store b"),
    ]
    with _mock_supabase(offerings):
        result = await optimize_lowest_cost(["p1", "p2"], 40.0, -74.0, max_stops=1)
    assert result["no_route"] is True
    assert result["no_route_reason"] == "max_stops"
    assert result["stops"] == []


@pytest.mark.asyncio
async def test_lowest_cost_no_route_when_all_stores_beyond_radius():
    offerings = [_make_offering("s1", "p1", 2.00, None, 45.0, -74.0, "milk", "store a")]
    with _mock_supabase(offerings):
        result = await optimize_lowest_cost(
            ["p1"], user_lat=40.0, user_lng=-74.0, max_radius_miles=5.0
        )
    assert result["no_route"] is True
    assert result["no_route_reason"] == "max_radius"
