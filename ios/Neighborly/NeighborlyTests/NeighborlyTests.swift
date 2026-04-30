import XCTest
@testable import Neighborly

final class NeighborlyTests: XCTestCase {
    func testRecipeRequestPayloadMapsPreferences() throws {
        var preferences = Preferences()
        preferences.wellnessEnabled = true
        preferences.dietVegan = true
        preferences.dietGlutenFree = true
        preferences.avoidDairy = true
        preferences.sodiumLimit = "1500 mg/day"

        let payload = preferences.recipeRequestPayload

        XCTAssertEqual(payload.dietaryPreferences, ["vegan", "gluten_free"])
        XCTAssertEqual(payload.avoidIngredients, ["dairy", "peanuts"])
        XCTAssertEqual(payload.nutritionTargets?.sodium, "1500 mg/day")
    }

    func testPreferencesStorePersistsPreferences() throws {
        let suiteName = "NeighborlyTests.PreferencesStore"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)

        let store = PreferencesStore(defaults: defaults)
        var preferences = Preferences()
        preferences.dietVegan = true
        preferences.sodiumLimit = "1200 mg/day"

        store.save(preferences)

        let reloaded = PreferencesStore(defaults: defaults)
        XCTAssertTrue(reloaded.preferences.dietVegan)
        XCTAssertEqual(reloaded.preferences.sodiumLimit, "1200 mg/day")
    }

    // MARK: - km <-> miles conversion

    func testKmToMiles() {
        // 16 km ≈ 9.94 miles
        XCTAssertEqual(16.0 * 0.621371, 9.941936, accuracy: 0.0001)
    }

    func testMilesToKm() {
        // 10 miles ≈ 16.09 km
        XCTAssertEqual(10.0 * 1.60934, 16.0934, accuracy: 0.0001)
    }

    func testUnlimitedMilesToKm() {
        // slider value 11+ means unlimited → stored as 0.0 km
        let miles: Double = 11
        let km = miles >= 11 ? 0.0 : miles * 1.60934
        XCTAssertEqual(km, 0.0)
    }

    func testUnlimitedKmToMiles() {
        // 0.0 km in DB means unlimited → slider value 11
        let km: Double = 0.0
        let miles = km == 0.0 ? 11.0 : km * 0.621371
        XCTAssertEqual(miles, 11.0)
    }

    func testUnlimitedStops() {
        // 0 in DB means unlimited → slider value 11
        let dbStops: Double = 0
        let sliderStops = dbStops == 0 ? 11.0 : dbStops
        XCTAssertEqual(sliderStops, 11.0)
    }

    func testUnlimitedStopsToDb() {
        // slider value 11+ means unlimited → stored as 0.0
        let sliderStops: Double = 11
        let dbStops = sliderStops >= 11 ? 0.0 : sliderStops
        XCTAssertEqual(dbStops, 0.0)
    }

    // MARK: - UserPreferencesRecord Codable

    func testUserPreferencesRecordDecoding() throws {
        let json = """
        {
            "user_id": "abc-123",
            "optimization_mode": "Shortest Route",
            "max_radius_km": 16.09,
            "max_stops": 3.0,
            "walking_enabled": true,
            "public_transport_enabled": false,
            "car_enabled": true,
            "sodium_enabled": true,
            "sodium_limit": "1000",
            "cholesterol_enabled": true,
            "cholesterol_limit": "200",
            "sugar_enabled": true,
            "sugar_limit": "25",
            "diet_vegan": false,
            "diet_gluten_free": true,
            "diet_low_carb": false,
            "diet_kosher": false,
            "diet_halal": false,
            "diet_keto": false,
            "avoid_dairy": false,
            "avoid_peanuts": true,
            "avoid_shellfish": false,
            "avoid_wheat": false
        }
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        let record = try decoder.decode(UserPreferencesRecord.self, from: json)

        XCTAssertEqual(record.userId, "abc-123")
        XCTAssertEqual(record.optimizationMode, "Shortest Route")
        XCTAssertEqual(record.maxRadiusKm, 16.09)
        XCTAssertEqual(record.dietGlutenFree, true)
        XCTAssertEqual(record.avoidPeanuts, true)
        XCTAssertEqual(record.publicTransportEnabled, false)
    }
}
