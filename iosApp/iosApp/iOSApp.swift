// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI
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
        SharedApp.shared.start().lectureMonitor.registerTaskHandler()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
        }
    }
}
