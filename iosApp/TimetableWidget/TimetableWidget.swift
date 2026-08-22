// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import WidgetKit
import SwiftUI
import Shared

/// The timeline entry: whatever Kotlin last read out of the shared database.
///
/// There are no model types in this file any more. Until P8 the extension held Swift `Codable`
/// mirrors of the Kotlin DTOs and decoded them out of a JSON blob the app wrote into the App
/// Group's `NSUserDefaults` on every database change. The extension now links `Shared.framework`
/// and reads the database in the App Group container directly — the same one the app writes, in
/// place since P6 — so `WidgetLecture` and `WidgetDay` come from `WidgetSnapshot.kt` and cannot
/// drift out of step with it.
struct TimetableEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct TimetableProvider: TimelineProvider {

    /// Shown while the real snapshot loads, and in the widget gallery.
    func placeholder(in context: Context) -> TimetableEntry {
        TimetableEntry(date: .now, snapshot: .previewTwoDays)
    }

    func getSnapshot(in context: Context, completion: @escaping (TimetableEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
        } else {
            loadEntry(completion)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TimetableEntry>) -> Void) {
        loadEntry { entry in
            completion(Timeline(entries: [entry], policy: .after(nextRefreshDate(for: entry))))
        }
    }

    /// Kotlin answers on a background thread; WidgetKit accepts its completions from any of them.
    private func loadEntry(_ completion: @escaping (TimetableEntry) -> Void) {
        WidgetSnapshotProvider.shared.load { snapshot in
            completion(TimetableEntry(date: .now, snapshot: snapshot))
        }
    }

    /// When to ask again:
    /// - a lecture is running → when it ends, so the card stops saying "now"
    /// - one is coming up → when it starts, for the same reason
    /// - nothing today → 7 in the morning, and at most an hour from now either way
    private func nextRefreshDate(for entry: TimetableEntry) -> Date {
        if let lecture = entry.snapshot.upNext {
            if lecture.isOngoing, lecture.end > .now { return lecture.end }
            if lecture.start > .now { return lecture.start }
            if lecture.end > .now { return lecture.end }
        }
        let nextMorning = Calendar.current.nextDate(
            after: .now,
            matching: DateComponents(hour: 7, minute: 0),
            matchingPolicy: .nextTime
        ) ?? Date().addingTimeInterval(8 * 3600)
        return min(nextMorning, Date().addingTimeInterval(60 * 60))
    }
}

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

// ── Vorschau-Daten ────────────────────────────────────────────────────────────
// Die Typen kommen aus Kotlin; nur diese Beispielwerte stehen hier, damit die
// SwiftUI-Vorschauen ohne Datenbank laufen.

extension WidgetLecture {
    static var previewRunning: WidgetLecture {
        WidgetLecture(name: "Mathematik 1", shortName: "MATHE",
                      startText: "08:15", endText: "11:30",
                      location: "HOR-120", isTest: false, isOngoing: true,
                      start: Date().addingTimeInterval(-3600), end: Date().addingTimeInterval(1800))
    }
    static var previewNext: WidgetLecture {
        WidgetLecture(name: "Programmierung", shortName: "PROG",
                      startText: "13:00", endText: "16:00",
                      location: "HOR-231", isTest: false, isOngoing: false,
                      start: Date().addingTimeInterval(5400), end: Date().addingTimeInterval(9000))
    }
    static var previewExam: WidgetLecture {
        WidgetLecture(name: "Klausur Analysis", shortName: "KLSR",
                      startText: "09:00", endText: "11:00",
                      location: "HOR-Aula", isTest: true, isOngoing: false,
                      start: Date().addingTimeInterval(86400), end: Date().addingTimeInterval(86400 + 7200))
    }
}

extension WidgetSnapshot {
    private static var twoDays: [WidgetDay] {
        [
            WidgetDay(date: Calendar.current.startOfDay(for: .now),
                      lectures: [.previewRunning, .previewNext]),
            WidgetDay(date: Calendar.current.startOfDay(for: Date().addingTimeInterval(86400)),
                      lectures: [.previewExam]),
        ]
    }

    static var previewTwoDays: WidgetSnapshot {
        WidgetSnapshot(upNext: .previewRunning, upNextIsRunning: true, days: twoDays)
    }
    static var previewComingUp: WidgetSnapshot {
        WidgetSnapshot(upNext: .previewNext, upNextIsRunning: false, days: twoDays)
    }
    static var previewNoMoreToday: WidgetSnapshot {
        WidgetSnapshot(upNext: nil, upNextIsRunning: false, days: twoDays)
    }
    static var previewEmpty: WidgetSnapshot {
        WidgetSnapshot(upNext: nil, upNextIsRunning: false, days: [])
    }
}

// ── Vorschauen ────────────────────────────────────────────────────────────────

#Preview("Small – Läuft gerade", as: .systemSmall) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewTwoDays)
}

#Preview("Small – Nächste Vorlesung", as: .systemSmall) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewComingUp)
}

#Preview("Small – Keine Vorlesungen", as: .systemSmall) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewEmpty)
}

#Preview("Medium – Zwei Tage", as: .systemMedium) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewTwoDays)
}

#Preview("Medium – Keine Daten", as: .systemMedium) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewEmpty)
}

#Preview("Large – Zwei Tage", as: .systemLarge) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewTwoDays)
}

#Preview("Large – Klausur morgen", as: .systemLarge) {
    TimetableWidget()
} timeline: {
    TimetableEntry(date: .now, snapshot: .previewNoMoreToday)
}
