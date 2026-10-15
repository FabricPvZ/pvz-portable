package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Headless tests for the ported weather mini-game helpers
 * (src/Lawn/Challenge.cpp:1681-1760, 2764-2863, 4825-4853).
 *
 * <p>Drives {@link ChallengeWeather} against a real {@link Board}/{@link Challenge} built through
 * {@link LawnEntityTestSupport}, asserting the rain-seed award, the stormy-night flash state machine
 * (including the thunder cue and the won/lost branches), the rain splash spawns and the overlay
 * draw counts. No window or retail assets are required.
 */
class ChallengeWeatherTest {

    /** A {@link Graphics} that counts the blits/fills weather code issues into the no-op target. */
    static class RecordingGraphics extends Graphics {
        int mDrawImageCalls;
        int mFillRectCalls;
        final List<Color> mFillColors = new ArrayList<>();
        final List<int[]> mFillRects = new ArrayList<>();

        @Override
        public void drawImage(Image theImage, int theX, int theY) {
            mDrawImageCalls++;
            super.drawImage(theImage, theX, theY);
        }

        @Override
        public void fillRect(int theX, int theY, int theWidth, int theHeight) {
            mFillRectCalls++;
            Color aColor = getColor();
            mFillColors.add(new Color(aColor.mRed, aColor.mGreen, aColor.mBlue, aColor.mAlpha));
            mFillRects.add(new int[] {theX, theY, theWidth, theHeight});
            super.fillRect(theX, theY, theWidth, theHeight);
        }
    }

    private LawnEntityTestSupport.TestLawnApp mApp;
    private Board mBoard;
    private Challenge mChallenge;
    private ChallengeWeather mWeather;

    @BeforeEach
    void setUp() {
        mApp = LawnEntityTestSupport.newApp();
        mApp.mGameScene = GameScenes.SCENE_PLAYING;
        mApp.mPlayerInfo.mLevel = 50;
        mBoard = new Board(mApp);
        mBoard.mLevel = 1;
        mBoard.mMainCounter = 5;
        mBoard.mEffectCounter = 0;
        mChallenge = mBoard.mChallenge;
        mWeather = new ChallengeWeather(mChallenge);
    }

    private Coin findUsableSeedPacket() {
        for (Coin aCoin : mBoard.mCoins) {
            if (aCoin.mType == CoinType.COIN_USABLE_SEED_PACKET) {
                return aCoin;
            }
        }
        return null;
    }

    private static boolean isExcludedSeed(int theSeedType) {
        return theSeedType == SeedType.SEED_NONE
                || theSeedType == SeedType.SEED_SUNFLOWER
                || theSeedType == SeedType.SEED_TWINSUNFLOWER
                || theSeedType == SeedType.SEED_INSTANT_COFFEE
                || theSeedType == SeedType.SEED_UMBRELLA
                || theSeedType == SeedType.SEED_SUNSHROOM
                || theSeedType == SeedType.SEED_IMITATER;
    }

    // -------------------------------------------------------------------------------------
    // UpdateRainingSeeds
    // -------------------------------------------------------------------------------------

    @Test
    void rainingSeedsDropsAUsableSeedPacketAndReschedules() {
        mChallenge.mChallengeStateCounter = 1;

        mWeather.updateRainingSeeds();

        Coin aCoin = findUsableSeedPacket();
        assertNotNull(aCoin, "a usable seed packet coin must be dropped");
        assertEquals(60.0f, aCoin.mPosY);
        assertTrue(aCoin.mPosX >= 100 && aCoin.mPosX <= 649, "coin X uses the upstream 100..649 range");
        assertTrue(!isExcludedSeed(aCoin.mUsableSeedType), "the packet must hold an eligible seed");
        assertTrue(mChallenge.mChallengeStateCounter >= 500 && mChallenge.mChallengeStateCounter <= 999,
                "the counter is rescheduled into 500..999");
    }

    @Test
    void rainingSeedsWaitsForTheAwardDrop() {
        mChallenge.mChallengeStateCounter = 1;
        mBoard.mLevelAwardSpawned = true;

        mWeather.updateRainingSeeds();

        assertEquals(1, mChallenge.mChallengeStateCounter, "the counter is untouched while the award is pending");
        assertNull(findUsableSeedPacket());
    }

    // -------------------------------------------------------------------------------------
    // UpdateStormyNight
    // -------------------------------------------------------------------------------------

    @Test
    void stormyNightSchedulesAFlashWhilePlaying() {
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        mChallenge.mChallengeStateCounter = 1;

        mWeather.updateStormyNight();

        assertTrue(mChallenge.mChallengeState >= ChallengeState.STATECHALLENGE_STORM_FLASH_1
                        && mChallenge.mChallengeState <= ChallengeState.STATECHALLENGE_STORM_FLASH_3,
                "a random flash state is selected");
        assertTrue(mChallenge.mChallengeStateCounter >= Challenge.STORM_FLASH_TIME + 300,
                "the countdown restarts at STORM_FLASH_TIME + 300..750");
    }

    @Test
    void stormyNightPortalsYetiCountdownToTheShorterRange() {
        LawnEntityTestSupport.StubBoard aBoard = LawnEntityTestSupport.newStubBoard(mApp);
        ChallengeWeather aWeather = new ChallengeWeather(aBoard.mChallenge);
        LawnEntityTestSupport.StubZombie aYeti = aBoard.addStubZombie(0, 100, 100);
        aYeti.mZombieType = ZombieType.ZOMBIE_YETI;
        aBoard.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        aBoard.mChallenge.mChallengeStateCounter = 1;

        aWeather.updateStormyNight();

        assertTrue(aBoard.mChallenge.mChallengeStateCounter >= Challenge.STORM_FLASH_TIME + 200
                        && aBoard.mChallenge.mChallengeStateCounter <= Challenge.STORM_FLASH_TIME + 300,
                "a present yeti shortens the countdown to STORM_FLASH_TIME + 200..300");
    }

    @Test
    void stormyNightPlaysThunderAtTheCountdownMarks() {
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_1;
        mChallenge.mChallengeStateCounter = 301;

        mWeather.updateStormyNight();

        assertTrue(mApp.mFoleysPlayed.contains(FoleyType.FOLEY_THUNDER),
                "the counter hitting 300 in a flash state cues the thunder foley");
        assertEquals(300, mChallenge.mChallengeStateCounter);
    }

    @Test
    void stormyNightEndsInTheWonScene() {
        mApp.mGameScene = GameScenes.SCENE_ZOMBIES_WON;
        mChallenge.mChallengeStateCounter = 1;

        mWeather.updateStormyNight();

        assertEquals(ChallengeState.STATECHALLENGE_STORM_FLASH_3, mChallenge.mChallengeState);
        assertTrue(mChallenge.mChallengeStateCounter >= Challenge.STORM_FLASH_TIME - 50
                        && mChallenge.mChallengeStateCounter <= Challenge.STORM_FLASH_TIME + 50,
                "the won scene uses STORM_FLASH_TIME + -50..50");
    }

    @Test
    void stormyNightResetsOutsidePlayAndWonScenes() {
        mApp.mGameScene = GameScenes.SCENE_MENU;
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_2;
        mChallenge.mChallengeStateCounter = 0;

        mWeather.updateStormyNight();

        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, mChallenge.mChallengeState);
        assertEquals(0, mChallenge.mChallengeStateCounter);
    }

    @Test
    void stormyNightPauseFreezesAFinishedFlash() {
        mBoard.mPaused = true;
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_1;
        mChallenge.mChallengeStateCounter = 1;

        mWeather.updateStormyNight();

        assertEquals(1, mChallenge.mChallengeStateCounter, "a paused finished flash does not tick");
    }

    // -------------------------------------------------------------------------------------
    // UpdateRain
    // -------------------------------------------------------------------------------------

    @Test
    void updateRainSpawnsThreeSplashesAndReschedules() {
        mChallenge.mRainCounter = 0;

        mWeather.updateRain();

        assertEquals(3, mApp.mAddedReanimations, "each rain tick spawns splash, circle and splash");
        assertNotNull(mApp.mReanim.mColorOverride);
        assertEquals(255, mApp.mReanim.mColorOverride.mRed);
        assertEquals(255, mApp.mReanim.mColorOverride.mGreen);
        assertEquals(255, mApp.mReanim.mColorOverride.mBlue);
        assertTrue(mApp.mReanim.mScaleX >= 0.7f && mApp.mReanim.mScaleX <= 1.2f);
        assertTrue(mChallenge.mRainCounter >= 10 && mChallenge.mRainCounter <= 20);
    }

    @Test
    void updateRainWaitsWhileTheCounterIsPositive() {
        mChallenge.mRainCounter = 5;

        mWeather.updateRain();

        assertEquals(4, mChallenge.mRainCounter);
        assertEquals(0, mApp.mAddedReanimations);
    }

    @Test
    void updateRainIsSuppressedDuringPreloading() {
        mChallenge.mRainCounter = 0;
        mApp.mGameScene = GameScenes.SCENE_LEVEL_INTRO;

        mWeather.updateRain();

        assertEquals(0, mApp.mAddedReanimations);
        assertEquals(-1, mChallenge.mRainCounter, "the counter still decrements");
    }

    // -------------------------------------------------------------------------------------
    // DrawRain / DrawStormFlash / DrawStormNight
    // -------------------------------------------------------------------------------------

    @Test
    void drawRainBlitsTheDistantGrid() {
        RecordingGraphics aGraphics = new RecordingGraphics();

        mWeather.drawRain(aGraphics);

        assertEquals(9 * 7, aGraphics.mDrawImageCalls,
                "the distant rain is a 9x7 grid of unscaled blits (the near grid uses the scaled path)");
    }

    @Test
    void drawRainIsSkippedBeforePreloading() {
        RecordingGraphics aGraphics = new RecordingGraphics();
        mApp.mGameScene = GameScenes.SCENE_LEVEL_INTRO;

        mWeather.drawRain(aGraphics);

        assertEquals(0, aGraphics.mDrawImageCalls);
    }

    @Test
    void drawStormFlashWritesTwoFullScreenPasses() {
        RecordingGraphics aGraphics = new RecordingGraphics();

        mWeather.drawStormFlash(aGraphics, 150, 255);

        assertEquals(2, aGraphics.mFillRectCalls);
        for (int[] aRect : aGraphics.mFillRects) {
            assertEquals(-1000, aRect[0]);
            assertEquals(-1000, aRect[1]);
            assertEquals(2800, aRect[2]);
            assertEquals(2600, aRect[3]);
        }
        assertTrue(aGraphics.mFillColors.get(0).mAlpha >= 0 && aGraphics.mFillColors.get(0).mAlpha <= 255,
                "the darkness alpha is clamped to a byte");
        assertEquals(PvzpCommon.pvzpAnimateCurve(150, 75, 150, 255, 0, PvzpCurves.CURVE_LINEAR),
                aGraphics.mFillColors.get(1).mAlpha);
    }

    @Test
    void drawStormNightBlacksOutWhenNotFlashing() {
        RecordingGraphics aGraphics = new RecordingGraphics();
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;

        mWeather.drawStormNight(aGraphics);

        assertTrue(aGraphics.mFillRectCalls >= 1);
        Color aBlack = aGraphics.mFillColors.get(0);
        assertEquals(0, aBlack.mRed);
        assertEquals(0, aBlack.mGreen);
        assertEquals(0, aBlack.mBlue);
        assertEquals(255, aBlack.mAlpha);
        assertEquals(GameConstants.BOARD_WIDTH + 2000, aGraphics.mFillRects.get(0)[2]);
        assertEquals(GameConstants.BOARD_HEIGHT + 2000, aGraphics.mFillRects.get(0)[3]);
    }

    @Test
    void drawStormNightFlashesDuringTheFirstFlashState() {
        RecordingGraphics aGraphics = new RecordingGraphics();
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_1;
        mChallenge.mChallengeStateCounter = 100;

        mWeather.drawStormNight(aGraphics);

        // The flash is two full-screen passes; the board UI drawn afterwards may add its own fills.
        assertTrue(aGraphics.mFillRectCalls >= 2);
        assertEquals(2800, aGraphics.mFillRects.get(0)[2]);
        assertEquals(2600, aGraphics.mFillRects.get(0)[3]);
        assertEquals(2800, aGraphics.mFillRects.get(1)[2]);
        assertEquals(2600, aGraphics.mFillRects.get(1)[3]);
    }
}
