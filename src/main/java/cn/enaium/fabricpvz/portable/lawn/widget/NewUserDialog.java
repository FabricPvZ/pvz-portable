/*
 * Ported from src/Lawn/Widget/NewUserDialog.h:19-55 and src/Lawn/Widget/NewUserDialog.cpp:1-121.
 *
 * The create/rename player profile dialog. Not owned by another agent, and conceptually part of
 * LawnApp (LawnApp::DoCreateUserDialog/DoRenameUserDialog), so it is ported here.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.EditListener;
import cn.enaium.fabricpvz.portable.sexy.EditWidget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from {@code NewUserDialog} (src/Lawn/Widget/NewUserDialog.h:31).
 */
public class NewUserDialog extends LawnDialog implements EditListener {

    public LawnApp mApp; // NewUserDialog.h:44
    public EditWidget mNameEditWidget; // NewUserDialog.h:45

    // NewUserDialog::NewUserDialog (NewUserDialog.cpp:23)
    public NewUserDialog(LawnApp theApp, boolean isRename) {
        super(theApp,
                isRename ? Dialogs.DIALOG_RENAMEUSER : Dialogs.DIALOG_CREATEUSER,
                true,
                // these localization strings don't exist upstream
                isRename ? theApp.getString("RENAME_USER", "RENAME USER") : theApp.getString("NEW_USER", "NEW USER"),
                theApp.getString("PLEASE_ENTER_NAME", "Please enter your name:"),
                "[DIALOG_BUTTON_OK]",
                Dialog.BUTTONS_OK_CANCEL);

        mApp = theApp;
        mVerticalCenterText = false;
        mNameEditWidget = LawnCommon.createEditWidget(0, this, this);
        mNameEditWidget.mMaxChars = 12;
        mNameEditWidget.addWidthCheckFont(
                cn.enaium.fabricpvz.portable.lawn.Resources.getFontById(cn.enaium.fabricpvz.portable.lawn.Resources.FONT_BRIANNETOD16_ID), 220);
        calcSize(110, 40);
    }

    // NewUserDialog::AddedToManager (NewUserDialog.cpp:45)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mNameEditWidget);
        theWidgetManager.setFocus(mNameEditWidget);
    }

    // NewUserDialog::RemovedFromManager (NewUserDialog.cpp:52)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mNameEditWidget);
    }

    // NewUserDialog::GetPreferredHeight (NewUserDialog.cpp:59)
    @Override
    public int getPreferredHeight(int theWidth) {
        return super.getPreferredHeight(theWidth) + 40;
    }

    // NewUserDialog::Resize (NewUserDialog.cpp:64)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);
        mNameEditWidget.resize(mContentInsets.mLeft + 12, mHeight - 155,
                mWidth - mContentInsets.mLeft - mContentInsets.mRight - 24, 28);
    }

    // NewUserDialog::Draw (NewUserDialog.cpp:71)
    @Override
    public void draw(Graphics g) {
        super.draw(g);
        LawnCommon.drawEditBox(g, mNameEditWidget);
    }

    // NewUserDialog::EditWidgetText (NewUserDialog.cpp:77)
    @Override
    public void editWidgetText(int theId, String theString) {
        mApp.buttonDepress(mId + 2000);
    }

    // NewUserDialog::AllowChar (NewUserDialog.cpp:82)
    public boolean allowChar(int theId, char theChar) {
        return Character.isLetterOrDigit(theChar) || theChar == ' ';
    }

    // NewUserDialog::GetName (NewUserDialog.cpp:87)
    public String getName() {
        StringBuilder aString = new StringBuilder();
        char aLastChar = ' ';

        for (int i = 0; i < mNameEditWidget.mString.length(); i++) {
            char aChar = mNameEditWidget.mString.charAt(i);
            if (aChar != ' ') {
                aString.append(aChar);
            } else if (aChar != aLastChar) {
                aString.append(' ');
            }

            aLastChar = aChar;
        }

        if (aString.length() > 0 && aString.charAt(aString.length() - 1) == ' ') {
            aString.setLength(aString.length() - 1);
        }

        return aString.toString();
    }

    // NewUserDialog::SetName (NewUserDialog.cpp:113)
    public void setName(String theName) {
        mNameEditWidget.setText(theName, true);
        mNameEditWidget.mCursorPos = theName.length();
        mNameEditWidget.mHilitePos = 0;
    }
}
