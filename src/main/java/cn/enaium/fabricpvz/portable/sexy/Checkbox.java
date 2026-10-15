package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/SexyAppFramework/widget/Checkbox.h:36 (implementation
 * src/SexyAppFramework/widget/Checkbox.cpp:31).
 *
 * <p>A two-state checkbox. When no images are supplied the widget draws itself from the outline,
 * background and check colours.
 */
public class Checkbox extends Widget {

    // Checkbox.h:38
    protected CheckboxListener mListener;

    // Checkbox.h:41
    public int mId;

    // Checkbox.h:43
    public boolean mChecked;

    // Checkbox.h:45
    public Image mUncheckedImage;

    // Checkbox.h:46
    public Image mCheckedImage;

    // Checkbox.h:48
    public Rect mCheckedRect = new Rect();

    // Checkbox.h:49
    public Rect mUncheckedRect = new Rect();

    // Checkbox.h:51 — only used if no image is specified
    public Color mOutlineColor;

    // Checkbox.h:52
    public Color mBkgColor;

    // Checkbox.h:53
    public Color mCheckColor;

    // Checkbox::Checkbox (Checkbox.cpp:31)
    public Checkbox(Image theUncheckedImage, Image theCheckedImage, int theId,
            CheckboxListener theCheckboxListener) {
        mListener = theCheckboxListener;
        mId = theId;
        mChecked = false;
        mUncheckedImage = theUncheckedImage;
        mCheckedImage = theCheckedImage;
        mOutlineColor = Color.White;
        mBkgColor = new Color(80, 80, 80);
        mCheckColor = new Color(255, 255, 0);

        mDoFinger = true;
    }

    // Checkbox::SetChecked (Checkbox.cpp:44)
    public void setChecked(boolean checked, boolean tellListener) {
        mChecked = checked;
        if (tellListener && (mListener != null)) {
            mListener.checkboxChecked(mId, mChecked);
        }
        markDirty();
    }

    // Checkbox::IsChecked (Checkbox.cpp:52)
    public boolean isChecked() {
        return mChecked;
    }

    // Checkbox::Draw (Checkbox.cpp:57)
    @Override
    public void draw(Graphics g) {
        super.draw(g);

        if ((mCheckedRect.mWidth == 0) && (mCheckedImage != null) && (mUncheckedImage != null)) {
            if (mChecked) {
                g.drawImage(mCheckedImage, 0, 0);
            } else {
                g.drawImage(mUncheckedImage, 0, 0);
            }
        } else if ((mCheckedRect.mWidth != 0) && (mUncheckedImage != null)) {
            if (mChecked) {
                g.drawImage(mUncheckedImage, 0, 0, mCheckedRect);
            } else {
                g.drawImage(mUncheckedImage, 0, 0, mUncheckedRect);
            }
        } else if ((mUncheckedImage == null) && (mCheckedImage == null)) {
            // No image, default draw method
            g.setColor(mOutlineColor);
            g.fillRect(0, 0, mWidth, mHeight);
            g.setColor(mBkgColor);
            g.fillRect(1, 1, mWidth - 2, mHeight - 2);

            if (mChecked) {
                g.setColor(mCheckColor);
                g.drawLine(1, 1, mWidth - 2, mHeight - 2);
                g.drawLine(mWidth - 1, 1, 1, mHeight - 2);
            }
        }
    }

    // Checkbox::MouseDown (Checkbox.h:55) — forwards to the click-count dispatcher
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);
    }

    // Checkbox::MouseDown (Checkbox.cpp:92)
    @Override
    public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
        super.mouseDown(x, y, theBtnNum, theClickCount);

        mChecked = !mChecked;
        if (mListener != null) {
            mListener.checkboxChecked(mId, mChecked);
        }
        markDirty();
    }
}
