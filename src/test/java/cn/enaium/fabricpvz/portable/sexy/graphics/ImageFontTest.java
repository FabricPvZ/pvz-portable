package cn.enaium.fabricpvz.portable.sexy.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * Tests for the bitmap font subsystem ({@link ImageFont} / {@link FontData}), ported from
 * src/SexyAppFramework/graphics/ImageFont.cpp.
 *
 * <p>Covers the legacy key/value {@code .fnt} parser ({@code FontData::LoadLegacy}), the PopCap
 * descriptor command interpreter ({@code FontData::HandleCommand}) and metrics / glyph blits. A
 * final assumption-gated test parses the real {@code data/ContinuumBold14.txt} out of {@code main.pak}
 * when a local copy of the game is available.
 */
class ImageFontTest {

    /** Minimal recording image used both as the glyph sheet and as the draw destination. */
    static class RecordingImage extends Image {

        static class Blt {
            int x;
            int y;
            Rect src;
        }

        final List<Blt> blts = new ArrayList<>();

        RecordingImage(int w, int h) {
            mWidth = w;
            mHeight = h;
            mNumCols = 1;
            mNumRows = 1;
        }

        @Override
        public void blt(Image theImage, int theX, int theY, Rect theSrcRect, cn.enaium.fabricpvz.portable.sexy.Color theColor, int theDrawMode, boolean linearFilter) {
            Blt b = new Blt();
            b.x = theX;
            b.y = theY;
            b.src = theSrcRect;
            blts.add(b);
        }
    }

    private static void assertRect(Rect expected, Rect actual) {
        assertEquals(expected.mX, actual.mX, "x");
        assertEquals(expected.mY, actual.mY, "y");
        assertEquals(expected.mWidth, actual.mWidth, "w");
        assertEquals(expected.mHeight, actual.mHeight, "h");
    }

    @Test
    void legacyFntParsesMetricsAndWidths(@TempDir Path dir) throws IOException {
        // Legacy format: "<spaceWidth> <ascent>" then "<char> <width>" pairs; the glyph sheet is a
        // single horizontal strip whose per-character rectangles advance by the width.
        Path fnt = dir.resolve("test.fnt");
        Files.writeString(fnt, "6 8\nA 5\nB 6\n");

        RecordingImage strip = new RecordingImage(11, 10);
        ImageFont font = new ImageFont(strip, fnt.toString());

        assertTrue(font.isInitialized());
        assertEquals(5, font.charWidth('A'));
        assertEquals(6, font.charWidth('B'));
        assertEquals(6, font.charWidth(' '));
        assertEquals(11, font.stringWidth("AB"));
        assertEquals(8, font.getAscent());
        assertEquals(10, font.getHeight());
        assertEquals(2, font.getDescent());
        assertEquals(10, font.getLineSpacing());
    }

    @Test
    void drawStringBlitsTheRightGlyphRects(@TempDir Path dir) throws IOException {
        Path fnt = dir.resolve("test.fnt");
        Files.writeString(fnt, "6 8\nA 5\nB 6\n");

        RecordingImage strip = new RecordingImage(11, 10);
        ImageFont font = new ImageFont(strip, fnt.toString());

        RecordingImage dest = new RecordingImage(100, 50);
        Graphics g = new Graphics(dest);
        g.setFont(font);
        g.drawString("AB", 0, 20);

        assertEquals(2, dest.blts.size(), "one blit per glyph");
        // 'A' image rect is (0,0,5,10); dest x = 0, y = 20 - (ascent(8)) = 12
        assertRect(new Rect(0, 0, 5, 10), dest.blts.get(0).src);
        assertEquals(0, dest.blts.get(0).x);
        assertEquals(12, dest.blts.get(0).y);
        // 'B' image rect is (5,0,6,10); dest x advances by 'A' width (5)
        assertRect(new Rect(5, 0, 6, 10), dest.blts.get(1).src);
        assertEquals(5, dest.blts.get(1).x);
        assertEquals(12, dest.blts.get(1).y);
    }

    @Test
    void missingGlyphDrawsNothingAndAdvancesByZero(@TempDir Path dir) throws IOException {
        Path fnt = dir.resolve("test.fnt");
        Files.writeString(fnt, "6 8\nA 5\n");

        RecordingImage strip = new RecordingImage(5, 10);
        ImageFont font = new ImageFont(strip, fnt.toString());

        RecordingImage dest = new RecordingImage(100, 50);
        Graphics g = new Graphics(dest);
        g.setFont(font);

        int[] width = new int[1];
        font.drawStringEx(g, 0, 20, "Z", cn.enaium.fabricpvz.portable.sexy.Color.White, null, width);

        assertTrue(dest.blts.isEmpty(), "missing glyph has an empty source rect");
        assertEquals(0, width[0]);
        assertEquals(0, font.stringWidth("Z"));
    }

    @Test
    void missingDescriptorFileFailsCleanly(@TempDir Path dir) {
        RecordingImage strip = new RecordingImage(10, 10);
        ImageFont font = new ImageFont(strip, dir.resolve("does-not-exist.fnt").toString());
        assertFalse(font.isInitialized());
        assertEquals(0, font.stringWidth("AB"));
    }

    @Test
    void descriptorInterpreterBuildsLayerDataWithoutAnApp() {
        FontData fd = new FontData();

        assertTrue(fd.parseDescriptorLine("Define CharList ('A','B')"));
        assertTrue(fd.parseDescriptorLine("CreateLayer Main"));
        FontLayer layer = fd.mFontLayerMap.get("MAIN");
        layer.mImage = new SharedImageRef(new RecordingImage(20, 10));

        assertTrue(fd.parseDescriptorLine("LayerSetCharWidths Main CharList (5,7)"));
        assertTrue(fd.parseDescriptorLine("LayerSetImageMap Main CharList ((0,0,5,10),(5,0,7,10))"));
        assertTrue(fd.parseDescriptorLine("LayerSetAscent Main 9"));
        assertTrue(fd.parseDescriptorLine("SetDefaultPointSize 12"));

        assertEquals(12, fd.mDefaultPointSize);
        assertEquals(5, layer.getCharData('A').mWidth);
        assertEquals(7, layer.getCharData('B').mWidth);
        assertRect(new Rect(5, 0, 7, 10), layer.getCharData('B').mImageRect);
        assertEquals(9, layer.mAscent);
        assertEquals(10, layer.mDefaultHeight);

        // Unknown commands are rejected (upstream FontData::HandleCommand default branch).
        assertFalse(fd.parseDescriptorLine("BogusCommand Main"));
    }

    @Test
    void descriptorInterpreterRejectsOutOfBoundsImageRects() {
        FontData fd = new FontData();
        assertTrue(fd.parseDescriptorLine("Define CharList ('A')"));
        assertTrue(fd.parseDescriptorLine("CreateLayer Main"));
        fd.mFontLayerMap.get("MAIN").mImage = new SharedImageRef(new RecordingImage(4, 4));
        assertFalse(fd.parseDescriptorLine("LayerSetImageMap Main CharList ((0,0,5,10))"));
    }

    /** Splits a descriptor file into ';'-separated commands, ignoring separators inside quotes. */
    private static List<String> splitDescriptor(String content) {
        List<String> commands = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && (inSingleQuotes || inDoubleQuotes)) {
                escaped = true;
                current.append(c);
                continue;
            }
            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            }
            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            }
            if (c == ';' && !inSingleQuotes && !inDoubleQuotes) {
                commands.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            commands.add(current.toString());
        }
        return commands;
    }

    @Test
    void parsesRealContinuumBold14DescriptorFromPak() throws IOException {
        Optional<Path> root = TestAssets.resourceRoot();
        Assumptions.assumeTrue(root.isPresent(), "no real PvZ assets available");
        Optional<Path> pakPath = TestAssets.resolve("main.pak");
        Assumptions.assumeTrue(pakPath.isPresent(), "no main.pak in the resource root");

        PakInterface pakInterface = new PakInterface();
        pakInterface.setResourceFolder(root.get().toString());
        assertTrue(pakInterface.addPakFile(pakPath.get()), "real main.pak must mount");

        byte[] fontBytes = pakInterface.requireResource("data/ContinuumBold14.txt");
        String content = new String(fontBytes, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1); // strip UTF-8 BOM
        }
        assertTrue(content.contains("LayerSetImage"), "descriptor should name its glyph image");

        // The companion glyph sheet must exist in the pak and be a decodable GIF. Retail main.pak
        // ships no ContinuumBold14.png/gif: `LayerSetImage Main 'ContinuumBold14'` resolves through
        // ImageLib::GetImage's alpha-companion probe, alphaPath1 = "_" + theFilename
        // (ImageLib.cpp:1277-1286), which is the _ContinuumBold14.gif member; GetImage then repaints
        // it with gAlphaComposeColor because no base image was found (ImageLib.cpp:1296-1307,
        // ApplyAlphaAsImage), so the sheet's blue channel is the glyph mask.
        assertTrue(pakInterface.exists("data/_ContinuumBold14.gif"),
                "glyph sheet should ship alongside the descriptor as data/_ContinuumBold14.gif");
        byte[] sheetBytes = pakInterface.requireResource("data/_ContinuumBold14.gif");
        assertEquals("GIF89a", new String(sheetBytes, 0, 6, StandardCharsets.ISO_8859_1),
                "the shipped glyph sheet is a GIF");
        assertTrue(((sheetBytes[6] & 0xFF) | ((sheetBytes[7] & 0xFF) << 8)) > 0, "GIF width");
        assertTrue(((sheetBytes[8] & 0xFF) | ((sheetBytes[9] & 0xFF) << 8)) > 0, "GIF height");

        FontData fd = new FontData();
        Set<String> needsApp = Set.of("LayerSetImage", "LayerSetImageMap");
        for (String command : splitDescriptor(content)) {
            String trimmed = command.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String firstToken = trimmed.split("[\\s,()]+", 2)[0];
            if (needsApp.contains(firstToken)) {
                continue; // these require a SexyAppBase to resolve the glyph image
            }
            fd.parseDescriptorLine(trimmed);
        }

        assertEquals(14, fd.mDefaultPointSize, "ContinuumBold14 declares point size 14");
        FontLayer layer = fd.mFontLayerMap.get("MAIN");
        assertTrue(layer != null, "descriptor declares a Main layer");
        assertTrue(layer.getCharData('A').mWidth > 0, "glyph 'A' must have a measured width");
    }
}
