/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.ChallengeIZombie (Challenge.cpp:4026-4041, 4043-4085,
 * 4087-4106, 4108-4114, 4116-4173, 4175-4401, 4403-4406, 4408-4469, 4493-4503, 4505-4539,
 * 4541-4561, 4563-4584, 4586-4601, 4603-4613, 4615-4622).
 *
 * Builds a real Board in the I,Zombie game modes through the entity test doubles
 * ({@link LawnEntityTestSupport}) and asserts the mode-specific objects/transitions the port
 * produces: the brain layout + pre-placed defenders, the red-line planting rules, the brain
 * targeting/eating/scoring flow and the bungee placement.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.pvzp.fx.FilterEffect;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

class ChallengeIZombieTest {

    private static final int STAGE_1 = GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1;
    private static final int ENDLESS = GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS;

    private static Board newBoard(TestLawnApp theApp, int theGameMode) {
        theApp.mGameMode = theGameMode;
        Common.sRand(0x5EED);
        return LawnEntityTestSupport.newBoard(theApp);
    }

    private static ChallengeIZombie newIZombie(Board theBoard) {
        return new ChallengeIZombie(theBoard.mChallenge);
    }

    /** A brain grid item shaped exactly like IZombieInitLevel creates one (Challenge.cpp:4178-4188). */
    private static GridItem brain(Board theBoard, int theGridY) {
        GridItem aBrain = theBoard.mGridItems.dataArrayAlloc();
        aBrain.mGridItemType = GridItemType.GRIDITEM_IZOMBIE_BRAIN;
        aBrain.mGridItemState = GridItemState.GRIDITEM_STATE_NORMAL;
        aBrain.mGridX = 0;
        aBrain.mGridY = theGridY;
        aBrain.mGridItemCounter = 70;
        aBrain.mRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, theGridY, 0);
        aBrain.mPosX = theBoard.gridToPixelX(0, theGridY) - 40;
        aBrain.mPosY = theBoard.gridToPixelY(0, theGridY) + 40;
        return aBrain;
    }

    private static int countPlants(Board theBoard, int theSeedType) {
        int aCount = 0;
        for (Plant aPlant : theBoard.mPlants) {
            if (!aPlant.mDead && aPlant.mSeedType == theSeedType) {
                aCount++;
            }
        }
        return aCount;
    }

    private static int countCoins(Board theBoard, int theCoinType) {
        int aCount = 0;
        for (Coin aCoin : theBoard.mCoins) {
            if (!aCoin.mDead && aCoin.mType == theCoinType) {
                aCount++;
            }
        }
        return aCount;
    }

    private static Zombie lastZombie(Board theBoard) {
        Zombie aLast = null;
        for (Zombie aZombie : theBoard.mZombies) {
            aLast = aZombie;
        }
        return aLast;
    }

    /** The brain targeting only reads the type/phase/row and the attack rect. */
    private static final class StubZombie extends Zombie {

        private final Rect mAttackRect;
        boolean mWalkingBackwards;

        StubZombie(Board theBoard, int theZombieType, int theRow, Rect theAttackRect) {
            mApp = theBoard.mApp;
            mBoard = theBoard;
            mRow = theRow;
            mX = 0;
            mY = 0;
            mZombieType = theZombieType;
            mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
            mAttackRect = theAttackRect;
        }

        @Override
        public Rect getZombieAttackRect() {
            return mAttackRect;
        }

        @Override
        public boolean isWalkingBackwards() {
            // Zombie.cpp:5074-5081 — a mind-controlled zombie always walks backwards.
            return mMindControlled || mWalkingBackwards;
        }

        @Override
        public void startEating() {
            // Zombie.cpp:2331-2336 — the real method's first act.
            mIsEating = true;
        }
    }

    // =====================================================================================
    // IZombieInitLevel
    // =====================================================================================

    @Test
    void initLevelLaysOutTheFiveBrainsAndTheStageOneDefenders() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        iz.iZombieInitLevel();

        assertEquals(0, board.mChallenge.mChallengeScore);
        assertEquals(0, board.mBonusLawnMowersRemaining, "I,Zombie levels never hand out mowers");

        // Challenge.cpp:4178-4188: five brains in column 0, one per lane.
        assertEquals(5, board.mGridItems.mSize);
        for (int aRow = 0; aRow < Challenge.I_ZOMBIE_WINNING_SCORE; aRow++) {
            GridItem aBrain = board.getGridItemAt(GridItemType.GRIDITEM_IZOMBIE_BRAIN, 0, aRow);
            assertNotNull(aBrain, "one brain per lane in column 0");
            assertEquals(GridItemState.GRIDITEM_STATE_NORMAL, aBrain.mGridItemState);
            assertEquals(GridItemType.GRIDITEM_IZOMBIE_BRAIN, aBrain.mGridItemType);
            assertEquals(70, aBrain.mGridItemCounter);
            assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PLANT, aRow, 0), aBrain.mRenderOrder);
            assertEquals(board.gridToPixelX(0, aRow) - 40.0f, aBrain.mPosX, 0.001f);
            assertEquals(board.gridToPixelY(0, aRow) + 40.0f, aBrain.mPosY, 0.001f);
        }

        // Stage 1: 2 fixed sunflowers + 7+3+6+2 placed ones; aColumns is 4 and every square starts
        // free, so each IZombiePlacePlants call can honour its full count.
        assertEquals(20, board.mPlants.mSize, "2 + 7 + 3 + 6 + 2 defenders");
        assertNotNull(plantAt(board, SeedType.SEED_SUNFLOWER, 3, 2));
        assertNotNull(plantAt(board, SeedType.SEED_SUNFLOWER, 3, 3));
        assertEquals(2 + 7, countPlants(board, SeedType.SEED_SUNFLOWER));
        for (Plant aPlant : board.mPlants) {
            assertTrue(aPlant.mPlantCol <= 3, "stage 1's red line sits between columns 3 and 4");
            assertTrue(aPlant.mRow <= 4);
        }
    }

    @Test
    void initLevelEndlessRollsTheEndlessFormation() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, ENDLESS);
        ChallengeIZombie iz = newIZombie(board);

        iz.iZombieInitLevel();

        // mSurvivalStage 0 forces the puffshroom count to 0 and the sunflower count to 8; the
        // formation roll then adds to those (Challenge.cpp:4335-4344).
        assertEquals(0, countPlants(board, SeedType.SEED_PUFFSHROOM));
        assertEquals(8, countPlants(board, SeedType.SEED_SUNFLOWER));
        assertTrue(board.mPlants.mSize > 8, "the stage-0 roll still lays out a formation");
        assertEquals(5, board.mGridItems.mSize);
        for (Plant aPlant : board.mPlants) {
            assertTrue(aPlant.mPlantCol <= 4, "endless uses aColumns 5");
        }
    }

    @Test
    void initLevelEndlessStageTenGrantsBetterOffDead() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, ENDLESS);
        ChallengeIZombie iz = newIZombie(board);
        board.mChallenge.mSurvivalStage = 10;

        iz.iZombieInitLevel();

        // Challenge.cpp:4272-4276 (ReportAchievement::GiveAchievement(..., BetterOffDead, true)).
        assertTrue(app.mPlayerInfo.mEarnedAchievements[AchievementId.BetterOffDead]);
        assertTrue(app.mSamplesPlayed.contains((long) Resources.SOUND_ACHIEVEMENT_ID));
        // Stage >= 10 rolls 5..7 puffshrooms (std::clamp(2 + 10/3, 2, 5) .. std::clamp(3 + 10/2, 2, 7)).
        int aPuffshrooms = countPlants(board, SeedType.SEED_PUFFSHROOM);
        assertTrue(aPuffshrooms >= 5 && aPuffshrooms <= 7, "puffshroom count " + aPuffshrooms);
        assertTrue(countPlants(board, SeedType.SEED_SUNFLOWER) >= 1, "8 - aPuffshroomCount sunflowers");
    }

    private static Plant plantAt(Board theBoard, int theSeedType, int theGridX, int theGridY) {
        for (Plant aPlant : theBoard.mPlants) {
            if (!aPlant.mDead && aPlant.mSeedType == theSeedType && aPlant.mPlantCol == theGridX
                    && aPlant.mRow == theGridY) {
                return aPlant;
            }
        }
        return null;
    }

    // =====================================================================================
    // IZombiePlacePlants / IZombieSetupPlant / IZombiePlacePlantInSquare
    // =====================================================================================

    @Test
    void placePlantsUsesTheRedLineColumnsAndTheRequestedRow() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        // Wall-nuts only appear in the rightmost three of the stage's four columns.
        iz.iZombiePlacePlants(SeedType.SEED_WALLNUT, 3, -1);
        assertEquals(3, countPlants(board, SeedType.SEED_WALLNUT));
        for (Plant aPlant : board.mPlants) {
            assertTrue(aPlant.mPlantCol >= 1, "wall-nuts may not use column 0");
        }

        // A concrete row restricts the whole placement to that lane (fresh board: the wall-nuts
        // above may occupy part of row 3, which would cap the peashooter count).
        Board aRowBoard = newBoard(app, STAGE_1);
        ChallengeIZombie aRowIz = newIZombie(aRowBoard);
        aRowIz.iZombiePlacePlants(SeedType.SEED_PEASHOOTER, 2, 3);

        assertEquals(2, countPlants(aRowBoard, SeedType.SEED_PEASHOOTER));
        for (Plant aPlant : aRowBoard.mPlants) {
            assertEquals(3, aPlant.mRow);
        }
    }

    @Test
    void placePlantsClampsTheCountToTheFreeSquares() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        iz.iZombiePlacePlants(SeedType.SEED_SUNFLOWER, 999, -1);

        // 4 columns x 5 rows is the whole plantable area of stage 1.
        assertEquals(20, board.mPlants.mSize);
        assertEquals(20, countPlants(board, SeedType.SEED_SUNFLOWER));
        for (Plant aPlant : board.mPlants) {
            assertTrue(aPlant.mPlantCol <= 3);
        }
    }

    @Test
    void setupPlantFreezesThePlantReanimsAndArmsPotatoMines() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        Plant aMine = board.newPlant(2, 2, SeedType.SEED_POTATOMINE, SeedType.SEED_NONE);
        Plant aSunflower = board.newPlant(1, 1, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        aMine.mBlinkCountdown = 40;
        aSunflower.mBlinkCountdown = 40;
        app.mReanim.mAnimRate = 7.5f;

        iz.iZombieSetupPlant(aMine);

        assertEquals(0.0f, app.mReanim.mAnimRate, "the body reanimation must be frozen");
        assertEquals(PlantState.STATE_POTATO_ARMED, aMine.mState);
        assertEquals(0, aMine.mBlinkCountdown);

        iz.iZombieSetupPlant(aSunflower);
        assertEquals(PlantState.STATE_NOTREADY, aSunflower.mState, "only potato mines get re-armed");
        assertEquals(0, aSunflower.mBlinkCountdown);
    }

    @Test
    void placePlantInSquareSkipsOccupiedSquares() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        iz.iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 2);
        assertEquals(1, board.mPlants.mSize);
        assertNotNull(plantAt(board, SeedType.SEED_SUNFLOWER, 2, 2));

        iz.iZombiePlacePlantInSquare(SeedType.SEED_SUNFLOWER, 2, 2);
        assertEquals(1, board.mPlants.mSize, "CanPlantAt rejects the now-occupied square");
    }

    // =====================================================================================
    // IZombiePlaceZombie
    // =====================================================================================

    @Test
    void placeZombieSnapsBungeesToTheirColumnAndOffsetsWalkers() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        iz.iZombiePlaceZombie(ZombieType.ZOMBIE_BUNGEE, 3, 2);
        Zombie aBungee = lastZombie(board);
        assertEquals(ZombieType.ZOMBIE_BUNGEE, aBungee.mZombieType);
        assertEquals(3, aBungee.mTargetCol);
        assertEquals(2, aBungee.mRow);
        assertEquals(board.gridToPixelX(3, 2), (int) aBungee.mPosX, "bungees do not get the walker offset");
        assertEquals(board.getPosYBasedOnRow(aBungee.mPosX + 40.0f, 2) - 30.0f, aBungee.mPosY, 0.001f);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, 2, 7), aBungee.mRenderOrder);

        iz.iZombiePlaceZombie(ZombieType.ZOMBIE_BALLOON, 4, 1);
        Zombie aWalker = lastZombie(board);
        assertEquals(2, board.mZombies.mSize);
        assertEquals(board.gridToPixelX(4, 1) - 30.0f, aWalker.mPosX, 0.001f);
    }

    // =====================================================================================
    // IZombieUpdate
    // =====================================================================================

    /** Records the hand-over instead of running Board::ZombiesWon (dialog + screen fade). */
    private static final class RecordingBoard extends Board {

        int mZombiesWonCount;

        RecordingBoard(TestLawnApp theApp) {
            super(theApp);
            mLevel = 1;
            mMainCounter = 5;
        }

        @Override
        public void zombiesWon(Zombie theZombie) {
            mZombiesWonCount++;
        }
    }

    private static RecordingBoard newRecordingBoard(TestLawnApp theApp, int theGameMode) {
        theApp.mGameMode = theGameMode;
        Common.sRand(0x5EED);
        return new RecordingBoard(theApp);
    }

    @Test
    void updateHandsTheLevelOverWhenTheBoardIsEmptyAndBroke() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        RecordingBoard board = newRecordingBoard(app, STAGE_1);
        ChallengeIZombie iz = new ChallengeIZombie(board.mChallenge);
        // A money coin cannot keep an empty, broke board alive.
        Coin aMoney = board.addCoin(100, 100, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_COIN);

        iz.iZombieUpdate();

        assertEquals(1, board.mZombiesWonCount);
        assertTrue(aMoney.mDead, "money coins are culled when the level hands over");
    }

    @Test
    void updateKeepsTheLevelAliveWhileSomethingCanStillHappen() {
        TestLawnApp app = LawnEntityTestSupport.newApp();

        // 50 sun in the bank: aSunMoney < 50 no longer holds.
        RecordingBoard aSunBoard = newRecordingBoard(app, STAGE_1);
        aSunBoard.mSunMoney = 50;
        new ChallengeIZombie(aSunBoard.mChallenge).iZombieUpdate();
        assertEquals(0, aSunBoard.mZombiesWonCount);

        // A zombie still on the lawn.
        RecordingBoard aZombieBoard = newRecordingBoard(app, STAGE_1);
        aZombieBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 0, 0);
        new ChallengeIZombie(aZombieBoard.mChallenge).iZombieUpdate();
        assertEquals(0, aZombieBoard.mZombiesWonCount);

        // An uncollected sun coin.
        RecordingBoard aSunCoinBoard = newRecordingBoard(app, STAGE_1);
        aSunCoinBoard.addCoin(100, 100, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
        new ChallengeIZombie(aSunCoinBoard.mChallenge).iZombieUpdate();
        assertEquals(0, aSunCoinBoard.mZombiesWonCount);

        // Two sun coins already being collected are 50 sun (CountSunBeingCollected).
        RecordingBoard aCollectedBoard = newRecordingBoard(app, STAGE_1);
        Coin aCollected1 = aCollectedBoard.addCoin(100, 100, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
        Coin aCollected2 = aCollectedBoard.addCoin(100, 100, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
        aCollected1.mIsBeingCollected = true;
        aCollected2.mIsBeingCollected = true;
        new ChallengeIZombie(aCollectedBoard.mChallenge).iZombieUpdate();
        assertEquals(0, aCollectedBoard.mZombiesWonCount);

        // A defender mid-squash.
        RecordingBoard aPlantBoard = newRecordingBoard(app, STAGE_1);
        Plant aSquash = aPlantBoard.newPlant(2, 2, SeedType.SEED_SQUASH, SeedType.SEED_NONE);
        aSquash.mState = PlantState.STATE_SQUASH_FALLING;
        new ChallengeIZombie(aPlantBoard.mChallenge).iZombieUpdate();
        assertEquals(0, aPlantBoard.mZombiesWonCount);

        // A potato mine about to blow.
        RecordingBoard aParticleBoard = newRecordingBoard(app, STAGE_1);
        PvzpParticleSystem aParticle = app.mEffectSystem.mParticleHolder.mParticleSystems.dataArrayAlloc();
        aParticle.mEffectType = ParticleEffect.PARTICLE_POTATO_MINE;
        new ChallengeIZombie(aParticleBoard.mChallenge).iZombieUpdate();
        assertEquals(0, aParticleBoard.mZombiesWonCount);
    }

    // =====================================================================================
    // IZombieGetBrainTarget / IZombieEatBrain / IZombieSquishBrain / IZombieScoreBrain
    // =====================================================================================

    @Test
    void getBrainTargetRequiresTheZombieToReachColumnZero() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        GridItem aBrain = brain(board, 2);

        Zombie aWalker = new StubZombie(board, ZombieType.ZOMBIE_NORMAL, 2, new Rect(10, 0, 44, 115));
        assertEquals(aBrain, iz.iZombieGetBrainTarget(aWalker));

        // Attack rect > 20 px on screen: not at the brain yet.
        Zombie aFarAway = new StubZombie(board, ZombieType.ZOMBIE_NORMAL, 2, new Rect(21, 0, 44, 115));
        assertNull(iz.iZombieGetBrainTarget(aFarAway));

        // Bungees and backwards walkers never eat.
        Zombie aBungee = new StubZombie(board, ZombieType.ZOMBIE_BUNGEE, 2, new Rect(10, 0, 44, 115));
        assertNull(iz.iZombieGetBrainTarget(aBungee));
        Zombie aBackwards = new StubZombie(board, ZombieType.ZOMBIE_NORMAL, 2, new Rect(10, 0, 44, 115));
        aBackwards.mMindControlled = true;
        assertNull(iz.iZombieGetBrainTarget(aBackwards));

        // Balloons shift their rect 25 px right (Rect(10..) becomes 35 > 20).
        Zombie aBalloon = new StubZombie(board, ZombieType.ZOMBIE_BALLOON, 2, new Rect(10, 0, 44, 115));
        assertNull(iz.iZombieGetBrainTarget(aBalloon));

        // A pre-vault polevaulter uses Rect(50 + mX, 0, 20, 115) instead of the attack rect.
        Zombie aPole = new StubZombie(board, ZombieType.ZOMBIE_POLEVAULTER, 2, new Rect(0, 0, 44, 115));
        aPole.mZombiePhase = ZombiePhase.PHASE_POLEVAULTER_PRE_VAULT;
        aPole.mX = 0;
        assertNull(iz.iZombieGetBrainTarget(aPole));
        aPole.mX = -30;
        assertEquals(aBrain, iz.iZombieGetBrainTarget(aPole));

        // A squished brain is no longer a target.
        aBrain.mGridItemState = GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED;
        assertNull(iz.iZombieGetBrainTarget(aWalker));

        // No brain in the lane at all.
        assertNull(iz.iZombieGetBrainTarget(new StubZombie(board, ZombieType.ZOMBIE_NORMAL, 4,
                new Rect(10, 0, 44, 115))));
    }

    @Test
    void eatBrainChewsTheBrainDownAndScoresIt() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        GridItem aBrain = brain(board, 2);
        Zombie aZombie = new StubZombie(board, ZombieType.ZOMBIE_NORMAL, 2, new Rect(10, 0, 44, 115));

        for (int i = 0; i < 70; i++) {
            assertTrue(iz.iZombieEatBrain(aZombie));
        }

        assertTrue(aZombie.mIsEating);
        assertTrue(aBrain.mDead, "the brain dies on its last bite");
        assertEquals(0, aBrain.mGridItemCounter);
        assertEquals(1, board.mChallenge.mChallengeScore);
        assertTrue(app.mSamplesPlayed.contains((long) SoundType.SOUND_GULP));
        assertEquals(Board.PROGRESS_METER_COUNTER / Challenge.I_ZOMBIE_WINNING_SCORE, board.mProgressMeterWidth);

        // The brain is gone, so the next bite target is null and nothing is eaten.
        assertFalse(iz.iZombieEatBrain(aZombie));
        assertEquals(1, board.mChallenge.mChallengeScore);
    }

    @Test
    void squishBrainMarksItSquishedAndScores() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        GridItem aBrain = brain(board, 3);

        iz.iZombieSquishBrain(aBrain);

        assertEquals(GridItemState.GRIDITEM_STATE_BRAIN_SQUISHED, aBrain.mGridItemState);
        assertEquals(500, aBrain.mGridItemCounter);
        assertEquals(Board.makeRenderOrder(RenderLayer.RENDER_LAYER_GRAVE_STONE, 3, 0), aBrain.mRenderOrder);
        assertEquals(1, board.mChallenge.mChallengeScore);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_SQUISH));
    }

    @Test
    void scoreBrainOnTheWinningBrainSpawnsTheLevelAward() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        GridItem aBrain = brain(board, 2);
        board.mChallenge.mChallengeScore = Challenge.I_ZOMBIE_WINNING_SCORE - 1;

        iz.iZombieScoreBrain(aBrain);

        assertEquals(Challenge.I_ZOMBIE_WINNING_SCORE, board.mChallenge.mChallengeScore);
        assertEquals(Board.PROGRESS_METER_COUNTER, board.mProgressMeterWidth);
        assertTrue(board.mLevelAwardSpawned, "a fixed I,Zombie stage drops SpawnLevelAward");
        assertEquals(BoardResult.BOARDRESULT_WON, app.mBoardResult);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_SPAWN_SUN));
        assertEquals(1, countCoins(board, CoinType.COIN_TROPHY));
    }

    @Test
    void scoreBrainOnTheWinningBrainEndsTheEndlessStage() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, ENDLESS);
        ChallengeIZombie iz = newIZombie(board);
        GridItem aBrain = brain(board, 2);
        board.mChallenge.mChallengeScore = Challenge.I_ZOMBIE_WINNING_SCORE - 1;
        board.mChallenge.mSurvivalStage = 1;  // 1 % 3 != 0, so this is not an award stage

        iz.iZombieScoreBrain(aBrain);

        assertEquals(Challenge.I_ZOMBIE_WINNING_SCORE, board.mChallenge.mChallengeScore);
        assertEquals(Board.PROGRESS_METER_COUNTER, board.mProgressMeterWidth);
        assertEquals(500, board.mNextSurvivalStageCounter, "puzzlePhaseComplete -> fadeOutLevel arms the next stage");
        assertFalse(board.mLevelAwardSpawned);
    }

    // =====================================================================================
    // IZombieStart / IZombiePlantDropRemainingSun / IZombieMouseDownWithZombie
    // =====================================================================================

    @Test
    void startShowsTheEatAllBrainsHint() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);

        iz.iZombieStart();

        assertEquals(AdviceType.ADVICE_I_ZOMBIE_EAT_ALL_BRAINS, board.mHelpIndex);
        assertEquals(500, board.mAdvice.mDuration, "MESSAGE_STYLE_HINT_FAST lasts 500 ms");
    }

    @Test
    void plantDropRemainingSunDropsOneSunPerFortyHealth() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        Plant aSunflower = board.newPlant(1, 1, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        Plant aPeashooter = board.newPlant(2, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        aSunflower.mPlantHealth = 100;

        iz.iZombiePlantDropRemainingSun(aSunflower);
        assertEquals(100 / 40 + 1, countCoins(board, CoinType.COIN_SUN));

        // Only sunflowers cough up their remaining sun.
        iz.iZombiePlantDropRemainingSun(aPeashooter);
        assertEquals(100 / 40 + 1, countCoins(board, CoinType.COIN_SUN));
    }

    @Test
    void setPlantFilterEffectAppliesToThePlantReanims() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        Plant aPlant = board.newPlant(3, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);

        iz.iZombieSetPlantFilterEffect(aPlant, FilterEffect.FILTER_EFFECT_WHITE);
        assertEquals(FilterEffect.FILTER_EFFECT_WHITE, app.mReanim.mFilterEffect);

        iz.iZombieSetPlantFilterEffect(aPlant, FilterEffect.FILTER_EFFECT_NONE);
        assertEquals(FilterEffect.FILTER_EFFECT_NONE, app.mReanim.mFilterEffect);
    }

    @Test
    void drawPlantRestoresTheGraphicsStateAndClearsTheFilter() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        Plant aPlant = board.newPlant(3, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        Graphics g = new Graphics();
        g.mTransX = 7.0f;
        g.mTransY = 9.0f;

        iz.iZombieDrawPlant(g, aPlant);

        assertEquals(7.0f, g.mTransX, 0.001f, "the four white-out passes must restore the transform");
        assertEquals(9.0f, g.mTransY, 0.001f);
        assertFalse(g.getColorizeImages(), "the colourised drawing must not leak past the plant");
        assertEquals(FilterEffect.FILTER_EFFECT_NONE, app.mReanim.mFilterEffect);

        // A plant without a body reanimation has nothing to outline.
        Plant aNoReanim = board.newPlant(4, 2, SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        aNoReanim.mBodyReanimID = ReanimationID.REANIMATIONID_NULL;
        Graphics aOther = new Graphics();
        iz.iZombieDrawPlant(aOther, aNoReanim);
        assertFalse(aOther.getColorizeImages());
    }

    @Test
    void mouseDownWithZombiePlantsThePacketRightOfTheLine() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        int aSeedType = SeedType.SEED_ZOMBIE_NORMAL;
        board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_BANK;
        board.mCursorObject.mType = aSeedType;
        board.mCursorObject.mSeedBankIndex = 0;
        board.mSeedBank.mNumPackets = 3;
        board.mSeedBank.mSeedPackets[0].mApp = app;
        board.mSeedBank.mSeedPackets[0].mBoard = board;
        board.mSeedBank.mSeedPackets[0].setPacketType(aSeedType, SeedType.SEED_NONE);
        board.mSunMoney = 150;

        int aX = board.gridToPixelX(5, 2) + 40;
        int aY = board.gridToPixelY(5, 2) + 50;
        assertEquals(5, board.plantingPixelToGridX(aX, aY, aSeedType));
        assertEquals(2, board.plantingPixelToGridY(aX, aY, aSeedType));

        iz.iZombieMouseDownWithZombie(aX, aY, 1);

        assertEquals(1, board.mZombies.mSize, "the packet's zombie is added right of the red line");
        Zombie aZombie = lastZombie(board);
        assertEquals(2, aZombie.mRow);
        assertEquals(board.gridToPixelX(5, 2) - 30.0f, aZombie.mPosX, 0.001f);
        assertEquals(150 - Plant.getCost(aSeedType, SeedType.SEED_NONE), board.mSunMoney);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_PLANT));
        assertTrue(board.mSeedBank.mSeedPackets[0].mRefreshing, "WasPlanted starts the packet refresh");
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, board.mCursorObject.mCursorType);
        assertEquals(SeedType.SEED_NONE, board.mCursorObject.mType);
    }

    @Test
    void mouseDownWithZombieHintsLeftOfTheLine() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        board.mCursorObject.mType = SeedType.SEED_ZOMBIE_NORMAL;

        iz.iZombieMouseDownWithZombie(board.gridToPixelX(2, 2) + 40, board.gridToPixelY(2, 2) + 50, 1);

        assertEquals(0, board.mZombies.mSize);
        assertEquals(AdviceType.ADVICE_I_ZOMBIE_NOT_PASSED_LINE, board.mHelpIndex);
        assertEquals(1500, board.mAdvice.mDuration, "MESSAGE_STYLE_HINT_LONG lasts 1500 ms");
    }

    @Test
    void mouseDownWithZombieHintsForBungeesLeftOfTheirColumn() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        // Bungees must land left of the red line, so a square right of it is rejected.
        board.mCursorObject.mType = SeedType.SEED_ZOMBIE_BUNGEE;

        iz.iZombieMouseDownWithZombie(board.gridToPixelX(5, 2) + 40, board.gridToPixelY(5, 2) + 50, 1);

        assertEquals(0, board.mZombies.mSize);
        assertEquals(AdviceType.ADVICE_I_ZOMBIE_LEFT_OF_LINE, board.mHelpIndex);
    }

    @Test
    void mouseDownWithZombieDropsThePacketWhenTheClickMisses() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = newBoard(app, STAGE_1);
        ChallengeIZombie iz = newIZombie(board);
        board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PLANT_FROM_BANK;
        board.mCursorObject.mType = SeedType.SEED_ZOMBIE_NORMAL;
        board.mCursorObject.mSeedBankIndex = 0;
        board.mSeedBank.mNumPackets = 3;
        board.mSeedBank.mSeedPackets[0].mApp = app;
        board.mSeedBank.mSeedPackets[0].mBoard = board;
        board.mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_ZOMBIE_NORMAL, SeedType.SEED_NONE);

        iz.iZombieMouseDownWithZombie(100, 100, -1);

        assertEquals(0, board.mZombies.mSize);
        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_DROP));
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, board.mCursorObject.mCursorType);
        assertEquals(SeedType.SEED_NONE, board.mCursorObject.mType);
    }
}
