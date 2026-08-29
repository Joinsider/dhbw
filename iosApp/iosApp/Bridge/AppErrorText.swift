// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import Foundation
import Shared

/// The sentence to show for an `AppError` — the iOS counterpart of `AppErrorMessage.kt`, and the
/// only place on this side that turns the classification into words.
///
/// It is a second copy of that mapping, which the project otherwise avoids. The alternative would
/// be moving the strings into Kotlin, and then neither platform's translators could reach them
/// through the tooling they use. What must not drift is the *set* of cases, and that is checked:
/// the `default` branch below is only reachable for a case `AppError` gains later, and it says so.
extension AppError {

    var userMessage: String {
        switch self {
        case is AppErrorOffline:
            return String(localized: "error.offline")
        case is AppErrorSessionExpired:
            return String(localized: "error.sessionExpired")
        case is AppErrorInvalidCredentials:
            return String(localized: "error.invalidCredentials")
        case is AppErrorNoCredentials:
            return String(localized: "error.loginRequired")
        case let http as AppErrorHttp:
            return String(format: String(localized: "error.server"), Int(http.code))
        case is AppErrorParse:
            return String(localized: "error.unreadableResponse")
        case is AppErrorStorage:
            return String(localized: "error.storage")
        case is AppErrorUnsupported:
            return String(localized: "error.unsupportedInDemo")
        default:
            // AppError.Unexpected, and anything added to the sealed hierarchy after this was
            // written. Kotlin's `when` is exhaustive; an Objective-C protocol cannot be, so this
            // is where a new case surfaces — as the generic sentence, not as a crash.
            return String(localized: "error.unknown")
        }
    }
}
