/*
 * Ported from src/Lawn/System/ReanimationLawn.h:45-71 (class ReanimatorCache) and
 * src/Lawn/System/ReanimationLawn.cpp:31-417.
 *
 * Runtime cel cache: pre-renders a *static* frame of a reanimation into an off-screen software
 * image once and blits that image afterwards. Used by the UI (almanac / store / seed bank previews)
 * and by the handful of gameplay fallbacks (ladder overlay, lawn-mower trailer) that need a
 * non-animated cel of a plant, a lawn mower or a zombie.
 *
 * PORT-NOTE (representation changes, all mechanical):
 *   - `std::array<std::unique_ptr<MemoryImage>, N>` becomes `MemoryImage[]` of length
 *     N (SeedType.NUM_SEED_TYPES / LawnMowerType.NUM_MOWER_TYPES /
 *     ZombieType.NUM_CACHED_ZOMBIE_TYPES); a null slot is the upstream `nullptr`.
 *   - `std::unique_ptr<MemoryImage>` return values become plain `MemoryImage` references; the
 *     owning raw pointer in ReanimCacheImageVariation is a plain reference as well.
 *   - the C++ destructor `~ReanimatorCache` is exposed as `dispose()` (the upstream function body is
 *     ReanimatorCacheDispose()); the unique_ptr arrays need no explicit release under Java's GC.
 *   - every stack-allocated `Reanimation` (ReanimationLawn.cpp:84, :286, :303-306, :322-325) is
 *     cleaned up through disposeStackReanimation(), the port of `Reanimation::~Reanimation`
 *     (Reanimator.cpp:336-339).
 */
package cn.enaium.fabricpvz.portable.lawn.system;

import java.util.Arrays;

import cn.enaium.fabricpvz.portable.lawn.DrawVariation;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnMowerType;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpList;
import cn.enaium.fabricpvz.portable.pvzp.PvzpListNode;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrack;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Ported from src/Lawn/System/ReanimationLawn.h:45-71 (class ReanimatorCache) and
 * src/Lawn/System/ReanimationLawn.cpp:31-417.
 *
 * <p>The cache is created by LawnApp and lives for the whole session
 * ({@code LawnApp::mReanimatorCache}). Each entry is built lazily by
 * {@link #drawCachedPlant}/{@link #drawCachedMower}/{@link #drawCachedZombie}: the first call
 * renders the reanimation through {@link #drawReanimatorFrame} into a fresh blank {@link MemoryImage}
 * (see {@link #makeBlankMemoryImage}) and the following ones only blit it.
 *
 * <p>{@link #mImageVariationList} holds the per-{@code DrawVariation} variants of the seed images
 * (zen garden / imitater / marigold colours), which is why the plant cache is not a single array
 * lookup like the mower and zombie ones.
 */
public class ReanimatorCache {

    /**
     * Upstream local table {@code MARIGOLD_VARIATIONS} (ReanimationLawn.cpp:36-48): the 11 marigold
     * petal colours addressed by {@code DrawVariation.VARIATION_MARIGOLD_WHITE ..
     * VARIATION_MARIGOLD_LIGHT_GREEN}. Hoisted to a constant here because Java would otherwise
     * allocate the array on every call.
     */
    private static final Color[] MARIGOLD_VARIATIONS = {
        new Color(255, 255, 255),
        new Color(230, 30, 195),
        new Color(250, 125, 5),
        new Color(255, 145, 215),
        new Color(160, 255, 245),
        new Color(230, 30, 30),
        new Color(5, 130, 255),
        new Color(195, 55, 235),
        new Color(235, 210, 255),
        new Color(255, 245, 55),
        new Color(180, 255, 105)
    };

    /** Index of {@link #getPlantImageSize}'s returned offset X (upstream {@code theOffsetX}). */
    private static final int PLANT_SIZE_OFFSET_X = 0;
    /** Index of {@link #getPlantImageSize}'s returned offset Y (upstream {@code theOffsetY}). */
    private static final int PLANT_SIZE_OFFSET_Y = 1;
    /** Index of {@link #getPlantImageSize}'s returned width (upstream {@code theWidth}). */
    private static final int PLANT_SIZE_WIDTH = 2;
    /** Index of {@link #getPlantImageSize}'s returned height (upstream {@code theHeight}). */
    private static final int PLANT_SIZE_HEIGHT = 3;

    /** Ported from src/Lawn/System/ReanimationLawn.h:50. */
    public MemoryImage[] mPlantImages = new MemoryImage[SeedType.MAX_SEED_TYPES];

    /** Ported from src/Lawn/System/ReanimationLawn.h:51. */
    public PvzpList<ReanimCacheImageVariation> mImageVariationList = new PvzpList<>();

    /** Ported from src/Lawn/System/ReanimationLawn.h:52. */
    public MemoryImage[] mLawnMowers = new MemoryImage[LawnMowerType.NUM_MOWER_TYPES];

    /** Ported from src/Lawn/System/ReanimationLawn.h:53. */
    public MemoryImage[] mZombieImages = new MemoryImage[ZombieType.MAX_ZOMBIE_TYPES];

    /** Ported from src/Lawn/System/ReanimationLawn.h:54. */
    public LawnApp mApp;

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:31-80
     * (ReanimatorCache::UpdateReanimationForVariation).
     *
     * <p>Applies a draw variation to an already-initialised reanimation: the marigold range only
     * recolours the {@code Marigold_petals} track, everything else switches a filter effect or
     * selects a different frame layer. Note the switch is exhaustive over the upstream variations
     * and asserts on anything else, so a variation that is neither a marigold colour nor one of the
     * six handled cases is a programming error.
     */
    public void updateReanimationForVariation(Reanimation theReanim, int theDrawVariation) {
        if (theDrawVariation >= DrawVariation.VARIATION_MARIGOLD_WHITE
                && theDrawVariation <= DrawVariation.VARIATION_MARIGOLD_LIGHT_GREEN) {
            int aVariationIndex = theDrawVariation - DrawVariation.VARIATION_MARIGOLD_WHITE;
            // upstream PVZP_ASSERT(aVariationIndex >= 0 && aVariationIndex < LENGTH(MARIGOLD_VARIATIONS))
            assert aVariationIndex >= 0 && aVariationIndex < MARIGOLD_VARIATIONS.length;
            theReanim.getTrackInstanceByName("Marigold_petals").mTrackColor = MARIGOLD_VARIATIONS[aVariationIndex];
        } else {
            switch (theDrawVariation) {
            case DrawVariation.VARIATION_IMITATER:
                theReanim.mFilterEffect = FilterEffect.FILTER_EFFECT_WASHED_OUT;
                break;
            case DrawVariation.VARIATION_IMITATER_LESS:
                theReanim.mFilterEffect = FilterEffect.FILTER_EFFECT_LESS_WASHED_OUT;
                break;
            case DrawVariation.VARIATION_ZEN_GARDEN:
                theReanim.setFramesForLayer("anim_zengarden");
                break;
            case DrawVariation.VARIATION_ZEN_GARDEN_WATER:
                theReanim.setFramesForLayer("anim_waterplants");
                break;
            case DrawVariation.VARIATION_AQUARIUM:
                theReanim.setFramesForLayer("anim_idle_aquarium");
                break;
            case DrawVariation.VARIATION_SPROUT_NO_FLOWER:
                theReanim.setFramesForLayer("anim_idle_noflower");
                break;
            default:
                // upstream PVZP_ASSERT(false)
                assert false : "unhandled DrawVariation " + theDrawVariation;
                break;
            }
        }
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:82-113
     * (ReanimatorCache::DrawReanimatorFrame).
     *
     * <p>Renders one frame of {@code theReanimationType} straight into {@code g} using a throw-away
     * reanimation. The track selection happens before the fixed render-group tweaks, exactly as
     * upstream: the kernel-pult hides its butter, the sunflower is advanced a hair (0.15) so it does
     * not sit on the blank first frame, and {@code anim_waterline} is always hidden because the cache
     * cel is drawn on land.
     *
     * @param theTrackName layer to lock onto, or {@code null} to keep the reanimation's default layer
     */
    public void drawReanimatorFrame(Graphics g, float thePosX, float thePosY, int theReanimationType,
            String theTrackName, int theDrawVariation) {
        Reanimation aReanim = new Reanimation();
        aReanim.reanimationInitializeType(thePosX, thePosY, theReanimationType);

        if (theTrackName != null && aReanim.trackExists(theTrackName)) {
            aReanim.setFramesForLayer(theTrackName);
        }
        if (theReanimationType == ReanimationType.REANIM_KERNELPULT) {
            aReanim.assignRenderGroupToTrack("Cornpult_butter", Reanimation.RENDER_GROUP_HIDDEN);
        } else if (theReanimationType == ReanimationType.REANIM_SUNFLOWER) {
            aReanim.mAnimTime = 0.15f;
        }
        aReanim.assignRenderGroupToTrack("anim_waterline", Reanimation.RENDER_GROUP_HIDDEN);

        if (g.getColorizeImages()) {
            aReanim.mColorOverride = g.getColor();
        }
        aReanim.overrideScale(g.mScaleX, g.mScaleY);

        if (theDrawVariation != DrawVariation.VARIATION_NORMAL) {
            updateReanimationForVariation(aReanim, theDrawVariation);
        }

        aReanim.draw(g);

        disposeStackReanimation(aReanim);
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:115-128
     * (ReanimatorCache::MakeBlankMemoryImage).
     *
     * <p>Allocates a fully transparent {@code 0xAARRGGBB} buffer. {@code mBits} carries the extra
     * canary word at index {@code width*height} (upstream {@code Sexy::MEMORYCHECK_ID}) which
     * {@link MemoryImage#bitsChanged()} checks; {@code mHasTrans}/{@code mHasAlpha} are pinned to
     * {@code true} because every cached cel is drawn with transparency.
     */
    public MemoryImage makeBlankMemoryImage(int theWidth, int theHeight) {
        MemoryImage aImage = new MemoryImage();

        int aBitsCount = theWidth * theHeight;
        aImage.mBits = new int[aBitsCount + 1];
        aImage.mWidth = theWidth;
        aImage.mHeight = theHeight;
        aImage.mHasTrans = true;
        aImage.mHasAlpha = true;
        Arrays.fill(aImage.mBits, 0, aBitsCount, 0);
        aImage.mBits[aBitsCount] = MemoryImage.MEMORYCHECK_ID;
        return aImage;
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:130-151
     * (ReanimatorCache::GetPlantImageSize).
     *
     * <p>Upstream returns the four values through reference parameters; the Java port returns them as
     * a 4-element array indexed by {@link #PLANT_SIZE_OFFSET_X}, {@link #PLANT_SIZE_OFFSET_Y},
     * {@link #PLANT_SIZE_WIDTH} and {@link #PLANT_SIZE_HEIGHT}.
     *
     * @return {@code {offsetX, offsetY, width, height}} of the cached cel for {@code theSeedType}
     */
    public int[] getPlantImageSize(int theSeedType) {
        int theOffsetX = -20;
        int theOffsetY = -20;
        int theWidth = 120;
        int theHeight = 120;

        if (theSeedType == SeedType.SEED_TALLNUT) {
            theOffsetY = -40;
            theHeight += 40;
        } else if (theSeedType == SeedType.SEED_MELONPULT || theSeedType == SeedType.SEED_WINTERMELON) {
            theOffsetX = -40;
            theWidth += 40;
        } else if (theSeedType == SeedType.SEED_COBCANNON) {
            theWidth += 80;
        }

        return new int[] {theOffsetX, theOffsetY, theWidth, theHeight};
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:153-205
     * (ReanimatorCache::MakeCachedMowerFrame).
     *
     * <p>All four mower kinds render into a 90x100 cel; the pool cleaner is the odd one out with
     * scale 0.8 and a downward offset. An unknown mower type asserts and yields {@code null}, as
     * upstream returns an empty {@code unique_ptr}.
     */
    public MemoryImage makeCachedMowerFrame(int theMowerType) {
        MemoryImage aImage = null;

        switch (theMowerType) {
        case LawnMowerType.LAWNMOWER_LAWN: {
            aImage = makeBlankMemoryImage(90, 100);
            Graphics aMemoryGraphics = new Graphics(aImage);
            aMemoryGraphics.setLinearBlend(true);
            aMemoryGraphics.mScaleX = 0.85f;
            aMemoryGraphics.mScaleY = 0.85f;
            drawReanimatorFrame(aMemoryGraphics, 10.0f, 0.0f, ReanimationType.REANIM_LAWNMOWER, "anim_normal",
                    DrawVariation.VARIATION_NORMAL);
            break;
        }
        case LawnMowerType.LAWNMOWER_POOL: {
            aImage = makeBlankMemoryImage(90, 100);
            Graphics aMemoryGraphics = new Graphics(aImage);
            aMemoryGraphics.setLinearBlend(true);
            aMemoryGraphics.mScaleX = 0.8f;
            aMemoryGraphics.mScaleY = 0.8f;
            drawReanimatorFrame(aMemoryGraphics, 10.0f, 25.0f, ReanimationType.REANIM_POOL_CLEANER, null,
                    DrawVariation.VARIATION_NORMAL);
            break;
        }
        case LawnMowerType.LAWNMOWER_ROOF: {
            aImage = makeBlankMemoryImage(90, 100);
            Graphics aMemoryGraphics = new Graphics(aImage);
            aMemoryGraphics.setLinearBlend(true);
            aMemoryGraphics.mScaleX = 0.85f;
            aMemoryGraphics.mScaleY = 0.85f;
            drawReanimatorFrame(aMemoryGraphics, 10.0f, 0.0f, ReanimationType.REANIM_ROOF_CLEANER, null,
                    DrawVariation.VARIATION_NORMAL);
            break;
        }
        case LawnMowerType.LAWNMOWER_SUPER_MOWER: {
            aImage = makeBlankMemoryImage(90, 100);
            Graphics aMemoryGraphics = new Graphics(aImage);
            aMemoryGraphics.setLinearBlend(true);
            aMemoryGraphics.mScaleX = 0.85f;
            aMemoryGraphics.mScaleY = 0.85f;
            drawReanimatorFrame(aMemoryGraphics, 10.0f, 0.0f, ReanimationType.REANIM_LAWNMOWER, "anim_tricked",
                    DrawVariation.VARIATION_NORMAL);
            break;
        }
        default:
            // upstream PVZP_ASSERT(false)
            assert false : "unhandled LawnMowerType " + theMowerType;
            break;
        }

        return aImage;
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:207-259
     * (ReanimatorCache::MakeCachedPlantFrame).
     *
     * <p>Draws the plant's {@code anim_idle} (potato mine {@code anim_armed} at 0.85, instant coffee
     * {@code anim_idle} at 0.8, explode-o-nut colourised red) and then, on top of the same image, the
     * head layers some plants need: the peashooter family, split pea ({@code anim_splitpea_idle}) and
     * threepeater ({@code anim_head_idle1/3/2}, in that order). The canvas and origin come from
     * {@link #getPlantImageSize}.
     */
    public MemoryImage makeCachedPlantFrame(int theSeedType, int theDrawVariation) {
        int[] aSize = getPlantImageSize(theSeedType);
        int aOffsetX = aSize[PLANT_SIZE_OFFSET_X];
        int aOffsetY = aSize[PLANT_SIZE_OFFSET_Y];
        int aWidth = aSize[PLANT_SIZE_WIDTH];
        int aHeight = aSize[PLANT_SIZE_HEIGHT];
        MemoryImage aMemoryImage = makeBlankMemoryImage(aWidth, aHeight);
        Graphics aMemoryGraphics = new Graphics(aMemoryImage);
        aMemoryGraphics.setLinearBlend(true);

        PlantDefinition aPlantDef = Plant.getPlantDefinition(theSeedType);
        // upstream PVZP_ASSERT(aPlantDef.mReanimationType != ReanimationType::REANIM_NONE); (commented out)

        if (theSeedType == SeedType.SEED_POTATOMINE) {
            aMemoryGraphics.mScaleX = 0.85f;
            aMemoryGraphics.mScaleY = 0.85f;
            drawReanimatorFrame(aMemoryGraphics, -(int) (aOffsetX - 12.0f), -(int) (aOffsetY - 12.0f),
                    aPlantDef.mReanimationType, "anim_armed", theDrawVariation);
        } else if (theSeedType == SeedType.SEED_INSTANT_COFFEE) {
            aMemoryGraphics.mScaleX = 0.8f;
            aMemoryGraphics.mScaleY = 0.8f;
            drawReanimatorFrame(aMemoryGraphics, -(int) (aOffsetX - 12.0f), -(int) (aOffsetY - 12.0f),
                    aPlantDef.mReanimationType, "anim_idle", theDrawVariation);
        } else if (theSeedType == SeedType.SEED_EXPLODE_O_NUT) {
            aMemoryGraphics.setColorizeImages(true);
            aMemoryGraphics.setColor(new Color(255, 64, 64));
            drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType, "anim_idle",
                    theDrawVariation);
        } else {
            drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType, "anim_idle",
                    theDrawVariation);

            if (theSeedType == SeedType.SEED_SPLITPEA) {
                drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType,
                        "anim_head_idle", theDrawVariation);
                drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType,
                        "anim_splitpea_idle", theDrawVariation);
            } else if (theSeedType == SeedType.SEED_THREEPEATER) {
                drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType,
                        "anim_head_idle1", theDrawVariation);
                drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType,
                        "anim_head_idle3", theDrawVariation);
                drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType,
                        "anim_head_idle2", theDrawVariation);
            } else if (trackExists(aPlantDef.mReanimationType, "anim_head_idle")) {
                // PORT-NOTE: upstream lists the pea shooters here (peashooter, snow pea, repeater, left
                // peater, gatling pea), i.e. exactly the plants whose body reanimation keeps the head on
                // a separate anim_head_idle layer. Asking the reanimation instead of the seed type keeps
                // that rule for every plant of the family, including one a registered seed type draws
                // with - this cached frame is what a seed packet, the almanac picture and the cursor
                // preview show, and the retail list left a registered peashooter headless in all three.
                // The two seed types above keep their own extra layers.
                drawReanimatorFrame(aMemoryGraphics, -aOffsetX, -aOffsetY, aPlantDef.mReanimationType,
                        "anim_head_idle", theDrawVariation);
            }
        }

        return aMemoryImage;
    }

    /**
     * @param theReanimationType a reanimation type
     * @param theTrackName the track to look for
     * @return whether the reanimation carries that track, i.e. whether drawing it is part of drawing the
     *     plant (the definition is loaded on demand, like every other reader of it)
     */
    private static boolean trackExists(int theReanimationType, String theTrackName) {
        Reanimator.reanimatorEnsureDefinitionLoaded(theReanimationType, false);
        ReanimatorDefinition aDefinition = Reanimator.gReanimatorDefArray[theReanimationType];
        if (aDefinition == null || aDefinition.mTracks == null) {
            return false;
        }

        ReanimatorTrack[] aTracks = (ReanimatorTrack[]) aDefinition.mTracks.mArrayData;
        if (aTracks == null) {
            return false;
        }

        for (ReanimatorTrack aTrack : aTracks) {
            if (theTrackName.equals(aTrack.mName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:261-333
     * (ReanimatorCache::MakeCachedZombieFrame).
     *
     * <p>200x210 cel with the zombie placed at (40,40). Three shapes, matching upstream: the common
     * {@code REANIM_ZOMBIE} body (with the per-type layer setup from
     * {@link Zombie#setupReanimLayers}, the screen door re-enabled for {@code ZOMBIE_DOOR} and an
     * extra {@code REANIM_FLAG} pass for {@code ZOMBIE_FLAG}); the boss, drawn twice with its inner
     * body groups hidden in between, plus the boss driver; and every other reanimation through
     * {@link #drawReanimatorFrame} with a per-type track ({@code anim_pogo}, {@code anim_walk}) and
     * origin. {@code ZOMBIE_CACHED_POLEVAULTER_WITH_POLE} borrows the polevaulter definition.
     */
    public MemoryImage makeCachedZombieFrame(int theZombieType) {
        MemoryImage aMemoryImage = makeBlankMemoryImage(200, 210);
        Graphics aMemoryGraphics = new Graphics(aMemoryImage);
        aMemoryGraphics.setLinearBlend(true);

        int aUseZombieType = theZombieType;
        if (theZombieType == ZombieType.ZOMBIE_CACHED_POLEVAULTER_WITH_POLE) {
            aUseZombieType = ZombieType.ZOMBIE_POLEVAULTER;
        }
        ZombieDefinition aZombieDef = Zombie.getZombieDefinition(aUseZombieType);
        // upstream PVZP_ASSERT(aZombieDef.mReanimationType != ReanimationType::REANIM_NONE);
        assert aZombieDef.mReanimationType != ReanimationType.REANIM_NONE;

        float aPosX = 40.0f;
        float aPosY = 40.0f;
        if (aZombieDef.mReanimationType == ReanimationType.REANIM_ZOMBIE) {
            Reanimation aReanim = new Reanimation();
            aReanim.reanimationInitializeType(aPosX, aPosY, aZombieDef.mReanimationType);
            aReanim.setFramesForLayer("anim_idle");
            Zombie.setupReanimLayers(aReanim, aUseZombieType);

            if (theZombieType == ZombieType.ZOMBIE_DOOR) {
                aReanim.assignRenderGroupToTrack("anim_screendoor", Reanimation.RENDER_GROUP_NORMAL);
            } else if (theZombieType == ZombieType.ZOMBIE_FLAG) {
                Reanimation aReanimFlag = new Reanimation();
                aReanimFlag.reanimationInitializeType(aPosX, aPosY, ReanimationType.REANIM_FLAG);
                aReanimFlag.setFramesForLayer("Zombie_flag");
                aReanimFlag.draw(aMemoryGraphics);
                disposeStackReanimation(aReanimFlag);
            }
            aReanim.draw(aMemoryGraphics);
            disposeStackReanimation(aReanim);
        } else if (aZombieDef.mReanimationType == ReanimationType.REANIM_BOSS) {
            Reanimation aReanim = new Reanimation();
            aReanim.reanimationInitializeType(-524.0f, -88.0f, aZombieDef.mReanimationType);
            aReanim.setFramesForLayer("anim_head_idle");
            Reanimation aReanimDriver = new Reanimation();
            aReanimDriver.reanimationInitializeType(46.0f, 22.0f, ReanimationType.REANIM_BOSS_DRIVER);
            aReanimDriver.setFramesForLayer("anim_idle");

            aReanim.draw(aMemoryGraphics);
            aReanimDriver.draw(aMemoryGraphics);
            aReanim.assignRenderGroupToTrack("boss_body1", Reanimation.RENDER_GROUP_HIDDEN);
            aReanim.assignRenderGroupToTrack("boss_neck", Reanimation.RENDER_GROUP_HIDDEN);
            aReanim.assignRenderGroupToTrack("boss_head2", Reanimation.RENDER_GROUP_HIDDEN);
            aReanim.draw(aMemoryGraphics);

            disposeStackReanimation(aReanimDriver);
            disposeStackReanimation(aReanim);
        } else {
            String aTrackName = "anim_idle";
            if (theZombieType == ZombieType.ZOMBIE_POGO) {
                aTrackName = "anim_pogo";
            } else if (theZombieType == ZombieType.ZOMBIE_CACHED_POLEVAULTER_WITH_POLE) {
                aTrackName = "anim_idle";
            } else if (theZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
                aTrackName = "anim_walk";
            } else if (theZombieType == ZombieType.ZOMBIE_GARGANTUAR) {
                aPosY = 60.0f;
            }

            drawReanimatorFrame(aMemoryGraphics, aPosX, aPosY, aZombieDef.mReanimationType, aTrackName,
                    DrawVariation.VARIATION_NORMAL);
        }
        return aMemoryImage;
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:335-338
     * ({@code ReanimatorCache::~ReanimatorCache}).
     *
     * <p>Java has no deterministic destructor, so the upstream destructor body is exposed as this
     * explicit method. The {@code unique_ptr} image arrays ({@link #mPlantImages},
     * {@link #mLawnMowers}, {@link #mZombieImages}) are released by the garbage collector instead of
     * by the destructor.
     */
    public void dispose() {
        reanimatorCacheDispose();
    }

    /** Ported from src/Lawn/System/ReanimationLawn.cpp:340-343 (ReanimatorCache::ReanimatorCacheInitialize). */
    public void reanimatorCacheInitialize() {
        mApp = LawnApp.gLawnApp;
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:345-354
     * (ReanimatorCache::ReanimatorCacheDispose).
     *
     * <p>Drops every cached variation image. Upstream {@code delete}s the pointed-at image; the Java
     * port clears the reference so the image is collectable as soon as the node goes away (the
     * {@code unique_ptr} plant/mower/zombie arrays are not touched here, exactly as upstream).
     */
    public void reanimatorCacheDispose() {
        while (mImageVariationList.mSize != 0) {
            ReanimCacheImageVariation aImageVariation = mImageVariationList.removeHead();
            aImageVariation.mImage = null;
        }
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:356-399
     * (ReanimatorCache::DrawCachedPlant).
     *
     * <p>Variation {@code VARIATION_NORMAL} uses the plain per-seed array (built on demand); every
     * other variation is looked up in {@link #mImageVariationList} by the
     * {@code (seed, variation)} pair and rendered/stored there on a miss. The blit then picks the
     * unscaled fast path when the app is not 3D-accelerated and the graphics' scale is 1, otherwise
     * the scaled software draw ({@link PvzpCommon#pvzpDrawImageScaledF}).
     */
    public void drawCachedPlant(Graphics g, float thePosX, float thePosY, int theSeedType, int theDrawVariation) {
        // upstream PVZP_ASSERT(theSeedType >= 0 && theSeedType < SeedType::NUM_SEED_TYPES);
        // widened to the seed-type id space (SeedType.MAX_SEED_TYPES): every seed type can be cached
        assert theSeedType >= 0 && theSeedType < SeedType.MAX_SEED_TYPES : theSeedType;

        MemoryImage aImage = null;
        if (theDrawVariation != DrawVariation.VARIATION_NORMAL) {
            for (PvzpListNode<ReanimCacheImageVariation> aNode = mImageVariationList.mHead; aNode != null;
                    aNode = aNode.mNext) {
                ReanimCacheImageVariation aImageVariation = aNode.mValue;
                if (aImageVariation.mSeedType == theSeedType && aImageVariation.mDrawVariation == theDrawVariation) {
                    aImage = aImageVariation.mImage;
                    break;
                }
            }

            if (aImage == null) {
                ReanimCacheImageVariation aNewImageVariation = new ReanimCacheImageVariation();
                aNewImageVariation.mSeedType = theSeedType;
                aNewImageVariation.mDrawVariation = theDrawVariation;
                aNewImageVariation.mImage = makeCachedPlantFrame(theSeedType, theDrawVariation);
                aImage = aNewImageVariation.mImage;
                mImageVariationList.addHead(aNewImageVariation);
            }
        } else {
            aImage = mPlantImages[theSeedType];
            if (aImage == null) {
                mPlantImages[theSeedType] = makeCachedPlantFrame(theSeedType, DrawVariation.VARIATION_NORMAL);
                aImage = mPlantImages[theSeedType];
            }
        }

        int[] aSize = getPlantImageSize(theSeedType);
        int aOffsetX = aSize[PLANT_SIZE_OFFSET_X];
        int aOffsetY = aSize[PLANT_SIZE_OFFSET_Y];
        if (!mApp.is3DAccelerated() && g.mScaleX == 1.0f && g.mScaleY == 1.0f) {
            g.drawImage(aImage, (int) (thePosX + aOffsetX), (int) (thePosY + aOffsetY));
        } else {
            PvzpCommon.pvzpDrawImageScaledF(g, aImage, thePosX + (aOffsetX * g.mScaleX),
                    thePosY + (aOffsetY * g.mScaleY), g.mScaleX, g.mScaleY);
        }
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:401-408
     * (ReanimatorCache::DrawCachedMower).
     *
     * <p>Lazily builds the 90x100 cel and always blits it through the scaled software draw (the cel
     * is 20 px wider than the placement, hence the {@code -20} X offset). Unlike the plant path there
     * is no unscaled fast path upstream.
     */
    public void drawCachedMower(Graphics g, float thePosX, float thePosY, int theMowerType) {
        // upstream PVZP_ASSERT(theMowerType >= 0 && theMowerType < LawnMowerType::NUM_MOWER_TYPES)
        assert theMowerType >= 0 && theMowerType < LawnMowerType.NUM_MOWER_TYPES : theMowerType;

        if (mLawnMowers[theMowerType] == null) {
            mLawnMowers[theMowerType] = makeCachedMowerFrame(theMowerType);
        }
        PvzpCommon.pvzpDrawImageScaledF(g, mLawnMowers[theMowerType], thePosX - 20.0f, thePosY, g.mScaleX, g.mScaleY);
    }

    /**
     * Ported from src/Lawn/System/ReanimationLawn.cpp:410-417
     * (ReanimatorCache::DrawCachedZombie).
     *
     * <p>Lazily builds the 200x210 cel and blits it through the scaled software draw.
     */
    public void drawCachedZombie(Graphics g, float thePosX, float thePosY, int theZombieType) {
        // upstream PVZP_ASSERT(theZombieType >= 0 && theZombieType < ZombieType::NUM_CACHED_ZOMBIE_TYPES);
        // widened to the zombie-type id space (ZombieType.MAX_ZOMBIE_TYPES): every type can be cached
        assert theZombieType >= 0 && theZombieType < ZombieType.MAX_ZOMBIE_TYPES : theZombieType;

        if (mZombieImages[theZombieType] == null) {
            mZombieImages[theZombieType] = makeCachedZombieFrame(theZombieType);
        }
        PvzpCommon.pvzpDrawImageScaledF(g, mZombieImages[theZombieType], thePosX, thePosY, g.mScaleX, g.mScaleY);
    }

    /**
     * Port of {@code Reanimation::~Reanimation} (src/PvzpLib/Reanimator.cpp:336-339), which is what
     * ends the life of the throw-away stack reanimations this class builds:
     *
     * <pre>
     * Reanimation::~Reanimation()
     * {
     *     ReanimationDie();
     *     ReanimationDelete();
     * }
     * </pre>
     *
     * <p>Java constructs those as locals too, so every construction site has to run the same two
     * calls when it is done with the object (upstream did it implicitly at end of scope).
     */
    private static void disposeStackReanimation(Reanimation theReanim) {
        theReanim.reanimationDie();
        theReanim.reanimationDelete();
    }
}
