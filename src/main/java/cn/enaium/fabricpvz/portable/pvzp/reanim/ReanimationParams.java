package cn.enaium.fabricpvz.portable.pvzp.reanim;

/**
 * Ported from src/PvzpLib/Reanimator.h:94-100 (class ReanimationParams).
 *
 * <p>Maps a reanimation type to its data file name and {@link ReanimFlags} bits.
 */
public class ReanimationParams {
    public int mReanimationType;
    public String mReanimFileName;
    public int mReanimParamFlags;

    public ReanimationParams(int theReanimationType, String theReanimFileName, int theReanimParamFlags) {
        mReanimationType = theReanimationType;
        mReanimFileName = theReanimFileName;
        mReanimParamFlags = theReanimParamFlags;
    }
}
