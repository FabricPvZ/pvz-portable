/*
 * Ported from src/ConstEnums.h:168-175.
 *
 * Upstream enum ChosenSeedState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ChosenSeedState {
    public static final int SEED_FLYING_TO_BANK = 0;
    public static final int SEED_IN_BANK = 1;
    public static final int SEED_FLYING_TO_CHOOSER = 2;
    public static final int SEED_IN_CHOOSER = 3;
    public static final int SEED_PACKET_HIDDEN = 4;

    private ChosenSeedState() {
    }
}
