package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpParticle.h:205 (enum ParticleSystemTracks).
 *
 * <p>Indices into {@code PvzpParticleEmitter.mTrackInterp}.
 */
public final class ParticleSystemTracks {
    private ParticleSystemTracks() {
    }

    public static final int TRACK_SPAWN_RATE = 0;
    public static final int TRACK_SPAWN_MIN_ACTIVE = 1;
    public static final int TRACK_SPAWN_MAX_ACTIVE = 2;
    public static final int TRACK_SPAWN_MAX_LAUNCHED = 3;
    public static final int TRACK_EMITTER_PATH = 4;
    public static final int TRACK_SYSTEM_RED = 5;
    public static final int TRACK_SYSTEM_GREEN = 6;
    public static final int TRACK_SYSTEM_BLUE = 7;
    public static final int TRACK_SYSTEM_ALPHA = 8;
    public static final int TRACK_SYSTEM_BRIGHTNESS = 9;
    public static final int NUM_SYSTEM_TRACKS = 10;
}
