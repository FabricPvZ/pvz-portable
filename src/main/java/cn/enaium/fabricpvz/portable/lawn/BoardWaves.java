package cn.enaium.fabricpvz.portable.lawn;

import java.util.Arrays;

import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;

/**
 * Ported from src/Lawn/Board.cpp (wave composition / zombie-spawning slice).
 *
 * <p>Covers {@code Board.cpp:484} ({@code AddGraveStones}), {@code :520-768}
 * ({@code GetNumWavesPerFlag}..{@code GetLevelRandSeed}), {@code :1137-1220}
 * ({@code InitZombieWavesForLevel}..{@code InitZombieWaves}), {@code :2336-2595}
 * ({@code CanZombieSpawnOnLevel}..{@code CanAddBobSled}), {@code :4671-4950}
 * ({@code GetGraveStonesCount}..{@code SpawnZombieWave}), {@code :5177-5378}
 * ({@code UpdateSunSpawning}..{@code UpdateZombieSpawning}), {@code :5808}
 * ({@code RowCanHaveZombies}), {@code :9019} ({@code GetGraveStoneCount}), {@code :9159}
 * ({@code BungeeIsTargetingCell}), {@code :9342} ({@code GetNumWavesPerSurvivalStage}) and
 * {@code :9480-9496} ({@code NumberZombiesInWave}, {@code IsZombieTypeSpawnedOnly}).
 *
 * <p>Package-private static methods with {@code Board} as first parameter (except the two
 * {@code ZombiePicker} initializers, which upstream are free functions on the picker only), so
 * {@link Board} can delegate. Determinism: every random draw goes through
 * {@link Common#rand(int)} / {@link PvzpCommon} / {@link MTRand}; no identity comparisons.
 */
final class BoardWaves {
    // Board.cpp:59-68
    private static final int ZOMBIE_COUNTDOWN_FIRST_WAVE = 1800;
    private static final int ZOMBIE_COUNTDOWN = 2500;
    private static final int ZOMBIE_COUNTDOWN_RANGE = 600;
    private static final int ZOMBIE_COUNTDOWN_BEFORE_FLAG = 4500;
    private static final int ZOMBIE_COUNTDOWN_BEFORE_REPICK = 5499;
    private static final int SUN_COUNTDOWN = 425;
    private static final int SUN_COUNTDOWN_RANGE = 275;
    private static final int SUN_COUNTDOWN_MAX = 950;
    private static final int FOG_BLOW_RETURN_TIME = 2000;
    private static final int FLAG_RAISE_TIME = 100;

    private BoardWaves() {
    }

    // Board.cpp:520
    static int getNumWavesPerFlag(Board b) {
        return (b.mApp.isFirstTimeAdventureMode() && b.mNumWaves < 10) ? b.mNumWaves : 10;
    }

    // Board.cpp:525
    static boolean isFlagWave(Board b, int theWaveNumber) {
        if (b.mApp.isFirstTimeAdventureMode() && b.mLevel == 1) {
            return false;
        }

        int aWavesPerFlag = getNumWavesPerFlag(b);
        return theWaveNumber % aWavesPerFlag == aWavesPerFlag - 1;
    }

    // Board.cpp:534
    static void zombiePickerInitForWave(ZombiePicker theZombiePicker) {
        theZombiePicker.mZombieCount = 0;
        theZombiePicker.mZombiePoints = 0;
        Arrays.fill(theZombiePicker.mZombieTypeCount, 0);
    }

    // Board.cpp:541
    static void zombiePickerInit(ZombiePicker theZombiePicker) {
        zombiePickerInitForWave(theZombiePicker);
        Arrays.fill(theZombiePicker.mAllWavesZombieTypeCount, 0);
    }

    // Board.cpp:547
    static void putZombieInWave(Board b, int theZombieType, int theWaveNumber, ZombiePicker theZombiePicker) {
        b.mZombiesInWave[theWaveNumber][theZombiePicker.mZombieCount++] = theZombieType;
        if (theZombiePicker.mZombieCount < Board.MAX_ZOMBIES_IN_WAVE) {
            b.mZombiesInWave[theWaveNumber][theZombiePicker.mZombieCount] = ZombieType.ZOMBIE_INVALID;
        }
        theZombiePicker.mZombiePoints -= Zombie.getZombieDefinition(theZombieType).mZombieValue;
        theZombiePicker.mZombieTypeCount[theZombieType]++;
        theZombiePicker.mAllWavesZombieTypeCount[theZombieType]++;
    }

    // Board.cpp:560
    static void putInMissingZombies(Board b, int theWaveNumber, ZombiePicker theZombiePicker) {
        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            if (Zombie.getZombieDefinition(aZombieType) == null) {
                continue;
            }
            if (theZombiePicker.mZombieTypeCount[aZombieType] <= 0 && aZombieType != ZombieType.ZOMBIE_YETI
                    && canZombieSpawnOnLevel(aZombieType, b.mLevel)) {
                putZombieInWave(b, aZombieType, theWaveNumber, theZombiePicker);
            }
        }
    }

    // Board.cpp:571
    static void pickZombieWaves(Board b) {
        // Set the total number of waves
        if (b.mApp.isAdventureMode()) {
            if (b.mApp.isWhackAZombieLevel()) {
                b.mNumWaves = 8;
            } else {
                b.mNumWaves = Challenge.gZombieWaves[Math.max(0, Math.min(b.mLevel - 1, GameConstants.NUM_LEVELS - 1))];
                if (!b.mApp.isFirstTimeAdventureMode() && !b.mApp.isMiniBossLevel()) {
                    b.mNumWaves = b.mNumWaves < 10 ? 20 : b.mNumWaves + 10;
                }
            }
        } else {
            int aGameMode = b.mApp.mGameMode;
            if (b.mApp.isSurvivalMode() || aGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                b.mNumWaves = getNumWavesPerSurvivalStage(b);
            } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN || aGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                    || b.mApp.isSquirrelLevel()) {
                b.mNumWaves = 0;
            } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE) {
                b.mNumWaves = 12;
            } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING || aGameMode == GameMode.GAMEMODE_CHALLENGE_AIR_RAID
                    || aGameMode == GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER || aGameMode == GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY
                    || aGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT || aGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS
                    || aGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
                b.mNumWaves = 20;
            } else if (b.mApp.isStormyNightLevel() || b.mApp.isLittleTroubleLevel() || b.mApp.isBungeeBlitzLevel()
                    || aGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN || b.mApp.isShovelLevel() || aGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2
                    || aGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2 || aGameMode == GameMode.GAMEMODE_CHALLENGE_POGO_PARTY) {
                b.mNumWaves = 30;
            } else {
                b.mNumWaves = 40;
            }
        }

        ZombiePicker aZombiePicker = new ZombiePicker();
        zombiePickerInit(aZombiePicker);
        int aIntroZombieType = getIntroducedZombieType(b);

        for (int aWave = 0; aWave < b.mNumWaves; aWave++) {
            zombiePickerInitForWave(aZombiePicker);
            b.mZombiesInWave[aWave][0] = ZombieType.ZOMBIE_INVALID;

            boolean aIsFlagWave = isFlagWave(b, aWave);
            boolean aIsFinalWave = aWave == b.mNumWaves - 1;

            if (b.mApp.isBungeeBlitzLevel() && aIsFlagWave) {
                for (int i = 0; i < 5; i++) {
                    putZombieInWave(b, ZombieType.ZOMBIE_BUNGEE, aWave, aZombiePicker);
                }

                if (!aIsFinalWave) {
                    continue;
                }
            }

            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                aZombiePicker.mZombiePoints = (b.mChallenge.mSurvivalStage * getNumWavesPerSurvivalStage(b) + aWave + 10) * 2 / 5 + 1;
            } else if (b.mApp.isSurvivalMode() && b.mChallenge.mSurvivalStage > 0) {
                aZombiePicker.mZombiePoints = (b.mChallenge.mSurvivalStage * getNumWavesPerSurvivalStage(b) + aWave) * 2 / 5 + 1;
            } else if (b.mApp.isAdventureMode() && b.mApp.hasFinishedAdventure() && b.mLevel != 5) {
                aZombiePicker.mZombiePoints = aWave * 2 / 5 + 1;
            } else {
                aZombiePicker.mZombiePoints = aWave / 3 + 1;
            }

            if (aIsFlagWave) {
                int aPlainZombiesNum = Math.min(aZombiePicker.mZombiePoints, 8);
                aZombiePicker.mZombiePoints = (int) (aZombiePicker.mZombiePoints * 2.5f);

                if (b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS && b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2) {
                    for (int i = 0; i < aPlainZombiesNum; i++) {
                        putZombieInWave(b, ZombieType.ZOMBIE_NORMAL, aWave, aZombiePicker);
                    }
                    putZombieInWave(b, ZombieType.ZOMBIE_FLAG, aWave, aZombiePicker);
                }
            }

            // Certain levels multiply the zombie points
            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
                aZombiePicker.mZombiePoints *= 6;
            } else if (b.mApp.isLittleTroubleLevel() || b.mApp.isWallnutBowlingLevel()) {
                aZombiePicker.mZombiePoints *= 4;
            } else if (b.mApp.isMiniBossLevel()) {
                aZombiePicker.mZombiePoints *= 3;
            } else if (b.mApp.isStormyNightLevel() && b.mApp.isAdventureMode()) {
                aZombiePicker.mZombiePoints *= 3;
            } else if (b.mApp.isShovelLevel() || b.mApp.isBungeeBlitzLevel()
                    || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
                aZombiePicker.mZombiePoints *= 2;
            }

            // Newly introduced zombies have fixed spawns in specific waves
            if (aIntroZombieType != ZombieType.ZOMBIE_INVALID && aIntroZombieType != ZombieType.ZOMBIE_DUCKY_TUBE) {
                boolean aSpawnIntro = false;
                if (aIntroZombieType == ZombieType.ZOMBIE_DIGGER || aIntroZombieType == ZombieType.ZOMBIE_BALLOON) {
                    if (aWave + 1 == 7 || aIsFinalWave) {
                        aSpawnIntro = true;
                    }
                } else if (aIntroZombieType == ZombieType.ZOMBIE_YETI) {
                    if (aWave == b.mNumWaves / 2 && !b.mApp.mSawYeti) {
                        aSpawnIntro = true;
                    }
                } else if (aWave == b.mNumWaves / 2 || aIsFinalWave) {
                    aSpawnIntro = true;
                }

                if (aSpawnIntro) {
                    putZombieInWave(b, aIntroZombieType, aWave, aZombiePicker);
                }
            }

            // Level 5-10 adds a Gargantuar to the final wave
            if (b.mLevel == 50 && aIsFinalWave) {
                putZombieInWave(b, ZombieType.ZOMBIE_GARGANTUAR, aWave, aZombiePicker);
            }
            // The final wave of an adventure level includes every zombie type of the level
            if (b.mApp.isAdventureMode() && aIsFinalWave) {
                putInMissingZombies(b, aWave, aZombiePicker);
            }
            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
                if (aWave % 10 == 5) {
                    for (int i = 0; i < 10; i++) {
                        putZombieInWave(b, ZombieType.ZOMBIE_LADDER, aWave, aZombiePicker);
                    }
                }

                if (aWave % 10 == 8) {
                    for (int i = 0; i < 10; i++) {
                        putZombieInWave(b, ZombieType.ZOMBIE_JACK_IN_THE_BOX, aWave, aZombiePicker);
                    }
                }

                if (aWave == 19) {
                    for (int i = 0; i < 3; i++) {
                        putZombieInWave(b, ZombieType.ZOMBIE_GARGANTUAR, aWave, aZombiePicker);
                    }
                }
                if (aWave == 29) {
                    for (int i = 0; i < 5; i++) {
                        putZombieInWave(b, ZombieType.ZOMBIE_GARGANTUAR, aWave, aZombiePicker);
                    }
                }
            }

            // Spend the remaining zombie points on random zombies
            while (aZombiePicker.mZombiePoints > 0 && aZombiePicker.mZombieCount < Board.MAX_ZOMBIES_IN_WAVE) {
                int aZombieType = pickZombieType(b, aZombiePicker.mZombiePoints, aWave, aZombiePicker);
                putZombieInWave(b, aZombieType, aWave, aZombiePicker);
            }
        }
    }

    // Board.cpp:770
    static int getLevelRandSeed(Board b) {
        int aRndSeed = b.mApp.mPlayerInfo.mId + b.mBoardRandSeed;
        if (b.mApp.isAdventureMode()) {
            aRndSeed += b.mApp.mPlayerInfo.mFinishedAdventure * 101 + b.mLevel;
        } else {
            aRndSeed += b.mChallenge.mSurvivalStage * 101 + b.mApp.mGameMode;
        }
        return aRndSeed;
    }

    // Board.cpp:1137
    static void initZombieWavesForLevel(Board b, int theForLevel) {
        if (b.mApp.isWhackAZombieLevel() || (b.mApp.isWallnutBowlingLevel() && !b.mApp.isFirstTimeAdventureMode())) {
            b.mChallenge.initZombieWaves();
            return;
        }

        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            if (Zombie.getZombieDefinition(aZombieType) == null) {
                continue;
            }
            b.mZombieAllowed[aZombieType] = canZombieSpawnOnLevel(aZombieType, theForLevel);
        }
    }

    // Board.cpp:1151
    static boolean isZombieWaveDistributionOk(Board b) {
        if (!b.mApp.isAdventureMode()) {
            return true;
        }

        int[] aZombieTypeCount = new int[ZombieType.MAX_ZOMBIE_TYPES];
        for (int aWave = 0; aWave < b.mNumWaves; aWave++) {
            for (int aIndex = 0; aIndex < Board.MAX_ZOMBIES_IN_WAVE; aIndex++) {
                int aZombieType = b.mZombiesInWave[aWave][aIndex];
                if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }

                aZombieTypeCount[aZombieType]++;
            }
        }

        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.NUM_ZOMBIE_TYPES; aZombieType++) {
            if (aZombieType != ZombieType.ZOMBIE_YETI && canZombieSpawnOnLevel(aZombieType, b.mLevel) && aZombieTypeCount[aZombieType] == 0) {
                // PORT-NOTE: WARN, not the INFO upstream used: the wave cannot be built as authored
                // and nothing else reports it.
                Common.logWarnLn("Didn't spawn required zombie {}, level {}",
                        Zombie.getZombieDefinition(aZombieType).mZombieName, b.mLevel);
                return false;
            }
        }
        return true;
    }

    // Board.cpp:1183
    static void initZombieWaves(Board b) {
        Arrays.fill(b.mZombieAllowed, false);
        if (b.mApp.isAdventureMode()) {
            initZombieWavesForLevel(b, b.mLevel);
        } else {
            b.mChallenge.initZombieWaves();
            allowZombieTypesTheRetailListsCannotName(b);
        }
        pickZombieWaves(b);

        b.mCurrentWave = 0;
        b.mTotalSpawnedWaves = 0;
        b.mApp.mSawYeti = false;
        if (b.mApp.isFirstTimeAdventureMode() && b.mLevel == 2) {
            b.mZombieCountDown = ZOMBIE_COUNTDOWN * 2;
        } else if (b.mApp.isSurvivalMode() && b.mChallenge.mSurvivalStage > 0) {
            b.mZombieCountDown = ZOMBIE_COUNTDOWN_RANGE;
        } else {
            b.mZombieCountDown = ZOMBIE_COUNTDOWN_FIRST_WAVE;
        }

        b.mZombieHealthWaveStart = 0;
        b.mLastBungeeWave = 0;
        b.mProgressMeterWidth = 0;
        b.mHugeWaveCountDown = 0;
        b.mLevelAwardSpawned = false;
        b.mZombieCountDownStart = b.mZombieCountDown;
        b.mZombieHealthToNextWave = -1;
    }

    // Board.cpp:1249
    static void initSurvivalStage(Board b) {
        b.refreshSeedPacketFromCursor();
        b.mApp.mSoundSystem.gamePause(true);
        b.freezeEffectsForCutscene(true);
        b.mLevelComplete = false;
        initZombieWaves(b);
        b.mApp.mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        b.mApp.showSeedChooserScreen();
        b.mCutScene.startLevelIntro();
        b.mSeedBank.updateWidth();

        for (int i = 0; i < GameConstants.SEEDBANK_MAX; i++) {
            SeedPacket aPacket = b.mSeedBank.mSeedPackets[i];
            aPacket.mX = b.getSeedPacketPositionX(i);
            aPacket.mPacketType = SeedType.SEED_NONE;
        }

        if (b.stageHasFog()) {
            b.mFogBlownCountDown = FOG_BLOW_RETURN_TIME;
        }
        for (int j = 0; j < Board.MAX_GRID_SIZE_Y; j++) {
            b.mWaveRowGotLawnMowered[j] = -100;
        }
    }

    // Board.cpp:9342
    static int getNumWavesPerSurvivalStage(Board b) {
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND || b.mApp.isSurvivalNormal(b.mApp.mGameMode)) {
            return 10;
        } else if (b.mApp.isSurvivalHard(b.mApp.mGameMode) || b.mApp.isSurvivalEndless(b.mApp.mGameMode)) {
            return 20;
        }

        throw new IllegalStateException("getNumWavesPerSurvivalStage: not a survival game mode");
    }

    // Board.cpp:4671
    static int getGraveStonesCount(Board b) {
        return getGraveStoneCount(b);
    }

    // Board.cpp:9019
    static int getGraveStoneCount(Board b) {
        int aCount = 0;

        for (GridItem aGridItem : b.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                aCount++;
            }
        }

        return aCount;
    }

    // Board.cpp:484
    static void addGraveStones(Board b, int theGridX, int theCount, MTRand theLevelRNG) {
        int aGridAllowGraveStonesCount = 0;
        for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
            if (b.canAddGraveStoneAt(theGridX, y)) {
                aGridAllowGraveStonesCount++;
            }
        }
        int aClampedCount = Math.min(theCount, aGridAllowGraveStonesCount);

        int i = 0;
        while (i < aClampedCount) {
            int aGridY = (int) theLevelRNG.next(Board.MAX_GRID_SIZE_Y);
            if (b.canAddGraveStoneAt(theGridX, aGridY)) {
                b.addAGraveStone(theGridX, aGridY);
                ++i;
            }
        }
    }

    // Board.cpp:2336
    static boolean canZombieSpawnOnLevel(int theZombieType, int theLevel) {
        ZombieDefinition aZombieDef = Zombie.getZombieDefinition(theZombieType);
        if (aZombieDef == null) {
            return false;
        }

        if (theZombieType == ZombieType.ZOMBIE_YETI) {
            return LawnApp.gLawnApp.canSpawnYetis();
        }

        if (theLevel < aZombieDef.mStartingLevel || aZombieDef.mPickWeight == 0) {
            return false;
        }
        // A zombie type with no per-level row in Challenge::gZombieAllowedLevels - the retail table
        // ends at NUM_ZOMBIE_TYPES - is gated by its own definition row alone, which the check above
        // just did.
        if (theZombieType >= Challenge.gZombieAllowedLevels.length) {
            return true;
        }

        return Challenge.gZombieAllowedLevels[theZombieType].mAllowedOnLevel[Math.max(0, Math.min(theLevel - 1, GameConstants.NUM_LEVELS - 1))] != 0;
    }

    /**
     * Marks every zombie type outside the retail id space as allowed for the current mode when its
     * own definition row admits it into waves.
     *
     * <p>The per-mode fills ({@code Challenge::InitZombieWaves}, and the survival stage picker inside
     * it) are retail data: they name retail zombie types. A type outside that table therefore cannot
     * appear in them, and its absence is not a decision against it - it is gated by its own definition
     * row instead ({@code mStartingLevel} was already checked by the caller, {@code mPickWeight} is
     * the "takes part in waves" flag), which is the same rule {@link #canZombieSpawnOnLevel} applies
     * in adventure mode.
     */
    private static void allowZombieTypesTheRetailListsCannotName(Board b) {
        for (int aZombieType = ZombieType.NUM_ZOMBIE_TYPES; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            ZombieDefinition aZombieDef = Zombie.getZombieDefinition(aZombieType);
            if (aZombieDef != null && aZombieDef.mPickWeight != 0) {
                b.mZombieAllowed[aZombieType] = true;
            }
        }
    }

    // Board.cpp:2353
    static int getIntroducedZombieType(Board b) {
        if (!b.mApp.isAdventureMode() || b.mLevel == 1) {
            return ZombieType.ZOMBIE_INVALID;
        }

        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.NUM_ZOMBIE_TYPES; aZombieType++) {
            ZombieDefinition aZombieDef = Zombie.getZombieDefinition(aZombieType);
            if ((aZombieType != ZombieType.ZOMBIE_YETI || b.mApp.canSpawnYetis()) && aZombieDef.mStartingLevel == b.mLevel) {
                return aZombieType;
            }
        }
        return ZombieType.ZOMBIE_INVALID;
    }

    // Board.cpp:2371
    static int pickGraveRisingZombieType(Board b) {
        PvzpWeightedArray[] aZombieWeightArray = new PvzpWeightedArray[ZombieType.MAX_ZOMBIE_TYPES];
        int aCount = 2;
        aZombieWeightArray[0] = new PvzpWeightedArray();
        aZombieWeightArray[0].mItem = ZombieType.ZOMBIE_NORMAL;
        aZombieWeightArray[0].mWeight = Zombie.getZombieDefinition(ZombieType.ZOMBIE_NORMAL).mPickWeight;
        aZombieWeightArray[1] = new PvzpWeightedArray();
        aZombieWeightArray[1].mItem = ZombieType.ZOMBIE_TRAFFIC_CONE;
        aZombieWeightArray[1].mWeight = Zombie.getZombieDefinition(ZombieType.ZOMBIE_TRAFFIC_CONE).mPickWeight;
        if (!b.stageHasGraveStones()) {
            aZombieWeightArray[2] = new PvzpWeightedArray();
            aZombieWeightArray[2].mItem = ZombieType.ZOMBIE_PAIL;
            aZombieWeightArray[2].mWeight = Zombie.getZombieDefinition(ZombieType.ZOMBIE_PAIL).mPickWeight;
            aCount++;
        }

        // Zombie types outside the retail id space, when their definition row admits them into waves,
        // rise from graves exactly like the retail ones: the loop below zeroes the weight of every
        // type the current level does not allow.
        for (int aZombieType = ZombieType.NUM_ZOMBIE_TYPES; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            ZombieDefinition aZombieDef = Zombie.getZombieDefinition(aZombieType);
            if (aZombieDef == null || aZombieDef.mPickWeight == 0) {
                continue;
            }
            aZombieWeightArray[aCount] = new PvzpWeightedArray();
            aZombieWeightArray[aCount].mItem = aZombieType;
            aZombieWeightArray[aCount].mWeight = aZombieDef.mPickWeight;
            aCount++;
        }

        for (int i = 0; i < aCount; i++) {
            int aZombieType = (int) aZombieWeightArray[i].mItem;
            ZombieDefinition aZombieDef = Zombie.getZombieDefinition(aZombieType);
            if ((b.mApp.isFirstTimeAdventureMode() && b.mLevel < aZombieDef.mStartingLevel)
                    || (!b.mZombieAllowed[aZombieType] && aZombieType != ZombieType.ZOMBIE_NORMAL)) {
                aZombieWeightArray[i].mWeight = 0;
            }
        }

        return (int) PvzpCommon.pvzpPickFromWeightedArray(aZombieWeightArray, aCount);
    }

    // Board.cpp:2399
    static int pickZombieType(Board b, int theZombiePoints, int theWaveIndex, ZombiePicker theZombiePicker) {
        int aPickCount = 0;
        PvzpWeightedArray[] aZombieWeightArray = new PvzpWeightedArray[ZombieType.MAX_ZOMBIE_TYPES];
        for (int aZombieType = ZombieType.ZOMBIE_NORMAL; aZombieType < ZombieType.MAX_ZOMBIE_TYPES; aZombieType++) {
            ZombieDefinition aZombieDef = Zombie.getZombieDefinition(aZombieType);
            if (aZombieDef == null) {
                continue;
            }
            if (!b.mZombieAllowed[aZombieType]) {
                continue;
            }

            // Exclude zombie types that fail the spawn restrictions or exceed the remaining points
            int aGameMode = b.mApp.mGameMode;
            // In endless mode bungee zombies only appear in flag waves
            if (aZombieType == ZombieType.ZOMBIE_BUNGEE && b.mApp.isSurvivalEndless(aGameMode)) {
                if (!isFlagWave(b, theWaveIndex)) {
                    continue;
                }
            } else if (aGameMode != GameMode.GAMEMODE_CHALLENGE_POGO_PARTY && aGameMode != GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA
                    && aGameMode != GameMode.GAMEMODE_CHALLENGE_AIR_RAID) {
                int aFirstAllowedWave = aZombieDef.mFirstAllowedWave;
                // Endless mode gradually moves the first allowed wave earlier
                if (b.mApp.isSurvivalEndless(aGameMode)) {
                    int aFlags = b.getSurvivalFlagsCompleted();
                    int aAllowedWave = aFirstAllowedWave - PvzpCommon.pvzpAnimateCurve(18, 50, aFlags, 0, 15, PvzpCurves.CURVE_LINEAR);
                    aFirstAllowedWave = Math.max(aAllowedWave, 1);
                }
                if (theWaveIndex + 1 < aFirstAllowedWave || theZombiePoints < aZombieDef.mZombieValue) {
                    continue;
                }
            }

            // Survival mode recomputes pick weights from the completed flag count
            int aPickWeight = aZombieDef.mPickWeight;
            if (b.mApp.isSurvivalMode()) {
                int aFlags = b.getSurvivalFlagsCompleted();
                // Per-wave spawn cap for Gargantuar and Zomboni
                if (aZombieType == ZombieType.ZOMBIE_GARGANTUAR || aZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                    if (theZombiePicker.mZombieTypeCount[aZombieType] >= PvzpCommon.pvzpAnimateCurve(10, 50, aFlags, 2, 50, PvzpCurves.CURVE_LINEAR)) {
                        continue;
                    }
                }
                // Redeye caps: per flag wave, and total across non-flag waves
                else if (aZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                    if (isFlagWave(b, theWaveIndex)) {
                        if (theZombiePicker.mZombieTypeCount[aZombieType] >= PvzpCommon.pvzpAnimateCurve(14, 100, aFlags, 1, 50, PvzpCurves.CURVE_LINEAR)) {
                            continue;
                        }
                    } else {
                        if (theZombiePicker.mAllWavesZombieTypeCount[aZombieType] >= PvzpCommon.pvzpAnimateCurve(10, 110, aFlags, 1, 50, PvzpCurves.CURVE_LINEAR)) {
                            continue;
                        }
                        aPickWeight = 1000;
                    }
                }
                // Weight decay for normal and conehead zombies
                else if (aZombieType == ZombieType.ZOMBIE_NORMAL) {
                    aPickWeight = PvzpCommon.pvzpAnimateCurve(10, 50, aFlags, aPickWeight, aPickWeight / 10, PvzpCurves.CURVE_LINEAR);
                } else if (aZombieType == ZombieType.ZOMBIE_TRAFFIC_CONE) {
                    aPickWeight = PvzpCommon.pvzpAnimateCurve(10, 50, aFlags, aPickWeight, aPickWeight / 4, PvzpCurves.CURVE_LINEAR);
                }
            }
            aZombieWeightArray[aPickCount] = new PvzpWeightedArray();
            aZombieWeightArray[aPickCount].mItem = aZombieType;
            aZombieWeightArray[aPickCount].mWeight = aPickWeight;
            aPickCount++;
        }

        return (int) PvzpCommon.pvzpPickFromWeightedArray(aZombieWeightArray, aPickCount);
    }

    // Board.cpp:2486
    static boolean isZombieTypePoolOnly(int theZombieType) {
        return theZombieType == ZombieType.ZOMBIE_SNORKEL || theZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER;
    }

    // Board.cpp:2491
    static boolean rowCanHaveZombieType(Board b, int theRow, int theZombieType) {
        if (!rowCanHaveZombies(b, theRow)) {
            return false;
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED && b.mPlantRow[theRow] == PlantRowType.PLANTROW_DIRT && b.mCurrentWave < 5) {
            return false;
        }
        if (b.mPlantRow[theRow] == PlantRowType.PLANTROW_POOL && !Zombie.zombieTypeCanGoInPool(theZombieType) && theZombieType != ZombieType.ZOMBIE_BALLOON) {
            return false;
        }
        if (b.mPlantRow[theRow] == PlantRowType.PLANTROW_HIGH_GROUND && !Zombie.zombieTypeCanGoOnHighGround(theZombieType)) {
            return false;
        }

        int aCurrentWave = b.mCurrentWave;
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            aCurrentWave += b.mChallenge.mSurvivalStage * getNumWavesPerSurvivalStage(b);
        }
        if (b.mPlantRow[theRow] == PlantRowType.PLANTROW_POOL) {
            if (aCurrentWave < 5 && !isZombieTypePoolOnly(theZombieType)) {
                return false;
            }
        } else if (isZombieTypePoolOnly(theZombieType)) {
            return false;
        }
        if (theZombieType == ZombieType.ZOMBIE_BOBSLED && b.mIceTimer[theRow] == 0) {
            return false;
        }
        if (theRow == 0 && !b.mApp.isSurvivalMode()) {
            if (theZombieType == ZombieType.ZOMBIE_GARGANTUAR || theZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                return false;
            }
        }
        if (theZombieType != ZombieType.ZOMBIE_DANCER || b.stageHasPool()) {
            return true;
        }
        // Outside pool levels, dancer zombies need rows on both sides to summon backup dancers
        return rowCanHaveZombies(b, theRow - 1) && rowCanHaveZombies(b, theRow + 1);
    }

    // Board.cpp:2546
    static int pickRowForNewZombie(Board b, int theZombieType) {
        // A rake that is attracting a zombie pulls the new zombie into its row
        GridItem aRake = b.getRake();
        if (aRake != null && aRake.mGridItemState == GridItemState.GRIDITEM_STATE_RAKE_ATTRACTING
                && rowCanHaveZombieType(b, aRake.mGridY, theZombieType)) {
            aRake.mGridItemState = GridItemState.GRIDITEM_STATE_RAKE_WAITING;
            PvzpCommon.pvzpUpdateSmoothArrayPick(b.mRowPickingArray, Board.MAX_GRID_SIZE_Y, aRake.mGridY);
            return aRake.mGridY;
        }

        for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
            if (!rowCanHaveZombieType(b, aRow, theZombieType)) {
                b.mRowPickingArray[aRow].mWeight = 0;
            } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
                b.mRowPickingArray[aRow].mWeight = b.mChallenge.portalCombatRowSpawnWeight(aRow);
            } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL && b.mCurrentWave <= 3 && aRow == 5) {
                b.mRowPickingArray[aRow].mWeight = 0;
            }
            // lawn mower loss protection
            else {
                int aWavesMowered = b.mCurrentWave - b.mWaveRowGotLawnMowered[aRow];
                if (b.mApp.isContinuousChallenge() && b.mCurrentWave == b.mNumWaves - 1) {
                    aWavesMowered = 100;
                }

                if (aWavesMowered <= 1) {
                    b.mRowPickingArray[aRow].mWeight = 0.01f;
                } else if (aWavesMowered <= 2) {
                    b.mRowPickingArray[aRow].mWeight = 0.5f;
                } else {
                    b.mRowPickingArray[aRow].mWeight = 1.0f;
                }
            }
        }
        return PvzpCommon.pvzpPickFromSmoothArray(b.mRowPickingArray, Board.MAX_GRID_SIZE_Y);
    }

    // Board.cpp:2597
    static boolean canAddBobSled(Board b) {
        for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
            if (b.mIceTimer[aRow] > 0 && b.mIceMinX[aRow] < 700) {
                return true;
            }
        }
        return false;
    }

    // Board.cpp:5808
    static boolean rowCanHaveZombies(Board b, int theRow) {
        if (theRow < 0 || theRow >= Board.MAX_GRID_SIZE_Y) {
            return false;
        }

        return (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RESODDED && theRow <= 4) || b.mPlantRow[theRow] != PlantRowType.PLANTROW_DIRT;
    }

    // Board.cpp:9496
    static boolean isZombieTypeSpawnedOnly(int theZombieType) {
        return theZombieType == ZombieType.ZOMBIE_BACKUP_DANCER || theZombieType == ZombieType.ZOMBIE_BOBSLED
                || theZombieType == ZombieType.ZOMBIE_IMP;
    }

    // Board.cpp:4710
    static void spawnZombiesFromPool(Board b) {
        if (b.mIceTrapCounter > 0) {
            return;
        }

        int aCount;
        int aZombiePoints;
        if (b.mLevel == 21 || b.mLevel == 22 || b.mLevel == 31 || b.mLevel == 32) {
            aCount = 2;
            aZombiePoints = 3;
        } else if (b.mLevel == 23 || b.mLevel == 24 || b.mLevel == 25 || b.mLevel == 33 || b.mLevel == 34 || b.mLevel == 35) {
            aCount = 3;
            aZombiePoints = 5;
        } else {
            aCount = 3;
            aZombiePoints = 7;
        }

        int aGridArrayCount = 0;
        PvzpWeightedGridArray[] aGridArray = new PvzpWeightedGridArray[Board.MAX_POOL_GRID_SIZE];
        for (int aGridX = 5; aGridX < Board.MAX_GRID_SIZE_X; aGridX++) {
            for (int aGridY = 2; aGridY <= 3; aGridY++) {
                aGridArray[aGridArrayCount] = new PvzpWeightedGridArray();
                aGridArray[aGridArrayCount].mX = aGridX;
                aGridArray[aGridArrayCount].mY = aGridY;
                aGridArray[aGridArrayCount].mWeight = 10000;
                aGridArrayCount++;
            }
        }

        aGridArrayCount = Math.max(aGridArrayCount, 0);
        for (int i = 0; i < aCount; i++) {
            PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(aGridArray, aGridArrayCount);
            aGrid.mWeight = 0;

            int aZombieType = pickGraveRisingZombieType(b);
            Zombie aZombie = b.addZombieInRow(aZombieType, aGrid.mY, b.mCurrentWave);
            if (aZombie == null) {
                return;
            }

            aZombie.riseFromGrave(aGrid.mX, aGrid.mY);
            aZombiePoints -= Zombie.getZombieDefinition(aZombieType).mZombieValue;
            aZombiePoints = Math.max(aZombiePoints, 1);
        }
    }

    // Board.cpp:4765
    static void setupBungeeDrop(Board b, BungeeDropGrid theBungeeDropGrid) {
        theBungeeDropGrid.mGridArrayCount = 0;
        for (int aGridX = 4; aGridX < Board.MAX_GRID_SIZE_X; aGridX++) {
            for (int aGridY = 0; aGridY <= 4; aGridY++) {
                int aCount = theBungeeDropGrid.mGridArrayCount;
                theBungeeDropGrid.mGridArray[aCount] = new PvzpWeightedGridArray();
                theBungeeDropGrid.mGridArray[aCount].mX = aGridX;
                theBungeeDropGrid.mGridArray[aCount].mY = aGridY;
                theBungeeDropGrid.mGridArray[aCount].mWeight = 10000;
                theBungeeDropGrid.mGridArrayCount++;
            }
        }
    }

    // Board.cpp:4782
    static void bungeeDropZombie(Board b, BungeeDropGrid theBungeeDropGrid, int theZombieType) {
        PvzpWeightedGridArray aGrid = PvzpCommon.pvzpPickFromWeightedGridArray(theBungeeDropGrid.mGridArray, theBungeeDropGrid.mGridArrayCount);
        aGrid.mWeight = 1;

        Zombie aBungeeZombie = b.addZombie(ZombieType.ZOMBIE_BUNGEE, b.mCurrentWave);
        Zombie aZombie = b.addZombie(theZombieType, b.mCurrentWave);

        aBungeeZombie.bungeeDropZombie(aZombie, aGrid.mX, aGrid.mY);
    }

    // Board.cpp:4794
    static void spawnZombiesFromSky(Board b) {
        if (b.mIceTrapCounter > 0) {
            return;
        }

        int aCount;
        int aZombiePoints;
        if (b.mLevel == 41 || b.mLevel == 42) {
            aCount = 2;
            aZombiePoints = 3;
        } else if (b.mLevel == 43 || b.mLevel == 44 || b.mLevel == 45) {
            aCount = 3;
            aZombiePoints = 5;
        } else {
            aCount = 3;
            aZombiePoints = 7;
        }

        BungeeDropGrid aBungeeDropGrid = new BungeeDropGrid();
        setupBungeeDrop(b, aBungeeDropGrid);
        aCount = Math.min(aCount, aBungeeDropGrid.mGridArrayCount);

        if (aBungeeDropGrid.mGridArrayCount == 0 || aCount <= 0) {
            return;
        }

        for (int i = 0; i < aCount; i++) {
            int aZombieType = pickGraveRisingZombieType(b);
            bungeeDropZombie(b, aBungeeDropGrid, aZombieType);
            aZombiePoints -= Zombie.getZombieDefinition(aZombieType).mZombieValue;
            aZombiePoints = Math.max(aZombiePoints, 1);
        }
    }

    // Board.cpp:4832
    static void spawnZombiesFromGraves(Board b) {
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2) {
            return;
        }

        if (b.stageHasRoof()) {
            spawnZombiesFromSky(b);
        } else if (b.stageHasPool()) {
            spawnZombiesFromPool(b);
            return;
        }

        for (GridItem aGridItem : b.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.mGridItemType != GridItemType.GRIDITEM_GRAVESTONE || aGridItem.mGridItemCounter < 100) {
                continue;
            }
            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER && Common.rand(b.mNumWaves) > b.mCurrentWave) {
                continue;
            }

            int aZombieType = pickGraveRisingZombieType(b);
            Zombie aZombie = b.addZombie(aZombieType, b.mCurrentWave);
            if (aZombie == null) {
                return;
            }

            aZombie.riseFromGrave(aGridItem.mGridX, aGridItem.mGridY);
        }
    }

    // Board.cpp:9159
    static boolean bungeeIsTargetingCell(Board b, int theGridX, int theGridY) {
        for (Zombie aZombie : b.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying() && aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE
                    && aZombie.mRow == theGridY && aZombie.mTargetCol == theGridX) {
                return true;
            }
        }
        return false;
    }

    // Board.cpp:4879
    static int totalZombiesHealthInWave(Board b, int theWaveIndex) {
        int aTotalHealth = 0;
        for (Zombie aZombie : b.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (aZombie.mFromWave == theWaveIndex && !aZombie.mMindControlled && !aZombie.isDeadOrDying()
                    && aZombie.mZombieType != ZombieType.ZOMBIE_BUNGEE && aZombie.mRelatedZombieID == ZombieID.ZOMBIEID_NULL) {
                aTotalHealth += aZombie.mBodyHealth + aZombie.mHelmHealth + aZombie.mShieldHealth * 0.2f + aZombie.mFlyingHealth;
            }
        }
        return aTotalHealth;
    }

    // Board.cpp:4895
    static void spawnZombieWave(Board b) {
        int[] aWaveZombieTypes = new int[Board.MAX_ZOMBIES_IN_WAVE];
        int aWaveZombieCount = 0;
        for (int i = 0; i < Board.MAX_ZOMBIES_IN_WAVE; i++) {
            int aZombieType = b.mZombiesInWave[b.mCurrentWave][i];
            if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                break;
            }
            aWaveZombieTypes[aWaveZombieCount++] = aZombieType;
        }

        if (LawnHooksRegistry.dispatcher().onWaveStart(b, b.mCurrentWave, Arrays.copyOf(aWaveZombieTypes, aWaveZombieCount))) {
            return;
        }

        b.mChallenge.spawnZombieWave();
        if (b.mApp.isBungeeBlitzLevel()) {
            BungeeDropGrid aBungeeDropGrid = new BungeeDropGrid();
            setupBungeeDrop(b, aBungeeDropGrid);
            for (int i = 0; i < Board.MAX_ZOMBIES_IN_WAVE; i++) {
                int aZombieType = b.mZombiesInWave[b.mCurrentWave][i];
                if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }

                if (aZombieType == ZombieType.ZOMBIE_BUNGEE || aZombieType == ZombieType.ZOMBIE_ZAMBONI) {
                    b.addZombie(aZombieType, b.mCurrentWave);
                } else {
                    bungeeDropZombie(b, aBungeeDropGrid, aZombieType);
                }
            }
        } else {
            for (int i = 0; i < Board.MAX_ZOMBIES_IN_WAVE; i++) {
                int aZombieType = b.mZombiesInWave[b.mCurrentWave][i];
                if (aZombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }

                if (aZombieType == ZombieType.ZOMBIE_BOBSLED && !canAddBobSled(b)) {
                    for (int j = 0; j < Zombie.MAX_ZOMBIE_FOLLOWERS; j++) {
                        b.addZombie(ZombieType.ZOMBIE_NORMAL, b.mCurrentWave);  // spawn 4 normal zombies instead of the bobsled team
                    }
                } else {
                    b.addZombie(aZombieType, b.mCurrentWave);
                }
            }
        }

        if (b.mCurrentWave == b.mNumWaves - 1 && !b.mApp.isContinuousChallenge()) {
            b.mRiseFromGraveCounter = 200;
        }
        if (isFlagWave(b, b.mCurrentWave)) {
            b.mFlagRaiseCounter = FLAG_RAISE_TIME;
        }
        b.mCurrentWave++;
        b.mTotalSpawnedWaves++;
    }

    // Board.cpp:9480
    static int numberZombiesInWave(Board b, int theWaveIndex) {
        for (int i = 0; i < Board.MAX_ZOMBIES_IN_WAVE; i++) {
            if (b.mZombiesInWave[theWaveIndex][i] == ZombieType.ZOMBIE_INVALID) {
                return i;
            }
        }

        return 0;
    }

    // Board.cpp:5177
    static void updateSunSpawning(Board b) {
        if (b.stageIsNight()
                || b.hasLevelAwardDropped()
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE
                || b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL
                || b.mApp.mGameMode == GameMode.GAMEMODE_INTRO
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                || b.mApp.isIZombieLevel()
                || b.mApp.isScaryPotterLevel()
                || b.mApp.isSquirrelLevel()
                || b.hasConveyorBeltSeedBank()
                || b.mTutorialState == TutorialState.TUTORIAL_SLOT_MACHINE_PULL) {
            return;
        }

        if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER || b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER) {
            if (b.mPlants.mSize == 0) {
                return;
            }
        }

        b.mSunCountDown--;
        if (b.mSunCountDown != 0) {
            return;
        }

        b.mNumSunsFallen++;
        b.mSunCountDown = Math.min(SUN_COUNTDOWN_MAX, SUN_COUNTDOWN + b.mNumSunsFallen * 10) + Common.rand(SUN_COUNTDOWN_RANGE);
        int aSunType = b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY ? CoinType.COIN_LARGESUN : CoinType.COIN_SUN;
        b.addCoin(PvzpCommon.randRangeInt(100, 649), 60, aSunType, CoinMotion.COIN_MOTION_FROM_SKY);
    }

    // Board.cpp:5214
    static void nextWaveComing(Board b) {
        if (b.mCurrentWave + 1 == b.mNumWaves) {
            if (!b.isSurvivalStageWithRepick() && b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_LAST_STAND && !b.mApp.isContinuousChallenge()) {
                b.mApp.addReanimation(0, 30, Board.makeRenderOrder(RenderLayer.RENDER_LAYER_ABOVE_UI, 0, 0), ReanimationType.REANIM_FINAL_WAVE);
                b.mFinalWaveSoundCounter = 60;
            }
        }

        if (b.mCurrentWave == 0) {
            b.mApp.playSample(SoundType.SOUND_AWOOGA);
        } else if (b.mApp.isWhackAZombieLevel() ? (b.mCurrentWave == b.mNumWaves - 1) : isFlagWave(b, b.mCurrentWave)) {
            b.mApp.playSample(SoundType.SOUND_SIREN);
        }
    }

    // Board.cpp:5235
    static void updateZombieSpawning(Board b) {
        if (b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL || b.mApp.mGameMode == GameMode.GAMEMODE_INTRO) {
            return;
        }

        if (b.mFinalWaveSoundCounter > 0) {
            b.mFinalWaveSoundCounter--;
            if (b.mFinalWaveSoundCounter == 0) {
                b.mApp.playSample(SoundType.SOUND_FINALWAVE);
            }
        }

        if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER
                || b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER
                || b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_REFRESH_PEASHOOTER
                || b.mTutorialState == TutorialState.TUTORIAL_SLOT_MACHINE_PULL) {
            return;
        }

        if (b.hasLevelAwardDropped()) {
            return;
        }

        if (b.mRiseFromGraveCounter > 0) {
            b.mRiseFromGraveCounter--;
            if (b.mRiseFromGraveCounter == 0) {
                spawnZombiesFromGraves(b);
            }
        }

        if (b.mHugeWaveCountDown > 0) {
            b.mHugeWaveCountDown--;
            if (b.mHugeWaveCountDown == 0) {
                b.clearAdvice(AdviceType.ADVICE_HUGE_WAVE);
                nextWaveComing(b);
                b.mZombieCountDown = 1;
            } else {
                if (b.mHugeWaveCountDown == 725) {
                    b.mApp.playSample(SoundType.SOUND_HUGE_WAVE);
                } else {
                    int aMusicTune = b.mApp.mMusic.mCurMusicTune;
                    if (aMusicTune == MusicTune.MUSIC_TUNE_DAY_GRASSWALK
                            || aMusicTune == MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES
                            || aMusicTune == MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST
                            || aMusicTune == MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF) {
                        if (b.mHugeWaveCountDown == 400) {
                            b.mApp.mMusic.startBurst();
                        }
                    } else if (aMusicTune == MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS) {
                        if (b.mHugeWaveCountDown == 700) {
                            b.mApp.mMusic.startBurst();
                        }
                    }
                    return;
                }
            }
        }

        if (b.mChallenge.updateZombieSpawning()) {
            return;
        }

        if (b.mCurrentWave == b.mNumWaves) {
            if (b.isFinalSurvivalStage()) {
                return;
            }
            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                return;
            }
            if (!b.mApp.isSurvivalMode() && !b.mApp.isContinuousChallenge()) {
                return;
            }
        }

        b.mZombieCountDown--;
        if (b.mCurrentWave == b.mNumWaves && b.mApp.isSurvivalMode()) {
            if (b.mZombieCountDown == 0) {
                b.fadeOutLevel();
            }
            return;
        }

        if (b.mZombieCountDown > 200 && b.mZombieCountDownStart - b.mZombieCountDown > 400
                && totalZombiesHealthInWave(b, b.mCurrentWave - 1) <= b.mZombieHealthToNextWave) {
            b.mZombieCountDown = 200;
        }
        if (b.mZombieCountDown == 5) {
            if (isFlagWave(b, b.mCurrentWave)) {
                b.clearAdviceImmediately();
                b.displayAdviceAgain("[ADVICE_HUGE_WAVE]", MessageStyle.MESSAGE_STYLE_HUGE_WAVE, AdviceType.ADVICE_HUGE_WAVE);
                b.mHugeWaveCountDown = 750;
                return;
            }
            nextWaveComing(b);
        }
        if (b.mZombieCountDown == 0) {
            spawnZombieWave(b);
            b.mZombieHealthWaveStart = totalZombiesHealthInWave(b, b.mCurrentWave - 1);

            if (b.mCurrentWave == b.mNumWaves && b.mApp.isSurvivalMode()) {
                b.mZombieHealthToNextWave = 0;
                b.mZombieCountDown = ZOMBIE_COUNTDOWN_BEFORE_REPICK + 1;
            } else if (isFlagWave(b, b.mCurrentWave) && !(b.mApp.isWallnutBowlingLevel() || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND)) {
                b.mZombieHealthToNextWave = 0;
                b.mZombieCountDown = ZOMBIE_COUNTDOWN_BEFORE_FLAG;
            } else {
                b.mZombieHealthToNextWave = (int) (PvzpCommon.randRangeFloat(0.5f, 0.65f) * b.mZombieHealthWaveStart);
                if (b.mApp.isLittleTroubleLevel() || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN
                        || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                    b.mZombieCountDown = 750;
                } else {
                    b.mZombieCountDown = ZOMBIE_COUNTDOWN + Common.rand(ZOMBIE_COUNTDOWN_RANGE);
                }
            }
            b.mZombieCountDownStart = b.mZombieCountDown;
        }
    }
}
