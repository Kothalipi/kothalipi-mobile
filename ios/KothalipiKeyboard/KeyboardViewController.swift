import UIKit
import AVFoundation

/// Wispr Flow-style Custom Voice Keyboard Extension for iOS (UIInputViewController).
/// Real audio capture + Gemini transcription/translation + direct insertion via textDocumentProxy.
class KeyboardViewController: UIInputViewController {

    private var micButton: UIButton!
    private var statusLabel: UILabel!
    private var modeButton: UIButton!
    private var isRecording = false
    private var isProcessing = false
    private var lastInsertedCount = 0

    private let defaults = UserDefaults(suiteName: "group.com.kothalipi.mobile") ?? UserDefaults.standard
    private var audioEngine: AVAudioEngine?
    private var audioFile: AVAudioFile?
    private var tempWavUrl: URL?
    private let fileQueue = DispatchQueue(label: "com.kothalipi.keyboard.audio")

    private var mode: String {
        get { defaults.string(forKey: "kothalipi_dictation_mode") ?? "bn" }
        set { defaults.set(newValue, forKey: "kothalipi_dictation_mode") }
    }

    private var apiKey: String {
        defaults.string(forKey: "kothalipi_gemini_api_key")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.04, green: 0.04, blue: 0.07, alpha: 1.0)
        setupUI()
    }

    private func modeTitle(_ m: String) -> String {
        switch m {
        case "en": return "🇬🇧 English"
        case "bn_to_en": return "বাংলা ➔ 🇬🇧 EN"
        case "auto": return "✨ Auto"
        default: return "🇧🇩 বাংলা"
        }
    }

    private func idleStatus(_ m: String) -> String {
        switch m {
        case "en": return "Tap mic to speak English"
        case "bn_to_en": return "বাংলায় বলুন, ইংরেজিতে টাইপ হবে"
        case "auto": return "বাংলা বা ইংরেজিতে বলুন"
        default: return "কথা বলতে মাইক চাপুন"
        }
    }

    private func setupUI() {
        modeButton = UIButton(type: .system)
        modeButton.setTitle(modeTitle(mode), for: .normal)
        modeButton.setTitleColor(UIColor(red: 0.87, green: 0.84, blue: 1.0, alpha: 1.0), for: .normal)
        modeButton.titleLabel?.font = UIFont.systemFont(ofSize: 13, weight: .semibold)
        modeButton.backgroundColor = UIColor(red: 0.11, green: 0.09, blue: 0.18, alpha: 1.0)
        modeButton.layer.cornerRadius = 15
        modeButton.layer.borderWidth = 1
        modeButton.layer.borderColor = UIColor(red: 0.55, green: 0.36, blue: 0.96, alpha: 0.35).cgColor
        modeButton.translatesAutoresizingMaskIntoConstraints = false
        modeButton.addTarget(self, action: #selector(cycleMode), for: .touchUpInside)

        statusLabel = UILabel()
        statusLabel.text = idleStatus(mode)
        statusLabel.textColor = UIColor(red: 0.62, green: 0.61, blue: 0.70, alpha: 1.0)
        statusLabel.font = UIFont.systemFont(ofSize: 13, weight: .medium)
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 2
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        micButton = UIButton(type: .system)
        micButton.setImage(UIImage(systemName: "mic.fill"), for: .normal)
        micButton.tintColor = .white
        micButton.backgroundColor = UIColor(red: 0.49, green: 0.23, blue: 0.93, alpha: 1.0)
        micButton.layer.cornerRadius = 36
        micButton.layer.shadowColor = UIColor(red: 0.55, green: 0.36, blue: 0.96, alpha: 0.6).cgColor
        micButton.layer.shadowOpacity = 0.65
        micButton.layer.shadowRadius = 12
        micButton.layer.shadowOffset = CGSize(width: 0, height: 6)
        micButton.translatesAutoresizingMaskIntoConstraints = false
        micButton.addTarget(self, action: #selector(micTapped), for: .touchUpInside)

        let globeButton = UIButton(type: .system)
        globeButton.setImage(UIImage(systemName: "globe"), for: .normal)
        globeButton.tintColor = .white
        globeButton.backgroundColor = UIColor(red: 0.10, green: 0.09, blue: 0.16, alpha: 1.0)
        globeButton.layer.cornerRadius = 10
        globeButton.translatesAutoresizingMaskIntoConstraints = false
        globeButton.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)

        let spaceButton = UIButton(type: .system)
        spaceButton.setTitle("Space", for: .normal)
        spaceButton.setTitleColor(UIColor(red: 0.85, green: 0.85, blue: 0.9, alpha: 1.0), for: .normal)
        spaceButton.backgroundColor = UIColor(red: 0.10, green: 0.09, blue: 0.16, alpha: 1.0)
        spaceButton.layer.cornerRadius = 10
        spaceButton.translatesAutoresizingMaskIntoConstraints = false
        spaceButton.addTarget(self, action: #selector(spaceTapped), for: .touchUpInside)

        let returnButton = UIButton(type: .system)
        returnButton.setTitle("↵", for: .normal)
        returnButton.setTitleColor(UIColor(red: 0.87, green: 0.84, blue: 1.0, alpha: 1.0), for: .normal)
        returnButton.backgroundColor = UIColor(red: 0.16, green: 0.11, blue: 0.28, alpha: 1.0)
        returnButton.layer.cornerRadius = 10
        returnButton.translatesAutoresizingMaskIntoConstraints = false
        returnButton.addTarget(self, action: #selector(returnTapped), for: .touchUpInside)

        let deleteButton = UIButton(type: .system)
        deleteButton.setImage(UIImage(systemName: "delete.left"), for: .normal)
        deleteButton.tintColor = .white
        deleteButton.backgroundColor = UIColor(red: 0.10, green: 0.09, blue: 0.16, alpha: 1.0)
        deleteButton.layer.cornerRadius = 10
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        deleteButton.addTarget(self, action: #selector(deleteTapped), for: .touchUpInside)

        view.addSubview(modeButton)
        view.addSubview(statusLabel)
        view.addSubview(micButton)
        view.addSubview(globeButton)
        view.addSubview(spaceButton)
        view.addSubview(returnButton)
        view.addSubview(deleteButton)

        NSLayoutConstraint.activate([
            view.heightAnchor.constraint(equalToConstant: 230),

            modeButton.topAnchor.constraint(equalTo: view.topAnchor, constant: 10),
            modeButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            modeButton.heightAnchor.constraint(equalToConstant: 30),
            modeButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 120),

            statusLabel.topAnchor.constraint(equalTo: modeButton.bottomAnchor, constant: 6),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            micButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            micButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 10),
            micButton.widthAnchor.constraint(equalToConstant: 72),
            micButton.heightAnchor.constraint(equalToConstant: 72),

            globeButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 14),
            globeButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -12),
            globeButton.widthAnchor.constraint(equalToConstant: 46),
            globeButton.heightAnchor.constraint(equalToConstant: 38),

            deleteButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -14),
            deleteButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -12),
            deleteButton.widthAnchor.constraint(equalToConstant: 46),
            deleteButton.heightAnchor.constraint(equalToConstant: 38),

            returnButton.trailingAnchor.constraint(equalTo: deleteButton.leadingAnchor, constant: -8),
            returnButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -12),
            returnButton.widthAnchor.constraint(equalToConstant: 46),
            returnButton.heightAnchor.constraint(equalToConstant: 38),

            spaceButton.leadingAnchor.constraint(equalTo: globeButton.trailingAnchor, constant: 8),
            spaceButton.trailingAnchor.constraint(equalTo: returnButton.leadingAnchor, constant: -8),
            spaceButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -12),
            spaceButton.heightAnchor.constraint(equalToConstant: 38)
        ])
    }

    @objc private func cycleMode() {
        guard !isRecording && !isProcessing else { return }
        switch mode {
        case "bn": mode = "en"
        case "en": mode = "bn_to_en"
        case "bn_to_en": mode = "auto"
        default: mode = "bn"
        }
        modeButton.setTitle(modeTitle(mode), for: .normal)
        statusLabel.text = idleStatus(mode)
    }

    @objc private func spaceTapped() { textDocumentProxy.insertText(" ") }
    @objc private func returnTapped() { textDocumentProxy.insertText("\n") }
    @objc private func deleteTapped() { textDocumentProxy.deleteBackward() }

    @objc private func micTapped() {
        guard !isProcessing else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        if !hasFullAccess {
            statusLabel.text = "Settings → Keyboard → Kothalipi → Allow Full Access অন করুন"
            return
        }

        if apiKey.isEmpty {
            statusLabel.text = "Kothalipi অ্যাপে Gemini API Key সেট করুন"
            return
        }

        if !isRecording {
            startKeyboardRecording()
        } else {
            stopKeyboardRecordingAndTranscribe()
        }
    }

    private func startKeyboardRecording() {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try session.setActive(true)

            let engine = AVAudioEngine()
            let inputNode = engine.inputNode
            let inputFormat = inputNode.outputFormat(forBus: 0)
            guard inputFormat.sampleRate > 0,
                  let targetFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true),
                  let converter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
                statusLabel.text = "মাইক্রোফোন চালু করা যায়নি"
                return
            }

            let url = FileManager.default.temporaryDirectory.appendingPathComponent("kb_\(UUID().uuidString).wav")
            let file = try AVAudioFile(forWriting: url, settings: targetFormat.settings)
            fileQueue.sync {
                self.tempWavUrl = url
                self.audioFile = file
            }

            let ratio = targetFormat.sampleRate / inputFormat.sampleRate
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: inputFormat) { [weak self] buffer, _ in
                guard let self else { return }
                let cap = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 64
                guard let outBuf = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: max(cap, 512)) else { return }
                var provided = false
                var err: NSError?
                let st = converter.convert(to: outBuf, error: &err) { _, outStatus in
                    if provided { outStatus.pointee = .noDataNow; return nil }
                    provided = true
                    outStatus.pointee = .haveData
                    return buffer
                }
                if st != .error, outBuf.frameLength > 0 {
                    self.fileQueue.async { try? self.audioFile?.write(from: outBuf) }
                }
            }

            try engine.start()
            audioEngine = engine
            isRecording = true
            micButton.setImage(UIImage(systemName: "stop.fill"), for: .normal)
            micButton.backgroundColor = UIColor(red: 0.93, green: 0.28, blue: 0.60, alpha: 1.0)
            statusLabel.text = mode == "bn_to_en" ? "শুনছি… ইংরেজিতে লিখব" : "শুনছি… বলুন"
        } catch {
            statusLabel.text = "মাইক পারমিশন দিন"
        }
    }

    private func stopKeyboardRecordingAndTranscribe() {
        isRecording = false
        isProcessing = true
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine?.stop()
        audioEngine = nil

        let urlToRead: URL? = fileQueue.sync {
            self.audioFile = nil
            return self.tempWavUrl
        }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        micButton.setImage(UIImage(systemName: "hourglass"), for: .normal)
        micButton.backgroundColor = UIColor(red: 0.35, green: 0.17, blue: 0.65, alpha: 1.0)
        statusLabel.text = "প্রসেসিং..."

        guard let url = urlToRead, let wavData = try? Data(contentsOf: url), wavData.count >= 1000 else {
            isProcessing = false
            resetMicButton()
            statusLabel.text = "কোনো কথা শোনা যায়নি"
            return
        }
        try? FileManager.default.removeItem(at: url)

        let activeMode = mode
        let key = apiKey

        Task {
            do {
                let text = try await transcribeWithGemini(wavData: wavData, mode: activeMode, apiKey: key)
                await MainActor.run {
                    self.isProcessing = false
                    self.resetMicButton()
                    self.textDocumentProxy.insertText(text)
                    self.lastInsertedCount = text.count
                    self.statusLabel.text = "টাইপ হয়েছে ✓"
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                        if !self.isRecording && !self.isProcessing {
                            self.statusLabel.text = self.idleStatus(self.mode)
                        }
                    }
                }
            } catch {
                await MainActor.run {
                    self.isProcessing = false
                    self.resetMicButton()
                    self.statusLabel.text = "প্রসেসিং ব্যর্থ হয়েছে"
                }
            }
        }
    }

    private func resetMicButton() {
        micButton.setImage(UIImage(systemName: "mic.fill"), for: .normal)
        micButton.backgroundColor = UIColor(red: 0.49, green: 0.23, blue: 0.93, alpha: 1.0)
    }

    private func transcribeWithGemini(wavData: Data, mode: String, apiKey: String) async throws -> String {
        let prompt: String
        switch mode {
        case "en":
            prompt = "Transcribe the spoken audio accurately in English. Output ONLY the English text with proper punctuation."
        case "bn_to_en":
            prompt = "Translate this spoken Bengali (Bangla) audio into natural, fluent English. Output ONLY the translated English text."
        case "auto":
            prompt = "Transcribe the spoken audio accurately in the language spoken (Bangla script or English). Output ONLY the text."
        default:
            prompt = "Transcribe the spoken audio accurately in Bengali script (বাংলা হরফে). Output ONLY the Bengali text with proper punctuation."
        }

        let body: [String: Any] = [
            "contents": [[
                "parts": [
                    ["inlineData": ["mimeType": "audio/wav", "data": wavData.base64EncodedString()]],
                    ["text": prompt]
                ]
            ]],
            "generationConfig": ["temperature": 0.1]
        ]

        let models = ["gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-flash-latest"]
        for model in models {
            guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=\(apiKey)") else { continue }
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.timeoutInterval = 25
            req.addValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)

            if let (data, resp) = try? await URLSession.shared.data(for: req),
               let http = resp as? HTTPURLResponse, (200...299).contains(http.statusCode),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let candidates = json["candidates"] as? [[String: Any]],
               let content = candidates.first?["content"] as? [String: Any],
               let parts = content["parts"] as? [[String: Any]] {
                let text = parts.compactMap { $0["text"] as? String }.joined().trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty { return text }
            }
        }
        throw NSError(domain: "KothalipiKeyboard", code: -1)
    }
}
