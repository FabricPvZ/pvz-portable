/*
 * Ported from src/Lawn/Widget/LawnDialog.h:39-102 and src/Lawn/Widget/LawnDialog.cpp:35-544.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SoundType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.Insets;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/LawnDialog.h:56-87 (implementation
 * src/Lawn/Widget/LawnDialog.cpp:35-386).
 *
 * <p>The base dialog of the lawn UI: a 9-slice stone frame with a header, vertically-centred
 * word-wrapped body text and one or two {@link LawnStoneButton}s placed by {@link #resize}. The
 * {@code theButtonMode} argument selects the button pair ({@code BUTTONS_YES_NO},
 * {@code BUTTONS_OK_CANCEL} or {@code BUTTONS_FOOTER}, see {@link Dialog}); the dialog result is a
 * {@code Dialog::ID_*} constant ({@link Dialog#ID_YES}/{@link Dialog#ID_NO}/{@link Dialog#ID_OK}/
 * {@link Dialog#ID_CANCEL}/{@link Dialog#ID_FOOTER}).
 */
public class LawnDialog extends Dialog {

    /** LawnDialog.cpp:35. */
    public static final int DIALOG_HEADER_OFFSET = 45;

    // LawnDialog.h:58-66
    public LawnApp mApp;
    public int mButtonDelay;
    public ReanimationWidget mReanimation;
    public boolean mDrawStandardBack;
    public LawnStoneButton mLawnYesButton;
    public LawnStoneButton mLawnNoButton;
    public boolean mTallBottom;
    public boolean mVerticalCenterText;

    // LawnDialog::LawnDialog (LawnDialog.cpp:37)
    public LawnDialog(LawnApp theApp, int theId, boolean isModal, String theDialogHeader,
            String theDialogLines, String theDialogFooter, int theButtonMode) {
        super(null, null, theId, isModal, theDialogHeader, theDialogLines, "", BUTTONS_NONE);
        mApp = theApp;
        mButtonDelay = -1;
        mReanimation = new ReanimationWidget();
        mReanimation.mLawnDialog = this;
        mDrawStandardBack = true;
        mTallBottom = false;
        mVerticalCenterText = true;
        mDialogHeader = PvzpStringFile.translate(theDialogHeader);
        mDialogLines = PvzpStringFile.translate(theDialogLines);
        setHeaderColor(new Color(0xE0, 0xBB, 0x62));
        setLinesColor(new Color(0xE0, 0xBB, 0x62));
        setHeaderFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT24_ID));
        setLinesFont(Resources.getFontById(Resources.FONT_DWARVENTODCRAFT15_ID));
        mContentInsets = new Insets(36, 35, 46, 36);

        // the localization key names for these dialogs are wrong
        if (theButtonMode == 1) {
            mLawnYesButton = GameButton.makeButton(1000, this, mApp.getString("BUTTON_YES", "Yes"));
            mLawnNoButton = GameButton.makeButton(1001, this, mApp.getString("BUTTON_NO", "No"));
        } else if (theButtonMode == 2) {
            mLawnYesButton = GameButton.makeButton(1000, this, mApp.getString("BUTTON_OK", "Ok"));
            mLawnNoButton = GameButton.makeButton(1001, this, mApp.getString("BUTTON_CANCEL", "Cancel"));
        } else if (theButtonMode == 3) {
            mLawnYesButton = GameButton.makeButton(1000, this, theDialogFooter);
            mLawnNoButton = null;
        } else {
            mLawnYesButton = null;
            mLawnNoButton = null;
        }

        mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        calcSizeInternal(0, 0, true);
    }

    // LawnDialog::GetLeft (LawnDialog.cpp:83)
    public int getLeft() {
        return mContentInsets.mLeft + mBackgroundInsets.mLeft;
    }

    // LawnDialog::GetWidth (LawnDialog.cpp:88)
    public int getWidth() {
        return mWidth - mContentInsets.mLeft - mContentInsets.mRight - mBackgroundInsets.mLeft
                - mBackgroundInsets.mRight;
    }

    // LawnDialog::GetTop (LawnDialog.cpp:93)
    public int getTop() {
        return mContentInsets.mTop + mBackgroundInsets.mTop + 99;
    }

    // LawnDialog::CalcSize (LawnDialog.cpp:98)
    public void calcSize(int theExtraX, int theExtraY) {
        calcSizeInternal(theExtraX, theExtraY, false);
    }

    /**
     * {@link #calcSize} with an explicit dispatch decision: {@code theInBaseConstructor} runs the
     * LawnDialog part of Resize only (see {@link #resizeBase}), otherwise the virtual Resize runs so
     * a subclass can lay out its widgets once they exist.
     */
    private void calcSizeInternal(int theExtraX, int theExtraY, boolean theInBaseConstructor) {
        // minimum width from the insets
        int aWidth = mBackgroundInsets.mLeft + mBackgroundInsets.mRight + mContentInsets.mLeft
                + mContentInsets.mRight + theExtraX;
        // plus the header string width
        if (mDialogHeader.length() > 0) {
            aWidth += mHeaderFont.stringWidth(mDialogHeader);
        }
        // minimum width of the dialog images
        int aTopMidWidth = Resources.getImageById(ImageID.IMAGE_DIALOG_TOPMIDDLE).getWidth();
        int aImageWidth = Resources.getImageById(ImageID.IMAGE_DIALOG_TOPLEFT).getWidth()
                + Resources.getImageById(ImageID.IMAGE_DIALOG_TOPRIGHT).getWidth() + aTopMidWidth;
        // clamp to the image width, rounding up to whole middle tiles
        if (aWidth <= aImageWidth) {
            aWidth = aImageWidth;
        } else if (aTopMidWidth > 0) {
            int anExtraWidth = (aWidth - aImageWidth) % aTopMidWidth;
            if (anExtraWidth != 0) {
                aWidth += aTopMidWidth - anExtraWidth;
            }
        }

        // minimum height from the insets
        int aHeight = mBackgroundInsets.mTop + mBackgroundInsets.mBottom + mContentInsets.mTop
                + mContentInsets.mBottom + theExtraY + DIALOG_HEADER_OFFSET;
        // plus the header string height
        if (mDialogHeader.length() > 0) {
            aHeight += -mHeaderFont.getAscentPadding() + mHeaderFont.getHeight() + mSpaceAfterHeader;
        }
        // also account for the body text
        if (mDialogLines.length() > 0) {
            aWidth += aTopMidWidth;
            Graphics g = new Graphics();
            g.setFont(mLinesFont);
            int aBasicWidth = aWidth - mBackgroundInsets.mLeft - mBackgroundInsets.mRight
                    - mContentInsets.mLeft - mContentInsets.mRight - 4;
            aHeight += getWordWrappedHeight(g, aBasicWidth, mDialogLines,
                    mLinesFont.getLineSpacing() + mLineSpacingOffset) + 30;
        }
        aHeight += mButtonHeight;
        // minimum height of the dialog images
        int aBottomHeight = (mTallBottom ? Resources.getImageById(ImageID.IMAGE_DIALOG_BIGBOTTOMLEFT)
                : Resources.getImageById(ImageID.IMAGE_DIALOG_BOTTOMLEFT)).getHeight();
        int aImageHeight = Resources.getImageById(ImageID.IMAGE_DIALOG_TOPLEFT).getHeight()
                + aBottomHeight + DIALOG_HEADER_OFFSET;
        // clamp to the image height, rounding up to whole center tiles
        if (aHeight < aImageHeight) {
            aHeight = aImageHeight;
        } else {
            int aCenterHeight = Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERLEFT).getHeight();
            int anExtraHeight = (aHeight - aImageHeight) % aCenterHeight;
            if (anExtraHeight != 0) {
                aHeight += aCenterHeight - anExtraHeight;
            }
        }

        if (theInBaseConstructor) {
            resizeBase(mX, mY, aWidth, aHeight);
        } else {
            resize(mX, mY, aWidth, aHeight);
        }
    }

    // LawnDialog::AddedToManager (LawnDialog.cpp:156)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);
        addWidget(mReanimation);
        if (mLawnYesButton != null) {
            addWidget(mLawnYesButton);
        }
        if (mLawnNoButton != null) {
            addWidget(mLawnNoButton);
        }
    }

    // LawnDialog::RemovedFromManager (LawnDialog.cpp:165)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);
        if (mLawnYesButton != null) {
            removeWidget(mLawnYesButton);
        }
        if (mLawnNoButton != null) {
            removeWidget(mLawnNoButton);
        }
        removeWidget(mReanimation);

        if (mReanimation.mReanim != null) {
            mApp.mEffectSystem.mReanimationHolder.mReanimations.dataArrayFree(mReanimation.mReanim);
            mReanimation.mReanim = null;
        }
    }

    // LawnDialog::SetButtonDelay (LawnDialog.cpp:178)
    public void setButtonDelay(int theDelay) {
        mButtonDelay = theDelay;
        if (mLawnYesButton != null) {
            mLawnYesButton.setDisabled(true);
        }
        if (mLawnNoButton != null) {
            mLawnNoButton.setDisabled(true);
        }
    }

    // LawnDialog::Update (LawnDialog.cpp:185)
    @Override
    public void update() {
        super.update();
        if (mButtonDelay == 0) {
            if (mLawnYesButton != null) {
                mLawnYesButton.setDisabled(false);
            }
            if (mLawnNoButton != null) {
                mLawnNoButton.setDisabled(false);
            }
        }
        markDirty();
    }

    // LawnDialog::ButtonPress (LawnDialog.cpp:195)
    @Override
    public void buttonPress(int theId) {
        mApp.playSample(SoundType.SOUND_GRAVEBUTTON);
    }

    // LawnDialog::ButtonDepress (LawnDialog.cpp:200)
    @Override
    public void buttonDepress(int theId) {
        if (mButtonDelay < 0 || mUpdateCnt > mButtonDelay) {
            super.buttonDepress(theId);
        }
    }

    // LawnDialog::CheckboxChecked (LawnDialog.cpp:208)
    public void checkboxChecked() {
        mApp.playSample(SoundType.SOUND_BUTTONCLICK);
    }

    // LawnDialog::KeyDown (LawnDialog.cpp:213)
    @Override
    public void keyDown(int theKey) {
        if (mId == Dialogs.DIALOG_PAUSED && mApp.mBoard != null) {
            mApp.mBoard.doTypingCheck(theKey);
        }

        if (mId != Dialogs.DIALOG_ALMANAC) {
            if (theKey == KeyCode.KEYCODE_SPACE || theKey == KeyCode.KEYCODE_RETURN
                    || theKey == 'y' || theKey == 'Y') {
                super.buttonDepress(Dialog.ID_YES);
            } else if (theKey == KeyCode.KEYCODE_ESCAPE || theKey == 'n' || theKey == 'N') {
                if (mLawnNoButton != null) {
                    super.buttonDepress(Dialog.ID_NO);
                } else if (theKey == KeyCode.KEYCODE_ESCAPE && mLawnYesButton != null) {
                    super.buttonDepress(Dialog.ID_YES);
                }
            }
        }
    }

    // LawnDialog::Resize (LawnDialog.cpp:243)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        resizeBase(theX, theY, theWidth, theHeight);
    }

    /**
     * The LawnDialog half of Resize (LawnDialog.cpp:243).
     *
     * <p>Upstream C++ dispatches the virtual {@code Resize} from {@code CalcSize} statically to
     * {@code LawnDialog::Resize} while {@code LawnDialog}'s own constructor runs (the derived ctor
     * body has not executed yet, so its widgets do not exist). Java cannot express that, so the
     * constructor's size pass calls this base implementation directly and skips the subclass
     * overrides that lay out their not-yet-built widgets.
     */
    private void resizeBase(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        int aButtonAreaX = mContentInsets.mLeft + mBackgroundInsets.mLeft - 5;
        int aButtonAreaY = mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom
                - Resources.getImageById(ImageID.IMAGE_BUTTON_LEFT).getHeight() + 2;
        int aButtonAreaWidth = mWidth - mContentInsets.mRight - mBackgroundInsets.mRight
                - mBackgroundInsets.mLeft - mContentInsets.mLeft + 8;
        // minimum width imposed by the button images
        int aButtonMinWidth = Resources.getImageById(ImageID.IMAGE_BUTTON_LEFT).getWidth()
                + Resources.getImageById(ImageID.IMAGE_BUTTON_RIGHT).getWidth();
        // extra width available in the button area
        int aBtnMidWidth = Resources.getImageById(ImageID.IMAGE_BUTTON_MIDDLE).getWidth();
        int aButtonExtraWidth = (aButtonAreaWidth - 10) / 2 - aBtnMidWidth - aButtonMinWidth + 1;
        // clamp to zero, otherwise round up to whole middle tiles
        if (aButtonExtraWidth <= 0) {
            aButtonExtraWidth = 0;
        } else if (aBtnMidWidth > 0) {
            int anExtraWidth = aButtonExtraWidth % aBtnMidWidth;
            if (anExtraWidth != 0) {
                aButtonExtraWidth += aBtnMidWidth - anExtraWidth;
            }
        }
        int aButtonWidth = aButtonMinWidth + aButtonExtraWidth;

        if (mTallBottom) {
            aButtonAreaY += 5;
        }

        if (mLawnYesButton != null && mLawnNoButton != null) {
            mLawnYesButton.resize(aButtonAreaX, aButtonAreaY, aButtonWidth,
                    Resources.getImageById(ImageID.IMAGE_BUTTON_LEFT).getHeight());
            mLawnNoButton.resize(aButtonAreaWidth - aButtonWidth + aButtonAreaX, aButtonAreaY,
                    aButtonWidth, Resources.getImageById(ImageID.IMAGE_BUTTON_LEFT).getHeight());
        } else if (mLawnYesButton != null) {
            aButtonExtraWidth = aButtonAreaWidth - aBtnMidWidth - aButtonMinWidth + 1;
            if (aButtonExtraWidth <= 0) {
                aButtonExtraWidth = 0;
            } else if (aBtnMidWidth > 0) {
                int anExtraWidth = aButtonExtraWidth % aBtnMidWidth;
                if (anExtraWidth != 0) {
                    aButtonExtraWidth += aBtnMidWidth - anExtraWidth;
                }
            }
            int aButtonWidth2 = aButtonMinWidth + aButtonExtraWidth;

            mLawnYesButton.resize(aButtonAreaX + (aButtonAreaWidth - aButtonWidth2) / 2,
                    aButtonAreaY, aButtonWidth2,
                    Resources.getImageById(ImageID.IMAGE_BUTTON_LEFT).getHeight());
        }

        if (mReanimation.mReanim != null) {
            mReanimation.resize((int) mReanimation.mPosX, (int) mReanimation.mPosY
                    + DIALOG_HEADER_OFFSET, mReanimation.mWidth, mReanimation.mHeight);
        }
    }

    // LawnDialog::Draw (LawnDialog.cpp:299)
    @Override
    public void draw(Graphics g) {
        if (!mDrawStandardBack) {
            return;
        }

        Image aBottomLeftImage = Resources.getImageById(ImageID.IMAGE_DIALOG_BOTTOMLEFT);
        Image aBottomMiddleImage = Resources.getImageById(ImageID.IMAGE_DIALOG_BOTTOMMIDDLE);
        Image aBottomRightImage = Resources.getImageById(ImageID.IMAGE_DIALOG_BOTTOMRIGHT);
        if (mTallBottom) {
            aBottomLeftImage = Resources.getImageById(ImageID.IMAGE_DIALOG_BIGBOTTOMLEFT);
            aBottomMiddleImage = Resources.getImageById(ImageID.IMAGE_DIALOG_BIGBOTTOMMIDDLE);
            aBottomRightImage = Resources.getImageById(ImageID.IMAGE_DIALOG_BIGBOTTOMRIGHT);
        }

        int aRepeatX = (mWidth - Resources.getImageById(ImageID.IMAGE_DIALOG_TOPRIGHT).getWidth()
                - Resources.getImageById(ImageID.IMAGE_DIALOG_TOPLEFT).getWidth())
                / Resources.getImageById(ImageID.IMAGE_DIALOG_TOPMIDDLE).getWidth();
        int aRepeatY = (mHeight - Resources.getImageById(ImageID.IMAGE_DIALOG_TOPLEFT).getHeight()
                - aBottomLeftImage.getHeight() - DIALOG_HEADER_OFFSET)
                / Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERLEFT).getHeight();

        int aPosX = 0;
        int aPosY = DIALOG_HEADER_OFFSET;
        g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_TOPLEFT), aPosX, aPosY);
        aPosX += Resources.getImageById(ImageID.IMAGE_DIALOG_TOPLEFT).getWidth();
        for (int i = 0; i < aRepeatX; i++) {
            g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_TOPMIDDLE), aPosX, aPosY);
            aPosX += Resources.getImageById(ImageID.IMAGE_DIALOG_TOPMIDDLE).getWidth();
        }
        g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_TOPRIGHT), aPosX, aPosY);

        aPosY += Resources.getImageById(ImageID.IMAGE_DIALOG_TOPRIGHT).getHeight();
        for (int y = 0; y < aRepeatY; y++) {
            aPosX = 0;

            g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERLEFT), aPosX, aPosY);
            aPosX += Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERLEFT).getWidth();

            for (int x = 0; x < aRepeatX; x++) {
                g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERMIDDLE), aPosX, aPosY);
                aPosX += Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERMIDDLE).getWidth();
            }

            g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERRIGHT), aPosX, aPosY);
            aPosY += Resources.getImageById(ImageID.IMAGE_DIALOG_CENTERLEFT).getHeight();
        }

        aPosX = 0;
        g.drawImage(aBottomLeftImage, aPosX, aPosY);
        aPosX += aBottomLeftImage.getWidth();
        for (int i = 0; i < aRepeatX; i++) {
            g.drawImage(aBottomMiddleImage, aPosX, aPosY);
            aPosX += aBottomMiddleImage.getWidth();
        }
        g.drawImage(aBottomRightImage, aPosX, aPosY);
        g.drawImage(Resources.getImageById(ImageID.IMAGE_DIALOG_HEADER),
                (mWidth - Resources.getImageById(ImageID.IMAGE_DIALOG_HEADER).getWidth()) / 2 - 5, 0);

        int aFontY = mContentInsets.mTop + mBackgroundInsets.mTop + DIALOG_HEADER_OFFSET;
        if (mDialogHeader.length() > 0) {
            int aOffsetY = aFontY - mHeaderFont.getAscentPadding() + mHeaderFont.getAscent();
            g.setFont(mHeaderFont);
            g.setColor(mColors.mHeader);
            writeCenteredLine(g, aOffsetY, mDialogHeader);
            aFontY = aOffsetY - mHeaderFont.getAscent() + mHeaderFont.getHeight() + mSpaceAfterHeader;
        }

        g.setFont(mLinesFont);
        g.setColor(mColors.mLines);
        int aLinesAreaWidth = mWidth - mContentInsets.mLeft - mContentInsets.mRight
                - mBackgroundInsets.mLeft - mBackgroundInsets.mRight - 4;
        Rect aRect = new Rect(mBackgroundInsets.mLeft + mContentInsets.mLeft + 2, aFontY,
                aLinesAreaWidth, 0);
        if (mVerticalCenterText) {
            int aLinesHeight = getWordWrappedHeight(g, aLinesAreaWidth, mDialogLines,
                    mLinesFont.getLineSpacing() + mLineSpacingOffset);
            int aLinesAreaHeight = mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom
                    - mButtonHeight - aFontY - 55;
            if (mTallBottom) {
                aLinesAreaHeight -= 36;
            }
            aRect.mY += (aLinesAreaHeight - aLinesHeight) / 2;
        }
        writeWordWrapped(g, aRect, mDialogLines, mLinesFont.getLineSpacing() + mLineSpacingOffset,
                mTextAlign);
    }
}
