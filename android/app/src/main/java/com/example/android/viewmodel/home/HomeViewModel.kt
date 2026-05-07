package com.example.android.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.model.RecipeRequestPayload
import com.example.android.data.model.RecipeSuggestion
import com.example.android.data.repository.ApiRecipeRepository
import com.example.android.data.repository.RecipeRepository
import com.example.android.viewmodel.route.OptimizedRouteStop
import com.example.android.viewmodel.route.RoutePlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class HomeMetric(
    val label: String,
    val value: String,
    val sublabel: String
)

data class RouteStop(
    val index: Int,
    val name: String,
    val address: String,
    val distance: String,
    val timeEstimate: String,
    val itemsLabel: String
)

data class HomeUiState(
    val userName: String = "John Doe",
    val savingsThisTrip: String = "$6.70",
    val totalBudget: String = "$120.00",
    val budgetUsed: String = "$57.31",
    val savedThisMonth: String = "$42.80",
    val savedThisMonthLabel: String = "this month",
    val avgTripTime: String = "34m",
    val milesSaved: String = "12.4",
    val itemsTracked: String = "89",
    val itemsTrackedLabel: String = "tracked",
    val alertsCount: String = "3",
    val optimizedStopsLabel: String = "3 stops",
    val metrics: List<HomeMetric> = emptyList(),
    val featuredRecipe: RecipeSuggestion? = null,
    val isLoadingRecipe: Boolean = false,
    val recipeError: String? = null
)

/**
 * Demo route preview shown on Home when no real optimized route is available yet.
 * Mirrors the original hardcoded sample so the screen still has visual content for
 * a fresh user. Once `RouteViewModel.uiState.optimizedRoute` is non-null, the
 * real route is rendered instead — see [HomeScreen].
 */
val DemoRouteStops: List<RouteStop> = listOf(
    RouteStop(1, "Aldi", "142 Atlantic Ave", "0.8 mi", "12 min", "3 items"),
    RouteStop(2, "Trader Joe's", "130 Court St", "1.2 mi", "8 min", "4 items"),
    RouteStop(3, "Costco", "976 3rd Ave", "2.4 mi", "15 min", "2 items")
)

/**
 * Map up to the first three stops of a real optimized route into the lightweight
 * [RouteStop] preview shape used on Home. Distance / time are not yet available
 * on `OptimizedRouteStop` (Mapbox wires that up in S1.5), so we leave them blank.
 * Items label uses the per-stop quantity sum.
 */
fun previewStopsFrom(activeRoute: RoutePlan): List<RouteStop> {
    return activeRoute.stops.take(PREVIEW_STOP_LIMIT).mapIndexed { index, stop ->
        RouteStop(
            index = index + 1,
            name = stop.storeName,
            address = stop.address ?: "Address not yet available",
            distance = "",
            timeEstimate = "",
            itemsLabel = stop.itemsLabel()
        )
    }
}

private const val PREVIEW_STOP_LIMIT = 3

private fun OptimizedRouteStop.itemsLabel(): String {
    val total = items.sumOf { it.quantity }
    return when (total) {
        0 -> "No items"
        1 -> "1 item"
        else -> "$total items"
    }
}

class HomeViewModel(
    private val recipeRepository: RecipeRepository = ApiRecipeRepository()
) : ViewModel() {
    var uiState by mutableStateOf(
        HomeUiState(
            metrics = listOf(
                HomeMetric("Budget", "$57.31", "of $120.00"),
                HomeMetric("Saved", "$42.80", "this month"),
                HomeMetric("Avg trip", "34m", ""),
                HomeMetric("Miles saved", "12.4", "miles saved"),
                HomeMetric("Items", "89", "tracked"),
                HomeMetric("Alerts", "3", "alerts")
            )
        )
    )
        private set

    private var lastPayload: RecipeRequestPayload? = null
    private var recipeJob: Job? = null

    /**
     * Generate (or refresh) the featured recipe. Mirrors iOS
     * `HomeViewModel.loadRecipe` (HomeViewModel.swift:56-74): if the payload
     * matches the last one served and we already have a recipe, skip the call
     * to avoid a loading flicker. The repository also caches by payload, so a
     * `force = true` always re-requests.
     */
    fun refreshRecipe(payload: RecipeRequestPayload, force: Boolean = false) {
        if (!force && payload == lastPayload && uiState.featuredRecipe != null) {
            return
        }
        recipeJob?.cancel()
        uiState = uiState.copy(isLoadingRecipe = true, recipeError = null)
        recipeJob = viewModelScope.launch {
            val result = recipeRepository.generate(payload, force)
            result.fold(
                onSuccess = { recipe ->
                    lastPayload = payload
                    uiState = uiState.copy(
                        featuredRecipe = recipe,
                        isLoadingRecipe = false,
                        recipeError = null
                    )
                },
                onFailure = { error ->
                    uiState = uiState.copy(
                        isLoadingRecipe = false,
                        recipeError = error.message ?: "Unable to generate a recipe right now."
                    )
                }
            )
        }
    }

    /** Force-regenerate the recipe regardless of cached payload. */
    fun retryRecipe(payload: RecipeRequestPayload) {
        refreshRecipe(payload, force = true)
    }
}
