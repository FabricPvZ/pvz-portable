/*
 * Ported from src/PvzpLib/PvzpCommon.h:62-69 (class PvzpSmoothArray).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpCommon.h:62-69 (class PvzpSmoothArray).
 *
 * <p>A pick candidate that remembers how recently it was picked, so
 * {@link PvzpCommon#pvzpPickFromSmoothArray(PvzpSmoothArray[], int)} can spread the picks out
 * (upstream {@code Board.mRowPickingArray} uses one entry per row). Upstream is a value struct; Java
 * instances are references, so arrays of candidates must be filled with {@code new PvzpSmoothArray()}.
 */
public final class PvzpSmoothArray {
    /** Upstream {@code mItem} ({@code int32_t}). */
    public int mItem;

    /** Upstream {@code mWeight} (float). */
    public float mWeight;

    /** Upstream {@code mLastPicked} (float): pick rounds since this entry was picked. */
    public float mLastPicked;

    /** Upstream {@code mSecondLastPicked} (float): pick rounds since the pick before that. */
    public float mSecondLastPicked;

    public PvzpSmoothArray() {
    }

    public PvzpSmoothArray(int theItem, float theWeight) {
        mItem = theItem;
        mWeight = theWeight;
    }

    /** Creates {@code theCount} default entries (a value-struct array, per the class note). */
    public static PvzpSmoothArray[] createArray(int theCount) {
        PvzpSmoothArray[] anArray = new PvzpSmoothArray[theCount];
        for (int i = 0; i < theCount; i++) {
            anArray[i] = new PvzpSmoothArray();
        }
        return anArray;
    }
}
