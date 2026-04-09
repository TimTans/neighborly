import Foundation
import CoreLocation

/// lightweight client for the mapbox directions api.
/// returns real walking/driving route geometry and per leg etas
/// without requiring the full mapbox navigation sdk.
///
/// to add turn by turn navigation with voice guidance:
/// 1. add the mapbox-navigation-ios spm package (v3.x)
/// 2. import MapboxNavigationCore, MapboxNavigationUIKit, MapboxDirections
/// 3. create a MapboxNavigationProvider:
///      let provider = MapboxNavigationProvider(coreConfig: .init())
///      let navigation = provider.mapboxNavigation
/// 4. build NavigationRouteOptions with the same waypoints used here
/// 5. calculate routes:
///      let request = navigation.routingProvider().calculateRoutes(options: options)
///      let result = await request.result
/// 6. present NavigationViewController for full ui:
///      let navVC = NavigationViewController(
///          navigationRoutes: routes,
///          navigationOptions: NavigationOptions(mapboxNavigation: navigation,
///              voiceController: provider.routeVoiceController,
///              eventsManager: provider.eventsManager())
///      )
///      present(navVC, animated: true)
/// 7. monitor progress via combine publishers:
///      navigation.navigation().routeProgress for distance/duration remaining
///      navigation.navigation().waypointsArrival for waypoint arrival events
///      navigation.navigation().voiceInstructions for spoken guidance
enum MapboxDirectionsService {

    struct DirectionsRoute {
        let coordinates: [CLLocationCoordinate2D]
        let legs: [RouteLeg]
        let totalDistance: Double  // meters
        let totalDuration: Double // seconds
    }

    struct RouteLeg {
        let distance: Double  // meters
        let duration: Double  // seconds
    }

    /// fetch a walking route through the given waypoints.
    /// returns the full route geometry and per leg distance/duration.
    static func getWalkingRoute(
        waypoints: [CLLocationCoordinate2D]
    ) async throws -> DirectionsRoute {
        guard waypoints.count >= 2 else {
            throw APIError.noData
        }

        let token = AppConfig.mapboxAccessToken
        guard !token.isEmpty else {
            throw APIError.invalidURL
        }

        // build coordinate string: lng,lat;lng,lat;...
        let coordString = waypoints
            .map { "\($0.longitude),\($0.latitude)" }
            .joined(separator: ";")

        // mapbox directions api, walking profile, geojson geometry, full resolution
        let urlString = "https://api.mapbox.com/directions/v5/mapbox/walking/"
            + "\(coordString)"
            + "?access_token=\(token)"
            + "&geometries=geojson&overview=full&steps=false"

        guard let url = URL(string: urlString) else {
            throw APIError.invalidURL
        }

        let (data, response) = try await URLSession.shared.data(from: url)

        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw APIError.serverError(statusCode: http.statusCode)
        }

        // parse the geojson response
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let routes = json["routes"] as? [[String: Any]],
              let route = routes.first,
              let geometry = route["geometry"] as? [String: Any],
              let coordArrays = geometry["coordinates"] as? [[Double]],
              let legsArray = route["legs"] as? [[String: Any]],
              let totalDistance = route["distance"] as? Double,
              let totalDuration = route["duration"] as? Double
        else {
            throw APIError.noData
        }

        let coordinates = coordArrays.map {
            CLLocationCoordinate2D(latitude: $0[1], longitude: $0[0])
        }

        let legs = legsArray.compactMap { leg -> RouteLeg? in
            guard let dist = leg["distance"] as? Double,
                  let dur = leg["duration"] as? Double else { return nil }
            return RouteLeg(distance: dist, duration: dur)
        }

        return DirectionsRoute(
            coordinates: coordinates,
            legs: legs,
            totalDistance: totalDistance,
            totalDuration: totalDuration
        )
    }
}
