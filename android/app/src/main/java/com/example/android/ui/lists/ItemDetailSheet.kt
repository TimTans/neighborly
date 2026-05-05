package com.example.android.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.data.local.GroceryListItemRecord
import com.example.android.data.model.Product
import com.example.android.ui.components.ProductImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    item: GroceryListItemRecord,
    fullProduct: Product?,
    isLoadingFullProduct: Boolean,
    errorMessage: String?,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetTheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                ProductImage(
                    imageUrl = fullProduct?.imageUrl,
                    contentDescription = item.name,
                    fallbackEmoji = fullProduct?.productCategories?.emoji ?: "🛒",
                    size = 96.dp,
                    cornerRadius = 12.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SheetTheme.textPrimary
                    )
                    fullProduct?.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SheetTheme.textSecondary
                        )
                    }
                    if (item.unitSize.isNotBlank()) {
                        Text(
                            text = item.unitSize,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SheetTheme.textMuted
                        )
                    }
                }
            }

            HorizontalDivider(color = SheetTheme.divider)

            QuantityControls(
                quantity = item.quantity,
                onIncrement = onIncrement,
                onDecrement = onDecrement
            )

            HorizontalDivider(color = SheetTheme.divider)

            StorePricesSection(
                fullProduct = fullProduct,
                isLoading = isLoadingFullProduct,
                errorMessage = errorMessage,
                fallbackBestPrice = item.price.takeIf { it > 0.0 },
                fallbackBestStoreName = item.store.takeIf { it.isNotBlank() }
            )

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onRemove,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = SheetTheme.orange
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Remove from list",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SheetTheme.orange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuantityControls(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Quantity",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = SheetTheme.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SheetTheme.cardBackground)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuantityIconButton(
                icon = Icons.Filled.Remove,
                tint = SheetTheme.orange,
                onClick = onDecrement,
                contentDescription = "Decrease quantity"
            )
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = SheetTheme.textPrimary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            QuantityIconButton(
                icon = Icons.Filled.Add,
                tint = SheetTheme.green,
                onClick = onIncrement,
                contentDescription = "Increase quantity"
            )
        }
    }
}

@Composable
private fun QuantityIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
