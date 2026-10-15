package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.TriVertex;

/**
 * Ported from src/PvzpLib/Trail.h:90 (class Trail) and src/PvzpLib/Trail.cpp:88-269.
 *
 * <p>A ribbon of up to 20 points (cap 20, {@code MAX_TRAIL_TRIANGLES = 38}) drawn as one batched
 * triangle strip: u runs 1→0 from tail to head, v is 1/0 across the ribbon, RGB comes from
 * {@link #mColorOverride} verbatim and only the alpha is modulated by the length/time tracks.
 * Points are relative to {@link #mTrailCenter}, which the owner sets. Public field names are
 * upstream; the render call is {@link Graphics#drawTrianglesTex} (the trail has no triangle group
 * and no SW/GL additive override, exactly like upstream).
 */
public class Trail {

    /** Ported from src/PvzpLib/Trail.h:28. */
    public static final int MAX_TRAIL_TRIANGLES = 38;

    public TrailPoint[] mTrailPoints = new TrailPoint[20];
    public int mNumTrailPoints;
    public boolean mDead;
    public int mRenderOrder;
    public int mTrailAge;
    public int mTrailDuration;
    public TrailDefinition mDefinition;
    public TrailHolder mTrailHolder;
    public float[] mTrailInterp = new float[TrailTracks.NUM_TRAIL_TRACKS];
    public SexyVector2 mTrailCenter = new SexyVector2();
    public boolean mIsAttachment;
    public Color mColorOverride = new Color(Color.White.mRed, Color.White.mGreen, Color.White.mBlue, Color.White.mAlpha);

    /** Ported from src/PvzpLib/Trail.cpp:88. */
    public Trail() {
        for (int i = 0; i < mTrailPoints.length; i++) {
            mTrailPoints[i] = new TrailPoint();
        }
        mNumTrailPoints = 0;
        mDead = false;
        mIsAttachment = false;
        mRenderOrder = 0;
        mTrailAge = 0;
        mDefinition = null;
        mTrailDuration = 0;
        mColorOverride = new Color(Color.White.mRed, Color.White.mGreen, Color.White.mBlue, Color.White.mAlpha);
    }

    /** Ported from src/PvzpLib/Trail.cpp:100. */
    public void addPoint(float x, float y) {
        int aMaxPoints = clamp(mDefinition.mMaxPoints, 2, 20);

        if (mNumTrailPoints > 0) {
            TrailPoint aPoint = mTrailPoints[mNumTrailPoints - 1];
            float aDistance = PvzpCommon.distance2D(x, y, aPoint.aPos.x, aPoint.aPos.y);
            if (aDistance < mDefinition.mMinPointDistance) {
                return;
            }
        }

        if (mNumTrailPoints == aMaxPoints) {
            for (int i = 0; i < mNumTrailPoints - 1; i++) {
                mTrailPoints[i].aPos.x = mTrailPoints[i + 1].aPos.x;
                mTrailPoints[i].aPos.y = mTrailPoints[i + 1].aPos.y;
            }
            mNumTrailPoints--;
        }

        TrailPoint aNewPoint = mTrailPoints[mNumTrailPoints];
        aNewPoint.aPos.x = x;
        aNewPoint.aPos.y = y;
        mNumTrailPoints++;
    }

    /** Ported from src/PvzpLib/Trail.cpp:127. */
    public void update() {
        mTrailAge++;
        if (mTrailAge >= mTrailDuration) {
            if (PvzpCommon.testBit(mDefinition.mTrailFlags, TrailFlags.TRAIL_FLAG_LOOPS)) {
                mTrailAge = 0;
            } else {
                mDead = true;
            }
        }
    }

    /** Ported from src/PvzpLib/Trail.cpp:143. */
    public boolean getNormalAtPoint(int nIndex, SexyVector2 theNormal) {
        SexyVector2 aDirection;
        if (nIndex == 0) {
            SexyVector2 aToNext = mTrailPoints[nIndex + 1].aPos.minus(mTrailPoints[nIndex].aPos);
            aDirection = aToNext.perp();
        } else if (nIndex == mNumTrailPoints - 1) {
            SexyVector2 aFromPrev = mTrailPoints[nIndex].aPos.minus(mTrailPoints[nIndex - 1].aPos);
            aDirection = aFromPrev.perp();
        } else {
            SexyVector2 aToNext = mTrailPoints[nIndex + 1].aPos.minus(mTrailPoints[nIndex].aPos);
            SexyVector2 aToPrev = mTrailPoints[nIndex - 1].aPos.minus(mTrailPoints[nIndex].aPos);
            aDirection = aToNext.normalize().plus(aToPrev.normalize());
        }

        float aMag = aDirection.magnitude();
        if (PvzpCommon.floatApproxEqual(aMag, 0.0f)) {
            return false;
        }

        theNormal.x = aDirection.x / aMag;
        theNormal.y = aDirection.y / aMag;
        return true;
    }

    /** Ported from src/PvzpLib/Trail.cpp:172. */
    public void draw(Graphics g) {
        if (mDead || mNumTrailPoints < 2) {
            return;
        }

        float aTimeValue = mTrailAge / ((float) mTrailDuration - 1);
        int aTriangleCount = (mNumTrailPoints - 1) * 2;

        TriVertex[][] aVertArray = new TriVertex[MAX_TRAIL_TRIANGLES][3];
        for (int i = 0; i < MAX_TRAIL_TRIANGLES; i++) {
            for (int j = 0; j < 3; j++) {
                aVertArray[i][j] = new TriVertex();
            }
        }

        boolean aHavePrev = false;
        SexyVector2 aNormalPrev = new SexyVector2();
        for (int i = 0; i < mNumTrailPoints - 1; i++) {
            if (!aHavePrev) {
                if (!getNormalAtPoint(i, aNormalPrev)) {
                    continue;
                }
                aHavePrev = true;
            }

            SexyVector2 aNormalNext = new SexyVector2();
            SexyVector2 aNormalCur = aNormalPrev;
            if (!getNormalAtPoint(i + 1, aNormalNext)) {
                aNormalNext = aNormalPrev;
            } else {
                aNormalPrev = aNormalNext;
            }

            TrailPoint aPointCur = mTrailPoints[i];
            TrailPoint aPointNext = mTrailPoints[i + 1];
            float aUCur = 1.0f - i / ((float) mNumTrailPoints - 1);
            float aUNext = 1.0f - (i + 1) / ((float) mNumTrailPoints - 1);
            float aWidthOverLengthCur = Definition.floatTrackEvaluate(mDefinition.mWidthOverLength, aUCur, mTrailInterp[TrailTracks.TRACK_WIDTH_OVER_LENGTH]);
            float aWidthOverLengthNext = Definition.floatTrackEvaluate(mDefinition.mWidthOverLength, aUNext, mTrailInterp[TrailTracks.TRACK_WIDTH_OVER_LENGTH]);
            float aWidthOverTimeCur = Definition.floatTrackEvaluate(mDefinition.mWidthOverTime, aTimeValue, mTrailInterp[TrailTracks.TRACK_WIDTH_OVER_TIME]);
            float aAlphaOverLengthCur = Definition.floatTrackEvaluate(mDefinition.mAlphaOverLength, aUCur, mTrailInterp[TrailTracks.TRACK_ALPHA_OVER_LENGTH]);
            float aAlphaOverLengthNext = Definition.floatTrackEvaluate(mDefinition.mAlphaOverLength, aUNext, mTrailInterp[TrailTracks.TRACK_ALPHA_OVER_LENGTH]);
            float aAlphaOverTimeCur = Definition.floatTrackEvaluate(mDefinition.mAlphaOverTime, aTimeValue, mTrailInterp[TrailTracks.TRACK_ALPHA_OVER_TIME]);
            int anAlphaCur = clamp(PvzpCommon.floatRoundToInt(aAlphaOverLengthCur * aAlphaOverTimeCur * mColorOverride.mAlpha), 0, 255);
            int anAlphaNext = clamp(PvzpCommon.floatRoundToInt(aAlphaOverLengthNext * aAlphaOverTimeCur * mColorOverride.mAlpha), 0, 255);
            Color aColorCur = new Color(mColorOverride.mRed, mColorOverride.mGreen, mColorOverride.mBlue, anAlphaCur);
            Color aColorNext = new Color(mColorOverride.mRed, mColorOverride.mGreen, mColorOverride.mBlue, anAlphaNext);

            SexyVector2[] aPosition = new SexyVector2[4];
            aPosition[0] = new SexyVector2(
                    mTrailCenter.x + aPointCur.aPos.x + aNormalCur.x * aWidthOverLengthCur * aWidthOverTimeCur,
                    mTrailCenter.y + aPointCur.aPos.y + aNormalCur.y * aWidthOverLengthCur * aWidthOverTimeCur);
            aPosition[1] = new SexyVector2(
                    mTrailCenter.x + aPointCur.aPos.x + -aNormalCur.x * aWidthOverLengthCur * aWidthOverTimeCur,
                    mTrailCenter.y + aPointCur.aPos.y + -aNormalCur.y * aWidthOverLengthCur * aWidthOverTimeCur);
            aPosition[2] = new SexyVector2(
                    mTrailCenter.x + aPointNext.aPos.x + aNormalNext.x * aWidthOverLengthNext * aWidthOverTimeCur,
                    mTrailCenter.y + aPointNext.aPos.y + aNormalNext.y * aWidthOverLengthNext * aWidthOverTimeCur);
            aPosition[3] = new SexyVector2(
                    mTrailCenter.x + aPointNext.aPos.x + -aNormalNext.x * aWidthOverLengthNext * aWidthOverTimeCur,
                    mTrailCenter.y + aPointNext.aPos.y + -aNormalNext.y * aWidthOverLengthNext * aWidthOverTimeCur);

            int aVertCur = i * 2;
            int aVertNext = aVertCur + 1;
            setVertex(aVertArray[aVertCur][0], aPosition[0], aUCur, 1.0f, aColorCur);
            setVertex(aVertArray[aVertCur][1], aPosition[1], aUCur, 0.0f, aColorCur);
            setVertex(aVertArray[aVertCur][2], aPosition[2], aUNext, 1.0f, aColorNext);
            setVertex(aVertArray[aVertNext][0], aPosition[2], aUNext, 1.0f, aColorNext);
            setVertex(aVertArray[aVertNext][1], aPosition[1], aUCur, 0.0f, aColorCur);
            setVertex(aVertArray[aVertNext][2], aPosition[3], aUNext, 0.0f, aColorNext);
        }

        g.drawTrianglesTex(mDefinition.mImage, aVertArray, aTriangleCount);
    }

    private static void setVertex(TriVertex v, SexyVector2 pos, float u, float vv, Color color) {
        v.x = pos.x;
        v.y = pos.y;
        v.u = u;
        v.v = vv;
        v.color = color.toInt();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
