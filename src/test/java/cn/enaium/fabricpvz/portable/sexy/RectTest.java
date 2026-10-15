package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Rect} and {@link FRect}, covering the intersect/union/contains/inflate
 * edges derived directly from upstream's half-open interval conventions
 * ({@code x >= mX && x < mX + mWidth}) and its "touching rectangles do not intersect" rule.
 */
class RectTest {

    @Test
    void intersectsRejectsTouchingEdges() {
        Rect a = new Rect(0, 0, 10, 10);

        assertTrue(a.intersects(new Rect(5, 5, 10, 10)));
        assertFalse(a.intersects(new Rect(10, 0, 5, 5)));   // touching on the right edge
        assertFalse(a.intersects(new Rect(0, 10, 5, 5)));   // touching on the bottom edge
        assertFalse(a.intersects(new Rect(10, 10, 5, 5)));  // corner touch
        assertFalse(a.intersects(new Rect(-5, -5, 5, 5)));  // touching top-left corner
        assertTrue(a.intersects(new Rect(-5, -5, 6, 6)));
    }

    @Test
    void intersectionClipsToOverlap() {
        Rect a = new Rect(0, 0, 10, 10);

        assertEquals(new Rect(5, 5, 5, 5), a.intersection(new Rect(5, 5, 10, 10)));
        assertEquals(new Rect(0, 0, 10, 10), a.intersection(new Rect(-5, -5, 20, 20)));
        // Touching edges yield a degenerate rectangle with one zero dimension.
        assertEquals(new Rect(10, 0, 0, 5), a.intersection(new Rect(10, 0, 5, 5)));
        // Fully disjoint regions collapse to (0,0,0,0).
        assertEquals(new Rect(0, 0, 0, 0), a.intersection(new Rect(20, 20, 5, 5)));
    }

    @Test
    void unionCoversBothRectangles() {
        Rect a = new Rect(0, 0, 10, 10);
        assertEquals(new Rect(0, 0, 15, 15), a.union(new Rect(5, 5, 10, 10)));
        assertEquals(new Rect(-5, -5, 20, 20), a.union(new Rect(-5, -5, 20, 20)));
    }

    @Test
    void containsUsesHalfOpenIntervals() {
        Rect a = new Rect(0, 0, 10, 10);

        assertTrue(a.contains(0, 0));
        assertTrue(a.contains(9, 9));
        assertFalse(a.contains(10, 5));   // right edge is exclusive
        assertFalse(a.contains(5, 10));   // bottom edge is exclusive
        assertFalse(a.contains(-1, 5));

        assertTrue(a.contains(new Point(1, 1)));
        assertFalse(a.contains(new Point(10, 1)));
    }

    @Test
    void negativeSizesNeverContainPoints() {
        Rect a = new Rect(0, 0, -5, -5);

        assertFalse(a.contains(0, 0));
        assertFalse(a.contains(-1, -1));
        assertFalse(a.intersects(new Rect(-10, -10, 2, 2)));
        assertEquals(new Rect(0, 0, 0, 0), a.intersection(new Rect(-10, -10, 20, 20)));
    }

    @Test
    void offsetAndInflateMutateInPlace() {
        Rect a = new Rect(5, 5, 10, 10);
        a.offset(2, 3);
        assertEquals(new Rect(7, 8, 10, 10), a);

        a.offset(new Point(-7, -8));
        assertEquals(new Rect(0, 0, 10, 10), a);

        Rect same = a.inflate(2, 3);
        assertEquals(new Rect(-2, -3, 14, 16), a);
        assertEquals(new Rect(-2, -3, 14, 16), same);
        assertEquals(a, same);
    }

    @Test
    void valueSemantics() {
        assertEquals(new Rect(1, 2, 3, 4), new Rect(1, 2, 3, 4));
        assertNotEquals(new Rect(1, 2, 3, 4), new Rect(1, 2, 3, 5));
        assertEquals(new Rect(1, 2, 3, 4).hashCode(), new Rect(1, 2, 3, 4).hashCode());
    }

    @Test
    void fRectMirrorsIntegerBehaviour() {
        FRect a = new FRect(0, 0, 10, 10);

        assertTrue(a.intersects(new FRect(9.5, 9.5, 1, 1)));
        assertFalse(a.intersects(new FRect(10, 0, 1, 1)));
        assertEquals(new FRect(5, 5, 5, 5), a.intersection(new FRect(5, 5, 10, 10)));
        assertEquals(new FRect(0, 0, 15, 15), a.union(new FRect(5, 5, 10, 10)));
        assertTrue(a.contains(9.999, 0.0));
        assertFalse(a.contains(10.0, 0.0));

        FRect b = new FRect(1, 2, 3, 4);
        b.inflate(0.5, 0.5);
        assertEquals(new FRect(0.5, 1.5, 4.0, 5.0), b);
    }
}
