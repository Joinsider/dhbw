// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI

/// The accent picker: one track across the row, painted with every accent in palette order, and a
/// thumb that snaps to the nearest one.
///
/// A slider rather than a grid of swatches, because seventeen swatches wrap into rows and the
/// eye has to search them; on one track the palette reads as one spectrum. The track is cut into
/// as many equal segments as there are accents, so a tap anywhere picks the segment under the
/// finger, and a drag moves through them one at a time with a selection tick on every step.
///
/// VoiceOver sees a single adjustable element: swipe up and down to step, the colour's name as
/// the value — the same thing a sighted user gets from the label above the track.
struct AccentSlider: View {

    @Binding var selection: AccentChoice

    private static let choices = AccentChoice.allCases
    private static let trackHeight: CGFloat = 28
    private static let thumbSize: CGFloat = 34

    private var index: Int { Self.choices.firstIndex(of: selection) ?? 0 }

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            let segment = width / CGFloat(Self.choices.count)
            ZStack(alignment: .leading) {
                track(segment: segment)
                thumb
                    .offset(x: segment * (CGFloat(index) + 0.5) - Self.thumbSize / 2)
                    .animation(.snappy(duration: 0.18), value: index)
            }
            .frame(height: Self.thumbSize)
            .contentShape(.rect)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in select(at: value.location.x, width: width) }
            )
        }
        .frame(height: Self.thumbSize)
        .sensoryFeedback(.selection, trigger: index)
        .accessibilityElement()
        .accessibilityLabel(Text("settings.accentColor"))
        .accessibilityValue(Text(selection.name))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: step(by: 1)
            case .decrement: step(by: -1)
            @unknown default: break
            }
        }
        .accessibilityIdentifier("settingsAccentSlider")
    }

    /// The accents as one gradient, each colour at the centre of its segment, with a small mark on
    /// every stop so the places the thumb can land are visible.
    private func track(segment: CGFloat) -> some View {
        let count = Self.choices.count
        let stops = Self.choices.enumerated().map { offset, choice in
            Gradient.Stop(color: choice.accent, location: (CGFloat(offset) + 0.5) / CGFloat(count))
        }
        return Capsule()
            .fill(LinearGradient(stops: stops, startPoint: .leading, endPoint: .trailing))
            .frame(height: Self.trackHeight)
            .overlay(alignment: .leading) {
                HStack(spacing: 0) {
                    ForEach(0..<count, id: \.self) { _ in
                        Circle()
                            .fill(.white.opacity(0.55))
                            .frame(width: 4, height: 4)
                            .frame(width: segment)
                    }
                }
            }
    }

    /// The selected colour in a ring of the card's own background, so it stands off the track.
    private var thumb: some View {
        Circle()
            .fill(selection.accent)
            .frame(width: Self.thumbSize, height: Self.thumbSize)
            .overlay(Circle().strokeBorder(Color.platformSecondaryGroupedBackground, lineWidth: 3))
            .shadow(color: .black.opacity(0.25), radius: 3, y: 1)
    }

    private func select(at x: CGFloat, width: CGFloat) {
        guard width > 0 else { return }
        let raw = Int((x / width) * CGFloat(Self.choices.count))
        let clamped = min(max(raw, 0), Self.choices.count - 1)
        if clamped != index { selection = Self.choices[clamped] }
    }

    private func step(by delta: Int) {
        let next = min(max(index + delta, 0), Self.choices.count - 1)
        if next != index { selection = Self.choices[next] }
    }
}
