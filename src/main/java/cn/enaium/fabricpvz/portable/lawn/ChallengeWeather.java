package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Challenge.cpp:1681-1760, 2764-2863, 4825-4853
 * ({@code Challenge::UpdateRainingSeeds}, {@code Challenge::UpdateStormyNight},
 * {@code Challenge::DrawStormFlash}, {@code Challenge::DrawRain}, {@code Challenge::DrawStormNight},
 * {@code Challenge::UpdateRain}).
 */
public class ChallengeWeather {
    public final Challenge c;

    public ChallengeWeather(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:1681-1708 ({@code Challenge::UpdateRainingSeeds}). */
    public void updateRainingSeeds() {
        if (c.mBoard.hasLevelAwardDropped() || --c.mChallengeStateCounter != 0) {
            return;
        }

        c.mChallengeStateCounter = PvzpCommon.randRangeInt(500, 999);

        Coin aCoin = c.mBoard.addCoin(PvzpCommon.randRangeInt(100, 649), 60,
                CoinType.COIN_USABLE_SEED_PACKET, CoinMotion.COIN_MOTION_FROM_SKY_SLOW);

        int aSeedType;
        do {
            aSeedType = Common.rand(c.mApp.getSeedsAvailable());
        } while (
                c.mBoard.seedNotRecommendedForLevel(aSeedType) != 0
                        || !c.mApp.hasSeedType(aSeedType)
                        || Plant.isUpgrade(aSeedType)
                        || aSeedType == SeedType.SEED_SUNFLOWER
                        || aSeedType == SeedType.SEED_TWINSUNFLOWER
                        || aSeedType == SeedType.SEED_INSTANT_COFFEE
                        || aSeedType == SeedType.SEED_UMBRELLA
                        || aSeedType == SeedType.SEED_SUNSHROOM
                        || aSeedType == SeedType.SEED_IMITATER
        );
        if (Common.rand(100) < PvzpCommon.pvzpAnimateCurve(0, 18, c.mBoard.countPlantByType(SeedType.SEED_LILYPAD),
                30, 1, PvzpCurves.CURVE_LINEAR)) {
            aSeedType = SeedType.SEED_LILYPAD;
        }

        aCoin.mUsableSeedType = aSeedType;
    }

    /** Ported from src/Lawn/Challenge.cpp:1710-1760 ({@code Challenge::UpdateStormyNight}). */
    public void updateStormyNight() {
        if (c.mBoard.mPaused) {
            if (c.mChallengeStateCounter == 1) {
                return;
            }

            if (c.mChallengeStateCounter == 150
                    && c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_1) {
                c.mChallengeStateCounter = 1;
                return;
            }
        }

        c.mChallengeStateCounter--;
        if ((c.mChallengeStateCounter == 300
                && (c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_1
                || c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_2))
                || (c.mChallengeStateCounter == 150
                && (c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_1
                || c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_3))) {
            c.mApp.playFoley(FoleyType.FOLEY_THUNDER);
        }
        if (c.mChallengeStateCounter > 0) {
            return;
        }

        if (c.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            c.mChallengeStateCounter = Challenge.STORM_FLASH_TIME + PvzpCommon.randRangeInt(-50, 50);
            c.mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_3;
            return;
        } else if (c.mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            c.mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
            c.mChallengeStateCounter = 0;
            return;
        } else {
            // If a yeti zombie is present, randomize the countdown to 350~450
            if (c.mBoard.countZombieByType(ZombieType.ZOMBIE_YETI) != 0) {
                c.mChallengeStateCounter = Challenge.STORM_FLASH_TIME + PvzpCommon.randRangeInt(200, 300);
            } else {
                int aMaxDur = Common.rand(2) != 0 ? 400 : 750;
                c.mChallengeStateCounter = Challenge.STORM_FLASH_TIME + PvzpCommon.randRangeInt(300, aMaxDur);
            }

            c.mChallengeState = PvzpCommon.randRangeInt(ChallengeState.STATECHALLENGE_STORM_FLASH_1,
                    ChallengeState.STATECHALLENGE_STORM_FLASH_3);
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:2764-2773 ({@code Challenge::DrawStormFlash}). */
    public void drawStormFlash(Graphics g, int theTime, int theMaxAmount) {
        MTRand aDrawRand = new MTRand(c.mBoard.mMainCounter / 6);
        int aDarkness = PvzpCommon.pvzpAnimateCurve(150, 0, theTime, 255 - theMaxAmount, 255,
                PvzpCurves.CURVE_LINEAR) + (int) aDrawRand.nextNoAssert(64L) - 32;
        g.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, aDarkness))));
        g.fillRect(-1000, -1000, 2800, 2600);

        g.setColor(new Color(255, 255, 255,
                PvzpCommon.pvzpAnimateCurve(150, 75, theTime, theMaxAmount, 0, PvzpCurves.CURVE_LINEAR)));
        g.fillRect(-1000, -1000, 2800, 2600);
    }

    /** Ported from src/Lawn/Challenge.cpp:2784-2832 ({@code Challenge::DrawRain}). */
    public void drawRain(Graphics g) {
        if (c.mBoard.mCutScene.isBeforePreloading() || !c.mApp.is3DAccelerated()) {
            return;
        }

        // Whatever is going on here is a bit yikes
        int aBoardOffsetX;
        /*
        if (aBoardOffsetX > 0)
        {
            aBoardOffsetX = (mBoard->mX + 100) / 100 * -100;
        }
        else
        {
            aBoardOffsetX = mBoard->mX / 100 * -100;
        }
        */

        aBoardOffsetX = c.mBoard.mX / 100 * -100;

        final int aTime = c.mBoard.mEffectCounter;
        int aTimeOffsetXEst = PvzpCommon.pvzpAnimateCurve(0, 100, Integer.remainderUnsigned(aTime, 100), 0, -100,
                PvzpCurves.CURVE_LINEAR);
        int aTimeOffsetYEst = PvzpCommon.pvzpAnimateCurve(0, 20, Integer.remainderUnsigned(aTime, 20), -100, 0,
                PvzpCurves.CURVE_LINEAR);

        // Draw the distant rain
        for (int aHorCnt = 9; aHorCnt > 0; aHorCnt--) {
            for (int aVerCnt = 7; aVerCnt > 0; aVerCnt--) {
                int aImageX = aTimeOffsetXEst + 100 * aHorCnt + aBoardOffsetX;
                int aImageY = aTimeOffsetYEst + 100 * aVerCnt;
                g.drawImage(Resources.getImageById(ImageID.IMAGE_RAIN), aImageX, aImageY);
            }
        }

        float aTimeOffsetXCls = PvzpCommon.pvzpAnimateCurve(0, 161, Integer.remainderUnsigned(aTime, 161), 0, -100,
                PvzpCurves.CURVE_LINEAR);
        float aTimeOffsetYCls = PvzpCommon.pvzpAnimateCurve(0, 33, Integer.remainderUnsigned(aTime, 33), -100, 0,
                PvzpCurves.CURVE_LINEAR);
        // Draw the nearby rain
        for (int aHorCnt = 0; aHorCnt < 9; aHorCnt++) {
            for (int aVerCnt = 0; aVerCnt < 7; aVerCnt++) {
                float aRainScaleCls = 1.5f;
                float aImageClsX = (aHorCnt * 100 + aTimeOffsetXCls) * aRainScaleCls + aBoardOffsetX;
                float aImageClsY = (aVerCnt * 100 + aTimeOffsetYCls) * aRainScaleCls;
                PvzpCommon.pvzpDrawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_RAIN), aImageClsX,
                        aImageClsY, aRainScaleCls, aRainScaleCls);
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:2834-2863 ({@code Challenge::DrawStormNight}). */
    public void drawStormNight(Graphics g) {
        if (c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_1 && c.mChallengeStateCounter < 300) {
            if (c.mChallengeStateCounter > Challenge.STORM_FLASH_TIME) {
                drawStormFlash(g, c.mChallengeStateCounter - Challenge.STORM_FLASH_TIME, 255);
            } else {
                drawStormFlash(g, c.mChallengeStateCounter, 92);
            }
        } else if (c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_2
                && c.mChallengeStateCounter < 300) {
            drawStormFlash(g, c.mChallengeStateCounter / 2, 255);
        } else if (c.mChallengeState == ChallengeState.STATECHALLENGE_STORM_FLASH_3
                && c.mChallengeStateCounter < 150) {
            drawStormFlash(g, c.mChallengeStateCounter, 255);
        } else {
            g.setColor(Color.Black);
            g.fillRect(-1000, -1000, GameConstants.BOARD_WIDTH + 2000, GameConstants.BOARD_HEIGHT + 2000);
        }

        c.mBoard.drawUIBottom(g);
        c.mBoard.drawTopRightUI(g);
    }

    /** Ported from src/Lawn/Challenge.cpp:4825-4853 ({@code Challenge::UpdateRain}). */
    public void updateRain() {
        c.mRainCounter--;
        if (c.mRainCounter < 0 && !c.mBoard.mCutScene.isBeforePreloading()) {
            float aPosX = PvzpCommon.randRangeFloat(40.0f, 740.0f);
            float aPosY = PvzpCommon.randRangeFloat(90.0f, 240.0f);
            Reanimation aSplashReanim = c.mApp.addReanimation(aPosX, aPosY, RenderLayer.RENDER_LAYER_GROUND,
                    ReanimationType.REANIM_RAIN_SPLASH);
            aSplashReanim.mColorOverride = new Color(255, 255, 255, PvzpCommon.randRangeInt(100, 200));
            float aScale = PvzpCommon.randRangeFloat(0.7f, 1.2f);
            aSplashReanim.overrideScale(aScale, aScale);

            aPosX = PvzpCommon.randRangeFloat(40.0f, 740.0f);
            aPosY = PvzpCommon.randRangeFloat(290.0f, 410.0f);
            c.mApp.addReanimation(aPosX, aPosY, RenderLayer.RENDER_LAYER_GROUND, ReanimationType.REANIM_RAIN_CIRCLE);
            aSplashReanim.mColorOverride = new Color(255, 255, 255, PvzpCommon.randRangeInt(50, 150));
            aScale = PvzpCommon.randRangeFloat(0.7f, 1.1f);
            aSplashReanim.overrideScale(aScale, aScale);

            aPosX = PvzpCommon.randRangeFloat(40.0f, 740.0f);
            aPosY = PvzpCommon.randRangeFloat(450.0f, 580.0f);
            c.mApp.addReanimation(aPosX, aPosY, RenderLayer.RENDER_LAYER_GROUND, ReanimationType.REANIM_RAIN_SPLASH);
            aSplashReanim.mColorOverride = new Color(255, 255, 255, PvzpCommon.randRangeInt(100, 200));
            aScale = PvzpCommon.randRangeFloat(0.7f, 1.2f);
            aSplashReanim.overrideScale(aScale, aScale);

            c.mRainCounter = PvzpCommon.randRangeInt(10, 20);
        }
    }
}
