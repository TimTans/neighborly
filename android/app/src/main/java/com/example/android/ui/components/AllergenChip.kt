package com.example.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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

/**
 * Maximum number of chips rendered before collapsing the rest into a "+N more"
 * counter. Mirrors iOS GroceryListView.swift:1063 chip density (the row is
 * meant to be glanceable, not exhaustive).
 */
private const val MAX_VISIBLE_CHIPS = 3

/**
 * Renders a horizontal row of red-tinted chips summarising the supplied
 * [violations]. Hides itself entirely when [violations] is empty so callers can
 * always include it in their layout without an `if` guard.
 *
 * Chip labels mirror the iOS allergen chips (GroceryListView.swift:1061-1068)
 * and the warning bullets (GroceryListView.swift:1187-1198): allergens render
 * as the allergen name, nutrient overruns as the nutrient name. Beyond
 * [MAX_VISIBLE_CHIPS] chips the remainder collapses into a `+N more` chip.
 */
@Composable
fun AllergenChip(
    violations: List<WellnessViolation>,
    modifier: Modifier = Modifier,
) {
    if (violations.isEmpty()) return

    val labels = violationChipLabels(violations, MAX_VISIBLE_CHIPS)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            Chip(label = label)
        }
    }
}

@Composable
private fun Chip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Produces the label list for a chip row given the raw [violations] and a cap
 * on the number of detail chips rendered before collapsing into "+N more".
 *
 * Pure function so it can be unit-tested without spinning up Compose.
 */
internal fun violationChipLabels(
    violations: List<WellnessViolation>,
    maxVisible: Int = MAX_VISIBLE_CHIPS,
): List<String> {
    if (violations.isEmpty()) return emptyList()
    val all = violations.map { it.shortLabel() }
    if (all.size <= maxVisible) return all
    return all.take(maxVisible) + "+${all.size - maxVisible} more"
}

private fun WellnessViolation.shortLabel(): String = when (this) {
    is WellnessViolation.Allergen -> "⚠ $name"
    is WellnessViolation.NutrientExceeded -> "⚠ $name"
}
