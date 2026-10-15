/*
 * Ported from src/Lawn/System/Music.h:55-63.
 *
 * Upstream enum MusicFile : int32_t; represented as public static final int constants.
 * The ids 1..4 are the keys of the concrete backend's song map
 * (upstream {@code SDLMusicInterface::mMusicMap}, an int-keyed map), so they MUST NOT change.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class MusicFile {

    /** Ported from src/Lawn/System/Music.h:57. */
    public static final int MUSIC_FILE_NONE = -1;

    /** Ported from src/Lawn/System/Music.h:58 (backed by sounds/mainmusic.mo3). */
    public static final int MUSIC_FILE_MAIN_MUSIC = 1;

    /**
     * Ported from src/Lawn/System/Music.h:59 (backed by sounds/mainmusic.mo3, a second
     * independent stream over the same file, Music.cpp:69).
     */
    public static final int MUSIC_FILE_DRUMS = 2;

    /**
     * Ported from src/Lawn/System/Music.h:60. Never loaded and never assigned (no load entry in
     * MUSIC_LOADING_FILES, so {@code Music.mCurMusicFileHihats} stays MUSIC_FILE_NONE).
     */
    public static final int MUSIC_FILE_HIHATS = 3;

    /** Ported from src/Lawn/System/Music.h:61 (backed by sounds/ZombiesOnYourLawn.ogg). */
    public static final int MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN = 4;

    /** Ported from src/Lawn/System/Music.h:62. */
    public static final int NUM_MUSIC_FILES = 5;

    private MusicFile() {
    }
}
