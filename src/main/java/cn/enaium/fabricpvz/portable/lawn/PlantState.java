/*
 * Ported from src/Lawn/Plant.h:49-98.
 *
 * Upstream enum PlantState (int32_t); represented as public static final int constants
 * (API-CONTRACTS.md §6). Declaration order is the upstream ordinal order.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class PlantState {
    public static final int STATE_NOTREADY = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_DOINGSPECIAL = 2;
    public static final int STATE_SQUASH_LOOK = 3;
    public static final int STATE_SQUASH_PRE_LAUNCH = 4;
    public static final int STATE_SQUASH_RISING = 5;
    public static final int STATE_SQUASH_FALLING = 6;
    public static final int STATE_SQUASH_DONE_FALLING = 7;
    public static final int STATE_GRAVEBUSTER_LANDING = 8;
    public static final int STATE_GRAVEBUSTER_EATING = 9;
    public static final int STATE_CHOMPER_BITING = 10;
    public static final int STATE_CHOMPER_BITING_GOT_ONE = 11;
    public static final int STATE_CHOMPER_BITING_MISSED = 12;
    public static final int STATE_CHOMPER_DIGESTING = 13;
    public static final int STATE_CHOMPER_SWALLOWING = 14;
    public static final int STATE_POTATO_RISING = 15;
    public static final int STATE_POTATO_ARMED = 16;
    public static final int STATE_POTATO_MASHED = 17;
    public static final int STATE_SPIKEWEED_ATTACKING = 18;
    public static final int STATE_SPIKEWEED_ATTACKING_2 = 19;
    public static final int STATE_SCAREDYSHROOM_LOWERING = 20;
    public static final int STATE_SCAREDYSHROOM_SCARED = 21;
    public static final int STATE_SCAREDYSHROOM_RAISING = 22;
    public static final int STATE_SUNSHROOM_SMALL = 23;
    public static final int STATE_SUNSHROOM_GROWING = 24;
    public static final int STATE_SUNSHROOM_BIG = 25;
    public static final int STATE_MAGNETSHROOM_SUCKING = 26;
    public static final int STATE_MAGNETSHROOM_CHARGING = 27;
    public static final int STATE_BOWLING_UP = 28;
    public static final int STATE_BOWLING_DOWN = 29;
    public static final int STATE_CACTUS_LOW = 30;
    public static final int STATE_CACTUS_RISING = 31;
    public static final int STATE_CACTUS_HIGH = 32;
    public static final int STATE_CACTUS_LOWERING = 33;
    public static final int STATE_TANGLEKELP_GRABBING = 34;
    public static final int STATE_COBCANNON_ARMING = 35;
    public static final int STATE_COBCANNON_LOADING = 36;
    public static final int STATE_COBCANNON_READY = 37;
    public static final int STATE_COBCANNON_FIRING = 38;
    public static final int STATE_KERNELPULT_BUTTER = 39;
    public static final int STATE_UMBRELLA_TRIGGERED = 40;
    public static final int STATE_UMBRELLA_REFLECTING = 41;
    public static final int STATE_IMITATER_MORPHING = 42;
    public static final int STATE_ZEN_GARDEN_WATERED = 43;
    public static final int STATE_ZEN_GARDEN_NEEDY = 44;
    public static final int STATE_ZEN_GARDEN_HAPPY = 45;
    public static final int STATE_MARIGOLD_ENDING = 46;
    public static final int STATE_FLOWERPOT_INVULNERABLE = 47;
    public static final int STATE_LILYPAD_INVULNERABLE = 48;

    private PlantState() {
    }
}
