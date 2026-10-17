package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Tests {@link OpenAlSoundManager}. Bounds/register checks and the device-less degradation run
 * everywhere; everything that needs a real OpenAL device is gated with
 * {@link Assumptions#assumeTrue(boolean, String)} so headless CI stays green.
 */
class OpenAlSoundManagerTest {

    @Test
    void emptyManagerStateAndBoundsMatchUpstream() {
        try (OpenAlSoundManager manager = new OpenAlSoundManager()) {
            assertEquals(0, manager.getFreeSoundId());
            assertEquals(0, manager.getNumSounds());
            assertEquals(SoundManager.MAX_SOURCE_SOUNDS, manager.mSourceSounds.length);
            assertEquals(SoundManager.MAX_CHANNELS, manager.mPlayingSounds.length);

            assertFalse(manager.loadSound(-1, "sounds/buttonclick"));
            assertFalse(manager.loadSound(SoundManager.MAX_SOURCE_SOUNDS, "sounds/buttonclick"));
            assertNull(manager.getSoundInstance(-1));
            assertNull(manager.getSoundInstance(SoundManager.MAX_SOURCE_SOUNDS));

            assertFalse(manager.setBaseVolume(-1, 1.0));
            assertFalse(manager.setBaseVolume(SoundManager.MAX_SOURCE_SOUNDS, 1.0));
            assertFalse(manager.setBasePan(-1, 0));
            assertFalse(manager.setBasePan(SoundManager.MAX_SOURCE_SOUNDS, 0));
            assertTrue(manager.setBaseVolume(0, 1.0));
            assertTrue(manager.setBasePan(0, 0));
            // Pan is stored but never applied: upstream RehupPan() is empty
            // (SDLSoundInstance.cpp:65-67) and no Lawn caller passes a pan.
            assertTrue(manager.setBasePan(0, -100));
            assertEquals(-100, manager.mBasePans[0]);
            assertTrue(manager.setBasePan(0, 0));

            // Base volumes default to 1 and base pans to 0 (SDLSoundManager.cpp:41-46).
            assertEquals(1.0, manager.mBaseVolumes[0]);
            assertEquals(1.0, manager.mBaseVolumes[SoundManager.MAX_SOURCE_SOUNDS - 1]);
            assertEquals(0, manager.mBasePans[0]);

            manager.flush();
            manager.stopAllSounds();
            manager.releaseSounds();
            manager.releaseChannels();
        }
    }

    @Test
    void masterVolumeRegisterFollowsMixMasterVolume() {
        try (OpenAlSoundManager manager = new OpenAlSoundManager()) {
            // Mix_MasterVolume defaults to MIX_MAX_VOLUME; GetMasterVolume() divides by 128.
            assertEquals(AudioGain.MIX_MAX_VOLUME, manager.mMixerMasterVolume);
            assertEquals(1.0, manager.getMasterVolume(), 1e-9);

            manager.setMasterVolume(0.85);
            assertEquals(108, manager.mMixerMasterVolume);
            assertEquals(0.84375, manager.getMasterVolume(), 1e-9);

            // Mix_MasterVolume treats a negative argument as a query and keeps the register.
            manager.setMasterVolume(-1.0);
            assertEquals(108, manager.mMixerMasterVolume);

            // ... and clamps to [0, 128].
            manager.setMasterVolume(3.0);
            assertEquals(128, manager.mMixerMasterVolume);
            assertEquals(1.0, manager.getMasterVolume(), 1e-9);

            manager.setMasterVolume(0.0);
            assertEquals(0, manager.mMixerMasterVolume);
            assertEquals(0.0, manager.getMasterVolume(), 1e-9);
        }
    }

    @Test
    void withoutAnAudioDeviceSoundsDegradeLikeAnUninitializedMixer() throws IOException {
        OpenAlDevice device = new OpenAlDevice();
        device.close();

        try (OpenAlSoundManager manager = new OpenAlSoundManager(new PakInterface(), device)) {
            assertFalse(manager.initialized());
            // SDLSoundManager.cpp:255-256 returns true without decoding when the mixer is not up.
            assertTrue(manager.loadSound(0, "sounds/buttonclick"));
            assertEquals(0, manager.getNumSounds());
            assertNull(manager.getSoundInstance(0));
            manager.stopAllSounds();
            manager.releaseChannels();
        }
    }

    @Test
    void loadsPlaysAndGarbageCollectsVoices(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("beep.wav"), TestAudio.wavTone(44100, 1, 6615));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            PakInterface pak = new PakInterface();
            pak.setResourceFolder(dir.toString());

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device)) {
                assertTrue(manager.initialized());
                assertTrue(manager.loadSound(0, "beep"));
                assertEquals(1, manager.getNumSounds());
                assertEquals(1, manager.getFreeSoundId());
                // Mix_Chunk::volume starts at MIX_MAX_VOLUME (1 * 1 * 1 * 128).
                assertEquals(128, manager.mSourceSounds[0].mChunkVolume);
                assertEquals(44100, manager.mSourceSounds[0].mSampleRate);
                assertEquals(6615, manager.mSourceSounds[0].mFrames);

                OpenAlSoundInstance voice = (OpenAlSoundInstance) manager.getSoundInstance(0);
                assertNotNull(voice);
                assertEquals(1.0, voice.getVolume());
                assertEquals(1.0, voice.mBaseVolume);

                // Pan registers are stored and have no audible effect (upstream RehupPan is empty).
                voice.setPan(-50);
                voice.setBasePan(25);
                assertEquals(-50, voice.mPan);
                assertEquals(25, voice.mBasePan);

                assertTrue(voice.play(false, true));
                assertTrue(voice.isPlaying());
                assertSame(voice, manager.mPlayingSounds[0]);

                waitUntil(() -> !voice.isPlaying());
                assertTrue(voice.isReleased(), "auto-release voice releases itself when it stops");

                // FindFreeChannel recycles the released slot.
                OpenAlSoundInstance second = (OpenAlSoundInstance) manager.getSoundInstance(0);
                assertNotNull(second);
                assertNotSame(voice, second);
                assertSame(second, manager.mPlayingSounds[0]);

                // SetVolume is the SFX bus gain: (1 * 1 * 0.5) * 128.
                manager.setVolume(0.5);
                assertEquals(64, manager.mSourceSounds[0].mChunkVolume);

                // A voice created afterwards picks up the sound's base volume:
                // (0.5 * 1 * 0.5) * 128 == 32.
                manager.setBaseVolume(0, 0.5);
                OpenAlSoundInstance third = (OpenAlSoundInstance) manager.getSoundInstance(0);
                assertNotNull(third);
                assertEquals(0.5, third.mBaseVolume);
                assertEquals(32, manager.mSourceSounds[0].mChunkVolume);

                manager.releaseSounds();
                assertEquals(0, manager.getNumSounds());
                assertFalse(third.play(false, true), "a released buffer can no longer be played");
            }
        }
    }

    @Test
    void allVoicesCanBeExhaustedWithoutStealing(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("beep.wav"), TestAudio.wavTone(44100, 1, 44100));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            PakInterface pak = new PakInterface();
            pak.setResourceFolder(dir.toString());

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device)) {
                assertTrue(manager.loadSound(0, "beep"));

                for (int i = 0; i < SoundManager.MAX_CHANNELS; i++) {
                    SoundInstance voice = manager.getSoundInstance(0);
                    assertNotNull(voice, "voice " + i);
                    assertTrue(voice.play(true, false), "looping voice " + i);
                    assertEquals(i, ((OpenAlSoundInstance) voice).mReservedChannel);
                }

                // No voice stealing: the 33rd request is silently dropped.
                assertNull(manager.getSoundInstance(0));

                manager.stopAllSounds();
                assertNull(manager.getSoundInstance(0), "stopped voices are not released (autoRelease=false)");

                manager.releaseChannels();
                assertNotNull(manager.getSoundInstance(0));
            }
        }
    }

    @Test
    void stopAllSoundsKeepsTheAutoReleaseFlag(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("beep.wav"), TestAudio.wavTone(44100, 1, 44100));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            PakInterface pak = new PakInterface();
            pak.setResourceFolder(dir.toString());

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device)) {
                assertTrue(manager.loadSound(0, "beep"));
                OpenAlSoundInstance voice = (OpenAlSoundInstance) manager.getSoundInstance(0);
                assertNotNull(voice);
                assertTrue(voice.play(true, true));
                assertTrue(voice.mAutoRelease);

                manager.stopAllSounds();

                assertFalse(voice.isPlaying());
                assertTrue(voice.mAutoRelease, "stopAllSounds saves and restores autoRelease");
            }
        }
    }

    @Test
    void loadsAUlawFileThroughTheProbeChain(@TempDir Path dir) throws IOException {
        byte[] ulaw = {(byte) 0xFF, 0x7F, (byte) 0x80, 0x00};
        Files.write(dir.resolve("diamond.au"), TestAudio.auUlaw(8012, 1, ulaw));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            PakInterface pak = new PakInterface();
            pak.setResourceFolder(dir.toString());

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device)) {
                // The manifest name has no extension; only sounds/diamond.au exists, so the probe has
                // to reach the ".au" candidate and sniff it as AU (SDLSoundManager.cpp:281-305).
                assertTrue(manager.loadSound(0, "diamond"));
                assertEquals(1, manager.getNumSounds());
                assertEquals("diamond.au", manager.mSourceSounds[0].mFileName);
                assertEquals(8012, manager.mSourceSounds[0].mSampleRate);
                assertEquals(1, manager.mSourceSounds[0].mChannels);
                assertEquals(4, manager.mSourceSounds[0].mFrames);
                assertNotNull(manager.getSoundInstance(0));
            }
        }
    }

    @Test
    void skipsAnUnrecognizedContainerButStillFailsOnAMissingAsset(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("weird.wav"), "not an audio container".getBytes(StandardCharsets.ISO_8859_1));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            PakInterface pak = new PakInterface();
            pak.setResourceFolder(dir.toString());

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device)) {
                // The asset exists but no decoder can read it: logged and skipped, the loading thread
                // must not abort (docs/VERIFICATION.md §59).
                assertTrue(manager.loadSound(0, "weird"), "an undecodable asset is skipped, not fatal");
                assertNull(manager.mSourceSounds[0]);
                assertEquals(0, manager.getNumSounds());

                // Nothing on disk at all: still the hard failure that ResourceManager reports.
                assertFalse(manager.loadSound(1, "absent"), "a missing asset must keep failing");
                assertEquals("absent", manager.mSourceFileNames[1]);
            }
        }
    }

    @Test
    void loadsARealPakSoundByContentSniffing() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(pakPath.isPresent(), "no main.pak in the resource root");

        PakInterface pak = new PakInterface();
        pak.setResourceFolder(root.get().toString());
        assertTrue(pak.addPakFile(pakPath.get()));

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device)) {
                // The retail resource id has no extension: the probe finds .ogg and the sniffer picks
                // the Ogg Vorbis decoder. LoadSound(name) scans the id space from the top down
                // (SDLSoundManager.cpp:307-334).
                long id = manager.loadSound("sounds/buttonclick");
                assertEquals(SoundManager.MAX_SOURCE_SOUNDS - 1, id);
                assertEquals(1, manager.getNumSounds());
                assertEquals("sounds/buttonclick.ogg", manager.mSourceSounds[(int) id].mFileName);
                assertTrue(manager.mSourceSounds[(int) id].mFrames > 0);

                SoundInstance voice = manager.getSoundInstance(id);
                assertNotNull(voice);
                assertTrue(voice.play(false, true));
                assertTrue(manager.mSourceSounds[(int) id].mChunkVolume > 0);
            }
        }
    }

    @Test
    void demoModeDerivesPlayingStateFromGameTicks(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("beep.wav"), TestAudio.wavTone(44100, 1, 44100));

        MutableDemoClock clock = new MutableDemoClock();

        try (OpenAlDevice device = new OpenAlDevice()) {
            Assumptions.assumeTrue(device.available(), "no audio device available");

            PakInterface pak = new PakInterface();
            pak.setResourceFolder(dir.toString());

            try (OpenAlSoundManager manager = new OpenAlSoundManager(pak, device, clock)) {
                assertTrue(manager.loadSound(0, "beep"));
                OpenAlSoundInstance voice = (OpenAlSoundInstance) manager.getSoundInstance(0);
                assertNotNull(voice);

                clock.mInDemoMode = true;
                clock.mUpdateCount = 0;
                assertTrue(voice.play(false, false));
                // duration_ms / (10 * pitch) + 1 == 1000 / 10 + 1 (SDLSoundInstance.cpp:216-221).
                assertEquals(101, voice.mDemoEndUpdateCount);

                clock.mUpdateCount = 100;
                assertTrue(voice.isPlaying());
                clock.mUpdateCount = 101;
                assertFalse(voice.isPlaying(), "the audio clock is replaced by the demo tick");

                // Looping demo voices stay playing for the whole session.
                assertTrue(voice.play(true, false));
                clock.mUpdateCount = 1_000_000;
                assertTrue(voice.isPlaying());
            }
        }
    }

    private static final class MutableDemoClock implements DemoClock {
        boolean mInDemoMode;
        int mUpdateCount;

        @Override
        public boolean isInDemoMode() {
            return mInDemoMode;
        }

        @Override
        public int updateCount() {
            return mUpdateCount;
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
