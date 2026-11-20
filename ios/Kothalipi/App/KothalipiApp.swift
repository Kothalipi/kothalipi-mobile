import SwiftUI

@main
struct KothalipiApp: App {
    @StateObject private var engineManager = KothalipiEngineManager.shared

    var body: some Scene {
        WindowGroup {
            HomeView()
                .environmentObject(engineManager)
                .preferredColorScheme(.dark)
        }
    }
}
