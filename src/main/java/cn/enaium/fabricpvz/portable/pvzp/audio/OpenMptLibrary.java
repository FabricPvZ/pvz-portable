package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import cn.enaium.fabricpvz.portable.sexy.Common;

/**
 * Foreign-function binding to the optional native {@code libopenmpt} shared library, plus the
 * process-lifetime module handles the MO3 decoder uses.
 *
 * <p>Ported from
 * src/SexyAppFramework/sound/SDL-Mixer-X/src/codecs/music_openmpt.c (the load-time construction at
 * lines 204-231, {@code OPENMPT_Jump} at 348-354, {@code OPENMPT_GetOrder} at 356-364 and
 * {@code OPENMPT_SetChannelVolume} at 395-400) and the installed
 * {@code libopenmpt_ext.h} (the {@code openmpt_module_ext_interface_interactive} layout at
 * libopenmpt_ext.h:147 and {@code openmpt_module_ext_destroy} at libopenmpt_ext.h:82).
 *
 * <p>The library is an optional native dependency. It is looked for in the game directory first -
 * {@code lib/native/<os>-<arch>/} and {@code native/<os>-<arch>/} of the game directory, which is
 * where the distribution ships it ({@code libopenmpt.dll} / {@code libopenmpt.dylib} /
 * {@code libopenmpt.so} plus, on Windows, the codec DLLs {@code libopenmpt.dll} imports) - and then
 * through the system lookup of the platform ({@code System.loadLibrary("openmpt")} and the platform's
 * usual install locations: Homebrew {@code brew install libopenmpt} on macOS, {@code apt install
 * libopenmpt0} on Debian/Ubuntu, {@code dnf install libopenmpt} on Fedora/RHEL - whose packages live
 * in {@code /usr/lib64} under the soname {@code libopenmpt.so.0}, both of which the candidate list
 * carries). When no copy is usable {@link #isAvailable()} is false
 * and {@link #unavailableReason()} names every path that was tried; MO3 music then reports
 * unavailable while the sound-effect decoders keep working. No class in this package ever leaks an
 * {@link UnsatisfiedLinkError} to its callers. Exactly one line is logged per process: INFO with the
 * path and the version when a library loaded, ERROR with the reason when none did.
 *
 * <p>PORT-NOTE: upstream reaches libopenmpt through SDL's {@code SDL_LoadObject}/
 * {@code SDL_LoadFunction} dlopen shim; this port binds the same C ABI through the Java FFM API
 * ({@link Linker#nativeLinker()}) and discovers the library through the game directory's own native
 * directory, {@code System.loadLibrary} or {@link SymbolLookup#libraryLookup(Path, Arena)} over a
 * list of platform paths. The loaded {@link Arena} and {@link SymbolLookup} are cached statically
 * for the process lifetime, together with the availability result and failure reason
 * (double-checked initialisation).
 *
 * <p>PORT-NOTE: upstream leaves the platform's library search path alone (SDL_Mixer-X's
 * {@code music_openmpt.c} opens the library by name, so a release has to put it where the loader
 * looks). This port loads the copy the distribution ships by absolute path first, which is what
 * makes the packaged library win over an older system one and what lets a player drop their own
 * build into {@code native/<os>-<arch>/}. The Windows builds of libopenmpt import their codec DLLs
 * statically ({@code openmpt-mpg123.dll}, {@code openmpt-vorbis.dll} - which imports
 * {@code openmpt-ogg.dll} - and {@code openmpt-zlib.dll}), and the retail music is
 * Ogg-Vorbis-compressed MO3, so those satellites are loaded from the same directory first; a
 * satellite that is missing only shows up as the library failing to load, which is reported like any
 * other unusable candidate.
 *
 * <p>PORT-NOTE: the FFM descriptors below were verified against the installed libopenmpt 0.8.9;
 * in particular {@code openmpt_module_ext_get_interface} must receive the exact 128-byte
 * {@code sizeof(openmpt_module_ext_interface_interactive)} and the two channel-volume function
 * pointers live at byte offsets 64 ({@code set_channel_volume}) and 72
 * ({@code get_channel_volume}). The module is always released with
 * {@code openmpt_module_ext_destroy} (the ext object owns the inner module), never with
 * {@code openmpt_module_destroy}.
 */
final class OpenMptLibrary {

    /** {@code sizeof(openmpt_module_ext_interface_interactive)} in libopenmpt_ext.h. */
    static final int INTERACTIVE_INTERFACE_SIZE = 128;

    /**
     * The launcher's game-directory property (the distribution's start scripts set it to the unpacked
     * root, the development run passes {@code run/}, and the provider reads it for {@code mods/}).
     * It is the directory the packaged native libraries are resolved against.
     */
    static final String GAME_DIRECTORY_PROPERTY = "pvz.gameDir";

    /** The directory of the packaged native libraries, relative to the game directory. */
    private static final String PACKAGED_NATIVE_DIRECTORY = "lib/native";

    /** The second directory a packaged native library is taken from, for one dropped in by hand. */
    private static final String LOOSE_NATIVE_DIRECTORY = "native";

    /**
     * The codec DLLs the Windows builds of libopenmpt are linked against, in load order (the
     * dependencies first: {@code openmpt-vorbis.dll} imports {@code openmpt-ogg.dll}, and
     * {@code libopenmpt.dll} imports the other three). They are loaded from the packaged directory
     * before the library itself.
     */
    private static final List<String> WINDOWS_SATELLITES = List.of(
            "openmpt-zlib.dll", "openmpt-mpg123.dll", "openmpt-ogg.dll", "openmpt-vorbis.dll");

    private static final long SET_CHANNEL_VOLUME_OFFSET = 64L;
    private static final long GET_CHANNEL_VOLUME_OFFSET = 72L;

    private static final Linker LINKER = Linker.nativeLinker();

    private static volatile boolean attempted;
    private static volatile Handles handles;
    private static volatile String reason = "";

    private OpenMptLibrary() {
    }

    /** True when libopenmpt was found and every required symbol resolved. */
    static boolean isAvailable() {
        return handles() != null;
    }

    /** Empty when libopenmpt is available; otherwise the discovery failure description. */
    static String unavailableReason() {
        handles(); // ensure initialisation ran
        return reason;
    }

    private static Handles handles() {
        if (!attempted) {
            init();
        }
        return handles;
    }

    private static synchronized void init() {
        if (attempted) {
            return;
        }
        List<String> problems = new ArrayList<>();

        Path gameDirectory = gameDirectory();
        String platform = nativePlatform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));

        // 1) the copy the distribution ships, in <gameDir>/lib/native/<os>-<arch>/ and - so that a
        //    library dropped in by hand is found too - <gameDir>/native/<os>-<arch>/. Loading it by
        //    absolute path is what makes the packaged copy win over a system one.
        for (Path candidate : packagedCandidates(gameDirectory, platform)) {
            Arena arena = Arena.ofShared();
            try {
                preloadSatellites(candidate.getParent(), platform, arena);
                Handles bound = bind(SymbolLookup.libraryLookup(candidate, arena));
                if (bound != null) {
                    handles = bound;
                    attempted = true;
                    Common.logInfoLn("Music: libopenmpt {} loaded from {}", version(), candidate);
                    return;
                }
                problems.add(candidate + ": required symbols not found");
            } catch (Throwable t) {
                problems.add(candidate + ": " + describe(t));
            }
            close(arena);
        }

        // 2) System.loadLibrary("openmpt") -> loaderLookup()
        try {
            System.loadLibrary("openmpt");
            Handles bound = bind(SymbolLookup.loaderLookup());
            if (bound != null) {
                handles = bound;
                attempted = true;
                Common.logInfoLn("Music: libopenmpt {} loaded from the system library search path"
                        + " (System.loadLibrary(\"openmpt\"))", version());
                return;
            }
            problems.add("System.loadLibrary(\"openmpt\"): required symbols not found");
        } catch (Throwable t) {
            problems.add("System.loadLibrary(\"openmpt\"): " + describe(t));
        }

        // 3) the platform's usual install locations, by absolute path
        Arena arena = Arena.ofShared();
        for (Path candidate : systemCandidates()) {
            try {
                Handles bound = bind(SymbolLookup.libraryLookup(candidate, arena));
                if (bound != null) {
                    handles = bound;
                    attempted = true;
                    Common.logInfoLn("Music: libopenmpt {} loaded from {}", version(), candidate);
                    return;
                }
                problems.add(candidate + ": required symbols not found");
            } catch (Throwable t) {
                problems.add(candidate + ": " + describe(t));
            }
        }
        close(arena);

        reason = "libopenmpt is not available; " + String.join("; ", problems)
                + ". The distribution ships it in " + PACKAGED_NATIVE_DIRECTORY + "/<os>-<arch>/ of the"
                + " game directory, or it can be installed (Homebrew: brew install libopenmpt,"
                + " Debian/Ubuntu: apt install libopenmpt0) or put on the library search path.";
        attempted = true;

        // Exactly one warning per process: MO3 music reports unavailable while sound effects keep
        // working (upstream also only reports this when it tries to open a module).
        Common.logErrorLn("Music disabled: {}", reason);
    }

    /** Releases a probe arena that did not yield a usable library (best effort). */
    private static void close(Arena arena) {
        try {
            arena.close();
        } catch (Throwable ignored) {
            // best effort: the process is out of options for this candidate anyway
        }
    }

    /**
     * Loads the codec DLLs of a packaged Windows directory before the library itself, in dependency
     * order, so that libopenmpt's own static imports resolve no matter what the JVM puts on the DLL
     * search path. A satellite that cannot be loaded is skipped: the library load then reports what
     * is missing, which is the diagnosis the candidate list is for.
     */
    private static void preloadSatellites(Path directory, String platform, Arena arena) {
        if (directory == null || platform == null || !platform.startsWith("windows")) {
            return;
        }
        for (String satellite : WINDOWS_SATELLITES) {
            Path path = directory.resolve(satellite);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                SymbolLookup.libraryLookup(path, arena);
            } catch (Throwable ignored) {
                // best effort: the library itself reports the failure that matters
            }
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getName() + (message == null ? "" : ": " + message);
    }

    /**
     * The {@code <os>-<arch>} directory name of the packaged native libraries for a platform, e.g.
     * {@code "windows-arm64"} - the shape {@code lib/native/} of the distribution uses. {@code null}
     * for a platform the distribution ships nothing for (another operating system, a 32-bit JVM, an
     * architecture no release targeted); the packaged directories are then skipped and the system
     * lookup decides, exactly like a build without the packaged libraries.
     */
    static String nativePlatform(String osName, String osArch) {
        String osNameLower = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String os;
        if (osNameLower.startsWith("windows")) {
            os = "windows";
        } else if (osNameLower.startsWith("mac") || osNameLower.startsWith("darwin")) {
            os = "macos";
        } else if (osNameLower.startsWith("linux")) {
            os = "linux";
        } else {
            return null;
        }

        String archName = osArch == null ? "" : osArch.toLowerCase(Locale.ROOT);
        String arch;
        if (archName.equals("amd64") || archName.equals("x86_64") || archName.equals("x64")) {
            arch = "x64";
        } else if (archName.equals("aarch64") || archName.equals("arm64")) {
            arch = "arm64";
        } else {
            return null;
        }
        return os + "-" + arch;
    }

    /**
     * The file name of the library inside a packaged directory: {@code libopenmpt.dll} on Windows,
     * {@code libopenmpt.dylib} on macOS, {@code libopenmpt.so} on Linux - upstream's own names, the
     * ones the distribution carries. It is stated per operating system because
     * {@code System.mapLibraryName("openmpt")} answers for the machine the JVM runs on (it says
     * {@code openmpt.dll} on Windows, though - not the name upstream uses, which is also why
     * {@code System.loadLibrary("openmpt")} cannot find an installed Windows copy, step 2 of
     * {@link #init()}).
     */
    static String packagedLibraryName(String platform) {
        String name = platform == null ? "" : platform.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return "libopenmpt.dll";
        }
        if (name.startsWith("macos")) {
            return "libopenmpt.dylib";
        }
        if (name.startsWith("linux")) {
            return "libopenmpt.so";
        }
        return System.mapLibraryName("openmpt");
    }

    /** The game directory the packaged libraries are resolved against. */
    static Path gameDirectory() {
        String property = System.getProperty(GAME_DIRECTORY_PROPERTY, "");
        if (!property.isBlank()) {
            return Path.of(property).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }

    /**
     * Every path the port probes, in probe order: the packaged candidates of the game directory
     * first, then the system ones (step 2 of {@link #init()} - {@code System.loadLibrary} - sits
     * between the two groups and has no path of its own).
     */
    static List<Path> candidatePaths(Path gameDirectory, String platform) {
        List<Path> paths = new ArrayList<>(packagedCandidates(gameDirectory, platform));
        paths.addAll(systemCandidates());
        return paths;
    }

    /**
     * The candidate files of the game directory's own native directories, in probe order: the one the
     * distribution fills ({@code lib/native/<os>-<arch>/}) and then a hand-placed one
     * ({@code native/<os>-<arch>/}), each holding {@link #packagedLibraryName(String)}. Empty when the
     * platform has no packaged directory of its own.
     */
    static List<Path> packagedCandidates(Path gameDirectory, String platform) {
        if (gameDirectory == null || platform == null) {
            return List.of();
        }
        String library = packagedLibraryName(platform);
        return List.of(
                gameDirectory.resolve(PACKAGED_NATIVE_DIRECTORY).resolve(platform).resolve(library),
                gameDirectory.resolve(LOOSE_NATIVE_DIRECTORY).resolve(platform).resolve(library));
    }

    /**
     * The first candidate {@code present} accepts, i.e. which candidate the probe order selects. The
     * packaged directories therefore win over an existing system library, and a candidate that is not
     * there cannot win by accident.
     */
    static Path firstPresent(List<Path> candidates, Predicate<Path> present) {
        for (Path candidate : candidates) {
            if (present.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The version of the loaded library as {@code major.minor.patch}, e.g. {@code "0.8.9"}, or
     * {@code "unknown"} when no library is loaded or the loaded one does not report a version.
     */
    static String version() {
        Handles h = handles();
        if (h == null || h.libraryVersion == null) {
            return "unknown";
        }
        try {
            int value = (int) h.libraryVersion.invoke();
            // openmpt_get_library_version(): (major << 24) + (minor << 16) + patch.
            return (value >>> 24) + "." + ((value >>> 16) & 0xFF) + "." + (value & 0xFFFF);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /**
     * The library directories of the Linux distributions this distribution supports, in probe order:
     * {@code /usr/lib64} (Fedora, RHEL, openSUSE and Arch's 64-bit layout), {@code /usr/lib} (Debian,
     * Ubuntu, Arch), the {@code /lib*} pair for distributions that do not merge {@code /} into
     * {@code /usr}, and the Debian/Ubuntu multiarch directories whose triplet names the architecture.
     */
    private static final List<String> LINUX_LIBRARY_DIRECTORIES = List.of(
            "/usr/lib64", "/usr/lib", "/lib64", "/lib",
            "/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu");

    /**
     * The file names a distribution installs libopenmpt under: the soname the shared-library packages
     * ship ({@code libopenmpt.so.0} - Fedora's {@code libopenmpt-0.8.6} holds exactly
     * {@code /usr/lib64/libopenmpt.so.0}, Debian's {@code libopenmpt0} the same name in the multiarch
     * directory) and the unversioned development name the {@code -devel}/{@code -dev} package adds.
     * The unversioned one is probed first, and without the soname a system installation would never be
     * found: {@code System.loadLibrary} needs a file literally named {@code libopenmpt.so} on
     * {@code java.library.path}, which a distribution without the development package does not have.
     */
    private static final List<String> LINUX_LIBRARY_NAMES = List.of("libopenmpt.so", "libopenmpt.so.0");

    /**
     * The system candidate paths, in probe order, including {@code System.mapLibraryName} variants:
     * the platform's usual install locations (Homebrew, {@code /usr/local}, {@code /usr} and the
     * Windows install directory of the official build) and then the library directories of the Linux
     * distributions (see {@link #LINUX_LIBRARY_DIRECTORIES}).
     */
    static List<Path> systemCandidates() {
        Set<Path> paths = new LinkedHashSet<>();
        paths.add(Path.of("/opt/homebrew/lib/libopenmpt.dylib"));
        paths.add(Path.of("/usr/local/lib/libopenmpt.dylib"));
        paths.add(Path.of("/opt/local/lib/libopenmpt.dylib"));
        paths.add(Path.of("/usr/lib/libopenmpt.so"));
        paths.add(Path.of("C:/Program Files/libopenmpt/libopenmpt.dll"));

        String mapped = System.mapLibraryName("openmpt");
        Set<Path> dirs = new LinkedHashSet<>();
        for (Path path : new ArrayList<>(paths)) {
            if (path.getParent() != null) {
                dirs.add(path.getParent());
            }
        }
        dirs.add(Path.of("/opt/homebrew/lib"));
        dirs.add(Path.of("/usr/local/lib"));
        dirs.add(Path.of("/usr/lib"));
        for (Path dir : dirs) {
            paths.add(dir.resolve(mapped));
        }

        // The Linux distribution directories come last, after the names the running platform's own
        // loader would use: a library put on java.library.path still wins over a distribution package.
        for (String dir : LINUX_LIBRARY_DIRECTORIES) {
            for (String name : LINUX_LIBRARY_NAMES) {
                paths.add(Path.of(dir, name));
            }
        }
        return new ArrayList<>(paths);
    }

    /** Resolves every required symbol and builds the downcall handles, or null when incomplete. */
    private static Handles bind(SymbolLookup lookup) {
        try {
            MemorySegment create = lookup.find("openmpt_module_ext_create_from_memory").orElse(null);
            MemorySegment destroy = lookup.find("openmpt_module_ext_destroy").orElse(null);
            MemorySegment getModule = lookup.find("openmpt_module_ext_get_module").orElse(null);
            MemorySegment getInterface = lookup.find("openmpt_module_ext_get_interface").orElse(null);
            MemorySegment setRepeat = lookup.find("openmpt_module_set_repeat_count").orElse(null);
            MemorySegment getDuration = lookup.find("openmpt_module_get_duration_seconds").orElse(null);
            MemorySegment readStereo = lookup.find("openmpt_module_read_interleaved_float_stereo").orElse(null);
            MemorySegment setPos = lookup.find("openmpt_module_set_position_order_row").orElse(null);
            MemorySegment getOrder = lookup.find("openmpt_module_get_current_order").orElse(null);
            MemorySegment getRow = lookup.find("openmpt_module_get_current_row").orElse(null);
            MemorySegment ctlBool = lookup.find("openmpt_module_ctl_set_boolean").orElse(null);
            MemorySegment numChannels = lookup.find("openmpt_module_get_num_channels").orElse(null);
            // Optional: openmpt_get_library_version() has been in the ABI since before the extended
            // interface this port requires, and the log line is not worth rejecting a library over.
            MemorySegment libraryVersion = lookup.find("openmpt_get_library_version").orElse(null);
            if (create == null || destroy == null || getModule == null || getInterface == null
                    || setRepeat == null || getDuration == null || readStereo == null || setPos == null
                    || getOrder == null || getRow == null || ctlBool == null || numChannels == null) {
                return null;
            }
            return new Handles(
                    LINKER.downcallHandle(create, FunctionDescriptor.of(
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(destroy, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(getModule, FunctionDescriptor.of(
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(getInterface, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)),
                    LINKER.downcallHandle(setRepeat, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)),
                    LINKER.downcallHandle(getDuration, FunctionDescriptor.of(
                            ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(readStereo, FunctionDescriptor.of(
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(setPos, FunctionDescriptor.of(
                            ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT)),
                    LINKER.downcallHandle(getOrder, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(getRow, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    LINKER.downcallHandle(ctlBool, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT)),
                    LINKER.downcallHandle(numChannels, FunctionDescriptor.of(
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)),
                    libraryVersion == null ? null
                            : LINKER.downcallHandle(libraryVersion, FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT)));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Creates a module from in-memory data, using the ext constructor exactly like
     * {@code music_openmpt.c:209}, and returns a lifetime-managing handle.
     *
     * <p>PORT-NOTE: the ext interface pointer table is allocated for the duration of the call only;
     * the two channel-volume function pointers are copied out before the scratch arena is released
     * (the pointers themselves are process-global libopenmpt functions).
     *
     * @throws IOException when the library is missing or the data is not a parseable module
     */
    static Module createModule(byte[] data) throws IOException {
        Handles h = handles();
        if (h == null) {
            throw new IOException(reason);
        }
        if (data == null || data.length == 0) {
            throw new IOException("libopenmpt: empty module data");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment memory = arena.allocate(data.length);
            MemorySegment.copy(data, 0, memory, ValueLayout.JAVA_BYTE, 0, data.length);

            MemorySegment ext = (MemorySegment) h.createFromMemory.invoke(memory, (long) data.length,
                    MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL,
                    MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
            if (ext == null || ext.equals(MemorySegment.NULL)) {
                throw new IOException("libopenmpt could not parse the module data"
                        + " (openmpt_module_ext_create_from_memory returned NULL)");
            }

            MemorySegment module = (MemorySegment) h.getModule.invoke(ext);
            if (module == null || module.equals(MemorySegment.NULL)) {
                h.destroy.invoke(ext);
                throw new IOException("libopenmpt: openmpt_module_ext_get_module returned NULL");
            }

            // music_openmpt.c:225/229: loop handling is driven by repeat_count, and seeking stops
            // all notes (the BASS_MUSIC_POSRESET equivalent).
            h.ctlSetBoolean.invoke(module, arena.allocateFrom("seek.sync_samples"), 0);

            MethodHandle setChannelVolume = null;
            MethodHandle getChannelVolume = null;
            MemorySegment iface = arena.allocate(INTERACTIVE_INTERFACE_SIZE);
            int found = (int) h.getInterface.invoke(ext, arena.allocateFrom("interactive"),
                    iface, (long) INTERACTIVE_INTERFACE_SIZE);
            if (found != 0) {
                setChannelVolume = LINKER.downcallHandle(
                        iface.get(ValueLayout.ADDRESS, SET_CHANNEL_VOLUME_OFFSET),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE));
                getChannelVolume = LINKER.downcallHandle(
                        iface.get(ValueLayout.ADDRESS, GET_CHANNEL_VOLUME_OFFSET),
                        FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT));
            }

            int channels = (int) h.getNumChannels.invoke(module);
            return new Module(h, ext, module, setChannelVolume, getChannelVolume, channels);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("libopenmpt module creation failed: " + describe(t), t);
        }
    }

    /** Immutable set of resolved downcall handles. */
    private static final class Handles {
        final MethodHandle createFromMemory;
        final MethodHandle destroy;
        final MethodHandle getModule;
        final MethodHandle getInterface;
        final MethodHandle setRepeatCount;
        final MethodHandle getDurationSeconds;
        final MethodHandle readFloatStereo;
        final MethodHandle setPositionOrderRow;
        final MethodHandle getCurrentOrder;
        final MethodHandle getCurrentRow;
        final MethodHandle ctlSetBoolean;
        final MethodHandle getNumChannels;

        /** {@code openmpt_get_library_version()}, or null when the library does not export it. */
        final MethodHandle libraryVersion;

        Handles(MethodHandle createFromMemory, MethodHandle destroy, MethodHandle getModule,
                MethodHandle getInterface, MethodHandle setRepeatCount, MethodHandle getDurationSeconds,
                MethodHandle readFloatStereo, MethodHandle setPositionOrderRow, MethodHandle getCurrentOrder,
                MethodHandle getCurrentRow, MethodHandle ctlSetBoolean, MethodHandle getNumChannels,
                MethodHandle libraryVersion) {
            this.createFromMemory = createFromMemory;
            this.destroy = destroy;
            this.getModule = getModule;
            this.getInterface = getInterface;
            this.setRepeatCount = setRepeatCount;
            this.getDurationSeconds = getDurationSeconds;
            this.readFloatStereo = readFloatStereo;
            this.setPositionOrderRow = setPositionOrderRow;
            this.getCurrentOrder = getCurrentOrder;
            this.getCurrentRow = getCurrentRow;
            this.ctlSetBoolean = ctlSetBoolean;
            this.getNumChannels = getNumChannels;
            this.libraryVersion = libraryVersion;
        }
    }

    /**
     * One live libopenmpt module. All standard functions operate on the inner module obtained from
     * the ext handle; the ext handle owns the module and is the only thing destroyed.
     */
    static final class Module implements AutoCloseable {

        private final Handles handles;
        private final MemorySegment ext;
        private final MemorySegment module;
        private final MethodHandle setChannelVolume;
        private final MethodHandle getChannelVolume;
        private final int numChannels;
        private boolean closed;

        private Module(Handles handles, MemorySegment ext, MemorySegment module,
                MethodHandle setChannelVolume, MethodHandle getChannelVolume, int numChannels) {
            this.handles = handles;
            this.ext = ext;
            this.module = module;
            this.setChannelVolume = setChannelVolume;
            this.getChannelVolume = getChannelVolume;
            this.numChannels = numChannels;
        }

        int numChannels() {
            return numChannels;
        }

        double durationSeconds() {
            try {
                return (double) handles.getDurationSeconds.invoke(module);
            } catch (Throwable t) {
                throw failure("openmpt_module_get_duration_seconds", t);
            }
        }

        void setRepeatCount(int repeatCount) {
            try {
                handles.setRepeatCount.invoke(module, repeatCount);
            } catch (Throwable t) {
                throw failure("openmpt_module_set_repeat_count", t);
            }
        }

        int readStereo(int sampleRate, int frames, MemorySegment out) {
            try {
                return (int) (long) handles.readFloatStereo.invoke(module, sampleRate, (long) frames, out);
            } catch (Throwable t) {
                throw failure("openmpt_module_read_interleaved_float_stereo", t);
            }
        }

        void setPositionOrderRow(int order, int row) {
            try {
                handles.setPositionOrderRow.invoke(module, order, row);
            } catch (Throwable t) {
                throw failure("openmpt_module_set_position_order_row", t);
            }
        }

        int currentOrder() {
            try {
                return (int) handles.getCurrentOrder.invoke(module);
            } catch (Throwable t) {
                throw failure("openmpt_module_get_current_order", t);
            }
        }

        int currentRow() {
            try {
                return (int) handles.getCurrentRow.invoke(module);
            } catch (Throwable t) {
                throw failure("openmpt_module_get_current_row", t);
            }
        }

        boolean setChannelVolume(int channel, double volume) {
            if (setChannelVolume == null) {
                return false;
            }
            try {
                return (int) setChannelVolume.invoke(ext, channel, volume) == 1;
            } catch (Throwable t) {
                throw failure("openmpt_module_ext_interface_interactive.set_channel_volume", t);
            }
        }

        double getChannelVolume(int channel) {
            if (getChannelVolume == null) {
                return 0.0;
            }
            try {
                return (double) getChannelVolume.invoke(ext, channel);
            } catch (Throwable t) {
                throw failure("openmpt_module_ext_interface_interactive.get_channel_volume", t);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                handles.destroy.invoke(ext);
            } catch (Throwable t) {
                throw failure("openmpt_module_ext_destroy", t);
            }
        }

        private static IllegalStateException failure(String call, Throwable cause) {
            return new IllegalStateException("libopenmpt call failed: " + call, cause);
        }
    }
}
