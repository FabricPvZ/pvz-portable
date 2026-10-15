package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend.BlendMode;

/**
 * Guards the sprite-transparency contract of the SDL backend on the plain quad path.
 *
 * <p>The defect this comes from: with the SDL software renderer the level-award seed packet's
 * transparent background was painted over the frame instead of letting the lawn show through (the
 * resting packet and its 2x award-screen twin both drew as opaque boxes; the same sprite was fine
 * through the scaled path, which is what made it look like a path difference). The backend now pushes
 * the draw blend mode onto SDL's renderer for every textured draw - not only for a geometry batch -
 * and {@code fillRect}/{@code clear}, which set that mode themselves, keep the geometry path's dedup
 * cache in step. Upstream does the equivalent for both paths
 * ({@code GLInterface::Blt} → {@code SetDrawMode}, GLInterface.cpp:1406-1421;
 * {@code GLInterface::DrawTrianglesTex} → {@code SetDrawMode}, GLInterface.cpp:1596-1609).
 *
 * <p>What is pinned here is the observable half: {@link
 * #plainQuadBlitLetsTheDestinationShowThroughTransparentPixels} blits a sprite whose border is fully
 * transparent - carrying the same dark RGB the real packet art's transparent texels carry, so a lost
 * alpha is visible as paint rather than as "nothing happened" - through
 * {@code Graphics.drawImage} → {@code GLImage.blt} → {@code RendererBackend.drawTexture}, with the
 * renderer left in the non-blending state {@code clear()} leaves behind. The cache-sync half of the
 * fix has no pixel assertion: SDL's software {@code SDL_RenderGeometry} follows the <em>texture's</em>
 * blend mode, so the renderer-mode dedup cache is only observable on the GPU backends this suite does
 * not run; it is covered by the comment on {@code SdlRenderer.applyDrawBlend}.
 *
 * <p>Headless: runs against {@link SdlRenderer#software} (no display, no assets, no {@code LawnApp}),
 * the same backend the pixel assertions in {@link SdlRendererTest} use.
 */
class SdlRendererSpriteAlphaTest {

    /** Opaque destination colour the transparent source pixels must leave untouched. */
    private static final int DESTINATION = 0xFF00FF00;
    /**
     * The RGB the seed packet art's fully transparent texels carry ({@code SeedPacket_Larger.png}
     * (0,0) = {@code 0x00202F15}). Using a non-black RGB is what makes a lost alpha visible: an
     * opaque copy paints this dark green where a blend leaves {@link #DESTINATION} alone.
     */
    private static final int TRANSPARENT_TEXEL = 0x00202F15;
    /** Opaque source colour for the inside of the sprite. */
    private static final int SOURCE_CENTRE = 0xFF22FF44;

    private static final int SOURCE_SIZE = 8;

    /** A sprite with a transparent first row/column (like every game sprite's empty background). */
    private static GLImage transparentSprite(SdlRenderer theRenderer) {
        GLImage aSource = new GLImage(theRenderer);
        aSource.create(SOURCE_SIZE, SOURCE_SIZE);
        int[] aBits = aSource.getBits();
        for (int y = 0; y < SOURCE_SIZE; y++) {
            for (int x = 0; x < SOURCE_SIZE; x++) {
                aBits[y * SOURCE_SIZE + x] = (x == 0 || y == 0) ? TRANSPARENT_TEXEL : SOURCE_CENTRE;
            }
        }
        aSource.bitsChanged();
        return aSource;
    }

    /** A draw target bound as an offscreen render target texture. */
    private static GLImage renderTarget(SdlRenderer theRenderer, int theSize) {
        GLImage aTarget = new GLImage(theRenderer);
        aTarget.create(theSize, theSize);
        aTarget.mRenderTarget = true;
        return aTarget;
    }

    private static int pixel(int[] thePixels, int theSize, int x, int y) {
        return thePixels[y * theSize + x];
    }

    /**
     * The resting level-award packet: a plain quad blit of a sprite whose transparent background must
     * let the destination show through, even when the renderer was just left in the non-blending state
     * {@code clear()} sets (the state a fresh renderer starts in too).
     */
    @Test
    void plainQuadBlitLetsTheDestinationShowThroughTransparentPixels() {
        try (SdlRenderer renderer = SdlRenderer.software(64, 64)) {
            GLImage aSource = transparentSprite(renderer);
            GLImage aTarget = renderTarget(renderer, 64);
            long aTargetHandle = aTarget.ensureTexture(aTarget);

            renderer.setRenderTarget(aTargetHandle);
            renderer.clear(DESTINATION); // also puts SDL's draw blend mode at NONE
            renderer.setBlendMode(BlendMode.NORMAL);

            // The plain quad path: Graphics.drawImage → GLImage.blt → drawTexture (no matrix, no
            // triangles), i.e. what draws the resting award packet and the 2x award-screen packet.
            Graphics aGraphics = new Graphics(aTarget);
            aGraphics.drawImage(aSource, 10, 10);

            int[] aPixels = new int[64 * 64];
            renderer.readTexturePixels(aTargetHandle, 64, 64, aPixels);
            renderer.setRenderTarget(0);

            assertEquals(Integer.toHexString(DESTINATION), Integer.toHexString(pixel(aPixels, 64, 10, 10)),
                    "a transparent source pixel must not paint the sprite's empty background");
            assertEquals(Integer.toHexString(DESTINATION), Integer.toHexString(pixel(aPixels, 64, 17, 10)),
                    "a transparent source pixel must not paint the sprite's empty background");
            assertEquals(Integer.toHexString(SOURCE_CENTRE), Integer.toHexString(pixel(aPixels, 64, 13, 13)),
                    "the opaque part of the sprite must still be drawn");
            assertEquals(Integer.toHexString(DESTINATION), Integer.toHexString(pixel(aPixels, 64, 5, 5)),
                    "outside the sprite the destination is untouched");
        }
    }
}
