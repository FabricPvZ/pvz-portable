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
 * Behaviour tests for {@link WidgetManager}: focus, the sticky mouse capture, the {1,-1,3} release
 * encoding, modal flags and the dirty-only screen redraw (src/SexyAppFramework/widget/WidgetManager.cpp).
 */
class WidgetManagerTest {

    @AfterEach
    void restoreGlobal() {
        SexyAppBase.current = null;
    }

    static class TestWidget extends Widget {
        final List<String> mEvents = new ArrayList<>();
        final List<Integer> mUpClickCounts = new ArrayList<>();
        final List<Integer> mUpButtonNums = new ArrayList<>();
        /** The raw {1,-1,3} encoding the manager passes to Widget.mouseUp. */
        final List<Integer> mRawUpButtonIds = new ArrayList<>();
        int mDraws;

        TestWidget(int theX, int theY, int theWidth, int theHeight) {
            resize(theX, theY, theWidth, theHeight);
        }

        @Override
        public void draw(Graphics g) {
            mDraws++;
        }

        @Override
        public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
            mEvents.add("down:" + theBtnNum + ":" + theClickCount);
        }

        @Override
        public void mouseUp(int x, int y, int theLastDownButtonId) {
            mRawUpButtonIds.add(theLastDownButtonId);
            super.mouseUp(x, y, theLastDownButtonId);
        }

        @Override
        public void mouseUp(int x, int y, int theBtnNum, int theClickCount) {
            mUpButtonNums.add(theBtnNum);
            mUpClickCounts.add(theClickCount);
            mEvents.add("up:" + theBtnNum + ":" + theClickCount);
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
        public void mouseWheel(int theDelta) {
            mEvents.add("wheel:" + theDelta);
        }

        @Override
        public void lostFocus() {
            super.lostFocus();
            mEvents.add("lostFocus");
        }

        @Override
        public void gotFocus() {
            super.gotFocus();
            mEvents.add("gotFocus");
        }
    }

    private static WidgetManager newManager() {
        return new WidgetManager(new SexyAppBase());
    }

    private static Graphics newTarget() {
        // The framework's null-target Graphics resolves to the no-op static image.
        return new Graphics((Image) null);
    }

    @Test
    void drawScreenOnlyRedrawsDirtyVisibleWidgetsAndClearsTheirDirtyFlag() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        TestWidget aHidden = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.addWidget(aHidden);
        aHidden.setVisible(false);
        aWidget.markDirty();
        aHidden.markDirty();
        aWidget.mDirty = true;
        aHidden.mDirty = true;

        assertTrue(aManager.drawScreen(newTarget()));

        assertEquals(1, aWidget.mDraws);
        assertEquals(0, aHidden.mDraws);
        assertFalse(aWidget.mDirty, "drawn widgets are clean again");
        assertTrue(aHidden.mDirty, "invisible widgets stay dirty so they redraw when shown");

        // Nothing is dirty any more: the second pass must not draw again.
        assertFalse(aManager.drawScreen(newTarget()));
        assertEquals(1, aWidget.mDraws);
    }

    @Test
    void mouseCaptureIsStickyUntilAllButtonsAreReleased() {
        WidgetManager aManager = newManager();
        TestWidget aFirst = new TestWidget(0, 0, 50, 50);
        TestWidget aSecond = new TestWidget(100, 0, 50, 50);
        aManager.addWidget(aFirst);
        aManager.addWidget(aSecond);

        aManager.mouseDown(10, 10, 1);
        assertSame(aFirst, aManager.mLastDownWidget);
        assertEquals(1, aManager.mDownButtons);
        assertEquals(1, aManager.mLastDownButtonId);
        assertTrue(aFirst.mIsDown);

        // A second press anywhere while a button is held is routed to the captured widget.
        aManager.mouseDown(110, 10, 1);
        assertSame(aFirst, aManager.mLastDownWidget);
        assertTrue(aFirst.mEvents.contains("down:0:1"));
        assertFalse(aSecond.mIsDown);

        aManager.mouseUp(110, 10, 1);
        assertNull(aManager.mLastDownWidget, "capture is released with the last button");
        assertEquals(0, aManager.mDownButtons);
        assertFalse(aFirst.mIsDown);
        assertEquals(List.of(0), aFirst.mUpButtonNums);
        assertEquals(List.of(1), aFirst.mUpClickCounts);
    }

    /**
     * A press whose release never arrives (the pointer left the window mid-click) used to leave the
     * manager holding a button down: every following click was routed to the stale down-widget as a drag,
     * so clicks looked dead until a full click pair arrived. {@code doMouseUps()} is what the app's
     * mouse-leave and focus-loss handlers call to recover.
     */
    @Test
    void aClickWithoutAReleaseIsRecoveredByDoMouseUps() {
        WidgetManager aManager = newManager();
        TestWidget aFirst = new TestWidget(0, 0, 50, 50);
        TestWidget aSecond = new TestWidget(100, 0, 50, 50);
        aManager.addWidget(aFirst);
        aManager.addWidget(aSecond);

        aManager.mouseDown(10, 10, 1);
        assertSame(aFirst, aManager.mLastDownWidget);
        assertTrue(aManager.mDownButtons != 0, "the press must be held");

        // No mouseUp: the pointer left the window and the release never came.
        aManager.doMouseUps();
        assertEquals(0, aManager.mDownButtons, "the held button must be released");
        assertNull(aManager.mLastDownWidget, "no widget may stay captured");

        // The next click lands on its own target again instead of being routed to the stale widget.
        aManager.mouseDown(110, 10, 1);
        assertSame(aSecond, aManager.mLastDownWidget, "the new press must pick the widget under it");
        assertTrue(aSecond.mIsDown);
        aManager.mouseUp(110, 10, 1);
        assertFalse(aSecond.mIsDown, "and the release must reach it");
    }

    @Test
    void doMouseUpsUsesTheOneMinusOneThreeClickEncoding() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 50, 50);
        aManager.addWidget(aWidget);
        aManager.mouseDown(10, 10, 1);
        aManager.mLastMouseX = 12;
        aManager.mLastMouseY = 14;

        aManager.doMouseUps(aWidget, 0x07);

        assertEquals(List.of(1, -1, 3), aWidget.mRawUpButtonIds,
                "the manager passes the upstream {1,-1,3} table to Widget.mouseUp");
        assertEquals(List.of(0, 1, 2), aWidget.mUpButtonNums, "Widget decodes the raw ids");
        assertEquals(List.of(1, 1, 1), aWidget.mUpClickCounts);
        assertFalse(aWidget.mIsDown);
    }

    @Test
    void leftMiddleAndRightButtonsTrackTheActualButtonMask() {
        WidgetManager aManager = newManager();

        aManager.mouseDown(0, 0, 1);
        assertTrue(aManager.isLeftButtonDown());
        aManager.mouseUp(0, 0, 1);
        assertFalse(aManager.isLeftButtonDown());

        aManager.mouseDown(0, 0, 3);
        assertTrue(aManager.isMiddleButtonDown());
        assertEquals(2, aManager.mLastDownButtonId);
        aManager.mouseUp(0, 0, 3);
        assertFalse(aManager.isMiddleButtonDown());

        aManager.mouseDown(0, 0, -1);
        assertTrue(aManager.isRightButtonDown());
        assertEquals(-1, aManager.mLastDownButtonId);
        aManager.mouseUp(0, 0, -1);
        assertFalse(aManager.isRightButtonDown());
    }

    @Test
    void modalBaseWidgetBlocksMouseAndFocusBeloyIt() {
        WidgetManager aManager = newManager();
        TestWidget aBelow = new TestWidget(0, 0, 100, 100);
        TestWidget aModal = new TestWidget(200, 0, 100, 100);
        aManager.addWidget(aBelow);
        aManager.addWidget(aModal);

        aManager.addBaseModal(aModal);

        assertSame(aModal, aManager.mBaseModalWidget);
        assertEquals(WidgetManager.WIDGETFLAGS_ALLOW_MOUSE | WidgetManager.WIDGETFLAGS_ALLOW_FOCUS,
                aManager.mDefaultBelowModalFlagsMod.mRemoveFlags);

        // A point only inside the widget below the modal must not hit anything: its
        // WIDGETFLAGS_ALLOW_MOUSE is removed by the below-modal flags mod.
        aManager.mousePosition(10, 10);
        assertNull(aManager.mOverWidget);
        assertFalse(aBelow.mIsOver);

        // The modal itself still receives the mouse.
        aManager.mousePosition(210, 10);
        assertSame(aModal, aManager.mOverWidget);
    }

    @Test
    void addBaseModalPushesAndRemoveBaseModalRestoresFocusAndFlags() {
        WidgetManager aManager = newManager();
        TestWidget aBelow = new TestWidget(0, 0, 100, 100);
        TestWidget aModal = new TestWidget(200, 0, 100, 100);
        aManager.addWidget(aBelow);
        aManager.addWidget(aModal);
        aManager.setFocus(aBelow);
        assertTrue(aBelow.mHasFocus);
        aBelow.mEvents.clear();

        int aSavedOverFlags = aManager.mWidgetFlags;
        aManager.addBaseModal(aModal);

        assertNull(aManager.mFocusWidget, "a widget below the modal loses focus");
        assertTrue(aBelow.mEvents.contains("lostFocus"));
        assertEquals(1, aManager.mPreModalInfoList.size());

        aManager.removeBaseModal(aModal);

        assertSame(aBelow, aManager.mFocusWidget, "the pre-modal focus is restored");
        assertTrue(aBelow.mHasFocus);
        assertEquals(aSavedOverFlags, aManager.mWidgetFlags);
        assertEquals(0, aManager.mPreModalInfoList.size());
    }

    @Test
    void initModalFlagsSplitsOverAndUnderFlagWords() {
        WidgetManager aManager = newManager();
        TestWidget aBelow = new TestWidget(0, 0, 100, 100);
        TestWidget aModal = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aBelow);
        aManager.addWidget(aModal);

        ModalFlags aFlags = new ModalFlags();
        aManager.initModalFlags(aFlags);
        assertTrue(aFlags.mIsOver, "without a modal everything is 'over'");
        assertTrue((aFlags.getFlags() & WidgetManager.WIDGETFLAGS_ALLOW_MOUSE) != 0);

        aManager.addBaseModal(aModal);
        aManager.initModalFlags(aFlags);
        assertFalse(aFlags.mIsOver);
        assertEquals(aManager.mWidgetFlags, aFlags.mOverFlags);
        assertEquals(0, aFlags.mUnderFlags & WidgetManager.WIDGETFLAGS_ALLOW_MOUSE,
                "widgets below the modal lose mouse access");
        assertEquals(0, aFlags.mUnderFlags & WidgetManager.WIDGETFLAGS_ALLOW_FOCUS);
    }

    @Test
    void lostFocusSynthesizesKeyUpsAndClearsTheButtonMask() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.setFocus(aWidget);
        aManager.keyDown((int) 'A');
        aManager.mouseDown(10, 10, 1);
        aWidget.mEvents.clear();

        aManager.lostFocus();

        assertFalse(aManager.mHasFocus);
        assertFalse(aManager.mKeyDown[(int) 'A'], "held keys are released on focus loss");
        assertEquals(0, aManager.mActualDownButtons);
        assertTrue(aWidget.mEvents.contains("lostFocus"));

        aManager.gotFocus();
        assertTrue(aManager.mHasFocus);
        assertTrue(aWidget.mEvents.contains("gotFocus"));
    }

    @Test
    void updateFrameAdvancesTheUpdateCountAndUpdatesEachWidgetOnce() {
        WidgetManager aManager = newManager();
        final int[] anUpdates = new int[1];
        TestWidget aWidget = new TestWidget(0, 0, 100, 100) {
            @Override
            public void update() {
                anUpdates[0]++;
                super.update();
            }
        };
        aManager.addWidget(aWidget);

        aManager.updateFrame();
        aManager.updateFrame();

        assertEquals(2, aManager.mUpdateCnt);
        assertEquals(2, anUpdates[0]);
        assertEquals(aManager.mUpdateCnt, aManager.mLastWMUpdateCount);
    }

    @Test
    void mouseWheelIsDeliveredOnlyToTheFocusedWidget() {
        WidgetManager aManager = newManager();
        TestWidget aFirst = new TestWidget(0, 0, 100, 100);
        TestWidget aSecond = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aFirst);
        aManager.addWidget(aSecond);
        // WidgetManager::SetFocus delivers GotFocus to the newly focused widget whenever the manager
        // has focus (WidgetManager.cpp:338-353), and its constructor pins mHasFocus = true
        // (WidgetManager.cpp:55), so the focus call itself is recorded before the wheel.
        aManager.setFocus(aSecond);

        aManager.mouseWheel(3);

        assertEquals(List.of("gotFocus", "wheel:3"), aSecond.mEvents);
        assertTrue(aFirst.mEvents.isEmpty());
    }

    @Test
    void mouseExitClearsTheOverWidgetAndMouseIn() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.mouseMove(10, 10);
        assertSame(aWidget, aManager.mOverWidget);
        assertTrue(aManager.mMouseIn);

        aManager.mouseExit(10, 10);

        assertNull(aManager.mOverWidget);
        assertFalse(aManager.mMouseIn);
        assertTrue(aWidget.mEvents.contains("leave"));
    }

    @Test
    void remapMouseMapsWindowPixelsIntoLogicalSpace() {
        WidgetManager aManager = newManager();
        aManager.resize(new Rect(50, 25, 200, 100), new Rect(0, 0, 400, 200));

        assertEquals(300, aManager.mWidth); // mWidth = dest.mWidth + 2 * dest.mX
        assertEquals(150, aManager.mHeight);

        int[] anX = {100};
        int[] aY = {50};
        aManager.remapMouse(anX, aY);

        assertEquals(100 * 200 / 400 + 50, anX[0]);
        assertEquals(50 * 100 / 200 + 25, aY[0]);
    }

    @Test
    void deferredOverlaysAreDrawnInPriorityOrder() {
        WidgetManager aManager = newManager();
        final List<String> anOrder = new ArrayList<>();
        TestWidget aLow = new TestWidget(0, 0, 10, 10) {
            @Override
            public void drawOverlay(Graphics g, int thePriority) {
                anOrder.add("low");
            }
        };
        TestWidget aHigh = new TestWidget(0, 0, 10, 10) {
            @Override
            public void drawOverlay(Graphics g, int thePriority) {
                anOrder.add("high");
            }
        };
        aManager.addWidget(aLow);
        aManager.addWidget(aHigh);
        aManager.mCurG = newTarget();

        aManager.deferOverlay(aHigh, 5);
        aManager.deferOverlay(aLow, 1);

        aManager.flushDeferredOverlayWidgets(0x7FFFFFFF);

        assertEquals(List.of("low", "high"), anOrder);
        assertTrue(aManager.mDeferredOverlayWidgets.isEmpty());
        assertEquals(0x7FFFFFFF, aManager.mMinDeferredOverlayPriority);
    }

    @Test
    void setFocusRejectsForeignWidgets() {
        WidgetManager aManager = newManager();
        WidgetManager anotherManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        anotherManager.addWidget(aWidget);

        aManager.setFocus(aWidget);
        assertNull(aManager.mFocusWidget, "focus only accepts widgets owned by this manager");

        anotherManager.setFocus(aWidget);
        assertSame(aWidget, anotherManager.mFocusWidget);
    }

    @Test
    void disableWidgetDropsFocusCaptureAndOverState() {
        WidgetManager aManager = newManager();
        TestWidget aWidget = new TestWidget(0, 0, 100, 100);
        aManager.addWidget(aWidget);
        aManager.mouseDown(10, 10, 1);
        aManager.setFocus(aWidget);

        aManager.disableWidget(aWidget);

        assertNull(aManager.mOverWidget);
        assertNull(aManager.mLastDownWidget);
        assertNull(aManager.mFocusWidget);
        assertEquals(0, aManager.mDownButtons);
    }
}
