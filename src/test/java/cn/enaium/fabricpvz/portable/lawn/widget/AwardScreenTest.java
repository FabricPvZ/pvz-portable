package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.AwardType;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Headless verification of the award screen: the start-button label table, the button ids and
 * layout rectangles, and the "is this a zombie note?" seam.
 *
 * <p>The screen itself can only be constructed with a full {@code LawnApp} that already owns a
 * {@code PlayerInfo}, so these tests exercise the static, side-effect-free rules the constructor
 * and the instance methods delegate to (each annotated with its upstream
 * {@code AwardScreen.cpp} line).
 */
class AwardScreenTest {

    // AwardScreen.h:36-40
    @Test
    void awardButtonIdsAreFixed() {
        assertEquals(100, AwardScreen.AwardScreen_Start);
        assertEquals(101, AwardScreen.AwardScreen_Menu);
    }

    // AwardScreen.cpp:184-243
    @Test
    void startButtonLabelFollowsTheAwardAndLevelTable() {
        // credits / help zombie notes
        assertEquals("[ROLL_CREDITS]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_CREDITS_ZOMBIENOTE, 7, true, true));
        assertEquals("[MAIN_MENU_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_HELP_ZOMBIENOTE, 1, true, false));

        // not adventure mode always falls back to the main menu
        assertEquals("[MAIN_MENU_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 7, false, false));
        // ...and takes precedence over the level-15 almanac branch
        assertEquals("[MAIN_MENU_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 15, false, false));

        // replaying level 1 after finishing the adventure
        assertEquals("[CONTINUE_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 1, true, true));
        // level 1 without a finished adventure is an ordinary next-level award
        assertEquals("[NEXT_LEVEL_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 1, true, false));

        assertEquals("[VIEW_ALMANAC_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 15, true, true));
        assertEquals("[CONTINUE_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 25, true, false));
        assertEquals("[CONTINUE_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 35, true, false));
        assertEquals("[CONTINUE_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 45, true, false));

        assertEquals("[NEXT_LEVEL_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 2, true, false));
        assertEquals("[NEXT_LEVEL_BUTTON]",
                AwardScreen.resolveStartButtonLabel(AwardType.AWARD_FORLEVEL, 50, true, false));
    }

    // AwardScreen.cpp:147,155,175
    @Test
    void buttonRectsMatchTheUpstreamLayout() {
        Rect start = AwardScreen.START_BUTTON_RECT;
        assertEquals(324, start.mX);
        assertEquals(500, start.mY);
        assertEquals(156, start.mWidth);
        assertEquals(42, start.mHeight);

        Rect continueButton = AwardScreen.CONTINUE_BUTTON_RECT;
        assertEquals(324, continueButton.mX);
        assertEquals(515, continueButton.mY);
        assertEquals(156, continueButton.mWidth);
        assertEquals(42, continueButton.mHeight);

        Rect menu = AwardScreen.MENU_BUTTON_RECT;
        assertEquals(677, menu.mX);
        assertEquals(16, menu.mY);
        assertEquals(111, menu.mWidth);
        assertEquals(26, menu.mHeight);
    }

    // AwardScreen.cpp:257-265
    @Test
    void paperNoteTruthTable() {
        // the credits/help zombie notes are always paper
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_CREDITS_ZOMBIENOTE, 1, true));
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_CREDITS_ZOMBIENOTE, 3, false));
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_HELP_ZOMBIENOTE, 1, false));

        // adventure notes at the end of each area
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 10, true));
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 20, true));
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 30, true));
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 40, true));
        assertTrue(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 50, true));

        // level rewards / other levels / non-adventure are not paper
        assertFalse(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 5, true));
        assertFalse(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 15, true));
        assertFalse(AwardScreen.isPaperNote(AwardType.AWARD_FORLEVEL, 10, false));
        assertFalse(AwardScreen.isPaperNote(AwardType.AWARD_ACHIEVEMENTONLY, 25, false));
    }
}
