/*
 * Ported from src/ConstEnums.h:548-555.
 *
 * Upstream enum GridSquareType (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class GridSquareType {
    public static final int GRIDSQUARE_NONE = 0;
    public static final int GRIDSQUARE_GRASS = 1;
    public static final int GRIDSQUARE_DIRT = 2;
    public static final int GRIDSQUARE_POOL = 3;
    public static final int GRIDSQUARE_HIGH_GROUND = 4;

    private GridSquareType() {
    }
}
