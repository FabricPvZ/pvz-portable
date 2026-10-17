package cn.enaium.fabricpvz.portable.contentmodfixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.HelmType;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * End-to-end verification of the mod ecosystem on the <em>real</em> Fabric Loader.
 *
 * <p>The test starts the loader the way a launcher does — a second JVM whose main class is
 * {@value #LAUNCHER_CLASS} — with the game, the loader (plus Mixin and ASM) and the game provider on
 * the class path and a real {@code mods/} directory holding three mod jars: the API's aggregate jar
 * (which carries its five feature modules nested, copied in by {@code :testModsJar}), the example mod
 * built by {@code :pvz-example-mod} (events + Mixin) and a throwaway mod jar assembled here from
 * {@link ContentMod}, which registers a plant and a zombie through the public content API.
 * <strong>Nothing of the API is on the class path</strong>, in either mode the test runs: like every
 * other mod, the loader has to find it in the mods directory.
 * The loader finds the game through {@link #PROVIDER_CLASS} (the {@code GameProvider} implementation
 * of {@code cn.enaium.fabricpvz:fabric-provider}), resolves the mods, runs their entry points,
 * applies their Mixin configurations and only then calls the game entry point, which the test
 * redirects to {@link ContentModGameMain} with {@code -Dpvz.game.mainClass}.
 *
 * <p>Everything the loader and the game observe is reported to a file by that fixture (the loader
 * defines its own copies of the game, API and mod classes, so the test cannot look at them directly)
 * and asserted below: the mod list, the API initialisation, the fixture's registrations as the game's
 * tables see them, the example mod's Mixin on a game class and its event listener, and — when this
 * machine has the retail assets — the real game booting Adventure level 1 up to
 * {@code GameScenes.SCENE_PLAYING} with the mods' Mixin running inside the board's frame loop.
 */
class FabricLoaderEndToEndTest {

    /** Launcher main class of the real Fabric Loader (client environment), from its own manifest. */
    private static final String LAUNCHER_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient";
    /** The game provider this workspace ships for the port. */
    private static final String PROVIDER_CLASS = "cn.enaium.fabricpvz.provider.PvzGameProvider";
    /** System property the game provider uses to replace the game entry point. */
    private static final String ENTRYPOINT_PROPERTY = "pvz.game.mainClass";
    /** Mod dependencies of the fixture mod; the negative test replaces this block. */
    private static final String MOD_DEPENDENCIES = """
            "fabricloader": ">=0.19.0",
            "fabric-api": ">=1.1.0",
            "api-plant": ">=1.1.0",
            "api-resource": ">=1.1.0",
            "api-zombie": ">=1.1.0",
            "pvz-portable": "*"
            """;

    /** Every mod the loader must have resolved, sorted: its own, the game's, and the mod jars. */
    private static final List<String> EXPECTED_MODS = List.of(
            // the loader's own mods: the loader, the JVM it runs on and its bundled MixinExtras
            "fabricloader", "java", "mixinextras",
            // the game, as the built-in mod the game provider declares
            "pvz-portable",
            // the API, from the mods directory like every other mod: the aggregator and the five
            // feature modules nested inside it (our own event bus lives in api-base, so the upstream
            // Fabric API base module is no longer part of it)
            "fabric-api", "api-base", "api-events", "api-plant",
            "api-resource", "api-zombie",
            // the two mod jars the test itself installs: the example mod and the fixture mod
            "pvz-example-mod", "pvz-content-fixture-mod");

    /** {@link #EXPECTED_MODS} in the order the fixture reports it (its report is sorted). */
    private static List<String> sorted(List<String> ids) {
        List<String> copy = new ArrayList<>(ids);
        copy.sort(java.util.Comparator.naturalOrder());
        return List.copyOf(copy);
    }

    /**
     * The mods the resource-override run resolves: the same installation, with the override fixture
     * mod in place of the content one.
     */
    private static final List<String> OVERRIDE_MODS = List.of(
            "fabricloader", "java", "mixinextras",
            "pvz-portable",
            "fabric-api", "api-base", "api-events", "api-plant",
            "api-resource", "api-zombie",
            "pvz-example-mod", ResourceOverrideMod.MOD_ID);

    @TempDir
    Path tmp;

    /** The loose file {@link ResourceOverrideMod}'s {@code addFile} registration serves. */
    private Path overrideFile;

    /** The loose directory {@link ResourceOverrideMod}'s {@code addRoot} registration serves. */
    private Path overrideRoot;

    /** Whether the launcher must skip booting the real game (see the harness's phase list). */
    private boolean skipGameBoot;

    /**
     * Writes the loose files the override fixture mod's {@code addFile} / {@code addRoot}
     * registrations serve, from the very byte generators the mod itself offers, and remembers their
     * paths: {@link #launch} passes them to the launcher as system properties, because the mod runs
     * in a second JVM that cannot see this test's objects.
     */
    private void createOverrideFixtures() throws IOException {
        Path overrides = Files.createDirectories(tmp.resolve("overrides"));
        overrideFile = overrides.resolve("file-override.bin");
        Files.write(overrideFile, ResourceOverrideMod.fileOverrideBytes());

        overrideRoot = Files.createDirectories(overrides.resolve("root"));
        Path image = overrideRoot.resolve(ResourceOverrideMod.ROOT_RESOURCE);
        Files.createDirectories(image.getParent());
        Files.write(image, ResourceOverrideMod.rootImageBytes());
    }

    @Test
    void bootsTheGameThroughTheRealLoaderWithModsAndReachesScenePlaying() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        copyInstalledMods(mods);
        buildFixtureModJar(mods, MOD_DEPENDENCIES);

        Path runDir = Files.createDirectories(tmp.resolve("run"));
        Path report = tmp.resolve("loader-report.txt");
        Path output = tmp.resolve("loader-output.txt");

        int exitCode = launch(mods, runDir, report, output);

        assertEquals(0, exitCode, "Fabric Loader must boot the game: " + failureMessage(output, report));
        Map<String, String> values = readReport(report);
        System.out.println("[loader-e2e] game-side report:\n" + Files.readString(report));

        // 1) The loader itself: it resolved the game (built-in mod), the loader mod, the API modules
        // (mods on the class path) and the two mod jars from the mods directory.
        assertEquals("0.19.5", values.get("loaderVersion"), "the real Fabric Loader must be the one running");
        assertEquals("1.1.0", values.get("gameVersion"), "the provider must report the game's version");
        assertEquals("client", values.get("environment"));
        assertTrue(Boolean.parseBoolean(values.get("development")), "the test runs the loader in development mode");
        assertNotNull(values.get("modsLoaded"), "the fixture must report the loaded mods");
        assertEquals(sorted(EXPECTED_MODS), List.of(values.get("modsLoaded").split(",")),
                "the loader must resolve exactly these mods");
        assertEquals(EXPECTED_MODS.size(), Integer.parseInt(values.get("modCount")));

        // 2) The API ran before the game entry point and sees the mods.
        assertEquals("1.2.0", values.get("apiVersion"), "the API entry point must run before the game");
        assertEquals("true", values.get("apiInitialized"));
        assertEquals("true", values.get("plantModuleLoaded"));
        assertEquals("true", values.get("zombieModuleLoaded"));
        assertEquals("true", values.get("exampleModLoaded"));
        assertEquals("true", values.get("fixtureModLoaded"));

        // 3) The fixture mod's registrations went through the public API and the game sees them.
        assertEquals("true", values.get("plantRegistered"));
        assertEquals("true", values.get("zombieRegistered"));
        assertEquals("true", values.get("seedOutsideUpstream"));
        assertEquals("true", values.get("zombieOutsideUpstream"));
        assertEquals("125", values.get("definitionCost"));
        assertEquals(String.valueOf(ReanimationType.REANIM_REPEATER), values.get("definitionReanim"));
        assertEquals("2", values.get("zombieDefinitionValue"));
        assertEquals("Fixture Pea Shooter+", values.get("plantName"));
        assertEquals("A test plant that fires two peas.", values.get("plantTooltip"));
        assertEquals("true", values.get("seedAvailable"), "a registered plant is available to the player");

        // 4) The planting rule the mod registered is consulted, and only for its own seed type.
        assertEquals("true", values.get("plantingRuleAllowedRow"));
        assertEquals("true", values.get("plantingRuleVetoedOtherRow"));
        assertEquals("2", values.get("plantingRuleCalls"));
        assertEquals("true", values.get("upstreamPlantingUnaffected"));

        // 5) The plant was placed, is the registered type and runs its behaviour callbacks.
        assertEquals("true", values.get("plantPlaced"));
        assertEquals("true", values.get("plantSeedTypeMatches"));
        assertEquals("400", values.get("plantHealth"), "the registered health replaces the default");
        assertEquals("400", values.get("plantMaxHealth"));
        assertEquals(String.valueOf(PlantSubClass.SUBCLASS_SHOOTER), values.get("plantSubclass"));
        assertEquals("true", values.get("plantBodyReanimLoaded"));
        assertEquals("1", values.get("plantPlacedCallbacks"));
        assertEquals("1", values.get("boardPlantCount"));
        assertEquals("1", values.get("plantTickCallbacks"), "Plant::update must reach the mod callback");

        // 6) The zombie spawned through Board::addZombieInRow with the registered numbers.
        assertEquals("true", values.get("zombieSpawned"));
        assertEquals("true", values.get("zombieTypeMatches"));
        assertEquals("400", values.get("zombieBodyHealth"));
        assertEquals("400", values.get("zombieBodyMaxHealth"));
        assertEquals(String.valueOf(HelmType.HELMTYPE_PAIL), values.get("zombieHelmType"));
        assertEquals("1100", values.get("zombieHelmHealth"));
        assertEquals("true", values.get("zombieBodyReanimLoaded"));
        assertEquals("1", values.get("zombieSpawnedCallbacks"));
        assertEquals("1", values.get("zombieTickCallbacks"), "Zombie::update must reach the mod callback");

        // 7) Wave participation: the registered zombie is allowed on the level and appears in a wave.
        assertEquals("true", values.get("zombieAllowedOnLevel"));
        assertTrue(Integer.parseInt(values.get("wavesContainingModZombie")) >= 1,
                "a wave-enabled mod zombie must be placed in the level's waves, got "
                        + values.get("wavesContainingModZombie"));
        assertEquals("true", values.get("upstreamZombieStillAllowed"));

        // 8) A mixin from a mod jar was applied to game classes, and the API's hook chain reaches the
        // mod's listener (sun 5 doubled by the example mod).
        assertEquals("true", values.get("mixinApplied"), "the example mod's LawnCommonMixin must have run");
        assertEquals("false", values.get("modInRangeResult"), "7 is not a multiple of 3");
        assertEquals("true", values.get("boardImplementsAccessor"), "the @Accessor mixin must add its interface");
        assertEquals("true", values.get("boardHasUpdateGame"));
        assertEquals("10", values.get("sunHookResult"), "the mod's listener must double collected sun");
        assertEquals("1", values.get("sunEvents"), "the hook chain must reach the mod listener exactly once");

        // 9) The real game: entry point invoked by the loader, Adventure level 1 running.
        if (!Boolean.parseBoolean(values.get("assetsPresent"))) {
            System.out.println("[loader-e2e] no retail assets on this machine: the SCENE_PLAYING phase "
                    + "was skipped, mod loading was still verified end to end");
            return;
        }

        assertEquals("true", values.get("assetsLoaded"), "the resource loading thread must complete");
        assertEquals("true", values.get("gameBooted"), "the game must boot through the loader");
        assertTrue(Boolean.parseBoolean(values.get("scenePlaying")),
                "the game must reach SCENE_PLAYING, got scene=" + values.get("scene"));
        assertEquals("1", values.get("realBoardLevel"), "a fresh profile starts at level 1");
        assertTrue(Integer.parseInt(values.get("realBoardWaves")) > 0, "level 1 must define zombie waves");
        assertEquals("true", values.get("realSeedAvailable"),
                "the mod plant must be available in the real level");
        assertEquals("true", values.get("realZombieAllowed"), "the mod zombie must be allowed on the level");
        assertTrue(Integer.parseInt(values.get("realWavesContainingModZombie")) >= 1,
                "the mod zombie must be part of the real level's waves");
        assertTrue(Integer.parseInt(values.get("boardTicksSeenByModMixin")) > 0,
                "the example mod's BoardMixin must run inside the real board's tick loop");
        assertEquals(values.get("realBoardSun"), values.get("modMixinSawSun"),
                "the mixin must observe the board's own sun counter");
        assertEquals("true", values.get("scenePlayingAfterTicks"),
                "the level must still be in SCENE_PLAYING after 300 frames");
    }

    @Test
    void refusesToStartWhenAModDependencyCannotBeResolved() throws Exception {
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        buildFixtureModJar(mods, """
                "fabricloader": ">=0.19.0",
                "pvz-portable": "*",
                "pvz-mod-that-does-not-exist": ">=9.0.0"
                """);
        Path runDir = Files.createDirectories(tmp.resolve("run"));
        Path report = tmp.resolve("loader-report.txt");
        Path output = tmp.resolve("loader-output.txt");

        int exitCode = launch(mods, runDir, report, output);
        String log = Files.readString(output);

        assertFalse(exitCode == 0, "an unresolvable mod dependency must abort startup");
        assertTrue(log.contains("pvz-mod-that-does-not-exist"),
                "the loader must name the missing dependency, output was:\n" + log);
        assertFalse(Files.exists(report), "the game entry point must never run");
    }

    /**
     * Starts the launcher in a second JVM with this test's class path, exactly like an installation
     * would.
     *
     * @return the launcher's exit code
     */
    private int launch(Path mods, Path runDir, Path report, Path output) throws Exception {
        return launch(mods, runDir, report, output, true);
    }

    @Test
    void bootsInProductionModeWithEveryModInTheModsDirectory() throws Exception {
        // The installation shape: the loader runs without the development flag, so the only mods are
        // the jars in mods/ — the API has to be there like any other mod, the way the game, the
        // loader and the provider sit on the class path.
        Path mods = Files.createDirectories(tmp.resolve("mods"));
        copyInstalledMods(mods);
        buildFixtureModJar(mods, MOD_DEPENDENCIES);

        Path runDir = Files.createDirectories(tmp.resolve("run"));
        Path report = tmp.resolve("loader-report.txt");
        Path output = tmp.resolve("loader-output.txt");

        int exitCode = launch(mods, runDir, report, output, false);

        assertEquals(0, exitCode, "Fabric Loader must boot the game: " + failureMessage(output, report));
        Map<String, String> values = readReport(report);
        System.out.println("[loader-e2e] game-side report:\n" + Files.readString(report));

        assertEquals(sorted(EXPECTED_MODS), List.of(values.get("modsLoaded").split(",")),
                "the same mods must be resolved, now from the mods directory");
        assertFalse(Boolean.parseBoolean(values.get("development")), "this run must not be a development run");
        assertEquals("true", values.get("apiInitialized"));
        assertEquals("true", values.get("plantRegistered"));
        assertEquals("true", values.get("mixinApplied"), "the example mod's Mixin must still apply");
        assertEquals("true", values.get("scenePlaying"), "the game must reach SCENE_PLAYING");
    }

    /**
     * The proof that a mod's {@code api-resource} registrations are what the engine reads: a fixture
     * mod registers one override of each kind ({@code addBytes} / {@code addFile} / {@code addRoot}),
     * and this test compares what the engine's own {@link PakInterface} returns - inside the launcher
     * JVM - with the bytes of that registration and with the retail {@code main.pak} member of the
     * same name, which it reads itself here, in a JVM where no Mixin exists.
     *
     * <p>Every name involved exists in the retail pak, so each override shadows a member the game
     * would otherwise serve; {@code reanim/PeaShooter.reanim} is registered by nothing, which is what
     * makes "the override answered" distinguishable from "the reader hands out the file system or the
     * pak for everything".
     */
    @Test
    void theEnginesOwnReaderServesTheFixtureModsResourceOverrides() throws Exception {
        Optional<Path> assets = TestAssets.resourceRoot();
        assumeTrue(assets.isPresent(), "no retail installation on this machine: the overrides cannot be "
                + "compared with the pak members they shadow");
        Path retailRoot = assets.get();
        assumeTrue(Files.isRegularFile(retailRoot.resolve("main.pak")),
                "the retail main.pak is missing: " + retailRoot);

        Path mods = Files.createDirectories(tmp.resolve("mods"));
        copyInstalledMods(mods);
        buildFixtureModJar(mods, ResourceOverrideMod.class, ResourceOverrideMod.MOD_ID,
                "PvZ Resource Override Fixture Mod",
                "Registers resource overrides through the resource API.", MOD_DEPENDENCIES);
        createOverrideFixtures();
        // This run needs the engine's reader with the pak mounted, not a game: the fixture's
        // registrations are on the game's own reading path either way (they happen before the game
        // entry point runs), so booting the game here would only test its tolerance of the fixture's
        // stub content.
        skipGameBoot = true;

        Path runDir = Files.createDirectories(tmp.resolve("run"));
        Path report = tmp.resolve("loader-report.txt");
        Path output = tmp.resolve("loader-output.txt");

        int exitCode = launch(mods, runDir, report, output);
        assertEquals(0, exitCode, "the launcher must finish cleanly: " + failureMessage(output, report));
        Map<String, String> values = readReport(report);
        System.out.println("[loader-e2e] game-side report:\n" + Files.readString(report));

        // 10) The module is live in this run: the fixture mod's entry point ran, api-resource came
        // from the mods directory like every other mod and its PakInterface Mixin was applied.
        assertEquals(sorted(OVERRIDE_MODS), List.of(values.get("modsLoaded").split(",")),
                "the loader must resolve exactly these mods");
        assertEquals("true", values.get("overrideModLoaded"));
        assertEquals("true", values.get("resourceModuleLoaded"));
        assertEquals("true", values.get("resourceOverridesInstalled"),
                "api-resource's Mixin must be applied to the engine: without it no override can be served");
        assertEquals("true", values.get("retailPakMounted"),
                "the engine must have the retail main.pak mounted for the control read");
        assertEquals("true", values.get("gameBootSkipped"), "this run must not boot the game");

        // The registrations the module reports: addBytes and addFile appear by name (a root is
        // resolved on demand, so images/Almanac.png legitimately cannot appear in this list).
        assertEquals(List.of(upper(ResourceOverrideMod.FILE_RESOURCE),
                        upper(ResourceOverrideMod.BYTES_RESOURCE)),
                List.of(values.get("overriddenNames").split(",")),
                "overriddenNames() must list exactly the addBytes and addFile registrations");
        assertEquals(overrideFile.toAbsolutePath().normalize().toString(),
                Path.of(values.get("fileOverrideRegistered")).toAbsolutePath().normalize().toString(),
                "addFile must serve the file this test wrote");
        assertEquals(overrideRoot.toAbsolutePath().normalize().toString(),
                Path.of(values.get("rootOverrideRegistered")).toAbsolutePath().normalize().toString(),
                "addRoot must serve the directory this test wrote");

        // 11) addBytes: the engine's own read returns the in-memory fixture bytes, they are readable
        // as the fixture's text, and the handle was not a pak member - the override answered.
        byte[] lawnStrings = ResourceOverrideMod.lawnStringsBytes();
        assertEquals("true", values.get("bytesOverrideServed"),
                "readResource must return the addBytes content byte for byte");
        assertEquals(sha256(lawnStrings), values.get("bytesOverrideReadSha256"));
        assertEquals(String.valueOf(lawnStrings.length), values.get("bytesOverrideReadLength"));
        assertEquals("true", values.get("bytesOverrideTextVisible"),
                "the content must be the fixture's own text, not a coincidental match");
        assertEquals("true", values.get("bytesOverrideExists"));
        assertEquals("false", values.get("bytesOverrideFromPak"),
                "an override must not be served as a pak member");
        assertNotEquals(sha256(pakMember(retailRoot, ResourceOverrideMod.BYTES_RESOURCE)),
                values.get("bytesOverrideReadSha256"),
                "the retail pak's member of the same name must differ from what the engine read");

        // 12) addFile: the same shape for the single-file registration.
        byte[] fileBytes = ResourceOverrideMod.fileOverrideBytes();
        assertEquals(sha256(fileBytes), values.get("fileOverrideExpectedSha256"),
                "the harness must hash the very file this test wrote");
        assertEquals(String.valueOf(fileBytes.length), values.get("fileOverrideExpectedLength"));
        assertEquals("true", values.get("fileOverrideServed"));
        assertEquals(sha256(fileBytes), values.get("fileOverrideReadSha256"));
        assertEquals(String.valueOf(fileBytes.length), values.get("fileOverrideReadLength"));
        assertEquals("true", values.get("fileOverrideExists"));
        assertEquals("false", values.get("fileOverrideFromPak"));
        assertNotEquals(sha256(pakMember(retailRoot, ResourceOverrideMod.FILE_RESOURCE)),
                values.get("fileOverrideReadSha256"),
                "the retail pak's member of the same name must differ from what the engine read");

        // 13) addRoot: images/Almanac.png is answered by the registered directory, not by its name.
        byte[] imageBytes = ResourceOverrideMod.rootImageBytes();
        assertEquals(sha256(imageBytes), values.get("rootOverrideExpectedSha256"));
        assertEquals("true", values.get("rootOverrideServed"));
        assertEquals(sha256(imageBytes), values.get("rootOverrideReadSha256"));
        assertEquals(String.valueOf(imageBytes.length), values.get("rootOverrideReadLength"));
        assertEquals("true", values.get("rootOverrideExists"));
        assertEquals("false", values.get("rootOverrideFromPak"));
        assertNotEquals(sha256(pakMember(retailRoot, ResourceOverrideMod.ROOT_RESOURCE)),
                values.get("rootOverrideReadSha256"),
                "the retail pak's member of the same name must differ from what the engine read");

        // 14) The control: a resource nobody registered still comes out of the mounted retail pak,
        // byte for byte, and differs from all three overridden answers.
        byte[] control = pakMember(retailRoot, ResourceOverrideMod.CONTROL_RESOURCE);
        assertEquals(sha256(control), values.get("controlReadSha256"),
                "an unoverridden resource must be served by the mounted retail pak");
        assertEquals(String.valueOf(control.length), values.get("controlReadLength"));
        assertEquals("true", values.get("controlFromPak"));
        assertEquals("true", values.get("controlExists"));
        assertEquals("true", values.get("controlDiffersFromOverrides"));
        assertNotEquals(sha256(control), values.get("bytesOverrideReadSha256"));
        assertNotEquals(sha256(control), values.get("fileOverrideReadSha256"));
        assertNotEquals(sha256(control), values.get("rootOverrideReadSha256"));
    }

    /**
     * A retail pak member's bytes, read with the engine's own reader in <em>this</em> JVM, where no
     * Mixin is registered: exactly what the game would serve if the mod registered nothing.
     */
    private static byte[] pakMember(Path retailRoot, String member) throws Exception {
        PakInterface engine = new PakInterface();
        engine.setResourceFolder(retailRoot.toString());
        Path pak = retailRoot.resolve("main.pak");
        assertTrue(engine.addPakFile(pak), "the retail main.pak must mount: " + pak);
        byte[] bytes = engine.readResource(member);
        assertNotNull(bytes, "the retail pak must contain " + member);
        return bytes;
    }

    /** The pak-path spelling the engine's own normalization produces (upper-case, '/' separators). */
    private static String upper(String pakPath) {
        return pakPath.toUpperCase(Locale.ROOT);
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    /**
     * Copies the mod jars the Gradle test task installs into {@code build/testmods} - the API's
     * aggregate jar and the example mod - into this test's own mods directory.
     */
    private static void copyInstalledMods(Path modsDir) throws IOException {
        String modsDirProperty = System.getProperty("pvz.test.modsDir");
        assertNotNull(modsDirProperty, "pvz.test.modsDir must be set (run through Gradle)");
        Path source = Path.of(modsDirProperty);
        assertTrue(Files.isDirectory(source), "the test mods build output is missing: " + source);

        List<Path> jars;
        try (Stream<Path> entries = Files.list(source)) {
            jars = entries.filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
        }

        // The API (one aggregate jar, its modules nested inside it) and the example mod: the class
        // path carries neither, so a missing jar here is a broken build, not a test to skip.
        assertEquals(2, jars.size(), "expected the API jar and the example mod in " + source + ": " + jars);

        for (Path jar : jars) {
            Files.copy(jar, modsDir.resolve(jar.getFileName()));
        }
    }

    /**
     * Starts the launcher in a second JVM with this test's class path, exactly like an installation
     * would.
     *
     * @param developmentMode whether the loader may also discover mods on the class path (a Loom
     *     development run) or only the ones in {@code mods/} (an installation)
     * @return the launcher's exit code
     */
    private int launch(Path mods, Path runDir, Path report, Path output, boolean developmentMode) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(java);
        command.add("--enable-native-access=ALL-UNNAMED");

        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // SDL (LWJGL's bindings) needs the first thread; the Gradle test task does the same.
            command.add("-XstartOnFirstThread");
        }

        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        // The real loader: development mode discovers mods that are on the class path too, exactly
        // like a Loom development run. No mod of this test is on the class path (the API is in the
        // mods directory like every other mod), so the resolved mod list is the same in both modes;
        // what differs is the loader's own environment.
        if (developmentMode) {
            command.add("-Dfabric.development=true");
        }
        // This port is the only provider on the class path.
        command.add("-Dfabric.skipMcProvider=true");
        // A crash must exit instead of opening the loader's Swing error window in a test run.
        command.add("-Dfabric.noGui=true");
        command.add("-Dpvz.gameDir=" + runDir);
        command.add("-Dfabric.modsFolder=" + mods);
        command.add("-D" + ENTRYPOINT_PROPERTY + "=" + ContentModGameMain.class.getName());
        command.add("-Dpvz.test.report=" + report);
        // The override fixture mod's registrations: they live in this test's temporary directory, so
        // the launcher JVM learns their paths - and whether it should boot the game at all - here.
        if (overrideFile != null) {
            command.add("-D" + ResourceOverrideMod.FILE_OVERRIDE_PROPERTY + "=" + overrideFile);
        }
        if (overrideRoot != null) {
            command.add("-D" + ResourceOverrideMod.ROOT_OVERRIDE_PROPERTY + "=" + overrideRoot);
        }
        if (skipGameBoot) {
            command.add("-Dpvz.test.skipGameBoot=true");
        }
        // Make the game class loader define this module's fixture classes: they must live in the same
        // class loader as the game, the API and the mods, otherwise the loader's copies and the test
        // module's copies of the same class would hold different static state.
        command.add("-Dpvz.extraClasspath=" + fixtureClasses());
        TestAssets.resourceRoot().ifPresent(assets -> command.add("-Dpvz.test.resources=" + assets));
        command.add(LAUNCHER_CLASS);
        command.add("-savedir");
        command.add(runDir.resolve("userdata").toString());

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(tmp.toFile())
                .redirectOutput(output.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();

        if (!process.waitFor(10, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError("Fabric Loader did not finish; launcher output:\n" + read(output));
        }

        System.out.println("[loader-e2e] " + LAUNCHER_CLASS + " exited with " + process.exitValue());
        System.out.println("[loader-e2e] launcher output:\n" + read(output));

        return process.exitValue();
    }

    /** Packages the compiled {@link ContentMod} plus a generated {@code fabric.mod.json} into a jar. */
    private static void buildFixtureModJar(Path modsDir, String dependencies) throws Exception {
        buildFixtureModJar(modsDir, ContentMod.class, ContentMod.MOD_ID, "PvZ Content Fixture Mod",
                "Registers one plant and one zombie through the content API.", dependencies);
    }

    /**
     * Packages a fixture mod: the compiled class (and its nested ones), plus a generated
     * {@code fabric.mod.json}, into the mods directory.
     *
     * @param modClass the fixture's {@code ModInitializer}, compiled into the test sources
     * @param modId the id the generated metadata declares
     * @param name the display name the generated metadata declares
     * @param description the metadata description
     * @param dependencies the {@code depends} block of the generated metadata
     */
    private static void buildFixtureModJar(Path modsDir, Class<?> modClass, String modId, String name,
            String description, String dependencies) throws Exception {
        Path classes = Path.of(modClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path modRoot = classes.resolve(modClass.getPackageName().replace('.', '/'));
        assertTrue(Files.isDirectory(modRoot), "fixture classes must be compiled: " + modRoot);

        String simpleName = modClass.getSimpleName();
        Path jar = modsDir.resolve(modId + ".jar");
        String metadata = """
                {
                  "schemaVersion": 1,
                  "id": "%s",
                  "version": "1.1.0",
                  "name": "%s",
                  "description": "%s",
                  "authors": ["PvZ Fabric"],
                  "license": "LGPL-3.0-or-later",
                  "environment": "*",
                  "entrypoints": {
                    "main": ["%s"]
                  },
                  "depends": {
                %s
                  }
                }
                """.formatted(modId, name, description, modClass.getName(), dependencies);

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("fabric.mod.json"));
            out.write(metadata.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();

            // Only the mod's own class: the game entry point of the fixture stays on the class path.
            try (Stream<Path> files = Files.list(modRoot)) {
                for (Path file : files.filter(path -> isModClass(path, simpleName)).toList()) {
                    out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    out.write(Files.readAllBytes(file));
                    out.closeEntry();
                }
            }
        }

        assertTrue(Files.size(jar) > 0, "the mod jar must not be empty");
    }

    /** The compiled test classes of this module: the game-side fixtures the launcher must expose. */
    private static String fixtureClasses() throws Exception {
        return Path.of(ContentMod.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    private static boolean isModClass(Path file, String simpleName) {
        String name = file.getFileName().toString();
        return name.equals(simpleName + ".class") || name.startsWith(simpleName + "$");
    }

    private static Map<String, String> readReport(Path report) throws IOException {
        return parse(Files.readString(report));
    }

    private static Map<String, String> parse(String content) {
        Map<String, String> values = new HashMap<>();

        for (String line : content.split("\n")) {
            int equals = line.indexOf('=');
            if (equals > 0) {
                values.put(line.substring(0, equals), line.substring(equals + 1).trim());
            }
        }

        return values;
    }

    private static String read(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "<no output>";
    }

    private static String failureMessage(Path output, Path report) throws IOException {
        String tail = read(output);
        int keep = 4000;

        if (tail.length() > keep) {
            tail = "…" + tail.substring(tail.length() - keep);
        }

        return "\n--- launcher output (tail) ---\n" + tail
                + "\n--- report ---\n" + (Files.exists(report) ? Files.readString(report) : "<none>");
    }
}
