use std::time::Duration;
use reqwest::multipart::{Form, Part};
use crate::EngineError;

/// Validates a Groq or OpenAI API key via their /models endpoint
pub async fn validate_openai_compatible_key(
    base_url: &str,
    provider_name: &str,
    api_key: &str,
) -> Result<String, EngineError> {
    let trimmed = api_key.trim();
    if trimmed.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: format!("{} API key is missing", provider_name),
        });
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(12))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let url = format!("{}/models", base_url.trim_end_matches('/'));
    let resp = client
        .get(&url)
        .bearer_auth(trimmed)
        .send()
        .await
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let status = resp.status();
    if status.is_success() {
        return Ok(format!("Connected to {}", provider_name));
    }
    let code = status.as_u16();
    let body = resp.text().await.unwrap_or_default();
    if code == 401 || code == 403 {
        return Err(EngineError::BadApiKey {
            detail: format!("{} HTTP {}: invalid API key", provider_name, code),
        });
    }
    if code == 429 {
        return Err(EngineError::RateLimitExceeded);
    }
    Err(EngineError::NetworkError {
        detail: format!("{} HTTP {}: {}", provider_name, code, body.chars().take(160).collect::<String>()),
    })
}

/// Transcribes audio via Groq Whisper (`whisper-large-v3-turbo`) or OpenAI Whisper (`whisper-1`)
pub async fn transcribe_whisper_compatible(
    base_url: &str,
    model: &str,
    api_key: &str,
    audio_bytes: &[u8],
    mode: &str, // "bn", "en", "bn_to_en", "auto"
) -> Result<(String, String), EngineError> {
    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "API key is missing".to_string(),
        });
    }
    if audio_bytes.len() < 1000 {
        return Err(EngineError::InvalidAudio {
            detail: "Audio recording is too short or empty".to_string(),
        });
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let endpoint = if mode == "bn_to_en" {
        format!("{}/audio/translations", base_url.trim_end_matches('/'))
    } else {
        format!("{}/audio/transcriptions", base_url.trim_end_matches('/'))
    };

    let part = Part::bytes(audio_bytes.to_vec())
        .file_name("audio.wav")
        .mime_str("audio/wav")
        .map_err(|e| EngineError::Other { detail: e.to_string() })?;

    let mut form = Form::new()
        .part("file", part)
        .text("model", model.to_string())
        .text("response_format", "json");

    if mode == "bn" {
        form = form.text("language", "bn");
    } else if mode == "en" {
        form = form.text("language", "en");
    }

    let resp = client
        .post(&endpoint)
        .bearer_auth(trimmed_key)
        .multipart(form)
        .send()
        .await
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let status = resp.status();
    let code = status.as_u16();
    let body = resp.text().await.unwrap_or_default();

    if status.is_success() {
        let json: serde_json::Value = serde_json::from_str(&body)
            .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;
        let text = json["text"].as_str().unwrap_or("").trim().to_string();
        if text.is_empty() {
            return Err(EngineError::InvalidAudio {
                detail: "No speech detected in audio".to_string(),
            });
        }
        let lang = if mode == "en" || mode == "bn_to_en" {
            "en"
        } else if text.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c)) {
            "bn"
        } else {
            "en"
        };
        return Ok((text, lang.to_string()));
    }

    if code == 401 || code == 403 {
        return Err(EngineError::BadApiKey {
            detail: format!("HTTP {}: invalid API key", code),
        });
    }
    if code == 429 {
        return Err(EngineError::RateLimitExceeded);
    }

    Err(EngineError::NetworkError {
        detail: format!("HTTP {}: {}", code, body.chars().take(180).collect::<String>()),
    })
}

/// Calls an OpenAI-compatible Chat Completions endpoint (Groq Llama 3.3 or OpenAI GPT-4o-mini)
pub async fn chat_complete_openai_compatible(
    base_url: &str,
    model: &str,
    api_key: &str,
    system_prompt: &str,
    user_prompt: &str,
) -> Result<String, EngineError> {
    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "API key is missing".to_string(),
        });
    }

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(20))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let url = format!("{}/chat/completions", base_url.trim_end_matches('/'));
    let payload = serde_json::json!({
        "model": model,
        "temperature": 0.1,
        "messages": [
            { "role": "system", "content": system_prompt },
            { "role": "user", "content": user_prompt }
        ]
    });

    let resp = client
        .post(&url)
        .bearer_auth(trimmed_key)
        .json(&payload)
        .send()
        .await
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let status = resp.status();
    let code = status.as_u16();
    let body = resp.text().await.unwrap_or_default();

    if status.is_success() {
        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body) {
            if let Some(content) = json["choices"][0]["message"]["content"].as_str() {
                let cleaned = content.trim().to_string();
                if !cleaned.is_empty() {
                    return Ok(cleaned);
                }
            }
        }
        return Err(EngineError::NetworkError {
            detail: "Empty completion response".to_string(),
        });
    }

    if code == 401 || code == 403 {
        return Err(EngineError::BadApiKey {
            detail: format!("HTTP {}: invalid API key", code),
        });
    }
    if code == 429 {
        return Err(EngineError::RateLimitExceeded);
    }

    Err(EngineError::NetworkError {
        detail: format!("HTTP {}: {}", code, body.chars().take(180).collect::<String>()),
    })
}

/// Rewrites existing text according to a spoken voice edit command using Gemini
pub async fn voice_edit_with_gemini(
    api_key: &str,
    existing_text: &str,
    audio_bytes: &[u8],
    mime_type: &str,
) -> Result<String, EngineError> {
    use base64::Engine;
    use crate::cloud_stt::{extract_candidate_text, GEMINI_MODELS};

    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "Gemini API key is missing".to_string(),
        });
    }
    if audio_bytes.len() < 1000 {
        return Err(EngineError::InvalidAudio {
            detail: "Voice command audio is too short".to_string(),
        });
    }

    let b64_audio = base64::engine::general_purpose::STANDARD.encode(audio_bytes);
    let effective_mime = if mime_type.trim().is_empty() { "audio/wav" } else { mime_type.trim() };

    let prompt = format!(
        "You are an expert bilingual (Bengali & English) writing assistant.\n\
         The user has the following EXISTING TEXT:\n\
         \"\"\"\n{existing_text}\n\"\"\"\n\n\
         Listen to the attached audio: it contains the user's VOICE EDIT COMMAND (in Bengali or English, e.g. \
         'এটাকে ফর্মাল ইমেইল বানাও', 'ইংরেজিতে শর্ট রিপ্লাই দাও', 'পয়েন্ট আকারে সাজাও', 'make it shorter', etc.).\n\
         Apply the user's spoken instruction to the EXISTING TEXT.\n\
         Output ONLY the final rewritten text. Do NOT add quotes, markdown fences, or explanations."
    );

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
        "generationConfig": { "temperature": 0.2 }
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let mut last_err = String::new();
    let mut saw_rate_limit = false;

    for model in GEMINI_MODELS {
        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent?key={}",
            model, trimmed_key
        );
        match client.post(&url).json(&payload).send().await {
            Ok(resp) => {
                let status = resp.status();
                let code = status.as_u16();
                let body = resp.text().await.unwrap_or_default();
                if status.is_success() {
                    if let Ok(json) = serde_json::from_str::<serde_json::Value>(&body) {
                        if let Some(t) = extract_candidate_text(&json) {
                            return Ok(t);
                        }
                    }
                } else if code == 401 || code == 403 {
                    return Err(EngineError::BadApiKey {
                        detail: format!("HTTP {}", code),
                    });
                } else if code == 429 {
                    saw_rate_limit = true;
                    last_err = format!("{} HTTP 429", model);
                } else {
                    last_err = format!("{} HTTP {}", model, code);
                }
            }
            Err(e) => last_err = e.to_string(),
        }
    }

    if saw_rate_limit {
        return Err(EngineError::RateLimitExceeded);
    }
    Err(EngineError::NetworkError {
        detail: format!("Voice edit failed: {}", last_err),
    })
}
