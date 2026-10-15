package cn.enaium.fabricpvz.portable.sexy;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/SexyAppFramework/widget/Dialog.h:56 (implementation
 * src/SexyAppFramework/widget/Dialog.cpp:53).
 *
 * <p>A standard modal/modeless message box with a header, word-wrapped lines, an optional footer
 * and one or two {@link DialogButton}s. Public fields keep the upstream names; virtual method names
 * are camelCased.
 */
public class Dialog extends Widget implements ButtonListener {
    // Dialog.h:58-66
    public static final int BUTTONS_NONE = 0;
    public static final int BUTTONS_YES_NO = 1;
    public static final int BUTTONS_OK_CANCEL = 2;
    public static final int BUTTONS_FOOTER = 3;

    // Dialog.h:68-75
    public static final int ID_YES = 1000;
    public static final int ID_NO = 1001;
    public static final int ID_OK = 1000;
    public static final int ID_CANCEL = 1001;
    public static final int ID_FOOTER = 1000;

    // Dialog.cpp:38-41 (mutable global strings in the upstream Sexy namespace)
    public static String DIALOG_YES_STRING = "YES";
    public static String DIALOG_NO_STRING = "NO";
    public static String DIALOG_OK_STRING = "OK";
    public static String DIALOG_CANCEL_STRING = "CANCEL";

    /**
     * Ported from the upstream {@code struct DialogColorScheme} (Dialog.h:45).
     *
     * <p>Value type with the seven theme colours used by the dialog.
     */
    public static class DialogColorScheme {
        public Color mHeader;
        public Color mLines;
        public Color mFooter;
        public Color mButtonText;
        public Color mButtonTextHilite;
        public Color mBkg;
        public Color mOutline;
    }

    // Dialog.h:77-123
    public DialogListener mDialogListener;
    public Image mComponentImage;
    public DialogColorScheme mColors;
    public DialogButton mYesButton;
    public DialogButton mNoButton;
    public int mNumButtons;

    public String mDialogHeader;
    public String mDialogFooter;
    public String mDialogLines;

    public int mButtonMode;
    public Font mHeaderFont;
    public Font mLinesFont;
    public int mTextAlign;
    public int mLineSpacingOffset;
    public int mButtonHeight;
    public Insets mBackgroundInsets;
    public Insets mContentInsets;
    public int mSpaceAfterHeader;
    public boolean mDragging;
    public int mDragMouseX;
    public int mDragMouseY;
    public int mId;
    public boolean mIsModal;
    public int mResult;

    public int mButtonHorzSpacing;
    public int mButtonSidePadding;

    // gDefaultDialogColors (Dialog.cpp:43)
    private static DialogColorScheme defaultDialogColors() {
        DialogColorScheme aColors = new DialogColorScheme();
        aColors.mHeader = new Color(255, 255, 255);
        aColors.mLines = new Color(255, 255, 0);
        aColors.mFooter = new Color(255, 255, 255);
        aColors.mButtonText = new Color(255, 255, 255);
        aColors.mButtonTextHilite = new Color(255, 255, 255);
        aColors.mBkg = new Color(80, 80, 80);
        aColors.mOutline = new Color(255, 255, 255);
        return aColors;
    }

    // Dialog::Dialog (Dialog.cpp:53)
    public Dialog(Image theComponentImage, Image theButtonComponentImage, int theId, boolean isModal,
            String theDialogHeader, String theDialogLines, String theDialogFooter,
            int theButtonMode) {
        mId = theId;
        mResult = 0x7FFFFFFF;
        mComponentImage = theComponentImage;
        mIsModal = isModal;
        mContentInsets = new Insets(24, 24, 24, 24);
        mBackgroundInsets = new Insets();
        mTextAlign = 0;
        mLineSpacingOffset = 0;
        mSpaceAfterHeader = 10;
        mButtonSidePadding = 0;
        mButtonHorzSpacing = 8;
        mDialogListener = SexyAppBase.current;

        mDialogHeader = theDialogHeader;
        mDialogFooter = theDialogFooter;
        mButtonMode = theButtonMode;

        if ((mButtonMode == BUTTONS_YES_NO) || (mButtonMode == BUTTONS_OK_CANCEL)) {
            mYesButton = new DialogButton(theButtonComponentImage, ID_YES, this);
            mNoButton = new DialogButton(theButtonComponentImage, ID_NO, this);

            if (mButtonMode == BUTTONS_YES_NO) {
                mYesButton.mLabel = DIALOG_YES_STRING;
                mNoButton.mLabel = DIALOG_NO_STRING;
            } else {
                mYesButton.mLabel = DIALOG_OK_STRING;
                mNoButton.mLabel = DIALOG_CANCEL_STRING;
            }
        } else if (mButtonMode == BUTTONS_FOOTER) {
            mYesButton = new DialogButton(theButtonComponentImage, ID_FOOTER, this);
            mYesButton.mLabel = mDialogFooter;
        } else {
            mNumButtons = 0;
        }

        mDialogLines = theDialogLines;

        mButtonHeight = (theButtonComponentImage == null) ? 24 : theButtonComponentImage.mHeight;

        mHasTransparencies = true;
        mHasAlpha = true;

        mDragging = false;
        mPriority = 1;

        DialogColorScheme aColors = defaultDialogColors();
        if (theButtonComponentImage == null) {
            aColors.mButtonText = new Color(0, 0, 0);
            aColors.mButtonTextHilite = new Color(0, 0, 0);
        }

        setColors(aColors);
    }

    // Dialog::~Dialog (Dialog.cpp:118)
    // Upstream default destructor; the Java port relies on GC. No explicit close of the buttons is
    // needed since Widget.dispose() owns their resources.

    // Dialog::SetColors (Dialog.cpp:120)
    public void setColors(DialogColorScheme theColors) {
        mColors = theColors;
        markDirty();

        if (mYesButton != null) {
            mYesButton.setLabelColor(mColors.mButtonText);
            mYesButton.setLabelHiliteColor(mColors.mButtonTextHilite);
        }
        if (mNoButton != null) {
            mNoButton.setLabelColor(mColors.mButtonText);
            mNoButton.setLabelHiliteColor(mColors.mButtonTextHilite);
        }
    }

    // Dialog::SetHeaderColor (Dialog.cpp:137)
    public void setHeaderColor(Color theColor) {
        mColors.mHeader = theColor;
        markDirty();
    }

    // Dialog::SetLinesColor (Dialog.cpp:143)
    public void setLinesColor(Color theColor) {
        mColors.mLines = theColor;
        markDirty();
    }

    // Dialog::SetButtonTextColor (Dialog.cpp:149)
    public void setButtonTextColor(Color theColor) {
        mColors.mButtonText = theColor;
        markDirty();

        if (mYesButton != null) {
            mYesButton.setLabelColor(theColor);
        }
        if (mNoButton != null) {
            mNoButton.setLabelColor(theColor);
        }
    }

    // Dialog::SetButtonTextHiliteColor (Dialog.cpp:160)
    public void setButtonTextHiliteColor(Color theColor) {
        mColors.mButtonTextHilite = theColor;
        markDirty();

        if (mYesButton != null) {
            mYesButton.setLabelHiliteColor(theColor);
        }
        if (mNoButton != null) {
            mNoButton.setLabelHiliteColor(theColor);
        }
    }

    // Dialog::SetButtonFont (Dialog.cpp:171)
    public void setButtonFont(Font theFont) {
        if (mYesButton != null) {
            mYesButton.setFont(theFont);
        }

        if (mNoButton != null) {
            mNoButton.setFont(theFont);
        }
    }

    // Dialog::SetHeaderFont (Dialog.cpp:180)
    public void setHeaderFont(Font theFont) {
        mHeaderFont = theFont.duplicate();
    }

    // Dialog::SetLinesFont (Dialog.cpp:185)
    public void setLinesFont(Font theFont) {
        mLinesFont = theFont.duplicate();
    }

    // Dialog::EnsureFonts (Dialog.cpp:190)
    public void ensureFonts() {
        if (mHeaderFont == null) {
            mHeaderFont = SexyAppBase.current.mDefaultFont.duplicate();
        }
        if (mLinesFont == null) {
            mLinesFont = SexyAppBase.current.mDefaultFont.duplicate();
        }
    }

    // Dialog::GetPreferredHeight (Dialog.cpp:198)
    public int getPreferredHeight(int theWidth) {
        ensureFonts();

        int aHeight = mContentInsets.mTop + mContentInsets.mBottom + mBackgroundInsets.mTop
                + mBackgroundInsets.mBottom;

        boolean needSpace = false;
        if (!mDialogHeader.isEmpty()) {
            aHeight += mHeaderFont.getHeight() - mHeaderFont.getAscentPadding();
            needSpace = true;
        }

        if (!mDialogLines.isEmpty()) {
            if (needSpace) {
                aHeight += mSpaceAfterHeader;
            }
            Graphics g = new Graphics();
            g.setFont(mLinesFont);
            aHeight += getWordWrappedHeight(g,
                    theWidth - mContentInsets.mLeft - mContentInsets.mRight - mBackgroundInsets.mLeft
                            - mBackgroundInsets.mRight - 4,
                    mDialogLines, mLinesFont.getLineSpacing() + mLineSpacingOffset);
            needSpace = true;
        }

        if (!mDialogFooter.isEmpty() && (mButtonMode != BUTTONS_FOOTER)) {
            if (needSpace) {
                aHeight += 8;
            }
            aHeight += mHeaderFont.getLineSpacing();
            needSpace = true;
        }

        if (mYesButton != null) {
            if (needSpace) {
                aHeight += 8;
            }
            aHeight += mButtonHeight + 8;
        }

        return aHeight;
    }

    // Dialog::Draw (Dialog.cpp:239)
    @Override
    public void draw(Graphics g) {
        ensureFonts();

        Rect aBoxRect = new Rect(mBackgroundInsets.mLeft, mBackgroundInsets.mTop,
                mWidth - mBackgroundInsets.mLeft - mBackgroundInsets.mRight,
                mHeight - mBackgroundInsets.mTop - mBackgroundInsets.mBottom);
        if (mComponentImage != null) {
            g.drawImageBox(aBoxRect, mComponentImage);
        } else {
            g.setColor(mColors.mOutline);
            g.drawRect(12, 12, mWidth - 12 * 2 - 1, mHeight - 12 * 2 - 1);
            g.setColor(mColors.mBkg);
            g.fillRect(12 + 1, 12 + 1, mWidth - 12 * 2 - 2, mHeight - 12 * 2 - 2);

            g.setColor(new Color(0, 0, 0, 128));
            g.fillRect(mWidth - 12, 12 * 2, 12, mHeight - 12 * 3);
            g.fillRect(12 * 2, mHeight - 12, mWidth - 12 * 2, 12);
        }

        int aCurY = mContentInsets.mTop + mBackgroundInsets.mTop;

        if (!mDialogHeader.isEmpty()) {
            aCurY += mHeaderFont.getAscent() - mHeaderFont.getAscentPadding();

            g.setFont(mHeaderFont);
            g.setColor(mColors.mHeader);
            writeCenteredLine(g, aCurY, mDialogHeader);

            aCurY += mHeaderFont.getHeight() - mHeaderFont.getAscent();

            aCurY += mSpaceAfterHeader;
        }

        g.setFont(mLinesFont);
        g.setColor(mColors.mLines);

        Rect aRect = new Rect(mBackgroundInsets.mLeft + mContentInsets.mLeft + 2, aCurY,
                mWidth - mContentInsets.mLeft - mContentInsets.mRight - mBackgroundInsets.mLeft
                        - mBackgroundInsets.mRight - 4,
                0);
        aCurY += writeWordWrapped(g, aRect, mDialogLines,
                mLinesFont.getLineSpacing() + mLineSpacingOffset, mTextAlign);

        if (!mDialogFooter.isEmpty() && (mButtonMode != BUTTONS_FOOTER)) {
            aCurY += 8;
            aCurY += mHeaderFont.getLineSpacing();

            g.setFont(mHeaderFont);
            g.setColor(mColors.mFooter);
            writeCenteredLine(g, aCurY, mDialogFooter);
        }
    }

    // Dialog::AddedToManager (Dialog.cpp:292)
    @Override
    public void addedToManager(WidgetManager theWidgetManager) {
        super.addedToManager(theWidgetManager);

        if (mYesButton != null) {
            theWidgetManager.addWidget(mYesButton);
        }
        if (mNoButton != null) {
            theWidgetManager.addWidget(mNoButton);
        }
    }

    // Dialog::RemovedFromManager (Dialog.cpp:302)
    @Override
    public void removedFromManager(WidgetManager theWidgetManager) {
        super.removedFromManager(theWidgetManager);

        if (mYesButton != null) {
            theWidgetManager.removeWidget(mYesButton);
        }
        if (mNoButton != null) {
            theWidgetManager.removeWidget(mNoButton);
        }
    }

    // Dialog::OrderInManagerChanged (Dialog.cpp:312)
    @Override
    public void orderInManagerChanged() {
        super.orderInManagerChanged();
        if (mYesButton != null) {
            mWidgetManager.putInfront(mYesButton, this);
        }
        if (mNoButton != null) {
            mWidgetManager.putInfront(mNoButton, this);
        }
    }

    // Dialog::Resize (Dialog.cpp:321)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        if ((mYesButton != null) && (mNoButton != null)) {
            int aBtnWidth = (mWidth - mContentInsets.mLeft - mContentInsets.mRight
                    - mBackgroundInsets.mLeft - mBackgroundInsets.mRight - mButtonSidePadding * 2
                    - mButtonHorzSpacing) / 2;
            int aBtnHeight = mButtonHeight;

            mYesButton.resize(mX + mBackgroundInsets.mLeft + mContentInsets.mLeft
                    + mButtonSidePadding,
                    mY + mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom - aBtnHeight,
                    aBtnWidth, aBtnHeight);
            mNoButton.resize(mYesButton.mX + aBtnWidth + mButtonHorzSpacing, mYesButton.mY,
                    aBtnWidth, aBtnHeight);
        } else if (mYesButton != null) {
            int aBtnHeight = mButtonHeight;

            mYesButton.resize(mX + mContentInsets.mLeft + mBackgroundInsets.mLeft,
                    mY + mHeight - mContentInsets.mBottom - mBackgroundInsets.mBottom - aBtnHeight,
                    mWidth - mContentInsets.mLeft - mContentInsets.mRight - mBackgroundInsets.mLeft
                            - mBackgroundInsets.mRight,
                    aBtnHeight);
        }
    }

    // Dialog::MouseDown (Dialog.h:131)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);
    }

    // Dialog::MouseDown (Dialog.cpp:342)
    @Override
    public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
        if (theClickCount == 1) {
            mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_DRAGGING);
            mDragging = true;
            mDragMouseX = x;
            mDragMouseY = y;
        }
        super.mouseDown(x, y, theBtnNum, theClickCount);
    }

    // Dialog::MouseDrag (Dialog.cpp:354)
    @Override
    public void mouseDrag(int x, int y) {
        if (mDragging) {
            int aNewX = mX + x - mDragMouseX;
            int aNewY = mY + y - mDragMouseY;

            if (aNewX < -8) {
                aNewX = -8;
            } else if (aNewX + mWidth > mWidgetManager.mWidth + 8) {
                aNewX = mWidgetManager.mWidth - mWidth + 8;
            }

            if (aNewY < -8) {
                aNewY = -8;
            } else if (aNewY + mHeight > mWidgetManager.mHeight + 8) {
                aNewY = mWidgetManager.mHeight - mHeight + 8;
            }

            mDragMouseX = mX + x - aNewX;
            mDragMouseY = mY + y - aNewY;

            if (mDragMouseX < 8) {
                mDragMouseX = 8;
            } else if (mDragMouseX > mWidth - 9) {
                mDragMouseX = mWidth - 9;
            }

            if (mDragMouseY < 8) {
                mDragMouseY = 8;
            } else if (mDragMouseY > mHeight - 9) {
                mDragMouseY = mHeight - 9;
            }

            move(aNewX, aNewY);
        }
    }

    // Dialog::MouseUp (Dialog.h:134)
    @Override
    public void mouseUp(int x, int y) {
        super.mouseUp(x, y);
    }

    // Dialog::MouseUp (Dialog.h:135)
    @Override
    public void mouseUp(int x, int y, int theClickCount) {
        super.mouseUp(x, y, theClickCount);
    }

    // Dialog::MouseUp (Dialog.cpp:388)
    @Override
    public void mouseUp(int x, int y, int theBtnNum, int theClickCount) {
        if (mDragging) {
            mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
            mDragging = false;
        }
        super.mouseUp(x, y, theBtnNum, theClickCount);
    }

    // Dialog::Update (Dialog.cpp:398)
    @Override
    public void update() {
        super.update();

        // Move(mX, mY+1);
    }

    // Dialog::IsModal (Dialog.cpp:405)
    public boolean isModal() {
        return mIsModal;
    }

    /**
     * Dialog::WaitForResult (Dialog.cpp:410).
     *
     * <p>Upstream pumps the platform message loop (or the Emscripten rAF loop) until a
     * {@link DialogButton} depresses and writes {@link #mResult}, then calls
     * {@code SexyAppBase::KillDialog(mId)} when {@code autoKill} is {@code true}. This port has no
     * platform message loop to pump and no {@code killDialog} entry point, so the observable
     * contract is reduced to the stored result: it is {@code 0x7FFFFFFF} until a button was
     * depressed, and the caller owns the dialog's lifetime (mirroring {@code autoKill == false}).
     */
    public int waitForResult(boolean autoKill) {
        return mResult;
    }

    // Dialog::ButtonPress (Dialog.cpp:444)
    @Override
    public void buttonPress(int theId) {
        if ((theId == ID_YES) || (theId == ID_NO)) {
            mDialogListener.dialogButtonPress(mId, theId);
        }
    }

    // Dialog::ButtonDepress (Dialog.cpp:450)
    @Override
    public void buttonDepress(int theId) {
        if ((theId == ID_YES) || (theId == ID_NO)) {
            mResult = theId;
            mDialogListener.dialogButtonDepress(mId, theId);
        }
    }

    // Dialog::ButtonDownTick (Dialog.h:143)
    @Override
    public void buttonDownTick(int theId) {
    }

    // Dialog::ButtonMouseEnter (Dialog.h:144)
    @Override
    public void buttonMouseEnter(int theId) {
    }

    // Dialog::ButtonMouseLeave (Dialog.h:145)
    @Override
    public void buttonMouseLeave(int theId) {
    }

    // Dialog::ButtonMouseMove (Dialog.h:146)
    @Override
    public void buttonMouseMove(int theId, int theX, int theY) {
    }
}
