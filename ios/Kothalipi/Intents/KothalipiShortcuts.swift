import AppIntents
import Foundation

/// App Intent for iPhone Action Button and Siri Shortcuts.
/// Opens Kothalipi and immediately begins voice capture.
@available(iOS 16.0, *)
struct DictateWithKothalipiIntent: AppIntent {
    static var title: LocalizedStringResource = "Dictate with Kothalipi"
    static var description = IntentDescription("Start instant voice typing and translation with Kothalipi.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        NotificationCenter.default.post(
            name: KothalipiEngineManager.startDictationNotification,
            object: nil
        )
        return .result()
    }
}

@available(iOS 16.0, *)
struct KothalipiShortcutsProvider: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: DictateWithKothalipiIntent(),
            phrases: [
                "Dictate with \(.applicationName)",
                "Start \(.applicationName) voice typing"
            ],
            shortTitle: "Dictate",
            systemImageName: "mic.fill"
        )
    }
}
