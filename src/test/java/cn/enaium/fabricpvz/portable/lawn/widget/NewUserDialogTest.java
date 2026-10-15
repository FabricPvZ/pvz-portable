/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.NewUserDialog - the "please enter your name" profile dialog.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless checks for {@link NewUserDialog}, in particular that the name field actually takes typed
 * text once the dialog is on screen (the field is only reachable through the widget manager's text
 * routing, which is also what the platform feeds from SDL's text-input events).
 */
class NewUserDialogTest {

    private static LawnApp newApp() {
        LawnApp app = new LawnApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    /**
     * A font with a non-zero advance for every character. {@code EditWidget.insertTextAtCursor}
     * (EditWidget.cpp:461-475) drops code points whose {@code charWidth} is 0, and the fixture's stub
     * fonts deliberately measure as zero width for layout maths, so a typing test needs this.
     */
    private static final class WideningFont extends Font {
        @Override
        public int stringWidth(String theString) {
            return theString == null ? 0 : theString.length() * 8;
        }

        @Override
        public int charWidth(int theChar) {
            return 8;
        }

        @Override
        public int charWidthKern(int theChar, int thePrevChar) {
            return 8;
        }

        @Override
        public void drawString(Graphics g, int theX, int theY, String theString, Color theColor, Rect theClipRect) {
        }

        @Override
        public Font duplicate() {
            return new WideningFont();
        }
    }

    /** A dialog whose name field can actually accept characters (see {@link WideningFont}). */
    private static NewUserDialog newTypableDialog(LawnApp app) {
        NewUserDialog aDialog = new NewUserDialog(app, false);
        aDialog.mNameEditWidget.setFont(new WideningFont(), null);
        app.addDialog(aDialog);
        return aDialog;
    }

    @Test
    void constructorBuildsTheNameFieldWithTheUpstreamLimit() {
        LawnApp app = newApp();
        NewUserDialog aDialog = new NewUserDialog(app, false);

        assertEquals(Dialogs.DIALOG_CREATEUSER, aDialog.mId);
        assertNotNull(aDialog.mNameEditWidget, "the name field must exist");
        assertEquals(12, aDialog.mNameEditWidget.mMaxChars, "upstream caps names at 12 characters");
    }

    @Test
    void addedToManagerFocusesTheNameField() {
        LawnApp app = newApp();
        NewUserDialog aDialog = new NewUserDialog(app, false);

        app.addDialog(aDialog);

        assertSame(aDialog.mNameEditWidget, app.mWidgetManager.mFocusWidget,
                "typing must land in the name field as soon as the dialog is on screen");
    }

    @Test
    void typedTextLandsInTheNameField() {
        LawnApp app = newApp();
        NewUserDialog aDialog = newTypableDialog(app);

        // This is the path SDL text-input events take: platform -> SexyAppBase -> WidgetManager.
        app.mWidgetManager.keyText("Alice");

        assertEquals("Alice", aDialog.mNameEditWidget.mString);
    }

    @Test
    void typingStopsAtTheCharacterLimit() {
        LawnApp app = newApp();
        NewUserDialog aDialog = newTypableDialog(app);

        app.mWidgetManager.keyText("0123456789ABCDEF");

        assertEquals(12, aDialog.mNameEditWidget.mString.length(),
                "the field must not grow past its limit: " + aDialog.mNameEditWidget.mString);
        assertTrue("0123456789ABCDEF".startsWith(aDialog.mNameEditWidget.mString));
    }

    @Test
    void renameDialogUsesTheRenameId() {
        LawnApp app = newApp();
        NewUserDialog aDialog = new NewUserDialog(app, true);

        assertEquals(Dialogs.DIALOG_RENAMEUSER, aDialog.mId);
    }
}
