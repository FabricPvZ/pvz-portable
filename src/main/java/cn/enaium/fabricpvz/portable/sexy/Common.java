package cn.enaium.fabricpvz.portable.sexy;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ported from src/SexyAppFramework/Common.h:123 (namespace Sexy) and
 * src/SexyAppFramework/Common.cpp:38 (global definitions).
 *
 * <p>Faithful port of the {@code Sexy::} global helper namespace: string case/trim helpers, string
 * &lt;-&gt; number conversions, path/file-name helpers, XML encode/decode, the {@code %define%}
 * evaluator, comma-separated number formatting, UTF-8 code point helpers, byte-order swaps and the
 * little/big endian conversion helpers, plus the global random number generator, logging and the
 * app-data/resource-folder accessors.
 *
 * <p>Java-specific deviations (each one also carries a {@code PORT-NOTE} at the method):
 * <ul>
 *   <li>{@code std::string_view} arguments become {@link String}; {@code char32_t} becomes
 *       {@code int} (0..0x10FFFF).</li>
 *   <li>Functions that returned {@code bool} plus an out-pointer
 *       ({@code StringToInt}/{@code StringToDouble}) return a boxed {@code Integer}/{@code Double}
 *       instead; {@code null} means the upstream {@code false} (nothing was parsed).</li>
 *   <li>{@code size_t} is 64-bit ({@link Long#BYTES}); the C++ typedefs {@code uchar}/{@code ushort}/
 *       {@code uint}/{@code ulong}/{@code int64} have no Java equivalent (unsigned values are kept in
 *       the next wider signed type), and {@code DefinesMap} is {@code Map<String,String>}.</li>
 * </ul>
 *
 * <p>{@code misc/memmgr.h} is intentionally <b>not</b> ported: it only exists to install
 * {@code SEXY_MEMTRACE} {@code operator new}/{@code delete} leak-tracking replacements (an entirely
 * preprocessor-level C++ mechanism, inactive unless {@code SEXY_MEMTRACE} is defined), defines no
 * runtime behavior and is not referenced by {@code Common}, {@code KeyCodes} or {@code RegEmu}. The
 * host platform shims of {@code Common.h} ({@code _WIN32} {@code nanosleep}, the {@code LENGTH}
 * macro, {@code unreachable()}, the {@code _WIN32}/{@code __SWITCH__} path branches) are likewise
 * not portable and are omitted.
 */
public final class Common {
    private Common() {
    }

    /** Ported from src/SexyAppFramework/Common.h:126. Upstream {@code const ulong SEXY_RAND_MAX = 0x7FFFFFFF;}. */
    public static final long SEXY_RAND_MAX = 0x7FFFFFFFL;

    /** Ported from src/SexyAppFramework/Common.h:128 (defined at Common.cpp:38). */
    public static boolean gDebug = false;

    /** Ported from src/SexyAppFramework/Common.cpp:39 ({@code static Sexy::MTRand gMTRand;}). */
    private static final MTRand gMTRand = new MTRand();

    /**
     * libc's default seed ({@code srand(1)}). Upstream reseeds that stream from the tick count
     * ({@code srand(SDL_GetTicks())}, SexyAppBase.cpp:3454), which is not reproducible; the port
     * keeps the default seed so every run shakes identically.
     */
    private static final long LIBC_RAND_SEED = 1L;

    /**
     * Stand-in for the libc {@code rand()} stream upstream uses at the few call sites outside
     * SexyAppFramework: {@code LawnApp::DrawCrazyDave}'s speech-bubble shake (LawnApp.cpp:2917-2918)
     * and {@code PvzpParticle}'s {@code FIELD_SHAKE} (which reseeds libc's stream per call,
     * PvzpParticle.cpp:542, and therefore keeps its own substitute in this port). See
     * {@link #libcRand(int)} for why it is a stream of its own.
     */
    private static final MTRand gLibcRand = new MTRand(LIBC_RAND_SEED);

    /** Ported from src/SexyAppFramework/Common.h:130. */
    public enum SexyLogPriority {
        Info,
        Error
    }

    // ------------------------------------------------------------------
    // Logging
    // ------------------------------------------------------------------

    /**
     * Logger name of the game's own output: the id of the built-in mod the Fabric Loader reports
     * this port under (the provider's {@code PvzGameProvider.GAME_ID}), so the game's lines sit next
     * to the loader's categories ({@code FabricLoader/...}) and the mods' (their mod id) and are
     * filtered by the same logger-level configuration.
     *
     * <p>Naming convention: one logger per area, the game's id as the root and
     * {@code pvz-portable/<area>} for an area that wants its own switch. The only child today is
     * {@link #DEBUG_LOGGER_NAME}.
     */
    public static final String LOGGER_NAME = "pvz-portable";

    /** The debug channel of the port (the {@code PvzpDebug} trace facility); see {@link #logDebugLn}. */
    public static final String DEBUG_LOGGER_NAME = LOGGER_NAME + "/debug";

    /** The game's log channel; everything the ported SexyAppFramework layer logs goes through it. */
    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    /** The verbose/trace channel; nothing logs here unless it is enabled. */
    private static final Logger DEBUG_LOGGER = LoggerFactory.getLogger(DEBUG_LOGGER_NAME);

    /** Ported from src/SexyAppFramework/Common.cpp:68. */
    private static Writer gLogFileSink;

    /** Ported from src/SexyAppFramework/Common.cpp:69. */
    private static final Object gLogFileSinkMutex = new Object();

    /**
     * Ported from src/SexyAppFramework/Common.cpp:71.
     *
     * <p>Upstream opens the path with {@code std::ios::app | std::ios::binary}; non-UTF-8 bytes are
     * impossible for a Java {@link String}, so the UTF-8 encoding is used.
     */
    public static void registerLogFileSink(String thePath) {
        synchronized (gLogFileSinkMutex) {
            try {
                gLogFileSink = new OutputStreamWriter(
                        new FileOutputStream(thePath, true), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logErrorLn("Failed to open log file '{}'", thePath);
            }
        }
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:78.
     *
     * <p>Upstream forwards the text to {@code SDL_LogMessage} (Info to stdout, Error to stderr) and
     * appends {@code theText + '\n'} to the registered log sink, if any.
     *
     * <p>PORT-NOTE: the console half is not SDL any more. The text is logged through SLF4J on
     * {@link #LOGGER_NAME} (Info to INFO, Error to ERROR), which under every launch of this
     * workspace is the provider's Log4j context, so the game's lines carry the same
     * {@code [time] [thread/LEVEL] [logger]: message} prefix as the loader's, the Mixin subsystem's
     * and the mods' lines; nothing is written to {@code System.out}/{@code System.err} a second
     * time. The registered log sink is unaffected and still receives the raw text.
     */
    public static void dispatchLogLn(SexyLogPriority thePriority, String theText) {
        if (theText == null || theText.isEmpty()) {
            return;
        }

        if (thePriority == SexyLogPriority.Error) {
            LOGGER.error(theText);
        } else {
            LOGGER.info(theText);
        }

        appendToLogFile(theText);
    }

    /**
     * The file half of {@link #dispatchLogLn}: appends {@code theText + '\n'} to the sink registered
     * by {@link #registerLogFileSink}, if any.
     */
    private static void appendToLogFile(String theText) {
        synchronized (gLogFileSinkMutex) {
            if (gLogFileSink == null) {
                return;
            }

            try {
                gLogFileSink.write(theText);
                gLogFileSink.write('\n');
                gLogFileSink.flush();
            } catch (IOException e) {
                LOGGER.warn("Failed to write to log file");
                try {
                    gLogFileSink.close();
                } catch (IOException ignored) {
                    // Upstream also simply drops the sink after a failed write.
                }
                gLogFileSink = null;
            }
        }
    }

    /** Ported from src/SexyAppFramework/Common.h:136 ({@code LogInfoLn} format-string template). INFO on {@link #LOGGER_NAME}. */
    public static void logInfoLn(String theFmt, Object... theArgs) {
        dispatchLogLn(SexyLogPriority.Info, strFormat(theFmt, theArgs));
    }

    /** Ported from src/SexyAppFramework/Common.h:142 ({@code LogErrorLn} format-string template). ERROR on {@link #LOGGER_NAME}. */
    public static void logErrorLn(String theFmt, Object... theArgs) {
        dispatchLogLn(SexyLogPriority.Error, strFormat(theFmt, theArgs));
    }

    /**
     * PORT-NOTE: the ERROR channel with a cause, so a caught {@code Throwable} keeps its stack trace
     * (rendered by the logging stack right after the message) instead of going out through
     * {@code printStackTrace} on stderr - the one way the game's output used to bypass the stack.
     */
    public static void logErrorLn(String theMsg, Throwable theCause) {
        if (theCause == null) {
            dispatchLogLn(SexyLogPriority.Error, theMsg);
            return;
        }

        LOGGER.error(theMsg, theCause);
        appendToLogFile(theMsg + ": " + theCause);
    }

    /**
     * PORT-NOTE: the WARN channel of the game's logger, for the call sites whose message reports a
     * failure that no other line reports (a lost music file, a gameplay spawn that could not be
     * placed, ...). Upstream had only Info/Error, which is why this is a Java-side helper next to
     * the ported {@link #logInfoLn}/{@link #logErrorLn} pair instead of a third
     * {@link SexyLogPriority}.
     */
    public static void logWarnLn(String theFmt, Object... theArgs) {
        String aText = strFormat(theFmt, theArgs);
        if (aText.isEmpty()) {
            return;
        }

        LOGGER.warn(aText);
        appendToLogFile(aText);
    }

    /**
     * PORT-NOTE: the debug channel of the port ({@link #DEBUG_LOGGER_NAME}), for the call sites of
     * the verbose/trace paths - {@code PvzpDebug}'s trace facility, which upstream also logged
     * through {@code LogInfoLn} and which is therefore as loud as {@code Product:} in the C++ build.
     *
     * <p>Those call sites are one line per resource group, per compiled definition, per reanim, per
     * cheat key and per movie-sync correction, i.e. noise on a healthy run; DEBUG is their level, and
     * a hot path pays nothing for them: with the channel off the message is not even formatted.
     */
    public static void logDebugLn(String theFmt, Object... theArgs) {
        if (!DEBUG_LOGGER.isDebugEnabled()) {
            return;
        }

        String aText = strFormat(theFmt, theArgs);
        if (aText.isEmpty()) {
            return;
        }

        DEBUG_LOGGER.debug(aText);
        appendToLogFile(aText);
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:134 ({@code std::format} usage).
     *
     * <p>Small {@code std::format}-like helper for the {@code "{}"} placeholder used by the upstream
     * log calls: {@code "{}"} consumes the next argument, an explicit index such as <code>{0}</code>
     * consumes that argument, and doubled braces produce a literal brace.
     */
    public static String strFormat(String theFmt, Object... theArgs) {
        StringBuilder aResult = new StringBuilder(theFmt.length() + 16);
        int anArgIndex = 0;
        for (int i = 0; i < theFmt.length(); i++) {
            char c = theFmt.charAt(i);
            if (c == '{') {
                if (i + 1 < theFmt.length() && theFmt.charAt(i + 1) == '{') {
                    aResult.append('{');
                    i++;
                    continue;
                }
                int aClose = theFmt.indexOf('}', i + 1);
                if (aClose < 0) {
                    aResult.append(theFmt, i, theFmt.length());
                    break;
                }
                String aSpec = theFmt.substring(i + 1, aClose);
                int anIndex;
                if (aSpec.isEmpty()) {
                    anIndex = anArgIndex++;
                } else if (aSpec.chars().allMatch(Character::isDigit)) {
                    anIndex = Integer.parseInt(aSpec);
                } else {
                    aResult.append(theFmt, i, aClose + 1);
                    i = aClose;
                    continue;
                }
                Object anArg = anIndex < theArgs.length ? theArgs[anIndex] : null;
                aResult.append(anArg == null ? "null" : anArg.toString());
                i = aClose;
            } else if (c == '}' && i + 1 < theFmt.length() && theFmt.charAt(i + 1) == '}') {
                aResult.append('}');
                i++;
            } else {
                aResult.append(c);
            }
        }
        return aResult.toString();
    }

    // ------------------------------------------------------------------
    // Random numbers (Common.cpp:97-115)
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/Common.cpp:97. */
    public static int rand() {
        return (int) gMTRand.next();
    }

    /** Ported from src/SexyAppFramework/Common.cpp:102. */
    public static int rand(int range) {
        // PORT-NOTE: C++ casts `range` to `unsigned long` before calling MTRand::Next.
        return (int) gMTRand.next(range);
    }

    /** Ported from src/SexyAppFramework/Common.cpp:107. */
    public static float rand(float range) {
        return gMTRand.next(range);
    }

    /** Ported from src/SexyAppFramework/Common.cpp:112. */
    public static void sRand(long theSeed) {
        gMTRand.sRand(theSeed);
    }

    /**
     * Ported from libc {@code rand() % range} — upstream's non-Sexy random stream. Only a handful of
     * call sites use it ({@code LawnApp::DrawCrazyDave}, {@code PvzpParticle::FIELD_SHAKE}).
     *
     * <p>PORT-NOTE: this must not go through {@link #rand(int)}/{@link #gMTRand}. Upstream's libc
     * generator is independent of {@code MTRand}, so it is neither silenced by
     * {@code MTRand::SetRandAllowed} (MTRand.cpp:75-88 — {@code LawnApp::DrawCrazyDave} relies on
     * that: it runs inside {@code SexyAppBase::DrawDirtyStuff}'s {@code MTAutoDisallowRand} guard,
     * SexyAppBase.cpp:1778) nor part of the gameplay random sequence that has to stay identical to
     * upstream for a fixed seed. Using the MTRand stream here asserts on the first drawn {SHAKE}
     * frame (MTRand.java:160) and would desync every later gameplay draw.
     *
     * @see #gLibcRand
     */
    public static int libcRand(int range) {
        return (int) gLibcRand.nextNoAssert(range);
    }

    // ------------------------------------------------------------------
    // App data / resource folders (Common.cpp:117-146)
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/Common.cpp:41 ({@code std::filesystem::path gAppDataFolder;}). */
    private static String gAppDataFolder = "";

    /** Ported from src/SexyAppFramework/Common.cpp:42. */
    private static String gResourceFolder = "";

    /** Ported from src/SexyAppFramework/Common.cpp:43 (cached string). */
    private static String gResourceFolderStr = "";

    /** Ported from src/SexyAppFramework/Common.cpp:117. */
    public static String getAppDataFolder() {
        return gAppDataFolder;
    }

    /** Ported from src/SexyAppFramework/Common.cpp:122. */
    public static void setAppDataFolder(String thePath) {
        gAppDataFolder = thePath == null ? "" : thePath;
    }

    /** Ported from src/SexyAppFramework/Common.cpp:127. */
    public static String getAppDataPath(String theRelativePath) {
        return pathToU8(pathFromU8(gAppDataFolder).resolve(pathFromU8(theRelativePath)));
    }

    /** Ported from src/SexyAppFramework/Common.cpp:132. */
    public static String getResourceFolder() {
        return gResourceFolderStr;
    }

    /** Ported from src/SexyAppFramework/Common.cpp:137. */
    public static void setResourceFolder(String thePath) {
        gResourceFolder = thePath == null ? "" : thePath;
        gResourceFolderStr = pathToU8(pathFromU8(gResourceFolder));
    }

    /** Ported from src/SexyAppFramework/Common.cpp:143. */
    public static String getResourcePath(String theRelativePath) {
        return pathToU8(pathFromU8(gResourceFolder).resolve(pathFromU8(theRelativePath)));
    }

    // ------------------------------------------------------------------
    // String helpers (Common.cpp:148-263 / 519-572)
    // ------------------------------------------------------------------

    /**
     * Ported from src/SexyAppFramework/Common.cpp:148.
     *
     * <p>PORT-NOTE: upstream lowercases byte-wise with {@code std::toupper} in the "C" locale, which
     * only touches ASCII {@code a-z}; this implementation is ASCII-only for the same reason (and so
     * never changes the UTF-8 byte length of the string).
     */
    public static String stringToUpper(String theString) {
        StringBuilder aResult = new StringBuilder(theString.length());
        for (int i = 0; i < theString.length(); i++) {
            aResult.append(toUpperAscii(theString.charAt(i)));
        }
        return aResult.toString();
    }

    /** Ported from src/SexyAppFramework/Common.cpp:156. Same PORT-NOTE as {@link #stringToUpper}. */
    public static String stringToLower(String theString) {
        StringBuilder aResult = new StringBuilder(theString.length());
        for (int i = 0; i < theString.length(); i++) {
            aResult.append(toLowerAscii(theString.charAt(i)));
        }
        return aResult.toString();
    }

    /** Ported from src/SexyAppFramework/Common.cpp:519. */
    public static String upper(String theData) {
        return stringToUpper(theData);
    }

    /** Ported from src/SexyAppFramework/Common.cpp:524. */
    public static String lower(String theData) {
        return stringToLower(theData);
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:190 ({@code inlineLTrim}, default chars " \t\r\n").
     *
     * <p>Upstream mutates the argument; Java strings are immutable, so the trimmed value is returned.
     */
    public static String inlineLTrim(String theData) {
        return inlineLTrim(theData, " \t\r\n");
    }

    /** Ported from src/SexyAppFramework/Common.h:190. */
    public static String inlineLTrim(String theData, String theChars) {
        int aStart = 0;
        while (aStart < theData.length() && theChars.indexOf(theData.charAt(aStart)) >= 0) {
            aStart++;
        }
        return theData.substring(aStart);
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:195 ({@code inlineRTrim}, default chars " \t\r\n").
     *
     * <p>Upstream {@code resize(find_last_not_of(theChars) + 1)}: an all-trimmed string becomes empty
     * ({@code std::string::npos + 1 == 0}).
     */
    public static String inlineRTrim(String theData) {
        return inlineRTrim(theData, " \t\r\n");
    }

    /** Ported from src/SexyAppFramework/Common.h:195. */
    public static String inlineRTrim(String theData, String theChars) {
        int anEnd = theData.length();
        while (anEnd > 0 && theChars.indexOf(theData.charAt(anEnd - 1)) >= 0) {
            anEnd--;
        }
        return theData.substring(0, anEnd);
    }

    /** Ported from src/SexyAppFramework/Common.h:200 ({@code inlineTrim}, default chars " \t\r\n"). */
    public static String inlineTrim(String theData) {
        return inlineTrim(theData, " \t\r\n");
    }

    /** Ported from src/SexyAppFramework/Common.h:200. */
    public static String inlineTrim(String theData, String theChars) {
        return inlineLTrim(inlineRTrim(theData, theChars), theChars);
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:164.
     *
     * <p>Unicode-aware trim: {@code IsUnicodeSpace} is applied to the decoded code points on both
     * ends. Upstream decodes UTF-8 and aborts on an invalid byte; Java strings are always valid
     * UTF-16, which corresponds to valid UTF-8 for all code points.
     */
    public static String trim(String theString) {
        if (theString.isEmpty()) {
            return theString;
        }

        int aStart = 0;
        while (aStart < theString.length()) {
            int aChar = theString.codePointAt(aStart);
            if (!isUnicodeSpace(aChar)) {
                break;
            }
            aStart += Character.charCount(aChar);
        }

        if (aStart >= theString.length()) {
            return "";
        }

        int anEnd = theString.length();
        while (anEnd > aStart) {
            int aChar = theString.codePointBefore(anEnd);
            if (!isUnicodeSpace(aChar)) {
                break;
            }
            anEnd -= Character.charCount(aChar);
        }

        return theString.substring(aStart, anEnd);
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:202 ({@code bool StringToInt(const std::string&amp;,
     * int*)}).
     *
     * <p>Uses {@code std::strtol} semantics with base 0: optional leading ASCII whitespace, an
     * optional {@code +}/{@code -} sign, then {@code 0x}/{@code 0X} hex, a leading {@code 0} octal or
     * decimal. The whole string must be consumed or the parse fails (upstream then leaves 0).
     *
     * <p>PORT-NOTE: overflow saturates to {@code LONG_MAX}/{@code LONG_MIN} exactly like {@code strtol}
     * before the upstream {@code (int)} truncation, so e.g. {@code "9223372036854775808"} becomes -1.
     *
     * @return the parsed value, or {@code null} if nothing was parsed (upstream returned {@code false})
     */
    public static Integer stringToInt(String theString) {
        if (theString == null || theString.isEmpty()) {
            return null;
        }

        int aLength = theString.length();
        int i = 0;
        while (i < aLength && isAsciiSpace(theString.charAt(i))) {
            i++;
        }

        int aSign = 1;
        if (i < aLength && (theString.charAt(i) == '+' || theString.charAt(i) == '-')) {
            if (theString.charAt(i) == '-') {
                aSign = -1;
            }
            i++;
        }

        int aBase = 10;
        if (i < aLength && theString.charAt(i) == '0') {
            if (i + 2 < aLength && (theString.charAt(i + 1) == 'x' || theString.charAt(i + 1) == 'X')
                    && digitValue(theString.charAt(i + 2), 16) >= 0) {
                aBase = 16;
                i += 2;
            } else {
                aBase = 8;
            }
        }

        if (i >= aLength) {
            return null;
        }

        long anAccumulator = 0;
        boolean anOverflow = false;
        for (; i < aLength; i++) {
            int aDigit = digitValue(theString.charAt(i), aBase);
            if (aDigit < 0) {
                return null;
            }
            if (!anOverflow) {
                if (anAccumulator > (Long.MAX_VALUE - aDigit) / aBase) {
                    anOverflow = true;
                } else {
                    anAccumulator = anAccumulator * aBase + aDigit;
                }
            }
        }

        long aValue;
        if (anOverflow) {
            aValue = aSign < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        } else {
            aValue = aSign < 0 ? -anAccumulator : anAccumulator;
        }
        return (int) aValue;
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:220 ({@code bool StringToDouble(const std::string&amp;,
     * double*)}).
     *
     * <p>Uses {@code std::strtod} semantics: optional leading ASCII whitespace, an optional sign, then
     * either {@code inf}/{@code infinity}, {@code nan}[{@code (…)}], a hex float ({@code 0x1.8p1}) or a
     * decimal float with an optional exponent. The whole string must be consumed or the parse fails.
     *
     * @return the parsed value, or {@code null} if nothing was parsed (upstream returned {@code false})
     */
    public static Double stringToDouble(String theString) {
        if (theString == null || theString.isEmpty()) {
            return null;
        }

        int aLength = theString.length();
        int i = 0;
        while (i < aLength && isAsciiSpace(theString.charAt(i))) {
            i++;
        }

        int anNumberStart = i;
        int aSign = 1;
        if (i < aLength && (theString.charAt(i) == '+' || theString.charAt(i) == '-')) {
            if (theString.charAt(i) == '-') {
                aSign = -1;
            }
            i++;
        }

        if (matchesIgnoreCase(theString, i, "infinity")) {
            i += 8;
            return i == aLength ? aSign * Double.POSITIVE_INFINITY : null;
        }
        if (matchesIgnoreCase(theString, i, "inf")) {
            i += 3;
            return i == aLength ? aSign * Double.POSITIVE_INFINITY : null;
        }
        if (matchesIgnoreCase(theString, i, "nan")) {
            i += 3;
            if (i < aLength && theString.charAt(i) == '(') {
                int aClose = theString.indexOf(')', i);
                if (aClose < 0) {
                    return null;
                }
                i = aClose + 1;
            }
            return i == aLength ? Double.NaN : null;
        }

        if (i + 1 < aLength && theString.charAt(i) == '0'
                && (theString.charAt(i + 1) == 'x' || theString.charAt(i + 1) == 'X')) {
            int p = i + 2;
            int aDigits = 0;
            while (p < aLength && digitValue(theString.charAt(p), 16) >= 0) {
                p++;
                aDigits++;
            }
            if (p < aLength && theString.charAt(p) == '.') {
                p++;
                while (p < aLength && digitValue(theString.charAt(p), 16) >= 0) {
                    p++;
                    aDigits++;
                }
            }
            if (aDigits == 0) {
                return null;
            }
            int aMantissaEnd = p;
            int anExponent = 0;
            if (p < aLength && (theString.charAt(p) == 'p' || theString.charAt(p) == 'P')) {
                int q = p + 1;
                int anExponentSign = 1;
                if (q < aLength && (theString.charAt(q) == '+' || theString.charAt(q) == '-')) {
                    if (theString.charAt(q) == '-') {
                        anExponentSign = -1;
                    }
                    q++;
                }
                int anExponentDigits = 0;
                long anExponentValue = 0;
                while (q < aLength && isDigit(theString.charAt(q))) {
                    anExponentValue = anExponentValue * 10 + (theString.charAt(q) - '0');
                    q++;
                    anExponentDigits++;
                }
                if (anExponentDigits > 0) {
                    p = q;
                    anExponent = (int) (anExponentSign * anExponentValue);
                }
            }
            if (p != aLength) {
                return null;
            }
            // Java requires the 'p' exponent, C does not (defaults to 0).
            double aValue = Double.parseDouble(
                    "0x" + theString.substring(i + 2, aMantissaEnd) + "p" + anExponent);
            return aSign * aValue;
        }

        int p = i;
        int aDigits = 0;
        while (p < aLength && isDigit(theString.charAt(p))) {
            p++;
            aDigits++;
        }
        if (p < aLength && theString.charAt(p) == '.') {
            p++;
            while (p < aLength && isDigit(theString.charAt(p))) {
                p++;
                aDigits++;
            }
        }
        if (aDigits == 0) {
            return null;
        }
        if (p < aLength && (theString.charAt(p) == 'e' || theString.charAt(p) == 'E')) {
            int q = p + 1;
            if (q < aLength && (theString.charAt(q) == '+' || theString.charAt(q) == '-')) {
                q++;
            }
            int anExponentDigits = 0;
            while (q < aLength && isDigit(theString.charAt(q))) {
                q++;
                anExponentDigits++;
            }
            if (anExponentDigits > 0) {
                p = q;
            }
        }
        if (p != aLength) {
            return null;
        }
        return Double.parseDouble(theString.substring(anNumberStart, p));
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:239.
     *
     * <p>PORT-NOTE: {@code (unsigned int)(-theValue)} on {@code INT_MIN} wraps to {@code 0x80000000}
     * in C++; {@link Integer#toUnsignedLong(int)} reproduces that after the Java {@code -theValue}
     * wrap-around.
     */
    public static String commaSeperate(int theValue) {
        if (theValue == 0) {
            return "0";
        }

        boolean isNeg = theValue < 0;
        long aCurValue = isNeg ? Integer.toUnsignedLong(-theValue) : theValue;

        StringBuilder aCurString = new StringBuilder();
        int aPlace = 0;
        while (aCurValue > 0) {
            if ((aPlace != 0) && (aPlace % 3 == 0)) {
                aCurString.insert(0, ',');
            }
            aCurString.insert(0, (char) ('0' + (int) (aCurValue % 10)));
            aCurValue /= 10;
            aPlace++;
        }

        if (isNeg) {
            aCurString.insert(0, '-');
        }

        return aCurString.toString();
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:392.
     *
     * <p>Replaces every {@code %name%} with {@code theDefinesMap[name]} (missing names become the
     * empty string); a single unmatched {@code %} stops the scan, exactly like upstream.
     */
    public static String evaluate(String theString, Map<String, String> theDefinesMap) {
        String anEvaluatedString = theString;

        for (;;) {
            int aPercentPos = anEvaluatedString.indexOf('%');
            if (aPercentPos < 0) {
                break;
            }

            int aSecondPercentPos = anEvaluatedString.indexOf('%', aPercentPos + 1);
            if (aSecondPercentPos < 0) {
                break;
            }

            String aName = anEvaluatedString.substring(aPercentPos + 1, aSecondPercentPos);
            String aValue = theDefinesMap.get(aName);
            if (aValue == null) {
                aValue = "";
            }

            anEvaluatedString = anEvaluatedString.substring(0, aPercentPos)
                    + aValue
                    + anEvaluatedString.substring(aSecondPercentPos + 1);
        }

        return anEvaluatedString;
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:422.
     *
     * <p>Unknown entities (or {@code &} without a following {@code ;}) collapse to a literal
     * {@code &} and the rest of the entity text is skipped, exactly like upstream.
     */
    public static String xmlDecodeString(String theString) {
        if (theString.indexOf('&') < 0) {
            return theString;
        }

        StringBuilder aNewString = new StringBuilder(theString.length());
        for (int i = 0; i < theString.length(); i++) {
            char c = theString.charAt(i);

            if (c == '&') {
                int aSemiPos = theString.indexOf(';', i);
                if (aSemiPos >= 0) {
                    String anEntName = theString.substring(i + 1, aSemiPos);
                    i = aSemiPos;

                    switch (anEntName) {
                        case "lt":
                            c = '<';
                            break;
                        case "amp":
                            c = '&';
                            break;
                        case "gt":
                            c = '>';
                            break;
                        case "quot":
                            c = '"';
                            break;
                        case "apos":
                            c = '\'';
                            break;
                        case "nbsp":
                            c = ' ';
                            break;
                        case "cr":
                            c = '\n';
                            break;
                        default:
                            break;
                    }
                }
            }

            aNewString.append(c);
        }

        return aNewString.toString();
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:466.
     *
     * <p>Every space after the first one in a run is encoded as {@code &nbsp;}.
     */
    public static String xmlEncodeString(String theString) {
        StringBuilder aNewString = new StringBuilder(theString.length() * 2);
        boolean hasSpace = false;

        for (int i = 0; i < theString.length(); i++) {
            char c = theString.charAt(i);

            if (c == ' ') {
                if (hasSpace) {
                    aNewString.append("&nbsp;");
                    continue;
                }
                hasSpace = true;
            } else {
                hasSpace = false;
            }

            switch (c) {
                case '<':
                    aNewString.append("&lt;");
                    break;
                case '&':
                    aNewString.append("&amp;");
                    break;
                case '>':
                    aNewString.append("&gt;");
                    break;
                case '"':
                    aNewString.append("&quot;");
                    break;
                case '\'':
                    aNewString.append("&apos;");
                    break;
                case '\n':
                    aNewString.append("&cr;");
                    break;
                default:
                    aNewString.append(c);
                    break;
            }
        }

        return aNewString.toString();
    }

    /** Ported from src/SexyAppFramework/Common.cpp:529. Returns the byte index of the match, or -1. */
    public static int strFindNoCase(String theStr, String theFind) {
        int aLen1 = theStr.length();
        int aLen2 = theFind.length();
        int cp = 0;
        while (cp < aLen1) {
            int p1 = cp;
            int p2 = 0;
            while (p1 < aLen1 && p2 < aLen2) {
                if (toLowerAscii(theStr.charAt(p1)) != toLowerAscii(theFind.charAt(p2))) {
                    break;
                }
                p1++;
                p2++;
            }
            if (p2 == aLen2) {
                return p1 - aLen2;
            }
            cp++;
        }
        return -1;
    }

    /** Ported from src/SexyAppFramework/Common.cpp:555 (default {@code maxLength = 10000000}). */
    public static boolean strPrefixNoCase(String theStr, String thePrefix) {
        return strPrefixNoCase(theStr, thePrefix, 10000000);
    }

    /** Ported from src/SexyAppFramework/Common.cpp:555. */
    public static boolean strPrefixNoCase(String theStr, String thePrefix, int maxLength) {
        char c1 = 0;
        char c2 = 0;
        int i;
        for (i = 0; i < maxLength; i++) {
            c1 = toLowerAscii(i < theStr.length() ? theStr.charAt(i) : 0);
            c2 = toLowerAscii(i < thePrefix.length() ? thePrefix.charAt(i) : 0);

            if (c1 == 0 || c2 == 0) {
                break;
            }

            if (c1 != c2) {
                return false;
            }
        }

        return c2 == 0 || i == maxLength;
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:462 ({@code StringLessNoCase}, used as a
     * {@code std::map}/{@code std::set} comparator).
     *
     * <p>PORT-NOTE: {@code strcasecmp} compares raw bytes unsigned; this comparator lowers ASCII case
     * and compares UTF-16 code units, which is identical for the ASCII keys used by the game.
     */
    public static final class StringLessNoCase implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            return strCaseCompare(s1, s2);
        }
    }

    /** Ported from src/SexyAppFramework/Common.h:462. */
    public static final Comparator<String> STRING_LESS_NO_CASE = new StringLessNoCase();

    /** Ported from src/SexyAppFramework/Common.h:462 ({@code strcasecmp(...) < 0}). */
    public static int strCaseCompare(String s1, String s2) {
        int aLen1 = s1.length();
        int aLen2 = s2.length();
        int aLen = Math.min(aLen1, aLen2);
        for (int i = 0; i < aLen; i++) {
            char c1 = toLowerAscii(s1.charAt(i));
            char c2 = toLowerAscii(s2.charAt(i));
            if (c1 != c2) {
                return c1 - c2;
            }
        }
        return aLen1 - aLen2;
    }

    // ------------------------------------------------------------------
    // Files and paths (Common.cpp:265-390)
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/Common.cpp:265. */
    public static String getCurDir() {
        try {
            return pathToU8(Paths.get("").toAbsolutePath());
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Ported from src/SexyAppFramework/Common.cpp:274. */
    public static String getFullPath(String theRelPath) {
        return getPathFrom(theRelPath, getCurDir());
    }

    /** Ported from src/SexyAppFramework/Common.cpp:279. */
    public static String getPathFrom(String theRelPath, String theDir) {
        Path aRelPath = pathFromU8(theRelPath);
        if (isPathRooted(theRelPath)) {
            return pathToU8(aRelPath.normalize());
        }

        Path aBaseDir;
        if (theDir != null && !theDir.isEmpty()) {
            aBaseDir = pathFromU8(theDir);
        } else {
            try {
                aBaseDir = Paths.get("").toAbsolutePath();
            } catch (RuntimeException e) {
                return pathToU8(aRelPath.normalize());
            }
        }

        return pathToU8(aBaseDir.resolve(aRelPath).normalize());
    }

    /** Ported from src/SexyAppFramework/Common.cpp:299. */
    public static boolean isPathRooted(String thePath) {
        if (thePath == null || thePath.isEmpty()) {
            return false;
        }
        return pathFromU8(thePath).isAbsolute();
    }

    /** Ported from src/SexyAppFramework/Common.cpp:330. */
    public static boolean allowAllAccess(String theFileName) {
        return false;
    }

    /** Ported from src/SexyAppFramework/Common.cpp:335. */
    public static boolean deltree(String thePath) {
        Path aPath = pathFromU8(thePath);
        if (!Files.exists(aPath)) {
            return false;
        }

        try {
            deleteRecursively(aPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Ported from src/SexyAppFramework/Common.cpp:346. */
    public static boolean fileExists(String theFileName) {
        return Files.exists(pathFromU8(theFileName));
    }

    /** Ported from src/SexyAppFramework/Common.cpp:352. */
    public static void mkDir(String theDir) {
        try {
            Files.createDirectories(pathFromU8(theDir));
        } catch (IOException e) {
            // Upstream ignores the error_code too.
        }
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:358.
     *
     * <p>Mirrors {@code std::filesystem::path}: an empty filename component (trailing separator, root,
     * or an empty path) yields {@code ""}.
     */
    public static String getFileName(String thePath) {
        return getFileName(thePath, false);
    }

    /** Ported from src/SexyAppFramework/Common.cpp:358. */
    public static String getFileName(String thePath, boolean noExtension) {
        String aName = fileName(thePath);
        if (aName == null) {
            return "";
        }
        return noExtension ? stem(aName) : aName;
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:370.
     *
     * <p>{@code withSlash} appends {@code '/'} (upstream {@code result += '/'}), not the native
     * separator.
     */
    public static String getFileDir(String thePath) {
        return getFileDir(thePath, false);
    }

    /** Ported from src/SexyAppFramework/Common.cpp:370. */
    public static String getFileDir(String thePath, boolean withSlash) {
        String aParent = parentPath(thePath);
        if (aParent.isEmpty()) {
            return "";
        }

        String aResult = aParent;
        if (withSlash && !aResult.isEmpty()) {
            aResult += '/';
        }

        return aResult;
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:384.
     *
     * <p>Upstream returns {@code path(theDirectory).lexically_normal()}: the empty path is returned
     * verbatim, everything else is lexically normalized (trailing slashes and {@code .}/{@code ..}
     * components removed) without touching the filesystem.
     *
     * <p>PORT-NOTE: unlike {@link #pathToU8} this deliberately keeps the host's native separators.
     * Its inputs are not asset paths: {@code SexyAppBase} passes Windows registry key names
     * ({@code "SOFTWARE\\" + mRegKey}, see {@code RegEmu}) whose {@code '\'} is a registry separator,
     * and on POSIX {@link java.nio.file.Path} treats {@code '\'} as an ordinary character, exactly
     * like upstream's non-Windows {@code PathFromU8}. Rewriting it to {@code '/'} would silently
     * change the keys persisted in {@code userdata/registry.regemu}. The resource-manifest callers
     * ({@code ResourceManager.parseSetDefaults}) pass {@code '/'}-free paths authored in
     * {@code properties/resources.xml}, so they are unaffected either way.
     */
    public static String removeTrailingSlash(String theDirectory) {
        if (theDirectory == null || theDirectory.isEmpty()) {
            return theDirectory == null ? "" : theDirectory;
        }

        try {
            return Paths.get(theDirectory).normalize().toString();
        } catch (RuntimeException e) {
            return theDirectory;
        }
    }

    // ------------------------------------------------------------------
    // Block memory helpers (Common.cpp:574-600)
    // ------------------------------------------------------------------

    /**
     * Ported from src/SexyAppFramework/Common.cpp:574.
     *
     * <p>{@code void*&} (a movable pointer) maps to {@link ByteBuffer}: {@code theSize} bytes are
     * copied from {@code theSrc} to {@code theDst} and both positions advance past them.
     */
    public static void sMemR(ByteBuffer theSrc, ByteBuffer theDst, int theSize) {
        int aSrcPos = theSrc.position();
        int aDstPos = theDst.position();
        for (int i = 0; i < theSize; i++) {
            theDst.put(aDstPos + i, theSrc.get(aSrcPos + i));
        }
        theSrc.position(aSrcPos + theSize);
        theDst.position(aDstPos + theSize);
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:580.
     *
     * <p>PORT-NOTE: upstream reads a native {@code size_t} (8 bytes on 64-bit platforms) before the
     * UTF-8 bytes; the buffer's own byte order is used here. Unused by the upstream game code.
     */
    public static String sMemRStr(ByteBuffer theSrc) {
        long aStrLen = theSrc.getLong();
        byte[] aBuffer = new byte[(int) aStrLen];
        theSrc.get(aBuffer);
        return new String(aBuffer, StandardCharsets.UTF_8);
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:589.
     *
     * <p>Mirror image of {@link #sMemR}; both positions advance.
     */
    public static void sMemW(ByteBuffer theDst, ByteBuffer theSrc, int theSize) {
        sMemR(theSrc, theDst, theSize);
    }

    /**
     * Ported from src/SexyAppFramework/Common.cpp:595.
     *
     * <p>PORT-NOTE: writes a native {@code size_t} (8 bytes on 64-bit platforms, the buffer's byte
     * order) followed by the UTF-8 bytes, without a terminator. Unused by the upstream game code.
     */
    public static void sMemWStr(ByteBuffer theDst, String theString) {
        byte[] aBytes = theString.getBytes(StandardCharsets.UTF_8);
        theDst.putLong(aBytes.length);
        theDst.put(aBytes);
    }

    // ------------------------------------------------------------------
    // Unicode helpers (Common.h:52 / 308-362)
    // ------------------------------------------------------------------

    /**
     * Ported from src/SexyAppFramework/Common.h:52 ({@code static bool IsUnicodeSpace(char32_t)}).
     */
    public static boolean isUnicodeSpace(int theChar) {
        switch (theChar) {
            case 0x0085: case 0x00A0: case 0x1680:
            case 0x2000: case 0x2001: case 0x2002: case 0x2003:
            case 0x2004: case 0x2005: case 0x2006: case 0x2007:
            case 0x2008: case 0x2009: case 0x200A:
            case 0x2028: case 0x2029: case 0x202F: case 0x205F:
            case 0x3000:
                return true;
            default:
                return theChar < 0x80 && isAsciiSpace((char) theChar);
        }
    }

    /** Ported from src/SexyAppFramework/Common.h:308. */
    public static boolean isOpeningPunctuation(int theChar) {
        switch (theChar) {
            case 0x3008: case 0x300A: case 0x300C: case 0x300E:
            case 0x3010: case 0x3014: case 0x3016: case 0x3018:
            case 0x301A:
            case 0xFF08: case 0xFF3B: case 0xFF5B:
            case 0x2018: case 0x201A: case 0x201B: case 0x201C:
                return true;
            default:
                return false;
        }
    }

    /** Ported from src/SexyAppFramework/Common.h:324. */
    public static boolean isClosingPunctuation(int theChar) {
        switch (theChar) {
            case 0x3009: case 0x300B: case 0x300D: case 0x300F:
            case 0x3011: case 0x3015: case 0x3017: case 0x3019:
            case 0x301B:
            case 0xFF09: case 0xFF3D: case 0xFF5D:
            case 0x2019: case 0x201D:
            case 0x3001: case 0x3002:
            case 0xFF0C: case 0xFF0E:
            case 0xFF01: case 0xFF1F:
            case 0xFF1A: case 0xFF1B:
                return true;
            default:
                return false;
        }
    }

    /** Ported from src/SexyAppFramework/Common.h:344. */
    public static boolean isAutoBreakChar(int theChar) {
        if (theChar < 0x80) {
            return false;
        }
        return (theChar >= 0x2018 && theChar <= 0x201D) ||  // Curly quotes
                (theChar >= 0x2600 && theChar <= 0x27BF) ||  // Misc Symbols, Dingbats
                (theChar >= 0x3000 && theChar <= 0x303F) ||  // CJK Symbols and Punctuation
                (theChar >= 0x3040 && theChar <= 0x309F) ||  // Hiragana
                (theChar >= 0x30A0 && theChar <= 0x30FF) ||  // Katakana
                (theChar >= 0x3400 && theChar <= 0x4DBF) ||  // CJK Extension A
                (theChar >= 0x4E00 && theChar <= 0x9FFF) ||  // CJK Unified Ideographs
                (theChar >= 0xAC00 && theChar <= 0xD7AF) ||  // Hangul Syllables
                (theChar >= 0xF900 && theChar <= 0xFAFF) ||  // CJK Compatibility Ideographs
                (theChar >= 0xFE30 && theChar <= 0xFE4F) ||  // CJK Compatibility Forms
                (theChar >= 0xFF01 && theChar <= 0xFF60) ||  // Fullwidth Forms
                (theChar >= 0x1F300 && theChar <= 0x1FAFF) || // Emoji Symbols & Pictographs
                (theChar >= 0x20000 && theChar <= 0x2FA1F);  // CJK Extension B-F & Supplements
    }

    // ------------------------------------------------------------------
    // UTF-8 helpers (Common.h:207-306)
    // ------------------------------------------------------------------

    /**
     * Ported from src/SexyAppFramework/Common.h:207 ({@code bool UTF8DecodeNext(std::string_view,
     * size_t&amp;, char32_t&amp;)}).
     *
     * <p>The byte offset is advanced only on success, exactly like upstream. Upstream returns
     * {@code bool} and assigns the code point through {@code theOutChar}; the code point is returned
     * here and {@code -1} stands for the upstream {@code false} (0 is a valid code point, so it cannot
     * be the sentinel).
     *
     * @param theString the UTF-8 bytes
     * @param theOffset single-element array holding the current byte offset (advanced in place)
     * @return the decoded code point, or -1 on end-of-string/invalid UTF-8
     */
    public static int utf8DecodeNext(byte[] theString, int[] theOffset) {
        int aPos = theOffset[0];
        if (aPos >= theString.length) {
            return -1;
        }

        int aFirst = theString[aPos] & 0xFF;
        if (aFirst < 0x80) {
            theOffset[0] = aPos + 1;
            return aFirst;
        }

        int aLength;
        if ((aFirst & 0xE0) == 0xC0) {
            aLength = 2;
        } else if ((aFirst & 0xF0) == 0xE0) {
            aLength = 3;
        } else if ((aFirst & 0xF8) == 0xF0) {
            aLength = 4;
        } else {
            return -1;
        }

        if (aPos + aLength > theString.length) {
            return -1;
        }

        for (int i = 1; i < aLength; i++) {
            if ((theString[aPos + i] & 0xC0) != 0x80) {
                return -1;
            }
        }

        int aValue;
        if (aLength == 2) {
            aValue = ((aFirst & 0x1F) << 6)
                    | (theString[aPos + 1] & 0x3F);
        } else if (aLength == 3) {
            aValue = ((aFirst & 0x0F) << 12)
                    | ((theString[aPos + 1] & 0x3F) << 6)
                    | (theString[aPos + 2] & 0x3F);
        } else {
            aValue = ((aFirst & 0x07) << 18)
                    | ((theString[aPos + 1] & 0x3F) << 12)
                    | ((theString[aPos + 2] & 0x3F) << 6)
                    | (theString[aPos + 3] & 0x3F);
        }

        theOffset[0] = aPos + aLength;
        return aValue;
    }

    /** Ported from src/SexyAppFramework/Common.h:253. */
    public static int utf8NextBoundary(byte[] theString, int theOffset) {
        if (theOffset >= theString.length) {
            return theString.length;
        }
        int[] aNext = {theOffset};
        if (utf8DecodeNext(theString, aNext) >= 0) {
            return aNext[0];
        }
        return theOffset + 1;
    }

    /** Ported from src/SexyAppFramework/Common.h:265. */
    public static int utf8PrevBoundary(byte[] theString, int theOffset) {
        if (theOffset == 0 || theString.length == 0) {
            return 0;
        }
        int aPos = Math.min(theOffset, theString.length) - 1;
        while (aPos > 0 && (theString[aPos] & 0xC0) == 0x80) {
            aPos--;
        }
        return aPos;
    }

    /** Ported from src/SexyAppFramework/Common.h:276. */
    public static int utf8ByteOffsetForCodePoint(byte[] theString, int theCodePointIndex) {
        int aOffset = 0;
        int[] aNext = {0};
        for (int i = 0; i < theCodePointIndex; i++) {
            aNext[0] = aOffset;
            if (utf8DecodeNext(theString, aNext) < 0) {
                return theString.length;
            }
            aOffset = aNext[0];
        }
        return aOffset;
    }

    /** Ported from src/SexyAppFramework/Common.h:289. */
    public static int utf8CodePointCount(byte[] theString) {
        int aCount = 0;
        int aOffset = 0;
        int[] aNext = {0};
        for (;;) {
            aNext[0] = aOffset;
            if (utf8DecodeNext(theString, aNext) < 0) {
                break;
            }
            aOffset = aNext[0];
            aCount++;
        }
        return aCount;
    }

    /** Ported from src/SexyAppFramework/Common.h:300. */
    public static int utf8CodePointAt(byte[] theString, int theOffset) {
        int[] aNext = {theOffset};
        int aChar = utf8DecodeNext(theString, aNext);
        return aChar < 0 ? 0 : aChar;
    }

    // ------------------------------------------------------------------
    // Byte swap / endian conversion (Common.h:380-460)
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/Common.h:381. */
    public static short byteSwap16(short v) {
        return Short.reverseBytes(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:395. */
    public static int byteSwap32(int v) {
        return Integer.reverseBytes(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:412. */
    public static long byteSwap64(long v) {
        return Long.reverseBytes(v);
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:435 ({@code EndianConvert&lt;T, TargetEndian&gt;}).
     *
     * <p>PORT-NOTE: the C++ helpers are no-ops on a little-endian build and swap otherwise; the same
     * host check is done at runtime here (all mainstream JVMs are little-endian, so these are
     * identities in practice, exactly like the upstream x86/ARM builds).
     */
    private static final boolean NATIVE_LITTLE_ENDIAN =
            ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    /** Ported from src/SexyAppFramework/Common.h:449. */
    public static short fromLE16(short v) {
        return NATIVE_LITTLE_ENDIAN ? v : byteSwap16(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:450. */
    public static short toLE16(short v) {
        return fromLE16(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:451. */
    public static int fromLE32(int v) {
        return NATIVE_LITTLE_ENDIAN ? v : byteSwap32(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:452. */
    public static int toLE32(int v) {
        return fromLE32(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:453. */
    public static long fromLE64(long v) {
        return NATIVE_LITTLE_ENDIAN ? v : byteSwap64(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:454. */
    public static long toLE64(long v) {
        return fromLE64(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:455. */
    public static short fromBE16(short v) {
        return NATIVE_LITTLE_ENDIAN ? byteSwap16(v) : v;
    }

    /** Ported from src/SexyAppFramework/Common.h:456. */
    public static short toBE16(short v) {
        return fromBE16(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:457. */
    public static int fromBE32(int v) {
        return NATIVE_LITTLE_ENDIAN ? byteSwap32(v) : v;
    }

    /** Ported from src/SexyAppFramework/Common.h:458. */
    public static int toBE32(int v) {
        return fromBE32(v);
    }

    /** Ported from src/SexyAppFramework/Common.h:459. */
    public static long fromBE64(long v) {
        return NATIVE_LITTLE_ENDIAN ? byteSwap64(v) : v;
    }

    /** Ported from src/SexyAppFramework/Common.h:460. */
    public static long toBE64(long v) {
        return fromBE64(v);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Ported from src/SexyAppFramework/Common.cpp:47. */
    private static char toLowerAscii(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    /** ASCII {@code toupper} in the "C" locale (upstream {@code std::toupper}). */
    private static char toUpperAscii(char c) {
        return (c >= 'a' && c <= 'z') ? (char) (c - 32) : c;
    }

    /** C {@code isspace} for ASCII (upstream {@code std::isspace} on an ASCII char). */
    private static boolean isAsciiSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == 0x0C || c == '\r';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int digitValue(char c, int base) {
        if (c >= '0' && c <= '9') {
            int v = c - '0';
            return v < base ? v : -1;
        }
        if (c >= 'a' && c <= 'z') {
            int v = c - 'a' + 10;
            return v < base ? v : -1;
        }
        if (c >= 'A' && c <= 'Z') {
            int v = c - 'A' + 10;
            return v < base ? v : -1;
        }
        return -1;
    }

    private static boolean matchesIgnoreCase(String theString, int theOffset, String theWhat) {
        if (theOffset + theWhat.length() > theString.length()) {
            return false;
        }
        for (int i = 0; i < theWhat.length(); i++) {
            if (toLowerAscii(theString.charAt(theOffset + i)) != theWhat.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:365-374 ({@code PathFromU8}).
     *
     * <p>PORT-NOTE: every string path the framework produces is in the portable (POSIX/generic)
     * form, i.e. {@code '/'}-separated, on <em>every</em> host — see {@link #toPortablePath} for
     * why. This is the input half of that bridge, so a Windows-style string
     * ({@code "data\Foo"}) means the same thing here as it does on Windows.
     */
    private static Path pathFromU8(String s) {
        return Paths.get(toPortablePath(s));
    }

    /**
     * Ported from src/SexyAppFramework/Common.h:365-374 ({@code PathToU8}): on Windows upstream
     * hands back {@code path::generic_u8string()} (forward slashes), and on POSIX
     * {@code path::string()} (also forward slashes). This is the output half of the bridge, so the
     * returned string never contains the host's native separator.
     */
    private static String pathToU8(Path p) {
        return toPortablePath(p.toString());
    }

    /**
     * Converts a host path string into the framework's portable path form: {@code '\'} becomes
     * {@code '/'}, on every host.
     *
     * <p>Upstream only reaches this state on Windows ({@code PathToU8} uses
     * {@code generic_u8string()}), yet the surrounding framework string code is {@code '/'}-based by
     * construction on all platforms: {@code GetFileDir(..., withSlash)} appends {@code '/'},
     * {@code ImageLib::GetImage} probes the {@code '_'} companion with {@code rfind('/')}
     * (ImageLib.cpp:1259-1293), the pak member table stores {@code '/'} names, and resource paths
     * come from the {@code '/'}-authored resource manifests. Emitting native separators (the old
     * {@code Path.toString()}) therefore injected {@code '\'} into that {@code '/'}-speaking world on
     * Windows only, which broke e.g. the {@code data\BrianneTod32Outline} font image lookup (see
     * docs/VERIFICATION.md §59).
     *
     * <p>Converting unconditionally — instead of following {@code File.separatorChar} — keeps the
     * two directions of the bridge idempotent and makes path handling independent of the host OS: a
     * path string means the same thing on macOS, Linux and Windows, and the Windows behaviour stays
     * covered by tests running on any host. {@link java.nio.file.Path} accepts {@code '/'} on
     * Windows, so real file access is unaffected; the only cost is that a POSIX file name containing
     * a literal {@code '\'} can no longer be named — which the framework never could address anyway.
     */
    private static String toPortablePath(String thePath) {
        return thePath.indexOf('\\') == -1 ? thePath : thePath.replace('\\', '/');
    }

    /**
     * Upstream relies on {@code std::filesystem::path} being separator-aware on the build platform.
     * The port's path strings are portable ({@link #toPortablePath}), so both separators are
     * separators on every host — otherwise {@code GetFileDir}/{@code GetFileName} would disagree
     * with {@link #getPathFrom} about what a Windows-style string means.
     */
    private static boolean isPathSeparator(char c) {
        return c == '/' || c == '\\';
    }

    /**
     * {@code std::filesystem::path::filename()}: {@code null} when the path has no filename
     * component (empty path, root, or a trailing separator).
     */
    private static String fileName(String thePath) {
        if (thePath == null || thePath.isEmpty()) {
            return null;
        }
        int anEnd = thePath.length();
        if (isPathSeparator(thePath.charAt(anEnd - 1))) {
            return null;
        }
        int i = anEnd - 1;
        while (i >= 0 && !isPathSeparator(thePath.charAt(i))) {
            i--;
        }
        return thePath.substring(i + 1, anEnd);
    }

    /** {@code std::filesystem::path::stem()}: filename with the final extension removed. */
    private static String stem(String theName) {
        if (theName.equals(".") || theName.equals("..")) {
            return theName;
        }
        int aDot = theName.lastIndexOf('.');
        if (aDot > 0) {
            return theName.substring(0, aDot);
        }
        return theName;
    }

    private static String parentPath(String thePath) {
        if (thePath == null || thePath.isEmpty()) {
            return "";
        }
        int aLength = thePath.length();
        int aRootLength = 0;
        while (aRootLength < aLength && isPathSeparator(thePath.charAt(aRootLength))) {
            aRootLength++;
        }
        if (aRootLength == aLength) {
            return thePath;
        }

        int i = aLength - 1;
        while (i >= aRootLength && !isPathSeparator(thePath.charAt(i))) {
            i--;
        }
        if (i < aRootLength) {
            return thePath.substring(0, aRootLength);
        }
        return thePath.substring(0, i);
    }

    private static void deleteRecursively(Path thePath) throws IOException {
        if (Files.isDirectory(thePath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (var aStream = Files.list(thePath)) {
                for (Path aChild : aStream.toList()) {
                    deleteRecursively(aChild);
                }
            }
        }
        Files.deleteIfExists(thePath);
    }
}
