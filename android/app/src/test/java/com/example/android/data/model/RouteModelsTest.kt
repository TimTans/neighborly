package com.example.android.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the JSON contract emitted by `OptimizeRouteRequest`. Mirrors the
 * `Json` config used by `KtorNeighborlyApi.defaultHttpClient` (`explicitNulls = false`).
 */
@OptIn(ExperimentalSerializationApi::class)
class RouteModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `serializes snake_case keys with all fields`() {
        val request = OptimizeRouteRequest(
            productIds = listOf("p1", "p2"),
            userLat = 37.7749,
            userLng = -122.4194,
            mode = "cost",
            maxStops = 5,
            maxRadiusMiles = 3.0
        )

        val encoded = json.encodeToString(request)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertTrue("expected product_ids key", obj.containsKey("product_ids"))
        val productIds = obj["product_ids"]
        assertNotNull(productIds)
        assertTrue("product_ids should be a JSON array", productIds is JsonArray)
        val productIdValues = (productIds as JsonArray).map { it.jsonPrimitive.content }
        assertEquals(listOf("p1", "p2"), productIdValues)

        assertEquals(37.7749, obj["user_lat"]!!.jsonPrimitive.double, 0.0)
        assertEquals(-122.4194, obj["user_lng"]!!.jsonPrimitive.double, 0.0)
        assertEquals("cost", obj["mode"]!!.jsonPrimitive.content)
        assertEquals(5, obj["max_stops"]!!.jsonPrimitive.int)
        assertEquals(3.0, obj["max_radius_miles"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `omits null fields when explicitNulls is false`() {
        val request = OptimizeRouteRequest(productIds = listOf("only-product"))

        val encoded = json.encodeToString(request)
        val obj = json.parseToJsonElement(encoded).jsonObject

        // product_ids is always present
        assertTrue(obj.containsKey("product_ids"))
        // All optional fields must be omitted, not serialized as null
        assertFalse("user_lat should be omitted", obj.containsKey("user_lat"))
        assertFalse("user_lng should be omitted", obj.containsKey("user_lng"))
        assertFalse("mode should be omitted", obj.containsKey("mode"))
        assertFalse("max_stops should be omitted", obj.containsKey("max_stops"))
        assertFalse("max_radius_miles should be omitted", obj.containsKey("max_radius_miles"))
    }

    @Test
    fun `serializes empty product_ids as empty JSON array`() {
        val request = OptimizeRouteRequest(productIds = emptyList())
        val encoded = json.encodeToString(request)
        val obj = json.parseToJsonElement(encoded).jsonObject

        val productIds = obj["product_ids"]
        assertTrue(productIds is JsonArray)
        assertEquals(0, (productIds as JsonArray).size)
    }
}
