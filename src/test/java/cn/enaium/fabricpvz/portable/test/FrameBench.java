package cn.enaium.fabricpvz.portable.test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.jfr.Recording;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;

/**
 * Measures the frame loop of a real run: frames per second, update rate and the time spent in the
 * frame's draw/blit phases, plus JVM GC activity. With {@code -Dpvz.bench.jfr=<file>} it also records a
 * JFR profile so the hot paths can be read back with {@code jfr print}.
 *
 * <p>Run through {@code ./gradlew bench -Ppvz.bench.seconds=20}.
 */
public final class FrameBench {

    private FrameBench() {
    }

    public static void main(String[] args) throws Exception {
        Path runDir = Path.of(System.getProperty("pvz.gameDir", ".")).toAbsolutePath();
        Path saveDir = runDir.resolve("bench-userdata");
        Files.createDirectories(saveDir);
        int seconds = Integer.getInteger("pvz.bench.seconds", 15);

        String jfrFile = System.getProperty("pvz.bench.jfr");
        Recording recording = null;
        if (jfrFile != null && !jfrFile.isEmpty()) {
            recording = new Recording();
            recording.setName("pvz-frame-bench");
            recording.enable("jdk.ExecutionSample").withPeriod(java.time.Duration.ofMillis(2));
            recording.enable("jdk.ObjectAllocationSample").withPeriod(java.time.Duration.ofMillis(5));
            recording.start();
            System.out.println("[bench] JFR recording started");
        }

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        AtomicBoolean running = new AtomicBoolean(true);

        // Sampling only touches framework counters and the JVM's own MXBeans: never the renderer, which
        // is not thread safe (see docs/VERIFICATION.md §10.1).
        List<GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
        LawnApp appRef = app;
        Thread sampler = new Thread(() -> {
            LawnApp currentApp = appRef;
            long previousDraws = 0;
            long previousUpdates = 0;
            long previousGcCount = 0;
            long previousGcTime = 0;
            long previousUsed = 0;
            int ticks = 0;
            int minFps = Integer.MAX_VALUE;
            int maxFps = 0;
            long totalFps = 0;
            int samples = 0;
            try {
                while (running.get()) {
                    Thread.sleep(1000);
                    if (currentApp == null) {
                        currentApp = LawnApp.gLawnApp;
                        if (currentApp == null) {
                            continue;
                        }
                    }
                    ticks++;
                    if (currentApp.mDrawCount == previousDraws && ticks < 3) {
                        previousDraws = currentApp.mDrawCount;
                        continue;
                    }
                    long frames = currentApp.mDrawCount - previousDraws;
                    long updates = currentApp.mUpdateCount - previousUpdates;
                    long gcCount = 0;
                    long gcTime = 0;
                    for (GarbageCollectorMXBean aCollector : collectors) {
                        gcCount += Math.max(aCollector.getCollectionCount(), 0);
                        gcTime += Math.max(aCollector.getCollectionTime(), 0);
                    }
                    long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    java.lang.management.OperatingSystemMXBean anOs = ManagementFactory.getOperatingSystemMXBean();
                    double cpuLoad = anOs instanceof com.sun.management.OperatingSystemMXBean aComSun
                            ? aComSun.getProcessCpuLoad() : -1;
                    System.out.printf("[bench] %2ds fps=%d updates=%d blt=%dms cpu=%.0f%% gc=%d/%dms heap=%dMB%n",
                            ticks, frames, updates, currentApp.mScreenBltTime, cpuLoad * 100,
                            gcCount - previousGcCount, gcTime - previousGcTime, used / (1024 * 1024));
                    minFps = (int) Math.min(minFps, frames);
                    maxFps = (int) Math.max(maxFps, frames);
                    totalFps += frames;
                    samples++;
                    previousDraws = currentApp.mDrawCount;
                    previousUpdates = currentApp.mUpdateCount;
                    previousGcCount = gcCount;
                    previousGcTime = gcTime;
                    previousUsed = used;
                    if (ticks >= seconds) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (samples > 0) {
                    System.out.printf("[bench] summary fps min=%d avg=%.1f max=%d over %ds (final heap=%dMB)%n",
                            minFps, (double) totalFps / samples, maxFps, samples,
                            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
                }
                currentApp.mShutdown.set(true);
            }
        }, "frame-sampler");
        sampler.setDaemon(true);
        sampler.start();

        try {
            app.setArgs(new String[] {"pvz-portable", "-savedir", saveDir.toString()});
            app.init();
            int level = Integer.getInteger("pvz.bench.level", 0);
            if (level > 0) {
                app.startLoadingThread();
                long deadline = System.nanoTime() + 120_000_000_000L;
                while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
                    app.updateFrames();
                    Thread.sleep(2);
                }
                cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo aProfile = app.mProfileMgr.addProfile("Bench");
                app.mPlayerInfo = aProfile;
                app.mGameMode = level;
                app.preNewGame(level, false);
                app.startPlaying();
                System.out.println("[bench] started level mode=" + level + " scene=" + app.mGameScene);

                int stress = Integer.getInteger("pvz.bench.zombies", 0);
                if (stress > 0) {
                    // Fill the board the way a late level looks: plants in every square, then a steady
                    // stream of zombies, so the render path carries the load a real session does.
                    for (int row = 0; row < 5; row++) {
                        for (int col = 0; col < 9; col++) {
                            app.mBoard.newPlant(col, row,
                                    col % 3 == 0 ? cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_PEASHOOTER
                                            : col % 3 == 1 ? cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_SUNFLOWER
                                                    : cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_WALLNUT,
                                    cn.enaium.fabricpvz.portable.lawn.SeedType.SEED_NONE);
                        }
                    }
                    for (int i = 0; i < stress; i++) {
                        app.mBoard.addZombieInRow(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL, i % 5, 1);
                        app.mBoard.addZombieInRow(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_TRAFFIC_CONE, (i + 2) % 5, 1);
                    }
                    System.out.println("[bench] stress: " + app.mBoard.mPlants.mSize + " plants, "
                            + app.mBoard.mZombies.mSize + " zombies");
                }
            }
            if (System.getProperty("pvz.bench.cpuFrames") != null) {
                // Bypass vsync: time the pure CPU cost of a frame (update + draw) so optimisations can be
                // measured even when the loop is capped at the display's refresh rate.
                int iterations = Integer.getInteger("pvz.bench.iterations", 1000);
                com.sun.management.ThreadMXBean aThreads =
                        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                long aThreadId = Thread.currentThread().threadId();
                long aAllocatedBefore = aThreads.getThreadAllocatedBytes(aThreadId);
                for (int i = 0; i < 200; i++) {
                    app.updateFrames();
                    app.mWidgetManager.drawScreen();
                }
                long start = System.nanoTime();
                for (int i = 0; i < iterations; i++) {
                    app.updateFrames();
                    app.mWidgetManager.drawScreen();
                }
                long elapsed = System.nanoTime() - start;
                long aAllocatedAfter = aThreads.getThreadAllocatedBytes(aThreadId);
                System.out.printf("[bench] cpu frame: %.3f ms/frame over %d frames (%.0f frames/s)"
                                + " allocated=%.1f KB/frame%n",
                        elapsed / 1_000_000.0 / iterations, iterations,
                        iterations / (elapsed / 1_000_000_000.0),
                        (aAllocatedAfter - aAllocatedBefore) / 1024.0 / iterations);
                running.set(false);
            } else {
                app.start();
                sampler.join(5000);
                running.set(false);
            }
        } finally {
            if (recording != null) {
                recording.stop();
                recording.dump(Path.of(jfrFile));
                System.out.println("[bench] JFR written to " + jfrFile);
            }
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }
}
