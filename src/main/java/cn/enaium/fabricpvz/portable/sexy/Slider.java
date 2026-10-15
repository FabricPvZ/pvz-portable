package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/SexyAppFramework/widget/Slider.h:35 (implementation
 * src/SexyAppFramework/widget/Slider.cpp:34).
 *
 * <p>A draggable slider whose value is normalised to the range [0, 1]. The track image is a
 * three-slice (start / repeatable middle / end) image; the thumb is drawn at the current value.
 */
public class Slider extends Widget {

    // Slider.h:37
    public SliderListener mListener;

    // Slider.h:38
    public double mVal;

    // Slider.h:39
    public int mId;

    // Slider.h:40
    public Image mTrackImage;

    // Slider.h:41
    public Image mThumbImage;

    // Slider.h:43
    public boolean mDragging;

    // Slider.h:44
    public int mRelX;

    // Slider.h:45
    public int mRelY;

    // Slider.h:47
    public boolean mHorizontal;

    // Slider::Slider (Slider.cpp:34)
    public Slider(Image theTrackImage, Image theThumbImage, int theId, SliderListener theListener) {
        mListener = theListener;
        mVal = 0.0;
        mId = theId;
        mTrackImage = theTrackImage;
        mThumbImage = theThumbImage;

        mDragging = false;
        mHorizontal = true;
        mRelX = mRelY = 0;
    }

    // Slider::SetValue (Slider.cpp:46)
    public void setValue(double theValue) {
        mVal = theValue;
        if (mVal < 0.0) {
            mVal = 0.0;
        } else if (mVal > 1.0) {
            mVal = 1.0;
        }

        markDirtyFull();
    }

    // Slider::HasTransparencies (Slider.cpp:57)
    public boolean hasTransparencies() {
        return true;
    }

    // Slider::Draw (Slider.cpp:62)
    @Override
    public void draw(Graphics g) {
        if (mTrackImage != null) {
            int cw = mHorizontal ? mTrackImage.getWidth() / 3 : mTrackImage.getWidth();
            int ch = mHorizontal ? mTrackImage.getHeight() : mTrackImage.getHeight() / 3;

            if (mHorizontal) {
                int ty = (mHeight - ch) / 2;

                g.drawImage(mTrackImage, 0, ty, new Rect(0, 0, cw, ch));

                Graphics aClipG = new Graphics(g);
                aClipG.clipRect(cw, ty, mWidth - cw * 2, ch);
                for (int i = 0; i < (mWidth - cw * 2 + cw - 1) / cw; i++) {
                    aClipG.drawImage(mTrackImage, cw + i * cw, ty, new Rect(cw, 0, cw, ch));
                }

                g.drawImage(mTrackImage, mWidth - cw, ty, new Rect(cw * 2, 0, cw, ch));
            } else {
                g.drawImage(mTrackImage, 0, 0, new Rect(0, 0, cw, ch));

                Graphics aClipG = new Graphics(g);
                aClipG.clipRect(0, ch, cw, mHeight - ch * 2);
                for (int i = 0; i < (mHeight - ch * 2 + ch - 1) / ch; i++) {
                    aClipG.drawImage(mTrackImage, 0, ch + i * ch, new Rect(0, ch, cw, ch));
                }

                g.drawImage(mTrackImage, 0, mHeight - ch, new Rect(0, ch * 2, cw, ch));
            }
        }

        if (mHorizontal && (mThumbImage != null)) {
            g.drawImage(mThumbImage, (int) (mVal * (mWidth - mThumbImage.getWidth())),
                    (mHeight - mThumbImage.getHeight()) / 2);
        } else if (!mHorizontal && (mThumbImage != null)) {
            g.drawImage(mThumbImage, (mWidth - mThumbImage.getWidth()) / 2,
                    (int) (mVal * (mHeight - mThumbImage.getHeight())));
        }
    }

    // Slider::MouseDown (Slider.cpp:103)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        if (mHorizontal) {
            int aThumbX = (int) (mVal * (mWidth - mThumbImage.getWidth()));

            if ((x >= aThumbX) && (x < aThumbX + mThumbImage.getWidth())) {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_DRAGGING);
                mDragging = true;
                mRelX = x - aThumbX;
            } else {
                // clicked on the bar, set position to mouse click
                double pos = (double) x / mWidth;
                setValue(pos);
            }
        } else {
            int aThumbY = (int) (mVal * (mHeight - mThumbImage.getHeight()));

            if ((y >= aThumbY) && (y < aThumbY + mThumbImage.getHeight())) {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_DRAGGING);
                mDragging = true;
                mRelY = y - aThumbY;
            } else {
                // clicked on the bar, set position to mouse click
                double pos = (double) y / mHeight;
                setValue(pos);
            }
        }
    }

    // Slider::MouseMove (Slider.cpp:141)
    @Override
    public void mouseMove(int x, int y) {
        if (mHorizontal) {
            int aThumbX = (int) (mVal * (mWidth - mThumbImage.getWidth()));

            if ((x >= aThumbX) && (x < aThumbX + mThumbImage.getWidth())) {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_DRAGGING);
            } else {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
            }
        } else {
            int aThumbY = (int) (mVal * (mHeight - mThumbImage.getHeight()));

            if ((y >= aThumbY) && (y < aThumbY + mThumbImage.getHeight())) {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_DRAGGING);
            } else {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
            }
        }
    }

    // Slider::MouseDrag (Slider.cpp:163)
    @Override
    public void mouseDrag(int x, int y) {
        if (mDragging) {
            double anOldVal = mVal;

            if (mHorizontal) {
                mVal = (x - mRelX) / (double) (mWidth - mThumbImage.getWidth());
            } else {
                mVal = (y - mRelY) / (double) (mHeight - mThumbImage.getHeight());
            }

            if (mVal < 0.0) {
                mVal = 0.0;
            }
            if (mVal > 1.0) {
                mVal = 1.0;
            }

            if (mVal != anOldVal) {
                mListener.sliderVal(mId, mVal);
                markDirtyFull();
            }
        }
    }

    // Slider::MouseUp (Slider.cpp:187)
    @Override
    public void mouseUp(int x, int y) {
        mDragging = false;
        mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        mListener.sliderVal(mId, mVal);
    }

    // Slider::MouseLeave (Slider.cpp:194)
    @Override
    public void mouseLeave() {
        if (!mDragging) {
            mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
    }
}
