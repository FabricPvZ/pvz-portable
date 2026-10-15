package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Flags.h:55.
 *
 * <p>Faithful port of {@code Sexy::ModalFlags}: an over/under pair of integer flag words with
 * a switch selecting which one is currently visible. The public fields keep their upstream names.
 */
public class ModalFlags {
    public int mOverFlags;
    public int mUnderFlags;
    public boolean mIsOver;

    // ModFlags
    public void modFlags(FlagsMod theFlagsMod) {
        mOverFlags = FlagsMod.getModFlags(mOverFlags, theFlagsMod);
        mUnderFlags = FlagsMod.getModFlags(mUnderFlags, theFlagsMod);
    }

    // GetFlags
    public int getFlags() {
        return mIsOver ? mOverFlags : mUnderFlags;
    }
}
