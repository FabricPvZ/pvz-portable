/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.TitleScreen.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;

/** Headless geometry/state and interaction checks for {@link TitleScreen}. */
class TitleScreenTest {

    @Test
    void constructorInitialisesBarGeometryAndState() {
        LawnApp app = new LawnApp();
        TitleScreen aScreen = new TitleScreen(app);

        // Load-bar geometry constants (TitleScreen.cpp:40-42).
        assertEquals(314.0f, aScreen.mTotalBarWidth, 0.0f);
        assertEquals(0.2f, aScreen.mBarVel, 0.0f);
        assertEquals(0.0f, aScreen.mCurBarWidth, 0.0f);
        assertEquals(TitleScreen.TITLESTATE_WAITING_FOR_FIRST_DRAW, aScreen.mTitleState);
        assertEquals(KeyCode.KEYCODE_UNKNOWN, aScreen.mQuickLoadKey);
        assertNotNull(aScreen.mStartButton);
        assertTrue(aScreen.mStartButton.mDisabled);
        assertFalse(aScreen.mStartButton.mVisible);
        assertEquals(TitleScreen.TitleScreen_Start, aScreen.mStartButton.mId);
    }

    @Test
    void registerButtonClickIsRecorded() {
        LawnApp app = new LawnApp();
        TitleScreen aScreen = new TitleScreen(app);

        aScreen.buttonDepress(TitleScreen.TitleScreen_Register);

        assertTrue(aScreen.mRegisterClicked);
    }

    @Test
    void cheatKeyIsCapturedWhileLoading() {
        LawnApp app = new LawnApp();
        app.mCheatKeys = true;
        app.mPlayerInfo = new PlayerInfo();
        TitleScreen aScreen = new TitleScreen(app);

        aScreen.keyDown(0x41);

        assertEquals(0x41, aScreen.mQuickLoadKey);
    }
}
