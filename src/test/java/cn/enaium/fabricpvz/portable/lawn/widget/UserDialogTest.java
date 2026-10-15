/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.UserDialog.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.sexy.ListWidget;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless geometry/behaviour checks for {@link UserDialog}. */
class UserDialogTest {

    /**
     * A fresh app with the services {@code LawnApp::Init()} and the loading thread provide: the
     * profile manager, the definition tables and the stub fonts the list widget is built with
     * (UserDialog.cpp:53-60 builds the list from {@code mPlayerInfo} and the profile manager).
     */
    private static LawnApp newApp() {
        LawnApp app = new LawnApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    /**
     * The same app with no profile configured at all, which is the state that offers only the
     * "(Create a New User)" row: the shared fixture seeds one profile, so it is dropped here
     * (UserDialog.cpp:53-59 then lists nothing but the create row).
     */
    private static LawnApp newEmptyProfileApp() {
        LawnApp app = newApp();
        app.mProfileMgr.clear();
        app.mPlayerInfo = null;
        return app;
    }

    @Test
    void emptyProfileListShowsOnlyCreateUserRow() {
        LawnApp app = newEmptyProfileApp();
        UserDialog aDialog = new UserDialog(app);

        assertEquals(Dialogs.DIALOG_USERDIALOG, aDialog.mId);
        assertEquals(0, aDialog.mNumUsers);
        assertNotNull(aDialog.mUserList);
        assertEquals(ListWidget.JUSTIFY_CENTER, aDialog.mUserList.mJustify);
        assertEquals(24, aDialog.mUserList.mItemHeight);
        // No profiles -> just the "(Create a New User)" row.
        assertEquals(1, aDialog.mUserList.getLineCount());
        assertEquals("", aDialog.getSelName());
    }

    @Test
    void createUserRowOpensCreateUserDialog() {
        LawnApp app = newEmptyProfileApp();
        UserDialog aDialog = new UserDialog(app);

        // Click the row at index mNumUsers (the "(Create a New User)" entry) once.
        aDialog.listClicked(0, aDialog.mNumUsers, 1);

        assertNotNull(app.getDialog(Dialogs.DIALOG_CREATEUSER));
    }

    @Test
    void selectionOutsideUserRangeIsRejected() {
        LawnApp app = newEmptyProfileApp();
        UserDialog aDialog = new UserDialog(app);

        // mSelectIdx defaults to -1; the create-user row is outside [0, mNumUsers).
        assertTrue(aDialog.mNumUsers == 0);
        assertEquals("", aDialog.getSelName());
    }
}
