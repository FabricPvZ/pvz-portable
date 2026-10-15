package cn.enaium.fabricpvz.portable.pvzp.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.TriVertex;

/**
 * Trail point lifecycle and render-call tests against a recording {@link Image} test double.
 */
class TrailTest {

    private static FloatParameterTrack constant(float v) {
        FloatParameterTrack t = new FloatParameterTrack();
        Definition.floatTrackSetDefault(t, v);
        return t;
    }

    private static TrailDefinition definition() {
        TrailDefinition d = new TrailDefinition();
        d.mImage = new Image();
        d.mImage.mWidth = 16;
        d.mImage.mHeight = 16;
        d.mMaxPoints = 2;
        d.mMinPointDistance = 1.0f;
        d.mWidthOverLength = constant(1.0f);
        d.mWidthOverTime = constant(1.0f);
        d.mAlphaOverLength = constant(1.0f);
        d.mAlphaOverTime = constant(1.0f);
        d.mTrailDuration = constant(10.0f);
        return d;
    }

    /** Records every batched triangle blit made through {@link Graphics#drawTrianglesTex}. */
    private static final class RecordingImage extends Image {
        int calls;
        Image texture;
        int triangleCount;
        final List<TriVertex[][]> vertices = new ArrayList<>();

        @Override
        public void bltTrianglesTex(Image theTexture, TriVertex[][] theVertices, int theNumTriangles,
                                    Rect theClipRect, Color theColor, int theDrawMode, float tx, float ty, boolean blend) {
            calls++;
            texture = theTexture;
            triangleCount = theNumTriangles;
            vertices.add(theVertices);
        }
    }

    @Test
    void drawEmitsTwoTrianglesWithHandComputedVertices() {
        Trail trail = new Trail();
        trail.mDefinition = definition();
        trail.mTrailDuration = 10;
        trail.mTrailCenter.x = 0.0f;
        trail.mTrailCenter.y = 0.0f;
        trail.addPoint(0.0f, 0.0f);
        trail.addPoint(10.0f, 0.0f);
        assertEquals(2, trail.mNumTrailPoints);

        RecordingImage dest = new RecordingImage();
        dest.mWidth = 800;
        dest.mHeight = 600;
        Graphics g = new Graphics(dest);

        trail.draw(g);

        assertEquals(1, dest.calls);
        assertEquals(trail.mDefinition.mImage, dest.texture);
        assertEquals(2, dest.triangleCount);

        TriVertex[][] v = dest.vertices.get(0);
        // Segment 0: u runs 1 -> 0.5, normal is (0,1), width*time = 1, alpha = 1*1*255.
        assertEquals(0.0f, v[0][0].x, 0.0f);
        assertEquals(1.0f, v[0][0].y, 0.0f);
        assertEquals(1.0f, v[0][0].u, 0.0f);
        assertEquals(1.0f, v[0][0].v, 0.0f);
        assertEquals(0xFFFFFFFF, v[0][0].color);

        assertEquals(0.0f, v[0][1].x, 0.0f);
        assertEquals(-1.0f, v[0][1].y, 0.0f);
        assertEquals(1.0f, v[0][1].u, 0.0f);
        assertEquals(0.0f, v[0][1].v, 0.0f);

        assertEquals(10.0f, v[0][2].x, 0.0f);
        assertEquals(1.0f, v[0][2].y, 0.0f);
        assertEquals(0.0f, v[0][2].u, 0.0f);
        assertEquals(1.0f, v[0][2].v, 0.0f);

        // Second triangle of the segment.
        assertEquals(10.0f, v[1][0].x, 0.0f);
        assertEquals(0.0f, v[1][0].u, 0.0f);
        assertEquals(1.0f, v[1][0].v, 0.0f);
        assertEquals(0.0f, v[1][1].x, 0.0f);
        assertEquals(-1.0f, v[1][1].y, 0.0f);
        assertEquals(1.0f, v[1][1].u, 0.0f);
        assertEquals(0.0f, v[1][1].v, 0.0f);
        assertEquals(10.0f, v[1][2].x, 0.0f);
        assertEquals(-1.0f, v[1][2].y, 0.0f);
        assertEquals(0.0f, v[1][2].u, 0.0f);
        assertEquals(0.0f, v[1][2].v, 0.0f);
    }

    @Test
    void drawSkippedWhenDeadOrTooFewPoints() {
        RecordingImage dest = new RecordingImage();
        dest.mWidth = 800;
        dest.mHeight = 600;
        Graphics g = new Graphics(dest);

        Trail trail = new Trail();
        trail.mDefinition = definition();
        trail.mTrailDuration = 10;
        trail.addPoint(0.0f, 0.0f);
        trail.draw(g);
        assertEquals(0, dest.calls, "a single point draws nothing");

        trail.addPoint(10.0f, 0.0f);
        trail.mDead = true;
        trail.draw(g);
        assertEquals(0, dest.calls, "a dead trail draws nothing");
    }

    @Test
    void addPointAppliesSpacingAndCapacityRules() {
        Trail trail = new Trail();
        trail.mDefinition = definition();
        trail.mTrailDuration = 10;
        trail.addPoint(0.0f, 0.0f);
        trail.addPoint(0.5f, 0.0f);
        assertEquals(1, trail.mNumTrailPoints, "points closer than MinPointDistance are rejected");

        trail.addPoint(3.0f, 0.0f);
        assertEquals(2, trail.mNumTrailPoints);
        trail.addPoint(6.0f, 0.0f); // MaxPoints == 2 => the oldest point is dropped
        assertEquals(2, trail.mNumTrailPoints);
        assertEquals(3.0f, trail.mTrailPoints[0].aPos.x, 0.0f);
        assertEquals(6.0f, trail.mTrailPoints[1].aPos.x, 0.0f);
    }

    @Test
    void updateHonoursLifetimeAndLoopFlag() {
        Trail trail = new Trail();
        trail.mDefinition = definition();
        trail.mTrailDuration = 3;
        trail.update();
        trail.update();
        assertFalse(trail.mDead);
        trail.update();
        assertTrue(trail.mDead, "a non-looping trail dies when its age reaches the duration");

        Trail looping = new Trail();
        looping.mDefinition = definition();
        looping.mDefinition.mTrailFlags = 1 << TrailFlags.TRAIL_FLAG_LOOPS;
        looping.mTrailDuration = 3;
        for (int i = 0; i < 10; i++) {
            looping.update();
        }
        assertFalse(looping.mDead);
        assertEquals(1, looping.mTrailAge);
    }
}
