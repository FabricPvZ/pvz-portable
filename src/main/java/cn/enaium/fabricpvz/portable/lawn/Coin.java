/*
 * Ported from src/Lawn/Coin.h:36-101 and src/Lawn/Coin.cpp.
 *
 * Sun objects are Coin instances; field/member names and per-type behaviours
 * follow the upstream source verbatim, with upstream line references on the
 * non-obvious maths. Randomness flows through cn.enaium.fabricpvz.portable.pvzp.PvzpCommon /
 * cn.enaium.fabricpvz.portable.sexy.Common (MTRand).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachmentIDRef;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public class Coin extends GameObject {

    // Ported from src/Lawn/Coin.h:46-73.
    public float mPosX;
    public float mPosY;
    public float mVelX;
    public float mVelY;
    public float mScale;
    public boolean mDead;
    public int mFadeCount;
    public float mCollectX;
    public float mCollectY;
    public int mGroundY;
    public int mCoinAge;
    public boolean mIsBeingCollected;
    public int mDisappearCounter;
    public int mType;
    public int mCoinMotion;
    public AttachmentIDRef mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
    public float mCollectionDistance;
    public int mUsableSeedType;
    public PottedPlant mPottedPlantSpec = new PottedPlant();
    public boolean mNeedsBouncyArrow;
    public boolean mHasBouncyArrow;
    public boolean mHitGround;
    public int mTimesDropped;

    /** Ported from src/Lawn/Coin.cpp:40. */
    public Coin() {
    }

    /** Ported from src/Lawn/Coin.cpp:46-405. */
    public void coinInitialize(int theX, int theY, int theCoinType, int theCoinMotion) {
        mPosX = theX;
        mPosY = theY;
        mType = theCoinType;
        mCollectionDistance = 0.0f;
        mDead = false;
        mWidth = 60;
        mHeight = 60;
        mDisappearCounter = 0;
        mIsBeingCollected = false;
        mFadeCount = 0;
        mCoinMotion = theCoinMotion;
        mCoinAge = 0;
        mAttachmentID = new AttachmentIDRef(AttachmentID.ATTACHMENTID_NULL);
        mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_COIN_BANK, 0, 1);
        mScale = 1.0f;
        mUsableSeedType = SeedType.SEED_NONE;
        mNeedsBouncyArrow = false;
        mHasBouncyArrow = false;
        mHitGround = false;
        mTimesDropped = 0;
        mPottedPlantSpec.initializePottedPlant(SeedType.SEED_NONE);

        if (isSun()) {
            float aPosX = mWidth * 0.5f;
            float aPosY = mHeight * 0.5f;
            Reanimation aSunReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_SUN);
            aSunReanim.setPosition(mPosX + aPosX, mPosY + aPosY);
            aSunReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
            aSunReanim.mAnimRate = 6.0f;
            Attachment.attachReanim(mAttachmentID, aSunReanim, aPosX, aPosY);
        } else if (mType == CoinType.COIN_SILVER) {
            mPosX -= 10.0f;
            mPosY -= 8.0f;

            float aPosX = 9.0f;
            float aPosY = 9.0f;
            Reanimation aCoinReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_COIN_SILVER);
            aCoinReanim.setPosition(mPosX + aPosX, mPosY + aPosY);
            aCoinReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
            aCoinReanim.mAnimTime = PvzpCommon.randRangeFloat(0.0f, 0.99f);
            aCoinReanim.mAnimRate *= PvzpCommon.randRangeFloat(0.6f, 1.0f);
            Attachment.attachReanim(mAttachmentID, aCoinReanim, aPosX, aPosY);
        } else if (mType == CoinType.COIN_GOLD) {
            mPosX -= 10.0f;
            mPosY -= 8.0f;

            float aPosX = 9.0f;
            float aPosY = 9.0f;
            Reanimation aCoinReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_COIN_GOLD);
            aCoinReanim.setPosition(mPosX + aPosX, mPosY + aPosY);
            aCoinReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
            aCoinReanim.mAnimTime = PvzpCommon.randRangeFloat(0.0f, 0.99f);
            aCoinReanim.mAnimRate *= PvzpCommon.randRangeFloat(0.6f, 1.0f);
            Attachment.attachReanim(mAttachmentID, aCoinReanim, aPosX, aPosY);
        } else if (mType == CoinType.COIN_DIAMOND) {
            mPosX -= 15.0f;
            mPosY -= 15.0f;

            float aPosX = -3.0f;
            float aPosY = 4.0f;
            Reanimation aCoinReanim = mApp.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_DIAMOND);
            aCoinReanim.setPosition(mPosX + aPosX, mPosY + aPosY);
            aCoinReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
            aCoinReanim.mAnimTime = PvzpCommon.randRangeFloat(0.0f, 0.99f);
            aCoinReanim.mAnimRate = PvzpCommon.randRangeFloat(50.0f, 80.0f);
            Attachment.attachReanim(mAttachmentID, aCoinReanim, aPosX, aPosY);
        }

        if (mApp.isStormyNightLevel()) {
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        }

        if (mType == CoinType.COIN_FINAL_SEED_PACKET) {
            mWidth = mApp.getImage(Resources.IMAGE_SEEDS_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_SEEDS_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_TROPHY) {
            mWidth = mApp.getImage(Resources.IMAGE_TROPHY_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_TROPHY_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_AWARD_SILVER_SUNFLOWER
            || mType == CoinType.COIN_AWARD_GOLD_SUNFLOWER) {
            mWidth = mApp.getImage(Resources.IMAGE_SUNFLOWER_TROPHY_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_SUNFLOWER_TROPHY_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_SHOVEL) {
            mWidth = mApp.getImage(Resources.IMAGE_SHOVEL_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_SHOVEL_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_CARKEYS) {
            mWidth = mApp.getImage(Resources.IMAGE_CARKEYS_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_CARKEYS_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_ALMANAC) {
            mWidth = mApp.getImage(Resources.IMAGE_ALMANAC_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_ALMANAC_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_VASE) {
            mWidth = mApp.getImage(Resources.IMAGE_SCARY_POT_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_SCARY_POT_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_WATERING_CAN) {
            mWidth = mApp.getImage(Resources.IMAGE_WATERINGCAN_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_WATERINGCAN_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_TACO) {
            mWidth = mApp.getImage(Resources.IMAGE_TACO_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_TACO_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_NOTE) {
            mWidth = mApp.getImage(Resources.IMAGE_ZOMBIE_NOTE_SMALL_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_ZOMBIE_NOTE_SMALL_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_USABLE_SEED_PACKET) {
            mWidth = mApp.getImage(Resources.IMAGE_SEEDS_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_SEEDS_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_FOG, 0, 2);
        } else if (mType == CoinType.COIN_PRESENT_PLANT || mType == CoinType.COIN_AWARD_PRESENT) {
            mWidth = mApp.getImage(Resources.IMAGE_PRESENT_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_PRESENT_ID).getCelHeight();
            if (mApp.isSurvivalEndless(mApp.mGameMode) || mApp.isEndlessIZombie(mApp.mGameMode)
                || mApp.isEndlessScaryPotter(mApp.mGameMode)) {
                int aSeedType = mApp.mZenGarden.pickRandomSeedType();
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            } else if (mBoard.mBackground == BackgroundType.BACKGROUND_1_DAY) {
                Integer[] aSeedList = {
                    SeedType.SEED_PEASHOOTER, SeedType.SEED_SUNFLOWER, SeedType.SEED_CHERRYBOMB,
                    SeedType.SEED_WALLNUT, SeedType.SEED_REPEATER, SeedType.SEED_POTATOMINE,
                    SeedType.SEED_SNOWPEA, SeedType.SEED_CHOMPER
                };
                int aSeedType = PvzpCommon.pvzpPickFromArray(aSeedList, aSeedList.length);
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            } else if (mBoard.mBackground == BackgroundType.BACKGROUND_2_NIGHT) {
                Integer[] aSeedList = {
                    SeedType.SEED_PUFFSHROOM, SeedType.SEED_SUNSHROOM, SeedType.SEED_FUMESHROOM,
                    SeedType.SEED_GRAVEBUSTER, SeedType.SEED_HYPNOSHROOM, SeedType.SEED_SCAREDYSHROOM,
                    SeedType.SEED_ICESHROOM, SeedType.SEED_DOOMSHROOM
                };
                int aSeedType = PvzpCommon.pvzpPickFromArray(aSeedList, aSeedList.length);
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            } else if (mBoard.mBackground == BackgroundType.BACKGROUND_3_POOL) {
                Integer[] aSeedList = {
                    SeedType.SEED_LILYPAD, SeedType.SEED_SQUASH, SeedType.SEED_THREEPEATER,
                    SeedType.SEED_TANGLEKELP, SeedType.SEED_JALAPENO, SeedType.SEED_SPIKEWEED,
                    SeedType.SEED_TORCHWOOD, SeedType.SEED_TALLNUT
                };
                int aSeedType = PvzpCommon.pvzpPickFromArray(aSeedList, aSeedList.length);
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            } else if (mBoard.mBackground == BackgroundType.BACKGROUND_4_FOG) {
                Integer[] aSeedList = {
                    SeedType.SEED_SEASHROOM, SeedType.SEED_PLANTERN, SeedType.SEED_CACTUS,
                    SeedType.SEED_BLOVER, SeedType.SEED_SPLITPEA, SeedType.SEED_STARFRUIT,
                    SeedType.SEED_PUMPKINSHELL, SeedType.SEED_MAGNETSHROOM
                };
                int aSeedType = PvzpCommon.pvzpPickFromArray(aSeedList, aSeedList.length);
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            } else if (mBoard.mBackground == BackgroundType.BACKGROUND_5_ROOF) {
                Integer[] aSeedList = {
                    SeedType.SEED_CABBAGEPULT, SeedType.SEED_KERNELPULT, SeedType.SEED_INSTANT_COFFEE,
                    SeedType.SEED_GARLIC, SeedType.SEED_UMBRELLA, SeedType.SEED_MELONPULT
                };
                int aSeedType = PvzpCommon.pvzpPickFromArray(aSeedList, aSeedList.length);
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            } else {
                int aSeedType = mApp.mZenGarden.pickRandomSeedType();
                mPottedPlantSpec.initializePottedPlant(aSeedType);
            }
        } else if (mType == CoinType.COIN_AWARD_MONEY_BAG || mType == CoinType.COIN_AWARD_BAG_DIAMOND) {
            mWidth = mApp.getImage(Resources.IMAGE_MONEYBAG_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_MONEYBAG_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (mType == CoinType.COIN_CHOCOLATE || mType == CoinType.COIN_AWARD_CHOCOLATE) {
            mWidth = mApp.getImage(Resources.IMAGE_CHOCOLATE_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_CHOCOLATE_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        } else if (isPresentWithAdvice()) {
            mWidth = mApp.getImage(Resources.IMAGE_PRESENT_ID).getCelWidth();
            mHeight = mApp.getImage(Resources.IMAGE_PRESENT_ID).getCelHeight();
            mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0);
        }

        switch (mCoinMotion) {
            case CoinMotion.COIN_MOTION_FROM_SKY:
                mVelY = 0.67f;
                mVelX = 0.0f;
                mGroundY = Common.rand(250) + 300;
                break;
            case CoinMotion.COIN_MOTION_FROM_SKY_SLOW:
                mVelY = 0.33f;
                mVelX = 0.0f;
                mGroundY = Common.rand(250) + 300;
                break;
            case CoinMotion.COIN_MOTION_FROM_PLANT:
                mVelY = -1.7f - PvzpCommon.randRangeFloat(0.0f, 1.7f);
                mVelX = -0.4f + PvzpCommon.randRangeFloat(0.0f, 0.8f);
                mGroundY = (int) (mPosY + 15 + Common.rand(20));
                mScale = 0.4f;
                break;
            case CoinMotion.COIN_MOTION_COIN:
                mVelY = -3.0f - PvzpCommon.randRangeFloat(0.0f, 2.0f);
                mVelX = -0.5f + PvzpCommon.randRangeFloat(0.0f, 1.0f);
                mGroundY = (int) (mPosY + 45 + Common.rand(20));
                mGroundY = Math.min(Math.max(mGroundY, 80), 521);
                if (mType == CoinType.COIN_AWARD_SILVER_SUNFLOWER
                    || mType == CoinType.COIN_AWARD_GOLD_SUNFLOWER) {
                    mPosY -= 100.0f;
                    mGroundY = (int) (mPosY + 45.0f);
                    mGroundY = Math.min(mGroundY, 400);
                }
                if (mType == CoinType.COIN_FINAL_SEED_PACKET
                    || mType == CoinType.COIN_USABLE_SEED_PACKET
                    || mType == CoinType.COIN_TROPHY
                    || mType == CoinType.COIN_SHOVEL
                    || mType == CoinType.COIN_CARKEYS
                    || mType == CoinType.COIN_ALMANAC
                    || mType == CoinType.COIN_VASE
                    || mType == CoinType.COIN_WATERING_CAN
                    || mType == CoinType.COIN_TACO
                    || mType == CoinType.COIN_NOTE) {
                    mGroundY -= 30;
                }
                break;
            case CoinMotion.COIN_MOTION_LAWNMOWER_COIN:
                mVelY = 0.0f;
                mVelX = 0.0f;
                mGroundY = 600;
                collect();
                break;
            case CoinMotion.COIN_MOTION_FROM_PRESENT:
                mVelY = 0.0f;
                mVelX = 0.0f;
                mGroundY = 600;
                mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 1);
                break;
            case CoinMotion.COIN_MOTION_FROM_BOSS:
                mVelY = -5.0f;
                mVelX = -3.0f;
                mPosX = 750.0f;
                mPosY = mType == CoinType.COIN_AWARD_SILVER_SUNFLOWER ? 130.0f : 245.0f;
                mGroundY = (int) (mPosY + 40);
                break;
            default:
                break;  // upstream PVZP_ASSERT(false)
        }

        float aScale;
        if (mType == CoinType.COIN_SMALLSUN) {
            aScale = 0.5f;
        } else if (mType == CoinType.COIN_LARGESUN) {
            aScale = 2.0f;
        } else {
            aScale = 1.0f;
        }
        mScale *= aScale;

        if (coinGetsBouncyArrow()) {
            mNeedsBouncyArrow = true;
        }

        if (mCoinMotion != CoinMotion.COIN_MOTION_FROM_PRESENT) {
            playLaunchSound();
        }
    }

    /** Ported from src/Lawn/Coin.cpp:413-416. */
    public static boolean isMoney(int theType) {
        return theType == CoinType.COIN_SILVER || theType == CoinType.COIN_GOLD
            || theType == CoinType.COIN_DIAMOND;
    }

    /** Ported from src/Lawn/Coin.cpp:418-421. */
    public boolean isMoney() {
        return isMoney(mType);
    }

    /** Ported from src/Lawn/Coin.cpp:423-426. */
    public boolean isSun() {
        return mType == CoinType.COIN_SUN || mType == CoinType.COIN_SMALLSUN
            || mType == CoinType.COIN_LARGESUN;
    }

    /** Ported from src/Lawn/Coin.cpp:428-431. */
    public boolean isPresentWithAdvice() {
        return mType == CoinType.COIN_PRESENT_MINIGAMES
            || mType == CoinType.COIN_PRESENT_PUZZLE_MODE
            || mType == CoinType.COIN_PRESENT_SURVIVAL_MODE;
    }

    /** Ported from src/Lawn/Coin.cpp:433-463. */
    public void scoreCoin() {
        die();

        if (isSun()) {
            int aSunValue = getSunValue();
            mBoard.addSunMoney(aSunValue);
        } else if (isMoney()) {
            int aCoinValue = getCoinValue(mType);
            mApp.mPlayerInfo.addCoins(aCoinValue);
            if (mBoard != null) {
                mBoard.mCoinsCollected += aCoinValue;

                if (mType == CoinType.COIN_SILVER || mType == CoinType.COIN_GOLD) {
                    mBoard.mLevelCoinsCollected++;
                    if (mBoard.mLevelCoinsCollected == 30 && mApp.mPlayerInfo.mCoins != 0) {
                        ReportAchievement.giveAchievement(mApp, AchievementId.PennyPincher, true);
                    }
                }
            }
        }

        if (mType == CoinType.COIN_DIAMOND && mBoard != null) {
            mBoard.mDiamondsCollected++;
        }
    }

    /** Ported from src/Lawn/Coin.cpp:464-467. */
    public void startFade() {
        mFadeCount = 15;
    }

    /** Ported from src/Lawn/Coin.cpp:469-480. */
    public void updateFade() {
        if (mApp.isEndlessIZombie(mApp.mGameMode) || mApp.isEndlessScaryPotter(mApp.mGameMode)
            || mType == CoinType.COIN_NOTE || !isLevelAward()) {
            mFadeCount--;
            if (mFadeCount == 0) {
                die();
            }
        }
    }

    /** Ported from src/Lawn/Coin.cpp:481-604. */
    public void updateFall() {
        if (mCoinMotion == CoinMotion.COIN_MOTION_FROM_PRESENT) {
            mPosX += mVelX;
            mPosY += mVelY;
            mVelX *= 0.95f;
            mVelY *= 0.95f;
            if (mCoinAge >= 80) {
                collect();
            }
        } else if (mPosY + mVelY < mGroundY) {
            mPosY += mVelY;
            if (mCoinMotion == CoinMotion.COIN_MOTION_FROM_PLANT) {
                mVelY += 0.09f;
            } else if (mCoinMotion == CoinMotion.COIN_MOTION_COIN
                || mCoinMotion == CoinMotion.COIN_MOTION_FROM_BOSS) {
                mVelY += 0.15f;
            }

            mPosX += mVelX;
            if (mPosX > GameConstants.BOARD_WIDTH - mWidth && mCoinMotion != CoinMotion.COIN_MOTION_FROM_BOSS) {
                mPosX = GameConstants.BOARD_WIDTH - mWidth;
                mVelX = -0.4f - PvzpCommon.randRangeFloat(0.0f, 0.4f);
            } else if (mPosX < 0.0f) {
                mPosX = 0.0f;
                mVelX = 0.4f + PvzpCommon.randRangeFloat(0.0f, 0.4f);
            }
        } else {
            if (mNeedsBouncyArrow && !mHasBouncyArrow) {
                float aParticleOffsetX = mWidth / 2;
                float aParticleOffsetY = mHeight / 2 - 60;
                if (mType == CoinType.COIN_TROPHY) {
                    aParticleOffsetX += 2.0f;
                } else if (mType == CoinType.COIN_AWARD_MONEY_BAG
                    || mType == CoinType.COIN_AWARD_BAG_DIAMOND) {
                    aParticleOffsetX += 2.0f;
                    aParticleOffsetY -= 2.0f;
                } else if (mType == CoinType.COIN_AWARD_PRESENT || isPresentWithAdvice()) {
                    aParticleOffsetY -= 20.0f;
                } else if (mType == CoinType.COIN_AWARD_SILVER_SUNFLOWER
                    || mType == CoinType.COIN_AWARD_GOLD_SUNFLOWER) {
                    aParticleOffsetX -= 6.0f;
                    aParticleOffsetY -= 40.0f;
                } else if (isMoney()) {
                    aParticleOffsetX += 12.0f;
                    aParticleOffsetY += 21.0f;
                }

                int aEffect;
                if (mType == CoinType.COIN_FINAL_SEED_PACKET) {
                    aEffect = ParticleEffect.PARTICLE_SEED_PACKET;
                } else if (isMoney()) {
                    aEffect = ParticleEffect.PARTICLE_COIN_PICKUP_ARROW;
                } else {
                    aEffect = ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW;
                }

                PvzpParticleSystem aParticle = mApp.addPvzpParticle(mPosX + aParticleOffsetX,
                    mPosY + aParticleOffsetY, 0, aEffect);
                Attachment.attachParticle(mAttachmentID, aParticle, aParticleOffsetX, aParticleOffsetY);
                mHasBouncyArrow = true;
            }

            if (!mHitGround) {
                mHitGround = true;
                playGroundSound();
            }

            mPosY = mGroundY;
            mPosX = PvzpCommon.floatRoundToInt(mPosX);

            if (mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_LAST_STAND || mBoard == null
                || mBoard.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT) {
                if (!isLevelAward() && !isPresentWithAdvice()) {
                    mDisappearCounter++;
                    if (mDisappearCounter >= getDisappearTime()) {
                        startFade();
                    }
                }
            }
        }

        if (mCoinMotion == CoinMotion.COIN_MOTION_FROM_PLANT) {
            float aFinalScale = getSunScale();
            if (mScale < aFinalScale) {
                mScale += 0.02f;
            } else {
                mScale = aFinalScale;
            }
        }
    }

    /** Ported from src/Lawn/Coin.cpp:605-729. */
    public void updateCollected() {
        int aDestX = 0;
        int aDestY = 0;
        if (isSun()) {
            aDestX = 15;
            aDestY = 0;
        } else if (isMoney()) {
            aDestX = 39;
            aDestY = 558;

            if (mApp.getDialog(Dialogs.DIALOG_STORE) != null) {
                aDestX = 662;
                aDestY = 546;
            } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mCrazyDaveState != CrazyDaveState.CRAZY_DAVE_OFF) {
                aDestX = 442;
            }
        } else if (isPresentWithAdvice()) {
            aDestX = 35;
            aDestY = 487;
        } else if (mType == CoinType.COIN_AWARD_PRESENT || mType == CoinType.COIN_PRESENT_PLANT) {
            mDisappearCounter++;
            if (mDisappearCounter >= 200) {
                startFade();
            }

            return;
        } else if (!isLevelAward()) {
            if (mType == CoinType.COIN_USABLE_SEED_PACKET) {
                mDisappearCounter++;
            }

            return;
        } else {
            aDestX = 400 - mWidth / 2;
            aDestY = 200 - mHeight / 2;
            mDisappearCounter++;
        }

        if (isLevelAward()) {
            mScale = PvzpCommon.pvzpAnimateCurveFloat(0, 400, mDisappearCounter, 1.01f, 2.0f,
                PvzpCurves.CURVE_EASE_IN_OUT);
            mPosX = PvzpCommon.pvzpAnimateCurveFloat(0, 350, mDisappearCounter, mCollectX, aDestX,
                PvzpCurves.CURVE_EASE_OUT);
            mPosY = PvzpCommon.pvzpAnimateCurveFloat(0, 350, mDisappearCounter, mCollectY, aDestY,
                PvzpCurves.CURVE_EASE_OUT);
            return;
        }

        float aDeltaX = Math.abs(mPosX - aDestX);
        float aDeltaY = Math.abs(mPosY - aDestY);
        if (mPosX > aDestX) {
            mPosX -= aDeltaX / 21.0f;
        } else if (mPosX < aDestX) {
            mPosX += aDeltaX / 21.0f;
        }
        if (mPosY > aDestY) {
            mPosY -= aDeltaY / 21.0f;
        } else if (mPosY < aDestY) {
            mPosY += aDeltaY / 21.0f;
        }

        mCollectionDistance = (float) Math.sqrt(aDeltaY * aDeltaY + aDeltaX * aDeltaX);

        if (isPresentWithAdvice()) {
            if (mCollectionDistance < 15.0f) {
                if (!mBoard.mHelpDisplayed[AdviceType.ADVICE_UNLOCKED_MODE]) {
                    if (mType == CoinType.COIN_PRESENT_MINIGAMES) {
                        mBoard.displayAdvice("[UNLOCKED_MINIGAMES]", MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE,
                            AdviceType.ADVICE_UNLOCKED_MODE);
                    } else if (mType == CoinType.COIN_PRESENT_PUZZLE_MODE) {
                        mBoard.displayAdvice("[UNLOCKED_PUZZLE_MODE]", MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE,
                            AdviceType.ADVICE_UNLOCKED_MODE);
                    } else {
                        mBoard.displayAdvice("[UNLOCKED_SURVIVAL_MODE]", MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE,
                            AdviceType.ADVICE_UNLOCKED_MODE);
                    }
                } else if (mBoard.mHelpIndex != AdviceType.ADVICE_UNLOCKED_MODE
                    || mBoard.mAdvice.mDuration == 0) {  // MessageWidget::IsBeingDisplayed (MessageWidget.h:65)
                    die();
                }
            }
        } else {
            float aScoringDistance = 8.0f;
            if (isMoney()) {
                aScoringDistance = 12.0f;
            }

            if (mCollectionDistance < aScoringDistance) {
                scoreCoin();
            }

            mScale = Math.min(Math.max(mCollectionDistance * 0.05f, 0.5f), 1.0f);
            mScale *= getSunScale();
        }
    }

    /** Ported from src/Lawn/Coin.cpp:730-770. */
    public void update() {
        mCoinAge++;
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && mApp.mGameScene != GameScenes.SCENE_AWARD
            && mBoard != null && !mBoard.mCutScene.shouldRunUpsellBoard()) {
            return;
        }

        if (mFadeCount != 0) {
            updateFade();
        } else if (!mIsBeingCollected) {
            updateFall();
        } else {
            updateCollected();
        }

        if (mAttachmentID.value != AttachmentID.ATTACHMENTID_NULL) {
            float aOffsetX = 0.0f;
            float aOffsetY = 0.0f;
            if (mType == CoinType.COIN_DIAMOND) {
                aOffsetX = 18.0f - 18.0f * mScale;
                aOffsetY = 13.0f - 13.0f * mScale;
            }

            Attachment.attachmentUpdateAndMove(mAttachmentID, mPosX + aOffsetX, mPosY + aOffsetY);
            Attachment.attachmentOverrideColor(mAttachmentID, getColor());
            Attachment.attachmentOverrideScale(mAttachmentID, mScale);

            if ((!mHitGround || mIsBeingCollected)
                && (mType == CoinType.COIN_SILVER || mType == CoinType.COIN_GOLD)) {
                // moving silver/gold coins use a static image, so hide the attachment's animation
                Attachment.attachmentOverrideColor(mAttachmentID, new Color(0, 0, 0, 0));
            }
        }
    }

    /** Ported from src/Lawn/Coin.cpp:771-786. */
    public Color getColor() {
        if ((isSun() || isMoney()) && mIsBeingCollected) {
            float aAlpha = Math.min(Math.max(mCollectionDistance * 0.035f, 0.35f), 1.0f) * 255.0f;
            return new Color(255, 255, 255, (int) aAlpha);
        }

        if (mFadeCount > 0) {
            int aAlpha = PvzpCommon.pvzpAnimateCurve(15, 0, mFadeCount, 255, 0, PvzpCurves.CURVE_LINEAR);
            return new Color(255, 255, 255, aAlpha);
        }

        return Color.White;
    }

    /** Ported from src/Lawn/Coin.cpp:788-795. */
    public int getFinalSeedPacketType() {
        if (mApp.isFirstTimeAdventureMode() && mBoard != null && mBoard.mLevel <= 50) {
            return mApp.getAwardSeedForLevel(mBoard.mLevel);
        }

        return SeedType.SEED_NONE;
    }

    /** Ported from src/Lawn/Coin.cpp:797-1004. */
    public void draw(Graphics g) {
        g.setColor(getColor());
        if (mType == CoinType.COIN_DIAMOND) {
            g.setColorizeImages(true);
            g.drawImageF(mApp.getImage(Resources.IMAGE_AWARDPICKUPGLOW_ID), mPosX - 56.0f, mPosY - 66.0f);
            g.setColorizeImages(false);
        }
        if (mType == CoinType.COIN_PRESENT_PLANT) {
            g.setColorizeImages(true);
            g.drawImageF(mApp.getImage(Resources.IMAGE_AWARDPICKUPGLOW_ID), mPosX - 50.0f, mPosY - 64.0f);
            g.setColorizeImages(false);
        }
        if (mType == CoinType.COIN_AWARD_PRESENT && mIsBeingCollected) {
            g.setColorizeImages(true);
            g.drawImageF(mApp.getImage(Resources.IMAGE_AWARDPICKUPGLOW_ID), mPosX - 50.0f, mPosY - 64.0f);
            g.setColorizeImages(false);
        }
        if (mType == CoinType.COIN_CHOCOLATE || mType == CoinType.COIN_AWARD_CHOCOLATE) {
            g.setColorizeImages(true);
            g.drawImageF(mApp.getImage(Resources.IMAGE_AWARDPICKUPGLOW_ID), mPosX - 56.0f, mPosY - 50.0f);
            g.setColorizeImages(false);
        }

        if (mAttachmentID.value != AttachmentID.ATTACHMENTID_NULL) {
            Graphics theAttachmentGraphics = new Graphics(g);
            makeParentGraphicsFrame(theAttachmentGraphics);
            Attachment.attachmentDraw(mAttachmentID, theAttachmentGraphics, false);
        }

        if ((mType == CoinType.COIN_SILVER || mType == CoinType.COIN_GOLD) && mHitGround && !mIsBeingCollected) {
            return;
        }

        if (mType == CoinType.COIN_DIAMOND) {
            return;
        }

        if (isLevelAward() && !mIsBeingCollected) {
            Color aFlashingColor = PvzpCommon.getFlashingColor(mCoinAge, 75);
            g.setColor(aFlashingColor);
        }

        if (mType == CoinType.COIN_SILVER || mType == CoinType.COIN_GOLD) {
            g.setColorizeImages(true);
            PvzpCommon.pvzpDrawImageCenterScaledF(g, mApp.getImage(Resources.IMAGE_REANIM_COINGLOW_ID), mPosX - 14.0f,
                mPosY - 12.0f, mScale, mScale);
            g.setColorizeImages(false);
        }

        Image aImage = null;
        int aImageCelCol = 0;
        float aDrawScale = mScale;
        float aOffsetX = 0.0f;
        float aOffsetY = 0.0f;
        if (mType == CoinType.COIN_SILVER) {
            aImage = mApp.getImage(Resources.IMAGE_REANIM_COIN_SILVER_DOLLAR_ID);
            aOffsetX = 8.0f;
            aOffsetY = 10.0f;
        } else if (mType == CoinType.COIN_GOLD) {
            aImage = mApp.getImage(Resources.IMAGE_REANIM_COIN_GOLD_DOLLAR_ID);
            aOffsetX = 8.0f;
            aOffsetY = 10.0f;
        } else if (isSun()) {
            return;
        } else if (mType == CoinType.COIN_FINAL_SEED_PACKET) {
            int aSeedType = getFinalSeedPacketType();
            g.setScale(mScale, mScale, 0.0f, 0.0f);
            SeedPacket.drawSeedPacket(g, 0.5f * (mWidth - mScale * mWidth) + mPosX,
                0.5f * (mHeight - mScale * mHeight) + mPosY, aSeedType, SeedType.SEED_NONE, 0.0f, 255, true, false);
            g.setScale(1.0f, 1.0f, 0.0f, 0.0f);
            return;
        } else if (mType == CoinType.COIN_PRESENT_PLANT || mType == CoinType.COIN_AWARD_PRESENT) {
            if (mIsBeingCollected) {
                mApp.mZenGarden.drawPottedPlantIcon(g, mPosX + 10.0f, mPosY - 20.0f, mPottedPlantSpec);
                return;
            }

            aImage = mApp.getImage(Resources.IMAGE_PRESENT_ID);
            aOffsetY = -20.0f;
        } else if (isPresentWithAdvice()) {
            aOffsetY = -20.0f;

            if (mIsBeingCollected) {
                aOffsetX = -10.0f;
                aOffsetY -= -10.0f;
                aImage = mApp.getImage(Resources.IMAGE_PRESENTOPEN_ID);
            } else {
                aImage = mApp.getImage(Resources.IMAGE_PRESENT_ID);
            }
        } else if (mType == CoinType.COIN_AWARD_MONEY_BAG || mType == CoinType.COIN_AWARD_BAG_DIAMOND) {
            aImage = mApp.getImage(Resources.IMAGE_MONEYBAG_HI_RES_ID);
            aOffsetX -= mWidth / 2;
            aOffsetY -= mHeight / 2;
            aDrawScale *= 0.5f;
        } else if (mType == CoinType.COIN_CHOCOLATE || mType == CoinType.COIN_AWARD_CHOCOLATE) {
            aImage = mApp.getImage(Resources.IMAGE_CHOCOLATE_ID);
        } else if (mType == CoinType.COIN_TROPHY) {
            aImage = mApp.getImage(Resources.IMAGE_TROPHY_HI_RES_ID);
            aOffsetX -= mWidth / 2;
            aOffsetY -= mHeight / 2;
            aDrawScale *= 0.5f;
        } else if (mType == CoinType.COIN_AWARD_SILVER_SUNFLOWER) {
            aImage = mApp.getImage(Resources.IMAGE_SUNFLOWER_TROPHY_ID);
            aOffsetX -= 5.0f;
            aDrawScale *= 0.6f;
        } else if (mType == CoinType.COIN_AWARD_GOLD_SUNFLOWER) {
            aImage = mApp.getImage(Resources.IMAGE_SUNFLOWER_TROPHY_ID);
            aImageCelCol = 1;
            aOffsetX -= 5.0f;
            aDrawScale *= 0.6f;
        } else if (mType == CoinType.COIN_SHOVEL) {
            aImage = mApp.getImage(Resources.IMAGE_SHOVEL_HI_RES_ID);
            aOffsetX -= 20.0f;
            aOffsetY -= 20.0f;
            aDrawScale *= 0.5f;
        } else if (mType == CoinType.COIN_CARKEYS) {
            aImage = mApp.getImage(Resources.IMAGE_CARKEYS_ID);
        } else if (mType == CoinType.COIN_ALMANAC) {
            aImage = mApp.getImage(Resources.IMAGE_ALMANAC_ID);
        } else if (mType == CoinType.COIN_TACO) {
            aImage = mApp.getImage(Resources.IMAGE_TACO_ID);
        } else if (mType == CoinType.COIN_VASE) {
            aImage = mApp.getImage(Resources.IMAGE_SCARY_POT_ID);
        } else if (mType == CoinType.COIN_WATERING_CAN) {
            aImage = mApp.getImage(Resources.IMAGE_WATERINGCAN_ID);
        } else if (mType == CoinType.COIN_NOTE) {
            aImage = mApp.getImage(Resources.IMAGE_ZOMBIE_NOTE_SMALL_ID);
        } else if (mType == CoinType.COIN_USABLE_SEED_PACKET) {
            int aGrayness = 255;
            if (mIsBeingCollected) {
                aGrayness = 128;
            } else {
                int aDisappearTime = getDisappearTime();
                if (mDisappearCounter > aDisappearTime - 300 && mDisappearCounter % 60 < 30) {
                    aGrayness = 192;
                }
            }

            g.setColorizeImages(true);
            SeedPacket.drawSeedPacket(g, (int) mPosX, (int) mPosY, mUsableSeedType, SeedType.SEED_NONE, 0.0f,
                aGrayness, false, false);
            g.setColorizeImages(false);

            return;
        } else {
            return;  // upstream PVZP_ASSERT(false)
        }

        g.setColorizeImages(true);
        PvzpCommon.pvzpDrawImageCelCenterScaledF(g, aImage, mPosX + aOffsetX, mPosY + aOffsetY, aImageCelCol,
            aDrawScale, aDrawScale);
        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Coin.cpp:1006-1019. */
    public void fanOutCoins(int theCoinType, int theNumCoins) {
        for (int i = 0; i < theNumCoins; i++) {
            double aAngle = GameConstants.PI / 2 + GameConstants.PI * (i + 1) / (theNumCoins + 1);
            float aPosX = mPosX + 20.0f;
            float aPosY = mPosY;
            Coin aCoin = mBoard.addCoin((int) aPosX, (int) aPosY, theCoinType, CoinMotion.COIN_MOTION_FROM_PRESENT);
            aCoin.mVelX = (float) (5.0 * Math.sin(aAngle));
            aCoin.mVelY = (float) (5.0 * Math.cos(aAngle));
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1021-1042. */
    public void tryAutoCollectAfterLevelAward() {
        boolean aCanBeAutoCollected = false;
        if (isMoney() && mCoinMotion != CoinMotion.COIN_MOTION_FROM_PRESENT) {
            aCanBeAutoCollected = true;
        }
        if (isSun()) {
            aCanBeAutoCollected = true;
        }
        if (mType == CoinType.COIN_PRESENT_PLANT || mType == CoinType.COIN_CHOCOLATE
            || isPresentWithAdvice()) {
            aCanBeAutoCollected = true;
        }

        if (aCanBeAutoCollected) {
            playCollectSound();
            collect();
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1044-1290. */
    public void collect() {
        if (mDead) {
            return;
        }

        mCollectX = mPosX;
        mCollectY = mPosY;
        mIsBeingCollected = true;

        boolean aIsEndlessAward = false;
        if ((mApp.isEndlessIZombie(mApp.mGameMode) || mApp.isEndlessScaryPotter(mApp.mGameMode))
            && isLevelAward()) {
            aIsEndlessAward = true;
        }

        if (mType == CoinType.COIN_PRESENT_PLANT || mType == CoinType.COIN_AWARD_PRESENT) {
            if (mApp.mZenGarden.isZenGardenFull(false)) {
                mBoard.displayAdvice("[DIALOG_ZEN_GARDEN_FULL]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                    AdviceType.ADVICE_NONE);
            } else {
                mBoard.mPottedPlantsCollected++;
                mBoard.displayAdvice("[ADVICE_FOUND_PLANT]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                    AdviceType.ADVICE_NONE);
                mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                    ParticleEffect.PARTICLE_PRESENT_PICKUP);
                mApp.mZenGarden.addPottedPlant(mPottedPlantSpec);
            }

            mDisappearCounter = 0;
            mFadeCount = 0;
            if (aIsEndlessAward) {
                Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                    ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, null);
                mBoard.fadeOutLevel();
            }

            return;
        }

        if (mType == CoinType.COIN_PRESENT_MINIGAMES) {
            mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                ParticleEffect.PARTICLE_PRESENT_PICKUP);

            mDisappearCounter = 0;
            mFadeCount = 0;

            Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, null);
            mApp.mPlayerInfo.mHasUnlockedMinigames = 1;

            return;
        }
        if (mType == CoinType.COIN_PRESENT_PUZZLE_MODE) {
            mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                ParticleEffect.PARTICLE_PRESENT_PICKUP);

            mDisappearCounter = 0;
            mFadeCount = 0;

            Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, null);
            mApp.mPlayerInfo.mHasUnlockedPuzzleMode = 1;

            return;
        }
        if (mType == CoinType.COIN_PRESENT_SURVIVAL_MODE) {
            mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                ParticleEffect.PARTICLE_PRESENT_PICKUP);

            mDisappearCounter = 0;
            mFadeCount = 0;

            Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, null);
            mApp.mPlayerInfo.mHasUnlockedSurvivalMode = 1;

            return;
        }

        if (mType == CoinType.COIN_CHOCOLATE || mType == CoinType.COIN_AWARD_CHOCOLATE) {
            mBoard.mChocolateCollected++;
            mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                ParticleEffect.PARTICLE_PRESENT_PICKUP);

            if (mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] < PlayerInfo.PURCHASE_COUNT_OFFSET) {
                mBoard.displayAdvice("[ADVICE_FOUND_CHOCOLATE]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                    AdviceType.ADVICE_NONE);
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET + 1;
            } else {
                mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE]++;
            }

            mDisappearCounter = 0;
            startFade();

            if (aIsEndlessAward) {
                Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                    ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, null);
                mBoard.fadeOutLevel();
            }

            return;
        }

        if (isLevelAward()) {
            if (aIsEndlessAward) {
                if (mType == CoinType.COIN_AWARD_BAG_DIAMOND) {
                    mApp.playSample(Resources.SOUND_DIAMOND_ID);
                    fanOutCoins(CoinType.COIN_DIAMOND, 1);
                    startFade();
                } else if (mType == CoinType.COIN_AWARD_MONEY_BAG) {
                    mApp.playFoley(FoleyType.FOLEY_COIN);
                    fanOutCoins(CoinType.COIN_GOLD, 5);
                    startFade();
                }
            } else if (mApp.isScaryPotterLevel()) {
                if (mType == CoinType.COIN_TROPHY) {
                    mApp.playFoley(FoleyType.FOLEY_COIN);
                    fanOutCoins(CoinType.COIN_GOLD, 5);
                } else if (mType == CoinType.COIN_AWARD_MONEY_BAG) {
                    mApp.playFoley(FoleyType.FOLEY_COIN);
                    fanOutCoins(CoinType.COIN_GOLD, 2);
                }
            } else if (mApp.isAdventureMode() && mBoard.mLevel == 50) {
                fanOutCoins(CoinType.COIN_DIAMOND, 3);
            } else if (mType == CoinType.COIN_AWARD_GOLD_SUNFLOWER) {
                fanOutCoins(CoinType.COIN_DIAMOND, 5);
            } else if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 4) {
                mApp.playSample(Resources.SOUND_SHOVEL_ID);
            } else if (mApp.isFirstTimeAdventureMode()
                && (mBoard.mLevel == 24 || mBoard.mLevel == 34 || mBoard.mLevel == 44)) {
                mApp.playSample(Resources.SOUND_TAP2_ID);
            } else if (mType == CoinType.COIN_TROPHY) {
                mApp.playSample(Resources.SOUND_DIAMOND_ID);
                fanOutCoins(CoinType.COIN_DIAMOND, 1);
            } else if (mType == CoinType.COIN_AWARD_MONEY_BAG) {
                mApp.playFoley(FoleyType.FOLEY_COIN);
                fanOutCoins(CoinType.COIN_GOLD, 5);
            } else {
                mApp.playSample(Resources.SOUND_SEEDLIFT_ID);
                mApp.playSample(Resources.SOUND_TAP2_ID);
            }

            mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                ParticleEffect.PARTICLE_STARBURST);

            mBoard.fadeOutLevel();
            Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                ParticleEffect.PARTICLE_SEED_PACKET, null);
            Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, null);
            Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
                ParticleEffect.PARTICLE_COIN_PICKUP_ARROW, null);

            if (mType == CoinType.COIN_NOTE) {
                mApp.addPvzpParticle(mPosX + 30.0f, mPosY + 30.0f, mRenderOrder + 1,
                    ParticleEffect.PARTICLE_PRESENT_PICKUP);
                startFade();
            } else if (!aIsEndlessAward && mApp.is3DAccelerated()) {
                float aParticleOffsetX = mWidth / 2;
                float aParticleOffsetY = mHeight / 2;
                PvzpParticleSystem aParticle = mApp.addPvzpParticle(mPosX + aParticleOffsetX,
                    mPosY + aParticleOffsetY, mRenderOrder - 1, ParticleEffect.PARTICLE_SEED_PACKET_PICKUP);
                Attachment.attachParticle(mAttachmentID, aParticle, aParticleOffsetX, aParticleOffsetY);
            }

            mDisappearCounter = 0;
            return;
        }

        if (mType == CoinType.COIN_USABLE_SEED_PACKET) {
            mBoard.mCursorObject.mType = mUsableSeedType;
            mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_USABLE_COIN;
            mBoard.mCursorObject.mCoinID = mBoard.mCoins.dataArrayGetID(this);

            mGroundY = (int) mPosY;
            mFadeCount = 0;
            return;
        }

        if (isMoney() && mBoard != null) {
            mBoard.showCoinBank();
        }

        mFadeCount = 0;

        if (isSun() && mBoard != null && !mBoard.hasConveyorBeltSeedBank()) {
            for (int i = 0; i < mBoard.mSeedBank.mNumPackets; i++) {
                SeedPacket aSeedPacket = mBoard.mSeedBank.mSeedPackets[i];
                int aCost = mBoard.getCurrentPlantCost(aSeedPacket.mPacketType, aSeedPacket.mImitaterType);
                int aSunProfit = mBoard.mSunMoney + mBoard.countSunBeingCollected() - aCost;
                if (aSunProfit >= 0 && aSunProfit < getSunValue()) {
                    aSeedPacket.flashIfReady();
                }
            }

            if (mBoard.stageHasFog()) {
                mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 2);
            }
        }

        Attachment.attachmentDetachCrossFadeParticleType(mAttachmentID,
            ParticleEffect.PARTICLE_COIN_PICKUP_ARROW, null);
        if (mApp.isFirstTimeAdventureMode() && mBoard != null && mBoard.mLevel == 11
            && (mType == CoinType.COIN_GOLD || mType == CoinType.COIN_SILVER)) {
            mBoard.displayAdvice("[ADVICE_CLICKED_ON_COIN]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                AdviceType.ADVICE_CLICKED_ON_COIN);
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1292-1295. */
    public float getSunScale() {
        return mType == CoinType.COIN_SMALLSUN ? 0.5f
            : mType == CoinType.COIN_LARGESUN ? 2.0f : 1.0f;
    }

    /** Ported from src/Lawn/Coin.cpp:1297-1300. */
    public int getSunValue() {
        return mType == CoinType.COIN_SUN ? 25
            : mType == CoinType.COIN_SMALLSUN ? 15
            : mType == CoinType.COIN_LARGESUN ? 50 : 0;
    }

    /** Ported from src/Lawn/Coin.cpp:1302-1305. */
    public static int getCoinValue(int theCoinType) {
        return theCoinType == CoinType.COIN_SILVER ? 1
            : theCoinType == CoinType.COIN_GOLD ? 5
            : theCoinType == CoinType.COIN_DIAMOND ? 100 : 0;
    }

    /** Ported from src/Lawn/Coin.cpp:1307-1319. */
    public void playLaunchSound() {
        if (mType == CoinType.COIN_DIAMOND
            || mType == CoinType.COIN_CHOCOLATE
            || mType == CoinType.COIN_AWARD_CHOCOLATE
            || mType == CoinType.COIN_PRESENT_PLANT
            || mType == CoinType.COIN_AWARD_PRESENT
            || isPresentWithAdvice()) {
            mApp.playFoley(FoleyType.FOLEY_CHIME);
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1321-1328. */
    public void playGroundSound() {
        if (mType == CoinType.COIN_GOLD) {
            mApp.playFoley(FoleyType.FOLEY_MONEYFALLS);
            return;
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1330-1369. */
    public void playCollectSound() {
        if (mType == CoinType.COIN_USABLE_SEED_PACKET) {
            mApp.playSample(Resources.SOUND_SEEDLIFT_ID);
            return;
        }

        if (mType == CoinType.COIN_SILVER || mType == CoinType.COIN_GOLD) {
            mApp.playFoley(FoleyType.FOLEY_COIN);
            return;
        }

        if (mType == CoinType.COIN_DIAMOND) {
            mApp.playSample(Resources.SOUND_DIAMOND_ID);
            return;
        }

        if (isSun()) {
            mApp.playFoley(FoleyType.FOLEY_SUN);
            return;
        }

        if (mType == CoinType.COIN_CHOCOLATE
            || mType == CoinType.COIN_PRESENT_PLANT
            || isPresentWithAdvice()
            || mType == CoinType.COIN_AWARD_PRESENT
            || mType == CoinType.COIN_AWARD_CHOCOLATE) {
            mApp.playFoley(FoleyType.FOLEY_PRIZE);
            return;
        }

        if (isSun()) {
            mApp.playFoley(FoleyType.FOLEY_SUN);
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1371-1381. */
    public void droppedUsableSeed() {
        mIsBeingCollected = false;

        if (mTimesDropped == 0) {
            mDisappearCounter = Math.min(mDisappearCounter, 1200);
        }

        mTimesDropped++;
    }

    /** Ported from src/Lawn/Coin.cpp:1383-1400. */
    public void mouseDown(int x, int y, int theClickCount) {
        if (mBoard == null || mBoard.mPaused || mApp.mGameScene != GameScenes.SCENE_PLAYING || mDead) {
            return;
        }

        if (theClickCount >= 0 && !mIsBeingCollected) {
            playCollectSound();
            collect();

            if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 1) {
                mBoard.displayAdvice("[ADVICE_CLICKED_ON_SUN]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                    AdviceType.ADVICE_CLICKED_ON_SUN);
            }
        }
    }

    /** Ported from src/Lawn/Coin.cpp:1402-1408. */
    public void die() {
        mDead = true;
        Attachment.attachmentDie(mAttachmentID);
    }

    /** Ported from src/Lawn/Coin.cpp:1410-1457. */
    public boolean mouseHitTest(int theX, int theY, HitResult theHitResult) {
        int aOffsetY = 0;
        if (mType == CoinType.COIN_PRESENT_PLANT || mType == CoinType.COIN_AWARD_PRESENT
            || isPresentWithAdvice()) {
            aOffsetY = -20;
        }

        int aExtraClickSize = 0;
        int aExtraClickHeight = 0;
        if (mApp.isWhackAZombieLevel()) {
            aExtraClickHeight = 30;
            aExtraClickSize = 15;
        }
        if (mType == CoinType.COIN_SUN) {
            aExtraClickSize = 15;
        }

        boolean aCanHitCoin = true;
        if (mDead || mIsBeingCollected) {
            aCanHitCoin = false;
        }
        if (mType == CoinType.COIN_USABLE_SEED_PACKET && mBoard != null) {
            if (mBoard.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_NORMAL
                && !mApp.isWhackAZombieLevel()) {
                aCanHitCoin = false;
            }
        }

        if (aCanHitCoin
            && theX >= mPosX - aExtraClickSize
            && theX < mPosX + mWidth + aExtraClickSize
            && theY >= mPosY + aOffsetY - aExtraClickSize
            && theY < mPosY + mHeight + aOffsetY + aExtraClickSize + aExtraClickHeight) {
            theHitResult.mObject = this;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_COIN;
            return true;
        }

        theHitResult.mObject = null;
        theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
        return false;
    }

    /** Ported from src/Lawn/Coin.cpp:1459-1477. */
    public boolean isLevelAward() {
        return mType == CoinType.COIN_FINAL_SEED_PACKET
            || mType == CoinType.COIN_TROPHY
            || mType == CoinType.COIN_AWARD_SILVER_SUNFLOWER
            || mType == CoinType.COIN_AWARD_GOLD_SUNFLOWER
            || mType == CoinType.COIN_SHOVEL
            || mType == CoinType.COIN_CARKEYS
            || mType == CoinType.COIN_ALMANAC
            || mType == CoinType.COIN_VASE
            || mType == CoinType.COIN_WATERING_CAN
            || mType == CoinType.COIN_TACO
            || mType == CoinType.COIN_NOTE
            || mType == CoinType.COIN_AWARD_MONEY_BAG
            || mType == CoinType.COIN_AWARD_BAG_DIAMOND
            || mType == CoinType.COIN_AWARD_PRESENT
            || mType == CoinType.COIN_AWARD_CHOCOLATE;
    }

    /** Ported from src/Lawn/Coin.cpp:1479-1493. */
    public boolean coinGetsBouncyArrow() {
        if (isLevelAward()) {
            return true;
        }

        if (mType == CoinType.COIN_SILVER || mType == CoinType.COIN_GOLD) {
            if (mApp.isFirstTimeAdventureMode() && mBoard != null && mBoard.mLevel == 11
                && !mBoard.mDroppedFirstCoin) {
                return true;
            }
        }

        return isPresentWithAdvice();
    }

    /** Ported from src/Lawn/Coin.h:84. */
    public boolean isOnGround() {
        return false;
    }

    /** Ported from src/Lawn/Coin.cpp:1495-1516. */
    public int getDisappearTime() {
        int aTime = 750;

        if (mType == CoinType.COIN_DIAMOND || mType == CoinType.COIN_PRESENT_PLANT
            || mType == CoinType.COIN_CHOCOLATE || mHasBouncyArrow) {
            aTime = 1500;
        }

        if ((mApp.isScaryPotterLevel() || mApp.isSlotMachineLevel())
            && mType == CoinType.COIN_USABLE_SEED_PACKET) {
            aTime = 1500;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            aTime = 6000;
        }

        return aTime;
    }
}
