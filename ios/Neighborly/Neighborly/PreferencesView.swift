import SwiftUI

// Added to fix some issue with destination in HomeView
struct PreferencesView: View {
    var body: some View {
        PreferencesOneScrollView()
    }
}

#Preview {
    PreferencesView()
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

enum Priority: String, CaseIterable, Identifiable, Codable {
    case lowestCost = "Lowest Cost"
    case shortestRoute = "Shortest Route"
    case fastestTrip = "Fastest Trip"
    var id: String { rawValue }
}

struct Preferences: Equatable, Codable {
    var priority: Priority = .lowestCost

    var enabledModes: Set<TransportMode> = [.walking, .publicTransport, .car]

    var maxTravelDistanceMiles: Double = 5
    var maxStops: Double = 5

    var wellnessEnabled: Bool = true

    var cholesterolLimit: String = ""
    var sodiumLimit: String = ""
    var sugarLimit: String = ""

    var dietVegan: Bool = false
    var dietGlutenFree: Bool = true
    var dietLowCarb: Bool = false
    var dietKosher: Bool = false
    var dietHalal: Bool = false
    var dietKeto: Bool = false

    var avoidDairy: Bool = false
    var avoidPeanuts: Bool = true
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
    @Environment(PreferencesStore.self) private var preferencesStore
    @Environment(\.dismiss) private var dismiss
    @State private var prefs = Preferences()
    @State private var hasLoadedPreferences = false

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

                        // Sliders (simple placeholder; plug your slider UI back in)
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Max Travel Distance").foregroundStyle(.secondary)
                            Slider(value: $prefs.maxTravelDistanceMiles, in: 1...10, step: 1)

                            Text("Max Number of Stops").foregroundStyle(.secondary)
                            Slider(value: $prefs.maxStops, in: 1...10, step: 1)
                        }
                        .padding(14)
                        .background(cardBackground)

                        // Wellness Section (collapses/expands based on scroll)
                        WellnessSection(
                            prefs: $prefs,
                            expanded: $wellnessExpanded
                        )
                        .padding(.top, 2)

                        PrimaryButton(title: "Save Preferences") {
                            preferencesStore.save(prefs)
                            dismiss()
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
                .onAppear {
                    guard !hasLoadedPreferences else { return }
                    prefs = preferencesStore.preferences
                    hasLoadedPreferences = true
                }

            }
            .navigationTitle("Your Preferences")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Circle()
                        .fill(NeighborlyTheme.surfaceSecondary)
                        .frame(width: 34, height: 34)
                }
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
                        Image(systemName: expanded ? "chevron.up" : "chevron.down")
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

                if expanded {
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
