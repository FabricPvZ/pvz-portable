package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Reports which libopenmpt the port loads and decodes the retail MO3 music with it - the check that
 * "the library loaded" is also "the music plays", on a machine whose game directory carries the
 * packaged native libraries:
 *
 * <pre>
 *   java -Dpvz.gameDir=&lt;unpacked distribution&gt; -cp &lt;test classes&gt;:lib/* \
 *        cn.enaium.fabricpvz.portable.pvzp.audio.NativeLibraryProbe
 * </pre>
 *
 * <p>It prints the platform directory the port resolves, every candidate of the probe order with
 * whether it is there, the candidate the packaged lookup selects, the availability and the version of
 * the loaded library, and for each of the game's two MO3 files the byte size, the duration and a full
 * pass through it (frames, sample count, share of non-zero samples, peak). It needs the real assets
 * (like the other asset-dependent tests) and to be run as its own JVM: the library is bound once per
 * process.
 */
public final class NativeLibraryProbe {

    /** The game's music: the two module files of main.pak (everything else is Ogg Vorbis or AU). */
    private static final List<String> MUSIC = List.of("sounds/MAINMUSIC.MO3", "sounds/MAINMUSIC_HIHATS.MO3");

    private NativeLibraryProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PROBE os=" + System.getProperty("os.name") + " arch=" + System.getProperty("os.arch")
                + " java=" + System.getProperty("java.version"));
        String platform = OpenMptLibrary.nativePlatform(
                System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        Path gameDirectory = OpenMptLibrary.gameDirectory();
        System.out.println("PROBE platform=" + platform
                + " packagedLibrary=" + OpenMptLibrary.packagedLibraryName(platform)
                + " systemLibrary=" + System.mapLibraryName("openmpt")
                + " gameDirectory=" + gameDirectory);

        for (Path candidate : OpenMptLibrary.candidatePaths(gameDirectory, platform)) {
            System.out.println("PROBE candidate=" + (Files.isRegularFile(candidate) ? "present " : "absent  ")
                    + candidate);
        }
        Path packaged = OpenMptLibrary.firstPresent(
                OpenMptLibrary.packagedCandidates(gameDirectory, platform), Files::isRegularFile);
        System.out.println("PROBE packagedCandidate=" + packaged);

        // The library binds once per process while this line is evaluated: the port's own line (path
        // and version) is logged here too.
        System.out.println("PROBE available=" + Mo3Decoder.isAvailable()
                + " version=" + OpenMptLibrary.version());
        if (!Mo3Decoder.isAvailable()) {
            System.out.println("PROBE reason=" + Mo3Decoder.unavailableReason());
            return;
        }

        PakInterface pak = mountMainPak();
        for (String music : MUSIC) {
            probeMusic(pak, music);
        }
    }

    private static PakInterface mountMainPak() throws Exception {
        Optional<Path> root = TestAssets.resourceRoot();
        if (root.isEmpty()) {
            throw new IllegalStateException("no real PvZ assets found (see TestAssets/ResourceLocator)");
        }
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        if (pakPath.isEmpty()) {
            throw new IllegalStateException("no main.pak under " + root.get());
        }
        System.out.println("PROBE assets=" + root.get() + " pak=" + pakPath.get());
        PakInterface pak = new PakInterface();
        pak.setResourceFolder(root.get().toString());
        pak.addPakFile(pakPath.get());
        return pak;
    }

    private static void probeMusic(PakInterface pak, String music) throws Exception {
        byte[] bytes = pak.readResource(music);
        if (bytes == null) {
            System.out.println("PROBE music=" + music + " missing");
            return;
        }
        double duration = Mo3Decoder.durationSeconds(bytes);

        long frames = 0;
        long samples = 0;
        long nonZero = 0;
        int peak = 0;
        short[] buffer = new short[4096 * Mo3Decoder.CHANNELS];
        try (Mo3Stream stream = Mo3Decoder.openMo3Stream(bytes)) {
            int read;
            while ((read = stream.read(buffer, 4096)) > 0) {
                frames += read;
                for (int i = 0; i < read * Mo3Decoder.CHANNELS; i++) {
                    int sample = buffer[i];
                    samples++;
                    if (sample != 0) {
                        nonZero++;
                    }
                    peak = Math.max(peak, Math.abs(sample));
                }
            }
        }
        System.out.printf(
                "PROBE music=%s bytes=%d duration=%.2fs frames=%d samples=%d nonZeroSamples=%d"
                        + " nonZeroRatio=%.4f peak=%d%n",
                music, bytes.length, duration, frames, samples, nonZero,
                samples == 0 ? 0.0 : (double) nonZero / samples, peak);
    }
}
