use std::time::Duration;
use crate::EngineError;

/// Checks if string contains Bengali script characters
pub fn contains_bangla(text: &str) -> bool {
    text.chars().any(|c| ('\u{0980}'..='\u{09FF}').contains(&c))
}

/// Translates spoken Bengali text into natural, fluent English using Gemini Flash Lite
pub async fn translate_bangla_to_english(
    api_key: &str,
    text: &str,
) -> Result<String, EngineError> {
    let trimmed = text.trim();
    if trimmed.is_empty() || (!contains_bangla(trimmed) && trimmed.is_ascii()) {
        return Ok(trimmed.to_string());
    }

    if api_key.trim().is_empty() {
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
        .timeout(Duration::from_secs(12))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let models = [
        "gemini-flash-lite-latest",
        "gemini-3.5-flash-lite",
        "gemini-3.8-flash",
        "gemini-flash-latest",
    ];
    let mut last_err = String::new();

    for model in models {
        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent?key={}",
            model, api_key
        );

        let resp = client.post(&url).json(&payload).send().await;
        match resp {
            Ok(r) if r.status().is_success() => {
                let json: serde_json::Value = r.json().await.map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;
                if let Some(t) = json["candidates"][0]["content"]["parts"][0]["text"].as_str() {
                    return Ok(t.trim().to_string());
                }
            }
            Ok(r) => {
                last_err = format!("HTTP {}", r.status());
            }
            Err(e) => {
                last_err = e.to_string();
            }
        }
    }

    Err(EngineError::NetworkError {
        detail: format!("Translation failed: {}", last_err),
    })
}
