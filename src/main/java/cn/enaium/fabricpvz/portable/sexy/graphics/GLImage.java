package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.FRect;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;

import java.util.Arrays;

/**
 * Ported from src/SexyAppFramework/graphics/GLImage.h:36 (implementation
 * src/SexyAppFramework/graphics/GLImage.cpp:26-213).
 *
 * <p>Faithful port of {@code Sexy::GLImage}, the GPU-backed image. Everything it inherits from
 * {@link MemoryImage} is the CPU-side bookkeeping (the pixel bits, the change counter, the render
 * flags); every draw entry point is overridden to forward to the platform
 * {@link RendererBackend} instead of touching those bits, exactly as upstream's {@code GLImage}
 * forwards to {@code GLInterface}.
 *
 * <p>Upstream stores the {@code TextureData} handle in {@code MemoryImage::mRenderData}; this port
 * stores an opaque backend texture handle there and records which CPU change count it was uploaded
 * from in {@code mRenderDataBitsChangedCount} ({@code -1} = never uploaded). Textures are created
 * lazily on first use and refreshed when the source bits change (upstream's
 * {@code GLInterface::CreateImageTexture}).
 *
 * <p>PORT-NOTE: upstream holds a raw {@code GLInterface*}; here that is a {@link RendererBackend}
 * that may be {@code null} (e.g. before the app has a renderer). With a null backend every draw is
 * a no-op, mirroring the intent of upstream's "no GL interface" case without the crash.
 *
 * <p>PORT-NOTE: upstream takes a dynamic cast to {@code MemoryImage} in the draw paths
 * ({@code GLInterface::Blt}); the Java port instead checks {@code instanceof MemoryImage} and
 * no-ops for a plain {@link Image}, which cannot be textured.
 *
 * <p>PORT-NOTE: {@code PolyFill3D} is inherited unchanged from the base image and returns
 * {@code false}, matching this port's {@link RendererBackend}, which (like upstream's
 * {@code GLInterface} in this fork) exposes no polygon-fill entry point.
 */
public class GLImage extends MemoryImage {

    /** Upstream {@code GLImage::mGLInterface} (GLImage.h:41); may be {@code null}. */
    public RendererBackend mRendererBackend;

    /**
     * Marks the special "screen" render target produced by {@link #screen(int, int)}: it has no
     * texture of its own (the backend's target {@code 0} is the screen) and must never be uploaded.
     */
    private boolean mIsScreenTarget;

    /**
     * Set by the creator when this image is used as an offscreen draw target (upstream's
     * {@code TextureData} for render-target images). Such images are created with
     * {@link RendererBackend#createRenderTargetTexture(int, int)} instead of
     * {@link RendererBackend#createTexture(int, int, int[])} because a plain texture cannot be
     * drawn into. Documented on {@link #ensureTexture(MemoryImage)}.
     */
    public boolean mRenderTarget;

    /** Texture the current filter setting was last applied to (filter-setting dedup); -1 = none. */
    private long mFilterTexture = -1;
    /** Filter value last applied through {@link RendererBackend#setTextureFilter(long, boolean)}. */
    private boolean mFilterLinear;

    /** Most vertices a quad can grow to while being clipped by the four edges of a rect. */
    private static final int MAX_CLIP_VERTICES = 8;

    /**
     * Scratch for the {@code x, y, u, v} quad a clip-checked draw builds before deciding whether to
     * clip; reused so the frame loop (thousands of draws) allocates nothing on the unclipped path, and
     * sized for the {@link #MAX_CLIP_VERTICES} a clipped quad can grow to.
     */
    private final float[] mQuadUv = new float[MAX_CLIP_VERTICES * 4];

    /** Backend the render target was last bound on (bind dedup across screen/offscreen draws). */
    private static RendererBackend sBoundBackend;
    /** Texture handle last bound as the render target; -1 = none bound yet. */
    private static long sBoundTarget = -1;

    /** Upstream {@code GLImage()} (GLImage.cpp:28-34): picks up the app's renderer. */
    public GLImage() {
        this(SexyAppBase.current != null ? SexyAppBase.current.mRendererBackend : null);
    }

    /** Upstream {@code GLImage(GLInterface*)} (GLImage.cpp:36-42). */
    public GLImage(RendererBackend theBackend) {
        super();
        mRendererBackend = theBackend;
    }

    /**
     * The renderer this image draws through, resolved lazily.
     *
     * <p>Upstream {@code GLImage} draws through the process-global {@code gGLInterface}, which always
     * exists by the time an image is used. In this port the renderer is created with the window, and
     * the framework builds the screen image before that ({@code SexyAppBase.init} calls
     * {@code WidgetManager.ensureScreenImage} while {@code makeWindow} creates the renderer
     * afterwards), so an image may legitimately hold no backend yet. Resolving it on demand keeps
     * such an image usable instead of silently discarding every draw (failure mode: a black window).
     *
     * @return the backend to draw through, or {@code null} when there is no renderer at all
     */
    protected RendererBackend backend() {
        if (mRendererBackend != null) {
            return mRendererBackend;
        }
        // Any image built before the renderer exists (the screen image is the one the framework
        // definitely creates early) draws through the app's renderer as soon as there is one.
        return SexyAppBase.current != null ? SexyAppBase.current.mRendererBackend : null;
    }

    /**
     * Creates the screen render target: the image the framework draws the frame into.
     *
     * <p>PORT-NOTE: in this port the screen image is only a size carrier — the backend's target
     * {@code 0} is the real screen (see {@link RendererBackend#setRenderTarget(long)}, set by
     * {@link Graphics}), so this image's texture handle stays {@code 0} and it is allocated purely
     * so {@code getWidth()}/{@code getHeight()} describe the screen.
     *
     * @param width screen width in logical pixels
     * @param height screen height in logical pixels
     * @return a screen-target image
     */
    public static GLImage screen(int width, int height) {
        return screen(SexyAppBase.current != null ? SexyAppBase.current.mRendererBackend : null, width, height);
    }

    /**
     * Creates a screen render target bound to an explicit backend (used when the app/renderer is
     * not reachable through {@link SexyAppBase#current}, e.g. for tests).
     *
     * <p>Sets both {@code mIsScreenTarget} and {@link #mRenderTarget}: the screen is drawn into via
     * {@code new Graphics(image)}, and every image a factory returns as a draw target carries
     * {@code mRenderTarget} — even though, being the screen, {@link #ensureTexture(MemoryImage)}
     * still returns {@code 0} for it.
     */
    static GLImage screen(RendererBackend theBackend, int width, int height) {
        GLImage aScreen = new GLImage(theBackend);
        aScreen.create(width, height);
        aScreen.mIsScreenTarget = true;
        aScreen.mRenderTarget = true;
        return aScreen;
    }

    /** Upstream {@code GLImage::Check3D(GLImage*)} (GLImage.cpp:86-89): always textured. */
    @Override
    public boolean is3D() {
        return true;
    }

    @Override
    public void create(int theWidth, int theHeight) {
        // The handle is size-specific, so it must be destroyed before super.create changes the size
        // (upstream GLImage::Create only forwards because the GL interface recreates TextureData lazily).
        destroyTexture();
        super.create(theWidth, theHeight);
    }

    /**
     * Upstream {@code GLImage::PurgeBits} (GLImage.cpp:97-105) ending in
     * {@code MemoryImage::PurgeBits} (MemoryImage.cpp:1002-1028): mark the image purgeable, force the
     * bits to be materialized so the texture upload has a source, and only then let the base class
     * drop the CPU copy.
     *
     * <p>PORT-NOTE: upstream's base class refuses to free the bits while there is no render data to
     * hold them — under 3D acceleration it returns early when {@code mRenderData == nullptr},
     * deferring the purge until the texture exists. This port therefore returns without dropping
     * the bits when there is no backend or the image is the screen target, and otherwise forces the
     * upload via {@link #ensureTexture(MemoryImage)} before purging, so a never-drawn
     * {@code GLImage} (the ResourceManager {@code nobits} path) is not blanked.
     */
    @Override
    public void purgeBits() {
        mPurgeBits = true;

        commitBits();
        getBits();

        if (backend() == null || mIsScreenTarget) {
            // No render data can hold the pixels (upstream defers the purge until the texture exists).
            return;
        }

        // The texture must own the pixels before the CPU copy is dropped.
        ensureTexture(this);
        super.purgeBits();
    }

    /**
     * Ports upstream's GPU read-back branch of {@code MemoryImage::GetBits}
     * (MemoryImage.cpp:1106-1171, the {@code mRenderData == nullptr || ... || !RecoverBits(this)} case,
     * with {@code GLInterface::RecoverBits} at GLInterface.cpp:1337-1386): when the CPU bits were
     * purged but the texture still holds the pixels, they are read back from the GPU — and only
     * when the texture is known to be up to date (upstream asserts
     * {@code mBitsChangedCount == data->mBitsChangedCount}, GLInterface.cpp:1340-1343).
     *
     * <p>This must not bump {@link #mBitsChangedCount}/{@link #mBitsChanged}, otherwise
     * {@link #ensureTexture(MemoryImage)} would re-upload in a loop.
     */
    @Override
    public int[] getBits() {
        if (mBits == null && mRenderData != 0 && backend() != null
                && mRenderDataBitsChangedCount == mBitsChangedCount) {
            int aSize = mWidth * mHeight;
            mBits = new int[aSize + 1];
            backend().readTexturePixels(mRenderData, mWidth, mHeight, mBits);
            mBits[aSize] = MEMORYCHECK_ID;
            return mBits;
        }
        return super.getBits();
    }

    /**
     * Upstream {@code GLInterface::Remove3DData} / {@code GLImage::~GLImage} (GLInterface.cpp:1167-1177):
     * releases the GPU texture. There is no finalizer in this port, so this is the single place the
     * texture is freed (also called from {@link #create(int, int)} before reallocating).
     */
    @Override
    public void delete3DBuffers() {
        destroyTexture();
        super.delete3DBuffers();
    }

    private void destroyTexture() {
        if (backend() != null && mRenderData != 0) {
            backend().destroyTexture(mRenderData);
        }
        mRenderData = 0;
        mRenderDataBitsChangedCount = -1;
        mFilterTexture = -1;
    }

    /**
     * Returns the backend texture for the given image, uploading it on first use and re-uploading
     * it whenever the CPU bits changed since the last upload (upstream
     * {@code GLInterface::CreateImageTexture} + the {@code bitsChangedCount} staleness check).
     *
     * <p>Returns {@code 0} for the screen target (nothing to upload) and when there is no backend.
     *
     * <p>If the image has {@link #mRenderTarget} set it is created with
     * {@link RendererBackend#createRenderTargetTexture(int, int)} (blank, drawable), otherwise with
     * {@link RendererBackend#createTexture(int, int, int[])} and its pixels; both are refreshed with
     * {@link RendererBackend#updateTexture(long, int[], int, int)}, which is valid for either kind.
     *
     * @param theImage any {@link MemoryImage} (typically a {@link GLImage}) used as a texture source
     * @return the texture handle, or {@code 0}
     */
    public long ensureTexture(MemoryImage theImage) {
        if (backend() == null) {
            return 0;
        }
        if (theImage instanceof GLImage aGLImage && aGLImage.mIsScreenTarget) {
            return 0;
        }
        if (theImage.mRenderData == 0 || theImage.mRenderDataBitsChangedCount != theImage.mBitsChangedCount) {
            // mBits is width*height+1 long (the last word is the canary); upload only the pixels.
            int aPixelCount = theImage.mWidth * theImage.mHeight;
            if (theImage.mRenderData == 0) {
                if (theImage instanceof GLImage aTargetImage && aTargetImage.mRenderTarget) {
                    // A plain texture cannot be drawn into; render targets are created blank.
                    theImage.mRenderData = backend().createRenderTargetTexture(theImage.mWidth, theImage.mHeight);
                } else {
                    // No defensive copy: the backend reads exactly width*height words, and the bits array
                    // carries one extra canary word (a copy per re-upload of every animated image would be
                    // pure garbage in a 100 Hz loop).
                    theImage.mRenderData = backend().createTexture(theImage.mWidth, theImage.mHeight,
                            theImage.getBits());
                }
            } else {
                // updateTexture is valid for both static and render-target textures (SDL_UpdateTexture).
                backend().updateTexture(theImage.mRenderData, theImage.getBits(),
                        theImage.mWidth, theImage.mHeight);
            }
            theImage.mRenderDataBitsChangedCount = theImage.mBitsChangedCount;
        }
        return theImage.mRenderData;
    }

    /**
     * Applies the texture filtering mode, skipping the call when the same {@code (texture, linear)}
     * pair was already applied (the framework toggles nearest/linear frequently).
     */
    private void applyFilter(long theTexture, boolean theLinear) {
        if (backend() == null || (mFilterTexture == theTexture && mFilterLinear == theLinear)) {
            return;
        }
        backend().setTextureFilter(theTexture, theLinear);
        mFilterTexture = theTexture;
        mFilterLinear = theLinear;
    }

    /**
     * Binds this image as the backend's render target when it is a draw target.
     *
     * <p>Upstream's {@code GLInterface} always drew into the currently bound framebuffer, so binding
     * the destination here restores those semantics: the screen image binds handle {@code 0} (the
     * default framebuffer) and an offscreen draw target binds its own texture. A plain image that is
     * not a draw target leaves the backend's current target untouched. The last (backend, handle)
     * pair is cached, so repeated draws into the same target issue no redundant
     * {@link RendererBackend#setRenderTarget(long)}; the pair is keyed on the backend instance so a
     * frame that mixes screen and offscreen draws stays correct.
     */
    private void bindTarget() {
        if (backend() == null) {
            return;
        }
        long aHandle = mIsScreenTarget ? 0 : (mRenderTarget ? ensureTexture(this) : -1);
        if (aHandle < 0) {
            // A non-drawable image handed to Graphics: leave the current target alone, do not guess.
            return;
        }
        if (sBoundBackend == backend() && sBoundTarget == aHandle) {
            return;
        }
        backend().setRenderTarget(aHandle);
        sBoundBackend = backend();
        sBoundTarget = aHandle;
    }

    /** Maps the framework draw mode to the backend blend mode (upstream {@code SetDrawMode}). */
    private static RendererBackend.BlendMode blendMode(int theDrawMode) {
        if (theDrawMode == Graphics.DRAWMODE_ADDITIVE) {
            return RendererBackend.BlendMode.ADDITIVE;
        }
        return RendererBackend.BlendMode.NORMAL;
    }

    @Override
    public void fillRect(Rect theRect, Color theColor, int theDrawMode) {
        bindTarget();
        if (backend() == null) {
            return;
        }
        backend().setBlendMode(blendMode(theDrawMode));
        backend().fillRect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight, theColor.toInt());
    }

    /**
     * Overridden so the target is bound before the base class writes the CPU bits: keeping the two
     * consistent means the next {@link #bindTarget()} re-uploads (the change counter moved).
     */
    @Override
    public void clearRect(Rect theRect) {
        bindTarget();
        super.clearRect(theRect);
    }

    @Override
    public void drawLine(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor, int theDrawMode) {
        bindTarget();
        if (backend() == null) {
            return;
        }
        backend().setBlendMode(blendMode(theDrawMode));
        backend().drawLine((float) theStartX, (float) theStartY, (float) theEndX, (float) theEndY, theColor.toInt());
    }

    /** Upstream {@code GLImage::DrawLineAA} (GLImage.cpp:141-151) forwards to {@code DrawLine}. */
    @Override
    public void drawLineAA(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor, int theDrawMode) {
        bindTarget();
        drawLine(theStartX, theStartY, theEndX, theEndY, theColor, theDrawMode);
    }

    @Override
    public void blt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
        bindTarget();
        theImage.mDrawn = true;
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }
        commitBits();

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, linearFilter);
        backend().setBlendMode(blendMode(theDrawMode));
        backend().drawTexture(aTexture, theSrcRect.mX, theSrcRect.mY, theSrcRect.mWidth, theSrcRect.mHeight,
                theX, theY, theSrcRect.mWidth, theSrcRect.mHeight, 0, 0, 0, RendererBackend.FLIP_NONE, theColor.toInt());
    }

    /** Writes one {@code x, y, u, v} vertex of a scratch quad. */
    private static void setQuadVertex(float[] theQuad, int theIndex, float theX, float theY, float theU, float theV) {
        theQuad[theIndex * 4] = theX;
        theQuad[theIndex * 4 + 1] = theY;
        theQuad[theIndex * 4 + 2] = theU;
        theQuad[theIndex * 4 + 3] = theV;
    }

    /** Writes one corner of a rotated quad; the corner is rotated about the destination centre. */
    private static void setRotatedQuadVertex(float[] theQuad, int theIndex, float theRelativeX, float theRelativeY,
                                             float theCenterX, float theCenterY, float theCos, float theSin,
                                             float theU, float theV) {
        setQuadVertex(theQuad, theIndex,
                theCenterX + theRelativeX * theCos - theRelativeY * theSin,
                theCenterY + theRelativeX * theSin + theRelativeY * theCos, theU, theV);
    }

    /**
     * Whether all four corners of a scratch quad ({@code x, y, u, v} per vertex) lie inside
     * {@code theClipRect}. A corner exactly on the far edge counts as inside: a quad that only reaches
     * the clip's edge covers the same pixels whether it is clipped or not.
     */
    private static boolean isQuadInsideClip(float[] theQuad, Rect theClipRect) {
        float aLeft = theClipRect.mX;
        float aTop = theClipRect.mY;
        float aRight = theClipRect.mX + theClipRect.mWidth;
        float aBottom = theClipRect.mY + theClipRect.mHeight;
        for (int i = 0; i < 4; i++) {
            float aX = theQuad[i * 4];
            float aY = theQuad[i * 4 + 1];
            if (aX < aLeft || aX > aRight || aY < aTop || aY > aBottom) {
                return false;
            }
        }
        return true;
    }

    /**
     * Upstream {@code GLInterface::DrawPolyClipped} (GLInterface.cpp:897-931) as
     * {@code TextureData::BltTransformed} (GLInterface.cpp:943-1000) uses it: clips a textured quad by
     * the four edges of {@code theClipRect} and draws the result as a triangle fan.
     *
     * <p>One Sutherland–Hodgman pass per edge; like upstream's {@code PointClipper} pair ({@code less}
     * on the left/top edge, {@code greater_equal} on the right/bottom one) the kept region is
     * {@code [left, right) x [top, bottom)}. Each vertex carries {@code x, y, u, v} and every component
     * is interpolated at an intersection, so a clipped draw samples exactly the texels the unclipped one
     * would have.
     *
     * <p>The fan goes through {@link RendererBackend#drawTriangles} — the primitive the backend already
     * expands a textured quad into ({@code SdlRenderer.drawTexturedQuadGpu}) — with the same coordinate
     * model (quad corners, not pixel centres) and normalised u/v, so clipping only ever removes the part
     * outside the rect.
     *
     * @param theQuad 4 quad vertices of {@code x, y, u, v}; also the clipper's input scratch, so it must
     *                hold {@link #MAX_CLIP_VERTICES} vertices
     * @return false when the quad lies entirely outside the clip (nothing drawn)
     */
    private boolean drawClippedQuad(long theTexture, float[] theQuad, Rect theClipRect, int theColorArgb,
                                    RendererBackend.BlendMode theMode) {
        float[] aIn = theQuad;
        float[] aOut = new float[MAX_CLIP_VERTICES * 4];
        int aCount = 4;
        for (int aEdge = 0; aEdge < 4; aEdge++) {
            boolean aKeepGreater = aEdge < 2;
            int aComponent = aEdge & 1;
            float aLimit = switch (aEdge) {
                case 0 -> theClipRect.mX;
                case 1 -> theClipRect.mY;
                case 2 -> theClipRect.mX + theClipRect.mWidth;
                default -> theClipRect.mY + theClipRect.mHeight;
            };
            int aClippedCount = 0;
            for (int i = 0; i < aCount; i++) {
                int j = i + 1 == aCount ? 0 : i + 1;
                float aValue = aIn[i * 4 + aComponent];
                float aNextValue = aIn[j * 4 + aComponent];
                boolean aInside = aKeepGreater ? aValue >= aLimit : aValue < aLimit;
                boolean aNextInside = aKeepGreater ? aNextValue >= aLimit : aNextValue < aLimit;
                if (aInside) {
                    System.arraycopy(aIn, i * 4, aOut, aClippedCount * 4, 4);
                    aClippedCount++;
                }
                if (aInside != aNextInside) {
                    float aFraction = (aLimit - aValue) / (aNextValue - aValue);
                    for (int c = 0; c < 4; c++) {
                        aOut[aClippedCount * 4 + c] = aIn[i * 4 + c]
                                + (aIn[j * 4 + c] - aIn[i * 4 + c]) * aFraction;
                    }
                    aClippedCount++;
                }
            }
            float[] aSwap = aIn;
            aIn = aOut;
            aOut = aSwap;
            aCount = aClippedCount;
            if (aCount == 0) {
                return false;
            }
        }
        if (aCount < 3) {
            return false;
        }

        int[] aColors = new int[aCount];
        Arrays.fill(aColors, theColorArgb);
        int[] aIndices = new int[(aCount - 2) * 3];
        for (int i = 1; i <= aCount - 2; i++) {
            aIndices[(i - 1) * 3] = 0;
            aIndices[(i - 1) * 3 + 1] = i;
            aIndices[(i - 1) * 3 + 2] = i + 1;
        }
        backend().setBlendMode(theMode);
        backend().drawTriangles(theTexture, aIn, aColors, aCount, aIndices, aIndices.length, theMode);
        return true;
    }

    /**
     * Upstream {@code GLImage::BltF} (GLImage.cpp:153-172): the destination is the source size at
     * {@code (theX, theY)}, drawn only when it intersects {@code theClipRect}.
     *
     * <p>Upstream sends a destination the clip only partly covers through
     * {@code GLInterface::BltClipF}, which clips the quad inside the transform pipeline
     * (GLImage.cpp:150-165); this port now clips it through {@link #drawClippedQuad} as well, instead of
     * drawing the whole destination and letting it spill outside the clip.
     */
    @Override
    public void bltF(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode) {
        bindTarget();
        theImage.mDrawn = true;
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }

        FRect aClip = new FRect(theClipRect.mX, theClipRect.mY, theClipRect.mWidth, theClipRect.mHeight);
        FRect aDest = new FRect(theX, theY, theSrcRect.mWidth, theSrcRect.mHeight);
        FRect anIntersect = aDest.intersection(aClip);
        if (anIntersect.mWidth <= 0 || anIntersect.mHeight <= 0) {
            return;
        }

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, true);
        RendererBackend.BlendMode aMode = blendMode(theDrawMode);

        if (anIntersect.mWidth != aDest.mWidth || anIntersect.mHeight != aDest.mHeight) {
            float u0 = (float) theSrcRect.mX / theImage.mWidth;
            float v0 = (float) theSrcRect.mY / theImage.mHeight;
            float u1 = (float) (theSrcRect.mX + theSrcRect.mWidth) / theImage.mWidth;
            float v1 = (float) (theSrcRect.mY + theSrcRect.mHeight) / theImage.mHeight;
            float[] aQuad = mQuadUv;
            setQuadVertex(aQuad, 0, theX, theY, u0, v0);
            setQuadVertex(aQuad, 1, theX + theSrcRect.mWidth, theY, u1, v0);
            setQuadVertex(aQuad, 2, theX + theSrcRect.mWidth, theY + theSrcRect.mHeight, u1, v1);
            setQuadVertex(aQuad, 3, theX, theY + theSrcRect.mHeight, u0, v1);
            drawClippedQuad(aTexture, aQuad, theClipRect, theColor.toInt(), aMode);
            return;
        }

        backend().setBlendMode(aMode);
        backend().drawTexture(aTexture, theSrcRect.mX, theSrcRect.mY, theSrcRect.mWidth, theSrcRect.mHeight,
                theX, theY, theSrcRect.mWidth, theSrcRect.mHeight, 0, 0, 0, RendererBackend.FLIP_NONE, theColor.toInt());
    }

    /**
     * Upstream {@code GLImage::BltRotated} (GLImage.cpp:174-181) through
     * {@code GLInterface::BltRotated} (GLInterface.cpp:1470-1481).
     *
     * <p>PORT-NOTE: upstream rotates with {@code SexyTransform2D::RotateRad} (SexyMatrix.cpp:119-137),
     * which is counter-clockwise-positive in screen coordinates (y down), whereas
     * {@link RendererBackend#drawTexture} documents its angle as clockwise-positive degrees.
     * The angle is therefore negated. The rotation centre is given relative to the destination
     * rectangle's top-left, which is exactly what upstream's
     * {@code Translate(-rotCx, -rotCy) ... Translate(x + rotCx, y + rotCy)} composition produces.
     */
    @Override
    public void bltRotated(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, double theRot, float theRotCenterX, float theRotCenterY) {
        bindTarget();
        theImage.mDrawn = true;
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }
        commitBits();

        if (bltRotatedClipHelper(theX, theY, theSrcRect, theClipRect, theRot, theRotCenterX, theRotCenterY) == null) {
            return;
        }

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, true);
        backend().setBlendMode(blendMode(theDrawMode));
        float anAngle = (float) -Math.toDegrees(theRot);
        backend().drawTexture(aTexture, theSrcRect.mX, theSrcRect.mY, theSrcRect.mWidth, theSrcRect.mHeight,
                theX, theY, theSrcRect.mWidth, theSrcRect.mHeight, anAngle, theRotCenterX, theRotCenterY,
                RendererBackend.FLIP_NONE, theColor.toInt());
    }

    /**
     * Upstream {@code MemoryImage::BltRotatedClipHelper} (MemoryImage.cpp:1362-1404): maps the four
     * source corners through the rotation and returns their bounding rectangle intersected with the
     * clip, or {@code null} when nothing would be drawn.
     */
    private static FRect bltRotatedClipHelper(float theX, float theY, Rect theSrcRect, Rect theClipRect, double theRot, float theRotCenterX, float theRotCenterY) {
        float aCos = (float) Math.cos(theRot);
        float aSin = (float) Math.sin(theRot);

        float[] px = {0, theSrcRect.mWidth, theSrcRect.mWidth, 0};
        float[] py = {0, 0, theSrcRect.mHeight, theSrcRect.mHeight};
        float aMinX = 10000000;
        float aMaxX = -10000000;
        float aMinY = 10000000;
        float aMaxY = -10000000;

        for (int i = 0; i < 4; i++) {
            float ox = px[i] - theRotCenterX;
            float oy = py[i] - theRotCenterY;

            px[i] = (theRotCenterX + ox * aCos + oy * aSin) + theX;
            py[i] = (theRotCenterY + oy * aCos - ox * aSin) + theY;

            if (px[i] < aMinX) aMinX = px[i];
            if (px[i] > aMaxX) aMaxX = px[i];
            if (py[i] < aMinY) aMinY = py[i];
            if (py[i] > aMaxY) aMaxY = py[i];
        }

        FRect aClipRect = new FRect(theClipRect.mX, theClipRect.mY, theClipRect.mWidth, theClipRect.mHeight);
        FRect aDestRect = new FRect(aMinX, aMinY, aMaxX - aMinX, aMaxY - aMinY).intersection(aClipRect);
        if (aDestRect.mWidth <= 0 || aDestRect.mHeight <= 0) {
            return null;
        }
        return aDestRect;
    }

    /**
     * Upstream {@code GLImage::StretchBlt} (GLImage.cpp:191-199) through
     * {@code GLInterface::StretchBlt} (GLInterface.cpp:1443-1462).
     *
     * <p>Clips the destination against {@code theClipRect} and shrinks the source rectangle by the
     * same proportions (upstream {@code MemoryImage::StretchBltClipHelper}, MemoryImage.cpp:1406-1418),
     * then draws the resulting quad. The source rectangle snapshots are the double math of the
     * helper truncated to the integer texture coordinates {@link RendererBackend#drawTexture} takes.
     */
    @Override
    public void stretchBlt(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
        bindTarget();
        theImage.mDrawn = true;
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }
        commitBits();

        Rect aDestOut = new Rect(theDestRect.mX, theDestRect.mY, theDestRect.mWidth, theDestRect.mHeight).intersection(theClipRect);
        double aXFactor = theSrcRect.mWidth / (double) theDestRect.mWidth;
        double aYFactor = theSrcRect.mHeight / (double) theDestRect.mHeight;

        int aSrcX = (int) (theSrcRect.mX + (aDestOut.mX - theDestRect.mX) * aXFactor);
        int aSrcY = (int) (theSrcRect.mY + (aDestOut.mY - theDestRect.mY) * aYFactor);
        int aSrcW = (int) (theSrcRect.mWidth + (aDestOut.mWidth - theDestRect.mWidth) * aXFactor);
        int aSrcH = (int) (theSrcRect.mHeight + (aDestOut.mHeight - theDestRect.mHeight) * aYFactor);
        if (aSrcW <= 0 || aSrcH <= 0) {
            return;
        }

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, !fastStretch);
        backend().setBlendMode(blendMode(theDrawMode));
        backend().drawTexture(aTexture, aSrcX, aSrcY, aSrcW, aSrcH,
                aDestOut.mX, aDestOut.mY, aDestOut.mWidth, aDestOut.mHeight, 0, 0, 0,
                RendererBackend.FLIP_NONE, theColor.toInt());
    }

    /**
     * Upstream {@code GLImage::StretchBltMirror} (GLImage.cpp:208-213) through
     * {@code GLInterface::StretchBlt(..., mirror = true)} (GLInterface.cpp:1443-1462), using the
     * mirrored clip helper (upstream {@code MemoryImage::StretchBltMirrorClipHelper},
     * MemoryImage.cpp:1421-1438) so the clipped source rectangle mirrors with the destination.
     */
    @Override
    public void stretchBltMirror(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
        bindTarget();
        theImage.mDrawn = true;
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }
        commitBits();

        Rect aDestOut = new Rect(theDestRect.mX, theDestRect.mY, theDestRect.mWidth, theDestRect.mHeight).intersection(theClipRect);
        double aXFactor = theSrcRect.mWidth / (double) theDestRect.mWidth;
        double aYFactor = theSrcRect.mHeight / (double) theDestRect.mHeight;

        int aTotalClip = theDestRect.mWidth - aDestOut.mWidth;
        int aLeftClip = aDestOut.mX - theDestRect.mX;
        int aRightClip = aTotalClip - aLeftClip;

        int aSrcX = (int) (theSrcRect.mX + aRightClip * aXFactor);
        int aSrcY = (int) (theSrcRect.mY + (aDestOut.mY - theDestRect.mY) * aYFactor);
        int aSrcW = (int) (theSrcRect.mWidth + (aDestOut.mWidth - theDestRect.mWidth) * aXFactor);
        int aSrcH = (int) (theSrcRect.mHeight + (aDestOut.mHeight - theDestRect.mHeight) * aYFactor);
        if (aSrcW <= 0 || aSrcH <= 0) {
            return;
        }

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, !fastStretch);
        backend().setBlendMode(blendMode(theDrawMode));
        backend().drawTexture(aTexture, aSrcX, aSrcY, aSrcW, aSrcH,
                aDestOut.mX, aDestOut.mY, aDestOut.mWidth, aDestOut.mHeight, 0, 0, 0,
                RendererBackend.FLIP_HORIZONTAL, theColor.toInt());
    }

    /**
     * Upstream {@code GLImage::BltMirror} (GLImage.cpp:194-206) through
     * {@code GLInterface::BltMirror} (GLInterface.cpp:1427-1440): the image is mirrored horizontally
     * inside the same destination rectangle as {@link #blt}.
     *
     * <p>As upstream, this does not set {@code theImage.mDrawn}.
     */
    @Override
    public void bltMirror(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
        bindTarget();
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }
        commitBits();

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, linearFilter);
        backend().setBlendMode(blendMode(theDrawMode));
        backend().drawTexture(aTexture, theSrcRect.mX, theSrcRect.mY, theSrcRect.mWidth, theSrcRect.mHeight,
                theX, theY, theSrcRect.mWidth, theSrcRect.mHeight, 0, 0, 0, RendererBackend.FLIP_HORIZONTAL, theColor.toInt());
    }

    /**
     * Upstream {@code GLImage::BltMatrix} (GLImage.cpp:183-189) through
     * {@code GLInterface::BltTransformed} / {@code TextureData::BltTransformed}
     * (GLInterface.cpp:943-1000): the source rectangle, centred on the origin, is mapped by
     * {@code theMatrix} and offset by {@code (x, y)}.
     *
     * <p>This port renders it through {@link RendererBackend#drawTexture}, which expresses an
     * axis-aligned scale + rotation + optional horizontal flip. The 2x2 part is decomposed into its
     * two columns: column 0 is the texture +x axis and column 1 the +y axis, so their lengths are
     * the per-axis scales and the angle of column 0 (negated when the determinant is negative)
     * is the rotation. The translation {@code (m02, m12)} is the quad centre (it is part of the
     * matrix product, as upstream) plus {@code (x, y)}.
     *
     * <p>PORT-NOTE: a matrix whose columns are not perpendicular (a true shear, produced when
     * {@code skewX != skewY}) cannot be represented by {@code drawTexture}; this port drops the
     * shear component and draws the closest scale/rotation/flip quad rather than fabricating a
     * wrong transform.
     *
     * <p>The clip rect is honoured the way upstream honours it: {@code TextureData::BltTransformed}
     * tests the transformed quad against it and clips the polygon when it leaves the rect. The
     * decomposition above can only ever draw the whole quad, so a clipped draw went out unclipped —
     * the seed packet's cooldown wipe darkening its plant art over the refilled part was one of them.
     */
    @Override
    public void bltMatrix(Image theImage, float x, float y, SexyMatrix3 theMatrix, Rect theClipRect, Color theColor, int theDrawMode, Rect theSrcRect, boolean blend) {
        bindTarget();
        theImage.mDrawn = true;
        if (backend() == null || !(theImage instanceof MemoryImage aMemoryImage)) {
            return;
        }

        float a = theMatrix.m00;
        float b = theMatrix.m10;
        float c = theMatrix.m01;
        float d = theMatrix.m11;

        float sx = (float) Math.hypot(a, b);
        float sy = (float) Math.hypot(c, d);
        boolean mirrored = (a * d - c * b) < 0;
        float anAngle = (float) Math.toDegrees(mirrored ? Math.atan2(-b, -a) : Math.atan2(b, a));

        float aDestW = theSrcRect.mWidth * sx;
        float aDestH = theSrcRect.mHeight * sy;
        float aCenterX = theMatrix.m02 + x;
        float aCenterY = theMatrix.m12 + y;
        float aDestX = aCenterX - aDestW / 2;
        float aDestY = aCenterY - aDestH / 2;

        long aTexture = ensureTexture(aMemoryImage);
        applyFilter(aTexture, true);
        RendererBackend.BlendMode aMode = blendMode(theDrawMode);

        float u0 = (float) theSrcRect.mX / theImage.mWidth;
        float v0 = (float) theSrcRect.mY / theImage.mHeight;
        float u1 = (float) (theSrcRect.mX + theSrcRect.mWidth) / theImage.mWidth;
        float v1 = (float) (theSrcRect.mY + theSrcRect.mHeight) / theImage.mHeight;
        if (mirrored) {
            float aSwap = u0;
            u0 = u1;
            u1 = aSwap;
        }

        // The four corners of the quad the draw below would cover, in the backend's model (corners
        // rotated about the destination centre, exactly what drawTexture does with angle/centre).
        float[] aQuad = mQuadUv;
        if (anAngle == 0.0f) {
            setQuadVertex(aQuad, 0, aDestX, aDestY, u0, v0);
            setQuadVertex(aQuad, 1, aDestX + aDestW, aDestY, u1, v0);
            setQuadVertex(aQuad, 2, aDestX + aDestW, aDestY + aDestH, u1, v1);
            setQuadVertex(aQuad, 3, aDestX, aDestY + aDestH, u0, v1);
        } else {
            double aRadians = Math.toRadians(anAngle);
            float aCos = (float) Math.cos(aRadians);
            float aSin = (float) Math.sin(aRadians);
            float aHalfW = aDestW / 2;
            float aHalfH = aDestH / 2;
            setRotatedQuadVertex(aQuad, 0, -aHalfW, -aHalfH, aCenterX, aCenterY, aCos, aSin, u0, v0);
            setRotatedQuadVertex(aQuad, 1, aHalfW, -aHalfH, aCenterX, aCenterY, aCos, aSin, u1, v0);
            setRotatedQuadVertex(aQuad, 2, aHalfW, aHalfH, aCenterX, aCenterY, aCos, aSin, u1, v1);
            setRotatedQuadVertex(aQuad, 3, -aHalfW, aHalfH, aCenterX, aCenterY, aCos, aSin, u0, v1);
        }

        if (!isQuadInsideClip(aQuad, theClipRect)) {
            drawClippedQuad(aTexture, aQuad, theClipRect, theColor.toInt(), aMode);
            return;
        }

        backend().setBlendMode(aMode);
        backend().drawTexture(aTexture, theSrcRect.mX, theSrcRect.mY, theSrcRect.mWidth, theSrcRect.mHeight,
                aDestX, aDestY, aDestW, aDestH, anAngle, aDestW / 2, aDestH / 2,
                mirrored ? RendererBackend.FLIP_HORIZONTAL : RendererBackend.FLIP_NONE, theColor.toInt());
    }

    /**
     * Upstream {@code GLImage::BltTrianglesTex} (GLImage.cpp:202-210) through
     * {@code GLInterface::DrawTrianglesTex}.
     *
     * <p>{@code blend} selects the texture filter for the batch exactly as upstream's
     * {@code SetLinearFilter(blend)} does; {@code theClipRect} stays unused because the draw is
     * clipped by the backend's current clip state (owned by {@link Graphics}, not touched here).
     * The {@code theTexture.color}-is-0 convention of {@code TriVertex} (TriVertex.h:33-40) falls
     * back to {@code theColor}.
     */
    @Override
    public void bltTrianglesTex(Image theTexture, TriVertex[][] theVertices, int theNumTriangles, Rect theClipRect, Color theColor, int theDrawMode, float tx, float ty, boolean blend) {
        bindTarget();
        theTexture.mDrawn = true;
        if (backend() == null || !(theTexture instanceof MemoryImage aMemoryImage)) {
            return;
        }

        long aTexture = ensureTexture(aMemoryImage);
        if (aTexture == 0) {
            return;
        }

        // GLInterface.cpp:1604 (and :1621 for the mixed path): SetLinearFilter(blend) before the batch
        // is drawn. The filter is a property of the source texture, so it belongs here, not on the
        // backend's batch state.
        applyFilter(aTexture, blend);

        RendererBackend.BlendMode aMode = blendMode(theDrawMode);
        float[] aPositionsUv = new float[theNumTriangles * 12];
        int[] aColors = new int[theNumTriangles * 3];
        int[] anIndices = new int[theNumTriangles * 3];
        int aColor = theColor.toInt();
        for (int i = 0; i < theNumTriangles; i++) {
            for (int j = 0; j < 3; j++) {
                TriVertex aVertex = theVertices[i][j];
                int anIndex = i * 3 + j;
                int aBase = anIndex * 4;
                aPositionsUv[aBase] = aVertex.x + tx;
                aPositionsUv[aBase + 1] = aVertex.y + ty;
                aPositionsUv[aBase + 2] = aVertex.u;
                aPositionsUv[aBase + 3] = aVertex.v;
                aColors[anIndex] = aVertex.color != 0 ? aVertex.color : aColor;
                anIndices[anIndex] = anIndex;
            }
        }

        backend().setBlendMode(aMode);
        backend().drawTriangles(aTexture, aPositionsUv, aColors, theNumTriangles * 3, anIndices, theNumTriangles * 3, aMode);
    }

    /**
     * Upstream {@code GLImage::FillScanLinesWithCoverage} (GLImage.cpp:59-84): render the covered
     * spans into a temporary software image and blit that into this (GPU) image.
     *
     * <p>The exact upstream off-by-one in the bounding box is preserved: the right edge uses
     * {@code mX + mWidth - 1} while the bottom uses {@code mY}, because the temporary image size
     * derives from it.
     */
    @Override
    public void fillScanLinesWithCoverage(Span[] theSpans, int theSpanCount, Color theColor, int theDrawMode, byte[] theCoverage, int theCoverX, int theCoverY, int theCoverWidth, int theCoverHeight) {
        if (theSpanCount == 0) {
            return;
        }

        int l = theSpans[0].mX;
        int t = theSpans[0].mY;
        int r = l + theSpans[0].mWidth;
        int b = t;
        for (int i = 1; i < theSpanCount; ++i) {
            l = Math.min(theSpans[i].mX, l);
            r = Math.max(theSpans[i].mX + theSpans[i].mWidth - 1, r);
            t = Math.min(theSpans[i].mY, t);
            b = Math.max(theSpans[i].mY, b);
        }
        for (int i = 0; i < theSpanCount; ++i) {
            theSpans[i].mX -= l;
            theSpans[i].mY -= t;
        }

        // PORT-NOTE: upstream's stack-allocated MemoryImage aTempImage registers with the app for its
        // lifetime (Init/destructor); Java has no destructor, so a null app keeps this per-call
        // scratch image out of SexyAppBase's memory-image set (same fix as MemoryImage.mirrorSource).
        MemoryImage aTempImage = new MemoryImage(null);
        aTempImage.create(r - l + 1, b - t + 1);
        aTempImage.fillScanLinesWithCoverage(theSpans, theSpanCount, theColor, theDrawMode, theCoverage,
                theCoverX - l, theCoverY - t, theCoverWidth, theCoverHeight);
        blt(aTempImage, l, t, new Rect(0, 0, r - l + 1, b - t + 1), Color.White, theDrawMode, true);

        // PORT-NOTE: only the temporary image's own BitsChanged() runs (inside its fill above); as
        // upstream, this GLImage's CPU bits and its change counter are untouched, so the GPU content
        // is not invalidated and the bits stay recoverable through getBits().
    }
}
