package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.DefFieldType;
import cn.enaium.fabricpvz.portable.pvzp.DefMap;
import cn.enaium.fabricpvz.portable.pvzp.DefMapBuilder;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;

/**
 * Ported from src/PvzpLib/Trail.cpp:34 (the {@code gLawnTrailArray} table) and :42-82 (the
 * {@code gTrailDefMap} plus {@code TrailLoadADef}/{@code TrailLoadDefinitions}/{@code TrailFreeDefinitions}).
 */
public final class TrailDefinitions {

    public static int gTrailDefCount;
    public static TrailDefinition[] gTrailDefArray;
    public static int gTrailParamArraySize;
    public static TrailParams[] gTrailParamArray;

    private TrailDefinitions() {
    }

    /** Ported from src/PvzpLib/Definition.cpp:47 ({@code gTrailDefMap}). */
    public static final DefMap gTrailDefMap = new DefMapBuilder()
            .field("Image", "mImage", DefFieldType.DT_IMAGE)
            .field("MaxPoints", "mMaxPoints", DefFieldType.DT_INT)
            .field("MinPointDistance", "mMinPointDistance", DefFieldType.DT_FLOAT)
            .field("TrailFlags", "mTrailFlags", DefFieldType.DT_FLAGS, Definition.gTrailFlagDefSymbols)
            .field("WidthOverLength", "mWidthOverLength", DefFieldType.DT_TRACK_FLOAT)
            .field("WidthOverTime", "mWidthOverTime", DefFieldType.DT_TRACK_FLOAT)
            .field("AlphaOverLength", "mAlphaOverLength", DefFieldType.DT_TRACK_FLOAT)
            .field("AlphaOverTime", "mAlphaOverTime", DefFieldType.DT_TRACK_FLOAT)
            .field("TrailDuration", "mTrailDuration", DefFieldType.DT_TRACK_FLOAT)
            .build(TrailDefinition::trailDefinitionConstructor);

    /** Ported from src/PvzpLib/Trail.cpp:34 ({@code gLawnTrailArray}). */
    public static final TrailParams[] gLawnTrailArray = {
            new TrailParams(TrailType.TRAIL_ICE, "particles/IceTrail.trail"),
    };

    /** Ported from src/PvzpLib/Trail.cpp:42 ({@code TrailLoadADef}). */
    public static boolean trailLoadADef(TrailDefinition theTrailDef, String theTrailFileName) {
        if (!Definition.loadXML(theTrailFileName, gTrailDefMap, theTrailDef)) {
            return false;
        }

        Definition.floatTrackSetDefault(theTrailDef.mWidthOverLength, 1.0f);
        Definition.floatTrackSetDefault(theTrailDef.mWidthOverTime, 1.0f);
        Definition.floatTrackSetDefault(theTrailDef.mTrailDuration, 100.0f);
        Definition.floatTrackSetDefault(theTrailDef.mAlphaOverLength, 1.0f);
        Definition.floatTrackSetDefault(theTrailDef.mAlphaOverTime, 1.0f);
        return true;
    }

    /** Ported from src/PvzpLib/Trail.cpp:57 ({@code TrailLoadDefinitions}). */
    public static void trailLoadDefinitions(TrailParams[] theTrailParamArray, int theTrailParamArraySize) {
        gTrailParamArraySize = theTrailParamArraySize;
        gTrailParamArray = theTrailParamArray;
        gTrailDefCount = theTrailParamArraySize;
        gTrailDefArray = new TrailDefinition[theTrailParamArraySize];
        for (int i = 0; i < theTrailParamArraySize; i++) {
            // C++ `new TrailDefinition[n]` default-constructs every element; Java arrays start null.
            gTrailDefArray[i] = new TrailDefinition();
        }

        for (int i = 0; i < gTrailParamArraySize; i++) {
            TrailParams aTrailParams = gTrailParamArray[i];
            if (!trailLoadADef(gTrailDefArray[i], aTrailParams.mTrailFileName)) {
                PvzpDebug.pvzpErrorMessageBox("Failed to load trail '" + aTrailParams.mTrailFileName + "'", "Error");
            }
        }
    }

    /** Ported from src/PvzpLib/Trail.cpp:72 ({@code TrailFreeDefinitions}). */
    public static void trailFreeDefinitions() {
        for (int i = 0; i < gTrailDefCount; i++) {
            Definition.freeMap(gTrailDefMap, gTrailDefArray[i]);
        }
        gTrailDefArray = null;
        gTrailDefCount = 0;
        gTrailParamArray = null;
        gTrailParamArraySize = 0;
    }
}
