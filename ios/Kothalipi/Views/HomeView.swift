import SwiftUI
import AVFoundation

/// Wispr Flow-style SwiftUI Home View for iOS
/// Supports Hold-to-Talk, Auto-Silence Stop (VAD), Multi-Provider BYOK, Voice Snippets, and Voice Edit Commands!
struct HomeView: View {
    @EnvironmentObject var manager: KothalipiEngineManager
    @State private var selectedTab = 0 // 0 = Dictate, 1 = Snippets, 2 = History
    @State private var isRecording = false
    @State private var isVoiceEditRecording = false
    @State private var isProcessing = false
    @State private var isTranslating = false
    @State private var recordingSeconds = 0
    @State private var timer: Timer?

    @State private var pressStartTime: Date?
    @State private var startedByHold = false

    @State private var resultText = ""
    @State private var errorMessage = ""
    @State private var copiedRecently = false
    @State private var showSettings = false
    @State private var searchText = ""
    @State private var snippetTrigger = ""
    @State private var snippetContent = ""
    @State private var waveBars: [CGFloat] = Array(repeating: 0.15, count: 12)

    let haptic = UIImpactFeedbackGenerator(style: .medium)

    var activeRecording: Bool { isRecording || isVoiceEditRecording }

    var body: some View {
        ZStack {
            Color(hex: "08070F").ignoresSafeArea()

            Circle()
                .fill(activeRecording ? Color(hex: "EC4899").opacity(0.16) : Color(hex: "7C3AED").opacity(0.14))
                .blur(radius: 90)
                .frame(width: 320, height: 320)
                .offset(y: -260)

            VStack(spacing: 0) {
                headerBar

                if selectedTab == 0 {
                    dictateTab
                } else if selectedTab == 1 {
                    snippetsTab
                } else {
                    historyTab
                }

                bottomTabBar
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsSheet(manager: manager)
        }
        .onReceive(manager.audioRecorder.$audioLevel) { level in
            guard activeRecording else { return }
            waveBars.removeFirst()
            waveBars.append(CGFloat(max(0.12, min(level, 1.0))))
        }
        .onReceive(NotificationCenter.default.publisher(for: KothalipiEngineManager.startDictationNotification)) { _ in
            selectedTab = 0
            if !activeRecording && !isProcessing {
                startRecording(isVoiceEdit: false)
            }
        }
    }

    private var headerBar: some View {
        HStack {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(LinearGradient(colors: [Color(hex: "7C3AED"), Color(hex: "EC4899")], startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 38, height: 38)
                    Image(systemName: "mic.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("কথালিপি")
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundColor(Color(hex: "F8F8FC"))

                    HStack(spacing: 5) {
                        Circle()
                            .fill(manager.hasAnyApiKey ? Color(hex: "10B981") : Color(hex: "F59E0B"))
                            .frame(width: 7, height: 7)
                        Text(manager.hasAnyApiKey ? "AI Ready (\(manager.primaryProvider.capitalized))" : "API Key দিন")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(manager.hasAnyApiKey ? Color(hex: "10B981") : Color(hex: "F59E0B"))
                    }
                    .onTapGesture { showSettings = true }
                }
            }

            Spacer()

            Button(action: { showSettings = true }) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 18))
                    .foregroundColor(Color(hex: "F8F8FC"))
                    .frame(width: 38, height: 38)
                    .background(Color(hex: "131120"))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "A855F7").opacity(0.25), lineWidth: 1))
                    .cornerRadius(12)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private var dictateTab: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 14) {
                if !manager.hasAnyApiKey {
                    Button(action: { showSettings = true }) {
                        HStack(spacing: 10) {
                            Image(systemName: "key.fill").foregroundColor(Color(hex: "F59E0B"))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Gemini / Groq API Key সেট করুন")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "FDE68A"))
                                Text("ফ্রি API Key বসাতে এখানে ট্যাপ করুন")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "D4D4D8"))
                            }
                            Spacer()
                            Text("Setup →").font(.system(size: 12, weight: .bold)).foregroundColor(Color(hex: "F59E0B"))
                        }
                        .padding(14)
                        .background(Color(hex: "F59E0B").opacity(0.14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "F59E0B").opacity(0.4), lineWidth: 1))
                        .cornerRadius(14)
                    }
                }

                // 4-Mode Switcher
                HStack(spacing: 4) {
                    modeButton(title: "বাংলা", mode: "bn", colors: [Color(hex: "7C3AED"), Color(hex: "8B5CF6")])
                    modeButton(title: "English", mode: "en", colors: [Color(hex: "2563EB"), Color(hex: "06B6D4")])
                    modeButton(title: "বাংলা➔EN", mode: "bn_to_en", colors: [Color(hex: "7C3AED"), Color(hex: "EC4899")])
                    modeButton(title: "Auto", mode: "auto", colors: [Color(hex: "059669"), Color(hex: "06B6D4")])
                }
                .padding(4)
                .background(Color(hex: "131120"))
                .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
                .cornerRadius(18)

                // Style + Polish + Auto-Stop VAD Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button(action: { haptic.impactOccurred(); manager.smartPolish.toggle() }) {
                            HStack(spacing: 4) {
                                Image(systemName: "sparkles").font(.system(size: 11))
                                Text("Polish").font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(manager.smartPolish ? Color(hex: "A7F3D0") : Color(hex: "9E9CB3"))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(manager.smartPolish ? Color(hex: "10B981").opacity(0.2) : Color(hex: "131120"))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(manager.smartPolish ? Color(hex: "10B981") : Color.white.opacity(0.1), lineWidth: 1))
                            .cornerRadius(12)
                        }

                        Button(action: { haptic.impactOccurred(); manager.autoSilenceStop.toggle() }) {
                            HStack(spacing: 4) {
                                Image(systemName: "waveform").font(.system(size: 11))
                                Text("Auto-Stop").font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(manager.autoSilenceStop ? Color(hex: "A5F3FC") : Color(hex: "9E9CB3"))
                            .padding(.horizontal, 10).padding(.vertical, 6)
                            .background(manager.autoSilenceStop ? Color(hex: "06B6D4").opacity(0.2) : Color(hex: "131120"))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(manager.autoSilenceStop ? Color(hex: "06B6D4") : Color.white.opacity(0.1), lineWidth: 1))
                            .cornerRadius(12)
                        }

                        Button(action: { haptic.impactOccurred(); manager.offlineMode.toggle() }) {
                            Text(manager.offlineMode ? "Offline ON" : "Offline")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(manager.offlineMode ? Color(hex: "FDE68A") : Color(hex: "9E9CB3"))
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(manager.offlineMode ? Color(hex: "F59E0B").opacity(0.2) : Color(hex: "131120"))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(manager.offlineMode ? Color(hex: "F59E0B") : Color.white.opacity(0.1), lineWidth: 1))
                                .cornerRadius(12)
                        }

                        ForEach([("normal", "স্বাভাবিক"), ("formal", "ফর্মাল"), ("casual", "চ্যাট"), ("bullets", "পয়েন্ট")], id: \.0) { id, label in
                            Button(action: { haptic.impactOccurred(); manager.writingStyle = id }) {
                                Text(label)
                                    .font(.system(size: 11, weight: manager.writingStyle == id ? .bold : .medium))
                                    .foregroundColor(manager.writingStyle == id ? .white : Color(hex: "9E9CB3"))
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                    .background(manager.writingStyle == id ? Color(hex: "8B5CF6").opacity(0.25) : Color(hex: "131120"))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(manager.writingStyle == id ? Color(hex: "8B5CF6") : Color.white.opacity(0.1), lineWidth: 1))
                                    .cornerRadius(12)
                            }
                        }
                    }
                }

                // Hero Mic Orb — supports BOTH Tap-to-Toggle AND Hold-to-Talk!
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(activeRecording ? Color(hex: "EC4899").opacity(0.24) : Color(hex: "8B5CF6").opacity(0.14))
                            .frame(width: 164, height: 164)
                            .scaleEffect(activeRecording ? (1.08 + CGFloat(manager.audioRecorder.audioLevel) * 0.25) : 1.0)
                            .animation(.easeOut(duration: 0.12), value: manager.audioRecorder.audioLevel)

                        ZStack {
                            Circle()
                                .fill(micGradient)
                                .frame(width: 106, height: 106)
                                .shadow(color: (activeRecording ? Color(hex: "EC4899") : Color(hex: "8B5CF6")).opacity(0.5), radius: 22, x: 0, y: 10)

                            if isProcessing {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white)).scaleEffect(1.4)
                            } else {
                                Image(systemName: activeRecording ? "stop.fill" : "mic.fill")
                                    .font(.system(size: 38, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                        }
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onChanged { _ in
                                    guard !isProcessing else { return }
                                    if pressStartTime == nil {
                                        pressStartTime = Date()
                                        startedByHold = false
                                        if !activeRecording {
                                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.32) {
                                                if let start = pressStartTime, Date().timeIntervalSince(start) >= 0.3, !activeRecording {
                                                    startedByHold = true
                                                    startRecording(isVoiceEdit: false)
                                                }
                                            }
                                        }
                                    }
                                }
                                .onEnded { _ in
                                    guard !isProcessing else { return }
                                    let heldDuration = pressStartTime.map { Date().timeIntervalSince($0) } ?? 0
                                    pressStartTime = nil
                                    if startedByHold && heldDuration >= 0.32 {
                                        startedByHold = false
                                        if isRecording { stopAndTranscribe(isVoiceEdit: false) }
                                    } else {
                                        if isRecording { stopAndTranscribe(isVoiceEdit: false) }
                                        else if isVoiceEditRecording { stopAndTranscribe(isVoiceEdit: true) }
                                        else { startRecording(isVoiceEdit: false) }
                                    }
                                }
                        )
                    }
                    .padding(.top, 8)

                    if activeRecording {
                        HStack(spacing: 5) {
                            ForEach(0..<waveBars.count, id: \.self) { i in
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(LinearGradient(colors: [Color(hex: "EC4899"), Color(hex: "8B5CF6")], startPoint: .top, endPoint: .bottom))
                                    .frame(width: 4, height: 8 + waveBars[i] * 24)
                                    .animation(.easeOut(duration: 0.08), value: waveBars[i])
                            }
                        }
                        .frame(height: 32)

                        Text(String(format: "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60))
                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                            .foregroundColor(isVoiceEditRecording ? Color(hex: "F59E0B") : Color(hex: "EC4899"))
                    }

                    Text(statusPrompt)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(isVoiceEditRecording ? Color(hex: "F59E0B") : Color(hex: "9E9CB3"))
                        .multilineTextAlignment(.center)
                }

                if !errorMessage.isEmpty {
                    HStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill").foregroundColor(Color(hex: "FCA5A5"))
                        Text(errorMessage).font(.system(size: 13)).foregroundColor(Color(hex: "FCA5A5"))
                        Spacer()
                    }
                    .padding(12)
                    .background(Color(hex: "EF4444").opacity(0.16))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "EF4444").opacity(0.4), lineWidth: 1))
                    .cornerRadius(14)
                }

                // Result Card with Voice Edit Command button!
                if !resultText.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            let words = resultText.split { $0.isWhitespace }.count
                            Text("\(modeDisplayName(manager.dictationMode)) • \(words) শব্দ")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color(hex: "8B5CF6"))
                            Spacer()
                            Button(action: { resultText = ""; copiedRecently = false }) {
                                Image(systemName: "xmark").font(.system(size: 12, weight: .bold)).foregroundColor(Color(hex: "9E9CB3"))
                            }
                        }

                        Text(resultText)
                            .font(.system(size: 16))
                            .foregroundColor(Color(hex: "F8F8FC"))
                            .lineSpacing(5)
                            .textSelection(.enabled)

                        HStack(spacing: 8) {
                            Button(action: {
                                haptic.impactOccurred()
                                UIPasteboard.general.string = resultText
                                copiedRecently = true
                            }) {
                                Label(copiedRecently ? "Copied" : "Copy", systemImage: copiedRecently ? "checkmark" : "doc.on.doc")
                                    .font(.system(size: 12, weight: .semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(copiedRecently ? Color(hex: "10B981") : Color(hex: "7C3AED"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }

                            Button(action: toggleVoiceEdit) {
                                Label(isVoiceEditRecording ? "Apply" : "Voice Edit", systemImage: isVoiceEditRecording ? "stop.fill" : "wand.and.stars")
                                    .font(.system(size: 12, weight: .semibold))
                                    .padding(.horizontal, 12).padding(.vertical, 10)
                                    .background(isVoiceEditRecording ? Color(hex: "F59E0B") : Color(hex: "261D42"))
                                    .foregroundColor(isVoiceEditRecording ? .black : .white)
                                    .cornerRadius(12)
                            }
                            .disabled(isProcessing || isRecording)

                            Button(action: translateCurrentResult) {
                                if isTranslating {
                                    ProgressView().tint(.white).padding(.horizontal, 12).padding(.vertical, 10)
                                } else {
                                    Image(systemName: "globe").font(.system(size: 14, weight: .semibold))
                                        .padding(.horizontal, 12).padding(.vertical, 10)
                                }
                            }
                            .background(Color(hex: "1E1B30"))
                            .foregroundColor(.white)
                            .cornerRadius(12)
                            .disabled(isTranslating)

                            ShareLink(item: resultText) {
                                Image(systemName: "square.and.arrow.up").font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 12).padding(.vertical, 10)
                                    .background(Color(hex: "1E1B30"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }
                        }
                    }
                    .padding(16)
                    .background(Color(hex: "131120"))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
                    .cornerRadius(18)
                }

                HStack(spacing: 12) {
                    Image(systemName: "keyboard.fill").font(.system(size: 20)).foregroundColor(Color(hex: "06B6D4"))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("iOS ভয়েস কীবোর্ড ও Action Button")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(hex: "F8F8FC"))
                        Text("Settings → General → Keyboard → Keyboards → Kothalipi (Allow Full Access অন করুন)")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "9E9CB3"))
                    }
                    Spacer()
                }
                .padding(14)
                .background(Color(hex: "131120"))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.08), lineWidth: 1))
                .cornerRadius(16)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
        }
    }

    private var snippetsTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("ভয়েস স্ন্যাপেট / শর্টকাট")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Color(hex: "F8F8FC"))
                Text("ট্রিগার বললেই (যেমন: 'আমার ঠিকানা বলো' বা 'বিকাশ নম্বর') সেভ করা টেক্সট অটো বসবে।")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "9E9CB3"))
            }
            .padding(.horizontal, 20)

            VStack(spacing: 8) {
                TextField("ট্রিগার (যেমন: আমার ঠিকানা বলো)", text: $snippetTrigger)
                    .padding(10)
                    .background(Color(hex: "1A172B"))
                    .foregroundColor(.white)
                    .cornerRadius(10)

                TextField("আউটপুট টেক্সট (যেমন: House 12, Dhanmondi)", text: $snippetContent)
                    .padding(10)
                    .background(Color(hex: "1A172B"))
                    .foregroundColor(.white)
                    .cornerRadius(10)

                Button(action: {
                    manager.addSnippet(trigger: snippetTrigger, content: snippetContent)
                    snippetTrigger = ""
                    snippetContent = ""
                }) {
                    Label("স্ন্যাপেট যোগ করুন", systemImage: "plus")
                        .font(.system(size: 13, weight: .bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color(hex: "7C3AED"))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
            }
            .padding(14)
            .background(Color(hex: "131120"))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
            .cornerRadius(14)
            .padding(.horizontal, 20)

            if manager.snippets.isEmpty {
                Spacer()
                Text("এখনো কোনো স্ন্যাপেট যোগ করা হয়নি")
                    .foregroundColor(Color(hex: "9E9CB3"))
                    .font(.system(size: 14))
                    .frame(maxWidth: .infinity)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(manager.snippets) { item in
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("“\(item.trigger)”")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(Color(hex: "8B5CF6"))
                                    Text(item.content)
                                        .font(.system(size: 14))
                                        .foregroundColor(Color(hex: "F8F8FC"))
                                }
                                Spacer()
                                Button(action: { manager.deleteSnippet(id: item.id) }) {
                                    Image(systemName: "trash").foregroundColor(Color(hex: "FCA5A5"))
                                }
                            }
                            .padding(14)
                            .background(Color(hex: "131120"))
                            .cornerRadius(14)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }

    private var historyTab: some View {
        VStack(spacing: 12) {
            HStack {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundColor(Color(hex: "9E9CB3"))
                    TextField("হিস্ট্রি খুঁজুন…", text: $searchText).foregroundColor(.white).font(.system(size: 14))
                }
                .padding(10)
                .background(Color(hex: "131120"))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
                .cornerRadius(12)

                if !manager.history.isEmpty {
                    Button("Clear") { manager.clearHistory() }
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(hex: "FCA5A5"))
                }
            }
            .padding(.horizontal, 20)

            let filtered = searchText.isEmpty
                ? manager.history
                : manager.history.filter { $0.text.localizedCaseInsensitiveContains(searchText) }

            if filtered.isEmpty {
                Spacer()
                Text(manager.history.isEmpty ? "এখনো কোনো ডিক্টেশন হিস্ট্রি নেই" : "কিছু পাওয়া যায়নি")
                    .foregroundColor(Color(hex: "9E9CB3"))
                    .font(.system(size: 14))
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(filtered) { item in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text("\(modeDisplayName(item.mode)) • \(item.timestamp.formatted(date: .abbreviated, time: .shortened))")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(Color(hex: "8B5CF6"))
                                    Spacer()
                                    Button(action: { haptic.impactOccurred(); UIPasteboard.general.string = item.text }) {
                                        Image(systemName: "doc.on.doc").font(.system(size: 13)).foregroundColor(Color(hex: "9E9CB3"))
                                    }
                                    ShareLink(item: item.text) {
                                        Image(systemName: "square.and.arrow.up").font(.system(size: 13)).foregroundColor(Color(hex: "9E9CB3"))
                                    }
                                    Button(action: { manager.deleteHistory(id: item.id) }) {
                                        Image(systemName: "trash").font(.system(size: 13)).foregroundColor(Color(hex: "FCA5A5"))
                                    }
                                }
                                Text(item.text).font(.system(size: 15)).foregroundColor(Color(hex: "F8F8FC")).lineLimit(5)
                            }
                            .padding(14)
                            .background(Color(hex: "131120"))
                            .cornerRadius(14)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }

    private var bottomTabBar: some View {
        HStack(spacing: 4) {
            tabPill(title: "ডিক্টেশন", icon: "mic.fill", index: 0)
            tabPill(title: "স্ন্যাপেট", icon: "bolt.fill", index: 1)
            tabPill(title: "হিস্ট্রি", icon: "clock.arrow.circlepath", index: 2)
        }
        .padding(5)
        .background(Color(hex: "131120"))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
        .cornerRadius(20)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private func tabPill(title: String, icon: String, index: Int) -> some View {
        Button(action: { selectedTab = index }) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 13))
                Text(title).font(.system(size: 12, weight: selectedTab == index ? .bold : .medium))
            }
            .foregroundColor(selectedTab == index ? .white : Color(hex: "9E9CB3"))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .background(
                selectedTab == index
                    ? LinearGradient(colors: [Color(hex: "7C3AED"), Color(hex: "8B5CF6")], startPoint: .leading, endPoint: .trailing)
                    : LinearGradient(colors: [.clear], startPoint: .leading, endPoint: .trailing)
            )
            .cornerRadius(15)
        }
    }

    private func modeButton(title: String, mode: String, colors: [Color]) -> some View {
        Button(action: { haptic.impactOccurred(); manager.dictationMode = mode; errorMessage = "" }) {
            Text(title)
                .font(.system(size: 12, weight: manager.dictationMode == mode ? .bold : .medium))
                .foregroundColor(manager.dictationMode == mode ? .white : Color(hex: "9E9CB3"))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(
                    manager.dictationMode == mode
                        ? LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing)
                        : LinearGradient(colors: [.clear], startPoint: .leading, endPoint: .trailing)
                )
                .cornerRadius(14)
        }
    }

    private var micGradient: LinearGradient {
        if isProcessing {
            return LinearGradient(colors: [Color(hex: "4C1D95"), Color(hex: "581C87")], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        if isVoiceEditRecording {
            return LinearGradient(colors: [Color(hex: "F59E0B"), Color(hex: "EC4899")], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        if isRecording {
            return LinearGradient(colors: [Color(hex: "EC4899"), Color(hex: "7C3AED")], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        switch manager.dictationMode {
        case "en": return LinearGradient(colors: [Color(hex: "2563EB"), Color(hex: "06B6D4")], startPoint: .topLeading, endPoint: .bottomTrailing)
        case "bn_to_en": return LinearGradient(colors: [Color(hex: "7C3AED"), Color(hex: "EC4899")], startPoint: .topLeading, endPoint: .bottomTrailing)
        case "auto": return LinearGradient(colors: [Color(hex: "059669"), Color(hex: "06B6D4")], startPoint: .topLeading, endPoint: .bottomTrailing)
        default: return LinearGradient(colors: [Color(hex: "8B5CF6"), Color(hex: "6D28D9")], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    private var statusPrompt: String {
        if isProcessing { return "AI প্রসেস করছে…" }
        if isVoiceEditRecording { return "এডিট কমান্ড বলুন (যেমন: ফর্মাল ইমেইল বানাও)…" }
        if isRecording {
            switch manager.dictationMode {
            case "en": return "Listening… Speak in English"
            case "bn_to_en": return "শুনছি… বাংলায় বলুন, ইংরেজিতে হবে"
            case "auto": return "শুনছি… বাংলা বা ইংরেজিতে বলুন"
            default: return "শুনছি… বাংলায় বলুন"
            }
        }
        return "ট্যাপ করুন অথবা চেপে ধরে কথা বলুন"
    }

    private func modeDisplayName(_ mode: String) -> String {
        switch mode {
        case "en": return "English"
        case "bn_to_en": return "বাংলা ➔ EN"
        case "auto": return "Auto"
        default: return "বাংলা"
        }
    }

    private func toggleVoiceEdit() {
        guard !isProcessing, !resultText.isEmpty else { return }
        if isVoiceEditRecording {
            stopAndTranscribe(isVoiceEdit: true)
        } else if !isRecording {
            startRecording(isVoiceEdit: true)
        }
    }

    private func startRecording(isVoiceEdit: Bool) {
        guard !isProcessing else { return }
        haptic.impactOccurred()

        if manager.offlineMode && !isVoiceEdit {
            errorMessage = ""
            copiedRecently = false
            isRecording = true
            recordingSeconds = 0
            timer?.invalidate()
            timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in recordingSeconds += 1 }

            manager.offlineSpeech.start(
                mode: manager.dictationMode,
                onPartial: { partial in self.resultText = partial },
                onAmplitude: { amp in
                    self.waveBars.removeFirst()
                    self.waveBars.append(CGFloat(max(0.12, min(amp, 1.0))))
                },
                onResult: { finalText in
                    self.isRecording = false
                    self.timer?.invalidate()
                    self.timer = nil
                    let expanded = self.manager.applySnippets(to: finalText)
                    self.resultText = expanded
                    self.manager.addHistory(text: expanded, mode: self.manager.dictationMode, style: "offline")
                },
                onError: { err in
                    self.isRecording = false
                    self.timer?.invalidate()
                    self.timer = nil
                    self.errorMessage = err
                }
            )
            return
        }

        if !manager.hasAnyApiKey {
            errorMessage = KothalipiError.missingApiKey.localizedDescription
            showSettings = true
            return
        }

        errorMessage = ""
        copiedRecently = false
        manager.audioRecorder.startRecording(
            autoSilenceStop: manager.autoSilenceStop,
            onSilenceDetected: {
                if self.activeRecording && !self.isProcessing {
                    self.stopAndTranscribe(isVoiceEdit: isVoiceEdit)
                }
            }
        ) { success, err in
            if success {
                if isVoiceEdit { isVoiceEditRecording = true } else { isRecording = true }
                recordingSeconds = 0
                timer?.invalidate()
                timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
                    recordingSeconds += 1
                }
            } else {
                errorMessage = err ?? "মাইক্রোফোন পারমিশন প্রয়োজন"
            }
        }
    }

    private func stopAndTranscribe(isVoiceEdit: Bool) {
        if manager.offlineMode && !isVoiceEdit {
            isRecording = false
            timer?.invalidate()
            timer = nil
            manager.offlineSpeech.stop()
            return
        }
        isRecording = false
        isVoiceEditRecording = false
        timer?.invalidate()
        timer = nil
        isProcessing = true

        guard let wavData = manager.audioRecorder.stopRecording() else {
            isProcessing = false
            errorMessage = KothalipiError.emptyAudio.localizedDescription
            return
        }

        let mode = manager.dictationMode
        let style = manager.writingStyle
        let polish = manager.smartPolish
        let currentText = resultText

        Task {
            do {
                let result: String
                if isVoiceEdit && !currentText.isEmpty {
                    result = try await manager.voiceEditText(existingText: currentText, wavData: wavData)
                } else {
                    result = try await manager.processRecording(wavData: wavData, mode: mode, style: style, smartPolish: polish)
                }
                manager.addHistory(text: result, mode: mode, style: style)
                await MainActor.run {
                    resultText = result
                    errorMessage = ""
                    isProcessing = false
                    haptic.impactOccurred()
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isProcessing = false
                }
            }
        }
    }

    private func translateCurrentResult() {
        guard !resultText.isEmpty, !isTranslating else { return }
        isTranslating = true
        let current = resultText
        Task {
            do {
                let translated = try await manager.translateBanglaToEnglish(current)
                await MainActor.run { resultText = translated; isTranslating = false }
            } catch {
                await MainActor.run { errorMessage = error.localizedDescription; isTranslating = false }
            }
        }
    }
}

struct SettingsSheet: View {
    @ObservedObject var manager: KothalipiEngineManager
    @Environment(\.dismiss) var dismiss

    @State private var geminiKey = ""
    @State private var groqKey = ""
    @State private var openaiKey = ""
    @State private var provider = "auto"
    @State private var showKeys = false
    @State private var testingProvider: String?
    @State private var testStatus: (Bool, String)?

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Primary AI Provider (Auto Fallback)")) {
                    Picker("Provider", selection: $provider) {
                        Text("Auto").tag("auto")
                        Text("Gemini").tag("gemini")
                        Text("Groq Whisper").tag("groq")
                        Text("OpenAI").tag("openai")
                    }
                    .pickerStyle(.segmented)
                }

                Section(
                    header: Text("Google Gemini API Key (ফ্রি)"),
                    footer: Link("aistudio.google.com/apikey থেকে ফ্রি Key নিন ↗", destination: URL(string: "https://aistudio.google.com/apikey")!)
                ) {
                    keyRow(placeholder: "AIzaSy...", text: $geminiKey, prov: "gemini")
                }

                Section(
                    header: Text("Groq Whisper Key (ফ্রি ব্যাকআপ)"),
                    footer: Link("console.groq.com/keys থেকে ফ্রি Key নিন ↗", destination: URL(string: "https://console.groq.com/keys")!)
                ) {
                    keyRow(placeholder: "gsk_...", text: $groqKey, prov: "groq")
                }

                Section(header: Text("OpenAI API Key (অপশনাল)")) {
                    keyRow(placeholder: "sk-...", text: $openaiKey, prov: "openai")
                }

                if let status = testStatus {
                    Section {
                        Text(status.1)
                            .font(.footnote)
                            .foregroundColor(status.0 ? .green : .red)
                    }
                }

                Section(header: Text("ভয়েস ও AI ফিচার")) {
                    Toggle("Show API Keys", isOn: $showKeys)
                    Toggle("AI Smart Polish", isOn: $manager.smartPolish)
                    Toggle("Auto-Silence Stop (২ সেকেন্ড)", isOn: $manager.autoSilenceStop)
                }
            }
            .navigationTitle("কথালিপি সেটিংস")
            .navigationBarItems(
                leading: Button("Cancel") { dismiss() },
                trailing: Button("Save") {
                    manager.apiKey = geminiKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    manager.groqApiKey = groqKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    manager.openaiApiKey = openaiKey.trimmingCharacters(in: .whitespacesAndNewlines)
                    manager.primaryProvider = provider
                    dismiss()
                }
                .fontWeight(.bold)
            )
            .onAppear {
                geminiKey = manager.apiKey
                groqKey = manager.groqApiKey
                openaiKey = manager.openaiApiKey
                provider = manager.primaryProvider
            }
        }
    }

    @ViewBuilder
    private func keyRow(placeholder: String, text: Binding<String>, prov: String) -> some View {
        HStack {
            if showKeys {
                TextField(placeholder, text: text).autocorrectionDisabled().textInputAutocapitalization(.never)
            } else {
                SecureField(placeholder, text: text).autocorrectionDisabled().textInputAutocapitalization(.never)
            }
            Button(action: { testKey(prov: prov, key: text.wrappedValue) }) {
                if testingProvider == prov {
                    ProgressView()
                } else {
                    Text("Test").font(.caption.bold())
                }
            }
            .disabled(testingProvider != nil || text.wrappedValue.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    private func testKey(prov: String, key: String) {
        testingProvider = prov
        testStatus = nil
        Task {
            do {
                let msg = try await manager.validateProviderKey(provider: prov, key: key)
                await MainActor.run { testingProvider = nil; testStatus = (true, msg) }
            } catch {
                await MainActor.run { testingProvider = nil; testStatus = (false, error.localizedDescription) }
            }
        }
    }
}

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default: (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: Double(a) / 255)
    }
}
