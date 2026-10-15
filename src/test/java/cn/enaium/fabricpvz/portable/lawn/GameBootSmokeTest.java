package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * End-to-end smoke test against the real retail assets: boots the application (resource discovery,
 * {@code main.pak} mount, {@code properties/resources.xml} manifest, image/font/audio loading, the
 * widget tree, the loading thread), starts an Adventure level and runs the frame loop.
 *
 * <p>Skipped automatically when no game data is available; runs in CI without a display because the
 * test task sets {@code SDL_VIDEODRIVER=dummy} and the renderer falls back to SDL's software backend.
 */
class GameBootSmokeTest {

    @TempDir
    Path tmp;

    private static void tick(LawnApp app, int frames) {
        for (int i = 0; i < frames; i++) {
            app.updateFrames();
        }
    }

    @Test
    void bootsAndRunsAdventureLevelOne() throws IOException, InterruptedException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        Path saveDir = Files.createDirectories(tmp.resolve("userdata"));
        // argv[0] is the program name, exactly like the C++ entry point the parser expects.
        String[] args = {
            "pvz-portable", "-resdir", resourceRoot.get().toString(),
            "-savedir", saveDir.toString(),
        };

        // The entry point installs the resource extractor (upstream main.cpp); do the same here.
        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        try {
            app.setArgs(args);
            app.init();

            // start() would run the whole blocking main loop; drive its two pieces by hand instead:
            // start the loading thread (as start() does) and pump frames ourselves.
            app.startLoadingThread();

            // The loading thread decodes/upload every asset asynchronously; tick frames while waiting
            // (bounded by wall time, not by frame count).
            long deadline = System.nanoTime() + 180_000_000_000L;
            while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
                app.updateFrames();
                Thread.sleep(2);
            }
            assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");

            // A fresh save directory has no profiles (the retail game shows the new-user dialog here);
            // create one exactly like that dialog does and continue with it.
            cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo aProfile = app.mProfileMgr.addProfile("SmokeTest");
            app.mPlayerInfo = aProfile;

            // Start Adventure level 1 without touching saved progress.
            app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
            app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
            assertNotNull(app.mBoard, "starting a new adventure game must create a board");
            assertEquals(1, app.mBoard.mLevel, "a fresh profile starts at level 1");

            app.startPlaying();
            assertEquals(GameScenes.SCENE_PLAYING, app.mGameScene, "the board must be in playing state");

            tick(app, 600);

            Board board = app.mBoard;
            assertNotNull(board);
            assertTrue(board.mNumWaves > 0, "level 1 must define zombie waves");
            assertNotNull(board.mZombiesInWave, "wave composition must be initialised");
            assertTrue(board.mSunMoney >= 50, "the level must keep its starting sun: " + board.mSunMoney);
            assertNotNull(board.mCutScene, "the cut scene handler must exist");
            assertTrue(app.mWidgetManager.mWidgets.size() > 0, "the widget tree must not be empty");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
