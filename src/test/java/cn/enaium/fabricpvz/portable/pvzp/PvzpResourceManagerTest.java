/*
 * Tests for the resource-manager helpers the compiled definition cache depends on.
 */
package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.sexy.graphics.SharedImageRef;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * {@code PvpzFindImagePath} is what the compiled definition cache writes for every image member of a
 * definition.
 *
 * <p>A null member must never resolve to a name: the map is full of entries whose image references were
 * released ({@code get()} returns null), and matching one of those turned every empty image slot of
 * every reanimation definition into the background image's name. Reloading such a cache then drew the
 * background picture wherever a definition had no image - the "almost every texture is wrong" report.
 */
class PvzpResourceManagerTest {

    private LawnApp mApp;

    private PvzpResourceManager newManager() {
        mApp = new LawnApp();
        HeadlessTestApp.initialize(mApp);
        return new PvzpResourceManager(mApp);
    }

    private static MemoryImage newImage(LawnApp app, int width, int height) {
        MemoryImage anImage = new MemoryImage(app);
        anImage.create(width, height);
        return anImage;
    }

    @Test
    void nullImageHasNoPath() {
        PvzpResourceManager aManager = newManager();
        String[] aPath = {"unset"};

        assertFalse(aManager.findImagePath(null, aPath), "a null image must not resolve to a name");
        assertEquals("unset", aPath[0], "the output must stay untouched");
    }

    @Test
    void releasedEntryNeverMatchesANullQuery() {
        PvzpResourceManager aManager = newManager();
        MemoryImage anImage = newImage(mApp, 4, 4);
        aManager.addImageToMap(new SharedImageRef(anImage), "IMAGE_A");
        // A second entry that holds no image at all (its reference was released, like an unloaded
        // delay-load entry): it must not answer a null lookup either.
        aManager.addImageToMap(new SharedImageRef(), "IMAGE_BACKGROUND1");

        String[] aPath = {"unset"};
        assertFalse(aManager.findImagePath(null, aPath));
        assertTrue(aManager.findImagePath(anImage, aPath), "a live image still resolves");
        assertEquals("IMAGE_A", aPath[0]);
    }

    @Test
    void liveImagesResolveToTheirOwnName() {
        PvzpResourceManager aManager = newManager();
        MemoryImage aBackground = newImage(mApp, 8, 8);
        MemoryImage aSprout = newImage(mApp, 2, 3);
        aManager.addImageToMap(new SharedImageRef(aBackground), "IMAGE_BACKGROUND1");
        aManager.addImageToMap(new SharedImageRef(aSprout), "IMAGE_REANIM_SPROUT_BODY");

        String[] aPath = new String[1];
        assertTrue(aManager.findImagePath(aSprout, aPath));
        assertEquals("IMAGE_REANIM_SPROUT_BODY", aPath[0], "each image keeps its own name");
    }
}
