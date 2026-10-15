package cn.enaium.fabricpvz.portable.pvzp.fx;

import cn.enaium.fabricpvz.portable.pvzp.DataArray;
import cn.enaium.fabricpvz.portable.pvzp.Definition;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;

/**
 * Ported from src/PvzpLib/Trail.h:107 (class TrailHolder) and src/PvzpLib/Trail.cpp:271-303.
 */
public class TrailHolder {

    public DataArray<Trail> mTrails = new DataArray<>(Trail::new);

    /** Ported from src/PvzpLib/Trail.cpp:271. */
    public void initializeHolder() {
        mTrails.dataArrayInitialize(1024, "trails");
    }

    /** Ported from src/PvzpLib/Trail.cpp:276. */
    public void disposeHolder() {
        mTrails.dataArrayDispose();
    }

    /** Ported from src/PvzpLib/Trail.cpp:281. */
    public Trail allocTrail(int theRenderOrder, int theTrailType) {
        return allocTrailFromDef(theRenderOrder, TrailDefinitions.gTrailDefArray[theTrailType]);
    }

    /** Ported from src/PvzpLib/Trail.cpp:287. */
    public Trail allocTrailFromDef(int theRenderOrder, TrailDefinition theDefinition) {
        if (mTrails.mSize == mTrails.mMaxSize) {
            return null;
        }

        Trail aTrail = mTrails.dataArrayAlloc();
        aTrail.mTrailHolder = this;
        aTrail.mDefinition = theDefinition;

        for (int i = 0; i < TrailTracks.NUM_TRAIL_TRACKS; i++) {
            aTrail.mTrailInterp[i] = PvzpCommon.randRangeFloat(0.0f, 1.0f);
        }
        float aDurationInterp = PvzpCommon.randRangeFloat(0.0f, 1.0f);
        aTrail.mTrailDuration = (int) Definition.floatTrackEvaluate(aTrail.mDefinition.mTrailDuration, 0.0f, aDurationInterp);
        return aTrail;
    }
}
