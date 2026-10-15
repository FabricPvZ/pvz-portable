package cn.enaium.fabricpvz.portable.lawn.system;

import java.util.Arrays;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.TriVertex;

/**
 * Ported from src/Lawn/System/PoolEffect.h:30-49 / src/Lawn/System/PoolEffect.cpp:31-264.
 *
 * <p>Faithful port of {@code PoolEffect}: the animated pool water (the per-cell wave offset grid
 * drawn as 150 triangles per layer, {effect documentation by @windowslover1234}) plus the caustic
 * light overlay. The caustic texture is a 128x64 {@link MemoryImage} that is recomputed every frame
 * from a 256x256 grayscale pattern sampled out of {@code IMAGE_POOL_CAUSTIC_EFFECT} with a
 * wrap-around bilinear filter ({@link #bilinearLookupFixedPoint}).
 *
 * <p>PORT-NOTE: {@code PvzpHesitationBracket} (the debug scope guard opened at the top of
 * {@code PoolEffectInitialize}) has no Java counterpart, see {@code PvzpDebug.java:17}.
 *
 * <p>PORT-NOTE: the {@code GLInterface*} fetch from {@code ((GLImage*)g->mDestImage)->mGLInterface}
 * at the end of {@code PoolEffectDraw} is not ported: the value was never used upstream, and this
 * port's renderer backend owns texture upload ({@code bltTrianglesTex}).
 *
 * <p>PORT-NOTE: {@code mOffsetArray}/{@code mVertArray} are upstream stack locals. Java objects
 * ({@code TriVertex}, the innermost {@code float[2]} arrays) are heap-allocated, and
 * {@code poolEffectDraw} runs every frame, so both are preallocated here and fully rewritten on
 * every call — the drawn values are identical.
 */
public class PoolEffect {

    /** Ported from src/Lawn/System/PoolEffect.cpp:32 ({@code CAUSTIC_IMAGE_WIDTH}). */
    private static final int CAUSTIC_IMAGE_WIDTH = 128;

    /** Ported from src/Lawn/System/PoolEffect.cpp:33 ({@code CAUSTIC_IMAGE_HEIGHT}). */
    private static final int CAUSTIC_IMAGE_HEIGHT = 64;

    /** Upstream {@code std::vector<unsigned char> mCausticGrayscaleImage} (PoolEffect.h:34). */
    public byte[] mCausticGrayscaleImage = new byte[0];

    /** Upstream {@code std::unique_ptr<Sexy::MemoryImage> mCausticImage} (PoolEffect.h:35). */
    public MemoryImage mCausticImage;

    /** Upstream {@code LawnApp* mApp} (PoolEffect.h:36). */
    public LawnApp mApp;

    /** Upstream {@code unsigned int mPoolCounter} (PoolEffect.h:37). */
    public int mPoolCounter;

    /** Ported from src/Lawn/System/PoolEffect.cpp:144 ({@code float aOffsetArray[3][16][6][2]}). */
    private final float[][][][] mOffsetArray = new float[3][16][6][2];

    /** Ported from src/Lawn/System/PoolEffect.cpp:179 ({@code TriVertex aVertArray[3][150][3]}). */
    private final TriVertex[][][] mVertArray = new TriVertex[3][150][3];

    /** Java API contract: instantiates the preallocated vertex buffer (see the class PORT-NOTE). */
    public PoolEffect() {
        for (int aLayer = 0; aLayer < 3; aLayer++) {
            for (int aTriangle = 0; aTriangle < 150; aTriangle++) {
                for (int aVert = 0; aVert < 3; aVert++) {
                    mVertArray[aLayer][aTriangle][aVert] = new TriVertex();
                }
            }
        }
    }

    /** Ported from src/Lawn/System/PoolEffect.cpp:37 (PoolEffect::PoolEffectInitialize). */
    public void poolEffectInitialize() {
        // load pool caustics into memory
        mApp = LawnApp.gLawnApp;
        mPoolCounter = 0;

        mCausticImage = new MemoryImage(SexyAppBase.current);
        mCausticImage.mWidth = CAUSTIC_IMAGE_WIDTH;
        mCausticImage.mHeight = CAUSTIC_IMAGE_HEIGHT;
        mCausticImage.mBits = new int[CAUSTIC_IMAGE_WIDTH * CAUSTIC_IMAGE_HEIGHT + 1];
        mCausticImage.mHasTrans = true;
        mCausticImage.mHasAlpha = true;
        mCausticImage.mRenderFlags |= MemoryImage.RenderImageFlag_Repeat;
        // memset(mBits, 0xFF, W * H * 4): every pixel word becomes 0xFFFFFFFF, the spare word is
        // the MemoryImage canary slot.
        Arrays.fill(mCausticImage.mBits, 0, CAUSTIC_IMAGE_WIDTH * CAUSTIC_IMAGE_HEIGHT, 0xFFFFFFFF);
        mCausticImage.mBits[CAUSTIC_IMAGE_WIDTH * CAUSTIC_IMAGE_HEIGHT] = MemoryImage.MEMORYCHECK_ID;

        mCausticGrayscaleImage = new byte[256 * 256];
        MemoryImage aCausticGrayscaleImage = (MemoryImage) mApp.getImage(Resources.IMAGE_POOL_CAUSTIC_EFFECT_ID);
        int[] aCausticBits = aCausticGrayscaleImage.getBits();
        int index = 0;
        for (int x = 0; x < 256; x++) {
            for (int y = 0; y < 256; y++) {
                mCausticGrayscaleImage[index] = (byte) aCausticBits[index];
                index++;
            }
        }
    }

    /**
     * Ported from src/Lawn/System/PoolEffect.cpp:69 (PoolEffect::BilinearLookupFixedPoint).
     *
     * <p>Fixed-point (16.16) wrap-around bilinear sample of the 256x256 grayscale pattern. The
     * arithmetic is a bit-exact reproduction of upstream's {@code unsigned int} math: {@code >>> 16}
     * stands in for the unsigned {@code / 65536} (which can wrap the intermediate 32-bit product),
     * and the pattern byte is masked with {@code 0xFF}.
     */
    public int bilinearLookupFixedPoint(int u, int v) {
        int timeU = u & 0xFFFF0000;
        int timeV = v & 0xFFFF0000;
        int factorU1 = ((u - timeU) & 0x0000FFFE) + 1;
        int factorV1 = ((v - timeV) & 0x0000FFFE) + 1;
        int factorU0 = 65536 - factorU1;
        int factorV0 = 65536 - factorV1;
        int indexU0 = (timeU >>> 16) % 256;
        int indexU1 = ((timeU >>> 16) + 1) % 256;
        int indexV0 = (timeV >>> 16) % 256;
        int indexV1 = ((timeV >>> 16) + 1) % 256;

        return
                ((((factorU0 * factorV1) >>> 16) * (mCausticGrayscaleImage[indexV1 * 256 + indexU0] & 0xFF)) >>> 16) +
                ((((factorU1 * factorV1) >>> 16) * (mCausticGrayscaleImage[indexV1 * 256 + indexU1] & 0xFF)) >>> 16) +
                ((((factorU0 * factorV0) >>> 16) * (mCausticGrayscaleImage[indexV0 * 256 + indexU0] & 0xFF)) >>> 16) +
                ((((factorU1 * factorV0) >>> 16) * (mCausticGrayscaleImage[indexV0 * 256 + indexU1] & 0xFF)) >>> 16);
    }

    /**
     * Ported from src/Lawn/System/PoolEffect.cpp:89 (PoolEffect::UpdateWaterEffect).
     *
     * <p>Recomputes the 128x64 caustic texture: two scrolling samples of the grayscale pattern are
     * averaged and mapped through the caustic alpha curve, then written into the RGB-preserving
     * (alpha-only) texture word.
     */
    public void updateWaterEffect() {
        int idx = 0;
        for (int y = 0; y < CAUSTIC_IMAGE_HEIGHT; y++) {
            int timeV1 = (256 - y) << 17;
            int timeV0 = y << 17;

            for (int x = 0; x < CAUSTIC_IMAGE_WIDTH; x++) {
                int[] pix = mCausticImage.mBits;

                int timeU = x << 17;
                int timePool0 = mPoolCounter << 16;
                int timePool1 = ((mPoolCounter & 65535) + 1) << 16;
                // Unsigned shifts/divisions: Integer.divideUnsigned matches upstream's unsigned `/`.
                int a1 = bilinearLookupFixedPoint(
                        timeU - Integer.divideUnsigned(timePool1, 6),
                        timeV1 + Integer.divideUnsigned(timePool0, 8)) & 0xFF; // scroll speed
                int a0 = bilinearLookupFixedPoint(
                        timeU + Integer.divideUnsigned(timePool0, 10),
                        timeV0) & 0xFF; // scroll speed
                int a = ((a0 + a1) / 2) & 0xFF;

                int alpha;
                if (a >= 160) {
                    alpha = 255 - 2 * (a - 160);
                } else if (a >= 128) {
                    alpha = 5 * (a - 128);
                } else {
                    alpha = 0;
                }

                pix[idx] = (pix[idx] & 0x00FFFFFF) + ((alpha / 3) << 24); // alpha of caustic effect
                idx++;
            }
        }

        // Upstream bumps the texture-invalidation counter directly (MemoryImage.h mBitsChangedCount).
        mCausticImage.mBitsChangedCount++;
    }

    /**
     * Ported from src/Lawn/System/PoolEffect.cpp:130 (PoolEffect::PoolEffectDraw).
     *
     * <p>Draws the pool backdrop (two animated wave layers plus the caustic layer) as 150 triangles
     * each over a 16x6 vertex grid; with software rendering it collapses to the flat pool image.
     */
    public void poolEffectDraw(Graphics g, boolean theIsNight) {
        if (!mApp.is3DAccelerated()) {
            // skip if using software rendering, never true in this port
            if (theIsNight) {
                g.drawImage(mApp.getImage(Resources.IMAGE_POOL_NIGHT_ID), 34, 278);
            } else {
                g.drawImage(mApp.getImage(Resources.IMAGE_POOL_ID), 34, 278);
            }
            return;
        }
        // pool background
        Image aPoolImage = mApp.getImage(Resources.IMAGE_POOL_ID);
        float aGridSquareX = aPoolImage.getWidth() / 15.0f;
        float aGridSquareY = aPoolImage.getHeight() / 5.0f;
        float[][][][] aOffsetArray = mOffsetArray;
        for (int x = 0; x <= 15; x++) {
            for (int y = 0; y <= 5; y++) { // handles the caustic effect
                aOffsetArray[2][x][y][0] = x / 15.0f;
                aOffsetArray[2][x][y][1] = y / 5.0f;
                if (x != 0 && x != 15 && y != 0 && y != 5) {
                    // LCM of all sin wave effective periods (1600, 300, 1800, 220, 3200/3, 200, 720, 640, 88)
                    final int POOL_PHASE_PERIOD = 316800;
                    float aPoolPhase = (float) (Integer.remainderUnsigned(mPoolCounter, POOL_PHASE_PERIOD) * GameConstants.PI); // speed, * 2 is default
                    float aWaveTime1 = (float) (aPoolPhase / 800.0);
                    float aWaveTime2 = (float) (aPoolPhase / 150.0);
                    float aWaveTime3 = (float) (aPoolPhase / 900.0);
                    float aWaveTime4 = (float) (aPoolPhase / 800.0);
                    float aWaveTime5 = (float) (aPoolPhase / 110.0);
                    float xPhase = (float) (x * 3.0f * 2 * GameConstants.PI / 15.0f); // 15.0f
                    float yPhase = (float) (y * 3.0f * 2 * GameConstants.PI / 5.0f); // more speed options, 5.0f
                    // verticies for rendering, dividing by 1 gives interesting results
                    aOffsetArray[0][x][y][0] = (float) (Math.sin(yPhase + aWaveTime2) * 0.002f + Math.sin(yPhase + aWaveTime1) * 0.005f);
                    aOffsetArray[0][x][y][1] = (float) (Math.sin(xPhase + aWaveTime5) * 0.01f + Math.sin(xPhase + aWaveTime3) * 0.015f + Math.sin(xPhase + aWaveTime4) * 0.005f);
                    aOffsetArray[1][x][y][0] = (float) (Math.sin(yPhase * 0.2f + aWaveTime2) * 0.015f + Math.sin(yPhase * 0.2f + aWaveTime1) * 0.012f);
                    aOffsetArray[1][x][y][1] = (float) (Math.sin(xPhase * 0.2f + aWaveTime5) * 0.005f + Math.sin(xPhase * 0.2f + aWaveTime3) * 0.015f + Math.sin(xPhase * 0.2f + aWaveTime4) * 0.02f);
                    aOffsetArray[2][x][y][0] += Math.sin(yPhase + aWaveTime1 * 1.5f) * 0.004f + Math.sin(yPhase + aWaveTime2 * 1.5f) * 0.005f;
                    aOffsetArray[2][x][y][1] += Math.sin(xPhase * 4.0f + aWaveTime5 * 2.5f) * 0.005f + Math.sin(xPhase * 2.0f + aWaveTime3 * 2.5f) * 0.04f + Math.sin(xPhase * 3.0f + aWaveTime4 * 2.5f) * 0.02f;
                } else {
                    // skip animation
                    aOffsetArray[0][x][y][0] = 0.0f;
                    aOffsetArray[0][x][y][1] = 0.0f;
                    aOffsetArray[1][x][y][0] = 0.0f;
                    aOffsetArray[1][x][y][1] = 0.0f;
                }
            }
        }

        int[] aIndexOffsetX = { 0, 0, 1, 0, 1, 1 };
        int[] aIndexOffsetY = { 0, 1, 1, 0, 1, 0 };
        TriVertex[][][] aVertArray = mVertArray;

        for (int x = 0; x < 15; x++) {
            for (int y = 0; y < 5; y++) {
                // Upstream walks 6 consecutive TriVertex from &aVertArray[aLayer][x*10+y*2][0],
                // i.e. two triangles per grid cell (cell c = x*5+y occupies slots 2c and 2c+1).
                int aBaseSlot = x * 10 + y * 2;
                for (int aLayer = 0; aLayer < 3; aLayer++) {
                    for (int aVertIndex = 0; aVertIndex < 6; aVertIndex++) {
                        int aIndexX = x + aIndexOffsetX[aVertIndex];
                        int aIndexY = y + aIndexOffsetY[aVertIndex];
                        TriVertex pVert = aVertArray[aLayer][aBaseSlot + aVertIndex / 3][aVertIndex % 3];
                        if (aLayer == 2) { // caustic effect
                            pVert.x = (704.0f / 15.0f) * aIndexX + 45.0f; // x-offset
                            pVert.y = 30.0f * aIndexY + 288.0f; // y-offset
                            pVert.u = aOffsetArray[2][aIndexX][aIndexY][0] + aIndexX / 15.0f;
                            pVert.v = aOffsetArray[2][aIndexX][aIndexY][1] + aIndexY / 5.0f;
                            // use correct colors depending on the scene
                            if (!g.mClipRect.contains((int) pVert.x, (int) pVert.y)) {
                                pVert.color = 0x00FFFFFF;
                            } else if (aIndexX == 0 || aIndexX == 15 || aIndexY == 0) {
                                pVert.color = 0x20FFFFFF;
                            } else if (theIsNight) {
                                pVert.color = 0x30FFFFFF;
                            } else {
                                pVert.color = aIndexX <= 7 ? 0xC0FFFFFF : 0x80FFFFFF;
                            }
                        } else {
                            // update water outlines
                            pVert.color = 0xFFFFFFFF;
                            pVert.x = aIndexX * aGridSquareX + 35.0f;
                            pVert.y = aIndexY * aGridSquareY + 279.0f;
                            pVert.u = aOffsetArray[aLayer][aIndexX][aIndexY][0] + aIndexX / 15.0f;
                            pVert.v = aOffsetArray[aLayer][aIndexX][aIndexY][1] + aIndexY / 5.0f;
                            if (!g.mClipRect.contains((int) pVert.x, (int) pVert.y)) {
                                pVert.color = 0x00FFFFFF;
                            }
                        }
                    }
                }
            }
        }
        // draw correct shading type depending on area.
        if (theIsNight) {
            g.drawTrianglesTex(mApp.getImage(Resources.IMAGE_POOL_BASE_NIGHT_ID), aVertArray[0], 150);
            g.drawTrianglesTex(mApp.getImage(Resources.IMAGE_POOL_SHADING_NIGHT_ID), aVertArray[1], 150);
        } else {
            g.drawTrianglesTex(mApp.getImage(Resources.IMAGE_POOL_BASE_ID), aVertArray[0], 150);
            g.drawTrianglesTex(mApp.getImage(Resources.IMAGE_POOL_SHADING_ID), aVertArray[1], 150);
        }
        // update positions
        updateWaterEffect();
        // Send caustic effect tris to OpenGL (tex, verts, tris)
        g.drawTrianglesTex(mCausticImage, aVertArray[2], 150);
    }

    /** Ported from src/Lawn/System/PoolEffect.cpp:261 (PoolEffect::PoolEffectUpdate). */
    public void poolEffectUpdate() {
        ++mPoolCounter;
    }
}
