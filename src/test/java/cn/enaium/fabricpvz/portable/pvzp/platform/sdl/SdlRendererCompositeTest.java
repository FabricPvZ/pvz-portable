package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;

/**
 * The presentation path of {@link SdlRenderer}: a window whose drawable differs from the logical canvas
 * (a high-density window, or any resized window) gets an offscreen canvas texture, the frame is drawn
 * into it 1:1 and {@link SdlRenderer#compositeToOutput()} scales it into the window once per frame.
 *
 * <p>Verified against the SDL software renderer, where the whole path is observable pixel by pixel: a
 * 2x drawable must contain every canvas pixel as one uniform {@code 2x2} block (which is what "a rotated
 * primitive is not distorted by the scaling" means), a wider drawable must get black bars and a 1:1
 * window must keep drawing into the window itself, byte for byte as before. The scaling policy itself
 * ({@code >= 1x} whole numbers are nearest, everything else linear) is a pure function and tested as
 * such.
 */
class SdlRendererCompositeTest {

    private static final int OPAQUE_BLACK = 0xFF000000;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

    private static int pixel(int[] pixels, int width, int x, int y) {
        return pixels[y * width + x];
    }

    private static int distinctColors(int[] pixels) {
        Set<Integer> aColors = new HashSet<>();
        for (int aPixel : pixels) {
            aColors.add(aPixel);
        }
        return aColors.size();
    }

    @Test
    void letterboxRectCoversTheScalingCases() {
        // 1:1
        assertArrayEquals(new int[] {0, 0, 800, 600}, SdlRenderer.letterboxRect(800, 600, 800, 600));
        // Whole-number magnifications
        assertArrayEquals(new int[] {0, 0, 1600, 1200}, SdlRenderer.letterboxRect(800, 600, 1600, 1200));
        assertArrayEquals(new int[] {0, 0, 2400, 1800}, SdlRenderer.letterboxRect(800, 600, 2400, 1800));
        // The case this machine runs: 800x600 in a 1230x923 drawable -> 1.5375x, no letterboxing
        assertArrayEquals(new int[] {0, 0, 1230, 923}, SdlRenderer.letterboxRect(800, 600, 1230, 923));
        // Taller than the canvas: black bars above and below, centred with the odd pixel rounded down
        assertArrayEquals(new int[] {0, 20, 320, 160}, SdlRenderer.letterboxRect(200, 100, 320, 200));
        assertArrayEquals(new int[] {0, 21, 320, 160}, SdlRenderer.letterboxRect(200, 100, 320, 203));
        // Wider than the canvas: bars left and right
        assertArrayEquals(new int[] {200, 0, 800, 600}, SdlRenderer.letterboxRect(800, 600, 1200, 600));
        assertArrayEquals(new int[] {100, 0, 800, 600}, SdlRenderer.letterboxRect(800, 600, 1000, 600));
        // Smaller than the canvas: the canvas shrinks to fit
        assertArrayEquals(new int[] {0, 0, 400, 300}, SdlRenderer.letterboxRect(800, 600, 400, 300));
        assertArrayEquals(new int[] {0, 50, 400, 300}, SdlRenderer.letterboxRect(800, 600, 400, 400));
        // Odd differences stay inside the output
        int[] aOdd = SdlRenderer.letterboxRect(100, 100, 151, 100);
        assertArrayEquals(new int[] {25, 0, 100, 100}, aOdd);
        // Degenerate input is not a crash
        assertArrayEquals(new int[] {0, 0, 0, 0}, SdlRenderer.letterboxRect(0, 0, 0, 0));
    }

    @Test
    void integerScaleIsOnlyForWholeMagnifications() {
        assertTrue(SdlRenderer.integerScale(800, 600, new int[] {0, 0, 800, 600}), "1x");
        assertTrue(SdlRenderer.integerScale(800, 600, new int[] {0, 0, 1600, 1200}), "2x");
        assertTrue(SdlRenderer.integerScale(800, 600, new int[] {0, 0, 2400, 1800}), "3x");

        int[] aRealDrawable = SdlRenderer.letterboxRect(800, 600, 1230, 923);
        assertFalse(SdlRenderer.integerScale(800, 600, aRealDrawable),
                "1.5375x must be filtered: " + java.util.Arrays.toString(aRealDrawable));
        assertFalse(SdlRenderer.integerScale(800, 600, SdlRenderer.letterboxRect(800, 600, 640, 480)), "0.8x");
        assertFalse(SdlRenderer.integerScale(800, 600, SdlRenderer.letterboxRect(800, 600, 400, 300)), "0.5x");
        assertFalse(SdlRenderer.integerScale(800, 600, new int[] {0, 0, 1600, 600}), "uneven axes are not a copy");
        assertFalse(SdlRenderer.integerScale(800, 600, new int[] {0, 0, 0, 0}), "empty rectangle");
    }

    /**
     * A 2x drawable is the case nearest filtering exists for: the canvas is copied pixel for pixel, each
     * source pixel becoming one {@code 2x2} block. Anything else (a rotated quad scaled per primitive,
     * or a fractional scale duplicating some source pixels) would show up as blocks that are not uniform
     * or as colours the canvas never had.
     */
    @Test
    void twoTimesDrawableCopiesTheCanvasPixelForPixel() {
        try (SdlRenderer renderer = SdlRenderer.software(1600, 1200)) {
            renderer.setLogicalSize(800, 600);
            long aQuad = renderer.createTexture(2, 2, new int[] {RED, GREEN, BLUE, WHITE});
            renderer.setRenderTarget(0);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(10, 10, 100, 50, RED);
            renderer.fillRect(600, 500, 100, 50, 0x80336699);
            // Rotated by 45 degrees around its centre: the primitive whose shape per-primitive scaling
            // used to change.
            renderer.drawTexture(aQuad, 0, 0, 2, 2, 400, 300, 80, 80, 45f, 40, 40,
                    RendererBackend.FLIP_NONE, -1);
            renderer.compositeToOutput();

            assertTrue(renderer.sceneTargetHandle() != 0,
                    "a 1600x1200 drawable over a 800x600 canvas needs an offscreen canvas texture");
            assertArrayEquals(new int[] {0, 0, 1600, 1200}, renderer.logicalPresentationRect(),
                    "the canvas fills the whole drawable: no letterboxing");

            int[] aCanvas = new int[800 * 600];
            renderer.readTexturePixels(0, 800, 600, aCanvas);
            int[] aPresented = new int[1600 * 1200];
            assertEquals(1600 * 1200, renderer.readPresentedPixels(1600, 1200, aPresented),
                    "readPresentedPixels reports the pixels it wrote");

            String aMismatch = null;
            for (int y = 0; y < 600 && aMismatch == null; y++) {
                for (int x = 0; x < 800; x++) {
                    int aExpected = pixel(aCanvas, 800, x, y);
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int aActual = pixel(aPresented, 1600, x * 2 + dx, y * 2 + dy);
                            if (aActual != aExpected) {
                                aMismatch = "canvas(" + x + "," + y + ")=" + Integer.toHexString(aExpected)
                                        + " but the 2x block at (" + (x * 2 + dx) + "," + (y * 2 + dy) + ")="
                                        + Integer.toHexString(aActual);
                                break;
                            }
                        }
                        if (aMismatch != null) {
                            break;
                        }
                    }
                    if (aMismatch != null) {
                        break;
                    }
                }
            }
            assertEquals(null, aMismatch, "every 2x2 block must be one canvas pixel");
            assertEquals(distinctColors(aCanvas), distinctColors(aPresented),
                    "a whole-number scale introduces no colour the canvas does not have");
            assertNotEquals(distinctColors(aCanvas), 1, "the probe scene has to have content");

            // Reading the window does not disturb the canvas binding: the canvas is still what the
            // framework draws into, and it still reads back 1:1.
            int[] aAgain = new int[800 * 600];
            renderer.readTexturePixels(0, 800, 600, aAgain);
            assertArrayEquals(aCanvas, aAgain, "the canvas is unchanged by the composite and the read-back");
            renderer.destroyTexture(aQuad);
        }
    }

    /**
     * A drawable wider than the canvas: the frame is centred and the bars around it are the renderer's
     * own black, drawn by the composite itself rather than filled in by SDL's letterbox.
     */
    @Test
    void widerDrawableIsLetterboxedWithBlackBars() {
        try (SdlRenderer renderer = SdlRenderer.software(1000, 600)) {
            renderer.setLogicalSize(800, 600);
            renderer.setRenderTarget(0);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(0, 0, 800, 600, GREEN);
            renderer.compositeToOutput();

            assertArrayEquals(new int[] {100, 0, 800, 600}, renderer.logicalPresentationRect(),
                    "the canvas is centred horizontally");

            int[] aPresented = new int[1000 * 600];
            renderer.readPresentedPixels(1000, 600, aPresented);
            for (int y = 0; y < 600; y += 199) {
                assertEquals(OPAQUE_BLACK, pixel(aPresented, 1000, 0, y), "left bar at y=" + y);
                assertEquals(OPAQUE_BLACK, pixel(aPresented, 1000, 99, y), "left bar edge at y=" + y);
                assertEquals(OPAQUE_BLACK, pixel(aPresented, 1000, 900, y), "right bar edge at y=" + y);
                assertEquals(OPAQUE_BLACK, pixel(aPresented, 1000, 999, y), "right bar at y=" + y);
                assertEquals(GREEN, pixel(aPresented, 1000, 100, y), "canvas inside the bar at y=" + y);
                assertEquals(GREEN, pixel(aPresented, 1000, 899, y), "canvas inside the bar at y=" + y);
            }

            // The canvas itself is untouched by the composite and unaffected by the bars.
            int[] aCanvas = new int[800 * 600];
            renderer.readTexturePixels(0, 800, 600, aCanvas);
            assertEquals(GREEN, pixel(aCanvas, 800, 0, 0), "the canvas holds what was drawn into it");
            assertEquals(GREEN, pixel(aCanvas, 800, 799, 599), "the canvas holds what was drawn into it");
        }
    }

    /**
     * The 1:1 case - the whole regression baseline: without a size difference no canvas texture is
     * created at all, the window is drawn into directly (so every earlier behaviour, every SceneDump
     * probe and every byte of the previous output stays as it was) and the composite has nothing to do.
     */
    @Test
    void aOneToOneDrawableKeepsDrawingIntoTheWindow() {
        try (SdlRenderer renderer = SdlRenderer.software(800, 600)) {
            renderer.setLogicalSize(800, 600);
            renderer.setRenderTarget(0);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(100, 100, 200, 200, RED);
            renderer.compositeToOutput();

            assertEquals(0, renderer.sceneTargetHandle(),
                    "no size difference means no offscreen canvas and no copy");
            assertArrayEquals(new int[] {0, 0, 800, 600}, renderer.logicalPresentationRect(),
                    "the canvas is the whole window");

            int[] aCanvas = new int[800 * 600];
            renderer.readTexturePixels(0, 800, 600, aCanvas);
            int[] aPresented = new int[800 * 600];
            renderer.readPresentedPixels(800, 600, aPresented);
            assertArrayEquals(aCanvas, aPresented, "the window shows exactly what was drawn");
            assertEquals(RED, pixel(aCanvas, 800, 150, 150), "the fill landed in the window");
            assertEquals(OPAQUE_BLACK, pixel(aCanvas, 800, 50, 50), "and nowhere else");
        }
    }

    /**
     * The bug this pair of tests exists for: SDL refuses to present while a render target is bound
     * ("You can't present on a render target"), and the composite leaves the canvas bound - on purpose,
     * so the framework's binding dedup stays true. Presenting therefore has to release the canvas, put
     * the frame up and bind it again, or the window shows nothing at all while every click still works.
     *
     * <p>Both the window and the software renderer enforce that rule (verified against SDL: the call
     * returns false and the error above), so this runs headlessly.
     */
    @Test
    void presentingWithTheCanvasBoundIsAcceptedAgain() {
        try (SdlRenderer renderer = SdlRenderer.software(1600, 1200)) {
            renderer.setLogicalSize(800, 600);
            renderer.setRenderTarget(0);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(0, 0, 800, 600, GREEN);
            renderer.compositeToOutput();

            renderer.present();
            assertTrue(renderer.lastPresentSucceeded(),
                    "present releases the canvas, so SDL accepts it");

            // Presenting must also leave the canvas bound again: the framework believes the screen (the
            // canvas) is the current target and skips the bind, so drawing after a present has to land in
            // the canvas, not in the window.
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(0, 0, 800, 600, BLUE);

            int[] aCanvas = new int[800 * 600];
            renderer.readTexturePixels(0, 800, 600, aCanvas);
            assertEquals(BLUE, pixel(aCanvas, 800, 400, 300), "drawing after a present lands in the canvas");
        }
    }

    /** The 1:1 case has no canvas to release, and a present must stay acceptable there as well. */
    @Test
    void presentingWithoutACanvasIsAccepted() {
        try (SdlRenderer renderer = SdlRenderer.software(800, 600)) {
            renderer.setLogicalSize(800, 600);
            renderer.setRenderTarget(0);
            renderer.clear(OPAQUE_BLACK);
            renderer.compositeToOutput();
            renderer.present();
            assertTrue(renderer.lastPresentSucceeded(), "the window itself was the target, so presenting is fine");
        }
    }
}
