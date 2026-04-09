import Foundation

struct Product: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let brand: String?
    let imageUrl: String?
    let unitSize: String
    let upc: String
    let productCategories: ProductCategory
    let storeProducts: [StoreProduct]

    /// Lowest available price across all stores, preferring sale price.
    var bestPrice: Double? {
        storeProducts
            .map { $0.salePrice ?? $0.price }
            .min()
    }

    /// Store name that has the best price.
    var bestPriceStoreName: String? {
        guard let best = storeProducts.min(by: {
            ($0.salePrice ?? $0.price) < ($1.salePrice ?? $1.price)
        }) else { return nil }
        return best.stores.name
    }
}

struct ProductCategory: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let slug: String

    /// Emoji fallback for products without images, keyed by category slug.
    var emoji: String {
        switch slug {
        case "milk":                    return "🥛"
        case "water":                   return "💧"
        case "yogurt":                  return "🥄"
        case "bread":                   return "🍞"
        case "chicken":                 return "🍗"
        case "turkey":                  return "🦃"
        case "cereal":                  return "🥣"
        case "eggs":                    return "🥚"
        case "cheese":                  return "🧀"
        case "fresh-fruit":             return "🍎"
        case "fresh-vegetables":        return "🥬"
        case "pasta-rice-grains":       return "🍝"
        case "chips":                   return "🍿"
        case "canned-packaged-foods":   return "🥫"
        case "frozen-vegetables":       return "🥦"
        case "bakery":                  return "🥐"
        case "beverages":               return "🥤"
        case "breakfast":               return "🥞"
        case "deli":                    return "🥪"
        case "frozen":                  return "❄️"
        case "international":           return "🌍"
        case "meatandseafood":          return "🥩"
        case "pantry":                  return "🫙"
        case "produce":                 return "🥕"
        case "refrigerated":            return "🧊"
        case "snacks":                  return "🍿"
        default:                        return "🛒"
        }
    }
}

struct StoreProduct: Codable, Hashable {
    let price: Double
    let salePrice: Double?
    let inStock: Bool
    let storeId: String
    let stores: Store
}

struct Store: Codable, Hashable {
    let id: String?
    let name: String
    let chain: String?
    let storeNumber: String?
    let zipCode: String?
    let address: String?
    let lat: Double?
    let lng: Double?
}

struct ProductSearchResponse: Codable {
    let data: [Product]
    let count: Int
}

// MARK: - Route Optimization

struct OptimizedRoute: Codable {
    let totalCost: Double
    let stops: [RouteStop]
    let itemsNotFound: [String]
}

struct RouteStop: Codable, Identifiable {
    var id: String { store.id ?? store.name }
    let store: Store
    let items: [RouteItem]
    let subtotal: Double
}

struct RouteItem: Codable, Identifiable, Hashable {
    var id: String { productId }
    let productId: String
    let name: String
    let brand: String?
    let unitSize: String
    let imageUrl: String?
    let categorySlug: String?
    let price: Double
    let salePrice: Double?
}
