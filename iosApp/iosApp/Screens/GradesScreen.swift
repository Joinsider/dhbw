// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// Every semester's grades, oldest first.
///
/// There is no semester picker any more. The store hands over one list, already in the order the
/// semesters happened in (`SemesterOrder`), and the sections come from `GradesState.sections` —
/// the same grouping the Compose page draws, so the two cannot disagree about what belongs where.
///
/// One view for both platforms. On macOS the list is centred at a readable width by insetting the
/// scroll content, as the Settings pane does, so the whole pane scrolls and the scroll indicator
/// sits at the window edge.
struct GradesScreen: View {

    @Environment(AppModel.self) private var model
    @Environment(\.accentChoice) private var accentChoice
    @State private var searchText = ""
    @State private var refreshError: String?

    #if os(macOS)
    /// The width of the grades column on a Mac, the same as the Settings column.
    private static let columnWidth: CGFloat = 640
    #endif

    private var state: GradesState { model.grades.state }

    /// The store owns which module is open; the sheet only reports being dismissed.
    private var detailsPresented: Binding<Bool> {
        Binding(
            get: { state.selectedModule != nil },
            set: { isPresented in
                if !isPresented { model.grades.dispatch(GradesIntentModuleClosed()) }
            }
        )
    }

    var body: some View {
        content
            .navigationTitle("grades.title")
            .searchable(text: $searchText, prompt: Text("grades.searchPrompt"))
            .refreshable { model.grades.dispatch(GradesIntentRefresh()) }
            .sheet(isPresented: detailsPresented) {
                if let module = state.selectedModule {
                    ModuleDetailsSheet(
                        module: module,
                        entries: state.selectedModuleEntries,
                        details: state.moduleDetails,
                        isLoading: state.isLoadingDetails,
                        error: state.detailsError
                    )
                    .gradeDetailsSizing()
                }
            }
            .task {
                // EnsureLoaded, not Load: the store outlives the tab, and asking for a reload on every
                // appearance is the request storm P4 removed.
                model.grades.dispatch(GradesIntentEnsureLoaded())
                model.grades.onEffect { effect in
                    if let failed = effect as? GradesEffectRefreshFailed {
                        refreshError = failed.error.userMessage
                    }
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
            .accessibilityIdentifier("gradesScreen")
    }

    @ViewBuilder
    private var content: some View {
        #if os(macOS)
        GeometryReader { proxy in
            gradeList
                .contentMargins(
                    .horizontal,
                    max(20, (proxy.size.width - Self.columnWidth) / 2),
                    for: .scrollContent
                )
        }
        #else
        gradeList
        #endif
    }

    private var gradeList: some View {
        List {
            if state.requiresLogin {
                ContentUnavailableView(
                    "grades.loginRequiredTitle",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: Text("grades.loginRequired")
                )
            } else {
                if let error = state.error {
                    Section {
                        Label {
                            Text(error.userMessage)
                                .accessibilityIdentifier("gradesError")
                        } icon: {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(.red)
                        }
                        Button("common.retry") { model.grades.dispatch(GradesIntentLoad()) }
                    }
                }

                if state.isLoading && state.grades.isEmpty {
                    HStack { ProgressView(); Text("common.loading").foregroundStyle(.secondary) }
                } else if visibleSections.isEmpty {
                    // Two different nothings: a search that matched nothing, and a load that
                    // brought nothing.
                    if searchText.isEmpty {
                        if state.error == nil {
                            ContentUnavailableView(
                                "grades.emptyTitle",
                                systemImage: "chart.bar.doc.horizontal",
                                description: Text("grades.empty")
                            )
                        }
                    } else {
                        ContentUnavailableView.search
                    }
                } else {
                    if searchText.isEmpty {
                        summary
                    }
                    ForEach(visibleSections, id: \.semesterName) { section in
                        Section {
                            ForEach(section.grades, id: \.moduleNumber) { entry in
                                // A tap gesture rather than a Button: a Button in these rows
                                // never receives the tap — verified on the simulator, where the
                                // action never ran. The trait keeps VoiceOver told what it is.
                                GradeRow(entry: entry)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        model.grades.dispatch(GradesIntentModuleOpened(entry: entry))
                                    }
                                    .accessibilityAddTraits(.isButton)
                                    .accessibilityIdentifier("gradeRow_\(entry.moduleNumber)")
                            }
                        } header: {
                            SemesterHeader(section: section)
                        }
                    }
                }
            }
        }
        .platformGroupedList()
    }

    /// The degree so far: the overall GPA large, with the credits and the module count beside it.
    private var summary: some View {
        Section {
            HStack(alignment: .center, spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(state.overallGpa.map { $0.doubleValue.formatted(.number.precision(.fractionLength(2))) } ?? "–")
                        .font(.system(size: 44, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(accentChoice.accent)
                    Text("grades.overallGpa")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 12)
                VStack(alignment: .trailing, spacing: 8) {
                    summaryValue(
                        state.totalCreditsEarned.formatted(.number.precision(.fractionLength(0))),
                        label: "grades.creditsEarned"
                    )
                    summaryValue("\(state.modulesCompleted)", label: "grades.modulesCompleted")
                }
            }
            .padding(.vertical, 6)
            .accessibilityElement(children: .combine)
        }
    }

    private func summaryValue(_ value: String, label: LocalizedStringKey) -> some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.callout)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3.weight(.semibold))
                .monospacedDigit()
        }
    }

    /// The store's sections, narrowed by the search field. Sections that lose every module
    /// disappear rather than standing there empty.
    private var visibleSections: [SemesterGrades] {
        guard !searchText.isEmpty else { return state.sections }
        return state.sections.compactMap { section in
            let matches = section.grades.filter {
                $0.moduleName.localizedCaseInsensitiveContains(searchText)
                    || $0.moduleNumber.localizedCaseInsensitiveContains(searchText)
            }
            return matches.isEmpty ? nil : SemesterGrades(semesterName: section.semesterName, grades: matches)
        }
    }
}

// MARK: - Shared pieces

/// A grade in a fixed-width rounded badge. Failing grades (above 4,0 on the German scale) are red;
/// everything else stays neutral, because the state does not say more than that. A module that does
/// not count towards the degree is drawn quieter.
struct GradeBadge: View {

    let grade: String?
    var countsTowardDegree: Bool = true

    var body: some View {
        Text(grade ?? "–")
            .font(.headline)
            .monospacedDigit()
            .frame(width: 56)
            .padding(.vertical, 6)
            .foregroundStyle(foreground)
            .background(fill, in: .rect(cornerRadius: 8))
            .accessibilityHidden(true)
    }

    private var foreground: Color {
        if GradeBadge.isFailing(grade) { return .red }
        return countsTowardDegree ? .primary : .secondary
    }

    private var fill: Color {
        GradeBadge.isFailing(grade) ? Color.red.opacity(0.14) : Color.platformTertiaryFill
    }

    /// `1,7` or `1.7` is passing; `5,0` is not. Anything that does not parse is not called failing.
    static func isFailing(_ grade: String?) -> Bool {
        guard let grade, let value = Double(grade.replacingOccurrences(of: ",", with: ".")) else {
            return false
        }
        return value > 4.0
    }
}

extension View {
    /// The sheet's size: a fitted window on macOS, and medium or full height on iOS.
    @ViewBuilder
    fileprivate func gradeDetailsSizing() -> some View {
        #if os(macOS)
        presentationSizing(.fitted)
        #else
        presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        #endif
    }
}

// MARK: - Rows

/// Semester name on the left, its own average on the right.
private struct SemesterHeader: View {

    @Environment(\.accentChoice) private var accentChoice
    let section: SemesterGrades

    var body: some View {
        HStack {
            Text(section.semesterName)
            Spacer()
            if let average = semesterAverage {
                Text(average.formatted(.number.precision(.fractionLength(2))))
                    .monospacedDigit()
                    .foregroundStyle(accentChoice.accent)
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// `ComputeGpa` for one section, which is what the store does not carry.
    ///
    /// Called rather than reimplemented: this used to be the same loop written a second time in
    /// Swift, and when the shared rule learnt to skip failed attempts and repeated modules, the
    /// header kept averaging them — a semester average that disagreed with the total above it.
    private var semesterAverage: Double? {
        ComputeGpa().invoke(grades: section.grades).average?.doubleValue
    }
}

/// One module: its name over two lines of detail, and the grade in a badge on the right.
private struct GradeRow: View {

    let entry: GradeEntry

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(entry.moduleName)
                    .fixedSize(horizontal: false, vertical: true)
                Text(detailLine)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if let status = entry.status, !status.isEmpty {
                    Text(status)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            GradeBadge(grade: entry.grade, countsTowardDegree: entry.countsTowardDegree)
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }

    /// `05-01-0101 · 6,0 CP`: the module number and what the module is worth.
    private var detailLine: String {
        "\(entry.moduleNumber) · \(entry.credits.formatted(.number.precision(.fractionLength(1)))) CP"
    }
}
