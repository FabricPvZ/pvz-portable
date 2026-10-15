package cn.enaium.fabricpvz.portable.sexy.graphics;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Ported from src/SexyAppFramework/graphics/Font.h:38 (class _Font) and
 * src/SexyAppFramework/graphics/Font.cpp:29-77.
 *
 * <p>Abstract font interface. In the target package it is named {@code Font} (upstream {@code _Font});
 * the public fields keep their upstream names. It carries exactly the four int metrics
 * {@code mAscent}, {@code mAscentPadding}, {@code mHeight} and {@code mLineSpacingOffset}; scale lives
 * on {@link ImageFont} and on {@link Graphics}, not here.
 *
 * <p>Java deviation: {@code char32_t} becomes {@code int} code points (use
 * {@link String#codePointAt(int)} / {@link String#offsetByCodePoints}); {@code std::string_view}
 * becomes {@link String}.
 */
public abstract class Font {

    public int mAscent;
    public int mAscentPadding; // How much space is above the avg uppercase char
    public int mHeight;
    public int mLineSpacingOffset; // This plus height should get added between lines

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:29 (_Font::_Font). */
    public Font() {
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:37 (_Font::_Font(const _Font&)). */
    public Font(Font theFont) {
        mAscent = theFont.mAscent;
        mAscentPadding = theFont.mAscentPadding;
        mHeight = theFont.mHeight;
        mLineSpacingOffset = theFont.mLineSpacingOffset;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:49 (_Font::GetAscent). */
    public int getAscent() {
        return mAscent;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:54 (_Font::GetAscentPadding). */
    public int getAscentPadding() {
        return mAscentPadding;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:59 (_Font::GetDescent). */
    public int getDescent() {
        return mHeight - mAscent;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:64 (_Font::GetHeight). */
    public int getHeight() {
        return mHeight;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:69 (_Font::GetLineSpacingOffset). */
    public int getLineSpacingOffset() {
        return mLineSpacingOffset;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.cpp:74 (_Font::GetLineSpacing). */
    public int getLineSpacing() {
        return mHeight + mLineSpacingOffset;
    }

    /** Ported from src/SexyAppFramework/graphics/Font.h:55 (_Font::StringWidth). */
    public abstract int stringWidth(String theString);

    /** Ported from src/SexyAppFramework/graphics/Font.h:56 (_Font::CharWidth). */
    public abstract int charWidth(int theChar);

    /** Ported from src/SexyAppFramework/graphics/Font.h:57 (_Font::CharWidthKern). */
    public abstract int charWidthKern(int theChar, int thePrevChar);

    /** Ported from src/SexyAppFramework/graphics/Font.h:59 (_Font::DrawString). */
    public abstract void drawString(Graphics g, int theX, int theY, String theString, Color theColor, Rect theClipRect);

    /** Ported from src/SexyAppFramework/graphics/Font.h:61 (_Font::Duplicate). */
    public abstract Font duplicate();
}
