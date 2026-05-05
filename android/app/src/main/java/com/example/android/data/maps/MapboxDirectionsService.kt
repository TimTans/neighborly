package com.example.android.data.maps

import com.example.android.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single leg returned from the Mapbox Directions API. Distances are in
 * meters and durations in seconds, matching the API contract directly so the
 * caller can compute its own ETAs (e.g. `max(1, duration / 60)` minutes).
 */
data class DirectionsLeg(
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Walking directions result. `coordinates` is the full GeoJSON line geometry
 * decoded into (latitude, longitude) pairs for direct rendering on a map. Per
 * the iOS implementation we request `geometries=geojson&overview=full`.
 */
data class DirectionsResult(
    val coordinates: List<Pair<Double, Double>>,
    val legs: List<DirectionsLeg>
)

/**
 * Lightweight client for the Mapbox Directions API, matching the iOS
 * `MapboxDirectionsService.getWalkingRoute` contract.
 *
 * Waypoints are passed as `(longitude, latitude)` pairs to mirror the Mapbox
 * URL convention (`{lng,lat;lng,lat;...}`).
 */
interface MapboxDirectionsService {
    suspend fun walkingRoute(waypoints: List<Pair<Double, Double>>): Result<DirectionsResult>
}

class KtorMapboxDirectionsService(
    private val accessToken: String = BuildConfig.MAPBOX_ACCESS_TOKEN,
    private val client: HttpClient = defaultHttpClient()
) : MapboxDirectionsService {

    override suspend fun walkingRoute(
        waypoints: List<Pair<Double, Double>>
    ): Result<DirectionsResult> {
        if (waypoints.size < 2) {
            return Result.failure(IllegalArgumentException("Need at least 2 waypoints"))
        }
        if (accessToken.isBlank()) {
            return Result.failure(IllegalStateException("MAPBOX_ACCESS_TOKEN is not set"))
        }

        val coordString = waypoints.joinToString(";") { (lng, lat) -> "$lng,$lat" }
        val url = "https://api.mapbox.com/directions/v5/mapbox/walking/$coordString" +
            "?geometries=geojson&overview=full&steps=false&access_token=$accessToken"

        return try {
            val response: HttpResponse = client.get(url)
            if (!response.status.isSuccess()) {
                return Result.failure(
                    RuntimeException("Mapbox Directions HTTP ${response.status.value}: ${response.safeBody()}")
                )
            }
            val payload: DirectionsResponse = response.body()
            val route = payload.routes.firstOrNull()
                ?: return Result.failure(RuntimeException("Mapbox Directions returned no routes"))

            val coords = route.geometry.coordinates.mapNotNull { pair ->
                if (pair.size >= 2) pair[1] to pair[0] else null // GeoJSON is [lng,lat]
            }
            val legs = route.legs.map {
                DirectionsLeg(distanceMeters = it.distance, durationSeconds = it.duration)
            }
            Result.success(DirectionsResult(coordinates = coords, legs = legs))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (io: IOException) {
            Result.failure(io)
        } catch (decode: JsonConvertException) {
            Result.failure(decode)
        } catch (decode: SerializationException) {
            Result.failure(decode)
        } catch (other: Throwable) {
            Result.failure(other)
        }
    }

    private suspend fun HttpResponse.safeBody(): String? =
        runCatching { bodyAsText().takeIf { it.isNotBlank() } }.getOrNull()

    @Serializable
    private data class DirectionsResponse(val routes: List<RouteDto> = emptyList())

    @Serializable
    private data class RouteDto(
        val geometry: GeometryDto,
        val legs: List<LegDto> = emptyList()
    )

    @Serializable
    private data class GeometryDto(val coordinates: List<List<Double>> = emptyList())

    @Serializable
    private data class LegDto(val distance: Double, val duration: Double)

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    }
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }
}
