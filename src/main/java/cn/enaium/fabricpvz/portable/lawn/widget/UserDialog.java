/*
 * Ported from src/Lawn/Widget/UserDialog.h:28-68 and UserDialog.cpp:30-196.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import java.util.Map;

import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.DialogButton;
import cn.enaium.fabricpvz.portable.sexy.EditListener;
import cn.enaium.fabricpvz.portable.sexy.ListListener;
import cn.enaium.fabricpvz.portable.sexy.ListWidget;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Widget/UserDialog.h:28-68 and UserDialog.cpp:30-196.
 *
 * <p>Profile chooser: lists the local players (plus a "(Create a New User)" row) and offers Rename /
 * Delete for the highlighted profile. Double-clicking a row accepts it.
 */
public class UserDialog extends LawnDialog implements ListListener, EditListener {

    // UserDialog.h:31-35 (enum)
    public static final int UserDialog_RenameUser = 0;
    public static final int UserDialog_DeleteUser = 1;

    public ListWidget mUserList;
    public DialogButton mRenameButton;
    public DialogButton mDeleteButton;
    public int mNumUsers;

    // UserDialog::UserDialog (UserDialog.cpp:41-81)
    public UserDialog(LawnApp theApp) {
        super(theApp, Dialogs.DIALOG_USERDIALOG, true, theApp.getString("WHO_ARE_YOU", "WHO ARE YOU?"),
                "", "", Dialog.BUTTONS_OK_CANCEL);

        mVerticalCenterText = false;
        mUserList = new ListWidget(0, theApp.getFont(Resources.FONT_BRIANNETOD16_ID), this);
        mUserList.setColors(gUserListWidgetColors());
        mUserList.mDrawOutline = true;
        mUserList.mJustify = ListWidget.JUSTIFY_CENTER;
        mUserList.mItemHeight = 24;

        mRenameButton = GameButton.makeButton(UserDialog_RenameUser, this, mApp.getString("RENAME_BUTTON", "Rename"));
        mDeleteButton = GameButton.makeButton(UserDialog_DeleteUser, this, mApp.getString("DELETE_BUTTON", "Delete"));

        mNumUsers = 0;
        if (theApp.mPlayerInfo != null) {
            mUserList.setSelect(mUserList.addLine(theApp.mPlayerInfo.mName, false));
            mNumUsers++;
        }

        for (Map.Entry<String, PlayerInfo> anEntry : theApp.mProfileMgr.getProfileMap().entrySet()) {
            if (theApp.mPlayerInfo != null && anEntry.getValue().mName.equals(theApp.mPlayerInfo.mName)) {
                continue;
            }

            mUserList.addLine(anEntry.getValue().mName, false);
            mNumUsers++;
        }

        if (mNumUsers < 8) {
            mUserList.addLine(mApp.getString("CREATE_NEW_USER", "(Create a New User)"), false);
        }

        mTallBottom = true;
        calcSize(210, 270);
    }

    /** Ported from the gUserListWidgetColors initialiser (UserDialog.cpp:31-38). */
    private static ListWidget.ListWidgetColorScheme gUserListWidgetColors() {
        ListWidget.ListWidgetColorScheme aColors = new ListWidget.ListWidgetColorScheme();
        aColors.mBkg = new Color(23, 24, 35);
        aColors.mOutline = new Color(0, 0, 0);
        aColors.mText = new Color(235, 225, 180);
        aColors.mHilite = new Color(255, 255, 255);
        aColors.mSelect = new Color(20, 180, 15);
        aColors.mSelectText = null;
        return aColors;
    }

    // UserDialog::Resize (UserDialog.cpp:83-89)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);
        mUserList.resize(getLeft() + 30, getTop() + 4, getWidth() - 60, 200);
        mRenameButton.layout(Widget.LAY_SameLeft | Widget.LAY_Above | Widget.LAY_SameHeight | Widget.LAY_SameWidth,
                mLawnYesButton, 0, 0, 0, 0);
        mDeleteButton.layout(Widget.LAY_SameLeft | Widget.LAY_Above | Widget.LAY_SameHeight | Widget.LAY_SameWidth,
                mLawnNoButton, 0, 0, 0, 0);
    }

    // UserDialog::GetPreferredHeight (UserDialog.cpp:91-94)
    @Override
    public int getPreferredHeight(int theWidth) {
        return super.getPreferredHeight(theWidth) + 190;
    }

    // UserDialog::AddedToManager (UserDialog.cpp:96-103)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        // Upstream adds these to the dialog itself (UserDialog.cpp:99-101: AddWidget); Resize lays them
        // out in dialog coordinates, so they must not be manager-level (screen coordinates).
        addWidget(mUserList);
        addWidget(mDeleteButton);
        addWidget(mRenameButton);
    }

    // UserDialog::RemovedFromManager (UserDialog.cpp:105-112)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mUserList);
        removeWidget(mDeleteButton);
        removeWidget(mRenameButton);
    }

    // UserDialog::GetSelName (UserDialog.cpp:112-119)
    public String getSelName() {
        if (mUserList.mSelectIdx < 0 || mUserList.mSelectIdx >= mNumUsers) {
            return "";
        }
        return mUserList.getStringAt(mUserList.mSelectIdx);
    }

    // UserDialog::FinishDeleteUser (UserDialog.cpp:121-143)
    public void finishDeleteUser() {
        int aSelIdx = mUserList.mSelectIdx;
        mUserList.removeLine(mUserList.mSelectIdx);

        aSelIdx--;
        aSelIdx = Math.max(aSelIdx, 0);
        if (mUserList.getLineCount() > 0) {
            mUserList.setSelect(aSelIdx);
        }

        mNumUsers--;
        if (mNumUsers == 7) {
            mUserList.addLine(mApp.getString("CREATE_NEW_USER", "(Create a New User)"), false);
        }
    }

    // UserDialog::FinishRenameUser (UserDialog.cpp:145-151)
    public void finishRenameUser(String theNewName) {
        if (mUserList.mSelectIdx < mNumUsers) {
            mUserList.setLine(mUserList.mSelectIdx, theNewName);
        }
    }

    // UserDialog::Draw (UserDialog.cpp:153-156)
    @Override
    public void draw(Graphics g) {
        super.draw(g);
    }

    // UserDialog::ListClicked (UserDialog.cpp:158-167)
    @Override
    public void listClicked(int theId, int theIdx, int theClickCount) {
        if (theIdx == mNumUsers) {
            mApp.doCreateUserDialog();
        } else {
            mUserList.setSelect(theIdx);
            if (theClickCount == 2) {
                mApp.finishUserDialog(true);
            }
        }
    }

    @Override
    public void listClosed(int theId) {
    }

    @Override
    public void listHiliteChanged(int theId, int theCurIdx, int theNewIdx) {
    }

    // UserDialog::ButtonDepress (UserDialog.cpp:169-186)
    @Override
    public void buttonDepress(int theId) {
        super.buttonDepress(theId);
        String aSelName = getSelName();
        if (!aSelName.isEmpty()) {
            switch (theId) {
            case UserDialog_RenameUser:
                mApp.doRenameUserDialog(aSelName);
                break;

            case UserDialog_DeleteUser:
                mApp.doConfirmDeleteUserDialog(aSelName);
                break;

            default:
                break;
            }
        }
    }

    // UserDialog::EditWidgetText (UserDialog.cpp:188-192)
    @Override
    public void editWidgetText(int theId, String theString) {
        mApp.buttonDepress(mId + 2000);
    }

    // UserDialog::AllowChar (UserDialog.cpp:194-196)
    public boolean allowChar(int theId, char theChar) {
        return Character.isDigit(theChar);
    }
}
