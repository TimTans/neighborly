import Foundation

struct NutritionTargetsPayload: Codable, Equatable, Hashable {
    let cholesterol: String?
    let sodium: String?
    let sugar: String?
}

struct RecipeRequestPayload: Codable, Equatable, Hashable {
    let dietaryPreferences: [String]
    let avoidIngredients: [String]
    let nutritionTargets: NutritionTargetsPayload?

    enum CodingKeys: String, CodingKey {
        case dietaryPreferences = "dietary_preferences"
        case avoidIngredients = "avoid_ingredients"
        case nutritionTargets = "nutrition_targets"
    }
}

struct RecipeSuggestion: Codable, Identifiable, Equatable, Hashable {
    let title: String
    let summary: String
    let whyItMatches: [String]
    let prepMinutes: Int
    let cookMinutes: Int
    let servings: Int
    let ingredients: [String]
    let steps: [String]
    let nutritionNotes: [String]

    var id: String { title + summary }
}

extension Preferences {
    var recipeRequestPayload: RecipeRequestPayload {
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
            nutritionTargets: nutritionTargets?.isEmpty == true ? nil : nutritionTargets
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
