// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// What Dualis records behind one module: its attempts, and the exams each attempt is made of.
///
/// The grade list can only show the module's final grade. A module like Mathematik III carries a
/// Klausur in Angewandte Mathematik and one in Statistik, and its 3,2 is what those two came to
/// together — visible nowhere else in the app.
///
/// One view for both platforms, laid out like the lecture details: a header with the module's name
/// and its grade, then grouped sections with an icon per row. The values are selectable, so a
/// number can be copied on a Mac. On macOS the sheet is 480 × 560pt and the form scrolls.
struct ModuleDetailsSheet: View {

    let module: GradeEntry
    /// Every row the list holds for this module, one per semester it was attempted in.
    let entries: [GradeEntry]
    let details: ModuleResultDetails?
    let isLoading: Bool
    let error: (any AppError)?

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    header
                }
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)

                if let details, !details.attempts.isEmpty {
                    ForEach(Array(details.attempts.enumerated()), id: \.offset) { _, attempt in
                        attemptSection(attempt)
                    }
                } else if isLoading {
                    Section {
                        HStack {
                            ProgressView()
                            Text("grades.details.loading").foregroundStyle(.secondary)
                        }
                    }
                } else if error != nil {
                    Section {
                        Text("grades.details.failed").foregroundStyle(.secondary)
                    }
                } else if module.resultId == nil {
                    Section {
                        Text("grades.details.unavailable").foregroundStyle(.secondary)
                    }
                }

                // Only worth showing when the module was attempted more than once: for everything
                // else this repeats the row the user just tapped.
                if entries.count > 1 {
                    Section("grades.details.entries") {
                        ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                            entryRow(entry)
                        }
                    }
                }

                if let units = details?.units, !units.isEmpty {
                    Section("grades.details.units") {
                        ForEach(Array(units.enumerated()), id: \.offset) { _, unit in
                            unitRow(unit)
                        }
                    }
                }
            }
            .formStyle(.grouped)
            .navigationTitle("grades.details.title")
            .platformInlineTitle()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.close") { dismiss() }
                        .keyboardShortcut(.defaultAction)
                        .accessibilityIdentifier("gradeDetailsCloseButton")
                }
            }
            .sheetPlatformFit()
        }
        // Escape closes the sheet on a Mac, as it does on any macOS sheet.
        .sheetExitCommand { dismiss() }
        .accessibilityIdentifier("moduleDetailsSheet")
    }

    /// The module's name, its grade, and the line that says where it belongs.
    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                Text(details?.moduleName ?? module.moduleName)
                    .font(.title2.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
                    .textSelection(.enabled)
                Spacer(minLength: 8)
                GradeBadge(grade: module.grade, countsTowardDegree: module.countsTowardDegree)
            }
            Text(verbatim: headerDetail)
                .font(.callout)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 4)
        // One label for the header, so VoiceOver reads the module as a sentence.
        .accessibilityElement(children: .combine)
    }

    /// `05-01-0101 · Wintersemester 2025/26 · 6,0 CP`.
    private var headerDetail: String {
        [
            module.moduleNumber,
            module.semesterName,
            "\(module.credits.formatted(.number.precision(.fractionLength(1)))) CP",
        ]
        .filter { !$0.isEmpty }
        .joined(separator: " · ")
    }

    private func attemptSection(_ attempt: ModuleAttempt) -> some View {
        Section {
            ForEach(Array(attempt.exams.enumerated()), id: \.offset) { _, exam in
                examRow(exam)
            }

            if let result = attempt.result {
                HStack(spacing: 12) {
                    SettingsIcon(systemName: "sum", color: .brand)
                    Text("grades.details.total")
                        .fontWeight(.semibold)
                    Spacer()
                    Text(result)
                        .fontWeight(.semibold)
                        .monospacedDigit()
                        .textSelection(.enabled)
                }
            }
        } header: {
            if let number = attempt.number {
                Text("grades.details.attempt \(Int(truncating: number))")
            } else {
                Text("grades.details.attemptUnnumbered")
            }
        }
    }

    private func examRow(_ exam: ExamResult) -> some View {
        HStack(alignment: .center, spacing: 12) {
            SettingsIcon(systemName: "doc.text.fill", color: .blue)
            VStack(alignment: .leading, spacing: 2) {
                if let unit = exam.unitName {
                    Text(unit)
                }
                // The exam's own line: what kind of exam it was, what share of the Baustein it
                // carries, and in which semester it was written.
                Text(subtitle(for: exam))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 8)
            Text(exam.grade ?? "–")
                .font(.headline)
                .monospacedDigit()
                .textSelection(.enabled)
        }
    }

    private func subtitle(for exam: ExamResult) -> String {
        var parts: [String] = [exam.name]
        if let weight = exam.weightPercent?.doubleValue {
            parts.append("\(weight.formatted(.number.precision(.fractionLength(0))))%")
        }
        if let semester = exam.semesterName { parts.append(semester) }
        if let date = exam.date { parts.append(date) }
        return parts.joined(separator: " · ")
    }

    private func entryRow(_ entry: GradeEntry) -> some View {
        HStack(alignment: .center, spacing: 12) {
            SettingsIcon(systemName: "list.bullet.rectangle.fill", color: .gray)
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.semesterName)
                if let status = entry.status, !status.isEmpty {
                    Text(status)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(entry.grade ?? "–")
                    .monospacedDigit()
                    .textSelection(.enabled)
                // Says out loud what the totals at the top of the screen did silently: a failed
                // or superseded attempt is on the transcript but not in the average.
                Text(entry.countsTowardDegree ? "grades.details.counted" : "grades.details.notCounted")
                    .font(.caption2)
                    .foregroundStyle(entry.countsTowardDegree ? Color.brand : .secondary)
            }
        }
    }

    private func unitRow(_ unit: ModuleUnit) -> some View {
        HStack(alignment: .center, spacing: 12) {
            SettingsIcon(systemName: "book.closed.fill", color: .teal)
            VStack(alignment: .leading, spacing: 2) {
                Text(unit.name)
                    .fixedSize(horizontal: false, vertical: true)
                Text(unit.number)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            if unit.attended {
                Image(systemName: "checkmark")
                    .fontWeight(.semibold)
                    .foregroundStyle(Color.brand)
            }
        }
    }
}

// MARK: - Platform sizing

private extension View {

    /// A macOS sheet is 480pt wide and 560pt tall, and the form scrolls inside it. It used to take
    /// the height of its content, but a module with several attempts and units then grew taller
    /// than the window: the bottom bar with the close button was cut off, and a form already as
    /// tall as its content had nothing to scroll.
    @ViewBuilder
    func sheetPlatformFit() -> some View {
        #if os(macOS)
        frame(width: 480)
            .frame(minHeight: 280, idealHeight: 560)
        #else
        self
        #endif
    }

    /// Escape closes the sheet. macOS only: iOS has no exit command, and its sheet is swiped away.
    @ViewBuilder
    func sheetExitCommand(_ action: @escaping () -> Void) -> some View {
        #if os(macOS)
        onExitCommand(perform: action)
        #else
        self
        #endif
    }
}
