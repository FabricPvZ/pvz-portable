/*
 * Ported from src/Lawn/System/Music.h:36-53.
 *
 * Upstream enum MusicTune : int32_t; represented as public static final int constants.
 * The -1 sentinel (MUSIC_TUNE_NONE) MUST be preserved because {@code Music.mCurMusicTune}
 * and the tune-selection tables compare against it.
 */
package cn.enaium.fabricpvz.portable.lawn;

public final class MusicTune {

    /** Ported from src/Lawn/System/Music.h:38. */
    public static final int MUSIC_TUNE_NONE = -1;

    /** Ported from src/Lawn/System/Music.h:39. */
    public static final int MUSIC_TUNE_DAY_GRASSWALK = 1;

    /** Ported from src/Lawn/System/Music.h:40. */
    public static final int MUSIC_TUNE_NIGHT_MOONGRAINS = 2;

    /** Ported from src/Lawn/System/Music.h:41. */
    public static final int MUSIC_TUNE_POOL_WATERYGRAVES = 3;

    /** Ported from src/Lawn/System/Music.h:42. */
    public static final int MUSIC_TUNE_FOG_RIGORMORMIST = 4;

    /** Ported from src/Lawn/System/Music.h:43. */
    public static final int MUSIC_TUNE_ROOF_GRAZETHEROOF = 5;

    /** Ported from src/Lawn/System/Music.h:44 (seed chooser and minigame screens). */
    public static final int MUSIC_TUNE_CHOOSE_YOUR_SEEDS = 6;

    /** Ported from src/Lawn/System/Music.h:45. */
    public static final int MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME = 7;

    /** Ported from src/Lawn/System/Music.h:46. */
    public static final int MUSIC_TUNE_ZEN_GARDEN = 8;

    /** Ported from src/Lawn/System/Music.h:47. */
    public static final int MUSIC_TUNE_PUZZLE_CEREBRAWL = 9;

    /** Ported from src/Lawn/System/Music.h:48. */
    public static final int MUSIC_TUNE_MINIGAME_LOONBOON = 10;

    /** Ported from src/Lawn/System/Music.h:49. */
    public static final int MUSIC_TUNE_CONVEYER = 11;

    /** Ported from src/Lawn/System/Music.h:50. */
    public static final int MUSIC_TUNE_FINAL_BOSS_BRAINIAC_MANIAC = 12;

    /** Ported from src/Lawn/System/Music.h:51. */
    public static final int MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN = 13;

    /** Ported from src/Lawn/System/Music.h:52. */
    public static final int NUM_MUSIC_TUNES = 14;

    private MusicTune() {
    }
}
