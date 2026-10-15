/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.CheatDialog (CheatDialog.h:27-45 / CheatDialog.cpp:33-151).
 *
 * Headless and asset-free: every app is initialised with {@link HeadlessTestApp}, and the one
 * filesystem-touching seam of ApplyCheat (WriteCurrentUserConfig -> PlayerInfo::SaveDetails) is
 * short-circuited by the {@link TestApp} double so the parsing assertions are deterministic.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless behaviour/parse checks for {@link CheatDialog}. */
class CheatDialogTest {

    /** {@link LawnApp} double that records {@code WriteCurrentUserConfig} instead of hitting disk. */
    private static final class TestApp extends LawnApp {
        boolean mWroteConfig;

        @Override
        public boolean writeCurrentUserConfig() {
            mWroteConfig = true;
            return true;
        }
    }

    private static TestApp newApp() {
        TestApp app = new TestApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    private static CheatDialog newDialog(TestApp theApp, String theText) {
        CheatDialog aDialog = new CheatDialog(theApp);
        aDialog.mLevelEditWidget.setText(theText, true);
        return aDialog;
    }

    @Test
    void constructorUsesCheatDialogIdAndDisablesVerticalCentering() {
        TestApp app = newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mPlayerInfo.setLevel(1);

        CheatDialog aDialog = new CheatDialog(app);

        assertEquals(Dialogs.DIALOG_CHEAT, aDialog.mId);
        assertFalse(aDialog.mVerticalCenterText);
        assertNotNull(aDialog.mLevelEditWidget);
        assertEquals(12, aDialog.mLevelEditWidget.mMaxChars); // CheatDialog.cpp:37
    }

    @Test
    void seedsAdventureStageStringWhenNotFinished() {
        TestApp app = newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mPlayerInfo.mFinishedAdventure = 0;
        app.mPlayerInfo.setLevel(7);

        CheatDialog aDialog = new CheatDialog(app);

        assertEquals(LawnApp.getStageString(7), aDialog.mLevelEditWidget.mString); // CheatDialog.cpp:49
    }

    @Test
    void seedsFinishedAdventureWithFPrefix() {
        TestApp app = newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mPlayerInfo.mFinishedAdventure = 1;
        app.mPlayerInfo.setLevel(7);

        CheatDialog aDialog = new CheatDialog(app);

        assertEquals("F" + LawnApp.getStageString(7), aDialog.mLevelEditWidget.mString); // CheatDialog.cpp:45
    }

    @Test
    void seedsChallengeModesWithCPrefix() {
        TestApp app = newApp();
        app.mGameMode = 23;

        CheatDialog aDialog = new CheatDialog(app);

        assertEquals("C23", aDialog.mLevelEditWidget.mString); // CheatDialog.cpp:41
    }

    @Test
    void allowCharAcceptsOnlyCheatCharacters() {
        TestApp app = newApp();
        CheatDialog aDialog = new CheatDialog(app);

        for (char aChar : new char[] {'0', '9', '-', 'c', 'C', 'f', 'F'}) {
            assertTrue(aDialog.allowChar(0, aChar), "expected '" + aChar + "' to be allowed");
        }
        for (char aChar : new char[] {'x', ' ', '+', '.', 'z'}) {
            assertFalse(aDialog.allowChar(0, aChar), "expected '" + aChar + "' to be rejected");
        }
    }

    @Test
    void applyCheatCPrefixSetsClampedChallengeMode() {
        TestApp app = newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;

        CheatDialog aDialog = newDialog(app, "C7");
        assertTrue(aDialog.applyCheat());

        assertEquals(7, app.mGameMode); // CheatDialog.cpp:105-107
    }

    @Test
    void applyCheatLowercaseCPrefixAlsoAccepted() {
        TestApp app = newApp();
        CheatDialog aDialog = newDialog(app, "c12");
        assertTrue(aDialog.applyCheat());
        assertEquals(12, app.mGameMode);
    }

    @Test
    void applyCheatClampsChallengeIndexToNumChallengeModes() {
        TestApp app = newApp();
        CheatDialog aDialog = newDialog(app, "c9999");
        assertTrue(aDialog.applyCheat());
        assertEquals(ChallengeScreen.NUM_CHALLENGE_MODES, app.mGameMode); // std::clamp(x, 0, NUM_CHALLENGE_MODES)
    }

    @Test
    void applyCheatClampsNegativeChallengeIndexToZero() {
        TestApp app = newApp();
        app.mGameMode = 40;
        CheatDialog aDialog = newDialog(app, "c-5");
        assertTrue(aDialog.applyCheat());
        assertEquals(0, app.mGameMode);
    }

    @Test
    void applyCheatBareNumberSetsAdventureLevel() {
        TestApp app = newApp();
        app.mPlayerInfo.mFinishedAdventure = 1;
        CheatDialog aDialog = newDialog(app, "5");

        assertTrue(aDialog.applyCheat());

        assertEquals(GameMode.GAMEMODE_ADVENTURE, app.mGameMode);
        assertEquals(5, app.mPlayerInfo.getLevel()); // CheatDialog.cpp:128-140
        assertEquals(0, app.mPlayerInfo.mFinishedAdventure);
        assertTrue(app.mWroteConfig);
    }

    @Test
    void applyCheatAreaSubareaComputesLevel() {
        TestApp app = newApp();
        CheatDialog aDialog = newDialog(app, "2-3");
        assertTrue(aDialog.applyCheat());
        assertEquals((2 - 1) * 10 + 3, app.mPlayerInfo.getLevel()); // CheatDialog.cpp:122-126
        assertEquals(0, app.mPlayerInfo.mFinishedAdventure);
    }

    @Test
    void applyCheatFAreaSubareaSetsFinishedAdventure() {
        TestApp app = newApp();
        CheatDialog aDialog = newDialog(app, "F2-3");
        assertTrue(aDialog.applyCheat());
        assertEquals((2 - 1) * 10 + 3, app.mPlayerInfo.getLevel()); // CheatDialog.cpp:111-116
        assertEquals(1, app.mPlayerInfo.mFinishedAdventure);
    }

    @Test
    void applyCheatFSingleSetsFinishedAdventure() {
        TestApp app = newApp();
        CheatDialog aDialog = newDialog(app, "f7");
        assertTrue(aDialog.applyCheat());
        assertEquals(7, app.mPlayerInfo.getLevel()); // CheatDialog.cpp:117-121
        assertEquals(1, app.mPlayerInfo.mFinishedAdventure);
    }

    @Test
    void applyCheatRejectsGarbageAndShowsErrorDialog() {
        TestApp app = newApp();
        app.mPlayerInfo.setLevel(9);
        CheatDialog aDialog = newDialog(app, "abc");

        assertFalse(aDialog.applyCheat());

        assertEquals(9, app.mPlayerInfo.getLevel());
        assertNotNull(app.getDialog(Dialogs.DIALOG_CHEATERROR)); // CheatDialog.cpp:134-142
    }

    @Test
    void applyCheatRejectsNonPositiveLevel() {
        TestApp app = newApp();

        CheatDialog aDialog = newDialog(app, "-3");
        assertFalse(aDialog.applyCheat());
        assertNotNull(app.getDialog(Dialogs.DIALOG_CHEATERROR));
    }

    @Test
    void applyCheatZeroIsInvalid() {
        TestApp app = newApp();
        CheatDialog aDialog = newDialog(app, "0");
        assertFalse(aDialog.applyCheat());
    }

    @Test
    void resizePositionsEditWidgetAtUpstreamLiterals() {
        TestApp app = newApp();
        CheatDialog aDialog = new CheatDialog(app);

        assertEquals(aDialog.mContentInsets.mLeft + 12, aDialog.mLevelEditWidget.mX); // CheatDialog.cpp:63
        assertEquals(aDialog.mHeight - 155, aDialog.mLevelEditWidget.mY);
        assertEquals(aDialog.mWidth - aDialog.mContentInsets.mLeft - aDialog.mContentInsets.mRight - 24,
                aDialog.mLevelEditWidget.mWidth);
        assertEquals(28, aDialog.mLevelEditWidget.mHeight);
    }

    @Test
    void addedAndRemovedFromManagerTogglesEditWidget() {
        TestApp app = newApp();
        CheatDialog aDialog = new CheatDialog(app);

        app.addDialog(Dialogs.DIALOG_CHEAT, aDialog);
        assertTrue(aDialog.mWidgets.contains(aDialog.mLevelEditWidget));

        app.killDialog(Dialogs.DIALOG_CHEAT);
        assertFalse(aDialog.mWidgets.contains(aDialog.mLevelEditWidget));
        assertNull(app.getDialog(Dialogs.DIALOG_CHEAT));
    }
}
