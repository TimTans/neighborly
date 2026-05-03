package com.example.android.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.android.data.model.Product
import com.example.android.data.model.StoreProduct
import com.example.android.data.repository.GroceryProductSummary
import com.example.android.domain.wellness.WellnessViolation
import com.example.android.ui.components.ProductImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailSheet(
    summary: GroceryProductSummary,
    fullProduct: Product?,
    isLoadingFullProduct: Boolean,
    errorMessage: String?,
    violations: List<WellnessViolation>,
    onAdd: () -> Unit,
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
                    imageUrl = summary.imageUrl,
                    contentDescription = summary.name,
                    fallbackEmoji = summary.categoryEmoji,
                    size = 96.dp,
                    cornerRadius = 12.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = SheetTheme.textPrimary
                    )
                    summary.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SheetTheme.textSecondary
                        )
                    }
                    if (summary.unitSize.isNotBlank()) {
                        Text(
                            text = summary.unitSize,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SheetTheme.textMuted
                        )
                    }
                }
            }

            HorizontalDivider(color = SheetTheme.divider)

            WellnessPanel(violations = violations)

            StorePricesSection(
                fullProduct = fullProduct,
                isLoading = isLoadingFullProduct,
                errorMessage = errorMessage,
                fallbackBestPrice = summary.bestPrice,
                fallbackBestStoreName = summary.bestStoreName
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SheetTheme.green,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Add to Grocery List",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun StorePricesSection(
    fullProduct: Product?,
    isLoading: Boolean,
    errorMessage: String?,
    fallbackBestPrice: Double?,
    fallbackBestStoreName: String?
) {
    when {
        fullProduct != null && fullProduct.storeProducts.isNotEmpty() -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("PRICES BY STORE")
                val sorted = fullProduct.storeProducts.sortedBy { it.salePrice ?: it.price }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    sorted.forEach { sp ->
                        StoreProductRow(sp)
                    }
                }
            }
        }
        isLoading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = SheetTheme.green
                )
                Text(
                    text = "Loading store prices...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SheetTheme.textMuted
                )
            }
        }
        errorMessage != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                FallbackPriceRow(fallbackBestPrice, fallbackBestStoreName)
            }
        }
        else -> {
            FallbackPriceRow(fallbackBestPrice, fallbackBestStoreName)
        }
    }
}

@Composable
private fun FallbackPriceRow(bestPrice: Double?, bestStoreName: String?) {
    if (bestPrice == null && bestStoreName.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("BEST PRICE")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SheetTheme.cardBackground)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SheetTheme.green)
            )
            Text(
                text = bestStoreName?.takeIf { it.isNotBlank() } ?: "Last known price",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = SheetTheme.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = bestPrice?.let { formatCurrency(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = SheetTheme.green
            )
        }
    }
}

@Composable
private fun StoreProductRow(sp: StoreProduct) {
    val effectivePrice = sp.salePrice ?: sp.price
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SheetTheme.cardBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(SheetTheme.green)
        )
        Text(
            text = sp.stores.name.ifBlank { sp.stores.chain ?: "Unknown" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = SheetTheme.textPrimary
        )
        if (!sp.inStock) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SheetTheme.background)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "out of stock",
                    style = MaterialTheme.typography.labelSmall,
                    color = SheetTheme.outOfStock
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            if (sp.salePrice != null) {
                Text(
                    text = formatCurrency(effectivePrice),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SheetTheme.green
                )
                Text(
                    text = formatCurrency(sp.price),
                    style = MaterialTheme.typography.labelSmall,
                    color = SheetTheme.textMuted,
                    textDecoration = TextDecoration.LineThrough
                )
            } else {
                Text(
                    text = formatCurrency(sp.price),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = SheetTheme.green
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp)
        ),
        color = SheetTheme.textMuted
    )
}

internal fun formatCurrency(value: Double): String = "$${"%.2f".format(value)}"

internal object SheetTheme {
    val background = Color(0xFFF7F3EC)
    val cardBackground = Color(0xFFFFFFFF)
    val green = Color(0xFF0C6A4A)
    val orange = Color(0xFFE67E22)
    val textPrimary = Color(0xFF1A1A1A)
    val textSecondary = Color(0xFF3F5A50)
    val textMuted = Color(0xFF6B7B73)
    val divider = Color(0xFFE5E0D6)
    val outOfStock = Color(0xB3D32F2F)
}
