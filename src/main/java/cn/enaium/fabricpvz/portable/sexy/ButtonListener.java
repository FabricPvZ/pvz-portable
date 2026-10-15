package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/widget/ButtonListener.h:27.
 *
 * <p>Upstream declares these as pure virtual; the Java port keeps the camelCase names. The
 * {@code checked} argument of the upstream {@code ButtonMouseMove} overload set is
 * {@code ButtonMouseMove(int theId, int theX, int theY)}.
 */
public interface ButtonListener {
    // ButtonListener.h:31
    void buttonPress(int theId);

    // ButtonListener.h:32
    void buttonDepress(int theId);

    // ButtonListener.h:33
    void buttonDownTick(int theId);

    // ButtonListener.h:34
    void buttonMouseEnter(int theId);

    // ButtonListener.h:35
    void buttonMouseLeave(int theId);

    // ButtonListener.h:36-40
    void buttonMouseMove(int theId, int theX, int theY);
}
