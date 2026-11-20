import UIKit

/// Native iOS Custom Keyboard Extension (UIInputViewController)
/// Enables Kothalipi Voice Typing across all iOS applications
class KeyboardViewController: UIInputViewController {

    private var micButton: UIButton!
    private var statusLabel: UILabel!
    private var isRecording = false

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = UIColor(red: 0.07, green: 0.06, blue: 0.11, alpha: 1.0)

        // Mic Button
        micButton = UIButton(type: .system)
        micButton.setTitle("🎙️", for: .normal)
        micButton.titleLabel?.font = UIFont.systemFont(ofSize: 32)
        micButton.backgroundColor = UIColor(red: 0.54, green: 0.17, blue: 0.89, alpha: 1.0)
        micButton.layer.cornerRadius = 35
        micButton.translatesAutoresizingMaskIntoConstraints = false
        micButton.addTarget(self, action: #selector(micTapped), for: .touchUpInside)

        // Status Label
        statusLabel = UILabel()
        statusLabel.text = "কথা বলতে মাইক চাপুন"
        statusLabel.textColor = UIColor(red: 0.62, green: 0.62, blue: 0.65, alpha: 1.0)
        statusLabel.font = UIFont.systemFont(ofSize: 14)
        statusLabel.textAlignment = .center
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(statusLabel)
        view.addSubview(micButton)

        NSLayoutConstraint.activate([
            statusLabel.topAnchor.constraint(equalTo: view.topAnchor, constant: 12),
            statusLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            micButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            micButton.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: 10),
            micButton.widthAnchor.constraint(equalToConstant: 70),
            micButton.heightAnchor.constraint(equalToConstant: 70)
        ])
    }

    @objc private func micTapped() {
        if !isRecording {
            isRecording = true
            micButton.setTitle("⏹️", for: .normal)
            micButton.backgroundColor = UIColor(red: 0.89, green: 0.33, blue: 1.0, alpha: 1.0)
            statusLabel.text = "শুনছি… বলুন"
        } else {
            isRecording = false
            micButton.setTitle("🎙️", for: .normal)
            micButton.backgroundColor = UIColor(red: 0.54, green: 0.17, blue: 0.89, alpha: 1.0)
            statusLabel.text = "টেক্সট টাইপ হচ্ছে…"

            // Insert text into the active text field
            textDocumentProxy.insertText(" ")
        }
    }
}
