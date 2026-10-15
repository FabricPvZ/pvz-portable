package cn.enaium.fabricpvz.portable.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import cn.enaium.fabricpvz.portable.lawn.GameScenes;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Main;

/**
 * Boots the game with the real video driver, drives the frame loop and writes what the window shows
 * into {@code build/window-dump/} (run through {@code ./gradlew dumpWindow}), together with the
 * logical and drawable sizes each frame.
 *
 * <p>Two things matter for a dependable dump. The widget tree is drawn with
 * {@code WidgetManager.drawScreen()} and read back <em>before</em> {@code SDL_RenderPresent}: after a
 * present the GPU back-buffer is undefined, so reading it then is meaningless (SDL returns a blank
 * surface on Metal). And every read-back happens on the frame-loop thread: SDL's renderer is not
 * thread safe and reading from another thread aborts the process inside Metal's command encoder.
 */
public final class WindowDump {

    private WindowDump() {
    }

    public static void main(String[] args) throws Exception {
        Path dumpDir = Path.of("build", "window-dump").toAbsolutePath();
        Path saveDir = dumpDir.resolve("userdata");
        Files.createDirectories(saveDir);

        Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        try {
            app.setArgs(new String[] {"pvz-portable", "-savedir", saveDir.toString()});
            app.init();
            app.startLoadingThread();

            int frames = 30;
            for (int frame = 1; frame <= frames; frame++) {
                long frameEnd = System.nanoTime() + 500_000_000L;
                while (System.nanoTime() < frameEnd) {
                    // Same order as the main loop: input/window events, update, draw.
                    app.processDeferredMessages(false);
                    app.doUpdateFrames();
                    app.mWidgetManager.drawScreen();
                    Thread.sleep(5);
                }

                if (app.mRendererBackend == null) {
                    System.out.println("[dump] frame " + frame + " renderer not ready");
                    continue;
                }
                int width = app.mRendererBackend.renderOutputWidth();
                int height = app.mRendererBackend.renderOutputHeight();
                int[] pixels = new int[width * height];
                try {
                    app.mRendererBackend.readTexturePixels(0, width, height, pixels);
                } catch (Throwable e) {
                    System.out.println("[dump] frame " + frame + " readback failed: " + e);
                    continue;
                }
                writePng(dumpDir.resolve(String.format("frame-%03d.png", frame)), pixels, width, height);
                app.mRendererBackend.present();

                System.out.println("[dump] frame " + frame
                        + " appLogical=" + app.mWidth + "x" + app.mHeight
                        + " drawable=" + width + "x" + height
                        + " scene=" + sceneName(app.mGameScene)
                        + " widgets=" + app.mWidgetManager.mWidgets.size()
                        + " " + describe(pixels));
            }
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    private static void writePng(Path file, int[] pixels, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static String describe(int[] pixels) {
        Map<Integer, Integer> histogram = new LinkedHashMap<>();
        for (int pixel : pixels) {
            histogram.merge(pixel, 1, Integer::sum);
        }
        StringBuilder topColors = new StringBuilder();
        int shown = 0;
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            if (shown++ < 4) {
                topColors.append(String.format("#%08X=%d ", entry.getKey(), entry.getValue()));
            }
        }
        return "distinct=" + histogram.size() + " colours=" + topColors;
    }

    private static String sceneName(int theScene) {
        return switch (theScene) {
            case GameScenes.SCENE_LOADING -> "LOADING";
            case GameScenes.SCENE_MENU -> "MENU";
            case GameScenes.SCENE_LEVEL_INTRO -> "LEVEL_INTRO";
            case GameScenes.SCENE_PLAYING -> "PLAYING";
            case GameScenes.SCENE_ZOMBIES_WON -> "ZOMBIES_WON";
            case GameScenes.SCENE_AWARD -> "AWARD";
            case GameScenes.SCENE_CREDIT -> "CREDIT";
            case GameScenes.SCENE_CHALLENGE -> "CHALLENGE";
            default -> "?" + theScene;
        };
    }
}
