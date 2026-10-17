package cn.enaium.fabricpvz.portable.sexy.pak;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The case-insensitive resolution of a resource path ({@link PakSource#resolveCaseInsensitive}): the
 * port has to fold <em>every</em> component, as upstream's {@code fcaseopen} does, because the retail
 * layout spells the resource directory {@code properties} while the game asks for
 * {@code Properties/ZombatarTOS.txt} (LawnApp.java:1568).
 */
class PakSourceCaseInsensitiveTest {

    @TempDir
    Path tmp;

    /**
     * A file system that distinguishes case, emulated over a virtual tree: the listing knows only the
     * lower-case spelling, and the requested path spells the directory differently.
     *
     * <p>The tree is deliberately virtual (only the root of it exists on disk): on a host whose file
     * system is case-insensitive, {@code Files.exists} answers a differently-cased request by itself,
     * so a real tree would be resolved by the fast path and never reach the fold at all - which is
     * exactly what hid this defect on macOS. This is the counter-proof of the fix: the previous
     * implementation folded the file name only, looked for the parent {@code <root>/Properties} as a
     * directory and returned {@code null}.
     */
    @Test
    void foldsEveryPathComponentNotJustTheFileName() throws IOException {
        Path root = Files.createDirectory(tmp.resolve("resource-root"));
        Map<Path, List<Path>> tree = Map.of(
                root, List.of(root.resolve("properties")),
                root.resolve("properties"), List.of(root.resolve("properties/ZombatarTOS.txt")));
        PakSource.DirectoryListing exactSpellings = tree::get;

        Path resolved = PakSource.resolveCaseInsensitive(root.resolve("Properties/ZombatarTOS.txt"), exactSpellings);
        assertEquals(root.resolve("properties/ZombatarTOS.txt"), resolved);

        // Mixed case in either component works the same way.
        assertEquals(root.resolve("properties/ZombatarTOS.txt"),
                PakSource.resolveCaseInsensitive(root.resolve("PROPERTIES/zombatartos.TXT"), exactSpellings));
        assertEquals(root.resolve("properties"),
                PakSource.resolveCaseInsensitive(root.resolve("Properties"), exactSpellings));
    }

    @Test
    void anUnresolvableOrAmbiguousComponentIsNull() throws IOException {
        Path root = Files.createDirectory(tmp.resolve("resource-root"));
        Map<Path, List<Path>> tree = Map.of(
                root, List.of(root.resolve("properties"), root.resolve("Properties")),
                root.resolve("properties"), List.of(root.resolve("properties/ZombatarTOS.txt")));
        PakSource.DirectoryListing twoSpellings = tree::get;

        // Two spellings of the same name have no unambiguous answer (upstream takes the first readdir
        // match; this refuses instead of picking one).
        assertNull(PakSource.resolveCaseInsensitive(root.resolve("propertieS"), twoSpellings));
        // A component that is not there at all, and a file where a directory is expected.
        assertNull(PakSource.resolveCaseInsensitive(root.resolve("Images"), twoSpellings));
        assertNull(PakSource.resolveCaseInsensitive(root.resolve("properties/ZombatarTOS.txt/more"), twoSpellings));
    }

    @Test
    void anExactSpellingIsReturnedAsIsWithoutListing() throws IOException {
        Path file = Files.writeString(tmp.resolve("ZombatarTOS.txt"), "terms");
        PakSource.DirectoryListing neverListed = directory -> {
            throw new AssertionError("the fast path must not list " + directory);
        };

        assertEquals(file, PakSource.resolveCaseInsensitive(file, neverListed));
    }

    /**
     * The defect end to end, as {@code LawnApp} triggers it: the file on disk is spelled
     * {@code properties/ZombatarTOS.txt} and the game asks for {@code Properties/ZombatarTOS.txt}.
     *
     * <p>On a case-insensitive host the file system answers this by itself (which is why macOS and
     * Windows never showed it); on Linux - where {@code PakInterface.open} has to fold the path - this
     * is the request that used to fail with {@code Failed to open 'Properties/ZombatarTOS.txt'}.
     */
    @Test
    void readsAMixedCaseResourcePathFromTheFileSystem() throws IOException {
        byte[] terms = "[TOS]Terms of use\r\n".getBytes(StandardCharsets.ISO_8859_1);
        Path resourceFolder = Files.createDirectory(tmp.resolve("resources"));
        Files.createDirectories(resourceFolder.resolve("properties"));
        Files.write(resourceFolder.resolve("properties/ZombatarTOS.txt"), terms);

        PakInterface pakInterface = new PakInterface();
        pakInterface.setResourceFolder(resourceFolder.toString());

        assertArrayEquals(terms, pakInterface.requireResource("Properties/ZombatarTOS.txt"));
        assertArrayEquals(terms, pakInterface.requireResource("PROPERTIES/ZOMBATARTOS.TXT"));
        // The resolved path is the file that is there. On a case-insensitive host it is the request
        // itself (the file system answered it), on a case-sensitive one it is the folded spelling.
        assertTrue(Files.isSameFile(resourceFolder.resolve("properties/ZombatarTOS.txt"),
                PakSource.resolveCaseInsensitive(resourceFolder.resolve("Properties/ZombatarTOS.txt"))));
    }
}
