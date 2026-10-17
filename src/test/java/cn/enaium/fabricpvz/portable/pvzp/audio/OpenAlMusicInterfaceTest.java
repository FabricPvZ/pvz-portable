package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Tests {@link OpenAlMusicInterface}: register/gain tables applied to the OpenAL source, the
 * {@code update()} fade steps and the pause/resume/stop/no-loop behaviour. The device-less
 * degradation test runs everywhere; the rest is gated on a real OpenAL device.
 */
class OpenAlMusicInterfaceTest {

    private static final int SONG = 1;

    @Test
    void withoutAnAudioDeviceMusicStaysUnavailable() {
        OpenAlDevice device = new OpenAlDevice();
        device.close();

        try (OpenAlMusicInterface music = new OpenAlMusicInterface(new PakInterface(), device)) {
            byte[] wav = TestAudio.wavTone(44100, 1, 4410);
            assertFalse(music.loadMusic(SONG, "sounds/mainmusic.mo3"));
            assertFalse(music.loadMusicData(SONG, wav, "tone.wav"));
            assertFalse(music.isPlaying(SONG));
            assertEquals(-1, music.getMusicOrder(SONG));

            assertDoesNotThrow(() -> music.playMusic(SONG, 0, false));
            assertDoesNotThrow(() -> music.fadeIn(SONG, -1, 0.002, false));
            assertDoesNotThrow(() -> music.fadeOut(SONG, true, 0.004));
            assertDoesNotThrow(() -> music.setSongVolume(SONG, 0.5));
            assertDoesNotThrow(music::update);
            assertDoesNotThrow(music::stopAllMusic);
            assertDoesNotThrow(music::pauseAllMusic);
            assertDoesNotThrow(music::resumeAllMusic);
            assertDoesNotThrow(() -> music.unloadMusic(SONG));
            assertDoesNotThrow(music::unloadAllMusic);
        }
    }

    @Test
    void volumeRegistersFollowTheUpstreamTables() throws IOException {
        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            try (OpenAlMusicInterface music = new OpenAlMusicInterface(new PakInterface(), device)) {
                byte[] wav = TestAudio.wavTone(44100, 1, 44100 * 30);
                assertTrue(music.loadMusicData(SONG, wav, "tone.wav"));

                // mGlobalVolume starts at MIX_MAX_VOLUME (SDLMusicInterface.cpp:38-41).
                assertEquals(AudioGain.MIX_MAX_VOLUME, music.mGlobalVolume);
                assertFalse(music.isPlaying(SONG));

                music.playMusic(SONG, 0, false);
                assertTrue(music.isPlaying(SONG));
                // PlayMusic sets mVolume = mVolumeCap = 1.0 -> 128/128 * 128/128.
                assertEquals(1.0f, music.sourceGain(SONG), 1e-6f);

                // SetVolume only caches (int)(volume * 80); update() applies it every frame.
                music.setVolume(0.85);
                music.update();
                assertEquals(0.53125f, music.sourceGain(SONG), 1e-6f); // 68/128 * 128/128

                // SetSongVolume writes the stream register immediately: 68/128 * 64/128.
                music.setSongVolume(SONG, 0.5);
                assertEquals(0.265625f, music.sourceGain(SONG), 1e-6f);

                // SetSongMaxVolume clamps the current volume down to the new cap: 68/128 * 32/128.
                music.setSongMaxVolume(SONG, 0.25);
                assertEquals(0.1328125f, music.sourceGain(SONG), 1e-6f);

                // Pause/resume mirror Mix_PauseMusicStream/Mix_ResumeMusicStream; IsPlaying follows
                // Mix_PlayingMusicStream (false while paused).
                music.pauseMusic(SONG);
                assertFalse(music.isPlaying(SONG));
                music.resumeMusic(SONG);
                assertTrue(music.isPlaying(SONG));

                // A WAV stream has no tracker orders or channels.
                assertEquals(-1, music.getMusicOrder(SONG));
                assertDoesNotThrow(() -> music.setChannelVolume(SONG, 3, 64.0));
                assertDoesNotThrow(() -> music.setMusicAmplify(SONG, 2.0));

                music.stopMusic(SONG);
                assertFalse(music.isPlaying(SONG));

                music.unloadMusic(SONG);
                assertFalse(music.isPlaying(SONG));
                assertFalse(music.loadMusicData(SONG, new byte[0], "empty.wav"));
            }
        }
    }

    @Test
    void fadeOutStepsOncePerUpdateAndHaltsAtZero() throws IOException {
        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            try (OpenAlMusicInterface music = new OpenAlMusicInterface(new PakInterface(), device)) {
                byte[] wav = TestAudio.wavTone(44100, 1, 44100 * 30);
                assertTrue(music.loadMusicData(SONG, wav, "tone.wav"));
                music.playMusic(SONG, 0, false);

                music.fadeOut(SONG, true, 0.5);

                music.update();
                assertTrue(music.isPlaying(SONG));
                assertEquals(0.5f, music.sourceGain(SONG), 1e-6f); // 1.0 - 0.5

                music.update();
                assertTrue(music.isPlaying(SONG), "exactly zero volume does not halt yet");
                assertEquals(0.0f, music.sourceGain(SONG), 1e-6f);

                music.update();
                assertFalse(music.isPlaying(SONG), "the next step goes below zero and halts the stream");
                assertEquals(0.0f, music.sourceGain(SONG), 1e-6f);
            }
        }
    }

    @Test
    void fadeInRampsFromTheCurrentVolumeAndDoesNotStopTheSong() throws IOException {
        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            try (OpenAlMusicInterface music = new OpenAlMusicInterface(new PakInterface(), device)) {
                byte[] wav = TestAudio.wavTone(44100, 1, 44100 * 30);
                assertTrue(music.loadMusicData(SONG, wav, "tone.wav"));
                music.playMusic(SONG, 0, false);

                music.setSongVolume(SONG, 0.0);
                // Upstream FadeIn does not reset mVolume (SDLMusicInterface.cpp:198-214): it ramps up
                // from the current 0.0 and restarts the stream.
                music.fadeIn(SONG, -1, 0.5, false);
                assertTrue(music.isPlaying(SONG));

                music.update();
                assertEquals(0.5f, music.sourceGain(SONG), 1e-6f);

                music.update();
                assertEquals(1.0f, music.sourceGain(SONG), 1e-6f);

                // The overshoot is clamped on the following step and the ramp disarms.
                music.update();
                assertTrue(music.isPlaying(SONG));
                assertEquals(1.0f, music.sourceGain(SONG), 1e-6f);
            }
        }
    }

    @Test
    void realMo3SongPlaysAndReportsTrackerOrders() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(pakPath.isPresent(), "no main.pak in the resource root");
        Assumptions.assumeTrue(Mo3Decoder.isAvailable(), "libopenmpt not available");

        PakInterface pak = new PakInterface();
        pak.setResourceFolder(root.get().toString());
        assertTrue(pak.addPakFile(pakPath.get()));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            try (OpenAlMusicInterface music = new OpenAlMusicInterface(pak, device)) {
                int drumsSong = 2;
                assertTrue(music.loadMusic(drumsSong, "sounds/mainmusic.mo3"));

                // Music::PlayFromOffset(MUSIC_FILE_DRUMS, 0x5C, 0.0) style start.
                music.playMusic(drumsSong, 0x5C, false);
                assertTrue(music.isPlaying(drumsSong));
                // The interface hands out the tracker order *pack* (row << 16 | order, see
                // MusicInterface::getMusicOrder) - the game masks it with 0xFFFF before comparing
                // (Music.cpp:506) and round-trips the whole pack through seekOrder. Masking here is
                // what makes the assertion independent of which row playback happens to be on: the
                // order is 0x5C and unmasked the value reads 0x2005C a moment into the song.
                assertEquals(0x5C, music.getMusicOrder(drumsSong) & 0xFFFF);

                // Music::SetupVolumeForTune ends with Mix_ModMusicStreamSetChannelVolume(handle,
                // track, (int)(volume * 128)); stem 0 is full, the rest silent.
                music.setChannelVolume(drumsSong, 0, 128.0);

                music.pauseMusic(drumsSong);
                assertFalse(music.isPlaying(drumsSong));
                music.resumeMusic(drumsSong);
                assertTrue(music.isPlaying(drumsSong));

                music.stopMusic(drumsSong);
                assertFalse(music.isPlaying(drumsSong));
            }
        }
    }
}
