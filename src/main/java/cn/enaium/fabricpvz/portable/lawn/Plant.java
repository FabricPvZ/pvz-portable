/*
 * Ported from src/Lawn/Plant.h and src/Lawn/Plant.cpp.
 *
 * The per-line port keeps upstream member/method names (camelCase methods, `m`-prefixed public
 * fields) and the upstream line ordering so it can be diffed against the C++ source. Tick units
 * are upstream ticks (1 tick = 10 ms, docs/research/03-plant.md §"Tick unit").
 */
package cn.enaium.fabricpvz.portable.lawn;

import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.DEG_TO_RAD;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.RAD_TO_DEG;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.distance2D;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.getFlashingColor;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.pvzpAnimateCurve;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.pvzpAnimateCurveFloat;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.pvzpAnimateCurveFloatTime;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.randRangeFloat;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.randRangeInt;
import static cn.enaium.fabricpvz.portable.pvzp.PvzpCommon.testBit;

import java.util.Arrays;

import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTransform;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public class Plant extends GameObject {
    /** Plant.h:28. */
    public static final int MAX_MAGNET_ITEMS = 5;

    /**
     * {@code cause} codes for {@link cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks#onPlantRemoved}
     * (docs/research/03-board-core.md §7.4: "shovel/eaten/crater"). Upstream has no such enum;
     * this port defines the reason codes here because Plant.cpp is where the hook is fired.
     */
    public static final int PLANT_REMOVED_OTHER = 0;
    public static final int PLANT_REMOVED_SHOVEL = 1;
    public static final int PLANT_REMOVED_EATEN = 2;
    public static final int PLANT_REMOVED_EXPLODED = 3;

    /**
     * Plant.cpp:48-106 — the plant definition table, index == SeedType; upstream rows 0 to
     * {@link SeedType#NUM_SEED_TYPES} {@code - 1} verbatim.
     *
     * <p>Same table, but as wide as the seed-type id space ({@link SeedType#MAX_SEED_TYPES}): seed
     * type {@code n} always has its row {@code n}, the retail rows below {@code NUM_SEED_TYPES} are
     * filled in here, and every row at or above them is empty ({@code null}) until a definition is
     * written into it. A {@code null} row is a seed type nothing defines, which is what
     * {@link #getPlantDefinition(int)} answers for it and what the loops walking the table skip, so
     * the table is readable before anything has been registered and a game that registers nothing
     * behaves exactly like the retail one.
     *
     * <p>Writes ({@code gPlantDefs[n] = ...}) happen before the game reads the table - the readers are
     * the game's own classes, so they are only ever reached after the process has decided what its
     * seed types are - and reads happen on the game thread afterwards. An entry is expected to be
     * index-aligned ({@code def.mSeedType == n}) and is treated as immutable once written.
     */
    public static final PlantDefinition[] gPlantDefs = Arrays.copyOf(new PlantDefinition[] {
        new PlantDefinition(SeedType.SEED_PEASHOOTER, null, ReanimationType.REANIM_PEASHOOTER, 0, 100, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "PEASHOOTER"),
        new PlantDefinition(SeedType.SEED_SUNFLOWER, null, ReanimationType.REANIM_SUNFLOWER, 1, 50, 750, PlantSubClass.SUBCLASS_NORMAL, 2500, "SUNFLOWER"),
        new PlantDefinition(SeedType.SEED_CHERRYBOMB, null, ReanimationType.REANIM_CHERRYBOMB, 3, 150, 5000, PlantSubClass.SUBCLASS_NORMAL, 0, "CHERRY_BOMB"),
        new PlantDefinition(SeedType.SEED_WALLNUT, null, ReanimationType.REANIM_WALLNUT, 2, 50, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "WALL_NUT"),
        new PlantDefinition(SeedType.SEED_POTATOMINE, null, ReanimationType.REANIM_POTATOMINE, 37, 25, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "POTATO_MINE"),
        new PlantDefinition(SeedType.SEED_SNOWPEA, null, ReanimationType.REANIM_SNOWPEA, 4, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "SNOW_PEA"),
        new PlantDefinition(SeedType.SEED_CHOMPER, null, ReanimationType.REANIM_CHOMPER, 31, 150, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "CHOMPER"),
        new PlantDefinition(SeedType.SEED_REPEATER, null, ReanimationType.REANIM_REPEATER, 5, 200, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "REPEATER"),
        new PlantDefinition(SeedType.SEED_PUFFSHROOM, null, ReanimationType.REANIM_PUFFSHROOM, 6, 0, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "PUFF_SHROOM"),
        new PlantDefinition(SeedType.SEED_SUNSHROOM, null, ReanimationType.REANIM_SUNSHROOM, 7, 25, 750, PlantSubClass.SUBCLASS_NORMAL, 2500, "SUN_SHROOM"),
        new PlantDefinition(SeedType.SEED_FUMESHROOM, null, ReanimationType.REANIM_FUMESHROOM, 9, 75, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "FUME_SHROOM"),
        new PlantDefinition(SeedType.SEED_GRAVEBUSTER, null, ReanimationType.REANIM_GRAVE_BUSTER, 40, 75, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "GRAVE_BUSTER"),
        new PlantDefinition(SeedType.SEED_HYPNOSHROOM, null, ReanimationType.REANIM_HYPNOSHROOM, 10, 75, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "HYPNO_SHROOM"),
        new PlantDefinition(SeedType.SEED_SCAREDYSHROOM, null, ReanimationType.REANIM_SCRAREYSHROOM, 33, 25, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "SCAREDY_SHROOM"),
        new PlantDefinition(SeedType.SEED_ICESHROOM, null, ReanimationType.REANIM_ICESHROOM, 36, 75, 5000, PlantSubClass.SUBCLASS_NORMAL, 0, "ICE_SHROOM"),
        new PlantDefinition(SeedType.SEED_DOOMSHROOM, null, ReanimationType.REANIM_DOOMSHROOM, 20, 125, 5000, PlantSubClass.SUBCLASS_NORMAL, 0, "DOOM_SHROOM"),
        new PlantDefinition(SeedType.SEED_LILYPAD, null, ReanimationType.REANIM_LILYPAD, 19, 25, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "LILY_PAD"),
        new PlantDefinition(SeedType.SEED_SQUASH, null, ReanimationType.REANIM_SQUASH, 21, 50, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "SQUASH"),
        new PlantDefinition(SeedType.SEED_THREEPEATER, null, ReanimationType.REANIM_THREEPEATER, 12, 325, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "THREEPEATER"),
        new PlantDefinition(SeedType.SEED_TANGLEKELP, null, ReanimationType.REANIM_TANGLEKELP, 17, 25, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "TANGLE_KELP"),
        new PlantDefinition(SeedType.SEED_JALAPENO, null, ReanimationType.REANIM_JALAPENO, 11, 125, 5000, PlantSubClass.SUBCLASS_NORMAL, 0, "JALAPENO"),
        new PlantDefinition(SeedType.SEED_SPIKEWEED, null, ReanimationType.REANIM_SPIKEWEED, 22, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "SPIKEWEED"),
        new PlantDefinition(SeedType.SEED_TORCHWOOD, null, ReanimationType.REANIM_TORCHWOOD, 29, 175, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "TORCHWOOD"),
        new PlantDefinition(SeedType.SEED_TALLNUT, null, ReanimationType.REANIM_TALLNUT, 28, 125, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "TALL_NUT"),
        new PlantDefinition(SeedType.SEED_SEASHROOM, null, ReanimationType.REANIM_SEASHROOM, 39, 0, 3000, PlantSubClass.SUBCLASS_SHOOTER, 150, "SEA_SHROOM"),
        new PlantDefinition(SeedType.SEED_PLANTERN, null, ReanimationType.REANIM_PLANTERN, 38, 25, 3000, PlantSubClass.SUBCLASS_NORMAL, 2500, "PLANTERN"),
        new PlantDefinition(SeedType.SEED_CACTUS, null, ReanimationType.REANIM_CACTUS, 15, 125, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "CACTUS"),
        new PlantDefinition(SeedType.SEED_BLOVER, null, ReanimationType.REANIM_BLOVER, 18, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "BLOVER"),
        new PlantDefinition(SeedType.SEED_SPLITPEA, null, ReanimationType.REANIM_SPLITPEA, 32, 125, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "SPLIT_PEA"),
        new PlantDefinition(SeedType.SEED_STARFRUIT, null, ReanimationType.REANIM_STARFRUIT, 30, 125, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "STARFRUIT"),
        new PlantDefinition(SeedType.SEED_PUMPKINSHELL, null, ReanimationType.REANIM_PUMPKIN, 25, 125, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "PUMPKIN"),
        new PlantDefinition(SeedType.SEED_MAGNETSHROOM, null, ReanimationType.REANIM_MAGNETSHROOM, 35, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "MAGNET_SHROOM"),
        new PlantDefinition(SeedType.SEED_CABBAGEPULT, null, ReanimationType.REANIM_CABBAGEPULT, 13, 100, 750, PlantSubClass.SUBCLASS_SHOOTER, 300, "CABBAGE_PULT"),
        new PlantDefinition(SeedType.SEED_FLOWERPOT, null, ReanimationType.REANIM_FLOWER_POT, 33, 25, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "FLOWER_POT"),
        new PlantDefinition(SeedType.SEED_KERNELPULT, null, ReanimationType.REANIM_KERNELPULT, 13, 100, 750, PlantSubClass.SUBCLASS_SHOOTER, 300, "KERNEL_PULT"),
        new PlantDefinition(SeedType.SEED_INSTANT_COFFEE, null, ReanimationType.REANIM_COFFEEBEAN, 33, 75, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "COFFEE_BEAN"),
        new PlantDefinition(SeedType.SEED_GARLIC, null, ReanimationType.REANIM_GARLIC, 8, 50, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "GARLIC"),
        new PlantDefinition(SeedType.SEED_UMBRELLA, null, ReanimationType.REANIM_UMBRELLALEAF, 23, 100, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "UMBRELLA_LEAF"),
        new PlantDefinition(SeedType.SEED_MARIGOLD, null, ReanimationType.REANIM_MARIGOLD, 24, 50, 3000, PlantSubClass.SUBCLASS_NORMAL, 2500, "MARIGOLD"),
        new PlantDefinition(SeedType.SEED_MELONPULT, null, ReanimationType.REANIM_MELONPULT, 14, 300, 750, PlantSubClass.SUBCLASS_SHOOTER, 300, "MELON_PULT"),
        new PlantDefinition(SeedType.SEED_GATLINGPEA, null, ReanimationType.REANIM_GATLINGPEA, 5, 250, 5000, PlantSubClass.SUBCLASS_SHOOTER, 150, "GATLING_PEA"),
        new PlantDefinition(SeedType.SEED_TWINSUNFLOWER, null, ReanimationType.REANIM_TWIN_SUNFLOWER, 1, 150, 5000, PlantSubClass.SUBCLASS_NORMAL, 2500, "TWIN_SUNFLOWER"),
        new PlantDefinition(SeedType.SEED_GLOOMSHROOM, null, ReanimationType.REANIM_GLOOMSHROOM, 27, 150, 5000, PlantSubClass.SUBCLASS_SHOOTER, 200, "GLOOM_SHROOM"),
        new PlantDefinition(SeedType.SEED_CATTAIL, null, ReanimationType.REANIM_CATTAIL, 27, 225, 5000, PlantSubClass.SUBCLASS_SHOOTER, 150, "CATTAIL"),
        new PlantDefinition(SeedType.SEED_WINTERMELON, null, ReanimationType.REANIM_WINTER_MELON, 27, 200, 5000, PlantSubClass.SUBCLASS_SHOOTER, 300, "WINTER_MELON"),
        new PlantDefinition(SeedType.SEED_GOLD_MAGNET, null, ReanimationType.REANIM_GOLD_MAGNET, 27, 50, 5000, PlantSubClass.SUBCLASS_NORMAL, 0, "GOLD_MAGNET"),
        new PlantDefinition(SeedType.SEED_SPIKEROCK, null, ReanimationType.REANIM_SPIKEROCK, 27, 125, 5000, PlantSubClass.SUBCLASS_NORMAL, 0, "SPIKEROCK"),
        new PlantDefinition(SeedType.SEED_COBCANNON, null, ReanimationType.REANIM_COBCANNON, 16, 500, 5000, PlantSubClass.SUBCLASS_NORMAL, 600, "COB_CANNON"),
        new PlantDefinition(SeedType.SEED_IMITATER, null, ReanimationType.REANIM_IMITATER, 33, 0, 750, PlantSubClass.SUBCLASS_NORMAL, 0, "IMITATER"),
        new PlantDefinition(SeedType.SEED_EXPLODE_O_NUT, null, ReanimationType.REANIM_WALLNUT, 2, 0, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "EXPLODE_O_NUT"),
        new PlantDefinition(SeedType.SEED_GIANT_WALLNUT, null, ReanimationType.REANIM_WALLNUT, 2, 0, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "GIANT_WALLNUT"),
        new PlantDefinition(SeedType.SEED_SPROUT, null, ReanimationType.REANIM_ZENGARDEN_SPROUT, 33, 0, 3000, PlantSubClass.SUBCLASS_NORMAL, 0, "SPROUT"),
        new PlantDefinition(SeedType.SEED_LEFTPEATER, null, ReanimationType.REANIM_REPEATER, 5, 200, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "REPEATER")
    }, SeedType.MAX_SEED_TYPES);

    public int mSeedType;
    public int mPlantCol;
    public int mAnimCounter;
    public int mFrame;
    public int mFrameLength;
    public int mNumFrames;
    public int mState;
    public int mPlantHealth;
    public int mPlantMaxHealth;
    public int mSubclass;
    public int mDisappearCountdown;
    public int mDoSpecialCountdown;
    public int mStateCountdown;
    public int mLaunchCounter;
    public int mLaunchRate;
    public Rect mPlantRect;
    public Rect mPlantAttackRect;
    public int mTargetX;
    public int mTargetY;
    public int mStartRow;
    public int mParticleID;
    public int mShootingCounter;
    public int mBodyReanimID;
    public int mHeadReanimID;
    public int mHeadReanimID2;
    public int mHeadReanimID3;
    public int mBlinkReanimID;
    public int mLightReanimID;
    public int mSleepingReanimID;
    public int mBlinkCountdown;
    public int mRecentlyEatenCountdown;
    public int mEatenFlashCountdown;
    public int mBeghouledFlashCountdown;
    public float mShakeOffsetX;
    public float mShakeOffsetY;
    public MagnetItem[] mMagnetItems;
    public int mTargetZombieID;
    public int mWakeUpCounter;
    public int mOnBungeeState;
    public int mImitaterType;
    public int mPottedPlantIndex;
    public boolean mAnimPing;
    public boolean mDead;
    public boolean mSquished;
    public boolean mIsAsleep;
    public boolean mIsOnBoard;
    public boolean mHighlighted;

    /**
     * Plant.cpp:109. Upstream declares {@code mPlantRect}, {@code mPlantAttackRect} and
     * {@code mMagnetItems} as inline value types (Plant.h:182-183, 202), so they are always valid
     * storage; allocate them here so a {@code new Plant()} — e.g. the save-game reader's
     * {@code DataArrayResetItemAt} — never exposes null rectangles or a null magnet array.
     */
    public Plant() {
        mPlantRect = new Rect();
        mPlantAttackRect = new Rect();
        mMagnetItems = new MagnetItem[MAX_MAGNET_ITEMS];
        for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
            mMagnetItems[i] = new MagnetItem();
        }
    }

    /** Plant.cpp:111-498. */
    public void plantInitialize(int theGridX, int theGridY, int theSeedType, int theImitaterType) {
        mPlantCol = theGridX;
        mRow = theGridY;
        if (mBoard != null) {
            mX = mBoard.gridToPixelX(theGridX, theGridY);
            mY = mBoard.gridToPixelY(theGridX, theGridY);
        }
        mAnimCounter = 0;
        mAnimPing = true;
        mFrame = 0;
        mShootingCounter = 0;
        mShakeOffsetX = 0.0f;
        mShakeOffsetY = 0.0f;
        mFrameLength = randRangeInt(12, 18);
        mTargetX = -1;
        mTargetY = -1;
        mStartRow = mRow;
        mNumFrames = 5;
        mState = PlantState.STATE_NOTREADY;
        mDead = false;
        mSquished = false;
        mSeedType = theSeedType;
        mImitaterType = theImitaterType;
        mPlantHealth = 300;
        mDoSpecialCountdown = 0;
        mDisappearCountdown = 200;
        mStateCountdown = 0;
        mParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
        mBodyReanimID = ReanimationID.REANIMATIONID_NULL;
        mHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        mHeadReanimID2 = ReanimationID.REANIMATIONID_NULL;
        mHeadReanimID3 = ReanimationID.REANIMATIONID_NULL;
        mBlinkReanimID = ReanimationID.REANIMATIONID_NULL;
        mLightReanimID = ReanimationID.REANIMATIONID_NULL;
        mSleepingReanimID = ReanimationID.REANIMATIONID_NULL;
        mBlinkCountdown = 0;
        mRecentlyEatenCountdown = 0;
        mEatenFlashCountdown = 0;
        mBeghouledFlashCountdown = 0;
        mWidth = 80;
        mHeight = 80;
        mMagnetItems = new MagnetItem[MAX_MAGNET_ITEMS];
        for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
            mMagnetItems[i] = new MagnetItem();
        }
        PlantDefinition aPlantDef = getPlantDefinition(theSeedType);
        mIsAsleep = false;
        mWakeUpCounter = 0;
        mOnBungeeState = PlantOnBungeeState.NOT_ON_BUNGEE;
        mPottedPlantIndex = -1;
        mLaunchRate = aPlantDef.mLaunchRate;
        mSubclass = aPlantDef.mSubClass;
        mRenderOrder = calcRenderOrder();

        Reanimation aBodyReanim = null;
        if (aPlantDef.mReanimationType != ReanimationType.REANIM_NONE) {
            float aOffsetY = plantDrawHeightOffset(mBoard, this, mSeedType, mPlantCol, mRow);
            aBodyReanim = mApp.addReanimation(0.0f, aOffsetY, mRenderOrder + 1, aPlantDef.mReanimationType);
            aBodyReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
            aBodyReanim.mAnimRate = randRangeFloat(10.0f, 15.0f);

            if (aBodyReanim.trackExists("anim_idle")) {
                aBodyReanim.setFramesForLayer("anim_idle");
            }

            if (mApp.isWallnutBowlingLevel() && aBodyReanim.trackExists("_ground")) {
                aBodyReanim.setFramesForLayer("_ground");
                if (mSeedType == SeedType.SEED_WALLNUT || mSeedType == SeedType.SEED_EXPLODE_O_NUT) {
                    aBodyReanim.mAnimRate = randRangeFloat(12.0f, 18.0f);
                } else if (mSeedType == SeedType.SEED_GIANT_WALLNUT) {
                    aBodyReanim.mAnimRate = randRangeFloat(6.0f, 10.0f);
                }
            }

            aBodyReanim.mIsAttachment = true;
            mBodyReanimID = mApp.reanimationGetID(aBodyReanim);
            mBlinkCountdown = 400 + Common.rand(400);
        }

        if (isNocturnal(mSeedType) && mBoard != null && !mBoard.stageIsNight()) {
            setSleeping(true);
        }

        if (mLaunchRate > 0) {
            if (makesSun()) {
                mLaunchCounter = randRangeInt(300, mLaunchRate / 2);
            } else {
                mLaunchCounter = randRangeInt(0, mLaunchRate);
            }
        } else {
            mLaunchCounter = 0;
        }

        switch (theSeedType) {
        case SeedType.SEED_BLOVER:
            mDoSpecialCountdown = 50;

            if (isInPlay()) {
                aBodyReanim.setFramesForLayer("anim_blow");
                aBodyReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                aBodyReanim.mAnimRate = 20.0f;
            } else {
                aBodyReanim.setFramesForLayer("anim_idle");
                aBodyReanim.mAnimRate = 10.0f;
            }
            break;
        case SeedType.SEED_PEASHOOTER:
        case SeedType.SEED_SNOWPEA:
        case SeedType.SEED_REPEATER:
        case SeedType.SEED_LEFTPEATER:
        case SeedType.SEED_GATLINGPEA:
            if (aBodyReanim != null) {
                aBodyReanim.mAnimRate = randRangeFloat(15.0f, 20.0f);
                Reanimation aHeadReanim = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, aPlantDef.mReanimationType);
                aHeadReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
                aHeadReanim.mAnimRate = aBodyReanim.mAnimRate;
                aHeadReanim.setFramesForLayer("anim_head_idle");
                mHeadReanimID = mApp.reanimationGetID(aHeadReanim);

                if (aBodyReanim.trackExists("anim_stem")) {
                    aHeadReanim.attachToAnotherReanimation(aBodyReanim, "anim_stem");
                } else if (aBodyReanim.trackExists("anim_idle")) {
                    aHeadReanim.attachToAnotherReanimation(aBodyReanim, "anim_idle");
                }
            }
            break;
        case SeedType.SEED_SPLITPEA: {
            aBodyReanim.mAnimRate = randRangeFloat(15.0f, 20.0f);
            Reanimation aHeadReanim1 = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, aPlantDef.mReanimationType);
            aHeadReanim1.mLoopType = ReanimLoopType.REANIM_LOOP;
            aHeadReanim1.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim1.setFramesForLayer("anim_head_idle");
            aHeadReanim1.attachToAnotherReanimation(aBodyReanim, "anim_idle");
            mHeadReanimID = mApp.reanimationGetID(aHeadReanim1);

            Reanimation aHeadReanim2 = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, aPlantDef.mReanimationType);
            aHeadReanim2.mLoopType = ReanimLoopType.REANIM_LOOP;
            aHeadReanim2.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim2.setFramesForLayer("anim_splitpea_idle");
            aHeadReanim2.attachToAnotherReanimation(aBodyReanim, "anim_idle");
            mHeadReanimID2 = mApp.reanimationGetID(aHeadReanim2);
            break;
        }
        case SeedType.SEED_THREEPEATER: {
            aBodyReanim.mAnimRate = randRangeFloat(15.0f, 20.0f);
            Reanimation aHeadReanim1 = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, aPlantDef.mReanimationType);
            aHeadReanim1.mLoopType = ReanimLoopType.REANIM_LOOP;
            aHeadReanim1.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim1.setFramesForLayer("anim_head_idle1");
            aHeadReanim1.attachToAnotherReanimation(aBodyReanim, "anim_head1");
            mHeadReanimID = mApp.reanimationGetID(aHeadReanim1);

            Reanimation aHeadReanim2 = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, aPlantDef.mReanimationType);
            aHeadReanim2.mLoopType = ReanimLoopType.REANIM_LOOP;
            aHeadReanim2.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim2.setFramesForLayer("anim_head_idle2");
            aHeadReanim2.attachToAnotherReanimation(aBodyReanim, "anim_head2");
            mHeadReanimID2 = mApp.reanimationGetID(aHeadReanim2);

            Reanimation aHeadReanim3 = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, aPlantDef.mReanimationType);
            aHeadReanim3.mLoopType = ReanimLoopType.REANIM_LOOP;
            aHeadReanim3.mAnimRate = aBodyReanim.mAnimRate;
            aHeadReanim3.setFramesForLayer("anim_head_idle3");
            aHeadReanim3.attachToAnotherReanimation(aBodyReanim, "anim_head3");
            mHeadReanimID3 = mApp.reanimationGetID(aHeadReanim3);
            break;
        }
        case SeedType.SEED_WALLNUT:
            mPlantHealth = 4000;
            mBlinkCountdown = 1000 + Common.rand(1000);
            break;
        case SeedType.SEED_EXPLODE_O_NUT:
            mPlantHealth = 4000;
            mBlinkCountdown = 1000 + Common.rand(1000);
            aBodyReanim.mColorOverride = new Color(255, 64, 64);
            break;
        case SeedType.SEED_GIANT_WALLNUT:
            mPlantHealth = 4000;
            mBlinkCountdown = 1000 + Common.rand(1000);
            break;
        case SeedType.SEED_TALLNUT:
            mPlantHealth = 8000;
            mHeight = 80;
            mBlinkCountdown = 1000 + Common.rand(1000);
            break;
        case SeedType.SEED_GARLIC:
            mPlantHealth = 400;
            aBodyReanim.setTruncateDisappearingFrames(true);
            break;
        case SeedType.SEED_GOLD_MAGNET:
            aBodyReanim.setTruncateDisappearingFrames(true);
            break;
        case SeedType.SEED_IMITATER:
            aBodyReanim.mAnimRate = randRangeFloat(25.0f, 30.0f);
            mStateCountdown = 200;
            break;
        case SeedType.SEED_CHERRYBOMB:
        case SeedType.SEED_JALAPENO:
            if (isInPlay()) {
                mDoSpecialCountdown = 100;

                aBodyReanim.setFramesForLayer("anim_explode");
                aBodyReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;

                mApp.playFoley(FoleyType.FOLEY_REVERSE_EXPLOSION);
            }
            break;
        case SeedType.SEED_POTATOMINE:
            aBodyReanim.mAnimRate = 12.0f;

            if (isInPlay()) {
                aBodyReanim.assignRenderGroupToTrack("anim_glow", Reanimation.RENDER_GROUP_HIDDEN);
                mStateCountdown = 1500;
            } else {
                aBodyReanim.setFramesForLayer("anim_armed");
                mState = PlantState.STATE_POTATO_ARMED;
            }
            break;
        case SeedType.SEED_GRAVEBUSTER:
            if (isInPlay()) {
                aBodyReanim.setFramesForLayer("anim_land");
                aBodyReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;

                mState = PlantState.STATE_GRAVEBUSTER_LANDING;
                mApp.playFoley(FoleyType.FOLEY_GRAVEBUSTERCHOMP);
            }
            break;
        case SeedType.SEED_SUNSHROOM:
            aBodyReanim.mFrameBasePose = 6;

            if (isInPlay()) {
                mX += Common.rand(10) - 5;
                mY += Common.rand(10) - 5;
            } else if (mIsAsleep) {
                aBodyReanim.setFramesForLayer("anim_bigsleep");
            } else {
                aBodyReanim.setFramesForLayer("anim_bigidle");
            }

            mState = PlantState.STATE_SUNSHROOM_SMALL;
            mStateCountdown = 12000;
            break;
        case SeedType.SEED_PUFFSHROOM:
        case SeedType.SEED_SEASHROOM:
            if (isInPlay()) {
                mX += Common.rand(10) - 5;
                mY += Common.rand(6) - 3;
            }
            break;
        case SeedType.SEED_PUMPKINSHELL:
            mPlantHealth = 4000;
            mWidth = 120;
            aBodyReanim.assignRenderGroupToTrack("Pumpkin_back", 1);
            break;
        case SeedType.SEED_CHOMPER:
            mState = PlantState.STATE_READY;
            break;
        case SeedType.SEED_PLANTERN:
            mStateCountdown = 50;

            if (!isOnBoard() || mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
                addAttachedParticle(mX + 40, mY + 40, RenderLayer.RENDER_LAYER_FOG + 1, ParticleEffect.PARTICLE_LANTERN_SHINE);
            }
            if (isInPlay()) {
                mApp.playSample(Resources.SOUND_PLANTERN_ID);
            }
            break;
        case SeedType.SEED_TORCHWOOD:
            break;
        case SeedType.SEED_MARIGOLD:
            aBodyReanim.mAnimRate = randRangeFloat(15.0f, 20.0f);
            break;
        case SeedType.SEED_CACTUS:
            mState = PlantState.STATE_CACTUS_LOW;
            break;
        case SeedType.SEED_INSTANT_COFFEE:
            mDoSpecialCountdown = 100;
            break;
        case SeedType.SEED_SCAREDYSHROOM:
            mState = PlantState.STATE_READY;
            break;
        case SeedType.SEED_COBCANNON:
            if (isInPlay()) {
                mState = PlantState.STATE_COBCANNON_ARMING;
                mStateCountdown = 500;

                aBodyReanim.setFramesForLayer("anim_unarmed_idle");
            }
            break;
        case SeedType.SEED_KERNELPULT:
            aBodyReanim.assignRenderGroupToPrefix("Cornpult_butter", Reanimation.RENDER_GROUP_HIDDEN);
            break;
        case SeedType.SEED_MAGNETSHROOM:
            aBodyReanim.setTruncateDisappearingFrames(true);
            break;
        case SeedType.SEED_SPIKEROCK:
            mPlantHealth = 450;
            break;
        case SeedType.SEED_SPROUT:
            break;
        case SeedType.SEED_FLOWERPOT:
            if (isInPlay()) {
                mState = PlantState.STATE_FLOWERPOT_INVULNERABLE;
                mStateCountdown = 100;
            }
            break;
        case SeedType.SEED_LILYPAD:
            if (isInPlay()) {
                mState = PlantState.STATE_LILYPAD_INVULNERABLE;
                mStateCountdown = 100;
            }
            break;
        case SeedType.SEED_TANGLEKELP:
            aBodyReanim.setTruncateDisappearingFrames(true);
            break;
        default:
            break;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BIG_TIME
                && (theSeedType == SeedType.SEED_WALLNUT || theSeedType == SeedType.SEED_SUNFLOWER || theSeedType == SeedType.SEED_MARIGOLD)) {
            mPlantHealth *= 2;
        }
        mPlantMaxHealth = mPlantHealth;

        if (mSeedType != SeedType.SEED_FLOWERPOT && isOnBoard()) {
            Plant aFlowerPot = mBoard.getFlowerPotAt(mPlantCol, mRow);
            if (aFlowerPot != null) {
                mApp.reanimationGet(aFlowerPot.mBodyReanimID).mAnimRate = 0.0f;
            }
        }
    }

    /** Plant.cpp:503-530. */
    public int calcRenderOrder() {
        int anOrder = PlantOrder.PLANT_ORDER_NORMAL;
        int aLayer = RenderLayer.RENDER_LAYER_PLANT;

        int aSeedType = mSeedType;
        if (mSeedType == SeedType.SEED_IMITATER && mImitaterType != SeedType.SEED_NONE) {
            aSeedType = mImitaterType;
        }

        if (mApp.isWallnutBowlingLevel()) {
            aLayer = RenderLayer.RENDER_LAYER_PROJECTILE;
        } else if (aSeedType == SeedType.SEED_PUMPKINSHELL) {
            anOrder = PlantOrder.PLANT_ORDER_PUMPKIN;
        } else if (isFlying(aSeedType)) {
            anOrder = PlantOrder.PLANT_ORDER_FLYER;
        } else if (aSeedType == SeedType.SEED_FLOWERPOT
                || (aSeedType == SeedType.SEED_LILYPAD && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN)) {
            anOrder = PlantOrder.PLANT_ORDER_LILYPAD;
        }

        return Board.makeRenderOrder(aLayer, mRow, anOrder * 5 - mX + 800);
    }

    /** Plant.cpp:532-594. */
    public void setSleeping(boolean theIsAsleep) {
        if (mIsAsleep == theIsAsleep || notOnGround()) {
            return;
        }

        mIsAsleep = theIsAsleep;
        if (theIsAsleep) {
            float aPosX = mX + 50.0f;
            float aPosY = mY + 40.0f;
            if (mSeedType == SeedType.SEED_FUMESHROOM) {
                aPosX += 12.0f;
            } else if (mSeedType == SeedType.SEED_SCAREDYSHROOM) {
                aPosY -= 20.0f;
            } else if (mSeedType == SeedType.SEED_GLOOMSHROOM) {
                aPosY -= 12.0f;
            }

            Reanimation aSleepReanim = mApp.addReanimation(aPosX, aPosY, mRenderOrder + 2, ReanimationType.REANIM_SLEEPING);
            aSleepReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
            aSleepReanim.mAnimRate = randRangeFloat(6.0f, 8.0f);
            aSleepReanim.mAnimTime = randRangeFloat(0.0f, 0.9f);
            mSleepingReanimID = mApp.reanimationGetID(aSleepReanim);
        } else {
            mApp.removeReanimation(mSleepingReanimID);
            mSleepingReanimID = ReanimationID.REANIMATIONID_NULL;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        if (theIsAsleep) {
            if (!isInPlay() && mSeedType == SeedType.SEED_SUNSHROOM) {
                aBodyReanim.setFramesForLayer("anim_bigsleep");
            } else if (aBodyReanim.trackExists("anim_sleep")) {
                float aAnimTime = aBodyReanim.mAnimTime;
                aBodyReanim.startBlend(20);
                aBodyReanim.setFramesForLayer("anim_sleep");
                aBodyReanim.mAnimTime = aAnimTime;
            } else {
                aBodyReanim.mAnimRate = 1.0f;
            }

            endBlink();
        } else {
            if (!isInPlay() && mSeedType == SeedType.SEED_SUNSHROOM) {
                aBodyReanim.setFramesForLayer("anim_bigidle");
            } else if (aBodyReanim.trackExists("anim_idle")) {
                float aAnimTime = aBodyReanim.mAnimTime;
                aBodyReanim.startBlend(20);
                aBodyReanim.setFramesForLayer("anim_idle");
                aBodyReanim.mAnimTime = aAnimTime;
            }

            if (aBodyReanim.mAnimRate < 2.0f && isInPlay()) {
                aBodyReanim.mAnimRate = randRangeFloat(10.0f, 15.0f);
            }
        }
    }

    /** Plant.cpp:596-639. */
    public int getDamageRangeFlags(int thePlantWeapon) {
        switch (mSeedType) {
        case SeedType.SEED_CACTUS:
            return thePlantWeapon == PlantWeapon.WEAPON_SECONDARY ? 1 : 2;
        case SeedType.SEED_CHERRYBOMB:
        case SeedType.SEED_JALAPENO:
        case SeedType.SEED_COBCANNON:
        case SeedType.SEED_DOOMSHROOM:
            return 127;
        case SeedType.SEED_MELONPULT:
        case SeedType.SEED_CABBAGEPULT:
        case SeedType.SEED_KERNELPULT:
        case SeedType.SEED_WINTERMELON:
            return 13;
        case SeedType.SEED_POTATOMINE:
            return 77;
        case SeedType.SEED_SQUASH:
            return 13;
        case SeedType.SEED_PUFFSHROOM:
        case SeedType.SEED_SEASHROOM:
        case SeedType.SEED_FUMESHROOM:
        case SeedType.SEED_GLOOMSHROOM:
        case SeedType.SEED_CHOMPER:
            return 9;
        case SeedType.SEED_CATTAIL:
            return 11;
        case SeedType.SEED_TANGLEKELP:
            return 5;
        case SeedType.SEED_GIANT_WALLNUT:
            return 17;
        default:
            return 1;
        }
    }

    /** Plant.cpp:641-644. */
    public boolean isOnHighGround() {
        return mBoard != null && mBoard.mGridSquareType[mPlantCol][mRow] == GridSquareType.GRIDSQUARE_HIGH_GROUND;
    }

    /** Plant.cpp:646-666. */
    public void spikeRockTakeDamage() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);

        spikeweedAttack();

        mPlantHealth -= 50;
        if (mPlantHealth <= 300) {
            aBodyReanim.assignRenderGroupToTrack("bigspike3", Reanimation.RENDER_GROUP_HIDDEN);
        }
        if (mPlantHealth <= 150) {
            aBodyReanim.assignRenderGroupToTrack("bigspike2", Reanimation.RENDER_GROUP_HIDDEN);
        }
        if (mPlantHealth <= 0) {
            mApp.playFoley(FoleyType.FOLEY_SQUISH);
            die();
        }
    }

    /** Plant.cpp:668-671. */
    public boolean isSpiky() {
        return mSeedType == SeedType.SEED_SPIKEWEED || mSeedType == SeedType.SEED_SPIKEROCK;
    }

    /** Plant.cpp:673-717. */
    public void doRowAreaDamage(int theDamage, int theDamageFlags) {
        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
        Rect aAttackRect = getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            int aDiffY = (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) ? 0 : (aZombie.mRow - mRow);
            if (mSeedType == SeedType.SEED_GLOOMSHROOM) {
                if (aDiffY < -1 || aDiffY > 1) {
                    continue;
                }
            } else if (aDiffY != 0) {
                continue;
            }

            if (aZombie.mOnHighGround == isOnHighGround() && aZombie.effectedByDamage(aDamageRangeFlags)) {
                Rect aZombieRect = aZombie.getZombieRect();
                if (Board.getRectOverlap(aAttackRect, aZombieRect) > 0) {
                    int aDamage = theDamage;
                    if ((aZombie.mZombieType == ZombieType.ZOMBIE_ZAMBONI || aZombie.mZombieType == ZombieType.ZOMBIE_CATAPULT)
                            && testBit(theDamageFlags, DamageFlags.DAMAGE_SPIKE)) {
                        aDamage = 1800;

                        if (mSeedType == SeedType.SEED_SPIKEROCK) {
                            spikeRockTakeDamage();
                        } else {
                            die();
                        }
                    }

                    aZombie.takeDamage(aDamage, theDamageFlags);
                    mApp.playFoley(FoleyType.FOLEY_SPLAT);
                }
            }
        }
    }

    /** Plant.cpp:719-732. */
    public PvzpParticleSystem addAttachedParticle(int thePosX, int thePosY, int theRenderPosition, int theEffect) {
        PvzpParticleSystem aParticle = mApp.particleTryToGet(mParticleID);
        if (aParticle != null) {
            aParticle.particleSystemDie();
        }

        PvzpParticleSystem aNewParticle = mApp.addPvzpParticle(thePosX, thePosY, theRenderPosition, theEffect);
        if (aNewParticle != null) {
            mParticleID = mApp.particleGetID(aNewParticle);
        }

        return aNewParticle;
    }

    /** Plant.cpp:734-812. */
    public boolean findTargetAndFire(int theRow, int thePlantWeapon) {
        Zombie aZombie = findTargetZombie(theRow, thePlantWeapon);
        if (aZombie == null) {
            return false;
        }

        endBlink();
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        Reanimation aHeadReanim = mApp.reanimationTryToGet(mHeadReanimID);

        if (mSeedType == SeedType.SEED_SPLITPEA && thePlantWeapon == PlantWeapon.WEAPON_SECONDARY) {
            Reanimation aHeadReanim2 = mApp.reanimationGet(mHeadReanimID2);
            aHeadReanim2.startBlend(20);
            aHeadReanim2.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
            aHeadReanim2.mAnimRate = 35.0f;
            aHeadReanim2.setFramesForLayer("anim_splitpea_shooting");
            mShootingCounter = 26;
        } else if (aHeadReanim != null && aHeadReanim.trackExists("anim_shooting")) {
            aHeadReanim.startBlend(20);
            aHeadReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
            aHeadReanim.mAnimRate = 35.0f;
            aHeadReanim.setFramesForLayer("anim_shooting");

            mShootingCounter = 35;
            if (mSeedType == SeedType.SEED_REPEATER || mSeedType == SeedType.SEED_SPLITPEA || mSeedType == SeedType.SEED_LEFTPEATER) {
                aHeadReanim.mAnimRate = 45.0f;
                mShootingCounter = 26;
            } else if (mSeedType == SeedType.SEED_GATLINGPEA) {
                aHeadReanim.mAnimRate = 38.0f;
                mShootingCounter = 100;
            }
        } else if (mState == PlantState.STATE_CACTUS_HIGH) {
            playBodyReanim("anim_shootinghigh", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 35.0f);
            mShootingCounter = 23;
        } else if (mSeedType == SeedType.SEED_GLOOMSHROOM) {
            playBodyReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 14.0f);
            mShootingCounter = 200;
        } else if (mSeedType == SeedType.SEED_CATTAIL) {
            playBodyReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 30.0f);
            mShootingCounter = 50;
        } else if (aBodyReanim != null && aBodyReanim.trackExists("anim_shooting")) {
            playBodyReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 35.0f);

            switch (mSeedType) {
            case SeedType.SEED_FUMESHROOM:
                mShootingCounter = 50;
                break;
            case SeedType.SEED_PUFFSHROOM:
                mShootingCounter = 29;
                break;
            case SeedType.SEED_SCAREDYSHROOM:
                mShootingCounter = 25;
                break;
            case SeedType.SEED_CABBAGEPULT:
                mShootingCounter = 32;
                break;
            case SeedType.SEED_MELONPULT:
            case SeedType.SEED_WINTERMELON:
                mShootingCounter = 36;
                break;
            case SeedType.SEED_KERNELPULT: {
                if (Common.rand(4) == 0) {
                    aBodyReanim = mApp.reanimationGet(mBodyReanimID);
                    aBodyReanim.assignRenderGroupToPrefix("Cornpult_butter", Reanimation.RENDER_GROUP_NORMAL);
                    aBodyReanim.assignRenderGroupToPrefix("Cornpult_kernal", Reanimation.RENDER_GROUP_HIDDEN);
                    mState = PlantState.STATE_KERNELPULT_BUTTER;
                }

                mShootingCounter = 30;
                break;
            }
            case SeedType.SEED_CACTUS:
                mShootingCounter = 35;
                break;
            default:
                mShootingCounter = 29;
                break;
            }
        } else {
            fire(aZombie, theRow, thePlantWeapon);
        }

        return true;
    }

    /** Plant.cpp:814-856. */
    public void launchThreepeater() {
        int rowAbove = mRow - 1;
        int rowBelow = mRow + 1;

        if ((findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY) != null)
                || (mBoard.rowCanHaveZombies(rowAbove) && findTargetZombie(rowAbove, PlantWeapon.WEAPON_PRIMARY) != null)
                || (mBoard.rowCanHaveZombies(rowBelow) && findTargetZombie(rowBelow, PlantWeapon.WEAPON_PRIMARY) != null)) {
            Reanimation aHeadReanim1 = mApp.reanimationGet(mHeadReanimID);
            Reanimation aHeadReanim2 = mApp.reanimationGet(mHeadReanimID2);
            Reanimation aHeadReanim3 = mApp.reanimationGet(mHeadReanimID3);

            if (mBoard.rowCanHaveZombies(rowBelow)) {
                aHeadReanim1.startBlend(10);
                aHeadReanim1.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                aHeadReanim1.mAnimRate = 20.0f;
                aHeadReanim1.setFramesForLayer("anim_shooting1");
            }

            aHeadReanim2.startBlend(10);
            aHeadReanim2.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
            aHeadReanim2.mAnimRate = 20.0f;
            aHeadReanim2.setFramesForLayer("anim_shooting2");

            if (mBoard.rowCanHaveZombies(rowAbove)) {
                aHeadReanim3.startBlend(10);
                aHeadReanim3.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                aHeadReanim3.mAnimRate = 20.0f;
                aHeadReanim3.setFramesForLayer("anim_shooting3");
            }

            mShootingCounter = 35;
        }
    }

    /** Plant.cpp:858-910. */
    public boolean findStarFruitTarget() {
        if (mRecentlyEatenCountdown > 0) {
            return true;
        }

        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
        int aCenterStarX = mX + 40;
        int aCenterStarY = mY + 40;

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            Rect aZombieRect = aZombie.getZombieRect();
            if (aZombie.effectedByDamage(aDamageRangeFlags)) {
                if (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS && mPlantCol >= 5) {
                    return true;
                }

                if (aZombie.mRow == mRow) {
                    if (aZombieRect.mX + aZombieRect.mWidth < aCenterStarX) {
                        return true;
                    }
                } else {
                    if (aZombie.mZombieType == ZombieType.ZOMBIE_DIGGER) {
                        aZombieRect.mWidth += 10;
                    }

                    float aProjectileTime = distance2D(aCenterStarX, aCenterStarY, aZombieRect.mX + aZombieRect.mWidth / 2,
                            aZombieRect.mY + aZombieRect.mHeight / 2) / 3.33f;
                    int aZombieHitX = (int) (aZombie.zombieTargetLeadX(aProjectileTime) - aZombieRect.mWidth / 2);
                    if ((aZombieHitX + aZombieRect.mWidth > aCenterStarX) && (aZombieHitX < aCenterStarX)) {
                        return true;
                    }

                    int aCenterZombieX = aZombieHitX + aZombieRect.mWidth / 2;
                    int aCenterZombieY = aZombieRect.mY + aZombieRect.mHeight / 2;
                    float angle = RAD_TO_DEG * (float) Math.atan2(aCenterZombieY - aCenterStarY, aCenterZombieX - aCenterStarX);
                    if (Math.abs(aZombie.mRow - mRow) < 2) {
                        if ((angle > 20.0f && angle < 40.0f) || (angle < -25.0f && angle > -45.0f)) {
                            return true;
                        }
                    } else {
                        if ((angle > 25.0f && angle < 35.0f) || (angle < -28.0f && angle > -38.0f)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /** Plant.cpp:912-919. */
    public void launchStarFruit() {
        if (findStarFruitTarget()) {
            playBodyReanim("anim_shoot", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 28.0f);
            mShootingCounter = 40;
        }
    }

    /** Plant.cpp:921-943. */
    public void starFruitFire() {
        mApp.playFoley(FoleyType.FOLEY_THROW);

        float aShootAngleX = (float) Math.cos(DEG_TO_RAD * 30.0f) * 3.33f;
        float aShootAngleY = (float) Math.sin(DEG_TO_RAD * 30.0f) * 3.33f;
        for (int i = 0; i < 5; i++) {
            Projectile aProjectile = mBoard.addProjectile(mX + 25, mY + 25, mRenderOrder - 1, mRow, ProjectileType.PROJECTILE_STAR);
            aProjectile.mDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
            aProjectile.mMotionType = ProjectileMotion.MOTION_STAR;

            switch (i) {
            case 0:
                aProjectile.mVelX = -3.33f;
                aProjectile.mVelY = 0.0f;
                break;
            case 1:
                aProjectile.mVelX = 0.0f;
                aProjectile.mVelY = 3.33f;
                break;
            case 2:
                aProjectile.mVelX = 0.0f;
                aProjectile.mVelY = -3.33f;
                break;
            case 3:
                aProjectile.mVelX = aShootAngleX;
                aProjectile.mVelY = aShootAngleY;
                break;
            case 4:
                aProjectile.mVelX = aShootAngleX;
                aProjectile.mVelY = -aShootAngleY;
                break;
            default:
                throw new IllegalStateException("unreachable");
            }
        }
    }

    /** Plant.cpp:945-996. */
    public void updateShooter() {
        mLaunchCounter--;
        if (mLaunchCounter <= 0) {
            mLaunchCounter = mLaunchRate - Common.rand(15);

            if (mSeedType == SeedType.SEED_THREEPEATER) {
                launchThreepeater();
            } else if (mSeedType == SeedType.SEED_STARFRUIT) {
                launchStarFruit();
            } else if (mSeedType == SeedType.SEED_SPLITPEA) {
                findTargetAndFire(mRow, PlantWeapon.WEAPON_PRIMARY);
                findTargetAndFire(mRow, PlantWeapon.WEAPON_SECONDARY);
            } else if (mSeedType == SeedType.SEED_CACTUS) {
                if (mState == PlantState.STATE_CACTUS_HIGH) {
                    findTargetAndFire(mRow, PlantWeapon.WEAPON_PRIMARY);
                } else if (mState == PlantState.STATE_CACTUS_LOW) {
                    findTargetAndFire(mRow, PlantWeapon.WEAPON_SECONDARY);
                }
            } else {
                findTargetAndFire(mRow, PlantWeapon.WEAPON_PRIMARY);
            }
        }

        if (mLaunchCounter == 50 && mSeedType == SeedType.SEED_CATTAIL) {
            findTargetAndFire(mRow, PlantWeapon.WEAPON_PRIMARY);
        }
        if (mLaunchCounter == 25) {
            if (mSeedType == SeedType.SEED_REPEATER || mSeedType == SeedType.SEED_LEFTPEATER) {
                findTargetAndFire(mRow, PlantWeapon.WEAPON_PRIMARY);
            } else if (mSeedType == SeedType.SEED_SPLITPEA) {
                findTargetAndFire(mRow, PlantWeapon.WEAPON_SECONDARY);
            }
        }
    }

    /** Plant.cpp:998-1001. */
    public boolean makesSun() {
        return mSeedType == SeedType.SEED_SUNFLOWER || mSeedType == SeedType.SEED_TWINSUNFLOWER || mSeedType == SeedType.SEED_SUNSHROOM;
    }

    /** Plant.cpp:1003-1076. */
    public void updateProductionPlant() {
        if (!isInPlay() || mApp.isIZombieLevel() || mApp.mGameMode == GameMode.GAMEMODE_UPSELL || mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            return;
        }

        if (mBoard.hasLevelAwardDropped()) {
            return;
        }

        if (mSeedType == SeedType.SEED_MARIGOLD && mBoard.mCurrentWave == mBoard.mNumWaves) {
            if (mState != PlantState.STATE_MARIGOLD_ENDING) {
                mState = PlantState.STATE_MARIGOLD_ENDING;
                mStateCountdown = 6000;
            } else if (mStateCountdown <= 0) {
                return;
            }
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                && mBoard.mChallenge.mChallengeState != ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT) {
            return;
        }

        mLaunchCounter--;
        if (mLaunchCounter <= 100) {
            int aFlashCountdown = pvzpAnimateCurve(100, 0, mLaunchCounter, 0, 100, PvzpCurves.CURVE_LINEAR);
            mEatenFlashCountdown = Math.max(mEatenFlashCountdown, aFlashCountdown);
        }
        if (mLaunchCounter <= 0) {
            mLaunchCounter = randRangeInt(mLaunchRate - 150, mLaunchRate);
            mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);

            if (mSeedType == SeedType.SEED_SUNSHROOM) {
                if (mState == PlantState.STATE_SUNSHROOM_SMALL) {
                    mBoard.addCoin(mX, mY, CoinType.COIN_SMALLSUN, CoinMotion.COIN_MOTION_FROM_PLANT);
                } else {
                    mBoard.addCoin(mX, mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
                }
            } else if (mSeedType == SeedType.SEED_SUNFLOWER) {
                mBoard.addCoin(mX, mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
            } else if (mSeedType == SeedType.SEED_TWINSUNFLOWER) {
                mBoard.addCoin(mX, mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
                mBoard.addCoin(mX, mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
            } else if (mSeedType == SeedType.SEED_MARIGOLD) {
                mBoard.addCoin(mX, mY, (Common.rand(100) < 10) ? CoinType.COIN_GOLD : CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
            }

            if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BIG_TIME) {
                if (mSeedType == SeedType.SEED_SUNFLOWER) {
                    mBoard.addCoin(mX, mY, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_PLANT);
                } else if (mSeedType == SeedType.SEED_MARIGOLD) {
                    mBoard.addCoin(mX, mY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                }
            }
        }
    }

    /** Plant.cpp:1078-1102. */
    public void updateSunShroom() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (mState == PlantState.STATE_SUNSHROOM_SMALL) {
            if (mStateCountdown == 0) {
                playBodyReanim("anim_grow", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 12.0f);
                mState = PlantState.STATE_SUNSHROOM_GROWING;
                mApp.playFoley(FoleyType.FOLEY_PLANTGROW);
            }

            updateProductionPlant();
        } else if (mState == PlantState.STATE_SUNSHROOM_GROWING) {
            if (aBodyReanim.mLoopCount > 0) {
                playBodyReanim("anim_bigidle", ReanimLoopType.REANIM_LOOP, 10, randRangeFloat(12.0f, 15.0f));
                mState = PlantState.STATE_SUNSHROOM_BIG;
            }
        } else {
            updateProductionPlant();
        }
    }

    /** Plant.cpp:1104-1130. */
    public void updateGraveBuster() {
        if (mState == PlantState.STATE_GRAVEBUSTER_LANDING) {
            if (mApp.reanimationGet(mBodyReanimID).mLoopCount > 0) {
                playBodyReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 10, 12.0f);
                mStateCountdown = 400;
                mState = PlantState.STATE_GRAVEBUSTER_EATING;
                addAttachedParticle(mX + 40, mY + 40, mRenderOrder + 4, ParticleEffect.PARTICLE_GRAVE_BUSTER);
            }
        } else if (mState == PlantState.STATE_GRAVEBUSTER_EATING && mStateCountdown == 0) {
            GridItem aGraveStone = mBoard.getGraveStoneAt(mPlantCol, mRow);
            if (aGraveStone != null) {
                aGraveStone.gridItemDie();
                mBoard.mGravesCleared++;
            }

            mApp.addPvzpParticle(mX + 40, mY + 40, mRenderOrder + 4, ParticleEffect.PARTICLE_GRAVE_BUSTER_DIE);
            die();
            mBoard.dropLootPiece(mX + 40, mY, 12);
        }
    }

    /** Plant.cpp:1132-1143. */
    public void playBodyReanim(String theTrackName, int theLoopType, int theBlendTime, float theAnimRate) {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);

        if (theBlendTime > 0) {
            aBodyReanim.startBlend(theBlendTime);
        }
        if (theAnimRate > 0.0f) {
            aBodyReanim.mAnimRate = theAnimRate;
        }

        aBodyReanim.mLoopType = theLoopType;
        aBodyReanim.mLoopCount = 0;
        aBodyReanim.setFramesForLayer(theTrackName);
    }

    /** Plant.cpp:1145-1196. */
    public void updatePotato() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);

        if (mState == PlantState.STATE_NOTREADY) {
            if (mStateCountdown == 0) {
                mApp.addPvzpParticle(mX + mWidth / 2, mY + mHeight / 2, mRenderOrder, ParticleEffect.PARTICLE_POTATO_MINE_RISE);
                playBodyReanim("anim_rise", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 18.0f);
                mState = PlantState.STATE_POTATO_RISING;
                mApp.playFoley(FoleyType.FOLEY_DIRT_RISE);
            }
        } else if (mState == PlantState.STATE_POTATO_RISING) {
            if (aBodyReanim.mLoopCount > 0) {
                float aRate = randRangeFloat(12.0f, 15.0f);
                playBodyReanim("anim_armed", ReanimLoopType.REANIM_LOOP, 0, aRate);

                Reanimation aLightReanim = mApp.addReanimation(0.0f, 0.0f, mRenderOrder + 2, getPlantDefinition(mSeedType).mReanimationType);
                aLightReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
                aLightReanim.mAnimRate = aRate - 2.0f;
                aLightReanim.setFramesForLayer("anim_glow");
                aLightReanim.mFrameCount = 10;
                aLightReanim.showOnlyTrack("anim_glow");
                aLightReanim.setTruncateDisappearingFrames("anim_glow", false);
                mLightReanimID = mApp.reanimationGetID(aLightReanim);
                aLightReanim.attachToAnotherReanimation(aBodyReanim, "anim_light");

                mState = PlantState.STATE_POTATO_ARMED;
                mBlinkCountdown = 400 + Common.rand(4000);
            }
        } else if (mState == PlantState.STATE_POTATO_ARMED) {
            if (findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY) != null) {
                doSpecial();
            } else {
                Reanimation aLightReanim = mApp.reanimationTryToGet(mLightReanimID);
                if (aLightReanim != null) {
                    aLightReanim.mFrameCount = pvzpAnimateCurve(200, 50, distanceToClosestZombie(), 10, 3, PvzpCurves.CURVE_LINEAR);
                }
            }
        }
    }

    /** Plant.cpp:1198-1266. */
    public void updateTanglekelp() {
        if (mState != PlantState.STATE_TANGLEKELP_GRABBING) {
            Zombie aZombie = findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY);
            if (aZombie != null) {
                mApp.playFoley(FoleyType.FOLEY_FLOOP);
                mState = PlantState.STATE_TANGLEKELP_GRABBING;
                mStateCountdown = 100;
                aZombie.poolSplash(false);

                float aVinesPosX = -13.0f;
                float aVinesPosY = 15.0f;
                if (aZombie.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
                    aVinesPosX = -43.0f;
                    aVinesPosY = 55.0f;
                }
                if (aZombie.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING) {
                    aVinesPosX = -20.0f;
                    aVinesPosY = 37.0f;
                }
                Reanimation aGrabReanim = aZombie.addAttachedReanim((int) aVinesPosX, (int) aVinesPosY, ReanimationType.REANIM_TANGLEKELP);
                if (aGrabReanim != null) {
                    aGrabReanim.setFramesForLayer("anim_grab");
                    aGrabReanim.mAnimRate = 24.0f;
                    aGrabReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
                }

                mTargetZombieID = mBoard.zombieGetID(aZombie);
            }
        } else {
            if (mStateCountdown == 50) {
                Zombie aZombie = mBoard.zombieTryToGet(mTargetZombieID);
                if (aZombie != null) {
                    aZombie.dragUnder();
                    aZombie.poolSplash(false);
                }
            }

            if (mStateCountdown == 20) {
                int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, mRow, 0);
                Reanimation aSplashReanim = mApp.addReanimation(mX - 23, mY + 7, aRenderPosition, ReanimationType.REANIM_SPLASH);
                aSplashReanim.overrideScale(1.3f, 1.3f);

                mApp.addPvzpParticle(mX + 31, mY + 64, aRenderPosition, ParticleEffect.PARTICLE_PLANTING_POOL);
                mApp.playFoley(FoleyType.FOLEY_ZOMBIE_ENTERING_WATER);
            }

            if (mStateCountdown == 0) {
                die();

                Zombie aZombie = mBoard.zombieTryToGet(mTargetZombieID);
                if (aZombie != null) {
                    aZombie.dieWithLoot();
                }
            }
        }
    }

    /** Plant.cpp:1268-1279. */
    public void spikeweedAttack() {
        if (mState != PlantState.STATE_SPIKEWEED_ATTACKING) {
            playBodyReanim("anim_attack", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 18.0f);
            mApp.playSample(Resources.SOUND_THROW_ID);

            mState = PlantState.STATE_SPIKEWEED_ATTACKING;
            mStateCountdown = 100;
        }
    }

    /** Plant.cpp:1281-1308. */
    public void updateSpikeweed() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (mState == PlantState.STATE_SPIKEWEED_ATTACKING) {
            if (mStateCountdown == 0) {
                mState = PlantState.STATE_NOTREADY;
            } else if (mSeedType == SeedType.SEED_SPIKEROCK) {
                if (mStateCountdown == 70 || mStateCountdown == 32) {
                    doRowAreaDamage(20, 33);
                }
            } else if (mStateCountdown == 75) {
                doRowAreaDamage(20, 33);
            }

            if (aBodyReanim.mLoopCount > 0) {
                playIdleAnim(randRangeFloat(12.0f, 15.0f));
            }
        } else if (findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY) != null) {
            spikeweedAttack();
        }
    }

    /** Plant.cpp:1310-1375. */
    public void updateScaredyShroom() {
        if (mShootingCounter > 0) {
            return;
        }

        boolean aHasZombieNearby = false;

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            Rect aZombieRect = aZombie.getZombieRect();
            int aDiffY = (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) ? 0 : (aZombie.mRow - mRow);
            if (!aZombie.mMindControlled && !aZombie.isDeadOrDying() && aDiffY <= 1 && aDiffY >= -1
                    && Board.getCircleRectOverlap(mX, mY + 20, 120, aZombieRect)) {
                aHasZombieNearby = true;
                break;
            }
        }

        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (mState == PlantState.STATE_READY) {
            if (aHasZombieNearby) {
                mState = PlantState.STATE_SCAREDYSHROOM_LOWERING;
                playBodyReanim("anim_scared", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 10.0f);
            }
        } else if (mState == PlantState.STATE_SCAREDYSHROOM_LOWERING) {
            if (aBodyReanim.mLoopCount > 0) {
                mState = PlantState.STATE_SCAREDYSHROOM_SCARED;
                playBodyReanim("anim_scaredidle", ReanimLoopType.REANIM_LOOP, 10, 0.0f);
            }
        } else if (mState == PlantState.STATE_SCAREDYSHROOM_SCARED) {
            if (!aHasZombieNearby) {
                mState = PlantState.STATE_SCAREDYSHROOM_RAISING;

                float aAnimRate = randRangeFloat(7.0f, 12.0f);
                playBodyReanim("anim_grow", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, aAnimRate);
            }
        } else if (mState == PlantState.STATE_SCAREDYSHROOM_RAISING) {
            if (aBodyReanim.mLoopCount > 0) {
                mState = PlantState.STATE_READY;

                float aAnimRate = randRangeFloat(10.0f, 15.0f);
                playIdleAnim(aAnimRate);
            }
        }

        if (mState != PlantState.STATE_READY) {
            mLaunchCounter = mLaunchRate;
        }
    }

    /** Plant.cpp:1377-1402. */
    public void updateTorchwood() {
        Rect aAttackRect = getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);

        for (Projectile aProjectile : mBoard.mProjectiles) {
            if (aProjectile.mDead) {
                continue;
            }
            if ((aProjectile.mRow == mRow)
                    && (aProjectile.mProjectileType == ProjectileType.PROJECTILE_PEA || aProjectile.mProjectileType == ProjectileType.PROJECTILE_SNOWPEA)) {
                Rect aProjectileRect = aProjectile.getProjectileRect();
                if (Board.getRectOverlap(aAttackRect, aProjectileRect) >= 10) {
                    if (aProjectile.mProjectileType == ProjectileType.PROJECTILE_PEA) {
                        aProjectile.convertToFireball(mPlantCol);
                    } else if (aProjectile.mProjectileType == ProjectileType.PROJECTILE_SNOWPEA) {
                        aProjectile.convertToPea(mPlantCol);
                    }
                }
            }
        }
    }

    /** Plant.cpp:1404-1422. */
    public void doSquashDamage() {
        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
        Rect aAttackRect = getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if ((aZombie.mRow == mRow || aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) && aZombie.effectedByDamage(aDamageRangeFlags)) {
                Rect aZombieRect = aZombie.getZombieRect();
                if (Board.getRectOverlap(aAttackRect, aZombieRect) > (aZombie.mZombieType == ZombieType.ZOMBIE_FOOTBALL ? -20 : 0)) {
                    aZombie.takeDamage(1800, 18);
                }
            }
        }
    }

    /** Plant.cpp:1424-1481. */
    public Zombie findSquashTarget() {
        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
        Rect aAttackRect = getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);

        int aClosestRange = 0;
        Zombie aClosestZombie = null;

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if ((aZombie.mRow == mRow || aZombie.mZombieType == ZombieType.ZOMBIE_BOSS)
                    && aZombie.mHasHead && !aZombie.isTangleKelpTarget() && aZombie.effectedByDamage(aDamageRangeFlags)) {
                Rect aZombieRect = aZombie.getZombieRect();

                if ((aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT && aZombieRect.mX < mX + 20)
                        || (aZombie.mZombiePhase != ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT
                                && aZombie.mZombiePhase != ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                                && aZombie.mZombiePhase != ZombiePhase.PHASE_SNORKEL_INTO_POOL
                                && aZombie.mZombiePhase != ZombiePhase.PHASE_DOLPHIN_INTO_POOL
                                && aZombie.mZombiePhase != ZombiePhase.PHASE_DOLPHIN_RIDING
                                && aZombie.mZombiePhase != ZombiePhase.PHASE_DOLPHIN_IN_JUMP
                                && !aZombie.isBobsledTeamWithSled())) {
                    int aRange = -Board.getRectOverlap(aAttackRect, aZombieRect);
                    if (aRange <= (aZombie.mIsEating ? 110 : 70)) {
                        int aPlantX = aAttackRect.mX;
                        if (aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_POST_VAULT
                                || aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT
                                || aZombie.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_IN_POOL
                                || aZombie.mZombieType == ZombieType.ZOMBIE_IMP
                                || aZombie.mZombieType == ZombieType.ZOMBIE_FOOTBALL
                                || mApp.isScaryPotterLevel()) {
                            aPlantX = aAttackRect.mX - 60;
                        }

                        if (aZombie.isWalkingBackwards() || aZombieRect.mX + aZombieRect.mWidth >= aPlantX) {
                            if (mBoard.zombieGetID(aZombie) == mTargetZombieID) {
                                return aZombie;
                            }

                            if (aClosestZombie == null || aRange < aClosestRange) {
                                aClosestZombie = aZombie;
                                aClosestRange = aRange;
                            }
                        }
                    }
                }
            }
        }

        return aClosestZombie;
    }

    /** Plant.cpp:1483-1582. */
    public void updateSquash() {
        if (mState == PlantState.STATE_NOTREADY) {
            Zombie aZombie = findSquashTarget();
            if (aZombie != null) {
                mTargetZombieID = mBoard.zombieGetID(aZombie);
                mTargetX = (int) (aZombie.zombieTargetLeadX(0.0f) - mWidth / 2);
                mState = PlantState.STATE_SQUASH_LOOK;
                mStateCountdown = 80;
                playBodyReanim(mTargetX < mX ? "anim_lookleft" : "anim_lookright", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 24.0f);
                mApp.playFoley(FoleyType.FOLEY_SQUASH_HMM);
            }
        } else if (mState == PlantState.STATE_SQUASH_LOOK) {
            if (mStateCountdown <= 0) {
                playBodyReanim("anim_jumpup", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
                mState = PlantState.STATE_SQUASH_PRE_LAUNCH;
                mStateCountdown = 45;
            }
        } else if (mState == PlantState.STATE_SQUASH_PRE_LAUNCH) {
            if (mStateCountdown <= 0) {
                Zombie aZombie = findSquashTarget();
                if (aZombie != null) {
                    mTargetX = (int) (aZombie.zombieTargetLeadX(30.0f) - mWidth / 2);
                }

                mState = PlantState.STATE_SQUASH_RISING;
                mStateCountdown = 50;
                mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, mRow, 0);
            }
        } else {
            int aTargetCol = mBoard.pixelToGridXKeepOnBoard(mTargetX, mY);
            int aDestY = mBoard.gridToPixelY(aTargetCol, mRow) + 8;

            if (mState == PlantState.STATE_SQUASH_RISING) {
                mX = pvzpAnimateCurve(50, 20, mStateCountdown, mBoard.gridToPixelX(mPlantCol, mStartRow), mTargetX, PvzpCurves.CURVE_EASE_IN_OUT);
                mY = pvzpAnimateCurve(50, 20, mStateCountdown, mBoard.gridToPixelY(mPlantCol, mStartRow), aDestY - 120, PvzpCurves.CURVE_EASE_IN_OUT);

                if (mStateCountdown == 0) {
                    playBodyReanim("anim_jumpdown", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 60.0f);
                    mState = PlantState.STATE_SQUASH_FALLING;
                    mStateCountdown = 10;
                }
            } else if (mState == PlantState.STATE_SQUASH_FALLING) {
                mY = pvzpAnimateCurve(10, 0, mStateCountdown, aDestY - 120, aDestY, PvzpCurves.CURVE_LINEAR);

                if (mStateCountdown == 5) {
                    doSquashDamage();
                }

                if (mStateCountdown == 0) {
                    if (mBoard.isPoolSquare(aTargetCol, mRow)) {
                        mApp.addReanimation(mX - 11, mY + 20, mRenderOrder + 1, ReanimationType.REANIM_SPLASH);
                        mApp.playFoley(FoleyType.FOLEY_SPLAT);
                        mApp.playSample(Resources.SOUND_ZOMBIESPLASH_ID);

                        die();
                    } else {
                        mState = PlantState.STATE_SQUASH_DONE_FALLING;
                        mStateCountdown = 100;

                        mBoard.shakeBoard(1, 4);
                        mApp.playFoley(FoleyType.FOLEY_THUMP);
                        float aOffsetY = mBoard.stageHasRoof() ? 69.0f : 80.0f;
                        mApp.addPvzpParticle(mX + 40, (int) (mY + aOffsetY), mRenderOrder + 4, ParticleEffect.PARTICLE_DUST_SQUASH);
                    }
                }
            } else if (mState == PlantState.STATE_SQUASH_DONE_FALLING) {
                if (mStateCountdown == 0) {
                    die();
                }
            }
        }
    }

    /** Plant.cpp:1584-1601. */
    public void updateDoomShroom() {
        if (mIsAsleep || mState == PlantState.STATE_DOINGSPECIAL) {
            return;
        }

        mState = PlantState.STATE_DOINGSPECIAL;
        mDoSpecialCountdown = 100;

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);

        aBodyReanim.setFramesForLayer("anim_explode");
        aBodyReanim.mAnimRate = 23.0f;
        aBodyReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD;
        aBodyReanim.setShakeOverride("DoomShroom_head1", 1.0f);
        aBodyReanim.setShakeOverride("DoomShroom_head2", 2.0f);
        aBodyReanim.setShakeOverride("DoomShroom_head3", 2.0f);
        mApp.playFoley(FoleyType.FOLEY_REVERSE_EXPLOSION);
    }

    /** Plant.cpp:1603-1610. */
    public void updateIceShroom() {
        if (!mIsAsleep && mState != PlantState.STATE_DOINGSPECIAL) {
            mState = PlantState.STATE_DOINGSPECIAL;
            mDoSpecialCountdown = 100;
        }
    }

    /** Plant.cpp:1612-1622. */
    public void updateBlover() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (aBodyReanim.mLoopCount > 0 && aBodyReanim.mLoopType != ReanimLoopType.REANIM_LOOP) {
            aBodyReanim.setFramesForLayer("anim_loop");
            aBodyReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
        }
    }

    /** Plant.cpp:1624-1626. */
    public void updateFlowerPot() {
        if (mState == PlantState.STATE_FLOWERPOT_INVULNERABLE && mStateCountdown == 0) {
            mState = PlantState.STATE_NOTREADY;
        }
    }

    /** Plant.cpp:1628-1632. */
    public void updateLilypad() {
        if (mState == PlantState.STATE_LILYPAD_INVULNERABLE && mStateCountdown == 0) {
            mState = PlantState.STATE_NOTREADY;
        }
    }

    /** Plant.cpp:1634-1644. */
    public void updateCoffeeBean() {
        if (mState == PlantState.STATE_DOINGSPECIAL) {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                die();
            }
        }
    }

    /** Plant.cpp:1646-1666. */
    public void updateUmbrella() {
        if (mState == PlantState.STATE_UMBRELLA_TRIGGERED) {
            if (mStateCountdown == 0) {
                mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PROJECTILE, mRow + 1, 0);
                mState = PlantState.STATE_UMBRELLA_REFLECTING;
            }
        } else if (mState == PlantState.STATE_UMBRELLA_REFLECTING) {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                playIdleAnim(0.0f);
                mState = PlantState.STATE_NOTREADY;
                mRenderOrder = calcRenderOrder();
            }
        }
    }

    /** Plant.cpp:1668-1703. */
    public void updateCobCannon() {
        if (mState == PlantState.STATE_COBCANNON_ARMING) {
            if (mStateCountdown == 0) {
                mState = PlantState.STATE_COBCANNON_LOADING;
                playBodyReanim("anim_charge", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
            }
        } else if (mState == PlantState.STATE_COBCANNON_LOADING) {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.5f)) {
                mApp.playFoley(FoleyType.FOLEY_SHOOP);
            }
            if (aBodyReanim.mLoopCount > 0) {
                mState = PlantState.STATE_COBCANNON_READY;
                playIdleAnim(12.0f);
            }
        } else if (mState == PlantState.STATE_COBCANNON_READY) {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            ReanimatorTrackInstance aCobTrack = aBodyReanim.getTrackInstanceByName("CobCannon_cob");
            aCobTrack.mTrackColor = getFlashingColor(mBoard.mMainCounter, 75);
        } else if (mState == PlantState.STATE_COBCANNON_FIRING) {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.48f)) {
                mApp.playFoley(FoleyType.FOLEY_COB_LAUNCH);
            }
        }
    }

    /** Plant.cpp:1705-1748. */
    public void updateCactus() {
        if (mShootingCounter > 0) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        if (mState == PlantState.STATE_CACTUS_RISING) {
            if (aBodyReanim.mLoopCount > 0) {
                mState = PlantState.STATE_CACTUS_HIGH;
                playBodyReanim("anim_idlehigh", ReanimLoopType.REANIM_LOOP, 20, 0.0f);
                if (mApp.isIZombieLevel()) {
                    aBodyReanim.mAnimRate = 0;
                }

                mLaunchCounter = 1;
            }
        } else if (mState == PlantState.STATE_CACTUS_HIGH) {
            if (findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY) == null) {
                mState = PlantState.STATE_CACTUS_LOWERING;
                playBodyReanim("anim_lower", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, aBodyReanim.mDefinition.mFPS);
            }
        } else if (mState == PlantState.STATE_CACTUS_LOWERING) {
            if (aBodyReanim.mLoopCount > 0) {
                mState = PlantState.STATE_CACTUS_LOW;
                playIdleAnim(0.0f);
            }
        } else if (findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY) != null) {
            mState = PlantState.STATE_CACTUS_RISING;
            playBodyReanim("anim_rise", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, aBodyReanim.mDefinition.mFPS);
            mApp.playFoley(FoleyType.FOLEY_PLANTGROW);
        }
    }

    /** Plant.cpp:1750-1836. */
    public void updateChomper() {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (mState == PlantState.STATE_READY) {
            if (findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY) != null) {
                playBodyReanim("anim_bite", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 24.0f);
                mState = PlantState.STATE_CHOMPER_BITING;
                mStateCountdown = 70;
            }
        } else if (mState == PlantState.STATE_CHOMPER_BITING) {
            if (mStateCountdown == 0) {
                mApp.playFoley(FoleyType.FOLEY_BIGCHOMP);

                Zombie aZombie = findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY);
                boolean doBite = false;
                if (aZombie != null) {
                    if (aZombie.mZombieType == ZombieType.ZOMBIE_GARGANTUAR || aZombie.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR
                            || aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                        doBite = true;
                    }
                }
                boolean doMiss = false;
                if (aZombie == null) {
                    doMiss = true;
                } else if (!aZombie.isImmobilizied()) {
                    if (aZombie.isBouncingPogo()
                            || aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT || aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
                        doMiss = true;
                    }
                }

                if (doBite) {
                    mApp.playFoley(FoleyType.FOLEY_SPLAT);
                    aZombie.takeDamage(40, 0);
                    mState = PlantState.STATE_CHOMPER_BITING_MISSED;
                } else if (doMiss) {
                    mState = PlantState.STATE_CHOMPER_BITING_MISSED;
                } else {
                    aZombie.dieWithLoot();
                    mState = PlantState.STATE_CHOMPER_BITING_GOT_ONE;
                }
            }
        } else if (mState == PlantState.STATE_CHOMPER_BITING_GOT_ONE) {
            if (aBodyReanim.mLoopCount > 0) {
                playBodyReanim("anim_chew", ReanimLoopType.REANIM_LOOP, 0, 15.0f);
                if (mApp.isIZombieLevel()) {
                    aBodyReanim.mAnimRate = 0;
                }

                mState = PlantState.STATE_CHOMPER_DIGESTING;
                mStateCountdown = 4000;
            }
        } else if (mState == PlantState.STATE_CHOMPER_DIGESTING) {
            if (mStateCountdown == 0) {
                playBodyReanim("anim_swallow", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
                mState = PlantState.STATE_CHOMPER_SWALLOWING;
            }
        } else if ((mState == PlantState.STATE_CHOMPER_SWALLOWING || mState == PlantState.STATE_CHOMPER_BITING_MISSED)
                && aBodyReanim.mLoopCount > 0) {
            playIdleAnim(aBodyReanim.mDefinition.mFPS);
            mState = PlantState.STATE_READY;
        }
    }

    /** Plant.cpp:1838-1856. */
    public MagnetItem getFreeMagnetItem() {
        if (mSeedType == SeedType.SEED_GOLD_MAGNET) {
            for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
                if (mMagnetItems[i].mItemType == MagnetItemType.MAGNET_ITEM_NONE) {
                    return mMagnetItems[i];
                }
            }

            return null;
        }

        return mMagnetItems[0];
    }

    /** Plant.cpp:1858-1967. */
    public void magnetShroomAttactItem(Zombie theZombie) {
        mState = PlantState.STATE_MAGNETSHROOM_SUCKING;
        mStateCountdown = 1500;
        playBodyReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
        mApp.playFoley(FoleyType.FOLEY_MAGNETSHROOM);

        MagnetItem aMagnetItem = getFreeMagnetItem();
        // PORT-NOTE: upstream takes `float& thePosX, float& thePosY` (Zombie::GetTrackPosition);
        // the port's out-parameter form is a single float[2] (x, y).
        float[] aPos = new float[2];
        if (theZombie.mHelmType == HelmType.HELMTYPE_PAIL) {
            int aDamageIndex = theZombie.getHelmDamageIndex();

            theZombie.mHelmHealth = 0;
            theZombie.mHelmType = HelmType.HELMTYPE_NONE;
            theZombie.getTrackPosition("anim_bucket", aPos);
            aMagnetItem.mPosX = aPos[0];
            aMagnetItem.mPosY = aPos[1];
            theZombie.reanimShowPrefix("anim_bucket", Reanimation.RENDER_GROUP_HIDDEN);
            theZombie.reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_NORMAL);

            aMagnetItem.mPosX -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BUCKET1_ID).getWidth() / 2;
            aMagnetItem.mPosY -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BUCKET1_ID).getHeight() / 2;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 25.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f) + 20.0f;
            aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_PAIL_1 + aDamageIndex;
        } else if (theZombie.mHelmType == HelmType.HELMTYPE_FOOTBALL) {
            int aDamageIndex = theZombie.getHelmDamageIndex();

            theZombie.mHelmHealth = 0;
            theZombie.mHelmType = HelmType.HELMTYPE_NONE;
            theZombie.getTrackPosition("zombie_football_helmet", aPos);
            aMagnetItem.mPosX = aPos[0];
            aMagnetItem.mPosY = aPos[1];
            theZombie.reanimShowPrefix("zombie_football_helmet", Reanimation.RENDER_GROUP_HIDDEN);
            theZombie.reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_NORMAL);

            aMagnetItem.mPosX += 37.0f;
            aMagnetItem.mPosY -= 60.0f;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 20.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f) + 20.0f;
            aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_FOOTBALL_HELMET_1 + aDamageIndex;
        } else if (theZombie.mShieldType == ShieldType.SHIELDTYPE_DOOR) {
            int aDamageIndex = theZombie.getShieldDamageIndex();

            theZombie.detachShield();
            theZombie.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
            if (!theZombie.mIsEating) {
                theZombie.startWalkAnim(0);
            }
            theZombie.getTrackPosition("anim_screendoor", aPos);
            aMagnetItem.mPosX = aPos[0];
            aMagnetItem.mPosY = aPos[1];

            aMagnetItem.mPosX -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_SCREENDOOR1_ID).getWidth() / 2;
            aMagnetItem.mPosY -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_SCREENDOOR1_ID).getHeight() / 2;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 30.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f);
            aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_DOOR_1 + aDamageIndex;
        } else if (theZombie.mShieldType == ShieldType.SHIELDTYPE_LADDER) {
            int aDamageIndex = theZombie.getShieldDamageIndex();

            theZombie.detachShield();

            aMagnetItem.mPosX = theZombie.mPosX + 31.0f;
            aMagnetItem.mPosY = theZombie.mPosY + 20.0f;
            aMagnetItem.mPosX -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_5_ID).getWidth() / 2;
            aMagnetItem.mPosY -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_5_ID).getHeight() / 2;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 30.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f);
            aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_LADDER_1 + aDamageIndex;
        } else if (theZombie.mZombieType == ZombieType.ZOMBIE_POGO) {
            theZombie.pogoBreak(16);
            theZombie.getTrackPosition("Zombie_pogo_stick", aPos);
            aMagnetItem.mPosX = aPos[0];
            aMagnetItem.mPosY = aPos[1];

            aMagnetItem.mPosX += 40.0f - Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_5_ID).getWidth() / 2;
            aMagnetItem.mPosY += 84.0f - Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_5_ID).getHeight() / 2;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 30.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f);
            aMagnetItem.mItemType = theZombie.mHasArm ? MagnetItemType.MAGNET_ITEM_POGO_1 : MagnetItemType.MAGNET_ITEM_POGO_3;
        } else if (theZombie.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_RUNNING) {
            theZombie.stopZombieSound();
            theZombie.pickRandomSpeed();
            theZombie.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
            theZombie.reanimShowPrefix("Zombie_jackbox_box", Reanimation.RENDER_GROUP_HIDDEN);
            theZombie.reanimShowPrefix("Zombie_jackbox_handle", Reanimation.RENDER_GROUP_HIDDEN);
            theZombie.getTrackPosition("Zombie_jackbox_box", aPos);
            aMagnetItem.mPosX = aPos[0];
            aMagnetItem.mPosY = aPos[1];

            aMagnetItem.mPosX -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_JACKBOX_BOX_ID).getWidth() / 2;
            aMagnetItem.mPosY -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_JACKBOX_BOX_ID).getHeight() / 2;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 20.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f) + 15.0f;
            aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_JACK_IN_THE_BOX;
        } else if (theZombie.mZombieType == ZombieType.ZOMBIE_DIGGER) {
            theZombie.diggerLoseAxe();
            theZombie.getTrackPosition("Zombie_digger_pickaxe", aPos);
            aMagnetItem.mPosX = aPos[0];
            aMagnetItem.mPosY = aPos[1];

            aMagnetItem.mPosX -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_DIGGER_PICKAXE_ID).getWidth() / 2;
            aMagnetItem.mPosY -= Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_DIGGER_PICKAXE_ID).getHeight() / 2;
            aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 45.0f;
            aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f) + 15.0f;
            aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_PICK_AXE;
        }
    }

    /** Plant.cpp:1969-2004. */
    public boolean drawMagnetItemsOnTop() {
        if (mSeedType == SeedType.SEED_GOLD_MAGNET) {
            for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
                if (mMagnetItems[i].mItemType != MagnetItemType.MAGNET_ITEM_NONE) {
                    return true;
                }
            }

            return false;
        }

        if (mSeedType == SeedType.SEED_MAGNETSHROOM) {
            for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
                MagnetItem aMagnetItem = mMagnetItems[i];
                if (aMagnetItem.mItemType != MagnetItemType.MAGNET_ITEM_NONE) {
                    SexyVector2 aVectorToPlant = new SexyVector2(mX + aMagnetItem.mDestOffsetX - aMagnetItem.mPosX,
                            mY + aMagnetItem.mDestOffsetY - aMagnetItem.mPosY);
                    if (aVectorToPlant.magnitude() > 20.0f) {
                        return true;
                    }
                }
            }

            return false;
        }

        return false;
    }

    /** Plant.cpp:2006-2176. */
    public void updateMagnetShroom() {
        for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
            MagnetItem aMagnetItem = mMagnetItems[i];
            if (aMagnetItem.mItemType != MagnetItemType.MAGNET_ITEM_NONE) {
                SexyVector2 aVectorToPlant = new SexyVector2(mX + aMagnetItem.mDestOffsetX - aMagnetItem.mPosX,
                        mY + aMagnetItem.mDestOffsetY - aMagnetItem.mPosY);
                if (aVectorToPlant.magnitude() > 20.0f) {
                    aMagnetItem.mPosX += aVectorToPlant.x * 0.05f;
                    aMagnetItem.mPosY += aVectorToPlant.y * 0.05f;
                }
            }
        }

        if (mState == PlantState.STATE_MAGNETSHROOM_CHARGING) {
            if (mStateCountdown == 0) {
                mState = PlantState.STATE_READY;

                float aAnimRate = randRangeFloat(10.0f, 15.0f);
                playBodyReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 30, aAnimRate);
                if (mApp.isIZombieLevel()) {
                    Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
                    aBodyReanim.mAnimRate = 0.0f;
                }

                mMagnetItems[0].mItemType = MagnetItemType.MAGNET_ITEM_NONE;
            }
        } else if (mState == PlantState.STATE_MAGNETSHROOM_SUCKING) {
            Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
            if (aBodyReanim.mLoopCount > 0) {
                playBodyReanim("anim_nonactive_idle2", ReanimLoopType.REANIM_LOOP, 20, 2.0f);
                if (mApp.isIZombieLevel()) {
                    aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
                    aBodyReanim.mAnimRate = 0.0f;
                }

                mState = PlantState.STATE_MAGNETSHROOM_CHARGING;
            }
        } else {
            float aClosestDistance = 0.0f;
            Zombie aClosestZombie = null;

            for (Zombie aZombie : mBoard.mZombies) {
                if (aZombie.mDead) {
                    continue;
                }
                int aDiffY = aZombie.mRow - mRow;
                Rect aZombieRect = aZombie.getZombieRect();

                if (aZombie.mMindControlled) {
                    continue;
                }

                if (!aZombie.mHasHead) {
                    continue;
                }

                if (aZombie.mZombieHeight != ZombieHeight.HEIGHT_ZOMBIE_NORMAL || aZombie.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE) {
                    continue;
                }

                if (aZombie.isDeadOrDying()) {
                    continue;
                }

                if (aZombieRect.mX > GameConstants.BOARD_WIDTH || aDiffY > 2 || aDiffY < -2) {
                    continue;
                }

                if (aZombie.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING
                        || aZombie.mZombiePhase == ZombiePhase.PHASE_DIGGER_STUNNED
                        || aZombie.mZombiePhase == ZombiePhase.PHASE_DIGGER_WALKING
                        || aZombie.mZombieType == ZombieType.ZOMBIE_POGO) {
                    if (!aZombie.mHasObject) {
                        continue;
                    }
                } else if (!(aZombie.mHelmType == HelmType.HELMTYPE_PAIL
                        || aZombie.mHelmType == HelmType.HELMTYPE_FOOTBALL
                        || aZombie.mShieldType == ShieldType.SHIELDTYPE_DOOR
                        || aZombie.mShieldType == ShieldType.SHIELDTYPE_LADDER
                        || aZombie.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_RUNNING)) {
                    continue;
                }

                int aRadius = aZombie.mIsEating ? 320 : 270;
                if (Board.getCircleRectOverlap(mX, mY + 20, aRadius, aZombieRect)) {
                    float aDistance = distance2D(mX, mY, aZombieRect.mX, aZombieRect.mY);
                    aDistance += Math.abs(aDiffY) * 80.0f;

                    if (aClosestZombie == null || aDistance < aClosestDistance) {
                        aClosestZombie = aZombie;
                        aClosestDistance = aDistance;
                    }
                }
            }

            if (aClosestZombie != null) {
                magnetShroomAttactItem(aClosestZombie);
                return;
            }

            float aClosestLadderDist = 0.0f;
            GridItem aClosestLadder = null;

            for (GridItem aGridItem : mBoard.mGridItems) {
                if (aGridItem.mDead) {
                    continue;
                }
                if (aGridItem.mGridItemType == GridItemType.GRIDITEM_LADDER) {
                    int aDiffX = Math.abs(aGridItem.mGridX - mPlantCol);
                    int aDiffY = Math.abs(aGridItem.mGridY - mRow);
                    int aSquareDistance = Math.max(aDiffX, aDiffY);
                    if (aSquareDistance <= 2) {
                        float aDistance = aSquareDistance + aDiffY * 0.05f;
                        if (aClosestLadder == null || aDistance < aClosestLadderDist) {
                            aClosestLadder = aGridItem;
                            aClosestLadderDist = aDistance;
                        }
                    }
                }
            }

            if (aClosestLadder != null) {
                mState = PlantState.STATE_MAGNETSHROOM_SUCKING;
                mStateCountdown = 1500;
                playBodyReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
                mApp.playFoley(FoleyType.FOLEY_MAGNETSHROOM);

                aClosestLadder.gridItemDie();

                MagnetItem aMagnetItem = getFreeMagnetItem();
                aMagnetItem.mPosX = mBoard.gridToPixelX(aClosestLadder.mGridX, aClosestLadder.mGridY) + 40;
                aMagnetItem.mPosY = mBoard.gridToPixelY(aClosestLadder.mGridX, aClosestLadder.mGridY);
                aMagnetItem.mDestOffsetX = randRangeFloat(-10.0f, 10.0f) + 10.0f;
                aMagnetItem.mDestOffsetY = randRangeFloat(-10.0f, 10.0f);
                aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_LADDER_PLACED;
            }
        }
    }

    /** Plant.cpp:2178-2199. */
    public Coin findGoldMagnetTarget() {
        Coin aClosestCoin = null;
        float aClosestDistance = 0.0f;

        for (Coin aCoin : mBoard.mCoins) {
            if (aCoin.mDead) {
                continue;
            }
            if (aCoin.isMoney() && aCoin.mCoinMotion != CoinMotion.COIN_MOTION_FROM_PRESENT && !aCoin.mIsBeingCollected && aCoin.mCoinAge >= 50) {
                float aDistance = distance2D(mX + mWidth / 2, mY + mHeight / 2, aCoin.mPosX + aCoin.mWidth / 2, aCoin.mPosY + aCoin.mHeight / 2);
                if (aClosestCoin == null || aDistance < aClosestDistance) {
                    aClosestCoin = aCoin;
                    aClosestDistance = aDistance;
                }
            }
        }

        return aClosestCoin;
    }

    /** Plant.cpp:2201-2235. */
    public void goldMagnetFindTargets() {
        if (getFreeMagnetItem() == null) {
            return;
        }

        for (;;) {
            MagnetItem aMagnetItem = getFreeMagnetItem();
            if (aMagnetItem == null) {
                break;
            }

            Coin aCoin = findGoldMagnetTarget();
            if (aCoin == null) {
                break;
            }

            aMagnetItem.mPosX = aCoin.mPosX + 15.0f;
            aMagnetItem.mPosY = aCoin.mPosY + 15.0f;
            aMagnetItem.mDestOffsetX = randRangeFloat(20.0f, 40.0f);
            aMagnetItem.mDestOffsetY = randRangeFloat(-20.0f, 0.0f) + 20.0f;

            switch (aCoin.mType) {
            case CoinType.COIN_SILVER:
                aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_SILVER_COIN;
                break;
            case CoinType.COIN_GOLD:
                aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_GOLD_COIN;
                break;
            case CoinType.COIN_DIAMOND:
                aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_DIAMOND;
                break;
            default:
                return;
            }

            aCoin.die();
        }
    }

    /** Plant.cpp:2237-2255. */
    public boolean isAGoldMagnetAboutToSuck() {
        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (!aPlant.notOnGround() && aPlant.mSeedType == SeedType.SEED_GOLD_MAGNET
                    && aPlant.mState == PlantState.STATE_MAGNETSHROOM_SUCKING) {
                Reanimation aBodyReanim = mApp.reanimationGet(aPlant.mBodyReanimID);
                if (aBodyReanim.mAnimTime < 0.5f) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Plant.cpp:2257-2306. */
    public void updateGoldMagnetShroom() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);

        boolean aIsSuckingCoin = false;
        for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
            MagnetItem aMagnetItem = mMagnetItems[i];
            if (aMagnetItem.mItemType != MagnetItemType.MAGNET_ITEM_NONE) {
                SexyVector2 aVectorToPlant = new SexyVector2(mX + aMagnetItem.mDestOffsetX - aMagnetItem.mPosX,
                        mY + aMagnetItem.mDestOffsetY - aMagnetItem.mPosY);
                float aDistance = aVectorToPlant.magnitude();
                if (aDistance < 20.0f) {
                    int aCoinType;
                    switch (aMagnetItem.mItemType) {
                    case MagnetItemType.MAGNET_ITEM_SILVER_COIN:
                        aCoinType = CoinType.COIN_SILVER;
                        break;
                    case MagnetItemType.MAGNET_ITEM_GOLD_COIN:
                        aCoinType = CoinType.COIN_GOLD;
                        break;
                    case MagnetItemType.MAGNET_ITEM_DIAMOND:
                        aCoinType = CoinType.COIN_DIAMOND;
                        break;
                    default:
                        return;
                    }

                    int aValue = Coin.getCoinValue(aCoinType);
                    mApp.mPlayerInfo.addCoins(aValue);
                    mBoard.mCoinsCollected += aValue;
                    mApp.playFoley(FoleyType.FOLEY_COIN);

                    aMagnetItem.mItemType = MagnetItemType.MAGNET_ITEM_NONE;
                } else {
                    float aSpeed = pvzpAnimateCurveFloatTime(30.0f, 0.0f, aDistance, 0.02f, 0.05f, PvzpCurves.CURVE_LINEAR);
                    aMagnetItem.mPosX += aVectorToPlant.x * aSpeed;
                    aMagnetItem.mPosY += aVectorToPlant.y * aSpeed;

                    aIsSuckingCoin = true;
                }
            }
        }

        if (mState == PlantState.STATE_MAGNETSHROOM_CHARGING) {
            if (mStateCountdown == 0) {
                mState = PlantState.STATE_READY;
            }
        } else if (mState == PlantState.STATE_MAGNETSHROOM_SUCKING) {
            if (aBodyReanim.shouldTriggerTimedEvent(0.4f)) {
                mApp.playFoley(FoleyType.FOLEY_MAGNETSHROOM);
                goldMagnetFindTargets();
            }

            if (aBodyReanim.mLoopCount > 0 && !aIsSuckingCoin) {
                playIdleAnim(14.0f);
                mState = PlantState.STATE_MAGNETSHROOM_CHARGING;
                mStateCountdown = randRangeInt(200, 300);
            }
        } else if (!isAGoldMagnetAboutToSuck() && Common.rand(50) == 0 && findGoldMagnetTarget() != null) {
            mBoard.showCoinBank();
            mState = PlantState.STATE_MAGNETSHROOM_SUCKING;
            playBodyReanim("anim_attract", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);
        }
    }

    /** Plant.cpp:2308-2318. */
    public void removeEffects() {
        mApp.removeParticle(mParticleID);
        mApp.removeReanimation(mBodyReanimID);
        mApp.removeReanimation(mHeadReanimID);
        mApp.removeReanimation(mHeadReanimID2);
        mApp.removeReanimation(mHeadReanimID3);
        mApp.removeReanimation(mLightReanimID);
        mApp.removeReanimation(mBlinkReanimID);
        mApp.removeReanimation(mSleepingReanimID);
    }

    /** Plant.cpp:2320-2359. */
    public void squish() {
        if (notOnGround()) {
            return;
        }

        if (!mIsAsleep) {
            if (mSeedType == SeedType.SEED_CHERRYBOMB || mSeedType == SeedType.SEED_JALAPENO
                    || mSeedType == SeedType.SEED_DOOMSHROOM || mSeedType == SeedType.SEED_ICESHROOM) {
                doSpecial();
                return;
            } else if (mSeedType == SeedType.SEED_POTATOMINE && mState != PlantState.STATE_NOTREADY) {
                doSpecial();
                return;
            }
        }

        if (mSeedType == SeedType.SEED_SQUASH && mState != PlantState.STATE_NOTREADY) {
            return;
        }

        mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, mRow, 8);
        mSquished = true;
        mDisappearCountdown = 500;
        mApp.playFoley(FoleyType.FOLEY_SQUISH);
        removeEffects();

        GridItem aLadder = mBoard.getLadderAt(mPlantCol, mRow);
        if (aLadder != null) {
            aLadder.gridItemDie();
        }

        if (mApp.isIZombieLevel()) {
            mBoard.mChallenge.iZombiePlantDropRemainingSun(this);
        }
    }

    /** Plant.cpp:2361-2506. */
    public void updateBowling() {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null && aBodyReanim.trackExists("_ground")) {
            float aSpeed = aBodyReanim.getTrackVelocity("_ground");
            if (mSeedType == SeedType.SEED_GIANT_WALLNUT) {
                aSpeed *= 2;
            }

            mX -= (int) aSpeed;
            if (mX > 800) {
                die();
            }
        }

        if (mState == PlantState.STATE_BOWLING_UP) {
            mY -= 2;
        } else if (mState == PlantState.STATE_BOWLING_DOWN) {
            mY += 2;
        }
        int aDistToGrid = mBoard.gridToPixelY(0, mRow) - mY;
        if (aDistToGrid < -2 || aDistToGrid > 2) {
            return;
        }

        int aNewState = mState;
        if (mState == PlantState.STATE_BOWLING_UP && mRow <= 0) {
            aNewState = PlantState.STATE_BOWLING_DOWN;
        } else if (mState == PlantState.STATE_BOWLING_DOWN && mRow >= 4) {
            aNewState = PlantState.STATE_BOWLING_UP;
        }

        Zombie aZombie = findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY);
        if (aZombie != null) {
            int aPosX = mX + mWidth / 2;
            int aPosY = mY + mHeight / 2;

            if (mSeedType == SeedType.SEED_EXPLODE_O_NUT) {
                mApp.playFoley(FoleyType.FOLEY_CHERRYBOMB);
                mApp.playSample(Resources.SOUND_BOWLINGIMPACT2_ID);

                int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY) | 32;
                mBoard.killAllZombiesInRadius(mRow, aPosX, aPosY, 90, 1, true, aDamageRangeFlags);
                mApp.addPvzpParticle(aPosX, aPosY, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_POWIE);
                mBoard.shakeBoard(3, -4);

                die();

                return;
            }

            mApp.playFoley(FoleyType.FOLEY_BOWLINGIMPACT);
            mBoard.shakeBoard(1, -2);

            if (mSeedType == SeedType.SEED_GIANT_WALLNUT) {
                aZombie.takeDamage(1800, 0);
            } else if (aZombie.mShieldType == ShieldType.SHIELDTYPE_DOOR && mState != PlantState.STATE_NOTREADY) {
                aZombie.takeDamage(1800, 0);
            } else if (aZombie.mShieldType != ShieldType.SHIELDTYPE_NONE) {
                aZombie.takeShieldDamage(400, 0);
            } else if (aZombie.mHelmType != HelmType.HELMTYPE_NONE) {
                if (aZombie.mHelmType == HelmType.HELMTYPE_PAIL) {
                    mApp.playFoley(FoleyType.FOLEY_SHIELD_HIT);
                } else if (aZombie.mHelmType == HelmType.HELMTYPE_TRAFFIC_CONE) {
                    mApp.playFoley(FoleyType.FOLEY_PLASTIC_HIT);
                }

                aZombie.takeHelmDamage(900, 0);
            } else {
                aZombie.takeDamage(1800, 0);
            }

            if ((!mApp.isFirstTimeAdventureMode() || mApp.mPlayerInfo.getLevel() > 10) && mSeedType == SeedType.SEED_WALLNUT) {
                mLaunchCounter++;
                if (mLaunchCounter == 2) {
                    mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
                    mBoard.addCoin(aPosX, aPosY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                } else if (mLaunchCounter == 3) {
                    mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
                    mBoard.addCoin((int) (aPosX - 5.0f), aPosY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                    mBoard.addCoin((int) (aPosX + 5.0f), aPosY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                } else if (mLaunchCounter == 4) {
                    mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
                    mBoard.addCoin((int) (aPosX - 10.0f), aPosY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                    mBoard.addCoin(aPosX, aPosY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                    mBoard.addCoin((int) (aPosX + 10.0f), aPosY, CoinType.COIN_SILVER, CoinMotion.COIN_MOTION_COIN);
                } else if (mLaunchCounter >= 5) {
                    mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
                    mBoard.addCoin(aPosX, aPosY, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);
                    ReportAchievement.giveAchievement(mApp, AchievementId.RollSomeHeads, true);
                }
            }

            if (mSeedType != SeedType.SEED_GIANT_WALLNUT) {
                if (mRow == 4 || mState == PlantState.STATE_BOWLING_DOWN) {
                    aNewState = PlantState.STATE_BOWLING_UP;
                } else if (mRow == 0 || mState == PlantState.STATE_BOWLING_UP) {
                    aNewState = PlantState.STATE_BOWLING_DOWN;
                } else {
                    aNewState = Common.rand(2) != 0 ? PlantState.STATE_BOWLING_UP : PlantState.STATE_BOWLING_DOWN;
                }
            }
        }

        if (aNewState == PlantState.STATE_BOWLING_UP) {
            mRow--;
            mState = PlantState.STATE_BOWLING_UP;
            mRenderOrder = calcRenderOrder();
        } else if (aNewState == PlantState.STATE_BOWLING_DOWN) {
            mState = PlantState.STATE_BOWLING_DOWN;
            mRenderOrder = calcRenderOrder();
            mRow++;
        }
    }

    /** Plant.cpp:2508-2596. */
    public void updateAbilities() {
        if (!isInPlay()) {
            return;
        }

        if (mState == PlantState.STATE_DOINGSPECIAL || mSquished) {
            mDisappearCountdown--;
            if (mDisappearCountdown < 0) {
                die();
                return;
            }
        }

        if (mWakeUpCounter > 0) {
            mWakeUpCounter--;
            if (mWakeUpCounter == 60) {
                mApp.playFoley(FoleyType.FOLEY_WAKEUP);
            }
            if (mWakeUpCounter == 0) {
                setSleeping(false);
            }
        }

        if (mIsAsleep || mSquished || mOnBungeeState != PlantOnBungeeState.NOT_ON_BUNGEE) {
            return;
        }

        updateShooting();

        if (mStateCountdown > 0) {
            mStateCountdown--;
        }

        if (mApp.isWallnutBowlingLevel()) {
            updateBowling();
            return;
        }

        if (mSeedType == SeedType.SEED_SQUASH) {
            updateSquash();
        } else if (mSeedType == SeedType.SEED_DOOMSHROOM) {
            updateDoomShroom();
        } else if (mSeedType == SeedType.SEED_ICESHROOM) {
            updateIceShroom();
        } else if (mSeedType == SeedType.SEED_CHOMPER) {
            updateChomper();
        } else if (mSeedType == SeedType.SEED_BLOVER) {
            updateBlover();
        } else if (mSeedType == SeedType.SEED_FLOWERPOT) {
            updateFlowerPot();
        } else if (mSeedType == SeedType.SEED_LILYPAD) {
            updateLilypad();
        } else if (mSeedType == SeedType.SEED_IMITATER) {
            updateImitater();
        } else if (mSeedType == SeedType.SEED_INSTANT_COFFEE) {
            updateCoffeeBean();
        } else if (mSeedType == SeedType.SEED_UMBRELLA) {
            updateUmbrella();
        } else if (mSeedType == SeedType.SEED_COBCANNON) {
            updateCobCannon();
        } else if (mSeedType == SeedType.SEED_CACTUS) {
            updateCactus();
        } else if (mSeedType == SeedType.SEED_MAGNETSHROOM) {
            updateMagnetShroom();
        } else if (mSeedType == SeedType.SEED_GOLD_MAGNET) {
            updateGoldMagnetShroom();
        } else if (mSeedType == SeedType.SEED_SUNSHROOM) {
            updateSunShroom();
        } else if (makesSun() || mSeedType == SeedType.SEED_MARIGOLD) {
            updateProductionPlant();
        } else if (mSeedType == SeedType.SEED_GRAVEBUSTER) {
            updateGraveBuster();
        } else if (mSeedType == SeedType.SEED_TORCHWOOD) {
            updateTorchwood();
        } else if (mSeedType == SeedType.SEED_POTATOMINE) {
            updatePotato();
        } else if (mSeedType == SeedType.SEED_SPIKEWEED || mSeedType == SeedType.SEED_SPIKEROCK) {
            updateSpikeweed();
        } else if (mSeedType == SeedType.SEED_TANGLEKELP) {
            updateTanglekelp();
        } else if (mSeedType == SeedType.SEED_SCAREDYSHROOM) {
            updateScaredyShroom();
        }

        if (mSubclass == PlantSubClass.SUBCLASS_SHOOTER) {
            updateShooter();
        }
        if (mDoSpecialCountdown > 0) {
            mDoSpecialCountdown--;
            if (mDoSpecialCountdown == 0) {
                doSpecial();
            }
        }
    }

    /** Plant.cpp:2598-2606. */
    public boolean isPartOfUpgradableTo(int theUpgradedType) {
        if (theUpgradedType == SeedType.SEED_COBCANNON && mSeedType == SeedType.SEED_KERNELPULT) {
            return mBoard.isValidCobCannonSpot(mPlantCol, mRow) || mBoard.isValidCobCannonSpot(mPlantCol - 1, mRow);
        }

        return isUpgradableTo(theUpgradedType);
    }

    /** Plant.cpp:2608-2641. */
    public boolean isUpgradableTo(int theUpgradedType) {
        if (theUpgradedType == SeedType.SEED_GATLINGPEA && mSeedType == SeedType.SEED_REPEATER) {
            return true;
        }
        if (theUpgradedType == SeedType.SEED_WINTERMELON && mSeedType == SeedType.SEED_MELONPULT) {
            return true;
        }
        if (theUpgradedType == SeedType.SEED_TWINSUNFLOWER && mSeedType == SeedType.SEED_SUNFLOWER) {
            return true;
        }
        if (theUpgradedType == SeedType.SEED_SPIKEROCK && mSeedType == SeedType.SEED_SPIKEWEED) {
            return true;
        }
        if (theUpgradedType == SeedType.SEED_COBCANNON && mSeedType == SeedType.SEED_KERNELPULT) {
            return mBoard.isValidCobCannonSpot(mPlantCol, mRow);
        }
        if (theUpgradedType == SeedType.SEED_GOLD_MAGNET && mSeedType == SeedType.SEED_MAGNETSHROOM) {
            return true;
        }
        if (theUpgradedType == SeedType.SEED_GLOOMSHROOM && mSeedType == SeedType.SEED_FUMESHROOM) {
            return true;
        }
        if (theUpgradedType == SeedType.SEED_CATTAIL && mSeedType == SeedType.SEED_LILYPAD) {
            Plant aPlant = mBoard.getTopPlantAt(mPlantCol, mRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            return aPlant == null || aPlant.mSeedType != SeedType.SEED_CATTAIL;
        }
        return false;
    }

    /** Plant.cpp:2643-2725. */
    public void updateReanimColor() {
        if (!isOnBoard()) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        int aSeedType = mBoard.getSeedTypeInCursor();
        Color aColorOverride;

        boolean isOnGlove = false;
        if (mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE) {
            Plant aPlant = mBoard.mPlants.dataArrayTryToGet(mBoard.mCursorObject.mGlovePlantID);
            if (aPlant != null && aPlant.mPlantCol == mPlantCol && aPlant.mRow == mRow) {
                isOnGlove = true;
            }
        }

        if (isOnGlove) {
            aColorOverride = new Color(128, 128, 128);
        } else if (isPartOfUpgradableTo(aSeedType) && mBoard.canPlantAt(mPlantCol, mRow, aSeedType) == PlantingReason.PLANTING_OK) {
            aColorOverride = getFlashingColor(mBoard.mMainCounter, 90);
        } else if (aSeedType == SeedType.SEED_COBCANNON && mSeedType == SeedType.SEED_KERNELPULT
                && mBoard.canPlantAt(mPlantCol - 1, mRow, aSeedType) == PlantingReason.PLANTING_OK) {
            aColorOverride = getFlashingColor(mBoard.mMainCounter, 90);
        } else if (mSeedType == SeedType.SEED_EXPLODE_O_NUT) {
            aColorOverride = new Color(255, 64, 64);
        } else {
            aColorOverride = new Color(255, 255, 255);
        }

        aBodyReanim.mColorOverride = aColorOverride;

        if (mHighlighted) {
            aBodyReanim.mExtraAdditiveColor = new Color(255, 255, 255, 196);
            aBodyReanim.mEnableExtraAdditiveDraw = true;
            if (mImitaterType == SeedType.SEED_IMITATER) {
                aBodyReanim.mExtraAdditiveColor = new Color(255, 255, 255, 92);
            }
        } else if (mBeghouledFlashCountdown > 0) {
            int anAlpha = pvzpAnimateCurve(50, 0, mBeghouledFlashCountdown % 50, 1, 128, PvzpCurves.CURVE_BOUNCE);
            aBodyReanim.mExtraAdditiveColor = new Color(255, 255, 255, anAlpha);
            aBodyReanim.mEnableExtraAdditiveDraw = true;
        } else if (mEatenFlashCountdown > 0) {
            int aGrayness = Math.max(0, Math.min(mEatenFlashCountdown * 3, mImitaterType == SeedType.SEED_IMITATER ? 128 : 255));
            aBodyReanim.mExtraAdditiveColor = new Color(aGrayness, aGrayness, aGrayness);
            aBodyReanim.mEnableExtraAdditiveDraw = true;
        } else {
            aBodyReanim.mEnableExtraAdditiveDraw = false;
        }

        if (mBeghouledFlashCountdown > 0) {
            int anAlpha = pvzpAnimateCurve(50, 0, mBeghouledFlashCountdown % 50, 1, 128, PvzpCurves.CURVE_BOUNCE);
            aBodyReanim.mExtraOverlayColor = new Color(255, 255, 255, anAlpha);
            aBodyReanim.mEnableExtraOverlayDraw = true;
        } else {
            aBodyReanim.mEnableExtraOverlayDraw = false;
        }

        aBodyReanim.propogateColorToAttachments();
    }

    /** Plant.cpp:2727-2732. */
    public boolean isOnBoard() {
        return mIsOnBoard;
    }

    /** Plant.cpp:2734-2737. */
    public boolean isInPlay() {
        return isOnBoard() && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN && mApp.mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM;
    }

    /** Plant.cpp:2739-2854. */
    public void updateReanim() {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        updateReanimColor();

        float aOffsetX = mShakeOffsetX;
        float aOffsetY = plantDrawHeightOffset(mBoard, this, mSeedType, mPlantCol, mRow);
        float aScaleX = 1.0f;
        float aScaleY = 1.0f;
        if ((mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BIG_TIME)
                && (mSeedType == SeedType.SEED_WALLNUT || mSeedType == SeedType.SEED_SUNFLOWER || mSeedType == SeedType.SEED_MARIGOLD)) {
            aScaleX = 1.5f;
            aScaleY = 1.5f;
            aOffsetX -= 20.0f;
            aOffsetY -= 40.0f;
        }
        if (mSeedType == SeedType.SEED_GIANT_WALLNUT) {
            aScaleX = 2.0f;
            aScaleY = 2.0f;
            aOffsetX -= 76.0f;
            aOffsetY -= 64.0f;
        }
        if (mSeedType == SeedType.SEED_INSTANT_COFFEE) {
            aScaleX = 0.8f;
            aScaleY = 0.8f;
            aOffsetX += 12.0f;
            aOffsetY += 10.0f;
        }
        if (mSeedType == SeedType.SEED_POTATOMINE) {
            aScaleX = 0.8f;
            aScaleY = 0.8f;
            aOffsetX += 12.0f;
            aOffsetY += 12.0f;
        }
        if (mState == PlantState.STATE_GRAVEBUSTER_EATING) {
            aOffsetY += pvzpAnimateCurveFloat(400, 0, mStateCountdown, 0.0f, 30.0f, PvzpCurves.CURVE_LINEAR);
        }
        if (mWakeUpCounter > 0) {
            float aScaleFactor = pvzpAnimateCurveFloat(70, 0, mWakeUpCounter, 1.0f, 0.8f, PvzpCurves.CURVE_EASE_SIN_WAVE);
            aScaleY *= aScaleFactor;
            aOffsetY += 80.0f - 80.0f * aScaleFactor;
        }

        aBodyReanim.update();

        if (mSeedType == SeedType.SEED_LEFTPEATER) {
            aOffsetX += 80.0f * aScaleX;
            aScaleX *= -1.0f;
        }

        if (mPottedPlantIndex != -1) {
            PottedPlant aPottedPlant = mApp.mPlayerInfo.mPottedPlant[mPottedPlantIndex];

            if (aPottedPlant.mFacing == PottedPlant.FacingDirection.FACING_LEFT) {
                aOffsetX += 80.0f * aScaleX;
                aScaleX *= -1.0f;
            }

            float aOffsetXStart;
            float aOffsetXEnd;
            float aOffsetYStart;
            float aOffsetYEnd;
            float aScaleStart;
            float aScaleEnd;
            if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_SMALL) {
                aOffsetXStart = 20.0f;
                aOffsetXEnd = 20.0f;
                aOffsetYStart = 40.0f;
                aOffsetYEnd = 40.0f;
                aScaleStart = 0.5f;
                aScaleEnd = 0.5f;
            } else if (aPottedPlant.mPlantAge == PottedPlantAge.PLANTAGE_MEDIUM) {
                aOffsetXStart = 20.0f;
                aOffsetXEnd = 10.0f;
                aOffsetYStart = 40.0f;
                aOffsetYEnd = 20.0f;
                aScaleStart = 0.5f;
                aScaleEnd = 0.75f;
            } else {
                aOffsetXStart = 10.0f;
                aOffsetXEnd = 0.0f;
                aOffsetYStart = 20.0f;
                aOffsetYEnd = 0.0f;
                aScaleStart = 0.75f;
                aScaleEnd = 1.0f;
            }

            float aAnimatedOffsetX = pvzpAnimateCurveFloat(100, 0, mStateCountdown, aOffsetXStart, aOffsetXEnd,
                    PvzpCurves.CURVE_LINEAR);
            float aAnimatedOffsetY = pvzpAnimateCurveFloat(100, 0, mStateCountdown, aOffsetYStart, aOffsetYEnd,
                    PvzpCurves.CURVE_LINEAR);
            float aAnimatedScale = pvzpAnimateCurveFloat(100, 0, mStateCountdown, aScaleStart, aScaleEnd,
                    PvzpCurves.CURVE_LINEAR);

            aOffsetX += aAnimatedOffsetX * aScaleX;
            aOffsetY += aAnimatedOffsetY * aScaleY;
            aScaleX *= aAnimatedScale;
            aScaleY *= aAnimatedScale;
            aOffsetX += mApp.mZenGarden.zenPlantOffsetX(aPottedPlant);
            aOffsetY += mApp.mZenGarden.plantPottedDrawHeightOffset(mSeedType, aScaleY);
        }

        aBodyReanim.setPosition(aOffsetX, aOffsetY);
        aBodyReanim.overrideScale(aScaleX, aScaleY);
    }

    /** Plant.cpp:2856-2875. */
    public void update() {
        boolean doUpdate = false;
        if (isOnBoard() && mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && mApp.isWallnutBowlingLevel()) {
            doUpdate = true;
        } else if (isOnBoard() && mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            doUpdate = true;
        } else if (isOnBoard() && mBoard.mCutScene.shouldRunUpsellBoard()) {
            doUpdate = true;
        } else if (!isOnBoard() || mApp.mGameScene == GameScenes.SCENE_PLAYING) {
            doUpdate = true;
        }

        if (doUpdate) {
            updateAbilities();
            animate();

            if (mPlantHealth < 0) {
                die();
            }

            updateReanim();
        }
    }

    /** Plant.cpp:2877-2886. */
    public boolean notOnGround() {
        if (mSeedType == SeedType.SEED_SQUASH) {
            if (mState == PlantState.STATE_SQUASH_RISING || mState == PlantState.STATE_SQUASH_FALLING
                    || mState == PlantState.STATE_SQUASH_DONE_FALLING) {
                return true;
            }
        }

        return mSquished || mOnBungeeState == PlantOnBungeeState.RISING_WITH_BUNGEE || mDead;
    }

    /** Plant.cpp:2888-2966. */
    public Reanimation attachBlinkAnim(Reanimation theReanimBody) {
        PlantDefinition aPlantDef = getPlantDefinition(mSeedType);
        Reanimation aAnimToAttach = theReanimBody;
        String aTrackToPlay = "anim_blink";
        String aTrackToAttach = null;

        if (mSeedType == SeedType.SEED_WALLNUT || mSeedType == SeedType.SEED_TALLNUT
                || mSeedType == SeedType.SEED_EXPLODE_O_NUT || mSeedType == SeedType.SEED_GIANT_WALLNUT) {
            int aHit = Common.rand(10);
            if (aHit < 1 && theReanimBody.trackExists("anim_blink_twitch")) {
                aTrackToPlay = "anim_blink_twitch";
            } else {
                aTrackToPlay = aHit < 7 ? "anim_blink_twice" : "anim_blink_thrice";
            }
        } else if (mSeedType == SeedType.SEED_THREEPEATER) {
            int aHit = Common.rand(3);
            if (aHit == 0) {
                aTrackToPlay = "anim_blink1";
                aTrackToAttach = "anim_face1";
                ReanimatorTrackInstance aTrackInstance = theReanimBody.getTrackInstanceByName("anim_head1");
                aAnimToAttach = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
            } else if (aHit == 1) {
                aTrackToPlay = "anim_blink2";
                aTrackToAttach = "anim_face2";
                ReanimatorTrackInstance aTrackInstance = theReanimBody.getTrackInstanceByName("anim_head2");
                aAnimToAttach = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
            } else {
                aTrackToPlay = "anim_blink3";
                aTrackToAttach = "anim_face3";
                ReanimatorTrackInstance aTrackInstance = theReanimBody.getTrackInstanceByName("anim_head3");
                aAnimToAttach = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
            }
        } else if (mSeedType == SeedType.SEED_SPLITPEA) {
            if (Common.rand(2) == 0) {
                aTrackToPlay = "anim_blink";
                aTrackToAttach = "anim_face";
                aAnimToAttach = mApp.reanimationTryToGet(mHeadReanimID);
            } else {
                aTrackToPlay = "anim_blink2";
                aTrackToAttach = "anim_face2";
                aAnimToAttach = mApp.reanimationTryToGet(mHeadReanimID2);
            }
        } else if (mSeedType == SeedType.SEED_TWINSUNFLOWER) {
            if (Common.rand(2) == 0) {
                aTrackToPlay = "anim_blink";
                aTrackToAttach = "anim_face";
            } else {
                aTrackToPlay = "anim_blink2";
                aTrackToAttach = "anim_face2";
            }
        } else if (mSeedType == SeedType.SEED_PEASHOOTER || mSeedType == SeedType.SEED_SNOWPEA || mSeedType == SeedType.SEED_REPEATER
                || mSeedType == SeedType.SEED_LEFTPEATER || mSeedType == SeedType.SEED_GATLINGPEA) {
            if (theReanimBody.trackExists("anim_stem")) {
                ReanimatorTrackInstance aTrackInstance = theReanimBody.getTrackInstanceByName("anim_stem");
                aAnimToAttach = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
            } else if (theReanimBody.trackExists("anim_idle")) {
                ReanimatorTrackInstance aTrackInstance = theReanimBody.getTrackInstanceByName("anim_idle");
                aAnimToAttach = Attachment.findReanimAttachment(aTrackInstance.mAttachmentID);
            }
        }

        if (aAnimToAttach == null) {
            Common.logInfoLn("Missing head anim");
            return null;
        }

        if (!theReanimBody.trackExists(aTrackToPlay)) {
            return null;
        }

        Reanimation aBlinkReanim = mApp.mEffectSystem.mReanimationHolder.allocReanimation(0.0f, 0.0f, 0, aPlantDef.mReanimationType);
        aBlinkReanim.setFramesForLayer(aTrackToPlay);
        aBlinkReanim.mLoopType = ReanimLoopType.REANIM_PLAY_ONCE_FULL_LAST_FRAME_AND_HOLD;
        aBlinkReanim.mAnimRate = 15.0f;
        aBlinkReanim.mColorOverride = theReanimBody.mColorOverride;

        if (aTrackToAttach != null && aAnimToAttach.trackExists(aTrackToAttach)) {
            aBlinkReanim.attachToAnotherReanimation(aAnimToAttach, aTrackToAttach);
        } else if (aAnimToAttach.trackExists("anim_face")) {
            aBlinkReanim.attachToAnotherReanimation(aAnimToAttach, "anim_face");
        } else if (aAnimToAttach.trackExists("anim_idle")) {
            aBlinkReanim.attachToAnotherReanimation(aAnimToAttach, "anim_idle");
        } else {
            Common.logInfoLn("Missing anim_idle for blink");
        }

        aBlinkReanim.mFilterEffect = theReanimBody.mFilterEffect;
        return aBlinkReanim;
    }

    /** Plant.cpp:2968-3012. */
    public void doBlink() {
        mBlinkCountdown = 400 + Common.rand(400);

        if (notOnGround() || mShootingCounter != 0) {
            return;
        }

        if (mSeedType == SeedType.SEED_POTATOMINE && mState != PlantState.STATE_POTATO_ARMED) {
            return;
        }

        if (mState == PlantState.STATE_CACTUS_RISING || mState == PlantState.STATE_CACTUS_HIGH || mState == PlantState.STATE_CACTUS_LOWERING
                || mState == PlantState.STATE_MAGNETSHROOM_SUCKING || mState == PlantState.STATE_MAGNETSHROOM_CHARGING) {
            return;
        }

        endBlink();
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        if ((mSeedType == SeedType.SEED_TALLNUT && aBodyReanim.getImageOverride("anim_idle") == Resources.getImageById(Resources.IMAGE_REANIM_TALLNUT_CRACKED2_ID))
                || (mSeedType == SeedType.SEED_GARLIC && aBodyReanim.getImageOverride("anim_face") == Resources.getImageById(Resources.IMAGE_REANIM_GARLIC_BODY3_ID))) {
            return;
        }

        if (mSeedType == SeedType.SEED_WALLNUT || mSeedType == SeedType.SEED_TALLNUT
                || mSeedType == SeedType.SEED_EXPLODE_O_NUT || mSeedType == SeedType.SEED_GIANT_WALLNUT) {
            mBlinkCountdown = 1000 + Common.rand(1000);
        }

        Reanimation aBlinkReanim = attachBlinkAnim(aBodyReanim);
        if (aBlinkReanim != null) {
            mBlinkReanimID = mApp.reanimationGetID(aBlinkReanim);
        }
        aBodyReanim.assignRenderGroupToPrefix("anim_eye", Reanimation.RENDER_GROUP_HIDDEN);
    }

    /** Plant.cpp:3014-3025. */
    public void endBlink() {
        if (mBlinkReanimID != ReanimationID.REANIMATIONID_NULL) {
            mApp.removeReanimation(mBlinkReanimID);
            mBlinkReanimID = ReanimationID.REANIMATIONID_NULL;

            Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
            if (aBodyReanim != null) {
                aBodyReanim.assignRenderGroupToPrefix("anim_eye", Reanimation.RENDER_GROUP_NORMAL);
            }
        }
    }

    /** Plant.cpp:3027-3049. */
    public void updateBlink() {
        if (mBlinkReanimID != ReanimationID.REANIMATIONID_NULL) {
            Reanimation aBlinkReanim = mApp.reanimationTryToGet(mBlinkReanimID);
            if (aBlinkReanim == null || aBlinkReanim.mLoopCount > 0) {
                endBlink();
            }
        }

        if (mIsAsleep) {
            return;
        }

        if (mBlinkCountdown > 0) {
            mBlinkCountdown--;
            if (mBlinkCountdown == 0) {
                doBlink();
            }
        }
    }

    /** Plant.cpp:3051-3088. */
    public void animateNuts() {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        Image aCracked1;
        Image aCracked2;
        String aTrackToOverride;
        if (mSeedType == SeedType.SEED_WALLNUT) {
            aCracked1 = Resources.getImageById(Resources.IMAGE_REANIM_WALLNUT_CRACKED1_ID);
            aCracked2 = Resources.getImageById(Resources.IMAGE_REANIM_WALLNUT_CRACKED2_ID);
            aTrackToOverride = "anim_face";
        } else if (mSeedType == SeedType.SEED_TALLNUT) {
            aCracked1 = Resources.getImageById(Resources.IMAGE_REANIM_TALLNUT_CRACKED1_ID);
            aCracked2 = Resources.getImageById(Resources.IMAGE_REANIM_TALLNUT_CRACKED2_ID);
            aTrackToOverride = "anim_idle";
        } else {
            return;
        }

        int aPosX = mX + 40;
        int aPosY = mY + 10;
        if (mSeedType == SeedType.SEED_TALLNUT) {
            aPosY -= 32;
        }

        Image aImageOverride = aBodyReanim.getImageOverride(aTrackToOverride);
        if (mPlantHealth < mPlantMaxHealth / 3) {
            if (aImageOverride != aCracked2) {
                aBodyReanim.setImageOverride(aTrackToOverride, aCracked2);
                mApp.addPvzpParticle(aPosX, aPosY, mRenderOrder + 4, ParticleEffect.PARTICLE_WALLNUT_EAT_LARGE);
            }
        } else if (mPlantHealth < mPlantMaxHealth * 2 / 3) {
            if (aImageOverride != aCracked1) {
                aBodyReanim.setImageOverride(aTrackToOverride, aCracked1);
                mApp.addPvzpParticle(aPosX, aPosY, mRenderOrder + 4, ParticleEffect.PARTICLE_WALLNUT_EAT_LARGE);
            }
        } else {
            aBodyReanim.setImageOverride(aTrackToOverride, null);
        }

        if (isInPlay() && !mApp.isIZombieLevel()) {
            if (mRecentlyEatenCountdown > 0) {
                aBodyReanim.mAnimRate = 0.1f;
                return;
            }

            if (aBodyReanim.mAnimRate < 1.0f && mOnBungeeState != PlantOnBungeeState.RISING_WITH_BUNGEE) {
                aBodyReanim.mAnimRate = randRangeFloat(10.0f, 15.0f);
            }
        }
    }

    /** Plant.cpp:3090-3120. */
    public void animateGarlic() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        Image aImageOverride = aBodyReanim.getImageOverride("anim_face");

        if (mPlantHealth < mPlantMaxHealth / 3) {
            if (aImageOverride != Resources.getImageById(Resources.IMAGE_REANIM_GARLIC_BODY3_ID)) {
                aBodyReanim.setImageOverride("anim_face", Resources.getImageById(Resources.IMAGE_REANIM_GARLIC_BODY3_ID));
                aBodyReanim.assignRenderGroupToPrefix("Garlic_stem", Reanimation.RENDER_GROUP_HIDDEN);
            }
        } else if (mPlantHealth < mPlantMaxHealth * 2 / 3) {
            if (aImageOverride != Resources.getImageById(Resources.IMAGE_REANIM_GARLIC_BODY2_ID)) {
                aBodyReanim.setImageOverride("anim_face", Resources.getImageById(Resources.IMAGE_REANIM_GARLIC_BODY2_ID));
            }
        } else {
            aBodyReanim.setImageOverride("anim_face", null);
        }
    }

    /** Plant.cpp:3122-3145. */
    public void animatePumpkin() {
        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        Image aImageOverride = aBodyReanim.getImageOverride("Pumpkin_front");

        if (mPlantHealth < mPlantMaxHealth / 3) {
            if (aImageOverride != Resources.getImageById(Resources.IMAGE_REANIM_PUMPKIN_DAMAGE3_ID)) {
                aBodyReanim.setImageOverride("Pumpkin_front", Resources.getImageById(Resources.IMAGE_REANIM_PUMPKIN_DAMAGE3_ID));
            }
        } else if (mPlantHealth < mPlantMaxHealth * 2 / 3) {
            if (aImageOverride != Resources.getImageById(Resources.IMAGE_REANIM_PUMPKIN_DAMAGE1_ID)) {
                aBodyReanim.setImageOverride("Pumpkin_front", Resources.getImageById(Resources.IMAGE_REANIM_PUMPKIN_DAMAGE1_ID));
            }
        } else {
            aBodyReanim.setImageOverride("Pumpkin_front", null);
        }
    }

    /** Plant.cpp:3147-3430 (`UpdateShooting`). */
    public void updateShooting() {
        if (notOnGround() || mShootingCounter == 0) {
            return;
        }

        mShootingCounter--;

        if (mSeedType == SeedType.SEED_FUMESHROOM && mShootingCounter == 15) {
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, mRow, 0);
            addAttachedParticle(mX + 85, mY + 31, aRenderPosition, ParticleEffect.PARTICLE_FUMECLOUD);
        }

        if (mSeedType == SeedType.SEED_GLOOMSHROOM) {
            if (mShootingCounter == 136 || mShootingCounter == 108 || mShootingCounter == 80 || mShootingCounter == 52) {
                int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, mRow, 0);
                addAttachedParticle(mX + 40, mY + 40, aRenderPosition, ParticleEffect.PARTICLE_GLOOMCLOUD);
            }
            if (mShootingCounter == 126 || mShootingCounter == 98 || mShootingCounter == 70 || mShootingCounter == 42) {
                fire(null, mRow, PlantWeapon.WEAPON_PRIMARY);
            }
        } else if (mSeedType == SeedType.SEED_GATLINGPEA) {
            if (mShootingCounter == 18 || mShootingCounter == 35 || mShootingCounter == 51 || mShootingCounter == 68) {
                fire(null, mRow, PlantWeapon.WEAPON_PRIMARY);
            }
        } else if (mSeedType == SeedType.SEED_CATTAIL) {
            if (mShootingCounter == 19) {
                Zombie aZombie = findTargetZombie(mRow, PlantWeapon.WEAPON_PRIMARY);
                if (aZombie != null) {
                    fire(aZombie, mRow, PlantWeapon.WEAPON_PRIMARY);
                }
            }
        } else if (mShootingCounter == 1) {
            if (mSeedType == SeedType.SEED_THREEPEATER) {
                int rowAbove = mRow - 1;
                int rowBelow = mRow + 1;
                Reanimation aHeadReanim2 = mApp.reanimationGet(mHeadReanimID2);
                Reanimation aHeadReanim3 = mApp.reanimationGet(mHeadReanimID3);
                Reanimation aHeadReanim1 = mApp.reanimationGet(mHeadReanimID);

                if (aHeadReanim1.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD) {
                    fire(null, rowBelow, PlantWeapon.WEAPON_PRIMARY);
                }
                if (aHeadReanim2.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD) {
                    fire(null, mRow, PlantWeapon.WEAPON_PRIMARY);
                }
                if (aHeadReanim3.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD) {
                    fire(null, rowAbove, PlantWeapon.WEAPON_PRIMARY);
                }
            } else if (mSeedType == SeedType.SEED_SPLITPEA) {
                Reanimation aHeadBackReanim = mApp.reanimationTryToGet(mHeadReanimID2);
                Reanimation aHeadFrontReanim = mApp.reanimationTryToGet(mHeadReanimID);
                if (aHeadFrontReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD && mLaunchCounter > 25) {
                    fire(null, mRow, PlantWeapon.WEAPON_PRIMARY);
                }
                if (aHeadBackReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD) {
                    fire(null, mRow, PlantWeapon.WEAPON_SECONDARY);
                }
            } else if (mState == PlantState.STATE_CACTUS_LOW) {
                fire(null, mRow, PlantWeapon.WEAPON_SECONDARY);
            } else if (mSeedType == SeedType.SEED_CABBAGEPULT || mSeedType == SeedType.SEED_KERNELPULT
                    || mSeedType == SeedType.SEED_MELONPULT || mSeedType == SeedType.SEED_WINTERMELON) {
                int aPlantWeapon = PlantWeapon.WEAPON_PRIMARY;
                if (mState == PlantState.STATE_KERNELPULT_BUTTER) {
                    Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
                    aBodyReanim.assignRenderGroupToPrefix("Cornpult_butter", Reanimation.RENDER_GROUP_HIDDEN);
                    aBodyReanim.assignRenderGroupToPrefix("Cornpult_kernal", Reanimation.RENDER_GROUP_NORMAL);
                    mState = PlantState.STATE_NOTREADY;
                    aPlantWeapon = PlantWeapon.WEAPON_SECONDARY;
                }

                Zombie aZombie = findTargetZombie(mRow, aPlantWeapon);
                fire(aZombie, mRow, aPlantWeapon);
            } else {
                fire(null, mRow, PlantWeapon.WEAPON_PRIMARY);
            }

            return;
        }

        if (mShootingCounter != 0) {
            return;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        Reanimation aHeadReanim = mApp.reanimationTryToGet(mHeadReanimID);
        if (mSeedType == SeedType.SEED_THREEPEATER) {
            Reanimation aHeadReanim2 = mApp.reanimationGet(mHeadReanimID2);
            Reanimation aHeadReanim3 = mApp.reanimationGet(mHeadReanimID3);

            if (aHeadReanim2.mLoopCount > 0) {
                if (aHeadReanim.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD) {
                    aHeadReanim.startBlend(20);
                    aHeadReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
                    aHeadReanim.setFramesForLayer("anim_head_idle1");
                    aHeadReanim.mAnimRate = aBodyReanim.mAnimRate;
                    aHeadReanim.mAnimTime = aBodyReanim.mAnimTime;
                }

                aHeadReanim2.startBlend(20);
                aHeadReanim2.mLoopType = ReanimLoopType.REANIM_LOOP;
                aHeadReanim2.setFramesForLayer("anim_head_idle2");
                aHeadReanim2.mAnimRate = aBodyReanim.mAnimRate;
                aHeadReanim2.mAnimTime = aBodyReanim.mAnimTime;

                if (aHeadReanim3.mLoopType == ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD) {
                    aHeadReanim3.startBlend(20);
                    aHeadReanim3.mLoopType = ReanimLoopType.REANIM_LOOP;
                    aHeadReanim3.setFramesForLayer("anim_head_idle3");
                    aHeadReanim3.mAnimRate = aBodyReanim.mAnimRate;
                    aHeadReanim3.mAnimTime = aBodyReanim.mAnimTime;
                }

                return;
            }
        } else if (mSeedType == SeedType.SEED_SPLITPEA) {
            Reanimation aHeadReanim2 = mApp.reanimationGet(mHeadReanimID2);

            if (aHeadReanim.mLoopCount > 0) {
                aHeadReanim.startBlend(20);
                aHeadReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
                aHeadReanim.setFramesForLayer("anim_head_idle");
                aHeadReanim.mAnimRate = aBodyReanim.mAnimRate;
                aHeadReanim.mAnimTime = aBodyReanim.mAnimTime;
            }

            if (aHeadReanim2.mLoopCount > 0) {
                aHeadReanim2.startBlend(20);
                aHeadReanim2.mLoopType = ReanimLoopType.REANIM_LOOP;
                aHeadReanim2.setFramesForLayer("anim_splitpea_idle");
                aHeadReanim2.mAnimRate = aBodyReanim.mAnimRate;
                aHeadReanim2.mAnimTime = aBodyReanim.mAnimTime;
            }

            return;
        } else if (mState == PlantState.STATE_CACTUS_HIGH) {
            if (aBodyReanim.mLoopCount > 0) {
                playBodyReanim("anim_idlehigh", ReanimLoopType.REANIM_LOOP, 20, 0.0f);

                aBodyReanim.mAnimRate = aBodyReanim.mDefinition.mFPS;
                if (mApp.isIZombieLevel()) {
                    aBodyReanim.mAnimRate = 0.0f;
                }

                return;
            }
        } else if (aHeadReanim != null) {
            if (aHeadReanim.mLoopCount > 0) {
                aHeadReanim.startBlend(20);
                aHeadReanim.mLoopType = ReanimLoopType.REANIM_LOOP;
                aHeadReanim.setFramesForLayer("anim_head_idle");
                aHeadReanim.mAnimRate = aBodyReanim.mAnimRate;
                aHeadReanim.mAnimTime = aBodyReanim.mAnimTime;
                return;
            }
        } else if (mSeedType == SeedType.SEED_COBCANNON) {
            if (aBodyReanim.mLoopCount > 0) {
                mState = PlantState.STATE_COBCANNON_ARMING;
                mStateCountdown = 3000;
                playBodyReanim("anim_unarmed_idle", ReanimLoopType.REANIM_LOOP, 20, aBodyReanim.mDefinition.mFPS);
                return;
            }
        } else if (aBodyReanim != null && aBodyReanim.mLoopCount > 0) {
            playIdleAnim(aBodyReanim.mDefinition.mFPS);
            return;
        }

        mShootingCounter = 1;
    }

    /** Plant.cpp:3432-3468. */
    public void animate() {
        if ((mSeedType == SeedType.SEED_CHERRYBOMB || mSeedType == SeedType.SEED_JALAPENO)
                && mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            mShakeOffsetX = randRangeFloat(-1.0f, 1.0f);
            mShakeOffsetY = randRangeFloat(-1.0f, 1.0f);
        }

        if (mRecentlyEatenCountdown > 0) {
            mRecentlyEatenCountdown--;
        }
        if (mEatenFlashCountdown > 0) {
            mEatenFlashCountdown--;
        }
        if (mBeghouledFlashCountdown > 0) {
            mBeghouledFlashCountdown--;
        }

        if (mSquished) {
            mFrame = 0;
            return;
        }

        if (mSeedType == SeedType.SEED_WALLNUT || mSeedType == SeedType.SEED_TALLNUT) {
            animateNuts();
        } else if (mSeedType == SeedType.SEED_GARLIC) {
            animateGarlic();
        } else if (mSeedType == SeedType.SEED_PUMPKINSHELL) {
            animatePumpkin();
        }

        updateBlink();

        if (mAnimPing) {
            if (mAnimCounter < mFrameLength * mNumFrames - 1) {
                mAnimCounter++;
            } else {
                mAnimPing = false;
                mAnimCounter -= mFrameLength;
            }
        } else if (mAnimCounter > 0) {
            mAnimCounter--;
        } else {
            mAnimPing = true;
            mAnimCounter += mFrameLength;
        }
        mFrame = mAnimCounter / mFrameLength;
    }

    /** Plant.cpp:3470-3535 (free function `PlantFlowerPotHeightOffset`). */
    public static float plantFlowerPotHeightOffset(int theSeedType, float theFlowerPotScale) {
        float aHeightOffset = -5.0f * theFlowerPotScale;
        float aScaleOffsetFix = 0.0f;

        switch (theSeedType) {
        case SeedType.SEED_CHOMPER:
        case SeedType.SEED_PLANTERN:
            aHeightOffset -= 5.0f;
            break;
        case SeedType.SEED_SCAREDYSHROOM:
            aHeightOffset += 5.0f;
            aScaleOffsetFix -= 8.0f;
            break;
        case SeedType.SEED_SUNSHROOM:
        case SeedType.SEED_PUFFSHROOM:
            aScaleOffsetFix -= 4.0f;
            break;
        case SeedType.SEED_HYPNOSHROOM:
        case SeedType.SEED_MAGNETSHROOM:
        case SeedType.SEED_PEASHOOTER:
        case SeedType.SEED_REPEATER:
        case SeedType.SEED_LEFTPEATER:
        case SeedType.SEED_SNOWPEA:
        case SeedType.SEED_THREEPEATER:
        case SeedType.SEED_SUNFLOWER:
        case SeedType.SEED_MARIGOLD:
        case SeedType.SEED_CABBAGEPULT:
        case SeedType.SEED_MELONPULT:
        case SeedType.SEED_TANGLEKELP:
        case SeedType.SEED_BLOVER:
        case SeedType.SEED_SPIKEWEED:
            aScaleOffsetFix -= 8.0f;
            break;
        case SeedType.SEED_SEASHROOM:
        case SeedType.SEED_POTATOMINE:
            aScaleOffsetFix -= 4.0f;
            break;
        case SeedType.SEED_LILYPAD:
            aScaleOffsetFix -= 16.0f;
            break;
        case SeedType.SEED_INSTANT_COFFEE:
            aScaleOffsetFix -= 20.0f;
            break;
        default:
            break;
        }

        return aHeightOffset + (theFlowerPotScale * aScaleOffsetFix - aScaleOffsetFix);
    }

    /** Plant.cpp:3537-3649 (free function `PlantDrawHeightOffset`). */
    public static float plantDrawHeightOffset(Board theBoard, Plant thePlant, int theSeedType, int theCol, int theRow) {
        float aHeightOffset = 0.0f;
        Plant aFlowerPot = theBoard != null ? theBoard.getFlowerPotAt(theCol, theRow) : null;

        boolean doFloating = false;
        if (isFlying(theSeedType)) {
            doFloating = false;
        } else if (theBoard == null) {
            if (isAquatic(theSeedType)) {
                doFloating = true;
            }
        } else if (theBoard.isPoolSquare(theCol, theRow)) {
            doFloating = true;
        } else if (thePlant != null && theBoard.mBackground == BackgroundType.BACKGROUND_ZOMBIQUARIUM) {
            doFloating = true;
        }

        if (doFloating) {
            long aCounter = theBoard != null ? theBoard.mMainCounter : LawnApp.gLawnApp.mAppCounter;

            float aPos = theRow * (float) GameConstants.PI + theCol * 0.25f * (float) GameConstants.PI;
            float aTime = (float) (aCounter % 200) * (2.0f * (float) GameConstants.PI / 200.0f);
            float aFloatingHeight = (float) Math.sin(aPos + aTime) * 2.0f;
            aHeightOffset += aFloatingHeight;
        }

        if (theBoard != null && (thePlant == null || !thePlant.mSquished)) {
            Plant aPot = aFlowerPot;
            if (aPot != null && !aPot.mSquished && theSeedType != SeedType.SEED_FLOWERPOT) {
                aHeightOffset += plantFlowerPotHeightOffset(theSeedType, 1.0f);
            }
        }

        if (theSeedType == SeedType.SEED_FLOWERPOT) {
            aHeightOffset += 26.0f;
        } else if (theSeedType == SeedType.SEED_LILYPAD) {
            aHeightOffset += 25.0f;
        } else if (theSeedType == SeedType.SEED_STARFRUIT) {
            aHeightOffset += 10.0f;
        } else if (theSeedType == SeedType.SEED_TANGLEKELP) {
            aHeightOffset += 24.0f;
        } else if (theSeedType == SeedType.SEED_SEASHROOM) {
            aHeightOffset += 28.0f;
        } else if (theSeedType == SeedType.SEED_INSTANT_COFFEE) {
            aHeightOffset -= 20.0f;
        } else if (theSeedType == SeedType.SEED_CACTUS) {
            return aHeightOffset;
        } else if (theSeedType == SeedType.SEED_PUMPKINSHELL) {
            aHeightOffset += 15.0f;
        } else if (theSeedType == SeedType.SEED_PUFFSHROOM) {
            aHeightOffset += 5.0f;
        } else if (theSeedType == SeedType.SEED_SCAREDYSHROOM) {
            aHeightOffset -= 14.0f;
        } else if (theSeedType == SeedType.SEED_GRAVEBUSTER) {
            aHeightOffset -= 40.0f;
        } else if (theSeedType == SeedType.SEED_SPIKEWEED || theSeedType == SeedType.SEED_SPIKEROCK) {
            int aBottomRow = 4;
            if (theBoard != null && theBoard.stageHas6Rows()) {
                aBottomRow = 5;
            }

            if (theSeedType == SeedType.SEED_SPIKEROCK) {
                aHeightOffset += 6.0f;
            }

            if (aFlowerPot != null && LawnApp.gLawnApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
                aHeightOffset += 5.0f;
            } else if (theBoard != null && theBoard.stageHasRoof()) {
                aHeightOffset += 15.0f;
            } else if (theBoard != null && theBoard.isPoolSquare(theCol, theRow)) {
                aHeightOffset += 0.0f;
            } else if (theRow == aBottomRow && theCol >= 7 && theBoard != null && theBoard.stageHas6Rows()) {
                aHeightOffset += 1.0f;
            } else if (theRow == aBottomRow && theCol < 7) {
                aHeightOffset += 12.0f;
            } else {
                aHeightOffset += 15.0f;
            }
        }

        return aHeightOffset;
    }

    /** Plant.cpp:3651-3667. */
    public void getPeaHeadOffset(int[] theOffsetX, int[] theOffsetY) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);

        int aTrackIndex = 0;
        if (aBodyReanim.trackExists("anim_stem")) {
            aTrackIndex = aBodyReanim.findTrackIndex("anim_stem");
        } else if (aBodyReanim.trackExists("anim_idle")) {
            aTrackIndex = aBodyReanim.findTrackIndex("anim_idle");
        }

        ReanimatorTransform aTransform = new ReanimatorTransform();
        aBodyReanim.getCurrentTransform(aTrackIndex, aTransform);
        theOffsetX[0] = (int) aTransform.mTransX;
        theOffsetY[0] = (int) aTransform.mTransY;
    }

    /** Plant.cpp:3669-3801. */
    public void drawMagnetItems(Graphics g) {
        float aOffsetX = 0.0f;
        float aOffsetY = plantDrawHeightOffset(mBoard, this, mSeedType, mPlantCol, mRow);

        for (int i = 0; i < MAX_MAGNET_ITEMS; i++) {
            MagnetItem aMagnetItem = mMagnetItems[i];
            if (aMagnetItem.mItemType != MagnetItemType.MAGNET_ITEM_NONE) {
                int aCelRow = 0;
                int aCelCol = 0;
                Image aImage = null;
                float aScale = 0.8f;

                if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_PAIL_1) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BUCKET1_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_PAIL_2) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BUCKET2_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_PAIL_3) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_BUCKET3_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_FOOTBALL_HELMET_1) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_FOOTBALL_HELMET_2) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET2_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_FOOTBALL_HELMET_3) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET3_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_DOOR_1) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_SCREENDOOR1_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_DOOR_2) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_SCREENDOOR2_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_DOOR_3) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_SCREENDOOR3_ID);
                } else if (aMagnetItem.mItemType >= MagnetItemType.MAGNET_ITEM_POGO_1 && aMagnetItem.mItemType <= MagnetItemType.MAGNET_ITEM_POGO_3) {
                    aCelCol = aMagnetItem.mItemType - MagnetItemType.MAGNET_ITEM_POGO_1;
                    aImage = Resources.getImageById(Resources.IMAGE_ZOMBIEPOGO_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_LADDER_1) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_1_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_LADDER_2) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE1_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_LADDER_3) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE2_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_LADDER_PLACED) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_LADDER_5_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_JACK_IN_THE_BOX) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_JACKBOX_BOX_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_PICK_AXE) {
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_ZOMBIE_DIGGER_PICKAXE_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_SILVER_COIN) {
                    aScale = 1.0f;
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_COIN_SILVER_DOLLAR_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_GOLD_COIN) {
                    aScale = 1.0f;
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_COIN_GOLD_DOLLAR_ID);
                } else if (aMagnetItem.mItemType == MagnetItemType.MAGNET_ITEM_DIAMOND) {
                    aScale = 1.0f;
                    aImage = Resources.getImageById(Resources.IMAGE_REANIM_DIAMOND_ID);
                }

                if (aScale == 1.0f) {
                    g.drawImageCel(aImage, (int) (aMagnetItem.mPosX - mX + aOffsetX), (int) (aMagnetItem.mPosY - mY + aOffsetY), aCelCol, aCelRow);
                } else {
                    PvzpCommon.pvzpDrawImageCelScaledF(g, aImage, aMagnetItem.mPosX - mX + aOffsetX, aMagnetItem.mPosY - mY + aOffsetY, aCelCol, aCelRow, aScale, aScale);
                }
            }
        }
    }

    /** Plant.cpp:3803-3807. */
    public static Image getImage(int theSeedType) {
        Image[] aImages = getPlantDefinition(theSeedType).mPlantImage;
        return aImages != null ? aImages[0] : null;
    }

    /** Plant.cpp:3809-3893 (`DrawShadow`). */
    public void drawShadow(Graphics g, float theOffsetX, float theOffsetY) {
        if (mSeedType == SeedType.SEED_LILYPAD || mSeedType == SeedType.SEED_STARFRUIT || mSeedType == SeedType.SEED_TANGLEKELP
                || mSeedType == SeedType.SEED_SEASHROOM || mSeedType == SeedType.SEED_COBCANNON || mSeedType == SeedType.SEED_SPIKEWEED
                || mSeedType == SeedType.SEED_SPIKEROCK || mSeedType == SeedType.SEED_GRAVEBUSTER || mSeedType == SeedType.SEED_CATTAIL
                || mOnBungeeState == PlantOnBungeeState.RISING_WITH_BUNGEE) {
            return;
        }

        if (isOnBoard() && mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && mApp.mZenGarden.mGardenType == GardenType.GARDEN_MAIN) {
            return;
        }

        int aShadowType = 0;
        float aShadowOffsetX = -3.0f;
        float aShadowOffsetY = 51.0f;
        float aScale = 1.0f;
        if (mBoard != null && mBoard.stageIsNight()) {
            aShadowType = 1;
        }

        if (mSeedType == SeedType.SEED_SQUASH) {
            if (mBoard != null) {
                aShadowOffsetY += mBoard.gridToPixelY(mPlantCol, mRow) - mY;
            }
            aShadowOffsetY += 5.0f;
        } else if (mSeedType == SeedType.SEED_PUFFSHROOM) {
            aScale = 0.5f;
            aShadowOffsetY = 42.0f;
        } else if (mSeedType == SeedType.SEED_SUNSHROOM) {
            aShadowOffsetY = 42.0f;
            if (mState == PlantState.STATE_SUNSHROOM_SMALL) {
                aScale = 0.5f;
            } else if (mState == PlantState.STATE_SUNSHROOM_GROWING) {
                Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
                aScale = 0.5f + 0.5f * aBodyReanim.mAnimTime;
            }
        } else if (mSeedType == SeedType.SEED_UMBRELLA) {
            aScale = 0.5f;
            aShadowOffsetX = -7.0f;
            aShadowOffsetY = 52.0f;
        } else if (mSeedType == SeedType.SEED_FUMESHROOM || mSeedType == SeedType.SEED_GLOOMSHROOM) {
            aScale = 1.3f;
            aShadowOffsetY = 47.0f;
        } else if (mSeedType == SeedType.SEED_CABBAGEPULT || mSeedType == SeedType.SEED_MELONPULT || mSeedType == SeedType.SEED_WINTERMELON) {
            aShadowOffsetY = 47.0f;
        } else if (mSeedType == SeedType.SEED_KERNELPULT) {
            aShadowOffsetX = 0.0f;
            aShadowOffsetY = 47.0f;
        } else if (mSeedType == SeedType.SEED_SCAREDYSHROOM) {
            aShadowOffsetX = -9.0f;
            aShadowOffsetY = 55.0f;
        } else if (mSeedType == SeedType.SEED_CHOMPER) {
            aShadowOffsetX = -21.0f;
            aShadowOffsetY = 57.0f;
        } else if (mSeedType == SeedType.SEED_FLOWERPOT) {
            aShadowOffsetX = -4.0f;
            aShadowOffsetY = 46.0f;
        } else if (mSeedType == SeedType.SEED_TALLNUT) {
            aShadowOffsetY = 54.0f;
            aScale = 1.3f;
        } else if (mSeedType == SeedType.SEED_PUMPKINSHELL) {
            aShadowOffsetY = 46.0f;
            aScale = 1.4f;
        } else if (mSeedType == SeedType.SEED_CACTUS) {
            aShadowOffsetX = -8.0f;
            aShadowOffsetY = 50.0f;
        } else if (mSeedType == SeedType.SEED_PLANTERN) {
            aShadowOffsetY = 57.0f;
        } else if (mSeedType == SeedType.SEED_INSTANT_COFFEE) {
            aShadowOffsetY = 71.0f;
        } else if (mSeedType == SeedType.SEED_GIANT_WALLNUT) {
            aShadowOffsetX = -33.0f;
            aShadowOffsetY = 56.0f;
            aScale = 1.7f;
        }

        if (isFlying(mSeedType)) {
            aShadowOffsetY += 10.0f;
            if (mBoard != null && (mBoard.getTopPlantAt(mPlantCol, mRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION) != null
                    || mBoard.getTopPlantAt(mPlantCol, mRow, PlantPriority.TOPPLANT_ONLY_PUMPKIN) != null)) {
                return;
            }
        }

        if (aShadowType == 0) {
            PvzpCommon.pvzpDrawImageCelCenterScaledF(g, Resources.getImageById(Resources.IMAGE_PLANTSHADOW_ID), theOffsetX + aShadowOffsetX, theOffsetY + aShadowOffsetY, 0, aScale, aScale);
        } else {
            PvzpCommon.pvzpDrawImageCelCenterScaledF(g, Resources.getImageById(Resources.IMAGE_PLANTSHADOW2_ID), theOffsetX + aShadowOffsetX, theOffsetY + aShadowOffsetY, 0, aScale, aScale);
        }
    }

    /** Plant.cpp:3895-4099 (`Draw`). */
    public void draw(Graphics g) {
        float aOffsetX = 0.0f;
        float aOffsetY = plantDrawHeightOffset(mBoard, this, mSeedType, mPlantCol, mRow);
        if (isFlying(mSeedType) && mSquished) {
            aOffsetY += 30.0f;
        }

        int aImageIndex = mFrame;
        Image aPlantImage = getImage(mSeedType);

        if (mSquished) {
            if (mSeedType == SeedType.SEED_FLOWERPOT) {
                aOffsetY -= 15.0f;
            }
            if (mSeedType == SeedType.SEED_INSTANT_COFFEE) {
                aOffsetY -= 20.0f;
            }

            g.setScale(1.0f, 0.25f, 0.0f, 0.0f);
            drawSeedType(g, mSeedType, mImitaterType, DrawVariation.VARIATION_NORMAL, aOffsetX, 60.0f + aOffsetY);
            g.setScale(1.0f, 1.0f, 0.0f, 0.0f);
        } else {
            boolean aDrawPumpkinBack = false;
            Plant aPumpkin = null;

            if (isOnBoard()) {
                aPumpkin = mBoard.getPumpkinAt(mPlantCol, mRow);
                if (aPumpkin != null) {
                    Plant aPlantInPumpkin = mBoard.getTopPlantAt(mPlantCol, mRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
                    if (aPlantInPumpkin != null) {
                        if (aPlantInPumpkin.mRenderOrder > aPumpkin.mRenderOrder
                                || aPlantInPumpkin.mOnBungeeState == PlantOnBungeeState.GETTING_GRABBED_BY_BUNGEE) {
                            aPlantInPumpkin = null;
                        }
                    }

                    if (aPlantInPumpkin == this) {
                        aDrawPumpkinBack = true;
                    }
                    if (aPlantInPumpkin == null && mSeedType == SeedType.SEED_PUMPKINSHELL) {
                        aDrawPumpkinBack = true;
                    }
                } else if (mSeedType == SeedType.SEED_PUMPKINSHELL) {
                    aDrawPumpkinBack = true;
                    aPumpkin = this;
                }
            } else if (mSeedType == SeedType.SEED_PUMPKINSHELL) {
                aDrawPumpkinBack = true;
                aPumpkin = this;
            }

            drawShadow(g, aOffsetX, aOffsetY);

            if (isFlying(mSeedType)) {
                long aCounter = isOnBoard() ? mBoard.mMainCounter : mApp.mAppCounter;

                float aTime = (float) ((mRow * 97.0 + mPlantCol * 61.0 + aCounter) * 0.03 % (2.0 * GameConstants.PI));
                float aWave = (float) Math.sin(aTime) * 2.0f;
                aOffsetY += aWave;
            }

            if (aDrawPumpkinBack) {
                Reanimation aPumpkinReanim = mApp.reanimationGet(aPumpkin.mBodyReanimID);
                Graphics aPumpkinGraphics = new Graphics(g);
                aPumpkinGraphics.mTransX += aPumpkin.mX - mX;
                aPumpkinGraphics.mTransY += aPumpkin.mY - mY;
                aPumpkinReanim.drawRenderGroup(aPumpkinGraphics, 1);
            }

            aOffsetX += mShakeOffsetX;
            aOffsetY += mShakeOffsetY;
            if (isInPlay() && mApp.isIZombieLevel()) {
                mBoard.mChallenge.iZombieDrawPlant(g, this);
            } else if (mBodyReanimID != ReanimationID.REANIMATIONID_NULL) {
                Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
                if (aBodyReanim != null) {
                    if (!mApp.is3DAccelerated() && mSeedType == SeedType.SEED_FLOWERPOT && isOnBoard()
                            && aBodyReanim.mAnimRate == 0.0f && aBodyReanim.isAnimPlaying("anim_idle")) {
                        mApp.mReanimatorCache.drawCachedPlant(g, aOffsetX, aOffsetY, mSeedType, DrawVariation.VARIATION_NORMAL);
                    } else {
                        aBodyReanim.draw(g);
                    }
                }
            } else {
                int aSeedType = SeedType.SEED_NONE;
                if (mBoard != null) {
                    aSeedType = mBoard.getSeedTypeInCursor();
                }

                if (isPartOfUpgradableTo(aSeedType) && mBoard.canPlantAt(mPlantCol, mRow, aSeedType) == PlantingReason.PLANTING_OK) {
                    g.setColorizeImages(true);
                    g.setColor(getFlashingColor(mBoard.mMainCounter, 90));
                } else if (aSeedType == SeedType.SEED_COBCANNON && mBoard.canPlantAt(mPlantCol - 1, mRow, aSeedType) == PlantingReason.PLANTING_OK) {
                    g.setColorizeImages(true);
                    g.setColor(getFlashingColor(mBoard.mMainCounter, 90));
                } else if (mBoard != null && mBoard.mTutorialState == TutorialState.TUTORIAL_SHOVEL_DIG) {
                    g.setColorizeImages(true);
                    g.setColor(getFlashingColor(mBoard.mMainCounter, 90));
                }

                PvzpCommon.pvzpDrawImageCelF(g, aPlantImage, aOffsetX, aOffsetY, aImageIndex, 0);
                g.setColorizeImages(false);
                if (mHighlighted) {
                    g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                    g.setColorizeImages(true);
                    g.setColor(new Color(255, 255, 255, 196));
                    PvzpCommon.pvzpDrawImageCelF(g, aPlantImage, aOffsetX, aOffsetY, aImageIndex, 0);
                    g.setDrawMode(Graphics.DRAWMODE_NORMAL);
                    g.setColorizeImages(false);
                } else if (mEatenFlashCountdown > 0) {
                    g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
                    g.setColorizeImages(true);
                    g.setColor(new Color(255, 255, 255, Math.max(0, Math.min(mEatenFlashCountdown * 3, 255))));
                    PvzpCommon.pvzpDrawImageCelF(g, aPlantImage, aOffsetX, aOffsetY, aImageIndex, 0);
                    g.setDrawMode(Graphics.DRAWMODE_NORMAL);
                    g.setColorizeImages(false);
                }
            }

            if (mSeedType == SeedType.SEED_MAGNETSHROOM && !drawMagnetItemsOnTop()) {
                drawMagnetItems(g);
            }
        }
    }

    /** Plant.cpp:4101-4178 (`DrawSeedType`). */
    public static void drawSeedType(Graphics g, int theSeedType, int theImitaterType, int theDrawVariation, float thePosX, float thePosY) {
        Graphics aSeedG = new Graphics(g);
        int aCelRow = 0;
        int aCelCol = 2;
        float aOffsetX = 0.0f;
        float aOffsetY = 0.0f;
        int aSeedType = theSeedType;
        int aDrawVariation = theDrawVariation;

        if (theSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            aSeedType = theImitaterType;
            aDrawVariation = DrawVariation.VARIATION_IMITATER;
            if (theImitaterType == SeedType.SEED_HYPNOSHROOM || theImitaterType == SeedType.SEED_SQUASH || theImitaterType == SeedType.SEED_POTATOMINE
                    || theImitaterType == SeedType.SEED_GARLIC || theImitaterType == SeedType.SEED_LILYPAD) {
                aDrawVariation = DrawVariation.VARIATION_IMITATER_LESS;
            }
        } else if (theDrawVariation == DrawVariation.VARIATION_NORMAL && theSeedType == SeedType.SEED_TANGLEKELP) {
            aDrawVariation = DrawVariation.VARIATION_AQUARIUM;
        }

        if (LawnApp.gLawnApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BIG_TIME
                && (aSeedType == SeedType.SEED_WALLNUT || aSeedType == SeedType.SEED_SUNFLOWER || aSeedType == SeedType.SEED_MARIGOLD)) {
            aSeedG.mScaleX *= 1.5f;
            aSeedG.mScaleY *= 1.5f;
            aOffsetX = -20.0f;
            aOffsetY = -40.0f;
        }
        if (aSeedType == SeedType.SEED_LEFTPEATER) {
            aOffsetX += aSeedG.mScaleX * 80.0f;
            aSeedG.mScaleX *= -1.0f;
        }

        if (Challenge.isZombieSeedType(aSeedType) != 0) {
            int aZombieType = Challenge.iZombieSeedTypeToZombieType(aSeedType);
            if (aZombieType == ZombieType.ZOMBIE_DANCER) {
                aSeedG.mScaleX *= 0.8f;
                aSeedG.mScaleY *= 0.8f;
                aOffsetX = 20.0f;
                aOffsetY = 42.0f;
            }
            LawnApp.gLawnApp.mReanimatorCache.drawCachedZombie(aSeedG, thePosX + aOffsetX, thePosY + aOffsetY, aZombieType);
        } else {
            PlantDefinition aPlantDef = getPlantDefinition(aSeedType);

            if (aSeedType == SeedType.SEED_GIANT_WALLNUT) {
                aSeedG.mScaleX *= 1.4f;
                aSeedG.mScaleY *= 1.4f;
                PvzpCommon.pvzpDrawImageScaledF(aSeedG, Resources.getImageById(Resources.IMAGE_REANIM_WALLNUT_BODY_ID), thePosX - 53.0f, thePosY - 56.0f, aSeedG.mScaleX, aSeedG.mScaleY);
            } else if (aPlantDef.mReanimationType != ReanimationType.REANIM_NONE) {
                LawnApp.gLawnApp.mReanimatorCache.drawCachedPlant(aSeedG, thePosX + aOffsetX, thePosY + aOffsetY, aSeedType, aDrawVariation);
            } else {
                if (aSeedType == SeedType.SEED_KERNELPULT) {
                    aCelRow = 2;
                } else if (aSeedType == SeedType.SEED_TWINSUNFLOWER) {
                    aCelRow = 1;
                }

                Image aPlantImage = getImage(aSeedType);
                if (aPlantImage.mNumCols <= 2) {
                    aCelCol = aPlantImage.mNumCols - 1;
                }

                PvzpCommon.pvzpDrawImageCelScaledF(aSeedG, aPlantImage, thePosX + aOffsetX, thePosY + aOffsetY, aCelCol, aCelRow, aSeedG.mScaleX, aSeedG.mScaleY);
            }
        }
    }

    /** Plant.cpp:4180-4201. */
    public void mouseDown(int x, int y, int theClickCount) {
        if (theClickCount < 0) {
            return;
        }

        if (mState == PlantState.STATE_COBCANNON_READY) {
            mBoard.clearCursor();
            mBoard.mCursorObject.mType = SeedType.SEED_NONE;
            mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_COBCANNON_TARGET;
            mBoard.mCursorObject.mSeedBankIndex = -1;
            mBoard.mCursorObject.mCoinID = CoinID.COINID_NULL;
            mBoard.mCursorObject.mCobCannonPlantID = mBoard.mPlants.dataArrayGetID(this);
            mBoard.mCobCannonCursorDelayCounter = 30;
            mBoard.mCobCannonMouseX = x;
            mBoard.mCobCannonMouseY = y;
        }
    }

    /** Plant.cpp:4203-4224. */
    public void iceZombies() {
        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            aZombie.hitIceTrap();
        }

        mBoard.mIceTrapCounter = 300;
        PvzpParticleSystem aPoolSparklyParticle = mApp.particleTryToGet(mBoard.mPoolSparklyParticleID);
        if (aPoolSparklyParticle != null) {
            aPoolSparklyParticle.mDontUpdate = false;
        }

        Zombie aBossZombie = mBoard.getBossZombie();
        if (aBossZombie != null) {
            aBossZombie.bossDestroyFireball();
        }
    }

    /** Plant.cpp:4226-4256. */
    public void burnRow(int theRow) {
        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if ((aZombie.mZombieType == ZombieType.ZOMBIE_BOSS || aZombie.mRow == theRow) && aZombie.effectedByDamage(aDamageRangeFlags)) {
                aZombie.removeColdEffects();
                aZombie.applyBurn();
            }
        }

        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridY == theRow && aGridItem.mGridItemType == GridItemType.GRIDITEM_LADDER) {
                aGridItem.gridItemDie();
            }
        }

        Zombie aBossZombie = mBoard.getBossZombie();
        if (aBossZombie != null && aBossZombie.mFireballRow == theRow) {
            aBossZombie.bossDestroyIceballInRow();
        }
    }

    /** Plant.cpp:4258-4276. */
    public void blowAwayFliers() {
        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying()) {
                // Blow away only flying balloons here; balloons mid-pop are excluded
                if (aZombie.mZombiePhase == ZombiePhase.PHASE_BALLOON_FLYING) {
                    aZombie.mBlowingAway = true;
                }
            }
        }

        mApp.playSample(Resources.SOUND_BLOVER_ID);
        mBoard.mFogBlownCountDown = 4000;
    }

    /** Plant.cpp:4278-4289. */
    public void killAllPlantsNearDoom() {
        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow == mRow && aPlant.mPlantCol == mPlantCol) {
                aPlant.die();
            }
        }
    }

    /** Plant.cpp:4291-4404 (`DoSpecial`). */
    public void doSpecial() {
        int aPosX = mX + mWidth / 2;
        int aPosY = mY + mHeight / 2;
        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);

        switch (mSeedType) {
        case SeedType.SEED_BLOVER:
            if (mState != PlantState.STATE_DOINGSPECIAL) {
                mState = PlantState.STATE_DOINGSPECIAL;
                blowAwayFliers();
            }
            break;
        case SeedType.SEED_CHERRYBOMB:
            mApp.playFoley(FoleyType.FOLEY_CHERRYBOMB);
            mApp.playFoley(FoleyType.FOLEY_JUICY);

            if (mBoard.killAllZombiesInRadius(mRow, aPosX, aPosY, 115, 1, true, aDamageRangeFlags) >= 10) {
                ReportAchievement.giveAchievement(mApp, AchievementId.Explodonator, true);
            }

            mApp.addPvzpParticle(aPosX, aPosY, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_POWIE);
            mBoard.shakeBoard(3, -4);

            die();
            break;
        case SeedType.SEED_DOOMSHROOM:
            mApp.playSample(Resources.SOUND_DOOMSHROOM_ID);

            mBoard.killAllZombiesInRadius(mRow, aPosX, aPosY, 250, 3, true, aDamageRangeFlags);
            killAllPlantsNearDoom();

            mApp.addPvzpParticle(aPosX, aPosY, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_DOOM);
            mBoard.addACrater(mPlantCol, mRow).mGridItemCounter = 18000;
            mBoard.shakeBoard(3, -4);

            die();
            break;
        case SeedType.SEED_JALAPENO:
            mApp.playFoley(FoleyType.FOLEY_JALAPENO_IGNITE);
            mApp.playFoley(FoleyType.FOLEY_JUICY);

            mBoard.doFwoosh(mRow);
            mBoard.shakeBoard(3, -4);

            burnRow(mRow);
            mBoard.mIceTimer[mRow] = 20;

            die();
            break;
        case SeedType.SEED_UMBRELLA:
            if (mState != PlantState.STATE_UMBRELLA_TRIGGERED && mState != PlantState.STATE_UMBRELLA_REFLECTING) {
                mState = PlantState.STATE_UMBRELLA_TRIGGERED;
                mStateCountdown = 5;

                playBodyReanim("anim_block", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 22.0f);
            }
            break;
        case SeedType.SEED_ICESHROOM:
            mApp.playFoley(FoleyType.FOLEY_FROZEN);
            iceZombies();
            mApp.addPvzpParticle(aPosX, aPosY, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_ICE_TRAP);

            die();
            break;
        case SeedType.SEED_POTATOMINE:
            aPosX = mX + mWidth / 2 - 20;
            aPosY = mY + mHeight / 2;

            mApp.playSample(Resources.SOUND_POTATO_MINE_ID);
            if (mBoard.killAllZombiesInRadius(mRow, aPosX, aPosY, 60, 0, false, aDamageRangeFlags) >= 1) {
                ReportAchievement.giveAchievement(mApp, AchievementId.Spudow, true);
            }

            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, mRow, 0);
            mApp.addPvzpParticle((int) (aPosX + 20.0f), aPosY, aRenderPosition, ParticleEffect.PARTICLE_POTATO_MINE);
            mBoard.shakeBoard(3, -4);

            die();
            break;
        case SeedType.SEED_INSTANT_COFFEE: {
            Plant aPlant = mBoard.getTopPlantAt(mPlantCol, mRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            if (aPlant != null && aPlant.mIsAsleep) {
                aPlant.mWakeUpCounter = 100;
            }

            mState = PlantState.STATE_DOINGSPECIAL;
            playBodyReanim("anim_crumble", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 22.0f);
            mApp.playFoley(FoleyType.FOLEY_COFFEE);
            break;
        }
        default:
            break;
        }
    }

    /** Plant.cpp:4406-4434. */
    public void imitaterMorph() {
        die();
        Plant aPlant = mBoard.addPlant(mPlantCol, mRow, mImitaterType, SeedType.SEED_IMITATER);

        int aFilter = FilterEffect.FILTER_EFFECT_WASHED_OUT;
        if (mImitaterType == SeedType.SEED_HYPNOSHROOM || mImitaterType == SeedType.SEED_SQUASH || mImitaterType == SeedType.SEED_POTATOMINE
                || mImitaterType == SeedType.SEED_GARLIC || mImitaterType == SeedType.SEED_LILYPAD) {
            aFilter = FilterEffect.FILTER_EFFECT_LESS_WASHED_OUT;
        }

        Reanimation aBodyReanim = mApp.reanimationTryToGet(aPlant.mBodyReanimID);
        if (aBodyReanim != null) {
            aBodyReanim.mFilterEffect = aFilter;
        }
        Reanimation aHeadReanim = mApp.reanimationTryToGet(aPlant.mHeadReanimID);
        if (aHeadReanim != null) {
            aHeadReanim.mFilterEffect = aFilter;
        }
        Reanimation aHeadReanim2 = mApp.reanimationTryToGet(aPlant.mHeadReanimID2);
        if (aHeadReanim2 != null) {
            aHeadReanim2.mFilterEffect = aFilter;
        }
        Reanimation aHeadReanim3 = mApp.reanimationTryToGet(aPlant.mHeadReanimID3);
        if (aHeadReanim3 != null) {
            aHeadReanim3.mFilterEffect = aFilter;
        }
    }

    /** Plant.cpp:4436-4458. */
    public void updateImitater() {
        if (mState != PlantState.STATE_IMITATER_MORPHING) {
            if (mStateCountdown == 0) {
                mState = PlantState.STATE_IMITATER_MORPHING;
                playBodyReanim("anim_explode", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 26.0f);
            }
        } else {
            Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
            if (aBodyReanim.shouldTriggerTimedEvent(0.8f)) {
                mApp.addPvzpParticle(mX + 40, mY + 40, RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_IMITATER_MORPH);
            }
            if (aBodyReanim.mLoopCount > 0) {
                imitaterMorph();
            }
        }
    }

    /** Plant.cpp:4460-4474. */
    public void cobCannonFire(int theTargetX, int theTargetY) {
        mState = PlantState.STATE_COBCANNON_FIRING;
        mShootingCounter = 206;
        playBodyReanim("anim_shooting", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 20, 12.0f);

        mTargetX = theTargetX - 47;
        mTargetY = theTargetY;

        Reanimation aBodyReanim = mApp.reanimationGet(mBodyReanimID);
        ReanimatorTrackInstance aTrackInstance = aBodyReanim.getTrackInstanceByName("CobCannon_Cob");
        aTrackInstance.mTrackColor = Color.White;
    }

    /** Plant.cpp:4476-4770 (`Fire`). */
    public void fire(Zombie theTargetZombie, int theRow, int thePlantWeapon) {
        if (mSeedType == SeedType.SEED_FUMESHROOM) {
            doRowAreaDamage(20, 2);
            mApp.playFoley(FoleyType.FOLEY_FUME);
            return;
        }
        if (mSeedType == SeedType.SEED_GLOOMSHROOM) {
            doRowAreaDamage(20, 2);
            return;
        }
        if (mSeedType == SeedType.SEED_STARFRUIT) {
            starFruitFire();
            return;
        }

        int aProjectileType;
        switch (mSeedType) {
        case SeedType.SEED_PEASHOOTER:
        case SeedType.SEED_REPEATER:
        case SeedType.SEED_THREEPEATER:
        case SeedType.SEED_SPLITPEA:
        case SeedType.SEED_GATLINGPEA:
        case SeedType.SEED_LEFTPEATER:
            aProjectileType = ProjectileType.PROJECTILE_PEA;
            break;
        case SeedType.SEED_SNOWPEA:
            aProjectileType = ProjectileType.PROJECTILE_SNOWPEA;
            break;
        case SeedType.SEED_PUFFSHROOM:
        case SeedType.SEED_SCAREDYSHROOM:
        case SeedType.SEED_SEASHROOM:
            aProjectileType = ProjectileType.PROJECTILE_PUFF;
            break;
        case SeedType.SEED_CACTUS:
        case SeedType.SEED_CATTAIL:
            aProjectileType = ProjectileType.PROJECTILE_SPIKE;
            break;
        case SeedType.SEED_CABBAGEPULT:
            aProjectileType = ProjectileType.PROJECTILE_CABBAGE;
            break;
        case SeedType.SEED_KERNELPULT:
            aProjectileType = ProjectileType.PROJECTILE_KERNEL;
            break;
        case SeedType.SEED_MELONPULT:
            aProjectileType = ProjectileType.PROJECTILE_MELON;
            break;
        case SeedType.SEED_WINTERMELON:
            aProjectileType = ProjectileType.PROJECTILE_WINTERMELON;
            break;
        case SeedType.SEED_COBCANNON:
            aProjectileType = ProjectileType.PROJECTILE_COBBIG;
            break;
        default:
            throw new IllegalStateException("Fire() on a plant with no projectile: " + mSeedType);
        }
        if (mSeedType == SeedType.SEED_KERNELPULT && thePlantWeapon == PlantWeapon.WEAPON_SECONDARY) {
            aProjectileType = ProjectileType.PROJECTILE_BUTTER;
        }

        mApp.playFoley(FoleyType.FOLEY_THROW);
        if (mSeedType == SeedType.SEED_SNOWPEA || mSeedType == SeedType.SEED_WINTERMELON) {
            mApp.playFoley(FoleyType.FOLEY_SNOW_PEA_SPARKLES);
        } else if (mSeedType == SeedType.SEED_PUFFSHROOM || mSeedType == SeedType.SEED_SCAREDYSHROOM || mSeedType == SeedType.SEED_SEASHROOM) {
            mApp.playFoley(FoleyType.FOLEY_PUFF);
        }

        int aOriginX;
        int aOriginY;
        if (mSeedType == SeedType.SEED_PUFFSHROOM) {
            aOriginX = mX + 40;
            aOriginY = mY + 40;
        } else if (mSeedType == SeedType.SEED_SEASHROOM) {
            aOriginX = mX + 45;
            aOriginY = mY + 63;
        } else if (mSeedType == SeedType.SEED_CABBAGEPULT) {
            aOriginX = mX + 5;
            aOriginY = mY - 12;
        } else if (mSeedType == SeedType.SEED_MELONPULT || mSeedType == SeedType.SEED_WINTERMELON) {
            aOriginX = mX + 25;
            aOriginY = mY - 46;
        } else if (mSeedType == SeedType.SEED_CATTAIL) {
            aOriginX = mX + 20;
            aOriginY = mY - 3;
        } else if (mSeedType == SeedType.SEED_KERNELPULT && thePlantWeapon == PlantWeapon.WEAPON_PRIMARY) {
            aOriginX = mX + 19;
            aOriginY = mY - 37;
        } else if (mSeedType == SeedType.SEED_KERNELPULT && thePlantWeapon == PlantWeapon.WEAPON_SECONDARY) {
            aOriginX = mX + 12;
            aOriginY = mY - 56;
        } else if (mSeedType == SeedType.SEED_PEASHOOTER || mSeedType == SeedType.SEED_SNOWPEA || mSeedType == SeedType.SEED_REPEATER) {
            int[] aOffsetX = new int[2];
            int[] aOffsetY = new int[2];
            getPeaHeadOffset(aOffsetX, aOffsetY);
            aOriginX = mX + aOffsetX[0] + 24;
            aOriginY = mY + aOffsetY[0] - 33;
        } else if (mSeedType == SeedType.SEED_LEFTPEATER) {
            int[] aOffsetX = new int[2];
            int[] aOffsetY = new int[2];
            getPeaHeadOffset(aOffsetX, aOffsetY);
            aOriginX = mX - aOffsetX[0] + 27;
            aOriginY = mY + aOffsetY[0] - 33;
        } else if (mSeedType == SeedType.SEED_GATLINGPEA) {
            int[] aOffsetX = new int[2];
            int[] aOffsetY = new int[2];
            getPeaHeadOffset(aOffsetX, aOffsetY);
            aOriginX = mX + aOffsetX[0] + 34;
            aOriginY = mY + aOffsetY[0] - 33;
        } else if (mSeedType == SeedType.SEED_SPLITPEA) {
            int[] aOffsetX = new int[2];
            int[] aOffsetY = new int[2];
            getPeaHeadOffset(aOffsetX, aOffsetY);
            aOriginY = mY + aOffsetY[0] - 33;

            if (thePlantWeapon == PlantWeapon.WEAPON_SECONDARY) {
                aOriginX = mX + aOffsetX[0] - 64;
            } else {
                aOriginX = mX + aOffsetX[0] + 24;
            }
        } else if (mSeedType == SeedType.SEED_THREEPEATER) {
            aOriginX = mX + 45;
            aOriginY = mY + 10;
        } else if (mSeedType == SeedType.SEED_SCAREDYSHROOM) {
            aOriginX = mX + 29;
            aOriginY = mY + 21;
        } else if (mSeedType == SeedType.SEED_CACTUS) {
            if (thePlantWeapon == PlantWeapon.WEAPON_PRIMARY) {
                aOriginX = mX + 93;
                aOriginY = mY - 50;
            } else {
                aOriginX = mX + 70;
                aOriginY = mY + 23;
            }
        } else if (mSeedType == SeedType.SEED_COBCANNON) {
            aOriginX = mX - 44;
            aOriginY = mY - 184;
        } else {
            aOriginX = mX + 10;
            aOriginY = mY + 5;
        }
        if (mBoard.getFlowerPotAt(mPlantCol, mRow) != null) {
            aOriginY -= 5;
        }

        if (mSeedType == SeedType.SEED_SNOWPEA) {
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, mRow, 1);
            mApp.addPvzpParticle(aOriginX + 8, aOriginY + 13, aRenderPosition, ParticleEffect.PARTICLE_SNOWPEA_PUFF);
        } else if (mSeedType == SeedType.SEED_PUFFSHROOM) {
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, mRow, 1);
            mApp.addPvzpParticle(aOriginX + 18, aOriginY + 13, aRenderPosition, ParticleEffect.PARTICLE_PUFFSHROOM_MUZZLE);
        } else if (mSeedType == SeedType.SEED_SCAREDYSHROOM) {
            int aRenderPosition = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_LAWN_MOWER, mRow, 1);
            mApp.addPvzpParticle(aOriginX + 27, aOriginY + 13, aRenderPosition, ParticleEffect.PARTICLE_PUFFSHROOM_MUZZLE);
        }

        Projectile aProjectile = mBoard.addProjectile(aOriginX, aOriginY, mRenderOrder - 1, theRow, aProjectileType);
        aProjectile.mDamageRangeFlags = getDamageRangeFlags(thePlantWeapon);

        if (mSeedType == SeedType.SEED_CABBAGEPULT || mSeedType == SeedType.SEED_KERNELPULT
                || mSeedType == SeedType.SEED_MELONPULT || mSeedType == SeedType.SEED_WINTERMELON) {
            float aRangeX;
            float aRangeY;
            if (theTargetZombie != null) {
                Rect aZombieRect = theTargetZombie.getZombieRect();
                aRangeX = theTargetZombie.zombieTargetLeadX(50.0f) - aOriginX - 30.0f;
                aRangeY = aZombieRect.mY - aOriginY;

                if (theTargetZombie.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING) {
                    aRangeX -= 60.0f;
                }
                if (theTargetZombie.mZombieType == ZombieType.ZOMBIE_POGO && theTargetZombie.mHasObject) {
                    aRangeX -= 60.0f;
                }
                if (theTargetZombie.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL) {
                    aRangeX -= 40.0f;
                }
                if (theTargetZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                    aRangeY = mBoard.gridToPixelY(8, mRow) - aOriginY;
                }
            } else {
                aRangeX = 700.0f - aOriginX;
                aRangeY = 0.0f;
            }
            if (aRangeX < 40.0f) {
                aRangeX = 40.0f;
            }

            aProjectile.mMotionType = ProjectileMotion.MOTION_LOBBED;
            aProjectile.mVelX = aRangeX / 120.0f;
            aProjectile.mVelY = 0.0f;
            aProjectile.mVelZ = aRangeY / 120.0f - 7.0f;
            aProjectile.mAccZ = 0.115f;
        } else if (mSeedType == SeedType.SEED_THREEPEATER) {
            if (theRow < mRow) {
                aProjectile.mMotionType = ProjectileMotion.MOTION_THREEPEATER;
                aProjectile.mVelY = -3.0f;
                aProjectile.mShadowY += 80.0f;
            } else if (theRow > mRow) {
                aProjectile.mMotionType = ProjectileMotion.MOTION_THREEPEATER;
                aProjectile.mVelY = 3.0f;
                aProjectile.mShadowY -= 80.0f;
            }
        } else if (mSeedType == SeedType.SEED_PUFFSHROOM || mSeedType == SeedType.SEED_SEASHROOM) {
            aProjectile.mMotionType = ProjectileMotion.MOTION_PUFF;
        } else if (mSeedType == SeedType.SEED_SPLITPEA && thePlantWeapon == PlantWeapon.WEAPON_SECONDARY) {
            aProjectile.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
        } else if (mSeedType == SeedType.SEED_LEFTPEATER) {
            aProjectile.mMotionType = ProjectileMotion.MOTION_BACKWARDS;
        } else if (mSeedType == SeedType.SEED_CATTAIL) {
            aProjectile.mVelX = 2.0f;
            aProjectile.mMotionType = ProjectileMotion.MOTION_HOMING;
            aProjectile.mTargetZombieID = mBoard.zombieGetID(theTargetZombie);
        } else if (mSeedType == SeedType.SEED_COBCANNON) {
            aProjectile.mVelX = 0.001f;
            aProjectile.mDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
            aProjectile.mMotionType = ProjectileMotion.MOTION_LOBBED;
            aProjectile.mVelY = 0.0f;
            aProjectile.mAccZ = 0.0f;
            aProjectile.mVelZ = -8.0f;
            aProjectile.mCobTargetX = mTargetX - 40;
            aProjectile.mCobTargetRow = mBoard.pixelToGridYKeepOnBoard(mTargetX, mTargetY);
        }
    }

    /** Plant.cpp:4772-4907 (`FindTargetZombie`). */
    public Zombie findTargetZombie(int theRow, int thePlantWeapon) {
        int aDamageRangeFlags = getDamageRangeFlags(thePlantWeapon);
        Rect aAttackRect = getPlantAttackRect(thePlantWeapon);
        int aHighestWeight = 0;
        Zombie aBestZombie = null;

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            int aRowDeviation = aZombie.mRow - theRow;
            if (aZombie.mZombieType == ZombieType.ZOMBIE_BOSS) {
                aRowDeviation = 0;
            }

            if (!aZombie.mHasHead || aZombie.isTangleKelpTarget()) {
                if (mSeedType == SeedType.SEED_POTATOMINE || mSeedType == SeedType.SEED_CHOMPER || mSeedType == SeedType.SEED_TANGLEKELP) {
                    continue;
                }
            }

            boolean needPortalCheck = false;
            if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
                if (mSeedType == SeedType.SEED_PEASHOOTER || mSeedType == SeedType.SEED_CACTUS || mSeedType == SeedType.SEED_REPEATER) {
                    needPortalCheck = true;
                }
            }

            if (mSeedType != SeedType.SEED_CATTAIL) {
                if (mSeedType == SeedType.SEED_GLOOMSHROOM) {
                    if (aRowDeviation < -1 || aRowDeviation > 1) {
                        continue;
                    }
                } else if (needPortalCheck) {
                    if (mBoard.mChallenge.canTargetZombieWithPortals(this, aZombie) == 0) {
                        continue;
                    }
                } else if (aRowDeviation != 0) {
                    continue;
                }
            }

            if (aZombie.effectedByDamage(aDamageRangeFlags)) {
                int aExtraRange = 0;

                if (mSeedType == SeedType.SEED_CHOMPER) {
                    if (aZombie.mZombiePhase == ZombiePhase.PHASE_DIGGER_WALKING) {
                        aAttackRect.mX += 20;
                        aAttackRect.mWidth -= 20;
                    }

                    if (aZombie.mZombiePhase == ZombiePhase.PHASE_POGO_BOUNCING
                            || (aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE && aZombie.mTargetCol == mPlantCol)) {
                        continue;
                    }

                    if (aZombie.mIsEating || mState == PlantState.STATE_CHOMPER_BITING) {
                        aExtraRange = 60;
                    }
                }

                if (mSeedType == SeedType.SEED_POTATOMINE) {
                    if ((aZombie.mZombieType == ZombieType.ZOMBIE_POGO && aZombie.mHasObject)
                            || aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT || aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT) {
                        continue;
                    }

                    if (aZombie.mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
                        aAttackRect.mX += 40;
                        // classic potato mine quirk; lets pole-vaulters set off the mine ("four pole-vaulters" trick)
                        aAttackRect.mWidth -= 40;
                    }

                    if (aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE && aZombie.mTargetCol != mPlantCol) {
                        continue;
                    }

                    if (aZombie.mIsEating) {
                        aExtraRange = 30;
                    }
                }

                if ((mSeedType == SeedType.SEED_EXPLODE_O_NUT && aZombie.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT)
                        || (mSeedType == SeedType.SEED_TANGLEKELP && !aZombie.mInPool)) {
                    continue;
                }

                Rect aZombieRect = aZombie.getZombieRect();
                if (!needPortalCheck && Board.getRectOverlap(aAttackRect, aZombieRect) < -aExtraRange) {
                    continue;
                }

                int aWeight = -aZombieRect.mX;
                if (mSeedType == SeedType.SEED_CATTAIL) {
                    aWeight = (int) -distance2D(mX + 40.0f, mY + 40.0f, aZombieRect.mX + aZombieRect.mWidth / 2, aZombieRect.mY + aZombieRect.mHeight / 2);
                    if (aZombie.isFlying()) {
                        aWeight += 10000;
                    }
                }

                if (aBestZombie == null || aWeight > aHighestWeight) {
                    aHighestWeight = aWeight;
                    aBestZombie = aZombie;
                }
            }
        }

        return aBestZombie;
    }

    /** Plant.cpp:4909-4929. */
    public int distanceToClosestZombie() {
        int aDamageRangeFlags = getDamageRangeFlags(PlantWeapon.WEAPON_PRIMARY);
        Rect aAttackRect = getPlantAttackRect(PlantWeapon.WEAPON_PRIMARY);
        int aClosestDistance = 1000;

        for (Zombie aZombie : mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mRow == mRow && aZombie.effectedByDamage(aDamageRangeFlags)) {
                Rect aZombieRect = aZombie.getZombieRect();
                int aDistance = -Board.getRectOverlap(aAttackRect, aZombieRect);
                if (aDistance < aClosestDistance) {
                    aClosestDistance = Math.max(aDistance, 0);
                }
            }
        }

        return aClosestDistance;
    }

    /**
     * Java port addition: upstream has no single plant-damage entry point (Zombie::EatPlant and
     * Projectile write {@code mPlantHealth} directly). Funnelling those writes through here lets the
     * PlantDamage hook rewrite the amount (docs/research/03-board-core.md §7.4).
     */
    public void takeDamage(int theDamage) {
        mPlantHealth -= LawnHooksRegistry.dispatcher().onPlantDamage(mBoard, this, theDamage);
    }

    /** Plant.cpp:4931-4964. */
    public void die() {
        die(PLANT_REMOVED_OTHER);
    }

    /**
     * {@link #die()} with an explicit {@code LawnHooks.onPlantRemoved} cause; callers that know why
     * the plant is going away (shovel, eaten, ...) use this overload.
     */
    public void die(int theCause) {
        if (isOnBoard() && mSeedType == SeedType.SEED_TANGLEKELP) {
            Zombie aZombie = mBoard.zombieTryToGet(mTargetZombieID);
            if (aZombie != null) {
                aZombie.dieWithLoot();
            }
        }

        mDead = true;
        removeEffects();

        if (!isFlying(mSeedType) && isOnBoard()) {
            GridItem aLadder = mBoard.getLadderAt(mPlantCol, mRow);
            if (aLadder != null) {
                aLadder.gridItemDie();
            }
        }

        if (isOnBoard()) {
            Plant aTopPlant = mBoard.getTopPlantAt(mPlantCol, mRow, PlantPriority.TOPPLANT_BUNGEE_ORDER);
            Plant aFlowerPot = mBoard.getFlowerPotAt(mPlantCol, mRow);
            if (aFlowerPot != null && aTopPlant == aFlowerPot) {
                Reanimation aPotReanim = mApp.reanimationGet(aFlowerPot.mBodyReanimID);
                aPotReanim.mAnimRate = randRangeFloat(10.0f, 15.0f);
            }
        }

        LawnHooksRegistry.dispatcher().onPlantRemoved(mBoard, this, theCause);
    }

    /**
     * Plant.cpp:4966-4972 — the definition of a seed type, or {@code null} when the seed type has
     * none.
     *
     * <p>A row that is still empty is a seed type nobody defined, and an id outside the seed-type id
     * space has no row at all; both answer {@code null} rather than a row, so a caller can tell
     * "this seed type has no definition" from "this seed type is defined" without having to know
     * which ids are in use.
     */
    public static PlantDefinition getPlantDefinition(int theSeedType) {
        if (theSeedType < 0 || theSeedType >= gPlantDefs.length) {
            return null;
        }

        PlantDefinition aDef = gPlantDefs[theSeedType];
        if (aDef == null) {
            return null;
        }

        if (aDef.mSeedType != theSeedType) {
            throw new IllegalStateException("gPlantDefs is not index-aligned at " + theSeedType);
        }
        return aDef;
    }

    /** Plant.cpp:4974-5037. */
    public static int getCost(int theSeedType, int theImitaterType) {
        if (LawnApp.gLawnApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || LawnApp.gLawnApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            if (theSeedType == SeedType.SEED_REPEATER) {
                return 1000;
            } else if (theSeedType == SeedType.SEED_FUMESHROOM) {
                return 500;
            } else if (theSeedType == SeedType.SEED_TALLNUT) {
                return 250;
            } else if (theSeedType == SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE) {
                return 100;
            } else if (theSeedType == SeedType.SEED_BEGHOULED_BUTTON_CRATER) {
                return 200;
            }
        }

        switch (theSeedType) {
        case SeedType.SEED_SLOT_MACHINE_SUN:
            return 0;
        case SeedType.SEED_SLOT_MACHINE_DIAMOND:
            return 0;
        case SeedType.SEED_ZOMBIQUARIUM_SNORKLE:
            return 100;
        case SeedType.SEED_ZOMBIQUARIUM_TROPHY:
            return 1000;
        case SeedType.SEED_ZOMBIE_NORMAL:
            return 50;
        case SeedType.SEED_ZOMBIE_TRAFFIC_CONE:
            return 75;
        case SeedType.SEED_ZOMBIE_POLEVAULTER:
            return 75;
        case SeedType.SEED_ZOMBIE_PAIL:
            return 125;
        case SeedType.SEED_ZOMBIE_LADDER:
            return 150;
        case SeedType.SEED_ZOMBIE_DIGGER:
            return 125;
        case SeedType.SEED_ZOMBIE_BUNGEE:
            return 125;
        case SeedType.SEED_ZOMBIE_FOOTBALL:
            return 175;
        case SeedType.SEED_ZOMBIE_BALLOON:
            return 150;
        case SeedType.SEED_ZOMBIE_SCREEN_DOOR:
            return 100;
        case SeedType.SEED_ZOMBONI:
            return 175;
        case SeedType.SEED_ZOMBIE_POGO:
            return 200;
        case SeedType.SEED_ZOMBIE_DANCER:
            return 350;
        case SeedType.SEED_ZOMBIE_GARGANTUAR:
            return 300;
        case SeedType.SEED_ZOMBIE_IMP:
            return 50;
        default: {
            if (theSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
                PlantDefinition aPlantDef = getPlantDefinition(theImitaterType);
                return aPlantDef.mSeedCost;
            } else {
                PlantDefinition aPlantDef = getPlantDefinition(theSeedType);
                return aPlantDef.mSeedCost;
            }
        }
        }
    }

    /** Plant.cpp:5039-5059. */
    public static String getNameString(int theSeedType, int theImitaterType) {
        PlantDefinition aPlantDef = getPlantDefinition(theSeedType);
        String aName = "[" + aPlantDef.mPlantName + "]";
        String aTranslatedName = PvzpStringFile.translate(aName);

        if (theSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            PlantDefinition aImitaterDef = getPlantDefinition(theImitaterType);
            String aImitaterName = "[" + aImitaterDef.mPlantName + "]";
            String aTranslatedImitaterName = PvzpStringFile.translate(aImitaterName);
            return aTranslatedName + " " + aTranslatedImitaterName;
        }

        return aTranslatedName;
    }

    /** Plant.cpp:5051-5055 (`GetToolTip`). */
    public static String getToolTip(int theSeedType) {
        PlantDefinition aPlantDef = getPlantDefinition(theSeedType);
        String aToolTip = "[" + aPlantDef.mPlantName + "_TOOLTIP]";
        return PvzpStringFile.translate(aToolTip);
    }

    /** Plant.cpp:5061-5078. */
    public static int getRefreshTime(int theSeedType, int theImitaterType) {
        if (Challenge.isZombieSeedType(theSeedType) != 0) {
            return 0;
        }

        if (theSeedType == SeedType.SEED_IMITATER && theImitaterType != SeedType.SEED_NONE) {
            PlantDefinition aPlantDef = getPlantDefinition(theImitaterType);
            return aPlantDef.mRefreshTime;
        } else {
            PlantDefinition aPlantDef = getPlantDefinition(theSeedType);
            return aPlantDef.mRefreshTime;
        }
    }

    /** Plant.cpp:5080-5093. */
    public static boolean isNocturnal(int theSeedtype) {
        return theSeedtype == SeedType.SEED_PUFFSHROOM
                || theSeedtype == SeedType.SEED_SEASHROOM
                || theSeedtype == SeedType.SEED_SUNSHROOM
                || theSeedtype == SeedType.SEED_FUMESHROOM
                || theSeedtype == SeedType.SEED_HYPNOSHROOM
                || theSeedtype == SeedType.SEED_DOOMSHROOM
                || theSeedtype == SeedType.SEED_ICESHROOM
                || theSeedtype == SeedType.SEED_MAGNETSHROOM
                || theSeedtype == SeedType.SEED_SCAREDYSHROOM
                || theSeedtype == SeedType.SEED_GLOOMSHROOM;
    }

    /** Plant.cpp:5095-5107. */
    public static boolean isFungus(int theSeedtype) {
        return theSeedtype == SeedType.SEED_PUFFSHROOM
                || theSeedtype == SeedType.SEED_SUNSHROOM
                || theSeedtype == SeedType.SEED_FUMESHROOM
                || theSeedtype == SeedType.SEED_HYPNOSHROOM
                || theSeedtype == SeedType.SEED_SCAREDYSHROOM
                || theSeedtype == SeedType.SEED_ICESHROOM
                || theSeedtype == SeedType.SEED_DOOMSHROOM
                || theSeedtype == SeedType.SEED_SEASHROOM
                || theSeedtype == SeedType.SEED_MAGNETSHROOM
                || theSeedtype == SeedType.SEED_GLOOMSHROOM;
    }

    /** Plant.cpp:5109-5115. */
    public static boolean isAquatic(int theSeedType) {
        return theSeedType == SeedType.SEED_LILYPAD
                || theSeedType == SeedType.SEED_TANGLEKELP
                || theSeedType == SeedType.SEED_SEASHROOM
                || theSeedType == SeedType.SEED_CATTAIL;
    }

    /** Plant.cpp:5117-5122. */
    public static boolean isFlying(int theSeedtype) {
        return theSeedtype == SeedType.SEED_INSTANT_COFFEE;
    }

    /** Plant.cpp:5124-5134. */
    public static boolean isUpgrade(int theSeedtype) {
        return theSeedtype == SeedType.SEED_GATLINGPEA
                || theSeedtype == SeedType.SEED_WINTERMELON
                || theSeedtype == SeedType.SEED_TWINSUNFLOWER
                || theSeedtype == SeedType.SEED_SPIKEROCK
                || theSeedtype == SeedType.SEED_COBCANNON
                || theSeedtype == SeedType.SEED_GOLD_MAGNET
                || theSeedtype == SeedType.SEED_GLOOMSHROOM
                || theSeedtype == SeedType.SEED_CATTAIL;
    }

    /** Plant.cpp:5136-5158. */
    public Rect getPlantRect() {
        if (mSeedType == SeedType.SEED_TALLNUT) {
            return new Rect(mX + 10, mY, mWidth, mHeight);
        } else if (mSeedType == SeedType.SEED_PUMPKINSHELL) {
            return new Rect(mX, mY, mWidth - 20, mHeight);
        } else if (mSeedType == SeedType.SEED_COBCANNON) {
            return new Rect(mX, mY, 140, 80);
        } else {
            return new Rect(mX + 10, mY, mWidth - 20, mHeight);
        }
    }

    /** Plant.cpp:5160-5190. */
    public Rect getPlantAttackRect(int thePlantWeapon) {
        if (mApp.isWallnutBowlingLevel()) {
            return new Rect(mX, mY, mWidth - 20, mHeight);
        } else if (thePlantWeapon == PlantWeapon.WEAPON_SECONDARY && mSeedType == SeedType.SEED_SPLITPEA) {
            return new Rect(0, mY, mX + 16, mHeight);
        }

        switch (mSeedType) {
        case SeedType.SEED_LEFTPEATER:
            return new Rect(0, mY, mX, mHeight);
        case SeedType.SEED_SQUASH:
            return new Rect(mX + 20, mY, mWidth - 35, mHeight);
        case SeedType.SEED_CHOMPER:
            return new Rect(mX + 80, mY, 40, mHeight);
        case SeedType.SEED_SPIKEWEED:
        case SeedType.SEED_SPIKEROCK:
            return new Rect(mX + 20, mY, mWidth - 50, mHeight);
        case SeedType.SEED_POTATOMINE:
            return new Rect(mX, mY, mWidth - 25, mHeight);
        case SeedType.SEED_TORCHWOOD:
            return new Rect(mX + 50, mY, 30, mHeight);
        case SeedType.SEED_PUFFSHROOM:
        case SeedType.SEED_SEASHROOM:
            return new Rect(mX + 60, mY, 230, mHeight);
        case SeedType.SEED_FUMESHROOM:
            return new Rect(mX + 60, mY, 340, mHeight);
        case SeedType.SEED_GLOOMSHROOM:
            return new Rect(mX - 80, mY - 80, 240, 240);
        case SeedType.SEED_TANGLEKELP:
            return new Rect(mX, mY, mWidth, mHeight);
        case SeedType.SEED_CATTAIL:
            return new Rect(-GameConstants.BOARD_WIDTH, -GameConstants.BOARD_HEIGHT, GameConstants.BOARD_WIDTH * 2, GameConstants.BOARD_HEIGHT * 2);
        default:
            return new Rect(mX + 60, mY, GameConstants.BOARD_WIDTH, mHeight);
        }
    }

    /** Plant.cpp:5192-5217. */
    public static void preloadPlantResources(int theSeedType) {
        PlantDefinition aPlantDef = getPlantDefinition(theSeedType);
        if (aPlantDef.mReanimationType != ReanimationType.REANIM_NONE) {
            Reanimator.reanimatorEnsureDefinitionLoaded(aPlantDef.mReanimationType, true);
        }

        if (theSeedType == SeedType.SEED_CHERRYBOMB) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_ZOMBIE_CHARRED, true);
        } else if (theSeedType == SeedType.SEED_JALAPENO) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_JALAPENO_FIRE, true);
        } else if (theSeedType == SeedType.SEED_TORCHWOOD) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_FIRE_PEA, true);
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_JALAPENO_FIRE, true);
        } else if (isNocturnal(theSeedType)) {
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SLEEPING, true);
        }
    }

    /** Plant.cpp:5219-5229. */
    public void playIdleAnim(float theRate) {
        Reanimation aBodyReanim = mApp.reanimationTryToGet(mBodyReanimID);
        if (aBodyReanim != null) {
            playBodyReanim("anim_idle", ReanimLoopType.REANIM_LOOP, 20, theRate);
            if (mApp.isIZombieLevel()) {
                aBodyReanim.mAnimRate = 0.0f;
            }
        }
    }

    /** Plant.h:296 (`UpdateNeedsFood() { ; }`). */
    public void updateNeedsFood() {
    }
}
