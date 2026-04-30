package com.example.android.data.repository.preferences

enum class OptimizationPriority(val label: String) {
    LowestCost("Lowest Cost"),
    ShortestRoute("Shortest Route"),
    FastestTrip("Fastest Trip")
}

enum class TransportMode(val label: String) {
    Walking("Walking"),
    PublicTransit("Public Transit"),
    Car("Driving");

    val emoji: String
        get() = when (this) {
            Walking -> "\uD83D\uDEB6"
            PublicTransit -> "\uD83D\uDE8C"
            Car -> "\uD83D\uDE97"
        }
}

data class PreferenceState(
    val priority: OptimizationPriority = OptimizationPriority.LowestCost,
    val enabledModes: Set<TransportMode> = setOf(
        TransportMode.Walking,
        TransportMode.PublicTransit,
        TransportMode.Car
    ),
    val maxTravelDistanceMiles: Float = 5f,
    val maxStops: Float = 5f,
    val wellnessEnabled: Boolean = true,
    val cholesterolLimit: String = "",
    val sodiumLimit: String = "",
    val sugarLimit: String = "",
    val dietVegan: Boolean = false,
    val dietGlutenFree: Boolean = true,
    val dietLowCarb: Boolean = false,
    val dietKosher: Boolean = false,
    val dietHalal: Boolean = false,
    val dietKeto: Boolean = false,
    val avoidDairy: Boolean = false,
    val avoidPeanuts: Boolean = true,
    val avoidShellfish: Boolean = false,
    val avoidWheat: Boolean = false
)
