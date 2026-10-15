/*
 * Regression test for the "controls are offset" report on the level-continue dialog and the main
 * menu's save/profile manager.
 *
 * Upstream adds these controls with `AddWidget(...)` - i.e. as children of the dialog itself - and
 * `Resize` lays them out in dialog coordinates (ContinueDialog.cpp:109-110, UserDialog.cpp:99-101,
 * ChallengeScreen.cpp:628-630). The port added them to the widget manager instead, which places
 * widgets in screen coordinates, so every one of them was drawn offset by the dialog origin.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless checks that ported dialogs and screens own their controls instead of the manager. */
class DialogChildOwnershipTest {

    /** Frame and button sizes measured from the real assets, so the layout math is representative. */
    private static final Object[][] FRAME_IMAGES = {
        {"IMAGE_DIALOG_TOPLEFT", 107, 97},
        {"IMAGE_DIALOG_TOPMIDDLE", 93, 97},
        {"IMAGE_DIALOG_TOPRIGHT", 120, 97},
        {"IMAGE_DIALOG_BOTTOMLEFT", 107, 112},
        {"IMAGE_DIALOG_BOTTOMMIDDLE", 93, 114},
        {"IMAGE_DIALOG_BOTTOMRIGHT", 120, 114},
        {"IMAGE_DIALOG_BIGBOTTOMLEFT", 107, 148},
        {"IMAGE_DIALOG_BIGBOTTOMMIDDLE", 93, 150},
        {"IMAGE_DIALOG_BIGBOTTOMRIGHT", 120, 150},
        {"IMAGE_DIALOG_CENTERLEFT", 107, 54},
        {"IMAGE_DIALOG_CENTERMIDDLE", 93, 54},
        {"IMAGE_DIALOG_CENTERRIGHT", 117, 54},
        {"IMAGE_DIALOG_HEADER", 187, 64},
        {"IMAGE_BUTTON_LEFT", 36, 46},
        {"IMAGE_BUTTON_MIDDLE", 46, 46},
        {"IMAGE_BUTTON_RIGHT", 35, 46},
    };

    private final Image[] mSaved = new Image[FRAME_IMAGES.length];

    @AfterEach
    void restoreImages() {
        for (int i = 0; i < FRAME_IMAGES.length; i++) {
            Resources.setImageById(idOf((String) FRAME_IMAGES[i][0]), mSaved[i]);
        }
    }

    /** A headless app whose dialog frame images have their real sizes. */
    private LawnApp newApp() {
        LawnApp app = new LawnApp();
        HeadlessTestApp.initialize(app);
        for (int i = 0; i < FRAME_IMAGES.length; i++) {
            int aId = idOf((String) FRAME_IMAGES[i][0]);
            mSaved[i] = Resources.getImageById(aId);
            MemoryImage aStub = new MemoryImage(null);
            aStub.create((Integer) FRAME_IMAGES[i][1], (Integer) FRAME_IMAGES[i][2]);
            Resources.setImageById(aId, aStub);
        }
        return app;
    }

    private static int idOf(String theName) {
        for (Resources.ResourceEntry anEntry : Resources.ENTRIES) {
            if (anEntry.name().equals(theName)) {
                return anEntry.id();
            }
        }
        throw new AssertionError("unknown resource " + theName);
    }

    /** The control must be a child of {@code theParent}, laid out in the parent's coordinates. */
    private static void assertOwnedBy(Widget theParent, Widget theChild, String theWhat) {
        assertChildOf(theParent, theChild, theWhat);
        assertTrue(theChild.mX >= 0 && theChild.mY >= 0
                        && theChild.mX + theChild.mWidth <= theParent.mWidth
                        && theChild.mY + theChild.mHeight <= theParent.mHeight,
                theWhat + " at " + theChild.mX + "," + theChild.mY + " " + theChild.mWidth + "x"
                        + theChild.mHeight + " must stay inside the " + theParent.mWidth + "x"
                        + theParent.mHeight + " parent");
    }

    /**
     * Ownership only: the challenge screen's page tabs deliberately hang past the bottom edge
     * (ChallengeScreen.cpp:156 places them at y = 540 with a 75px button, matching
     * {@code CHALLENGE_PAGE_BUTTON_Y}/{@code _HEIGHT}), so the inside-parent check applies to the
     * dialogs only.
     */
    private static void assertChildOf(Widget theParent, Widget theChild, String theWhat) {
        assertSame(theParent, theChild.mParent, theWhat + " must be a child of the dialog/screen");
        assertFalse(theParent.mWidgetManager != null
                        && theParent.mWidgetManager.mWidgets.contains(theChild),
                theWhat + " must not be added to the widget manager - that would place it in screen "
                        + "coordinates while the layout uses dialog coordinates");
    }

    /**
     * Upstream's {@code LawnMessageBox} returns the pressed button from a blocking wait
     * (Dialog.cpp:410-441); this port's wait does not pump (sexy/Dialog.java:506), so a caller that has
     * to act on the answer passes a callback, which the dialog's button delivers.
     */
    @Test
    void lawnMessageBoxAnswersTheCallerWhenItsButtonIsDepressed() {
        LawnApp app = newApp();
        boolean[] anAnswer = new boolean[1];

        app.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "H", "L", "[DIALOG_BUTTON_YES]",
                "[DIALOG_BUTTON_NO]", Dialog.BUTTONS_YES_NO, theYes -> anAnswer[0] = theYes);
        LawnDialog aBox = (LawnDialog) app.getDialog(Dialogs.DIALOG_MESSAGE);
        assertNotNull(aBox, "the box is up");

        aBox.buttonDepress(Dialog.ID_NO);

        assertFalse(anAnswer[0], "the No answer reaches the caller");
        assertNull(app.getDialog(Dialogs.DIALOG_MESSAGE), "the box is dismissed");

        app.lawnMessageBox(Dialogs.DIALOG_MESSAGE, "H", "L", "[DIALOG_BUTTON_YES]",
                "[DIALOG_BUTTON_NO]", Dialog.BUTTONS_YES_NO, theYes -> anAnswer[0] = theYes);
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_MESSAGE)).buttonDepress(Dialog.ID_YES);

        assertTrue(anAnswer[0], "the Yes answer reaches the caller");
    }

    @Test
    void levelContinueDialogOwnsItsButtons() {
        LawnApp app = newApp();
        app.doContinueDialog();

        ContinueDialog aDialog = (ContinueDialog) app.getDialog(Dialogs.DIALOG_CONTINUE);
        assertOwnedBy(aDialog, aDialog.mContinueButton, "the continue button");
        assertOwnedBy(aDialog, aDialog.mNewGameButton, "the restart button");
        assertOwnedBy(aDialog, aDialog.mLawnYesButton, "the footer button");
    }

    @Test
    void saveManagementDialogOwnsItsListAndButtons() {
        LawnApp app = newApp();
        app.doUserDialog();

        UserDialog aDialog = (UserDialog) app.getDialog(Dialogs.DIALOG_USERDIALOG);
        assertOwnedBy(aDialog, aDialog.mUserList, "the profile list");
        assertOwnedBy(aDialog, aDialog.mDeleteButton, "the delete button");
        assertOwnedBy(aDialog, aDialog.mRenameButton, "the rename button");
    }

    @Test
    void challengeScreenOwnsItsButtons() {
        LawnApp app = newApp();
        app.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);

        ChallengeScreen aScreen = app.mChallengeScreen;
        assertChildOf(aScreen, aScreen.mBackButton, "the back button");
        for (ButtonWidget aButton : aScreen.mPageButton) {
            assertChildOf(aScreen, aButton, "a page button");
        }
        for (ButtonWidget aButton : aScreen.mChallengeButtons) {
            assertChildOf(aScreen, aButton, "a challenge button");
        }
    }
}
