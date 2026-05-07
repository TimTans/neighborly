"""google routes api client.

normalizes WALK/DRIVE/TRANSIT requests to a single shape iOS already
understands. transit cannot accept intermediate waypoints, so for multi-stop
transit we stitch N-1 leg calls in parallel and concatenate.
"""

from __future__ import annotations

import asyncio
from typing import Literal

import httpx

from app.core.config import settings


class GoogleRoutesError(RuntimeError):
    """raised when google routes api returns an error or unparseable response."""


Mode = Literal["walking", "driving", "transit"]
GOOGLE_ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"

# field mask: request only what we need to keep payload small.
# steps populate the turn-by-turn directions list under the map.
_FIELD_MASK = (
    "routes.duration,"
    "routes.distanceMeters,"
    "routes.polyline.encodedPolyline,"
    "routes.legs.duration,"
    "routes.legs.distanceMeters,"
    "routes.legs.steps.distanceMeters,"
    "routes.legs.steps.staticDuration,"
    "routes.legs.steps.travelMode,"
    "routes.legs.steps.navigationInstruction,"
    "routes.legs.steps.transitDetails"
)

_TRAVEL_MODE_MAP = {
    "walking": "WALK",
    "driving": "DRIVE",
    "transit": "TRANSIT",
}


def decode_polyline(encoded: str) -> list[list[float]]:
    """
    decode google's polyline algorithm format (precision 5) to [[lng, lat], ...].
    same encoding used by mapbox legacy and many other providers.
    """
    coords: list[list[float]] = []
    index = 0
    lat = 0
    lng = 0
    length = len(encoded)

    while index < length:
        # decode latitude delta
        result = 0
        shift = 0
        while True:
            if index >= length:
                return coords
            b = ord(encoded[index]) - 63
            index += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        dlat = ~(result >> 1) if (result & 1) else (result >> 1)
        lat += dlat

        # decode longitude delta
        result = 0
        shift = 0
        while True:
            if index >= length:
                return coords
            b = ord(encoded[index]) - 63
            index += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        dlng = ~(result >> 1) if (result & 1) else (result >> 1)
        lng += dlng

        coords.append([lng / 1e5, lat / 1e5])

    return coords


def _waypoint(lat: float, lng: float) -> dict:
    return {"location": {"latLng": {"latitude": lat, "longitude": lng}}}


def _parse_seconds(value) -> float:
    """duration fields come back as e.g. '423s'. tolerate already-numeric too."""
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        return float(value.rstrip("s"))
    return 0.0


async def _fetch_one(
    client: httpx.AsyncClient,
    origin: tuple[float, float],
    destination: tuple[float, float],
    mode: Mode,
    intermediates: list[tuple[float, float]] | None = None,
) -> dict:
    """one google routes call. mutually-exclusive with intermediates for TRANSIT."""
    body: dict = {
        "origin": _waypoint(*origin),
        "destination": _waypoint(*destination),
        "travelMode": _TRAVEL_MODE_MAP[mode],
    }
    if intermediates:
        body["intermediates"] = [_waypoint(*pt) for pt in intermediates]
    if mode == "driving":
        body["routingPreference"] = "TRAFFIC_AWARE"

    response = await client.post(
        GOOGLE_ENDPOINT,
        json=body,
        headers={
            "Content-Type": "application/json",
            "X-Goog-Api-Key": settings.GOOGLE_MAPS_API_KEY,
            "X-Goog-FieldMask": _FIELD_MASK,
        },
    )
    if response.status_code != 200:
        raise GoogleRoutesError(
            f"google routes returned {response.status_code}: {response.text[:200]}"
        )

    payload = response.json()
    routes = payload.get("routes") or []
    if not routes:
        raise GoogleRoutesError("google routes returned no routes")
    return routes[0]


async def compute_route(
    waypoints: list[tuple[float, float]],
    mode: Mode,
    client: httpx.AsyncClient | None = None,
) -> dict:
    """
    waypoints: list of (lat, lng) in visit order. first is origin, last is
    destination, anything between is intermediate.

    returns:
    {
      "coordinates": [[lng, lat], ...],   # full polyline
      "legs": [{"distance_m": float, "duration_s": float}, ...],
      "total_distance_m": float,
      "total_duration_s": float,
    }

    walking/driving: one google call with intermediates.
    transit: N-1 parallel calls (transit doesn't allow intermediates), legs and
    polylines concatenated in visit order.
    """
    if not settings.GOOGLE_MAPS_API_KEY:
        raise GoogleRoutesError("google maps api key not configured")
    if len(waypoints) < 2:
        raise GoogleRoutesError("at least two waypoints required")

    owns_client = client is None
    client = client or httpx.AsyncClient(timeout=30.0)

    try:
        if mode == "transit":
            return await _compute_transit_stitched(client, waypoints)

        # walking + driving: single call with intermediates
        origin = waypoints[0]
        destination = waypoints[-1]
        intermediates = waypoints[1:-1] if len(waypoints) > 2 else None
        route = await _fetch_one(client, origin, destination, mode, intermediates)
        return _normalize_route(route)
    finally:
        if owns_client:
            await client.aclose()


async def _compute_transit_stitched(
    client: httpx.AsyncClient,
    waypoints: list[tuple[float, float]],
) -> dict:
    """run one transit call per consecutive pair of waypoints, stitch results."""
    pairs = list(zip(waypoints[:-1], waypoints[1:]))
    tasks = [
        _fetch_one(client, origin, destination, "transit")
        for origin, destination in pairs
    ]
    leg_routes = await asyncio.gather(*tasks)

    coordinates: list[list[float]] = []
    legs: list[dict] = []
    total_distance = 0.0
    total_duration = 0.0

    for route in leg_routes:
        normalized = _normalize_route(route)
        # avoid duplicating the boundary point between concatenated legs
        if coordinates and normalized["coordinates"]:
            coordinates.extend(normalized["coordinates"][1:])
        else:
            coordinates.extend(normalized["coordinates"])
        legs.extend(normalized["legs"])
        total_distance += normalized["total_distance_m"]
        total_duration += normalized["total_duration_s"]

    return {
        "coordinates": coordinates,
        "legs": legs,
        "total_distance_m": total_distance,
        "total_duration_s": total_duration,
    }


def _normalize_route(route: dict) -> dict:
    """flatten a google routes API route into our normalized shape."""
    polyline = (route.get("polyline") or {}).get("encodedPolyline") or ""
    coordinates = decode_polyline(polyline)

    legs_out: list[dict] = []
    for leg in route.get("legs") or []:
        legs_out.append(
            {
                "distance_m": float(leg.get("distanceMeters") or 0),
                "duration_s": _parse_seconds(leg.get("duration")),
                "steps": [_normalize_step(s) for s in (leg.get("steps") or [])],
            }
        )

    return {
        "coordinates": coordinates,
        "legs": legs_out,
        "total_distance_m": float(route.get("distanceMeters") or 0),
        "total_duration_s": _parse_seconds(route.get("duration")),
    }


def _normalize_step(step: dict) -> dict:
    """flatten one google routes step into the directions-list shape iOS uses."""
    instruction_obj = step.get("navigationInstruction") or {}
    instruction = instruction_obj.get("instructions") or ""
    maneuver = instruction_obj.get("maneuver")  # e.g. "TURN_LEFT", may be None for transit

    out: dict = {
        "instruction": instruction,
        "maneuver": maneuver,
        "distance_m": float(step.get("distanceMeters") or 0),
        "duration_s": _parse_seconds(step.get("staticDuration")),
        "travel_mode": step.get("travelMode") or "WALK",
        "transit_details": None,
    }

    transit = step.get("transitDetails")
    if transit:
        line = transit.get("transitLine") or {}
        vehicle = line.get("vehicle") or {}
        stop_details = transit.get("stopDetails") or {}
        departure = stop_details.get("departureStop") or {}
        arrival = stop_details.get("arrivalStop") or {}

        out["transit_details"] = {
            "line_name": line.get("name"),
            "line_short_name": line.get("nameShort") or _short_name_from_text(transit.get("tripShortText")),
            "line_color": line.get("color"),
            "line_text_color": line.get("textColor"),
            "vehicle_type": (vehicle.get("type") or vehicle.get("vehicleType")),
            "headsign": transit.get("headsign"),
            "num_stops": transit.get("stopCount"),
            "departure_stop": departure.get("name"),
            "arrival_stop": arrival.get("name"),
        }

    return out


def _short_name_from_text(text: str | None) -> str | None:
    """trip_short_text is typically just the route number/letter; pass through."""
    if not text:
        return None
    return text.strip() or None
