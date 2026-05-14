package com.example.android.data.repository.route

import com.example.android.viewmodel.route.RoutePlan

interface RouteRepository {
    suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?,
        mode: String? = null,
        maxStops: Int? = null,
        maxRadiusMiles: Double? = null
    ): Result<RoutePlan>

    suspend fun getSwapAlternatives(productId: String): Result<List<RouteSwapOption>>
}

data class RouteSwapOption(
    val productId: String,
    val name: String,
    val storeName: String?,
    val price: Double?,
    val reason: String?
)

/**
 * Fallback / test seam used before [ApiRouteRepository] was wired up. Kept around
 * so tests and offline scenarios can opt out of network calls without throwing
 * surprises at runtime — production code should depend on [ApiRouteRepository].
 */
class UnavailableRouteRepository : RouteRepository {
    override suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?,
        mode: String?,
        maxStops: Int?,
        maxRadiusMiles: Double?
    ): Result<RoutePlan> = Result.failure(
        IllegalStateException(
            "Route optimization API is not wired in this build. Inject ApiRouteRepository instead."
        )
    )

    override suspend fun getSwapAlternatives(productId: String): Result<List<RouteSwapOption>> =
        Result.failure(
            IllegalStateException(
                "Swap alternatives API is not wired in this build. Inject ApiRouteRepository instead."
            )
        )
}
