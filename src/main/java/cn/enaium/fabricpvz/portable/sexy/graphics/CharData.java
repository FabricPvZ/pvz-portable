package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.sexy.Rect;

import java.util.HashMap;
import java.util.Map;

/**
 * Ported from src/SexyAppFramework/graphics/ImageFont.h:42 (class CharData) and
 * src/SexyAppFramework/graphics/ImageFont.cpp:97 (CharData::CharData).
 *
 * <p>Per-character data of a {@link FontLayer}: the glyph's source rectangle inside the layer image,
 * its draw offset, the per-following-character kerning offsets, the advance width and the render
 * order bucket.
 *
 * <p>Java deviation: {@code std::map<char32_t,int> mKerningOffsets} becomes
 * {@code Map<Integer,Integer>}; {@code char32_t} keys are {@code Integer} code points.
 */
public class CharData {

    public Rect mImageRect;
    public Point mOffset;
    public Map<Integer, Integer> mKerningOffsets;
    public int mWidth;
    public int mOrder;

    /** Ported from src/SexyAppFramework/graphics/ImageFont.cpp:97 (CharData::CharData). */
    public CharData() {
        mImageRect = new Rect();
        mOffset = new Point();
        mKerningOffsets = new HashMap<>();
        mWidth = 0;
        mOrder = 0;
    }

    /**
     * Java helper: kerning offset for the given following character, defaulting to 0 like
     * {@code map::operator[]} does upstream.
     */
    public int kerningOffset(int theChar) {
        Integer anOffset = mKerningOffsets.get(theChar);
        return anOffset != null ? anOffset : 0;
    }
}
