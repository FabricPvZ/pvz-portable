package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface;

/**
 * Ported from src/SexyAppFramework/sound/SDLMusicInterface.cpp:29-355 (with the member layout of
 * SDLMusicInterface.h:40-63) — the SDL_mixer-X streamed-music backend replaced by one OpenAL source
 * per loaded song plus a background feeder thread.
 *
 * <p>Exact upstream semantics kept:
 * <ul>
 *   <li>The per-song registers and the fade integrator live in {@link MusicInfo}
 *       ({@link #update()} drives them once per frame, like {@code SexyAppBase::UpdateFrames} does
 *       with {@code mMusicInterface->Update()}).</li>
 *   <li>{@code playMusic}/{@code fadeIn} reset {@code mVolumeAdd = 0} (play) or arm
 *       {@code mVolumeAdd} (fade in, which does <b>not</b> reset {@code mVolume} — upstream ramps
 *       from whatever volume the song currently has), halt the stream, restart it from the
 *       beginning and jump to {@code theOffset} only when it is {@code > 0}.</li>
 *   <li>{@code stopMusic} halts <em>all</em> music first ({@code Mix_HaltMusic()}) and then zeroes
 *       the requested song's volume; {@code stopAllMusic} zeroes every entry.</li>
 *   <li>{@code fadeOut} arms a negative ramp only when {@code mVolume != 0.0}; {@code fadeOutAll}
 *       arms it unconditionally.</li>
 *   <li>{@code setSongMaxVolume} clamps the current volume down to the new cap and the cap becomes
 *       the fade ceiling; {@code setVolume} only caches {@code (int)(theVolume * 80)} (coefficient
 *       {@value AudioGain#MUSIC_GLOBAL_COEFFICIENT}) and the value is applied with the next
 *       {@link #update()}, exactly like {@code Mix_VolumeMusic}/{@code Mix_VolumeMusicGeneral}.</li>
 *   <li>{@code setMusicAmplify} is a no-op (SDLMusicInterface.cpp:299-302).</li>
 *   <li>{@code loadMusic} keeps the first handle for a song id, mirroring
 *       {@code std::map::insert} (SDLMusicInterface.cpp:68).</li>
 * </ul>
 *
 * <p>PORT-NOTE (pack-aware loading): upstream's {@code LoadMusic} uses {@code Mix_LoadMUS} on a disk
 * path and the game bypasses it by inserting a pak-loaded handle into {@code mMusicMap}
 * ({@code Music::PvzpLoadMusic}, src/Lawn/System/Music.cpp:75-115). This port reads through
 * {@link PakInterface} and sniffs the container, so either loading style works; {@link
 * #loadMusicData} is the direct equivalent of the in-memory game path.
 *
 * <p>PORT-NOTE (streaming): a tracker module is up to ~190 s of stereo PCM
 * ({@code sounds/mainmusic.mo3} ≈ 33 MB decoded), so songs are streamed: the loaded
 * {@link AudioStream} is pumped into {@value #QUEUED_BUFFERS} OpenAL buffers of
 * {@link OpenAlDevice#BUFFER_FRAMES} frames by a daemon feeder thread (upstream lets the SDL_mixer
 * audio callback decode on its own thread), while volume ramping stays {@link #update()}-driven.
 * {@code Mix_ModMusicStreamJumpToOrder} maps to {@link AudioStream#seekOrder(int)} and
 * {@code Mix_ModMusicStreamSetChannelVolume} to {@link AudioStream#setChannelVolume(int, double)}.
 */
public final class OpenAlMusicInterface implements MusicInterface, AutoCloseable {

    /** Number of OpenAL buffers queued per song (≈ 186 ms of PCM at 44100 Hz). */
    private static final int QUEUED_BUFFERS = 4;

    /** A loaded song: the upstream {@code SDLMusicInfo} registers plus the OpenAL stream state. */
    private static final class Song {
        final MusicInfo info = new MusicInfo();
        AudioStream stream;
        int source;
        final int[] buffers = new int[QUEUED_BUFFERS];
        final Deque<Integer> free = new ArrayDeque<>(QUEUED_BUFFERS);
        java.nio.ShortBuffer pcm;
        short[] scratch;
        int queued;
        boolean startRequested;
        boolean paused;
        boolean readEnded;
        boolean ended;
    }

    private final PakInterface mPakInterface;
    private final OpenAlDevice mDevice;
    private final boolean mOwnsDevice;

    /** Upstream {@code SDLMusicMap mMusicMap} + {@code std::recursive_mutex mMusicMapMutex}. */
    private final Map<Integer, Song> mMusicMap = new LinkedHashMap<>();
    private final Object mMusicMapMutex = new Object();

    /** Upstream {@code mGlobalVolume} = {@code (int)(volume * 80)}. */
    int mGlobalVolume = AudioGain.MIX_MAX_VOLUME;
    private boolean mGlobalVolumeDirty = true;

    private Thread mFeeder;
    private boolean mClosed;

    /** Creates its own device (closed again by {@link #close()}). */
    public OpenAlMusicInterface() {
        this(PakInterface.getInstance(), new OpenAlDevice(), true);
    }

    /** Uses a device owned by the caller (e.g. shared with {@link OpenAlSoundManager}). */
    public OpenAlMusicInterface(PakInterface pakInterface, OpenAlDevice device) {
        this(pakInterface, device, false);
    }

    private OpenAlMusicInterface(PakInterface pakInterface, OpenAlDevice device, boolean ownsDevice) {
        mPakInterface = pakInterface;
        mDevice = device;
        mOwnsDevice = ownsDevice;
    }

    /** The shared device, so a sound manager can be attached to the same context. */
    public OpenAlDevice device() {
        return mDevice;
    }

    @Override
    public boolean loadMusic(int theSongId, String theFileName) {
        if (theFileName == null || theFileName.isEmpty()) {
            return false;
        }
        try {
            return loadMusicInternal(theSongId, theFileName, null);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Equivalent of {@code Music::PvzpLoadMusic}'s in-memory path (src/Lawn/System/Music.cpp:75-115,
     * {@code Mix_LoadMUS_RW(SDL_RWFromMem(...), 1)}): loads a song from pak bytes that the caller
     * already holds.
     */
    public boolean loadMusicData(int theSongId, byte[] data, String theFileName) {
        if (data == null || data.length == 0) {
            return false;
        }
        try {
            return loadMusicInternal(theSongId, theFileName, data);
        } catch (IOException e) {
            return false;
        }
    }

    private boolean loadMusicInternal(int theSongId, String theFileName, byte[] data) throws IOException {
        if (!mDevice.available()) {
            return false;
        }

        AudioStream stream;
        if (data != null) {
            AudioFormat format = AudioFormat.sniff(data);
            if (format == AudioFormat.UNKNOWN) {
                return false;
            }
            stream = format.openStream(data);
        } else {
            stream = null;
            for (AudioAssets.Loaded loaded : AudioAssets.readAll(mPakInterface, theFileName)) {
                try {
                    stream = loaded.format().openStream(loaded.data());
                    break;
                } catch (IOException e) {
                    // Try the next probe candidate, like the sound path.
                }
            }
            if (stream == null) {
                return false;
            }
        }

        synchronized (mMusicMapMutex) {
            if (mClosed) {
                stream.close();
                return false;
            }
            if (mMusicMap.containsKey(theSongId)) {
                // Upstream std::map::insert keeps the existing entry (and leaks the new handle);
                // the port closes the redundant stream instead.
                stream.close();
                return true;
            }

            Song song = new Song();
            song.stream = stream;
            song.source = AL10.alGenSources();
            AL10.alSourcef(song.source, AL10.AL_PITCH, 1.0f);
            for (int i = 0; i < QUEUED_BUFFERS; i++) {
                song.buffers[i] = AL10.alGenBuffers();
                song.free.add(song.buffers[i]);
            }
            song.pcm = MemoryUtil.memAllocShort(OpenAlDevice.BUFFER_FRAMES * stream.channels());
            song.scratch = new short[OpenAlDevice.BUFFER_FRAMES * stream.channels()];

            mMusicMap.put(theSongId, song);
            ensureFeeder();
        }
        return true;
    }

    @Override
    public void playMusic(int theSongId, int theOffset, boolean noLoop) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song == null) {
                return;
            }
            song.info.mVolume = song.info.mVolumeCap;
            song.info.mVolumeAdd = 0.0;
            song.info.mStopOnFade = noLoop;

            haltStream(song);
            startStream(song, theOffset, !noLoop);
        }
    }

    @Override
    public void stopMusic(int theSongId) {
        synchronized (mMusicMapMutex) {
            for (Song song : mMusicMap.values()) {
                haltStream(song);
            }
            Song song = mMusicMap.get(theSongId);
            if (song != null) {
                song.info.mVolume = 0.0;
                haltStream(song);
            }
        }
    }

    @Override
    public void pauseMusic(int theSongId) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song != null) {
                pauseStream(song);
            }
        }
    }

    @Override
    public void resumeMusic(int theSongId) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song != null) {
                resumeStream(song);
            }
        }
    }

    @Override
    public void stopAllMusic() {
        synchronized (mMusicMapMutex) {
            for (Song song : mMusicMap.values()) {
                song.info.mVolume = 0.0;
                haltStream(song);
            }
        }
    }

    @Override
    public void unloadMusic(int theSongId) {
        synchronized (mMusicMapMutex) {
            stopMusic(theSongId);
            Song song = mMusicMap.remove(theSongId);
            if (song != null) {
                freeSong(song);
            }
        }
    }

    @Override
    public void unloadAllMusic() {
        synchronized (mMusicMapMutex) {
            stopAllMusic();
            for (Song song : mMusicMap.values()) {
                freeSong(song);
            }
            mMusicMap.clear();
        }
    }

    @Override
    public void pauseAllMusic() {
        synchronized (mMusicMapMutex) {
            for (Song song : mMusicMap.values()) {
                if (streamIsPlaying(song)) {
                    pauseStream(song);
                }
            }
        }
    }

    @Override
    public void resumeAllMusic() {
        synchronized (mMusicMapMutex) {
            for (Song song : mMusicMap.values()) {
                if (song.paused) {
                    resumeStream(song);
                }
            }
        }
    }

    @Override
    public void fadeIn(int theSongId, int theOffset, double theSpeed, boolean noLoop) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song == null) {
                return;
            }
            song.info.mVolumeAdd = theSpeed;
            song.info.mStopOnFade = noLoop;

            haltStream(song);
            startStream(song, theOffset, !noLoop);
        }
    }

    @Override
    public void fadeOut(int theSongId, boolean stopSong, double theSpeed) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song == null) {
                return;
            }
            if (song.info.mVolume != 0.0) {
                song.info.mVolumeAdd = -theSpeed;
            }
            song.info.mStopOnFade = stopSong;
        }
    }

    @Override
    public void fadeOutAll(boolean stopSong, double theSpeed) {
        synchronized (mMusicMapMutex) {
            for (Song song : mMusicMap.values()) {
                song.info.mVolumeAdd = -theSpeed;
                song.info.mStopOnFade = stopSong;
            }
        }
    }

    @Override
    public void setSongVolume(int theSongId, double theVolume) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song == null) {
                return;
            }
            song.info.mVolume = theVolume;
            applyGain(song);
        }
    }

    @Override
    public void setSongMaxVolume(int theSongId, double theMaxVolume) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song == null) {
                return;
            }
            song.info.mVolumeCap = theMaxVolume;
            song.info.mVolume = Math.min(song.info.mVolume, theMaxVolume);
            applyGain(song);
        }
    }

    @Override
    public boolean isPlaying(int theSongId) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            return song != null && streamIsPlaying(song);
        }
    }

    @Override
    public void setVolume(double theVolume) {
        synchronized (mMusicMapMutex) {
            mGlobalVolume = AudioGain.musicGeneralVolume(theVolume);
            mGlobalVolumeDirty = true;
        }
    }

    @Override
    public void setMusicAmplify(int theSongId, double theAmp) {
        // Upstream SDLMusicInterface::SetMusicAmplify() is an empty body (SDLMusicInterface.cpp:299-302).
    }

    @Override
    public void update() {
        synchronized (mMusicMapMutex) {
            if (mClosed) {
                return;
            }
            boolean globalDirty = mGlobalVolumeDirty;
            mGlobalVolumeDirty = false;

            for (Song song : mMusicMap.values()) {
                MusicInfo.StepResult result = song.info.step();

                if (result != MusicInfo.StepResult.UNCHANGED || globalDirty) {
                    applyGain(song);
                }
                if (result == MusicInfo.StepResult.APPLY_AND_HALT) {
                    haltStream(song);
                }
            }

            mMusicMapMutex.notifyAll();
        }
    }

    @Override
    public int getMusicOrder(int theSongId) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            return song == null ? -1 : song.stream.currentOrder();
        }
    }

    @Override
    public void setChannelVolume(int theSongId, int theTrack, double theVolume) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song != null) {
                song.stream.setChannelVolume(theTrack, theVolume);
            }
        }
    }

    /** Closes the feeder thread and unloads every song. */
    @Override
    public void close() {
        synchronized (mMusicMapMutex) {
            if (mClosed) {
                return;
            }
            mClosed = true;
            for (Song song : mMusicMap.values()) {
                haltStream(song);
                freeSong(song);
            }
            mMusicMap.clear();
            mMusicMapMutex.notifyAll();
        }
        if (mFeeder != null) {
            try {
                mFeeder.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (mOwnsDevice) {
            mDevice.close();
        }
    }

    // ------------------------------------------------------------------
    // Stream control
    // ------------------------------------------------------------------

    /** Upstream {@code Mix_HaltMusicStream}: stop the source and drop everything that was queued. */
    private void haltStream(Song song) {
        AL10.alSourceStop(song.source);
        int queued = AL10.alGetSourcei(song.source, AL11.AL_BUFFERS_QUEUED);
        while (queued-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(song.source);
            if (buffer != 0) {
                song.free.add(buffer);
            }
        }
        song.queued = 0;
        song.startRequested = false;
        song.paused = false;
        song.readEnded = false;
        song.ended = false;
    }

    private void startStream(Song song, int theOffset, boolean looping) {
        song.stream.setLooping(looping);
        song.stream.rewind();
        if (theOffset > 0) {
            song.stream.seekOrder(theOffset);
        }
        song.startRequested = true;
        song.paused = false;
        song.readEnded = false;
        song.ended = false;
        applyGain(song);
        mMusicMapMutex.notifyAll();
    }

    private void pauseStream(Song song) {
        if (song.startRequested) {
            AL10.alSourcePause(song.source);
        }
        song.paused = true;
    }

    private void resumeStream(Song song) {
        song.paused = false;
        if (song.startRequested && song.queued > 0 && !song.ended) {
            AL10.alSourcePlay(song.source);
        }
        mMusicMapMutex.notifyAll();
    }

    private void freeSong(Song song) {
        song.stream.close();
        AL10.alDeleteSources(song.source);
        song.source = 0;
        for (int buffer : song.buffers) {
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
        }
        if (song.pcm != null) {
            MemoryUtil.memFree(song.pcm);
            song.pcm = null;
        }
    }

    /** {@code Mix_PlayingMusicStream}: true while the stream plays, false when paused/ended/unknown. */
    private boolean streamIsPlaying(Song song) {
        return song.startRequested && !song.paused && !song.ended;
    }

    /**
     * Observability hook: the {@code AL_GAIN} currently applied to a loaded song's source (0 when the
     * song id is unknown). The value is {@code generalVolume * streamVolume / 128²}, i.e. the
     * upstream {@code Mix_VolumeMusicGeneral * Mix_VolumeMusicStream} pair folded into one gain.
     */
    float sourceGain(int theSongId) {
        synchronized (mMusicMapMutex) {
            Song song = mMusicMap.get(theSongId);
            if (song == null || song.source == 0) {
                return 0f;
            }
            return AL10.alGetSourcef(song.source, AL10.AL_GAIN);
        }
    }

    private void applyGain(Song song) {
        int streamVolume = AudioGain.musicStreamVolume(song.info.mVolume);
        AL10.alSourcef(song.source, AL10.AL_GAIN, AudioGain.musicSourceGain(mGlobalVolume, streamVolume));
    }

    // ------------------------------------------------------------------
    // Feeder thread (replaces the SDL_mixer audio callback decoding music)
    // ------------------------------------------------------------------

    private void ensureFeeder() {
        if (mFeeder == null) {
            mFeeder = new Thread(this::feederLoop, "PvZ-Music-Feeder");
            mFeeder.setDaemon(true);
            mFeeder.start();
        }
    }

    private void feederLoop() {
        mDevice.attachThread();
        while (true) {
            boolean active;
            synchronized (mMusicMapMutex) {
                if (mClosed) {
                    return;
                }
                active = pumpSongs();
                if (!active) {
                    try {
                        mMusicMapMutex.wait(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (active) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private boolean pumpSongs() {
        boolean anyActive = false;
        for (Song song : mMusicMap.values()) {
            if (!song.startRequested || song.paused || song.ended) {
                continue;
            }
            anyActive = true;
            refill(song);
        }
        return anyActive;
    }

    private void refill(Song song) {
        int processed = AL10.alGetSourcei(song.source, AL11.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(song.source);
            if (buffer != 0) {
                song.free.add(buffer);
                song.queued--;
            }
        }

        int channels = song.stream.channels();
        while (song.queued < QUEUED_BUFFERS && !song.readEnded) {
            Integer buffer = song.free.poll();
            if (buffer == null) {
                break;
            }

            int frames = song.stream.read(song.scratch, OpenAlDevice.BUFFER_FRAMES);
            if (frames <= 0) {
                song.readEnded = true;
                song.free.addFirst(buffer);
                break;
            }

            song.pcm.clear();
            song.pcm.limit(frames * channels);
            song.pcm.put(song.scratch, 0, frames * channels);
            song.pcm.flip();

            AL10.alBufferData(buffer, OpenAlDevice.alFormat(channels), song.pcm, song.stream.sampleRate());
            AL10.alSourceQueueBuffers(song.source, buffer);
            song.queued++;
        }

        if (song.queued > 0 && !song.paused
                && AL10.alGetSourcei(song.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(song.source);
        }
        if (song.readEnded && song.queued == 0) {
            song.ended = true;
        }
    }
}
