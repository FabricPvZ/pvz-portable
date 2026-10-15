/*
 * Ported from src/ConstEnums.h:150-167.
 *
 * Upstream enum ChallengeState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ChallengeState {
    public static final int STATECHALLENGE_NORMAL = 0;
    public static final int STATECHALLENGE_BEGHOULED_MOVING = 1;
    public static final int STATECHALLENGE_BEGHOULED_FALLING = 2;
    public static final int STATECHALLENGE_BEGHOULED_NO_MATCHES = 3;
    public static final int STATECHALLENGE_SLOT_MACHINE_ROLLING = 4;
    public static final int STATECHALLENGE_STORM_FLASH_1 = 5;
    public static final int STATECHALLENGE_STORM_FLASH_2 = 6;
    public static final int STATECHALLENGE_STORM_FLASH_3 = 7;
    public static final int STATECHALLENGE_ZEN_FADING = 8;
    public static final int STATECHALLENGE_SCARY_POTTER_MALLETING = 9;
    public static final int STATECHALLENGE_LAST_STAND_ONSLAUGHT = 10;
    public static final int STATECHALLENGE_TREE_JUST_GREW = 11;
    public static final int STATECHALLENGE_TREE_GIVE_WISDOM = 12;
    public static final int STATECHALLENGE_TREE_WAITING_TO_BABBLE = 13;
    public static final int STATECHALLENGE_TREE_BABBLING = 14;

    private ChallengeState() {
    }
}
