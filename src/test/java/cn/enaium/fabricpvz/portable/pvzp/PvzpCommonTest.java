package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Tests for the portable helper layer ({@link PvzpCommon}) and the definition path helpers
 * ({@link Definition}): curve maths, weighted/smooth picks, number rounding and clamping, string
 * replacement, colour maths, matrix helpers and the compiled-cache path rules. Every expected value is
 * computed by hand from the upstream formula.
 */
class PvzpCommonTest {

    /** Records the draw call so the string helpers can be checked (Font is abstract). */
    static final class RecordingFont extends Font {
        int mLastX;
        int mLastY;
        String mLastText;

        RecordingFont() {
            mHeight = 10;
        }

        @Override
        public int stringWidth(String theString) {
            return theString.length() * 7;
        }

        @Override
        public int charWidth(int theChar) {
            return 7;
        }

        @Override
        public int charWidthKern(int theChar, int thePrevChar) {
            return 7;
        }

        @Override
        public void drawString(Graphics g, int theX, int theY, String theString, Color theColor, Rect theClipRect) {
            mLastX = theX;
            mLastY = theY;
            mLastText = theString;
        }

        @Override
        public Font duplicate() {
            return this;
        }
    }

    private String mOriginalAppDataFolder;

    @BeforeEach
    void rememberFolder() {
        mOriginalAppDataFolder = Common.getAppDataFolder();
    }

    @AfterEach
    void restoreFolder() {
        Common.setAppDataFolder(mOriginalAppDataFolder);
    }

    // ------------------------------------------------------------------
    // Curves
    // ------------------------------------------------------------------

    @Test
    void curveFunctionsMatchTheHandComputedValues() {
        assertEquals(0.25f, PvzpCommon.pvzpCurveQuad(0.5f), 1e-6f);
        assertEquals(0.75f, PvzpCommon.pvzpCurveInvQuad(0.5f), 1e-6f);
        assertEquals(0.5f, PvzpCommon.pvzpCurveS(0.5f), 1e-6f);
        assertEquals(0.125f, PvzpCommon.pvzpCurveCubic(0.5f), 1e-6f);
        assertEquals(0.875f, PvzpCommon.pvzpCurveInvCubic(0.5f), 1e-6f);
        assertEquals(0.5f, PvzpCommon.pvzpCurveBounce(0.25f), 1e-6f);
        assertEquals(1.0f, PvzpCommon.pvzpCurveBounce(0.5f), 1e-6f);
        assertEquals(0.125f, PvzpCommon.pvzpCurveQuadS(0.25f), 1e-6f);
        assertEquals(0.375f, PvzpCommon.pvzpCurveInvQuadS(0.25f), 1e-6f);
        assertEquals(0.0625f, PvzpCommon.pvzpCurveCubicS(0.25f), 1e-6f);
        assertEquals(8.0f, PvzpCommon.pvzpCurvePoly(2.0f, 3.0f), 1e-6f);
        assertEquals(2.0f, PvzpCommon.pvzpCurveInvPoly(2.0f, 3.0f), 1e-6f);
        assertEquals(0.125f, PvzpCommon.pvzpCurvePolyS(0.25f, 2.0f), 1e-6f);
        assertEquals(0.0f, PvzpCommon.pvzpCurveCircle(0.0f), 1e-6f);
        assertEquals(1.0f, PvzpCommon.pvzpCurveCircle(1.0f), 1e-6f);
        assertEquals(0.0f, PvzpCommon.pvzpCurveInvCircle(0.0f), 1e-6f);
    }

    @Test
    void curveEvaluateInterpolatesBetweenTheEndpoints() {
        assertEquals(5.0f, PvzpCommon.pvzpCurveEvaluate(0.5f, 0.0f, 10.0f, PvzpCurves.CURVE_LINEAR), 1e-6f);
        assertEquals(0.0f, PvzpCommon.pvzpCurveEvaluate(0.5f, 0.0f, 10.0f, PvzpCurves.CURVE_CONSTANT), 1e-6f);
        assertEquals(2.5f, PvzpCommon.pvzpCurveEvaluate(0.5f, 0.0f, 10.0f, PvzpCurves.CURVE_EASE_IN), 1e-6f);
        assertEquals(10.0f, PvzpCommon.pvzpCurveEvaluate(0.25f, 0.0f, 10.0f, PvzpCurves.CURVE_SIN_WAVE), 1e-5f);
    }

    @Test
    void curveEvaluateClampedPinsTheEndpoints() {
        assertEquals(5.0f, PvzpCommon.pvzpCurveEvaluateClamped(-1.0f, 5.0f, 9.0f, PvzpCurves.CURVE_LINEAR), 1e-6f);
        assertEquals(9.0f, PvzpCommon.pvzpCurveEvaluateClamped(1.5f, 5.0f, 9.0f, PvzpCurves.CURVE_LINEAR), 1e-6f);
        assertEquals(5.0f, PvzpCommon.pvzpCurveEvaluateClamped(1.5f, 5.0f, 9.0f, PvzpCurves.CURVE_BOUNCE), 1e-6f);
        assertEquals(5.0f, PvzpCommon.pvzpCurveEvaluateClamped(1.5f, 5.0f, 9.0f, PvzpCurves.CURVE_SIN_WAVE), 1e-6f);
        assertEquals(5.0f, PvzpCommon.pvzpCurveEvaluateClamped(0.5f, 0.0f, 10.0f, PvzpCurves.CURVE_LINEAR), 1e-6f);
    }

    @Test
    void animateCurveVariantsAgree() {
        assertEquals(25.0f, PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 100.0f, 25.0f, 0.0f, 100.0f, PvzpCurves.CURVE_LINEAR),
                1e-5f);
        assertEquals(25.0f, PvzpCommon.pvzpAnimateCurveFloat(0, 100, 25, 0.0f, 100.0f, PvzpCurves.CURVE_LINEAR), 1e-5f);
        assertEquals(25, PvzpCommon.pvzpAnimateCurve(0, 100, 25, 0, 100, PvzpCurves.CURVE_LINEAR));
        assertEquals(51, PvzpCommon.pvzpAnimateCurve(0, 100, 50, 0, 101, PvzpCurves.CURVE_LINEAR), "50.5 rounds up");
        assertEquals(0, PvzpCommon.pvzpAnimateCurve(0, 100, 0, 0, 100, PvzpCurves.CURVE_LINEAR));
    }

    // ------------------------------------------------------------------
    // Numbers, strings, bits
    // ------------------------------------------------------------------

    @Test
    void numberHelpersMatchUpstreamRoundingAndClamping() {
        assertEquals(3, PvzpCommon.floatRoundToInt(2.5f));
        assertEquals(2, PvzpCommon.floatRoundToInt(2.4f));
        assertEquals(-3, PvzpCommon.floatRoundToInt(-2.5f));
        assertEquals(-2, PvzpCommon.floatRoundToInt(-2.4f));
        assertEquals(0, PvzpCommon.floatRoundToInt(0.0f));
        assertEquals(2.5f, PvzpCommon.floatLerp(0.0f, 10.0f, 0.25f), 1e-6f);
        assertEquals(5.0f, PvzpCommon.distance2D(0.0f, 0.0f, 3.0f, 4.0f), 1e-6f);
        assertTrue(PvzpCommon.floatApproxEqual(1.0f, 1.0f));
        assertFalse(PvzpCommon.floatApproxEqual(1.0f, 1.0001f));
        assertTrue(PvzpCommon.floatApproxEqual(1.0f, 1.0f + 1e-8f));
        assertEquals(0.017453292f, PvzpCommon.DEG_TO_RAD);
        assertEquals(57.29578f, PvzpCommon.RAD_TO_DEG);
    }

    @Test
    void stringHelpersReplaceTheFirstOccurrence() {
        assertEquals("Hello World!", PvzpCommon.pvzpReplaceString("Hello %s!", "%s", "World"));
        assertEquals("abc", PvzpCommon.pvzpReplaceString("abc", "z", "y"), "no match leaves the text alone");
        // NOTE: a "[KEY]" argument goes through PvzpStringFile.translate, which needs an app
        assertEquals("You have 12 coins", PvzpCommon.pvzpReplaceNumberString("You have X coins", "X", 12));
        assertEquals("-7", PvzpCommon.pvzpReplaceNumberString("X", "X", -7));
    }

    @Test
    void bitHelpersMatchTheUpstreamShiftSemantics() {
        int[] aFlags = {0};
        PvzpCommon.setBit(aFlags, 3, true);
        assertEquals(8, aFlags[0]);
        assertTrue(PvzpCommon.testBit(aFlags[0], 3));
        assertFalse(PvzpCommon.testBit(aFlags[0], 2));
        PvzpCommon.setBit(aFlags, 3, false);
        assertEquals(0, aFlags[0]);
        PvzpCommon.setBit(aFlags, 0, true);
        assertEquals(1, aFlags[0], "the default value argument sets the bit");
    }

    @Test
    void flashingColorFollowsTheUpstreamTriangle() {
        assertEquals(255, PvzpCommon.getFlashingColor(0, 40).mRed);
        assertEquals(155, PvzpCommon.getFlashingColor(10, 40).mRed);
        assertEquals(55, PvzpCommon.getFlashingColor(20, 40).mRed);
        assertEquals(65, PvzpCommon.getFlashingColor(21, 40).mRed);
        assertEquals(255, PvzpCommon.getFlashingColor(40, 40).mRed, "the counter wraps");
        Color aFlash = PvzpCommon.getFlashingColor(10, 40);
        assertEquals(aFlash.mRed, aFlash.mGreen);
        assertEquals(aFlash.mRed, aFlash.mBlue);
        assertEquals(255, aFlash.mAlpha);
    }

    @Test
    void colorHelpersClampAndMultiply() {
        assertEquals(new Color(255, 30, 50, 70), PvzpCommon.colorAdd(new Color(200, 10, 20, 30), new Color(100, 20, 30, 40)));
        assertEquals(255, PvzpCommon.colorComponentMultiply(255, 255));
        assertEquals(19, PvzpCommon.colorComponentMultiply(100, 50));
        assertEquals(0, PvzpCommon.colorComponentMultiply(0, 255));
        assertEquals(new Color(19, 19, 255, 0), PvzpCommon.colorsMultiply(new Color(100, 50, 255, 0), new Color(50, 100, 255, 255)));
    }

    // ------------------------------------------------------------------
    // Picks
    // ------------------------------------------------------------------

    @Test
    void picksUseTheWeights() {
        PvzpWeightedArray[] aSingle = {new PvzpWeightedArray(7L, 5)};
        assertEquals(7L, PvzpCommon.pvzpPickFromWeightedArray(aSingle, 1));

        PvzpWeightedArray[] aZeroFirst = {new PvzpWeightedArray(1L, 0), new PvzpWeightedArray(2L, 3)};
        assertEquals(2L, PvzpCommon.pvzpPickFromWeightedArray(aZeroFirst, 2), "a zero weight is never picked");

        PvzpWeightedGridArray[] aGrid = {new PvzpWeightedGridArray(0, 0, 0), new PvzpWeightedGridArray(3, 4, 2)};
        PvzpWeightedGridArray aPicked = PvzpCommon.pvzpPickFromWeightedGridArray(aGrid, 2);
        assertNotNull(aPicked);
        assertEquals(3, aPicked.mX);
        assertEquals(4, aPicked.mY);

        assertEquals(0L, PvzpCommon.pvzpPickFromWeightedArray(aSingle, 0), "an empty list yields the upstream 0");
        assertNull(PvzpCommon.pvzpPickFromWeightedGridArray(aGrid, 0));
    }

    @Test
    void pickFromArrayIsUniformAndBoundsSafe() {
        Integer[] aValues = {7};
        assertEquals(7, PvzpCommon.pvzpPickFromArray(aValues, 1));
        assertNull(PvzpCommon.pvzpPickFromArray(aValues, 0));
    }

    @Test
    void smoothWeightMatchesTheHandComputedFormula() {
        assertEquals(0.0f, PvzpCommon.pvzpCalcSmoothWeight(0.0f, 0.0f, 0.0f));
        // w=0.5: expected lengths 2 and 4, so factor1 = 1 + (-1/2)*2 = 0, factor2 = 1 + (-3/4)*2 = -0.5,
        // final = clamp(0*0.75 + -0.5*0.25, 0.01, 100) = 0.01 -> 0.5 * 0.01
        assertEquals(0.005f, PvzpCommon.pvzpCalcSmoothWeight(0.5f, 0.0f, 0.0f), 1e-6f);
        // w=1: expected lengths 1 and 2, factor1 = 1 + 0 = 1, factor2 = 1 + (-1/2)*2 = 0 -> 0.75
        assertEquals(0.75f, PvzpCommon.pvzpCalcSmoothWeight(1.0f, 0.0f, 0.0f), 1e-6f);
    }

    @Test
    void smoothPickUpdatesTheBookkeeping() {
        PvzpSmoothArray[] anArray = {new PvzpSmoothArray(5, 1.0f), new PvzpSmoothArray(6, 1.0f)};
        anArray[0].mLastPicked = 2.0f;
        anArray[0].mSecondLastPicked = 7.0f;
        anArray[1].mLastPicked = 3.0f;
        anArray[1].mSecondLastPicked = 4.0f;

        PvzpCommon.pvzpUpdateSmoothArrayPick(anArray, 2, 1);

        assertEquals(3.0f, anArray[0].mLastPicked, "the unpicked entry ages");
        assertEquals(8.0f, anArray[0].mSecondLastPicked);
        assertEquals(0.0f, anArray[1].mLastPicked, "the picked entry resets");
        assertEquals(4.0f, anArray[1].mSecondLastPicked, "the age increment lands before the swap");

        PvzpSmoothArray[] aPickable = {new PvzpSmoothArray(5, 1.0f), new PvzpSmoothArray(6, 0.0f)};
        assertEquals(5, PvzpCommon.pvzpPickFromSmoothArray(aPickable, 2), "only the positive weight can win");
        assertEquals(0.0f, aPickable[0].mLastPicked);
    }

    @Test
    void randomRangesStayInsideTheirBoundsAndRepeatWithASeed() {
        Common.sRand(123456L);
        int aFirst = PvzpCommon.randRangeInt(-5, 5);
        float aFirstFloat = PvzpCommon.randRangeFloat(-2.0f, 2.0f);
        Common.sRand(123456L);
        assertEquals(aFirst, PvzpCommon.randRangeInt(-5, 5), "the seeded sequence repeats");
        assertEquals(aFirstFloat, PvzpCommon.randRangeFloat(-2.0f, 2.0f));

        for (int i = 0; i < 200; i++) {
            int anInt = PvzpCommon.randRangeInt(3, 9);
            assertTrue(anInt >= 3 && anInt <= 9, "randRangeInt out of range: " + anInt);
            float aFloat = PvzpCommon.randRangeFloat(3.0f, 9.0f);
            assertTrue(aFloat >= 3.0f && aFloat < 9.0f, "randRangeFloat out of range: " + aFloat);
        }
    }

    // ------------------------------------------------------------------
    // Geometry, matrices, drawing
    // ------------------------------------------------------------------

    @Test
    void pointInPolygonFollowsTheWindingTest() {
        SexyVector2[] aSquare = {
                new SexyVector2(0.0f, 0.0f),
                new SexyVector2(10.0f, 0.0f),
                new SexyVector2(10.0f, 10.0f),
                new SexyVector2(0.0f, 10.0f)
        };
        assertTrue(PvzpCommon.pvzpIsPointInPolygon(aSquare, 4, new SexyVector2(5.0f, 5.0f)));
        assertFalse(PvzpCommon.pvzpIsPointInPolygon(aSquare, 4, new SexyVector2(15.0f, 5.0f)));
        assertFalse(PvzpCommon.pvzpIsPointInPolygon(aSquare, 4, new SexyVector2(5.0f, -1.0f)));
    }

    @Test
    void matrixHelpersMatchTheHandComputedResults() {
        SexyMatrix3 aMatrix = identity();
        PvzpCommon.sexyMatrix3Translation(aMatrix, 3.0f, 4.0f);
        assertEquals(3.0f, aMatrix.m02);
        assertEquals(4.0f, aMatrix.m12);

        PvzpCommon.pvzpScaleTransformMatrix(aMatrix, 5.0f, 6.0f, 2.0f, 3.0f);
        assertEquals(2.0f, aMatrix.m00);
        assertEquals(3.0f, aMatrix.m11);
        assertEquals(5.0f, aMatrix.m02);
        assertEquals(6.0f, aMatrix.m12);
        assertEquals(0.0f, aMatrix.m01);
        assertEquals(1.0f, aMatrix.m22);

        PvzpCommon.pvzpScaleRotateTransformMatrix(aMatrix, 1.0f, 2.0f, (float) (Math.PI / 2), 1.0f, 1.0f);
        assertEquals(0.0f, aMatrix.m00, 1e-6f);
        assertEquals(-1.0f, aMatrix.m10, 1e-6f);
        assertEquals(1.0f, aMatrix.m01, 1e-6f);
        assertEquals(0.0f, aMatrix.m11, 1e-6f);
        assertEquals(1.0f, aMatrix.m02);
        assertEquals(2.0f, aMatrix.m12);

        float[] aScale = new float[2];
        PvzpCommon.pvzpScaleTransformMatrix(aMatrix, 0.0f, 0.0f, 2.0f, 3.0f);
        PvzpCommon.sexyMatrix3ExtractScale(aMatrix, aScale);
        assertEquals(2.0f, aScale[0], 1e-5f);
        assertEquals(3.0f, aScale[1], 1e-5f);

        SexyMatrix3 aScaleMatrix = identity();
        PvzpCommon.pvzpScaleTransformMatrix(aScaleMatrix, 0.0f, 0.0f, 2.0f, 4.0f);
        SexyMatrix3 anInverse = identity();
        PvzpCommon.sexyMatrix3Inverse(aScaleMatrix, anInverse);
        assertEquals(0.5f, anInverse.m00, 1e-6f);
        assertEquals(0.25f, anInverse.m11, 1e-6f);
        assertEquals(1.0f, anInverse.m22, 1e-6f);

        SexyMatrix3 aProduct = identity();
        PvzpCommon.sexyMatrix3Multiply(aProduct, aScaleMatrix, aScaleMatrix);
        assertEquals(4.0f, aProduct.m00, 1e-6f);
        assertEquals(16.0f, aProduct.m11, 1e-6f);

        SexyMatrix3 aTransposed = identity();
        PvzpCommon.sexyMatrix3Transpose(aMatrix, aTransposed);
        assertEquals(aMatrix.m00, aTransposed.m00);
        assertEquals(aMatrix.m01, aTransposed.m10);
        assertEquals(aMatrix.m10, aTransposed.m01);
        assertEquals(aMatrix.m22, aTransposed.m22);
    }

    @Test
    void drawStringHelpersApplyTheJustification() {
        Graphics aGraphics = new Graphics(new Image());
        RecordingFont aFont = new RecordingFont();

        PvzpCommon.pvzpDrawString(aGraphics, "Hi", 100, 5, aFont, Color.White, DrawStringJustification.DS_ALIGN_LEFT);
        assertEquals(100, aFont.mLastX);
        assertEquals(5, aFont.mLastY);
        assertEquals("Hi", aFont.mLastText);

        PvzpCommon.pvzpDrawString(aGraphics, "Hi", 100, 5, aFont, Color.White, DrawStringJustification.DS_ALIGN_RIGHT);
        assertEquals(86, aFont.mLastX, "right alignment subtracts the string width (2 * 7)");

        PvzpCommon.pvzpDrawString(aGraphics, "Hi", 100, 5, aFont, Color.White, DrawStringJustification.DS_ALIGN_CENTER);
        assertEquals(93, aFont.mLastX, "centred alignment subtracts half the string width");

        PvzpCommon.pvzpDrawString(aGraphics, "Hi", 100, 5, aFont, Color.White,
                DrawStringJustification.DS_ALIGN_LEFT_VERTICAL_MIDDLE);
        assertEquals(100, aFont.mLastX);
    }

    @Test
    void imageDrawHelpersUseTheCelGeometry() {
        Graphics aGraphics = new Graphics(new Image());
        Image aStrip = new Image();
        aStrip.mWidth = 64;
        aStrip.mHeight = 32;
        aStrip.mNumCols = 2;
        aStrip.mNumRows = 1;
        assertEquals(32, aStrip.getCelWidth());
        assertEquals(32, aStrip.getCelHeight());

        // the helpers only compute the source rect and delegate to the Image blit; the base Image is a
        // no-op backend, so this pins the argument handling (and the no-throw contract) for real ones
        PvzpCommon.pvzpDrawImageCelF(aGraphics, aStrip, 1.0f, 2.0f, 1, 0);
        PvzpCommon.pvzpDrawImageCelScaled(aGraphics, aStrip, 1, 2, 1, 0, 2.0f, 2.0f);
        PvzpCommon.pvzpDrawImageCelScaledF(aGraphics, aStrip, 1.0f, 2.0f, 1, 0, 2.0f, 2.0f);
        PvzpCommon.pvzpDrawImageCelCenterScaledF(aGraphics, aStrip, 1.0f, 2.0f, 1, 2.0f, 2.0f);
        PvzpCommon.pvzpDrawImageScaledF(aGraphics, aStrip, 1.0f, 2.0f, 1.0f, 1.0f);
        PvzpCommon.pvzpDrawImageCenterScaledF(aGraphics, aStrip, 1.0f, 2.0f, 2.0f, 2.0f);
        PvzpCommon.pvzpBltMatrix(aGraphics, aStrip, identity(), aGraphics.mClipRect, Color.White,
                Graphics.DRAWMODE_NORMAL, new Rect(0, 0, 32, 32));
        PvzpCommon.pvzpDrawStringMatrix(aGraphics, new RecordingFont(), identity(), "Hi", Color.White);
    }

    @Test
    void sandingHelpersIgnoreNonMemoryImages() {
        Image anImage = new Image();
        PvzpCommon.pvzpMarkImageForSanding(anImage);
        PvzpCommon.pvzpSandImageIfNeeded(anImage);
        PvzpCommon.fixPixelsOnAlphaEdgeForBlending(anImage);
        assertEquals(0, anImage.mWidth, "a non-MemoryImage is left alone");
    }

    // ------------------------------------------------------------------
    // Definition path helpers
    // ------------------------------------------------------------------

    @Test
    void compiledCachePathsFollowTheUpstreamRules(@TempDir Path theDir) {
        assertEquals("compiled/particles/Award.xml.compiled",
                Definition.getCompiledFilePathFromXMLFilePath("particles/Award.xml"));

        Common.setAppDataFolder(theDir.toString());
        assertEquals(Path.of(theDir.toString(), "cache64", "compiled", "particles", "Award.xml.compiled").normalize(),
                Path.of(Definition.getCompiledCacheFullPath("compiled/particles/Award.xml.compiled")).normalize());
        assertEquals("cache64/", Definition.COMPILED_CACHE_ROOT);
        assertEquals(0xDEADFED4, Definition.COMPRESSION_COOKIE);
    }

    @Test
    void symbolTablesKeepTheUpstreamValues() {
        int[] aValue = new int[1];
        assertTrue(DefSymbol.symbolValueFromString(Definition.gParticleFlagSymbols, "additive", aValue));
        assertEquals(8, aValue[0], "ParticleFlags Additive is bit 8");
        assertTrue(DefSymbol.symbolValueFromString(Definition.gEmitterTypeSymbols, "CircleEvenSpacing", aValue));
        assertEquals(4, aValue[0]);
        assertTrue(DefSymbol.symbolValueFromString(Definition.gParticleTypeSymbols, "GroundConstraint", aValue));
        assertEquals(8, aValue[0]);
        assertTrue(DefSymbol.symbolValueFromString(Definition.gTrailFlagDefSymbols, "Loops", aValue));
        assertEquals(0, aValue[0]);
        assertFalse(DefSymbol.symbolValueFromString(Definition.gTrailFlagDefSymbols, "Nope", aValue));
        assertTrue(DefSymbol.symbolValueFromString(Definition.gDefTrackEaseSymbols, "EaseIn", aValue));
        assertEquals(PvzpCurves.CURVE_EASE_IN, aValue[0]);
        assertEquals(4, Definition.gDefLoadResPaths.length);
        assertEquals("IMAGE_REANIM_", Definition.gDefLoadResPaths[2].mPrefix);
        assertEquals("reanim/", Definition.gDefLoadResPaths[2].mDirectory);
    }

    private static SexyMatrix3 identity() {
        SexyMatrix3 aMatrix = new SexyMatrix3();
        aMatrix.m00 = 1.0f;
        aMatrix.m11 = 1.0f;
        aMatrix.m22 = 1.0f;
        return aMatrix;
    }
}
