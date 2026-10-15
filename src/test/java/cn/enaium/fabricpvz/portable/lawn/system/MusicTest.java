package cn.enaium.fabricpvz.portable.lawn.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.MusicBurstState;
import cn.enaium.fabricpvz.portable.lawn.MusicDrumsState;
import cn.enaium.fabricpvz.portable.lawn.MusicFile;
import cn.enaium.fabricpvz.portable.lawn.MusicTune;
import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.sexy.sound.MusicInterface;

/**
 * Exercises the ported {@link Music} behaviour against the spec in
 * docs/research/04-reanim-ui.md (section "Music + Foley runtime"): the {@code PlayMusic} MOD order
 * table, the {@code StartGameMusic} tune-selection tree, the fade-out curve and the burst/drums
 * state machine.
 *
 * <p>{@code Music.mMusicInterface} is a public field, so each test swaps in a recording backend.
 */
class MusicTest {

    // ------------------------------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------------------------------

    record PlayCall(int songId, int offset, boolean noLoop) {
    }

    record VolumeCall(int songId, double volume) {
    }

    record ChannelCall(int songId, int track, double volume) {
    }

    static final class RecordingMusicInterface implements MusicInterface {
        final List<PlayCall> playCalls = new ArrayList<>();
        final List<VolumeCall> volumeCalls = new ArrayList<>();
        final List<ChannelCall> channelCalls = new ArrayList<>();
        final List<Integer> stopMusicCalls = new ArrayList<>();

        /** Returned by {@link #getMusicOrder(int)}; set by the burst tests. */
        int musicOrder = -1;

        @Override
        public boolean loadMusic(int theSongId, String theFileName) {
            return true;
        }

        @Override
        public void playMusic(int theSongId, int theOffset, boolean noLoop) {
            playCalls.add(new PlayCall(theSongId, theOffset, noLoop));
        }

        @Override
        public void stopMusic(int theSongId) {
            stopMusicCalls.add(theSongId);
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
            volumeCalls.add(new VolumeCall(theSongId, theVolume));
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

        @Override
        public int getMusicOrder(int theSongId) {
            return musicOrder;
        }

        @Override
        public void setChannelVolume(int theSongId, int theTrack, double theVolume) {
            channelCalls.add(new ChannelCall(theSongId, theTrack, theVolume));
        }
    }

    /** Lets a test drive the burst machine without a live board. */
    static class TestableMusic extends Music {
        int zombieCount;

        @Override
        protected int countZombiesOnScreen() {
            return zombieCount;
        }
    }

    private static Music newMusic(RecordingMusicInterface iface) {
        Music music = new Music();
        // Detach from any process-wide app another test may have installed, so the burst machine
        // runs headless and deterministically.
        music.mApp = null;
        music.mMusicInterface = iface;
        return music;
    }

    /** Builds a {@link TestableMusic} detached from any process-wide app. */
    private static TestableMusic newTestableMusic(RecordingMusicInterface iface) {
        TestableMusic music = new TestableMusic();
        music.mApp = null;
        music.mMusicInterface = iface;
        music.mCurMusicTune = MusicTune.MUSIC_TUNE_DAY_GRASSWALK;
        music.mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;
        music.mCurMusicFileDrums = MusicFile.MUSIC_FILE_DRUMS;
        return music;
    }

    // ------------------------------------------------------------------------------------------
    // (a) PlayMusic MOD order table
    // ------------------------------------------------------------------------------------------

    @Test
    void playMusicUsesTheDocumentedOffsetTable() {
        int[][] table = {
            {MusicTune.MUSIC_TUNE_DAY_GRASSWALK, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x00},
            {MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x30},
            {MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x5E},
            {MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x7D},
            {MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0xB8},
            {MusicTune.MUSIC_TUNE_CHOOSE_YOUR_SEEDS, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x7A},
            {MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x98},
            {MusicTune.MUSIC_TUNE_ZEN_GARDEN, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0xDD},
            {MusicTune.MUSIC_TUNE_PUZZLE_CEREBRAWL, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0xB1},
            {MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0xA6},
            {MusicTune.MUSIC_TUNE_CONVEYER, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0xD4},
            {MusicTune.MUSIC_TUNE_FINAL_BOSS_BRAINIAC_MANIAC, MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x9E},
            {MusicTune.MUSIC_TUNE_CREDITS_ZOMBIES_ON_YOUR_LAWN,
                MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN, 0x00},
        };

        for (int[] aCase : table) {
            int aTune = aCase[0];
            int aMainFile = aCase[1];
            int anOffset = aCase[2];

            RecordingMusicInterface iface = new RecordingMusicInterface();
            Music music = newMusic(iface);
            music.playMusic(aTune, -1, -1);

            boolean aNoLoop = aMainFile == MusicFile.MUSIC_FILE_CREDITS_ZOMBIES_ON_YOUR_LAWN;
            assertFalse(iface.playCalls.isEmpty(), "no playMusic call for tune " + aTune);
            assertEquals(new PlayCall(aMainFile, anOffset, aNoLoop), iface.playCalls.get(0),
                    "first playMusic call for tune " + aTune);
            assertEquals(aMainFile, music.mCurMusicFileMain, "main file for tune " + aTune);
        }
    }

    @Test
    void playMusicStartsTheDrumsStreamForNightMoongrains() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        Music music = newMusic(iface);
        music.playMusic(MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS, -1, -1);

        assertEquals(MusicFile.MUSIC_FILE_MAIN_MUSIC, music.mCurMusicFileMain);
        assertEquals(MusicFile.MUSIC_FILE_DRUMS, music.mCurMusicFileDrums);
        assertEquals(2, iface.playCalls.size());
        assertEquals(new PlayCall(MusicFile.MUSIC_FILE_MAIN_MUSIC, 0x30, false), iface.playCalls.get(0));
        assertEquals(new PlayCall(MusicFile.MUSIC_FILE_DRUMS, 0x5C, false), iface.playCalls.get(1));
    }

    @Test
    void playMusicKeepsOnlyTheMainStreamForGrasswalk() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        Music music = newMusic(iface);
        music.playMusic(MusicTune.MUSIC_TUNE_DAY_GRASSWALK, -1, -1);

        assertEquals(MusicFile.MUSIC_FILE_MAIN_MUSIC, music.mCurMusicFileMain);
        assertEquals(MusicFile.MUSIC_FILE_NONE, music.mCurMusicFileDrums);
        assertEquals(1, iface.playCalls.size());
    }

    // ------------------------------------------------------------------------------------------
    // (b) StartGameMusic tune selection
    // ------------------------------------------------------------------------------------------

    @Test
    void selectGameTuneFollowsTheDecisionTree() {
        // signature order: mode, level, finalBoss, wallnut, whackA, littleTrouble, bungee, stormy,
        // scaryPotter, iZombie, night, pool, sixRows, roof
        assertEquals(MusicTune.MUSIC_TUNE_ZEN_GARDEN,
                select(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, -1, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_ZEN_GARDEN,
                select(GameMode.GAMEMODE_TREE_OF_WISDOM, -1, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_FINAL_BOSS_BRAINIAC_MANIAC,
                select(0, -1, true, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON,
                select(0, -1, false, true, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON,
                select(0, -1, false, false, true, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON,
                select(0, -1, false, false, false, true, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON,
                select(0, -1, false, false, false, false, true, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_MINIGAME_LOONBOON,
                select(GameMode.GAMEMODE_CHALLENGE_SPEED, -1, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_CONVEYER,
                select(0, 10, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_CONVEYER,
                select(0, 20, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_CONVEYER,
                select(0, 30, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_CONVEYER,
                select(GameMode.GAMEMODE_CHALLENGE_COLUMN, -1, false, false, false, false, false, false, false, false, false, false, false, false));
        // Level 40/50 are NOT conveyor levels.
        assertEquals(MusicTune.MUSIC_TUNE_DAY_GRASSWALK,
                select(0, 40, false, false, false, false, false, false, false, false, false, false, false, false));
        assertEquals(Music.MUSIC_TUNE_STOP,
                select(0, -1, false, false, false, false, false, true, false, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_PUZZLE_CEREBRAWL,
                select(0, -1, false, false, false, false, false, false, true, false, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_PUZZLE_CEREBRAWL,
                select(0, -1, false, false, false, false, false, false, false, true, false, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_FOG_RIGORMORMIST,
                select(0, -1, false, false, false, false, false, false, false, false, true, true, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_NIGHT_MOONGRAINS,
                select(0, -1, false, false, false, false, false, false, false, false, true, false, false, false));
        assertEquals(MusicTune.MUSIC_TUNE_POOL_WATERYGRAVES,
                select(0, -1, false, false, false, false, false, false, false, false, false, false, true, false));
        assertEquals(MusicTune.MUSIC_TUNE_ROOF_GRAZETHEROOF,
                select(0, -1, false, false, false, false, false, false, false, false, false, false, false, true));
        assertEquals(MusicTune.MUSIC_TUNE_DAY_GRASSWALK,
                select(0, -1, false, false, false, false, false, false, false, false, false, false, false, false));
    }

    private static int select(int mode, int level, boolean finalBoss, boolean wallnut, boolean whackA,
            boolean littleTrouble, boolean bungee, boolean stormy, boolean scaryPotter, boolean iZombie,
            boolean night, boolean pool, boolean sixRows, boolean roof) {
        return Music.selectGameTune(mode, level, finalBoss, wallnut, whackA, littleTrouble, bungee, stormy,
                scaryPotter, iZombie, night, pool, sixRows, roof);
    }

    // ------------------------------------------------------------------------------------------
    // (c) Fade out
    // ------------------------------------------------------------------------------------------

    @Test
    void fadeOutTicksTheLinearCurveThenStops() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        Music music = newMusic(iface);
        music.mCurMusicTune = MusicTune.MUSIC_TUNE_DAY_GRASSWALK;
        music.mCurMusicFileMain = MusicFile.MUSIC_FILE_MAIN_MUSIC;

        music.fadeOut(500);
        music.musicUpdate();

        // Music.cpp:576 — linear 1.0 -> 0.0 over the duration; at counter 499 the warped age is
        // (499-500)/(0-500) = 0.002, so the level is 1.0 + (0.0-1.0)*0.002 = 0.998.
        assertEquals(0.998, iface.volumeCalls.get(iface.volumeCalls.size() - 1).volume(), 1e-5);
        assertEquals(MusicFile.MUSIC_FILE_MAIN_MUSIC,
                iface.volumeCalls.get(iface.volumeCalls.size() - 1).songId());

        for (int i = 0; i < 500; i++) {
            music.musicUpdate();
        }

        assertTrue(iface.stopMusicCalls.contains(MusicFile.MUSIC_FILE_MAIN_MUSIC));
        assertEquals(MusicTune.MUSIC_TUNE_NONE, music.mCurMusicTune);
    }

    // ------------------------------------------------------------------------------------------
    // (d) Burst / drums state machine
    // ------------------------------------------------------------------------------------------

    @Test
    void updateMusicBurstDrivesTheSchemeOneStateMachine() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        TestableMusic music = newTestableMusic(iface);
        music.zombieCount = 10;
        iface.musicOrder = 0x00050005;

        // OFF -> STARTING once the on-screen zombie count reaches 10.
        music.updateMusicBurst();
        assertEquals(MusicBurstState.MUSIC_BURST_STARTING, music.mMusicBurstState);
        assertEquals(400, music.mBurstStateCounter);

        // STARTING queues the drums at counter == 100.
        music.mBurstStateCounter = 101;
        music.updateMusicBurst();
        assertEquals(100, music.mBurstStateCounter);
        assertEquals(MusicDrumsState.MUSIC_DRUMS_ON_QUEUED, music.mMusicDrumsState);
        assertEquals(0x00050005, music.mQueuedDrumTrackPackedOrder);

        // STARTING -> ON at counter == 0, which re-arms the burst counter at 800.
        music.mBurstStateCounter = 1;
        music.updateMusicBurst();
        assertEquals(MusicBurstState.MUSIC_BURST_ON, music.mMusicBurstState);
        assertEquals(800, music.mBurstStateCounter);

        // The queued drums turn ON once the main stream order has moved past the queued order.
        iface.musicOrder = 0x00060006;
        music.updateMusicBurst();
        assertEquals(MusicDrumsState.MUSIC_DRUMS_ON, music.mMusicDrumsState);
        assertTrue(iface.channelCalls.contains(
                new ChannelCall(MusicFile.MUSIC_FILE_MAIN_MUSIC, 24, 128)));

        // ON -> FINISHING when the zombie count drops below 4 (mBurstOverride stays -1).
        music.zombieCount = 2;
        music.mBurstStateCounter = 0;
        music.updateMusicBurst();
        assertEquals(MusicBurstState.MUSIC_BURST_FINISHING, music.mMusicBurstState);
        assertEquals(800, music.mBurstStateCounter);
        assertEquals(MusicDrumsState.MUSIC_DRUMS_OFF_QUEUED, music.mMusicDrumsState);
        assertEquals(0x00060006, music.mQueuedDrumTrackPackedOrder);

        // OFF_QUEUED -> FADING once the main order moves past the queued order.
        iface.musicOrder = 0x00070007;
        music.updateMusicBurst();
        assertEquals(MusicDrumsState.MUSIC_DRUMS_FADING, music.mMusicDrumsState);
        assertEquals(50, music.mDrumsStateCounter);

        // FADING -> OFF when the drum fade counter expires.
        music.mDrumsStateCounter = 1;
        music.updateMusicBurst();
        assertEquals(MusicDrumsState.MUSIC_DRUMS_OFF, music.mMusicDrumsState);

        // FINISHING -> OFF once the fade counter and the drums both reach the off state.
        music.mBurstStateCounter = 1;
        music.updateMusicBurst();
        assertEquals(MusicBurstState.MUSIC_BURST_OFF, music.mMusicBurstState);

        // Scheme 1 never issues a playMusic call (it mixes channel volumes instead).
        assertTrue(iface.playCalls.isEmpty());
    }

    @Test
    void updateMusicBurstStartsOnTheBurstOverride() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        TestableMusic music = newTestableMusic(iface);
        music.zombieCount = 0;
        music.mBurstOverride = 1;

        music.updateMusicBurst();
        assertEquals(MusicBurstState.MUSIC_BURST_STARTING, music.mMusicBurstState);
        assertEquals(400, music.mBurstStateCounter);
    }

    @Test
    void updateMusicBurstIgnoresNonBurstTunes() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        Music music = newMusic(iface);
        music.mCurMusicTune = MusicTune.MUSIC_TUNE_TITLE_CRAZY_DAVE_MAIN_THEME;

        music.updateMusicBurst();
        assertEquals(MusicBurstState.MUSIC_BURST_OFF, music.mMusicBurstState);
        assertTrue(iface.playCalls.isEmpty());
        assertTrue(iface.channelCalls.isEmpty());
    }

    @Test
    void musicUpdateKeepsTheMenuVolumeConstantForNonBurstTunes() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        Music music = newMusic(iface);
        // No app/board: musicUpdate must still drive the burst (which no-ops for this tune).
        music.mCurMusicTune = MusicTune.MUSIC_TUNE_ZEN_GARDEN;
        music.musicUpdate();
        assertEquals(MusicBurstState.MUSIC_BURST_OFF, music.mMusicBurstState);
    }

    @Test
    void channelVolumesMatchTheDayGrasswalkRanges() {
        RecordingMusicInterface iface = new RecordingMusicInterface();
        Music music = newMusic(iface);
        music.setupVolumeForTune(MusicTune.MUSIC_TUNE_DAY_GRASSWALK, 1.0f, 1.0f);

        assertEquals(30, iface.channelCalls.size());
        // Main channels 0..23 at full, drums 24..26 at 1.0, hi-hat 27 at 1.0, 28..29 muted.
        assertEquals(new ChannelCall(MusicFile.MUSIC_FILE_MAIN_MUSIC, 0, 128), iface.channelCalls.get(0));
        assertEquals(new ChannelCall(MusicFile.MUSIC_FILE_MAIN_MUSIC, 24, 128), iface.channelCalls.get(24));
        assertEquals(new ChannelCall(MusicFile.MUSIC_FILE_MAIN_MUSIC, 28, 0), iface.channelCalls.get(28));
        assertEquals(new ChannelCall(MusicFile.MUSIC_FILE_MAIN_MUSIC, 29, 0), iface.channelCalls.get(29));
    }
}
