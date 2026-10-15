package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * Real-asset integration checks for the screens that register their own delay-load resource groups and
 * for the screen-mode switch.
 *
 * <p>Four screens ({@code AwardScreen}, {@code AlmanacDialog}, {@code StoreScreen},
 * {@code ChallengeScreen}) built the group list but never loaded it, so every image they draw was null -
 * the main menu's Help button (an award screen showing the zombie note) crashed with an NPE. The check
 * below is generic: after the screen is constructed, every image of every group it registered must be
 * bound.
 */
class ScreenResourceLoadingTest {

    /** Boots the real game once (asset-gated like {@code GameBootSmokeTest}). */
    private static LawnApp boot(Path saveDir, Path resourceRoot) throws IOException, InterruptedException {
        // The entry point installs the resource extractor (upstream main.cpp); without it a group load
        // marks the group loaded but binds nothing, which is what made every delay-load screen draw null.
        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        app.setArgs(new String[] {
            "pvz-portable", "-resdir", resourceRoot.toString(), "-savedir", saveDir.toString(),
        });
        app.init();
        app.startLoadingThread();
        long deadline = System.nanoTime() + 180_000_000_000L;
        while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            app.updateFrames();
            Thread.sleep(2);
        }
        assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");
        app.mPlayerInfo = app.mProfileMgr.addProfile("Screens");
        return app;
    }

    /**
     * Paints the screen into an offscreen image. The reported help crash was an NPE raised while drawing
     * the images the screen never loaded, so drawing is the check that actually covers it.
     */
    private static void assertPaints(String theScreen, cn.enaium.fabricpvz.portable.sexy.Widget theWidget) {
        var aTarget = new cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage(null);
        aTarget.create(800, 600);
        theWidget.resize(0, 0, 800, 600);
        theWidget.draw(new cn.enaium.fabricpvz.portable.sexy.graphics.Graphics(aTarget));
    }

    private static void assertGroupsBound(String theScreen, Iterable<String> theGroups) {
        for (String aGroup : theGroups) {
            int aUnbound = 0;
            String aFirst = null;
            for (Resources.ResourceEntry anEntry : Resources.ENTRIES) {
                if (anEntry.type() == Resources.ResourceType.IMAGE && aGroup.equals(anEntry.group())) {
                    if (Resources.getImageById(anEntry.id()) == null) {
                        aUnbound++;
                        if (aFirst == null) {
                            aFirst = anEntry.name();
                        }
                    }
                }
            }
            org.junit.jupiter.api.Assertions.assertEquals(0, aUnbound,
                    theScreen + " left " + aUnbound + " images of " + aGroup + " unbound (e.g. " + aFirst
                            + "): the screen registers that group but never loads it");
        }
    }

    @Test
    void screensLoadTheGroupsTheyRegister(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException, InterruptedException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        LawnApp app = boot(Files.createDirectories(tmp.resolve("userdata")), resourceRoot.get());
        try {
            // Help on the main menu: the award screen showing the zombie note.


            var anAward = new cn.enaium.fabricpvz.portable.lawn.widget.AwardScreen(app,
                    cn.enaium.fabricpvz.portable.lawn.AwardType.AWARD_HELP_ZOMBIENOTE, false);
            assertGroupsBound("AwardScreen(Help)", anAward.mLoadedResourceNames);
            assertPaints("AwardScreen(Help)", anAward);

            app.mGameScene = GameScenes.SCENE_AWARD;

            var anAlmanac = new cn.enaium.fabricpvz.portable.lawn.widget.AlmanacDialog(app);
            assertGroupsBound("AlmanacDialog", anAlmanac.mLoadedResourceNames);
            assertPaints("AlmanacDialog", anAlmanac);

            var aStore = new cn.enaium.fabricpvz.portable.lawn.widget.StoreScreen(app);
            assertGroupsBound("StoreScreen", aStore.mLoadedResourceNames);
            assertPaints("StoreScreen", aStore);

            var aChallenge = new cn.enaium.fabricpvz.portable.lawn.widget.ChallengeScreen(app, ChallengePage.CHALLENGE_PAGE_SURVIVAL);
            assertGroupsBound("ChallengeScreen", aChallenge.mLoadedResourceNames);
            assertPaints("ChallengeScreen", aChallenge);
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    /**
     * Toggling fullscreen must switch the existing window: re-initialising the platform threw
     * "platform already initialized" as soon as fullscreen was turned off again.
     */
    @Test
    void screenModeSwitchDoesNotReinitialiseThePlatform(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException, InterruptedException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        LawnApp app = boot(Files.createDirectories(tmp.resolve("userdata")), resourceRoot.get());
        try {
            // The initial mode comes from the persisted ScreenMode preference, so only the round trip is checked.
            boolean wasWindowed = app.mIsWindowed;
            assertNotNull(app.mPlatformBackend, "a platform backend must exist");

            app.switchScreenMode(!wasWindowed); // switch away from the initial mode
            assertNotEquals(wasWindowed, app.mIsWindowed);
            app.switchScreenMode(wasWindowed); // back: this used to throw "platform already initialized"
            assertEquals(wasWindowed, app.mIsWindowed);
            app.switchScreenMode(!wasWindowed);
            app.switchScreenMode(wasWindowed);
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
