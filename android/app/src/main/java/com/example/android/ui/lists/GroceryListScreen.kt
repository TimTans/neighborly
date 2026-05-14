package com.example.android.ui.lists

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.data.local.GroceryListItemRecord
import com.example.android.data.location.FusedLocationHelper
import com.example.android.data.location.LocationHelper
import com.example.android.data.location.UserCoordinates
import com.example.android.data.repository.GroceryProductSummary
import com.example.android.ui.components.AllergenChip
import com.example.android.ui.components.ProductImage
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.viewmodel.route.RouteViewModel
import com.example.android.viewmodel.shopper.CatalogProduct
import com.example.android.viewmodel.shopper.GroceryListItemUi
import com.example.android.viewmodel.shopper.ShopperViewModel
import kotlinx.coroutines.launch

private val NeighborlyBackground = NeighborlyColors.Background
private val NeighborlyGreen = NeighborlyColors.Green
private val NeighborlyGreenSoft = NeighborlyColors.GreenSoft
private val NeighborlyOrange = NeighborlyColors.Orange

@Composable
fun GroceryListScreen(
    shopperViewModel: ShopperViewModel,
    routeViewModel: RouteViewModel,
    onNavigateToRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = shopperViewModel.uiState
    val routeState = routeViewModel.uiState
    val context = LocalContext.current
    val locationHelper: LocationHelper = remember(context) { FusedLocationHelper(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasOptimizableItems = state.groceryList.any { !it.productId.isNullOrBlank() }
    val isOptimizing = routeState.isLoading
    // Tracks whether *this* screen kicked off the in-flight optimize. Without this
    // flag the success `LaunchedEffect` would also fire whenever the user re-enters
    // the Lists tab while an optimized route already lives in the shared VM,
    // causing an immediate bounce back to the Route tab.
    var awaitingOptimization by remember { mutableStateOf(false) }

    fun launchOptimize(location: UserCoordinates?) {
        awaitingOptimization = true
        shopperViewModel.createRoute(routeViewModel, location)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        coroutineScope.launch {
            val location = if (granted) locationHelper.requestLastKnownLocation() else null
            launchOptimize(location)
        }
    }

    fun onCreateRouteTapped() {
        if (!hasOptimizableItems || isOptimizing) return
        if (locationHelper.hasPermission()) {
            coroutineScope.launch {
                val location = locationHelper.requestLastKnownLocation()
                launchOptimize(location)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // When *our* optimize call resolves (route appears, no error, not loading)
    // jump to the Route tab. Guarded by [awaitingOptimization] so revisiting the
    // Lists tab with an existing route in memory doesn't re-trigger navigation.
    LaunchedEffect(
        awaitingOptimization,
        routeState.optimizedRoute,
        routeState.isLoading,
        routeState.errorMessage
    ) {
        if (!awaitingOptimization) return@LaunchedEffect
        if (routeState.isLoading) return@LaunchedEffect
        if (routeState.errorMessage != null) {
            awaitingOptimization = false
            return@LaunchedEffect
        }
        if (routeState.optimizedRoute != null) {
            awaitingOptimization = false
            onNavigateToRoute()
        }
    }

    LaunchedEffect(state.routeCreationError) {
        val message = state.routeCreationError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message)
        if (result == SnackbarResult.Dismissed || result == SnackbarResult.ActionPerformed) {
            shopperViewModel.clearRouteCreationError()
        }
    }

    LaunchedEffect(routeState.errorMessage) {
        val message = routeState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Grocery List",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = NeighborlyColors.TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = shopperViewModel::updateSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search products to add") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true
            )

            if (state.isSearchLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NeighborlyGreen
                    )
                    Text("Searching products...", style = MaterialTheme.typography.bodyMedium, color = NeighborlyColors.MapText)
                }
            }

            state.searchError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (
                state.searchQuery.trim().length >= 2 &&
                !state.isSearchLoading &&
                state.searchError == null &&
                state.searchResults.isEmpty()
            ) {
                Text(
                    text = "No products found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeighborlyColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (state.searchResults.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column {
                        state.searchResults.forEach { product ->
                            SearchResultRow(
                                product = product,
                                violations = product.productId
                                    ?.let { shopperViewModel.violationsFor(it) }
                                    ?: emptyList(),
                                onAppear = {
                                    product.productId
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { shopperViewModel.loadNutritionFor(it) }
                                },
                                onTap = {
                                    shopperViewModel.showProductSheet(product.toProductSummary())
                                }
                            )
                        }
                    }
                }
            }

            if (state.isRefreshingPrices || state.refreshError != null) {
                Text(
                    text = if (state.isRefreshingPrices) {
                        "Refreshing saved prices..."
                    } else {
                        state.refreshError.orEmpty()
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.refreshError == null) NeighborlyColors.MapText else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (state.groceryList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            tint = NeighborlyGreen,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Your grocery list is empty",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeighborlyColors.TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Search above to add items.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = NeighborlyColors.MapText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.groceryList, key = { it.id }) { item ->
                        GroceryItemCard(
                            item = item,
                            violations = item.productId
                                ?.let { shopperViewModel.violationsFor(it) }
                                ?: emptyList(),
                            onAppear = {
                                item.productId
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { shopperViewModel.loadNutritionFor(it) }
                            },
                            onOpen = { shopperViewModel.showItemSheet(item.toRecord()) },
                            onIncrement = { shopperViewModel.incrementItem(item.id) },
                            onDecrement = { shopperViewModel.decrementItem(item.id) }
                        )
                    }
                }

                if (state.searchQuery.isBlank()) {
                    CreateRouteButton(
                        enabled = hasOptimizableItems && !isOptimizing,
                        isLoading = isOptimizing,
                        onClick = ::onCreateRouteTapped
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data -> Snackbar(snackbarData = data) }
            )
        }

        state.productSheet?.let { summary ->
            ProductDetailSheet(
                summary = summary,
                fullProduct = state.sheetProduct,
                isLoadingFullProduct = state.isLoadingSheetProduct,
                errorMessage = state.sheetProductError,
                violations = shopperViewModel.violationsFor(summary.productId),
                onAdd = { shopperViewModel.addFromProductSheet() },
                onDismiss = { shopperViewModel.dismissProductSheet() }
            )
        }

        state.itemSheet?.let { record ->
            ItemDetailSheet(
                item = record,
                fullProduct = state.sheetProduct,
                isLoadingFullProduct = state.isLoadingSheetProduct,
                errorMessage = state.sheetProductError,
                violations = shopperViewModel.violationsFor(record.productId),
                onIncrement = { shopperViewModel.incrementItem(record.id) },
                onDecrement = { shopperViewModel.decrementItem(record.id) },
                onRemove = {
                    shopperViewModel.deleteItem(record.id)
                    shopperViewModel.dismissItemSheet()
                },
                onDismiss = { shopperViewModel.dismissItemSheet() }
            )
        }

        WellnessWarningSheet(
            shown = state.pendingRouteWarnings != null,
            warnings = state.pendingRouteWarnings.orEmpty(),
            onConfirm = {
                awaitingOptimization = true
                coroutineScope.launch {
                    val location = if (locationHelper.hasPermission()) {
                        locationHelper.requestLastKnownLocation()
                    } else {
                        null
                    }
                    shopperViewModel.confirmRouteCreationDespiteWarnings(routeViewModel, location)
                }
            },
            onCancel = { shopperViewModel.dismissRouteWarnings() }
        )
    }
}

private fun CatalogProduct.toProductSummary(): GroceryProductSummary {
    return GroceryProductSummary(
        productId = productId,
        upc = upc,
        name = name,
        brand = brand,
        unitSize = unitSize,
        bestPrice = price,
        bestStoreName = store,
        categoryEmoji = categoryEmoji,
        imageUrl = imageUrl
    )
}

private fun GroceryListItemUi.toRecord(): GroceryListItemRecord {
    return GroceryListItemRecord(
        id = id,
        productId = productId,
        upc = upc,
        name = name,
        unitSize = unitSize,
        store = store,
        price = price,
        quantity = quantity,
        dateAddedMillis = dateAddedMillis
    )
}

@Composable
private fun SearchResultRow(
    product: CatalogProduct,
    violations: List<com.example.android.domain.wellness.WellnessViolation>,
    onAppear: () -> Unit,
    onTap: () -> Unit,
) {
    LaunchedEffect(product.productId) { onAppear() }
    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onTap)
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductImage(
                imageUrl = product.imageUrl,
                contentDescription = product.name,
                fallbackEmoji = product.categoryEmoji
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.bodyLarge, color = NeighborlyColors.TextPrimary)
                Text(
                    text = listOfNotNull(product.brand, product.unitSize.takeIf { it.isNotBlank() }).joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = NeighborlyColors.TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(product.price.formatPrice(), style = MaterialTheme.typography.bodyLarge, color = NeighborlyGreen)
                Text(product.store ?: "Store pending", style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextSecondary)
            }
        }
        if (violations.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
            AllergenChip(violations = violations)
        }
    }
}

@Composable
private fun GroceryItemCard(
    item: GroceryListItemUi,
    violations: List<com.example.android.domain.wellness.WellnessViolation>,
    onAppear: () -> Unit,
    onOpen: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    LaunchedEffect(item.productId) { onAppear() }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GroceryListItemRecord has no imageUrl/category yet; falls back to default emoji.
                ProductImage(
                    imageUrl = null,
                    contentDescription = item.name
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text(item.unitSize, style = MaterialTheme.typography.bodySmall, color = NeighborlyColors.TextSecondary)
                    Text("Best price: $${"%.2f".format(item.price)} at ${item.store}", style = MaterialTheme.typography.bodySmall, color = NeighborlyGreen)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.quantity > 1) Icons.Filled.RemoveCircle else Icons.Filled.Delete,
                        contentDescription = "Decrease",
                        tint = NeighborlyOrange,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onDecrement)
                    )
                    Text(item.quantity.toString(), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Icon(
                        imageVector = Icons.Filled.AddCircle,
                        contentDescription = "Increase",
                        tint = NeighborlyGreen,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(onClick = onIncrement)
                    )
                }
            }
            if (violations.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                AllergenChip(violations = violations)
            }
        }
    }
}

private fun Double?.formatPrice(): String {
    return this?.let { "$${"%.2f".format(it)}" } ?: "Price pending"
}

@Composable
private fun CreateRouteButton(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeighborlyGreen,
            contentColor = Color.White,
            disabledContainerColor = NeighborlyGreen.copy(alpha = 0.4f),
            disabledContentColor = Color.White
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isLoading) "Optimizing..." else "Create Route",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}
