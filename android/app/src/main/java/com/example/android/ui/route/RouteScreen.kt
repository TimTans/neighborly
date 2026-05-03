package com.example.android.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.android.data.model.categoryEmojiForSlug
import com.example.android.data.repository.route.RouteSwapOption
import com.example.android.ui.components.ProductImage
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.viewmodel.route.OptimizedRouteStop
import com.example.android.viewmodel.route.RouteMissingItem
import com.example.android.viewmodel.route.RoutePlan
import com.example.android.viewmodel.route.RouteStopItem
import com.example.android.viewmodel.route.RouteViewModel
import com.example.android.viewmodel.shopper.ShopperViewModel

private val NeighborlyBackground = NeighborlyColors.Background
private val NeighborlyGreen = NeighborlyColors.Green
private val NeighborlyGreenSoft = NeighborlyColors.GreenSoft
private val NeighborlyOrange = NeighborlyColors.Orange
private val NeighborlyInk = NeighborlyColors.TextPrimary

@Composable
fun RouteScreen(
    routeViewModel: RouteViewModel,
    shopperViewModel: ShopperViewModel,
    modifier: Modifier = Modifier
) {
    val state = routeViewModel.uiState

    Surface(modifier = modifier.fillMaxSize(), color = NeighborlyBackground) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Optimized Route",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = NeighborlyInk,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                when {
                    state.isLoading -> RouteLoadingCard()
                    state.optimizedRoute == null -> EmptyRouteCard(
                        message = state.errorMessage,
                        hasPendingProducts = state.pendingProductIds.isNotEmpty(),
                        onRetry = routeViewModel::optimizePendingRoute
                    )
                    else -> RouteContent(
                        route = state.optimizedRoute,
                        errorMessage = state.errorMessage,
                        onRetry = routeViewModel::optimizePendingRoute,
                        onSwapItem = routeViewModel::loadSwapAlternatives
                    )
                }
            }

            if (
                state.selectedSwapProductId != null ||
                state.isLoadingSwapOptions ||
                state.swapErrorMessage != null
            ) {
                SwapAlternativesDialog(
                    isLoading = state.isLoadingSwapOptions,
                    options = state.swapOptions,
                    errorMessage = state.swapErrorMessage,
                    onDismiss = routeViewModel::dismissSwapAlternatives,
                    onSelectOption = { option ->
                        // The route's selected swap product id is the *current* product id of
                        // the item being swapped. Resolve it back to the persisted grocery-list
                        // record id so the repository knows which row to mutate. If the item
                        // has no product id (e.g. legacy entries), there is nothing to swap.
                        val currentProductId = state.selectedSwapProductId
                        val currentItemId = shopperViewModel.findItemIdByProductId(currentProductId)
                        if (currentItemId == null) {
                            routeViewModel.dismissSwapAlternatives()
                            return@SwapAlternativesDialog
                        }
                        val preferences = shopperViewModel.uiState.preferences
                        val mode = preferences.priority.toBackendMode()
                        val maxStops = preferences.maxStops.toInt().takeIf { it < 11 }
                        val maxRadiusMiles = preferences.maxTravelDistanceMiles.toDouble()
                            .takeIf { preferences.maxTravelDistanceMiles.toInt() < 11 }
                        shopperViewModel.applySwap(currentItemId, option.productId) { newProductIds ->
                            routeViewModel.setPendingProducts(newProductIds)
                            routeViewModel.optimizePendingRoute(
                                mode = mode,
                                maxStops = maxStops,
                                maxRadiusMiles = maxRadiusMiles,
                            )
                        }
                        routeViewModel.dismissSwapAlternatives()
                    }
                )
            }
        }
    }
}

@Composable
private fun RouteContent(
    route: RoutePlan,
    errorMessage: String?,
    onRetry: () -> Unit,
    onSwapItem: (String?) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            RouteSummaryCard(route = route, errorMessage = errorMessage, onRetry = onRetry)
        }

        item {
            MapboxRouteMap(
                stops = route.stops,
                userLocation = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
        }

        items(route.stops, key = { stop -> "${stop.index}-${stop.storeId}-${stop.storeName}" }) { stop ->
            RouteStopCard(stop = stop, onSwapItem = onSwapItem)
        }

        if (route.missingItems.isNotEmpty()) {
            item {
                MissingItemsCard(items = route.missingItems)
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RouteSummaryCard(route: RoutePlan, errorMessage: String?, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total trip cost", style = MaterialTheme.typography.bodyMedium, color = NeighborlyColors.RouteSummaryLabel)
                    Text(
                        text = route.totalCost.formatMoneyOrDash(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = NeighborlyGreen
                    )
                }
                OutlinedButton(onClick = onRetry, enabled = route.stops.isNotEmpty()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Refresh")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryPill(text = route.stopCountLabel)
                SummaryPill(text = route.itemCountLabel)
                route.estimatedDurationMinutes?.let { SummaryPill(text = "$it min") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                route.totalDistanceMiles?.let { SummaryPill(text = "${"%.1f".format(it)} mi") }
                route.savings?.let { SummaryPill(text = "${it.formatMoneyOrDash()} saved") }
            }

            if (errorMessage != null) {
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = NeighborlyOrange)
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(NeighborlyGreenSoft)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = NeighborlyGreen)
    }
}

@Composable
private fun RouteStopCard(stop: OptimizedRouteStop, onSwapItem: (String?) -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                StopNumber(index = stop.index)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stop.storeName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    stop.address?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextSecondary)
                    }
                    Text(
                        text = listOfNotNull(
                            stop.distanceMiles?.let { "${"%.1f".format(it)} mi" },
                            stop.estimatedDurationMinutes?.let { "$it min" }
                        ).joinToString(" • ").ifBlank { "${stop.items.size} route items" },
                        style = MaterialTheme.typography.bodySmall,
                        color = NeighborlyGreen
                    )
                }
                Text(
                    text = stop.subtotal.formatMoneyOrDash(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NeighborlyGreen
                )
            }

            Divider(color = NeighborlyColors.RouteDivider)

            stop.items.forEach { item ->
                RouteItemRow(item = item, onSwapItem = onSwapItem)
            }
        }
    }
}

@Composable
private fun RouteItemRow(item: RouteStopItem, onSwapItem: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.swapAvailable || item.productId != null) { onSwapItem(item.productId) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProductImage(
            imageUrl = item.imageUrl,
            contentDescription = item.name,
            fallbackEmoji = categoryEmojiForSlug(item.categorySlug),
            size = 48.dp
        )
        Spacer(modifier = Modifier.size(2.dp))
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = NeighborlyGreen, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                text = listOfNotNull("Qty ${item.quantity}", item.unitSize).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = NeighborlyColors.TextSecondary
            )
            if (item.swapAvailable) {
                Text("Swap available", style = MaterialTheme.typography.bodySmall, color = NeighborlyOrange)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            item.originalPrice?.takeIf { original -> item.price != null && original > item.price }?.let {
                Text(it.formatMoneyOrDash(), style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.RouteStrike)
            }
            Text(item.price.formatMoneyOrDash(), style = MaterialTheme.typography.bodyLarge, color = NeighborlyGreen)
        }
    }
}

@Composable
private fun MissingItemsCard(items: List<RouteMissingItem>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Items not found",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyInk
            )
            items.forEach { item ->
                Text(
                    text = "${item.name}${item.reason?.let { ": $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeighborlyColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun EmptyRouteCard(message: String?, hasPendingProducts: Boolean, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Outlined.Map, contentDescription = null, tint = NeighborlyGreen, modifier = Modifier.size(48.dp))
            Text(
                "No optimized route yet",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyInk,
                textAlign = TextAlign.Center
            )
            Text(
                text = message ?: "Create a route from grocery-list product IDs once Worker B's persisted list is wired.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeighborlyColors.MapText,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                enabled = hasPendingProducts,
                colors = ButtonDefaults.buttonColors(containerColor = NeighborlyGreen)
            ) {
                Text("Optimize pending route")
            }
        }
    }
}

@Composable
private fun RouteLoadingCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = NeighborlyGreen, modifier = Modifier.size(28.dp))
            Text("Optimizing your route...", style = MaterialTheme.typography.bodyLarge, color = NeighborlyInk)
        }
    }
}

@Composable
private fun StopNumber(index: Int) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(NeighborlyGreen),
        contentAlignment = Alignment.Center
    ) {
        Text(index.toString(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun SwapAlternativesDialog(
    isLoading: Boolean,
    options: List<RouteSwapOption>,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSelectOption: (RouteSwapOption) -> Unit
) {
    androidx.compose.material.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap alternatives") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    isLoading -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = NeighborlyGreen, modifier = Modifier.size(22.dp))
                        Text("Loading alternatives...")
                    }
                    errorMessage != null -> Text(errorMessage, color = NeighborlyOrange)
                    options.isEmpty() -> Text("No alternatives returned for this product yet.")
                    else -> options.forEach { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOption(option) }
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(option.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(option.storeName, option.price.formatMoneyOrDash(), option.reason).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = NeighborlyColors.TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                text = "Close",
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(12.dp),
                color = NeighborlyGreen
            )
        }
    )
}

private fun Double?.formatMoneyOrDash(): String = this?.let { "$${"%.2f".format(it)}" } ?: "--"
