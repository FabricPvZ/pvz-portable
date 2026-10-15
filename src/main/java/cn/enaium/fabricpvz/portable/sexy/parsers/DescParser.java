package cn.enaium.fabricpvz.portable.sexy.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;

/**
 * Ported from src/SexyAppFramework/misc/DescParser.h:79 and src/SexyAppFramework/misc/DescParser.cpp:30
 *
 * <p>Parser for the PopCap "descriptor" text format used by {@code .fnt} font descriptors: commands
 * separated by {@code ;} (or by line breaks when {@link #CMDSEP_NO_INDENT} is set), {@code #}
 * comments, {@code 'quoted'} / {@code "quoted"} literals, parenthesized lists, and
 * {@code Define}able named values.
 *
 * <p>The scanner is transcribed one-to-one: same separator set ({@code ' ' \t \n ,}), same escape
 * handling, same errors ("Unexpected List Start", "Unexpected List End", "Unterminated List",
 * "Unterminated Single Quotes", "Unterminated Double Quotes", "Unable to Dereference \"x\"",
 * "Missing Command", "Failed to open file"). Numbers are converted with
 * {@link Strings#stringToInt} / {@link Strings#stringToDouble}, i.e. {@code strtol(base 0)} /
 * {@code strtod} with a mandatory full-string match (so {@code 0x2A}, {@code 0755} and
 * {@code -2} are all accepted).
 */
public abstract class DescParser {

    /** Upstream {@code CMDSEP_SEMICOLON}. */
    public static final int CMDSEP_SEMICOLON = 1;

    /** Upstream {@code CMDSEP_NO_INDENT}. */
    public static final int CMDSEP_NO_INDENT = 2;

    public int mCmdSep;

    public String mError = "";

    public int mCurrentLineNum;

    public String mCurrentLine = "";

    /** Upstream {@code std::map<std::string, std::unique_ptr<DataElement>>}, keyed upper-case. */
    public Map<String, DataElement> mDefineMap = new TreeMap<>();

    public DescParser() {
        mCmdSep = CMDSEP_SEMICOLON;
    }

    public boolean error(String theError) {
        mError = theError;
        return false;
    }

    public DataElement dereference(String theString) {
        String aDefineName = Common.stringToUpper(theString);

        return mDefineMap.get(aDefineName);
    }

    public boolean isImmediate(String theString) {
        // PORT-NOTE: upstream indexes theString[0] without a length check (undefined for an empty
        // string); an empty string is not immediate.
        if (theString.isEmpty()) {
            return false;
        }

        char aChar = theString.charAt(0);
        return ((aChar >= '0') && (aChar <= '9')) || (aChar == '-') || (aChar == '+')
                || (aChar == '\'') || (aChar == '"');
    }

    public String unquote(String theQuotedString) {
        if (!theQuotedString.isEmpty()
                && ((theQuotedString.charAt(0) == '\'') || (theQuotedString.charAt(0) == '"'))) {
            char aQuoteChar = theQuotedString.charAt(0);
            StringBuilder aLiteralString = new StringBuilder();
            boolean lastWasQuote = false;

            for (int i = 0; i < theQuotedString.length(); i++) {
                if (theQuotedString.charAt(i) == aQuoteChar) {
                    if (lastWasQuote) {
                        aLiteralString.append(aQuoteChar);
                    }

                    lastWasQuote = true;
                } else {
                    aLiteralString.append(theQuotedString.charAt(i));
                    lastWasQuote = false;
                }
            }

            return aLiteralString.toString();
        } else {
            return theQuotedString;
        }
    }

    public boolean getValues(ListDataElement theSource, ListDataElement theValues) {
        theValues.mElementVector.clear();

        for (int aSourceNum = 0; aSourceNum < theSource.mElementVector.size(); aSourceNum++) {
            if (theSource.mElementVector.get(aSourceNum).mIsList) {
                ListDataElement aChildList = new ListDataElement();
                theValues.mElementVector.add(aChildList);

                if (!getValues((ListDataElement) theSource.mElementVector.get(aSourceNum), aChildList)) {
                    return false;
                }
            } else {
                String aString = ((SingleDataElement) theSource.mElementVector.get(aSourceNum)).mString;

                if (aString.length() > 0) {
                    if ((aString.charAt(0) == '\'') || (aString.charAt(0) == '"')) {
                        SingleDataElement aChildData = new SingleDataElement(unquote(aString));
                        theValues.mElementVector.add(aChildData);
                    } else if (isImmediate(aString)) {
                        theValues.mElementVector.add(new SingleDataElement(aString));
                    } else {
                        String aDefineName = Common.stringToUpper(aString);

                        DataElement aDataElement = mDefineMap.get(aDefineName);

                        if (aDataElement == null) {
                            error("Unable to Dereference \"" + aString + "\"");
                            return false;
                        }

                        theValues.mElementVector.add(aDataElement.duplicate());
                    }
                }

            }
        }

        return true;
    }

    public String dataElementToString(DataElement theDataElement) {
        if (theDataElement.mIsList) {
            ListDataElement aListDataElement = (ListDataElement) theDataElement;

            StringBuilder aString = new StringBuilder("(");

            for (int i = 0; i < aListDataElement.mElementVector.size(); i++) {
                if (i != 0) {
                    aString.append(", ");
                }

                aString.append(dataElementToString(aListDataElement.mElementVector.get(i)));
            }

            aString.append(")");

            return aString.toString();
        } else {
            SingleDataElement aSingleDataElement = (SingleDataElement) theDataElement;
            return aSingleDataElement.mString;
        }
    }

    public boolean dataToString(DataElement theSource, String[] theString) {
        theString[0] = "";

        if (theSource.mIsList) {
            return false;
        }

        String aDefName = ((SingleDataElement) theSource).mString;

        DataElement aDataElement = dereference(aDefName);

        if (aDataElement != null) {
            if (aDataElement.mIsList) {
                return false;
            }

            theString[0] = unquote(((SingleDataElement) aDataElement).mString);
        } else {
            theString[0] = unquote(aDefName);
        }

        return true;
    }

    public boolean dataToInt(DataElement theSource, int[] theInt) {
        theInt[0] = 0;

        String[] aTempString = {""};
        if (!dataToString(theSource, aTempString)) {
            return false;
        }

        Integer anInt = Common.stringToInt(aTempString[0]);
        if (anInt == null) {
            return false;
        }
        theInt[0] = anInt;

        return true;
    }

    public boolean dataToStringVector(DataElement theSource, List<String> theStringVector) {
        theStringVector.clear();

        ListDataElement aStaticValues = new ListDataElement();
        ListDataElement aValues;

        if (theSource.mIsList) {
            if (!getValues((ListDataElement) theSource, aStaticValues)) {
                return false;
            }

            aValues = aStaticValues;
        } else {
            String aDefName = ((SingleDataElement) theSource).mString;

            DataElement aDataElement = dereference(aDefName);

            if (aDataElement == null) {
                error("Unable to Dereference \"" + aDefName + "\"");
                return false;
            }

            if (!aDataElement.mIsList) {
                return false;
            }

            aValues = (ListDataElement) aDataElement;
        }

        for (int i = 0; i < aValues.mElementVector.size(); i++) {
            if (aValues.mElementVector.get(i).mIsList) {
                theStringVector.clear();
                return false;
            }

            SingleDataElement aSingleDataElement = (SingleDataElement) aValues.mElementVector.get(i);

            theStringVector.add(aSingleDataElement.mString);
        }

        return true;
    }

    public boolean dataToList(DataElement theSource, ListDataElement theValues) {
        if (theSource.mIsList) {
            return getValues((ListDataElement) theSource, theValues);
        }

        DataElement aDataElement = dereference(((SingleDataElement) theSource).mString);

        if ((aDataElement == null) || (!aDataElement.mIsList)) {
            return false;
        }

        ListDataElement aListElement = (ListDataElement) aDataElement;

        theValues.copyFrom(aListElement);

        return true;
    }

    public boolean dataToIntVector(DataElement theSource, List<Integer> theIntVector) {
        theIntVector.clear();

        ArrayList<String> aStringVector = new ArrayList<>();
        if (!dataToStringVector(theSource, aStringVector)) {
            return false;
        }

        for (int i = 0; i < aStringVector.size(); i++) {
            Integer aIntVal = Common.stringToInt(aStringVector.get(i));
            if (aIntVal == null) {
                return false;
            }

            theIntVector.add(aIntVal);
        }

        return true;
    }

    public boolean dataToDoubleVector(DataElement theSource, List<Double> theDoubleVector) {
        theDoubleVector.clear();

        ArrayList<String> aStringVector = new ArrayList<>();
        if (!dataToStringVector(theSource, aStringVector)) {
            return false;
        }

        for (int i = 0; i < aStringVector.size(); i++) {
            Double aDoubleVal = Common.stringToDouble(aStringVector.get(i));
            if (aDoubleVal == null) {
                return false;
            }

            theDoubleVector.add(aDoubleVal);
        }

        return true;
    }

    public boolean parseToList(String theString, ListDataElement theList, boolean expectListEnd,
            int[] theStringPos) {
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        SingleDataElement aCurSingleDataElement = null;

        int[] aStringPos = theStringPos == null ? new int[1] : theStringPos;

        while (aStringPos[0] < theString.length()) {
            boolean addSingleChar = false;
            char aChar = theString.charAt(aStringPos[0]++);

            boolean isSeperator = (aChar == ' ') || (aChar == '\t') || (aChar == '\n') || (aChar == ',');

            if (escaped) {
                addSingleChar = true;
                escaped = false;
            } else {
                if ((aChar == '\'') && (!inDoubleQuotes)) {
                    inSingleQuotes = !inSingleQuotes;
                } else if ((aChar == '"') && (!inSingleQuotes)) {
                    inDoubleQuotes = !inDoubleQuotes;
                }

                if (aChar == '\\') {
                    escaped = true;
                } else if ((!inSingleQuotes) && (!inDoubleQuotes)) {
                    if (aChar == ')') {
                        if (expectListEnd) {
                            return true;
                        } else {
                            error("Unexpected List End");
                            return false;
                        }
                    } else if (aChar == '(') {
                        if (aCurSingleDataElement != null) {
                            error("Unexpected List Start");
                            return false;
                        } else {
                            ListDataElement aChildList = new ListDataElement();

                            if (!parseToList(theString, aChildList, true, aStringPos)) {
                                return false;
                            }

                            theList.mElementVector.add(aChildList);
                        }
                    } else if (isSeperator) {
                        if (aCurSingleDataElement != null) {
                            aCurSingleDataElement = null;
                        }
                    } else {
                        addSingleChar = true;
                    }
                } else {
                    addSingleChar = true;
                }
            }

            if (addSingleChar) {
                if (aCurSingleDataElement == null) {
                    aCurSingleDataElement = new SingleDataElement();
                    theList.mElementVector.add(aCurSingleDataElement);
                }

                aCurSingleDataElement.mString += aChar;
            }
        }

        if (inSingleQuotes) {
            error("Unterminated Single Quotes");
            return false;
        }

        if (inDoubleQuotes) {
            error("Unterminated Double Quotes");
            return false;
        }

        if (expectListEnd) {
            error("Unterminated List");
            return false;
        }

        return true;
    }

    public boolean parseToList(String theString, ListDataElement theList, boolean expectListEnd) {
        return parseToList(theString, theList, expectListEnd, null);
    }

    public boolean parseDescriptorLine(String theDescriptorLine) {
        ListDataElement aParams = new ListDataElement();
        if (!parseToList(theDescriptorLine, aParams, false, null)) {
            return false;
        }

        if (aParams.mElementVector.size() > 0) {
            if (aParams.mElementVector.get(0).mIsList) {
                error("Missing Command");
                return false;
            }

            if (!handleCommand(aParams)) {
                return false;
            }
        }

        return true;
    }

    /** You must implement this one. */
    public abstract boolean handleCommand(ListDataElement theParams);

    /**
     * Upstream {@code LoadDescriptor} is non-virtual, so this is final.
     *
     * <p>PORT-NOTE: upstream reads through the global app
     * ({@code gSexyAppBase->ReadUTF8StringFromFile}, DescParser.cpp:456).
     */
    public final boolean loadDescriptor(String theFileName) {
        mCurrentLineNum = 0;
        int aLineCount = 0;
        boolean hasErrors = false;

        mError = "";

        SexyAppBase aApp = SexyAppBase.current;
        String aContent = aApp == null ? null : aApp.readUtf8StringFromFile(theFileName);
        if (aContent == null) {
            error("Failed to open file");
            return false;
        }

        int aIndex = 0;
        int aBuffChar = 0;

        // The character loop accumulates one command at a time; PORT-NOTE: upstream appends to the
        // std::string directly, this port accumulates in a builder and publishes the field
        // mCurrentLine (read by subclass error() implementations) before parsing.
        StringBuilder aCurrentLine = new StringBuilder();

        while (aIndex < aContent.length()) {
            int aChar;

            boolean skipLine = false;
            boolean atLineStart = true;
            boolean inSingleQuotes = false;
            boolean inDoubleQuotes = false;
            boolean escaped = false;
            boolean isIndented = false;

            for (; ; ) {
                if (aBuffChar != 0) {
                    aChar = aBuffChar;
                    aBuffChar = 0;
                } else {
                    if (aIndex >= aContent.length()) {
                        break;
                    }
                    aChar = aContent.charAt(aIndex++);
                }

                if (aChar != '\r') {
                    if (aChar == '\n') {
                        aLineCount++;
                    }

                    if (((aChar == ' ') || (aChar == '\t')) && (atLineStart)) {
                        isIndented = true;
                    }

                    if ((!atLineStart) || ((aChar != ' ') && (aChar != '\t') && (aChar != '\n'))) {
                        if (atLineStart) {
                            if (((mCmdSep & CMDSEP_NO_INDENT) != 0) && (!isIndented)
                                    && (aCurrentLine.length() > 0)) {
                                // Start a new non-indented line
                                aBuffChar = aChar;
                                break;
                            }

                            if (aChar == '#') {
                                skipLine = true;
                            }

                            atLineStart = false;
                        }

                        if (aChar == '\n') {
                            isIndented = false;
                            atLineStart = true;
                        }

                        if ((aChar == '\n') && (skipLine)) {
                            skipLine = false;
                        } else if (!skipLine) {
                            if (aChar == '\\' && (inSingleQuotes || inDoubleQuotes) && !escaped) {
                                escaped = true;
                            } else {
                                if ((aChar == '\'') && (!inDoubleQuotes) && (!escaped)) {
                                    inSingleQuotes = !inSingleQuotes;
                                }

                                if ((aChar == '"') && (!inSingleQuotes) && (!escaped)) {
                                    inDoubleQuotes = !inDoubleQuotes;
                                }

                                if ((aChar == ';') && ((mCmdSep & CMDSEP_SEMICOLON) != 0)
                                        && (!inSingleQuotes) && (!inDoubleQuotes)) {
                                    break;
                                }

                                if (escaped) { // stay escaped for when this is actually parsed
                                    aCurrentLine.append('\\');
                                    escaped = false;
                                }

                                if (aCurrentLine.length() == 0) {
                                    mCurrentLineNum = aLineCount + 1;
                                }

                                aCurrentLine.append((char) aChar);
                            }
                        }
                    }
                }
            }

            if (aCurrentLine.length() > 0) {
                mCurrentLine = aCurrentLine.toString();
                if (!parseDescriptorLine(mCurrentLine)) {
                    hasErrors = true;
                    break;
                }

                mCurrentLine = "";
                aCurrentLine.setLength(0);
            }
        }

        mCurrentLine = "";
        mCurrentLineNum = 0;

        return !hasErrors;
    }
}
