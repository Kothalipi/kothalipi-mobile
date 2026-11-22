import SwiftUI
import AVFoundation

/// Wispr Flow-style SwiftUI Home View for iOS
struct HomeView: View {
    @EnvironmentObject var manager: KothalipiEngineManager
    @State private var selectedTab = 0
    @State private var isRecording = false
    @State private var isProcessing = false
    @State private var isTranslating = false
    @State private var recordingSeconds = 0
    @State private var timer: Timer?

    @State private var resultText = ""
    @State private var errorMessage = ""
    @State private var copiedRecently = false
    @State private var showSettings = false
    @State private var searchText = ""
    @State private var waveBars: [CGFloat] = Array(repeating: 0.15, count: 12)

    let haptic = UIImpactFeedbackGenerator(style: .medium)

    var body: some View {
        ZStack {
            Color(hex: "08070F").ignoresSafeArea()

            // Subtle top ambient glow
            Circle()
                .fill(isRecording ? Color(hex: "EC4899").opacity(0.16) : Color(hex: "7C3AED").opacity(0.14))
                .blur(radius: 90)
                .frame(width: 320, height: 320)
                .offset(y: -260)

            VStack(spacing: 0) {
                headerBar

                if selectedTab == 0 {
                    dictateTab
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
            guard isRecording else { return }
            waveBars.removeFirst()
            waveBars.append(CGFloat(max(0.12, min(level, 1.0))))
        }
        .onReceive(NotificationCenter.default.publisher(for: KothalipiEngineManager.startDictationNotification)) { _ in
            selectedTab = 0
            if !isRecording && !isProcessing {
                toggleRecording()
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
                            .fill(manager.apiKey.isEmpty ? Color(hex: "F59E0B") : Color(hex: "10B981"))
                            .frame(width: 7, height: 7)
                        Text(manager.apiKey.isEmpty ? "API Key দিন" : "Gemini Ready")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(manager.apiKey.isEmpty ? Color(hex: "F59E0B") : Color(hex: "10B981"))
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
            VStack(spacing: 16) {
                // Missing API key banner
                if manager.apiKey.isEmpty {
                    Button(action: { showSettings = true }) {
                        HStack(spacing: 10) {
                            Image(systemName: "key.fill")
                                .foregroundColor(Color(hex: "F59E0B"))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Gemini API Key সেট করুন")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "FDE68A"))
                                Text("ফ্রি API Key বসাতে এখানে ট্যাপ করুন")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "D4D4D8"))
                            }
                            Spacer()
                            Text("Setup →")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "F59E0B"))
                        }
                        .padding(14)
                        .background(Color(hex: "F59E0B").opacity(0.14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "F59E0B").opacity(0.4), lineWidth: 1))
                        .cornerRadius(14)
                    }
                }

                // 4-Mode Pill Switcher
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

                // Style & Smart Polish Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        Button(action: {
                            haptic.impactOccurred()
                            manager.smartPolish.toggle()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "sparkles")
                                    .font(.system(size: 11))
                                Text("AI Polish")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(manager.smartPolish ? Color(hex: "A7F3D0") : Color(hex: "9E9CB3"))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(manager.smartPolish ? Color(hex: "10B981").opacity(0.2) : Color(hex: "131120"))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(manager.smartPolish ? Color(hex: "10B981") : Color.white.opacity(0.1), lineWidth: 1))
                            .cornerRadius(12)
                        }

                        ForEach([("normal", "স্বাভাবিক"), ("formal", "ফর্মাল"), ("casual", "চ্যাট"), ("bullets", "পয়েন্ট")], id: \.0) { id, label in
                            Button(action: {
                                haptic.impactOccurred()
                                manager.writingStyle = id
                            }) {
                                Text(label)
                                    .font(.system(size: 11, weight: manager.writingStyle == id ? .bold : .medium))
                                    .foregroundColor(manager.writingStyle == id ? .white : Color(hex: "9E9CB3"))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(manager.writingStyle == id ? Color(hex: "8B5CF6").opacity(0.25) : Color(hex: "131120"))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(manager.writingStyle == id ? Color(hex: "8B5CF6") : Color.white.opacity(0.1), lineWidth: 1))
                                    .cornerRadius(12)
                            }
                        }
                    }
                }

                // Center Hero Microphone Orb
                VStack(spacing: 14) {
                    ZStack {
                        Circle()
                            .fill(isRecording ? Color(hex: "EC4899").opacity(0.24) : Color(hex: "8B5CF6").opacity(0.14))
                            .frame(width: 168, height: 168)
                            .scaleEffect(isRecording ? (1.08 + CGFloat(manager.audioRecorder.audioLevel) * 0.25) : 1.0)
                            .animation(.easeOut(duration: 0.12), value: manager.audioRecorder.audioLevel)

                        Button(action: toggleRecording) {
                            ZStack {
                                Circle()
                                    .fill(micGradient)
                                    .frame(width: 108, height: 108)
                                    .shadow(color: (isRecording ? Color(hex: "EC4899") : Color(hex: "8B5CF6")).opacity(0.5), radius: 22, x: 0, y: 10)

                                if isProcessing {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .scaleEffect(1.4)
                                } else {
                                    Image(systemName: isRecording ? "stop.fill" : "mic.fill")
                                        .font(.system(size: 40, weight: .semibold))
                                        .foregroundColor(.white)
                                }
                            }
                        }
                        .disabled(isProcessing)
                    }
                    .padding(.top, 10)

                    if isRecording {
                        HStack(spacing: 5) {
                            ForEach(0..<waveBars.count, id: \.self) { i in
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(LinearGradient(colors: [Color(hex: "EC4899"), Color(hex: "8B5CF6")], startPoint: .top, endPoint: .bottom))
                                    .frame(width: 4, height: 8 + waveBars[i] * 26)
                                    .animation(.easeOut(duration: 0.08), value: waveBars[i])
                            }
                        }
                        .frame(height: 34)

                        Text(String(format: "%02d:%02d", recordingSeconds / 60, recordingSeconds % 60))
                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                            .foregroundColor(Color(hex: "EC4899"))
                    }

                    Text(statusPrompt)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(hex: "9E9CB3"))
                        .multilineTextAlignment(.center)
                }

                // Error Banner
                if !errorMessage.isEmpty {
                    HStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(Color(hex: "FCA5A5"))
                        Text(errorMessage)
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "FCA5A5"))
                        Spacer()
                    }
                    .padding(12)
                    .background(Color(hex: "EF4444").opacity(0.16))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "EF4444").opacity(0.4), lineWidth: 1))
                    .cornerRadius(14)
                }

                // Result Card
                if !resultText.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            let words = resultText.split { $0.isWhitespace }.count
                            Text("\(modeDisplayName(manager.dictationMode)) • \(words) শব্দ")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color(hex: "8B5CF6"))
                            Spacer()
                            Button(action: {
                                resultText = ""
                                copiedRecently = false
                            }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(hex: "9E9CB3"))
                            }
                        }

                        Text(resultText)
                            .font(.system(size: 17))
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
                                    .font(.system(size: 13, weight: .semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(copiedRecently ? Color(hex: "10B981") : Color(hex: "7C3AED"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }

                            Button(action: translateCurrentResult) {
                                if isTranslating {
                                    ProgressView().tint(.white)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 10)
                                } else {
                                    Label("EN", systemImage: "globe")
                                        .font(.system(size: 13, weight: .semibold))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 10)
                                }
                            }
                            .background(Color(hex: "1E1B30"))
                            .foregroundColor(.white)
                            .cornerRadius(12)
                            .disabled(isTranslating)

                            ShareLink(item: resultText) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.system(size: 14, weight: .semibold))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(Color(hex: "1E1B30"))
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                            }
                        }
                    }
                    .padding(18)
                    .background(Color(hex: "131120"))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
                    .cornerRadius(18)
                }

                // Keyboard & Action Button Quick Tip Card
                HStack(spacing: 12) {
                    Image(systemName: "keyboard.fill")
                        .font(.system(size: 20))
                        .foregroundColor(Color(hex: "06B6D4"))
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
            .padding(.bottom, 16)
        }
    }

    private var historyTab: some View {
        VStack(spacing: 12) {
            HStack {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(Color(hex: "9E9CB3"))
                    TextField("হিস্ট্রি খুঁজুন…", text: $searchText)
                        .foregroundColor(.white)
                        .font(.system(size: 14))
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
                                    Button(action: {
                                        haptic.impactOccurred()
                                        UIPasteboard.general.string = item.text
                                    }) {
                                        Image(systemName: "doc.on.doc")
                                            .font(.system(size: 13))
                                            .foregroundColor(Color(hex: "9E9CB3"))
                                    }
                                    ShareLink(item: item.text) {
                                        Image(systemName: "square.and.arrow.up")
                                            .font(.system(size: 13))
                                            .foregroundColor(Color(hex: "9E9CB3"))
                                    }
                                    Button(action: { manager.deleteHistory(id: item.id) }) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 13))
                                            .foregroundColor(Color(hex: "FCA5A5"))
                                    }
                                }
                                Text(item.text)
                                    .font(.system(size: 15))
                                    .foregroundColor(Color(hex: "F8F8FC"))
                                    .lineLimit(5)
                            }
                            .padding(14)
                            .background(Color(hex: "131120"))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.white.opacity(0.08), lineWidth: 1))
                            .cornerRadius(14)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 12)
                }
            }
        }
    }

    private var bottomTabBar: some View {
        HStack(spacing: 6) {
            tabPill(title: "ডিক্টেশন", icon: "mic.fill", index: 0)
            tabPill(title: "হিস্ট্রি (\(manager.history.count))", icon: "clock.arrow.circlepath", index: 1)
        }
        .padding(5)
        .background(Color(hex: "131120"))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: "A855F7").opacity(0.22), lineWidth: 1))
        .cornerRadius(20)
        .padding(.horizontal, 20)
        .padding(.vertical, 8)
    }

    private func tabPill(title: String, icon: String, index: Int) -> some View {
        Button(action: { selectedTab = index }) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 14))
                Text(title).font(.system(size: 13, weight: selectedTab == index ? .bold : .medium))
            }
            .foregroundColor(selectedTab == index ? .white : Color(hex: "9E9CB3"))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(
                selectedTab == index
                    ? LinearGradient(colors: [Color(hex: "7C3AED"), Color(hex: "8B5CF6")], startPoint: .leading, endPoint: .trailing)
                    : LinearGradient(colors: [.clear], startPoint: .leading, endPoint: .trailing)
            )
            .cornerRadius(15)
        }
    }

    private func modeButton(title: String, mode: String, colors: [Color]) -> some View {
        Button(action: {
            haptic.impactOccurred()
            manager.dictationMode = mode
            errorMessage = ""
        }) {
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
        if isRecording {
            return LinearGradient(colors: [Color(hex: "EC4899"), Color(hex: "7C3AED")], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
        switch manager.dictationMode {
        case "en":
            return LinearGradient(colors: [Color(hex: "2563EB"), Color(hex: "06B6D4")], startPoint: .topLeading, endPoint: .bottomTrailing)
        case "bn_to_en":
            return LinearGradient(colors: [Color(hex: "7C3AED"), Color(hex: "EC4899")], startPoint: .topLeading, endPoint: .bottomTrailing)
        case "auto":
            return LinearGradient(colors: [Color(hex: "059669"), Color(hex: "06B6D4")], startPoint: .topLeading, endPoint: .bottomTrailing)
        default:
            return LinearGradient(colors: [Color(hex: "8B5CF6"), Color(hex: "6D28D9")], startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }

    private var statusPrompt: String {
        if isProcessing { return "Gemini AI প্রসেস করছে…" }
        if isRecording {
            switch manager.dictationMode {
            case "en": return "Listening… Speak in English"
            case "bn_to_en": return "শুনছি… বাংলায় বলুন, ইংরেজিতে হবে"
            case "auto": return "শুনছি… বাংলা বা ইংরেজিতে বলুন"
            default: return "শুনছি… বাংলায় বলুন"
            }
        }
        switch manager.dictationMode {
        case "en": return "Tap mic to dictate in English"
        case "bn_to_en": return "মুখে বাংলায় বলুন, ইংরেজিতে টাইপ হবে"
        case "auto": return "বাংলা বা ইংরেজি — অটো ডিটেক্ট হবে"
        default: return "কথা বলতে মাইক ট্যাপ করুন"
        }
    }

    private func modeDisplayName(_ mode: String) -> String {
        switch mode {
        case "en": return "English"
        case "bn_to_en": return "বাংলা ➔ EN"
        case "auto": return "Auto"
        default: return "বাংলা"
        }
    }

    private func toggleRecording() {
        guard !isProcessing else { return }
        haptic.impactOccurred()

        if manager.apiKey.isEmpty {
            errorMessage = KothalipiError.missingApiKey.localizedDescription
            showSettings = true
            return
        }

        if !isRecording {
            errorMessage = ""
            copiedRecently = false
            manager.audioRecorder.startRecording { success, err in
                if success {
                    isRecording = true
                    recordingSeconds = 0
                    timer?.invalidate()
                    timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
                        recordingSeconds += 1
                    }
                } else {
                    errorMessage = err ?? "মাইক্রোফোন পারমিশন প্রয়োজন"
                }
            }
        } else {
            isRecording = false
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

            Task {
                do {
                    let result = try await manager.processRecording(
                        wavData: wavData,
                        mode: mode,
                        style: style,
                        smartPolish: polish
                    )
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
    }

    private func translateCurrentResult() {
        guard !resultText.isEmpty, !isTranslating else { return }
        isTranslating = true
        let current = resultText
        Task {
            do {
                let translated = try await manager.translateBanglaToEnglish(current)
                await MainActor.run {
                    resultText = translated
                    isTranslating = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isTranslating = false
                }
            }
        }
    }
}

struct SettingsSheet: View {
    @ObservedObject var manager: KothalipiEngineManager
    @Environment(\.dismiss) var dismiss

    @State private var apiKeyInput = ""
    @State private var showKey = false
    @State private var isTesting = false
    @State private var testStatus: (Bool, String)?

    var body: some View {
        NavigationView {
            Form {
                Section(
                    header: Text("Google Gemini API Key"),
                    footer: Text("আপনার ফ্রি Gemini API Key দিন। এটি আপনার ডিভাইসেই সুরক্ষিত থাকবে।")
                ) {
                    HStack {
                        if showKey {
                            TextField("AIzaSy...", text: $apiKeyInput)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                        } else {
                            SecureField("AIzaSy...", text: $apiKeyInput)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                        }
                        Button(action: { showKey.toggle() }) {
                            Image(systemName: showKey ? "eye.slash" : "eye")
                                .foregroundColor(.secondary)
                        }
                    }

                    Button(action: testKey) {
                        HStack {
                            if isTesting {
                                ProgressView().padding(.trailing, 4)
                            }
                            Text("Test API Key")
                        }
                    }
                    .disabled(isTesting || apiKeyInput.trimmingCharacters(in: .whitespaces).isEmpty)

                    if let status = testStatus {
                        Text(status.1)
                            .font(.footnote)
                            .foregroundColor(status.0 ? .green : .red)
                    }

                    if let url = URL(string: "https://aistudio.google.com/apikey") {
                        Link("Google AI Studio থেকে ফ্রি Key নিন ↗", destination: url)
                    }
                }

                Section(header: Text("AI ফিচার")) {
                    Toggle("AI Smart Polish", isOn: $manager.smartPolish)
                }
            }
            .navigationTitle("কথালিপি সেটিংস")
            .navigationBarItems(
                leading: Button("Cancel") { dismiss() },
                trailing: Button("Save") {
                    manager.apiKey = apiKeyInput.trimmingCharacters(in: .whitespacesAndNewlines)
                    dismiss()
                }
                .fontWeight(.bold)
            )
            .onAppear {
                apiKeyInput = manager.apiKey
            }
        }
    }

    private func testKey() {
        isTesting = true
        testStatus = nil
        let key = apiKeyInput
        Task {
            do {
                let msg = try await manager.validateApiKey(key)
                await MainActor.run {
                    isTesting = false
                    testStatus = (true, msg)
                }
            } catch {
                await MainActor.run {
                    isTesting = false
                    testStatus = (false, error.localizedDescription)
                }
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
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
