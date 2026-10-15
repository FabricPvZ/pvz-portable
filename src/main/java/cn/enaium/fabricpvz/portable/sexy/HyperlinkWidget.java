package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/SexyAppFramework/widget/HyperlinkWidget.h:33 (implementation
 * src/SexyAppFramework/widget/HyperlinkWidget.cpp:31).
 *
 * <p>A label-only button drawn as a hyperlink: unlike {@link ButtonWidget} it ignores the bevel
 * frame and the button images, and instead draws the label in {@code mColor} (or {@code mOverColor}
 * when the mouse is over it) with an underline of {@code mUnderlineSize} rows.
 */
public class HyperlinkWidget extends ButtonWidget {
    // HyperlinkWidget.h:36-39
    public Color mColor;
    public Color mOverColor;
    public int mUnderlineSize;
    public int mUnderlineOffset;

    // HyperlinkWidget::HyperlinkWidget (HyperlinkWidget.cpp:31)
    public HyperlinkWidget(int theId, ButtonListener theButtonListener) {
        super(theId, theButtonListener);

        mColor = new Color(255, 255, 255);
        mOverColor = new Color(255, 255, 255);

        mDoFinger = true;
        mUnderlineOffset = 3;
        mUnderlineSize = 1;
    }

    // HyperlinkWidget::Draw (HyperlinkWidget.cpp:41)
    @Override
    public void draw(Graphics g) {
        Font aDefaultFont = mWidgetManager.mApp.mDefaultFont;
        if ((mFont == null) && (aDefaultFont != null)) {
            mFont = aDefaultFont.duplicate();
        }
        if (mFont == null) {
            return;
        }

        int aFontX = (mWidth - mFont.stringWidth(mLabel)) / 2;
        int aFontY = (mHeight + mFont.getAscent()) / 2 - 1;

        if (mIsOver) {
            g.setColor(mOverColor);
        } else {
            g.setColor(mColor);
        }

        g.setFont(mFont);
        g.drawString(mLabel, aFontX, aFontY);

        for (int i = 0; i < mUnderlineSize; i++) {
            g.fillRect(aFontX, aFontY + mUnderlineOffset + i, mFont.stringWidth(mLabel), 1);
        }
    }

    // HyperlinkWidget::MouseEnter (HyperlinkWidget.cpp:64)
    @Override
    public void mouseEnter() {
        super.mouseEnter();

        markDirtyFull();
    }

    // HyperlinkWidget::MouseLeave (HyperlinkWidget.cpp:71)
    @Override
    public void mouseLeave() {
        super.mouseLeave();

        markDirtyFull();
    }
}
