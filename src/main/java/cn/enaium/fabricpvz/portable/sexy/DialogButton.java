package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/SexyAppFramework/widget/DialogButton.h:33 (implementation
 * src/SexyAppFramework/widget/DialogButton.cpp:37).
 *
 * <p>A button whose background is a 9-slice component image, used by dialogs. When pressed the whole
 * button is translated by {@code (mTranslateX, mTranslateY)} and then translated back once the
 * label has been drawn.
 */
public class DialogButton extends ButtonWidget {
    // gDialogButtonColors (DialogButton.cpp:38-45)
    private static final ButtonColorScheme gDialogButtonColors = new ButtonColorScheme(
            new Color(255, 255, 255),
            new Color(255, 255, 255),
            new Color(0, 0, 0),
            new Color(255, 255, 255),
            new Color(132, 132, 132),
            new Color(212, 212, 212));

    // DialogButton.h:36-38
    public Image mComponentImage;
    public int mTranslateX;
    public int mTranslateY;
    public int mTextOffsetX;
    public int mTextOffsetY;

    // DialogButton::DialogButton (DialogButton.cpp:37)
    public DialogButton(Image theComponentImage, int theId, ButtonListener theListener) {
        super(theId, theListener);

        mComponentImage = theComponentImage;

        mTextOffsetX = 0;
        mTextOffsetY = 0;
        mTranslateX = 1;
        mTranslateY = 1;
        mDoFinger = true;

        setColors(gDialogButtonColors);
    }

    // DialogButton::Draw (DialogButton.cpp:54)
    @Override
    public void draw(Graphics g) {
        if (mBtnNoDraw) {
            return;
        }

        if (mComponentImage == null) {
            super.draw(g);
            return;
        }

        Font aDefaultFont = mWidgetManager.mApp.mDefaultFont;
        if ((mFont == null) && (mLabel.length() > 0) && (aDefaultFont != null)) {
            mFont = aDefaultFont.duplicate();
        }

        boolean doTranslate = isButtonDown();

        if (mNormalRect.mWidth == 0) {
            if (doTranslate) {
                g.translate(mTranslateX, mTranslateY);
            }

            g.drawImageBox(new Rect(0, 0, mWidth, mHeight), mComponentImage);
        } else {
            if (mDisabled && (mDisabledRect.mWidth > 0) && (mDisabledRect.mHeight > 0)) {
                g.drawImageBox(mDisabledRect, new Rect(0, 0, mWidth, mHeight), mComponentImage);
            } else if (isButtonDown()) {
                g.drawImageBox(mDownRect, new Rect(0, 0, mWidth, mHeight), mComponentImage);
            } else if (mOverAlpha > 0) {
                if (mOverAlpha < 1) {
                    g.drawImageBox(mNormalRect, new Rect(0, 0, mWidth, mHeight), mComponentImage);
                }

                g.setColorizeImages(true);
                g.setColor(new Color(255, 255, 255, (int) (mOverAlpha * 255)));
                g.drawImageBox(mOverRect, new Rect(0, 0, mWidth, mHeight), mComponentImage);
                g.setColorizeImages(false);
            } else if (mIsOver) {
                g.drawImageBox(mOverRect, new Rect(0, 0, mWidth, mHeight), mComponentImage);
            } else {
                g.drawImageBox(mNormalRect, new Rect(0, 0, mWidth, mHeight), mComponentImage);
            }

            if (doTranslate) {
                g.translate(mTranslateX, mTranslateY);
            }
        }

        if (mFont != null) {
            g.setFont(mFont);

            if (mIsOver) {
                g.setColor(mColors.mLabelHilite);
            } else {
                g.setColor(mColors.mLabel);
            }

            int aFontX = (mWidth - mFont.stringWidth(mLabel)) / 2;
            int aFontY = (mHeight + mFont.getAscent() - mFont.getAscentPadding()
                    - mFont.getAscent() / 6 - 1) / 2;

            g.drawString(mLabel, aFontX + mTextOffsetX, aFontY + mTextOffsetY);
        }

        if (doTranslate) {
            g.translate(-mTranslateX, -mTranslateY);
        }
    }
}
