import SwiftUI
import Auth

// Added to fix some issue with destination in HomeView
struct PreferencesView: View {
    var body: some View {
        PreferencesOneScrollView()
    }
}

#Preview {
    PreferencesView()
}


enum Priority: String, CaseIterable, Identifiable, Codable {
    case lowestCost = "Lowest Cost"
    case shortestRoute = "Shortest Route"
    case fastestTrip = "Fastest Trip"
    var id: String { rawValue }

    // maps to the backend mode parameter
    var backendMode: String {
        switch self {
        case .lowestCost: return "cost"
        case .shortestRoute: return "distance"
        case .fastestTrip: return "stops"
        }
    }
}

enum TransportMode: String, CaseIterable, Identifiable, Codable {
    case walking = "Walking"
    case publicTransport = "Public Transport"
    case car = "Car"

    var id: String { rawValue }

    var systemImage: String {
        switch self {
        case .walking: return "figure.walk"
        case .publicTransport: return "tram.fill"
        case .car: return "car.fill"
        }
    }

    var emoji: String {
        switch self {
        case .walking: return "🚶"
        case .publicTransport: return "🚌"
        case .car: return "🚗"
        }
    }

    var label: String {
        switch self {
        case .walking: return "Walking"
        case .publicTransport: return "Public Transport"
        case .car: return "Driving"
        }
    }
}

struct Preferences: Equatable, Codable {
    var priority: Priority = .lowestCost

    var enabledModes: Set<TransportMode> = [.walking, .publicTransport, .car]

    var maxTravelDistanceMiles: Double = 10
    var maxStops: Double = 5

    var wellnessEnabled: Bool = false

    var cholesterolLimit: String = ""
    var sodiumLimit: String = ""
    var sugarLimit: String = ""

    var dietVegan: Bool = false
    var dietGlutenFree: Bool = false
    var dietLowCarb: Bool = false
    var dietKosher: Bool = false
    var dietHalal: Bool = false
    var dietKeto: Bool = false

    var avoidDairy: Bool = false
    var avoidPeanuts: Bool = false
    var avoidShellfish: Bool = false
    var avoidWheat: Bool = false
}

// MARK: - Scroll Offset PreferenceKey

private struct ScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

// MARK: - Reusable UI

struct ToggleRow: View {
    let title: String
    @Binding var isOn: Bool

    var body: some View {
        HStack {
            Text(title).foregroundStyle(.secondary)
            Spacer()
            Toggle("", isOn: $isOn).labelsHidden()
        }
    }
}

struct LabeledTextField: View {
    let title: String
    let placeholder: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).foregroundStyle(.secondary)

            TextField(placeholder, text: $text)
                .foregroundStyle(NeighborlyTheme.textPrimary)
                .tint(NeighborlyTheme.green)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .padding(14)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(NeighborlyTheme.fieldBackground)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(NeighborlyTheme.border.opacity(0.6), lineWidth: 1)
                )
        }
    }
}

struct CheckRow: View {
    let title: String
    @Binding var checked: Bool

    var body: some View {
        Button {
            checked.toggle()
        } label: {
            HStack(spacing: 10) {
                Image(systemName: checked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(checked ? Color.blue : Color.secondary)
                Text(title).foregroundStyle(.secondary)
                Spacer()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct PrimaryButton: View {
    let title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.headline)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .fill(NeighborlyTheme.green)
                )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Single Screen

struct PreferencesOneScrollView: View {
    @Environment(AuthController.self) private var authController
    @AppStorage("optimizationMode")           private var savedPriority: String = Priority.lowestCost.rawValue
    @AppStorage("maxStops")                   private var savedMaxStops: Int    = 5
    @AppStorage("maxRadiusMiles")             private var savedMaxRadiusMiles: Double = 10
    @AppStorage("walkingEnabled")             private var savedWalkingEnabled: Bool = true
    @AppStorage("publicTransportEnabled")     private var savedPublicTransportEnabled: Bool = true
    @AppStorage("carEnabled")                 private var savedCarEnabled: Bool = true
    @AppStorage("wellnessEnabled")            private var savedWellnessEnabled: Bool = false
    @AppStorage("cholesterolLimit")           private var savedCholesterolLimit: String = ""
    @AppStorage("sodiumLimit")                private var savedSodiumLimit: String = ""
    @AppStorage("sugarLimit")                 private var savedSugarLimit: String = ""
    @AppStorage("dietVegan")                  private var savedDietVegan: Bool = false
    @AppStorage("dietGlutenFree")             private var savedDietGlutenFree: Bool = false
    @AppStorage("dietLowCarb")                private var savedDietLowCarb: Bool = false
    @AppStorage("dietKosher")                 private var savedDietKosher: Bool = false
    @AppStorage("dietHalal")                  private var savedDietHalal: Bool = false
    @AppStorage("dietKeto")                   private var savedDietKeto: Bool = false
    @AppStorage("avoidDairy")                 private var savedAvoidDairy: Bool = false
    @AppStorage("avoidPeanuts")               private var savedAvoidPeanuts: Bool = false
    @AppStorage("avoidShellfish")             private var savedAvoidShellfish: Bool = false
    @AppStorage("avoidWheat")                 private var savedAvoidWheat: Bool = false
    @State private var prefs = Preferences()
    @State private var didSave = false

    // When true, show the expanded wellness fields (your second screen content)
    @State private var wellnessExpanded: Bool = false

    // Controls how far user must scroll before expanding wellness
    private let expandThreshold: CGFloat = -120

    var body: some View {
        NavigationStack {
            ZStack {
                NeighborlyTheme.background.ignoresSafeArea()

                ScrollView {
                    // Track scroll offset
                    GeometryReader { geo in
                        Color.clear
                            .preference(key: ScrollOffsetKey.self,
                                        value: geo.frame(in: .named("scroll")).minY)
                    }
                    .frame(height: 0)

                    VStack(spacing: 16) {

                        // Priority
                        HStack {
                            Text("Prioritize").foregroundStyle(.secondary)
                            Spacer()
                            Picker("Prioritize", selection: $prefs.priority) {
                                ForEach(Priority.allCases) { p in
                                    Text(p.rawValue).tag(p)
                                }
                            }
                            .pickerStyle(.menu)
                        }
                        .padding(14)
                        .background(cardBackground)

                        // Modes of Transport
                        TransportModeSelector(enabledModes: $prefs.enabledModes)
                            .padding(14)
                            .background(cardBackground)

                        // Sliders
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Text("Max Travel Distance").foregroundStyle(.secondary)
                                Spacer()
                                Text(prefs.maxTravelDistanceMiles >= 11 ? "Unlimited" : "\(Int(prefs.maxTravelDistanceMiles)) mi")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(prefs.maxTravelDistanceMiles >= 11 ? Color.cyan : Color.secondary)
                            }
                            Slider(value: $prefs.maxTravelDistanceMiles, in: 1...11, step: 1)

                            HStack {
                                Text("Max Number of Stops").foregroundStyle(.secondary)
                                Spacer()
                                Text(prefs.maxStops >= 11 ? "Unlimited" : "\(Int(prefs.maxStops)) stop\(Int(prefs.maxStops) == 1 ? "" : "s")")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(prefs.maxStops >= 11 ? Color.cyan : Color.secondary)
                            }
                            Slider(value: $prefs.maxStops, in: 1...11, step: 1)
                        }
                        .padding(14)
                        .background(cardBackground)

                        // Wellness Section (collapses/expands based on scroll)
                        WellnessSection(
                            prefs: $prefs,
                            expanded: $wellnessExpanded
                        )
                        .padding(.top, 2)

                        PrimaryButton(title: didSave ? "Saved ✓" : "Save Preferences") {
                            savedPriority               = prefs.priority.rawValue
                            savedMaxStops               = prefs.maxStops >= 11 ? 0 : Int(prefs.maxStops)
                            savedMaxRadiusMiles         = prefs.maxTravelDistanceMiles >= 11 ? 0.0 : prefs.maxTravelDistanceMiles
                            savedWalkingEnabled         = prefs.enabledModes.contains(.walking)
                            savedPublicTransportEnabled = prefs.enabledModes.contains(.publicTransport)
                            savedCarEnabled             = prefs.enabledModes.contains(.car)
                            savedWellnessEnabled        = prefs.wellnessEnabled
                            savedCholesterolLimit       = prefs.cholesterolLimit
                            savedSodiumLimit            = prefs.sodiumLimit
                            savedSugarLimit             = prefs.sugarLimit
                            savedDietVegan              = prefs.dietVegan
                            savedDietGlutenFree         = prefs.dietGlutenFree
                            savedDietLowCarb            = prefs.dietLowCarb
                            savedDietKosher             = prefs.dietKosher
                            savedDietHalal              = prefs.dietHalal
                            savedDietKeto               = prefs.dietKeto
                            savedAvoidDairy             = prefs.avoidDairy
                            savedAvoidPeanuts           = prefs.avoidPeanuts
                            savedAvoidShellfish         = prefs.avoidShellfish
                            savedAvoidWheat             = prefs.avoidWheat
                            didSave = true
                            Task {
                                try? await Task.sleep(for: .seconds(1.5))
                                didSave = false
                            }
                            if let userId = authController.currentUser?.id.uuidString {
                                Task { try? await PreferencesService.save(prefs, userId: userId) }
                            }
                        }
                        .padding(.top, 8)

                        Spacer(minLength: 90)
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 12)
                }
                .coordinateSpace(name: "scroll")
                .onPreferenceChange(ScrollOffsetKey.self) { offset in
                    // If user has scrolled down enough, expand wellness automatically
                    if offset < expandThreshold, wellnessExpanded == false {
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) {
                            wellnessExpanded = true
                        }
                    }
                }

            }
            .navigationTitle("Your Preferences")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if let saved = Priority(rawValue: savedPriority) {
                    prefs.priority = saved
                }
                // 0 is the unlimited sentinel (slider shows 11); contract maintained by save button and DB sync
                prefs.maxStops               = savedMaxStops == 0 ? 11.0 : Double(savedMaxStops)
                prefs.maxTravelDistanceMiles = savedMaxRadiusMiles == 0.0 ? 11.0 : savedMaxRadiusMiles

                var modes: Set<TransportMode> = []
                if savedWalkingEnabled           { modes.insert(.walking) }
                if savedPublicTransportEnabled   { modes.insert(.publicTransport) }
                if savedCarEnabled               { modes.insert(.car) }
                prefs.enabledModes = modes

                prefs.wellnessEnabled    = savedWellnessEnabled
                prefs.cholesterolLimit   = savedCholesterolLimit
                prefs.sodiumLimit        = savedSodiumLimit
                prefs.sugarLimit         = savedSugarLimit
                prefs.dietVegan          = savedDietVegan
                prefs.dietGlutenFree     = savedDietGlutenFree
                prefs.dietLowCarb        = savedDietLowCarb
                prefs.dietKosher         = savedDietKosher
                prefs.dietHalal          = savedDietHalal
                prefs.dietKeto           = savedDietKeto
                prefs.avoidDairy         = savedAvoidDairy
                prefs.avoidPeanuts       = savedAvoidPeanuts
                prefs.avoidShellfish     = savedAvoidShellfish
                prefs.avoidWheat         = savedAvoidWheat
                // try to fetch from DB; remote wins if online
                Task {
                    guard authController.currentUser != nil else { return }
                    guard let fetched = try? await PreferencesService.fetch() else { return }
                    prefs = fetched
                    // keep AppStorage in sync with what came from DB
                    savedPriority               = fetched.priority.rawValue
                    savedMaxStops               = fetched.maxStops >= 11 ? 0 : Int(fetched.maxStops)
                    savedMaxRadiusMiles         = fetched.maxTravelDistanceMiles >= 11 ? 0.0 : fetched.maxTravelDistanceMiles
                    savedWalkingEnabled         = fetched.enabledModes.contains(.walking)
                    savedPublicTransportEnabled = fetched.enabledModes.contains(.publicTransport)
                    savedCarEnabled             = fetched.enabledModes.contains(.car)
                    savedWellnessEnabled        = fetched.wellnessEnabled
                    savedCholesterolLimit       = fetched.cholesterolLimit
                    savedSodiumLimit            = fetched.sodiumLimit
                    savedSugarLimit             = fetched.sugarLimit
                    savedDietVegan              = fetched.dietVegan
                    savedDietGlutenFree         = fetched.dietGlutenFree
                    savedDietLowCarb            = fetched.dietLowCarb
                    savedDietKosher             = fetched.dietKosher
                    savedDietHalal              = fetched.dietHalal
                    savedDietKeto               = fetched.dietKeto
                    savedAvoidDairy             = fetched.avoidDairy
                    savedAvoidPeanuts           = fetched.avoidPeanuts
                    savedAvoidShellfish         = fetched.avoidShellfish
                    savedAvoidWheat             = fetched.avoidWheat
                }
            }
            .onChange(of: prefs.priority) { _, newValue in
                savedPriority = newValue.rawValue
            }
        }
    }

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 22, style: .continuous)
            .fill(NeighborlyTheme.surface)
            .shadow(color: .black.opacity(0.03), radius: 10, x: 0, y: 5)
    }
}

// MARK: - Transport Mode Selector

struct TransportModeSelector: View {
    @Binding var enabledModes: Set<TransportMode>

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Modes of Transport").foregroundStyle(.secondary)

            HStack(spacing: 10) {
                ForEach(TransportMode.allCases) { mode in
                    let isSelected = enabledModes.contains(mode)
                    Button {
                        if isSelected {
                            enabledModes.remove(mode)
                        } else {
                            enabledModes.insert(mode)
                        }
                    } label: {
                        VStack(spacing: 6) {
                            Text(mode.emoji)
                                .font(.system(size: 28))
                            Text(mode.label)
                                .font(.caption)
                                .fontWeight(.medium)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .fill(isSelected ? NeighborlyTheme.greenSoft : NeighborlyTheme.surfaceSecondary)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(isSelected ? NeighborlyTheme.green : NeighborlyTheme.border.opacity(0.45), lineWidth: 1.5)
                        )
                        .foregroundStyle(isSelected ? NeighborlyTheme.green : NeighborlyTheme.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

// MARK: - Wellness Section (Collapsed -> Expanded)

struct WellnessSection: View {
    @Binding var prefs: Preferences
    @Binding var expanded: Bool

    var body: some View {
        VStack(spacing: 14) {
            // Header row — toggle controls enabled, chevron controls expand
            HStack {
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) {
                        expanded.toggle()
                    }
                } label: {
                    HStack {
                        Text("Wellness/Dietary Preferences")
                            .foregroundStyle(.secondary)
                        Spacer()
                        Image(systemName: prefs.wellnessEnabled ? "chevron.down" : "chevron.up")
                            .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Toggle("", isOn: $prefs.wellnessEnabled)
                    .labelsHidden()
                    .padding(.leading, 6)
            }

            // All wellness fields hidden when toggle is off
            if prefs.wellnessEnabled {
                LabeledTextField(
                    title: "Cholesterol Limit",
                    placeholder: "100 mg",
                    text: $prefs.cholesterolLimit
                )

                LabeledTextField(
                    title: "Sodium Limit",
                    placeholder: "1000 mg/Day",
                    text: $prefs.sodiumLimit
                )

                LabeledTextField(
                    title: "Sugar Limit",
                    placeholder: "20 g/Day",
                    text: $prefs.sugarLimit
                )

                Divider().opacity(0.15)

                VStack(alignment: .leading, spacing: 14) {
                    Text("Diet Type").foregroundStyle(.secondary)

                    HStack(alignment: .top, spacing: 24) {
                        VStack(alignment: .leading, spacing: 12) {
                            CheckRow(title: "Vegan", checked: $prefs.dietVegan)
                            CheckRow(title: "Gluten-Free", checked: $prefs.dietGlutenFree)
                            CheckRow(title: "Low Carb", checked: $prefs.dietLowCarb)
                        }
                        VStack(alignment: .leading, spacing: 12) {
                            CheckRow(title: "Kosher", checked: $prefs.dietKosher)
                            CheckRow(title: "Halal", checked: $prefs.dietHalal)
                            CheckRow(title: "Keto", checked: $prefs.dietKeto)
                        }
                    }
                }
                .padding(.top, 6)

                VStack(alignment: .leading, spacing: 14) {
                    Text("Allergens to Avoid").foregroundStyle(.secondary)
                    VStack(alignment: .leading, spacing: 12) {
                        CheckRow(title: "Dairy", checked: $prefs.avoidDairy)
                        CheckRow(title: "Peanuts", checked: $prefs.avoidPeanuts)
                        CheckRow(title: "Shellfish", checked: $prefs.avoidShellfish)
                        CheckRow(title: "Wheat", checked: $prefs.avoidWheat)
                    }
                }
                .padding(.top, 6)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(NeighborlyTheme.surface)
                .shadow(color: .black.opacity(0.03), radius: 10, x: 0, y: 5)
        )
    }
}

// MARK: - Preview

#Preview {
    PreferencesOneScrollView()
}
