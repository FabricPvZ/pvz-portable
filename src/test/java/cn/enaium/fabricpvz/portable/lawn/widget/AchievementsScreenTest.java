/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.AchievementsScreen and ReportAchievement.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.AchievementId;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;

/** Headless data/geometry and interaction checks for {@link AchievementsScreen}. */
class AchievementsScreenTest {

    @Test
    void achievementListIsComplete() {
        assertEquals(AchievementId.MAX_ACHIEVEMENTS, AchievementsScreen.gAchievementList.length);
        assertEquals(20, AchievementsScreen.gAchievementList.length);
        assertEquals("Home Lawn Security", AchievementsScreen.gAchievementList[0].name);
        assertEquals("Mustache Mode", AchievementsScreen.gAchievementList[19].name);
    }

    @Test
    void constructorInitialisesScrollState() {
        LawnApp app = new LawnApp();
        AchievementsScreen aScreen = new AchievementsScreen(app);

        assertEquals(800, aScreen.mWidth);
        assertEquals(-1, aScreen.mScrollDirection);
        assertEquals(30, aScreen.mDefaultScrollValue);
        assertEquals(1, aScreen.mScrollDecay);
        assertEquals(0, aScreen.mScrollValue);
        assertFalse(aScreen.mDidPressMoreButton);
    }

    @Test
    void scrollInteractionRoutesThroughDirectionAndValue() {
        LawnApp app = new LawnApp();
        AchievementsScreen aScreen = new AchievementsScreen(app);

        aScreen.mouseWheel(1);
        assertEquals(1, aScreen.mScrollDirection);
        assertEquals(30, aScreen.mScrollValue);

        aScreen.keyDown(KeyCode.KEYCODE_UP);
        assertEquals(1, aScreen.mScrollDirection);

        aScreen.keyDown(KeyCode.KEYCODE_DOWN);
        assertEquals(-1, aScreen.mScrollDirection);
    }

    @Test
    void giveAchievementSetsEarnedFlagOnce() {
        LawnApp app = new LawnApp();
        app.mPlayerInfo = new PlayerInfo();

        ReportAchievement.giveAchievement(app, AchievementId.HomeSecurity, false);
        assertTrue(app.mPlayerInfo.mEarnedAchievements[AchievementId.HomeSecurity]);

        // Second grant is a no-op (already earned).
        app.mPlayerInfo.mEarnedAchievements[AchievementId.HomeSecurity] = false;
        ReportAchievement.giveAchievement(app, AchievementId.HomeSecurity, false);
        assertTrue(app.mPlayerInfo.mEarnedAchievements[AchievementId.HomeSecurity]);

        // No player info -> silent no-op.
        app.mPlayerInfo = null;
        ReportAchievement.giveAchievement(app, AchievementId.Zombologist, true);
    }

    @Test
    void achievementIdValuesMatchUpstreamOrder() {
        assertEquals(0, AchievementId.HomeSecurity);
        assertEquals(1, AchievementId.NovelPeasPrize);
        assertEquals(10, AchievementId.Zombologist);
        assertEquals(19, AchievementId.MustacheMode);
        assertEquals(20, AchievementId.MAX_ACHIEVEMENTS);
    }
}
