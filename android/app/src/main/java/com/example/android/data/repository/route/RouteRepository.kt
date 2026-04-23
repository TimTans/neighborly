package com.example.android.data.repository.route

import com.example.android.viewmodel.route.RoutePlan

interface RouteRepository {
    suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?
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
 * Temporary Epic C seam while Worker A/B land API and persisted grocery-list plumbing.
 * Replace this with a repository backed by NeighborlyApi.optimizeRoute/getAlternatives.
 */
class UnavailableRouteRepository : RouteRepository {
    override suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?
    ): Result<RoutePlan> = Result.failure(
        IllegalStateException(
            "Route optimization API is not wired yet. Connect NeighborlyApi.optimizeRoute once Epic A4 is available."
        )
    )

    override suspend fun getSwapAlternatives(productId: String): Result<List<RouteSwapOption>> =
        Result.failure(
            IllegalStateException(
                "Swap alternatives API is not wired yet. Connect NeighborlyApi.getAlternatives once Epic A4 is available."
            )
        )
}
