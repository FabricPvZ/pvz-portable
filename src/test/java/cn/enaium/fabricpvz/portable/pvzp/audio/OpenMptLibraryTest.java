package cn.enaium.fabricpvz.portable.pvzp.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The native library lookup of {@link OpenMptLibrary}: the {@code <os>-<arch>} directory name of a
 * platform, the probe order of the candidates (the game directory's own native directories before
 * the system ones) and the version line of a loaded library. Nothing here loads a library.
 */
class OpenMptLibraryTest {

    @TempDir
    Path tmp;

    @Test
    void platformDirectoryNamesOfEveryShippedPlatform() {
        assertEquals("windows-x64", OpenMptLibrary.nativePlatform("Windows 11", "amd64"));
        assertEquals("windows-x64", OpenMptLibrary.nativePlatform("Windows 10", "x86_64"));
        assertEquals("windows-arm64", OpenMptLibrary.nativePlatform("Windows 11", "aarch64"));
        assertEquals("macos-x64", OpenMptLibrary.nativePlatform("Mac OS X", "x86_64"));
        assertEquals("macos-arm64", OpenMptLibrary.nativePlatform("Mac OS X", "aarch64"));
        assertEquals("macos-arm64", OpenMptLibrary.nativePlatform("Darwin", "arm64"));
        assertEquals("linux-x64", OpenMptLibrary.nativePlatform("Linux", "amd64"));
        assertEquals("linux-arm64", OpenMptLibrary.nativePlatform("Linux", "aarch64"));
    }

    @Test
    void packagedLibraryNamesAreTheUpstreamFileNames() {
        // Upstream's own file names, which is what the distribution carries. mapLibraryName says
        // openmpt.dll on Windows, so the packaged name is stated per operating system.
        assertEquals("libopenmpt.dll", OpenMptLibrary.packagedLibraryName("windows-x64"));
        assertEquals("libopenmpt.dll", OpenMptLibrary.packagedLibraryName("windows-arm64"));
        assertEquals("libopenmpt.dylib", OpenMptLibrary.packagedLibraryName("macos-arm64"));
        assertEquals("libopenmpt.so", OpenMptLibrary.packagedLibraryName("linux-x64"));
        assertEquals(System.mapLibraryName("openmpt"), OpenMptLibrary.packagedLibraryName(null));
    }

    @Test
    void theLinuxDistributionDirectoriesAndSonameAreCandidates() {
        List<Path> system = OpenMptLibrary.systemCandidates();

        // Fedora/RHEL install into /usr/lib64 under the soname (verified against Fedora 44's
        // libopenmpt-0.8.6-1.fc44.aarch64.rpm: it holds /usr/lib64/libopenmpt.so.0.5.5, and the
        // unversioned libopenmpt.so only comes with libopenmpt-devel). The system lookup of the port
        // used to probe /usr/lib/libopenmpt.so only, so an installed libopenmpt was never found.
        assertTrue(system.contains(Path.of("/usr/lib64/libopenmpt.so")), system.toString());
        assertTrue(system.contains(Path.of("/usr/lib64/libopenmpt.so.0")), system.toString());
        assertTrue(system.contains(Path.of("/lib64/libopenmpt.so.0")), system.toString());
        assertTrue(system.contains(Path.of("/lib/libopenmpt.so")), system.toString());
        // Debian/Ubuntu multiarch directories, whose triplet names the architecture.
        assertTrue(system.contains(Path.of("/usr/lib/aarch64-linux-gnu/libopenmpt.so.0")), system.toString());
        assertTrue(system.contains(Path.of("/usr/lib/x86_64-linux-gnu/libopenmpt.so")), system.toString());

        // Within a directory the unversioned name (the -devel/-dev one) is probed before the soname.
        assertTrue(system.indexOf(Path.of("/usr/lib64/libopenmpt.so"))
                < system.indexOf(Path.of("/usr/lib64/libopenmpt.so.0")));
        // The whole list stays free of duplicates (it is a LinkedHashSet before it is copied out).
        assertEquals(system.size(), Set.copyOf(system).size(), system.toString());
    }

    @Test
    void aDistributionLibraryIsFoundUnderTheSoname() {
        // A Fedora/RHEL host offers the soname only (no libopenmpt-devel installed): the candidate the
        // probe order selects is the soname - the list used to offer nothing a system installation
        // would have matched.
        List<Path> candidates = OpenMptLibrary.candidatePaths(tmp, "linux-arm64");
        Path soname = Path.of("/usr/lib64/libopenmpt.so.0");
        Path packaged = candidates.get(0);

        assertEquals(soname, OpenMptLibrary.firstPresent(candidates, soname::equals));
        // The copy the game directory ships still wins over the distribution one.
        assertEquals(packaged, OpenMptLibrary.firstPresent(candidates,
                path -> path.equals(soname) || path.equals(packaged)));
    }

    @Test
    void unknownSystemOrArchitectureFallsBackToTheSystemCandidates() {
        // Another operating system, a 32-bit JVM, an empty or absent property: no packaged directory
        // of its own, so the candidate list is exactly the system one.
        assertNull(OpenMptLibrary.nativePlatform("SunOS", "amd64"));
        assertNull(OpenMptLibrary.nativePlatform("FreeBSD", "aarch64"));
        assertNull(OpenMptLibrary.nativePlatform("Windows 11", "x86"));
        assertNull(OpenMptLibrary.nativePlatform("Windows 11", null));
        assertNull(OpenMptLibrary.nativePlatform(null, null));

        assertEquals(List.of(), OpenMptLibrary.packagedCandidates(tmp, null));
        assertEquals(List.of(), OpenMptLibrary.packagedCandidates(null, "windows-x64"));
        assertEquals(OpenMptLibrary.systemCandidates(), OpenMptLibrary.candidatePaths(tmp, null));
    }

    @Test
    void thePackagedDirectoriesComeBeforeEverySystemCandidate() {
        String library = OpenMptLibrary.packagedLibraryName("windows-arm64");
        List<Path> candidates = OpenMptLibrary.candidatePaths(tmp, "windows-arm64");

        assertEquals(tmp.resolve("lib/native/windows-arm64").resolve(library), candidates.get(0));
        assertEquals(tmp.resolve("native/windows-arm64").resolve(library), candidates.get(1));

        List<Path> system = OpenMptLibrary.systemCandidates();
        assertTrue(system.size() > 1, "the system candidate list is the platform fallback");
        assertEquals(system, candidates.subList(2, candidates.size()));
        assertEquals(2 + system.size(), candidates.size());

        // No duplicates and no packaged path hiding among the system ones.
        Set<Path> distinct = Set.copyOf(candidates);
        assertEquals(candidates.size(), distinct.size(), candidates.toString());
        Path packaged = tmp.resolve("lib/native/windows-arm64").resolve(library);
        assertTrue(system.stream().noneMatch(path -> path.equals(packaged)));
    }

    @Test
    void aPackagedLibraryWinsOverASystemOne() {
        List<Path> candidates = OpenMptLibrary.candidatePaths(tmp, "windows-x64");
        Path packaged = candidates.get(0);
        Path system = candidates.get(candidates.size() - 1);

        // Both are "there": the packaged one is still the candidate the probe order selects.
        assertEquals(packaged, OpenMptLibrary.firstPresent(List.of(packaged, system), path -> true));
        assertEquals(system, OpenMptLibrary.firstPresent(List.of(system), path -> true));
        assertNull(OpenMptLibrary.firstPresent(List.of(packaged, system), path -> false));
    }

    @Test
    void theFirstRealFileIsThePackagedOne() throws IOException {
        String platform = OpenMptLibrary.nativePlatform(
                System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        Assumptions.assumeTrue(platform != null, "the running platform has no packaged directory");

        // A fake file (the name is what is looked for, not its content): the packaged directory is the
        // one the loader would take, i.e. the game directory wins over anything the system offers.
        Path packaged = Files.createDirectories(tmp.resolve("lib/native").resolve(platform))
                .resolve(OpenMptLibrary.packagedLibraryName(platform));
        Files.write(packaged, new byte[] {0x7f});

        List<Path> candidates = OpenMptLibrary.candidatePaths(tmp, platform);
        assertEquals(packaged, OpenMptLibrary.firstPresent(candidates, Files::isRegularFile));
        // The packaged file is the first real file of the probe order - whatever the system offers
        // (this machine may well have its own copy installed).
        assertEquals(packaged, candidates.stream().filter(Files::isRegularFile).findFirst().orElseThrow());
    }

    @Test
    void theGameDirectoryIsTheLaunchersPropertyAndFallsBackToTheWorkingDirectory() {
        String property = System.getProperty(OpenMptLibrary.GAME_DIRECTORY_PROPERTY, "");
        assertTrue(property.isBlank() || OpenMptLibrary.gameDirectory()
                        .equals(Path.of(property).toAbsolutePath().normalize()),
                "the game directory is the launcher's property when it is set, else the working directory");
        assertNotNull(OpenMptLibrary.gameDirectory());
        assertTrue(OpenMptLibrary.gameDirectory().isAbsolute());
    }

    @Test
    void aLoadedLibraryReportsItsVersion() {
        Assumptions.assumeTrue(OpenMptLibrary.isAvailable(), "libopenmpt is not installed");

        String version = OpenMptLibrary.version();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "version was " + version);
    }
}
