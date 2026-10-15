package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.system.SaveGame;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * A board must be serializable in every state the game reaches.
 *
 * <p>Upstream keeps {@code mRowPickingArray} a C++ value array (memset in Board.cpp:1361), so it
 * always holds {@code MAX_GRID_SIZE_Y} live entries; {@code SaveGame} serializes all of them. A board
 * restored by {@code LawnApp::TryLoadGame} never runs {@code InitLevel}, so once the port left the
 * slots null the next save - pause, back to main menu, or the level-start autosave - threw
 * {@code NullPointerException: Cannot read field "mItem" because "aArray" is null}.
 */
class SaveBoardStateTest {

    /**
     * A save must survive a round trip through a board that has live particle systems: the restored
     * systems reference their emitters and particles by saved ids, and upstream's DataArrayGet falls
     * back to the slot at that index while the port used to throw
     * {@code Failed: dataArrayGet(...) for emitters/particles} - which aborted the save that the
     * Continue dialog's pause performs, leaving the loaded game paused with no sun and no zombies.
     */
    @Test
    void aBoardWithLiveParticlesSurvivesSaveLoadSave(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException, InterruptedException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        app.setArgs(new String[] {
            "pvz-portable", "-resdir", root.get().toString(),
            "-savedir", Files.createDirectories(tmp.resolve("userdata")).toString(),
        });
        app.init();
        app.startLoadingThread();
        long deadline = System.nanoTime() + 180_000_000_000L;
        while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            app.updateFrames();
            Thread.sleep(2);
        }
        assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");
        app.mPlayerInfo = app.mProfileMgr.addProfile("Particles");

        try {
            Path aSave = tmp.resolve("game1_0.v4");
            app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
            app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
            app.startPlaying();

            int aRenderOrder = Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0);
            // Effect 45 carries an emitter whose definition sets a cross-fade duration; asking it to
            // cross-fade allocates an extra emitter that is not part of the definition order, so a load
            // cannot recreate it by index - that is the id that used to make the save throw.
            var aSystem = app.addPvzpParticle(400.0f, 300.0f, aRenderOrder, 45);
            assertNotNull(aSystem, "the particle system must be created");
            aSystem.crossFade("FadeOut");

            assertTrue(SaveGame.lawnSaveGame(app.mBoard, aSave.toString()),
                    "a board with live particles must be serializable");

            app.makeNewBoard();
            assertTrue(app.mBoard.loadGame(aSave.toString()), "the save must load again");
            assertTrue(SaveGame.lawnSaveGame(app.mBoard, aSave.toString()),
                    "saving the restored board (the pause/Continue path) must work");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    @Test
    void aBoardCanBeSavedBeforeInitLevelAndAfterLoading(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException, InterruptedException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        app.setArgs(new String[] {
            "pvz-portable", "-resdir", root.get().toString(),
            "-savedir", Files.createDirectories(tmp.resolve("userdata")).toString(),
        });
        app.init();
        app.startLoadingThread();
        long deadline = System.nanoTime() + 180_000_000_000L;
        while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            app.updateFrames();
            Thread.sleep(2);
        }
        assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");
        app.mPlayerInfo = app.mProfileMgr.addProfile("SaveState");

        try {
            Path aSave = tmp.resolve("game1_0.v4");
            app.mGameMode = GameMode.GAMEMODE_ADVENTURE;

            // The row picking array drives which row a zombie spawns in; every slot must exist.
            app.makeNewBoard();
            for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                assertNotNull(app.mBoard.mRowPickingArray[i],
                        "mRowPickingArray[" + i + "] must be a live entry (upstream value array)");
            }

            // A board that has not run InitLevel yet (what TryLoadGame builds before loading).
            assertTrue(SaveGame.lawnSaveGame(app.mBoard, aSave.toString()),
                    "a fresh board must be serializable");

            app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
            app.startPlaying();
            assertTrue(SaveGame.lawnSaveGame(app.mBoard, aSave.toString()),
                    "a running level must be serializable");

            // ... and a board restored from that save (never runs InitLevel).
            app.makeNewBoard();
            assertTrue(app.mBoard.loadGame(aSave.toString()), "the save must load again");
            assertTrue(SaveGame.lawnSaveGame(app.mBoard, aSave.toString()),
                    "a loaded board must be serializable - this is the pause/quit save path");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
