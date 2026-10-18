package cn.enaium.fabricpvz.portable.test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.BoardResult;
import cn.enaium.fabricpvz.portable.lawn.CoinMotion;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GridSquareType;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantRowType;
import cn.enaium.fabricpvz.portable.lawn.PlantingReason;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.TutorialState;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;

/**
 * Extreme-load frame benchmark: builds a synthetic late-game board (a full lawn of shooters, a
 * 100/300/600 zombie flood, survival-endless waves past wave 40, an effect storm) and measures the
 * frame loop three ways — update / draw / present per frame — together with the drops against the
 * 100 Hz budget, allocation per frame, GC activity, peak object counts and draw-call volume.
 *
 * <p>It is the load-shape sibling of {@link FrameBench}: same measurement discipline (JVM MXBeans,
 * no renderer touching from a sampling thread), but it drives the frame itself so every phase can be
 * timed separately, and it builds the load instead of playing the game. Nothing in {@code src/main}
 * is instrumented; the two test-side hooks are the mod API's own {@link LawnHooksRegistry} (scene
 * maintenance, registered like a mod would register it) and a delegating {@link RendererBackend}
 * installed right after {@code init()} to count draw calls ({@link CountingRenderer}).
 *
 * <h2>Usage</h2>
 * <pre>
 * cd PvZ-Portable
 * ./gradlew stressBench -Ppvz.stress.scene=gatling -Ppvz.renderer=metal
 * ./gradlew stressBench -Ppvz.stress.scene=zombies -Ppvz.stress.zombies=600 -Ppvz.stress.seconds=10
 * ./gradlew stressBench -Ppvz.stress.scene=endless -Ppvz.stress.seconds=180 -Ppvz.stress.mode=loop
 * </pre>
 *
 * <p>Scenes: {@code title}, {@code empty}, {@code peashooter}, {@code gatling}, {@code zombies},
 * {@code endless}, {@code effects}. Modes: {@code frame} (the harness drives the frame, every phase
 * timed separately, vsync bypassed — this is the per-frame cost) and {@code loop} (the game's own
 * {@code LawnApp.start()} loop with vsync and the 100 Hz sleep, sampled once a second — this is where
 * real dropped frames show up as fewer updates than wall-clock seconds).
 */
public final class StressBench {

    /** {@code SexyAppBase.mFrameTime} is 10 ms: one game tick is one 100 Hz frame. */
    private static final int TICKS_PER_SECOND = 100;

    /** The 100 Hz frame budget the drops are counted against. */
    private static final double FRAME_BUDGET_MS = 10.0;

    /** The flood scenes draw zombie types from here, per the contract's mix (normal .. gargantuar). */
    private static final int[] FLOOD_TYPES = {
        ZombieType.ZOMBIE_NORMAL,
        ZombieType.ZOMBIE_TRAFFIC_CONE,
        ZombieType.ZOMBIE_PAIL,
        ZombieType.ZOMBIE_FOOTBALL,
        ZombieType.ZOMBIE_GARGANTUAR,
    };

    /** Volley-scene targets, one per row, rotating the three the board has to chew through. */
    private static final int[] TARGET_TYPES = {
        ZombieType.ZOMBIE_GARGANTUAR,
        ZombieType.ZOMBIE_PAIL,
        ZombieType.ZOMBIE_FOOTBALL,
        ZombieType.ZOMBIE_TRAFFIC_CONE,
        ZombieType.ZOMBIE_GARGANTUAR,
        ZombieType.ZOMBIE_PAIL,
    };

    /**
     * Body hit points a volley-scene target is given. A full lawn of 45 gatling peas removes a
     * retail gargantuar in well under a second, i.e. the volley would stop instead of being measured;
     * the target keeps a firing window open for the whole run window instead. The health is the only
     * field the benchmark writes on a game object, and it is written to make the load last, never to
     * change how the damage itself is processed.
     */
    private static final int TARGET_HEALTH = 50_000;

    /** No wave arrives on its own in the day scenes: this countdown outlives any window. */
    private static final int WAVES_OFF = 1_000_000;

    // ------------------------------------------------------------------------------------------
    // parameters
    // ------------------------------------------------------------------------------------------

    private final StressApp mApp;
    private final Path mOutDir;
    /**
     * Scene plus the parameters that change what the scene measures, used in the CSV file name so
     * that runs which differ only in a parameter (z100/z300/z600, the vsync variant, the endless run
     * with the population cap disabled) do not overwrite each other's CSV.
     */
    private final String mFileTag;
    private final String mScene;
    private final String mMode;
    private final int mFrames;
    private final int mWarmupFrames;
    private final int mRepeats;
    private final int mZombieTarget;
    private final int mLevel;
    private final int mEndlessStage;
    private final int mEndlessCountDown;
    /** Frame interval between presents in frame mode; 0 never presents, 1 presents every frame. */
    private final int mPresentEvery;
    /** Whether the endless scene replants an eaten square, so the plant side keeps producing. */
    private final boolean mReplant;
    private final int mZombieCap;
    private final int mEffectSuns;
    private final int mEffectCoins;
    private final int mCherryBombs;
    private final long mMaxWallNanos;
    private final boolean mCountDraws;

    // ------------------------------------------------------------------------------------------
    // scene state (kept across warmup and the measured window)
    // ------------------------------------------------------------------------------------------

    private int mStageAdvances;
    private int mSpawned;
    /** Seed the lawn was built from, so the endless scene can put an eaten square back. */
    private int mLawnSeed = SeedType.SEED_NONE;
    private int mPlantedAtSetup;

    private StressBench(StressApp theApp, Path theOutDir) {
        mApp = theApp;
        mOutDir = theOutDir;
        mScene = System.getProperty("pvz.stress.scene", "gatling");
        mMode = System.getProperty("pvz.stress.mode", "frame");
        mRepeats = Integer.getInteger("pvz.stress.repeats", 2);
        int seconds = Integer.getInteger("pvz.stress.seconds", mScene.equals("endless") ? 180 : 10);
        mFrames = Integer.getInteger("pvz.stress.frames", seconds * TICKS_PER_SECOND);
        // 600 frames (6 game-seconds): long enough that the JIT is warm and the particle pool has
        // reached its steady state before either repeat is measured
        mWarmupFrames = Integer.getInteger("pvz.stress.warmup", 600);
        mZombieTarget = Integer.getInteger("pvz.stress.zombies", 100);
        mLevel = Integer.getInteger("pvz.stress.level", 6);
        mEndlessStage = Integer.getInteger("pvz.stress.endlessStage", 2);
        mEndlessCountDown = Integer.getInteger("pvz.stress.endlessCountDown", 150);
        mPresentEvery = Integer.getInteger("pvz.stress.presentEvery", 60);
        mReplant = Boolean.parseBoolean(System.getProperty("pvz.stress.replant", "true"));
        // The endless scene's waves are compressed 16x, so its zombie population would otherwise grow
        // without bound: measured over three minutes it reaches ~800 zombies and ~965 live
        // reanimations, and the reanimation pool (1024) then throws "Data array full: reanims" from
        // LawnApp.addReanimation. The cap holds the population at a level a late endless session
        // actually shows, which is also what makes a "stable segment" measurable.
        mZombieCap = Integer.getInteger("pvz.stress.zombieCap", mScene.equals("endless") ? 250 : 0);
        mEffectSuns = Integer.getInteger("pvz.stress.effects.suns", 100);
        mEffectCoins = Integer.getInteger("pvz.stress.effects.coins", 100);
        mCherryBombs = Integer.getInteger("pvz.stress.effects.cherryBombs", 3);
        mMaxWallNanos = Integer.getInteger("pvz.stress.maxWallSeconds", 0) * 1_000_000_000L;
        mCountDraws = Boolean.parseBoolean(System.getProperty("pvz.stress.countDraws", "true"));

        StringBuilder tag = new StringBuilder(mScene);
        if (mScene.equals("zombies")) {
            tag.append("-z").append(mZombieTarget);
        }
        if (mScene.equals("endless") && mZombieCap == 0) {
            tag.append("-nocap");
        }
        if (mPresentEvery == 1) {
            tag.append("-vsync");
        }
        String jfrFile = System.getProperty("pvz.stress.jfr", "");
        if (!jfrFile.isBlank()) {
            tag.append("-jfr");
        }
        mFileTag = tag.toString();
    }

    public static void main(String[] args) throws Exception {
        Path runDir = Path.of(System.getProperty("pvz.gameDir", ".")).toAbsolutePath();
        Path saveDir = runDir.resolve("stress-userdata");
        Files.createDirectories(saveDir);
        Path outDir = runDir.resolve("build").resolve("stress");
        Files.createDirectories(outDir);

        boolean countDraws = Boolean.parseBoolean(System.getProperty("pvz.stress.countDraws", "true"));
        String jfrFile = System.getProperty("pvz.stress.jfr");
        jdk.jfr.Recording recording = null;
        if (jfrFile != null && !jfrFile.isEmpty()) {
            recording = new jdk.jfr.Recording();
            recording.setName("pvz-stress");
            recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(2));
            recording.enable("jdk.ObjectAllocationSample").withPeriod(java.time.Duration.ofMillis(5));
            recording.start();
        }
        Main.installHooks();
        StressApp app = new StressApp(countDraws);
        LawnApp.gLawnApp = app;
        StressBench bench = new StressBench(app, outDir);
        System.out.printf(Locale.ROOT, "[stress] backend=%s scene=%s mode=%s frames=%d (%.1f game-seconds)"
                        + " repeats=%d warmup=%d drawCounting=%s%n",
                System.getProperty("pvz.renderer", "(default)"), bench.mScene, bench.mMode, bench.mFrames,
                bench.mFrames / (double) TICKS_PER_SECOND, bench.mRepeats, bench.mWarmupFrames, countDraws);
        try {
            app.setArgs(new String[] {"pvz-portable", "-savedir", saveDir.toString()});
            app.init();
            app.startLoadingThread();
            bench.waitForLoading();
            bench.setUpScene();
            System.out.println("[stress] canvas=" + app.mRendererBackend.logicalWidth() + "x"
                    + app.mRendererBackend.logicalHeight() + " output="
                    + app.mRendererBackend.renderOutputWidth() + "x"
                    + app.mRendererBackend.renderOutputHeight());
            // The scene's own maintenance runs inside the game's tick (the mod API's onTick hook), so
            // the game drives its own scene in both modes and the work is attributed to the phase that
            // does it. Registered after the scene is built, so the setup ticks run no maintenance.
            LawnHooksRegistry.add(bench.new SceneHooks());
            if (bench.mMode.equals("loop")) {
                bench.runLoopMode();
            } else {
                bench.runFrameMode();
            }
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
            if (recording != null) {
                recording.stop();
                recording.dump(Path.of(jfrFile));
                System.out.println("[stress] JFR written to " + jfrFile);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // boot
    // ------------------------------------------------------------------------------------------

    /**
     * The title screen's state machine only advances once it has drawn ({@code mDrawnYet}), and the
     * loading thread is started from that state machine, so this pumps update+draw+present exactly
     * like {@link AdventurePlaythrough} does.
     */
    private void waitForLoading() throws InterruptedException {
        long deadline = System.nanoTime() + 180_000_000_000L;
        while (!mApp.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            mApp.processDeferredMessages(false);
            mApp.doUpdateFrames();
            mApp.mWidgetManager.drawScreen();
            mApp.mRendererBackend.present();
            Thread.sleep(2);
        }
        if (!mApp.mLoadingThreadCompleted.get()) {
            throw new IllegalStateException("loading thread did not finish in 180 s");
        }
        System.out.println("[stress] loading complete");
    }

    // ------------------------------------------------------------------------------------------
    // scene setup
    // ------------------------------------------------------------------------------------------

    private void setUpScene() throws InterruptedException {
        // The upgrade seeds (gatling pea) are refused by Board::CanPlantAt unless the game is in its
        // easy-planting mode: the benchmark builds a board, it does not buy the upgrade on the way.
        mApp.mEasyPlantingCheat = true;
        Board board;
        switch (mScene) {
        case "title":
            // The title screen is what LawnApp::init put up: nothing to build, no board at all.
            System.out.println("[stress] scene=title (title screen, no level)");
            return;
        case "empty":
            board = startBoard(GameMode.GAMEMODE_ADVENTURE, mLevel);
            wavesOff(board);
            break;
        case "peashooter":
            board = startBoard(GameMode.GAMEMODE_ADVENTURE, mLevel);
            wavesOff(board);
            mLawnSeed = SeedType.SEED_PEASHOOTER;
            fillLawn(board, mLawnSeed);
            addTargets(board);
            break;
        case "gatling":
            board = startBoard(GameMode.GAMEMODE_ADVENTURE, mLevel);
            wavesOff(board);
            mLawnSeed = SeedType.SEED_GATLINGPEA;
            fillLawn(board, mLawnSeed);
            addTargets(board);
            break;
        case "zombies":
            board = startBoard(GameMode.GAMEMODE_ADVENTURE, mLevel);
            wavesOff(board);
            mLawnSeed = SeedType.SEED_GATLINGPEA;
            fillLawn(board, mLawnSeed);
            topUpZombies(board, mZombieTarget);
            break;
        case "endless":
            board = startBoard(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3, 0);
            mLawnSeed = SeedType.SEED_GATLINGPEA;
            fillLawn(board, mLawnSeed);
            board.mChallenge.mSurvivalStage = mEndlessStage;
            board.mCurrentWave = 0;
            board.mNumWaves = board.getNumWavesPerSurvivalStage();
            board.pickZombieWaves();
            board.mZombieHealthToNextWave = -1;
            board.mZombieHealthWaveStart = 0;
            board.mZombieCountDown = mEndlessCountDown;
            board.mZombieCountDownStart = mEndlessCountDown;
            System.out.println("[stress] endless mode=" + mApp.mGameMode + " stage=" + mEndlessStage
                    + " flags=" + board.getSurvivalFlagsCompleted() + " wavesPerStage=" + board.mNumWaves);
            break;
        case "effects":
            board = startBoard(GameMode.GAMEMODE_ADVENTURE, mLevel);
            wavesOff(board);
            topUpCoins(board, mEffectSuns, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
            topUpCoins(board, mEffectCoins, CoinType.COIN_GOLD, CoinMotion.COIN_MOTION_FROM_SKY_SLOW);
            for (int i = 0; i < mCherryBombs; i++) {
                addCherryBomb(board);
            }
            break;
        default:
            throw new IllegalArgumentException("unknown scene " + mScene
                    + " (want title|empty|peashooter|gatling|zombies|endless|effects)");
        }
        mPlantedAtSetup = mLawnSeed == SeedType.SEED_NONE ? 0 : board.mPlants.mSize;
        System.out.println("[stress] scene=" + mScene + " mode=" + mApp.mGameMode + " level=" + board.mLevel
                + " bg=" + board.mBackground + " rows=" + rowCount(board) + " plants=" + board.mPlants.mSize
                + " zombies=" + board.mZombies.mSize + " coins=" + board.mCoins.mSize
                + " waves=" + board.mNumWaves + " wave=" + board.mCurrentWave);
    }

    /** Starts one level the way the harnesses of this repo do, with the level intro fast-forwarded. */
    private Board startBoard(int theGameMode, int theLevel) throws InterruptedException {
        PlayerInfo aProfile = mApp.mProfileMgr.addProfile("StressBench");
        aProfile.mFinishedAdventure = 1;
        aProfile.mLevel = theLevel;
        mApp.mPlayerInfo = aProfile;
        mApp.mGameMode = theGameMode;
        mApp.killGameSelector();
        mApp.preNewGame(theGameMode, false);
        mApp.startPlaying();
        // Fast-forward the level intro the way CutScene does when it is skipped (see SceneDump): the
        // board places its lawn items and the seed bank slides in on those ticks.
        for (int i = 0; i < 3; i++) {
            tick();
        }
        mApp.mBoard.mCutScene.mCutsceneTime = 100000;
        for (int i = 0; i < 5; i++) {
            tick();
        }
        mApp.mBoard.setTutorialState(TutorialState.TUTORIAL_OFF);
        mApp.mBoard.clearAdviceImmediately();
        // The level intro the harness skipped is what places the mowers (CutScene), so they are
        // placed here: the board of a real level has them, and a zombie that walks past the lawn
        // then meets a mower instead of ending the level.
        mApp.mBoard.initLawnMowers();
        return mApp.mBoard;
    }

    /** One plain frame (no measurement), used while the scene is being built. */
    private void tick() throws InterruptedException {
        mApp.processDeferredMessages(false);
        mApp.doUpdateFrames();
        mApp.mWidgetManager.drawScreen();
        mApp.mRendererBackend.present();
        Thread.sleep(2);
    }

    private static void wavesOff(Board theBoard) {
        theBoard.mZombieCountDown = WAVES_OFF;
        theBoard.mZombieCountDownStart = WAVES_OFF;
        theBoard.mHugeWaveCountDown = 0;
    }

    private static int rowCount(Board theBoard) {
        int rows = 0;
        for (int row = 0; row < Board.MAX_GRID_SIZE_Y; row++) {
            if (theBoard.mPlantRow[row] != PlantRowType.PLANTROW_DIRT) {
                rows++;
            }
        }
        return rows;
    }

    /**
     * Plants every square the game itself would accept: {@code canPlantAt} is the planting rule
     * (water needs a lily pad first, dirt takes nothing), so the lawn this builds is a legal lawn
     * rather than one the benchmark guessed at.
     */
    private static void fillLawn(Board theBoard, int theSeedType) {
        fillLawn(theBoard, theSeedType, true);
    }

    private static void fillLawn(Board theBoard, int theSeedType, boolean theVerbose) {
        java.util.Map<Integer, Integer> rejects = new java.util.TreeMap<>();
        int pads = 0;
        for (int row = 0; row < Board.MAX_GRID_SIZE_Y; row++) {
            for (int col = 0; col < Board.MAX_GRID_SIZE_X; col++) {
                // lily pads first, so the water squares pass the shooter's own planting check below
                int aReason = theBoard.canPlantAt(col, row, SeedType.SEED_LILYPAD);
                if (aReason == PlantingReason.PLANTING_OK) {
                    theBoard.newPlant(col, row, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
                    pads++;
                } else if (theBoard.mGridSquareType[col][row] != GridSquareType.GRIDSQUARE_DIRT) {
                    rejects.merge(aReason, 1, Integer::sum);
                }
            }
        }
        int planted = 0;
        for (int row = 0; row < Board.MAX_GRID_SIZE_Y; row++) {
            for (int col = 0; col < Board.MAX_GRID_SIZE_X; col++) {
                int aReason = theBoard.canPlantAt(col, row, theSeedType);
                if (aReason == PlantingReason.PLANTING_OK) {
                    theBoard.newPlant(col, row, theSeedType, SeedType.SEED_NONE);
                    planted++;
                } else {
                    rejects.merge(aReason, 1, Integer::sum);
                }
            }
        }
        if (theVerbose) {
            System.out.println("[stress] fillLawn seed=" + theSeedType + " pads=" + pads + " planted="
                    + planted + " rejects=" + rejects);
        }
    }

    /** One high-health target per plant row, so every shooter on the board has something to fire at. */
    private static void addTargets(Board theBoard) {
        int index = 0;
        for (int row = 0; row < Board.MAX_GRID_SIZE_Y; row++) {
            if (theBoard.mPlantRow[row] == PlantRowType.PLANTROW_DIRT) {
                continue;
            }
            Zombie aZombie = theBoard.addZombieInRow(TARGET_TYPES[index++ % TARGET_TYPES.length], row, -1);
            if (aZombie != null) {
                aZombie.mBodyHealth = TARGET_HEALTH;
                aZombie.mBodyMaxHealth = TARGET_HEALTH;
            }
        }
    }

    /** Keeps {@code theCount} zombies walking into the lawn: the dead ones are replaced as they fall. */
    private void topUpZombies(Board theBoard, int theCount) {
        int rows = rowCount(theBoard);
        int missing = theCount - theBoard.mZombies.mSize;
        for (int i = 0; i < missing; i++) {
            int aType = FLOOD_TYPES[(mSpawned + i) % FLOOD_TYPES.length];
            theBoard.addZombieInRow(aType, (mSpawned + i) % rows, -1);
        }
        if (missing > 0) {
            mSpawned += missing;
        }
    }

    private void topUpCoins(Board theBoard, int theCount, int theCoinType, int theMotion) {
        int missing = theCount - theBoard.countCoinByType(theCoinType);
        for (int i = 0; i < missing; i++) {
            theBoard.addCoin(60 + (int) (Math.random() * 620), 40 + (int) (Math.random() * 180), theCoinType,
                    theMotion);
        }
    }

    /** One cherry bomb on a free square, reloaded as the previous one detonates. */
    private void addCherryBomb(Board theBoard) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int col = (int) (Math.random() * Board.MAX_GRID_SIZE_X);
            int row = (int) (Math.random() * Board.MAX_GRID_SIZE_Y);
            if (theBoard.canPlantAt(col, row, SeedType.SEED_CHERRYBOMB) == PlantingReason.PLANTING_OK) {
                theBoard.newPlant(col, row, SeedType.SEED_CHERRYBOMB, SeedType.SEED_NONE);
                return;
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // scene maintenance, on the game's own tick (mod API hook)
    // ------------------------------------------------------------------------------------------

    /** The scene's upkeep, driven from {@code Board::UpdateGame} — no game code is touched for it. */
    private final class SceneHooks implements LawnHooks {
        @Override
        public void onTick(Board theBoard, int theTick) {
            switch (mScene) {
            case "peashooter", "gatling":
                keepTargets(theBoard);
                maintainLawn(theBoard);
                break;
            case "zombies":
                topUpZombies(theBoard, mZombieTarget);
                maintainLawn(theBoard);
                break;
            case "effects":
                if ((theTick & 15) == 0) {
                    topUpCoins(theBoard, mEffectSuns, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_FROM_SKY);
                    topUpCoins(theBoard, mEffectCoins, CoinType.COIN_GOLD,
                            CoinMotion.COIN_MOTION_FROM_SKY_SLOW);
                    if (cherryBombCount(theBoard) < mCherryBombs) {
                        addCherryBomb(theBoard);
                    }
                }
                break;
            case "endless":
                driveEndless(theBoard);
                break;
            default:
                break;
            }
        }
    }

    /**
     * Keeps the lawn at the size the scene was built with. A flood eats the front squares within the
     * first seconds (a zombie reaches column 8 after roughly 200 ticks), and an eaten square is never
     * replanted by the game: without this, the second repeat of a flood scene would measure a board
     * with a different plant side than the first, which is exactly what must not vary between runs.
     */
    private void maintainLawn(Board theBoard) {
        if (mReplant && mLawnSeed != SeedType.SEED_NONE && theBoard.mPlants.mSize < mPlantedAtSetup) {
            fillLawn(theBoard, mLawnSeed, false);
        }
    }

    /** Replaces a target the lawn finally chewed through, so the volley keeps being measured. */
    private void keepTargets(Board theBoard) {
        int rows = rowCount(theBoard);
        int alive = 0;
        for (Zombie aZombie : theBoard.mZombies) {
            if (!aZombie.mDead) {
                alive++;
            }
        }
        if (alive >= rows) {
            return;
        }
        Zombie aZombie = theBoard.addZombieInRow(TARGET_TYPES[mSpawned % TARGET_TYPES.length],
                mSpawned % rows, -1);
        if (aZombie != null) {
            aZombie.mBodyHealth = TARGET_HEALTH;
            aZombie.mBodyMaxHealth = TARGET_HEALTH;
            mSpawned++;
        }
    }

    private static int cherryBombCount(Board theBoard) {
        int count = 0;
        for (Plant aPlant : theBoard.mPlants) {
            if (!aPlant.mDead && aPlant.mSeedType == SeedType.SEED_CHERRYBOMB) {
                count++;
            }
        }
        return count;
    }

    /**
     * Drives the survival-endless board: waves arrive on a compressed countdown (the real generator,
     * at a shorter interval), and a stage that completes is rolled over the way {@code LawnApp} rolls
     * it when the player survives it ({@code mSurvivalStage++} and re-pick), so the board keeps the
     * flag-curve weights of a session that is far past wave 40 instead of ending at wave 20.
     *
     * <p>The countdown is only shortened, never forced to a value the next tick would overwrite: a
     * clamp of "not more than N" still lets the countdown walk down to zero and spawn.
     */
    private void driveEndless(Board theBoard) {
        if (theBoard.mBoardFadeOutCounter >= 0) {
            // never let the stage end under the harness: the fade would hand the level back to the
            // intro/seed-chooser path and the window would stop measuring a playing board
            theBoard.mBoardFadeOutCounter = -1;
        }
        if (theBoard.mApp.mBoardResult == BoardResult.BOARDRESULT_LOST) {
            // a zombie that got past every mower of a row would end the level; the benchmark is
            // measuring the board, so the loss is cleared and the run continues
            theBoard.mApp.mBoardResult = BoardResult.BOARDRESULT_NONE;
        }
        if (mReplant && theBoard.mPlants.mSize < mPlantedAtSetup) {
            // put the eaten squares back, so the plant side keeps producing for the whole window
            maintainLawn(theBoard);
        }
        if (theBoard.mCurrentWave >= theBoard.mNumWaves) {
            theBoard.mChallenge.mSurvivalStage++;
            theBoard.mCurrentWave = 0;
            theBoard.mNumWaves = theBoard.getNumWavesPerSurvivalStage();
            theBoard.pickZombieWaves();
            theBoard.mZombieHealthToNextWave = -1;
            theBoard.mZombieHealthWaveStart = 0;
            theBoard.mZombieCountDown = mEndlessCountDown;
            theBoard.mZombieCountDownStart = mEndlessCountDown;
            mStageAdvances++;
            return;
        }
        if (mZombieCap > 0 && theBoard.mZombies.mSize >= mZombieCap) {
            if (theBoard.mZombieCountDown < 400) {
                // over the cap: hold the countdown so the next wave waits for the lawn to thin them out
                theBoard.mZombieCountDown = 400;
                theBoard.mZombieCountDownStart = 400;
            }
            return;
        }
        if (theBoard.mZombieCountDown > mEndlessCountDown) {
            theBoard.mZombieCountDown = mEndlessCountDown;
        }
    }

    // ------------------------------------------------------------------------------------------
    // frame mode: the harness drives the frame, so every phase is timed on its own
    // ------------------------------------------------------------------------------------------

    private void runFrameMode() throws Exception {
        for (int repeat = 1; repeat <= mRepeats; repeat++) {
            runFrameWindow(repeat);
        }
    }

    private void runFrameWindow(int theRepeat) throws Exception {
        Board board = mApp.mBoard;
        for (int i = 0; i < mWarmupFrames; i++) {
            frame(i);
        }
        mSpawned = 0;
        mStageAdvances = 0;

        Window window = new Window(mFrames);
        com.sun.management.ThreadMXBean aThreads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long aThreadId = Thread.currentThread().threadId();
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        long gcBefore = gcCount(collectors);
        long gcTimeBefore = gcTime(collectors);
        long heapBefore = usedHeap();

        long startNanos = System.nanoTime();
        long allocBefore = aThreads.getThreadAllocatedBytes(aThreadId);
        for (int i = 0; i < mFrames; i++) {
            measuredFrame(window, i);
            window.record(board, mApp, mCountDraws ? mApp.mCountingRenderer : null);
            if (mMaxWallNanos > 0 && System.nanoTime() - startNanos > mMaxWallNanos) {
                System.out.println("[stress] wall cap reached after " + window.mFrames + " frames");
                break;
            }
        }
        long allocAfter = aThreads.getThreadAllocatedBytes(aThreadId);
        long wallNanos = System.nanoTime() - startNanos;
        window.finish(allocAfter - allocBefore, gcCount(collectors) - gcBefore, gcTime(collectors) - gcTimeBefore,
                heapBefore, usedHeap(), wallNanos);

        String label = "scene=" + mScene + " mode=frame renderer=" + rendererName() + " repeat=" + theRepeat;
        printWindow(window, label);
        writeCsv(window, mFileTag + "-" + rendererName() + "-frame-r" + theRepeat + ".csv");
    }

    /** One unmeasured frame, used for warmup: the same three phases, not timed. */
    private void frame(int theFrameIndex) {
        mApp.processDeferredMessages(false);
        mApp.doUpdateFrames();
        mApp.processSafeDeleteList();
        mApp.mWidgetManager.drawScreen();
        if (mPresentEvery > 0 && theFrameIndex % mPresentEvery == 0) {
            mApp.mRendererBackend.present();
        }
    }

    /**
     * One measured frame: the same calls {@code SexyAppBase.process} makes (message pump, update,
     * safe-delete list, draw, present), timed around each phase. {@code doUpdateFramesF} is not
     * called because it is a no-op unless {@code mVSyncUpdates} is set, which the harness never sets.
     *
     * <p>{@code present} is only called every {@link #mPresentEvery} frames (default: once a game
     * second). Presenting is what the *display* gates: with a window on screen, {@code present} (and
     * the canvas composite inside {@code drawScreen}) wait for the display's frame callback, so a
     * frame that presents measures the refresh period, not the CPU. Presenting rarely keeps the
     * measurement a real one (the window still shows the game) while the per-frame numbers stay the
     * game's own cost; the present samples are reported separately.
     */
    private void measuredFrame(Window theWindow, int theFrameIndex) {
        long t0 = System.nanoTime();
        mApp.processDeferredMessages(false);
        long t1 = System.nanoTime();
        mApp.doUpdateFrames();
        mApp.processSafeDeleteList();
        long t2 = System.nanoTime();
        boolean drew = mApp.mWidgetManager.drawScreen();
        long t3 = System.nanoTime();
        boolean presented = false;
        if (mPresentEvery > 0 && theFrameIndex % mPresentEvery == 0) {
            mApp.mRendererBackend.present();
            presented = true;
        }
        long t4 = System.nanoTime();
        theWindow.add(t1 - t0, t2 - t1, t3 - t2, presented ? t4 - t3 : 0, drew, presented);
    }

    // ------------------------------------------------------------------------------------------
    // loop mode: the game's own loop, sampled once a second
    // ------------------------------------------------------------------------------------------

    private void runLoopMode() throws Exception {
        int seconds = Integer.getInteger("pvz.stress.loopSeconds", mFrames / TICKS_PER_SECOND);
        com.sun.management.ThreadMXBean aThreads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long aThreadId = Thread.currentThread().threadId();
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        StringBuilder csv = new StringBuilder("second,fps,updates,drawMsPerFrame,bltMs,gcCount,gcMs,heapMB,"
                + "scene,plants,zombies,projectiles,particles,coins,reanims,wave,stage,flags\n");

        Thread sampler = new Thread(() -> {
            long prevDraws = -1;
            long prevUpdates = -1;
            long prevDrawTime = -1;
            long prevGcCount = 0;
            long prevGcTime = 0;
            long baseUpdates = 0;
            long start = System.nanoTime();
            try {
                for (int tick = 1; tick <= seconds; tick++) {
                    Thread.sleep(1000);
                    Board board = mApp.mBoard;
                    long draws = mApp.mDrawCount;
                    long updates = mApp.mUpdateCount;
                    long drawTime = mApp.mDrawTime;
                    long aGcCount = gcCount(collectors);
                    long aGcTime = gcTime(collectors);
                    if (prevDraws < 0) {
                        prevDraws = draws;
                        prevUpdates = updates;
                        prevDrawTime = drawTime;
                        prevGcCount = aGcCount;
                        prevGcTime = aGcTime;
                        baseUpdates = updates;
                        continue;
                    }
                    long frames = draws - prevDraws;
                    long updateDelta = updates - prevUpdates;
                    double drawMsPerFrame = frames > 0 ? (drawTime - prevDrawTime) / (double) frames : 0;
                    System.out.printf(Locale.ROOT, "[stress] loop t=%3ds fps=%3d updates=%3d drawMs=%.3f"
                                    + " bltMs=%d gc=%d/%dms heap=%dMB scene=%d plants=%d zombies=%d proj=%d"
                                    + " part=%d%n",
                            tick, frames, updateDelta, drawMsPerFrame, mApp.mScreenBltTime,
                            aGcCount - prevGcCount, aGcTime - prevGcTime, usedHeap() / (1024 * 1024),
                            mApp.mGameScene, size(board == null ? null : board.mPlants),
                            size(board == null ? null : board.mZombies),
                            size(board == null ? null : board.mProjectiles), particleCount());
                    csv.append(tick).append(',').append(frames).append(',').append(updateDelta).append(',')
                            .append(String.format(Locale.ROOT, "%.4f", drawMsPerFrame)).append(',')
                            .append(mApp.mScreenBltTime).append(',')
                            .append(aGcCount - prevGcCount).append(',').append(aGcTime - prevGcTime).append(',')
                            .append(usedHeap() / (1024 * 1024)).append(',')
                            .append(mApp.mGameScene).append(',')
                            .append(size(board == null ? null : board.mPlants)).append(',')
                            .append(size(board == null ? null : board.mZombies)).append(',')
                            .append(size(board == null ? null : board.mProjectiles)).append(',')
                            .append(particleCount()).append(',')
                            .append(size(board == null ? null : board.mCoins)).append(',')
                            .append(reanimCount()).append(',')
                            .append(board == null ? -1 : board.mCurrentWave).append(',')
                            .append(board == null ? -1 : board.mChallenge.mSurvivalStage).append(',')
                            .append(flags(board, mApp)).append('\n');
                    prevDraws = draws;
                    prevUpdates = updates;
                    prevDrawTime = drawTime;
                    prevGcCount = aGcCount;
                    prevGcTime = aGcTime;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                double wall = (System.nanoTime() - start) / 1e9;
                long updates = mApp.mUpdateCount - baseUpdates;
                System.out.printf(Locale.ROOT, "[stress] loop done: wall=%.1fs updates=%d (%.1f game-seconds,"
                                + " %.1f%% of real time) draws=%d allocated=%.1f MB%n",
                        wall, updates, updates / (double) TICKS_PER_SECOND,
                        100.0 * (updates / (double) TICKS_PER_SECOND) / wall, mApp.mDrawCount,
                        aThreads.getThreadAllocatedBytes(aThreadId) / (1024.0 * 1024));
                mApp.mShutdown.set(true);
            }
        }, "stress-sampler");
        sampler.setDaemon(true);
        sampler.start();
        mApp.start();
        sampler.join(8000);
        Files.writeString(mOutDir.resolve(mFileTag + "-" + rendererName() + "-loop.csv"), csv.toString());
        System.out.println("[stress] loop csv: " + mOutDir.resolve(mFileTag + "-" + rendererName() + "-loop.csv"));
    }

    private static long size(DataArray<?> theArray) {
        return theArray == null ? -1 : theArray.mSize;
    }

    /** Flag count of a survival board; {@code -1} outside survival ({@code getSurvivalFlagsCompleted} throws there). */
    private static int flags(Board theBoard, LawnApp theApp) {
        return theBoard == null || theApp == null || !theApp.isSurvivalMode()
                ? -1 : theBoard.getSurvivalFlagsCompleted();
    }

    private long particleCount() {
        EffectSystem anEffect = mApp.mEffectSystem;
        if (anEffect == null || anEffect.mParticleHolder == null) {
            return -1;
        }
        return anEffect.mParticleHolder.mParticles.mSize;
    }

    private long reanimCount() {
        EffectSystem anEffect = mApp.mEffectSystem;
        if (anEffect == null || anEffect.mReanimationHolder == null) {
            return -1;
        }
        return anEffect.mReanimationHolder.mReanimations.mSize;
    }

    /** Renderer tag for file names: what was requested ({@code -Dpvz.renderer}), not the Java class. */
    private String rendererName() {
        String requested = System.getProperty("pvz.renderer", "");
        return requested.isBlank() ? "auto" : requested;
    }

    private static long gcCount(List<GarbageCollectorMXBean> theCollectors) {
        long total = 0;
        for (GarbageCollectorMXBean aCollector : theCollectors) {
            total += Math.max(aCollector.getCollectionCount(), 0);
        }
        return total;
    }

    private static long gcTime(List<GarbageCollectorMXBean> theCollectors) {
        long total = 0;
        for (GarbageCollectorMXBean aCollector : theCollectors) {
            total += Math.max(aCollector.getCollectionTime(), 0);
        }
        return total;
    }

    private static long usedHeap() {
        Runtime aRuntime = Runtime.getRuntime();
        return aRuntime.totalMemory() - aRuntime.freeMemory();
    }

    // ------------------------------------------------------------------------------------------
    // reporting
    // ------------------------------------------------------------------------------------------

    private void printWindow(Window theWindow, String theLabel) {
        System.out.printf(Locale.ROOT, "[stress] ---- %s frames=%d (%.1f game-seconds) wall=%.3fs drawn=%d/%d"
                        + " presentEvery=%d%n",
                theLabel, theWindow.mFrames, theWindow.mFrames / (double) TICKS_PER_SECOND,
                theWindow.mWallNanos / 1e9, theWindow.mDrawnFrames, theWindow.mFrames, mPresentEvery);
        printPhase(theWindow, "events", theWindow.mEvents);
        printPhase(theWindow, "update", theWindow.mUpdate);
        printPhase(theWindow, "draw", theWindow.mDraw);
        printPhase(theWindow, "total", theWindow.mTotal);
        if (theWindow.mPresentSamples.length > 0) {
            long[] present = theWindow.mPresentSamples.clone();
            Arrays.sort(present);
            System.out.printf(Locale.ROOT, "[stress] present ms (n=%d, display-synced) avg=%.3f p50=%.3f"
                            + " p95=%.3f max=%.3f%n",
                    present.length, mean(present, present.length), percentile(present, 0.5),
                    percentile(present, 0.95), percentile(present, 1.0));
        }

        int overBudget = 0;
        int longestRun = 0;
        int run = 0;
        for (int i = 0; i < theWindow.mFrames; i++) {
            if (theWindow.mTotal[i] / 1e6 > FRAME_BUDGET_MS) {
                overBudget++;
                run++;
                longestRun = Math.max(longestRun, run);
            } else {
                run = 0;
            }
        }
        double avg = mean(theWindow.mTotal, theWindow.mFrames);
        System.out.printf(Locale.ROOT, "[stress] budget %.0fms: over=%d/%d (%.2f%%) longestRun=%d"
                        + " headroom=%.1f%%%n",
                FRAME_BUDGET_MS, overBudget, theWindow.mFrames, 100.0 * overBudget / theWindow.mFrames,
                longestRun, 100.0 * (FRAME_BUDGET_MS - avg) / FRAME_BUDGET_MS);
        System.out.printf(Locale.ROOT, "[stress] alloc=%.1f KB/frame gc=%d (%d ms) heap=%dMB->%dMB%n",
                theWindow.mFrames == 0 ? 0 : theWindow.mAllocBytes / 1024.0 / theWindow.mFrames,
                theWindow.mGcCount, theWindow.mGcTimeMs, theWindow.mHeapBefore / (1024 * 1024),
                theWindow.mHeapAfter / (1024 * 1024));
        System.out.printf(Locale.ROOT, "[stress] peak plants=%d zombies=%d projectiles=%d particles=%d"
                        + " emitters=%d systems=%d reanims=%d coins=%d gridItems=%d mowers=%d%n",
                max(theWindow.mPlants), max(theWindow.mZombies), max(theWindow.mProjectiles),
                max(theWindow.mParticles), max(theWindow.mEmitters), max(theWindow.mSystems),
                max(theWindow.mReanims), max(theWindow.mCoins), max(theWindow.mGridItems),
                max(theWindow.mMowers));
        if (theWindow.mDrawCalls != null) {
            long[] draws = Arrays.copyOf(theWindow.mDrawCalls, theWindow.mFrames);
            Arrays.sort(draws);
            System.out.printf(Locale.ROOT, "[stress] drawCalls/frame avg=%.0f p50=%.0f p95=%.0f max=%.0f"
                            + " (texture=%d triangles=%d fill=%d line=%d)%n",
                    meanUnits(draws, draws.length), percentileUnits(draws, 0.5), percentileUnits(draws, 0.95),
                    percentileUnits(draws, 1.0), theWindow.mTextureDraws, theWindow.mTriangleDraws,
                    theWindow.mFillDraws, theWindow.mLineDraws);
        }
        if (mScene.equals("endless") || mScene.equals("zombies")) {
            printPerSecond(theWindow);
        }
    }

    private static void printPhase(Window theWindow, String theName, long[] theSamples) {
        long[] sorted = Arrays.copyOf(theSamples, theWindow.mFrames);
        Arrays.sort(sorted);
        System.out.printf(Locale.ROOT, "[stress] %-7s ms avg=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f%n",
                theName, mean(sorted, sorted.length), percentile(sorted, 0.5), percentile(sorted, 0.95),
                percentile(sorted, 0.99), percentile(sorted, 1.0));
    }

    /**
     * Per-game-second view of the endless scene: frame cost together with the load that produced it
     * (zombies, plants, projectiles, particles, wave/stage/flag) — the time series the contract asks
     * for, and the only honest way to pick a stable segment out of a session whose load grows.
     */
    private static void printPerSecond(Window theWindow) {
        System.out.println("[stress] per-second: t avgMs p95Ms maxMs zombies plants proj particles reanims"
                + " wave stage flags scene");
        for (int second = 1; second * TICKS_PER_SECOND <= theWindow.mFrames; second++) {
            int from = (second - 1) * TICKS_PER_SECOND;
            int to = second * TICKS_PER_SECOND;
            long[] slice = Arrays.copyOfRange(theWindow.mTotal, from, to);
            Arrays.sort(slice);
            System.out.printf(Locale.ROOT, "[stress] t=%3d avg=%.2f p95=%.2f max=%.2f z=%d p=%d pt=%d pa=%d"
                            + " r=%d w=%d s=%d f=%d scene=%d%n",
                    second, mean(slice, slice.length), percentile(slice, 0.95), percentile(slice, 1.0),
                    theWindow.mZombies[to - 1], theWindow.mPlants[to - 1], theWindow.mProjectiles[to - 1],
                    theWindow.mParticles[to - 1], theWindow.mReanims[to - 1], theWindow.mWaves[to - 1],
                    theWindow.mStages[to - 1], theWindow.mFlags[to - 1], theWindow.mScenes[to - 1]);
        }
        int from = Math.max(0, theWindow.mFrames - 60 * TICKS_PER_SECOND);
        long[] tail = Arrays.copyOfRange(theWindow.mTotal, from, theWindow.mFrames);
        Arrays.sort(tail);
        System.out.printf(Locale.ROOT, "[stress] last-60s: avg=%.3f p95=%.3f max=%.3f (zombies=%d plants=%d"
                        + " proj=%d particles=%d stage=%d flags=%d)%n",
                mean(tail, tail.length), percentile(tail, 0.95), percentile(tail, 1.0),
                theWindow.mZombies[theWindow.mFrames - 1], theWindow.mPlants[theWindow.mFrames - 1],
                theWindow.mProjectiles[theWindow.mFrames - 1], theWindow.mParticles[theWindow.mFrames - 1],
                theWindow.mStages[theWindow.mFrames - 1], theWindow.mFlags[theWindow.mFrames - 1]);
    }

    private void writeCsv(Window theWindow, String theFileName) throws Exception {
        StringBuilder out = new StringBuilder("frame,total_ms,events_ms,update_ms,draw_ms,present_ms,drawn,"
                + "plants,zombies,projectiles,particles,emitters,systems,reanims,coins,gridItems,mowers,"
                + "wave,stage,flags,scene,drawCalls\n");
        for (int i = 0; i < theWindow.mFrames; i++) {
            out.append(i).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", theWindow.mTotal[i] / 1e6)).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", theWindow.mEvents[i] / 1e6)).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", theWindow.mUpdate[i] / 1e6)).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", theWindow.mDraw[i] / 1e6)).append(',')
                    .append(String.format(Locale.ROOT, "%.4f", theWindow.mPresent[i] / 1e6)).append(',')
                    .append(theWindow.mDrawn[i]).append(',')
                    .append(theWindow.mPlants[i]).append(',').append(theWindow.mZombies[i]).append(',')
                    .append(theWindow.mProjectiles[i]).append(',').append(theWindow.mParticles[i]).append(',')
                    .append(theWindow.mEmitters[i]).append(',').append(theWindow.mSystems[i]).append(',')
                    .append(theWindow.mReanims[i]).append(',').append(theWindow.mCoins[i]).append(',')
                    .append(theWindow.mGridItems[i]).append(',').append(theWindow.mMowers[i]).append(',')
                    .append(theWindow.mWaves[i]).append(',').append(theWindow.mStages[i]).append(',')
                    .append(theWindow.mFlags[i]).append(',').append(theWindow.mScenes[i]).append(',')
                    .append(theWindow.mDrawCalls == null ? -1 : theWindow.mDrawCalls[i]).append('\n');
        }
        Files.writeString(mOutDir.resolve(theFileName), out.toString());
        System.out.println("[stress] csv: " + mOutDir.resolve(theFileName));
    }

    private static double mean(long[] theSamples, int theCount) {
        if (theCount == 0) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < theCount; i++) {
            total += theSamples[i];
        }
        return total / theCount / 1e6;
    }

    /** Nearest-rank percentile of a sorted array, in milliseconds. */
    private static double percentile(long[] theSorted, double theQuantile) {
        if (theSorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(theQuantile * theSorted.length) - 1;
        return theSorted[Math.max(0, Math.min(index, theSorted.length - 1))] / 1e6;
    }

    /** Mean of a sorted array in its own unit (used for the draw-call counts). */
    private static double meanUnits(long[] theSamples, int theCount) {
        if (theCount == 0) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < theCount; i++) {
            total += theSamples[i];
        }
        return total / theCount;
    }

    /** Nearest-rank percentile in the array's own unit (used for the draw-call counts). */
    private static double percentileUnits(long[] theSorted, double theQuantile) {
        if (theSorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(theQuantile * theSorted.length) - 1;
        return theSorted[Math.max(0, Math.min(index, theSorted.length - 1))];
    }

    private static long max(long[] theValues) {
        long max = 0;
        for (long aValue : theValues) {
            max = Math.max(max, aValue);
        }
        return max;
    }

    private static int max(int[] theValues) {
        int max = 0;
        for (int aValue : theValues) {
            max = Math.max(max, aValue);
        }
        return max;
    }

    // ------------------------------------------------------------------------------------------
    // measurement storage
    // ------------------------------------------------------------------------------------------

    private static final class Window {
        private final int mCapacity;
        private int mFrames;
        private final long[] mEvents;
        private final long[] mUpdate;
        private final long[] mDraw;
        private final long[] mPresent;
        private final long[] mTotal;
        private final int[] mDrawn;
        private final long[] mPlants;
        private final int[] mZombies;
        private final long[] mProjectiles;
        private final long[] mParticles;
        private final long[] mEmitters;
        private final long[] mSystems;
        private final long[] mReanims;
        private final long[] mCoins;
        private final long[] mGridItems;
        private final long[] mMowers;
        private final int[] mWaves;
        private final int[] mStages;
        private final int[] mFlags;
        private final int[] mScenes;
        private long[] mDrawCalls;
        private long mTextureDraws;
        private long mTriangleDraws;
        private long mFillDraws;
        private long mLineDraws;
        private long mPreviousDrawCalls = -1;
        private long mPreviousTextures;
        private long mPreviousTriangles;
        private long mPreviousFills;
        private long mPreviousLines;
        private int mDrawnFrames;
        private int mPresentedFrames;
        private long[] mPresentSamples;
        private long mAllocBytes;
        private long mGcCount;
        private long mGcTimeMs;
        private long mHeapBefore;
        private long mHeapAfter;
        private long mWallNanos;

        Window(int theCapacity) {
            mCapacity = theCapacity + 1;
            mEvents = new long[mCapacity];
            mUpdate = new long[mCapacity];
            mDraw = new long[mCapacity];
            mPresent = new long[mCapacity];
            mTotal = new long[mCapacity];
            mDrawn = new int[mCapacity];
            mPlants = new long[mCapacity];
            mZombies = new int[mCapacity];
            mProjectiles = new long[mCapacity];
            mParticles = new long[mCapacity];
            mEmitters = new long[mCapacity];
            mSystems = new long[mCapacity];
            mReanims = new long[mCapacity];
            mCoins = new long[mCapacity];
            mGridItems = new long[mCapacity];
            mMowers = new long[mCapacity];
            mWaves = new int[mCapacity];
            mStages = new int[mCapacity];
            mFlags = new int[mCapacity];
            mScenes = new int[mCapacity];
            mPresentSamples = new long[mCapacity];
        }

        void add(long theEvents, long theUpdate, long theDraw, long thePresent, boolean theDrew,
                boolean thePresented) {
            if (mFrames >= mCapacity) {
                return;
            }
            int i = mFrames++;
            mEvents[i] = theEvents;
            mUpdate[i] = theUpdate;
            mDraw[i] = theDraw;
            mPresent[i] = thePresent;
            // the CPU frame cost: what the game itself must spend to produce one 100 Hz tick
            mTotal[i] = theEvents + theUpdate + theDraw;
            mDrawn[i] = theDrew ? 1 : 0;
            if (theDrew) {
                mDrawnFrames++;
            }
            if (thePresented) {
                mPresentSamples[mPresentedFrames++] = thePresent;
            }
        }

        void record(Board theBoard, LawnApp theApp, CountingRenderer theRenderer) {
            int i = mFrames - 1;
            if (i < 0 || i >= mCapacity) {
                return;
            }
            EffectSystem anEffect = theApp == null ? null : theApp.mEffectSystem;
            mPlants[i] = theBoard == null ? -1 : theBoard.mPlants.mSize;
            mZombies[i] = theBoard == null ? -1 : theBoard.mZombies.mSize;
            mProjectiles[i] = theBoard == null ? -1 : theBoard.mProjectiles.mSize;
            mCoins[i] = theBoard == null ? -1 : theBoard.mCoins.mSize;
            mMowers[i] = theBoard == null ? -1 : theBoard.mLawnMowers.mSize;
            mGridItems[i] = theBoard == null ? -1 : theBoard.mGridItems.mSize;
            mWaves[i] = theBoard == null ? -1 : theBoard.mCurrentWave;
            mStages[i] = theBoard == null ? -1 : theBoard.mChallenge.mSurvivalStage;
            mFlags[i] = flags(theBoard, theApp);
            mScenes[i] = theApp == null ? -1 : theApp.mGameScene;
            if (anEffect == null || anEffect.mParticleHolder == null) {
                mParticles[i] = -1;
                mEmitters[i] = -1;
                mSystems[i] = -1;
            } else {
                mParticles[i] = anEffect.mParticleHolder.mParticles.mSize;
                mEmitters[i] = anEffect.mParticleHolder.mEmitters.mSize;
                mSystems[i] = anEffect.mParticleHolder.mParticleSystems.mSize;
            }
            mReanims[i] = anEffect == null || anEffect.mReanimationHolder == null
                    ? -1 : anEffect.mReanimationHolder.mReanimations.mSize;
            if (theRenderer != null) {
                if (mDrawCalls == null) {
                    mDrawCalls = new long[mCapacity];
                }
                mDrawCalls[i] = mPreviousDrawCalls < 0 ? 0 : theRenderer.mDrawCalls - mPreviousDrawCalls;
                mPreviousDrawCalls = theRenderer.mDrawCalls;
                mTextureDraws += theRenderer.mTextures - mPreviousTextures;
                mTriangleDraws += theRenderer.mTriangles - mPreviousTriangles;
                mFillDraws += theRenderer.mFills - mPreviousFills;
                mLineDraws += theRenderer.mLines - mPreviousLines;
                mPreviousTextures = theRenderer.mTextures;
                mPreviousTriangles = theRenderer.mTriangles;
                mPreviousFills = theRenderer.mFills;
                mPreviousLines = theRenderer.mLines;
            }
        }

        void finish(long theAllocBytes, long theGcCount, long theGcTimeMs, long theHeapBefore, long theHeapAfter,
                long theWallNanos) {
            mDrawCalls = mDrawCalls == null ? null : Arrays.copyOf(mDrawCalls, mFrames);
            mPresentSamples = Arrays.copyOf(mPresentSamples, mPresentedFrames);
            mAllocBytes = theAllocBytes;
            mGcCount = theGcCount;
            mGcTimeMs = theGcTimeMs;
            mHeapBefore = theHeapBefore;
            mHeapAfter = theHeapAfter;
            mWallNanos = theWallNanos;
        }
    }

    // ------------------------------------------------------------------------------------------
    // draw-call counting: a delegating backend installed right after init()
    // ------------------------------------------------------------------------------------------

    /**
     * Delegating {@link RendererBackend} that counts draw calls. {@link StressApp#init} installs it
     * on the app's own backend and points the screen image back at the app's field so the screen's
     * draws resolve through the wrapper too (see {@link GLImage}). It costs one field increment per
     * draw and never touches a pixel; {@code -Dpvz.stress.countDraws=false} removes it entirely.
     */
    static final class CountingRenderer implements RendererBackend {
        private final RendererBackend mDelegate;
        private long mDrawCalls;
        private long mTextures;
        private long mTriangles;
        private long mFills;
        private long mLines;

        CountingRenderer(RendererBackend theDelegate) {
            mDelegate = theDelegate;
        }

        @Override
        public int[] logicalPresentationRect() {
            return mDelegate.logicalPresentationRect();
        }

        @Override
        public int logicalWidth() {
            return mDelegate.logicalWidth();
        }

        @Override
        public int logicalHeight() {
            return mDelegate.logicalHeight();
        }

        @Override
        public long createTexture(int width, int height, int[] argbPixels) {
            return mDelegate.createTexture(width, height, argbPixels);
        }

        @Override
        public long createRenderTargetTexture(int width, int height) {
            return mDelegate.createRenderTargetTexture(width, height);
        }

        @Override
        public void updateTexture(long texture, int[] argbPixels, int width, int height) {
            mDelegate.updateTexture(texture, argbPixels, width, height);
        }

        @Override
        public void destroyTexture(long texture) {
            mDelegate.destroyTexture(texture);
        }

        @Override
        public void setTextureFilter(long texture, boolean linear) {
            mDelegate.setTextureFilter(texture, linear);
        }

        @Override
        public void drawTexture(long texture, int srcX, int srcY, int srcW, int srcH, float dstX, float dstY,
                float dstW, float dstH, float angleDegrees, float centerX, float centerY, int flip,
                int colorModARGB) {
            mDrawCalls++;
            mTextures++;
            mDelegate.drawTexture(texture, srcX, srcY, srcW, srcH, dstX, dstY, dstW, dstH, angleDegrees,
                    centerX, centerY, flip, colorModARGB);
        }

        @Override
        public void fillRect(float x, float y, float width, float height, int colorARGB) {
            mDrawCalls++;
            mFills++;
            mDelegate.fillRect(x, y, width, height, colorARGB);
        }

        @Override
        public void drawLine(float x1, float y1, float x2, float y2, int colorARGB) {
            mDrawCalls++;
            mLines++;
            mDelegate.drawLine(x1, y1, x2, y2, colorARGB);
        }

        @Override
        public void drawTriangles(long texture, float[] positionsUv, int[] colors, int vertexCount, int[] indices,
                int indexCount, BlendMode mode) {
            mDrawCalls++;
            mTriangles++;
            mDelegate.drawTriangles(texture, positionsUv, colors, vertexCount, indices, indexCount, mode);
        }

        @Override
        public void setClipRect(int x, int y, int width, int height) {
            mDelegate.setClipRect(x, y, width, height);
        }

        @Override
        public void clearClipRect() {
            mDelegate.clearClipRect();
        }

        @Override
        public void setBlendMode(BlendMode mode) {
            mDelegate.setBlendMode(mode);
        }

        @Override
        public void setRenderTarget(long texture) {
            mDelegate.setRenderTarget(texture);
        }

        @Override
        public void clear(int colorARGB) {
            mDelegate.clear(colorARGB);
        }

        @Override
        public void readTexturePixels(long texture, int width, int height, int[] dest) {
            mDelegate.readTexturePixels(texture, width, height, dest);
        }

        @Override
        public void compositeToOutput() {
            mDelegate.compositeToOutput();
        }

        @Override
        public void present() {
            mDelegate.present();
        }

        @Override
        public void setLogicalSize(int width, int height) {
            mDelegate.setLogicalSize(width, height);
        }

        @Override
        public int renderOutputWidth() {
            return mDelegate.renderOutputWidth();
        }

        @Override
        public int renderOutputHeight() {
            return mDelegate.renderOutputHeight();
        }
    }

    /** The app, with the draw-call counter installed right after the framework built the renderer. */
    static final class StressApp extends LawnApp {
        private final boolean mWantCounting;
        CountingRenderer mCountingRenderer;

        StressApp(boolean theWantCounting) {
            mWantCounting = theWantCounting;
        }

        @Override
        public void init() {
            super.init();
            if (!mWantCounting || mRendererBackend == null) {
                return;
            }
            mCountingRenderer = new CountingRenderer(mRendererBackend);
            mRendererBackend = mCountingRenderer;
            if (mWidgetManager != null && mWidgetManager.mImage instanceof GLImage aScreen
                    && aScreen.mRendererBackend != null) {
                // The screen image resolved its backend before this wrap (SexyAppBase.makeWindow);
                // clearing it makes every screen draw resolve through the app's field, i.e. through
                // the counter.
                aScreen.mRendererBackend = null;
            }
            System.out.println("[stress] draw counting installed over "
                    + mCountingRenderer.mDelegate.getClass().getSimpleName());
        }
    }
}
