package com.example.android.data.repository.route

import com.example.android.data.api.NeighborlyApi
import com.example.android.data.model.OptimizedRoute
import com.example.android.data.model.Product
import com.example.android.data.model.ProductCategory
import com.example.android.data.model.ProductSearchResponse
import com.example.android.data.model.RecipeRequestPayload
import com.example.android.data.model.RecipeSuggestion
import com.example.android.data.model.RouteItem
import com.example.android.data.model.RouteStop
import com.example.android.data.model.Store
import com.example.android.data.model.StoreProduct
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-rolled fake of [NeighborlyApi]. Each method returns whatever is configured
 * via the corresponding `*Result` field, so individual tests can stage either
 * `Result.success` or `Result.failure` without pulling in a mocking library.
 */
private class FakeNeighborlyApi(
    var optimizeResult: Result<OptimizedRoute> = Result.failure(IllegalStateException("not configured")),
    var alternativesResult: Result<List<Product>> = Result.failure(IllegalStateException("not configured"))
) : NeighborlyApi {

    var lastOptimizeArgs: OptimizeArgs? = null
        private set
    var lastAlternativesProductId: String? = null
        private set

    data class OptimizeArgs(
        val productIds: List<String>,
        val userLat: Double?,
        val userLng: Double?,
        val mode: String?,
        val maxStops: Int?,
        val maxRadiusMiles: Double?
    )

    override suspend fun searchProducts(
        query: String,
        page: Int,
        pageSize: Int
    ): Result<ProductSearchResponse> = Result.failure(UnsupportedOperationException("not used"))

    override suspend fun getProduct(id: String): Result<Product> =
        Result.failure(UnsupportedOperationException("not used"))

    override suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?,
        mode: String?,
        maxStops: Int?,
        maxRadiusMiles: Double?
    ): Result<OptimizedRoute> {
        lastOptimizeArgs = OptimizeArgs(productIds, userLat, userLng, mode, maxStops, maxRadiusMiles)
        return optimizeResult
    }

    override suspend fun getAlternatives(productId: String): Result<List<Product>> {
        lastAlternativesProductId = productId
        return alternativesResult
    }

    override suspend fun generateRecipe(payload: RecipeRequestPayload): Result<RecipeSuggestion> =
        Result.failure(UnsupportedOperationException("not used"))
}

class ApiRouteRepositoryTest {

    @Test
    fun `optimizeRoute maps stops to one-based indices and effective prices`() = runTest {
        val saleItem = RouteItem(
            productId = "p-sale",
            name = "Milk",
            unitSize = "1 gal",
            price = 4.99,
            salePrice = 3.49
        )
        val regularItem = RouteItem(
            productId = "p-regular",
            name = "Bread",
            unitSize = "1 loaf",
            price = 2.50,
            salePrice = null
        )

        val backendRoute = OptimizedRoute(
            totalCost = 6.99,
            stops = listOf(
                RouteStop(
                    store = Store(id = "store-A", name = "Trader Joe's", address = "123 Main"),
                    items = listOf(saleItem),
                    subtotal = 3.49
                ),
                RouteStop(
                    store = Store(id = "store-B", name = "Safeway"),
                    items = listOf(regularItem),
                    subtotal = 2.50
                )
            ),
            itemsNotFound = listOf("avocado", "kale")
        )

        val api = FakeNeighborlyApi(optimizeResult = Result.success(backendRoute))
        val repo = ApiRouteRepository(api)

        val result = repo.optimizeRoute(
            productIds = listOf("p-sale", "p-regular"),
            userLat = 1.0,
            userLng = 2.0,
            mode = "cost",
            maxStops = 4,
            maxRadiusMiles = 5.0
        )

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()

        assertEquals(6.99, plan.totalCost!!, 0.0)
        assertEquals(2, plan.stops.size)

        // 1-based stop indices.
        assertEquals(1, plan.stops[0].index)
        assertEquals(2, plan.stops[1].index)

        // Sale item: price = salePrice, originalPrice = price.
        val firstStopItem = plan.stops[0].items.single()
        assertEquals(3.49, firstStopItem.price!!, 0.0)
        assertEquals(4.99, firstStopItem.originalPrice!!, 0.0)
        assertTrue(firstStopItem.swapAvailable)

        // Regular item: price = price, originalPrice = null (only set when on sale).
        val secondStopItem = plan.stops[1].items.single()
        assertEquals(2.50, secondStopItem.price!!, 0.0)
        assertNull(secondStopItem.originalPrice)
        assertTrue(secondStopItem.swapAvailable)

        // itemsNotFound becomes RouteMissingItem entries (productId/null reason).
        assertEquals(2, plan.missingItems.size)
        assertEquals(listOf("avocado", "kale"), plan.missingItems.map { it.name })
        plan.missingItems.forEach {
            assertNull(it.productId)
            assertNull(it.reason)
        }

        // Args are forwarded.
        val args = api.lastOptimizeArgs!!
        assertEquals(listOf("p-sale", "p-regular"), args.productIds)
        assertEquals(1.0, args.userLat!!, 0.0)
        assertEquals(2.0, args.userLng!!, 0.0)
        assertEquals("cost", args.mode)
        assertEquals(4, args.maxStops)
        assertEquals(5.0, args.maxRadiusMiles!!, 0.0)
    }

    @Test
    fun `optimizeRoute propagates imageUrl and categorySlug into RouteStopItem`() = runTest {
        val item = RouteItem(
            productId = "p-img",
            name = "Whole Milk",
            unitSize = "1 gal",
            imageUrl = "https://example.com/milk.png",
            categorySlug = "milk",
            price = 4.99,
            salePrice = null
        )
        val itemNoMedia = RouteItem(
            productId = "p-bare",
            name = "Mystery Item",
            unitSize = "1 ct",
            imageUrl = null,
            categorySlug = null,
            price = 1.00,
            salePrice = null
        )
        val backendRoute = OptimizedRoute(
            totalCost = 5.99,
            stops = listOf(
                RouteStop(
                    store = Store(id = "store-A", name = "Trader Joe's"),
                    items = listOf(item, itemNoMedia),
                    subtotal = 5.99
                )
            )
        )
        val api = FakeNeighborlyApi(optimizeResult = Result.success(backendRoute))
        val repo = ApiRouteRepository(api)

        val plan = repo.optimizeRoute(
            productIds = listOf("p-img", "p-bare"),
            userLat = null,
            userLng = null
        ).getOrThrow()

        val mappedItems = plan.stops.single().items
        assertEquals("https://example.com/milk.png", mappedItems[0].imageUrl)
        assertEquals("milk", mappedItems[0].categorySlug)
        assertNull(mappedItems[1].imageUrl)
        assertNull(mappedItems[1].categorySlug)
    }

    @Test
    fun `optimizeRoute propagates failure from the api`() = runTest {
        val boom = RuntimeException("backend exploded")
        val api = FakeNeighborlyApi(optimizeResult = Result.failure(boom))
        val repo = ApiRouteRepository(api)

        val result = repo.optimizeRoute(
            productIds = listOf("p1"),
            userLat = null,
            userLng = null,
            mode = null,
            maxStops = null,
            maxRadiusMiles = null
        )

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `optimizeRoute maps an empty route with only missing items`() = runTest {
        val backendRoute = OptimizedRoute(
            totalCost = 0.0,
            stops = emptyList(),
            itemsNotFound = listOf("ghost")
        )
        val api = FakeNeighborlyApi(optimizeResult = Result.success(backendRoute))
        val repo = ApiRouteRepository(api)

        val plan = repo.optimizeRoute(
            productIds = listOf("p1"),
            userLat = null,
            userLng = null
        ).getOrThrow()

        assertTrue(plan.stops.isEmpty())
        assertEquals(1, plan.missingItems.size)
        assertEquals("ghost", plan.missingItems.single().name)
    }

    @Test
    fun `getSwapAlternatives maps Product to RouteSwapOption using best-price store`() = runTest {
        val cheapStore = Store(id = "s-cheap", name = "BudgetMart")
        val pricierStore = Store(id = "s-pricey", name = "FancyMart")

        val product = Product(
            id = "prod-1",
            name = "Almond Milk",
            unitSize = "1 qt",
            upc = "0123456789",
            productCategories = ProductCategory(id = "c1", name = "Dairy", slug = "milk"),
            storeProducts = listOf(
                StoreProduct(
                    price = 5.99,
                    salePrice = null,
                    storeId = "s-pricey",
                    stores = pricierStore
                ),
                StoreProduct(
                    price = 4.50,
                    salePrice = 3.99,
                    storeId = "s-cheap",
                    stores = cheapStore
                )
            )
        )

        val api = FakeNeighborlyApi(alternativesResult = Result.success(listOf(product)))
        val repo = ApiRouteRepository(api)

        val swaps = repo.getSwapAlternatives("prod-original").getOrThrow()

        assertEquals("prod-original", api.lastAlternativesProductId)
        assertEquals(1, swaps.size)
        val swap = swaps.single()
        assertEquals("prod-1", swap.productId)
        assertEquals("Almond Milk", swap.name)
        assertEquals("BudgetMart", swap.storeName)
        assertEquals(3.99, swap.price!!, 0.0)
        assertNull(swap.reason)
    }

    @Test
    fun `getSwapAlternatives propagates failure from the api`() = runTest {
        val boom = IllegalStateException("network down")
        val api = FakeNeighborlyApi(alternativesResult = Result.failure(boom))
        val repo = ApiRouteRepository(api)

        val result = repo.getSwapAlternatives("prod-1")

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `getSwapAlternatives returns empty list when api returns no products`() = runTest {
        val api = FakeNeighborlyApi(alternativesResult = Result.success(emptyList()))
        val repo = ApiRouteRepository(api)

        val swaps = repo.getSwapAlternatives("prod-1").getOrThrow()
        assertTrue(swaps.isEmpty())
    }
}
