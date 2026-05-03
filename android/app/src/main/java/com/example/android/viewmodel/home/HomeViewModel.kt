package com.example.android.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.android.viewmodel.route.OptimizedRouteStop
import com.example.android.viewmodel.route.RoutePlan

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
    val metrics: List<HomeMetric> = emptyList()
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

class HomeViewModel : ViewModel() {
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
}
