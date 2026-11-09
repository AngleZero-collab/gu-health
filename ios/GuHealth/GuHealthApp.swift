import SwiftUI

@main
struct GuHealthApp: App {
    @StateObject private var store = AppStore()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(store).tint(CareTheme.green).preferredColorScheme(.light) }
    }
}
