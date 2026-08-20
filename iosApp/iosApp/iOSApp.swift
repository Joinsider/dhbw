import SwiftUI
import ComposeApp

@main
struct iOSApp: App {

    init() {
        UIRootViewControllerHelper.shared.getViewController = {
            let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene
            return windowScene?.windows.first?.rootViewController
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}