package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resource directory discovery for the Java port.
 *
 * <p>Search order (first hit wins), matching the port plan:
 * <ol>
 *   <li>{@code -resdir=<path>} command line argument</li>
 *   <li>{@code PVZ_RES_DIR} environment variable</li>
 *   <li>current working directory</li>
 *   <li>directory of the running jar / executable</li>
 *   <li>the local Steam installation of Plants vs. Zombies GOTY (app id 3590), searched through
 *       every Steam library folder</li>
 * </ol>
 *
 * <p>A directory qualifies as a resource root when it contains {@code main.pak} (case-insensitive)
 * or a {@code properties} directory, mirroring what PvZ-Portable needs at boot.
 */
public final class ResourceLocator {

    public static final String MAIN_PAK = "main.pak";
    public static final String PROPERTIES_DIR = "properties";

    private ResourceLocator() {
    }

    /** Result of a discovery run, remembering where the root came from for diagnostics. */
    public record Discovery(Path resourceRoot, Source source, List<Path> searched) {
        public enum Source {
            CLI_ARGUMENT,
            ENVIRONMENT,
            WORKING_DIRECTORY,
            EXECUTABLE_DIRECTORY,
            STEAM
        }
    }

    /** Extracts the value of a {@code -resdir <path>} / {@code -resdir=<path>} argument. */
    public static Optional<String> parseResDirArgument(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-resdir=")) {
                return Optional.of(arg.substring("-resdir=".length()));
            }
            if (arg.equals("-resdir") && i + 1 < args.length) {
                return Optional.of(args[i + 1]);
            }
        }
        return Optional.empty();
    }

    /** Extracts the value of a {@code -savedir <path>} / {@code -savedir=<path>} argument. */
    public static Optional<String> parseSaveDirArgument(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-savedir=")) {
                return Optional.of(arg.substring("-savedir=".length()));
            }
            if (arg.equals("-savedir") && i + 1 < args.length) {
                return Optional.of(args[i + 1]);
            }
        }
        return Optional.empty();
    }

    /** Discovers the resource root using the standard search order. */
    public static Discovery discover(String[] args, Path workingDir, Path executableDir, String envResDir) {
        return discover(args, workingDir, executableDir, envResDir, SteamLibraryFinder.defaultSteamRoots());
    }

    /** Discovers the resource root, searching the given Steam roots (injectable for tests). */
    public static Discovery discover(
            String[] args, Path workingDir, Path executableDir, String envResDir, List<Path> steamRoots) {
        List<Path> searched = new ArrayList<>();

        Optional<String> cliResDir = parseResDirArgument(args);
        if (cliResDir.isPresent()) {
            Path candidate = Path.of(cliResDir.get()).toAbsolutePath();
            searched.add(candidate);
            if (isValidResourceRoot(candidate)) {
                return new Discovery(candidate, Discovery.Source.CLI_ARGUMENT, searched);
            }
        }

        if (envResDir != null && !envResDir.isBlank()) {
            Path candidate = Path.of(envResDir).toAbsolutePath();
            searched.add(candidate);
            if (isValidResourceRoot(candidate)) {
                return new Discovery(candidate, Discovery.Source.ENVIRONMENT, searched);
            }
        }

        if (workingDir != null) {
            searched.add(workingDir);
            if (isValidResourceRoot(workingDir)) {
                return new Discovery(workingDir, Discovery.Source.WORKING_DIRECTORY, searched);
            }
        }

        if (executableDir != null && (workingDir == null || !executableDir.equals(workingDir))) {
            searched.add(executableDir);
            if (isValidResourceRoot(executableDir)) {
                return new Discovery(executableDir, Discovery.Source.EXECUTABLE_DIRECTORY, searched);
            }
        }

        if (steamRoots != null) {
            for (Path steamRoot : steamRoots) {
                if (!Files.isDirectory(steamRoot)) {
                    continue;
                }
                for (Path library : SteamLibraryFinder.steamLibraries(steamRoot)) {
                    Path found = SteamLibraryFinder.findGameResourceRoot(library);
                    if (found != null) {
                        searched.add(found);
                        return new Discovery(found, Discovery.Source.STEAM, searched);
                    }
                }
            }
        }

        return new Discovery(null, null, searched);
    }

    /** Convenience overload using the current process working directory. */
    public static Discovery discover(String[] args) {
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        Path exeDir = executableDirectory();
        return discover(args, cwd, exeDir, System.getenv("PVZ_RES_DIR"));
    }

    /** Directory containing the running code (jar location, or the classpath root). */
    public static Path executableDirectory() {
        try {
            var location = ResourceLocator.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                Path path = Path.of(location.toURI());
                return Files.isDirectory(path) ? path : path.getParent();
            }
        } catch (Exception ignored) {
            // Fall through to the working directory.
        }
        return null;
    }

    /** A directory is a resource root when it has main.pak or a properties folder. */
    public static boolean isValidResourceRoot(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return hasMainPak(dir) || Files.isDirectory(dir.resolve(PROPERTIES_DIR));
    }

    public static boolean hasMainPak(Path dir) {
        if (Files.isRegularFile(dir.resolve(MAIN_PAK)) || Files.isRegularFile(dir.resolve("MAIN.PAK"))) {
            return true;
        }
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(MAIN_PAK));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Searches {@code base} for a valid resource root: the directory itself, a macOS
     * {@code *.app/Contents/Resources} bundle inside it, or any immediate subdirectory.
     */
    public static Path findResourceRootWithin(Path base) {
        if (isValidResourceRoot(base)) {
            return base;
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(base.resolve("Resources"));
        candidates.addAll(appBundleResources(base));
        try (var stream = Files.list(base)) {
            stream.filter(Files::isDirectory).forEach(candidates::add);
        } catch (IOException ignored) {
            // best effort
        }
        for (Path candidate : candidates) {
            if (isValidResourceRoot(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Path> appBundleResources(Path base) {
        List<Path> out = new ArrayList<>();
        try (var stream = Files.list(base)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".app"))
                    .map(p -> p.resolve("Contents/Resources"))
                    .filter(Files::isDirectory)
                    .forEach(out::add);
        } catch (IOException ignored) {
            // best effort
        }
        return out;
    }

    /** Every Steam library game directory candidate, for diagnostics/tests. */
    public static List<Path> steamSearchPaths() {
        List<Path> out = new LinkedHashSet<>(SteamLibraryFinder.defaultSteamRoots()).stream().toList();
        return new ArrayList<>(out);
    }
}
