package com.example.android.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Product JSON contract end-to-end including ProductNutrition
 * (added in S2.1) and the multi-store shape used by the search and detail
 * flows. Mirrors the snake_case payload the FastAPI backend emits.
 */
class ProductParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `parses product with nutrition allergens and multi-store prices`() {
        val raw = """
            {
              "id": "p1",
              "name": "Almond Butter",
              "brand": "Acme",
              "image_url": "https://example.com/img.png",
              "unit_size": "16 oz",
              "upc": "0000001",
              "product_categories": {"id": "c1", "name": "Pantry", "slug": "pantry"},
              "store_products": [
                {"price": 8.99, "sale_price": 6.99, "in_stock": true, "store_id": "s1",
                 "stores": {"id": "s1", "name": "Aldi", "lat": 40.0, "lng": -74.0}},
                {"price": 7.49, "in_stock": false, "store_id": "s2",
                 "stores": {"name": "Trader Joe's"}}
              ],
              "product_nutrition": {
                "serving_size_g": 30.0,
                "calories_kcal": 190.0,
                "sodium_mg": 0.0,
                "sugar_g": 2.0,
                "contains_peanuts": false,
                "contains_dairy": false
              }
            }
        """.trimIndent()

        val product = json.decodeFromString(Product.serializer(), raw)

        assertEquals("p1", product.id)
        assertEquals("Acme", product.brand)
        assertEquals("16 oz", product.unitSize)
        assertEquals("https://example.com/img.png", product.imageUrl)
        assertEquals("pantry", product.productCategories.slug)
        assertEquals("🫙", product.productCategories.emoji)

        // bestPrice picks the sale price across stores
        assertEquals(6.99, product.bestPrice!!, 0.0001)
        assertEquals("Aldi", product.bestPriceStoreName)

        // out-of-stock store still parses
        assertEquals(false, product.storeProducts[1].inStock)

        // nutrition propagates with snake_case keys
        val nutrition = product.productNutrition
        assertNotNull(nutrition)
        assertEquals(30.0, nutrition!!.servingSizeG!!, 0.0)
        assertEquals(190.0, nutrition.caloriesKcal!!, 0.0)
        assertEquals(0.0, nutrition.sodiumMg!!, 0.0)
        assertEquals(false, nutrition.containsPeanuts)
    }

    @Test
    fun `parses product with no nutrition and no brand`() {
        val raw = """
            {
              "id": "p2",
              "name": "Bananas",
              "unit_size": "1 lb",
              "upc": "0000002",
              "product_categories": {"id": "c-prod", "name": "Produce", "slug": "produce"},
              "store_products": []
            }
        """.trimIndent()

        val product = json.decodeFromString(Product.serializer(), raw)

        assertEquals("p2", product.id)
        assertNull(product.brand)
        assertNull(product.productNutrition)
        assertTrue(product.storeProducts.isEmpty())
        assertNull(product.bestPrice)
        assertNull(product.bestPriceStoreName)
    }

    @Test
    fun `ProductSearchResponse parses with multiple products and count`() {
        val raw = """
            {
              "data": [
                {"id":"a","name":"A","unit_size":"1 oz","upc":"a","product_categories":{"id":"c","name":"Pantry","slug":"pantry"},"store_products":[]},
                {"id":"b","name":"B","unit_size":"1 oz","upc":"b","product_categories":{"id":"c","name":"Pantry","slug":"pantry"},"store_products":[]}
              ],
              "count": 2
            }
        """.trimIndent()

        val response = json.decodeFromString(ProductSearchResponse.serializer(), raw)

        assertEquals(2, response.count)
        assertEquals(listOf("a", "b"), response.data.map { it.id })
    }

    @Test
    fun `category emoji helper falls through to cart for unknown slugs`() {
        assertEquals("🛒", categoryEmojiForSlug(null))
        assertEquals("🛒", categoryEmojiForSlug(""))
        assertEquals("🛒", categoryEmojiForSlug("not-a-real-category"))
        assertEquals("🥛", categoryEmojiForSlug("milk"))
    }
}
