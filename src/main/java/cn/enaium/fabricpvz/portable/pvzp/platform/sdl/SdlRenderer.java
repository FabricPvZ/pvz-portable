package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLBlendMode;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLRender;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_FPoint;
import org.lwjgl.sdl.SDL_FRect;
import org.lwjgl.sdl.SDL_Rect;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.sdl.SDL_Texture;
import org.lwjgl.sdl.SDL_Vertex;
import org.lwjgl.sdl.SDL_FColor;
import org.lwjgl.system.MemoryUtil;
import cn.enaium.fabricpvz.portable.sexy.graphics.RendererBackend;

/**
 * SDL3 ({@code SDL_Renderer}) implementation of {@link RendererBackend}.
 *
 * <p>Upstream renders through SDL + OpenGL ES 2.0 ({@code GLInterface}); this port uses SDL3's own
 * 2D renderer, which internally picks Metal/OpenGL/Vulkan on desktop and software on demand. The
 * observable behaviour required by the framework is preserved: textured quads with rotation, flip,
 * colour/alpha modulation, blend modes, clip rectangles, offscreen render targets and pixel
 * read-back.
 *
 * <p>Colours are {@code 0xAARRGGBB}; SDL's colour modulation is a multiplier like upstream's vertex
 * tint ({@code mColorizeImages ? mColor : White}), so the values map 1:1. Per-pixel alpha blending
 * works on every backend; the per-draw colour/alpha <em>modulation</em> is applied by the GPU
 * backends (Metal/OpenGL/Vulkan) and ignored by SDL's software renderer, which is only used
 * headlessly (tests) and as a last-resort fallback when no GPU driver is available.
 *
 * <p>The framework's "screen" target ({@code setRenderTarget(0)}) is the canvas a frame is drawn into.
 * While the window drawable measures the same as the logical canvas that is the window itself, drawn
 * 1:1 exactly as before. When it does not - a window on a high-density display, or any resized window -
 * the renderer keeps an offscreen canvas texture of the logical size, draws every primitive into it
 * unscaled, and {@link #compositeToOutput() composites} it into the window once per frame at the end of
 * {@code WidgetManager.drawScreen}, which is upstream's {@code mScreenImage} architecture (upstream
 * draws into {@code GLInterface::mScreenImage} and blits it in {@code GLInterface::Redraw}). It also
 * avoids the artifacts SDL's logical presentation has on this backend: it scales the vertices of every
 * draw, so rotated and skewed quads come out distorted, and a fractional scale duplicates some source
 * pixels and not others ({@code sceneTarget()}).
 */
public final class SdlRenderer implements RendererBackend, AutoCloseable {

    private final long mRenderer;
    private final boolean mSoftware;

    private final SDL_Rect mClip = SDL_Rect.malloc();
    private final SDL_Rect mReadRect = SDL_Rect.malloc();
    /** Diagnostic: log every textured draw (set {@code pvz.traceDraws}). */
    private final boolean mTraceDraws = System.getProperty("pvz.traceDraws") != null;
    /** Last draw blend mode pushed through SDL for the geometry path (-1 = none yet). */
    private int mLastGeometryBlend = -1;

    /** How many stack traces the diagnostic still prints. */
    private final java.util.Set<String> mTracedSignatures = new java.util.HashSet<>();

    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    /** Last (texture, value) pairs pushed to SDL, so repeated draws skip the native calls. */
    private int mLastColorMod;
    private long mLastColorModTexture;
    private int mLastTextureBlend = -1;
    private long mLastTextureBlendTexture;

    /** Scratch buffers for {@link #drawTexturedQuadGpu}: no allocation per draw. */
    private final float[] mQuadPositionsUv = new float[16];

    private final float[][] mQuadCorners = {{0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};

    private final int[] mQuadColors = new int[4];

    private final SDL_FRect mSrc = SDL_FRect.malloc();
    private final SDL_FRect mDst = SDL_FRect.malloc();
    /** Scratch for {@link #compositeToOutput()}'s destination rectangle: the frame path allocates nothing. */
    private final int[] mDstRect = new int[4];
    private final SDL_FPoint mCenter = SDL_FPoint.malloc();
    private final IntBuffer mSizeA = MemoryUtil.memAllocInt(1);
    private final IntBuffer mSizeB = MemoryUtil.memAllocInt(1);

    private ByteBuffer mUploadBuffer;
    private ByteBuffer mVertexBuffer;
    private IntBuffer mIndexBuffer;
    private long mCurrentTarget;
    private BlendMode mBlendMode = BlendMode.NORMAL;
    private int mLogicalWidth = -1;
    private int mLogicalHeight = -1;
    private boolean mClosed;

    /** Window handle ({@code SDL_Window*}) the renderer draws into; 0 for a software renderer. */
    private final long mWindow;

    /**
     * Drawable pixels of the window, i.e. what a present fills and what a read-back of the window reads;
     * refreshed from SDL by {@link #refreshOutputSize()} because it follows the window size and the
     * display's pixel density.
     */
    private int mOutputWidth = -1;
    private int mOutputHeight = -1;

    /** True once a renderer-output/window-drawable mismatch was reported; see {@link #checkRendererOutputMatchesWindow()}. */
    private boolean mOutputMismatchLogged;

    /**
     * Offscreen canvas texture the framework draws a frame into while the window drawable is larger or
     * smaller than the logical canvas; 0 = the window itself is the canvas (1:1, upstream's behaviour).
     */
    private long mSceneTexture;

    /** True once the presence of a scene canvas has been decided (the texture is then never re-made). */
    private boolean mSceneDecided;

    /** True once a failed {@code SDL_RenderPresent} was reported; see {@link #presentNow()}. */
    private boolean mPresentErrorLogged;

    /** Whether SDL accepted the last {@code SDL_RenderPresent}; see {@link #lastPresentSucceeded()}. */
    private boolean mLastPresentSucceeded = true;

    private SdlRenderer(long renderer, boolean software, long window) {
        this.mRenderer = renderer;
        this.mSoftware = software;
        this.mWindow = window;
    }

    /** Creates a GPU-backed renderer for a window. */
    public static SdlRenderer forWindow(long window, String driver) {
        // LWJGL's CharSequence overload NPEs on null, so use the raw variant for "default driver".
        long renderer = driver == null
                ? SDLRender.nSDL_CreateRenderer(window, MemoryUtil.NULL)
                : SDLRender.SDL_CreateRenderer(window, driver);
        if (renderer == 0) {
            throw new IllegalStateException("SDL_CreateRenderer failed: " + SDLError.SDL_GetError());
        }
        SDLRender.SDL_SetRenderVSync(renderer, 1);
        // Worth logging: if SDL falls back to the software renderer the frame loop rasterizes every blit
        // on the CPU, which shows up as an unresponsive window while the game still reports its usual
        // frame rate. The driver name also makes "which backend am I actually on" answerable.
        String aName = SDLRender.SDL_GetRendererName(renderer);
        cn.enaium.fabricpvz.portable.sexy.Common.logInfoLn("RENDERER: '{}' (requested '{}')",
                aName == null ? "?" : aName, driver == null ? "default" : driver);
        return new SdlRenderer(renderer, false, window);
    }

    /**
     * Creates a software renderer writing into a hidden surface; used for headless rendering
     * (tests, CI) where no display is available.
     */
    public static SdlRenderer software(int width, int height) {
        // The software renderer needs the video subsystem for its surface plumbing; with
        // SDL_VIDEODRIVER=dummy this works without a display (verified on macOS and in CI).
        if (SDLInit.SDL_WasInit(SDLInit.SDL_INIT_VIDEO) == 0 && !SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            throw new IllegalStateException("SDL_Init(SDL_INIT_VIDEO) failed: " + SDLError.SDL_GetError());
        }
        SDL_Surface surface = SDLSurface.SDL_CreateSurface(width, height, SDLPixels.SDL_PIXELFORMAT_ARGB8888);
        if (surface == null) {
            throw new IllegalStateException("SDL_CreateSurface failed: " + SDLError.SDL_GetError());
        }
        long renderer = SDLRender.SDL_CreateSoftwareRenderer(surface);
        if (renderer == 0) {
            SDLSurface.SDL_DestroySurface(surface);
            throw new IllegalStateException("SDL_CreateSoftwareRenderer failed: " + SDLError.SDL_GetError());
        }
        SdlRenderer aRenderer = new SdlRenderer(renderer, true, 0);
        // The surface is the output and its size never changes: no window to ask.
        aRenderer.mOutputWidth = width;
        aRenderer.mOutputHeight = height;
        return aRenderer;
    }

    public boolean isSoftware() {
        return mSoftware;
    }

    /** Underlying {@code SDL_Renderer*} handle (0 when closed). */
    public long handle() {
        return mRenderer;
    }

    @Override
    public long createTexture(int width, int height, int[] argbPixels) {
        SDL_Texture texture = SDLRender.SDL_CreateTexture(
                mRenderer, SDLPixels.SDL_PIXELFORMAT_ARGB8888, SDLRender.SDL_TEXTUREACCESS_STATIC, width, height);
        if (texture == null) {
            throw new IllegalStateException("SDL_CreateTexture(" + width + "x" + height + ") failed: " + SDLError.SDL_GetError());
        }
        long handle = texture.address();
        // The framework's default is nearest filtering (pixel art); Graphics switches to linear per
        // draw where upstream passes linearFilter/mLinearBlend.
        SDLRender.SDL_SetTextureScaleMode(texture, SDLSurface.SDL_SCALEMODE_NEAREST);
        if (argbPixels != null) {
            updateTexture(handle, argbPixels, width, height);
        }
        return handle;
    }

    @Override
    public long createRenderTargetTexture(int width, int height) {
        SDL_Texture texture = SDLRender.SDL_CreateTexture(
                mRenderer, SDLPixels.SDL_PIXELFORMAT_ARGB8888, SDLRender.SDL_TEXTUREACCESS_TARGET, width, height);
        if (texture == null) {
            throw new IllegalStateException(
                    "SDL_CreateTexture(TARGET, " + width + "x" + height + ") failed: " + SDLError.SDL_GetError());
        }
        SDLRender.SDL_SetTextureBlendMode(texture, SDLBlendMode.SDL_BLENDMODE_BLEND);
        return texture.address();
    }

    @Override
    public void updateTexture(long texture, int[] argbPixels, int width, int height) {
        if (texture == 0) {
            throw new IllegalArgumentException("texture handle must not be 0");
        }
        int count = width * height;
        if (argbPixels.length < count) {
            throw new IllegalArgumentException("expected " + count + " pixels, got " + argbPixels.length);
        }
        ByteBuffer buffer = uploadBuffer(count * 4);
        IntBuffer ints = buffer.asIntBuffer();
        ints.clear();
        ints.put(argbPixels, 0, count);
        if (!SDLRender.SDL_UpdateTexture(SDL_Texture.create(texture), null, buffer, width * 4)) {
            throw new IllegalStateException("SDL_UpdateTexture failed: " + SDLError.SDL_GetError());
        }
    }

    private ByteBuffer uploadBuffer(int bytes) {
        if (mUploadBuffer == null || mUploadBuffer.capacity() < bytes) {
            if (mUploadBuffer != null) {
                MemoryUtil.memFree(mUploadBuffer);
            }
            mUploadBuffer = MemoryUtil.memAlloc(bytes);
        }
        return mUploadBuffer;
    }

    @Override
    public void destroyTexture(long texture) {
        if (texture != 0) {
            for (int i = 0; i < mTextureSizeCount; i++) {
                if (mTextureSizeKeys[i] == texture) {
                    mTextureSizeKeys[i] = mTextureSizeKeys[mTextureSizeCount - 1];
                    mTextureSizeValues[i] = mTextureSizeValues[mTextureSizeCount - 1];
                    mTextureSizeValues[mTextureSizeCount - 1] = null;
                    mTextureSizeCount--;
                    break;
                }
            }
            SDLRender.SDL_DestroyTexture(SDL_Texture.create(texture));
        }
    }

    @Override
    public void setTextureFilter(long texture, boolean linear) {
        if (texture != 0) {
            SDLRender.SDL_SetTextureScaleMode(
                    SDL_Texture.create(texture),
                    linear ? SDLSurface.SDL_SCALEMODE_LINEAR : SDLSurface.SDL_SCALEMODE_NEAREST);
        }
    }

    @Override
    public void drawTexture(
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
            int colorModARGB) {
        if (texture == 0 || dstW <= 0 || dstH <= 0) {
            return;
        }
        if (!mSoftware) {
            // GPU backends: draw a textured quad through SDL_RenderGeometry with per-vertex colours, which
            // is exactly upstream's model (vertex colour = tint × alpha) and — unlike SDL's texture
            // colour/alpha modulation — is honoured by every backend (verified on Metal).
            drawTexturedQuadGpu(texture, srcX, srcY, srcW, srcH, dstX, dstY, dstW, dstH, angleDegrees, centerX,
                    centerY, flip, colorModARGB);
            return;
        }

        SDL_Texture handle = SDL_Texture.create(texture);
        applyColorMod(handle, colorModARGB);
        applyTextureBlend(handle);
        // SDL's software renderer takes the *renderer's* draw blend mode for SDL_RenderTexture (and
        // forces an opaque copy when it is NONE), so a textured blit must push it like the geometry
        // path does; otherwise the source's transparent pixels are copied over the frame.
        applyDrawBlend(mBlendMode);

        mSrc.set(srcX, srcY, srcW, srcH);
        mDst.set(dstX, dstY, dstW, dstH);
        if (angleDegrees == 0f && flip == FLIP_NONE) {
            if (!SDLRender.SDL_RenderTexture(mRenderer, handle, mSrc, mDst)) {
                throw new IllegalStateException("SDL_RenderTexture failed: " + SDLError.SDL_GetError());
            }
        } else {
            mCenter.set(centerX, centerY);
            if (!SDLRender.SDL_RenderTextureRotated(mRenderer, handle, mSrc, mDst, angleDegrees, mCenter, flip)) {
                throw new IllegalStateException("SDL_RenderTextureRotated failed: " + SDLError.SDL_GetError());
            }
        }
    }

    /** Draws the quad via {@code SDL_RenderGeometry}, applying the tint through vertex colours. */
    private void drawTexturedQuadGpu(
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
            int colorModARGB) {
        float u0 = (float) srcX / textureWidthCache(texture);
        float v0 = (float) srcY / textureHeightCache(texture);
        float u1 = (float) (srcX + srcW) / textureWidthCache(texture);
        float v1 = (float) (srcY + srcH) / textureHeightCache(texture);
        if ((flip & FLIP_HORIZONTAL) != 0) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        if ((flip & FLIP_VERTICAL) != 0) {
            float tmp = v0;
            v0 = v1;
            v1 = tmp;
        }

        float[][] corners = mQuadCorners;
        corners[0][0] = dstX; corners[0][1] = dstY; corners[0][2] = u0; corners[0][3] = v0;
        corners[1][0] = dstX + dstW; corners[1][1] = dstY; corners[1][2] = u1; corners[1][3] = v0;
        corners[2][0] = dstX + dstW; corners[2][1] = dstY + dstH; corners[2][2] = u1; corners[2][3] = v1;
        corners[3][0] = dstX; corners[3][1] = dstY + dstH; corners[3][2] = u0; corners[3][3] = v1;
        if (angleDegrees != 0f) {
            double radians = Math.toRadians(angleDegrees);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            float pivotX = dstX + centerX;
            float pivotY = dstY + centerY;
            for (float[] corner : corners) {
                double rx = corner[0] - pivotX;
                double ry = corner[1] - pivotY;
                corner[0] = (float) (pivotX + rx * cos - ry * sin);
                corner[1] = (float) (pivotY + rx * sin + ry * cos);
            }
        }

        // Reused scratch arrays: a busy frame issues thousands of draws and allocating here showed up as
        // avoidable garbage (the frame loop runs at 100 Hz).
        for (int i = 0; i < 4; i++) {
            mQuadPositionsUv[i * 4] = corners[i][0];
            mQuadPositionsUv[i * 4 + 1] = corners[i][1];
            mQuadPositionsUv[i * 4 + 2] = corners[i][2];
            mQuadPositionsUv[i * 4 + 3] = corners[i][3];
            mQuadColors[i] = colorModARGB;
        }
        drawTriangles(texture, mQuadPositionsUv, mQuadColors, 4, QUAD_INDICES, 6, mBlendMode);
    }

    /**
     * Texture handle to {width, height}. Keyed by the raw {@code long} in parallel arrays: this runs for
     * every textured draw, and a {@code HashMap<Long, ...>} boxed the handle on each lookup.
     */
    private long[] mTextureSizeKeys = new long[64];

    private int[][] mTextureSizeValues = new int[64][];

    private int mTextureSizeCount;

    private int textureWidthCache(long texture) {
        return textureSize(texture)[0];
    }

    private int textureHeightCache(long texture) {
        return textureSize(texture)[1];
    }

    private int[] textureSize(long texture) {
        for (int i = 0; i < mTextureSizeCount; i++) {
            if (mTextureSizeKeys[i] == texture) {
                return mTextureSizeValues[i];
            }
        }

        java.nio.FloatBuffer width = MemoryUtil.memAllocFloat(1);
        java.nio.FloatBuffer height = MemoryUtil.memAllocFloat(1);
        int[] aSize;
        try {
            if (!SDLRender.SDL_GetTextureSize(SDL_Texture.create(texture), width, height)) {
                throw new IllegalStateException("SDL_GetTextureSize failed: " + SDLError.SDL_GetError());
            }
            aSize = new int[] {(int) width.get(0), (int) height.get(0)};
        } finally {
            MemoryUtil.memFree(width);
            MemoryUtil.memFree(height);
        }

        if (mTextureSizeCount == mTextureSizeKeys.length) {
            mTextureSizeKeys = java.util.Arrays.copyOf(mTextureSizeKeys, mTextureSizeCount * 2);
            mTextureSizeValues = java.util.Arrays.copyOf(mTextureSizeValues, mTextureSizeCount * 2);
        }
        mTextureSizeKeys[mTextureSizeCount] = texture;
        mTextureSizeValues[mTextureSizeCount] = aSize;
        mTextureSizeCount++;
        return aSize;
    }

    private void applyColorMod(SDL_Texture texture, int colorModARGB) {
        if (colorModARGB == -1) {
            return;
        }
        // Consecutive draws of the same texture with the same tint (reanimation tracks of one atlas, the
        // loader bar's strips) repeat these native calls thousands of times per frame.
        if (colorModARGB == mLastColorMod && texture.address() == mLastColorModTexture) {
            return;
        }
        mLastColorMod = colorModARGB;
        mLastColorModTexture = texture.address();
        int alpha = (colorModARGB >>> 24) & 0xFF;
        int red = (colorModARGB >>> 16) & 0xFF;
        int green = (colorModARGB >>> 8) & 0xFF;
        int blue = colorModARGB & 0xFF;
        SDLRender.SDL_SetTextureColorMod(texture, (byte) red, (byte) green, (byte) blue);
        SDLRender.SDL_SetTextureAlphaMod(texture, (byte) alpha);
    }

    private void applyTextureBlend(SDL_Texture texture) {
        int aValue = blendModeValue(mBlendMode);
        if (aValue == mLastTextureBlend && texture.address() == mLastTextureBlendTexture) {
            return;
        }
        SDLRender.SDL_SetTextureBlendMode(texture, aValue);
        mLastTextureBlend = aValue;
        mLastTextureBlendTexture = texture.address();
    }

    /**
     * Pushes the current draw blend mode onto the renderer itself ({@code SDL_SetRenderDrawBlendMode}).
     *
     * <p>Every textured draw needs this, not just the geometry batch: SDL's software renderer takes the
     * <em>renderer's</em> draw blend mode for {@code SDL_RenderTexture} and forces an opaque copy when
     * it is {@code SDL_BLENDMODE_NONE} — the state {@code SDL_CreateRenderer} starts in — so a blit that
     * skips this call copies the source's transparent pixels over the frame instead of blending them.
     * Upstream sets the blend function per draw for both paths
     * ({@code GLInterface::Blt} → {@code SetDrawMode}, GLInterface.cpp:1406-1421, and
     * {@code GLInterface::DrawTrianglesTex} → {@code SetDrawMode}, GLInterface.cpp:1596-1609).
     */
    private void applyDrawBlend(BlendMode theMode) {
        int aValue = blendModeValue(theMode);
        if (aValue == mLastGeometryBlend) {
            return;
        }
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, aValue);
        mLastGeometryBlend = aValue;
    }

    private static int blendModeValue(BlendMode mode) {
        return switch (mode) {
            case NORMAL -> SDLBlendMode.SDL_BLENDMODE_BLEND;
            case ADDITIVE -> SDLBlendMode.SDL_BLENDMODE_ADD;
            case NONE -> SDLBlendMode.SDL_BLENDMODE_NONE;
        };
    }

    @Override
    public void fillRect(float x, float y, float width, float height, int colorARGB) {
        if (width <= 0 || height <= 0) {
            return;
        }
        setDrawColor(colorARGB);
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, SDLBlendMode.SDL_BLENDMODE_BLEND);
        mLastGeometryBlend = SDLBlendMode.SDL_BLENDMODE_BLEND;
        mDst.set(x, y, width, height);
        SDLRender.SDL_RenderFillRect(mRenderer, mDst);
    }

    @Override
    public void drawLine(float x1, float y1, float x2, float y2, int colorARGB) {
        setDrawColor(colorARGB);
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, SDLBlendMode.SDL_BLENDMODE_BLEND);
        SDLRender.SDL_RenderLine(mRenderer, x1, y1, x2, y2);
    }

    /**
     * Sets the color used by the primitive draws (fills and lines).
     *
     * <p>The float setter is used on purpose: SDL3's integer {@code SDL_SetRenderDrawColor} drops the
     * alpha for blended primitives in both the software and the GPU backends (a half-transparent fill
     * comes out fully opaque), while {@code SDL_SetRenderDrawColorFloat} blends as documented. The
     * intro cutscene's dim band and every screen fade depend on it
     * (see {@code SdlRendererTest.translucentFillBlendsWithTheDestination}).
     */
    private void setDrawColor(int colorARGB) {
        SDLRender.SDL_SetRenderDrawColorFloat(
                mRenderer,
                ((colorARGB >>> 16) & 0xFF) / 255.0f,
                ((colorARGB >>> 8) & 0xFF) / 255.0f,
                (colorARGB & 0xFF) / 255.0f,
                ((colorARGB >>> 24) & 0xFF) / 255.0f);
    }

    @Override
    public void drawTriangles(
            long texture,
            float[] positionsUv,
            int[] colors,
            int vertexCount,
            int[] indices,
            int indexCount,
            BlendMode mode) {
        if (vertexCount <= 0 || indexCount <= 0) {
            return;
        }
        if (texture != 0) {
            applyTextureBlend(SDL_Texture.create(texture));
        }
        ByteBuffer vertexBuffer = vertexBuffer(vertexCount);
        SDL_Vertex.Buffer vertices = SDL_Vertex.create(MemoryUtil.memAddress(vertexBuffer), vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            SDL_Vertex vertex = vertices.get(i);
            vertex.position$().set(positionsUv[i * 4], positionsUv[i * 4 + 1]);
            int argb = colors[i];
            SDL_FColor color = vertex.color();
            color.r(((argb >>> 16) & 0xFF) / 255f);
            color.g(((argb >>> 8) & 0xFF) / 255f);
            color.b((argb & 0xFF) / 255f);
            color.a(((argb >>> 24) & 0xFF) / 255f);
            vertex.tex_coord().set(positionsUv[i * 4 + 2], positionsUv[i * 4 + 3]);
        }
        IntBuffer indicesBuffer = indicesBuffer(indexCount);
        indicesBuffer.clear();
        indicesBuffer.put(indices, 0, indexCount);
        indicesBuffer.flip(); // LWJGL passes the pointer at the buffer's current position

        applyDrawBlend(mode);
        SDL_Texture textureHandle = texture == 0 ? null : SDL_Texture.create(texture);
        if (!SDLRender.SDL_RenderGeometry(mRenderer, textureHandle, vertices, indicesBuffer)) {
            throw new IllegalStateException("SDL_RenderGeometry failed: " + SDLError.SDL_GetError());
        }
    }

    private ByteBuffer vertexBuffer(int vertexCount) {
        int bytes = vertexCount * SDL_Vertex.SIZEOF;
        if (mVertexBuffer == null || mVertexBuffer.capacity() < bytes) {
            if (mVertexBuffer != null) {
                MemoryUtil.memFree(mVertexBuffer);
            }
            mVertexBuffer = MemoryUtil.memAlloc(bytes);
        }
        return mVertexBuffer;
    }

    private IntBuffer indicesBuffer(int indexCount) {
        if (mIndexBuffer == null || mIndexBuffer.capacity() < indexCount) {
            if (mIndexBuffer != null) {
                MemoryUtil.memFree(mIndexBuffer);
            }
            mIndexBuffer = MemoryUtil.memAllocInt(indexCount);
        }
        return mIndexBuffer;
    }

    @Override
    public void setClipRect(int x, int y, int width, int height) {
        mClip.set(x, y, width, height);
        SDLRender.SDL_SetRenderClipRect(mRenderer, mClip);
    }

    @Override
    public void clearClipRect() {
        SDLRender.SDL_SetRenderClipRect(mRenderer, null);
    }

    @Override
    public void setBlendMode(BlendMode mode) {
        mBlendMode = mode;
    }

    /**
     * Binds the window drawable as the target, or the scene canvas when the renderer has one (see
     * {@link #sceneTarget()}), and sets the logical presentation to match.
     */
    @Override
    public void setRenderTarget(long texture) {
        if (texture == 0) {
            long aScene = sceneTarget();
            mCurrentTarget = 0; // 0 keeps meaning "the canvas the framework draws into"
            if (aScene != 0) {
                SDLRender.SDL_SetRenderTarget(mRenderer, SDL_Texture.create(aScene));
                // The canvas is drawn 1:1: scaling happens once, in compositeToOutput().
                SDLRender.SDL_SetRenderLogicalPresentation(
                        mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
                return;
            }
            SDLRender.SDL_SetRenderTarget(mRenderer, null);
            // No canvas texture means the window is drawn into directly, with SDL's logical presentation
            // letterboxing the 800x600 screen space into it (upstream's behaviour, and the 1:1 case).
            if (mLogicalWidth > 0 && mLogicalHeight > 0) {
                SDLRender.SDL_SetRenderLogicalPresentation(
                        mRenderer, mLogicalWidth, mLogicalHeight, SDLRender.SDL_LOGICAL_PRESENTATION_LETTERBOX);
            }
            return;
        }
        mCurrentTarget = texture;
        SDLRender.SDL_SetRenderTarget(mRenderer, SDL_Texture.create(texture));
        // Offscreen targets are drawn in target pixels: disable logical presentation entirely.
        SDLRender.SDL_SetRenderLogicalPresentation(
                mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
    }

    /** Currently bound render target (0 = the screen canvas, which may be the scene texture). */
    public long currentRenderTarget() {
        return mCurrentTarget;
    }

    /**
     * The offscreen canvas texture the framework draws into, created lazily on the first frame whose
     * window drawable differs from the logical canvas and then kept for the renderer's lifetime.
     *
     * <p>Equal sizes mean the window can be drawn into directly: that is the port's behaviour to date
     * and it must stay byte for byte identical (no texture, no copy, no scaling), so {@code 0} is
     * returned and nothing is created - the decision is re-made on every call while it is "no canvas".
     * Once a canvas exists it is never released, so a window resized back to exactly the canvas size
     * keeps using the canvas it already has instead of rebuilding it on every size change.
     *
     * <p>This is what allows the window to be created with {@code SDL_WINDOW_HIGH_PIXEL_DENSITY}: the
     * framework's 800x600 screen space is rendered 1:1 into the canvas and scaled once when the frame is
     * composited into the (larger) drawable, so no individual primitive is ever scaled - upstream does
     * the same with {@code GLInterface::mScreenImage} plus the blit in {@code GLInterface::Redraw}
     * (the rotated/skewed quads that SDL's per-primitive logical-presentation scaling used to distort are
     * therefore unaffected).
     */
    private long sceneTarget() {
        if (mSceneDecided) {
            return mSceneTexture;
        }
        if (mLogicalWidth <= 0 || mLogicalHeight <= 0) {
            return 0;
        }
        refreshOutputSize();
        if (mOutputWidth <= 0 || mOutputHeight <= 0
                || (mOutputWidth == mLogicalWidth && mOutputHeight == mLogicalHeight)) {
            return 0;
        }
        long aTexture = createRenderTargetTexture(mLogicalWidth, mLogicalHeight);
        // The canvas is composited with a copy, never blended into the window.
        SDLRender.SDL_SetTextureBlendMode(SDL_Texture.create(aTexture), SDLBlendMode.SDL_BLENDMODE_NONE);
        // A fresh render target texture holds undefined pixels; start from the same black the window
        // would have shown, so the frames before the game paints anything are not garbage.
        SDLRender.SDL_SetRenderTarget(mRenderer, SDL_Texture.create(aTexture));
        SDLRender.SDL_SetRenderLogicalPresentation(
                mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
        setDrawColor(0xFF000000);
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, SDLBlendMode.SDL_BLENDMODE_NONE);
        mLastGeometryBlend = SDLBlendMode.SDL_BLENDMODE_NONE;
        SDLRender.SDL_RenderClear(mRenderer);
        mSceneTexture = aTexture;
        mSceneDecided = true;
        return aTexture;
    }

    /**
     * The scene canvas texture handle, {@code 0} while the renderer draws straight into the window
     * (before the first frame that needs a canvas, or in the 1:1 case). Diagnostics and tests.
     */
    long sceneTargetHandle() {
        return mSceneTexture;
    }

    /**
     * Composites the frame into the window: clears it, blits the canvas into the letterboxed rectangle
     * and leaves the canvas bound as the drawing target again.
     *
     * <p>Without a canvas (1:1) the frame was drawn into the window itself and there is nothing to do.
     */
    @Override
    public void compositeToOutput() {
        long aScene = sceneTarget();
        if (aScene == 0) {
            return;
        }
        refreshOutputSize();
        int[] aDst = letterboxRect(mLogicalWidth, mLogicalHeight, mOutputWidth, mOutputHeight, mDstRect);
        SDL_Texture aSceneTexture = SDL_Texture.create(aScene);

        SDLRender.SDL_SetRenderTarget(mRenderer, null);
        SDLRender.SDL_SetRenderLogicalPresentation(
                mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
        // The window is the bound target now, which is the only state in which the software renderer
        // reports its window framebuffer surface rather than the canvas: that surface is what this
        // composite and the present actually write into.
        checkRendererOutputMatchesWindow();
        // The black bars are drawn here, not left to SDL's letterbox fill: the whole window is cleared
        // and only the canvas rectangle is written over it.
        setDrawColor(0xFF000000);
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, SDLBlendMode.SDL_BLENDMODE_NONE);
        mLastGeometryBlend = SDLBlendMode.SDL_BLENDMODE_NONE;
        SDLRender.SDL_RenderClear(mRenderer);

        // Nearest only for whole-number magnifications (2x, 3x: every source pixel becomes an n x n
        // block); anything else - fractional magnification, or shrinking - is resampled linearly so the
        // error is spread evenly instead of some source pixels being copied twice and others not at all.
        SDLRender.SDL_SetTextureScaleMode(aSceneTexture,
                integerScale(mLogicalWidth, mLogicalHeight, aDst)
                        ? SDLSurface.SDL_SCALEMODE_NEAREST : SDLSurface.SDL_SCALEMODE_LINEAR);
        mDst.set(aDst[0], aDst[1], aDst[2], aDst[3]);
        if (!SDLRender.SDL_RenderTexture(mRenderer, aSceneTexture, null, mDst)) {
            throw new IllegalStateException("SDL_RenderTexture(scene canvas) failed: " + SDLError.SDL_GetError());
        }

        // The framework's "0 = screen" binding is still meant to hold and the next frame draws into the
        // canvas again: bind it back rather than leaving the window current.
        setRenderTarget(0);
    }

    /**
     * The rectangle the canvas is presented into: scaled by the largest factor that fits both axes,
     * centred, in whole pixels.
     *
     * <p>The one piece of presentation geometry there is - the composite's destination and the mouse
     * mapping both come from it, so a screenshot and a click cannot disagree about where the canvas is.
     * Callers pass the space the rectangle is wanted in: window <em>points</em> for the mouse
     * ({@code SDL_GetWindowSize}) and window <em>pixels</em> for the composite and the read-back.
     */
    static int[] letterboxRect(int canvasWidth, int canvasHeight, int outputWidth, int outputHeight) {
        return letterboxRect(canvasWidth, canvasHeight, outputWidth, outputHeight, new int[4]);
    }

    /** {@link #letterboxRect(int, int, int, int)} into a caller-owned array (the frame path reuses one). */
    private static int[] letterboxRect(int canvasWidth, int canvasHeight, int outputWidth, int outputHeight,
            int[] dest) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) {
            dest[0] = 0;
            dest[1] = 0;
            dest[2] = Math.max(0, outputWidth);
            dest[3] = Math.max(0, outputHeight);
            return dest;
        }
        double aScale = Math.min(outputWidth / (double) canvasWidth, outputHeight / (double) canvasHeight);
        int aWidth = Math.max(1, (int) Math.round(canvasWidth * aScale));
        int aHeight = Math.max(1, (int) Math.round(canvasHeight * aScale));
        dest[0] = (outputWidth - aWidth) / 2;
        dest[1] = (outputHeight - aHeight) / 2;
        dest[2] = aWidth;
        dest[3] = aHeight;
        return dest;
    }

    /**
     * Whether presenting {@code canvasWidth x canvasHeight} into {@code rect} is a whole-number
     * magnification in both axes (1x, 2x, 3x ...), the case where nearest filtering reproduces the
     * canvas exactly. Fractional magnifications (800x600 in a 1230x923 drawable is 1.5375x) and any
     * shrink are not, and are filtered linearly.
     */
    static boolean integerScale(int canvasWidth, int canvasHeight, int[] rect) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || rect == null || rect.length < 4
                || rect[2] <= 0 || rect[3] <= 0) {
            return false;
        }
        double aScaleX = rect[2] / (double) canvasWidth;
        double aScaleY = rect[3] / (double) canvasHeight;
        return isWholeScale(aScaleX) && isWholeScale(aScaleY) && Math.abs(aScaleX - aScaleY) < 1e-3;
    }

    private static boolean isWholeScale(double theScale) {
        return theScale >= 1.0 - 1e-3 && Math.abs(theScale - Math.round(theScale)) <= 1e-3;
    }

    /**
     * The drawable size of the window in pixels - what the window shows and what a read-back of it
     * reads.
     *
     * <p>Asked of the window rather than of the renderer, because for SDL's software renderer the
     * renderer's answer follows the <em>bound target</em> ({@code SW_SetRenderTarget} repoints
     * {@code SW_RenderData.surface}, which {@code SW_GetOutputSize} reports), so it is the scene canvas
     * whenever one is bound. The window's own drawable is the size a frame is presented at and the size
     * the composite's letterbox geometry is measured against.
     *
     * <p>That the software renderer's window framebuffer surface can disagree with it is checked
     * separately, where the window is the bound target: {@link #checkRendererOutputMatchesWindow()}.
     */
    private void refreshOutputSize() {
        if (mWindow == 0) {
            return;
        }
        if (SDLVideo.SDL_GetWindowSizeInPixels(mWindow, mSizeA, mSizeB)) {
            mOutputWidth = mSizeA.get(0);
            mOutputHeight = mSizeB.get(0);
        }
    }

    /**
     * Reports the one error state that silently ruins a frame: SDL's software renderer drawing into a
     * window framebuffer surface that is not the window's drawable any more.
     *
     * <p>That renderer captures the window's framebuffer when it is created ({@code SW_CreateRenderer})
     * and re-reads it only when a {@code SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED} reaches the window-event
     * watch SDL installs for the renderer once it exists; a window whose size was still pending at that
     * moment - on Wayland the compositor's configure of a fullscreen window - leaves it with the surface
     * of the requested size for the rest of the process. Every frame is then composited with window
     * geometry into the smaller surface (clipped: measured on Linux/Wayland as an 800x600 surface in a
     * 1464x885 window) and presented as that surface, and a read-back of the window returns only the
     * surface. {@code SdlPlatform} settles the window size before the renderer exists, which is the fix;
     * this is the report that keeps a machine where that did not help from being silent about it.
     *
     * <p>Only meaningful while the window itself is the bound target (with a canvas bound the renderer
     * reports the canvas), which is the state both callers are in.
     */
    private void checkRendererOutputMatchesWindow() {
        if (mWindow == 0 || mOutputMismatchLogged) {
            return;
        }
        int aOutputWidth;
        int aOutputHeight;
        if (!SDLRender.SDL_GetRenderOutputSize(mRenderer, mSizeA, mSizeB)) {
            return;
        }
        aOutputWidth = mSizeA.get(0);
        aOutputHeight = mSizeB.get(0);
        refreshOutputSize();
        if (aOutputWidth <= 0 || mOutputWidth <= 0 || (aOutputWidth == mOutputWidth && aOutputHeight == mOutputHeight)) {
            return;
        }
        mOutputMismatchLogged = true;
        cn.enaium.fabricpvz.portable.sexy.Common.logErrorLn(
                "the renderer's output is {}x{} but the window drawable is {}x{}: the frame is composited "
                        + "into the renderer's output, so it is clipped and the window shows that surface "
                        + "(SDL's software renderer keeps the window framebuffer surface it captured when it "
                        + "was created)",
                aOutputWidth, aOutputHeight, mOutputWidth, mOutputHeight);
    }

    /**
     * Width of the window's drawable in pixels - what a present fills and what a read-back of the
     * window reads ({@link #readPresentedPixels}' read-back width).
     */
    public int presentedWidth() {
        refreshOutputSize();
        return mOutputWidth;
    }

    /** Height of the window's drawable in pixels; see {@link #presentedWidth()}. */
    public int presentedHeight() {
        refreshOutputSize();
        return mOutputHeight;
    }

    @Override
    public void clear(int colorARGB) {
        setDrawColor(colorARGB);
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, SDLBlendMode.SDL_BLENDMODE_NONE);
        mLastGeometryBlend = SDLBlendMode.SDL_BLENDMODE_NONE;
        SDLRender.SDL_RenderClear(mRenderer);
    }

    /**
     * Reads back a texture's pixels: {@code texture} has to be a render target
     * ({@link #createRenderTargetTexture}), which is what {@code GLImage} creates for an image used as
     * an offscreen draw target.
     *
     * <p>PORT-NOTE: SDL refuses {@code SDL_SetRenderTarget} for a texture that was not created with
     * {@code SDL_TEXTUREACCESS_TARGET} ("Texture not created with SDL_TEXTUREACCESS_TARGET"). The bind
     * is checked here and its refusal is propagated, because a read-back of an ordinary
     * ({@code SDL_TEXTUREACCESS_STATIC}) image texture would otherwise return whatever target was
     * still bound - the screen canvas or the window - clipped into the requested rectangle. That is
     * upstream's {@code GLInterface::RecoverBits} path ({@code GLImage.getBits()} after the CPU bits
     * were purged), which upstream serves with a real framebuffer object and {@code glReadPixels};
     * SDL's 2D render API has no equivalent for a static texture, so a static texture is refused
     * instead of silently yielding the previously bound target's pixels. Measured on macOS:
     * {@code SDL_SetRenderTarget(static 1400x600)} returns false and, before this check, the following
     * read-back was the 800x600 canvas that was bound before it.
     *
     * @throws IllegalStateException when {@code texture} is not a draw target and SDL refuses to bind
     *                               it, naming the requested rectangle and the SDL error
     */
    @Override
    public void readTexturePixels(long texture, int width, int height, int[] dest) {
        long previousTarget = mCurrentTarget;
        if (texture == 0) {
            // 0 keeps meaning the framework's "screen" binding: the scene canvas, or the window.
            setRenderTarget(0);
        } else if (!SDLRender.SDL_SetRenderTarget(mRenderer, SDL_Texture.create(texture))) {
            // The failed bind left some other target current: put the previous binding back before
            // reporting, so the renderer is not left in an unintended state.
            setRenderTarget(previousTarget);
            throw new IllegalStateException("SDL_SetRenderTarget failed for a " + width + "x" + height
                    + " texture read-back (the texture was not created as a draw target): "
                    + SDLError.SDL_GetError());
        } else {
            mCurrentTarget = texture;
            // Offscreen targets are read in target pixels: disable logical presentation entirely.
            SDLRender.SDL_SetRenderLogicalPresentation(
                    mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
        }
        try {
            readCurrentTargetPixels(width, height, dest, false);
        } finally {
            setRenderTarget(previousTarget);
        }
    }

    /**
     * Reads back what the window currently shows - the composite of the frame, so call it after {@link
     * #compositeToOutput()} and before {@link #present()}: after a present the back buffer is undefined.
     *
     * <p>The real window is bound for the read even while a scene canvas exists (the framework's
     * {@code 0 = screen} target is the canvas for <em>drawing</em>), and the previous binding is restored
     * before returning.
     *
     * @param width  pixels per row to read (the window's drawable, see {@link #presentedWidth()})
     * @param height rows to read
     * @param dest   {@code width*height} pixels
     * @return the number of pixels read ({@code width * height})
     * @throws IllegalStateException when the renderer's output is smaller than the requested rectangle
     *                               (a short read-back would otherwise leave the rest of {@code dest}
     *                               untouched instead of reporting that the output does not match)
     */
    public int readPresentedPixels(int width, int height, int[] dest) {
        long previousTarget = mCurrentTarget;
        SDLRender.SDL_SetRenderTarget(mRenderer, null);
        SDLRender.SDL_SetRenderLogicalPresentation(
                mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
        checkRendererOutputMatchesWindow();
        try {
            readCurrentTargetPixels(width, height, dest, true);
        } finally {
            setRenderTarget(previousTarget);
        }
        return Math.max(0, width) * Math.max(0, height);
    }

    /**
     * Copies the pixels of the currently bound target into {@code dest}.
     *
     * @param requireCoverage when true, the read-back must cover the whole requested rectangle and a
     *                        shorter one is refused; see {@link #readPresentedPixels}. Left false for a
     *                        texture read-back, whose caller asks for the texture's own size.
     */
    private void readCurrentTargetPixels(int width, int height, int[] dest, boolean requireCoverage) {
        if (width <= 0 || height <= 0) {
            return;
        }
        SDL_Surface read = SDLRender.SDL_RenderReadPixels(mRenderer, null);
        if (read == null) {
            throw new IllegalStateException("SDL_RenderReadPixels failed: " + SDLError.SDL_GetError());
        }
        if (requireCoverage && (read.w() < width || read.h() < height)) {
            // A read-back smaller than the request would leave the rest of `dest` untouched (the blit
            // below clips into the requested rectangle), which is exactly how a stale software-renderer
            // surface turned into a window dump full of zero pixels instead of an error: the window was
            // 1464x885, SDL's software renderer read its 800x600 window framebuffer back, and the
            // difference stayed at the array's initial value. A window read-back that does not cover the
            // window is a refusal, not a partial image.
            int aReadWidth = read.w();
            int aReadHeight = read.h();
            SDLSurface.SDL_DestroySurface(read);
            throw new IllegalStateException("SDL_RenderReadPixels returned " + aReadWidth + "x" + aReadHeight
                    + " for a " + width + "x" + height + " read-back: the renderer's output is smaller than the"
                    + " requested rectangle");
        }
        SDL_Surface converted = SDLSurface.SDL_CreateSurface(width, height, SDLPixels.SDL_PIXELFORMAT_ARGB8888);
        try {
            SDLSurface.SDL_BlitSurface(read, null, converted, null);
            SDLSurface.SDL_LockSurface(converted);
            try {
                ByteBuffer pixels = converted.pixels();
                int pitch = converted.pitch();
                for (int row = 0; row < height; row++) {
                    int rowOffset = row * pitch;
                    int destOffset = row * width;
                    for (int col = 0; col < width; col++) {
                        dest[destOffset + col] = pixels.getInt(rowOffset + col * 4);
                    }
                }
            } finally {
                SDLSurface.SDL_UnlockSurface(converted);
            }
        } finally {
            SDLSurface.SDL_DestroySurface(converted);
            SDLSurface.SDL_DestroySurface(read);
        }
    }

    @Override
    public void present() {
        // SDL refuses to present while a render target is bound - "You can't present on a render target" -
        // and fails the call, which on a window that shows nothing at all is the difference between a
        // frame and a black window. The canvas therefore has to be released for the present. Without a
        // canvas (the 1:1 case) the framework drew into the window itself and there is nothing to release.
        if (mSceneTexture == 0) {
            presentNow();
            return;
        }
        SDLRender.SDL_SetRenderTarget(mRenderer, null);
        SDLRender.SDL_SetRenderLogicalPresentation(
                mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
        presentNow();
        // Back to the canvas: the framework's binding dedup (GLImage.sBoundTarget) still believes the
        // screen - which is the canvas - is current, so the next frame's first draw has to land there.
        setRenderTarget(0);
    }

    /**
     * {@code SDL_RenderPresent}, reporting a failure once.
     *
     * <p>A failed present is otherwise invisible - the window simply keeps showing whatever it showed
     * before - and the call has real failure modes (no drawable while the window is minimised, a
     * fullscreen transition, a render target that was bound by mistake). One line in the log is what
     * turns "the window is empty and nothing says why" into a diagnosable report, and repeating it every
     * frame at 100 Hz would drown the log, so it is said once per renderer.
     */
    private void presentNow() {
        boolean aOk = SDLRender.SDL_RenderPresent(mRenderer);
        mLastPresentSucceeded = aOk;
        if (!aOk && !mPresentErrorLogged) {
            mPresentErrorLogged = true;
            cn.enaium.fabricpvz.portable.sexy.Common.logErrorLn("SDL_RenderPresent failed: {}",
                    SDLError.SDL_GetError());
        }
    }

    /**
     * Whether the last {@link #present()} was accepted by SDL.
     *
     * <p>Diagnostic, and the reason this method exists: a refused present is silent - SDL returns false,
     * the window keeps showing the previous frame and nothing else fails - so a headless test or the
     * window-dump harness needs a way to ask. The real mistake this catches: presenting while a render
     * target is bound, which is what the scene canvas is.
     */
    public boolean lastPresentSucceeded() {
        return mLastPresentSucceeded;
    }

    @Override
    public void setLogicalSize(int width, int height) {
        mLogicalWidth = width;
        mLogicalHeight = height;
        if (mSceneTexture != 0) {
            // Drawing goes into the fixed-size canvas: it stays 1:1 whatever the logical size says.
            SDLRender.SDL_SetRenderLogicalPresentation(
                    mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
            return;
        }
        SDLRender.SDL_SetRenderLogicalPresentation(mRenderer, width, height, SDLRender.SDL_LOGICAL_PRESENTATION_LETTERBOX);
    }

    /**
     * The <em>window-space</em> rectangle the canvas is presented into, in window coordinates (points) -
     * the space mouse events arrive in.
     *
     * <p>SDL's own query is not used for this: {@code SDL_GetRenderLogicalPresentationRect} reports
     * output <em>pixels</em> (so on a high-density display the mapping would be off by the density
     * factor) and is empty while an offscreen target is bound. The rectangle is computed from the canvas
     * and the window size with {@link #letterboxRect}, the same function the composite uses, so drawing
     * and mouse mapping cannot disagree. Without a window (software renderer) the surface size is used,
     * which is density 1 and therefore already in the space the events are in.
     */
    @Override
    public int[] logicalPresentationRect() {
        if (mLogicalWidth <= 0 || mLogicalHeight <= 0) {
            return RendererBackend.super.logicalPresentationRect();
        }
        if (mWindow != 0 && SDLVideo.SDL_GetWindowSize(mWindow, mSizeA, mSizeB)) {
            return letterboxRect(mLogicalWidth, mLogicalHeight, mSizeA.get(0), mSizeB.get(0));
        }
        refreshOutputSize();
        if (mOutputWidth <= 0 || mOutputHeight <= 0) {
            return RendererBackend.super.logicalPresentationRect();
        }
        return letterboxRect(mLogicalWidth, mLogicalHeight, mOutputWidth, mOutputHeight);
    }

    @Override
    public int logicalWidth() {
        return mLogicalWidth;
    }

    @Override
    public int logicalHeight() {
        return mLogicalHeight;
    }

    @Override
    public int renderOutputWidth() {
        SDLRender.SDL_GetCurrentRenderOutputSize(mRenderer, mSizeA, mSizeB);
        return mSizeA.get(0);
    }

    @Override
    public int renderOutputHeight() {
        SDLRender.SDL_GetCurrentRenderOutputSize(mRenderer, mSizeA, mSizeB);
        return mSizeB.get(0);
    }

    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        mClosed = true;
        if (mSceneTexture != 0) {
            SDLRender.SDL_DestroyTexture(SDL_Texture.create(mSceneTexture));
            mSceneTexture = 0;
        }
        SDLRender.SDL_DestroyRenderer(mRenderer);
        if (mUploadBuffer != null) {
            MemoryUtil.memFree(mUploadBuffer);
            mUploadBuffer = null;
        }
        if (mVertexBuffer != null) {
            MemoryUtil.memFree(mVertexBuffer);
            mVertexBuffer = null;
        }
        if (mIndexBuffer != null) {
            MemoryUtil.memFree(mIndexBuffer);
            mIndexBuffer = null;
        }
        mClip.close();
        mReadRect.close();
        mSrc.close();
        mDst.close();
        mCenter.close();
        MemoryUtil.memFree(mSizeA);
        MemoryUtil.memFree(mSizeB);
    }
}
