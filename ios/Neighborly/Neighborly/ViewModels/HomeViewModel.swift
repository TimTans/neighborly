import Foundation
import SwiftUI

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
}
