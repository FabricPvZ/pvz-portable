package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLBlendMode;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLRender;
import org.lwjgl.sdl.SDLSurface;
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

    private SdlRenderer(long renderer, boolean software) {
        this.mRenderer = renderer;
        this.mSoftware = software;
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
        return new SdlRenderer(renderer, false);
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
        return new SdlRenderer(renderer, true);
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

    @Override
    public void setRenderTarget(long texture) {
        mCurrentTarget = texture;
        SDLRender.SDL_SetRenderTarget(mRenderer, texture == 0 ? null : SDL_Texture.create(texture));
        // Logical presentation is a screen-space convenience (800x600 letterboxed into the window).
        // Offscreen targets must be drawn 1:1, otherwise content would be scaled into the target.
        if (texture == 0) {
            if (mLogicalWidth > 0 && mLogicalHeight > 0) {
                SDLRender.SDL_SetRenderLogicalPresentation(
                        mRenderer, mLogicalWidth, mLogicalHeight, SDLRender.SDL_LOGICAL_PRESENTATION_LETTERBOX);
            }
        } else {
            // Offscreen targets are drawn in target pixels: disable logical presentation entirely.
            SDLRender.SDL_SetRenderLogicalPresentation(
                    mRenderer, 0, 0, SDLRender.SDL_LOGICAL_PRESENTATION_DISABLED);
        }
    }

    /** Currently bound render target (0 = screen). */
    public long currentRenderTarget() {
        return mCurrentTarget;
    }

    @Override
    public void clear(int colorARGB) {
        setDrawColor(colorARGB);
        SDLRender.SDL_SetRenderDrawBlendMode(mRenderer, SDLBlendMode.SDL_BLENDMODE_NONE);
        mLastGeometryBlend = SDLBlendMode.SDL_BLENDMODE_NONE;
        SDLRender.SDL_RenderClear(mRenderer);
    }

    @Override
    public void readTexturePixels(long texture, int width, int height, int[] dest) {
        long previousTarget = mCurrentTarget;
        setRenderTarget(texture);
        if (width <= 0 || height <= 0) {
            setRenderTarget(previousTarget);
            return;
        }
        SDL_Surface read = SDLRender.SDL_RenderReadPixels(mRenderer, null);
        if (read == null) {
            setRenderTarget(previousTarget);
            throw new IllegalStateException("SDL_RenderReadPixels failed: " + SDLError.SDL_GetError());
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
            setRenderTarget(previousTarget);
        }
    }

    @Override
    public void present() {
        SDLRender.SDL_RenderPresent(mRenderer);
    }

    @Override
    public void setLogicalSize(int width, int height) {
        mLogicalWidth = width;
        mLogicalHeight = height;
        SDLRender.SDL_SetRenderLogicalPresentation(mRenderer, width, height, SDLRender.SDL_LOGICAL_PRESENTATION_LETTERBOX);
    }

    /**
     * The window-space rectangle SDL presents the logical canvas into
     * ({@code SDL_GetRenderLogicalPresentationRect}, the analogue of upstream's
     * {@code mPresentationRect}). With {@code SDL_LOGICAL_PRESENTATION_LETTERBOX} this is smaller than
     * the window whenever the aspect ratio differs, and offset from the origin when it is wider.
     */
    @Override
    public int[] logicalPresentationRect() {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            SDL_FRect aRect = SDL_FRect.malloc(stack);
            // A renderer without a logical presentation reports an empty rect (SDL3 returns 0x0);
            // an empty rect would make the widget manager's remap divide by zero, so fall back.
            if (!SDLRender.SDL_GetRenderLogicalPresentationRect(mRenderer, aRect)
                    || aRect.w() <= 0 || aRect.h() <= 0) {
                return RendererBackend.super.logicalPresentationRect();
            }
            return new int[] {Math.round(aRect.x()), Math.round(aRect.y()),
                    Math.round(aRect.w()), Math.round(aRect.h())};
        }
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
