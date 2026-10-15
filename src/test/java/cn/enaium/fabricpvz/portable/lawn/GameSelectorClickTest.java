package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.widget.GameSelector;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * The adventure map is created with the title screen but only shown once the player picks "Adventure",
 * and its three flower reanimations are freed whenever the level list is rebuilt. Upstream's
 * {@code ReanimationGet} returns the slot at the id's index, so the click handler simply touches an
 * inert object; the port's throwing lookup turned such a click - reachable from a UI script or a click
 * during the loading hand-off - into
 * {@code IllegalStateException: Failed: dataArrayGet(...) for reanims}.
 */
class GameSelectorClickTest {

    @Test
    void clickingTheMapWithoutItsFlowerReanimationsDoesNotCrash(
            @org.junit.jupiter.api.io.TempDir Path tmp) throws IOException, InterruptedException {
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
        app.mPlayerInfo = app.mProfileMgr.addProfile("Selector");

        try {
            app.showGameSelector();
            GameSelector aSelector = app.mGameSelector;
            assertNotNull(aSelector, "the adventure map must exist");

            // The state a map that was never drawn is in: its flower reanimations are already freed.
            // Free the slots outright (what a rebuilt level list leaves behind); the dead-but-still-
            // allocated state that reanimationDie() produces is not enough to make the id stale.
            for (int i = 0; i < 3; i++) {
                app.mEffectSystem.mReanimationHolder.mReanimations.dataArrayFree(
                        app.reanimationTryToGet(aSelector.mFlowerReanimID[i]));
            }

            assertDoesNotThrow(() -> aSelector.mouseDown(0, 0, 1),
                    "a click on a map without its flower reanimations must be ignored, not crash");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
