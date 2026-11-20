/// Audio utilities for Kothalipi Mobile

/// Calculate root mean square (RMS) amplitude normalized from 0.0 to 1.0
pub fn calculate_rms_amplitude(pcm_bytes: &[u8]) -> f32 {
    if pcm_bytes.len() < 2 {
        return 0.0;
    }

    let mut sum_squares = 0.0f64;
    let sample_count = pcm_bytes.len() / 2;

    for chunk in pcm_bytes.chunks_exact(2) {
        let sample = i16::from_le_bytes([chunk[0], chunk[1]]) as f64;
        sum_squares += sample * sample;
    }

    let rms = (sum_squares / sample_count as f64).sqrt();
    let normalized = (rms / 32768.0) as f32;
    normalized.clamp(0.0, 1.0)
}

/// Encode 16-bit PCM samples into standard RIFF WAV format
pub fn pcm_to_wav(pcm_bytes: &[u8], sample_rate: u32, channels: u16) -> Vec<u8> {
    let mut wav = Vec::with_capacity(44 + pcm_bytes.len());
    let bits_per_sample = 16u16;
    let byte_rate = sample_rate * channels as u32 * (bits_per_sample as u32 / 8);
    let block_align = channels * (bits_per_sample / 8);
    let data_len = pcm_bytes.len() as u32;
    let total_len = 36 + data_len;

    // RIFF chunk descriptor
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&total_len.to_le_bytes());
    wav.extend_from_slice(b"WAVE");

    // "fmt " sub-chunk
    wav.extend_from_slice(b"fmt ");
    wav.extend_from_slice(&16u32.to_le_bytes()); // Subchunk1Size for PCM
    wav.extend_from_slice(&1u16.to_le_bytes());  // AudioFormat 1 = PCM
    wav.extend_from_slice(&channels.to_le_bytes());
    wav.extend_from_slice(&sample_rate.to_le_bytes());
    wav.extend_from_slice(&byte_rate.to_le_bytes());
    wav.extend_from_slice(&block_align.to_le_bytes());
    wav.extend_from_slice(&bits_per_sample.to_le_bytes());

    // "data" sub-chunk
    wav.extend_from_slice(b"data");
    wav.extend_from_slice(&data_len.to_le_bytes());
    wav.extend_from_slice(pcm_bytes);

    wav
}
