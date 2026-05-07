import Foundation
import CoreLocation

struct NutritionTargetsPayload: Codable, Equatable, Hashable {
    let cholesterol: String?
    let sodium: String?
    let sugar: String?
}

struct RecipeRequestPayload: Codable, Equatable, Hashable {
    let dietaryPreferences: [String]
    let avoidIngredients: [String]
    let nutritionTargets: NutritionTargetsPayload?
    let userLat: Double?
    let userLng: Double?
    let maxRadiusMiles: Double?

    enum CodingKeys: String, CodingKey {
        case dietaryPreferences = "dietary_preferences"
        case avoidIngredients = "avoid_ingredients"
        case nutritionTargets = "nutrition_targets"
        case userLat = "user_lat"
        case userLng = "user_lng"
        case maxRadiusMiles = "max_radius_miles"
    }
}

struct RecipeIngredient: Codable, Equatable, Hashable, Identifiable {
    let name: String
    let quantity: String
    let productId: String?
    let fromCatalog: Bool
    let storeName: String?
    let price: Double?
    let imageUrl: String?
    let categorySlug: String?

    var id: String {
        if let productId, !productId.isEmpty { return productId }
        return name + quantity
    }

    /// Emoji fallback when image is missing — same map used by ProductCategory.
    var emoji: String { ProductCategory.emoji(for: categorySlug) }
}

struct RecipeSuggestion: Codable, Identifiable, Equatable, Hashable {
    let title: String
    let summary: String
    let whyItMatches: [String]
    let prepMinutes: Int
    let cookMinutes: Int
    let servings: Int
    let ingredients: [RecipeIngredient]
    let steps: [String]
    let nutritionNotes: [String]

    var id: String { title + summary }
}

extension Preferences {
    /// Build the recipe request payload, embedding the user's coordinates and
    /// maximum travel radius so the backend can filter the LLM's product
    /// catalog to items the shopper can actually reach.
    func recipeRequestPayload(userLocation: CLLocation? = nil) -> RecipeRequestPayload {
        let dietaryPreferences = [
            dietVegan ? "vegan" : nil,
            dietGlutenFree ? "gluten_free" : nil,
            dietLowCarb ? "low_carb" : nil,
            dietKosher ? "kosher" : nil,
            dietHalal ? "halal" : nil,
            dietKeto ? "keto" : nil,
        ]
        .compactMap { $0 }

        let avoidIngredients = [
            avoidDairy ? "dairy" : nil,
            avoidPeanuts ? "peanuts" : nil,
            avoidShellfish ? "shellfish" : nil,
            avoidWheat ? "wheat" : nil,
        ]
        .compactMap { $0 }

        let nutritionTargets = wellnessEnabled
            ? NutritionTargetsPayload(
                cholesterol: cholesterolLimit.nilIfBlank,
                sodium: sodiumLimit.nilIfBlank,
                sugar: sugarLimit.nilIfBlank
            )
            : nil

        return RecipeRequestPayload(
            dietaryPreferences: dietaryPreferences,
            avoidIngredients: avoidIngredients,
            nutritionTargets: nutritionTargets?.isEmpty == true ? nil : nutritionTargets,
            userLat: userLocation?.coordinate.latitude,
            userLng: userLocation?.coordinate.longitude,
            maxRadiusMiles: userLocation == nil ? nil : maxTravelDistanceMiles
        )
    }
}

private extension NutritionTargetsPayload {
    var isEmpty: Bool {
        cholesterol == nil && sodium == nil && sugar == nil
    }
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
