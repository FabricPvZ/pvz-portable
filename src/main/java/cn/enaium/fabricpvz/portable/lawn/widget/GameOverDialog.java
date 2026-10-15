/*
 * Ported from src/Lawn/Widget/LawnDialog.h:89-102 and src/Lawn/Widget/LawnDialog.cpp:443-544.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.DialogButton;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;

/**
 * Ported from src/Lawn/Widget/LawnDialog.h:89-102 (implementation
 * src/Lawn/Widget/LawnDialog.cpp:443-544).
 *
 * <p>The "game over" dialog: a {@link LawnDialog} with a footer "Try Again" button (dialog-result
 * {@link Dialog#ID_FOOTER}) plus an extra top-right "Main Menu" button with id {@code 1}.
 */
public class GameOverDialog extends LawnDialog {

    // LawnDialog.h:91
    public DialogButton mMenuButton;

    // GameOverDialog::GameOverDialog (LawnDialog.cpp:443)
    public GameOverDialog(String theMessage, boolean theShowChallengeName) {
        super(LawnApp.gLawnApp, Dialogs.DIALOG_GAME_OVER, true, "[GAME_OVER]", theMessage, "",
                Dialog.BUTTONS_FOOTER);
        mMenuButton = null;
        mLawnYesButton.setLabel("[TRY_AGAIN]");
        if (theShowChallengeName) {
            mDialogHeader = PvzpStringFile.translate(mApp.getCurrentChallengeDef().mChallengeName);
        }

        if (theMessage.length() == 0) {
            mContentInsets.mTop += 15;
        }
        calcSize(0, 0);
        LawnApp.centerDialog(this, mWidth, mHeight);
        mClip = false;

        mMenuButton = GameButton.makeButton(1, this, "[MAIN_MENU_BUTTON]");
        mMenuButton.resize(635 - mX, -10 - mY, 163, 46);

        mApp.mBoard.mShowShovel = false;
        mApp.mBoard.mMenuButton.mBtnNoDraw = true;
    }

    // GameOverDialog::KeyDown (LawnDialog.cpp:472)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(1);
            return;
        }

        super.keyDown(theKey);
    }

    // GameOverDialog::ButtonDepress (LawnDialog.cpp:483)
    @Override
    public void buttonDepress(int theId) {
        if (theId == 1) {
            mApp.killDialog(Dialogs.DIALOG_GAME_OVER);
            mApp.killBoard();
            if (mApp.isSurvivalMode()) {
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
            } else if (mApp.isPuzzleMode()) {
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
            } else if (mApp.isAdventureMode()) {
                mApp.showGameSelector();
            } else {
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            }
        } else if (theId == Dialog.ID_FOOTER) {
            mApp.killDialog(Dialogs.DIALOG_GAME_OVER);
            mApp.endLevel();
        }
    }

    // GameOverDialog::AddedToManager (LawnDialog.cpp:517)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        if (mMenuButton != null) {
            addWidget(mMenuButton);
        }
    }

    // GameOverDialog::RemovedFromManager (LawnDialog.cpp:526)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        if (mMenuButton != null) {
            removeWidget(mMenuButton);
        }
    }

    // GameOverDialog::MouseDrag (LawnDialog.cpp:536)
    @Override
    public void mouseDrag(int x, int y) {
        super.mouseDrag(x, y);
        if (mMenuButton != null) {
            mMenuButton.resize(635 - mX, -10 - mY, 163, 46);
        }
    }
}
