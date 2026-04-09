import SwiftUI
import Auth

struct HomeView: View {
    @Environment(AuthController.self) private var authController
    @State private var viewModel = HomeViewModel()
    @State private var showPreferences = false

    private var displayName: String {
        let user = authController.currentUser
        let first = user?.userMetadata["first_name"]?.stringValue ?? ""
        let last = user?.userMetadata["last_name"]?.stringValue ?? ""
        let full = [first, last].filter { !$0.isEmpty }.joined(separator: " ")
        return full.isEmpty ? (user?.email ?? "User") : full
    }

    private var initials: String {
        let user = authController.currentUser
        let first = user?.userMetadata["first_name"]?.stringValue ?? ""
        let last = user?.userMetadata["last_name"]?.stringValue ?? ""
        let f = first.first.map(String.init) ?? ""
        let l = last.first.map(String.init) ?? ""
        let result = (f + l).uppercased()
        return result.isEmpty ? "?" : result
    }

    var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 18) {
                    homeTopBar
                    heroCard
                    metricsGrid
                    optimizedRouteCard
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 24)
            }
            .background(NeighborlyTheme.background.ignoresSafeArea())
            .navigationDestination(isPresented: $showPreferences) {
                PreferencesView()
            }
        }
    }

    // MARK: - Top Bar

    private var homeTopBar: some View {
        HStack(spacing: 12) {
            // Gradient logo
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(
                        LinearGradient(
                            colors: [
                                NeighborlyTheme.green,
                                Color(red: 0.33, green: 0.73, blue: 0.53)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 44, height: 44)

                Text("N")
                    .font(.system(size: 22, weight: .bold, design: .serif))
                    .foregroundStyle(.white)
            }

            Text("Neighborly")
                .font(.system(size: 24, weight: .heavy, design: .serif))
                .foregroundStyle(NeighborlyTheme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            Spacer()

            // Notification bell
            ZStack(alignment: .topTrailing) {
                RoundedRectangle(cornerRadius: 14)
                    .fill(Color.white.opacity(0.65))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "bell")
                            .font(.system(size: 20))
                            .foregroundStyle(NeighborlyTheme.textSecondary)
                    )

                Circle()
                    .fill(.red)
                    .frame(width: 8, height: 8)
                    .offset(x: 3, y: -3)
            }

            // Avatar with sign-out menu
            Menu {
                Text(displayName)
                Divider()
                Button {
                    showPreferences = true
                } label: {
                    Label("Preferences", systemImage: "person.crop.circle")
                }
                Divider()
                Button(role: .destructive) {
                    Task { await authController.signOut() }
                } label: {
                    Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                }
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(
                            LinearGradient(
                                colors: [NeighborlyTheme.orange, NeighborlyTheme.orange.opacity(0.8)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: 44, height: 44)

                    Text(initials)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(.white)
                }
            }
        }
    }

    // MARK: - Hero Card

    private var heroCard: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 30)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.10, green: 0.33, blue: 0.24),
                            Color(red: 0.25, green: 0.64, blue: 0.45)
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            // Decorative background circles
            Circle()
                .fill(Color.white.opacity(0.05))
                .frame(width: 170, height: 170)
                .offset(x: 100, y: -45)

            Circle()
                .fill(Color.white.opacity(0.04))
                .frame(width: 140, height: 140)
                .offset(x: 60, y: 110)

            VStack(alignment: .leading, spacing: 14) {
                Text("Good morning")
                    .font(.system(size: 18))
                    .foregroundStyle(.white.opacity(0.8))

                Text(displayName)
                    .font(.system(size: 28, weight: .heavy, design: .serif))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                // Rich text with bold highlights
                (
                    Text("Your optimized route is ready — ")
                        .foregroundStyle(.white.opacity(0.82))
                    + Text("3 stores")
                        .foregroundStyle(Color(red: 0.73, green: 0.88, blue: 0.67))
                        .fontWeight(.bold)
                    + Text(", 12 items, saving ")
                        .foregroundStyle(.white.opacity(0.82))
                    + Text("$\(viewModel.savingsThisTrip)")
                        .foregroundStyle(Color(red: 0.73, green: 0.88, blue: 0.67))
                        .fontWeight(.bold)
                )
                .font(.system(size: 15))
                .fixedSize(horizontal: false, vertical: true)

                Button {} label: {
                    HStack {
                        Spacer()
                        Text("Start Trip")
                            .font(.system(size: 18, weight: .bold))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 18, weight: .semibold))
                        Spacer()
                    }
                    .foregroundStyle(Color(red: 0.17, green: 0.42, blue: 0.30))
                    .padding(.vertical, 16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 22))
                }
                .padding(.top, 4)
            }
            .padding(22)
        }
    }

    // MARK: - Metrics Grid

    private var metricsGrid: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                budgetMetricCard
                savedMetricCard
            }

            HStack(spacing: 12) {
                smallMetricCard(
                    value: viewModel.avgTripTime,
                    label: "avg trip",
                    icon: "clock",
                    tint: NeighborlyTheme.textSecondary
                )
                smallMetricCard(
                    value: viewModel.milesSaved,
                    label: "miles saved",
                    icon: "mappin.and.ellipse",
                    tint: NeighborlyTheme.textSecondary
                )
            }

            HStack(spacing: 12) {
                smallMetricCard(
                    value: viewModel.itemsTracked,
                    label: "tracked",
                    icon: "shippingbox",
                    tint: NeighborlyTheme.textSecondary
                )
                smallMetricCard(
                    value: viewModel.alertsCount,
                    label: "alerts",
                    icon: "bell",
                    tint: NeighborlyTheme.textSecondary
                )
            }
        }
    }

    private var budgetMetricCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(NeighborlyTheme.greenSoft)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "dollarsign.circle.fill")
                            .font(.system(size: 18))
                            .foregroundColor(NeighborlyTheme.green)
                    )

                Text("BUDGET")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(NeighborlyTheme.textMuted)
            }

            Text(viewModel.budgetUsed)
                .font(.system(size: 24, weight: .heavy, design: .serif))
                .foregroundColor(NeighborlyTheme.green)

            Text("of \(viewModel.totalBudget)")
                .font(.system(size: 13))
                .foregroundColor(NeighborlyTheme.textMuted)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(NeighborlyTheme.greenSoft)
                        .frame(height: 8)

                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color(red: 0.25, green: 0.65, blue: 0.45),
                                    NeighborlyTheme.green
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geo.size.width * viewModel.budgetProgress, height: 8)
                }
            }
            .frame(height: 8)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    private var savedMetricCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(NeighborlyTheme.orangeSoft)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "chart.line.uptrend.xyaxis")
                            .font(.system(size: 16))
                            .foregroundColor(NeighborlyTheme.orange)
                    )

                Text("SAVED")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(NeighborlyTheme.textMuted)
            }

            Text(viewModel.savedThisMonth)
                .font(.system(size: 24, weight: .heavy, design: .serif))
                .foregroundColor(NeighborlyTheme.orange)

            Text(viewModel.savedThisMonthLabel)
                .font(.system(size: 13))
                .foregroundColor(NeighborlyTheme.textMuted)

            HStack(alignment: .bottom, spacing: 5) {
                ForEach(Array(viewModel.savingsBarHeights.enumerated()), id: \.offset) { index, height in
                    let isLast = index == viewModel.savingsBarHeights.count - 1
                    RoundedRectangle(cornerRadius: 4)
                        .fill(
                            isLast
                            ? AnyShapeStyle(
                                LinearGradient(
                                    colors: [NeighborlyTheme.orange, NeighborlyTheme.orange.opacity(0.7)],
                                    startPoint: .top,
                                    endPoint: .bottom
                                )
                              )
                            : AnyShapeStyle(NeighborlyTheme.orange.opacity(0.2))
                        )
                        .frame(height: 24 * height)
                }
            }
            .frame(height: 24, alignment: .bottom)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    private func smallMetricCard(
        value: String,
        label: String,
        icon: String,
        tint: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(tint)

            Text(value)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(NeighborlyTheme.textPrimary)

            Text(label)
                .font(.system(size: 12))
                .foregroundColor(NeighborlyTheme.textMuted)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    // MARK: - Optimized Route Card

    private var optimizedRouteCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Image(systemName: "paperplane.fill")
                    .font(.system(size: 14))
                    .foregroundColor(NeighborlyTheme.green)

                Text("OPTIMIZED ROUTE")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(NeighborlyTheme.green)
                    .tracking(0.5)

                Spacer()

                Text(viewModel.optimizedStopsLabel)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(NeighborlyTheme.textSecondary)
            }

            mapPlaceholder

            VStack(spacing: 12) {
                ForEach(viewModel.routeStops) { stop in
                    routeStopRow(stop)
                }
            }
        }
        .padding(20)
        .background(NeighborlyTheme.greenSoft)
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }

    private var mapPlaceholder: some View {
        ZStack {
            LinearGradient(
                colors: [
                    NeighborlyTheme.green.opacity(0.15),
                    NeighborlyTheme.green.opacity(0.05)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(spacing: 6) {
                Image(systemName: "map")
                    .font(.system(size: 28))
                    .foregroundColor(NeighborlyTheme.green.opacity(0.5))

                Text("Google Maps integration")
                    .font(.system(size: 13))
                    .foregroundColor(NeighborlyTheme.textMuted)
            }
        }
        .frame(height: 140)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func routeStopRow(_ stop: HomeRouteStop) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(NeighborlyTheme.green)
                .frame(width: 28, height: 28)
                .overlay(
                    Text("\(stop.index)")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(stop.name)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(NeighborlyTheme.textPrimary)

                Text(stop.address)
                    .font(.system(size: 13))
                    .foregroundColor(NeighborlyTheme.textMuted)

                HStack(spacing: 8) {
                    Text(stop.itemsLabel)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(NeighborlyTheme.green)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(NeighborlyTheme.greenSoft)
                        .clipShape(Capsule())

                    Text(stop.timeEstimate)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(NeighborlyTheme.orange)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(NeighborlyTheme.orangeSoft)
                        .clipShape(Capsule())
                }
            }

            Spacer()

            Text(stop.distance)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(NeighborlyTheme.textSecondary)
        }
        .padding(12)
        .background(NeighborlyTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

#Preview {
    HomeView()
        .environment(AuthController())
}
