// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import Shared

/// What Dualis records behind one module: its attempts, and the exams each attempt is made of.
///
/// The grade list can only show the module's final grade. A module like Mathematik III carries a
/// Klausur in Angewandte Mathematik and one in Statistik, and its 3,2 is what those two came to
/// together — visible nowhere else in the app.
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
            List {
                header

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
                            HStack(alignment: .firstTextBaseline) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(unit.name)
                                    Text(unit.number).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if unit.attended {
                                    Image(systemName: "checkmark").foregroundStyle(Color.brand)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("grades.details.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.close") { dismiss() }
                }
            }
        }
        .accessibilityIdentifier("moduleDetailsSheet")
    }

    private var header: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text(details?.moduleName ?? module.moduleName)
                    .font(.headline)
                Text(module.moduleNumber)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("\(module.credits.formatted(.number.precision(.fractionLength(1)))) CP")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func attemptSection(_ attempt: ModuleAttempt) -> some View {
        Section {
            ForEach(Array(attempt.exams.enumerated()), id: \.offset) { _, exam in
                examRow(exam)
            }

            if let result = attempt.result {
                HStack {
                    Text("grades.details.total").fontWeight(.semibold)
                    Spacer()
                    Text(result).fontWeight(.semibold).monospacedDigit()
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
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                if let unit = exam.unitName {
                    Text(unit).font(.subheadline)
                }
                // The exam's own line: what kind of exam it was, what share of the Baustein it
                // carries, and in which semester it was written.
                Text(subtitle(for: exam))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(exam.grade ?? "–").font(.headline).monospacedDigit()
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
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.semesterName)
                if let status = entry.status, !status.isEmpty {
                    Text(status).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(entry.grade ?? "–").monospacedDigit()
                // Says out loud what the totals at the top of the screen did silently: a failed
                // or superseded attempt is on the transcript but not in the average.
                Text(entry.countsTowardDegree ? "grades.details.counted" : "grades.details.notCounted")
                    .font(.caption2)
                    .foregroundStyle(entry.countsTowardDegree ? Color.brand : .secondary)
            }
        }
    }
}
