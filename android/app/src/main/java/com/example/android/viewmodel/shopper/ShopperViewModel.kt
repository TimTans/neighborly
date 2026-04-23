package com.example.android.viewmodel.shopper

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.local.GroceryListItemRecord
import com.example.android.data.local.preferences.SharedPreferencesPreferenceRepository
import com.example.android.data.local.SharedPreferencesGroceryListLocalDataSource
import com.example.android.data.repository.GroceryListRepository
import com.example.android.data.repository.GroceryProductSummary
import com.example.android.data.repository.preferences.OptimizationPriority
import com.example.android.data.repository.preferences.PreferenceRepository
import com.example.android.data.repository.preferences.PreferenceState
import com.example.android.data.repository.preferences.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogProduct(
    val productId: String?,
    val upc: String,
    val name: String,
    val brand: String?,
    val unitSize: String,
    val price: Double?,
    val store: String?,
    val categoryEmoji: String = "\uD83D\uDED2",
    val imageUrl: String? = null
)

data class GroceryListItemUi(
    val id: String,
    val productId: String?,
    val upc: String,
    val name: String,
    val unitSize: String,
    val store: String,
    val price: Double,
    val quantity: Int = 1,
    val dateAddedMillis: Long
)

data class ShopperUiState(
    val groceryList: List<GroceryListItemUi> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<CatalogProduct> = emptyList(),
    val isSearchLoading: Boolean = false,
    val searchError: String? = null,
    val isRefreshingPrices: Boolean = false,
    val refreshError: String? = null,
    val preferences: PreferenceState = PreferenceState()
) {
    val filteredCatalog: List<CatalogProduct>
        get() = searchResults
}

class ShopperViewModel @JvmOverloads constructor(
    application: Application,
    private val groceryListRepository: GroceryListRepository = GroceryListRepository(
        SharedPreferencesGroceryListLocalDataSource(application.applicationContext)
    ),
    private val preferenceRepository: PreferenceRepository = SharedPreferencesPreferenceRepository(
        application.applicationContext
    )
) : AndroidViewModel(application) {
    var uiState by mutableStateOf(ShopperUiState())
        private set

    private var searchJob: Job? = null

    init {
        uiState = uiState.copy(groceryList = groceryListRepository.loadItems().toUiItems())
        viewModelScope.launch {
            val persistedPreferences = withContext(Dispatchers.IO) {
                preferenceRepository.loadPreferences()
            }
            uiState = uiState.copy(preferences = persistedPreferences)
        }
        refreshPrices()
    }

    fun updateSearchQuery(value: String) {
        searchJob?.cancel()
        uiState = uiState.copy(searchQuery = value, searchError = null)

        val query = value.trim()
        if (query.length < MIN_SEARCH_QUERY_LENGTH) {
            uiState = uiState.copy(searchResults = emptyList(), isSearchLoading = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            uiState = uiState.copy(isSearchLoading = true, searchError = null)
            groceryListRepository.searchProducts(query)
                .onSuccess { products ->
                    if (uiState.searchQuery.trim() == query) {
                        uiState = uiState.copy(
                            searchResults = products.map { it.toCatalogProduct() },
                            isSearchLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    if (uiState.searchQuery.trim() == query) {
                        uiState = uiState.copy(
                            searchResults = emptyList(),
                            isSearchLoading = false,
                            searchError = error.message ?: "Unable to search products."
                        )
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        uiState = uiState.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearchLoading = false,
            searchError = null
        )
    }

    fun addProduct(product: CatalogProduct) {
        val updatedItems = groceryListRepository.addOrIncrement(product.toProductSummary())
        uiState = uiState.copy(
            groceryList = updatedItems.toUiItems(),
            searchQuery = "",
            searchResults = emptyList(),
            isSearchLoading = false,
            searchError = null
        )
    }

    fun incrementItem(id: String) {
        uiState = uiState.copy(groceryList = groceryListRepository.incrementItem(id).toUiItems())
    }

    fun decrementItem(id: String) {
        uiState = uiState.copy(groceryList = groceryListRepository.decrementItem(id).toUiItems())
    }

    fun deleteItem(id: String) {
        uiState = uiState.copy(groceryList = groceryListRepository.deleteItem(id).toUiItems())
    }

    fun refreshPrices() {
        viewModelScope.launch {
            uiState = uiState.copy(isRefreshingPrices = true, refreshError = null)
            groceryListRepository.refreshPrices()
                .onSuccess { items ->
                    uiState = uiState.copy(
                        groceryList = items.toUiItems(),
                        isRefreshingPrices = false
                    )
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        isRefreshingPrices = false,
                        refreshError = error.message ?: "Unable to refresh prices."
                    )
                }
        }
    }

    fun updatePriority(priority: OptimizationPriority) {
        updatePreferences { it.copy(priority = priority) }
    }

    fun toggleTransportMode(mode: TransportMode) {
        val updatedModes = uiState.preferences.enabledModes.toMutableSet().apply {
            if (contains(mode)) remove(mode) else add(mode)
        }
        updatePreferences { it.copy(enabledModes = updatedModes) }
    }

    fun updateMaxTravelDistance(value: Float) {
        updatePreferences { it.copy(maxTravelDistanceMiles = value) }
    }

    fun updateMaxStops(value: Float) {
        updatePreferences { it.copy(maxStops = value) }
    }

    fun updateWellnessEnabled(enabled: Boolean) {
        updatePreferences { it.copy(wellnessEnabled = enabled) }
    }

    fun updateSodiumLimit(value: String) {
        updatePreferences { it.copy(sodiumLimit = value) }
    }

    fun updateCholesterolLimit(value: String) {
        updatePreferences { it.copy(cholesterolLimit = value) }
    }

    fun updateSugarLimit(value: String) {
        updatePreferences { it.copy(sugarLimit = value) }
    }

    fun toggleDietVegan() {
        updatePreferences { it.copy(dietVegan = !it.dietVegan) }
    }

    fun toggleDietGlutenFree() {
        updatePreferences { it.copy(dietGlutenFree = !it.dietGlutenFree) }
    }

    fun toggleDietLowCarb() {
        updatePreferences { it.copy(dietLowCarb = !it.dietLowCarb) }
    }

    fun toggleDietKosher() {
        updatePreferences { it.copy(dietKosher = !it.dietKosher) }
    }

    fun toggleDietHalal() {
        updatePreferences { it.copy(dietHalal = !it.dietHalal) }
    }

    fun toggleDietKeto() {
        updatePreferences { it.copy(dietKeto = !it.dietKeto) }
    }

    fun toggleAvoidDairy() {
        updatePreferences { it.copy(avoidDairy = !it.avoidDairy) }
    }

    fun toggleAvoidPeanuts() {
        updatePreferences { it.copy(avoidPeanuts = !it.avoidPeanuts) }
    }

    fun toggleAvoidShellfish() {
        updatePreferences { it.copy(avoidShellfish = !it.avoidShellfish) }
    }

    fun toggleAvoidWheat() {
        updatePreferences { it.copy(avoidWheat = !it.avoidWheat) }
    }

    private fun updatePreferences(transform: (PreferenceState) -> PreferenceState) {
        val updatedPreferences = transform(uiState.preferences)
        uiState = uiState.copy(preferences = updatedPreferences)
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.savePreferences(updatedPreferences)
        }
    }

    private fun List<GroceryListItemRecord>.toUiItems(): List<GroceryListItemUi> {
        return map { item ->
            GroceryListItemUi(
                id = item.id,
                productId = item.productId,
                upc = item.upc,
                name = item.name,
                unitSize = item.unitSize,
                store = item.store,
                price = item.price,
                quantity = item.quantity,
                dateAddedMillis = item.dateAddedMillis
            )
        }
    }

    private fun GroceryProductSummary.toCatalogProduct(): CatalogProduct {
        return CatalogProduct(
            productId = productId,
            upc = upc,
            name = name,
            brand = brand,
            unitSize = unitSize,
            price = bestPrice,
            store = bestStoreName,
            categoryEmoji = categoryEmoji,
            imageUrl = imageUrl
        )
    }

    private fun CatalogProduct.toProductSummary(): GroceryProductSummary {
        return GroceryProductSummary(
            productId = productId,
            upc = upc,
            name = name,
            brand = brand,
            unitSize = unitSize,
            bestPrice = price,
            bestStoreName = store,
            categoryEmoji = categoryEmoji,
            imageUrl = imageUrl
        )
    }

    private companion object {
        const val MIN_SEARCH_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
