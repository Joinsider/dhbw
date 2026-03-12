// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import WidgetKit
import SwiftUI

// ── Konstanten ────────────────────────────────────────────────────────────────

/// App-Group-Bezeichner – muss mit dem Wert in den Entitlements beider Targets übereinstimmen.
let kAppGroupSuite  = "group.de.fampopprol.dhbwhorb"
let kUpNextKey      = "widget_up_next"
let kMultiDayKey    = "widget_multi_day"

// ── Swift-seitige Codable-Spiegel der KMP-DTOs ────────────────────────────────
// Feldnamen und Diskriminatorwerte müssen mit WidgetSerializableModels.kt übereinstimmen.

struct WidgetClassInfo: Codable, Identifiable {
    let name: String
    let shortName: String
    let startTime: String       // "HH:mm"
    let endTime: String         // "HH:mm"
    let location: String
    let isTest: Bool
    let isOngoing: Bool
    let startEpoch: Double
    let endEpoch: Double

    /// Stable identity für `ForEach` – Kombination aus Epoch-Sekunden.
    var id: Double { startEpoch }

    var startDate: Date { Date(timeIntervalSince1970: startEpoch) }
    var endDate:   Date { Date(timeIntervalSince1970: endEpoch) }
}

struct WidgetDayInfo: Codable, Identifiable {
    /// ISO-8601 Datums-String `"YYYY-MM-DD"`.
    let date: String
    let classes: [WidgetClassInfo]

    var id: String { date }

    /// `Date`-Objekt für Datumsvergleiche (z. B. `isDateInToday`).
    var parsedDate: Date? {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.date(from: date)
    }
}

/// Spiegelt `WidgetUpNextDto` aus Kotlin – das `type`-Feld ist der Diskriminator.
struct UpNextInfo: Codable {
    enum UpNextType: String, Codable {
        case currentlyRunning = "currently_running"
        case comingUp         = "coming_up"
        case noMoreToday      = "no_more_today"
    }
    let type: UpNextType
    let lecture: WidgetClassInfo?
}

// ── TimelineEntry ─────────────────────────────────────────────────────────────

struct TimetableEntry: TimelineEntry {
    let date: Date
    let upNext:   UpNextInfo?
    let multiDay: [WidgetDayInfo]

    /// Keine Daten verfügbar (App noch nie gestartet oder nicht eingeloggt).
    var hasData: Bool { upNext != nil || !multiDay.isEmpty }
}

// ── TimelineProvider ──────────────────────────────────────────────────────────

struct TimetableProvider: TimelineProvider {

    private var sharedDefaults: UserDefaults? { UserDefaults(suiteName: kAppGroupSuite) }

    // Snapshot beim Hinzufügen des Widgets zur Ansicht
    func placeholder(in context: Context) -> TimetableEntry {
        TimetableEntry(date: .now, upNext: .previewCurrentlyRunning, multiDay: .previewTwoDays)
    }

    // Snapshot für die Widget-Galerie
    func getSnapshot(in context: Context, completion: @escaping (TimetableEntry) -> Void) {
        completion(context.isPreview ? placeholder(in: context) : buildEntry())
    }

    // Timeline – ein einzelner Eintrag; Refresh-Datum wird aus Vorlesungszeiten berechnet
    func getTimeline(in context: Context, completion: @escaping (Timeline<TimetableEntry>) -> Void) {
        let entry = buildEntry()
        completion(Timeline(entries: [entry], policy: .after(nextRefreshDate(for: entry))))
    }

    // ── Private Hilfsmethoden ─────────────────────────────────────────────────

    private func buildEntry() -> TimetableEntry {
        TimetableEntry(
            date:     .now,
            upNext:   loadUpNext(),
            multiDay: loadMultiDay()
        )
    }

    private func loadUpNext() -> UpNextInfo? {
        guard
            let json = sharedDefaults?.string(forKey: kUpNextKey),
            let data = json.data(using: .utf8)
        else { return nil }
        return try? JSONDecoder().decode(UpNextInfo.self, from: data)
    }

    private func loadMultiDay() -> [WidgetDayInfo] {
        guard
            let json = sharedDefaults?.string(forKey: kMultiDayKey),
            let data = json.data(using: .utf8)
        else { return [] }
        return (try? JSONDecoder().decode([WidgetDayInfo].self, from: data)) ?? []
    }

    /// Berechnet das nächste Refresh-Datum:
    /// - Läuft gerade eine Vorlesung → am Ende dieser Vorlesung
    /// - Steht die nächste bevor → zum Startzeitpunkt (damit Status auf „läuft" wechselt)
    /// - Keine Daten / alles vorbei → 7 Uhr morgen früh
    private func nextRefreshDate(for entry: TimetableEntry) -> Date {
        if let lecture = entry.upNext?.lecture {
            let startDate = lecture.startDate
            let endDate   = lecture.endDate
            if lecture.isOngoing, endDate > .now   { return endDate }
            if startDate > .now                    { return startDate }
            if endDate > .now                      { return endDate }
        }
        // Fallback: 7 Uhr morgen früh, maximal aber in 60 Minuten
        let nextMorning = Calendar.current.nextDate(
            after: .now,
            matching: DateComponents(hour: 7, minute: 0),
            matchingPolicy: .nextTime
        ) ?? Date().addingTimeInterval(8 * 3600)
        return min(nextMorning, Date().addingTimeInterval(60 * 60))
    }
}

// ── Widget-Konfiguration ──────────────────────────────────────────────────────

struct TimetableWidget: Widget {
    let kind: String = "de.fampopprol.dhbwhorb.TimetableWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: TimetableProvider()) { entry in
            TimetableWidgetEntryView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("DHBW Stundenplan")
        .description("Zeigt deine nächsten Vorlesungen.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// ── Entry-View-Dispatcher ─────────────────────────────────────────────────────

struct TimetableWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: TimetableEntry

    var body: some View {
        switch family {
        case .systemSmall:  SmallWidgetView(entry: entry)
        case .systemMedium: MediumWidgetView(entry: entry)
        case .systemLarge:  LargeWidgetView(entry: entry)
        default:            SmallWidgetView(entry: entry)
        }
    }
}

// ── Vorschau-Daten (Erweiterungen) ────────────────────────────────────────────

extension UpNextInfo {
    static var previewCurrentlyRunning: UpNextInfo {
        UpNextInfo(type: .currentlyRunning, lecture: .previewRunning)
    }
    static var previewComingUp: UpNextInfo {
        UpNextInfo(type: .comingUp, lecture: .previewNext)
    }
    static var previewNoMore: UpNextInfo {
        UpNextInfo(type: .noMoreToday, lecture: nil)
    }
}

extension WidgetClassInfo {
    static var previewRunning: WidgetClassInfo {
        WidgetClassInfo(name: "Mathematik 1", shortName: "MATHE",
                        startTime: "08:15", endTime: "11:30",
                        location: "HOR-120", isTest: false, isOngoing: true,
                        startEpoch: Date().timeIntervalSince1970 - 3600,
                        endEpoch:   Date().timeIntervalSince1970 + 1800)
    }
    static var previewNext: WidgetClassInfo {
        WidgetClassInfo(name: "Programmierung", shortName: "PROG",
                        startTime: "13:00", endTime: "16:00",
                        location: "HOR-231", isTest: false, isOngoing: false,
                        startEpoch: Date().timeIntervalSince1970 + 5400,
                        endEpoch:   Date().timeIntervalSince1970 + 9000)
    }
    static var previewExam: WidgetClassInfo {
        WidgetClassInfo(name: "Klausur Analysis", shortName: "KLSR",
                        startTime: "09:00", endTime: "11:00",
                        location: "HOR-Aula", isTest: true, isOngoing: false,
                        startEpoch: Date().timeIntervalSince1970 + 86400,
                        endEpoch:   Date().timeIntervalSince1970 + 86400 + 7200)
    }
}

extension Array where Element == WidgetDayInfo {
    static var previewTwoDays: [WidgetDayInfo] {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        let today    = df.string(from: Date())
        let tomorrow = df.string(from: Date().addingTimeInterval(86400))
        return [
            WidgetDayInfo(date: today, classes: [.previewRunning, .previewNext]),
            WidgetDayInfo(date: tomorrow, classes: [.previewExam]),
        ]
    }
    static var previewOneDay: [WidgetDayInfo] {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        return [WidgetDayInfo(date: df.string(from: Date()), classes: [.previewRunning])]
    }
}

// ── Vorschauen ────────────────────────────────────────────────────────────────

#Preview("Small – Läuft gerade", as: .systemSmall) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: .previewCurrentlyRunning, multiDay: .previewTwoDays)
}

#Preview("Small – Nächste Vorlesung", as: .systemSmall) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: .previewComingUp, multiDay: .previewTwoDays)
}

#Preview("Small – Keine Vorlesungen", as: .systemSmall) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: .previewNoMore, multiDay: [])
}

#Preview("Medium – Zwei Tage", as: .systemMedium) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: .previewCurrentlyRunning, multiDay: .previewTwoDays)
}

#Preview("Medium – Keine Daten", as: .systemMedium) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: nil, multiDay: [])
}

#Preview("Large – Zwei Tage", as: .systemLarge) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: .previewCurrentlyRunning, multiDay: .previewTwoDays)
}

#Preview("Large – Klausur morgen", as: .systemLarge) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, upNext: .previewNoMore, multiDay: .previewTwoDays)
}
