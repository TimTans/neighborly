package com.example.android.domain.wellness

import com.example.android.data.model.ProductNutrition
import com.example.android.data.repository.preferences.PreferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the iOS-parity behavior of [violations] (Models/Product.swift:35-58).
 */
class ViolationsTest {

    private fun prefs(
        wellnessEnabled: Boolean = true,
        cholesterolLimit: String = "",
        sodiumLimit: String = "",
        sugarLimit: String = "",
        avoidDairy: Boolean = false,
        avoidPeanuts: Boolean = false,
        avoidShellfish: Boolean = false,
        avoidWheat: Boolean = false,
    ): PreferenceState = PreferenceState(
        wellnessEnabled = wellnessEnabled,
        cholesterolLimit = cholesterolLimit,
        sodiumLimit = sodiumLimit,
        sugarLimit = sugarLimit,
        avoidDairy = avoidDairy,
        avoidPeanuts = avoidPeanuts,
        avoidShellfish = avoidShellfish,
        avoidWheat = avoidWheat,
    )

    @Test
    fun `no violations when nothing matches`() {
        val nutrition = ProductNutrition(
            sodiumMg = 100.0,
            cholesterolMg = 10.0,
            sugarG = 1.0,
            containsDairy = false,
            containsPeanuts = false,
            containsShellfish = false,
            containsWheat = false,
        )
        val result = nutrition.violations(prefs(sodiumLimit = "500", cholesterolLimit = "200", sugarLimit = "50"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `allergen fires when both flag and pref are set`() {
        val nutrition = ProductNutrition(containsPeanuts = true)
        val result = nutrition.violations(prefs(avoidPeanuts = true))
        assertEquals(listOf(WellnessViolation.Allergen("Peanuts")), result)
    }

    @Test
    fun `allergen does not fire when pref is off`() {
        val nutrition = ProductNutrition(containsPeanuts = true)
        val result = nutrition.violations(prefs(avoidPeanuts = false))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `allergen does not fire when product flag is null - benefit of the doubt`() {
        val nutrition = ProductNutrition(containsPeanuts = null)
        val result = nutrition.violations(prefs(avoidPeanuts = true))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `allergen does not fire when product flag is false`() {
        val nutrition = ProductNutrition(containsPeanuts = false)
        val result = nutrition.violations(prefs(avoidPeanuts = true))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation fires when wellnessEnabled is true and limit exceeded`() {
        val nutrition = ProductNutrition(sodiumMg = 800.0)
        val result = nutrition.violations(prefs(wellnessEnabled = true, sodiumLimit = "600"))
        assertEquals(
            listOf(WellnessViolation.NutrientExceeded("Sodium", 800.0, 600.0)),
            result
        )
    }

    @Test
    fun `nutrient violation does not fire when wellnessEnabled is false`() {
        val nutrition = ProductNutrition(sodiumMg = 800.0)
        val result = nutrition.violations(prefs(wellnessEnabled = false, sodiumLimit = "600"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation does not fire when actual equals limit`() {
        val nutrition = ProductNutrition(sodiumMg = 600.0)
        val result = nutrition.violations(prefs(sodiumLimit = "600"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation handles invalid input - empty string`() {
        val nutrition = ProductNutrition(sodiumMg = 800.0)
        val result = nutrition.violations(prefs(sodiumLimit = ""))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation handles invalid input - non-numeric`() {
        val nutrition = ProductNutrition(sodiumMg = 800.0)
        val result = nutrition.violations(prefs(sodiumLimit = "abc"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation handles invalid input - zero`() {
        val nutrition = ProductNutrition(sodiumMg = 800.0)
        val result = nutrition.violations(prefs(sodiumLimit = "0"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation handles invalid input - negative`() {
        val nutrition = ProductNutrition(sodiumMg = 800.0)
        val result = nutrition.violations(prefs(sodiumLimit = "-5"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `nutrient violation does not fire when product field is null`() {
        val nutrition = ProductNutrition(sodiumMg = null)
        val result = nutrition.violations(prefs(sodiumLimit = "600"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple violations accumulate across allergens and nutrients`() {
        val nutrition = ProductNutrition(
            sodiumMg = 800.0,
            cholesterolMg = 250.0,
            sugarG = 100.0,
            containsDairy = true,
            containsPeanuts = true,
            containsShellfish = true,
            containsWheat = true,
        )
        val result = nutrition.violations(
            prefs(
                wellnessEnabled = true,
                cholesterolLimit = "200",
                sodiumLimit = "600",
                sugarLimit = "50",
                avoidDairy = true,
                avoidPeanuts = true,
                avoidShellfish = true,
                avoidWheat = true,
            )
        )
        assertEquals(
            listOf(
                WellnessViolation.Allergen("Dairy"),
                WellnessViolation.Allergen("Peanuts"),
                WellnessViolation.Allergen("Shellfish"),
                WellnessViolation.Allergen("Wheat"),
                WellnessViolation.NutrientExceeded("Cholesterol", 250.0, 200.0),
                WellnessViolation.NutrientExceeded("Sodium", 800.0, 600.0),
                WellnessViolation.NutrientExceeded("Sugar", 100.0, 50.0),
            ),
            result
        )
    }
}
