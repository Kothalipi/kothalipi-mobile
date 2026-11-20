# Kothalipi Mobile (Android & iOS)

> **কথালিপি মোবাইল:** উচ্চগতির বাংলা ও ইংরেজি ভয়েস টাইপিং এবং রিয়েল-টাইম বাংলা-টু-ইংলিশ অনুবাদক।

---

## 🏛️ Architecture Overview

Kothalipi Mobile utilizes an ultra-high performance native architecture:

* **Shared Core Engine (Rust):**
  * Audio processing, PCM to WAV conversion, RMS amplitude visualizer.
  * Google Gemini Speech-to-Text (`gemini-3.5-transcribe` & `gemini-2.5-flash`).
  * Live Bangla to fluent English Translation (`gemini-flash-lite-latest`).
  * AI Smart Polish (punctuation, grammar, filler word removal).
  * Mozilla **UniFFI** generates native Kotlin and Swift bindings with zero boilerplate.

* **Android Native (Kotlin + Jetpack Compose):**
  * **Main App:** Modern dark-mode UI with live waveform and pulse recording.
  * **Kothalipi Voice Keyboard (`InputMethodService`):** System-wide voice keyboard usable in WhatsApp, Messenger, Notes, etc.
  * **Floating Mic Assistant (`AccessibilityService` / `WindowManager`):** Chat-head style floating microphone to dictate anywhere.

* **iOS Native (Swift + SwiftUI):**
  * **Main App:** SwiftUI app with `AVAudioEngine` low-latency recording.
  * **Custom Keyboard Extension (`UIInputViewController`):** Dictate directly inside any iOS text input field.
  * **Action Button & Siri Shortcuts (`AppIntents`):** 1-press dictation trigger for iPhone 15/16 Pro.

---

## 📁 Repository Structure

```
kothalipi-mobile/
├── core/                  # Rust shared core library (UniFFI)
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs         # UniFFI interface & engine exports
│       ├── audio.rs       # WAV encoding & RMS amplitude
│       ├── cloud_stt.rs   # Gemini STT engine
│       ├── translation.rs # Bangla to English translation
│       └── polish.rs      # AI Smart Polish
├── android/               # Native Android (Kotlin + Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/kothalipi/mobile/
│   │   │   ├── MainActivity.kt        # Jetpack Compose UI
│   │   │   ├── keyboard/              # Voice Keyboard Service
│   │   │   └── overlay/               # Floating Assistant Service
│   │   └── build.gradle.kts
│   └── settings.gradle.kts
├── ios/                   # Native iOS (Swift + SwiftUI)
│   ├── Kothalipi/
│   │   ├── App/                       # SwiftUI App Entry
│   │   ├── Views/                     # Home & Settings Screens
│   │   ├── Audio/                     # AVAudioEngine Recorder
│   │   └── Intents/                   # Action Button & Siri Shortcuts
│   └── KothalipiKeyboard/             # iOS Custom Keyboard Extension
└── scripts/               # Cross-compilation scripts
    ├── build-android.sh   # Builds .so libraries and Kotlin bindings
    ├── build-ios.sh       # Builds XCFramework and Swift bindings
    └── build-all.sh       # Full mobile build pipeline
```

---

## 🚀 Getting Started

### 1. Build the Rust Core Library

Make executable and build:
```bash
chmod +x scripts/*.sh

# Build for Android
./scripts/build-android.sh

# Build for iOS
./scripts/build-ios.sh
```

### 2. Run Android App
Open the `android/` directory in **Android Studio**:
- Connect your device or start an emulator.
- Click **Run** (`Shift + F10`).

### 3. Run iOS App
Open the `ios/` directory in **Xcode**:
- Select your target device / simulator.
- Click **Run** (`Cmd + R`).
