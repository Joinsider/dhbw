// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI

/// The app's own colours, on top of the system's.
///
/// Everything else here is a stock UIKit control — `Form`, `List`, `TabView` — because that is
/// what an iOS app should feel like. What makes it *this* app is the red from the icon: it tints
/// every control through `.tint(.brand)` on the root, so switches, links, the selected tab and
/// the pull-to-refresh spinner all speak with the same voice. Android gets that from Material You;
/// on iOS one accent colour is the equivalent, and it costs one line.
extension Color {

    /// DHBW red. The dark variant is lifted, because the icon's red on a black background is
    /// too dense to read a caption off.
    static let brand = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 1.00, green: 0.35, blue: 0.36, alpha: 1)
            : UIColor(red: 0.78, green: 0.09, blue: 0.14, alpha: 1)
    })

    /// A quieter brand tone for backgrounds that carry text.
    static let brandSoft = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 1.00, green: 0.35, blue: 0.36, alpha: 0.22)
            : UIColor(red: 0.78, green: 0.09, blue: 0.14, alpha: 0.10)
    })
}

extension ShapeStyle where Self == LinearGradient {

    /// The gradient on the app mark and the login button — the icon's own top-to-bottom fade.
    static var brandGradient: LinearGradient {
        LinearGradient(
            colors: [Color.brand.opacity(0.92), Color.brand],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

/// A grouped card the way `Form` draws one, for the places that are not a `Form`.
struct CardBackground: ViewModifier {

    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
    }
}

extension View {
    func cardBackground() -> some View { modifier(CardBackground()) }
}
