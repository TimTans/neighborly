import SwiftUI
import SwiftData

struct RecipeDetailView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var groceryItems: [GroceryListItem]

    let recipe: RecipeSuggestion

    @State private var addedIngredientIds: Set<String> = []

    /// keys that mark an ingredient as already on the grocery list. catalog
    /// items match by productId; pantry items match by name+quantity since they
    /// don't have a productId. covers both items already saved from prior
    /// sessions and ones added in this session.
    private var addedIngredientKeys: Set<String> {
        var keys = Set<String>()
        for item in groceryItems {
            if let pid = item.productId, !pid.isEmpty {
                keys.insert(pid)
            }
            keys.insert(item.name + item.unitSize)
        }
        return keys.union(addedIngredientIds)
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                headerCard
                ingredientsSection
                section(title: "Steps", items: recipe.steps)
                section(title: "Why It Matches", items: recipe.whyItMatches)
                section(title: "Nutrition Notes", items: recipe.nutritionNotes)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 24)
        }
        .background(NeighborlyTheme.background.ignoresSafeArea())
        .navigationTitle("Recipe")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var ingredientsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Text("Ingredients")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                Spacer()
                if let total = catalogSubtotal {
                    Text(total, format: .currency(code: "USD"))
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(NeighborlyTheme.green)
                }
            }

            VStack(alignment: .leading, spacing: 10) {
                ForEach(recipe.ingredients) { ingredient in
                    ingredientRow(ingredient)
                }
            }
        }
        .padding(18)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    private func ingredientRow(_ ingredient: RecipeIngredient) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(ingredient.quantity)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(NeighborlyTheme.textPrimary)
                Text(ingredient.name)
                    .font(.system(size: 15))
                    .foregroundStyle(NeighborlyTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if !ingredient.fromCatalog {
                    Text("pantry")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(NeighborlyTheme.textMuted)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(NeighborlyTheme.textMuted.opacity(0.12))
                        .clipShape(Capsule())
                }
            }

            // Sub-card with Add only when there's a real catalog match.
            if ingredient.fromCatalog {
                ingredientProductCard(ingredient)
            }
        }
    }

    private func ingredientProductCard(_ ingredient: RecipeIngredient) -> some View {
        let isAdded = addedIngredientKeys.contains(ingredient.id)

        return HStack(spacing: 12) {
            ingredientThumbnail(ingredient)
                .frame(width: 44, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                if let store = ingredient.storeName {
                    Text(store)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(NeighborlyTheme.textPrimary)
                        .lineLimit(1)
                }
                if let price = ingredient.price {
                    Text(price, format: .currency(code: "USD"))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(NeighborlyTheme.green)
                }
            }

            Spacer(minLength: 0)

            Button {
                addIngredientToList(ingredient)
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: isAdded ? "checkmark" : "plus")
                        .font(.system(size: 11, weight: .bold))
                    Text(isAdded ? "Added" : "Add")
                        .font(.system(size: 12, weight: .semibold))
                }
                .foregroundStyle(isAdded ? NeighborlyTheme.textMuted : .white)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    Capsule().fill(isAdded
                        ? NeighborlyTheme.textMuted.opacity(0.15)
                        : NeighborlyTheme.green)
                )
            }
            .buttonStyle(.plain)
            .disabled(isAdded)
        }
        .padding(10)
        .background(NeighborlyTheme.surfaceSecondary.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    @ViewBuilder
    private func ingredientThumbnail(_ ingredient: RecipeIngredient) -> some View {
        if let imageUrl = ingredient.imageUrl, let url = URL(string: imageUrl) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: .fit)
                default:
                    ingredientEmojiTile(ingredient.emoji)
                }
            }
            .background(NeighborlyTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            ingredientEmojiTile(ingredient.emoji)
        }
    }

    private func ingredientEmojiTile(_ emoji: String) -> some View {
        Text(emoji)
            .font(.title3)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(NeighborlyTheme.greenSoft)
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func addIngredientToList(_ ingredient: RecipeIngredient) {
        guard ingredient.fromCatalog else { return }
        let item = GroceryListItem(from: ingredient)
        modelContext.insert(item)
        addedIngredientIds.insert(ingredient.id)
    }

    private var catalogSubtotal: Double? {
        let prices = recipe.ingredients.compactMap { $0.fromCatalog ? $0.price : nil }
        guard !prices.isEmpty else { return nil }
        return prices.reduce(0, +)
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(recipe.title)
                .font(.system(size: 28, weight: .heavy, design: .serif))
                .foregroundStyle(NeighborlyTheme.textPrimary)

            Text(recipe.summary)
                .font(.system(size: 15))
                .foregroundStyle(NeighborlyTheme.textSecondary)

            HStack(spacing: 10) {
                detailPill("\(recipe.prepMinutes)m prep", color: NeighborlyTheme.green)
                detailPill("\(recipe.cookMinutes)m cook", color: NeighborlyTheme.orange)
                detailPill("Serves \(recipe.servings)", color: NeighborlyTheme.textSecondary)
            }
        }
        .padding(20)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private func section(title: String, items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(NeighborlyTheme.textPrimary)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                    HStack(alignment: .top, spacing: 10) {
                        Text("\(index + 1).")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(NeighborlyTheme.green)

                        Text(item)
                            .font(.system(size: 15))
                            .foregroundStyle(NeighborlyTheme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
        }
        .padding(18)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    private func detailPill(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }
}

#Preview {
    NavigationStack {
        RecipeDetailView(
            recipe: RecipeSuggestion(
                title: "Lemon Chickpea Rice Bowl",
                summary: "A bright, weeknight-friendly bowl with herbs and vegetables.",
                whyItMatches: ["Fully vegan", "Uses lower-sodium staples"],
                prepMinutes: 15,
                cookMinutes: 20,
                servings: 4,
                ingredients: [
                    RecipeIngredient(
                        name: "Brown rice",
                        quantity: "1 cup",
                        productId: "demo-rice",
                        fromCatalog: true,
                        storeName: "ShopRite",
                        price: 2.49,
                        imageUrl: nil,
                        categorySlug: "pasta-rice-grains"
                    ),
                    RecipeIngredient(
                        name: "No-salt chickpeas",
                        quantity: "1 can",
                        productId: "demo-chickpeas",
                        fromCatalog: true,
                        storeName: "KeyFood",
                        price: 1.79,
                        imageUrl: nil,
                        categorySlug: "canned-packaged-foods"
                    ),
                    RecipeIngredient(
                        name: "Salt",
                        quantity: "to taste",
                        productId: nil,
                        fromCatalog: false,
                        storeName: nil,
                        price: nil,
                        imageUrl: nil,
                        categorySlug: nil
                    ),
                ],
                steps: ["Cook the rice.", "Saute the vegetables.", "Assemble and serve."],
                nutritionNotes: ["Choose no-salt-added chickpeas to keep sodium down."]
            )
        )
    }
}
