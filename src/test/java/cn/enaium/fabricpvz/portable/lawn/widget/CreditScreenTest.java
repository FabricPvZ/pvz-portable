/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.CreditScreen.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless checks for {@link CreditScreen}: the 268-row karaoke timing table, the verbatim rows,
 * the button ids / layout constants and the constructor wiring.
 */
class CreditScreenTest {

    // CreditScreen.cpp:42-312
    @Test
    void timingTableHasEveryRow() {
        assertEquals(268, CreditScreen.gCreditsTimingCount);
        assertEquals(268, CreditScreen.gCreditsTiming.length);
        assertEquals(CreditScreen.gCreditsTimingCount, CreditScreen.gCreditsTiming.length);
    }

    // CreditScreen.cpp:42-311
    @Test
    void timingFramesRunStrictlyIncreasingFrom128Point5To1033() {
        CreditScreen.CreditsTiming[] aTiming = CreditScreen.gCreditsTiming;

        assertEquals(128.5f, aTiming[0].mFrame, 0.0f);
        assertEquals(1033.0f, aTiming[CreditScreen.gCreditsTimingCount - 1].mFrame, 0.0f);

        for (int i = 0; i < CreditScreen.gCreditsTimingCount - 1; i++) {
            assertTrue(aTiming[i].mFrame < aTiming[i + 1].mFrame,
                    "row " + i + " frame " + aTiming[i].mFrame + " must precede row " + (i + 1)
                            + " frame " + aTiming[i + 1].mFrame);
        }
    }

    // CreditScreen.cpp:45,48,309 (known rows dotted through the table)
    @Test
    void knownRowsAreTranscribedVerbatim() {
        CreditScreen.CreditsTiming aFirst = CreditScreen.gCreditsTiming[0];
        assertEquals(CreditScreen.CreditWordType.WORD_AW, aFirst.mWordType);
        assertEquals(0, aFirst.mWordX);
        assertEquals(CreditScreen.CreditBrainType.BRAIN_OFF, aFirst.mBrainType);

        CreditScreen.CreditsTiming a141 = findRow(141.0f);
        assertNotNull(a141, "frame 141.0 row is present");
        assertEquals(CreditScreen.CreditWordType.WORD_AW, a141.mWordType);
        assertEquals(214, a141.mWordX);
        assertEquals(CreditScreen.CreditBrainType.BRAIN_NEXT_WORD, a141.mBrainType);

        CreditScreen.CreditsTiming aLast = CreditScreen.gCreditsTiming[CreditScreen.gCreditsTimingCount - 1];
        assertEquals(CreditScreen.CreditWordType.WORD_OFF, aLast.mWordType);
        assertEquals(0, aLast.mWordX);
        assertEquals(CreditScreen.CreditBrainType.BRAIN_OFF, aLast.mBrainType);
    }

    // CreditScreen.h:32-64 -> int constants
    @Test
    void phaseWordAndBrainEnumsKeepUpstreamOrder() {
        assertEquals(0, CreditScreen.CreditsPhase.CREDITS_MAIN1);
        assertEquals(1, CreditScreen.CreditsPhase.CREDITS_MAIN2);
        assertEquals(2, CreditScreen.CreditsPhase.CREDITS_MAIN3);
        assertEquals(3, CreditScreen.CreditsPhase.CREDITS_END);

        assertEquals(0, CreditScreen.CreditWordType.WORD_AA);
        assertEquals(1, CreditScreen.CreditWordType.WORD_EE);
        assertEquals(2, CreditScreen.CreditWordType.WORD_AW);
        assertEquals(3, CreditScreen.CreditWordType.WORD_OH);
        assertEquals(4, CreditScreen.CreditWordType.WORD_OFF);

        assertEquals(0, CreditScreen.CreditBrainType.BRAIN_FLY_ON);
        assertEquals(1, CreditScreen.CreditBrainType.BRAIN_FAST_ON);
        assertEquals(2, CreditScreen.CreditBrainType.BRAIN_NEXT_WORD);
        assertEquals(3, CreditScreen.CreditBrainType.BRAIN_FAST_OFF);
        assertEquals(4, CreditScreen.CreditBrainType.BRAIN_FLY_OFF);
        assertEquals(5, CreditScreen.CreditBrainType.BRAIN_OFF);
    }

    // CreditScreen.h:79-83 and CreditScreen.cpp:40,350-363
    @Test
    void buttonIdsAndLayoutConstants() {
        assertEquals(0, CreditScreen.Credits_Button_Replay);
        assertEquals(1, CreditScreen.Credits_Button_MainMenu);

        assertEquals(0.3f, CreditScreen.CREDIT_SCREEN_ANIM_RATE, 0.0f);

        // mMainMenuButton: (298,554,209,46)
        assertEquals(298, CreditScreen.MAIN_MENU_BUTTON_X);
        assertEquals(554, CreditScreen.MAIN_MENU_BUTTON_Y);
        assertEquals(209, CreditScreen.MAIN_MENU_BUTTON_WIDTH);
        assertEquals(46, CreditScreen.MAIN_MENU_BUTTON_HEIGHT);

        // mReplayButton: (10,530,125,65), text offsets (33,-5)
        assertEquals(10, CreditScreen.REPLAY_BUTTON_X);
        assertEquals(530, CreditScreen.REPLAY_BUTTON_Y);
        assertEquals(125, CreditScreen.REPLAY_BUTTON_WIDTH);
        assertEquals(65, CreditScreen.REPLAY_BUTTON_HEIGHT);
        assertEquals(33, CreditScreen.REPLAY_BUTTON_TEXT_OFFSET_X);
        assertEquals(-5, CreditScreen.REPLAY_BUTTON_TEXT_OFFSET_Y);
    }

    // CreditScreen.cpp:326-378
    @Test
    void constructorBuildsButtonsOverlayAndPhase() {
        LawnApp app = new LawnApp();
        // The constructor frees the effect system's reanimations and loads its resource group
        // (CreditScreen.cpp:340-343), so the app needs the services LawnApp::Init would have built.
        HeadlessTestApp.initialize(app);

        CreditScreen aScreen = new CreditScreen(app);

        assertNotNull(aScreen.mMainMenuButton);
        assertEquals(CreditScreen.Credits_Button_MainMenu, aScreen.mMainMenuButton.mId);
        assertEquals(CreditScreen.MAIN_MENU_BUTTON_X, aScreen.mMainMenuButton.mX);
        assertEquals(CreditScreen.MAIN_MENU_BUTTON_Y, aScreen.mMainMenuButton.mY);
        assertEquals(CreditScreen.MAIN_MENU_BUTTON_WIDTH, aScreen.mMainMenuButton.mWidth);
        assertEquals(CreditScreen.MAIN_MENU_BUTTON_HEIGHT, aScreen.mMainMenuButton.mHeight);
        assertFalse(aScreen.mMainMenuButton.mVisible);

        assertNotNull(aScreen.mReplayButton);
        assertEquals(CreditScreen.Credits_Button_Replay, aScreen.mReplayButton.mId);
        assertEquals(CreditScreen.REPLAY_BUTTON_X, aScreen.mReplayButton.mX);
        assertEquals(CreditScreen.REPLAY_BUTTON_Y, aScreen.mReplayButton.mY);
        assertEquals(CreditScreen.REPLAY_BUTTON_WIDTH, aScreen.mReplayButton.mWidth);
        assertEquals(CreditScreen.REPLAY_BUTTON_HEIGHT, aScreen.mReplayButton.mHeight);
        assertEquals(CreditScreen.REPLAY_BUTTON_TEXT_OFFSET_X, aScreen.mReplayButton.mTextOffsetX);
        assertEquals(CreditScreen.REPLAY_BUTTON_TEXT_OFFSET_Y, aScreen.mReplayButton.mTextOffsetY);
        assertFalse(aScreen.mReplayButton.mVisible);

        assertNotNull(aScreen.mOverlayWidget);
        assertEquals(800, aScreen.mOverlayWidget.mWidth);
        assertEquals(600, aScreen.mOverlayWidget.mHeight);

        assertEquals(CreditScreen.CreditsPhase.CREDITS_MAIN1, aScreen.mCreditsPhase);
        assertEquals(0, aScreen.mCreditsPhaseCounter);
        assertEquals(700, aScreen.mBlinkCountdown);
        assertFalse(aScreen.mPreloaded);
    }

    private static CreditScreen.CreditsTiming findRow(float theFrame) {
        for (CreditScreen.CreditsTiming aRow : CreditScreen.gCreditsTiming) {
            if (aRow.mFrame == theFrame) {
                return aRow;
            }
        }
        return null;
    }
}
