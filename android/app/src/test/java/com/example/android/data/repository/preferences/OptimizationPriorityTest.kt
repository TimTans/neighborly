package com.example.android.data.repository.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in the mapping from user-facing priority to backend `mode`
 * query parameter on `/routes/optimize`.
 */
class OptimizationPriorityTest {

    @Test
    fun `LowestCost maps to cost`() {
        assertEquals("cost", OptimizationPriority.LowestCost.toBackendMode())
    }

    @Test
    fun `ShortestRoute maps to distance`() {
        assertEquals("distance", OptimizationPriority.ShortestRoute.toBackendMode())
    }

    @Test
    fun `FastestTrip maps to stops`() {
        assertEquals("stops", OptimizationPriority.FastestTrip.toBackendMode())
    }

    @Test
    fun `every priority has a non-empty backend mode`() {
        OptimizationPriority.values().forEach { priority ->
            assertEquals(true, priority.toBackendMode().isNotBlank())
        }
    }
}
