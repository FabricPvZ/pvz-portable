/*
 * Ported from src/ConstEnums.h:772-778.
 *
 * Upstream enum PottedPlantAge (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PottedPlantAge {
    public static final int PLANTAGE_SPROUT = 0;
    public static final int PLANTAGE_SMALL = 1;
    public static final int PLANTAGE_MEDIUM = 2;
    public static final int PLANTAGE_FULL = 3;

    private PottedPlantAge() {
    }
}
