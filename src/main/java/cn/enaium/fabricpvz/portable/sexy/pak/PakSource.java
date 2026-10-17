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

    /**
     * Resolves a file-system path the way upstream's {@code fcaseopen} does: <em>every</em> component
     * that is not there as spelled is folded to the spelling the directory actually holds, so
     * {@code Properties/ZombatarTOS.txt} finds {@code properties/ZombatarTOS.txt} on a file system
     * that distinguishes case (upstream {@code fcaseopen.c:68-130} walks the path component by
     * component with {@code strcasecmp}).
     *
     * <p>This is the one funnel for resources that live as plain files rather than as pak members
     * ({@link PakInterface#open}, {@link PakInterface#exists}, {@link PakInterface#addPakFile},
     * {@code DataReader.openFile}, the legacy font descriptor read), which is why the fold has to
     * cover directories too: the retail layout spells the resource directory {@code properties} in
     * lower case while the game asks for {@code Properties/...}, and macOS/Windows hide that
     * mismatch by not distinguishing case at all.
     *
     * <p>PORT-NOTE: upstream keeps an unresolved component verbatim and lets the open fail; this
     * returns {@code null} instead, and refuses a component that matches more than one entry (upstream
     * takes the first {@code readdir} match, i.e. an arbitrary one of two spellings of the same name).
     *
     * @return {@code path} itself when it is there as spelled (the only case on a case-insensitive
     *         file system), the path with its components folded when every one of them resolves, or
     *         {@code null} when some component does not.
     */
    public static Path resolveCaseInsensitive(Path path) throws IOException {
        return resolveCaseInsensitive(path, PakSource::listDirectory);
    }

    /**
     * {@link #resolveCaseInsensitive(Path)} against a caller-supplied directory listing: the seam that
     * makes the fold testable on a host whose file system is case-insensitive (a test can then drive
     * the fold with a listing that only holds the exact spellings).
     */
    static Path resolveCaseInsensitive(Path path, DirectoryListing listing) throws IOException {
        if (Files.exists(path)) {
            return path;
        }
        Path name = path.getFileName();
        if (name == null) {
            return null; // a root: there is nothing left to fold
        }
        Path parent = path.getParent();
        if (parent == null) {
            parent = Path.of(""); // a single relative name: the working directory
        }
        Path resolvedParent = Files.exists(parent) ? parent : resolveCaseInsensitive(parent, listing);
        if (resolvedParent == null) {
            return null;
        }
        List<Path> entries = listing.list(resolvedParent);
        if (entries == null) {
            return null; // not a directory
        }
        String wanted = name.toString();
        List<Path> matches = new ArrayList<>();
        for (Path entry : entries) {
            if (entry.getFileName().toString().equalsIgnoreCase(wanted)) {
                matches.add(entry);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** Entries of {@code directory} in any order, or {@code null} when it is not a directory. */
    @FunctionalInterface
    interface DirectoryListing {
        List<Path> list(Path directory) throws IOException;
    }

    /** {@link DirectoryListing} over the real file system. */
    private static List<Path> listDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var stream = Files.list(directory)) {
            return stream.toList();
        }
    }
}
