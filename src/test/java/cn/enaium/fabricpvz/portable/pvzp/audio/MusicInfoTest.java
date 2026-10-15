package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.audio.MusicInfo.StepResult;

/**
 * Asserts the {@code SDLMusicInterface::Update()} fade integrator
 * (src/SexyAppFramework/sound/SDLMusicInterface.cpp:304-341) per {@code update()} call, against
 * hand-computed values. All speeds used here are exact binary fractions so the expectations are
 * exact.
 */
class MusicInfoTest {

    @Test
    void defaultsMatchSdlMusicInfo() {
        MusicInfo info = new MusicInfo();
        assertEquals(0.0, info.mVolume);
        assertEquals(0.0, info.mVolumeAdd);
        assertEquals(1.0, info.mVolumeCap);
        assertFalse(info.mStopOnFade);
    }

    @Test
    void stepDoesNothingWhileNoFadeIsArmed() {
        MusicInfo info = new MusicInfo();
        assertEquals(StepResult.UNCHANGED, info.step());
        assertEquals(0.0, info.mVolume);
        assertEquals(0.0, info.mVolumeAdd);
    }

    @Test
    void fadeInRampsUpFromTheCurrentVolumeAndClampsAtTheCap() {
        MusicInfo info = new MusicInfo();
        info.mVolumeAdd = 0.25; // FadeIn armed

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.25, info.mVolume);

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.5, info.mVolume);

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.75, info.mVolume);

        // Reaching exactly the cap is not yet an overshoot: mVolumeAdd survives one more call.
        assertEquals(StepResult.APPLY, info.step());
        assertEquals(1.0, info.mVolume);
        assertEquals(0.25, info.mVolumeAdd);

        // Now the overshoot clamps and clears the ramp.
        assertEquals(StepResult.APPLY, info.step());
        assertEquals(1.0, info.mVolume);
        assertEquals(0.0, info.mVolumeAdd);

        assertEquals(StepResult.UNCHANGED, info.step());
        assertEquals(1.0, info.mVolume);
    }

    @Test
    void fadeInDoesNotResetTheVolumeFirst() {
        // Upstream FadeIn only arms mVolumeAdd (SDLMusicInterface.cpp:205): it ramps from the current
        // volume, unlike PlayMusic which resets mVolume to the cap.
        MusicInfo info = new MusicInfo();
        info.mVolume = 0.4;
        info.mVolumeAdd = 0.002;

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.402, info.mVolume, 1e-12);
    }

    @Test
    void fadeOutWithStopSongHaltsOneStepAfterReachingZero() {
        MusicInfo info = new MusicInfo();
        info.mVolume = 1.0;
        info.mVolumeCap = 1.0;
        // SDLMusicInterface::FadeOut(id, stopSong = true, speed = 0.5).
        info.mVolumeAdd = -0.5;
        info.mStopOnFade = true;

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.5, info.mVolume);

        // Exactly zero is not "below zero", so the fade keeps running.
        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.0, info.mVolume);
        assertEquals(-0.5, info.mVolumeAdd);

        assertEquals(StepResult.APPLY_AND_HALT, info.step());
        assertEquals(0.0, info.mVolume);
        assertEquals(0.0, info.mVolumeAdd);

        assertEquals(StepResult.UNCHANGED, info.step());
    }

    @Test
    void fadeOutWithoutStopSongKeepsPlayingAtZeroVolume() {
        MusicInfo info = new MusicInfo();
        info.mVolume = 1.0;
        info.mVolumeAdd = -0.5; // FadeOut(id, stopSong = false, speed = 0.5)
        info.mStopOnFade = false;

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(StepResult.APPLY, info.step());
        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.0, info.mVolume);
        assertEquals(0.0, info.mVolumeAdd);
        assertEquals(StepResult.UNCHANGED, info.step());
    }

    @Test
    void setSongMaxVolumeLowerTheCeilingAndTheRampClampsToIt() {
        MusicInfo info = new MusicInfo();
        info.mVolume = 0.4;
        // SetSongMaxVolume clamps the current volume down to the new cap.
        info.mVolumeCap = 0.5;
        info.mVolumeAdd = 0.25;

        assertEquals(StepResult.APPLY, info.step());
        assertEquals(0.5, info.mVolume);
        assertEquals(0.0, info.mVolumeAdd);
        assertTrue(info.mVolume <= info.mVolumeCap);
    }

    @Test
    void defaultFadeSpeedsStepOncePerCall() {
        // FadeIn default speed 0.002 / FadeOut default 0.004 (MusicInterface.h:51-53).
        MusicInfo info = new MusicInfo();
        info.mVolumeAdd = 0.002;
        for (int i = 0; i < 10; i++) {
            assertEquals(StepResult.APPLY, info.step());
        }
        assertEquals(0.02, info.mVolume, 1e-12);

        MusicInfo out = new MusicInfo();
        out.mVolume = 1.0;
        out.mVolumeAdd = -0.004;
        for (int i = 0; i < 10; i++) {
            assertEquals(StepResult.APPLY, out.step());
        }
        assertEquals(0.96, out.mVolume, 1e-12);
    }
}
