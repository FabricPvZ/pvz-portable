package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;

/**
 * Ported from src/PvzpLib/PvzpFoley.h:207 (class PvzpFoley) and src/PvzpLib/PvzpFoley.cpp.
 *
 * <p>Sound-effect instance bookkeeping for the {@code FOLEY_*} catalogue: per type it keeps up to
 * {@link FoleyTypeData#MAX_FOLEY_INSTANCES} voices, applies the "played too recently" anti-overlap
 * rule (10 centiseconds), the {@code ONE_AT_A_TIME} refcount rule, {@code DONT_REPEAT} variation
 * selection, pause/mute handling and the music-volume sharing for {@code USES_MUSIC_VOLUME} sounds.
 * The cue table {@link #gLawnFoleyParamArray} is ported entry-for-entry.
 *
 * <p>PORT-NOTE: upstream's {@code PvzpDSoundInstance} (a {@code SDLSoundInstance} subclass exposing
 * sound position) is not ported — it only adds unused {@code GetSoundPosition}/{@code SetSoundPosition}
 * stubs and the pause path only needs the base {@link SoundInstance#stop()}/{@link SoundInstance#play}.
 */
public class PvzpFoley {

    /** Ported from src/PvzpLib/PvzpFoley.h:30 ({@code MAX_FOLEY_TYPES}). */
    public static final int MAX_FOLEY_TYPES = 110;

    public static int gFoleyParamArraySize;
    public static FoleyParams[] gFoleyParamArray;

    public FoleyTypeData[] mFoleyTypeData = new FoleyTypeData[MAX_FOLEY_TYPES];

    /** Ported from src/PvzpLib/PvzpFoley.cpp:30 ({@code gLawnFoleyParamArray}). */
    public static final FoleyParams[] gLawnFoleyParamArray = {
            new FoleyParams(FoleyType.FOLEY_SUN, 10.0f, 0, Resources.SOUND_POINTS_ID),
            new FoleyParams(FoleyType.FOLEY_SPLAT, 10.0f, 0, Resources.SOUND_SPLAT_ID, Resources.SOUND_SPLAT2_ID, Resources.SOUND_SPLAT3_ID),
            new FoleyParams(FoleyType.FOLEY_LAWNMOWER, 10.0f, 0, Resources.SOUND_LAWNMOWER_ID),
            new FoleyParams(FoleyType.FOLEY_THROW, 10.0f, 0, Resources.SOUND_THROW_ID, Resources.SOUND_THROW_ID, Resources.SOUND_THROW_ID, Resources.SOUND_THROW2_ID),
            new FoleyParams(FoleyType.FOLEY_SPAWN_SUN, 10.0f, 0, Resources.SOUND_THROW_ID),
            new FoleyParams(FoleyType.FOLEY_CHOMP, 0.0f, 0, Resources.SOUND_CHOMP_ID, Resources.SOUND_CHOMP2_ID),
            new FoleyParams(FoleyType.FOLEY_CHOMP_SOFT, 4.0f, 0, Resources.SOUND_CHOMPSOFT_ID),
            new FoleyParams(FoleyType.FOLEY_PLANT, 0.0f, 0, Resources.SOUND_PLANT_ID, Resources.SOUND_PLANT2_ID),
            new FoleyParams(FoleyType.FOLEY_USE_SHOVEL, 0.0f, 0, Resources.SOUND_PLANT2_ID),
            new FoleyParams(FoleyType.FOLEY_DROP, 0.0f, 0, Resources.SOUND_TAP2_ID),
            new FoleyParams(FoleyType.FOLEY_BLEEP, 0.0f, 0, Resources.SOUND_BLEEP_ID),
            new FoleyParams(FoleyType.FOLEY_GROAN, 0.0f, 0, Resources.SOUND_GROAN_ID, Resources.SOUND_GROAN2_ID, Resources.SOUND_GROAN3_ID, Resources.SOUND_GROAN4_ID, Resources.SOUND_GROAN5_ID, Resources.SOUND_GROAN6_ID),
            new FoleyParams(FoleyType.FOLEY_BRAINS, 0.0f, 0, Resources.SOUND_GROAN_ID, Resources.SOUND_GROAN2_ID, Resources.SOUND_GROAN3_ID, Resources.SOUND_GROAN4_ID, Resources.SOUND_GROAN5_ID, Resources.SOUND_GROAN6_ID, Resources.SOUND_SUKHBIR4_ID, Resources.SOUND_SUKHBIR5_ID, Resources.SOUND_SUKHBIR6_ID),
            new FoleyParams(FoleyType.FOLEY_SUKHBIR, 0.0f, 0, Resources.SOUND_GROAN_ID, Resources.SOUND_GROAN2_ID, Resources.SOUND_GROAN3_ID, Resources.SOUND_GROAN4_ID, Resources.SOUND_GROAN5_ID, Resources.SOUND_GROAN6_ID, Resources.SOUND_SUKHBIR_ID, Resources.SOUND_SUKHBIR2_ID, Resources.SOUND_SUKHBIR3_ID),
            new FoleyParams(FoleyType.FOLEY_JACKINTHEBOX, 0.0f, 7, Resources.SOUND_JACKINTHEBOX_ID),
            new FoleyParams(FoleyType.FOLEY_ART_CHALLENGE, 0.0f, 0, Resources.SOUND_DIAMOND_ID),
            new FoleyParams(FoleyType.FOLEY_ZAMBONI, 5.0f, 0, Resources.SOUND_ZAMBONI_ID),
            new FoleyParams(FoleyType.FOLEY_THUNDER, 10.0f, 0, Resources.SOUND_THUNDER_ID),
            new FoleyParams(FoleyType.FOLEY_FROZEN, 0.0f, 0, Resources.SOUND_FROZEN_ID),
            new FoleyParams(FoleyType.FOLEY_ZOMBIESPLASH, 10.0f, 0, Resources.SOUND_PLANT_WATER_ID, Resources.SOUND_ZOMBIE_ENTERING_WATER_ID),
            new FoleyParams(FoleyType.FOLEY_BOWLINGIMPACT, -3.0f, 0, Resources.SOUND_BOWLINGIMPACT_ID),
            new FoleyParams(FoleyType.FOLEY_SQUISH, 0.0f, 0, Resources.SOUND_CHOMP_ID, Resources.SOUND_CHOMP2_ID),
            new FoleyParams(FoleyType.FOLEY_TIRE_POP, 0.0f, 0, Resources.SOUND_BALLOON_POP_ID),
            new FoleyParams(FoleyType.FOLEY_EXPLOSION, 0.0f, 0, Resources.SOUND_EXPLOSION_ID),
            new FoleyParams(FoleyType.FOLEY_SLURP, 2.0f, 0, Resources.SOUND_SLURP_ID),
            new FoleyParams(FoleyType.FOLEY_LIMBS_POP, 10.0f, 0, Resources.SOUND_LIMBS_POP_ID),
            new FoleyParams(FoleyType.FOLEY_POGO_ZOMBIE, 4.0f, 0, Resources.SOUND_POGO_ZOMBIE_ID),
            new FoleyParams(FoleyType.FOLEY_SNOW_PEA_SPARKLES, 10.0f, 0, Resources.SOUND_SNOW_PEA_SPARKLES_ID),
            new FoleyParams(FoleyType.FOLEY_ZOMBIE_FALLING, 10.0f, 0, Resources.SOUND_ZOMBIE_FALLING_1_ID, Resources.SOUND_ZOMBIE_FALLING_2_ID),
            new FoleyParams(FoleyType.FOLEY_PUFF, 10.0f, 0, Resources.SOUND_PUFF_ID),
            new FoleyParams(FoleyType.FOLEY_FUME, 10.0f, 0, Resources.SOUND_FUME_ID),
            new FoleyParams(FoleyType.FOLEY_COIN, 10.0f, 0, Resources.SOUND_COIN_ID),
            new FoleyParams(FoleyType.FOLEY_KERNEL_SPLAT, 10.0f, 0, Resources.SOUND_KERNELPULT_ID, Resources.SOUND_KERNELPULT2_ID),
            new FoleyParams(FoleyType.FOLEY_DIGGER, 0.0f, 7, Resources.SOUND_DIGGER_ZOMBIE_ID),
            new FoleyParams(FoleyType.FOLEY_JACK_SURPRISE, 1.0f, 0, Resources.SOUND_JACK_SURPRISE_ID, Resources.SOUND_JACK_SURPRISE_ID, Resources.SOUND_JACK_SURPRISE2_ID),
            new FoleyParams(FoleyType.FOLEY_VASE_BREAKING, -5.0f, 0, Resources.SOUND_VASE_BREAKING_ID),
            new FoleyParams(FoleyType.FOLEY_POOL_CLEANER, 2.0f, 0, Resources.SOUND_POOL_CLEANER_ID),
            new FoleyParams(FoleyType.FOLEY_BASKETBALL, 10.0f, 0, Resources.SOUND_BASKETBALL_ID),
            new FoleyParams(FoleyType.FOLEY_IGNITE, 5.0f, 0, Resources.SOUND_IGNITE_ID, Resources.SOUND_IGNITE_ID, Resources.SOUND_IGNITE_ID, Resources.SOUND_IGNITE2_ID),
            new FoleyParams(FoleyType.FOLEY_FIREPEA, 10.0f, 0, Resources.SOUND_FIREPEA_ID),
            new FoleyParams(FoleyType.FOLEY_THUMP, 2.0f, 0, Resources.SOUND_GARGANTUAR_THUMP_ID),
            new FoleyParams(FoleyType.FOLEY_SQUASH_HMM, 2.0f, 0, Resources.SOUND_SQUASH_HMM_ID, Resources.SOUND_SQUASH_HMM_ID, Resources.SOUND_SQUASH_HMM2_ID),
            new FoleyParams(FoleyType.FOLEY_MAGNETSHROOM, 2.0f, 0, Resources.SOUND_MAGNETSHROOM_ID),
            new FoleyParams(FoleyType.FOLEY_BUTTER, 2.0f, 0, Resources.SOUND_BUTTER_ID),
            new FoleyParams(FoleyType.FOLEY_BUNGEE_SCREAM, 2.0f, 0, Resources.SOUND_BUNGEE_SCREAM_ID, Resources.SOUND_BUNGEE_SCREAM2_ID, Resources.SOUND_BUNGEE_SCREAM3_ID),
            new FoleyParams(FoleyType.FOLEY_BOSS_EXPLOSION_SMALL, 2.0f, 0, Resources.SOUND_EXPLOSION_ID),
            new FoleyParams(FoleyType.FOLEY_SHIELD_HIT, 10.0f, 0, Resources.SOUND_SHIELDHIT_ID, Resources.SOUND_SHIELDHIT2_ID),
            new FoleyParams(FoleyType.FOLEY_SWING, 2.0f, 0, Resources.SOUND_SWING_ID),
            new FoleyParams(FoleyType.FOLEY_BONK, 2.0f, 0, Resources.SOUND_BONK_ID),
            new FoleyParams(FoleyType.FOLEY_RAIN, 0.0f, 5, Resources.SOUND_RAIN_ID),
            new FoleyParams(FoleyType.FOLEY_DOLPHIN_BEFORE_JUMPING, 0.0f, 0, Resources.SOUND_DOLPHIN_BEFORE_JUMPING_ID),
            new FoleyParams(FoleyType.FOLEY_DOLPHIN_APPEARS, 0.0f, 0, Resources.SOUND_DOLPHIN_APPEARS_ID),
            new FoleyParams(FoleyType.FOLEY_PLANT_WATER, 0.0f, 0, Resources.SOUND_PLANT_WATER_ID),
            new FoleyParams(FoleyType.FOLEY_ZOMBIE_ENTERING_WATER, 0.0f, 0, Resources.SOUND_ZOMBIE_ENTERING_WATER_ID),
            new FoleyParams(FoleyType.FOLEY_GRAVEBUSTERCHOMP, 0.0f, 4, Resources.SOUND_GRAVEBUSTERCHOMP_ID),
            new FoleyParams(FoleyType.FOLEY_CHERRYBOMB, 0.0f, 0, Resources.SOUND_CHERRYBOMB_ID),
            new FoleyParams(FoleyType.FOLEY_JALAPENO_IGNITE, 0.0f, 0, Resources.SOUND_JALAPENO_ID),
            new FoleyParams(FoleyType.FOLEY_REVERSE_EXPLOSION, 0.0f, 0, Resources.SOUND_REVERSE_EXPLOSION_ID),
            new FoleyParams(FoleyType.FOLEY_PLASTIC_HIT, 5.0f, 0, Resources.SOUND_PLASTICHIT_ID, Resources.SOUND_PLASTICHIT2_ID),
            new FoleyParams(FoleyType.FOLEY_WINMUSIC, 0.0f, 8, Resources.SOUND_WINMUSIC_ID),
            new FoleyParams(FoleyType.FOLEY_BALLOONINFLATE, 10.0f, 0, Resources.SOUND_BALLOONINFLATE_ID),
            new FoleyParams(FoleyType.FOLEY_BIGCHOMP, -2.0f, 0, Resources.SOUND_BIGCHOMP_ID),
            new FoleyParams(FoleyType.FOLEY_MELONIMPACT, -5.0f, 0, Resources.SOUND_MELONIMPACT_ID, Resources.SOUND_MELONIMPACT2_ID),
            new FoleyParams(FoleyType.FOLEY_PLANTGROW, -2.0f, 0, Resources.SOUND_PLANTGROW_ID),
            new FoleyParams(FoleyType.FOLEY_SHOOP, -5.0f, 0, Resources.SOUND_SHOOP_ID),
            new FoleyParams(FoleyType.FOLEY_JUICY, 2.0f, 0, Resources.SOUND_JUICY_ID),
            new FoleyParams(FoleyType.FOLEY_NEWSPAPER_RARRGH, -2.0f, 0, Resources.SOUND_NEWSPAPER_RARRGH_ID, Resources.SOUND_NEWSPAPER_RARRGH2_ID, Resources.SOUND_NEWSPAPER_RARRGH2_ID),
            new FoleyParams(FoleyType.FOLEY_NEWSPAPER_RIP, -2.0f, 0, Resources.SOUND_NEWSPAPER_RIP_ID),
            new FoleyParams(FoleyType.FOLEY_FLOOP, 0.0f, 0, Resources.SOUND_FLOOP_ID),
            new FoleyParams(FoleyType.FOLEY_COFFEE, 0.0f, 0, Resources.SOUND_COFFEE_ID),
            new FoleyParams(FoleyType.FOLEY_LOW_GROAN, 2.0f, 0, Resources.SOUND_LOWGROAN_ID, Resources.SOUND_LOWGROAN2_ID),
            new FoleyParams(FoleyType.FOLEY_PRIZE, 0.0f, 0, Resources.SOUND_PRIZE_ID),
            new FoleyParams(FoleyType.FOLEY_YUCK, 1.0f, 0, Resources.SOUND_YUCK_ID, Resources.SOUND_YUCK_ID, Resources.SOUND_YUCK2_ID),
            new FoleyParams(FoleyType.FOLEY_UMBRELLA, 2.0f, 0, Resources.SOUND_THROW2_ID),
            new FoleyParams(FoleyType.FOLEY_GRASSSTEP, 2.0f, 0, Resources.SOUND_GRASSSTEP_ID),
            new FoleyParams(FoleyType.FOLEY_SHOVEL, 5.0f, 0, Resources.SOUND_SHOVEL_ID),
            new FoleyParams(FoleyType.FOLEY_COB_LAUNCH, 10.0f, 0, Resources.SOUND_COBLAUNCH_ID),
            new FoleyParams(FoleyType.FOLEY_WATERING, 10.0f, 0, Resources.SOUND_WATERING_ID),
            new FoleyParams(FoleyType.FOLEY_POLEVAULT, 5.0f, 0, Resources.SOUND_POLEVAULT_ID),
            new FoleyParams(FoleyType.FOLEY_GRAVESTONE_RUMBLE, 10.0f, 0, Resources.SOUND_GRAVESTONE_RUMBLE_ID),
            new FoleyParams(FoleyType.FOLEY_DIRT_RISE, 5.0f, 0, Resources.SOUND_DIRT_RISE_ID),
            new FoleyParams(FoleyType.FOLEY_FERTILIZER, 0.0f, 0, Resources.SOUND_FERTILIZER_ID),
            new FoleyParams(FoleyType.FOLEY_PORTAL, 0.0f, 0, Resources.SOUND_PORTAL_ID),
            new FoleyParams(FoleyType.FOLEY_WAKEUP, 0.0f, 0, Resources.SOUND_WAKEUP_ID),
            new FoleyParams(FoleyType.FOLEY_BUGSPRAY, 0.0f, 0, Resources.SOUND_BUGSPRAY_ID),
            new FoleyParams(FoleyType.FOLEY_SCREAM, 0.0f, 0, Resources.SOUND_SCREAM_ID),
            new FoleyParams(FoleyType.FOLEY_PAPER, 0.0f, 0, Resources.SOUND_PAPER_ID),
            new FoleyParams(FoleyType.FOLEY_MONEYFALLS, 0.0f, 0, Resources.SOUND_MONEYFALLS_ID),
            new FoleyParams(FoleyType.FOLEY_IMP, 5.0f, 0, Resources.SOUND_IMP_ID, Resources.SOUND_IMP2_ID),
            new FoleyParams(FoleyType.FOLEY_HYDRAULIC_SHORT, 3.0f, 0, Resources.SOUND_HYDRAULIC_SHORT_ID),
            new FoleyParams(FoleyType.FOLEY_HYDRAULIC, 0.0f, 0, Resources.SOUND_HYDRAULIC_ID),
            new FoleyParams(FoleyType.FOLEY_GARGANTUDEATH, 3.0f, 0, Resources.SOUND_GARGANTUDEATH_ID),
            new FoleyParams(FoleyType.FOLEY_CERAMIC, 0.0f, 0, Resources.SOUND_CERAMIC_ID),
            new FoleyParams(FoleyType.FOLEY_BOSS_BOULDER_ATTACK, 0.0f, 0, Resources.SOUND_BOSSBOULDERATTACK_ID),
            new FoleyParams(FoleyType.FOLEY_CHIME, 0.0f, 0, Resources.SOUND_CHIME_ID),
            new FoleyParams(FoleyType.FOLEY_CRAZY_DAVE_SHORT, 0.0f, 16, Resources.SOUND_CRAZYDAVESHORT1_ID, Resources.SOUND_CRAZYDAVESHORT2_ID, Resources.SOUND_CRAZYDAVESHORT3_ID),
            new FoleyParams(FoleyType.FOLEY_CRAZY_DAVE_LONG, 0.0f, 16, Resources.SOUND_CRAZYDAVELONG1_ID, Resources.SOUND_CRAZYDAVELONG2_ID, Resources.SOUND_CRAZYDAVELONG3_ID),
            new FoleyParams(FoleyType.FOLEY_CRAZY_DAVE_EXTRA_LONG, 0.0f, 16, Resources.SOUND_CRAZYDAVEEXTRALONG1_ID, Resources.SOUND_CRAZYDAVEEXTRALONG2_ID, Resources.SOUND_CRAZYDAVEEXTRALONG3_ID),
            new FoleyParams(FoleyType.FOLEY_CRAZY_DAVE_CRAZY, 0.0f, 0, Resources.SOUND_CRAZYDAVECRAZY_ID),
            new FoleyParams(FoleyType.FOLEY_PHONOGRAPH, 0.0f, 0, Resources.SOUND_PHONOGRAPH_ID),
            new FoleyParams(FoleyType.FOLEY_DANCER, 0.0f, 6, Resources.SOUND_DANCER_ID),
            new FoleyParams(FoleyType.FOLEY_FINAL_FANFARE, 0.0f, 0, Resources.SOUND_FINALFANFARE_ID),
            new FoleyParams(FoleyType.FOLEY_CRAZY_DAVE_SCREAM, 0.0f, 0, Resources.SOUND_CRAZYDAVESCREAM_ID),
            new FoleyParams(FoleyType.FOLEY_CRAZY_DAVE_SCREAM_2, 0.0f, 0, Resources.SOUND_CRAZYDAVESCREAM2_ID),
    };

    /** Ported from src/PvzpLib/PvzpFoley.cpp:56 (default construction of the 110 type slots). */
    public PvzpFoley() {
        for (int i = 0; i < MAX_FOLEY_TYPES; i++) {
            mFoleyTypeData[i] = new FoleyTypeData();
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:159 ({@code PvzpFoleyInitialize}). */
    public static void pvzpFoleyInitialize(FoleyParams[] theFoleyParamArray, int theFoleyParamArraySize) {
        gFoleyParamArray = theFoleyParamArray;
        gFoleyParamArraySize = theFoleyParamArraySize;
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:165 ({@code PvzpFoleyDispose}). */
    public static void pvzpFoleyDispose() {
        gFoleyParamArray = null;
        gFoleyParamArraySize = 0;
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:171 ({@code SoundSystemReleaseFinishedInstances}). */
    public static void soundSystemReleaseFinishedInstances(PvzpFoley theSoundSystem) {
        for (int aFoleyType = 0; aFoleyType < gFoleyParamArraySize; aFoleyType++) {
            for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
                FoleyInstance aFoleyInstance = theSoundSystem.mFoleyTypeData[aFoleyType].mFoleyInstances[i];
                if (aFoleyInstance.mRefCount != 0 && !aFoleyInstance.mPaused) {
                    if (!aFoleyInstance.mInstance.isPlaying()) {
                        aFoleyInstance.mInstance.release();
                        aFoleyInstance.mInstance = null;
                        aFoleyInstance.mRefCount = 0;
                    }
                }
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:189 ({@code SoundSystemHasFoleyPlayedTooRecently}). */
    public static boolean soundSystemHasFoleyPlayedTooRecently(PvzpFoley theSoundSystem, int theFoleyType) {
        FoleyTypeData aFoleyData = theSoundSystem.mFoleyTypeData[theFoleyType];
        for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
            FoleyInstance aFoleyInstance = aFoleyData.mFoleyInstances[i];
            if (aFoleyInstance.mRefCount != 0 && SexyAppBase.current.mUpdateCount - aFoleyInstance.mStartTime < 10) {
                return true;
            }
        }
        return false;
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:203 ({@code LookupFoley}). */
    public static FoleyParams lookupFoley(int theFoleyType) {
        return gFoleyParamArray[theFoleyType];
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:216 ({@code SoundSystemFindInstance}). */
    public static FoleyInstance soundSystemFindInstance(PvzpFoley theSoundSystem, int theFoleyType) {
        FoleyTypeData aFoleyData = theSoundSystem.mFoleyTypeData[theFoleyType];
        for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
            FoleyInstance aFoleyInstance = aFoleyData.mFoleyInstances[i];
            if (aFoleyInstance.mRefCount > 0) {
                return aFoleyInstance;
            }
        }
        return null;
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:225 ({@code SoundSystemGetFreeInstanceIndex}). */
    public static FoleyInstance soundSystemGetFreeInstanceIndex(PvzpFoley theSoundSystem, int theFoleyType) {
        FoleyTypeData aFoleyData = theSoundSystem.mFoleyTypeData[theFoleyType];
        for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
            FoleyInstance aFoleyInstance = aFoleyData.mFoleyInstances[i];
            if (aFoleyInstance.mRefCount == 0) {
                return aFoleyInstance;
            }
        }
        return null;
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:247. */
    public void playFoleyPitch(int theFoleyType, float thePitch) {
        FoleyParams aFoleyParams = lookupFoley(theFoleyType);
        soundSystemReleaseFinishedInstances(this);
        if (soundSystemHasFoleyPlayedTooRecently(this, theFoleyType) && !testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_LOOP)) {
            return;
        }

        if (testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_ONE_AT_A_TIME)) {
            FoleyInstance aFoleyInstance = soundSystemFindInstance(this, theFoleyType);
            if (aFoleyInstance != null) {
                aFoleyInstance.mRefCount++;
                aFoleyInstance.mStartTime = SexyAppBase.current.mUpdateCount;
                return;
            }
        }
        FoleyInstance aFoleyInstance = soundSystemGetFreeInstanceIndex(this, theFoleyType);
        if (aFoleyInstance == null) {
            return;
        }

        int aVariations = 0;
        int[] aVariationsArray = new int[10];
        FoleyTypeData aFoleyData = mFoleyTypeData[theFoleyType];
        for (int i = 0; i < 10; i++) {
            if (!testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_DONT_REPEAT) || aFoleyData.mLastVariationPlayed != i) {
                if (aFoleyParams.mSfxID[i] == 0) {
                    break;
                }
                aVariationsArray[aVariations] = i;
                aVariations++;
            }
        }
        int aVariation = aVariationsArray[Common.rand(aVariations)];
        aFoleyData.mLastVariationPlayed = aVariation;
        SoundInstance aSoundInstance = SexyAppBase.current.getSoundManager().getSoundInstance(Resources.getSoundById(aFoleyParams.mSfxID[aVariation]));
        if (aSoundInstance == null) {
            return;
        }

        aFoleyInstance.mInstance = aSoundInstance;
        aFoleyInstance.mRefCount = 1;
        aFoleyInstance.mStartTime = SexyAppBase.current.mUpdateCount;
        aFoleyInstance.mPaused = false;
        aFoleyData.mLastVariationPlayed = aVariation;
        if (thePitch != 0.0f) {
            aSoundInstance.adjustPitch(thePitch);
        }
        if (testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_USES_MUSIC_VOLUME)) {
            applyMusicVolume(aFoleyInstance);
        }
        boolean aIsLooping = testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_LOOP);
        aSoundInstance.play(aIsLooping, false);
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:301. */
    public void playFoley(int theFoleyType) {
        FoleyParams aFoleyParams = lookupFoley(theFoleyType);
        float aPitch = 0.0f;
        if (aFoleyParams.mPitchRange != 0.0f) {
            aPitch = Common.rand(aFoleyParams.mPitchRange);
        }
        playFoleyPitch(theFoleyType, aPitch);
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:310. */
    public void stopFoley(int theFoleyType) {
        soundSystemReleaseFinishedInstances(this);
        FoleyInstance aFoleyInstance = soundSystemFindInstance(this, theFoleyType);
        if (aFoleyInstance == null) {
            return;
        }

        aFoleyInstance.mRefCount--;
        if (aFoleyInstance.mRefCount == 0) {
            aFoleyInstance.mInstance.release();
            aFoleyInstance.mInstance = null;
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:327. */
    public void gamePause(boolean theEnteringPause) {
        soundSystemReleaseFinishedInstances(this);
        for (int aFoleyType = 0; aFoleyType < gFoleyParamArraySize; aFoleyType++) {
            FoleyParams aFoleyParams = lookupFoley(aFoleyType);
            if (testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_MUTE_ON_PAUSE)) {
                FoleyTypeData aFoleyData = mFoleyTypeData[aFoleyType];
                for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
                    FoleyInstance aFoleyInstance = aFoleyData.mFoleyInstances[i];
                    if (aFoleyInstance.mRefCount != 0) {
                        SoundInstance aSoundInstance = aFoleyInstance.mInstance;
                        if (theEnteringPause) {
                            aFoleyInstance.mPaused = true;
                            aFoleyInstance.mPauseOffset = 0;
                            aSoundInstance.stop();
                        } else if (aFoleyInstance.mPaused) {
                            aFoleyInstance.mPaused = false;
                            boolean aIsLooping = testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_LOOP);
                            aSoundInstance.play(aIsLooping, false);
                        }
                    }
                }
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:362. */
    public void cancelPausedFoley() {
        soundSystemReleaseFinishedInstances(this);
        for (int aFoleyType = 0; aFoleyType < gFoleyParamArraySize; aFoleyType++) {
            FoleyTypeData aFoleyData = mFoleyTypeData[aFoleyType];
            for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
                FoleyInstance aFoleyInstance = aFoleyData.mFoleyInstances[i];
                if (aFoleyInstance.mRefCount != 0 && aFoleyInstance.mPaused) {
                    aFoleyInstance.mRefCount = 0;
                    aFoleyInstance.mInstance.release();
                    aFoleyInstance.mInstance = null;
                }
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:381. */
    public void applyMusicVolume(FoleyInstance theFoleyInstance) {
        if (SexyAppBase.current.mSfxVolume < 1e-6) {
            theFoleyInstance.mInstance.setVolume(0.0);
        } else {
            theFoleyInstance.mInstance.setVolume(SexyAppBase.current.mMusicVolume / SexyAppBase.current.mSfxVolume);
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:389. */
    public void rehookupSoundWithMusicVolume() {
        soundSystemReleaseFinishedInstances(this);
        for (int aFoleyType = 0; aFoleyType < gFoleyParamArraySize; aFoleyType++) {
            FoleyParams aFoleyParams = lookupFoley(aFoleyType);
            if (testBit(aFoleyParams.mFoleyFlags, FoleyFlags.FOLEYFLAGS_USES_MUSIC_VOLUME)) {
                FoleyTypeData aFoleyData = mFoleyTypeData[aFoleyType];
                for (int i = 0; i < FoleyTypeData.MAX_FOLEY_INSTANCES; i++) {
                    FoleyInstance aFoleyInstance = aFoleyData.mFoleyInstances[i];
                    if (aFoleyInstance.mRefCount != 0) {
                        applyMusicVolume(aFoleyInstance);
                    }
                }
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpFoley.cpp:408. */
    public boolean isFoleyPlaying(int theFoleyType) {
        soundSystemReleaseFinishedInstances(this);
        return soundSystemFindInstance(this, theFoleyType) != null;
    }

    private static boolean testBit(int theNum, int theIdx) {
        return (theNum & (1 << theIdx)) != 0;
    }
}
