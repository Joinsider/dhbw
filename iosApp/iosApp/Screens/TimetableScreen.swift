// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// The weekly timetable.
///
/// One horizontally paged scroll view over week offsets, the way the Compose pager works, so the
/// store sees the same `WeekFocused` intents on both platforms and keeps loading exactly one week
/// ahead. The range is finite rather than the pager's fake-infinite 2001 pages: a `LazyHStack`
/// only builds the pages it shows, and two years in each direction is more than Dualis serves.
struct TimetableScreen: View {

    @Environment(AppModel.self) private var model
    @State private var focusedOffset: Int? = 0
    @State private var refreshError: String?

    private static let offsets = Array(-104...104)

    private var state: TimetableState { model.timetable.state }
    private var week: WeekState { state.week(offset: Int32(focusedOffset ?? 0)) }

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(Self.offsets, id: \.self) { offset in
                    WeekPage(week: state.week(offset: Int32(offset)), offset: offset)
                        .containerRelativeFrame(.horizontal)
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollIndicators(.hidden)
        .scrollPosition(id: $focusedOffset)
        .navigationTitle(weekRangeText(start: week.start, end: week.end) ?? String(localized: "timetable.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    withAnimation { focusedOffset = 0 }
                } label: {
                    Text("timetable.today")
                }
                .disabled((focusedOffset ?? 0) == 0)
                .accessibilityIdentifier("timetableTodayButton")
            }
        }
        .onChange(of: focusedOffset) { _, new in
            model.timetable.dispatch(TimetableIntentWeekFocused(offset: Int32(new ?? 0)))
        }
        .task {
            // The first page never "changes", so the store would otherwise never hear about it.
            model.timetable.dispatch(TimetableIntentWeekFocused(offset: Int32(focusedOffset ?? 0)))
            model.timetable.onEffect { effect in
                if let failed = effect as? TimetableEffectRefreshFailed {
                    refreshError = failed.error.userMessage
                }
            }
        }
        .sheet(isPresented: lectureSheetBinding) {
            if let lecture = state.selectedLecture {
                LectureDetailSheet(lecture: lecture)
                    .presentationDetents([.medium])
                    .presentationDragIndicator(.visible)
            }
        }
        .alert(
            Text("common.error"),
            isPresented: Binding(get: { refreshError != nil }, set: { if !$0 { refreshError = nil } })
        ) {
            Button("common.ok", role: .cancel) { refreshError = nil }
        } message: {
            Text(refreshError ?? "")
        }
    }

    private var lectureSheetBinding: Binding<Bool> {
        Binding(
            get: { state.selectedLecture != nil },
            set: { shown in
                if !shown { model.timetable.dispatch(TimetableIntentLectureDismissed()) }
            }
        )
    }
}

/// One week: the lectures grouped by day, or why there are none.
private struct WeekPage: View {

    @Environment(AppModel.self) private var model
    let week: WeekState
    let offset: Int

    var body: some View {
        List {
            if week.isLoading && week.lectures.isEmpty {
                loadingRow
            } else if week.lectures.isEmpty {
                emptyRow
            } else {
                ForEach(days, id: \.key) { day in
                    Section(day.title) {
                        ForEach(day.lectures, id: \.id) { lecture in
                            Button {
                                model.timetable.dispatch(TimetableIntentLectureOpened(lecture: lecture))
                            } label: {
                                LectureRow(lecture: lecture)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            if let error = week.error {
                Section {
                    Text(error.userMessage)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("timetableError")
                }
            }
        }
        .listStyle(.insetGrouped)
        .refreshable {
            model.timetable.dispatch(TimetableIntentRefresh(offset: Int32(offset)))
        }
        .accessibilityIdentifier("timetableWeek\(offset)")
    }

    private var loadingRow: some View {
        HStack {
            ProgressView()
            Text("timetable.loadingWeek").foregroundStyle(.secondary)
        }
    }

    private var emptyRow: some View {
        ContentUnavailableView(
            "timetable.emptyTitle",
            systemImage: "calendar.badge.exclamationmark",
            description: Text("timetable.emptyDescription")
        )
    }

    /// Lectures grouped by calendar day, in order.
    ///
    /// Grouped here and not in the store: it is a presentation decision, and the Compose UI groups
    /// the same list its own way for a layout that has no sections.
    private var days: [(key: String, title: String, lectures: [Lecture])] {
        let grouped = Dictionary(grouping: week.lectures) { lecture in
            "\(lecture.start.year)-\(lecture.start.month.ordinal)-\(lecture.start.day)"
        }
        return grouped.keys.sorted().compactMap { key in
            guard let lectures = grouped[key]?.sorted(by: { $0.start.compareTo(other: $1.start) < 0 }),
                  let first = lectures.first else { return nil }
            return (key: key, title: first.start.dayHeaderText, lectures: lectures)
        }
    }
}

private struct LectureRow: View {

    let lecture: Lecture

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading) {
                Text(lecture.start.timeText).font(.callout).monospacedDigit()
                Text(lecture.end.timeText).font(.caption).foregroundStyle(.secondary).monospacedDigit()
            }
            .frame(width: 56, alignment: .leading)

            VStack(alignment: .leading, spacing: 2) {
                Text(lecture.displayName).font(.body)
                if !lecture.location.isEmpty {
                    Text(lecture.location).font(.caption).foregroundStyle(.secondary)
                }
                if !lecture.lecturers.isEmpty {
                    Text(lecture.lecturers.joined(separator: ", "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()

            if lecture.isTest {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
                    .accessibilityLabel(Text("timetable.exam"))
            }
        }
        .contentShape(Rectangle())
        // One label per row, so VoiceOver reads a lecture as a sentence instead of six fragments.
        .accessibilityElement(children: .combine)
    }
}

private struct LectureDetailSheet: View {

    @Environment(\.dismiss) private var dismiss
    let lecture: Lecture

    var body: some View {
        NavigationStack {
            List {
                LabeledContent("timetable.subject", value: lecture.displayName)
                LabeledContent("timetable.date", value: lecture.start.dateText)
                LabeledContent("timetable.start", value: lecture.start.timeText)
                LabeledContent("timetable.end", value: lecture.end.timeText)
                if !lecture.location.isEmpty {
                    LabeledContent("timetable.room", value: lecture.location)
                }
                if !lecture.lecturers.isEmpty {
                    LabeledContent("timetable.lecturers", value: lecture.lecturers.joined(separator: ", "))
                }
                if lecture.isTest {
                    Label("timetable.exam", systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                }
            }
            .navigationTitle("timetable.details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.close") { dismiss() }
                }
            }
        }
    }
}
