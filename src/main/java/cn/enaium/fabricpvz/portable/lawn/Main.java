package cn.enaium.fabricpvz.portable.lawn;

/**
 * Ported from src/main.cpp:85-106.
 *
 * <p>Process entry point: installs the game-side hooks, creates the application and runs the
 * canonical lifecycle {@code SetArgs -> Init -> Start -> Shutdown}.
 *
 * <p>In the Java port this class is normally reached through Fabric Loader: the launcher
 * ({@code net.fabricmc.loader.impl.launch.knot.KnotClient}) discovers the game through the game
 * provider the game jar itself carries, loads the {@code mods/} directory and
 * mod entry points, and then calls {@code Main.main(String[])} through the loader's class loader.
 * Running it directly (without the launcher) also works and simply means no mods are loaded.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        installHooks();

        LawnApp aLawnApp = new LawnApp();
        LawnApp.gLawnApp = aLawnApp;
        // Upstream passes real argv (argv[0] = program name) and the parser starts at index 1; the JVM
        // hands us only the arguments, so mirror the C++ layout with a placeholder program name.
        String[] aArgs = new String[args.length + 1];
        aArgs[0] = "pvz-portable";
        System.arraycopy(args, 0, aArgs, 1, args.length);
        aLawnApp.setArgs(aArgs);
        aLawnApp.init();
        aLawnApp.start();
        aLawnApp.shutdown();
    }

    /**
     * Installs the game-side hooks that upstream {@code main.cpp} sets before creating the app: the
     * string-format colours and the resource-group extractor that binds loaded resources into
     * {@code Resources.gResources}.
     *
     * <p>Anything that boots the game outside {@link #main} (tools, tests) must call this first,
     * otherwise resource ids resolve to {@code null} and the first image draw fails.
     */
    public static void installHooks() {
        // Upstream: PvzpStringListSetColors(gLawnStringFormats, gLawnStringFormatCount);
        cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile.pvzpStringListSetColors(
                cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile.gLawnStringFormats,
                cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile.gLawnStringFormatCount);

        // Upstream: gExtractResourcesByName = Sexy::ExtractResourcesByName;
        cn.enaium.fabricpvz.portable.pvzp.PvzpResourceManager.gExtractResourcesByName = Resources::extractResourcesByName;
    }
}
