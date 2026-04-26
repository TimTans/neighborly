import SwiftUI
import UIKit

enum NeighborlyTheme {
    static let background = dynamicColor(
        light: UIColor(red: 247/255, green: 243/255, blue: 236/255, alpha: 1),
        dark: UIColor(red: 18/255, green: 20/255, blue: 22/255, alpha: 1)
    )

    static let surface = dynamicColor(
        light: .white,
        dark: UIColor(red: 30/255, green: 33/255, blue: 36/255, alpha: 1)
    )

    static let surfaceSecondary = dynamicColor(
        light: UIColor(red: 241/255, green: 237/255, blue: 230/255, alpha: 1),
        dark: UIColor(red: 42/255, green: 45/255, blue: 49/255, alpha: 1)
    )

    static let fieldBackground = dynamicColor(
        light: .white,
        dark: UIColor(red: 36/255, green: 39/255, blue: 43/255, alpha: 1)
    )

    static let border = Color(uiColor: .separator)
    static let textPrimary = Color(uiColor: .label)
    static let textSecondary = Color(uiColor: .secondaryLabel)
    static let textMuted = Color(uiColor: .tertiaryLabel)

    static let green = Color(red: 12/255, green: 106/255, blue: 74/255)
    static let greenSoft = dynamicColor(
        light: UIColor(red: 224/255, green: 241/255, blue: 232/255, alpha: 1),
        dark: UIColor(red: 31/255, green: 58/255, blue: 47/255, alpha: 1)
    )

    static let orange = Color(red: 230/255, green: 126/255, blue: 34/255)
    static let orangeSoft = dynamicColor(
        light: UIColor(red: 255/255, green: 243/255, blue: 224/255, alpha: 1),
        dark: UIColor(red: 72/255, green: 50/255, blue: 29/255, alpha: 1)
    )

    static let cardBackground = surface

    private static func dynamicColor(light: UIColor, dark: UIColor) -> Color {
        Color(
            uiColor: UIColor { traits in
                traits.userInterfaceStyle == .dark ? dark : light
            }
        )
    }
}
