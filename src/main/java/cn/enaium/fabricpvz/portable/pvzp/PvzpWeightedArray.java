/*
 * Ported from src/PvzpLib/PvzpCommon.h:38-42 (struct PvzpWeightedArray).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpCommon.h:38-42 (struct PvzpWeightedArray).
 *
 * <p>A weighted pick entry: upstream stores {@code intptr_t mItem} (a raw pointer or an integer cast
 * into one) and {@code int32_t mWeight}; the Java port keeps the integer payload as a {@code long}.
 */
public final class PvzpWeightedArray {
    /** Upstream {@code mItem} ({@code intptr_t}). */
    public long mItem;

    /** Upstream {@code mWeight} ({@code int32_t}). */
    public int mWeight;

    public PvzpWeightedArray() {
    }

    public PvzpWeightedArray(long theItem, int theWeight) {
        mItem = theItem;
        mWeight = theWeight;
    }
}
