/*
 * Ported from src/Lawn/System/Music.h:65-71.
 *
 * Upstream enum MusicBurstState : int32_t; represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class MusicBurstState {

    /** Ported from src/Lawn/System/Music.h:67. */
    public static final int MUSIC_BURST_OFF = 0;

    /** Ported from src/Lawn/System/Music.h:68. */
    public static final int MUSIC_BURST_STARTING = 1;

    /** Ported from src/Lawn/System/Music.h:69. */
    public static final int MUSIC_BURST_ON = 2;

    /** Ported from src/Lawn/System/Music.h:70. */
    public static final int MUSIC_BURST_FINISHING = 3;

    private MusicBurstState() {
    }
}
