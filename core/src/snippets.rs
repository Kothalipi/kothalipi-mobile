/// Voice Snippets / Shortcuts expansion engine.
/// Matches spoken trigger phrases (Bangla or English) and replaces them with saved text.

#[derive(Clone, Debug, uniffi::Record)]
pub struct VoiceSnippet {
    pub trigger: String,
    pub content: String,
}

fn normalize_phrase(s: &str) -> String {
    s.trim()
        .trim_matches(|c: char| matches!(c, '।' | '.' | ',' | '?' | '!' | '"' | '\'' | ';' | ':' | ' '))
        .to_lowercase()
}

/// Applies voice snippets to transcribed text.
/// 1. If the entire utterance matches a snippet trigger (ignoring punctuation/case), returns the snippet content.
/// 2. Also replaces exact trigger occurrences inside longer sentences.
pub fn apply_snippets(text: &str, snippets: &[VoiceSnippet]) -> String {
    let trimmed = text.trim();
    if trimmed.is_empty() || snippets.is_empty() {
        return trimmed.to_string();
    }

    let normalized_full = normalize_phrase(trimmed);

    // Priority 1: Exact utterance trigger match
    for s in snippets {
        let trig = normalize_phrase(&s.trigger);
        if !trig.is_empty() && normalized_full == trig {
            return s.content.clone();
        }
    }

    // Priority 2: Inline trigger replacement (longest triggers first)
    let mut sorted: Vec<&VoiceSnippet> = snippets
        .iter()
        .filter(|s| !s.trigger.trim().is_empty())
        .collect();
    sorted.sort_by(|a, b| b.trigger.len().cmp(&a.trigger.len()));

    let mut output = trimmed.to_string();
    for s in sorted {
        let trig_raw = s.trigger.trim();
        if output.contains(trig_raw) {
            output = output.replace(trig_raw, &s.content);
        } else {
            // Case-insensitive match for ASCII/English triggers
            let lower_out = output.to_lowercase();
            let lower_trig = trig_raw.to_lowercase();
            if let Some(pos) = lower_out.find(&lower_trig) {
                if output.is_char_boundary(pos) && output.is_char_boundary(pos + lower_trig.len()) {
                    output.replace_range(pos..pos + lower_trig.len(), &s.content);
                }
            }
        }
    }

    output
}
