package cn.enaium.fabricpvz.portable.sexy.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;

/**
 * Tests for {@link Image} (ported from src/SexyAppFramework/graphics/Image.h:74 and
 * Image.cpp:31-262). Expected rectangles are hand-derived from the upstream formulas:
 *
 * <pre>
 * GetCelWidth()  = mWidth  / mNumCols
 * GetCelHeight() = mHeight / mNumRows
 * GetCelRect(cel)      = Rect((cel % mNumCols) * w, (cel / mNumCols) * h, w, h)
 * GetCelRect(col, row) = Rect(col * w, row * h, w, h)
 * GetAnimCelRect(t): cel = GetAnimCel(t);
 *   if mNumCols &gt; 1: Rect(cel * celW, 0, celW, mHeight)
 *   else:            Rect(0, cel * celH, mWidth, celH)
 * DrawRect(r): FillRect(r.mX,      r.mY,      r.mWidth + 1, 1)
 *              FillRect(r.mX,      r.mY + r.mHeight, r.mWidth + 1, 1)
 *              FillRect(r.mX,      r.mY + 1,  1, r.mHeight - 1)
 *              FillRect(r.mX + r.mWidth, r.mY + 1, 1, r.mHeight - 1)
 * </pre>
 */
class ImageTest {

    /** 1. Constructor defaults. */
    @Test
    void constructorDefaults() {
        Image image = new Image();

        assertEquals(0, image.mWidth);
        assertEquals(0, image.mHeight);
        assertEquals(1, image.mNumRows);
        assertEquals(1, image.mNumCols);
        assertFalse(image.mDrawn);
        assertNull(image.mAnimInfo);
    }

    /** 2. A single 1x1 cel. */
    @Test
    void singleCelGeometry() {
        Image image = new Image();
        image.mWidth = 1;
        image.mHeight = 1;

        assertEquals(1, image.getCelWidth());   // 1 / 1
        assertEquals(1, image.getCelHeight());  // 1 / 1
        // cel 0: (0 % 1) * 1, (0 / 1) * 1, 1, 1
        assertEquals(new Rect(0, 0, 1, 1), image.getCelRect(0));
        assertEquals(new Rect(0, 0, 1, 1), image.getCelRect(0, 0));
    }

    /** 3. A 4x1 horizontal strip. */
    @Test
    void horizontalStripGeometry() {
        Image image = new Image();
        image.mWidth = 16;
        image.mHeight = 8;
        image.mNumCols = 4;
        image.mNumRows = 1;

        assertEquals(4, image.getCelWidth());  // 16 / 4
        assertEquals(8, image.getCelHeight()); // 8 / 1
        // cel 0: (0 % 4)*4 = 0,  (0 / 4)*8 = 0
        assertEquals(new Rect(0, 0, 4, 8), image.getCelRect(0));
        // cel 3: (3 % 4)*4 = 12, (3 / 4)*8 = 0
        assertEquals(new Rect(12, 0, 4, 8), image.getCelRect(3));
        // col 2, row 0: 2*4 = 8, 0*8 = 0
        assertEquals(new Rect(8, 0, 4, 8), image.getCelRect(2, 0));
    }

    /** 4. A 2x3 grid addressing cels both ways. */
    @Test
    void gridGeometry() {
        Image image = new Image();
        image.mWidth = 10;
        image.mHeight = 9;
        image.mNumCols = 2;
        image.mNumRows = 3;

        assertEquals(5, image.getCelWidth());  // 10 / 2
        assertEquals(3, image.getCelHeight()); // 9 / 3

        // getCelRect(cel) = ((cel % 2)*5, (cel / 2)*3, 5, 3)
        Rect[] expected = {
            new Rect(0, 0, 5, 3), // cel 0: (0,0)
            new Rect(5, 0, 5, 3), // cel 1: (1,0)
            new Rect(0, 3, 5, 3), // cel 2: (0,1)
            new Rect(5, 3, 5, 3), // cel 3: (1,1)
            new Rect(0, 6, 5, 3), // cel 4: (0,2)
            new Rect(5, 6, 5, 3), // cel 5: (1,2)
        };
        for (int cel = 0; cel < 6; cel++) {
            assertEquals(expected[cel], image.getCelRect(cel), "getCelRect(" + cel + ")");
        }
        // getCelRect(1, 2) = (1*5, 2*3, 5, 3) = (5, 6, 5, 3)
        assertEquals(new Rect(5, 6, 5, 3), image.getCelRect(1, 2));
    }

    /** 5a. getAnimCel uses the AnimInfo; getAnimCelRect uses the branch dictated by mNumCols. */
    @Test
    void animCelAndRectForStrip() {
        Image image = new Image();
        image.mWidth = 16;
        image.mHeight = 8;
        image.mNumCols = 4;
        image.mNumRows = 1;

        AnimInfo anim = new AnimInfo();
        anim.mAnimType = AnimType.AnimType_Loop;
        anim.mFrameDelay = 5;
        anim.compute(4); // mTotalAnimTime = 5 * 4 = 20, mNumCels = 4
        image.mAnimInfo = anim;

        // getCel(t) = (t / 5) % 4
        for (int t = 0; t <= 4; t++) {
            assertEquals(0, image.getAnimCel(t), "getAnimCel(" + t + ")");
        }
        for (int t = 5; t <= 9; t++) {
            assertEquals(1, image.getAnimCel(t), "getAnimCel(" + t + ")");
        }
        for (int t = 10; t <= 14; t++) {
            assertEquals(2, image.getAnimCel(t), "getAnimCel(" + t + ")");
        }
        for (int t = 15; t <= 19; t++) {
            assertEquals(3, image.getAnimCel(t), "getAnimCel(" + t + ")");
        }
        assertEquals(0, image.getAnimCel(20)); // 20 % 20 = 0

        // mNumCols > 1: Rect(cel*celW, 0, celW, mHeight), celW = 16/4 = 4
        assertEquals(new Rect(0, 0, 4, 8), image.getAnimCelRect(0));  // cel 0
        assertEquals(new Rect(4, 0, 4, 8), image.getAnimCelRect(5));  // cel 1
        assertEquals(new Rect(8, 0, 4, 8), image.getAnimCelRect(10)); // cel 2
        assertEquals(new Rect(12, 0, 4, 8), image.getAnimCelRect(15)); // cel 3
        assertEquals(new Rect(0, 0, 4, 8), image.getAnimCelRect(20)); // wrap
    }

    /** 5b. A 1x4 vertical strip takes the else branch of getAnimCelRect. */
    @Test
    void animCelRectForVerticalStrip() {
        Image image = new Image();
        image.mWidth = 8;
        image.mHeight = 32;
        image.mNumCols = 1;
        image.mNumRows = 4;

        AnimInfo anim = new AnimInfo();
        anim.mAnimType = AnimType.AnimType_Loop;
        anim.mFrameDelay = 5;
        anim.compute(4); // mTotalAnimTime = 20
        image.mAnimInfo = anim;

        // celW = 8/1 = 8, celH = 32/4 = 8
        // mNumCols == 1: Rect(0, cel*celH, mWidth, celH) = (0, cel*8, 8, 8)
        assertEquals(new Rect(0, 0, 8, 8), image.getAnimCelRect(0));  // cel 0
        assertEquals(new Rect(0, 8, 8, 8), image.getAnimCelRect(5));  // cel 1
        assertEquals(new Rect(0, 16, 8, 8), image.getAnimCelRect(10)); // cel 2
        assertEquals(new Rect(0, 24, 8, 8), image.getAnimCelRect(15)); // cel 3
    }

    /** 5c. With no AnimInfo, getAnimCel is 0 and getAnimCelRect uses cel 0. */
    @Test
    void animCelWithoutAnimInfo() {
        Image image = new Image();
        image.mWidth = 8;
        image.mHeight = 32;
        image.mNumCols = 1;
        image.mNumRows = 4;

        assertEquals(0, image.getAnimCel(123));
        // cel 0, mNumCols == 1 -> Rect(0, 0, mWidth, celH) = (0, 0, 8, 8)
        assertEquals(new Rect(0, 0, 8, 8), image.getAnimCelRect(123));
    }

    /** 6. copyAttributes copies the grid and deep-clones the AnimInfo. */
    @Test
    void copyAttributesClonesAnimInfo() {
        Image source = new Image();
        source.mNumCols = 4;
        source.mNumRows = 2;
        AnimInfo sourceAnim = new AnimInfo();
        sourceAnim.mFrameDelay = 5;
        sourceAnim.compute(4);
        source.mAnimInfo = sourceAnim;

        Image target = new Image();
        target.copyAttributes(source);

        assertEquals(4, target.mNumCols);
        assertEquals(2, target.mNumRows);
        assertNotNull(target.mAnimInfo);
        assertEquals(5, target.mAnimInfo.mFrameDelay);
        assertEquals(4, target.mAnimInfo.mNumCels);

        // Mutating the source AnimInfo must not affect the target's clone.
        sourceAnim.mFrameDelay = 99;
        sourceAnim.mNumCels = 7;
        assertEquals(5, target.mAnimInfo.mFrameDelay);
        assertEquals(4, target.mAnimInfo.mNumCels);
    }

    /** 6b. copyAttributes with a null source AnimInfo clears the target's. */
    @Test
    void copyAttributesWithNullAnimInfo() {
        Image source = new Image();
        source.mNumCols = 3;
        source.mNumRows = 1;
        source.mAnimInfo = null;

        Image target = new Image();
        target.mAnimInfo = new AnimInfo();
        target.copyAttributes(source);

        assertEquals(3, target.mNumCols);
        assertEquals(1, target.mNumRows);
        assertNull(target.mAnimInfo);
    }

    /** 7. Copy constructor copies geometry, clones AnimInfo, resets mDrawn, drops mFilePath. */
    @Test
    void copyConstructor() {
        Image source = new Image();
        source.mWidth = 16;
        source.mHeight = 8;
        source.mNumCols = 4;
        source.mNumRows = 1;
        source.mFilePath = "some/path.png";
        source.mDrawn = true;
        AnimInfo sourceAnim = new AnimInfo();
        sourceAnim.mFrameDelay = 5;
        sourceAnim.compute(4);
        source.mAnimInfo = sourceAnim;

        Image copy = new Image(source);

        assertEquals(16, copy.mWidth);
        assertEquals(8, copy.mHeight);
        assertEquals(4, copy.mNumCols);
        assertEquals(1, copy.mNumRows);
        assertFalse(copy.mDrawn);          // reset, not copied
        assertNull(copy.mFilePath);        // not copied (Image.cpp:41-51)
        assertNotNull(copy.mAnimInfo);
        assertEquals(5, copy.mAnimInfo.mFrameDelay);

        // AnimInfo is a clone: mutating the source's must not affect the copy.
        sourceAnim.mFrameDelay = 99;
        assertEquals(5, copy.mAnimInfo.mFrameDelay);
    }

    /** 8a. Every base-class draw entry point is a no-op and returns without throwing. */
    @Test
    void baseVirtualsAreNoOps() {
        Image image = new Image();
        Rect rect = new Rect(1, 2, 3, 4);
        Color color = Color.White;
        Span[] spans = {new Span(1, 2, 3)};
        byte[] coverage = new byte[4];
        TriVertex[][] triangles = {
            {new TriVertex(), new TriVertex(), new TriVertex()}
        };

        image.fillRect(rect, color, Graphics.DRAWMODE_NORMAL);
        image.drawRect(rect, color, Graphics.DRAWMODE_NORMAL);
        image.clearRect(rect);
        image.drawLine(0, 0, 1, 1, color, Graphics.DRAWMODE_NORMAL);
        image.drawLineAA(0, 0, 1, 1, color, Graphics.DRAWMODE_NORMAL);
        image.fillScanLines(spans, 1, color, Graphics.DRAWMODE_NORMAL);
        image.fillScanLinesWithCoverage(spans, 1, color, Graphics.DRAWMODE_NORMAL,
                coverage, 0, 0, 2, 2);
        image.blt(new Image(), 0, 0, rect, color, Graphics.DRAWMODE_NORMAL, true);
        image.bltF(new Image(), 0f, 0f, rect, rect, color, Graphics.DRAWMODE_NORMAL);
        image.bltRotated(new Image(), 0f, 0f, rect, rect, color, Graphics.DRAWMODE_NORMAL,
                0.0, 0f, 0f);
        image.stretchBlt(new Image(), rect, rect, rect, color, Graphics.DRAWMODE_NORMAL, false);
        image.bltMatrix(new Image(), 0f, 0f, new SexyMatrix3(), rect, color,
                Graphics.DRAWMODE_NORMAL, rect, false);
        image.bltTrianglesTex(new Image(), triangles, 1, rect, color,
                Graphics.DRAWMODE_NORMAL, 0f, 0f, false);
        image.bltMirror(new Image(), 0, 0, rect, color, Graphics.DRAWMODE_NORMAL, true);
        image.stretchBltMirror(new Image(), rect, rect, rect, color,
                Graphics.DRAWMODE_NORMAL, false);

        // Nothing observable changed on the plain base image.
        assertEquals(0, image.mWidth);
        assertEquals(0, image.mHeight);
        assertEquals(1, image.mNumCols);
        assertEquals(1, image.mNumRows);
        assertFalse(image.mDrawn);
        assertNull(image.mAnimInfo);
    }

    /** 8b. PolyFill3D returns false and is3D() returns false on the base image. */
    @Test
    void basePolyFill3DAndIs3D() {
        Image image = new Image();
        Point[] vertices = {new Point(0, 0), new Point(1, 0), new Point(0, 1)};

        assertFalse(image.polyFill3D(vertices, 3, new Rect(0, 0, 4, 4),
                Color.White, Graphics.DRAWMODE_NORMAL, 0, 0));
        assertFalse(image.is3D());
    }

    /** 8c. drawRect issues exactly the four upstream border rectangles via fillRect. */
    @Test
    void drawRectIssuesFourBorderRects() {
        List<Rect> recorded = new ArrayList<>();
        Image recorder = new Image() {
            @Override
            public void fillRect(Rect theRect, Color theColor, int theDrawMode) {
                recorded.add(theRect);
            }
        };

        int w = 10;
        int h = 20;
        recorder.drawRect(new Rect(0, 0, w, h), Color.White, Graphics.DRAWMODE_NORMAL);

        List<Rect> expected = List.of(
                new Rect(0, 0, w + 1, 1),       // top
                new Rect(0, h, w + 1, 1),       // bottom
                new Rect(0, 1, 1, h - 1),       // left
                new Rect(w, 1, 1, h - 1));      // right
        assertEquals(expected, recorded);
    }
}
