package cn.enaium.fabricpvz.portable.sexy.pak;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.pvzp.PakWriter;

/** Verifies the ported pak container against the upstream format (PakInterface.cpp). */
class PakInterfaceTest {

    private static final byte[] SOUND_BYTES = "OggS-fake-sound-payload".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] IMAGE_BYTES = new byte[] {0x7F, 0x00, 0x11, (byte) 0xFF, (byte) 0x99};

    @TempDir
    Path tmp;

    private PakInterface mountFixture() throws IOException {
        Map<String, byte[]> members = new LinkedHashMap<>();
        members.put("sounds\\zombie_falling_1.ogg", SOUND_BYTES);
        members.put("images/Plants/Peashooter.png", IMAGE_BYTES);
        members.put("data/Text.txt", "line one\r\nline two\r\nlast line".getBytes(StandardCharsets.ISO_8859_1));
        Path pak = PakWriter.write(tmp.resolve("fixture.pak"), members);
        PakInterface pakInterface = new PakInterface();
        pakInterface.setResourceFolder(tmp.toString());
        assertTrue(pakInterface.addPakFile(pak), "fixture pak must mount");
        return pakInterface;
    }

    @Test
    void normalizesPathsLikeUpstream() throws IOException {
        PakInterface pakInterface = mountFixture();
        assertEquals("SOUNDS/ZOMBIE_FALLING_1.OGG", pakInterface.normalizePakPath("sounds\\zombie_falling_1.ogg"));
        assertEquals("IMAGES/PLANTS/PEASHOOTER.PNG", pakInterface.normalizePakPath("./images/Plants/../Plants/Peashooter.png"));
        assertEquals("A/B", pakInterface.normalizePakPath("a//b"));
        assertEquals("../../UP", pakInterface.normalizePakPath("../../up"));

        Path rooted = tmp.resolve("images/Plants/Peashooter.png");
        assertEquals("IMAGES/PLANTS/PEASHOOTER.PNG", pakInterface.normalizePakPath(rooted.toString()));
    }

    @Test
    void readsMembersAndAppliesXorObfuscation() throws IOException {
        PakInterface pakInterface = mountFixture();
        assertArrayEquals(SOUND_BYTES, pakInterface.requireResource("sounds/zombie_falling_1.ogg"));
        // Lookup is case-insensitive because keys are upper-cased after normalization.
        assertArrayEquals(IMAGE_BYTES, pakInterface.requireResource("IMAGES\\PLANTS\\PEASHOOTER.PNG"));
        assertEquals(4, pakInterface.resourceCount(), "3 members + the pak itself");
    }

    @Test
    void seekTellAndEofMatchUpstream() throws IOException {
        PakInterface pakInterface = mountFixture();
        try (PakFileHandle handle = pakInterface.open("data/Text.txt", "rb")) {
            assertNotNull(handle);
            assertEquals(0, handle.tell());
            assertFalse(handle.eof());

            handle.seek(5, PakFileHandle.SEEK_SET);
            assertEquals(5, handle.tell());

            byte[] one = new byte[1];
            handle.read(one, 0, 1);
            assertEquals('o', one[0]);

            handle.seek(2, PakFileHandle.SEEK_END);
            assertEquals(handle.size() - 2, handle.tell());

            handle.seek(10, PakFileHandle.SEEK_SET);
            handle.seek(10, PakFileHandle.SEEK_CUR);
            assertEquals(20, handle.tell());

            handle.seek(-100, PakFileHandle.SEEK_CUR);
            assertEquals(0, handle.tell(), "positions are clamped at 0");

            handle.seek(1000, PakFileHandle.SEEK_SET);
            assertEquals(handle.size(), handle.tell(), "positions are clamped at the member size");
        }
    }

    @Test
    void getcSkipsCarriageReturns() throws IOException {
        PakInterface pakInterface = mountFixture();
        try (PakFileHandle handle = pakInterface.open("data/Text.txt", "rb")) {
            StringBuilder sb = new StringBuilder();
            for (int ch = handle.getc(); ch >= 0; ch = handle.getc()) {
                sb.append((char) ch);
            }
            assertEquals("line one\nline two\nlast line", sb.toString());
        }
    }

    @Test
    void getsHonoursUpstreamLineSemantics() throws IOException {
        PakInterface pakInterface = mountFixture();
        try (PakFileHandle handle = pakInterface.open("data/Text.txt", "rb")) {
            assertEquals("line one\n", handle.gets(64));
            assertEquals("line two\n", handle.gets(64));
            assertEquals("last", handle.gets(5), "stops after size-1 characters");
            // FGetS stops at '\n' (or the cap) but never skips forward, so the rest of the
            // truncated line is still there (PakInterface.cpp:388-404).
            assertEquals(" line", handle.gets(64));
            assertNull(handle.gets(64), "EOF with nothing read returns null");
        }
    }

    @Test
    void ungetcRewindsOneByte() throws IOException {
        PakInterface pakInterface = mountFixture();
        try (PakFileHandle handle = pakInterface.open("data/Text.txt", "rb")) {
            int first = handle.getc();
            handle.ungetc(first);
            assertEquals(first, handle.getc());
        }
    }

    @Test
    void fallsBackToLooseFilesInResourceFolder() throws IOException {
        PakInterface pakInterface = mountFixture();
        Files.createDirectories(tmp.resolve("properties"));
        Files.writeString(tmp.resolve("properties/partner.xml"), "<partner/>");

        try (PakFileHandle handle = pakInterface.open("properties/partner.xml", "rb")) {
            assertNotNull(handle);
            assertFalse(handle.isPakMember());
            assertEquals("<partner/>", new String(handle.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertTrue(pakInterface.exists("properties/partner.xml"));
    }

    @Test
    void missingResourcesReturnNull() throws IOException {
        PakInterface pakInterface = mountFixture();
        assertNull(pakInterface.open("does/not/exist.png", "rb"));
        assertNull(pakInterface.readResource("does/not/exist.png"));
        assertFalse(pakInterface.exists("does/not/exist.png"));
    }

    @Test
    void rejectsFilesWithoutPakMagic() throws IOException {
        Path notAPak = tmp.resolve("not-a-pak.bin");
        Files.write(notAPak, new byte[64]);
        PakInterface pakInterface = new PakInterface();
        assertFalse(pakInterface.addPakFile(notAPak));
        assertEquals(0, pakInterface.resourceCount());
    }
}
