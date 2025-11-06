import SwiftUI
import ComposeApp

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: NSViewControllerRepresentable {
    func makeNSViewController(context: Context) -> NSViewController {
        return MainKt.MainViewController()
    }

    func updateNSViewController(_ nsViewController: NSViewController, context: Context) {
    }
}

