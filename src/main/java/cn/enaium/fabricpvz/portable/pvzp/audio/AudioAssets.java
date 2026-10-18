package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:320-334 (the path
 * resolution/probe loop).
 *
 * <p>Resource lookup for audio assets, replacing the extension probe of
 * {@code SDLSoundManager::DecodeSound} (src/SexyAppFramework/sound/SDLSoundManager.cpp:246-305).
 *
 * <p>Upstream appends {@code .wav}, {@code .mp3}, {@code .ogg} and finally tries an {@code .au}
 * reader, keeping the first one that decodes. This port keeps the same probe order but decides with
 * a content sniffer ({@link AudioFormat#sniff(byte[])}) instead of the extension, and reads every
 * candidate through {@link PakInterface} (pak member wins over the file system, exactly like
 * upstream's {@code p_fopen}).
 */
final class AudioAssets {

    /**
     * Upstream {@code gSoundFormats} ({@code SDLSoundManager.cpp:246}) extended with
     * {@code .mo3} (music only in the retail pak) and the verbatim resource name, so callers may pass
     * either an extension-less resource path ({@code "sounds/chomp"}) or a complete file name
     * ({@code "sounds/mainmusic.mo3"}).
     */
    static final String[] PROBE_EXTENSIONS = {".wav", ".mp3", ".ogg", ".mo3", ".au", ""};

    /** A resource that was found and whose content has been sniffed (format may be {@link AudioFormat#UNKNOWN}). */
    record Loaded(String name, byte[] data, AudioFormat format) {
    }

    private AudioAssets() {
    }

    /**
     * Reads every probe candidate that exists, in probe order; the caller decodes them in order and
     * keeps the first success. Empty when nothing was found.
     *
     * <p>Candidates whose container is not recognized are returned with
     * {@link AudioFormat#UNKNOWN} instead of being dropped, so a caller can tell an asset that is not
     * there at all (empty list) from one that is there but not decodable by this port — the loading
     * thread reports the former as a missing resource and only logs the latter
     * (../verification/VERIFICATION.md §59).
     */
    static List<Loaded> readAll(PakInterface pakInterface, String fileName) throws IOException {
        List<Loaded> found = new ArrayList<>();
        for (String extension : PROBE_EXTENSIONS) {
            byte[] data = pakInterface.readResource(fileName + extension);
            if (data == null || data.length == 0) {
                continue;
            }
            found.add(new Loaded(fileName + extension, data, AudioFormat.sniff(data)));
        }
        return found;
    }
}
