package cn.enaium.fabricpvz.portable.sexy.sound;

/**
 * Ported from src/SexyAppFramework/sound/MusicInterface.h:33 (class).
 *
 * <p>Abstract music (streamed song) interface. There are <b>no</b> {@code MUSIC_ID_*} constants
 * anywhere upstream; the song ids are the game-side {@code MusicFile} enum values cast to
 * {@code int} (see src/Lawn/System/Music.h:47-56). The game loads music from inside the pak, so a
 * concrete implementation must additionally accept raw asset bytes (upstream
 * {@code Music::PvzpLoadMusic} builds a {@code Mix_Music*} from memory and inserts it into
 * {@code SDLMusicInterface::mMusicMap} directly).
 *
 * <p>Upstream C++ default arguments are not expressible in Java, so callers must pass them
 * explicitly:
 * <ul>
 *   <li>{@code PlayMusic(id)} = {@code playMusic(id, 0, false)}</li>
 *   <li>{@code FadeIn(id)} = {@code fadeIn(id, -1, 0.002, false)}</li>
 *   <li>{@code FadeOut(id)} = {@code fadeOut(id, true, 0.004)}</li>
 *   <li>{@code FadeOutAll()} = {@code fadeOutAll(true, 0.004)}</li>
 * </ul>
 *
 * <p>{@code theOffset} is a tracker order index, not a time offset (upstream passes it to
 * {@code Mix_ModMusicStreamJumpToOrder}); it is ignored by non-tracker streams.
 */
public interface MusicInterface {

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:37. */
    boolean loadMusic(int theSongId, String theFileName);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:38. */
    void playMusic(int theSongId, int theOffset, boolean noLoop);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:39. */
    void stopMusic(int theSongId);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:40. */
    void pauseMusic(int theSongId);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:41. */
    void resumeMusic(int theSongId);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:42. */
    void stopAllMusic();

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:44. */
    void unloadMusic(int theSongId);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:45. */
    void unloadAllMusic();

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:46. */
    void pauseAllMusic();

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:47. */
    void resumeAllMusic();

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:49. */
    void fadeIn(int theSongId, int theOffset, double theSpeed, boolean noLoop);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:50. */
    void fadeOut(int theSongId, boolean stopSong, double theSpeed);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:51. */
    void fadeOutAll(boolean stopSong, double theSpeed);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:52. */
    void setSongVolume(int theSongId, double theVolume);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:53. */
    void setSongMaxVolume(int theSongId, double theMaxVolume);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:54. */
    boolean isPlaying(int theSongId);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:56. */
    void setVolume(double theVolume);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:57 (a no-op upstream). */
    void setMusicAmplify(int theSongId, double theAmp);

    /** Ported from src/SexyAppFramework/sound/MusicInterface.h:58. Called once per frame. */
    void update();

    /**
     * Upstream {@code SDLMusicInterface::GetMusicOrder} (src/SexyAppFramework/sound/SDLMusicInterface.cpp:343-355)
     * / {@code Mix_ModMusicStreamGetOrder}. Not part of the C++ base interface but required by the
     * game layer ({@code src/Lawn/System/Music.cpp:379}): returns the tracker order pack
     * ({@code (row << 16) | order}), or -1 for an unknown song id.
     */
    default int getMusicOrder(int theSongId) {
        return -1;
    }

    /**
     * Upstream {@code Mix_ModMusicStreamSetChannelVolume}, used by
     * {@code Music::SetupVolumeForTune} (src/Lawn/System/Music.cpp:118-173) to mix the 30 tracker
     * channels of {@code mainmusic.mo3} into logical stems.
     *
     * @param theVolume channel volume in {@code MIX_MAX_VOLUME} units (0..128); the upstream call
     *     site passes {@code (int)(volume * 128)}. No-op for backends without tracker channels.
     */
    default void setChannelVolume(int theSongId, int theTrack, double theVolume) {
    }
}
