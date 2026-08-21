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
        // The column has to know its own width: two lectures at the same time share it, so a
        // block's width is a fraction of it rather than all of it.
        GeometryReader { proxy in
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

                ForEach(placed(on: day), id: \.lecture.id) { block in
                    let laneWidth = proxy.size.width / CGFloat(block.laneCount)
                    LectureBlock(lecture: block.lecture, isNarrow: block.laneCount > 1)
                        .frame(width: laneWidth, height: height(of: block.lecture))
                        .offset(x: laneWidth * CGFloat(block.lane), y: offset(of: block.lecture))
                        .onTapGesture { onLecture(block.lecture) }
                }
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .background(day.isToday ? Color.brandSoft.opacity(0.35) : .clear)
        }
        .frame(height: CGFloat(hours.count) * Self.hourHeight)
    }

    // MARK: - Overlapping lectures

    /// A lecture and which of the parallel columns it gets.
    private struct PlacedLecture {
        let lecture: Lecture
        let lane: Int
        let laneCount: Int
    }

    /// Lays parallel lectures side by side instead of on top of each other.
    ///
    /// Two lectures at the same hour used to be drawn at the same place, and the second one's
    /// text landed on the first one's — unreadable, and it looked like one broken block. The rule
    /// is the one every calendar uses: lectures that overlap in time form a group, each takes the
    /// first lane that is free, and the whole group is as wide as the column divided by the
    /// number of lanes the group needed.
    private func placed(on day: GridDay) -> [PlacedLecture] {
        let ordered = lectures(on: day)
        guard !ordered.isEmpty else { return [] }

        var result: [PlacedLecture] = []
        var group: [(lecture: Lecture, lane: Int)] = []
        var laneEnds: [Int] = []          // when the lecture currently in each lane ends
        var groupEnd = Int.min

        func closeGroup() {
            let laneCount = max(1, laneEnds.count)
            result += group.map { PlacedLecture(lecture: $0.lecture, lane: $0.lane, laneCount: laneCount) }
            group = []
            laneEnds = []
            groupEnd = Int.min
        }

        for lecture in ordered {
            let start = minutes(from: lecture.start)
            let end = minutes(from: lecture.end)

            // A lecture that starts after everything in the group has ended begins a new group,
            // so an afternoon lecture is not squeezed by a clash in the morning.
            if start >= groupEnd { closeGroup() }

            let lane = laneEnds.firstIndex { $0 <= start } ?? laneEnds.count
            if lane < laneEnds.count { laneEnds[lane] = end } else { laneEnds.append(end) }
            group.append((lecture, lane))
            groupEnd = max(groupEnd, end)
        }
        closeGroup()

        return result
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

/// One lecture in the grid. Short by necessity — a column is about 60 pt wide on an iPhone, and
/// half that when two lectures run at the same time.
private struct LectureBlock: View {

    let lecture: Lecture
    let isNarrow: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(lecture.shortName)
                .font(.system(size: isNarrow ? 9 : 11, weight: .semibold))
                .lineLimit(isNarrow ? 3 : 2)
                .minimumScaleFactor(0.8)
            if !lecture.location.isEmpty && !isNarrow {
                Text(lecture.location)
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 3)
        .padding(.vertical, 2)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(lecture.isTest ? Color.examFill : Color.lectureFill, in: .rect(cornerRadius: 6))
        .overlay(alignment: .leading) {
            Rectangle()
                .fill(lecture.isTest ? Color.examEdge : Color.brand)
                .frame(width: 2.5)
                .clipShape(.rect(topLeadingRadius: 6, bottomLeadingRadius: 6))
        }
        .overlay(alignment: .topTrailing) {
            if lecture.isTest {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 8))
                    .foregroundStyle(Color.examEdge)
                    .padding(2)
            }
        }
        .padding(.horizontal, 1)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            (lecture.isTest ? String(localized: "timetable.exam") + ": " : "")
                + "\(lecture.displayName), \(lecture.start.timeText) – \(lecture.end.timeText)"
                + (lecture.location.isEmpty ? "" : ", \(lecture.location)")
        )
        .accessibilityAddTraits(.isButton)
    }
}
