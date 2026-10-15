/*
 * Ported from src/Lawn/Widget/ZombatarTOS.h:1-78 and src/Lawn/Widget/ZombatarTOS.cpp:1-233.
 *
 * The modal terms-of-service dialog shown the first time a profile enters the Zombatar editor
 * (LawnApp::ShowZombatarTOS). A scrollable PICO129 body text clipped to a fixed window with an
 * image slider, a checkbox that has to be ticked before "Accept" is honoured (an arrow flashes at
 * it otherwise) and the back/accept image buttons.
 *
 * Upstream derives from LawnDialog with Dialog::BUTTONS_NONE and adds the slider, buttons and
 * checkbox as child widgets (local coordinates, like LawnDialog's own buttons), so the resize
 * literals stay un-offset exactly as ZombatarTOS.cpp:106-119.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.Checkbox;
import cn.enaium.fabricpvz.portable.sexy.CheckboxListener;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Slider;
import cn.enaium.fabricpvz.portable.sexy.SliderListener;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/ZombatarTOS.h:55-75 (declaration) / src/Lawn/Widget/ZombatarTOS.cpp:53
 * (constructor).
 */
public class ZombatarTOS extends LawnDialog implements SliderListener, CheckboxListener {

    // ZombatarTOS.cpp:41-58
    public static final int TOS_DIALOG_WIDTH = 600;
    public static final int TOS_DIALOG_HEIGHT = 450;
    public static final int TOS_SLIDER_X = 500;
    public static final int TOS_SLIDER_Y = 140;
    public static final int TOS_SLIDER_WIDTH = 29;
    public static final int TOS_SLIDER_HEIGHT = 135;
    public static final int TOS_BACK_X = 40;
    public static final int TOS_ACCEPT_X = 450;
    public static final int TOS_BUTTON_Y = 344;
    public static final int TOS_CHECK_X = 400;
    public static final int TOS_CHECK_Y = 340;
    public static final int TOS_TEXT_X = 50;
    public static final int TOS_TEXT_Y = 130;
    public static final int TOS_TEXT_WIDTH = 435;
    public static final int TOS_CLIP_HEIGHT = 160;
    public static final int TOS_ARROW_X = 420;
    public static final int TOS_ARROW_Y = 290;
    public static final int TOS_CHECK_WIDTH = 45;
    public static final int TOS_CHECK_HEIGHT = 45;

    // ZombatarTOS.h:44-50 (protected enum, exposed for the widget listeners)
    public static final int ZombatarTOS_Checkbox = 500;
    public static final int ZombatarTOS_Accept = 501;
    public static final int ZombatarTOS_Back = 502;
    public static final int ZombatarTOS_Slider = 503;

    // ZombatarTOS.h:54-62
    public Slider mTOSSlider;
    public NewLawnButton mBackButton;
    public NewLawnButton mAcceptButton;
    public Checkbox mTOSCheckbox;
    public int mTextHeight;
    public boolean mFlashArrow;
    public int mArrowAlpha;
    public int mArrowFadeDir;
    public String mBodyText = "";

    // ZombatarTOS::ZombatarTOS (ZombatarTOS.cpp:53-77)
    public ZombatarTOS(LawnApp theApp) {
        super(theApp, Dialogs.DIALOG_ZOMBATAR_TOS, true, "[ZOMBATAR_TOS_HEADER]", "", "",
                Dialog.BUTTONS_NONE);

        mTextHeight = 0;
        mFlashArrow = false;
        mArrowAlpha = 0;
        mArrowFadeDir = 3;

        mTOSSlider = new Slider(
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_TOS_SLIDER_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_TOS_SLIDER_THUMB_ID),
                ZombatarTOS_Slider,
                this);
        mTOSSlider.mHorizontal = false;
        mTOSSlider.setValue(0);

        mBackButton = GameButton.makeNewButton(ZombatarTOS_Back, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACK_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACK_BUTTON_HIGHLIGHT_ID), null);

        mAcceptButton = GameButton.makeNewButton(ZombatarTOS_Accept, this, "", null,
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_ACCEPT_BUTTON_ID),
                Resources.getImageById(Resources.IMAGE_ZOMBATAR_ACCEPT_BUTTON_HIGHLIGHT_ID), null);

        mTOSCheckbox = LawnCommon.makeNewCheckbox(ZombatarTOS_Checkbox, this, false);

        resize(0, 0, TOS_DIALOG_WIDTH, TOS_DIALOG_HEIGHT);
    }

    // ZombatarTOS::AddedToManager (ZombatarTOS.cpp:79-87)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mTOSSlider);
        addWidget(mBackButton);
        addWidget(mAcceptButton);
        addWidget(mTOSCheckbox);
    }

    // ZombatarTOS::RemovedFromManager (ZombatarTOS.cpp:89-97)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        removeWidget(mTOSSlider);
        removeWidget(mBackButton);
        removeWidget(mAcceptButton);
        removeWidget(mTOSCheckbox);
    }

    // ZombatarTOS::Resize (ZombatarTOS.cpp:99-121)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        Image aBack = Resources.getImageById(Resources.IMAGE_ZOMBATAR_BACK_BUTTON_ID);
        int aBackWidth = aBack != null ? aBack.getWidth() : 98;
        int aBackHeight = aBack != null ? aBack.getHeight() : 26;
        Image aAccept = Resources.getImageById(Resources.IMAGE_ZOMBATAR_ACCEPT_BUTTON_ID);
        int aAcceptWidth = aAccept != null ? aAccept.getWidth() : aBackWidth;
        int aAcceptHeight = aAccept != null ? aAccept.getHeight() : aBackHeight;

        mTOSSlider.resize(TOS_SLIDER_X, TOS_SLIDER_Y, TOS_SLIDER_WIDTH, TOS_SLIDER_HEIGHT);
        mBackButton.resize(TOS_BACK_X, TOS_BUTTON_Y, aBackWidth, aBackHeight);
        mAcceptButton.resize(TOS_ACCEPT_X, TOS_BUTTON_Y, aAcceptWidth, aAcceptHeight);
        mTOSCheckbox.resize(TOS_CHECK_X, TOS_CHECK_Y, TOS_CHECK_WIDTH, TOS_CHECK_HEIGHT);
    }

    // ZombatarTOS::Draw (ZombatarTOS.cpp:123-158)
    @Override
    public void draw(Graphics g) {
        super.draw(g);

        if (mBodyText.isEmpty()) {
            mBodyText = PvzpStringFile.translate("[ZOMBATAR_TOS]");
        }
        if (mTextHeight <= 0) {
            mTextHeight = PvzpStringFile.pvzpDrawStringWrappedHelper(g, mBodyText,
                    new Rect(0, 0, TOS_TEXT_WIDTH, 0), Resources.getFontById(Resources.FONT_PICO129_ID),
                    Color.White, DrawStringJustification.DS_ALIGN_LEFT, false);
        }

        int aMaxScroll = Math.max(0, mTextHeight - TOS_CLIP_HEIGHT);
        int aOffset = (int) (mTOSSlider.mVal * aMaxScroll);

        // GraphicsStateGuard around the ClipRect (ZombatarTOS.cpp:135-142): a child graphics carries
        // the same effect without leaking the clip back into the dialog's draw state.
        Graphics aTextGraphics = g.create();
        aTextGraphics.clipRect(TOS_TEXT_X, TOS_TEXT_Y, TOS_TEXT_WIDTH, TOS_CLIP_HEIGHT);
        PvzpStringFile.pvzpDrawStringWrapped(aTextGraphics, mBodyText,
                new Rect(TOS_TEXT_X, TOS_TEXT_Y - aOffset, TOS_TEXT_WIDTH, mTextHeight),
                Resources.getFontById(Resources.FONT_PICO129_ID), Color.White,
                DrawStringJustification.DS_ALIGN_LEFT);

        Image aArrow = Resources.getImageById(Resources.IMAGE_ZOMBATAR_TOS_ARROW_ID);
        if (mFlashArrow && aArrow != null) {
            g.setColorizeImages(true);
            g.setColor(new Color(255, 255, 255, mArrowAlpha));
            g.drawImage(aArrow, TOS_ARROW_X, TOS_ARROW_Y);
            g.setColorizeImages(false);
            g.setColor(Color.White);
        }
    }

    // ZombatarTOS::Update (ZombatarTOS.cpp:160-181)
    @Override
    public void update() {
        super.update();
        if (mFlashArrow) {
            mArrowAlpha += mArrowFadeDir;
            if (mArrowAlpha >= 255) {
                mArrowAlpha = 255;
                mArrowFadeDir = -3;
            } else if (mArrowAlpha <= 0) {
                mArrowAlpha = 0;
                mArrowFadeDir = 3;
            }
            markDirty();
        }
    }

    // ZombatarTOS::ButtonPress (ZombatarTOS.cpp:183-185) — empty, unlike LawnDialog::ButtonPress.
    @Override
    public void buttonPress(int theId) {
    }

    // ZombatarTOS::ButtonDepress (ZombatarTOS.cpp:187-211)
    @Override
    public void buttonDepress(int theId) {
        switch (theId) {
            case ZombatarTOS_Back:
                mApp.killDialog(mId);
                break;
            case ZombatarTOS_Accept:
                if (!mTOSCheckbox.mChecked) {
                    mFlashArrow = true;
                    mArrowAlpha = 0;
                    mArrowFadeDir = 3;
                    return;
                }
                if (mApp.mPlayerInfo != null) {
                    mApp.mPlayerInfo.mZombatarAccepted = 1;
                    mApp.mPlayerInfo.saveDetails();
                }
                mApp.killDialog(mId);
                if (mApp.mGameSelector != null && mApp.mGameSelector.mZombatarWidget != null) {
                    mApp.mGameSelector.mZombatarWidget.open();
                }
                break;
            default:
                break;
        }
    }

    // ZombatarTOS::KeyDown (ZombatarTOS.cpp:213-219)
    @Override
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_ESCAPE) {
            buttonDepress(ZombatarTOS_Back);
        }
    }

    // ZombatarTOS::MouseWheel (ZombatarTOS.cpp:221-232)
    @Override
    public void mouseWheel(int theDelta) {
        if (mTextHeight <= TOS_CLIP_HEIGHT) {
            return;
        }

        int aMaxScroll = Math.max(0, mTextHeight - TOS_CLIP_HEIGHT);
        int aOffset = (int) (mTOSSlider.mVal * aMaxScroll);
        aOffset -= theDelta * 12;
        mTOSSlider.setValue(Math.max(0.0, Math.min(1.0, (double) aOffset / aMaxScroll)));
    }

    // ZombatarTOS::CheckboxChecked (ZombatarTOS.cpp:214-224)
    @Override
    public void checkboxChecked(int theId, boolean checked) {
        mApp.playSample(SoundType.SOUND_BUTTONCLICK);
        if (theId == ZombatarTOS_Checkbox && checked) {
            mFlashArrow = false;
            mArrowAlpha = 0;
        }
    }

    // ZombatarTOS::SliderVal (ZombatarTOS.cpp:226-231)
    @Override
    public void sliderVal(int theId, double theVal) {
        markDirty();
    }
}
