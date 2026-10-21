// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

/// One colour in one appearance, as plain components. The palette below reads as a table because
/// of it.
struct RGBAValue {
    let r: CGFloat
    let g: CGFloat
    let b: CGFloat
    let a: CGFloat

    init(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat, _ a: CGFloat = 1) {
        self.r = r
        self.g = g
        self.b = b
        self.a = a
    }
}

/// The app's own colours, on top of the system's.
///
/// Everything else here is a stock control — `Form`, `List`, `TabView` — because that is what an
/// app on each platform should feel like. What makes it *this* app is the red from the icon: it
/// tints every control through `.tint(.brand)` on the root, so switches, links, the selected tab
/// and the pull-to-refresh spinner all speak with the same voice. Android gets that from Material
/// You; on iOS and macOS one accent colour is the equivalent, and it costs one line.
extension Color {

    /// A colour that follows the appearance: `light` in light mode, `dark` in dark mode.
    ///
    /// UIKit resolves it through the trait collection, AppKit through the best match of the
    /// appearance. Both get the same components, so the palette is one definition on both
    /// platforms.
    init(light: RGBAValue, dark: RGBAValue) {
#if canImport(UIKit)
        self.init(uiColor: UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: value.r, green: value.g, blue: value.b, alpha: value.a)
        })
#else
        self.init(nsColor: NSColor(name: nil) { appearance in
            let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            let value = isDark ? dark : light
            return NSColor(red: value.r, green: value.g, blue: value.b, alpha: value.a)
        })
#endif
    }

    /// DHBW red. The dark variant is lifted, because the icon's red on a black background is
    /// too dense to read a caption off.
    static let brand = Color(
        light: RGBAValue(0.78, 0.09, 0.14),
        dark: RGBAValue(1.00, 0.35, 0.36)
    )

    /// The timetable blocks. Opaque, not translucent: the grid tints the column of the current
    /// day, and a see-through block sitting on that tint came out a different colour from the
    /// same lecture on any other day.
    static let lectureFill = Color(
        light: RGBAValue(0.98, 0.91, 0.92),
        dark: RGBAValue(0.23, 0.10, 0.11)
    )

    /// Exams, in the warning colour rather than the accent — the same distinction Android makes
    /// with `colorScheme.error` against `colorScheme.primary`, except that here the accent is
    /// already red, so the exam has to move instead.
    static let examFill = Color(
        light: RGBAValue(1.00, 0.95, 0.86),
        dark: RGBAValue(0.24, 0.17, 0.05)
    )

    static let examEdge = Color(
        light: RGBAValue(0.85, 0.48, 0.00),
        dark: RGBAValue(1.00, 0.69, 0.25)
    )

    /// A quieter brand tone for backgrounds that carry text.
    static let brandSoft = Color(
        light: RGBAValue(0.78, 0.09, 0.14, 0.10),
        dark: RGBAValue(1.00, 0.35, 0.36, 0.22)
    )

    // MARK: System surfaces

    /// The background behind a `Form` or a grouped `List`.
    static var platformGroupedBackground: Color {
#if canImport(UIKit)
        Color(uiColor: .systemGroupedBackground)
#else
        Color(nsColor: .windowBackgroundColor)
#endif
    }

    /// The card that sits on `platformGroupedBackground`.
    static var platformSecondaryGroupedBackground: Color {
#if canImport(UIKit)
        Color(uiColor: .secondarySystemGroupedBackground)
#else
        Color(nsColor: .controlBackgroundColor)
#endif
    }

    /// A quiet fill for a tile that sits on a card.
    static var platformTertiaryFill: Color {
#if canImport(UIKit)
        Color(uiColor: .tertiarySystemFill)
#else
        Color.primary.opacity(0.06)
#endif
    }

    /// The hairlines between grid hours.
    static var platformSeparator: Color {
#if canImport(UIKit)
        Color(uiColor: .separator)
#else
        Color(nsColor: .separatorColor)
#endif
    }
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
            .background(Color.platformSecondaryGroupedBackground, in: .rect(cornerRadius: 16))
    }
}

extension View {
    func cardBackground() -> some View { modifier(CardBackground()) }

    /// `.navigationBarTitleDisplayMode(.inline)`, which only UIKit has. The macOS title bar is
    /// always inline, so there is nothing to ask for.
    @ViewBuilder
    func platformInlineTitle() -> some View {
#if os(iOS)
        navigationBarTitleDisplayMode(.inline)
#else
        self
#endif
    }

    /// The inset grouped list of iOS, and the closest list style AppKit has to it.
    @ViewBuilder
    func platformGroupedList() -> some View {
#if os(iOS)
        listStyle(.insetGrouped)
#else
        listStyle(.inset)
#endif
    }

    /// Address-field traits: the e-mail keyboard and no capitalisation. Both are UIKit-only, and
    /// neither changes what the field stores, so macOS simply does without them.
    @ViewBuilder
    func emailEntryTraits() -> some View {
#if os(iOS)
        keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
#else
        self
#endif
    }

    /// A minimum size for a sheet's content. iOS sizes sheets itself; a macOS sheet takes the size
    /// of its content, which without this can be a few pixels tall.
    @ViewBuilder
    func platformSheetFrame() -> some View {
#if os(macOS)
        frame(minWidth: 460, minHeight: 420)
#else
        self
#endif
    }
}
