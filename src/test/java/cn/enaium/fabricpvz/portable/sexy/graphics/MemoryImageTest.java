package cn.enaium.fabricpvz.portable.sexy.graphics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;

/**
 * Behaviour tests for the software rasterizer in {@link MemoryImage} (faithful port of
 * {@code Sexy::MemoryImage}; upstream src/SexyAppFramework/graphics/MemoryImage.cpp and
 * {@code inc_routines/*.inc}).
 *
 * <p>Every asserted pixel word below is hand-derived from the ported arithmetic and the
 * derivation is written next to the assertion. Where the port's integer expression differs from
 * the C++ source (for example the port uses {@code >>>} unsigned shifts and explicitly groups the
 * channel terms), the expectation follows the port.
 *
 * <p>Two behaviours that the port documents with {@code PORT-NOTE} comments are exercised on
 * purpose: the {@code fillScanLinesWithCoverage} "a == 0 leaves the destination pointer
 * unadvanced" quirk, and the {@code fastStretchBlt} colorized branch that draws nothing.
 */
class MemoryImageTest {

    private static final int CANARY = MemoryImage.MEMORYCHECK_ID; // 0x4BEEFADE

    /** Builds a {@code w}x{@code h} image from {@code w*h} pixels; commits the alpha flags. */
    private static MemoryImage fromPixels(int w, int h, int... pixels) {
        MemoryImage img = new MemoryImage();
        img.setBits(pixels, w, h, false);
        img.commitBits();
        return img;
    }

    /** A fully transparent (all zero) {@code w}x{@code h} image. */
    private static MemoryImage blank(int w, int h) {
        return fromPixels(w, h, new int[w * h]);
    }

    private static int pixel(MemoryImage img, int x, int y) {
        return img.getBits()[y * img.mWidth + x];
    }

    private static int[] row(MemoryImage img, int y) {
        int[] out = new int[img.mWidth];
        for (int x = 0; x < img.mWidth; x++) {
            out[x] = pixel(img, x, y);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 1. lifetime / canary / create
    // ------------------------------------------------------------------

    @Test
    void createAllocatesNothingUntilGetBitsAndBumpsChangedCount() {
        MemoryImage img = new MemoryImage();
        assertNull(img.mBits); // lazily allocated
        assertEquals(0, img.mBitsChangedCount);

        img.create(4, 3);
        assertEquals(4, img.mWidth);
        assertEquals(3, img.mHeight);
        // create() -> "All zeros --> trans + alpha"
        assertTrue(img.mHasTrans);
        assertTrue(img.mHasAlpha);
        assertEquals(1, img.mBitsChangedCount); // exactly one bitsChanged() from create()
        assertTrue(img.mBitsChanged);

        int[] bits = img.getBits();
        assertEquals(13, bits.length); // 4*3 + 1 canary
        assertEquals(CANARY, bits[12]);
        assertEquals(0, bits[0]);

        img.commitBits();
        // all-zero buffer: alpha 0 is present -> mHasTrans, never a partial alpha -> !mHasAlpha
        assertTrue(img.mHasTrans);
        assertFalse(img.mHasAlpha);
        assertFalse(img.mBitsChanged);
    }

    // ------------------------------------------------------------------
    // 2. setBits
    // ------------------------------------------------------------------

    @Test
    void setBitsCopiesWritesCanaryAndBumpsCount() {
        MemoryImage img = new MemoryImage();
        img.setBits(new int[] {0x11111111, 0x22222222, 0x33333333, 0x44444444}, 2, 2, false);
        assertEquals(2, img.mWidth);
        assertEquals(2, img.mHeight);
        assertEquals(5, img.mBits.length); // 2*2 + canary
        assertEquals(0x11111111, img.mBits[0]);
        assertEquals(0x44444444, img.mBits[3]);
        assertEquals(CANARY, img.mBits[4]);
        assertEquals(1, img.mBitsChangedCount);
        assertTrue(img.mBitsChanged);
    }

    @Test
    void setBitsReallocatesOnDimensionChangeAndCommitClearsChanged() {
        MemoryImage img = new MemoryImage();
        img.setBits(new int[] {0x11111111, 0x22222222, 0x33333333, 0x44444444}, 2, 2, false);
        assertEquals(1, img.mBitsChangedCount);

        // different dimensions -> new buffer (3*2+1 = 7 words)
        img.setBits(new int[] {0x01000000, 0x02000000, 0x03000000, 0x04000000, 0x05000000, 0x06000000}, 3, 2, true);
        assertEquals(3, img.mWidth);
        assertEquals(2, img.mHeight);
        assertEquals(7, img.mBits.length);
        assertEquals(CANARY, img.mBits[6]);
        assertEquals(2, img.mBitsChangedCount);
        assertFalse(img.mBitsChanged); // commitBits=true cleared it
        assertTrue(img.mHasAlpha); // alpha 0x01..0x06 are neither 0 nor 255
    }

    @Test
    void setBitsSameDimensionsReusesBufferAndSameReferenceIsNoOp() {
        MemoryImage img = new MemoryImage();
        img.setBits(new int[] {0x11111111, 0x22222222, 0x33333333, 0x44444444}, 2, 2, false);

        int[] before = img.mBits;
        img.setBits(new int[] {0x55555555, 0x66666666, 0x77777777, 0x88888888}, 2, 2, false);
        // same dimensions -> the existing buffer is reused
        assertSame(before, img.mBits);
        assertEquals(0x55555555, img.mBits[0]);
        assertEquals(2, img.mBitsChangedCount);

        int[] same = img.mBits;
        img.setBits(same, 2, 2, false);
        // theBits == mBits -> entire body is skipped
        assertEquals(2, img.mBitsChangedCount);
        assertTrue(img.mBitsChanged); // unchanged from the previous setBits
    }

    // ------------------------------------------------------------------
    // 3. fillRect
    // ------------------------------------------------------------------

    @Test
    void fillRectOpaqueWritesTheColorWord() {
        MemoryImage img = blank(3, 2);
        img.fillRect(new Rect(0, 0, 3, 2), new Color(0x112233, 255), Graphics.DRAWMODE_NORMAL);
        // alpha == 0xFF fast path: every covered word is simply theColor.toInt()
        assertEquals(0xFF112233, pixel(img, 0, 0));
        assertEquals(0xFF112233, pixel(img, 1, 0));
        assertEquals(0xFF112233, pixel(img, 2, 0));
        assertEquals(0xFF112233, pixel(img, 0, 1));
        assertEquals(0xFF112233, pixel(img, 1, 1));
        assertEquals(0xFF112233, pixel(img, 2, 1));
    }

    @Test
    void fillRectOpaquePartialLeavesOtherPixelsUntouched() {
        MemoryImage img = blank(3, 2);
        img.fillRect(new Rect(1, 0, 1, 1), new Color(0x112233, 255), Graphics.DRAWMODE_NORMAL);
        assertEquals(0xFF112233, pixel(img, 1, 0));
        assertEquals(0, pixel(img, 0, 0));
        assertEquals(0, pixel(img, 2, 0));
        assertEquals(0, pixel(img, 1, 1));
    }

    @Test
    void fillRectTranslucentBlendsWithDestination() {
        MemoryImage img = fromPixels(1, 1, 0xFF000000);
        // dest 0xFF000000: aDestAlpha = 255, aNewDestAlpha = 255 + (255-255)*128/255 = 255
        //   newAlpha = 255*128/255 = 128, oma = 256-128 = 128
        // red:   ((dest&0xFF0000)=0)*128 >>> 8 = 0 ; ((src&0xFF0000)=0xFF0000)*128 >>> 8 = 0x7F8000, &0xFF0000 = 0x7F0000 -> 0x7F
        // green/blue: dest and src bytes are 0 -> 0
        img.fillRect(new Rect(0, 0, 1, 1), new Color(255, 0, 0, 128), Graphics.DRAWMODE_NORMAL);
        assertEquals(0xFF7F0000, pixel(img, 0, 0));
    }

    // ------------------------------------------------------------------
    // 4. clear / clearRect
    // ------------------------------------------------------------------

    @Test
    void clearZeroesPixelsButKeepsCanary() {
        MemoryImage img = fromPixels(2, 2, 0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC);
        assertEquals(CANARY, img.mBits[4]);
        int before = img.mBitsChangedCount;

        img.clear();
        assertEquals(0, img.mBits[0]);
        assertEquals(0, img.mBits[3]);
        assertEquals(5, img.getBits().length); // 2*2 pixels + canary; always w*h+1 (SetBits:1077-1084 / GetBits:1109-1113)
        assertEquals(CANARY, img.getBits()[4]); // clear() only fills w*h words
        assertEquals(before + 1, img.mBitsChangedCount);
    }

    @Test
    void clearRectZeroesOnlyTheRegion() {
        MemoryImage img = fromPixels(3, 2, 0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233);
        int before = img.mBitsChangedCount;

        img.clearRect(new Rect(1, 0, 2, 1));
        // MemoryImage.cpp:1233-1235: ClearRect only zeroes the w words it walks, so pixel (0,0)
        // keeps the value the fixture painted (0xFF112233).
        assertEquals(0xFF112233, pixel(img, 0, 0));
        assertEquals(0, pixel(img, 1, 0));
        assertEquals(0, pixel(img, 2, 0));
        assertEquals(0xFF112233, pixel(img, 0, 1)); // untouched row
        assertEquals(CANARY, img.mBits[6]);
        assertEquals(before + 1, img.mBitsChangedCount);
    }

    // ------------------------------------------------------------------
    // 5. commitBits / setImageMode
    // ------------------------------------------------------------------

    @Test
    void commitBitsDetectsTransparentAlpha() {
        MemoryImage img = new MemoryImage();
        img.setBits(new int[] {0x00000000, 0xFF000000, 0x00112233, 0xFF445566}, 2, 2, false);
        img.commitBits();
        assertTrue(img.mHasTrans); // an alpha of 0 was seen
        assertFalse(img.mHasAlpha);
        assertFalse(img.mBitsChanged);
    }

    @Test
    void commitBitsDetectsPartialAlpha() {
        MemoryImage img = new MemoryImage();
        img.setBits(new int[] {0xC8000000, 0xFF000000, 0xFF000000, 0xFF000000}, 2, 2, false);
        img.commitBits();
        assertFalse(img.mHasTrans);
        assertTrue(img.mHasAlpha); // alpha 0xC8 is neither 0 nor 255
    }

    @Test
    void commitBitsAllOpaqueSetsNeitherFlag() {
        MemoryImage img = new MemoryImage();
        img.setBits(new int[] {0xFF000000, 0xFF112233, 0xFF445566, 0xFF778899}, 2, 2, false);
        img.commitBits();
        assertFalse(img.mHasTrans);
        assertFalse(img.mHasAlpha);
    }

    @Test
    void setImageModePinsFlagsAgainstCommitBits() {
        MemoryImage img = blank(2, 2);
        img.setImageMode(true, false); // pinned: trans, no alpha
        assertTrue(img.mForcedMode);
        assertTrue(img.mHasTrans);
        assertFalse(img.mHasAlpha);

        // mutate every pixel to opaque white, then re-commit: the pinned flags must survive
        img.fillRect(new Rect(0, 0, 2, 2), new Color(255, 255, 255, 255), Graphics.DRAWMODE_NORMAL);
        img.bitsChanged();
        img.commitBits();

        assertTrue(img.mForcedMode);
        assertTrue(img.mHasTrans); // unchanged although the buffer is now fully opaque
        assertFalse(img.mHasAlpha);
    }

    // ------------------------------------------------------------------
    // 6. lines
    // ------------------------------------------------------------------

    @Test
    void drawLineHorizontalTakesTheFillRectEarlyOut() {
        MemoryImage img = blank(6, 4);
        // startY == endY -> fillRect(Rect(0, 0, 5, 1)) -> opaque colour word
        img.drawLine(0, 0, 4, 0, new Color(0x112233, 255), Graphics.DRAWMODE_NORMAL);
        assertArrayEquals(new int[] {0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233, 0xFF112233, 0},
                row(img, 0));
        assertArrayEquals(new int[] {0, 0, 0, 0, 0, 0}, row(img, 1));
    }

    @Test
    void normalDrawLineWalksTheExpectedBresenhamPixels() {
        MemoryImage img = blank(6, 4);
        // dv = 2, dh = 4 -> mostly-horizontal; G = 2*dv-dh = 0
        //   start pixel (0,0); then G<=0 at x=1 -> (1,0); G>0 at x=2 -> (2,1);
        //   x=3 -> (3,1); x=4 -> (4,2). Total 5 pixels.
        img.drawLine(0, 0, 4, 2, new Color(0x112233, 255), Graphics.DRAWMODE_NORMAL);

        int changed = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 6; x++) {
                if (pixel(img, x, y) != 0) {
                    changed++;
                }
            }
        }
        assertEquals(5, changed);
        assertEquals(0xFF112233, pixel(img, 0, 0));
        assertEquals(0xFF112233, pixel(img, 1, 0));
        assertEquals(0xFF112233, pixel(img, 2, 1));
        assertEquals(0xFF112233, pixel(img, 3, 1));
        assertEquals(0xFF112233, pixel(img, 4, 2));
        // every other pixel is untouched
        assertEquals(0, pixel(img, 0, 1));
        assertEquals(0, pixel(img, 3, 0));
        assertEquals(0, pixel(img, 5, 2));
    }

    @Test
    void normalDrawLineTranslucentUsesLinearSrcPlusDestTimesOma() {
        MemoryImage img = blank(6, 4);
        // src word (see normalDrawLine translucent branch):
        //   ((((255*128)+0x80)>>>8)*0xFF0000 + 0x7F8000)>>>8 & 0xFF0000 = 0x7F0000 -> red 0x7F
        //   green/blue resolve to 0x7F as well -> src = 0xFF7F7F7F
        // oma = 256-128 = 128; dest is black, so src + 0 = 0xFF7F7F7F on all 5 walked pixels.
        // (This is the linear src+dest*oma form, NOT the over-operator.)
        img.drawLine(0, 0, 4, 2, new Color(255, 255, 255, 128), Graphics.DRAWMODE_NORMAL);
        assertEquals(0xFF7F7F7F, pixel(img, 0, 0));
        assertEquals(0xFF7F7F7F, pixel(img, 1, 0));
        assertEquals(0xFF7F7F7F, pixel(img, 2, 1));
        assertEquals(0xFF7F7F7F, pixel(img, 3, 1));
        assertEquals(0xFF7F7F7F, pixel(img, 4, 2));
    }

    @Test
    void drawLineAARampsTwoPixelsWide() {
        MemoryImage img = blank(6, 4);
        // (0,0)->(4,2): errAdj = (int)(2/4*0x10000) = 32768, anErrAcc0 = 0
        //   weight = 255 - ((errAcc>>>8)&0xFF); alpha 0xFF -> weightA returns the weight.
        //   dest alpha 0 -> aNewDestAlpha = weight, a = 255 -> colour channels are (c*weight)>>>8.
        // The 7 changed pixels and their blended words:
        img.drawLineAA(0, 0, 4, 2, Color.White, Graphics.DRAWMODE_NORMAL);
        assertArrayEquals(new int[] {
                0xFFFEFEFE, 0x7FFEFEFE, 0, 0, 0, 0,
                0, 0x80FEFEFE, 0xFFFEFEFE, 0x7FFEFEFE, 0, 0,
                0, 0, 0, 0x80FEFEFE, 0x7FFEFEFE, 0,
                0, 0, 0, 0, 0, 0}, rowMajor(img));
    }

    private static int[] rowMajor(MemoryImage img) {
        int[] out = new int[img.mWidth * img.mHeight];
        for (int i = 0; i < out.length; i++) {
            out[i] = img.getBits()[i];
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 7. blt (alpha compositing)
    // ------------------------------------------------------------------

    @Test
    void bltWhiteCompositesEachSourcePixel() {
        MemoryImage dest = fromPixels(4, 2,
                0xFF204060, 0xFF204060, 0xFF204060, 0xFF204060,
                0xFF204060, 0xFF204060, 0xFF204060, 0xFF204060);
        MemoryImage src = fromPixels(3, 1, 0x00000000, 0xFFFFFFFF, 0x80408040);

        dest.blt(src, 0, 0, new Rect(0, 0, 3, 1), Color.White, Graphics.DRAWMODE_NORMAL, false);

        // src alpha 0 -> a == 0 -> dest untouched
        assertEquals(0xFF204060, pixel(dest, 0, 0));
        // src 0xFFFFFFFF: a = 255, dest alpha 255 -> aNewDestAlpha 255, a = 255, oma = 1
        //   red   = ((0x200000*1)>>>8 = 0x2000) + ((0xFF0000*255)>>>8 & 0xFF0000 = 0xFE0000) = 0xFE2000
        //   green = ((0x4000*1)>>>8 = 0x40)  + ((0xFF00*255)>>>8 & 0xFF00 = 0xFE00)      = 0xFE40
        //   blue  = ((0x60*1)>>>8 = 0)       + ((0xFF*255)>>>8 & 0xFF = 0xFE)            = 0xFE
        //   OR -> 0xFFFEFEFE
        assertEquals(0xFFFEFEFE, pixel(dest, 1, 0));
        // src 0x80408040: a = 0x80 = 128, dest alpha 255 -> aNewDestAlpha 255, a = 128, oma = 128
        //   red   = ((0x200000*128)>>>8 = 0x100000) + ((0x400000*128)>>>8 & 0xFF0000 = 0x200000) = 0x300000
        //   green = ((0x4000*128)>>>8 = 0x2000)     + ((0x8000*128)>>>8 & 0xFF00 = 0x4000)       = 0x6000
        //   blue  = ((0x60*128)>>>8 = 0x30)         + ((0x40*128)>>>8 & 0xFF = 0x20)             = 0x50
        //   -> 0xFF306050
        assertEquals(0xFF306050, pixel(dest, 2, 0));
        assertEquals(0xFF204060, pixel(dest, 3, 0));
        assertEquals(0xFF204060, pixel(dest, 0, 1));
    }

    @Test
    void bltTintedColourisesTheSource() {
        MemoryImage dest = fromPixels(4, 2,
                0xFF204060, 0xFF204060, 0xFF204060, 0xFF204060,
                0xFF204060, 0xFF204060, 0xFF204060, 0xFF204060);
        MemoryImage src = fromPixels(3, 1, 0x00000000, 0xFFFFFFFF, 0x80408040);

        dest.blt(src, 0, 0, new Rect(0, 0, 3, 1), new Color(255, 0, 0, 128), Graphics.DRAWMODE_NORMAL, false);

        assertEquals(0xFF204060, pixel(dest, 0, 0)); // transparent stays
        // src 0xFFFFFFFF, ca = 128 -> a = (255*128)/255 = 128 ; cr = 255, cg = cb = 0
        //   red   = ((0x200000*128)>>>8 = 0x100000) + (((0xFF0000*128)>>>8 * 255)>>>8 & 0xFF0000 = 0x7F0000)
        //         = 0x8F0000  -> 0x8F
        //   green = ((0x4000*128)>>>8 = 0x2000) + (src green 0) = 0x2000 -> 0x20
        //   blue  = ((0x60*128)>>>8 = 0x30)     + (src blue 0)       = 0x30
        //   -> 0xFF8F2030
        assertEquals(0xFF8F2030, pixel(dest, 1, 0));
        // src 0x80408040, ca = 128 -> a = (0x80*128)/255 = 64 -> oma = 192, cr = 255
        //   -> 0xFF273048
        assertEquals(0xFF273048, pixel(dest, 2, 0));
    }

    @Test
    void bltMarksTheSourceDrawn() {
        MemoryImage dest = blank(2, 2);
        MemoryImage src = fromPixels(1, 1, 0xFFFFFFFF);
        assertFalse(src.mDrawn);
        dest.blt(src, 0, 0, new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL, false);
        assertTrue(src.mDrawn);
    }

    @Test
    void bltNonMemoryImageSourceDoesNothing() {
        MemoryImage dest = blank(2, 2);
        int before = dest.mBitsChangedCount;
        Image notMemory = new Image();

        dest.blt(notMemory, 0, 0, new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL, false);

        assertTrue(notMemory.mDrawn); // mDrawn is set before the instanceof check
        assertEquals(0, pixel(dest, 0, 0));
        assertEquals(before, dest.mBitsChangedCount); // returns before bitsChanged()
    }

    @Test
    void bltWhiteOpaqueSourceIsStraightCopy() {
        MemoryImage dest = fromPixels(3, 1, 0xFF204060, 0xFF204060, 0xFF204060);
        // neither mHasTrans nor mHasAlpha -> upstream serves this from the RL "all opaque" runs,
        // which for a fully opaque source is a straight word copy (dest pixels are overwritten).
        MemoryImage src = fromPixels(2, 1, 0xFFFF0000, 0xFF00FF00);

        dest.blt(src, 0, 0, new Rect(0, 0, 2, 1), Color.White, Graphics.DRAWMODE_NORMAL, false);

        assertEquals(0xFFFF0000, pixel(dest, 0, 0));
        assertEquals(0xFF00FF00, pixel(dest, 1, 0));
        assertEquals(0xFF204060, pixel(dest, 2, 0)); // outside the source rect
    }

    // ------------------------------------------------------------------
    // 8. blt additive
    // ------------------------------------------------------------------

    @Test
    void additiveBltWeightsBySourceAlphaAndClamps() {
        MemoryImage dest = fromPixels(2, 1, 0xFFC0D0E0, 0xFF808080);
        // alpha 0x80 on both pixels -> mHasAlpha true -> alpha-weighted branch
        MemoryImage src = fromPixels(2, 1, 0x80A0B0C0, 0x80404040);

        dest.additiveBlt(src, 0, 0, new Rect(0, 0, 2, 1), Color.White);

        // pixel 0: a = 0x80 = 128
        //   red   = ((0xC00000 + (0xA00000*128)>>>8 = 0x500000)>>>16) = 0x110 -> clamp 255
        //   green = ((0xD000   + (0xB000*128)>>>8 = 0x5800)>>>8)      = 0x128 -> clamp 255
        //   blue  = (0xE0      + (0xC0*128)>>>8 = 0x60)               = 0x140 -> clamp 255
        //   dest alpha preserved -> 0xFFFFFFFF
        assertEquals(0xFFFFFFFF, pixel(dest, 0, 0));
        // pixel 1: red = (0x800000 + 0x200000)>>>16 = 0xA0 ; green = (0x8000+0x2000)>>>8 = 0xA0 ;
        //          blue = 0x80 + 0x20 = 0xA0 -> 0xFFA0A0A0
        assertEquals(0xFFA0A0A0, pixel(dest, 1, 0));
    }

    @Test
    void additiveBltFastPathIgnoresSourceAlpha() {
        MemoryImage dest = fromPixels(2, 1, 0xFFC0D0E0, 0xFF102030);
        // both source pixels have alpha 0 -> mHasAlpha stays false -> no alpha weighting,
        // the raw source channels are added regardless of the (zero) alpha.
        MemoryImage src = fromPixels(2, 1, 0x00A0B0C0, 0x00204050);

        dest.additiveBlt(src, 0, 0, new Rect(0, 0, 2, 1), Color.White);

        // pixel 0: red (0xC00000+0xA00000)>>>16 = 0x160 -> 255; green (0xD000+0xB000)>>>8 = 0x180 -> 255;
        //          blue 0xE0+0xC0 = 0x1A0 -> 255
        assertEquals(0xFFFFFFFF, pixel(dest, 0, 0));
        // pixel 1: red (0x100000+0x200000)>>>16 = 0x30; green (0x2000+0x4000)>>>8 = 0x60; blue 0x30+0x50 = 0x80
        assertEquals(0xFF306080, pixel(dest, 1, 0));
    }

    // ------------------------------------------------------------------
    // 9. bltMirror
    // ------------------------------------------------------------------

    @Test
    void bltMirrorReversesTheSourceRowIntoTheSameRect() {
        MemoryImage dest = fromPixels(4, 1, 0xFF204060, 0xFF204060, 0xFF204060, 0xFF204060);
        MemoryImage src = fromPixels(3, 1, 0xC0FF0000, 0xFF00FF00, 0x800000FF);

        dest.bltMirror(src, 0, 0, new Rect(0, 0, 3, 1), Color.White, Graphics.DRAWMODE_NORMAL, false);

        // source column order is reversed: dest x0 <- src[2], x1 <- src[1], x2 <- src[0]
        assertEquals(0xFF1020AF, pixel(dest, 0, 0)); // src 0x800000FF (a=128) over 0xFF204060
        assertEquals(0xFF00FE40, pixel(dest, 1, 0)); // src 0xFF00FF00 (opaque)
        assertEquals(0xFFC71018, pixel(dest, 2, 0)); // src 0xC0FF0000 (a=192)
        assertEquals(0xFF204060, pixel(dest, 3, 0)); // outside the source rect
    }

    // ------------------------------------------------------------------
    // 10. stretchBlt
    // ------------------------------------------------------------------

    @Test
    void stretchBltClipHelperWritesOnlyTheIntersection() {
        MemoryImage dest = blank(3, 3);
        MemoryImage src = fromPixels(2, 2, 0xFF808080, 0xFF808080, 0xFF808080, 0xFF808080);

        // destRect (0,0,2,2) ∩ clipRect (1,0,2,2) = (1,0,1,2); the source rect shrinks to (1,0,1,2)
        dest.stretchBlt(src, new Rect(0, 0, 2, 2), new Rect(0, 0, 2, 2), new Rect(1, 0, 2, 2),
                Color.White, Graphics.DRAWMODE_NORMAL, false);

        assertEquals(0, pixel(dest, 0, 0)); // clipped away
        assertEquals(0, pixel(dest, 2, 0)); // clipped away
        assertEquals(0, pixel(dest, 1, 2)); // below the destination rect
        // src 0xFF808080 opaque over black: b = (0x80*255)>>>8 = 0x7F, likewise g, r -> 0xFF7F7F7F
        assertEquals(0xFF7F7F7F, pixel(dest, 1, 0));
        assertEquals(0xFF7F7F7F, pixel(dest, 1, 1));
    }

    @Test
    void slowStretchBltOnePixelToTwoByTwoFloorsByOne() {
        MemoryImage dest = blank(3, 3);
        MemoryImage src = fromPixels(1, 1, 0xFF3366CC);

        dest.stretchBlt(src, new Rect(0, 0, 2, 2), new Rect(0, 0, 1, 1), new Rect(0, 0, 3, 3),
                Color.White, Graphics.DRAWMODE_NORMAL, false);

        // 1x1 -> 2x2 enlarging path: each new pixel accumulates 256*aFactor(=257) = 0x10100 * channel,
        // and the final >>>16 yields the channel value (0x10100*0x33 = 51, that is 0x33 and one
        // extra 0x33 from the *0x100 term) -> source channels 0x33/0x66/0xCC survive.
        // The final blend is floor(x*255/256) -> r 0x33-1 = 0x32, g 0x66-1 = 0x65, b 0xCC-1 = 0xCB.
        assertEquals(0xFF3265CB, pixel(dest, 0, 0));
        assertEquals(0xFF3265CB, pixel(dest, 1, 0));
        assertEquals(0xFF3265CB, pixel(dest, 0, 1));
        assertEquals(0xFF3265CB, pixel(dest, 1, 1));
        assertEquals(0, pixel(dest, 2, 2));
    }

    @Test
    void fastStretchBltSamplesNearestWithTheFirstSampleOffset() {
        MemoryImage dest = blank(4, 4);
        // row0: red green ; row1: blue yellow
        MemoryImage src = fromPixels(2, 2, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00);

        dest.stretchBlt(src, new Rect(0, 0, 4, 4), new Rect(0, 0, 2, 2), new Rect(0, 0, 4, 4),
                Color.White, Graphics.DRAWMODE_NORMAL, true);

        // anAddX = anAddY = 0.5; aSrcX = srcRect.mX then aSrcX += anAddX before the first read:
        //   dest col 0 samples srcX 0.5 -> 0 ; col 1 -> 1.0 -> 1 ; col 2 -> 1.5 -> 1 ; col 3 -> 2.0 -> 2
        //   which walks off the end of the row (index 2 in a 2-wide image = next row, index 4 = the
        //   canary word 0x4BEEFADE).
        // Opaque source -> a = 255, floor(x*255/256) -> 0xFE for full channels; the canary
        // 0x4BEEFADE (a = 0x4B) blended yields 0x4BEDF9DD.
        assertArrayEquals(new int[] {
                0xFFFE0000, 0xFF00FE00, 0xFF00FE00, 0xFF0000FE,
                0xFFFE0000, 0xFF00FE00, 0xFF00FE00, 0xFF0000FE,
                0xFF0000FE, 0xFFFEFE00, 0xFFFEFE00, 0x4BEDF9DD,
                0xFF0000FE, 0xFFFEFE00, 0xFFFEFE00, 0x4BEDF9DD}, rowMajor(dest));
    }

    @Test
    void fastStretchBltColorizedWritesNothingButBumpsChangedCount() {
        MemoryImage dest = blank(4, 4);
        MemoryImage src = fromPixels(2, 2, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00);
        int before = dest.mBitsChangedCount;

        // upstream's colorized FastStretchBlt branch is empty; the port reproduces that
        dest.stretchBlt(src, new Rect(0, 0, 4, 4), new Rect(0, 0, 2, 2), new Rect(0, 0, 4, 4),
                new Color(255, 0, 0), Graphics.DRAWMODE_NORMAL, true);

        for (int i = 0; i < 16; i++) {
            assertEquals(0, dest.getBits()[i]);
        }
        assertEquals(before + 1, dest.mBitsChangedCount); // bitsChanged() still runs
    }

    // ------------------------------------------------------------------
    // 11. bltRotated
    // ------------------------------------------------------------------

    @Test
    void bltRotatedEntirelyOutsideClipWritesNothing() {
        MemoryImage dest = blank(4, 4);
        MemoryImage src = fromPixels(2, 2, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
        int before = dest.mBitsChangedCount;

        dest.bltRotated(src, 0, 0, new Rect(0, 0, 2, 2), new Rect(100, 100, 4, 4),
                Color.White, Graphics.DRAWMODE_NORMAL, 0, 0, 0);

        for (int i = 0; i < 16; i++) {
            assertEquals(0, dest.getBits()[i]);
        }
        assertEquals(before, dest.mBitsChangedCount); // clip helper returns false before bitsChanged()
    }

    @Test
    void bltRotatedZeroRotationBilinearIsNearlyIdentity() {
        MemoryImage dest = blank(16, 16);
        MemoryImage src = fromPixels(4, 4,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC);

        dest.bltRotated(src, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                Color.White, Graphics.DRAWMODE_NORMAL, 0, 0, 0);

        // rot 0: aUFactor = aVFactor = 1 -> a1 = (255 * ((0xFFFF*0xFFFF)>>>16 = 0xFFFE))>>>16 = 254,
        // a2..a4 = 0, so a = 254 -> the "a > 250" opaque path.
        // red  = 0x330000*254 & 0xFF000000 = 0x32000000, >>>8 -> 0x32
        // green= 0x6600*254   & 0x00FF0000 = 0x650000,   >>>8 -> 0x65
        // blue = 0xCC*254     & 0x0000FF00 = 0xCA00,     >>>8 -> 0xCA
        // The horizontal edges clamp the span to x = destX+1 .. destX+2 and the last row is
        // skipped by the aV >= (srcH-1)<<16 test, so 6 pixels change.
        int expected = 0xFF3265CA;
        assertEquals(expected, pixel(dest, 11, 10));
        assertEquals(expected, pixel(dest, 12, 10));
        assertEquals(expected, pixel(dest, 11, 11));
        assertEquals(expected, pixel(dest, 12, 11));
        assertEquals(expected, pixel(dest, 11, 12));
        assertEquals(expected, pixel(dest, 12, 12));

        int changed = 0;
        for (int i = 0; i < 256; i++) {
            if (dest.getBits()[i] != 0) {
                changed++;
            }
        }
        assertEquals(6, changed);
        assertEquals(0, pixel(dest, 10, 10));
        assertEquals(0, pixel(dest, 13, 13));
    }

    @Test
    void bltRotatedQuarterTurnOnFourByFour() {
        MemoryImage dest = blank(16, 16);
        MemoryImage src = fromPixels(4, 4,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC,
                0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC);

        dest.bltRotated(src, 1, 1, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                Color.White, Graphics.DRAWMODE_NORMAL, Math.PI / 2, 0, 0);

        // rot = pi/2 -> cos fixed-point rounds to 0, sin fixed-point to 0x10000; the rotated
        // bounding box is (1,0,4,1) and the covered span is dest x = 2..3 on row 0.
        assertEquals(0xFF3265CA, pixel(dest, 2, 0));
        assertEquals(0xFF3265CA, pixel(dest, 3, 0));
        assertEquals(0, pixel(dest, 1, 0));
        assertEquals(0, pixel(dest, 4, 0));
    }

    @Test
    void bltRotatedQuarterTurnOfTwoByOneDrawsNothing() {
        MemoryImage dest = blank(16, 16);
        MemoryImage src = fromPixels(2, 1, 0xFFFF0000, 0xFF00FF00);
        int before = dest.mBitsChangedCount;

        dest.bltRotated(src, 5, 5, new Rect(0, 0, 2, 1), new Rect(0, 0, 16, 16),
                Color.White, Graphics.DRAWMODE_NORMAL, Math.PI / 2, 0, 0);

        // with cos == 0 the u-edge clamps demand aU in [0, (srcW-1)<<16) = [0, 0x10000), which the
        // two-row span cannot satisfy simultaneously, and aLeft (1) ends up greater than aRight (0)
        for (int i = 0; i < 256; i++) {
            assertEquals(0, dest.getBits()[i]);
        }
        assertEquals(before + 1, dest.mBitsChangedCount); // clip helper passed, bitsChanged() ran
    }

    @Test
    void bltRotatedAdditiveAddsTheColour() {
        MemoryImage dest = fromPixels(16, 16, filled(16 * 16, 0xFF204060));
        MemoryImage src = fromPixels(4, 4, filled(16, 0xFF3366CC));

        dest.bltRotated(src, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                Color.White, Graphics.DRAWMODE_ADDITIVE, 0, 0, 0);

        // additive path adds the weighted source onto the destination, alpha preserved:
        //   r = addMax(0x32 + 0x20) = 0x52 ; g = addMax(0x65 + 0x40) = 0xA5 ; b = addMax(0xCA + 0x60) = 0x12A -> 0xFF
        int expected = 0xFF52A5FF;
        assertEquals(expected, pixel(dest, 11, 10));
        assertEquals(expected, pixel(dest, 12, 12));
        assertEquals(0xFF204060, pixel(dest, 10, 10));
    }

    @Test
    void bltRotatedTintedModulatesTheSource() {
        MemoryImage dest = fromPixels(16, 16, filled(16 * 16, 0xFF204060));
        MemoryImage src = fromPixels(4, 4, filled(16, 0xFF3366CC));

        dest.bltRotated(src, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                new Color(255, 0, 0, 128), Graphics.DRAWMODE_NORMAL, 0, 0, 0);

        // non-white, non-additive: a1..a4 are pre-scaled by ca=128, so a = (254*128)>>>8 = 127.
        // cr = mRed+1 = 256, cg = cb = 1. r/g/b are the modulated source channels, then blended
        // against dest 0xFF204060 with finalAlpha = 256 - ((256-a)*(256-da))>>>8 = 256:
        //   red = (0x19000000 + 129*dr)/256 & 0xFF0000 = 0x28, green 0x1F, blue 0x2F.
        int expected = 0xFF281F2F;
        assertEquals(expected, pixel(dest, 11, 10));
        assertEquals(expected, pixel(dest, 12, 12));
        assertEquals(0xFF204060, pixel(dest, 10, 10));
    }

    @Test
    void bltRotatedOffsetSourceRectSamplesTheOffsetRegion() {
        // bltF is upstream's rot == 0 path into bltRotated. A srcRect at (1,1) anchors the source
        // pointer at aSrcBase = 1*4 + 1 = 5 (upstream MemoryImage.cpp:1473), so the sampled texels
        // differ from a srcRect anchored at the origin.
        MemoryImage src = fromPixels(4, 4,
                0xFF010203, 0xFF040506, 0xFF070809, 0xFF0A0B0C,
                0xFF111213, 0xFF141516, 0xFF171819, 0xFF1A1B1C,
                0xFF212223, 0xFF242526, 0xFF272829, 0xFF2A2B2C,
                0xFF313233, 0xFF343536, 0xFF373839, 0xFF3A3B3C);

        MemoryImage offset = blank(16, 16);
        offset.bltF(src, 10, 10, new Rect(1, 1, 3, 3), new Rect(0, 0, 16, 16),
                Color.White, Graphics.DRAWMODE_NORMAL);
        // A 2x2 srcRect gives a degenerate empty span here (anEdgeLeft = 1 > anEdgeRight = 0), so a
        // 3-wide rect is used: anEdgeRight = ((3<<16)-0x17FFF)/0x10000 = 1, span = dest x 11,
        // rows 10..11, and the bilinear weight makes a1 = 254, a2..a4 = 0 -> the "a > 250" path.
        // sample y=0: index aSrcBase + 0*4 + 1 = 6 -> 0xFF171819
        //   red   = (0x170000*254) & 0xFF000000 = 0x16000000 -> 0x16
        //   green = (0x1800*254)   & 0x00FF0000 = 0x170000   -> 0x17
        //   blue  = (0x19*254)     & 0x0000FF00 = 0x1800     -> 0x18
        assertEquals(0xFF161718, pixel(offset, 11, 10));
        // sample y=1: index 5 + 1*4 + 1 = 10 -> 0xFF272829 -> 0xFF262728
        assertEquals(0xFF262728, pixel(offset, 11, 11));
        assertEquals(0, pixel(offset, 10, 10));
        assertEquals(0, pixel(offset, 12, 10));
        assertEquals(0, pixel(offset, 11, 12));

        MemoryImage origin = blank(16, 16);
        origin.bltF(src, 10, 10, new Rect(0, 0, 3, 3), new Rect(0, 0, 16, 16),
                Color.White, Graphics.DRAWMODE_NORMAL);
        // same geometry, source anchored at the origin: index 1 -> 0xFF040506, index 5 -> 0xFF141516
        assertEquals(0xFF030405, pixel(origin, 11, 10));
        assertEquals(0xFF131415, pixel(origin, 11, 11));
        assertNotEquals(pixel(origin, 11, 10), pixel(offset, 11, 10));
        assertNotEquals(pixel(origin, 11, 11), pixel(offset, 11, 11));
    }

    @Test
    void bltRotatedAdditiveTintUsesPremultipliedWeights() {
        MemoryImage src = fromPixels(4, 4, filled(16, 0xFF3366CC));

        MemoryImage additive = fromPixels(16, 16, filled(16 * 16, 0xFF204060));
        additive.bltRotated(src, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                new Color(200, 100, 50, 128), Graphics.DRAWMODE_ADDITIVE, 0, 0, 0);
        // MI_BltRotated_Additive.inc premultiplies the tint by its alpha:
        //   acr = 200*128/255 = 100, acg = 100*128/255 = 50, acb = 50*128/255 = 25
        // uniform opaque source -> a1 = 254, so the sums are 0x330000*254, 0x6600*254 and 0xCC*254:
        //   red   = addMax((0x32 * acr) >>> 8 + 0x20)       = addMax(19 + 0x20) = 0x33
        //   green = addMax((0x653400 * acg) >>> 24 + 0x40)  = addMax(19 + 0x40) = 0x53
        //   blue  = addMax((0xCA68 * acb) >>> 16 + 0x60)    = addMax(19 + 0x60) = 0x73
        assertEquals(0xFF335373, pixel(additive, 11, 10));
        assertEquals(0xFF335373, pixel(additive, 12, 12));
        assertEquals(0xFF204060, pixel(additive, 10, 10));

        MemoryImage clamped = fromPixels(16, 16, filled(16 * 16, 0xFFF0D0C0));
        clamped.bltRotated(src, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                new Color(200, 100, 50, 128), Graphics.DRAWMODE_ADDITIVE, 0, 0, 0);
        // red = 19 + 0xF0 = 259 -> clamped to 255; green = 19 + 0xD0 = 0xE3; blue = 19 + 0xC0 = 0xD3
        assertEquals(0xFFFFE3D3, pixel(clamped, 11, 10));
        assertEquals(0xFFFFE3D3, pixel(clamped, 12, 12));

        MemoryImage normal = fromPixels(16, 16, filled(16 * 16, 0xFF204060));
        normal.bltRotated(src, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 16, 16),
                new Color(200, 100, 50, 255), Graphics.DRAWMODE_NORMAL, 0, 0, 0);
        // the normal path keeps the +1 rounded weights (cr=201, cg=101, cb=51) with ca=255:
        //   a1 = (254*255)>>>8 = 253 > 250 -> opaque: r = 0x27, g = 0x27, b = 0x28
        assertEquals(0xFF272728, pixel(normal, 11, 10));
        assertEquals(0xFF272728, pixel(normal, 12, 12));
    }

    private static int[] filled(int count, int value) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = value;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 12. bltMatrix
    // ------------------------------------------------------------------

    @Test
    void bltMatrixIdentityDrawsTheSourceCentredOnTheOrigin() {
        MemoryImage dest = blank(4, 4);
        MemoryImage src = fromPixels(2, 2, 0xFF3366AA, 0xFF3366AA, 0xFF3366AA, 0xFF3366AA);

        SexyMatrix3 m = new SexyMatrix3();
        m.m00 = 1;
        m.m11 = 1;

        dest.bltMatrix(src, 0, 0, m, new Rect(0, 0, 4, 4), Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 2, 2), false);

        // PORT-NOTE: this port uses its own barycentric software triangle rasterizer (not SWTri).
        // Corners (-w/2..w/2) - 0.5 => (-1.5,-1.5) .. (0.5,0.5); only pixel (0,0) is covered and it
        // samples the source colour 0xFF3366AA (nearest texel), which the opaque untinted 0xf0
        // branch stores verbatim: `*pix = tex | 0xFF000000`, SWTri_Pixel8888.inc:70.
        assertEquals(0xFF3366AA, pixel(dest, 0, 0));
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (x != 0 || y != 0) {
                    assertEquals(0, pixel(dest, x, y));
                }
            }
        }
    }

    @Test
    void bltMatrixTranslationMovesTheBlock() {
        MemoryImage dest = blank(6, 6);
        MemoryImage src = fromPixels(2, 2, 0xFF3366AA, 0xFF3366AA, 0xFF3366AA, 0xFF3366AA);

        SexyMatrix3 m = new SexyMatrix3();
        m.m00 = 1;
        m.m11 = 1;
        m.m02 = 2;
        m.m12 = 2;

        dest.bltMatrix(src, 0, 0, m, new Rect(0, 0, 6, 6), Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 2, 2), false);

        // the block is translated by (2,2): the covered bbox is x,y in {1,2}; every covered pixel
        // samples the uniform source colour 0xFF3366AA, and an opaque untinted texel is stored
        // verbatim (alpha 0xFF >= 0xf0): `*pix = tex | 0xFF000000`, SWTri_Pixel8888.inc:70.
        assertEquals(0xFF3366AA, pixel(dest, 1, 1));
        assertEquals(0xFF3366AA, pixel(dest, 2, 1));
        assertEquals(0xFF3366AA, pixel(dest, 1, 2));
        assertEquals(0xFF3366AA, pixel(dest, 2, 2));
        assertEquals(0, pixel(dest, 0, 0));
    }

    // ------------------------------------------------------------------
    // 13. bltTrianglesTex
    // ------------------------------------------------------------------

    private static TriVertex[][] triangle(float x0, float y0, float u0, float v0,
                                          float x1, float y1, float u1, float v1,
                                          float x2, float y2, float u2, float v2) {
        return new TriVertex[][] {{
                new TriVertex(x0, y0, u0, v0),
                new TriVertex(x1, y1, u1, v1),
                new TriVertex(x2, y2, u2, v2)}};
    }

    @Test
    void bltTrianglesTexNormalPaintsCoveredPixels() {
        MemoryImage dest = blank(4, 4);
        MemoryImage tex = fromPixels(1, 1, 0xFF3366CC);

        dest.bltTrianglesTex(tex, triangle(0, 0, 0, 0, 2, 0, 1, 0, 0, 2, 0, 1),
                1, new Rect(0, 0, 4, 4), Color.White, Graphics.DRAWMODE_NORMAL, 0, 0, false);

        // covered pixels (barycentric test at pixel centre): (0,0), (1,0), (0,1)
        // the 1x1 texel 0xFF3366CC is fully opaque, untinted and lies in a fully opaque source, so
        // the fragment alpha is 0xFF and the 0xf0 branch stores the texel verbatim:
        // `*pix = tex | 0xFF000000`, SWTri_Pixel8888.inc:70 (no blend is performed).
        int expected = 0xFF3366CC;
        assertEquals(expected, pixel(dest, 0, 0));
        assertEquals(expected, pixel(dest, 1, 0));
        assertEquals(expected, pixel(dest, 0, 1));
        assertEquals(0, pixel(dest, 2, 0));
        assertEquals(0, pixel(dest, 1, 1));
    }

    @Test
    void bltTrianglesTexAdditiveAddsOntoBlack() {
        MemoryImage dest = blank(4, 4);
        MemoryImage tex = fromPixels(1, 1, 0xFF3366CC);

        dest.bltTrianglesTex(tex, triangle(0, 0, 0, 0, 2, 0, 1, 0, 0, 2, 0, 1),
                1, new Rect(0, 0, 4, 4), Color.White, Graphics.DRAWMODE_ADDITIVE, 0, 0, false);

        // additive: dest alpha is preserved (0 here), channels = (src*a)>>>8 = (c*255)>>>8
        int expected = 0x003265CB;
        assertEquals(expected, pixel(dest, 0, 0));
        assertEquals(expected, pixel(dest, 1, 0));
        assertEquals(expected, pixel(dest, 0, 1));
        assertEquals(0, pixel(dest, 2, 0));
    }

    @Test
    void bltTrianglesTexAdditiveClamps() {
        MemoryImage dest = fromPixels(4, 4, filled(16, 0xFFC0D0E0));
        MemoryImage tex = fromPixels(1, 1, 0xFFFFFFFF);

        dest.bltTrianglesTex(tex, triangle(0, 0, 0, 0, 2, 0, 1, 0, 0, 2, 0, 1),
                1, new Rect(0, 0, 4, 4), Color.White, Graphics.DRAWMODE_ADDITIVE, 0, 0, false);

        // 0xC0 + 0xFE, 0xD0 + 0xFE and 0xE0 + 0xFE all clamp to 255 -> 0xFFFFFFFF
        assertEquals(0xFFFFFFFF, pixel(dest, 0, 0));
        assertEquals(0xFFFFFFFF, pixel(dest, 1, 0));
        assertEquals(0xFFFFFFFF, pixel(dest, 0, 1));
        assertEquals(0xFFC0D0E0, pixel(dest, 2, 0)); // not covered
    }

    @Test
    void bltTrianglesTexZeroAreaDrawsNothing() {
        MemoryImage dest = blank(4, 4);
        MemoryImage tex = fromPixels(1, 1, 0xFF3366CC);
        int before = dest.mBitsChangedCount;

        // collinear vertices -> aArea == 0 -> the rasterizer returns early
        dest.bltTrianglesTex(tex, triangle(0, 0, 0, 0, 2, 0, 1, 0, 4, 0, 0, 1),
                1, new Rect(0, 0, 4, 4), Color.White, Graphics.DRAWMODE_NORMAL, 0, 0, false);

        for (int i = 0; i < 16; i++) {
            assertEquals(0, dest.getBits()[i]);
        }
        assertEquals(before + 1, dest.mBitsChangedCount); // bltTrianglesTex still calls bitsChanged()
    }

    // ------------------------------------------------------------------
    // 14. fillScanLinesWithCoverage
    // ------------------------------------------------------------------

    @Test
    void fillScanLinesWithCoverageReproducesTheZeroAlphaPointerQuirk() {
        MemoryImage img = blank(3, 1);
        int before = img.mBitsChangedCount;

        Span[] spans = {new Span(0, 0, 3)};
        byte[] coverage = {0, (byte) 128, (byte) 255};
        img.fillScanLinesWithCoverage(spans, 1, new Color(0xFF3366CC), Graphics.DRAWMODE_NORMAL,
                coverage, 0, 0, 3, 1);

        // cover word = byte + 1; a = (cover * alpha) >>> 8
        //   w=0: cover 1  -> a=0   -> the destination pointer is NOT advanced (upstream quirk)
        //   w=1: cover 129 -> a=128 -> paints index 0 (still the first pixel) : 0x803265CB
        //   w=2: cover 256 -> a=255 -> paints index 1                        : 0xFF3265CB
        assertEquals(0x803265CB, pixel(img, 0, 0));
        assertEquals(0xFF3265CB, pixel(img, 1, 0));
        assertEquals(0, pixel(img, 2, 0)); // never reached (pointer only advanced twice)
        assertEquals(before + 1, img.mBitsChangedCount);
    }

    // ------------------------------------------------------------------
    // 15. buffer lifetime
    // ------------------------------------------------------------------

    @Test
    void purgeBitsDefersWhenNothingCanRestoreTheBits() {
        MemoryImage img = fromPixels(2, 2, 0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC);
        int[] before = img.mBits;

        // mRenderData == 0 and no mFilePath: nothing could restore the pixels, so purgeBits defers
        img.purgeBits();
        assertTrue(img.mPurgeBits);
        assertNotNull(img.mBits);
        assertSame(before, img.mBits);
        assertEquals(0xFF112233, img.mBits[0]);
        assertEquals(0xFFAABBCC, img.mBits[3]);
        // getBits() hands back the same, still-intact pixels
        assertSame(before, img.getBits());
        assertEquals(0xFF112233, img.getBits()[0]);
    }

    @Test
    void purgeBitsDropsWhenRenderDataCanRestoreTheBits() {
        MemoryImage img = fromPixels(2, 2, 0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC);
        img.mRenderData = 1; // fake GPU handle

        img.purgeBits();
        assertTrue(img.mPurgeBits);
        assertNull(img.mBits);

        int[] bits = img.getBits();
        assertNotNull(img.mBits);
        assertEquals(5, bits.length);
        assertEquals(CANARY, bits[4]);
        assertEquals(0, bits[0]); // re-materialised as zeros (no mFilePath to re-decode from)
        assertEquals(0, bits[3]);
    }

    @Test
    void purgeBitsDropsWhenAFilePathCanRestoreTheBits() {
        MemoryImage img = fromPixels(2, 2, 0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC);
        img.mFilePath = "some/asset.png";

        img.purgeBits();
        assertTrue(img.mPurgeBits);
        assertNull(img.mBits);
    }

    @Test
    void reInitCompletesADeferredPurgeOnceRestorable() {
        MemoryImage img = fromPixels(2, 2, 0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC);

        img.purgeBits(); // deferred
        assertNotNull(img.mBits);

        img.reInit(); // still nothing can restore the bits -> still deferred
        assertNotNull(img.mBits);
        assertTrue(img.mPurgeBits);

        img.mRenderData = 1; // now restorable
        img.reInit();
        assertNull(img.mBits);
    }

    @Test
    void deleteSWBuffersMaterialisesTheBits() {
        MemoryImage img = fromPixels(2, 2, 0xFF112233, 0xFF445566, 0xFF778899, 0xFFAABBCC);
        img.mRenderData = 1;
        img.purgeBits();
        assertNull(img.mBits);

        img.deleteSWBuffers();
        assertNotNull(img.mBits); // getBits() materialises the CPU bits
        assertTrue(img.mPurgeBits); // still flagged as purged
    }

    @Test
    void deleteExtraAnd3DBuffersDoNotThrowWithNullApp() {
        MemoryImage img = new MemoryImage(null);
        assertDoesNotThrow(img::deleteExtraBuffers);
        assertDoesNotThrow(img::delete3DBuffers);
    }
}
