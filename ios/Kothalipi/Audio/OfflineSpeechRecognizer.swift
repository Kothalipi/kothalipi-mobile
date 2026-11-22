import Foundation
import AVFoundation
import Speech

/// 100% Free OS-Native On-Device Offline Speech Recognizer for iOS (SFSpeechRecognizer).
final class OfflineSpeechRecognizer: NSObject, ObservableObject {
    private var audioEngine: AVAudioEngine?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var speechRecognizer: SFSpeechRecognizer?

    func start(
        mode: String,
        onPartial: @escaping (String) -> Void,
        onAmplitude: @escaping (Float) -> Void,
        onResult: @escaping (String) -> Void,
        onError: @escaping (String) -> Void
    ) {
        SFSpeechRecognizer.requestAuthorization { [weak self] authStatus in
            guard let self else { return }
            guard authStatus == .authorized else {
                DispatchQueue.main.async {
                    onError("অফলাইন মোডের জন্য Settings → Speech Recognition অনুমতি দিন।")
                }
                return
            }

            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                guard granted else {
                    DispatchQueue.main.async { onError("মাইক্রোফোন পারমিশন প্রয়োজন।") }
                    return
                }

                DispatchQueue.main.async {
                    self.beginSession(
                        mode: mode,
                        onPartial: onPartial,
                        onAmplitude: onAmplitude,
                        onResult: onResult,
                        onError: onError
                    )
                }
            }
        }
    }

    private func beginSession(
        mode: String,
        onPartial: @escaping (String) -> Void,
        onAmplitude: @escaping (Float) -> Void,
        onResult: @escaping (String) -> Void,
        onError: @escaping (String) -> Void
    ) {
        stop()

        let localeId = (mode == "en") ? "en-US" : "bn-BD"
        let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeId))
            ?? SFSpeechRecognizer(locale: Locale(identifier: "en-US"))

        guard let recognizer, recognizer.isAvailable else {
            onError("অফলাইন স্পিচ ইঞ্জিন পাওয়া যায়নি।")
            return
        }
        self.speechRecognizer = recognizer

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            if recognizer.supportsOnDeviceRecognition {
                request.requiresOnDeviceRecognition = true
            }
            self.recognitionRequest = request

            let engine = AVAudioEngine()
            let inputNode = engine.inputNode
            let format = inputNode.outputFormat(forBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                request.append(buffer)
                if let ch = buffer.floatChannelData?[0] {
                    let frames = Int(buffer.frameLength)
                    if frames > 0 {
                        var sum: Float = 0
                        for i in 0..<frames { sum += ch[i] * ch[i] }
                        let rms = sqrt(sum / Float(frames))
                        DispatchQueue.main.async { onAmplitude(min(max(rms * 4.5, 0), 1)) }
                    }
                }
            }

            engine.prepare()
            try engine.start()
            self.audioEngine = engine

            var latestText = ""
            self.recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
                if let result {
                    latestText = result.bestTranscription.formattedString
                    DispatchQueue.main.async { onPartial(latestText) }
                    if result.isFinal {
                        self?.stop()
                        DispatchQueue.main.async { onResult(latestText) }
                    }
                } else if let error {
                    self?.stop()
                    if !latestText.isEmpty {
                        DispatchQueue.main.async { onResult(latestText) }
                    } else {
                        DispatchQueue.main.async { onError("অফলাইন রিকগনিশন ব্যর্থ: \(error.localizedDescription)") }
                    }
                }
            }
        } catch {
            stop()
            onError("অফলাইন মাইক চালু করা যায়নি।")
        }
    }

    func stop() {
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine?.stop()
        audioEngine = nil
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask?.finish()
        recognitionTask = nil
    }
}
