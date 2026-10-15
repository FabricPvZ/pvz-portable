/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.ContinueDialog.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless geometry/behaviour checks for {@link ContinueDialog}. */
class ContinueDialogTest {

    /**
     * A fresh app with the services {@code LawnApp::Init()} and the loading thread provide: the
     * effect system, music/foley, the definition tables and the stub images/fonts the dialog and its
     * buttons measure themselves with (ContinueDialog.cpp:64-68 reads button/dialog images).
     */
    private static LawnApp newApp() {
        LawnApp app = new LawnApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    @Test
    void constructorBuildsContinueAndNewGameButtons() {
        LawnApp app = newApp();
        ContinueDialog aDialog = new ContinueDialog(app);

        assertEquals(Dialogs.DIALOG_CONTINUE, aDialog.mId);
        assertNotNull(aDialog.mContinueButton);
        assertNotNull(aDialog.mNewGameButton);
        assertEquals(ContinueDialog.ContinueDialog_Continue, aDialog.mContinueButton.mId);
        assertEquals(ContinueDialog.ContinueDialog_NewGame, aDialog.mNewGameButton.mId);
    }

    @Test
    void dialogIsWideEnoughForBothButtons() {
        LawnApp app = newApp();
        ContinueDialog aDialog = new ContinueDialog(app);

        int aBtnWidth = app.getImage(Resources.IMAGE_BUTTON_LEFT_ID).mWidth
                + app.getImage(Resources.IMAGE_BUTTON_MIDDLE_ID).mWidth * 3
                + app.getImage(Resources.IMAGE_BUTTON_RIGHT_ID).mWidth;
        // Two buttons plus the interior padding must fit (ContinueDialog.cpp:69-74).
        assertTrue(aDialog.mWidth >= 2 * aBtnWidth - 40);
    }

    @Test
    void newGameButtonOpensRestartConfirmDialog() {
        LawnApp app = newApp();
        ContinueDialog aDialog = new ContinueDialog(app);

        aDialog.buttonDepress(ContinueDialog.ContinueDialog_NewGame);

        assertNotNull(app.getDialog(Dialogs.DIALOG_RESTARTCONFIRM));
    }
}
