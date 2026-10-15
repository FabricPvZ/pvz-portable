package cn.enaium.fabricpvz.portable.pvzp.reanim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Assumption-gated integration check against a real {@code reanim/*.reanim} asset from a locally
 * installed copy of the game. Runs only when the retail data is available
 * ({@code -Dpvz.test.resources=<dir>} or a discovered Steam install).
 *
 * <p>{@code reanim/LilyPad.reanim} (418 bytes) is used because its whole frame table fits in the
 * test: 12 fps, two 4-frame tracks {@code anim_idle} and {@code anim_blink}, with the documented
 * previous-frame inheritance and the {@code <f>-1</f>} blank frames.
 */
class ReanimatorAssetTest {

    @Test
    void loadsRealLilyPadFrameTableAndPlaysItHeadlessly() throws IOException {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "no real PvZ assets available");
        Optional<Path> aPakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(aPakPath.isPresent(), "no main.pak in the resource root");

        PakInterface aPakInterface = PakInterface.getInstance();
        aPakInterface.setResourceFolder(aRoot.get().toString());
        Assumptions.assumeTrue(aPakInterface.addPakFile(aPakPath.get()), "real main.pak must mount");

        // Images/fonts are resolved through the injectable Definition seam: every name maps to one
        // image the test owns (shared by name, like the app's image map), so the draw pass can only
        // touch images that exist.
        Set<Image> aCreatedImages = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Image> anImagesByName = new HashMap<>();
        Definition.gDefResourceResolver = new Definition.DefinitionResourceResolver() {
            @Override
            public Image loadImage(String theName) {
                return resolveImage(anImagesByName, theName, aCreatedImages);
            }

            @Override
            public Image getSharedImage(String thePathToTry) {
                return resolveImage(anImagesByName, thePathToTry, aCreatedImages);
            }
        };

        // Keep the compiled-definition cache out of the user's app-data folder, so the run is
        // hermetic and always parses the XML out of the mounted pak.
        String anOldAppDataFolder = Common.getAppDataFolder();
        Path aCacheFolder = Files.createTempDirectory("pvz-reanim-cache");
        Common.setAppDataFolder(aCacheFolder.toString());
        try {
            // Exercise the runtime's lazy-load path: the registry names the real file, the loader
            // parses it out of the mounted pak on demand (REANIM_LOADBAR_SPROUT keeps the atlas off).
            ReanimTestSupport.installSingleEntryRegistry(
                    ReanimationType.REANIM_LOADBAR_SPROUT, "reanim/LilyPad.reanim", 1);

            Reanimation aReanimation = new Reanimation();
            aReanimation.reanimationInitializeType(0.0f, 0.0f, ReanimationType.REANIM_LOADBAR_SPROUT);

            ReanimatorDefinition aDefinition = Reanimator.gReanimatorDefArray[ReanimationType.REANIM_LOADBAR_SPROUT];
            assertEquals(12.0f, aDefinition.mFPS, 1e-6f);
            ReanimatorTrack[] aTracks = Reanimator.tracksOf(aDefinition);
            assertEquals(2, aTracks.length);
            assertEquals("anim_idle", aTracks[0].mName);
            assertEquals("anim_blink", aTracks[1].mName);

            ReanimatorTransform[] anIdle = Reanimator.transformsOf(aTracks[0]);
            ReanimatorTransform[] aBlink = Reanimator.transformsOf(aTracks[1]);
            assertEquals(4, anIdle.length);
            assertEquals(4, aBlink.length);

            // anim_idle: frame 0 is the only visible frame and every later frame inherits its pose.
            assertEquals(-0.2f, anIdle[0].mTransX, 1e-4f);
            assertEquals(5.5f, anIdle[0].mTransY, 1e-4f);
            assertEquals(0.8f, anIdle[0].mScaleX, 1e-4f);
            assertEquals(0.8f, anIdle[0].mScaleY, 1e-4f);
            assertEquals(0.0f, anIdle[0].mFrame, 1e-6f);
            assertEquals(1.0f, anIdle[0].mAlpha, 1e-6f);
            assertNotNull(anIdle[0].mImage);
            for (int i = 1; i < 4; i++) {
                assertEquals(-1.0f, anIdle[i].mFrame, 1e-6f);
                assertEquals(-0.2f, anIdle[i].mTransX, 1e-4f);  // inherited from frame 0
                assertSame(anIdle[0].mImage, anIdle[i].mImage);  // inherited image
            }

            // anim_blink: blank first frame, then a 3-frame blink built from two alternating images.
            assertEquals(-1.0f, aBlink[0].mFrame, 1e-6f);
            assertEquals(0.0f, aBlink[0].mTransX, 1e-6f);
            assertEquals(1.0f, aBlink[0].mScaleX, 1e-6f);
            assertNull(aBlink[0].mImage);
            assertEquals(45.2f, aBlink[1].mTransX, 1e-4f);
            assertEquals(50.1f, aBlink[1].mTransY, 1e-4f);
            assertEquals(0.8f, aBlink[1].mScaleX, 1e-4f);
            assertEquals(0.0f, aBlink[1].mFrame, 1e-6f);
            assertNotNull(aBlink[1].mImage);
            assertEquals(0.0f, aBlink[2].mFrame, 1e-6f);  // inherited
            assertEquals(45.2f, aBlink[2].mTransX, 1e-4f);  // inherited
            assertNotSame(aBlink[1].mImage, aBlink[2].mImage);
            assertSame(aBlink[1].mImage, aBlink[3].mImage);

            // Play it: the LOADBAR_SPROUT type carries REANIM_NO_ATLAS, so the plain image path runs.
            assertEquals(4, aReanimation.mFrameCount);

            int[] aRange = new int[2];
            aReanimation.getFramesForLayer("anim_blink", aRange);
            assertEquals(1, aRange[0]);
            assertEquals(3, aRange[1]);

            ReanimTestSupport.RecordingImage aTarget = new ReanimTestSupport.RecordingImage(800, 600);
            Graphics aGraphics = new Graphics(aTarget);
            for (int i = 0; i < 40; i++) {
                aReanimation.update();
                aReanimation.draw(aGraphics);
            }

            assertFalse(aTarget.mBlits.isEmpty(), "the reanimation must draw its visible frames");
            for (ReanimTestSupport.Blit aBlit : aTarget.mBlits) {
                assertNotNull(aBlit.mImage, "a drawn frame must reference an existing image");
                assertTrue(aCreatedImages.contains(aBlit.mImage), "drew an image the resource layer never produced");
            }
        } finally {
            ReanimTestSupport.freeRegistry();
            Common.setAppDataFolder(anOldAppDataFolder);
            Definition.gDefResourceResolver = null;
        }
    }

    /** Caches one image per resource name, mirroring the app's shared-image map. */
    private static Image resolveImage(Map<String, Image> theImagesByName, String theName, Set<Image> theCreatedImages) {
        Image aCached = theImagesByName.get(theName);
        if (aCached != null) {
            return aCached;
        }
        Image anImage = ReanimTestSupport.image(48, 32);
        anImage.mFilePath = theName;
        theImagesByName.put(theName, anImage);
        theCreatedImages.add(anImage);
        return anImage;
    }
}
