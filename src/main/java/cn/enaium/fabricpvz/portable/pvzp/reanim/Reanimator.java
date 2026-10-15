package cn.enaium.fabricpvz.portable.pvzp.reanim;

import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/Reanimator.h:49-110 (constants, globals and the free functions of the
 * Reanimator API) and src/PvzpLib/Reanimator.cpp:29-190 (NO_BASE_POSE, the definition globals and
 * {@code gLawnReanimationArray}) plus src/PvzpLib/Reanimator.cpp:192-290 (definition loading and
 * freeing), :1176-1235 (definition registry) and :360-395 (atlas creation/preload).
 *
 * <p>The process-global definition storage of this port: {@code gReanimatorDefArray} is indexed by
 * {@link ReanimationType} exactly like upstream, and {@code gLawnReanimationArray} is the
 * type → file → flags table (all 144 shipped reanimation types).
 */
public final class Reanimator {
    /** Upstream {@code DEFAULT_FIELD_PLACEHOLDER} (Reanimator.h:51). */
    public static final float DEFAULT_FIELD_PLACEHOLDER = -10000.0f;
    /** Upstream {@code SECONDS_PER_UPDATE} (Reanimator.h:52), the fixed 1/100 s update tick. */
    public static final float SECONDS_PER_UPDATE = 0.01f;

    /** Upstream {@code gReanimatorDefCount} (Reanimator.h:90). */
    public static int gReanimatorDefCount;
    /** Upstream {@code gReanimatorDefArray} (Reanimator.h:91). */
    public static ReanimatorDefinition[] gReanimatorDefArray;
    /** Upstream {@code gReanimationParamArraySize} (Reanimator.h:102). */
    public static int gReanimationParamArraySize;
    /** Upstream {@code gReanimationParamArray} (Reanimator.h:103). */
    public static ReanimationParams[] gReanimationParamArray;

    /** Upstream {@code gLawnReanimationArray} (Reanimator.cpp:45-190). */
    public static final ReanimationParams[] gLawnReanimationArray = {
        new ReanimationParams(ReanimationType.REANIM_LOADBAR_SPROUT, "reanim/LoadBar_sprout.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_LOADBAR_ZOMBIEHEAD, "reanim/LoadBar_Zombiehead.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_SODROLL, "reanim/SodRoll.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_FINAL_WAVE, "reanim/FinalWave.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_PEASHOOTER, "reanim/PeaShooterSingle.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_WALLNUT, "reanim/Wallnut.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_LILYPAD, "reanim/Lilypad.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SUNFLOWER, "reanim/SunFlower.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_LAWNMOWER, "reanim/LawnMower.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_READYSETPLANT, "reanim/StartReadySetPlant.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CHERRYBOMB, "reanim/CherryBomb.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SQUASH, "reanim/Squash.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_DOOMSHROOM, "reanim/DoomShroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SNOWPEA, "reanim/SnowPea.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_REPEATER, "reanim/PeaShooter.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SUNSHROOM, "reanim/SunShroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_TALLNUT, "reanim/Tallnut.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_FUMESHROOM, "reanim/Fumeshroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_PUFFSHROOM, "reanim/Puffshroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_HYPNOSHROOM, "reanim/Hypnoshroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CHOMPER, "reanim/Chomper.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE, "reanim/Zombie.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SUN, "reanim/Sun.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_POTATOMINE, "reanim/PotatoMine.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SPIKEWEED, "reanim/Caltrop.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SPIKEROCK, "reanim/SpikeRock.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_THREEPEATER, "reanim/ThreePeater.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_MARIGOLD, "reanim/Marigold.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ICESHROOM, "reanim/IceShroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_FOOTBALL, "reanim/Zombie_football.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_NEWSPAPER, "reanim/Zombie_paper.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_ZAMBONI, "reanim/Zombie_zamboni.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SPLASH, "reanim/splash.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_JALAPENO, "reanim/Jalapeno.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_JALAPENO_FIRE, "reanim/fire.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_COIN_SILVER, "reanim/Coin_silver.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CHARRED, "reanim/Zombie_charred.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CHARRED_IMP, "reanim/Zombie_charred_imp.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CHARRED_DIGGER, "reanim/Zombie_charred_digger.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CHARRED_ZAMBONI, "reanim/Zombie_charred_zamboni.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CHARRED_CATAPULT, "reanim/Zombie_charred_catapult.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CHARRED_GARGANTUAR, "reanim/Zombie_charred_gargantuar.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SCRAREYSHROOM, "reanim/ScaredyShroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_PUMPKIN, "reanim/Pumpkin.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_PLANTERN, "reanim/Plantern.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_TORCHWOOD, "reanim/Torchwood.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SPLITPEA, "reanim/SplitPea.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SEASHROOM, "reanim/SeaShroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BLOVER, "reanim/Blover.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_FLOWER_POT, "reanim/Pot.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CACTUS, "reanim/Cactus.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_DANCER, "reanim/Zombie_disco.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_TANGLEKELP, "reanim/Tanglekelp.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_STARFRUIT, "reanim/Starfruit.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_POLEVAULTER, "reanim/Zombie_polevaulter.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BALLOON, "reanim/Zombie_balloon.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_GARGANTUAR, "reanim/Zombie_gargantuar.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_IMP, "reanim/Zombie_imp.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_DIGGER, "reanim/Zombie_digger.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_DIGGER_DIRT, "reanim/Digger_rising_dirt.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_DOLPHINRIDER, "reanim/Zombie_dolphinrider.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_POGO, "reanim/Zombie_pogo.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BACKUP_DANCER, "reanim/Zombie_backup.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BOBSLED, "reanim/Zombie_bobsled.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_JACKINTHEBOX, "reanim/Zombie_jackbox.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SNORKEL, "reanim/Zombie_snorkle.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BUNGEE, "reanim/Zombie_bungi.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CATAPULT, "reanim/Zombie_catapult.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_LADDER, "reanim/Zombie_ladder.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_PUFF, "reanim/Puff.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SLEEPING, "reanim/Z.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_GRAVE_BUSTER, "reanim/Gravebuster.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIES_WON, "reanim/ZombiesWon.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_MAGNETSHROOM, "reanim/Magnetshroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BOSS, "reanim/Zombie_boss.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CABBAGEPULT, "reanim/Cabbagepult.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_KERNELPULT, "reanim/Cornpult.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_MELONPULT, "reanim/Melonpult.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_COFFEEBEAN, "reanim/Coffeebean.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_UMBRELLALEAF, "reanim/Umbrellaleaf.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_GATLINGPEA, "reanim/GatlingPea.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CATTAIL, "reanim/Cattail.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_GLOOMSHROOM, "reanim/GloomShroom.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BOSS_ICEBALL, "reanim/Zombie_boss_iceball.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_BOSS_FIREBALL, "reanim/Zombie_boss_fireball.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_COBCANNON, "reanim/CobCannon.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_GARLIC, "reanim/Garlic.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_GOLD_MAGNET, "reanim/GoldMagnet.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_WINTER_MELON, "reanim/WinterMelon.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_TWIN_SUNFLOWER, "reanim/TwinSunflower.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_POOL_CLEANER, "reanim/PoolCleaner.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ROOF_CLEANER, "reanim/RoofCleaner.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_FIRE_PEA, "reanim/FirePea.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_IMITATER, "reanim/Imitater.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_YETI, "reanim/Zombie_yeti.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_BOSS_DRIVER, "reanim/Zombie_Boss_driver.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_LAWN_MOWERED_ZOMBIE, "reanim/LawnMoweredZombie.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CRAZY_DAVE, "reanim/CrazyDave.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_TEXT_FADE_ON, "reanim/TextFadeOn.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_HAMMER, "reanim/Hammer.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_SLOT_MACHINE_HANDLE, "reanim/SlotMachine.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_FOOTBALL, "reanim/Credits_Football.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_JACKBOX, "reanim/Credits_Jackbox.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_SELECTOR_SCREEN, "reanim/SelectorScreen.reanim", 3),
        new ReanimationParams(ReanimationType.REANIM_PORTAL_CIRCLE, "reanim/Portal_Circle.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_PORTAL_SQUARE, "reanim/Portal_Square.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZENGARDEN_SPROUT, "reanim/ZenGarden_sprout.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZENGARDEN_WATERINGCAN, "reanim/ZenGarden_wateringcan.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_ZENGARDEN_FERTILIZER, "reanim/ZenGarden_fertilizer.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_ZENGARDEN_BUGSPRAY, "reanim/ZenGarden_bugspray.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_ZENGARDEN_PHONOGRAPH, "reanim/ZenGarden_phonograph.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_DIAMOND, "reanim/Diamond.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_HAND, "reanim/Zombie_hand.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_STINKY, "reanim/Stinky.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_RAKE, "reanim/Rake.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_RAIN_CIRCLE, "reanim/Rain_circle.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_RAIN_SPLASH, "reanim/Rain_splash.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_SURPRISE, "reanim/Zombie_surprise.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_COIN_GOLD, "reanim/Coin_gold.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_TREEOFWISDOM, "reanim/TreeOfWisdom.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_TREEOFWISDOM_CLOUDS, "reanim/TreeOfWisdomClouds.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_TREEOFWISDOM_TREEFOOD, "reanim/TreeFood.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_MAIN, "reanim/Credits_Main.reanim", 3),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_MAIN2, "reanim/Credits_Main2.reanim", 3),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_MAIN3, "reanim/Credits_Main3.reanim", 3),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CREDITS_DANCE, "reanim/Zombie_credits_dance.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_STAGE, "reanim/Credits_stage.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_BIGBRAIN, "reanim/Credits_BigBrain.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_FLOWER_PETALS, "reanim/Credits_Flower_petals.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_INFANTRY, "reanim/Credits_Infantry.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_THROAT, "reanim/Credits_Throat.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_CRAZYDAVE, "reanim/Credits_CrazyDave.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_BOSSDANCE, "reanim/Credits_Bossdance.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CREDITS_SCREEN_DOOR, "reanim/Zombie_Credits_Screendoor.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_ZOMBIE_CREDITS_CONEHEAD, "reanim/Zombie_Credits_Conehead.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_ZOMBIEARMY1, "reanim/Credits_ZombieArmy1.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_ZOMBIEARMY2, "reanim/Credits_ZombieArmy2.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_TOMBSTONES, "reanim/Credits_Tombstones.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_SOLARPOWER, "reanim/Credits_SolarPower.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_ANYHOUR, "reanim/Credits_Anyhour.reanim", 3),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_WEARETHEUNDEAD, "reanim/Credits_WeAreTheUndead.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_CREDITS_DISCOLIGHTS, "reanim/Credits_DiscoLights.reanim", 1),
        new ReanimationParams(ReanimationType.REANIM_FLAG, "reanim/Zombie_FlagPole.reanim", 0),
        new ReanimationParams(ReanimationType.REANIM_ZOMBATAR_HEAD, "reanim/zombatar_zombie_head.reanim", 0),
    };

    private Reanimator() {
    }

    static ReanimatorTrack[] tracksOf(ReanimatorDefinition theDefinition) {
        return (ReanimatorTrack[]) theDefinition.mTracks.mArrayData;
    }

    static ReanimatorTransform[] transformsOf(ReanimatorTrack theTrack) {
        return (ReanimatorTransform[]) theTrack.mTransforms.mArrayData;
    }

    /**
     * Ported from src/PvzpLib/Reanimator.cpp:221-262 (ReanimationLoadDefinition) — the XML parse
     * itself is the generic {@link Definition} loader, this method owns the per-track
     * previous-value inheritance pass ({@code ReanimationFillInMissingData}, Reanimator.cpp:205-219).
     */
    public static boolean reanimationLoadDefinition(String theFileName, ReanimatorDefinition theDefinition) {
        if (!Definition.loadXML(theFileName, ReanimatorFormats.gReanimatorDefMap, theDefinition)) {
            return false;
        }

        for (ReanimatorTrack aTrack : tracksOf(theDefinition)) {
            float aPrevTransX = 0.0f;
            float aPrevTransY = 0.0f;
            float aPrevSkewX = 0.0f;
            float aPrevSkewY = 0.0f;
            float aPrevScaleX = 1.0f;
            float aPrevScaleY = 1.0f;
            float aPrevFrame = 0.0f;
            float aPrevAlpha = 1.0f;
            Image aPrevImage = null;
            Font aPrevFont = null;
            String aPrevText = "";

            for (ReanimatorTransform aTransform : transformsOf(aTrack)) {
                if (aTransform.mTransX == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mTransX = aPrevTransX;
                } else {
                    aPrevTransX = aTransform.mTransX;
                }
                if (aTransform.mTransY == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mTransY = aPrevTransY;
                } else {
                    aPrevTransY = aTransform.mTransY;
                }
                if (aTransform.mSkewX == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mSkewX = aPrevSkewX;
                } else {
                    aPrevSkewX = aTransform.mSkewX;
                }
                if (aTransform.mSkewY == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mSkewY = aPrevSkewY;
                } else {
                    aPrevSkewY = aTransform.mSkewY;
                }
                if (aTransform.mScaleX == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mScaleX = aPrevScaleX;
                } else {
                    aPrevScaleX = aTransform.mScaleX;
                }
                if (aTransform.mScaleY == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mScaleY = aPrevScaleY;
                } else {
                    aPrevScaleY = aTransform.mScaleY;
                }
                if (aTransform.mFrame == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mFrame = aPrevFrame;
                } else {
                    aPrevFrame = aTransform.mFrame;
                }
                if (aTransform.mAlpha == DEFAULT_FIELD_PLACEHOLDER) {
                    aTransform.mAlpha = aPrevAlpha;
                } else {
                    aPrevAlpha = aTransform.mAlpha;
                }
                if (aTransform.mImage == null) {
                    aTransform.mImage = aPrevImage;
                } else {
                    aPrevImage = aTransform.mImage;
                }
                if (aTransform.mFont == null) {
                    aTransform.mFont = aPrevFont;
                } else {
                    aPrevFont = aTransform.mFont;
                }
                if (aTransform.mText.isEmpty()) {
                    aTransform.mText = aPrevText;
                } else {
                    aPrevText = aTransform.mText;
                }
            }
        }
        return true;
    }

    /**
     * Ported from src/PvzpLib/Reanimator.cpp:264-290 (ReanimationFreeDefinition).
     *
     * <p>PORT-NOTE: upstream also reverses the shared {@code const char*} text dedup before freeing
     * (Reanimator.cpp:278-288) so the definition's strings are freed once; Java strings are values,
     * so nothing has to be undone here.
     */
    public static void reanimationFreeDefinition(ReanimatorDefinition theDefinition) {
        if (theDefinition.mReanimAtlas != null) {
            theDefinition.mReanimAtlas = null;
        }
        Definition.freeMap(ReanimatorFormats.gReanimatorDefMap, theDefinition);
    }

    /**
     * Ported from src/PvzpLib/Reanimator.cpp:1176-1207 (ReanimatorEnsureDefinitionLoaded).
     *
     * <p>PORT-NOTE: upstream tests the {@code mTracks.tracks != nullptr} sentinel for "already
     * loaded"; the Java definition always owns a non-null typed array, so the equivalent test is a
     * non-empty track array (every shipped {@code .reanim} has at least one track, and a failed load
     * leaves it empty, so a failure is retried exactly like upstream).
     */
    public static void reanimatorEnsureDefinitionLoaded(int theReanimType, boolean theIsPreloading) {
        ReanimatorDefinition aReanimDef = gReanimatorDefArray[theReanimType];
        if (aReanimDef.mTracks.mArrayCount != 0) {
            return;
        }

        ReanimationParams aReanimParams = gReanimationParamArray[theReanimType];
        PvzpDebug.pvzpLogLn("'{}'", aReanimParams.mReanimFileName);
        if (theIsPreloading) {
            if (SexyAppBase.current != null && SexyAppBase.current.mShutdown.get()) {
                return;  // abort preloading when the app is shutting down
            }
        } else {
            PvzpDebug.pvzpLogLn("Non-cheater failed to preload '{}'", aReanimParams.mReanimFileName);
        }

        PerfTimer aTimer = new PerfTimer();
        aTimer.start();
        if (!reanimationLoadDefinition(aReanimParams.mReanimFileName, aReanimDef)) {
            PvzpDebug.pvzpErrorMessageBox("Failed to load reanim '" + aReanimParams.mReanimFileName + "'", "Error");
        }
        int aDuration = (int) aTimer.getDuration();
        if (aDuration > 100) {
            PvzpDebug.pvzpLogLn("LOADING:Long reanim '{}' {} ms", aReanimParams.mReanimFileName, aDuration);
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1209-1225 (ReanimatorLoadDefinitions). */
    public static void reanimatorLoadDefinitions(ReanimationParams[] theReanimationParamArray, int theReanimationParamArraySize) {
        gReanimationParamArraySize = theReanimationParamArraySize;
        gReanimationParamArray = theReanimationParamArray;
        gReanimatorDefCount = theReanimationParamArraySize;
        gReanimatorDefArray = new ReanimatorDefinition[theReanimationParamArraySize];

        for (int i = 0; i < gReanimationParamArraySize; i++) {
            ReanimatorDefinition aDefinition = new ReanimatorDefinition();
            gReanimatorDefArray[i] = aDefinition;
            ReanimationParams aReanimationParams = theReanimationParamArray[i];
            if (Definition.isCompiled(aReanimationParams.mReanimFileName)) {
                reanimatorEnsureDefinitionLoaded(aReanimationParams.mReanimationType, true);
            }
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:1227-1235 (ReanimatorFreeDefinitions). */
    public static void reanimatorFreeDefinitions() {
        for (int i = 0; i < gReanimatorDefCount; i++) {
            reanimationFreeDefinition(gReanimatorDefArray[i]);
        }

        gReanimatorDefArray = null;
        gReanimatorDefCount = 0;
        gReanimationParamArray = null;
        gReanimationParamArraySize = 0;
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:360-383 (ReanimationCreateAtlas). */
    public static void reanimationCreateAtlas(ReanimatorDefinition theDefinition, int theReanimationType) {
        ReanimationParams aParam = gReanimationParamArray[theReanimationType];
        if (theDefinition.mReanimAtlas != null
                || (aParam.mReanimParamFlags & (1 << ReanimFlags.REANIM_NO_ATLAS)) != 0) {
            return;
        }

        PerfTimer aTimer = new PerfTimer();
        aTimer.start();
        ReanimAtlas aAtlas = new ReanimAtlas();
        theDefinition.mReanimAtlas = aAtlas;
        aAtlas.reanimAtlasCreate(theDefinition);

        double aDuration = Math.max(aTimer.getDuration(), 0.0);
        if (aDuration > 20 && theReanimationType != ReanimationType.REANIM_NONE) {
            PvzpDebug.pvzpLogLn("LOADING:Long atlas '{}' {} ms", aParam.mReanimFileName, (int) aDuration);
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:385-395 (ReanimationPreload). */
    public static void reanimationPreload(int theReanimationType) {
        ReanimatorDefinition aReanimDef = gReanimatorDefArray[theReanimationType];
        reanimationCreateAtlas(aReanimDef, theReanimationType);
        if (aReanimDef.mReanimAtlas != null) {
            PvzpCommon.pvzpSandImageIfNeeded(aReanimDef.mReanimAtlas.mMemoryImage);
        }
    }

    /** Ported from src/PvzpLib/Reanimator.cpp:525-551 (BlendTransform). */
    public static void blendTransform(ReanimatorTransform theResult, ReanimatorTransform theTransform1, ReanimatorTransform theTransform2, float theBlendFactor) {
        theResult.mTransX = PvzpCommon.floatLerp(theTransform1.mTransX, theTransform2.mTransX, theBlendFactor);
        theResult.mTransY = PvzpCommon.floatLerp(theTransform1.mTransY, theTransform2.mTransY, theBlendFactor);
        theResult.mScaleX = PvzpCommon.floatLerp(theTransform1.mScaleX, theTransform2.mScaleX, theBlendFactor);
        theResult.mScaleY = PvzpCommon.floatLerp(theTransform1.mScaleY, theTransform2.mScaleY, theBlendFactor);
        theResult.mAlpha = PvzpCommon.floatLerp(theTransform1.mAlpha, theTransform2.mAlpha, theBlendFactor);

        float aSkewX2 = theTransform2.mSkewX;
        float aSkewY2 = theTransform2.mSkewY;
        // when the skew differs by more than 180 degrees, theTransform2's skew is ignored
        while (aSkewX2 > theTransform1.mSkewX + 180.0f) {
            aSkewX2 = theTransform1.mSkewX;
        }
        while (aSkewX2 < theTransform1.mSkewX - 180.0f) {
            aSkewX2 = theTransform1.mSkewX;
        }
        while (aSkewY2 > theTransform1.mSkewY + 180.0f) {
            aSkewY2 = theTransform1.mSkewY;
        }
        while (aSkewY2 < theTransform1.mSkewY - 180.0f) {
            aSkewY2 = theTransform1.mSkewY;
        }

        theResult.mSkewX = PvzpCommon.floatLerp(theTransform1.mSkewX, aSkewX2, theBlendFactor);
        theResult.mSkewY = PvzpCommon.floatLerp(theTransform1.mSkewY, aSkewY2, theBlendFactor);
        theResult.mFrame = theTransform1.mFrame;
        theResult.mFont = theTransform1.mFont;
        theResult.mText = theTransform1.mText;
        theResult.mImage = theTransform1.mImage;
    }
}
