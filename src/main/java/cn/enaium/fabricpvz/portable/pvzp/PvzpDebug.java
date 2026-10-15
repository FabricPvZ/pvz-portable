/*
 * Ported from src/PvzpLib/PvzpDebug.h:19-80.
 */
package cn.enaium.fabricpvz.portable.pvzp;

import cn.enaium.fabricpvz.portable.sexy.Common;

/**
 * Ported from src/PvzpLib/PvzpDebug.h:19-80.
 *
 * <p>The portable logging/assert/error-box layer used by {@link Definition} and the reanim/particle
 * loaders. Upstream {@code PvzpLogLn} forwards to {@code Sexy::DispatchLogLn(Info, ...)}, which the
 * port keeps for the two error paths (assert/error box) and demotes to the debug channel for the
 * trace paths - see {@link #pvzpLogLn(String, Object...)}; {@code PvzpErrorMessageBox} opens a modal
 * SDL message box.
 *
 * <p>Java notes: {@code PvzpErrorMessageBox} has no window here, so it logs the message through
 * {@link Common#logErrorLn} and returns; the application layer decides whether to surface it.
 * {@code PvzpMalloc}/{@code PvzpFree} and {@code PvzpHesitationBracket} have no Java counterpart
 * (garbage collection replaces the raw block allocator, and the hesitation bracket is an empty macro
 * upstream).
 */
public final class PvzpDebug {
    private static long gLastTraceTime;

    private PvzpDebug() {
    }

    /**
     * Ported from src/PvzpLib/PvzpDebug.h:44-48 ({@code PvzpLogLn}).
     *
     * <p>Format string uses upstream {@code std::format} placeholders, which
     * {@link Common#strFormat(String, Object...)} implements.
     *
     * <p>PORT-NOTE: upstream this is a {@code Sexy::LogInfoLn} call, i.e. as loud as the game's boot
     * banner, but its call sites are the verbose ones (per resource group, per compiled definition,
     * per reanim, per cheat key, per movie-sync correction). It logs on the port's debug channel
     * ({@link Common#logDebugLn}) at DEBUG, so a normal run is quiet and
     * {@code -Dfabric.log.level=debug} brings every one of them back in the same format.
     */
    public static void pvzpLogLn(String theFmt, Object... theArgs) {
        Common.logDebugLn(theFmt, theArgs);
    }

    /** Ported from src/PvzpLib/PvzpDebug.h:51-61 ({@code PvzpTraceWithoutSpamming}): at most one trace per second. DEBUG. */
    public static void pvzpTraceWithoutSpamming(String theFmt, Object... theArgs) {
        long aTime = System.currentTimeMillis() / 1000L;
        if (aTime <= gLastTraceTime) {
            return;
        }
        gLastTraceTime = aTime;
        Common.logDebugLn(theFmt, theArgs);
    }

    /** Ported from src/PvzpLib/PvzpDebug.h:64-67 ({@code PvzpAssertFailed}: formats and reports). */
    public static void pvzpAssertFailed(String theCondition, String theFile, int theLine, String theMsg) {
        pvzpAssertReport(theCondition, theFile, theLine, theMsg);
    }

    /** Ported from src/PvzpLib/PvzpDebug.h:31 ({@code PvzpAssertReport}). */
    public static void pvzpAssertReport(String theCondition, String theFile, int theLine, String theMsg) {
        Common.logErrorLn("Assertion failed: ({}) {}:{} {}", theCondition, theFile, theLine, theMsg == null ? "" : theMsg);
        pvzpTraceMemory();
    }

    /** Ported from src/PvzpLib/PvzpDebug.h:32 ({@code PvzpErrorMessageBox}). */
    public static void pvzpErrorMessageBox(String theMessage, String theTitle) {
        Common.logErrorLn("{}: {}", theTitle, theMessage);
    }

    /**
     * Ported from src/PvzpLib/PvzpDebug.h:30 ({@code PvzpTraceMemory}); the upstream Debug build prints
     * the live allocation totals, which have no Java counterpart.
     */
    public static void pvzpTraceMemory() {
        Runtime aRuntime = Runtime.getRuntime();
        Common.logInfoLn("PvzpTraceMemory: used={} total={} max={}",
                aRuntime.totalMemory() - aRuntime.freeMemory(), aRuntime.totalMemory(), aRuntime.maxMemory());
    }
}
