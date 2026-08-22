// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import UserNotifications
import Shared

/// Appearance, notifications, and signing out.
///
/// Two settings the Compose screen offers are missing here on purpose: Material You and the seed
/// colour. They pick a Material palette from the wallpaper, and iOS has neither — the app follows
/// the system accent and `ThemeMode` instead. The store still holds them for Android and Desktop.
struct SettingsScreen: View {

    @Environment(AppModel.self) private var model
    @State private var showLogoutConfirmation = false
    @State private var cacheWarning = false

    private var state: SettingsState { model.settings.state }

    var body: some View {
        Form {
            Section("settings.appearance") {
                Picker("settings.theme", selection: themeBinding) {
                    Text("settings.themeSystem").tag(ThemeMode.system)
                    Text("settings.themeLight").tag(ThemeMode.light)
                    Text("settings.themeDark").tag(ThemeMode.dark)
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("settingsThemePicker")
            }

            Section {
                Toggle("settings.notifications", isOn: notificationsBinding)
                    .accessibilityIdentifier("settingsNotificationsToggle")
                Toggle("settings.lectureAlerts", isOn: lectureAlertsBinding)
                    .disabled(!state.notificationsEnabled)
                    .accessibilityIdentifier("settingsLectureAlertsToggle")
            } header: {
                Text("settings.notificationsHeader")
            } footer: {
                Text("settings.lectureAlertsFooter")
            }

            Section("settings.account") {
                if let name = model.app.state.userFullName {
                    LabeledContent("settings.signedInAs", value: name)
                }
                if model.app.state.isDemo {
                    Label("settings.demoMode", systemImage: "theatermasks")
                        .foregroundStyle(.secondary)
                }
                Button("settings.logout", role: .destructive) { showLogoutConfirmation = true }
                    .accessibilityIdentifier("settingsLogoutButton")
            }
        }
        .navigationTitle("settings.title")
        .task {
            model.settings.dispatch(SettingsIntentLoad())
            model.app.onEffect { effect in
                if effect is AppEffectCacheNotCleared { cacheWarning = true }
            }
        }
        .confirmationDialog(
            Text("settings.logoutConfirmTitle"),
            isPresented: $showLogoutConfirmation,
            titleVisibility: .visible
        ) {
            Button("settings.logout", role: .destructive) {
                model.app.dispatch(AppIntentLogoutRequested())
            }
            Button("common.cancel", role: .cancel) {}
        }
        .alert(Text("settings.cacheNotClearedTitle"), isPresented: $cacheWarning) {
            Button("common.ok", role: .cancel) { cacheWarning = false }
        } message: {
            Text("settings.cacheNotCleared")
        }
        .accessibilityIdentifier("settingsScreen")
    }

    private var themeBinding: Binding<ThemeMode> {
        Binding(
            get: { state.themeMode },
            set: { model.settings.dispatch(SettingsIntentThemeModeChanged(mode: $0)) }
        )
    }

    /// Turning notifications on asks iOS first: the store would otherwise remember a preference
    /// the system never granted, and nothing would ever arrive.
    private var notificationsBinding: Binding<Bool> {
        Binding(
            get: { state.notificationsEnabled },
            set: { enabled in
                guard enabled else {
                    model.settings.dispatch(SettingsIntentNotificationsChanged(enabled: false))
                    return
                }
                Task {
                    let granted = (try? await UNUserNotificationCenter.current()
                        .requestAuthorization(options: [.alert, .sound, .badge])) ?? false
                    model.settings.dispatch(SettingsIntentNotificationsChanged(enabled: granted))
                }
            }
        )
    }

    private var lectureAlertsBinding: Binding<Bool> {
        Binding(
            get: { state.lectureAlertsEnabled },
            set: { model.settings.dispatch(SettingsIntentLectureAlertsChanged(enabled: $0)) }
        )
    }
}
