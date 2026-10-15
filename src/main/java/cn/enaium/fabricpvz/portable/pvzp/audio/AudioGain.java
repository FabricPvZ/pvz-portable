package cn.enaium.fabricpvz.portable.pvzp.audio;

/**
 * Pure gain/volume helpers extracted verbatim from the upstream SDL-Mixer-X backends, so that the
 * exact integer-truncating scaling tables can be unit tested without an audio device.
 *
 * <p>Ported from src/SexyAppFramework/sound/SDLSoundInstance.cpp:60-63 and :189-192,
 * src/SexyAppFramework/sound/SDLSoundManager.cpp:420-428 and
 * src/SexyAppFramework/sound/SDLMusicInterface.cpp:252-302 and :304-341.
 *
 * <p>All registers are kept in {@code MIX_MAX_VOLUME} (= {@code SDL_MIX_MAXVOLUME} = 128) units,
 * exactly like upstream, and translated to OpenAL linear gains by dividing by 128.
 *
 * <p>Sources (all {@code PvZ-Portable-Cpp/src/SexyAppFramework/sound/}):
 * <ul>
 *   <li>{@code SDLSoundInstance.cpp:60-63} — chunk gain:
 *       {@code Mix_VolumeChunk(chunk, (mBaseVolume * mVolume * mMasterVolume) * 128)}; the cast to
 *       {@code int} truncates, negative results are ignored by {@code Mix_VolumeChunk} and values
 *       above 128 are clamped (SDL-Mixer-X {@code src/mixer.c:1311-1325}).</li>
 *   <li>{@code SDLSoundManager.cpp:420-428} — {@code GetMasterVolume() = Mix_MasterVolume(-1)/128}
 *       and {@code SetMasterVolume(v) = Mix_MasterVolume((int)(v*128))}.</li>
 *   <li>{@code SDLMusicInterface.cpp:296} — the music global gain coefficient is {@code 80}, not
 *       128: {@code mGlobalVolume = (int)(theVolume*80)}.</li>
 *   <li>{@code SDLMusicInterface.cpp:252-283} — per-song volume register
 *       {@code Mix_VolumeMusicStream(handle, (int)(mVolume*128))}.</li>
 *   <li>{@code SDLSoundInstance.cpp:189-192} — {@code AdjustPitch} is an equal-tempered playback
 *       rate multiplier: {@code powf(1.0594630943592952645618252949463, steps)}.</li>
 * </ul>
 */
public final class AudioGain {

    /** {@code MIX_MAX_VOLUME}/{@code SDL_MIX_MAXVOLUME}. */
    public static final int MIX_MAX_VOLUME = 128;

    /** Upstream {@code SDLMusicInterface.cpp:296}: the music global volume coefficient. */
    public static final double MUSIC_GLOBAL_COEFFICIENT = 80.0;

    /** Equal-tempered semitone ratio used by {@code SDLSoundInstance::AdjustPitch}. */
    private static final double SEMITONE_RATIO = 1.0594630943592952645618252949463;

    private AudioGain() {
    }

    /**
     * Upstream {@code SDLSoundInstance::RehupVolume()} register update.
     *
     * @param previousChunkVolume the current {@code Mix_Chunk::volume} register value; returned
     *     unchanged when the computed value is negative (SDL-Mixer-X silently ignores those)
     * @return the new register value in {@code [0, 128]}
     */
    public static int chunkVolume(double baseVolume, double volume, double masterVolume,
            int previousChunkVolume) {
        int value = (int) (baseVolume * volume * masterVolume * MIX_MAX_VOLUME);
        if (value < 0) {
            return previousChunkVolume;
        }
        return Math.min(value, MIX_MAX_VOLUME);
    }

    /** OpenAL {@code AL_GAIN} for a chunk register value (the mixer's {@code /128} factor). */
    public static float sourceGain(int chunkVolume) {
        return chunkVolume / (float) MIX_MAX_VOLUME;
    }

    /**
     * Upstream {@code SDLSoundManager::SetMasterVolume}:
     * {@code Mix_MasterVolume((int)(theVolume * 128))}, where {@code Mix_MasterVolume}
     * (SDL-Mixer-X {@code src/mixer.c:1848-1859}) clamps to {@code [0, 128]} and is a query
     * (returning the previous register unchanged) for negative input.
     *
     * @param previousMasterVolume the current {@code Mix_MasterVolume} register
     */
    public static int mixerMasterVolume(double masterVolume, int previousMasterVolume) {
        int value = (int) (masterVolume * MIX_MAX_VOLUME);
        if (value < 0) {
            return previousMasterVolume;
        }
        return Math.min(value, MIX_MAX_VOLUME);
    }

    /** OpenAL listener gain equivalent of the SDL_mixer global {@code Mix_MasterVolume} register. */
    public static float mixerMasterGain(int mixerMasterVolume) {
        return mixerMasterVolume / (float) MIX_MAX_VOLUME;
    }

    /** Upstream {@code SDLMusicInterface::SetVolume}: {@code (int)(theVolume * 80)}. */
    public static int musicGeneralVolume(double volume) {
        return (int) (volume * MUSIC_GLOBAL_COEFFICIENT);
    }

    /** Upstream {@code Mix_VolumeMusicStream(handle, (int)(mVolume * 128))}. */
    public static int musicStreamVolume(double volume) {
        return (int) (volume * MIX_MAX_VOLUME);
    }

    /**
     * OpenAL {@code AL_GAIN} for a music source: the general music volume (from
     * {@link #musicGeneralVolume(double)}) multiplied by the stream's own volume register.
     *
     * <p>PORT-NOTE: the upstream openmpt codec doubles the value it hands to libopenmpt
     * ({@code music_openmpt.c:238-242}: {@code mptVol = volume * 2}, i.e. an effective factor of
     * {@code volume/64}); this port keeps the single {@code /128} factor for every codec so that Ogg
     * and MO3 music are the same loudness.
     */
    public static float musicSourceGain(int generalVolume, int streamVolume) {
        return clampRegister(generalVolume) / (float) MIX_MAX_VOLUME
                * clampRegister(streamVolume) / (float) MIX_MAX_VOLUME;
    }

    /** {@code Mix_VolumeMusicStream} clamps its argument to {@code [0, 128]}. */
    private static int clampRegister(int register) {
        return Math.max(0, Math.min(register, MIX_MAX_VOLUME));
    }

    /** Upstream {@code SDLSoundInstance::AdjustPitch}: playback rate for {@code theNumSteps} semitones. */
    public static float pitchFromSemitones(double theNumSteps) {
        return (float) Math.pow(SEMITONE_RATIO, theNumSteps);
    }
}
