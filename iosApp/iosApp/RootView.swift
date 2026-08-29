// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import WidgetKit
import Shared

/// The six stores, boxed once for the whole app.
///
/// They are singletons in Koin — a tab switch must not reload anything, which is what P4 fixed —
/// so their boxes are held here rather than in the screens that read them.
@MainActor
@Observable
final class AppModel {

    let app: StoreBox<AppStoreBridge>
    let auth: StoreBox<AuthStoreBridge>
    let timetable: StoreBox<TimetableStoreBridge>
    let grades: StoreBox<GradesStoreBridge>
    let documents: StoreBox<DocumentsStoreBridge>
    let settings: StoreBox<SettingsStoreBridge>

    init() {
        let shared = SharedApp.shared.start()
        app = StoreBox(shared.app)
        auth = StoreBox(shared.auth)
        timetable = StoreBox(shared.timetable)
        grades = StoreBox(shared.grades)
        documents = StoreBox(shared.documents)
        settings = StoreBox(shared.settings)

        // The login screen finishes by telling the app store, not by navigating itself: the root
        // decides what is on screen, from one flag, the way `DhbwNavHost` does on the other two
        // platforms.
        auth.onEffect { [app] effect in
            if effect is AuthEffectLoggedIn {
                app.dispatch(AppIntentLoggedIn())
            }
        }

        app.dispatch(AppIntentStarted())
        settings.dispatch(SettingsIntentLoad())

        // The widget extension reads the same database out of the App Group container, but it is
        // a separate process: only the app can tell WidgetKit that a fetch has landed.
        handles.append(shared.observeTimetableChanges {
            WidgetCenter.shared.reloadAllTimelines()
        })

        // Background monitoring follows both switches, the way `MainActivity` does on Android —
        // a change to either re-evaluates the schedule. The state flow replays its current value,
        // so a launch with both already on schedules straight away.
        let monitor = shared.lectureMonitor
        var scheduled: Bool?
        handles.append(shared.settings.observeState { state in
            let wanted = state.notificationsEnabled
                && (state.lectureAlertsEnabled || state.reminderLeadMinutes > 0)
            guard wanted != scheduled else { return }
            scheduled = wanted
            if wanted { monitor.schedule() } else { monitor.cancel() }
        })

        // The reminders iOS holds are wall-clock notification requests, and nothing refreshes them
        // on its own: a reinstall or an app update clears them, and the timetable moves underneath
        // them. Replanning at launch is what MainActivity does on Android.
        shared.rescheduleReminders {}
    }

    /// Observations that live as long as the app does. Nothing cancels them; the process ending
    /// is what stops them, which is the honest lifetime for "while the app runs".
    private var handles: [ObservationHandle] = []
}

struct RootView: View {

    @Environment(AppModel.self) private var model

    var body: some View {
        Group {
            if model.app.state.isRestoring {
                // Nothing is known yet about the stored session. Guessing here is what showed the
                // login screen to people who were still logged in.
                ProgressView()
                    .controlSize(.large)
                    .accessibilityLabel(Text("common.loading"))
            } else if model.app.state.isLoggedIn {
                MainTabView()
            } else {
                LoginScreen()
            }
        }
        // One accent for the whole app — see Design/Theme.swift for why that is the iOS
        // equivalent of the Material You seed colour the other platforms use.
        .tint(Color.brand)
        .preferredColorScheme(colorScheme)
        .animation(.default, value: model.app.state.isLoggedIn)
    }

    /// `nil` means "follow the system", which is what `ThemeMode.SYSTEM` asks for.
    private var colorScheme: ColorScheme? {
        let mode = model.settings.state.themeMode
        if mode == ThemeMode.light { return .light }
        if mode == ThemeMode.dark { return .dark }
        return nil
    }
}

/// The four tabs.
///
/// A `NavigationStack` per tab, not one around the `TabView`: each tab keeps its own history, so
/// leaving Grades open on a semester and coming back shows that semester again.
struct MainTabView: View {

    var body: some View {
        TabView {
            Tab("nav.timetable", systemImage: "calendar") {
                NavigationStack { TimetableScreen() }
            }
            Tab("nav.grades", systemImage: "chart.bar.doc.horizontal") {
                NavigationStack { GradesScreen() }
            }
            Tab("nav.documents", systemImage: "doc.text") {
                NavigationStack { DocumentsScreen() }
            }
            Tab("nav.settings", systemImage: "gearshape") {
                NavigationStack { SettingsScreen() }
            }
        }
    }
}
