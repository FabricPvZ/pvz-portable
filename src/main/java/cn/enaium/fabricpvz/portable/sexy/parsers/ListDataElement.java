package cn.enaium.fabricpvz.portable.sexy.parsers;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from src/SexyAppFramework/misc/DescParser.h:61 (bodies in
 * src/SexyAppFramework/graphics/ImageFont.cpp:64-95 upstream).
 *
 * <p>A parenthesized list of descriptors; elements are either {@link SingleDataElement} values or
 * nested {@link ListDataElement}s, matching upstream's deep-copy semantics.
 */
public class ListDataElement extends DataElement {

    public List<DataElement> mElementVector = new ArrayList<>();

    public ListDataElement() {
        mIsList = true;
    }

    /** Upstream copy constructor: deep-copies every contained element. */
    public ListDataElement(ListDataElement theListDataElement) {
        mIsList = true;
        for (DataElement anElement : theListDataElement.mElementVector) {
            mElementVector.add(anElement.duplicate());
        }
    }

    /**
     * Java stand-in for upstream's deep-copy {@code operator=}: clears this list and copies the
     * other list's elements, duplicating each of them.
     */
    public void copyFrom(ListDataElement theListDataElement) {
        mElementVector.clear();
        for (DataElement anElement : theListDataElement.mElementVector) {
            mElementVector.add(anElement.duplicate());
        }
    }

    @Override
    public DataElement duplicate() {
        return new ListDataElement(this);
    }
}
