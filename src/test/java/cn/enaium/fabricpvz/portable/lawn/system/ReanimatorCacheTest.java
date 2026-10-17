package cn.enaium.fabricpvz.portable.lawn.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import cn.enaium.fabricpvz.portable.lawn.DrawVariation;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorDefinition;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Verifies what {@link ReanimatorCache#makeCachedPlantFrame} draws for a registered seed type: that
 * cached frame is the picture a seed packet, the almanac entry and the cursor preview show, and the
 * peashooter family keeps its head on a separate {@code anim_head_idle} layer of the body reanimation.
 *
 * <p>Upstream draws that second pass for a written-out list of seed types (its own pea shooters), which
 * is why a plant registered with the same reanimation used to appear without a head in all three places
 * - the rule is the reanimation, so it is read from the reanimation ({@code docs/VERIFICATION.md} §48).
 * The test renders the frame of a registered seed type through the real retail {@code .reanim} data and
 * compares it with the retail peashooter's, plus a control that shows the comparison is sensitive to
 * which reanimation the definition names.
 *
 * <p>Assumption-gated on the retail data ({@code -Dpvz.test.resources=<dir>} or a discovered Steam
 * install), like the other asset tests.
 */
class ReanimatorCacheTest {

    /** A seed type above the retail range, i.e. one a registrant can define (SeedType.MAX_SEED_TYPES). */
    private static final int TEST_SEED_TYPE = 100;
    /** Second registered seed type, for the control frame. */
    private static final int TEST_SEED_TYPE_2 = 101;

    @Test
    void aRegisteredPlantOfThePeaFamilyCachesTheSameFrameAsTheRetailPeashooter() throws Exception {
        Optional<Path> aRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(aRoot.isPresent(), "no real PvZ assets available");
        Optional<Path> aPakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(aPakPath.isPresent(), "no main.pak in the resource root");

        PakInterface aPakInterface = PakInterface.getInstance();
        aPakInterface.setResourceFolder(aRoot.get().toString());
        Assumptions.assumeTrue(aPakInterface.addPakFile(aPakPath.get()), "real main.pak must mount");

        // The reanimation's tracks and cels come out of the mounted pak; every cel is one stub image per
        // name, so the frames below are compared on the drawing work the cache does (how many passes it
        // makes, where it places them) rather than on retail pixels.
        Map<String, Image> anImagesByName = new HashMap<>();
        Set<Image> aCreatedImages = Collections.newSetFromMap(new IdentityHashMap<>());
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

        String anOldAppDataFolder = Common.getAppDataFolder();
        Path aCacheFolder = Files.createTempDirectory("pvz-reanim-cache");
        Common.setAppDataFolder(aCacheFolder.toString());
        LawnApp anApp = new LawnApp();
        try {
            HeadlessTestApp.initialize(anApp);
            assertNotNull(anApp.mReanimatorCache, "the fixture gives the app the cache LawnApp::Init creates");

            // The frames below load the two definitions through this test's stub resolver, and a loaded
            // definition is process-global: remember what was already there and drop only what this
            // test loaded, so no other test draws a sunflower through these stubs afterwards.
            boolean aPeashooterWasLoaded =
                    Reanimator.gReanimatorDefArray[ReanimationType.REANIM_PEASHOOTER] != null;
            boolean aSunflowerWasLoaded = Reanimator.gReanimatorDefArray[ReanimationType.REANIM_SUNFLOWER] != null;

            // A registered plant drawn with the peashooter's own reanimation, and a control registered
            // with one that has no head layer.
            Plant.gPlantDefs[TEST_SEED_TYPE] = new PlantDefinition(TEST_SEED_TYPE, null,
                    ReanimationType.REANIM_PEASHOOTER, 0, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "TEST_PEA");
            Plant.gPlantDefs[TEST_SEED_TYPE_2] = new PlantDefinition(TEST_SEED_TYPE_2, null,
                    ReanimationType.REANIM_SUNFLOWER, 0, 50, 750, PlantSubClass.SUBCLASS_NORMAL, 2500, "TEST_SUN");

            MemoryImage aRetail = anApp.mReanimatorCache.makeCachedPlantFrame(SeedType.SEED_PEASHOOTER,
                    DrawVariation.VARIATION_NORMAL);
            MemoryImage aRegistered = anApp.mReanimatorCache.makeCachedPlantFrame(TEST_SEED_TYPE,
                    DrawVariation.VARIATION_NORMAL);
            MemoryImage aControl = anApp.mReanimatorCache.makeCachedPlantFrame(TEST_SEED_TYPE_2,
                    DrawVariation.VARIATION_NORMAL);

            assertNotNull(aRetail);
            assertNotNull(aRegistered);
            assertNotNull(aControl);
            assertTrue(opaquePixels(aRetail) > 0, "the retail peashooter's frame must have art in it");

            // The control: a different reanimation renders differently, so an equal pair below means the
            // passes matched and not that the frame is empty.
            assertFalse(sameFrame(aRetail, aControl),
                    "a frame of another reanimation must differ from the peashooter's");

            assertEquals(aRetail.mWidth, aRegistered.mWidth, "the canvases must be the same size");
            assertEquals(aRetail.mHeight, aRegistered.mHeight, "the canvases must be the same size");
            assertArrayEquals(aRetail.getBits(), aRegistered.getBits(),
                    "a registered plant drawn with the peashooter's reanimation must cache the same frame, "
                            + "head layer included (the cursor preview and the almanac show this frame)");
            if (!aPeashooterWasLoaded) {
                releaseDefinition(ReanimationType.REANIM_PEASHOOTER);
            }
            if (!aSunflowerWasLoaded) {
                releaseDefinition(ReanimationType.REANIM_SUNFLOWER);
            }
        } finally {
            Plant.gPlantDefs[TEST_SEED_TYPE] = null;
            Plant.gPlantDefs[TEST_SEED_TYPE_2] = null;
            Common.setAppDataFolder(anOldAppDataFolder);
            Definition.gDefResourceResolver = null;
        }
    }

    /** Drops a definition this test loaded, so the next reader loads it again (with its own images). */
    private static void releaseDefinition(int theReanimationType) {
        ReanimatorDefinition aDefinition = Reanimator.gReanimatorDefArray[theReanimationType];
        if (aDefinition != null) {
            Reanimator.reanimationFreeDefinition(aDefinition);
            Reanimator.gReanimatorDefArray[theReanimationType] = null;
        }
    }

    /** @return whether two cached frames would draw the same thing */
    private static boolean sameFrame(MemoryImage theFirst, MemoryImage theSecond) {
        return theFirst.mWidth == theSecond.mWidth && theFirst.mHeight == theSecond.mHeight
                && java.util.Arrays.equals(theFirst.getBits(), theSecond.getBits());
    }

    /** @return how many pixels of a cached frame are not fully transparent */
    private static int opaquePixels(MemoryImage theFrame) {
        int aCount = 0;
        for (int aPixel : theFrame.getBits()) {
            if ((aPixel >>> 24) != 0) {
                aCount++;
            }
        }
        return aCount;
    }

    /** Caches one stub image per resource name, mirroring the app's shared-image map. */
    private static Image resolveImage(Map<String, Image> theImagesByName, String theName, Set<Image> theCreatedImages) {
        Image aCached = theImagesByName.get(theName);
        if (aCached != null) {
            return aCached;
        }

        MemoryImage anImage = new MemoryImage();
        anImage.create(48, 32);
        Image aStub = anImage;
        aStub.mFilePath = theName;
        theImagesByName.put(theName, aStub);
        theCreatedImages.add(aStub);
        return aStub;
    }
}
