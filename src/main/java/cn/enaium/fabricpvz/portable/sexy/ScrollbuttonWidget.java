package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/SexyAppFramework/widget/ScrollbuttonWidget.h:35 (implementation
 * src/SexyAppFramework/widget/ScrollbuttonWidget.cpp:31).
 *
 * <p>A bevel-framed arrow button used by {@link ScrollbarWidget}. {@code mType} (1 = up, 2 = down,
 * 3 = left, 4 = right) overrides {@code mHorizontal} and {@code mId}.
 */
public class ScrollbuttonWidget extends ButtonWidget {
    // ScrollbuttonWidget.h:38
    public boolean mHorizontal;

    // ScrollbuttonWidget.h:42
    public int mType;

    // ScrollbuttonWidget::ScrollbuttonWidget (ScrollbuttonWidget.cpp:31). Upstream's theType is
    // defaulted to 0; Java has no default arguments, so every caller passes it explicitly.
    public ScrollbuttonWidget(int theId, ButtonListener theButtonListener, int theType) {
        super(theId, theButtonListener);

        mHorizontal = false;
        mType = theType;
    }

    // ScrollbuttonWidget::~ScrollbuttonWidget (ScrollbuttonWidget.cpp:37)
    // Upstream default destructor; the Java port relies on GC.

    // ScrollbuttonWidget::Draw (ScrollbuttonWidget.cpp:39)
    @Override
    public void draw(Graphics g) {
        int anArrowOffset = 0;

        g.setColor(new Color(212, 212, 212));
        g.fillRect(0, 0, mWidth, mHeight);

        if (mIsDown && mIsOver && !mDisabled) {
            anArrowOffset = 1;
            g.setColor(new Color(132, 132, 132));
            g.drawRect(0, 0, mWidth - 1, mHeight - 1);
        } else {
            g.setColor(new Color(255, 255, 255));
            g.fillRect(1, 1, mWidth - 2, 1);
            g.fillRect(1, 1, 1, mHeight - 2);

            g.setColor(Color.Black);
            g.fillRect(0, mHeight - 1, mWidth, 1);
            g.fillRect(mWidth - 1, 0, 1, mHeight);

            g.setColor(new Color(132, 132, 132));
            g.fillRect(1, mHeight - 2, mWidth - 2, 1);
            g.fillRect(mWidth - 2, 1, 1, mHeight - 2);
        }

        if (!mDisabled) {
            g.setColor(Color.Black);
        } else {
            g.setColor(new Color(132, 132, 132));
        }

        if (mHorizontal || (mType == 3 || mType == 4)) {
            for (int i = 0; i < 4; i++) {
                if (mId == 0 || mType == 3) {
                    g.fillRect(i + (mWidth - 4) / 2 + anArrowOffset,
                            mHeight / 2 - i - 1 + anArrowOffset, 1, 1 + i * 2);
                } else {
                    g.fillRect((3 - i) + (mWidth - 4) / 2 + anArrowOffset,
                            mHeight / 2 - i - 1 + anArrowOffset, 1, 1 + i * 2);
                }
            }
        } else {
            for (int i = 0; i < 4; i++) {
                if (mId == 0 || mType == 1) {
                    g.fillRect(mWidth / 2 - i - 1 + anArrowOffset,
                            i + (mHeight - 4) / 2 + anArrowOffset, 1 + i * 2, 1);
                } else {
                    g.fillRect(mWidth / 2 - i - 1 + anArrowOffset,
                            (3 - i) + (mHeight - 4) / 2 + anArrowOffset, 1 + i * 2, 1);
                }
            }
        }
    }
}
