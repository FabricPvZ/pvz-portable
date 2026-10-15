/*
 * Ported from src/ConstEnums.h:765-771.
 *
 * Upstream enum PlantRowType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantRowType {
    public static final int PLANTROW_DIRT = 0;
    public static final int PLANTROW_NORMAL = 1;
    public static final int PLANTROW_POOL = 2;
    public static final int PLANTROW_HIGH_GROUND = 3;

    private PlantRowType() {
    }
}
