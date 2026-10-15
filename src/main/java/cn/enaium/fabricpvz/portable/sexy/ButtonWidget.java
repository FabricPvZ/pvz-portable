package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/SexyAppFramework/widget/ButtonWidget.h:56 (implementation
 * src/SexyAppFramework/widget/ButtonWidget.cpp:34).
 *
 * <p>A clickable widget: either a flat three-colour bevel frame with a label, or 9-slice images.
 * Public fields keep their upstream names, methods are camelCased.
 *
 * <p>PORT-NOTE: upstream's {@code std::unique_ptr<_Font> mFont} is a plain {@link Font} reference in
 * Java; {@link #setFont} and the lazily-installed default font use {@link Font#duplicate()} exactly
 * as upstream's {@code std::unique_ptr::reset(theFont->Duplicate())}.
 *
 * <p>PORT-NOTE: upstream's {@code ~ButtonWidget() = default} has no Java counterpart; the widget
 * owns no resources that are not owned by the app (the images are borrowed pointers).
 */
public class ButtonWidget extends Widget {
    /** Ported from src/SexyAppFramework/widget/ButtonWidget.h:37 (struct ButtonColorScheme). */
    public static class ButtonColorScheme {
        public Color mLabel;
        public Color mLabelHilite;
        public Color mDarkOutline;
        public Color mLightOutline;
        public Color mMediumOutline;
        public Color mBkg;

        // ButtonColorScheme member default-construction (ButtonWidget.h:38-45)
        public ButtonColorScheme() {
            mLabel = new Color();
            mLabelHilite = new Color();
            mDarkOutline = new Color();
            mLightOutline = new Color();
            mMediumOutline = new Color();
            mBkg = new Color();
        }

        // Aggregate initialisation, as used by the gDefaultButtonColors table (ButtonWidget.h:47).
        public ButtonColorScheme(Color theLabel, Color theLabelHilite, Color theDarkOutline,
                Color theLightOutline, Color theMediumOutline, Color theBkg) {
            mLabel = theLabel;
            mLabelHilite = theLabelHilite;
            mDarkOutline = theDarkOutline;
            mLightOutline = theLightOutline;
            mMediumOutline = theMediumOutline;
            mBkg = theBkg;
        }

        /**
         * PORT-NOTE: upstream's {@code mColors = theColors} copies the struct (and therefore every
         * {@link Color} value). Java would alias the caller's colours, so the port copies them;
         * otherwise {@code SetLabelColor} &amp; friends would mutate shared schemes.
         */
        public ButtonColorScheme(ButtonColorScheme theColors) {
            mLabel = new Color(theColors.mLabel.mRed, theColors.mLabel.mGreen,
                    theColors.mLabel.mBlue, theColors.mLabel.mAlpha);
            mLabelHilite = new Color(theColors.mLabelHilite.mRed, theColors.mLabelHilite.mGreen,
                    theColors.mLabelHilite.mBlue, theColors.mLabelHilite.mAlpha);
            mDarkOutline = new Color(theColors.mDarkOutline.mRed, theColors.mDarkOutline.mGreen,
                    theColors.mDarkOutline.mBlue, theColors.mDarkOutline.mAlpha);
            mLightOutline = new Color(theColors.mLightOutline.mRed, theColors.mLightOutline.mGreen,
                    theColors.mLightOutline.mBlue, theColors.mLightOutline.mAlpha);
            mMediumOutline = new Color(theColors.mMediumOutline.mRed, theColors.mMediumOutline.mGreen,
                    theColors.mMediumOutline.mBlue, theColors.mMediumOutline.mAlpha);
            mBkg = new Color(theColors.mBkg.mRed, theColors.mBkg.mGreen, theColors.mBkg.mBlue,
                    theColors.mBkg.mAlpha);
        }
    }

    /** gDefaultButtonColors (ButtonWidget.h:47-54). */
    public static final ButtonColorScheme gDefaultButtonColors = new ButtonColorScheme(
            new Color(0, 0, 0),
            new Color(0, 0, 0),
            new Color(0, 0, 0),
            new Color(255, 255, 255),
            new Color(132, 132, 132),
            new Color(212, 212, 212));

    // ButtonWidget.h:59-63
    public static final int BUTTON_LABEL_LEFT = -1;
    public static final int BUTTON_LABEL_CENTER = 0;
    public static final int BUTTON_LABEL_RIGHT = 1;

    // ButtonWidget.h:65-78
    public int mId;
    public String mLabel;
    public int mLabelJustify;
    public Font mFont;
    public ButtonColorScheme mColors;
    public Image mButtonImage;
    public Image mOverImage;
    public Image mDownImage;
    public Image mDisabledImage;
    public Rect mNormalRect;
    public Rect mOverRect;
    public Rect mDownRect;
    public Rect mDisabledRect;

    // ButtonWidget.h:88-97
    public boolean mInverted;
    public boolean mBtnNoDraw;
    public boolean mFrameNoDraw;
    public ButtonListener mButtonListener;

    public double mOverAlpha;
    public double mOverAlphaSpeed;
    public double mOverAlphaFadeInSpeed;

    // ButtonWidget::ButtonWidget (ButtonWidget.cpp:34)
    public ButtonWidget(int theId, ButtonListener theButtonListener) {
        mId = theId;
        // PORT-NOTE: upstream default-constructs the std::string mLabel to "" (ButtonWidget.h:66).
        mLabel = "";
        mLabelJustify = BUTTON_LABEL_CENTER;
        mFont = null;
        mButtonImage = null;
        mOverImage = null;
        mDownImage = null;
        mDisabledImage = null;
        mNormalRect = new Rect();
        mOverRect = new Rect();
        mDownRect = new Rect();
        mDisabledRect = new Rect();
        mInverted = false;
        mBtnNoDraw = false;
        mFrameNoDraw = false;
        mButtonListener = theButtonListener;
        mHasAlpha = true;

        mOverAlpha = 0;
        mOverAlphaSpeed = 0;
        mOverAlphaFadeInSpeed = 0;

        setColors(gDefaultButtonColors);
    }

    // ButtonWidget::HaveButtonImage (ButtonWidget.cpp:77)
    public boolean haveButtonImage(Image theImage, Rect theRect) {
        return (theImage != null || theRect.mWidth != 0);
    }

    // ButtonWidget::DrawButtonImage (ButtonWidget.cpp:82)
    public void drawButtonImage(Graphics g, Image theImage, Rect theRect, int x, int y) {
        if (theRect.mWidth != 0) {
            g.drawImage(mButtonImage, x, y, theRect);
        } else {
            g.drawImage(theImage, x, y);
        }
    }

    // ButtonWidget::SetFont (ButtonWidget.cpp:57)
    public void setFont(Font theFont) {
        mFont = theFont.duplicate();
    }

    // ButtonWidget::SetColors (ButtonWidget.cpp:62)
    public void setColors(ButtonColorScheme theColors) {
        mColors = new ButtonColorScheme(theColors);
        markDirty();
    }

    // ButtonWidget::SetLabelColor (ButtonWidget.cpp:68)
    public void setLabelColor(Color theColor) {
        // PORT-NOTE: upstream stores a Color by value; Java copies so the colour cannot alias.
        mColors.mLabel = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue, theColor.mAlpha);
        markDirty();
    }

    // ButtonWidget::SetLabelHiliteColor (ButtonWidget.cpp:74)
    public void setLabelHiliteColor(Color theColor) {
        mColors.mLabelHilite = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue,
                theColor.mAlpha);
        markDirty();
    }

    // ButtonWidget::SetBkgColor (ButtonWidget.cpp:80)
    public void setBkgColor(Color theColor) {
        mColors.mBkg = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue, theColor.mAlpha);
        markDirty();
    }

    // ButtonWidget::IsButtonDown (ButtonWidget.cpp:86)
    public boolean isButtonDown() {
        return mIsDown && mIsOver && !mDisabled;
    }

    // ButtonWidget::Draw (ButtonWidget.cpp:104)
    @Override
    public void draw(Graphics g) {
        if (mBtnNoDraw) {
            return;
        }

        Font aDefaultFont = mWidgetManager.mApp.mDefaultFont;
        if ((mFont == null) && (mLabel.length() > 0) && (aDefaultFont != null)) {
            mFont = aDefaultFont.duplicate();
        }

        boolean isDown = mIsDown && mIsOver && !mDisabled;
        isDown ^= mInverted;

        int aFontX = 0; // BUTTON_LABEL_LEFT
        int aFontY = 0;

        if (mFont != null) {
            if (mLabelJustify == BUTTON_LABEL_CENTER) {
                aFontX = (mWidth - mFont.stringWidth(mLabel)) / 2;
            } else if (mLabelJustify == BUTTON_LABEL_RIGHT) {
                aFontX = mWidth - mFont.stringWidth(mLabel);
            }
            aFontY = (mHeight + mFont.getAscent() - mFont.getAscent() / 6 - 1) / 2;
        }

        g.setFont(mFont);

        if ((mButtonImage == null) && (mDownImage == null)) {
            if (!mFrameNoDraw) {
                g.setColor(mColors.mBkg);
                g.fillRect(0, 0, mWidth, mHeight);
            }

            if (isDown) {
                if (!mFrameNoDraw) {
                    g.setColor(mColors.mDarkOutline);
                    g.fillRect(0, 0, mWidth - 1, 1);
                    g.fillRect(0, 0, 1, mHeight - 1);

                    g.setColor(mColors.mLightOutline);
                    g.fillRect(0, mHeight - 1, mWidth, 1);
                    g.fillRect(mWidth - 1, 0, 1, mHeight);

                    g.setColor(mColors.mMediumOutline);
                    g.fillRect(1, 1, mWidth - 3, 1);
                    g.fillRect(1, 1, 1, mHeight - 3);
                }

                if (mIsOver) {
                    g.setColor(mColors.mLabelHilite);
                } else {
                    g.setColor(mColors.mLabel);
                }

                g.drawString(mLabel, aFontX + 1, aFontY + 1);
            } else {
                if (!mFrameNoDraw) {
                    g.setColor(mColors.mLightOutline);
                    g.fillRect(0, 0, mWidth - 1, 1);
                    g.fillRect(0, 0, 1, mHeight - 1);

                    g.setColor(mColors.mDarkOutline);
                    g.fillRect(0, mHeight - 1, mWidth, 1);
                    g.fillRect(mWidth - 1, 0, 1, mHeight);

                    g.setColor(mColors.mMediumOutline);
                    g.fillRect(1, mHeight - 2, mWidth - 2, 1);
                    g.fillRect(mWidth - 2, 1, 1, mHeight - 2);
                }

                if (mIsOver) {
                    g.setColor(mColors.mLabelHilite);
                } else {
                    g.setColor(mColors.mLabel);
                }

                g.drawString(mLabel, aFontX, aFontY);
            }
        } else {
            if (!isDown) {
                if (mDisabled && haveButtonImage(mDisabledImage, mDisabledRect)) {
                    drawButtonImage(g, mDisabledImage, mDisabledRect, 0, 0);
                } else if ((mOverAlpha > 0) && haveButtonImage(mOverImage, mOverRect)) {
                    if (haveButtonImage(mButtonImage, mNormalRect) && mOverAlpha < 1) {
                        drawButtonImage(g, mButtonImage, mNormalRect, 0, 0);
                    }

                    g.setColorizeImages(true);
                    g.setColor(new Color(255, 255, 255, (int) (mOverAlpha * 255)));
                    drawButtonImage(g, mOverImage, mOverRect, 0, 0);
                    g.setColorizeImages(false);
                } else if ((mIsOver || mIsDown) && haveButtonImage(mOverImage, mOverRect)) {
                    drawButtonImage(g, mOverImage, mOverRect, 0, 0);
                } else if (haveButtonImage(mButtonImage, mNormalRect)) {
                    drawButtonImage(g, mButtonImage, mNormalRect, 0, 0);
                }

                if (mIsOver) {
                    g.setColor(mColors.mLabelHilite);
                } else {
                    g.setColor(mColors.mLabel);
                }
                g.drawString(mLabel, aFontX, aFontY);
            } else {
                if (haveButtonImage(mDownImage, mDownRect)) {
                    drawButtonImage(g, mDownImage, mDownRect, 0, 0);
                } else if (haveButtonImage(mOverImage, mOverRect)) {
                    drawButtonImage(g, mOverImage, mOverRect, 1, 1);
                } else {
                    drawButtonImage(g, mButtonImage, mNormalRect, 1, 1);
                }

                g.setColor(mColors.mLabelHilite);
                g.drawString(mLabel, aFontX + 1, aFontY + 1);
            }
        }
    }

    // ButtonWidget::SetDisabled (ButtonWidget.cpp:231)
    @Override
    public void setDisabled(boolean isDisabled) {
        super.setDisabled(isDisabled);

        if (haveButtonImage(mDisabledImage, mDisabledRect)) {
            markDirty();
        }
    }

    // ButtonWidget::MouseEnter (ButtonWidget.cpp:238)
    @Override
    public void mouseEnter() {
        super.mouseEnter();

        if (mOverAlphaFadeInSpeed == 0 && mOverAlpha > 0) {
            mOverAlpha = 0;
        }

        if (mIsDown || (haveButtonImage(mOverImage, mOverRect))
                || (!mColors.mLabelHilite.equals(mColors.mLabel))) {
            markDirty();
        }

        mButtonListener.buttonMouseEnter(mId);
    }

    // ButtonWidget::MouseLeave (ButtonWidget.cpp:252)
    @Override
    public void mouseLeave() {
        super.mouseLeave();

        if (mOverAlphaSpeed == 0 && mOverAlpha > 0) {
            mOverAlpha = 0;
        } else if (mOverAlphaSpeed > 0 && mOverAlpha == 0) { // fade out from full
            mOverAlpha = 1;
        }

        if (mIsDown || haveButtonImage(mOverImage, mOverRect)
                || (!mColors.mLabelHilite.equals(mColors.mLabel))) {
            markDirty();
        }

        mButtonListener.buttonMouseLeave(mId);
    }

    // ButtonWidget::MouseMove (ButtonWidget.cpp:267)
    @Override
    public void mouseMove(int theX, int theY) {
        super.mouseMove(theX, theY);

        mButtonListener.buttonMouseMove(mId, theX, theY);
    }

    // ButtonWidget::MouseDown (ButtonWidget.cpp:274)
    @Override
    public void mouseDown(int theX, int theY, int theClickCount) {
        super.mouseDown(theX, theY, theClickCount);
    }

    // ButtonWidget::MouseDown (ButtonWidget.cpp:275)
    @Override
    public void mouseDown(int theX, int theY, int theBtnNum, int theClickCount) {
        super.mouseDown(theX, theY, theBtnNum, theClickCount);

        mButtonListener.buttonPress(mId);

        markDirty();
    }

    // ButtonWidget::MouseUp (ButtonWidget.cpp:283)
    @Override
    public void mouseUp(int theX, int theY) {
        super.mouseUp(theX, theY);
    }

    // ButtonWidget::MouseUp (ButtonWidget.cpp:284)
    @Override
    public void mouseUp(int theX, int theY, int theClickCount) {
        super.mouseUp(theX, theY, theClickCount);
    }

    // ButtonWidget::MouseUp (ButtonWidget.cpp:285)
    @Override
    public void mouseUp(int theX, int theY, int theBtnNum, int theClickCount) {
        super.mouseUp(theX, theY, theBtnNum, theClickCount);

        if (mIsOver && mWidgetManager.mHasFocus) {
            mButtonListener.buttonDepress(mId);
        }

        markDirty();
    }

    // ButtonWidget::Update (ButtonWidget.cpp:294)
    @Override
    public void update() {
        super.update();

        if (mIsDown && mIsOver) {
            mButtonListener.buttonDownTick(mId);
        }

        if (!mIsDown && !mIsOver && (mOverAlpha > 0)) {
            if (mOverAlphaSpeed > 0) {
                mOverAlpha -= mOverAlphaSpeed;
                if (mOverAlpha < 0) {
                    mOverAlpha = 0;
                }
            } else {
                mOverAlpha = 0;
            }

            markDirty();
        } else if (mIsOver && mOverAlphaFadeInSpeed > 0 && mOverAlpha < 1) {
            mOverAlpha += mOverAlphaFadeInSpeed;
            if (mOverAlpha > 1) {
                mOverAlpha = 1;
            }
            markDirty();
        }
    }
}
