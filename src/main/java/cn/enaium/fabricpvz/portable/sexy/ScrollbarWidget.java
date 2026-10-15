package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/SexyAppFramework/widget/ScrollbarWidget.h:40 (implementation
 * src/SexyAppFramework/widget/ScrollbarWidget.cpp:32).
 *
 * <p>A scrollbar built from two {@link ScrollbuttonWidget} arrow buttons and a draggable thumb. It
 * reports position changes through a {@link ScrollListener}, and (when
 * {@code mInvisIfNoScroll} is set) hides itself while the content fits.
 *
 * <p>PORT-NOTE: upstream declares {@code mValue}/{@code mMaxValue}/{@code mPageSize} as
 * {@code double} and {@code ScrollListener::ScrollPosition(int, double)}; the Java port keeps those
 * exact types.
 *
 * <p>PORT-NOTE: upstream calls {@code mScrollListener->ScrollPosition(...)} unguarded; the Java
 * port tolerates a null listener (as required by this widget slice).
 */
public class ScrollbarWidget extends Widget implements ButtonListener {
    // ScrollbarWidget.h:45-49 (enum)
    public static final int UPDATE_MODE_IDLE = 0;
    public static final int UPDATE_MODE_PGUP = 1;
    public static final int UPDATE_MODE_PGDN = 2;

    // ScrollbarWidget.h:54-56
    public ScrollbuttonWidget mUpButton;
    public ScrollbuttonWidget mDownButton;

    public boolean mInvisIfNoScroll;

    // ScrollbarWidget.h:60
    public int mId;

    // ScrollbarWidget.h:62-65
    public double mValue;
    public double mMaxValue;
    public double mPageSize;
    public boolean mHorizontal;

    // ScrollbarWidget.h:67-70
    public boolean mPressedOnThumb;
    public int mMouseDownThumbPos;
    public int mMouseDownX;
    public int mMouseDownY;

    // ScrollbarWidget.h:72-77
    public int mUpdateMode;
    public int mUpdateAcc;
    public int mButtonAcc;
    public int mLastMouseX;
    public int mLastMouseY;
    public ScrollListener mScrollListener;

    // ScrollbarWidget::ScrollbarWidget (ScrollbarWidget.cpp:32)
    public ScrollbarWidget(int theId, ScrollListener theScrollListener) {
        mId = theId;
        mScrollListener = theScrollListener;

        setDisabled(true);

        mUpButton = new ScrollbuttonWidget(0, this, 0);
        mUpButton.setDisabled(true);

        mDownButton = new ScrollbuttonWidget(1, this, 0);
        mDownButton.setDisabled(true);

        mInvisIfNoScroll = false;
        mPressedOnThumb = false;
        mValue = 0;
        mMaxValue = 0;
        mPageSize = 0;
        mUpdateAcc = 0;
        mButtonAcc = 0;
        mUpdateMode = 0;
        mHorizontal = false;

        addWidget(mUpButton);
        addWidget(mDownButton);
    }

    // ScrollbarWidget::~ScrollbarWidget (ScrollbarWidget.cpp:59)
    @Override
    public void dispose() {
        removeAllWidgets(true, false);

        super.dispose();
    }

    // ScrollbarWidget::SetInvisIfNoScroll (ScrollbarWidget.cpp:64)
    public void setInvisIfNoScroll(boolean invisIfNoScroll) {
        mInvisIfNoScroll = invisIfNoScroll;

        if (mInvisIfNoScroll) {
            setVisible(false);
            mDownButton.setVisible(false);
            mUpButton.setVisible(false);
        }
    }

    // ScrollbarWidget::SetHorizontal (ScrollbarWidget.cpp:76)
    public void setHorizontal(boolean isHorizontal) {
        mHorizontal = isHorizontal;
        mDownButton.mHorizontal = mHorizontal;
        mUpButton.mHorizontal = mHorizontal;
    }

    // ScrollbarWidget::ResizeScrollbar (ScrollbarWidget.cpp:84)
    public void resizeScrollbar(int theX, int theY, int theWidth, int theHeight) {
        resize(theX, theY, theWidth, theHeight);
        if (mHorizontal) {
            theX = theY = 0; // Use New Container Stuff
            mUpButton.resize(theX, theY, theHeight, theHeight);
            mDownButton.resize(theX + theWidth - theHeight, theY, theHeight, theHeight);
        } else {
            theX = theY = 0; // Use New Container Stuff
            mUpButton.resize(theX, theY, theWidth, theWidth);
            mDownButton.resize(theX, theY + theHeight - theWidth, theWidth, theWidth);
        }
    }

    // ScrollbarWidget::SetMaxValue (ScrollbarWidget.cpp:101)
    public void setMaxValue(double theNewMaxValue) {
        mMaxValue = theNewMaxValue;
        clampValue();
        markDirty();
    }

    // ScrollbarWidget::SetPageSize (ScrollbarWidget.cpp:108)
    public void setPageSize(double theNewPageSize) {
        mPageSize = theNewPageSize;
        clampValue();
        markDirty();
    }

    // ScrollbarWidget::SetValue (ScrollbarWidget.cpp:115)
    public void setValue(double theNewValue) {
        mValue = theNewValue;
        clampValue();
        if (mScrollListener != null) {
            mScrollListener.scrollPosition(mId, mValue);
        }
        markDirty();
    }

    // ScrollbarWidget::AtBottom (ScrollbarWidget.cpp:123)
    public boolean atBottom() {
        return mMaxValue - mPageSize - mValue <= 1.0;
    }

    // ScrollbarWidget::GoToBottom (ScrollbarWidget.cpp:128)
    public void goToBottom() {
        mValue = mMaxValue - mPageSize;
        clampValue();
        setValue(mValue);
    }

    // ScrollbarWidget::DrawThumb (ScrollbarWidget.cpp:135)
    public void drawThumb(Graphics g, int theX, int theY, int theWidth, int theHeight) {
        g.setColor(new Color(212, 212, 212));
        g.fillRect(theX, theY, theWidth, theHeight);

        g.setColor(new Color(255, 255, 255));
        g.fillRect(theX + 1, theY + 1, theWidth - 2, 1);
        g.fillRect(theX + 1, theY + 1, 1, theHeight - 2);

        g.setColor(Color.Black);
        g.fillRect(theX, theY + theHeight - 1, theWidth, 1);
        g.fillRect(theX + theWidth - 1, theY, 1, theHeight);

        g.setColor(new Color(132, 132, 132));
        g.fillRect(theX + 1, theY + theHeight - 2, theWidth - 2, 1);
        g.fillRect(theX + theWidth - 2, theY + 1, 1, theHeight - 2);
    }

    // ScrollbarWidget::GetTrackSize (ScrollbarWidget.cpp:153)
    public int getTrackSize() {
        if (mHorizontal) {
            return mWidth - 2 * mUpButton.mWidth;
        } else {
            return mHeight - 2 * mUpButton.mWidth;
        }
    }

    // ScrollbarWidget::GetThumbSize (ScrollbarWidget.cpp:161)
    public int getThumbSize() {
        if (mPageSize > mMaxValue) {
            return 0;
        }

        int aThumbSize = (int) (((double) (getTrackSize() * mPageSize / mMaxValue)) + 0.5);
        return Math.max(8, aThumbSize);
    }

    // ScrollbarWidget::GetThumbPosition (ScrollbarWidget.cpp:170)
    public int getThumbPosition() {
        if (mPageSize > mMaxValue) {
            return mUpButton.mWidth;
        }
        return (int) (((double) ((mValue * (getTrackSize() - getThumbSize()))
                / (mMaxValue - mPageSize))) + 0.5) + mUpButton.mWidth;
    }

    // ScrollbarWidget::Draw (ScrollbarWidget.cpp:176)
    @Override
    public void draw(Graphics g) {
        int aThumbSize = getThumbSize();
        int aThumbPosition = getThumbPosition();

        if (mHorizontal) {
            if (mUpdateMode == UPDATE_MODE_PGUP) {
                g.setColor(new Color(48, 48, 48));
            } else {
                g.setColor(new Color(232, 232, 232));
            }
            g.fillRect(0, 0, aThumbPosition, mHeight);

            if (aThumbSize > 0) {
                drawThumb(g, aThumbPosition, 0, aThumbSize, mHeight);
            }

            if (mUpdateMode == UPDATE_MODE_PGDN) {
                g.setColor(new Color(48, 48, 48));
            } else {
                g.setColor(new Color(232, 232, 232));
            }
            g.fillRect(aThumbPosition + aThumbSize, 0, mWidth - aThumbPosition - aThumbSize,
                    mHeight);
        } else {
            if (mUpdateMode == UPDATE_MODE_PGUP) {
                g.setColor(new Color(48, 48, 48));
            } else {
                g.setColor(new Color(232, 232, 232));
            }
            g.fillRect(0, 0, mWidth, aThumbPosition);

            if (aThumbSize > 0) {
                drawThumb(g, 0, aThumbPosition, mWidth, aThumbSize);
            }

            if (mUpdateMode == UPDATE_MODE_PGDN) {
                g.setColor(new Color(48, 48, 48));
            } else {
                g.setColor(new Color(232, 232, 232));
            }
            g.fillRect(0, aThumbPosition + aThumbSize, mWidth,
                    mHeight - aThumbPosition - aThumbSize);
        }
    }

    // ScrollbarWidget::ClampValue (ScrollbarWidget.cpp:217)
    public void clampValue() {
        double anOldValue = mValue;

        if (mValue > mMaxValue - mPageSize) {
            mValue = mMaxValue - mPageSize;
        }
        if (mValue < 0.0) {
            mValue = 0.0;
        }

        boolean canScroll = mPageSize < mMaxValue;

        setDisabled(!canScroll);
        mUpButton.setDisabled(!canScroll);
        mDownButton.setDisabled(!canScroll);

        if (mInvisIfNoScroll) {
            setVisible(canScroll);
            mDownButton.setVisible(canScroll);
            mUpButton.setVisible(canScroll);
        }

        if (mValue != anOldValue && mScrollListener != null) {
            mScrollListener.scrollPosition(mId, mValue);
        }
    }

    // ScrollbarWidget::SetThumbPosition (ScrollbarWidget.cpp:242)
    public void setThumbPosition(int thePosition) {
        setValue(((thePosition - mUpButton.mWidth) * (mMaxValue - mPageSize))
                / (getTrackSize() - getThumbSize()));
    }

    // ScrollbarWidget::ButtonPress (ScrollbarWidget.cpp:247)
    @Override
    public void buttonPress(int theId) {
        mButtonAcc = 0;

        if (theId == 0) {
            setValue(mValue - 1);
        } else {
            setValue(mValue + 1);
        }
    }

    // ScrollbarWidget::ButtonDepress (ScrollbarWidget.cpp:257)
    @Override
    public void buttonDepress(int theId) {
    }

    // ScrollbarWidget::ButtonDownTick (ScrollbarWidget.cpp:259)
    @Override
    public void buttonDownTick(int theId) {
        if (theId == 0) {
            if (++mButtonAcc >= 25) {
                setValue(mValue - 1);
                mButtonAcc = 24;
            }
        } else {
            if (++mButtonAcc >= 25) {
                setValue(mValue + 1);
                mButtonAcc = 24;
            }
        }
    }

    // PORT-NOTE: upstream ScrollbarWidget overrides only ButtonPress/ButtonDepress/ButtonDownTick;
    // the remaining ButtonListener methods (ButtonListener.h:37-39) are left pure, which makes the
    // C++ class abstract (it is never instantiated in-tree). Java has no abstract-method inheritance
    // from interfaces without declaring the class abstract, and ListWidget needs an instantiable
    // type, so the port supplies the upstream-inherited no-op behaviour.
    @Override
    public void buttonMouseEnter(int theId) {
    }

    @Override
    public void buttonMouseLeave(int theId) {
    }

    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }

    // ScrollbarWidget::Update (ScrollbarWidget.cpp:279)
    @Override
    public void update() {
        super.update();

        switch (mUpdateMode) {
            case UPDATE_MODE_PGUP:
                if (thumbCompare(mLastMouseX, mLastMouseY) != -1) {
                    mUpdateMode = UPDATE_MODE_IDLE;
                    markDirty();
                } else if (++mUpdateAcc >= 25) {
                    setValue(mValue - mPageSize);
                    mUpdateAcc = 20;
                }
                break;
            case UPDATE_MODE_PGDN:
                if (thumbCompare(mLastMouseX, mLastMouseY) != 1) {
                    mUpdateMode = UPDATE_MODE_IDLE;
                    markDirty();
                } else if (++mUpdateAcc >= 25) {
                    setValue(mValue + mPageSize);
                    mUpdateAcc = 20;
                }
                break;
            default:
                break;
        }
    }

    // ScrollbarWidget::ThumbCompare (ScrollbarWidget.cpp:312)
    public int thumbCompare(int x, int y) {
        int aTrackPosition;

        if (mHorizontal) {
            aTrackPosition = x;
        } else {
            aTrackPosition = y;
        }

        if (aTrackPosition < getThumbPosition()) {
            return -1;
        }

        if (aTrackPosition >= getThumbPosition() + getThumbSize()) {
            return 1;
        }

        return 0;
    }

    // ScrollbarWidget::MouseDown (ScrollbarWidget.h:97)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);
    }

    // ScrollbarWidget::MouseDown (ScrollbarWidget.cpp:330)
    @Override
    public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
        super.mouseDown(x, y, theBtnNum, theClickCount);

        if (!mDisabled) {
            switch (thumbCompare(x, y)) {
                case -1:
                    setValue(mValue - mPageSize);
                    mUpdateMode = UPDATE_MODE_PGUP;
                    mUpdateAcc = 0;
                    break;
                case 0:
                    mPressedOnThumb = true;
                    mMouseDownThumbPos = getThumbPosition();
                    mMouseDownX = x;
                    mMouseDownY = y;
                    break;
                case 1:
                    setValue(mValue + mPageSize);
                    mUpdateMode = UPDATE_MODE_PGDN;
                    mUpdateAcc = 0;
                    break;
                default:
                    break;
            }
        }

        mLastMouseX = x;
        mLastMouseY = y;
    }

    // ScrollbarWidget::MouseUp (ScrollbarWidget.h:98)
    @Override
    public void mouseUp(int x, int y) {
        super.mouseUp(x, y);
    }

    // ScrollbarWidget::MouseUp (ScrollbarWidget.cpp:361)
    @Override
    public void mouseUp(int x, int y, int theBtnNum, int theClickCount) {
        super.mouseUp(x, y, theBtnNum, theClickCount);

        mUpdateMode = 0;
        mPressedOnThumb = false;
        markDirty();
    }

    // ScrollbarWidget::MouseDrag (ScrollbarWidget.cpp:370)
    @Override
    public void mouseDrag(int x, int y) {
        super.mouseDrag(x, y);

        if (mPressedOnThumb) {
            if (mHorizontal) {
                setThumbPosition(mMouseDownThumbPos + x - mMouseDownX);
            } else {
                setThumbPosition(mMouseDownThumbPos + y - mMouseDownY);
            }
        }

        mLastMouseX = x;
        mLastMouseY = y;
    }
}
