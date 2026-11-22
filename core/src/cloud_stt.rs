use std::time::Duration;
use base64::Engine;
use crate::EngineError;

/// Valid, widely-available Google Gemini models in priority order.
/// All support multimodal audio input via inlineData and Bangla/English text.
pub const GEMINI_MODELS: &[&str] = &[
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite",
    "gemini-2.0-flash",
    "gemini-flash-latest",
];

/// Extract human-readable error message from Google Gemini JSON error body
fn extract_google_error_message(body: &str) -> String {
    if let Ok(json) = serde_json::from_str::<serde_json::Value>(body) {
        if let Some(msg) = json["error"]["message"].as_str() {
            return msg.to_string();
        }
    }
    body.chars().take(240).collect()
}

/// Returns true if an HTTP 400/401/403 response is specifically caused by an invalid API key
fn is_api_key_error(status: u16, body: &str) -> bool {
    if status == 401 || status == 403 {
        return true;
    }
    let lower = body.to_lowercase();
    lower.contains("api_key_invalid")
        || lower.contains("api key not valid")
        || lower.contains("api key expired")
        || lower.contains("invalid api key")
}

/// Extract concatenated text from Gemini generateContent JSON response
pub fn extract_candidate_text(json: &serde_json::Value) -> Option<String> {
    let parts = json["candidates"][0]["content"]["parts"].as_array()?;
    let mut combined = String::new();
    for part in parts {
        if let Some(t) = part["text"].as_str() {
            combined.push_str(t);
        }
    }
    let trimmed = combined.trim().to_string();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

/// Validates the user's Gemini API key by querying the models endpoint
pub async fn validate_api_key(api_key: &str) -> Result<String, EngineError> {
    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "Gemini API key is missing".to_string(),
        });
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(12))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let url = format!(
        "https://generativelanguage.googleapis.com/v1beta/models?key={}&pageSize=5",
        trimmed_key
    );

    let resp = client
        .get(&url)
        .send()
        .await
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let status = resp.status();
    let body = resp.text().await.unwrap_or_default();

    if status.is_success() {
        return Ok("Connected to Google Gemini API".to_string());
    }

    let msg = extract_google_error_message(&body);
    if is_api_key_error(status.as_u16(), &body) {
        return Err(EngineError::BadApiKey {
            detail: format!("HTTP {}: {}", status.as_u16(), msg),
        });
    }
    if status.as_u16() == 429 {
        return Err(EngineError::RateLimitExceeded);
    }

    Err(EngineError::NetworkError {
        detail: format!("HTTP {}: {}", status.as_u16(), msg),
    })
}

/// Performs speech-to-text transcription via Google Gemini API with specific language mode and writing style
pub async fn transcribe_gemini_with_options(
    api_key: &str,
    audio_bytes: &[u8],
    mime_type: &str,
    mode: &str,  // "bn", "en", "bn_to_en", "auto"
    style: &str, // "normal", "formal", "casual", "bullets"
    enable_smart_polish: bool,
) -> Result<(String, String), EngineError> {
    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "Gemini API key is missing".to_string(),
        });
    }

    // Standard 44-byte WAV header + at least ~30ms of 16kHz 16-bit audio (~1000 bytes)
    if audio_bytes.len() < 1000 {
        return Err(EngineError::InvalidAudio {
            detail: "Audio recording is too short or empty".to_string(),
        });
    }

    let b64_audio = base64::engine::general_purpose::STANDARD.encode(audio_bytes);
    let effective_mime = if mime_type.trim().is_empty() {
        "audio/wav"
    } else {
        mime_type.trim()
    };

    let polish_instruction = if enable_smart_polish {
        " Remove filler words (um, uh, ah, মানে, আর কি), false starts, and stutters while preserving the exact meaning."
    } else {
        ""
    };

    let style_instruction = match style {
        "formal" => " Format the output in a polite, professional, well-structured tone suitable for emails or work.",
        "casual" => " Keep the tone natural, friendly, and conversational suitable for chat messages.",
        "bullets" => " Organize the spoken points into clean, concise bullet points using '• '.",
        _ => "",
    };

    let base_prompt = match mode {
        "en" => {
            "Transcribe the spoken audio accurately in English. \
             Output ONLY the final English text with proper punctuation and capitalization. \
             If there is no speech, output an empty string. Do not add commentary or quotes."
        }
        "bn_to_en" => {
            "Listen to this spoken Bengali (Bangla) audio and translate it directly into natural, fluent, and accurate English. \
             Output ONLY the final English text with proper punctuation and capitalization. \
             If there is no speech, output an empty string. Do not add quotes, explanations, or commentary."
        }
        "auto" => {
            "Transcribe the spoken audio accurately in the language spoken (Bengali script বাংলা for Bangla, Latin script for English, preserving natural bilingual mix if spoken). \
             Use proper punctuation (। for Bangla, . for English). \
             Output ONLY the final text. If there is no speech, output an empty string. Do not add commentary."
        }
        _ => {
            // "bn" default
            "Transcribe the spoken audio accurately in Bengali script (বাংলা হরফে). \
             Output ONLY the final Bengali text with proper punctuation (যেমন দাঁড়ি ।, কমা ,, প্রশ্নবোধক ?). \
             Do not translate into English or use Romanized Banglish unless an English technical term is explicitly spoken. \
             If there is no speech, output an empty string. Do not add commentary or quotes."
        }
    };

    let prompt = format!("{base_prompt}{polish_instruction}{style_instruction}");

    let payload = serde_json::json!({
        "contents": [
            {
                "parts": [
                    {
                        "inlineData": {
                            "mimeType": effective_mime,
                            "data": b64_audio
                        }
                    },
                    { "text": prompt }
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.1
        }
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let mut last_error = String::new();
    let mut saw_rate_limit = false;

    for model in GEMINI_MODELS {
        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent?key={}",
            model, trimmed_key
        );

        let response = client.post(&url).json(&payload).send().await;

        match response {
            Ok(resp) => {
                let status = resp.status();
                let status_code = status.as_u16();
                let body = resp.text().await.unwrap_or_default();

                if status.is_success() {
                    if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body) {
                        if let Some(cleaned) = extract_candidate_text(&json) {
                            let language = if mode == "en" || mode == "bn_to_en" {
                                "en"
                            } else if cleaned.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)) {
                                "bn"
                            } else {
                                "en"
                            };
                            return Ok((cleaned, language.to_string()));
                        } else {
                            return Err(EngineError::InvalidAudio {
                                detail: "No speech detected in audio".to_string(),
                            });
                        }
                    } else {
                        last_error = format!("Invalid JSON from {}", model);
                    }
                } else if is_api_key_error(status_code, &body) {
                    let msg = extract_google_error_message(&body);
                    return Err(EngineError::BadApiKey {
                        detail: format!("HTTP {}: {}", status_code, msg),
                    });
                } else if status_code == 429 {
                    saw_rate_limit = true;
                    last_error = format!("{} HTTP 429 rate limited", model);
                    // Try next fallback model before giving up!
                    continue;
                } else {
                    let msg = extract_google_error_message(&body);
                    last_error = format!("{} HTTP {}: {}", model, status_code, msg);
                    continue;
                }
            }
            Err(e) => {
                last_error = e.to_string();
            }
        }
    }

    if saw_rate_limit {
        return Err(EngineError::RateLimitExceeded);
    }

    Err(EngineError::NetworkError {
        detail: format!("Transcription failed on all Gemini models: {}", last_error),
    })
}

/// Backward-compatible transcription helper
pub async fn transcribe_gemini_with_mode(
    api_key: &str,
    audio_bytes: &[u8],
    mime_type: &str,
    mode: &str,
) -> Result<(String, String), EngineError> {
    transcribe_gemini_with_options(api_key, audio_bytes, mime_type, mode, "normal", false).await
}

/// Performs speech-to-text transcription via Google Gemini API (default Bangla mode)
pub async fn transcribe_gemini(
    api_key: &str,
    audio_bytes: &[u8],
    mime_type: &str,
) -> Result<(String, String), EngineError> {
    transcribe_gemini_with_options(api_key, audio_bytes, mime_type, "bn", "normal", false).await
}
