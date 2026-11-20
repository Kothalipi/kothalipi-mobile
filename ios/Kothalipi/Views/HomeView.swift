import SwiftUI
import AVFoundation

/// Wispr Flow-style SwiftUI Home View for iOS
struct HomeView: View {
    @EnvironmentObject var manager: KothalipiEngineManager
    @State private var isRecording = false
    @State private var statusText = "কথা বলতে মাইক চাপুন"
    @State private var resultText = ""
    @State private var translateToEnglish = false
    @State private var showSettings = false
    @State private var apiKeyInput = ""

    let hapticFeedback = UIImpactFeedbackGenerator(style: .medium)

    var body: some View {
        ZStack {
            // Ultra-deep obsidian background with subtle top-center radial violet glow
            Color(hex: "09080E")
                .ignoresSafeArea()

            Circle()
                .fill(Color(hex: "7C3AED").opacity(0.12))
                .blur(radius: 90)
                .frame(width: 300, height: 300)
                .offset(y: -200)

            VStack(spacing: 24) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("কথালিপি")
                            .font(.system(size: 28, weight: .heavy))
                            .foregroundColor(Color(hex: "F8F8FC"))

                        Text("AI Voice Dictation & Flow")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "8E8CA0"))
                    }

                    Spacer()

                    Button(action: {
                        apiKeyInput = manager.apiKey
                        showSettings = true
                    }) {
                        Image(systemName: "gearshape.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color(hex: "9E9EA7"))
                            .padding(10)
                            .background(Color(hex: "161424"))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)

                // Wispr Flow Mode Pill Switcher
                HStack(spacing: 0) {
                    Button(action: {
                        hapticFeedback.impactOccurred()
                        translateToEnglish = false
                    }) {
                        Text("🇧🇩 বাংলা")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.vertical, 10)
                            .padding(.horizontal, 18)
                            .background(!translateToEnglish ? LinearGradient(colors: [Color(hex: "8A2BE2"), Color(hex: "7C3AED")], startPoint: .leading, endPoint: .trailing) : LinearGradient(colors: [Color.clear], startPoint: .leading, endPoint: .trailing))
                            .cornerRadius(18)
                    }

                    Button(action: {
                        hapticFeedback.impactOccurred()
                        translateToEnglish = true
                    }) {
                        Text("বাংলা ➔ 🇬🇧 English")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.vertical, 10)
                            .padding(.horizontal, 18)
                            .background(translateToEnglish ? LinearGradient(colors: [Color(hex: "8A2BE2"), Color(hex: "EC4899")], startPoint: .leading, endPoint: .trailing) : LinearGradient(colors: [Color.clear], startPoint: .leading, endPoint: .trailing))
                            .cornerRadius(18)
                    }
                }
                .padding(4)
                .background(Color(hex: "141220"))
                .overlay(
                    RoundedRectangle(cornerRadius: 22)
                        .stroke(Color(hex: "A855F7").opacity(0.2), lineWidth: 1)
                )
                .cornerRadius(22)
                .padding(.top, 8)

                Spacer()

                // Center Hero Microphone Button with Glowing Pulses
                VStack(spacing: 20) {
                    ZStack {
                        // Outer breathing pulse halo
                        Circle()
                            .fill(isRecording ? Color(hex: "EC4899").opacity(0.25) : Color(hex: "8A2BE2").opacity(0.15))
                            .frame(width: 160, height: 160)
                            .scaleEffect(isRecording ? 1.25 : 1.0)
                            .animation(isRecording ? Animation.easeInOut(duration: 0.7).repeatForever(autoreverses: true) : .default, value: isRecording)

                        // Hero Glassmorphic Button
                        Button(action: toggleRecording) {
                            Circle()
                                .fill(
                                    isRecording
                                        ? LinearGradient(colors: [Color(hex: "EC4899"), Color(hex: "8A2BE2")], startPoint: .topLeading, endPoint: .bottomTrailing)
                                        : LinearGradient(colors: [Color(hex: "8A2BE2"), Color(hex: "6D28D9")], startPoint: .topLeading, endPoint: .bottomTrailing)
                                )
                                .frame(width: 104, height: 104)
                                .shadow(color: isRecording ? Color(hex: "EC4899").opacity(0.5) : Color(hex: "8A2BE2").opacity(0.4), radius: 20, x: 0, y: 10)
                                .overlay(
                                    Image(systemName: isRecording ? "stop.fill" : "mic.fill")
                                        .font(.system(size: 42))
                                        .foregroundColor(.white)
                                )
                        }
                    }

                    // Live animated soundwave bars
                    if isRecording {
                        HStack(spacing: 5) {
                            ForEach(0..<5) { index in
                                WaveformBar(delay: Double(index) * 0.12)
                            }
                        }
                        .frame(height: 24)
                    }

                    Text(statusText)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(Color(hex: "9E9EA7"))
                }

                Spacer()

                // Result Box (Wispr Flow style glassmorphic card)
                if !resultText.isEmpty {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(resultText)
                            .font(.system(size: 18))
                            .foregroundColor(Color(hex: "F8F8FC"))
                            .lineSpacing(6)

                        HStack {
                            Spacer()

                            Button(action: {
                                hapticFeedback.impactOccurred()
                                UIPasteboard.general.string = resultText
                            }) {
                                Label("Copy", systemImage: "doc.on.doc")
                                    .font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background(Color(hex: "8A2BE2"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }

                            ShareLink(item: resultText) {
                                Label("Share", systemImage: "square.and.arrow.up")
                                    .font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 9)
                                    .background(Color(hex: "221E33"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }
                        }
                    }
                    .padding(20)
                    .background(Color(hex: "141220"))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color(hex: "A855F7").opacity(0.2), lineWidth: 1)
                    )
                    .cornerRadius(18)
                    .padding(.horizontal, 20)
                } else {
                    Spacer().frame(height: 50)
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
        hapticFeedback.impactOccurred()

        if !isRecording {
            manager.audioRecorder.startRecording { success in
                if success {
                    isRecording = true
                    statusText = translateToEnglish ? "শুনছি… ইংরেজিতে অনুবাদ হবে" : "শুনছি… বলুন"
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
                        hapticFeedback.impactOccurred()
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

/// Dynamic bouncing waveform bar component
struct WaveformBar: View {
    let delay: Double
    @State private var isAnimating = false

    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(LinearGradient(colors: [Color(hex: "EC4899"), Color(hex: "8A2BE2")], startPoint: .top, endPoint: .bottom))
            .frame(width: 4, height: isAnimating ? 24 : 6)
            .animation(
                Animation.easeInOut(duration: 0.45)
                    .repeatForever(autoreverses: true)
                    .delay(delay),
                value: isAnimating
            )
            .onAppear {
                isAnimating = true
            }
    }
}
