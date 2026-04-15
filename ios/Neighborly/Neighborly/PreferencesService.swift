import Foundation
import Supabase

// MARK: - DB record

struct UserPreferencesRecord: Codable {
    var userId: String
    // stores Priority.rawValue (display name e.g. "Lowest Cost") — consistent with AppStorage
    var optimizationMode: String?
    // stored in km; convert to/from miles at the service boundary (11 miles = unlimited = 0.0 km)
    var maxRadiusKm: Double?
    // 0.0 = unlimited (maps to slider value 11)
    var maxStops: Double?
    var walkingEnabled: Bool?
    var publicTransportEnabled: Bool?
    var carEnabled: Bool?
    // these three flags collectively represent Preferences.wellnessEnabled:
    // on save all three are set to wellnessEnabled; on fetch wellnessEnabled = sodium || cholesterol || sugar
    var sodiumEnabled: Bool?
    var sodiumLimit: String?
    var cholesterolEnabled: Bool?
    var cholesterolLimit: String?
    var sugarEnabled: Bool?
    var sugarLimit: String?
    var dietVegan: Bool?
    var dietGlutenFree: Bool?
    var dietLowCarb: Bool?
    var dietKosher: Bool?
    var dietHalal: Bool?
    var dietKeto: Bool?
    var avoidDairy: Bool?
    var avoidPeanuts: Bool?
    var avoidShellfish: Bool?
    var avoidWheat: Bool?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case optimizationMode = "optimization_mode"
        case maxRadiusKm = "max_radius_km"
        case maxStops = "max_stops"
        case walkingEnabled = "walking_enabled"
        case publicTransportEnabled = "public_transport_enabled"
        case carEnabled = "car_enabled"
        case sodiumEnabled = "sodium_enabled"
        case sodiumLimit = "sodium_limit"
        case cholesterolEnabled = "cholesterol_enabled"
        case cholesterolLimit = "cholesterol_limit"
        case sugarEnabled = "sugar_enabled"
        case sugarLimit = "sugar_limit"
        case dietVegan = "diet_vegan"
        case dietGlutenFree = "diet_gluten_free"
        case dietLowCarb = "diet_low_carb"
        case dietKosher = "diet_kosher"
        case dietHalal = "diet_halal"
        case dietKeto = "diet_keto"
        case avoidDairy = "avoid_dairy"
        case avoidPeanuts = "avoid_peanuts"
        case avoidShellfish = "avoid_shellfish"
        case avoidWheat = "avoid_wheat"
    }
}

// MARK: - Service

enum PreferencesService {

    /// fetch the current user's preferences from supabase.
    /// returns nil if no row exists yet (first-time user).
    /// throws on network or decoding errors.
    static func fetch() async throws -> Preferences? {
        let records: [UserPreferencesRecord] = try await supabase
            .from("user_preferences")
            .select()
            .limit(1)
            .execute()
            .value

        guard let record = records.first else { return nil }

        var prefs = Preferences()

        if let mode = record.optimizationMode {
            prefs.priority = Priority(rawValue: mode) ?? .lowestCost
        }

        if let km = record.maxRadiusKm {
            prefs.maxTravelDistanceMiles = km == 0.0 ? 11.0 : km * 0.621371
        }

        if let stops = record.maxStops {
            prefs.maxStops = stops == 0.0 ? 11.0 : stops
        }

        var modes: Set<TransportMode> = []
        if record.walkingEnabled ?? true         { modes.insert(.walking) }
        if record.publicTransportEnabled ?? true  { modes.insert(.publicTransport) }
        if record.carEnabled ?? true              { modes.insert(.car) }
        prefs.enabledModes = modes

        // only override if at least one flag was explicitly stored; otherwise keep struct default (true)
        let anyWellnessStored = record.sodiumEnabled != nil
            || record.cholesterolEnabled != nil
            || record.sugarEnabled != nil
        if anyWellnessStored {
            prefs.wellnessEnabled = (record.sodiumEnabled ?? false)
                || (record.cholesterolEnabled ?? false)
                || (record.sugarEnabled ?? false)
        }

        prefs.sodiumLimit      = record.sodiumLimit ?? ""
        prefs.cholesterolLimit = record.cholesterolLimit ?? ""
        prefs.sugarLimit       = record.sugarLimit ?? ""

        prefs.dietVegan      = record.dietVegan ?? false
        prefs.dietGlutenFree = record.dietGlutenFree ?? true
        prefs.dietLowCarb    = record.dietLowCarb ?? false
        prefs.dietKosher     = record.dietKosher ?? false
        prefs.dietHalal      = record.dietHalal ?? false
        prefs.dietKeto       = record.dietKeto ?? false

        prefs.avoidDairy     = record.avoidDairy ?? false
        prefs.avoidPeanuts   = record.avoidPeanuts ?? true
        prefs.avoidShellfish = record.avoidShellfish ?? false
        prefs.avoidWheat     = record.avoidWheat ?? false

        return prefs
    }

    /// save the given preferences to supabase for the given user.
    /// upserts on user_id conflict — safe to call regardless of whether
    /// a row already exists.
    static func save(_ prefs: Preferences, userId: String) async throws {

        let km: Double = prefs.maxTravelDistanceMiles >= 11
            ? 0.0
            : prefs.maxTravelDistanceMiles * 1.60934

        let stops: Double = prefs.maxStops >= 11 ? 0.0 : prefs.maxStops

        let record = UserPreferencesRecord(
            userId: userId,
            optimizationMode: prefs.priority.rawValue,
            maxRadiusKm: km,
            maxStops: stops,
            walkingEnabled: prefs.enabledModes.contains(.walking),
            publicTransportEnabled: prefs.enabledModes.contains(.publicTransport),
            carEnabled: prefs.enabledModes.contains(.car),
            sodiumEnabled: prefs.wellnessEnabled,
            sodiumLimit: prefs.sodiumLimit.isEmpty ? nil : prefs.sodiumLimit,
            cholesterolEnabled: prefs.wellnessEnabled,
            cholesterolLimit: prefs.cholesterolLimit.isEmpty ? nil : prefs.cholesterolLimit,
            sugarEnabled: prefs.wellnessEnabled,
            sugarLimit: prefs.sugarLimit.isEmpty ? nil : prefs.sugarLimit,
            dietVegan: prefs.dietVegan,
            dietGlutenFree: prefs.dietGlutenFree,
            dietLowCarb: prefs.dietLowCarb,
            dietKosher: prefs.dietKosher,
            dietHalal: prefs.dietHalal,
            dietKeto: prefs.dietKeto,
            avoidDairy: prefs.avoidDairy,
            avoidPeanuts: prefs.avoidPeanuts,
            avoidShellfish: prefs.avoidShellfish,
            avoidWheat: prefs.avoidWheat
        )

        try await supabase
            .from("user_preferences")
            .upsert(record, onConflict: "user_id")
            .execute()
    }
}
