package com.example.android.ui.route

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.data.location.UserCoordinates
import com.example.android.data.maps.DirectionsResult
import com.example.android.data.maps.KtorMapboxDirectionsService
import com.example.android.data.maps.MapboxDirectionsService
import com.example.android.viewmodel.route.OptimizedRouteStop
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.IconImage
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolylineAnnotation
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions

private val PolylineGreen = Color(0xFF1FA86E)
private val BadgeOrange = Color(0xFFE67E22)
private val BadgeOrangeSoft = Color(0xFFFFF3E0)
private val NeighborlyGreen = Color(0xFF0C6A4A)

/**
 * Compose wrapper around the Mapbox Maps SDK matching the iOS
 * `MapboxRouteMap.swift`:
 *   - Numbered store pins (1..N) drawn as bitmaps with a green circle and
 *     downward-pointing tail.
 *   - User-location 2D puck with bearing.
 *   - Walking polyline from the Mapbox Directions API (green, 0.8 opacity);
 *     falls back to straight lines between stops if the API fails.
 *   - Per-leg ETA badges ("X min") rendered as Compose [ViewAnnotation]s
 *     above each destination pin. Choosing view annotations keeps the badge
 *     legible at any zoom level and reuses Compose typography.
 *   - Camera fit to bounds of [user, stops] with 40dp padding via the
 *     `MapboxMap.cameraForCoordinates` helper, animated with `flyTo`.
 *   - Tapping a pin zooms to that single stop; an "All stops" pill in the
 *     top-end corner returns to the fit-all viewport (mirrors iOS behavior).
 */
@Composable
fun MapboxRouteMap(
    stops: List<OptimizedRouteStop>,
    userLocation: UserCoordinates?,
    modifier: Modifier = Modifier,
    directionsService: MapboxDirectionsService = remember { KtorMapboxDirectionsService() }
) {
    val viewportState = rememberMapViewportState()
    var directions by remember { mutableStateOf<DirectionsResult?>(null) }
    // null = fit all stops; non-null = zoomed to a single tapped pin's index
    var focusedStopIndex by remember(stops) { mutableStateOf<Int?>(null) }

    val pinPoints = remember(stops) {
        stops.mapNotNull { stop ->
            val lat = stop.latitude
            val lng = stop.longitude
            if (lat != null && lng != null) Triple(stop, lat, lng) else null
        }
    }

    // Fetch directions whenever stops or user location change. On failure we
    // leave `directions = null` and the polyline below falls back to straight
    // lines between stops. Mirrors iOS `fetchRoute()`.
    LaunchedEffect(stops, userLocation) {
        directions = null
        val waypoints = buildList<Pair<Double, Double>> {
            userLocation?.let { add(it.longitude to it.latitude) }
            pinPoints.forEach { (_, lat, lng) -> add(lng to lat) }
        }
        if (waypoints.size >= 2) {
            directionsService.walkingRoute(waypoints)
                .onSuccess { directions = it }
        }
    }

    val polylinePoints: List<Point> = remember(directions, pinPoints, userLocation) {
        directions?.coordinates?.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
            ?: buildList {
                userLocation?.let { add(Point.fromLngLat(it.longitude, it.latitude)) }
                pinPoints.forEach { (_, lat, lng) -> add(Point.fromLngLat(lng, lat)) }
            }
    }

    // Pre-render each numbered pin bitmap once per stop count change.
    val pinBitmaps: Map<Int, Bitmap> = remember(pinPoints.size) {
        (1..pinPoints.size).associateWith { renderPinBitmap(it) }
    }
    val pinIcons: Map<Int, IconImage> = remember(pinBitmaps) {
        pinBitmaps.mapValues { (_, bmp) -> IconImage(bmp) }
    }

    Box(modifier = modifier) {
        MapboxMap(
            modifier = Modifier.matchParentSize(),
            mapViewportState = viewportState,
            style = { MapStyle(style = "mapbox://styles/mapbox/streets-v12") }
        ) {
            // Enable the user-location puck and recompute camera bounds once the
            // map view exists. Recomputes when `focusedStopIndex` flips so a
            // pin tap (or "All stops" reset) animates the camera.
            MapEffect(stops, userLocation, focusedStopIndex) { mapView ->
                mapView.location.updateSettings {
                    enabled = true
                    locationPuck = createDefault2DPuck(withBearing = true)
                    puckBearingEnabled = true
                }

                val focused = focusedStopIndex
                if (focused != null) {
                    val pin = pinPoints.getOrNull(focused)
                    if (pin != null) {
                        val (_, lat, lng) = pin
                        val targetCamera = CameraOptions.Builder()
                            .center(Point.fromLngLat(lng, lat))
                            .zoom(15.0)
                            .build()
                        viewportState.flyTo(targetCamera)
                        return@MapEffect
                    }
                }

                val coords = buildList<Point> {
                    userLocation?.let { add(Point.fromLngLat(it.longitude, it.latitude)) }
                    pinPoints.forEach { (_, lat, lng) -> add(Point.fromLngLat(lng, lat)) }
                }
                if (coords.isEmpty()) return@MapEffect

                val targetCamera = if (coords.size == 1) {
                    CameraOptions.Builder()
                        .center(coords.first())
                        .zoom(14.0)
                        .build()
                } else {
                    // v11 non-deprecated overload: takes a CameraOptions anchor
                    // alongside coordinates / padding. Pass an empty anchor so
                    // Mapbox computes both center and zoom from the bounds.
                    mapView.mapboxMap.cameraForCoordinates(
                        coords,
                        CameraOptions.Builder().build(),
                        EdgeInsets(40.0, 40.0, 40.0, 40.0),
                        null,
                        null
                    )
                }
                viewportState.flyTo(targetCamera)
            }

            // Walking polyline (real or straight-line fallback). Always shown
            // regardless of focus state.
            if (polylinePoints.size >= 2) {
                PolylineAnnotation(points = polylinePoints) {
                    lineColor = PolylineGreen
                    lineWidth = 4.0
                    lineOpacity = 0.8
                }
            }

            // Numbered pins. Per-pin onClick captures the tapped index so the
            // camera can fly to that single stop.
            pinPoints.forEachIndexed { index, (_, lat, lng) ->
                val displayNumber = index + 1
                val point = Point.fromLngLat(lng, lat)
                val icon = pinIcons[displayNumber] ?: return@forEachIndexed

                PointAnnotation(
                    point = point,
                    onClick = {
                        focusedStopIndex = index
                        // Returning true marks the click handled and prevents
                        // any further click propagation on the map.
                        true
                    }
                ) {
                    iconImage = icon
                    iconAnchor = IconAnchor.BOTTOM
                }

                // Per-leg ETA badge: walking duration from the previous waypoint
                // (user when present, otherwise previous stop). Always shown.
                val legIndex = if (userLocation != null) index else index - 1
                val durationSeconds = directions?.legs?.getOrNull(legIndex)?.durationSeconds
                if (durationSeconds != null) {
                    val minutes = maxOf(1, (durationSeconds / 60.0).toInt())
                    ViewAnnotation(
                        options = viewAnnotationOptions {
                            geometry(point)
                            allowOverlap(true)
                        }
                    ) {
                        EtaBadge(minutes = minutes)
                    }
                }
            }
        }

        // "All stops" reset pill, top-end corner. Only visible when zoomed
        // to a single pin. Mirrors iOS `MapboxRouteMap.swift` lines 93-110.
        if (focusedStopIndex != null) {
            AllStopsPill(
                onClick = { focusedStopIndex = null },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun AllStopsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(NeighborlyGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.wrapContentSize()
        )
        Text(
            text = "All stops",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
    }
}

@Composable
private fun EtaBadge(minutes: Int) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(50))
            .background(BadgeOrangeSoft)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$minutes min",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = BadgeOrange
        )
    }
}

/**
 * Render a numbered pin bitmap matching the iOS `PinRenderer`: 30dp green
 * circle + downward triangle tail with the index drawn in white.
 */
private fun renderPinBitmap(number: Int): Bitmap {
    val width = 60
    val pinDiameter = 30
    val triHeight = 6
    val totalHeight = pinDiameter + triHeight
    val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val greenColor = android.graphics.Color.argb(255, 12, 106, 74)

    val circleX = (width - pinDiameter) / 2f
    val circleY = 0f

    val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = greenColor
        setShadowLayer(2f, 0f, 1f, android.graphics.Color.argb(64, 0, 0, 0))
    }
    canvas.drawCircle(
        circleX + pinDiameter / 2f,
        circleY + pinDiameter / 2f,
        pinDiameter / 2f,
        circlePaint
    )

    val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 16f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val textBaseline = circleY + pinDiameter / 2f - (numberPaint.descent() + numberPaint.ascent()) / 2f
    canvas.drawText(number.toString(), circleX + pinDiameter / 2f, textBaseline, numberPaint)

    val triTop = circleY + pinDiameter - 1f
    val triPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = greenColor }
    val triPath = Path().apply {
        moveTo(width / 2f, triTop + triHeight)
        lineTo(width / 2f - 5f, triTop)
        lineTo(width / 2f + 5f, triTop)
        close()
    }
    canvas.drawPath(triPath, triPaint)

    return bitmap
}
