// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import WidgetKit

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Small (.systemSmall)  ·  Nächste / aktuelle Vorlesung
// ═══════════════════════════════════════════════════════════════════════════════

struct SmallWidgetView: View {
    let entry: TimetableEntry

    var body: some View {
        switch entry.upNext?.type {
        case .currentlyRunning:
            upNextCard(
                label: "JETZT",
                labelColor: .red,
                lecture: entry.upNext!.lecture!
            )
        case .comingUp:
            upNextCard(
                label: "NÄCHSTE",
                labelColor: .accentColor,
                lecture: entry.upNext!.lecture!
            )
        default:
            noClassesView(compact: true)
        }
    }

    // ── Vorlesungskarte ───────────────────────────────────────────────────────

    @ViewBuilder
    private func upNextCard(label: String, labelColor: Color, lecture: WidgetClassInfo) -> some View {
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
            Text("\(lecture.startTime) – \(lecture.endTime)")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(14)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Medium (.systemMedium)  ·  Kompaktliste heute + morgen
// ═══════════════════════════════════════════════════════════════════════════════

struct MediumWidgetView: View {
    let entry: TimetableEntry

    var body: some View {
        if entry.multiDay.isEmpty {
            noClassesView(compact: false)
        } else {
            HStack(alignment: .top, spacing: 10) {
                ForEach(entry.multiDay.prefix(2)) { day in
                    VStack(alignment: .leading, spacing: 6) {
                        // Tag-Kopfzeile
                        Text(day.localizedDayLabel)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.secondary)
                            .textCase(.uppercase)

                        // Vorlesungsliste (kompakt, maximal 3 Einträge pro Tag)
                        ForEach(day.classes.prefix(3)) { lecture in
                            ClassRowView(lecture: lecture, compact: true)
                        }

                        if day.classes.count > 3 {
                            Text("+\(day.classes.count - 3) weitere")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .topLeading)

                    // Trennlinie zwischen Spalten (außer nach der letzten)
                    if day.id != entry.multiDay.prefix(2).last?.id {
                        Divider()
                    }
                }
            }
            .padding(14)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Large (.systemLarge)  ·  Detailkarten für heute + morgen
// ═══════════════════════════════════════════════════════════════════════════════

struct LargeWidgetView: View {
    let entry: TimetableEntry

    var body: some View {
        if entry.multiDay.isEmpty {
            noClassesView(compact: false)
        } else {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(entry.multiDay.prefix(2)) { day in
                    DaySectionView(day: day)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
        }
    }
}

/// Tagesabschnitt für das Large-Widget: Kopfzeile + vollständige Detailkarten.
private struct DaySectionView: View {
    let day: WidgetDayInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Tag-Kopfzeile mit Datumsstring
            HStack {
                Text(day.localizedDayLabel)
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(.primary)
                Spacer()
                if let parsed = day.parsedDate {
                    Text(parsed, format: .dateTime.day().month(.abbreviated))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.bottom, 2)

            // Vorlesungskarten (maximal 5 pro Tag – für Large realistisch)
            ForEach(day.classes.prefix(5)) { lecture in
                ClassRowView(lecture: lecture, compact: false)
            }

            if day.classes.count > 5 {
                Text("+\(day.classes.count - 5) weitere")
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
    let lecture: WidgetClassInfo
    let compact: Bool

    private var accentColor: Color {
        if lecture.isTest    { return .red }
        if lecture.isOngoing { return .accentColor }
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
                    Text("\(lecture.startTime)–\(lecture.endTime)")
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
                        Text("KLAUSUR")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.red)
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
        Text(compact ? "Keine Vorlesungen" : "Keine bevorstehenden Vorlesungen")
            .font(compact ? .caption : .callout)
            .multilineTextAlignment(.center)
            .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
}

// ═══════════════════════════════════════════════════════════════════════════════
// MARK: – Hilfserweiterungen
// ═══════════════════════════════════════════════════════════════════════════════

extension WidgetDayInfo {
    /// Gibt „Heute", „Morgen" oder „Mo, 12.03." zurück (auf Deutsch).
    var localizedDayLabel: String {
        guard let date = parsedDate else { return self.date }
        if Calendar.current.isDateInToday(date)     { return "Heute" }
        if Calendar.current.isDateInTomorrow(date)  { return "Morgen" }
        let fmt = DateFormatter()
        fmt.dateFormat = "EEE, dd.MM."
        fmt.locale = Locale(identifier: "de_DE")
        return fmt.string(from: date)
    }
}

