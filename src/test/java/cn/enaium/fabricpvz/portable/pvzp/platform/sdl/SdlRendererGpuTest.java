package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformBackend;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend.BlendMode;

/**
 * Verifies the parts of the shipping renderer path that SDL's software renderer cannot do: per-draw
 * colour/alpha modulation and textured triangles ({@code SDL_RenderGeometry}). Uses a real (hidden)
 * window plus SDL's default GPU renderer — Metal on macOS, OpenGL/Vulkan elsewhere.
 *
 * <p>Skips itself when no GPU renderer is available (headless CI without a display); the macOS test
 * task adds {@code -XstartOnFirstThread}, which SDL requires.
 */
class SdlRendererGpuTest {

    private static final int OPAQUE_BLACK = 0xFF000000;
    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
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
        int actualBlue = (actualArgb & 0xFF);
        assertTrue(
                Math.abs(expectedRed - actualRed) <= tolerance
                        && Math.abs(expectedGreen - actualGreen) <= tolerance
                        && Math.abs(expectedBlue - actualBlue) <= tolerance,
                message + ": expected ~" + Integer.toHexString(expectedArgb & 0xFFFFFF)
                        + " got " + Integer.toHexString(actualArgb & 0xFFFFFF));
    }

    @Test
    void modulatesColorsAndRendersTriangles() {
        SdlPlatform platform = new SdlPlatform();
        platform.init(new PlatformBackend.Options(320, 240, "pvz-fabric renderer test", false, true, false));
        try (SdlRenderer renderer = platform.createRenderer(320, 240, null, true)) {
            Assumptions.assumeFalse(renderer.isSoftware(), "no GPU renderer available in this environment");

            long target = renderer.createRenderTargetTexture(64, 64);
            long white = renderer.createTexture(2, 2, new int[] {WHITE, WHITE, WHITE, WHITE});
            try {
                renderer.setRenderTarget(target);
                renderer.clear(OPAQUE_BLACK);
                renderer.fillRect(0, 0, 64, 64, RED);
                renderer.setBlendMode(BlendMode.NORMAL);
                // 50% alpha modulation: the GPU backend must blend white into red.
                renderer.drawTexture(white, 0, 0, 2, 2, 0, 0, 64, 64, 0, 0, 0, RendererBackend.FLIP_NONE, 0x80FFFFFF);
                renderer.setRenderTarget(0);

                int[] pixels = new int[64 * 64];
                renderer.readTexturePixels(target, 64, 64, pixels);
                assertPixelNear(0xFFFF7F7F, pixel(pixels, 64, 32, 32), 12, "50% white over red (alpha modulation)");

                // Textured triangles (upstream GLInterface::DrawTrianglesTex path).
                long quad = renderer.createTexture(2, 2, new int[] {RED, GREEN, 0xFF0000FF, WHITE});
                try {
                    renderer.setRenderTarget(target);
                    renderer.clear(OPAQUE_BLACK);
                    float[] vertices = {
                        0f, 0f, 0f, 0f,
                        8f, 0f, 1f, 0f,
                        8f, 8f, 1f, 1f,
                        0f, 8f, 0f, 1f,
                    };
                    int[] colors = {WHITE, WHITE, WHITE, WHITE};
                    int[] indices = {0, 1, 2, 0, 2, 3};
                    renderer.drawTriangles(quad, vertices, colors, 4, indices, 6, BlendMode.NORMAL);
                    renderer.setRenderTarget(0);

                    int[] trianglePixels = new int[64 * 64];
                    renderer.readTexturePixels(target, 64, 64, trianglePixels);
                    assertPixelNear(RED, pixel(trianglePixels, 64, 2, 2), 8, "textured triangle top-left");
                    assertPixelNear(GREEN, pixel(trianglePixels, 64, 6, 2), 8, "textured triangle top-right");
                    assertPixelNear(WHITE, pixel(trianglePixels, 64, 6, 6), 8, "textured triangle bottom-right");
                    assertPixelNear(OPAQUE_BLACK, pixel(trianglePixels, 64, 12, 12), 4, "outside the triangle stays black");
                } finally {
                    renderer.destroyTexture(quad);
                }
            } finally {
                renderer.destroyTexture(white);
                renderer.destroyTexture(target);
            }
            assertEquals(0, renderer.currentRenderTarget());
        } finally {
            platform.shutdown();
        }
    }
}
