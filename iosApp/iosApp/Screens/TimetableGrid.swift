// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// The week as a calendar: Monday to Friday across, the hours down, every lecture a block whose
/// height is how long it lasts.
///
/// This is the Android layout (`WeeklyLecturesView`) rebuilt in SwiftUI, and it is deliberately
/// the same shape: the hour column on the left, five day columns, blocks placed by time rather
/// than listed. What the list cannot show is the gaps — a free afternoon looks the same as a full
/// one when it is a list of rows.
struct TimetableGrid: View {

    let week: WeekState
    let onLecture: (Lecture) -> Void
    let onRefresh: () -> Void

    /// Same bounds Android uses: at least 08:00–19:00, widened by anything outside it.
    private var startHour: Int {
        min(8, week.lectures.map { Int($0.start.hour) }.min() ?? 8)
    }

    private var endHour: Int {
        // A lecture ending at 19:30 needs the 20:00 line to have something to end against.
        let latest = week.lectures.map { Int($0.end.minute) > 0 ? Int($0.end.hour) + 1 : Int($0.end.hour) }.max() ?? 19
        return max(19, latest)
    }

    private var hours: [Int] { Array(startHour...endHour) }

    private static let hourHeight: CGFloat = 58
    private static let gutterWidth: CGFloat = 40

    var body: some View {
        ScrollView(.vertical) {
            VStack(spacing: 0) {
                header
                grid
            }
        }
        .refreshable { onRefresh() }
        .overlay {
            if week.isLoading && week.lectures.isEmpty {
                banner(Text("timetable.loadingWeek"), showsSpinner: true)
            } else if week.lectures.isEmpty {
                banner(Text("timetable.emptyDescription"), showsSpinner: false)
            } else if let error = week.error {
                banner(Text(error.userMessage), showsSpinner: false)
                    .accessibilityIdentifier("timetableError")
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        HStack(spacing: 0) {
            Color.clear.frame(width: Self.gutterWidth)
            ForEach(days, id: \.weekday) { day in
                VStack(spacing: 1) {
                    Text(day.weekdayText)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(day.isToday ? Color.brand : .secondary)
                    Text(day.dayNumberText)
                        .font(.footnote.weight(day.isToday ? .bold : .regular))
                        .foregroundStyle(day.isToday ? Color.brand : .primary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(day.isToday ? Color.brandSoft : .clear)
                .accessibilityElement(children: .combine)
            }
        }
        .background(.bar)
    }

    // MARK: - Grid

    private var grid: some View {
        HStack(alignment: .top, spacing: 0) {
            hourGutter
            HStack(spacing: 1) {
                ForEach(days, id: \.weekday) { day in
                    dayColumn(day)
                }
            }
        }
        .padding(.bottom, 24)
    }

    private var hourGutter: some View {
        VStack(spacing: 0) {
            ForEach(hours, id: \.self) { hour in
                Text(String(format: "%02d", hour))
                    .font(.caption2)
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
                    .frame(width: Self.gutterWidth, height: Self.hourHeight, alignment: .topTrailing)
                    .padding(.trailing, 4)
                    .offset(y: -6)
            }
        }
        .accessibilityHidden(true)
    }

    private func dayColumn(_ day: GridDay) -> some View {
        ZStack(alignment: .topLeading) {
            // The hour lines, so a block's height can be read against something.
            VStack(spacing: 0) {
                ForEach(hours, id: \.self) { _ in
                    Rectangle()
                        .fill(Color(uiColor: .separator).opacity(0.35))
                        .frame(height: 0.5)
                        .frame(maxHeight: Self.hourHeight, alignment: .top)
                }
            }
            .frame(height: CGFloat(hours.count) * Self.hourHeight, alignment: .top)

            ForEach(lectures(on: day), id: \.id) { lecture in
                LectureBlock(lecture: lecture)
                    .frame(height: height(of: lecture))
                    .offset(y: offset(of: lecture))
                    .onTapGesture { onLecture(lecture) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(day.isToday ? Color.brandSoft.opacity(0.4) : .clear)
    }

    // MARK: - Geometry

    private func offset(of lecture: Lecture) -> CGFloat {
        CGFloat(minutes(from: lecture.start)) / 60 * Self.hourHeight
    }

    private func height(of lecture: Lecture) -> CGFloat {
        let span = minutes(from: lecture.end) - minutes(from: lecture.start)
        // 24 pt is still readable as a block; below that a 15-minute slot becomes a line.
        return max(24, CGFloat(span) / 60 * Self.hourHeight)
    }

    private func minutes(from time: Kotlinx_datetimeLocalDateTime) -> Int {
        (Int(time.hour) - startHour) * 60 + Int(time.minute)
    }

    // MARK: - Days

    private struct GridDay {
        let weekday: Int
        let date: Date?
        let weekdayText: String
        let dayNumberText: String
        let isToday: Bool
    }

    /// Monday to Friday of the shown week.
    ///
    /// The dates come from `WeekState.start` when the week has been loaded, and from the lectures
    /// otherwise; with neither, the columns still stand and only lose their date line — an empty
    /// grid is a better answer than a blank screen.
    private var days: [GridDay] {
        let calendar = Calendar.current
        let monday: Date? = week.start?.foundationDate
            ?? week.lectures.map(\.start.foundationDate).min().map {
                calendar.date(from: calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: $0))
            } ?? nil

        return (0..<5).map { index in
            let date = monday.flatMap { calendar.date(byAdding: .day, value: index, to: $0) }
            return GridDay(
                weekday: index,
                date: date,
                weekdayText: date?.formatted(Date.FormatStyle().weekday(.abbreviated))
                    ?? Self.fallbackWeekdays[index],
                dayNumberText: date?.formatted(Date.FormatStyle().day().month(.defaultDigits)) ?? "",
                isToday: date.map { calendar.isDateInToday($0) } ?? false
            )
        }
    }

    private static let fallbackWeekdays = ["Mo", "Di", "Mi", "Do", "Fr"]

    private func lectures(on day: GridDay) -> [Lecture] {
        guard let date = day.date else { return [] }
        let calendar = Calendar.current
        return week.lectures
            .filter { calendar.isDate($0.start.foundationDate, inSameDayAs: date) }
            .sorted { $0.start.compareTo(other: $1.start) < 0 }
    }

    private func banner(_ text: Text, showsSpinner: Bool) -> some View {
        VStack(spacing: 8) {
            if showsSpinner { ProgressView() }
            text
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: .rect(cornerRadius: 14))
        .padding(24)
    }
}

/// One lecture in the grid. Short by necessity — a column is about 60 pt wide on an iPhone.
private struct LectureBlock: View {

    let lecture: Lecture

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(lecture.shortName)
                .font(.caption2.weight(.semibold))
                .lineLimit(2)
            if !lecture.location.isEmpty {
                Text(lecture.location)
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 3)
        .padding(.vertical, 2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(lecture.isTest ? Color.orange.opacity(0.22) : Color.brandSoft, in: .rect(cornerRadius: 6))
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(lecture.isTest ? Color.orange : Color.brand)
                .frame(width: 2.5)
                .clipShape(.rect(topLeadingRadius: 6, bottomLeadingRadius: 6))
        }
        .padding(.horizontal, 1)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "\(lecture.displayName), \(lecture.start.timeText) – \(lecture.end.timeText)"
                + (lecture.location.isEmpty ? "" : ", \(lecture.location)")
        )
        .accessibilityAddTraits(.isButton)
    }
}
