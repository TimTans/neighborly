import Foundation
import SwiftUI
import CoreLocation

struct HomeMetric: Identifiable {
    let id = UUID()
    let label: String
    let value: String
    let sublabel: String
    let icon: String
    let tint: MetricTint

    enum MetricTint {
        case green, orange, gray
    }
}

struct HomeRouteStop: Identifiable {
    let id = UUID()
    let index: Int
    let name: String
    let address: String
    let distance: String
    let timeEstimate: String
    let itemsLabel: String
}

@Observable
final class HomeViewModel {
    var featuredRecipe: RecipeSuggestion?
    var isLoadingRecipe = false
    var recipeError: String?

    private var lastRecipeRequest: RecipeRequestPayload?
    /// owned by the model so the in-flight request survives view lifecycle
    /// (e.g. user switching tabs mid-load). without this, .task cancellation
    /// would discard the response and we'd have wasted backend credits.
    private var loadTask: Task<Void, Never>?

    let userName: String = "John Doe"
    let savingsThisTrip: String = "6.70"
    let totalBudget: String = "$120.00"
    let budgetUsed: String = "$57.31"
    let budgetProgress: Double = 57.31 / 120.0
    let savedThisMonth: String = "$42.80"
    let savedThisMonthLabel: String = "this month"
    let avgTripTime: String = "34m"
    let milesSaved: String = "12.4"
    let itemsTracked: String = "89"
    let alertsCount: String = "3"
    let optimizedStopsLabel: String = "3 stops"

    let savingsBarHeights: [CGFloat] = [0.4, 0.7, 0.5, 0.9, 0.6]

    let routeStops: [HomeRouteStop] = [
        HomeRouteStop(index: 1, name: "Aldi", address: "142 Atlantic Ave", distance: "0.8 mi", timeEstimate: "12 min", itemsLabel: "3 items"),
        HomeRouteStop(index: 2, name: "Trader Joe's", address: "130 Court St", distance: "1.2 mi", timeEstimate: "8 min", itemsLabel: "4 items"),
        HomeRouteStop(index: 3, name: "Costco", address: "976 3rd Ave", distance: "2.4 mi", timeEstimate: "15 min", itemsLabel: "2 items")
    ]

    /// Kick off a recipe load. Returns immediately — the actual work runs in
    /// a Task owned by this model, so it survives view disappearance.
    /// Subsequent calls with the same payload while one is already in-flight
    /// or already cached are no-ops.
    @MainActor
    func startLoadIfNeeded(
        using preferences: Preferences,
        userLocation: CLLocation? = nil,
        force: Bool = false
    ) {
        let payload = preferences.recipeRequestPayload(userLocation: userLocation)

        if !force {
            // already loading the same request → don't fire a duplicate
            if isLoadingRecipe, payload == lastRecipeRequest { return }
            // already have a cached result for these prefs → keep it
            if featuredRecipe != nil, payload == lastRecipeRequest { return }
        }

        // a force or a different payload supersedes any in-flight load
        loadTask?.cancel()

        isLoadingRecipe = true
        recipeError = nil
        lastRecipeRequest = payload

        loadTask = Task { [weak self] in
            do {
                let recipe = try await APIService.generateRecipe(
                    preferences: preferences,
                    userLocation: userLocation
                )
                if Task.isCancelled { return }
                await MainActor.run {
                    self?.featuredRecipe = recipe
                    self?.isLoadingRecipe = false
                }
            } catch is CancellationError {
                return
            } catch {
                if Task.isCancelled { return }
                await MainActor.run {
                    self?.featuredRecipe = nil
                    self?.recipeError = error.localizedDescription
                    self?.isLoadingRecipe = false
                }
            }
        }
    }
}
