package cn.enaium.fabricpvz.portable.contentmodfixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import cn.enaium.fabricpvz.api.PvzFabricApi;
import cn.enaium.fabricpvz.api.plant.PlantRegistry;
import cn.enaium.fabricpvz.api.zombie.ZombieRegistry;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.LawnCommon;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantingReason;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimationParams;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * "Game main class" of {@link FabricLoaderEndToEndTest}.
 *
 * <p>The test starts Fabric Loader's launcher
 * ({@code net.fabricmc.loader.impl.launch.knot.KnotClient}) in a second JVM and points the loader's
 * game provider at this class through {@code -Dpvz.game.mainClass}. Everything below therefore runs
 * inside the loader's class loader, with the mods from a real {@code mods/} directory already
 * resolved, their entry points already executed and their Mixin configurations already registered —
 * i.e. exactly where the game's own {@code Main} would run.
 *
 * <p>It reports through a file because the loader's class loader defines its own copies of the game,
 * API and mod classes: the test's copies cannot observe them directly. The report is rewritten after
 * every phase, so a failure still shows how far the run got.
 *
 * <p>Phases:
 * <ol>
 *   <li>what the loader loaded: every mod id, the API version, the example mod and the fixture mod;</li>
 *   <li>the fixture mod's registrations and how the game's own tables and a headless board see them
 *       (no game assets needed);</li>
 *   <li>the mod Mixin: the example mod's {@code @Inject} into {@code LawnCommon.modInRange}, its
 *       {@code @Accessor} on {@code Board} and the gameplay hook chain the API installed;</li>
 *   <li>the real game: boots the actual entry point sequence against the installed assets, starts
 *       Adventure level 1 and asserts {@code SCENE_PLAYING}, then ticks the board to show the mod's
 *       board Mixin runs inside the real frame loop (skipped, and reported as such, when this
 *       machine has no game data).</li>
 * </ol>
 */
public final class ContentModGameMain {

    private ContentModGameMain() {
    }

    /** Argument the test passes to name the game data directory. */
    private static final String RESDIR_ARGUMENT = "-resdir";
    /** Argument the test passes to name the writable directory. */
    private static final String SAVEDIR_ARGUMENT = "-savedir";

    /** Class of the example mod jar that the test puts into {@code mods/}. */
    private static final String EXAMPLE_MOD = "cn.enaium.fabricpvz.examplemod.ExampleMod";

    private static final StringBuilder REPORT = new StringBuilder();

    public static void main(String[] args) throws Exception {
        Path reportPath = Path.of(requiredProperty("pvz.test.report"));

        FabricLoader loader = FabricLoader.getInstance();
        report("loaderVersion", loader.getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("<missing>"));
        report("gameVersion", loader.getRawGameVersion());
        report("development", loader.isDevelopmentEnvironment());
        report("environment", loader.getEnvironmentType().name().toLowerCase(java.util.Locale.ROOT));

        List<String> modIds = new ArrayList<>();
        for (ModContainer container : loader.getAllMods()) {
            modIds.add(container.getMetadata().getId());
        }
        // Sorted: the loader deliberately shuffles mods in a development environment.
        report("modsLoaded", String.join(",", new TreeSet<>(modIds)));
        report("modCount", modIds.size());
        report("exampleModLoaded", loader.isModLoaded("pvz-example-mod"));
        report("plantModuleLoaded", loader.isModLoaded("api-plant"));
        report("zombieModuleLoaded", loader.isModLoaded("api-zombie"));
        report("fixtureModLoaded", loader.isModLoaded(ContentMod.MOD_ID));
        report("apiInitialized", PvzFabricApi.isInitialized());
        report("apiVersion", PvzFabricApi.getVersion());
        flush(reportPath);

        registrationPhase(reportPath);
        mixinAndEventPhase(reportPath);
        realGamePhase(reportPath, args);

        System.out.println("[loader-test-main] report written to " + reportPath);
    }

    /**
     * Phase 2: the plant and the zombie the fixture mod registered through the public API, as the
     * game's own definition tables and a headless board see them. Runs without game assets.
     */
    private static void registrationPhase(Path reportPath) throws Exception {
        report("plantRegistered", PlantRegistry.get("fixture_peashooter_plus").isPresent());
        report("zombieRegistered", ZombieRegistry.get("fixture_bucket_runner").isPresent());

        int seed = ContentMod.peaPlus.seedType();
        int type = ContentMod.bucketRunner.zombieType();
        report("plantSeedType", seed);
        report("zombieType", type);
        report("seedOutsideUpstream", seed >= SeedType.CUSTOM_SEED_FIRST);
        report("zombieOutsideUpstream", type >= ZombieType.CUSTOM_ZOMBIE_FIRST);

        report("definitionCost", Plant.getPlantDefinition(seed).mSeedCost);
        report("definitionReanim", Plant.getPlantDefinition(seed).mReanimationType);
        report("zombieDefinitionValue", Zombie.getZombieDefinition(type).mZombieValue);
        report("plantName", Plant.getNameString(seed, SeedType.SEED_NONE));
        report("plantTooltip", Plant.getToolTip(seed));

        // A real headless board, built the way the game's own tests build one: an app whose
        // reanimations look loaded (retail .reanim data is not needed for this phase).
        LawnApp app = new LoadedReanimApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        HeadlessTestApp.initialize(app);
        app.mPlayerInfo.mLevel = 1;
        app.mPlayerInfo.mFinishedAdventure = 0;
        Common.sRand(0x5EED);
        Board board = new Board(app);
        board.initLevel();
        report("headlessBoardLevel", board.mLevel);
        report("seedAvailable", app.hasSeedType(seed));

        // The planting rule the mod registered is consulted by Board::CanPlantAt, and only for its
        // own seed type.
        int allowedRow = ContentMod.ALLOWED_ROW;
        report("plantingRuleAllowedRow", board.canPlantAt(1, allowedRow, seed) == PlantingReason.PLANTING_OK);
        report("plantingRuleVetoedOtherRow",
                board.canPlantAt(1, allowedRow + 1, seed) == PlantingReason.PLANTING_NOT_HERE);
        report("plantingRuleCalls", ContentMod.plantingRuleCalls);
        report("upstreamPlantingUnaffected",
                board.canPlantAt(1, allowedRow, SeedType.SEED_PEASHOOTER) == PlantingReason.PLANTING_OK);

        // The mod plant is a normal plant to the game.
        Plant plant = board.addPlant(1, allowedRow, seed, SeedType.SEED_NONE);
        report("plantPlaced", plant != null);
        report("plantSeedTypeMatches", plant.mSeedType == seed);
        report("plantHealth", plant.mPlantHealth);
        report("plantMaxHealth", plant.mPlantMaxHealth);
        report("plantSubclass", plant.mSubclass);
        report("plantBodyReanimLoaded", plant.mBodyReanimID != ReanimationID.REANIMATIONID_NULL);
        report("plantPlacedCallbacks", ContentMod.plantPlacedCallbacks);
        report("boardPlantCount", plantCount(board));

        int ticksBefore = ContentMod.plantTickCallbacks;
        plant.update();
        report("plantTickCallbacks", ContentMod.plantTickCallbacks - ticksBefore);

        // The mod zombie spawns through the board's own spawner with the registered numbers.
        int spawnsBefore = ContentMod.zombieSpawnedCallbacks;
        Zombie zombie = board.addZombieInRow(type, 3, 0);
        report("zombieSpawned", zombie != null);
        report("zombieTypeMatches", zombie.mZombieType == type);
        report("zombieBodyHealth", zombie.mBodyHealth);
        report("zombieBodyMaxHealth", zombie.mBodyMaxHealth);
        report("zombieHelmType", zombie.mHelmType);
        report("zombieHelmHealth", zombie.mHelmHealth);
        report("zombieBodyReanimLoaded", zombie.mBodyReanimID != ReanimationID.REANIMATIONID_NULL);
        report("zombieSpawnedCallbacks", ContentMod.zombieSpawnedCallbacks - spawnsBefore);

        int zombieTicksBefore = ContentMod.zombieTickCallbacks;
        zombie.update();
        report("zombieTickCallbacks", ContentMod.zombieTickCallbacks - zombieTicksBefore);

        report("zombieAllowedOnLevel", board.mZombieAllowed[type]);
        report("wavesContainingModZombie", wavesContaining(board, type));
        report("upstreamZombieStillAllowed", board.mZombieAllowed[ZombieType.ZOMBIE_NORMAL]);
        report("plantReanimFile", reanimFileOf(Plant.getPlantDefinition(seed).mReanimationType));
        flush(reportPath);
    }

    /**
     * Phase 3: the example mod's Mixin really transformed game classes, and the gameplay event chain
     * the API installed reaches the mod's listeners.
     */
    private static void mixinAndEventPhase(Path reportPath) throws Exception {
        // The example mod's LawnCommonMixin injects into this static method and sets a flag.
        Object modInRange = LawnCommon.class.getMethod("modInRange", int.class, int.class).invoke(null, 7, 3);
        report("modInRangeResult", modInRange);
        report("mixinApplied", staticBoolean(EXAMPLE_MOD, "mixinApplied"));

        // The API's hook bridge fans gameplay hooks out to the mod listeners; the example mod's
        // SUN_COLLECTED listener doubles the amount.
        int hooked = LawnHooksRegistry.dispatcher().onSunCollected(null, 5, 0);
        report("sunHookResult", hooked);
        report("sunEvents", staticInt(EXAMPLE_MOD, "sunEvents"));

        // The example mod's accessor Mixin adds an interface to Board.
        Class<?> boardType = Board.class;
        boolean accessorAdded = false;
        for (Class<?> implemented : boardType.getInterfaces()) {
            if (implemented.getName().equals("cn.enaium.fabricpvz.examplemod.mixin.BoardAccessor")) {
                accessorAdded = true;
                break;
            }
        }
        report("boardImplementsAccessor", accessorAdded);
        report("boardHasUpdateGame", hasMethod(boardType, "update"));
        flush(reportPath);
    }

    /**
     * Phase 4: the real game. Boots the application exactly like the game's own entry point does,
     * starts Adventure level 1 and asserts {@code SCENE_PLAYING}, then ticks the board so the
     * example mod's board Mixin runs inside the real frame loop.
     */
    private static void realGamePhase(Path reportPath, String[] args) throws Exception {
        Optional<Path> assets = TestAssets.resourceRoot();
        report("assetsPresent", assets.isPresent());

        if (assets.isEmpty()) {
            report("gameBooted", false);
            flush(reportPath);
            return;
        }

        Path saveDir = argument(args, SAVEDIR_ARGUMENT);
        List<String> gameArgs = new ArrayList<>();
        gameArgs.add("pvz-portable");
        gameArgs.add(RESDIR_ARGUMENT);
        gameArgs.add(assets.get().toString());

        if (saveDir != null) {
            gameArgs.add(SAVEDIR_ARGUMENT);
            gameArgs.add(saveDir.toString());
        }

        int updatesBefore = staticInt(EXAMPLE_MOD, "boardUpdates");

        cn.enaium.fabricpvz.portable.lawn.Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;

        try {
            app.setArgs(gameArgs.toArray(String[]::new));
            app.init();

            // start() runs the blocking main loop; drive its two pieces by hand instead.
            app.startLoadingThread();

            long deadline = System.nanoTime() + 180_000_000_000L;
            while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
                app.updateFrames();
                Thread.sleep(2);
            }
            report("assetsLoaded", app.mLoadingThreadCompleted.get());

            PlayerInfo profile = app.mProfileMgr.addProfile("LoaderTest");
            app.mPlayerInfo = profile;
            app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
            app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);
            app.startPlaying();

            report("gameBooted", true);
            report("scenePlaying", app.mGameScene == GameScenes.SCENE_PLAYING);
            report("scene", app.mGameScene);
            report("realBoardLevel", app.mBoard == null ? -1 : app.mBoard.mLevel);
            report("realBoardWaves", app.mBoard == null ? -1 : app.mBoard.mNumWaves);

            // The registered plant and zombie are usable in the real level too.
            int seed = ContentMod.peaPlus.seedType();
            int type = ContentMod.bucketRunner.zombieType();
            report("realSeedAvailable", app.hasSeedType(seed));
            if (app.mBoard != null) {
                report("realZombieAllowed", app.mBoard.mZombieAllowed[type]);
                report("realWavesContainingModZombie", wavesContaining(app.mBoard, type));
            }

            for (int frame = 0; frame < 300; frame++) {
                app.updateFrames();
            }

            // The example mod's BoardMixin injects into Board::Update, so a running board must have
            // ticked it and recorded the sun counter it shadows.
            report("boardTicksSeenByModMixin", staticInt(EXAMPLE_MOD, "boardUpdates") - updatesBefore);
            report("modMixinSawSun", staticInt(EXAMPLE_MOD, "lastObservedSun"));
            report("realBoardSun", app.mBoard == null ? -1 : app.mBoard.mSunMoney);
            report("scenePlayingAfterTicks", app.mGameScene == GameScenes.SCENE_PLAYING);
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
            flush(reportPath);
        }
    }

    /**
     * The example mod is a mod jar from {@code mods/}, never on the test compile class path (that is
     * the point of the test), so its state is read reflectively.
     */
    private static boolean staticBoolean(String className, String fieldName) throws Exception {
        return value(className, fieldName).getBoolean(null);
    }

    private static int staticInt(String className, String fieldName) throws Exception {
        return value(className, fieldName).getInt(null);
    }

    private static java.lang.reflect.Field value(String className, String fieldName) throws Exception {
        java.lang.reflect.Field field = Class.forName(className).getField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static void report(String key, Object value) {
        REPORT.append(key).append('=').append(value).append('\n');
    }

    private static void flush(Path reportPath) throws java.io.IOException {
        Files.writeString(reportPath, REPORT.toString());
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException("The test must set -D" + name);
        }

        return value;
    }

    private static Path argument(String[] args, String name) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return Path.of(args[i + 1]);
            }
        }

        return null;
    }

    private static int plantCount(Board board) {
        int count = 0;
        for (Plant plant : board.mPlants) {
            if (!plant.mDead) {
                count++;
            }
        }
        return count;
    }

    private static int wavesContaining(Board board, int theZombieType) {
        int count = 0;
        for (int wave = 0; wave < board.mNumWaves; wave++) {
            for (int index = 0; index < Board.MAX_ZOMBIES_IN_WAVE; index++) {
                int zombieType = board.mZombiesInWave[wave][index];
                if (zombieType == ZombieType.ZOMBIE_INVALID) {
                    break;
                }
                if (zombieType == theZombieType) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static String reanimFileOf(int theReanimationType) {
        for (ReanimationParams params : Reanimator.gLawnReanimationArray) {
            if (params.mReanimationType == theReanimationType) {
                return params.mReanimFileName;
            }
        }
        return null;
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (var method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A headless adventure app whose reanimations look loaded, the same fixture the game's own
     * plant/zombie tests use: retail .reanim data is absent in this phase, so the stub track state is
     * what a loaded reanimation would provide.
     */
    private static final class LoadedReanimApp extends LawnApp {
        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation reanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            HeadlessTestApp.stubLoadedTracks(reanim);
            return reanim;
        }
    }
}
