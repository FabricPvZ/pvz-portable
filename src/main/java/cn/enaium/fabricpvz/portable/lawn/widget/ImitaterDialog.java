/*
 * Ported from src/Lawn/Widget/ImitaterDialog.cpp (186 lines) and
 * src/Lawn/Widget/ImitaterDialog.h:32-50.
 *
 * Modal dialog shown when the imitater button of the seed chooser is clicked: the player picks the
 * plant the imitater seed should copy. The chosen seed is immediately moved into the seed bank.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.ChosenSeedState;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.NotRecommend;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ToolTipWidget;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

public class ImitaterDialog extends LawnDialog {
    /** ImitaterDialog.cpp:23-24. */
    public static final int IMITATER_DIALOG_WIDTH = 500;
    public static final int IMITATER_DIALOG_HEIGHT = 600;

    public ToolTipWidget mToolTip;
    /** SeedType value. */
    public int mToolTipSeed;

    /** Scratch out-param for the position getter, so the draw/update paths allocate nothing. */
    private final Point mTempPoint = new Point();
    /** Scratch hit-test rect, mirroring upstream's stack-local {@code Rect} in SeedHitTest. */
    private final Rect mHitRect = new Rect(0, 0, 0, 0);

    // ImitaterDialog.cpp:26-38
    public ImitaterDialog() {
        super((LawnApp) SexyAppBase.current, Dialogs.DIALOG_IMITATER, true, "[CHOOSE_SEED_TO_COPY]",
                "", "[DIALOG_BUTTON_OK]", Dialog.BUTTONS_YES_NO);
        calcSize(IMITATER_DIALOG_WIDTH - mWidth, IMITATER_DIALOG_HEIGHT - mHeight);
        mToolTip = new ToolTipWidget();
        mClip = false;
        mToolTipSeed = SeedType.SEED_NONE;
        mLawnYesButton.mBtnNoDraw = true;
        mLawnYesButton.mMouseVisible = false;
        mLawnNoButton.mBtnNoDraw = true;
        mLawnNoButton.mMouseVisible = false;
    }

    // ImitaterDialog.cpp:42-58
    public int seedHitTest(int x, int y) {
        for (int aSeedType = 0; aSeedType < SeedType.SEED_GATLINGPEA; aSeedType++) {
            if (mApp.hasSeedType(aSeedType)) {
                getSeedPosition(aSeedType, mTempPoint);
                if (new Rect(mTempPoint.mX, mTempPoint.mY, GameConstants.SEED_PACKET_WIDTH,
                        GameConstants.SEED_PACKET_HEIGHT).contains(x, y)) {
                    return aSeedType;
                }
            }
        }
        return SeedType.SEED_NONE;
    }

    // ImitaterDialog.cpp:60-71
    public void updateCursor() {
        int aSeedType = seedHitTest(mApp.mWidgetManager.mLastMouseX - mX, mApp.mWidgetManager.mLastMouseY - mY);
        if (aSeedType != SeedType.SEED_NONE && !mApp.mSeedChooserScreen.seedNotAllowedToPick(aSeedType)) {
            mApp.setCursor(SexyAppBase.CURSOR_HAND);
        } else {
            mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
    }

    // ImitaterDialog.cpp:73-78
    @Override
    public void update() {
        super.update();
        showToolTip();
        updateCursor();
    }

    // ImitaterDialog.cpp:80-84
    public void getSeedPosition(int theIndex, Point out) {
        getSeedPosition(theIndex, mWidth, out);
    }

    /** ImitaterDialog.cpp:82-83. */
    public static void getSeedPosition(int theIndex, int theDialogWidth, Point out) {
        out.mX = (theIndex % 8) * (GameConstants.SEED_PACKET_WIDTH + 1) + theDialogWidth / 2 - 210;
        out.mY = (theIndex / 8) * (GameConstants.SEED_PACKET_HEIGHT + 1) + 112;
    }

    // ImitaterDialog.cpp:86-102
    @Override
    public void draw(Graphics g) {
        super.draw(g);
        g.setLinearBlend(true);
        for (int aSeedType = 0; aSeedType < SeedType.SEED_GATLINGPEA; aSeedType++) {
            if (mApp.hasSeedType(aSeedType)) {
                getSeedPosition(aSeedType, mTempPoint);
                boolean aGrayed = mApp.mSeedChooserScreen.seedNotAllowedToPick(aSeedType)
                        || mApp.mSeedChooserScreen.seedNotRecommendedToPick(aSeedType) != 0;
                cn.enaium.fabricpvz.portable.lawn.SeedPacket.drawSeedPacket(g, mTempPoint.mX, mTempPoint.mY,
                        SeedType.SEED_IMITATER, aSeedType, 0, aGrayed ? 115 : 255, true, false);
            }
        }
        mToolTip.draw(g);
    }

    // ImitaterDialog.cpp:104-149
    public void showToolTip() {
        if (!mApp.mWidgetManager.mMouseIn || !mApp.mActive) {
            removeToolTip();
            return;
        }

        int aSeedType = seedHitTest(mApp.mWidgetManager.mLastMouseX - mX, mApp.mWidgetManager.mLastMouseY - mY);
        if (aSeedType == SeedType.SEED_NONE) {
            removeToolTip();
        } else if (aSeedType != mToolTipSeed) {
            removeToolTip();
            int aRecFlags = mApp.mSeedChooserScreen.seedNotRecommendedToPick(aSeedType);
            if (mApp.mSeedChooserScreen.seedNotAllowedToPick(aSeedType)) {
                mToolTip.setWarningText("[NOT_ALLOWED_ON_THIS_LEVEL]");
            } else if (aRecFlags != 0) {
                if (PvzpCommon.testBit(aRecFlags, NotRecommend.NOT_RECOMMENDED_NOCTURNAL)) {
                    mToolTip.setWarningText("[NOCTURNAL_WARNING]");
                } else {
                    mToolTip.setWarningText("[NOT_RECOMMENDED_FOR_LEVEL]");
                }
            } else {
                mToolTip.setWarningText("");
            }
            mToolTip.setTitle(Plant.getNameString(SeedType.SEED_IMITATER, aSeedType));
            mToolTip.setLabel(Plant.getToolTip(aSeedType));

            getSeedPosition(aSeedType, mTempPoint);
            mToolTip.mX = (GameConstants.SEED_PACKET_WIDTH - mToolTip.mWidth) / 2 + mTempPoint.mX;
            mToolTip.mY = GameConstants.SEED_PACKET_HEIGHT + mTempPoint.mY;
            mToolTip.mVisible = true;
            mToolTipSeed = aSeedType;
        }
    }

    // ImitaterDialog.cpp:151-155
    public void removeToolTip() {
        mToolTip.mVisible = false;
        mToolTipSeed = SeedType.SEED_NONE;
    }

    /**
     * ImitaterDialog.cpp:163-168 — the picked plant is put into the chooser as a visible imitater
     * seed, so that the seed chooser can fly it into the bank as usual.
     */
    public static void chooseImitaterTarget(ChosenSeed theImitaterSeed, int theChosenSeedType) {
        theImitaterSeed.mSeedState = ChosenSeedState.SEED_IN_CHOOSER;
        theImitaterSeed.mImitaterType = theChosenSeedType;
    }

    // ImitaterDialog.cpp:157-186
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        int aSeedType = seedHitTest(x, y);
        if (aSeedType != SeedType.SEED_NONE) {
            SeedChooserScreen aSeedChooser = mApp.mSeedChooserScreen;
            if (!aSeedChooser.seedNotAllowedToPick(aSeedType)) {
                ChosenSeed aImitater = aSeedChooser.mChosenSeeds[SeedType.SEED_IMITATER];
                chooseImitaterTarget(aImitater, aSeedType);
                aImitater.mX = aSeedChooser.mImitaterButton.mX;
                aImitater.mY = aSeedChooser.mImitaterButton.mY;
                aSeedChooser.clickedSeedInChooser(aImitater);
                aSeedChooser.updateImitaterButton();
                mApp.killDialog(mId);
            }
        } else {
            super.mouseDown(x, y, theClickCount);
        }
    }

    // ImitaterDialog.h:48 — MouseUp is a no-op.
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
    }
}
