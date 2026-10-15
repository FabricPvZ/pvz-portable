package cn.enaium.fabricpvz.portable.sexy.pak;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Ported from src/SexyAppFramework/paklib/PakInterface.cpp (PakCollection).
 *
 * <p>Stores the raw, still-obfuscated bytes of one .pak file. The upstream
 * implementation (non-LOW_MEMORY path) reads the whole file and XORs every byte with
 * {@link #PAK_XOR_KEY}; the LOW_MEMORY path does the same on buffered reads. This port
 * memory-maps the file and applies the XOR while copying bytes out, which yields identical
 * results with a much smaller heap footprint.
 */
public final class PakSource implements AutoCloseable {

    /** Upstream: PakInterface.cpp `*aDataPtr++ ^= 0xF7;` */
    public static final int PAK_XOR_KEY = 0xF7;

    private final Path mPath;
    private final FileChannel mChannel;
    private final MappedByteBuffer mBuffer;
    private final byte[] mBytes;
    private final long mSize;
    private boolean mClosed;

    private PakSource(Path path, FileChannel channel, MappedByteBuffer buffer, byte[] bytes, long size) {
        this.mPath = path;
        this.mChannel = channel;
        this.mBuffer = buffer;
        this.mBytes = bytes;
        this.mSize = size;
    }

    public static PakSource open(Path path) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        long size = channel.size();
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
        return new PakSource(path, channel, buffer, null, size);
    }

    public Path path() {
        return mPath;
    }

    public long size() {
        return mSize;
    }

    /** Reads {@code len} bytes at {@code offset}, de-obfuscating them (upstream XOR 0xF7). */
    public void readBytes(long offset, byte[] dest, int destOffset, int len) {
        if (offset < 0 || len < 0 || offset + len > mSize) {
            throw new IndexOutOfBoundsException(
                    "pak read out of bounds: offset=" + offset + " len=" + len + " size=" + mSize);
        }
        if (len == 0) {
            return;
        }
        if (mBuffer != null) {
            MappedByteBuffer view = mBuffer.duplicate();
            view.position((int) offset);
            view.get(dest, destOffset, len);
        } else {
            System.arraycopy(mBytes, (int) offset, dest, destOffset, len);
        }
        for (int i = destOffset, end = destOffset + len; i < end; i++) {
            dest[i] ^= PAK_XOR_KEY;
        }
    }

    /** Reads the whole source (de-obfuscated); upstream keeps the whole file in memory. */
    public byte[] readAllBytes() {
        byte[] out = new byte[(int) mSize];
        readBytes(0, out, 0, out.length);
        return out;
    }

    @Override
    public void close() throws IOException {
        if (mClosed) {
            return;
        }
        mClosed = true;
        if (mChannel != null) {
            mChannel.close();
        }
    }

    /** Case-insensitive existence probe used while mounting paks (upstream uses fcaseopen). */
    public static Path resolveCaseInsensitive(Path path) throws IOException {
        if (Files.exists(path)) {
            return path;
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        String wanted = path.getFileName().toString();
        List<Path> matches = new ArrayList<>();
        try (var stream = Files.list(parent)) {
            stream.filter(p -> p.getFileName().toString().equalsIgnoreCase(wanted)).forEach(matches::add);
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }
}
