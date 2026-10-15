package cn.enaium.fabricpvz.portable.pvzp.reanim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests of the runtime atlas packer and of the atlas branch of the draw path: single-cel images are
 * packed into a power-of-two-wide memory image (strips are skipped), the placement records are
 * retrievable by image, and a packed track draws that atlas sub-rect while
 * {@link Reanimation#getCurrentTrackImage(String)} reports no stable source image — the upstream
 * behaviour for atlas-encoded frames.
 */
class ReanimAtlasTest {
    private static final int TEST_TYPE = ReanimationType.REANIM_ZOMBIE;

    @BeforeAll
    static void installRegistry() {
        // REANIM_ZOMBIE ships with the atlas enabled (flags = 0).
        ReanimTestSupport.installSingleEntryRegistry(TEST_TYPE, "reanim/Zombie.reanim", 0);
    }

    @AfterAll
    static void freeRegistry() {
        ReanimTestSupport.freeRegistry();
    }

    private static MemoryImage memoryImage(int theWidth, int theHeight) {
        MemoryImage aImage = new MemoryImage();
        aImage.create(theWidth, theHeight);
        return aImage;
    }

    @Test
    void packsSingleCelImagesAndSkipsStrips() {
        MemoryImage aBody = memoryImage(20, 10);
        MemoryImage aHead = memoryImage(30, 24);
        MemoryImage aStrip = memoryImage(60, 20);
        aStrip.mNumCols = 3;

        ReanimatorDefinition aDefinition = ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("t",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aBody),
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, aHead),
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 2.0f, 1.0f, aStrip)));

        ReanimAtlas aAtlas = new ReanimAtlas();
        aAtlas.reanimAtlasCreate(aDefinition);

        assertEquals(2, aAtlas.mImageArray.size());  // the 3-column strip is never packed
        assertTrue(aAtlas.findImage(aStrip) < 0);
        assertTrue(aAtlas.findImage(aBody) >= 0);
        assertTrue(aAtlas.findImage(aHead) >= 0);
        assertNull(aAtlas.getEncodedReanimAtlas(aStrip));
        assertNull(aAtlas.getEncodedReanimAtlas(null));

        // Width rules: a power of two, at least the widest image plus its spacing.
        assertNotNull(aAtlas.mMemoryImage);
        int aWidth = aAtlas.mMemoryImage.mWidth;
        assertEquals(0, aWidth & (aWidth - 1));
        assertTrue(aWidth >= 32);

        // Both placements are inside the atlas and never overlap each other.
        ReanimAtlasImage aPlacedBody = aAtlas.getEncodedReanimAtlas(aBody);
        ReanimAtlasImage aPlacedHead = aAtlas.getEncodedReanimAtlas(aHead);
        assertNotNull(aPlacedBody);
        assertNotNull(aPlacedHead);
        assertSame(aBody, aPlacedBody.mOriginalImage);
        assertEquals(20, aPlacedBody.mWidth);
        assertEquals(10, aPlacedBody.mHeight);
        assertEquals(30, aPlacedHead.mWidth);
        assertEquals(24, aPlacedHead.mHeight);
        Rect aBodyRect = new Rect(aPlacedBody.mX, aPlacedBody.mY, aPlacedBody.mWidth, aPlacedBody.mHeight);
        Rect aHeadRect = new Rect(aPlacedHead.mX, aPlacedHead.mY, aPlacedHead.mWidth, aPlacedHead.mHeight);
        assertTrue(aBodyRect.mX >= 1 && aBodyRect.mY >= 1);
        assertTrue(aBodyRect.mX + aBodyRect.mWidth <= aWidth);
        assertTrue(aHeadRect.mX + aHeadRect.mWidth <= aWidth);
        assertTrue(!aBodyRect.intersects(aHeadRect));
        assertTrue(aAtlas.mMemoryImage.mHeight >= Math.max(aBodyRect.mY + 10, aHeadRect.mY + 24));
        assertEquals(0, aAtlas.mMemoryImage.mHeight & (aAtlas.mMemoryImage.mHeight - 1));
    }

    @Test
    void drawsPackedTracksFromTheAtlas() {
        MemoryImage aBody = memoryImage(20, 10);
        ReanimatorDefinition aDefinition = ReanimTestSupport.definition(12.0f, ReanimTestSupport.track("t",
                ReanimTestSupport.transform(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, aBody)));
        ReanimAtlas aAtlas = new ReanimAtlas();
        aAtlas.reanimAtlasCreate(aDefinition);
        aDefinition.mReanimAtlas = aAtlas;

        Reanimator.gReanimatorDefArray[TEST_TYPE] = aDefinition;
        Reanimation aReanimation = new Reanimation();
        aReanimation.reanimationInitializeType(10.0f, 20.0f, TEST_TYPE);
        aReanimation.mLoopType = ReanimLoopType.REANIM_LOOP;

        // Atlas-packed frames have no stable source image (upstream returns nullptr as well).
        assertNull(aReanimation.getCurrentTrackImage("t"));

        ReanimTestSupport.RecordingImage aTarget = new ReanimTestSupport.RecordingImage(800, 600);
        assertTrue(aReanimation.drawTrack(new Graphics(aTarget), 0, Reanimation.RENDER_GROUP_NORMAL));
        assertEquals(1, aTarget.mBlits.size());

        ReanimTestSupport.Blit aBlit = aTarget.mBlits.get(0);
        assertSame(aAtlas.mMemoryImage, aBlit.mImage);  // drawn from the packed atlas image
        ReanimAtlasImage aPlaced = aAtlas.getEncodedReanimAtlas(aBody);
        assertEquals(aPlaced.mX, aBlit.mSrcRect.mX);
        assertEquals(aPlaced.mY, aBlit.mSrcRect.mY);
        assertEquals(aPlaced.mWidth, aBlit.mSrcRect.mWidth);
        assertEquals(aPlaced.mHeight, aBlit.mSrcRect.mHeight);

        // Pivot comes from the original image metrics (20x10 -> +10/+5) and the overlay carries
        // the reanimation's position.
        assertEquals(20.0f, aBlit.m02, 1e-3f);
        assertEquals(25.0f, aBlit.m12, 1e-3f);
    }
}
