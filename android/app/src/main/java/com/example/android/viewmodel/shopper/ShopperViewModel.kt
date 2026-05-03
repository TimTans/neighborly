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
import com.example.android.data.model.ProductNutrition
import com.example.android.data.repository.GroceryListRepository
import com.example.android.data.repository.GroceryProductSummary
import com.example.android.data.repository.toGroceryProductSummary
import com.example.android.data.repository.preferences.OptimizationPriority
import com.example.android.data.repository.preferences.PreferenceRemoteRepository
import com.example.android.data.repository.preferences.PreferenceRepository
import com.example.android.data.repository.preferences.PreferenceState
import com.example.android.data.repository.preferences.TransportMode
import com.example.android.data.location.UserCoordinates
import com.example.android.domain.wellness.WellnessViolation
import com.example.android.domain.wellness.violations
import com.example.android.viewmodel.route.RouteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row in the wellness warning sheet shown before "Create Route" optimizes.
 * Mirrors the iOS tuple at GroceryListView.swift:678-693.
 */
data class WarningItem(
    val productId: String,
    val productName: String,
    val violations: List<WellnessViolation>,
)

/**
 * Pure helper that produces the wellness warning rows for a grocery list.
 *
 * - Items without a productId are skipped (no nutrition can ever be looked up
 *   for them — they were added before products had server IDs).
 * - Items whose nutrition is *not* yet cached are also skipped. Callers should
 *   call [ShopperViewModel.ensureNutritionLoaded] first when they need the
 *   warnings to be exhaustive (e.g. before showing the "Create Route" sheet).
 * - Allergen violations always fire; nutrient-limit violations only fire when
 *   `prefs.wellnessEnabled = true` — that gating lives inside
 *   [com.example.android.domain.wellness.violations] so we just defer to it
 *   here.
 */
internal fun computeRouteWarnings(
    items: List<GroceryListItemUi>,
    nutritionCache: Map<String, ProductNutrition?>,
    prefs: PreferenceState,
): List<WarningItem> {
    return items.mapNotNull { item ->
        val productId = item.productId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (!nutritionCache.containsKey(productId)) return@mapNotNull null
        val nutrition = nutritionCache[productId] ?: return@mapNotNull null
        val violations = nutrition.violations(prefs)
        if (violations.isEmpty()) return@mapNotNull null
        WarningItem(productId = productId, productName = item.name, violations = violations)
    }
}

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
    val routeCreationError: String? = null,
    /**
     * Per-`productId` nutrition cache, mirroring iOS `nutritionCache`
     * (GroceryListView.swift:130). Keys present with a `null` value mean the
     * server was queried and returned no nutrition payload — recording that
     * absence prevents redundant refetches.
     */
    val nutritionCache: Map<String, ProductNutrition?> = emptyMap(),
    /**
     * Non-null while the wellness warning sheet is in front of "Create Route".
     * Set by [ShopperViewModel.createRoute] when wellness violations exist for
     * the current grocery list; cleared by either
     * [ShopperViewModel.confirmRouteCreationDespiteWarnings] (proceed) or
     * [ShopperViewModel.dismissRouteWarnings] (cancel). Mirrors iOS
     * `wellnessWarnings` (GroceryListView.swift:678-693).
     */
    val pendingRouteWarnings: List<WarningItem>? = null,
    /**
     * Set after the first successful (or attempted) `fetch` against Supabase
     * for the active session — guards against the screen overlaying remote
     * values on top of in-progress local edits whenever the user revisits the
     * Preferences screen. Mirrors the iOS `appearedOnce` pattern in
     * PreferencesView.swift:360-385.
     */
    val hasReconciledRemote: Boolean = false,
    /**
     * Most recent remote-sync error, or `null` after a successful
     * fetch/save. The Preferences screen surfaces this near the wellness
     * card so the user knows when changes failed to upload.
     */
    val remoteSyncError: String? = null,
    /**
     * `true` while the most recent local edit has been pushed to Supabase
     * successfully and not yet superseded. Used to render a "Synced" hint.
     */
    val isRemoteSynced: Boolean = false
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
    private val api: NeighborlyApi = KtorNeighborlyApi(),
    /**
     * Remote-sync repo. `null` is allowed so unit tests don't need to stand
     * up a Supabase client. In production [MainActivity] wires the
     * `SupabasePreferenceRemoteRepository` singleton.
     */
    private val remotePreferenceRepository: PreferenceRemoteRepository? = null
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
                    val updatedCache = uiState.nutritionCache + (productId to product.productNutrition)
                    if (uiState.productSheet?.productId == productId ||
                        uiState.itemSheet?.productId == productId
                    ) {
                        uiState = uiState.copy(
                            sheetProduct = product,
                            isLoadingSheetProduct = false,
                            sheetProductError = null,
                            nutritionCache = updatedCache
                        )
                    } else {
                        uiState = uiState.copy(nutritionCache = updatedCache)
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

    /**
     * Lazily fetches and caches the [ProductNutrition] for [productId]. Mirrors
     * iOS `loadNutrition(for:)` (GroceryListView.swift:131-148). No-ops if the
     * key is already cached (even with a `null` value — that means we already
     * fetched and the server has no nutrition for this product). Errors are
     * swallowed silently because wellness chips are informational; a failed
     * fetch should never block the UI or propagate an error to the user.
     */
    fun loadNutritionFor(productId: String) {
        if (productId.isBlank()) return
        if (uiState.nutritionCache.containsKey(productId)) return
        viewModelScope.launch {
            api.getProduct(productId)
                .onSuccess { product ->
                    uiState = uiState.copy(
                        nutritionCache = uiState.nutritionCache + (productId to product.productNutrition)
                    )
                }
        }
    }

    /**
     * Returns the wellness violations for the cached nutrition of [productId]
     * given the user's current preferences. Returns an empty list when
     * [productId] is null/blank, when nutrition has not yet been fetched, or
     * when the server returned no nutrition for that product.
     */
    fun violationsFor(productId: String?): List<WellnessViolation> {
        if (productId.isNullOrBlank()) return emptyList()
        if (!uiState.nutritionCache.containsKey(productId)) return emptyList()
        val nutrition = uiState.nutritionCache[productId] ?: return emptyList()
        return nutrition.violations(uiState.preferences)
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

        uiState = uiState.copy(routeCreationError = null)
        // Set pending IDs eagerly so the iOS-style "intercept then continue"
        // path in [confirmRouteCreationDespiteWarnings] can call
        // [RouteViewModel.optimizePendingRoute] without re-supplying them.
        routeViewModel.setPendingProducts(productIds)

        viewModelScope.launch {
            ensureNutritionLoaded(productIds)
            val warnings = computeRouteWarnings(
                items = uiState.groceryList,
                nutritionCache = uiState.nutritionCache,
                prefs = uiState.preferences,
            )
            if (warnings.isNotEmpty()) {
                uiState = uiState.copy(pendingRouteWarnings = warnings)
                return@launch
            }
            launchOptimize(routeViewModel, userLocation)
        }
    }

    /**
     * Continues the optimize call that was paused by the wellness warning
     * sheet. Clears [ShopperUiState.pendingRouteWarnings] and forwards the
     * already-pending products in the [RouteViewModel] to the backend.
     */
    fun confirmRouteCreationDespiteWarnings(
        routeViewModel: RouteViewModel,
        userLocation: UserCoordinates?,
    ) {
        uiState = uiState.copy(pendingRouteWarnings = null)
        launchOptimize(routeViewModel, userLocation)
    }

    /**
     * User chose to bail out of "Create Route" from the warning sheet. Drops
     * the pending warnings without firing optimize.
     */
    fun dismissRouteWarnings() {
        if (uiState.pendingRouteWarnings != null) {
            uiState = uiState.copy(pendingRouteWarnings = null)
        }
    }

    private fun launchOptimize(
        routeViewModel: RouteViewModel,
        userLocation: UserCoordinates?,
    ) {
        val preferences = uiState.preferences
        val mode = preferences.priority.toBackendMode()
        val maxStops = preferences.maxStops.toInt().takeIf { it < UNLIMITED_PREFERENCE_SLIDER_VALUE }
        val maxRadiusMiles = preferences.maxTravelDistanceMiles.toDouble()
            .takeIf { it < UNLIMITED_PREFERENCE_SLIDER_VALUE }

        routeViewModel.optimizePendingRoute(
            userLat = userLocation?.latitude,
            userLng = userLocation?.longitude,
            mode = mode,
            maxStops = maxStops,
            maxRadiusMiles = maxRadiusMiles,
        )
    }

    /**
     * Eagerly fetches nutrition for any [productIds] not already present in the
     * cache. Fetches run in parallel — wellness warnings should never serialize
     * a 20-item list. Failures are silently swallowed; missing nutrition just
     * means an item gets excluded from warnings, mirroring iOS
     * `loadNutrition()` (GroceryListView.swift:663-676).
     */
    internal suspend fun ensureNutritionLoaded(productIds: List<String>) {
        val missing = productIds
            .filter { it.isNotBlank() && !uiState.nutritionCache.containsKey(it) }
            .distinct()
        if (missing.isEmpty()) return

        val fetched: Map<String, ProductNutrition?> = coroutineScope {
            missing.map { id ->
                async {
                    id to api.getProduct(id).getOrNull()?.productNutrition
                }
            }.awaitAll().toMap()
        }
        // Merge — drop entries whose key already showed up via another path
        // while we were awaiting (loadNutritionFor / loadSheetProduct).
        val merged = uiState.nutritionCache.toMutableMap()
        fetched.forEach { (id, nutrition) ->
            if (!merged.containsKey(id)) merged[id] = nutrition
        }
        uiState = uiState.copy(nutritionCache = merged)
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
        // Mark as not-yet-synced; the remote save below resets the flag once
        // the upsert returns. Local writes always win locally — even if the
        // network call fails the next launch loads from disk.
        uiState = uiState.copy(
            preferences = updatedPreferences,
            isRemoteSynced = false
        )
        viewModelScope.launch(Dispatchers.IO) {
            preferenceRepository.savePreferences(updatedPreferences)
        }
        // Fan out to Supabase if a userId is known. We capture it eagerly
        // because edits during sign-out are common during dev and we don't
        // want a stale id surfacing in the upsert. Errors land on
        // `remoteSyncError` for the screen to render.
        val userId = pendingRemoteUserId
        val repo = remotePreferenceRepository
        if (userId != null && repo != null) {
            viewModelScope.launch {
                repo.save(updatedPreferences, userId)
                    .onSuccess {
                        uiState = uiState.copy(
                            isRemoteSynced = true,
                            remoteSyncError = null
                        )
                    }
                    .onFailure { error ->
                        uiState = uiState.copy(
                            isRemoteSynced = false,
                            remoteSyncError = error.message
                                ?: "Could not sync preferences."
                        )
                    }
            }
        }
    }

    /**
     * Tracks the active Supabase user id so [updatePreferences] can fan out
     * to the remote repo without each toggle handler having to plumb it. Set
     * by [fetchRemotePreferences].
     */
    private var pendingRemoteUserId: String? = null

    /**
     * Reconcile local preferences with Supabase. Idempotent within a session:
     * subsequent calls no-op once [ShopperUiState.hasReconciledRemote] is
     * true so a slider drag isn't clobbered by an out-of-order remote
     * payload. The Preferences screen invokes this on first composition.
     *
     * On fetch failure we keep the local state untouched and surface the
     * error via [ShopperUiState.remoteSyncError].
     */
    fun fetchRemotePreferences(userId: String) {
        if (userId.isBlank()) return
        pendingRemoteUserId = userId
        if (uiState.hasReconciledRemote) return
        val repo = remotePreferenceRepository ?: run {
            // No remote repo (tests / disabled build) — mark reconciled so the
            // screen doesn't keep retrying.
            uiState = uiState.copy(hasReconciledRemote = true)
            return
        }
        viewModelScope.launch {
            repo.fetch(userId)
                .onSuccess { remote ->
                    if (remote != null) {
                        uiState = uiState.copy(
                            preferences = remote,
                            hasReconciledRemote = true,
                            remoteSyncError = null,
                            isRemoteSynced = true
                        )
                        // Persist the merged state to disk so the next cold
                        // launch starts in sync even when offline.
                        viewModelScope.launch(Dispatchers.IO) {
                            preferenceRepository.savePreferences(remote)
                        }
                    } else {
                        // First-time user: nothing on the server yet. Push
                        // the local defaults up so iOS can read them.
                        uiState = uiState.copy(
                            hasReconciledRemote = true,
                            remoteSyncError = null
                        )
                        repo.save(uiState.preferences, userId)
                            .onSuccess {
                                uiState = uiState.copy(isRemoteSynced = true)
                            }
                            .onFailure { error ->
                                uiState = uiState.copy(
                                    remoteSyncError = error.message
                                        ?: "Could not sync preferences."
                                )
                            }
                    }
                }
                .onFailure { error ->
                    // Keep local state, mark reconciled so we don't loop on
                    // repeated screen opens, surface the error.
                    uiState = uiState.copy(
                        hasReconciledRemote = true,
                        remoteSyncError = error.message
                            ?: "Could not load preferences."
                    )
                }
        }
    }

    /** Clear the transient sync banner once the user has acknowledged it. */
    fun clearRemoteSyncError() {
        if (uiState.remoteSyncError != null) {
            uiState = uiState.copy(remoteSyncError = null)
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
