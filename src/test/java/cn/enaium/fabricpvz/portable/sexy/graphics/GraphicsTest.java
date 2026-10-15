package cn.enaium.fabricpvz.portable.sexy.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Behaviour tests for {@link Graphics}: it is a pure CPU-side state object that forwards to the
 * destination {@link Image}, so a recording {@link Image} subclass is used to assert the exact
 * arguments each blit entry point receives (upstream src/SexyAppFramework/graphics/Graphics.cpp).
 */
class GraphicsTest {

    /** Records the arguments of every virtual blit call. */
    static class RecordingImage extends Image {

        static class Blt {
            Image image;
            int x;
            int y;
            Rect src;
            Color color;
            int drawMode;
            boolean linear;
            double rot;
            float rotCx;
            float rotCy;
            Rect dest;
            boolean fastStretch;
        }

        final List<Blt> blts = new ArrayList<>();
        final List<Blt> stretchBlts = new ArrayList<>();
        final List<Blt> rotatedBlts = new ArrayList<>();
        final List<Blt> mirrorBlts = new ArrayList<>();
        final List<Blt> matrixBlts = new ArrayList<>();
        final List<Blt> fillRects = new ArrayList<>();

        RecordingImage(int w, int h) {
            mWidth = w;
            mHeight = h;
            mNumCols = 1;
            mNumRows = 1;
        }

        private Blt base(Image theImage, Color theColor, int theDrawMode) {
            Blt b = new Blt();
            b.image = theImage;
            b.color = theColor;
            b.drawMode = theDrawMode;
            return b;
        }

        @Override
        public void blt(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.x = theX;
            b.y = theY;
            b.src = theSrcRect;
            b.linear = linearFilter;
            blts.add(b);
        }

        @Override
        public void bltF(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.x = (int) theX;
            b.y = (int) theY;
            b.src = theSrcRect;
            blts.add(b);
        }

        @Override
        public void stretchBlt(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.dest = theDestRect;
            b.src = theSrcRect;
            b.fastStretch = fastStretch;
            stretchBlts.add(b);
        }

        @Override
        public void bltRotated(Image theImage, float theX, float theY, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, double theRot, float theRotCenterX, float theRotCenterY) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.x = (int) theX;
            b.y = (int) theY;
            b.src = theSrcRect;
            b.rot = theRot;
            b.rotCx = theRotCenterX;
            b.rotCy = theRotCenterY;
            rotatedBlts.add(b);
        }

        @Override
        public void bltMirror(Image theImage, int theX, int theY, Rect theSrcRect, Color theColor, int theDrawMode, boolean linearFilter) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.x = theX;
            b.y = theY;
            b.src = theSrcRect;
            mirrorBlts.add(b);
        }

        @Override
        public void stretchBltMirror(Image theImage, Rect theDestRect, Rect theSrcRect, Rect theClipRect, Color theColor, int theDrawMode, boolean fastStretch) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.dest = theDestRect;
            b.src = theSrcRect;
            mirrorBlts.add(b);
        }

        @Override
        public void bltMatrix(Image theImage, float x, float y, cn.enaium.fabricpvz.portable.sexy.SexyMatrix3 theMatrix, Rect theClipRect, Color theColor, int theDrawMode, Rect theSrcRect, boolean blend) {
            Blt b = base(theImage, theColor, theDrawMode);
            b.x = (int) x;
            b.y = (int) y;
            b.src = theSrcRect;
            matrixBlts.add(b);
        }

        @Override
        public void fillRect(Rect theRect, Color theColor, int theDrawMode) {
            Blt b = base(null, theColor, theDrawMode);
            b.dest = theRect;
            fillRects.add(b);
        }
    }

    /** Monospace font: every code point is 10 px wide, ascent 8, height 10. */
    static class FakeFont extends Font {

        static class Draw {
            int x;
            int y;
            String text;
        }

        final List<Draw> draws = new ArrayList<>();

        FakeFont() {
            mAscent = 8;
            mAscentPadding = 0;
            mHeight = 10;
            mLineSpacingOffset = 0;
        }

        @Override
        public int stringWidth(String theString) {
            return 10 * theString.codePointCount(0, theString.length());
        }

        @Override
        public int charWidth(int theChar) {
            return 10;
        }

        @Override
        public int charWidthKern(int theChar, int thePrevChar) {
            return 10;
        }

        @Override
        public void drawString(Graphics g, int theX, int theY, String theString, Color theColor, Rect theClipRect) {
            Draw d = new Draw();
            d.x = theX;
            d.y = theY;
            d.text = theString;
            draws.add(d);
        }

        @Override
        public Font duplicate() {
            return new FakeFont();
        }
    }

    private static void assertRect(Rect expected, Rect actual) {
        assertEquals(expected.mX, actual.mX, "x");
        assertEquals(expected.mY, actual.mY, "y");
        assertEquals(expected.mWidth, actual.mWidth, "w");
        assertEquals(expected.mHeight, actual.mHeight, "h");
    }

    @Test
    void celSelectionPicksColumnAndRow() {
        RecordingImage dest = new RecordingImage(200, 200);
        RecordingImage strip = new RecordingImage(60, 20);
        strip.mNumCols = 3;
        strip.mNumRows = 1;

        Graphics g = new Graphics(dest);
        g.drawImageCel(strip, 5, 7, 2); // cel 2 -> col 2, row 0

        assertEquals(1, dest.blts.size());
        RecordingImage.Blt b = dest.blts.get(0);
        assertEquals(5, b.x);
        assertEquals(7, b.y);
        assertRect(new Rect(40, 0, 20, 20), b.src);
        assertEquals(Color.White.toInt(), b.color.toInt());
        assertEquals(Graphics.DRAWMODE_NORMAL, b.drawMode);
        assertTrue(b.linear);
    }

    @Test
    void celSelectionRejectsOutOfRange() {
        RecordingImage dest = new RecordingImage(200, 200);
        RecordingImage strip = new RecordingImage(60, 20);
        strip.mNumCols = 3;
        strip.mNumRows = 2;
        Graphics g = new Graphics(dest);
        g.drawImageCel(strip, 0, 0, 3, 0); // col 3 >= numCols
        g.drawImageCel(strip, 0, 0, 0, 2); // row 2 >= numRows
        assertTrue(dest.blts.isEmpty());
    }

    @Test
    void clipRectIntersectionShrinksSource() {
        RecordingImage dest = new RecordingImage(200, 200);
        RecordingImage img = new RecordingImage(20, 20);
        Graphics g = new Graphics(dest);

        g.setClipRect(0, 0, 10, 10);
        g.drawImage(img, 5, 5);

        assertEquals(1, dest.blts.size());
        RecordingImage.Blt b = dest.blts.get(0);
        assertEquals(5, b.x);
        assertEquals(5, b.y);
        // dest = (5,5,20,20) ∩ (0,0,10,10) = (5,5,5,5); src shrinks to (0,0,5,5)
        assertRect(new Rect(0, 0, 5, 5), b.src);

        // Nested clip intersects further; clearing restores the full destination.
        g.clipRect(2, 2, 100, 100);
        assertRect(new Rect(2, 2, 8, 8), g.getClipRect());
        g.clearClipRect();
        assertRect(new Rect(0, 0, 200, 200), g.getClipRect());
    }

    @Test
    void translateAndScaleCompose() {
        RecordingImage dest = new RecordingImage(200, 200);
        RecordingImage img = new RecordingImage(20, 10);
        Graphics g = new Graphics(dest);

        g.translate(10, 20);
        assertEquals(10.0f, g.getTransX());
        assertEquals(20.0f, g.getTransY());

        g.setScale(2, 3, 0, 0); // scale origins become trans + 0 = (10, 20)

        g.drawImage(img, 5, 4);

        // theX = 15, theY = 24; dest = (10 + floor((15-10)*2), 20 + floor((24-20)*3), ceil(20*2), ceil(10*3))
        assertEquals(1, dest.stretchBlts.size());
        RecordingImage.Blt b = dest.stretchBlts.get(0);
        assertRect(new Rect(20, 32, 40, 30), b.dest);
        assertRect(new Rect(0, 0, 20, 10), b.src);
        assertFalse(b.fastStretch);
    }

    @Test
    void drawImageRotatedMapsCenterAndSource() {
        RecordingImage dest = new RecordingImage(200, 200);
        RecordingImage img = new RecordingImage(20, 10);
        Graphics g = new Graphics(dest);

        g.drawImageRotated(img, 3, 4, 1.5);

        assertEquals(1, dest.rotatedBlts.size());
        RecordingImage.Blt b = dest.rotatedBlts.get(0);
        assertEquals(3, b.x);
        assertEquals(4, b.y);
        assertRect(new Rect(0, 0, 20, 10), b.src);
        assertEquals(1.5, b.rot);
        assertEquals(10.0f, b.rotCx); // GetWidth()/2
        assertEquals(5.0f, b.rotCy); // GetHeight()/2

        dest.rotatedBlts.clear();
        g.drawImageRotated(img, 3, 4, 0.25, new Rect(2, 2, 8, 6));
        RecordingImage.Blt b2 = dest.rotatedBlts.get(0);
        assertRect(new Rect(2, 2, 8, 6), b2.src);
        assertEquals(4.0f, b2.rotCx); // src width / 2
        assertEquals(3.0f, b2.rotCy); // src height / 2
    }

    @Test
    void colorAndBlendModePropagate() {
        RecordingImage dest = new RecordingImage(200, 200);
        RecordingImage img = new RecordingImage(20, 10);
        Graphics g = new Graphics(dest);

        g.setColor(new Color(10, 20, 30, 40));
        g.setColorizeImages(true);
        g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
        g.drawImage(img, 0, 0);

        RecordingImage.Blt b = dest.blts.get(0);
        assertEquals(10, b.color.mRed);
        assertEquals(20, b.color.mGreen);
        assertEquals(30, b.color.mBlue);
        assertEquals(40, b.color.mAlpha);
        assertEquals(Graphics.DRAWMODE_ADDITIVE, b.drawMode);

        // Without colorize, the tint passed down is white (image alpha still modulates).
        dest.blts.clear();
        g.setColorizeImages(false);
        g.drawImage(img, 0, 0);
        assertEquals(Color.White.toInt(), dest.blts.get(0).color.toInt());
    }

    @Test
    void fillRectHonoursAlphaZeroEarlyOutAndClip() {
        RecordingImage dest = new RecordingImage(200, 200);
        Graphics g = new Graphics(dest);

        g.setColor(new Color(255, 0, 0, 0));
        g.fillRect(0, 0, 10, 10);
        assertTrue(dest.fillRects.isEmpty(), "alpha 0 must not draw");

        g.setColor(new Color(255, 0, 0, 255));
        g.setClipRect(0, 0, 4, 4);
        g.fillRect(2, 2, 10, 10);
        assertRect(new Rect(2, 2, 2, 2), dest.fillRects.get(0).dest);
    }

    @Test
    void pushStateAndPopStateRestoreEverything() {
        RecordingImage dest = new RecordingImage(200, 200);
        Graphics g = new Graphics(dest);

        g.setColor(new Color(1, 2, 3, 4));
        g.translate(5, 6);
        g.setClipRect(0, 0, 10, 10);
        g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);

        g.pushState();
        g.setColor(new Color(9, 9, 9, 9));
        g.translate(100, 100);
        g.clearClipRect();
        g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        g.popState();

        assertEquals(1, g.getColor().mRed);
        assertEquals(5.0f, g.getTransX());
        assertEquals(6.0f, g.getTransY());
        assertEquals(Graphics.DRAWMODE_ADDITIVE, g.getDrawMode());
        // setClipRect translated by (5,6): (5,6,10,10) ∩ dest bounds.
        assertRect(new Rect(5, 6, 10, 10), g.getClipRect());
    }

    @Test
    void createCopiesStateWithoutStack() {
        RecordingImage dest = new RecordingImage(200, 200);
        Graphics g = new Graphics(dest);
        g.setColor(new Color(7, 8, 9, 255));
        g.translate(3, 4);

        Graphics copy = g.create();
        assertEquals(7, copy.getColor().mRed);
        assertEquals(3.0f, copy.getTransX());
        // The copy shares the destination but has its own (empty) state stack.
        assertTrue(copy.mStateStack.isEmpty());
        assertTrue(g.mStateStack.isEmpty());
    }

    @Test
    void drawStringWrappedBreaksAtSpacesAsUpstream() {
        // Monospace FakeFont: each char is 10 wide => "aa aa" in a 25-wide rect wraps after "aa".
        RecordingImage dest = new RecordingImage(200, 200);
        Graphics g = new Graphics(dest);
        FakeFont font = new FakeFont();
        g.setFont(font);

        Graphics.IntRef maxWidth = new Graphics.IntRef();
        int height = g.writeWordWrapped(new Rect(0, 0, 25, 0), "aa aa", -1, -1, maxWidth, -1, null);

        assertEquals(2, font.draws.size(), "two wrapped lines");
        assertEquals("aa", font.draws.get(0).text);
        assertEquals(0, font.draws.get(0).x);
        assertEquals(8, font.draws.get(0).y); // theRect.mY + (ascent - ascentPadding)
        assertEquals("aa", font.draws.get(1).text);
        assertEquals(18, font.draws.get(1).y); // + lineSpacing (10)
        // aYOffset grows to 28, + descent (height-ascent = 2) - lineSpacing (10) = 20
        assertEquals(20, height);
        assertEquals(30, maxWidth.value);
    }

    @Test
    void drawStringColorAppliesHexTags() {
        RecordingImage dest = new RecordingImage(200, 200);
        Graphics g = new Graphics(dest);
        FakeFont font = new FakeFont();
        g.setFont(font);
        g.setColor(new Color(255, 255, 255, 255));

        g.drawStringColor("ab^ff0000^cd", 0, 0);

        // Two drawString calls: "ab" white, then "cd" red.
        assertEquals(2, font.draws.size());
        assertEquals("ab", font.draws.get(0).text);
        assertEquals("cd", font.draws.get(1).text);
    }
}
