package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/Trail.h:37 (enum TrailTracks).
 *
 * <p>Indices into {@code Trail.mTrailInterp}.
 */
public final class TrailTracks {
    private TrailTracks() {
    }

    public static final int TRACK_WIDTH_OVER_LENGTH = 0;
    public static final int TRACK_WIDTH_OVER_TIME = 1;
    public static final int TRACK_ALPHA_OVER_LENGTH = 2;
    public static final int TRACK_ALPHA_OVER_TIME = 3;
    public static final int NUM_TRAIL_TRACKS = 4;
}
