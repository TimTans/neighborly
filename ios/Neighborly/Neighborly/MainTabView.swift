import SwiftUI

@Observable
final class RouteState {
    var optimizedRoute: OptimizedRoute?
    var isOptimizing = false
    var error: String?
}

struct MainTabView: View {
    @State private var selectedTab = 0
    @State private var routeState = RouteState()

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem {
                    Image(systemName: selectedTab == 0 ? "house.fill" : "house")
                    Text("Home")
                }
                .tag(0)

            GroceryListView(routeState: routeState, selectedTab: $selectedTab)
                .tabItem {
                    Image(systemName: selectedTab == 1 ? "list.clipboard.fill" : "list.clipboard")
                    Text("Lists")
                }
                .tag(1)

            RouteView(routeState: routeState)
                .tabItem {
                    Image(systemName: selectedTab == 2 ? "paperplane.fill" : "paperplane")
                    Text("Route")
                }
                .tag(2)
        }
        .tint(NeighborlyTheme.green)
    }
}

#Preview {
    MainTabView()
}
