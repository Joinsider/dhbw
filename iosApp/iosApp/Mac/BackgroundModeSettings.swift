// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if os(macOS)
import ServiceManagement
import SwiftUI

/// The rows of the "Background" section in Settings (macOS only).
///
/// The switch shows the login item's real state, read from the system, and writes straight through
/// to it. There is no preference of the app's own behind it, so the switch cannot disagree with the
/// system after the user removes the item in System Settings.
struct BackgroundModeSettings: View {

    private let background = BackgroundMode.shared

    var body: some View {
        Group {
            rows
        }
        .onAppear { background.refresh() }
    }

    @ViewBuilder
    private var rows: some View {
        Toggle("settings.backgroundMode", isOn: Binding(
            get: { background.isEnabled },
            set: { wanted in Task { await background.setEnabled(wanted) } }
        ))
        .accessibilityIdentifier("settingsBackgroundModeToggle")

        if background.status == .requiresApproval {
            Label("settings.backgroundApproval", systemImage: "exclamationmark.circle")
                .foregroundStyle(.secondary)
            Button("settings.openLoginItems") {
                SMAppService.openSystemSettingsLoginItems()
            }
        }

        if let message = background.lastError {
            Label {
                Text(verbatim: "\(String(localized: "settings.backgroundFailed")) \(message)")
            } icon: {
                Image(systemName: "xmark.octagon")
            }
            .foregroundStyle(.red)
        }
    }
}
#endif
