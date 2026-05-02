package com.example.android.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.ui.components.ProductImage
import com.example.android.viewmodel.shopper.CatalogProduct
import com.example.android.viewmodel.shopper.GroceryListItemUi
import com.example.android.viewmodel.shopper.ShopperViewModel

private val NeighborlyBackground = Color(0xFFF7F3EC)
private val NeighborlyGreen = Color(0xFF0C6A4A)
private val NeighborlyGreenSoft = Color(0xFFE0F1E8)
private val NeighborlyOrange = Color(0xFFE67E22)

@Composable
fun GroceryListScreen(
    shopperViewModel: ShopperViewModel,
    modifier: Modifier = Modifier
) {
    val state = shopperViewModel.uiState
    var selectedItem by remember { mutableStateOf<GroceryListItemUi?>(null) }

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
                color = Color(0xFF1A1A1A),
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
                    Text("Searching products...", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4F7E6B))
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
                    color = Color(0xFF777777),
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
                            SearchResultRow(product = product) {
                                shopperViewModel.addProduct(product)
                            }
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
                    color = if (state.refreshError == null) Color(0xFF4F7E6B) else MaterialTheme.colorScheme.error,
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
                            color = Color(0xFF1A1A1A),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Search above to add items.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF4F7E6B),
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
                            onOpen = { selectedItem = item },
                            onIncrement = { shopperViewModel.incrementItem(item.id) },
                            onDecrement = { shopperViewModel.decrementItem(item.id) }
                        )
                    }
                }
            }
        }

        selectedItem?.let { item ->
            androidx.compose.material.AlertDialog(
                onDismissRequest = { selectedItem = null },
                title = { Text(item.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Store: ${item.store}")
                        Text("Size: ${item.unitSize}")
                        Text("Price: $${"%.2f".format(item.price)}")
                        Text("Quantity: ${item.quantity}")
                    }
                },
                confirmButton = {
                    Text(
                        text = "Close",
                        modifier = Modifier
                            .clickable { selectedItem = null }
                            .padding(12.dp),
                        color = NeighborlyGreen
                    )
                },
                dismissButton = {
                    Text(
                        text = "Delete",
                        modifier = Modifier
                            .clickable {
                                shopperViewModel.deleteItem(item.id)
                                selectedItem = null
                            }
                            .padding(12.dp),
                        color = NeighborlyOrange
                    )
                }
            )
        }
    }
}

@Composable
private fun SearchResultRow(product: CatalogProduct, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProductImage(
            imageUrl = product.imageUrl,
            contentDescription = product.name,
            fallbackEmoji = product.categoryEmoji
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(product.name, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF1A1A1A))
            Text(
                text = listOfNotNull(product.brand, product.unitSize.takeIf { it.isNotBlank() }).joinToString(" / "),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF777777)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(product.price.formatPrice(), style = MaterialTheme.typography.bodyLarge, color = NeighborlyGreen)
            Text(product.store ?: "Store pending", style = MaterialTheme.typography.bodySmall, color = Color(0xFF777777))
        }
    }
}

@Composable
private fun GroceryItemCard(
    item: GroceryListItemUi,
    onOpen: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(16.dp),
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
                Text(item.unitSize, style = MaterialTheme.typography.bodySmall, color = Color(0xFF777777))
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
    }
}

private fun Double?.formatPrice(): String {
    return this?.let { "$${"%.2f".format(it)}" } ?: "Price pending"
}
