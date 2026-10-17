package cn.enaium.fabricpvz.portable.sexy.pak;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The port's in-memory handle ({@link PakFileHandle#ofBytes}), added for the {@code api-resource}
 * module's byte overrides: it has to behave like a pak member's handle, since it stands in for one.
 * {@link PakFileHandle#ofFile} is the file/root override handle, the engine's own filesystem shape.
 */
class PakFileHandleTest {

    private static final byte[] TEXT =
            "line one\r\nline two\r\nlast line".getBytes(StandardCharsets.ISO_8859_1);

    @TempDir
    Path tmp;

    @Test
    void memoryHandleReportsItsSizeAndIsNotAPakMember() throws IOException {
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            assertTrue(handle.isMemory());
            assertFalse(handle.isPakMember());
            assertEquals(TEXT.length, handle.size());
            assertEquals(0, handle.tell());
            assertFalse(handle.eof());
        }
    }

    @Test
    void memoryHandleSeeksAndClampsLikeAPakMember() throws IOException {
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            handle.seek(5, PakFileHandle.SEEK_SET);
            assertEquals(5, handle.tell());

            byte[] one = new byte[1];
            handle.read(one, 0, 1);
            assertEquals('o', one[0]);

            handle.seek(2, PakFileHandle.SEEK_END);
            assertEquals(handle.size() - 2, handle.tell());

            handle.seek(-100, PakFileHandle.SEEK_CUR);
            assertEquals(0, handle.tell(), "positions are clamped at 0");

            handle.seek(1000, PakFileHandle.SEEK_SET);
            assertEquals(handle.size(), handle.tell(), "positions are clamped at the size");
            assertTrue(handle.eof());
        }
    }

    @Test
    void memoryHandleReadsAndStopsAtEof() throws IOException {
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            byte[] buffer = new byte[TEXT.length + 8];
            int read = handle.read(buffer, 0, buffer.length);
            assertEquals(TEXT.length, read, "a read never returns more bytes than the handle holds");
            assertArrayEquals(TEXT, java.util.Arrays.copyOf(buffer, read));
            assertEquals(0, handle.read(buffer, 0, 1), "at EOF a read returns zero");
        }
    }

    @Test
    void memoryHandleSkipsCarriageReturnsAndHandlesLinesLikeAPakMember() throws IOException {
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            StringBuilder sb = new StringBuilder();
            for (int ch = handle.getc(); ch >= 0; ch = handle.getc()) {
                sb.append((char) ch);
            }
            assertEquals("line one\nline two\nlast line", sb.toString());
        }
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            assertEquals("line one\n", handle.gets(64));
            assertEquals("line two\n", handle.gets(64));
            assertEquals("last", handle.gets(5), "stops after size-1 characters");
            assertEquals(" line", handle.gets(64));
            assertNull(handle.gets(64), "EOF with nothing read returns null");
        }
    }

    @Test
    void memoryHandleUngetcRewindsOneByte() throws IOException {
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            int first = handle.getc();
            handle.ungetc(first);
            assertEquals(first, handle.getc());
        }
    }

    @Test
    void memoryHandleReadsAllBytesFromTheCurrentPosition() throws IOException {
        try (PakFileHandle handle = PakFileHandle.ofBytes(TEXT)) {
            handle.seek(9, PakFileHandle.SEEK_SET);
            assertArrayEquals(java.util.Arrays.copyOfRange(TEXT, 9, TEXT.length), handle.readAllBytes());
        }
    }

    @Test
    void closingAMemoryHandleIsANoOp() throws IOException {
        PakFileHandle handle = PakFileHandle.ofBytes(TEXT);
        handle.close();
        handle.close();
        assertEquals(TEXT.length, handle.size(), "the bytes are still readable after close");
    }

    @Test
    void ofBytesRejectsNull() {
        assertThrows(NullPointerException.class, () -> PakFileHandle.ofBytes(null));
    }

    @Test
    void ofFileReadsTheFileAndCloses() throws IOException {
        Path file = Files.writeString(tmp.resolve("partner.xml"), "<partner/>");

        PakFileHandle handle = PakFileHandle.ofFile(file);
        assertFalse(handle.isMemory());
        assertFalse(handle.isPakMember());
        assertEquals("<partner/>", new String(handle.readAllBytes(), StandardCharsets.UTF_8));
        handle.close();
    }

    @Test
    void ofFileRejectsNull() {
        assertThrows(NullPointerException.class, () -> PakFileHandle.ofFile(null));
    }
}
