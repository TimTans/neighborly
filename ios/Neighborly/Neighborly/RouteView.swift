import SwiftUI
import SwiftData

struct RouteView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var groceryItems: [GroceryListItem]
    var routeState: RouteState

    @State private var swapItem: RouteItem?
    @State private var alternatives: [Product] = []
    @State private var isLoadingAlternatives = false

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
                MapboxRouteMap(stops: route.stops)
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                // Summary card
                summaryCard(route)

                // Store stops
                ForEach(Array(route.stops.enumerated()), id: \.element.id) { index, stop in
                    stopCard(stop, index: index + 1)
                }

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
            HStack {
                Image(systemName: "paperplane.fill")
                    .font(.caption)
                    .foregroundStyle(NeighborlyTheme.green)
                Text("LOWEST COST ROUTE")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(NeighborlyTheme.green)
                    .tracking(0.5)
                Spacer()
            }

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
            }
            .frame(maxWidth: .infinity)
        }
        .padding(20)
        .background(NeighborlyTheme.greenSoft)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    // MARK: - Stop Card

    private func stopCard(_ stop: RouteStop, index: Int) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            // Store header
            HStack(spacing: 10) {
                Circle()
                    .fill(NeighborlyTheme.green)
                    .frame(width: 28, height: 28)
                    .overlay(
                        Text("\(index)")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.white)
                    )

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
                    Text("\(stop.items.count) item\(stop.items.count == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundStyle(NeighborlyTheme.textMuted)
                }
            }

            Divider()

            // Item rows — tappable for swap
            ForEach(stop.items) { item in
                Button {
                    Task { await loadAlternatives(for: item) }
                } label: {
                    HStack(spacing: 10) {
                        let emoji = categoryEmojiForSlug(item.categorySlug)
                        Text(emoji)
                            .font(.title3)
                            .frame(width: 32, height: 32)
                            .background(NeighborlyTheme.greenSoft)
                            .clipShape(RoundedRectangle(cornerRadius: 6))

                        VStack(alignment: .leading, spacing: 1) {
                            Text(item.name)
                                .font(.caption)
                                .foregroundStyle(NeighborlyTheme.textPrimary)
                                .lineLimit(1)
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

                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.caption2)
                            .foregroundStyle(NeighborlyTheme.textMuted)
                    }
                }
            }
        }
        .padding(16)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
            groceryItem.upc = replacement.upc
            groceryItem.productId = replacement.id
        }

        // Re-optimize with updated product IDs
        let productIds = groceryItems.compactMap { $0.productId }
        swapItem = nil

        Task {
            routeState.isOptimizing = true
            do {
                let route = try await APIService.optimizeRoute(productIds: productIds)
                routeState.optimizedRoute = route
            } catch {
                routeState.error = "Couldn't re-optimize route"
            }
            routeState.isOptimizing = false
        }
    }

    // MARK: - Helpers

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

#Preview {
    RouteView(routeState: RouteState())
}
