package cn.enaium.fabricpvz.portable.sexy;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/SexyAppFramework/widget/ListWidget.h:49 (implementation
 * src/SexyAppFramework/widget/ListWidget.cpp:44).
 *
 * <p>A scrollable list of text lines that can be chained into a parent/child cascade (a "header"
 * list plus any number of aligned follower lists). Every cascade operation walks from the list it is
 * invoked on up to the root {@code mParent} and then down the {@code mChild} chain, which is why the
 * same mutation is applied in a loop in most of the methods below.
 *
 * <p>{@code mScrollbar} is the optional attached {@link ScrollbarWidget}: it is set from the outside
 * (there is no {@code AddScrollbar} here) and every use is guarded by an upstream null check.
 */
public class ListWidget extends Widget implements ScrollListener {
    // ListWidget.h:39
    public static class ListWidgetColorScheme {
        public Color mBkg;
        public Color mOutline;
        public Color mText;
        public Color mHilite;
        public Color mSelect;

        /**
         * Upstream {@code std::optional<Color> mSelectText}: {@code null} means {@code std::nullopt}
         * (the selected line then keeps its own {@code mLineColors} entry).
         */
        public Color mSelectText;

        public ListWidgetColorScheme() {
        }

        public ListWidgetColorScheme(Color theBkg, Color theOutline, Color theText, Color theHilite,
                Color theSelect, Color theSelectText) {
            mBkg = theBkg;
            mOutline = theOutline;
            mText = theText;
            mHilite = theHilite;
            mSelect = theSelect;
            mSelectText = theSelectText;
        }
    }

    // ListWidget.h:52-57
    public static final int JUSTIFY_LEFT = 0;
    public static final int JUSTIFY_CENTER = 1;
    public static final int JUSTIFY_RIGHT = 2;

    /** Upstream {@code static constexpr ListWidgetColorScheme gInitialListWidgetColors} (ListWidget.cpp:35). */
    private static final ListWidgetColorScheme gInitialListWidgetColors = new ListWidgetColorScheme(
            new Color(255, 255, 255),
            new Color(255, 255, 255),
            new Color(0, 0, 0),
            new Color(0, 192, 0),
            new Color(0, 0, 128),
            new Color(255, 255, 255));

    // ListWidget.h:61-81
    public int mId;
    public Font mFont;

    public ScrollbarWidget mScrollbar;

    public int mJustify;
    public ListWidgetColorScheme mColors;

    public List<String> mLines;
    public List<Color> mLineColors;
    public double mPosition;
    public double mPageSize;
    public int mHiliteIdx;
    public int mSelectIdx;
    public ListListener mListListener;
    public ListWidget mParent;
    public ListWidget mChild;
    public boolean mSortFromChild;
    public boolean mDrawOutline;
    public int mMaxNumericPlaces;
    public int mItemHeight;

    public boolean mDrawSelectWhenHilited;
    public boolean mDoFingerWhenHilited;

    // ListWidget::ListWidget (ListWidget.cpp:44)
    public ListWidget(int theId, Font theFont, ListListener theListListener) {
        mJustify = JUSTIFY_LEFT;
        mHiliteIdx = -1;
        mSelectIdx = -1;

        if (theFont != null) {
            mItemHeight = theFont.getHeight();
        } else {
            mItemHeight = -1;
        }

        setColors(gInitialListWidgetColors);

        mId = theId;
        mFont = theFont;
        mListListener = theListListener;
        mParent = null;
        mChild = null;
        mScrollbar = null;
        mPosition = 0;
        mPageSize = 0;
        mSortFromChild = false;
        mDrawOutline = true;
        mMaxNumericPlaces = 0;
        mDrawSelectWhenHilited = false;
        mDoFingerWhenHilited = true;

        mLines = new ArrayList<>();
        mLineColors = new ArrayList<>();
    }

    // ListWidget::SetColors (ListWidget.cpp:74)
    public void setColors(ListWidgetColorScheme theColors) {
        // Upstream assigns the struct by value; copy field by field so callers keep ownership.
        mColors = new ListWidgetColorScheme(theColors.mBkg, theColors.mOutline, theColors.mText,
                theColors.mHilite, theColors.mSelect, theColors.mSelectText);
        markDirty();
    }

    // ListWidget::RemovedFromManager (ListWidget.cpp:80)
    @Override
    public void removedFromManager(WidgetManager theManager) {
        super.removedFromManager(theManager);
        if (mListListener != null) {
            mListListener.listClosed(mId);
        }
    }

    // ListWidget::GetSortKey (ListWidget.cpp:87)
    public String getSortKey(int theIdx) {
        String aString = mLines.get(theIdx);

        while (aString.length() < mMaxNumericPlaces) {
            aString = "0" + aString;
        }

        if (mSortFromChild) {
            return mChild.getSortKey(theIdx) + aString;
        }

        if (mChild == null) {
            return aString;
        }

        return aString + mChild.getSortKey(theIdx);
    }

    // ListWidget::Sort (ListWidget.cpp:107)
    public void sort(boolean ascending) {
        int aCount = mLines.size();
        int[] aMap = new int[aCount];
        String[] aKeys = new String[aCount];

        for (int i = 0; i < aCount; i++) {
            aMap[i] = i;
            aKeys[i] = getSortKey(i);
        }

        for (int i = 1; i < aCount; i++) {
            for (int j = 0; j < aCount - i; j++) {
                int aComp = aKeys[j].compareTo(aKeys[j + 1]);
                if ((ascending && (aComp > 0)) || (!ascending && (aComp < 0))) {
                    int aTempMap = aMap[j];
                    aMap[j] = aMap[j + 1];
                    aMap[j + 1] = aTempMap;

                    String aTempKey = aKeys[j];
                    aKeys[j] = aKeys[j + 1];
                    aKeys[j + 1] = aTempKey;
                }
            }
        }

        ListWidget aListWidget = this;
        while (aListWidget.mParent != null) {
            aListWidget = aListWidget.mParent;
        }

        while (aListWidget != null) {
            List<String> aNewLines = new ArrayList<>();
            List<Color> aNewLineColors = new ArrayList<>();

            for (int i = 0; i < aCount; i++) {
                aNewLines.add(aListWidget.mLines.get(aMap[i]));
                aNewLineColors.add(aListWidget.mLineColors.get(aMap[i]));
            }

            aListWidget.mLines = aNewLines;
            aListWidget.mLineColors = aNewLineColors;

            aListWidget.markDirty();

            aListWidget = aListWidget.mChild;
        }
    }

    // ListWidget::GetStringAt (ListWidget.cpp:155)
    public String getStringAt(int theIdx) {
        return mLines.get(theIdx);
    }

    // ListWidget::Resize (ListWidget.cpp:161)
    @Override
    public void resize(int theX, int theY, int theWidth, int theHeight) {
        super.resize(theX, theY, theWidth, theHeight);

        double aPageSize = 1;
        int anItemHeight = (mItemHeight != -1) ? mItemHeight : mFont.getHeight();

        if (mHeight > anItemHeight + 8) {
            aPageSize = (mHeight - 8.0) / anItemHeight;
        }

        mPageSize = aPageSize;

        if (mScrollbar != null) {
            mScrollbar.setPageSize(aPageSize);
        }
    }

    // ListWidget::AddLine (ListWidget.cpp:177)
    public int addLine(String theLine, boolean alphabetical) {
        int anIdx = -1;
        boolean inserted = false;

        if (alphabetical) {
            for (int i = 0; i < mLines.size(); i++) {
                if (theLine.compareTo(mLines.get(i)) < 0) {
                    anIdx = i;

                    ListWidget aListWidget = this;

                    while (aListWidget.mParent != null) {
                        aListWidget = aListWidget.mParent;
                    }

                    while (aListWidget != null) {
                        if (aListWidget == this) {
                            aListWidget.mLines.add(i, theLine);
                        } else {
                            aListWidget.mLines.add(i, "-");
                        }

                        aListWidget.mLineColors.add(i, mColors.mText);
                        aListWidget.markDirty();

                        aListWidget = aListWidget.mChild;
                    }

                    inserted = true;
                    break;
                }
            }
        }

        if (!inserted) {
            anIdx = mLines.size();

            ListWidget aListWidget = this;

            while (aListWidget.mParent != null) {
                aListWidget = aListWidget.mParent;
            }

            while (aListWidget != null) {
                if (aListWidget == this) {
                    aListWidget.mLines.add(theLine);
                } else {
                    aListWidget.mLines.add("-");
                }

                aListWidget.mLineColors.add(mColors.mText);
                aListWidget.markDirty();

                aListWidget = aListWidget.mChild;
            }
        }

        if (mScrollbar != null) {
            mScrollbar.setMaxValue(mLines.size());
        }

        return anIdx;
    }

    // ListWidget::SetLine (ListWidget.cpp:242)
    public void setLine(int theIdx, String theString) {
        mLines.set(theIdx, theString);
        markDirty();
    }

    // ListWidget::GetLineCount (ListWidget.cpp:248)
    public int getLineCount() {
        return mLines.size();
    }

    // ListWidget::GetLineIdx (ListWidget.cpp:253)
    public int getLineIdx(String theLine) {
        for (int i = 0; i < mLines.size(); i++) {
            if (mLines.get(i).compareTo(theLine) == 0) {
                return i;
            }
        }

        return -1;
    }

    // ListWidget::SetColor (ListWidget.cpp:262)
    public void setColor(String theLine, Color theColor) {
        int anIdx = getLineIdx(theLine);
        setLineColor(anIdx, theColor);
    }

    // ListWidget::SetLineColor (ListWidget.cpp:268)
    public void setLineColor(int theIdx, Color theColor) {
        if ((theIdx >= 0) && (theIdx < mLines.size())) {
            ListWidget aListWidget = this;

            while (aListWidget.mParent != null) {
                aListWidget = aListWidget.mParent;
            }

            while (aListWidget != null) {
                aListWidget.mLineColors.set(theIdx, theColor);
                aListWidget.markDirty();

                aListWidget = aListWidget.mChild;
            }
        }
    }

    // ListWidget::RemoveLine (ListWidget.cpp:287)
    public void removeLine(int theIdx) {
        if (theIdx != -1) {
            ListWidget aListWidget = this;

            while (aListWidget.mParent != null) {
                aListWidget = aListWidget.mParent;
            }

            while (aListWidget != null) {
                aListWidget.mLines.remove(theIdx);
                aListWidget.mLineColors.remove(theIdx);

                aListWidget.markDirty();
                aListWidget = aListWidget.mChild;
            }
        }

        if (mScrollbar != null) {
            mScrollbar.setMaxValue(mLines.size());
        }
    }

    // ListWidget::RemoveAll (ListWidget.cpp:310)
    public void removeAll() {
        ListWidget aListWidget = this;

        while (aListWidget.mParent != null) {
            aListWidget = aListWidget.mParent;
        }

        while (aListWidget != null) {
            aListWidget.mLines.clear();
            aListWidget.mLineColors.clear();
            aListWidget.mSelectIdx = -1;
            aListWidget.mHiliteIdx = -1;

            aListWidget.markDirty();
            aListWidget = aListWidget.mChild;
        }

        if (mScrollbar != null) {
            mScrollbar.setMaxValue(mLines.size());
        }
    }

    // ListWidget::GetOptimalWidth (ListWidget.cpp:332)
    public int getOptimalWidth() {
        int aMaxWidth = 0;

        for (int i = 0; i < mLines.size(); i++) {
            aMaxWidth = Math.max(aMaxWidth, mFont.stringWidth(mLines.get(i)));
        }

        return aMaxWidth + 16;
    }

    // ListWidget::GetOptimalHeight (ListWidget.cpp:342)
    public int getOptimalHeight() {
        int anItemHeight = (mItemHeight != -1) ? mItemHeight : mFont.getHeight();

        return anItemHeight * mLines.size() + 8;
    }

    // ListWidget::OrderInManagerChanged (ListWidget.cpp:349)
    @Override
    public void orderInManagerChanged() {
        super.orderInManagerChanged();

        if (mChild != null) {
            SexyAppBase.current.mWidgetManager.putInfront(mChild, this);
        }
        if (mScrollbar != null) {
            SexyAppBase.current.mWidgetManager.putInfront(mScrollbar, this);
        }
    }

    // ListWidget::Draw (ListWidget.cpp:359)
    @Override
    public void draw(Graphics g) {
        g.setColor(mColors.mBkg);
        g.fillRect(0, 0, mWidth, mHeight);

        Graphics aClipG = new Graphics(g);
        aClipG.clipRect(4, 4, mWidth - 8, mHeight - 8);

        Graphics aSelectClipG = new Graphics(g);
        aSelectClipG.clipRect(0, 4, mWidth, mHeight - 8);

        aClipG.setFont(mFont);

        int aFirstLine = (int) mPosition;
        int aLastLine = Math.min(mLines.size() - 1, (int) mPosition + (int) mPageSize + 1);

        int anItemHeight;
        int anItemOffset;
        if (mItemHeight != -1) {
            anItemHeight = mItemHeight;
            anItemOffset = (anItemHeight - mFont.getHeight()) / 2;
        } else {
            anItemHeight = mFont.getHeight();
            anItemOffset = 0;
        }

        for (int i = aFirstLine; i <= aLastLine; i++) {
            int aDrawY = 4 + (int) ((i - mPosition) * anItemHeight);

            if ((i == mSelectIdx) || ((i == mHiliteIdx) && mDrawSelectWhenHilited)) {
                aSelectClipG.setColor(mColors.mSelect);
                aSelectClipG.fillRect(0, aDrawY, mWidth, anItemHeight);
            }

            if (i == mHiliteIdx) {
                aClipG.setColor(mColors.mHilite);
            } else if ((i == mSelectIdx) && (mColors.mSelectText != null)) {
                aClipG.setColor(mColors.mSelectText);
            } else {
                aClipG.setColor(mLineColors.get(i));
            }

            String aString = mLines.get(i);
            int aFontX;
            switch (mJustify) {
                case JUSTIFY_LEFT:
                    aFontX = 4;
                    break;
                case JUSTIFY_CENTER:
                    aFontX = (mWidth - mFont.stringWidth(aString)) / 2;
                    break;
                default:
                    aFontX = mWidth - mFont.stringWidth(aString) - 4;
                    break;
            }

            aClipG.drawString(aString, aFontX, aDrawY + mFont.getAscent() + anItemOffset);
        }

        if (mDrawOutline) {
            g.setColor(mColors.mOutline);
            g.drawRect(0, 0, mWidth - 1, mHeight - 1);
        }
    }

    // ListWidget::ScrollPosition (ListWidget.cpp:429)
    @Override
    public void scrollPosition(int theId, double thePosition) {
        if (mChild != null) {
            mChild.scrollPosition(theId, thePosition);
        }

        mPosition = thePosition;
        markDirty();
    }

    // ListWidget::SetHilite (ListWidget.cpp:438)
    public void setHilite(int theHiliteIdx, boolean notifyListener) {
        int anOldIdx = mHiliteIdx;
        mHiliteIdx = theHiliteIdx;
        if ((anOldIdx != mHiliteIdx) && notifyListener && (mListListener != null)) {
            mListListener.listHiliteChanged(mId, anOldIdx, mHiliteIdx);
        }
    }

    // ListWidget::SetHilite (ListWidget.cpp:438) — upstream default argument notifyListener = false
    public void setHilite(int theHiliteIdx) {
        setHilite(theHiliteIdx, false);
    }

    // ListWidget::MouseMove (ListWidget.cpp:447)
    @Override
    public void mouseMove(int x, int y) {
        int anItemHeight = (mItemHeight != -1) ? mItemHeight : mFont.getHeight();

        int aNewHilite = (int) (((y - 4) / (double) anItemHeight) + mPosition);
        if ((aNewHilite < 0) || (aNewHilite >= mLines.size())) {
            aNewHilite = -1;
        }

        if (aNewHilite != mHiliteIdx) {
            ListWidget aListWidget = this;

            while (aListWidget.mParent != null) {
                aListWidget = aListWidget.mParent;
            }

            while (aListWidget != null) {
                aListWidget.setHilite(aNewHilite, true);
                aListWidget.markDirty();
                aListWidget = aListWidget.mChild;
            }

            if ((mHiliteIdx == -1) || !mDoFingerWhenHilited) {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
            } else {
                mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_HAND);
            }
        }
    }

    // ListWidget::MouseDown (ListWidget.h:111)
    @Override
    public void mouseDown(int x, int y, int theClickCount) {
        super.mouseDown(x, y, theClickCount);
    }

    // ListWidget::MouseDown (ListWidget.cpp:476)
    @Override
    public void mouseDown(int x, int y, int theBtnNum, int theClickCount) {
        if ((mHiliteIdx != -1) && (mListListener != null)) {
            mListListener.listClicked(mId, mHiliteIdx, theClickCount);
        }
    }

    // ListWidget::MouseLeave (ListWidget.cpp:482)
    @Override
    public void mouseLeave() {
        ListWidget aListWidget = this;

        while (aListWidget.mParent != null) {
            aListWidget = aListWidget.mParent;
        }

        while (aListWidget != null) {
            aListWidget.setHilite(-1, true);
            aListWidget.markDirty();
            aListWidget = aListWidget.mChild;
        }

        mWidgetManager.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
    }

    // ListWidget::SetSelect (ListWidget.cpp:499)
    public void setSelect(int theSelectIdx) {
        ListWidget aListWidget = this;

        while (aListWidget.mParent != null) {
            aListWidget = aListWidget.mParent;
        }

        while (aListWidget != null) {
            aListWidget.mSelectIdx = theSelectIdx;
            aListWidget.markDirty();
            aListWidget = aListWidget.mChild;
        }
    }

    // ListWidget::MouseWheel (ListWidget.cpp:515)
    @Override
    public void mouseWheel(int theDelta) {
        if (mScrollbar != null) {
            int aScrollAmount = 5;
            if (theDelta > 0) {
                mScrollbar.setValue(mScrollbar.mValue - aScrollAmount);
                // mScrollbar.mUpdateMode = ScrollbarWidget.UPDATE_MODE_PGUP;
                // mScrollbar.mUpdateAcc = 0;
            } else if (theDelta < 0) {
                mScrollbar.setValue(mScrollbar.mValue + aScrollAmount);
                // mScrollbar.mUpdateMode = ScrollbarWidget.UPDATE_MODE_PGDN;
                // mScrollbar.mUpdateAcc = 0;
            }
        }
    }
}
