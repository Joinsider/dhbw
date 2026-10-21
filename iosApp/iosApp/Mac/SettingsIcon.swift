// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if os(macOS)
import SwiftUI

/// The coloured tile at the start of a row in the macOS Settings, as System Settings draws them:
/// a white SF Symbol on a rounded square of one colour.
struct SettingsIcon: View {

    let systemName: String
    let color: Color

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 24, height: 24)
            .background(color, in: .rect(cornerRadius: 6))
            .accessibilityHidden(true)
    }
}
#endif
