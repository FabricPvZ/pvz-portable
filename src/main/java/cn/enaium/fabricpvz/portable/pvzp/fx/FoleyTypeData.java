package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpFoley.h:197 (class FoleyTypeData).
 *
 * <p>{@code MAX_FOLEY_INSTANCES = 8} per type; {@code mLastVariationPlayed = -1} so the first
 * {@code DONT_REPEAT} pick has all variations available.
 */
public class FoleyTypeData {

    /** Ported from src/PvzpLib/PvzpFoley.h:31 ({@code MAX_FOLEY_INSTANCES}). */
    public static final int MAX_FOLEY_INSTANCES = 8;

    public FoleyInstance[] mFoleyInstances = new FoleyInstance[MAX_FOLEY_INSTANCES];
    public int mLastVariationPlayed;

    /** Ported from src/PvzpLib/PvzpFoley.cpp:145. */
    public FoleyTypeData() {
        for (int i = 0; i < MAX_FOLEY_INSTANCES; i++) {
            mFoleyInstances[i] = new FoleyInstance();
        }
        mLastVariationPlayed = -1;
    }
}
