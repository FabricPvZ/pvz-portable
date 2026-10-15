/*
 * Ported from src/ConstEnums.h:779-786.
 *
 * Upstream enum PottedPlantNeed (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PottedPlantNeed {
    public static final int PLANTNEED_NONE = 0;
    public static final int PLANTNEED_WATER = 1;
    public static final int PLANTNEED_FERTILIZER = 2;
    public static final int PLANTNEED_BUGSPRAY = 3;
    public static final int PLANTNEED_PHONOGRAPH = 4;

    private PottedPlantNeed() {
    }
}
