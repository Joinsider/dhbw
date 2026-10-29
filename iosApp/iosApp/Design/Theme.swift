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

/// The accents the user can choose on iOS and macOS. Android and Desktop choose a seed colour
/// instead; see ADR-0002 for why the two are different settings.
///
/// Every entry is a hand-checked triple: the accent, the lecture block fill and the dark variants
/// of both. The palette is curated rather than free because a free colour cannot promise the
/// contrast the controls need, nor that a pink lecture is not mistaken for an orange exam.
/// `brand` is the DHBW red the app has always shown and must stay identical to `Color.brand`.
///
/// The ids are what Kotlin stores (`SettingsState.KNOWN_ACCENT_CHOICES`); adding a case means
/// adding its id there too.
enum AccentChoice: String, CaseIterable, Identifiable {
    case brand, pink, raspberry, purple, violet, indigo, blue, lightBlue, cyan, teal, green
    case lightGreen, lime, yellow, amber, orange, deepOrange, blueGrey

    var id: String { rawValue }

    /// The choice for a stored id. Anything unknown, such as a colour removed in a later version,
    /// shows as DHBW red rather than failing.
    init(id: String) {
        self = AccentChoice(rawValue: id) ?? .brand
    }

    /// Tint for controls, and for text and icons that carry the accent.
    var accent: Color {
        Color(light: palette.accentLight, dark: palette.accentDark)
    }

    /// The fill of a timetable block. Opaque for the same reason as `Color.lectureFill`.
    var lectureFill: Color {
        Color(light: palette.fillLight, dark: palette.fillDark)
    }

    /// A quiet tint behind text, the way `Color.brandSoft` is behind the current day.
    var soft: Color {
        Color(light: palette.accentLight.withAlpha(0.10), dark: palette.accentDark.withAlpha(0.22))
    }

    /// Exams are orange so that they stand apart from the red accent. Next to a yellow, amber or
    /// orange accent that orange would be one more shade of the same colour, so those accents move
    /// the exam to violet instead — the rule `Color.examFill` states for red, applied to them.
    private var examIsViolet: Bool {
        switch self {
        case .yellow, .amber, .orange, .deepOrange: true
        default: false
        }
    }

    var examFill: Color {
        examIsViolet
            ? Color(light: RGBAValue(0.94, 0.91, 0.98), dark: RGBAValue(0.19, 0.12, 0.26))
            : .examFill
    }

    var examEdge: Color {
        examIsViolet
            ? Color(light: RGBAValue(0.48, 0.25, 0.75), dark: RGBAValue(0.78, 0.60, 1.00))
            : .examEdge
    }

    /// The name shown under the swatch and read out by VoiceOver.
    var name: LocalizedStringKey {
        switch self {
        case .brand: "accent.brand"
        case .pink: "accent.pink"
        case .raspberry: "accent.raspberry"
        case .purple: "accent.purple"
        case .violet: "accent.violet"
        case .indigo: "accent.indigo"
        case .blue: "accent.blue"
        case .lightBlue: "accent.lightBlue"
        case .cyan: "accent.cyan"
        case .teal: "accent.teal"
        case .green: "accent.green"
        case .lightGreen: "accent.lightGreen"
        case .lime: "accent.lime"
        case .yellow: "accent.yellow"
        case .amber: "accent.amber"
        case .orange: "accent.orange"
        case .deepOrange: "accent.deepOrange"
        case .blueGrey: "accent.blueGrey"
        }
    }

    private struct Palette {
        let accentLight: RGBAValue
        let accentDark: RGBAValue
        let fillLight: RGBAValue
        let fillDark: RGBAValue
    }

    private var palette: Palette {
        switch self {
        case .brand:
            Palette(
                accentLight: RGBAValue(0.78, 0.09, 0.14),
                accentDark: RGBAValue(1.00, 0.35, 0.36),
                fillLight: RGBAValue(0.98, 0.91, 0.92),
                fillDark: RGBAValue(0.23, 0.10, 0.11)
            )
        case .raspberry:
            Palette(
                accentLight: RGBAValue(0.76, 0.09, 0.36),
                accentDark: RGBAValue(1.00, 0.45, 0.70),
                fillLight: RGBAValue(0.98, 0.91, 0.95),
                fillDark: RGBAValue(0.26, 0.10, 0.18)
            )
        case .purple:
            Palette(
                accentLight: RGBAValue(0.48, 0.25, 0.75),
                accentDark: RGBAValue(0.78, 0.60, 1.00),
                fillLight: RGBAValue(0.94, 0.91, 0.98),
                fillDark: RGBAValue(0.19, 0.12, 0.26)
            )
        case .blue:
            Palette(
                accentLight: RGBAValue(0.05, 0.40, 0.80),
                accentDark: RGBAValue(0.44, 0.70, 1.00),
                fillLight: RGBAValue(0.90, 0.94, 1.00),
                fillDark: RGBAValue(0.09, 0.14, 0.26)
            )
        case .teal:
            Palette(
                accentLight: RGBAValue(0.00, 0.45, 0.47),
                accentDark: RGBAValue(0.35, 0.85, 0.85),
                fillLight: RGBAValue(0.88, 0.97, 0.97),
                fillDark: RGBAValue(0.07, 0.22, 0.23)
            )
        case .green:
            Palette(
                accentLight: RGBAValue(0.14, 0.45, 0.18),
                accentDark: RGBAValue(0.45, 0.88, 0.50),
                fillLight: RGBAValue(0.90, 0.97, 0.90),
                fillDark: RGBAValue(0.11, 0.24, 0.12)
            )

        case .pink:
            Palette(
                accentLight: RGBAValue(1.00, 0.24, 0.87),
                accentDark: RGBAValue(1.00, 0.62, 0.93),
                fillLight: RGBAValue(1.00, 0.95, 0.99),
                fillDark: RGBAValue(0.24, 0.00, 0.20)
            )
        case .violet:
            Palette(
                accentLight: RGBAValue(0.37, 0.21, 0.69),
                accentDark: RGBAValue(0.64, 0.53, 0.86),
                fillLight: RGBAValue(0.93, 0.91, 0.97),
                fillDark: RGBAValue(0.10, 0.06, 0.18)
            )
        case .indigo:
            Palette(
                accentLight: RGBAValue(0.22, 0.29, 0.67),
                accentDark: RGBAValue(0.52, 0.57, 0.84),
                fillLight: RGBAValue(0.91, 0.92, 0.97),
                fillDark: RGBAValue(0.06, 0.08, 0.18)
            )
        case .lightBlue:
            Palette(
                accentLight: RGBAValue(0.01, 0.49, 0.72),
                accentDark: RGBAValue(0.01, 0.62, 0.92),
                fillLight: RGBAValue(0.88, 0.96, 1.00),
                fillDark: RGBAValue(0.00, 0.16, 0.24)
            )
        case .cyan:
            Palette(
                accentLight: RGBAValue(0.00, 0.51, 0.57),
                accentDark: RGBAValue(0.00, 0.67, 0.76),
                fillLight: RGBAValue(0.88, 0.99, 1.00),
                fillDark: RGBAValue(0.00, 0.21, 0.24)
            )
        case .lightGreen:
            Palette(
                accentLight: RGBAValue(0.35, 0.50, 0.19),
                accentDark: RGBAValue(0.49, 0.70, 0.26),
                fillLight: RGBAValue(0.94, 0.97, 0.91),
                fillDark: RGBAValue(0.12, 0.18, 0.06)
            )
        case .lime:
            Palette(
                accentLight: RGBAValue(0.46, 0.48, 0.12),
                accentDark: RGBAValue(0.75, 0.79, 0.20),
                fillLight: RGBAValue(0.97, 0.98, 0.90),
                fillDark: RGBAValue(0.18, 0.19, 0.05)
            )
        case .yellow:
            Palette(
                accentLight: RGBAValue(0.55, 0.45, 0.01),
                accentDark: RGBAValue(0.99, 0.85, 0.21),
                fillLight: RGBAValue(1.00, 0.98, 0.88),
                fillDark: RGBAValue(0.24, 0.19, 0.00)
            )
        case .amber:
            Palette(
                accentLight: RGBAValue(0.61, 0.43, 0.00),
                accentDark: RGBAValue(1.00, 0.70, 0.00),
                fillLight: RGBAValue(1.00, 0.96, 0.88),
                fillDark: RGBAValue(0.24, 0.17, 0.00)
            )
        case .orange:
            Palette(
                accentLight: RGBAValue(0.69, 0.39, 0.00),
                accentDark: RGBAValue(0.98, 0.55, 0.00),
                fillLight: RGBAValue(1.00, 0.95, 0.88),
                fillDark: RGBAValue(0.24, 0.13, 0.00)
            )
        case .deepOrange:
            Palette(
                accentLight: RGBAValue(0.85, 0.23, 0.04),
                accentDark: RGBAValue(0.96, 0.41, 0.24),
                fillLight: RGBAValue(0.99, 0.91, 0.89),
                fillDark: RGBAValue(0.23, 0.06, 0.01)
            )
        case .blueGrey:
            Palette(
                accentLight: RGBAValue(0.33, 0.43, 0.48),
                accentDark: RGBAValue(0.50, 0.60, 0.65),
                fillLight: RGBAValue(0.93, 0.94, 0.95),
                fillDark: RGBAValue(0.10, 0.13, 0.14)
            )
        }
    }
}

/// Where the chosen accent id is mirrored for the widget extension. The widget is another process
/// and cannot see this target's files, so it reads the same two constants from its own copy in
/// `TimetableWidget/WidgetViews.swift`; change both together.
enum WidgetAccentStore {
    static let suiteName = "group.de.fampopprol.dhbwhorb"
    static let key = "accent_choice"
}

extension RGBAValue {
    /// The same colour with another alpha, for the translucent tints.
    func withAlpha(_ alpha: CGFloat) -> RGBAValue {
        RGBAValue(r, g, b, alpha)
    }
}

extension EnvironmentValues {
    /// The accent the user chose. Set once at the root next to `.tint`, for views that colour
    /// things themselves (timetable blocks, today's column, the accent in a few headers): a
    /// `.tint` reaches controls only, not a `foregroundStyle` or a `fill`.
    @Entry var accentChoice: AccentChoice = .brand
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

    /// The text of a login input. macOS gets a plain field, because the bordered default ignores the
    /// height the login fields are given, and the size of the button's label; iOS keeps its own.
    @ViewBuilder
    func platformLoginField() -> some View {
#if os(macOS)
        textFieldStyle(.plain)
            .font(.title3)
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
