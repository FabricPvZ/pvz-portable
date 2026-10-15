package cn.enaium.fabricpvz.portable.pvzp;

import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;

/**
 * Ported from src/PvzpLib/PvzpStringFile.h:37 and src/PvzpLib/PvzpStringFile.cpp:48
 *
 * <p>A named text style used by {@code {FORMAT}} control codes inside LawnStrings text, e.g.
 * {@code "Deals {STAT}normal damage to zombies"}. See {@code PvzpStringFile.gLawnStringFormats} for
 * the 12 built-in styles.
 *
 * <p>PORT-NOTE ({@code mNewFont}): upstream declares {@code _Font** mNewFont}, a pointer to the
 * caller's font variable, so a style can redirect the font slot. No PvZ caller ever installs a
 * non-null font pointer into a format entry (only {@code PvzpDrawStringWrappedHelper} sets its own
 * current format from the {@code theFont} argument), so this port stores the font directly;
 * {@code null} keeps the current font, exactly like the upstream {@code nullptr} check.
 */
public class PvzpStringListFormat {

    public String mFormatName;

    public Font mNewFont;

    public Color mNewColor;

    public int mLineSpacingOffset;

    public int mFormatFlags;

    public PvzpStringListFormat() {
        mFormatName = "";
        mNewFont = null;
        mNewColor = new Color();
        mLineSpacingOffset = 0;
        mFormatFlags = 0;
    }

    /**
     * Java stand-in for upstream's implicit copy construction (used when measuring). The colour is
     * copied by value like the C++ struct copy does, so a caller that mutates a copy's
     * {@code mNewColor} (e.g. the almanac's magnet-shroom alpha toggle) cannot corrupt the shared
     * table entry.
     */
    public PvzpStringListFormat(PvzpStringListFormat theFormat) {
        mFormatName = theFormat.mFormatName;
        mNewFont = theFormat.mNewFont;
        mNewColor = new Color(theFormat.mNewColor.mRed, theFormat.mNewColor.mGreen,
                theFormat.mNewColor.mBlue, theFormat.mNewColor.mAlpha);
        mLineSpacingOffset = theFormat.mLineSpacingOffset;
        mFormatFlags = theFormat.mFormatFlags;
    }

    /** Upstream {@code PvzpStringListFormat(const char*, _Font**, const Color&, int, unsigned int)}. */
    public PvzpStringListFormat(String theFormatName, Font theFont, Color theColor,
            int theLineSpacingOffset, int theFormatFlags) {
        mFormatName = theFormatName;
        mNewFont = theFont;
        mNewColor = theColor;
        mLineSpacingOffset = theLineSpacingOffset;
        mFormatFlags = theFormatFlags;
    }
}
