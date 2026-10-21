// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import UserNotifications
import Shared
import os
#if os(macOS)
import AppKit
#else
import UIKit
#endif

private let notificationLog = Logger(subsystem: "de.fampopprol.dhbw", category: "Notifications")

/// Appearance, notifications, and signing out.
///
/// Two settings the Compose screen offers are missing here on purpose: Material You and the seed
/// colour. They pick a Material palette from the wallpaper, and iOS has neither — the app follows
/// the system accent and `ThemeMode` instead. The store still holds them for Android and Desktop.
struct SettingsScreen: View {

    @Environment(AppModel.self) private var model
    @State private var showLogoutConfirmation = false
    @State private var cacheWarning = false
    @State private var notificationsBlocked = false

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
                Picker("settings.reminder", selection: reminderBinding) {
                    Text("settings.reminderOff").tag(Int32(0))
                    Text("settings.reminder15").tag(Int32(15))
                    Text("settings.reminder30").tag(Int32(30))
                    Text("settings.reminder60").tag(Int32(60))
                }
                .disabled(!state.notificationsEnabled)
                .accessibilityIdentifier("settingsReminderPicker")
            } header: {
                Text("settings.notificationsHeader")
            } footer: {
                #if os(macOS)
                Text("settings.lectureAlertsFooter.macos")
                #else
                Text("settings.lectureAlertsFooter.ios")
                #endif
            }

            #if DEBUG
            Section {
                Button("settings.testNotificationNow") { sendTestNotification(delaySeconds: 0) }
                    .accessibilityIdentifier("settingsTestNotificationNowButton")
                Button("settings.testNotificationDelayed") { sendTestNotification(delaySeconds: 10) }
                    .accessibilityIdentifier("settingsTestNotificationDelayedButton")
                Button("settings.testReminder") { sendTestReminder() }
                    .accessibilityIdentifier("settingsTestReminderButton")
            } header: {
                Text("settings.testNotificationsHeader")
            } footer: {
                Text("settings.testNotificationsFooter")
            }
            #endif

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
        .alert(Text("settings.notificationsBlockedTitle"), isPresented: $notificationsBlocked) {
            Button("settings.openSystemSettings") { openNotificationSettings() }
            Button("common.cancel", role: .cancel) {}
        } message: {
            Text("settings.notificationsBlocked")
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
                Task { await enableNotifications() }
            }
        )
    }

    /// Asks the system, and turns the switch on only if it grants permission.
    ///
    /// The error used to be dropped with `try?`, so a refused request looked like a plain "no":
    /// the switch snapped back and nothing said why. On an unsigned Mac build the system refuses
    /// outright (UNErrorDomain, "Notifications are not allowed for this application"), and that
    /// is now in the log and in front of the user.
    private func enableNotifications() async {
        do {
            let granted = try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
            notificationLog.notice("Notification permission \(granted ? "granted" : "denied", privacy: .public)")
            if !granted { notificationsBlocked = true }
            model.settings.dispatch(SettingsIntentNotificationsChanged(enabled: granted))
        } catch {
            notificationLog.error("Requesting notification permission failed: \(error.localizedDescription, privacy: .public)")
            notificationsBlocked = true
            model.settings.dispatch(SettingsIntentNotificationsChanged(enabled: false))
        }
    }

    #if DEBUG
    /// Sends through the real Kotlin path: permission, the dispatcher, and the foreground presenter
    /// in `NotificationPresenter`. A `false` means iOS does not allow notifications, which is the
    /// same answer the switch above gets, so it gets the same alert.
    private func sendTestNotification(delaySeconds: Int32) {
        SharedApp.shared.sendTestNotification(delaySeconds: delaySeconds) { delivered in
            if !delivered.boolValue { Task { @MainActor in notificationsBlocked = true } }
        }
    }

    /// Hands one reminder to the scheduler for the next whole minute. The timetable's own reminders
    /// are left alone (`LectureReminderScheduler.add`, not a replan).
    private func sendTestReminder() {
        SharedApp.shared.sendTestReminder { scheduled in
            if !scheduled.boolValue { Task { @MainActor in notificationsBlocked = true } }
        }
    }
    #endif

    private func openNotificationSettings() {
        #if os(macOS)
        if let url = URL(string: "x-apple.systempreferences:com.apple.Notifications-Settings.extension") {
            NSWorkspace.shared.open(url)
        }
        #else
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
        #endif
    }

    /// The reminder is scheduled by iOS itself, so a new lead time has to be replanned now rather
    /// than at the next background run — otherwise the choice would not take effect for an hour.
    private var reminderBinding: Binding<Int32> {
        Binding(
            get: { state.reminderLeadMinutes },
            set: { minutes in
                model.settings.dispatch(SettingsIntentReminderLeadChanged(minutes: minutes))
                SharedApp.shared.rescheduleReminders {}
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
