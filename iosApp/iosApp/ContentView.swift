// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import UIKit
import SwiftUI
import ComposeApp
import WidgetKit

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // Sobald die KMP-Seite neue Widget-Daten in NSUserDefaults geschrieben hat,
            // weist diese Notification das System an, die Timeline neu zu laden.
            // Name muss mit WidgetDataWriter.NOTIFICATION_WIDGET_DATA_UPDATED übereinstimmen.
            .onReceive(
                NotificationCenter.default.publisher(
                    for: Notification.Name("de.fampopprol.dhbwhorb.widgetDataUpdated")
                )
            ) { _ in
                WidgetCenter.shared.reloadAllTimelines()
            }
    }
}
