package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/widget/ScrollListener.h:27.
 *
 * <p>Implemented by the widgets that own a {@code ScrollbarWidget} ({@code TextWidget},
 * {@code ListWidget}); {@code ListWidget} is ported (and uses {@code ScrollbarWidget}), while
 * {@code TextWidget} is unused by the game and is therefore not ported (see the widget slice
 * summary).
 */
public interface ScrollListener {
    // ScrollListener.h:31
    void scrollPosition(int theId, double thePosition);
}
