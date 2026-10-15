/*
 * Ported from src/Lawn/Challenge.cpp:916-943, 1047-1066, 1762-1852, 2120-2135
 * ({@code Challenge::UpdateToolTip}, {@code Challenge::MouseDown} slot-machine pull branch,
 * {@code Challenge::UpdateSlotMachine}, {@code Challenge::DrawSlotMachine}).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Slot Machine mini-game state machine, split out of {@link Challenge}.
 *
 * <p>Ported from src/Lawn/Challenge.cpp:916-943 (UpdateToolTip), 1047-1066 (the slot-machine pull
 * branch of MouseDown), 1762-1852 (UpdateSlotMachine) and 2120-2135 (DrawSlotMachine).
 */
public class ChallengeSlotMachine {

    public final Challenge c;

    public ChallengeSlotMachine(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:1762-1852 ({@code Challenge::UpdateSlotMachine}). */
    public void updateSlotMachine() {
        int aSunMoney = Math.max(0, Math.min(2000, c.mBoard.mSunMoney));
        if (aSunMoney >= Challenge.SLOT_MACHINE_WINNING_SCORE - 100) {
            c.mBoard.displayAdvice("[ADVICE_ALMOST_THERE]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                    AdviceType.ADVICE_ALMOST_THERE);
        }
        if (aSunMoney >= 2000) {
            c.spawnLevelAward(4, 2);
            c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);
        }
        c.mBoard.mProgressMeterWidth = PvzpCommon.pvzpAnimateCurve(0, Challenge.SLOT_MACHINE_WINNING_SCORE,
                aSunMoney, 0, Board.PROGRESS_METER_COUNTER, PvzpCurves.CURVE_LINEAR);

        if (c.mBoard.mAdvice.mDuration == 0) {
            String aMsg = PvzpCommon.pvzpReplaceNumberString("[ADVICE_SLOT_MACHINE_COLLECT_SUN]", "{SCORE}",
                    Challenge.SLOT_MACHINE_WINNING_SCORE);
            c.mBoard.displayAdvice(aMsg, MessageStyle.MESSAGE_STYLE_SLOT_MACHINE,
                    AdviceType.ADVICE_SLOT_MACHINE_COLLECT_SUN);
        }

        if (c.mChallengeState != ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING) {
            if (c.mBoard.mAdvice.mDuration == 0 && !c.mBoard.hasLevelAwardDropped()) {
                c.mBoard.displayAdviceAgain("[ADVICE_SLOT_MACHINE_SPIN_AGAIN]",
                        MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_SLOT_MACHINE_SPIN_AGAIN);
            }
        } else if (c.mBoard.mSeedBank.mSeedPackets[0].mSlotMachineCountDown <= 0) {
            c.mApp.reanimationGet(c.mReanimChallenge).playReanim("anim_return",
                    ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
            c.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;

            int aPacket1 = c.mBoard.mSeedBank.mSeedPackets[0].mPacketType;
            int aPacket2 = c.mBoard.mSeedBank.mSeedPackets[1].mPacketType;
            int aPacket3 = c.mBoard.mSeedBank.mSeedPackets[2].mPacketType;
            if (aPacket1 != aPacket2 || aPacket2 != aPacket3) {
                if (aPacket1 == aPacket2 || aPacket2 == aPacket3 || aPacket1 == aPacket3) {
                    c.mApp.playFoley(FoleyType.FOLEY_ART_CHALLENGE);
                    int aSeedType = (aPacket1 == aPacket2 || aPacket1 == aPacket3) ? aPacket1 : aPacket2;
                    if (aSeedType == SeedType.SEED_SLOT_MACHINE_DIAMOND) {
                        c.mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_2_DIAMONDS]",
                                MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_NONE);
                        c.mBoard.addCoin(360, 85, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
                    } else if (aSeedType == SeedType.SEED_SLOT_MACHINE_SUN) {
                        c.mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_2_SUNS]",
                                MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_NONE);
                        for (int i = 0; i < 4; i++) {
                            c.mBoard.addCoin(320 + i * 15, 85, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
                        }
                    } else {
                        c.mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_2_OF_A_KIND]",
                                MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_NONE);
                        c.mBoard.addCoin(360, 85, CoinType.COIN_USABLE_SEED_PACKET, CoinMotion.COIN_MOTION_COIN)
                                .mUsableSeedType = aSeedType;
                    }
                }
            } else {
                c.mApp.playFoley(FoleyType.FOLEY_ART_CHALLENGE);
                if (aPacket1 == SeedType.SEED_SLOT_MACHINE_DIAMOND) {
                    c.mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_DIAMOND_JACKPOT]",
                            MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_NONE);
                    for (int i = 0; i < 5; i++) {
                        c.mBoard.addCoin(320 + i * 12, 85, CoinType.COIN_DIAMOND, CoinMotion.COIN_MOTION_COIN);
                    }
                } else if (aPacket1 == SeedType.SEED_SLOT_MACHINE_SUN) {
                    c.mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_SUN_JACKPOT]",
                            MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_NONE);
                    for (int i = 0; i < 20; i++) {
                        c.mBoard.addCoin(320 + i * 3, 85, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
                    }
                } else {
                    c.mBoard.displayAdvice("[ADVICE_SLOT_MACHINE_3_OF_A_KIND]",
                            MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, AdviceType.ADVICE_NONE);
                    for (int i = 0; i < 3; i++) {
                        c.mBoard.addCoin(320 + i * 20, 85, CoinType.COIN_USABLE_SEED_PACKET,
                                CoinMotion.COIN_MOTION_COIN).mUsableSeedType = aPacket1;
                    }
                }
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:2120-2135 ({@code Challenge::DrawSlotMachine}). */
    public void drawSlotMachine(Graphics g) {
        if (c.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            return;
        }

        Graphics gBoardParent = new Graphics(g);
        if (c.mSlotMachineRollCount < 3 && c.mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                && c.mChallengeState != ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING
                && !c.mBoard.hasLevelAwardDropped()) {
            gBoardParent.setColor(PvzpCommon.getFlashingColor(c.mBoard.mMainCounter, 75));
            gBoardParent.setColorizeImages(true);
        }
        gBoardParent.mTransX = c.mBoard.mSeedBank.mX - c.mBoard.mX;
        gBoardParent.mTransY = c.mBoard.mSeedBank.mY - c.mBoard.mY;
        c.mApp.reanimationGet(c.mReanimChallenge).draw(gBoardParent);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:916-943 ({@code Challenge::UpdateToolTip}).
     *
     * <p>Only the slot-machine handle produces a tooltip; the full upstream body is reproduced here
     * so {@link Challenge#updateToolTip} can delegate directly.
     */
    public int updateToolTip(int theX, int theY, HitResult theHitResult) {
        if (!c.mApp.isSlotMachineLevel()) {
            return 0;
        }

        HitResult aLocalHitResult = new HitResult();
        if (theHitResult == null) {
            c.mBoard.mouseHitTest(theX, theY, aLocalHitResult);
            theHitResult = aLocalHitResult;
        }
        if (theHitResult.mObjectType != GameObjectType.OBJECT_TYPE_SLOT_MACHINE_HANDLE
                || c.mBoard.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_NORMAL
                || c.mChallengeState != ChallengeState.STATECHALLENGE_NORMAL) {
            return 0;
        }

        if (!c.mBoard.canTakeSunMoney(25)) {
            c.mBoard.mToolTip.setWarningText("[NOT_ENOUGH_SUN]");
        }

        Rect aSlotMachineHandleRect = c.slotMachineGetHandleRect();
        c.mBoard.mToolTip.setLabel("[SLOT_MACHINE_PULL_TOOLTIP]");
        c.mBoard.mToolTip.setPosition(aSlotMachineHandleRect.mX + 15, aSlotMachineHandleRect.mY + 65);
        c.mBoard.mToolTip.mVisible = true;
        c.mBoard.mToolTip.mCenter = true;
        return 1;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1047-1066 ({@code Challenge::MouseDown} slot-machine pull
     * branch).
     *
     * <p>The caller has already checked that this is a slot-machine level, the hit object is the
     * slot-machine handle, the cursor is normal and the challenge state is normal; pulling the
     * handle always consumes the click.
     */
    public boolean mouseDownPull() {
        if (c.mBoard.takeSunMoney(25)) {
            for (int i = 0; i < 3; i++) {
                c.mBoard.mSeedBank.mSeedPackets[i].slotMachineStart();
            }

            c.mApp.reanimationGet(c.mReanimChallenge).playReanim("anim_pull",
                    ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 36.0f);
            c.mChallengeState = ChallengeState.STATECHALLENGE_SLOT_MACHINE_ROLLING;
            c.mBoard.setTutorialState(TutorialState.TUTORIAL_SLOT_MACHINE_COMPLETED);
            c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);

            c.mSlotMachineRollCount++;
            c.mApp.playSample(SoundType.SOUND_SLOT_MACHINE);
        }

        return true;
    }
}
