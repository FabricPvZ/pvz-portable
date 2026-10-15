/*
 * Ported from src/PvzpLib/PvzpCommon.h:38-180 and src/PvzpLib/PvzpCommon.cpp:1-1244 (the portable
 * helper layer of PvzpLib).
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.util.List;

import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.ActiveFontLayer;
import cn.enaium.fabricpvz.portable.sexy.graphics.CharData;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.FontLayer;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.ImageFont;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.RenderCommand;

/**
 * Ported from src/PvzpLib/PvzpCommon.h:38-180 and src/PvzpLib/PvzpCommon.cpp:1-1244.
 *
 * <p>The portable helper layer: curve maths, weighted/smooth random picks, matrix helpers, the
 * string/image draw helpers the game layer calls, colour helpers, string replacement and the image
 * "sanding" path. Upstream also holds the resource-manager subclass
 * ({@code PvzpResourceManager}, PvzpCommon.cpp:1006-1180) and the {@code Rand}-based helpers; the
 * resource subclass plus {@code PvzpLoadResources}/{@code PvzpLoadNextResource}/
 * {@code PvzpAddImageToMap}/{@code PvzpFindImagePath}/{@code PvzpFindFontPath} already live in
 * {@link PvzpResourceManager}, so they are not duplicated here.
 *
 * <p>Java deviations:
 * <ul>
 *   <li>{@code Pvzp_SWTri_AddAllDrawTriFuncs} (PvzpCommon.cpp:41-113) has no counterpart: it
 *       registers the software triangle rasteriser table, and this port renders triangles through
 *       {@code RendererBackend} instead.</li>
 *   <li>{@code FindGlobalAllocator}/{@code FreeGlobalAllocators} (PvzpCommon.cpp:1176-1201) are
 *       one-line delegates to {@link PvzpAllocator#findGlobalAllocator(int)}/
 *       {@link PvzpAllocator#freeGlobalAllocators()}, which own the upstream global allocator table
 *       ({@code gGlobalAllocators}, PvzpList.h:20-49).</li>
 *   <li>Raw pointer parameters become arrays or values ({@code uint* thePixel} becomes an {@code x,y}
 *       cell, {@code uint&} becomes an {@code int[]} holder), and {@code size_t} counters stay
 *       {@code int}/{@code long}.</li>
 * </ul>
 */
public final class PvzpCommon {
    /** Ported from src/PvzpLib/PvzpCommon.h:42 ({@code DEG_TO_RAD} template). */
    public static final float DEG_TO_RAD = 0.017453292f;

    /** Ported from src/PvzpLib/PvzpCommon.h:43 ({@code RAD_TO_DEG} template). */
    public static final float RAD_TO_DEG = 57.29578f;

    /** Upstream {@code RENDERIMAGEFLAG_SANDING} (PvzpCommon.h:45); see {@link PvzpResourceManager}. */
    public static final int RENDERIMAGEFLAG_SANDING = PvzpResourceManager.RENDERIMAGEFLAG_SANDING;

    private PvzpCommon() {
    }

    // ------------------------------------------------------------------
    // Picks (PvzpCommon.h:52-58, PvzpCommon.cpp:116-238)
    // ------------------------------------------------------------------

    /**
     * Ported from src/PvzpLib/PvzpCommon.h:52-58 ({@code PvzpPickFromArray}): a uniform pick. The
     * upstream {@code T{}} fallback for a non-positive count becomes {@code null}.
     */
    public static <T> T pvzpPickFromArray(T[] theArray, int theCount) {
        if (theCount <= 0) {
            return null;
        }
        return theArray[Common.rand(theCount)];
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:116-119 ({@code PvzpPickFromWeightedArray}). */
    public static long pvzpPickFromWeightedArray(PvzpWeightedArray[] theArray, int theCount) {
        PvzpWeightedArray aPicked = pvzpPickArrayItemFromWeightedArray(theArray, theCount);
        return aPicked == null ? 0L : aPicked.mItem;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:121-146 ({@code PvzpPickArrayItemFromWeightedArray}). */
    public static PvzpWeightedArray pvzpPickArrayItemFromWeightedArray(PvzpWeightedArray[] theArray, int theCount) {
        if (theCount <= 0) {
            return null;
        }

        int aTotalWeight = 0;
        for (int i = 0; i < theCount; i++) {
            aTotalWeight += theArray[i].mWeight;
        }

        aTotalWeight = Common.rand(aTotalWeight);

        for (int i = 0; i < theCount; i++) {
            aTotalWeight -= theArray[i].mWeight;
            if (aTotalWeight < 0) {
                return theArray[i];
            }
        }

        return null;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:148-173 ({@code PvzpPickFromWeightedGridArray}). */
    public static PvzpWeightedGridArray pvzpPickFromWeightedGridArray(PvzpWeightedGridArray[] theArray, int theCount) {
        if (theCount <= 0) {
            return null;
        }

        int aTotalWeight = 0;
        for (int i = 0; i < theCount; i++) {
            aTotalWeight += theArray[i].mWeight;
        }

        aTotalWeight = Common.rand(aTotalWeight);

        for (int i = 0; i < theCount; i++) {
            aTotalWeight -= theArray[i].mWeight;
            if (aTotalWeight < 0) {
                return theArray[i];
            }
        }

        return null;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:175-190 ({@code PvzpCalcSmoothWeight}). */
    public static float pvzpCalcSmoothWeight(float aWeight, float aLastPicked, float aSecondLastPicked) {
        if (aWeight < 1E-6f) {
            return 0.0f;
        }

        float aExpectedLength1 = 1.0f / aWeight;
        float aExpectedLength2 = aExpectedLength1 * 2.0f;
        float aAdvancedLength1 = aLastPicked + 1.0f - aExpectedLength1;
        float aAdvancedLength2 = aSecondLastPicked + 1.0f - aExpectedLength2;
        float aFactor1 = 1.0f + aAdvancedLength1 / aExpectedLength1 * 2.0f;
        float aFactor2 = 1.0f + aAdvancedLength2 / aExpectedLength2 * 2.0f;
        float aFactorFinal = Math.clamp(aFactor1 * 0.75f + aFactor2 * 0.25f, 0.01f, 100.0f);
        return aWeight * aFactorFinal;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:192-223 ({@code PvzpPickFromSmoothArray}). */
    public static int pvzpPickFromSmoothArray(PvzpSmoothArray[] theArray, int theCount) {
        float aTotalWeight = 0.0f;
        for (int i = 0; i < theCount; i++) {
            aTotalWeight += theArray[i].mWeight;
        }

        float aNormalizeFactor = 1.0f / aTotalWeight;
        float aTotalAdjustedWeight = 0.0f;
        for (int j = 0; j < theCount; j++) {
            aTotalAdjustedWeight += pvzpCalcSmoothWeight(theArray[j].mWeight * aNormalizeFactor, theArray[j].mLastPicked,
                    theArray[j].mSecondLastPicked);
        }

        float aRandWeight = Common.rand(aTotalAdjustedWeight);
        float aAccumulatedWeight = 0.0f;
        int k;
        for (k = 0; k < theCount - 1; k++) {
            aAccumulatedWeight += pvzpCalcSmoothWeight(theArray[k].mWeight * aNormalizeFactor, theArray[k].mLastPicked,
                    theArray[k].mSecondLastPicked);
            if (aRandWeight <= aAccumulatedWeight) {
                break;
            }
        }

        pvzpUpdateSmoothArrayPick(theArray, theCount, k);
        return theArray[k].mItem;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:225-238 ({@code PvzpUpdateSmoothArrayPick}). */
    public static void pvzpUpdateSmoothArrayPick(PvzpSmoothArray[] theArray, int theCount, int thePickIndex) {
        for (int i = 0; i < theCount; i++) {
            if (theArray[i].mWeight > 0.0f) {
                theArray[i].mLastPicked += 1.0f;
                theArray[i].mSecondLastPicked += 1.0f;
            }
        }

        theArray[thePickIndex].mSecondLastPicked = theArray[thePickIndex].mLastPicked;
        theArray[thePickIndex].mLastPicked = 0.0f;
    }

    // ------------------------------------------------------------------
    // Curves (PvzpCommon.cpp:240-379)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:240-243 ({@code PvzpCurveQuad}). */
    public static float pvzpCurveQuad(float theTime) {
        return theTime * theTime;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:245-248 ({@code PvzpCurveInvQuad}). */
    public static float pvzpCurveInvQuad(float theTime) {
        return 2 * theTime - theTime * theTime;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:250-253 ({@code PvzpCurveS}). */
    public static float pvzpCurveS(float theTime) {
        return 3 * theTime * theTime - 2 * theTime * theTime * theTime;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:255-264 ({@code PvzpCurveInvQuadS}). */
    public static float pvzpCurveInvQuadS(float theTime) {
        if (theTime <= 0.5f) {
            return pvzpCurveInvQuad(theTime * 2.0f) * 0.5f;
        }
        return pvzpCurveQuad((theTime - 0.5f) * 2.0f) * 0.5f + 0.5f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:266-269 ({@code PvzpCurveBounce}). */
    public static float pvzpCurveBounce(float theTime) {
        return 1 - Math.abs(2 * theTime - 1);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:271-278 ({@code PvzpCurveQuadS}). */
    public static float pvzpCurveQuadS(float theTime) {
        if (theTime <= 0.5f) {
            return pvzpCurveQuad(theTime * 2.0f) * 0.5f;
        }
        return pvzpCurveInvQuad((theTime - 0.5f) * 2.0f) * 0.5f + 0.5f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:280-283 ({@code PvzpCurveCubic}). */
    public static float pvzpCurveCubic(float theTime) {
        return theTime * theTime * theTime;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:285-288 ({@code PvzpCurveInvCubic}). */
    public static float pvzpCurveInvCubic(float theTime) {
        return (theTime - 1.0f) * (theTime - 1.0f) * (theTime - 1.0f) + 1.0f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:290-297 ({@code PvzpCurveCubicS}). */
    public static float pvzpCurveCubicS(float theTime) {
        if (theTime <= 0.5f) {
            return pvzpCurveCubic(theTime * 2.0f) * 0.5f;
        }
        return pvzpCurveInvCubic((theTime - 0.5f) * 2.0f) * 0.5f + 0.5f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:299-302 ({@code PvzpCurvePoly}, {@code pow()}). */
    public static float pvzpCurvePoly(float theTime, float thePoly) {
        return (float) Math.pow(theTime, thePoly);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:304-307 ({@code PvzpCurveInvPoly}). */
    public static float pvzpCurveInvPoly(float theTime, float thePoly) {
        return (float) Math.pow(theTime - 1.0f, thePoly) + 1.0f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:309-316 ({@code PvzpCurvePolyS}). */
    public static float pvzpCurvePolyS(float theTime, float thePoly) {
        if (theTime <= 0.5f) {
            return pvzpCurvePoly(theTime * 2.0f, thePoly) * 0.5f;
        }
        return pvzpCurveInvPoly((theTime - 0.5f) * 2.0f, thePoly) * 0.5f + 0.5f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:318-325 ({@code PvzpCurveCircle}). */
    public static float pvzpCurveCircle(float theTime) {
        if (theTime > 1 - 1E-6f) {
            return 1.0f;
        }
        return 1.0f - (float) Math.sqrt(1.0f - theTime * theTime);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:327-334 ({@code PvzpCurveInvCircle}). */
    public static float pvzpCurveInvCircle(float theTime) {
        if (theTime < 1E-6f) {
            return 0.0f;
        }
        return (float) Math.sqrt(1.0f - (theTime - 1.0f) * (theTime - 1.0f));
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.h:129-150 ({@code PvzpCurveEvaluate}). The upstream
     * {@code PVZP_ASSERT(false)} default keeps {@code aWarpedTime} at 0, which the Java port does too
     * (no debug/release switch to trap with).
     */
    public static float pvzpCurveEvaluate(float theTime, float thePositionStart, float thePositionEnd, int theCurve) {
        float aWarpedTime = 0;
        switch (theCurve) {
            case PvzpCurves.CURVE_CONSTANT:
                aWarpedTime = 0;
                break;
            case PvzpCurves.CURVE_LINEAR:
                aWarpedTime = theTime;
                break;
            case PvzpCurves.CURVE_EASE_IN:
                aWarpedTime = pvzpCurveQuad(theTime);
                break;
            case PvzpCurves.CURVE_EASE_OUT:
                aWarpedTime = pvzpCurveInvQuad(theTime);
                break;
            case PvzpCurves.CURVE_EASE_IN_OUT:
                aWarpedTime = pvzpCurveS(pvzpCurveS(theTime));
                break;
            case PvzpCurves.CURVE_EASE_IN_OUT_WEAK:
                aWarpedTime = pvzpCurveS(theTime);
                break;
            case PvzpCurves.CURVE_FAST_IN_OUT:
                aWarpedTime = pvzpCurveInvQuadS(pvzpCurveInvQuadS(theTime));
                break;
            case PvzpCurves.CURVE_FAST_IN_OUT_WEAK:
                aWarpedTime = pvzpCurveInvQuadS(theTime);
                break;
            case PvzpCurves.CURVE_BOUNCE:
                aWarpedTime = pvzpCurveBounce(theTime);
                break;
            case PvzpCurves.CURVE_BOUNCE_FAST_MIDDLE:
                aWarpedTime = pvzpCurveQuad(pvzpCurveBounce(theTime));
                break;
            case PvzpCurves.CURVE_BOUNCE_SLOW_MIDDLE:
                aWarpedTime = pvzpCurveInvQuad(pvzpCurveBounce(theTime));
                break;
            case PvzpCurves.CURVE_SIN_WAVE:
                aWarpedTime = (float) Math.sin(2 * Math.PI * theTime);
                break;
            case PvzpCurves.CURVE_EASE_SIN_WAVE:
                aWarpedTime = (float) Math.sin(2 * Math.PI * pvzpCurveS(theTime));
                break;
            default:
                break;
        }
        return (thePositionEnd - thePositionStart) * aWarpedTime + thePositionStart;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:336-360 ({@code PvzpCurveEvaluateClamped}). */
    public static float pvzpCurveEvaluateClamped(float theTime, float thePositionStart, float thePositionEnd, int theCurve) {
        if (theTime <= 0.0f) {
            return thePositionStart;
        }

        if (theTime >= 1.0f) {
            if (theCurve == PvzpCurves.CURVE_BOUNCE
                    || theCurve == PvzpCurves.CURVE_BOUNCE_FAST_MIDDLE
                    || theCurve == PvzpCurves.CURVE_BOUNCE_SLOW_MIDDLE
                    || theCurve == PvzpCurves.CURVE_SIN_WAVE
                    || theCurve == PvzpCurves.CURVE_EASE_SIN_WAVE) {
                return thePositionStart;
            }
            return thePositionEnd;
        }

        return pvzpCurveEvaluate(theTime, thePositionStart, thePositionEnd, theCurve);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:362-366 ({@code PvzpAnimateCurveFloatTime}). */
    public static float pvzpAnimateCurveFloatTime(float theTimeStart, float theTimeEnd, float theTimeAge,
                                                  float thePositionStart, float thePositionEnd, int theCurve) {
        float aWarpedAge = (theTimeAge - theTimeStart) / (theTimeEnd - theTimeStart);
        return pvzpCurveEvaluateClamped(aWarpedAge, thePositionStart, thePositionEnd, theCurve);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:368-374 ({@code PvzpAnimateCurveFloat}). */
    public static float pvzpAnimateCurveFloat(int theTimeStart, int theTimeEnd, int theTimeAge, float thePositionStart,
                                              float thePositionEnd, int theCurve) {
        float aWarpedAge = (theTimeAge - theTimeStart) / (float) (theTimeEnd - theTimeStart);
        return pvzpCurveEvaluateClamped(aWarpedAge, thePositionStart, thePositionEnd, theCurve);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:376-379 ({@code PvzpAnimateCurve}). */
    public static int pvzpAnimateCurve(int theTimeStart, int theTimeEnd, int theTimeAge, int thePositionStart,
                                       int thePositionEnd, int theCurve) {
        return floatRoundToInt(pvzpAnimateCurveFloat(theTimeStart, theTimeEnd, theTimeAge, (float) thePositionStart,
                (float) thePositionEnd, theCurve));
    }

    // ------------------------------------------------------------------
    // Random ranges and small maths helpers (PvzpCommon.cpp:381-391, PvzpCommon.h:169-177)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:381-385 ({@code RandRangeInt}). */
    public static int randRangeInt(int theMin, int theMax) {
        return Common.rand(theMax - theMin + 1) + theMin;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:387-391 ({@code RandRangeFloat}). */
    public static float randRangeFloat(float theMin, float theMax) {
        return Common.rand(theMax - theMin) + theMin;
    }

    /** Ported from src/PvzpLib/PvzpCommon.h:171 ({@code Distance2D}). */
    public static float distance2D(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    /** Ported from src/PvzpLib/PvzpCommon.h:172 ({@code FloatLerp}). */
    public static float floatLerp(float theStart, float theEnd, float theFactor) {
        return theStart + theFactor * (theEnd - theStart);
    }

    /** Ported from src/PvzpLib/PvzpCommon.h:173 ({@code FloatRoundToInt}). */
    public static int floatRoundToInt(float theFloatValue) {
        return (int) (theFloatValue > 0 ? theFloatValue + 0.5f : theFloatValue - 0.5f);
    }

    /** Ported from src/PvzpLib/PvzpCommon.h:174 ({@code FloatApproxEqual}); {@code FLT_EPSILON} is 1.1920929e-7f. */
    public static boolean floatApproxEqual(float theFloatVal1, float theFloatVal2) {
        return Math.abs(theFloatVal1 - theFloatVal2) < 1.1920929E-7f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1176-1191 ({@code FindGlobalAllocator}). */
    public static PvzpAllocator findGlobalAllocator(int theSize) {
        return PvzpAllocator.findGlobalAllocator(theSize);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1193-1201 ({@code FreeGlobalAllocators}). */
    public static void freeGlobalAllocators() {
        PvzpAllocator.freeGlobalAllocators();
    }

    /** Ported from src/PvzpLib/PvzpCommon.h:178 ({@code SetBit}): {@code uint&} becomes an {@code int[]} holder. */
    public static void setBit(int[] theNum, int theIdx, boolean theValue) {
        if (theValue) {
            theNum[0] |= 1 << theIdx;
        } else {
            theNum[0] &= ~(1 << theIdx);
        }
    }

    /** Ported from src/PvzpLib/PvzpCommon.h:179 ({@code TestBit}). */
    public static boolean testBit(int theNum, int theIdx) {
        return (theNum & (1 << theIdx)) != 0;
    }

    // ------------------------------------------------------------------
    // Draw helpers (PvzpCommon.cpp:393-421, 587-596, 670-821)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:393-408 ({@code PvzpDrawString}). */
    public static void pvzpDrawString(Graphics g, String theText, int thePosX, int thePosY, Font theFont, Color theColor,
                                      int theJustification) {
        String aFinalString = PvzpStringFile.translate(theText);

        int aPosX = thePosX;
        if (theJustification == DrawStringJustification.DS_ALIGN_RIGHT
                || theJustification == DrawStringJustification.DS_ALIGN_RIGHT_VERTICAL_MIDDLE) {
            aPosX -= theFont.stringWidth(aFinalString);
        } else if (theJustification == DrawStringJustification.DS_ALIGN_CENTER
                || theJustification == DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE) {
            aPosX -= theFont.stringWidth(aFinalString) / 2;
        }

        theFont.drawString(g, aPosX, thePosY, aFinalString, theColor, g.mClipRect);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:410-419 ({@code PvzpDrawImageCelScaled}). */
    public static void pvzpDrawImageCelScaled(Graphics g, Image theImageStrip, int thePosX, int thePosY, int theCelCol,
                                              int theCelRow, float theScaleX, float theScaleY) {
        int aCelWidth = theImageStrip.getCelWidth();
        int aCelHeight = theImageStrip.getCelHeight();
        Rect aSrcRect = new Rect(aCelWidth * theCelCol, aCelHeight * theCelRow, aCelWidth, aCelHeight);
        Rect aDestRect = new Rect(thePosX, thePosY, floatRoundToInt(aCelWidth * theScaleX),
                floatRoundToInt(aCelHeight * theScaleY));
        g.drawImage(theImageStrip, aDestRect, aSrcRect);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:587-596 ({@code PvzpDrawImageCelF}). */
    public static void pvzpDrawImageCelF(Graphics g, Image theImageStrip, float thePosX, float thePosY, int theCelCol,
                                         int theCelRow) {
        int aCelWidth = theImageStrip.getCelWidth();
        int aCelHeight = theImageStrip.getCelHeight();
        Rect theSrcRect = new Rect(aCelWidth * theCelCol, aCelHeight * theCelRow,
                aCelWidth, aCelHeight);
        g.drawImageF(theImageStrip, thePosX, thePosY, theSrcRect);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:705-734 ({@code PvzpDrawImageCelCenterScaledF}). */
    public static void pvzpDrawImageCelCenterScaledF(Graphics g, Image theImageStrip, float thePosX, float thePosY,
                                                     int theCelCol, float theScaleX, float theScaleY) {
        int aCelWidth = theImageStrip.getCelWidth();
        int aCelHeight = theImageStrip.getCelHeight();
        Rect aSrcRect = new Rect(aCelWidth * theCelCol, 0, aCelWidth, aCelHeight);
        if (theScaleX == 1.0f && theScaleY == 1.0f) {
            g.drawImageF(theImageStrip, thePosX, thePosY, aSrcRect);
            return;
        }

        float aTransX = aCelWidth * 0.5f + thePosX + g.mTransX;
        float aTransY = aCelHeight * 0.5f + thePosY + g.mTransY;
        pvzpBltMatrix(g, theImageStrip, scaleMatrix(theScaleX, theScaleY, aTransX, aTransY), g.mClipRect,
                g.mColorizeImages ? g.mColor : Color.White, g.mDrawMode, aSrcRect);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:736-766 ({@code PvzpDrawImageCelScaledF}). */
    public static void pvzpDrawImageCelScaledF(Graphics g, Image theImageStrip, float thePosX, float thePosY, int theCelCol,
                                               int theCelRow, float theScaleX, float theScaleY) {
        int aCelWidth = theImageStrip.getCelWidth();
        int aCelHeight = theImageStrip.getCelHeight();
        Rect aSrcRect = new Rect(aCelWidth * theCelCol, aCelHeight * theCelRow,
                aCelWidth, aCelHeight);
        if (theScaleX == 1.0f && theScaleY == 1.0f) {
            g.drawImageF(theImageStrip, thePosX, thePosY, aSrcRect);
            return;
        }

        float aTransX = aCelWidth * 0.5f * theScaleX + thePosX + g.mTransX;
        float aTransY = aCelHeight * 0.5f * theScaleY + thePosY + g.mTransY;
        pvzpBltMatrix(g, theImageStrip, scaleMatrix(theScaleX, theScaleY, aTransX, aTransY), g.mClipRect,
                g.mColorizeImages ? g.mColor : Color.White, g.mDrawMode, aSrcRect);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:768-793 ({@code PvzpDrawImageScaledF}). */
    public static void pvzpDrawImageScaledF(Graphics g, Image theImage, float thePosX, float thePosY, float theScaleX,
                                            float theScaleY) {
        if (theScaleX == 1.0f && theScaleY == 1.0f) {
            g.drawImageF(theImage, thePosX, thePosY);
            return;
        }

        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);
        float aTransX = theImage.mWidth * 0.5f * theScaleX + thePosX + g.mTransX;
        float aTransY = theImage.mHeight * 0.5f * theScaleY + thePosY + g.mTransY;
        pvzpBltMatrix(g, theImage, scaleMatrix(theScaleX, theScaleY, aTransX, aTransY), g.mClipRect,
                g.mColorizeImages ? g.mColor : Color.White, g.mDrawMode, aSrcRect);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:795-821 ({@code PvzpDrawImageCenterScaledF}). */
    public static void pvzpDrawImageCenterScaledF(Graphics g, Image theImage, float thePosX, float thePosY, float theScaleX,
                                                  float theScaleY) {
        if (theScaleX == 1.0f && theScaleY == 1.0f) {
            g.drawImageF(theImage, thePosX, thePosY);
            return;
        }

        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);
        float aTransX = theImage.mWidth * 0.5f + thePosX + g.mTransX;
        float aTransY = theImage.mHeight * 0.5f + thePosY + g.mTransY;
        pvzpBltMatrix(g, theImage, scaleMatrix(theScaleX, theScaleY, aTransX, aTransY), g.mClipRect,
                g.mColorizeImages ? g.mColor : Color.White, g.mDrawMode, aSrcRect);
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:670-703 ({@code PvzpBltMatrix}).
     *
     * <p>Upstream picks between the 3D-accelerated offset path, the {@code GLImage} destination path
     * ({@code GLInterface::BltTransformed}) and the software {@code BltMatrix} path; in this port
     * {@code Image.bltMatrix} owns the destination choice (including the GL/3D case), so the call is
     * the software one and the two 3D-only offset pixels and the software-triangle additive flag
     * disappear with the removed renderer.
     */
    public static void pvzpBltMatrix(Graphics g, Image theImage, SexyMatrix3 theTransform, Rect theClipRect,
                                     Color theColor, int theDrawMode, Rect theSrcRect) {
        pvzpSandImageIfNeeded(theImage);
        g.mDestImage.bltMatrix(theImage, 0.0f, 0.0f, theTransform, theClipRect, theColor, theDrawMode, theSrcRect,
                g.mLinearBlend);
    }

    private static SexyMatrix3 scaleMatrix(float theScaleX, float theScaleY, float theTransX, float theTransY) {
        SexyMatrix3 aTransform = new SexyMatrix3();
        aTransform.m00 = theScaleX;
        aTransform.m10 = 0.0f;
        aTransform.m20 = 0.0f;
        aTransform.m01 = 0.0f;
        aTransform.m11 = theScaleY;
        aTransform.m21 = 0.0f;
        aTransform.m02 = theTransX;
        aTransform.m12 = theTransY;
        aTransform.m22 = 1.0f;
        return aTransform;
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:427-585 ({@code PvzpDrawStringMatrix}): lays out the glyphs
     * of an {@link ImageFont} into {@link RenderCommand}s bucketed by
     * {@code clamp(order + 128, 0, 255)} and replays them through {@link #pvzpBltMatrix}.
     */
    public static void pvzpDrawStringMatrix(Graphics g, Font theFont, SexyMatrix3 theMatrix, String theString,
                                            Color theColor) {
        String aFinalString = PvzpStringFile.translate(theString);

        RenderCommand[] gRenderCommandPool = new RenderCommand[POOL_SIZE];
        RenderCommand[] aRenderTail = new RenderCommand[256];
        RenderCommand[] aRenderHead = new RenderCommand[256];
        int aCurPoolIdx = 0;

        if (!(theFont instanceof ImageFont aFont)) {
            return;
        }
        if (aFont.mFontData == null || !aFont.mFontData.mInitialized) {
            return;
        }

        aFont.prepare();
        int aCurXPos = 0;
        byte[] aBytes = aFinalString.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int[] aDecodeOffset = {0};
        int aCurRawChar = Common.utf8DecodeNext(aBytes, aDecodeOffset);
        while (aCurRawChar >= 0) {
            int aNextRawChar = Common.utf8DecodeNext(aBytes, aDecodeOffset);
            boolean aHasNext = aNextRawChar >= 0;
            int aChar = aFont.getMappedChar(aCurRawChar);
            int aNextChar = aHasNext ? aFont.getMappedChar(aNextRawChar) : 0;

            int aMaxXPos = aCurXPos;
            List<ActiveFontLayer> aLayerList = aFont.mActiveLayerList;
            for (ActiveFontLayer anActiveLayer : aLayerList) {
                FontLayer aLayer = anActiveLayer.mBaseFontLayer;
                CharData aCharData = aLayer.getCharData(aChar);
                Rect aRect = anActiveLayer.mScaledCharImageRects.get(aChar);
                if (aRect == null || aCharData == null) {
                    continue;
                }

                double aScale = aFont.mScale;
                int aLayerPointSize = aLayer.mPointSize;
                if (aLayerPointSize != 0) {
                    aScale *= (float) aFont.mPointSize / (float) aLayerPointSize;
                }

                int anImageX;
                int anImageY;
                int aCharWidth;
                int aSpacing;
                if (aScale == 1.0f) {
                    anImageX = aCharData.mOffset.mX + aLayer.mOffset.mX + aCurXPos;
                    anImageY = aCharData.mOffset.mY + aLayer.mOffset.mY - aLayer.mAscent;
                    aCharWidth = aCharData.mWidth;

                    if (aNextChar == 0) {
                        aSpacing = 0;
                    } else {
                        aSpacing = aLayer.mSpacing + aCharData.kerningOffset(aNextChar);
                    }
                } else {
                    anImageX = aCurXPos + (int) Math.floor((aCharData.mOffset.mX + aLayer.mOffset.mX) * aScale);
                    anImageY = -(int) Math.floor((aLayer.mAscent - aLayer.mOffset.mY - aCharData.mOffset.mY) * aScale);
                    aCharWidth = (int) (aCharData.mWidth * aScale);

                    if (aNextChar == 0) {
                        aSpacing = 0;
                    } else {
                        aSpacing = aLayer.mSpacing + (int) (aCharData.kerningOffset(aNextChar) * aScale);
                    }
                }

                Color aColor = new Color(
                        Math.min(aLayer.mColorAdd.mRed + theColor.mRed * aLayer.mColorMult.mRed / 255, 255),
                        Math.min(aLayer.mColorAdd.mGreen + theColor.mGreen * aLayer.mColorMult.mGreen / 255, 255),
                        Math.min(aLayer.mColorAdd.mBlue + theColor.mBlue * aLayer.mColorMult.mBlue / 255, 255),
                        Math.min(aLayer.mColorAdd.mAlpha + theColor.mAlpha * aLayer.mColorMult.mAlpha / 255, 255));
                int anOrder = aCharData.mOrder + aLayer.mBaseOrder;

                if (aCurPoolIdx >= POOL_SIZE) {
                    break;
                }

                RenderCommand aRenderCommand = new RenderCommand();
                gRenderCommandPool[aCurPoolIdx++] = aRenderCommand;
                aRenderCommand.mImage = anActiveLayer.mScaledImage;
                aRenderCommand.mColor = aColor;
                aRenderCommand.mDest[0] = anImageX;
                aRenderCommand.mDest[1] = anImageY;
                aRenderCommand.mSrc[0] = aRect.mX;
                aRenderCommand.mSrc[1] = aRect.mY;
                aRenderCommand.mSrc[2] = aRect.mWidth;
                aRenderCommand.mSrc[3] = aRect.mHeight;
                aRenderCommand.mMode = aLayer.mDrawMode;
                aRenderCommand.mUseAlphaCorrection = aLayer.mUseAlphaCorrection;
                aRenderCommand.mNext = null;

                int anOrderIdx = Math.clamp(anOrder + 128, 0, 255);
                if (aRenderTail[anOrderIdx] != null) {
                    aRenderTail[anOrderIdx].mNext = aRenderCommand;
                    aRenderTail[anOrderIdx] = aRenderCommand;
                } else {
                    aRenderHead[anOrderIdx] = aRenderCommand;
                    aRenderTail[anOrderIdx] = aRenderCommand;
                }

                aMaxXPos = Math.max(aMaxXPos, aCurXPos + aSpacing + aCharWidth);
            }

            aCurXPos = aMaxXPos;
            aCurRawChar = aNextRawChar;
        }

        for (int aPoolIdx = 0; aPoolIdx < 256; aPoolIdx++) {
            RenderCommand aRenderCommand = aRenderHead[aPoolIdx];

            while (aRenderCommand != null) {
                int aDrawMode = g.getDrawMode();
                if (aRenderCommand.mMode != -1) {
                    aDrawMode = aRenderCommand.mMode;
                }

                if (aRenderCommand.mImage != null) {
                    Rect aSrcRect = new Rect(aRenderCommand.mSrc[0],
                            aRenderCommand.mSrc[1], aRenderCommand.mSrc[2], aRenderCommand.mSrc[3]);
                    SexyTransform2D aTransform = new SexyTransform2D();
                    float aPosX = aSrcRect.mWidth * 0.5f + aRenderCommand.mDest[0];
                    float aPosY = aSrcRect.mHeight * 0.5f + aRenderCommand.mDest[1];
                    sexyMatrix3Translation(aTransform, aPosX, aPosY);
                    sexyMatrix3Multiply(aTransform, theMatrix, aTransform);
                    pvzpBltMatrix(g, aRenderCommand.mImage, aTransform, g.mClipRect, aRenderCommand.mColor, aDrawMode,
                            aSrcRect);
                }

                aRenderCommand = aRenderCommand.mNext;
            }
        }
    }

    private static final int POOL_SIZE = 4096;

    // ------------------------------------------------------------------
    // Image sanding (PvzpCommon.cpp:653-668, 822-897)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:653-656 ({@code PvzpMarkImageForSanding}). */
    public static void pvzpMarkImageForSanding(Image theImage) {
        if (theImage instanceof MemoryImage aMemoryImage) {
            aMemoryImage.mRenderFlags |= RENDERIMAGEFLAG_SANDING;
        }
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:658-668 ({@code PvzpSandImageIfNeeded}). */
    public static void pvzpSandImageIfNeeded(Image theImage) {
        if (!(theImage instanceof MemoryImage aImage)) {
            return;
        }
        if ((aImage.mRenderFlags & RENDERIMAGEFLAG_SANDING) != 0) {
            fixPixelsOnAlphaEdgeForBlending(theImage);
            aImage.mRenderFlags &= ~RENDERIMAGEFLAG_SANDING;
        }
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:822-862 ({@code AverageNearByPixels}). The upstream
     * {@code uint32_t* thePixel} (a raw pointer into the bit array) becomes the {@code x,y} cell, and
     * the result is a {@code long} carrying the unsigned 32-bit value.
     */
    public static long averageNearByPixels(MemoryImage theImage, int x, int y) {
        int[] aBits = theImage.getBits();
        int aRed = 0;
        int aGreen = 0;
        int aBlue = 0;
        int aBitsCount = 0;

        for (int i = -1; i <= 1; i++) {
            if (i == 0) {
                continue;
            }
            for (int j = -1; j <= 1; j++) {
                if ((x != 0 || j != -1) && (x != theImage.mWidth - 1 || j != 1) && (y != 0 || i != -1)
                        && (y != theImage.mHeight - 1 || i != 1)) {
                    int aPixel = aBits[(y + i) * theImage.mWidth + (x + j)];
                    if ((aPixel & 0xFF000000) != 0) {
                        aRed += (aPixel >> 16) & 0x000000FF;
                        aGreen += (aPixel >> 8) & 0x000000FF;
                        aBlue += aPixel & 0x000000FF;
                        aBitsCount++;
                    }
                }
            }
        }

        if (aBitsCount == 0) {
            return 0;
        }

        aRed /= aBitsCount;
        aRed = Math.min(aRed, 255);
        aGreen /= aBitsCount;
        aGreen = Math.min(aGreen, 255);
        aBlue /= aBitsCount;
        aBlue = Math.min(aBlue, 255);
        return ((long) aRed << 16) | ((long) aGreen << 8) | aBlue;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:864-897 ({@code FixPixelsOnAlphaEdgeForBlending}). */
    public static void fixPixelsOnAlphaEdgeForBlending(Image theImage) {
        if (!(theImage instanceof MemoryImage aImage)) {
            return;
        }

        aImage.commitBits();
        if (!aImage.mHasTrans) {
            return;
        }

        int[] aBits = aImage.getBits();
        PerfTimer aTimer = new PerfTimer();
        aTimer.start();

        for (int y = 0; y < theImage.mHeight; y++) {
            for (int x = 0; x < theImage.mWidth; x++) {
                int anIndex = y * theImage.mWidth + x;
                if ((aBits[anIndex] & 0xFF000000) == 0) {
                    aBits[anIndex] = (int) averageNearByPixels(aImage, x, y);
                }
            }
        }
        aImage.bitsChanged();

        int aDuration = (int) Math.max(aTimer.getDuration(), 0.0);
        if (aDuration > 20) {
            PvzpDebug.pvzpLogLn("LOADING:Long sanding '{}' {} ms on {}", theImage.mFilePath, aDuration,
                    PvzpResourceManager.gGetCurrentLevelName.get());
        }
    }

    // ------------------------------------------------------------------
    // Matrix helpers (PvzpCommon.cpp:598-651, 899-967)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:598-602 ({@code SexyMatrix3Translation}). */
    public static void sexyMatrix3Translation(SexyMatrix3 m, float x, float y) {
        m.m02 += x;
        m.m12 += y;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:604-615 ({@code PvzpScaleTransformMatrix}). */
    public static void pvzpScaleTransformMatrix(SexyMatrix3 m, float x, float y, float theScaleX, float theScaleY) {
        m.m00 = theScaleX;
        m.m10 = 0.0f;
        m.m20 = 0.0f;
        m.m01 = 0.0f;
        m.m11 = theScaleY;
        m.m21 = 0.0f;
        m.m02 = x;
        m.m12 = y;
        m.m22 = 1.0f;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:617-628 ({@code PvzpScaleRotateTransformMatrix}). */
    public static void pvzpScaleRotateTransformMatrix(SexyMatrix3 m, float x, float y, float rad, float theScaleX,
                                                      float theScaleY) {
        m.m00 = (float) Math.cos(rad) * theScaleX;
        m.m10 = (float) -Math.sin(rad) * theScaleX;
        m.m20 = 0.0f;
        m.m01 = (float) Math.sin(rad) * theScaleY;
        m.m11 = (float) Math.cos(rad) * theScaleY;
        m.m21 = 0.0f;
        m.m02 = x;
        m.m12 = y;
        m.m22 = 1.0f;
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:630-651 ({@code SexyMatrix3ExtractScale}). Upstream's two
     * {@code float&} out-parameters become a two-element array.
     */
    public static void sexyMatrix3ExtractScale(SexyMatrix3 m, float[] theScaleXY) {
        float kx = (float) Math.atan2(m.m00, m.m10);
        if (Math.abs(kx) < Math.PI / 4 || Math.abs(kx) > 4 * Math.PI / 3) {
            theScaleXY[0] = (float) (m.m10 / Math.cos(kx));
        } else {
            theScaleXY[0] = (float) (m.m00 / Math.sin(kx));
        }

        float ky = (float) Math.atan2(m.m11, m.m01);
        if (Math.abs(ky) < Math.PI / 4 || Math.abs(ky) > 4 * Math.PI / 3) {
            theScaleXY[1] = (float) (m.m01 / Math.cos(ky));
        } else {
            theScaleXY[1] = (float) (m.m11 / Math.sin(ky));
        }
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:899-919 ({@code SexyMatrix3Transpose}). */
    public static void sexyMatrix3Transpose(SexyMatrix3 m, SexyMatrix3 r) {
        SexyMatrix3 temp = new SexyMatrix3();
        temp.m00 = m.m00;
        temp.m01 = m.m10;
        temp.m02 = m.m20;
        temp.m10 = m.m01;
        temp.m11 = m.m11;
        temp.m12 = m.m21;
        temp.m20 = m.m02;
        temp.m21 = m.m12;
        temp.m22 = m.m22;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r.set(i, j, temp.get(i, j));
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:921-944 ({@code SexyMatrix3Inverse}). */
    public static void sexyMatrix3Inverse(SexyMatrix3 m, SexyMatrix3 r) {
        float aDet = (m.m22 * m.m11 - m.m21 * m.m12) * m.m00 - (m.m22 * m.m10 - m.m20 * m.m12) * m.m01
                + (m.m21 * m.m10 - m.m20 * m.m11) * m.m02;
        float aInvDet = 1.0f / aDet;

        SexyMatrix3 temp = new SexyMatrix3();
        temp.m00 = (m.m22 * m.m11 - m.m21 * m.m12) * aInvDet;
        temp.m01 = (m.m02 * m.m21 - m.m22 * m.m01) * aInvDet;
        temp.m02 = (m.m12 * m.m01 - m.m02 * m.m11) * aInvDet;
        temp.m10 = (m.m20 * m.m12 - m.m22 * m.m10) * aInvDet;
        temp.m11 = (m.m00 * m.m22 - m.m02 * m.m20) * aInvDet;
        temp.m12 = (m.m02 * m.m10 - m.m12 * m.m00) * aInvDet;
        temp.m20 = (m.m21 * m.m10 - m.m20 * m.m11) * aInvDet;
        temp.m21 = (m.m20 * m.m01 - m.m21 * m.m00) * aInvDet;
        temp.m22 = (m.m00 * m.m11 - m.m10 * m.m01) * aInvDet;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r.set(i, j, temp.get(i, j));
            }
        }
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:946-965 ({@code SexyMatrix3Multiply}). */
    public static void sexyMatrix3Multiply(SexyMatrix3 m, SexyMatrix3 l, SexyMatrix3 r) {
        SexyMatrix3 temp = new SexyMatrix3();
        temp.m00 = l.m00 * r.m00 + l.m01 * r.m10 + l.m02 * r.m20;
        temp.m01 = l.m00 * r.m01 + l.m01 * r.m11 + l.m02 * r.m21;
        temp.m02 = l.m00 * r.m02 + l.m01 * r.m12 + l.m02 * r.m22;
        temp.m10 = l.m10 * r.m00 + l.m11 * r.m10 + l.m12 * r.m20;
        temp.m11 = l.m10 * r.m01 + l.m11 * r.m11 + l.m12 * r.m21;
        temp.m12 = l.m10 * r.m02 + l.m11 * r.m12 + l.m12 * r.m22;
        temp.m20 = l.m20 * r.m00 + l.m21 * r.m10 + l.m22 * r.m20;
        temp.m21 = l.m20 * r.m01 + l.m21 * r.m11 + l.m22 * r.m21;
        temp.m22 = l.m20 * r.m02 + l.m21 * r.m12 + l.m22 * r.m22;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                m.set(i, j, temp.get(i, j));
            }
        }
    }

    // ------------------------------------------------------------------
    // Colours (PvzpCommon.cpp:969-1002)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:969-977 ({@code GetFlashingColor}). */
    public static Color getFlashingColor(long theCounter, int theFlashTime) {
        int aTimeAge = (int) Math.floorMod(theCounter, (long) theFlashTime);
        int aTimeInf = theFlashTime / 2;
        int aGrayness = Math.clamp(200 * Math.abs(aTimeInf - aTimeAge) / aTimeInf + 55, 0, 255);
        return new Color(aGrayness, aGrayness, aGrayness, 255);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:979-987 ({@code ColorAdd}). */
    public static Color colorAdd(Color theColor1, Color theColor2) {
        return new Color(
                Math.clamp(theColor1.mRed + theColor2.mRed, 0, 255),
                Math.clamp(theColor1.mGreen + theColor2.mGreen, 0, 255),
                Math.clamp(theColor1.mBlue + theColor2.mBlue, 0, 255),
                Math.clamp(theColor1.mAlpha + theColor2.mAlpha, 0, 255));
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:989-992 ({@code ColorComponentMultiply}). */
    public static int colorComponentMultiply(int theColor1, int theColor2) {
        return Math.clamp(theColor1 * theColor2 / 255, 0, 255);
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:994-1002 ({@code ColorsMultiply}). */
    public static Color colorsMultiply(Color theColor1, Color theColor2) {
        return new Color(
                colorComponentMultiply(theColor1.mRed, theColor2.mRed),
                colorComponentMultiply(theColor1.mGreen, theColor2.mGreen),
                colorComponentMultiply(theColor1.mBlue, theColor2.mBlue),
                colorComponentMultiply(theColor1.mAlpha, theColor2.mAlpha));
    }

    // ------------------------------------------------------------------
    // Strings and geometry (PvzpCommon.cpp:1203-1244)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1203-1214 ({@code PvzpReplaceString}). */
    public static String pvzpReplaceString(String theText, String theStringToFind, String theStringToSubstitute) {
        String aFinalString = PvzpStringFile.translate(theText);
        int aPos = aFinalString.indexOf(theStringToFind);
        if (aPos >= 0) {
            String aFinalStringToSubstitute = PvzpStringFile.translate(theStringToSubstitute);
            aFinalString = aFinalString.substring(0, aPos) + aFinalStringToSubstitute
                    + aFinalString.substring(aPos + theStringToFind.length());
        }
        return aFinalString;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1216-1227 ({@code PvzpReplaceNumberString}). */
    public static String pvzpReplaceNumberString(String theText, String theStringToFind, int theNumber) {
        String aFinalString = PvzpStringFile.translate(theText);
        int aPos = aFinalString.indexOf(theStringToFind);
        if (aPos >= 0) {
            String aNumberString = Integer.toString(theNumber);
            aFinalString = aFinalString.substring(0, aPos) + aNumberString
                    + aFinalString.substring(aPos + theStringToFind.length());
        }
        return aFinalString;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1229-1244 ({@code PvzpIsPointInPolygon}). */
    public static boolean pvzpIsPointInPolygon(SexyVector2[] thePolygonPoint, int theNumberPolygonPoints,
                                               SexyVector2 theCheckPoint) {
        for (int i = 0; i < theNumberPolygonPoints; i++) {
            SexyVector2 cur = thePolygonPoint[i];
            SexyVector2 nex = thePolygonPoint[i == theNumberPolygonPoints - 1 ? 0 : i + 1];

            SexyVector2 u = nex.minus(cur).perp();
            SexyVector2 v = theCheckPoint.minus(cur);
            if (u.dot(v) < 0) {
                return false;
            }
        }
        return true;
    }
}
