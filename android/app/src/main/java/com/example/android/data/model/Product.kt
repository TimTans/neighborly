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
    val storeProducts: List<StoreProduct> = emptyList(),
    @SerialName("product_nutrition")
    val productNutrition: ProductNutrition? = null
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

/**
 * Per-product nutrition + allergen data, mirrored from iOS `ProductNutrition`
 * (Models/Product.swift:3-18). All fields are optional because the backend may
 * return rows where nutrition has not been populated yet — `null` flags are
 * treated as "no violation" (benefit of the doubt) by the violation engine.
 */
@Serializable
data class ProductNutrition(
    @SerialName("serving_size_g")
    val servingSizeG: Double? = null,
    @SerialName("servings_per_container")
    val servingsPerContainer: Double? = null,
    @SerialName("calories_kcal")
    val caloriesKcal: Double? = null,
    @SerialName("protein_g")
    val proteinG: Double? = null,
    @SerialName("fat_g")
    val fatG: Double? = null,
    @SerialName("carbs_g")
    val carbsG: Double? = null,
    @SerialName("fiber_g")
    val fiberG: Double? = null,
    @SerialName("sodium_mg")
    val sodiumMg: Double? = null,
    @SerialName("cholesterol_mg")
    val cholesterolMg: Double? = null,
    @SerialName("sugar_g")
    val sugarG: Double? = null,
    @SerialName("contains_dairy")
    val containsDairy: Boolean? = null,
    @SerialName("contains_peanuts")
    val containsPeanuts: Boolean? = null,
    @SerialName("contains_shellfish")
    val containsShellfish: Boolean? = null,
    @SerialName("contains_wheat")
    val containsWheat: Boolean? = null
)
