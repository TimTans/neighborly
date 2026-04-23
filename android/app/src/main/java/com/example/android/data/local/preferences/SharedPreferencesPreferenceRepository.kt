package com.example.android.data.local.preferences

import android.content.Context
import com.example.android.data.repository.preferences.OptimizationPriority
import com.example.android.data.repository.preferences.PreferenceRepository
import com.example.android.data.repository.preferences.PreferenceState
import com.example.android.data.repository.preferences.TransportMode

class SharedPreferencesPreferenceRepository(context: Context) : PreferenceRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        "neighborly_preferences",
        Context.MODE_PRIVATE
    )

    override fun loadPreferences(): PreferenceState {
        val defaults = PreferenceState()
        return PreferenceState(
            priority = enumValueOrDefault(
                preferences.getString(KEY_PRIORITY, null),
                defaults.priority
            ),
            enabledModes = preferences.getStringSet(KEY_ENABLED_MODES, null)
                ?.mapNotNull { enumValueOrNull<TransportMode>(it) }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: defaults.enabledModes,
            maxTravelDistanceMiles = preferences.getFloat(
                KEY_MAX_TRAVEL_DISTANCE_MILES,
                defaults.maxTravelDistanceMiles
            ),
            maxStops = preferences.getFloat(KEY_MAX_STOPS, defaults.maxStops),
            wellnessEnabled = preferences.getBoolean(KEY_WELLNESS_ENABLED, defaults.wellnessEnabled),
            cholesterolLimit = preferences.getString(KEY_CHOLESTEROL_LIMIT, defaults.cholesterolLimit)
                ?: defaults.cholesterolLimit,
            sodiumLimit = preferences.getString(KEY_SODIUM_LIMIT, defaults.sodiumLimit)
                ?: defaults.sodiumLimit,
            sugarLimit = preferences.getString(KEY_SUGAR_LIMIT, defaults.sugarLimit)
                ?: defaults.sugarLimit,
            dietVegan = preferences.getBoolean(KEY_DIET_VEGAN, defaults.dietVegan),
            dietGlutenFree = preferences.getBoolean(KEY_DIET_GLUTEN_FREE, defaults.dietGlutenFree),
            dietLowCarb = preferences.getBoolean(KEY_DIET_LOW_CARB, defaults.dietLowCarb),
            dietKosher = preferences.getBoolean(KEY_DIET_KOSHER, defaults.dietKosher),
            dietHalal = preferences.getBoolean(KEY_DIET_HALAL, defaults.dietHalal),
            dietKeto = preferences.getBoolean(KEY_DIET_KETO, defaults.dietKeto),
            avoidDairy = preferences.getBoolean(KEY_AVOID_DAIRY, defaults.avoidDairy),
            avoidPeanuts = preferences.getBoolean(KEY_AVOID_PEANUTS, defaults.avoidPeanuts),
            avoidShellfish = preferences.getBoolean(KEY_AVOID_SHELLFISH, defaults.avoidShellfish),
            avoidWheat = preferences.getBoolean(KEY_AVOID_WHEAT, defaults.avoidWheat)
        )
    }

    override fun savePreferences(preferences: PreferenceState) {
        this.preferences.edit()
            .putString(KEY_PRIORITY, preferences.priority.name)
            .putStringSet(KEY_ENABLED_MODES, preferences.enabledModes.map { it.name }.toSet())
            .putFloat(KEY_MAX_TRAVEL_DISTANCE_MILES, preferences.maxTravelDistanceMiles)
            .putFloat(KEY_MAX_STOPS, preferences.maxStops)
            .putBoolean(KEY_WELLNESS_ENABLED, preferences.wellnessEnabled)
            .putString(KEY_CHOLESTEROL_LIMIT, preferences.cholesterolLimit)
            .putString(KEY_SODIUM_LIMIT, preferences.sodiumLimit)
            .putString(KEY_SUGAR_LIMIT, preferences.sugarLimit)
            .putBoolean(KEY_DIET_VEGAN, preferences.dietVegan)
            .putBoolean(KEY_DIET_GLUTEN_FREE, preferences.dietGlutenFree)
            .putBoolean(KEY_DIET_LOW_CARB, preferences.dietLowCarb)
            .putBoolean(KEY_DIET_KOSHER, preferences.dietKosher)
            .putBoolean(KEY_DIET_HALAL, preferences.dietHalal)
            .putBoolean(KEY_DIET_KETO, preferences.dietKeto)
            .putBoolean(KEY_AVOID_DAIRY, preferences.avoidDairy)
            .putBoolean(KEY_AVOID_PEANUTS, preferences.avoidPeanuts)
            .putBoolean(KEY_AVOID_SHELLFISH, preferences.avoidShellfish)
            .putBoolean(KEY_AVOID_WHEAT, preferences.avoidWheat)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return enumValueOrNull<T>(value) ?: default
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? {
        return value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
    }

    private companion object {
        const val KEY_PRIORITY = "priority"
        const val KEY_ENABLED_MODES = "enabled_modes"
        const val KEY_MAX_TRAVEL_DISTANCE_MILES = "max_travel_distance_miles"
        const val KEY_MAX_STOPS = "max_stops"
        const val KEY_WELLNESS_ENABLED = "wellness_enabled"
        const val KEY_CHOLESTEROL_LIMIT = "cholesterol_limit"
        const val KEY_SODIUM_LIMIT = "sodium_limit"
        const val KEY_SUGAR_LIMIT = "sugar_limit"
        const val KEY_DIET_VEGAN = "diet_vegan"
        const val KEY_DIET_GLUTEN_FREE = "diet_gluten_free"
        const val KEY_DIET_LOW_CARB = "diet_low_carb"
        const val KEY_DIET_KOSHER = "diet_kosher"
        const val KEY_DIET_HALAL = "diet_halal"
        const val KEY_DIET_KETO = "diet_keto"
        const val KEY_AVOID_DAIRY = "avoid_dairy"
        const val KEY_AVOID_PEANUTS = "avoid_peanuts"
        const val KEY_AVOID_SHELLFISH = "avoid_shellfish"
        const val KEY_AVOID_WHEAT = "avoid_wheat"
    }
}
