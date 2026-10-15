/*
 * Ported from src/PvzpLib/PvzpCommon.h:44-49 (struct PvzpWeightedGridArray).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpCommon.h:44-49 (struct PvzpWeightedGridArray).
 *
 * <p>A weighted pick entry over a board grid cell; the public fields keep their upstream names.
 */
public final class PvzpWeightedGridArray {
    /** Upstream {@code mX} ({@code int32_t}). */
    public int mX;

    /** Upstream {@code mY} ({@code int32_t}). */
    public int mY;

    /** Upstream {@code mWeight} ({@code int32_t}). */
    public int mWeight;

    public PvzpWeightedGridArray() {
    }

    public PvzpWeightedGridArray(int theX, int theY, int theWeight) {
        mX = theX;
        mY = theY;
        mWeight = theWeight;
    }
}
