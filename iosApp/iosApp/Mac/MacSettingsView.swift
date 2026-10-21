// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if os(macOS)
import Shared
import SwiftUI

/// The three debug-only test actions, implemented by `SettingsScreen`.
struct MacTestNotificationActions {
    let sendNow: () -> Void
    let sendDelayed: () -> Void
    let sendReminder: () -> Void
}

/// The macOS layout of Settings, in the style of System Settings: a grouped form centred at a
/// readable width, with a coloured icon at the start of each row.
///
/// This view only lays out. The bindings and actions come from `SettingsScreen`, which is where
/// the store is read and written, so the iOS and macOS screens share one set of behaviours.
struct MacSettingsView: View {

    let userName: String?
    let isDemo: Bool
    let state: SettingsState
    let theme: Binding<ThemeMode>
    let notifications: Binding<Bool>
    let lectureAlerts: Binding<Bool>
    let reminder: Binding<Int32>
    /// Notifications were refused by the system. Shows the inline row with the way out.
    let notificationsDenied: Bool
    /// The debug-only test buttons; nil in release builds.
    let testActions: MacTestNotificationActions?
    let onLogout: () -> Void
    let onOpenNotificationSettings: () -> Void

    /// The repository the source link points at, as in the app's store metadata.
    private static let sourceURL = URL(string: "https://github.com/joinsider/dhbw")

    var body: some View {
        Form {
            accountSection
            appearanceSection
            notificationsSection
            backgroundSection
            aboutSection
            #if DEBUG
            if let testActions {
                debugSection(testActions)
            }
            #endif
        }
        .formStyle(.grouped)
        .frame(maxWidth: 640)
    }

    // MARK: Sections

    @ViewBuilder
    private var accountSection: some View {
        Section("settings.account") {
            if let userName {
                LabeledContent {
                    Text(verbatim: userName)
                } label: {
                    rowLabel("settings.signedInAs", systemName: "person.crop.circle.fill", color: .gray)
                }
            }
            if isDemo {
                rowLabel("settings.demoMode", systemName: "theatermasks.fill", color: .gray)
                    .foregroundStyle(.secondary)
            }
            Button("settings.logout", role: .destructive, action: onLogout)
                .accessibilityIdentifier("settingsLogoutButton")
        }
    }

    @ViewBuilder
    private var appearanceSection: some View {
        Section("settings.appearance") {
            Picker(selection: theme) {
                Text("settings.themeSystem").tag(ThemeMode.system)
                Text("settings.themeLight").tag(ThemeMode.light)
                Text("settings.themeDark").tag(ThemeMode.dark)
            } label: {
                rowLabel("settings.themeMode", systemName: "paintpalette.fill", color: .blue)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("settingsThemePicker")
        }
    }

    @ViewBuilder
    private var notificationsSection: some View {
        Section {
            if notificationsDenied {
                blockedRow
            }
            Toggle(isOn: notifications) {
                rowLabel("settings.notifications", systemName: "bell.badge.fill", color: .red)
            }
            .toggleStyle(.switch)
            .accessibilityIdentifier("settingsNotificationsToggle")

            Toggle(isOn: lectureAlerts) {
                rowLabel("settings.lectureAlerts", systemName: "calendar.badge.clock", color: .green)
            }
            .toggleStyle(.switch)
            .disabled(!state.notificationsEnabled)
            .accessibilityIdentifier("settingsLectureAlertsToggle")

            Picker(selection: reminder) {
                Text("settings.reminderOff").tag(Int32(0))
                Text("settings.reminder15").tag(Int32(15))
                Text("settings.reminder30").tag(Int32(30))
                Text("settings.reminder60").tag(Int32(60))
            } label: {
                rowLabel("settings.reminder", systemName: "clock.fill", color: .orange)
            }
            .pickerStyle(.menu)
            .disabled(!state.notificationsEnabled)
            .accessibilityIdentifier("settingsReminderPicker")
        } header: {
            Text("settings.notificationsHeader")
        } footer: {
            Text("settings.lectureAlertsFooter.macos")
        }
    }

    /// Shown while the system refuses notifications for the app. The alert explains the same thing
    /// once; this row keeps the explanation and the button in view until the permission changes.
    private var blockedRow: some View {
        HStack(alignment: .center, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.orange)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("settings.notificationsBlockedTitle")
                    .fontWeight(.medium)
                Text("settings.notificationsBlocked")
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Button("settings.openSystemSettings", action: onOpenNotificationSettings)
        }
    }

    @ViewBuilder
    private var backgroundSection: some View {
        Section {
            BackgroundModeSettings()
        } header: {
            Text("settings.backgroundHeader")
        } footer: {
            Text("settings.backgroundFooter")
        }
    }

    @ViewBuilder
    private var aboutSection: some View {
        Section("settings.aboutHeader") {
            LabeledContent {
                Text(verbatim: versionText)
                    .foregroundStyle(.secondary)
            } label: {
                rowLabel("settings.version", systemName: "info.circle.fill", color: .gray)
            }
            if let sourceURL = Self.sourceURL {
                Link(destination: sourceURL) {
                    rowLabel("settings.sourceCode", systemName: "chevron.left.forwardslash.chevron.right", color: .gray)
                }
            }
        }
    }

    #if DEBUG
    @ViewBuilder
    private func debugSection(_ actions: MacTestNotificationActions) -> some View {
        Section {
            Button("settings.testNotificationNow", action: actions.sendNow)
                .accessibilityIdentifier("settingsTestNotificationNowButton")
            Button("settings.testNotificationDelayed", action: actions.sendDelayed)
                .accessibilityIdentifier("settingsTestNotificationDelayedButton")
            Button("settings.testReminder", action: actions.sendReminder)
                .accessibilityIdentifier("settingsTestReminderButton")
        } header: {
            Text("settings.testNotificationsHeader")
        } footer: {
            Text("settings.testNotificationsFooter")
        }
    }
    #endif

    // MARK: Helpers

    /// A row title with its coloured icon. Every labelled row in this view uses it, so the icons
    /// line up down the page.
    private func rowLabel(_ key: LocalizedStringKey, systemName: String, color: Color) -> some View {
        Label {
            Text(key)
        } icon: {
            SettingsIcon(systemName: systemName, color: color)
        }
    }

    /// `CFBundleShortVersionString` and the build number, as the bundle reports them.
    private var versionText: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? "–"
        let build = info?["CFBundleVersion"] as? String ?? "–"
        return "\(version) (\(build))"
    }
}
#endif
