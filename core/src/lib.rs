use std::sync::RwLock;

pub mod audio;
pub mod cloud_stt;
pub mod polish;
pub mod translation;

uniffi::setup_scaffolding!();

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
            api_key: RwLock::new(api_key),
        }
    }

    pub fn set_api_key(&self, key: String) {
        if let Ok(mut lock) = self.api_key.write() {
            *lock = key;
        }
    }

    pub fn get_api_key(&self) -> String {
        self.api_key.read().map(|k| k.clone()).unwrap_or_default()
    }

    /// Transcribe raw audio bytes (e.g. WAV, M4A, MP3, AAC)
    pub fn transcribe_audio(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
    ) -> Result<TranscriptionResult, EngineError> {
        let key = self.get_api_key();
        let rt = tokio::runtime::Runtime::new()
            .map_err(|e| EngineError::Other { detail: e.to_string() })?;

        let (text, language) = rt.block_on(async {
            cloud_stt::transcribe_gemini(&key, &audio_bytes, &mime_type).await
        })?;

        Ok(TranscriptionResult { text, language })
    }

    /// Translate spoken Bengali into fluent English
    pub fn translate_bangla_to_english(&self, text: String) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = tokio::runtime::Runtime::new()
            .map_err(|e| EngineError::Other { detail: e.to_string() })?;

        rt.block_on(async {
            translation::translate_bangla_to_english(&key, &text).await
        })
    }

    /// Smart polish punctuation, capitalization, and filler words
    pub fn smart_polish(&self, text: String, language: String) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = tokio::runtime::Runtime::new()
            .map_err(|e| EngineError::Other { detail: e.to_string() })?;

        rt.block_on(async {
            polish::smart_polish(&key, &text, &language).await
        })
    }

    /// End-to-end recording processing pipeline with specific mode ("bn", "en", "bn_to_en")
    pub fn process_recording_with_mode(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
        mode: String,
        enable_smart_polish: bool,
    ) -> Result<ProcessResult, EngineError> {
        let key = self.get_api_key();
        let rt = tokio::runtime::Runtime::new()
            .map_err(|e| EngineError::Other { detail: e.to_string() })?;

        rt.block_on(async {
            let (raw_text, detected_language) =
                cloud_stt::transcribe_gemini_with_mode(&key, &audio_bytes, &mime_type, &mode).await?;

            if mode == "bn_to_en" {
                Ok(ProcessResult {
                    processed_text: raw_text.clone(),
                    raw_text,
                    was_translated: true,
                    was_polished: false,
                    detected_language: "en".to_string(),
                })
            } else if enable_smart_polish {
                let polished = polish::smart_polish(&key, &raw_text, &detected_language)
                    .await
                    .unwrap_or_else(|_| raw_text.clone());
                Ok(ProcessResult {
                    processed_text: polished,
                    raw_text,
                    was_translated: false,
                    was_polished: true,
                    detected_language,
                })
            } else {
                Ok(ProcessResult {
                    processed_text: raw_text.clone(),
                    raw_text,
                    was_translated: false,
                    was_polished: false,
                    detected_language,
                })
            }
        })
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
        self.process_recording_with_mode(audio_bytes, mime_type, mode.to_string(), enable_smart_polish)
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
