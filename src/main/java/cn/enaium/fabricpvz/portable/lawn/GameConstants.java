/*
 * Ported from src/GameConstants.h:24-53.
 *
 * Board, seed-bank and level constants (plus the PI literal) from the upstream
 * header. Upstream constexpr const int/double values are exposed as
 * public static final fields with the upstream names.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class GameConstants {

    public static final double PI = 3.141592653589793;

    // Constants
    public static final int BOARD_WIDTH = 800;
    public static final int BOARD_HEIGHT = 600;
    public static final int WIDE_BOARD_WIDTH = 800;
    public static final int BOARD_OFFSET = 220;
    public static final int BOARD_EDGE = -100;
    public static final int BOARD_IMAGE_WIDTH_OFFSET = 1180;
    public static final int BOARD_ICE_START = 800;
    public static final int LAWN_XMIN = 40;
    public static final int LAWN_YMIN = 80;
    public static final int HIGH_GROUND_HEIGHT = 30;

    public static final int SEEDBANK_MAX = 10;
    public static final int SEED_BANK_OFFSET_X = 0;
    public static final int SEED_BANK_OFFSET_X_END = 10;
    public static final int SEED_CHOOSER_OFFSET_Y = 516;
    public static final int SEED_PACKET_WIDTH = 50;
    public static final int SEED_PACKET_HEIGHT = 70;

    // About levels
    public static final int ADVENTURE_AREAS = 5;
    public static final int LEVELS_PER_AREA = 10;
    public static final int NUM_LEVELS = ADVENTURE_AREAS * LEVELS_PER_AREA;
    public static final int FINAL_LEVEL = NUM_LEVELS;
    public static final int LAST_STAND_FLAGS = 5;
    public static final int SURVIVAL_NORMAL_FLAGS = 5;
    public static final int SURVIVAL_HARD_FLAGS = 10;

    private GameConstants() {
    }
}
