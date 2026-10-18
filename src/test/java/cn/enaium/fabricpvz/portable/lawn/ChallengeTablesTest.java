package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Golden-table tests for the ported {@link Challenge} data (../verification/research/03-levels.md §1.1-1.3,
 * ../verification/research/04-reanim-ui.md §3.1) and for the stage classification Board derives from the
 * background (Board.cpp:8649-8662).
 *
 * <p>Every expected vector below is hard-coded from the cited documentation section, never recomputed
 * from the implementation.
 */
class ChallengeTablesTest {

    /** ../verification/research/03-levels.md §1.1 = Challenge.h:259-265 ({@code gZombieWaves}). */
    private static final int[] GOLDEN_ZOMBIE_WAVES = {
            4, 6, 8, 10, 8, 10, 20, 10, 20, 20,
            10, 20, 10, 20, 10, 10, 20, 10, 20, 20,
            10, 20, 20, 30, 20, 20, 30, 20, 30, 30,
            10, 20, 10, 20, 20, 10, 20, 10, 20, 20,
            10, 20, 20, 30, 20, 20, 30, 20, 30, 30,
    };

    /** ../verification/research/04-reanim-ui.md §3.1: page totals of the 72 {@code gChallengeDefs} rows. */
    private static final int GOLDEN_SURVIVAL_PAGE_COUNT = 11;

    private static final int GOLDEN_CHALLENGE_PAGE_COUNT = 20;
    private static final int GOLDEN_LIMBO_PAGE_COUNT = 21;
    private static final int GOLDEN_PUZZLE_PAGE_COUNT = 20;
    private static final int GOLDEN_CHALLENGE_DEF_COUNT = 72;

    // ---------------------------------------------------------------------------------------------
    // gZombieWaves
    // ---------------------------------------------------------------------------------------------

    @Test
    void zombieWavesMatchLevelsDoc() {
        assertEquals(GameConstants.NUM_LEVELS, Challenge.gZombieWaves.length,
                "one wave count per adventure level (Challenge.h:259)");
        assertArrayEquals(GOLDEN_ZOMBIE_WAVES, Challenge.gZombieWaves,
                "gZombieWaves must equal ../verification/research/03-levels.md §1.1");
    }

    // ---------------------------------------------------------------------------------------------
    // gZombieAllowedLevels
    // ---------------------------------------------------------------------------------------------

    @Test
    void zombieAllowedLevelsGoldenVectors() {
        // ../verification/research/03-levels.md §1.3, per-entry availability vectors (5 rows of 10).
        assertAllowed(ZombieType.ZOMBIE_NORMAL, repeat(1));
        assertAllowed(ZombieType.ZOMBIE_FLAG, repeat(1));
        assertAllowed(ZombieType.ZOMBIE_TRAFFIC_CONE, new int[] {
                0, 0, 1, 1, 1, 1, 1, 1, 1, 1,
                0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        });
        assertAllowed(ZombieType.ZOMBIE_POLEVAULTER, new int[] {
                0, 0, 0, 0, 0, 1, 1, 0, 1, 1,
                0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
                0, 0, 0, 1, 0, 0, 0, 0, 1, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
        });
        assertAllowed(ZombieType.ZOMBIE_PAIL, new int[] {
                0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                0, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                0, 1, 0, 1, 0, 0, 1, 0, 1, 1,
                0, 0, 0, 0, 0, 0, 1, 0, 1, 1,
                0, 1, 0, 0, 1, 0, 0, 0, 1, 1,
        });
        assertAllowed(ZombieType.ZOMBIE_NEWSPAPER, new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                1, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                0, 1, 0, 1, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        });
        assertAllowed(ZombieType.ZOMBIE_DOOR, new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 1, 1, 0, 0, 1, 0, 1, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        });
        assertAllowed(ZombieType.ZOMBIE_FOOTBALL, new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 1, 1, 0, 0, 1,
                0, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        });
        // Challenge.h:340-347 / Zombie.cpp:89 (mStartingLevel = 18): levels 18-20 only. docs §1.3
        // prints this row as "0×20 / <1-row> / 0×30", whose 60-value arity is a notation slip; the
        // source bytes and the zombie's mStartingLevel both put the three 1s in levels 18-20.
        assertAllowed(ZombieType.ZOMBIE_DANCER, new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        });
        assertAllowed(ZombieType.ZOMBIE_BUNGEE, new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                1, 1, 0, 0, 0, 0, 1, 0, 1, 1,
        });
        assertAllowed(ZombieType.ZOMBIE_GARGANTUAR, new int[] {
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
        });
        // Challenge.h:358, 431, 484 write { 0 } (all 50 zero).
        assertAllowed(ZombieType.ZOMBIE_DUCKY_TUBE, repeat(0));
        assertAllowed(ZombieType.ZOMBIE_REDEYE_GARGANTUAR, repeat(0));
    }

    @Test
    void zombieAllowedLevelsAreCompleteAndWellFormed() {
        assertEquals(ZombieType.NUM_ZOMBIE_TYPES, Challenge.gZombieAllowedLevels.length,
                "one gZombieAllowedLevels entry per zombie type (Challenge.h:267)");

        boolean[] seen = new boolean[ZombieType.NUM_ZOMBIE_TYPES];
        for (ZombieAllowedLevels anEntry : Challenge.gZombieAllowedLevels) {
            assertNotNull(anEntry, "entry must be constructed");
            assertEquals(GameConstants.NUM_LEVELS, anEntry.mAllowedOnLevel.length,
                    "every entry carries a 50-flag availability vector");
            assertTrue(anEntry.mZombieType >= 0 && anEntry.mZombieType < ZombieType.NUM_ZOMBIE_TYPES,
                    "mZombieType must be a valid ZombieType, was " + anEntry.mZombieType);
            for (int aLevel = 0; aLevel < GameConstants.NUM_LEVELS; aLevel++) {
                int aFlag = anEntry.mAllowedOnLevel[aLevel];
                assertTrue(aFlag == 0 || aFlag == 1,
                        "availability flags are 0/1 (entry " + anEntry.mZombieType + ", level " + (aLevel + 1) + ")");
            }
            // Upstream lists one entry per zombie type in ZombieType order.
            assertEquals(anEntry.mZombieType, indexOf(anEntry), "entries follow ZombieType order");
            seen[anEntry.mZombieType] = true;
        }

        for (int aZombieType = 0; aZombieType < ZombieType.NUM_ZOMBIE_TYPES; aZombieType++) {
            assertTrue(seen[aZombieType], "every zombie type has an entry, missing " + aZombieType);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // gChallengeDefs
    // ---------------------------------------------------------------------------------------------

    @Test
    void challengeDefinitionsMatchChallengeScreenTable() {
        assertEquals(GOLDEN_CHALLENGE_DEF_COUNT, Challenge.gChallengeDefs.length,
                "gChallengeDefs has one row per GameMode below GAMEMODE_INTRO (ChallengeScreen.cpp:39-215)");

        int aSurvival = 0;
        int aChallenge = 0;
        int aLimbo = 0;
        int aPuzzle = 0;
        for (ChallengeDefinition aDef : Challenge.gChallengeDefs) {
            assertNotNull(aDef, "row must be constructed");
            assertNotNull(aDef.mChallengeName, "rows carry the raw upstream name literal");
            switch (aDef.mPage) {
            case ChallengePage.CHALLENGE_PAGE_SURVIVAL:
                aSurvival++;
                break;
            case ChallengePage.CHALLENGE_PAGE_CHALLENGE:
                aChallenge++;
                break;
            case ChallengePage.CHALLENGE_PAGE_LIMBO:
                aLimbo++;
                break;
            case ChallengePage.CHALLENGE_PAGE_PUZZLE:
                aPuzzle++;
                break;
            default:
                throw new AssertionError("unknown challenge page " + aDef.mPage);
            }
        }
        assertEquals(GOLDEN_SURVIVAL_PAGE_COUNT, aSurvival, "Survival page rows");
        assertEquals(GOLDEN_CHALLENGE_PAGE_COUNT, aChallenge, "Challenge page rows");
        assertEquals(GOLDEN_LIMBO_PAGE_COUNT, aLimbo, "Limbo page rows");
        assertEquals(GOLDEN_PUZZLE_PAGE_COUNT, aPuzzle, "Puzzle page rows");

        // GetChallengeDefinition asserts the table is indexed by game mode (ChallengeScreen.cpp:219).
        for (int i = 0; i < Challenge.gChallengeDefs.length; i++) {
            assertEquals(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1 + i, Challenge.gChallengeDefs[i].mChallengeMode,
                    "gChallengeDefs[i].mChallengeMode == GAMEMODE_SURVIVAL_NORMAL_STAGE_1 + i");
        }
    }

    @Test
    void challengeDefinitionSpotChecks() {
        // Rows copied from src/Lawn/Widget/ChallengeScreen.cpp (line numbers in the comment).
        ChallengeDefinition aSurvivalDay = challengeDefForMode(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1);
        assertEquals(0, aSurvivalDay.mChallengeIconIndex); // :40
        assertEquals(ChallengePage.CHALLENGE_PAGE_SURVIVAL, aSurvivalDay.mPage);
        assertEquals(0, aSurvivalDay.mRow);
        assertEquals(0, aSurvivalDay.mCol);
        assertEquals("[SURVIVAL_DAY_NORMAL]", aSurvivalDay.mChallengeName);

        // Endless stage 3 is the only endless row that stays on the Survival page (:52).
        ChallengeDefinition aEndlessPool = challengeDefForMode(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3);
        assertEquals(ChallengePage.CHALLENGE_PAGE_SURVIVAL, aEndlessPool.mPage);
        assertEquals(2, aEndlessPool.mRow);
        assertEquals(2, aEndlessPool.mCol);
        assertEquals(10, aEndlessPool.mChallengeIconIndex);
        assertEquals("[SURVIVAL_POOL_ENDLESS]", aEndlessPool.mChallengeName);

        ChallengeDefinition aBeghouledTwist = challengeDefForMode(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST);
        assertEquals(20, aBeghouledTwist.mChallengeIconIndex); // :63
        assertEquals(ChallengePage.CHALLENGE_PAGE_CHALLENGE, aBeghouledTwist.mPage);
        assertEquals(1, aBeghouledTwist.mRow);
        assertEquals(3, aBeghouledTwist.mCol);
        assertEquals("[BEGHOULED_TWIST]", aBeghouledTwist.mChallengeName);

        ChallengeDefinition aWhackAZombie = challengeDefForMode(GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE);
        assertEquals(16, aWhackAZombie.mChallengeIconIndex); // :69
        assertEquals(2, aWhackAZombie.mRow);
        assertEquals(4, aWhackAZombie.mCol);
        assertEquals("[WHACK_A_ZOMBIE]", aWhackAZombie.mChallengeName);

        ChallengeDefinition aIZombieEndless = challengeDefForMode(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS);
        assertEquals(11, aIZombieEndless.mChallengeIconIndex); // :109
        assertEquals(ChallengePage.CHALLENGE_PAGE_PUZZLE, aIZombieEndless.mPage);
        assertEquals(3, aIZombieEndless.mRow);
        assertEquals(4, aIZombieEndless.mCol);
        assertEquals("[I_ZOMBIE_ENDLESS]", aIZombieEndless.mChallengeName);

        // The three rows whose names are not [BRACKETED] upstream literals (:88, :89, :110, :111).
        ChallengeDefinition aSquirrel = challengeDefForMode(GameMode.GAMEMODE_CHALLENGE_SQUIRREL);
        assertEquals(ChallengePage.CHALLENGE_PAGE_LIMBO, aSquirrel.mPage);
        assertEquals(2, aSquirrel.mRow);
        assertEquals(3, aSquirrel.mCol);
        assertEquals("Squirrel", aSquirrel.mChallengeName);

        ChallengeDefinition aTree = challengeDefForMode(GameMode.GAMEMODE_TREE_OF_WISDOM);
        assertEquals(ChallengePage.CHALLENGE_PAGE_LIMBO, aTree.mPage);
        assertEquals(2, aTree.mRow);
        assertEquals(4, aTree.mCol);
        assertEquals("Tree of Wisdom", aTree.mChallengeName);

        ChallengeDefinition anUpsell = challengeDefForMode(GameMode.GAMEMODE_UPSELL);
        assertEquals(ChallengePage.CHALLENGE_PAGE_LIMBO, anUpsell.mPage);
        assertEquals(3, anUpsell.mRow);
        assertEquals(4, anUpsell.mCol);
        assertEquals("Upsell", anUpsell.mChallengeName);

        ChallengeDefinition anIntro = challengeDefForMode(GameMode.GAMEMODE_INTRO);
        assertEquals(ChallengePage.CHALLENGE_PAGE_LIMBO, anIntro.mPage);
        assertEquals(2, anIntro.mRow);
        assertEquals(3, anIntro.mCol);
        assertEquals("Intro", anIntro.mChallengeName);
    }

    /**
     * Upstream {@code GetChallengeDefinition} (ChallengeScreen.cpp:214-222) takes the challenge
     * INDEX, i.e. {@code GameMode - GAMEMODE_SURVIVAL_NORMAL_STAGE_1} (0-based), not the GameMode
     * value itself - it asserts {@code gChallengeDefs[i].mChallengeMode == i + GAMEMODE_SURVIVAL_NORMAL_STAGE_1}.
     */
    private static ChallengeDefinition challengeDefForMode(int theGameMode) {
        return Challenge.getChallengeDefinition(theGameMode - GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1);
    }

    // ---------------------------------------------------------------------------------------------
    // Adventure stage classification for all 50 levels (Board.cpp:843-878, 8649-8662)
    // ---------------------------------------------------------------------------------------------

    @Test
    void adventureStageClassificationCoversAllFiftyLevels() {
        Board aBoard = newBoard(BackgroundType.BACKGROUND_1_DAY);

        for (int aLevel = 1; aLevel <= GameConstants.NUM_LEVELS; aLevel++) {
            int aBackground = goldenAdventureBackground(aLevel);
            aBoard.mBackground = aBackground;

            boolean aPool = aBackground == BackgroundType.BACKGROUND_3_POOL
                    || aBackground == BackgroundType.BACKGROUND_4_FOG;
            boolean aRoof = aBackground == BackgroundType.BACKGROUND_5_ROOF
                    || aBackground == BackgroundType.BACKGROUND_6_BOSS;
            boolean aSixRows = aPool;

            assertEquals(aBackground, aBoard.mBackground, "level " + aLevel + " golden background");
            assertEquals(aPool, aBoard.stageHasPool(), "level " + aLevel + " stageHasPool");
            assertEquals(aRoof, aBoard.stageHasRoof(), "level " + aLevel + " stageHasRoof");
            assertEquals(aSixRows, aBoard.stageHas6Rows(), "level " + aLevel + " stageHas6Rows");
        }
    }

    @Test
    void adventureStagePredicateGoldenTable() {
        Board aBoard = newBoard(BackgroundType.BACKGROUND_1_DAY);

        // Levels 1-10 (Day), 11-20 (Night): no pool, no roof, five rows.
        for (int aLevel = 1; aLevel <= 20; aLevel++) {
            aBoard.mBackground = goldenAdventureBackground(aLevel);
            assertTrue(!aBoard.stageHasPool(), "level " + aLevel + " has no pool");
            assertTrue(!aBoard.stageHasRoof(), "level " + aLevel + " has no roof");
            assertTrue(!aBoard.stageHas6Rows(), "level " + aLevel + " uses five rows");
        }
        // Levels 21-30 (Pool) and 31-40 (Fog): pool + six rows, no roof.
        for (int aLevel = 21; aLevel <= 40; aLevel++) {
            aBoard.mBackground = goldenAdventureBackground(aLevel);
            assertTrue(aBoard.stageHasPool(), "level " + aLevel + " has a pool");
            assertTrue(aBoard.stageHas6Rows(), "level " + aLevel + " uses six rows");
            assertTrue(!aBoard.stageHasRoof(), "level " + aLevel + " has no roof");
        }
        // Levels 41-49 (Roof) and 50 (Boss): roof.
        for (int aLevel = 41; aLevel <= GameConstants.NUM_LEVELS; aLevel++) {
            aBoard.mBackground = goldenAdventureBackground(aLevel);
            assertTrue(aBoard.stageHasRoof(), "level " + aLevel + " is a roof stage");
        }
    }

    /**
     * Golden adventure background per level, ported from Board.cpp:845-877 (the {@code GAMEMODE_ADVENTURE}
     * branch of {@code Board::PickBackground}): 1-10 Day, 11-20 Night, 21-30 Pool, 31-40 Fog, 41-49
     * Roof, 50 Boss.
     */
    private static int goldenAdventureBackground(int theLevel) {
        if (theLevel <= GameConstants.LEVELS_PER_AREA) {
            return BackgroundType.BACKGROUND_1_DAY;
        }
        if (theLevel <= 2 * GameConstants.LEVELS_PER_AREA) {
            return BackgroundType.BACKGROUND_2_NIGHT;
        }
        if (theLevel <= 3 * GameConstants.LEVELS_PER_AREA) {
            return BackgroundType.BACKGROUND_3_POOL;
        }
        if (theLevel <= 4 * GameConstants.LEVELS_PER_AREA) {
            return BackgroundType.BACKGROUND_4_FOG;
        }
        if (theLevel < GameConstants.FINAL_LEVEL) {
            return BackgroundType.BACKGROUND_5_ROOF;
        }
        if (theLevel == GameConstants.FINAL_LEVEL) {
            return BackgroundType.BACKGROUND_6_BOSS;
        }
        return BackgroundType.BACKGROUND_1_DAY;
    }

    /**
     * Builds a real {@link Board} so the assertions drive the ported production predicates. Board's
     * constructor allocates the seed bank/cutscene, which measure image resources, so the app is set
     * up through the shared headless fixture (stub image/font for every resource id) instead of
     * needing the retail assets.
     */
    private static Board newBoard(int theBackground) {
        LawnApp aApp = new LawnApp();
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(aApp);
        Board aBoard = new Board(aApp);
        aBoard.mBackground = theBackground;
        return aBoard;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static void assertAllowed(int theZombieType, int[] theGolden) {
        assertEquals(GameConstants.NUM_LEVELS, theGolden.length, "golden vector length");
        ZombieAllowedLevels anEntry = null;
        for (ZombieAllowedLevels aCandidate : Challenge.gZombieAllowedLevels) {
            if (aCandidate.mZombieType == theZombieType) {
                anEntry = aCandidate;
                break;
            }
        }
        assertNotNull(anEntry, "gZombieAllowedLevels must contain zombie type " + theZombieType);
        assertArrayEquals(theGolden, anEntry.mAllowedOnLevel,
                "availability vector for zombie type " + theZombieType);
    }

    /** The position of an entry inside {@code gZombieAllowedLevels}. */
    private static int indexOf(ZombieAllowedLevels theEntry) {
        for (int i = 0; i < Challenge.gZombieAllowedLevels.length; i++) {
            if (Challenge.gZombieAllowedLevels[i] == theEntry) {
                return i;
            }
        }
        return -1;
    }

    private static int[] repeat(int theValue) {
        int[] aVector = new int[GameConstants.NUM_LEVELS];
        java.util.Arrays.fill(aVector, theValue);
        return aVector;
    }
}
