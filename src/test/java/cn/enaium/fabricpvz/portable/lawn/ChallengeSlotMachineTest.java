/*
 * Headless tests for the ported slot-machine challenge (src/Lawn/Challenge.cpp:916-943, 1047-1066,
 * 1762-1852, 2120-2135).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Exercises {@link ChallengeSlotMachine} against a headless {@link Board}: pulling the handle,
 * rolling/payout transitions, the slot-machine tooltip and the handle reanimation draw. Entity
 * ctors run against {@link LawnEntityTestSupport} test doubles, so no retail assets are needed.
 */
class ChallengeSlotMachineTest {

    private TestLawnApp mApp;
    private Board mBoard;
    private Challenge mChallenge;
    private ChallengeSlotMachine mSlotMachine;

    @BeforeEach
    void setUp() {
        mApp = LawnEntityTestSupport.newApp();
        mApp.mGameMode = GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE;
        mBoard = LawnEntityTestSupport.newBoard(mApp);
        mApp.mBoard = mBoard;
        mChallenge = new Challenge(mBoard);
        mChallenge.mReanimChallenge = 1;
        mSlotMachine = new ChallengeSlotMachine(mChallenge);
        mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_NORMAL;
    }

    private static HitResult handleHit() {
        HitResult aHitResult = new HitResult();
        aHitResult.mObjectType = GameObjectType.OBJECT_TYPE_SLOT_MACHINE_HANDLE;
        return aHitResult;
    }

    // ------------------------------------------------------------------------------------------
    // mouseDownPull
    // ------------------------------------------------------------------------------------------

    @Test
    void mouseDownPullStartsWithEnoughSun() {
        mBoard.mSunMoney = 100;

        assertTrue(mSlotMachine.mouseDownPull());

        assertEquals(75, mBoard.mSunMoney, "25 sun is consumed");
        for (int i = 0; i < 3; i++) {
            assertEquals(300, mBoard.mSeedBank.mSeedPackets[i].mSlotMachineCountDown,
                    "packet " + i + " starts rolling");
        }
        assertEquals(ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING, mChallenge.mChallengeState);
        assertEquals(1, mChallenge.mSlotMachineRollCount);
        assertEquals(TutorialState.TUTORIAL_SLOT_MACHINE_COMPLETED, mBoard.mTutorialState);
        assertEquals("anim_pull", mApp.mReanim.mLastTrack);
        assertEquals(ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, mApp.mReanim.mLastLoopType);
        assertEquals(36.0f, mApp.mReanim.mLastAnimRate, 0.0001f);
        assertTrue(mApp.mSamplesPlayed.contains((long) SoundType.SOUND_SLOT_MACHINE));
    }

    @Test
    void mouseDownPullWithoutSunKeepsStateAndBuzzes() {
        mBoard.mSunMoney = 10;

        assertTrue(mSlotMachine.mouseDownPull(), "the click is still consumed");

        assertEquals(10, mBoard.mSunMoney, "sun is not taken");
        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, mChallenge.mChallengeState);
        assertEquals(0, mChallenge.mSlotMachineRollCount);
        for (int i = 0; i < 3; i++) {
            assertEquals(0, mBoard.mSeedBank.mSeedPackets[i].mSlotMachineCountDown);
        }
        assertTrue(mApp.mSamplesPlayed.contains((long) Resources.SOUND_BUZZER_ID));
        assertFalse(mApp.mSamplesPlayed.contains((long) SoundType.SOUND_SLOT_MACHINE));
    }

    // ------------------------------------------------------------------------------------------
    // updateSlotMachine
    // ------------------------------------------------------------------------------------------

    @Test
    void updateSlotMachineCollectSunAdviceThenSpinAgain() {
        mBoard.mSunMoney = 500;

        mSlotMachine.updateSlotMachine();
        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_SLOT_MACHINE_COLLECT_SUN"),
                "label was " + mBoard.mAdvice.mLabel);
        assertEquals(AdviceType.ADVICE_SLOT_MACHINE_COLLECT_SUN, mBoard.mHelpIndex);

        // Expire the advice so the idle state can show the "spin again" hint.
        mBoard.mAdvice.mDuration = 0;
        mSlotMachine.updateSlotMachine();
        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_SLOT_MACHINE_SPIN_AGAIN"),
                "label was " + mBoard.mAdvice.mLabel);
    }

    @Test
    void updateSlotMachineAlmostThereAndProgressMeter() {
        mBoard.mSunMoney = 1950;

        mSlotMachine.updateSlotMachine();

        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_ALMOST_THERE"),
                "label was " + mBoard.mAdvice.mLabel);
        assertEquals(146, mBoard.mProgressMeterWidth);
    }

    @Test
    void updateSlotMachineAwardsTrophyAtMaxSun() {
        mBoard.mSunMoney = 2000;

        mSlotMachine.updateSlotMachine();

        assertTrue(mBoard.mLevelAwardSpawned);
        assertEquals(BoardResult.BOARDRESULT_WON, mApp.mBoardResult);
        assertEquals(150, mBoard.mProgressMeterWidth);
    }

    @Test
    void updateSlotMachineDiamondJackpotDropsFiveDiamonds() {
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING;
        for (int i = 0; i < 3; i++) {
            mBoard.mSeedBank.mSeedPackets[i].mPacketType = SeedType.SEED_SLOT_MACHINE_DIAMOND;
            mBoard.mSeedBank.mSeedPackets[i].mSlotMachineCountDown = 0;
        }

        mSlotMachine.updateSlotMachine();

        assertEquals(ChallengeState.STATECHALLENGE_NORMAL, mChallenge.mChallengeState);
        assertEquals("anim_return", mApp.mReanim.mLastTrack);
        assertTrue(mApp.mFoleysPlayed.contains(FoleyType.FOLEY_ART_CHALLENGE));
        assertEquals(5, mBoard.mCoins.mSize);
        assertEquals(CoinType.COIN_DIAMOND, mBoard.mCoins.dataArrayGetItemAt(0).mType);
        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_SLOT_MACHINE_DIAMOND_JACKPOT"),
                "label was " + mBoard.mAdvice.mLabel);
    }

    @Test
    void updateSlotMachineThreeSunJackpotDropsTwentySuns() {
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING;
        for (int i = 0; i < 3; i++) {
            mBoard.mSeedBank.mSeedPackets[i].mPacketType = SeedType.SEED_SLOT_MACHINE_SUN;
            mBoard.mSeedBank.mSeedPackets[i].mSlotMachineCountDown = 0;
        }

        mSlotMachine.updateSlotMachine();

        assertEquals(20, mBoard.mCoins.mSize);
        assertEquals(CoinType.COIN_SUN, mBoard.mCoins.dataArrayGetItemAt(0).mType);
        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_SLOT_MACHINE_SUN_JACKPOT"),
                "label was " + mBoard.mAdvice.mLabel);
    }

    @Test
    void updateSlotMachineTwoOfAKindDropsUsableSeed() {
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING;
        mBoard.mSeedBank.mSeedPackets[0].mPacketType = SeedType.SEED_PEASHOOTER;
        mBoard.mSeedBank.mSeedPackets[1].mPacketType = SeedType.SEED_PEASHOOTER;
        mBoard.mSeedBank.mSeedPackets[2].mPacketType = SeedType.SEED_WALLNUT;

        mSlotMachine.updateSlotMachine();

        assertEquals(1, mBoard.mCoins.mSize);
        Coin aCoin = mBoard.mCoins.dataArrayGetItemAt(0);
        assertEquals(CoinType.COIN_USABLE_SEED_PACKET, aCoin.mType);
        assertEquals(SeedType.SEED_PEASHOOTER, aCoin.mUsableSeedType);
        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_SLOT_MACHINE_2_OF_A_KIND"),
                "label was " + mBoard.mAdvice.mLabel);
    }

    @Test
    void updateSlotMachineThreeOfAKindDropsThreeUsableSeeds() {
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING;
        for (int i = 0; i < 3; i++) {
            mBoard.mSeedBank.mSeedPackets[i].mPacketType = SeedType.SEED_PEASHOOTER;
            mBoard.mSeedBank.mSeedPackets[i].mSlotMachineCountDown = 0;
        }

        mSlotMachine.updateSlotMachine();

        assertEquals(3, mBoard.mCoins.mSize);
        for (Coin aCoin : mBoard.mCoins) {
            assertEquals(CoinType.COIN_USABLE_SEED_PACKET, aCoin.mType);
            assertEquals(SeedType.SEED_PEASHOOTER, aCoin.mUsableSeedType);
        }
        assertTrue(mBoard.mAdvice.mLabel.contains("ADVICE_SLOT_MACHINE_3_OF_A_KIND"),
                "label was " + mBoard.mAdvice.mLabel);
    }

    // ------------------------------------------------------------------------------------------
    // updateToolTip
    // ------------------------------------------------------------------------------------------

    @Test
    void updateToolTipShowsHandleTooltip() {
        mBoard.mSunMoney = 100;

        assertEquals(1, mSlotMachine.updateToolTip(10, 10, handleHit()));

        assertTrue(mBoard.mToolTip.mVisible);
        assertTrue(mBoard.mToolTip.mCenter);
        assertTrue(mBoard.mToolTip.mLabel.contains("SLOT_MACHINE_PULL_TOOLTIP"),
                "label was " + mBoard.mToolTip.mLabel);
        assertEquals("", mBoard.mToolTip.mWarningText, "sun is affordable so no warning");
        Rect aHandleRect = mChallenge.slotMachineGetHandleRect();
        assertEquals(aHandleRect.mX + 15, mBoard.mToolTip.mX);
        assertEquals(aHandleRect.mY + 65, mBoard.mToolTip.mY);
    }

    @Test
    void updateToolTipWarnsWhenSunIsShort() {
        mBoard.mSunMoney = 10;

        assertEquals(1, mSlotMachine.updateToolTip(10, 10, handleHit()));
        assertTrue(mBoard.mToolTip.mWarningText.contains("NOT_ENOUGH_SUN"),
                "warning was " + mBoard.mToolTip.mWarningText);
    }

    @Test
    void updateToolTipGuards() {
        // Not a slot-machine level.
        mApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        assertEquals(0, mSlotMachine.updateToolTip(10, 10, handleHit()));

        mApp.mGameMode = GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE;

        // Wrong cursor.
        mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_HAMMER;
        assertEquals(0, mSlotMachine.updateToolTip(10, 10, handleHit()));

        // Wrong challenge state.
        mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_NORMAL;
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING;
        assertEquals(0, mSlotMachine.updateToolTip(10, 10, handleHit()));

        // Handle not hit.
        mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        HitResult aHitResult = new HitResult();
        aHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
        assertEquals(0, mSlotMachine.updateToolTip(10, 10, aHitResult));
    }

    @Test
    void updateToolTipNullResultHitTests() {
        mBoard.mSunMoney = 100;

        // The empty board has no slot-machine handle under (5, 5), so the internal hit test
        // returns false.
        assertEquals(0, mSlotMachine.updateToolTip(5, 5, null));
    }

    // ------------------------------------------------------------------------------------------
    // drawSlotMachine
    // ------------------------------------------------------------------------------------------

    @Test
    void drawSlotMachineReturnsWhileZombiesWon() {
        mApp.mGameScene = GameScenes.SCENE_ZOMBIES_WON;
        // A null reanimation id would trap the draw path; the early return must avoid it.
        mChallenge.mReanimChallenge = ReanimationID.REANIMATIONID_NULL;

        mSlotMachine.drawSlotMachine(new Graphics());
    }

    @Test
    void drawSlotMachineDrawsTheHandleReanimation() {
        mApp.mGameScene = GameScenes.SCENE_PLAYING;

        mSlotMachine.drawSlotMachine(new Graphics());

        assertNotNull(mApp.mReanim, "the handle reanimation is consulted");
    }
}
