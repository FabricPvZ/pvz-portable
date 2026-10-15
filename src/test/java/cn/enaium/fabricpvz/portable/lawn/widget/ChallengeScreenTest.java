/*
 * Tests for ChallengeScreen / ChallengeDefinition static tables, lock/enablement logic and layout.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.GameMode;

/**
 * Exercises the pure (asset-free) surface of {@link ChallengeScreen}: the 72-entry
 * {@code gChallengeDefs} table, index alignment, page membership, the trophy lock rules
 * and the thumbnail layout constants.
 */
class ChallengeScreenTest {

    @Test
    void tableHasSeventyTwoEntriesIndexAlignedToGameMode() {
        assertEquals(72, ChallengeScreen.NUM_CHALLENGE_MODES);
        assertEquals(ChallengeScreen.NUM_CHALLENGE_MODES, ChallengeScreen.gChallengeDefs.length);

        for (int i = 0; i < ChallengeScreen.gChallengeDefs.length; i++) {
            // gChallengeDefs is strictly index-aligned: def index == GameMode - GAMEMODE_SURVIVAL_NORMAL_STAGE_1.
            assertEquals(i + 1, ChallengeScreen.gChallengeDefs[i].mChallengeMode,
                    "def " + i + " should open GameMode " + (i + 1));
            assertEquals(i, ChallengeScreen.getChallengeDefinition(i).mChallengeMode - 1);
        }
    }

    @Test
    void pageMembershipCounts() {
        assertEquals(11, countOnPage(ChallengePage.CHALLENGE_PAGE_SURVIVAL));
        assertEquals(20, countOnPage(ChallengePage.CHALLENGE_PAGE_CHALLENGE));
        assertEquals(21, countOnPage(ChallengePage.CHALLENGE_PAGE_LIMBO));
        assertEquals(20, countOnPage(ChallengePage.CHALLENGE_PAGE_PUZZLE));
        assertEquals(72, countOnPage(ChallengePage.CHALLENGE_PAGE_SURVIVAL)
                + countOnPage(ChallengePage.CHALLENGE_PAGE_CHALLENGE)
                + countOnPage(ChallengePage.CHALLENGE_PAGE_LIMBO)
                + countOnPage(ChallengePage.CHALLENGE_PAGE_PUZZLE));
    }

    @Test
    void rawLiteralNamesAreKept() {
        assertEquals("Squirrel", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SQUIRREL - 1).mChallengeName);
        assertEquals("Tree of Wisdom", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_TREE_OF_WISDOM - 1).mChallengeName);
        assertEquals("Upsell", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_UPSELL - 1).mChallengeName);
        assertEquals("Intro", ChallengeScreen.getChallengeDefinition(GameMode.GAMEMODE_INTRO - 1).mChallengeName);
    }

    @Test
    void entryEnablementTable() {
        for (int i = 0; i < ChallengeScreen.gChallengeDefs.length; i++) {
            // Unlocked (no pending trophy): every entry is enabled.
            assertTrue(ChallengeScreen.isEntryEnabled(i, 0),
                    "entry " + i + " (mode " + ChallengeScreen.gChallengeDefs[i].mChallengeMode + ") must be enabled");

            // A pending trophy requirement always disables the entry.
            assertFalse(ChallengeScreen.isEntryEnabled(i, 1), "entry " + i + " should stay locked");
            assertFalse(ChallengeScreen.isEntryEnabled(i, 10), "entry " + i + " should stay locked");
        }
    }

    @Test
    void layoutGeometry() {
        // ChallengeScreen.cpp:166-170 -> survival rows are 145 px apart from y=125, other pages 119 from y=93.
        assertEquals(38, ChallengeScreen.challengeButtonX(0));
        assertEquals(193, ChallengeScreen.challengeButtonX(1));
        assertEquals(658, ChallengeScreen.challengeButtonX(4));
        assertEquals(125, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0));
        assertEquals(415, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_SURVIVAL, 2));
        assertEquals(93, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0));
        assertEquals(450, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3));
        assertEquals(93, ChallengeScreen.challengeButtonY(ChallengePage.CHALLENGE_PAGE_PUZZLE, 0));

        assertEquals(104, ChallengeScreen.CHALLENGE_BUTTON_WIDTH);
        assertEquals(115, ChallengeScreen.CHALLENGE_BUTTON_HEIGHT);

        // ChallengeScreen.cpp:141-158 -> page buttons at (200 + 100*page, 540, 100, 75).
        assertEquals(200, ChallengeScreen.CHALLENGE_PAGE_BUTTON_X);
        assertEquals(100, ChallengeScreen.CHALLENGE_PAGE_BUTTON_PITCH);
        assertEquals(540, ChallengeScreen.CHALLENGE_PAGE_BUTTON_Y);
        assertEquals(100, ChallengeScreen.CHALLENGE_PAGE_BUTTON_WIDTH);
        assertEquals(75, ChallengeScreen.CHALLENGE_PAGE_BUTTON_HEIGHT);

        // ChallengeScreen.cpp:129-139 -> back button at (18, 568, 111, 26).
        assertEquals(18, ChallengeScreen.CHALLENGE_BACK_BUTTON_X);
        assertEquals(568, ChallengeScreen.CHALLENGE_BACK_BUTTON_Y);
        assertEquals(111, ChallengeScreen.CHALLENGE_BACK_BUTTON_WIDTH);
        assertEquals(26, ChallengeScreen.CHALLENGE_BACK_BUTTON_HEIGHT);
    }

    private static int countOnPage(int thePage) {
        int aCount = 0;
        for (ChallengeDefinition aDef : ChallengeScreen.gChallengeDefs) {
            if (aDef.mPage == thePage) {
                aCount++;
            }
        }
        return aCount;
    }
}
