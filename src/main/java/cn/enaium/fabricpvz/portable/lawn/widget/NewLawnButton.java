/*
 * Ported from src/Lawn/Widget/GameButton.h:104-122 and src/Lawn/Widget/GameButton.cpp:321-409.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.DialogButton;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/Widget/GameButton.h:104-122 (implementation
 * src/Lawn/Widget/GameButton.cpp:321-409).
 *
 * <p>An image-based dialog button: normal/hover/down/disabled images, a hover-alpha ramp, an
 * optional highlight font and an optional polygon hit shape.
 */
public class NewLawnButton extends DialogButton {

    // GameButton.h:106-113
    public Font mHiliteFont;
    public int mTextDownOffsetX;
    public int mTextDownOffsetY;
    public int mButtonOffsetX;
    public int mButtonOffsetY;
    public boolean mUsePolygonShape;
    public SexyVector2[] mPolygonShape = new SexyVector2[4];

    // NewLawnButton::NewLawnButton (GameButton.cpp:321)
    public NewLawnButton(Image theComponentImage, int theId, ButtonListener theListener) {
        super(theComponentImage, theId, theListener);
        mHiliteFont = null;
        mTextDownOffsetX = 0;
        mTextDownOffsetY = 0;
        mButtonOffsetX = 0;
        mButtonOffsetY = 0;
        mUsePolygonShape = false;
        setBkgColor(Color.White);
    }

    // NewLawnButton::SetLabel (GameButton.cpp:289)
    public void setLabel(String theLabel) {
        mLabel = PvzpStringFile.translate(theLabel);
    }

    // NewLawnButton::Draw (GameButton.cpp:338)
    @Override
    public void draw(Graphics g) {
        if (mBtnNoDraw) {
            return;
        }

        boolean isDown = (mIsDown && mIsOver && !mDisabled) ^ mInverted;
        int aFontX = mTextOffsetX + mTranslateX;
        int aFontY = mTextOffsetY + mTranslateY;
        if (mFont != null) {
            if (mLabelJustify == ButtonWidget.BUTTON_LABEL_CENTER) {
                aFontX += (mWidth - mFont.stringWidth(mLabel)) / 2;
            } else if (mLabelJustify == ButtonWidget.BUTTON_LABEL_RIGHT) {
                aFontX += mWidth - mFont.stringWidth(mLabel);
            }

            aFontY += (mHeight - mFont.getAscent() / 6 + mFont.getAscent() - 1) / 2;
        }

        g.setColorizeImages(true);
        if (!isDown) {
            g.setColor(mColors.mBkg);
            if (mDisabled && haveButtonImage(mDisabledImage, mDisabledRect)) {
                drawButtonImage(g, mDisabledImage, mDisabledRect, mButtonOffsetX, mButtonOffsetY);
            } else if (mOverAlpha > 0.0f && haveButtonImage(mOverImage, mOverRect)) {
                // fade transition not finished
                if (haveButtonImage(mButtonImage, mNormalRect) && mOverAlpha < 1.0f) {
                    drawButtonImage(g, mButtonImage, mNormalRect, mButtonOffsetX, mButtonOffsetY);
                }

                g.mColor.mAlpha = (int) (mOverAlpha * 255);
                drawButtonImage(g, mOverImage, mOverRect, mButtonOffsetX, mButtonOffsetY);
            } else if ((mIsOver || mIsDown) && haveButtonImage(mOverImage, mOverRect)) {
                drawButtonImage(g, mOverImage, mOverRect, mButtonOffsetX, mButtonOffsetY);
            } else if (haveButtonImage(mButtonImage, mNormalRect)) {
                drawButtonImage(g, mButtonImage, mNormalRect, mButtonOffsetX, mButtonOffsetY);
            }

            g.setColorizeImages(false);
            if (mIsOver) {
                g.setFont(mHiliteFont != null ? mHiliteFont : mFont);
                g.setColor(mColors.mLabelHilite);
            } else {
                g.setFont(mFont);
                g.setColor(mColors.mLabel);
            }
            g.drawString(mLabel, aFontX, aFontY);
        } else {
            g.setColor(mColors.mBkg);
            if (haveButtonImage(mDownImage, mDownRect)) {
                drawButtonImage(g, mDownImage, mDownRect, mButtonOffsetX + mTranslateX,
                        mButtonOffsetY + mTranslateY);
            } else if (haveButtonImage(mOverImage, mOverRect)) {
                drawButtonImage(g, mOverImage, mOverRect, mButtonOffsetX + mTranslateX,
                        mButtonOffsetY + mTranslateY);
            } else {
                drawButtonImage(g, mButtonImage, mNormalRect, mButtonOffsetX + mTranslateX,
                        mButtonOffsetY + mTranslateY);
            }

            g.setColorizeImages(false);
            g.setFont(mHiliteFont != null ? mHiliteFont : mFont);
            g.setColor(mColors.mLabelHilite);
            g.drawString(mLabel, aFontX + mTextDownOffsetX, aFontY + mTextDownOffsetY);
        }
    }

    // NewLawnButton::IsPointVisible (GameButton.cpp:405)
    @Override
    public boolean isPointVisible(int x, int y) {
        if (!mUsePolygonShape) {
            return super.isPointVisible(x, y);
        }

        return PvzpCommon.pvzpIsPointInPolygon(mPolygonShape, 4, new SexyVector2(x, y));
    }
}
