package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.ResourceLocator;
import cn.enaium.fabricpvz.portable.pvzp.SteamLibraryFinder;

/**
 * Verifies the resource-directory resolution added by the Java port on top of upstream's
 * executable-directory rule: {@code -resdir} wins, otherwise the working directory / Steam
 * installation are searched (see {@link SexyAppBase#resolveResourceDirectory()}).
 */
class ResourceDirectoryResolutionTest {

    @TempDir
    Path tmp;

    @Test
    void explicitResourceDirectoryWins() {
        SexyAppBase app = new SexyAppBase();
        app.mResourceDir = tmp.toString();
        assertEquals(tmp.toString(), app.resolveResourceDirectory());
    }

    @Test
    void fallsBackToDiscoveryWhenNoExplicitDirectory() throws IOException {
        SexyAppBase app = new SexyAppBase();
        app.mResourceDir = "";
        String resolved = app.resolveResourceDirectory();
        assertNotNull(resolved);

        boolean realAssetsAvailable = ResourceLocator.discover(new String[0]).resourceRoot() != null;
        Assumptions.assumeTrue(realAssetsAvailable, "no discoverable PvZ assets on this machine");
        assertTrue(ResourceLocator.isValidResourceRoot(Path.of(resolved)),
                "discovered directory must be a usable resource root: " + resolved);
    }

    @Test
    void steamSearchIsPartOfDiscovery() {
        Path real = SteamLibraryFinder.findInstalledGameResourceRoot();
        Assumptions.assumeTrue(real != null, "no Steam PvZ installation on this machine");
        assertTrue(Files.isRegularFile(real.resolve("main.pak")) || Files.isRegularFile(real.resolve("MAIN.PAK")),
                "Steam discovery must find a directory containing main.pak");
    }
}
