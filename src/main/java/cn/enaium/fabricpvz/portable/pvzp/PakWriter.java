package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@code .pak} containers for tests and asset tooling.
 *
 * <p>This is the inverse of {@link cn.enaium.fabricpvz.portable.sexy.pak.PakInterface}: the header table is
 * followed by the member data in table order and the whole file is XOR-obfuscated with 0xF7.
 * Only used by tests/tools, never by the game runtime.
 */
public final class PakWriter {

    private PakWriter() {
    }

    /**
     * Builds a pak image containing the given members (in insertion order).
     *
     * @param members map of member name (may use '\' or '/') to content
     */
    public static byte[] build(java.util.Map<String, byte[]> members) {
        java.io.ByteArrayOutputStream header = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();

        writeLE32(header, 0xBAC04AC0); // magic
        writeLE32(header, 0); // version

        for (var entry : members.entrySet()) {
            String name = entry.getKey().replace('/', '\\');
            byte[] content = entry.getValue();
            header.write(0x01); // flags: not end-of-table
            header.write(name.length());
            header.writeBytes(name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            writeLE32(header, content.length);
            writeLE64(header, 0L); // file time
            body.writeBytes(content);
        }
        header.write(0x80); // end-of-table flag

        byte[] table = header.toByteArray();
        byte[] bodyBytes = body.toByteArray();
        byte[] image = new byte[table.length + bodyBytes.length];
        System.arraycopy(table, 0, image, 0, table.length);
        System.arraycopy(bodyBytes, 0, image, table.length, bodyBytes.length);
        for (int i = 0; i < image.length; i++) {
            image[i] ^= (byte) 0xF7;
        }
        return image;
    }

    /** Writes a pak image to disk. */
    public static Path write(Path target, java.util.Map<String, byte[]> members) throws IOException {
        byte[] image = build(members);
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(target, image);
        return target;
    }

    private static void writeLE32(java.io.ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeLE64(java.io.ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >>> (8 * i)) & 0xFF));
        }
    }
}
