import Foundation
import AVFoundation

struct DictationHistoryItem: Identifiable, Codable, Equatable {
    let id: UUID
    let text: String
    let mode: String
    let style: String
    let timestamp: Date
}

/// Thread-safe, crash-proof 16kHz mono 16-bit WAV audio recorder with real-time RMS amplitude.
final class AudioRecorder: NSObject, ObservableObject {
    private var audioEngine: AVAudioEngine?
    private var audioFile: AVAudioFile?
    private var tempWavUrl: URL?
    private let fileQueue = DispatchQueue(label: "com.kothalipi.audio.write")

    @Published var audioLevel: Float = 0.0

    func startRecording(completion: @escaping (Bool, String?) -> Void) {
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
                    DispatchQueue.main.async {
                        completion(false, "মাইক্রোফোন হার্ডওয়্যার পাওয়া যায়নি।")
                    }
                    return
                }

                guard let targetFormat = AVAudioFormat(
                    commonFormat: .pcmFormatInt16,
                    sampleRate: 16000,
                    channels: 1,
                    interleaved: true
                ) else {
                    DispatchQueue.main.async {
                        completion(false, "অডিও ফরম্যাট তৈরি করা যায়নি।")
                    }
                    return
                }

                guard let converter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
                    DispatchQueue.main.async {
                        completion(false, "অডিও কনভার্টার চালু করা যায়নি।")
                    }
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

                inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, _ in
                    guard let self else { return }

                    // Compute live RMS amplitude from input float channel data for waveform UI
                    if let channelData = buffer.floatChannelData?[0] {
                        let frameCount = Int(buffer.frameLength)
                        if frameCount > 0 {
                            var sum: Float = 0
                            for i in 0..<frameCount {
                                let s = channelData[i]
                                sum += s * s
                            }
                            let rms = sqrt(sum / Float(frameCount))
                            let normalized = min(max(rms * 4.5, 0.0), 1.0)
                            DispatchQueue.main.async {
                                self.audioLevel = normalized
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

                DispatchQueue.main.async {
                    completion(true, nil)
                }
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

        DispatchQueue.main.async {
            self.audioLevel = 0.0
        }

        let urlToRead: URL? = fileQueue.sync {
            // Closing AVAudioFile flushes the WAV header length cleanly
            self.audioFile = nil
            return self.tempWavUrl
        }

        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        guard let url = urlToRead, let data = try? Data(contentsOf: url) else {
            return nil
        }
        try? FileManager.default.removeItem(at: url)

        // Require at least 1000 bytes (44-byte WAV header + >30ms of audio)
        return data.count >= 1000 ? data : nil
    }
}

/// Shared engine manager for Kothalipi iOS App & Keyboard Extension.
final class KothalipiEngineManager: ObservableObject {
    static let shared = KothalipiEngineManager()
    static let appGroupSuite = "group.com.kothalipi.mobile"
    static let startDictationNotification = Notification.Name("KothalipiStartDictation")

    let audioRecorder = AudioRecorder()

    private let defaults: UserDefaults
    private let apiKeyDefaultsKey = "kothalipi_gemini_api_key"
    private let modeDefaultsKey = "kothalipi_dictation_mode"
    private let styleDefaultsKey = "kothalipi_writing_style"
    private let polishDefaultsKey = "kothalipi_smart_polish"
    private let historyDefaultsKey = "kothalipi_history_v1"

    static let geminiModels = [
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-flash-latest"
    ]

    @Published var apiKey: String {
        didSet {
            let trimmed = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
            defaults.set(trimmed, forKey: apiKeyDefaultsKey)
        }
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

    @Published var history: [DictationHistoryItem] = []

    init() {
        self.defaults = UserDefaults(suiteName: Self.appGroupSuite) ?? UserDefaults.standard
        self.apiKey = defaults.string(forKey: apiKeyDefaultsKey)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.dictationMode = defaults.string(forKey: modeDefaultsKey) ?? "bn"
        self.writingStyle = defaults.string(forKey: styleDefaultsKey) ?? "normal"
        if defaults.object(forKey: polishDefaultsKey) == nil {
            self.smartPolish = true
        } else {
            self.smartPolish = defaults.bool(forKey: polishDefaultsKey)
        }
        self.history = loadHistory()
    }

    func addHistory(text: String, mode: String, style: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let item = DictationHistoryItem(
            id: UUID(),
            text: trimmed,
            mode: mode,
            style: style,
            timestamp: Date()
        )
        let updated = Array(([item] + history).prefix(50))
        DispatchQueue.main.async {
            self.history = updated
        }
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

    func validateApiKey(_ keyToTest: String) async throws -> String {
        let key = keyToTest.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            throw KothalipiError.missingApiKey
        }
        guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models?key=\(key)&pageSize=5") else {
            throw KothalipiError.badApiKey
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 12
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResp = response as? HTTPURLResponse else {
            throw KothalipiError.network("ইন্টারনেট সংযোগ চেক করুন")
        }
        if (200...299).contains(httpResp.statusCode) {
            return "সংযোগ সফল ✓"
        }
        let body = String(data: data, encoding: .utf8) ?? ""
        if httpResp.statusCode == 401 || httpResp.statusCode == 403 || body.lowercased().contains("api_key_invalid") {
            throw KothalipiError.badApiKey
        }
        if httpResp.statusCode == 429 {
            throw KothalipiError.rateLimited
        }
        throw KothalipiError.network("HTTP \(httpResp.statusCode)")
    }

    func processRecording(
        wavData: Data,
        mode: String,
        style: String = "normal",
        smartPolish: Bool = true
    ) async throws -> String {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            throw KothalipiError.missingApiKey
        }
        guard wavData.count >= 1000 else {
            throw KothalipiError.emptyAudio
        }

        let b64 = wavData.base64EncodedString()
        let polishRule = smartPolish
            ? " Remove filler words (um, uh, ah, মানে, আর কি), false starts, and stutters while preserving the exact meaning."
            : ""
        let styleRule: String
        switch style {
        case "formal":
            styleRule = " Format the output in a polite, professional tone suitable for emails or work."
        case "casual":
            styleRule = " Keep the tone natural, friendly, and conversational for chat messages."
        case "bullets":
            styleRule = " Organize the spoken points into clean bullet points starting with '• '."
        default:
            styleRule = ""
        }

        let basePrompt: String
        switch mode {
        case "en":
            basePrompt = "Transcribe the spoken audio accurately in English. Output ONLY the final English text with proper punctuation and capitalization. If there is no speech, output an empty string. Do not add commentary."
        case "bn_to_en":
            basePrompt = "Listen to this spoken Bengali (Bangla) audio and translate it directly into natural, fluent English. Output ONLY the translated English text with proper punctuation and capitalization. If there is no speech, output an empty string. Do not add quotes or commentary."
        case "auto":
            basePrompt = "Transcribe the spoken audio accurately in the language spoken (Bengali script বাংলা for Bangla, Latin script for English). Use proper punctuation. Output ONLY the final text. If there is no speech, output an empty string."
        default:
            basePrompt = "Transcribe the spoken audio accurately in Bengali script (বাংলা হরফে). Output ONLY the final Bengali text with proper punctuation (দাঁড়ি ।, কমা ,, প্রশ্নবোধক ?). Do not translate into English. If there is no speech, output an empty string."
        }

        let body: [String: Any] = [
            "contents": [
                [
                    "parts": [
                        ["inlineData": ["mimeType": "audio/wav", "data": b64]],
                        ["text": basePrompt + polishRule + styleRule]
                    ]
                ]
            ],
            "generationConfig": ["temperature": 0.1]
        ]

        return try await callGeminiWithFallback(key: key, body: body, isAudio: true)
    }

    func translateBanglaToEnglish(_ text: String) async throws -> String {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { throw KothalipiError.missingApiKey }
        let prompt = "Translate the following spoken Bangla text into natural, fluent English. Output ONLY the translated English text with normal punctuation. Do NOT add quotes or notes.\n\nText: \(text)"
        let body: [String: Any] = [
            "contents": [["parts": [["text": prompt]]]],
            "generationConfig": ["temperature": 0.0]
        ]
        return try await callGeminiWithFallback(key: key, body: body, isAudio: false)
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
                        if !combined.isEmpty {
                            return combined
                        } else if isAudio {
                            throw KothalipiError.emptyAudio
                        }
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

        if sawRateLimit {
            throw KothalipiError.rateLimited
        }
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
            return "Gemini API Key সেট করা নেই! সেটিংস থেকে আপনার ফ্রি API Key দিন।"
        case .badApiKey:
            return "ভুল বা অকার্যকর API Key! সেটিংস থেকে সঠিক Google AI Studio Key দিন।"
        case .rateLimited:
            return "ফ্রি API কোটা সীমা পূর্ণ হয়েছে। কয়েক সেকেন্ড পর আবার চেষ্টা করুন।"
        case .emptyAudio:
            return "কোনো কথা শোনা যায়নি। অনুগ্রহ করে মাইক্রোফোনে স্পষ্ট করে বলুন।"
        case .network:
            return "ইন্টারনেট সংযোগ বা Gemini সার্ভারে সমস্যা হয়েছে। আবার চেষ্টা করুন।"
        }
    }
}
