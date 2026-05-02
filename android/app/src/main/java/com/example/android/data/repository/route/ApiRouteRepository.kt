package com.example.android.data.repository.route

import com.example.android.data.api.KtorNeighborlyApi
import com.example.android.data.api.NeighborlyApi
import com.example.android.data.model.OptimizedRoute
import com.example.android.data.model.Product
import com.example.android.data.model.RouteItem
import com.example.android.data.model.RouteStop
import com.example.android.viewmodel.route.OptimizedRouteStop
import com.example.android.viewmodel.route.RouteMissingItem
import com.example.android.viewmodel.route.RoutePlan
import com.example.android.viewmodel.route.RouteStopItem

/**
 * Production [RouteRepository] backed by [NeighborlyApi]. Maps the raw
 * `OptimizedRoute` / `Product` DTOs returned by the backend into the domain
 * types consumed by `RouteViewModel`.
 *
 * Errors from the API ([com.example.android.data.api.NeighborlyApiError])
 * propagate through the underlying [Result] without rewrapping — callers can
 * still pattern-match on the sealed error type if they need to.
 */
class ApiRouteRepository(
    private val api: NeighborlyApi = KtorNeighborlyApi()
) : RouteRepository {

    override suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?,
        mode: String?,
        maxStops: Int?,
        maxRadiusMiles: Double?
    ): Result<RoutePlan> = api.optimizeRoute(
        productIds = productIds,
        userLat = userLat,
        userLng = userLng,
        mode = mode,
        maxStops = maxStops,
        maxRadiusMiles = maxRadiusMiles
    ).map { it.toRoutePlan() }

    override suspend fun getSwapAlternatives(productId: String): Result<List<RouteSwapOption>> =
        api.getAlternatives(productId).map { products ->
            products.map { it.toSwapOption() }
        }

    private fun OptimizedRoute.toRoutePlan(): RoutePlan = RoutePlan(
        totalCost = totalCost,
        totalDistanceMiles = null,
        estimatedDurationMinutes = null,
        savings = null,
        stops = stops.mapIndexed { index, stop -> stop.toOptimizedRouteStop(index + 1) },
        missingItems = itemsNotFound.map { name ->
            RouteMissingItem(
                productId = null,
                name = name,
                reason = null
            )
        }
    )

    private fun RouteStop.toOptimizedRouteStop(index: Int): OptimizedRouteStop =
        OptimizedRouteStop(
            index = index,
            storeId = store.id,
            storeName = store.name,
            address = store.address,
            distanceMiles = null,
            estimatedDurationMinutes = null,
            latitude = store.lat,
            longitude = store.lng,
            items = items.map { it.toRouteStopItem() }
        )

    private fun RouteItem.toRouteStopItem(): RouteStopItem {
        val effectivePrice = salePrice ?: price
        val originalPrice = if (salePrice != null) price else null
        return RouteStopItem(
            productId = productId,
            name = name,
            quantity = 1,
            unitSize = unitSize,
            price = effectivePrice,
            originalPrice = originalPrice,
            swapAvailable = true,
            imageUrl = imageUrl,
            categorySlug = categorySlug
        )
    }

    private fun Product.toSwapOption(): RouteSwapOption = RouteSwapOption(
        productId = id,
        name = name,
        storeName = bestPriceStoreName,
        price = bestPrice,
        reason = null
    )
}
