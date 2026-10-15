package cn.enaium.fabricpvz.portable.pvzp.audio;

/**
 * Fully decoded sound-effect data: interleaved signed 16-bit little-endian PCM, kept at the sample
 * rate and channel count of the source asset.
 *
 * <p>Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:201-243 (the 16-bit signed PCM
 * assembly of the hand-written {@code .au} reader).
 *
 * <p>Upstream SDL_mixer-X converts every decoded chunk to the live mixer spec (44100 Hz, signed
 * 16-bit, stereo) through {@code SDL_BuildAudioCVT} before it becomes a {@code Mix_Chunk}
 * (src/SexyAppFramework/sound/SDLSoundManager.cpp:206-243). OpenAL performs the same conversion per
 * buffer/source from the format described here, so no CPU-side resampling is needed.
 *
 * <p>MO3 SFX data ({@link #decodeSound}) is rendered as 44100 Hz stereo by libopenmpt, mirroring
 * the SDL-Mixer-X openmpt codec (which renders straight into the mixer format).
 *
 * @param sampleRate samples per second (e.g. 44100)
 * @param channels 1 (mono) or 2 (stereo)
 * @param samples interleaved samples; {@code samples.length} must be a multiple of {@code channels}
 */
public record PcmSound(int sampleRate, int channels, short[] samples) {

    public PcmSound {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        if (channels <= 0) {
            throw new IllegalArgumentException("channels must be positive: " + channels);
        }
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("samples must not be empty");
        }
        if (samples.length % channels != 0) {
            throw new IllegalArgumentException("samples length " + samples.length
                    + " is not a multiple of channels " + channels);
        }
    }

    /** Number of sample frames (one frame covers every channel). */
    public int frameCount() {
        return samples.length / channels;
    }

    /** Playback duration in seconds at {@link #sampleRate()}. */
    public double durationSeconds() {
        return frameCount() / (double) sampleRate;
    }

    /** True when at least one sample is non-zero (used by tests to reject silent decodes). */
    public boolean hasNonSilentSamples() {
        for (short sample : samples) {
            if (sample != 0) {
                return true;
            }
        }
        return false;
    }
}
