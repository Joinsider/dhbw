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

    /// macOS only: window and activation-policy handling that SwiftUI cannot express, see `Mac/BackgroundMode.swift`.
#if os(macOS)
    @NSApplicationDelegateAdaptor(MacAppDelegate.self) private var appDelegate
#endif

    init() {
#if os(macOS)
        // Read once, before any scene is built, because the scene's launch behaviour depends on it.
        _ = LaunchContext.launchedAtLogin
#endif
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
#if os(macOS)
        // One window, not a group: `openWindow` from the menu bar must bring the existing window
        // forward rather than open a second one. A login launch starts without it, and state
        // restoration is off for that launch so the window does not come back through the back door.
        Window("DHBW Horb", id: MacWindow.mainID) {
            RootView()
                .environment(model)
                // A Mac window has no screen edge to fill, so it gets a default size and a floor
                // below which the sidebar and the timetable grid stop fitting.
                .frame(minWidth: 760, minHeight: 540)
        }
        .defaultSize(width: 1040, height: 700)
        .defaultLaunchBehavior(BackgroundMode.startsHidden ? .suppressed : .automatic)
        .restorationBehavior(BackgroundMode.startsHidden ? .disabled : .automatic)

        // Only while background mode is on. Its status is the login item, re-read on activation.
        MenuBarExtra(isInserted: menuBarBinding) {
            MenuBarMenu()
                .environment(model)
        } label: {
            MenuBarLabel()
        }
        .menuBarExtraStyle(.menu)
#else
        WindowGroup {
            RootView()
                .environment(model)
        }
#endif
    }

#if os(macOS)
    private var menuBarBinding: Binding<Bool> {
        Binding(get: { BackgroundMode.shared.isEnabled }, set: { _ in })
    }
#endif
}
