package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachmentIDRef;
import cn.enaium.fabricpvz.portable.sexy.Common;

/**
 * Headless tests for the ported {@link Coin} (src/Lawn/Coin.cpp).
 *
 * <p>Every {@link CoinType} is classified/valued at least once, every {@link CoinMotion} is
 * initialised under a fixed MTRand seed, and the fall/collection/expiry paths are asserted against
 * hand-computed values. No assets or window are required.
 */
class CoinTest {

    static class RecordingLawnApp extends LawnApp {
        final List<Integer> foleys = new ArrayList<>();
        final List<Long> samples = new ArrayList<>();

        @Override
        public void playFoley(int theFoleyType) {
            foleys.add(theFoleyType);
        }

        @Override
        public void playSample(long theSoundNum) {
            samples.add(theSoundNum);
        }
    }

    private RecordingLawnApp mApp;
    private Board mBoard;

    @BeforeEach
    void setUp() {
        mApp = new RecordingLawnApp();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(mApp);
        mBoard = new Board(mApp);
    }

    private Coin newCoin(int theType, int theMotion) {
        Coin aCoin = new Coin();
        aCoin.mApp = mApp;
        aCoin.mBoard = mBoard;
        aCoin.mType = theType;
        aCoin.mCoinMotion = theMotion;
        aCoin.mWidth = 60;
        aCoin.mHeight = 60;
        aCoin.mScale = 1.0f;
        aCoin.mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
        return aCoin;
    }

    // ------------------------------------------------------------ per-type tables

    @Test
    void classificationCoversEveryCoinType() {
        boolean[] seen = new boolean[CoinType.COIN_PRESENT_SURVIVAL_MODE + 1];
        for (int type = 0; type <= CoinType.COIN_PRESENT_SURVIVAL_MODE; type++) {
            Coin aCoin = newCoin(type, CoinMotion.COIN_MOTION_FROM_SKY);
            boolean sun = aCoin.isSun();
            boolean money = aCoin.isMoney();
            boolean levelAward = aCoin.isLevelAward();
            boolean present = aCoin.isPresentWithAdvice();

            assertFalse(sun && money, "type " + type + " cannot be both sun and money");
            assertFalse(sun && levelAward, "type " + type + " cannot be both sun and level award");
            assertEquals(Coin.isMoney(type), money);

            // Expected memberships from the upstream switch/if tables.
            assertTrue(sun == (type == CoinType.COIN_SUN || type == CoinType.COIN_SMALLSUN
                || type == CoinType.COIN_LARGESUN));
            assertTrue(money == (type == CoinType.COIN_SILVER || type == CoinType.COIN_GOLD
                || type == CoinType.COIN_DIAMOND));
            assertTrue(present == (type == CoinType.COIN_PRESENT_MINIGAMES
                || type == CoinType.COIN_PRESENT_PUZZLE_MODE || type == CoinType.COIN_PRESENT_SURVIVAL_MODE));
            seen[type] = true;
        }
        for (int type = 0; type < seen.length; type++) {
            assertTrue(seen[type], "coin type " + type + " was not classified");
        }
    }

    @Test
    void sunAndCoinValuesPerType() {
        for (int type = 0; type <= CoinType.COIN_PRESENT_SURVIVAL_MODE; type++) {
            Coin aCoin = newCoin(type, CoinMotion.COIN_MOTION_FROM_SKY);

            int expectedSun = type == CoinType.COIN_SUN ? 25
                : type == CoinType.COIN_SMALLSUN ? 15
                : type == CoinType.COIN_LARGESUN ? 50 : 0;
            float expectedScale = type == CoinType.COIN_SMALLSUN ? 0.5f
                : type == CoinType.COIN_LARGESUN ? 2.0f : 1.0f;
            int expectedCoins = type == CoinType.COIN_SILVER ? 1
                : type == CoinType.COIN_GOLD ? 5
                : type == CoinType.COIN_DIAMOND ? 100 : 0;

            assertEquals(expectedSun, aCoin.getSunValue(), "sun value " + type);
            assertEquals(expectedScale, aCoin.getSunScale(), 1.0e-6f, "sun scale " + type);
            assertEquals(expectedCoins, Coin.getCoinValue(type), "coin value " + type);
        }
    }

    @Test
    void disappearTimePerType() {
        for (int type = 0; type <= CoinType.COIN_PRESENT_SURVIVAL_MODE; type++) {
            Coin aCoin = newCoin(type, CoinMotion.COIN_MOTION_FROM_SKY);
            int expected = 750;
            if (type == CoinType.COIN_DIAMOND || type == CoinType.COIN_PRESENT_PLANT
                || type == CoinType.COIN_CHOCOLATE) {
                expected = 1500;
            }
            assertEquals(expected, aCoin.getDisappearTime(), "disappear time " + type);
        }
    }

    // ---------------------------------------------------------------- motions

    /**
     * Replays the draws {@code CoinInitialize} makes before the motion switch and returns the next
     * one, in {@code [0, theRange)}.
     *
     * <p>Coin.cpp:72 runs {@code mPottedPlantSpec.InitializePottedPlant(SEED_NONE)} *before* the
     * switch; that call draws {@code RandRangeInt(FACING_RIGHT=0, FACING_LEFT=1) = Rand(2)} for
     * {@code mFacing} and {@code RandRangeInt(3, 5) = Rand(3)} for {@code mFeedingsPerGrow}
     * (PlayerInfo.cpp:314-329). The motion's own {@code Rand} calls therefore start at draw #3.
     */
    private static int drawAfterPottedPlantInit(long theSeed, int theRange) {
        Common.sRand(theSeed);
        Common.rand(2); // PottedPlant::InitializePottedPlant -> mFacing
        Common.rand(3); // PottedPlant::InitializePottedPlant -> mFeedingsPerGrow
        return Common.rand(theRange);
    }

    @Test
    void fromSkyMotionIsSeededAndCorrect() {
        Common.sRand(12345L);
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.coinInitialize(100, 200, CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY);

        // Coin.cpp:315-319: mVelY = 0.67f, mVelX = 0.0f, mGroundY = Rand(250) + 300
        int expectedGround = drawAfterPottedPlantInit(12345L, 250) + 300;

        assertEquals(0.67f, aCoin.mVelY, 1.0e-6f);
        assertEquals(0.0f, aCoin.mVelX, 1.0e-6f);
        assertEquals(expectedGround, aCoin.mGroundY);
    }

    @Test
    void fromSkySlowMotionIsSeededAndCorrect() {
        Common.sRand(777L);
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY_SLOW);
        aCoin.coinInitialize(100, 200, CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY_SLOW);

        // Coin.cpp:321-325: mVelY = 0.33f, mVelX = 0.0f, mGroundY = Rand(250) + 300
        int expectedGround = drawAfterPottedPlantInit(777L, 250) + 300;

        assertEquals(0.33f, aCoin.mVelY, 1.0e-6f);
        assertEquals(expectedGround, aCoin.mGroundY);
    }

    @Test
    void fromPlantMotionIsSeededAndCorrect() {
        Common.sRand(4242L);
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_PLANT);
        aCoin.coinInitialize(100, 200, CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_PLANT);

        // Re-derive the exact draw order: Coin.cpp:72 spends Rand(2) (mFacing) and Rand(3)
        // (mFeedingsPerGrow) in InitializePottedPlant, then Coin.cpp:327-332 draws
        // mVelY = -1.7f - RandRangeFloat(0, 1.7), mVelX = -0.4f + RandRangeFloat(0, 0.8),
        // mGroundY = mPosY + 15 + Rand(20).
        Common.sRand(4242L);
        Common.rand(2); // PottedPlant::InitializePottedPlant -> mFacing
        Common.rand(3); // PottedPlant::InitializePottedPlant -> mFeedingsPerGrow
        float expectedVelY = -1.7f - PvzpCommon.randRangeFloat(0.0f, 1.7f);
        float expectedVelX = -0.4f + PvzpCommon.randRangeFloat(0.0f, 0.8f);
        int expectedGround = (int) (200.0f + 15 + Common.rand(20));

        assertEquals(expectedVelY, aCoin.mVelY, 1.0e-4f);
        assertEquals(expectedVelX, aCoin.mVelX, 1.0e-4f);
        assertEquals(expectedGround, aCoin.mGroundY);
        assertEquals(0.4f, aCoin.mScale, 1.0e-6f, "from-plant scale seed");
    }

    @Test
    void coinMotionLawnmowerCollectsImmediately() {
        Coin aCoin = new Coin();
        aCoin.mApp = mApp;
        aCoin.mBoard = mBoard;
        aCoin.coinInitialize(100, 200, CoinType.COIN_NONE, CoinMotion.COIN_MOTION_LAWNMOWER_COIN);
        assertTrue(aCoin.mIsBeingCollected);
        assertEquals(600, aCoin.mGroundY);
    }

    @Test
    void fromPresentMotionHasNoLaunchVelocity() {
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_PRESENT);
        aCoin.coinInitialize(100, 200, CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_PRESENT);
        assertEquals(0.0f, aCoin.mVelX, 1.0e-6f);
        assertEquals(0.0f, aCoin.mVelY, 1.0e-6f);
        assertEquals(600, aCoin.mGroundY);
    }

    @Test
    void fromBossMotionLaunchesFromTheRightEdge() {
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_BOSS);
        aCoin.coinInitialize(100, 200, CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_BOSS);
        assertEquals(750.0f, aCoin.mPosX, 1.0e-4f);
        assertEquals(245.0f, aCoin.mPosY, 1.0e-4f);
        assertEquals(-3.0f, aCoin.mVelX, 1.0e-6f);
        assertEquals(-5.0f, aCoin.mVelY, 1.0e-6f);
        assertEquals(285, aCoin.mGroundY);
    }

    // -------------------------------------------------------- fall / collection

    @Test
    void fromPlantScaleAnimatesTowardSunScale() {
        Coin aSmallSun = newCoin(CoinType.COIN_SMALLSUN, CoinMotion.COIN_MOTION_FROM_PLANT);
        aSmallSun.mPosY = 100.0f;
        aSmallSun.mVelY = 0.0f;
        aSmallSun.mGroundY = 100;  // already on the ground -> scale branch runs
        aSmallSun.mScale = 0.4f;
        aSmallSun.updateFall();
        assertEquals(0.42f, aSmallSun.mScale, 1.0e-4f);

        Coin aLargeSun = newCoin(CoinType.COIN_LARGESUN, CoinMotion.COIN_MOTION_FROM_PLANT);
        aLargeSun.mPosY = 100.0f;
        aLargeSun.mVelY = 0.0f;
        aLargeSun.mGroundY = 100;
        aLargeSun.mScale = 2.0f;
        aLargeSun.updateFall();
        assertEquals(2.0f, aLargeSun.mScale, 1.0e-4f);
    }

    @Test
    void updateFallSnapsToGroundAndStartsFadeAtDisappearTime() {
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.mPosY = 600.0f;
        aCoin.mVelY = 0.67f;
        aCoin.mGroundY = 600;
        aCoin.mPosX = 123.4f;
        aCoin.mDisappearCounter = 749;

        aCoin.updateFall();

        assertTrue(aCoin.mHitGround);
        assertEquals(600.0f, aCoin.mPosY, 1.0e-4f, "snapped to ground");
        assertEquals(123, (int) aCoin.mPosX, "x rounded to int");
        assertEquals(750, aCoin.mDisappearCounter);
        assertEquals(15, aCoin.mFadeCount, "fade begins at the disappear time");
    }

    @Test
    void fallingCoinDescendsUntilGround() {
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.mPosY = 100.0f;
        aCoin.mVelY = 0.67f;
        aCoin.mVelX = 0.0f;
        aCoin.mGroundY = 400;

        aCoin.updateFall();
        assertEquals(100.67f, aCoin.mPosY, 1.0e-4f);
        assertFalse(aCoin.mHitGround);
    }

    @Test
    void updateFadeKillsAfterFifteenTicks() {
        Coin aCoin = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.startFade();
        assertEquals(15, aCoin.mFadeCount);

        for (int i = 0; i < 14; i++) {
            aCoin.updateFade();
        }
        assertFalse(aCoin.mDead);
        assertEquals(1, aCoin.mFadeCount);

        aCoin.updateFade();
        assertTrue(aCoin.mDead);
    }

    @Test
    void collectingASunCreditsTheBoard() {
        Coin aCoin = newCoin(CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.mPosX = 15.0f;
        aCoin.mPosY = 0.0f;
        aCoin.mIsBeingCollected = true;
        aCoin.mCollectX = 15.0f;
        aCoin.mCollectY = 0.0f;

        aCoin.updateCollected();

        assertEquals(25, mBoard.mSunMoney, "a 25-sun coin credits 25 sun");
        assertTrue(aCoin.mDead);
    }

    @Test
    void collectingASunCoinDoesNotCreditIfTooFar() {
        Coin aCoin = newCoin(CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.mPosX = 400.0f;
        aCoin.mPosY = 300.0f;
        aCoin.mIsBeingCollected = true;
        aCoin.mCollectX = 400.0f;
        aCoin.mCollectY = 300.0f;

        aCoin.updateCollected();

        assertEquals(0, mBoard.mSunMoney);
        assertFalse(aCoin.mDead);
        assertTrue(aCoin.mCollectionDistance > 8.0f);
    }

    // -------------------------------------------------------------- sounds / input

    @Test
    void collectSoundsPerType() {
        assertCollectFoleys(CoinType.COIN_SUN, new int[] {FoleyType.FOLEY_SUN}, new long[0]);
        assertCollectFoleys(CoinType.COIN_SILVER, new int[] {FoleyType.FOLEY_COIN}, new long[0]);
        assertCollectFoleys(CoinType.COIN_GOLD, new int[] {FoleyType.FOLEY_COIN}, new long[0]);
        assertCollectFoleys(CoinType.COIN_DIAMOND, new int[0], new long[] {Resources.SOUND_DIAMOND_ID});
        assertCollectFoleys(CoinType.COIN_CHOCOLATE, new int[] {FoleyType.FOLEY_PRIZE}, new long[0]);
        assertCollectFoleys(CoinType.COIN_USABLE_SEED_PACKET, new int[0],
            new long[] {Resources.SOUND_SEEDLIFT_ID});
    }

    private void assertCollectFoleys(int theType, int[] theFoleys, long[] theSamples) {
        mApp.foleys.clear();
        mApp.samples.clear();
        Coin aCoin = newCoin(theType, CoinMotion.COIN_MOTION_FROM_SKY);
        aCoin.playCollectSound();

        assertEquals(theFoleys.length, mApp.foleys.size(), "foley count for " + theType);
        for (int i = 0; i < theFoleys.length; i++) {
            assertEquals(theFoleys[i], (int) mApp.foleys.get(i));
        }
        assertEquals(theSamples.length, mApp.samples.size(), "sample count for " + theType);
        for (int i = 0; i < theSamples.length; i++) {
            assertEquals(theSamples[i], (long) mApp.samples.get(i));
        }
    }

    @Test
    void mouseHitTestUsesTypeSpecificPadding() {
        Coin aSun = newCoin(CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
        aSun.mPosX = 100.0f;
        aSun.mPosY = 100.0f;
        HitResult aResult = new HitResult();

        // Sun gets an extra 15px of padding on every side: [85,175) x [85,175).
        assertTrue(aSun.mouseHitTest(100, 100, aResult));
        assertEquals(GameObjectType.OBJECT_TYPE_COIN, aResult.mObjectType);
        assertFalse(aSun.mouseHitTest(84, 100, aResult));
        assertFalse(aSun.mouseHitTest(175, 100, aResult));
        assertEquals(GameObjectType.OBJECT_TYPE_NONE, aResult.mObjectType);

        // A dead coin cannot be hit.
        aSun.mDead = true;
        assertFalse(aSun.mouseHitTest(100, 100, aResult));
    }

    @Test
    void fanOutCoinsLaunchAtFivePixelsPerTick() {
        Coin aSource = newCoin(CoinType.COIN_NONE, CoinMotion.COIN_MOTION_FROM_SKY);
        aSource.mPosX = 100.0f;
        aSource.mPosY = 200.0f;

        aSource.fanOutCoins(CoinType.COIN_NONE, 3);

        assertEquals(3, mBoard.mCoins.mSize);
        for (Coin aCoin : mBoard.mCoins) {
            assertEquals(5.0f, (float) Math.hypot(aCoin.mVelX, aCoin.mVelY), 1.0e-3f);
        }
    }

    @Test
    void isMoneyStaticMatchesInstance() {
        for (int type = 0; type <= CoinType.COIN_PRESENT_SURVIVAL_MODE; type++) {
            Coin aCoin = newCoin(type, CoinMotion.COIN_MOTION_FROM_SKY);
            assertEquals(Coin.isMoney(type), aCoin.isMoney(), "static/instance money mismatch " + type);
        }
    }
}
