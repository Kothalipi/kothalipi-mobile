import SwiftUI
import AVFoundation

struct HomeView: View {
    @EnvironmentObject var manager: KothalipiEngineManager
    @State private var isRecording = false
    @State private var statusText = "কথা বলতে মাইক চাপুন"
    @State private var resultText = ""
    @State private var translateToEnglish = false
    @State private var showSettings = false
    @State private var apiKeyInput = ""

    var body: some View {
        ZStack {
            Color(hex: "0F0F14")
                .ignoresSafeArea()

            VStack(spacing: 24) {
                // Header
                HStack {
                    Text("কথালিপি")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(Color(hex: "F5F5F7"))

                    Spacer()

                    Button(action: {
                        apiKeyInput = manager.apiKey
                        showSettings = true
                    }) {
                        Image(systemName: "gearshape.fill")
                            .font(.system(size: 22))
                            .foregroundColor(Color(hex: "9E9EA7"))
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)

                // Language Mode Selector
                HStack(spacing: 0) {
                    Button(action: { translateToEnglish = false }) {
                        Text("বাংলা")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 16)
                            .background(!translateToEnglish ? Color(hex: "8A2BE2") : Color.clear)
                            .cornerRadius(16)
                    }

                    Button(action: { translateToEnglish = true }) {
                        Text("বাংলা ➔ English")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 16)
                            .background(translateToEnglish ? Color(hex: "8A2BE2") : Color.clear)
                            .cornerRadius(16)
                    }
                }
                .background(Color(hex: "1A1A24"))
                .cornerRadius(20)
                .padding(.top, 8)

                Spacer()

                // Center Recording Button
                VStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(isRecording ? Color(hex: "E254FF").opacity(0.3) : Color(hex: "8A2BE2").opacity(0.2))
                            .frame(width: 140, height: 140)
                            .scaleEffect(isRecording ? 1.2 : 1.0)
                            .animation(isRecording ? Animation.easeInOut(duration: 0.8).repeatForever(autoreverses: true) : .default, value: isRecording)

                        Button(action: toggleRecording) {
                            Circle()
                                .fill(isRecording ? Color(hex: "E254FF") : Color(hex: "8A2BE2"))
                                .frame(width: 100, height: 100)
                                .overlay(
                                    Image(systemName: isRecording ? "stop.fill" : "mic.fill")
                                        .font(.system(size: 40))
                                        .foregroundColor(.white)
                                )
                        }
                    }

                    Text(statusText)
                        .font(.system(size: 16))
                        .foregroundColor(Color(hex: "9E9EA7"))
                }

                Spacer()

                // Result Box
                if !resultText.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        Text(resultText)
                            .font(.system(size: 18))
                            .foregroundColor(Color(hex: "F5F5F7"))
                            .lineSpacing(6)

                        HStack {
                            Spacer()

                            Button(action: {
                                UIPasteboard.general.string = resultText
                            }) {
                                Label("Copy", systemImage: "doc.on.doc")
                                    .font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(Color(hex: "8A2BE2"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }

                            ShareLink(item: resultText) {
                                Label("Share", systemImage: "square.and.arrow.up")
                                    .font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(Color(hex: "2A2A38"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }
                        }
                    }
                    .padding(18)
                    .background(Color(hex: "1A1A24"))
                    .cornerRadius(16)
                    .padding(.horizontal, 20)
                } else {
                    Spacer().frame(height: 40)
                }
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsSheet(apiKey: $apiKeyInput, onSave: { key in
                manager.apiKey = key
                showSettings = false
            })
        }
    }

    private func toggleRecording() {
        if !isRecording {
            manager.audioRecorder.startRecording { success in
                if success {
                    isRecording = true
                    statusText = "শুনছি… কথা বলুন"
                } else {
                    statusText = "মাইক্রোফোন পারমিশন প্রয়োজন"
                }
            }
        } else {
            isRecording = false
            statusText = "প্রসেস হচ্ছে…"

            guard let wavData = manager.audioRecorder.stopRecording() else {
                statusText = "রেকর্ডিং পাওয়া যায়নি"
                return
            }

            Task {
                do {
                    let result = try await manager.processRecording(
                        wavData: wavData,
                        translateToEnglish: translateToEnglish
                    )
                    await MainActor.run {
                        resultText = result
                        statusText = "সম্পন্ন!"
                    }
                } catch {
                    await MainActor.run {
                        statusText = "এরর: \(error.localizedDescription)"
                    }
                }
            }
        }
    }
}

struct SettingsSheet: View {
    @Binding var apiKey: String
    var onSave: (String) -> Void
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Gemini API Key")) {
                    SecureField("AIzaSy...", text: $apiKey)
                }
            }
            .navigationTitle("সেটিংস")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(apiKey) }
                }
            }
        }
    }
}

extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex)
        var rgbValue: UInt64 = 0
        scanner.scanHexInt64(&rgbValue)
        let r = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let g = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let b = Double(rgbValue & 0x0000FF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
