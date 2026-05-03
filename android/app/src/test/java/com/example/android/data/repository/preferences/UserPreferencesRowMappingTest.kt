package com.example.android.data.repository.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the cross-platform round trip between [PreferenceState] and the
 * Supabase `user_preferences` row. iOS shipped first
 * (`ios/Neighborly/Neighborly/PreferencesService.swift`), so any mismatch
 * with these tests means iOS users will see corrupted preferences after an
 * Android save.
 */
class UserPreferencesRowMappingTest {

    private val userId = "user-uuid-1"

    @Test
    fun `round trip preserves a typical state`() {
        val original = PreferenceState(
            priority = OptimizationPriority.ShortestRoute,
            enabledModes = setOf(TransportMode.Walking, TransportMode.Car),
            maxTravelDistanceMiles = 5f,
            maxStops = 4f,
            wellnessEnabled = true,
            cholesterolLimit = "200",
            sodiumLimit = "1500",
            sugarLimit = "30",
            dietVegan = true,
            dietGlutenFree = false,
            dietLowCarb = true,
            dietKosher = false,
            dietHalal = false,
            dietKeto = false,
            avoidDairy = true,
            avoidPeanuts = false,
            avoidShellfish = true,
            avoidWheat = false,
        )

        val row = original.toRow(userId)
        val recovered = row.toPreferenceState()

        assertEquals(original.priority, recovered.priority)
        assertEquals(original.enabledModes, recovered.enabledModes)
        // 5 mi -> ~8.0467 km -> ~5.0000 mi after the inverse multiply.
        // Allow tiny float drift through km roundtrip.
        assertTrue(
            "miles drifted: ${recovered.maxTravelDistanceMiles}",
            kotlin.math.abs(recovered.maxTravelDistanceMiles - 5f) < 0.01f
        )
        assertEquals(4f, recovered.maxStops)
        assertEquals(original.wellnessEnabled, recovered.wellnessEnabled)
        assertEquals(original.cholesterolLimit, recovered.cholesterolLimit)
        assertEquals(original.sodiumLimit, recovered.sodiumLimit)
        assertEquals(original.sugarLimit, recovered.sugarLimit)
        assertEquals(original.dietVegan, recovered.dietVegan)
        assertEquals(original.dietGlutenFree, recovered.dietGlutenFree)
        assertEquals(original.dietLowCarb, recovered.dietLowCarb)
        assertEquals(original.dietKosher, recovered.dietKosher)
        assertEquals(original.dietHalal, recovered.dietHalal)
        assertEquals(original.dietKeto, recovered.dietKeto)
        assertEquals(original.avoidDairy, recovered.avoidDairy)
        assertEquals(original.avoidPeanuts, recovered.avoidPeanuts)
        assertEquals(original.avoidShellfish, recovered.avoidShellfish)
        assertEquals(original.avoidWheat, recovered.avoidWheat)
    }

    @Test
    fun `5 miles encodes to roughly 8 point 0467 km`() {
        val state = PreferenceState(maxTravelDistanceMiles = 5f)
        val row = state.toRow(userId)
        // 5 * 1.60934 = 8.0467
        assertTrue(
            "expected ~8.0467 km, got ${row.maxRadiusKm}",
            kotlin.math.abs((row.maxRadiusKm ?: 0.0) - 8.0467) < 0.001
        )
    }

    @Test
    fun `unlimited slider value 11 encodes as 0_0 km`() {
        val state = PreferenceState(maxTravelDistanceMiles = 11f, maxStops = 11f)
        val row = state.toRow(userId)
        assertEquals(0.0, row.maxRadiusKm!!, 0.0)
        assertEquals(0.0, row.maxStops!!, 0.0)
    }

    @Test
    fun `0_0 km decodes to slider value 11 (unlimited)`() {
        val row = UserPreferencesRow(
            userId = userId,
            maxRadiusKm = 0.0,
            maxStops = 0.0,
        )
        val state = row.toPreferenceState()
        assertEquals(UNLIMITED_SENTINEL_SLIDER_VALUE, state.maxTravelDistanceMiles)
        assertEquals(UNLIMITED_SENTINEL_SLIDER_VALUE, state.maxStops)
    }

    @Test
    fun `optimizationMode label round trips through fromLabel`() {
        OptimizationPriority.values().forEach { priority ->
            val state = PreferenceState(priority = priority)
            val row = state.toRow(userId)
            assertEquals(priority.label, row.optimizationMode)
            assertEquals(priority, row.toPreferenceState().priority)
        }
    }

    @Test
    fun `unknown optimization mode falls back to LowestCost`() {
        val row = UserPreferencesRow(userId = userId, optimizationMode = "Garbled Mode")
        assertEquals(OptimizationPriority.LowestCost, row.toPreferenceState().priority)
    }

    @Test
    fun `wellness fan-out collapses into single switch on read`() {
        // Android collapses the three iOS per-field flags into one switch.
        // Only one of the three being true should still surface as enabled.
        val row = UserPreferencesRow(
            userId = userId,
            sodiumEnabled = false,
            cholesterolEnabled = true,
            sugarEnabled = false,
        )
        assertEquals(true, row.toPreferenceState().wellnessEnabled)
    }

    @Test
    fun `all-false wellness flags decode as disabled`() {
        val row = UserPreferencesRow(
            userId = userId,
            sodiumEnabled = false,
            cholesterolEnabled = false,
            sugarEnabled = false,
        )
        assertEquals(false, row.toPreferenceState().wellnessEnabled)
    }

    @Test
    fun `missing wellness flags fall back to defaults (true)`() {
        val row = UserPreferencesRow(userId = userId)
        // Defaults from PreferenceState() — wellness on.
        assertEquals(true, row.toPreferenceState().wellnessEnabled)
    }

    @Test
    fun `wellness flags are fanned out on save`() {
        val state = PreferenceState(wellnessEnabled = true)
        val row = state.toRow(userId)
        assertEquals(true, row.sodiumEnabled)
        assertEquals(true, row.cholesterolEnabled)
        assertEquals(true, row.sugarEnabled)
    }

    @Test
    fun `transport modes default to enabled when absent`() {
        // Mirrors PreferencesService.swift:94-98 — missing flag -> enabled.
        val row = UserPreferencesRow(userId = userId)
        val state = row.toPreferenceState()
        assertEquals(
            setOf(TransportMode.Walking, TransportMode.PublicTransit, TransportMode.Car),
            state.enabledModes
        )
    }

    @Test
    fun `empty wellness limits become null on save`() {
        // iOS stores empty strings as null (PreferencesService.swift:149).
        val state = PreferenceState(
            sodiumLimit = "",
            cholesterolLimit = "",
            sugarLimit = "",
        )
        val row = state.toRow(userId)
        assertEquals(null, row.sodiumLimit)
        assertEquals(null, row.cholesterolLimit)
        assertEquals(null, row.sugarLimit)
    }

    @Test
    fun `null wellness limits decode as empty strings`() {
        val row = UserPreferencesRow(userId = userId)
        val state = row.toPreferenceState()
        assertEquals("", state.sodiumLimit)
        assertEquals("", state.cholesterolLimit)
        assertEquals("", state.sugarLimit)
    }
}
