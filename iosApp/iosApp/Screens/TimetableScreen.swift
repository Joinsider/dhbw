// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// How a week is drawn.
///
/// Persisted, because it is a preference rather than a navigation state: whoever reads the week
/// as a grid wants it as a grid tomorrow too. `@AppStorage` and not the settings store — no other
/// platform has this choice, so putting it in `SettingsState` would export an iOS-only field to
/// Android and Desktop.
enum TimetableLayout: String {
    case grid
    case list

    var symbol: String { self == .grid ? "list.bullet" : "square.grid.3x3.topleft.filled" }
    var switchLabel: LocalizedStringKey { self == .grid ? "timetable.showList" : "timetable.showGrid" }
}

/// The weekly timetable.
///
/// One horizontally paged scroll view over week offsets, the way the Compose pager works, so the
/// store sees the same `WeekFocused` intents on both platforms and keeps loading exactly one week
/// ahead. The range is finite rather than the pager's fake-infinite 2001 pages: a `LazyHStack`
/// only builds the pages it shows, and two years in each direction is more than Dualis serves.
struct TimetableScreen: View {

    @Environment(AppModel.self) private var model
    @AppStorage("timetableLayout") private var layoutRaw = TimetableLayout.grid.rawValue
    @State private var focusedOffset = 0
    @State private var refreshError: String?

    /// A year in each direction. `TabView` keeps a handful of pages alive around the current one,
    /// so the range costs nothing until it is scrolled through.
    private static let offsets = Array(-52...52)

    private var layout: TimetableLayout { TimetableLayout(rawValue: layoutRaw) ?? .grid }
    private var state: TimetableState { model.timetable.state }
    private var week: WeekState { state.week(offset: Int32(focusedOffset)) }

    var body: some View {
        VStack(spacing: 0) {
            weekBar
            Divider()
            pages
        }
        .navigationTitle("timetable.title")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    layoutRaw = (layout == .grid ? TimetableLayout.list : .grid).rawValue
                } label: {
                    Label(layout.switchLabel, systemImage: layout.symbol)
                }
                .accessibilityIdentifier("timetableLayoutButton")
            }
        }
        .onChange(of: focusedOffset) { _, new in
            model.timetable.dispatch(TimetableIntentWeekFocused(offset: Int32(new)))
        }
        .task {
            // The first page never "changes", so the store would otherwise never hear about it.
            model.timetable.dispatch(TimetableIntentWeekFocused(offset: Int32(focusedOffset)))
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

    /// Week range with a step button on either side.
    ///
    /// The buttons exist next to the swipe because a swipe is invisible: nothing on the screen
    /// said the week could be changed at all, and on the grid the horizontal gesture competes
    /// with the columns.
    private var weekBar: some View {
        HStack(spacing: 8) {
            Button { step(-1) } label: {
                Image(systemName: "chevron.left").font(.body.weight(.semibold))
            }
            .accessibilityLabel(Text("timetable.previousWeek"))
            .accessibilityIdentifier("timetablePreviousWeek")

            Spacer(minLength: 0)

            VStack(spacing: 1) {
                Text(weekRangeText(start: week.start, end: week.end) ?? " ")
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                if focusedOffset != 0 {
                    Button("timetable.today") { withAnimation { focusedOffset = 0 } }
                        .font(.caption2.weight(.semibold))
                        .buttonStyle(.plain)
                        .foregroundStyle(Color.brand)
                        .accessibilityIdentifier("timetableTodayButton")
                } else {
                    Text("timetable.thisWeek")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer(minLength: 0)

            Button { step(1) } label: {
                Image(systemName: "chevron.right").font(.body.weight(.semibold))
            }
            .accessibilityLabel(Text("timetable.nextWeek"))
            .accessibilityIdentifier("timetableNextWeek")
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
        .background(.bar)
    }

    /// A paging `TabView` rather than a `ScrollView` with `.scrollPosition`.
    ///
    /// The scroll-view version drifted: with the pages built lazily, every relayout — a week finishing
    /// its load, the sheet closing — could leave the scroll offset between two pages, and the
    /// binding then reported a week nobody had navigated to. A `TabView` selection is the page,
    /// not a position that has to be rounded back into one.
    private var pages: some View {
        TabView(selection: $focusedOffset) {
            ForEach(Self.offsets, id: \.self) { offset in
                WeekPage(week: state.week(offset: Int32(offset)), offset: offset, layout: layout)
                    .tag(offset)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
    }

    private func step(_ delta: Int) {
        let target = focusedOffset + delta
        guard Self.offsets.contains(target) else { return }
        withAnimation { focusedOffset = target }
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

/// One week, in whichever layout is selected.
private struct WeekPage: View {

    @Environment(AppModel.self) private var model
    let week: WeekState
    let offset: Int
    let layout: TimetableLayout

    var body: some View {
        Group {
            switch layout {
            case .grid:
                TimetableGrid(
                    week: week,
                    onLecture: { model.timetable.dispatch(TimetableIntentLectureOpened(lecture: $0)) },
                    onRefresh: { model.timetable.dispatch(TimetableIntentRefresh(offset: Int32(offset))) }
                )
            case .list:
                agenda
            }
        }
        .accessibilityIdentifier("timetableWeek\(offset)")
    }

    private var agenda: some View {
        List {
            if week.isLoading && week.lectures.isEmpty {
                HStack {
                    ProgressView()
                    Text("timetable.loadingWeek").foregroundStyle(.secondary)
                }
            } else if week.lectures.isEmpty {
                ContentUnavailableView(
                    "timetable.emptyTitle",
                    systemImage: "calendar.badge.exclamationmark",
                    description: Text("timetable.emptyDescription")
                )
            } else {
                ForEach(week.days, id: \.key) { day in
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
    }
}

extension WeekState {

    /// Lectures grouped by calendar day, in order.
    ///
    /// Grouped here and not in the store: it is a presentation decision, and the grid below reads
    /// the same list a different way.
    var days: [(key: String, title: String, lectures: [Lecture])] {
        let grouped = Dictionary(grouping: lectures) { lecture in
            "\(lecture.start.year)-\(lecture.start.month.ordinal)-\(lecture.start.day)"
        }
        return grouped.keys.sorted().compactMap { key in
            guard let dayLectures = grouped[key]?.sorted(by: { $0.start.compareTo(other: $1.start) < 0 }),
                  let first = dayLectures.first else { return nil }
            return (key: key, title: first.start.dayHeaderText, lectures: dayLectures)
        }
    }
}

struct LectureRow: View {

    let lecture: Lecture

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading) {
                Text(lecture.start.timeText).font(.callout).monospacedDigit()
                Text(lecture.end.timeText).font(.caption).foregroundStyle(.secondary).monospacedDigit()
            }
            .frame(width: 56, alignment: .leading)

            RoundedRectangle(cornerRadius: 2)
                .fill(lecture.isTest ? Color.orange : Color.brand)
                .frame(width: 3)
                .accessibilityHidden(true)

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

struct LectureDetailSheet: View {

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
