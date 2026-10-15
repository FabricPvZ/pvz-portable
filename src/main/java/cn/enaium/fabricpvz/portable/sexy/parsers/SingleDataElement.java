package cn.enaium.fabricpvz.portable.sexy.parsers;

/**
 * Ported from src/SexyAppFramework/misc/DescParser.h:46 (bodies in
 * src/SexyAppFramework/graphics/ImageFont.cpp:45-62 upstream).
 */
public class SingleDataElement extends DataElement {

    public String mString = "";

    public SingleDataElement() {
        mIsList = false;
    }

    public SingleDataElement(String theString) {
        mString = theString;
        mIsList = false;
    }

    @Override
    public DataElement duplicate() {
        return new SingleDataElement(mString);
    }
}
