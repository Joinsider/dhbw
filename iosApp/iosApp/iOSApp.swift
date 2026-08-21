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
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
        }
    }
}
