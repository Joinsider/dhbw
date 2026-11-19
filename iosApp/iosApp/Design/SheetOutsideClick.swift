// SPDX-FileCopyrightText: 2024 Joinside <suitor-fall-life@duck.com>
// SPDX-License-Identifier: AGPL-3.0-or-later

import SwiftUI

#if os(macOS)
import AppKit
#endif

extension View {

    /// Closes a sheet when the user clicks the window behind it, as a popover would.
    ///
    /// A macOS sheet is document-modal: AppKit swallows a click on its parent window and only
    /// Escape or a button gets the user out. The details sheets hold nothing to lose, so a click
    /// next to them should close them too. A local event monitor still sees that click before
    /// AppKit drops it; it closes the sheet only when the click lands in the window this sheet is
    /// attached to, and it consumes the click so it does not also act on the list behind it.
    ///
    /// iOS needs none of this: its sheet is swiped away, and a tap above a medium detent already
    /// dismisses it.
    @ViewBuilder
    func dismissOnOutsideClick(_ action: @escaping () -> Void) -> some View {
        #if os(macOS)
        modifier(OutsideClickDismissal(action: action))
        #else
        self
        #endif
    }
}

#if os(macOS)
private struct OutsideClickDismissal: ViewModifier {

    let action: () -> Void
    @State private var hostWindow: NSWindow?
    @State private var monitor: Any?

    func body(content: Content) -> some View {
        content
            .background(WindowReader(window: $hostWindow))
            .onAppear(perform: install)
            .onDisappear(perform: remove)
    }

    private func install() {
        guard monitor == nil else { return }
        monitor = NSEvent.addLocalMonitorForEvents(matching: [.leftMouseDown, .rightMouseDown]) { event in
            guard let sheet = hostWindow,
                  let clicked = event.window,
                  clicked !== sheet,
                  sheet.sheetParent === clicked
            else { return event }
            action()
            return nil
        }
    }

    private func remove() {
        if let monitor { NSEvent.removeMonitor(monitor) }
        monitor = nil
    }
}

/// Hands out the window a SwiftUI view lives in — here, the sheet's own window.
private struct WindowReader: NSViewRepresentable {

    @Binding var window: NSWindow?

    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async { window = view.window }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        if window !== nsView.window {
            DispatchQueue.main.async { window = nsView.window }
        }
    }
}
#endif
