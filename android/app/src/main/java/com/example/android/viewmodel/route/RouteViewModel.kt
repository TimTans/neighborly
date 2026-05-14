package com.example.android.viewmodel.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.repository.route.ApiRouteRepository
import com.example.android.data.repository.route.RouteRepository
import com.example.android.data.repository.route.RouteSwapOption
import kotlinx.coroutines.launch

data class RoutePlan(
    val totalCost: Double?,
    val totalDistanceMiles: Double?,
    val estimatedDurationMinutes: Int?,
    val savings: Double?,
    val stops: List<OptimizedRouteStop>,
    val missingItems: List<RouteMissingItem> = emptyList()
) {
    val stopCountLabel: String
        get() = when (stops.size) {
            0 -> "No stops"
            1 -> "1 stop"
            else -> "${stops.size} stops"
        }

    val itemCount: Int
        get() = stops.sumOf { stop -> stop.items.sumOf { item -> item.quantity } }

    val itemCountLabel: String
        get() = when (itemCount) {
            0 -> "No items"
            1 -> "1 item"
            else -> "$itemCount items"
        }
}

data class OptimizedRouteStop(
    val index: Int,
    val storeId: String?,
    val storeName: String,
    val address: String?,
    val distanceMiles: Double?,
    val estimatedDurationMinutes: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val items: List<RouteStopItem>
) {
    val subtotal: Double?
        get() {
            val pricedItems = items.filter { it.price != null }
            if (pricedItems.isEmpty()) return null
            return pricedItems.sumOf { item -> item.price!! * item.quantity }
        }
}

data class RouteStopItem(
    val productId: String?,
    val name: String,
    val quantity: Int,
    val unitSize: String?,
    val price: Double?,
    val originalPrice: Double?,
    val swapAvailable: Boolean = false,
    val imageUrl: String? = null,
    val categorySlug: String? = null
)

data class RouteMissingItem(
    val productId: String?,
    val name: String,
    val reason: String?
)

data class RouteUiState(
    val optimizedRoute: RoutePlan? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pendingProductIds: List<String> = emptyList(),
    val selectedSwapProductId: String? = null,
    val swapOptions: List<RouteSwapOption> = emptyList(),
    val isLoadingSwapOptions: Boolean = false,
    val swapErrorMessage: String? = null
) {
    val hasRoute: Boolean
        get() = optimizedRoute != null
}

class RouteViewModel(
    private val routeRepository: RouteRepository = ApiRouteRepository()
) : ViewModel() {
    var uiState by mutableStateOf(RouteUiState())
        private set

    fun createRoute(
        productIds: List<String>,
        userLat: Double? = null,
        userLng: Double? = null,
        mode: String? = null,
        maxStops: Int? = null,
        maxRadiusMiles: Double? = null
    ) {
        setPendingProducts(productIds)
        optimizePendingRoute(userLat, userLng, mode, maxStops, maxRadiusMiles)
    }

    fun setPendingProducts(productIds: List<String>) {
        uiState = uiState.copy(pendingProductIds = productIds.distinct(), errorMessage = null)
    }

    fun submitOptimizedRoute(routePlan: RoutePlan) {
        uiState = uiState.copy(
            optimizedRoute = routePlan,
            isLoading = false,
            errorMessage = null,
            pendingProductIds = emptyList()
        )
    }

    fun optimizePendingRoute(
        userLat: Double? = null,
        userLng: Double? = null,
        mode: String? = null,
        maxStops: Int? = null,
        maxRadiusMiles: Double? = null
    ) {
        val productIds = uiState.pendingProductIds
        if (productIds.isEmpty()) {
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = "Add grocery items with product IDs before optimizing a route."
            )
            return
        }

        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            routeRepository.optimizeRoute(
                productIds = productIds,
                userLat = userLat,
                userLng = userLng,
                mode = mode,
                maxStops = maxStops,
                maxRadiusMiles = maxRadiusMiles
            )
                .onSuccess { submitOptimizedRoute(it) }
                .onFailure { failure ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = failure.message ?: "Unable to optimize route."
                    )
                }
        }
    }

    fun loadSwapAlternatives(productId: String?) {
        if (productId.isNullOrBlank()) {
            uiState = uiState.copy(
                selectedSwapProductId = null,
                swapOptions = emptyList(),
                isLoadingSwapOptions = false,
                swapErrorMessage = "This item does not have a product ID for swap lookup."
            )
            return
        }

        uiState = uiState.copy(
            selectedSwapProductId = productId,
            swapOptions = emptyList(),
            isLoadingSwapOptions = true,
            swapErrorMessage = null
        )
        viewModelScope.launch {
            routeRepository.getSwapAlternatives(productId)
                .onSuccess { options ->
                    uiState = uiState.copy(
                        swapOptions = options,
                        isLoadingSwapOptions = false,
                        swapErrorMessage = null
                    )
                }
                .onFailure { failure ->
                    uiState = uiState.copy(
                        swapOptions = emptyList(),
                        isLoadingSwapOptions = false,
                        swapErrorMessage = failure.message ?: "Unable to load swap alternatives."
                    )
                }
        }
    }

    fun dismissSwapAlternatives() {
        uiState = uiState.copy(
            selectedSwapProductId = null,
            swapOptions = emptyList(),
            isLoadingSwapOptions = false,
            swapErrorMessage = null
        )
    }

    fun clearRoute() {
        uiState = RouteUiState()
    }
}
