//
//  NeighborlyTests.swift
//  NeighborlyTests
//
//  Created by Jawad Chowdhury on 2/10/26.
//

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
}
