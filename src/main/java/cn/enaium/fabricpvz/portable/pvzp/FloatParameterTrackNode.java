/*
 * Ported from src/PvzpLib/PvzpParticle.h:69-80 (class FloatParameterTrackNode).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:69-80 (class FloatParameterTrackNode).
 *
 * <p>Each node describes one stage of a value's change over time. Upstream stores the two curve
 * fields as the {@code PvzpCurves} enum; in Java they are the {@code cn.enaium.fabricpvz.portable.lawn.PvzpCurves}
 * int constants ({@code int32_t} in both cases, which matters for the compiled cache layout).
 */
public final class FloatParameterTrackNode {
    /**
     * Upstream {@code sizeof(FloatParameterTrackNode)}: 3 floats + 2 int32, alignment 4.
     * Used for the compiled cache layout (Definition.cpp:469 {@code DefGetSizeFloatTrack}).
     */
    public static final int SIZE = 20;

    /** Upstream {@code mTime}: start time of the stage. */
    public float mTime;

    /** Upstream {@code mLowValue}: minimum value allowed in the stage. */
    public float mLowValue;

    /** Upstream {@code mHighValue}: maximum value allowed in the stage. */
    public float mHighValue;

    /** Upstream {@code mCurveType} ({@code PvzpCurves}): easing curve to the next stage. */
    public int mCurveType;

    /** Upstream {@code mDistribution} ({@code PvzpCurves}): probability distribution between min and max. */
    public int mDistribution;

    public FloatParameterTrackNode() {
    }

    public FloatParameterTrackNode(float theTime, float theLowValue, float theHighValue, int theCurveType, int theDistribution) {
        mTime = theTime;
        mLowValue = theLowValue;
        mHighValue = theHighValue;
        mCurveType = theCurveType;
        mDistribution = theDistribution;
    }

    public FloatParameterTrackNode(FloatParameterTrackNode theOther) {
        mTime = theOther.mTime;
        mLowValue = theOther.mLowValue;
        mHighValue = theOther.mHighValue;
        mCurveType = theOther.mCurveType;
        mDistribution = theOther.mDistribution;
    }
}
