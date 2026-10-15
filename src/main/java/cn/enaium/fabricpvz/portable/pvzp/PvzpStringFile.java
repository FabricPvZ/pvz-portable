package cn.enaium.fabricpvz.portable.pvzp;

import java.util.Optional;

import cn.enaium.fabricpvz.portable.lawn.DrawStringJustification;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/PvzpLib/PvzpStringFile.h:20 and src/PvzpLib/PvzpStringFile.cpp:24
 *
 * <p>The localized string bank: {@code Properties/LawnStrings.txt} is a flat sequence of
 * {@code [KEY]} sections followed by their text, for example
 * <pre>
 * [LOADING]Loading...
 * [CLICK_TO_START]Click to Start
 * </pre>
 * Keys are upper-cased and stored in the app's string properties
 * ({@link SexyAppBase#setString}), which is also where {@code properties/default.xml} writes its
 * overrides — that is why {@code LawnApp} loads {@code LawnStrings.txt} first and the localized
 * {@code properties/*.xml} afterwards (LawnApp.cpp:1640-1646). Text is looked up with
 * {@code [KEY]} (see {@link #translate}) or by a bare key (see {@link #find}).
 *
 * <p>Parsing rules are transcribed exactly: a name is the text between {@code '['} and {@code ']'}
 * trimmed with {@link Common#trim} (empty names other than the terminating one are an error), a
 * value runs up to the next {@code '['} with all {@code '\r'} removed, and a successful read whose
 * name is empty (the remaining text being pure whitespace) terminates the file.
 */
public final class PvzpStringFile {

    private PvzpStringFile() {
    }

    /**
     * Upstream {@code PvzpStringListReadName} (PvzpStringFile.cpp:68).
     *
     * <p>PORT-NOTE: upstream takes {@code const char*& thePtr}, a pointer that walks the file text;
     * this port uses {@code theFileText} plus {@code thePtr[0]} as that cursor. Note the upstream
     * cursor arithmetic ({@code thePtr += aCount + 2}) is relative to the cursor, not to the located
     * {@code '['}, so leading whitespace before the very first name shifts the cursor (kept as-is).
     */
    public static boolean readName(String theFileText, int[] thePtr, String[] theName) {
        int aNameStart = theFileText.indexOf('[', thePtr[0]);
        if (aNameStart == -1) {
            if (!isAllWhiteSpace(theFileText, thePtr[0])) { // the remaining text is not all whitespace
                Common.logInfoLn("Failed to find string name");
                return false;
            }

            theName[0] = "";
            return true;
        } else {
            int aNameEnd = theFileText.indexOf(']', aNameStart + 1);
            if (aNameEnd == -1) {
                Common.logInfoLn("Failed to find ']'");
                return false;
            }

            int aCount = aNameEnd - aNameStart - 1;
            theName[0] = Common.trim(theFileText.substring(aNameStart + 1, aNameStart + 1 + aCount));
            if (theName[0].length() == 0) {
                Common.logInfoLn("Name Too Short");
                return false;
            }

            thePtr[0] += aCount + 2; // advance past ']'
            return true;
        }
    }

    /** Upstream {@code strspn(thePtr, " \n\r\t") == strlen(thePtr)}. */
    private static boolean isAllWhiteSpace(String theText, int theStart) {
        for (int i = theStart; i < theText.length(); i++) {
            char c = theText.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    /**
     * Upstream {@code PvzpStringRemoveReturnChars}: removes every {@code '\r'}. Upstream mutates the
     * string in place; this returns the stripped text.
     */
    public static String removeReturnChars(String theString) {
        StringBuilder aResult = new StringBuilder(theString.length());
        for (int i = 0; i < theString.length(); i++) {
            if (theString.charAt(i) != '\r') {
                aResult.append(theString.charAt(i));
            }
        }
        return aResult.toString();
    }

    /**
     * Upstream {@code PvzpStringListReadValue} (PvzpStringFile.cpp:110): the value runs up to the next
     * {@code '['} (or the end of the text), is trimmed and has its return characters removed.
     */
    public static boolean readValue(String theFileText, int[] thePtr, String[] theValue) {
        int aValueEnd = theFileText.indexOf('[', thePtr[0]);
        int aLen = aValueEnd >= 0 ? aValueEnd - thePtr[0] : theFileText.length() - thePtr[0];
        theValue[0] = Common.trim(theFileText.substring(thePtr[0], thePtr[0] + aLen)); // up to the next '[', or the rest of the text
        theValue[0] = removeReturnChars(theValue[0]);
        thePtr[0] += aLen; // advance to the next '[' (or the end)
        return true;
    }

    /**
     * Upstream {@code PvzpStringListReadItems}: reads {@code [KEY]} sections until the terminating
     * empty name and stores them upper-cased in the app's string properties.
     */
    public static boolean readItems(String theFileText) {
        int[] aPtr = {0};
        String[] aName = {""};
        String[] aValue = {""};

        for (; ; ) {
            if (!readName(theFileText, aPtr, aName)) {
                return false;
            }
            if (aName[0].length() == 0) { // a successful read with an empty name means reading is done
                return true;
            }
            if (!readValue(theFileText, aPtr, aValue)) {
                return false;
            }

            String aNameUpper = Common.stringToUpper(aName[0]);
            SexyAppBase.current.setString(aNameUpper, aValue[0]);
        }
    }

    /**
     * Upstream {@code PvzpStringListReadFile}. Reads through the app
     * ({@code ReadUTF8StringFromFile}), so pak members are honored like everywhere else.
     */
    public static boolean readFile(String theFileName) {
        SexyAppBase aApp = SexyAppBase.current;
        String aFileContent = aApp == null ? null : aApp.readUtf8StringFromFile(theFileName);
        if (aFileContent == null) {
            Common.logInfoLn("Failed to open '{}'", theFileName);
            return false;
        }

        return readItems(aFileContent);
    }

    /**
     * Upstream {@code PvzpStringListLoad} (PvzpStringFile.cpp:160). PORT-NOTE: upstream reports a
     * failure through {@code PvzpErrorMessageBox}; the UI error box belongs to the game slice, so this
     * returns the success flag and logs the failure instead.
     */
    public static boolean load(String theFileName) {
        if (!readFile(theFileName)) {
            Common.logErrorLn("Failed to load string list file '{}'", theFileName);
            return false;
        }
        return true;
    }

    /**
     * Upstream {@code PvzpStringListFind}: the app's string property, or {@code "<Missing NAME>"}.
     */
    public static String find(String theName) {
        String aValue = SexyAppBase.current.mStringProperties.get(theName);
        if (aValue != null) {
            return aValue;
        } else {
            return "<Missing " + theName + ">";
        }
    }

    /**
     * Upstream {@code PvzpStringTranslate}: {@code "[KEY]"} becomes its banked text, anything else is
     * returned unchanged. A {@code null} input (upstream's {@code const char*} overload) yields "".
     */
    public static String translate(String theString) {
        if (theString == null) {
            return "";
        }

        if (theString.length() >= 3 && theString.charAt(0) == '[') {
            String aName = theString.substring(1, theString.length() - 1);
            return find(aName);
        }
        return theString;
    }

    /** Upstream {@code PvzpStringTryTranslate}: the banked text, or empty when the key is unknown. */
    public static Optional<String> tryTranslate(String theString) {
        if (theString.length() >= 3 && theString.charAt(0) == '[') {
            String aName = theString.substring(1, theString.length() - 1);
            String aValue = SexyAppBase.current.mStringProperties.get(aName);
            if (aValue != null) {
                return Optional.of(aValue);
            }
        }
        return Optional.empty();
    }

    /** Upstream {@code PvzpStringListExists}: whether the {@code "[KEY]"} form resolves. */
    public static boolean exists(String theString) {
        return tryTranslate(theString).isPresent();
    }

    // ------------------------------------------------------------------
    // Format table (PvzpStringFile.cpp:29-60)
    // ------------------------------------------------------------------

    /** Upstream {@code gPvzpStringFormatCount}: installed by {@link #pvzpStringListSetColors}. */
    public static int gPvzpStringFormatCount;

    /** Upstream {@code gPvzpStringFormats}: the active format table (empty until {@code main()}). */
    public static PvzpStringListFormat[] gPvzpStringFormats = new PvzpStringListFormat[0];

    /** Upstream {@code gLawnStringFormatCount}. */
    public static final int gLawnStringFormatCount = 12;

    /** Upstream {@code gLawnStringFormats[12]}, values verbatim. */
    public static final PvzpStringListFormat[] gLawnStringFormats = {
            new PvzpStringListFormat("NORMAL", null, new Color(40, 50, 90, 255), 0, 0),
            new PvzpStringListFormat("FLAVOR", null, new Color(143, 67, 27, 255), 0, 1),
            new PvzpStringListFormat("KEYWORD", null, new Color(143, 67, 27, 255), 0, 0),
            new PvzpStringListFormat("NOCTURNAL", null, new Color(136, 50, 170, 255), 0, 0),
            new PvzpStringListFormat("AQUATIC", null, new Color(11, 161, 219, 255), 0, 0),
            new PvzpStringListFormat("STAT", null, new Color(204, 36, 29, 255), 0, 0),
            new PvzpStringListFormat("METAL", null, new Color(204, 36, 29, 255), 0, 2),
            new PvzpStringListFormat("KEYMETAL", null, new Color(143, 67, 27, 255), 0, 2),
            new PvzpStringListFormat("SHORTLINE", null, new Color(0, 0, 0, 0), -9, 0),
            new PvzpStringListFormat("EXTRASHORTLINE", null, new Color(0, 0, 0, 0), -14, 0),
            new PvzpStringListFormat("CREDITS1", null, new Color(0, 0, 0, 0), 3, 0),
            new PvzpStringListFormat("CREDITS2", null, new Color(0, 0, 0, 0), 2, 0) // wrong size (2 duplicates)
    };

    /**
     * Upstream {@code PvzpStringListSetColors} (PvzpStringFile.cpp:61), called once from
     * {@code main()} with {@code gLawnStringFormats}.
     */
    public static void pvzpStringListSetColors(PvzpStringListFormat[] theFormats, int theCount) {
        gPvzpStringFormats = theFormats;
        gPvzpStringFormatCount = theCount;
    }

    // ------------------------------------------------------------------
    // Formatted drawing (PvzpStringFile.cpp:204-500)
    // ------------------------------------------------------------------

    /**
     * Upstream {@code PvzpWriteStringSetFormat}: installs the first format whose name is a prefix of
     * {@code theFormat} ({@code strncmp} semantics) into {@code theCurrentFormat}.
     */
    public static void pvzpWriteStringSetFormat(String theFormat, PvzpStringListFormat theCurrentFormat) {
        for (int i = 0; i < gPvzpStringFormatCount; i++) {
            PvzpStringListFormat aFormat = gPvzpStringFormats[i];
            if (theFormat.startsWith(aFormat.mFormatName)) {
                if (aFormat.mNewFont != null) {
                    theCurrentFormat.mNewFont = aFormat.mNewFont;
                }
                if (!aFormat.mNewColor.equals(new Color(0, 0, 0, 0))) {
                    theCurrentFormat.mNewColor = aFormat.mNewColor;
                }
                theCurrentFormat.mLineSpacingOffset = aFormat.mLineSpacingOffset;
                theCurrentFormat.mFormatFlags = aFormat.mFormatFlags;
                return;
            }
        }
    }

    /** Upstream {@code CharIsSpaceInFormat}. */
    public static boolean charIsSpaceInFormat(char theChar, PvzpStringListFormat theCurrentFormat) {
        return theChar == ' ' || (PvzpCommon.testBit(theCurrentFormat.mFormatFlags,
                PvzpStringFormatFlag.PVZP_FORMAT_IGNORE_NEWLINES) && theChar == '\n');
    }

    /**
     * Upstream {@code PvzpWriteString} (PvzpStringFile.cpp:227): draws text with {@code {FORMAT}}
     * control codes, honouring the current format's colour and flag set.
     */
    public static int pvzpWriteString(Graphics g, String theString, int theX, int theY,
            PvzpStringListFormat theCurrentFormat, int theWidth, int theJustification,
            boolean drawString, int theOffset, int theLength) {
        Font aFont = theCurrentFormat.mNewFont;
        if (drawString) {
            switch (theJustification) {
                case DrawStringJustification.DS_ALIGN_RIGHT:
                case DrawStringJustification.DS_ALIGN_RIGHT_VERTICAL_MIDDLE:
                    theX += pvzpMeasureSpareX(g, theString, theX, theY, theCurrentFormat, theWidth,
                            theOffset, theLength);
                    break;
                case DrawStringJustification.DS_ALIGN_CENTER:
                case DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE:
                    theX += pvzpMeasureSpareX(g, theString, theX, theY, theCurrentFormat, theWidth,
                            theOffset, theLength) / 2;
                    break;
                default:
                    break;
            }
        }

        if (theLength < 0 || theOffset + theLength > theString.length()) {
            theLength = theString.length();
        } else {
            theLength = theOffset + theLength; // theLength becomes the end position of the substring
        }

        StringBuilder aString = new StringBuilder();
        int aXOffset = 0;
        boolean aPrevCharWasSpace = false;
        for (int i = theOffset; i < theLength; i++) {
            if (theString.charAt(i) == '{') {
                int aFormatStart = i;
                int aFormatEnd = theString.indexOf('}', aFormatStart + 1);
                if (aFormatEnd >= 0) { // a complete "{FORMAT}" control code
                    i = aFormatEnd; // move i to '}'
                    if (drawString) {
                        // draw the accumulated text
                        aFont.drawString(g, theX + aXOffset, theY, aString.toString(),
                                theCurrentFormat.mNewColor, g.mClipRect);
                    }

                    aXOffset += aFont.stringWidth(aString.toString());
                    aString.setLength(0);
                    pvzpWriteStringSetFormat(theString.substring(aFormatStart + 1, aFormatEnd), theCurrentFormat);
                    // PORT-NOTE: upstream keeps using the font captured at entry (a stale local), so
                    // this port does the same instead of re-reading theCurrentFormat.mNewFont.
                }
            } else {
                if (PvzpCommon.testBit(theCurrentFormat.mFormatFlags,
                        PvzpStringFormatFlag.PVZP_FORMAT_IGNORE_NEWLINES)) { // newlines are treated as spaces
                    if (charIsSpaceInFormat(theString.charAt(i), theCurrentFormat)) {
                        if (!aPrevCharWasSpace) {
                            aString.append(' ');
                        }
                        continue;
                    } else {
                        aPrevCharWasSpace = false; // collapse consecutive spaces into one
                    }
                }

                aString.append(theString.charAt(i));
            }
        }

        if (drawString) {
            // draw the accumulated text
            aFont.drawString(g, theX + aXOffset, theY, aString.toString(), theCurrentFormat.mNewColor,
                    g.mClipRect);
        }
        return aXOffset + aFont.stringWidth(aString.toString());
    }

    /**
     * Upstream's {@code aMeasureSpareX} lambda inside {@code PvzpWriteString}: width left over when
     * the text is laid out left-aligned, measured with a copy of the current format.
     */
    private static int pvzpMeasureSpareX(Graphics g, String theString, int theX, int theY,
            PvzpStringListFormat theCurrentFormat, int theWidth, int theOffset, int theLength) {
        PvzpStringListFormat aMeasureFormat = new PvzpStringListFormat(theCurrentFormat);
        return theWidth - pvzpWriteString(g, theString, theX, theY, aMeasureFormat, theWidth,
                DrawStringJustification.DS_ALIGN_LEFT, false, theOffset, theLength);
    }

    /** Upstream {@code PvzpWriteWordWrappedHelper} (PvzpStringFile.cpp:300). */
    public static int pvzpWriteWordWrappedHelper(Graphics g, String theString, int theX, int theY,
            PvzpStringListFormat theCurrentFormat, int theWidth, int theJustification,
            boolean drawString, int theOffset, int theLength, int theMaxChars) {
        if (theOffset + theLength > theMaxChars) {
            theLength = theMaxChars - theOffset;
            if (theLength <= 0) {
                return -1;
            }
        }
        return pvzpWriteString(g, theString, theX, theY, theCurrentFormat, theWidth, theJustification,
                drawString, theOffset, theLength);
    }

    /**
     * Upstream {@code PvzpDrawStringWrappedHelper} (PvzpStringFile.cpp:311): word-wraps
     * {@code theText} inside {@code theRect}, applying {@code {FORMAT}} codes as it goes and
     * returning the drawn height.
     *
     * <p>PORT-NOTE: upstream walks the text as UTF-8 bytes; this port walks Java chars/code points
     * (like {@code Graphics.drawStringWrapped} does), which yields the same substring boundaries.
     * Upstream also accumulates an {@code aMaxWidth} that is never read — omitted here.
     */
    public static int pvzpDrawStringWrappedHelper(Graphics g, String theText, Rect theRect, Font theFont,
            Color theColor, int theJustification, boolean drawString) {
        int theMaxChars = theText.length();
        PvzpStringListFormat aCurrentFormat = new PvzpStringListFormat();
        aCurrentFormat.mNewFont = theFont;
        aCurrentFormat.mNewColor = theColor;
        aCurrentFormat.mFormatName = "";
        aCurrentFormat.mLineSpacingOffset = 0;
        aCurrentFormat.mFormatFlags = 0;

        int aYOffset = theFont.getAscent() - theFont.getAscentPadding();
        int aLineSpacing = theFont.getLineSpacing() + aCurrentFormat.mLineSpacingOffset;
        int aLineFeedPos = 0;
        int aCurPos = 0;
        int aCurWidth = 0;
        int aCurChar = 0;
        int aPrevChar = 0;

        int aBreakDrawLen = -1;        // chars from aLineFeedPos to draw (-1 = no break point)
        int aBreakResumePos = 0;       // offset where next line starts
        boolean aBreakSkipSpaces = false; // skip consecutive spaces after break

        while (aCurPos < theText.length()) {
            int aCharStart = aCurPos;

            if (theText.charAt(aCurPos) == '{') {
                int aFmtStart = aCurPos;
                int aFmtEnd = theText.indexOf('}', aFmtStart + 1);
                if (aFmtEnd >= 0) {
                    aCurPos = aFmtEnd + 1;
                    int aOldAscentOffset = theFont.getAscent() - theFont.getAscentPadding();
                    Color aExistingColor = aCurrentFormat.mNewColor;
                    pvzpWriteStringSetFormat(theText.substring(aFmtStart + 1, aFmtEnd), aCurrentFormat);
                    aCurrentFormat.mNewColor = aExistingColor;
                    int aNewAscentOffset = aCurrentFormat.mNewFont.getAscent()
                            - aCurrentFormat.mNewFont.getAscentPadding();
                    aLineSpacing = aCurrentFormat.mNewFont.getLineSpacing() + aCurrentFormat.mLineSpacingOffset;
                    aYOffset += aNewAscentOffset - aOldAscentOffset;
                    continue;
                }
            }

            aCurChar = theText.codePointAt(aCurPos);
            aCurPos += Character.charCount(aCurChar);
            if (aCurChar == '\r') { // skip CR for CRLF/LF compatibility
                continue;
            }
            int aCharEnd = aCurPos;
            boolean aIsNewline = (aCurChar == '\n')
                    && !PvzpCommon.testBit(aCurrentFormat.mFormatFlags,
                            PvzpStringFormatFlag.PVZP_FORMAT_IGNORE_NEWLINES);
            boolean aIsSpace = !aIsNewline && (aCurChar == ' '
                    || (aCurChar < 0x80 && charIsSpaceInFormat((char) aCurChar, aCurrentFormat)));

            if (aIsSpace) {
                aBreakDrawLen = aCharStart - aLineFeedPos;
                aBreakResumePos = aCharEnd;
                aBreakSkipSpaces = true;
                aCurChar = ' ';
            } else if (aIsNewline) {
                aBreakDrawLen = aCharStart - aLineFeedPos;
                aBreakResumePos = aCharEnd;
                aBreakSkipSpaces = false;
                aCurWidth = theRect.mWidth + 1;
            }

            aCurWidth += aCurrentFormat.mNewFont.charWidthKern(aCurChar, aPrevChar);

            if (!aIsSpace && !aIsNewline && Common.isAutoBreakChar(aCurChar)
                    && !Common.isClosingPunctuation(aCurChar)
                    && aCharStart > aLineFeedPos
                    && !Common.isOpeningPunctuation(aPrevChar)) {
                aBreakDrawLen = aCharStart - aLineFeedPos;
                aBreakResumePos = aCharStart;
                aBreakSkipSpaces = false;
            }
            aPrevChar = aCurChar;

            if (aCurWidth > theRect.mWidth) {
                int aLineWidth;
                if (aBreakDrawLen >= 0) {
                    int aCurY = (int) g.mTransY + theRect.mY + aYOffset;
                    if (aCurY >= g.mClipRect.mY
                            && aCurY <= g.mClipRect.mY + g.mClipRect.mHeight + aLineSpacing) {
                        pvzpWriteWordWrappedHelper(g, theText, theRect.mX, theRect.mY + aYOffset,
                                aCurrentFormat, theRect.mWidth, theJustification, drawString,
                                aLineFeedPos, aBreakDrawLen, theMaxChars);
                    }

                    aLineWidth = aCurWidth;
                    if (aLineWidth < 0) {
                        break;
                    }

                    aCurPos = aBreakResumePos;
                    if (aBreakSkipSpaces) {
                        while (aCurPos < theText.length() && theText.charAt(aCurPos) == ' ') {
                            aCurPos++;
                        }
                    }
                } else {
                    // No break point: force break, ensure at least one char per line
                    int aDrawEnd = aCharStart;
                    if (aDrawEnd <= aLineFeedPos) {
                        aDrawEnd = aCharEnd;
                    }

                    aLineWidth = pvzpWriteWordWrappedHelper(g, theText, theRect.mX, theRect.mY + aYOffset,
                            aCurrentFormat, theRect.mWidth, theJustification, drawString,
                            aLineFeedPos, aDrawEnd - aLineFeedPos, theMaxChars);
                    if (aLineWidth < 0) {
                        break;
                    }

                    aCurPos = aDrawEnd;
                }

                aYOffset += aLineSpacing;
                aLineFeedPos = aCurPos;
                aBreakDrawLen = -1;
                aCurWidth = 0;
                aPrevChar = 0;
            }
        }

        if (aLineFeedPos < theText.length()) {
            int aLastLineLength = pvzpWriteWordWrappedHelper(g, theText, theRect.mX, theRect.mY + aYOffset,
                    aCurrentFormat, theRect.mWidth, theJustification, drawString, aLineFeedPos,
                    theText.length() - aLineFeedPos, theMaxChars); // draw the last line
            if (aLastLineLength >= 0) {
                aYOffset += aLineSpacing;
            }
        } else {
            aYOffset += aLineSpacing;
        }

        return aCurrentFormat.mNewFont.getDescent() + aYOffset - aLineSpacing;
    }

    /**
     * Upstream {@code PvzpDrawStringWrapped} (PvzpStringFile.cpp:489): {@code [KEY]} translation
     * followed by {@code {FORMAT}}-aware word wrapping inside {@code theRect}.
     */
    public static void pvzpDrawStringWrapped(Graphics g, String theText, Rect theRect, Font theFont,
            Color theColor, int theJustification) {
        String aTextFinal = translate(theText);
        Rect aRectPvzpUse = new Rect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
        if (theJustification == DrawStringJustification.DS_ALIGN_LEFT_VERTICAL_MIDDLE
                || theJustification == DrawStringJustification.DS_ALIGN_RIGHT_VERTICAL_MIDDLE
                || theJustification == DrawStringJustification.DS_ALIGN_CENTER_VERTICAL_MIDDLE) { // vertical centering required
            aRectPvzpUse.mY += (aRectPvzpUse.mHeight - pvzpDrawStringWrappedHelper(g, aTextFinal,
                    aRectPvzpUse, theFont, theColor, theJustification, false)) / 2;
        }
        pvzpDrawStringWrappedHelper(g, aTextFinal, aRectPvzpUse, theFont, theColor, theJustification, true);
    }
}
