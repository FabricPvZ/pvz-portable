package cn.enaium.fabricpvz.portable.sexy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ported from src/SexyAppFramework/misc/RegEmu.h:33 (namespace regemu) and
 * src/SexyAppFramework/misc/RegEmu.cpp:36.
 *
 * <p>A simple Windows Registry emulator: a typed key/value store persisted to a {@code registry.regemu}
 * file. Keys map to value maps, each value carrying a type tag
 * ({@link #REGEMU_SZ}, {@link #REGEMU_DWORD}, {@link #REGEMU_BINARY}, ...), a length and raw bytes.
 *
 * <p>The on-disk format is byte-for-byte the upstream one (all integers little-endian):
 * <pre>
 *   "REGEMU"                     6 bytes
 *   version : uint16             1 (written but never validated on load)
 *   numKeys : uint32
 *   per key:
 *     keyNameLen  : uint32       byte length of the name including the trailing NUL
 *     keyName                  raw bytes + NUL
 *     numValues   : uint32
 *     per value:
 *       valueNameLen : uint32    byte length of the name including the trailing NUL
 *       valueName               raw bytes + NUL
 *       type    : uint32
 *       length  : uint32
 *       value                   length raw bytes
 * </pre>
 *
 * <p>PORT-NOTE: upstream writes the host representation of the integers, which is little-endian on
 * every platform the project builds on; this port writes little-endian explicitly. Keys are kept in
 * unsigned byte order (like {@code std::map<std::string, ...>}) via {@link #BYTE_ORDER} so the file
 * layout does not depend on the JVM locale. Names are encoded as UTF-8 (upstream writes the raw
 * {@code std::string} bytes, which are UTF-8 in practice).
 *
 * <p>The typed {@code registryReadXxx}/{@code registryWriteXxx} helpers are the exact behavior of
 * {@code SexyAppBase::RegistryReadString/Integer/Boolean/Data} and
 * {@code SexyAppBase::RegistryWriteString/Integer/Boolean/Data}
 * (src/SexyAppFramework/SexyAppBase.cpp:1065-1084 and :1268-1320); upstream keeps them in
 * {@code SexyAppBase} where the {@code "SOFTWARE\\"} + {@code mRegKey} prefix and the {@code '\\'}
 * value-name splitting happen. They are exposed here on the raw store as well, without that prefix
 * handling.
 */
public final class RegEmu {
    private RegEmu() {
    }

    /** Ported from src/SexyAppFramework/misc/RegEmu.h:37-46 (exact values, including the aliases). */
    public static final int REGEMU_NONE = 0;
    public static final int REGEMU_SZ = 1;
    public static final int REGEMU_EXPAND_SZ = 2;
    public static final int REGEMU_BINARY = 3;
    public static final int REGEMU_DWORD = 4;
    public static final int REGEMU_DWORD_LITTLE_ENDIAN = 4;
    public static final int REGEMU_DWORD_BIG_ENDIAN = 5;
    public static final int REGEMU_MULTI_SZ = 7;
    public static final int REGEMU_QWORD = 11;
    public static final int REGEMU_QWORD_LITTLE_ENDIAN = 11;

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:36. */
    public static final int REGEMU_VERSION = 1;

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:38 ({@code struct RegValue}). */
    public static final class RegValue {
        public int mType;
        public int mLength;
        public byte[] mValue = new byte[0];
    }

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:44. */
    private static final Comparator<String> BYTE_ORDER = RegEmu::compareUtf8;

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:45 ({@code static RegContents registry;}). */
    private static final TreeMap<String, TreeMap<String, RegValue>> registry = new TreeMap<>(BYTE_ORDER);

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:46. */
    private static String currFile = "";

    // ------------------------------------------------------------------
    // Raw API (RegEmu.h:49-53)
    // ------------------------------------------------------------------

    /**
     * Ported from src/SexyAppFramework/misc/RegEmu.cpp:94.
     *
     * <p>Clears the in-memory store and loads the file; a missing file or an invalid/truncated file
     * leaves the (possibly partially filled) store as-is, exactly like upstream.
     */
    public static void setRegFile(String fileName) {
        currFile = fileName == null ? "" : fileName;
        registry.clear();

        byte[] aData;
        try {
            aData = Files.readAllBytes(Paths.get(currFile));
        } catch (IOException | RuntimeException e) {
            Common.logInfoLn("RegEmu: Can't read '{}': File does not exist", currFile);
            return;
        }

        if (aData.length < 6
                || aData[0] != 'R' || aData[1] != 'E' || aData[2] != 'G'
                || aData[3] != 'E' || aData[4] != 'M' || aData[5] != 'U') {
            Common.logInfoLn("RegEmu: Can't read '{}': Invalid header", currFile);
            return;
        }

        int aPos = 6;

        // version: read and ignored by upstream
        if (aPos + 2 > aData.length) {
            return;
        }
        aPos += 2;

        if (aPos + 4 > aData.length) {
            return;
        }
        long aNumKeys = readU32(aData, aPos);
        aPos += 4;

        for (long i = 0; i < aNumKeys; i++) {
            if (aPos + 4 > aData.length) {
                return;
            }
            long aKeyNameLen = readU32(aData, aPos);
            aPos += 4;

            if (aPos + aKeyNameLen > aData.length) {
                return;
            }
            String aKeyName = cString(aData, aPos, (int) aKeyNameLen);
            aPos += (int) aKeyNameLen;

            TreeMap<String, RegValue> aValueMap = new TreeMap<>(BYTE_ORDER);
            registry.put(aKeyName, aValueMap);

            if (aPos + 4 > aData.length) {
                return;
            }
            long aNumValues = readU32(aData, aPos);
            aPos += 4;

            for (long j = 0; j < aNumValues; j++) {
                RegValue aValue = new RegValue();

                if (aPos + 4 > aData.length) {
                    return;
                }
                long aValueNameLen = readU32(aData, aPos);
                aPos += 4;

                if (aPos + aValueNameLen > aData.length) {
                    return;
                }
                String aValueName = cString(aData, aPos, (int) aValueNameLen);
                aPos += (int) aValueNameLen;

                if (aPos + 4 > aData.length) {
                    return;
                }
                aValue.mType = (int) readU32(aData, aPos);
                aPos += 4;

                if (aPos + 4 > aData.length) {
                    return;
                }
                aValue.mLength = (int) readU32(aData, aPos);
                aPos += 4;

                int aLength = aValue.mLength;
                if (aLength < 0 || aPos + aLength > aData.length) {
                    return;
                }
                aValue.mValue = Arrays.copyOfRange(aData, aPos, aPos + aLength);
                aPos += aLength;

                aValueMap.put(aValueName, aValue);
            }
        }

        Common.logInfoLn("RegEmu: Loaded from '{}': {} total key(s)", currFile, registry.size());
    }

    /**
     * Ported from src/SexyAppFramework/misc/RegEmu.cpp:153.
     *
     * <p>Upstream takes caller-owned {@code type}/{@code value}/{@code length} pointers; the copied
     * value is returned through {@code theOutValue} instead (its {@code mValue} is exactly
     * {@code mLength} bytes).
     */
    public static boolean registryRead(String keyName, String valueName, RegValue theOutValue) {
        TreeMap<String, RegValue> aValueMap = registry.get(keyName);
        if (aValueMap == null) {
            Common.logInfoLn("RegEmu: Key '{}' does not exist", keyName);
            return false;
        }
        RegValue aValue = aValueMap.get(valueName);
        if (aValue == null) {
            Common.logInfoLn("RegEmu: Value '{}' does not exist", valueName);
            return false;
        }

        theOutValue.mType = aValue.mType;
        theOutValue.mLength = aValue.mLength;
        theOutValue.mValue = Arrays.copyOf(aValue.mValue, aValue.mLength);
        return true;
    }

    /**
     * Ported from src/SexyAppFramework/misc/RegEmu.cpp:172.
     *
     * <p>Creates the key if needed, stores {@code length} bytes and immediately rewrites the file.
     */
    public static boolean registryWrite(String keyName, String valueName, int type, byte[] value, int length) {
        TreeMap<String, RegValue> aValueMap = registry.get(keyName);
        if (aValueMap == null) {
            aValueMap = new TreeMap<>(BYTE_ORDER);
            registry.put(keyName, aValueMap);
        }

        RegValue aRegValue = new RegValue();
        aRegValue.mType = type;
        aRegValue.mLength = length;
        aRegValue.mValue = Arrays.copyOf(value, length);

        aValueMap.put(valueName, aRegValue);

        saveToFile();

        return true;
    }

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:190. */
    public static boolean registryEraseKey(String keyName) {
        if (!registry.containsKey(keyName)) {
            return false;
        }

        registry.remove(keyName);
        Common.logInfoLn("RegEmu: Erased key '{}'", keyName);

        saveToFile();

        return true;
    }

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:203. */
    public static boolean registryEraseValue(String keyName, String valueName) {
        TreeMap<String, RegValue> aValueMap = registry.get(keyName);
        if (aValueMap == null || !aValueMap.containsKey(valueName)) {
            return false;
        }

        aValueMap.remove(valueName);
        Common.logInfoLn("RegEmu: Erased value '{}' from key '{}'", valueName, keyName);

        saveToFile();

        return true;
    }

    // ------------------------------------------------------------------
    // Typed helpers (SexyAppBase.cpp:1065-1084, :1268-1320)
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1065. */
    public static boolean registryWriteString(String keyName, String valueName, String theString) {
        byte[] aBytes = theString.getBytes(StandardCharsets.UTF_8);
        return registryWrite(keyName, valueName, REGEMU_SZ, aBytes, aBytes.length);
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1070 (native 4-byte int, little-endian). */
    public static boolean registryWriteInteger(String keyName, String valueName, int theValue) {
        return registryWrite(keyName, valueName, REGEMU_DWORD,
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(theValue).array(), 4);
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1075 ({@code 1}/{@code 0} as a DWORD). */
    public static boolean registryWriteBoolean(String keyName, String valueName, boolean theValue) {
        return registryWriteInteger(keyName, valueName, theValue ? 1 : 0);
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1081. */
    public static boolean registryWriteData(String keyName, String valueName, byte[] theValue) {
        return registryWrite(keyName, valueName, REGEMU_BINARY, theValue, theValue.length);
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1268. Returns {@code null} like the upstream {@code false}. */
    public static String registryReadString(String keyName, String valueName) {
        RegValue aValue = new RegValue();
        if (!registryRead(keyName, valueName, aValue)) {
            return null;
        }
        if (aValue.mType != REGEMU_SZ) {
            return null;
        }
        return new String(aValue.mValue, 0, aValue.mLength, StandardCharsets.UTF_8);
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1286. Returns {@code null} like the upstream {@code false}. */
    public static Integer registryReadInteger(String keyName, String valueName) {
        RegValue aValue = new RegValue();
        if (!registryRead(keyName, valueName, aValue)) {
            return null;
        }
        if (aValue.mType != REGEMU_DWORD || aValue.mLength < 4) {
            return null;
        }
        return ByteBuffer.wrap(aValue.mValue).order(ByteOrder.LITTLE_ENDIAN).getInt(0);
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1301. */
    public static Boolean registryReadBoolean(String keyName, String valueName) {
        Integer aValue = registryReadInteger(keyName, valueName);
        if (aValue == null) {
            return null;
        }
        return aValue != 0;
    }

    /** Ported from src/SexyAppFramework/SexyAppBase.cpp:1311. Returns {@code null} on a missing/wrong-typed value. */
    public static byte[] registryReadData(String keyName, String valueName) {
        RegValue aValue = new RegValue();
        if (!registryRead(keyName, valueName, aValue)) {
            return null;
        }
        if (aValue.mType != REGEMU_BINARY) {
            return null;
        }
        return Arrays.copyOf(aValue.mValue, aValue.mLength);
    }

    // ------------------------------------------------------------------
    // Persistence (RegEmu.cpp:48-92)
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/misc/RegEmu.cpp:48 ({@code static void SaveToFile()}). */
    private static void saveToFile() {
        if (currFile.isEmpty()) {
            Common.logInfoLn("RegEmu: Filename not specified, can't save");
            return;
        }

        try (OutputStream aFile = new FileOutputStream(currFile)) {
            aFile.write("REGEMU".getBytes(StandardCharsets.US_ASCII), 0, 6);

            writeU16(aFile, REGEMU_VERSION);
            writeU32(aFile, registry.size());

            for (Map.Entry<String, TreeMap<String, RegValue>> aKeyPair : registry.entrySet()) {
                byte[] aKeyNameBytes = aKeyPair.getKey().getBytes(StandardCharsets.UTF_8);
                writeU32(aFile, aKeyNameBytes.length + 1);
                aFile.write(aKeyNameBytes);
                aFile.write(0);

                writeU32(aFile, aKeyPair.getValue().size());

                for (Map.Entry<String, RegValue> aValuePair : aKeyPair.getValue().entrySet()) {
                    byte[] aValueNameBytes = aValuePair.getKey().getBytes(StandardCharsets.UTF_8);
                    writeU32(aFile, aValueNameBytes.length + 1);
                    aFile.write(aValueNameBytes);
                    aFile.write(0);

                    RegValue aValue = aValuePair.getValue();
                    writeU32(aFile, aValue.mType);
                    writeU32(aFile, aValue.mLength);
                    aFile.write(aValue.mValue, 0, aValue.mLength);
                }
            }
        } catch (IOException e) {
            Common.logInfoLn("RegEmu: Couldn't open '{}' for writing", currFile);
        }
    }

    private static void writeU16(OutputStream theFile, int theValue) throws IOException {
        theFile.write(theValue & 0xFF);
        theFile.write((theValue >>> 8) & 0xFF);
    }

    private static void writeU32(OutputStream theFile, long theValue) throws IOException {
        theFile.write((int) (theValue & 0xFF));
        theFile.write((int) ((theValue >>> 8) & 0xFF));
        theFile.write((int) ((theValue >>> 16) & 0xFF));
        theFile.write((int) ((theValue >>> 24) & 0xFF));
    }

    private static long readU32(byte[] theData, int thePos) {
        return (theData[thePos] & 0xFFL)
                | ((theData[thePos + 1] & 0xFFL) << 8)
                | ((theData[thePos + 2] & 0xFFL) << 16)
                | ((theData[thePos + 3] & 0xFFL) << 24);
    }

    /** Reads a NUL-terminated name of at most {@code theLen} bytes (upstream {@code std::string(char*)}). */
    private static String cString(byte[] theData, int thePos, int theLen) {
        int anEnd = thePos;
        int aLimit = Math.min(thePos + theLen, theData.length);
        while (anEnd < aLimit && theData[anEnd] != 0) {
            anEnd++;
        }
        return new String(theData, thePos, anEnd - thePos, StandardCharsets.UTF_8);
    }

    /** Unsigned byte-wise comparison, matching {@code std::less<std::string>}. */
    private static int compareUtf8(String s1, String s2) {
        byte[] aBytes1 = s1.getBytes(StandardCharsets.UTF_8);
        byte[] aBytes2 = s2.getBytes(StandardCharsets.UTF_8);
        int aLength = Math.min(aBytes1.length, aBytes2.length);
        for (int i = 0; i < aLength; i++) {
            int c1 = aBytes1[i] & 0xFF;
            int c2 = aBytes2[i] & 0xFF;
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return aBytes1.length - aBytes2.length;
    }
}
