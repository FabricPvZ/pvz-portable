package cn.enaium.fabricpvz.portable.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChosenSeedState;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.widget.ChosenSeed;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;
import cn.enaium.fabricpvz.portable.sexy.ButtonWidget;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;

/**
 * Dumps the level-award seed-packet pickup ("拾取种子包") transition frame by frame: the moment the
 * vanishing seed packet is clicked, which grows from scale 1.01 to 2.0 over 400 ticks while the
 * white level fade runs (the "slowly enlarging white-out"). Also probes the backend directly, so the
 * dump separates "the game asks for a filtered scale" from "the backend filters a scaled draw".
 *
 * <p>Run through {@code ./gradlew zoomDump} (real video driver; {@code -Ppvz.zoom.dumpDir=...} to
 * choose the output directory, {@code -Ppvz.zoom.resDir=...} to point at the assets).
 */
public final class AwardPickupDump {

    private final LawnApp mApp;
    private final Path mDumpDir;
    /** Tick counter for the whole run (same role as {@code AdventurePlaythrough.mTicks}). */
    private int mTicks;
    /** Present every Nth tick while driving (vsync-paced presents dominate a headless-ish run). */
    private int mPresentEvery = 8;
    private int mWidth;
    private int mHeight;

    private AwardPickupDump(LawnApp theApp, Path theDumpDir) {
        mApp = theApp;
        mDumpDir = theDumpDir;
    }

    public static void main(String[] args) throws Exception {
        Path runDir = Path.of(System.getProperty("pvz.gameDir", ".")).toAbsolutePath();
        Path dumpDir = Path.of(System.getProperty("pvz.zoom.dumpDir",
                runDir.resolve("build").resolve("zoom-filter-before").toString())).toAbsolutePath();
        Path saveDir = dumpDir.resolve("userdata");
        Files.createDirectories(saveDir);

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        AwardPickupDump aRun = new AwardPickupDump(app, dumpDir);
        try {
            List<String> anAppArgs = new ArrayList<>(List.of("pvz-portable", "-savedir", saveDir.toString()));
            String aResDir = System.getProperty("pvz.zoom.resDir", "");
            if (!aResDir.isBlank()) {
                anAppArgs.add("-resdir");
                anAppArgs.add(aResDir);
            }
            app.setArgs(anAppArgs.toArray(String[]::new));
            app.init();
            app.startLoadingThread();
            aRun.waitForLoading();
            System.out.println("[zoom] renderer=" + (app.mRendererBackend == null ? "null" : "ready")
                    + " logical=" + app.mRendererBackend.logicalWidth() + "x"
                    + app.mRendererBackend.logicalHeight()
                    + " output=" + app.mRendererBackend.renderOutputWidth() + "x"
                    + app.mRendererBackend.renderOutputHeight());

            aRun.probeScaledDrawFilter();
            aRun.playToAwardPickup();
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    // ------------------------------------------------------------------
    // Backend probe
    // ------------------------------------------------------------------

    /**
     * Draws a two-texel stripe texture scaled 32x with the filter the game requests for scaled draws
     * (linear) and reads the result back: a filtered draw interpolates across the texel step, a
     * nearest one repeats the two texels. This isolates the backend from the game's draw calls.
     */
    private void probeScaledDrawFilter() throws IOException {
        RendererBackend aBackend = mApp.mRendererBackend;
        int aSize = 128;
        // Red | Blue columns on a 2-texel-wide texture; the boundary at x=64 is the texel step that
        // must be interpolated when the draw is filtered and must stay hard when it is not.
        int aRed = 0xFFFF0000;
        int aBlue = 0xFF0000FF;
        long aLinear = aBackend.createTexture(2, 1, new int[] {aRed, aBlue});
        long aNearest = aBackend.createTexture(2, 1, new int[] {aRed, aBlue});
        long aTarget = aBackend.createRenderTargetTexture(aSize, aSize);
        int[] aPixels = new int[aSize * aSize];

        aBackend.setRenderTarget(aTarget);
        aBackend.clear(0xFF000000);
        aBackend.setBlendMode(RendererBackend.BlendMode.NONE);
        aBackend.setTextureFilter(aLinear, true);
        aBackend.drawTexture(aLinear, 0, 0, 2, 1, 0, 0, aSize, 32, 0, 0, 0,
                RendererBackend.FLIP_NONE, 0xFFFFFFFF);
        aBackend.setTextureFilter(aNearest, false);
        aBackend.drawTexture(aNearest, 0, 0, 2, 1, 0, 48, aSize, 32, 0, 0, 0,
                RendererBackend.FLIP_NONE, 0xFFFFFFFF);
        aBackend.readTexturePixels(aTarget, aSize, aSize, aPixels);
        aBackend.setRenderTarget(0);

        System.out.println("[probe] linear  y=16 " + scanline(aPixels, aSize, 16, 56, 73, 2));
        System.out.println("[probe] nearest y=64 " + scanline(aPixels, aSize, 64, 56, 73, 2));
        aBackend.destroyTexture(aLinear);
        aBackend.destroyTexture(aNearest);
        aBackend.destroyTexture(aTarget);
    }

    /** A run of ARGB words as hex, for quoting the texel step of a scaled draw. */
    private static String scanline(int[] thePixels, int theWidth, int theY, int theStartX, int theEndX, int theStep) {
        StringBuilder aText = new StringBuilder();
        for (int x = theStartX; x < theEndX; x += theStep) {
            aText.append(String.format("%08X", thePixels[theY * theWidth + x]));
            if (x + theStep < theEndX) {
                aText.append(' ');
            }
        }
        return aText.toString();
    }

    // ------------------------------------------------------------------
    // Game drive
    // ------------------------------------------------------------------

    private void waitForLoading() throws InterruptedException {
        long aDeadline = System.nanoTime() + 180_000_000_000L;
        while (!mApp.mLoadingThreadCompleted.get() && System.nanoTime() < aDeadline) {
            mApp.processDeferredMessages(false);
            mApp.doUpdateFrames();
            mApp.mWidgetManager.drawScreen();
            mApp.mRendererBackend.present();
            Thread.sleep(2);
        }
        mWidth = mApp.mRendererBackend.renderOutputWidth();
        mHeight = mApp.mRendererBackend.renderOutputHeight();
    }

    /**
     * Starts adventure level 1 through the real menu, cutscene and seed chooser. The title screen
     * must be dismissed exactly like a player does: it stays in the widget tree until its own state
     * machine calls {@code LawnApp::LoadingCompleted}, and until then it draws over the board.
     */
    private void playToAwardPickup() throws IOException, InterruptedException {
        PlayerInfo aProfile = mApp.mProfileMgr.addProfile("AwardPickupDump");
        mApp.mPlayerInfo = aProfile;
        aProfile.mLevel = 1;
        aProfile.mFinishedAdventure = 0;
        mApp.mGameMode = GameMode.GAMEMODE_ADVENTURE;

        // "CLICK TO START" on the title screen, then Adventure on the main menu (LawnApp::LoadingCompleted
        // is what removes the title screen widget).
        boolean aClickedStart = false;
        for (int i = 0; i < 3000; i++) {
            var aTitle = mApp.mTitleScreen;
            if (aTitle != null && aTitle.mStartButton != null && !aTitle.mStartButton.mDisabled
                    && aTitle.mStartButton.mVisible) {
                click(aTitle.mStartButton.mX + aTitle.mStartButton.mWidth / 2,
                        aTitle.mStartButton.mY + aTitle.mStartButton.mHeight / 2);
                aClickedStart = true;
                for (int j = 0; j < 240; j++) {
                    pump();
                }
                break;
            }
            pump();
        }
        System.out.println("[zoom] click-to-start=" + aClickedStart + " titleScreen=" + (mApp.mTitleScreen != null)
                + " selector=" + (mApp.mGameSelector != null) + " scene=" + sceneName(mApp.mGameScene));
        for (int i = 0; i < 4000 && mApp.mGameSelector == null; i++) {
            pump();
        }
        boolean aClickedAdventure = false;
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
                    click(aButton.mX + aButton.mWidth / 2, aButton.mY + aButton.mHeight / 2);
                    aClickedAdventure = true;
                    for (int j = 0; j < 60; j++) {
                        pump();
                    }
                    break;
                }
            }
            if (aClickedAdventure) {
                break;
            }
            pump();
        }
        System.out.println("[zoom] adventure clicked=" + aClickedAdventure
                + " scene=" + sceneName(mApp.mGameScene));

        int aBudget = 20_000;
        while (mTicks < aBudget && mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            clickDialogIfAny();
            SeedChooserScreen aChooser = mApp.mSeedChooserScreen;
            if (aChooser != null && mApp.mGameScene != GameScenes.SCENE_PLAYING) {
                clickChooser(aChooser);
            }
            pump();
        }
        System.out.println("[zoom] scene=" + sceneName(mApp.mGameScene) + " ticks=" + mTicks
                + " widgets=" + mApp.mWidgetManager.mWidgets.size()
                + " titleScreen=" + (mApp.mTitleScreen != null));
        if (mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            throw new IllegalStateException("level never started");
        }

        Board aBoard = mApp.mBoard;
        // No zombies: the award packet is the only thing that should draw over the lawn.
        aBoard.mZombieCountDown = 1_000_000;
        // The game's own award path (Challenge::SpawnLevelAward), which is what a won level runs.
        aBoard.mChallenge.spawnLevelAward(4, 2);
        Coin aCoin = null;
        for (Coin aCandidate : aBoard.mCoins) {
            if (aCandidate.mType == CoinType.COIN_FINAL_SEED_PACKET) {
                aCoin = aCandidate;
                break;
            }
        }
        if (aCoin == null) {
            throw new IllegalStateException("no award seed packet coin was spawned");
        }
        System.out.println("[zoom] award coin at " + aCoin.mPosX + "," + aCoin.mPosY
                + " scale=" + aCoin.mScale);

        // Let it fall and settle, then dump the resting packet (the "before" reference frame).
        for (int i = 0; i < 140; i++) {
            pumpFrame();
        }
        screenshot("collect-000-resting.png");
        System.out.println("[zoom] resting packet pos=" + aCoin.mPosX + "," + aCoin.mPosY
                + " scale=" + aCoin.mScale + " hitGround=" + aCoin.mHitGround
                + " row=" + packetScanline(aCoin));

        System.out.println("[zoom] before click scene=" + sceneName(mApp.mGameScene)
                + " result=" + mApp.mBoardResult + " levelComplete=" + aBoard.mLevelComplete
                + " fadeCounter=" + aBoard.mBoardFadeOutCounter);

        // The real pickup: the click a player makes on the packet (Board::MouseDown -> Coin::MouseDown).
        int aClickX = (int) aCoin.mPosX + aCoin.mWidth / 2;
        int aClickY = (int) aCoin.mPosY + aCoin.mHeight / 2;
        click(aClickX, aClickY);
        System.out.println("[zoom] after click: collecting=" + aCoin.mIsBeingCollected
                + " dialogs=" + mApp.getDialogCount() + " scene=" + sceneName(mApp.mGameScene)
                + " board=" + (mApp.mBoard != null));

        for (int i = 0; i < 420; i++) {
            pumpFrame();
            if (mApp.mBoard == null) {
                System.out.println("[zoom] board was killed at i=" + i + " scene=" + sceneName(mApp.mGameScene)
                        + " result=" + mApp.mBoardResult + " levelComplete=" + aBoard.mLevelComplete
                        + " fadeCounter=" + aBoard.mBoardFadeOutCounter);
                break;
            }
            if (i % 25 == 0) {
                String aName = String.format("collect-%03d-counter%03d.png", i, aCoin.mDisappearCounter);
                screenshot(aName);
                System.out.println("[zoom] " + aName + " scale=" + aCoin.mScale
                        + " pos=" + aCoin.mPosX + "," + aCoin.mPosY
                        + " fade=" + mApp.mBoard.mBoardFadeOutCounter
                        + " row=" + packetScanline(aCoin));
            }
            if (aCoin.mDead) {
                break;
            }
        }

        // The collected packet ends the level: the board fades out, dies, and the level-award screen
        // (the "you got a new plant!" screen, which draws the same packet at 2x) takes over.
        for (int i = 0; i < 900; i++) {
            pumpFrame();
            if (mApp.mGameScene == GameScenes.SCENE_AWARD && i > 200) {
                break;
            }
        }
        screenshot("award-screen.png");
        System.out.println("[zoom] award screen scene=" + sceneName(mApp.mGameScene)
                + " widgets=" + mApp.mWidgetManager.mWidgets.size());
    }

    /** A horizontal run of output pixels straight through the packet's middle, for the report. */
    private String packetScanline(Coin theCoin) {
        int[] aRect = mApp.mRendererBackend.logicalPresentationRect();
        int aLogicalWidth = mApp.mRendererBackend.logicalWidth();
        int aLogicalHeight = mApp.mRendererBackend.logicalHeight();
        int aCenterX = aRect[0] + ((int) theCoin.mPosX + theCoin.mWidth / 2) * aRect[2] / aLogicalWidth;
        int aCenterY = aRect[1] + ((int) theCoin.mPosY + theCoin.mHeight / 2) * aRect[3] / aLogicalHeight;
        // The packet doubles in size, so the column 20 output pixels right of the centre is inside the
        // packet for every scale this transition produces.
        int aStart = aCenterX + 20;
        if (aStart < 0 || aStart + 24 > mWidth || aCenterY < 0 || aCenterY >= mHeight) {
            return "out-of-frame center=" + aCenterX + "," + aCenterY;
        }
        int[] aPixels = new int[mWidth * mHeight];
        mApp.mRendererBackend.readTexturePixels(0, mWidth, mHeight, aPixels);
        return "y=" + aCenterY + " x" + aStart + ".." + (aStart + 23) + " "
                + scanline(aPixels, mWidth, aCenterY, aStart, aStart + 24, 1);
    }

    /**
     * One framework step: events, update, draw and (every {@link #mPresentEvery} ticks) present. The
     * window's vsync paces {@code present}, and the menu/chooser state machines only need the draws.
     */
    private void pump() {
        mApp.processDeferredMessages(false);
        mApp.doUpdateFrames();
        mApp.mWidgetManager.drawScreen();
        if (mTicks % mPresentEvery == 0) {
            mApp.mRendererBackend.present();
        }
        mTicks++;
    }

    /** Draws and presents exactly one frame (used while dumping the transition). */
    private void pumpFrame() {
        int anOld = mPresentEvery;
        mPresentEvery = 1;
        pump();
        mPresentEvery = anOld;
    }

    /**
     * Clicks at a point in the widgets' own space (the renderer's logical canvas, 800x600). Derived
     * from the real input path: {@code SexyAppBase.dispatchPlatformEvent} remaps the window
     * coordinates to this space before handing them to {@code WidgetManager}, and the synthetic SDL
     * event queue is not usable here (see {@code AdventurePlaythrough.click}).
     */
    private void click(int theX, int theY) {
        mApp.mWidgetManager.mouseMove(theX, theY);
        mApp.mWidgetManager.mouseDown(theX, theY, 1);
        mApp.mWidgetManager.mouseUp(theX, theY, 1);
    }

    /** Clicks the primary button of whatever dialog is up (Crazy Dave's intro, level complete, ...). */
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
        if (mTicks % 4 != 0 || theChooser.mSeedsInFlight > 0) {
            return;
        }
        int[] aOrder = {
            SeedType.SEED_PEASHOOTER, SeedType.SEED_SUNFLOWER, SeedType.SEED_WALLNUT,
            SeedType.SEED_SNOWPEA, SeedType.SEED_REPEATER, SeedType.SEED_POTATOMINE,
            SeedType.SEED_CHERRYBOMB, SeedType.SEED_SQUASH
        };
        if (theChooser.mStartButton.mDisabled) {
            for (int aSeed : aOrder) {
                ChosenSeed aChosenSeed = theChooser.mChosenSeeds[aSeed];
                if (aChosenSeed == null || aChosenSeed.mSeedState != ChosenSeedState.SEED_IN_CHOOSER) {
                    continue;
                }
                click(theChooser.mX + aChosenSeed.mX + 25, theChooser.mY + aChosenSeed.mY + 35);
                return;
            }
        }
        if (!theChooser.mStartButton.mDisabled) {
            click(theChooser.mStartButton.mX + theChooser.mStartButton.mWidth / 2,
                    theChooser.mStartButton.mY + theChooser.mStartButton.mHeight / 2);
        }
    }

    private void screenshot(String theName) throws IOException {
        int[] aPixels = new int[mWidth * mHeight];
        mApp.mRendererBackend.readTexturePixels(0, mWidth, mHeight, aPixels);
        BufferedImage anImage = new BufferedImage(mWidth, mHeight, BufferedImage.TYPE_INT_ARGB);
        anImage.setRGB(0, 0, mWidth, mHeight, aPixels, 0, mWidth);
        Files.createDirectories(mDumpDir);
        ImageIO.write(anImage, "png", mDumpDir.resolve(theName).toFile());
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
