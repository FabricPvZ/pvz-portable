package cn.enaium.fabricpvz.portable.pvzp.platform.sdl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLHints;
import org.lwjgl.sdl.SDL_HintCallbackI;
import org.lwjgl.system.MemoryUtil;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.pvzp.platform.PlatformBackend;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Tests for the SDL platform's event mapping.
 */
class SdlPlatformTest {

    /**
     * A high-density window reports a larger drawable than its logical size; the app must keep the
     * logical one, otherwise {@code SexyAppBase} adopts the drawable size as its canvas and the window
     * shows almost nothing (the canvas is drawn into the top-left quarter of the logical presentation).
     * Real case on this machine: an 800x600 window is 1600x1200 pixels on Retina.
     */
    @Test
    void resizeEventCarriesTheLogicalWindowSizeNotTheDrawableSize() {
        int[] resized = SdlPlatform.eventWindowSize(800, 600, 1600, 1200);
        assertArrayEquals(new int[] {800, 600}, resized);
    }

    @Test
    void resizeEventMatchesTheLogicalSizeWhenThereIsNoScaling() {
        int[] resized = SdlPlatform.eventWindowSize(1024, 768, 1024, 768);
        assertArrayEquals(new int[] {1024, 768}, resized);
    }

    /**
     * The frame loop sleeps once per frame that the present did not already cover. Sleeping must give up
     * the core: {@code SDL_DelayPrecise} busy-waits for the remainder, which pegged a CPU core and made the
     * machine feel laggy on displays whose refresh rate does not throttle the loop.
     */
    @Test
    void sleepingYieldsTheCoreInsteadOfSpinning() {
        LawnApp aApp = new LawnApp();
        HeadlessTestApp.initialize(aApp);
        SdlPlatform aPlatform = new SdlPlatform();
        com.sun.management.ThreadMXBean aThreads =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long aThreadId = Thread.currentThread().threadId();

        aPlatform.sleepMillis(5, 0); // warm-up
        long aCpuBefore = aThreads.getThreadCpuTime(aThreadId);
        long aWallBefore = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            aPlatform.sleepMillis(5, 0);
        }
        long aCpuNanos = aThreads.getThreadCpuTime(aThreadId) - aCpuBefore;
        long aWallMillis = (System.nanoTime() - aWallBefore) / 1_000_000L;

        org.junit.jupiter.api.Assertions.assertTrue(aWallMillis >= 800,
                "200 x 5 ms must take about a second of wall time, took " + aWallMillis + " ms");
        org.junit.jupiter.api.Assertions.assertTrue(aCpuNanos / 1_000_000L < aWallMillis / 2,
                "sleeping must not burn the core: " + aCpuNanos / 1_000_000L + " ms of CPU in "
                        + aWallMillis + " ms of wall time");
    }

    @Test
    void resizeEventTracksALogicalResize() {
        int[] resized = SdlPlatform.eventWindowSize(1024, 768, 2048, 1536);
        assertEquals(1024, resized[0]);
        assertEquals(768, resized[1]);
    }

    /**
     * The click that activates an unfocused window has to be delivered as a click too, and SDL's
     * desktop backends drop it by default: on macOS {@code -[SDL3View acceptsFirstMouse:]} returns
     * exactly this hint's value (its default is false), and the Windows, X11 and Wayland drivers ignore
     * the activating click unless it is set. Left unset, the click only activates the window and never
     * reaches the game, so one click appears to do nothing and a second click is needed.
     *
     * <p>The port therefore installs the hint itself, and it has to be in place before the window
     * exists: the first click can arrive as soon as the window is on screen. SDL calls a hint callback
     * whenever the hint changes, which is what makes the order observable - the callback fires while
     * {@link SdlPlatform#windowHandle()} is still 0.
     */
    @Test
    void clickthroughHintIsInstalledBeforeTheWindowIsCreated() {
        SdlPlatform aPlatform = new SdlPlatform();
        java.util.concurrent.atomic.AtomicLong aWindowWhenSet = new java.util.concurrent.atomic.AtomicLong(-1);
        java.util.concurrent.atomic.AtomicReference<String> aValueWhenSet =
                new java.util.concurrent.atomic.AtomicReference<>();
        SDL_HintCallbackI aWatch = (userdata, name, oldValue, newValue) -> {
            aWindowWhenSet.set(aPlatform.windowHandle());
            aValueWhenSet.set(newValue == 0 ? null : MemoryUtil.memUTF8(newValue));
        };
        SDLHints.SDL_AddHintCallback(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, aWatch, 0);
        try {
            aPlatform.init(new PlatformBackend.Options(320, 240, "clickthrough hint test", false, false, false));

            assertTrue(aPlatform.windowHandle() != 0, "init() creates the window");
            assertEquals("1", aValueWhenSet.get(), "the port installs the click-through hint itself");
            assertEquals(0, aWindowWhenSet.get(),
                    "the hint must be set before SDL_CreateWindow: a click can arrive as soon as the window is on screen");
            assertTrue(SDLHints.SDL_GetHintBoolean(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, false),
                    "SDL reads the hint back as enabled");
        } finally {
            SDLHints.SDL_RemoveHintCallback(SDLHints.SDL_HINT_MOUSE_FOCUS_CLICKTHROUGH, aWatch, 0);
            aPlatform.shutdown();
        }
    }
}
