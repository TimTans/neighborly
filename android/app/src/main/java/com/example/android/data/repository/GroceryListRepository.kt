package com.example.android.data.repository

import com.example.android.data.api.KtorNeighborlyApi
import com.example.android.data.api.NeighborlyApi
import com.example.android.data.local.GroceryListItemRecord
import com.example.android.data.local.GroceryListLocalDataSource
import com.example.android.data.model.Product
import java.util.UUID

data class GroceryProductSummary(
    val productId: String?,
    val upc: String,
    val name: String,
    val brand: String?,
    val unitSize: String,
    val bestPrice: Double?,
    val bestStoreName: String?,
    val categoryEmoji: String = "\uD83D\uDED2",
    val imageUrl: String? = null
)

interface GroceryProductSearchGateway {
    suspend fun searchProducts(query: String): Result<List<GroceryProductSummary>>
    suspend fun refreshProducts(productIds: List<String>): Result<List<GroceryProductSummary>>
}

class LocalFallbackGroceryProductSearchGateway : GroceryProductSearchGateway {
    override suspend fun searchProducts(query: String): Result<List<GroceryProductSummary>> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.length < 2) return Result.success(emptyList())

        return Result.success(
            fallbackProducts
                .filter { product ->
                    product.name.lowercase().contains(normalizedQuery) ||
                        product.brand?.lowercase()?.contains(normalizedQuery) == true
                }
                .take(10)
        )
    }

    override suspend fun refreshProducts(productIds: List<String>): Result<List<GroceryProductSummary>> {
        if (productIds.isEmpty()) return Result.success(emptyList())
        return Result.success(fallbackProducts.filter { it.productId in productIds })
    }

    private companion object {
        val fallbackProducts = listOf(
            GroceryProductSummary("fallback-bananas", "0001", "Organic Bananas", "Neighborly", "1 bunch", 1.29, "Trader Joe's", "\uD83C\uDF4C"),
            GroceryProductSummary("fallback-milk", "0002", "Whole Milk", "Dairy Fresh", "1 gal", 3.49, "Aldi", "\uD83E\uDD5B"),
            GroceryProductSummary("fallback-chicken", "0003", "Chicken Breast", "Market Cut", "2 lbs", 5.99, "Costco", "\uD83C\uDF57"),
            GroceryProductSummary("fallback-bread", "0004", "Sourdough Bread", "Bakery Co.", "1 loaf", 3.99, "Trader Joe's", "\uD83C\uDF5E"),
            GroceryProductSummary("fallback-spinach", "0005", "Baby Spinach", "Green Valley", "5 oz", 2.49, "Aldi", "\uD83E\uDD6C"),
            GroceryProductSummary("fallback-yogurt", "0006", "Greek Yogurt", "Dairy Fresh", "32 oz", 4.29, "Walmart", "\uD83E\uDD63"),
            GroceryProductSummary("fallback-avocado", "0007", "Avocados", "Fresh Pick", "4 ct", 2.99, "Aldi", "\uD83E\uDD51"),
            GroceryProductSummary("fallback-oil", "0008", "Olive Oil", "Pantry Gold", "500 ml", 5.49, "Trader Joe's", "\uD83E\uDED2")
        )
    }
}

class ApiGroceryProductSearchGateway(
    private val api: NeighborlyApi = KtorNeighborlyApi()
) : GroceryProductSearchGateway {
    override suspend fun searchProducts(query: String): Result<List<GroceryProductSummary>> {
        return api.searchProducts(query)
            .map { response -> response.data.map { it.toGroceryProductSummary() } }
    }

    override suspend fun refreshProducts(productIds: List<String>): Result<List<GroceryProductSummary>> {
        if (productIds.isEmpty()) return Result.success(emptyList())

        val refreshedProducts = mutableListOf<GroceryProductSummary>()
        productIds.distinct().forEach { productId ->
            api.getProduct(productId)
                .onSuccess { product -> refreshedProducts += product.toGroceryProductSummary() }
                .onFailure { error -> return Result.failure(error) }
        }

        return Result.success(refreshedProducts)
    }

    private fun Product.toGroceryProductSummary(): GroceryProductSummary {
        return GroceryProductSummary(
            productId = id,
            upc = upc,
            name = name,
            brand = brand,
            unitSize = unitSize,
            bestPrice = bestPrice,
            bestStoreName = bestPriceStoreName,
            categoryEmoji = productCategories.emoji,
            imageUrl = imageUrl
        )
    }
}

class GroceryListRepository(
    private val localDataSource: GroceryListLocalDataSource,
    private val productSearchGateway: GroceryProductSearchGateway = ApiGroceryProductSearchGateway()
) {
    fun loadItems(): List<GroceryListItemRecord> = localDataSource.loadItems()

    fun addOrIncrement(product: GroceryProductSummary): List<GroceryListItemRecord> {
        val items = localDataSource.loadItems()
        val existing = items.firstOrNull { it.upc.isNotBlank() && it.upc == product.upc }
        val updatedItems = if (existing == null) {
            items + GroceryListItemRecord(
                id = UUID.randomUUID().toString(),
                productId = product.productId,
                upc = product.upc,
                name = product.name,
                unitSize = product.unitSize,
                store = product.bestStoreName.orEmpty(),
                price = product.bestPrice ?: 0.0,
                quantity = 1,
                dateAddedMillis = System.currentTimeMillis()
            )
        } else {
            items.map { item ->
                if (item.id == existing.id) item.copy(quantity = item.quantity + 1) else item
            }
        }
        localDataSource.saveItems(updatedItems)
        return updatedItems
    }

    fun incrementItem(id: String): List<GroceryListItemRecord> {
        val updatedItems = localDataSource.loadItems().map { item ->
            if (item.id == id) item.copy(quantity = item.quantity + 1) else item
        }
        localDataSource.saveItems(updatedItems)
        return updatedItems
    }

    fun decrementItem(id: String): List<GroceryListItemRecord> {
        val updatedItems = localDataSource.loadItems().mapNotNull { item ->
            when {
                item.id != id -> item
                item.quantity > 1 -> item.copy(quantity = item.quantity - 1)
                else -> null
            }
        }
        localDataSource.saveItems(updatedItems)
        return updatedItems
    }

    fun deleteItem(id: String): List<GroceryListItemRecord> {
        val updatedItems = localDataSource.loadItems().filterNot { it.id == id }
        localDataSource.saveItems(updatedItems)
        return updatedItems
    }

    suspend fun searchProducts(query: String): Result<List<GroceryProductSummary>> {
        return productSearchGateway.searchProducts(query)
    }

    suspend fun refreshPrices(): Result<List<GroceryListItemRecord>> {
        val items = localDataSource.loadItems()
        val productIds = items.mapNotNull { it.productId }
        val refreshedProducts = productSearchGateway.refreshProducts(productIds).getOrElse {
            return Result.failure(it)
        }
        if (refreshedProducts.isEmpty()) return Result.success(items)

        val refreshedByProductId = refreshedProducts
            .mapNotNull { product -> product.productId?.let { it to product } }
            .toMap()
        val updatedItems = items.map { item ->
            val product = refreshedByProductId[item.productId] ?: return@map item
            item.copy(
                store = product.bestStoreName ?: item.store,
                price = product.bestPrice ?: item.price,
                unitSize = product.unitSize.ifBlank { item.unitSize }
            )
        }
        localDataSource.saveItems(updatedItems)
        return Result.success(updatedItems)
    }
}
