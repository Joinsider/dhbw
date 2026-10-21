// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if os(macOS)
import AppKit
import CoreServices
import Observation
import os
import ServiceManagement
import SwiftUI

private let log = Logger(subsystem: "de.fampopprol.dhbw", category: "BackgroundMode")

/// Whether this launch was started by the login items mechanism rather than by the user.
///
/// `SMAppService.mainApp` launches the app through launchd and LaunchServices. Two signals, either
/// one is enough:
///
/// 1. The launching Apple event carries `keyAELaunchedAsLogInItem` in its `keyAEPropData`
///    parameter. That is how LaunchServices has always marked login-item launches. SwiftUI does not
///    promise the event is still current when `App.init` runs, so this is best effort.
/// 2. The system has been up for less than five minutes. Login items start right after the login
///    window, and only a reboot makes the uptime that small, so this covers the case where the event
///    is not visible. The cost of a false positive (a user launch within five minutes of boot) is one
///    launch without a window, and the menu bar item opens one.
///
/// A logout and login without a reboot leaves the uptime large, so only signal 1 recognises it.
enum LaunchContext {
    static let launchedAtLogin: Bool = {
        let event = NSAppleEventManager.shared().currentAppleEvent
        if event?.paramDescriptor(forKeyword: UInt32(keyAEPropData))?.enumCodeValue
            == UInt32(keyAELaunchedAsLogInItem) {
            return true
        }
        return ProcessInfo.processInfo.systemUptime < 300
    }()
}

/// The background mode is the login item. The system's registration is the one source of truth:
/// nothing here stores a second flag that could drift from it, and the status is re-read whenever
/// the app becomes active, because the user can remove the item in System Settings at any time.
@MainActor
@Observable
final class BackgroundMode {

    static let shared = BackgroundMode()

    /// A login launch with background mode on: start without a window and without a Dock icon.
    static var startsHidden: Bool {
        LaunchContext.launchedAtLogin && shared.isEnabled
    }

    private(set) var status: SMAppService.Status
    /// The last failure from registering or unregistering, shown in Settings until the next change.
    private(set) var lastError: String?

    private let service = SMAppService.mainApp

    private init() {
        status = SMAppService.mainApp.status
    }

    var isEnabled: Bool { status == .enabled }

    func refresh() {
        status = service.status
    }

    /// Registers or unregisters the login item, then reads the status back from the system.
    ///
    /// The status is read back rather than assumed: `register()` can succeed and still leave the
    /// item waiting for approval in System Settings (`.requiresApproval`).
    func setEnabled(_ enabled: Bool) async {
        do {
            if enabled {
                try service.register()
            } else {
                try await service.unregister()
            }
            lastError = nil
        } catch {
            log.error("Changing the login item to \(enabled, privacy: .public) failed: \(error.localizedDescription, privacy: .public)")
            lastError = error.localizedDescription
        }
        refresh()
    }
}

/// Identifiers and window helpers shared by the app, the menu bar and the delegate.
enum MacWindow {
    static let mainID = "main"

    /// Brings the main window back. The Dock icon returns first, so the window has a place to go
    /// and can take focus.
    @MainActor
    static func show(_ openWindow: OpenWindowAction) {
        NSApp.setActivationPolicy(.regular)
        openWindow(id: mainID)
        NSApp.activate(ignoringOtherApps: true)
    }

    /// Posted by the AppDelegate when the app is opened again with no window showing (Finder,
    /// Launchpad, Spotlight, or a Dock click). The delegate has no `openWindow`, so the always-present
    /// menu bar label listens and does the opening, see `MenuBarLabel`.
    static let openRequest = Notification.Name("de.fampopprol.dhbw.openMainWindow")
}

extension NSWindow {
    /// A window the user sees as an app window. Menu bar panels and status windows are excluded, so
    /// they do not count as "a window is open" when deciding whether to leave the Dock.
    var isUserWindow: Bool {
        !(self is NSPanel) && styleMask.contains(.titled)
    }
}

/// The AppKit side of the background mode, which SwiftUI cannot express on its own.
///
/// - Starts a login launch in `.accessory` (menu bar only) before any window exists, so no Dock
///   icon flashes up at login.
/// - Keeps the app running when its last window closes. The `false` below is also what AppKit does
///   by default, so with background mode off nothing changes.
/// - When background mode is on: a window appearing puts the Dock icon back, and the last window
///   closing takes it away again.
@MainActor
final class MacAppDelegate: NSObject, NSApplicationDelegate {

    private var tokens: [NSObjectProtocol] = []

    func applicationWillFinishLaunching(_ notification: Notification) {
        if BackgroundMode.startsHidden {
            NSApp.setActivationPolicy(.accessory)
        }
        observeWindows()
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        guard BackgroundMode.startsHidden else { return }
        // Backstop for the scene's `.suppressed` launch behaviour: if a window was put on screen
        // anyway, close it. Closing does not quit the app (see below), so this only hides it.
        DispatchQueue.main.async {
            NSApp.windows.filter(\.isUserWindow).forEach { $0.close() }
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    /// Opening the app again (Finder, Launchpad, Spotlight, Dock click) while it runs hidden must
    /// bring the window back. Only in background mode with no window showing: otherwise AppKit and
    /// SwiftUI keep their default handling, which is what happens today.
    ///
    /// The window is opened by the menu bar label, which owns `openWindow`, so this posts
    /// `MacWindow.openRequest` and reports the event as handled.
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        guard !flag, BackgroundMode.shared.isEnabled else { return true }
        NotificationCenter.default.post(name: MacWindow.openRequest, object: nil)
        return false
    }

    private func observeWindows() {
        let center = NotificationCenter.default
        // The user can switch the login item off in System Settings while the app runs; the menu bar
        // item and the switch must notice when the app is next in front.
        tokens.append(center.addObserver(forName: NSApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in
            MainActor.assumeIsolated { BackgroundMode.shared.refresh() }
        })
        tokens.append(center.addObserver(forName: NSWindow.didBecomeKeyNotification, object: nil, queue: .main) { note in
            guard let window = note.object as? NSWindow, window.isUserWindow else { return }
            MainActor.assumeIsolated { MacAppDelegate.showInDockIfNeeded() }
        })
        tokens.append(center.addObserver(forName: NSWindow.willCloseNotification, object: nil, queue: .main) { note in
            guard let window = note.object as? NSWindow, window.isUserWindow else { return }
            // The closing window still reports itself visible here, so the count waits one turn.
            DispatchQueue.main.async {
                MainActor.assumeIsolated { MacAppDelegate.hideFromDockIfNoWindows() }
            }
        })
    }

    private static func showInDockIfNeeded() {
        guard NSApp.activationPolicy() != .regular else { return }
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
    }

    private static func hideFromDockIfNoWindows() {
        guard BackgroundMode.shared.isEnabled else { return }
        let anyOpen = NSApp.windows.contains { $0.isUserWindow && $0.isVisible }
        if !anyOpen, NSApp.activationPolicy() == .regular {
            NSApp.setActivationPolicy(.accessory)
            log.notice("Last window closed, background mode: back to the menu bar only")
        }
    }
}
#endif
