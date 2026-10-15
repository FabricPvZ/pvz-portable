package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.TriVertex;

/**
 * Ported from src/PvzpLib/EffectSystem.h:31 (class PvzpTriangleGroup) and
 * src/PvzpLib/EffectSystem.cpp:364-553.
 *
 * <p>Batches up to {@code MAX_TRIANGLES} transformed texture quads (two triangles each) for one
 * image and flushes them through the destination image's triangle blit. The software triangle
 * rasterisers ({@code PvzpDrawTriangle.inc}, {@code PvzpDrawTriangleInc.inc},
 * {@code Pvzp_SWTri_AddAllDrawTriFuncs}) are NOT ported: this port draws through the framework's
 * triangle blit (the backend rasteriser on {@link GLImage}, {@link MemoryImage#bltTrianglesTex}
 * otherwise), which is exactly what {@code DrawGroup} does upstream, so the whole software-only
 * rasteriser and the {@code gPvzpTriangleDrawAdditive} flag are obsolete here.
 *
 * <p>PORT-NOTE: upstream keeps mixed draw modes in one batch when the destination is the GL screen
 * image ({@code CanMixTriangleDrawModes}) and otherwise flushes. Java's batched blit takes a single
 * draw mode, so this port always flushes when the image or the draw mode changes — the emitted draw
 * order (emitter order, then particle order head→tail, with a flush per image/mode/cap) is
 * unchanged. The Sutherland–Hodgman clip against the draw clip rect is likewise omitted:
 * {@code DrawGroup} passes {@code g.mClipRect} to the flush, which bounds the software rasteriser
 * ({@link MemoryImage#bltTrianglesTex}) the way upstream's vertex-level clip to {@code g->mClipRect}
 * does (EffectSystem.cpp:445-455), while the 64-vertex reservoir and its clippers stay dead weight
 * here. The GPU path ignores that rect (the destination translation is {@code 0} there and a partial
 * clip is a pre-existing backend gap, not something this batch introduces).
 */
public class PvzpTriangleGroup {

    /** Ported from src/PvzpLib/EffectSystem.h:29 ({@code MAX_TRIANGLES}). */
    public static final int MAX_TRIANGLES = 256;

    public Image mImage;
    public TriVertex[][] mVertArray = new TriVertex[MAX_TRIANGLES][3];
    public byte[] mTriangleDrawModes = new byte[MAX_TRIANGLES];
    public int mTriangleCount;
    public int mDrawMode;
    public boolean mHasMixedDrawModes;

    /** Ported from src/PvzpLib/EffectSystem.cpp:364. */
    public PvzpTriangleGroup() {
        for (int i = 0; i < MAX_TRIANGLES; i++) {
            for (int j = 0; j < 3; j++) {
                mVertArray[i][j] = new TriVertex();
            }
        }
        mImage = null;
        mTriangleCount = 0;
        mDrawMode = Graphics.DRAWMODE_NORMAL;
        mHasMixedDrawModes = false;
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:374. */
    public int appendTriangleDrawMode(int theDrawMode) {
        int aTriangleIndex = mTriangleCount++;
        if (aTriangleIndex > 0 && mDrawMode != theDrawMode) {
            mHasMixedDrawModes = true;
        }
        mTriangleDrawModes[aTriangleIndex] = (byte) theDrawMode;
        mDrawMode = theDrawMode;
        return aTriangleIndex;
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:386. */
    public void drawGroup(Graphics g) {
        if (mImage != null && mTriangleCount != 0) {
            // EffectSystem.cpp:392-405 hands the batch to the destination image directly, with the
            // destination translation (0, 0), the batch's own draw mode and a white tint (the colour
            // rides on the vertices). Graphics::DrawTrianglesTex (Graphics.cpp:945, Graphics.java:1005)
            // instead forwards mTransX/mTransY, which the vertices already carry: DrawParticle adds
            // g->mTransX/mTransY to the particle position (PvzpParticle.cpp:1035,
            // PvzpParticleEmitter.java:767-768), so routing the batch through it applied the draw
            // translation a second time and pushed every particle batch off by (mTransX, mTransY).
            g.mDestImage.bltTrianglesTex(mImage, mVertArray, mTriangleCount, g.mClipRect,
                    Color.White, mDrawMode, 0.0f, 0.0f, g.mLinearBlend);
            mTriangleCount = 0;
            mHasMixedDrawModes = false;
        }
    }

    /** Ported from src/PvzpLib/EffectSystem.cpp:417. */
    public void addTriangle(Graphics g, Image theImage, SexyMatrix3 theMatrix, Rect theClipRect,
                            Color theColor, int theDrawMode, Rect theSrcRect) {
        if (mTriangleCount > 0 && mImage != theImage) {
            drawGroup(g);
        }
        if (mTriangleCount > 0 && mDrawMode != theDrawMode) {
            drawGroup(g);
        }
        mImage = theImage;

        SexyVector2[] p = new SexyVector2[4];
        float x = -theSrcRect.mWidth * 0.5f;
        float y = -theSrcRect.mHeight * 0.5f;
        p[0] = new SexyVector2(x, y);
        p[1] = new SexyVector2(x, y + theSrcRect.mHeight);
        p[2] = new SexyVector2(x + theSrcRect.mWidth, y);
        p[3] = new SexyVector2(x + theSrcRect.mWidth, y + theSrcRect.mHeight);
        SexyVector2[] tp = new SexyVector2[4];
        for (int i = 0; i < 4; i++) {
            tp[i] = theMatrix.times(p[i]);
            tp[i].x -= 0.5f;
            tp[i].y -= 0.5f;
        }

        float aOneOverWidth = 1.0f / mImage.mWidth;
        float aOneOverHeight = 1.0f / mImage.mHeight;
        float aTexX1 = aOneOverWidth * theSrcRect.mX;
        float aTexY1 = aOneOverHeight * theSrcRect.mY;
        float aTexX2 = aOneOverWidth * (theSrcRect.mX + theSrcRect.mWidth);
        float aTexY2 = aOneOverHeight * (theSrcRect.mY + theSrcRect.mHeight);
        int aTexColor = theColor.toInt();

        if (mTriangleCount + 2 > MAX_TRIANGLES) {
            drawGroup(g);
        }
        appendTriangleDrawMode(theDrawMode);
        int aTri = mTriangleCount - 1;
        appendTriangleDrawMode(theDrawMode);
        int aTri2 = mTriangleCount - 1;

        setVertex(mVertArray[aTri][0], tp[0].x, tp[0].y, aTexX1, aTexY1, aTexColor);
        setVertex(mVertArray[aTri][1], tp[1].x, tp[1].y, aTexX1, aTexY2, aTexColor);
        setVertex(mVertArray[aTri][2], tp[2].x, tp[2].y, aTexX2, aTexY1, aTexColor);
        setVertex(mVertArray[aTri2][0], tp[2].x, tp[2].y, aTexX2, aTexY1, aTexColor);
        setVertex(mVertArray[aTri2][1], tp[1].x, tp[1].y, aTexX1, aTexY2, aTexColor);
        setVertex(mVertArray[aTri2][2], tp[3].x, tp[3].y, aTexX2, aTexY2, aTexColor);

        if (mTriangleCount == MAX_TRIANGLES) {
            drawGroup(g);
        }
    }

    private static void setVertex(TriVertex v, float x, float y, float u, float vv, int color) {
        v.x = x;
        v.y = y;
        v.u = u;
        v.v = vv;
        v.color = color;
    }
}
