import Foundation
import AVFoundation

struct DictationHistoryItem: Identifiable, Codable, Equatable {
    let id: UUID
    let text: String
    let mode: String
    let style: String
    let timestamp: Date
}

struct VoiceSnippetItem: Identifiable, Codable, Equatable {
    let id: UUID
    let trigger: String
    let content: String
}

/// Thread-safe, crash-proof 16kHz mono 16-bit WAV audio recorder with live RMS amplitude & Auto-Silence Stop (VAD).
final class AudioRecorder: NSObject, ObservableObject {
    private var audioEngine: AVAudioEngine?
    private var audioFile: AVAudioFile?
    private var tempWavUrl: URL?
    private let fileQueue = DispatchQueue(label: "com.kothalipi.audio.write")

    @Published var audioLevel: Float = 0.0

    func startRecording(
        autoSilenceStop: Bool = false,
        silenceDuration: TimeInterval = 2.0,
        onSilenceDetected: (() -> Void)? = nil,
        completion: @escaping (Bool, String?) -> Void
    ) {
        let session = AVAudioSession.sharedInstance()
        session.requestRecordPermission { [weak self] granted in
            guard let self else { return }
            guard granted else {
                DispatchQueue.main.async {
                    completion(false, "মাইক্রোফোন পারমিশন প্রয়োজন। Settings থেকে অনুমতি দিন।")
                }
                return
            }

            do {
                try session.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker, .allowBluetooth])
                try session.setActive(true)

                let engine = AVAudioEngine()
                let inputNode = engine.inputNode
                let inputFormat = inputNode.outputFormat(forBus: 0)

                guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
                    DispatchQueue.main.async { completion(false, "মাইক্রোফোন হার্ডওয়্যার পাওয়া যায়নি।") }
                    return
                }

                guard let targetFormat = AVAudioFormat(
                    commonFormat: .pcmFormatInt16,
                    sampleRate: 16000,
                    channels: 1,
                    interleaved: true
                ), let converter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
                    DispatchQueue.main.async { completion(false, "অডিও কনভার্টার চালু করা যায়নি।") }
                    return
                }

                let wavUrl = FileManager.default.temporaryDirectory
                    .appendingPathComponent("kothalipi_record_\(UUID().uuidString).wav")
                let file = try AVAudioFile(forWriting: wavUrl, settings: targetFormat.settings)

                self.fileQueue.sync {
                    self.tempWavUrl = wavUrl
                    self.audioFile = file
                }

                let ratio = targetFormat.sampleRate / inputFormat.sampleRate
                var hasHeardSpeech = false
                var speechFrames = 0
                var silenceStart: Date?
                var silenceTriggered = false

                inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, _ in
                    guard let self else { return }

                    var normalized: Float = 0
                    if let channelData = buffer.floatChannelData?[0] {
                        let frameCount = Int(buffer.frameLength)
                        if frameCount > 0 {
                            var sum: Float = 0
                            for i in 0..<frameCount {
                                let s = channelData[i]
                                sum += s * s
                            }
                            let rms = sqrt(sum / Float(frameCount))
                            normalized = min(max(rms * 4.5, 0.0), 1.0)
                            DispatchQueue.main.async {
                                self.audioLevel = normalized
                            }
                        }
                    }

                    // Auto-Silence Stop (VAD)
                    if autoSilenceStop && !silenceTriggered {
                        if normalized > 0.09 {
                            speechFrames += 1
                            if speechFrames >= 4 { hasHeardSpeech = true }
                            silenceStart = nil
                        } else if hasHeardSpeech && normalized < 0.045 {
                            if let start = silenceStart {
                                if Date().timeIntervalSince(start) >= silenceDuration {
                                    silenceTriggered = true
                                    DispatchQueue.main.async { onSilenceDetected?() }
                                }
                            } else {
                                silenceStart = Date()
                            }
                        }
                    }

                    let outputFrameCapacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 64
                    guard let convertedBuffer = AVAudioPCMBuffer(
                        pcmFormat: targetFormat,
                        frameCapacity: max(outputFrameCapacity, 512)
                    ) else { return }

                    var hasProvidedInput = false
                    var convError: NSError?
                    let status = converter.convert(to: convertedBuffer, error: &convError) { _, outStatus in
                        if hasProvidedInput {
                            outStatus.pointee = .noDataNow
                            return nil
                        }
                        hasProvidedInput = true
                        outStatus.pointee = .haveData
                        return buffer
                    }

                    if status != .error, convertedBuffer.frameLength > 0 {
                        self.fileQueue.async {
                            try? self.audioFile?.write(from: convertedBuffer)
                        }
                    }
                }

                try engine.start()
                self.audioEngine = engine
                DispatchQueue.main.async { completion(true, nil) }
            } catch {
                DispatchQueue.main.async {
                    completion(false, "রেকর্ডিং শুরু করা যায়নি: \(error.localizedDescription)")
                }
            }
        }
    }

    func stopRecording() -> Data? {
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine?.stop()
        audioEngine = nil

        DispatchQueue.main.async { self.audioLevel = 0.0 }

        let urlToRead: URL? = fileQueue.sync {
            self.audioFile = nil
            return self.tempWavUrl
        }

        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        guard let url = urlToRead, let data = try? Data(contentsOf: url) else { return nil }
        try? FileManager.default.removeItem(at: url)
        return data.count >= 1000 ? data : nil
    }
}

/// Shared multi-provider engine manager for Kothalipi iOS App & Keyboard Extension.
final class KothalipiEngineManager: ObservableObject {
    static let shared = KothalipiEngineManager()
    static let appGroupSuite = "group.com.kothalipi.mobile"
    static let startDictationNotification = Notification.Name("KothalipiStartDictation")

    let audioRecorder = AudioRecorder()
    let offlineSpeech = OfflineSpeechRecognizer()

    private let defaults: UserDefaults
    private let apiKeyDefaultsKey = "kothalipi_gemini_api_key"
    private let groqKeyDefaultsKey = "kothalipi_groq_api_key"
    private let openaiKeyDefaultsKey = "kothalipi_openai_api_key"
    private let providerDefaultsKey = "kothalipi_primary_provider"
    private let modeDefaultsKey = "kothalipi_dictation_mode"
    private let styleDefaultsKey = "kothalipi_writing_style"
    private let polishDefaultsKey = "kothalipi_smart_polish"
    private let vadDefaultsKey = "kothalipi_auto_silence_stop"
    private let offlineDefaultsKey = "kothalipi_offline_mode"
    private let historyDefaultsKey = "kothalipi_history_v1"
    private let snippetsDefaultsKey = "kothalipi_snippets_v1"

    static let geminiModels = [
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-flash-latest"
    ]

    @Published var apiKey: String {
        didSet { defaults.set(apiKey.trimmingCharacters(in: .whitespacesAndNewlines), forKey: apiKeyDefaultsKey) }
    }
    @Published var groqApiKey: String {
        didSet { defaults.set(groqApiKey.trimmingCharacters(in: .whitespacesAndNewlines), forKey: groqKeyDefaultsKey) }
    }
    @Published var openaiApiKey: String {
        didSet { defaults.set(openaiApiKey.trimmingCharacters(in: .whitespacesAndNewlines), forKey: openaiKeyDefaultsKey) }
    }
    @Published var primaryProvider: String {
        didSet { defaults.set(primaryProvider, forKey: providerDefaultsKey) }
    }
    @Published var dictationMode: String {
        didSet { defaults.set(dictationMode, forKey: modeDefaultsKey) }
    }
    @Published var writingStyle: String {
        didSet { defaults.set(writingStyle, forKey: styleDefaultsKey) }
    }
    @Published var smartPolish: Bool {
        didSet { defaults.set(smartPolish, forKey: polishDefaultsKey) }
    }
    @Published var autoSilenceStop: Bool {
        didSet { defaults.set(autoSilenceStop, forKey: vadDefaultsKey) }
    }
    @Published var offlineMode: Bool {
        didSet { defaults.set(offlineMode, forKey: offlineDefaultsKey) }
    }

    @Published var history: [DictationHistoryItem] = []
    @Published var snippets: [VoiceSnippetItem] = []

    var hasAnyApiKey: Bool {
        !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !groqApiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !openaiApiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    init() {
        self.defaults = UserDefaults(suiteName: Self.appGroupSuite) ?? UserDefaults.standard
        self.apiKey = defaults.string(forKey: apiKeyDefaultsKey)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.groqApiKey = defaults.string(forKey: groqKeyDefaultsKey)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.openaiApiKey = defaults.string(forKey: openaiKeyDefaultsKey)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.primaryProvider = defaults.string(forKey: providerDefaultsKey) ?? "auto"
        self.dictationMode = defaults.string(forKey: modeDefaultsKey) ?? "bn"
        self.writingStyle = defaults.string(forKey: styleDefaultsKey) ?? "normal"
        self.smartPolish = defaults.object(forKey: polishDefaultsKey) == nil ? true : defaults.bool(forKey: polishDefaultsKey)
        self.autoSilenceStop = defaults.bool(forKey: vadDefaultsKey)
        self.offlineMode = defaults.bool(forKey: offlineDefaultsKey)
        self.history = loadHistory()
        self.snippets = loadSnippets()
    }

    // MARK: - Snippets
    func addSnippet(trigger: String, content: String) {
        let t = trigger.trimmingCharacters(in: .whitespacesAndNewlines)
        let c = content.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !c.isEmpty else { return }
        let updated = [VoiceSnippetItem(id: UUID(), trigger: t, content: c)] + snippets
        snippets = updated
        if let data = try? JSONEncoder().encode(updated) {
            defaults.set(data, forKey: snippetsDefaultsKey)
        }
    }

    func deleteSnippet(id: UUID) {
        let updated = snippets.filter { $0.id != id }
        snippets = updated
        if let data = try? JSONEncoder().encode(updated) {
            defaults.set(data, forKey: snippetsDefaultsKey)
        }
    }

    func applySnippets(to text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !snippets.isEmpty else { return trimmed }

        let punct = CharacterSet(charactersIn: "।.,?!\"';: ")
        let normFull = trimmed.trimmingCharacters(in: punct).lowercased()

        for s in snippets {
            let normTrig = s.trigger.trimmingCharacters(in: punct).lowercased()
            if !normTrig.isEmpty && normFull == normTrig {
                return s.content
            }
        }

        var output = trimmed
        for s in snippets.sorted(by: { $0.trigger.count > $1.trigger.count }) {
            let trig = s.trigger.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trig.isEmpty {
                output = output.replacingOccurrences(of: trig, with: s.content, options: .caseInsensitive)
            }
        }
        return output
    }

    private func loadSnippets() -> [VoiceSnippetItem] {
        guard let data = defaults.data(forKey: snippetsDefaultsKey),
              let items = try? JSONDecoder().decode([VoiceSnippetItem].self, from: data) else {
            return []
        }
        return items
    }

    // MARK: - History
    func addHistory(text: String, mode: String, style: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let item = DictationHistoryItem(id: UUID(), text: trimmed, mode: mode, style: style, timestamp: Date())
        let updated = Array(([item] + history).prefix(50))
        DispatchQueue.main.async { self.history = updated }
        if let data = try? JSONEncoder().encode(updated) {
            defaults.set(data, forKey: historyDefaultsKey)
        }
    }

    func deleteHistory(id: UUID) {
        let updated = history.filter { $0.id != id }
        history = updated
        if let data = try? JSONEncoder().encode(updated) {
            defaults.set(data, forKey: historyDefaultsKey)
        }
    }

    func clearHistory() {
        history = []
        defaults.removeObject(forKey: historyDefaultsKey)
    }

    private func loadHistory() -> [DictationHistoryItem] {
        guard let data = defaults.data(forKey: historyDefaultsKey),
              let items = try? JSONDecoder().decode([DictationHistoryItem].self, from: data) else {
            return []
        }
        return items
    }

    // MARK: - Provider Ordering & Validation
    private func providerOrder() -> [(String, String)] {
        let ordered: [(String, String)]
        switch primaryProvider {
        case "groq": ordered = [("groq", groqApiKey), ("gemini", apiKey), ("openai", openaiApiKey)]
        case "openai": ordered = [("openai", openaiApiKey), ("gemini", apiKey), ("groq", groqApiKey)]
        default: ordered = [("gemini", apiKey), ("groq", groqApiKey), ("openai", openaiApiKey)]
        }
        return ordered.compactMap { name, key in
            let k = key.trimmingCharacters(in: .whitespacesAndNewlines)
            return k.isEmpty ? nil : (name, k)
        }
    }

    func validateProviderKey(provider: String, key: String) async throws -> String {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw KothalipiError.missingApiKey }

        var request: URLRequest
        switch provider {
        case "groq":
            guard let url = URL(string: "https://api.groq.com/openai/v1/models") else { throw KothalipiError.badApiKey }
            request = URLRequest(url: url)
            request.addValue("Bearer \(trimmed)", forHTTPHeaderField: "Authorization")
        case "openai":
            guard let url = URL(string: "https://api.openai.com/v1/models") else { throw KothalipiError.badApiKey }
            request = URLRequest(url: url)
            request.addValue("Bearer \(trimmed)", forHTTPHeaderField: "Authorization")
        default:
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models?key=\(trimmed)&pageSize=5") else {
                throw KothalipiError.badApiKey
            }
            request = URLRequest(url: url)
        }
        request.timeoutInterval = 12

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw KothalipiError.network("ইন্টারনেট সংযোগ চেক করুন")
        }
        if (200...299).contains(httpResp.statusCode) {
            return "\(provider.capitalized) সংযোগ সফল ✓"
        }
        let body = String(data: data, encoding: .utf8) ?? ""
        if httpResp.statusCode == 401 || httpResp.statusCode == 403 || body.lowercased().contains("api_key_invalid") {
            throw KothalipiError.badApiKey
        }
        if httpResp.statusCode == 429 { throw KothalipiError.rateLimited }
        throw KothalipiError.network("HTTP \(httpResp.statusCode)")
    }

    // MARK: - Main Transcription Pipeline (Multi-Provider + Snippets)
    func processRecording(
        wavData: Data,
        mode: String,
        style: String = "normal",
        smartPolish: Bool = true
    ) async throws -> String {
        guard wavData.count >= 1000 else { throw KothalipiError.emptyAudio }
        let providers = providerOrder()
        guard !providers.isEmpty else { throw KothalipiError.missingApiKey }

        var lastError: Error = KothalipiError.network("All providers failed")

        for (prov, key) in providers {
            do {
                let rawText: String
                switch prov {
                case "groq":
                    rawText = try await transcribeWhisperAndFormat(
                        baseUrl: "https://api.groq.com/openai/v1",
                        whisperModel: "whisper-large-v3-turbo",
                        chatModel: "llama-3.3-70b-versatile",
                        apiKey: key,
                        wavData: wavData,
                        mode: mode,
                        style: style,
                        smartPolish: smartPolish
                    )
                case "openai":
                    rawText = try await transcribeWhisperAndFormat(
                        baseUrl: "https://api.openai.com/v1",
                        whisperModel: "whisper-1",
                        chatModel: "gpt-4o-mini",
                        apiKey: key,
                        wavData: wavData,
                        mode: mode,
                        style: style,
                        smartPolish: smartPolish
                    )
                default:
                    rawText = try await transcribeGemini(
                        key: key,
                        wavData: wavData,
                        mode: mode,
                        style: style,
                        smartPolish: smartPolish
                    )
                }
                return applySnippets(to: rawText)
            } catch KothalipiError.emptyAudio {
                throw KothalipiError.emptyAudio
            } catch {
                lastError = error
            }
        }

        throw lastError
    }

    // MARK: - Voice Edit Command (AI Rewrite Mode)
    func voiceEditText(existingText: String, wavData: Data) async throws -> String {
        guard wavData.count >= 1000 else { throw KothalipiError.emptyAudio }
        let providers = providerOrder()
        guard !providers.isEmpty else { throw KothalipiError.missingApiKey }

        var lastError: Error = KothalipiError.network("Voice edit failed")

        for (prov, key) in providers {
            do {
                switch prov {
                case "groq":
                    let cmd = try await callWhisperEndpoint(
                        baseUrl: "https://api.groq.com/openai/v1",
                        model: "whisper-large-v3-turbo",
                        apiKey: key,
                        wavData: wavData,
                        mode: "auto"
                    )
                    return try await callOpenAIChat(
                        baseUrl: "https://api.groq.com/openai/v1",
                        model: "llama-3.3-70b-versatile",
                        apiKey: key,
                        system: "You are an expert bilingual (Bengali & English) writing assistant. Rewrite the EXISTING TEXT strictly following the VOICE EDIT COMMAND. Output ONLY the final rewritten text.",
                        user: "EXISTING TEXT:\n\"\"\"\n\(existingText)\n\"\"\"\n\nVOICE EDIT COMMAND:\n\(cmd)"
                    )
                case "openai":
                    let cmd = try await callWhisperEndpoint(
                        baseUrl: "https://api.openai.com/v1",
                        model: "whisper-1",
                        apiKey: key,
                        wavData: wavData,
                        mode: "auto"
                    )
                    return try await callOpenAIChat(
                        baseUrl: "https://api.openai.com/v1",
                        model: "gpt-4o-mini",
                        apiKey: key,
                        system: "You are an expert bilingual (Bengali & English) writing assistant. Rewrite the EXISTING TEXT strictly following the VOICE EDIT COMMAND. Output ONLY the final rewritten text.",
                        user: "EXISTING TEXT:\n\"\"\"\n\(existingText)\n\"\"\"\n\nVOICE EDIT COMMAND:\n\(cmd)"
                    )
                default:
                    let prompt = """
                    You are an expert bilingual (Bengali & English) writing assistant.
                    EXISTING TEXT:
                    \"\"\"
                    \(existingText)
                    \"\"\"
                    Listen to the attached audio: it contains the user's VOICE EDIT COMMAND (e.g. 'এটাকে ফর্মাল ইমেইল বানাও', 'ইংরেজিতে শর্ট রিপ্লাই দাও', 'পয়েন্ট আকারে সাজাও').
                    Apply the instruction to the EXISTING TEXT. Output ONLY the final rewritten text without quotes or commentary.
                    """
                    let body: [String: Any] = [
                        "contents": [[
                            "parts": [
                                ["inlineData": ["mimeType": "audio/wav", "data": wavData.base64EncodedString()]],
                                ["text": prompt]
                            ]
                        ]],
                        "generationConfig": ["temperature": 0.2]
                    ]
                    return try await callGeminiWithFallback(key: key, body: body, isAudio: true)
                }
            } catch KothalipiError.emptyAudio {
                throw KothalipiError.emptyAudio
            } catch {
                lastError = error
            }
        }

        throw lastError
    }

    func translateBanglaToEnglish(_ text: String) async throws -> String {
        let providers = providerOrder()
        guard !providers.isEmpty else { throw KothalipiError.missingApiKey }
        var lastErr: Error = KothalipiError.network("Translation failed")

        for (prov, key) in providers {
            do {
                switch prov {
                case "groq":
                    return try await callOpenAIChat(
                        baseUrl: "https://api.groq.com/openai/v1",
                        model: "llama-3.3-70b-versatile",
                        apiKey: key,
                        system: "Translate the following spoken Bangla text into natural, fluent English. Output ONLY the translated English text.",
                        user: text
                    )
                case "openai":
                    return try await callOpenAIChat(
                        baseUrl: "https://api.openai.com/v1",
                        model: "gpt-4o-mini",
                        apiKey: key,
                        system: "Translate the following spoken Bangla text into natural, fluent English. Output ONLY the translated English text.",
                        user: text
                    )
                default:
                    let prompt = "Translate the following spoken Bangla text into natural, fluent English. Output ONLY the translated English text with normal punctuation.\n\nText: \(text)"
                    let body: [String: Any] = [
                        "contents": [["parts": [["text": prompt]]]],
                        "generationConfig": ["temperature": 0.0]
                    ]
                    return try await callGeminiWithFallback(key: key, body: body, isAudio: false)
                }
            } catch {
                lastErr = error
            }
        }
        throw lastErr
    }

    // MARK: - Internal Provider Implementations
    private func transcribeGemini(
        key: String,
        wavData: Data,
        mode: String,
        style: String,
        smartPolish: Bool
    ) async throws -> String {
        let polishRule = smartPolish
            ? " Remove filler words (um, uh, ah, মানে, আর কি), false starts, and stutters while preserving the exact meaning."
            : ""
        let styleRule: String
        switch style {
        case "formal": styleRule = " Format the output in a polite, professional tone suitable for emails."
        case "casual": styleRule = " Keep the tone natural, friendly, and conversational for chat messages."
        case "bullets": styleRule = " Organize the spoken points into clean bullet points starting with '• '."
        default: styleRule = ""
        }

        let basePrompt: String
        switch mode {
        case "en":
            basePrompt = "Transcribe the spoken audio accurately in English. Output ONLY the final English text with proper punctuation and capitalization. If there is no speech, output an empty string."
        case "bn_to_en":
            basePrompt = "Listen to this spoken Bengali (Bangla) audio and translate it directly into natural, fluent English. Output ONLY the translated English text with proper punctuation. If there is no speech, output an empty string."
        case "auto":
            basePrompt = "Transcribe the spoken audio accurately in the language spoken (Bengali script বাংলা for Bangla, Latin script for English). Use proper punctuation. Output ONLY the final text."
        default:
            basePrompt = "Transcribe the spoken audio accurately in Bengali script (বাংলা হরফে). Output ONLY the final Bengali text with proper punctuation (দাঁড়ি ।, কমা ,, প্রশ্নবোধক ?). Do not translate into English."
        }

        let body: [String: Any] = [
            "contents": [[
                "parts": [
                    ["inlineData": ["mimeType": "audio/wav", "data": wavData.base64EncodedString()]],
                    ["text": basePrompt + polishRule + styleRule]
                ]
            ]],
            "generationConfig": ["temperature": 0.1]
        ]
        return try await callGeminiWithFallback(key: key, body: body, isAudio: true)
    }

    private func transcribeWhisperAndFormat(
        baseUrl: String,
        whisperModel: String,
        chatModel: String,
        apiKey: String,
        wavData: Data,
        mode: String,
        style: String,
        smartPolish: Bool
    ) async throws -> String {
        let raw = try await callWhisperEndpoint(
            baseUrl: baseUrl,
            model: whisperModel,
            apiKey: apiKey,
            wavData: wavData,
            mode: mode
        )
        if !smartPolish && style == "normal" { return raw }

        let styleRule: String
        switch style {
        case "formal": styleRule = "Rewrite in a polite, professional tone suitable for emails."
        case "casual": styleRule = "Keep it natural, friendly, and concise for chat messages."
        case "bullets": styleRule = "Format the points into clean bullet points starting with '• '."
        default: styleRule = "Maintain exact original tone and meaning."
        }
        let sys = "You are an expert Bengali and English transcription editor. Fix punctuation and remove filler words (um, uh, মানে, আর কি). \(styleRule) Do NOT translate. Output ONLY the final text."
        return (try? await callOpenAIChat(baseUrl: baseUrl, model: chatModel, apiKey: apiKey, system: sys, user: raw)) ?? raw
    }

    private func callWhisperEndpoint(
        baseUrl: String,
        model: String,
        apiKey: String,
        wavData: Data,
        mode: String
    ) async throws -> String {
        let path = mode == "bn_to_en" ? "/audio/translations" : "/audio/transcriptions"
        guard let url = URL(string: baseUrl + path) else { throw KothalipiError.network("Invalid URL") }

        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()

        func appendField(_ name: String, _ value: String) {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }

        appendField("model", model)
        appendField("response_format", "json")
        if mode == "bn" { appendField("language", "bn") }
        else if mode == "en" { appendField("language", "en") }

        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: audio/wav\r\n\r\n".data(using: .utf8)!)
        body.append(wavData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 30
        req.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.httpBody = body

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse else { throw KothalipiError.network("No response") }
        if (200...299).contains(http.statusCode) {
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let text = json["text"] as? String {
                let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cleaned.isEmpty { return cleaned }
            }
            throw KothalipiError.emptyAudio
        }
        if http.statusCode == 401 || http.statusCode == 403 { throw KothalipiError.badApiKey }
        if http.statusCode == 429 { throw KothalipiError.rateLimited }
        throw KothalipiError.network("HTTP \(http.statusCode)")
    }

    private func callOpenAIChat(
        baseUrl: String,
        model: String,
        apiKey: String,
        system: String,
        user: String
    ) async throws -> String {
        guard let url = URL(string: baseUrl + "/chat/completions") else { throw KothalipiError.network("Invalid URL") }
        let payload: [String: Any] = [
            "model": model,
            "temperature": 0.1,
            "messages": [
                ["role": "system", "content": system],
                ["role": "user", "content": user]
            ]
        ]
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.timeoutInterval = 20
        req.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse else { throw KothalipiError.network("No response") }
        if (200...299).contains(http.statusCode),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let choices = json["choices"] as? [[String: Any]],
           let msg = choices.first?["message"] as? [String: Any],
           let content = msg["content"] as? String {
            return content.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if http.statusCode == 401 || http.statusCode == 403 { throw KothalipiError.badApiKey }
        if http.statusCode == 429 { throw KothalipiError.rateLimited }
        throw KothalipiError.network("HTTP \(http.statusCode)")
    }

    private func callGeminiWithFallback(key: String, body: [String: Any], isAudio: Bool) async throws -> String {
        var lastError = ""
        var sawRateLimit = false

        for model in Self.geminiModels {
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=\(key)") else {
                continue
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.timeoutInterval = 30
            request.addValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                guard let httpResp = response as? HTTPURLResponse else { continue }
                let status = httpResp.statusCode

                if (200...299).contains(status) {
                    if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let candidates = json["candidates"] as? [[String: Any]],
                       let content = candidates.first?["content"] as? [String: Any],
                       let parts = content["parts"] as? [[String: Any]] {
                        let combined = parts.compactMap { $0["text"] as? String }.joined()
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                        if !combined.isEmpty { return combined }
                        if isAudio { throw KothalipiError.emptyAudio }
                    }
                    lastError = "খালি রেসপন্স"
                } else {
                    let respText = String(data: data, encoding: .utf8) ?? ""
                    let lower = respText.lowercased()
                    if status == 401 || status == 403 || lower.contains("api_key_invalid") || lower.contains("api key not valid") {
                        throw KothalipiError.badApiKey
                    }
                    if status == 429 {
                        sawRateLimit = true
                        lastError = "HTTP 429"
                        continue
                    }
                    lastError = "HTTP \(status)"
                }
            } catch let err as KothalipiError {
                throw err
            } catch {
                lastError = error.localizedDescription.replacingOccurrences(of: key, with: "••••")
            }
        }

        if sawRateLimit { throw KothalipiError.rateLimited }
        throw KothalipiError.network(lastError)
    }
}

enum KothalipiError: LocalizedError {
    case missingApiKey
    case badApiKey
    case rateLimited
    case emptyAudio
    case network(String)

    var errorDescription: String? {
        switch self {
        case .missingApiKey:
            return "কোনো API Key সেট করা নেই! সেটিংস থেকে Gemini, Groq বা OpenAI Key দিন।"
        case .badApiKey:
            return "ভুল বা অকার্যকর API Key! সেটিংস থেকে সঠিক Key দিন।"
        case .rateLimited:
            return "ফ্রি API কোটা সীমা পূর্ণ হয়েছে। ব্যাকআপ হিসেবে Groq Key যোগ করুন অথবা একটু পর চেষ্টা করুন।"
        case .emptyAudio:
            return "কোনো কথা শোনা যায়নি। অনুগ্রহ করে মাইক্রোফোনে স্পষ্ট করে বলুন।"
        case .network:
            return "ইন্টারনেট সংযোগ বা AI সার্ভারে সমস্যা হয়েছে। আবার চেষ্টা করুন।"
        }
    }
}
