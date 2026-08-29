// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import Foundation
import Shared

/// Kotlin dates as Foundation dates.
///
/// The Compose UI formats with its own weekday and month string resources; here the system does
/// it, so the timetable follows whatever the user set in Settings — 24-hour clock, first day of
/// the week, and the German or English month names iOS already ships.
extension Kotlinx_datetimeLocalDateTime {

    /// The same wall-clock time, read in the device's current time zone.
    ///
    /// A `LocalDateTime` has no offset; Dualis states lecture times in local time and the phone is
    /// in that time zone, so interpreting them there is the same "10:00" the portal shows.
    var foundationDate: Date {
        var components = DateComponents()
        components.year = Int(year)
        components.month = Int(month.ordinal) + 1
        components.day = Int(day)
        components.hour = Int(hour)
        components.minute = Int(minute)
        return Calendar.current.date(from: components) ?? Date()
    }

    /// `10:00`, in the device's clock format.
    var timeText: String {
        foundationDate.formatted(date: .omitted, time: .shortened)
    }

    /// `Mon, 13 Oct`.
    var dayHeaderText: String {
        foundationDate.formatted(
            Date.FormatStyle().weekday(.abbreviated).day().month(.abbreviated)
        )
    }

    /// `13 Oct 2025`.
    var dateText: String {
        foundationDate.formatted(date: .abbreviated, time: .omitted)
    }
}

/// `13 – 17 Oct`, the title over a timetable week.
func weekRangeText(start: Kotlinx_datetimeLocalDateTime?, end: Kotlinx_datetimeLocalDateTime?) -> String? {
    guard let start, let end else { return nil }
    let style = Date.FormatStyle().day().month(.abbreviated)
    return "\(start.foundationDate.formatted(style)) – \(end.foundationDate.formatted(style))"
}
