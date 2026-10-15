package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpFoley.h:153 (class FoleyParams).
 *
 * <p>Upstream stores {@code intptr_t* mSfxID[10]} (pointers to the {@code Sexy::SOUND_*} globals so
 * the table can be {@code constinit} before resource loading). This port stores the resource ids
 * directly; slot value {@code 0} is the {@code nullptr} terminator the lookup loop stops at, exactly
 * like upstream. The 10-slot capacity and the zero-padding of unused slots are preserved.
 */
public class FoleyParams {

    public static final int MAX_SFX_IDS = 10;

    public int mFoleyType;
    public float mPitchRange;
    public int[] mSfxID = new int[MAX_SFX_IDS];
    public int mFoleyFlags;

    public FoleyParams() {
    }

    public FoleyParams(int theFoleyType, float thePitchRange, int theFoleyFlags, int... theSfxIDs) {
        mFoleyType = theFoleyType;
        mPitchRange = thePitchRange;
        mFoleyFlags = theFoleyFlags;
        for (int i = 0; i < theSfxIDs.length && i < MAX_SFX_IDS; i++) {
            mSfxID[i] = theSfxIDs[i];
        }
    }
}
