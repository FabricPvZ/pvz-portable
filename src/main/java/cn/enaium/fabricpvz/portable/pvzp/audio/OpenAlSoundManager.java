package cn.enaium.fabricpvz.portable.pvzp.audio;

import java.io.IOException;
import java.util.List;

import org.lwjgl.openal.AL10;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Ported from src/SexyAppFramework/sound/SDLSoundManager.cpp:27-506 (and SDLSoundManager.h:43-53 for
 * the member layout) — the SDL_mixer-X chunk manager replaced by OpenAL buffers and a fixed pool of
 * {@link SoundManager#MAX_CHANNELS} sources.
 *
 * <p>Faithful behaviour kept from upstream:
 * <ul>
 *   <li>Device/mixer parameters: 44100 Hz, signed 16-bit, stereo, 2048-frame buffers
 *       ({@code Mix_OpenAudio(44100, AUDIO_S16SYS, 2, 2048)}, SDLSoundManager.cpp:57); see
 *       {@link OpenAlDevice}.</li>
 *   <li>{@code LoadSound} probes {@code .wav}/{@code .mp3}/{@code .ogg}/{@code .au} and decodes from
 *       memory (SDLSoundManager.cpp:246-305); the probe list is extended with {@code .mo3} and the
 *       verbatim name and the format is sniffed from the content (see {@link AudioAssets}).</li>
 *   <li>When the mixer/device is unavailable, {@code LoadSound(id, name)} returns {@code true}
 *       without decoding and {@code GetSoundInstance} returns {@code null}
 *       (SDLSoundManager.cpp:255-256, :372-381) — sounds silently do nothing while decoding still
 *       works.</li>
 *   <li>An asset that exists but cannot be decoded (no known container, or its decoder rejects it)
 *       is logged and skipped: {@code LoadSound(id, name)} reports success and the slot keeps a
 *       {@code null} buffer, which is the same state the uninitialized-mixer case leaves behind. An
 *       asset that is not in the pak/files at all still returns {@code false}, so a resource the
 *       manifest names but nobody ships keeps failing the loading thread (docs/VERIFICATION.md §59).</li>
 *   <li>{@code LoadSound(name)} reuses the id whose file name matches, otherwise it scans
 *       <em>from the top of the array down</em> ({@code for (i = MAX_SOURCE_SOUNDS-1; i >= 0; i--)})
 *       for a free slot; {@code GetFreeSoundId} scans <em>forward</em>.</li>
 *   <li>Channel allocation is "no voice stealing": one voice per slot, slots are reused only when
 *       {@code isReleased()} reports true, a garbage sweep runs at most every 1000 ms
 *       ({@code SDL_GetTicks()}), and when all 32 slots are busy the sound is silently dropped
 *       (SDLSoundManager.cpp:476-506, :364-370).</li>
 *   <li>{@code setVolume(v)} is the SFX bus gain and re-hups every live voice; {@code
 *       setMasterVolume(v)} drives the SDL_mixer-wide master gain
 *       ({@code Mix_MasterVolume((int)(v*128))}), applied to the OpenAL listener so that it scales
 *       music as well, exactly like the upstream mixer-wide master volume.</li>
 * </ul>
 *
 * <p>PORT-NOTE: {@code ReleaseSound}/{@code ReleaseSounds} additionally stop the voices that are
 * currently playing the released buffer, because deleting an OpenAL buffer that is still attached to
 * a source is an {@code AL_INVALID_OPERATION} (upstream leaves the dangling {@code Mix_Chunk*} in
 * place). Deleting is still performed on every path, so no buffer leaks.
 */
public final class OpenAlSoundManager implements SoundManager, AutoCloseable {

    /**
     * Upstream channel entry: the decoded buffer plus the registers that SDL_mixer keeps on the
     * {@code Mix_Chunk} (SDLSoundManager.h:43-53).
     */
    static final class SoundBuffer {
        /** OpenAL buffer name; upstream {@code Mix_Chunk*}. */
        int mBuffer;
        String mFileName = "";
        /** {@code Mix_Chunk::volume}; shared by every voice playing this sound. */
        int mChunkVolume = AudioGain.MIX_MAX_VOLUME;
        int mChannels;
        int mSampleRate;
        int mFrames;
        boolean mReleased;
    }

    private final PakInterface mPakInterface;
    private final OpenAlDevice mDevice;
    private final boolean mOwnsDevice;
    private final DemoClock mDemoClock;

    final SoundBuffer[] mSourceSounds = new SoundBuffer[MAX_SOURCE_SOUNDS];
    final String[] mSourceFileNames = new String[MAX_SOURCE_SOUNDS];
    final double[] mBaseVolumes = new double[MAX_SOURCE_SOUNDS];
    final int[] mBasePans = new int[MAX_SOURCE_SOUNDS];
    final OpenAlSoundInstance[] mPlayingSounds = new OpenAlSoundInstance[MAX_CHANNELS];

    /** Upstream {@code SDLSoundManager::mMasterVolume} (the SFX bus gain). */
    double mMasterVolume = 1.0;

    /** Upstream {@code Mix_MasterVolume} register. */
    int mMixerMasterVolume = AudioGain.MIX_MAX_VOLUME;

    private long mLastReleaseTick;
    private boolean mClosed;

    /** Creates its own device (closed again by {@link #close()}). */
    public OpenAlSoundManager() {
        this(PakInterface.getInstance(), new OpenAlDevice(), DemoClock.NEVER, true);
    }

    /** Uses a device owned by the caller (e.g. shared with {@link OpenAlMusicInterface}). */
    public OpenAlSoundManager(PakInterface pakInterface, OpenAlDevice device) {
        this(pakInterface, device, DemoClock.NEVER, false);
    }

    /** Uses a caller-owned device and a demo clock (see {@link DemoClock}). */
    public OpenAlSoundManager(PakInterface pakInterface, OpenAlDevice device, DemoClock demoClock) {
        this(pakInterface, device, demoClock, false);
    }

    private OpenAlSoundManager(PakInterface pakInterface, OpenAlDevice device, DemoClock demoClock,
            boolean ownsDevice) {
        mPakInterface = pakInterface;
        mDevice = device;
        mDemoClock = demoClock;
        mOwnsDevice = ownsDevice;

        for (int i = 0; i < MAX_SOURCE_SOUNDS; i++) {
            mSourceFileNames[i] = "";
            mBaseVolumes[i] = 1;
            mBasePans[i] = 0;
        }
        mDevice.setMasterGain(AudioGain.mixerMasterGain(mMixerMasterVolume));
    }

    /** The shared device, so a music interface can be attached to the same context. */
    public OpenAlDevice device() {
        return mDevice;
    }

    /** Upstream {@code SDL_WasInit(SDL_INIT_AUDIO) && mInitializedMixer}. */
    @Override
    public boolean initialized() {
        return !mClosed && mDevice.available();
    }

    /** Upstream {@code SDL_GetTicks()} (milliseconds). */
    private static long ticks() {
        return System.nanoTime() / 1_000_000L;
    }

    boolean isInDemoMode() {
        return mDemoClock.isInDemoMode();
    }

    int demoUpdateCount() {
        return mDemoClock.updateCount();
    }

    @Override
    public boolean loadSound(long theSfxID, String theFilename) {
        if (theSfxID < 0 || theSfxID >= MAX_SOURCE_SOUNDS) {
            return false;
        }

        releaseSound(theSfxID);

        if (!initialized()) {
            return true;
        }

        mSourceFileNames[(int) theSfxID] = theFilename;

        List<AudioAssets.Loaded> candidates = readCandidates(theFilename);
        if (candidates.isEmpty()) {
            // Not a single probe candidate exists: a genuinely missing resource, which has to fail
            // like every other unresolvable resource (ResourceManager.doLoadSound).
            return false;
        }

        // The asset is there, so a container this port cannot decode is skipped (with a log line)
        // instead of aborting the loading thread — same treatment music already gets
        // (Music.loadSong logs "music failed to load" and carries on). See docs/VERIFICATION.md §59.
        mSourceSounds[(int) theSfxID] = decode(theFilename, candidates);
        return true;
    }

    /** Upstream {@code SDLSoundManager::DecodeSound} (SDLSoundManager.cpp:281-305). */
    private SoundBuffer decode(String theFilename, List<AudioAssets.Loaded> theCandidates) {
        for (AudioAssets.Loaded loaded : theCandidates) {
            if (loaded.format() == AudioFormat.UNKNOWN) {
                Common.logWarnLn("Skipping sound '{}': unrecognized audio container in {}", theFilename,
                        loaded.name());
                continue;
            }

            PcmSound pcm;
            try {
                pcm = loaded.format().decodeSound(loaded.data());
            } catch (IOException e) {
                // Upstream keeps probing the next format when Mix_LoadWAV_RW fails.
                Common.logWarnLn("Skipping sound '{}': {} decoder rejected {} ({})", theFilename,
                        loaded.format(), loaded.name(), e.getMessage());
                continue;
            }

            SoundBuffer buffer = new SoundBuffer();
            buffer.mBuffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer.mBuffer, OpenAlDevice.alFormat(pcm.channels()), pcm.samples(),
                    pcm.sampleRate());
            buffer.mFileName = loaded.name();
            buffer.mChannels = pcm.channels();
            buffer.mSampleRate = pcm.sampleRate();
            buffer.mFrames = pcm.frameCount();
            return buffer;
        }
        return null;
    }

    private List<AudioAssets.Loaded> readCandidates(String theFilename) {
        try {
            return AudioAssets.readAll(mPakInterface, theFilename);
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public long loadSound(String theFilename) {
        for (int i = 0; i < MAX_SOURCE_SOUNDS; i++) {
            if (theFilename.equals(mSourceFileNames[i])) {
                return i;
            }
        }

        for (int i = MAX_SOURCE_SOUNDS - 1; i >= 0; i--) {
            if (mSourceSounds[i] == null) {
                if (!loadSound(i, theFilename)) {
                    return -1;
                }
                return i;
            }
        }

        return -1;
    }

    @Override
    public void releaseSound(long theSfxID) {
        if (theSfxID < 0 || theSfxID >= MAX_SOURCE_SOUNDS) {
            return;
        }
        releaseBuffer(mSourceSounds[(int) theSfxID]);
        mSourceSounds[(int) theSfxID] = null;
        mSourceFileNames[(int) theSfxID] = "";
    }

    private void releaseBuffer(SoundBuffer buffer) {
        if (buffer == null) {
            return;
        }
        buffer.mReleased = true;
        for (OpenAlSoundInstance playing : mPlayingSounds) {
            if (playing != null && playing.usesBuffer(buffer)) {
                playing.stop();
            }
        }
        if (buffer.mBuffer != 0) {
            AL10.alDeleteBuffers(buffer.mBuffer);
            buffer.mBuffer = 0;
        }
    }

    @Override
    public void setVolume(double theVolume) {
        mMasterVolume = theVolume;

        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (mPlayingSounds[i] != null) {
                mPlayingSounds[i].rehupVolume();
            }
        }
    }

    @Override
    public boolean setBaseVolume(long theSfxID, double theBaseVolume) {
        if (theSfxID < 0 || theSfxID >= MAX_SOURCE_SOUNDS) {
            return false;
        }
        mBaseVolumes[(int) theSfxID] = theBaseVolume;
        return true;
    }

    @Override
    public boolean setBasePan(long theSfxID, int theBasePan) {
        if (theSfxID < 0 || theSfxID >= MAX_SOURCE_SOUNDS) {
            return false;
        }
        mBasePans[(int) theSfxID] = theBasePan;
        return true;
    }

    @Override
    public SoundInstance getSoundInstance(long theSfxID) {
        if (theSfxID < 0 || theSfxID >= MAX_SOURCE_SOUNDS) {
            return null;
        }

        int aFreeChannel = findFreeChannel();
        if (aFreeChannel < 0) {
            return null;
        }

        SoundBuffer sourceSound = mSourceSounds[(int) theSfxID];
        if (sourceSound == null || sourceSound.mReleased || sourceSound.mBuffer == 0) {
            return null;
        }

        OpenAlSoundInstance aSound = new OpenAlSoundInstance(this, sourceSound, aFreeChannel);
        mPlayingSounds[aFreeChannel] = aSound;

        aSound.setBasePan(mBasePans[(int) theSfxID]);
        aSound.setBaseVolume(mBaseVolumes[(int) theSfxID]);

        return aSound;
    }

    @Override
    public void releaseSounds() {
        for (int i = 0; i < MAX_SOURCE_SOUNDS; i++) {
            releaseBuffer(mSourceSounds[i]);
            mSourceSounds[i] = null;
            mSourceFileNames[i] = "";
        }
    }

    @Override
    public void releaseChannels() {
        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (mPlayingSounds[i] != null) {
                mPlayingSounds[i].close();
                mPlayingSounds[i] = null;
            }
        }
    }

    @Override
    public double getMasterVolume() {
        return AudioGain.mixerMasterGain(mMixerMasterVolume);
    }

    @Override
    public void setMasterVolume(double theVolume) {
        mMixerMasterVolume = AudioGain.mixerMasterVolume(theVolume, mMixerMasterVolume);
        mDevice.setMasterGain(AudioGain.mixerMasterGain(mMixerMasterVolume));
    }

    @Override
    public void flush() {
        // Upstream SDLSoundManager::Flush() is an empty body (SDLSoundManager.cpp:430-432).
    }

    @Override
    public void stopAllSounds() {
        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (mPlayingSounds[i] != null) {
                boolean isAutoRelease = mPlayingSounds[i].mAutoRelease;
                mPlayingSounds[i].stop();
                mPlayingSounds[i].mAutoRelease = isAutoRelease;
            }
        }
    }

    @Override
    public long getFreeSoundId() {
        for (int i = 0; i < MAX_SOURCE_SOUNDS; i++) {
            if (mSourceSounds[i] == null) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getNumSounds() {
        int aCount = 0;
        for (int i = 0; i < MAX_SOURCE_SOUNDS; i++) {
            if (mSourceSounds[i] != null) {
                aCount++;
            }
        }
        return aCount;
    }

    /** Upstream {@code SDLSoundManager::FindFreeChannel} (SDLSoundManager.cpp:476-506). */
    private int findFreeChannel() {
        long aTick = ticks();
        if (aTick - mLastReleaseTick > 1000) {
            releaseFreeChannels();
            mLastReleaseTick = aTick;
        }

        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (mPlayingSounds[i] == null) {
                return i;
            }
            if (mPlayingSounds[i].isReleased()) {
                mPlayingSounds[i].close();
                mPlayingSounds[i] = null;
                return i;
            }
        }

        return -1;
    }

    /** Upstream {@code SDLSoundManager::ReleaseFreeChannels} (SDLSoundManager.cpp:495-506). */
    private void releaseFreeChannels() {
        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (mPlayingSounds[i] != null && mPlayingSounds[i].isReleased()) {
                mPlayingSounds[i].close();
                mPlayingSounds[i] = null;
            }
        }
    }

    /**
     * Applies a changed shared chunk register to every voice currently playing that sound (upstream
     * {@code Mix_VolumeChunk} affects all channels using the chunk).
     */
    void rehupChunkVolume(SoundBuffer theBuffer) {
        for (int i = 0; i < MAX_CHANNELS; i++) {
            if (mPlayingSounds[i] != null && mPlayingSounds[i].usesBuffer(theBuffer)) {
                mPlayingSounds[i].applyGain();
            }
        }
    }

    @Override
    public void close() {
        if (mClosed) {
            return;
        }
        releaseChannels();
        releaseSounds();
        mClosed = true;
        if (mOwnsDevice) {
            mDevice.close();
        }
    }
}
