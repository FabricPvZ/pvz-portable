package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies the {@code 0xDEADFED4} + zlib compiled cache container and its invalidation rules. */
class CacheTest {

    private static final byte[] DEFINITION = "compiled-definition-payload".getBytes(StandardCharsets.ISO_8859_1);

    @TempDir
    Path tmp;

    private static int readLE32(byte[] theData, int theOffset) {
        return (theData[theOffset] & 0xFF)
                | ((theData[theOffset + 1] & 0xFF) << 8)
                | ((theData[theOffset + 2] & 0xFF) << 16)
                | ((theData[theOffset + 3] & 0xFF) << 24);
    }

    @Test
    void headerIsCookiePlusUncompressedSizeFollowedByZlib() {
        byte[] aCompressed = CompiledCache.compress(DEFINITION);
        assertEquals(CompiledCache.COOKIE, readLE32(aCompressed, 0));
        assertEquals(DEFINITION.length, readLE32(aCompressed, 4));
        // zlib stream header: CMF 0x78 (deflate, 32K window), FLG chosen for the 0x78xx pair.
        assertEquals(0x78, aCompressed[8] & 0xFF);
        assertArrayEquals(DEFINITION, CompiledCache.uncompress(aCompressed));
    }

    @Test
    void writesAndReadsCompiledPayload() {
        byte[] aPayload = CompiledCache.wrapPayload(0x12345678, DEFINITION);
        Path aCacheFile = tmp.resolve("cache64/compiled/reanim/test.reanim.compiled");

        assertTrue(CompiledCache.write(aCacheFile, aPayload));
        assertArrayEquals(aPayload, CompiledCache.read(aCacheFile));
    }

    @Test
    void readCompiledChecksTheSchemaHash() {
        byte[] aPayload = CompiledCache.wrapPayload(0x0BADF00D, DEFINITION);
        Path aCacheFile = tmp.resolve(CompiledCache.CACHE_ROOT + "compiled/reanim/test.reanim.compiled");
        assertTrue(CompiledCache.write(aCacheFile, aPayload));

        byte[] aData = CompiledCache.readCompiled(tmp.toString(), "compiled/reanim/test.reanim.compiled", 0x0BADF00D);
        assertArrayEquals(DEFINITION, aData);

        // A changed schema hash means the cached definition is stale.
        assertNull(CompiledCache.readCompiled(tmp.toString(), "compiled/reanim/test.reanim.compiled", 0x12345678));
    }

    @Test
    void readCompiledFallsBackToWorkingDirectoryRelativePath() {
        byte[] aPayload = CompiledCache.wrapPayload(7, DEFINITION);
        Path aRelative = tmp.resolve("relative.compiled");
        assertTrue(CompiledCache.write(aRelative, aPayload));

        // A bogus app-data root forces the cwd-relative fallback (upstream DefinitionReadCompiledFile).
        byte[] aData = CompiledCache.readCompiled(tmp.resolve("empty-appdata").toString(), aRelative.toString(), 7);
        assertArrayEquals(DEFINITION, aData);
    }

    @Test
    void rejectsWrongCookieAndTruncatedData() throws IOException {
        Path aBadCookie = tmp.resolve("bad-cookie.compiled");
        byte[] aBytes = CompiledCache.compress(DEFINITION);
        aBytes[0] ^= 0xFF;
        Files.write(aBadCookie, aBytes);
        assertNull(CompiledCache.read(aBadCookie));

        Path aTruncated = tmp.resolve("truncated.compiled");
        Files.write(aTruncated, new byte[] {0x01, 0x02, 0x03});
        assertNull(CompiledCache.read(aTruncated));

        Path aMissing = tmp.resolve("missing.compiled");
        assertNull(CompiledCache.read(aMissing));
    }

    @Test
    void isCompiledInvalidatesWhenTheSourceChanges() throws IOException {
        // Upstream DefinitionIsCompiled() takes a resource-relative XML path and stats it through the
        // file system, so the source has to live relative to the working directory.
        Path aRelativeDir = Files.createTempDirectory(Path.of("."), "cache-test-");
        try {
            Path aAppData = tmp.resolve("appdata");
            String anXmlRel = aRelativeDir.resolve("test.reanim").toString();
            String aCompiledRel = CompiledCache.getCompiledFilePathFromXmlFilePath(anXmlRel);
            Path aCacheFile = Path.of(CompiledCache.getCompiledCacheFullPath(aAppData.toString(), aCompiledRel));
            assertTrue(CompiledCache.write(aCacheFile, CompiledCache.wrapPayload(1, DEFINITION)));

            Path aSource = Path.of(anXmlRel);
            Files.write(aSource, new byte[] {1});

            Files.setLastModifiedTime(aSource, FileTime.fromMillis(1000));
            Files.setLastModifiedTime(aCacheFile, FileTime.fromMillis(2000));
            assertTrue(CompiledCache.isCompiled(anXmlRel, aAppData.toString()));

            // Touch the source: the cache is now older and must be recompiled.
            Files.setLastModifiedTime(aSource, FileTime.fromMillis(3000));
            assertFalse(CompiledCache.isCompiled(anXmlRel, aAppData.toString()));

            // A missing source cannot be validated (upstream logs and returns false).
            Files.delete(aSource);
            assertFalse(CompiledCache.isCompiled(anXmlRel, aAppData.toString()));
        } finally {
            deleteRecursively(aRelativeDir);
        }
    }

    private static void deleteRecursively(Path thePath) throws IOException {
        if (!Files.exists(thePath)) {
            return;
        }
        try (var aStream = Files.walk(thePath)) {
            for (Path aChild : aStream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(aChild);
            }
        }
    }

    @Test
    void compiledPathHelpersMatchUpstream() {
        assertEquals("compiled/particles/Award.xml.compiled",
                CompiledCache.getCompiledFilePathFromXmlFilePath("particles/Award.xml"));
        assertEquals("/data/cache64/compiled/particles/Award.xml.compiled",
                CompiledCache.getCompiledCacheFullPath("/data", "compiled/particles/Award.xml.compiled"));
    }
}
