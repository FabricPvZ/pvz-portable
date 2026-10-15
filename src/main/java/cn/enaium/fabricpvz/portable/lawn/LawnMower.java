/*
 * Ported from src/Lawn/LawnMower.h:32-73 and src/Lawn/LawnMower.cpp:23-428.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/LawnMower.h:45-72 and src/Lawn/LawnMower.cpp.
 *
 * <p>Not a {@link GameObject} upstream, so {@code mAllocId} is allocated explicitly for the
 * deterministic render-order tie-break.
 */
public class LawnMower {

    public LawnApp mApp;
    public Board mBoard;
    public float mPosX;
    public float mPosY;
    public int mRenderOrder;
    public int mRow;
    public int mAnimTicksPerFrame;
    public int mReanimID;
    public int mChompCounter;
    public int mRollingInCounter;
    public int mSquishedCounter;
    public int mMowerState;
    public boolean mDead;
    public boolean mVisible;
    public int mMowerType;
    public float mAltitude;
    public int mMowerHeight;
    public int mLastPortalX;

    /** Monotonic allocation id replacing C++ pointer comparison (see docs/research/03-board-core.md §7.3). */
    public long mAllocId = GameObject.nextAllocId();

    /** Ported from src/Lawn/LawnMower.cpp:30-85 (LawnMower::LawnMowerInitialize). */
    public void lawnMowerInitialize(int theRow) {
        mApp = LawnApp.gLawnApp;
        mRow = theRow;
        mPosX = -160.0f;
        mBoard = mApp.mBoard;
        mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, theRow, 0);
        mPosY = mBoard.getPosYBasedOnRow(mPosX + 40.0f, theRow) + 23.0f;
        mDead = false;
        mMowerState = LawnMowerState.MOWER_READY;
        mVisible = true;
        mChompCounter = 0;
        mRollingInCounter = 0;
        mSquishedCounter = 0;
        mLastPortalX = -1;

        int aReanimType;
        if (mBoard.stageHasRoof()) {
            mMowerType = LawnMowerType.LAWNMOWER_ROOF;
            aReanimType = ReanimationType.REANIM_ROOF_CLEANER;
        } else if (mBoard.mPlantRow[mRow] == PlantRowType.PLANTROW_POOL
                && mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_POOL_CLEANER] != 0) {
            mMowerType = LawnMowerType.LAWNMOWER_POOL;
            aReanimType = ReanimationType.REANIM_POOL_CLEANER;
        } else {
            mMowerType = LawnMowerType.LAWNMOWER_LAWN;
            aReanimType = ReanimationType.REANIM_LAWNMOWER;
        }

        Reanimation aMowerReanim = mApp.addReanimation(0.0f, 18.0f, mRenderOrder, aReanimType);
        aMowerReanim.mAnimRate = 0.0f;
        aMowerReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
        aMowerReanim.mIsAttachment = true;
        aMowerReanim.overrideScale(0.85f, 0.85f);
        mReanimID = mApp.reanimationGetID(aMowerReanim);

        if (mMowerType == LawnMowerType.LAWNMOWER_LAWN) {
            aMowerReanim.setFramesForLayer("anim_normal");
        } else if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
            aMowerReanim.overrideScale(0.8f, 0.8f);
            aMowerReanim.setFramesForLayer("anim_land");
            aMowerReanim.setTruncateDisappearingFrames(null, false);
        }

        if (mBoard.mSuperMowerMode && mMowerType == LawnMowerType.LAWNMOWER_LAWN) {
            enableSuperMower(true);
        }
    }

    /** Ported from src/Lawn/LawnMower.cpp:87-141 (LawnMower::UpdatePool). */
    public void updatePool() {
        boolean isPoolRange = false;
        if (mPosX > 26.0f && mPosX < 660.0f) {
            isPoolRange = true;
        }

        Reanimation aMowerReanim = mApp.reanimationGet(mReanimID);
        if (isPoolRange && mMowerHeight == MowerHeight.MOWER_HEIGHT_LAND) {
            Reanimation aSplashReanim = mApp.addReanimation(mPosX + 0.0f, mPosY + 25.0f, mRenderOrder + 1, ReanimationType.REANIM_SPLASH);
            aSplashReanim.overrideScale(1.2f, 0.8f);
            mApp.addPvzpParticle(mPosX + 0.0f + 50.0f, mPosY + 0.0f + 42.0f, mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);
            mApp.playFoley(FoleyType.FOLEY_ZOMBIESPLASH);
            mMowerHeight = MowerHeight.MOWER_HEIGHT_DOWN_TO_POOL;
        } else if (mMowerHeight == MowerHeight.MOWER_HEIGHT_DOWN_TO_POOL) {
            mAltitude -= 2.0f;
            if (mAltitude <= -28.0f) {
                mAltitude = 0.0f;
                mMowerHeight = MowerHeight.MOWER_HEIGHT_IN_POOL;
                aMowerReanim.playReanim("anim_water", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
            }
        } else if (mMowerHeight == MowerHeight.MOWER_HEIGHT_IN_POOL) {
            if (!isPoolRange) {
                mAltitude = -28.0f;
                mMowerHeight = MowerHeight.MOWER_HEIGHT_UP_TO_LAND;
                Reanimation aSplashReanim = mApp.addReanimation(mPosX + 0.0f, mPosY + 25.0f, mRenderOrder + 1, ReanimationType.REANIM_SPLASH);
                aSplashReanim.overrideScale(1.2f, 0.8f);
                mApp.addPvzpParticle(mPosX + 0.0f + 50.0f, mPosY + 0.0f + 42.0f, mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);
                mApp.playFoley(FoleyType.FOLEY_PLANT_WATER);
                aMowerReanim.playReanim("anim_land", ReanimLoopType.REANIM_LOOP, 0, 0.0f);
            }
        } else if (mMowerHeight == MowerHeight.MOWER_HEIGHT_UP_TO_LAND) {
            mAltitude += 2.0f;
            if (mAltitude >= 0.0f) {
                mAltitude = 0.0f;
                mMowerHeight = MowerHeight.MOWER_HEIGHT_LAND;
            }
        }

        if (mMowerHeight == MowerHeight.MOWER_HEIGHT_IN_POOL
                && aMowerReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD && aMowerReanim.mLoopCount > 0) {
            aMowerReanim.playReanim("anim_water", ReanimLoopType.REANIM_LOOP, 10, 35.0f);
        }
    }

    /** Ported from src/Lawn/LawnMower.cpp:143-176 (LawnMower::MowZombie). */
    public void mowZombie(Zombie theZombie) {
        if (mMowerState == LawnMowerState.MOWER_READY) {
            startMower();
            mChompCounter = 25;
        } else if (mMowerState == LawnMowerState.MOWER_TRIGGERED) {
            mChompCounter = 50;
        }

        if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
            mApp.playFoley(FoleyType.FOLEY_SHOOP);
            if (mMowerHeight == MowerHeight.MOWER_HEIGHT_IN_POOL) {
                Reanimation aMowerReanim = mApp.reanimationGet(mReanimID);
                aMowerReanim.playReanim("anim_suck", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 35.0f);
            } else {
                Reanimation aMowerReanim = mApp.reanimationGet(mReanimID);
                aMowerReanim.playReanim("anim_landsuck", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 35.0f);
            }

            theZombie.dieWithLoot();
        } else {
            mApp.playFoley(FoleyType.FOLEY_SPLAT);
            theZombie.mowDown();
        }
    }

    /** Ported from src/Lawn/LawnMower.cpp:178-273 (LawnMower::Update). */
    public void update() {
        if (mMowerState == LawnMowerState.MOWER_SQUISHED) {
            mSquishedCounter--;
            if (mSquishedCounter <= 0) {
                die();
            }
            return;
        }

        if (mMowerState == LawnMowerState.MOWER_ROLLING_IN) {
            mRollingInCounter++;
            mPosX = PvzpCommon.pvzpAnimateCurveFloat(0, 100, mRollingInCounter, -160.0f, -21.0f, PvzpCurves.CURVE_EASE_IN_OUT);
            if (mRollingInCounter == 100) {
                mMowerState = LawnMowerState.MOWER_READY;
            }
            return;
        }

        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && !mBoard.mCutScene.shouldRunUpsellBoard()) {
            return;
        }

        Rect aAttackRect = getLawnMowerAttackRect();
        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                continue;
            }

            if (aZombie.mRow - mRow != 0) {
                continue;
            }

            if (aZombie.mZombieType != ZombieType.ZOMBIE_BOSS
                    && aZombie.mRow - mRow == 0
                    && aZombie.mZombiePhase != ZombiePhase.PHASE_ZOMBIE_MOWERED
                    && !aZombie.isTangleKelpTarget()
                    && aZombie.effectedByDamage(127)) {
                Rect aZombieRect = aZombie.getZombieRect();
                int aOverlap = Board.getRectOverlap(aAttackRect, aZombieRect);
                if (aOverlap > (aZombie.mZombieType == ZombieType.ZOMBIE_BALLOON ? 20 : 0)) {
                    // bungee zombies and dead zombies cannot trigger the mower themselves
                    if (mMowerState != LawnMowerState.MOWER_READY
                            || (aZombie.mZombieType != ZombieType.ZOMBIE_BUNGEE && aZombie.mHasHead)) {
                        mowZombie(aZombie);
                    }
                }
            }
        }

        if (mMowerState != LawnMowerState.MOWER_TRIGGERED && mMowerState != LawnMowerState.MOWER_SQUISHED) {
            return;
        }

        float aSpeed = 3.33f;
        if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
            aSpeed = 2.5f;
        }
        if (mChompCounter > 0) {
            mChompCounter--;
            aSpeed = PvzpCommon.pvzpAnimateCurveFloat(50, 0, mChompCounter, aSpeed, 1.0f, PvzpCurves.CURVE_BOUNCE_SLOW_MIDDLE);
        }
        mPosX += aSpeed;
        mPosY = mBoard.getPosYBasedOnRow(mPosX + 40.0f, mRow) + 23.0f;

        if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
            updatePool();
        }
        if (mMowerType == LawnMowerType.LAWNMOWER_LAWN && mBoard.mPlantRow[mRow] == PlantRowType.PLANTROW_POOL && mPosX > 50.0f) {
            Reanimation aSplashReanim = mApp.addReanimation(mPosX, mPosY + 25.0f, mRenderOrder + 1, ReanimationType.REANIM_SPLASH);
            aSplashReanim.overrideScale(1.2f, 0.8f);
            mApp.addPvzpParticle(mPosX + 50.0f, mPosY + 67.0f, mRenderOrder + 1, ParticleEffect.PARTICLE_PLANTING_POOL);
            mApp.playSample(SoundType.SOUND_ZOMBIE_ENTERING_WATER);
            mApp.mSoundSystem.stopFoley(FoleyType.FOLEY_LAWNMOWER);
            die();
        }
        if (mPosX > GameConstants.WIDE_BOARD_WIDTH) {
            die();
        }

        mApp.reanimationGet(mReanimID).update();
    }

    /** Ported from src/Lawn/LawnMower.cpp:275-367 (LawnMower::Draw). */
    public void draw(Graphics g) {
        if (!mVisible) {
            return;
        }

        if (mMowerHeight != MowerHeight.MOWER_HEIGHT_UP_TO_LAND
                && mMowerHeight != MowerHeight.MOWER_HEIGHT_DOWN_TO_POOL
                && mMowerHeight != MowerHeight.MOWER_HEIGHT_IN_POOL
                && mMowerState != LawnMowerState.MOWER_SQUISHED) {
            int aShadowType = 0;
            float aScaleX = 1.0f;
            float aScaleY = 1.0f;
            if (mBoard.stageIsNight()) {
                aShadowType = 1;
            }

            float aShadowX = mPosX - 7.0f;
            float aShadowY = mPosY - mAltitude + 47.0f;
            if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
                aShadowX -= 17.0f;
                aShadowY -= 8.0f;
            }
            if (mMowerType == LawnMowerType.LAWNMOWER_ROOF) {
                aShadowX -= 9.0f;
                aShadowY -= 36.0f;
                aScaleY = 1.2f;
                if (mMowerState == LawnMowerState.MOWER_TRIGGERED) {
                    aShadowY += 36.0f;
                }
            }

            if (aShadowType == 0) {
                PvzpCommon.pvzpDrawImageCelCenterScaledF(g, Resources.getImageById(ImageID.IMAGE_PLANTSHADOW),
                        aShadowX, aShadowY, 0, aScaleX, aScaleY);
            } else {
                PvzpCommon.pvzpDrawImageCelCenterScaledF(g, Resources.getImageById(ImageID.IMAGE_PLANTSHADOW2),
                        aShadowX, aShadowY, 0, aScaleX, aScaleY);
            }
        }

        Graphics aMowerGraphics = new Graphics(g);
        aMowerGraphics.mTransX += mPosX + 6.0f;
        aMowerGraphics.mTransY += mPosY - mAltitude;
        if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
            if (mMowerState == LawnMowerState.MOWER_TRIGGERED) {
                aMowerGraphics.mTransY -= 7.0f;
                aMowerGraphics.mTransX -= 10.0f;
            } else {
                aMowerGraphics.mTransY -= 33.0f;
            }

            if (mMowerHeight == MowerHeight.MOWER_HEIGHT_UP_TO_LAND || mMowerHeight == MowerHeight.MOWER_HEIGHT_DOWN_TO_POOL) {
                aMowerGraphics.setClipRect(-50, -50, 150, (int) (132 + mAltitude));
            }
        } else if (mMowerType == LawnMowerType.LAWNMOWER_ROOF) {
            if (mMowerState == LawnMowerState.MOWER_TRIGGERED) {
                aMowerGraphics.mTransY -= 4.0f;
                aMowerGraphics.mTransX -= 10.0f;
            } else {
                aMowerGraphics.mTransY -= 40.0f;
            }
        }

        if (mMowerState == LawnMowerState.MOWER_TRIGGERED || mMowerState == LawnMowerState.MOWER_SQUISHED) {
            mApp.reanimationGet(mReanimID).draw(aMowerGraphics);
        } else {
            int aMowerType = mMowerType;
            if (mMowerType == LawnMowerType.LAWNMOWER_LAWN && mBoard.mSuperMowerMode) {
                aMowerType = LawnMowerType.LAWNMOWER_SUPER_MOWER;
            }
            mApp.mReanimatorCache.drawCachedMower(aMowerGraphics, 0.0f, 19.0f, aMowerType);
        }
    }

    /** Ported from src/Lawn/LawnMower.cpp:369-380 (LawnMower::Die). */
    public void die() {
        mDead = true;
        mApp.removeReanimation(mReanimID);
        if (mBoard.mBonusLawnMowersRemaining > 0 && !mBoard.hasLevelAwardDropped()) {
            LawnMower aLawnMower = mBoard.mLawnMowers.dataArrayAlloc();
            aLawnMower.lawnMowerInitialize(mRow);
            aLawnMower.mMowerState = LawnMowerState.MOWER_ROLLING_IN;
            mBoard.mBonusLawnMowersRemaining--;
        }
    }

    /** Ported from src/Lawn/LawnMower.cpp:382-404 (LawnMower::StartMower). */
    public void startMower() {
        if (mMowerState == LawnMowerState.MOWER_TRIGGERED) {
            return;
        }

        Reanimation aMowerReanim = mApp.reanimationGet(mReanimID);
        if (mMowerType == LawnMowerType.LAWNMOWER_POOL) {
            aMowerReanim.mAnimRate = 35.0f;
            mApp.playFoley(FoleyType.FOLEY_POOL_CLEANER);
        } else {
            aMowerReanim.mAnimRate = 70.0f;
            mApp.playFoley(FoleyType.FOLEY_LAWNMOWER);
        }

        mBoard.mWaveRowGotLawnMowered[mRow] = mBoard.mCurrentWave;
        mBoard.mTriggeredLawnMowers++;
        mMowerState = LawnMowerState.MOWER_TRIGGERED;
    }

    /** Ported from src/Lawn/LawnMower.cpp:406-415 (LawnMower::SquishMower). */
    public void squishMower() {
        Reanimation aMowerReanim = mApp.reanimationGet(mReanimID);
        aMowerReanim.overrideScale(0.85f, 0.22f);
        aMowerReanim.setPosition(-11.0f, 65.0f);

        mMowerState = LawnMowerState.MOWER_SQUISHED;
        mSquishedCounter = 500;
        mApp.playFoley(FoleyType.FOLEY_SQUISH);
    }

    /** Ported from src/Lawn/LawnMower.cpp:417-420 (LawnMower::GetLawnMowerAttackRect). */
    public Rect getLawnMowerAttackRect() {
        return new Rect((int) mPosX, (int) mPosY, 50, 80);
    }

    /** Ported from src/Lawn/LawnMower.cpp:422-428 (LawnMower::EnableSuperMower). */
    public void enableSuperMower(boolean theEnable) {
        if (mMowerType == LawnMowerType.LAWNMOWER_LAWN) {
            mApp.reanimationGet(mReanimID).setFramesForLayer("anim_tricked");
        }
    }
}
