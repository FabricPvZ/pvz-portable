package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Flags.h:75.
 *
 * <p>Faithful port of {@code Sexy::AutoModalFlags}: a scoped {@link FlagsMod} application on a
 * {@link ModalFlags}. Upstream restores the previous flags in the destructor; the Java
 * equivalent is {@link #close()} (and therefore try-with-resources), while the previous
 * values are still saved eagerly in the constructor exactly as upstream does.
 */
public class AutoModalFlags implements AutoCloseable {
    public ModalFlags mModalFlags;
    public int mOldOverFlags;
    public int mOldUnderFlags;

    public AutoModalFlags(ModalFlags theModalFlags, FlagsMod theFlagMod) {
        mModalFlags = theModalFlags;
        mOldOverFlags = theModalFlags.mOverFlags;
        mOldUnderFlags = theModalFlags.mUnderFlags;
        theModalFlags.modFlags(theFlagMod);
    }

    // ~AutoModalFlags()
    @Override
    public void close() {
        mModalFlags.mOverFlags = mOldOverFlags;
        mModalFlags.mUnderFlags = mOldUnderFlags;
    }
}
