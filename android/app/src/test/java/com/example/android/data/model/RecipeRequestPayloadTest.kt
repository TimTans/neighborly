package com.example.android.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the wire format for `POST /recipes/generate`. iOS expects snake_case
 * keys (`dietary_preferences`, `avoid_ingredients`, `nutrition_targets`,
 * `cholesterol_mg`, etc) — drift here breaks cross-platform parity.
 */
@OptIn(ExperimentalSerializationApi::class)
class RecipeRequestPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `serializes snake_case keys for top-level fields`() {
        val payload = RecipeRequestPayload(
            dietaryPreferences = listOf("vegan", "gluten-free"),
            avoidIngredients = listOf("peanuts"),
            nutritionTargets = NutritionTargetsPayload(
                cholesterolMg = 200.0,
                sodiumMg = 1500.0,
                sugarG = 30.0,
            ),
        )

        val text = json.encodeToString(RecipeRequestPayload.serializer(), payload)

        assertTrue(text.contains("\"dietary_preferences\""))
        assertTrue(text.contains("\"avoid_ingredients\""))
        assertTrue(text.contains("\"nutrition_targets\""))
        assertTrue(text.contains("\"cholesterol_mg\":200"))
        assertTrue(text.contains("\"sodium_mg\":1500"))
        assertTrue(text.contains("\"sugar_g\":30"))
    }

    @Test
    fun `omits nutrition_targets when null with explicitNulls disabled`() {
        val payload = RecipeRequestPayload(
            dietaryPreferences = listOf("keto"),
            avoidIngredients = emptyList(),
            nutritionTargets = null,
        )

        val text = json.encodeToString(RecipeRequestPayload.serializer(), payload)

        assertFalse("nutrition_targets must be omitted when null: $text", text.contains("nutrition_targets"))
    }

    @Test
    fun `parses payload with snake_case keys back into RecipeRequestPayload`() {
        val text = """
            {
              "dietary_preferences": ["vegan"],
              "avoid_ingredients": ["dairy", "peanuts"],
              "nutrition_targets": {
                "cholesterol_mg": 250.5,
                "sodium_mg": 2000.0,
                "sugar_g": 25.0
              }
            }
        """.trimIndent()

        val payload = json.decodeFromString(RecipeRequestPayload.serializer(), text)

        assertEquals(listOf("vegan"), payload.dietaryPreferences)
        assertEquals(listOf("dairy", "peanuts"), payload.avoidIngredients)
        val targets = payload.nutritionTargets!!
        assertEquals(250.5, targets.cholesterolMg!!, 0.0)
        assertEquals(2000.0, targets.sodiumMg!!, 0.0)
        assertEquals(25.0, targets.sugarG!!, 0.0)
    }

    @Test
    fun `defaults to empty lists when arrays are omitted`() {
        val text = "{}"

        val payload = json.decodeFromString(RecipeRequestPayload.serializer(), text)

        assertTrue(payload.dietaryPreferences.isEmpty())
        assertTrue(payload.avoidIngredients.isEmpty())
        assertNull(payload.nutritionTargets)
    }
}
