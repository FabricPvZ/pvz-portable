package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the resource directory search order and the Steam fallback. */
class ResourceLocatorTest {

    @TempDir
    Path tmp;

    private Path makeRootWithPak(String name) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve(name));
        Files.write(dir.resolve("main.pak"), new byte[] {1, 2, 3});
        return dir;
    }

    private Path makeRootWithProperties(String name) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve(name));
        Files.createDirectories(dir.resolve("properties"));
        return dir;
    }

    @Test
    void systemPropertyIsHonoured() throws IOException {
        Path propertyRoot = makeRootWithProperties("property");
        Path cwdRoot = makeRootWithPak("cwd-for-property");
        System.setProperty(ResourceLocator.RES_DIR_PROPERTY, propertyRoot.toString());
        try {
            var found = ResourceLocator.discover(new String[0], cwdRoot, null, null, List.of());
            assertEquals(propertyRoot.toAbsolutePath(), found.resourceRoot());
            assertEquals(ResourceLocator.Discovery.Source.SYSTEM_PROPERTY, found.source());
        } finally {
            System.clearProperty(ResourceLocator.RES_DIR_PROPERTY);
        }
    }

    @Test
    void theCommandLineStillWinsOverTheProperty() throws IOException {
        Path cliRoot = makeRootWithPak("cli-over-property");
        Path propertyRoot = makeRootWithPak("property-loses");
        System.setProperty(ResourceLocator.RES_DIR_PROPERTY, propertyRoot.toString());
        try {
            var found = ResourceLocator.discover(
                    new String[] {"-resdir", cliRoot.toString()}, null, null, null, List.of());
            assertEquals(cliRoot.toAbsolutePath(), found.resourceRoot());
            assertEquals(ResourceLocator.Discovery.Source.CLI_ARGUMENT, found.source());
        } finally {
            System.clearProperty(ResourceLocator.RES_DIR_PROPERTY);
        }
    }

    @Test
    void theDiagnosisNamesWhatWasSearchedAndHowToFixIt() throws IOException {
        Path cwd = Files.createDirectories(tmp.resolve("nothing-here"));
        var found = ResourceLocator.discover(new String[0], cwd, null, null, List.of());
        assertNull(found.resourceRoot());
        String diagnosis = ResourceLocator.diagnose(found);
        // The one thing the failure message has to carry: where the search looked, and the three
        // ways to point it somewhere else (the JVM-argument form is the one a launcher can set).
        assertTrue(diagnosis.contains(cwd.toAbsolutePath().toString()), diagnosis);
        assertTrue(diagnosis.contains("-D" + ResourceLocator.RES_DIR_PROPERTY + "="), diagnosis);
        assertTrue(diagnosis.contains("-resdir"), diagnosis);
        assertTrue(diagnosis.contains("PVZ_RES_DIR"), diagnosis);
        assertTrue(diagnosis.contains("user.home="), diagnosis);
    }

    @Test
    void commandLineArgumentWins() throws IOException {
        Path cliRoot = makeRootWithPak("cli");
        Path cwdRoot = makeRootWithPak("cwd");

        var found = ResourceLocator.discover(
                new String[] {"-resdir", cliRoot.toString()}, cwdRoot, null, null, List.of());

        assertEquals(cliRoot.toAbsolutePath(), found.resourceRoot());
        assertEquals(ResourceLocator.Discovery.Source.CLI_ARGUMENT, found.source());
    }

    @Test
    void equalsFormArgumentIsParsed() throws IOException {
        Path cliRoot = makeRootWithPak("cli-eq");
        var found = ResourceLocator.discover(
                new String[] {"-resdir=" + cliRoot, "-savedir=/tmp/save"}, null, null, null, List.of());
        assertEquals(cliRoot.toAbsolutePath(), found.resourceRoot());
        assertEquals(Path.of("/tmp/save"), Path.of(ResourceLocator.parseSaveDirArgument(
                        new String[] {"-savedir=/tmp/save"}).orElseThrow()));
    }

    @Test
    void environmentVariableIsSecond() throws IOException {
        Path envRoot = makeRootWithPak("env");
        Path cwdRoot = makeRootWithPak("cwd2");
        var found = ResourceLocator.discover(new String[0], cwdRoot, null, envRoot.toString(), List.of());
        assertEquals(envRoot.toAbsolutePath(), found.resourceRoot());
        assertEquals(ResourceLocator.Discovery.Source.ENVIRONMENT, found.source());
    }

    @Test
    void workingDirectoryBeatsExecutableDirectory() throws IOException {
        Path cwdRoot = makeRootWithPak("cwd3");
        Path exeRoot = makeRootWithPak("exe");
        var found = ResourceLocator.discover(new String[0], cwdRoot, exeRoot, null, List.of());
        assertEquals(cwdRoot.toAbsolutePath(), found.resourceRoot());
        assertEquals(ResourceLocator.Discovery.Source.WORKING_DIRECTORY, found.source());

        var exeOnly = ResourceLocator.discover(new String[0], tmp.resolve("empty-cwd"), exeRoot, null, List.of());
        assertEquals(exeRoot.toAbsolutePath(), exeOnly.resourceRoot());
        assertEquals(ResourceLocator.Discovery.Source.EXECUTABLE_DIRECTORY, exeOnly.source());
    }

    @Test
    void propertiesFolderAloneIsAValidRoot() throws IOException {
        Path propsRoot = makeRootWithProperties("props");
        assertTrue(ResourceLocator.isValidResourceRoot(propsRoot));
        var found = ResourceLocator.discover(new String[0], propsRoot, null, null, List.of());
        assertEquals(propsRoot.toAbsolutePath(), found.resourceRoot());
    }

    @Test
    void steamInstallationIsUsedAsLastResort() throws IOException {
        Path steamRoot = Files.createDirectories(tmp.resolve("steam"));
        Path gameDir = Files.createDirectories(
                steamRoot.resolve("steamapps/common/Plants Vs Zombies/Plants vs. Zombies.app/Contents/Resources"));
        Files.write(gameDir.resolve("main.pak"), new byte[] {9});

        var found = ResourceLocator.discover(
                new String[0], tmp.resolve("no-assets-here"), null, null, List.of(steamRoot));

        assertEquals(gameDir.toAbsolutePath(), found.resourceRoot());
        assertEquals(ResourceLocator.Discovery.Source.STEAM, found.source());
    }

    @Test
    void steamGameInSecondaryLibraryIsFound() throws IOException {
        Path steamRoot = Files.createDirectories(tmp.resolve("steam2"));
        Files.createDirectories(steamRoot.resolve("steamapps"));
        Path secondLibrary = Files.createDirectories(tmp.resolve("library2/steamapps"));
        Files.writeString(
                steamRoot.resolve("steamapps/libraryfolders.vdf"),
                """
                "libraryfolders"
                {
                    "0"
                    {
                        "path"		"%s"
                    }
                    "1"
                    {
                        "path"		"%s"
                    }
                }
                """
                        .formatted(steamRoot.toAbsolutePath(), tmp.resolve("library2").toAbsolutePath()));

        Path gameDir = Files.createDirectories(secondLibrary.resolve("common/Plants vs. Zombies"));
        Files.write(gameDir.resolve("main.pak"), new byte[] {7});

        var found = ResourceLocator.discover(new String[0], null, null, null, List.of(steamRoot));
        assertEquals(gameDir.toAbsolutePath(), found.resourceRoot());
    }

    @Test
    void returnsNoRootWhenNothingMatches() {
        var found = ResourceLocator.discover(new String[0], tmp.resolve("nothing"), null, null, List.of());
        assertNull(found.resourceRoot());
        assertNull(found.source());
    }

    @Test
    void findResourceRootWithinHandlesNestedLayouts() throws IOException {
        Path base = Files.createDirectories(tmp.resolve("base/Plants vs. Zombies.app/Contents/Resources"));
        Files.write(base.resolve("main.pak"), new byte[] {1});
        Path found = ResourceLocator.findResourceRootWithin(tmp.resolve("base"));
        assertEquals(base.toAbsolutePath(), found.toAbsolutePath());
    }

    @Test
    void discoversRealSteamAssetsWhenPresent() {
        Path real = SteamLibraryFinder.findInstalledGameResourceRoot();
        org.junit.jupiter.api.Assumptions.assumeTrue(real != null, "no Steam PvZ installation on this machine");
        assertNotNull(real);
        assertTrue(ResourceLocator.isValidResourceRoot(real));
    }
}
