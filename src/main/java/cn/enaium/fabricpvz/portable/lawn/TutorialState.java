/*
 * Ported from src/ConstEnums.h:1187-1220.
 *
 * Upstream enum TutorialState (int32_t); represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class TutorialState {
    public static final int TUTORIAL_OFF = 0;
    public static final int TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER = 1;
    public static final int TUTORIAL_LEVEL_1_PLANT_PEASHOOTER = 2;
    public static final int TUTORIAL_LEVEL_1_REFRESH_PEASHOOTER = 3;
    public static final int TUTORIAL_LEVEL_1_COMPLETED = 4;
    public static final int TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER = 5;
    public static final int TUTORIAL_LEVEL_2_PLANT_SUNFLOWER = 6;
    public static final int TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER = 7;
    public static final int TUTORIAL_LEVEL_2_COMPLETED = 8;
    public static final int TUTORIAL_MORESUN_PICK_UP_SUNFLOWER = 9;
    public static final int TUTORIAL_MORESUN_PLANT_SUNFLOWER = 10;
    public static final int TUTORIAL_MORESUN_REFRESH_SUNFLOWER = 11;
    public static final int TUTORIAL_MORESUN_COMPLETED = 12;
    public static final int TUTORIAL_SLOT_MACHINE_PULL = 13;
    public static final int TUTORIAL_SLOT_MACHINE_COMPLETED = 14;
    public static final int TUTORIAL_SHOVEL_PICKUP = 15;
    public static final int TUTORIAL_SHOVEL_DIG = 16;
    public static final int TUTORIAL_SHOVEL_KEEP_DIGGING = 17;
    public static final int TUTORIAL_SHOVEL_COMPLETED = 18;
    public static final int TUTORIAL_ZOMBIQUARIUM_BUY_SNORKEL = 19;
    public static final int TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL = 20;
    public static final int TUTORIAL_ZOMBIQUARIUM_CLICK_TROPHY = 21;
    public static final int TUTORIAL_ZEN_GARDEN_PICKUP_WATER = 22;
    public static final int TUTORIAL_ZEN_GARDEN_WATER_PLANT = 23;
    public static final int TUTORIAL_ZEN_GARDEN_KEEP_WATERING = 24;
    public static final int TUTORIAL_ZEN_GARDEN_VISIT_STORE = 25;
    public static final int TUTORIAL_ZEN_GARDEN_FERTILIZE_PLANTS = 26;
    public static final int TUTORIAL_ZEN_GARDEN_COMPLETED = 27;
    public static final int TUTORIAL_WHACK_A_ZOMBIE_BEFORE_PICK_SEED = 28;
    public static final int TUTORIAL_WHACK_A_ZOMBIE_PICK_SEED = 29;
    public static final int TUTORIAL_WHACK_A_ZOMBIE_COMPLETED = 30;

    private TutorialState() {
    }
}
