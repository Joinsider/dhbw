// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// Every semester's grades, oldest first.
///
/// There is no semester picker any more. The store hands over one list, already in the order the
/// semesters happened in (`SemesterOrder`), and the sections come from `GradesState.sections` —
/// the same grouping the Compose page draws, so the two cannot disagree about what belongs where.
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
                                GradeRow(entry: entry)
                            }
                        } header: {
                            SemesterHeader(section: section)
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

    /// The three numbers that describe the whole degree so far.
    private var summary: some View {
        Section {
            HStack(spacing: 12) {
                statTile(
                    value: state.overallGpa.map { $0.doubleValue.formatted(.number.precision(.fractionLength(2))) } ?? "–",
                    label: "grades.overallGpa",
                    isBrand: true
                )
                statTile(
                    value: state.totalCreditsEarned.formatted(.number.precision(.fractionLength(0))),
                    label: "grades.creditsEarned",
                    isBrand: false
                )
                statTile(
                    value: "\(state.modulesCompleted)",
                    label: "grades.modulesCompleted",
                    isBrand: false
                )
            }
            .listRowInsets(EdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12))
        }
    }

    private func statTile(value: String, label: LocalizedStringKey, isBrand: Bool) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2.bold())
                .fontDesign(.rounded)
                .monospacedDigit()
                .foregroundStyle(isBrand ? Color.brand : Color.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(isBrand ? Color.brandSoft : Color(uiColor: .tertiarySystemFill), in: .rect(cornerRadius: 12))
        .accessibilityElement(children: .combine)
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

/// Semester name on the left, its own average on the right.
private struct SemesterHeader: View {

    let section: SemesterGrades

    var body: some View {
        HStack {
            Text(section.semesterName)
            Spacer()
            if let average = semesterAverage {
                Text(average.formatted(.number.precision(.fractionLength(2))))
                    .monospacedDigit()
                    .foregroundStyle(Color.brand)
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
