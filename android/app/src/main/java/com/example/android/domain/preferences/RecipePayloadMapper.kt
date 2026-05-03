package com.example.android.domain.preferences

import com.example.android.data.model.NutritionTargetsPayload
import com.example.android.data.model.RecipeRequestPayload
import com.example.android.data.repository.preferences.PreferenceState

/**
 * Maps a [PreferenceState] to the body for `POST /recipes/generate`. Mirrors the
 * iOS `Preferences.recipeRequestPayload` extension (RecipeSuggestion.swift:36-68).
 *
 * - `dietaryPreferences` and `avoidIngredients` collect a fixed string per
 *   enabled flag, so the backend sees the same vocabulary on both platforms.
 * - `nutritionTargets` is null unless wellness is enabled. Each limit is parsed
 *   from its String field; a value that is blank, non-numeric, or non-positive
 *   becomes null. If all three end up null the parent `nutritionTargets` is
 *   itself omitted, matching iOS `isEmpty` short-circuit.
 */
fun PreferenceState.toRecipeRequestPayload(): RecipeRequestPayload {
    val dietaryPreferences = buildList {
        if (dietVegan) add("vegan")
        if (dietGlutenFree) add("gluten_free")
        if (dietLowCarb) add("low_carb")
        if (dietKosher) add("kosher")
        if (dietHalal) add("halal")
        if (dietKeto) add("keto")
    }

    val avoidIngredients = buildList {
        if (avoidDairy) add("dairy")
        if (avoidPeanuts) add("peanuts")
        if (avoidShellfish) add("shellfish")
        if (avoidWheat) add("wheat")
    }

    val nutritionTargets = if (wellnessEnabled) {
        val targets = NutritionTargetsPayload(
            cholesterolMg = cholesterolLimit.toPositiveDoubleOrNull(),
            sodiumMg = sodiumLimit.toPositiveDoubleOrNull(),
            sugarG = sugarLimit.toPositiveDoubleOrNull(),
        )
        if (targets.cholesterolMg == null && targets.sodiumMg == null && targets.sugarG == null) {
            null
        } else {
            targets
        }
    } else {
        null
    }

    return RecipeRequestPayload(
        dietaryPreferences = dietaryPreferences,
        avoidIngredients = avoidIngredients,
        nutritionTargets = nutritionTargets,
    )
}

private fun String.toPositiveDoubleOrNull(): Double? {
    val parsed = trim().toDoubleOrNull() ?: return null
    return parsed.takeIf { it > 0.0 }
}
