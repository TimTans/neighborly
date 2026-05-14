package com.example.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OptimizedRoute(
    @SerialName("total_cost")
    val totalCost: Double,
    val stops: List<RouteStop>,
    @SerialName("items_not_found")
    val itemsNotFound: List<String> = emptyList()
)

@Serializable
data class RouteStop(
    val store: Store,
    val items: List<RouteItem>,
    val subtotal: Double
) {
    val id: String
        get() = store.id ?: store.name
}

@Serializable
data class RouteItem(
    @SerialName("product_id")
    val productId: String,
    val name: String,
    val brand: String? = null,
    @SerialName("unit_size")
    val unitSize: String,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("category_slug")
    val categorySlug: String? = null,
    val price: Double,
    @SerialName("sale_price")
    val salePrice: Double? = null
) {
    val id: String
        get() = productId
}

@Serializable
data class OptimizeRouteRequest(
    @SerialName("product_ids")
    val productIds: List<String>,
    @SerialName("user_lat")
    val userLat: Double? = null,
    @SerialName("user_lng")
    val userLng: Double? = null,
    @SerialName("mode")
    val mode: String? = null,
    @SerialName("max_stops")
    val maxStops: Int? = null,
    @SerialName("max_radius_miles")
    val maxRadiusMiles: Double? = null
)
