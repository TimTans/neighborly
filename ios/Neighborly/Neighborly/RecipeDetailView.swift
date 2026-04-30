import SwiftUI

struct RecipeDetailView: View {
    let recipe: RecipeSuggestion

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                headerCard
                section(title: "Ingredients", items: recipe.ingredients)
                section(title: "Steps", items: recipe.steps)
                section(title: "Why It Matches", items: recipe.whyItMatches)
                section(title: "Nutrition Notes", items: recipe.nutritionNotes)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 24)
        }
        .background(NeighborlyTheme.background.ignoresSafeArea())
        .navigationTitle("Recipe")
        .navigationBarTitleDisplayMode(.inline)
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
                ingredients: ["1 cup brown rice", "1 can no-salt chickpeas"],
                steps: ["Cook the rice.", "Saute the vegetables.", "Assemble and serve."],
                nutritionNotes: ["Choose no-salt-added chickpeas to keep sodium down."]
            )
        )
    }
}
