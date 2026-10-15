/*
 * Tests for the LawnApp cheat/zen entry points wired in Z5:
 *   LawnApp::DoCheatDialog / FinishCheatDialog (LawnApp.cpp:1003-1024) and
 *   LawnApp::FinishZenGardenToturial (LawnApp.cpp:3102-3107).
 *
 * Headless: the two methods that would otherwise restart a level call {@code PreNewGame}, so the
 * {@link TestApp} double records that call (and {@code WriteCurrentUserConfig}) instead of building
 * a Board; everything else runs for real.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.widget.CheatDialog;
import cn.enaium.fabricpvz.portable.lawn.widget.ZombatarTOS;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless flow checks for {@code LawnApp}'s cheat-dialog and zen-tutorial entry points. */
class LawnAppCheatTest {

    /** Records {@code PreNewGame}/{@code WriteCurrentUserConfig} so no level is actually built. */
    private static final class TestApp extends LawnApp {
        int mPreNewGameCount;
        int mPreNewGameMode = Integer.MIN_VALUE;
        boolean mPreNewGameLookForSaved = true;
        boolean mWroteConfig;

        @Override
        public void preNewGame(int theGameMode, boolean theLookForSavedGame) {
            mPreNewGameCount++;
            mPreNewGameMode = theGameMode;
            mPreNewGameLookForSaved = theLookForSavedGame;
        }

        @Override
        public boolean writeCurrentUserConfig() {
            mWroteConfig = true;
            return true;
        }
    }

    private static TestApp newApp() {
        TestApp app = new TestApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        HeadlessTestApp.initialize(app);
        app.mPlayerInfo.setLevel(1);
        app.mPlayerInfo.mFinishedAdventure = 0;
        return app;
    }

    private static CheatDialog dialog(TestApp theApp) {
        return (CheatDialog) theApp.getDialog(Dialogs.DIALOG_CHEAT);
    }

    @Test
    void doCheatDialogAddsCheatDialog() {
        TestApp app = newApp();

        app.doCheatDialog();

        CheatDialog aDialog = dialog(app);
        assertNotNull(aDialog); // LawnApp.cpp:1003-1009
        assertEquals(Dialogs.DIALOG_CHEAT, aDialog.mId);
    }

    @Test
    void doCheatDialogReplacesExistingDialog() {
        TestApp app = newApp();

        app.doCheatDialog();
        CheatDialog aFirst = dialog(app);
        app.doCheatDialog();

        assertNotSame(aFirst, dialog(app)); // upstream KillDialog first (LawnApp.cpp:1005)
    }

    @Test
    void finishCheatDialogCancelJustCloses() {
        TestApp app = newApp();
        app.doCheatDialog();

        app.finishCheatDialog(false);

        assertNull(app.getDialog(Dialogs.DIALOG_CHEAT)); // LawnApp.cpp:1020
        assertEquals(0, app.mPreNewGameCount);
    }

    @Test
    void finishCheatDialogOkAppliesCheatAndRestarts() {
        TestApp app = newApp();
        app.doCheatDialog();
        dialog(app).mLevelEditWidget.setText("3", true);

        app.finishCheatDialog(true);

        assertNull(app.getDialog(Dialogs.DIALOG_CHEAT));
        assertEquals(3, app.mPlayerInfo.getLevel()); // CheatDialog::ApplyCheat
        assertEquals(GameMode.GAMEMODE_ADVENTURE, app.mGameMode);
        assertEquals(BoardResult.BOARDRESULT_CHEAT, app.mBoardResult); // LawnApp.cpp:1022
        assertEquals(1, app.mPreNewGameCount); // LawnApp.cpp:1023
        assertEquals(GameMode.GAMEMODE_ADVENTURE, app.mPreNewGameMode);
        assertFalse(app.mPreNewGameLookForSaved);
    }

    @Test
    void finishCheatDialogKeepsDialogWhenCheatInvalid() {
        TestApp app = newApp();
        app.doCheatDialog();
        dialog(app).mLevelEditWidget.setText("abc", true);

        app.finishCheatDialog(true);

        assertNotNull(app.getDialog(Dialogs.DIALOG_CHEAT)); // ApplyCheat returned false -> early return
        assertNotNull(app.getDialog(Dialogs.DIALOG_CHEATERROR));
        assertEquals(0, app.mPreNewGameCount);
    }

    @Test
    void finishCheatDialogWithoutDialogIsNoop() {
        TestApp app = newApp();

        app.finishCheatDialog(true);

        assertEquals(0, app.mPreNewGameCount);
    }

    @Test
    void finishZenGardenToturialWinsAndReturnsToAdventure() {
        TestApp app = newApp();

        app.finishZenGardenToturial();

        assertEquals(BoardResult.BOARDRESULT_WON, app.mBoardResult); // LawnApp.cpp:3104
        assertEquals(1, app.mPreNewGameCount);
        assertEquals(GameMode.GAMEMODE_ADVENTURE, app.mPreNewGameMode); // LawnApp.cpp:3106
        assertFalse(app.mPreNewGameLookForSaved);
    }

    @Test
    void showZombatarTOSAddsTermsDialog() {
        TestApp app = newApp();

        app.showZombatarTOS();

        // LawnApp.cpp:620-626: ZombatarTOS on DIALOG_ZOMBATAR_TOS, centred and focused.
        assertInstanceOf(ZombatarTOS.class, app.getDialog(Dialogs.DIALOG_ZOMBATAR_TOS));
    }
}
