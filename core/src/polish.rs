use std::time::Duration;
use crate::EngineError;

/// Cleans and formats raw transcribed speech with proper grammar, punctuation and spacing
pub async fn smart_polish(
    api_key: &str,
    text: &str,
    language: &str,
) -> Result<String, EngineError> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return Ok(String::new());
    }

    if api_key.trim().is_empty() {
        return Ok(trimmed.to_string());
    }

    let lang_hint = if language == "bn" { "Bengali / Bangla" } else { "English" };
    let prompt = format!(
        "You are an expert audio transcription editor. Clean up the following {lang_hint} spoken text: \
         - Fix spelling mistakes, capitalization, and punctuation. \
         - Remove repeated/stutter words or filler words (um, uh, ah, মানে, আর কি). \
         - Maintain the exact original tone, dialect, and meaning. \
         - NEVER translate or shorten the content. \
         - Output ONLY the polished text without any quotes or explanations.\n\nText: {trimmed}"
    );

    let payload = serde_json::json!({
        "contents": [{ "parts": [{ "text": prompt }] }],
        "generationConfig": { "temperature": 0.1 }
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(12))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    let url = format!(
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key={}",
        api_key
    );

    if let Ok(resp) = client.post(&url).json(&payload).send().await {
        if resp.status().is_success() {
            if let Ok(json) = resp.json::<serde_json::Value>().await {
                if let Some(t) = json["candidates"][0]["content"]["parts"][0]["text"].as_str() {
                    return Ok(t.trim().to_string());
                }
            }
        }
    }

    // Fallback to raw text if polish fails
    Ok(trimmed.to_string())
}
