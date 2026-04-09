import SwiftUI
import MapboxMaps
import CoreLocation

/// displays store stops on a mapbox map with numbered pins, a real walking
/// route polyline (via mapbox directions api), and the user's live location.
///
/// to replace this with live turn by turn navigation:
/// 1. add mapbox-navigation-ios spm package
/// 2. replace this view with a UIViewControllerRepresentable wrapping
///    NavigationViewController from MapboxNavigationUIKit
/// 3. build NavigationRouteOptions from the same waypoints
/// 4. use mapboxNavigation.routingProvider().calculateRoutes(options:)
/// 5. present NavigationViewController with the calculated routes
/// 6. subscribe to navigation.waypointsArrival to detect store arrivals
///    and automatically show the shopping list for that store
struct MapboxRouteMap: View {
    let stops: [RouteStop]

    @State private var viewport: Viewport = .styleDefault
    @State private var routeCoordinates: [CLLocationCoordinate2D] = []
    @State private var legInfos: [MapboxDirectionsService.RouteLeg] = []
    private let locationManager = CLLocationManager()

    private var storeCoordinates: [CLLocationCoordinate2D] {
        stops.compactMap { stop in
            guard let lat = stop.store.lat, let lng = stop.store.lng else { return nil }
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
    }

    var body: some View {
        Map(viewport: $viewport) {
            // user location puck
            Puck2D(bearing: .heading)

            // route polyline, real walking route if available, straight lines as fallback
            let lineCoords = routeCoordinates.isEmpty ? storeCoordinates : routeCoordinates
            if lineCoords.count >= 2 {
                PolylineAnnotation(lineCoordinates: lineCoords)
                    .lineColor(StyleColor(NeighborlyTheme.green))
                    .lineWidth(4)
                    .lineOpacity(0.8)
            }

            // numbered store pins with eta badges
            ForEvery(Array(stops.enumerated()), id: \.element.id) { index, stop in
                if let lat = stop.store.lat, let lng = stop.store.lng {
                    MapViewAnnotation(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng)) {
                        storePinView(
                            index: index + 1,
                            walkMinutes: legDurationMinutes(legIndex: index)
                        )
                    }
                    .allowOverlap(true)
                }
            }
        }
        .mapStyle(.streets)
        .onAppear {
            locationManager.requestWhenInUseAuthorization()
            fitToStops()
            Task { await fetchRoute() }
        }
    }

    // MARK: - Store Pin

    private func storePinView(index: Int, walkMinutes: Int?) -> some View {
        VStack(spacing: 2) {
            // eta badge above pin
            if let mins = walkMinutes {
                Text("\(mins) min")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(NeighborlyTheme.orange)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(NeighborlyTheme.orangeSoft)
                    .clipShape(Capsule())
            }

            ZStack {
                Circle()
                    .fill(NeighborlyTheme.green)
                    .frame(width: 30, height: 30)
                    .shadow(color: .black.opacity(0.2), radius: 2, y: 1)

                Text("\(index)")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.white)
            }

            Triangle()
                .fill(NeighborlyTheme.green)
                .frame(width: 10, height: 6)
                .offset(y: -2)
        }
    }

    // MARK: - Directions

    private func fetchRoute() async {
        // build waypoints: user location (if available), store 1, store 2, ...
        var waypoints: [CLLocationCoordinate2D] = []
        if let userLoc = locationManager.location?.coordinate {
            waypoints.append(userLoc)
        }
        waypoints.append(contentsOf: storeCoordinates)

        guard waypoints.count >= 2 else { return }

        do {
            let route = try await MapboxDirectionsService.getWalkingRoute(waypoints: waypoints)
            routeCoordinates = route.coordinates
            legInfos = route.legs
        } catch {
            // fall back to straight lines (already the default)
        }
    }

    /// walking duration in minutes for the leg leading to stop at legIndex.
    /// leg 0 is user to store1, leg 1 is store1 to store2, etc.
    private func legDurationMinutes(legIndex: Int) -> Int? {
        // If user location was included, legs are offset by 0 (leg 0 = user→stop 0)
        // If no user location, there's no "leg to first stop"
        let hasUserLeg = locationManager.location != nil
        let targetLeg = hasUserLeg ? legIndex : legIndex - 1

        guard targetLeg >= 0, targetLeg < legInfos.count else { return nil }
        let seconds = legInfos[targetLeg].duration
        return max(1, Int(seconds / 60))
    }

    // MARK: - Viewport

    private func fitToStops() {
        var allCoords = storeCoordinates
        if let userLoc = locationManager.location?.coordinate {
            allCoords.append(userLoc)
        }

        guard allCoords.count >= 2 else {
            if let first = allCoords.first {
                viewport = .camera(center: first, zoom: 14)
            }
            return
        }

        let line = LineString(allCoords)
        viewport = .overview(
            geometry: line,
            bearing: 0,
            pitch: 0,
            geometryPadding: EdgeInsets(top: 40, leading: 40, bottom: 40, trailing: 40),
            maxZoom: 15
        )
    }
}

// Simple triangle shape for pin pointer
private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: rect.midX, y: rect.maxY))
            p.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
            p.closeSubpath()
        }
    }
}
