// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI

/// The coloured tile at the start of a row in Settings and lecture details, as System Settings
/// draws them: a white SF Symbol on a rounded square of one colour. The glyph is fitted into a
/// square box inside the tile rather than sized by its font alone, so wide or badged symbols
/// (calendar.badge.clock, chevron.left.forwardslash.chevron.right) cannot reach the tile's edges.
/// The tile grows with Dynamic Type, capped so accessibility sizes do not swamp the rows.
struct SettingsIcon: View {

    let systemName: String
    let color: Color

    #if os(macOS)
    @ScaledMetric(relativeTo: .body) private var scaledTile: CGFloat = 24
    @ScaledMetric(relativeTo: .body) private var scaledGlyph: CGFloat = 13
    private static let maxTile: CGFloat = 24
    private static let maxGlyph: CGFloat = 13
    private static let cornerRadius: CGFloat = 6
    private static let weight: Font.Weight = .semibold
    #else
    @ScaledMetric(relativeTo: .body) private var scaledTile: CGFloat = 26
    @ScaledMetric(relativeTo: .body) private var scaledGlyph: CGFloat = 14
    private static let maxTile: CGFloat = 32
    private static let maxGlyph: CGFloat = 18
    private static let cornerRadius: CGFloat = 6
    private static let weight: Font.Weight = .medium
    #endif

    private var tileSize: CGFloat { min(scaledTile, Self.maxTile) }
    private var glyphSize: CGFloat { min(scaledGlyph, Self.maxGlyph) }

    var body: some View {
        Image(systemName: systemName)
            .resizable()
            .scaledToFit()
            .fontWeight(Self.weight)
            .foregroundStyle(.white)
            .frame(width: glyphSize, height: glyphSize)
            .frame(width: tileSize, height: tileSize)
            .background(color, in: .rect(cornerRadius: Self.cornerRadius))
            .accessibilityHidden(true)
    }
}
