import AppIntents
import WebKit

@available(iOS 16.0, *)
struct SkipAdIntent: AppIntent {
    static var title: LocalizedStringResource = "Skip Ad"
    static var description = IntentDescription("Detects and clicks the Skip Ad button in the SkipVox browser.")
    
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        // In a real app, we would communicate with the main app or the active WKWebView.
        // For this MVP, we assume the app is running and has a reference to the webView.
        
        // This is a placeholder for the actual logic that would bridge to the UI.
        NotificationCenter.default.post(name: .skipAdRequested, object: nil)
        
        return .result(value: "Attempting to skip ad...")
    }
}

extension NSNotification.Name {
    static let skipAdRequested = NSNotification.Name("skipAdRequested")
}

@available(iOS 16.0, *)
struct SkipVoxShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SkipAdIntent(),
            phrases: [
                "Skip ad in \\(.applicationName)",
                "Hey Siri, skip ad"
            ],
            shortTitle: "Skip Ad",
            systemImageName: "forward.fill"
        )
    }
}
