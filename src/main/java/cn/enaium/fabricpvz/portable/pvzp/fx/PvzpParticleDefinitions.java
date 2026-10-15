package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.pvzp.DefFieldType;
import cn.enaium.fabricpvz.portable.pvzp.DefMap;
import cn.enaium.fabricpvz.portable.pvzp.DefMapBuilder;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Ported from src/PvzpLib/PvzpParticle.cpp:37 (the {@code gLawnParticleArray} table) and
 * :146-232 (the definition maps + {@code PvzpParticleLoadADef}/{@code PvzpParticleLoadDefinitions}/
 * {@code PvzpParticleFreeDefinitions}).
 *
 * <p>The four particle definition maps ({@code gParticleDefMap}, {@code gEmitterDefMap},
 * {@code gParticleFieldDefMap}) are built with {@link DefMapBuilder} so their field offsets match the
 * LP64 layout {@link Definition} hashes and writes into its compiled cache. Global names are
 * upstream; the array globals map to Java arrays.
 */
public final class PvzpParticleDefinitions {

    public static int gParticleDefCount;
    public static PvzpParticleDefinition[] gParticleDefArray;
    public static int gParticleParamArraySize;
    public static ParticleParams[] gParticleParamArray;

    private PvzpParticleDefinitions() {
    }

    /** Ported from src/PvzpLib/Definition.cpp:74 ({@code gParticleFieldDefMap}). */
    public static final DefMap gParticleFieldDefMap = new DefMapBuilder()
            .field("FieldType", "mFieldType", DefFieldType.DT_ENUM, Definition.gParticleTypeSymbols)
            .field("x", "mX", DefFieldType.DT_TRACK_FLOAT)
            .field("y", "mY", DefFieldType.DT_TRACK_FLOAT)
            .build(ParticleField::new);

    /** Ported from src/PvzpLib/Definition.cpp:82 ({@code gEmitterDefMap}). */
    public static final DefMap gEmitterDefMap = new DefMapBuilder()
            .field("Image", "mImage", DefFieldType.DT_IMAGE)
            .field("ImageRow", "mImageRow", DefFieldType.DT_INT)
            .field("ImageCol", "mImageCol", DefFieldType.DT_INT)
            .field("ImageFrames", "mImageFrames", DefFieldType.DT_INT)
            .field("Animated", "mAnimated", DefFieldType.DT_INT)
            .field("ParticleFlags", "mParticleFlags", DefFieldType.DT_FLAGS, Definition.gParticleFlagSymbols)
            .field("EmitterType", "mEmitterType", DefFieldType.DT_ENUM, Definition.gEmitterTypeSymbols)
            .field("Name", "mName", DefFieldType.DT_STRING)
            .field("SystemDuration", "mSystemDuration", DefFieldType.DT_TRACK_FLOAT)
            .field("OnDuration", "mOnDuration", DefFieldType.DT_STRING)
            .field("CrossFadeDuration", "mCrossFadeDuration", DefFieldType.DT_TRACK_FLOAT)
            .field("SpawnRate", "mSpawnRate", DefFieldType.DT_TRACK_FLOAT)
            .field("SpawnMinActive", "mSpawnMinActive", DefFieldType.DT_TRACK_FLOAT)
            .field("SpawnMaxActive", "mSpawnMaxActive", DefFieldType.DT_TRACK_FLOAT)
            .field("SpawnMaxLaunched", "mSpawnMaxLaunched", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterRadius", "mEmitterRadius", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterOffsetX", "mEmitterOffsetX", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterOffsetY", "mEmitterOffsetY", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterBoxX", "mEmitterBoxX", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterBoxY", "mEmitterBoxY", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterPath", "mEmitterPath", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterSkewX", "mEmitterSkewX", DefFieldType.DT_TRACK_FLOAT)
            .field("EmitterSkewY", "mEmitterSkewY", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleDuration", "mParticleDuration", DefFieldType.DT_TRACK_FLOAT)
            .field("SystemRed", "mSystemRed", DefFieldType.DT_TRACK_FLOAT)
            .field("SystemGreen", "mSystemGreen", DefFieldType.DT_TRACK_FLOAT)
            .field("SystemBlue", "mSystemBlue", DefFieldType.DT_TRACK_FLOAT)
            .field("SystemAlpha", "mSystemAlpha", DefFieldType.DT_TRACK_FLOAT)
            .field("SystemBrightness", "mSystemBrightness", DefFieldType.DT_TRACK_FLOAT)
            .field("LaunchSpeed", "mLaunchSpeed", DefFieldType.DT_TRACK_FLOAT)
            .field("LaunchAngle", "mLaunchAngle", DefFieldType.DT_TRACK_FLOAT)
            .field("Field", "mParticleFields", DefFieldType.DT_ARRAY, gParticleFieldDefMap)
            .field("SystemField", "mSystemFields", DefFieldType.DT_ARRAY, gParticleFieldDefMap)
            .field("ParticleRed", "mParticleRed", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleGreen", "mParticleGreen", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleBlue", "mParticleBlue", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleAlpha", "mParticleAlpha", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleBrightness", "mParticleBrightness", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleSpinAngle", "mParticleSpinAngle", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleSpinSpeed", "mParticleSpinSpeed", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleScale", "mParticleScale", DefFieldType.DT_TRACK_FLOAT)
            .field("ParticleStretch", "mParticleStretch", DefFieldType.DT_TRACK_FLOAT)
            .field("CollisionReflect", "mCollisionReflect", DefFieldType.DT_TRACK_FLOAT)
            .field("CollisionSpin", "mCollisionSpin", DefFieldType.DT_TRACK_FLOAT)
            .field("ClipTop", "mClipTop", DefFieldType.DT_TRACK_FLOAT)
            .field("ClipBottom", "mClipBottom", DefFieldType.DT_TRACK_FLOAT)
            .field("ClipLeft", "mClipLeft", DefFieldType.DT_TRACK_FLOAT)
            .field("ClipRight", "mClipRight", DefFieldType.DT_TRACK_FLOAT)
            .field("AnimationRate", "mAnimationRate", DefFieldType.DT_TRACK_FLOAT)
            .build(PvzpEmitterDefinition::pvzpEmitterDefinitionConstructor);

    /** Ported from src/PvzpLib/Definition.cpp:136 ({@code gParticleDefMap}). */
    public static final DefMap gParticleDefMap = new DefMapBuilder()
            .field("Emitter", "mEmitterDefs", DefFieldType.DT_ARRAY, gEmitterDefMap)
            .build(PvzpParticleDefinition::pvzpParticleDefinitionConstructor);

    /** Ported from src/PvzpLib/PvzpParticle.cpp:37 ({@code gLawnParticleArray}, 106 entries). */
    public static final ParticleParams[] gLawnParticleArray = {
            new ParticleParams(ParticleEffect.PARTICLE_MELONSPLASH, "particles/MelonImpact.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_WINTERMELON, "particles/WinterMelonImpact.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_FUMECLOUD, "particles/FumeCloud.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POPCORNSPLASH, "particles/PopcornSplash.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POWIE, "particles/Powie.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_JACKEXPLODE, "particles/JackExplode.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_HEAD, "particles/ZombieHead.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_ARM, "particles/ZombieArm.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_TRAFFIC_CONE, "particles/ZombieTrafficCone.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_PAIL, "particles/ZombiePail.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_HELMET, "particles/ZombieHelmet.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_FLAG, "particles/ZombieFlag.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_DOOR, "particles/ZombieDoor.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_NEWSPAPER, "particles/ZombieNewspaper.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_HEADLIGHT, "particles/ZombieHeadLight.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POW, "particles/Pow.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_POGO, "particles/ZombiePogo.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_NEWSPAPER_HEAD, "particles/ZombieNewspaperHead.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_BALLOON_HEAD, "particles/ZombieBalloonHead.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SOD_ROLL, "particles/SodRoll.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_GRAVE_STONE_RISE, "particles/GraveStoneRise.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PLANTING, "particles/Planting.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PLANTING_POOL, "particles/PlantingPool.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_RISE, "particles/ZombieRise.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_GRAVE_BUSTER, "particles/GraveBuster.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_GRAVE_BUSTER_DIE, "particles/GraveBusterDie.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POOL_SPLASH, "particles/PoolSplash.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ICE_SPARKLE, "particles/IceSparkle.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SEED_PACKET, "particles/SeedPacket.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_TALL_NUT_BLOCK, "particles/TallNutBlock.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_DOOM, "particles/Doom.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_DIGGER_RISE, "particles/DiggerRise.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_DIGGER_TUNNEL, "particles/DiggerTunnel.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_DANCER_RISE, "particles/DancerRise.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POOL_SPARKLY, "particles/PoolSparkly.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_WALLNUT_EAT_SMALL, "particles/WallnutEatSmall.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_WALLNUT_EAT_LARGE, "particles/WallnutEatLarge.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PEA_SPLAT, "particles/PeaSplat.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_BUTTER_SPLAT, "particles/ButterSplat.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_CABBAGE_SPLAT, "particles/CabbageSplat.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PUFF_SPLAT, "particles/PuffSplat.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_STAR_SPLAT, "particles/StarSplat.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ICE_TRAP, "particles/IceTrap.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SNOWPEA_SPLAT, "particles/SnowPeaSplat.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SNOWPEA_PUFF, "particles/SnowPeaPuff.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SNOWPEA_TRAIL, "particles/SnowPeaTrail.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_LANTERN_SHINE, "particles/LanternShine.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SEED_PACKET_PICKUP, "particles/Award.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POTATO_MINE, "particles/PotatoMine.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POTATO_MINE_RISE, "particles/PotatoMineRise.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PUFFSHROOM_TRAIL, "particles/PuffShroomTrail.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PUFFSHROOM_MUZZLE, "particles/PuffShroomMuzzle.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SEED_PACKET_FLASH, "particles/SeedPacketFlash.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_WHACK_A_ZOMBIE_RISE, "particles/WhackAZombieRise.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_LADDER, "particles/ZombieLadder.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_UMBRELLA_REFLECT, "particles/UmbrellaReflect.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SEED_PACKET_PICK, "particles/SeedPacketPick.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ICE_TRAP_ZOMBIE, "particles/IceTrapZombie.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ICE_TRAP_RELEASE, "particles/IceTrapRelease.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZAMBONI_SMOKE, "particles/ZamboniSmoke.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_GLOOMCLOUD, "particles/GloomCloud.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_POGO_HEAD, "particles/ZombiePogoHead.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZAMBONI_TIRE, "particles/ZamboniTire.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION, "particles/ZamboniExplosion.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION2, "particles/ZamboniExplosion2.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_CATAPULT_EXPLOSION, "particles/CatapultExplosion.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_MOWER_CLOUD, "particles/MowerCloud.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_BOSS_ICE_BALL, "particles/BossIceBallTrail.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_BLASTMARK, "particles/BlastMark.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_COIN_PICKUP_ARROW, "particles/CoinPickupArrow.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PRESENT_PICKUP, "particles/PresentPickup.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_IMITATER_MORPH, "particles/ImitaterMorph.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_MOWERED_ZOMBIE_HEAD, "particles/MoweredZombieHead.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_MOWERED_ZOMBIE_ARM, "particles/MoweredZombieArm.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_HEAD_POOL, "particles/ZombieHeadPool.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_BOSS_FIREBALL, "particles/Zombie_boss_fireball.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_FIREBALL_DEATH, "particles/FireballDeath.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ICEBALL_DEATH, "particles/IceballDeath.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ICEBALL_TRAIL, "particles/Iceball_Trail.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_FIREBALL_TRAIL, "particles/Fireball_Trail.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_BOSS_EXPLOSION, "particles/BossExplosion.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_SCREEN_FLASH, "particles/ScreenFlash.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_TROPHY_SPARKLE, "particles/TrophySparkle.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PORTAL_CIRCLE, "particles/PortalCircle.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PORTAL_SQUARE, "particles/PortalSquare.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POTTED_PLANT_GLOW, "particles/PottedPlantGlow.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POTTED_WATER_PLANT_GLOW, "particles/PottedWaterPlantGlow.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_POTTED_ZEN_GLOW, "particles/PottedZenGlow.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_MIND_CONTROL, "particles/MindControl.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_VASE_SHATTER, "particles/VaseShatter.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_VASE_SHATTER_LEAF, "particles/VaseShatterLeaf.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_VASE_SHATTER_ZOMBIE, "particles/VaseShatterZombie.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_AWARD_PICKUP_ARROW, "particles/AwardPickupArrow.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_SEAWEED, "particles/Zombie_seaweed.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_MUSTACHE, "particles/ZombieMustache.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_SUNGLASS, "particles/ZombieFutureGlasses.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_PINATA, "particles/Pinata.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_DUST_SQUASH, "particles/Dust_Squash.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_DUST_FOOT, "particles/Dust_Foot.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_ZOMBIE_DAISIES, "particles/Daisy.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_CREDIT_STROBE, "particles/Credits_Strobe.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_CREDITS_RAYSWIPE, "particles/Credits_RaysWipe.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_CREDITS_ZOMBIEHEADWIPE, "particles/Credits_ZombieHeadWipe.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_STARBURST, "particles/Starburst.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_CREDITS_FOG, "particles/Credits_fog.xml"),
            new ParticleParams(ParticleEffect.PARTICLE_PERSENT_PICK_UP_ARROW, "particles/UpsellArrow.xml"),
    };

    /** Ported from src/PvzpLib/PvzpParticle.cpp:146 ({@code PvzpParticleLoadADef}). */
    public static boolean pvzpParticleLoadADef(PvzpParticleDefinition theParticleDef, String theParticleFileName) {
        if (!Definition.loadXML(theParticleFileName, gParticleDefMap, theParticleDef)) {
            PvzpDebug.pvzpErrorMessageBox("Failed to load particle '" + theParticleFileName + "'", "Error");
            return false;
        } else {
            PvzpEmitterDefinition[] aEmitterDefs = (PvzpEmitterDefinition[]) theParticleDef.mEmitterDefs.mArrayData;
            for (int i = 0; i < theParticleDef.mEmitterDefs.mArrayCount; i++) {
                PvzpEmitterDefinition aDef = aEmitterDefs[i];
                Definition.floatTrackSetDefault(aDef.mSystemDuration, 0.0f);
                Definition.floatTrackSetDefault(aDef.mSpawnRate, 0.0f);
                Definition.floatTrackSetDefault(aDef.mSpawnMinActive, -1.0f);
                Definition.floatTrackSetDefault(aDef.mSpawnMaxActive, -1.0f);
                Definition.floatTrackSetDefault(aDef.mSpawnMaxLaunched, -1.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterRadius, 0.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterOffsetX, 0.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterOffsetY, 0.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterBoxX, 0.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterBoxY, 0.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterSkewX, 0.0f);
                Definition.floatTrackSetDefault(aDef.mEmitterSkewY, 0.0f);
                Definition.floatTrackSetDefault(aDef.mParticleDuration, 100.0f);
                Definition.floatTrackSetDefault(aDef.mLaunchSpeed, 0.0f);
                Definition.floatTrackSetDefault(aDef.mSystemRed, 1.0f);
                Definition.floatTrackSetDefault(aDef.mSystemGreen, 1.0f);
                Definition.floatTrackSetDefault(aDef.mSystemBlue, 1.0f);
                Definition.floatTrackSetDefault(aDef.mSystemAlpha, 1.0f);
                Definition.floatTrackSetDefault(aDef.mSystemBrightness, 1.0f);
                Definition.floatTrackSetDefault(aDef.mLaunchAngle, 0.0f);
                Definition.floatTrackSetDefault(aDef.mCrossFadeDuration, 0.0f);
                Definition.floatTrackSetDefault(aDef.mParticleRed, 1.0f);
                Definition.floatTrackSetDefault(aDef.mParticleGreen, 1.0f);
                Definition.floatTrackSetDefault(aDef.mParticleBlue, 1.0f);
                Definition.floatTrackSetDefault(aDef.mParticleAlpha, 1.0f);
                Definition.floatTrackSetDefault(aDef.mParticleBrightness, 1.0f);
                Definition.floatTrackSetDefault(aDef.mParticleSpinAngle, 0.0f);
                Definition.floatTrackSetDefault(aDef.mParticleSpinSpeed, 0.0f);
                Definition.floatTrackSetDefault(aDef.mParticleScale, 1.0f);
                Definition.floatTrackSetDefault(aDef.mParticleStretch, 1.0f);
                Definition.floatTrackSetDefault(aDef.mCollisionReflect, 0.0f);
                Definition.floatTrackSetDefault(aDef.mCollisionSpin, 0.0f);
                Definition.floatTrackSetDefault(aDef.mClipTop, 0.0f);
                Definition.floatTrackSetDefault(aDef.mClipBottom, 0.0f);
                Definition.floatTrackSetDefault(aDef.mClipLeft, 0.0f);
                Definition.floatTrackSetDefault(aDef.mClipRight, 0.0f);
                Definition.floatTrackSetDefault(aDef.mAnimationRate, 0.0f);
                if (aDef.mImage instanceof MemoryImage aMemoryImage) {
                    aMemoryImage.mRenderFlags |= MemoryImage.RenderImageFlag_MinimizeNumSubdivisions;
                }
            }
            return true;
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:197 ({@code PvzpParticleLoadDefinitions}). */
    public static void pvzpParticleLoadDefinitions(ParticleParams[] theParticleParamArray, int theParticleParamArraySize) {
        gParticleParamArraySize = theParticleParamArraySize;
        gParticleParamArray = theParticleParamArray;
        gParticleDefCount = theParticleParamArraySize;
        gParticleDefArray = new PvzpParticleDefinition[theParticleParamArraySize];

        for (int i = 0; i < gParticleParamArraySize; i++) {
            ParticleParams aParticleParams = gParticleParamArray[i];
            // C++ `new PvzpParticleDefinition[n]` default-constructs every element; Java arrays start null.
            gParticleDefArray[i] = new PvzpParticleDefinition();
            if (!pvzpParticleLoadADef(gParticleDefArray[i], aParticleParams.mParticleFileName)) {
                PvzpDebug.pvzpErrorMessageBox("Failed to load particle '" + aParticleParams.mParticleFileName + "'", "Error");
            }
            cn.enaium.fabricpvz.portable.sexy.SexyAppBase.current.mCompletedLoadingThreadTasks.addAndGet(6);
        }
    }

    /** Ported from src/PvzpLib/PvzpParticle.cpp:224 ({@code PvzpParticleFreeDefinitions}). */
    public static void pvzpParticleFreeDefinitions() {
        for (int i = 0; i < gParticleDefCount; i++) {
            Definition.freeMap(gParticleDefMap, gParticleDefArray[i]);
        }
        gParticleDefArray = null;
        gParticleDefCount = 0;
        gParticleParamArray = null;
        gParticleParamArraySize = 0;
    }
}
