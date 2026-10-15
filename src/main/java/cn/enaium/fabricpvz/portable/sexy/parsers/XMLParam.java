package cn.enaium.fabricpvz.portable.sexy.parsers;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/misc/XMLParser.h:39
 *
 * <p>Upstream {@code XMLParam} is a vestigial key/value holder that the parser itself never
 * instantiates; it is kept because it belongs to the public XML parser API.
 */
public class XMLParam {

    public String mKey;

    public String mValue;

    public XMLParam() {
    }

    public XMLParam(String theKey, String theValue) {
        mKey = theKey;
        mValue = theValue;
    }
}
