package cn.enaium.fabricpvz.portable.sexy.parsers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/misc/XMLParser.h:51
 *
 * <p>Upstream calls this {@code XMLElement} (there is no {@code XMLNode} in the framework).
 * The public fields mirror the C++ struct member-for-member.
 */
public class XMLElement {

    /** Upstream {@code XMLElement::TYPE_NONE}. */
    public static final int TYPE_NONE = 0;
    /** Upstream {@code XMLElement::TYPE_START}. */
    public static final int TYPE_START = 1;
    /** Upstream {@code XMLElement::TYPE_END}. */
    public static final int TYPE_END = 2;
    /** Upstream {@code XMLElement::TYPE_ELEMENT}. */
    public static final int TYPE_ELEMENT = 3;
    /** Upstream {@code XMLElement::TYPE_INSTRUCTION}. */
    public static final int TYPE_INSTRUCTION = 4;
    /** Upstream {@code XMLElement::TYPE_COMMENT}. */
    public static final int TYPE_COMMENT = 5;

    public int mType;

    public String mSection = "";

    public String mValue = "";

    public String mInstruction = "";

    /**
     * Upstream {@code XMLParamMap} is {@code std::map<std::string, std::string>}; all upstream
     * consumers only look attributes up by key, so this port preserves the attribute encounter
     * order instead (last write wins for a repeated key, exactly like upstream).
     */
    public Map<String, String> mAttributes = new LinkedHashMap<>();

    /**
     * Upstream {@code XMLParamMapIteratorList} stores iterators into {@code mAttributes} in their
     * original order. Java map iterators cannot be stored, so this holds the attribute keys in the
     * same order instead (a repeated key is appended again, exactly like upstream re-appends the
     * same iterator).
     */
    public List<String> mAttributeIteratorList = new ArrayList<>();
}
