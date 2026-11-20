import AppIntents

/// App Intent for iPhone Action Button and Siri Shortcuts
/// Allows users to trigger Kothalipi Voice Dictation instantly
@available(iOS 16.0, *)
struct DictateWithKothalipiIntent: AppIntent {
    static var title: LocalizedStringResource = "Dictate with Kothalipi"
    static var description = IntentDescription("Start instant voice typing and translation with Kothalipi.")

    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        // Opens Kothalipi and initiates voice capture
        return .result()
    }
}
