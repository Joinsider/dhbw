// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import WidgetKit
import SwiftUI

@main
struct TimetableWidgetBundle: WidgetBundle {
    var body: some Widget {
        // Home-Screen-Widget (Small / Medium / Large)
        TimetableWidget()
        // Control-Center-Widget (iOS 18 Controls – Platzhalter)
        TimetableWidgetControl()
    }
}
