/*
 * Tests for {@link GameSelector} (ported from src/Lawn/Widget/GameSelector.cpp:49-1388).
 *
 * The constructor needs the selector images and reanimations, so most tests cover the
 * image-independent surface: the button id table, the selector animation state constants, the pure
 * profile-unlock decision behind SyncProfile and the fixed layout coordinates; the one test that
 * builds a real selector uses the shared headless fixture (stub resources) instead of retail art.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;

class GameSelectorTest {

    /**
     * A {@link cn.enaium.fabricpvz.portable.lawn.LawnApp} whose reanimations look loaded. Without retail .reanim
     * data every definition parses to zero tracks, so {@code mTrackInstances} stays null
     * (Reanimator.cpp:397-421) while {@code GameSelector::SyncButtons} indexes it through
     * {@code GetTrackInstanceByName} (Reanimator.cpp:974-977).
     */
    static final class LoadedReanimLawnApp extends cn.enaium.fabricpvz.portable.lawn.LawnApp {
        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            cn.enaium.fabricpvz.portable.test.HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    // GameSelector.h:55-67
    @Test
    void buttonIdsMatchUpstreamValues() {
        assertEquals(100, GameSelector.GameSelector_Adventure);
        assertEquals(101, GameSelector.GameSelector_Minigame);
        assertEquals(102, GameSelector.GameSelector_Puzzle);
        assertEquals(103, GameSelector.GameSelector_Options);
        assertEquals(104, GameSelector.GameSelector_Help);
        assertEquals(105, GameSelector.GameSelector_Quit);
        assertEquals(106, GameSelector.GameSelector_ChangeUser);
        assertEquals(107, GameSelector.GameSelector_Store);
        assertEquals(108, GameSelector.GameSelector_Almanac);
        assertEquals(109, GameSelector.GameSelector_ZenGarden);
        assertEquals(110, GameSelector.GameSelector_Survival);
        assertEquals(111, GameSelector.GameSelector_Zombatar);
        assertEquals(112, GameSelector.GameSelector_Achievements);
    }

    @Test
    void buttonIdsAreContiguousFrom100To112() {
        int[] anIds = {
            GameSelector.GameSelector_Adventure,
            GameSelector.GameSelector_Minigame,
            GameSelector.GameSelector_Puzzle,
            GameSelector.GameSelector_Options,
            GameSelector.GameSelector_Help,
            GameSelector.GameSelector_Quit,
            GameSelector.GameSelector_ChangeUser,
            GameSelector.GameSelector_Store,
            GameSelector.GameSelector_Almanac,
            GameSelector.GameSelector_ZenGarden,
            GameSelector.GameSelector_Survival,
            GameSelector.GameSelector_Zombatar,
            GameSelector.GameSelector_Achievements
        };
        assertEquals(13, anIds.length);
        for (int i = 0; i < anIds.length; i++) {
            assertEquals(100 + i, anIds[i]);
        }
    }

    // GameSelector.h:42-47
    @Test
    void selectorAnimStateConstants() {
        assertEquals(0, GameSelector.SELECTOR_OPEN);
        assertEquals(1, GameSelector.SELECTOR_NEW_USER);
        assertEquals(2, GameSelector.SELECTOR_SHOW_SIGN);
        assertEquals(3, GameSelector.SELECTOR_IDLE);
    }

    // GameSelector.cpp:49 (gFlowerCenter)
    @Test
    void flowerCentersMatchUpstream() {
        assertEquals(3, GameSelector.gFlowerCenter.length);
        assertEquals(765.0f, GameSelector.gFlowerCenter[0][0]);
        assertEquals(483.0f, GameSelector.gFlowerCenter[0][1]);
        assertEquals(663.0f, GameSelector.gFlowerCenter[1][0]);
        assertEquals(455.0f, GameSelector.gFlowerCenter[1][1]);
        assertEquals(701.0f, GameSelector.gFlowerCenter[2][0]);
        assertEquals(439.0f, GameSelector.gFlowerCenter[2][1]);
    }

    // ------------------------------------------------------------------
    // computeSyncProfileState — GameSelector.cpp:497-529
    // ------------------------------------------------------------------

    private static GameSelector.SyncProfileState sync(int theLevel, boolean theFinishedAdventure,
            boolean theMinigames, boolean thePuzzle, boolean theSurvival, boolean theTrialLocked) {
        return GameSelector.computeSyncProfileState(theLevel, theFinishedAdventure, theMinigames,
                thePuzzle, theSurvival, theTrialLocked, true);
    }

    @Test
    void freshLevel1ProfileStartsAdventureWithEverythingLocked() {
        GameSelector.SyncProfileState aState = sync(1, false, false, false, false, false);
        assertEquals(1, aState.mLevel);
        assertTrue(aState.mShowStartButton);
        assertTrue(aState.mMinigamesLocked);
        assertTrue(aState.mPuzzleLocked);
        assertTrue(aState.mSurvivalLocked);
    }

    @Test
    void level2HidesStartButtonButKeepsModesLocked() {
        GameSelector.SyncProfileState aState = sync(2, false, false, false, false, false);
        assertEquals(2, aState.mLevel);
        assertFalse(aState.mShowStartButton);
        assertTrue(aState.mMinigamesLocked);
        assertTrue(aState.mPuzzleLocked);
        assertTrue(aState.mSurvivalLocked);
    }

    @Test
    void level25And45KeepStartButtonHidden() {
        assertFalse(sync(25, false, false, false, false, false).mShowStartButton);
        assertFalse(sync(45, false, false, false, false, false).mShowStartButton);
    }

    @Test
    void finishedAdventureUnlocksAllThreeModesAndHidesStartButton() {
        GameSelector.SyncProfileState aState = sync(1, true, false, false, false, false);
        assertFalse(aState.mShowStartButton);
        assertFalse(aState.mMinigamesLocked);
        assertFalse(aState.mPuzzleLocked);
        assertFalse(aState.mSurvivalLocked);
    }

    @Test
    void eachUnlockFlagOnlyOpensItsOwnMode() {
        GameSelector.SyncProfileState aMinigames = sync(1, false, true, false, false, false);
        assertFalse(aMinigames.mMinigamesLocked);
        assertTrue(aMinigames.mPuzzleLocked);
        assertTrue(aMinigames.mSurvivalLocked);
        assertTrue(aMinigames.mShowStartButton);

        GameSelector.SyncProfileState aPuzzle = sync(1, false, false, true, false, false);
        assertTrue(aPuzzle.mMinigamesLocked);
        assertFalse(aPuzzle.mPuzzleLocked);
        assertTrue(aPuzzle.mSurvivalLocked);

        GameSelector.SyncProfileState aSurvival = sync(1, false, false, false, true, false);
        assertTrue(aSurvival.mMinigamesLocked);
        assertTrue(aSurvival.mPuzzleLocked);
        assertFalse(aSurvival.mSurvivalLocked);
    }

    @Test
    void trialLockRelocksPuzzleAndSurvivalButNotMinigames() {
        GameSelector.SyncProfileState aState = sync(30, false, true, true, true, true);
        assertFalse(aState.mMinigamesLocked);
        assertTrue(aState.mPuzzleLocked);
        assertTrue(aState.mSurvivalLocked);
    }

    @Test
    void trialLockAfterAdventureKeepsMinigamesOpen() {
        GameSelector.SyncProfileState aState = sync(1, true, false, false, false, true);
        assertFalse(aState.mShowStartButton);
        assertFalse(aState.mMinigamesLocked);
        assertTrue(aState.mPuzzleLocked);
        assertTrue(aState.mSurvivalLocked);
    }

    @Test
    void withoutProfileTheLevelIsResetTo1AndEverythingStaysLocked() {
        // GameSelector.cpp:497-499 / :504: the whole unlock block is guarded by `mPlayerInfo && !IsIceDemo()`.
        GameSelector.SyncProfileState aState = GameSelector.computeSyncProfileState(
                45, true, true, true, true, false, false);
        assertEquals(1, aState.mLevel);
        assertTrue(aState.mShowStartButton);
        assertTrue(aState.mMinigamesLocked);
        assertTrue(aState.mPuzzleLocked);
        assertTrue(aState.mSurvivalLocked);
    }

    // ------------------------------------------------------------------
    // Geometry (GameSelector.cpp:168, :257, :270) — the image-independent parts
    // ------------------------------------------------------------------

    @Test
    void storeAndAlmanacButtonCoordinatesMatchUpstream() {
        assertEquals(405, GameSelector.STORE_BUTTON_X);
        assertEquals(484, GameSelector.STORE_BUTTON_Y);
        assertEquals(327, GameSelector.ALMANAC_BUTTON_X);
        assertEquals(428, GameSelector.ALMANAC_BUTTON_Y);
    }

    @Test
    void achievementsButtonHangsThirtyFivePixelsAboveTheBottom() {
        // mApp->mHeight - PEDESTAL->mHeight - 35; the 35 px gap and the x origin do not depend on the image.
        assertEquals(20, GameSelector.ACHIEVEMENTS_BUTTON_X);
        assertEquals(35, GameSelector.ACHIEVEMENTS_BUTTON_BOTTOM_MARGIN);
        int anAppHeight = 600;
        int aPedestalHeight = 88;
        assertEquals(anAppHeight - aPedestalHeight - GameSelector.ACHIEVEMENTS_BUTTON_BOTTOM_MARGIN,
                anAppHeight - aPedestalHeight - 35);
    }

    @Test
    void adventureButtonIdRoutesToClickedAdventure() {
        cn.enaium.fabricpvz.portable.lawn.LawnApp app = new LoadedReanimLawnApp();
        // The selector ctor reads the image table (button rectangles) and the reanimation services
        // (the zombie hand); the shared headless fixture binds a stub image for every resource id and
        // makes the unloaded reanimations indexable, so no retail art is needed.
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);

        GameSelector aSelector = new GameSelector(app);
        // GameSelector_Adventure (100) -> ClickedAdventure() (GameSelector.cpp:1199-1201): the selector
        // enters the zombie-hand "starting game" state and disables every button.
        aSelector.buttonDepress(GameSelector.GameSelector_Adventure);

        assertTrue(aSelector.mStartingGame);
        assertTrue(aSelector.mAdventureButton.mDisabled);
    }

    // ------------------------------------------------------------------
    // Zombatar wiring — GameSelector.cpp:347, :951, :959, :966, :749, :1374-1382 and
    // GameSelector.h:112 (std::unique_ptr<ZombatarWidget> mZombatarWidget)
    // ------------------------------------------------------------------

    /** Builds a fully wired headless selector whose profile has {@code mZombatarAccepted = theAccepted}. */
    private static GameSelector newSelectorWithAccepted(int theAccepted) {
        cn.enaium.fabricpvz.portable.lawn.LawnApp app = new LoadedReanimLawnApp();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);

        PlayerInfo aProfile = new PlayerInfo();
        aProfile.mZombatarAccepted = theAccepted;
        app.mPlayerInfo = aProfile;

        GameSelector aSelector = new GameSelector(app);
        // Wire the selector into the manager so AddedToManager (GameSelector.cpp:951-952) runs and
        // mZombatarWidget->Open()'s BringToFront/SetFocus calls have a live WidgetManager.
        app.mWidgetManager.addWidget(aSelector);
        return aSelector;
    }

    @Test
    void zombatarWidgetIsConstructedAsTheConcreteWidget() {
        // GameSelector.cpp:347: mZombatarWidget = std::make_unique<ZombatarWidget>(this);
        // GameSelector.h:112 types the field as ZombatarWidget, not the Widget base.
        GameSelector aSelector = newSelectorWithAccepted(1);
        assertNotNull(aSelector.mZombatarWidget);
        assertInstanceOf(ZombatarWidget.class, aSelector.mZombatarWidget);
    }

    @Test
    void showZombatarScreenOpensTheEditorWhenTermsAccepted() {
        // GameSelector.cpp:1376-1381: when the profile exists and mZombatarAccepted != 0, upstream
        // calls mZombatarWidget->Open(), which slides the selector to (-BOARD_WIDTH, 0). The base
        // Widget had no Open(), so before this port the branch did nothing.
        GameSelector aSelector = newSelectorWithAccepted(1);
        assertEquals(0, aSelector.mDestX);

        aSelector.showZombatarScreen();

        assertEquals(-GameConstants.BOARD_WIDTH, aSelector.mDestX);
        assertEquals(75, aSelector.mSlideCounter);
        assertNull(aSelector.mApp.getDialog(Dialogs.DIALOG_ZOMBATAR_TOS));
    }

    @Test
    void showZombatarScreenShowsTermsWhenNotAccepted() {
        // GameSelector.cpp:1374-1381: mZombatarAccepted == 0 routes to ShowZombatarTOS() and never
        // opens the editor (the selector is not slid).
        GameSelector aSelector = newSelectorWithAccepted(0);

        aSelector.showZombatarScreen();

        assertNotNull(aSelector.mApp.getDialog(Dialogs.DIALOG_ZOMBATAR_TOS));
        assertEquals(0, aSelector.mDestX);
        assertEquals(0, aSelector.mSlideCounter);
    }

    @Test
    void zombatarButtonDepressRoutesToShowZombatarScreen() {
        // GameSelector.cpp:1245-1251: ButtonDepress(GameSelector_Zombatar) calls ShowZombatarScreen().
        GameSelector aSelector = newSelectorWithAccepted(1);

        aSelector.buttonDepress(GameSelector.GameSelector_Zombatar);

        assertEquals(-GameConstants.BOARD_WIDTH, aSelector.mDestX);
        assertEquals(75, aSelector.mSlideCounter);
    }
}
