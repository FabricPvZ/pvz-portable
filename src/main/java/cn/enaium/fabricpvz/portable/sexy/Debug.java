package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Debug.h:31 (the {@code gInAssert} flag and the
 * {@code DBG_ASSERT}/{@code DBG_ASSERTE} macros at Debug.h:40-41).
 *
 * <p>Faithful port of the assertion helpers. Upstream's macros set {@code gInAssert} around
 * {@code assert(exp)}:
 * <pre>{@code { gInAssert = true; assert(exp); gInAssert = false; } }</pre>
 * which is reproduced here as static methods. In an {@code NDEBUG} (release) build upstream
 * compiles the macros away; the Java port always evaluates them, matching a debug build.
 *
 * <p>PORT-NOTE: the allocation-tracking helpers in Debug.cpp ({@code SexyMemAddTrack},
 * {@code SexyMemRemoveTrack}, {@code SexyDumpUnfreed}) instrument C++ {@code operator new}
 * through static {@code memmgr.h} hooks and have no Java analogue (allocation is managed by
 * the garbage collector), so they are not part of this class.
 */
public final class Debug {
    /** Ported from src/SexyAppFramework/misc/Debug.h:31. */
    public static boolean gInAssert = false;

    private Debug() {
    }

    // DBG_ASSERTE(exp)
    public static void dbgAsserte(boolean exp) {
        check(exp);
    }

    // DBG_ASSERT(exp)
    public static void dbgAssert(boolean exp) {
        check(exp);
    }

    private static void check(boolean exp) {
        gInAssert = true;
        try {
            if (!exp) {
                throw new AssertionError();
            }
        } finally {
            gInAssert = false;
        }
    }
}
