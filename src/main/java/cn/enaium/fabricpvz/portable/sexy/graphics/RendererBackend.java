package cn.enaium.fabricpvz.portable.sexy.graphics;

/**
 * Platform drawing backend used by {@link Graphics}.
 *
 * <p>The upstream framework renders through SDL + OpenGL ES 2.0 ({@code GLInterface}); this port
 * implements the same semantics on the SDL3 renderer ({@code SDL_Renderer}) through the LWJGL
 * {@code lwjgl-sdl} bindings. Backends are the only place that talks to the platform: everything
 * above ({@link Graphics}, {@link Image}, {@link MemoryImage}, fonts) is platform independent and
 * unit testable with a recording test double.
 *
 * <p>Coordinates are in renderer pixels, y down, origin at the top-left of the render target.
 * Colours are 32-bit {@code 0xAARRGGBB} values.
 */
public interface RendererBackend {

    /** Texture blend modes required by the framework ({@code Graphics::mDrawMode}). */
    enum BlendMode {
        /** Standard alpha blending (upstream {@code Graphics::DRAWMODE_NORMAL}). */
        NORMAL,
        /** Additive blending (upstream {@code Graphics::DRAWMODE_ADDITIVE}). */
        ADDITIVE,
        /** No blending (upstream opaque draws). */
        NONE
    }

    /**
     * The window-space rectangle the logical canvas is presented into (upstream
     * {@code GLInterface::mPresentationRect}).
     *
     * <p>Mouse events arrive in window coordinates, so the widget manager needs this rectangle to
     * translate them back into the logical space its widgets are laid out in. The default assumes the
     * whole output is the logical canvas (no scaling, e.g. a backend that renders 1:1).
     *
     * @return {@code {x, y, width, height}} in output/window pixels
     */
    default int[] logicalPresentationRect() {
        return new int[] {0, 0, renderOutputWidth(), renderOutputHeight()};
    }

    /**
     * Width of the logical canvas in logical pixels (the coordinate space widgets are laid out in,
     * upstream {@code GLInterface::mWidth}). The default is the output size, i.e. "no scaling".
     */
    default int logicalWidth() {
        return renderOutputWidth();
    }

    /** Height of the logical canvas in logical pixels; see {@link #logicalWidth()}. */
    default int logicalHeight() {
        return renderOutputHeight();
    }

    /** Flip flags for {@link #drawTexture}. */
    int FLIP_NONE = 0;
    int FLIP_HORIZONTAL = 1;
    int FLIP_VERTICAL = 2;
    int FLIP_BOTH = 3;

    /**
     * Creates a texture with the given size and optional initial pixels.
     *
     * @param argbPixels {@code width*height} pixels ({@code 0xAARRGGBB}, straight alpha) or {@code null} for blank
     * @return an opaque texture handle; {@code 0} is never returned, failures throw
     */
    long createTexture(int width, int height, int[] argbPixels);

    /**
     * Creates a texture that can be used as a render target (upstream's offscreen images, e.g. cached
     * reanimation frames). Drawing into it is done with {@link #setRenderTarget(long)}; its contents
     * are readable with {@link #readTexturePixels}.
     */
    long createRenderTargetTexture(int width, int height);

    /** Replaces the full contents of a texture (used for streaming/animated images). */
    void updateTexture(long texture, int[] argbPixels, int width, int height);

    /** Releases the texture. Safe to call with {@code 0}. */
    void destroyTexture(long texture);

    /**
     * Selects nearest (default) or linear filtering for this texture, mirroring upstream's
     * {@code linearFilter}/{@code mLinearBlend} switch: the framework draws pixel art with nearest
     * filtering and switches to linear only for scaled/reanim draws that request it.
     */
    void setTextureFilter(long texture, boolean linear);

    /**
     * Draws a region of a texture.
     *
     * <p>Semantics match upstream {@code Graphics::DrawImage} rotation/scale/colour-mod paths:
     * rotation is clockwise around {@code (centerX, centerY)} given in destination coordinates,
     * and {@code colorModARGB} multiplies both the texture rgb and alpha (255 = unchanged;
     * the framework passes straight-alpha values).
     */
    void drawTexture(
            long texture,
            int srcX,
            int srcY,
            int srcW,
            int srcH,
            float dstX,
            float dstY,
            float dstW,
            float dstH,
            float angleDegrees,
            float centerX,
            float centerY,
            int flip,
            int colorModARGB);

    /** Filled rectangle with the given colour (alpha blended). */
    void fillRect(float x, float y, float width, float height, int colorARGB);

    /** Line drawing used by the framework's drawing helpers. */
    void drawLine(float x1, float y1, float x2, float y2, int colorARGB);

    /**
     * Draws textured triangles (upstream {@code GLInterface::DrawTrianglesTex}), used by particles
     * and the credits screen.
     *
     * @param texture     texture handle, or {@code 0} for untextured triangles
     * @param positionsUv interleaved {@code x, y, u, v} quadruples per vertex (u/v normalised)
     * @param colors      one {@code 0xAARRGGBB} colour per vertex
     * @param vertexCount number of vertices
     * @param indices     triangle indices (3 per triangle) into the vertex arrays
     * @param indexCount  number of indices
     * @param mode        blending for this draw (upstream encodes additive per triangle in vertex z)
     */
    void drawTriangles(
            long texture,
            float[] positionsUv,
            int[] colors,
            int vertexCount,
            int[] indices,
            int indexCount,
            BlendMode mode);

    /**
     * Restricts drawing to the given rectangle (upstream {@code Graphics::SetClipRect}).
     * The rectangle is absolute (not relative to the current transform).
     */
    void setClipRect(int x, int y, int width, int height);

    /** Removes the clip rectangle (upstream {@code Graphics::ClearClipRect}). */
    void clearClipRect();

    /** Sets the blend mode for subsequent draws. */
    void setBlendMode(BlendMode mode);

    /**
     * Redirects drawing into a texture (upstream offscreen {@code Graphics} targets).
     *
     * @param texture texture handle created by {@link #createTexture}, or {@code 0} for the screen
     */
    void setRenderTarget(long texture);

    /** Clears the current target with the given colour. */
    void clear(int colorARGB);

    /** Reads back a texture into {@code dest} ({@code width*height} pixels). */
    void readTexturePixels(long texture, int width, int height, int[] dest);

    /** Presents the rendered frame (no-op for offscreen targets). */
    void present();

    /**
     * Sets the logical drawing resolution (upstream's fixed 800x600 screen space) and the scaling
     * behaviour when the window is larger or smaller: the content is letterboxed, never stretched.
     */
    void setLogicalSize(int width, int height);

    /** Current render output width in pixels (window or render target). */
    int renderOutputWidth();

    /** Current render output height in pixels. */
    int renderOutputHeight();
}
