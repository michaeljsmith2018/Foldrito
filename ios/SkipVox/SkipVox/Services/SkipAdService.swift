import Foundation
import WebKit

class SkipAdService {
    static let shared = SkipAdService()
    
    /// JavaScript snippet to detect and click "Skip Ad" buttons on YouTube and other platforms.
    let skipScript = """
    (function() {
        const selectors = [
            '.ytp-ad-skip-button',
            '.ytp-ad-skip-button-modern',
            '.videoAdUiSkipButton',
            '[class*="skip-ad"]',
            '[id*="skip-ad"]'
        ];
        
        for (const selector of selectors) {
            const button = document.querySelector(selector);
            if (button) {
                button.click();
                return "Skipped using " + selector;
            }
        }
        
        // Search for text-based buttons
        const buttons = document.querySelectorAll('button, div, span');
        for (const b of buttons) {
            if (b.innerText && (b.innerText.toLowerCase().includes('skip ad') || b.innerText.toLowerCase().includes('skip'))) {
                b.click();
                return "Skipped using text search";
            }
        }
        
        return "No skip button found";
    })();
    """
    
    func skipAd(in webView: WKWebView, completion: ((Result<String, Error>) -> Void)? = nil) {
        webView.evaluateJavaScript(skipScript) { (result, error) in
            if let error = error {
                completion?(.failure(error))
            } else if let result = result as? String {
                completion?(.success(result))
            } else {
                completion?(.success("Unknown result"))
            }
        }
    }
}
