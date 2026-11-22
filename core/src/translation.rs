use std::time::Duration;
use crate::cloud_stt::{extract_candidate_text, GEMINI_MODELS};
use crate::EngineError;

/// Checks if string contains Bengali script characters
pub fn contains_bangla(text: &str) -> bool {
    text.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c))
}

/// Translates spoken Bengali text into natural, fluent English using Gemini
pub async fn translate_bangla_to_english(
    api_key: &str,
    text: &str,
) -> Result<String, EngineError> {
    let trimmed = text.trim();
    if trimmed.is_empty() || (!contains_bangla(trimmed) && trimmed.is_ascii()) {
        return Ok(trimmed.to_string());
    }

    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Err(EngineError::BadApiKey {
            detail: "Gemini API key is required for translation".to_string(),
        });
    }

    let prompt = format!(
        "Translate the following spoken Bangla text into natural, fluent, and accurate English. \
         Output ONLY the translated English text, formatted with normal punctuation and capitalization. \
         Do NOT add quotes, markdown, notes, or explanations.\n\nText: {trimmed}"
    );

    let payload = serde_json::json!({
        "contents": [{ "parts": [{ "text": prompt }] }],
        "generationConfig": { "temperature": 0.0 }
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let mut last_err = String::new();
    let mut saw_rate_limit = false;

    for model in GEMINI_MODELS {
        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent?key={}",
            model, trimmed_key
        );

        let resp = client.post(&url).json(&payload).send().await;
        match resp {
            Ok(r) if r.status().is_success() => {
                if let Ok(json) = r.json::<serde_json::Value>().await {
                    if let Some(t) = extract_candidate_text(&json) {
                        return Ok(t);
                    }
                }
            }
            Ok(r) => {
                let code = r.status().as_u16();
                if code == 401 || code == 403 {
                    return Err(EngineError::BadApiKey {
                        detail: format!("HTTP {}", code),
                    });
                }
                if code == 429 {
                    saw_rate_limit = true;
                }
                last_err = format!("{} HTTP {}", model, code);
            }
            Err(e) => {
                last_err = e.to_string();
            }
        }
    }

    if saw_rate_limit {
        return Err(EngineError::RateLimitExceeded);
    }

    Err(EngineError::NetworkError {
        detail: format!("Translation failed: {}", last_err),
    })
}
