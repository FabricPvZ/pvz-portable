package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMessageBox;
import org.lwjgl.sdl.SDLMisc;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_DropEvent;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.sdl.SDL_MouseButtonEvent;
import org.lwjgl.sdl.SDL_MouseMotionEvent;
import org.lwjgl.sdl.SDL_MouseWheelEvent;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.sdl.SDL_WindowEvent;
import org.lwjgl.system.MemoryUtil;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformBackend;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformEvent;

/**
 * SDL3 implementation of {@link PlatformBackend}.
 *
 * <p>Ports the behaviour of src/SexyAppFramework/platform/default/Window.cpp (window creation) and
 * Input.cpp (event translation) onto SDL3 through the LWJGL {@code lwjgl-sdl} bindings. The upstream
 * port also created an OpenGL context here; this port draws through {@link SdlRenderer} instead, so
 * only video/event/cursor/clipboard/message-box services live here.
 *
 * <p>Platform notes verified on macOS: SDL requires the JVM to run with {@code -XstartOnFirstThread},
 * and with {@code SDL_VIDEODRIVER=dummy} the whole backend works headlessly (SDL then reports the
 * software renderer).
 */
public final class SdlPlatform implements PlatformBackend {

    private long mWindow;
    private boolean mInitialized;
    private boolean mQuitRequested;
    private boolean mCursorHidden;

    private final ByteBuffer mEventBuffer = MemoryUtil.memAlloc(SDL_Event.SIZEOF);
    private final SDL_Event mEvent = new SDL_Event(mEventBuffer);
    private final IntBuffer mSizeA = MemoryUtil.memAllocInt(1);
    private final IntBuffer mSizeB = MemoryUtil.memAllocInt(1);

    private final long[] mSystemCursors = new long[CursorType.values().length];
    private long mCustomCursor;
    private ByteBuffer mCustomCursorPixels;

    private SdlRenderer mRenderer;
    private int mWidth = 800;
    private int mHeight = 600;
    private int mPixelWidth = 800;
    private int mPixelHeight = 600;

    @Override
    public void init(Options options) {
        if (mInitialized) {
            throw new IllegalStateException("platform already initialized");
        }
        // The click that activates an unfocused window has to be delivered as a click too. SDL's
        // desktop backends drop it by default: on macOS -[SDL3View acceptsFirstMouse:] returns exactly
        // this hint's value, and its default is false, while the Windows, X11 and Wayland drivers ignore
        // the activating click unless the hint is set. Left unset, the first click after the window
        // loses focus only activates the window and never reaches the game, so one click appears to do
        // nothing and a second click is needed for the press to register. SDL reads the hint when a
        // click arrives rather than when the window is created, but it is installed here, before the
        // window exists, so the very first click on it is already covered.
        SDLHints.SDL_SetHint(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, "1");
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            throw new IllegalStateException("SDL_Init(SDL_INIT_VIDEO) failed: " + SDLError.SDL_GetError());
        }
        long flags = 0;
        // Upstream creates the window with SDL_WINDOW_OPENGL | SDL_WINDOW_RESIZABLE and no high-density
        // flag (platform/default/Window.cpp:59), so the drawable matches the window's own size: the
        // framework draws in window coordinates and nothing rescales them. Requesting a high-density
        // drawable instead (an 800x600 window becoming 1600x1200 pixels on Retina) makes every draw go
        // through SDL's logical-presentation scaling, and quads drawn with rotation/skew come out
        // distorted on the Metal backend. The 800x600 output is upscaled at present instead, which is
        // what the original game does on a Retina display.
        if (options.resizable()) {
            flags |= SDLVideo.SDL_WINDOW_RESIZABLE;
        }
        if (options.hidden()) {
            flags |= SDLVideo.SDL_WINDOW_HIDDEN;
        }
        if (options.fullscreen()) {
            // Upstream asks for SDL_WINDOW_FULLSCREEN_DESKTOP at creation (platform/default/Window.cpp:60).
            flags |= SDLVideo.SDL_WINDOW_FULLSCREEN;
        }
        mWindow = SDLVideo.SDL_CreateWindow(options.title(), options.width(), options.height(), flags);
        if (mWindow == 0) {
            SDLInit.SDL_Quit();
            throw new IllegalStateException("SDL_CreateWindow failed: " + SDLError.SDL_GetError());
        }
        mInitialized = true;
        updateWindowSize();
        // Showing a window is not activating it. SDL_CreateWindow already shows a window that was not
        // created hidden, but on macOS that path is -[NSWindow makeKeyAndOrderFront:] on an application
        // that is not active, and SDL's own launch path does not activate on macOS 14+ (there the
        // SDL_MAC_BACKGROUND_APP default makes SDL skip its -[NSApp activateIgnoringOtherApps:] call).
        // The window therefore opened behind whatever launched it, with the application not active and
        // no key window, so a later Dock icon click had no focused window to hand focus back to.
        // SDL_RaiseWindow is the SDL API for this: its macOS backend activates the application and then
        // orders the window front, and the other desktop backends raise and activate through the window
        // manager. A window created hidden is left alone (SDL returns early for SDL_WINDOW_HIDDEN).
        SDLVideo.SDL_RaiseWindow(mWindow);
        // SDL3 keeps text input off until a window asks for it, while SDL2 (which upstream is written
        // against) enabled it for desktop windows automatically. Without this no SDL_EVENT_TEXT_INPUT is
        // ever delivered, so nothing that takes typed text works: the new-user dialog's name field, the
        // cheat dialog and the zombatar fields all stay empty however much you type.
        SDLKeyboard.SDL_StartTextInput(mWindow);
    }

    /** Underlying {@code SDL_Window*} handle (0 before {@link #init}). */
    public long windowHandle() {
        return mWindow;
    }

    /**
     * Creates the 2D renderer for this window and remembers it for {@link #swapBuffers()}.
     * Uses SDL's default driver on desktop; pass {@code "software"} for headless runs.
     */
    public SdlRenderer createRenderer(int logicalWidth, int logicalHeight, String driver, boolean softwareFallback) {
        if (!mInitialized) {
            throw new IllegalStateException("platform not initialized");
        }
        SdlRenderer renderer;
        try {
            renderer = SdlRenderer.forWindow(mWindow, driver);
        } catch (IllegalStateException e) {
            if (!softwareFallback) {
                throw e;
            }
            // Headless (SDL_VIDEODRIVER=dummy) has no GPU driver: fall back to the software renderer
            // on the window surface, which SDL3 supports for window+renderer combinations.
            renderer = SdlRenderer.software(mPixelWidth, mPixelHeight);
        }
        renderer.setLogicalSize(logicalWidth, logicalHeight);
        mRenderer = renderer;
        return renderer;
    }

    public SdlRenderer renderer() {
        return mRenderer;
    }

    @Override
    public boolean pollEvent(PlatformEvent out) {
        out.reset();
        if (!mInitialized || !SDLEvents.SDL_PollEvent(mEvent)) {
            return false;
        }
        switch (mEvent.type()) {
            case SDLEvents.SDL_EVENT_QUIT -> {
                mQuitRequested = true;
                out.type = PlatformEvent.Type.QUIT;
            }
            case SDLEvents.SDL_EVENT_KEY_DOWN, SDLEvents.SDL_EVENT_KEY_UP -> {
                SDL_KeyboardEvent key = mEvent.key();
                out.type = mEvent.type() == SDLEvents.SDL_EVENT_KEY_DOWN
                        ? PlatformEvent.Type.KEY_DOWN
                        : PlatformEvent.Type.KEY_UP;
                out.keyCode = SdlKeys.toKeyCode(key.key());
                out.repeat = key.repeat();
            }
            case SDLEvents.SDL_EVENT_TEXT_INPUT -> {
                out.type = PlatformEvent.Type.TEXT_INPUT;
                out.text = MemoryUtil.memUTF8(mEvent.text().text());
            }
            case SDLEvents.SDL_EVENT_MOUSE_MOTION -> {
                SDL_MouseMotionEvent motion = mEvent.motion();
                out.type = PlatformEvent.Type.MOUSE_MOTION;
                out.x = (int) motion.x();
                out.y = (int) motion.y();
                out.dx = (int) motion.xrel();
                out.dy = (int) motion.yrel();
            }
            case SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN, SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP -> {
                SDL_MouseButtonEvent button = mEvent.button();
                out.type = mEvent.type() == SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN
                        ? PlatformEvent.Type.MOUSE_DOWN
                        : PlatformEvent.Type.MOUSE_UP;
                out.x = (int) button.x();
                out.y = (int) button.y();
                out.button = button.button();
            }
            case SDLEvents.SDL_EVENT_MOUSE_WHEEL -> {
                SDL_MouseWheelEvent wheel = mEvent.wheel();
                out.type = PlatformEvent.Type.MOUSE_WHEEL;
                out.dx = wheel.integer_x();
                out.dy = wheel.integer_y();
            }
            case SDLEvents.SDL_EVENT_WINDOW_RESIZED, SDLEvents.SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED -> {
                updateWindowSize();
                out.type = PlatformEvent.Type.WINDOW_RESIZE;
                int[] aLogical = eventWindowSize(mWidth, mHeight, mPixelWidth, mPixelHeight);
                out.width = aLogical[0];
                out.height = aLogical[1];
            }
            case SDLEvents.SDL_EVENT_WINDOW_MOUSE_LEAVE -> out.type = PlatformEvent.Type.MOUSE_LEAVE;
            case SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED -> out.type = PlatformEvent.Type.WINDOW_FOCUS_GAINED;
            case SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST -> out.type = PlatformEvent.Type.WINDOW_FOCUS_LOST;
            case SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED -> out.type = PlatformEvent.Type.WINDOW_CLOSE;
            case SDLEvents.SDL_EVENT_DROP_FILE -> {
                SDL_DropEvent drop = mEvent.drop();
                ByteBuffer data = drop.data();
                if (data != null) {
                    out.type = PlatformEvent.Type.DROP_FILE;
                    out.fileName = MemoryUtil.memUTF8(data);
                }
            }
            default -> out.type = null;
        }
        return out.type != null;
    }

    /**
     * Window size to report in a {@link PlatformEvent.Type#WINDOW_RESIZE} event: the <em>logical</em>
     * window size, never the drawable size.
     *
     * <p>Upstream's app lays out in window coordinates. On a high-density display the drawable is
     * larger - an 800x600 window is 1600x1200 pixels on Retina - and {@code SexyAppBase} adopts the
     * size from the event as its canvas ({@code mWidth}/{@code mHeight}, then
     * {@code WidgetManager.resize}). Feeding the drawable size back would make the game draw a
     * 1600x1200 canvas into an 800x600 logical presentation, leaving the window showing little more
     * than the top-left quarter. The drawable size stays available to the renderer through
     * {@link RendererBackend#renderOutputWidth()} / {@link RendererBackend#renderOutputHeight()}.
     *
     * @param theLogicalWidth  {@code SDL_GetWindowSize} width
     * @param theLogicalHeight {@code SDL_GetWindowSize} height
     * @param thePixelWidth    {@code SDL_GetWindowSizeInPixels} width (unused, documents the input)
     * @param thePixelHeight   {@code SDL_GetWindowSizeInPixels} height (unused)
     * @return a two-element array with the logical width and height
     */
    static int[] eventWindowSize(int theLogicalWidth, int theLogicalHeight,
            int thePixelWidth, int thePixelHeight) {
        return new int[] {theLogicalWidth, theLogicalHeight};
    }

    private void updateWindowSize() {
        if (mWindow == 0) {
            return;
        }
        if (SDLVideo.SDL_GetWindowSize(mWindow, mSizeA, mSizeB)) {
            mWidth = mSizeA.get(0);
            mHeight = mSizeB.get(0);
        }
        if (SDLVideo.SDL_GetWindowSizeInPixels(mWindow, mSizeA, mSizeB)) {
            mPixelWidth = mSizeA.get(0);
            mPixelHeight = mSizeB.get(0);
        }
    }

    /** True once an SDL_EVENT_QUIT was observed; upstream sets {@code mCloseRequest}. */
    public boolean quitRequested() {
        return mQuitRequested;
    }

    @Override
    public void swapBuffers() {
        if (mRenderer != null) {
            mRenderer.present();
        }
    }

    @Override
    public long ticksMillis() {
        return SDLTimer.SDL_GetTicks();
    }

    @Override
    public void sleepMillis(long millis, int nanos) {
        long totalNanos = millis * 1_000_000L + nanos;
        if (totalNanos <= 0) {
            return;
        }
        // Upstream sleeps with nanosleep (SexyAppBase.cpp:2744). SDL_DelayPrecise - the precise variant -
        // busy-waits for the remainder instead of yielding the core, and the frame loop reaches this path
        // on every frame whose present did not already consume the frame budget (a display faster than the
        // 100 Hz update rate, or one without vsync). That pinned a core and made the whole machine feel
        // laggy while the game reported a full frame rate.
        long aMillis = totalNanos / 1_000_000L;
        if (aMillis > 0) {
            SDLTimer.SDL_Delay((int) aMillis);
        } else {
            // Sub-millisecond remainders: yield rather than spin.
            Thread.yield();
        }
    }

    @Override
    public void setFullscreen(boolean theFullscreen) {
        if (mWindow == 0) {
            return;
        }
        // Upstream ignores the result (platform/default/Window.cpp:46). A backend that cannot switch (the
        // dummy video driver used by headless runs) must not take the game down with it.
        if (!SDLVideo.SDL_SetWindowFullscreen(mWindow, theFullscreen)) {
            cn.enaium.fabricpvz.portable.sexy.Common.logErrorLn("SDL_SetWindowFullscreen({}) failed: {}", theFullscreen,
                    SDLError.SDL_GetError());
        }
        SDLVideo.SDL_SyncWindow(mWindow);
        updateWindowSize();
    }

    @Override
    public void setTitle(String title) {
        if (mWindow != 0) {
            SDLVideo.SDL_SetWindowTitle(mWindow, title);
        }
    }

    @Override
    public int windowWidth() {
        return mWidth;
    }

    @Override
    public int windowHeight() {
        return mHeight;
    }

    @Override
    public int pixelWidth() {
        return mPixelWidth;
    }

    @Override
    public int pixelHeight() {
        return mPixelHeight;
    }

    @Override
    public void setClipboardText(String text) {
        SDLClipboard.SDL_SetClipboardText(text);
    }

    @Override
    public String getClipboardText() {
        return SDLClipboard.SDL_GetClipboardText();
    }

    @Override
    public void setCursor(CursorType cursor) {
        long handle = systemCursor(cursor);
        if (handle != 0) {
            SDLMouse.SDL_SetCursor(handle);
        }
    }

    private long systemCursor(CursorType cursor) {
        int index = cursor.ordinal();
        if (mSystemCursors[index] != 0) {
            return mSystemCursors[index];
        }
        int sdlId = switch (cursor) {
            case ARROW -> SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT;
            case HAND -> SDLMouse.SDL_SYSTEM_CURSOR_POINTER;
            case IBEAM -> SDLMouse.SDL_SYSTEM_CURSOR_TEXT;
            case CROSSHAIR -> SDLMouse.SDL_SYSTEM_CURSOR_CROSSHAIR;
            case HRESIZE -> SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE;
            case VRESIZE -> SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE;
            case NOT_ALLOWED -> SDLMouse.SDL_SYSTEM_CURSOR_NOT_ALLOWED;
        };
        long created = SDLMouse.SDL_CreateSystemCursor(sdlId);
        mSystemCursors[index] = created;
        return created;
    }

    @Override
    public void setCustomCursor(Image cursorImage, int hotX, int hotY) {
        if (cursorImage == null) {
            destroyCustomCursor();
            return;
        }
        if (!(cursorImage instanceof MemoryImage memoryImage)) {
            throw new IllegalArgumentException("custom cursors must be software images, got "
                    + cursorImage.getClass().getName());
        }
        int width = memoryImage.mWidth;
        int height = memoryImage.mHeight;
        int[] bits = memoryImage.mBits;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        pixels.asIntBuffer().put(bits, 0, width * height);
        SDL_Surface surface =
                SDLSurface.SDL_CreateSurfaceFrom(width, height, SDLPixels.SDL_PIXELFORMAT_ARGB8888, pixels, width * 4);
        if (surface == null) {
            MemoryUtil.memFree(pixels);
            throw new IllegalStateException("SDL_CreateSurfaceFrom failed: " + SDLError.SDL_GetError());
        }
        long cursor = SDLMouse.SDL_CreateColorCursor(surface, hotX, hotY);
        SDLSurface.SDL_DestroySurface(surface);
        if (cursor == 0) {
            MemoryUtil.memFree(pixels);
            throw new IllegalStateException("SDL_CreateColorCursor failed: " + SDLError.SDL_GetError());
        }
        destroyCustomCursor();
        mCustomCursor = cursor;
        mCustomCursorPixels = pixels;
        SDLMouse.SDL_SetCursor(cursor);
    }

    private void destroyCustomCursor() {
        if (mCustomCursor != 0) {
            SDLMouse.SDL_DestroyCursor(mCustomCursor);
            mCustomCursor = 0;
        }
        if (mCustomCursorPixels != null) {
            MemoryUtil.memFree(mCustomCursorPixels);
            mCustomCursorPixels = null;
        }
    }

    @Override
    public void hideCursor(boolean hide) {
        if (hide == mCursorHidden) {
            return;
        }
        mCursorHidden = hide;
        if (hide) {
            SDLMouse.SDL_HideCursor();
        } else {
            SDLMouse.SDL_ShowCursor();
        }
    }

    @Override
    public void showMessageBox(String title, String message, boolean isError) {
        int flags = isError ? SDLMessageBox.SDL_MESSAGEBOX_ERROR : SDLMessageBox.SDL_MESSAGEBOX_INFORMATION;
        SDLMessageBox.SDL_ShowSimpleMessageBox(flags, title, message, mWindow);
    }

    @Override
    public void openURL(String url) {
        if (!SDLMisc.SDL_OpenURL(url)) {
            throw new IllegalStateException("SDL_OpenURL failed: " + SDLError.SDL_GetError());
        }
    }

    @Override
    public void shutdown() {
        if (!mInitialized) {
            return;
        }
        mInitialized = false;
        if (mRenderer != null) {
            mRenderer.close();
            mRenderer = null;
        }
        destroyCustomCursor();
        for (int i = 0; i < mSystemCursors.length; i++) {
            if (mSystemCursors[i] != 0) {
                SDLMouse.SDL_DestroyCursor(mSystemCursors[i]);
                mSystemCursors[i] = 0;
            }
        }
        if (mWindow != 0) {
            SDLKeyboard.SDL_StopTextInput(mWindow);
            SDLVideo.SDL_DestroyWindow(mWindow);
            mWindow = 0;
        }
        SDLInit.SDL_Quit();
        MemoryUtil.memFree(mEventBuffer);
        MemoryUtil.memFree(mSizeA);
        MemoryUtil.memFree(mSizeB);
    }
}
