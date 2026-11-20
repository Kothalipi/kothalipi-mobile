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

    func processRecording(wavData: Data, mode: String) async throws -> String {
        return try await fallbackProcess(wavData: wavData, mode: mode)
    }

    func processRecording(wavData: Data, translateToEnglish: Bool) async throws -> String {
        let mode = translateToEnglish ? "bn_to_en" : "bn"
        return try await fallbackProcess(wavData: wavData, mode: mode)
    }

    private func fallbackProcess(wavData: Data, mode: String) async throws -> String {
        let b64 = wavData.base64EncodedString()
        let prompt: String
        switch mode {
        case "en":
            prompt = "Transcribe this spoken audio verbatim in English only. Output ONLY the English words with proper punctuation and capitalization."
        case "bn_to_en":
            prompt = "Translate this spoken Bengali (Bangla) audio directly into natural, fluent English. Output ONLY the translated English text with proper punctuation and capitalization."
        default:
            prompt = "Transcribe this spoken audio verbatim in Bengali script (বাংলা হরফে). Output ONLY the Bangla text with proper punctuation. Do not translate."
        }

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

        let models = ["gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-flash-latest"]
        var lastErr = ""

        for model in models {
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=\(apiKey)") else { continue }
            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.addValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let httpResp = response as? HTTPURLResponse, httpResp.statusCode == 200 {
                    if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let candidates = json["candidates"] as? [[String: Any]],
                       let content = candidates.first?["content"] as? [String: Any],
                       let parts = content["parts"] as? [[String: Any]],
                       let text = parts.first?["text"] as? String {
                        return text.trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                } else if let httpResp = response as? HTTPURLResponse {
                    lastErr = "HTTP \(httpResp.statusCode)"
                }
            } catch {
                lastErr = error.localizedDescription
            }
        }

        throw NSError(domain: "Kothalipi", code: -1, userInfo: [NSLocalizedDescriptionKey: "Gemini সার্ভিস পাওয়া যায়নি: \(lastErr)"])
    }
}
