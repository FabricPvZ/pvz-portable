package cn.enaium.fabricpvz.portable.pvzp.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.TriVertex;

/**
 * {@link PvzpTriangleGroup} batching and flush-contract tests against a recording {@link Image} test
 * double.
 *
 * <p>The flush must hand the batch to the destination image itself with the destination translation
 * {@code (0, 0)} (EffectSystem.cpp:392-405): the vertices already carry the draw translation, because
 * {@link PvzpParticleEmitter#drawParticle} adds {@code g.mTransX}/{@code g.mTransY} to the particle
 * position (PvzpParticle.cpp:1035). Routing the batch through {@link Graphics#drawTrianglesTex}
 * (Graphics.cpp:945 forwards {@code mTransX}/{@code mTransY}) applied it a second time and drew every
 * particle batch {@code (mTransX, mTransY)} off — the fume-shroom spore cloud landed 220 px to the
 * right of the plant.
 */
class PvzpTriangleGroupTest {

    /** Records every batched triangle blit made against it. */
    private static final class RecordingImage extends Image {
        int calls;
        Image texture;
        int triangleCount;
        Rect clipRect;
        Color color;
        int drawMode;
        float tx;
        float ty;
        boolean blend;
        final List<TriVertex[][]> vertices = new ArrayList<>();

        @Override
        public void bltTrianglesTex(Image theTexture, TriVertex[][] theVertices, int theNumTriangles,
                                    Rect theClipRect, Color theColor, int theDrawMode, float tx, float ty, boolean blend) {
            calls++;
            texture = theTexture;
            triangleCount = theNumTriangles;
            clipRect = theClipRect;
            color = theColor;
            drawMode = theDrawMode;
            this.tx = tx;
            this.ty = ty;
            this.blend = blend;
            vertices.add(theVertices);
        }
    }

    private static Image texture() {
        Image image = new Image();
        image.mWidth = 32;
        image.mHeight = 27;
        return image;
    }

    /** Translation-only matrix, i.e. what {@code PvzpScaleRotateTransformMatrix} yields for scale 1. */
    private static SexyMatrix3 translation(float x, float y) {
        SexyMatrix3 m = new SexyMatrix3();
        m.loadIdentity();
        m.m02 = x;
        m.m12 = y;
        return m;
    }

    @Test
    void flushAppliesTheDrawTranslationOnlyOnce() {
        Image aTexture = texture();
        RecordingImage dest = new RecordingImage();
        dest.mWidth = 800;
        dest.mHeight = 600;
        Graphics g = new Graphics(dest);
        // The board is drawn translated (its widget offset); the particle path adds this to the
        // particle position before transforming the vertices.
        g.mTransX = 220.0f;
        g.mTransY = 0.0f;

        PvzpTriangleGroup group = new PvzpTriangleGroup();
        group.addTriangle(g, aTexture, translation(447.0f, 310.0f), g.mClipRect,
                new Color(255, 255, 255, 207), Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 32, 27));
        group.drawGroup(g);

        assertEquals(1, dest.calls);
        assertEquals(aTexture, dest.texture);
        assertEquals(2, dest.triangleCount);
        // The batch goes straight to the destination image: no second application of the Graphics
        // translation (upstream passes 0.0f, 0.0f to BltTrianglesTex).
        assertEquals(0.0f, dest.tx, 0.0f);
        assertEquals(0.0f, dest.ty, 0.0f);
        // The batch's own draw mode and the white tint are passed, the colour rides on the vertices.
        assertEquals(Graphics.DRAWMODE_NORMAL, dest.drawMode);
        assertEquals(Color.White, dest.color);
        assertEquals(g.mClipRect, dest.clipRect);

        TriVertex[][] v = dest.vertices.get(0);
        // Quad centred on the matrix translation, half a source pixel in: 447 - 16 - 0.5.
        assertEquals(430.5f, v[0][0].x, 0.0001f);
        assertEquals(296.0f, v[0][0].y, 0.0001f);
        assertEquals(462.5f, v[0][2].x, 0.0001f);
        assertEquals(207, (v[0][0].color >>> 24) & 0xFF);
    }

    @Test
    void flushCarriesTheBatchDrawMode() {
        Image aTexture = texture();
        RecordingImage dest = new RecordingImage();
        dest.mWidth = 800;
        dest.mHeight = 600;
        Graphics g = new Graphics(dest);
        assertEquals(Graphics.DRAWMODE_NORMAL, g.mDrawMode);

        PvzpTriangleGroup group = new PvzpTriangleGroup();
        group.addTriangle(g, aTexture, translation(100.0f, 100.0f), g.mClipRect,
                Color.White, Graphics.DRAWMODE_ADDITIVE, new Rect(0, 0, 32, 27));
        group.drawGroup(g);

        assertEquals(Graphics.DRAWMODE_ADDITIVE, dest.drawMode);
        assertTrue(group.mTriangleCount == 0);
    }

    @Test
    void flushHappensWhenTheImageChanges() {
        Image aFirst = texture();
        Image aSecond = texture();
        RecordingImage dest = new RecordingImage();
        dest.mWidth = 800;
        dest.mHeight = 600;
        Graphics g = new Graphics(dest);

        PvzpTriangleGroup group = new PvzpTriangleGroup();
        group.addTriangle(g, aFirst, translation(100.0f, 100.0f), g.mClipRect,
                Color.White, Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 32, 27));
        group.addTriangle(g, aSecond, translation(200.0f, 100.0f), g.mClipRect,
                Color.White, Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 32, 27));

        assertEquals(1, dest.calls);
        assertEquals(aFirst, dest.texture);
        assertEquals(aSecond, group.mImage);
        assertEquals(2, group.mTriangleCount);
    }
}
