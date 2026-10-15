package cn.enaium.fabricpvz.portable.pvzp.platform;

/**
 * One platform event produced by {@link PlatformBackend#pollEvent}.
 *
 * <p>Instances are reused by the backend (allocation free polling): the framework must consume the
 * fields before polling again, exactly like the upstream SDL event loop.
 *
 * <p>Mapping notes: upstream {@code platform/default/Input.cpp:540-791} translates SDL events into
 * {@code Sexy::KeyCode}/{@code KeyCode} values and a mouse button convention where 1 = left,
 * 2 = middle, 3 = right is normalized to the framework's own constants; this class carries the
 * already-translated values.
 */
public final class PlatformEvent {

    public enum Type {
        /** Mouse moved: {@link #x}/{@link #y} are window pixel coordinates, {@link #dx}/{@link #dy} the relative delta. */
        MOUSE_MOTION,
        /** Mouse button pressed; {@link #button} carries the framework button id (1 left, 2 middle, 3 right). */
        MOUSE_DOWN,
        /** Mouse button released. */
        MOUSE_UP,
        /** Wheel scrolled; {@link #dy} is the vertical amount (positive = up), {@link #dx} horizontal. */
        MOUSE_WHEEL,
        /** Key pressed; {@link #keyCode} is a {@code KeyCode} value, {@link #repeat} marks auto-repeat. */
        KEY_DOWN,
        /** Key released. */
        KEY_UP,
        /** Text input; {@link #text} holds the UTF-8 characters produced by the key stroke. */
        TEXT_INPUT,

    /** The pointer left the window (SDL_EVENT_WINDOW_MOUSE_LEAVE). */
    MOUSE_LEAVE,
        /** Window resized; {@link #width}/{@link #height} are the new pixel dimensions. */
        WINDOW_RESIZE,
        WINDOW_FOCUS_GAINED,
        WINDOW_FOCUS_LOST,
        /** Window close requested (user pressed the close button). */
        WINDOW_CLOSE,
        /** Application quit requested (Cmd+Q / OS session end). */
        QUIT,
        /** Files dropped on the window; {@link #fileName} carries the first path. */
        DROP_FILE
    }

    public Type type;
    public int x;
    public int y;
    public int dx;
    public int dy;
    public int button;
    public int keyCode;
    public boolean repeat;
    public String text;
    public int width;
    public int height;
    public String fileName;

    /** Clears all fields before the backend fills them in. */
    public void reset() {
        type = null;
        x = 0;
        y = 0;
        dx = 0;
        dy = 0;
        button = 0;
        keyCode = 0;
        repeat = false;
        text = null;
        width = 0;
        height = 0;
        fileName = null;
    }
}
