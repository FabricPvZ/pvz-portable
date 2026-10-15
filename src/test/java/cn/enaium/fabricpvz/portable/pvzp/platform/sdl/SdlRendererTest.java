package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend.BlendMode;

/**
 * Headless verification of the SDL3 renderer backend: every drawing primitive the framework relies
 * on (fill, clip, textured draw with scale, rotated draw, blend modes, offscreen render targets,
 * pixel read-back) is exercised against the SDL software renderer and checked pixel by pixel.
 *
 * <p>Runs without a display ({@code SDL_VIDEODRIVER=dummy}); the Gradle test task sets that
 * automatically.
 */
class SdlRendererTest {

    private static final int OPAQUE_BLACK = 0xFF000000;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

    private static int pixel(int[] pixels, int width, int x, int y) {
        return pixels[y * width + x];
    }

    private static void assertPixelNear(int expectedArgb, int actualArgb, int tolerance, String message) {
        int expectedRed = (expectedArgb >>> 16) & 0xFF;
        int expectedGreen = (expectedArgb >>> 8) & 0xFF;
        int expectedBlue = expectedArgb & 0xFF;
        int actualRed = (actualArgb >>> 16) & 0xFF;
        int actualGreen = (actualArgb >>> 8) & 0xFF;
        int actualBlue = actualArgb & 0xFF;
        assertTrue(
                Math.abs(expectedRed - actualRed) <= tolerance
                        && Math.abs(expectedGreen - actualGreen) <= tolerance
                        && Math.abs(expectedBlue - actualBlue) <= tolerance,
                message + ": expected ~" + Integer.toHexString(expectedArgb & 0xFFFFFF)
                        + " got " + Integer.toHexString(actualArgb & 0xFFFFFF));
    }

    @Test
    void fillRectAndClearAffectOnlyTheRequestedArea() {
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(10, 10, 20, 20, RED);
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(target);

            assertPixelNear(RED, pixel(pixels, 64, 15, 15), 1, "inside the filled rect");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 9, 15), 1, "left of the filled rect");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 30, 15), 1, "right of the filled rect");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 15, 9), 1, "above the filled rect");
        }
    }

    /**
     * A translucent solid fill has to blend with what is already there: the intro cutscene dims the
     * lawn with {@code FillRect(Color(0,0,0,128))} before drawing the PvZ logo, and an opaque fill
     * turns that dim band into a black bar.
     */
    @Test
    void translucentFillBlendsWithTheDestination() {
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(0, 0, 32, 32, 0x8000FF00);
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(target);

            // 0x80/255 of green over black -> about 0x80 green.
            assertPixelNear(0xFF008000, pixel(pixels, 64, 10, 10), 4,
                    "a half-transparent fill must blend with the destination");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 40, 40), 1, "outside the filled rect");
        }
    }

    /**
     * The window-space rect SDL presents the logical canvas into; the app feeds it to the widget
     * manager so pointer coordinates land on the right widgets (upstream's
     * {@code GLInterface::mPresentationRect}).
     */
    @Test
    void logicalPresentationRectReportsTheLetterboxedCanvas() {
        try (SdlRenderer renderer = SdlRenderer.software(320, 200)) {
            renderer.setLogicalSize(200, 100);
            int[] rect = renderer.logicalPresentationRect();
            // 200x100 in 320x200: scale 1.6 -> 320x160, centred vertically
            assertTrue(Math.abs(rect[0] - 0) <= 1 && Math.abs(rect[1] - 20) <= 1
                            && Math.abs(rect[2] - 320) <= 1 && Math.abs(rect[3] - 160) <= 1,
                    "expected about 0,20 320x160 but got " + rect[0] + "," + rect[1] + " " + rect[2] + "x"
                            + rect[3]);
        }
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            int[] rect = renderer.logicalPresentationRect();
            assertEquals(0, rect[0]);
            assertEquals(0, rect[1]);
            assertEquals(64, rect[2], "without a logical size the whole output is the canvas");
            assertEquals(64, rect[3]);
        }
    }

    @Test
    void clipRectLimitsDrawing() {
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.setClipRect(0, 0, 32, 32);
            renderer.fillRect(0, 0, 64, 64, GREEN);
            renderer.clearClipRect();
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(target);

            assertPixelNear(GREEN, pixel(pixels, 64, 16, 16), 1, "inside the clip rect");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 40, 16), 1, "right of the clip rect");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 16, 40), 1, "below the clip rect");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 40, 40), 1, "outside the clip rect");
        }
    }

    @Test
    void drawTextureScalesAndKeepsColors() {
        int[] source = {RED, GREEN, BLUE, WHITE}; // 2x2
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            long texture = renderer.createTexture(2, 2, source);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.drawTexture(texture, 0, 0, 2, 2, 0, 0, 8, 8, 0, 0, 0, RendererBackend.FLIP_NONE, -1);
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(texture);
            renderer.destroyTexture(target);

            assertPixelNear(RED, pixel(pixels, 64, 2, 2), 1, "top-left quadrant red");
            assertPixelNear(GREEN, pixel(pixels, 64, 6, 2), 1, "top-right quadrant green");
            assertPixelNear(BLUE, pixel(pixels, 64, 2, 6), 1, "bottom-left quadrant blue");
            assertPixelNear(WHITE, pixel(pixels, 64, 6, 6), 1, "bottom-right quadrant white");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 9, 9), 1, "outside the drawn rect");
        }
    }

    @Test
    void rotatedDrawMapsQuadrantsClockwise() {
        int[] source = {RED, GREEN, BLUE, WHITE}; // 2x2: (0,0) red (1,0) green (0,1) blue (1,1) white
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            long texture = renderer.createTexture(2, 2, source);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.drawTexture(texture, 0, 0, 2, 2, 16, 16, 8, 8, 90f, 4, 4, RendererBackend.FLIP_NONE, -1);
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(texture);
            renderer.destroyTexture(target);

            // 90 degrees clockwise: source bottom-left (blue) -> dest top-left, source top-left (red)
            // -> dest top-right, source bottom-right (white) -> dest bottom-left, source top-right
            // (green) -> dest bottom-right. Verified against the SDL software renderer.
            assertPixelNear(RED, pixel(pixels, 64, 21, 18), 2, "source top-left rotated to top-right");
            assertPixelNear(GREEN, pixel(pixels, 64, 21, 21), 2, "source top-right rotated to bottom-right");
            assertPixelNear(BLUE, pixel(pixels, 64, 18, 18), 2, "source bottom-left rotated to top-left");
            assertPixelNear(WHITE, pixel(pixels, 64, 18, 21), 2, "source bottom-right rotated to bottom-left");
        }
    }

    @Test
    void alphaBlendingMixesWithTheDestination() {
        // Half-transparent pixels in the source (per-pixel alpha, the PvZ case for shadows/ghosts).
        int[] source = {0x80FFFFFF, 0x80FFFFFF, 0x80FFFFFF, 0x80FFFFFF};
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            long texture = renderer.createTexture(2, 2, source);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.fillRect(0, 0, 32, 32, RED);
            renderer.setBlendMode(BlendMode.NORMAL);
            renderer.drawTexture(texture, 0, 0, 2, 2, 8, 8, 16, 16, 0, 0, 0, RendererBackend.FLIP_NONE, -1);
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(texture);
            renderer.destroyTexture(target);

            assertPixelNear(0xFFFF8080, pixel(pixels, 64, 16, 16), 6, "50% white over red");
            assertPixelNear(OPAQUE_BLACK, pixel(pixels, 64, 40, 40), 2, "outside the blend area stays black");
        }
    }

    @Test
    void addedBlendModeBrightensTheDestination() {
        int[] source = {0x80FF0000, 0x80FF0000, 0x80FF0000, 0x80FF0000}; // 50% red, additive
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            long target = renderer.createRenderTargetTexture(64, 64);
            long texture = renderer.createTexture(2, 2, source);
            renderer.setRenderTarget(target);
            renderer.clear(OPAQUE_BLACK);
            renderer.setBlendMode(BlendMode.ADDITIVE);
            renderer.drawTexture(texture, 0, 0, 2, 2, 0, 0, 16, 16, 0, 0, 0, RendererBackend.FLIP_NONE, -1);
            renderer.setRenderTarget(0);

            int[] pixels = new int[64 * 64];
            renderer.readTexturePixels(target, 64, 64, pixels);
            renderer.destroyTexture(texture);
            renderer.destroyTexture(target);

            int blended = pixel(pixels, 64, 8, 8);
            assertTrue((blended >>> 16 & 0xFF) > 0x40, "additive red must lighten the black target: " + Integer.toHexString(blended));
            assertEquals(0, blended & 0xFF, "additive red must not add blue");
        }
    }

}
