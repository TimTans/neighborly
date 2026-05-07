import SwiftUI
import SwiftData
import Charts
import Network
import CoreLocation

// MARK: - Network Monitor

@Observable
final class NetworkMonitor {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "NetworkMonitor")
    private(set) var isConnected = true
    private(set) var didReconnect = false

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let wasConnected = self?.isConnected ?? true
            let nowConnected = path.status == .satisfied
            DispatchQueue.main.async {
                self?.isConnected = nowConnected
                if !wasConnected && nowConnected {
                    self?.didReconnect = true
                }
            }
        }
        monitor.start(queue: queue)
    }

    func acknowledgeReconnect() {
        didReconnect = false
    }

    deinit {
        monitor.cancel()
    }
}

// MARK: - Location Manager

final class LocationHelper: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private(set) var lastLocation: CLLocation?
    private var locationContinuation: CheckedContinuation<CLLocation?, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestIfNeeded() {
        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
        manager.requestLocation()
    }

    /// Request location and wait up to `timeout` seconds for a fix.
    /// Returns the cached location immediately if already available.
    func requestLocation(timeout: TimeInterval = 3) async -> CLLocation? {
        if let lastLocation { return lastLocation }

        if manager.authorizationStatus == .notDetermined {
            manager.requestWhenInUseAuthorization()
        }
        manager.requestLocation()

        return await withCheckedContinuation { continuation in
            locationContinuation = continuation

            // timeout: resolve with nil if location doesn't arrive in time
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(timeout))
                if let pending = locationContinuation {
                    locationContinuation = nil
                    pending.resume(returning: lastLocation)
                }
            }
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        lastLocation = locations.last
        if let continuation = locationContinuation {
            locationContinuation = nil
            continuation.resume(returning: lastLocation)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // silently fall back to no location — tiebreaker just won't apply
        if let continuation = locationContinuation {
            locationContinuation = nil
            continuation.resume(returning: nil)
        }
    }
}

// MARK: - Grocery List View

struct GroceryListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \GroceryListItem.dateAdded, order: .reverse) private var items: [GroceryListItem]
    var routeState: RouteState
    @Binding var selectedTab: Int

    @AppStorage("optimizationMode") private var savedPriority: String = Priority.lowestCost.rawValue
    @AppStorage("maxStops")       private var maxStops: Int    = 5
    @AppStorage("maxRadiusMiles") private var maxRadiusMiles: Double = 10
    @AppStorage("wellnessEnabled")      private var savedWellnessEnabled: Bool = false
    @AppStorage("avoidPeanuts")         private var savedAvoidPeanuts: Bool = false
    @AppStorage("avoidDairy")           private var savedAvoidDairy: Bool = false
    @AppStorage("avoidShellfish")       private var savedAvoidShellfish: Bool = false
    @AppStorage("avoidWheat")           private var savedAvoidWheat: Bool = false
    @AppStorage("sodiumLimit")          private var savedSodiumLimit: String = ""
    @AppStorage("cholesterolLimit")     private var savedCholesterolLimit: String = ""
    @AppStorage("sugarLimit")           private var savedSugarLimit: String = ""
    @State private var searchText = ""
    @State private var searchResults: [ProductSearchResult] = []
    @State private var isSearching = false
    @State private var searchError: String?
    @State private var searchPage = 1
    @State private var searchTotalCount = 0
    @State private var isLoadingMore = false
    @State private var detailSubject: DetailSubject?
    @State private var networkMonitor = NetworkMonitor()
    @State private var locationHelper = LocationHelper()
    @State private var nutritionCache: [String: ProductNutrition] = [:]
    @State private var nutritionLoaded = false
    @State private var wellnessWarnings: [(name: String, reasons: [String])] = []
    @State private var showWellnessWarning = false

    private var currentPrefs: Preferences {
        var p = Preferences()
        p.wellnessEnabled    = savedWellnessEnabled
        p.avoidPeanuts       = savedAvoidPeanuts
        p.avoidDairy         = savedAvoidDairy
        p.avoidShellfish     = savedAvoidShellfish
        p.avoidWheat         = savedAvoidWheat
        p.sodiumLimit        = savedSodiumLimit
        p.cholesterolLimit   = savedCholesterolLimit
        p.sugarLimit         = savedSugarLimit
        return p
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                NeighborlyTheme.background
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    searchBar
                        .zIndex(1)

                    if let error = searchError, !searchText.isEmpty {
                        errorBanner(error)
                    }

                    if !searchResults.isEmpty {
                        searchResultsList
                    } else if isSearching {
                        loadingView
                    } else if !searchText.isEmpty && searchText.count >= 2 {
                        noResultsView
                    } else if items.isEmpty {
                        emptyState
                    } else {
                        groceryList
                    }

                    // Create Route button (visible when list has items and search is inactive)
                    if !items.isEmpty && searchText.isEmpty {
                        createRouteButton
                    }
                }
            }
            .navigationTitle("Grocery List")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(item: $detailSubject) { subject in
                ProductDetailSheet(
                    subject: subject,
                    prefs: currentPrefs,
                    onAdd: { product in
                        addOrIncrement(product)
                        detailSubject = nil
                        searchText = ""
                        searchResults = []
                    },
                    onRemove: { item in
                        modelContext.delete(item)
                        detailSubject = nil
                    },
                    onIncrement: { item in item.quantity += 1 },
                    onDecrement: { item in
                        if item.quantity > 1 { item.quantity -= 1 }
                    }
                )
                .presentationDetents([.medium, .large])
            }
            .sheet(isPresented: $showWellnessWarning) {
                WellnessWarningSheet(warnings: wellnessWarnings) {
                    showWellnessWarning = false
                    Task { await optimizeRoute() }
                } onReview: {
                    showWellnessWarning = false
                }
                .presentationDetents([.medium])
            }
            .alert("No Route Found", isPresented: showNoRouteAlert) {
                Button("OK") { routeState.noRouteReason = nil }
            } message: {
                Text("No route found with your preferences (Max radius or max stops)")
            }
        }
        .task(id: searchText) {
            await performSearch()
        }
        .task {
            await refreshPrices()
        }
        .task(id: networkMonitor.didReconnect) {
            guard networkMonitor.didReconnect else { return }
            await refreshPrices()
            networkMonitor.acknowledgeReconnect()
        }
        .task(id: items.map { $0.productId ?? "" }.joined()) {
            await loadNutrition()
        }
    }

    // MARK: - Search Bar

    private var searchBar: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(NeighborlyTheme.textMuted)

            TextField("Search products to add...", text: $searchText)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()

            if isSearching {
                ProgressView()
                    .scaleEffect(0.8)
            }

            if !searchText.isEmpty {
                Button {
                    searchText = ""
                    searchResults = []
                    searchError = nil
                    searchPage = 1
                    searchTotalCount = 0
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }
            }
        }
        .padding(12)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.04), radius: 4, y: 2)
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 4)
    }

    // MARK: - Search Results

    private var searchResultsList: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(searchResults) { result in
                    Button {
                        detailSubject = DetailSubject(kind: .search(result))
                    } label: {
                        HStack(spacing: 12) {
                            searchResultThumbnail(result)
                                .frame(width: 40, height: 40)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(result.name)
                                    .font(.subheadline)
                                    .foregroundStyle(NeighborlyTheme.textPrimary)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                if let brand = result.brand {
                                    Text(brand)
                                        .font(.caption)
                                        .foregroundStyle(NeighborlyTheme.textSecondary)
                                }
                                if let size = result.unitSize, !size.isEmpty {
                                    Text(size)
                                        .font(.caption)
                                        .foregroundStyle(NeighborlyTheme.textMuted)
                                }
                                AllergenChips(
                                    containsDairy: result.containsDairy,
                                    containsPeanuts: result.containsPeanuts,
                                    containsShellfish: result.containsShellfish,
                                    containsWheat: result.containsWheat,
                                    prefs: currentPrefs
                                )
                            }

                            Spacer()

                            HStack(spacing: 6) {
                                if let price = result.bestPrice {
                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text(price, format: .currency(code: "USD"))
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(NeighborlyTheme.green)
                                        if let store = result.bestPriceStoreName {
                                            Text(store)
                                                .font(.caption2)
                                                .foregroundStyle(NeighborlyTheme.textMuted)
                                        }
                                    }
                                }

                                Image(systemName: "chevron.right")
                                    .font(.caption2)
                                    .foregroundStyle(NeighborlyTheme.textMuted)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                    }

                    if result.id != searchResults.last?.id {
                        Divider()
                            .padding(.leading, 52)
                    }
                }

                // load more when reaching the bottom
                if searchResults.count < searchTotalCount {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .onAppear {
                            Task { await loadMoreResults() }
                        }
                }
            }
            .background(NeighborlyTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 16)
            .padding(.top, 4)
        }
    }

    /// Shows the product image if available, otherwise a category emoji.
    @ViewBuilder
    private func productThumbnail(_ product: Product) -> some View {
        thumbnail(imageUrl: product.imageUrl, emoji: product.productCategories.emoji)
    }

    /// Slim variant for search rows.
    @ViewBuilder
    private func searchResultThumbnail(_ result: ProductSearchResult) -> some View {
        thumbnail(imageUrl: result.imageUrl, emoji: result.emoji)
    }

    @ViewBuilder
    private func thumbnail(imageUrl: String?, emoji: String) -> some View {
        if let imageUrl, let url = URL(string: imageUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                default:
                    categoryEmoji(emoji)
                }
            }
        } else {
            categoryEmoji(emoji)
        }
    }

    private func categoryEmoji(_ emoji: String) -> some View {
        Text(emoji)
            .font(.title2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(NeighborlyTheme.greenSoft)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    // MARK: - Loading

    private var loadingView: some View {
        VStack(spacing: 12) {
            Spacer()
            ProgressView()
            Text("Searching...")
                .font(.subheadline)
                .foregroundStyle(NeighborlyTheme.textMuted)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - No Results

    private var noResultsView: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "magnifyingglass")
                .font(.system(size: 36))
                .foregroundStyle(NeighborlyTheme.textMuted)
            Text("No products found")
                .font(.headline)
                .foregroundStyle(NeighborlyTheme.textPrimary)
            Text("Try a different search term")
                .font(.subheadline)
                .foregroundStyle(NeighborlyTheme.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Error Banner

    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "wifi.slash")
                .font(.caption)
            Text(message)
                .font(.caption)
        }
        .foregroundStyle(.red.opacity(0.8))
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "cart")
                .font(.system(size: 48))
                .foregroundStyle(NeighborlyTheme.textMuted)
            Text("Your grocery list is empty")
                .font(.headline)
                .foregroundStyle(NeighborlyTheme.textPrimary)
            Text("Search above to add items")
                .font(.subheadline)
                .foregroundStyle(NeighborlyTheme.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Grocery List

    private var groceryList: some View {
        List {
            ForEach(items) { item in
                ZStack(alignment: .topTrailing) {
                    GroceryItemRow(item: item)

                    if let pid = item.productId,
                       let nutrition = nutritionCache[pid],
                       !nutrition.violations(against: currentPrefs).isEmpty {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundStyle(.red)
                            .font(.system(size: 14))
                            .offset(x: 4, y: -4)
                    }
                }
                .listRowBackground(NeighborlyTheme.cardBackground)
                .contentShape(Rectangle())
                .onTapGesture {
                    detailSubject = DetailSubject(kind: .existingItem(item))
                }
            }
            .onDelete(perform: deleteItems)

            HStack {
                Text("\(items.count) item\(items.count == 1 ? "" : "s")")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(NeighborlyTheme.textSecondary)
                Spacer()
                Text("Tap item for details")
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.textMuted)
            }
            .padding(.vertical, 6)
            .listRowBackground(NeighborlyTheme.greenSoft)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    // MARK: - Create Route

    private var createRouteButton: some View {
        Button {
            let warnings = collectViolations()
            if warnings.isEmpty {
                Task { await optimizeRoute() }
            } else {
                wellnessWarnings = warnings
                showWellnessWarning = true
            }
        } label: {
            HStack(spacing: 8) {
                if routeState.isOptimizing {
                    ProgressView()
                        .tint(.white)
                } else {
                    Image(systemName: "paperplane.fill")
                }
                Text(routeState.isOptimizing ? "Optimizing..." : "Create Route")
                    .fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(routeState.isOptimizing ? NeighborlyTheme.green.opacity(0.6) : NeighborlyTheme.green)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(routeState.isOptimizing)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private var showNoRouteAlert: Binding<Bool> {
        Binding(
            get: { routeState.noRouteReason != nil },
            set: { if !$0 { routeState.noRouteReason = nil } }
        )
    }

    private func optimizeRoute() async {
        let productIds = items.compactMap { $0.productId }
        guard !productIds.isEmpty else {
            routeState.error = "No products to optimize"
            return
        }

        routeState.isOptimizing  = true
        routeState.error         = nil
        routeState.noRouteReason = nil

        do {
            let loc  = await locationHelper.requestLocation()
            let mode = Priority(rawValue: savedPriority)?.backendMode ?? "cost"
            let route = try await APIService.optimizeRoute(
                productIds:     productIds,
                userLat:        loc?.coordinate.latitude,
                userLng:        loc?.coordinate.longitude,
                mode:           mode,
                maxStops:       maxStops == 0 ? nil : maxStops,
                maxRadiusMiles: maxRadiusMiles == 0 ? nil : maxRadiusMiles
            )
            if route.noRoute == true {
                routeState.noRouteReason = route.noRouteReason ?? "constraints"
            } else {
                routeState.optimizedRoute = route
                selectedTab = 2
            }
        } catch {
            routeState.error = "Couldn't optimize route"
        }
        routeState.isOptimizing = false
    }

    // MARK: - Actions

    private func performSearch() async {
        let query = searchText.trimmingCharacters(in: .whitespaces)

        guard query.count >= 2 else {
            searchResults = []
            searchError = nil
            isSearching = false
            searchPage = 1
            searchTotalCount = 0
            return
        }

        try? await Task.sleep(for: .milliseconds(300))

        guard !Task.isCancelled else { return }

        isSearching = true
        searchError = nil
        searchPage = 1

        do {
            let response = try await APIService.searchProducts(query: query)
            guard !Task.isCancelled else { return }
            searchResults = response.data
            searchTotalCount = response.count
            searchError = nil
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            searchResults = []
            print("search error: \(error)")
            searchError = Self.userFacingMessage(for: error)
        }

        isSearching = false
    }

    private static func userFacingMessage(for error: Error) -> String {
        if error is DecodingError {
            return "Couldn't read response"
        }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .notConnectedToInternet, .networkConnectionLost:
                return "No internet connection"
            case .timedOut:
                return "Request timed out"
            default:
                return "Couldn't reach server"
            }
        }
        if let apiError = error as? APIError {
            return apiError.errorDescription ?? "Something went wrong"
        }
        return "Something went wrong"
    }

    private func loadMoreResults() async {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !isLoadingMore, searchResults.count < searchTotalCount else { return }

        isLoadingMore = true
        let nextPage = searchPage + 1

        do {
            let response = try await APIService.searchProducts(query: query, page: nextPage)
            searchResults.append(contentsOf: response.data)
            searchPage = nextPage
        } catch {
            // silently fail on pagination, user still has existing results
        }

        isLoadingMore = false
    }

    private func addOrIncrement(_ product: Product) {
        if let existing = items.first(where: { $0.upc == product.upc }) {
            existing.quantity += 1
        } else {
            let newItem = GroceryListItem(from: product)
            modelContext.insert(newItem)
        }
    }

    private func deleteItems(at offsets: IndexSet) {
        for index in offsets {
            modelContext.delete(items[index])
        }
    }


    private func refreshPrices() async {
        let itemsToRefresh = items.filter { $0.productId != nil }
        guard !itemsToRefresh.isEmpty else { return }

        await withTaskGroup(of: Void.self) { group in
            for item in itemsToRefresh {
                guard let productId = item.productId else { continue }
                let currentPrice = item.price
                group.addTask {
                    do {
                        let product = try await APIService.getProduct(id: productId)
                        if let newPrice = product.bestPrice, newPrice != currentPrice {
                            await MainActor.run {
                                item.price = newPrice
                            }
                        }
                    } catch {
                        // Just skip
                    }
                }
            }
        }
    }

    private func loadNutrition() async {
        let productIds = items.compactMap { $0.productId }
        guard !productIds.isEmpty else { return }

        var cache: [String: ProductNutrition] = [:]
        for id in productIds {
            guard let product = try? await APIService.getProduct(id: id) else { continue }
            if let nutrition = product.productNutrition {
                cache[id] = nutrition
            }
        }
        nutritionCache = cache
        nutritionLoaded = true
    }

    private func collectViolations() -> [(name: String, reasons: [String])] {
        items.compactMap { item -> (name: String, reasons: [String])? in
            guard let pid = item.productId,
                  let nutrition = nutritionCache[pid] else { return nil }
            let vs = nutrition.violations(against: currentPrefs)
            guard !vs.isEmpty else { return nil }
            let reasons = vs.map { violation -> String in
                switch violation {
                case .allergen(let name): return "contains \(name)"
                case .nutrientExceeded(let name, let actual, let limit):
                    return "\(name) \(String(format: "%.0f", actual)) (limit \(String(format: "%.0f", limit)))"
                }
            }
            return (name: item.name, reasons: reasons)
        }
    }
}

// MARK: - Grocery Item Row

struct GroceryItemRow: View {
    @Environment(\.modelContext) private var modelContext
    @Bindable var item: GroceryListItem

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.name)
                    .font(.subheadline)
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                    .lineLimit(2)
                Text(item.unitSize)
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.textMuted)
            }

            Spacer()

            HStack(spacing: 8) {
                Button {
                    if item.quantity > 1 {
                        item.quantity -= 1
                    } else {
                        withAnimation {
                            modelContext.delete(item)
                        }
                    }
                } label: {
                    Image(systemName: item.quantity > 1 ? "minus.circle" : "trash.circle")
                        .font(.title3)
                        .foregroundStyle(item.quantity > 1 ? NeighborlyTheme.orange : .red.opacity(0.7))
                }
                .buttonStyle(.plain)

                Text("\(item.quantity)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                    .frame(minWidth: 20, alignment: .center)

                Button {
                    item.quantity += 1
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3)
                        .foregroundStyle(NeighborlyTheme.orange)
                }
                .buttonStyle(.plain)
            }

            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(NeighborlyTheme.textMuted)
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Detail Subject

/// what the unified ProductDetailSheet is showing.
/// search flow: a slim search row; the sheet fetches the full product.
/// existing item flow: a SwiftData GroceryListItem; the sheet fetches by productId.
struct DetailSubject: Identifiable {
    let id = UUID()
    let kind: Kind

    enum Kind {
        case search(ProductSearchResult)
        case existingItem(GroceryListItem)
    }

    var productId: String? {
        switch kind {
        case .search(let r):       return r.id
        case .existingItem(let i): return i.productId
        }
    }

    var displayName: String {
        switch kind {
        case .search(let r):       return r.name
        case .existingItem(let i): return i.name
        }
    }

    var displayBrand: String? {
        switch kind {
        case .search(let r):       return r.brand
        case .existingItem:        return nil  // GroceryListItem doesn't carry brand
        }
    }

    var displayUnitSize: String {
        switch kind {
        case .search(let r):       return r.displayUnitSize
        case .existingItem(let i): return i.unitSize
        }
    }

    var fallbackEmoji: String {
        switch kind {
        case .search(let r):       return r.emoji
        case .existingItem:        return "🛒"
        }
    }

    var fallbackImageUrl: String? {
        switch kind {
        case .search(let r):       return r.imageUrl
        case .existingItem:        return nil
        }
    }

    /// stored snapshot price, used as last-resort fallback for existing items.
    var storedPrice: Double? {
        switch kind {
        case .search:              return nil
        case .existingItem(let i): return i.price > 0 ? i.price : nil
        }
    }
}

// MARK: - Product Detail Sheet (unified)

struct ProductDetailSheet: View {
    let subject: DetailSubject
    let prefs: Preferences
    var onAdd: ((Product) -> Void)? = nil
    var onRemove: ((GroceryListItem) -> Void)? = nil
    var onIncrement: ((GroceryListItem) -> Void)? = nil
    var onDecrement: ((GroceryListItem) -> Void)? = nil

    @State private var product: Product?
    @State private var loadState: LoadState = .loading
    @State private var historyDays: Int = 90

    enum LoadState { case loading, loaded, failed }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header

                Divider()

                priceSection

                if let nutrition = product?.productNutrition, prefs.wellnessEnabled {
                    WellnessPanel(nutrition: nutrition, prefs: prefs)
                }

                if let id = effectiveProductId {
                    PriceHistorySection(productId: id, days: $historyDays)
                }

                actionSection
            }
            .padding(20)
        }
        .background(NeighborlyTheme.background)
        .task(id: subject.id) { await loadProduct() }
    }

    // MARK: header

    @ViewBuilder
    private var header: some View {
        HStack(alignment: .top, spacing: 14) {
            thumbnail
                .frame(width: 72, height: 72)
                .background(NeighborlyTheme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 4) {
                Text(subject.displayName)
                    .font(.title3.weight(.bold))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                if let brand = product?.brand ?? subject.displayBrand {
                    Text(brand)
                        .font(.subheadline)
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                }
                if !subject.displayUnitSize.isEmpty {
                    Text(subject.displayUnitSize)
                        .font(.subheadline)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }
                if case .existingItem(let item) = subject.kind {
                    Text("Qty: \(item.quantity)")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                }
            }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        let urlString = product?.imageUrl ?? subject.fallbackImageUrl
        let emoji = product?.productCategories.emoji ?? subject.fallbackEmoji

        if let urlString, let url = URL(string: urlString) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: .fit)
                case .failure:
                    Text(emoji)
                        .font(.largeTitle)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(NeighborlyTheme.greenSoft)
                default:
                    ProgressView()
                }
            }
        } else {
            Text(emoji)
                .font(.largeTitle)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(NeighborlyTheme.greenSoft)
        }
    }

    // MARK: prices

    @ViewBuilder
    private var priceSection: some View {
        switch loadState {
        case .loading:
            HStack {
                Spacer()
                ProgressView()
                    .padding(.vertical, 24)
                Spacer()
            }
        case .loaded:
            if let product, !product.storeProducts.isEmpty {
                pricesByStoreList(product)
            } else {
                noLivePricesFallback(reason: subject.productId == nil
                    ? "No live price data for this item."
                    : "No store prices available right now.")
            }
        case .failed:
            fetchFailedFallback
        }
    }

    @ViewBuilder
    private func pricesByStoreList(_ product: Product) -> some View {
        Text("PRICES BY STORE")
            .font(.caption.weight(.semibold))
            .foregroundStyle(NeighborlyTheme.textMuted)
            .tracking(0.5)

        VStack(spacing: 10) {
            ForEach(product.storeProducts.sorted(by: {
                ($0.salePrice ?? $0.price) < ($1.salePrice ?? $1.price)
            }), id: \.storeId) { sp in
                storeRow(sp)
            }
        }
    }

    @ViewBuilder
    private func storeRow(_ sp: StoreProduct) -> some View {
        HStack {
            Circle()
                .fill(NeighborlyTheme.green)
                .frame(width: 8, height: 8)

            Text(sp.stores.name)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(NeighborlyTheme.textPrimary)

            if !sp.inStock {
                Text("out of stock")
                    .font(.caption2)
                    .foregroundStyle(.red.opacity(0.7))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(NeighborlyTheme.background)
                    .clipShape(Capsule())
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 1) {
                if let sale = sp.salePrice {
                    Text(sale, format: .currency(code: "USD"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.green)
                    Text(sp.price, format: .currency(code: "USD"))
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .strikethrough()
                } else {
                    Text(sp.price, format: .currency(code: "USD"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.green)
                }
            }
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func noLivePricesFallback(reason: String) -> some View {
        Text("PRICES")
            .font(.caption.weight(.semibold))
            .foregroundStyle(NeighborlyTheme.textMuted)
            .tracking(0.5)

        if let stored = subject.storedPrice {
            HStack {
                Circle()
                    .fill(NeighborlyTheme.orange)
                    .frame(width: 8, height: 8)
                Text("Last known price")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                Spacer()
                Text(stored, format: .currency(code: "USD"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.orange)
            }
            .padding(.vertical, 10)
            .padding(.horizontal, 14)
            .background(NeighborlyTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }

        Text(reason)
            .font(.caption)
            .foregroundStyle(NeighborlyTheme.textMuted)
            .frame(maxWidth: .infinity, alignment: .center)
    }

    @ViewBuilder
    private var fetchFailedFallback: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "wifi.exclamationmark")
                    .foregroundStyle(NeighborlyTheme.orange)
                Text("Couldn't reach server — showing last known price")
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if let stored = subject.storedPrice {
                HStack {
                    Circle()
                        .fill(NeighborlyTheme.orange)
                        .frame(width: 8, height: 8)
                    Text("Last known price")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                    Spacer()
                    Text(stored, format: .currency(code: "USD"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.orange)
                }
                .padding(.vertical, 10)
                .padding(.horizontal, 14)
                .background(NeighborlyTheme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Button {
                Task { await loadProduct() }
            } label: {
                Label("Retry", systemImage: "arrow.clockwise")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(NeighborlyTheme.greenSoft)
                    .foregroundStyle(NeighborlyTheme.green)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
    }

    // MARK: actions

    @ViewBuilder
    private var actionSection: some View {
        switch subject.kind {
        case .search:
            if let product, let onAdd {
                Button { onAdd(product) } label: {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text("Add to Grocery List")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(NeighborlyTheme.green)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .padding(.top, 4)
            }
        case .existingItem(let item):
            VStack(spacing: 10) {
                HStack(spacing: 12) {
                    Button { onDecrement?(item) } label: {
                        Image(systemName: "minus.circle.fill")
                            .font(.title2)
                            .foregroundStyle(item.quantity > 1
                                ? NeighborlyTheme.green
                                : NeighborlyTheme.textMuted)
                    }
                    .disabled(item.quantity <= 1)

                    Text("\(item.quantity)")
                        .font(.title3.weight(.semibold))
                        .frame(minWidth: 32)

                    Button { onIncrement?(item) } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                            .foregroundStyle(NeighborlyTheme.green)
                    }

                    Spacer()
                }

                if let onRemove {
                    Button(role: .destructive) {
                        onRemove(item)
                    } label: {
                        Label("Remove from List", systemImage: "trash")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.red.opacity(0.08))
                            .foregroundStyle(.red)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
            .padding(.top, 4)
        }
    }

    // MARK: loading

    private var effectiveProductId: String? {
        product?.id ?? subject.productId
    }

    private func loadProduct() async {
        guard let id = subject.productId else {
            loadState = .loaded
            return
        }
        loadState = .loading
        do {
            product = try await APIService.getProduct(id: id)
            loadState = .loaded
        } catch {
            loadState = .failed
        }
    }
}

// MARK: - Price History Section

struct PriceHistorySection: View {
    let productId: String
    @Binding var days: Int

    @State private var series: [PriceHistorySeries] = []
    @State private var loadState: LoadState = .loading

    enum LoadState { case loading, loaded, failed }

    private static let windowOptions: [(label: String, days: Int)] = [
        ("30D", 30), ("90D", 90), ("1Y", 365),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("PRICE HISTORY")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.textMuted)
                    .tracking(0.5)
                Spacer()
                Picker("Window", selection: $days) {
                    ForEach(Self.windowOptions, id: \.days) { opt in
                        Text(opt.label).tag(opt.days)
                    }
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 180)
            }

            content
        }
        .padding(.top, 4)
        .task(id: productId) { await load() }
        .task(id: days) { await load() }
    }

    @ViewBuilder
    private var content: some View {
        switch loadState {
        case .loading:
            HStack {
                Spacer()
                ProgressView().padding(.vertical, 18)
                Spacer()
            }
        case .failed:
            HStack(spacing: 8) {
                Image(systemName: "wifi.exclamationmark")
                    .foregroundStyle(NeighborlyTheme.textMuted)
                Text("Couldn't load price history")
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.textMuted)
                Spacer()
                Button("Retry") { Task { await load() } }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.green)
            }
            .padding(12)
            .background(NeighborlyTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        case .loaded:
            if plottable.isEmpty {
                Text("No price history yet for this window.")
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.textMuted)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 14)
                    .background(NeighborlyTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                chart
                legend
            }
        }
    }

    /// flatten series → (date, price, store) datums, dropping points whose
    /// timestamp couldn't parse or whose effective price is nil.
    private var plottable: [Datum] {
        series.flatMap { s in
            s.points.compactMap { p -> Datum? in
                guard let date = p.recordedDate, let price = p.effectivePrice else { return nil }
                return Datum(date: date, price: price, store: s.displayName)
            }
        }
    }

    private struct Datum: Identifiable {
        let id = UUID()
        let date: Date
        let price: Double
        let store: String
    }

    private var chart: some View {
        Chart(plottable) { d in
            LineMark(
                x: .value("Date", d.date),
                y: .value("Price", d.price)
            )
            .foregroundStyle(by: .value("Store", d.store))
            .interpolationMethod(.monotone)
            .symbol(by: .value("Store", d.store))
        }
        .chartYAxis {
            AxisMarks(format: .currency(code: "USD"))
        }
        .chartLegend(.hidden)
        .frame(height: 180)
        .padding(.vertical, 8)
        .padding(.horizontal, 4)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var legend: some View {
        let stores = Array(Set(plottable.map { $0.store })).sorted()
        return HStack(spacing: 12) {
            ForEach(stores, id: \.self) { name in
                HStack(spacing: 4) {
                    Circle()
                        .fill(NeighborlyTheme.green)
                        .frame(width: 7, height: 7)
                    Text(name)
                        .font(.caption2)
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                }
            }
            Spacer()
        }
    }

    private func load() async {
        loadState = .loading
        do {
            series = try await APIService.getPriceHistory(productId: productId, days: days)
            loadState = .loaded
        } catch {
            loadState = .failed
        }
    }
}

// MARK: - Allergen Chips

private struct AllergenChips: View {
    let containsDairy: Bool?
    let containsPeanuts: Bool?
    let containsShellfish: Bool?
    let containsWheat: Bool?
    let prefs: Preferences

    init(
        containsDairy: Bool?,
        containsPeanuts: Bool?,
        containsShellfish: Bool?,
        containsWheat: Bool?,
        prefs: Preferences
    ) {
        self.containsDairy = containsDairy
        self.containsPeanuts = containsPeanuts
        self.containsShellfish = containsShellfish
        self.containsWheat = containsWheat
        self.prefs = prefs
    }

    init(nutrition: ProductNutrition, prefs: Preferences) {
        self.init(
            containsDairy: nutrition.containsDairy,
            containsPeanuts: nutrition.containsPeanuts,
            containsShellfish: nutrition.containsShellfish,
            containsWheat: nutrition.containsWheat,
            prefs: prefs
        )
    }

    var body: some View {
        let chips = chips()
        if !chips.isEmpty {
            HStack(spacing: 4) {
                ForEach(chips, id: \.self) { chip in
                    Text(chip)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(NeighborlyTheme.orange)
                        .clipShape(Capsule())
                }
            }
        }
    }

    private func chips() -> [String] {
        var result: [String] = []
        if prefs.avoidPeanuts   && containsPeanuts   == true { result.append("🥜 Peanuts") }
        if prefs.avoidDairy     && containsDairy     == true { result.append("🥛 Dairy") }
        if prefs.avoidShellfish && containsShellfish == true { result.append("🦐 Shellfish") }
        if prefs.avoidWheat     && containsWheat     == true { result.append("🌾 Wheat") }
        return result
    }
}

// MARK: - Wellness Panel

private struct WellnessPanel: View {
    let nutrition: ProductNutrition
    let prefs: Preferences

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("NUTRITION (PER SERVING)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(NeighborlyTheme.textMuted)
                .tracking(0.5)

            nutrientRow(label: "Sodium",      value: nutrition.sodiumMg,      unit: "mg", limitStr: prefs.sodiumLimit)
            nutrientRow(label: "Cholesterol", value: nutrition.cholesterolMg, unit: "mg", limitStr: prefs.cholesterolLimit)
            nutrientRow(label: "Sugar",       value: nutrition.sugarG,        unit: "g",  limitStr: prefs.sugarLimit)

            if let cal = nutrition.caloriesKcal {
                Divider().opacity(0.15)
                HStack(spacing: 8) {
                    Label("\(Int(cal)) kcal", systemImage: "flame")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                    if let protein = nutrition.proteinG {
                        Text("·  \(String(format: "%.1f", protein))g protein")
                            .font(.caption)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                    }
                    if let fat = nutrition.fatG {
                        Text("·  \(String(format: "%.1f", fat))g fat")
                            .font(.caption)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                    }
                }
            }

            AllergenChips(nutrition: nutrition, prefs: prefs)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.gray.opacity(0.06))
        )
    }

    @ViewBuilder
    private func nutrientRow(label: String, value: Double?, unit: String, limitStr: String) -> some View {
        let limit = parseLimit(limitStr)
        let fraction: Double? = (value != nil && limit != nil) ? min((value! / limit!), 1.0) : nil

        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label).font(.caption).foregroundStyle(NeighborlyTheme.textSecondary)
                Spacer()
                if let v = value {
                    Text("\(String(format: "%.0f", v)) \(unit)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(fraction.map { barColor($0) } ?? NeighborlyTheme.textMuted)
                } else {
                    Text("—").font(.caption).foregroundStyle(NeighborlyTheme.textMuted)
                }
                if let limit {
                    Text("/ \(String(format: "%.0f", limit)) \(unit) limit")
                        .font(.caption2).foregroundStyle(NeighborlyTheme.textMuted)
                }
            }
            if let f = fraction {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 3).fill(Color.gray.opacity(0.12))
                        RoundedRectangle(cornerRadius: 3)
                            .fill(barColor(f))
                            .frame(width: geo.size.width * f)
                    }
                }
                .frame(height: 4)
            }
        }
    }

    private func barColor(_ fraction: Double) -> Color {
        if fraction < 0.5 { return NeighborlyTheme.green }
        if fraction < 0.8 { return NeighborlyTheme.orange }
        return .red
    }

    private func parseLimit(_ s: String) -> Double? {
        guard !s.isEmpty else { return nil }
        let token = s.components(separatedBy: CharacterSet(charactersIn: " /\t")).first ?? ""
        return Double(token)
    }
}

// MARK: - Wellness Warning Sheet

private struct WellnessWarningSheet: View {
    let warnings: [(name: String, reasons: [String])]
    let onContinue: () -> Void
    let onReview: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(NeighborlyTheme.orange)
                        .font(.title2)
                    Text("Dietary Warning")
                        .font(.headline)
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                }

                Text("These items may not match your preferences:")
                    .font(.subheadline)
                    .foregroundStyle(NeighborlyTheme.textSecondary)

                VStack(alignment: .leading, spacing: 8) {
                    ForEach(warnings, id: \.name) { warning in
                        VStack(alignment: .leading, spacing: 2) {
                            Text("· \(warning.name)")
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(NeighborlyTheme.textPrimary)
                            Text(warning.reasons.joined(separator: ", "))
                                .font(.caption)
                                .foregroundStyle(NeighborlyTheme.orange)
                        }
                    }
                }
                .padding(12)
                .background(NeighborlyTheme.orangeSoft)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(20)

            Spacer()

            VStack(spacing: 10) {
                Button(action: onContinue) {
                    Text("Continue Anyway")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(NeighborlyTheme.green)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                }

                Button(action: onReview) {
                    Text("Review List")
                        .font(.subheadline)
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(NeighborlyTheme.background)
    }
}

// MARK: - Preview

#Preview {
    GroceryListView(routeState: RouteState(), selectedTab: .constant(1))
        .modelContainer(for: GroceryListItem.self, inMemory: true)
}
