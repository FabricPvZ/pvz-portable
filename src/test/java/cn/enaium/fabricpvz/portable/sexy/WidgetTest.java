package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Behaviour tests for {@link Widget} / {@link WidgetContainer} geometry, z-order, the boolean dirty
 * model, mouse enter/leave dispatch and the click-count encoding
 * (src/SexyAppFramework/widget/Widget.cpp, WidgetContainer.cpp).
 */
class WidgetTest {

    @AfterEach
    void restoreGlobal() {
        SexyAppBase.current = null;
    }

    /** Records every event the framework dispatches at a widget. */
    static class TestWidget extends Widget {
        int mDraws;
        final List<String> mEvents = new ArrayList<>();
        int mLastBtnNum = -1;
        int mLastClickCount = -1;
        int mLastX = Integer.MIN_VALUE;
        int mLastY = Integer.MIN_VALUE;
        char mLastChar;

        TestWidget() {
        }

        TestWidget(int theX, int theY, int theWidth, int theHeight) {
            resize(theX, theY, theWidth, theHeight);
        }

        @Override
        public void draw(Graphics g) {
            mDraws++;
        }

        @Override
        public void mouseEnter() {
            mEvents.add("enter");
        }

        @Override
        public void mouseLeave() {
            mEvents.add("leave");
        }

        @Override
        public void mouseMove(int x, int y) {
            mLastX = x;
            mLastY = y;
            mEvents.add("move");
        }

        @Override
        public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
            mLastBtnNum = theBtnNum;
            mLastClickCount = theClickCount;
            mEvents.add("down");
        }

        @Override
        public void mouseUp(int x, int y, int theBtnNum, int theClickCount) {
            mLastBtnNum = theBtnNum;
            mLastClickCount = theClickCount;
            mEvents.add("up");
        }

        @Override
        public void keyChar(char theChar) {
            mLastChar = theChar;
            mEvents.add("char:" + theChar);
        }
    }

    private static WidgetManager newManager() {
        return new WidgetManager(new SexyAppBase());
    }

    @Test
    void containsUsesAbsoluteCoordinatesAndExcludesTheRightBottomEdges() {
        TestWidget aWidget = new TestWidget(10, 20, 30, 40);

        assertTrue(aWidget.contains(10, 20));
        assertTrue(aWidget.contains(39, 59));
        assertFalse(aWidget.contains(40, 59));
        assertFalse(aWidget.contains(39, 60));
        assertFalse(aWidget.contains(9, 20));
    }

    @Test
    void getInsetRectShrinksByTheMouseInsets() {
        TestWidget aWidget = new TestWidget(10, 20, 30, 40);
        aWidget.mMouseInsets = new Insets(2, 3, 4, 5);

        Rect anInset = aWidget.getInsetRect();

        assertEquals(12, anInset.mX);
        assertEquals(23, anInset.mY);
        assertEquals(24, anInset.mWidth);
        assertEquals(32, anInset.mHeight);
    }

    @Test
    void layoutFlagsApplyInAscendingBitOrder() {
        TestWidget aRelative = new TestWidget(100, 100, 50, 30);
        TestWidget aWidget = new TestWidget(0, 0, 10, 10);

        // LAY_Above is applied first (ascending bit order) and uses the widget's *own* height,
        // exactly like upstream: top = relTop - height.
        aWidget.layout(Widget.LAY_SameLeft | Widget.LAY_Above, aRelative, 0, 0, 0, 0);

        assertEquals(100, aWidget.mX);
        assertEquals(90, aWidget.mY); // 100 - 10 (the widget's own height, not the relative one)
        assertEquals(10, aWidget.mWidth);
        assertEquals(10, aWidget.mHeight);

        aWidget.layout(Widget.LAY_SetSize | Widget.LAY_VCenter, aRelative, 0, 0, 7, 9);
        assertEquals(7, aWidget.mWidth);
        assertEquals(9, aWidget.mHeight);
        assertEquals(100 + (30 - 9) / 2, aWidget.mY);
    }

    @Test
    void addWidgetInsertsInZOrderAndHitTestingWalksBackwards() {
        WidgetContainer aContainer = new WidgetContainer();
        TestWidget aBottom = new TestWidget(0, 0, 100, 100);
        TestWidget aTop = new TestWidget(0, 0, 100, 100);

        aContainer.addWidget(aBottom);
        aContainer.addWidget(aTop);

        assertEquals(2, aContainer.mWidgets.size());
        assertSame(aBottom, aContainer.mWidgets.get(0)); // index 0 = bottom-most
        assertSame(aTop, aContainer.mWidgets.get(1));

        boolean[] found = new boolean[1];
        int[] aWidgetX = new int[1];
        int[] aWidgetY = new int[1];
        Widget aHit = aContainer.getWidgetAtHelper(50, 50, WidgetManager.WIDGETFLAGS_ALLOW_MOUSE, found,
                aWidgetX, aWidgetY);

        assertSame(aTop, aHit, "backwards hit-testing must return the top-most widget");
        assertEquals(50, aWidgetX[0]);
        assertEquals(50, aWidgetY[0]);
    }

    @Test
    void bringToBackAndPutBehindReorderTheWidgetList() {
        WidgetContainer aContainer = new WidgetContainer();
        TestWidget aFirst = new TestWidget(0, 0, 10, 10);
        TestWidget aSecond = new TestWidget(0, 0, 10, 10);
        aContainer.addWidget(aFirst);
        aContainer.addWidget(aSecond);

        aContainer.bringToBack(aSecond);
        assertSame(aSecond, aContainer.mWidgets.get(0));

        aContainer.bringToFront(aFirst);
        assertSame(aFirst, aContainer.mWidgets.get(1));

        aContainer.putBehind(aFirst, aSecond);
        assertSame(aFirst, aContainer.mWidgets.get(0));
        assertTrue(aContainer.isBelow(aFirst, aSecond));
        assertFalse(aContainer.isBelow(aSecond, aFirst));
    }

    @Test
    void markDirtyPropagatesToTheParentAndToWidgetsAbove() {
        WidgetContainer aContainer = new WidgetContainer();
        TestWidget aBottom = new TestWidget(0, 0, 100, 100);
        TestWidget aTop = new TestWidget(0, 0, 100, 100);
        aContainer.addWidget(aBottom);
        aContainer.addWidget(aTop);
        aContainer.mDirty = false;
        aBottom.mDirty = false;
        aTop.mDirty = false;

        aBottom.markDirty();

        assertTrue(aContainer.mDirty, "a child must mark its top-level container dirty");
        assertTrue(aBottom.mDirty);
        assertTrue(aTop.mDirty, "widgets above the dirty widget must be redrawn");
    }

    @Test
    void markDirtyFullMarksEverythingBelowAnOpaqueDirtyWidget() {
        WidgetContainer aContainer = new WidgetContainer();
        TestWidget aBottom = new TestWidget(0, 0, 100, 100);
        TestWidget aTop = new TestWidget(0, 0, 100, 100);
        aContainer.addWidget(aBottom);
        aContainer.addWidget(aTop);
        aContainer.mDirty = false;
        aBottom.mDirty = false;
        aTop.mDirty = false;

        aTop.markDirtyFull();

        assertTrue(aContainer.mDirty);
        assertTrue(aTop.mDirty);
        assertTrue(aBottom.mDirty);
    }

    @Test
    void markAllDirtyReachesEveryDescendant() {
        WidgetContainer aContainer = new WidgetContainer();
        TestWidget aChild = new TestWidget(0, 0, 10, 10);
        aContainer.addWidget(aChild);
        aContainer.mDirty = false;
        aChild.mDirty = false;

        aContainer.markAllDirty();

        assertTrue(aContainer.mDirty);
        assertTrue(aChild.mDirty);
    }

    @Test
    void mouseEnterLeaveAndMoveAreDispatchedWithWidgetLocalCoordinates() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(10, 20, 100, 100);
        aManager.addWidget(aWidget);
        aWidget.mEvents.clear();

        aManager.mousePosition(15, 25);
        assertEquals(List.of("enter", "move"), aWidget.mEvents);
        assertEquals(5, aWidget.mLastX);
        assertEquals(5, aWidget.mLastY);

        aManager.mousePosition(50, 50);
        assertEquals(List.of("enter", "move", "move"), aWidget.mEvents);

        aManager.mousePosition(200, 200);
        assertEquals(List.of("enter", "move", "move", "leave"), aWidget.mEvents);
        assertFalse(aWidget.mIsOver);
    }

    @Test
    void setVisibleHidesTheWidgetFromHitTestingAndMarksItDirtyFull() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aWidget.markDirty();
        aWidget.mDirty = false;

        aWidget.setVisible(false);

        assertTrue(aWidget.mDirty);
        assertNull(aManager.getWidgetAt(10, 10, null, null));

        aWidget.mDirty = false;
        aWidget.setVisible(true);
        assertTrue(aWidget.mDirty);
        assertSame(aWidget, aManager.getWidgetAt(10, 10, null, null));
    }

    @Test
    void widgetClickCountEncodingMapsToButtonAndCount() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);

        // right button single click (platform encoding -1)
        aManager.mouseDown(10, 10, -1);
        assertEquals(1, aWidget.mLastBtnNum);
        assertEquals(1, aWidget.mLastClickCount);
        aManager.mouseUp(10, 10, -1);

        // right button double click (-2)
        aManager.mouseDown(10, 10, -2);
        assertEquals(1, aWidget.mLastBtnNum);
        assertEquals(2, aWidget.mLastClickCount);
        aManager.mouseUp(10, 10, -2);

        // middle button (3)
        aManager.mouseDown(10, 10, 3);
        assertEquals(2, aWidget.mLastBtnNum);
        assertEquals(1, aWidget.mLastClickCount);
        aManager.mouseUp(10, 10, 3);

        // left button double click (2)
        aManager.mouseDown(10, 10, 2);
        assertEquals(0, aWidget.mLastBtnNum);
        assertEquals(2, aWidget.mLastClickCount);
        aManager.mouseUp(10, 10, 2);
    }

    @Test
    void widgetMouseUpDispatchesThroughTheLastDownButtonId() {
        TestWidget aWidget = new TestWidget(0, 0, 10, 10);

        aWidget.mouseUp(1, 2, 3);
        assertEquals(2, aWidget.mLastBtnNum);
        assertEquals(1, aWidget.mLastClickCount);

        aWidget.mouseUp(1, 2, -3);
        assertEquals(1, aWidget.mLastBtnNum);
        assertEquals(3, aWidget.mLastClickCount);
    }

    @Test
    void wantsFocusWidgetReceivesFocusOnMouseDownAndTabCyclesFocus() {
        WidgetManager aManager = newManager();
        TestWidget aFirst = new TestWidget(0, 0, 100, 100);
        TestWidget aSecond = new TestWidget(200, 0, 100, 100);
        aFirst.mWantsFocus = true;
        aSecond.mWantsFocus = true;
        aFirst.mTabNext = aSecond;
        aSecond.mTabPrev = aFirst;
        aManager.addWidget(aFirst);
        aManager.addWidget(aSecond);

        aManager.mouseDown(10, 10, 1);
        assertSame(aFirst, aManager.mFocusWidget, "the hit widget asked for focus");
        assertTrue(aFirst.mHasFocus);
        aManager.mouseUp(10, 10, 1);

        // TAB forwards to the focused widget, which moves focus to mTabNext
        aManager.mKeyDown[KeyCode.KEYCODE_SHIFT] = false;
        aManager.keyDown(KeyCode.KEYCODE_TAB);
        assertSame(aSecond, aManager.mFocusWidget);
        assertFalse(aFirst.mHasFocus);
        assertTrue(aSecond.mHasFocus);

        // SHIFT+TAB walks back through mTabPrev
        aManager.mKeyDown[KeyCode.KEYCODE_SHIFT] = true;
        aManager.keyDown(KeyCode.KEYCODE_TAB);
        assertSame(aFirst, aManager.mFocusWidget);
    }

    @Test
    void keyCharAndKeyTextAreRoutedToTheFocusedWidgetAsAscii() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.setFocus(aWidget);
        aWidget.mEvents.clear();

        aManager.keyChar('x');
        assertEquals('x', aWidget.mLastChar);

        // KeyText fans a UTF-8 string out to KeyChar for the ASCII subset only
        aWidget.mEvents.clear();
        aWidget.keyText("A\u00e9B");
        assertEquals(List.of("char:A", "char:B"), aWidget.mEvents);
    }

    @Test
    void widgetRemovedHelperDisablesAndUnparentsTheWidget() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.setFocus(aWidget);

        aManager.removeWidget(aWidget);

        assertNull(aWidget.mWidgetManager);
        assertNull(aManager.mFocusWidget);
        assertFalse(aWidget.mHasFocus);
        assertEquals(0, aManager.mWidgets.size());
    }

    @Test
    void nullTargetGraphicsFallsBackToTheNoOpStaticImage() {
        // WidgetManager hands a Graphics bound to the screen image to every widget; tests drive the
        // same code path through the framework's no-op static target image.
        Graphics aGraphics = new Graphics((Image) null);
        assertNotNull(aGraphics.mDestImage);
        assertFalse(aGraphics.is3D());
    }

    @Test
    void setDisabledClearsInteractionStateThroughTheManager() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.mouseDown(10, 10, 1);
        aManager.setFocus(aWidget);
        assertSame(aWidget, aManager.mLastDownWidget);

        aWidget.setDisabled(true);

        assertTrue(aWidget.mDisabled);
        assertNull(aManager.mLastDownWidget, "disabling a widget releases its mouse capture");
        assertNull(aManager.mFocusWidget);
        assertEquals(0, aManager.mDownButtons);
        assertFalse(aWidget.mIsDown);
    }
}
