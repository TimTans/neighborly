package com.example.android.domain.preferences

import com.example.android.data.repository.preferences.PreferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors iOS `Preferences.recipeRequestPayload` (RecipeSuggestion.swift:36-68).
 * Verifies the diet/avoid string vocabularies match what the backend expects and
 * the wellness/nutrition limits round-trip correctly.
 */
class RecipePayloadMapperTest {

    /** PreferenceState defaults set `dietGlutenFree = true` and `avoidPeanuts = true`. */
    private val baseline = PreferenceState(
        wellnessEnabled = false,
        dietGlutenFree = false,
        avoidPeanuts = false,
    )

    @Test
    fun `maps vegan plus gluten-free plus avoid peanuts to the expected payload`() {
        val state = baseline.copy(
            dietVegan = true,
            dietGlutenFree = true,
            avoidPeanuts = true,
        )

        val payload = state.toRecipeRequestPayload()

        assertEquals(listOf("vegan", "gluten_free"), payload.dietaryPreferences)
        assertEquals(listOf("peanuts"), payload.avoidIngredients)
        assertNull(payload.nutritionTargets)
    }

    @Test
    fun `emits dietary preferences in stable iOS-matching order`() {
        val state = baseline.copy(
            dietVegan = true,
            dietGlutenFree = true,
            dietLowCarb = true,
            dietKosher = true,
            dietHalal = true,
            dietKeto = true,
        )

        val payload = state.toRecipeRequestPayload()

        assertEquals(
            listOf("vegan", "gluten_free", "low_carb", "kosher", "halal", "keto"),
            payload.dietaryPreferences,
        )
    }

    @Test
    fun `emits avoid ingredients in stable order`() {
        val state = baseline.copy(
            avoidDairy = true,
            avoidPeanuts = true,
            avoidShellfish = true,
            avoidWheat = true,
        )

        val payload = state.toRecipeRequestPayload()

        assertEquals(listOf("dairy", "peanuts", "shellfish", "wheat"), payload.avoidIngredients)
    }

    @Test
    fun `wellness disabled produces null nutrition targets even when limits are set`() {
        val state = baseline.copy(
            wellnessEnabled = false,
            cholesterolLimit = "200",
            sodiumLimit = "1500",
            sugarLimit = "25",
        )

        val payload = state.toRecipeRequestPayload()

        assertNull(payload.nutritionTargets)
    }

    @Test
    fun `wellness enabled with valid limits produces a populated NutritionTargetsPayload`() {
        val state = baseline.copy(
            wellnessEnabled = true,
            cholesterolLimit = "200",
            sodiumLimit = "1500.5",
            sugarLimit = "25",
        )

        val payload = state.toRecipeRequestPayload()

        val targets = payload.nutritionTargets!!
        assertEquals(200.0, targets.cholesterolMg!!, 0.0)
        assertEquals(1500.5, targets.sodiumMg!!, 0.0)
        assertEquals(25.0, targets.sugarG!!, 0.0)
    }

    @Test
    fun `wellness enabled but all limits blank produces null nutrition targets`() {
        val state = baseline.copy(
            wellnessEnabled = true,
            cholesterolLimit = "",
            sodiumLimit = "",
            sugarLimit = "",
        )

        val payload = state.toRecipeRequestPayload()

        assertNull(payload.nutritionTargets)
    }

    @Test
    fun `non-positive or non-numeric limits drop out individually`() {
        val state = baseline.copy(
            wellnessEnabled = true,
            cholesterolLimit = "0",      // non-positive: dropped
            sodiumLimit = "abc",          // non-numeric: dropped
            sugarLimit = "30",            // valid: kept
        )

        val payload = state.toRecipeRequestPayload()

        val targets = payload.nutritionTargets!!
        assertNull(targets.cholesterolMg)
        assertNull(targets.sodiumMg)
        assertEquals(30.0, targets.sugarG!!, 0.0)
    }

    @Test
    fun `negative limits drop out`() {
        val state = baseline.copy(
            wellnessEnabled = true,
            cholesterolLimit = "-50",
            sodiumLimit = "1500",
            sugarLimit = "",
        )

        val payload = state.toRecipeRequestPayload()

        val targets = payload.nutritionTargets!!
        assertNull(targets.cholesterolMg)
        assertEquals(1500.0, targets.sodiumMg!!, 0.0)
        assertNull(targets.sugarG)
    }

    @Test
    fun `default state with all flags off yields empty arrays and null targets`() {
        val state = PreferenceState(
            wellnessEnabled = false,
            dietGlutenFree = false,
            avoidPeanuts = false,
        )

        val payload = state.toRecipeRequestPayload()

        assertTrue(payload.dietaryPreferences.isEmpty())
        assertTrue(payload.avoidIngredients.isEmpty())
        assertNull(payload.nutritionTargets)
    }
}
