import SwiftUI
import SwiftData
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
    @AppStorage("wellnessEnabled")      private var savedWellnessEnabled: Bool = true
    @AppStorage("avoidPeanuts")         private var savedAvoidPeanuts: Bool = true
    @AppStorage("avoidDairy")           private var savedAvoidDairy: Bool = false
    @AppStorage("avoidShellfish")       private var savedAvoidShellfish: Bool = false
    @AppStorage("avoidWheat")           private var savedAvoidWheat: Bool = false
    @AppStorage("sodiumLimit")          private var savedSodiumLimit: String = ""
    @AppStorage("cholesterolLimit")     private var savedCholesterolLimit: String = ""
    @AppStorage("sugarLimit")           private var savedSugarLimit: String = ""
    @State private var searchText = ""
    @State private var searchResults: [Product] = []
    @State private var isSearching = false
    @State private var searchError: String?
    @State private var searchPage = 1
    @State private var searchTotalCount = 0
    @State private var isLoadingMore = false
    @State private var selectedItem: GroceryListItem?
    @State private var selectedProduct: Product?
    @State private var searchDetailProduct: Product?
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

                    if isSearching {
                        loadingView
                    } else if !searchResults.isEmpty {
                        searchResultsList
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
            .sheet(item: $selectedItem) { item in
                ItemDetailSheet(item: item, product: selectedProduct)
                    .presentationDetents([.medium])
            }
            .sheet(item: $searchDetailProduct) { product in
                ProductDetailSheet(product: product, prefs: currentPrefs) {
                    addOrIncrement(product)
                    searchDetailProduct = nil
                    searchText = ""
                    searchResults = []
                }
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
                ForEach(searchResults) { product in
                    Button {
                        searchDetailProduct = product
                    } label: {
                        HStack(spacing: 12) {
                            productThumbnail(product)
                                .frame(width: 40, height: 40)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(product.name)
                                    .font(.subheadline)
                                    .foregroundStyle(NeighborlyTheme.textPrimary)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                if let brand = product.brand {
                                    Text(brand)
                                        .font(.caption)
                                        .foregroundStyle(NeighborlyTheme.textSecondary)
                                }
                                Text(product.unitSize)
                                    .font(.caption)
                                    .foregroundStyle(NeighborlyTheme.textMuted)
                                if let nutrition = product.productNutrition {
                                    AllergenChips(nutrition: nutrition, prefs: currentPrefs)
                                }
                            }

                            Spacer()

                            HStack(spacing: 6) {
                                if let price = product.bestPrice {
                                    VStack(alignment: .trailing, spacing: 2) {
                                        Text(price, format: .currency(code: "USD"))
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundStyle(NeighborlyTheme.green)
                                        if let store = product.bestPriceStoreName {
                                            Text(store)
                                                .font(.caption2)
                                                .foregroundStyle(NeighborlyTheme.textMuted)
                                        }
                                    }
                                }

                                if product.storeProducts.count > 1 {
                                    Image(systemName: "chevron.right")
                                        .font(.caption2)
                                        .foregroundStyle(NeighborlyTheme.textMuted)
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                    }

                    if product.id != searchResults.last?.id {
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
        if let imageUrl = product.imageUrl, let url = URL(string: imageUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                default:
                    categoryEmoji(product.productCategories.emoji)
                }
            }
        } else {
            categoryEmoji(product.productCategories.emoji)
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
                    Task {
                        await fetchAndShowDetail(for: item)
                    }
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

        isSearching = true
        searchError = nil
        searchPage = 1

        try? await Task.sleep(for: .milliseconds(300))

        guard !Task.isCancelled else { return }

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
            searchError = "Couldn't reach server"
        }

        isSearching = false
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

    private func fetchAndShowDetail(for item: GroceryListItem) async {
        if let productId = item.productId {
            selectedProduct = try? await APIService.getProduct(id: productId)
        } else {
            selectedProduct = nil
        }
        selectedItem = item
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

// MARK: - Item Detail Sheet

struct ItemDetailSheet: View {
    let item: GroceryListItem
    let product: Product?

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 16) {
                // Item header
                HStack(alignment: .top, spacing: 14) {
                    if let imageUrl = product?.imageUrl, let url = URL(string: imageUrl) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image
                                    .resizable()
                                    .aspectRatio(contentMode: .fit)
                            case .failure:
                                Text(product?.productCategories.emoji ?? "🛒")
                                    .font(.largeTitle)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                                    .background(NeighborlyTheme.greenSoft)
                            default:
                                ProgressView()
                            }
                        }
                        .frame(width: 72, height: 72)
                        .background(NeighborlyTheme.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    } else {
                        Text(product?.productCategories.emoji ?? "🛒")
                            .font(.largeTitle)
                            .frame(width: 72, height: 72)
                            .background(NeighborlyTheme.greenSoft)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.name)
                            .font(.title3.weight(.bold))
                            .foregroundStyle(NeighborlyTheme.textPrimary)
                        Text(item.unitSize)
                            .font(.subheadline)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                        Text("Qty: \(item.quantity)")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(NeighborlyTheme.textSecondary)
                    }
                }

                Divider()

                if let product = product, !product.storeProducts.isEmpty {
                    // Real multi-store prices
                    Text("PRICES BY STORE")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .tracking(0.5)

                    VStack(spacing: 10) {
                        ForEach(product.storeProducts.sorted(by: {
                            ($0.salePrice ?? $0.price) < ($1.salePrice ?? $1.price)
                        }), id: \.storeId) { sp in
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
                    }
                } else {
                    // No product data so only show stored price
                    Text("STORED PRICE")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .tracking(0.5)

                    HStack {
                        Circle()
                            .fill(NeighborlyTheme.orange)
                            .frame(width: 8, height: 8)
                        Text("Last known price")
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(NeighborlyTheme.textPrimary)
                        Spacer()
                        Text(item.price, format: .currency(code: "USD"))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(NeighborlyTheme.orange)
                    }
                    .padding(.vertical, 10)
                    .padding(.horizontal, 14)
                    .background(NeighborlyTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    Text("Search for this item to see current store prices.")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .padding(20)

            Spacer()
        }
        .background(NeighborlyTheme.background)
    }
}

// MARK: - Product Detail Sheet (from search results)

struct ProductDetailSheet: View {
    let product: Product
    let prefs: Preferences
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Product header
                    HStack(alignment: .top, spacing: 14) {
                        if let imageUrl = product.imageUrl, let url = URL(string: imageUrl) {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                case .failure:
                                    Text(product.productCategories.emoji)
                                        .font(.largeTitle)
                                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                                        .background(NeighborlyTheme.greenSoft)
                                default:
                                    ProgressView()
                                }
                            }
                            .frame(width: 72, height: 72)
                            .background(NeighborlyTheme.cardBackground)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        } else {
                            Text(product.productCategories.emoji)
                                .font(.largeTitle)
                                .frame(width: 72, height: 72)
                                .background(NeighborlyTheme.greenSoft)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            Text(product.name)
                                .font(.title3.weight(.bold))
                                .foregroundStyle(NeighborlyTheme.textPrimary)
                            if let brand = product.brand {
                                Text(brand)
                                    .font(.subheadline)
                                    .foregroundStyle(NeighborlyTheme.textSecondary)
                            }
                            Text(product.unitSize)
                                .font(.subheadline)
                                .foregroundStyle(NeighborlyTheme.textMuted)
                        }
                    }

                    Divider()

                    if !product.storeProducts.isEmpty {
                        Text("PRICES BY STORE")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(NeighborlyTheme.textMuted)
                            .tracking(0.5)

                        VStack(spacing: 10) {
                            ForEach(product.storeProducts.sorted(by: {
                                ($0.salePrice ?? $0.price) < ($1.salePrice ?? $1.price)
                            }), id: \.storeId) { sp in
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
                        }
                    }

                    if let nutrition = product.productNutrition, prefs.wellnessEnabled {
                        WellnessPanel(nutrition: nutrition, prefs: prefs)
                    }

                    // Add to list button
                    Button(action: onAdd) {
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
                .padding(20)
            }
        }
        .background(NeighborlyTheme.background)
    }
}

// MARK: - Allergen Chips

private struct AllergenChips: View {
    let nutrition: ProductNutrition
    let prefs: Preferences

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
        if prefs.avoidPeanuts   && nutrition.containsPeanuts   == true { result.append("🥜 Peanuts") }
        if prefs.avoidDairy     && nutrition.containsDairy     == true { result.append("🥛 Dairy") }
        if prefs.avoidShellfish && nutrition.containsShellfish == true { result.append("🦐 Shellfish") }
        if prefs.avoidWheat     && nutrition.containsWheat     == true { result.append("🌾 Wheat") }
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
