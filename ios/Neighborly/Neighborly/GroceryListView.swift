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

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        lastLocation = locations.last
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // silently fall back to no location — tiebreaker just won't apply
    }
}

// MARK: - Grocery List View

struct GroceryListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \GroceryListItem.dateAdded, order: .reverse) private var items: [GroceryListItem]
    var routeState: RouteState
    @Binding var selectedTab: Int

    @State private var searchText = ""
    @State private var searchResults: [Product] = []
    @State private var isSearching = false
    @State private var searchError: String?
    @State private var selectedItem: GroceryListItem?
    @State private var selectedProduct: Product?
    @State private var searchDetailProduct: Product?
    @State private var networkMonitor = NetworkMonitor()
    @State private var locationHelper = LocationHelper()

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
                ProductDetailSheet(product: product) {
                    addOrIncrement(product)
                    searchDetailProduct = nil
                    searchText = ""
                    searchResults = []
                }
                .presentationDetents([.medium, .large])
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
                GroceryItemRow(item: item)
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
            Task { await optimizeRoute() }
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

    private func optimizeRoute() async {
        let productIds = items.compactMap { $0.productId }
        guard !productIds.isEmpty else {
            routeState.error = "No products to optimize"
            return
        }

        // request location for tiebreaking (non-blocking)
        locationHelper.requestIfNeeded()

        routeState.isOptimizing = true
        routeState.error = nil

        do {
            let loc = locationHelper.lastLocation
            let route = try await APIService.optimizeRoute(
                productIds: productIds,
                userLat: loc?.coordinate.latitude,
                userLng: loc?.coordinate.longitude
            )
            routeState.optimizedRoute = route
            routeState.isOptimizing = false
            selectedTab = 2
        } catch {
            routeState.error = "Couldn't optimize route"
            routeState.isOptimizing = false
        }
    }

    // MARK: - Actions

    private func performSearch() async {
        let query = searchText.trimmingCharacters(in: .whitespaces)

        guard query.count >= 2 else {
            searchResults = []
            searchError = nil
            isSearching = false
            return
        }

        isSearching = true
        searchError = nil

        try? await Task.sleep(for: .milliseconds(300))

        guard !Task.isCancelled else { return }

        do {
            let response = try await APIService.searchProducts(query: query)
            guard !Task.isCancelled else { return }
            searchResults = response.data
            searchError = nil
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            searchResults = []
            searchError = "Couldn't reach server"
        }

        isSearching = false
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
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 5)
                .padding(.top, 10)

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
    let onAdd: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 36, height: 5)
                .padding(.top, 10)

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

// MARK: - Preview

#Preview {
    GroceryListView(routeState: RouteState(), selectedTab: .constant(1))
        .modelContainer(for: GroceryListItem.self, inMemory: true)
}
