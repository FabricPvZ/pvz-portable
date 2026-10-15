package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Asserts the upstream SDL-Mixer-X volume tables exactly, straight from
 * {@code SDLSoundInstance.cpp:60-63}, {@code SDLSoundManager.cpp:420-428},
 * {@code SDLMusicInterface.cpp:252-302}.
 */
class AudioGainTest {

    private static final float EPSILON = 1e-6f;

    @Test
    void chunkVolumeFollowsTheUpstreamSfxTable() {
        // (mBaseVolume * mVolume * mMasterVolume) * 128, cast to int, clamped to [0,128].
        assertEquals(128, AudioGain.chunkVolume(1.0, 1.0, 1.0, 0));
        assertEquals(64, AudioGain.chunkVolume(1.0, 0.5, 1.0, 0));
        assertEquals(64, AudioGain.chunkVolume(0.5, 1.0, 1.0, 0));
        assertEquals(64, AudioGain.chunkVolume(1.0, 1.0, 0.5, 0));
        assertEquals(32, AudioGain.chunkVolume(0.5, 0.5, 1.0, 0));
        assertEquals(0, AudioGain.chunkVolume(1.0, 1.0, 0.0, 128));
    }

    @Test
    void chunkVolumeTruncatesAndClampsLikeTheCCast() {
        // (int)(0.999 * 128) == 127 (truncation towards zero, not rounding).
        assertEquals(127, AudioGain.chunkVolume(0.999, 1.0, 1.0, 0));
        // (int)(1.001 * 128) == 128 before the clamp.
        assertEquals(128, AudioGain.chunkVolume(1.001, 1.0, 1.0, 0));
        // Mix_VolumeChunk clamps values above MIX_MAX_VOLUME.
        assertEquals(128, AudioGain.chunkVolume(2.0, 1.0, 1.0, 0));
        // (int)(-0.128) truncates to 0, which Mix_VolumeChunk accepts.
        assertEquals(0, AudioGain.chunkVolume(1.0, -0.001, 1.0, 77));
    }

    @Test
    void chunkVolumeIgnoresNegativeResultsAndKeepsTheRegister() {
        // Mix_VolumeChunk keeps the previous chunk->volume when handed a negative value.
        assertEquals(77, AudioGain.chunkVolume(-1.0, 1.0, 1.0, 77));
        assertEquals(128, AudioGain.chunkVolume(1.0, -2.0, 1.0, 128));
    }

    @Test
    void chunkRegisterMapsToOpenAlGainByDividingBy128() {
        assertEquals(1.0f, AudioGain.sourceGain(128), EPSILON);
        assertEquals(0.5f, AudioGain.sourceGain(64), EPSILON);
        assertEquals(0.0f, AudioGain.sourceGain(0), EPSILON);
    }

    @Test
    void mixerMasterVolumeMirrorsMixMasterVolume() {
        // SetMasterVolume: (int)(theVolume * 128); Mix_MasterVolume clamps and ignores negatives.
        assertEquals(128, AudioGain.mixerMasterVolume(1.0, 0));
        assertEquals(108, AudioGain.mixerMasterVolume(0.85, 0)); // (int)(108.8)
        assertEquals(64, AudioGain.mixerMasterVolume(0.5, 0));
        assertEquals(0, AudioGain.mixerMasterVolume(0.0, 128));
        assertEquals(128, AudioGain.mixerMasterVolume(2.0, 0));
        assertEquals(91, AudioGain.mixerMasterVolume(-1.0, 91));
        // GetMasterVolume() = Mix_MasterVolume(-1) / 128.
        assertEquals(1.0f, AudioGain.mixerMasterGain(128), EPSILON);
        assertEquals(0.84375f, AudioGain.mixerMasterGain(108), EPSILON);
        assertEquals(0.0f, AudioGain.mixerMasterGain(0), EPSILON);
    }

    @Test
    void musicRegistersKeepThe80CoefficientOfTheMusicInterface() {
        // SDLMusicInterface::SetVolume -> (int)(theVolume * 80).
        assertEquals(80, AudioGain.musicGeneralVolume(1.0));
        assertEquals(68, AudioGain.musicGeneralVolume(0.85)); // (int)(68.0)
        assertEquals(0, AudioGain.musicGeneralVolume(0.0));
        // Mix_VolumeMusicStream(handle, (int)(mVolume * 128)).
        assertEquals(128, AudioGain.musicStreamVolume(1.0));
        assertEquals(64, AudioGain.musicStreamVolume(0.5));
        assertEquals(0, AudioGain.musicStreamVolume(0.0));
    }

    @Test
    void musicSourceGainIsTheProductOfGeneralAndStreamVolume() {
        assertEquals(1.0f, AudioGain.musicSourceGain(128, 128), EPSILON);
        assertEquals(0.625f, AudioGain.musicSourceGain(80, 128), EPSILON); // default 0.85 music volume
        assertEquals(0.5f, AudioGain.musicSourceGain(128, 64), EPSILON);
        assertEquals(0.0f, AudioGain.musicSourceGain(0, 128), EPSILON);
        // Mix_VolumeMusicStream clamps its register to [0,128].
        assertEquals(1.0f, AudioGain.musicSourceGain(200, 128), EPSILON);
        assertEquals(0.0f, AudioGain.musicSourceGain(-5, 128), EPSILON);
    }

    @Test
    void pitchIsAnEqualTemperedPlaybackRate() {
        // SDLSoundInstance::AdjustPitch = powf(1.0594630943592952645618252949463, steps).
        assertEquals(1.0f, AudioGain.pitchFromSemitones(0), EPSILON);
        assertEquals(1.0594631f, AudioGain.pitchFromSemitones(1), 1e-6f);
        assertEquals(2.0f, AudioGain.pitchFromSemitones(12), 1e-5f);
        assertEquals(0.5f, AudioGain.pitchFromSemitones(-12), 1e-5f);
    }
}
