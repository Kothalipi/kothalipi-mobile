import UIKit

/// Wispr Flow-style Custom Keyboard Extension for iOS (UIInputViewController)
/// Super sleek, voice-first minimal keyboard for hands-free typing
class KeyboardViewController: UIInputViewController {

    private var micButton: UIButton!
    private var statusLabel: UILabel!
    private var modeButton: UIButton!
    private var isRecording = false
    private var translateToEnglish = false

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = UIColor(red: 0.05, green: 0.04, blue: 0.08, alpha: 1.0)

        setupUI()
    }

    private func setupUI() {
        // Mode Selector Pill
        modeButton = UIButton(type: .system)
        modeButton.setTitle("🇧🇩 বাংলা ➔ 🇬🇧 EN", for: .normal)
        modeButton.setTitleColor(UIColor(red: 0.66, green: 0.33, blue: 0.97, alpha: 1.0), for: .normal)
        modeButton.titleLabel?.font = UIFont.systemFont(ofSize: 13, weight: .semibold)
        modeButton.backgroundColor = UIColor(red: 0.11, green: 0.09, blue: 0.17, alpha: 1.0)
        modeButton.layer.cornerRadius = 15
        modeButton.layer.borderWidth = 1
        modeButton.layer.borderColor = UIColor(red: 0.66, green: 0.33, blue: 0.97, alpha: 0.25).cgColor
        modeButton.contentEdgeInsets = UIEdgeInsets(top: 6, left: 14, bottom: 6, right: 14)
        modeButton.translatesAutoresizingMaskIntoConstraints = false
        modeButton.addTarget(self, action: #selector(toggleMode), for: .touchUpInside)

        // Status Label
        statusLabel = UILabel()
        statusLabel.text = "কথা বলতে মাইক চাপুন"
        statusLabel.textColor = UIColor(red: 0.56, green: 0.55, blue: 0.63, alpha: 1.0)
        statusLabel.font = UIFont.systemFont(ofSize: 13, weight: .medium)
        statusLabel.textAlignment = .center
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        // Center Hero Mic Button
        micButton = UIButton(type: .system)
        micButton.setTitle("🎙️", for: .normal)
        micButton.titleLabel?.font = UIFont.systemFont(ofSize: 34)
        micButton.backgroundColor = UIColor(red: 0.54, green: 0.17, blue: 0.89, alpha: 1.0)
        micButton.layer.cornerRadius = 38
        micButton.layer.shadowColor = UIColor(red: 0.54, green: 0.17, blue: 0.89, alpha: 0.5).cgColor
        micButton.layer.shadowOpacity = 0.6
        micButton.layer.shadowRadius = 12
        micButton.layer.shadowOffset = CGSize(width: 0, height: 6)
        micButton.translatesAutoresizingMaskIntoConstraints = false
        micButton.addTarget(self, action: #selector(micTapped), for: .touchUpInside)

        // Bottom Controls: Switch Keyboard & Delete
        let globeButton = UIButton(type: .system)
        globeButton.setTitle("🌐", for: .normal)
        globeButton.titleLabel?.font = UIFont.systemFont(ofSize: 22)
        globeButton.translatesAutoresizingMaskIntoConstraints = false
        globeButton.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)

        let deleteButton = UIButton(type: .system)
        deleteButton.setTitle("⌫", for: .normal)
        deleteButton.titleLabel?.font = UIFont.systemFont(ofSize: 22)
        deleteButton.setTitleColor(UIColor.white, for: .normal)
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        deleteButton.addTarget(self, action: #selector(deleteTapped), for: .touchUpInside)

        let spaceButton = UIButton(type: .system)
        spaceButton.setTitle("Space", for: .normal)
        spaceButton.setTitleColor(UIColor(red: 0.7, green: 0.7, blue: 0.75, alpha: 1.0), for: .normal)
        spaceButton.backgroundColor = UIColor(red: 0.11, green: 0.09, blue: 0.17, alpha: 1.0)
        spaceButton.layer.cornerRadius = 10
        spaceButton.translatesAutoresizingMaskIntoConstraints = false
        spaceButton.addTarget(self, action: #selector(spaceTapped), for: .touchUpInside)

        view.addSubview(modeButton)
        view.addSubview(statusLabel)
        view.addSubview(micButton)
        view.addSubview(globeButton)
        view.addSubview(spaceButton)
        view.addSubview(deleteButton)

        NSLayoutConstraint.activate([
            modeButton.topAnchor.constraint(equalTo: view.topAnchor, constant: 10),
            modeButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            statusLabel.topAnchor.constraint(equalTo: modeButton.bottomAnchor, constant: 6),
            statusLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),

            micButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            micButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 8),
            micButton.widthAnchor.constraint(equalToConstant: 76),
            micButton.heightAnchor.constraint(equalToConstant: 76),

            globeButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            globeButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -16),

            spaceButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            spaceButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -16),
            spaceButton.widthAnchor.constraint(equalToConstant: 120),
            spaceButton.heightAnchor.constraint(equalToConstant: 38),

            deleteButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            deleteButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -16)
        ])
    }

    @objc private func toggleMode() {
        translateToEnglish.toggle()
        modeButton.setTitle(translateToEnglish ? "🇧🇩 বাংলা ➔ 🇬🇧 EN" : "🇧🇩 বাংলা", for: .normal)
    }

    @objc private func spaceTapped() {
        textDocumentProxy.insertText(" ")
    }

    @objc private func deleteTapped() {
        textDocumentProxy.deleteBackward()
    }

    @objc private func micTapped() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()

        if !isRecording {
            isRecording = true
            micButton.setTitle("⏹️", for: .normal)
            micButton.backgroundColor = UIColor(red: 0.92, green: 0.28, blue: 0.6, alpha: 1.0)
            statusLabel.text = translateToEnglish ? "শুনছি… ইংরেজিতে অনুবাদ হবে" : "শুনছি… বাংলায় বলুন"
        } else {
            isRecording = false
            micButton.setTitle("🎙️", for: .normal)
            micButton.backgroundColor = UIColor(red: 0.54, green: 0.17, blue: 0.89, alpha: 1.0)
            statusLabel.text = "টাইপ করা হচ্ছে…"

            // Insert text demo
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.statusLabel.text = "কথা বলতে মাইক চাপুন"
            }
        }
    }
}
