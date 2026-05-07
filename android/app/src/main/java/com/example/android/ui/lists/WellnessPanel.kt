package com.example.android.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.android.domain.wellness.WellnessViolation
import kotlin.math.roundToLong

/**
 * Wellness alerts panel rendered inside the product / item detail sheets.
 * Mirrors iOS `WellnessPanel` (GroceryListView.swift:1073-1162) with the
 * Android Material 3 error palette so dark mode picks up automatically.
 *
 * Hidden when [violations] is empty.
 */
@Composable
fun WellnessPanel(
    violations: List<WellnessViolation>,
    modifier: Modifier = Modifier,
) {
    if (violations.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Wellness alerts",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        violations.forEach { violation ->
            Text(
                text = violation.bulletLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * Long-form bullet text for the wellness panel and warning sheet.
 * Allergen example: "⚠ Contains peanuts"
 * Nutrient example: "⚠ Sodium: 800 mg, limit 600 mg"
 */
internal fun WellnessViolation.bulletLine(): String = when (this) {
    is WellnessViolation.Allergen ->
        "⚠ Contains ${name.lowercase()}"
    is WellnessViolation.NutrientExceeded ->
        "⚠ $name: ${formatNutrient(actual)} ${unitFor(name)}, limit ${formatNutrient(limit)} ${unitFor(name)}"
}

private fun unitFor(nutrient: String): String = when (nutrient.lowercase()) {
    "sugar" -> "g"
    else -> "mg"
}

private fun formatNutrient(value: Double): String = value.roundToLong().toString()
