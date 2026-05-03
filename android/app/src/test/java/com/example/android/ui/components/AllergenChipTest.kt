package com.example.android.ui.components

import com.example.android.domain.wellness.WellnessViolation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure label helper backing [AllergenChip]. The composable layer
 * itself is exercised by manual testing — these tests just lock in the chip
 * labelling and overflow behavior so future label-format tweaks don't silently
 * regress.
 */
class AllergenChipTest {

    @Test
    fun `empty violations yields empty labels`() {
        assertEquals(emptyList<String>(), violationChipLabels(emptyList()))
    }

    @Test
    fun `single allergen violation labels with leading warning`() {
        val labels = violationChipLabels(listOf(WellnessViolation.Allergen("Peanuts")))
        assertEquals(listOf("⚠ Peanuts"), labels)
    }

    @Test
    fun `nutrient violation uses the nutrient name`() {
        val labels = violationChipLabels(
            listOf(WellnessViolation.NutrientExceeded("Sodium", actual = 800.0, limit = 600.0))
        )
        assertEquals(listOf("⚠ Sodium"), labels)
    }

    @Test
    fun `under cap renders all chips`() {
        val violations = listOf(
            WellnessViolation.Allergen("Peanuts"),
            WellnessViolation.Allergen("Dairy"),
            WellnessViolation.Allergen("Wheat"),
        )
        assertEquals(
            listOf("⚠ Peanuts", "⚠ Dairy", "⚠ Wheat"),
            violationChipLabels(violations, maxVisible = 3),
        )
    }

    @Test
    fun `over cap collapses the tail into a plus N more chip`() {
        val violations = listOf(
            WellnessViolation.Allergen("Peanuts"),
            WellnessViolation.Allergen("Dairy"),
            WellnessViolation.Allergen("Wheat"),
            WellnessViolation.Allergen("Shellfish"),
            WellnessViolation.NutrientExceeded("Sodium", 800.0, 600.0),
        )
        assertEquals(
            listOf("⚠ Peanuts", "⚠ Dairy", "⚠ Wheat", "+2 more"),
            violationChipLabels(violations, maxVisible = 3),
        )
    }
}
