/*
 * Ported from src/ConstEnums.h:1241-1339.
 *
 * Upstream enum ZombiePhase (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class ZombiePhase {
    public static final int PHASE_ZOMBIE_NORMAL = 0;
    public static final int PHASE_ZOMBIE_DYING = 1;
    public static final int PHASE_ZOMBIE_BURNED = 2;
    public static final int PHASE_ZOMBIE_MOWERED = 3;
    public static final int PHASE_BUNGEE_DIVING = 4;
    public static final int PHASE_BUNGEE_DIVING_SCREAMING = 5;
    public static final int PHASE_BUNGEE_AT_BOTTOM = 6;
    public static final int PHASE_BUNGEE_GRABBING = 7;
    public static final int PHASE_BUNGEE_RISING = 8;
    public static final int PHASE_BUNGEE_HIT_OUCHY = 9;
    public static final int PHASE_BUNGEE_CUTSCENE = 10;
    public static final int PHASE_POLEVAULTER_PRE_VAULT = 11;
    public static final int PHASE_POLEVAULTER_IN_VAULT = 12;
    public static final int PHASE_POLEVAULTER_POST_VAULT = 13;
    public static final int PHASE_RISING_FROM_GRAVE = 14;
    public static final int PHASE_JACK_IN_THE_BOX_RUNNING = 15;
    public static final int PHASE_JACK_IN_THE_BOX_POPPING = 16;
    public static final int PHASE_BOBSLED_SLIDING = 17;
    public static final int PHASE_BOBSLED_BOARDING = 18;
    public static final int PHASE_BOBSLED_CRASHING = 19;
    public static final int PHASE_POGO_BOUNCING = 20;
    public static final int PHASE_POGO_HIGH_BOUNCE_1 = 21;
    public static final int PHASE_POGO_HIGH_BOUNCE_2 = 22;
    public static final int PHASE_POGO_HIGH_BOUNCE_3 = 23;
    public static final int PHASE_POGO_HIGH_BOUNCE_4 = 24;
    public static final int PHASE_POGO_HIGH_BOUNCE_5 = 25;
    public static final int PHASE_POGO_HIGH_BOUNCE_6 = 26;
    public static final int PHASE_POGO_FORWARD_BOUNCE_2 = 27;
    public static final int PHASE_POGO_FORWARD_BOUNCE_7 = 28;
    public static final int PHASE_NEWSPAPER_READING = 29;
    public static final int PHASE_NEWSPAPER_MADDENING = 30;
    public static final int PHASE_NEWSPAPER_MAD = 31;
    public static final int PHASE_DIGGER_TUNNELING = 32;
    public static final int PHASE_DIGGER_RISING = 33;
    public static final int PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE = 34;
    public static final int PHASE_DIGGER_RISE_WITHOUT_AXE = 35;
    public static final int PHASE_DIGGER_STUNNED = 36;
    public static final int PHASE_DIGGER_WALKING = 37;
    public static final int PHASE_DIGGER_WALKING_WITHOUT_AXE = 38;
    public static final int PHASE_DIGGER_CUTSCENE = 39;
    public static final int PHASE_DANCER_DANCING_IN = 40;
    public static final int PHASE_DANCER_SNAPPING_FINGERS = 41;
    public static final int PHASE_DANCER_SNAPPING_FINGERS_WITH_LIGHT = 42;
    public static final int PHASE_DANCER_SNAPPING_FINGERS_HOLD = 43;
    public static final int PHASE_DANCER_DANCING_LEFT = 44;
    public static final int PHASE_DANCER_WALK_TO_RAISE = 45;
    public static final int PHASE_DANCER_RAISE_LEFT_1 = 46;
    public static final int PHASE_DANCER_RAISE_RIGHT_1 = 47;
    public static final int PHASE_DANCER_RAISE_LEFT_2 = 48;
    public static final int PHASE_DANCER_RAISE_RIGHT_2 = 49;
    public static final int PHASE_DANCER_RISING = 50;
    public static final int PHASE_DOLPHIN_WALKING = 51;
    public static final int PHASE_DOLPHIN_INTO_POOL = 52;
    public static final int PHASE_DOLPHIN_RIDING = 53;
    public static final int PHASE_DOLPHIN_IN_JUMP = 54;
    public static final int PHASE_DOLPHIN_WALKING_IN_POOL = 55;
    public static final int PHASE_DOLPHIN_WALKING_WITHOUT_DOLPHIN = 56;
    public static final int PHASE_SNORKEL_WALKING = 57;
    public static final int PHASE_SNORKEL_INTO_POOL = 58;
    public static final int PHASE_SNORKEL_WALKING_IN_POOL = 59;
    public static final int PHASE_SNORKEL_UP_TO_EAT = 60;
    public static final int PHASE_SNORKEL_EATING_IN_POOL = 61;
    public static final int PHASE_SNORKEL_DOWN_FROM_EAT = 62;
    public static final int PHASE_ZOMBIQUARIUM_ACCEL = 63;
    public static final int PHASE_ZOMBIQUARIUM_DRIFT = 64;
    public static final int PHASE_ZOMBIQUARIUM_BACK_AND_FORTH = 65;
    public static final int PHASE_ZOMBIQUARIUM_BITE = 66;
    public static final int PHASE_CATAPULT_LAUNCHING = 67;
    public static final int PHASE_CATAPULT_RELOADING = 68;
    public static final int PHASE_GARGANTUAR_THROWING = 69;
    public static final int PHASE_GARGANTUAR_SMASHING = 70;
    public static final int PHASE_IMP_GETTING_THROWN = 71;
    public static final int PHASE_IMP_LANDING = 72;
    public static final int PHASE_BALLOON_FLYING = 73;
    public static final int PHASE_BALLOON_POPPING = 74;
    public static final int PHASE_BALLOON_WALKING = 75;
    public static final int PHASE_LADDER_CARRYING = 76;
    public static final int PHASE_LADDER_PLACING = 77;
    public static final int PHASE_BOSS_ENTER = 78;
    public static final int PHASE_BOSS_IDLE = 79;
    public static final int PHASE_BOSS_SPAWNING = 80;
    public static final int PHASE_BOSS_STOMPING = 81;
    public static final int PHASE_BOSS_BUNGEES_ENTER = 82;
    public static final int PHASE_BOSS_BUNGEES_DROP = 83;
    public static final int PHASE_BOSS_BUNGEES_LEAVE = 84;
    public static final int PHASE_BOSS_DROP_RV = 85;
    public static final int PHASE_BOSS_HEAD_ENTER = 86;
    public static final int PHASE_BOSS_HEAD_IDLE_BEFORE_SPIT = 87;
    public static final int PHASE_BOSS_HEAD_IDLE_AFTER_SPIT = 88;
    public static final int PHASE_BOSS_HEAD_SPIT = 89;
    public static final int PHASE_BOSS_HEAD_LEAVE = 90;
    public static final int PHASE_YETI_RUNNING = 91;
    public static final int PHASE_SQUASH_PRE_LAUNCH = 92;
    public static final int PHASE_SQUASH_RISING = 93;
    public static final int PHASE_SQUASH_FALLING = 94;
    public static final int PHASE_SQUASH_DONE_FALLING = 95;

    private ZombiePhase() {
    }
}
