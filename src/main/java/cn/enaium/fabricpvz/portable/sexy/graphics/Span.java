package cn.enaium.fabricpvz.portable.sexy.graphics;

/**
 * Ported from src/SexyAppFramework/graphics/Image.h:37.
 *
 * <p>Faithful port of {@code Sexy::Span}: a single horizontal run of pixels used by the
 * scan-line fill helpers. The public fields keep their upstream names.
 */
public class Span {
    public int mY;
    public int mX;
    public int mWidth;

    public Span() {
    }

    public Span(int theX, int theY, int theWidth) {
        mX = theX;
        mY = theY;
        mWidth = theWidth;
    }
}
