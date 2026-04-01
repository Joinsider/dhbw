import SwiftUI
import composeApp

@main
struct iOSApp: App {

    init() {
        UIRootViewControllerHelper.getViewController = {
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