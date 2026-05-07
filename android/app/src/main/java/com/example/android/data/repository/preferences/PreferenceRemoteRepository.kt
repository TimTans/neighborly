package com.example.android.data.repository.preferences

import com.example.android.data.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format mirror of the iOS `UserPreferencesRecord`
 * (`ios/Neighborly/Neighborly/PreferencesService.swift:6-60`). Column names
 * are the canonical contract — the iOS app shipped first. All optional fields
 * are nullable so that rows partially populated by either client decode
 * cleanly.
 *
 * The wellness *_enabled booleans are intentionally redundant: iOS treats them
 * as per-field flags but Android collapses them into a single
 * [PreferenceState.wellnessEnabled] toggle. On save we set all three to the
 * Android collapsed value; on fetch we OR them. See [SupabasePreferenceRemoteRepository].
 */
@Serializable
internal data class UserPreferencesRow(
    @SerialName("user_id") val userId: String,
    @SerialName("optimization_mode") val optimizationMode: String? = null,
    @SerialName("max_radius_km") val maxRadiusKm: Double? = null,
    @SerialName("max_stops") val maxStops: Double? = null,
    @SerialName("walking_enabled") val walkingEnabled: Boolean? = null,
    @SerialName("public_transport_enabled") val publicTransportEnabled: Boolean? = null,
    @SerialName("car_enabled") val carEnabled: Boolean? = null,
    @SerialName("sodium_enabled") val sodiumEnabled: Boolean? = null,
    @SerialName("sodium_limit") val sodiumLimit: String? = null,
    @SerialName("cholesterol_enabled") val cholesterolEnabled: Boolean? = null,
    @SerialName("cholesterol_limit") val cholesterolLimit: String? = null,
    @SerialName("sugar_enabled") val sugarEnabled: Boolean? = null,
    @SerialName("sugar_limit") val sugarLimit: String? = null,
    @SerialName("diet_vegan") val dietVegan: Boolean? = null,
    @SerialName("diet_gluten_free") val dietGlutenFree: Boolean? = null,
    @SerialName("diet_low_carb") val dietLowCarb: Boolean? = null,
    @SerialName("diet_kosher") val dietKosher: Boolean? = null,
    @SerialName("diet_halal") val dietHalal: Boolean? = null,
    @SerialName("diet_keto") val dietKeto: Boolean? = null,
    @SerialName("avoid_dairy") val avoidDairy: Boolean? = null,
    @SerialName("avoid_peanuts") val avoidPeanuts: Boolean? = null,
    @SerialName("avoid_shellfish") val avoidShellfish: Boolean? = null,
    @SerialName("avoid_wheat") val avoidWheat: Boolean? = null,
)

interface PreferenceRemoteRepository {
    /** Returns the user's stored preferences, or `null` if no row exists yet. */
    suspend fun fetch(userId: String): Result<PreferenceState?>

    /** Upserts the user's preferences on `user_id` conflict. */
    suspend fun save(prefs: PreferenceState, userId: String): Result<Unit>
}

/**
 * Supabase-backed implementation. Mirrors iOS PreferencesService.swift
 * (fetch, save). Conversion helpers in this file are the cross-platform seam:
 * change either side and the round trip will silently corrupt.
 */
class SupabasePreferenceRemoteRepository(
    /**
     * Indirection so unit tests don't need a live Supabase client. In
     * production this is the singleton `data.supabase`.
     */
    private val tableProvider: () -> io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder = {
        supabase.from("user_preferences")
    }
) : PreferenceRemoteRepository {

    override suspend fun fetch(userId: String): Result<PreferenceState?> = runCatching {
        val rows = tableProvider()
            .select {
                filter { eq("user_id", userId) }
                limit(1)
            }
            .decodeList<UserPreferencesRow>()
        rows.firstOrNull()?.toPreferenceState()
    }

    override suspend fun save(prefs: PreferenceState, userId: String): Result<Unit> =
        runCatching {
            tableProvider().upsert(
                value = prefs.toRow(userId),
                onConflict = "user_id"
            )
            Unit
        }
}

// region Mapping

/**
 * iOS-compatible km↔miles conversion. iOS uses `0.621371` exactly
 * (PreferencesService.swift:87) — keep the same constant so a round-trip on
 * either platform produces identical floats.
 */
private const val KM_PER_MILE = 1.60934
private const val MILES_PER_KM = 0.621371

/**
 * Slider sentinel meaning "unlimited". Both iOS and Android encode this as
 * `0.0` km / `0.0` stops on the wire.
 */
internal const val UNLIMITED_SENTINEL_SLIDER_VALUE = 11f

internal fun UserPreferencesRow.toPreferenceState(): PreferenceState {
    val defaults = PreferenceState()

    val priority = OptimizationPriority.fromLabel(optimizationMode)

    val miles: Float = when (val km = maxRadiusKm) {
        null -> defaults.maxTravelDistanceMiles
        0.0 -> UNLIMITED_SENTINEL_SLIDER_VALUE
        else -> (km * MILES_PER_KM).toFloat()
    }

    val stops: Float = when (val s = maxStops) {
        null -> defaults.maxStops
        0.0 -> UNLIMITED_SENTINEL_SLIDER_VALUE
        else -> s.toFloat()
    }

    // iOS PreferencesService.swift:94-98: missing flag = enabled (true) by
    // default. Mirrors that fallback so a row written by either client
    // decodes the same on the other.
    val modes = buildSet {
        if (walkingEnabled ?: true) add(TransportMode.Walking)
        if (publicTransportEnabled ?: true) add(TransportMode.PublicTransit)
        if (carEnabled ?: true) add(TransportMode.Car)
    }

    // Wellness toggle: iOS treats the three *_enabled flags as per-field but
    // Android collapses them into a single switch. Match iOS fetch behavior
    // (PreferencesService.swift:101-108): if *any* flag was stored, take the
    // OR; otherwise fall back to the struct default `true`.
    val anyWellnessStored = sodiumEnabled != null
        || cholesterolEnabled != null
        || sugarEnabled != null
    val wellnessEnabled = if (anyWellnessStored) {
        (sodiumEnabled ?: false) ||
            (cholesterolEnabled ?: false) ||
            (sugarEnabled ?: false)
    } else {
        defaults.wellnessEnabled
    }

    return PreferenceState(
        priority = priority,
        enabledModes = modes,
        maxTravelDistanceMiles = miles,
        maxStops = stops,
        wellnessEnabled = wellnessEnabled,
        cholesterolLimit = cholesterolLimit ?: "",
        sodiumLimit = sodiumLimit ?: "",
        sugarLimit = sugarLimit ?: "",
        // iOS PreferencesService.swift:114-124: dietGlutenFree and
        // avoidPeanuts default to `true` when missing; everything else
        // defaults to `false`. Match exactly.
        dietVegan = dietVegan ?: false,
        dietGlutenFree = dietGlutenFree ?: true,
        dietLowCarb = dietLowCarb ?: false,
        dietKosher = dietKosher ?: false,
        dietHalal = dietHalal ?: false,
        dietKeto = dietKeto ?: false,
        avoidDairy = avoidDairy ?: false,
        avoidPeanuts = avoidPeanuts ?: true,
        avoidShellfish = avoidShellfish ?: false,
        avoidWheat = avoidWheat ?: false,
    )
}

internal fun PreferenceState.toRow(userId: String): UserPreferencesRow {
    val km: Double = if (maxTravelDistanceMiles >= UNLIMITED_SENTINEL_SLIDER_VALUE) {
        0.0
    } else {
        maxTravelDistanceMiles.toDouble() * KM_PER_MILE
    }
    val stops: Double = if (maxStops >= UNLIMITED_SENTINEL_SLIDER_VALUE) {
        0.0
    } else {
        maxStops.toDouble()
    }

    return UserPreferencesRow(
        userId = userId,
        optimizationMode = priority.label,
        maxRadiusKm = km,
        maxStops = stops,
        walkingEnabled = TransportMode.Walking in enabledModes,
        publicTransportEnabled = TransportMode.PublicTransit in enabledModes,
        carEnabled = TransportMode.Car in enabledModes,
        // Collapse Android's single switch into iOS's three per-field flags
        // (mirrors PreferencesService.swift:148-152). On fetch we OR them.
        sodiumEnabled = wellnessEnabled,
        sodiumLimit = sodiumLimit.takeIf { it.isNotEmpty() },
        cholesterolEnabled = wellnessEnabled,
        cholesterolLimit = cholesterolLimit.takeIf { it.isNotEmpty() },
        sugarEnabled = wellnessEnabled,
        sugarLimit = sugarLimit.takeIf { it.isNotEmpty() },
        dietVegan = dietVegan,
        dietGlutenFree = dietGlutenFree,
        dietLowCarb = dietLowCarb,
        dietKosher = dietKosher,
        dietHalal = dietHalal,
        dietKeto = dietKeto,
        avoidDairy = avoidDairy,
        avoidPeanuts = avoidPeanuts,
        avoidShellfish = avoidShellfish,
        avoidWheat = avoidWheat,
    )
}

// endregion
