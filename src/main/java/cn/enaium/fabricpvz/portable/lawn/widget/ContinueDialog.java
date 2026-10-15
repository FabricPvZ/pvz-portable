/*
 * Ported from src/Lawn/Widget/ContinueDialog.h:24-54 and ContinueDialog.cpp:31-215.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.BoardResult;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChallengePage;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.DialogButton;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/ContinueDialog.h:24-46 and ContinueDialog.cpp:31-215.
 *
 * <p>The "CONTINUE GAME?" dialog shown when a save exists: continue the current game or start over
 * (restart the level in adventure mode, new game otherwise).
 */
public class ContinueDialog extends LawnDialog {

    // ContinueDialog.h:28 (enum)
    public static final int ContinueDialog_Continue = 0;
    public static final int ContinueDialog_NewGame = 1;

    public DialogButton mContinueButton;
    public DialogButton mNewGameButton;

    // ContinueDialog::ContinueDialog (ContinueDialog.cpp:31-88)
    public ContinueDialog(LawnApp theApp) {
        super(theApp, Dialogs.DIALOG_CONTINUE, true,
                theApp.getString("CONTINUE_GAME_HEADER", "CONTINUE GAME?"), "",
                "[DIALOG_BUTTON_CANCEL]", Dialog.BUTTONS_FOOTER);

        if (theApp.isAdventureMode()) {
            mDialogLines = mApp.getString("CONTINUE_GAME_OR_RESTART",
                    "Do you want to continue your current game or restart the level?");
            mContinueButton = GameButton.makeButton(ContinueDialog_Continue, this, "[CONTINUE_BUTTON]");
            mNewGameButton = GameButton.makeButton(ContinueDialog_NewGame, this, "[RESTART_LEVEL]");
        } else {
            mDialogLines = mApp.getString("CONTINUE_GAME",
                    "Do you want to continue your current game or start a new game?");
            mContinueButton = GameButton.makeButton(ContinueDialog_Continue, this, "[CONTINUE_BUTTON]");
            mNewGameButton = GameButton.makeButton(ContinueDialog_NewGame, this, "[NEW_GAME_BUTTON]");
        }

        mTallBottom = true;
        calcSize(10, 60);

        Image aBtnLeft = mApp.getImage(Resources.IMAGE_BUTTON_LEFT_ID);
        Image aBtnMiddle = mApp.getImage(Resources.IMAGE_BUTTON_MIDDLE_ID);
        Image aBtnRight = mApp.getImage(Resources.IMAGE_BUTTON_RIGHT_ID);
        int aBtnLeftWidth = aBtnLeft.mWidth;
        int aBtnMid = aBtnMiddle.mWidth;
        int aBtnRightWidth = aBtnRight.mWidth;
        int aBtnWidth = aBtnLeftWidth + aBtnMid * 3 + aBtnRightWidth;
        int aInsetH = mContentInsets.mLeft + mContentInsets.mRight
                + mBackgroundInsets.mLeft + mBackgroundInsets.mRight;
        // Min width that keeps the Continue and New Game buttons from overlapping.
        int aMinCancelWidth = 2 * aBtnWidth - 40;
        int aSteps = (aMinCancelWidth - aBtnLeftWidth - aBtnRightWidth + aBtnMid - 1) / aBtnMid;
        int aMinWidth = aBtnLeftWidth + aBtnRightWidth + aSteps * aBtnMid + aInsetH - 8;
        if (mWidth < aMinWidth) {
            int aTopMidWidth = mApp.getImage(Resources.IMAGE_DIALOG_TOPMIDDLE_ID).mWidth;
            int aImageWidth = mApp.getImage(Resources.IMAGE_DIALOG_TOPLEFT_ID).mWidth
                    + mApp.getImage(Resources.IMAGE_DIALOG_TOPRIGHT_ID).mWidth + aTopMidWidth;
            int aWidth = aMinWidth;
            if (aWidth <= aImageWidth) {
                aWidth = aImageWidth;
            } else if (aTopMidWidth > 0) {
                int anExtraWidth = (aWidth - aImageWidth) % aTopMidWidth;
                if (anExtraWidth != 0) {
                    aWidth += aTopMidWidth - anExtraWidth;
                }
            }
            resize(mX, mY, aWidth, mHeight);
        }
    }

    // ContinueDialog::GetPreferredHeight (ContinueDialog.cpp:90-93)
    @Override
    public int getPreferredHeight(int theWidth) {
        return super.getPreferredHeight(theWidth) + 40;
    }

    // ContinueDialog::Resize (ContinueDialog.cpp:95-104)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        int aBtnWidth = mApp.getImage(Resources.IMAGE_BUTTON_LEFT_ID).mWidth
                + mApp.getImage(Resources.IMAGE_BUTTON_MIDDLE_ID).mWidth * 3
                + mApp.getImage(Resources.IMAGE_BUTTON_RIGHT_ID).mWidth;
        int aBtnHeight = mLawnYesButton.mHeight;

        mContinueButton.resize(mLawnYesButton.mX - 20, mLawnYesButton.mY - aBtnHeight, aBtnWidth, aBtnHeight);
        mNewGameButton.resize(mLawnYesButton.mX + mLawnYesButton.mWidth - aBtnWidth + 20, mContinueButton.mY,
                aBtnWidth, aBtnHeight);
    }

    // ContinueDialog::AddedToManager (ContinueDialog.cpp:106-111)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        // Upstream adds these as children of the dialog (ContinueDialog.cpp:109-110: AddWidget), NOT to
        // the widget manager: Resize positions them in dialog coordinates, so a manager-level widget
        // (which is placed in screen coordinates) lands offset by the dialog origin.
        addWidget(mContinueButton);
        addWidget(mNewGameButton);
    }

    // ContinueDialog::RemovedFromManager (ContinueDialog.cpp:113-118)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mContinueButton);
        removeWidget(mNewGameButton);
    }

    // ContinueDialog::KeyDown (ContinueDialog.cpp:120-135)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(Dialog.ID_FOOTER);
            return;
        }

        if (theKey == KeyCode.KEYCODE_RETURN || theKey == KeyCode.KEYCODE_SPACE) {
            buttonDepress(ContinueDialog_Continue);
            return;
        }

        super.keyDown(theKey);
    }

    // ContinueDialog::RestartLoopingSounds (ContinueDialog.cpp:137-153)
    public void restartLoopingSounds() {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS || mApp.isStormyNightLevel()) {
            mApp.playFoley(FoleyType.FOLEY_RAIN);
        }

        Board aBoard = mApp.mBoard;
        if (aBoard == null) {
            return;
        }
        for (Zombie aZombie : aBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mPlayingSong) {
                aZombie.startZombieSound();
            }
        }
    }

    // ContinueDialog::ButtonDepress (ContinueDialog.cpp:155-215)
    @Override
    public void buttonDepress(int theId) {
        if (theId == ContinueDialog_Continue) {
            restartLoopingSounds();
            mApp.killDialog(mId);
        } else if (theId == ContinueDialog_NewGame) {
            if (mApp.isAdventureMode()) {
                LawnDialog aDialog = (LawnDialog) mApp.doDialog(
                        Dialogs.DIALOG_RESTARTCONFIRM,
                        true,
                        "[RESTART_LEVEL_HEADER]",
                        "[RESTART_LEVEL_BODY]",
                        "",
                        Dialog.BUTTONS_OK_CANCEL);
                aDialog.mLawnYesButton.mLabel = PvzpStringFile.translate("[RESTART_BUTTON]");
            } else {
                LawnDialog aDialog = (LawnDialog) mApp.doDialog(
                        Dialogs.DIALOG_RESTARTCONFIRM,
                        true,
                        mApp.getString("NEW_GAME_HEADER", "New Game?"),
                        mApp.getString("NEW_GAME", "Are you sure that you want to start a new game?"),
                        "",
                        Dialog.BUTTONS_OK_CANCEL);
                aDialog.mLawnYesButton.mLabel = PvzpStringFile.translate("[NEW_GAME_BUTTON]");
            }
        } else {
            mApp.killDialog(mId);
            mApp.mBoardResult = BoardResult.BOARDRESULT_QUIT;
            if (mApp.isAdventureMode()) {
                mApp.showGameSelector();
            } else if (mApp.isSurvivalMode()) {
                mApp.killBoard();
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_SURVIVAL);
            } else if (mApp.isPuzzleMode()) {
                mApp.killBoard();
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_PUZZLE);
            } else {
                mApp.killBoard();
                mApp.showChallengeScreen(ChallengePage.CHALLENGE_PAGE_CHALLENGE);
            }
        }
    }
}
