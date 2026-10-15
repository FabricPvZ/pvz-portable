package cn.enaium.fabricpvz.portable.sexy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/SexyAppFramework/widget/Widget.h:40 (implementation
 * src/SexyAppFramework/widget/Widget.cpp:32).
 *
 * <p>Base widget: geometry, visibility, mouse/keyboard events and the text-drawing helpers. Public
 * fields and the virtual-method names keep their upstream spelling (camelCased where upstream used
 * PascalCase).
 */
public class Widget extends WidgetContainer {
    // Layout flags used in Widget::Layout (Widget.h:125-158)
    public static final int LAY_SameWidth = 0x0001;
    public static final int LAY_SameHeight = 0x0002;

    public static final int LAY_SetLeft = 0x0010;
    public static final int LAY_SetTop = 0x0020;
    public static final int LAY_SetWidth = 0x0040;
    public static final int LAY_SetHeight = 0x0080;

    public static final int LAY_Above = 0x0100;
    public static final int LAY_Below = 0x0200;
    public static final int LAY_Right = 0x0400;
    public static final int LAY_Left = 0x0800;

    public static final int LAY_SameLeft = 0x1000;
    public static final int LAY_SameRight = 0x2000;
    public static final int LAY_SameTop = 0x4000;
    public static final int LAY_SameBottom = 0x8000;

    public static final int LAY_GrowToRight = 0x10000;
    public static final int LAY_GrowToLeft = 0x20000;
    public static final int LAY_GrowToTop = 0x40000;
    public static final int LAY_GrowToBottom = 0x80000;

    public static final int LAY_HCenter = 0x100000;
    public static final int LAY_VCenter = 0x200000;
    public static final int LAY_Max = 0x400000;

    public static final int LAY_SameSize = LAY_SameWidth | LAY_SameHeight;
    public static final int LAY_SameCorner = LAY_SameLeft | LAY_SameTop;
    public static final int LAY_SetPos = LAY_SetLeft | LAY_SetTop;
    public static final int LAY_SetSize = LAY_SetWidth | LAY_SetHeight;

    public boolean mVisible;
    public boolean mMouseVisible;
    public boolean mDisabled;
    public boolean mHasFocus;
    public boolean mIsDown;
    public boolean mIsOver;
    public boolean mHasTransparencies;
    public Insets mMouseInsets;
    public boolean mDoFinger;
    public boolean mWantsFocus;

    public Widget mTabPrev;
    public Widget mTabNext;

    /** Upstream {@code static bool Widget::mWriteColoredString} (Widget.cpp:37). */
    public static boolean mWriteColoredString = true;

    /** headshot2017: upstream {@code mLoadedResourceNames}, released when the widget is destroyed. */
    public final List<String> mLoadedResourceNames = new ArrayList<>();

    // Widget::Widget (Widget.cpp:39)
    public Widget() {
        mWidgetManager = null;
        mVisible = true;
        mDisabled = false;
        mIsDown = false;
        mIsOver = false;
        mDoFinger = false;
        mMouseVisible = true;
        mHasFocus = false;
        mHasTransparencies = false;
        mWantsFocus = false;
        mTabPrev = null;
        mTabNext = null;
        mMouseInsets = new Insets();
    }

    /**
     * Ported from {@code Widget::~Widget} (Widget.cpp:55): the C++ destructor releases every
     * tracked resource through the resource manager. Java has no destructors, so the widget slice
     * calls this explicitly (see {@link WidgetContainer#removeAllWidgets(boolean, boolean)} and
     * {@code SexyAppBase.safeDeleteWidget}).
     */
    public void dispose() {
        SexyAppBase anApp = SexyAppBase.current;
        if ((anApp != null) && (anApp.mResourceManager != null)) {
            anApp.mResourceManager.releaseTrackedResources(mLoadedResourceNames);
        }
    }

    // Widget::WidgetRemovedHelper (Widget.cpp:60)
    public void widgetRemovedHelper() {
        if (mWidgetManager == null) {
            return;
        }

        // Call RemovedFromManager on all child widgets and disable them and stuff like that
        for (Widget aWidget : new ArrayList<>(mWidgets)) {
            aWidget.widgetRemovedHelper();
        }

        mWidgetManager.disableWidget(this);

        List<WidgetManager.PreModalInfo> aList = mWidgetManager.mPreModalInfoList;
        for (WidgetManager.PreModalInfo aPreModalInfo : aList) {
            if (aPreModalInfo.mPrevBaseModalWidget == this) {
                aPreModalInfo.mPrevBaseModalWidget = null;
            }
            if (aPreModalInfo.mPrevFocusWidget == this) {
                aPreModalInfo.mPrevFocusWidget = null;
            }
        }

        removedFromManager(mWidgetManager);
        markDirtyFull(this);

        mWidgetManager = null;
    }

    // Widget::OrderInManagerChanged (Widget.cpp:89)
    public void orderInManagerChanged() {
    }

    // Widget::IsPointVisible (Widget.cpp:93)
    public boolean isPointVisible(int x, int y) {
        return true;
    }

    // Widget::SetVisible (Widget.cpp:100)
    public void setVisible(boolean isVisible) {
        if (mVisible == isVisible) {
            return;
        }

        mVisible = isVisible;

        if (mVisible) {
            markDirty();
        } else {
            markDirtyFull();
        }

        if (mWidgetManager != null) {
            mWidgetManager.rehupMouse();
        }
    }

    // Widget::Draw (Widget.cpp:119) — already translated
    @Override
    public void draw(Graphics g) {
    }

    // Widget::DrawOverlay (Widget.cpp:120)
    public void drawOverlay(Graphics g) {
    }

    // Widget::DrawOverlay (Widget.cpp:122)
    public void drawOverlay(Graphics g, int thePriority) {
        drawOverlay(g);
    }

    // Widget::Resize (Widget.cpp:126)
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        if ((mX == theX) && (mY == theY) && (mWidth == theWidth) && (mHeight == theHeight)) {
            return;
        }

        // Mark everything dirty that is over or under the old position
        markDirtyFull();

        mX = theX;
        mY = theY;
        mWidth = theWidth;
        mHeight = theHeight;

        // Mark things dirty that are over the new position
        markDirty();

        if (mWidgetManager != null) {
            mWidgetManager.rehupMouse();
        }
    }

    // Widget::Resize (Widget.cpp:146)
    public void resize(Rect theRect) {
        resize(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }

    // Widget::Move (Widget.cpp:151)
    public void move(int theNewX, int theNewY) {
        resize(theNewX, theNewY, mWidth, mHeight);
    }

    // Widget::WantsFocus (Widget.cpp:156)
    public boolean wantsFocus() {
        return mWantsFocus;
    }

    // Widget::SetDisabled (Widget.cpp:161)
    public void setDisabled(boolean isDisabled) {
        if (mDisabled == isDisabled) {
            return;
        }

        mDisabled = isDisabled;

        if (isDisabled && (mWidgetManager != null)) {
            mWidgetManager.disableWidget(this);
        }

        markDirty();

        // Incase a widget is enabled right under our cursor
        if (!isDisabled && (mWidgetManager != null)
                && contains(mWidgetManager.mLastMouseX, mWidgetManager.mLastMouseY)) {
            mWidgetManager.mousePosition(mWidgetManager.mLastMouseX, mWidgetManager.mLastMouseY);
        }
    }

    // Widget::GotFocus (Widget.cpp:178)
    public void gotFocus() {
        mHasFocus = true;
    }

    // Widget::LostFocus (Widget.cpp:183)
    public void lostFocus() {
        mHasFocus = false;
    }

    // Widget::Update (Widget.cpp:188)
    @Override
    public void update() {
        super.update();
    }

    // Widget::KeyChar (Widget.cpp:193)
    public void keyChar(char theChar) {
    }

    // Widget::KeyText (Widget.cpp:195) — legacy widgets get ASCII via KeyChar; non-ASCII needs an override
    public void keyText(String theText) {
        for (byte aChar : theText.getBytes(StandardCharsets.UTF_8)) {
            int aValue = aChar & 0xFF;
            if (aValue >= 32 && aValue < 128) {
                keyChar((char) aValue);
            }
        }
    }

    // Widget::KeyDown (Widget.cpp:202)
    public void keyDown(int theKey) {
        if (theKey == KeyCode.KEYCODE_TAB) {
            if (mWidgetManager.mKeyDown[KeyCode.KEYCODE_SHIFT]) {
                if (mTabPrev != null) {
                    mWidgetManager.setFocus(mTabPrev);
                }
            } else {
                if (mTabNext != null) {
                    mWidgetManager.setFocus(mTabNext);
                }
            }
        }
    }

    // Widget::KeyUp (Widget.cpp:219)
    public void keyUp(int theKey) {
    }

    // Widget::ShowFinger (Widget.cpp:223)
    public void showFinger(boolean on) {
        if (mWidgetManager == null) {
            return;
        }

        if (on) {
            mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_HAND);
        } else {
            mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
    }

    // Widget::MouseEnter (Widget.cpp:235)
    public void mouseEnter() {
    }

    // Widget::MouseLeave (Widget.cpp:240)
    public void mouseLeave() {
    }

    // Widget::MouseMove (Widget.cpp:245)
    public void mouseMove(int x, int y) {
    }

    // Widget::MouseDrag (Widget.cpp:246)
    public void mouseDrag(int x, int y) {
    }

    // Widget::MouseDown (Widget.cpp:248)
    public void mouseDown(int x, int y, int theClickCount) {
        if (theClickCount == 3) {
            mouseDown(x, y, 2, 1);
        } else if (theClickCount >= 0) {
            mouseDown(x, y, 0, theClickCount);
        } else {
            mouseDown(x, y, 1, -theClickCount);
        }
    }

    // Widget::MouseDown (Widget.cpp:260)
    public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
    }

    // Widget::MouseUp (Widget.cpp:261)
    public void mouseUp(int x, int y) {
    }

    // Widget::MouseUp (Widget.cpp:263)
    public void mouseUp(int x, int y, int theLastDownButtonId) {
        mouseUp(x, y);

        if (theLastDownButtonId == 3) {
            mouseUp(x, y, 2, 1);
        } else if (theLastDownButtonId >= 0) {
            mouseUp(x, y, 0, theLastDownButtonId);
        } else {
            mouseUp(x, y, 1, -theLastDownButtonId);
        }
    }

    // Widget::MouseUp (Widget.cpp:277)
    public void mouseUp(int x, int y, int theBtnNum, int theClickCount) {
    }

    // Widget::MouseWheel (Widget.cpp:279)
    public void mouseWheel(int theDelta) {
    }

    // Widget::WriteCenteredLine (Widget.cpp:284)
    public Rect writeCenteredLine(Graphics g, int anOffset, String theLine) {
        Font aFont = g.getFont();
        int aWidth = aFont.stringWidth(theLine);
        int aX = (mWidth - aWidth) / 2;

        g.drawString(theLine, aX, anOffset);

        return new Rect(aX, anOffset - aFont.getAscent(), aWidth, aFont.getHeight());
    }

    // Widget::WriteCenteredLine (Widget.cpp:296)
    public Rect writeCenteredLine(Graphics g, int anOffset, String theLine, Color theColor1,
            Color theColor2, Point theShadowOffset) {
        Font aFont = g.getFont();
        int aWidth = aFont.stringWidth(theLine);
        int aX = (mWidth - aWidth) / 2;

        g.setColor(theColor2);
        g.drawString(theLine, (mWidth - aWidth) / 2 + theShadowOffset.mX,
                anOffset + theShadowOffset.mY);

        g.setColor(theColor1);
        g.drawString(theLine, (mWidth - aWidth) / 2, anOffset);

        // account for shadow in position and size
        return new Rect(aX + Math.min(0, theShadowOffset.mX),
                anOffset - aFont.getAscent() + Math.min(0, theShadowOffset.mY),
                aWidth + Math.abs(theShadowOffset.mX),
                aFont.getHeight() + Math.abs(theShadowOffset.mY));
    }

    // Widget::WriteString (Widget.cpp:317)
    public int writeString(Graphics g, String theString, int theX, int theY, int theWidth,
            int theJustification, boolean drawString, int theOffset, int theLength) {
        boolean oldColored = g.mWriteColoredString;
        g.mWriteColoredString = mWriteColoredString;
        int aXOffset = g.writeString(theString, theX, theY, theWidth, theJustification, drawString,
                theOffset, theLength, -1);
        g.mWriteColoredString = oldColored;

        return aXOffset;
    }

    // Widget::WriteWordWrapped (Widget.cpp:326)
    public int writeWordWrapped(Graphics g, Rect theRect, String theLine, int theLineSpacing,
            int theJustification) {
        boolean oldColored = g.mWriteColoredString;
        g.mWriteColoredString = mWriteColoredString;
        int aReturn = g.writeWordWrapped(theRect, theLine, theLineSpacing, theJustification, null,
                -1, null);
        g.mWriteColoredString = oldColored;

        return aReturn;
    }

    // Widget::GetWordWrappedHeight (Widget.cpp:335)
    public int getWordWrappedHeight(Graphics g, int theWidth, String theLine, int aLineSpacing) {
        return g.getWordWrappedHeight(theWidth, theLine, aLineSpacing, null);
    }

    // Widget::GetNumDigits (Widget.cpp:340)
    public int getNumDigits(int theNumber) {
        long aDivisor = 10;
        int aNumDigits = 1;
        while (theNumber >= aDivisor) {
            aNumDigits++;
            aDivisor *= 10;
        }

        return aNumDigits;
    }

    // Widget::WriteNumberFromStrip (Widget.cpp:352)
    public void writeNumberFromStrip(Graphics g, int theNumber, int theX, int theY,
            Image theNumberStrip, int aSpacing) {
        long aDivisor = 10;
        int aNumDigits = 1;
        while (theNumber >= aDivisor) {
            aNumDigits++;
            aDivisor *= 10;
        }
        if (theNumber == 0) {
            aDivisor = 10;
        }

        int aDigitLen = theNumberStrip.getWidth() / 10;

        for (int aDigitIdx = 0; aDigitIdx < aNumDigits; aDigitIdx++) {
            aDivisor /= 10;
            int aDigit = (int) ((theNumber / aDivisor) % 10);

            Graphics aClipG = g.create();
            aClipG.clipRect(theX + aDigitIdx * (aDigitLen + aSpacing), theY, aDigitLen,
                    theNumberStrip.getHeight());
            aClipG.drawImage(theNumberStrip,
                    theX + aDigitIdx * (aDigitLen + aSpacing) - aDigit * aDigitLen, theY);
        }
    }

    // Widget::Contains (Widget.cpp:373)
    public boolean contains(int theX, int theY) {
        return (theX >= mX) && (theX < mX + mWidth) && (theY >= mY) && (theY < mY + mHeight);
    }

    // Widget::GetInsetRect (Widget.cpp:379)
    public Rect getInsetRect() {
        return new Rect(mX + mMouseInsets.mLeft, mY + mMouseInsets.mTop,
                mWidth - mMouseInsets.mLeft - mMouseInsets.mRight,
                mHeight - mMouseInsets.mTop - mMouseInsets.mBottom);
    }

    // Widget::DeferOverlay (Widget.cpp:386)
    public void deferOverlay(int thePriority) {
        mWidgetManager.deferOverlay(this, thePriority);
    }

    // Widget::Layout (Widget.cpp:391)
    public void layout(int theLayoutFlags, Widget theRelativeWidget, int theLeftPad, int theTopPad,
            int theWidthPad, int theHeightPad) {
        int aRelLeft = theRelativeWidget.left();
        int aRelTop = theRelativeWidget.top();
        if (theRelativeWidget == mParent) {
            aRelLeft = 0;
            aRelTop = 0;
        }

        int aRelWidth = theRelativeWidget.width();
        int aRelHeight = theRelativeWidget.height();
        int aRelRight = aRelLeft + aRelWidth;
        int aRelBottom = aRelTop + aRelHeight;

        int aLeft = left();
        int aTop = top();
        int aWidth = width();
        int aHeight = height();

        int aType = 1;
        while (aType < LAY_Max) {
            if ((theLayoutFlags & aType) != 0) {
                switch (aType) {
                    case LAY_SameWidth -> aWidth = aRelWidth + theWidthPad;
                    case LAY_SameHeight -> aHeight = aRelHeight + theHeightPad;

                    case LAY_Above -> aTop = aRelTop - aHeight + theTopPad;
                    case LAY_Below -> aTop = aRelBottom + theTopPad;
                    case LAY_Right -> aLeft = aRelRight + theLeftPad;
                    case LAY_Left -> aLeft = aRelLeft - aWidth + theLeftPad;

                    case LAY_SameLeft -> aLeft = aRelLeft + theLeftPad;
                    case LAY_SameRight -> aLeft = aRelRight - aWidth + theLeftPad;
                    case LAY_SameTop -> aTop = aRelTop + theTopPad;
                    case LAY_SameBottom -> aTop = aRelBottom - aHeight + theTopPad;

                    case LAY_GrowToRight -> aWidth = aRelRight - aLeft + theWidthPad;
                    case LAY_GrowToLeft -> aWidth = aRelLeft - aLeft + theWidthPad;
                    case LAY_GrowToTop -> aHeight = aRelTop - aTop + theHeightPad;
                    case LAY_GrowToBottom -> aHeight = aRelBottom - aTop + theHeightPad;

                    case LAY_SetLeft -> aLeft = theLeftPad;
                    case LAY_SetTop -> aTop = theTopPad;
                    case LAY_SetWidth -> aWidth = theWidthPad;
                    case LAY_SetHeight -> aHeight = theHeightPad;

                    case LAY_HCenter -> aLeft = aRelLeft + (aRelWidth - aWidth) / 2 + theLeftPad;
                    case LAY_VCenter -> aTop = aRelTop + (aRelHeight - aHeight) / 2 + theTopPad;

                    default -> {
                    }
                }
            }

            aType <<= 1;
        }

        resize(aLeft, aTop, aWidth, aHeight);
    }

    // Widget.h:160-164
    public int left() {
        return mX;
    }

    public int top() {
        return mY;
    }

    public int right() {
        return mX + mWidth;
    }

    public int bottom() {
        return mY + mHeight;
    }

    public int width() {
        return mWidth;
    }

    public int height() {
        return mHeight;
    }
}
