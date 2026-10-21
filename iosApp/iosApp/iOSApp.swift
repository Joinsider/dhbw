// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import UserNotifications
import WidgetKit
import Shared
#if canImport(UIKit)
import UIKit
#endif

@main
struct iOSApp: App {

    /// Built once, here, for the same reason Koin starts in `Application.onCreate()` on Android:
    /// the first view already resolves stores, and starting the graph from a `.task` would run
    /// after that first frame.
    @State private var model = AppModel()

    /// Held here because the notification center keeps only a weak reference to its delegate.
    private static let notificationPresenter = NotificationPresenter()

    init() {
        // Before anything can post: a change found while the window is open must still show.
        UNUserNotificationCenter.current().delegate = Self.notificationPresenter

#if os(iOS)
        // Kotlin presents UIKit controllers (the iOS-only file and share hand-offs) through the
        // root view controller. macOS has no such controller, and nothing on that side asks.
        UIRootViewControllerHelper.shared.getViewController = {
            let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene
            return windowScene?.windows.first?.rootViewController
        }
#endif

        // Has to happen here and nowhere later: iOS rejects a `BGTaskScheduler` registration that
        // arrives after the app has finished launching, and the failure is a crash on the next
        // submit rather than an error at registration time. Whether the task is ever *submitted*
        // is a different question, and one the settings switches answer — see `AppModel`.
        let shared = SharedApp.shared.start()
        shared.lectureMonitor.registerTaskHandler()

        // WidgetCenter is Swift-only, so Kotlin cannot reach it. The background check needs it:
        // without this, a lecture change found while the app was closed would update the database
        // and leave the widget showing yesterday.
        shared.setWidgetReload {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                // A Mac window has no screen edge to fill, so it gets a default size and a floor
                // below which the sidebar and the timetable grid stop fitting.
                #if os(macOS)
                .frame(minWidth: 760, minHeight: 540)
                #endif
        }
        #if os(macOS)
        .defaultSize(width: 1040, height: 700)
        #endif
    }
}
