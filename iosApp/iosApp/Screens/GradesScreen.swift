// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// Grades, either for one semester or combined.
struct GradesScreen: View {

    @Environment(AppModel.self) private var model
    @State private var searchText = ""
    @State private var refreshError: String?

    private var state: GradesState { model.grades.state }

    var body: some View {
        List {
            if state.requiresLogin {
                ContentUnavailableView(
                    "grades.loginRequiredTitle",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: Text("grades.loginRequired")
                )
            } else {
                if !state.semesters.isEmpty {
                    semesterPicker
                }
                if let error = state.error {
                    Section {
                        Text(error.userMessage)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("gradesError")
                        Button("common.retry") { model.grades.dispatch(GradesIntentLoad()) }
                    }
                }
                if state.isLoading && state.grades.isEmpty {
                    HStack { ProgressView(); Text("common.loading").foregroundStyle(.secondary) }
                } else if visibleGrades.isEmpty {
                    // Two different nothings: a search that matched nothing, and a load that
                    // brought nothing. Showing "check the spelling" for the second one — which is
                    // what demo mode produces, because Dualis grades have no demo data — reads as
                    // if the user had mistyped something.
                    if searchText.isEmpty {
                        ContentUnavailableView(
                            "grades.emptyTitle",
                            systemImage: "chart.bar.doc.horizontal",
                            description: Text("grades.empty")
                        )
                    } else {
                        ContentUnavailableView.search
                    }
                } else {
                    statisticsSection
                    ForEach(sections, id: \.title) { section in
                        Section(section.title) {
                            ForEach(section.entries, id: \.moduleNumber) { entry in
                                GradeRow(entry: entry)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("grades.title")
        .searchable(text: $searchText, prompt: Text("grades.searchPrompt"))
        .refreshable { model.grades.dispatch(GradesIntentRefresh()) }
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

    private var semesterPicker: some View {
        Picker("grades.semester", selection: semesterBinding) {
            Text("grades.allSemesters").tag(Semester.companion.All.id)
            ForEach(state.semesters, id: \.id) { semester in
                Text(semester.name).tag(semester.id)
            }
        }
        .pickerStyle(.menu)
        .accessibilityIdentifier("gradesSemesterPicker")
    }

    /// Bound to the semester *id*: `Semester` is a Kotlin class, and `Picker` needs a `Hashable`
    /// tag it can compare.
    private var semesterBinding: Binding<String> {
        Binding(
            get: { state.selectedSemester?.id ?? Semester.companion.All.id },
            set: { id in
                let chosen = state.semesters.first { $0.id == id } ?? Semester.companion.All
                model.grades.dispatch(GradesIntentSemesterSelected(semester: chosen))
            }
        )
    }

    private var statisticsSection: some View {
        Section("grades.statistics") {
            if let gpa = state.isShowingAllSemesters ? state.overallGpa : state.semesterGpa {
                LabeledContent(
                    state.isShowingAllSemesters ? "grades.overallGpa" : "grades.semesterGpa",
                    value: gpa.doubleValue.formatted(.number.precision(.fractionLength(2)))
                )
            }
            LabeledContent(
                "grades.creditsEarned",
                value: state.totalCreditsEarned.formatted(.number.precision(.fractionLength(1)))
            )
            LabeledContent("grades.modulesCompleted", value: "\(state.modulesCompleted)")
        }
    }

    private var visibleGrades: [GradeEntry] {
        guard !searchText.isEmpty else { return state.grades }
        return state.grades.filter {
            $0.moduleName.localizedCaseInsensitiveContains(searchText)
                || $0.moduleNumber.localizedCaseInsensitiveContains(searchText)
        }
    }

    /// One section per semester in the combined view, one section otherwise.
    private var sections: [(title: String, entries: [GradeEntry])] {
        let grouped = Dictionary(grouping: visibleGrades) { $0.semesterName }
        return grouped.keys.sorted(by: >).map { name in
            (title: name, entries: grouped[name] ?? [])
        }
    }
}

private struct GradeRow: View {

    let entry: GradeEntry

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.moduleName)
                Text(entry.moduleNumber).font(.caption).foregroundStyle(.secondary)
                if let status = entry.status, !status.isEmpty {
                    Text(status).font(.caption2).foregroundStyle(.secondary)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(entry.grade ?? "–").font(.headline).monospacedDigit()
                Text("\(entry.credits.formatted(.number.precision(.fractionLength(1)))) CP")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
