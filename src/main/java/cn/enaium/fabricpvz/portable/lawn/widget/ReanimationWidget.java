/*
 * Ported from src/Lawn/Widget/LawnDialog.h:39-54 and src/Lawn/Widget/LawnDialog.cpp:388-440.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Widget/LawnDialog.h:39-54 (implementation
 * src/Lawn/Widget/LawnDialog.cpp:388-440).
 *
 * <p>A widget wrapper around one looping {@link Reanimation}, used by {@link LawnDialog} to draw
 * decorative animations (for example the "seed slot upgraded" sparkle).
 */
public class ReanimationWidget extends Widget {

    // LawnDialog.h:41-47
    public LawnApp mApp;
    public Reanimation mReanim;
    public LawnDialog mLawnDialog;
    public float mPosX;
    public float mPosY;

    // ReanimationWidget::ReanimationWidget (LawnDialog.cpp:388)
    public ReanimationWidget() {
        mApp = LawnApp.gLawnApp;
        mPosX = 0.0f;
        mPosY = 0.0f;
        mReanim = null;
        mMouseVisible = false;
        mClip = false;
        mLawnDialog = null;
        mHasAlpha = true;
    }

    // ReanimationWidget::~ReanimationWidget / Dispose (LawnDialog.cpp:393)
    @Override
    public void dispose() {
        if (mReanim != null) {
            mApp.mEffectSystem.mReanimationHolder.mReanimations.dataArrayFree(mReanim);
            mReanim = null;
        }
        super.dispose();
    }

    // ReanimationWidget::AddReanimation (LawnDialog.cpp:406)
    public void addReanimation(float x, float y, int theReanimationType) {
        mPosX = x;
        mPosY = y;
        mReanim = mApp.mEffectSystem.mReanimationHolder.allocReanimation(x, y, 0, theReanimationType);
        mReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
        mReanim.mIsAttachment = true;
        if (mReanim.trackExists("anim_idle")) {
            mReanim.setFramesForLayer("anim_idle");
        }

        resize((int) x, (int) y, 10, 10);
    }

    // ReanimationWidget::Draw (LawnDialog.cpp:423)
    @Override
    public void draw(Graphics g) {
        if (mReanim != null) {
            mReanim.draw(g);
        }
    }

    // ReanimationWidget::Update (LawnDialog.cpp:429)
    @Override
    public void update() {
        if (mReanim != null) {
            mReanim.update();
            markDirty();
        }
    }
}
