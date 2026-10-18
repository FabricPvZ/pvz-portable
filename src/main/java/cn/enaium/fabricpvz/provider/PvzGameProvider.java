package cn.enaium.fabricpvz.provider;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.metadata.BuiltinModMetadata;
import net.fabricmc.loader.impl.util.Arguments;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.LogHandler;

/**
 * Fabric Loader game provider for the PvZ port.
 *
 * <p>The real Fabric Loader knows nothing about any particular game: it discovers the game at
 * runtime through this SPI, which is registered in {@code META-INF/services/
 * net.fabricmc.loader.impl.game.GameProvider} and picked up by
 * {@code net.fabricmc.loader.impl.launch.knot.Knot#createGameProvider} through a
 * {@link java.util.ServiceLoader} (with a fast path that reads the services file of the loader jar
 * itself, which only ever lists Minecraft's provider).
 *
 * <p>This is the whole of the PvZ-specific launch glue; the mod loader, the metadata parser, the
 * dependency resolver, the Mod class loader and Mixin all come from
 * {@code net.fabricmc:fabric-loader}. The port is started with
 * {@code java -cp <game+loader+provider+mods> net.fabricmc.loader.impl.launch.knot.KnotClient} and
 * the {@code mods} directory of the game directory (or {@code -Dfabric.modsFolder}).
 *
 * <p>Contract and choices worth knowing:
 * <ul>
 *   <li>the game is identified on the class path by {@link #GAME_PROBE_CLASS}, so it works whether
 *       the game is a directory of classes ({@code classes/java/main}) or a jar;</li>
 *   <li>{@link #unlockClassPath} adds that class path entry to the Knot class loader, which is what
 *       makes the loader define the game's classes (and therefore apply mod Mixin transformations
 *       to them); everything else — LWJGL, the JDK — keeps coming from the parent class loader
 *       because this provider deliberately does not restrict the parent class path;</li>
 *   <li>the game itself (and its shipped mod metadata) is a built-in mod, exactly like Minecraft
 *       is, so no {@code fabric.mod.json} has to live in the game jar;</li>
 *   <li>{@link #ENTRYPOINT_PROPERTY} redirects the entry point; the port's own tests use it to boot
 *       a fixture main class inside the loader instead of the game's real entry point.</li>
 * </ul>
 *
 * <p>Logging: the loader hands its log ({@code net.fabricmc.loader.impl.util.log.Log}) to whatever
 * log handler the game provider installs, and this provider installs the loader's SLF4J bridge in its
 * constructor (see {@link #PvzGameProvider()}). From then on the loader's own lines, the Mixin
 * subsystem's lines (Knot's Mixin service logs through {@code Log} as well) and the mods' lines
 * (SLF4J directly) are one stream, formatted by the Log4j configuration this module publishes
 * ({@code log4j2.xml}). Without that, the loader would keep {@code BuiltinLogHandler} and every
 * producer would write in its own format to its own stream.
 *
 * <p>Mixin environment: the loader drives Mixin itself
 * ({@code net.fabricmc.loader.impl.launch.FabricMixinBootstrap#init}) from the {@code mixins} list
 * of every loaded mod's {@code fabric.mod.json}, so nothing has to be done here beyond adding the
 * game to the class path.
 */
public final class PvzGameProvider implements GameProvider {

    /** Mod id of the game itself, used by mods in their {@code depends} block. */
    public static final String GAME_ID = "pvz-portable";
    /** Human readable game name, shown by the loader at startup. */
    public static final String GAME_NAME = "PvZ Portable";
    /**
     * Game version used unless {@value #GAME_VERSION_PROPERTY} overrides it. It is the game's own
     * version ({@code PvZ-Portable}'s {@code version}, which states it as well): what the loader
     * reports as the built-in {@code pvz-portable} mod names the game a user runs. Changing the game's
     * version means changing both.
     */
    public static final String DEFAULT_GAME_VERSION = "1.2.3";
    /** Main class of the game. */
    public static final String DEFAULT_ENTRYPOINT = "cn.enaium.fabricpvz.portable.lawn.Main";

    /** Class used to find the game on the class path; see {@link #locateGame}. */
    public static final String GAME_PROBE_CLASS = DEFAULT_ENTRYPOINT;
    /** Package prefix of the game's classes, used to tell game classes from mod classes. */
    public static final String GAME_PACKAGE_PREFIX = "cn.enaium.fabricpvz.portable.";

    /** System property overriding the game version (default {@value #DEFAULT_GAME_VERSION}). */
    public static final String GAME_VERSION_PROPERTY = "pvz.gameVersion";
    /** System property overriding the game entry point (default {@value #DEFAULT_ENTRYPOINT}). */
    public static final String ENTRYPOINT_PROPERTY = "pvz.game.mainClass";
    /** System property holding the game directory, the alternative to the {@code -gamedir} argument. */
    public static final String GAME_DIR_PROPERTY = "pvz.gameDir";
    /** Command line argument holding the game directory (upstream-style single dash). */
    public static final String GAME_DIR_ARGUMENT = "-gamedir";
    /** System property with further class path entries for the game class loader. */
    public static final String EXTRA_CLASS_PATH_PROPERTY = "pvz.extraClasspath";

    /** {@value #LOG_HANDLER_CLASS}: the loader's own SLF4J bridge, the class its Minecraft provider uses. */
    private static final String LOG_HANDLER_CLASS =
            "net.fabricmc.loader.impl.game.minecraft.Slf4jLogHandler";

    private static final String GAME_PROBE_RESOURCE = GAME_PROBE_CLASS.replace('.', '/') + ".class";

    /**
     * Classes of the game get their package-private members widened and may be modified by mod
     * access wideners; other classes (mods, libraries) may only be modified by access wideners.
     *
     * <p>The widening only takes effect when the launcher enables Fabric's package access hack
     * ({@code -Dfabric.fixPackageAccess=true} or a named mapping namespace); the port ships public
     * game APIs, so it is not required, but asking for it matches Minecraft's provider and keeps
     * mods that shadow package-private members working in both modes.
     */
    private static final Set<BuiltinTransform> GAME_TRANSFORMS =
            EnumSet.of(BuiltinTransform.WIDEN_ALL_PACKAGE_ACCESS, BuiltinTransform.CLASS_TWEAKS);
    private static final Set<BuiltinTransform> OTHER_TRANSFORMS =
            EnumSet.of(BuiltinTransform.CLASS_TWEAKS);

    /** Holds the classes patched before they are loaded; the port ships no entry point patches. */
    private final GameTransformer transformer = new GameTransformer();
    private final List<Path> gameJars = new ArrayList<>(1);
    private Arguments arguments;
    private String entrypoint = DEFAULT_ENTRYPOINT;
    private String gameVersion = DEFAULT_GAME_VERSION;

    /**
     * Points Fabric Loader's log at the workspace's logging stack (SLF4J over Log4j), which is what
     * makes the loader's own lines, the Mixin subsystem's lines and the mods' lines one stream in
     * one format.
     *
     * <p>Why the constructor: the loader installs a game provider, and then a logging environment, in
     * this order ({@code Knot#init}) -
     * <ol>
     *   <li>early lines (this provider's class path warnings, the "Loading ... with Fabric Loader"
     *       line) go to {@code BuiltinLogHandler}, which holds them in a buffer while it waits for the
     *       provider to say where logging goes and prints them with its own console format if nobody
     *       does;</li>
     *   <li>{@code Knot#createGameProvider} instantiates the provider through the {@code ServiceLoader}
     *       - this constructor;</li>
     *   <li>{@code Log#finishBuiltinConfig} flushes that buffer.</li>
     * </ol>
     * Installing the handler here is therefore the last moment at which the loader's first line can
     * still be routed: {@code Log#init} replays the buffer into the new handler (the built-in one is
     * closed, and the file it would have dumped is not written), so a boot log has exactly one format
     * from its very first line. Doing it in {@link #initialize} instead would be a line too late.
     *
     * <p>Minecraft's own provider does the same thing with the same mechanism
     * ({@code MinecraftGameProvider#setupLogHandler} installs {@code Log4jLogHandler} or
     * {@code Slf4jLogHandler}) - this port uses the SLF4J one because the SLF4J API is the workspace's
     * single facade: the loader and Mixin through {@code Slf4jLogHandler}, every mod through
     * {@code org.slf4j.LoggerFactory}, all of them in the Log4j context {@code log4j-slf4j2-impl}
     * binds here and configured by {@code log4j2.xml} (a resource of this module, published with the
     * stack).
     *
     * <p>Both handlers live in the loader's own {@code impl.game.minecraft} package, so - like the
     * loader's provider does - this one names the class instead of importing it, and degrades to the
     * loader's built-in console handler (with a warning) when the stack is not on the class path.
     */
    public PvzGameProvider() {
        installLogHandler();
    }

    /**
     * Hands the loader's log to the SLF4J bridge (and so to the Log4j backend of this workspace).
     *
     * <p>The handler is loaded by this class's own class loader - the launch class path, where the
     * loader and the logging stack both are - so the loader, the Mixin subsystem and the mods all use
     * the one {@code Log} and the one Log4j context; nothing is loaded into the game class loader,
     * which would only create a second copy of the handler's static state.
     */
    private static void installLogHandler() {
        try {
            Class<?> handlerClass = Class.forName(LOG_HANDLER_CLASS);
            Log.init((LogHandler) handlerClass.getConstructor().newInstance());
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                | IllegalAccessException | InvocationTargetException | LinkageError e) {
            // No SLF4J/Log4j stack on the class path (a consumer that excluded it): the loader keeps
            // the handler it already has, which still logs to the console, and this warning is the
            // only sign that a boot is running without the workspace's log configuration.
            Log.warn(LogCategory.GAME_PROVIDER,
                    "No %s on the class path (%s): keeping the loader's built-in console log handler",
                    LOG_HANDLER_CLASS, e);
        }
    }

    @Override
    public String getGameId() {
        return GAME_ID;
    }

    @Override
    public String getGameName() {
        return GAME_NAME;
    }

    @Override
    public String getRawGameVersion() {
        return gameVersion;
    }

    @Override
    public String getNormalizedGameVersion() {
        return gameVersion;
    }

    /**
     * The game is a built-in mod: its id is what mods depend on and the loader reports it like any
     * other mod, but its metadata and version come from this provider instead of a
     * {@code fabric.mod.json} inside the game jar.
     */
    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        ModMetadata metadata = new BuiltinModMetadata.Builder(GAME_ID, gameVersion)
                .setName(GAME_NAME)
                .setDescription("Java port of PvZ-Portable (engine + game logic)")
                .build();

        return Collections.singletonList(new BuiltinMod(gameJars, metadata));
    }

    @Override
    public String getEntrypoint() {
        return entrypoint;
    }

    /**
     * The game directory, which is where the loader looks for {@code mods/} and for the
     * {@code .fabric/} cache.
     *
     * <p>Resolution order: {@code -Dpvz.gameDir}, then the {@code -gamedir <path>} /
     * {@code -gamedir=<path>} command line argument, then the current working directory.
     */
    @Override
    public Path getLaunchDirectory() {
        String property = System.getProperty(GAME_DIR_PROPERTY);

        if (property != null && !property.isBlank()) {
            return Paths.get(property).toAbsolutePath().normalize();
        }

        String fromArguments = gameDirArgument();

        return (fromArguments == null ? Paths.get(".") : Paths.get(fromArguments)).toAbsolutePath().normalize();
    }

    /**
     * The port is a plain child-first-class-loader game: it needs no {@code URLClassLoader} level
     * between the loader and the game (that exists for legacy Forge installations).
     */
    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public Set<BuiltinTransform> getBuiltinTransforms(String className) {
        return className.startsWith(GAME_PACKAGE_PREFIX) ? GAME_TRANSFORMS : OTHER_TRANSFORMS;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Parses the game arguments and finds the game on the class path.
     *
     * @return {@code false} when the game is not there, which lets the loader keep looking for
     *     another provider (and report a sensible error when there is none)
     */
    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        this.arguments = new Arguments();
        arguments.parse(args);

        String configuredEntrypoint = System.getProperty(ENTRYPOINT_PROPERTY, DEFAULT_ENTRYPOINT).trim();
        this.entrypoint = configuredEntrypoint.isEmpty() ? DEFAULT_ENTRYPOINT : configuredEntrypoint;

        String configuredVersion = System.getProperty(GAME_VERSION_PROPERTY, DEFAULT_GAME_VERSION).trim();
        this.gameVersion = configuredVersion.isEmpty() ? DEFAULT_GAME_VERSION : configuredVersion;

        Path gameJar = findGameJar(launcher);

        if (gameJar == null) {
            return false;
        }

        gameJars.clear();
        gameJars.add(gameJar);

        return true;
    }

    /** Locates the class path entry that contains the game's main class. */
    private static Path findGameJar(FabricLauncher launcher) {
        for (Path path : launcher.getClassPath()) {
            if (containsGame(path)) {
                return path;
            }
        }

        return null;
    }

    private static boolean containsGame(Path path) {
        if (Files.isDirectory(path)) {
            return Files.isRegularFile(path.resolve(GAME_PROBE_RESOURCE));
        }

        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
            return false;
        }

        try (ZipFile jar = new ZipFile(path.toFile())) {
            return jar.getEntry(GAME_PROBE_RESOURCE) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * No entry point patches: the port's entry point needs no rewriting, so the transformer stays
     * empty. It is still provided, because the loader asks for it once transformers are
     * initialized.
     */
    @Override
    public void initialize(FabricLauncher launcher) {
        transformer.locateEntrypoints(launcher, gameJars);
    }

    @Override
    public GameTransformer getEntrypointTransformer() {
        return transformer;
    }

    /**
     * Adds the game to the Knot class loader. This is the step that makes Mixin able to transform
     * the game: classes that are class path entries of the Knot loader are defined by it (instead
     * of being delegated to the parent class loader) and therefore run through the transformer
     * chain.
     *
     * <p>{@value #EXTRA_CLASS_PATH_PROPERTY} appends further entries (for development setups that
     * keep game-side classes outside the game jar or directory, such as this port's own loader
     * tests); missing entries are skipped with a warning, exactly like the loader treats a missing
     * class path entry.
     */
    @Override
    public void unlockClassPath(FabricLauncher launcher) {
        for (Path gameJar : gameJars) {
            launcher.addToClassPath(gameJar);
        }

        for (Path path : extraClassPath()) {
            if (Files.exists(path)) {
                Log.info(LogCategory.GAME_PROVIDER, "Adding %s to the game class path", path);
                launcher.addToClassPath(path);
            } else {
                Log.warn(LogCategory.GAME_PROVIDER, "Ignoring missing %s entry %s",
                        EXTRA_CLASS_PATH_PROPERTY, path);
            }
        }
    }

    /** Extra game class path entries, from {@value #EXTRA_CLASS_PATH_PROPERTY}. */
    private static List<Path> extraClassPath() {
        String value = System.getProperty(EXTRA_CLASS_PATH_PROPERTY);

        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<Path> paths = new ArrayList<>();

        for (String entry : value.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                paths.add(Paths.get(entry.trim()));
            }
        }

        return paths;
    }

    @Override
    public Arguments getArguments() {
        return arguments;
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return arguments == null ? new String[0] : arguments.toArray();
    }

    /**
     * Starts the game: first runs the mod entry points the game is responsible for, then calls the
     * game's {@code main(String[])} through the loader's class loader.
     *
     * <p>Fabric Loader invokes {@code preLaunch} entry points itself, but the {@code main} and
     * {@code client} entry points of a game are invoked by the game (Minecraft does it from its
     * patched entry point through {@code net.fabricmc.loader.impl.game.minecraft.Hooks#startClient}).
     * A non-Minecraft game has to do the same from its game provider, otherwise no mod would ever
     * initialize: this is the step that calls {@link ModInitializer#onInitialize()} and
     * {@link ClientModInitializer#onInitializeClient()}.
     *
     * <p>The port is a desktop game, so it is launched with the client launcher
     * ({@code net.fabricmc.loader.impl.launch.knot.KnotClient}); the {@code client} entry points run
     * when the loader reports the client environment.
     *
     * <p>The entry point must be public with a public static {@code main(String[])}, like any JVM
     * entry point; the game arguments are passed through unchanged.
     */
    @Override
    public void launch(ClassLoader loader) {
        FabricLoader fabricLoader = FabricLoader.getInstance();
        fabricLoader.invokeEntrypoints("main", ModInitializer.class, ModInitializer::onInitialize);

        if (fabricLoader.getEnvironmentType() == EnvType.CLIENT) {
            fabricLoader.invokeEntrypoints("client", ClientModInitializer.class,
                    ClientModInitializer::onInitializeClient);
        }

        String targetClass = entrypoint;
        MethodHandle invoker;

        try {
            Class<?> type = loader.loadClass(targetClass);
            invoker = MethodHandles.publicLookup()
                    .findStatic(type, "main", MethodType.methodType(void.class, String[].class));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Could not find the game entry point " + targetClass
                    + " (override it with -D" + ENTRYPOINT_PROPERTY + ")", e);
        }

        String[] gameArgs = arguments == null ? new String[0] : arguments.toArray();

        try {
            invoker.invokeExact(gameArgs);
        } catch (Throwable t) {
            throw new IllegalStateException("The game entry point " + targetClass + " threw an exception", t);
        }
    }

    /** Reads the game directory from the {@code -gamedir} argument, if the command line has one. */
    private String gameDirArgument() {
        if (arguments == null) {
            return null;
        }

        List<String> args = arguments.getExtraArgs();

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);

            if (arg.equals(GAME_DIR_ARGUMENT) && i + 1 < args.size()) {
                return args.get(i + 1);
            }

            if (arg.startsWith(GAME_DIR_ARGUMENT + "=")) {
                return arg.substring(GAME_DIR_ARGUMENT.length() + 1);
            }
        }

        return null;
    }
}
