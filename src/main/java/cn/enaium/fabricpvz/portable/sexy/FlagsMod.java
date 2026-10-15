package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Flags.h:31.
 *
 * <p>Faithful port of {@code Sexy::FlagsMod}: a pair of add/remove bit masks applied to an
 * integer flag word. Upstream's free functions {@code ModFlags}/{@code GetModFlags}
 * (Flags.h:45 and Flags.h:50) are provided here as static methods because Java has no
 * pass-by-reference for primitives.
 */
public class FlagsMod {
    public int mAddFlags;
    public int mRemoveFlags;

    public FlagsMod() {
        mAddFlags = 0;
        mRemoveFlags = 0;
    }

    public FlagsMod(int theAddFlags, int theRemoveFlags) {
        mAddFlags = theAddFlags;
        mRemoveFlags = theRemoveFlags;
    }

    /**
     * Ported from src/SexyAppFramework/misc/Flags.h:45.
     *
     * <p>Upstream: {@code theFlags = (theFlags | mAddFlags) & ~mRemoveFlags;} returning the
     * new value instead of mutating a reference.
     */
    public int modFlags(int theFlags) {
        return (theFlags | mAddFlags) & ~mRemoveFlags;
    }

    /**
     * Ported from src/SexyAppFramework/misc/Flags.h:50.
     *
     * <p>Upstream: {@code return (theFlags | theFlagMod.mAddFlags) & ~theFlagMod.mRemoveFlags;}
     */
    public static int getModFlags(int theFlags, FlagsMod theFlagMod) {
        return (theFlags | theFlagMod.mAddFlags) & ~theFlagMod.mRemoveFlags;
    }
}
