package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
 * <p>The library is an optional native dependency: libopenmpt must be installed (Homebrew
 * {@code brew install libopenmpt} on macOS, {@code apt install libopenmpt0} on Debian/Ubuntu, or a
 * libopenmpt build reachable from the library search path). When it is missing
 * {@link #isAvailable()} is false and {@link #unavailableReason()} explains why; MO3 music then
 * reports unavailable while the sound-effect decoders keep working. No class in this package ever
 * leaks an {@link UnsatisfiedLinkError} to its callers.
 *
 * <p>PORT-NOTE: upstream reaches libopenmpt through SDL's {@code SDL_LoadObject}/
 * {@code SDL_LoadFunction} dlopen shim; this port binds the same C ABI through the Java FFM API
 * ({@link Linker#nativeLinker()}) and discovers the library through {@code System.loadLibrary} or
 * {@link SymbolLookup#libraryLookup(Path, Arena)} over a list of platform paths. The loaded
 * {@link Arena} and {@link SymbolLookup} are cached statically for the process lifetime, together
 * with the availability result and failure reason (double-checked initialisation).
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

        // 1) System.loadLibrary("openmpt") -> loaderLookup()
        try {
            System.loadLibrary("openmpt");
            Handles bound = bind(SymbolLookup.loaderLookup());
            if (bound != null) {
                handles = bound;
                attempted = true;
                return;
            }
            problems.add("System.loadLibrary(\"openmpt\"): required symbols not found");
        } catch (Throwable t) {
            problems.add("System.loadLibrary(\"openmpt\"): " + describe(t));
        }

        // 2) explicit candidate paths
        List<Path> candidates = candidatePaths();
        Arena arena = Arena.ofShared();
        for (Path candidate : candidates) {
            try {
                Handles bound = bind(SymbolLookup.libraryLookup(candidate, arena));
                if (bound != null) {
                    handles = bound;
                    attempted = true;
                    return;
                }
                problems.add(candidate + ": required symbols not found");
            } catch (Throwable t) {
                problems.add(candidate + ": " + describe(t));
            }
        }
        try {
            arena.close();
        } catch (Throwable ignored) {
            // best effort: the process is out of options anyway
        }

        reason = "libopenmpt is not available; " + String.join("; ", problems)
                + ". Install libopenmpt (Homebrew: brew install libopenmpt, Debian/Ubuntu:"
                + " apt install libopenmpt0) or add it to the library search path.";
        attempted = true;

        // Exactly one warning per process: MO3 music reports unavailable while sound effects keep
        // working (upstream also only reports this when it tries to open a module).
        Common.logErrorLn("Music disabled: {}", reason);
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getName() + (message == null ? "" : ": " + message);
    }

    /** Candidate library paths, in probe order, including {@code System.mapLibraryName} variants. */
    private static List<Path> candidatePaths() {
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
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS)));
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

        Handles(MethodHandle createFromMemory, MethodHandle destroy, MethodHandle getModule,
                MethodHandle getInterface, MethodHandle setRepeatCount, MethodHandle getDurationSeconds,
                MethodHandle readFloatStereo, MethodHandle setPositionOrderRow, MethodHandle getCurrentOrder,
                MethodHandle getCurrentRow, MethodHandle ctlSetBoolean, MethodHandle getNumChannels) {
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
