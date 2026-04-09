import SwiftUI
import SwiftData
import MapboxMaps

@main
struct NeighborlyApp: App {
    @State private var authController = AuthController()

    init() {
        let token = AppConfig.mapboxAccessToken
        if !token.isEmpty {
            MapboxOptions.accessToken = token
        }
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if authController.isLoading {
                    ProgressView("Loading...")
                } else if authController.isAuthenticated {
                    MainTabView()
                } else {
                    LoginView()
                }
            }
            .environment(authController)
            .task {
                await authController.listenForAuthChanges()
            }
        }
        .modelContainer(for: GroceryListItem.self)
    }
}
