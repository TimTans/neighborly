package com.example.android.viewmodel.home

import com.example.android.viewmodel.route.OptimizedRouteStop
import com.example.android.viewmodel.route.RoutePlan
import com.example.android.viewmodel.route.RouteStopItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePreviewStopsTest {

    @Test
    fun `previewStopsFrom truncates to first three stops with sequential indices`() {
        val plan = RoutePlan(
            totalCost = 30.0,
            totalDistanceMiles = null,
            estimatedDurationMinutes = null,
            savings = null,
            stops = listOf(
                stop("Aldi", "1 A St", quantity = 2),
                stop("Trader Joe's", "2 B St", quantity = 4),
                stop("Costco", "3 C St", quantity = 1),
                stop("Whole Foods", "4 D St", quantity = 5)
            )
        )

        val preview = previewStopsFrom(plan)

        assertEquals(3, preview.size)
        assertEquals(listOf(1, 2, 3), preview.map { it.index })
        assertEquals(listOf("Aldi", "Trader Joe's", "Costco"), preview.map { it.name })
        assertEquals(listOf("1 A St", "2 B St", "3 C St"), preview.map { it.address })
    }

    @Test
    fun `previewStopsFrom uses placeholder when address is null`() {
        val plan = RoutePlan(
            totalCost = null,
            totalDistanceMiles = null,
            estimatedDurationMinutes = null,
            savings = null,
            stops = listOf(stop("Mystery Mart", address = null, quantity = 1))
        )

        val preview = previewStopsFrom(plan)

        assertEquals("Address not yet available", preview.single().address)
    }

    @Test
    fun `previewStopsFrom items label matches summed quantities`() {
        val plan = RoutePlan(
            totalCost = null,
            totalDistanceMiles = null,
            estimatedDurationMinutes = null,
            savings = null,
            stops = listOf(
                stop("One Item", "X", quantity = 1),
                stop("Many Items", "Y", quantity = 5),
                stop("No Items", "Z", quantity = 0)
            )
        )

        val preview = previewStopsFrom(plan)

        assertEquals("1 item", preview[0].itemsLabel)
        assertEquals("5 items", preview[1].itemsLabel)
        assertEquals("No items", preview[2].itemsLabel)
    }

    @Test
    fun `previewStopsFrom leaves distance and time blank pending mapbox data`() {
        val plan = RoutePlan(
            totalCost = null,
            totalDistanceMiles = null,
            estimatedDurationMinutes = null,
            savings = null,
            stops = listOf(stop("Aldi", "1 A St", quantity = 1))
        )

        val preview = previewStopsFrom(plan).single()

        assertEquals("", preview.distance)
        assertEquals("", preview.timeEstimate)
    }

    private fun stop(
        name: String,
        address: String?,
        quantity: Int
    ): OptimizedRouteStop = OptimizedRouteStop(
        index = 0,
        storeId = null,
        storeName = name,
        address = address,
        distanceMiles = null,
        estimatedDurationMinutes = null,
        latitude = null,
        longitude = null,
        items = if (quantity == 0) {
            emptyList()
        } else {
            listOf(
                RouteStopItem(
                    productId = null,
                    name = "Item",
                    quantity = quantity,
                    unitSize = null,
                    price = null,
                    originalPrice = null
                )
            )
        }
    )
}
