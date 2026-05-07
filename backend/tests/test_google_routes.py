"""tests for google routes service and /routes/directions endpoint."""

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import google_routes
from app.services.google_routes import (
    GoogleRoutesError,
    compute_route,
    decode_polyline,
)

client = TestClient(app)


# google's canonical polyline test vector: "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
# decodes to (38.5, -120.2), (40.7, -120.95), (43.252, -126.453).
def test_decode_polyline_basic():
    decoded = decode_polyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
    assert len(decoded) == 3
    assert decoded[0][1] == pytest.approx(38.5, abs=1e-3)  # lat
    assert decoded[0][0] == pytest.approx(-120.2, abs=1e-3)  # lng
    assert decoded[1][1] == pytest.approx(40.7, abs=1e-3)
    assert decoded[1][0] == pytest.approx(-120.95, abs=1e-3)
    assert decoded[2][1] == pytest.approx(43.252, abs=1e-3)
    assert decoded[2][0] == pytest.approx(-126.453, abs=1e-3)


def test_decode_polyline_empty_returns_empty():
    assert decode_polyline("") == []


def _ok_handler(payload: dict):
    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/directions/v2:computeRoutes")
        return httpx.Response(200, json=payload)

    return handler


@pytest.mark.asyncio
async def test_compute_route_walking_single_call(monkeypatch):
    monkeypatch.setattr(google_routes.settings, "GOOGLE_MAPS_API_KEY", "test-key")

    call_count = {"n": 0}

    async def handler(request: httpx.Request) -> httpx.Response:
        call_count["n"] += 1
        body = request.read().decode()
        assert '"travelMode":"WALK"' in body or '"travelMode": "WALK"' in body
        # walking with intermediates → all in one call
        assert "intermediates" in body
        return httpx.Response(
            200,
            json={
                "routes": [
                    {
                        "duration": "600s",
                        "distanceMeters": 800,
                        "polyline": {"encodedPolyline": "_p~iF~ps|U"},
                        "legs": [
                            {
                                "duration": "300s",
                                "distanceMeters": 400,
                                "steps": [
                                    {
                                        "distanceMeters": 200,
                                        "staticDuration": "150s",
                                        "travelMode": "WALK",
                                        "navigationInstruction": {
                                            "instructions": "Head north on 5th Ave",
                                            "maneuver": "STRAIGHT",
                                        },
                                    }
                                ],
                            },
                            {"duration": "300s", "distanceMeters": 400, "steps": []},
                        ],
                    }
                ]
            },
        )

    test_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))

    result = await compute_route(
        [(40.7128, -74.0060), (40.7589, -73.9851), (40.7831, -73.9712)],
        "walking",
        client=test_client,
    )

    assert call_count["n"] == 1
    assert result["total_duration_s"] == 600
    assert result["total_distance_m"] == 800
    assert len(result["legs"]) == 2
    # first leg has one walking step; verify shape
    step = result["legs"][0]["steps"][0]
    assert step["instruction"] == "Head north on 5th Ave"
    assert step["maneuver"] == "STRAIGHT"
    assert step["travel_mode"] == "WALK"
    assert step["distance_m"] == 200
    assert step["transit_details"] is None
    await test_client.aclose()


@pytest.mark.asyncio
async def test_compute_route_driving_includes_traffic_pref(monkeypatch):
    monkeypatch.setattr(google_routes.settings, "GOOGLE_MAPS_API_KEY", "test-key")

    seen_body: dict = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        seen_body["raw"] = request.read().decode()
        return httpx.Response(
            200,
            json={
                "routes": [
                    {
                        "duration": "1200s",
                        "distanceMeters": 5000,
                        "polyline": {"encodedPolyline": "_p~iF~ps|U"},
                        "legs": [{"duration": "1200s", "distanceMeters": 5000}],
                    }
                ]
            },
        )

    test_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    await compute_route(
        [(40.7128, -74.0060), (40.7831, -73.9712)],
        "driving",
        client=test_client,
    )

    assert "TRAFFIC_AWARE" in seen_body["raw"]
    assert "DRIVE" in seen_body["raw"]
    await test_client.aclose()


@pytest.mark.asyncio
async def test_compute_route_transit_stitches_n_minus_one_calls(monkeypatch):
    """transit can't take intermediates — must split into per-leg calls."""
    monkeypatch.setattr(google_routes.settings, "GOOGLE_MAPS_API_KEY", "test-key")

    call_count = {"n": 0}

    async def handler(request: httpx.Request) -> httpx.Response:
        call_count["n"] += 1
        body = request.read().decode()
        # each transit call must NOT have intermediates
        assert "intermediates" not in body
        assert "TRANSIT" in body
        return httpx.Response(
            200,
            json={
                "routes": [
                    {
                        "duration": "500s",
                        "distanceMeters": 2000,
                        "polyline": {"encodedPolyline": "_p~iF~ps|U"},
                        "legs": [{"duration": "500s", "distanceMeters": 2000}],
                    }
                ]
            },
        )

    test_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    result = await compute_route(
        [(40.7128, -74.0060), (40.7589, -73.9851), (40.7831, -73.9712)],
        "transit",
        client=test_client,
    )

    # 3 waypoints → 2 transit calls
    assert call_count["n"] == 2
    # totals should be summed across legs
    assert result["total_duration_s"] == 1000
    assert result["total_distance_m"] == 4000
    assert len(result["legs"]) == 2
    await test_client.aclose()


@pytest.mark.asyncio
async def test_compute_route_missing_api_key_raises(monkeypatch):
    monkeypatch.setattr(google_routes.settings, "GOOGLE_MAPS_API_KEY", "")

    with pytest.raises(GoogleRoutesError):
        await compute_route([(40.0, -74.0), (40.1, -74.1)], "walking")


@pytest.mark.asyncio
async def test_compute_route_propagates_google_error(monkeypatch):
    monkeypatch.setattr(google_routes.settings, "GOOGLE_MAPS_API_KEY", "test-key")

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(403, text="permission denied")

    test_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))

    with pytest.raises(GoogleRoutesError, match="403"):
        await compute_route(
            [(40.0, -74.0), (40.1, -74.1)],
            "walking",
            client=test_client,
        )
    await test_client.aclose()


@pytest.mark.asyncio
async def test_compute_route_transit_step_extracts_line_details(monkeypatch):
    """transit step with a TransitDetails block should surface line/headsign/stops."""
    monkeypatch.setattr(google_routes.settings, "GOOGLE_MAPS_API_KEY", "test-key")

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "routes": [
                    {
                        "duration": "1800s",
                        "distanceMeters": 6000,
                        "polyline": {"encodedPolyline": "_p~iF~ps|U"},
                        "legs": [
                            {
                                "duration": "1800s",
                                "distanceMeters": 6000,
                                "steps": [
                                    {
                                        "distanceMeters": 6000,
                                        "staticDuration": "1500s",
                                        "travelMode": "TRANSIT",
                                        "navigationInstruction": {
                                            "instructions": "Take Subway Q toward Manhattan"
                                        },
                                        "transitDetails": {
                                            "headsign": "Manhattan",
                                            "stopCount": 6,
                                            "tripShortText": "Q",
                                            "transitLine": {
                                                "name": "Broadway Express",
                                                "nameShort": "Q",
                                                "color": "#FFCC00",
                                                "textColor": "#000000",
                                                "vehicle": {"type": "SUBWAY"},
                                            },
                                            "stopDetails": {
                                                "departureStop": {"name": "Atlantic Av-Barclays Ctr"},
                                                "arrivalStop": {"name": "Times Sq-42 St"},
                                            },
                                        },
                                    }
                                ],
                            }
                        ],
                    }
                ]
            },
        )

    test_client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    result = await compute_route(
        [(40.6841, -73.9778), (40.7560, -73.9866)],
        "transit",
        client=test_client,
    )

    step = result["legs"][0]["steps"][0]
    assert step["travel_mode"] == "TRANSIT"
    transit = step["transit_details"]
    assert transit["line_short_name"] == "Q"
    assert transit["vehicle_type"] == "SUBWAY"
    assert transit["headsign"] == "Manhattan"
    assert transit["num_stops"] == 6
    assert transit["departure_stop"] == "Atlantic Av-Barclays Ctr"
    assert transit["arrival_stop"] == "Times Sq-42 St"
    assert transit["line_color"] == "#FFCC00"
    await test_client.aclose()


def test_directions_route_rejects_single_waypoint():
    response = client.post(
        "/routes/directions",
        json={"waypoints": [{"lat": 40.7, "lng": -74.0}], "mode": "walking"},
    )
    assert response.status_code == 400


def test_directions_route_returns_502_on_google_error(monkeypatch):
    async def fake_compute_route(waypoints, mode, client=None):
        raise GoogleRoutesError("upstream went bad")

    monkeypatch.setattr(google_routes, "compute_route", fake_compute_route)

    response = client.post(
        "/routes/directions",
        json={
            "waypoints": [{"lat": 40.7, "lng": -74.0}, {"lat": 40.75, "lng": -74.0}],
            "mode": "walking",
        },
    )
    assert response.status_code == 502
