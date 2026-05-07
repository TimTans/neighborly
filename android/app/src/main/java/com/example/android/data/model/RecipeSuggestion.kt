package com.example.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A server-generated recipe suggestion. Mirrors iOS `RecipeSuggestion`
 * (Models/RecipeSuggestion.swift) — JSON field names match exactly so the
 * `/recipes/generate` contract stays platform-portable.
 */
@Serializable
data class RecipeSuggestion(
    val title: String,
    val summary: String,
    @SerialName("why_it_matches") val whyItMatches: List<String> = emptyList(),
    @SerialName("prep_minutes") val prepMinutes: Int? = null,
    @SerialName("cook_minutes") val cookMinutes: Int? = null,
    val servings: Int? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    @SerialName("nutrition_notes") val nutritionNotes: List<String> = emptyList(),
) {
    /** Stable identity, mirrors iOS `RecipeSuggestion.id`. */
    val id: String get() = title + summary
}

/**
 * Body for `POST /recipes/generate`. Mirrors iOS `RecipeRequestPayload`.
 */
@Serializable
data class RecipeRequestPayload(
    @SerialName("dietary_preferences") val dietaryPreferences: List<String> = emptyList(),
    @SerialName("avoid_ingredients") val avoidIngredients: List<String> = emptyList(),
    @SerialName("nutrition_targets") val nutritionTargets: NutritionTargetsPayload? = null,
)

/**
 * Optional wellness limits forwarded to the recipe generator. Mirrors iOS
 * `NutritionTargetsPayload` — units are explicit in the field names.
 */
@Serializable
data class NutritionTargetsPayload(
    @SerialName("cholesterol_mg") val cholesterolMg: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    @SerialName("sugar_g") val sugarG: Double? = null,
)
