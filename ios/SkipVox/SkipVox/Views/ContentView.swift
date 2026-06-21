import SwiftUI
import WebKit

struct ContentView: View {
    @State private var urlString = "https://www.youtube.com"
    @State private var webView = WKWebView()
    @State private var statusMessage = "Ready"
    
    var body: some View {
        VStack {
            HStack {
                TextField("URL", text: $urlString)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                Button("Go") {
                    if let url = URL(string: urlString) {
                        webView.load(URLRequest(url: url))
                    }
                }
            }
            .padding()
            
            WebView(webView: $webView)
                .edgesIgnoringSafeArea(.all)
            
            HStack {
                Text(statusMessage)
                    .font(.caption)
                Spacer()
                Button(action: {
                    SkipAdService.shared.skipAd(in: webView) { result in
                        switch result {
                        case .success(let message):
                            statusMessage = message
                        case .failure(let error):
                            statusMessage = "Error: \\(error.localizedDescription)"
                        }
                    }
                }) {
                    Text("Manual Skip")
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
            .padding()
        }
        .onReceive(NotificationCenter.default.publisher(for: .skipAdRequested)) { _ in
            SkipAdService.shared.skipAd(in: webView) { result in
                switch result {
                case .success(let message):
                    statusMessage = message
                case .failure(let error):
                    statusMessage = "Error: \(error.localizedDescription)"
                }
            }
        }
    }
}

struct WebView: UIViewRepresentable {
    @Binding var webView: WKWebView
    
    func makeUIView(context: Context) -> WKWebView {
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
