import Foundation
import CoreLocation

/// fetches a route through a series of waypoints from our own backend, which
/// proxies google routes api for walking, driving, and transit. the backend
/// hides the api key, decodes google's encoded polylines, and stitches transit
/// legs (transit cannot accept intermediate waypoints in a single google call).
///
/// the file name is historical — this service no longer hits mapbox directly.
/// mapbox is still used to render the map and pins; the route data is google's.
enum MapboxDirectionsService {

    struct DirectionsRoute {
        let coordinates: [CLLocationCoordinate2D]
        let legs: [RouteLeg]
        let totalDistance: Double  // meters
        let totalDuration: Double  // seconds
    }

    struct RouteLeg {
        let distance: Double  // meters
        let duration: Double  // seconds
        let steps: [RouteStep]
    }

    struct RouteStep: Identifiable {
        let id = UUID()
        let instruction: String
        let maneuver: String?
        let distance: Double  // meters
        let duration: Double  // seconds
        let travelMode: String  // "WALK" | "DRIVE" | "TRANSIT" | etc.
        let transitDetails: TransitDetails?
    }

    struct TransitDetails {
        let lineName: String?
        let lineShortName: String?
        let lineColorHex: String?
        let lineTextColorHex: String?
        let vehicleType: String?  // "SUBWAY" | "BUS" | "RAIL" | etc.
        let headsign: String?
        let numStops: Int?
        let departureStop: String?
        let arrivalStop: String?
    }

    /// fetch a route in the given transport mode through the waypoints in order.
    /// throws APIError on network/server failure.
    static func getRoute(
        mode: TransportMode,
        waypoints: [CLLocationCoordinate2D]
    ) async throws -> DirectionsRoute {
        guard waypoints.count >= 2 else {
            throw APIError.noData
        }

        let url = AppConfig.apiBaseURL
            .appendingPathComponent("routes")
            .appendingPathComponent("directions")

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(
            BackendRequest(
                waypoints: waypoints.map { .init(lat: $0.latitude, lng: $0.longitude) },
                mode: mode.backendValue
            )
        )

        let (data, response) = try await URLSession.shared.data(for: request)

        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw APIError.serverError(statusCode: http.statusCode)
        }

        let payload = try JSONDecoder().decode(BackendResponse.self, from: data)

        let coords = payload.coordinates.compactMap { pair -> CLLocationCoordinate2D? in
            guard pair.count == 2 else { return nil }
            return CLLocationCoordinate2D(latitude: pair[1], longitude: pair[0])
        }
        let legs = payload.legs.map(Self.makeLeg)

        return DirectionsRoute(
            coordinates: coords,
            legs: legs,
            totalDistance: payload.totalDistanceM,
            totalDuration: payload.totalDurationS
        )
    }

    private static func makeLeg(_ leg: BackendResponse.Leg) -> RouteLeg {
        let steps = (leg.steps ?? []).map(Self.makeStep)
        return RouteLeg(
            distance: leg.distanceM,
            duration: leg.durationS,
            steps: steps
        )
    }

    private static func makeStep(_ step: BackendResponse.Step) -> RouteStep {
        RouteStep(
            instruction: step.instruction,
            maneuver: step.maneuver,
            distance: step.distanceM,
            duration: step.durationS,
            travelMode: step.travelMode,
            transitDetails: step.transitDetails.map(Self.makeTransitDetails)
        )
    }

    private static func makeTransitDetails(
        _ td: BackendResponse.WireTransitDetails
    ) -> TransitDetails {
        TransitDetails(
            lineName: td.lineName,
            lineShortName: td.lineShortName,
            lineColorHex: td.lineColor,
            lineTextColorHex: td.lineTextColor,
            vehicleType: td.vehicleType,
            headsign: td.headsign,
            numStops: td.numStops,
            departureStop: td.departureStop,
            arrivalStop: td.arrivalStop
        )
    }

    // MARK: - Wire types

    private struct BackendRequest: Encodable {
        let waypoints: [Waypoint]
        let mode: String

        struct Waypoint: Encodable {
            let lat: Double
            let lng: Double
        }
    }

    private struct BackendResponse: Decodable {
        let coordinates: [[Double]]
        let legs: [Leg]
        let totalDistanceM: Double
        let totalDurationS: Double

        enum CodingKeys: String, CodingKey {
            case coordinates
            case legs
            case totalDistanceM = "total_distance_m"
            case totalDurationS = "total_duration_s"
        }

        struct Leg: Decodable {
            let distanceM: Double
            let durationS: Double
            let steps: [Step]?

            enum CodingKeys: String, CodingKey {
                case distanceM = "distance_m"
                case durationS = "duration_s"
                case steps
            }
        }

        struct Step: Decodable {
            let instruction: String
            let maneuver: String?
            let distanceM: Double
            let durationS: Double
            let travelMode: String
            let transitDetails: WireTransitDetails?

            enum CodingKeys: String, CodingKey {
                case instruction
                case maneuver
                case distanceM = "distance_m"
                case durationS = "duration_s"
                case travelMode = "travel_mode"
                case transitDetails = "transit_details"
            }
        }

        struct WireTransitDetails: Decodable {
            let lineName: String?
            let lineShortName: String?
            let lineColor: String?
            let lineTextColor: String?
            let vehicleType: String?
            let headsign: String?
            let numStops: Int?
            let departureStop: String?
            let arrivalStop: String?

            enum CodingKeys: String, CodingKey {
                case lineName = "line_name"
                case lineShortName = "line_short_name"
                case lineColor = "line_color"
                case lineTextColor = "line_text_color"
                case vehicleType = "vehicle_type"
                case headsign
                case numStops = "num_stops"
                case departureStop = "departure_stop"
                case arrivalStop = "arrival_stop"
            }
        }
    }
}

private extension TransportMode {
    /// the value our backend's /routes/directions endpoint expects.
    var backendValue: String {
        switch self {
        case .walking:         return "walking"
        case .car:             return "driving"
        case .publicTransport: return "transit"
        }
    }
}
