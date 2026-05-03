package com.example.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.data.model.RecipeSuggestion
import com.example.android.ui.home.components.FeaturedRecipeCard
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.ui.theme.NeighborlyShapes
import com.example.android.ui.theme.NeighborlySpacing
import com.example.android.viewmodel.home.DemoRouteStops
import com.example.android.viewmodel.home.HomeUiState
import com.example.android.viewmodel.home.HomeViewModel
import com.example.android.viewmodel.home.RouteStop
import com.example.android.viewmodel.home.previewStopsFrom
import com.example.android.viewmodel.route.RoutePlan
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    displayName: String,
    initials: String,
    groceryListItemCount: Int,
    activeRoute: RoutePlan?,
    onOpenPreferences: () -> Unit,
    onSignOut: () -> Unit,
    onStartTrip: () -> Unit,
    onRefreshRecipe: () -> Unit,
    onRetryRecipe: () -> Unit,
    onOpenRecipe: (RecipeSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseState = viewModel.uiState
    val state = baseState.copy(
        userName = displayName,
        itemsTracked = groceryListItemCount.toString(),
        itemsTrackedLabel = "in your list",
        optimizedStopsLabel = activeRoute?.stopCountLabel ?: baseState.optimizedStopsLabel
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = NeighborlyColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            HomeTopBar(
                initials = initials,
                displayName = displayName,
                onOpenPreferences = onOpenPreferences,
                onSignOut = onSignOut
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = NeighborlySpacing.ScreenHorizontal,
                        vertical = NeighborlySpacing.ScreenVertical
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(
                    name = state.userName,
                    activeRoute = activeRoute,
                    fallbackSavings = state.savingsThisTrip.removePrefix("$"),
                    onStartTrip = onStartTrip
                )

                FeaturedRecipeCard(
                    recipe = state.featuredRecipe,
                    isLoading = state.isLoadingRecipe,
                    error = state.recipeError,
                    onRefresh = onRefreshRecipe,
                    onRetry = onRetryRecipe,
                    onOpen = onOpenRecipe
                )

                MetricsGrid(state = state)

                OptimizedRouteCard(
                    stopsLabel = state.optimizedStopsLabel,
                    activeRoute = activeRoute
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    initials: String,
    displayName: String,
    onOpenPreferences: () -> Unit,
    onSignOut: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(color = NeighborlyColors.Background, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NeighborlyColors.Green),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "N",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Text(
                    text = "Neighborly",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = NeighborlyColors.TextPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NeighborlyColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = NeighborlyColors.TextPrimary
                    )
                }

                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NeighborlyColors.Orange)
                            .clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            menuExpanded = false
                            onOpenPreferences()
                        }) {
                            Text("Preferences")
                        }
                        DropdownMenuItem(onClick = {
                            menuExpanded = false
                            onSignOut()
                        }) {
                            Text("Sign Out")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    name: String,
    activeRoute: RoutePlan?,
    fallbackSavings: String,
    onStartTrip: () -> Unit
) {
    val tripReady = activeRoute != null
    val tagline = if (activeRoute != null) {
        val storeCount = activeRoute.stops.size
        val itemCount = activeRoute.itemCount
        val totalCost = activeRoute.totalCost
        if (totalCost != null) {
            "Your optimized route is ready — $storeCount ${if (storeCount == 1) "store" else "stores"}, " +
                "$itemCount ${if (itemCount == 1) "item" else "items"}, total ${formatCurrency(totalCost)}."
        } else {
            "Your optimized route is ready — $storeCount ${if (storeCount == 1) "store" else "stores"}, " +
                "$itemCount ${if (itemCount == 1) "item" else "items"}."
        }
    } else {
        "Your optimized route is ready — 3 stores, 12 items, saving $$fallbackSavings."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NeighborlyShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Green)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Good morning,", style = MaterialTheme.typography.bodyMedium, color = NeighborlyColors.GreenSoft)
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = tagline,
                style = MaterialTheme.typography.bodySmall,
                color = NeighborlyColors.GreenSoft
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(NeighborlyShapes.Pill)
                    .background(
                        if (tripReady) NeighborlyColors.Surface else NeighborlyColors.Surface.copy(alpha = 0.5f)
                    )
                    .then(
                        if (tripReady) Modifier.clickable { onStartTrip() } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start Trip →",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (tripReady) NeighborlyColors.Green else NeighborlyColors.Green.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun MetricsGrid(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BudgetMetricCard(modifier = Modifier.weight(1f), used = state.budgetUsed, total = state.totalBudget)
            SavedMetricCard(modifier = Modifier.weight(1f), amount = state.savedThisMonth, sublabel = state.savedThisMonthLabel)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallMetricCard(modifier = Modifier.weight(1f), value = state.avgTripTime, label = "avg trip", icon = Icons.Outlined.Schedule, tint = NeighborlyColors.IconMuted)
            SmallMetricCard(modifier = Modifier.weight(1f), value = state.milesSaved, label = "miles saved", icon = Icons.Outlined.Place, tint = NeighborlyColors.IconMuted)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallMetricCard(modifier = Modifier.weight(1f), value = state.itemsTracked, label = state.itemsTrackedLabel, icon = Icons.Outlined.Inventory2, tint = NeighborlyColors.IconMuted)
            SmallMetricCard(modifier = Modifier.weight(1f), value = state.alertsCount, label = "alerts", icon = Icons.Outlined.Notifications, tint = NeighborlyColors.IconMuted)
        }
    }
}

@Composable
private fun BudgetMetricCard(modifier: Modifier = Modifier, used: String, total: String) {
    val progress = (57.31 / 120.0).toFloat().coerceIn(0f, 1f)

    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = Icons.Outlined.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp), tint = NeighborlyColors.Green)
                Text("BUDGET", style = MaterialTheme.typography.labelSmall, color = NeighborlyColors.TextTertiary)
            }
            Text(used, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NeighborlyColors.Green)
            Text("of $total", style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextTertiary)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NeighborlyColors.Green,
                trackColor = NeighborlyColors.GreenSoft
            )
        }
    }
}

@Composable
private fun SavedMetricCard(modifier: Modifier = Modifier, amount: String, sublabel: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = Icons.Outlined.ShowChart, contentDescription = null, modifier = Modifier.size(18.dp), tint = NeighborlyColors.Orange)
                Text("SAVED", style = MaterialTheme.typography.labelSmall, color = NeighborlyColors.TextTertiary)
            }
            Text(amount, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NeighborlyColors.Orange)
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextTertiary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f).forEach { height ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp * height)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NeighborlyColors.Orange.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallMetricCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color
) {
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = NeighborlyColors.Surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NeighborlyColors.TextBody)
            Text(label, style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextTertiary)
        }
    }
}

@Composable
private fun OptimizedRouteCard(stopsLabel: String, activeRoute: RoutePlan?) {
    val stops: List<RouteStop>
    val subtotalByIndex: Map<Int, Double?>
    val isDemo: Boolean
    if (activeRoute != null) {
        stops = previewStopsFrom(activeRoute)
        subtotalByIndex = activeRoute.stops.take(stops.size)
            .mapIndexed { idx, stop -> (idx + 1) to stop.subtotal }
            .toMap()
        isDemo = false
    } else {
        stops = DemoRouteStops
        subtotalByIndex = emptyMap()
        isDemo = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NeighborlyShapes.LargeCard,
        colors = CardDefaults.cardColors(containerColor = NeighborlyColors.GreenSoft)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = NeighborlyColors.TextTertiary)
                    Text(
                        text = if (isDemo) "OPTIMIZED ROUTE (DEMO)" else "OPTIMIZED ROUTE",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NeighborlyColors.TextTertiary
                    )
                }
                Text(stopsLabel, style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.Green)
            }

            if (activeRoute != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeRoute.totalCost?.let { cost ->
                        Text(
                            text = formatCurrency(cost),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = NeighborlyColors.Green
                        )
                    }
                    Text(
                        text = activeRoute.itemCountLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = NeighborlyColors.TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(colors = listOf(NeighborlyColors.MapGradientStart, NeighborlyColors.GreenSoft))),
                contentAlignment = Alignment.Center
            ) {
                Text("Google Maps integration", style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.MapText)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stops.forEach { stop ->
                    RouteRow(
                        index = stop.index,
                        name = stop.name,
                        address = stop.address,
                        itemsLabel = stop.itemsLabel,
                        timeEstimate = stop.timeEstimate,
                        distance = stop.distance,
                        subtotal = subtotalByIndex[stop.index]
                    )
                }
            }

            if (isDemo) {
                Text(
                    text = "Demo data — create a route from your list",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeighborlyColors.TextTertiary
                )
            }
        }
    }
}

@Composable
private fun RouteRow(
    index: Int,
    name: String,
    address: String,
    itemsLabel: String,
    timeEstimate: String,
    distance: String,
    subtotal: Double? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                            .background(NeighborlyColors.Green),
                contentAlignment = Alignment.Center
            ) {
                Text(index.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = NeighborlyColors.TextStrong)
                Text(address, style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeighborlyColors.GreenSoft)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(itemsLabel, style = MaterialTheme.typography.labelSmall, color = NeighborlyColors.Green)
                    }
                    if (timeEstimate.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeighborlyColors.OrangeSoft)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(timeEstimate, style = MaterialTheme.typography.labelSmall, color = NeighborlyColors.Orange)
                        }
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (subtotal != null) {
                Text(formatCurrency(subtotal), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = NeighborlyColors.Green)
            }
            Text(
                text = if (distance.isEmpty()) "—" else distance,
                style = MaterialTheme.typography.bodySmall,
                color = NeighborlyColors.TextSecondary
            )
        }
    }
}

private fun formatCurrency(amount: Double): String {
    return String.format(Locale.US, "$%.2f", amount)
}
