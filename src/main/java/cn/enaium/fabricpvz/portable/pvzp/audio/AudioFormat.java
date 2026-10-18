package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;

/**
 * Audio container formats the port can decode, detected from the asset content (not from the file
 * name/extension).
 *
 * <p>Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:246-305 (the extension probe
 * that decided which codec got the bytes).
 *
 * <p>Upstream relies on SDL_mixer-X codec registration plus an extension probe order
 * ({@code SDLSoundManager.cpp:246}: {@code .wav}, {@code .mp3}, {@code .ogg}, then the hand-written
 * {@code .au} reader). The retail GOTY assets only contain {@code .ogg} (169 files) and {@code .mo3}
 * (2 files) — see ../verification/research/02-graphics-audio.md §10.10.3 — while the Steam build ships two
 * µ-law {@code .au} sounds ({@code sounds/diamond.au}, {@code sounds/tapglass.au}, see
 * ../verification/VERIFICATION.md §59), so this port sniffs the first bytes of the fetched asset and dispatches
 * to the matching decoder instead of guessing from the name.
 */
public enum AudioFormat {

    /** Ogg Vorbis ({@code "OggS"}), decoded with stb_vorbis (lwjgl-stb). */
    OGG,

    /** RIFF/WAVE PCM, decoded by {@link WavDecoder}. */
    WAV,

    /**
     * Sun/NeXT AU ({@code ".snd"}, encoding 1 = µ-law), decoded by {@link AuDecoder}.
     *
     * <p>Upstream's {@code SDLSoundManager::LoadAUSound} is the last resort of the extension probe
     * (SDLSoundManager.cpp:281-305), and the retail GOTY assets never reach it (169 {@code .ogg} +
     * 2 {@code .mo3}, no {@code .au}); the Steam assets do ({@code sounds/diamond.au},
     * {@code sounds/tapglass.au} — see ../verification/VERIFICATION.md §59).
     */
    AU,

    /** Un4seen MO3 tracker container ({@code "MO3" 0x01}), decoded by {@link Mo3Decoder}. */
    MO3,

    /** Nothing recognizable (upstream's legacy {@code .mp3} probe is not supported). */
    UNKNOWN;

    /**
     * Sniffs the container format of {@code data} from its magic bytes.
     *
     * <p>Recognizes {@code "RIFF"}{@code "WAVE"} (WAV), {@code "OggS"} (OGG), {@code ".snd"} (AU)
     * and {@code "MO3"} (MO3); anything else is {@link #UNKNOWN}.
     */
    public static AudioFormat sniff(byte[] data) {
        if (data == null || data.length < 12) {
            return UNKNOWN;
        }
        if (isAscii(data, 0, "RIFF") && isAscii(data, 8, "WAVE")) {
            return WAV;
        }
        if (isAscii(data, 0, "OggS")) {
            return OGG;
        }
        if (isAscii(data, 0, ".snd")) {
            return AU;
        }
        if (isAscii(data, 0, "MO3")) {
            return MO3;
        }
        return UNKNOWN;
    }

    private static boolean isAscii(byte[] data, int offset, String magic) {
        if (offset + magic.length() > data.length) {
            return false;
        }
        for (int i = 0; i < magic.length(); i++) {
            if ((data[offset + i] & 0xFF) != magic.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Decodes the complete asset into 16-bit PCM using the decoder for this format. */
    public PcmSound decodeSound(byte[] data) throws IOException {
        return switch (this) {
            case WAV -> WavDecoder.decode(data);
            case OGG -> OggVorbisDecoder.decode(data);
            case AU -> AuDecoder.decode(data);
            case MO3 -> Mo3Decoder.decode(data);
            case UNKNOWN -> throw new IOException("Unsupported audio format");
        };
    }

    /** Opens a streaming decoder for this format (used for music). */
    public AudioStream openStream(byte[] data) throws IOException {
        return switch (this) {
            case WAV -> WavDecoder.openStream(data);
            case OGG -> OggVorbisDecoder.openStream(data);
            case AU -> AuDecoder.openStream(data);
            case MO3 -> Mo3Decoder.openStream(data);
            case UNKNOWN -> throw new IOException("Unsupported audio format");
        };
    }
}
