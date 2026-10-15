/*
 * Ported from src/Lawn/System/Music.h:73-80.
 *
 * Upstream enum MusicDrumsState : int32_t; represented as public static final int constants.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class MusicDrumsState {

    /** Ported from src/Lawn/System/Music.h:75. */
    public static final int MUSIC_DRUMS_OFF = 0;

    /** Ported from src/Lawn/System/Music.h:76. */
    public static final int MUSIC_DRUMS_ON_QUEUED = 1;

    /** Ported from src/Lawn/System/Music.h:77. */
    public static final int MUSIC_DRUMS_ON = 2;

    /** Ported from src/Lawn/System/Music.h:78. */
    public static final int MUSIC_DRUMS_OFF_QUEUED = 3;

    /** Ported from src/Lawn/System/Music.h:79. */
    public static final int MUSIC_DRUMS_FADING = 4;

    private MusicDrumsState() {
    }
}
