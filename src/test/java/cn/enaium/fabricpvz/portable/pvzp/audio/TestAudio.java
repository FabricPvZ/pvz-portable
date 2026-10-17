package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Test fixture: builds in-memory 16-bit PCM WAV payloads so the backend tests can exercise loading,
 * playing and volume math without the retail assets.
 */
final class TestAudio {

    private TestAudio() {
    }

    /** A non-silent 440 Hz tone WAV with {@code frames} frames per channel. */
    static byte[] wavTone(int sampleRate, int channels, int frames) {
        short[] samples = new short[frames * channels];
        for (int frame = 0; frame < frames; frame++) {
            short value = (short) (Math.sin(2 * Math.PI * 440.0 * frame / sampleRate) * 8000);
            for (int channel = 0; channel < channels; channel++) {
                samples[frame * channels + channel] = value;
            }
        }
        return wav(sampleRate, channels, samples);
    }

    /** A 16-bit PCM RIFF/WAVE file around the given interleaved samples. */
    static byte[] wav(int sampleRate, int channels, short[] samples) {
        int blockAlign = channels * Short.BYTES;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(dataChunk(samples));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("RIFF"));
        writeUint32(out, 4 + data.size());
        out.writeBytes(ascii("WAVE"));
        out.writeBytes(chunk("fmt ", fmtPayload(1, channels, sampleRate, blockAlign, 16)));
        out.writeBytes(data.toByteArray());
        return out.toByteArray();
    }

    /**
     * A Sun/NeXT AU ({@code ".snd"}) container around the given µ-law encoded bytes, framed exactly
     * like the retail Steam assets ({@code sounds/diamond.au}): a 28-byte header — the six mandatory
     * big-endian words plus the 4-byte info field — followed by the payload.
     *
     * @param dataSize the big-endian {@code dataSize} header word ({@code ulaw.length} for a
     *     well-formed file, {@code 0xFFFFFFFF} for "unknown, up to the end of the file")
     */
    static byte[] auUlaw(int sampleRate, int channels, byte[] ulaw, long dataSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii(".snd"));
        writeUint32Be(out, 28);
        writeUint32Be(out, dataSize);
        writeUint32Be(out, 1); // encoding 1 == 8-bit ITU-T G.711 µ-law
        writeUint32Be(out, sampleRate);
        writeUint32Be(out, channels);
        writeUint32Be(out, 0); // info field
        out.writeBytes(ulaw);
        return out.toByteArray();
    }

    /** {@link #auUlaw(int, int, byte[], long)} with an accurate {@code dataSize} word. */
    static byte[] auUlaw(int sampleRate, int channels, byte[] ulaw) {
        return auUlaw(sampleRate, channels, ulaw, ulaw.length);
    }

    private static byte[] fmtPayload(int formatTag, int channels, int sampleRate, int blockAlign,
            int bitsPerSample) {
        byte[] payload = new byte[16];
        putUint16(payload, 0, formatTag);
        putUint16(payload, 2, channels);
        putUint32(payload, 4, sampleRate);
        putUint32(payload, 8, sampleRate * blockAlign);
        putUint16(payload, 12, blockAlign);
        putUint16(payload, 14, bitsPerSample);
        return payload;
    }

    private static byte[] dataChunk(short[] samples) {
        byte[] payload = new byte[samples.length * Short.BYTES];
        for (int i = 0; i < samples.length; i++) {
            putUint16(payload, i * Short.BYTES, samples[i] & 0xFFFF);
        }
        return chunk("data", payload);
    }

    private static byte[] chunk(String id, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii(id));
        writeUint32(out, payload.length);
        out.writeBytes(payload);
        if ((payload.length & 1) != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static void writeUint32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeUint32Be(ByteArrayOutputStream out, long value) {
        out.write((int) ((value >>> 24) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    private static void putUint16(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    private static void putUint32(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }
}
