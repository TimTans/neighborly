package com.example.android.data.repository

import com.example.android.data.api.NeighborlyApi
import com.example.android.data.model.NutritionTargetsPayload
import com.example.android.data.model.OptimizedRoute
import com.example.android.data.model.Product
import com.example.android.data.model.ProductSearchResponse
import com.example.android.data.model.RecipeRequestPayload
import com.example.android.data.model.RecipeSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hand-rolled fake of [NeighborlyApi]. Only `generateRecipe` is exercised here;
 * the other endpoints throw if unexpectedly hit.
 */
private class FakeNeighborlyApi(
    private val responder: (RecipeRequestPayload) -> Result<RecipeSuggestion>,
) : NeighborlyApi {

    var generateRecipeCalls: Int = 0
        private set
    var lastPayload: RecipeRequestPayload? = null
        private set

    override suspend fun searchProducts(
        query: String,
        page: Int,
        pageSize: Int,
    ): Result<ProductSearchResponse> = Result.failure(UnsupportedOperationException("not used"))

    override suspend fun getProduct(id: String): Result<Product> =
        Result.failure(UnsupportedOperationException("not used"))

    override suspend fun optimizeRoute(
        productIds: List<String>,
        userLat: Double?,
        userLng: Double?,
        mode: String?,
        maxStops: Int?,
        maxRadiusMiles: Double?,
    ): Result<OptimizedRoute> = Result.failure(UnsupportedOperationException("not used"))

    override suspend fun getAlternatives(productId: String): Result<List<Product>> =
        Result.failure(UnsupportedOperationException("not used"))

    override suspend fun generateRecipe(payload: RecipeRequestPayload): Result<RecipeSuggestion> {
        generateRecipeCalls++
        lastPayload = payload
        return responder(payload)
    }
}

class RecipeRepositoryTest {

    private val recipeA = RecipeSuggestion(
        title = "Recipe A",
        summary = "A summary",
        whyItMatches = listOf("matches profile"),
        prepMinutes = 5,
        cookMinutes = 15,
        servings = 2,
        ingredients = listOf("flour", "water"),
        steps = listOf("mix", "bake"),
        nutritionNotes = listOf("low sodium"),
    )

    private val recipeB = recipeA.copy(title = "Recipe B", summary = "Different summary")

    private val payloadVegan = RecipeRequestPayload(
        dietaryPreferences = listOf("vegan"),
        avoidIngredients = listOf("peanuts"),
        nutritionTargets = null,
    )

    private val payloadKeto = RecipeRequestPayload(
        dietaryPreferences = listOf("keto"),
        avoidIngredients = emptyList(),
        nutritionTargets = NutritionTargetsPayload(sugarG = 10.0),
    )

    @Test
    fun `second call with same payload returns cache without hitting api`() = runTest {
        val api = FakeNeighborlyApi { Result.success(recipeA) }
        val repo = ApiRecipeRepository(api)

        val first = repo.generate(payloadVegan).getOrThrow()
        val second = repo.generate(payloadVegan).getOrThrow()

        assertEquals(1, api.generateRecipeCalls)
        assertEquals(recipeA, first)
        assertSame(first, second)
    }

    @Test
    fun `force=true bypasses cache and re-requests from api`() = runTest {
        var current = recipeA
        val api = FakeNeighborlyApi { Result.success(current) }
        val repo = ApiRecipeRepository(api)

        repo.generate(payloadVegan).getOrThrow()
        current = recipeB
        val refreshed = repo.generate(payloadVegan, force = true).getOrThrow()

        assertEquals(2, api.generateRecipeCalls)
        assertEquals(recipeB, refreshed)
    }

    @Test
    fun `payload change invalidates cache and re-requests`() = runTest {
        val api = FakeNeighborlyApi { payload ->
            if (payload == payloadVegan) Result.success(recipeA) else Result.success(recipeB)
        }
        val repo = ApiRecipeRepository(api)

        val veganResult = repo.generate(payloadVegan).getOrThrow()
        val ketoResult = repo.generate(payloadKeto).getOrThrow()
        val ketoAgain = repo.generate(payloadKeto).getOrThrow()

        assertEquals(2, api.generateRecipeCalls)
        assertEquals(recipeA, veganResult)
        assertEquals(recipeB, ketoResult)
        assertSame(ketoResult, ketoAgain)
    }

    @Test
    fun `failure does not poison the cache and the next call retries`() = runTest {
        val boom = RuntimeException("backend exploded")
        var hasFailed = false
        val api = FakeNeighborlyApi { _ ->
            if (!hasFailed) {
                hasFailed = true
                Result.failure(boom)
            } else {
                Result.success(recipeA)
            }
        }
        val repo = ApiRecipeRepository(api)

        val firstAttempt = repo.generate(payloadVegan)
        assertTrue(firstAttempt.isFailure)
        assertEquals(boom, firstAttempt.exceptionOrNull())

        val retry = repo.generate(payloadVegan)
        assertTrue(retry.isSuccess)
        assertEquals(recipeA, retry.getOrThrow())

        // Two calls reached the api: the failed one didn't store anything.
        assertEquals(2, api.generateRecipeCalls)
        assertNotNull(api.lastPayload)
    }
}
