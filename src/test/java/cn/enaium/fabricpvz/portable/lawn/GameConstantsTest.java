package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verifies the GameConstants.h port against the upstream values.
 */
class GameConstantsTest {

    @Test
    void boardConstantsMatchUpstream() {
        assertEquals(3.141592653589793, GameConstants.PI);
        assertEquals(800, GameConstants.BOARD_WIDTH);
        assertEquals(600, GameConstants.BOARD_HEIGHT);
        assertEquals(800, GameConstants.WIDE_BOARD_WIDTH);
        assertEquals(220, GameConstants.BOARD_OFFSET);
        assertEquals(-100, GameConstants.BOARD_EDGE);
        assertEquals(1180, GameConstants.BOARD_IMAGE_WIDTH_OFFSET);
        assertEquals(800, GameConstants.BOARD_ICE_START);
        assertEquals(40, GameConstants.LAWN_XMIN);
        assertEquals(80, GameConstants.LAWN_YMIN);
        assertEquals(30, GameConstants.HIGH_GROUND_HEIGHT);
    }

    @Test
    void seedBankConstantsMatchUpstream() {
        assertEquals(10, GameConstants.SEEDBANK_MAX);
        assertEquals(0, GameConstants.SEED_BANK_OFFSET_X);
        assertEquals(10, GameConstants.SEED_BANK_OFFSET_X_END);
        assertEquals(516, GameConstants.SEED_CHOOSER_OFFSET_Y);
        assertEquals(50, GameConstants.SEED_PACKET_WIDTH);
        assertEquals(70, GameConstants.SEED_PACKET_HEIGHT);
    }

    @Test
    void levelConstantsMatchUpstream() {
        assertEquals(5, GameConstants.ADVENTURE_AREAS);
        assertEquals(10, GameConstants.LEVELS_PER_AREA);
        assertEquals(50, GameConstants.NUM_LEVELS);
        assertEquals(50, GameConstants.FINAL_LEVEL);
        assertEquals(5, GameConstants.LAST_STAND_FLAGS);
        assertEquals(5, GameConstants.SURVIVAL_NORMAL_FLAGS);
        assertEquals(10, GameConstants.SURVIVAL_HARD_FLAGS);
    }
}
