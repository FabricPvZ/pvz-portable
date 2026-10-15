/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.MessageWidget.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless tests for {@link MessageWidget}: the style → duration table, the label limits, the
 * countdown/expiry and the queued-message hand-off. The reanimated (HUGE_WAVE) style needs retail
 * art, so these tests exercise the plain-text styles which run asset-free.
 */
class MessageWidgetTest {

    /**
     * A fresh app with the services {@code LawnApp}'s init and the loading thread provide
     * ({@code mEffectSystem}, the foley/reanim definition tables, stub images and fonts), without
     * which {@link Board} and {@link MessageWidget} dereference null.
     */
    private static LawnApp newApp() {
        LawnApp app = new LawnApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    private static MessageWidget newWidgetWithBoard() {
        LawnApp app = newApp();
        Board board = new Board(app);
        board.mPaused = false;
        app.mBoard = board;
        return new MessageWidget(app);
    }

    @Test
    void durationTableMatchesUpstream() {
        int[][] aCases = {
                {MessageStyle.MESSAGE_STYLE_HINT_LONG, 1500},
                {MessageStyle.MESSAGE_STYLE_BIG_MIDDLE, 1500},
                {MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG, 1500},
                {MessageStyle.MESSAGE_STYLE_HINT_TALL_LONG, 1500},
                {MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE, 500},
                {MessageStyle.MESSAGE_STYLE_HINT_FAST, 500},
                {MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST, 500},
                {MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST, 500},
                {MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1, 500},
                {MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2, 500},
                {MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER, 500},
                {MessageStyle.MESSAGE_STYLE_HINT_STAY, 10000},
                {MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY, 10000},
                {MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER_STAY, 10000},
                {MessageStyle.MESSAGE_STYLE_HOUSE_NAME, 250},
                {MessageStyle.MESSAGE_STYLE_SLOT_MACHINE, 750},
                {MessageStyle.MESSAGE_STYLE_ACHIEVEMENT, 250},
        };

        MessageWidget aWidget = new MessageWidget(newApp());
        for (int[] aCase : aCases) {
            aWidget.setLabel("A message", aCase[0]);
            assertEquals(aCase[1], aWidget.mDuration, "duration of style " + aCase[0]);
            assertEquals(aCase[1], aWidget.mDisplayTime, "display time of style " + aCase[0]);
            assertTrue(aWidget.mDuration != 0, "the message is being displayed");
        }
    }

    @Test
    void countdownExpiresAndClearsTheStyle() {
        MessageWidget aWidget = newWidgetWithBoard();
        aWidget.setLabel("Hint", MessageStyle.MESSAGE_STYLE_HINT_FAST); // 500 ticks

        for (int i = 0; i < 499; i++) {
            aWidget.update();
        }
        assertEquals(1, aWidget.mDuration, "still counting down");

        aWidget.update();
        assertEquals(0, aWidget.mDuration, "reached zero");
        assertEquals(MessageStyle.MESSAGE_STYLE_OFF, aWidget.mMessageStyle);
    }

    @Test
    void stayStylesDoNotCountDown() {
        MessageWidget aWidget = newWidgetWithBoard();
        aWidget.setLabel("Stay", MessageStyle.MESSAGE_STYLE_HINT_STAY); // 10000: no countdown

        for (int i = 0; i < 6000; i++) {
            aWidget.update();
        }
        assertEquals(10000, aWidget.mDuration);
    }

    @Test
    void queuedMessageIsPromotedWhenTheCurrentOneExpires() {
        MessageWidget aWidget = newWidgetWithBoard();
        aWidget.setLabel("First", MessageStyle.MESSAGE_STYLE_HINT_FAST); // 500 ticks
        aWidget.mLabelNext = "Second";
        aWidget.mMessageStyleNext = MessageStyle.MESSAGE_STYLE_HINT_LONG; // 1500 ticks

        for (int i = 0; i < 500; i++) {
            aWidget.update();
        }

        assertEquals("Second", aWidget.mLabel, "the queued label is promoted");
        assertEquals(MessageStyle.MESSAGE_STYLE_HINT_LONG, aWidget.mMessageStyle);
        assertEquals(1500, aWidget.mDuration);
        assertEquals(1500, aWidget.mDisplayTime);
        assertEquals(MessageStyle.MESSAGE_STYLE_OFF, aWidget.mMessageStyleNext,
                "the queued style is consumed");
    }

    @Test
    void setLabelQueuesWhileAReanimMessageIsRunning() {
        MessageWidget aWidget = new MessageWidget(newApp());
        // Simulate the reanim path without art: type set, currently displaying.
        aWidget.mReanimType = ReanimationType.REANIM_TEXT_FADE_ON;
        aWidget.mSlideOffTime = 50;
        aWidget.mDuration = 100;
        aWidget.mMessageStyle = MessageStyle.MESSAGE_STYLE_HUGE_WAVE;

        aWidget.setLabel("Queued", MessageStyle.MESSAGE_STYLE_HINT_FAST);

        assertEquals("Queued", aWidget.mLabelNext);
        assertEquals(MessageStyle.MESSAGE_STYLE_HINT_FAST, aWidget.mMessageStyleNext);
        assertEquals(100, aWidget.mDuration,
                "ClearLabel caps the running reanim duration at 100 + slideOffTime + 1");
    }

    @Test
    void labelIsTruncatedToBufferAndLineLimits() {
        MessageWidget aWidget = new MessageWidget(newApp());

        aWidget.setLabel("a".repeat(200), MessageStyle.MESSAGE_STYLE_HINT_FAST);
        assertEquals(MessageWidget.MAX_MESSAGE_LENGTH - 1, aWidget.mLabel.length(),
                "at most 127 bytes are kept");

        aWidget.setLabel("L1\nL2\nL3\nL4\nL5\nL6", MessageStyle.MESSAGE_STYLE_HINT_FAST);
        assertEquals("L1\nL2\nL3\nL4\nL5", aWidget.mLabel, "at most 5 lines are kept");
    }

    @Test
    void clearLabelStopsPlainTextImmediately() {
        MessageWidget aWidget = new MessageWidget(newApp());
        aWidget.setLabel("Plain", MessageStyle.MESSAGE_STYLE_HINT_FAST);
        assertFalse(aWidget.mDuration == 0);

        aWidget.clearLabel();
        assertEquals(0, aWidget.mDuration);
    }
}
