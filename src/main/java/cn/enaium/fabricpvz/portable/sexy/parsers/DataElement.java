package cn.enaium.fabricpvz.portable.sexy.parsers;

/**
 * Ported from src/SexyAppFramework/misc/DescParser.h:34 (bodies for the trio live in
 * src/SexyAppFramework/graphics/ImageFont.cpp:38-95 upstream).
 *
 * <p>Base class of the descriptor data tree: either a quoted/immediate string
 * ({@link SingleDataElement}) or a parenthesized list ({@link ListDataElement}).
 */
public abstract class DataElement {

    public boolean mIsList;

    public DataElement() {
        mIsList = false;
    }

    /** Upstream pure virtual {@code Duplicate()}. */
    public abstract DataElement duplicate();
}
