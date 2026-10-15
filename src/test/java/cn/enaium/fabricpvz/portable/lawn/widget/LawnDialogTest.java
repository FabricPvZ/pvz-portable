/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.widget.LawnDialog.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;

/**
 * Headless tests for {@link LawnDialog}: the button-mode → id mapping, the {@code Dialog::ID_*}
 * result values and the button-delay/keyboard behaviour. Constructing a {@code LawnDialog} measures
 * the 9-slice chrome, so the tests are gated on the retail assets being present.
 */
class LawnDialogTest {

    private static final int UNRESOLVED_RESULT = 0x7FFFFFFF;

    private LawnApp appWithAssets() {
        LawnApp app = new LawnApp();
        Assumptions.assumeTrue(app.mResourceManager != null
                && app.getImage(Resources.IMAGE_DIALOG_TOPLEFT_ID) != null
                && app.getImage(Resources.IMAGE_BUTTON_LEFT_ID) != null,
                "no real PvZ assets available");
        return app;
    }

    @Test
    void yesNoModeMapsToDialogButtonIds() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_YES_NO);

        assertNotNull(aDialog.mLawnYesButton);
        assertNotNull(aDialog.mLawnNoButton);
        assertEquals(Dialog.ID_YES, aDialog.mLawnYesButton.mId);
        assertEquals(Dialog.ID_NO, aDialog.mLawnNoButton.mId);
        assertEquals(1000, aDialog.mLawnYesButton.mId);
        assertEquals(1001, aDialog.mLawnNoButton.mId);
    }

    @Test
    void okCancelModeUsesSameIdsWithLocalizedLabels() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_OK_CANCEL);

        assertEquals(Dialog.ID_OK, aDialog.mLawnYesButton.mId);
        assertEquals(Dialog.ID_CANCEL, aDialog.mLawnNoButton.mId);
        assertEquals(app.getString("BUTTON_OK", "Ok"), aDialog.mLawnYesButton.mLabel);
        assertEquals(app.getString("BUTTON_CANCEL", "Cancel"), aDialog.mLawnNoButton.mLabel);
    }

    @Test
    void footerModeUsesOnlyTheYesButton() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "[FOOTER]",
                Dialog.BUTTONS_FOOTER);

        assertNotNull(aDialog.mLawnYesButton);
        assertNull(aDialog.mLawnNoButton);
        assertEquals(Dialog.ID_FOOTER, aDialog.mLawnYesButton.mId);
    }

    @Test
    void noButtonsModeBuildsNeitherButton() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_NONE);

        assertNull(aDialog.mLawnYesButton);
        assertNull(aDialog.mLawnNoButton);
    }

    @Test
    void buttonDepressStoresDialogResultValues() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_YES_NO);
        assertEquals(UNRESOLVED_RESULT, aDialog.mResult, "fresh dialog has no result");

        aDialog.buttonDepress(Dialog.ID_YES);
        assertEquals(Dialog.ID_YES, aDialog.mResult);
        assertEquals(1000, aDialog.mResult);

        aDialog.buttonDepress(Dialog.ID_NO);
        assertEquals(Dialog.ID_NO, aDialog.mResult);
        assertEquals(1001, aDialog.mResult);
    }

    @Test
    void buttonDelayBlocksEarlyDepressAndDisablesButtons() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_YES_NO);

        aDialog.setButtonDelay(30);
        assertTrue(aDialog.mLawnYesButton.mDisabled);
        assertTrue(aDialog.mLawnNoButton.mDisabled);

        aDialog.buttonDepress(Dialog.ID_YES);
        assertEquals(UNRESOLVED_RESULT, aDialog.mResult,
                "ButtonDepress is ignored while mUpdateCnt <= mButtonDelay");

        // a negative delay (the ctor default) allows depress immediately
        aDialog.mButtonDelay = -1;
        aDialog.buttonDepress(Dialog.ID_YES);
        assertEquals(Dialog.ID_YES, aDialog.mResult);
    }

    @Test
    void keyboardMapsSpaceReturnAndEscape() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_YES_NO);

        aDialog.keyDown(KeyCode.KEYCODE_RETURN);
        assertEquals(Dialog.ID_YES, aDialog.mResult);

        aDialog.keyDown(KeyCode.KEYCODE_ESCAPE);
        assertEquals(Dialog.ID_NO, aDialog.mResult, "ESC chooses No when a No button exists");
    }

    @Test
    void layoutAccessorsMatchInsets() {
        LawnApp app = appWithAssets();
        LawnDialog aDialog = new LawnDialog(app, Dialogs.DIALOG_INFO, true, "H", "L", "",
                Dialog.BUTTONS_YES_NO);

        assertEquals(aDialog.mContentInsets.mLeft + aDialog.mBackgroundInsets.mLeft,
                aDialog.getLeft());
        assertEquals(aDialog.mWidth - aDialog.mContentInsets.mLeft - aDialog.mContentInsets.mRight
                - aDialog.mBackgroundInsets.mLeft - aDialog.mBackgroundInsets.mRight,
                aDialog.getWidth());
        assertEquals(36 + 0, aDialog.getLeft());
        assertEquals(35 + 0 + 99, aDialog.getTop());
    }
}
