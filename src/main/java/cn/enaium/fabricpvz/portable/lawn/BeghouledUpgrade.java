/*
 * Ported from src/Lawn/Challenge.h:48-54.
 *
 * Upstream enum BeghouledUpgrade (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class BeghouledUpgrade {
    public static final int BEGHOULED_UPGRADE_REPEATER = 0;
    public static final int BEGHOULED_UPGRADE_FUMESHROOM = 1;
    public static final int BEGHOULED_UPGRADE_TALLNUT = 2;
    public static final int NUM_BEGHOULED_UPGRADES = 3;

    private BeghouledUpgrade() {
    }
}
