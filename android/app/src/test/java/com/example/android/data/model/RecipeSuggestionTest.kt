package com.example.android.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the JSON contract for `/recipes/generate` responses. The shape mirrors
 * iOS — see `Models/RecipeSuggestion.swift` — so any drift between platforms
 * shows up here.
 */
@OptIn(ExperimentalSerializationApi::class)
class RecipeSuggestionTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `parses happy path response with all fields populated`() {
        val payload = """
            {
              "title": "Lemon Herb Quinoa Bowl",
              "summary": "A bright, plant-forward bowl with citrus and fresh herbs.",
              "why_it_matches": ["vegan-friendly", "low sodium"],
              "prep_minutes": 10,
              "cook_minutes": 15,
              "servings": 2,
              "ingredients": ["1 cup quinoa", "1 lemon", "parsley"],
              "steps": ["Cook quinoa", "Zest lemon", "Toss together"],
              "nutrition_notes": ["Roughly 320 kcal per serving"]
            }
        """.trimIndent()

        val recipe = json.decodeFromString(RecipeSuggestion.serializer(), payload)

        assertEquals("Lemon Herb Quinoa Bowl", recipe.title)
        assertEquals("A bright, plant-forward bowl with citrus and fresh herbs.", recipe.summary)
        assertEquals(listOf("vegan-friendly", "low sodium"), recipe.whyItMatches)
        assertEquals(10, recipe.prepMinutes)
        assertEquals(15, recipe.cookMinutes)
        assertEquals(2, recipe.servings)
        assertEquals(listOf("1 cup quinoa", "1 lemon", "parsley"), recipe.ingredients)
        assertEquals(listOf("Cook quinoa", "Zest lemon", "Toss together"), recipe.steps)
        assertEquals(listOf("Roughly 320 kcal per serving"), recipe.nutritionNotes)
    }

    @Test
    fun `round trips a recipe through serialize and deserialize`() {
        val original = RecipeSuggestion(
            title = "Chickpea Stew",
            summary = "Hearty one-pot stew.",
            whyItMatches = listOf("high fiber"),
            prepMinutes = 5,
            cookMinutes = 25,
            servings = 4,
            ingredients = listOf("chickpeas", "tomato"),
            steps = listOf("Saute aromatics", "Simmer 20 min"),
            nutritionNotes = listOf("~260 kcal/serving"),
        )

        val text = json.encodeToString(RecipeSuggestion.serializer(), original)
        val decoded = json.decodeFromString(RecipeSuggestion.serializer(), text)

        assertEquals(original, decoded)
        // Snake-case keys are present on the wire.
        assertTrue("Expected why_it_matches in payload: $text", text.contains("\"why_it_matches\""))
        assertTrue("Expected prep_minutes in payload: $text", text.contains("\"prep_minutes\""))
        assertTrue("Expected cook_minutes in payload: $text", text.contains("\"cook_minutes\""))
        assertTrue("Expected nutrition_notes in payload: $text", text.contains("\"nutrition_notes\""))
    }

    @Test
    fun `defaults to empty lists and null timing when optional fields are missing`() {
        val payload = """
            {
              "title": "Sparse Recipe",
              "summary": "Just the essentials."
            }
        """.trimIndent()

        val recipe = json.decodeFromString(RecipeSuggestion.serializer(), payload)

        assertEquals("Sparse Recipe", recipe.title)
        assertEquals("Just the essentials.", recipe.summary)
        assertTrue(recipe.whyItMatches.isEmpty())
        assertTrue(recipe.ingredients.isEmpty())
        assertTrue(recipe.steps.isEmpty())
        assertTrue(recipe.nutritionNotes.isEmpty())
        assertNull(recipe.prepMinutes)
        assertNull(recipe.cookMinutes)
        assertNull(recipe.servings)
    }

    @Test
    fun `id is derived from title and summary`() {
        val recipe = RecipeSuggestion(title = "Toast", summary = "Bread, but warmer.")
        assertEquals("ToastBread, but warmer.", recipe.id)
    }
}
