import Foundation
import Observation

@Observable
final class PreferencesStore {
    private let defaults: UserDefaults
    private let storageKey = "neighborly.preferences"

    var preferences: Preferences

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults

        guard
            let data = defaults.data(forKey: storageKey),
            let decoded = try? JSONDecoder().decode(Preferences.self, from: data)
        else {
            preferences = Preferences()
            return
        }

        preferences = decoded
    }

    func save(_ updatedPreferences: Preferences) {
        preferences = updatedPreferences
        guard let data = try? JSONEncoder().encode(updatedPreferences) else {
            return
        }
        defaults.set(data, forKey: storageKey)
    }
}
