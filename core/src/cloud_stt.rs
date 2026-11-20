use std::time::Duration;
use base64::Engine;
use crate::EngineError;

/// Performs speech-to-text transcription via Google Gemini API
pub async fn transcribe_gemini(
    api_key: &str,
    audio_bytes: &[u8],
    mime_type: &str,
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

    let prompt = "Transcribe the spoken audio verbatim in its original spoken language (primarily Bengali / Bangla or English). \
                  Output ONLY the transcribed words with proper punctuation. Do not translate, explain, or add commentary.";

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
        .timeout(Duration::from_secs(20))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let models = ["gemini-3.5-transcribe", "gemini-2.5-flash", "gemini-flash-latest"];
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
                        let language = if cleaned.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)) {
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
