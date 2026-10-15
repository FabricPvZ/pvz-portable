package cn.enaium.fabricpvz.portable.pvzp.fx;

/**
 * Ported from src/PvzpLib/PvzpFoley.h:35 (enum FoleyFlags).
 *
 * <p>Each enumerator is a bit index into {@code FoleyParams.mFoleyFlags}.
 */
public final class FoleyFlags {
    private FoleyFlags() {
    }

    public static final int FOLEYFLAGS_LOOP = 0;
    public static final int FOLEYFLAGS_ONE_AT_A_TIME = 1;
    public static final int FOLEYFLAGS_MUTE_ON_PAUSE = 2;
    public static final int FOLEYFLAGS_USES_MUSIC_VOLUME = 3;
    public static final int FOLEYFLAGS_DONT_REPEAT = 4;
}
