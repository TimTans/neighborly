package com.example.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val brand: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("unit_size")
    val unitSize: String,
    val upc: String,
    @SerialName("product_categories")
    val productCategories: ProductCategory,
    @SerialName("store_products")
    val storeProducts: List<StoreProduct> = emptyList()
) {
    val bestPrice: Double?
        get() = storeProducts.minOfOrNull { it.salePrice ?: it.price }

    val bestPriceStoreName: String?
        get() = storeProducts
            .minByOrNull { it.salePrice ?: it.price }
            ?.stores
            ?.name
}

@Serializable
data class ProductCategory(
    val id: String,
    val name: String,
    val slug: String
) {
    val emoji: String
        get() = categoryEmojiForSlug(slug)
}

/**
 * Maps a category slug to its display emoji. Used by both the [ProductCategory.emoji]
 * extension (when the full category object is available) and by route-list rendering
 * (where only a slug string is carried on `RouteStopItem`).
 *
 * Falls back to "🛒" — same default as [ProductImage]'s fallback emoji — for an
 * unknown or null slug.
 */
fun categoryEmojiForSlug(slug: String?): String = when (slug) {
    "milk" -> "🥛"
    "water" -> "💧"
    "yogurt" -> "🥄"
    "bread" -> "🍞"
    "chicken" -> "🍗"
    "turkey" -> "🦃"
    "cereal" -> "🥣"
    "eggs" -> "🥚"
    "cheese" -> "🧀"
    "fresh-fruit" -> "🍎"
    "fresh-vegetables" -> "🥬"
    "pasta-rice-grains" -> "🍝"
    "chips" -> "🍿"
    "canned-packaged-foods" -> "🥫"
    "frozen-vegetables" -> "🥦"
    "bakery" -> "🥐"
    "beverages" -> "🥤"
    "breakfast" -> "🥞"
    "deli" -> "🥪"
    "frozen" -> "❄️"
    "international" -> "🌍"
    "meatandseafood" -> "🥩"
    "pantry" -> "🫙"
    "produce" -> "🥕"
    "refrigerated" -> "🧊"
    "snacks" -> "🍿"
    else -> "🛒"
}

@Serializable
data class StoreProduct(
    val price: Double,
    @SerialName("sale_price")
    val salePrice: Double? = null,
    @SerialName("in_stock")
    val inStock: Boolean = true,
    @SerialName("store_id")
    val storeId: String,
    val stores: Store
)

@Serializable
data class Store(
    val id: String? = null,
    val name: String,
    val chain: String? = null,
    @SerialName("store_number")
    val storeNumber: String? = null,
    @SerialName("zip_code")
    val zipCode: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class ProductSearchResponse(
    val data: List<Product>,
    val count: Int
)
