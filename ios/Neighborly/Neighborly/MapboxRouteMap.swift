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
    var onStopSelected: ((Int?) -> Void)?

    @State private var viewport: Viewport = .styleDefault
    @State private var routeCoordinates: [CLLocationCoordinate2D] = []
    @State private var legInfos: [MapboxDirectionsService.RouteLeg] = []
    @State private var isZoomedToPin = false
    private let locationManager = CLLocationManager()

    private var storeCoordinates: [CLLocationCoordinate2D] {
        stops.compactMap { stop in
            guard let lat = stop.store.lat, let lng = stop.store.lng else { return nil }
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
    }

    /// pre-built annotation data for the PointAnnotationGroup
    private var pinAnnotations: [PinData] {
        let hasUserLeg = locationManager.location != nil
        return stops.enumerated().compactMap { index, stop in
            guard let lat = stop.store.lat, let lng = stop.store.lng else { return nil }
            let targetLeg = hasUserLeg ? index : index - 1
            var walkMinutes: Int?
            if targetLeg >= 0, targetLeg < legInfos.count {
                walkMinutes = max(1, Int(legInfos[targetLeg].duration / 60))
            }
            return PinData(
                id: stop.id,
                index: index,
                displayNumber: index + 1,
                coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng),
                walkMinutes: walkMinutes
            )
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Map(viewport: $viewport) {
                Puck2D(bearing: .heading)

                // route polyline, real walking route if available, straight lines as fallback
                let lineCoords = routeCoordinates.isEmpty ? storeCoordinates : routeCoordinates
                if lineCoords.count >= 2 {
                    PolylineAnnotation(lineCoordinates: lineCoords)
                        .lineColor(StyleColor(NeighborlyTheme.green))
                        .lineWidth(4)
                        .lineOpacity(0.8)
                }

                // numbered store pins as layer annotations (reliable overlap at all zoom levels)
                PointAnnotationGroup(pinAnnotations, id: \.id) { pin in
                    PointAnnotation(coordinate: pin.coordinate)
                        .image(.init(image: PinRenderer.render(number: pin.displayNumber, walkMinutes: pin.walkMinutes), name: "pin-\(pin.displayNumber)"))
                        .iconAnchor(.bottom)
                        .onTapGesture {
                            handlePinTap(index: pin.index, lat: pin.coordinate.latitude, lng: pin.coordinate.longitude)
                        }
                }
                .iconAllowOverlap(true)
                .textAllowOverlap(true)
            }
            .mapStyle(.streets)
            .onAppear {
                locationManager.requestWhenInUseAuthorization()
                fitToStops()
                Task { await fetchRoute() }
            }
            .onChange(of: stops) { _, _ in
                routeCoordinates = []
                legInfos = []
                fitToStops(animated: true)
                Task { await fetchRoute() }
            }

            // show all stops button when zoomed to a single pin
            if isZoomedToPin {
                Button {
                    isZoomedToPin = false
                    fitToStops(animated: true)
                    onStopSelected?(nil)
                } label: {
                    Label("All stops", systemImage: "arrow.up.left.and.arrow.down.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(NeighborlyTheme.green)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.2), radius: 3, y: 1)
                }
                .padding(8)
                .transition(.opacity)
            }
        }
    }

    // MARK: - Pin Tap

    private func handlePinTap(index: Int, lat: Double, lng: Double) {
        isZoomedToPin = true
        withViewportAnimation(.easeIn(duration: 0.4)) {
            viewport = .camera(center: CLLocationCoordinate2D(latitude: lat, longitude: lng), zoom: 15)
        }
        onStopSelected?(index)
    }

    // MARK: - Directions

    private func fetchRoute() async {
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

    // MARK: - Viewport

    private func fitToStops(animated: Bool = false) {
        var allCoords = storeCoordinates
        if let userLoc = locationManager.location?.coordinate {
            allCoords.append(userLoc)
        }

        guard allCoords.count >= 2 else {
            if let first = allCoords.first {
                let update = { viewport = .camera(center: first, zoom: 14) }
                if animated { withViewportAnimation(.easeIn(duration: 0.5)) { update() } }
                else { update() }
            }
            return
        }

        let line = LineString(allCoords)
        let newViewport = Viewport.overview(
            geometry: line,
            bearing: 0,
            pitch: 0,
            geometryPadding: EdgeInsets(top: 40, leading: 40, bottom: 40, trailing: 40),
            maxZoom: 15
        )

        if animated {
            withViewportAnimation(.easeIn(duration: 0.5)) { viewport = newViewport }
        } else {
            viewport = newViewport
        }
    }
}

// MARK: - Pin Data

private struct PinData: Identifiable {
    let id: String
    let index: Int
    let displayNumber: Int
    let coordinate: CLLocationCoordinate2D
    let walkMinutes: Int?
}

// MARK: - Pin Image Renderer

private enum PinRenderer {
    private static let greenColor = UIColor(red: 12/255, green: 106/255, blue: 74/255, alpha: 1)
    private static let orangeColor = UIColor(red: 230/255, green: 126/255, blue: 34/255, alpha: 1)
    private static let orangeSoftColor = UIColor(red: 255/255, green: 243/255, blue: 224/255, alpha: 1)

    private static var cache: [String: UIImage] = [:]

    static func render(number: Int, walkMinutes: Int?) -> UIImage {
        let key = "\(number)-\(walkMinutes ?? -1)"
        if let cached = cache[key] { return cached }

        let scale = UIScreen.main.scale
        let pinDiameter: CGFloat = 30
        let triHeight: CGFloat = 6
        let width: CGFloat = 60
        let etaHeight: CGFloat = walkMinutes != nil ? 18 : 0
        let gap: CGFloat = walkMinutes != nil ? 3 : 0
        let totalHeight = etaHeight + gap + pinDiameter + triHeight

        let renderer = UIGraphicsImageRenderer(
            size: CGSize(width: width, height: totalHeight),
            format: {
                let fmt = UIGraphicsImageRendererFormat()
                fmt.scale = scale
                return fmt
            }()
        )

        let image = renderer.image { ctx in
            // ETA badge
            if let mins = walkMinutes {
                let text = "\(mins) min"
                let font = UIFont.systemFont(ofSize: 9, weight: .semibold)
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: orangeColor]
                let textSize = (text as NSString).size(withAttributes: attrs)
                let badgeW = textSize.width + 10
                let badgeH: CGFloat = 16
                let badgeX = (width - badgeW) / 2
                let badgePath = UIBezierPath(roundedRect: CGRect(x: badgeX, y: 1, width: badgeW, height: badgeH), cornerRadius: badgeH / 2)
                orangeSoftColor.setFill()
                badgePath.fill()
                let textX = (width - textSize.width) / 2
                let textY = 1 + (badgeH - textSize.height) / 2
                (text as NSString).draw(at: CGPoint(x: textX, y: textY), withAttributes: attrs)
            }

            let circleY = etaHeight + gap

            // shadow
            let gc = ctx.cgContext
            gc.saveGState()
            gc.setShadow(offset: CGSize(width: 0, height: 1), blur: 2, color: UIColor.black.withAlphaComponent(0.25).cgColor)
            let circleRect = CGRect(x: (width - pinDiameter) / 2, y: circleY, width: pinDiameter, height: pinDiameter)
            greenColor.setFill()
            UIBezierPath(ovalIn: circleRect).fill()
            gc.restoreGState()

            // number
            let numText = "\(number)"
            let numFont = UIFont.systemFont(ofSize: 13, weight: .bold)
            let numAttrs: [NSAttributedString.Key: Any] = [.font: numFont, .foregroundColor: UIColor.white]
            let numSize = (numText as NSString).size(withAttributes: numAttrs)
            (numText as NSString).draw(
                at: CGPoint(x: circleRect.midX - numSize.width / 2, y: circleRect.midY - numSize.height / 2),
                withAttributes: numAttrs
            )

            // triangle pointer
            let triTop = circleY + pinDiameter - 1
            let triPath = UIBezierPath()
            triPath.move(to: CGPoint(x: width / 2, y: triTop + triHeight))
            triPath.addLine(to: CGPoint(x: width / 2 - 5, y: triTop))
            triPath.addLine(to: CGPoint(x: width / 2 + 5, y: triTop))
            triPath.close()
            greenColor.setFill()
            triPath.fill()
        }

        cache[key] = image
        return image
    }
}
