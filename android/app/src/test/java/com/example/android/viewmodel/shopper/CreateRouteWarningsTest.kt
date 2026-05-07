package com.example.android.viewmodel.shopper

import com.example.android.data.model.ProductNutrition
import com.example.android.data.repository.preferences.PreferenceState
import com.example.android.domain.wellness.WellnessViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in [computeRouteWarnings] — the pure helper invoked by
 * [ShopperViewModel.createRoute] before the wellness warning sheet is shown.
 *
 * These cases mirror the iOS `collectViolations()`
 * (GroceryListView.swift:678-693) and the wellness-disabled branch of the
 * underlying violation engine.
 */
class CreateRouteWarningsTest {

    private fun item(
        suffix: String,
        productId: String? = "p-$suffix",
        name: String = "Item",
        upc: String = "000",
    ) = GroceryListItemUi(
        id = "row-$suffix",
        productId = productId,
        upc = upc,
        name = name,
        unitSize = "1 ct",
        store = "Aldi",
        price = 1.0,
        quantity = 1,
        dateAddedMillis = 0L,
    )

    private val peanutNutrition = ProductNutrition(
        sodiumMg = 100.0,
        cholesterolMg = 10.0,
        sugarG = 1.0,
        containsDairy = false,
        containsPeanuts = true,
        containsShellfish = false,
        containsWheat = false,
    )
    private val cleanNutrition = ProductNutrition(
        sodiumMg = 50.0,
        cholesterolMg = 5.0,
        sugarG = 0.5,
        containsDairy = false,
        containsPeanuts = false,
        containsShellfish = false,
        containsWheat = false,
    )

    @Test
    fun `empty list yields no warnings`() {
        val warnings = computeRouteWarnings(
            items = emptyList(),
            nutritionCache = emptyMap(),
            prefs = PreferenceState(),
        )
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `no item violates returns empty`() {
        val items = listOf(item("a"), item("b"))
        val cache = mapOf("p-a" to cleanNutrition, "p-b" to cleanNutrition)
        val warnings = computeRouteWarnings(items, cache, PreferenceState(avoidPeanuts = true))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `one violating item produces one warning`() {
        val items = listOf(item("a", name = "PB Crackers"), item("b", name = "Apples"))
        val cache = mapOf("p-a" to peanutNutrition, "p-b" to cleanNutrition)
        val warnings = computeRouteWarnings(items, cache, PreferenceState(avoidPeanuts = true))
        assertEquals(1, warnings.size)
        val warning = warnings.single()
        assertEquals("p-a", warning.productId)
        assertEquals("PB Crackers", warning.productName)
        assertEquals(listOf(WellnessViolation.Allergen("Peanuts")), warning.violations)
    }

    @Test
    fun `wellness disabled but allergens still trigger`() {
        // Mirrors Violations.kt: allergen flags ignore `wellnessEnabled`, only
        // nutrient-limit checks gate on it.
        val items = listOf(item("a", name = "PB Cup"))
        val cache = mapOf("p-a" to peanutNutrition)
        val prefs = PreferenceState(
            wellnessEnabled = false,
            avoidPeanuts = true,
            sodiumLimit = "1", // would have flagged if wellness were on
        )
        val warnings = computeRouteWarnings(items, cache, prefs)
        assertEquals(1, warnings.size)
        // Only the allergen, not the sodium overrun.
        assertEquals(
            listOf(WellnessViolation.Allergen("Peanuts")),
            warnings.single().violations,
        )
    }

    @Test
    fun `missing nutrition for an item excludes it from warnings`() {
        // Two items would both violate; only the cached one shows up. The
        // un-cached item is treated as "unknown", not "safe", but the route
        // intercept silently drops it because we can't justify a UX claim
        // without data — mirrors iOS collectViolations.
        val items = listOf(item("a"), item("b"))
        val cache = mapOf("p-a" to peanutNutrition) // p-b absent
        val warnings = computeRouteWarnings(items, cache, PreferenceState(avoidPeanuts = true))
        assertEquals(listOf("p-a"), warnings.map { it.productId })
    }

    @Test
    fun `null cache value treated as no data`() {
        // Distinct from "key absent": we recorded an explicit null after the
        // server returned no nutrition payload. Either way, no warning.
        val items = listOf(item("a"))
        val cache = mapOf<String, ProductNutrition?>("p-a" to null)
        val warnings = computeRouteWarnings(items, cache, PreferenceState(avoidPeanuts = true))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `items without productId are skipped`() {
        val items = listOf(item("a", productId = null, name = "Manual"))
        val warnings = computeRouteWarnings(items, emptyMap(), PreferenceState(avoidPeanuts = true))
        assertTrue(warnings.isEmpty())
    }
}
