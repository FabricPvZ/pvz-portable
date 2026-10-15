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
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * The mixer is addressed by the id the loader handed out, not by the resource entry id: upstream
 * assigns {@code Sexy::SOUND_*} from {@code GetSoundThrow} (Resources.cpp:2195) and every call site
 * reads that value. The resource entry ids run past {@link SoundManager#MAX_SOURCE_SOUNDS} (the Crazy
 * Dave laugh is 404, the map roll-in 467), so passing an entry id straight to the sound manager made
 * exactly those sounds silent - the adventure roll-in and the level-start laugh among them.
 */
class SoundIdResolutionTest {

    /** Sounds the adventure flow plays right when the player enters the mode and starts a level. */
    private static final int[] ADVENTURE_SOUNDS = {
        Resources.SOUND_BUTTONCLICK_ID,
        Resources.SOUND_ROLL_IN_ID,
        Resources.SOUND_EVILLAUGH_ID,
        Resources.SOUND_TAP_ID,
    };

    @Test
    void adventureSoundsResolveToPlayableMixerIds(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException, InterruptedException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        app.setArgs(new String[] {
            "pvz-portable", "-resdir", resourceRoot.get().toString(),
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

        try {
            // The entry ids of the laugh and the roll-in sit past the mixer's source limit, which is
            // exactly why an untranslated id silently produced no sound at all.
            assertTrue(Resources.SOUND_EVILLAUGH_ID >= SoundManager.MAX_SOURCE_SOUNDS,
                    "the laugh's entry id is expected to exceed the mixer's source limit");
            assertTrue(Resources.SOUND_ROLL_IN_ID >= SoundManager.MAX_SOURCE_SOUNDS,
                    "the roll-in's entry id is expected to exceed the mixer's source limit");

            for (int anEntryId : ADVENTURE_SOUNDS) {
                String aName = null;
                for (Resources.ResourceEntry anEntry : Resources.ENTRIES) {
                    if (anEntry.id() == anEntryId) {
                        aName = anEntry.name();
                        break;
                    }
                }
                assertNotNull(aName, "entry " + anEntryId + " must be in the resource table");

                // -1 from the resource manager means "not loaded"; the manager hands out small ids
                // (the first is 0), which is why the entry id can never double as the mixer id.
                long aManagerId = app.mResourceManager.getSound(aName);
                assertTrue(aManagerId >= 0, aName + " must be loaded");
                assertEquals(aManagerId, Resources.getSoundById(anEntryId),
                        "the bound slot must hold the loaded mixer id - that is what the game reads");
                assertTrue(aManagerId < SoundManager.MAX_SOURCE_SOUNDS,
                        aName + " must resolve to a mixer id inside the source limit, got " + aManagerId);
                assertNotNull(app.mSoundManager.getSoundInstance(aManagerId),
                        "the mixer must hand out an instance for the resolved id of " + aName);

                // The game-level entry point takes the resource entry id and does the translation itself.
                app.mMuteSoundsForCutscene = false;
                app.playSample(anEntryId);
            }
        } finally {
            app.mSoundManager.stopAllSounds();
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
