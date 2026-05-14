import SwiftUI
import SwiftData

struct RouteView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var groceryItems: [GroceryListItem]
    var routeState: RouteState

    @AppStorage("optimizationMode") private var savedPriority: String = Priority.lowestCost.rawValue
    @AppStorage("maxStops")       private var maxStops: Int    = 5
    @AppStorage("maxRadiusMiles") private var maxRadiusMiles: Double = 10

    @State private var swapItem: RouteItem?
    @State private var alternatives: [Product] = []
    @State private var isLoadingAlternatives = false
    @State private var focusedStopIndex: Int?
    @State private var directionsRoute: MapboxDirectionsService.DirectionsRoute?
    @State private var directionsMode: TransportMode = .walking
    @State private var checkedItemIds: Set<String> = []
    @State private var reportingItem: PriceReportingTarget?
    @State private var priceReportSummary: [PriceReportPairKey: PriceReportSummary] = [:]
    @State private var reportPopoverKey: PriceReportPairKey?

    var body: some View {
        NavigationStack {
            ZStack {
                NeighborlyTheme.background.ignoresSafeArea()

                if let route = routeState.optimizedRoute {
                    routeContent(route)
                } else {
                    emptyState
                }
            }
            .navigationTitle("Route")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(item: $swapItem) { item in
                SwapSheet(
                    item: item,
                    alternatives: alternatives,
                    isLoading: isLoadingAlternatives
                ) { replacement in
                    performSwap(original: item, replacement: replacement)
                }
                .presentationDetents([.medium, .large])
            }
            .sheet(item: $reportingItem) { target in
                PriceReportSheet(
                    item: target.item,
                    storeProductId: target.item.storeProductId,
                    storeName: target.storeName,
                    onSubmitted: { submittedPrice in
                        Task {
                            await refreshSummary(
                                for: PriceReportPair(
                                    productId: target.item.productId,
                                    storeId:   target.storeId
                                ),
                                optimisticPrice: submittedPrice
                            )
                        }
                    }
                )
                .presentationDetents([.medium, .large])
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if let route = routeState.optimizedRoute {
                    tripBanner(route)
                }
            }
            .onChange(of: routeState.optimizedRoute) { _, _ in
                // a fresh optimization is a fresh trip
                checkedItemIds = []
                priceReportSummary = [:]
            }
            .task(id: routeState.optimizedRoute) {
                if let route = routeState.optimizedRoute {
                    await loadSummary(for: route)
                }
            }
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "paperplane")
                .font(.system(size: 48))
                .foregroundStyle(NeighborlyTheme.textMuted)
            Text("No route yet")
                .font(.headline)
                .foregroundStyle(NeighborlyTheme.textPrimary)
            Text("Add items to your grocery list,\nthen tap Create Route")
                .font(.subheadline)
                .foregroundStyle(NeighborlyTheme.textSecondary)
                .multilineTextAlignment(.center)
        }
    }

    // MARK: - Route Content

    private func routeContent(_ route: OptimizedRoute) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                // Map
                MapboxRouteMap(
                    stops: route.stops,
                    onStopSelected: { stopIndex in
                        withAnimation(.easeInOut(duration: 0.25)) {
                            focusedStopIndex = stopIndex
                        }
                    },
                    onRouteChanged: { newRoute, mode in
                        directionsRoute = newRoute
                        directionsMode = mode
                    }
                )
                .frame(height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 20))

                // Travel summary (total ETA + distance)
                if let directions = directionsRoute {
                    travelSummary(route: directions, mode: directionsMode)
                }

                // Summary card
                summaryCard(route)

                // Collapsed indicator when stops are hidden
                if let focused = focusedStopIndex, focused > 0 {
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            focusedStopIndex = nil
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "chevron.up.2")
                                .font(.caption2.weight(.bold))
                            Text("Show all \(route.stops.count) stops")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundStyle(NeighborlyTheme.green)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity)
                        .background(NeighborlyTheme.greenSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }

                // Stops with directions blocks between them
                interleavedStopsAndDirections(route: route)

                // Items not found
                if !route.itemsNotFound.isEmpty {
                    notFoundCard(count: route.itemsNotFound.count)
                }
            }
            .padding(16)
        }
    }

    // MARK: - Summary Card

    private func summaryCard(_ route: OptimizedRoute) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 6) {
                Image(systemName: routeLabelIcon)
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.green)
                Text(routeLabelText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.green)
                    .tracking(0.5)
            }
            .frame(maxWidth: .infinity)

            HStack(spacing: 20) {
                VStack(spacing: 2) {
                    Text(route.totalCost, format: .currency(code: "USD"))
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(NeighborlyTheme.green)
                    Text("total")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }

                Divider().frame(height: 36)

                VStack(spacing: 2) {
                    let totalItems = route.stops.reduce(0) { $0 + $1.items.count }
                    Text("\(totalItems)")
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                    Text("items")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }

                Divider().frame(height: 36)

                VStack(spacing: 2) {
                    Text("\(route.stops.count)")
                        .font(.title2.weight(.heavy))
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                    Text(route.stops.count == 1 ? "store" : "stores")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }

                if let dist = route.totalDistance, dist > 0 {
                    Divider().frame(height: 36)

                    VStack(spacing: 2) {
                        Text(String(format: "%.1f", dist))
                            .font(.title2.weight(.heavy))
                            .foregroundStyle(NeighborlyTheme.textPrimary)
                        Text("miles")
                            .font(.caption)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                    }
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(20)
        .background(NeighborlyTheme.greenSoft)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    // MARK: - Stop Card

    private func stopCard(_ stop: RouteStop, index: Int) -> some View {
        let collected = stop.items.filter { checkedItemIds.contains($0.id) }.count
        let total = stop.items.count
        let stopComplete = collected == total && total > 0

        return VStack(alignment: .leading, spacing: 12) {
            // Store header
            HStack(spacing: 10) {
                ZStack {
                    Circle()
                        .fill(stopComplete ? NeighborlyTheme.green : NeighborlyTheme.green)
                        .frame(width: 28, height: 28)
                    if stopComplete {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                    } else {
                        Text("\(index)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(stop.store.name)
                        .font(.subheadline.weight(.bold))
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                    if let address = stop.store.address {
                        Text(address)
                            .font(.caption)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(stop.subtotal, format: .currency(code: "USD"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.green)
                    if stopComplete {
                        Text("DONE")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(NeighborlyTheme.green))
                    } else {
                        Text("\(collected)/\(total) collected")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(NeighborlyTheme.textMuted)
                    }
                }
            }

            Divider()

            // Item rows: leading checkbox toggles, rest of row taps to swap
            ForEach(stop.items) { item in
                stopItemRow(item, in: stop)
            }
        }
        .padding(16)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func stopItemRow(_ item: RouteItem, in stop: RouteStop) -> some View {
        let checked = checkedItemIds.contains(item.id)

        HStack(spacing: 10) {
            Button {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.78)) {
                    if checked {
                        checkedItemIds.remove(item.id)
                    } else {
                        checkedItemIds.insert(item.id)
                    }
                }
            } label: {
                Image(systemName: checked ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(checked
                        ? NeighborlyTheme.green
                        : NeighborlyTheme.textMuted.opacity(0.6))
                    .contentTransition(.symbolEffect(.replace))
            }
            .buttonStyle(.plain)

            HStack(spacing: 10) {
                routeItemThumbnail(item)
                    .frame(width: 32, height: 32)
                    .background(NeighborlyTheme.greenSoft)
                    .clipShape(RoundedRectangle(cornerRadius: 6))

                VStack(alignment: .leading, spacing: 1) {
                    Text(item.name)
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                        .lineLimit(1)
                        .strikethrough(checked, color: NeighborlyTheme.textMuted)
                    Text(item.unitSize)
                        .font(.caption2)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }

                Spacer()

                if let sale = item.salePrice {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text(sale, format: .currency(code: "USD"))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(NeighborlyTheme.green)
                        Text(item.price, format: .currency(code: "USD"))
                            .font(.caption2)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                            .strikethrough()
                    }
                } else {
                    Text(item.price, format: .currency(code: "USD"))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.green)
                }

                if let storeId = stop.store.id,
                   let summary = priceReportSummary[PriceReportPairKey(
                       productId: item.productId,
                       storeId:   storeId
                   )],
                   summary.count > 0 {
                    reportBadge(summary: summary)
                }

                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.caption2)
                    .foregroundStyle(NeighborlyTheme.textMuted)
            }
            .opacity(checked ? 0.55 : 1)
            .contentShape(Rectangle())
            .onTapGesture {
                Task { await loadAlternatives(for: item) }
            }
        }
        .contextMenu {
            Button {
                openReport(for: item, in: stop)
            } label: {
                Label("Report inaccurate price", systemImage: "exclamationmark.bubble")
            }
        }
    }

    // MARK: - Not Found Card

    private func notFoundCard(count: Int) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle")
                .foregroundStyle(NeighborlyTheme.orange)
            Text("\(count) item\(count == 1 ? "" : "s") not available at any store")
                .font(.caption)
                .foregroundStyle(NeighborlyTheme.textSecondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(NeighborlyTheme.orangeSoft)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Swap Logic

    private func loadAlternatives(for item: RouteItem) async {
        isLoadingAlternatives = true
        alternatives = []
        swapItem = item

        do {
            alternatives = try await APIService.getAlternatives(productId: item.productId)
        } catch {
            alternatives = []
        }
        isLoadingAlternatives = false
    }

    private func performSwap(original: RouteItem, replacement: Product) {
        // Update grocery list: swap old product for new
        if let groceryItem = groceryItems.first(where: { $0.productId == original.productId }) {
            groceryItem.name = replacement.name
            groceryItem.price = replacement.bestPrice ?? 0
            groceryItem.unitSize = replacement.unitSize
            groceryItem.upc = replacement.upc ?? ""
            groceryItem.productId = replacement.id
        }

        // Re-optimize with updated product IDs
        let productIds = groceryItems.compactMap { $0.productId }
        swapItem = nil

        Task {
            routeState.isOptimizing = true
            do {
                let mode = Priority(rawValue: savedPriority)?.backendMode ?? "cost"
                let route = try await APIService.optimizeRoute(
                    productIds:     productIds,
                    mode:           mode,
                    maxStops:       maxStops == 0 ? nil : maxStops,
                    maxRadiusMiles: maxRadiusMiles == 0 ? nil : maxRadiusMiles
                )
                routeState.optimizedRoute = route
            } catch {
                routeState.error = "Couldn't re-optimize route"
            }
            routeState.isOptimizing = false
        }
    }

    // MARK: - Route Label

    private var routeLabelText: String {
        switch Priority(rawValue: savedPriority) {
        case .lowestCost: return "LOWEST COST ROUTE"
        case .shortestRoute: return "SHORTEST DISTANCE ROUTE"
        case .fastestTrip: return "FEWEST STOPS ROUTE"
        case .none: return "LOWEST COST ROUTE"
        }
    }

    private var routeLabelIcon: String {
        switch Priority(rawValue: savedPriority) {
        case .lowestCost: return "paperplane.fill"
        case .shortestRoute: return "point.topleft.down.to.point.bottomright.curvepath.fill"
        case .fastestTrip: return "mappin.and.ellipse"
        case .none: return "paperplane.fill"
        }
    }

    // MARK: - Helpers

    @ViewBuilder
    private func routeItemThumbnail(_ item: RouteItem) -> some View {
        if let imageUrl = item.imageUrl, let url = URL(string: imageUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                default:
                    Text(categoryEmojiForSlug(item.categorySlug))
                        .font(.title3)
                }
            }
        } else {
            Text(categoryEmojiForSlug(item.categorySlug))
                .font(.title3)
        }
    }

    private func categoryEmojiForSlug(_ slug: String?) -> String {
        guard let slug else { return "🛒" }
        switch slug {
        case "milk": return "🥛"
        case "water": return "💧"
        case "yogurt": return "🥄"
        case "bread": return "🍞"
        case "chicken": return "🍗"
        case "turkey": return "🦃"
        case "cereal": return "🥣"
        case "eggs": return "🥚"
        case "cheese": return "🧀"
        case "fresh-fruit": return "🍎"
        case "fresh-vegetables": return "🥬"
        case "pasta-rice-grains": return "🍝"
        case "chips": return "🍿"
        case "canned-packaged-foods": return "🥫"
        case "frozen-vegetables": return "🥦"
        case "bakery": return "🥐"
        case "beverages": return "🥤"
        case "breakfast": return "🥞"
        case "deli": return "🥪"
        case "frozen": return "❄️"
        case "international": return "🌍"
        case "meatandseafood": return "🥩"
        case "pantry": return "🫙"
        case "produce": return "🥕"
        case "refrigerated": return "🧊"
        case "snacks": return "🍿"
        default: return "🛒"
        }
    }

    // MARK: - Price Report Helpers

    private func openReport(for item: RouteItem, in stop: RouteStop) {
        guard let storeId = stop.store.id else { return }
        reportingItem = PriceReportingTarget(
            item:      item,
            storeId:   storeId,
            storeName: stop.store.name
        )
    }

    @ViewBuilder
    private func reportBadge(summary: PriceReportSummary) -> some View {
        let key = PriceReportPairKey(productId: summary.productId, storeId: summary.storeId)
        let isShowing = Binding<Bool>(
            get: { reportPopoverKey == key },
            set: { if !$0 { reportPopoverKey = nil } }
        )

        Button {
            reportPopoverKey = key
        } label: {
            HStack(spacing: 2) {
                Image(systemName: "exclamationmark.bubble.fill")
                    .font(.caption2)
                Text("\(summary.count)")
                    .font(.caption2.weight(.semibold))
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(NeighborlyTheme.orangeSoft)
            .foregroundStyle(NeighborlyTheme.orange)
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
        .popover(isPresented: isShowing) {
            reportPopoverContent(summary: summary)
                .presentationCompactAdaptation(.popover)
        }
    }

    @ViewBuilder
    private func reportPopoverContent(summary: PriceReportSummary) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.bubble.fill")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(NeighborlyTheme.orange)
                Text("\(summary.count) \(summary.count == 1 ? "person" : "people") reported")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.textSecondary)
            }
            Text(summary.latestReportedPrice, format: .currency(code: "USD"))
                .font(.title2.weight(.heavy))
                .foregroundStyle(NeighborlyTheme.orange)
            if let relative = relativeTimeString(from: summary.latestReportedAt) {
                Text("last reported \(relative)")
                    .font(.caption2)
                    .foregroundStyle(NeighborlyTheme.textMuted)
            }
        }
        .padding(14)
        .frame(minWidth: 180)
    }

    @MainActor
    private func loadSummary(for route: OptimizedRoute) async {
        var pairs: [PriceReportPair] = []
        for stop in route.stops {
            guard let storeId = stop.store.id else { continue }
            for item in stop.items {
                pairs.append(PriceReportPair(
                    productId: item.productId,
                    storeId:   storeId
                ))
            }
        }
        guard !pairs.isEmpty else { return }
        do {
            let summaries = try await APIService.getPriceReportSummary(pairs: pairs)
            var lookup: [PriceReportPairKey: PriceReportSummary] = [:]
            for s in summaries {
                lookup[PriceReportPairKey(productId: s.productId, storeId: s.storeId)] = s
            }
            priceReportSummary = lookup
        } catch {
            // silent: no badges if the fetch fails.
        }
    }

    @MainActor
    private func refreshSummary(
        for pair: PriceReportPair,
        optimisticPrice: Double? = nil
    ) async {
        let key = PriceReportPairKey(productId: pair.productId, storeId: pair.storeId)

        // immediate optimistic bump so the badge reflects the user's submission
        // before the server response lands.
        if let optimisticPrice {
            let prior = priceReportSummary[key]
            priceReportSummary[key] = PriceReportSummary(
                productId: pair.productId,
                storeId:   pair.storeId,
                count:     (prior?.count ?? 0) + 1,
                latestReportedPrice: optimisticPrice,
                latestReportedAt:    ISO8601DateFormatter().string(from: Date())
            )
        }

        do {
            let summaries = try await APIService.getPriceReportSummary(pairs: [pair])
            if let updated = summaries.first {
                priceReportSummary[key] = updated
            }
        } catch {
            // silent: keep the optimistic value if the refresh fails.
        }
    }
}

// MARK: - Swap Sheet

struct SwapSheet: View {
    let item: RouteItem
    let alternatives: [Product]
    let isLoading: Bool
    let onSwap: (Product) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Current item
                    VStack(alignment: .leading, spacing: 8) {
                        Text("CURRENT ITEM")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(NeighborlyTheme.textMuted)
                            .tracking(0.5)

                        HStack(spacing: 10) {
                            Text(item.name)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(NeighborlyTheme.textPrimary)
                            Spacer()
                            let effectivePrice = item.salePrice ?? item.price
                            Text(effectivePrice, format: .currency(code: "USD"))
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(NeighborlyTheme.green)
                        }
                        .padding(12)
                        .background(NeighborlyTheme.orangeSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    Divider()

                    // Alternatives
                    Text("SWAP FOR")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .tracking(0.5)

                    if isLoading {
                        HStack {
                            Spacer()
                            ProgressView()
                            Text("Finding alternatives...")
                                .font(.subheadline)
                                .foregroundStyle(NeighborlyTheme.textMuted)
                            Spacer()
                        }
                        .padding(.vertical, 20)
                    } else if alternatives.isEmpty {
                        Text("No alternatives found in this category")
                            .font(.subheadline)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 20)
                    } else {
                        VStack(spacing: 8) {
                            ForEach(alternatives) { alt in
                                Button {
                                    onSwap(alt)
                                } label: {
                                    HStack(spacing: 10) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(alt.name)
                                                .font(.subheadline)
                                                .foregroundStyle(NeighborlyTheme.textPrimary)
                                                .lineLimit(2)
                                                .multilineTextAlignment(.leading)
                                            if let brand = alt.brand {
                                                Text(brand)
                                                    .font(.caption)
                                                    .foregroundStyle(NeighborlyTheme.textSecondary)
                                            }
                                            Text(alt.unitSize)
                                                .font(.caption2)
                                                .foregroundStyle(NeighborlyTheme.textMuted)
                                        }

                                        Spacer()

                                        if let price = alt.bestPrice {
                                            VStack(alignment: .trailing, spacing: 2) {
                                                Text(price, format: .currency(code: "USD"))
                                                    .font(.subheadline.weight(.semibold))
                                                    .foregroundStyle(NeighborlyTheme.green)
                                                if let store = alt.bestPriceStoreName {
                                                    Text(store)
                                                        .font(.caption2)
                                                        .foregroundStyle(NeighborlyTheme.textMuted)
                                                }
                                            }
                                        }

                                        Image(systemName: "arrow.triangle.2.circlepath.circle.fill")
                                            .font(.title3)
                                            .foregroundStyle(NeighborlyTheme.orange)
                                    }
                                    .padding(12)
                                    .background(NeighborlyTheme.cardBackground)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }
                    }
                }
                .padding(20)
            }
        }
        .background(NeighborlyTheme.background)
    }
}

// MARK: - Trip Banner (sticky bottom)

extension RouteView {
    fileprivate func tripBanner(_ route: OptimizedRoute) -> some View {
        let allItems = route.stops.flatMap { $0.items }
        let collectedItems = allItems.filter { checkedItemIds.contains($0.id) }
        let collectedTotal = collectedItems.reduce(0.0) { $0 + ($1.salePrice ?? $1.price) }
        let total = route.totalCost
        let progress: Double = total > 0 ? min(1, collectedTotal / total) : 0

        // Active stop = first stop with any unchecked items
        let activeStop: (index: Int, stop: RouteStop)? = {
            for (idx, stop) in route.stops.enumerated() {
                let pending = stop.items.contains { !checkedItemIds.contains($0.id) }
                if pending { return (idx, stop) }
            }
            return nil
        }()

        return VStack(spacing: 8) {
            // progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(NeighborlyTheme.green.opacity(0.18))
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(red: 0.25, green: 0.65, blue: 0.45),
                                    NeighborlyTheme.green
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geo.size.width * progress)
                }
            }
            .frame(height: 5)
            .clipShape(Capsule())

            HStack(alignment: .center, spacing: 12) {
                if let active = activeStop {
                    let pending = active.stop.items.filter {
                        !checkedItemIds.contains($0.id)
                    }.count
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Stop \(active.index + 1) of \(route.stops.count) · \(active.stop.store.name)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(NeighborlyTheme.textPrimary)
                            .lineLimit(1)
                        Text("\(pending) item\(pending == 1 ? "" : "s") to grab here")
                            .font(.caption2)
                            .foregroundStyle(NeighborlyTheme.textSecondary)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.seal.fill")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(NeighborlyTheme.green)
                            Text("Trip complete")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(NeighborlyTheme.green)
                        }
                        Text("All \(allItems.count) item\(allItems.count == 1 ? "" : "s") collected")
                            .font(.caption2)
                            .foregroundStyle(NeighborlyTheme.textSecondary)
                    }
                }

                Spacer(minLength: 0)

                VStack(alignment: .trailing, spacing: 1) {
                    Text(collectedTotal, format: .currency(code: "USD"))
                        .font(.system(size: 16, weight: .heavy, design: .rounded))
                        .foregroundStyle(NeighborlyTheme.green)
                    Text("of " + total.formatted(.currency(code: "USD")))
                        .font(.caption2)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .background(.ultraThinMaterial)
        .overlay(
            Rectangle()
                .frame(height: 1)
                .foregroundStyle(NeighborlyTheme.textMuted.opacity(0.15))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        )
    }
}

// MARK: - Interleaved Stops + Directions

extension RouteView {
    /// renders directions blocks between stops, in visit order. when the user
    /// taps a pin we hide everything before that stop. when route data hasn't
    /// arrived yet we just render the stops.
    @ViewBuilder
    fileprivate func interleavedStopsAndDirections(route: OptimizedRoute) -> some View {
        let directions = directionsRoute
        // hasUserLeg: legs.count == stops.count when user location was the origin.
        // when only stores are waypoints, legs.count == stops.count - 1 and stop 0
        // has no preceding leg.
        let hasUserLeg: Bool = {
            guard let directions else { return false }
            return directions.legs.count >= route.stops.count
        }()

        ForEach(Array(route.stops.enumerated()), id: \.element.id) { stopIdx, stop in
            if focusedStopIndex == nil || stopIdx >= focusedStopIndex! {
                let legIdx = hasUserLeg ? stopIdx : stopIdx - 1
                if let directions, legIdx >= 0, legIdx < directions.legs.count {
                    DirectionsBlockCard(
                        leg: directions.legs[legIdx],
                        mode: directionsMode,
                        toStopName: stop.store.name
                    )
                    .transition(.opacity.combined(with: .move(edge: .top)))
                }
                stopCard(stop, index: stopIdx + 1)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    fileprivate func travelSummary(
        route: MapboxDirectionsService.DirectionsRoute,
        mode: TransportMode
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: mode.systemImage)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(Circle().fill(modeColor(for: mode)))

            VStack(alignment: .leading, spacing: 2) {
                Text(formatDuration(route.totalDuration))
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                Text(formatDistance(route.totalDistance))
                    .font(.system(size: 13))
                    .foregroundStyle(NeighborlyTheme.textSecondary)
            }
            Spacer()
            Text(mode.label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(NeighborlyTheme.textMuted)
        }
        .padding(14)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - Per-Leg Directions Block

struct DirectionsBlockCard: View {
    let leg: MapboxDirectionsService.RouteLeg
    let mode: TransportMode
    let toStopName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "arrow.turn.down.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(modeColor(for: mode))
                Text("DIRECTIONS TO \(toStopName.uppercased())")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(NeighborlyTheme.textMuted)
                    .tracking(0.5)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Text("\(formatDistance(leg.distance)) · \(formatDuration(leg.duration))")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.textSecondary)
            }

            VStack(alignment: .leading, spacing: 0) {
                if leg.steps.isEmpty {
                    Text("No turn-by-turn directions available.")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .padding(.vertical, 8)
                } else {
                    ForEach(Array(leg.steps.enumerated()), id: \.offset) { index, step in
                        DirectionsStepRow(
                            step: step,
                            isLast: index == leg.steps.count - 1,
                            accent: modeColor(for: mode)
                        )
                    }
                }
            }
        }
        .padding(16)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct DirectionsStepRow: View {
    let step: MapboxDirectionsService.RouteStep
    let isLast: Bool
    let accent: Color

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            // icon column with vertical connecting line
            VStack(spacing: 0) {
                Image(systemName: stepIcon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(Circle().fill(stepColor))
                if !isLast {
                    Rectangle()
                        .fill(NeighborlyTheme.textMuted.opacity(0.2))
                        .frame(width: 2)
                        .frame(maxHeight: .infinity)
                }
            }
            .frame(width: 28)

            VStack(alignment: .leading, spacing: 4) {
                Text(plain(step.instruction))
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                if let transit = step.transitDetails {
                    transitSubtitle(transit)
                } else {
                    Text(metaLine())
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                }
            }
            .padding(.bottom, isLast ? 0 : 14)

            Spacer(minLength: 0)
        }
    }

    private var stepIcon: String {
        switch step.travelMode.uppercased() {
        case "WALK", "WALKING":         return "figure.walk"
        case "DRIVE", "DRIVING":        return "car.fill"
        case "BICYCLE", "BICYCLING":    return "bicycle"
        case "TRANSIT":
            switch (step.transitDetails?.vehicleType ?? "").uppercased() {
            case "SUBWAY", "METRO_RAIL", "MONORAIL":      return "tram.fill"
            case "BUS", "TROLLEYBUS", "INTERCITY_BUS":    return "bus.fill"
            case "RAIL", "HEAVY_RAIL", "COMMUTER_TRAIN":  return "train.side.front.car"
            case "FERRY":                                 return "ferry.fill"
            default:                                       return "tram.fill"
            }
        default:                        return "arrow.up.right"
        }
    }

    private var stepColor: Color {
        if step.travelMode.uppercased() == "TRANSIT",
           let hex = step.transitDetails?.lineColorHex,
           let color = Color(hex: hex) {
            return color
        }
        return accent
    }

    private func transitSubtitle(_ transit: MapboxDirectionsService.TransitDetails) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if let short = transit.lineShortName, !short.isEmpty {
                    Text(short)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(transit.lineTextColorHex.flatMap { Color(hex: $0) } ?? .white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(stepColor))
                }
                if let head = transit.headsign {
                    Text("toward \(head)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                }
            }
            HStack(spacing: 6) {
                if let stops = transit.numStops {
                    Text("\(stops) stop\(stops == 1 ? "" : "s")")
                        .font(.caption2)
                        .foregroundStyle(NeighborlyTheme.textSecondary)
                }
                Text("·")
                    .font(.caption2)
                    .foregroundStyle(NeighborlyTheme.textMuted)
                Text(formatDuration(step.duration))
                    .font(.caption2)
                    .foregroundStyle(NeighborlyTheme.textSecondary)
            }
            if let dep = transit.departureStop, let arr = transit.arrivalStop {
                Text("\(dep) → \(arr)")
                    .font(.caption2)
                    .foregroundStyle(NeighborlyTheme.textMuted)
                    .lineLimit(2)
            }
        }
    }

    private func metaLine() -> String {
        let dist = formatDistance(step.distance)
        let dur = formatDuration(step.duration)
        if dist.isEmpty { return dur }
        return "\(dist) · \(dur)"
    }

    /// strip stray html tags (Google Routes API generally returns plain text but
    /// occasional bold tags appear in some legacy responses).
    private func plain(_ s: String) -> String {
        guard s.contains("<") else { return s }
        return s.replacingOccurrences(
            of: "<[^>]+>",
            with: "",
            options: .regularExpression
        )
    }
}

// MARK: - Helpers

fileprivate func modeColor(for mode: TransportMode) -> Color {
    switch mode {
    case .walking:         return Color(red: 0x00/255, green: 0xC8/255, blue: 0x53/255)
    case .car:             return Color(red: 0x1A/255, green: 0x73/255, blue: 0xE8/255)
    case .publicTransport: return Color(red: 0xFB/255, green: 0x8C/255, blue: 0x00/255)
    }
}

fileprivate func formatDuration(_ seconds: Double) -> String {
    let total = max(0, Int(seconds.rounded()))
    if total < 60 { return "\(total) sec" }
    let mins = total / 60
    if mins < 60 { return "\(mins) min" }
    let hrs = mins / 60
    let remMin = mins % 60
    return remMin == 0 ? "\(hrs) hr" : "\(hrs) hr \(remMin) min"
}

fileprivate func relativeTimeString(from iso: String) -> String? {
    let parser = ISO8601DateFormatter()
    parser.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = parser.date(from: iso) ?? ISO8601DateFormatter().date(from: iso)
    guard let date else { return nil }
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .short
    return formatter.localizedString(for: date, relativeTo: .now)
}

fileprivate func formatDistance(_ meters: Double) -> String {
    guard meters > 0 else { return "" }
    let miles = meters / 1609.34
    if miles < 0.1 {
        let feet = Int((meters * 3.28084).rounded())
        return "\(feet) ft"
    }
    return String(format: "%.1f mi", miles)
}

private extension Color {
    /// initialize from "#RRGGBB" or "RRGGBB" hex strings (returns nil for invalid).
    init?(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let value = UInt32(s, radix: 16) else { return nil }
        let r = Double((value >> 16) & 0xFF) / 255
        let g = Double((value >> 8) & 0xFF) / 255
        let b = Double(value & 0xFF) / 255
        self = Color(red: r, green: g, blue: b)
    }
}

struct PriceReportingTarget: Identifiable {
    let id = UUID()
    let item: RouteItem
    let storeId: String
    let storeName: String
}

#Preview {
    RouteView(routeState: RouteState())
}
