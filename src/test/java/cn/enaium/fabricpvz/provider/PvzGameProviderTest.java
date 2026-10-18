package cn.enaium.fabricpvz.provider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Fabric Loader game provider SPI implementation: the registration the loader's
 * {@link ServiceLoader} reads, game discovery on the class path (directory and jar), the built-in
 * game mod, the transform requests, and the launch call that hands control to the game entry point.
 *
 * <p>Everything is asserted against the real Fabric Loader classes ({@code net.fabricmc.loader});
 * the loader itself runs end to end in the game's own loader integration test.
 */
class PvzGameProviderTest {

    @TempDir
    Path tmp;

    /**
     * {@code PvzGameProvider#launch} runs the mod entry points through the public
     * {@link net.fabricmc.loader.api.FabricLoader} facade and asks it for the environment, and that
     * facade needs a launcher; Knot would normally be it.
     */
    @BeforeAll
    static void installLauncher() {
        FabricLauncherBase.setLauncher(new RecordingLauncher().proxy());
    }

    @AfterEach
    void clearOverrides() {
        System.clearProperty(PvzGameProvider.ENTRYPOINT_PROPERTY);
        System.clearProperty(PvzGameProvider.GAME_VERSION_PROPERTY);
        System.clearProperty(PvzGameProvider.GAME_DIR_PROPERTY);
    }

    @Test
    void theServicesFileRegistersExactlyThisProvider() throws Exception {
        String registered;

        try (InputStream in = getClass().getResourceAsStream(
                "/META-INF/services/net.fabricmc.loader.impl.game.GameProvider")) {
            assertNotNull(in, "the provider jar must declare the GameProvider service");
            registered = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        assertEquals(PvzGameProvider.class.getName(), registered,
                "a single line keeps the loader on its fast provider path");
        assertFalse(registered.contains("\n"), "more than one line makes the loader reread the file");

        Class<?> type = Class.forName(registered);
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(GameProvider.class.isAssignableFrom(type), registered + " must implement GameProvider");
        assertNotNull(type.getDeclaredConstructor().newInstance(), "the loader needs a no-arg constructor");

        int found = 0;

        for (GameProvider provider : ServiceLoader.load(GameProvider.class)) {
            if (provider instanceof PvzGameProvider) {
                found++;
            }
        }

        assertEquals(1, found, "the ServiceLoader must find exactly one PvZ provider");
    }

    @Test
    void locatesTheGameDirectoryOnTheClassPath() throws Exception {
        Path gameClasses = gameClassesDirectory();
        PvzGameProvider provider = new PvzGameProvider();
        RecordingLauncher launcher = new RecordingLauncher(gameClasses);

        assertTrue(provider.locateGame(launcher.proxy(), new String[0]), "the game must be found on the class path");

        assertEquals(PvzGameProvider.GAME_ID, provider.getGameId());
        assertEquals(PvzGameProvider.GAME_NAME, provider.getGameName());
        assertEquals(PvzGameProvider.DEFAULT_GAME_VERSION, provider.getRawGameVersion());
        assertEquals(PvzGameProvider.DEFAULT_GAME_VERSION, provider.getNormalizedGameVersion());
        assertEquals(PvzGameProvider.DEFAULT_ENTRYPOINT, provider.getEntrypoint());

        assertEquals(1, provider.getBuiltinMods().size(), "the game is the provider's only built-in mod");
        GameProvider.BuiltinMod game = provider.getBuiltinMods().iterator().next();
        assertEquals(PvzGameProvider.GAME_ID, game.metadata.getId());
        assertEquals(PvzGameProvider.GAME_NAME, game.metadata.getName());
        assertEquals(PvzGameProvider.DEFAULT_GAME_VERSION, game.metadata.getVersion().getFriendlyString());
        assertEquals(List.of(gameClasses), game.paths,
                "the built-in game mod must point at the class path entry the game was found in");

        provider.unlockClassPath(launcher.proxy());
        assertEquals(List.of(gameClasses), launcher.added,
                "unlocking the class path makes the loader define (and mod) the game classes");
    }

    @Test
    void locatesTheGameJarOnTheClassPath() throws Exception {
        Path gameJar = tmp.resolve("pvz-portable.jar");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(gameJar))) {
            out.putNextEntry(new JarEntry(PvzGameProvider.GAME_PROBE_CLASS.replace('.', '/') + ".class"));
            out.write(new byte[] {1, 2, 3});
            out.closeEntry();
        }

        PvzGameProvider provider = new PvzGameProvider();

        assertTrue(provider.locateGame(new RecordingLauncher(gameJar).proxy(), new String[0]));
        assertEquals(List.of(gameJar), provider.getBuiltinMods().iterator().next().paths);
    }

    @Test
    void unlocksTheGameAndTheExtraClassPathEntries() throws Exception {
        Path gameClasses = gameClassesDirectory();
        Path extra = Files.createDirectories(tmp.resolve("extra"));
        System.setProperty(PvzGameProvider.EXTRA_CLASS_PATH_PROPERTY,
                extra + java.io.File.pathSeparator + tmp.resolve("missing"));
        PvzGameProvider provider = new PvzGameProvider();
        RecordingLauncher launcher = new RecordingLauncher(gameClasses);
        provider.locateGame(launcher.proxy(), new String[0]);

        try {
            provider.unlockClassPath(launcher.proxy());
            assertEquals(List.of(gameClasses, extra), launcher.added,
                    "the game comes first, then the extra entries; a missing entry is skipped");
        } finally {
            System.clearProperty(PvzGameProvider.EXTRA_CLASS_PATH_PROPERTY);
        }
    }

    @Test
    void reportsNoGameWhenTheClassPathHasNone() {
        PvzGameProvider provider = new PvzGameProvider();

        assertFalse(provider.locateGame(new RecordingLauncher(tmp.resolve("empty")).proxy(), new String[0]),
                "without the game on the class path the loader must keep looking for another provider");
    }

    @Test
    void entrypointAndGameVersionCanBeOverridden() {
        System.setProperty(PvzGameProvider.ENTRYPOINT_PROPERTY, "com.example.OtherMain");
        System.setProperty(PvzGameProvider.GAME_VERSION_PROPERTY, "2.5.0");
        PvzGameProvider provider = new PvzGameProvider();
        provider.locateGame(new RecordingLauncher(tmp.resolve("empty")).proxy(), new String[0]);

        assertEquals("com.example.OtherMain", provider.getEntrypoint());
        assertEquals("2.5.0", provider.getRawGameVersion());
    }

    @Test
    void theGameDirectoryComesFromThePropertyThenFromTheArgument() throws Exception {
        PvzGameProvider provider = new PvzGameProvider();
        provider.locateGame(new RecordingLauncher(tmp.resolve("empty")).proxy(),
                new String[] {"-resdir", "/assets", "-gamedir", tmp.resolve("fromArgument").toString()});

        assertEquals(tmp.resolve("fromArgument").toAbsolutePath().normalize(), provider.getLaunchDirectory());

        System.setProperty(PvzGameProvider.GAME_DIR_PROPERTY, tmp.resolve("fromProperty").toString());

        assertEquals(tmp.resolve("fromProperty").toAbsolutePath().normalize(), provider.getLaunchDirectory(),
                "the system property wins over the command line argument");
    }

    @Test
    void gameClassesAndModClassesGetDifferentTransforms() {
        PvzGameProvider provider = new PvzGameProvider();
        Set<GameProvider.BuiltinTransform> game =
                provider.getBuiltinTransforms("cn.enaium.fabricpvz.portable.lawn.Board");

        assertTrue(game.contains(GameProvider.BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS));
        assertTrue(game.contains(GameProvider.BuiltinTransform.CLASS_TWEAKS));
        assertEquals(Set.of(GameProvider.BuiltinTransform.CLASS_TWEAKS),
                provider.getBuiltinTransforms("cn.enaium.fabricpvz.examplemod.ExampleMod"),
                "mod classes may only be modified through access wideners");
        assertTrue(provider.isEnabled());
        assertFalse(provider.requiresUrlClassLoader());
    }

    @Test
    void gameArgumentsArePassedThroughUnchanged() {
        PvzGameProvider provider = new PvzGameProvider();
        String[] args = {"-resdir", "/assets", "-savedir", "/save"};
        provider.locateGame(new RecordingLauncher(tmp.resolve("empty")).proxy(), args);

        Assertions.assertArrayEquals(args, provider.getLaunchArguments(false));
        Assertions.assertArrayEquals(args, provider.getLaunchArguments(true));
        assertEquals(List.of(args), List.copyOf(provider.getArguments().getExtraArgs()));
    }

    @Test
    void launchCallsTheMainClassOfTheGame() throws Exception {
        System.setProperty(PvzGameProvider.ENTRYPOINT_PROPERTY, EntrypointFixture.class.getName());
        PvzGameProvider provider = new PvzGameProvider();
        String[] args = {"-savedir", "/save"};
        provider.locateGame(new RecordingLauncher(tmp.resolve("empty")).proxy(), args);
        EntrypointFixture.reset();

        provider.launch(getClass().getClassLoader());

        assertEquals(1, EntrypointFixture.calls(), "launch must call main(String[]) exactly once");
        Assertions.assertArrayEquals(args, EntrypointFixture.arguments());
    }

    @Test
    void launchFailsWithAnActionableMessageWhenTheEntrypointIsMissing() {
        System.setProperty(PvzGameProvider.ENTRYPOINT_PROPERTY, "does.not.Exist");
        PvzGameProvider provider = new PvzGameProvider();
        provider.locateGame(new RecordingLauncher(tmp.resolve("empty")).proxy(), new String[0]);

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                () -> provider.launch(getClass().getClassLoader()));

        assertTrue(failure.getMessage().contains(PvzGameProvider.ENTRYPOINT_PROPERTY),
                "the error must name the property that overrides the entry point: " + failure.getMessage());
    }

    /** Creates a class path entry containing the class the provider probes for. */
    private Path gameClassesDirectory() throws IOException {
        Path root = tmp.resolve("classes");
        Path main = root.resolve(PvzGameProvider.GAME_PROBE_CLASS.replace('.', '/') + ".class");
        Files.createDirectories(main.getParent());
        Files.write(main, new byte[] {1, 2, 3});

        return root;
    }

    /** Stand-in launcher: the provider only ever asks it for the class path and unlocks it. */
    private static final class RecordingLauncher implements InvocationHandler {

        private final List<Path> classPath;
        private final FabricLauncher proxy;
        final List<Path> added = new ArrayList<>();

        RecordingLauncher(Path... classPath) {
            this.classPath = List.of(classPath);
            this.proxy = (FabricLauncher) Proxy.newProxyInstance(
                    FabricLauncher.class.getClassLoader(), new Class<?>[] {FabricLauncher.class}, this);
        }

        FabricLauncher proxy() {
            return proxy;
        }

        @Override
        public Object invoke(Object unused, Method method, Object[] args) {
            switch (method.getName()) {
            case "getClassPath":
                return classPath;
            case "addToClassPath":
                added.add((Path) args[0]);
                return null;
            case "getEnvironmentType":
                return EnvType.CLIENT;
            case "isDevelopment":
                return false;
            case "toString":
                return "RecordingLauncher" + classPath;
            default:
                throw new UnsupportedOperationException("RecordingLauncher." + method.getName());
            }
        }
    }
}
