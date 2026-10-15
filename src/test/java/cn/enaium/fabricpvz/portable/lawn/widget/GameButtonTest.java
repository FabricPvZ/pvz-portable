/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.widget.GameButton and its DialogButton family.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.sexy.ButtonListener;

/**
 * Headless state-machine tests for {@link GameButton} plus the click-callback wiring of the
 * {@link LawnStoneButton}/{@link NewLawnButton} family. No retail art is needed: the ctor, label
 * translation, hover/down/disabled state and the listener path are all asset-free.
 */
class GameButtonTest {

    /** Records the {@link ButtonListener} callbacks a dialog button fires. */
    private static final class RecordingListener implements ButtonListener {
        int pressed;
        int depressed = -1;
        int downTicks;

        @Override
        public void buttonPress(int theId) {
            pressed++;
        }

        @Override
        public void buttonDepress(int theId) {
            depressed = theId;
        }

        @Override
        public void buttonDownTick(int theId) {
            downTicks++;
        }

        @Override
        public void buttonMouseEnter(int theId) {
        }

        @Override
        public void buttonMouseLeave(int theId) {
        }

        @Override
        public void buttonMouseMove(int theId, int theX, int theY) {
        }
    }

    @Test
    void hoverDownDisabledTransitions() {
        LawnApp app = new LawnApp();
        GameButton aButton = new GameButton(7);
        aButton.resize(10, 10, 100, 40);

        // mouse inside the button rect (GameButton::IsMouseOver, GameButton.cpp:229)
        app.mWidgetManager.mLastMouseX = 50;
        app.mWidgetManager.mLastMouseY = 20;
        aButton.update();
        assertTrue(aButton.mIsOver, "mouse inside the rect hovers");
        assertFalse(aButton.mIsDown, "no mouse button held yet");
        assertFalse(aButton.isButtonDown());

        // mDownButtons bit 0 is the left button; with no dialogs the focus check is skipped
        app.mWidgetManager.mDownButtons = 1;
        aButton.update();
        assertTrue(aButton.mIsDown, "left button held");
        assertTrue(aButton.isButtonDown(), "down + over + enabled => IsButtonDown");

        // leaving the rect clears hover (and therefore IsButtonDown)
        app.mWidgetManager.mLastMouseX = 0;
        aButton.update();
        assertFalse(aButton.mIsOver);
        assertFalse(aButton.isButtonDown());

        // disabled swallows hover entirely
        app.mWidgetManager.mLastMouseX = 50;
        aButton.setDisabled(true);
        aButton.update();
        assertFalse(aButton.mIsOver);
    }

    @Test
    void hoverAlphaRampsInAndOut() {
        LawnApp app = new LawnApp();
        GameButton aButton = new GameButton(1);
        aButton.resize(0, 0, 100, 40);
        aButton.mOverAlphaFadeInSpeed = 0.25;
        app.mWidgetManager.mLastMouseX = 50;
        app.mWidgetManager.mLastMouseY = 20;

        aButton.update();
        assertTrue(aButton.mOverAlpha > 0, "fades in while hovered");
        aButton.update();
        assertEquals(0.5, aButton.mOverAlpha, 1e-9);

        // leaving fades back out at mOverAlphaSpeed
        aButton.mOverAlphaSpeed = 0.25;
        app.mWidgetManager.mLastMouseX = 999;
        aButton.update();
        assertEquals(0.25, aButton.mOverAlpha, 1e-9);
        aButton.update();
        assertEquals(0.0, aButton.mOverAlpha, 1e-9);
    }

    @Test
    void setLabelTranslatesStrings() {
        new LawnApp();
        GameButton aButton = new GameButton(3);
        aButton.setLabel("PLAIN TEXT");
        assertEquals("PLAIN TEXT", aButton.mLabel, "non-key text is unchanged");

        aButton.setLabel("[NOT_A_REAL_KEY]");
        assertEquals("<Missing NOT_A_REAL_KEY>", aButton.mLabel,
                "PvzpStringTranslate of an unknown key");
    }

    @Test
    void stoneButtonClickFiresListener() {
        LawnApp app = new LawnApp();
        RecordingListener aListener = new RecordingListener();
        LawnStoneButton aButton = GameButton.makeButton(42, aListener, "[TEST_BUTTON]");

        aButton.mWidgetManager = app.mWidgetManager;
        app.mWidgetManager.mHasFocus = true;
        aButton.mIsOver = true;

        aButton.mouseUp(0, 0, 0, 1);
        assertEquals(42, aListener.depressed, "mouse-up over the button depresses its id");
    }

    @Test
    void factoriesApplyUpstreamDefaults() {
        new LawnApp();

        LawnStoneButton aStone = GameButton.makeButton(5, null, "Label");
        assertEquals(33, aStone.mHeight, "MakeButton sets the 33px height (GameButton.cpp:306)");
        assertEquals(1, aStone.mTranslateX);
        assertEquals(1, aStone.mTranslateY);
        assertTrue(aStone.mHasAlpha);
        assertTrue(aStone.mHasTransparencies);
        assertEquals("Label", aStone.mLabel);
    }
}
