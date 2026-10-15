package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/PvzpLib/Trail.h:63 (class TrailDefinition).
 *
 * <p>Defaults are the upstream member initialisers ({@code mMaxPoints=2},
 * {@code mMinPointDistance=1.0f}); the five float tracks are defaulted to CURVE_CONSTANT tracks by
 * {@code TrailLoadADef} after loading.
 */
public class TrailDefinition {

    public Image mImage = null;
    public int mMaxPoints = 2;
    public float mMinPointDistance = 1.0f;
    public int mTrailFlags = 0;
    public FloatParameterTrack mTrailDuration = new FloatParameterTrack();
    public FloatParameterTrack mWidthOverLength = new FloatParameterTrack();
    public FloatParameterTrack mWidthOverTime = new FloatParameterTrack();
    public FloatParameterTrack mAlphaOverLength = new FloatParameterTrack();
    public FloatParameterTrack mAlphaOverTime = new FloatParameterTrack();

    /** Ported from src/PvzpLib/Definition.cpp (TrailDefinitionConstructor equivalent). */
    public static TrailDefinition trailDefinitionConstructor() {
        return new TrailDefinition();
    }
}
