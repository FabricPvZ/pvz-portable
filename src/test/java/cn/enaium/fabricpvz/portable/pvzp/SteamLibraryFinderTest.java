package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies Steam root/library discovery and libraryfolders.vdf parsing. */
class SteamLibraryFinderTest {

    @TempDir
    Path tmp;

    @Test
    void steamRootsPerOperatingSystem() {
        Path home = Path.of("/home/player");

        List<Path> mac = SteamLibraryFinder.steamRoots("Mac OS X", home, Map.of());
        assertEquals(
                List.of(Path.of("/home/player/Library/Application Support/Steam")), mac);

        List<Path> windows = SteamLibraryFinder.steamRoots(
                "Windows 11", home, Map.of("ProgramFiles(x86)", "C:\\Program Files (x86)"));
        assertEquals(Path.of("C:\\Program Files (x86)\\Steam"), windows.get(0));

        List<Path> linux = SteamLibraryFinder.steamRoots("Linux", home, Map.of());
        assertTrue(linux.contains(Path.of("/home/player/.steam/steam")));
        assertTrue(linux.contains(Path.of("/home/player/.local/share/Steam")));
        assertTrue(linux.contains(Path.of("/home/player/.var/app/com.valvesoftware.Steam/.local/share/Steam")));
    }

    @Test
    void homesPreferTheEnvironmentOverARemappedUserHome() {
        // What a launcher does: user.home becomes the game directory. Both are searched, the real one
        // first - the case that made a Steam installation invisible to a launch under HMCL.
        List<Path> homes = SteamLibraryFinder.homes("/Users/somebody", "/Users/somebody/.minecraft");
        assertEquals(
            List.of(Path.of("/Users/somebody"), Path.of("/Users/somebody/.minecraft")),
            homes
        );
        // A machine where they agree keeps one.
        assertEquals(List.of(Path.of("/Users/somebody")), SteamLibraryFinder.homes("/Users/somebody", "/Users/somebody"));
        assertEquals(List.of(Path.of("/home/x")), SteamLibraryFinder.homes(null, "/home/x"));
        assertEquals(List.of(), SteamLibraryFinder.homes(null, null));
    }

    @Test
    void everyHomeContributesItsSteamRoots() {
        List<Path> roots = SteamLibraryFinder.steamRoots(
                "Mac OS X",
                SteamLibraryFinder.homes("/Users/somebody", "/Users/somebody/.minecraft"),
                Map.of()
        );
        assertEquals(
            List.of(
                Path.of("/Users/somebody/Library/Application Support/Steam"),
                Path.of("/Users/somebody/.minecraft/Library/Application Support/Steam")
            ),
            roots
        );
    }

    @Test
    void parsesBothLibraryFolderFormats() {
        String vdf = """
                "libraryfolders"
                {
                	"0"
                	{
                		"path"		"/home/player/.steam/steam"
                	}
                	"1"
                	{
                		"path"		"/mnt/games/SteamLibrary"
                		"label"		""
                		"apps"
                		{
                			"3590"		"4123456"
                		}
                	}
                }
                """;
        assertEquals(
                List.of(Path.of("/home/player/.steam/steam"), Path.of("/mnt/games/SteamLibrary")),
                SteamLibraryFinder.parseLibraryFolders(vdf));
    }

    @Test
    void findsGameInsideSecondaryLibraryAndValidatesManifest() throws IOException {
        Path primary = Files.createDirectories(tmp.resolve("steam"));
        Path secondary = Files.createDirectories(tmp.resolve("secondary"));
        Files.createDirectories(secondary.resolve("steamapps/common/Plants vs. Zombies"));
        Files.createDirectories(primary.resolve("steamapps"));
        Files.writeString(
                primary.resolve("steamapps/libraryfolders.vdf"),
                "\"libraryfolders\"\n{\n\t\"1\"\n\t{\n\t\t\"path\"\t\t\"" + secondary + "\"\n\t}\n}\n");
        Files.writeString(secondary.resolve("steamapps/appmanifest_3590.acf"), "\"AppState\"\n{\n\t\"appid\"\n\t\"3590\"\n}\n");
        Files.write(secondary.resolve("steamapps/common/Plants vs. Zombies/main.pak"), new byte[] {1});

        List<Path> libraries = SteamLibraryFinder.steamLibraries(primary);
        assertEquals(2, libraries.size());
        assertEquals(
                secondary.resolve("steamapps/common/Plants vs. Zombies").toAbsolutePath(),
                SteamLibraryFinder.findGameResourceRoot(secondary).toAbsolutePath());
        assertNull(SteamLibraryFinder.findGameResourceRoot(primary.resolve("steamapps").getParent().resolve("missing")));
    }

    @Test
    void caseInsensitiveGameFolderMatch() throws IOException {
        Path steam = Files.createDirectories(tmp.resolve("steam3"));
        Path game = Files.createDirectories(steam.resolve("steamapps/common/PLANTS VS ZOMBIES"));
        Files.createDirectories(game.resolve("properties"));
        Files.write(game.resolve("main.pak"), new byte[] {2});
        assertEquals(game.toAbsolutePath(), SteamLibraryFinder.findGameResourceRoot(steam).toAbsolutePath());
    }
}
