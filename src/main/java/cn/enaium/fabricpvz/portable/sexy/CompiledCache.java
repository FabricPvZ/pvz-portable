package cn.enaium.fabricpvz.portable.sexy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Ported from src/PvzpLib/Definition.cpp:534-569, 578-673, 1240-1276 (the {@code *.compiled}
 * definition cache) and docs/research/05-assets-resource-stack.md §3.6.
 *
 * <p>The game caches compiled definition payloads (particles/reanimations) next to the app-data
 * directory as {@code <xml>.compiled} files. The container is:
 * <pre>
 *   uint32 mCookie            == 0xDEADFED4 (little-endian)
 *   uint32 mUncompressedSize
 *   zlib deflate stream of the payload
 * </pre>
 * The payload itself begins with a {@code uint32} schema hash (a CRC32 of the definition map), which
 * must match the hash the caller computed or the cache entry is treated as stale.
 *
 * <p>Cache location and invalidation rules mirror upstream exactly:
 * <ul>
 *   <li>{@link #getCompiledFilePathFromXmlFilePath} builds {@code "compiled/<xml>.compiled"}.</li>
 *   <li>{@link #getCompiledCacheFullPath} prefixes {@code "cache64/"} under the app-data path
 *       (the 64-bit pointer-size branch; the Java runtime is always 64-bit).</li>
 *   <li>{@link #isCompiled} rejects a cache whose modification time is older than the source XML
 *       ({@code sourceTime <= compiledTime} is required), and reports {@code true} immediately when
 *       the compiled path is itself a pak member (upstream {@code IsFileInPakFile}).</li>
 *   <li>{@link #readCompiled} rejects a payload whose stored hash differs from the caller's
 *       expected hash (upstream {@code DefinitionReadCompiledFile}).</li>
 * </ul>
 */
public final class CompiledCache {

    /** Upstream {@code CompressedDefinitionHeader::mCookie} (Definition.cpp:1244). */
    public static final int COOKIE = 0xDEADFED4;

    /** Upstream {@code DefinitionGetCompiledCacheFullPath}: "cache64/" for a 64-bit host. */
    public static final String CACHE_ROOT = "cache64/";

    private static final int HEADER_SIZE = 8;

    private CompiledCache() {
    }

    /** Ported from Definition.cpp:560-563. */
    public static String getCompiledFilePathFromXmlFilePath(String theXMLFilePath) {
        return "compiled/" + theXMLFilePath + ".compiled";
    }

    /** Ported from Definition.cpp:565-569. */
    public static String getCompiledCacheFullPath(String theAppDataPath, String theCompiledFilePath) {
        String base = theAppDataPath == null ? "" : theAppDataPath;
        if (!base.isEmpty() && !base.endsWith("/") && !base.endsWith("\\")) {
            base += "/";
        }
        return base + CACHE_ROOT + theCompiledFilePath;
    }

    /** Ported from Definition.cpp:1240-1248. Prepends the header and zlib-deflates the payload. */
    public static byte[] compress(byte[] theBuffer) {
        byte[] aCompressed = deflate(theBuffer);
        ByteBuffer aOut = ByteBuffer.allocate(HEADER_SIZE + aCompressed.length).order(ByteOrder.LITTLE_ENDIAN);
        aOut.putInt(COOKIE);
        aOut.putInt(theBuffer.length);
        aOut.put(aCompressed);
        return aOut.array();
    }

    /**
     * Ported from Definition.cpp:534-558. Returns {@code null} when the buffer is shorter than the
     * header, when the cookie is wrong, or when the stream does not inflate to the declared size.
     */
    public static byte[] uncompress(byte[] theCompressedBuffer) {
        if (theCompressedBuffer == null || theCompressedBuffer.length < HEADER_SIZE) {
            return null;
        }
        ByteBuffer aBuf = ByteBuffer.wrap(theCompressedBuffer).order(ByteOrder.LITTLE_ENDIAN);
        int aCookie = aBuf.getInt();
        if (aCookie != COOKIE) {
            return null;
        }
        int anUncompressedSize = aBuf.getInt();
        if (anUncompressedSize < 0) {
            return null;
        }
        byte[] aResult = inflate(theCompressedBuffer, HEADER_SIZE, theCompressedBuffer.length - HEADER_SIZE, anUncompressedSize);
        if (aResult == null || aResult.length != anUncompressedSize) {
            return null;
        }
        return aResult;
    }

    /** Ported from Definition.cpp:1250-1274. Writes {@code payload} compressed to {@code theFile}. */
    public static boolean write(Path theFile, byte[] theBuffer) {
        try {
            Path parent = theFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(theFile, compress(theBuffer));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Ported from Definition.cpp:578-600. Reads and verifies a compiled cache file. */
    public static byte[] read(Path theFile) {
        try {
            if (!Files.isRegularFile(theFile)) {
                return null;
            }
            return uncompress(Files.readAllBytes(theFile));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Ported from Definition.cpp:578-637. Resolves the cache under {@code theAppDataPath}, falls
     * back to the path relative to the working directory, and rejects the entry when the stored
     * schema hash does not equal {@code theExpectedHash}. Returns the payload after the hash word,
     * or {@code null} when the cache is missing, corrupt or stale.
     */
    public static byte[] readCompiled(String theAppDataPath, String theCompiledFilePath, int theExpectedHash) {
        Path aFullPath = Path.of(getCompiledCacheFullPath(theAppDataPath, theCompiledFilePath));
        byte[] aPayload = read(aFullPath);
        if (aPayload == null) {
            aPayload = read(Path.of(theCompiledFilePath));
        }
        if (aPayload == null || aPayload.length < 4) {
            return null;
        }
        int aStoredHash = readLE32(aPayload, 0);
        if (aStoredHash != theExpectedHash) {
            return null;
        }
        byte[] aData = new byte[aPayload.length - 4];
        System.arraycopy(aPayload, 4, aData, 0, aData.length);
        return aData;
    }

    /** Ported from Definition.cpp:1258-1268. Builds {@code uint32 hash + payload}. */
    public static byte[] wrapPayload(int theHash, byte[] theData) {
        ByteBuffer aOut = ByteBuffer.allocate(4 + theData.length).order(ByteOrder.LITTLE_ENDIAN);
        aOut.putInt(theHash);
        aOut.put(theData);
        return aOut.array();
    }

    /** The hash word at the start of an unwrapped payload. */
    public static int readPayloadHash(byte[] thePayload) {
        return thePayload.length < 4 ? 0 : readLE32(thePayload, 0);
    }

    /**
     * Ported from Definition.cpp:648-673. {@code true} when the compiled cache may be reused:
     * a pak-resident compiled file is always considered current, otherwise the compiled file's
     * modification time must be at least the source XML's.
     */
    public static boolean isCompiled(String theXMLFilePath, String theAppDataPath) {
        String aCompiledFilePath = getCompiledFilePathFromXmlFilePath(theXMLFilePath);
        if (isFileInPakFile(aCompiledFilePath)) {
            return true;
        }

        long aCompiledTime = fileModTime(getCompiledCacheFullPath(theAppDataPath, aCompiledFilePath));
        if (aCompiledTime == Long.MIN_VALUE) {
            aCompiledTime = fileModTime(aCompiledFilePath);
        }
        if (aCompiledTime == Long.MIN_VALUE) {
            return false;
        }
        long aXmlTime = fileModTime(theXMLFilePath);
        if (aXmlTime == Long.MIN_VALUE) {
            return false;
        }
        return aXmlTime <= aCompiledTime;
    }

    /** Ported from Definition.cpp:637-647. Mirrors upstream {@code IsFileInPakFile}. */
    private static boolean isFileInPakFile(String theFilePath) {
        try (cn.enaium.fabricpvz.portable.sexy.pak.PakFileHandle aHandle =
                cn.enaium.fabricpvz.portable.sexy.pak.PakInterface.getInstance().open(theFilePath, "rb")) {
            return aHandle != null && aHandle.isPakMember();
        } catch (IOException e) {
            return false;
        }
    }

    /** Upstream {@code DefinitionGetFileModTime}; {@code Long.MIN_VALUE} when unavailable. */
    private static long fileModTime(String theFilePath) {
        try {
            Path aPath = Path.of(theFilePath);
            if (!Files.isRegularFile(aPath)) {
                return Long.MIN_VALUE;
            }
            return Files.getLastModifiedTime(aPath).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private static int readLE32(byte[] theData, int theOffset) {
        return (theData[theOffset] & 0xFF)
                | ((theData[theOffset + 1] & 0xFF) << 8)
                | ((theData[theOffset + 2] & 0xFF) << 16)
                | ((theData[theOffset + 3] & 0xFF) << 24);
    }

    private static byte[] deflate(byte[] theData) {
        Deflater aDeflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            aDeflater.setInput(theData);
            aDeflater.finish();
            byte[] aBuffer = new byte[Math.max(64, theData.length + 64)];
            int aLength = 0;
            while (!aDeflater.finished()) {
                if (aLength == aBuffer.length) {
                    aBuffer = java.util.Arrays.copyOf(aBuffer, aBuffer.length * 2);
                }
                aLength += aDeflater.deflate(aBuffer, aLength, aBuffer.length - aLength);
            }
            return java.util.Arrays.copyOf(aBuffer, aLength);
        } finally {
            aDeflater.end();
        }
    }

    private static byte[] inflate(byte[] theData, int theOffset, int theLength, int theUncompressedSize) {
        Inflater anInflater = new Inflater();
        try {
            anInflater.setInput(theData, theOffset, theLength);
            byte[] aOut = new byte[theUncompressedSize];
            int aWritten = 0;
            while (!anInflater.finished() && aWritten < theUncompressedSize) {
                int n = anInflater.inflate(aOut, aWritten, theUncompressedSize - aWritten);
                if (n == 0) {
                    if (anInflater.finished() || anInflater.needsInput() || anInflater.needsDictionary()) {
                        break;
                    }
                }
                aWritten += n;
            }
            if (aWritten != theUncompressedSize) {
                return null;
            }
            return aOut;
        } catch (DataFormatException e) {
            return null;
        } finally {
            anInflater.end();
        }
    }
}
