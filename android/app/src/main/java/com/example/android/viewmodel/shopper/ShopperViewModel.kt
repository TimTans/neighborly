package com.example.android.viewmodel.shopper

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.api.KtorNeighborlyApi
import com.example.android.data.api.NeighborlyApi
import com.example.android.data.connectivity.NetworkMonitor
import com.example.android.data.local.GroceryListItemRecord
import com.example.android.data.local.preferences.SharedPreferencesPreferenceRepository
import com.example.android.data.local.SharedPreferencesGroceryListLocalDataSource
import com.example.android.data.model.Product
import com.example.android.data.repository.GroceryListRepository
import com.example.android.data.repository.GroceryProductSummary
import com.example.android.data.repository.toGroceryProductSummary
import com.example.android.data.repository.preferences.OptimizationPriority
import com.example.android.data.repository.preferences.PreferenceRepository
import com.example.android.data.repository.preferences.PreferenceState
import com.example.android.data.repository.preferences.TransportMode
import com.example.android.data.location.UserCoordinates
import com.example.android.viewmodel.route.RouteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    val preferences: PreferenceState = PreferenceState(),
    val productSheet: GroceryProductSummary? = null,
    val itemSheet: GroceryListItemRecord? = null,
    val sheetProduct: Product? = null,
    val isLoadingSheetProduct: Boolean = false,
    val sheetProductError: String? = null,
    val routeCreationError: String? = null
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
    ),
    private val networkMonitor: NetworkMonitor? = null,
    private val api: NeighborlyApi = KtorNeighborlyApi()
) : AndroidViewModel(application) {
    var uiState by mutableStateOf(ShopperUiState())
        private set

    private var searchJob: Job? = null
    private var sheetProductJob: Job? = null

    init {
        uiState = uiState.copy(groceryList = groceryListRepository.loadItems().toUiItems())
        viewModelScope.launch {
            val persistedPreferences = withContext(Dispatchers.IO) {
                preferenceRepository.loadPreferences()
            }
            uiState = uiState.copy(preferences = persistedPreferences)
        }
        refreshPrices()
        observeConnectivity()
    }

    private fun observeConnectivity() {
        val monitor = networkMonitor ?: return
        viewModelScope.launch {
            // Skip the very first emission — `init` already kicks off `refreshPrices()`.
            // Only `false -> true` transitions after that should trigger a refresh, mirroring
            // iOS `NetworkMonitor.didReconnect` (GroceryListView.swift:9-36).
            var previous: Boolean? = null
            monitor.isOnline.collect { current ->
                if (previous == false && current) {
                    refreshPrices()
                }
                previous = current
            }
        }
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
        val updated = groceryListRepository.incrementItem(id)
        uiState = uiState.copy(
            groceryList = updated.toUiItems(),
            itemSheet = uiState.itemSheet?.let { current ->
                if (current.id == id) updated.firstOrNull { it.id == id } else current
            }
        )
    }

    fun decrementItem(id: String) {
        val updated = groceryListRepository.decrementItem(id)
        val nextItemSheet = uiState.itemSheet?.let { current ->
            if (current.id == id) updated.firstOrNull { it.id == id } else current
        }
        uiState = uiState.copy(
            groceryList = updated.toUiItems(),
            itemSheet = nextItemSheet
        )
    }

    fun deleteItem(id: String) {
        val updated = groceryListRepository.deleteItem(id)
        val nextItemSheet = uiState.itemSheet?.takeIf { it.id != id }
        uiState = uiState.copy(
            groceryList = updated.toUiItems(),
            itemSheet = nextItemSheet
        )
    }

    fun showProductSheet(summary: GroceryProductSummary) {
        sheetProductJob?.cancel()
        uiState = uiState.copy(
            productSheet = summary,
            itemSheet = null,
            sheetProduct = null,
            sheetProductError = null,
            isLoadingSheetProduct = false
        )
        loadSheetProduct(summary.productId)
    }

    fun dismissProductSheet() {
        sheetProductJob?.cancel()
        uiState = uiState.copy(
            productSheet = null,
            sheetProduct = null,
            isLoadingSheetProduct = false,
            sheetProductError = null
        )
    }

    fun showItemSheet(item: GroceryListItemRecord) {
        sheetProductJob?.cancel()
        uiState = uiState.copy(
            itemSheet = item,
            productSheet = null,
            sheetProduct = null,
            sheetProductError = null,
            isLoadingSheetProduct = false
        )
        loadSheetProduct(item.productId)
    }

    fun dismissItemSheet() {
        sheetProductJob?.cancel()
        uiState = uiState.copy(
            itemSheet = null,
            sheetProduct = null,
            isLoadingSheetProduct = false,
            sheetProductError = null
        )
    }

    fun addFromProductSheet() {
        val summary = uiState.productSheet ?: return
        val updatedItems = groceryListRepository.addOrIncrement(summary)
        uiState = uiState.copy(
            groceryList = updatedItems.toUiItems(),
            searchQuery = "",
            searchResults = emptyList(),
            isSearchLoading = false,
            searchError = null,
            productSheet = null,
            sheetProduct = null,
            isLoadingSheetProduct = false,
            sheetProductError = null
        )
    }

    private fun loadSheetProduct(productId: String?) {
        if (productId.isNullOrBlank()) return
        uiState = uiState.copy(isLoadingSheetProduct = true, sheetProductError = null)
        sheetProductJob = viewModelScope.launch {
            api.getProduct(productId)
                .onSuccess { product ->
                    if (uiState.productSheet?.productId == productId ||
                        uiState.itemSheet?.productId == productId
                    ) {
                        uiState = uiState.copy(
                            sheetProduct = product,
                            isLoadingSheetProduct = false,
                            sheetProductError = null
                        )
                    }
                }
                .onFailure { error ->
                    if (uiState.productSheet?.productId == productId ||
                        uiState.itemSheet?.productId == productId
                    ) {
                        uiState = uiState.copy(
                            isLoadingSheetProduct = false,
                            sheetProductError = error.message ?: "Unable to load product details."
                        )
                    }
                }
        }
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

    /**
     * Kicks off route optimization for the current grocery list. The screen layer
     * is responsible for requesting location permission first; we just take an
     * optional [userLocation] and forward it to the route view model along with
     * the user's persisted preferences.
     *
     * Empty-product-ID lists set [ShopperUiState.routeCreationError] instead of
     * hitting the API — mirrors iOS `optimizeRoute` (GroceryListView.swift:523-528).
     *
     * The slider sentinel `11` (max value) is interpreted as "unlimited" and
     * sent to the backend as `null` for both `max_stops` and `max_radius_miles`,
     * matching iOS `Priority` mapping in PreferencesView.swift.
     */
    fun createRoute(
        routeViewModel: RouteViewModel,
        userLocation: UserCoordinates?
    ) {
        val productIds = uiState.groceryList.mapNotNull { item ->
            item.productId?.takeIf { it.isNotBlank() }
        }
        if (productIds.isEmpty()) {
            uiState = uiState.copy(
                routeCreationError = "Add at least one priced product before creating a route."
            )
            return
        }

        val preferences = uiState.preferences
        val mode = preferences.priority.toBackendMode()
        val maxStops = preferences.maxStops.toInt().takeIf { it < UNLIMITED_PREFERENCE_SLIDER_VALUE }
        val maxRadiusMiles = preferences.maxTravelDistanceMiles.toDouble()
            .takeIf { it < UNLIMITED_PREFERENCE_SLIDER_VALUE }

        uiState = uiState.copy(routeCreationError = null)
        routeViewModel.createRoute(
            productIds = productIds,
            userLat = userLocation?.latitude,
            userLng = userLocation?.longitude,
            mode = mode,
            maxStops = maxStops,
            maxRadiusMiles = maxRadiusMiles
        )
    }

    fun clearRouteCreationError() {
        if (uiState.routeCreationError != null) {
            uiState = uiState.copy(routeCreationError = null)
        }
    }

    fun findItemIdByProductId(productId: String?): String? {
        if (productId.isNullOrBlank()) return null
        return uiState.groceryList.firstOrNull { it.productId == productId }?.id
    }

    /**
     * Apply a swap selected from the route's alternatives dialog. Fetches the full
     * [Product] for [replacementProductId] so the persisted grocery-list record can
     * carry the same field set as items added via search, then replaces the row
     * identified by [currentItemId] in the local list. On success [onComplete] is
     * invoked with the updated list of product IDs so the route can be re-optimized.
     */
    fun applySwap(
        currentItemId: String,
        replacementProductId: String,
        onComplete: (productIds: List<String>) -> Unit
    ) {
        viewModelScope.launch {
            api.getProduct(replacementProductId)
                .onSuccess { product ->
                    val summary = product.toGroceryProductSummary()
                    val updatedItems = groceryListRepository.replaceProduct(currentItemId, summary)
                    uiState = uiState.copy(
                        groceryList = updatedItems.toUiItems(),
                        refreshError = null
                    )
                    onComplete(updatedItems.mapNotNull { it.productId })
                }
                .onFailure { error ->
                    uiState = uiState.copy(
                        refreshError = error.message ?: "Unable to apply swap."
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
        // Preference sliders (`maxStops`, `maxTravelDistanceMiles`) range 1..11; the
        // top notch represents "unlimited" — drop the cap when sending to the
        // backend. Mirrors iOS PreferencesView.swift sentinel handling.
        const val UNLIMITED_PREFERENCE_SLIDER_VALUE = 11
    }
}
