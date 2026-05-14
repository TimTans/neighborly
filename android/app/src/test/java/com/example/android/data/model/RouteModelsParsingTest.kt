package com.example.android.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the JSON-decoding behavior for `/routes/optimize` responses.
 */
@OptIn(ExperimentalSerializationApi::class)
class RouteModelsParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `parses happy path response with stops and missing items`() {
        val payload = """
            {
              "total_cost": 12.34,
              "stops": [
                {
                  "store": {
                    "id": "store-1",
                    "name": "Trader Joe's",
                    "address": "123 Main St",
                    "lat": 37.0,
                    "lng": -122.0
                  },
                  "items": [
                    {
                      "product_id": "prod-1",
                      "name": "Milk",
                      "unit_size": "1 gal",
                      "price": 3.99,
                      "sale_price": 2.99
                    }
                  ],
                  "subtotal": 2.99
                }
              ],
              "items_not_found": ["weird-item"]
            }
        """.trimIndent()

        val route = json.decodeFromString(OptimizedRoute.serializer(), payload)

        assertEquals(12.34, route.totalCost, 0.0)
        assertEquals(1, route.stops.size)
        val stop = route.stops.single()
        assertEquals("store-1", stop.store.id)
        assertEquals("Trader Joe's", stop.store.name)
        assertEquals("store-1", stop.id) // falls through to store.id
        assertEquals(1, stop.items.size)
        val item = stop.items.single()
        assertEquals("prod-1", item.productId)
        assertEquals("prod-1", item.id)
        assertEquals(3.99, item.price, 0.0)
        assertEquals(2.99, item.salePrice!!, 0.0)
        assertEquals(listOf("weird-item"), route.itemsNotFound)
    }

    @Test
    fun `parses no-route response with empty stops and unfound items`() {
        val payload = """
            {
              "total_cost": 0.0,
              "stops": [],
              "items_not_found": ["banana", "cucumber"]
            }
        """.trimIndent()

        val route = json.decodeFromString(OptimizedRoute.serializer(), payload)

        assertTrue(route.stops.isEmpty())
        assertEquals(listOf("banana", "cucumber"), route.itemsNotFound)
        assertEquals(0.0, route.totalCost, 0.0)
    }

    @Test
    fun `RouteStop id falls back to store name when store id is null`() {
        val payload = """
            {
              "total_cost": 1.0,
              "stops": [
                {
                  "store": { "name": "Corner Mart" },
                  "items": [],
                  "subtotal": 0.0
                }
              ]
            }
        """.trimIndent()

        val route = json.decodeFromString(OptimizedRoute.serializer(), payload)
        val stop = route.stops.single()
        assertNull(stop.store.id)
        assertEquals("Corner Mart", stop.id)
    }

    @Test
    fun `RouteItem id returns productId`() {
        val item = RouteItem(
            productId = "abc-123",
            name = "Bread",
            unitSize = "1 loaf",
            price = 4.5
        )
        assertEquals("abc-123", item.id)
    }

    @Test
    fun `defaults to empty itemsNotFound when omitted`() {
        val payload = """
            {
              "total_cost": 5.0,
              "stops": []
            }
        """.trimIndent()

        val route = json.decodeFromString(OptimizedRoute.serializer(), payload)
        assertTrue(route.itemsNotFound.isEmpty())
    }
}
