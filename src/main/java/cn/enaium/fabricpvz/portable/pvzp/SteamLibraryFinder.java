package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Steam installation discovery for the retail copy of Plants vs. Zombies (GOTY Edition, app id 3590).
 *
 * <p>The game assets are read from the installed Steam copy, matching the Java port's resource
 * requirement: resources may come either from the working directory or from the local Steam
 * installation. This class locates the Steam root(s), parses {@code steamapps/libraryfolders.vdf}
 * (and its current variant {@code libraryfolders.vdf} with "path" entries), and finds the game's
 * resource folder inside the library.
 *
 * <p>Known layouts (verified against real installations):
 * <ul>
 *   <li>macOS: {@code ~/Library/Application Support/Steam/steamapps/common/Plants Vs Zombies/
 *       Plants vs. Zombies.app/Contents/Resources/} (contains main.pak + properties/)</li>
 *   <li>Windows: {@code <steam>/steamapps/common/Plants vs. Zombies/} (main.pak next to the exe)</li>
 *   <li>Linux: {@code ~/.steam/steam/steamapps/common/Plants vs. Zombies/}</li>
 * </ul>
 */
public final class SteamLibraryFinder {

    /** Steam application id of "Plants vs. Zombies: Game of the Year Edition". */
    public static final int PvZ_APP_ID = 3590;

    public static final String APP_MANIFEST = "appmanifest_" + PvZ_APP_ID + ".acf";

    private static final String[] GAME_DIR_NAMES = {
        "Plants vs. Zombies",
        "Plants Vs Zombies",
        "Plants vs Zombies",
        "Plants vs. Zombies GOTY Edition",
        "PlantsVsZombies",
    };

    private SteamLibraryFinder() {
    }

    /** Ordered candidate Steam root directories for the current OS. */
    public static List<Path> defaultSteamRoots() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", "."));
        return steamRoots(os, home, System.getenv());
    }

    /**
     * Ordered candidate Steam root directories for {@code os} ({@code System.getProperty("os.name")},
     * docs/research/05-assets-resource-stack.md:793-795).
     *
     * <p>The match is case-insensitive because {@code os.name} is {@code "Mac OS X"}, not {@code "mac"}.
     */
    static List<Path> steamRoots(String os, Path home, java.util.Map<String, String> env) {
        String aOs = os.toLowerCase(Locale.ROOT);
        List<Path> roots = new ArrayList<>();
        if (aOs.contains("mac") || aOs.contains("darwin")) {
            roots.add(home.resolve("Library/Application Support/Steam"));
        } else if (aOs.contains("win")) {
            addIfPresent(roots, env.get("ProgramFiles(x86)"), "Steam");
            addIfPresent(roots, env.get("ProgramFiles"), "Steam");
            addIfPresent(roots, env.get("ProgramW6432"), "Steam");
            roots.add(Path.of("C:/Program Files (x86)/Steam"));
            roots.add(Path.of("C:/Program Files/Steam"));
        } else {
            roots.add(home.resolve(".steam/steam"));
            roots.add(home.resolve(".steam/root"));
            roots.add(home.resolve(".local/share/Steam"));
            roots.add(home.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam"));
            roots.add(home.resolve(".steam/debian-installation"));
        }
        return distinct(roots);
    }

    /**
     * Adds {@code <base>/<child>} when {@code base} is present.
     *
     * <p>PORT-NOTE: a Windows base ({@code C:\Program Files (x86)}) is a single path segment on a
     * Unix JVM, so {@link Path#resolve} would join it with {@code '/'} instead of {@code '\'}
     * (the documented {@code %ProgramFiles(x86)%\Steam} form,
     * docs/research/05-assets-resource-stack.md:794). Join the separator by hand when the base is a
     * Windows path.
     */
    private static void addIfPresent(List<Path> roots, String base, String child) {
        if (base == null || base.isBlank()) {
            return;
        }
        if (base.matches("(?i)^[a-z]:.*")) {
            String aPrefix = base.endsWith("\\") ? base : base + "\\";
            roots.add(Path.of(aPrefix + child));
        } else {
            roots.add(Path.of(base).resolve(child));
        }
    }

    /**
     * Case-insensitive directory match: returns the path as the directory is named on disk (pak keys
     * are upper-cased after normalization upstream, PakInterface.cpp:79-80, and {@code fcaseopen}
     * exists precisely because file systems are not case-sensitive). An exact-case match wins over
     * a folded one so the result never depends on the directory listing order.
     */
    private static Path findDirectoryIgnoringCase(Path theDir, String theName) {
        Path aFold = null;
        try (var stream = Files.list(theDir)) {
            for (Path anEntry : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(anEntry)) {
                    continue;
                }
                String anEntryName = anEntry.getFileName().toString();
                if (anEntryName.equals(theName)) {
                    return anEntry;
                }
                if (aFold == null && anEntryName.equalsIgnoreCase(theName)) {
                    aFold = anEntry;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return aFold;
    }

    private static List<Path> distinct(List<Path> paths) {
        return new ArrayList<>(new LinkedHashSet<>(paths));
    }

    /** All Steam library roots: the Steam root itself plus every "path" entry from libraryfolders.vdf. */
    public static List<Path> steamLibraries(Path steamRoot) {
        List<Path> libraries = new ArrayList<>();
        libraries.add(steamRoot);
        Path vdf = steamRoot.resolve("steamapps/libraryfolders.vdf");
        if (Files.isRegularFile(vdf)) {
            try {
                libraries.addAll(parseLibraryFolders(Files.readString(vdf)));
            } catch (IOException e) {
                // Unreadable library file: keep the default root (upstream has no equivalent, this is best-effort discovery).
            }
        }
        return distinct(libraries);
    }

    /**
     * Parses a Steam {@code libraryfolders.vdf} listing and returns the "path" values.
     * Handles both the legacy numeric-index format and the current {@code "path" "..."} format.
     *
     * <p>The value is the <b>second</b> quoted token on the line: {@code "path" <padding> "<abs
     * path>"} (docs/research/05-assets-resource-stack.md:797). Reading the text between the two
     * quotes around the key instead yields the padding, not the path.
     */
    static List<Path> parseLibraryFolders(String vdfContent) {
        List<Path> paths = new ArrayList<>();
        for (String line : vdfContent.split("\r?\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("\"path\"") && !trimmed.startsWith("\"Path\"")) {
                continue;
            }
            int aKeyEnd = trimmed.indexOf('"', 1);
            int aValueStart = aKeyEnd < 0 ? -1 : trimmed.indexOf('"', aKeyEnd + 1);
            int aValueEnd = aValueStart < 0 ? -1 : trimmed.indexOf('"', aValueStart + 1);
            if (aValueStart < 0 || aValueEnd < 0) {
                continue;
            }
            String value = trimmed.substring(aValueStart + 1, aValueEnd);
            paths.add(Path.of(value.replace("\\\\", "\\")));
        }
        return paths;
    }

    /** Finds the PvZ resource folder inside a Steam library, if installed there. */
    public static Path findGameResourceRoot(Path steamRoot) {
        Path steamApps = steamRoot.resolve("steamapps");
        boolean manifestPresent = Files.isRegularFile(steamApps.resolve(APP_MANIFEST));
        Path common = steamApps.resolve("common");
        if (!Files.isDirectory(common)) {
            return null;
        }
        for (Path gameDir : candidateGameDirs(common)) {
            Path existing = findDirectoryIgnoringCase(common, gameDir.getFileName().toString());
            if (existing == null) {
                continue;
            }
            Path root = ResourceLocator.findResourceRootWithin(existing);
            if (root != null) {
                return root;
            }
        }
        if (manifestPresent) {
            // Installed but assets not where we expect: report the common dir for diagnostics.
            return null;
        }
        return null;
    }

    private static List<Path> candidateGameDirs(Path common) {
        List<Path> dirs = new ArrayList<>();
        for (String name : GAME_DIR_NAMES) {
            dirs.add(common.resolve(name));
        }
        if (Files.isDirectory(common)) {
            try (var stream = Files.list(common)) {
                stream.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("plants"))
                        .forEach(dirs::add);
            } catch (IOException ignored) {
                // best effort
            }
        }
        return dirs;
    }

    /** Searches all default Steam roots and libraries for the game's resource folder. */
    public static Path findInstalledGameResourceRoot() {
        for (Path root : defaultSteamRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            for (Path library : steamLibraries(root)) {
                Path found = findGameResourceRoot(library);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
