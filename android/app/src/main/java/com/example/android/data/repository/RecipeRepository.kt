package com.example.android.data.repository

import com.example.android.data.api.KtorNeighborlyApi
import com.example.android.data.api.NeighborlyApi
import com.example.android.data.model.RecipeRequestPayload
import com.example.android.data.model.RecipeSuggestion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Generates a recipe based on the user's preferences. Mirrors iOS
 * `HomeViewModel.loadRecipe` semantics (HomeViewModel.swift:56-74): the
 * repository remembers the last `RecipeRequestPayload` it served and short-
 * circuits when an identical request arrives, so flipping a screen on and off
 * doesn't burn an LLM call.
 */
interface RecipeRepository {
    suspend fun generate(payload: RecipeRequestPayload, force: Boolean = false): Result<RecipeSuggestion>
}

class ApiRecipeRepository(
    private val api: NeighborlyApi = KtorNeighborlyApi(),
) : RecipeRepository {
    private val mutex = Mutex()
    private var cachedPayload: RecipeRequestPayload? = null
    private var cachedRecipe: RecipeSuggestion? = null

    override suspend fun generate(
        payload: RecipeRequestPayload,
        force: Boolean,
    ): Result<RecipeSuggestion> {
        mutex.withLock {
            val cached = cachedRecipe
            if (!force && cached != null && cachedPayload == payload) {
                return Result.success(cached)
            }
        }
        return api.generateRecipe(payload).onSuccess { recipe ->
            mutex.withLock {
                cachedPayload = payload
                cachedRecipe = recipe
            }
        }
    }
}
