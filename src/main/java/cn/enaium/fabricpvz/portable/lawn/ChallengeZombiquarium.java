package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;

/**
 * Ported from src/Lawn/Challenge.cpp:3307-3443 (Challenge::ZombiquariumSpawnSnorkle,
 * Challenge::ZombiquariumPacketClicked, Challenge::ZombiquariumDropBrain,
 * Challenge::ZombiquariumMouseDown, Challenge::ZombiquariumUpdate).
 */
public class ChallengeZombiquarium {
    public final Challenge c;

    public ChallengeZombiquarium(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:3307-3313 (Challenge::ZombiquariumSpawnSnorkle). */
    public Zombie zombiquariumSpawnSnorkle() {
        Zombie aZombie = c.mBoard.addZombieInRow(ZombieType.ZOMBIE_SNORKEL, 0, 0);
        aZombie.mPosX = PvzpCommon.randRangeFloat(50, 650);
        aZombie.mPosY = PvzpCommon.randRangeFloat(100, 400);
        return aZombie;
    }

    /** Ported from src/Lawn/Challenge.cpp:3315-3344 (Challenge::ZombiquariumPacketClicked). */
    public void zombiquariumPacketClicked(SeedPacket theSeedPacket) {
        int aCost = c.mBoard.getCurrentPlantCost(theSeedPacket.mPacketType, SeedType.SEED_NONE);
        if (c.mBoard.canTakeSunMoney(aCost)) {
            if (theSeedPacket.mPacketType == SeedType.SEED_ZOMBIQUARIUM_SNORKLE) {
                if (c.mBoard.countZombiesOnScreen() > 100) {
                    return;
                }

                if (c.mBoard.mTutorialState == TutorialState.TUTORIAL_ZOMBIQUARIUM_BUY_SNORKEL) {
                    c.mBoard.clearAdvice(AdviceType.ADVICE_ZOMBIQUARIUM_BUY_SNORKEL);
                    c.mBoard.tutorialArrowRemove();
                    c.mBoard.mTutorialState = TutorialState.TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL;
                }

                Zombie aZombie = zombiquariumSpawnSnorkle();
                c.mApp.playFoley(FoleyType.FOLEY_ZOMBIESPLASH);
                c.mApp.addPvzpParticle(aZombie.mPosX + 60.0f, aZombie.mPosY + 20.0f, RenderLayer.RENDER_LAYER_TOP,
                        ParticleEffect.PARTICLE_PLANTING_POOL);
            } else if (theSeedPacket.mPacketType == SeedType.SEED_ZOMBIQUARIUM_TROPHY) {
                c.spawnLevelAward(2, 0);
                c.mBoard.clearAdvice(AdviceType.ADVICE_NONE);
            }

            c.mBoard.takeSunMoney(aCost);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3346-3358 (Challenge::ZombiquariumDropBrain). */
    public void zombiquariumDropBrain(int x, int y) {
        c.mBoard.clearAdvice(AdviceType.ADVICE_ZOMBIQUARIUM_CLICK_TO_FEED);
        GridItem aBrain = c.mBoard.mGridItems.dataArrayAlloc();
        aBrain.mGridItemType = GridItemType.GRIDITEM_BRAIN;
        aBrain.mRenderOrder = 400000;
        aBrain.mGridX = 0;
        aBrain.mGridY = 0;
        aBrain.mGridItemCounter = 0;
        aBrain.mPosX = x - 15.0f;
        aBrain.mPosY = y - 15.0f;
        c.mApp.playSample(SoundType.SOUND_TAP);
    }

    /** Ported from src/Lawn/Challenge.cpp:3360-3379 (Challenge::ZombiquariumMouseDown). */
    public void zombiquariumMouseDown(int x, int y) {
        if (x < 80 || x > 720 || y < 90 || y > 430) {
            return;
        }

        int aBrainsCount = 0;
        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_BRAIN) {
                aBrainsCount++;
            }
        }
        if (aBrainsCount < 3 && c.mBoard.takeSunMoney(5)) {
            zombiquariumDropBrain(x, y);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:3381-3443 (Challenge::ZombiquariumUpdate). */
    public void zombiquariumUpdate() {
        if (c.mBoard.mZombies.mSize == 0 && !c.mBoard.hasLevelAwardDropped()) {
            c.mBoard.zombiesWon(null);
            return;
        }
        if (c.mBoard.mAdvice.mDuration == 0
                && !c.mBoard.mHelpDisplayed[AdviceType.ADVICE_ZOMBIQUARIUM_COLLECT_SUN]) {
            String aMsg = PvzpCommon.pvzpReplaceNumberString("[ADVICE_ZOMBIQUARIUM_COLLECT_SUN]", "{SCORE}",
                    Challenge.ZOMBIQUARIUM_WINNING_SCORE);
            c.mBoard.displayAdvice(aMsg, MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                    AdviceType.ADVICE_ZOMBIQUARIUM_COLLECT_SUN);
        }

        int aScore = Math.max(0, Math.min(Challenge.ZOMBIQUARIUM_WINNING_SCORE, c.mBoard.mSunMoney));
        c.mBoard.mProgressMeterWidth = PvzpCommon.pvzpAnimateCurve(0, Challenge.ZOMBIQUARIUM_WINNING_SCORE, aScore, 0,
                Board.PROGRESS_METER_COUNTER, PvzpCurves.CURVE_LINEAR);
        if (aScore >= Challenge.ZOMBIQUARIUM_WINNING_SCORE - 100) {
            c.mBoard.displayAdvice("[ADVICE_ALMOST_THERE]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                    AdviceType.ADVICE_ALMOST_THERE);
        }
        if (aScore >= 110 && c.mBoard.mTutorialState == TutorialState.TUTORIAL_OFF) {
            c.mBoard.mTutorialState = TutorialState.TUTORIAL_ZOMBIQUARIUM_BUY_SNORKEL;
            float aPosX = c.mBoard.mSeedBank.mX + c.mBoard.mSeedBank.mSeedPackets[0].mX;
            float aPosY = c.mBoard.mSeedBank.mY + c.mBoard.mSeedBank.mSeedPackets[0].mY;
            c.mBoard.tutorialArrowShow((int) aPosX, (int) aPosY);
            c.mBoard.displayAdvice("[ADVICE_ZOMBIQUARIUM_BUY_SNORKEL]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                    AdviceType.ADVICE_ZOMBIQUARIUM_BUY_SNORKEL);
        } else if (aScore < 100 && c.mBoard.mTutorialState == TutorialState.TUTORIAL_ZOMBIQUARIUM_BUY_SNORKEL) {
            c.mBoard.tutorialArrowRemove();
            c.mBoard.clearAdvice(AdviceType.ADVICE_ZOMBIQUARIUM_BUY_SNORKEL);
            c.mBoard.mTutorialState = TutorialState.TUTORIAL_OFF;
        }
        if (aScore >= Challenge.ZOMBIQUARIUM_WINNING_SCORE
                && c.mBoard.mTutorialState == TutorialState.TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL) {
            c.mBoard.mTutorialState = TutorialState.TUTORIAL_ZOMBIQUARIUM_CLICK_TROPHY;
            float aPosX = c.mBoard.mSeedBank.mX + c.mBoard.mSeedBank.mSeedPackets[1].mX;
            float aPosY = c.mBoard.mSeedBank.mY + c.mBoard.mSeedBank.mSeedPackets[1].mY;
            c.mBoard.tutorialArrowShow((int) aPosX, (int) aPosY);
            c.mBoard.displayAdvice("[ADVICE_ZOMBIQUARIUM_CLICK_TROPHY]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                    AdviceType.ADVICE_ZOMBIQUARIUM_CLICK_TROPHY);
        } else if (aScore < Challenge.ZOMBIQUARIUM_WINNING_SCORE
                && c.mBoard.mTutorialState == TutorialState.TUTORIAL_ZOMBIQUARIUM_CLICK_TROPHY) {
            c.mBoard.tutorialArrowRemove();
            c.mBoard.clearAdvice(AdviceType.ADVICE_ZOMBIQUARIUM_CLICK_TROPHY);
            c.mBoard.mTutorialState = TutorialState.TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL;
        }

        for (GridItem aGridItem : c.mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_BRAIN) {
                aGridItem.mGridItemCounter++;
                aGridItem.mPosY += 0.15f;
                if (aGridItem.mPosY >= 500.0f) {
                    aGridItem.gridItemDie();
                }
            }
        }
    }
}
