use std::sync::{OnceLock, RwLock};

pub mod audio;
pub mod cloud_stt;
pub mod polish;
pub mod translation;

uniffi::setup_scaffolding!();

static RUNTIME: OnceLock<tokio::runtime::Runtime> = OnceLock::new();

fn runtime() -> Result<&'static tokio::runtime::Runtime, EngineError> {
    if let Some(rt) = RUNTIME.get() {
        return Ok(rt);
    }
    let rt = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .map_err(|e| EngineError::Other {
            detail: e.to_string(),
        })?;
    let _ = RUNTIME.set(rt);
    RUNTIME.get().ok_or_else(|| EngineError::Other {
        detail: "Failed to initialize async runtime".to_string(),
    })
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("Bad or missing API key: {detail}")]
    BadApiKey { detail: String },
    #[error("Network connection error: {detail}")]
    NetworkError { detail: String },
    #[error("Rate limit exceeded")]
    RateLimitExceeded,
    #[error("Invalid audio input: {detail}")]
    InvalidAudio { detail: String },
    #[error("Internal engine error: {detail}")]
    Other { detail: String },
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct TranscriptionResult {
    pub text: String,
    pub language: String,
}

#[derive(Clone, Debug, uniffi::Record)]
pub struct ProcessResult {
    pub raw_text: String,
    pub processed_text: String,
    pub was_translated: bool,
    pub was_polished: bool,
    pub detected_language: String,
}

#[derive(uniffi::Object)]
pub struct KothalipiMobileEngine {
    api_key: RwLock<String>,
}

#[uniffi::export]
impl KothalipiMobileEngine {
    #[uniffi::constructor]
    pub fn new(api_key: String) -> Self {
        Self {
            api_key: RwLock::new(api_key.trim().to_string()),
        }
    }

    pub fn set_api_key(&self, key: String) {
        if let Ok(mut lock) = self.api_key.write() {
            *lock = key.trim().to_string();
        }
    }

    pub fn get_api_key(&self) -> String {
        self.api_key.read().map(|k| k.clone()).unwrap_or_default()
    }

    /// Validates that the configured Gemini API key can connect to Google Gemini
    pub fn validate_api_key(&self) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;
        rt.block_on(async { cloud_stt::validate_api_key(&key).await })
    }

    /// Transcribe raw audio bytes (e.g. WAV, M4A, MP3, AAC)
    pub fn transcribe_audio(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
    ) -> Result<TranscriptionResult, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;

        let (text, language) = rt.block_on(async {
            cloud_stt::transcribe_gemini(&key, &audio_bytes, &mime_type).await
        })?;

        Ok(TranscriptionResult { text, language })
    }

    /// Translate spoken Bengali into fluent English
    pub fn translate_bangla_to_english(&self, text: String) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;

        rt.block_on(async { translation::translate_bangla_to_english(&key, &text).await })
    }

    /// Smart polish punctuation, capitalization, and filler words
    pub fn smart_polish(&self, text: String, language: String) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;

        rt.block_on(async { polish::smart_polish(&key, &text, &language).await })
    }

    /// Smart polish with explicit writing style ("normal", "formal", "casual", "bullets")
    pub fn smart_polish_with_style(
        &self,
        text: String,
        language: String,
        style: String,
    ) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;

        rt.block_on(async { polish::smart_polish_with_style(&key, &text, &language, &style).await })
    }

    /// Full end-to-end single-pass recording pipeline (fast! avoids double latency)
    /// mode: "bn", "en", "bn_to_en", "auto"
    /// style: "normal", "formal", "casual", "bullets"
    pub fn process_recording_full(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
        mode: String,
        style: String,
        enable_smart_polish: bool,
    ) -> Result<ProcessResult, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;

        rt.block_on(async {
            // Single-pass multimodal transcription + polish + style saves ~50% latency and API quota!
            let (processed_text, detected_language) = cloud_stt::transcribe_gemini_with_options(
                &key,
                &audio_bytes,
                &mime_type,
                &mode,
                &style,
                enable_smart_polish,
            )
            .await?;

            Ok(ProcessResult {
                raw_text: processed_text.clone(),
                processed_text,
                was_translated: mode == "bn_to_en",
                was_polished: enable_smart_polish || style != "normal",
                detected_language,
            })
        })
    }

    /// End-to-end recording processing pipeline with specific mode ("bn", "en", "bn_to_en", "auto")
    pub fn process_recording_with_mode(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
        mode: String,
        enable_smart_polish: bool,
    ) -> Result<ProcessResult, EngineError> {
        self.process_recording_full(
            audio_bytes,
            mime_type,
            mode,
            "normal".to_string(),
            enable_smart_polish,
        )
    }

    /// End-to-end recording processing pipeline
    pub fn process_recording(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
        translate_to_english: bool,
        enable_smart_polish: bool,
    ) -> Result<ProcessResult, EngineError> {
        let mode = if translate_to_english { "bn_to_en" } else { "bn" };
        self.process_recording_full(
            audio_bytes,
            mime_type,
            mode.to_string(),
            "normal".to_string(),
            enable_smart_polish,
        )
    }

    /// Calculate RMS amplitude for real-time waveform visualizers (0.0 to 1.0)
    pub fn calculate_amplitude(&self, pcm_bytes: Vec<u8>) -> f32 {
        audio::calculate_rms_amplitude(&pcm_bytes)
    }

    /// Encodes 16-bit PCM samples into standard WAV format
    pub fn encode_pcm_to_wav(&self, pcm_bytes: Vec<u8>, sample_rate: u32, channels: u16) -> Vec<u8> {
        audio::pcm_to_wav(&pcm_bytes, sample_rate, channels)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_wav_encoding_header() {
        let pcm = vec![0u8; 3200];
        let wav = audio::pcm_to_wav(&pcm, 16000, 1);
        assert_eq!(wav.len(), 44 + 3200);
        assert_eq!(&wav[0..4], b"RIFF");
        assert_eq!(&wav[8..12], b"WAVE");
        assert_eq!(&wav[36..40], b"data");
    }

    #[test]
    fn test_rms_amplitude() {
        let silent = vec![0u8; 100];
        assert_eq!(audio::calculate_rms_amplitude(&silent), 0.0);

        let loud: Vec<u8> = (0..100).flat_map(|_| 16384i16.to_le_bytes()).collect();
        let amp = audio::calculate_rms_amplitude(&loud);
        assert!(amp > 0.45 && amp < 0.55);
    }

    #[test]
    fn test_empty_api_key_rejected() {
        let engine = KothalipiMobileEngine::new("   ".to_string());
        let err = engine.validate_api_key().unwrap_err();
        assert!(matches!(err, EngineError::BadApiKey { .. }));
    }

    #[test]
    fn test_short_audio_rejected() {
        let engine = KothalipiMobileEngine::new("AIzaTestKey".to_string());
        let err = engine
            .process_recording_with_mode(vec![0u8; 100], "audio/wav".to_string(), "bn".to_string(), true)
            .unwrap_err();
        assert!(matches!(err, EngineError::InvalidAudio { .. }));
    }

    #[test]
    fn test_bangla_detection() {
        assert!(translation::contains_bangla("আমি বাংলায় কথা বলি"));
        assert!(!translation::contains_bangla("Hello world"));
    }
}
