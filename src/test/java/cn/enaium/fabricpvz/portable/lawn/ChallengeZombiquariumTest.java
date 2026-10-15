/*
 * Headless tests for {@link ChallengeZombiquarium} (Mini-Game Zombiquarium).
 *
 * Every asserted value is quoted from the upstream source line noted next to it
 * (src/Lawn/Challenge.cpp:3307-3443). The board is built on the same asset-free headless app the
 * Board/Coin suites use: real reanimations with stubbed track arrays, recording audio/effect
 * doubles, no window.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class ChallengeZombiquariumTest {

    /**
     * A headless Zombiquarium app whose reanimations look loaded and whose audio/particle calls are
     * recorded. Mirrors {@code BoardTest.LoadedReanimApp} plus the {@code LawnEntityTestSupport}
     * recording doubles.
     */
    private static final class RecordingApp extends LawnApp {
        final List<Integer> mFoleys = new ArrayList<>();
        final List<Long> mSamples = new ArrayList<>();
        int mParticleCount;

        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }

        @Override
        public void playFoley(int theFoleyType) {
            mFoleys.add(theFoleyType);
        }

        @Override
        public void playSample(long theSoundNum) {
            mSamples.add(theSoundNum);
        }

        @Override
        public PvzpParticleSystem addPvzpParticle(float theX, float theY, int theRenderOrder, int theEffect) {
            mParticleCount++;
            // Allocate from the global holder so port code that resolves the new particle by id
            // (Attachment.attachParticle, Board::TutorialArrowShow) works headlessly.
            return cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem.gEffectSystem
                    .mParticleHolder.mParticleSystems.dataArrayAlloc();
        }
    }

    private static RecordingApp newApp() {
        RecordingApp app = new RecordingApp();
        LawnApp.gLawnApp = app;
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        app.mPlayerInfo = app.mProfileMgr.addProfile("ZombiquariumTest");
        app.mAppRandSeed = 0x5EED;
        HeadlessTestApp.initialize(app);
        return app;
    }

    private static Board newBoard() {
        Common.sRand(0x5EED);
        Board board = new Board(newApp());
        board.mSunMoney = 150;
        // Board::InitLevel fills mRowPickingArray (Board.cpp:1361-1368); a raw ctor leaves the
        // entries null, which SaveGame's boardBaseFields chunk dereferences (via zombiesWon's save).
        for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
            board.mRowPickingArray[aRow] = new cn.enaium.fabricpvz.portable.pvzp.PvzpSmoothArray();
            board.mRowPickingArray[aRow].mItem = aRow;
        }
        return board;
    }

    private static ChallengeZombiquarium helper(Board theBoard) {
        return new ChallengeZombiquarium(theBoard.mChallenge);
    }

    private static List<GridItem> liveBrains(Board theBoard) {
        List<GridItem> aBrains = new ArrayList<>();
        for (GridItem aGridItem : theBoard.mGridItems) {
            if (!aGridItem.mDead && aGridItem.mGridItemType == GridItemType.GRIDITEM_BRAIN) {
                aBrains.add(aGridItem);
            }
        }
        return aBrains;
    }

    /** Adds one zombie so {@code ZombiquariumUpdate} does not take the game-over branch. */
    private static void seedZombie(Board theBoard) {
        assertNotNull(theBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 0, 0));
    }

    // ----------------------------------------------------------------------------- SpawnSnorkle

    @Test
    void spawnSnorkleCreatesARandomlyPositionedSnorkelZombie() {
        Board board = newBoard();
        Zombie aZombie = helper(board).zombiquariumSpawnSnorkle();

        assertNotNull(aZombie);
        assertEquals(ZombieType.ZOMBIE_SNORKEL, aZombie.mZombieType);
        assertEquals(0, aZombie.mRow, "Challenge.cpp:3309 AddZombieInRow(..., 0, 0)");
        // Challenge.cpp:3310-3311 RandRangeFloat(50, 650) / RandRangeFloat(100, 400).
        assertTrue(aZombie.mPosX >= 50.0f && aZombie.mPosX < 650.0f, "mPosX " + aZombie.mPosX);
        assertTrue(aZombie.mPosY >= 100.0f && aZombie.mPosY < 400.0f, "mPosY " + aZombie.mPosY);
        assertEquals(1, board.mZombies.mSize);
    }

    // ------------------------------------------------------------------------------- DropBrain

    @Test
    void dropBrainAllocatesTheUpstreamBrainGridItem() {
        Board board = newBoard();
        helper(board).zombiquariumDropBrain(200, 300);

        List<GridItem> aBrains = liveBrains(board);
        assertEquals(1, aBrains.size());
        GridItem aBrain = aBrains.get(0);
        assertEquals(GridItemType.GRIDITEM_BRAIN, aBrain.mGridItemType);
        assertEquals(400000, aBrain.mRenderOrder, "Challenge.cpp:3350");
        assertEquals(0, aBrain.mGridX);
        assertEquals(0, aBrain.mGridY);
        assertEquals(0, aBrain.mGridItemCounter, "Challenge.cpp:3353");
        assertEquals(185.0f, aBrain.mPosX, 0.001f, "Challenge.cpp:3354 x - 15");
        assertEquals(285.0f, aBrain.mPosY, 0.001f, "Challenge.cpp:3355 y - 15");
        assertTrue(((RecordingApp) board.mApp).mSamples.contains((long) SoundType.SOUND_TAP),
                "Challenge.cpp:3356 PlaySample(SOUND_TAP)");
    }

    @Test
    void dropBrainClearsTheFeedAdvice() {
        Board board = newBoard();
        board.displayAdvice("[ADVICE_ZOMBIQUARIUM_CLICK_TO_FEED]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                AdviceType.ADVICE_ZOMBIQUARIUM_CLICK_TO_FEED);
        assertTrue(board.mAdvice.mDuration != 0);

        helper(board).zombiquariumDropBrain(200, 300);

        assertEquals(0, board.mAdvice.mDuration, "Challenge.cpp:3348 ClearAdvice(ADVICE_ZOMBIQUARIUM_CLICK_TO_FEED)");
    }

    // ------------------------------------------------------------------------------ MouseDown

    @Test
    void mouseDownIgnoresClicksOutsideThePlayfield() {
        Board board = newBoard();
        ChallengeZombiquarium h = helper(board);

        h.zombiquariumMouseDown(79, 200);
        h.zombiquariumMouseDown(721, 200);
        h.zombiquariumMouseDown(200, 89);
        h.zombiquariumMouseDown(200, 431);

        assertEquals(0, liveBrains(board).size(), "Challenge.cpp:3362 bounds x[80,720] y[90,430]");
        assertEquals(150, board.mSunMoney, "out-of-bounds clicks must not spend sun");
    }

    @Test
    void mouseDownDropsABrainForFiveSun() {
        Board board = newBoard();
        helper(board).zombiquariumMouseDown(200, 200);

        assertEquals(1, liveBrains(board).size());
        assertEquals(145, board.mSunMoney, "Challenge.cpp:3377 TakeSunMoney(5)");
    }

    @Test
    void mouseDownDoesNothingWithoutFiveSun() {
        Board board = newBoard();
        board.mSunMoney = 4;
        helper(board).zombiquariumMouseDown(200, 200);

        assertEquals(0, liveBrains(board).size());
        assertEquals(4, board.mSunMoney);
    }

    @Test
    void mouseDownRefusesAFourthBrain() {
        Board board = newBoard();
        ChallengeZombiquarium h = helper(board);
        h.zombiquariumMouseDown(100, 100);
        h.zombiquariumMouseDown(200, 200);
        h.zombiquariumMouseDown(300, 300);
        assertEquals(3, liveBrains(board).size());
        assertEquals(135, board.mSunMoney);

        h.zombiquariumMouseDown(400, 400);

        assertEquals(3, liveBrains(board).size(), "Challenge.cpp:3375 aBrainsCount < 3");
        assertEquals(135, board.mSunMoney, "a refused drop must not spend sun");
    }

    // -------------------------------------------------------------------------- PacketClicked

    @Test
    void packetClickedSnorkleSpawnsAndSpends() {
        Board board = newBoard();
        RecordingApp app = (RecordingApp) board.mApp;
        board.mTutorialState = TutorialState.TUTORIAL_ZOMBIQUARIUM_BUY_SNORKEL;

        SeedPacket aPacket = new SeedPacket();
        aPacket.mPacketType = SeedType.SEED_ZOMBIQUARIUM_SNORKLE;
        int aParticlesBefore = app.mParticleCount;
        helper(board).zombiquariumPacketClicked(aPacket);

        assertEquals(1, board.mZombies.mSize, "Challenge.cpp:3332 ZombiquariumSpawnSnorkle()");
        assertEquals(50, board.mSunMoney, "snorkel costs 100 sun");
        assertTrue(app.mFoleys.contains(FoleyType.FOLEY_ZOMBIESPLASH), "Challenge.cpp:3333 PlayFoley(FOLEY_ZOMBIESPLASH)");
        assertEquals(aParticlesBefore + 1, app.mParticleCount, "Challenge.cpp:3334 PARTICLE_PLANTING_POOL");
        // Challenge.cpp:3325-3329 tutorial advance.
        assertEquals(TutorialState.TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL, board.mTutorialState);
    }

    @Test
    void packetClickedSnorkleDoesNothingWithoutEnoughSun() {
        Board board = newBoard();
        board.mSunMoney = 50;
        SeedPacket aPacket = new SeedPacket();
        aPacket.mPacketType = SeedType.SEED_ZOMBIQUARIUM_SNORKLE;

        helper(board).zombiquariumPacketClicked(aPacket);

        assertEquals(0, board.mZombies.mSize);
        assertEquals(50, board.mSunMoney);
    }

    @Test
    void packetClickedTrophySpawnsTheLevelAward() {
        Board board = newBoard();
        board.mSunMoney = 2000;
        SeedPacket aPacket = new SeedPacket();
        aPacket.mPacketType = SeedType.SEED_ZOMBIQUARIUM_TROPHY;

        helper(board).zombiquariumPacketClicked(aPacket);

        // Challenge.cpp:3340-3342 SpawnLevelAward(2, 0) + ClearAdvice(ADVICE_NONE).
        assertTrue(board.mLevelAwardSpawned);
        assertEquals(BoardResult.BOARDRESULT_WON, board.mApp.mBoardResult);
        assertEquals(0, board.mAdvice.mDuration);
    }

    // -------------------------------------------------------------------------------- Update

    @Test
    void updateEndsTheLevelWhenNoZombiesRemain() {
        Board board = newBoard();
        helper(board).zombiquariumUpdate();

        // Challenge.cpp:3383-3387 ZombiesWon(nullptr) when the board is clear.
        assertEquals(BoardResult.BOARDRESULT_LOST, board.mApp.mBoardResult);
    }

    @Test
    void updateShowsTheCollectSunHintAndArmsTheBuySnorkelTutorial() {
        Board board = newBoard();
        seedZombie(board);
        board.mSunMoney = 500;

        helper(board).zombiquariumUpdate();

        assertTrue(board.mHelpDisplayed[AdviceType.ADVICE_ZOMBIQUARIUM_COLLECT_SUN],
                "Challenge.cpp:3388-3392 collect-sun advice");
        assertEquals(TutorialState.TUTORIAL_ZOMBIQUARIUM_BUY_SNORKEL, board.mTutorialState,
                "Challenge.cpp:3401-3406 aScore >= 110 teaches the snorkel purchase");
        // Challenge.cpp:3395-3396 PvzpAnimateCurve(0, 1000, 500, 0, 150, LINEAR) = 75.
        assertEquals(75, board.mProgressMeterWidth);
    }

    @Test
    void updateAdvancesAndRewindsTheTrophyTutorial() {
        Board board = newBoard();
        seedZombie(board);

        // Bought the snorkel and reached the winning score -> click-the-trophy.
        board.mSunMoney = Challenge.ZOMBIQUARIUM_WINNING_SCORE;
        board.mTutorialState = TutorialState.TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL;
        helper(board).zombiquariumUpdate();
        assertEquals(TutorialState.TUTORIAL_ZOMBIQUARIUM_CLICK_TROPHY, board.mTutorialState,
                "Challenge.cpp:3419-3427 aScore >= WINNING_SCORE");
        assertTrue(board.mHelpDisplayed[AdviceType.ADVICE_ALMOST_THERE],
                "Challenge.cpp:3398-3401 ALMOST_THERE at WINNING_SCORE - 100");

        // Score slips and the tutorial rewinds.
        board.mSunMoney = 500;
        helper(board).zombiquariumUpdate();
        assertEquals(TutorialState.TUTORIAL_ZOMBIQUARIUM_BOUGHT_SNORKEL, board.mTutorialState,
                "Challenge.cpp:3428-3433 aScore < WINNING_SCORE");

        assertEquals(75, board.mProgressMeterWidth, "Challenge.cpp:3395-3396 clamp + curve");
    }

    @Test
    void updateDriftsBrainsDownAndKillsThemBelowTheLawn() {
        Board board = newBoard();
        seedZombie(board);
        ChallengeZombiquarium h = helper(board);
        h.zombiquariumDropBrain(200, 200);
        GridItem aBrain = liveBrains(board).get(0);

        // Challenge.cpp:3435-3442: +1 counter, +0.15 y per tick, die at y >= 500.
        for (int i = 0; i < 2400 && !aBrain.mDead; i++) {
            h.zombiquariumUpdate();
        }

        assertTrue(aBrain.mDead, "brain must die past y=500 (started at 200, 0.15/tick)");
        assertTrue(aBrain.mGridItemCounter > 0, "the drift loop increments the counter each tick");
        assertTrue(aBrain.mPosY >= 500.0f);
    }

    @Test
    void updateLeavesNonBrainGridItemsAlone() {
        Board board = newBoard();
        seedZombie(board);
        GridItem aLadder = board.mGridItems.dataArrayAlloc();
        aLadder.mGridItemType = GridItemType.GRIDITEM_LADDER;
        aLadder.mPosY = 100.0f;

        helper(board).zombiquariumUpdate();

        assertFalse(aLadder.mDead);
        assertEquals(100.0f, aLadder.mPosY, 0.001f, "only GRIDITEM_BRAIN drifts");
        assertEquals(0, aLadder.mGridItemCounter);
    }
}
