package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/Trail.h:51 (class TrailParams).
 */
public class TrailParams {

    public int mTrailType;
    public String mTrailFileName;

    public TrailParams() {
    }

    public TrailParams(int theTrailType, String theTrailFileName) {
        mTrailType = theTrailType;
        mTrailFileName = theTrailFileName;
    }
}
