package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundInstance;
import cn.enaium.fabricpvz.portable.sexy.sound.SoundManager;

/**
 * Regression for the four in-game sound events reported silent - sun collection, a zombie taking a
 * hit, a plant being bitten and the final/huge wave cue - driven through the real application with
 * the retail assets.
 *
 * <p>The four calls were present and correctly wired (see {@link SoundIdResolutionTest} for the id
 * translation); they were dropped one layer higher: {@link LawnApp#playFoley}/{@link
 * LawnApp#playSample} skip everything while {@code mMuteSoundsForCutscene} is set, and the port
 * never ran the board's {@code CutScene} teardown (upstream {@code ~CutScene}, CutScene.cpp:121-128,
 * reached through {@code KillBoard -> SafeDeleteWidget(mBoard) -> ~Board}). The cinematic intro sets
 * that flag ({@code CutScene::LoadIntroBoard}, CutScene.cpp:1793) and, without the teardown, it
 * stayed set for the rest of the session.
 *
 * <p>Both tests boot the real app from {@code main.pak} and talk to the real OpenAL mixer through a
 * recording wrapper, so "the sound was requested" and "the mixer started the source" are asserted
 * on the production objects. The suite is skipped when the retail assets are unavailable.
 */
class GameplaySoundEventsTest {

    /** Delegates to the real mixer and records every sound id the game asks it to play. */
    private static final class RecordingSoundManager implements SoundManager {
        final SoundManager delegate;
        final List<Long> requested = new ArrayList<>();
        final java.util.Map<Long, SoundInstance> lastVoice = new java.util.HashMap<>();

        RecordingSoundManager(SoundManager theDelegate) {
            delegate = theDelegate;
        }

        boolean requestedAnyOf(long... theIds) {
            for (long anId : theIds) {
                if (requested.contains(anId)) {
                    return true;
                }
            }
            return false;
        }

        @Override public boolean initialized() { return delegate.initialized(); }
        @Override public boolean loadSound(long theSfxID, String theFilename) { return delegate.loadSound(theSfxID, theFilename); }
        @Override public long loadSound(String theFilename) { return delegate.loadSound(theFilename); }
        @Override public void releaseSound(long theSfxID) { delegate.releaseSound(theSfxID); }
        @Override public void setVolume(double theVolume) { delegate.setVolume(theVolume); }
        @Override public boolean setBaseVolume(long theSfxID, double theBaseVolume) { return delegate.setBaseVolume(theSfxID, theBaseVolume); }
        @Override public boolean setBasePan(long theSfxID, int theBasePan) { return delegate.setBasePan(theSfxID, theBasePan); }
        @Override public void releaseSounds() { delegate.releaseSounds(); }
        @Override public void releaseChannels() { delegate.releaseChannels(); }
        @Override public double getMasterVolume() { return delegate.getMasterVolume(); }
        @Override public void setMasterVolume(double theVolume) { delegate.setMasterVolume(theVolume); }
        @Override public void flush() { delegate.flush(); }
        @Override public void stopAllSounds() { delegate.stopAllSounds(); }
        @Override public long getFreeSoundId() { return delegate.getFreeSoundId(); }
        @Override public int getNumSounds() { return delegate.getNumSounds(); }

        @Override
        public SoundInstance getSoundInstance(long theSfxID) {
            requested.add(theSfxID);
            SoundInstance aVoice = delegate.getSoundInstance(theSfxID);
            if (aVoice != null) {
                lastVoice.put(theSfxID, aVoice);
            }
            return aVoice;
        }
    }

    @TempDir
    Path tmp;

    /** A booted app with a profile and every resource loaded; {@code null} when assets are absent. */
    private LawnApp bootApp() throws IOException, InterruptedException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        app.setArgs(new String[] {
            "pvz-portable", "-resdir", resourceRoot.get().toString(),
            "-savedir", Files.createDirectories(tmp.resolve("userdata")).toString(),
        });
        app.init();
        app.startLoadingThread();
        long deadline = System.nanoTime() + 180_000_000_000L;
        while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            app.updateFrames();
            Thread.sleep(2);
        }
        assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");
        app.mPlayerInfo = app.mProfileMgr.addProfile("GameplaySoundEvents");
        return app;
    }

    /**
     * Runs the cinematic intro the way {@code GameSelector::Update} does for a first-time adventure
     * level 1 (GameSelector.cpp:773) until it hands over to {@code GAMEMODE_ADVENTURE} at
     * {@code TimeIntro_End}.
     *
     * @return true when the intro really muted the cutscene sounds (the mechanism under test)
     */
    private static boolean runIntroCinematic(LawnApp app) {
        app.preNewGame(GameMode.GAMEMODE_INTRO, false);
        boolean aSawMute = false;
        for (int i = 0; i < 5000 && app.mGameMode != GameMode.GAMEMODE_ADVENTURE; i++) {
            app.updateFrames();
            aSawMute |= app.mMuteSoundsForCutscene;
        }
        return aSawMute;
    }

    @Test
    void theIntroCinematicTeardownUnmutesTheGameAudio() throws IOException, InterruptedException {
        LawnApp app = bootApp();
        try {
            assertTrue(runIntroCinematic(app), "the cinematic intro must set the cutscene mute");
            assertEquals(GameMode.GAMEMODE_ADVENTURE, app.mGameMode,
                    "the intro must hand the game over to the adventure mode");
            assertFalse(app.mMuteSoundsForCutscene,
                    "the deleted intro board must tear its cutscene down (upstream ~CutScene) and "
                            + "clear the mute, otherwise the whole session is silent");

            // The game-level entry points must reach the mixer afterwards.
            RecordingSoundManager aRecorder = new RecordingSoundManager(app.mSoundManager);
            app.mSoundManager = aRecorder;
            app.playSample(Resources.SOUND_POINTS_ID);
            assertTrue(aRecorder.requestedAnyOf(Resources.getSoundById(Resources.SOUND_POINTS_ID)),
                    "a sound played after the intro must reach the mixer");
        } finally {
            app.mSoundManager.stopAllSounds();
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    @Test
    void sunZombieHitPlantBiteAndFinalWaveSoundsReachTheMixer()
            throws IOException, InterruptedException {
        LawnApp app = bootApp();
        try {
            // The user reaches these events *after* the intro cinematic; reproducing that state is
            // what the original defect needed (with the flag stuck true every one of these was an
            // inaudible no-op).
            assertTrue(runIntroCinematic(app), "the cinematic intro must set the cutscene mute");

            app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
            app.startPlaying();
            Board aBoard = app.mBoard;
            assertNotNull(aBoard, "the adventure level must have a board");
            assertEquals(GameScenes.SCENE_PLAYING, app.mGameScene);

            RecordingSoundManager aRecorder = new RecordingSoundManager(app.mSoundManager);
            app.mSoundManager = aRecorder;

            // --- 收集阳光 / collecting sun: Coin::MouseDown -> PlayCollectSound -> FOLEY_SUN -------
            // (FoleyType.FOLEY_SUN -> SOUND_POINTS, PvzpFoley.cpp:31 / Coin.cpp:1348-1352)
            aBoard.mTutorialState = TutorialState.TUTORIAL_OFF;
            Coin aSun = aBoard.addCoin(160, 300, CoinType.COIN_SUN, CoinMotion.COIN_MOTION_COIN);
            aSun.mPosX = 160;
            aSun.mPosY = 300;
            aSun.mouseDown(180, 320, 1);
            long aPointsId = Resources.getSoundById(Resources.SOUND_POINTS_ID);
            assertTrue(aRecorder.requestedAnyOf(aPointsId),
                    "collecting sun must ask the mixer for SOUND_POINTS (foley FOLEY_SUN)");
            SoundInstance aSunVoice = aRecorder.lastVoice.get(aPointsId);
            assertNotNull(aSunVoice, "the mixer must hand out a voice for the sun sound");
            assertTrue(aSunVoice.isPlaying(), "the sun sound source must be playing");

            // --- 僵尸受击 / zombie takes a hit: Projectile::DoImpact -> FOLEY_SPLAT --------------
            // (FoleyType.FOLEY_SPLAT -> SOUND_SPLAT/SPLAT2/SPLAT3, PvzpFoley.cpp:32 /
            // Projectile.cpp:823-938)
            Zombie aZombie = aBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, -1);
            assertNotNull(aZombie, "the board must be able to spawn a zombie");
            aZombie.mPosX = 500;
            aZombie.mPosY = aBoard.getPosYBasedOnRow(500, 2) - 30;
            Projectile aPea = aBoard.addProjectile((int) aZombie.mPosX, (int) aZombie.mPosY, 0, 2,
                    ProjectileType.PROJECTILE_PEA);
            aPea.doImpact(aZombie);
            assertTrue(aRecorder.requestedAnyOf(
                            Resources.getSoundById(Resources.SOUND_SPLAT_ID),
                            Resources.getSoundById(Resources.SOUND_SPLAT2_ID),
                            Resources.getSoundById(Resources.SOUND_SPLAT3_ID)),
                    "a pea hitting a zombie must ask the mixer for a SOUND_SPLAT variation");

            // --- 植物受击 / plant being bitten: Zombie::AnimateChewSound -> FOLEY_CHOMP ----------
            // (FoleyType.FOLEY_CHOMP -> SOUND_CHOMP/CHOMP2, PvzpFoley.cpp:36 / Zombie.cpp:4823-4878)
            Plant aPlant = aBoard.newPlant(3, 4, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
            assertNotNull(aPlant, "the board must be able to plant");
            Zombie aBiter = aBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 4, -1);
            assertNotNull(aBiter, "the board must be able to spawn a biter");
            // Place the biter so its own attack rect (zombie-relative, read from the entity) starts on
            // the plant's left edge: getRectOverlap() measures the horizontal overlap of
            // Zombie::GetZombieAttackRect() and Plant::GetPlantRect() (Board.cpp:6443-6475).
            aBiter.mX = 0;
            aBiter.mY = 0;
            Rect aLocalAttackRect = aBiter.getZombieAttackRect();
            aBiter.mX = aPlant.mX + 10 - aLocalAttackRect.mX;
            aBiter.mY = aPlant.mY;
            aBiter.mPosX = aBiter.mX;
            aBiter.mPosY = aBiter.mY;
            assertNotNull(aBiter.findPlantTarget(ZombieAttackType.ATTACKTYPE_CHEW),
                    "the biter must reach the plant, so the chewing branch is the one exercised");
            aBiter.animateChewSound();
            assertTrue(aRecorder.requestedAnyOf(
                            Resources.getSoundById(Resources.SOUND_CHOMP_ID),
                            Resources.getSoundById(Resources.SOUND_CHOMP2_ID),
                            Resources.getSoundById(Resources.SOUND_CHOMPSOFT_ID)),
                    "a zombie chewing a plant must ask the mixer for a SOUND_CHOMP variation");

            // --- 最后一波 / final wave + huge wave: Board::UpdateZombieSpawning -------------------
            // (SOUND_FINALWAVE, Board.cpp:5245 and SOUND_HUGE_WAVE, Board.cpp:5280)
            aBoard.mHugeWaveCountDown = 726;
            BoardWaves.updateZombieSpawning(aBoard);
            assertTrue(aRecorder.requestedAnyOf(
                            Resources.getSoundById(Resources.SOUND_HUGE_WAVE_ID)),
                    "the huge wave countdown must ask the mixer for SOUND_HUGE_WAVE");

            aBoard.mHugeWaveCountDown = 0;
            aBoard.mFinalWaveSoundCounter = 1;
            BoardWaves.updateZombieSpawning(aBoard);
            assertTrue(aRecorder.requestedAnyOf(
                            Resources.getSoundById(Resources.SOUND_FINALWAVE_ID)),
                    "the final wave counter must ask the mixer for SOUND_FINALWAVE");

            // The state that made all four a no-op before the fix: the cinematic intro had set the
            // flag and nothing ever cleared it (see theIntroCinematicTeardownUnmutesTheGameAudio).
            assertFalse(app.mMuteSoundsForCutscene,
                    "the game must not stay muted after the intro cinematic");
        } finally {
            app.mSoundManager.stopAllSounds();
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
