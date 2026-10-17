package cn.enaium.fabricpvz.portable.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Checks the release zip for the native libopenmpt libraries the port loads first
 * ({@code lib/native/<os>-<arch>/} of the game directory, see {@code OpenMptLibrary}).
 *
 * <p>The build downloads the official archive, verifies it and stages the libraries
 * ({@code libopenmptNatives} of {@code build.gradle.kts}); this test asserts what the zip actually
 * carries: the entries of every platform the build ships, with the sha256 the manifest inside the
 * zip records for them, and that the manifest names the pinned archive.
 *
 * <p>The zip is built before the tests run ({@code pvz.test.distribution} is the archive the test
 * task passes in), so a missing property means the test is not running under this build's test task.
 */
class DistributionNativeLibrariesTest {

    /** The libopenmpt release the packaged libraries come from. */
    private static final String VERSION = "0.8.9";
    private static final String ARCHIVE_URL =
            "https://lib.openmpt.org/files/libopenmpt/dev/libopenmpt-" + VERSION
                    + "+release.dev.windows.vs2022.zip";
    private static final String ARCHIVE_SHA256 = "a23e375e576e3a6997ffeeb6cf34488e9020e2a5d8226856b02628b6a87c5e8e";

    /**
     * The platform directories the distribution ships. Windows only: upstream publishes compiled
     * libopenmpt binaries for Windows and points every other operating system at its distribution
     * packages (lib.openmpt.org/libopenmpt/download), so macOS and Linux keep the system lookup.
     */
    private static final Map<String, List<String>> PLATFORMS = Map.of(
            "windows-x64", List.of(
                    "libopenmpt.dll", "openmpt-ogg.dll", "openmpt-mpg123.dll",
                    "openmpt-vorbis.dll", "openmpt-zlib.dll"),
            "windows-arm64", List.of(
                    "libopenmpt.dll", "openmpt-ogg.dll", "openmpt-mpg123.dll",
                    "openmpt-vorbis.dll", "openmpt-zlib.dll"));

    @Test
    void theZipCarriesEveryPackagedNativeLibrary() throws IOException {
        try (ZipFile zip = new ZipFile(distribution().toFile())) {
            Map<String, String> manifest = manifest(zip);
            StringBuilder missing = new StringBuilder();
            for (Map.Entry<String, List<String>> platform : PLATFORMS.entrySet()) {
                for (String library : platform.getValue()) {
                    String entryName = root(zip) + "lib/native/" + platform.getKey() + "/" + library;
                    ZipEntry entry = zip.getEntry(entryName);
                    if (entry == null) {
                        missing.append(entryName).append(' ');
                        continue;
                    }
                    // The manifest of the zip records the sha256 of every file it carries, so the
                    // entry and the identity recorded for it are asserted in one step.
                    assertEquals(manifest.get(platform.getKey() + " " + library), sha256(zip, entry),
                            entryName);
                }
            }
            assertEquals("", missing.toString(), "the release zip must carry these entries");
        }
    }

    @Test
    void theManifestNamesThePinnedArchiveAndItsFiles() throws IOException {
        try (ZipFile zip = new ZipFile(distribution().toFile())) {
            Map<String, String> manifest = manifest(zip);

            assertEquals(VERSION, manifest.get("libopenmpt"));
            assertEquals(ARCHIVE_URL, manifest.get("url"));
            assertEquals(ARCHIVE_SHA256, manifest.get("archive-sha256"));

            // Every file of every platform has a record of its own; the header keys have none.
            assertEquals(
                    PLATFORMS.values().stream().mapToInt(List::size).sum(),
                    manifest.keySet().stream().filter(key -> key.contains(" ")).count(),
                    manifest.toString());
        }
    }

    @Test
    void theZipCarriesTheLibopenmptLicence() throws IOException {
        try (ZipFile zip = new ZipFile(distribution().toFile())) {
            ZipEntry entry = zip.getEntry(root(zip) + "lib/native/libopenmpt-LICENSE.txt");
            assertNotNull(entry, "the BSD-3-Clause licence of the library must ship with it");

            String licence = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(licence.contains("OpenMPT Project Developers"), licence.substring(0, 80));
            assertTrue(licence.contains("Redistribution and use in source and binary forms"),
                    "the licence text is the BSD-3-Clause of the OpenMPT project");
        }
    }

    /** The release zip the test task built and passed in. */
    private static Path distribution() {
        String property = System.getProperty("pvz.test.distribution", "");
        Assumptions.assumeTrue(!property.isBlank(),
                "pvz.test.distribution is not set: run the tests through ./gradlew test");
        Path zip = Path.of(property);
        Assumptions.assumeTrue(Files.isRegularFile(zip), "no release zip at " + zip);
        return zip;
    }

    /** The single root directory of the distribution zip ({@code pvz-portable-<version>/}). */
    private static String root(ZipFile zip) {
        String name = Path.of(zip.getName()).getFileName().toString();
        String root = name.substring(0, name.length() - ".zip".length()) + "/";
        assertNotNull(zip.getEntry(root), "the distribution zip holds one root directory, " + root);
        return root;
    }

    /**
     * The {@code lib/native/libopenmpt-natives.txt} manifest of the zip, as {@code key -> value} for
     * the header lines ({@code libopenmpt}, {@code url}, {@code archive-sha256}) and
     * {@code "<platform> <file>" -> sha256} for the file records.
     */
    private static Map<String, String> manifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry(root(zip) + "lib/native/libopenmpt-natives.txt");
        assertNotNull(entry, "the packaged native directory carries its manifest");
        Map<String, String> records = new LinkedHashMap<>();
        for (String line : new String(zip.getInputStream(entry).readAllBytes(),
                StandardCharsets.UTF_8).split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 3) {
                records.put(parts[0] + " " + parts[1], parts[2]);
            } else if (parts.length == 2) {
                records.put(parts[0], parts[1]);
            } else if (parts.length == 1) {
                int separator = parts[0].indexOf('=');
                if (separator > 0) {
                    records.put(parts[0].substring(0, separator), parts[0].substring(separator + 1));
                }
            }
        }
        return records;
    }

    private static String sha256(ZipFile zip, ZipEntry entry) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
