package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/widget/Insets.h:31 (implementation Insets.cpp:24).
 *
 * <p>Value type holding the four inset amounts of a widget. Public fields keep the upstream names.
 */
public class Insets {
    public int mLeft;
    public int mTop;
    public int mRight;
    public int mBottom;

    // Insets::Insets (Insets.cpp:24)
    public Insets() {
        mLeft = 0;
        mTop = 0;
        mRight = 0;
        mBottom = 0;
    }

    // Insets::Insets(int, int, int, int) (Insets.cpp:34)
    public Insets(int theLeft, int theTop, int theRight, int theBottom) {
        mLeft = theLeft;
        mTop = theTop;
        mRight = theRight;
        mBottom = theBottom;
    }
}
