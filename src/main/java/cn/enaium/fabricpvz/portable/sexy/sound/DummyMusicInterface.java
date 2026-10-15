package cn.enaium.fabricpvz.portable.sexy.sound;

/**
 * Ported from src/SexyAppFramework/sound/DummyMusicInterface.h:31 (class, header-only upstream).
 *
 * <p>Null music backend: {@code loadMusic} and {@code isPlaying} return {@code false}, everything
 * else is a no-op. Selected by {@code SexyAppBase::CreateMusicInterface()} when
 * {@code SexyAppBase::mNoSoundNeeded} is true (src/SexyAppFramework/SexyAppBase.cpp:3338-3344).
 */
public class DummyMusicInterface implements MusicInterface {

    @Override
    public boolean loadMusic(int theSongId, String theFileName) {
        return false;
    }

    @Override
    public void playMusic(int theSongId, int theOffset, boolean noLoop) {
    }

    @Override
    public void stopMusic(int theSongId) {
    }

    @Override
    public void pauseMusic(int theSongId) {
    }

    @Override
    public void resumeMusic(int theSongId) {
    }

    @Override
    public void stopAllMusic() {
    }

    @Override
    public void unloadMusic(int theSongId) {
    }

    @Override
    public void unloadAllMusic() {
    }

    @Override
    public void pauseAllMusic() {
    }

    @Override
    public void resumeAllMusic() {
    }

    @Override
    public void fadeIn(int theSongId, int theOffset, double theSpeed, boolean noLoop) {
    }

    @Override
    public void fadeOut(int theSongId, boolean stopSong, double theSpeed) {
    }

    @Override
    public void fadeOutAll(boolean stopSong, double theSpeed) {
    }

    @Override
    public void setSongVolume(int theSongId, double theVolume) {
    }

    @Override
    public void setSongMaxVolume(int theSongId, double theMaxVolume) {
    }

    @Override
    public boolean isPlaying(int theSongId) {
        return false;
    }

    @Override
    public void setVolume(double theVolume) {
    }

    @Override
    public void setMusicAmplify(int theSongId, double theAmp) {
    }

    @Override
    public void update() {
    }
}
