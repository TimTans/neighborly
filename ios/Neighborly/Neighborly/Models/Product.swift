import Foundation

struct ProductNutrition: Codable, Hashable, Sendable {
    let servingSizeG: Double?
    let servingsPerContainer: Double?
    let caloriesKcal: Double?
    let proteinG: Double?
    let fatG: Double?
    let carbsG: Double?
    let fiberG: Double?
    let sodiumMg: Double?
    let cholesterolMg: Double?
    let sugarG: Double?
    let containsDairy: Bool?
    let containsPeanuts: Bool?
    let containsShellfish: Bool?
    let containsWheat: Bool?
}

enum WellnessViolation: Equatable {
    case allergen(String)
    case nutrientExceeded(String, Double, Double)  // name, actual, limit
}

extension ProductNutrition {
    /// parse a limit string like "1000 mg/Day" or "100" into a Double.
    private static func parseLimit(_ s: String) -> Double? {
        guard !s.isEmpty else { return nil }
        let token = s.components(separatedBy: CharacterSet(charactersIn: " /\t")).first ?? ""
        return Double(token)
    }

    /// returns all preference violations for this product.
    /// nil flags (unknown data) are treated as no violation — benefit of the doubt.
    func violations(against prefs: Preferences) -> [WellnessViolation] {
        guard prefs.wellnessEnabled else { return [] }
        var result: [WellnessViolation] = []

        if prefs.avoidPeanuts   && containsPeanuts   == true { result.append(.allergen("peanuts")) }
        if prefs.avoidDairy     && containsDairy     == true { result.append(.allergen("dairy")) }
        if prefs.avoidShellfish && containsShellfish == true { result.append(.allergen("shellfish")) }
        if prefs.avoidWheat     && containsWheat     == true { result.append(.allergen("wheat")) }

        if let limit = Self.parseLimit(prefs.sodiumLimit),
           let actual = sodiumMg, actual > limit {
            result.append(.nutrientExceeded("sodium", actual, limit))
        }
        if let limit = Self.parseLimit(prefs.cholesterolLimit),
           let actual = cholesterolMg, actual > limit {
            result.append(.nutrientExceeded("cholesterol", actual, limit))
        }
        if let limit = Self.parseLimit(prefs.sugarLimit),
           let actual = sugarG, actual > limit {
            result.append(.nutrientExceeded("sugar", actual, limit))
        }

        return result
    }
}

struct Product: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let brand: String?
    let imageUrl: String?
    let unitSize: String
    let upc: String?
    let productCategories: ProductCategory
    let storeProducts: [StoreProduct]
    let productNutrition: ProductNutrition?

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

struct ProductCategory: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let slug: String

    /// Emoji fallback for products without images, keyed by category slug.
    var emoji: String { Self.emoji(for: slug) }

    static func emoji(for slug: String?) -> String {
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

struct StoreProduct: Codable, Hashable, Sendable {
    let price: Double
    let salePrice: Double?
    let inStock: Bool
    let storeId: String
    let stores: Store
}

struct Store: Codable, Hashable, Sendable {
    let id: String?
    let name: String
    let chain: String?
    let storeNumber: String?
    let zipCode: String?
    let address: String?
    let lat: Double?
    let lng: Double?
}

struct ProductSearchResponse: Codable, Sendable {
    let data: [Product]
    let count: Int
}

/// Slim product row returned by GET /products/search.
/// Carries enough to render a search row; tap fetches the full Product.
struct ProductSearchResult: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let brand: String?
    let imageUrl: String?
    let unitSize: String?
    let upc: String?
    let categorySlug: String?
    let bestPrice: Double?
    let bestPriceStoreName: String?
    let containsDairy: Bool?
    let containsPeanuts: Bool?
    let containsShellfish: Bool?
    let containsWheat: Bool?

    /// Emoji fallback derived from the same slug→emoji map used by ProductCategory.
    var emoji: String {
        ProductCategory.emoji(for: categorySlug)
    }

    /// Display-safe unit size — empty string when the source row has null.
    var displayUnitSize: String { unitSize ?? "" }
}

struct ProductSearchResultsResponse: Codable, Sendable {
    let data: [ProductSearchResult]
    let count: Int
}

// MARK: - Price History

struct PriceHistoryPoint: Codable, Hashable, Sendable {
    let recordedAt: String
    let price: Double?
    let salePrice: Double?

    /// effective price: prefers sale price when present.
    var effectivePrice: Double? { salePrice ?? price }

    /// supabase timestamps may or may not include fractional seconds, so try both.
    var recordedDate: Date? {
        let isoFractional = ISO8601DateFormatter()
        isoFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = isoFractional.date(from: recordedAt) { return d }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime]
        return iso.date(from: recordedAt)
    }
}

struct PriceHistorySeries: Codable, Identifiable, Hashable, Sendable {
    let storeId: String?
    let storeName: String?
    let storeChain: String?
    let points: [PriceHistoryPoint]

    var id: String { storeId ?? storeName ?? UUID().uuidString }
    var displayName: String { storeName ?? "Unknown store" }
}

struct PriceHistoryResponse: Codable, Sendable {
    let data: [PriceHistorySeries]
}

// MARK: - Route Optimization

struct OptimizedRoute: Codable, Equatable, Sendable {
    let totalCost: Double
    let totalDistance: Double?
    let stops: [RouteStop]
    let itemsNotFound: [String]
    let noRoute: Bool?
    let noRouteReason: String?
}

struct RouteStop: Codable, Identifiable, Equatable, Sendable {
    var id: String { store.id ?? store.name }
    let store: Store
    let items: [RouteItem]
    let subtotal: Double
}

struct RouteItem: Codable, Identifiable, Hashable, Sendable {
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
