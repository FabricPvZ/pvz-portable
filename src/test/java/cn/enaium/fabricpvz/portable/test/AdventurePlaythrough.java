package cn.enaium.fabricpvz.portable.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.BoardResult;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantingReason;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;

/**
 * Plays Adventure mode end to end through the real window and the real input path: every action is a
 * synthetic mouse event pushed into SDL's queue, so it travels through {@code SdlPlatform.pollEvent}
 * into the widget tree exactly like a hardware click (see ../verification/VERIFICATION.md §14).
 *
 * <p>Two test-only assists keep a full run tractable. Both live in this file as a {@link LawnHooks}
 * listener registered at start-up - the game itself is untouched, so nothing has to be reverted:
 * zombies are kept at one body hit point ("instant kill") and the wave countdown is capped so each level
 * plays out in seconds of game time. The loop drives the framework's own update/draw entry points
 * without the vsync-bound present cadence, which runs the simulation roughly eight times faster than
 * real time.
 *
 * <p>Run through {@code ./gradlew playthrough -Ppvz.play.lastLevel=50}.
 */
public final class AdventurePlaythrough {

    /** Ticks between real draws (the simulation still advances every tick). */
    private static final int DRAW_EVERY = 10;

    private final LawnApp mApp;
    private final Path mDumpDir;
    private final boolean mFast;
    private final Stats mStats = new Stats();

    private int mTicks;
    private int mPlantingSeed = SeedType.SEED_NONE;
    private int mPlantedThisTick;
    private int mLevel;
    /** Game mode to start; adventure by default, any other mode for the coverage sweep. */
    private int mMode = GameMode.GAMEMODE_ADVENTURE;

    private AdventurePlaythrough(LawnApp theApp, Path theDumpDir, boolean theFast) {
        mApp = theApp;
        mDumpDir = theDumpDir;
        mFast = theFast;
    }

    /** Per-level and total counters, filled by the hook listener. */
    static final class Stats {
        int levelsWon;
        int levelsLost;
        int plantsPlaced;
        int zombiesSpawned;
        int zombiesKilled;
        int sunsClicked;
        int packetsClicked;
        final List<String> levelLines = new ArrayList<>();
    }

    /** Test-only assists and counters. Registered in-process; the game code is untouched. */
    private final class TestHooks implements LawnHooks {
        @Override
        public void onTick(Board theBoard, int theTick) {
            if (!mFast) {
                return;
            }
            for (Zombie aZombie : theBoard.mZombies) {
                if (!aZombie.mDead && aZombie.mBodyHealth > 1) {
                    // "Instant kill": one body hit point, so the next projectile finishes it.
                    aZombie.mBodyHealth = 1;
                    aZombie.mHelmHealth = 0;
                    aZombie.mShieldHealth = 0;
                    aZombie.mFlyingHealth = 0;
                }
            }
            if (theBoard.mZombieCountDown > 40) {
                theBoard.mZombieCountDown = 40; // waves arrive back to back
            }
        }

        @Override
        public boolean onZombieSpawn(Board theBoard, int theZombieType, int theRow, boolean theFromWave) {
            mStats.zombiesSpawned++;
            return false;
        }

        @Override
        public void onZombieDeath(Board theBoard, Zombie theZombie) {
            mStats.zombiesKilled++;
        }

        @Override
        public void onPlantPlaced(Board theBoard, Plant thePlant, int theGridX, int theGridY) {
            mStats.plantsPlaced++;
            mPlantedThisTick++;
        }
    }

    public static void main(String[] args) throws Exception {
        int firstLevel = Integer.getInteger("pvz.play.firstLevel", 1);
        int lastLevel = Integer.getInteger("pvz.play.lastLevel", 50);
        int tickBudget = Integer.getInteger("pvz.play.tickBudget", 30_000);
        boolean fast = Boolean.parseBoolean(System.getProperty("pvz.play.fast", "true"));
        Path runDir = Path.of(System.getProperty("pvz.gameDir", ".")).toAbsolutePath();
        Path dumpDir = runDir.resolve("build").resolve("playthrough");
        Path saveDir = runDir.resolve("playthrough-userdata");
        Files.createDirectories(dumpDir);
        Files.createDirectories(saveDir);

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        AdventurePlaythrough aRun = new AdventurePlaythrough(app, dumpDir, fast);
        LawnHooksRegistry.add(aRun.new TestHooks());
        try {
            app.setArgs(new String[] {"pvz-portable", "-savedir", saveDir.toString()});
            app.init();
            app.startLoadingThread();
            waitForLoading(app);

            PlayerInfo aProfile = app.mProfileMgr.addProfile("Playthrough");
            app.mPlayerInfo = aProfile;
            System.out.println("[play] loaded; starting adventure levels " + firstLevel + ".." + lastLevel);

            if (System.getProperty("pvz.play.fromMenu", "true").equals("true")) {
                aRun.enterAdventureFromMenu();
            }

            int firstMode = Integer.getInteger("pvz.play.firstMode", -1);
            int lastMode = Integer.getInteger("pvz.play.lastMode", -1);
            if (firstMode >= 0) {
                aRun.sweepGameModes(firstMode, Math.max(firstMode, lastMode), tickBudget);
            } else {
                for (int aLevel = firstLevel; aLevel <= lastLevel; aLevel++) {
                    if (!aRun.playLevel(aLevel, tickBudget)) {
                        System.out.println("[play] level " + aLevel + " did not complete; stopping");
                        break;
                    }
                }
                aRun.printSummary(firstLevel, lastLevel);
            }
        } finally {
            LawnHooksRegistry.remove(LawnHooksRegistry.dispatcher());
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    private static void waitForLoading(LawnApp app) throws InterruptedException {
        long deadline = System.nanoTime() + 180_000_000_000L;
        // Draw as well as update: the title screen's state machine only advances once it has drawn
        // (TitleScreen checks mDrawnYet), and the loading thread is started from that state machine.
        while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            app.processDeferredMessages(false);
            app.doUpdateFrames();
            app.mWidgetManager.drawScreen();
            app.mRendererBackend.present();
            Thread.sleep(2);
        }
    }

    /**
     * Waits for the main menu and clicks "Adventure", which is how a player starts the run: the game then
     * opens level 1 with its own widget order, level intro and seed chooser.
     */
    private void enterAdventureFromMenu() {
        // The loading screen hands over to the title screen, which waits for the player to press
        // "CLICK TO START"; only then does the game show the main menu.
        for (int i = 0; i < 4000; i++) {
            var aTitle = mApp.mTitleScreen;
            if (aTitle != null && aTitle.mStartButton != null && !aTitle.mStartButton.mDisabled
                    && aTitle.mStartButton.mVisible) {
                int aX = aTitle.mStartButton.mX + aTitle.mStartButton.mWidth / 2;
                int aY = aTitle.mStartButton.mY + aTitle.mStartButton.mHeight / 2;
                System.out.println("[play] clicking CLICK-TO-START at " + aX + "," + aY);
                click(aX, aY);
                for (int j = 0; j < 240; j++) {
                    pump();
                }
                break;
            }
            pump();
        }
        for (int i = 0; i < 4000 && mApp.mGameSelector == null; i++) {
            pump();
        }
        for (int i = 0; i < 2000; i++) {
            var aSelector = mApp.mGameSelector;
            if (aSelector == null) {
                pump();
                continue;
            }
            for (var aWidget : aSelector.mWidgets) {
                if (aWidget instanceof cn.enaium.fabricpvz.portable.lawn.widget.NewLawnButton aButton
                        && aButton.mId == cn.enaium.fabricpvz.portable.lawn.widget.GameSelector.GameSelector_Adventure
                        && aButton.mVisible && !aButton.mDisabled) {
                    System.out.println("[play] clicking Adventure at " + (aButton.mX + aButton.mWidth / 2) + ","
                            + (aButton.mY + aButton.mHeight / 2));
                    click(aButton.mX + aButton.mWidth / 2, aButton.mY + aButton.mHeight / 2);
                    for (int j = 0; j < 60; j++) {
                        pump();
                    }
                    return;
                }
            }
            pump();
        }
        System.out.println("[play] could not find the Adventure button");
    }

    /**
     * One framework step: events, update, and every {@value #DRAW_EVERY}th tick a real draw + present.
     *
     * <p>The window keeps showing the game (and the frame is drawn through the real renderer), but the
     * simulation runs about ten times faster than the display: a full adventure run is 50 levels, and
     * updating without presenting is what makes that tractable.
     */
    private void pump() {
        mApp.processDeferredMessages(false);
        mApp.doUpdateFrames();
        if (mTicks % DRAW_EVERY == 0) {
            mApp.mWidgetManager.drawScreen();
            mApp.mRendererBackend.present();
        }
        mTicks++;
    }

    /** Starts one level (adventure level number or challenge game mode) and plays it with clicks. */
    private boolean playLevel(int theLevel, int theTickBudget) throws IOException {
        mLevel = theLevel;
        mPlantingSeed = SeedType.SEED_NONE;
        mApp.mPlayerInfo.mLevel = theLevel;
        mApp.mGameMode = mMode;
        // Deliberately no startPlaying() here: the game plays its own level intro, the seed chooser
        // ("Let's Rock") hands over to the board, and the level's delay-load group (background, graves,
        // fog) is loaded on the way - calling startPlaying() directly skips that and the board then draws
        // a null backdrop.
        mApp.preNewGame(mMode, false);

        int aStartPlants = mStats.plantsPlaced;
        int aStartZombies = mStats.zombiesSpawned;
        int aStartTicks = mTicks;
        int aSuns = mStats.sunsClicked;
        int aPackets = mStats.packetsClicked;

        int aTickInLevel = 0;
        boolean aWon = false;
        while (aTickInLevel < theTickBudget) {
            clickStep();
            pump();
            aTickInLevel++;
            if (mMode == GameMode.GAMEMODE_ADVENTURE && theLevel == 1 && (aTickInLevel % 200 == 0)
                    && System.getProperty("pvz.play.logClicks") != null) {
                var aChooser = mApp.mSeedChooserScreen;
                System.out.println("[play]   diag tick=" + aTickInLevel + " scene=" + sceneName(mApp.mGameScene)
                        + " active=" + mApp.mActive + " mouse=" + mApp.mWidgetManager.mLastMouseX + ","
                        + mApp.mWidgetManager.mLastMouseY + " chooser=" + (aChooser != null)
                        + " inFlight=" + (aChooser == null ? -1 : aChooser.mSeedsInFlight)
                        + " startDisabled=" + (aChooser == null || aChooser.mStartButton.mDisabled)
                        + " seedState(PEASHOOTER)=" + (aChooser == null ? -1
                                : aChooser.mChosenSeeds[SeedType.SEED_PEASHOOTER].mSeedState)
                        + " focused=" + (mApp.mWidgetManager.mFocusWidget == null ? "null"
                                : mApp.mWidgetManager.mFocusWidget.getClass().getSimpleName()));
            }
            if (mMode == GameMode.GAMEMODE_ADVENTURE && theLevel == 1 && aTickInLevel == 600) {
                screenshot("level1-midplay.png");
            }

            if (mApp.mBoardResult == BoardResult.BOARDRESULT_WON) {
                aWon = true;
                break;
            }
            if (mApp.mBoardResult == BoardResult.BOARDRESULT_LOST) {
                break;
            }
        }
        screenshot("level" + String.format("%02d", theLevel) + "-end.png");

        String aLine = String.format(
                "level %2d %s ticks=%d plants=%d zombies=%d/%d suns=%d packets=%d scene=%s",
                theLevel, aWon ? "WON " : "LOST", mTicks - aStartTicks, mStats.plantsPlaced - aStartPlants,
                mStats.zombiesKilled - aStartZombies, mStats.zombiesSpawned - aStartZombies,
                mStats.sunsClicked - aSuns, mStats.packetsClicked - aPackets,
                sceneName(mApp.mGameScene));
        System.out.println("[play] " + aLine);
        mStats.levelLines.add(aLine);
        if (aWon) {
            mStats.levelsWon++;
        } else {
            mStats.levelsLost++;
        }
        dismissEndScreens();
        return aWon;
    }

    /**
     * Starts every non-adventure game mode in {@code [firstMode, lastMode]} and plays it with the same
     * click logic as an adventure level. One mode failing (an exception, or never producing a result)
     * does not stop the sweep - the point is a coverage report, not a win.
     */
    private void sweepGameModes(int firstMode, int lastMode, int theTickBudget) throws IOException {
        int aModes = 0;
        int aFailed = 0;
        for (int aMode = firstMode; aMode <= lastMode; aMode++) {
            if (aMode == GameMode.GAMEMODE_ADVENTURE || aMode == GameMode.GAMEMODE_INTRO
                    || aMode == GameMode.GAMEMODE_UPSELL) {
                continue; // the adventure run and the two cinematics have their own coverage
            }
            aModes++;
            mMode = aMode;
            mLevel = aMode;
            String aName = gameModeName(aMode);
            try {
                boolean aWon = playLevel(aMode, theTickBudget);
                System.out.println("[mode] " + aName + " result=" + (aWon ? "WON" : "NO_RESULT")
                        + " boardResult=" + mApp.mBoardResult);
            } catch (Throwable t) {
                aFailed++;
                System.out.println("[mode] " + aName + " THREW " + t);
                t.printStackTrace(System.out);
                try {
                    mApp.killBoard();
                    mApp.mBoardResult = BoardResult.BOARDRESULT_NONE;
                } catch (Throwable ignored) {
                    // keep sweeping: the next mode starts from a fresh board anyway
                }
            }
        }
        System.out.println("[mode] swept " + aModes + " modes, " + aFailed + " threw");
    }

    /** The {@code GAMEMODE_*} constant name of a mode id, for readable reports. */
    private static String gameModeName(int theMode) {
        for (java.lang.reflect.Field aField : GameMode.class.getFields()) {
            try {
                if (aField.getType() == int.class && aField.getInt(null) == theMode) {
                    return aField.getName().replace("GAMEMODE_", "") + " (" + theMode + ")";
                }
            } catch (IllegalAccessException ignored) {
                // fall through to the numeric name
            }
        }
        return "MODE_" + theMode;
    }

    /** One click decision per tick: dialogs, seed chooser, sun collection, planting. */
    private void clickStep() {
        if (clickDialogIfAny()) {
            return;
        }

        SeedChooserScreen aChooser = mApp.mSeedChooserScreen;
        if (aChooser != null && mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            clickChooser(aChooser);
            return;
        }

        Board aBoard = mApp.mBoard;
        if (aBoard == null || mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            return;
        }

        if (clickSun(aBoard)) {
            return;
        }
        plantSomething(aBoard);
    }

    /** Clicks the primary button of whatever dialog is up (level complete, continue, error, ...). */
    private boolean clickDialogIfAny() {
        if (mApp.getDialogCount() <= 0) {
            return false;
        }
        for (var aWidget : mApp.mWidgetManager.mWidgets) {
            if (aWidget instanceof cn.enaium.fabricpvz.portable.sexy.Dialog aDialog && aDialog.mWidgets != null) {
                for (var aChild : aDialog.mWidgets) {
                    if (aChild instanceof ButtonWidget aButton && aButton.mVisible && !aButton.mDisabled
                            && mTicks % 4 == 0) {
                        click(aButton.mX + aButton.mWidth / 2, aButton.mY + aButton.mHeight / 2);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Picks seeds in the chooser until it will let us start, then presses "Let's Rock". */
    private void clickChooser(SeedChooserScreen theChooser) {
        if (mTicks % 4 != 0) {
            return;
        }
        // Not while packets are mid-flight: clicking then lands on whatever is underneath.
        if (theChooser.mSeedsInFlight > 0) {
            return;
        }
        if (System.getProperty("pvz.play.logClicks") != null && mClickLog < 6) {
            System.out.println("[play]   chooser state=" + theChooser.mChooseState + " inFlight="
                    + theChooser.mSeedsInFlight + " bank=" + theChooser.mBoard.mSeedBank.mNumPackets
                    + " startDisabled=" + theChooser.mStartButton.mDisabled
                    + " peashooterState=" + theChooser.mChosenSeeds[SeedType.SEED_PEASHOOTER].mSeedState
                    + " states(" + SeedType.SEED_PEASHOOTER + "/" + SeedType.SEED_SUNFLOWER + "/"
                    + SeedType.SEED_WALLNUT + "/" + SeedType.SEED_SNOWPEA + "/" + SeedType.SEED_POTATOMINE
                    + ")=" + theChooser.mChosenSeeds[SeedType.SEED_PEASHOOTER].mSeedState + ","
                    + theChooser.mChosenSeeds[SeedType.SEED_SUNFLOWER].mSeedState + ","
                    + theChooser.mChosenSeeds[SeedType.SEED_WALLNUT].mSeedState + ","
                    + theChooser.mChosenSeeds[SeedType.SEED_SNOWPEA].mSeedState + ","
                    + theChooser.mChosenSeeds[SeedType.SEED_POTATOMINE].mSeedState
                    + " pos=" + theChooser.mChosenSeeds[SeedType.SEED_SUNFLOWER].mX + ","
                    + theChooser.mChosenSeeds[SeedType.SEED_SUNFLOWER].mY
                    + " chooserRect=" + theChooser.mX + "," + theChooser.mY + " " + theChooser.mWidth + "x"
                    + theChooser.mHeight + " tree=" + mApp.mWidgetManager.mWidgets.size()
                    + " cutTime=" + (mApp.mBoard == null || mApp.mBoard.mCutScene == null ? -1
                            : mApp.mBoard.mCutScene.mCutsceneTime)
                    + " daveState=" + mApp.mCrazyDaveState
                    + " daveTime=" + (mApp.mBoard == null || mApp.mBoard.mCutScene == null ? -1
                            : mApp.mBoard.mCutScene.mCrazyDaveTime)
                    + " sounds=" + (mApp.mSoundSystem == null ? "null" : "ok")
                    + " music=" + (mApp.mMusic == null ? "null" : "ok"));
        }

        int[] aOrder = {
            SeedType.SEED_PEASHOOTER, SeedType.SEED_SUNFLOWER, SeedType.SEED_WALLNUT,
            SeedType.SEED_SNOWPEA, SeedType.SEED_REPEATER, SeedType.SEED_POTATOMINE,
            SeedType.SEED_CHERRYBOMB, SeedType.SEED_SQUASH
        };
        if (theChooser.mStartButton.mDisabled) {
            for (int aSeed : aOrder) {
                var aChosenSeed = theChooser.mChosenSeeds[aSeed];
                if (aChosenSeed == null || aChosenSeed.mSeedState != cn.enaium.fabricpvz.portable.lawn.ChosenSeedState.SEED_IN_CHOOSER) {
                    continue;
                }
                // mChosenSeeds[...].mX/mY are the packet's settled position once the chooser is at rest
                // (getSeedPositionInChooser maps a slot index, not a seed type, and returned the first
                // slot for every seed).
                // The chooser positions its packets in its own coordinate space, and the widget manager
                // delivers clicks to a widget in that space: add the chooser's origin to click the packet.
                int aX = theChooser.mX + aChosenSeed.mX + 25;
                int aY = theChooser.mY + aChosenSeed.mY + 35;
                if (aX < 0 || aX > 800 || aY < 0 || aY > 600) {
                    continue;
                }
                if (System.getProperty("pvz.play.logClicks") != null && mClickLog < 12) {
                    System.out.println("[play]   picking seed " + aSeed + " at screen " + aX + "," + aY
                            + " (chooser origin " + theChooser.mX + "," + theChooser.mY + ")");
                }
                click(aX, aY);
                mStats.packetsClicked++;
                return;
            }
        }

        cn.enaium.fabricpvz.portable.lawn.widget.GameButton aStart = theChooser.mStartButton;
        if (aStart != null && !aStart.mDisabled) {
            click(aStart.mX + aStart.mWidth / 2, aStart.mY + aStart.mHeight / 2);
        }
    }

    /** Clicks the first sun that has settled. */
    private boolean clickSun(Board theBoard) {
        for (Coin aCoin : theBoard.mCoins) {
            if (aCoin.mType == CoinType.COIN_SUN || aCoin.mType == CoinType.COIN_SMALLSUN) {
                if (aCoin.mPosY > 70f) {
                    click((int) aCoin.mPosX + 20, (int) aCoin.mPosY + 20);
                    mStats.sunsClicked++;
                    return true;
                }
            }
        }
        return false;
    }

    /** Plants: pick a seed from the bank when the cursor is empty, otherwise drop it on a legal square. */
    private void plantSomething(Board theBoard) {
        if (mTicks % 3 != 0) {
            return;
        }
        if (mPlantingSeed != SeedType.SEED_NONE) {
            for (int aRow = 0; aRow < 6; aRow++) { // 5 lawn rows plus the pool/roof row; canPlantAt filters
                for (int aCol = 0; aCol < 9; aCol++) {
                    if (theBoard.canPlantAt(aCol, aRow, mPlantingSeed) == PlantingReason.PLANTING_OK) {
                        int aX = theBoard.gridToPixelX(aCol, aRow) + 40;
                        int aY = theBoard.gridToPixelY(aCol, aRow) + 50;
                        mPlantedThisTick = 0;
                        click(aX, aY);
                        if (mPlantedThisTick > 0) {
                            mPlantingSeed = SeedType.SEED_NONE;
                        }
                        return;
                    }
                }
            }
            // Nothing legal: clear the cursor so the next tick picks a different seed.
            mPlantingSeed = SeedType.SEED_NONE;
            return;
        }

        int[] aOrder = {
            SeedType.SEED_PEASHOOTER, SeedType.SEED_SUNFLOWER, SeedType.SEED_WALLNUT,
            SeedType.SEED_SNOWPEA, SeedType.SEED_REPEATER, SeedType.SEED_LILYPAD,
            SeedType.SEED_CHERRYBOMB
        };
        for (int aSeed : aOrder) {
            if (Plant.getCost(aSeed, SeedType.SEED_NONE) > theBoard.mSunMoney) {
                continue;
            }
            var aBank = theBoard.mSeedBank;
            if (aBank == null) {
                return;
            }
            for (int i = 0; i < aBank.mSeedPackets.length; i++) {
                var aPacket = aBank.mSeedPackets[i];
                if (aPacket == null || aPacket.mPacketType != aSeed) {
                    continue;
                }
                click(aBank.mX + theBoard.getSeedPacketPositionX(i) + 25, aBank.mY + 30);
                mPlantingSeed = aSeed;
                return;
            }
        }
    }

    /** Clicks through the award / level-complete screens so the next level can start. */
    private void dismissEndScreens() throws IOException {
        for (int i = 0; i < 400; i++) {
            if (mApp.mBoardResult == BoardResult.BOARDRESULT_NONE && mApp.getDialogCount() <= 0) {
                return;
            }
            clickStep();
            mApp.processDeferredMessages(false);
            mApp.doUpdateFrames();
            mApp.mWidgetManager.drawScreen();
            mApp.mRendererBackend.present();
            if (i == 200) {
                screenshot("level" + String.format("%02d", mLevel) + "-award.png");
            }
        }
    }

    private void printSummary(int theFirstLevel, int theLastLevel) {
        System.out.println("[play] === summary ===");
        System.out.println("[play] levels won=" + mStats.levelsWon + " lost=" + mStats.levelsLost
                + " plants=" + mStats.plantsPlaced + " zombies=" + mStats.zombiesKilled + "/"
                + mStats.zombiesSpawned + " suns=" + mStats.sunsClicked + " packets=" + mStats.packetsClicked);
        System.out.println("[play] requested levels " + theFirstLevel + ".." + theLastLevel
                + "; profile level now " + (mApp.mPlayerInfo == null ? -1 : mApp.mPlayerInfo.mLevel)
                + "; finishedAdventure=" + (mApp.mPlayerInfo != null && mApp.mPlayerInfo.mFinishedAdventure != 0));
    }

    /**
     * Clicks at a point through the same entry the platform's event dispatch and the port's demo playback
     * use ({@code WidgetManager.mouseMove/mouseDown/mouseUp}), so the click travels the game's real
     * handling: mouse remap, widget hit test, the widget's mouse callbacks and the game logic behind them.
     *
     * <p>Synthetic SDL events were tried first: {@code SDL_PushEvent} reported success but the platform's
     * {@code SDL_PollEvent} never returned the event in this environment, so the queue route is not usable
     * here.
     */
    private int mClickLog;

    private void click(int theX, int theY) {
        if (mClickLog < 40 && System.getProperty("pvz.play.logClicks") != null) {
            mClickLog++;
            System.out.println("[play]   click #" + mClickLog + " at " + theX + "," + theY
                    + " stack=" + new Throwable().getStackTrace()[1].getMethodName());
        }
        mApp.mWidgetManager.mouseMove(theX, theY);
        mApp.mWidgetManager.mouseDown(theX, theY, 1);
        mApp.mWidgetManager.mouseUp(theX, theY, 1);
    }

    private void screenshot(String theName) throws IOException {
        int aWidth = mApp.mRendererBackend.renderOutputWidth();
        int aHeight = mApp.mRendererBackend.renderOutputHeight();
        int[] aPixels = new int[aWidth * aHeight];
        mApp.mRendererBackend.readTexturePixels(0, aWidth, aHeight, aPixels);
        var anImage = new java.awt.image.BufferedImage(aWidth, aHeight,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        anImage.setRGB(0, 0, aWidth, aHeight, aPixels, 0, aWidth);
        Files.createDirectories(mDumpDir);
        javax.imageio.ImageIO.write(anImage, "png", mDumpDir.resolve(theName).toFile());
    }

    private static String sceneName(int theScene) {
        return switch (theScene) {
            case GameScenes.SCENE_LOADING -> "LOADING";
            case GameScenes.SCENE_MENU -> "MENU";
            case GameScenes.SCENE_LEVEL_INTRO -> "LEVEL_INTRO";
            case GameScenes.SCENE_PLAYING -> "PLAYING";
            case GameScenes.SCENE_ZOMBIES_WON -> "ZOMBIES_WON";
            case GameScenes.SCENE_AWARD -> "AWARD";
            case GameScenes.SCENE_CREDIT -> "CREDIT";
            case GameScenes.SCENE_CHALLENGE -> "CHALLENGE";
            default -> "?" + theScene;
        };
    }
}
