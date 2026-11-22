use std::sync::{OnceLock, RwLock};

pub mod audio;
pub mod cloud_stt;
pub mod polish;
pub mod providers;
pub mod snippets;
pub mod translation;

pub use snippets::VoiceSnippet;

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

const GROQ_BASE_URL: &str = "https://api.groq.com/openai/v1";
const GROQ_WHISPER_MODEL: &str = "whisper-large-v3-turbo";
const GROQ_CHAT_MODEL: &str = "llama-3.3-70b-versatile";

const OPENAI_BASE_URL: &str = "https://api.openai.com/v1";
const OPENAI_WHISPER_MODEL: &str = "whisper-1";
const OPENAI_CHAT_MODEL: &str = "gpt-4o-mini";

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
    api_key: RwLock<String>,        // Gemini API Key
    groq_api_key: RwLock<String>,   // Groq API Key (Whisper + Llama 3.3)
    openai_api_key: RwLock<String>, // OpenAI API Key (Whisper + GPT-4o-mini)
    primary_provider: RwLock<String>, // "auto", "gemini", "groq", "openai"
    snippets: RwLock<Vec<VoiceSnippet>>,
}

#[uniffi::export]
impl KothalipiMobileEngine {
    #[uniffi::constructor]
    pub fn new(api_key: String) -> Self {
        Self {
            api_key: RwLock::new(api_key.trim().to_string()),
            groq_api_key: RwLock::new(String::new()),
            openai_api_key: RwLock::new(String::new()),
            primary_provider: RwLock::new("auto".to_string()),
            snippets: RwLock::new(Vec::new()),
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

    pub fn set_groq_api_key(&self, key: String) {
        if let Ok(mut lock) = self.groq_api_key.write() {
            *lock = key.trim().to_string();
        }
    }

    pub fn set_openai_api_key(&self, key: String) {
        if let Ok(mut lock) = self.openai_api_key.write() {
            *lock = key.trim().to_string();
        }
    }

    /// Primary provider: "auto", "gemini", "groq", "openai"
    pub fn set_primary_provider(&self, provider: String) {
        if let Ok(mut lock) = self.primary_provider.write() {
            *lock = provider.trim().to_lowercase();
        }
    }

    pub fn set_snippets(&self, items: Vec<VoiceSnippet>) {
        if let Ok(mut lock) = self.snippets.write() {
            *lock = items;
        }
    }

    /// Validates that the configured Gemini API key can connect to Google Gemini
    pub fn validate_api_key(&self) -> Result<String, EngineError> {
        let key = self.get_api_key();
        let rt = runtime()?;
        rt.block_on(async { cloud_stt::validate_api_key(&key).await })
    }

    /// Validates any provider key ("gemini", "groq", "openai")
    pub fn validate_provider_key(&self, provider: String, key: String) -> Result<String, EngineError> {
        let rt = runtime()?;
        rt.block_on(async {
            match provider.trim().to_lowercase().as_str() {
                "groq" => providers::validate_openai_compatible_key(GROQ_BASE_URL, "Groq", &key).await,
                "openai" => providers::validate_openai_compatible_key(OPENAI_BASE_URL, "OpenAI", &key).await,
                _ => cloud_stt::validate_api_key(&key).await,
            }
        })
    }

    /// Transcribe raw audio bytes (e.g. WAV, M4A, MP3, AAC)
    pub fn transcribe_audio(
        &self,
        audio_bytes: Vec<u8>,
        mime_type: String,
    ) -> Result<TranscriptionResult, EngineError> {
        let res = self.process_recording_full(
            audio_bytes,
            mime_type,
            "bn".to_string(),
            "normal".to_string(),
            false,
        )?;
        Ok(TranscriptionResult {
            text: res.processed_text,
            language: res.detected_language,
        })
    }

    /// Translate spoken Bengali into fluent English (with provider fallback)
    pub fn translate_bangla_to_english(&self, text: String) -> Result<String, EngineError> {
        let rt = runtime()?;
        let order = self.provider_order();

        rt.block_on(async {
            let mut last_err = EngineError::BadApiKey {
                detail: "No API key configured".to_string(),
            };
            for (prov, key) in order {
                let res = match prov {
                    "gemini" => translation::translate_bangla_to_english(&key, &text).await,
                    "groq" => {
                        providers::chat_complete_openai_compatible(
                            GROQ_BASE_URL,
                            GROQ_CHAT_MODEL,
                            &key,
                            "Translate the following spoken Bangla text into natural, fluent English. Output ONLY the translated English text.",
                            &text,
                        )
                        .await
                    }
                    "openai" => {
                        providers::chat_complete_openai_compatible(
                            OPENAI_BASE_URL,
                            OPENAI_CHAT_MODEL,
                            &key,
                            "Translate the following spoken Bangla text into natural, fluent English. Output ONLY the translated English text.",
                            &text,
                        )
                        .await
                    }
                    _ => continue,
                };
                match res {
                    Ok(t) => return Ok(t),
                    Err(e) => last_err = e,
                }
            }
            Err(last_err)
        })
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

    /// Voice Edit Command (AI Rewrite Mode):
    /// Rewrites `existing_text` according to a spoken voice instruction in `audio_bytes`.
    pub fn voice_edit_text(
        &self,
        existing_text: String,
        audio_bytes: Vec<u8>,
        mime_type: String,
    ) -> Result<ProcessResult, EngineError> {
        let rt = runtime()?;
        let order = self.provider_order();
        if order.is_empty() {
            return Err(EngineError::BadApiKey {
                detail: "No API key configured (Gemini, Groq, or OpenAI)".to_string(),
            });
        }

        rt.block_on(async {
            let mut last_err = EngineError::NetworkError {
                detail: "Voice edit failed".to_string(),
            };
            for (prov, key) in order {
                let res = match prov {
                    "gemini" => {
                        providers::voice_edit_with_gemini(&key, &existing_text, &audio_bytes, &mime_type).await
                    }
                    "groq" => {
                        match providers::transcribe_whisper_compatible(
                            GROQ_BASE_URL,
                            GROQ_WHISPER_MODEL,
                            &key,
                            &audio_bytes,
                            "auto",
                        )
                        .await
                        {
                            Ok((cmd, _)) => {
                                let user_msg = format!(
                                    "EXISTING TEXT:\n\"\"\"\n{existing_text}\n\"\"\"\n\nVOICE EDIT COMMAND:\n{cmd}"
                                );
                                providers::chat_complete_openai_compatible(
                                    GROQ_BASE_URL,
                                    GROQ_CHAT_MODEL,
                                    &key,
                                    "You are an expert bilingual (Bengali & English) writing assistant. Rewrite the EXISTING TEXT strictly following the VOICE EDIT COMMAND. Output ONLY the final rewritten text without quotes or commentary.",
                                    &user_msg,
                                )
                                .await
                            }
                            Err(e) => Err(e),
                        }
                    }
                    "openai" => {
                        match providers::transcribe_whisper_compatible(
                            OPENAI_BASE_URL,
                            OPENAI_WHISPER_MODEL,
                            &key,
                            &audio_bytes,
                            "auto",
                        )
                        .await
                        {
                            Ok((cmd, _)) => {
                                let user_msg = format!(
                                    "EXISTING TEXT:\n\"\"\"\n{existing_text}\n\"\"\"\n\nVOICE EDIT COMMAND:\n{cmd}"
                                );
                                providers::chat_complete_openai_compatible(
                                    OPENAI_BASE_URL,
                                    OPENAI_CHAT_MODEL,
                                    &key,
                                    "You are an expert bilingual (Bengali & English) writing assistant. Rewrite the EXISTING TEXT strictly following the VOICE EDIT COMMAND. Output ONLY the final rewritten text without quotes or commentary.",
                                    &user_msg,
                                )
                                .await
                            }
                            Err(e) => Err(e),
                        }
                    }
                    _ => continue,
                };

                match res {
                    Ok(rewritten) => {
                        let lang = if translation::contains_bangla(&rewritten) { "bn" } else { "en" };
                        return Ok(ProcessResult {
                            raw_text: existing_text,
                            processed_text: rewritten,
                            was_translated: false,
                            was_polished: true,
                            detected_language: lang.to_string(),
                        });
                    }
                    Err(EngineError::InvalidAudio { detail }) => {
                        return Err(EngineError::InvalidAudio { detail });
                    }
                    Err(e) => last_err = e,
                }
            }
            Err(last_err)
        })
    }

    /// Full end-to-end recording pipeline with Multi-Provider Auto-Fallback + Voice Snippets!
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
        let rt = runtime()?;
        let order = self.provider_order();
        if order.is_empty() {
            return Err(EngineError::BadApiKey {
                detail: "API key is missing (set Gemini, Groq, or OpenAI key in Settings)".to_string(),
            });
        }
        let active_snippets = self.snippets.read().map(|s| s.clone()).unwrap_or_default();

        rt.block_on(async {
            let mut last_err = EngineError::NetworkError {
                detail: "All providers failed".to_string(),
            };

            for (prov, key) in order {
                let res = match prov {
                    "gemini" => {
                        cloud_stt::transcribe_gemini_with_options(
                            &key,
                            &audio_bytes,
                            &mime_type,
                            &mode,
                            &style,
                            enable_smart_polish,
                        )
                        .await
                    }
                    "groq" => {
                        Self::run_whisper_and_format(
                            GROQ_BASE_URL,
                            GROQ_WHISPER_MODEL,
                            GROQ_CHAT_MODEL,
                            &key,
                            &audio_bytes,
                            &mode,
                            &style,
                            enable_smart_polish,
                        )
                        .await
                    }
                    "openai" => {
                        Self::run_whisper_and_format(
                            OPENAI_BASE_URL,
                            OPENAI_WHISPER_MODEL,
                            OPENAI_CHAT_MODEL,
                            &key,
                            &audio_bytes,
                            &mode,
                            &style,
                            enable_smart_polish,
                        )
                        .await
                    }
                    _ => continue,
                };

                match res {
                    Ok((text, detected_language)) => {
                        let final_text = snippets::apply_snippets(&text, &active_snippets);
                        return Ok(ProcessResult {
                            raw_text: text,
                            processed_text: final_text,
                            was_translated: mode == "bn_to_en",
                            was_polished: enable_smart_polish || style != "normal",
                            detected_language,
                        });
                    }
                    Err(EngineError::InvalidAudio { detail }) => {
                        return Err(EngineError::InvalidAudio { detail });
                    }
                    Err(e) => {
                        last_err = e;
                    }
                }
            }

            Err(last_err)
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

impl KothalipiMobileEngine {
    fn provider_order(&self) -> Vec<(&'static str, String)> {
        let gemini = self.api_key.read().map(|k| k.clone()).unwrap_or_default();
        let groq = self.groq_api_key.read().map(|k| k.clone()).unwrap_or_default();
        let openai = self.openai_api_key.read().map(|k| k.clone()).unwrap_or_default();
        let primary = self
            .primary_provider
            .read()
            .map(|p| p.clone())
            .unwrap_or_else(|_| "auto".to_string());

        let mut list: Vec<(&'static str, String)> = Vec::new();
        let ordered: [(&'static str, String); 3] = match primary.as_str() {
            "groq" => [("groq", groq), ("gemini", gemini), ("openai", openai)],
            "openai" => [("openai", openai), ("gemini", gemini), ("groq", groq)],
            _ => [("gemini", gemini), ("groq", groq), ("openai", openai)],
        };
        for (name, key) in ordered {
            if !key.trim().is_empty() {
                list.push((name, key));
            }
        }
        list
    }

    async fn run_whisper_and_format(
        base_url: &str,
        whisper_model: &str,
        chat_model: &str,
        api_key: &str,
        audio_bytes: &[u8],
        mode: &str,
        style: &str,
        enable_smart_polish: bool,
    ) -> Result<(String, String), EngineError> {
        let (raw, lang) = providers::transcribe_whisper_compatible(
            base_url,
            whisper_model,
            api_key,
            audio_bytes,
            mode,
        )
        .await?;

        if !enable_smart_polish && style == "normal" {
            return Ok((raw, lang));
        }

        let style_rule = match style {
            "formal" => "Rewrite in a polite, professional tone suitable for emails.",
            "casual" => "Keep it natural, friendly, and concise for chat messages.",
            "bullets" => "Format the points into clean bullet points starting with '• '.",
            _ => "Maintain exact original tone and meaning.",
        };
        let sys = format!(
            "You are an expert Bengali and English transcription editor. Fix punctuation and remove filler words (um, uh, মানে, আর কি). {style_rule} Do NOT translate. Output ONLY the final text."
        );
        let formatted = providers::chat_complete_openai_compatible(base_url, chat_model, api_key, &sys, &raw)
            .await
            .unwrap_or(raw);
        Ok((formatted, lang))
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

    #[test]
    fn test_voice_snippets_exact_and_inline() {
        let list = vec![
            VoiceSnippet {
                trigger: "আমার ঠিকানা বলো".to_string(),
                content: "House 12, Road 4, Dhanmondi, Dhaka".to_string(),
            },
            VoiceSnippet {
                trigger: "বিকাশ নম্বর".to_string(),
                content: "01700-000000 (Personal)".to_string(),
            },
        ];
        assert_eq!(
            snippets::apply_snippets("আমার ঠিকানা বলো।", &list),
            "House 12, Road 4, Dhanmondi, Dhaka"
        );
        assert_eq!(
            snippets::apply_snippets("আমার বিকাশ নম্বর হলো", &list),
            "আমার 01700-000000 (Personal) হলো"
        );
    }
}
