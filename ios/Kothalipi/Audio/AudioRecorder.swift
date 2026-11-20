import Foundation
import AVFoundation

/// Handles high quality 16kHz mono audio recording for Kothalipi
class AudioRecorder: NSObject, ObservableObject {
    private var audioEngine: AVAudioEngine?
    private var audioFile: AVAudioFile?
    private var tempWavUrl: URL?

    func startRecording(completion: @escaping (Bool) -> Void) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            guard granted else {
                completion(false)
                return
            }

            do {
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
                try session.setActive(true)

                let engine = AVAudioEngine()
                let inputNode = engine.inputNode
                let inputFormat = inputNode.outputFormat(forBus: 0)

                // 16 kHz mono 16-bit PCM format
                let targetFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true)!

                self.tempWavUrl = FileManager.default.temporaryDirectory.appendingPathComponent("kothalipi_record.wav")
                self.audioFile = try AVAudioFile(forWriting: self.tempWavUrl!, settings: targetFormat.settings)

                let converter = AVAudioConverter(from: inputFormat, to: targetFormat)!

                inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { buffer, _ in
                    let convertedBuffer = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: AVAudioFrameCount(targetFormat.sampleRate * 0.1))!
                    var error: NSError?
                    converter.convert(to: convertedBuffer, error: &error) { _, outStatus in
                        outStatus.pointee = .haveData
                        return buffer
                    }
                    try? self.audioFile?.write(from: convertedBuffer)
                }

                try engine.start()
                self.audioEngine = engine
                completion(true)
            } catch {
                completion(false)
            }
        }
    }

    func stopRecording() -> Data? {
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine = nil
        audioFile = nil

        guard let url = tempWavUrl else { return nil }
        return try? Data(contentsOf: url)
    }
}

/// Swift wrapper managing the UniFFI Rust Core Engine
class KothalipiEngineManager: ObservableObject {
    static let shared = KothalipiEngineManager()

    let audioRecorder = AudioRecorder()
    private let apiKeyDefaultsKey = "kothalipi_gemini_api_key"

    @Published var apiKey: String {
        didSet {
            UserDefaults.standard.set(apiKey, forKey: apiKeyDefaultsKey)
        }
    }

    init() {
        self.apiKey = UserDefaults.standard.string(forKey: apiKeyDefaultsKey) ?? ""
    }

    func processRecording(wavData: Data, translateToEnglish: Bool) async throws -> String {
        // Rust Core UniFFI Invocation
        // Once the XCFramework is compiled, KothalipiMobileEngine is available directly:
        // let engine = KothalipiMobileEngine(apiKey: apiKey)
        // let result = try engine.processRecording(audioBytes: [UInt8](wavData), mimeType: "audio/wav", translateToEnglish: translateToEnglish, enableSmartPolish: true)
        // return result.processedText

        // Fallback or native implementation while framework compiles:
        return try await fallbackProcess(wavData: wavData, translateToEnglish: translateToEnglish)
    }

    private func fallbackProcess(wavData: Data, translateToEnglish: Bool) async throws -> String {
        // Direct bridge matching Rust logic
        let b64 = wavData.base64EncodedString()
        let prompt = translateToEnglish
            ? "Translate this spoken audio directly into natural, fluent English. Output ONLY the English text."
            : "Transcribe this spoken audio verbatim in Bengali. Output ONLY the words."

        let body: [String: Any] = [
            "contents": [
                [
                    "parts": [
                        ["text": prompt],
                        ["inline_data": ["mime_type": "audio/wav", "data": b64]]
                    ]
                ]
            ],
            "generationConfig": ["temperature": 0.0]
        ]

        let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=\(apiKey)")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, _) = try await URLSession.shared.data(for: request)
        if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
           let candidates = json["candidates"] as? [[String: Any]],
           let content = candidates.first?["content"] as? [String: Any],
           let parts = content["parts"] as? [[String: Any]],
           let text = parts.first?["text"] as? String {
            return text.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return ""
    }
}
