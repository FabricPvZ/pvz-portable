/*
 * Ported from src/Lawn/GridItem.h:32-97 and src/Lawn/GridItem.cpp:23-645.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Lawn/GridItem.h:48-96 and src/Lawn/GridItem.cpp.
 *
 * <p>Not a {@link GameObject} upstream (GridItem.h declares the fields itself), so the
 * pointer-tie-break replacement {@code mAllocId} is allocated explicitly.
 */
public class GridItem {

    /** Ported from src/Lawn/GridItem.h:32 (NUM_MOTION_TRAIL_FRAMES). */
    public static final int NUM_MOTION_TRAIL_FRAMES = 12;

    /** Ported from src/Lawn/GridItem.h:41-46 (class MotionTrailFrame). */
    public static class MotionTrailFrame {
        public float mPosX;
        public float mPosY;
        public float mAnimTime;
    }

    public LawnApp mApp;
    public Board mBoard;
    public int mGridItemType;
    public int mGridItemState;
    public int mGridX;
    public int mGridY;
    public int mGridItemCounter;
    public int mRenderOrder;
    public boolean mDead;
    public float mPosX;
    public float mPosY;
    public float mGoalX;
    public float mGoalY;
    public int mGridItemReanimID;
    public int mGridItemParticleID;
    public int mZombieType;
    public int mSeedType;
    public int mScaryPotType;
    public boolean mHighlighted;
    public int mTransparentCounter;
    public int mSunCount;
    public MotionTrailFrame[] mMotionTrailFrames = new MotionTrailFrame[NUM_MOTION_TRAIL_FRAMES];
    public int mMotionTrailCount;

    /** Monotonic allocation id replacing C++ pointer comparison (see docs/research/03-board-core.md §7.3). */
    public long mAllocId = GameObject.nextAllocId();

    /** Ported from src/Lawn/GridItem.cpp:23-63 (GridItem::GridItem). */
    public GridItem() {
        mApp = LawnApp.gLawnApp;
        mPosX = 0.0f;
        mPosY = 0.0f;
        mBoard = mApp.mBoard;
        mGoalX = 0.0f;
        mGoalY = 0.0f;
        mGridItemType = GridItemType.GRIDITEM_NONE;
        mGridX = 0;
        mGridY = 0;
        mGridItemCounter = 0;
        mRenderOrder = 0;
        mDead = false;
        mGridItemReanimID = ReanimationID.REANIMATIONID_NULL;
        mGridItemParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        mZombieType = ZombieType.ZOMBIE_INVALID;
        mSeedType = SeedType.SEED_NONE;
        mScaryPotType = ScaryPotType.SCARYPOT_NONE;
        mHighlighted = false;
        mTransparentCounter = 0;
        mSunCount = 0;
        for (int i = 0; i < NUM_MOTION_TRAIL_FRAMES; i++) {
            mMotionTrailFrames[i] = new MotionTrailFrame();
        }
        mMotionTrailCount = 0;
    }

    /** Ported from src/Lawn/GridItem.cpp:65-81 (GridItem::GridItemDie). */
    public void gridItemDie() {
        mDead = true;

        Reanimation aGridItemReanim = mApp.reanimationTryToGet(mGridItemReanimID);
        if (aGridItemReanim != null) {
            aGridItemReanim.reanimationDie();
            mGridItemReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        var aGridItemParticle = mApp.particleTryToGet(mGridItemParticleID);
        if (aGridItemParticle != null) {
            aGridItemParticle.particleSystemDie();
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:83-93 (GridItem::DrawGridItemOverlay). */
    public void drawGridItemOverlay(Graphics g) {
        if (mGridItemType == GridItemType.GRIDITEM_STINKY) {
            if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE
                    && !mApp.mZenGarden.isStinkyHighOnChocolate()) {
                g.drawImageF(Resources.getImageById(ImageID.IMAGE_PLANTSPEECHBUBBLE), mPosX + 50.0f, mPosY - 36.0f);
                PvzpCommon.pvzpDrawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_CHOCOLATE),
                        mPosX + 63.0f, mPosY - 28.0f, 0.44f, 0.44f);
            }
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:95-126 (GridItem::DrawGridItem). */
    public void drawGridItem(Graphics g) {
        switch (mGridItemType) {
            case GridItemType.GRIDITEM_GRAVESTONE -> drawGraveStone(g);
            case GridItemType.GRIDITEM_CRATER -> drawCrater(g);
            case GridItemType.GRIDITEM_LADDER -> drawLadder(g);
            case GridItemType.GRIDITEM_PORTAL_CIRCLE -> {
            }
            case GridItemType.GRIDITEM_PORTAL_SQUARE -> {
            }
            case GridItemType.GRIDITEM_ZEN_TOOL -> {
            }
            case GridItemType.GRIDITEM_RAKE -> {
            }
            case GridItemType.GRIDITEM_BRAIN ->
                    g.drawImageF(Resources.getImageById(ImageID.IMAGE_BRAIN), mPosX, mPosY);
            case GridItemType.GRIDITEM_SCARY_POT -> drawScaryPot(g);
            // squirrels are invisible in the original
            case GridItemType.GRIDITEM_SQUIRREL -> {
            }
            case GridItemType.GRIDITEM_STINKY -> drawStinky(g);
            case GridItemType.GRIDITEM_IZOMBIE_BRAIN -> drawIZombieBrain(g);
            default -> throw new IllegalStateException("Unexpected GridItemType " + mGridItemType);
        }

        Reanimation aGridItemReanim = mApp.reanimationTryToGet(mGridItemReanimID);
        if (aGridItemReanim != null) {
            aGridItemReanim.draw(g);
        }

        var aGridItemParticle = mApp.particleTryToGet(mGridItemParticleID);
        if (aGridItemParticle != null) {
            aGridItemParticle.draw(g);
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:128-155 (GridItem::DrawIZombieBrain). */
    public void drawIZombieBrain(Graphics g) {
        if (mGridItemState == GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED) {
            PvzpCommon.pvzpDrawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_BRAIN),
                    mPosX, mPosY + 20.0f, 1.0f, 0.25f);
            return;
        }

        if (mBoard.mAdvice.mDuration > 0 && mBoard.mHelpIndex == AdviceType.ADVICE_I_ZOMBIE_EAT_ALL_BRAINS) {
            Color aFlashingColor = PvzpCommon.getFlashingColor(mBoard.mMainCounter, 75);
            g.setColorizeImages(true);
            g.setColor(aFlashingColor);
        }

        g.drawImageF(Resources.getImageById(ImageID.IMAGE_BRAIN), mPosX, mPosY);
        if (mTransparentCounter > 0) {
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.setColorizeImages(true);
            g.setColor(new Color(255, 255, 255, Math.clamp(mTransparentCounter * 3, 0, 255)));
            g.drawImageF(Resources.getImageById(ImageID.IMAGE_BRAIN), mPosX, mPosY);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
            g.setColorizeImages(false);
        }

        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/GridItem.cpp:157-199 (GridItem::DrawGraveStone). */
    public void drawGraveStone(Graphics g) {
        if (mGridItemCounter <= 0) {
            return;
        }

        int aHeightPosition = PvzpCommon.pvzpAnimateCurve(0, 100, mGridItemCounter, 1000, 0, PvzpCurves.CURVE_EASE_IN_OUT);
        int aGridCelLook = mBoard.mGridCelLook[mGridX][mGridY];
        int aGridCelOffsetX = mBoard.mGridCelOffset[mGridX][mGridY][0];
        int aGridCelOffsetY = mBoard.mGridCelOffset[mGridX][mGridY][1];
        int aCelWidth = Resources.getImageById(ImageID.IMAGE_TOMBSTONES).getCelWidth();
        int aCelHeight = Resources.getImageById(ImageID.IMAGE_TOMBSTONES).getCelHeight();
        int aGraveCol = aGridCelLook % 5;
        int aGraveRow;
        if (mGridY == 0) {
            aGraveRow = 1;
        } else if (mGridItemState == GridItemState.GRIDITEM_STATE_GRAVESTONE_SPECIAL) {
            aGraveRow = 0;
        } else {
            aGraveRow = 2 + aGridCelLook % 2;
        }

        int aVisibleHeight = PvzpCommon.pvzpAnimateCurve(0, 1000, aHeightPosition, aCelHeight, 0, PvzpCurves.CURVE_EASE_IN_OUT);
        int aExtraBottomClip = PvzpCommon.pvzpAnimateCurve(0, 50, aHeightPosition, 0, 14, PvzpCurves.CURVE_EASE_IN_OUT);
        int aVisibleHeightDirt = PvzpCommon.pvzpAnimateCurve(500, 1000, aHeightPosition, aCelHeight, 0, PvzpCurves.CURVE_EASE_IN_OUT);
        int aExtraTopClip = 0;
        Plant aPlant = mBoard.getTopPlantAt(mGridX, mGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
        if (aPlant != null && aPlant.mState == PlantState.STATE_GRAVEBUSTER_EATING) {
            aExtraTopClip = (int) PvzpCommon.pvzpAnimateCurveFloat(
                    400, 0, aPlant.mStateCountdown, 10.0f, 40.0f, PvzpCurves.CURVE_LINEAR);
        }

        Rect aSrcRect = new Rect(aCelWidth * aGraveCol, aCelHeight * aGraveRow + aExtraTopClip,
                aCelWidth, aVisibleHeight - aExtraBottomClip - aExtraTopClip);
        Rect aSrcRectDirt = new Rect(aCelWidth * aGraveCol, aCelHeight * aGraveRow, aCelWidth, aVisibleHeightDirt);
        int x = mBoard.gridToPixelX(mGridX, mGridY) + aGridCelOffsetX - 4;
        int y = mBoard.gridToPixelY(mGridX, mGridY) + aCelHeight + aGridCelOffsetY - 9;
        g.drawImage(Resources.getImageById(ImageID.IMAGE_TOMBSTONES), x, y - aVisibleHeight + aExtraTopClip, aSrcRect);
        g.drawImage(Resources.getImageById(ImageID.IMAGE_TOMBSTONE_MOUNDS), x, y - aVisibleHeightDirt, aSrcRectDirt);
    }

    /** Ported from src/Lawn/GridItem.cpp:200-238 (GridItem::DrawStinky). */
    public void drawStinky(Graphics g) {
        Reanimation aStinkyReanim = mApp.reanimationGet(mGridItemReanimID);
        float aOriginalTime = aStinkyReanim.mAnimTime;

        assert mMotionTrailCount <= NUM_MOTION_TRAIL_FRAMES;
        for (int i = mMotionTrailCount - 1; i >= 0; i--) {
            if (i % 2 != 0) {
                MotionTrailFrame aFrame = mMotionTrailFrames[i];
                float aDiffX = aFrame.mPosX - mPosX;
                float aDiffY = aFrame.mPosY - mPosY;

                int anAlpha = PvzpCommon.pvzpAnimateCurve(0, 11, i, 64, 16, PvzpCurves.CURVE_LINEAR);
                g.setColor(new Color(255, 255, 255, anAlpha));
                g.setColorizeImages(true);
                aStinkyReanim.mAnimTime = aFrame.mAnimTime;

                g.mTransX += aDiffX;
                g.mTransY += aDiffY;
                aStinkyReanim.draw(g);

                g.setColorizeImages(false);
                g.mTransX -= aDiffX;
                g.mTransY -= aDiffY;
            }
        }
        aStinkyReanim.mAnimTime = aOriginalTime;

        if (mGridItemType == GridItemType.GRIDITEM_STINKY && mHighlighted) {
            aStinkyReanim.mEnableExtraAdditiveDraw = true;
            aStinkyReanim.mExtraAdditiveColor = new Color(255, 255, 255, 196);
        }
        aStinkyReanim.draw(g);
        aStinkyReanim.mEnableExtraAdditiveDraw = false;
    }

    /** Ported from src/Lawn/GridItem.cpp:240-312 (GridItem::DrawCrater). */
    public void drawCrater(Graphics g) {
        float aXPos = mBoard.gridToPixelX(mGridX, mGridY) - 8.0f;
        float aYPos = mBoard.gridToPixelY(mGridX, mGridY) + 40.0f;
        if (mGridItemCounter < 25) {
            int anAlpha = PvzpCommon.pvzpAnimateCurve(25, 0, mGridItemCounter, 255, 0, PvzpCurves.CURVE_LINEAR);
            g.setColor(new Color(255, 255, 255, anAlpha));
            g.setColorizeImages(true);
        }

        boolean fading = mGridItemCounter < 9000;
        int aImage = ImageID.IMAGE_CRATER;
        int aCelCol = 0;

        if (mBoard.isPoolSquare(mGridX, mGridY)) {
            if (mBoard.stageIsNight()) {
                aImage = ImageID.IMAGE_CRATER_WATER_NIGHT;
            } else {
                aImage = ImageID.IMAGE_CRATER_WATER_DAY;
            }

            if (fading) {
                aCelCol = 1;
            }

            // sway loops every 200 frames; the local modulo avoids float drift after long runs
            final int CRATER_ANIM_PERIOD = 200;
            float aPos = (float) (mGridY * GameConstants.PI + mGridX * GameConstants.PI * 0.25f);
            float aTime = (float) ((mBoard.mMainCounter % CRATER_ANIM_PERIOD)
                    * (GameConstants.PI * 2.0f / (double) CRATER_ANIM_PERIOD));
            aYPos += (float) Math.sin(aPos + aTime) * 2.0f;
        } else if (mBoard.stageHasRoof()) {
            if (mGridX < 5) {
                aImage = ImageID.IMAGE_CRATER_ROOF_LEFT;
                aXPos += 16.0f;
                aYPos += -16.0f;
            } else {
                aImage = ImageID.IMAGE_CRATER_ROOF_CENTER;
                aXPos += 18.0f;
                aYPos += -9.0f;
            }

            if (fading) {
                aCelCol = 1;
            }
        } else if (mBoard.stageIsNight()) {
            aCelCol = 1;
            if (fading) {
                aImage = ImageID.IMAGE_CRATER_FADING;
            }
        } else if (fading) {
            aImage = ImageID.IMAGE_CRATER_FADING;
        }

        PvzpCommon.pvzpDrawImageCelF(g, Resources.getImageById(aImage), aXPos, aYPos, aCelCol, 0);
        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/GridItem.cpp:314-400 (GridItem::DrawScaryPot). */
    public void drawScaryPot(Graphics g) {
        int aImageCol = mGridItemState - GridItemState.GRIDITEM_STATE_SCARY_POT_QUESTION;
        assert aImageCol >= 0 && aImageCol < 3;

        int aXPos = mBoard.gridToPixelX(mGridX, mGridY) - 5;
        int aYPos = mBoard.gridToPixelY(mGridX, mGridY) - 15;
        PvzpCommon.pvzpDrawImageCelCenterScaledF(g, Resources.getImageById(ImageID.IMAGE_PLANTSHADOW2),
                aXPos - 5.0f, aYPos + 72.0f, 0, 1.3f, 1.3f);

        if (mTransparentCounter > 0) {
            Image scaryPot = Resources.getImageById(ImageID.IMAGE_SCARY_POT);
            g.drawImageCel(scaryPot, aXPos, aYPos, aImageCol, 0);

            Graphics aInsideGraphics = new Graphics(g);
            if (mScaryPotType == ScaryPotType.SCARYPOT_SEED) {
                aInsideGraphics.mScaleX = 0.7f;
                aInsideGraphics.mScaleY = 0.7f;
                SeedPacket.drawSeedPacket(aInsideGraphics, aXPos + 23.0f, aYPos + 33.0f,
                        mSeedType, SeedType.SEED_NONE, 0.0f, 255, false, false);
            } else if (mScaryPotType == ScaryPotType.SCARYPOT_ZOMBIE) {
                aInsideGraphics.mScaleX = 0.4f;
                aInsideGraphics.mScaleY = 0.4f;
                float aOffsetX = 6.0f;
                float aOffsetY = 19.0f;
                if (mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
                    aInsideGraphics.mScaleX = 0.4f;
                    aInsideGraphics.mScaleY = 0.4f;
                    aOffsetX = 1.0f;
                    aOffsetY = 16.0f;
                }
                if (mZombieType == ZombieType.ZOMBIE_GARGANTUAR) {
                    aInsideGraphics.mScaleX = 0.3f;
                    aInsideGraphics.mScaleY = 0.3f;
                    aOffsetX += 9.0f;
                    aOffsetY += 7.0f;
                }
                mApp.mReanimatorCache.drawCachedZombie(aInsideGraphics, aXPos + aOffsetX, aYPos + aOffsetY, mZombieType);
            } else if (mScaryPotType == ScaryPotType.SCARYPOT_SUN) {
                int aSuns = mBoard.mChallenge.scaryPotterCountSunInPot(this);

                Reanimation aReanim = new Reanimation();
                aReanim.reanimationInitializeType(0.0f, 0.0f, ReanimationType.REANIM_SUN);
                aReanim.overrideScale(0.5f, 0.5f);

                for (int i = 0; i < aSuns; i++) {
                    float aOffsetX = 42.0f;
                    float aOffsetY = 62.0f;
                    switch (i) {
                        case 1 -> {
                            aOffsetX += 3.0f;
                            aOffsetY -= 20.0f;
                        }
                        case 2 -> {
                            aOffsetX -= 6.0f;
                            aOffsetY -= 10.0f;
                        }
                        case 3 -> {
                            aOffsetX += 6.0f;
                            aOffsetY -= 5.0f;
                        }
                        case 4 -> {
                            aOffsetX += 5.0f;
                            aOffsetY -= 15.0f;
                        }
                        default -> {
                        }
                    }

                    aReanim.setPosition(aXPos + aOffsetX, aYPos + aOffsetY);
                    aReanim.draw(g);
                }
                aReanim.reanimationDelete();
            }

            int aAlpha = PvzpCommon.pvzpAnimateCurve(0, 50, mTransparentCounter, 255, 58, PvzpCurves.CURVE_LINEAR);
            g.setColorizeImages(true);
            g.setColor(new Color(255, 255, 255, aAlpha));
        }

        g.drawImageCel(Resources.getImageById(ImageID.IMAGE_SCARY_POT), aXPos, aYPos, aImageCol, 1);
        if (mHighlighted) {
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.setColorizeImages(true);
            if (mTransparentCounter == 0) {
                g.setColor(new Color(255, 255, 255, 196));
            }
            g.drawImageCel(Resources.getImageById(ImageID.IMAGE_SCARY_POT), aXPos, aYPos, aImageCol, 1);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        }

        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/GridItem.cpp:402-407 (GridItem::DrawLadder). */
    public void drawLadder(Graphics g) {
        int aXPos = mBoard.gridToPixelX(mGridX, mGridY);
        int aYPos = mBoard.gridToPixelY(mGridX, mGridY);
        PvzpCommon.pvzpDrawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_LADDER_5),
                aXPos + 25.0f, aYPos - 4.0f, 0.8f, 0.8f);
    }

    /** Ported from src/Lawn/GridItem.cpp:409-417 (GridItem::AddGraveStoneParticles). */
    public void addGraveStoneParticles() {
        int aXOffset = mBoard.mGridCelOffset[mGridX][mGridY][0];
        int aYOffset = mBoard.mGridCelOffset[mGridX][mGridY][1];
        int aXPos = mBoard.gridToPixelX(mGridX, mGridY) + 14 + aXOffset;
        int aYPos = mBoard.gridToPixelY(mGridX, mGridY) + 78 + aYOffset;
        mApp.addPvzpParticle(aXPos, aYPos, mRenderOrder + 1, ParticleEffect.PARTICLE_GRAVE_STONE_RISE);
        mApp.playFoley(FoleyType.FOLEY_DIRT_RISE);
    }

    /** Ported from src/Lawn/GridItem.cpp:419-451 (GridItem::OpenPortal). */
    public void openPortal() {
        float aXPos = mGridX * 80.0f - 6.0f;
        float aYPos = mBoard.gridToPixelY(0, mGridY) - 65.0f;
        Reanimation aPortalReanim = mApp.reanimationTryToGet(mGridItemReanimID);
        if (aPortalReanim == null) {
            int aReanimType = ReanimationType.REANIM_PORTAL_CIRCLE;
            if (mGridItemType == GridItemType.GRIDITEM_PORTAL_SQUARE) {
                aYPos += 25.0f;
                aXPos -= 4.0f;
                aReanimType = ReanimationType.REANIM_PORTAL_SQUARE;
            }
            aPortalReanim = mApp.addReanimation(aXPos, aYPos, 0, aReanimType);
            aPortalReanim.mIsAttachment = true;
            mGridItemReanimID = mApp.reanimationGetID(aPortalReanim);
        } else {
            aPortalReanim.setPosition(aXPos, aYPos);
        }

        var aPortalParticle = mApp.particleTryToGet(mGridItemParticleID);
        if (aPortalParticle != null) {
            aPortalParticle.particleSystemDie();
            mGridItemParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        }

        aPortalReanim.playReanim("anim_appear", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 12.0f);
        mApp.playFoley(FoleyType.FOLEY_PORTAL);
    }

    /** Ported from src/Lawn/GridItem.cpp:453-469 (GridItem::ClosePortal). */
    public void closePortal() {
        Reanimation aPortalReanim = mApp.reanimationTryToGet(mGridItemReanimID);
        if (aPortalReanim != null) {
            aPortalReanim.playReanim("anim_dissapear", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 12.0f);
        }

        var aPortalParticle = mApp.particleTryToGet(mGridItemParticleID);
        if (aPortalParticle != null) {
            aPortalParticle.particleSystemDie();
            mGridItemParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        }

        mGridItemState = GridItemState.GRIDITEM_STATE_PORTAL_CLOSED;
    }

    /** Ported from src/Lawn/GridItem.cpp:471-475 (GridItem::IsOpenPortal). */
    public boolean isOpenPortal() {
        return mGridItemState != GridItemState.GRIDITEM_STATE_PORTAL_CLOSED
                && (mGridItemType == GridItemType.GRIDITEM_PORTAL_CIRCLE
                || mGridItemType == GridItemType.GRIDITEM_PORTAL_SQUARE);
    }

    /** Ported from src/Lawn/GridItem.cpp:477-504 (GridItem::UpdatePortal). */
    public void updatePortal() {
        Reanimation aPortalReanim = mApp.reanimationGet(mGridItemReanimID);
        if (mGridItemState == GridItemState.GRIDITEM_STATE_PORTAL_CLOSED) {
            if (aPortalReanim.mLoopCount > 0) {
                gridItemDie();
            }
        } else if (aPortalReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD && aPortalReanim.mLoopCount > 0) {
            aPortalReanim.playReanim("anim_pulse", ReanimLoopType.REANIM_LOOP, 0, 12.0f);

            int aEffect = ParticleEffect.PARTICLE_PORTAL_CIRCLE;
            float aXPos = mGridX * 80.0f + 13.0f;
            float aYPos = mBoard.gridToPixelY(0, mGridY) - 39.0f;
            if (mGridItemType == GridItemType.GRIDITEM_PORTAL_SQUARE) {
                aEffect = ParticleEffect.PARTICLE_PORTAL_SQUARE;
                aXPos -= 8.0f;
                aYPos += 15.0f;
            }

            var aParticle = mApp.addPvzpParticle(aXPos, aYPos, 0, aEffect);
            mGridItemParticleID = mApp.particleGetID(aParticle);
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:506-540 (GridItem::UpdateScaryPot). */
    public void updateScaryPot() {
        if (mApp.mCheatKeys && mApp.mWidgetManager.mKeyDown[KeyCode.KEYCODE_SHIFT]) {
            if (mTransparentCounter < 50) {
                mTransparentCounter++;
            }
            return;
        }

        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_PLANTERN && !aPlant.notOnGround()) {
                int aDiffX = Math.abs(aPlant.mPlantCol - mGridX);
                int aDiffY = Math.abs(aPlant.mRow - mGridY);
                if (Math.max(aDiffX, aDiffY) <= 1) {
                    if (mTransparentCounter < 50) {
                        mTransparentCounter++;
                    }
                    return;
                }
            }
        }

        if (mTransparentCounter > 0) {
            mTransparentCounter--;
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:542-557 (GridItem::UpdateBrain). */
    public void updateBrain() {
        if (mGridItemState == GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED) {
            mGridItemCounter--;
            if (mGridItemCounter <= 0) {
                gridItemDie();
            }
        }

        if (mTransparentCounter > 0) {
            mTransparentCounter--;
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:559-589 (GridItem::Update). */
    public void update() {
        Reanimation aGridItemReanim = mApp.reanimationTryToGet(mGridItemReanimID);
        if (aGridItemReanim != null) {
            aGridItemReanim.update();
        }

        var aGridItemParticle = mApp.particleTryToGet(mGridItemParticleID);
        if (aGridItemParticle != null) {
            aGridItemParticle.update();
        }

        if (mGridItemType == GridItemType.GRIDITEM_PORTAL_CIRCLE || mGridItemType == GridItemType.GRIDITEM_PORTAL_SQUARE) {
            updatePortal();
        }
        if (mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
            updateScaryPot();
        }
        if (mGridItemType == GridItemType.GRIDITEM_RAKE) {
            updateRake();
        }
        if (mGridItemType == GridItemType.GRIDITEM_IZOMBIE_BRAIN) {
            updateBrain();
        }
    }

    /** Ported from src/Lawn/GridItem.cpp:591-610 (GridItem::RakeFindZombie). */
    public Zombie rakeFindZombie() {
        Rect aRakeRect = new Rect((int) mPosX, (int) mPosY, 63, 80);

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying() && !aZombie.isBobsledTeamWithSled() && aZombie.mRow - mGridY == 0
                    && aZombie.effectedByDamage(1 << DamageRangeFlags.DAMAGES_GROUND)) {
                Rect aZombieRect = aZombie.getZombieRect();
                if (Board.getRectOverlap(aRakeRect, aZombieRect) >= 0) {
                    return aZombie;
                }
            }
        }

        return null;
    }

    /** Ported from src/Lawn/GridItem.cpp:612-644 (GridItem::UpdateRake). */
    public void updateRake() {
        if (mGridItemState == GridItemState.GRIDITEM_STATE_RAKE_ATTRACTING
                || mGridItemState == GridItemState.GRIDITEM_STATE_RAKE_WAITING) {
            if (rakeFindZombie() != null) {
                Reanimation aRakeReanim = mApp.reanimationGet(mGridItemReanimID);
                aRakeReanim.mAnimRate = 20.0f;
                mGridItemCounter = 200;
                mGridItemState = GridItemState.GRIDITEM_STATE_RAKE_TRIGGERED;
                mApp.playFoley(FoleyType.FOLEY_SWING);
            }
        } else if (mGridItemState == GridItemState.GRIDITEM_STATE_RAKE_TRIGGERED) {
            Reanimation aRakeReanim = mApp.reanimationGet(mGridItemReanimID);
            if (aRakeReanim != null && aRakeReanim.shouldTriggerTimedEvent(0.8f)) {
                Zombie aZombie = rakeFindZombie();
                if (aZombie != null) {
                    aZombie.takeDamage(1800, 0);
                    mApp.playFoley(FoleyType.FOLEY_BONK);
                }
            }

            mGridItemCounter--;
            if (mGridItemCounter == 0) {
                gridItemDie();
            }
        }
    }
}
