// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import Shared
import SwiftUI

/// The sheet for one lecture: the title and the time as a header, and the room and the lecturers
/// as a grouped form with icon tiles. iOS and macOS share it; the sheet's presentation is set at
/// the call site in `TimetableScreen`.
struct LectureDetailsView: View {

    let lecture: Lecture

    @Environment(\.dismiss) private var dismiss

    /// Lecturers can be unknown for a lecture, and the room can be empty; either way the form only
    /// appears when there is something in it.
    private var hasDetails: Bool {
        !lecture.location.isEmpty || !lecture.lecturers.isEmpty
    }

    /// `Tuesday, November 3, 2026 · 8:30 – 12:00`, in the system's language and clock format.
    private var whenText: String {
        let day = lecture.start.foundationDate.formatted(date: .complete, time: .omitted)
        return "\(day) · \(lecture.start.timeText) – \(lecture.end.timeText)"
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("timetable.details")
                #if os(iOS)
                .navigationBarTitleDisplayMode(.inline)
                #endif
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("common.close") { dismiss() }
                            .keyboardShortcut(.defaultAction)
                            .accessibilityIdentifier("lectureDetailsCloseButton")
                    }
                }
        }
        #if os(macOS)
        // Escape closes the sheet, as it does on any macOS sheet; Return is on the button above.
        .onExitCommand { dismiss() }
        #endif
    }

    @ViewBuilder
    private var content: some View {
        #if os(macOS)
        VStack(alignment: .leading, spacing: 0) {
            header
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .padding(.bottom, hasDetails ? 16 : 24)

            if hasDetails {
                Form {
                    Section {
                        detailsRows
                    }
                }
                .formStyle(.grouped)
                .scrollDisabled(true)
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(width: 460)
        .fixedSize(horizontal: false, vertical: true)
        #else
        // iOS scrolls the form, so the medium and large detents both work without a fixed height.
        Form {
            Section {
                header
            }
            if hasDetails {
                Section {
                    detailsRows
                }
            }
        }
        #endif
    }

    /// The room and lecturer rows, shared by both layouts above.
    @ViewBuilder
    private var detailsRows: some View {
        if !lecture.location.isEmpty {
            LabeledContent {
                Text(verbatim: lecture.location)
                    .textSelection(.enabled)
            } label: {
                rowLabel("timetable.room", systemName: "door.left.hand.open", color: .blue)
            }
        }
        if !lecture.lecturers.isEmpty {
            LabeledContent {
                VStack(alignment: .trailing, spacing: 2) {
                    ForEach(Array(lecture.lecturers.enumerated()), id: \.offset) { _, name in
                        Text(verbatim: name)
                            .textSelection(.enabled)
                    }
                }
            } label: {
                rowLabel("timetable.lecturers", systemName: "person.2.fill", color: .teal)
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            if lecture.isTest {
                Label("timetable.exam", systemImage: "exclamationmark.triangle.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.examEdge)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.examFill, in: .capsule)
            }

            Text(verbatim: lecture.displayName)
                .font(.title2.weight(.semibold))
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)

            Text(verbatim: whenText)
                .font(.callout)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // One label for the whole header, so VoiceOver reads the lecture as a sentence.
        .accessibilityElement(children: .combine)
    }

    private func rowLabel(_ key: LocalizedStringKey, systemName: String, color: Color) -> some View {
        Label {
            Text(key)
        } icon: {
            SettingsIcon(systemName: systemName, color: color)
        }
    }
}
