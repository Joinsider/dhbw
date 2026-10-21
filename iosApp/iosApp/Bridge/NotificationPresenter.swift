// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import UserNotifications

/// Lets lecture notifications show while the app is in front.
///
/// UserNotifications hides a notification for the app that is currently active unless its delegate
/// asks for it in `willPresent`. Without this, the hourly check on iOS and macOS can post a change
/// and nothing appears while the window is open, which is the usual state on a Mac. The delegate
/// is held weakly by the system, so the owner keeps a strong reference (see `iOSApp`).
final class NotificationPresenter: NSObject, UNUserNotificationCenterDelegate {

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }
}
