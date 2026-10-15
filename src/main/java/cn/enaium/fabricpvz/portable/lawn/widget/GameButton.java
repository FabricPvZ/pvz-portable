/*
 * Ported from src/Lawn/Widget/GameButton.h:23-91 and src/Lawn/Widget/GameButton.cpp:32-282.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget.ButtonColorScheme;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.sexy.WidgetManager;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/GameButton.h:23-91 (implementation
 * src/Lawn/Widget/GameButton.cpp:32-282).
 *
 * <p>A hand-rolled button drawn inside its parent's {@code Draw()} (upstream it is deliberately
 * <em>not</em> a {@code Sexy::Widget}). It owns a normal/hover/down/disabled image state machine,
 * an additive hover overlay, an optional hover-alpha ramp and a stone 3-slice rendering mode, and
 * it draws its {@code mLabel} (translated through {@code PvzpStringTranslate}) with
 * {@code mColors.mLabel}/{@code mColors.mLabelHilite}.
 *
 * <p>The upstream free functions {@code DrawStoneButton}, {@code MakeButton} and {@code MakeNewButton}
 * are static methods here.
 */
public class GameButton {

    // GameButton.h:27-32
    public static final int BUTTON_LABEL_LEFT = -1;
    public static final int BUTTON_LABEL_CENTER = 0;
    public static final int BUTTON_LABEL_RIGHT = 1;

    // GameButton.cpp:32-39 (gGameButtonColors)
    private static final ButtonColorScheme gGameButtonColors = new ButtonColorScheme(
            new Color(0, 0, 0),
            new Color(0, 0, 0),
            new Color(0, 0, 0),
            new Color(255, 255, 255),
            new Color(132, 132, 132),
            new Color(212, 212, 212));

    // GameButton.h:41-75
    public LawnApp mApp;
    public Widget mParentWidget;
    public int mX;
    public int mY;
    public int mWidth;
    public int mHeight;
    public boolean mIsOver;
    public boolean mIsDown;
    public boolean mDisabled;
    public ButtonColorScheme mColors;
    public int mId;
    public String mLabel;
    public int mLabelJustify;
    public Font mFont;
    public Image mButtonImage;
    public Image mOverImage;
    public Image mDownImage;
    public Image mDisabledImage;
    public Image mOverOverlayImage;
    public Rect mNormalRect;
    public Rect mOverRect;
    public Rect mDownRect;
    public Rect mDisabledRect;
    public boolean mInverted;
    public boolean mBtnNoDraw;
    public boolean mFrameNoDraw;
    public double mOverAlpha;
    public double mOverAlphaSpeed;
    public double mOverAlphaFadeInSpeed;
    public boolean mDrawStoneButton;
    public int mTextOffsetX;
    public int mTextOffsetY;
    public int mButtonOffsetX;
    public int mButtonOffsetY;

    // GameButton::DrawStoneButton (GameButton.cpp:41)
    public static void drawStoneButton(Graphics g, int x, int y, int theWidth, int theHeight,
            boolean isDown, boolean isHighLighted, String theLabel) {
        Image aLeftImage = Resources.getImageById(ImageID.IMAGE_BUTTON_LEFT);
        Image aMiddleImage = Resources.getImageById(ImageID.IMAGE_BUTTON_MIDDLE);
        Image aRightImage = Resources.getImageById(ImageID.IMAGE_BUTTON_RIGHT);
        int aFontX = x;
        int aFontY = y;
        int aImageX = x;
        if (isDown) {
            aLeftImage = Resources.getImageById(ImageID.IMAGE_BUTTON_DOWN_LEFT);
            aMiddleImage = Resources.getImageById(ImageID.IMAGE_BUTTON_DOWN_MIDDLE);
            aRightImage = Resources.getImageById(ImageID.IMAGE_BUTTON_DOWN_RIGHT);
            aFontX++;
            aFontY++;
            aImageX++;
        }

        int aRepeat = (theWidth - aLeftImage.getWidth() - aRightImage.getWidth())
                / aMiddleImage.getWidth();
        g.drawImage(aLeftImage, aImageX, y);
        aImageX += aLeftImage.getWidth();
        while (aRepeat > 0) {
            g.drawImage(aMiddleImage, aImageX, y);
            aImageX += aMiddleImage.getWidth();
            --aRepeat;
        }
        g.drawImage(aRightImage, aImageX, y);

        g.setFont(isHighLighted
                ? Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18BRIGHTGREENINSET_ID)
                : Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID));
        Font aMeasureFont = Resources.getFontById(Resources.FONT_DWARVENTODCRAFT18GREENINSET_ID);
        aFontX += (theWidth - aMeasureFont.stringWidth(theLabel)) / 2 + 1;
        aFontY += (theHeight - aMeasureFont.getAscent() / 6 - 1 + aMeasureFont.getAscent()) / 2 - 4;
        g.setColor(Color.White);
        g.drawString(theLabel, aFontX, aFontY);
    }

    // GameButton::GameButton (GameButton.cpp:77)
    public GameButton(int theId) {
        mLabel = "";
        mNormalRect = new Rect();
        mOverRect = new Rect();
        mDownRect = new Rect();
        mDisabledRect = new Rect();
        mOverAlpha = 0;
        mOverAlphaSpeed = 0;
        mOverAlphaFadeInSpeed = 0;
        mApp = LawnApp.gLawnApp;
        mId = theId;
        mLabelJustify = 0;
        mFont = null;
        mButtonImage = null;
        mOverImage = null;
        mDownImage = null;
        mDisabledImage = null;
        mOverOverlayImage = null;
        mInverted = false;
        mBtnNoDraw = false;
        mFrameNoDraw = false;
        mIsOver = false;
        mIsDown = false;
        mDisabled = false;
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
        mParentWidget = null;
        mDrawStoneButton = false;
        mTextOffsetX = 0;
        mTextOffsetY = 0;
        mButtonOffsetX = 0;
        mButtonOffsetY = 0;
        mColors = new ButtonColorScheme(gGameButtonColors);
    }

    // GameButton::SetLabelColor (GameButton.cpp:98)
    public void setLabelColor(Color theColor) {
        mColors.mLabel = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue, theColor.mAlpha);
    }

    // GameButton::SetLabelHiliteColor (GameButton.cpp:103)
    public void setLabelHiliteColor(Color theColor) {
        mColors.mLabelHilite = new Color(theColor.mRed, theColor.mGreen, theColor.mBlue,
                theColor.mAlpha);
    }

    // GameButton::HaveButtonImage (GameButton.cpp:108)
    public static boolean haveButtonImage(Image theImage, Rect theRect) {
        return theImage != null || theRect.mWidth != 0;
    }

    // GameButton::DrawButtonImage (GameButton.cpp:113)
    public void drawButtonImage(Graphics g, Image theImage, Rect theRect, int theX, int theY) {
        int aPosX = theX + mButtonOffsetX;
        int aPosY = theY + mButtonOffsetY;
        if (theRect.mWidth != 0) {
            g.drawImage(mButtonImage, aPosX, aPosY, theRect);
        } else {
            g.drawImage(theImage, aPosX, aPosY);
        }
    }

    // GameButton::SetDisabled (GameButton.cpp:123)
    public void setDisabled(boolean theDisabled) {
        mDisabled = theDisabled;
    }

    // GameButton::SetFont (GameButton.cpp:128)
    public void setFont(Font theFont) {
        mFont = theFont.duplicate();
    }

    // GameButton::IsButtonDown (GameButton.cpp:133)
    public boolean isButtonDown() {
        return mIsDown && mIsOver && !mDisabled && !mBtnNoDraw;
    }

    // GameButton::Draw (GameButton.cpp:138)
    public void draw(Graphics g) {
        if (mBtnNoDraw) {
            return;
        }

        boolean isDown = isButtonDown() ^ mInverted;
        boolean isHighLighted = isMouseOver();
        if (mDrawStoneButton) {
            drawStoneButton(g, mX, mY, mWidth, mHeight, isDown, isHighLighted, mLabel);
            return;
        }

        g.mTransX += mX;
        g.mTransY += mY;
        if (mFont == null && mLabel.length() > 0) {
            mFont = Resources.getFontById(Resources.FONT_PICO129_ID).duplicate();
        }

        int aFontX = mTextOffsetX;
        int aFontY = mTextOffsetY;
        if (mFont != null) {
            if (mLabelJustify == BUTTON_LABEL_CENTER) {
                aFontX += (mWidth - mFont.stringWidth(mLabel)) / 2;
            } else if (mLabelJustify == BUTTON_LABEL_RIGHT) {
                aFontX += mWidth - mFont.stringWidth(mLabel);
            }

            aFontY += (mHeight - mFont.getAscent() / 6 + mFont.getAscent() - 1) / 2;
        }
        g.setFont(mFont);

        if (!isDown) {
            if (mDisabled && haveButtonImage(mDisabledImage, mDisabledRect)) {
                drawButtonImage(g, mDisabledImage, mDisabledRect, 0, 0);
            } else if (mOverAlpha > 0.0f && haveButtonImage(mOverImage, mOverRect)) {
                // fade transition not finished
                if (haveButtonImage(mButtonImage, mNormalRect) && mOverAlpha < 1.0f) {
                    drawButtonImage(g, mButtonImage, mNormalRect, 0, 0);
                }

                g.setColorizeImages(true);
                g.setColor(new Color(255, 255, 255, (int) (mOverAlpha * 255)));
                drawButtonImage(g, mOverImage, mOverRect, 0, 0);
                g.setColorizeImages(false);
            } else if (isHighLighted && haveButtonImage(mOverImage, mOverRect)) {
                drawButtonImage(g, mOverImage, mOverRect, 0, 0);
            } else if (haveButtonImage(mButtonImage, mNormalRect)) {
                drawButtonImage(g, mButtonImage, mNormalRect, 0, 0);
            }

            g.setColor(isHighLighted ? mColors.mLabelHilite : mColors.mLabel);
            g.drawString(mLabel, aFontX, aFontY);

            if (isHighLighted && mOverOverlayImage != null) {
                g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                drawButtonImage(g, mOverOverlayImage, mNormalRect, 0, 0);
                g.setDrawMode(Graphics.DRAWMODE_NORMAL);
            }
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

            if (isHighLighted && mOverOverlayImage != null) {
                g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                drawButtonImage(g, mOverOverlayImage, mNormalRect, 0, 0);
                g.setDrawMode(Graphics.DRAWMODE_NORMAL);
            }
        }

        g.mTransX -= mX;
        g.mTransY -= mY;
    }

    // GameButton::Resize (GameButton.cpp:221)
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        mX = theX;
        mY = theY;
        mWidth = theWidth;
        mHeight = theHeight;
    }

    // GameButton::IsMouseOver (GameButton.cpp:229)
    public boolean isMouseOver() {
        if (mDisabled || mBtnNoDraw) {
            return false;
        }

        WidgetManager aManager = mApp.mWidgetManager;
        boolean aFocusMatches = aManager.mFocusWidget != null && aManager.mFocusWidget == mParentWidget;
        if (!aFocusMatches && mApp.getDialogCount() > 0) {
            return false;
        }

        int aMouseX = aManager.mLastMouseX;
        int aMouseY = aManager.mLastMouseY;
        if (mParentWidget != null) {
            Point anAbsPos = mParentWidget.getAbsPos();
            aMouseX -= anAbsPos.mX;
            aMouseY -= anAbsPos.mY;
        }
        return new Rect(mX, mY, mWidth, mHeight).contains(aMouseX, aMouseY);
    }

    // GameButton::Update (GameButton.cpp:249)
    public void update() {
        WidgetManager aManager = mApp.mWidgetManager;
        mIsOver = isMouseOver();

        boolean aFocusMatches = aManager.mFocusWidget != null && aManager.mFocusWidget == mParentWidget;
        if (aFocusMatches || mApp.getDialogCount() <= 0) {
            mIsDown = (aManager.mDownButtons & 5) != 0;
        } else {
            mIsDown = false;
        }

        if (!mIsDown && !mIsOver && mOverAlpha > 0) {
            if (mOverAlphaSpeed < 0) {
                mOverAlpha = 0;
                return;
            }
            mOverAlpha -= mOverAlphaSpeed;
            if (mOverAlpha < 0) {
                mOverAlpha = 0;
            }
        } else if (mIsOver && mOverAlphaFadeInSpeed > 0 && mOverAlpha < 1) {
            if (mOverAlphaFadeInSpeed > 0) {
                mOverAlpha += mOverAlphaFadeInSpeed;
                if (mOverAlpha > 1) {
                    mOverAlpha = 1;
                }
            } else {
                mOverAlpha = 1;
            }
        }
    }

    // GameButton::SetLabel (GameButton.cpp:286)
    public void setLabel(String theLabel) {
        mLabel = PvzpStringFile.translate(theLabel);
    }

    // MakeButton (GameButton.cpp:306)
    public static LawnStoneButton makeButton(int theId, ButtonListener theListener, String theText) {
        LawnStoneButton aButton = new LawnStoneButton(null, theId, theListener);
        aButton.setLabel(theText);

        aButton.mTranslateX = 1;
        aButton.mTranslateY = 1;
        aButton.mHasAlpha = true;
        aButton.mHasTransparencies = true;
        aButton.mHeight = 33;
        return aButton;
    }

    // MakeNewButton (GameButton.cpp:411)
    public static NewLawnButton makeNewButton(int theId, ButtonListener theListener, String theText,
            Font theFont, Image theImageNormal, Image theImageOver, Image theImageDown) {
        NewLawnButton aButton = new NewLawnButton(null, theId, theListener);
        aButton.setFont(theFont == null
                ? Resources.getFontById(Resources.FONT_BRIANNETOD12_ID) : theFont);
        aButton.setLabel(theText);

        aButton.mWidth = theImageNormal.getWidth();
        aButton.mHeight = theImageNormal.getHeight();
        aButton.mButtonImage = theImageNormal;
        aButton.mDownImage = theImageDown;
        aButton.mOverImage = theImageOver;
        aButton.mHasAlpha = true;
        aButton.mHasTransparencies = true;
        aButton.mTranslateX = 1;
        aButton.mTranslateY = 1;
        return aButton;
    }
}
