package com.example.android.viewmodel.home

import com.example.android.data.model.RecipeRequestPayload
import com.example.android.data.model.RecipeSuggestion
import com.example.android.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeRecipeStateTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refreshRecipe success populates featuredRecipe and clears loading`() = runTest {
        val recipe = sampleRecipe("Lemon Bowl")
        val repo = FakeRecipeRepository(result = Result.success(recipe))
        val viewModel = HomeViewModel(recipeRepository = repo)

        viewModel.refreshRecipe(payload())

        assertEquals(recipe, viewModel.uiState.featuredRecipe)
        assertFalse(viewModel.uiState.isLoadingRecipe)
        assertNull(viewModel.uiState.recipeError)
        assertEquals(1, repo.callCount)
    }

    @Test
    fun `refreshRecipe failure sets error and clears loading`() = runTest {
        val repo = FakeRecipeRepository(result = Result.failure(IllegalStateException("offline")))
        val viewModel = HomeViewModel(recipeRepository = repo)

        viewModel.refreshRecipe(payload())

        assertNull(viewModel.uiState.featuredRecipe)
        assertFalse(viewModel.uiState.isLoadingRecipe)
        assertNotNull(viewModel.uiState.recipeError)
        assertTrue(viewModel.uiState.recipeError!!.contains("offline"))
    }

    @Test
    fun `refreshRecipe with unchanged payload is a no-op once recipe is populated`() = runTest {
        val repo = FakeRecipeRepository(result = Result.success(sampleRecipe("Cached")))
        val viewModel = HomeViewModel(recipeRepository = repo)
        val firstPayload = payload(diet = listOf("vegan"))

        viewModel.refreshRecipe(firstPayload)
        assertEquals(1, repo.callCount)

        viewModel.refreshRecipe(firstPayload)
        assertEquals("identical payloads should not refetch", 1, repo.callCount)
    }

    @Test
    fun `force=true bypasses payload cache and always re-requests`() = runTest {
        val repo = FakeRecipeRepository(result = Result.success(sampleRecipe("Force")))
        val viewModel = HomeViewModel(recipeRepository = repo)
        val p = payload()

        viewModel.refreshRecipe(p)
        assertEquals(1, repo.callCount)
        assertTrue(repo.lastForce == false)

        viewModel.refreshRecipe(p, force = true)
        assertEquals(2, repo.callCount)
        assertTrue(repo.lastForce == true)
    }

    @Test
    fun `retryRecipe is equivalent to refreshRecipe with force=true`() = runTest {
        val repo = FakeRecipeRepository(result = Result.success(sampleRecipe("Retry")))
        val viewModel = HomeViewModel(recipeRepository = repo)
        val p = payload()

        viewModel.refreshRecipe(p)
        viewModel.retryRecipe(p)

        assertEquals(2, repo.callCount)
        assertEquals(true, repo.lastForce)
    }

    @Test
    fun `changing payload triggers a fresh request`() = runTest {
        val repo = FakeRecipeRepository(result = Result.success(sampleRecipe("Changing")))
        val viewModel = HomeViewModel(recipeRepository = repo)

        viewModel.refreshRecipe(payload(diet = listOf("vegan")))
        viewModel.refreshRecipe(payload(diet = listOf("keto")))

        assertEquals(2, repo.callCount)
    }

    private fun payload(
        diet: List<String> = emptyList(),
        avoid: List<String> = emptyList(),
    ) = RecipeRequestPayload(dietaryPreferences = diet, avoidIngredients = avoid)

    private fun sampleRecipe(title: String) = RecipeSuggestion(
        title = title,
        summary = "summary",
        whyItMatches = listOf("matches"),
        prepMinutes = 10,
        cookMinutes = 15,
        servings = 2,
        ingredients = listOf("salt"),
        steps = listOf("cook"),
        nutritionNotes = listOf("low sodium"),
    )

    private class FakeRecipeRepository(
        private val result: Result<RecipeSuggestion>
    ) : RecipeRepository {
        var callCount: Int = 0
            private set
        var lastPayload: RecipeRequestPayload? = null
            private set
        var lastForce: Boolean? = null
            private set

        override suspend fun generate(
            payload: RecipeRequestPayload,
            force: Boolean
        ): Result<RecipeSuggestion> {
            callCount += 1
            lastPayload = payload
            lastForce = force
            return result
        }
    }
}
