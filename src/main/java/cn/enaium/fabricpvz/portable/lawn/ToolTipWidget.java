/*
 * Ported from src/Lawn/ToolTipWidget.h:24-63 and src/Lawn/ToolTipWidget.cpp:31-249.
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/ToolTipWidget.h:24-63 (implementation
 * src/Lawn/ToolTipWidget.cpp:31-249).
 *
 * <p>A context tooltip (upstream a plain class, not a {@code Widget}): a yellow box with a title, an
 * optional blinking warning line and a word-wrapped body, drawn by the widget that owns it. The
 * label/title/warning text run through {@code PvzpStringTranslate} and re-measure the box.
 */
public class ToolTipWidget {

    // ToolTipWidget.h:28-41
    public String mTitle = "";
    public String mLabel = "";
    public String mWarningText = "";
    public int mX;
    public int mY;
    public int mWidth;
    public int mHeight;
    public boolean mVisible;
    public boolean mCenter;
    public int mMinLeft;
    public int mMaxBottom;
    public int mGetsLinesWidth;
    public int mWarningFlashCounter;
    public int mMaxLinesWidth; // max line width override (0 = auto)

    // ToolTipWidget::ToolTipWidget (ToolTipWidget.cpp:31)
    public ToolTipWidget() {
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
        mVisible = true;
        mCenter = false;
        mMinLeft = 0;
        mMaxBottom = GameConstants.BOARD_HEIGHT;
        mGetsLinesWidth = 0;
        mWarningFlashCounter = 0;
        mMaxLinesWidth = 0;
    }

    // ToolTipWidget::GetLines (ToolTipWidget.cpp:46)
    public void getLines(List<String> theLines) {
        Font aPico129 = Resources.getFontById(Resources.FONT_PICO129_ID);
        byte[] aLabelBytes = mLabel.getBytes(StandardCharsets.UTF_8);

        int aLineWidth = 0;
        int aLineStart = 0;
        int aCurPos = 0;
        int aPrevChar = 0;

        int aBreakDrawLen = -1;
        int aBreakResumePos = 0;

        while (aCurPos < aLabelBytes.length) {
            int aCharStart = aCurPos;
            int[] aOffset = {aCurPos};
            int aChar = Common.utf8DecodeNext(aLabelBytes, aOffset);
            if (aChar < 0) {
                aCurPos = aCharStart + 1;
                continue;
            }
            aCurPos = aOffset[0];
            if (aChar == '\r') { // skip CR for CRLF/LF compatibility
                continue;
            }
            int aCharEnd = aCurPos;

            if (aChar == '\n') {
                theLines.add(new String(aLabelBytes, aLineStart, aCharStart - aLineStart,
                        StandardCharsets.UTF_8));
                aLineWidth = 0;
                aLineStart = aCharEnd;
                aBreakDrawLen = -1;
                aPrevChar = 0;
                continue;
            }

            aLineWidth += aPico129.charWidth(aChar);

            if (aChar == ' ') {
                aBreakDrawLen = aCharStart - aLineStart;
                aBreakResumePos = aCharEnd;
                if (aLineWidth >= mGetsLinesWidth) {
                    theLines.add(new String(aLabelBytes, aLineStart, aBreakDrawLen,
                            StandardCharsets.UTF_8));
                    aCurPos = aBreakResumePos;
                    while (aCurPos < aLabelBytes.length && aLabelBytes[aCurPos] == ' ') {
                        aCurPos++;
                    }
                    aLineStart = aCurPos;
                    aLineWidth = 0;
                    aBreakDrawLen = -1;
                    aPrevChar = 0;
                    continue;
                }
            } else if (Common.isAutoBreakChar(aChar)
                    && !Common.isClosingPunctuation(aChar)
                    && aCharStart > aLineStart
                    && !Common.isOpeningPunctuation(aPrevChar)) {
                aBreakDrawLen = aCharStart - aLineStart;
                aBreakResumePos = aCharStart;
                if (aLineWidth >= mGetsLinesWidth) {
                    theLines.add(new String(aLabelBytes, aLineStart, aBreakDrawLen,
                            StandardCharsets.UTF_8));
                    aCurPos = aBreakResumePos;
                    aLineStart = aCurPos;
                    aLineWidth = 0;
                    aBreakDrawLen = -1;
                    aPrevChar = 0;
                    continue;
                }
            }
            aPrevChar = aChar;
        }

        if (aLineStart < aLabelBytes.length) {
            theLines.add(new String(aLabelBytes, aLineStart, aLabelBytes.length - aLineStart,
                    StandardCharsets.UTF_8));
        }
    }

    // ToolTipWidget::CalculateSize (ToolTipWidget.cpp:126)
    public void calculateSize() {
        Font aTinyBold = Resources.getFontById(Resources.FONT_TINYBOLD_ID);
        Font aPico129 = Resources.getFontById(Resources.FONT_PICO129_ID);

        java.util.ArrayList<String> aLines = new java.util.ArrayList<>();

        int aTitleWidth = aTinyBold.stringWidth(mTitle);
        int aWarningWidth = aPico129.stringWidth(mWarningText);
        int aMaxWidth = Math.max(aTitleWidth, aWarningWidth);

        mGetsLinesWidth = Math.max(aMaxWidth - 30, 100);
        if (mMaxLinesWidth > 0) {
            mGetsLinesWidth = Math.min(mGetsLinesWidth, mMaxLinesWidth);
        }
        getLines(aLines);

        for (String aLine : aLines) {
            aMaxWidth = Math.max(aMaxWidth, aPico129.stringWidth(aLine));
        }

        int aHeight = 6;
        if (!mTitle.isEmpty()) {
            aHeight = aTinyBold.getAscent() + 8;
        }
        if (!mWarningText.isEmpty()) {
            aHeight += aTinyBold.getAscent() + 2;
        }
        aHeight += aLines.size() * aPico129.getAscent();

        mWidth = aMaxWidth + 10;
        mHeight = aHeight + aLines.size() * 2 - 2;
    }

    // ToolTipWidget::SetLabel (ToolTipWidget.cpp:160)
    public void setLabel(String theLabel) {
        mLabel = PvzpStringFile.translate(theLabel);
        calculateSize();
    }

    // ToolTipWidget::SetTitle (ToolTipWidget.cpp:166)
    public void setTitle(String theTitle) {
        mTitle = PvzpStringFile.translate(theTitle);
        calculateSize();
    }

    // ToolTipWidget::SetWarningText (ToolTipWidget.cpp:172)
    public void setWarningText(String theWarningText) {
        mWarningText = PvzpStringFile.translate(theWarningText);
        calculateSize();
    }

    // ToolTipWidget::FlashWarning (ToolTipWidget.h:58)
    public void flashWarning() {
        mWarningFlashCounter = 70;
    }

    // ToolTipWidget::Update (ToolTipWidget.h:59)
    public void update() {
        if (mWarningFlashCounter > 0) {
            mWarningFlashCounter--;
        }
    }

    // ToolTipWidget::SetPosition (ToolTipWidget.h:60)
    public void setPosition(int theX, int theY) {
        mX = theX;
        mY = theY;
    }

    // ToolTipWidget::Draw (ToolTipWidget.cpp:178)
    public void draw(Graphics g) {
        if (!mVisible) {
            return;
        }

        Font aTinyBold = Resources.getFontById(Resources.FONT_TINYBOLD_ID);
        Font aPico129 = Resources.getFontById(Resources.FONT_PICO129_ID);

        int aPosX = mX;
        if (mCenter) {
            aPosX -= mWidth / 2;
        }
        if (mMinLeft - (int) g.mTransX > aPosX) { // aPosX + g->mTransX < mMinLeft
            aPosX = mMinLeft - (int) g.mTransX;
        } else if (aPosX + mWidth + (int) g.mTransX > GameConstants.BOARD_WIDTH) {
            aPosX = GameConstants.BOARD_WIDTH - (int) g.mTransX - mWidth;
        }

        int aPosY = mY;
        if (-(int) g.mTransY > aPosY) { // aPosY + g->mTransY > 0
            aPosY = -(int) g.mTransY;
        } else if (mMaxBottom < mY + mHeight + (int) g.mTransY) {
            aPosY = mMaxBottom - (int) g.mTransY - mHeight;
        }

        g.setColor(new Color(255, 255, 200, 255));
        g.fillRect(aPosX, aPosY, mWidth, mHeight);
        g.setColor(Color.Black);
        g.drawRect(aPosX, aPosY, mWidth - 1, mHeight - 1);
        aPosY++;

        if (!mTitle.isEmpty()) {
            g.setFont(aTinyBold);
            g.drawString(mTitle, aPosX + (mWidth - aTinyBold.stringWidth(mTitle)) / 2,
                    aPosY + aTinyBold.getAscent());
            aPosY += aTinyBold.getAscent() + 2;
        }

        if (!mWarningText.isEmpty()) {
            g.setFont(aPico129);
            int x = aPosX + (mWidth - aPico129.stringWidth(mWarningText)) / 2;
            int y = aPosY + aPico129.getAscent();

            Color aWarningColor = new Color(255, 0, 0);
            if (mWarningFlashCounter > 0 && mWarningFlashCounter % 20 < 10) {
                aWarningColor = new Color(0, 0, 0);
            }

            g.setColor(aWarningColor);
            g.drawString(mWarningText, x, y);
            g.setColor(Color.Black);

            aPosY += aPico129.getAscent() + 2;
        }

        java.util.ArrayList<String> aLines = new java.util.ArrayList<>();
        getLines(aLines);

        g.setFont(aPico129);
        for (String aLine : aLines) {
            g.drawString(aLine, aPosX + (mWidth - aPico129.stringWidth(aLine)) / 2,
                    aPosY + aPico129.getAscent());
            aPosY += aPico129.getAscent() + 2;
        }
    }
}
