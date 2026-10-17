package cn.enaium.fabricpvz.portable.sexy.graphics;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GLImage}: every draw entry point is exercised against a recording
 * {@link RendererBackend} double that captures the exact call geometry (texture handle, source and
 * destination rectangles, angle, centre, flip, colour modulation, blend mode, fill/line arguments).
 */
class GLImageTest {

    /** Recording {@link RendererBackend}: appends one record per call, assigning texture handles. */
    static final class Recorder implements RendererBackend {
        record TexCall(long texture, int srcX, int srcY, int srcW, int srcH,
                       float dstX, float dstY, float dstW, float dstH,
                       float angle, float centerX, float centerY, int flip, int colorMod) {}
        record FillCall(float x, float y, float w, float h, int color) {}
        record LineCall(float x1, float y1, float x2, float y2, int color) {}
        record CreateCall(int width, int height, int[] pixels) {}
        record UpdateCall(long texture, int[] pixels, int width, int height) {}
        record FilterCall(long texture, boolean linear) {}
        record RenderTargetCall(int width, int height) {}
        record TriangleCall(long texture, float[] positionsUv, int[] colors, int vertexCount,
                            int[] indices, int indexCount, BlendMode mode) {}

        final List<TexCall> textures = new ArrayList<>();
        final List<FillCall> fills = new ArrayList<>();
        final List<LineCall> lines = new ArrayList<>();
        final List<CreateCall> creates = new ArrayList<>();
        final List<UpdateCall> updates = new ArrayList<>();
        final List<FilterCall> filters = new ArrayList<>();
        final List<RenderTargetCall> renderTargetCreates = new ArrayList<>();
        final List<TriangleCall> triangleCalls = new ArrayList<>();
        final List<BlendMode> blendModes = new ArrayList<>();
        final List<Long> destroyed = new ArrayList<>();
        final List<Long> renderTargets = new ArrayList<>();
        final List<Long> readbacks = new ArrayList<>();
        /** Ordered log of {@code "target:<handle>"} / {@code "draw:<handle>"} events. */
        final List<String> events = new ArrayList<>();

        /** When set, {@link #readTexturePixels} copies these words into the destination. */
        int[] readbackPixels;

        private long mNextHandle = 1000;

        @Override
        public long createTexture(int width, int height, int[] argbPixels) {
            creates.add(new CreateCall(width, height, argbPixels));
            return mNextHandle++;
        }

        @Override
        public long createRenderTargetTexture(int width, int height) {
            renderTargetCreates.add(new RenderTargetCall(width, height));
            return mNextHandle++;
        }

        @Override
        public void setTextureFilter(long texture, boolean linear) {
            filters.add(new FilterCall(texture, linear));
        }

        @Override
        public void drawTriangles(long texture, float[] positionsUv, int[] colors, int vertexCount,
                                  int[] indices, int indexCount, BlendMode mode) {
            triangleCalls.add(new TriangleCall(texture, positionsUv, colors, vertexCount, indices, indexCount, mode));
        }

        @Override
        public void updateTexture(long texture, int[] argbPixels, int width, int height) {
            updates.add(new UpdateCall(texture, argbPixels, width, height));
        }

        @Override
        public void destroyTexture(long texture) {
            destroyed.add(texture);
        }

        @Override
        public void drawTexture(long texture, int srcX, int srcY, int srcW, int srcH,
                                float dstX, float dstY, float dstW, float dstH,
                                float angleDegrees, float centerX, float centerY,
                                int flip, int colorModARGB) {
            textures.add(new TexCall(texture, srcX, srcY, srcW, srcH, dstX, dstY, dstW, dstH,
                    angleDegrees, centerX, centerY, flip, colorModARGB));
            events.add("draw:" + texture);
        }

        @Override
        public void fillRect(float x, float y, float width, float height, int colorARGB) {
            fills.add(new FillCall(x, y, width, height, colorARGB));
        }

        @Override
        public void drawLine(float x1, float y1, float x2, float y2, int colorARGB) {
            lines.add(new LineCall(x1, y1, x2, y2, colorARGB));
        }

        @Override
        public void setClipRect(int x, int y, int width, int height) {
        }

        @Override
        public void clearClipRect() {
        }

        @Override
        public void setBlendMode(BlendMode mode) {
            blendModes.add(mode);
        }

        @Override
        public void setRenderTarget(long texture) {
            renderTargets.add(texture);
            events.add("target:" + texture);
        }

        @Override
        public void clear(int colorARGB) {
        }

        @Override
        public void readTexturePixels(long texture, int width, int height, int[] dest) {
            readbacks.add(texture);
            if (readbackPixels != null) {
                System.arraycopy(readbackPixels, 0, dest, 0, Math.min(readbackPixels.length, width * height));
            }
        }

        @Override
        public void present() {
        }

        @Override
        public void setLogicalSize(int width, int height) {
        }

        @Override
        public int renderOutputWidth() {
            return 800;
        }

        @Override
        public int renderOutputHeight() {
            return 600;
        }
    }

    private static GLImage sourceImage(Recorder theRecorder, int theWidth, int theHeight, int[] theBits) {
        GLImage anImage = new GLImage(theRecorder);
        anImage.create(theWidth, theHeight);
        anImage.setBits(theBits, theWidth, theHeight, false);
        return anImage;
    }

    private static GLImage targetImage(Recorder theRecorder) {
        GLImage anImage = new GLImage(theRecorder);
        anImage.create(200, 200);
        return anImage;
    }


    /**
     * Asserts the pixels an upload carried. The framework hands the image's own array over (one canary
     * word longer than the pixel data) instead of copying it - a copy per upload of every animated image
     * was pure garbage in the 100 Hz loop - and the backend reads exactly {@code width * height} words.
     */
    private static void assertUploaded(int[] theExpected, int[] theUploaded) {
        assertTrue(theUploaded.length >= theExpected.length,
                "uploaded " + theUploaded.length + " words for " + theExpected.length + " pixels");
        assertArrayEquals(theExpected, java.util.Arrays.copyOf(theUploaded, theExpected.length));
    }

    @Test
    void bltUploadsOnceAndRefreshesOnBitsChange() {
        Recorder aRecorder = new Recorder();
        int[] aBits = {0xFF112233, 0x80445566, 0xFF778899, 0x00000000};
        GLImage aSource = sourceImage(aRecorder, 2, 2, aBits);
        GLImage aTarget = targetImage(aRecorder);

        Rect aSrcRect = new Rect(0, 0, 2, 2);
        aTarget.blt(aSource, 3, 4, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, true);

        assertEquals(1, aRecorder.creates.size());
        assertEquals(2, aRecorder.creates.get(0).width());
        assertEquals(2, aRecorder.creates.get(0).height());
        assertUploaded(aBits, aRecorder.creates.get(0).pixels());
        assertNotEquals(0L, aSource.mRenderData);

        assertEquals(1, aRecorder.textures.size());
        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(aSource.mRenderData, aCall.texture());
        assertEquals(0, aCall.srcX());
        assertEquals(0, aCall.srcY());
        assertEquals(2, aCall.srcW());
        assertEquals(2, aCall.srcH());
        assertEquals(3f, aCall.dstX());
        assertEquals(4f, aCall.dstY());
        assertEquals(2f, aCall.dstW());
        assertEquals(2f, aCall.dstH());
        assertEquals(0f, aCall.angle());
        assertEquals(RendererBackend.FLIP_NONE, aCall.flip());
        assertEquals(Color.White.toInt(), aCall.colorMod());
        assertEquals(List.of(RendererBackend.BlendMode.NORMAL), aRecorder.blendModes);
        assertTrue(aSource.mDrawn);

        // Second blit: the cached texture is reused, so no second createTexture.
        aTarget.blt(aSource, 5, 6, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(1, aRecorder.creates.size());
        assertEquals(2, aRecorder.textures.size());

        // Mutating the source bits invalidates the cached texture -> updateTexture.
        int[] aNewBits = {0xFF000000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
        aSource.setBits(aNewBits, 2, 2, false);
        aTarget.blt(aSource, 7, 8, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(1, aRecorder.creates.size());
        assertEquals(1, aRecorder.updates.size());
        assertEquals(aSource.mRenderData, aRecorder.updates.get(0).texture());
        assertUploaded(aNewBits, aRecorder.updates.get(0).pixels());
    }

    @Test
    void bltMapsAdditiveDrawMode() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[4]);
        GLImage aTarget = targetImage(aRecorder);

        aTarget.blt(aSource, 0, 0, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_ADDITIVE, true);
        assertEquals(RendererBackend.BlendMode.ADDITIVE, aRecorder.blendModes.get(0));
    }

    @Test
    void bltRotatedNegatesAngleAndPassesCenter() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 4, new int[16]);
        GLImage aTarget = targetImage(aRecorder);

        Rect aSrcRect = new Rect(0, 0, 4, 4);
        Rect aClipRect = new Rect(0, 0, 200, 200);
        aTarget.bltRotated(aSource, 10f, 20f, aSrcRect, aClipRect, Color.White,
                Graphics.DRAWMODE_NORMAL, Math.PI / 2, 2f, 2f);

        assertEquals(1, aRecorder.textures.size());
        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(-90f, aCall.angle(), 1e-3f);
        assertEquals(2f, aCall.centerX());
        assertEquals(2f, aCall.centerY());
        assertEquals(10f, aCall.dstX());
        assertEquals(20f, aCall.dstY());
        assertEquals(4f, aCall.dstW());
        assertEquals(4f, aCall.dstH());
        assertEquals(RendererBackend.FLIP_NONE, aCall.flip());
    }

    @Test
    void bltRotatedFullyClippedDrawsNothing() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 4, new int[16]);
        GLImage aTarget = targetImage(aRecorder);

        aTarget.bltRotated(aSource, 10f, 20f, new Rect(0, 0, 4, 4), new Rect(1000, 1000, 10, 10),
                Color.White, Graphics.DRAWMODE_NORMAL, Math.PI / 2, 2f, 2f);
        assertTrue(aRecorder.textures.isEmpty());
    }

    @Test
    void stretchBltClipsSourceAndDestination() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 40, 40, new int[1600]);
        GLImage aTarget = targetImage(aRecorder);
        Rect aSrcRect = new Rect(0, 0, 40, 40);

        // Clip fully contains the destination: unclipped src/dst.
        aTarget.stretchBlt(aSource, new Rect(100, 100, 80, 80), aSrcRect, new Rect(0, 0, 200, 200),
                Color.White, Graphics.DRAWMODE_NORMAL, false);
        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(0, aCall.srcX());
        assertEquals(0, aCall.srcY());
        assertEquals(40, aCall.srcW());
        assertEquals(40, aCall.srcH());
        assertEquals(100f, aCall.dstX());
        assertEquals(100f, aCall.dstY());
        assertEquals(80f, aCall.dstW());
        assertEquals(80f, aCall.dstH());

        // Clip cuts the destination in half: source shrinks by the same proportion.
        aRecorder.textures.clear();
        aTarget.stretchBlt(aSource, new Rect(100, 100, 80, 80), aSrcRect, new Rect(100, 100, 40, 80),
                Color.White, Graphics.DRAWMODE_NORMAL, false);
        aCall = aRecorder.textures.get(0);
        assertEquals(0, aCall.srcX());
        assertEquals(0, aCall.srcY());
        assertEquals(20, aCall.srcW());
        assertEquals(40, aCall.srcH());
        assertEquals(100f, aCall.dstX());
        assertEquals(100f, aCall.dstY());
        assertEquals(40f, aCall.dstW());
        assertEquals(80f, aCall.dstH());
    }

    @Test
    void bltMirrorFlipsHorizontallyInSameDestination() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 40, 40, new int[1600]);
        GLImage aTarget = targetImage(aRecorder);
        Rect aSrcRect = new Rect(5, 6, 20, 10);

        aTarget.bltMirror(aSource, 10, 20, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, true);

        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(RendererBackend.FLIP_HORIZONTAL, aCall.flip());
        assertEquals(5, aCall.srcX());
        assertEquals(6, aCall.srcY());
        assertEquals(20, aCall.srcW());
        assertEquals(10, aCall.srcH());
        assertEquals(10f, aCall.dstX());
        assertEquals(20f, aCall.dstY());
        assertEquals(20f, aCall.dstW());
        assertEquals(10f, aCall.dstH());
    }

    @Test
    void stretchBltMirrorClipsAndMirrorsSource() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 40, 40, new int[1600]);
        GLImage aTarget = targetImage(aRecorder);

        // Clip removes the right half of the destination: the source keeps its left half mirrored.
        aTarget.stretchBltMirror(aSource, new Rect(100, 100, 80, 80), new Rect(0, 0, 40, 40),
                new Rect(100, 100, 40, 80), Color.White, Graphics.DRAWMODE_NORMAL, false);

        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(RendererBackend.FLIP_HORIZONTAL, aCall.flip());
        assertEquals(20, aCall.srcX());
        assertEquals(0, aCall.srcY());
        assertEquals(20, aCall.srcW());
        assertEquals(40, aCall.srcH());
        assertEquals(100f, aCall.dstX());
        assertEquals(100f, aCall.dstY());
        assertEquals(40f, aCall.dstW());
        assertEquals(80f, aCall.dstH());
    }

    @Test
    void bltFClipsEmptyDestination() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 4, new int[16]);
        GLImage aTarget = targetImage(aRecorder);
        Rect aSrcRect = new Rect(0, 0, 4, 4);

        aTarget.bltF(aSource, 10f, 10f, aSrcRect, new Rect(0, 0, 200, 200), Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(1, aRecorder.textures.size());
        assertEquals(10f, aRecorder.textures.get(0).dstX());
        assertEquals(10f, aRecorder.textures.get(0).dstY());
        assertEquals(4f, aRecorder.textures.get(0).dstW());
        assertEquals(4f, aRecorder.textures.get(0).dstH());

        aTarget.bltF(aSource, 500f, 500f, aSrcRect, new Rect(0, 0, 200, 200), Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(1, aRecorder.textures.size());
    }

    @Test
    void bltMatrixDecomposesRotationScaleAndMirror() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 2, new int[8]);
        GLImage aTarget = targetImage(aRecorder);
        Rect aSrcRect = new Rect(0, 0, 4, 2);

        // 90-degree rotation (texture +x -> screen up, texture +y -> screen right) with sx=2, sy=3.
        SexyMatrix3 aMatrix = new SexyMatrix3();
        aMatrix.m00 = 0f;
        aMatrix.m10 = -2f;
        aMatrix.m01 = 3f;
        aMatrix.m11 = 0f;
        aMatrix.m02 = 100f;
        aMatrix.m12 = 50f;

        aTarget.bltMatrix(aSource, 0f, 0f, aMatrix, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, aSrcRect, false);

        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(-90f, aCall.angle(), 1e-3f);
        assertEquals(RendererBackend.FLIP_NONE, aCall.flip());
        assertEquals(8f, aCall.dstW(), 1e-3f);   // 4 * sx(2)
        assertEquals(6f, aCall.dstH(), 1e-3f);   // 2 * sy(3)
        assertEquals(96f, aCall.dstX(), 1e-3f);  // 100 - 8/2
        assertEquals(47f, aCall.dstY(), 1e-3f);  // 50 - 6/2
        assertEquals(4f, aCall.centerX(), 1e-3f);
        assertEquals(3f, aCall.centerY(), 1e-3f);

        // Negative scaleX => mirrored: flip instead of a 180-degree rotation.
        aRecorder.textures.clear();
        aMatrix.m10 = 2f;
        aTarget.bltMatrix(aSource, 0f, 0f, aMatrix, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, aSrcRect, false);
        aCall = aRecorder.textures.get(0);
        assertEquals(RendererBackend.FLIP_HORIZONTAL, aCall.flip());
        assertEquals(-90f, aCall.angle(), 1e-3f);
    }

    /**
     * {@code GLInterface::DrawTrianglesTex} sets the sampler filter from the {@code blend} argument
     * (GLInterface.cpp:1604) before the batch is drawn, and {@code Graphics::DrawTrianglesTex} passes
     * {@code mLinearBlend} (Graphics.cpp:947). Batched textured draws (particles, trails, pool
     * caustics) are scaled well past 1:1, so without this the texture keeps the backend's
     * {@code createTexture} default (nearest) and the scaled batch shows hard pixel blocks; a
     * 54x54 {@code particles/AWARDRAYS_STAR} covers most of the screen at the level-award pickup.
     */
    @Test
    void bltTrianglesTexAppliesTheRequestedFilter() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[4]);
        GLImage aTarget = targetImage(aRecorder);
        TriVertex[][] aTriangle = {{
            vertex(10f, 10f, 0f, 0f),
            vertex(10f, 10f, 0f, 1f),
            vertex(10f, 10f, 1f, 0f),
        }};

        aTarget.bltTrianglesTex(aSource, aTriangle, 1, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, 0f, 0f, true);
        long aTexture = aSource.mRenderData;
        assertEquals(List.of(new Recorder.FilterCall(aTexture, true)), aRecorder.filters);
        assertEquals(aTexture, aRecorder.triangleCalls.get(0).texture());

        // The same texture asked for nearest must be switched back, not left filtered.
        aTarget.bltTrianglesTex(aSource, aTriangle, 1, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, 0f, 0f, false);
        assertEquals(new Recorder.FilterCall(aTexture, false), aRecorder.filters.get(1));
    }

    private static TriVertex vertex(float theX, float theY, float theU, float theV) {
        TriVertex aVertex = new TriVertex();
        aVertex.x = theX;
        aVertex.y = theY;
        aVertex.u = theU;
        aVertex.v = theV;
        return aVertex;
    }

    @Test
    void fillRectAndDrawLineForwardWithBlendMode() {
        Recorder aRecorder = new Recorder();
        GLImage aTarget = targetImage(aRecorder);

        aTarget.fillRect(new Rect(1, 2, 3, 4), Color.White, Graphics.DRAWMODE_ADDITIVE);
        Recorder.FillCall aFill = aRecorder.fills.get(0);
        assertEquals(1f, aFill.x());
        assertEquals(2f, aFill.y());
        assertEquals(3f, aFill.w());
        assertEquals(4f, aFill.h());
        assertEquals(Color.White.toInt(), aFill.color());
        assertEquals(RendererBackend.BlendMode.ADDITIVE, aRecorder.blendModes.get(0));

        aTarget.drawLine(1, 2, 3, 4, Color.Black, Graphics.DRAWMODE_NORMAL);
        Recorder.LineCall aLine = aRecorder.lines.get(0);
        assertEquals(1f, aLine.x1());
        assertEquals(2f, aLine.y1());
        assertEquals(3f, aLine.x2());
        assertEquals(4f, aLine.y2());
        assertEquals(Color.Black.toInt(), aLine.color());
        assertEquals(RendererBackend.BlendMode.NORMAL, aRecorder.blendModes.get(1));

        // DrawLineAA forwards to DrawLine exactly.
        aTarget.drawLineAA(5, 6, 7, 8, Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(2, aRecorder.lines.size());
        assertEquals(5f, aRecorder.lines.get(1).x1());
    }

    @Test
    void fillScanLinesAndDrawRectUseSoftwareSpanFill() {
        Recorder aRecorder = new Recorder();
        GLImage aTarget = targetImage(aRecorder);

        aTarget.fillScanLines(new Span[]{new Span(0, 0, 4), new Span(1, 2, 3)}, 2, Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(2, aRecorder.fills.size());
        assertEquals(2f, aRecorder.fills.get(1).y());

        aRecorder.fills.clear();
        aTarget.drawRect(new Rect(10, 10, 20, 20), Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(4, aRecorder.fills.size());
    }

    @Test
    void screenTargetIsSizeCarrierAndNeverUploaded() {
        Recorder aRecorder = new Recorder();
        GLImage aScreen = GLImage.screen(aRecorder, 800, 600);

        assertTrue(aScreen.is3D());
        assertEquals(800, aScreen.getWidth());
        assertEquals(600, aScreen.getHeight());
        assertEquals(0L, aScreen.mRenderData);
        assertTrue(aScreen.mRenderTarget);
        assertEquals(0L, aScreen.ensureTexture(aScreen));
        assertTrue(aRecorder.creates.isEmpty());

        // Screen target: purgeBits materializes the bits and defers the purge (no texture to hold them).
        aScreen.purgeBits();
        assertTrue(aScreen.mPurgeBits);
        assertNotNull(aScreen.mBits);
        assertTrue(aRecorder.creates.isEmpty());

        // screen(w, h) with no app/renderer still yields a valid size carrier.
        GLImage aPlainScreen = GLImage.screen(320, 240);
        assertTrue(aPlainScreen.is3D());
        assertEquals(320, aPlainScreen.getWidth());
        assertEquals(240, aPlainScreen.getHeight());
        assertEquals(0L, aPlainScreen.mRenderData);
    }

    @Test
    void fillScanLinesWithCoverageBlitsTempImageOnly() {
        Recorder aRecorder = new Recorder();
        GLImage aTarget = targetImage(aRecorder);
        int aBefore = aTarget.mBitsChangedCount;

        Span[] aSpans = {new Span(0, 0, 4), new Span(1, 2, 3)};
        byte[] aCoverage = new byte[64];

        aTarget.fillScanLinesWithCoverage(aSpans, 2, Color.White, Graphics.DRAWMODE_NORMAL, aCoverage, 0, 0, 8, 8);

        // The temp image is uploaded and blitted into this image: exactly one texture draw.
        assertEquals(1, aRecorder.textures.size());
        Recorder.TexCall aCall = aRecorder.textures.get(0);
        assertEquals(0f, aCall.dstX());
        assertEquals(0f, aCall.dstY());
        assertEquals(5f, aCall.dstW());  // r - l + 1 = 4 - 0 + 1
        assertEquals(3f, aCall.dstH());  // b - t + 1 = 2 - 0 + 1
        // As upstream, the destination GLImage's CPU bits/counter are untouched (only the temp's are).
        assertEquals(aBefore, aTarget.mBitsChangedCount);
    }

    @Test
    void purgeBitsKeepsTheBitsOfAStaticTextureWithNoFile() {
        Recorder aRecorder = new Recorder();
        int[] aBits = {0xFF112233, 0x80445566, 0xFF778899, 0x00000000};
        GLImage anImage = sourceImage(aRecorder, 2, 2, aBits);

        // A static texture cannot be read back and the image was made from bits (no file to re-decode
        // from), so dropping the CPU copy would lose the pixels for good: they are kept.
        anImage.purgeBits();

        assertTrue(anImage.mPurgeBits);
        assertNotEquals(0L, anImage.mRenderData);
        assertEquals(1, aRecorder.creates.size());
        assertUploaded(aBits, aRecorder.creates.get(0).pixels());
        assertNotNull(anImage.mBits);

        // getBits hands the kept pixels straight back, never asking the backend for a read-back.
        assertSame(anImage.mBits, anImage.getBits());
        assertArrayEquals(aBits, new int[]{anImage.mBits[0], anImage.mBits[1], anImage.mBits[2], anImage.mBits[3]});
        assertTrue(aRecorder.readbacks.isEmpty());
    }

    @Test
    void purgeBitsDropsTheBitsOfADrawTargetAndDoesNotReupload() {
        Recorder aRecorder = new Recorder();
        GLImage anImage = sourceImage(aRecorder, 2, 2, new int[]{0xFF112233, 0x80445566, 0xFF778899, 0x00000000});
        anImage.mRenderTarget = true; // a draw target's own texture holds the pixels
        GLImage aTarget = targetImage(aRecorder);

        // Upstream deferral: the texture must exist before the CPU bits are freed.
        anImage.purgeBits();

        assertTrue(anImage.mPurgeBits);
        assertNotEquals(0L, anImage.mRenderData);
        assertNull(anImage.mBits);
        assertEquals(1, aRecorder.renderTargetCreates.size());
        assertTrue(aRecorder.creates.isEmpty());

        // The texture already owns the pixels, so a later blit must not re-create or re-upload them.
        int aChanged = anImage.mBitsChangedCount;
        aTarget.blt(anImage, 0, 0, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(1, aRecorder.renderTargetCreates.size());
        assertTrue(aRecorder.updates.isEmpty());
        assertEquals(aChanged, anImage.mBitsChangedCount);
    }

    @Test
    void getBitsReadsBackPurgedPixelsFromTexture() {
        Recorder aRecorder = new Recorder();
        int[] aBits = {0xFF112233, 0x80445566, 0xFF778899, 0x00000000};
        GLImage anImage = sourceImage(aRecorder, 2, 2, aBits);
        anImage.mRenderTarget = true; // only a draw target's texture can be read back
        anImage.purgeBits(); // creates the draw-target texture, then drops the CPU bits
        assertNull(anImage.mBits);
        assertEquals(1, aRecorder.renderTargetCreates.size());

        int[] aRestored = {0xDEADBEEF, 0xCAFEBABE, 0x01234567, 0x89ABCDEF};
        aRecorder.readbackPixels = aRestored;
        int aChanged = anImage.mBitsChangedCount;

        int[] aResult = anImage.getBits();

        assertEquals(5, aResult.length);
        assertArrayEquals(aRestored, new int[]{aResult[0], aResult[1], aResult[2], aResult[3]});
        assertEquals(MemoryImage.MEMORYCHECK_ID, aResult[4]);
        assertEquals(1, aRecorder.readbacks.size());
        assertEquals(anImage.mRenderData, aRecorder.readbacks.get(0).longValue());

        // Readback must not re-upload nor change the invalidation counter.
        assertEquals(1, aRecorder.renderTargetCreates.size());
        assertTrue(aRecorder.updates.isEmpty());
        assertEquals(aChanged, anImage.mBitsChangedCount);
    }

    @Test
    void getBitsDoesNotReadBackAStaticTexture() {
        Recorder aRecorder = new Recorder();
        int[] aBits = {0xFF112233, 0x80445566, 0xFF778899, 0x00000000};
        GLImage anImage = sourceImage(aRecorder, 2, 2, aBits);
        anImage.ensureTexture(anImage); // uploads the static, non-drawable texture
        assertNotEquals(0L, anImage.mRenderData);
        assertFalse(anImage.mRenderTarget);

        // The state the read-back branch used to answer for: CPU bits gone, texture present and current.
        anImage.mBits = null;
        // These wrong pixels would be handed back if the read-back branch were taken.
        aRecorder.readbackPixels = new int[]{0xDEADBEEF, 0xCAFEBABE, 0x01234567, 0x89ABCDEF};

        int[] aResult = anImage.getBits();

        assertTrue(aRecorder.readbacks.isEmpty(), "a static texture must never be read back");
        assertEquals(5, aResult.length);
        // No file to re-decode from: a fresh, zeroed buffer - never another target's pixels.
        assertEquals(0, aResult[0]);
        assertEquals(0, aResult[3]);
        assertEquals(MemoryImage.MEMORYCHECK_ID, aResult[4]);
    }

    @Test
    void delete3DBuffersReleasesTextureAndResetsState() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[4]);
        GLImage aTarget = targetImage(aRecorder);
        aTarget.blt(aSource, 0, 0, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);

        long aHandle = aSource.mRenderData;
        assertNotEquals(0L, aHandle);

        aSource.delete3DBuffers();
        assertEquals(0L, aSource.mRenderData);
        assertEquals(-1, aSource.mRenderDataBitsChangedCount);
        assertEquals(List.of(aHandle), aRecorder.destroyed);
    }

    @Test
    void createReleasesPreviousTexture() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[4]);
        GLImage aTarget = targetImage(aRecorder);
        aTarget.blt(aSource, 0, 0, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);
        long aHandle = aSource.mRenderData;

        aSource.create(4, 4);
        assertEquals(0L, aSource.mRenderData);
        assertEquals(-1, aSource.mRenderDataBitsChangedCount);
        assertTrue(aRecorder.destroyed.contains(aHandle));
    }

    @Test
    void bltTrianglesTexDrawsThroughBackend() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 4, new int[16]);
        GLImage aTarget = targetImage(aRecorder);

        TriVertex[][] aTriangles = {{
                new TriVertex(1f, 2f, 0.1f, 0.2f),              // color 0 -> falls back to theColor
                new TriVertex(3f, 4f, 0.3f, 0.4f, 0xFF00FF00),
                new TriVertex(5f, 6f, 0.5f, 0.6f, 0x80FF0000),
        }};
        int aColor = new Color(9, 8, 7).toInt();

        aTarget.bltTrianglesTex(aSource, aTriangles, 1, new Rect(0, 0, 200, 200),
                new Color(9, 8, 7), Graphics.DRAWMODE_ADDITIVE, 10f, 20f, true);

        assertTrue(aSource.mDrawn);
        assertEquals(1, aRecorder.triangleCalls.size());
        Recorder.TriangleCall aCall = aRecorder.triangleCalls.get(0);
        assertEquals(aSource.mRenderData, aCall.texture());
        assertEquals(3, aCall.vertexCount());
        assertEquals(3, aCall.indexCount());
        assertArrayEquals(new int[] {0, 1, 2}, aCall.indices());
        assertEquals(RendererBackend.BlendMode.ADDITIVE, aCall.mode());
        assertEquals(RendererBackend.BlendMode.ADDITIVE, aRecorder.blendModes.get(aRecorder.blendModes.size() - 1));

        float[] aPositionsUv = aCall.positionsUv();
        assertEquals(12, aPositionsUv.length);
        // vertex 0: (1+10, 2+20, 0.1, 0.2)
        assertEquals(11f, aPositionsUv[0], 1e-4f);
        assertEquals(22f, aPositionsUv[1], 1e-4f);
        assertEquals(0.1f, aPositionsUv[2], 1e-4f);
        assertEquals(0.2f, aPositionsUv[3], 1e-4f);
        // vertex 1: (3+10, 4+20, 0.3, 0.4)
        assertEquals(13f, aPositionsUv[4], 1e-4f);
        assertEquals(24f, aPositionsUv[5], 1e-4f);
        assertEquals(0.3f, aPositionsUv[6], 1e-4f);
        assertEquals(0.4f, aPositionsUv[7], 1e-4f);

        assertArrayEquals(new int[] {aColor, 0xFF00FF00, 0x80FF0000}, aCall.colors());
    }

    /**
     * The seed packet's cooldown wipe, reduced to its blit: the dark overlay draws the packet cel at
     * {@code (433, 8)} with a clip that covers only the first three rows of it
     * ({@code SeedPacket.drawSeedPacket}, SeedPacket.cpp:549-565). Upstream sends that through
     * {@code GLInterface::BltClipF} and clips the quad (GLImage.cpp:150-165); this port used to draw the
     * whole 50x70 destination, which left the packet uniformly dark for the whole cooldown.
     */
    @Test
    void bltFClipsPartiallyCoveredDestination() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 100, 70, new int[7000]);
        GLImage aTarget = targetImage(aRecorder);

        aTarget.bltF(aSource, 433f, 8f, new Rect(0, 0, 50, 70), new Rect(433, 8, 50, 3),
                new Color(64, 64, 64), Graphics.DRAWMODE_NORMAL);

        assertTrue(aRecorder.textures.isEmpty(), "a partially clipped bltF must not draw the whole quad");
        assertEquals(1, aRecorder.triangleCalls.size());
        Recorder.TriangleCall aCall = aRecorder.triangleCalls.get(0);
        assertEquals(aSource.mRenderData, aCall.texture());
        assertEquals(4, aCall.vertexCount());
        assertEquals(6, aCall.indexCount());
        assertEquals(RendererBackend.BlendMode.NORMAL, aCall.mode());

        // The clipped quad is the destination cut at y = 11, with v interpolated by the same cut.
        assertEquals(List.of(
                "433.0000,11.0000,0.0000,0.0429",
                "433.0000,8.0000,0.0000,0.0000",
                "483.0000,11.0000,0.5000,0.0429",
                "483.0000,8.0000,0.5000,0.0000"), quadVertices(aCall));
    }

    /**
     * The same wipe over the packet's plant art, which is scaled and so goes through {@code bltMatrix}:
     * the 120x120 cel drawn at 0.5 about {@code (458, 37)} covers {@code (428, 7)-(488, 67)} and the
     * wipe's clip only keeps {@code x >= 433, y >= 8} of it, with the source coordinates shrunk with the
     * destination exactly as upstream's polygon clip does.
     */
    @Test
    void bltMatrixClipsQuadThatLeavesTheClip() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 120, 120, new int[14400]);
        GLImage aTarget = targetImage(aRecorder);

        SexyMatrix3 aMatrix = new SexyMatrix3();
        aMatrix.m00 = 0.5f;
        aMatrix.m11 = 0.5f;
        aMatrix.m02 = 458f;
        aMatrix.m12 = 37f;

        aTarget.bltMatrix(aSource, 0f, 0f, aMatrix, new Rect(433, 8, 50, 70), Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 120, 120), true);

        assertTrue(aRecorder.textures.isEmpty(), "a clipped bltMatrix must not draw the whole quad");
        assertEquals(1, aRecorder.triangleCalls.size());
        Recorder.TriangleCall aCall = aRecorder.triangleCalls.get(0);
        assertEquals(aSource.mRenderData, aCall.texture());
        assertEquals(4, aCall.vertexCount());
        assertEquals(6, aCall.indexCount());

        // (428, 7)-(488, 67) cut at x = 433 (u = 5/60) and y = 8 (v = 1/60).
        assertEquals(List.of(
                "433.0000,67.0000,0.0833,1.0000",
                "433.0000,8.0000,0.0833,0.0167",
                "483.0000,67.0000,0.9167,1.0000",
                "483.0000,8.0000,0.9167,0.0167"), quadVertices(aCall));
    }

    /** The fan's vertices as sorted {@code "x,y,u,v"} strings: the clipper's vertex order is its own. */
    private static List<String> quadVertices(Recorder.TriangleCall theCall) {
        List<String> aVertices = new ArrayList<>();
        float[] aPositionsUv = theCall.positionsUv();
        for (int i = 0; i < theCall.vertexCount(); i++) {
            aVertices.add(String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f,%.4f",
                    aPositionsUv[i * 4], aPositionsUv[i * 4 + 1],
                    aPositionsUv[i * 4 + 2], aPositionsUv[i * 4 + 3]));
        }
        aVertices.sort(null);
        return aVertices;
    }

    /** A quad entirely outside the clip draws nothing at all (upstream clips it away). */
    @Test
    void bltMatrixDrawsNothingWhenTheQuadIsOutsideTheClip() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 2, new int[8]);
        GLImage aTarget = targetImage(aRecorder);

        SexyMatrix3 aMatrix = new SexyMatrix3();
        aMatrix.m00 = 2f;
        aMatrix.m11 = 3f;
        aMatrix.m02 = 500f;
        aMatrix.m12 = 500f;
        aTarget.bltMatrix(aSource, 0f, 0f, aMatrix, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 4, 2), false);

        assertTrue(aRecorder.textures.isEmpty());
        assertTrue(aRecorder.triangleCalls.isEmpty());
    }

    /** The unclipped fast path is untouched: the whole quad still goes through {@code drawTexture}. */
    @Test
    void bltMatrixStillDrawsTheWholeQuadWhenTheClipContainsIt() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 2, new int[8]);
        GLImage aTarget = targetImage(aRecorder);

        SexyMatrix3 aMatrix = new SexyMatrix3();
        aMatrix.m00 = 2f;
        aMatrix.m11 = 3f;
        aMatrix.m02 = 100f;
        aMatrix.m12 = 50f;
        aTarget.bltMatrix(aSource, 0f, 0f, aMatrix, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 4, 2), false);

        assertEquals(1, aRecorder.textures.size());
        assertTrue(aRecorder.triangleCalls.isEmpty());
    }

    @Test
    void polyFill3DFallsBackToSoftware() {
        Recorder aRecorder = new Recorder();
        GLImage aTarget = targetImage(aRecorder);

        assertFalse(aTarget.polyFill3D(new Point[0], 0, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, 0, 0));
        assertTrue(aRecorder.triangleCalls.isEmpty());
    }

    @Test
    void nullBackendDrawsAreNoOps() {
        GLImage anImage = new GLImage();
        anImage.create(4, 4);
        assertTrue(anImage.is3D());
        assertEquals(0L, anImage.ensureTexture(anImage));

        // No backend to hold the pixels: purgeBits materializes the bits and defers the purge.
        anImage.purgeBits();
        assertTrue(anImage.mPurgeBits);
        assertNotNull(anImage.mBits);
    }

    @Test
    void textureFilterFollowsLinearFlagAndIsCached() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[4]);
        GLImage aTarget = targetImage(aRecorder);
        Rect aSrcRect = new Rect(0, 0, 2, 2);

        aTarget.blt(aSource, 0, 0, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, false);
        assertEquals(1, aRecorder.filters.size());
        assertEquals(aSource.mRenderData, aRecorder.filters.get(0).texture());
        assertFalse(aRecorder.filters.get(0).linear());

        // Same (texture, linear) pair: the redundant call is elided.
        aTarget.blt(aSource, 1, 1, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, false);
        assertEquals(1, aRecorder.filters.size());

        // Different flag: a new call is issued.
        aTarget.blt(aSource, 2, 2, aSrcRect, Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(2, aRecorder.filters.size());
        assertTrue(aRecorder.filters.get(1).linear());
    }

    @Test
    void stretchBltLinearFilterTracksFastStretch() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 4, new int[16]);
        GLImage aTarget = targetImage(aRecorder);
        Rect aSrcRect = new Rect(0, 0, 4, 4);

        aTarget.stretchBlt(aSource, new Rect(0, 0, 40, 40), aSrcRect, new Rect(0, 0, 200, 200),
                Color.White, Graphics.DRAWMODE_NORMAL, false);
        assertEquals(1, aRecorder.filters.size());
        assertTrue(aRecorder.filters.get(0).linear()); // !fastStretch == true

        aTarget.stretchBlt(aSource, new Rect(0, 0, 40, 40), aSrcRect, new Rect(0, 0, 200, 200),
                Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(2, aRecorder.filters.size());
        assertFalse(aRecorder.filters.get(1).linear()); // !fastStretch == false
    }

    @Test
    void bltRotatedAndMatrixRequestLinearFilter() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 4, 4, new int[16]);
        GLImage aTarget = targetImage(aRecorder);

        aTarget.bltRotated(aSource, 10, 10, new Rect(0, 0, 4, 4), new Rect(0, 0, 200, 200),
                Color.White, Graphics.DRAWMODE_NORMAL, 0.0, 2, 2);
        assertEquals(1, aRecorder.filters.size());
        assertTrue(aRecorder.filters.get(0).linear());

        // bltMatrix uses the same texture, so its (identical) linear request is cached away.
        SexyMatrix3 aMatrix = new SexyMatrix3();
        aMatrix.m00 = 1f;
        aMatrix.m11 = 1f;
        aTarget.bltMatrix(aSource, 0, 0, aMatrix, new Rect(0, 0, 200, 200), Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 4, 4), false);
        assertEquals(1, aRecorder.filters.size());

        // bltMirror passes its own linear flag through (false rolls the mode back).
        aTarget.bltMirror(aSource, 0, 0, new Rect(0, 0, 4, 4), Color.White, Graphics.DRAWMODE_NORMAL, false);
        assertEquals(2, aRecorder.filters.size());
        assertFalse(aRecorder.filters.get(1).linear());
    }

    @Test
    void renderTargetImageUsesCreateRenderTargetTexture() {
        Recorder aRecorder = new Recorder();
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[] {0xFF112233, 0x80445566, 0xFF778899, 0x00000000});
        aSource.mRenderTarget = true;
        GLImage aTarget = targetImage(aRecorder);

        aTarget.blt(aSource, 0, 0, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(1, aRecorder.renderTargetCreates.size());
        assertEquals(2, aRecorder.renderTargetCreates.get(0).width());
        assertEquals(2, aRecorder.renderTargetCreates.get(0).height());
        assertTrue(aRecorder.creates.isEmpty());
        assertNotEquals(0L, aSource.mRenderData);

        // updateTexture stays valid for the render-target texture.
        aSource.setBits(new int[] {0xFF000000, 0, 0, 0}, 2, 2, false);
        aTarget.blt(aSource, 1, 1, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(1, aRecorder.updates.size());
        assertEquals(aSource.mRenderData, aRecorder.updates.get(0).texture());
    }

    @Test
    void offscreenTargetBindsBeforeFirstDrawAndOnlyOnce() {
        Recorder aRecorder = new Recorder();
        GLImage aTarget = new GLImage(aRecorder);
        aTarget.create(8, 8);
        aTarget.mRenderTarget = true;
        GLImage aSource = sourceImage(aRecorder, 2, 2, new int[4]);

        aTarget.blt(aSource, 0, 0, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);

        assertEquals(1, aRecorder.renderTargets.size());
        assertEquals(aTarget.mRenderData, aRecorder.renderTargets.get(0).longValue());
        // Binding happens before the texture draw.
        assertEquals("target:" + aTarget.mRenderData, aRecorder.events.get(0));
        assertTrue(aRecorder.events.get(1).startsWith("draw:"));

        // The same target is not re-bound on the next draw.
        aTarget.blt(aSource, 1, 1, new Rect(0, 0, 2, 2), Color.White, Graphics.DRAWMODE_NORMAL, true);
        assertEquals(1, aRecorder.renderTargets.size());
    }

    @Test
    void screenTargetBindsHandleZeroOnce() {
        Recorder aRecorder = new Recorder();
        GLImage aScreen = GLImage.screen(aRecorder, 8, 8);

        aScreen.fillRect(new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(List.of(0L), aRecorder.renderTargets);

        aScreen.fillRect(new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL);
        assertEquals(List.of(0L), aRecorder.renderTargets);
    }

    @Test
    void bindSequenceAcrossScreenAndOffscreen() {
        Recorder aRecorder = new Recorder();
        GLImage aScreen = GLImage.screen(aRecorder, 8, 8);
        GLImage anOffscreen = new GLImage(aRecorder);
        anOffscreen.create(8, 8);
        anOffscreen.mRenderTarget = true;

        aScreen.fillRect(new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL);
        anOffscreen.fillRect(new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL);
        aScreen.fillRect(new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL);

        assertEquals(List.of(0L, anOffscreen.mRenderData, 0L), aRecorder.renderTargets);
    }

    @Test
    void clearRectBindsTargetAndClearsBits() {
        Recorder aRecorder = new Recorder();
        GLImage anOffscreen = new GLImage(aRecorder);
        anOffscreen.create(8, 8);
        anOffscreen.mRenderTarget = true;
        int aBefore = anOffscreen.mBitsChangedCount;

        anOffscreen.clearRect(new Rect(0, 0, 4, 4));

        assertEquals(1, aRecorder.renderTargets.size());
        assertEquals(anOffscreen.mRenderData, aRecorder.renderTargets.get(0).longValue());
        assertTrue(anOffscreen.mBitsChangedCount > aBefore);
    }

    @Test
    void plainImageHandedToGraphicsDoesNotBindOrGuess() {
        Recorder aRecorder = new Recorder();
        GLImage aPlain = targetImage(aRecorder); // not a screen, not mRenderTarget

        aPlain.fillRect(new Rect(0, 0, 1, 1), Color.White, Graphics.DRAWMODE_NORMAL);

        assertTrue(aRecorder.renderTargets.isEmpty());
    }
}
