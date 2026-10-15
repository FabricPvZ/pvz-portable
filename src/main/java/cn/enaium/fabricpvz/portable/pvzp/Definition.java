/*
 * Ported from src/PvzpLib/Definition.h (class DefMap/DefField/DefSymbol/DefinitionArrayDef/
 * CompressedDefinitionHeader/DefLoadResPath consumers) and src/PvzpLib/Definition.cpp (the whole
 * definition engine: XML field readers, compiled-cache read/write, schema hash, sizes, frees).
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import cn.enaium.fabricpvz.portable.lawn.PvzpCurves;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.PerfTimer;
import cn.enaium.fabricpvz.portable.sexy.SexyVector2;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.pak.PakFileHandle;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.sexy.parsers.XMLElement;
import cn.enaium.fabricpvz.portable.sexy.parsers.XMLParser;

/**
 * Ported from src/PvzpLib/Definition.h and src/PvzpLib/Definition.cpp:1-1372.
 *
 * <p>The definition/format layer of PvzpLib: it turns a definition class ({@code _DefClass},
 * described by a {@link DefMap}) into data read either from the XML that ships in the pak or from the
 * compiled definition cache the game writes next to the app data.
 *
 * <p><b>Compiled cache format</b> (Definition.cpp:534-558, 1240-1274 and the doc
 * docs/research/05-assets-resource-stack.md §3.6): little-endian throughout,
 * {@code CompressedDefinitionHeader{mCookie = 0xDEADFED4, mUncompressedSize}} followed by a zlib
 * ({@code compress()}-compatible) stream whose payload is
 * {@code uint32 defHash || raw struct image (mDefSize bytes) || pointer/string records}. The records
 * follow the {@link DefMap} field order: a string/image/font record is
 * {@code uint32 length || bytes}, an array record is {@code uint32 elementDefSize || count * element
 * image || per-element nested records}, and a float-track record is
 * {@code uint32 nodeCount || nodeCount * FloatParameterTrackNode images}. The struct image holds every
 * non-pointer member at its declared {@link DefField#mFieldOffset} (a "pointer slot" is written as
 * zero: upstream copies a live heap address there, which is not reproducible).
 *
 * <p><b>Java mapping.</b> Upstream addresses definition members with
 * {@code *(T*)((char*)theDefinition + aField->mFieldOffset)}; this port keeps {@code mFieldOffset} for
 * the flat struct image and the schema hash, and reads/writes the member through the public Java field
 * named by {@link DefField#mMemberName} (docs/API-CONTRACTS.md §6 keeps the upstream {@code m}
 * prefix). Raw pointers become arrays ({@link DefinitionArrayDef}, {@link FloatParameterTrack}), and
 * the resource manager/error box reach the app layer through
 * {@link DefinitionResourceResolver} (upstream used the {@code gSexyAppBase} global directly).
 */
public final class Definition {
    /**
     * Ported from src/PvzpLib/Definition.cpp:545, 1243 ({@code aHeader->mCookie != 0xDEADFED4L}).
     */
    public static final int COMPRESSION_COOKIE = 0xDEADFED4;

    /**
     * Ported from src/PvzpLib/Definition.cpp:630 ({@code (sizeof(void*) == 8) ? "cache64/" : "cache32/"});
     * a 64-bit JVM makes this constant.
     */
    public static final String COMPILED_CACHE_ROOT = "cache64/";

    /**
     * Ported from the {@code PVZ_DEBUG} switch of Definition.cpp:1294-1298: in a debug build the
     * loader insists on an up-to-date cache, in a release build it always tries the cached file first.
     * Java has no compile-time switch, so the release behaviour is the default and a debug build sets
     * this to {@code true}.
     */
    public static boolean gRequireCompiledUpToDate = false;

    // ------------------------------------------------------------------
    // Symbol tables (Definition.cpp:78-93, 172, 943-955)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/Definition.cpp:78-80 ({@code gTrailFlagDefSymbols}). */
    public static final DefSymbol[] gTrailFlagDefSymbols = {
            new DefSymbol(0, "Loops"),
            new DefSymbol(-1, null)
    };

    /** Ported from src/PvzpLib/Definition.cpp:82-85 ({@code gParticleFlagSymbols}). */
    public static final DefSymbol[] gParticleFlagSymbols = {
            new DefSymbol(0, "RandomLaunchSpin"),
            new DefSymbol(1, "AlignLaunchSpin"),
            new DefSymbol(2, "AlignToPixel"),
            new DefSymbol(4, "ParticleLoops"),
            new DefSymbol(3, "SystemLoops"),
            new DefSymbol(5, "ParticlesDontFollow"),
            new DefSymbol(6, "RandomStartTime"),
            new DefSymbol(7, "DieIfOverloaded"),
            new DefSymbol(8, "Additive"),
            new DefSymbol(9, "FullScreen"),
            new DefSymbol(10, "SoftwareOnly"),
            new DefSymbol(11, "HardwareOnly"),
            new DefSymbol(-1, null)
    };

    /** Ported from src/PvzpLib/Definition.cpp:86-89 ({@code gEmitterTypeSymbols}). */
    public static final DefSymbol[] gEmitterTypeSymbols = {
            new DefSymbol(0, "Circle"),
            new DefSymbol(1, "Box"),
            new DefSymbol(2, "BoxPath"),
            new DefSymbol(3, "CirclePath"),
            new DefSymbol(4, "CircleEvenSpacing"),
            new DefSymbol(-1, null)
    };

    /** Ported from src/PvzpLib/Definition.cpp:90-93 ({@code gParticleTypeSymbols}). */
    public static final DefSymbol[] gParticleTypeSymbols = {
            new DefSymbol(1, "Friction"),
            new DefSymbol(2, "Acceleration"),
            new DefSymbol(3, "Attractor"),
            new DefSymbol(4, "MaxVelocity"),
            new DefSymbol(5, "Velocity"),
            new DefSymbol(6, "Position"),
            new DefSymbol(7, "SystemPosition"),
            new DefSymbol(8, "GroundConstraint"),
            new DefSymbol(9, "Shake"),
            new DefSymbol(10, "Circle"),
            new DefSymbol(11, "Away"),
            new DefSymbol(-1, null)
    };

    /**
     * Ported from src/PvzpLib/Definition.cpp:943-955 ({@code gDefTrackEaseSymbols}). Upstream iterates
     * this table by its array length and the table has no {@code nullptr} terminator; the Java port
     * appends one so the iteration is bounds-safe.
     */
    public static final DefSymbol[] gDefTrackEaseSymbols = {
            new DefSymbol(PvzpCurves.CURVE_EASE_IN_OUT_WEAK, "EaseInOutWeak"),
            new DefSymbol(PvzpCurves.CURVE_FAST_IN_OUT_WEAK, "FastInOutWeak"),
            new DefSymbol(PvzpCurves.CURVE_EASE_IN_OUT, "EaseInOut"),
            new DefSymbol(PvzpCurves.CURVE_FAST_IN_OUT, "FastInOut"),
            new DefSymbol(PvzpCurves.CURVE_EASE_IN, "EaseIn"),
            new DefSymbol(PvzpCurves.CURVE_EASE_OUT, "EaseOut"),
            new DefSymbol(PvzpCurves.CURVE_EASE_SIN_WAVE, "EaseSinWave"),
            new DefSymbol(PvzpCurves.CURVE_BOUNCE_FAST_MIDDLE, "BounceFastMiddle"),
            new DefSymbol(PvzpCurves.CURVE_BOUNCE_SLOW_MIDDLE, "BounceSlowMiddle"),
            new DefSymbol(PvzpCurves.CURVE_BOUNCE, "Bounce"),
            new DefSymbol(PvzpCurves.CURVE_SIN_WAVE, "SinWave"),
            new DefSymbol(PvzpCurves.CURVE_LINEAR, "Linear"),
            new DefSymbol(-1, null)
    };

    /** Ported from src/PvzpLib/Definition.cpp:172 ({@code gDefLoadResPaths}). */
    public static final DefLoadResPath[] gDefLoadResPaths = {
            new DefLoadResPath("IMAGE_", ""),
            new DefLoadResPath("IMAGE_", "particles/"),
            new DefLoadResPath("IMAGE_REANIM_", "reanim/"),
            new DefLoadResPath("IMAGE_REANIM_", "images/")
    };

    // ------------------------------------------------------------------
    // Resource seam (upstream reached gSexyAppBase directly)
    // ------------------------------------------------------------------

    /**
     * Ported from the {@code gSexyAppBase->mResourceManager}/{@code gSexyAppBase->GetSharedImage} uses
     * of Definition.cpp:317-360, 477-508 and the {@code PvzpFindImagePath}/{@code PvzpFindFontPath}
     * uses of Definition.cpp:455-470. The application layer installs a resolver backed by its
     * {@code PvzpResourceManager} ({@code PvzpResourceManager.findImagePath/findFontPath} are the
     * upstream functions); with no resolver installed, named images/fonts cannot be bound and
     * {@link #loadImage(Image[], String)}/{@link #loadFont(Font[], String)} report failure.
     */
    public interface DefinitionResourceResolver {
        /** Upstream {@code mResourceManager->LoadImage(theName)}. */
        default Image loadImage(String theName) {
            return null;
        }

        /** Upstream {@code gSexyAppBase->GetSharedImage(thePathToTry)}. */
        default Image getSharedImage(String thePathToTry) {
            return null;
        }

        /** Upstream {@code PvzpAddImageToMap(&aImageRef, theName)}. */
        default void addImageToMap(Image theImage, String theName) {
        }

        /** Upstream {@code PvzpMarkImageForSanding((Image*)aImageRef)}. */
        default void markImageForSanding(Image theImage) {
            PvzpCommon.pvzpMarkImageForSanding(theImage);
        }

        /** Upstream {@code mResourceManager->LoadFont(theName)}. */
        default Font loadFont(String theName) {
            return null;
        }

        /** Upstream {@code PvzpFindImagePath}(Definition.cpp:455-458). */
        default String findImagePath(Image theImage) {
            return theImage != null && theImage.mFilePath != null ? theImage.mFilePath : "";
        }

        /** Upstream {@code PvzpFindFontPath}(Definition.cpp:463-468). */
        default String findFontPath(Font theFont) {
            return "";
        }
    }

    /** Upstream {@code gSexyAppBase}: installed by the app layer, may stay null in headless use. */
    public static volatile DefinitionResourceResolver gDefResourceResolver;

    private Definition() {
    }

    // ------------------------------------------------------------------
    // Paths and cache freshness (Definition.cpp:560-647)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/Definition.cpp:560-563 ({@code DefinitionGetCompiledFilePathFromXMLFilePath}). */
    public static String getCompiledFilePathFromXMLFilePath(String theXMLFilePath) {
        return "compiled/" + theXMLFilePath + ".compiled";
    }

    /** Ported from src/PvzpLib/Definition.cpp:565-569 ({@code DefinitionGetCompiledCacheFullPath}). */
    public static String getCompiledCacheFullPath(String theCompiledFilePath) {
        return Common.getAppDataPath(COMPILED_CACHE_ROOT + theCompiledFilePath);
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:637-647 ({@code IsFileInPakFile}): files opened from the
     * pak have no backing file, which the framework exposes as {@link PakFileHandle#isPakMember()}.
     */
    public static boolean isFileInPakFile(String theFilePath) {
        try (PakFileHandle aFile = PakInterface.getInstance().open(theFilePath, "rb")) {
            return aFile != null && aFile.isPakMember();
        } catch (IOException anException) {
            return false;
        }
    }

    /** Ported from src/PvzpLib/Definition.cpp:571-576 ({@code DefinitionGetFileModTime}). */
    private static FileTime getFileModTime(String theFilePath) {
        try {
            return Files.getLastModifiedTime(Path.of(theFilePath));
        } catch (IOException anException) {
            return null;
        }
    }

    /** Ported from src/PvzpLib/Definition.cpp:648-673 ({@code DefinitionIsCompiled}). */
    public static boolean isCompiled(String theXMLFilePath) {
        String aCompiledFilePath = getCompiledFilePathFromXMLFilePath(theXMLFilePath);
        if (isFileInPakFile(aCompiledFilePath)) {
            return true;
        }

        String aFullCompiledPath = getCompiledCacheFullPath(aCompiledFilePath);
        FileTime aCompiledFileTime = getFileModTime(aFullCompiledPath);
        if (aCompiledFileTime == null) {
            aCompiledFileTime = getFileModTime(aCompiledFilePath);
            if (aCompiledFileTime == null) {
                return false;
            }
        }

        FileTime aXMLFileTime = getFileModTime(theXMLFilePath);
        if (aXMLFileTime == null) {
            PvzpDebug.pvzpLogLn("Can't find source file to compile '{}'", theXMLFilePath);
            return false;
        }

        return aXMLFileTime.compareTo(aCompiledFileTime) <= 0;
    }

    // ------------------------------------------------------------------
    // Loading entry points (Definition.cpp:1060-1330)
    // ------------------------------------------------------------------

    /**
     * Ported from the log helper of src/PvzpLib/Definition.h:126-132 ({@code DefinitionXmlError}).
     */
    static void xmlError(XMLParser theXmlParser, String theMessage) {
        PvzpDebug.pvzpLogLn("{}({}): XML Definition Error: {}", theXmlParser.getFileName(), theXmlParser.getCurrentLineNum(),
                theMessage);
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1281-1288 ({@code DefinitionLoadXML}), i.e. the
     * cache-first {@code DefinitionCompileAndLoad} used by every asset loader.
     */
    public static boolean loadXML(String theXMLFilePath, DefMap theDefMap, Object theDefinition) {
        return compileAndLoad(theXMLFilePath, theDefMap, theDefinition);
    }

    /** Convenience over {@link #loadXML(String, DefMap, Object)} returning the fresh definition, or null. */
    public static Object loadXML(String theXMLFilePath, DefMap theDefMap) {
        Object aDefinition = theDefMap.newDefinition();
        if (!compileAndLoad(theXMLFilePath, theDefMap, aDefinition)) {
            return null;
        }
        return aDefinition;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1277-1318 ({@code DefinitionCompileAndLoad}).
     *
     * <p>Reads the compiled cache when it is usable, otherwise compiles from the XML and writes the
     * cache. Upstream shows an error box and {@code exit(0)} when both fail in release builds; a
     * library port cannot terminate the process, so the failure is logged and {@code false} returned.
     */
    public static boolean compileAndLoad(String theXMLFilePath, DefMap theDefMap, Object theDefinition) {
        String aCompiledFilePath = getCompiledFilePathFromXMLFilePath(theXMLFilePath);

        boolean aShouldTryCompiled = !gRequireCompiledUpToDate || isCompiled(theXMLFilePath);
        if (aShouldTryCompiled && readCompiledFile(aCompiledFilePath, theDefMap, theDefinition)) {
            return true;
        }

        PerfTimer aTimer = new PerfTimer();
        aTimer.start();
        boolean aResult = compileFile(theXMLFilePath, aCompiledFilePath, theDefMap, theDefinition);
        PvzpDebug.pvzpLogLn("compile {} ms:'{}'", (int) aTimer.getDuration(), aCompiledFilePath);
        if (aResult) {
            return true;
        }

        PvzpDebug.pvzpErrorMessageBox("missing resource " + aCompiledFilePath, "Error");
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:1253-1274 ({@code DefinitionCompileFile}). */
    public static boolean compileFile(String theXMLFilePath, String theCompiledFilePath, DefMap theDefMap,
                                      Object theDefinition) {
        if (theDefinition == null) {
            PvzpDebug.pvzpErrorMessageBox("null definition target for " + theXMLFilePath, "Error");
            return false;
        }

        XMLParser aXMLParser = new XMLParser();
        try {
            if (!aXMLParser.openFile(theXMLFilePath)) {
                PvzpDebug.pvzpLogLn("XML file not found: {}", theXMLFilePath);
                return false;
            }
            if (!loadMap(aXMLParser, theDefMap, theDefinition)) {
                return false;
            }
        } finally {
            aXMLParser.close();
        }

        return writeCompiledFile(theCompiledFilePath, theDefMap, theDefinition);
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1063-1072 ({@code DefinitionLoadMap}): constructs the
     * definition, then reads fields until the closing tag.
     */
    public static boolean loadMap(XMLParser theXmlParser, DefMap theDefMap, Object theDefinition) {
        fillWithDefaults(theDefMap, theDefinition);

        boolean[] aDone = new boolean[1];
        while (!aDone[0]) {
            if (!readField(theXmlParser, theDefMap, theDefinition, aDone)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1086-1101 ({@code DefinitionFillWithDefaults}): upstream
     * zeroes the struct and sets its string members to {@code ""}; the Java equivalent is to copy the
     * members of a freshly constructed default instance onto the target.
     */
    public static void fillWithDefaults(DefMap theDefMap, Object theDefinition) {
        Object aFresh = theDefMap.newDefinition();
        if (aFresh.getClass() != theDefinition.getClass()) {
            throw new IllegalArgumentException("definition class " + theDefinition.getClass().getName()
                    + " does not match the map's constructor result " + aFresh.getClass().getName());
        }
        for (java.lang.reflect.Field aField : aFresh.getClass().getFields()) {
            try {
                aField.set(theDefinition, aField.get(aFresh));
            } catch (IllegalAccessException anException) {
                throw new IllegalStateException("cannot reset field " + aField.getName(), anException);
            }
        }
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:899-1010 ({@code DefinitionReadField}). Returns false on
     * an unknown element or a malformed value, exactly like upstream.
     */
    public static boolean readField(XMLParser theXmlParser, DefMap theDefMap, Object theDefinition, boolean[] theDone) {
        if (theXmlParser.hasFailed()) {
            return false;
        }

        XMLElement aXMLElement = new XMLElement();
        if (!theXmlParser.nextElement(aXMLElement) || aXMLElement.mType == XMLElement.TYPE_END) {
            theDone[0] = true;
            return true;
        }
        if (aXMLElement.mType != XMLElement.TYPE_START) {
            xmlError(theXmlParser, "Missing element start");
            return false;
        }

        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];

            if (aField.mFieldType == DefFieldType.DT_FLAGS) {
                int[] aFlags = {aField.getIntMember(theDefinition)};
                if (readFlagField(theXmlParser, aXMLElement.mValue, aFlags, aField.symbols())) {
                    aField.setIntMember(theDefinition, aFlags[0]);
                    return true;
                }
            }

            if (!aField.nameMatches(aXMLElement.mValue)) {
                continue;
            }

            boolean aSuccess;
            switch (aField.mFieldType) {
                case DefFieldType.DT_INT:
                    aSuccess = readIntField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_FLOAT:
                    aSuccess = readFloatField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_STRING:
                    aSuccess = readStringField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_ENUM:
                    aSuccess = readEnumField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_VECTOR2:
                    aSuccess = readVector2Field(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_ARRAY:
                    aSuccess = readArrayField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_TRACK_FLOAT:
                    aSuccess = readFloatTrackField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_IMAGE:
                    aSuccess = readImageField(theXmlParser, aField, theDefinition);
                    break;
                case DefFieldType.DT_FONT:
                    aSuccess = readFontField(theXmlParser, aField, theDefinition);
                    break;
                default:
                    throw new IllegalStateException("unsupported field type " + DefFieldType.nameOf(aField.mFieldType)
                            + " for field '" + aField.mFieldName + "'");
            }
            if (aSuccess) {
                return true;
            }

            xmlError(theXmlParser, "Failed to read '" + aXMLElement.mValue + "' field");
            return false;
        }

        xmlError(theXmlParser, "Ignoring unknown element '" + aXMLElement.mValue + "'");
        return false;
    }

    // ------------------------------------------------------------------
    // Per-type XML readers (Definition.cpp:596-1085)
    // ------------------------------------------------------------------

    /**
     * Ported from src/PvzpLib/Definition.cpp:599-630 ({@code DefinitionReadXMLString}): the element
     * content, or {@code ""} for an empty element.
     */
    public static boolean readXMLString(XMLParser theXmlParser, String[] theValue) {
        XMLElement aXMLElement = new XMLElement();
        if (!theXmlParser.nextElement(aXMLElement)) {
            xmlError(theXmlParser, "Missing element value");
            return false;
        }
        if (aXMLElement.mType == XMLElement.TYPE_END) {
            return true;
        }
        if (aXMLElement.mType != XMLElement.TYPE_ELEMENT) {
            xmlError(theXmlParser, "unknown element type");
            return false;
        }

        theValue[0] = aXMLElement.mValue;

        if (!theXmlParser.nextElement(aXMLElement)) {
            xmlError(theXmlParser, "Can't read element end");
            return false;
        }
        if (aXMLElement.mType != XMLElement.TYPE_END) {
            xmlError(theXmlParser, "Missing element end");
            return false;
        }
        return true;
    }

    /** Ported from src/PvzpLib/Definition.cpp:654-666 ({@code DefSymbolValueFromString}). */
    public static boolean symbolValueFromString(DefSymbol[] theSymbolMap, String theName, int[] theResultValue) {
        return DefSymbol.symbolValueFromString(theSymbolMap, theName, theResultValue);
    }

    /** Ported from src/PvzpLib/Definition.cpp:668-680 ({@code DefinitionReadIntField}, {@code sscanf "%d"}). */
    public static boolean readIntField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        int[] aValue = new int[1];
        if (scanInt(aStringValue[0], aValue) >= 0) {
            theField.setIntMember(theDefinition, aValue[0]);
            return true;
        }

        xmlError(theXmlParser, "Can't parse int value '" + aStringValue[0] + "'");
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:682-694 ({@code DefinitionReadFloatField}, {@code sscanf "%f"}). */
    public static boolean readFloatField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        float[] aValue = new float[1];
        if (scanFloat(aStringValue[0], 0, aValue) >= 0) {
            theField.setFloatMember(theDefinition, aValue[0]);
            return true;
        }

        xmlError(theXmlParser, "Can't parse float value '" + aStringValue[0] + "'");
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:696-712 ({@code DefinitionReadStringField}). */
    public static boolean readStringField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        theField.setMember(theDefinition, aStringValue[0] == null || aStringValue[0].isEmpty() ? "" : aStringValue[0]);
        return true;
    }

    /** Ported from src/PvzpLib/Definition.cpp:714-726 ({@code DefinitionReadEnumField}). */
    public static boolean readEnumField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        int[] aValue = new int[1];
        if (symbolValueFromString(theField.symbols(), aStringValue[0], aValue)) {
            theField.setIntMember(theDefinition, aValue[0]);
            return true;
        }

        xmlError(theXmlParser, "Can't parse enum value '" + aStringValue[0] + "'");
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:728-740 ({@code DefinitionReadVector2Field}, {@code sscanf "%f %f"}). */
    public static boolean readVector2Field(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        float[] aFirst = new float[1];
        int aLength = scanFloat(aStringValue[0], 0, aFirst);
        if (aLength >= 0) {
            float[] aSecond = new float[1];
            int aSecondLength = scanFloat(aStringValue[0], aLength, aSecond);
            if (aSecondLength >= 0) {
                theField.setMember(theDefinition, new SexyVector2(aFirst[0], aSecond[0]));
                return true;
            }
        }

        xmlError(theXmlParser, "Can't parse vector2 value '" + aStringValue[0] + "'");
        return false;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:742-780 ({@code DefinitionReadArrayField}). Upstream grows
     * the backing block (doubling whenever the element count is a power of two) and appends one
     * element; only the element count and the element contents are observable, so the Java port simply
     * reallocates an array of {@code count + 1} elements.
     */
    public static boolean readArrayField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        DefMap aDefMap = theField.arrayDefMap();
        DefinitionArrayDef aArray = (DefinitionArrayDef) theField.getMember(theDefinition);
        if (aArray == null) {
            throw new IllegalStateException("array field '" + theField.mFieldName + "' of "
                    + theDefinition.getClass().getName() + " is null; initialise it with a typed empty array");
        }

        Object[] anOldData = aArray.mArrayData;
        Object[] aNewData = allocateElements(anOldData, aArray.mArrayCount + 1);
        System.arraycopy(anOldData, 0, aNewData, 0, aArray.mArrayCount);
        Object aElement = aDefMap.newDefinition();
        aNewData[aArray.mArrayCount] = aElement;
        aArray.mArrayData = aNewData;
        aArray.mArrayCount = aArray.mArrayCount + 1;

        if (loadMap(theXmlParser, aDefMap, aNewData[aArray.mArrayCount - 1])) {
            return true;
        }

        xmlError(theXmlParser, "failed to read sub def");
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:839-937 ({@code DefinitionReadFloatTrackField}). */
    public static boolean readFloatTrackField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        FloatParameterTrack aTrack = (FloatParameterTrack) theField.getMember(theDefinition);
        if (aTrack == null) {
            throw new IllegalStateException("float track field '" + theField.mFieldName + "' of "
                    + theDefinition.getClass().getName() + " is null");
        }
        return readFloatTrackField(theXmlParser, aTrack);
    }

    /** Ported from src/PvzpLib/Definition.cpp:839-937 ({@code DefinitionReadFloatTrackField}). */
    public static boolean readFloatTrackField(XMLParser theXmlParser, FloatParameterTrack theTrack) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }
        String aText = aStringValue[0] == null ? "" : aStringValue[0];

        float[] aValue = new float[1];
        int anIdx = 0;
        theTrack.mCountNodes = 0;

        List<FloatParameterTrackNode> aNodes = new ArrayList<>();
        boolean aBreak = false;
        while (true) {
            if (anIdx >= aText.length()) {
                return false;
            }
            if (charAt(aText, anIdx) == '\0') {
                break;
            }

            FloatParameterTrackNode aTrackNode = new FloatParameterTrackNode();
            aTrackNode.mTime = -1;
            aTrackNode.mCurveType = PvzpCurves.CURVE_LINEAR;
            aTrackNode.mDistribution = PvzpCurves.CURVE_LINEAR;

            if (charAt(aText, anIdx) == '[') {
                // <range>
                anIdx++;
                int aLength = scanFloat(aText, anIdx, aValue);
                if (aLength < 0) {
                    return false;
                }
                anIdx += aLength;
                aTrackNode.mLowValue = aValue[0];
                aTrackNode.mHighValue = aValue[0];
                if (charAt(aText, anIdx) != ']') {
                    anIdx++; // space (' ')
                    int aCurveLength = matchCurveSymbol(aText, anIdx);
                    if (aCurveLength > 0) {
                        aTrackNode.mDistribution = aCurveLength >>> 16;
                        anIdx += (aCurveLength & 0xFFFF) + 1; // the curve plus the following space
                    }
                    int aHighLength = scanFloat(aText, anIdx, aValue);
                    if (aHighLength > 0) {
                        anIdx += aHighLength;
                        aTrackNode.mHighValue = aValue[0];
                    } else if (aHighLength < 0) {
                        // sscanf returns 0 when there is no float to read at all; a hard failure is
                        // only reached when the stream itself misbehaves, which the pointer form cannot
                        // express.
                        aHighLength = 0;
                    }
                }
                if (charAt(aText, anIdx) != ']') {
                    return false;
                }
                anIdx++;
                if (charAt(aText, anIdx) == '\0') {
                    aBreak = true;
                } else {
                    if (charAt(aText, anIdx) == ',') {
                        anIdx++;
                        int aTimeLength = scanFloat(aText, anIdx, aValue);
                        if (aTimeLength < 0) {
                            return false;
                        }
                        anIdx += aTimeLength;
                        aTrackNode.mTime = aValue[0] * 0.01f;
                    }
                    if (charAt(aText, anIdx) == '\0') {
                        aBreak = true;
                    } else {
                        anIdx++;
                    }
                }
            } else {
                // <norange>
                int aLength = scanFloat(aText, anIdx, aValue);
                if (aLength < 0) {
                    return false;
                }
                anIdx += aLength;
                aTrackNode.mLowValue = aValue[0];
                aTrackNode.mHighValue = aValue[0];

                if (charAt(aText, anIdx) == '\0') {
                    aBreak = true;
                } else {
                    if (charAt(aText, anIdx) == ',') {
                        anIdx++;
                        int aTimeLength = scanFloat(aText, anIdx, aValue);
                        if (aTimeLength < 0) {
                            return false;
                        }
                        anIdx += aTimeLength;
                        aTrackNode.mTime = aValue[0] * 0.01f;
                    }
                    if (charAt(aText, anIdx) == '\0') {
                        aBreak = true;
                    } else {
                        anIdx++;
                        int aCurveLength = matchCurveSymbol(aText, anIdx);
                        if (aCurveLength > 0) {
                            aTrackNode.mCurveType = aCurveLength >>> 16;
                            anIdx += aCurveLength & 0xFFFF;
                            if (charAt(aText, anIdx) == '\0') {
                                aBreak = true;
                            } else {
                                anIdx++;
                            }
                        }
                    }
                }
            }

            aNodes.add(aTrackNode);
            if (aBreak) {
                break;
            }
        }

        // Search forward for a timestamp (Definition.cpp:895-928): the nodes before a timestamped node
        // are spread evenly between the previous timestamp and it.
        int aBaseIdx = 0;
        anIdx = 0;
        float aHigh = 0.0f;
        float aLow = 0.0f;
        do {
            int aFoundIdx = -1;
            for (anIdx = aBaseIdx; anIdx < aNodes.size(); anIdx++) {
                if (aNodes.get(anIdx).mTime >= 0.0f) {
                    aHigh = aNodes.get(anIdx).mTime;
                    aFoundIdx = anIdx;
                    break;
                }
            }
            if (aFoundIdx < 0) {
                aHigh = 1.0f;  // no further timestamp: the remaining nodes are spread up to 1.0
            }
            for (int i = aBaseIdx; i < anIdx; i++) {
                float anInterp;
                if (((anIdx - 1) - aBaseIdx) != 0) {
                    anInterp = (float) (i - aBaseIdx) / (float) ((anIdx - 1) - aBaseIdx);
                } else if (aBaseIdx == 0) {
                    anInterp = 0.0f;
                } else {
                    anInterp = 1.0f;
                }
                aNodes.get(i).mTime = aHigh * anInterp + aLow * (1 - anInterp);
            }
            aBaseIdx = anIdx + 1;
            aLow = aHigh;
        } while (aBaseIdx < aNodes.size());

        FloatParameterTrackNode[] aNodeArray = aNodes.toArray(new FloatParameterTrackNode[0]);
        theTrack.setNodes(aNodeArray);
        return true;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:782-820 ({@code DefinitionReadFlagField}). Upstream reads
     * the flag through {@code sscanf("%f")} and casts to {@code int} before setting/clearing the bit,
     * which the Java port keeps.
     */
    public static boolean readFlagField(XMLParser theXmlParser, String theElementName, int[] theResultValue,
                                        DefSymbol[] theSymbolMap) {
        int[] aValue = new int[1];
        if (!symbolValueFromString(theSymbolMap, theElementName, aValue)) {
            return false;
        }

        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        float[] aFlag = new float[1];
        if (scanFloat(aStringValue[0], 0, aFlag) < 0) {
            xmlError(theXmlParser, "Can't parse int value '" + aStringValue[0] + "'");
            return false;
        }

        int aFlagInt = (int) aFlag[0];

        if (theResultValue != null) {
            if (aFlagInt != 0) {
                theResultValue[0] |= 1 << aValue[0];
            } else {
                theResultValue[0] &= ~(1 << aValue[0]);
            }
        }

        return true;
    }

    /** Ported from src/PvzpLib/Definition.cpp:822-837 ({@code DefinitionReadImageField}). */
    public static boolean readImageField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        Image[] anImage = new Image[1];
        if (loadImage(anImage, aStringValue[0])) {
            theField.setMember(theDefinition, anImage[0]);
            return true;
        }

        PvzpDebug.pvzpErrorMessageBox("Failed to find image '" + aStringValue[0] + "' in " + theXmlParser.getFileName(),
                "Missing image");
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:839-855 ({@code DefinitionReadFontField}). */
    public static boolean readFontField(XMLParser theXmlParser, DefField theField, Object theDefinition) {
        String[] aStringValue = {""};
        if (!readXMLString(theXmlParser, aStringValue)) {
            return false;
        }

        Font[] aFont = new Font[1];
        if (loadFont(aFont, aStringValue[0])) {
            theField.setMember(theDefinition, aFont[0]);
            return true;
        }

        PvzpDebug.pvzpErrorMessageBox("Failed to find font '" + aStringValue[0] + "' in " + theXmlParser.getFileName(),
                "Missing font");
        return false;
    }

    // ------------------------------------------------------------------
    // Resource binding (Definition.cpp:300-360)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/Definition.cpp:311-346 ({@code DefinitionLoadImage}). */
    public static boolean loadImage(Image[] theImage, String theName) {
        if (theName == null || theName.isEmpty()) {
            theImage[0] = null;
            return true;
        }

        DefinitionResourceResolver aResolver = gDefResourceResolver;
        if (aResolver != null) {
            Image anImage = aResolver.loadImage(theName);
            if (anImage != null) {
                theImage[0] = anImage;
                return true;
            }

            for (DefLoadResPath aLoadResPath : gDefLoadResPaths) {
                int aPrefixLen = aLoadResPath.mPrefix.length();
                if (aPrefixLen < theName.length()) {
                    String aPathToTry = aLoadResPath.mDirectory + theName.substring(aPrefixLen);
                    Image anImageRef = aResolver.getSharedImage(aPathToTry);
                    if (anImageRef != null) {
                        aResolver.addImageToMap(anImageRef, theName);
                        aResolver.markImageForSanding(anImageRef);
                        theImage[0] = anImageRef;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ported from src/PvzpLib/Definition.cpp:348-360 ({@code DefinitionLoadFont}). */
    public static boolean loadFont(Font[] theFont, String theName) {
        DefinitionResourceResolver aResolver = gDefResourceResolver;
        Font aFont = aResolver == null ? null : aResolver.loadFont(theName);
        theFont[0] = aFont;
        return aFont != null;
    }

    // ------------------------------------------------------------------
    // Compiled cache: sizes (Definition.cpp:430-500)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/Definition.cpp:430-432 ({@code DefGetSizeString}). */
    public static int getSizeString(String theValue) {
        return stringByteLength(theValue) + 4;
    }

    /** Ported from src/PvzpLib/Definition.cpp:434-440 ({@code DefinitionGetArraySize}). */
    public static int getArraySize(DefinitionArrayDef theValue, DefMap theDefMap) {
        int aResult = theValue.mArrayCount * theDefMap.mDefSize + 4;
        for (int i = 0; i < theValue.mArrayCount; i++) {
            aResult += getDeepSize(theDefMap, theValue.mArrayData[i]);
        }
        return aResult;
    }

    /** Ported from src/PvzpLib/Definition.cpp:442-444 ({@code DefGetSizeFloatTrack}). */
    public static int getSizeFloatTrack(FloatParameterTrack theValue) {
        return FloatParameterTrackNode.SIZE * theValue.mCountNodes + 4;
    }

    /** Ported from src/PvzpLib/Definition.cpp:446-451 ({@code DefGetSizeImage}). */
    public static int getSizeImage(Image theValue) {
        return stringByteLength(findImagePath(theValue)) + 4;
    }

    /** Ported from src/PvzpLib/Definition.cpp:453-458 ({@code DefGetSizeFont}). */
    public static int getSizeFont(Font theValue) {
        return stringByteLength(findFontPath(theValue)) + 4;
    }

    /** Ported from src/PvzpLib/Definition.cpp:460-488 ({@code DefinitionGetDeepSize}). */
    public static int getDeepSize(DefMap theDefMap, Object theDefinition) {
        int aResult = 0;
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            switch (aField.mFieldType) {
                case DefFieldType.DT_STRING:
                    aResult += getSizeString((String) aField.getMember(theDefinition));
                    break;
                case DefFieldType.DT_ARRAY:
                    aResult += getArraySize((DefinitionArrayDef) aField.getMember(theDefinition), aField.arrayDefMap());
                    break;
                case DefFieldType.DT_TRACK_FLOAT:
                    aResult += getSizeFloatTrack((FloatParameterTrack) aField.getMember(theDefinition));
                    break;
                case DefFieldType.DT_IMAGE:
                    aResult += getSizeImage((Image) aField.getMember(theDefinition));
                    break;
                case DefFieldType.DT_FONT:
                    aResult += getSizeFont((Font) aField.getMember(theDefinition));
                    break;
                default:
                    break;
            }
        }
        return aResult;
    }

    /** Ported from src/PvzpLib/Definition.cpp:490-492 ({@code DefinitionGetSize}). */
    public static int getSize(DefMap theDefMap, Object theDefinition) {
        return theDefMap.mDefSize + getDeepSize(theDefMap, theDefinition);
    }

    // ------------------------------------------------------------------
    // Compiled cache: schema hash (Definition.cpp:400-428)
    // ------------------------------------------------------------------

    /**
     * Ported from src/PvzpLib/Definition.cpp:400-411 ({@code DefinitionCalcHashSymbolMap}). Hashes the
     * symbol name bytes and the little-endian symbol value, exactly like upstream's
     * {@code crc32(hash, name, strlen)} / {@code crc32(hash, &mSymbolValue, sizeof(int))}.
     */
    public static int calcHashSymbolMap(int aSchemaHash, DefSymbol[] theSymbolMap) {
        if (theSymbolMap == null) {
            return aSchemaHash;
        }
        int aHash = aSchemaHash;
        for (DefSymbol aSymbol : theSymbolMap) {
            if (aSymbol == null || aSymbol.mSymbolName == null) {
                break;
            }
            aHash = crc32(aHash, utf8(aSymbol.mSymbolName));
            aHash = crc32(aHash, littleEndianInt(aSymbol.mSymbolValue));
        }
        return aHash;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:413-446 ({@code DefinitionCalcHashDefMap}). The upstream
     * {@code PvzpList<const DefMap*>} of already-visited maps (which breaks the cycles of nested array
     * maps) becomes a {@link List}; identity comparison matches the upstream pointer comparison.
     */
    public static int calcHashDefMap(int aSchemaHash, DefMap theDefMap, List<DefMap> theProgressMaps) {
        for (DefMap aMap : theProgressMaps) {
            if (aMap == theDefMap) {
                return aSchemaHash;
            }
        }
        theProgressMaps.add(theDefMap);

        int aHash = crc32(aSchemaHash, littleEndianInt(theDefMap.mDefSize));
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            aHash = crc32(aHash, littleEndianInt(aField.mFieldType));
            aHash = crc32(aHash, littleEndianInt(aField.mFieldOffset));
            switch (aField.mFieldType) {
                case DefFieldType.DT_ENUM:
                case DefFieldType.DT_FLAGS:
                    aHash = calcHashSymbolMap(aHash, aField.symbols());
                    break;
                case DefFieldType.DT_ARRAY:
                    aHash = calcHashDefMap(aHash, aField.arrayDefMap(), theProgressMaps);
                    break;
                default:
                    break;
            }
        }
        return aHash;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:448-457 ({@code DefinitionCalcHash}): the seed is
     * {@code crc32(0, Z_NULL, 0) + 1 == 1}.
     */
    /**
     * Compiled-cache format version, mixed into the schema hash so a cache written by another build is
     * rejected and recompiled instead of being trusted.
     *
     * <p>Bumped to 2 after the cache writer stamped the background image's name into every empty image
     * slot of every definition (docs/VERIFICATION.md §12): those files look structurally valid and kept
     * poisoning reanimations load after load, so the fix could not take effect until they expired.
     */
    public static final int CACHE_FORMAT_VERSION = 2;

    public static int calcHash(DefMap theDefMap) {
        return calcHashDefMap(CACHE_FORMAT_VERSION, theDefMap, new ArrayList<>());
    }

    // ------------------------------------------------------------------
    // Compiled cache: writing (Definition.cpp:1191-1274)
    // ------------------------------------------------------------------

    /**
     * Ported from src/PvzpLib/Definition.cpp:1240-1248 ({@code DefinitionCompressCompiledBuffer}):
     * {@code CompressedDefinitionHeader} followed by a zlib stream produced by
     * {@code compress()}-equivalent deflate at the default level.
     */
    public static byte[] compressCompiledBuffer(byte[] theBuffer, int theBufferSize) {
        Deflater aDeflater = new Deflater();
        byte[] aCompressed;
        try {
            aDeflater.setInput(theBuffer, 0, theBufferSize);
            aDeflater.finish();
            byte[] aScratch = new byte[Math.max(64, theBufferSize + 64)];
            int aTotal = 0;
            while (!aDeflater.finished()) {
                int aCount = aDeflater.deflate(aScratch, aTotal, aScratch.length - aTotal);
                if (aCount == 0 && aDeflater.needsInput()) {
                    break;
                }
                aTotal += aCount;
                if (aTotal == aScratch.length) {
                    byte[] aBigger = new byte[aScratch.length * 2];
                    System.arraycopy(aScratch, 0, aBigger, 0, aTotal);
                    aScratch = aBigger;
                }
            }
            aCompressed = new byte[aTotal + 8];
            ByteBuffer aHeader = ByteBuffer.wrap(aCompressed).order(ByteOrder.LITTLE_ENDIAN);
            aHeader.putInt(COMPRESSION_COOKIE);
            aHeader.putInt(theBufferSize);
            System.arraycopy(aScratch, 0, aCompressed, 8, aTotal);
        } finally {
            aDeflater.end();
        }
        return aCompressed;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:534-558 ({@code DefinitionUncompressCompiledBuffer}):
     * validates the 8-byte header and inflates the payload; returns null (with a log) on any mismatch.
     */
    public static byte[] uncompressCompiledBuffer(byte[] theCompressedBuffer, String theCompiledFilePath) {
        if (theCompressedBuffer == null || theCompressedBuffer.length < 8) {
            PvzpDebug.pvzpLogLn("Compile def too small: {}", theCompiledFilePath);
            return null;
        }

        ByteBuffer aHeader = ByteBuffer.wrap(theCompressedBuffer).order(ByteOrder.LITTLE_ENDIAN);
        int aCookie = aHeader.getInt();
        int anUncompressedSize = aHeader.getInt();
        if (aCookie != COMPRESSION_COOKIE) {
            PvzpDebug.pvzpLogLn("Compiled fire cookie wrong: {}", theCompiledFilePath);
            return null;
        }
        if (anUncompressedSize < 0) {
            PvzpDebug.pvzpLogLn("Compiled fire size wrong: {}", theCompiledFilePath);
            return null;
        }

        byte[] aUncompressed = new byte[anUncompressedSize];
        Inflater anInflater = new Inflater();
        try {
            anInflater.setInput(theCompressedBuffer, 8, theCompressedBuffer.length - 8);
            int aTotal = 0;
            while (aTotal < anUncompressedSize) {
                int aCount = anInflater.inflate(aUncompressed, aTotal, anUncompressedSize - aTotal);
                if (aCount == 0) {
                    if (anInflater.finished() || anInflater.needsInput() || anInflater.needsDictionary()) {
                        break;
                    }
                }
                aTotal += aCount;
            }
            if (aTotal != anUncompressedSize) {
                PvzpDebug.pvzpLogLn("Compiled fire size wrong: {}", theCompiledFilePath);
                return null;
            }
        } catch (DataFormatException anException) {
            PvzpDebug.pvzpLogLn("Compiled fire data wrong: {}", theCompiledFilePath);
            return null;
        } finally {
            anInflater.end();
        }
        return aUncompressed;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1250-1262 ({@code DefinitionWriteCompiledFile}): builds
     * the payload (hash, struct image, records), compresses it and stores it under the app data cache
     * root, creating the directory first.
     */
    public static boolean writeCompiledFile(String theCompiledFilePath, DefMap theDefMap, Object theDefinition) {
        int aDefSize = getSize(theDefMap, theDefinition) + 4;
        ByteBuffer aPayload = ByteBuffer.allocate(aDefSize).order(ByteOrder.LITTLE_ENDIAN);
        aPayload.putInt(calcHash(theDefMap));
        aPayload.put(buildStructImage(theDefMap, theDefinition));
        writeCacheRecords(aPayload, theDefMap, theDefinition);
        if (aPayload.position() != aDefSize) {
            throw new IllegalStateException("compiled definition payload size mismatch: wrote " + aPayload.position()
                    + " of " + aDefSize + " bytes");
        }

        byte[] aCompressed = compressCompiledBuffer(aPayload.array(), aDefSize);

        String aFullCompiledPath = getCompiledCacheFullPath(theCompiledFilePath);
        Common.mkDir(Common.getFileDir(aFullCompiledPath));
        try {
            Files.write(Path.of(aFullCompiledPath), aCompressed);
        } catch (IOException anException) {
            return false;
        }
        return true;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1180-1189 ({@code DefMapWriteToCache}): the
     * pointer/string records follow the {@link DefMap} field order.
     */
    public static void writeCacheRecords(ByteBuffer theWritePtr, DefMap theDefMap, Object theDefinition) {
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            switch (aField.mFieldType) {
                case DefFieldType.DT_STRING:
                    writeCacheString(theWritePtr, (String) aField.getMember(theDefinition));
                    break;
                case DefFieldType.DT_ARRAY:
                    writeCacheArray(theWritePtr, (DefinitionArrayDef) aField.getMember(theDefinition), aField.arrayDefMap());
                    break;
                case DefFieldType.DT_TRACK_FLOAT:
                    writeCacheFloatTrack(theWritePtr, (FloatParameterTrack) aField.getMember(theDefinition));
                    break;
                case DefFieldType.DT_IMAGE:
                    writeCacheString(theWritePtr, findImagePath((Image) aField.getMember(theDefinition)));
                    break;
                case DefFieldType.DT_FONT:
                    writeCacheString(theWritePtr, findFontPath((Font) aField.getMember(theDefinition)));
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Definition.cpp:1165-1170 ({@code DefWriteToCacheString}). */
    public static void writeCacheString(ByteBuffer theWritePtr, String theValue) {
        byte[] aBytes = utf8(theValue == null ? "" : theValue);
        theWritePtr.putInt(aBytes.length);
        if (aBytes.length > 0) {
            theWritePtr.put(aBytes);
        }
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:1172-1179 ({@code DefWriteToCacheArray}): the element map
     * size, the raw element images, then the nested records.
     */
    public static void writeCacheArray(ByteBuffer theWritePtr, DefinitionArrayDef theValue, DefMap theDefMap) {
        theWritePtr.putInt(theDefMap.mDefSize);
        for (int i = 0; i < theValue.mArrayCount; i++) {
            theWritePtr.put(buildStructImage(theDefMap, theValue.mArrayData[i]));
        }
        for (int i = 0; i < theValue.mArrayCount; i++) {
            writeCacheRecords(theWritePtr, theDefMap, theValue.mArrayData[i]);
        }
    }

    /** Ported from src/PvzpLib/Definition.cpp:1181-1185 ({@code DefWriteToCacheFloatTrack}). */
    public static void writeCacheFloatTrack(ByteBuffer theWritePtr, FloatParameterTrack theValue) {
        theWritePtr.putInt(theValue.mCountNodes);
        for (int i = 0; i < theValue.mCountNodes; i++) {
            theWritePtr.put(buildNodeImage(theValue.mNodes[i]));
        }
    }

    // ------------------------------------------------------------------
    // Compiled cache: reading (Definition.cpp:362-398, 510-532)
    // ------------------------------------------------------------------

    /**
     * Ported from src/PvzpLib/Definition.cpp:578-628 ({@code DefinitionReadCompiledFile}): tries
     * {@code <appdata>/cache64/compiled/...} first (upstream also accepts a pak member there), then the
     * path as given, then validates the schema hash and rebuilds the definition.
     */
    public static boolean readCompiledFile(String theCompiledFilePath, DefMap theDefMap, Object theDefinition) {
        String aFullCompiledPath = getCompiledCacheFullPath(theCompiledFilePath);
        byte[] aCompressedBuffer = readFileBytes(aFullCompiledPath);
        if (aCompressedBuffer == null) {
            aCompressedBuffer = readFileBytes(theCompiledFilePath);
        }
        if (aCompressedBuffer == null) {
            return false;
        }

        byte[] anUncompressedBuffer = uncompressCompiledBuffer(aCompressedBuffer, theCompiledFilePath);
        if (anUncompressedBuffer == null) {
            return false;
        }

        int aDefHash = calcHash(theDefMap);
        if (anUncompressedBuffer.length < theDefMap.mDefSize + 4) {
            PvzpDebug.pvzpLogLn("Compiled file size too small: {}", theCompiledFilePath);
            return false;
        }

        ByteBuffer aBufferPtr = ByteBuffer.wrap(anUncompressedBuffer).order(ByteOrder.LITTLE_ENDIAN);
        int aCacheHash = aBufferPtr.getInt();
        if (aCacheHash != aDefHash) {
            PvzpDebug.pvzpLogLn("Compiled file schema wrong: {}", theCompiledFilePath);
            return false;
        }

        byte[] aStructImage = new byte[theDefMap.mDefSize];
        aBufferPtr.get(aStructImage);
        applyStructImage(theDefMap, theDefinition, aStructImage);

        boolean aResult = readCacheRecords(aBufferPtr, theDefMap, theDefinition);
        int aReadMemSize = aBufferPtr.position();
        if (aResult && aReadMemSize != anUncompressedBuffer.length) {
            PvzpDebug.pvzpLogLn("Compiled file wrong size: {}", theCompiledFilePath);
            return false;
        }
        return aResult;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:362-398 ({@code DefMapReadFromCache}): fixes up the
     * pointer-typed members of the definition.
     */
    public static boolean readCacheRecords(ByteBuffer theReadPtr, DefMap theDefMap, Object theDefinition) {
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            boolean aSucceed = true;
            switch (aField.mFieldType) {
                case DefFieldType.DT_STRING:
                    aSucceed = readCacheString(theReadPtr, aField, theDefinition);
                    break;
                case DefFieldType.DT_ARRAY:
                    aSucceed = readCacheArray(theReadPtr, aField, theDefinition);
                    break;
                case DefFieldType.DT_IMAGE:
                    aSucceed = readCacheImage(theReadPtr, aField, theDefinition);
                    break;
                case DefFieldType.DT_FONT:
                    aSucceed = readCacheFont(theReadPtr, aField, theDefinition);
                    break;
                case DefFieldType.DT_TRACK_FLOAT:
                    aSucceed = readCacheFloatTrack(theReadPtr, aField, theDefinition);
                    break;
                default:
                    break;
            }
            if (!aSucceed) {
                return false;
            }
        }
        return true;
    }

    /** Ported from src/PvzpLib/Definition.cpp:290-302 ({@code DefReadFromCacheString}). */
    public static boolean readCacheString(ByteBuffer theReadPtr, DefField theField, Object theDefinition) {
        int aLen = readCacheLength(theReadPtr);
        String aString = readCacheStringBytes(theReadPtr, aLen);
        if (aString == null) {
            return false;
        }
        theField.setMember(theDefinition, aString);
        return true;
    }

    /** Ported from src/PvzpLib/Definition.cpp:304-319 ({@code DefReadFromCacheArray}). */
    public static boolean readCacheArray(ByteBuffer theReadPtr, DefField theField, Object theDefinition) {
        DefMap aDefMap = theField.arrayDefMap();
        DefinitionArrayDef aArray = (DefinitionArrayDef) theField.getMember(theDefinition);
        if (aArray == null) {
            aArray = new DefinitionArrayDef();
            theField.setMember(theDefinition, aArray);
        }

        if (theReadPtr.remaining() < 4) {
            PvzpDebug.pvzpLogLn("cache has old def: array size");
            return false;
        }
        int aDefSize = theReadPtr.getInt();
        if (aDefSize != aDefMap.mDefSize) {
            PvzpDebug.pvzpLogLn("cache has old def: array size");
            return false;
        }
        if (aArray.mArrayCount == 0) {
            return true;
        }
        if (aArray.mArrayCount < 0 || theReadPtr.remaining() < (long) aDefSize * aArray.mArrayCount) {
            PvzpDebug.pvzpLogLn("cache has wrong array size");
            return false;
        }

        byte[] anImages = new byte[aDefSize * aArray.mArrayCount];
        theReadPtr.get(anImages);
        Object[] aElements = allocateElements(aArray.mArrayData, aArray.mArrayCount);
        for (int i = 0; i < aArray.mArrayCount; i++) {
            Object anElement = aDefMap.newDefinition();
            byte[] anImage = new byte[aDefSize];
            System.arraycopy(anImages, i * aDefSize, anImage, 0, aDefSize);
            applyStructImage(aDefMap, anElement, anImage);
            aElements[i] = anElement;
        }
        aArray.mArrayData = aElements;

        for (int i = 0; i < aArray.mArrayCount; i++) {
            if (!readCacheRecords(theReadPtr, aDefMap, aArray.mArrayData[i])) {
                return false;
            }
        }
        return true;
    }

    /** Ported from src/PvzpLib/Definition.cpp:321-336 ({@code DefReadFromCacheImage}). */
    public static boolean readCacheImage(ByteBuffer theReadPtr, DefField theField, Object theDefinition) {
        String anImageName = readCacheStringBytes(theReadPtr, readCacheLength(theReadPtr));

        Image[] anImage = {null};
        theField.setMember(theDefinition, null);
        if (anImageName == null) {
            return false;
        }
        if (anImageName.isEmpty()) {
            return true;
        }
        boolean aResult = loadImage(anImage, anImageName);
        theField.setMember(theDefinition, anImage[0]);
        return aResult;
    }

    /** Ported from src/PvzpLib/Definition.cpp:338-352 ({@code DefReadFromCacheFont}). */
    public static boolean readCacheFont(ByteBuffer theReadPtr, DefField theField, Object theDefinition) {
        String aFontName = readCacheStringBytes(theReadPtr, readCacheLength(theReadPtr));

        Font[] aFont = {null};
        theField.setMember(theDefinition, null);
        if (aFontName == null) {
            return false;
        }
        if (aFontName.isEmpty()) {
            return true;
        }
        boolean aResult = loadFont(aFont, aFontName);
        theField.setMember(theDefinition, aFont[0]);
        return aResult;
    }

    /** Ported from src/PvzpLib/Definition.cpp:354-365 ({@code DefReadFromCacheFloatTrack}). */
    public static boolean readCacheFloatTrack(ByteBuffer theReadPtr, DefField theField, Object theDefinition) {
        FloatParameterTrack aTrack = (FloatParameterTrack) theField.getMember(theDefinition);
        if (aTrack == null) {
            aTrack = new FloatParameterTrack();
            theField.setMember(theDefinition, aTrack);
        }

        int aCountNodes = readCacheLength(theReadPtr);
        if (aCountNodes < 0) {
            return false;
        }
        aTrack.mCountNodes = aCountNodes;
        if (aCountNodes > 0) {
            if (theReadPtr.remaining() < (long) FloatParameterTrackNode.SIZE * aCountNodes) {
                PvzpDebug.pvzpLogLn("cache has wrong float track size");
                return false;
            }
            FloatParameterTrackNode[] aNodes = new FloatParameterTrackNode[aCountNodes];
            for (int i = 0; i < aCountNodes; i++) {
                byte[] anImage = new byte[FloatParameterTrackNode.SIZE];
                theReadPtr.get(anImage);
                aNodes[i] = readNodeImage(anImage);
            }
            aTrack.mNodes = aNodes;
        } else {
            aTrack.mNodes = null;
        }
        return true;
    }

    /** Reads a record length (or element/array count); -1 when the buffer is exhausted (corrupt cache). */
    private static int readCacheLength(ByteBuffer theReadPtr) {
        if (theReadPtr.remaining() < 4) {
            PvzpDebug.pvzpLogLn("cache is truncated");
            return -1;
        }
        return theReadPtr.getInt();
    }

    /** Reads {@code theLength} UTF-8 bytes; null when the buffer is exhausted (corrupt cache). */
    private static String readCacheStringBytes(ByteBuffer theReadPtr, int theLength) {
        if (theLength < 0 || theReadPtr.remaining() < theLength) {
            PvzpDebug.pvzpLogLn("cache is truncated");
            return null;
        }
        if (theLength == 0) {
            return "";
        }
        byte[] aBytes = new byte[theLength];
        theReadPtr.get(aBytes);
        return new String(aBytes, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Flat struct image (the "raw data" half of a cache entry)
    // ------------------------------------------------------------------

    /**
     * Ported from the raw {@code SMemW(aDef, theDefinition, theDefMap->mDefSize)} of Definition.cpp:1256
     * plus the constructor defaults: serialises every non-pointer member of {@code theDefinition} into a
     * flat image of {@link DefMap#mDefSize} bytes, laying each member out at its
     * {@link DefField#mFieldOffset}. Pointer slots (strings, images, fonts) are written as zero because
     * upstream copies a live heap pointer there; the pointee data follows in the records.
     */
    public static byte[] buildStructImage(DefMap theDefMap, Object theDefinition) {
        byte[] anImage = new byte[theDefMap.mDefSize];
        ByteBuffer aBuffer = ByteBuffer.wrap(anImage).order(ByteOrder.LITTLE_ENDIAN);
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            int anOffset = aField.mFieldOffset;
            switch (aField.mFieldType) {
                case DefFieldType.DT_INT:
                case DefFieldType.DT_ENUM:
                case DefFieldType.DT_FLAGS:
                    aBuffer.putInt(anOffset, aField.getIntMember(theDefinition));
                    break;
                case DefFieldType.DT_FLOAT:
                    aBuffer.putFloat(anOffset, aField.getFloatMember(theDefinition));
                    break;
                case DefFieldType.DT_VECTOR2: {
                    SexyVector2 aValue = (SexyVector2) aField.getMember(theDefinition);
                    if (aValue != null) {
                        aBuffer.putFloat(anOffset, aValue.x);
                        aBuffer.putFloat(anOffset + 4, aValue.y);
                    }
                    break;
                }
                case DefFieldType.DT_ARRAY: {
                    DefinitionArrayDef aValue = (DefinitionArrayDef) aField.getMember(theDefinition);
                    aBuffer.putInt(anOffset + 8, aValue == null ? 0 : aValue.mArrayCount);
                    break;
                }
                case DefFieldType.DT_TRACK_FLOAT: {
                    FloatParameterTrack aValue = (FloatParameterTrack) aField.getMember(theDefinition);
                    aBuffer.putInt(anOffset + 8, aValue == null ? 0 : aValue.mCountNodes);
                    break;
                }
                default:
                    break;
            }
        }
        return anImage;
    }

    /**
     * Inverse of {@link #buildStructImage}: restores every non-pointer member from
     * {@code theStructImage} (upstream {@code SMemR(aBufferPtr, theDefinition, mDefSize)}). The
     * pointer/count slots are restored for the array and float-track members - the records that follow
     * fill in their contents.
     */
    public static void applyStructImage(DefMap theDefMap, Object theDefinition, byte[] theStructImage) {
        ByteBuffer aBuffer = ByteBuffer.wrap(theStructImage).order(ByteOrder.LITTLE_ENDIAN);
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            int anOffset = aField.mFieldOffset;
            switch (aField.mFieldType) {
                case DefFieldType.DT_INT:
                case DefFieldType.DT_ENUM:
                case DefFieldType.DT_FLAGS:
                    aField.setIntMember(theDefinition, aBuffer.getInt(anOffset));
                    break;
                case DefFieldType.DT_FLOAT:
                    aField.setFloatMember(theDefinition, aBuffer.getFloat(anOffset));
                    break;
                case DefFieldType.DT_VECTOR2:
                    aField.setMember(theDefinition, new SexyVector2(aBuffer.getFloat(anOffset), aBuffer.getFloat(anOffset + 4)));
                    break;
                case DefFieldType.DT_ARRAY: {
                    DefinitionArrayDef aValue = (DefinitionArrayDef) aField.getMember(theDefinition);
                    if (aValue == null) {
                        aValue = new DefinitionArrayDef();
                        aField.setMember(theDefinition, aValue);
                    }
                    aValue.mArrayCount = aBuffer.getInt(anOffset + 8);
                    break;
                }
                case DefFieldType.DT_TRACK_FLOAT: {
                    FloatParameterTrack aValue = (FloatParameterTrack) aField.getMember(theDefinition);
                    if (aValue == null) {
                        aValue = new FloatParameterTrack();
                        aField.setMember(theDefinition, aValue);
                    }
                    aValue.mCountNodes = aBuffer.getInt(anOffset + 8);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private static byte[] buildNodeImage(FloatParameterTrackNode theNode) {
        byte[] anImage = new byte[FloatParameterTrackNode.SIZE];
        ByteBuffer aBuffer = ByteBuffer.wrap(anImage).order(ByteOrder.LITTLE_ENDIAN);
        aBuffer.putFloat(0, theNode.mTime);
        aBuffer.putFloat(4, theNode.mLowValue);
        aBuffer.putFloat(8, theNode.mHighValue);
        aBuffer.putInt(12, theNode.mCurveType);
        aBuffer.putInt(16, theNode.mDistribution);
        return anImage;
    }

    private static FloatParameterTrackNode readNodeImage(byte[] theImage) {
        ByteBuffer aBuffer = ByteBuffer.wrap(theImage).order(ByteOrder.LITTLE_ENDIAN);
        return new FloatParameterTrackNode(aBuffer.getFloat(0), aBuffer.getFloat(4), aBuffer.getFloat(8), aBuffer.getInt(12),
                aBuffer.getInt(16));
    }

    // ------------------------------------------------------------------
    // Frees (Definition.cpp:1320-1372) - the Java port resets the members and lets the GC do the rest
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/Definition.cpp:1340-1359 ({@code DefinitionFreeMap}). */
    public static void freeMap(DefMap theDefMap, Object theDefinition) {
        int aFieldCount = theDefMap.fieldCount();
        for (int i = 0; i < aFieldCount; i++) {
            DefField aField = theDefMap.mMapFields[i];
            switch (aField.mFieldType) {
                case DefFieldType.DT_STRING:
                    aField.setMember(theDefinition, "");
                    break;
                case DefFieldType.DT_ARRAY:
                    freeArrayField((DefinitionArrayDef) aField.getMember(theDefinition), aField.arrayDefMap());
                    break;
                case DefFieldType.DT_TRACK_FLOAT:
                    FloatParameterTrack aTrack = (FloatParameterTrack) aField.getMember(theDefinition);
                    aTrack.mNodes = null;
                    aTrack.mCountNodes = 0;
                    break;
                case DefFieldType.DT_IMAGE:
                case DefFieldType.DT_FONT:
                    aField.setMember(theDefinition, null);
                    break;
                default:
                    break;
            }
        }
    }

    /** Ported from src/PvzpLib/Definition.cpp:1322-1338 ({@code DefinitionFreeArrayField}). */
    public static void freeArrayField(DefinitionArrayDef theArray, DefMap theDefMap) {
        if (theArray == null) {
            return;
        }
        for (int i = 0; i < theArray.mArrayCount; i++) {
            freeMap(theDefMap, theArray.mArrayData[i]);
        }
        theArray.mArrayData = emptyElements(theArray.mArrayData);
        theArray.mArrayCount = 0;
    }

    // ------------------------------------------------------------------
    // Float track helpers (Definition.h:200-238, Definition.cpp:1385-1424)
    // ------------------------------------------------------------------

    /** Ported from src/PvzpLib/Definition.h:200-203 ({@code FloatTrackIsSet}). */
    public static boolean floatTrackIsSet(FloatParameterTrack theTrack) {
        return theTrack.mCountNodes != 0 && theTrack.mNodes[0].mCurveType != PvzpCurves.CURVE_CONSTANT;
    }

    /** Ported from src/PvzpLib/Definition.h:208-237 ({@code FloatTrackEvaluate}). */
    public static float floatTrackEvaluate(FloatParameterTrack theTrack, float theTimeValue, float theInterp) {
        if (theTrack.mCountNodes == 0) {
            return 0.0f;
        }

        if (theTimeValue < theTrack.mNodes[0].mTime) {
            return PvzpCommon.pvzpCurveEvaluate(theInterp, theTrack.mNodes[0].mLowValue, theTrack.mNodes[0].mHighValue,
                    theTrack.mNodes[0].mDistribution);
        }

        for (int i = 1; i < theTrack.mCountNodes; i++) {
            FloatParameterTrackNode aNodeNxt = theTrack.mNodes[i];
            if (theTimeValue <= aNodeNxt.mTime) {
                FloatParameterTrackNode aNodeCur = theTrack.mNodes[i - 1];
                float aTimeFraction = (theTimeValue - aNodeCur.mTime) / (aNodeNxt.mTime - aNodeCur.mTime);
                float aLeftValue = PvzpCommon.pvzpCurveEvaluate(theInterp, aNodeCur.mLowValue, aNodeCur.mHighValue,
                        aNodeCur.mDistribution);
                float aRightValue = PvzpCommon.pvzpCurveEvaluate(theInterp, aNodeNxt.mLowValue, aNodeNxt.mHighValue,
                        aNodeNxt.mDistribution);
                return PvzpCommon.pvzpCurveEvaluate(aTimeFraction, aLeftValue, aRightValue, aNodeCur.mCurveType);
            }
        }

        FloatParameterTrackNode aLastNode = theTrack.mNodes[theTrack.mCountNodes - 1];
        return PvzpCommon.pvzpCurveEvaluate(theInterp, aLastNode.mLowValue, aLastNode.mHighValue, aLastNode.mDistribution);
    }

    /** Ported from src/PvzpLib/Definition.h:239-242 ({@code FloatTrackEvaluateFromLastTime}). */
    public static float floatTrackEvaluateFromLastTime(FloatParameterTrack theTrack, float theTimeValue, float theInterp) {
        return theTimeValue < 0.0f ? 0.0f : floatTrackEvaluate(theTrack, theTimeValue, theInterp);
    }

    /** Ported from src/PvzpLib/Definition.cpp:1385-1404 ({@code FloatTrackSetDefault}). */
    public static void floatTrackSetDefault(FloatParameterTrack theTrack, float theValue) {
        if (theTrack.mNodes == null && theValue != 0.0f) {
            theTrack.setNodes(new FloatParameterTrackNode[] {
                    new FloatParameterTrackNode(0.0f, theValue, theValue, PvzpCurves.CURVE_CONSTANT, PvzpCurves.CURVE_LINEAR)
            });
        } else if (theTrack.mNodes == null) {
            theTrack.mCountNodes = 0;
        }
    }

    /** Ported from src/PvzpLib/Definition.cpp:1406-1409 ({@code FloatTrackIsConstantZero}). */
    public static boolean floatTrackIsConstantZero(FloatParameterTrack theTrack) {
        return theTrack.mCountNodes == 0
                || (theTrack.mCountNodes == 1 && theTrack.mNodes[0].mLowValue == 0.0f && theTrack.mNodes[0].mHighValue == 0.0f);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Upstream reads {@code aStringChars[anIdx]} from a NUL-terminated buffer; a '\0' offsets the end. */
    private static char charAt(String theText, int theIndex) {
        return theIndex >= 0 && theIndex < theText.length() ? theText.charAt(theIndex) : '\0';
    }

    /**
     * Matches one {@code gDefTrackEaseSymbols} entry at {@code theIndex} (upstream
     * Definition.cpp:866-877 {@code strncmp}). Returns {@code (curveValue << 16) | nameLength}, or 0
     * when no symbol matches.
     */
    private static int matchCurveSymbol(String theText, int theIndex) {
        for (DefSymbol aSymbol : gDefTrackEaseSymbols) {
            if (aSymbol == null || aSymbol.mSymbolName == null) {
                break;
            }
            int aStrLen = aSymbol.mSymbolName.length();
            if (theIndex + aStrLen <= theText.length()
                    && theText.regionMatches(theIndex, aSymbol.mSymbolName, 0, aStrLen)) {
                return (aSymbol.mSymbolValue << 16) | aStrLen;
            }
        }
        return 0;
    }

    /**
     * Ported from the {@code sscanf("%f")} calls of Definition.cpp:850-900: parses a float starting at
     * {@code theIndex}, skipping leading whitespace like {@code %f}, and returns the number of
     * characters consumed (as {@code %n} would) or -1 when there is no float.
     */
    static int scanFloat(String theText, int theIndex, float[] theValue) {
        int anIndex = theIndex;
        int aLength = theText.length();
        while (anIndex < aLength && isSpace(theText.charAt(anIndex))) {
            anIndex++;
        }

        int aStart = anIndex;
        if (anIndex < aLength && (theText.charAt(anIndex) == '+' || theText.charAt(anIndex) == '-')) {
            anIndex++;
        }
        int aDigitCount = 0;
        while (anIndex < aLength && isDigit(theText.charAt(anIndex))) {
            anIndex++;
            aDigitCount++;
        }
        if (anIndex < aLength && theText.charAt(anIndex) == '.') {
            anIndex++;
            while (anIndex < aLength && isDigit(theText.charAt(anIndex))) {
                anIndex++;
                aDigitCount++;
            }
        }
        if (aDigitCount == 0) {
            return -1;
        }

        if (anIndex < aLength && (theText.charAt(anIndex) == 'e' || theText.charAt(anIndex) == 'E')) {
            int anExpIndex = anIndex + 1;
            if (anExpIndex < aLength && (theText.charAt(anExpIndex) == '+' || theText.charAt(anExpIndex) == '-')) {
                anExpIndex++;
            }
            int anExpDigits = 0;
            while (anExpIndex < aLength && isDigit(theText.charAt(anExpIndex))) {
                anExpIndex++;
                anExpDigits++;
            }
            if (anExpDigits > 0) {
                anIndex = anExpIndex;
            }
        }

        theValue[0] = Float.parseFloat(theText.substring(aStart, anIndex));
        return anIndex - theIndex;
    }

    /**
     * Ported from the {@code sscanf("%d")} calls of Definition.cpp:674-678 and Definition.cpp:790-796:
     * parses an integer at the start of {@code theText}, skipping leading whitespace, and returns the
     * number of characters consumed or -1 when there is no integer.
     */
    static int scanInt(String theText, int[] theValue) {
        int anIndex = 0;
        int aLength = theText.length();
        while (anIndex < aLength && isSpace(theText.charAt(anIndex))) {
            anIndex++;
        }

        int aStart = anIndex;
        if (anIndex < aLength && (theText.charAt(anIndex) == '+' || theText.charAt(anIndex) == '-')) {
            anIndex++;
        }
        int aDigitCount = 0;
        while (anIndex < aLength && isDigit(theText.charAt(anIndex))) {
            anIndex++;
            aDigitCount++;
        }
        if (aDigitCount == 0) {
            return -1;
        }

        theValue[0] = Integer.parseInt(theText.substring(aStart, anIndex));
        return anIndex;
    }

    private static boolean isDigit(char theChar) {
        return theChar >= '0' && theChar <= '9';
    }

    private static boolean isSpace(char theChar) {
        return theChar == ' ' || theChar == '\t' || theChar == '\n' || theChar == '\r' || theChar == '\f' || theChar == 0x0B;
    }

    private static byte[] readFileBytes(String theFilePath) {
        try {
            Path aPath = Path.of(theFilePath);
            return Files.isReadable(aPath) ? Files.readAllBytes(aPath) : null;
        } catch (IOException | RuntimeException anException) {
            return null;
        }
    }

    /** Element storage for a known-size array: keeps the pointee array type (upstream {@code void*}). */
    private static Object[] allocateElements(Object[] theExisting, int theCount) {
        Class<?> aComponentType = theExisting != null ? theExisting.getClass().getComponentType() : Object.class;
        return (Object[]) java.lang.reflect.Array.newInstance(aComponentType, theCount);
    }

    private static Object[] emptyElements(Object[] theExisting) {
        return allocateElements(theExisting, 0);
    }

    private static String findImagePath(Image theImage) {
        DefinitionResourceResolver aResolver = gDefResourceResolver;
        if (aResolver != null) {
            String aPath = aResolver.findImagePath(theImage);
            if (aPath != null && !aPath.isEmpty()) {
                return aPath;
            }
        }
        return theImage != null && theImage.mFilePath != null ? theImage.mFilePath : "";
    }

    private static String findFontPath(Font theFont) {
        DefinitionResourceResolver aResolver = gDefResourceResolver;
        if (theFont == null || aResolver == null) {
            return "";
        }
        String aPath = aResolver.findFontPath(theFont);
        return aPath == null ? "" : aPath;
    }

    static byte[] utf8(String theText) {
        return theText.getBytes(StandardCharsets.UTF_8);
    }

    private static int stringByteLength(String theText) {
        return theText == null ? 0 : utf8(theText).length;
    }

    private static byte[] littleEndianInt(int theValue) {
        return new byte[] {
                (byte) theValue,
                (byte) (theValue >>> 8),
                (byte) (theValue >>> 16),
                (byte) (theValue >>> 24)
        };
    }

    /**
     * Ported from the zlib {@code crc32(crc, buf, len)} calls of Definition.cpp:400-446, including the
     * ability to continue from an arbitrary previous value (upstream seeds with 1).
     */
    static int crc32(int theCrc, byte[] theData) {
        int aCrc = ~theCrc;
        for (byte aByte : theData) {
            aCrc = (aCrc >>> 8) ^ CRC_TABLE[(aCrc ^ aByte) & 0xFF];
        }
        return ~aCrc;
    }

    private static final int[] CRC_TABLE = makeCrcTable();

    private static int[] makeCrcTable() {
        int[] aTable = new int[256];
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) {
                c = (c & 1) != 0 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
            }
            aTable[n] = c;
        }
        return aTable;
    }
}
