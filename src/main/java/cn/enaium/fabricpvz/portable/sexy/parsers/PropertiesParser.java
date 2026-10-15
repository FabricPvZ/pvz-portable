package cn.enaium.fabricpvz.portable.sexy.parsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.enaium.fabricpvz.portable.sexy.Buffer;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;

/**
 * Ported from src/SexyAppFramework/misc/PropertiesParser.h:37 and
 * src/SexyAppFramework/misc/PropertiesParser.cpp:32
 *
 * <p>Loads PopCap's {@code properties/*.xml} files, which are XML documents of the shape:
 * <pre>
 *   &lt;Properties&gt;
 *     &lt;String id="NAME"&gt;text&lt;/String&gt;
 *     &lt;Boolean id="FLAG"&gt;true&lt;/Boolean&gt;
 *     &lt;Integer id="NUM"&gt;12&lt;/Integer&gt;
 *     &lt;Double id="F"&gt;1.5&lt;/Double&gt;
 *     &lt;StringArray id="LIST"&gt;&lt;String&gt;a&lt;/String&gt;&lt;String&gt;b&lt;/String&gt;&lt;/StringArray&gt;
 *   &lt;/Properties&gt;
 * </pre>
 * Values are stored on the app: {@code String} via {@link SexyAppBase#setString},
 * {@code Boolean}/{@code Integer}/{@code Double} via the matching setter, {@code StringArray}
 * straight into {@link SexyAppBase#mStringVectorProperties} (PropertiesParser.cpp:142-149).
 *
 * <p>Error text mirrors upstream: {@code Fail} only records the first error and appends
 * {@code " on Line N"} / {@code " in File 'x'"} when the underlying XML parser knows them; the
 * invalid-value messages are {@code "Invalid Section 'x'"}, {@code "Element Not Expected 'x'"},
 * {@code "Unexpected Section: 'x'"}, {@code "Invalid Boolean Value: 'x'"},
 * {@code "Invalid Integer Value: 'x'"}, {@code "Invalid Double Value: 'x'"}.
 */
public class PropertiesParser {

    public SexyAppBase mApp;

    /** Upstream {@code std::unique_ptr<XMLParser>}; released (set to null) when parsing finishes. */
    public XMLParser mXMLParser;

    public String mError = "";

    public boolean mHasFailed;

    public PropertiesParser(SexyAppBase theApp) {
        mApp = theApp;
        mHasFailed = false;
    }

    protected void fail(String theErrorText) {
        if (!mHasFailed) {
            mHasFailed = true;
            int aLineNum = mXMLParser.getCurrentLineNum();

            mError = theErrorText;
            if (aLineNum > 0) {
                mError += String.format(Locale.ROOT, " on Line %d", aLineNum);
            }
            if (!mXMLParser.getFileName().isEmpty()) {
                mError += String.format(Locale.ROOT, " in File '%s'", mXMLParser.getFileName());
            }
        }
    }

    protected boolean parseSingleElement(String[] aString) {
        aString[0] = "";

        for (; ; ) {
            XMLElement aXMLElement = new XMLElement();
            if (!mXMLParser.nextElement(aXMLElement)) {
                return false;
            }

            if (aXMLElement.mType == XMLElement.TYPE_START) {
                fail("Unexpected Section: '" + aXMLElement.mValue + "'");
                return false;
            } else if (aXMLElement.mType == XMLElement.TYPE_ELEMENT) {
                aString[0] = aXMLElement.mValue;
            } else if (aXMLElement.mType == XMLElement.TYPE_END) {
                return true;
            }
        }
    }

    protected boolean parseStringArray(List<String> theStringVector) {
        theStringVector.clear();

        for (; ; ) {
            XMLElement aXMLElement = new XMLElement();
            if (!mXMLParser.nextElement(aXMLElement)) {
                return false;
            }

            if (aXMLElement.mType == XMLElement.TYPE_START) {
                if ("String".equals(aXMLElement.mValue)) {
                    String[] aString = {""};

                    if (!parseSingleElement(aString)) {
                        return false;
                    }

                    theStringVector.add(aString[0]);
                } else {
                    fail("Invalid Section '" + aXMLElement.mValue + "'");
                    return false;
                }
            } else if (aXMLElement.mType == XMLElement.TYPE_ELEMENT) {
                fail("Element Not Expected '" + aXMLElement.mValue + "'");
                return false;
            } else if (aXMLElement.mType == XMLElement.TYPE_END) {
                return true;
            }
        }
    }

    protected boolean parseProperties() {
        for (; ; ) {
            XMLElement aXMLElement = new XMLElement();
            if (!mXMLParser.nextElement(aXMLElement)) {
                return false;
            }

            if (aXMLElement.mType == XMLElement.TYPE_START) {
                if ("String".equals(aXMLElement.mValue)) {
                    String[] aDef = {""};
                    if (!parseSingleElement(aDef)) {
                        return false;
                    }

                    String anId = aXMLElement.mAttributes.getOrDefault("id", "");
                    mApp.setString(anId, aDef[0]);
                } else if ("StringArray".equals(aXMLElement.mValue)) {
                    List<String> aDef = new ArrayList<>();

                    if (!parseStringArray(aDef)) {
                        return false;
                    }

                    String anId = aXMLElement.mAttributes.getOrDefault("id", "");

                    mApp.mStringVectorProperties.put(anId, aDef);
                } else if ("Boolean".equals(aXMLElement.mValue)) {
                    String[] aVal = {""};

                    if (!parseSingleElement(aVal)) {
                        return false;
                    }

                    aVal[0] = Common.upper(aVal[0]);

                    boolean boolVal;
                    if (("1".equals(aVal[0])) || ("YES".equals(aVal[0])) || ("ON".equals(aVal[0]))
                            || ("TRUE".equals(aVal[0]))) {
                        boolVal = true;
                    } else if (("0".equals(aVal[0])) || ("NO".equals(aVal[0])) || ("OFF".equals(aVal[0]))
                            || ("FALSE".equals(aVal[0]))) {
                        boolVal = false;
                    } else {
                        fail("Invalid Boolean Value: '" + aVal[0] + "'");
                        return false;
                    }

                    String anId = aXMLElement.mAttributes.getOrDefault("id", "");

                    mApp.setBoolean(anId, boolVal);
                } else if ("Integer".equals(aXMLElement.mValue)) {
                    String[] aVal = {""};

                    if (!parseSingleElement(aVal)) {
                        return false;
                    }

                    Integer anInt = Common.stringToInt(aVal[0]);
                    if (anInt == null) {
                        fail("Invalid Integer Value: '" + aVal[0] + "'");
                        return false;
                    }

                    String anId = aXMLElement.mAttributes.getOrDefault("id", "");

                    mApp.setInteger(anId, anInt);
                } else if ("Double".equals(aXMLElement.mValue)) {
                    String[] aVal = {""};

                    if (!parseSingleElement(aVal)) {
                        return false;
                    }

                    Double aDouble = Common.stringToDouble(aVal[0]);
                    if (aDouble == null) {
                        fail("Invalid Double Value: '" + aVal[0] + "'");
                        return false;
                    }

                    String anId = aXMLElement.mAttributes.getOrDefault("id", "");

                    mApp.setDouble(anId, aDouble);
                } else {
                    fail("Invalid Section '" + aXMLElement.mValue + "'");
                    return false;
                }
            } else if (aXMLElement.mType == XMLElement.TYPE_ELEMENT) {
                fail("Element Not Expected '" + aXMLElement.mValue + "'");
                return false;
            } else if (aXMLElement.mType == XMLElement.TYPE_END) {
                return true;
            }
        }
    }

    protected boolean doParseProperties() {
        if (!mXMLParser.hasFailed()) {
            for (; ; ) {
                XMLElement aXMLElement = new XMLElement();
                if (!mXMLParser.nextElement(aXMLElement)) {
                    break;
                }

                if (aXMLElement.mType == XMLElement.TYPE_START) {
                    if ("Properties".equals(aXMLElement.mValue)) {
                        if (!parseProperties()) {
                            break;
                        }
                    } else {
                        fail("Invalid Section '" + aXMLElement.mValue + "'");
                        break;
                    }
                } else if (aXMLElement.mType == XMLElement.TYPE_ELEMENT) {
                    fail("Element Not Expected '" + aXMLElement.mValue + "'");
                    break;
                }
            }
        }

        if (mXMLParser.hasFailed()) {
            fail(mXMLParser.getErrorText());
        }

        mXMLParser = null;

        return !mHasFailed;
    }

    public boolean parsePropertiesBuffer(Buffer theBuffer) {
        mXMLParser = new XMLParser();

        String aString = theBuffer.toUTF8String();
        if (aString == null) {
            fail("Failed to convert properties buffer to UTF-8 string");
            return false;
        }

        mXMLParser.setStringSource(aString);
        return doParseProperties();
    }

    public boolean parsePropertiesFile(String theFilename) {
        mXMLParser = new XMLParser();
        mXMLParser.openFile(theFilename);
        return doParseProperties();
    }

    public String getErrorText() {
        return mError;
    }
}
