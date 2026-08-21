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
    }
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
        .preferredColorScheme(colorScheme)
        .animation(.default, value: model.app.state.isLoggedIn)
        // The KMP side writes the widget snapshot into the App Group and posts this; the name must
        // match WidgetDataWriter.NOTIFICATION_WIDGET_DATA_UPDATED.
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("de.fampopprol.dhbwhorb.widgetDataUpdated")
            )
        ) { _ in
            WidgetCenter.shared.reloadAllTimelines()
        }
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
