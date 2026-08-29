// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
import WidgetKit
import Shared

@main
struct iOSApp: App {

    /// Built once, here, for the same reason Koin starts in `Application.onCreate()` on Android:
    /// the first view already resolves stores, and starting the graph from a `.task` would run
    /// after that first frame.
    @State private var model = AppModel()

    init() {
        UIRootViewControllerHelper.shared.getViewController = {
            let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene
            return windowScene?.windows.first?.rootViewController
        }

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
        }
    }
}
