package com.example.android.domain.wellness

import com.example.android.data.model.ProductNutrition
import com.example.android.data.repository.preferences.PreferenceState

/**
 * A reason a product violates the user's wellness preferences. Mirrors the iOS
 * `WellnessViolation` enum (Models/Product.swift:20-23).
 */
sealed class WellnessViolation {
    data class Allergen(val name: String) : WellnessViolation()
    data class NutrientExceeded(
        val name: String,
        val actual: Double,
        val limit: Double,
    ) : WellnessViolation()
}

/**
 * Returns all preference violations for this product. Mirrors iOS
 * `ProductNutrition.violations(against:)` (Models/Product.swift:35-58).
 *
 * Allergen flags fire when the user has set both `avoid<Allergen>` and the
 * product's `contains<Allergen>` is `true`. Unknown values (`null`) are treated
 * as no violation. Nutrient-limit checks run only when `wellnessEnabled = true`,
 * the limit string parses to a positive number, and the product nutrient
 * strictly exceeds the limit.
 */
fun ProductNutrition.violations(prefs: PreferenceState): List<WellnessViolation> {
    val out = mutableListOf<WellnessViolation>()

    if (prefs.avoidDairy && containsDairy == true) out += WellnessViolation.Allergen("Dairy")
    if (prefs.avoidPeanuts && containsPeanuts == true) out += WellnessViolation.Allergen("Peanuts")
    if (prefs.avoidShellfish && containsShellfish == true) out += WellnessViolation.Allergen("Shellfish")
    if (prefs.avoidWheat && containsWheat == true) out += WellnessViolation.Allergen("Wheat")

    if (prefs.wellnessEnabled) {
        val cholesterolLimit = prefs.cholesterolLimit.toDoubleOrNullSafe()
        if (cholesterolLimit != null && cholesterolMg != null && cholesterolMg > cholesterolLimit) {
            out += WellnessViolation.NutrientExceeded("Cholesterol", cholesterolMg, cholesterolLimit)
        }
        val sodiumLimit = prefs.sodiumLimit.toDoubleOrNullSafe()
        if (sodiumLimit != null && sodiumMg != null && sodiumMg > sodiumLimit) {
            out += WellnessViolation.NutrientExceeded("Sodium", sodiumMg, sodiumLimit)
        }
        val sugarLimit = prefs.sugarLimit.toDoubleOrNullSafe()
        if (sugarLimit != null && sugarG != null && sugarG > sugarLimit) {
            out += WellnessViolation.NutrientExceeded("Sugar", sugarG, sugarLimit)
        }
    }

    return out
}

private fun String.toDoubleOrNullSafe(): Double? =
    trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it > 0.0 }
