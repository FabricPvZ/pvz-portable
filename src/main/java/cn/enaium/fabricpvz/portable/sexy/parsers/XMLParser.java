package cn.enaium.fabricpvz.portable.sexy.parsers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.pak.PakFileHandle;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Ported from src/SexyAppFramework/misc/XMLParser.h:73 and src/SexyAppFramework/misc/XMLParser.cpp:62
 *
 * <p>A streaming, byte-oriented XML reader (the PopCap "one element per call" style). The scanner
 * rules are transcribed one-to-one from upstream: whitespace/quote tracking, the {@code <!-- -->}
 * and {@code <? ?>} modes, the {@code />} empty-element expansion, strict start/end tag matching,
 * and the exact set of failures ("Unexpected '&lt;'", "Unexpected '&gt;'", "Unexpected End",
 * "End 'x' Doesn't Match Start 'y'", "Unexpected End of File", "Illegal Character").
 *
 * <p>PORT-NOTE (byte vs. char strings): upstream works on {@code std::string} byte sequences. This
 * port keeps that model internally (each internal string holds one char per source byte, values
 * 0..255) so that every scanning rule is byte-exact, and converts to Java text
 * ({@link #byteStringToText}) only when a string leaves the parser through {@link XMLElement} or
 * {@link #addAttribute}: ISO-8859-1 in {@link XMLEncodingType#ASCII} mode (raw bytes kept 1:1),
 * UTF-8 otherwise (UTF-16 input is transcoded to UTF-8 by the char getters, exactly like upstream).
 */
public class XMLParser implements AutoCloseable {

    /** Upstream {@code XMLParser::XMLEncodingType}. */
    public enum XMLEncodingType {
        ASCII,
        UTF_8,
        UTF_16,
        UTF_16_LE,
        UTF_16_BE
    }

    protected String mFileName = "";
    protected String mErrorText = "";
    protected int mLineNum;
    protected PakFileHandle mFile;
    protected boolean mHasFailed;
    protected boolean mAllowComments;
    /** Upstream {@code XMLParserBuffer} used as a LIFO push-back stack, one entry per byte. */
    protected Deque<Integer> mBufferedText = new ArrayDeque<>();
    protected String mSection = "";
    /** Java stand-in for the {@code mGetCharFunc} member pointer (upstream XMLParser.h:84). */
    protected XMLEncodingType mEncoding = XMLEncodingType.UTF_8;
    protected boolean mForcedEncodingType;
    protected boolean mFirstChar;
    protected boolean mByteSwap;

    private final byte[] mOneByte = new byte[1];
    private final byte[] mTwoBytes = new byte[2];

    public XMLParser() {
        mFile = null;
        mLineNum = 0;
        mAllowComments = false;
        mEncoding = XMLEncodingType.UTF_8;
        mForcedEncodingType = false;
    }

    /**
     * Upstream {@code ~XMLParser} closes the file handle. Java has no destructor, so this is
     * exposed directly.
     */
    @Override
    public void close() {
        if (mFile != null) {
            try {
                mFile.close();
            } catch (IOException e) {
                // upstream p_fclose cannot fail
            }
            mFile = null;
        }
    }

    public void setEncodingType(XMLEncodingType theEncoding) {
        mEncoding = theEncoding;
        mForcedEncodingType = true;
    }

    protected void fail(String theErrorText) {
        mHasFailed = true;
        mErrorText = theErrorText;
    }

    protected void init() {
        mSection = "";
        mLineNum = 1;
        mHasFailed = false;
        mErrorText = "";
        mFirstChar = true;
        mByteSwap = false;
    }

    protected boolean addAttribute(XMLElement theElement, String aAttributeKey, String aAttributeValue) {
        // Upstream: insert() into the map, then overwrite when the key already exists; the
        // returned flag is whether the key was newly inserted.
        boolean aRet = theElement.mAttributes.put(aAttributeKey, byteStringToText(aAttributeValue)) == null;

        if (!"/".equals(aAttributeKey)) {
            theElement.mAttributeIteratorList.add(aAttributeKey);
        }

        return aRet;
    }

    protected boolean getAsciiChar(int[] theChar, boolean[] error) {
        int aChar = readFileByte();
        if (aChar < 0) {
            return false;
        }

        theChar[0] = aChar;
        return true;
    }

    /** EOF is not an encoding error. */
    protected boolean getUtf8Char(int[] theChar, boolean[] error) {
        int aChar = readFileByte();
        if (aChar < 0) {
            return false;
        }

        if (mFirstChar) {
            mFirstChar = false;
            if (aChar == 0xEF) {
                int b2 = readFileByte();
                int b3 = b2 < 0 ? -1 : readFileByte();
                if (b2 >= 0 && b3 >= 0) {
                    if (b2 == 0xBB && b3 == 0xBF) {
                        return getUtf8Char(theChar, error);
                    }
                    mBufferedText.addLast(b3);
                    mBufferedText.addLast(b2);
                }
            }
        }

        theChar[0] = aChar;
        return true;
    }

    protected boolean getUtf16Char(int[] theChar, boolean[] error) {
        error[0] = true;
        int aTempChar = readFile2Bytes();
        if (aTempChar < 0) {
            return false;
        }

        if (mFirstChar) {
            mFirstChar = false;
            if (aTempChar == 0xFEFF) {
                mByteSwap = false;
                return getUtf16Char(theChar, error);
            } else if (aTempChar == 0xFFFE) {
                mByteSwap = true;
                return getUtf16Char(theChar, error);
            }
        }
        if (mByteSwap) {
            aTempChar = ((aTempChar << 8) | (aTempChar >> 8)) & 0xFFFF;
        }

        int codepoint = aTempChar;
        if ((aTempChar & 0xD800) == 0xD800) {
            int aNextChar = readFile2Bytes();
            if (aNextChar < 0) {
                return false;
            }

            if (mByteSwap) {
                aNextChar = ((aNextChar << 8) | (aNextChar >> 8)) & 0xFFFF;
            }
            if ((aNextChar & 0xDC00) == 0xDC00) {
                codepoint = (((aTempChar & ~0xD800) << 10) | (aNextChar & ~0xDC00)) + 0x10000;
            } else {
                return false;
            }
        }

        return emitUtf8Char(theChar, error, codepoint);
    }

    protected boolean getUtf16LeChar(int[] theChar, boolean[] error) {
        error[0] = true;
        int aTempChar = readFile2Bytes();
        if (aTempChar < 0) {
            return false;
        }

        aTempChar = fromLE16(aTempChar);

        int codepoint = aTempChar;
        if ((aTempChar & 0xD800) == 0xD800) {
            int aNextChar = readFile2Bytes();
            if (aNextChar < 0) {
                return false;
            }

            aNextChar = fromLE16(aNextChar);
            if ((aNextChar & 0xDC00) == 0xDC00) {
                codepoint = (((aTempChar & ~0xD800) << 10) | (aNextChar & ~0xDC00)) + 0x10000;
            } else {
                return false;
            }
        }

        return emitUtf8Char(theChar, error, codepoint);
    }

    protected boolean getUtf16BeChar(int[] theChar, boolean[] error) {
        error[0] = true;
        int aTempChar = readFile2Bytes();
        if (aTempChar < 0) {
            return false;
        }

        aTempChar = fromBE16(aTempChar);

        int codepoint = aTempChar;
        if ((aTempChar & 0xD800) == 0xD800) {
            int aNextChar = readFile2Bytes();
            if (aNextChar < 0) {
                return false;
            }

            aNextChar = fromBE16(aNextChar);
            if ((aNextChar & 0xDC00) == 0xDC00) {
                codepoint = (((aTempChar & ~0xD800) << 10) | (aNextChar & ~0xDC00)) + 0x10000;
            } else {
                return false;
            }
        }

        return emitUtf8Char(theChar, error, codepoint);
    }

    /** Upstream {@code EncodeUTF8} (XMLParser.cpp:30): pushes the trailing UTF-8 bytes back. */
    private boolean emitUtf8Char(int[] theChar, boolean[] error, int codepoint) {
        int[] utf8 = new int[4];
        int len = encodeUtf8(codepoint, utf8);
        for (int i = len - 1; i >= 1; --i) {
            mBufferedText.addLast(utf8[i]);
        }

        theChar[0] = utf8[0];
        error[0] = false;
        return true;
    }

    static int encodeUtf8(int code, int[] out) {
        if (code <= 0x7F) {
            out[0] = code;
            return 1;
        }
        if (code <= 0x7FF) {
            out[0] = 0xC0 | (code >> 6);
            out[1] = 0x80 | (code & 0x3F);
            return 2;
        }
        if (code <= 0xFFFF) {
            out[0] = 0xE0 | (code >> 12);
            out[1] = 0x80 | ((code >> 6) & 0x3F);
            out[2] = 0x80 | (code & 0x3F);
            return 3;
        }
        if (code <= 0x10FFFF) {
            out[0] = 0xF0 | (code >> 18);
            out[1] = 0x80 | ((code >> 12) & 0x3F);
            out[2] = 0x80 | ((code >> 6) & 0x3F);
            out[3] = 0x80 | (code & 0x3F);
            return 4;
        }
        out[0] = '?';
        return 1;
    }

    private static int fromLE16(int value) {
        // PORT-NOTE: upstream FromLE16 is a no-op on little-endian targets; our target set is LE,
        // and readFile2Bytes() already decodes little-endian.
        return value & 0xFFFF;
    }

    private static int fromBE16(int value) {
        return ((value << 8) | (value >> 8)) & 0xFFFF;
    }

    private int readFileByte() {
        if (mFile == null) {
            return -1;
        }
        try {
            return mFile.read(mOneByte, 0, 1) == 1 ? (mOneByte[0] & 0xFF) : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private int readFile2Bytes() {
        if (mFile == null) {
            return -1;
        }
        try {
            if (mFile.read(mTwoBytes, 0, 1) != 1) {
                return -1;
            }
            if (mFile.read(mTwoBytes, 1, 1) != 1) {
                return -1;
            }
            return (mTwoBytes[0] & 0xFF) | ((mTwoBytes[1] & 0xFF) << 8);
        } catch (IOException e) {
            return -1;
        }
    }

    private boolean getChar(int[] theChar, boolean[] error) {
        switch (mEncoding) {
            case ASCII:
                return getAsciiChar(theChar, error);
            case UTF_8:
                return getUtf8Char(theChar, error);
            case UTF_16:
                return getUtf16Char(theChar, error);
            case UTF_16_LE:
                return getUtf16LeChar(theChar, error);
            case UTF_16_BE:
                return getUtf16BeChar(theChar, error);
            default:
                return getUtf8Char(theChar, error);
        }
    }

    public boolean openFile(String theFileName) {
        try {
            mFile = PakInterface.getInstance().open(theFileName, "r");
        } catch (IOException e) {
            mFile = null;
        }

        if (mFile == null) {
            mLineNum = 0;
            fail("Unable to open file " + theFileName);
            return false;
        } else if (!mForcedEncodingType) {
            int aFileLen = fileSize();

            mEncoding = XMLEncodingType.ASCII;
            if (aFileLen >= 2) { // UTF-16?
                int aChar1 = fileGetc();
                int aChar2 = fileGetc();

                if ((aChar1 == 0xFF && aChar2 == 0xFE) || (aChar1 == 0xFE && aChar2 == 0xFF)) {
                    mEncoding = XMLEncodingType.UTF_16;
                }

                fileUngetc(aChar2);
                fileUngetc(aChar1);
            }
            if (mEncoding == XMLEncodingType.ASCII) {
                if (aFileLen >= 3) { // UTF-8?
                    int aChar1 = fileGetc();
                    int aChar2 = fileGetc();
                    int aChar3 = fileGetc();

                    if (aChar1 == 0xEF && aChar2 == 0xBB && aChar3 == 0xBF) {
                        mEncoding = XMLEncodingType.UTF_8;
                    }

                    fileUngetc(aChar3);
                    fileUngetc(aChar2);
                    fileUngetc(aChar1);
                }
            }
        }

        mFileName = theFileName;
        init();
        return true;
    }

    public void setStringSource(String theString) {
        init();

        byte[] aBytes = theString.getBytes(StandardCharsets.UTF_8);

        int offset = 0;
        if (aBytes.length >= 3 && (aBytes[0] & 0xFF) == 0xEF && (aBytes[1] & 0xFF) == 0xBB
                && (aBytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        // Upstream resizes (and thus truncates) the push-back stack, then stores the bytes in
        // reverse so that popping from the end yields the text in order.
        mBufferedText.clear();
        for (int i = aBytes.length - offset - 1; i >= 0; i--) {
            mBufferedText.addLast(aBytes[offset + i] & 0xFF);
        }
    }

    public boolean nextElement(XMLElement theElement) {
        for (; ; ) {
            theElement.mType = XMLElement.TYPE_NONE;
            theElement.mSection = byteStringToText(mSection);
            theElement.mValue = "";
            theElement.mAttributes.clear();
            theElement.mAttributeIteratorList.clear();
            theElement.mInstruction = "";

            boolean hasSpace = false;
            boolean inQuote = false;
            boolean gotEndQuote = false;

            boolean doingAttribute = false;
            boolean attributeVal = false;
            StringBuilder aAttributeKey = new StringBuilder();
            StringBuilder aAttributeValue = new StringBuilder();

            String aLastAttributeKey = "";

            StringBuilder theValue = new StringBuilder();
            StringBuilder theInstruction = new StringBuilder();

            for (; ; ) {
                // Process character by character

                int c;
                int aVal;

                if (!mBufferedText.isEmpty()) {
                    c = mBufferedText.removeLast();

                    aVal = 1;
                } else {
                    if (mFile != null) {
                        boolean[] error = {false};
                        int[] aChar = {0};
                        if (getChar(aChar, error)) {
                            c = aChar[0];
                            aVal = 1;
                        } else {
                            if (error[0]) {
                                fail("Illegal Character");
                            }
                            c = 0;
                            aVal = 0;
                        }
                    } else {
                        c = 0;
                        aVal = 0;
                    }
                }

                if (aVal == 1) {
                    boolean processChar = false;

                    if (c == '\n') {
                        mLineNum++;
                    }

                    if (theElement.mType == XMLElement.TYPE_COMMENT) {
                        // Just add text to theInstruction until we find -->

                        StringBuilder aStrPtr = theInstruction;

                        aStrPtr.append((char) c);

                        int aLen = aStrPtr.length();

                        if ((c == '>') && (aLen >= 3) && (aStrPtr.charAt(aLen - 2) == '-')
                                && (aStrPtr.charAt(aLen - 3) == '-')) {
                            aStrPtr.setLength(aLen - 3);
                            break;
                        }
                    } else if (theElement.mType == XMLElement.TYPE_INSTRUCTION) {
                        // Just add text to theInstruction until we find ?>

                        StringBuilder aStrPtr = theValue;

                        if ((theInstruction.length() != 0) || isSpace(c)) {
                            aStrPtr = theInstruction;
                        }

                        aStrPtr.append((char) c);

                        int aLen = aStrPtr.length();

                        if ((c == '>') && (aLen >= 2) && (aStrPtr.charAt(aLen - 2) == '?')) {
                            aStrPtr.setLength(aLen - 2);
                            break;
                        }
                    } else {
                        if (c == '"') {
                            inQuote = !inQuote;
                            if (theElement.mType == XMLElement.TYPE_NONE
                                    || theElement.mType == XMLElement.TYPE_ELEMENT) {
                                processChar = true;
                            }

                            if (!inQuote) {
                                gotEndQuote = true;
                            }
                        } else if (!inQuote) {
                            if (c == '<') {
                                if (theElement.mType == XMLElement.TYPE_ELEMENT) {
                                    //TODO (kept verbatim from upstream): Fix buffered text.  Not sure what I meant by that.

                                    //OLD: mBufferedText = c + mBufferedText;

                                    mBufferedText.addLast(c);
                                    break;
                                }

                                if (theElement.mType == XMLElement.TYPE_NONE) {
                                    theElement.mType = XMLElement.TYPE_START;
                                } else {
                                    fail("Unexpected '<'");
                                    return false;
                                }
                            } else if (c == '>') {
                                if (theElement.mType == XMLElement.TYPE_START) {
                                    boolean insertEnd = false;

                                    if (aAttributeKey.length() == 1 && aAttributeKey.charAt(0) == '/') {
                                        // We will get this if we have a space before the />, so we can ignore it
                                        //  and go about our business now
                                        insertEnd = true;
                                    } else {
                                        // Probably isn't committed yet
                                        if (aAttributeKey.length() > 0) {
//                                                theElement->mAttributes[aLastAttributeKey] = aAttributeValue;

                                            String aKey = Common.xmlDecodeString(aAttributeKey.toString());
                                            String aValue = Common.xmlDecodeString(aAttributeValue.toString());

                                            aLastAttributeKey = aKey;
                                            addAttribute(theElement, aLastAttributeKey, aValue);

                                            aAttributeKey.setLength(0);
                                            aAttributeValue.setLength(0);
                                        }

                                        if (aLastAttributeKey.length() > 0) {
                                            String aVal2 = theElement.mAttributes.get(aLastAttributeKey);

                                            int aLen = aVal2.length();

                                            if ((aLen > 0) && (aVal2.charAt(aLen - 1) == '/')) {
                                                // Its an empty element, fake start and end segments
//                                                theElement->mAttributes[aLastAttributeKey] = aVal.substr(0, aLen - 1);

                                                addAttribute(theElement, aLastAttributeKey,
                                                        Common.xmlDecodeString(aVal2.substring(0, aLen - 1)));

                                                insertEnd = true;
                                            }
                                        } else {
                                            int aLen = theValue.length();

                                            if ((aLen > 0) && (theValue.charAt(aLen - 1) == '/')) {
                                                // Its an empty element, fake start and end segments
                                                theValue.setLength(aLen - 1);
                                                insertEnd = true;
                                            }
                                        }
                                    }

                                    // Do we want to fake an ending section?
                                    if (insertEnd) {
                                        String anAddString = "</" + theValue + ">";

                                        int anAddLength = anAddString.length();

                                        for (int i = 0; i < anAddLength; i++) {
                                            mBufferedText.addLast((int) anAddString.charAt(anAddLength - i - 1));
                                        }

                                        // clear out aAttributeKey, since it contains "/" as its value and will insert
                                        // it into the element's attribute map.
                                        aAttributeKey.setLength(0);

                                        //OLD: mBufferedText = "</" + theElement->mValue + ">" + mBufferedText;
                                    }

                                    if (mSection.length() != 0) {
                                        mSection += "/";
                                    }

                                    mSection += theValue;

                                    break;
                                } else if (theElement.mType == XMLElement.TYPE_END) {
                                    int aLastSlash = mSection.lastIndexOf('/');
                                    if ((aLastSlash == -1) && (mSection.length() == 0)) {
                                        fail("Unexpected End");
                                        return false;
                                    }

                                    String aLastSectionName = mSection.substring(aLastSlash + 1);

                                    if (!aLastSectionName.equals(theValue.toString())) {
                                        fail("End '" + byteStringToText(theValue.toString()) + "' Doesn't Match Start '"
                                                + byteStringToText(aLastSectionName) + "'");
                                        return false;
                                    }

                                    if (aLastSlash == -1) {
                                        mSection = "";
                                    } else {
                                        mSection = mSection.substring(0, aLastSlash);
                                    }

                                    break;
                                } else {
                                    fail("Unexpected '>'");
                                    return false;
                                }
                            } else if ((c == '/') && (theElement.mType == XMLElement.TYPE_START)
                                    && (theValue.length() == 0)) {
                                theElement.mType = XMLElement.TYPE_END;
                            } else if ((c == '?') && (theElement.mType == XMLElement.TYPE_START)
                                    && (theValue.length() == 0)) {
                                theElement.mType = XMLElement.TYPE_INSTRUCTION;
                            } else if (isSpace(c)) {
                                if (theValue.length() != 0) {
                                    hasSpace = true;
                                }

                                // It's a comment!
                                if ((theElement.mType == XMLElement.TYPE_START) && ("!--".contentEquals(theValue))) {
                                    theElement.mType = XMLElement.TYPE_COMMENT;
                                }
                            } else if (c > 32) {
                                processChar = true;
                            } else {
                                fail("Illegal Character");
                                return false;
                            }
                        } else {
                            processChar = true;
                        }

                        if (processChar) {
                            if (theElement.mType == XMLElement.TYPE_NONE) {
                                theElement.mType = XMLElement.TYPE_ELEMENT;
                            }

                            if (theElement.mType == XMLElement.TYPE_START) {
                                if (hasSpace) {
                                    if ((!doingAttribute) || ((!attributeVal) && (c != '='))
                                            || ((attributeVal) && ((aAttributeValue.length() > 0) || gotEndQuote))) {
                                        if (doingAttribute) {
                                            String aKey = Common.xmlDecodeString(aAttributeKey.toString());
                                            String aValue = Common.xmlDecodeString(aAttributeValue.toString());

//                                                theElement->mAttributes[aAttributeKey] = aAttributeValue;

                                            addAttribute(theElement, aKey, aValue);

                                            aAttributeKey.setLength(0);
                                            aAttributeValue.setLength(0);

                                            aLastAttributeKey = "";
                                        } else {
                                            doingAttribute = true;
                                        }

                                        attributeVal = false;
                                    }

                                    hasSpace = false;
                                }

                                StringBuilder aStrPtr = null;

                                if (!doingAttribute) {
                                    theValue.append((char) c);
                                } else {
                                    if (c == '=') {
                                        attributeVal = true;
                                        gotEndQuote = false;
                                    } else {
                                        if (!attributeVal) {
                                            aStrPtr = aAttributeKey;
                                        } else {
                                            aStrPtr = aAttributeValue;
                                        }
                                    }
                                }

                                if (aStrPtr != null) {
                                    aStrPtr.append((char) c);
                                }
                            } else {
                                if (hasSpace) {
                                    theValue.append(' ');
                                    hasSpace = false;
                                }

                                theValue.append((char) c);
                            }
                        }
                    }
                } else {
                    if (theElement.mType != XMLElement.TYPE_NONE) {
                        fail("Unexpected End of File");
                    }

                    return false;
                }
            }

            if (aAttributeKey.length() > 0) {
                String aKey = Common.xmlDecodeString(aAttributeKey.toString());
                String aValue = Common.xmlDecodeString(aAttributeValue.toString());
//                theElement->mAttributes[aAttributeKey] = aAttributeValue;

                addAttribute(theElement, aKey, aValue);
            }

            theElement.mValue = Common.xmlDecodeString(byteStringToText(theValue.toString()));
            theElement.mInstruction = byteStringToText(theInstruction.toString());

            // Ignore comments
            if ((theElement.mType != XMLElement.TYPE_COMMENT) || mAllowComments) {
                return true;
            }
        }
    }

    public boolean hasFailed() {
        return mHasFailed;
    }

    public String getErrorText() {
        return mErrorText;
    }

    public int getCurrentLineNum() {
        return mLineNum;
    }

    public String getFileName() {
        return mFileName;
    }

    public void allowComments(boolean doAllow) {
        mAllowComments = doAllow;
    }

    /**
     * Upstream declares {@code EndOfFile()} (XMLParser.h:126) but never defines or calls it. This is
     * the obvious completion: the push-back stack is empty and the file is exhausted.
     */
    public boolean endOfFile() {
        if (!mBufferedText.isEmpty()) {
            return false;
        }
        if (mFile == null) {
            return true;
        }
        try {
            return mFile.eof();
        } catch (IOException e) {
            return true;
        }
    }

    /** Upstream {@code ::isspace} in the C locale, applied to an unsigned byte. */
    private static boolean isSpace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == 0x0C || c == '\r';
    }

    /**
     * Converts an internal byte-string (one char per source byte) to Java text.
     *
     * <p>PORT-NOTE: upstream keeps raw bytes in {@code std::string} and every consumer treats them
     * as UTF-8, so the byte stream is decoded as UTF-8 here (UTF-16 input has already been
     * transcoded by the char getters, matching upstream). Bytes that are not valid UTF-8 (which
     * upstream would pass through unchanged) are mapped 1:1 through ISO-8859-1 so no byte is lost.
     */
    private String byteStringToText(String theByteString) {
        if (theByteString.isEmpty()) {
            return "";
        }

        byte[] aBytes = new byte[theByteString.length()];
        for (int i = 0; i < aBytes.length; i++) {
            aBytes[i] = (byte) theByteString.charAt(i);
        }

        return decodeUtf8(aBytes);
    }

    private static String decodeUtf8(byte[] theBytes) {
        CharsetDecoder aDecoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return aDecoder.decode(ByteBuffer.wrap(theBytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(theBytes, StandardCharsets.ISO_8859_1);
        }
    }

    private int fileSize() {
        try {
            return mFile.size();
        } catch (IOException e) {
            return 0;
        }
    }

    private int fileGetc() {
        try {
            return mFile.getc();
        } catch (IOException e) {
            return -1;
        }
    }

    private void fileUngetc(int ch) {
        // PORT-NOTE: upstream calls p_ungetc unconditionally, even for EOF (-1). Pushing back an
        // EOF sentinel would corrupt the position in this port, so it is skipped.
        if (ch < 0) {
            return;
        }
        try {
            mFile.ungetc(ch);
        } catch (IOException e) {
            // ignored, like upstream's unchecked ungetc
        }
    }
}
