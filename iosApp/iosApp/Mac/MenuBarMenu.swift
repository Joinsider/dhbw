// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if os(macOS)
import AppKit
import Shared
import SwiftUI

/// The menu bar item's icon. It is rendered for as long as the item is inserted, which is what makes
/// it the place that can open the window: it listens for `MacWindow.openRequest` from the AppDelegate
/// (reopen while hidden) and opens the window through the environment's `openWindow`.
struct MenuBarLabel: View {

    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Image(systemName: "graduationcap")
            .onReceive(NotificationCenter.default.publisher(for: MacWindow.openRequest)) { _ in
                MacWindow.show(openWindow)
            }
    }
}

/// The menu bar item shown in background mode.
///
/// It reads the same timetable store as the window, so the "next lecture" line is whatever the
/// timetable already knows. Opening the menu asks the store for the current week; the store answers
/// from its cache first, so this does not hit Dualis when the week is already there.
struct MenuBarMenu: View {

    @Environment(AppModel.self) private var model
    @Environment(\.openWindow) private var openWindow

    var body: some View {
        Group {
            Text(verbatim: nextLectureLine)
            Text(verbatim: lastCheckLine)
            Divider()
            Button("menu.openTimetable") { MacWindow.show(openWindow) }
            Button("menu.refresh") {
                model.timetable.dispatch(TimetableIntentRefresh(offset: 0))
            }
            Divider()
            Button("menu.quit") { NSApp.terminate(nil) }
                .keyboardShortcut("q")
        }
        .onAppear {
            model.timetable.dispatch(TimetableIntentWeekFocused(offset: 0))
        }
    }

    /// `Letzte Prüfung: heute, 14:03`, with the failure after it when the last run failed.
    private var lastCheckLine: String {
        let label = String(localized: "settings.lastCheck")
        guard let check = model.settings.state.lastLectureCheck else {
            return "\(label): \(String(localized: "settings.lastCheckNever"))"
        }
        let line = "\(label): \(LectureCheckText.when(check))"
        guard let failure = LectureCheckText.failure(check) else { return line }
        return "\(line) · \(failure)"
    }

    /// The first lecture that starts today or tomorrow, or a line saying there is none.
    private var nextLectureLine: String {
        let now = Date()
        let calendar = Calendar.current
        let next = [0, 1]
            .flatMap { model.timetable.state.week(offset: Int32($0)).lectures }
            .filter { lecture in
                let start = lecture.start.foundationDate
                return start >= now && (calendar.isDateInToday(start) || calendar.isDateInTomorrow(start))
            }
            .min { $0.start.foundationDate < $1.start.foundationDate }

        guard let next else { return String(localized: "menu.noLectureSoon") }
        let day = calendar.isDateInToday(next.start.foundationDate)
            ? String(localized: "menu.today")
            : String(localized: "menu.tomorrow")
        return "\(day), \(next.start.timeText) · \(next.displayName)"
    }
}
#endif
