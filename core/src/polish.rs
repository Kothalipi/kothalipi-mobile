use std::time::Duration;
use crate::cloud_stt::{extract_candidate_text, GEMINI_MODELS};
use crate::EngineError;

/// Cleans and formats raw transcribed speech with proper grammar, punctuation, spacing, and optional style
pub async fn smart_polish_with_style(
    api_key: &str,
    text: &str,
    language: &str,
    style: &str, // "normal", "formal", "casual", "bullets"
) -> Result<String, EngineError> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return Ok(String::new());
    }

    let trimmed_key = api_key.trim();
    if trimmed_key.is_empty() {
        return Ok(trimmed.to_string());
    }

    let lang_hint = if language == "bn" {
        "Bengali / Bangla"
    } else {
        "English"
    };

    let style_rule = match style {
        "formal" => "- Rewrite in a polite, professional, well-structured tone suitable for emails or formal writing.",
        "casual" => "- Keep it natural, concise, and friendly for chat messages.",
        "bullets" => "- Format the ideas into clear bullet points starting with '• '.",
        _ => "- Maintain the exact original tone, dialect, and meaning.",
    };

    let prompt = format!(
        "You are an expert audio transcription editor. Clean up the following {lang_hint} spoken text:\n\
         - Fix spelling mistakes, capitalization, and punctuation.\n\
         - Remove repeated/stutter words or filler words (um, uh, ah, মানে, আর কি).\n\
         {style_rule}\n\
         - NEVER translate to another language.\n\
         - Output ONLY the polished text without any quotes or explanations.\n\nText: {trimmed}"
    );

    let payload = serde_json::json!({
        "contents": [{ "parts": [{ "text": prompt }] }],
        "generationConfig": { "temperature": 0.1 }
    });

    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .map_err(|e| EngineError::NetworkError { detail: e.to_string() })?;

    for model in GEMINI_MODELS {
        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent?key={}",
            model, trimmed_key
        );

        if let Ok(resp) = client.post(&url).json(&payload).send().await {
            if resp.status().is_success() {
                if let Ok(json) = resp.json::<serde_json::Value>().await {
                    if let Some(t) = extract_candidate_text(&json) {
                        return Ok(t);
                    }
                }
            }
        }
    }

    // Fallback to raw text if polish fails
    Ok(trimmed.to_string())
}

/// Backward-compatible smart polish helper
pub async fn smart_polish(
    api_key: &str,
    text: &str,
    language: &str,
) -> Result<String, EngineError> {
    smart_polish_with_style(api_key, text, language, "normal").await
}
