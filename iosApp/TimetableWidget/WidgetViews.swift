// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import WidgetKit
import Shared

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Akzentfarbe
// ═══════════════════════════════════════════════════════════════════════════════

/// The accent the user picked in the app (ADR-0002).
///
/// The app mirrors the choice's id into the App Group and reloads the timelines when it changes.
/// This is the widget's copy of the palette in `iosApp/iosApp/Design/Theme.swift`: the widget target
/// cannot see the app's files, so the values are written out again here. Change both together, and
/// keep the ids in step with `SettingsState.KNOWN_ACCENT_CHOICES`. An unknown or missing id is DHBW red.
enum WidgetAccent {

    private typealias RGB = (r: Double, g: Double, b: Double)

    private static let table: [String: (light: RGB, dark: RGB)] = [
        "brand": ((0.78, 0.09, 0.14), (1.00, 0.35, 0.36)),
        "raspberry": ((0.76, 0.09, 0.36), (1.00, 0.45, 0.70)),
        "purple": ((0.48, 0.25, 0.75), (0.78, 0.60, 1.00)),
        "blue": ((0.05, 0.40, 0.80), (0.44, 0.70, 1.00)),
        "teal": ((0.00, 0.45, 0.47), (0.35, 0.85, 0.85)),
        "green": ((0.14, 0.45, 0.18), (0.45, 0.88, 0.50)),
        "pink": ((1.00, 0.24, 0.87), (1.00, 0.62, 0.93)),
        "violet": ((0.37, 0.21, 0.69), (0.64, 0.53, 0.86)),
        "indigo": ((0.22, 0.29, 0.67), (0.52, 0.57, 0.84)),
        "lightBlue": ((0.01, 0.49, 0.72), (0.01, 0.62, 0.92)),
        "cyan": ((0.00, 0.51, 0.57), (0.00, 0.67, 0.76)),
        "lightGreen": ((0.35, 0.50, 0.19), (0.49, 0.70, 0.26)),
        "lime": ((0.46, 0.48, 0.12), (0.75, 0.79, 0.20)),
        "yellow": ((0.55, 0.45, 0.01), (0.99, 0.85, 0.21)),
        "amber": ((0.61, 0.43, 0.00), (1.00, 0.70, 0.00)),
        "orange": ((0.69, 0.39, 0.00), (0.98, 0.55, 0.00)),
        "deepOrange": ((0.85, 0.23, 0.04), (0.96, 0.41, 0.24)),
        "blueGrey": ((0.33, 0.43, 0.48), (0.50, 0.60, 0.65)),
    ]

    /// The accent for the current choice, read on every timeline build.
    static var color: Color {
        let id = UserDefaults(suiteName: "group.de.fampopprol.dhbwhorb")?.string(forKey: "accent_choice")
        let pair = table[id ?? "brand"] ?? table["brand"]!
        return Color(uiColor: UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? pair.dark : pair.light
            return UIColor(red: value.r, green: value.g, blue: value.b, alpha: 1)
        })
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Small (.systemSmall)  ·  Nächste / aktuelle Vorlesung
// ═══════════════════════════════════════════════════════════════════════════════

struct SmallWidgetView: View {
    let entry: TimetableEntry

    var body: some View {
        if let lecture = entry.snapshot.upNext {
            upNextCard(
                label: entry.snapshot.upNextIsRunning ? String(localized: "NOW") : String(localized: "NEXT"),
                labelColor: entry.snapshot.upNextIsRunning ? .red : WidgetAccent.color,
                lecture: lecture
            )
        } else {
            noClassesView(compact: true)
        }
    }

    // ── Vorlesungskarte ───────────────────────────────────────────────────────

    @ViewBuilder
    private func upNextCard(label: String, labelColor: Color, lecture: WidgetLecture) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // Status-Label + Icon
            HStack(spacing: 4) {
                Image(systemName: lecture.isTest ? "pencil.circle.fill" : "book.fill")
                    .font(.caption2)
                Text(label)
                    .font(.caption2.weight(.heavy))
                    .kerning(0.5)
            }
            .foregroundStyle(labelColor)

            Spacer(minLength: 6)

            // Veranstaltungsname – prominentester Text
            Text(lecture.name)
                .font(.headline)
                .lineLimit(3)
                .minimumScaleFactor(0.75)
                .fixedSize(horizontal: false, vertical: false)

            Spacer(minLength: 4)

            // Raum
            Label(lecture.location, systemImage: "mappin.and.ellipse")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)

            // Zeitfenster
            Text("\(lecture.startText) – \(lecture.endText)")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(6)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Medium (.systemMedium)  ·  Kompaktliste heute + morgen
// ═══════════════════════════════════════════════════════════════════════════════

struct MediumWidgetView: View {
    let entry: TimetableEntry

    private var days: [WidgetDay] { Array(entry.snapshot.days.prefix(2)) }

    var body: some View {
        if days.isEmpty {
            noClassesView(compact: false)
        } else {
            HStack(alignment: .top, spacing: 10) {
                ForEach(Array(days.enumerated()), id: \.offset) { index, day in
                    VStack(alignment: .leading, spacing: 6) {
                        // Tag-Kopfzeile
                        Text(day.dayLabel)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                            .textCase(.uppercase)

                        // Vorlesungsliste (kompakt, maximal 3 Einträge pro Tag)
                        ForEach(day.lectures.prefix(3), id: \.start) { lecture in
                            ClassRowView(lecture: lecture, compact: true)
                        }

                        if day.lectures.count > 3 {
                            Text("+\(day.lectures.count - 3) more")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .topLeading)

                    // Trennlinie zwischen Spalten (außer nach der letzten)
                    if index < days.count - 1 {
                        Divider()
                    }
                }
            }
            .padding(6)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Large (.systemLarge)  ·  Detailkarten für heute + morgen
// ═══════════════════════════════════════════════════════════════════════════════

struct LargeWidgetView: View {
    let entry: TimetableEntry

    var body: some View {
        if entry.snapshot.days.isEmpty {
            noClassesView(compact: false)
        } else {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(entry.snapshot.days.prefix(2), id: \.date) { day in
                    DaySectionView(day: day)
                }
                Spacer(minLength: 0)
            }
            .padding(6)
        }
    }
}

/// Tagesabschnitt für das Large-Widget: Kopfzeile + vollständige Detailkarten.
private struct DaySectionView: View {
    let day: WidgetDay

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Tag-Kopfzeile mit Datumsstring
            HStack {
                Text(day.dayLabel)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                Spacer()
                Text(day.date, format: .dateTime.day().month(.abbreviated))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.bottom, 2)

            // Vorlesungskarten (maximal 5 pro Tag – für Large realistisch)
            ForEach(day.lectures.prefix(5), id: \.start) { lecture in
                ClassRowView(lecture: lecture, compact: false)
            }

            if day.lectures.count > 5 {
                Text("+\(day.lectures.count - 5) more")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Gemeinsame Subviews
// ═══════════════════════════════════════════════════════════════════════════════

/// Eine einzelne Vorlesungszeile mit farbigem Statusstreifen links.
struct ClassRowView: View {
    let lecture: WidgetLecture
    let compact: Bool

    private var accentColor: Color {
        if lecture.isTest    { return .red }
        if lecture.isOngoing { return WidgetAccent.color }
        return Color.secondary.opacity(0.4)
    }

    var body: some View {
        HStack(spacing: 6) {
            // Farbiger Statusstreifen
            RoundedRectangle(cornerRadius: 2)
                .fill(accentColor)
                .frame(width: 3, height: compact ? 28 : 38)

            VStack(alignment: .leading, spacing: 1) {
                // Name: Kurzname im kompakten Modus, Vollname im Detailmodus
                Text(compact ? lecture.shortName : lecture.name)
                    .font(compact ? .caption.weight(.semibold) : .callout.weight(.semibold))
                    .lineLimit(1)

                // Zeit + Raum in einer Zeile
                HStack(spacing: 4) {
                    Text("\(lecture.startText)–\(lecture.endText)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    if !lecture.location.isEmpty {
                        Text("·")
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                        Label(lecture.location, systemImage: "mappin")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .labelStyle(.titleOnly)
                            .lineLimit(1)
                    }

                    if lecture.isTest {
                        // Ohne die beiden Modifier bricht „KLAUSUR" im Medium-Widget mitten
                        // im Wort um, sobald der Raumname die Zeile schon fast füllt.
                        Text("EXAM")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.red)
                            .lineLimit(1)
                            .fixedSize()
                    }
                }
            }

            Spacer(minLength: 0)
        }
    }
}

/// Platzhalter-View wenn keine Vorlesungsdaten vorliegen.
func noClassesView(compact: Bool) -> some View {
    VStack(spacing: compact ? 6 : 10) {
        Image(systemName: "calendar.badge.checkmark")
            .font(compact ? .title2 : .largeTitle)
            .foregroundStyle(.secondary)
        Text(compact ? "No lectures" : "No upcoming lectures")
            .font(compact ? .caption : .callout)
            .multilineTextAlignment(.center)
            .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Hilfserweiterungen
// ═══════════════════════════════════════════════════════════════════════════════

extension WidgetDay {
    /// „Heute", „Morgen" oder „Mo, 12.03." — in der Sprache des Geräts.
    ///
    /// `setLocalizedDateFormatFromTemplate` statt eines festen Musters: das Datumsformat war hier
    /// zusammen mit den Texten hart auf Deutsch verdrahtet, und ein englisches Gerät bekam dann
    /// „Mo, 12.03." neben englischen Beschriftungen.
    var dayLabel: String {
        if Calendar.current.isDateInToday(date)    { return String(localized: "Today") }
        if Calendar.current.isDateInTomorrow(date) { return String(localized: "Tomorrow") }
        let fmt = DateFormatter()
        fmt.locale = .current
        fmt.setLocalizedDateFormatFromTemplate("EEEddMM")
        return fmt.string(from: date)
    }
}
