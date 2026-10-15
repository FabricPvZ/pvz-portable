package cn.enaium.fabricpvz.portable.pvzp.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Foley cue-table and trigger tests, ported from PvzpFoley.cpp. The sound backend is an in-memory
 * {@link SoundManager} double, so the suite needs no OpenAL.
 */
class FoleyTest {

    private static final class RecordingSoundInstance implements SoundInstance {
        boolean playing;
        boolean released;
        double volume;
        double pitch;

        @Override public void release() { released = true; playing = false; }
        @Override public void setBaseVolume(double theBaseVolume) { }
        @Override public void setBasePan(int theBasePan) { }
        @Override public void adjustPitch(double theNumSteps) { pitch = theNumSteps; }
        @Override public void setVolume(double theVolume) { volume = theVolume; }
        @Override public void setPan(int thePosition) { }
        @Override public boolean play(boolean looping, boolean autoRelease) { playing = true; return true; }
        @Override public void stop() { playing = false; }
        @Override public boolean isPlaying() { return playing; }
        @Override public boolean isReleased() { return released; }
        @Override public double getVolume() { return volume; }
    }

    private static final class RecordingSoundManager implements SoundManager {
        final List<Long> requestedIds = new ArrayList<>();
        final List<RecordingSoundInstance> instances = new ArrayList<>();

        @Override public boolean initialized() { return true; }
        @Override public boolean loadSound(long theSfxID, String theFilename) { return true; }
        @Override public long loadSound(String theFilename) { return 0; }
        @Override public void releaseSound(long theSfxID) { }
        @Override public void setVolume(double theVolume) { }
        @Override public boolean setBaseVolume(long theSfxID, double theBaseVolume) { return true; }
        @Override public boolean setBasePan(long theSfxID, int theBasePan) { return true; }
        @Override public SoundInstance getSoundInstance(long theSfxID) {
            requestedIds.add(theSfxID);
            RecordingSoundInstance instance = new RecordingSoundInstance();
            instances.add(instance);
            return instance;
        }
        @Override public void releaseSounds() { }
        @Override public void releaseChannels() { }
        @Override public double getMasterVolume() { return 1.0; }
        @Override public void setMasterVolume(double theVolume) { }
        @Override public void flush() { }
        @Override public void stopAllSounds() { }
        @Override public long getFreeSoundId() { return 0; }
        @Override public int getNumSounds() { return 0; }
    }

    private SexyAppBase app;
    private RecordingSoundManager soundManager;
    private PvzpFoley foley;

    @BeforeEach
    void setUp() {
        app = new SexyAppBase();
        soundManager = new RecordingSoundManager();
        app.mSoundManager = soundManager;
        app.mUpdateCount = 100;
        app.mSfxVolume = 1.0;
        app.mMusicVolume = 0.5;
        PvzpFoley.pvzpFoleyInitialize(PvzpFoley.gLawnFoleyParamArray, FoleyType.NUM_FOLEY);
        foley = new PvzpFoley();
    }

    @Test
    void cueTableMatchesUpstream() {
        FoleyParams[] table = PvzpFoley.gLawnFoleyParamArray;
        assertEquals(104, table.length);
        assertEquals(FoleyType.NUM_FOLEY, table.length);
        for (int i = 0; i < table.length; i++) {
            assertEquals(i, table[i].mFoleyType, "table row " + i + " must keep enum order");
        }

        FoleyParams splat = table[FoleyType.FOLEY_SPLAT];
        assertEquals(10.0f, splat.mPitchRange);
        assertEquals(Resources.SOUND_SPLAT_ID, splat.mSfxID[0]);
        assertEquals(Resources.SOUND_SPLAT2_ID, splat.mSfxID[1]);
        assertEquals(Resources.SOUND_SPLAT3_ID, splat.mSfxID[2]);
        assertEquals(0, splat.mSfxID[3], "unused slots are the nullptr terminator");
        assertEquals(0, splat.mFoleyFlags);

        assertEquals(7, table[FoleyType.FOLEY_JACKINTHEBOX].mFoleyFlags); // LOOP|ONE_AT_A_TIME|MUTE_ON_PAUSE
        assertEquals(5, table[FoleyType.FOLEY_RAIN].mFoleyFlags);         // LOOP|MUTE_ON_PAUSE
        assertEquals(16, table[FoleyType.FOLEY_CRAZY_DAVE_SHORT].mFoleyFlags); // DONT_REPEAT
        assertEquals(-5.0f, table[FoleyType.FOLEY_MELONIMPACT].mPitchRange);
        assertEquals(Resources.SOUND_MELONIMPACT2_ID, table[FoleyType.FOLEY_MELONIMPACT].mSfxID[1]);
        assertEquals(0, table[FoleyType.FOLEY_MELONIMPACT].mSfxID[2]);
    }

    @Test
    void repeatedFramesDoNotRetriggerWithinTheAntiOverlapWindow() {
        foley.playFoleyPitch(FoleyType.FOLEY_SPLAT, 0.0f);
        assertEquals(1, soundManager.requestedIds.size());

        // Same update count and the immediately following frames (0..9 cs): suppressed.
        for (int i = 0; i < 9; i++) {
            app.mUpdateCount = 100 + i;
            foley.playFoleyPitch(FoleyType.FOLEY_SPLAT, 0.0f);
        }
        assertEquals(1, soundManager.requestedIds.size(), "a non-looping foley must not overlap within 10 cs");

        // 10 cs after the play it is allowed again.
        app.mUpdateCount = 110;
        foley.playFoleyPitch(FoleyType.FOLEY_SPLAT, 0.0f);
        assertEquals(2, soundManager.requestedIds.size());
    }

    @Test
    void oneAtATimeReplaysOnlyBumpTheRefCount() {
        foley.playFoley(FoleyType.FOLEY_JACKINTHEBOX);
        foley.playFoley(FoleyType.FOLEY_JACKINTHEBOX);
        foley.playFoley(FoleyType.FOLEY_JACKINTHEBOX);

        assertEquals(1, soundManager.requestedIds.size(), "ONE_AT_A_TIME must not open a second stream");
        FoleyInstance instance = PvzpFoley.soundSystemFindInstance(foley, FoleyType.FOLEY_JACKINTHEBOX);
        assertEquals(3, instance.mRefCount);
        assertEquals(100, instance.mStartTime);
    }

    @Test
    void dontRepeatNeverPicksTheSameVariationTwiceInARow() {
        int[] variations = { Resources.SOUND_CRAZYDAVESHORT1_ID, Resources.SOUND_CRAZYDAVESHORT2_ID, Resources.SOUND_CRAZYDAVESHORT3_ID };
        long[] sentinels = {2001L, 2002L, 2003L};
        long[] saved = new long[variations.length];
        for (int i = 0; i < variations.length; i++) {
            saved[i] = Resources.getSoundById(variations[i]);
            Resources.setSoundById(variations[i], sentinels[i]);
        }
        List<Long> played = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            app.mUpdateCount = 100 + i * 10;
            for (RecordingSoundInstance instance : soundManager.instances) {
                instance.playing = false; // allow the release step to free the slot
            }
            foley.playFoley(FoleyType.FOLEY_CRAZY_DAVE_SHORT);
            played.add(soundManager.requestedIds.get(soundManager.requestedIds.size() - 1));
        }
        for (int i = 1; i < played.size(); i++) {
            assertNotEquals(played.get(i - 1), played.get(i), "DONT_REPEAT forbids repeating the last variation");
        }
        for (long id : played) {
            boolean known = false;
            for (long sentinel : sentinels) {
                known |= id == sentinel;
            }
            assertTrue(known, "variation " + id + " must be one of the loaded ids of the table");
        }
        for (int i = 0; i < variations.length; i++) {
            Resources.setSoundById(variations[i], saved[i]);
        }
    }

    @Test
    void cueFiringOrderFollowsTheCalls() {
        // The mixer is addressed by the loaded sound id (upstream reads the Sexy::SOUND_* global),
        // so bind distinctive values and expect them back instead of the resource entry ids.
        long[] saved = {Resources.getSoundById(Resources.SOUND_POINTS_ID),
                Resources.getSoundById(Resources.SOUND_LAWNMOWER_ID),
                Resources.getSoundById(Resources.SOUND_PUFF_ID)};
        Resources.setSoundById(Resources.SOUND_POINTS_ID, 1001L);
        Resources.setSoundById(Resources.SOUND_LAWNMOWER_ID, 1002L);
        Resources.setSoundById(Resources.SOUND_PUFF_ID, 1003L);
        try {
            foley.playFoleyPitch(FoleyType.FOLEY_SUN, 0.0f);
            app.mUpdateCount = 110;
            foley.playFoleyPitch(FoleyType.FOLEY_LAWNMOWER, 0.0f);
            app.mUpdateCount = 120;
            foley.playFoleyPitch(FoleyType.FOLEY_PUFF, 0.0f);

            assertEquals(List.of(1001L, 1002L, 1003L), soundManager.requestedIds);
        } finally {
            Resources.setSoundById(Resources.SOUND_POINTS_ID, saved[0]);
            Resources.setSoundById(Resources.SOUND_LAWNMOWER_ID, saved[1]);
            Resources.setSoundById(Resources.SOUND_PUFF_ID, saved[2]);
        }
    }

    @Test
    void loopingFoleyRestartsWhileYoung() {
        foley.playFoley(FoleyType.FOLEY_RAIN);
        foley.playFoley(FoleyType.FOLEY_RAIN); // LOOP => the too-recent rule is bypassed
        assertEquals(2, soundManager.requestedIds.size());
    }
}
