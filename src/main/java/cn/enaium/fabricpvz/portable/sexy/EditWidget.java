package cn.enaium.fabricpvz.portable.sexy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/SexyAppFramework/widget/EditWidget.h:46 (implementation
 * src/SexyAppFramework/widget/EditWidget.cpp:43).
 *
 * <p>Single-line editable text field with caret blinking, selection/hiliting, horizontal scrolling,
 * clipboard copy/paste and single-step undo. Upstream stores {@code std::string} values as UTF-8
 * byte sequences and tracks cursor/hilite positions as byte offsets into that sequence; this port
 * keeps the public {@link #mString} as a Java {@code String} and performs the same offset arithmetic
 * against its UTF-8 encoding via the {@link Common#utf8NextBoundary} family, so every upstream offset
 * convention (and {@code substr}) is preserved exactly.
 */
public class EditWidget extends Widget {

    /** Ported from src/SexyAppFramework/widget/EditWidget.h:37. */
    public static class EditWidgetColorScheme {
        public Color mBkg;
        public Color mOutline;
        public Color mText;
        public Color mHilite;
        public Color mHiliteText;

        public EditWidgetColorScheme() {
        }

        public EditWidgetColorScheme(Color theBkg, Color theOutline, Color theText, Color theHilite,
                Color theHiliteText) {
            mBkg = theBkg;
            mOutline = theOutline;
            mText = theText;
            mHilite = theHilite;
            mHiliteText = theHiliteText;
        }
    }

    /** Ported from src/SexyAppFramework/widget/EditWidget.h:56. */
    public static class WidthCheck {
        public Font mFont;
        public int mWidth;
    }

    // EditWidget.cpp:31
    private static final EditWidgetColorScheme gEditWidgetColors = new EditWidgetColorScheme(
            new Color(255, 255, 255),
            new Color(0, 0, 0),
            new Color(0, 0, 0),
            new Color(0, 0, 0),
            new Color(255, 255, 255));

    public int mId;
    public String mString;
    public Font mFont;
    public EditWidgetColorScheme mColors;

    public List<WidthCheck> mWidthCheckList = new ArrayList<>();

    public EditListener mEditListener;
    public boolean mShowingCursor;
    public boolean mDrawSelOverride; // set this to true to draw selected text even when not in focus
    public boolean mHadDoubleClick; // Used to fix a bug with double clicking to hilite a word after
                                    // the widget manager started calling mouse drag before
                                    // mouse down/up events
    public boolean mHadFocusBeforePress; // mHasFocus snapshot taken in WantsFocus (pre-SetFocus)
    public int mCursorPos;
    public int mHilitePos;
    public int mBlinkAcc;
    public int mBlinkDelay;
    public int mLeftPos;
    public int mMaxChars;
    public int mMaxPixels;

    public String mUndoString;
    public int mUndoCursor;
    public int mUndoHilitePos;
    public int mLastModifyIdx;

    // EditWidget::EditWidget (EditWidget.cpp:43)
    public EditWidget(int theId, EditListener theEditListener) {
        mId = theId;
        mEditListener = theEditListener;

        mString = "";
        mFont = null;
        mUndoString = "";
        mHadDoubleClick = false;
        mHadFocusBeforePress = false;
        mHilitePos = -1;
        mLastModifyIdx = -1; // cursor position right after the last modification
        mLeftPos = 0;
        mUndoCursor = 0;
        mUndoHilitePos = 0;
        mBlinkAcc = 0;
        mCursorPos = 0;
        mShowingCursor = false;
        mDrawSelOverride = false;
        mMaxChars = -1;
        mMaxPixels = -1;
        mBlinkDelay = 40;

        setColors(gEditWidgetColors);
    }

    // EditWidget::~EditWidget (EditWidget.cpp:66)
    @Override
    public void dispose() {
        clearWidthCheckFonts();

        super.dispose();
    }

    // EditWidget::SetColors (EditWidget.cpp:72)
    public void setColors(EditWidgetColorScheme theColors) {
        mColors = theColors;
        markDirty();
    }

    // EditWidget::ClearWidthCheckFonts (EditWidget.cpp:78)
    public void clearWidthCheckFonts() {
        mWidthCheckList.clear();
    }

    // EditWidget::AddWidthCheckFont (EditWidget.cpp:83)
    public void addWidthCheckFont(Font theFont, int theMaxPixels) {
        WidthCheck aCheck = new WidthCheck();
        aCheck.mWidth = theMaxPixels;
        aCheck.mFont = theFont.duplicate();
        mWidthCheckList.add(aCheck);
    }

    // EditWidget::SetText (EditWidget.cpp:91)
    public void setText(String theText, boolean leftPosToZero) {
        mString = theText;
        mCursorPos = utf8(mString).length;
        mHilitePos = 0;
        if (leftPosToZero) {
            mLeftPos = 0;
        } else {
            focusCursor(true);
        }

        markDirty();
    }

    // EditWidget::WantsFocus (EditWidget.cpp:104)
    @Override
    public boolean wantsFocus() {
        mHadFocusBeforePress = mHasFocus; // WidgetManager calls WantsFocus right before SetFocus on
                                          // each press
        return true;
    }

    // EditWidget::Resize (EditWidget.cpp:110)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        focusCursor(false);
    }

    // EditWidget::SetFont (EditWidget.cpp:117)
    public void setFont(Font theFont, Font theWidthCheckFont) {
        mFont = theFont.duplicate();

        clearWidthCheckFonts();
        if (theWidthCheckFont != null) {
            addWidthCheckFont(theWidthCheckFont, -1);
        }
    }

    // EditWidget::Draw (EditWidget.cpp:126) // Already translated
    @Override
    public void draw(Graphics g) {
        Font aDefaultFont = mWidgetManager.mApp.mDefaultFont;
        if ((mFont == null) && (aDefaultFont != null)) {
            mFont = aDefaultFont.duplicate();
        }
        if (mFont == null) {
            return;
        }

        byte[] aString = utf8(mString);

        g.setColor(mColors.mBkg);
        g.fillRect(0, 0, mWidth, mHeight);

        for (int i = 0; i < 2; i++) {
            Graphics aClipG = g.create();
            aClipG.setFont(mFont);

            if (i == 1) {
                int aCursorX = getCaretXOffset();
                int aHiliteX = aCursorX + 2;
                if ((mHilitePos != -1) && (mCursorPos != mHilitePos)) {
                    aHiliteX = mFont.stringWidth(prefix(aString, mHilitePos))
                            - mFont.stringWidth(prefix(aString, mLeftPos));
                }

                if (!mShowingCursor) {
                    aCursorX += 2;
                }

                aCursorX = Math.min(Math.max(0, aCursorX), mWidth - 8);
                aHiliteX = Math.min(Math.max(0, aHiliteX), mWidth - 8);

                aClipG.clipRect(4 + Math.min(aCursorX, aHiliteX),
                        (mHeight - mFont.getHeight()) / 2,
                        Math.abs(aHiliteX - aCursorX), mFont.getHeight());
            } else {
                aClipG.clipRect(4, 0, mWidth - 8, mHeight);
            }

            boolean hasfocus = mHasFocus || mDrawSelOverride;
            if (i == 1 && hasfocus) {
                aClipG.setColor(mColors.mHilite);
                aClipG.fillRect(0, 0, mWidth, mHeight);
            }

            if (i == 0 || !hasfocus) {
                aClipG.setColor(mColors.mText);
            } else {
                aClipG.setColor(mColors.mHiliteText);
            }
            aClipG.drawString(fromByte(aString, mLeftPos), 4,
                    (mHeight - mFont.getHeight()) / 2 + mFont.getAscent());
        }

        g.setColor(mColors.mOutline);
        g.drawRect(0, 0, mWidth - 1, mHeight - 1);
    }

    // EditWidget::UpdateCaretPos (EditWidget.cpp:181)
    protected void updateCaretPos() {
        SexyAppBase anApp = mWidgetManager.mApp;

        Point aPoint = getAbsPos();

        if (aPoint.mX < 10) {
            aPoint.mX = 10;
        } else if (aPoint.mX > anApp.mWidth - 10) {
            aPoint.mX = anApp.mWidth - 10;
        }
        if (aPoint.mY < 10) {
            aPoint.mY = 10;
        } else if (aPoint.mY > anApp.mHeight - 10) {
            aPoint.mY = anApp.mHeight - 10;
        }
    }

    // EditWidget::GetCaretXOffset (EditWidget.cpp:193)
    protected int getCaretXOffset() {
        byte[] aString = utf8(mString);
        return mFont.stringWidth(prefix(aString, mCursorPos))
                - mFont.stringWidth(prefix(aString, mLeftPos));
    }

    // EditWidget::UpdateTextInputArea (EditWidget.cpp:198)
    protected void updateTextInputArea() {
        if (mFont == null || mWidgetManager == null || !mHasFocus) {
            // FocusCursor may fire while unattached or unfocused
            return;
        }

        int aCursorX = Math.min(Math.max(0, getCaretXOffset()), mWidth - 8);

        Point anAbsPos = getAbsPos();
        int aTextY = anAbsPos.mY + (mHeight - mFont.getHeight()) / 2; // match where the caret is drawn
        mWidgetManager.mApp.setTextInputRect(
                new Rect(anAbsPos.mX + 4 + aCursorX, aTextY, 1, mFont.getHeight()));
    }

    // EditWidget::GotFocus (EditWidget.cpp:210)
    @Override
    public void gotFocus() {
        super.gotFocus();

        mShowingCursor = true;
        mBlinkAcc = 0;
        markDirty();

        updateTextInputArea(); // set before StartTextInput so the platform anchors the IME upfront

        StringBuilder value = new StringBuilder();
        boolean wrote = mWidgetManager.mApp.startTextInput(value);
        if (wrote) {
            setText(value.toString(), true);
        }
    }

    // EditWidget::LostFocus (EditWidget.cpp:226)
    @Override
    public void lostFocus() {
        super.lostFocus();

        mWidgetManager.mApp.stopTextInput();
        mShowingCursor = false;
        markDirty();
    }

    // EditWidget::Update (EditWidget.cpp:235)
    @Override
    public void update() {
        super.update();

        if (mHasFocus) {
            if (mWidgetManager.mApp.mTabletPC) {
                updateCaretPos();
            }

            if (++mBlinkAcc > mBlinkDelay) {
                markDirty();
                mBlinkAcc = 0;
                mShowingCursor = !mShowingCursor;
            }
        }
    }

    // EditWidget::EnforceMaxChars (EditWidget.cpp:255)
    public void enforceMaxChars() {
        byte[] aString = utf8(mString);
        if ((mMaxChars != -1) && (Common.utf8CodePointCount(aString) > mMaxChars)) {
            mString = prefix(aString, Common.utf8ByteOffsetForCodePoint(aString, mMaxChars));
        }
    }

    // EditWidget::EnforceMaxPixels (EditWidget.cpp:261)
    public void enforceMaxPixels() {
        if (mMaxPixels <= 0 && mWidthCheckList.isEmpty()) {
            // no width checking in effect
            return;
        }

        if (mWidthCheckList.isEmpty()) {
            while (mFont.stringWidth(mString) > mMaxPixels) {
                byte[] aString = utf8(mString);
                mString = prefix(aString, Common.utf8PrevBoundary(aString, aString.length));
            }

            return;
        }

        for (WidthCheck aCheck : mWidthCheckList) {
            int aWidth = aCheck.mWidth;
            if (aWidth <= 0) {
                aWidth = mMaxPixels;
                if (aWidth <= 0) {
                    continue;
                }
            }

            while (aCheck.mFont.stringWidth(mString) > aWidth) {
                byte[] aString = utf8(mString);
                mString = prefix(aString, Common.utf8PrevBoundary(aString, aString.length));
            }
        }
    }

    // EditWidget::IsPartOfWord (EditWidget.cpp:289)
    public boolean isPartOfWord(int theChar) {
        return (((theChar >= 'A') && (theChar <= 'Z'))
                || ((theChar >= 'a') && (theChar <= 'z'))
                || ((theChar >= '0') && (theChar <= '9'))
                || (theChar >= 0x80)
                || (theChar == '_'));
    }

    // EditWidget::ProcessKey (EditWidget.cpp:298)
    protected void processKey(int theKey, char theChar) {
        boolean shiftDown = mWidgetManager.mKeyDown[KeyCode.KEYCODE_SHIFT];
        boolean controlDown = mWidgetManager.mKeyDown[KeyCode.KEYCODE_CONTROL];

        if ((theKey == KeyCode.KEYCODE_SHIFT) || (theKey == KeyCode.KEYCODE_CONTROL)) {
            return;
        }

        boolean bigChange = false;
        boolean removeHilite = !shiftDown;

        if (shiftDown && (mHilitePos == -1)) {
            mHilitePos = mCursorPos;
        }

        String anOldString = mString;
        int anOldCursorPos = mCursorPos;
        int anOldHilitePos = mHilitePos;
        if ((theChar == 3) || (theChar == 24)) {
            // Copy	selection

            if ((mHilitePos != -1) && (mHilitePos != mCursorPos)) {
                int aSelStart = Math.min(mCursorPos, mHilitePos);
                int aSelLen = Math.max(mCursorPos, mHilitePos) - aSelStart;
                mWidgetManager.mApp.copyToClipboard(slice(utf8(mString), aSelStart, aSelLen));

                if (theChar == 3) {
                    removeHilite = false;
                } else {
                    byte[] aString = utf8(mString);
                    mString = prefix(aString, Math.min(mCursorPos, mHilitePos))
                            + fromByte(aString, Math.max(mCursorPos, mHilitePos));
                    mCursorPos = Math.min(mCursorPos, mHilitePos);
                    mHilitePos = -1;
                    bigChange = true;
                }
            }
        } else if (theChar == 22) {
            // Paste selection

            String aBaseString = mWidgetManager.mApp.getClipboard();

            if (aBaseString.length() > 0) {
                int aLineEnd = findFirstOf(aBaseString, "\r\n");
                if (aLineEnd != -1) {
                    aBaseString = aBaseString.substring(0, aLineEnd);
                }

                insertTextAtCursor(aBaseString);
                bigChange = (!mString.equals(anOldString))
                        || (mCursorPos != anOldCursorPos)
                        || (mHilitePos != anOldHilitePos);
            }
        } else if (theChar == 26) {
            // Undo

            mLastModifyIdx = -1;

            String aSwapString = mString;
            int aSwapCursorPos = mCursorPos;
            int aSwapHilitePos = mHilitePos;

            mString = mUndoString;
            mCursorPos = mUndoCursor;
            mHilitePos = mUndoHilitePos;

            mUndoString = aSwapString;
            mUndoCursor = aSwapCursorPos;
            mUndoHilitePos = aSwapHilitePos;

            removeHilite = false;
        } else if (theKey == KeyCode.KEYCODE_LEFT) {
            if (controlDown) {
                // Get to a word
                while (mCursorPos > 0) {
                    byte[] aString = utf8(mString);
                    int aPrev = Common.utf8PrevBoundary(aString, mCursorPos);
                    if (isPartOfWord(Common.utf8CodePointAt(aString, aPrev))) {
                        break;
                    }
                    mCursorPos = aPrev;
                }

                // Go beyond the word
                while (mCursorPos > 0) {
                    byte[] aString = utf8(mString);
                    int aPrev = Common.utf8PrevBoundary(aString, mCursorPos);
                    if (!isPartOfWord(Common.utf8CodePointAt(aString, aPrev))) {
                        break;
                    }
                    mCursorPos = aPrev;
                }
            } else if (shiftDown || (mHilitePos == -1)) {
                mCursorPos = Common.utf8PrevBoundary(utf8(mString), mCursorPos);
            } else {
                mCursorPos = Math.min(mCursorPos, mHilitePos);
            }
        } else if (theKey == KeyCode.KEYCODE_RIGHT) {
            byte[] aString = utf8(mString);
            if (controlDown) {
                // Get to whitespace
                while (mCursorPos < aString.length) {
                    if (!isPartOfWord(Common.utf8CodePointAt(aString, mCursorPos))) {
                        break;
                    }
                    mCursorPos = Common.utf8NextBoundary(aString, mCursorPos);
                }

                // Go beyond the whitespace
                while (mCursorPos < aString.length) {
                    if (isPartOfWord(Common.utf8CodePointAt(aString, mCursorPos))) {
                        break;
                    }
                    mCursorPos = Common.utf8NextBoundary(aString, mCursorPos);
                }
            } else if (shiftDown || (mHilitePos == -1)) {
                mCursorPos = Common.utf8NextBoundary(aString, mCursorPos);
            } else {
                mCursorPos = Math.max(mCursorPos, mHilitePos);
            }
        } else if (theKey == KeyCode.KEYCODE_BACK) {
            if (!mString.isEmpty()) {
                if ((mHilitePos != -1) && (mHilitePos != mCursorPos)) {
                    // Delete selection
                    byte[] aString = utf8(mString);
                    mString = prefix(aString, Math.min(mCursorPos, mHilitePos))
                            + fromByte(aString, Math.max(mCursorPos, mHilitePos));
                    mCursorPos = Math.min(mCursorPos, mHilitePos);
                    mHilitePos = -1;

                    bigChange = true;
                } else {
                    // Delete char behind cursor
                    if (mCursorPos > 0) {
                        byte[] aString = utf8(mString);
                        int aPrev = Common.utf8PrevBoundary(aString, mCursorPos);
                        if (mCursorPos != mLastModifyIdx) {
                            bigChange = true;
                        }
                        mString = prefix(aString, aPrev) + fromByte(aString, mCursorPos);
                        mCursorPos = aPrev;
                        mLastModifyIdx = mCursorPos;
                    }
                    mHilitePos = -1;
                }
            }
        } else if (theKey == KeyCode.KEYCODE_DELETE) {
            if (!mString.isEmpty()) {
                if ((mHilitePos != -1) && (mHilitePos != mCursorPos)) {
                    // Delete selection
                    byte[] aString = utf8(mString);
                    mString = prefix(aString, Math.min(mCursorPos, mHilitePos))
                            + fromByte(aString, Math.max(mCursorPos, mHilitePos));
                    mCursorPos = Math.min(mCursorPos, mHilitePos);
                    mHilitePos = -1;

                    bigChange = true;
                } else {
                    // Delete char in front of cursor
                    if (mCursorPos < utf8(mString).length) {
                        if (mCursorPos != mLastModifyIdx) {
                            bigChange = true;
                        }
                        byte[] aString = utf8(mString);
                        int aNext = Common.utf8NextBoundary(aString, mCursorPos);
                        mString = prefix(aString, mCursorPos) + fromByte(aString, aNext);
                        mLastModifyIdx = mCursorPos;
                    }
                }
            }
        } else if (theKey == KeyCode.KEYCODE_HOME) {
            mCursorPos = 0;
        } else if (theKey == KeyCode.KEYCODE_END) {
            mCursorPos = utf8(mString).length;
        } else if (theKey == KeyCode.KEYCODE_RETURN) {
            if (mEditListener != null) {
                mEditListener.editWidgetText(mId, mString);
            }
        } else {
            int uTheChar = theChar;
            int range = 127;
            if (SexyAppBase.current.mbAllowExtendedChars) {
                range = 255;
            }

            if ((uTheChar >= 32) && (uTheChar <= range)) {
                keyText(String.valueOf(theChar));
                if (mString.equals(anOldString)) {
                    // nothing renderable inserted
                    removeHilite = false;
                }
            } else {
                removeHilite = false;
            }
        }

        enforceMaxChars();

        enforceMaxPixels();

        mCursorPos = Math.min(Math.max(mCursorPos, 0), utf8(mString).length);

        if (anOldCursorPos != mCursorPos) {
            mBlinkAcc = 0;
            mShowingCursor = true;
        }

        focusCursor(true);

        if (removeHilite || mHilitePos == mCursorPos) {
            mHilitePos = -1;
        }

        if (bigChange) {
            mUndoString = anOldString;
            mUndoCursor = anOldCursorPos;
            mUndoHilitePos = anOldHilitePos;
        }

        markDirty();
    }

    // EditWidget::InsertTextAtCursor (EditWidget.cpp:540)
    private void insertTextAtCursor(String theText) {
        if (mFont == null) {
            return;
        }

        // Keep only code points the font can render.
        StringBuilder aInsert = new StringBuilder();
        byte[] aTextBytes = theText.getBytes(StandardCharsets.UTF_8);
        int aOffset = 0;
        while (aOffset < aTextBytes.length) {
            int aCharStart = aOffset;
            int[] aNext = {aOffset};
            int aChar = Common.utf8DecodeNext(aTextBytes, aNext);
            if (aChar < 0) {
                aOffset = aCharStart + 1; // skip the invalid byte, keep the rest
                continue;
            }
            aOffset = aNext[0];
            if (aChar >= 32 && mFont.charWidth(aChar) != 0) {
                aInsert.append(new String(aTextBytes, aCharStart, aOffset - aCharStart,
                        StandardCharsets.UTF_8));
            }
        }

        if (aInsert.length() == 0) {
            return;
        }

        String aInsertString = aInsert.toString();
        byte[] aString = utf8(mString);
        if ((mHilitePos != -1) && (mHilitePos != mCursorPos)) {
            // Replace selection with inserted text.
            mString = prefix(aString, Math.min(mCursorPos, mHilitePos))
                    + aInsertString
                    + fromByte(aString, Math.max(mCursorPos, mHilitePos));
            mCursorPos = Math.min(mCursorPos, mHilitePos);
        } else {
            mString = prefix(aString, mCursorPos) + aInsertString + fromByte(aString, mCursorPos);
        }

        mCursorPos += aInsertString.getBytes(StandardCharsets.UTF_8).length;
        mHilitePos = -1;
    }

    // EditWidget::KeyText (EditWidget.cpp:579)
    @Override
    public void keyText(String theText) {
        if (theText == null || theText.isEmpty()) {
            return;
        }

        String anOldString = mString;
        int anOldCursorPos = mCursorPos;
        int anOldHilitePos = mHilitePos;
        int aPreInsertCursorPos = mCursorPos;
        boolean aHadSelection = (mHilitePos != -1) && (mHilitePos != mCursorPos);

        insertTextAtCursor(theText);

        enforceMaxChars();

        enforceMaxPixels();

        mCursorPos = Math.min(Math.max(mCursorPos, 0), utf8(mString).length);

        if ((!mString.equals(anOldString))
                || (mCursorPos != anOldCursorPos)
                || (mHilitePos != anOldHilitePos)) {
            if (aHadSelection || aPreInsertCursorPos != mLastModifyIdx) {
                mUndoString = anOldString;
                mUndoCursor = anOldCursorPos;
                mUndoHilitePos = anOldHilitePos;
            }
            mLastModifyIdx = mCursorPos;
            mHilitePos = -1;
            mBlinkAcc = 0;
            mShowingCursor = true;
        } else {
            mLastModifyIdx = -1;
        }

        focusCursor(true);
        markDirty();
    }

    // EditWidget::KeyDown (EditWidget.cpp:622)
    @Override
    public void keyDown(int theKey) {
        if (((theKey < 'A') || (theKey >= 'Z'))) {
            processKey(theKey, (char) 0);
        }

        super.keyDown(theKey);
    }

    // EditWidget::KeyChar (EditWidget.cpp:630)
    @Override
    public void keyChar(char theChar) {
        processKey(KeyCode.KEYCODE_UNKNOWN, theChar);

        super.keyChar(theChar);
    }

    // EditWidget::GetCharAt (EditWidget.cpp:637)
    public int getCharAt(int x, int y) {
        int aPos = 0;

        byte[] aString = utf8(mString);

        for (int i = mLeftPos; i < aString.length;) {
            int aNext = Common.utf8NextBoundary(aString, i);
            String aLoSubStr = slice(aString, mLeftPos, i - mLeftPos);
            String aHiSubStr = slice(aString, mLeftPos, aNext - mLeftPos);

            int aLoLen = mFont.stringWidth(aLoSubStr);
            int aHiLen = mFont.stringWidth(aHiSubStr);
            if (x >= (aLoLen + aHiLen) / 2 + 5) {
                aPos = aNext;
            }
            i = aNext;
        }

        return aPos;
    }

    // EditWidget::FocusCursor (EditWidget.cpp:659)
    public void focusCursor(boolean bigJump) {
        while (mCursorPos < mLeftPos) {
            if (bigJump) {
                for (int i = 0; i < 10 && mLeftPos > 0; i++) {
                    mLeftPos = Common.utf8PrevBoundary(utf8(mString), mLeftPos);
                }
            } else {
                mLeftPos = Common.utf8PrevBoundary(utf8(mString), mLeftPos);
            }
            markDirty();
        }

        if (mFont != null) {
            byte[] aString = utf8(mString);
            while ((mWidth - 8 > 0)
                    && (mFont.stringWidth(prefix(aString, mCursorPos))
                            - mFont.stringWidth(prefix(aString, mLeftPos)) >= mWidth - 8)) {
                if (bigJump) {
                    for (int i = 0; i < 10 && mLeftPos < aString.length; i++) {
                        mLeftPos = Common.utf8NextBoundary(aString, mLeftPos);
                    }
                } else {
                    mLeftPos = Common.utf8NextBoundary(aString, mLeftPos);
                }

                markDirty();
            }
        }

        updateTextInputArea();
    }

    // EditWidget::MouseDown (EditWidget.cpp:693)
    @Override
    public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
        super.mouseDown(x, y, theBtnNum, theClickCount);

        if (mHadFocusBeforePress) {
            // restart the session on a repeat press; the first press goes through GotFocus
            StringBuilder value = new StringBuilder();
            boolean wrote = mWidgetManager.mApp.startTextInput(value);
            if (wrote) {
                setText(value.toString(), true);
            }
        }

        mHilitePos = -1;
        mCursorPos = getCharAt(x, y);

        if (theClickCount > 1) {
            mHadDoubleClick = true;
            hiliteWord();
        }

        markDirty();

        focusCursor(false);
    }

    // EditWidget::MouseDown (EditWidget.h:99)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);
    }

    // EditWidget::MouseUp (EditWidget.cpp:719)
    @Override
    public void mouseUp(int x, int y, int theBtnNum, int theClickCount) {
        super.mouseUp(x, y, theBtnNum, theClickCount);
        if (mHilitePos == mCursorPos) {
            mHilitePos = -1;
        }

        if (mHadDoubleClick) {
            mHilitePos = -1;
            mCursorPos = getCharAt(x, y);

            mHadDoubleClick = false;
            hiliteWord();
        }

        markDirty();
    }

    // EditWidget::MouseUp (EditWidget.h:100)
    @Override
    public void mouseUp(int x, int y) {
        super.mouseUp(x, y);
    }

    // EditWidget::MouseUp (EditWidget.h:101)
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        super.mouseUp(x, y, theClickCount);
    }

    // EditWidget::HiliteWord (EditWidget.cpp:737)
    protected void hiliteWord() {
        byte[] aString = utf8(mString);

        if (mCursorPos < aString.length) {
            // Find first space before word
            mHilitePos = mCursorPos;
            while (mHilitePos > 0) {
                int aPrev = Common.utf8PrevBoundary(aString, mHilitePos);
                if (!isPartOfWord(Common.utf8CodePointAt(aString, aPrev))) {
                    break;
                }
                mHilitePos = aPrev;
            }

            // Find first space after word
            while (mCursorPos < aString.length) {
                if (!isPartOfWord(Common.utf8CodePointAt(aString, mCursorPos))) {
                    break;
                }
                mCursorPos = Common.utf8NextBoundary(aString, mCursorPos);
            }
        }
    }

    // EditWidget::MouseDrag (EditWidget.cpp:763)
    @Override
    public void mouseDrag(int x, int y) {
        super.mouseDrag(x, y);

        if (mHilitePos == -1) {
            mHilitePos = mCursorPos;
        }

        mCursorPos = getCharAt(x, y);
        markDirty();

        focusCursor(false);
    }

    // EditWidget::MouseEnter (EditWidget.cpp:776)
    @Override
    public void mouseEnter() {
        super.mouseEnter();

        mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_TEXT);
    }

    // EditWidget::MouseLeave (EditWidget.cpp:783)
    @Override
    public void mouseLeave() {
        super.mouseLeave();

        mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
    }

    // EditWidget::MarkDirty (EditWidget.cpp:790)
    @Override
    public void markDirty() {
        if (mColors.mBkg.mAlpha != 255) {
            super.markDirtyFull();
        } else {
            super.markDirty();
        }
    }

    // ------------------------------------------------------------------
    // UTF-8 <-> Java String helpers (upstream keeps raw std::string bytes and
    // byte offsets; positions in this port are UTF-8 byte offsets too)
    // ------------------------------------------------------------------

    private static byte[] utf8(String theString) {
        return theString.getBytes(StandardCharsets.UTF_8);
    }

    /** upstream {@code theString.substr(0, theByteLen)} */
    private static String prefix(byte[] theBytes, int theByteLen) {
        return new String(theBytes, 0, theByteLen, StandardCharsets.UTF_8);
    }

    /** upstream {@code theString.substr(theByteOffset)} */
    private static String fromByte(byte[] theBytes, int theByteOffset) {
        return new String(theBytes, theByteOffset, theBytes.length - theByteOffset,
                StandardCharsets.UTF_8);
    }

    /** upstream {@code theString.substr(theByteOffset, theByteLen)} */
    private static String slice(byte[] theBytes, int theByteOffset, int theByteLen) {
        return new String(theBytes, theByteOffset, theByteLen, StandardCharsets.UTF_8);
    }

    /** upstream {@code theString.find_first_of(theChars)} ({@code std::string::npos} -&gt; -1) */
    private static int findFirstOf(String theString, String theChars) {
        int aMin = -1;
        for (int i = 0; i < theChars.length(); i++) {
            int aPos = theString.indexOf(theChars.charAt(i));
            if (aPos != -1 && (aMin == -1 || aPos < aMin)) {
                aMin = aPos;
            }
        }
        return aMin;
    }
}
