package cn.enaium.fabricpvz.portable.sexy.graphics;

import java.util.Arrays;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Debug;
import cn.enaium.fabricpvz.portable.sexy.FRect;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;

/**
 * Ported from src/SexyAppFramework/graphics/MemoryImage.h:46 and src/SexyAppFramework/graphics/MemoryImage.cpp:31.
 *
 * <p>Faithful port of {@code Sexy::MemoryImage}: an image backed by a straight-ARGB CPU pixel
 * buffer plus the software fill/line/blit rasterizers. The pixel word is {@code 0xAARRGGBB}
 * ({@code mBits}) and {@code mBitsChangedCount} is the only texture-invalidation signal the
 * renderer side uses (GLInterface re-uploads when it changes), exactly like upstream.
 *
 * <p>Like upstream, the whole drawing surface mutates {@code mBits} and finishes with
 * {@link #bitsChanged()}; {@link #commitBits()} is the only place {@code mHasTrans}/{@code mHasAlpha}
 * are derived (unless {@link #setImageMode(boolean, boolean)} pinned them).
 *
 * <h2>Deliberately dropped DirectDraw-era members (all with zero remaining users in this port)</h2>
 * <ul>
 *   <li><b>Native alpha data</b> ({@code mNativeAlphaData}, {@code GetNativeAlphaData},
 *       {@code DeleteNativeData}, {@code GetBits}' un-shift path). Upstream builds it only for the
 *       DirectDraw display format so the accelerated blitter can read converted pixels; this port
 *       uploads straight ARGB to the renderer backend instead.</li>
 *   <li><b>Run-length alpha/additive classification</b> ({@code mRLAlphaData},
 *       {@code mRLAdditiveData}, {@code GetRLAlphaData}, {@code GetRLAdditiveData} and the
 *       {@code MI_GetRLAlphaData.inc} classified fast path). Only the DirectDraw blitters consumed
 *       them.</li>
 *   <li><b>Palettes</b> ({@code mColorTable}, {@code mColorIndices}, {@code mWantPal},
 *       {@code Palletize}, {@code Quantize8Bit} and the palettized branches of every blitter).
 *       They are a CPU-memory optimization for the software blitters; every image in this port is
 *       straight ARGB (see docs/research/02-graphics-audio.md §8.8.2, which lists exactly these as
 *       droppable). {@code ImageLib} expands palettized PNG/GIF eagerly.
 *       {@code PrecacheAdditive}/{@code PrecacheAlpha}/{@code PrecacheNative}
 *       ({@code SexyAppBase.cpp:3994-4008}) had zero callers repo-wide and are not ported.</li>
 *   <li><b>Volatile-surface bookkeeping</b> ({@code mIsVolatile}/{@code SetVolatile}). These flag a
 *       DirectDraw-era image whose pixels live in video memory and can be lost on a mode switch; the
 *       field is write-only in upstream too (only {@code Init} and {@code SetVolatile} touch it,
 *       MemoryImage.cpp:70/769-772, with no reader anywhere in {@code src/}) and there is no lost
 *       surface in this port, so both were dropped.</li>
 * </ul>
 *
 * <p>PORT-NOTE: {@code MEMORYCHECK_ID} is still written into the spare slot {@code mBits[w*h]} as
 * upstream does and checked by {@link #bitsChanged()} (upstream's {@code DBG_ASSERTE}); callers must
 * never write past {@code w*h} words.
 *
 * <p>PORT-NOTE: upstream implements {@code BltMatrix}/{@code BltTrianglesTex} through the
 * {@code SWTri} software triangle rasterizer (128 per-format entry points), which
 * docs/research/02-graphics-audio.md §8.8.2 lists as droppable (superseded by GPU triangles).
 * Since the SDL {@link RendererBackend} of this port has no triangle primitive, the software
 * fallback here is a self-contained barycentric textured-triangle rasterizer. It reproduces
 * upstream's fragment rules verbatim — the {@code 0x08} alpha cull, the {@code >>> 8} tint
 * modulation and the {@code 0xf0} "store {@code tex | 0xFF000000}" opaque path
 * ({@code SWTri_Pixel8888.inc:31-76}) — and only its sub-{@code 0xf0} blend rounding differs
 * (upstream's {@code finalAlpha} premultiplied mix). It is only reachable for CPU-backed targets;
 * {@link GLImage} handles the screen.
 */
public class MemoryImage extends Image {

    /** Upstream {@code MemoryImage.h:40}; canary stored at {@code mBits[mWidth*mHeight]}. */
    public static final int MEMORYCHECK_ID = 0x4BEEFADE;

    // RenderImageFlags (upstream GLInterface.h:49-56)
    public static final int RenderImageFlag_MinimizeNumSubdivisions = 0x0001;
    public static final int RenderImageFlag_Use64By64Subdivisions = 0x0002;
    public static final int RenderImageFlag_UseA4R4G4B4 = 0x0004;
    public static final int RenderImageFlag_UseA8R8G8B8 = 0x0008;
    public static final int RenderImageFlag_Repeat = 0x0010;
    public static final int RenderImageFlag_TextureMask = 0x001F;

    /** {@code 0xAARRGGBB} pixels, length {@code mWidth*mHeight + 1} (last word = canary). */
    public int[] mBits;
    public int mBitsChangedCount;
    /** GPU mirror of {@link #mBits} (upstream {@code TextureData* mRenderData}); opaque handle, 0 = none. */
    public long mRenderData;
    /** {@link #mBitsChangedCount} at the time {@link #mRenderData} was uploaded; -1 = never uploaded. */
    public int mRenderDataBitsChangedCount;
    /** Texture hints the game sets (upstream {@code RenderImageFlags}); the SDL backend ignores them. */
    public int mRenderFlags;

    public boolean mForcedMode;
    public boolean mHasTrans;
    public boolean mHasAlpha;
    public boolean mPurgeBits;

    public boolean mBitsChanged;
    public SexyAppBase mApp;

    public MemoryImage() {
        this(SexyAppBase.current);
    }

    public MemoryImage(SexyAppBase theApp) {
        mApp = theApp;

        init();
    }

    private void init() {
        mHasTrans = false;
        mHasAlpha = false;
        mBitsChanged = false;
        mForcedMode = false;

        mRenderData = 0;
        mRenderDataBitsChangedCount = -1;
        mRenderFlags = 0;
        mBitsChangedCount = 0;

        mPurgeBits = false;

        if (mApp != null) {
            mApp.addMemoryImage(this);
        }
    }

    // ------------------------------------------------------------------
    // change tracking / mode
    // ------------------------------------------------------------------

    // BitsChanged
    public void bitsChanged() {
        mBitsChanged = true;
        mBitsChangedCount++;
        mFilePath = null;

        // Verify secret value at end to protect against overwrite
        if (mBits != null) {
            Debug.dbgAsserte(mBits[mWidth * mHeight] == MEMORYCHECK_ID);
        }
    }

    // CommitBits
    public void commitBits() {
        if (mBitsChanged && !mForcedMode) {
            // Analyze
            if (mBits != null) {
                mHasTrans = false;
                mHasAlpha = false;

                int aSize = mWidth * mHeight;
                for (int i = 0; i < aSize; i++) {
                    int anAlpha = mBits[i] >>> 24;

                    if (anAlpha == 0) {
                        mHasTrans = true;
                    } else if (anAlpha != 255) {
                        mHasAlpha = true;
                    }
                }
            } else {
                mHasTrans = true;
                mHasAlpha = false;
            }

            mBitsChanged = false;
        }
    }

    // SetImageMode
    public void setImageMode(boolean hasTrans, boolean hasAlpha) {
        mForcedMode = true;
        mHasTrans = hasTrans;
        mHasAlpha = hasAlpha;
    }

    // ------------------------------------------------------------------
    // buffer lifetime
    // ------------------------------------------------------------------

    // PurgeBits
    public void purgeBits() {
        mPurgeBits = true;

        if (mBits == null) {
            return;
        }

        // Upstream never loses pixels: under 3D acceleration it defers the purge while no render data
        // exists yet (MemoryImage.cpp:1002-1009, the `mRenderData == nullptr` early return), otherwise
        // it keeps a native-display copy of the pixels. This port has no native copy, so it defers
        // whenever nothing else can restore the bits: no GPU mirror (mRenderData) and no file for
        // getBits() to re-decode from (mFilePath). reInit() completes the deferred purge later.
        if (mRenderData == 0 && (mFilePath == null || mFilePath.isEmpty())) {
            return;
        }

        mBits = null;
    }

    // DeleteSWBuffers
    public void deleteSWBuffers() {
        // Upstream keeps the CPU bits around (it materializes them first, then drops the native/RL copies).
        getBits();
    }

    // Delete3DBuffers
    public void delete3DBuffers() {
        if (mApp != null) {
            mApp.remove3DData(this);
        }
    }

    // DeleteExtraBuffers
    public void deleteExtraBuffers() {
        deleteSWBuffers();
        delete3DBuffers();
    }

    // ReInit
    public void reInit() {
        if (mPurgeBits) {
            purgeBits();
        }
    }

    // SetBits
    public void setBits(int[] theBits, int theWidth, int theHeight, boolean commitBits) {
        if (theBits != mBits) {
            if (mBits == null || theWidth != mWidth || theHeight != mHeight) {
                mBits = new int[theWidth * theHeight + 1];
                mWidth = theWidth;
                mHeight = theHeight;
            }
            System.arraycopy(theBits, 0, mBits, 0, mWidth * mHeight);
            mBits[mWidth * mHeight] = MEMORYCHECK_ID;

            bitsChanged();
            if (commitBits) {
                commitBits();
            }
        }
    }

    // Create
    public void create(int theWidth, int theHeight) {
        mBits = null;

        mWidth = theWidth;
        mHeight = theHeight;

        // All zeros --> trans + alpha
        mHasTrans = true;
        mHasAlpha = true;

        bitsChanged();
    }

    // GetBits
    public int[] getBits() {
        if (mBits == null) {
            int aSize = mWidth * mHeight;

            mBits = new int[aSize + 1];
            mBits[aSize] = MEMORYCHECK_ID;

            if (mFilePath != null && !mFilePath.isEmpty()) {
                ImageLib.Image aLoadedImage = ImageLib.getImage(mFilePath, true);

                if (aLoadedImage != null && aLoadedImage.getWidth() == mWidth && aLoadedImage.getHeight() == mHeight) {
                    System.arraycopy(aLoadedImage.getBits(), 0, mBits, 0, aSize);
                } else {
                    cn.enaium.fabricpvz.portable.sexy.Common.logErrorLn("failed to recover bits for image '{}'", mFilePath);
                }
            }
        }

        return mBits;
    }

    // Clear
    public void clear() {
        int[] ptr = getBits();
        if (ptr != null) {
            Arrays.fill(ptr, 0, mWidth * mHeight, 0);

            bitsChanged();
        }
    }

    // ------------------------------------------------------------------
    // fills
    // ------------------------------------------------------------------

    // FillRect
    @Override
    public void fillRect(Rect theRect, Color theColor, int theDrawMode) {
        int src = theColor.toInt();

        int[] aBits = getBits();

        int oldAlpha = src >>> 24;

        if (oldAlpha == 0xFF) {
            for (int aRow = theRect.mY; aRow < theRect.mY + theRect.mHeight; aRow++) {
                int aDestPixels = aRow * mWidth + theRect.mX;

                for (int i = 0; i < theRect.mWidth; i++) {
                    aBits[aDestPixels++] = src;
                }
            }
        } else {
            for (int aRow = theRect.mY; aRow < theRect.mY + theRect.mHeight; aRow++) {
                int aDestPixels = aRow * mWidth + theRect.mX;

                for (int i = 0; i < theRect.mWidth; i++) {
                    int dest = aBits[aDestPixels];

                    int aDestAlpha = dest >>> 24;
                    int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * oldAlpha) / 255;

                    int newAlpha = (aNewDestAlpha == 0) ? 0 : 255 * oldAlpha / aNewDestAlpha; // fully transparent: color weight is moot

                    int oma = 256 - newAlpha;

                    aBits[aDestPixels++] = (aNewDestAlpha << 24)
                            | (((((dest & 0x0000FF) * oma) >>> 8) + ((((src & 0x0000FF) * newAlpha) >>> 8) & 0x0000FF)))
                            | (((((dest & 0x00FF00) * oma) >>> 8) + ((((src & 0x00FF00) * newAlpha) >>> 8) & 0x00FF00)))
                            | (((((dest & 0xFF0000) * oma) >>> 8) + ((((src & 0xFF0000) * newAlpha) >>> 8) & 0xFF0000)));
                }
            }
        }

        bitsChanged();
    }

    // ClearRect
    @Override
    public void clearRect(Rect theRect) {
        int[] aBits = getBits();

        for (int aRow = theRect.mY; aRow < theRect.mY + theRect.mHeight; aRow++) {
            int aDestPixels = aRow * mWidth + theRect.mX;

            for (int i = 0; i < theRect.mWidth; i++) {
                aBits[aDestPixels++] = 0;
            }
        }

        bitsChanged();
    }

    // FillScanLinesWithCoverage
    @Override
    public void fillScanLinesWithCoverage(Span[] theSpans, int theSpanCount, Color theColor, int theDrawMode, byte[] theCoverage, int theCoverX, int theCoverY, int theCoverWidth, int theCoverHeight) {
        int[] theBits = getBits();
        int src = theColor.toInt();
        for (int i = 0; i < theSpanCount; ++i) {
            Span aSpan = theSpans[i];
            int x = aSpan.mX - theCoverX;
            int y = aSpan.mY - theCoverY;

            int aDestPixels = aSpan.mY * mWidth + aSpan.mX;
            int aCoverBits = y * theCoverWidth + x;
            for (int w = 0; w < aSpan.mWidth; ++w) {
                int cover = (theCoverage[aCoverBits++] & 0xFF) + 1;
                int a = (cover * theColor.mAlpha) >>> 8;
                int oma;
                int dest = theBits[aDestPixels];

                if (a > 0) {
                    int aDestAlpha = dest >>> 24;
                    int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;

                    a = 255 * a / aNewDestAlpha;
                    oma = 256 - a;
                    // PORT-NOTE: upstream leaves aDestPixels unadvanced when a == 0 (MemoryImage.cpp:1720-1731);
                    // that behaviour is reproduced here on purpose.
                    theBits[aDestPixels++] = (aNewDestAlpha << 24)
                            | (((((dest & 0x0000FF) * oma) >>> 8) + ((((src & 0x0000FF) * a) >>> 8) & 0x0000FF)))
                            | (((((dest & 0x00FF00) * oma) >>> 8) + ((((src & 0x00FF00) * a) >>> 8) & 0x00FF00)))
                            | (((((dest & 0xFF0000) * oma) >>> 8) + ((((src & 0xFF0000) * a) >>> 8) & 0xFF0000)));
                }
            }
        }
        bitsChanged();
    }

    // ------------------------------------------------------------------
    // lines
    // ------------------------------------------------------------------

    /** Upstream {@code mAdd8BitMaxTable}: {@code table[i] = min(i, 255)} for i in [0, 511]. */
    private static int addMax(int value) {
        return value < 255 ? value : 255;
    }

    // NormalDrawLine
    public void normalDrawLine(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor) {
        double aMinX = Math.min(theStartX, theEndX);
        double aMinY = Math.min(theStartY, theEndY);
        double aMaxX = Math.max(theStartX, theEndX);
        double aMaxY = Math.max(theStartY, theEndY);

        int aRMask = 0xFF0000;
        int aGMask = 0x00FF00;
        int aBMask = 0x0000FF;
        int aRRoundAdd = aRMask >> 1;
        int aGRoundAdd = aGMask >> 1;
        int aBRoundAdd = aBMask >> 1;

        int[] aSurface = getBits();

        if (theColor.mAlpha == 255) {
            int aColor = 0xFF000000
                    | ((((((theColor.mRed * aRMask) + aRRoundAdd) >>> 8) & aRMask)))
                    | ((((((theColor.mGreen * aGMask) + aGRoundAdd) >>> 8) & aGMask)))
                    | ((((((theColor.mBlue * aBMask) + aBRoundAdd) >>> 8) & aBMask)));

            double dv = theEndY - theStartY;
            double dh = theEndX - theStartX;
            int G, DeltaG1, DeltaG2;
            double swap;
            int inc = 1;
            int aCurX;
            int aCurY;
            int aRowWidth = mWidth;
            int aRowAdd = aRowWidth;

            if (Math.abs(dv) < Math.abs(dh)) {
                // Mostly horizontal
                if (dh < 0) {
                    dh = -dh;
                    dv = -dv;
                    swap = theEndY;
                    theEndY = theStartY;
                    theStartY = swap;
                    swap = theEndX;
                    theEndX = theStartX;
                    theStartX = swap;
                }
                if (dv < 0) {
                    dv = -dv;
                    inc = -1;
                    aRowAdd = -aRowAdd;
                }

                int aDestPixels = ((int) theStartY * aRowWidth) + (int) theStartX;
                aSurface[aDestPixels] = aColor;
                aDestPixels++;

                aCurY = (int) theStartY;
                aCurX = (int) theStartX + 1;

                G = (int) (2 * dv - dh);
                DeltaG1 = (int) (2 * (dv - dh));
                DeltaG2 = (int) (2 * dv);

                G += (int) (DeltaG2 * (theStartY - (int) theStartY));

                while (aCurX <= theEndX) {
                    if (G > 0) {
                        G += DeltaG1;
                        aCurY += inc;
                        aDestPixels += aRowAdd;

                        if (aCurX < aMinX || aCurY < aMinY || aCurX > aMaxX || aCurY > aMaxY) {
                            break;
                        }
                    } else {
                        G += DeltaG2;
                    }

                    aSurface[aDestPixels] = aColor;

                    aCurX++;
                    aDestPixels++;
                }
            } else {
                // Mostly vertical
                if (dv < 0) {
                    dh = -dh;
                    dv = -dv;
                    swap = theEndY;
                    theEndY = theStartY;
                    theStartY = swap;
                    swap = theEndX;
                    theEndX = theStartX;
                    theStartX = swap;
                }

                if (dh < 0) {
                    dh = -dh;
                    inc = -1;
                }

                int aDestPixels = ((int) theStartY * aRowWidth) + (int) theStartX;
                aSurface[aDestPixels] = aColor;
                aDestPixels += aRowAdd;

                aCurX = (int) theStartX;
                aCurY = (int) theStartY + 1;

                G = (int) (2 * dh - dv);
                DeltaG1 = (int) (2 * (dh - dv));
                DeltaG2 = (int) (2 * dh);

                G += (int) (DeltaG2 * (theStartX - (int) theStartX));

                while (aCurY <= theEndY) {
                    if (G > 0) {
                        G += DeltaG1;
                        aCurX += inc;
                        aDestPixels += inc;

                        if (aCurX < aMinX || aCurY < aMinY || aCurX > aMaxX || aCurY > aMaxY) {
                            break;
                        }
                    } else {
                        G += DeltaG2;
                    }

                    aSurface[aDestPixels] = aColor;

                    aCurY++;
                    aDestPixels += aRowAdd;
                }
            }
        } else {
            int src = 0xFF000000
                    | ((((((((theColor.mRed * theColor.mAlpha) + 0x80) >>> 8) * aRMask) + aRRoundAdd) >>> 8) & aRMask))
                    | ((((((((theColor.mGreen * theColor.mAlpha) + 0x80) >>> 8) * aGMask) + aGRoundAdd) >>> 8) & aGMask))
                    | ((((((((theColor.mBlue * theColor.mAlpha) + 0x80) >>> 8) * aBMask) + aBRoundAdd) >>> 8) & aBMask));
            int oma = 256 - theColor.mAlpha;

            double dv = theEndY - theStartY;
            double dh = theEndX - theStartX;
            int G, DeltaG1, DeltaG2;
            double swap;
            int inc = 1;
            int aCurX;
            int aCurY;
            int aRowWidth = mWidth;
            int aRowAdd = aRowWidth;

            if (Math.abs(dv) < Math.abs(dh)) {
                // Mostly horizontal
                if (dh < 0) {
                    dh = -dh;
                    dv = -dv;
                    swap = theEndY;
                    theEndY = theStartY;
                    theStartY = swap;
                    swap = theEndX;
                    theEndX = theStartX;
                    theStartX = swap;
                }
                if (dv < 0) {
                    dv = -dv;
                    inc = -1;
                    aRowAdd = -aRowAdd;
                }

                int aDestPixels = ((int) theStartY * aRowWidth) + (int) theStartX;
                int dest = aSurface[aDestPixels];
                aSurface[aDestPixels++] = src
                        + (((((dest & aRMask) * oma) + aRRoundAdd) >>> 8) & aRMask)
                        + (((((dest & aGMask) * oma) + aGRoundAdd) >>> 8) & aGMask)
                        + (((((dest & aBMask) * oma) + aBRoundAdd) >>> 8) & aBMask);

                aCurY = (int) theStartY;
                aCurX = (int) theStartX + 1;

                G = (int) (2 * dv - dh);
                DeltaG1 = (int) (2 * (dv - dh));
                DeltaG2 = (int) (2 * dv);

                G += (int) (DeltaG2 * (theStartX - (int) theStartX));

                while (aCurX <= theEndX) {
                    if (G > 0) {
                        G += DeltaG1;
                        aCurY += inc;
                        aDestPixels += aRowAdd;

                        if (aCurX < aMinX || aCurY < aMinY || aCurX > aMaxX || aCurY > aMaxY) {
                            break;
                        }
                    } else {
                        G += DeltaG2;
                    }

                    dest = aSurface[aDestPixels];
                    aSurface[aDestPixels++] = src
                            + (((((dest & aRMask) * oma) + aRRoundAdd) >>> 8) & aRMask)
                            + (((((dest & aGMask) * oma) + aGRoundAdd) >>> 8) & aGMask)
                            + (((((dest & aBMask) * oma) + aBRoundAdd) >>> 8) & aBMask);

                    aCurX++;
                }
            } else {
                // Mostly vertical
                if (dv < 0) {
                    dh = -dh;
                    dv = -dv;
                    swap = theEndY;
                    theEndY = theStartY;
                    theStartY = swap;
                    swap = theEndX;
                    theEndX = theStartX;
                    theStartX = swap;
                }

                if (dh < 0) {
                    dh = -dh;
                    inc = -1;
                }

                int aDestPixels = ((int) theStartY * aRowWidth) + (int) theStartX;
                int dest = aSurface[aDestPixels];
                aSurface[aDestPixels] = src
                        + (((((dest & aRMask) * oma) + aRRoundAdd) >>> 8) & aRMask)
                        + (((((dest & aGMask) * oma) + aGRoundAdd) >>> 8) & aGMask)
                        + (((((dest & aBMask) * oma) + aBRoundAdd) >>> 8) & aBMask);
                aDestPixels += aRowAdd;

                aCurX = (int) theStartX;
                aCurY = (int) theStartY + 1;

                G = (int) (2 * dh - dv);
                DeltaG1 = (int) (2 * (dh - dv));
                DeltaG2 = (int) (2 * dh);

                G += (int) (DeltaG2 * (theStartX - (int) theStartX));

                while (aCurY <= theEndY) {
                    if (G > 0) {
                        G += DeltaG1;
                        aCurX += inc;
                        aDestPixels += inc;

                        if (aCurX < aMinX || aCurY < aMinY || aCurX > aMaxX || aCurY > aMaxY) {
                            break;
                        }
                    } else {
                        G += DeltaG2;
                    }

                    dest = aSurface[aDestPixels];
                    aSurface[aDestPixels] = src
                            + (((((dest & aRMask) * oma) + aRRoundAdd) >>> 8) & aRMask)
                            + (((((dest & aGMask) * oma) + aGRoundAdd) >>> 8) & aGMask)
                            + (((((dest & aBMask) * oma) + aBRoundAdd) >>> 8) & aBMask);

                    aCurY++;
                    aDestPixels += aRowAdd;
                }
            }
        }
    }

    // AdditiveDrawLine
    public void additiveDrawLine(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor) {
        double aMinX = Math.min(theStartX, theEndX);
        double aMinY = Math.min(theStartY, theEndY);
        double aMaxX = Math.max(theStartX, theEndX);
        double aMaxY = Math.max(theStartY, theEndY);

        int aRMask = 0xFF0000;
        int aGMask = 0x00FF00;
        int aBMask = 0x0000FF;
        int aRedShift = 16;
        int aGreenShift = 8;
        int aBlueShift = 0;

        int[] aSurface = getBits();

        int rc = ((theColor.mRed * theColor.mAlpha) / 255);
        int gc = ((theColor.mGreen * theColor.mAlpha) / 255);
        int bc = ((theColor.mBlue * theColor.mAlpha) / 255);

        double dv = theEndY - theStartY;
        double dh = theEndX - theStartX;
        int G, DeltaG1, DeltaG2;
        double swap;
        int inc = 1;
        int aCurX;
        int aCurY;
        int aRowWidth = mWidth;
        int aRowAdd = aRowWidth;

        if (Math.abs(dv) < Math.abs(dh)) {
            // Mostly horizontal
            if (dh < 0) {
                dh = -dh;
                dv = -dv;
                swap = theEndY;
                theEndY = theStartY;
                theStartY = swap;
                swap = theEndX;
                theEndX = theStartX;
                theStartX = swap;
            }

            if (dv < 0) {
                dv = -dv;
                inc = -1;
                aRowAdd = -aRowAdd;
            }

            int aDestPixels = ((int) theStartY * aRowWidth) + (int) theStartX;
            int dest = aSurface[aDestPixels];

            int r = addMax(((dest & aRMask) >>> aRedShift) + rc);
            int g = addMax(((dest & aGMask) >>> aGreenShift) + gc);
            int b = addMax(((dest & aBMask) >>> aBlueShift) + bc);

            aSurface[aDestPixels++] = 0xFF000000 | (r << aRedShift) | (g << aGreenShift) | (b << aBlueShift);

            aCurY = (int) theStartY;
            aCurX = (int) theStartX + 1;

            G = (int) (2 * dv - dh);
            DeltaG1 = (int) (2 * (dv - dh));
            DeltaG2 = (int) (2 * dv);

            while (aCurX <= theEndX) {
                if (G > 0) {
                    G += DeltaG1;
                    aCurY += inc;
                    aDestPixels += aRowAdd;

                    if (aCurX < aMinX || aCurY < aMinY || aCurX > aMaxX || aCurY > aMaxY) {
                        break;
                    }
                } else {
                    G += DeltaG2;
                }

                dest = aSurface[aDestPixels];

                r = addMax(((dest & aRMask) >>> aRedShift) + rc);
                g = addMax(((dest & aGMask) >>> aGreenShift) + gc);
                b = addMax(((dest & aBMask) >>> aBlueShift) + bc);

                aSurface[aDestPixels++] = 0xFF000000 | (r << aRedShift) | (g << aGreenShift) | (b << aBlueShift);

                aCurX++;
            }
        } else {
            // Mostly vertical
            if (dv < 0) {
                dh = -dh;
                dv = -dv;
                swap = theEndY;
                theEndY = theStartY;
                theStartY = swap;
                swap = theEndX;
                theEndX = theStartX;
                theStartX = swap;
            }

            if (dh < 0) {
                dh = -dh;
                inc = -1;
            }

            int aDestPixels = ((int) theStartY * aRowWidth) + (int) theStartX;

            int dest = aSurface[aDestPixels];

            int r = addMax(((dest & aRMask) >>> aRedShift) + rc);
            int g = addMax(((dest & aGMask) >>> aGreenShift) + gc);
            int b = addMax(((dest & aBMask) >>> aBlueShift) + bc);

            aSurface[aDestPixels] = 0xFF000000 | (r << aRedShift) | (g << aGreenShift) | (b << aBlueShift);

            aDestPixels += aRowAdd;

            aCurX = (int) theStartX;
            aCurY = (int) theStartY + 1;

            G = (int) (2 * dh - dv);
            DeltaG1 = (int) (2 * (dh - dv));
            DeltaG2 = (int) (2 * dh);
            while (aCurY <= theEndY) {
                if (G > 0) {
                    G += DeltaG1;
                    aCurX += inc;
                    aDestPixels += inc;

                    if (aCurX < aMinX || aCurY < aMinY || aCurX > aMaxX || aCurY > aMaxY) {
                        break;
                    }
                } else {
                    G += DeltaG2;
                }

                dest = aSurface[aDestPixels];

                r = addMax(((dest & aRMask) >>> aRedShift) + rc);
                g = addMax(((dest & aGMask) >>> aGreenShift) + gc);
                b = addMax(((dest & aBMask) >>> aBlueShift) + bc);

                aSurface[aDestPixels] = 0xFF000000 | (r << aRedShift) | (g << aGreenShift) | (b << aBlueShift);

                aCurY++;
                aDestPixels += aRowAdd;
            }
        }
    }

    // DrawLine
    @Override
    public void drawLine(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor, int theDrawMode) {
        if (theStartY == theEndY) {
            int aStartX = (int) Math.min(theStartX, theEndX);
            int aEndX = (int) Math.max(theStartX, theEndX);

            fillRect(new Rect(aStartX, (int) theStartY, aEndX - aStartX + 1, (int) theEndY - (int) theStartY + 1), theColor, theDrawMode);
            return;
        } else if (theStartX == theEndX) {
            int aStartY = (int) Math.min(theStartY, theEndY);
            int aEndY = (int) Math.max(theStartY, theEndY);

            fillRect(new Rect((int) theStartX, aStartY, (int) theEndX - (int) theStartX + 1, aEndY - aStartY + 1), theColor, theDrawMode);
            return;
        }

        switch (theDrawMode) {
            case Graphics.DRAWMODE_NORMAL:
                normalDrawLine(theStartX, theStartY, theEndX, theEndY, theColor);
                break;
            case Graphics.DRAWMODE_ADDITIVE:
                additiveDrawLine(theStartX, theStartY, theEndX, theEndY, theColor);
                break;
            default:
                break;
        }

        bitsChanged();
    }

    // NormalDrawLineAA
    public void normalDrawLineAA(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor) {
        int[] aBits = getBits();
        int color = theColor.toInt();

        int aX0 = (int) theStartX, aX1 = (int) theEndX;
        int aY0 = (int) theStartY, aY1 = (int) theEndY;
        int aXinc = 1;
        if (aY0 > aY1) {
            int aTempX = aX0, aTempY = aY0;
            aX0 = aX1;
            aY0 = aY1;
            aX1 = aTempX;
            aY1 = aTempY;
            double aTempXd = theStartX, aTempYd = theStartY;
            theStartX = theEndX;
            theStartY = theEndY;
            theEndX = aTempXd;
            theEndY = aTempYd;
        }

        int dx = aX1 - aX0;
        int dy = aY1 - aY0;
        double dxd = theEndX - theStartX;
        double dyd = theEndY - theStartY;
        if (dx < 0) {
            dx = -dx;
            aXinc = -1;
            dxd = -dxd;
        }

        int aDestPixels = aY0 * mWidth + aX0;
        int anErrAcc = (int) (Math.abs(theStartX - aX0) * Math.abs(theStartY - aY0) * 0x10000);
        if (dx >= dy) {
            // mostly horizontal
            int anErrAdj = (int) (dyd / dxd * 0x10000);

            int aWeight = 255 - ((anErrAcc >>> 8) & 0xFF);

            int a = weightA(aWeight, theColor);
            int oma = 256 - a;
            int dest = aBits[aDestPixels];
            aBits[aDestPixels] = blendAA(color, dest, a, oma);
            while (--dx != 0) {
                anErrAcc += anErrAdj;
                if (anErrAcc >= 0x10000) {
                    anErrAcc &= 0xFFFF;
                    aDestPixels += mWidth;
                    aY0++;
                }
                aDestPixels += aXinc;

                aWeight = 255 - ((anErrAcc >>> 8) & 0xFF);
                int aWeight2 = (aWeight ^ 0xFF);

                dest = aBits[aDestPixels];
                a = weightA(aWeight, theColor);
                oma = 256 - a;

                aBits[aDestPixels] = blendAA(color, dest, a, oma);
                if (aY0 < mHeight - 1) {
                    dest = aBits[aDestPixels + mWidth];
                    a = weightA(aWeight2, theColor);
                    oma = 256 - a;
                    aBits[aDestPixels + mWidth] = blendAA(color, dest, a, oma);
                }
            }
        } else {
            // mostly vertical
            int anErrAdj = (int) (dxd / dyd * 0x10000);

            int aWeight = 255 - ((anErrAcc >>> 8) & 0xFF);

            int a = weightA(aWeight, theColor);
            int oma = 256 - a;
            int dest = aBits[aDestPixels];
            aBits[aDestPixels] = blendAA(color, dest, a, oma);
            while (--dy != 0) {
                anErrAcc += anErrAdj;
                if (anErrAcc >= 0x10000) {
                    anErrAcc &= 0xFFFF;
                    aDestPixels += aXinc;
                    aX0 += aXinc;
                }
                aDestPixels += mWidth;

                aWeight = 255 - ((anErrAcc >>> 8) & 0xFF);
                int aWeight2 = (aWeight ^ 0xFF);

                dest = aBits[aDestPixels];
                a = weightA(aWeight, theColor);
                oma = 256 - a;

                aBits[aDestPixels] = blendAA(color, dest, a, oma);
                if (aX0 < mWidth - 1) {
                    a = weightA(aWeight2, theColor);
                    oma = 256 - a;
                    dest = aBits[aDestPixels + aXinc];
                    aBits[aDestPixels + aXinc] = blendAA(color, dest, a, oma);
                }
            }
        }
        aDestPixels = aY1 * mWidth + aX1;
        int aWeight = 255 - ((anErrAcc >>> 8) & 0xFF);

        int a = weightA(aWeight, theColor);
        int oma = 256 - a;
        int dest = aBits[aDestPixels];
        aBits[aDestPixels] = blendAA(color, dest, a, oma);
    }

    /** Upstream {@code CALC_WEIGHT_A}: {@code (w * (alpha+1)) >> 8}, or plain {@code w} for a 255 alpha. */
    private static int weightA(int theWeight, Color theColor) {
        if (theColor.mAlpha != 255) {
            return (theWeight * (theColor.mAlpha + 1)) >>> 8;
        }
        return theWeight;
    }

    /** Upstream {@code BLEND_PIXEL} (GENERIC_DrawLineAA.inc). */
    private static int blendAA(int color, int dest, int a, int oma) {
        int aDestAlpha = dest >>> 24;
        int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;
        a = (aNewDestAlpha == 0) ? 0 : 255 * a / aNewDestAlpha;
        oma = 256 - a;
        return (aNewDestAlpha << 24)
                | (((((color & 0xFF0000) * a) + ((dest & 0xFF0000) * oma)) >>> 8) & 0xFF0000)
                | (((((color & 0x00FF00) * a) + ((dest & 0x00FF00) * oma)) >>> 8) & 0x00FF00)
                | (((((color & 0x0000FF) * a) + ((dest & 0x0000FF) * oma)) >>> 8) & 0x0000FF);
    }

    // DrawLineAA
    @Override
    public void drawLineAA(double theStartX, double theStartY, double theEndX, double theEndY, Color theColor, int theDrawMode) {
        if (theStartY == theEndY) {
            int aStartX = (int) Math.min(theStartX, theEndX);
            int aEndX = (int) Math.max(theStartX, theEndX);

            fillRect(new Rect(aStartX, (int) theStartY, aEndX - aStartX + 1, (int) theEndY - (int) theStartY + 1), theColor, theDrawMode);
            return;
        } else if (theStartX == theEndX) {
            int aStartY = (int) Math.min(theStartY, theEndY);
            int aEndY = (int) Math.max(theStartY, theEndY);

            fillRect(new Rect((int) theStartX, aStartY, (int) theEndX - (int) theStartX + 1, aEndY - aStartY + 1), theColor, theDrawMode);
            return;
        }

        normalDrawLineAA(theStartX, theStartY, theEndX, theEndY, theColor);

        bitsChanged();
    }

    // ------------------------------------------------------------------
    // straight blits
    // ------------------------------------------------------------------

    // NormalBlt
    public void normalBlt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor) {
        normalBlt(theImage, theX, theY, theSrcRect, theColor, false);
    }

    private void normalBlt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, boolean mirror) {
        theImage.mDrawn = true;

        if (!(theImage instanceof MemoryImage aSrcMemoryImage)) {
            return;
        }

        int[] aDestBits = getBits();
        int[] aSrcBits = aSrcMemoryImage.getBits();
        int aSrcWidth = theImage.mWidth;

        int aSrcLeft = mirror ? theSrcRect.mX + theSrcRect.mWidth - 1 : theSrcRect.mX;
        int aSrcDir = mirror ? -1 : 1;

        int aDestRow = theY * mWidth + theX;

        // PORT-NOTE: upstream's MI_NormalBlt.inc tests the *destination's* mHasAlpha/mHasTrans here
        // (unqualified members), which would silently skip a blit onto an opaque destination when the
        // tint is white; this port tests the source's flags, matching MI_AdditiveBlt.inc and the
        // documented intent of the branch (alpha-aware blit only when the source needs it).
        if ((aSrcMemoryImage.mHasAlpha) || (aSrcMemoryImage.mHasTrans) || (!theColor.equals(Color.White))) {
            if (theColor.equals(Color.White)) {
                for (int y = 0; y < theSrcRect.mHeight; y++) {
                    int aDestPixels = aDestRow;
                    int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                    for (int x = 0; x < theSrcRect.mWidth; x++) {
                        int src = aSrcBits[aSrcPtr];
                        aSrcPtr += aSrcDir;
                        int dest = aDestBits[aDestPixels];

                        int a = src >>> 24;

                        if (a != 0) {
                            int aDestAlpha = dest >>> 24;
                            int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;
                            a = 255 * a / aNewDestAlpha;

                            int oma = 256 - a;

                            aDestBits[aDestPixels++] = (aNewDestAlpha << 24)
                                    | (((((dest & 0x0000FF) * oma) >>> 8) + ((((src & 0x0000FF) * a) >>> 8) & 0x0000FF)))
                                    | (((((dest & 0x00FF00) * oma) >>> 8) + ((((src & 0x00FF00) * a) >>> 8) & 0x00FF00)))
                                    | (((((dest & 0xFF0000) * oma) >>> 8) + ((((src & 0xFF0000) * a) >>> 8) & 0xFF0000)));
                        } else {
                            aDestPixels++;
                        }
                    }

                    aDestRow += mWidth;
                }
            } else {
                int ca = theColor.mAlpha;
                int cr = theColor.mRed;
                int cg = theColor.mGreen;
                int cb = theColor.mBlue;

                for (int y = 0; y < theSrcRect.mHeight; y++) {
                    int aDestPixels = aDestRow;
                    int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                    for (int x = 0; x < theSrcRect.mWidth; x++) {
                        int src = aSrcBits[aSrcPtr];
                        aSrcPtr += aSrcDir;
                        int dest = aDestBits[aDestPixels];

                        int a = ((src >>> 24) * ca) / 255;

                        if (a != 0) {
                            int aDestAlpha = dest >>> 24;
                            int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;

                            a = 255 * a / aNewDestAlpha;

                            int oma = 256 - a;

                            aDestBits[aDestPixels++] = (aNewDestAlpha << 24)
                                    | (((((dest & 0x0000FF) * oma) >>> 8) + ((((src & 0x0000FF) * a * cb) >>> 16) & 0x0000FF)))
                                    | (((((dest & 0x00FF00) * oma) >>> 8) + ((((src & 0x00FF00) * a * cg) >>> 16) & 0x00FF00)))
                                    | (((((dest & 0xFF0000) * oma) >>> 8) + (((((src & 0xFF0000) * a) >>> 8) * cr) >>> 8) & 0xFF0000));
                        } else {
                            aDestPixels++;
                        }
                    }

                    aDestRow += mWidth;
                }
            }
        } else {
            // PORT-NOTE: upstream's MI_NormalBlt.inc:145-222 serves this case from the run-length alpha
            // classification (GetRLAlphaData): a source with neither transparency nor alpha produces
            // only "fully opaque" runs there, i.e. a straight copy. Those RL buffers are dropped in this
            // port, so the copy is written directly.
            for (int y = 0; y < theSrcRect.mHeight; y++) {
                int aDestPixels = aDestRow;
                int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                for (int x = 0; x < theSrcRect.mWidth; x++) {
                    aDestBits[aDestPixels++] = aSrcBits[aSrcPtr];
                    aSrcPtr += aSrcDir;
                }

                aDestRow += mWidth;
            }
        }

        bitsChanged();
    }

    // AdditiveBlt
    public void additiveBlt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor) {
        additiveBlt(theImage, theX, theY, theSrcRect, theColor, false);
    }

    private void additiveBlt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, boolean mirror) {
        theImage.mDrawn = true;

        if (!(theImage instanceof MemoryImage aSrcMemoryImage)) {
            return;
        }

        int[] aDestBits = getBits();
        int[] aSrcBits = aSrcMemoryImage.getBits();
        int aSrcWidth = theImage.mWidth;

        int aSrcLeft = mirror ? theSrcRect.mX + theSrcRect.mWidth - 1 : theSrcRect.mX;
        int aSrcDir = mirror ? -1 : 1;

        int aDestRow = theY * mWidth + theX;

        if (theColor.equals(Color.White)) {
            if (aSrcMemoryImage.mHasAlpha) {
                for (int y = 0; y < theSrcRect.mHeight; y++) {
                    int aDestPixels = aDestRow;
                    int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                    for (int x = 0; x < theSrcRect.mWidth; x++) {
                        int src = aSrcBits[aSrcPtr];
                        aSrcPtr += aSrcDir;
                        int dest = aDestBits[aDestPixels];

                        int a = (src & 0xFF000000) >>> 24;
                        int r = addMax(((dest & 0xFF0000) + (((src & 0xFF0000) * a) >>> 8)) >>> 16);
                        int g = addMax(((dest & 0x00FF00) + (((src & 0x00FF00) * a) >>> 8)) >>> 8);
                        int b = addMax((dest & 0x0000FF) + (((src & 0x0000FF) * a) >>> 8));

                        aDestBits[aDestPixels++] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                    }

                    aDestRow += mWidth;
                }
            } else {
                for (int y = 0; y < theSrcRect.mHeight; y++) {
                    int aDestPixels = aDestRow;
                    int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                    for (int x = 0; x < theSrcRect.mWidth; x++) {
                        int src = aSrcBits[aSrcPtr];
                        aSrcPtr += aSrcDir;
                        int dest = aDestBits[aDestPixels];

                        int r = addMax(((dest & 0xFF0000) + (src & 0xFF0000)) >>> 16);
                        int g = addMax(((dest & 0x00FF00) + (src & 0x00FF00)) >>> 8);
                        int b = addMax((dest & 0x0000FF) + (src & 0x0000FF));

                        aDestBits[aDestPixels++] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                    }

                    aDestRow += mWidth;
                }
            }
        } else {
            int ca = theColor.mAlpha;
            int cr = (theColor.mRed * ca) / 255;
            int cg = (theColor.mGreen * ca) / 255;
            int cb = (theColor.mBlue * ca) / 255;

            if (aSrcMemoryImage.mHasAlpha) {
                for (int y = 0; y < theSrcRect.mHeight; y++) {
                    int aDestPixels = aDestRow;
                    int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                    for (int x = 0; x < theSrcRect.mWidth; x++) {
                        int src = aSrcBits[aSrcPtr];
                        aSrcPtr += aSrcDir;
                        int dest = aDestBits[aDestPixels];

                        int a = (src & 0xFF000000) >>> 24;
                        int r = addMax(((dest & 0xFF0000) + (((((src & 0xFF0000) * cr) >>> 8) * a) >>> 8)) >>> 16);
                        int g = addMax(((dest & 0x00FF00) + (((((src & 0x00FF00) * cg) >>> 8) * a) >>> 8)) >>> 8);
                        int b = addMax((dest & 0x0000FF) + (((((src & 0x0000FF) * cb) >>> 8) * a) >>> 8));

                        aDestBits[aDestPixels++] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                    }

                    aDestRow += mWidth;
                }
            } else {
                for (int y = 0; y < theSrcRect.mHeight; y++) {
                    int aDestPixels = aDestRow;
                    int aSrcPtr = (theSrcRect.mY + y) * aSrcWidth + aSrcLeft;

                    for (int x = 0; x < theSrcRect.mWidth; x++) {
                        int src = aSrcBits[aSrcPtr];
                        aSrcPtr += aSrcDir;
                        int dest = aDestBits[aDestPixels];

                        int r = addMax(((dest & 0xFF0000) + (((src & 0xFF0000) * cr) >>> 8)) >>> 16);
                        int g = addMax(((dest & 0x00FF00) + (((src & 0x00FF00) * cg) >>> 8)) >>> 8);
                        int b = addMax((dest & 0x0000FF) + (((src & 0x0000FF) * cb) >>> 8));

                        aDestBits[aDestPixels++] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                    }

                    aDestRow += mWidth;
                }
            }
        }

        bitsChanged();
    }

    // Blt
    @Override
    public void blt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
        theImage.mDrawn = true;

        Debug.dbgAsserte(theColor.mRed >= 0 && theColor.mRed <= 255);
        Debug.dbgAsserte(theColor.mGreen >= 0 && theColor.mGreen <= 255);
        Debug.dbgAsserte(theColor.mBlue >= 0 && theColor.mBlue <= 255);
        Debug.dbgAsserte(theColor.mAlpha >= 0 && theColor.mAlpha <= 255);

        switch (theDrawMode) {
            case Graphics.DRAWMODE_NORMAL:
                normalBlt(theImage, theX, theY, theSrcRect, theColor);
                break;
            case Graphics.DRAWMODE_ADDITIVE:
                additiveBlt(theImage, theX, theY, theSrcRect, theColor);
                break;
            default:
                break;
        }
    }

    // BltMirror
    /**
     * PORT-NOTE: upstream's {@code MemoryImage} does not override {@code BltMirror} — only
     * {@code GLImage} implements mirroring, through the shader transform in
     * {@code GLInterface::BltMirror} (GLInterface.cpp:1432-1440) — so a software mirror blit is a
     * no-op upstream. {@code Graphics} still routes mirrored draws to this virtual
     * (Graphics.cpp:740/753), and the deliverable for this port requires a working software mirror,
     * so this override mirrors the source rect inside the same destination rect (the source pixels
     * are walked right-to-left; the blend rules are the upstream ones).
     */
    @Override
    public void bltMirror(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
        theImage.mDrawn = true;

        switch (theDrawMode) {
            case Graphics.DRAWMODE_NORMAL:
                normalBlt(theImage, theX, theY, theSrcRect, theColor, true);
                break;
            case Graphics.DRAWMODE_ADDITIVE:
                additiveBlt(theImage, theX, theY, theSrcRect, theColor, true);
                break;
            default:
                break;
        }
    }

    // BltF
    @Override
    public void bltF(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode) {
        theImage.mDrawn = true;

        bltRotated(theImage, theX, theY, theSrcRect, theClipRect, theColor, theDrawMode, 0, 0, 0);
    }

    // ------------------------------------------------------------------
    // rotated / stretched blits
    // ------------------------------------------------------------------

    /**
     * Upstream {@code MemoryImage::BltRotatedClipHelper}. The C++ signature takes {@code theX}/{@code theY}
     * by reference but never writes them, so the Java port takes them by value and writes only the
     * {@code FRect} out-parameter.
     */
    public boolean bltRotatedClipHelper(float theX, float theY, Rect theSrcRect, Rect theClipRect, double theRot, FRect theDestRect, float theRotCenterX, float theRotCenterY) {
        // Clipping Code (this used to be in Graphics::DrawImageRotated)
        float aCos = (float) Math.cos(theRot);
        float aSin = (float) Math.sin(theRot);

        // Map the four corners and find the bounding rectangle
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

            if (px[i] < aMinX) {
                aMinX = px[i];
            }
            if (px[i] > aMaxX) {
                aMaxX = px[i];
            }
            if (py[i] < aMinY) {
                aMinY = py[i];
            }
            if (py[i] > aMaxY) {
                aMaxY = py[i];
            }
        }

        FRect aClipRect = new FRect(theClipRect.mX, theClipRect.mY, theClipRect.mWidth, theClipRect.mHeight);

        FRect aDestRect = new FRect(aMinX, aMinY, aMaxX - aMinX, aMaxY - aMinY).intersection(aClipRect);
        if ((aDestRect.mWidth <= 0) || (aDestRect.mHeight <= 0)) { // nothing to draw
            return false;
        }

        theDestRect.mX = aDestRect.mX;
        theDestRect.mY = aDestRect.mY;
        theDestRect.mWidth = aDestRect.mWidth;
        theDestRect.mHeight = aDestRect.mHeight;
        return true;
    }

    // BltRotated
    @Override
    public void bltRotated(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, double theRot, float theRotCenterX, float theRotCenterY) {
        theImage.mDrawn = true;

        // This BltRotatedClipHelper clipping used to happen in Graphics::DrawImageRotated
        FRect aDestRect = new FRect();
        if (!bltRotatedClipHelper(theX, theY, theSrcRect, theClipRect, theRot, aDestRect, theRotCenterX, theRotCenterY)) {
            return;
        }

        if (theImage instanceof MemoryImage aMemoryImage) {
            bltRotatedCore(aMemoryImage, aDestRect, theSrcRect, theRot, theX, theY, theRotCenterX, theRotCenterY, theColor, theDrawMode == Graphics.DRAWMODE_ADDITIVE);

            bitsChanged();
        }
    }

    /**
     * Upstream {@code BltRotatedHelper.inc} + {@code MI_BltRotated.inc} / {@code MI_BltRotated_Additive.inc}:
     * a bilinear, fixed-point rotated blit.
     */
    private void bltRotatedCore(MemoryImage theImage, FRect aDestRect, Rect theSrcRect, double theRot, float theX, float theY, float theRotCenterX, float theRotCenterY, Color theColor, boolean additive) {
        double aCos = Math.cos(theRot);
        double aSin = Math.sin(theRot);

        int aCosLong = (int) (aCos * 0x10000);
        int aSinLong = (int) (aSin * 0x10000);

        int[] aDestBits = getBits();
        int[] aSrcBits = theImage.getBits();
        int aWidth = theImage.mWidth;
        // Upstream anchors the source pointer at the source rect origin
        // (MemoryImage.cpp:1473: GetBits() + theSrcRect.mX + theSrcRect.mY*mWidth); the fixed-point
        // aU/aV produced below are relative to the source rect, not to the image.
        int aSrcBase = theSrcRect.mY * aWidth + theSrcRect.mX;

        int aDestRow = ((int) aDestRect.mY * mWidth) + (int) aDestRect.mX;

        boolean white = theColor.equals(Color.White);
        // MI_BltRotated.inc: the normal path uses the +1 rounded channel weights.
        int cr = theColor.mRed + 1;
        int cg = theColor.mGreen + 1;
        int cb = theColor.mBlue + 1;
        // MI_BltRotated_Additive.inc: the additive path weights the tint by its alpha instead
        // (cr = (mRed*mAlpha)/255 ...), so it needs its own constants.
        int ca = theColor.mAlpha;
        int acr = (theColor.mRed * ca) / 255;
        int acg = (theColor.mGreen * ca) / 255;
        int acb = (theColor.mBlue * ca) / 255;

        for (int y = 0; y < (int) aDestRect.mHeight; y++, aDestRow += mWidth) {
            float aBaseX = (int) aDestRect.mX - theRotCenterX - theX;
            float aBaseY = ((int) aDestRect.mY + y) - theRotCenterY - theY;

            int aU = (int) ((aCos * aBaseX - aSin * aBaseY + theRotCenterX) * 0x10000);
            int aV = (int) ((aCos * aBaseY + aSin * aBaseX + theRotCenterY) * 0x10000);

            int aDestPixels = aDestRow;

            int aLeft = 0;
            int aRight = (int) aDestRect.mWidth - 1;

            int aUInt = aU >> 16;
            int aVInt = aV >> 16;

            if (aCosLong <= -0x0001) {
                if (aUInt < 0) {
                    continue;
                }

                int anEdgeLeft = (((theSrcRect.mWidth << 16) - 0x27FFF - aU) / aCosLong);
                if (anEdgeLeft >= aLeft) {
                    aLeft = anEdgeLeft;
                }

                int anEdgeRight = ((0x07FFF - aU) / aCosLong);
                if (anEdgeRight < aRight) {
                    aRight = anEdgeRight;
                }
            } else if (aCosLong >= 0x0001) {
                if (aUInt >= theSrcRect.mWidth - 1) {
                    continue;
                }

                int anEdgeLeft = ((0x10000 - aU) / aCosLong);
                if (anEdgeLeft > aLeft) {
                    aLeft = anEdgeLeft;
                }

                int anEdgeRight = (((theSrcRect.mWidth << 16) - 0x17FFF - aU) / aCosLong);
                if (anEdgeRight < aRight) {
                    aRight = anEdgeRight;
                }
            } else if ((aU < 0) || (aU >= (theSrcRect.mWidth - 1) << 16)) {
                continue;
            }

            if (aSinLong <= -0x0001) {
                if (aVInt < 0) {
                    continue;
                }

                int anEdgeLeft = (((theSrcRect.mHeight << 16) - 0x27FFF - aV) / aSinLong);
                if (anEdgeLeft > aLeft) {
                    aLeft = anEdgeLeft;
                }

                int anEdgeRight = ((0x0000 - aV) / aSinLong);
                if (anEdgeRight < aRight) {
                    aRight = anEdgeRight;
                }
            } else if (aSinLong >= 0x0001) {
                if (aVInt >= theSrcRect.mHeight - 1) {
                    continue;
                }

                int anEdgeLeft = ((0x10000 - aV) / aSinLong);
                if (anEdgeLeft > aLeft) {
                    aLeft = anEdgeLeft;
                }

                int anEdgeRight = (((theSrcRect.mHeight << 16) - 0x17FFF - aV) / aSinLong);
                if (anEdgeRight < aRight) {
                    aRight = anEdgeRight;
                }
            } else if ((aV < 0) || (aV >= (theSrcRect.mHeight - 1) << 16)) {
                continue;
            }

            aU += aLeft * aCosLong;
            aV += aLeft * aSinLong;
            aDestPixels += aLeft;

            for (int x = aLeft; x <= aRight; x++) {
                int anUInt = aU >> 16;
                int aVInt2 = aV >> 16;

                int srcptr = aSrcBase + aVInt2 * aWidth + anUInt;

                int aUFactor = (aU & 0xFFFE) + 1; // aUFactor needs to be between 1 and 0xFFFF to avoid overflow
                int aVFactor = (aV & 0xFFFE) + 1; // ditto for aVFactor

                int src1 = aSrcBits[srcptr];
                int src2 = aSrcBits[srcptr + 1];
                int src3 = aSrcBits[srcptr + aWidth];
                int src4 = aSrcBits[srcptr + 1 + aWidth];

                int a1 = ((src1 >>> 24) * (((0x10000 - aUFactor) * (0x10000 - aVFactor)) >>> 16)) >>> 16;
                int a2 = ((src2 >>> 24) * ((aUFactor * (0x10000 - aVFactor)) >>> 16)) >>> 16;
                int a3 = ((src3 >>> 24) * (((0x10000 - aUFactor) * aVFactor) >>> 16)) >>> 16;
                int a4 = ((src4 >>> 24) * ((aUFactor * aVFactor) >>> 16)) >>> 16;

                int dest = aDestBits[aDestPixels];

                if (additive) {
                    if (white) {
                        int a = a1 + a2 + a3 + a4;
                        if (a == 0) {
                            aDestPixels++;
                        } else {
                            int r = addMax(((((src1 & 0xFF0000) * a1) + ((src2 & 0xFF0000) * a2) + ((src3 & 0xFF0000) * a3) + ((src4 & 0xFF0000) * a4)) >>> 24) + ((dest & 0xFF0000) >>> 16));
                            int g = addMax(((((src1 & 0x00FF00) * a1) + ((src2 & 0x00FF00) * a2) + ((src3 & 0x00FF00) * a3) + ((src4 & 0x00FF00) * a4)) >>> 16) + ((dest & 0x00FF00) >>> 8));
                            int b = addMax(((((src1 & 0x0000FF) * a1) + ((src2 & 0x0000FF) * a2) + ((src3 & 0x0000FF) * a3) + ((src4 & 0x0000FF) * a4)) >>> 8) + (dest & 0x0000FF));

                            aDestBits[aDestPixels++] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                        }
                    } else {
                        int a = a1 + a2 + a3 + a4;
                        if (a == 0) {
                            aDestPixels++;
                        } else {
                            // MI_BltRotated_Additive.inc non-white branch: the tint is premultiplied by
                            // its own alpha (cr = (mRed*mAlpha)/255), not the +1 rounded normal weights.
                            int r = addMax((((((src1 & 0xFF0000) * a1) + ((src2 & 0xFF0000) * a2) + ((src3 & 0xFF0000) * a3) + ((src4 & 0xFF0000) * a4)) >>> 24) * acr >>> 8) + ((dest & 0xFF0000) >>> 16));
                            int g = addMax(((((src1 & 0x00FF00) * a1) + ((src2 & 0x00FF00) * a2) + ((src3 & 0x00FF00) * a3) + ((src4 & 0x00FF00) * a4)) * acg >>> 24) + ((dest & 0x00FF00) >>> 8));
                            int b = addMax(((((src1 & 0x0000FF) * a1) + ((src2 & 0x0000FF) * a2) + ((src3 & 0x0000FF) * a3) + ((src4 & 0x0000FF) * a4)) * acb >>> 16) + (dest & 0x0000FF));

                            aDestBits[aDestPixels++] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                        }
                    }
                } else {
                    if (white) {
                        int a = a1 + a2 + a3 + a4;
                        if (a == 0) {
                            aDestPixels++;
                        } else {
                            int r = ((((src1 & 0xFF0000) * a1) + ((src2 & 0xFF0000) * a2) + ((src3 & 0xFF0000) * a3) + ((src4 & 0xFF0000) * a4)) & 0xFF000000);
                            int g = ((((src1 & 0x00FF00) * a1) + ((src2 & 0x00FF00) * a2) + ((src3 & 0x00FF00) * a3) + ((src4 & 0x00FF00) * a4)) & 0x00FF0000);
                            int b = ((((src1 & 0x0000FF) * a1) + ((src2 & 0x0000FF) * a2) + ((src3 & 0x0000FF) * a3) + ((src4 & 0x0000FF) * a4)) & 0x0000FF00);
                            if (a > 250) { // opaque
                                aDestBits[aDestPixels++] = 0xFF000000 | (r >>> 8) | (g >>> 8) | (b >>> 8);
                            } else { // blend
                                int destPixel = dest;
                                int da = destPixel >>> 24;

                                int dr = (((destPixel & 0xFF0000) * da) >>> 8) & 0xFF0000;
                                int dg = (((destPixel & 0x00FF00) * da) >>> 8) & 0x00FF00;
                                int db = (((destPixel & 0x0000FF) * da) >>> 8) & 0x0000FF;

                                int finalAlpha = 256 - (((256 - a) * (256 - da)) >>> 8);
                                r = ((r + (256 - a) * dr) / finalAlpha) & 0xFF0000;
                                g = ((g + (256 - a) * dg) / finalAlpha) & 0x00FF00;
                                b = ((b + (256 - a) * db) / finalAlpha) & 0x0000FF;

                                aDestBits[aDestPixels++] = ((finalAlpha - 1) << 24) | r | g | b;
                            }
                        }
                    } else {
                        a1 = (a1 * ca) >>> 8;
                        a2 = (a2 * ca) >>> 8;
                        a3 = (a3 * ca) >>> 8;
                        a4 = (a4 * ca) >>> 8;
                        int a = a1 + a2 + a3 + a4;

                        if (a == 0) { // transparent
                            aDestPixels++;
                        } else {
                            int r = (cr * ((((src1 & 0xFF0000) * a1) + ((src2 & 0xFF0000) * a2) + ((src3 & 0xFF0000) * a3) + ((src4 & 0xFF0000) * a4)) >>> 8)) & 0xFF000000;
                            int g = (((((src1 & 0x00FF00) * a1) + ((src2 & 0x00FF00) * a2) + ((src3 & 0x00FF00) * a3) + ((src4 & 0x00FF00) * a4)) & 0x00FF0000) * cg) & 0xFF000000;
                            int b = (((((src1 & 0x0000FF) * a1) + ((src2 & 0x0000FF) * a2) + ((src3 & 0x0000FF) * a3) + ((src4 & 0x0000FF) * a4)) & 0x0000FF00) * cb) & 0x00FF0000;
                            if (a > 250) { // opaque
                                aDestBits[aDestPixels++] = 0xFF000000 | (r >>> 8) | (g >>> 16) | (b >>> 16);
                            } else { // blend
                                int destPixel = dest;
                                int da = destPixel >>> 24;

                                int dr = (((destPixel & 0xFF0000) * da) >>> 8) & 0xFF0000;
                                int dg = (((destPixel & 0x00FF00) * da) >>> 8) & 0x00FF00;
                                int db = (((destPixel & 0x0000FF) * da) >>> 8) & 0x0000FF;

                                int finalAlpha = 256 - (((256 - a) * (256 - da)) >>> 8);
                                r = ((r + (256 - a) * dr) / finalAlpha) & 0xFF0000;
                                g = (((g >>> 8) + (256 - a) * dg) / finalAlpha) & 0x00FF00;
                                b = (((b >>> 8) + (256 - a) * db) / finalAlpha) & 0x0000FF;

                                aDestBits[aDestPixels++] = ((finalAlpha - 1) << 24) | r | g | b;
                            }
                        }
                    }
                }

                aU += aCosLong;
                aV += aSinLong;
            }
        }
    }

    // StretchBltClipHelper
    public boolean stretchBltClipHelper(Rect theSrcRect, Rect theClipRect, Rect theDestRect, FRect theSrcRectOut, Rect theDestRectOut) {
        Rect aDestOut = new Rect(theDestRect.mX, theDestRect.mY, theDestRect.mWidth, theDestRect.mHeight).intersection(theClipRect);
        theDestRectOut.mX = aDestOut.mX;
        theDestRectOut.mY = aDestOut.mY;
        theDestRectOut.mWidth = aDestOut.mWidth;
        theDestRectOut.mHeight = aDestOut.mHeight;

        double aXFactor = theSrcRect.mWidth / (double) theDestRect.mWidth;
        double aYFactor = theSrcRect.mHeight / (double) theDestRect.mHeight;

        theSrcRectOut.mX = theSrcRect.mX + (theDestRectOut.mX - theDestRect.mX) * aXFactor;
        theSrcRectOut.mY = theSrcRect.mY + (theDestRectOut.mY - theDestRect.mY) * aYFactor;
        theSrcRectOut.mWidth = theSrcRect.mWidth + (theDestRectOut.mWidth - theDestRect.mWidth) * aXFactor;
        theSrcRectOut.mHeight = theSrcRect.mHeight + (theDestRectOut.mHeight - theDestRect.mHeight) * aYFactor;

        return theSrcRectOut.mWidth > 0 && theSrcRectOut.mHeight > 0;
    }

    // StretchBltMirrorClipHelper
    public boolean stretchBltMirrorClipHelper(Rect theSrcRect, Rect theClipRect, Rect theDestRect, FRect theSrcRectOut, Rect theDestRectOut) {
        Rect aDestOut = new Rect(theDestRect.mX, theDestRect.mY, theDestRect.mWidth, theDestRect.mHeight).intersection(theClipRect);
        theDestRectOut.mX = aDestOut.mX;
        theDestRectOut.mY = aDestOut.mY;
        theDestRectOut.mWidth = aDestOut.mWidth;
        theDestRectOut.mHeight = aDestOut.mHeight;

        double aXFactor = theSrcRect.mWidth / (double) theDestRect.mWidth;
        double aYFactor = theSrcRect.mHeight / (double) theDestRect.mHeight;

        int aTotalClip = theDestRect.mWidth - theDestRectOut.mWidth;
        int aLeftClip = theDestRectOut.mX - theDestRect.mX;
        int aRightClip = aTotalClip - aLeftClip;

        theSrcRectOut.mX = theSrcRect.mX + (aRightClip) * aXFactor;
        theSrcRectOut.mY = theSrcRect.mY + (theDestRectOut.mY - theDestRect.mY) * aYFactor;
        theSrcRectOut.mWidth = theSrcRect.mWidth + (theDestRectOut.mWidth - theDestRect.mWidth) * aXFactor;
        theSrcRectOut.mHeight = theSrcRect.mHeight + (theDestRectOut.mHeight - theDestRect.mHeight) * aYFactor;

        return theSrcRectOut.mWidth > 0 && theSrcRectOut.mHeight > 0;
    }

    // SlowStretchBlt
    public void slowStretchBlt(Image theImage, Rect theDestRect, FRect theSrcRect, Color theColor, int theDrawMode) {
        theImage.mDrawn = true;

        if (!(theImage instanceof MemoryImage aSrcMemoryImage)) {
            return;
        }

        int[] aDestBits = getBits();
        int[] aSrcBits = aSrcMemoryImage.getBits();
        int aSrcRowWidth = aSrcMemoryImage.getWidth();

        int aSrcXI = (int) Math.floor(theSrcRect.mX);
        int aSrcYI = (int) Math.floor(theSrcRect.mY);
        int aSrcWidthI = (int) Math.ceil(theSrcRect.mWidth + (theSrcRect.mX - aSrcXI));
        int aSrcHeightI = (int) Math.ceil(theSrcRect.mHeight + (theSrcRect.mY - aSrcYI));

        if (aSrcXI < 0) {
            aSrcXI = 0;
        }
        if (aSrcYI < 0) {
            aSrcYI = 0;
        }
        if (aSrcXI + aSrcWidthI > theImage.mWidth) {
            aSrcWidthI = theImage.mWidth - aSrcXI;
        }
        if (aSrcYI + aSrcHeightI > theImage.mHeight) {
            aSrcHeightI = theImage.mHeight - aSrcYI;
        }
        if (aSrcWidthI <= 0 || aSrcHeightI <= 0) {
            return;
        }

        int aTempDestWidth = theDestRect.mWidth + 4;
        int aTempDestHeight = theDestRect.mHeight + 4;

        // For holding horizontally resized pixels not vertically (yet)
        int[] aNewHorzPixels = new int[aTempDestWidth * aSrcHeightI * 4];

        if (theSrcRect.mWidth >= theDestRect.mWidth) {
            double aDestXFactor = theDestRect.mWidth / theSrcRect.mWidth;
            double aDestXOffset = 1.0 + (aSrcXI - theSrcRect.mX) * aDestXFactor;

            // Shrinking
            for (int aSrcX = 0; aSrcX < aSrcWidthI; aSrcX++) {
                double aDestX1 = aDestXFactor * aSrcX + aDestXOffset;
                double aDestX2 = aDestX1 + aDestXFactor;

                int aDestXI1 = (int) aDestX1;
                int aDestXI2 = (int) aDestX2;

                int s1 = aSrcYI * aSrcRowWidth + aSrcXI + aSrcX;

                if (aDestXI1 == aDestXI2) {
                    int d = aDestXI1 * 4;
                    int aFactor = (int) (257 * aDestXFactor);

                    for (int aSrcY = 0; aSrcY < aSrcHeightI; aSrcY++) {
                        int pixel = aSrcBits[s1];

                        aNewHorzPixels[d++] += aFactor * (pixel & 0xFF);
                        aNewHorzPixels[d++] += aFactor * ((pixel >>> 8) & 0xFF);
                        aNewHorzPixels[d++] += aFactor * ((pixel >>> 16) & 0xFF);
                        aNewHorzPixels[d++] += aFactor * (pixel >>> 24);

                        d += aTempDestWidth * 4 - 4;
                        s1 += aSrcRowWidth;
                    }
                } else {
                    int aFactor1 = (int) (257 * (aDestXI2 - aDestX1));
                    int aFactor2 = (int) (257 * (aDestX2 - aDestXI2));

                    int d = aDestXI1 * 4;

                    for (int aSrcY = 0; aSrcY < aSrcHeightI; aSrcY++) {
                        int pixel = aSrcBits[s1];

                        aNewHorzPixels[d++] += aFactor1 * (pixel & 0xFF);
                        aNewHorzPixels[d++] += aFactor1 * ((pixel >>> 8) & 0xFF);
                        aNewHorzPixels[d++] += aFactor1 * ((pixel >>> 16) & 0xFF);
                        aNewHorzPixels[d++] += aFactor1 * (pixel >>> 24);

                        aNewHorzPixels[d++] += aFactor2 * (pixel & 0xFF);
                        aNewHorzPixels[d++] += aFactor2 * ((pixel >>> 8) & 0xFF);
                        aNewHorzPixels[d++] += aFactor2 * ((pixel >>> 16) & 0xFF);
                        aNewHorzPixels[d++] += aFactor2 * (pixel >>> 24);

                        d += aTempDestWidth * 4 - 8;
                        s1 += aSrcRowWidth;
                    }
                }
            }
        } else {
            double aSrcXFactor;
            if (theDestRect.mWidth != 1) {
                aSrcXFactor = (theSrcRect.mWidth - 1) / (theDestRect.mWidth - 1);
            } else {
                aSrcXFactor = (theSrcRect.mWidth) / (theDestRect.mWidth);
            }

            for (int aDestX = 1; aDestX < aTempDestWidth - 1; aDestX++) {
                int d = aDestX * 4;

                double aSrcX = (aDestX - 1) * aSrcXFactor + theSrcRect.mX;
                int aSrcXI2 = (int) aSrcX;

                int aFactor1 = (int) (257 * (1.0 - (aSrcX - aSrcXI2)));
                int aFactor2 = 257 - aFactor1;

                int s = aSrcYI * aSrcRowWidth + aSrcXI2;

                for (int aDestY = 0; aDestY < aSrcHeightI; aDestY++) {
                    int pixel1 = aSrcBits[s++];
                    int pixel2 = aSrcBits[s];

                    aNewHorzPixels[d++] = (aFactor1 * (pixel1 & 0xFF)) + (aFactor2 * (pixel2 & 0xFF));
                    aNewHorzPixels[d++] = (aFactor1 * ((pixel1 >>> 8) & 0xFF)) + (aFactor2 * ((pixel2 >>> 8) & 0xFF));
                    aNewHorzPixels[d++] = (aFactor1 * ((pixel1 >>> 16) & 0xFF)) + (aFactor2 * ((pixel2 >>> 16) & 0xFF));
                    aNewHorzPixels[d++] = (aFactor1 * (pixel1 >>> 24)) + (aFactor2 * (pixel2 >>> 24));

                    d += aTempDestWidth * 4 - 4;
                    s += aSrcRowWidth - 1;
                }
            }
        }

        int[] aNewPixels = new int[aTempDestWidth * aTempDestHeight * 4];

        // Now resize vertically
        if (theSrcRect.mHeight >= theDestRect.mHeight) {
            double aDestYFactor = theDestRect.mHeight / theSrcRect.mHeight;

            double aDestYOffset = 1.0 + (aSrcYI - theSrcRect.mY) * aDestYFactor;

            for (int aSrcY = 0; aSrcY < aSrcHeightI; aSrcY++) {
                double aDestY1 = aDestYFactor * aSrcY + aDestYOffset;
                double aDestY2 = aDestY1 + aDestYFactor;

                int aDestYI1 = (int) Math.floor(aDestY1);
                int aDestYI2 = (int) Math.floor(aDestY2);

                int s = aSrcY * aTempDestWidth * 4;

                if (aDestYI1 == aDestYI2) {
                    int d = aDestYI1 * aTempDestWidth * 4;
                    int aFactor = (int) (256 * aDestYFactor);

                    for (int aSrcX = 0; aSrcX < aTempDestWidth; aSrcX++) {
                        aNewPixels[d++] += aFactor * aNewHorzPixels[s++];
                        aNewPixels[d++] += aFactor * aNewHorzPixels[s++];
                        aNewPixels[d++] += aFactor * aNewHorzPixels[s++];
                        aNewPixels[d++] += aFactor * aNewHorzPixels[s++];
                    }
                } else {
                    int aFactor1 = (int) (256 * (aDestYI2 - aDestY1));
                    int aFactor2 = (int) (256 * (aDestY2 - aDestYI2));

                    int d1 = aDestYI1 * aTempDestWidth * 4;
                    int d2 = aDestYI2 * aTempDestWidth * 4;

                    for (int aSrcX = 0; aSrcX < aTempDestWidth; aSrcX++) {
                        aNewPixels[d1++] += aFactor1 * aNewHorzPixels[s];
                        aNewPixels[d2++] += aFactor2 * aNewHorzPixels[s++];

                        aNewPixels[d1++] += aFactor1 * aNewHorzPixels[s];
                        aNewPixels[d2++] += aFactor2 * aNewHorzPixels[s++];

                        aNewPixels[d1++] += aFactor1 * aNewHorzPixels[s];
                        aNewPixels[d2++] += aFactor2 * aNewHorzPixels[s++];

                        aNewPixels[d1++] += aFactor1 * aNewHorzPixels[s];
                        aNewPixels[d2++] += aFactor2 * aNewHorzPixels[s++];
                    }
                }
            }
        } else {
            double aSrcYFactor;
            if (theDestRect.mHeight != 1) {
                aSrcYFactor = (theSrcRect.mHeight - 1) / (theDestRect.mHeight - 1);
            } else {
                aSrcYFactor = (theSrcRect.mHeight) / (theDestRect.mHeight);
            }

            for (int aDestY = 1; aDestY < theDestRect.mHeight + 1; aDestY++) {
                int d = (aDestY * aTempDestWidth + 1) * 4;

                double aSrcY = (aDestY - 1) * aSrcYFactor + (theSrcRect.mY - ((int) theSrcRect.mY));
                int aSrcYI2 = (int) aSrcY;

                int aFactor1 = (int) (256 * (1.0 - (aSrcY - aSrcYI2)));
                int aFactor2 = 256 - aFactor1;

                int s1 = (aSrcYI2 * aTempDestWidth + 1) * 4;
                int s2;

                if (aSrcYI2 == aSrcHeightI - 1) {
                    s2 = s1;
                } else {
                    s2 = ((aSrcYI2 + 1) * aTempDestWidth + 1) * 4;
                }

                for (int aDestX = 1; aDestX < aTempDestWidth - 1; aDestX++) {
                    aNewPixels[d++] = (aFactor1 * aNewHorzPixels[s1++]) + (aFactor2 * aNewHorzPixels[s2++]);
                    aNewPixels[d++] = (aFactor1 * aNewHorzPixels[s1++]) + (aFactor2 * aNewHorzPixels[s2++]);
                    aNewPixels[d++] = (aFactor1 * aNewHorzPixels[s1++]) + (aFactor2 * aNewHorzPixels[s2++]);
                    aNewPixels[d++] = (aFactor1 * aNewHorzPixels[s1++]) + (aFactor2 * aNewHorzPixels[s2++]);
                }
            }
        }

        for (int y = 0; y < theDestRect.mHeight; y++) {
            int aDestPixels = (theDestRect.mY + y) * mWidth + theDestRect.mX;

            for (int x = 0; x < theDestRect.mWidth; x++) {
                int p = ((y + 1) * aTempDestWidth + x + 1) * 4;

                int b = aNewPixels[p++] >>> 16;
                int g = aNewPixels[p++] >>> 16;
                int r = aNewPixels[p++] >>> 16;
                int a = aNewPixels[p++] >>> 16;

                if (a != 0) {
                    int dest = aDestBits[aDestPixels];
                    int aDestAlpha = dest >>> 24;
                    int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;

                    a = 255 * a / aNewDestAlpha;
                    int oma = 256 - a;

                    aDestBits[aDestPixels++] = (aNewDestAlpha << 24)
                            | (((((dest & 0x0000FF) * oma) >>> 8) & 0x0000FF) + ((b * a) >>> 8))
                            | (((((dest & 0x00FF00) * oma) >>> 8) & 0x00FF00) + (((g * a) >>> 8) << 8))
                            | (((((dest & 0xFF0000) * oma) >>> 8) & 0xFF0000) + (((r * a) >>> 8) << 16));
                } else {
                    aDestPixels++;
                }
            }
        }
    }

    // FastStretchBlt
    public void fastStretchBlt(Image theImage, Rect theDestRect, FRect theSrcRect, Color theColor, int theDrawMode) {
        theImage.mDrawn = true;

        if (!(theImage instanceof MemoryImage aSrcMemoryImage)) {
            return;
        }

        int[] aDestBits = getBits();
        int[] aSrcBits = aSrcMemoryImage.getBits();

        int aDestPixelsRow = theDestRect.mY * mWidth + theDestRect.mX;

        double aSrcY = theSrcRect.mY;

        double anAddX = theSrcRect.mWidth / theDestRect.mWidth;
        double anAddY = theSrcRect.mHeight / theDestRect.mHeight;

        if (theColor.equals(Color.White)) {
            for (int y = 0; y < theDestRect.mHeight; y++) {
                double aSrcX = theSrcRect.mX;

                int aDestPixels = aDestPixelsRow;

                for (int x = 0; x < theDestRect.mWidth; x++) {
                    aSrcX += anAddX;

                    int aSrcPixels = ((int) aSrcX) + (aSrcMemoryImage.mWidth * ((int) aSrcY));
                    int src = aSrcBits[aSrcPixels];

                    int dest = aDestBits[aDestPixels];

                    int a = src >>> 24;

                    if (a != 0) {
                        int aDestAlpha = dest >>> 24;
                        int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;

                        a = 255 * a / aNewDestAlpha;

                        int oma = 256 - a;

                        aDestBits[aDestPixels++] = (aNewDestAlpha << 24)
                                | (((((dest & 0x0000FF) * oma) >>> 8) + (((src & 0x0000FF) * a) >>> 8) & 0x0000FF))
                                | (((((dest & 0x00FF00) * oma) >>> 8) + (((src & 0x00FF00) * a) >>> 8) & 0x00FF00))
                                | (((((dest & 0xFF0000) * oma) >>> 8) + (((src & 0xFF0000) * a) >>> 8) & 0xFF0000));
                    } else {
                        aDestPixels++;
                    }
                }

                aDestPixelsRow += mWidth;
                aSrcY += anAddY;
            }
        }
        // PORT-NOTE: upstream's colorized FastStretchBlt branch is empty (MemoryImage.cpp:1590-1604, marked
        // "TODO: Make the special version"), so a colorized fast stretch draws nothing and that is reproduced here.

        bitsChanged();
    }

    // StretchBlt
    @Override
    public void stretchBlt(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
        theImage.mDrawn = true;

        Rect aDestRect = new Rect();
        FRect aSrcRect = new FRect();

        if (!stretchBltClipHelper(theSrcRect, theClipRect, theDestRect, aSrcRect, aDestRect)) {
            return;
        }

        if (fastStretch) {
            fastStretchBlt(theImage, aDestRect, aSrcRect, theColor, theDrawMode);
        } else {
            slowStretchBlt(theImage, aDestRect, aSrcRect, theColor, theDrawMode);
        }
    }

    // StretchBltMirror
    @Override
    public void stretchBltMirror(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
        theImage.mDrawn = true;

        Rect aDestRect = new Rect();
        FRect aSrcRect = new FRect();

        if (!stretchBltMirrorClipHelper(theSrcRect, theClipRect, theDestRect, aSrcRect, aDestRect)) {
            return;
        }

        if (!(theImage instanceof MemoryImage)) {
            return;
        }

        // PORT-NOTE: upstream has no software mirror-stretch (only GLImage implements StretchBltMirror,
        // GLImage.cpp:207-213, which mirrors in the shader). Mirroring the source pixels into a scratch
        // buffer is exactly equivalent to sampling the source mirrored, and keeps the upstream separable
        // resampler untouched.
        MemoryImage aMirrored = mirrorSource(theImage);

        if (fastStretch) {
            fastStretchBlt(aMirrored, aDestRect, aSrcRect, theColor, theDrawMode);
        } else {
            slowStretchBlt(aMirrored, aDestRect, aSrcRect, theColor, theDrawMode);
        }
    }

    private static MemoryImage mirrorSource(Image theImage) {
        MemoryImage aSrc = (MemoryImage) theImage;
        // PORT-NOTE: a scratch image is created with a null app on purpose. Upstream registers every
        // MemoryImage with the app (Init -> AddMemoryImage) and unregisters it in the destructor;
        // Java has no destructor, so registering per-call scratch images would grow
        // SexyAppBase.mMemoryImageSet without bound.
        MemoryImage aMirrored = new MemoryImage(null);
        aMirrored.create(aSrc.mWidth, aSrc.mHeight);

        int[] aSrcBits = aSrc.getBits();
        int[] aDestBits = aMirrored.getBits();

        for (int y = 0; y < aSrc.mHeight; y++) {
            int aSrcRow = y * aSrc.mWidth;
            int aDestRow = y * aSrc.mWidth;
            for (int x = 0; x < aSrc.mWidth; x++) {
                aDestBits[aDestRow + (aSrc.mWidth - 1 - x)] = aSrcBits[aSrcRow + x];
            }
        }

        aMirrored.bitsChanged();
        return aMirrored;
    }

    // ------------------------------------------------------------------
    // textured matrix / triangle blits (software fallback, see the class PORT-NOTE)
    // ------------------------------------------------------------------

    // BltMatrix
    @Override
    public void bltMatrix(Image theImage, float x, float y, SexyMatrix3 theMatrix, Rect theClipRect, Color theColor, int theDrawMode, Rect theSrcRect, boolean blend) {
        theImage.mDrawn = true;

        if (!(theImage instanceof MemoryImage anImage)) {
            return;
        }

        float w2 = theSrcRect.mWidth / 2.0f;
        float h2 = theSrcRect.mHeight / 2.0f;

        float u0 = (float) theSrcRect.mX / theImage.mWidth;
        float u1 = (float) (theSrcRect.mX + theSrcRect.mWidth) / theImage.mWidth;
        float v0 = (float) theSrcRect.mY / theImage.mHeight;
        float v1 = (float) (theSrcRect.mY + theSrcRect.mHeight) / theImage.mHeight;

        float[] px = {-w2, w2, -w2, w2};
        float[] py = {-h2, -h2, h2, h2};
        float[] u = {u0, u1, u0, u1};
        float[] v = {v0, v0, v1, v1};

        for (int i = 0; i < 4; i++) {
            float tx = theMatrix.m00 * px[i] + theMatrix.m01 * py[i] + theMatrix.m02;
            float ty = theMatrix.m10 * px[i] + theMatrix.m11 * py[i] + theMatrix.m12;
            px[i] = tx + x - 0.5f;
            py[i] = ty + y - 0.5f;
        }

        // Two triangles: (0,1,3) and (0,3,2)
        drawTexTriangle(anImage, px[0], py[0], u[0], v[0],
                px[1], py[1], u[1], v[1],
                px[3], py[3], u[3], v[3],
                theColor, theDrawMode, theClipRect);
        drawTexTriangle(anImage, px[0], py[0], u[0], v[0],
                px[3], py[3], u[3], v[3],
                px[2], py[2], u[2], v[2],
                theColor, theDrawMode, theClipRect);

        bitsChanged();
    }

    // BltTrianglesTex
    @Override
    public void bltTrianglesTex(Image theTexture, TriVertex[][] theVertices, int theNumTriangles, Rect theClipRect, Color theColor, int theDrawMode, float tx, float ty, boolean blend) {
        theTexture.mDrawn = true;

        if (!(theTexture instanceof MemoryImage anImage)) {
            return;
        }

        for (int i = 0; i < theNumTriangles; i++) {
            TriVertex v0 = theVertices[i][0];
            TriVertex v1 = theVertices[i][1];
            TriVertex v2 = theVertices[i][2];

            int vertexColor = v0.color != 0 ? v0.color : (v1.color != 0 ? v1.color : v2.color);

            drawTexTriangle(anImage,
                    v0.x + tx, v0.y + ty, v0.u, v0.v,
                    v1.x + tx, v1.y + ty, v1.u, v1.v,
                    v2.x + tx, v2.y + ty, v2.u, v2.v,
                    vertexColor != 0 ? new Color(vertexColor, vertexColor >>> 24) : theColor,
                    theDrawMode, theClipRect);
        }

        bitsChanged();
    }

    /**
     * Barycentric textured-triangle rasterizer replacing upstream's {@code SWTri::SWDrawShape}
     * (see the class PORT-NOTE): nearest-texel sampling, flat colour modulation, the upstream
     * alpha cull / opaque-store fragment rules ({@code SWTri_Pixel8888.inc:31-76}) and the
     * normal/additive blend rules.
     *
     * <p>PORT-NOTE: two upstream features are intentionally not reproduced here. (1) Per-vertex
     * colours are not interpolated: the caller passes one flat {@link Color} per triangle
     * ({@code bltTrianglesTex} collapses upstream's per-vertex diffuse to the first non-zero vertex
     * colour), and (2) {@code TriVertex.z} — upstream's per-triangle additive marker — is ignored,
     * the draw mode selects additive instead. The rasterizer is only reachable for CPU-backed
     * targets; {@link GLImage} draws triangles through {@code RendererBackend.drawTriangles}.
     */
    private void drawTexTriangle(MemoryImage aSrc, float x0, float y0, float u0, float v0, float x1, float y1, float u1, float v1, float x2, float y2, float u2, float v2, Color theColor, int theDrawMode, Rect theClipRect) {
        float aMinX = Math.max(theClipRect.mX, Math.min(x0, Math.min(x1, x2)));
        float aMaxX = Math.min(theClipRect.mX + theClipRect.mWidth, Math.max(x0, Math.max(x1, x2)));
        float aMinY = Math.max(theClipRect.mY, Math.min(y0, Math.min(y1, y2)));
        float aMaxY = Math.min(theClipRect.mY + theClipRect.mHeight, Math.max(y0, Math.max(y1, y2)));

        int aLeft = Math.max(0, (int) Math.ceil(aMinX));
        int aRight = Math.min(mWidth - 1, (int) Math.floor(aMaxX));
        int aTop = Math.max(0, (int) Math.ceil(aMinY));
        int aBottom = Math.min(mHeight - 1, (int) Math.floor(aMaxY));

        float aArea = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        if (aArea == 0) {
            return;
        }
        if (aArea < 0) {
            // Keep the winding consistent so the barycentric sign test works for both.
            float t;
            t = x1;
            x1 = x2;
            x2 = t;
            t = y1;
            y1 = y2;
            y2 = t;
            t = u1;
            u1 = u2;
            u2 = t;
            t = v1;
            v1 = v2;
            v2 = t;
            aArea = -aArea;
        }

        int[] aDestBits = getBits();
        int[] aSrcBits = aSrc.getBits();
        int aTextureWidth = aSrc.mWidth;
        int aTextureHeight = aSrc.mHeight;

        int aColor = theColor.toInt();
        boolean white = theColor.equals(Color.White);
        boolean additive = theDrawMode == Graphics.DRAWMODE_ADDITIVE;

        for (int y = aTop; y <= aBottom; y++) {
            int aDestPixels = y * mWidth + aLeft;
            for (int x = aLeft; x <= aRight; x++, aDestPixels++) {
                float aPX = x + 0.5f;
                float aPY = y + 0.5f;

                float aW0 = ((x1 - aPX) * (y2 - aPY) - (y1 - aPY) * (x2 - aPX)) / aArea;
                if (aW0 < 0) {
                    continue;
                }
                float aW1 = ((x2 - aPX) * (y0 - aPY) - (y2 - aPY) * (x0 - aPX)) / aArea;
                if (aW1 < 0) {
                    continue;
                }
                float aW2 = 1.0f - aW0 - aW1;
                if (aW2 < 0) {
                    continue;
                }

                int aSrcX = (int) ((aW0 * u0 + aW1 * u1 + aW2 * u2) * aTextureWidth);
                int aSrcY = (int) ((aW0 * v0 + aW1 * v1 + aW2 * v2) * aTextureHeight);
                if (aSrcX < 0) {
                    aSrcX = 0;
                } else if (aSrcX >= aTextureWidth) {
                    aSrcX = aTextureWidth - 1;
                }
                if (aSrcY < 0) {
                    aSrcY = 0;
                } else if (aSrcY >= aTextureHeight) {
                    aSrcY = aTextureHeight - 1;
                }

                int src = aSrcBits[aSrcY * aTextureWidth + aSrcX];
                int dest = aDestBits[aDestPixels];

                // SWTri_GetTexel.inc:62-67: the fragment alpha is the texel alpha when the source
                // carries alpha (SWDrawShape derives talpha from mHasAlpha/mHasTrans,
                // SWTri.cpp:360) and a flat 0xFF when it does not.
                int a = (aSrc.mHasAlpha || aSrc.mHasTrans) ? (src >>> 24) : 0xFF;
                int sr = (src >>> 16) & 0xFF;
                int sg = (src >>> 8) & 0xFF;
                int sb = src & 0xFF;

                if (!white) {
                    // SWTri_TexelARGB.inc:36-43 (the GLOBAL_ARGB branch, which is what a non-white
                    // theColor selects: SWDrawShape sets globalargb = theColor != Color::White,
                    // SWTri.cpp:354): the global tint scales the texel channels and its alpha, all
                    // weighted by >>> 8.
                    a = (a * (aColor >>> 24)) >>> 8;
                    sr = (sr * ((aColor >>> 16) & 0xFF)) >>> 8;
                    sg = (sg * ((aColor >>> 8) & 0xFF)) >>> 8;
                    sb = (sb * (aColor & 0xFF)) >>> 8;
                }

                // SWTri_Pixel8888.inc:31-76: a fragment at/below 0x08 is culled, and once the
                // (modulated) alpha reaches 0xf0 the pixel is stored verbatim as
                // `*pix = tex | 0xFF000000`; only the sub-alpha remainder blends.
                if (a <= 0x08) {
                    continue;
                }

                if (additive) {
                    int r = addMax(((dest >>> 16) & 0xFF) + ((sr * a) >>> 8));
                    int g = addMax(((dest >>> 8) & 0xFF) + ((sg * a) >>> 8));
                    int b = addMax((dest & 0xFF) + ((sb * a) >>> 8));

                    aDestBits[aDestPixels] = (dest & 0xFF000000) | (r << 16) | (g << 8) | b;
                } else if (a >= 0xF0) {
                    aDestBits[aDestPixels] = 0xFF000000 | (sr << 16) | (sg << 8) | sb;
                } else {
                    int aDestAlpha = dest >>> 24;
                    int aNewDestAlpha = aDestAlpha + ((255 - aDestAlpha) * a) / 255;

                    a = 255 * a / aNewDestAlpha;
                    int oma = 256 - a;

                    // Same masked source-over arithmetic as MI_NormalBlt.inc.
                    int r = ((((dest & 0xFF0000) * oma) >>> 8) + (((sr << 16) * a) >>> 8) & 0xFF0000);
                    int g = ((((dest & 0x00FF00) * oma) >>> 8) + (((sg << 8) * a) >>> 8) & 0x00FF00);
                    int b = (((dest & 0x0000FF) * oma) >>> 8) + ((sb * a) >>> 8) & 0x0000FF;

                    aDestBits[aDestPixels] = (aNewDestAlpha << 24) | r | g | b;
                }
            }
        }
    }
}
