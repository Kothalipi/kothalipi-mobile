use std::time::Duration;
use base64::Engine;
use crate::EngineError;

/// Performs speech-to-text transcription via Google Gemini API with specific language mode
pub async fn transcribe_gemini_with_mode(
    api_key: &str,
    audio_bytes: &[u8],
    mime_type: &str,
    mode: &str, // "bn", "en", "bn_to_en"
) -> Result<(String, String), EngineError> {
    if api_key.trim().is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "Gemini API key is missing".to_string(),
        });
    }

    if audio_bytes.is_empty() {
        return Err(EngineError::InvalidAudio {
            detail: "Audio payload is empty".to_string(),
        });
    }

    let b64_audio = base64::engine::general_purpose::STANDARD.encode(audio_bytes);
    let effective_mime = if mime_type.is_empty() { "audio/wav" } else { mime_type };

    let prompt = match mode {
        "en" => {
            "Transcribe the spoken audio verbatim in English only. \
             Output ONLY the transcribed English words with proper punctuation and capitalization. \
             Do not translate, explain, or add commentary."
        }
        "bn_to_en" => {
            "Translate this spoken Bengali (Bangla) audio directly into natural, fluent, and polished English. \
             Output ONLY the translated English text with proper punctuation and capitalization. \
             Do not add quotes, explanations, or commentary."
        }
        _ => {
            // "bn" default
            "Transcribe the spoken audio verbatim in Bengali script (বাংলা হরফে). \
             Output ONLY the transcribed Bengali words with proper punctuation (যেমন দাঁড়ি, কমা). \
             Do not translate into English or use Roman letters. Do not add commentary."
        }
    };

    let payload = serde_json::json!({
        "contents": [
            {
                "parts": [
                    { "text": prompt },
                    {
                        "inline_data": {
                            "mime_type": effective_mime,
                            "data": b64_audio
                        }
                    }
                ]
            }
        ],
        "generationConfig": {
            "temperature": 0.0
        }
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(25))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    // High availability modern models in priority order
    let models = [
        "gemini-flash-lite-latest",
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-flash-latest",
    ];
    let mut last_error = String::new();

    for model in models {
        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent?key={}",
            model, api_key
        );

        let response = client
            .post(&url)
            .json(&payload)
            .send()
            .await;

        match response {
            Ok(resp) => {
                let status = resp.status();
                if status.is_success() {
                    let json: serde_json::Value = resp.json().await.map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;
                    if let Some(text) = json["candidates"][0]["content"]["parts"][0]["text"].as_str() {
                        let cleaned = text.trim().to_string();
                        let language = if mode == "en" || mode == "bn_to_en" {
                            "en"
                        } else if cleaned.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)) {
                            "bn"
                        } else {
                            "en"
                        };
                        return Ok((cleaned, language.to_string()));
                    }
                } else if status.as_u16() == 429 {
                    return Err(EngineError::RateLimitExceeded);
                } else if status.as_u16() == 400 || status.as_u16() == 403 {
                    return Err(EngineError::BadApiKey {
                        detail: format!("Google API error HTTP {}: invalid or restricted key", status),
                    });
                } else {
                    last_error = format!("HTTP {}", status);
                }
            }
            Err(e) => {
                last_error = e.to_string();
            }
        }
    }

    Err(EngineError::NetworkError {
        detail: format!("Transcription failed on all Gemini models: {}", last_error),
    })
}

/// Performs speech-to-text transcription via Google Gemini API (default Bangla mode)
pub async fn transcribe_gemini(
    api_key: &str,
    audio_bytes: &[u8],
    mime_type: &str,
) -> Result<(String, String), EngineError> {
    transcribe_gemini_with_mode(api_key, audio_bytes, mime_type, "bn").await
}
