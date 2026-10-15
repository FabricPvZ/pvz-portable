package cn.enaium.fabricpvz.portable.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrack;
import cn.enaium.fabricpvz.portable.pvzp.FloatParameterTrackNode;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpListNode;
import cn.enaium.fabricpvz.portable.pvzp.fx.ParticleField;
import cn.enaium.fabricpvz.portable.pvzp.fx.ParticleFlags;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleEmitter;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleHolder;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.lawn.AlmanacPage;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.CoinMotion;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.CutScene;
import cn.enaium.fabricpvz.portable.lawn.DamageFlags;
import cn.enaium.fabricpvz.portable.lawn.LawnMower;
import cn.enaium.fabricpvz.portable.lawn.ParticleEffect;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.RenderLayer;
import cn.enaium.fabricpvz.portable.lawn.SeedBank;
import cn.enaium.fabricpvz.portable.lawn.SeedPacket;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.StoreItem;
import cn.enaium.fabricpvz.portable.lawn.widget.AlmanacDialog;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.lawn.TutorialState;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombiePhase;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;

/**
 * Boots the real game, jumps into a level and screenshots the window for one named scene. Run
 * through {@code ./gradlew sceneDump -Ppvz.dump.scenario=<graveRise|fume|packet|zomboni>}.
 *
 * <p>Same read-back discipline as {@link WindowDump}: the frame is read before {@code present()}.
 */
public final class SceneDump {

    private final LawnApp mApp;
    private final Path mDumpDir;
    private int[] mBaseline;

    private SceneDump(LawnApp theApp, Path theDumpDir) {
        mApp = theApp;
        mDumpDir = theDumpDir;
    }

    public static void main(String[] args) throws Exception {
        String scenario = System.getProperty("pvz.dump.scenario", "graveRise");
        Path dumpDir = Path.of("build", "scene-dump", scenario).toAbsolutePath();
        Path saveDir = dumpDir.resolve("userdata");
        Files.createDirectories(saveDir);

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        SceneDump dump = new SceneDump(app, dumpDir);
        try {
            app.setArgs(new String[] {"pvz-portable", "-savedir", saveDir.toString()});
            app.init();
            app.startLoadingThread();

            for (int i = 0; i < 3000 && !app.mLoadingThreadCompleted.get(); i++) {
                dump.tick();
            }
            System.out.println("[" + scenario + "] loading complete=" + app.mLoadingThreadCompleted.get());
            app.loadingCompleted();

            // 11 is 2-1 (the grave level), 26 is 3-6, the level the zomboni is introduced in
            // (ZombieDefinition.mStartingLevel = 26, Zombie.cpp:93).
            int level = scenario.equals("graveRise") ? 11 : scenario.equals("zomboni") ? 26 : 1;
            if (app.mPlayerInfo == null) {
                app.mPlayerInfo = app.mProfileMgr.addProfile("SceneDump");
            }
            app.mPlayerInfo.mLevel = level;
            app.mPlayerInfo.mFinishedAdventure = 0;

            app.killGameSelector();
            app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
            if (scenario.equals("zomboni")) {
                // PreNewGame ends in LawnApp::NewGame, which already started the level intro
                // (LawnApp.java:564-578, 641-651); calling StartPlaying here would jump straight to
                // SCENE_PLAYING and skip the intro's time windows — the lawn mowers are placed at the
                // exact-equality window mCutsceneTime == TimePanLeftStart and the board is panned back
                // to x=0 in the same frame (CutScene.java:1043-1045, 976-979). The zomboni scenario
                // needs both, so it plays the intro at the game's own pace and only supplies the two
                // player interactions ("Let's Rock" and the clicks on Crazy Dave's dialog).
                dump.playIntro(app);
            } else {
                app.startPlaying();
                // Fast-forward the level intro exactly like CutScene does when it is skipped: the board
                // places the lawn items, slides the seed bank in and enables the grave stones.
                for (int i = 0; i < 3; i++) {
                    dump.tick();
                }
                app.mBoard.mCutScene.mCutsceneTime = 100000;
                for (int i = 0; i < 5; i++) {
                    dump.tick();
                }
            }
            app.mBoard.setTutorialState(TutorialState.TUTORIAL_OFF);
            if (!app.isChallengeWithoutSeedBank()) {
                app.mBoard.mSeedBank.move(10, 0);
            }
            app.mBoard.clearAdviceImmediately();

            System.out.println("[" + scenario + "] scene=" + app.mGameScene + " level=" + app.mBoard.mLevel
                    + " bg=" + app.mBoard.mBackground + " waves=" + app.mBoard.mNumWaves);

            switch (scenario) {
                case "graveRise" -> dump.graveRise();
                case "fume" -> dump.fume();
                case "packet" -> dump.packet();
                case "packetbg" -> dump.packetbg();
                case "zomboni" -> dump.zomboni();
                default -> throw new IllegalArgumentException("unknown scenario " + scenario);
            }
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    // ---------------------------------------------------------------------------------------

    private void graveRise() throws Exception {
        mApp.mBoard.mEnableGraveStones = true;
        for (int i = 0; i < 400; i++) {
            tick();
        }
        mBaseline = capture("baseline");
        System.out.println("[graveRise] gridItems=" + mApp.mBoard.mGridItems.mSize);

        mApp.mBoard.mCurrentWave = mApp.mBoard.mNumWaves - 1;
        mApp.mBoard.mZombieCountDown = 1;

        for (int tick = 0; tick < 600; tick++) {
            tick();
            int rising = countRising();
            if (rising > 0 && tick % 5 == 0) {
                int[] pixels = capture(null);
                save(pixels, String.format("rise-tick-%03d", tick));
                System.out.println("[graveRise] tick=" + tick + " rising=" + rising
                        + " changed=" + changed(mBaseline, pixels));
            }
            if (rising == 0 && tick > 60 && mApp.mBoard.mZombies.mSize > 0) {
                int[] pixels = capture(null);
                save(pixels, "rise-done");
                System.out.println("[graveRise] done at tick=" + tick);
                return;
            }
        }
    }

    private void fume() throws Exception {
        System.out.println("[fume] is3D=" + mApp.is3DAccelerated() + " backend="
                + (mApp.mRendererBackend == null ? "null" : mApp.mRendererBackend.getClass().getSimpleName()));
        Plant plant = mApp.mBoard.addPlant(1, 2, SeedType.SEED_FUMESHROOM, SeedType.SEED_NONE);
        Zombie target = mApp.mBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, 2, 0);
        if (target != null && plant != null) {
            target.mPosX = plant.mX + 110.0f;
            target.mX = (int) target.mPosX;
            target.mPosY = plant.mY;
            target.mY = (int) target.mPosY;
            // The fume attack would kill it (20 damage per cloud) and the wave list would then complete
            // the level, which stops the board from updating the effect system. Keep it alive so the
            // long-run probes below really run inside a playing level.
            target.mBodyHealth = 1000000;
            target.mBodyMaxHealth = 1000000;
        }
        for (int i = 0; i < 20; i++) {
            tick();
        }
        mBaseline = capture("baseline");
        if (plant == null) {
            System.out.println("[fume] no plant");
            return;
        }
        System.out.println("[fume] plant at " + plant.mX + "," + plant.mY + " target=" + (target != null));
        // Plant::UpdateShooting spawns the spore cloud with exactly this call (Plant.cpp:3211-3214).
        plant.addAttachedParticle(plant.mX + 85, plant.mY + 31,
                Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, plant.mRow, 0),
                ParticleEffect.PARTICLE_FUMECLOUD);

        boolean dumped = false;
        int captured = 0;
        // Spread over the whole attack: the cloud's SystemDuration is 100 ticks and its particles live
        // 50-60 ticks, so the stream grows to SpawnMinActive 40 by tick 40, drains by ~tick 160 and the
        // system dies at tick 100 + the last particle's life.
        int[] aCaptureTicks = {0, 6, 12, 24, 40, 60, 80, 100, 120};
        for (int tick = 0; tick < 1200; tick++) {
            tick();
            PvzpParticleSystem ps = mApp.particleTryToGet(plant.mParticleID);
            if (ps != null && !dumped) {
                dumped = true;
                System.out.println("[fume] FIRE tick=" + tick + " shootingCounter=" + plant.mShootingCounter
                        + " emitters=" + ps.mEmitterList.mSize);
                for (PvzpListNode<Integer> node = ps.mEmitterList.mHead; node != null; node = node.mNext) {
                    PvzpParticleEmitter e = ps.mParticleHolder.mEmitters.dataArrayGet(node.mValue);
                    System.out.println("[fume]  emitter flags=" + e.mEmitterDef.mParticleFlags
                            + " cel=" + (e.mEmitterDef.mImage == null ? "-"
                                    : e.mEmitterDef.mImage.getCelWidth() + "x" + e.mEmitterDef.mImage.getCelHeight())
                            + " (IMAGE_PUFFSHROOM_PUFF1 = " + (e.mEmitterDef.mImage == null ? "-"
                                    : e.mEmitterDef.mImage.mFilePath) + ")"
                            + " frames=" + e.mEmitterDef.mImageFrames);
                    System.out.println("[fume]  def spawnRate=" + track(e.mEmitterDef.mSpawnRate)
                            + " spawnMinActive=" + track(e.mEmitterDef.mSpawnMinActive)
                            + " spawnMaxLaunched=" + track(e.mEmitterDef.mSpawnMaxLaunched)
                            + " particleDuration=" + track(e.mEmitterDef.mParticleDuration)
                            + " systemDuration=" + track(e.mEmitterDef.mSystemDuration)
                            + " particleScale=" + track(e.mEmitterDef.mParticleScale));
                    for (int i = 0; i < e.mEmitterDef.mParticleFields.mArrayCount; i++) {
                        ParticleField aField = ((ParticleField[]) e.mEmitterDef.mParticleFields.mArrayData)[i];
                        System.out.println("[fume]  field#" + i + " type=" + aField.mFieldType
                                + " x=" + track(aField.mX) + " y=" + track(aField.mY));
                    }
                }
            }
            if (ps != null) {
                for (PvzpListNode<Integer> node = ps.mEmitterList.mHead; node != null; node = node.mNext) {
                    PvzpParticleEmitter e = ps.mParticleHolder.mEmitters.dataArrayGet(node.mValue);
                    System.out.println("[fume-t] tick=" + tick + " sysTime=" + e.mSystemTimeValue
                            + " n=" + e.mParticleList.mSize + " spawned=" + e.mParticlesSpawned);
                }
            }
            if (ps != null && captured < aCaptureTicks.length && tick == aCaptureTicks[captured]) {
                int[] pixels = capture(null);
                save(pixels, String.format("fume-%03d", tick));
                System.out.println("[fume] tick=" + tick + " changed=" + changed(mBaseline, pixels));
                captured++;
            }
            if (dumped && ps == null && tick > 60) {
                longRun(plant, tick);
                saturationProbe(plant);
                break;
            }
        }
        System.out.println("[fume] captured=" + captured);
    }

    /**
     * Keeps one live zombie in the plant's row, held inside the fume range and out of the mower's
     * reach, so the level cannot complete mid-probe (completion would stop the board updating the
     * effect system and every pool reading below would be meaningless).
     */
    private void keepZombieAlive(Plant thePlant) {
        if (mApp.mBoard == null) {
            return;
        }
        if (mApp.mBoard.mZombies.mSize == 0) {
            mApp.mBoard.addZombieInRow(ZombieType.ZOMBIE_NORMAL, thePlant.mRow, 0);
        }
        for (Zombie aZombie : mApp.mBoard.mZombies) {
            aZombie.mPosX = 600.0f;
            aZombie.mX = 600;
            aZombie.mBodyHealth = 1000000;
            aZombie.mBodyMaxHealth = 1000000;
        }
    }

    /**
     * Long-session decay probe: the user reports that the spore cloud renders at the start of a
     * level and stops rendering after a while, which a burst captured near a fresh level cannot see.
     * Keeps the plant firing (a cloud every 200 ticks = 2 s, the plant's own cadence:
     * {@code FindTargetAndFire} sets {@code mShootingCounter 50} and {@code UpdateShooting} spawns at
     * 15, Plant.cpp:3204-3220) plus a background churn of other effects, and records for every cloud
     * the violet coverage of the spore band together with every pool that could dry up. 100 ticks is
     * one second of level time.
     */
    private void longRun(Plant thePlant, int theStartTick) throws Exception {
        // This dump runs a day level, where IsNocturnal(FUMESHROOM) sleeps the plant
        // (Plant.cpp:186-187 -> Plant.java:257-259): wake it the way a Night level or a Coffee Bean
        // does, so the natural attack path also runs here.
        thePlant.setSleeping(false);

        PvzpParticleHolder aHolder = mApp.mEffectSystem.mParticleHolder;
        int[] aCaptureAges = {12, 24, 40};
        int aAttacks = 0;
        int aMissing = 0;
        int aCloudStart = -1;
        int aCaptureIndex = 0;
        int aCaptured = 0;
        int aSecond = 0;
        int aChurn = 0;

        for (int aTick = theStartTick + 1; aTick < theStartTick + 30000; aTick++) {
            tick();
            int aLevelTick = aTick - theStartTick;
            keepZombieAlive(thePlant);
            if (mApp.mBoard == null || mApp.mGameScene != GameScenes.SCENE_PLAYING) {
                System.out.println("[fume-long] level ended at " + aLevelTick / 100.0 + "s: attacks=" + aAttacks
                        + " missing=" + aMissing + " captured=" + aCaptured + describePools());
                break;
            }

            // Background churn: other effects are what fill the pools in a real level.
            if (aLevelTick % 50 == 0) {
                mApp.addPvzpParticle(200.0f + (aLevelTick % 400), 200.0f + (aLevelTick % 200),
                        Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 0, 1),
                        ParticleEffect.PARTICLE_PEA_SPLAT);
                aChurn++;
            }

            // The cloud the plant itself would spawn.
            if (aLevelTick % 200 == 0) {
                aAttacks++;
                PvzpParticleSystem aNew = thePlant.addAttachedParticle(thePlant.mX + 85, thePlant.mY + 31,
                        Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, thePlant.mRow, 0),
                        ParticleEffect.PARTICLE_FUMECLOUD);
                aCloudStart = aTick;
                aCaptureIndex = 0;
                if (aNew == null) {
                    aMissing++;
                    System.out.println("[fume-long] ATTACK " + aAttacks + " at " + aLevelTick / 100.0
                            + "s allocParticleSystem returned null" + describePools());
                }
            }

            PvzpParticleSystem aSystem = mApp.particleTryToGet(thePlant.mParticleID);
            if (aSystem != null && aCaptureIndex < aCaptureAges.length
                    && aTick - aCloudStart == aCaptureAges[aCaptureIndex]) {
                int[] pixels = capture(null);
                if (aCaptureIndex == 1) {
                    save(pixels, String.format("fume-long-%04d", aAttacks));
                }
                System.out.println("[fume-long] attack=" + aAttacks + " age=" + aCaptureAges[aCaptureIndex]
                        + " t=" + aLevelTick / 100.0 + "s violet=" + violetBand(pixels)
                        + describeSystem(aSystem));
                aCaptured++;
                aCaptureIndex++;
            }

            if (aLevelTick % 6000 == 0) {
                aSecond++;
                System.out.println("[fume-long] === " + aSecond + " min of level time: attacks=" + aAttacks
                        + " missing=" + aMissing + " churn=" + aChurn + " captured=" + aCaptured
                        + describePools());
            }
        }
        System.out.println("[fume-long] done: attacks=" + aAttacks + " missing=" + aMissing
                + " churn=" + aChurn + " captured=" + aCaptured + describePools());
    }

    /**
     * Long-level decay probe: churn effects that are born and die (the way a real level's sun,
     * splats and zombie parts do) and, after every churn round, a fresh spore cloud whose violet
     * coverage is measured. A slot the holder hands out and never gets back shows up here as a pool
     * that climbs until PvzpParticleHolder::AllocParticleSystemFromDef refuses to allocate
     * (PvzpParticle.cpp:1253-1262): the cloud then never spawns and nothing is drawn — the decay the
     * user reports after a level has been running for a while.
     */
    private void saturationProbe(Plant thePlant) throws Exception {
        PvzpParticleHolder aHolder = mApp.mEffectSystem.mParticleHolder;
        System.out.println("[fume-full] before churn" + describePools());

        for (int aRound = 1; aRound <= 30; aRound++) {
            if (mApp.mBoard == null || mApp.mGameScene != GameScenes.SCENE_PLAYING) {
                System.out.println("[fume-full] level ended before round " + aRound + describePools());
                break;
            }
            keepZombieAlive(thePlant);
            int aBaseline = aHolder.mParticleSystems.mSize;
            for (int i = 0; i < 40; i++) {
                mApp.addPvzpParticle(300.0f + i, 300.0f,
                        Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 0, 1),
                        ParticleEffect.PARTICLE_PEA_SPLAT);
            }
            // Every churn effect finishes and leaves the delete queue before the next round, so what
            // this reports is what the holder kept, not what is momentarily alive.
            int aWaited = 0;
            while (aHolder.mParticleSystems.mSize > aBaseline && aWaited < 2000) {
                tick();
                aWaited++;
            }

            PvzpParticleSystem aCloud = thePlant.addAttachedParticle(thePlant.mX + 85, thePlant.mY + 31,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, thePlant.mRow, 0),
                    ParticleEffect.PARTICLE_FUMECLOUD);
            for (int i = 0; i < 40; i++) {
                tick();
            }
            PvzpParticleSystem aSystem = mApp.particleTryToGet(thePlant.mParticleID);
            int[] pixels = capture(null);
            int aViolet = violetBand(pixels);
            if (aRound <= 2 || aRound % 5 == 0 || aCloud == null || aViolet == 0) {
                save(pixels, "fume-full-" + aRound);
                System.out.println("[fume-full] round=" + aRound + " waited=" + aWaited + " cloud=" + (aCloud != null)
                        + " systemAlive=" + (aSystem != null) + " violet=" + aViolet
                        + (aSystem == null ? "" : describeSystem(aSystem)) + describePools());
            }
            if (aCloud == null) {
                System.out.println("[fume-full] holder refused the cloud at round " + aRound + describePools());
                break;
            }
        }
        System.out.println("[fume-full] done" + describePools());
    }

    /** Violet spore pixels inside the fume band of a render-output frame (800x600 logical -> NxM out). */
    private int violetBand(int[] pixels) {
        int width = mApp.mRendererBackend.renderOutputWidth();
        int height = mApp.mRendererBackend.renderOutputHeight();
        int aCount = 0;
        for (int y = Math.min(400, height - 1); y <= Math.min(540, height - 1); y++) {
            for (int x = Math.min(600, width - 1); x <= Math.min(1190, width - 1); x++) {
                int aPixel = pixels[y * width + x];
                int aRed = (aPixel >> 16) & 0xFF;
                int aGreen = (aPixel >> 8) & 0xFF;
                int aBlue = aPixel & 0xFF;
                if (aBlue - aGreen > 25 && aBlue > 90 && aRed - aGreen > 10) {
                    aCount++;
                }
            }
        }
        return aCount;
    }

    /** Per-emitter particle count and the identity/live state of the sprite the batch samples. */
    private static String describeSystem(PvzpParticleSystem theSystem) {
        StringBuilder aText = new StringBuilder();
        for (PvzpListNode<Integer> aNode = theSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            PvzpParticleEmitter aEmitter = theSystem.mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
            aText.append(" particles=").append(aEmitter.mParticleList.mSize)
                    .append(" spawned=").append(aEmitter.mParticlesSpawned)
                    .append(" drawn=").append(aEmitter.mParticleList.mSize)
                    .append(" img=").append(imageState(aEmitter.mEmitterDef.mImage));
            if (aEmitter.mParticleList.mHead != null) {
                cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticle aFirst =
                        theSystem.mParticleHolder.mParticles.dataArrayGet(aEmitter.mParticleList.mHead.mValue);
                aText.append(" head=(").append(Math.round(aFirst.mPosition.x)).append(',')
                        .append(Math.round(aFirst.mPosition.y)).append(')');
            }
        }
        return aText.toString();
    }

    /** Identity plus live texture/bits state of the image a triangle batch samples. */
    private static String imageState(cn.enaium.fabricpvz.portable.sexy.graphics.Image theImage) {
        if (theImage == null) {
            return "null";
        }
        StringBuilder aText = new StringBuilder(theImage.getClass().getSimpleName());
        aText.append('(').append(theImage.mFilePath).append(')');
        if (theImage instanceof GLImage aGlImage) {
            aText.append(" texture=").append(aGlImage.mRenderData == 0 ? "0" : Long.toHexString(aGlImage.mRenderData));
            int[] aBits = aGlImage.getBits();
            aText.append(" bits=").append(aBits == null ? "null" : String.valueOf(aBits.length));
        }
        return aText.toString();
    }

    /** Occupancy of every holder pool that could dry up (sizes are upstream 1024-capacity pools). */
    private String describePools() {
        PvzpParticleHolder aParticleHolder = mApp.mEffectSystem.mParticleHolder;
        return " pools[systems=" + aParticleHolder.mParticleSystems.mSize
                + "/" + aParticleHolder.mParticleSystems.mMaxSize
                + " emitters=" + aParticleHolder.mEmitters.mSize + "/" + aParticleHolder.mEmitters.mMaxSize
                + " particles=" + aParticleHolder.mParticles.mSize + "/" + aParticleHolder.mParticles.mMaxSize
                + " reanims=" + mApp.mEffectSystem.mReanimationHolder.mReanimations.mSize
                + "/" + mApp.mEffectSystem.mReanimationHolder.mReanimations.mMaxSize
                + " attachments=" + mApp.mEffectSystem.mAttachmentHolder.mAttachments.mSize
                + "/" + mApp.mEffectSystem.mAttachmentHolder.mAttachments.mMaxSize + "]";
    }

    /** Renders one parsed float track as {@code low..high@time / ...}. */
    private static String track(FloatParameterTrack theTrack) {
        if (theTrack == null || theTrack.mCountNodes == 0) {
            return "-";
        }
        StringBuilder aText = new StringBuilder();
        for (FloatParameterTrackNode aNode : theTrack.mNodes) {
            if (aText.length() > 0) {
                aText.append(" / ");
            }
            aText.append(aNode.mLowValue).append("..").append(aNode.mHighValue).append('@').append(aNode.mTime);
        }
        return aText.toString();
    }

    /**
     * Every surface the seed-packet background is drawn on, in one run: the seven bank slots, an
     * almanac plant page, a slot-machine drop ({@code ChallengeSlotMachine} spawns that coin exactly
     * this way) and the level-award packet the user reports as correct
     * ({@code COIN_FINAL_SEED_PACKET}, at scale 1 and at scale 2). Each surface prints the ARGB of
     * the same packet-relative pixels so they can be compared one to one.
     */
    private void packetbg() throws Exception {
        Board aBoard = mApp.mBoard;
        SeedBank aBank = aBoard.mSeedBank;

        // A level with a full seven-slot bank (the packet upgrade) and enough progress for the almanac
        // to list every adventure plant.
        mApp.mPlayerInfo.mLevel = 50;
        mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_PACKET_UPGRADE] = 1;
        int[] aSeeds = {SeedType.SEED_SUNFLOWER, SeedType.SEED_PEASHOOTER, SeedType.SEED_CHERRYBOMB,
                SeedType.SEED_WALLNUT, SeedType.SEED_POTATOMINE, SeedType.SEED_SNOWPEA, SeedType.SEED_REPEATER};
        aBank.mNumPackets = aSeeds.length;
        for (int i = 0; i < aSeeds.length; i++) {
            SeedPacket aPacket = aBank.mSeedPackets[i];
            aPacket.mPacketType = aSeeds[i];
            aPacket.mImitaterType = SeedType.SEED_NONE;
            aPacket.mRefreshCounter = 0;
            aPacket.mRefreshTime = 0;
            aPacket.mRefreshing = false;
            aPacket.mActive = true;
            aPacket.mOffsetX = 0;
        }
        aBank.updateWidth();
        for (int i = 0; i < 20; i++) {
            tick();
        }

        System.out.println("[packetbg] bank packets=" + aBank.mNumPackets + " w=" + aBank.mWidth
                + " bank=" + aBank.mX + "," + aBank.mY + " board=" + aBoard.mX + "," + aBoard.mY
                + " render=" + mApp.mRendererBackend.renderOutputWidth() + "x"
                + mApp.mRendererBackend.renderOutputHeight() + " logical=" + mApp.mWidth + "x" + mApp.mHeight
                + " present=" + java.util.Arrays.toString(mApp.mRendererBackend.logicalPresentationRect()));

        int[] aBankPixels = capture(null);
        save(aBankPixels, "bank");
        saveCrop(aBankPixels, "bank", 80, 0, 500, 92, 4);
        for (int i = 0; i < aBank.mNumPackets; i++) {
            SeedPacket aPacket = aBank.mSeedPackets[i];
            probe(aBankPixels, "bank-slot" + i,
                    aBoard.mX + aBank.mX + aPacket.mX + aPacket.mOffsetX, aBoard.mY + aBank.mY + aPacket.mY,
                    aPacket.mWidth, aPacket.mHeight);
        }

        // Almanac plant page (AlmanacDialog::DrawPlants, AlmanacDialog.java:360).
        AlmanacDialog aAlmanac = mApp.doAlmanacDialog(SeedType.SEED_PEASHOOTER, ZombieType.ZOMBIE_NORMAL);
        aAlmanac.mOpenPage = AlmanacPage.ALMANAC_PAGE_PLANTS;
        aAlmanac.mSelectedSeed = SeedType.SEED_PEASHOOTER;
        for (int i = 0; i < 20; i++) {
            tick();
        }
        int[] anAlmanacPixels = capture(null);
        save(anAlmanacPixels, "almanac");
        saveCrop(anAlmanacPixels, "almanac", 0, 80, 480, 500, 2);
        System.out.println("[packetbg] almanac dialog=" + aAlmanac.mX + "," + aAlmanac.mY
                + " " + aAlmanac.mWidth + "x" + aAlmanac.mHeight + " page=" + aAlmanac.mOpenPage);
        Point aPoint = new Point();
        AlmanacDialog.getSeedPosition(SeedType.SEED_PEASHOOTER, aPoint);
        probe(anAlmanacPixels, "almanac-peashooter", aAlmanac.mX + aPoint.mX, aAlmanac.mY + aPoint.mY, 50, 70);
        mApp.killAlmanacDialog();

        // The slot-machine drop (ChallengeSlotMachine.java:78 spawns exactly this coin).
        for (int i = 0; i < 20; i++) {
            tick();
        }
        Coin aDrop = aBoard.addCoin(360, 85, CoinType.COIN_USABLE_SEED_PACKET, CoinMotion.COIN_MOTION_COIN);
        aDrop.mUsableSeedType = SeedType.SEED_PEASHOOTER;
        for (int i = 0; i < 60; i++) {
            tick();
        }
        int[] aDropPixels = capture(null);
        save(aDropPixels, "slot-drop");
        probe(aDropPixels, "slot-drop", aBoard.mX + (int) aDrop.mPosX, aBoard.mY + (int) aDrop.mPosY, 50, 70);

        // Control: the level-award packet (Challenge::SpawnLevelAward, Challenge.java:945) at scale 1
        // (IMAGE_SEEDS, the same cel path the three broken surfaces use) and at scale 2
        // (IMAGE_SEEDPACKET_LARGER, Coin.java:707-711) - the surface the user reports as correct.
        Coin anAward = aBoard.addCoin(560, 300, CoinType.COIN_FINAL_SEED_PACKET, CoinMotion.COIN_MOTION_COIN);
        for (int i = 0; i < 30; i++) {
            tick();
        }
        for (float aScale : new float[] {1.0f, 2.0f}) {
            anAward.mScale = aScale;
            for (int i = 0; i < 5; i++) {
                tick();
            }
            int[] aPixels = capture(null);
            String aName = "award-" + (int) aScale + "x";
            save(aPixels, aName);
            int aDrawX = aBoard.mX
                    + Math.round(0.5f * (anAward.mWidth - aScale * anAward.mWidth) + anAward.mPosX);
            int aDrawY = aBoard.mY
                    + Math.round(0.5f * (anAward.mHeight - aScale * anAward.mHeight) + anAward.mPosY);
            probe(aPixels, aName, aDrawX, aDrawY,
                    Math.round(aScale * anAward.mWidth), Math.round(aScale * anAward.mHeight));
        }
    }

    /**
     * Prints the pixels that decide whether a packet's background blended with what is behind it: the
     * four corners, the two side edges, the centre, and the pixels just outside the left and right
     * edges (where the bank's wood / the almanac's back / the lawn must show through).
     *
     * <p>Logical game coordinates map to output pixels with {@code output = logical *
     * renderOutputWidth / mWidth} and no offset ({@code readTexturePixels(0, ..)} starts at the canvas
     * origin). Callers include the board's own offset for board-drawn objects.
     */
    private void probe(int[] thePixels, String theLabel, int theX, int theY, int theWidth, int theHeight) {
        int aOutWidth = mApp.mRendererBackend.renderOutputWidth();
        float aScaleX = (float) aOutWidth / mApp.mWidth;
        float aScaleY = (float) mApp.mRendererBackend.renderOutputHeight() / mApp.mHeight;
        int aX = Math.round(theX * aScaleX);
        int aY = Math.round(theY * aScaleY);
        int aW = Math.max(1, Math.round(theWidth * aScaleX));
        int aH = Math.max(1, Math.round(theHeight * aScaleY));
        StringBuilder aText = new StringBuilder();
        aText.append("[").append(theLabel).append("] logical=").append(theX).append(',').append(theY)
                .append(" ").append(theWidth).append('x').append(theHeight)
                .append(" -> output=").append(aX).append(',').append(aY).append(' ').append(aW).append('x').append(aH)
                .append(" scale=").append(String.format("%.4f", aScaleX));
        aText.append("\n  corners TL=").append(argbAt(thePixels, aOutWidth, aX, aY))
                .append(" TR=").append(argbAt(thePixels, aOutWidth, aX + aW - 1, aY))
                .append(" BL=").append(argbAt(thePixels, aOutWidth, aX, aY + aH - 1))
                .append(" BR=").append(argbAt(thePixels, aOutWidth, aX + aW - 1, aY + aH - 1));
        aText.append("\n  edges L=").append(argbAt(thePixels, aOutWidth, aX, aY + aH / 2))
                .append(" R=").append(argbAt(thePixels, aOutWidth, aX + aW - 1, aY + aH / 2))
                .append(" T=").append(argbAt(thePixels, aOutWidth, aX + aW / 2, aY))
                .append(" B=").append(argbAt(thePixels, aOutWidth, aX + aW / 2, aY + aH - 1))
                .append(" centre=").append(argbAt(thePixels, aOutWidth, aX + aW / 2, aY + aH / 2));
        aText.append("\n  behind L=").append(argbAt(thePixels, aOutWidth, aX - 2, aY + aH / 2))
                .append(" R=").append(argbAt(thePixels, aOutWidth, aX + aW + 1, aY + aH / 2))
                .append(" TL=").append(argbAt(thePixels, aOutWidth, aX - 2, aY - 2))
                .append(" BR=").append(argbAt(thePixels, aOutWidth, aX + aW + 1, aY + aH + 1));
        System.out.println(aText);
    }

    private static String argbAt(int[] thePixels, int theWidth, int theX, int theY) {
        int aHeight = thePixels.length / theWidth;
        if (theX < 0 || theY < 0 || theX >= theWidth || theY >= aHeight) {
            return "--------";
        }
        return String.format("%08x", thePixels[theY * theWidth + theX]);
    }

    private void packet() throws Exception {
        mApp.mBoard.mSeedBank.mNumPackets = 3;
        mApp.mBoard.mSeedBank.mSeedPackets[0].setPacketType(SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE);
        mApp.mBoard.mSeedBank.mSeedPackets[1].setPacketType(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        mApp.mBoard.mSeedBank.mSeedPackets[2].setPacketType(SeedType.SEED_CHERRYBOMB, SeedType.SEED_NONE);
        for (int i = 0; i < 20; i++) {
            tick();
        }
        mBaseline = capture("baseline");
        System.out.println("[packet] render=" + mApp.mRendererBackend.renderOutputWidth() + "x"
                + mApp.mRendererBackend.renderOutputHeight() + " logical=" + mApp.mWidth + "x" + mApp.mHeight);

        // Pick the packet up (SeedPacket::MouseDown ends in Deactivate) and then plant it.
        mApp.mBoard.mSeedBank.mSeedPackets[1].mouseDown(0, 0, 1);
        System.out.println("[packet] after mouseDown active=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mActive);
        mApp.mBoard.mSeedBank.mSeedPackets[1].wasPlanted();
        System.out.println("[packet] refreshTime=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mRefreshTime
                + " active=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mActive);
        System.out.println("[packet] bank at " + mApp.mBoard.mSeedBank.mX + "," + mApp.mBoard.mSeedBank.mY
                + " packets at " + mApp.mBoard.mSeedBank.mSeedPackets[0].mX + ","
                + mApp.mBoard.mSeedBank.mSeedPackets[1].mX + ","
                + mApp.mBoard.mSeedBank.mSeedPackets[2].mX
                + " boardX=" + mApp.mBoard.mX + " boardY=" + mApp.mBoard.mY);

        int[] earlyPixels = null;
        int[] latePixels = null;
        int earlyCounter = -1;
        int lateCounter = -1;
        for (int tick = 0; tick < 900 && mApp.mBoard.mSeedBank.mSeedPackets[1].mRefreshing; tick++) {
            tick();
            int counter = mApp.mBoard.mSeedBank.mSeedPackets[1].mRefreshCounter;
            if (earlyPixels == null && counter >= 5) {
                earlyPixels = capture(null);
                earlyCounter = counter;
            }
            if (latePixels == null && counter >= 600) {
                latePixels = capture(null);
                lateCounter = counter;
            }
            if (counter > 0 && counter % 100 == 0) {
                int[] pixels = capture(null);
                save(pixels, "packet-counter-" + counter);
                saveCrop(pixels, "packet-counter-" + counter, 530, 0, 660, 130, 6);
                System.out.println("[packet] tick=" + tick + " counter=" + counter
                        + " active=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mActive
                        + " changed=" + changed(mBaseline, pixels));
            }
        }
        int[] pixels = capture(null);
        save(pixels, "packet-ready");
        saveCrop(pixels, "packet-ready", 530, 0, 660, 130, 6);
        System.out.println("[packet] ready counter=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mRefreshCounter
                + " refreshing=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mRefreshing
                + " active=" + mApp.mBoard.mSeedBank.mSeedPackets[1].mActive
                + " changed=" + changed(mBaseline, pixels));

        if (earlyPixels != null && latePixels != null) {
            System.out.println("[packet] early counter=" + earlyCounter + " vs late counter=" + lateCounter
                    + " diffRows=" + diffRows(earlyPixels, latePixels, 0, 200));
        }
    }

    /** Per-row diff of two frames inside one y band: the wipe boundary shows up as a changing row run. */
    private String diffRows(int[] a, int[] b, int y0, int y1) {
        int x0 = 530;
        int x1 = 660;
        int width = mApp.mRendererBackend.renderOutputWidth();
        StringBuilder sb = new StringBuilder();
        int rows = 0;
        for (int y = y0; y < y1; y++) {
            int count = 0;
            int minX = Integer.MAX_VALUE;
            int maxX = -1;
            for (int x = x0; x < x1; x++) {
                if (a[y * width + x] != b[y * width + x]) {
                    count++;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
            if (count > 0) {
                rows++;
                if (rows <= 40) {
                    sb.append("y=").append(y).append(" n=").append(count)
                            .append(" x=").append(minX).append("..").append(maxX).append("; ");
                }
            }
        }
        return "rowsWithDiff=" + rows + " " + sb;
    }

    /**
     * Plays the level intro to its end at the game's own pace (10 cutscene ticks per frame), supplying
     * only the interactions a player would: "Let's Rock" on the seed chooser and the clicks that
     * advance Crazy Dave's dialog. Everything else — the mower placement window, the board pan back to
     * x=0, the seed bank slide, the grave stones — has to come from the real cutscene.
     */
    private void playIntro(LawnApp theApp) throws InterruptedException {
        int aTicks = 0;
        while (theApp.mGameScene != GameScenes.SCENE_PLAYING && aTicks < 6000) {
            tick();
            aTicks++;
            CutScene aCutScene = theApp.mBoard.mCutScene;
            if (aCutScene.mSeedChoosing) {
                aCutScene.endSeedChooser();
            }
            int aClicks = 0;
            while (theApp.mCrazyDaveMessageIndex != -1 && aClicks++ < 100) {
                aCutScene.advanceCrazyDaveDialog(false);
            }
        }
        System.out.println("[intro] ticks=" + aTicks + " scene=" + theApp.mGameScene
                + " boardX=" + theApp.mBoard.mX + " mowers=" + theApp.mBoard.mLawnMowers.mSize);
    }

    // ---------------------------------------------------------------------------------------

    /**
     * The zomboni's two particle effects, four moments in one level: a fresh drive (the control),
     * the exhaust smoke its body damage spawns, the same exhaust smoke while it drives on flat tires
     * (attached to the body reanim track), and the explosion of the mower collision.
     *
     * <p>Upstream spawn sites: {@code Zombie::UpdateZamboni} (Zombie.cpp:4023-4053) has no particle;
     * the smoke comes from {@code Zombie::TakeBodyDamage} at body damage index 2
     * (Zombie.cpp:7863-7867, {@code AddAttachedParticle(27, 72, PARTICLE_ZAMBONI_SMOKE)}) and from
     * {@code ZamboniDeath}'s flat-tire branch (Zombie.cpp:6514-6523,
     * {@code AttachParticleToTrack("zombie_zamboni_1", 35, 85)}); the explosions come from
     * {@code ZamboniDeath}'s else branch (Zombie.cpp:6528) and {@code Zombie::MowDown}
     * (Zombie.cpp:8571-8578) plus the phase-counter expiry in {@code Zombie::UpdateDeath}
     * (Zombie.cpp:9251-9266).
     *
     * <p>The zomboni is pinned at one x, so its own pixels do not move between frames: everything the
     * probes report inside the box is a particle (the drive animation of the truck itself lives
     * inside the same box, so the reported "grey" count of the control frame is the base line).
     */
    private void zomboni() throws Exception {
        Board aBoard = mApp.mBoard;
        // No wave zombies in the frames below; every zombie here is spawned by this scenario.
        aBoard.mZombieCountDown = 1000000;
        System.out.println("[zomboni] board=" + aBoard.mX + "," + aBoard.mY + " level=" + aBoard.mLevel
                + " rows=" + aBoard.mPlantRow.length + " mowers=" + aBoard.mLawnMowers.mSize);
        for (LawnMower aMower : aBoard.mLawnMowers) {
            System.out.println("[zomboni] mower row=" + aMower.mRow + " x=" + aMower.mPosX
                    + " state=" + aMower.mMowerState);
        }

        // Row 1 is a land row of this pool level (rows 2-3 are the pool; a zomboni only drives on
        // land), which also keeps the animated water out of the probe box below.
        Zombie aZomboni = aBoard.addZombieInRow(ZombieType.ZOMBIE_ZAMBONI, 1, Zombie.ZOMBIE_WAVE_DEBUG);
        if (aZomboni == null) {
            System.out.println("[zomboni] spawn failed");
            return;
        }
        pinZombie(aZomboni, 620.0f);
        for (int i = 0; i < 30; i++) {
            tick();
            pinZombie(aZomboni, 620.0f);
        }
        System.out.println("[zomboni] drive zombie=" + aZomboni.mX + "," + aZomboni.mY
                + " posX=" + aZomboni.mPosX + " health=" + aZomboni.mBodyHealth + "/" + aZomboni.mBodyMaxHealth
                + " damageIndex=" + aZomboni.getBodyDamageIndex() + " phase=" + aZomboni.mZombiePhase
                + " flatTires=" + aZomboni.mFlatTires + " attachments=" + aZomboni.mAttachmentID
                + " anim=" + animName(aZomboni));
        mBaseline = capture("zomboni-drive-baseline");
        report("zomboni-drive-baseline", aZomboni);

        // (a) Fresh drive: upstream spawns nothing here, so this is the control.
        for (int i = 1; i <= 4; i++) {
            driveZomboni(aZomboni, 30, 620.0f);
            report("zomboni-drive-" + (i * 30), aZomboni);
        }

        probeSystemPixels("zomboni-probe-drive");

        // (b) Body damage index 2 -> the exhaust smoke of Zombie::TakeBodyDamage (Zombie.cpp:7863).
        aZomboni.takeDamage(1000, 0);
        System.out.println("[zomboni] damaged health=" + aZomboni.mBodyHealth
                + " damageIndex=" + aZomboni.getBodyDamageIndex() + " attachments=" + aZomboni.mAttachmentID
                + " systems=" + zomboniSystems());
        for (int i = 1; i <= 4; i++) {
            driveZomboni(aZomboni, 30, 620.0f);
            report("zomboni-damaged-" + (i * 30), aZomboni);
        }

        probeSystemPixels("zomboni-probe-damaged");

        // (c) Flat tires (spike damage) -> mFlatTires, the wheelie anim and the exhaust smoke attached
        // to the body reanim track while it drives. 620 keeps it above the wheelie2 branch's mPosX<600
        // so the smoke branch (ZamboniDeath, Zombie.cpp:6514-6523) is the one exercised.
        aZomboni.zamboniDeath(1 << DamageFlags.DAMAGE_SPIKE);
        System.out.println("[zomboni] flatTires=" + aZomboni.mFlatTires + " phase=" + aZomboni.mZombiePhase
                + " phaseCounter=" + aZomboni.mPhaseCounter + " attachments=" + aZomboni.mAttachmentID
                + " anim=" + animName(aZomboni) + " systems=" + zomboniSystems());
        for (int i = 1; i <= 4; i++) {
            driveZomboni(aZomboni, 30, 620.0f);
            report("zomboni-wheelie-" + (i * 30), aZomboni);
        }

        // (c2) The attached exhaust smoke, isolated: the same held wheelie pose is captured with the
        // track smoke alive and after the game's own detach call has let it fade out. Nothing else in
        // the zomboni's pose changes over those 60 ticks (anim_wheelie1 is PLAY_ONCE_AND_HOLD), so the
        // pixels that differ inside the box above the truck are the smoke the attachment drew.
        Reanimation aBodyReanim = mApp.reanimationTryToGet(aZomboni.mBodyReanimID);
        int[] aSmokeWith = capture(null);
        save(aSmokeWith, "zomboni-attached-with");
        System.out.println("[zomboni] attached with grey=" + greyBand(aSmokeWith, 560, 20, 260, 190)
                + " " + zomboniSystems());
        Attachment.attachmentDetachCrossFadeParticleType(
                aBodyReanim.getTrackInstanceByName("zombie_zamboni_1").mAttachmentID,
                ParticleEffect.PARTICLE_ZAMBONI_SMOKE, null);
        for (int t = 0; t < 60; t++) {
            tick();
            pinZombie(aZomboni, 620.0f);
        }
        int[] aSmokeWithout = capture(null);
        save(aSmokeWithout, "zomboni-attached-without");
        System.out.println("[zomboni] attached without grey=" + greyBand(aSmokeWithout, 560, 20, 260, 190)
                + " diff=" + diffBand(aSmokeWith, aSmokeWithout, 560, 20, 260, 190)
                + " bbox=" + java.util.Arrays.toString(changedBox(aSmokeWith, aSmokeWithout))
                + " " + zomboniSystems());

        probeSystemPixels("zomboni-probe-wheelie");

        // (d) The dying phase counter runs out -> the explosion of Zombie::UpdateDeath
        // (Zombie.cpp:9251-9266), captured from the tick the zomboni dies.
        int aDeathTick = -1;
        int aCaptured = 0;
        int[] aBeforeDeath = null;
        for (int i = 0; i < 600; i++) {
            tick();
            pinZombie(aZomboni, 620.0f);
            if (aZomboni.mDead || aZomboni.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED) {
                if (aDeathTick < 0) {
                    aDeathTick = i;
                    // The explosion system itself is not attached to the body reanim, so the frame
                    // just before the death is the before-picture for the diff below.
                    aBeforeDeath = capture(null);
                    System.out.println("[zomboni] died at tick=" + i + " phase=" + aZomboni.mZombiePhase
                            + " pools=" + describePools());
                }
            }
            if (aDeathTick >= 0 && i - aDeathTick <= 60 && (i - aDeathTick) % 15 == 0) {
                int[] pixels = capture(null);
                save(pixels, "zomboni-explosion-" + (i - aDeathTick));
                aCaptured++;
                System.out.println("[zomboni] explosion age=" + (i - aDeathTick)
                        + " grey=" + greyBand(pixels, 620 - 150 + aBoard.mX, (int) aZomboni.mY - 200 + aBoard.mY,
                                400, 300)
                        + " diff=" + (aBeforeDeath == null ? -1 : diffBand(aBeforeDeath, pixels,
                                620 - 150 + aBoard.mX, (int) aZomboni.mY - 200 + aBoard.mY, 400, 300))
                        + " pools=" + describePools() + zomboniSystems());
            }
            if (aDeathTick >= 0 && i - aDeathTick > 60) {
                break;
            }
        }
        probeSystemPixels("zomboni-probe-explosion");
        System.out.println("[zomboni] explosion frames=" + aCaptured + " deathTick=" + aDeathTick);

        // (e) The lawnmower collision: a fresh zomboni driven into the row's mower
        // (LawnMower::Update -> MowZombie -> Zombie::MowDown, Zombie.cpp:8558-8578).
        Zombie aMowed = aBoard.addZombieInRow(ZombieType.ZOMBIE_ZAMBONI, 1, Zombie.ZOMBIE_WAVE_DEBUG);
        if (aMowed == null) {
            System.out.println("[zomboni] mower probe: spawn failed");
            return;
        }
        // The mower's attack rect is (mPosX, mPosY, 50, 80) (LawnMower.cpp:417-420) and a ready mower
        // sits at mPosX -21, so the zomboni has to be inside x < 29 for the overlap to be non-zero.
        pinZombie(aMowed, 10.0f);
        for (int i = 0; i < 20; i++) {
            tick();
            pinZombie(aMowed, 10.0f);
        }
        int[] aBeforeMow = capture("zomboni-mow-before");
        System.out.println("[zomboni] mow probe zombie=" + aMowed.mX + "," + aMowed.mY
                + " dead=" + aMowed.mDead + " phase=" + aMowed.mZombiePhase
                + " health=" + aMowed.mBodyHealth + " mower=" + mowerState(aBoard, aMowed.mRow)
                + " grey=" + greyBand(aBeforeMow, 10 - 150 + aBoard.mX,
                        (int) aMowed.mY - 200 + aBoard.mY, 400, 300) + " pools=" + describePools()
                + zomboniSystems());
        for (int i = 1; i <= 8; i++) {
            for (int t = 0; t < 10; t++) {
                tick();
                pinZombie(aMowed, 10.0f);
            }
            int[] pixels = capture(null);
            save(pixels, "zomboni-mow-" + (i * 10));
            System.out.println("[zomboni] mow tick=" + (i * 10) + " dead=" + aMowed.mDead
                    + " phase=" + aMowed.mZombiePhase + " mower=" + mowerState(aBoard, aMowed.mRow)
                    + " grey=" + greyBand(pixels, 10 - 150 + aBoard.mX, (int) aMowed.mY - 200 + aBoard.mY, 400, 300)
                    + " diff=" + diffBand(aBeforeMow, pixels, 10 - 150 + aBoard.mX,
                        (int) aMowed.mY - 200 + aBoard.mY, 400, 300)
                    + " changedAll=" + changed(aBeforeMow, pixels) + " pools=" + describePools()
                    + zomboniSystems());
        }
        probeSystemPixels("zomboni-probe-mow");

        // (f) The definition on bare lawn, with no zombie and no attachment in the way: the same
        // PvzpParticleSystem the two spawn sites above create, drawn at a fixed spot where the only
        // thing that can add bright, unsaturated pixels is smoke.
        int[] aLawnBefore = capture("zomboni-smoke-lawn-before");
        System.out.println("[zomboni] lawn before grey=" + greyBand(aLawnBefore, 240, 90, 160, 140)
                + " " + describePools());
        PvzpParticleSystem aDirect = mApp.addPvzpParticle(300.0f, 150.0f,
                Board.makeRenderOrder(RenderLayer.RENDER_LAYER_PARTICLE, 1, 0), ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
        System.out.println("[zomboni] lawn system=" + (aDirect != null)
                + " particles=" + (aDirect == null ? -1 : particleCount(aDirect)) + " " + zomboniSystems());
        for (int i = 1; i <= 5; i++) {
            for (int t = 0; t < 20; t++) {
                tick();
            }
            int[] pixels = capture(null);
            save(pixels, "zomboni-smoke-lawn-" + (i * 20));
            System.out.println("[zomboni] lawn tick=" + (i * 20) + " grey=" + greyBand(pixels, 240, 90, 160, 140)
                    + " diff=" + diffBand(aLawnBefore, pixels, 240, 90, 160, 140)
                    + " particles=" + (aDirect == null ? -1 : particleCount(aDirect)));
        }
    }

    /** Live particles of one system (all its emitters), or -1 when it is gone. */
    private static int particleCount(PvzpParticleSystem theSystem) {
        int aCount = 0;
        for (PvzpListNode<Integer> aNode = theSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
            aCount += theSystem.mParticleHolder.mEmitters.dataArrayGet(aNode.mValue).mParticleList.mSize;
        }
        return aCount;
    }

    /** The state/position of the mower of one row, or {@code none}. */
    private static String mowerState(Board theBoard, int theRow) {
        LawnMower aMower = theBoard.findLawnMowerInRow(theRow);
        if (aMower == null) {
            return "none";
        }
        return aMower.mMowerState + "@" + Math.round(aMower.mPosX);
    }

    /** Holds a zomboni at one x so its own pixels are identical from frame to frame. */
    private static void pinZombie(Zombie theZombie, float theX) {
        theZombie.mPosX = theX;
        theZombie.mX = (int) theX;
    }

    private void driveZomboni(Zombie theZombie, int theTicks, float theX) throws InterruptedException {
        for (int i = 0; i < theTicks; i++) {
            tick();
            pinZombie(theZombie, theX);
        }
    }

    /**
     * Differential probe for "does this effect reach the screen": renders the frame, kills every live
     * zomboni-effect system and renders the frame again. Nothing ticks in between, so the two frames
     * differ by exactly the pixels those particles contributed — the reading that separates "the
     * system is alive with N particles" (see {@link #zomboniSystems()}) from "the system draws".
     */
    private void probeSystemPixels(String theLabel) throws Exception {
        redraw();
        int[] aWith = captureStable(theLabel + "-with");
        save(aWith, theLabel + "-with");
        int aKilled = 0;
        int aParticles = 0;
        for (PvzpParticleSystem aSystem : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            int aType = aSystem.mEffectType;
            if (aType == ParticleEffect.PARTICLE_ZAMBONI_SMOKE
                    || aType == ParticleEffect.PARTICLE_ZAMBONI_TIRE
                    || aType == ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION
                    || aType == ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION2) {
                for (PvzpListNode<Integer> aNode = aSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
                    aParticles += aSystem.mParticleHolder.mEmitters.dataArrayGet(aNode.mValue).mParticleList.mSize;
                }
                aSystem.particleSystemDie();
                aKilled++;
            }
        }
        if (aKilled == 0) {
            System.out.println("[zomboni] " + theLabel + " no live zomboni system to remove");
            return;
        }
        redraw();
        int[] aWithout = captureStable(theLabel + "-without");
        save(aWithout, theLabel + "-without");
        int[] aBox = changedBox(aWith, aWithout);
        System.out.println("[zomboni] " + theLabel + " killedSystems=" + aKilled + " particles=" + aParticles
                + " particlePixels=" + changed(aWith, aWithout)
                + " bbox=" + java.util.Arrays.toString(aBox));
    }

    /** Redraws the current (unticked) scene so a second capture really reflects the current state. */
    private void redraw() throws InterruptedException {
        mApp.mWidgetManager.drawScreen();
        Thread.sleep(2);
    }

    /** Bounding box of the pixels two frames differ in: {@code [x, y, w, h]} or {@code [-1,-1,0,0]}. */
    private int[] changedBox(int[] a, int[] b) {
        int aWidth = mApp.mRendererBackend.renderOutputWidth();
        int aHeight = a.length / aWidth;
        int aMinX = Integer.MAX_VALUE;
        int aMinY = Integer.MAX_VALUE;
        int aMaxX = -1;
        int aMaxY = -1;
        for (int y = 0; y < aHeight; y++) {
            for (int x = 0; x < aWidth; x++) {
                int aIndex = y * aWidth + x;
                if (a[aIndex] != b[aIndex]) {
                    aMinX = Math.min(aMinX, x);
                    aMinY = Math.min(aMinY, y);
                    aMaxX = Math.max(aMaxX, x);
                    aMaxY = Math.max(aMaxY, y);
                }
            }
        }
        if (aMaxX < 0) {
            return new int[] {-1, -1, 0, 0};
        }
        return new int[] {aMinX, aMinY, aMaxX - aMinX + 1, aMaxY - aMinY + 1};
    }

    /** Saves one frame and reports what the zomboni's box contains and which effects are alive. */
    private void report(String theName, Zombie theZombie) throws Exception {
        int[] pixels = capture(null);
        save(pixels, theName);
        int aX = (int) theZombie.mPosX - 150 + mApp.mBoard.mX;
        int aY = (int) theZombie.mY - 200 + mApp.mBoard.mY;
        System.out.println("[zomboni] " + theName + " health=" + theZombie.mBodyHealth
                + " damageIndex=" + theZombie.getBodyDamageIndex() + " phase=" + theZombie.mZombiePhase
                + " flatTires=" + theZombie.mFlatTires + " anim=" + animName(theZombie)
                + " attachments=" + theZombie.mAttachmentID + " systems=" + zomboniSystems()
                + " grey=" + greyBand(pixels, aX, aY, 400, 300)
                + " diff=" + diffBand(mBaseline, pixels, aX, aY, 400, 300)
                + " changedAll=" + changed(mBaseline, pixels) + " " + describePools());
    }

    /** Which of the zomboni's body reanim states is currently showing. */
    private static String animName(Zombie theZombie) {
        Reanimation aReanim = theZombie.mApp.reanimationTryToGet(theZombie.mBodyReanimID);
        if (aReanim == null) {
            return "-";
        }
        return "drive=" + aReanim.isTrackShowing("anim_drive")
                + ",wheelie1=" + aReanim.isTrackShowing("anim_wheelie1")
                + ",wheelie2=" + aReanim.isTrackShowing("anim_wheelie2");
    }

    /**
     * Every live particle system of the zomboni's effects, with its emitters' particle counts, the
     * identity/texture state of the sprite those particles sample, and the system time: the reading
     * that separates "the system was never spawned" from "the system runs and nothing reaches the
     * screen".
     */
    private String zomboniSystems() {
        StringBuilder aText = new StringBuilder();
        for (PvzpParticleSystem aSystem : mApp.mEffectSystem.mParticleHolder.mParticleSystems) {
            int aType = aSystem.mEffectType;
            if (aType != ParticleEffect.PARTICLE_ZAMBONI_SMOKE
                    && aType != ParticleEffect.PARTICLE_ZAMBONI_TIRE
                    && aType != ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION
                    && aType != ParticleEffect.PARTICLE_ZAMBONI_EXPLOSION2) {
                continue;
            }
            aText.append(" [type=").append(aType).append(" attachment=").append(aSystem.mIsAttachment)
                    .append(" order=").append(aSystem.mRenderOrder)
                    .append(" emitters=").append(aSystem.mEmitterList.mSize);
            for (PvzpListNode<Integer> aNode = aSystem.mEmitterList.mHead; aNode != null; aNode = aNode.mNext) {
                PvzpParticleEmitter aEmitter = aSystem.mParticleHolder.mEmitters.dataArrayGet(aNode.mValue);
                aText.append(" {n=").append(aEmitter.mParticleList.mSize)
                        .append(" spawned=").append(aEmitter.mParticlesSpawned)
                        .append(" sysTime=").append(aEmitter.mSystemTimeValue)
                        .append(" img=").append(imageState(aEmitter.mEmitterDef.mImage)).append('}');
            }
            aText.append(']');
        }
        return aText.length() == 0 ? " [no zomboni system alive]" : aText.toString();
    }

    /** Counts bright, unsaturated (grey/white) pixels in a logical board rectangle: the smoke sprite. */
    private int greyBand(int[] thePixels, int theLogicalX, int theLogicalY, int theLogicalW, int theLogicalH) {
        int[] aRect = outputRect(theLogicalX, theLogicalY, theLogicalW, theLogicalH);
        int aWidth = mApp.mRendererBackend.renderOutputWidth();
        int aHeight = mApp.mRendererBackend.renderOutputHeight();
        int aCount = 0;
        for (int y = Math.max(0, aRect[1]); y < Math.min(aHeight, aRect[1] + aRect[3]); y++) {
            for (int x = Math.max(0, aRect[0]); x < Math.min(aWidth, aRect[0] + aRect[2]); x++) {
                int aPixel = thePixels[y * aWidth + x];
                int aRed = (aPixel >> 16) & 0xFF;
                int aGreen = (aPixel >> 8) & 0xFF;
                int aBlue = aPixel & 0xFF;
                int aMax = Math.max(aRed, Math.max(aGreen, aBlue));
                int aMin = Math.min(aRed, Math.min(aGreen, aBlue));
                if (aMax - aMin <= 30 && aMax >= 140) {
                    aCount++;
                }
            }
        }
        return aCount;
    }

    /** Pixels that changed inside a logical board rectangle (particle coverage independent of colour). */
    private int diffBand(int[] a, int[] b, int theLogicalX, int theLogicalY, int theLogicalW, int theLogicalH) {
        int[] aRect = outputRect(theLogicalX, theLogicalY, theLogicalW, theLogicalH);
        int aWidth = mApp.mRendererBackend.renderOutputWidth();
        int aHeight = mApp.mRendererBackend.renderOutputHeight();
        int aCount = 0;
        for (int y = Math.max(0, aRect[1]); y < Math.min(aHeight, aRect[1] + aRect[3]); y++) {
            for (int x = Math.max(0, aRect[0]); x < Math.min(aWidth, aRect[0] + aRect[2]); x++) {
                if (a[y * aWidth + x] != b[y * aWidth + x]) {
                    aCount++;
                }
            }
        }
        return aCount;
    }

    /** Logical game coordinates to output pixels ({@code output = logical * renderOutput / mWidth}). */
    private int[] outputRect(int theLogicalX, int theLogicalY, int theLogicalW, int theLogicalH) {
        float aScaleX = (float) mApp.mRendererBackend.renderOutputWidth() / mApp.mWidth;
        float aScaleY = (float) mApp.mRendererBackend.renderOutputHeight() / mApp.mHeight;
        return new int[] {Math.round(theLogicalX * aScaleX), Math.round(theLogicalY * aScaleY),
                Math.max(1, Math.round(theLogicalW * aScaleX)), Math.max(1, Math.round(theLogicalH * aScaleY))};
    }

    private int countRising() {
        int n = 0;
        for (Zombie z : mApp.mBoard.mZombies) {
            if (!z.mDead && z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE) {
                n++;
            }
        }
        return n;
    }

    private void tick() throws InterruptedException {
        mApp.processDeferredMessages(false);
        mApp.doUpdateFrames();
        mApp.mWidgetManager.drawScreen();
        Thread.sleep(2);
    }

    /** Reads the window back, retrying while the buffer comes back blank (the renderer can). */
    private int[] captureStable(String name) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int[] pixels = captureRaw(name);
            int distinct = 0;
            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            for (int i = 0; i < pixels.length && distinct <= 2000; i += 7) {
                if (seen.add(pixels[i])) {
                    distinct++;
                }
            }
            if (distinct > 100) {
                return pixels;
            }
            mApp.mWidgetManager.drawScreen();
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return captureRaw(name);
    }

    private int[] capture(String name) {
        return captureStable(name);
    }

    private int[] captureRaw(String name) {
        int width = mApp.mRendererBackend.renderOutputWidth();
        int height = mApp.mRendererBackend.renderOutputHeight();
        int[] pixels = new int[width * height];
        mApp.mRendererBackend.readTexturePixels(0, width, height, pixels);
        mApp.mRendererBackend.present();
        if (name != null) {
            save(pixels, name);
        }
        return pixels;
    }

    private void save(int[] pixels, String name) {
        int width = mApp.mRendererBackend.renderOutputWidth();
        int height = mApp.mRendererBackend.renderOutputHeight();
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            Files.createDirectories(mDumpDir);
            ImageIO.write(image, "png", mDumpDir.resolve(name + ".png").toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Saves a nearest-neighbour zoom of one region, so hand-inspecting a 50x70 packet is possible. */
    private void saveCrop(int[] pixels, String name, int x0, int y0, int x1, int y1, int zoom) {
        int width = mApp.mRendererBackend.renderOutputWidth();
        int height = mApp.mRendererBackend.renderOutputHeight();
        int cropW = Math.min(x1, width) - Math.max(0, x0);
        int cropH = Math.min(y1, height) - Math.max(0, y0);
        BufferedImage image = new BufferedImage(cropW * zoom, cropH * zoom, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                int argb = pixels[(Math.max(0, y0) + y) * width + Math.max(0, x0) + x];
                for (int dy = 0; dy < zoom; dy++) {
                    for (int dx = 0; dx < zoom; dx++) {
                        image.setRGB(x * zoom + dx, y * zoom + dy, argb);
                    }
                }
            }
        }
        try {
            Files.createDirectories(mDumpDir);
            ImageIO.write(image, "png", mDumpDir.resolve(name + "-zoom.png").toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static int changed(int[] a, int[] b) {
        int n = 0;
        for (int i = 0; i < a.length && i < b.length; i++) {
            if (a[i] != b[i]) {
                n++;
            }
        }
        return n;
    }
}
