package cn.enaium.fabricpvz.portable.sexy;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/SexyAppFramework/widget/WidgetContainer.h:42 (implementation
 * src/SexyAppFramework/widget/WidgetContainer.cpp:30).
 *
 * <p>The widget tree node: a z-ordered list of child widgets plus geometry, the boolean dirty flag
 * and the modal-flag bookkeeping. Public fields keep the upstream names.
 *
 * <p>Upstream stores the children in a {@code std::list<Widget*>} whose iterators are used to make
 * the update traversal robust against children adding/removing themselves
 * ({@code mUpdateIterator}/{@code mUpdateIteratorModified}). The Java port keeps the same protocol
 * with an index cursor ({@link #mUpdateIterator}) into {@link #mWidgets}; index 0 is the bottom-most
 * widget exactly like upstream.
 */
public class WidgetContainer {
    /** Upstream {@code WidgetList mWidgets} — index 0 is the bottom-most widget. */
    public final List<Widget> mWidgets = new ArrayList<>();

    public WidgetManager mWidgetManager;
    public WidgetContainer mParent;

    public boolean mUpdateIteratorModified;
    /** Upstream {@code WidgetList::iterator mUpdateIterator} (index into {@link #mWidgets}). */
    public int mUpdateIterator;
    public int mLastWMUpdateCount;
    public int mUpdateCnt;
    public boolean mDirty;

    public int mX;
    public int mY;
    public int mWidth;
    public int mHeight;

    public boolean mHasAlpha;
    public boolean mClip;
    public FlagsMod mWidgetFlagsMod;
    public int mPriority;
    public int mZOrder;

    // WidgetContainer::WidgetContainer (WidgetContainer.cpp:30)
    public WidgetContainer() {
        mWidgetManager = null;
        mParent = null;
        mUpdateIteratorModified = false;
        mUpdateIterator = 0;
        mLastWMUpdateCount = 0;
        mUpdateCnt = 0;
        mDirty = false;
        mX = 0;
        mY = 0;
        mWidth = 0;
        mHeight = 0;
        mHasAlpha = false;
        mClip = true;
        mWidgetFlagsMod = new FlagsMod();
        mPriority = 0;
        mZOrder = 0;
    }

    // WidgetContainer::GetRect (WidgetContainer.cpp:53)
    public Rect getRect() {
        return new Rect(mX, mY, mWidth, mHeight);
    }

    // WidgetContainer::Intersects (WidgetContainer.cpp:58)
    public boolean intersects(WidgetContainer theWidget) {
        return getRect().intersects(theWidget.getRect());
    }

    // WidgetContainer::AddWidget (WidgetContainer.cpp:84)
    public void addWidget(Widget theWidget) {
        if (!mWidgets.contains(theWidget)) {
            insertWidgetHelper(mWidgets.size(), theWidget);
            theWidget.mWidgetManager = mWidgetManager;
            theWidget.mParent = this;

            if (mWidgetManager != null) {
                theWidget.addedToManager(mWidgetManager);
                theWidget.markDirtyFull();
                mWidgetManager.rehupMouse();
            }

            markDirty();
        }
    }

    // WidgetContainer::HasWidget (WidgetContainer.cpp:104)
    public boolean hasWidget(Widget theWidget) {
        return mWidgets.contains(theWidget);
    }

    // WidgetContainer::RemoveWidget (WidgetContainer.cpp:109)
    public void removeWidget(Widget theWidget) {
        int anIdx = mWidgets.indexOf(theWidget);
        if (anIdx >= 0) {
            theWidget.widgetRemovedHelper();
            theWidget.mParent = null;

            boolean erasedCur = (anIdx == mUpdateIterator);
            mWidgets.remove(anIdx);
            if (erasedCur) {
                // upstream sets mUpdateIterator to the element following the erased one
                mUpdateIterator = anIdx;
                mUpdateIteratorModified = true;
            }
        }
    }

    // WidgetContainer::GetWidgetAtHelper (WidgetContainer.cpp:132)
    public Widget getWidgetAtHelper(int x, int y, int theFlags, boolean[] found, int[] theWidgetX,
            int[] theWidgetY) {
        boolean belowModal = false;

        int aBaseFlags = FlagsMod.getModFlags(theFlags, mWidgetFlagsMod);

        for (int anIdx = mWidgets.size() - 1; anIdx >= 0; anIdx--) {
            Widget aWidget = mWidgets.get(anIdx);

            int aCurFlags = aBaseFlags;
            aCurFlags = FlagsMod.getModFlags(aCurFlags, aWidget.mWidgetFlagsMod);
            if (belowModal && (mWidgetManager != null)) {
                aCurFlags = FlagsMod.getModFlags(aCurFlags, mWidgetManager.mBelowModalFlagsMod);
            }

            if ((aCurFlags & WidgetManager.WIDGETFLAGS_ALLOW_MOUSE) != 0) {
                if (aWidget.mVisible) {
                    boolean[] childFound = new boolean[1];
                    Widget aCheckWidget = aWidget.getWidgetAtHelper(x - aWidget.mX, y - aWidget.mY,
                            aCurFlags, childFound, theWidgetX, theWidgetY);
                    if ((aCheckWidget != null) || childFound[0]) {
                        found[0] = true;
                        return aCheckWidget;
                    }

                    if (aWidget.mMouseVisible && aWidget.getInsetRect().contains(x, y)) {
                        found[0] = true;

                        if (aWidget.isPointVisible(x - aWidget.mX, y - aWidget.mY)) {
                            if (theWidgetX != null) {
                                theWidgetX[0] = x - aWidget.mX;
                            }
                            if (theWidgetY != null) {
                                theWidgetY[0] = y - aWidget.mY;
                            }
                            return aWidget;
                        }
                    }
                }
            }

            if ((mWidgetManager != null) && (aWidget == mWidgetManager.mBaseModalWidget)) {
                belowModal = true;
            }
        }

        found[0] = false;
        return null;
    }

    // WidgetContainer::IsBelowHelper (WidgetContainer.cpp:178)
    public boolean isBelowHelper(Widget theWidget1, Widget theWidget2, boolean[] found) {
        for (Widget aWidget : mWidgets) {
            if (aWidget == theWidget1) {
                found[0] = true;
                return true;
            } else if (aWidget == theWidget2) {
                found[0] = true;
                return false;
            }

            boolean result = aWidget.isBelowHelper(theWidget1, theWidget2, found);
            if (found[0]) {
                return result;
            }
        }

        return false;
    }

    // WidgetContainer::IsBelow (WidgetContainer.cpp:185)
    public boolean isBelow(Widget theWidget1, Widget theWidget2) {
        boolean[] found = new boolean[1];
        return isBelowHelper(theWidget1, theWidget2, found);
    }

    // WidgetContainer::MarkAllDirty (WidgetContainer.cpp:212)
    public void markAllDirty() {
        markDirty();

        for (Widget aWidget : mWidgets) {
            aWidget.mDirty = true;
            aWidget.markAllDirty();
        }
    }

    // WidgetContainer::InsertWidgetHelper (WidgetContainer.cpp:225)
    public void insertWidgetHelper(int where, Widget theWidget) {
        // Search forwards
        int anIdx = where;
        while (anIdx < mWidgets.size()) {
            Widget aWidget = mWidgets.get(anIdx);
            if (aWidget.mZOrder >= theWidget.mZOrder) {
                if (anIdx != 0) {
                    // Upstream re-reads *anItr here (WidgetContainer.cpp:240) rather than the
                    // decremented anItr2, so the comparison is against the same element; kept
                    // bit-compatible on purpose.
                    if (aWidget.mZOrder > theWidget.mZOrder) {
                        break; // need to search backwards
                    }
                }

                mWidgets.add(anIdx, theWidget);
                return;
            }
            anIdx++;
        }

        // Search backwards
        while (anIdx > 0) {
            anIdx--;
            Widget aWidget = mWidgets.get(anIdx);
            if (aWidget.mZOrder <= theWidget.mZOrder) {
                mWidgets.add(anIdx + 1, theWidget);
                return;
            }
        }

        // It goes at the beginning
        mWidgets.add(0, theWidget);
    }

    // WidgetContainer::BringToFront (WidgetContainer.cpp:265)
    public void bringToFront(Widget theWidget) {
        int anIdx = mWidgets.indexOf(theWidget);
        if (anIdx >= 0) {
            if (anIdx == mUpdateIterator) {
                mUpdateIterator++;
                mUpdateIteratorModified = true;
            }

            mWidgets.remove(anIdx);
            insertWidgetHelper(mWidgets.size(), theWidget);

            theWidget.orderInManagerChanged();
        }
    }

    // WidgetContainer::BringToBack (WidgetContainer.cpp:282)
    public void bringToBack(Widget theWidget) {
        int anIdx = mWidgets.indexOf(theWidget);
        if (anIdx >= 0) {
            if (anIdx == mUpdateIterator) {
                mUpdateIterator++;
                mUpdateIteratorModified = true;
            }

            mWidgets.remove(anIdx);
            insertWidgetHelper(0, theWidget);

            theWidget.orderInManagerChanged();
        }
    }

    // WidgetContainer::PutBehind (WidgetContainer.cpp:299)
    public void putBehind(Widget theWidget, Widget theRefWidget) {
        int anIdx = mWidgets.indexOf(theWidget);
        if (anIdx >= 0) {
            if (anIdx == mUpdateIterator) {
                mUpdateIterator++;
                mUpdateIteratorModified = true;
            }

            mWidgets.remove(anIdx);
            anIdx = mWidgets.indexOf(theRefWidget);
            insertWidgetHelper(anIdx >= 0 ? anIdx : 0, theWidget);

            theWidget.orderInManagerChanged();
        }
    }

    // WidgetContainer::PutInfront (WidgetContainer.cpp:319)
    public void putInfront(Widget theWidget, Widget theRefWidget) {
        int anIdx = mWidgets.indexOf(theWidget);
        if (anIdx >= 0) {
            if (anIdx == mUpdateIterator) {
                mUpdateIterator++;
                mUpdateIteratorModified = true;
            }

            mWidgets.remove(anIdx);
            anIdx = mWidgets.indexOf(theRefWidget);
            if (anIdx >= 0) {
                anIdx++;
            } else {
                anIdx = 0;
            }
            insertWidgetHelper(anIdx, theWidget);

            theWidget.orderInManagerChanged();
        }
    }

    // WidgetContainer::GetAbsPos (WidgetContainer.cpp:341)
    public Point getAbsPos() {
        if (mParent == null) {
            return new Point(mX, mY);
        }
        return new Point(mX + mParent.getAbsPos().mX, mY + mParent.getAbsPos().mY);
    }

    // WidgetContainer::AddedToManager (WidgetContainer.cpp:351)
    public void addedToManager(WidgetManager theWidgetManager) {
        for (Widget theWidget : mWidgets) {
            theWidget.mWidgetManager = theWidgetManager;
            theWidget.addedToManager(theWidgetManager);

            markDirty();
        }
    }

    // WidgetContainer::RemovedFromManager (WidgetContainer.cpp:366)
    public void removedFromManager(WidgetManager theWidgetManager) {
        for (Widget aWidget : mWidgets) {
            aWidget.removedFromManager(theWidgetManager);
            aWidget.mWidgetManager = null;
        }

        if (theWidgetManager.mPopupCommandWidget == this) {
            theWidgetManager.mPopupCommandWidget = null;
        }
    }

    // WidgetContainer::MarkDirty (WidgetContainer.cpp:379)
    public void markDirty() {
        if (mParent != null) {
            mParent.markDirty(this);
        } else {
            mDirty = true;
        }
    }

    // WidgetContainer::MarkDirtyFull (WidgetContainer.cpp:387)
    public void markDirtyFull() {
        if (mParent != null) {
            mParent.markDirtyFull(this);
        } else {
            mDirty = true;
        }
    }

    // WidgetContainer::MarkDirtyFull(WidgetContainer*) (WidgetContainer.cpp:395)
    public void markDirtyFull(WidgetContainer theWidget) {
        // Mark all things dirty that are under or over this widget

        // Mark ourselves dirty
        markDirtyFull();

        theWidget.mDirty = true;

        // Top-level windows are treated differently, as marking a child dirty always
        //  causes a parent redraw which always causes all children to redraw
        if (mParent != null) {
            return;
        }

        int aFoundIdx = mWidgets.indexOf(theWidget);
        if (aFoundIdx < 0) {
            return;
        }

        int anIdx = aFoundIdx;
        if (anIdx != 0) {
            anIdx--;

            for (;;) {
                Widget aWidget = mWidgets.get(anIdx);

                if (aWidget.mVisible) {
                    if (!aWidget.mHasTransparencies && !aWidget.mHasAlpha) {
                        // Clip the widget's bounds to the screen and check if it fully overlapped
                        // by this non-transparent widget underneath it; if it is fully overlapped
                        // then we can stop marking dirty underneath it since it's not transparent.
                        Rect aRect = new Rect(theWidget.mX, theWidget.mY, theWidget.mWidth,
                                theWidget.mHeight).intersection(new Rect(0, 0, mWidth, mHeight));
                        if (aWidget.contains(aRect.mX, aRect.mY)
                                && aWidget.contains(aRect.mX + aRect.mWidth - 1,
                                        aRect.mY + aRect.mHeight - 1)) {
                            aWidget.markDirty();
                            break;
                        }
                    }

                    if (aWidget.intersects(theWidget)) {
                        markDirty(aWidget);
                    }
                }

                if (anIdx == 0) {
                    break;
                }

                anIdx--;
            }
        }

        anIdx = aFoundIdx;
        while (anIdx < mWidgets.size()) {
            Widget aWidget = mWidgets.get(anIdx);
            if (aWidget.mVisible && aWidget.intersects(theWidget)) {
                markDirty(aWidget);
            }

            anIdx++;
        }
    }

    // WidgetContainer::MarkDirty(WidgetContainer*) (WidgetContainer.cpp:461)
    public void markDirty(WidgetContainer theWidget) {
        if (theWidget.mDirty) {
            return;
        }

        // Only mark things dirty that are on top of this widget
        // Mark ourselves dirty
        markDirty();

        theWidget.mDirty = true;

        // Top-level windows are treated differently, as marking a child dirty always
        //  causes a parent redraw which always causes all children to redraw
        if (mParent != null) {
            return;
        }

        if (theWidget.mHasAlpha) {
            markDirtyFull(theWidget);
        } else {
            boolean found = false;
            for (Widget aWidget : mWidgets) {
                if (aWidget == theWidget) {
                    found = true;
                } else if (found) {
                    if (aWidget.mVisible && aWidget.intersects(theWidget)) {
                        markDirty(aWidget);
                    }
                }
            }
        }
    }

    // WidgetContainer::Update (WidgetContainer.cpp:502)
    public void update() {
        mUpdateCnt++;
    }

    // WidgetContainer::UpdateAll (WidgetContainer.cpp:504)
    public void updateAll(ModalFlags theFlags) {
        try (AutoModalFlags anAutoModalFlags = new AutoModalFlags(theFlags, mWidgetFlagsMod)) {
            if ((theFlags.getFlags() & WidgetManager.WIDGETFLAGS_MARK_DIRTY) != 0) {
                markDirty();
            }

            // Can update?
            WidgetManager aWidgetManager = mWidgetManager;
            if (aWidgetManager == null) {
                return;
            }

            if ((theFlags.getFlags() & WidgetManager.WIDGETFLAGS_UPDATE) != 0) {
                if (mLastWMUpdateCount != aWidgetManager.mUpdateCnt) {
                    mLastWMUpdateCount = aWidgetManager.mUpdateCnt;
                    update();
                }
            }

            mUpdateIterator = 0;

            while (mUpdateIterator < mWidgets.size()) {
                mUpdateIteratorModified = false;

                Widget aWidget = mWidgets.get(mUpdateIterator);
                if (aWidget == aWidgetManager.mBaseModalWidget) {
                    theFlags.mIsOver = true;
                }

                aWidget.updateAll(theFlags);

                if (!mUpdateIteratorModified) {
                    mUpdateIterator++;
                }
            }

            mUpdateIteratorModified = true; // prevent incrementing off the end of the list
        }
    }

    // WidgetContainer::UpdateFAll (WidgetContainer.cpp:544)
    public void updateFAll(ModalFlags theFlags, float theFrac) {
        try (AutoModalFlags anAutoModalFlags = new AutoModalFlags(theFlags, mWidgetFlagsMod)) {
            mUpdateIterator = 0;
            while (mUpdateIterator < mWidgets.size()) {
                mUpdateIteratorModified = false;

                Widget aWidget = mWidgets.get(mUpdateIterator);
                if (aWidget == mWidgetManager.mBaseModalWidget) {
                    theFlags.mIsOver = true;
                }

                aWidget.updateFAll(theFlags, theFrac);

                if (!mUpdateIteratorModified) {
                    mUpdateIterator++;
                }
            }

            mUpdateIteratorModified = true; // prevent incrementing off the end of the list
        }
    }

    // WidgetContainer::Draw (WidgetContainer.cpp:590)
    public void draw(Graphics g) {
    }

    // WidgetContainer::DrawAll (WidgetContainer.cpp:592)
    public void drawAll(ModalFlags theFlags, Graphics g) {
        if (mPriority > mWidgetManager.mMinDeferredOverlayPriority) {
            mWidgetManager.flushDeferredOverlayWidgets(mPriority);
        }

        try (AutoModalFlags anAutoModalFlags = new AutoModalFlags(theFlags, mWidgetFlagsMod)) {
            if (mClip && ((theFlags.getFlags() & WidgetManager.WIDGETFLAGS_CLIP) != 0)) {
                g.clipRect(0, 0, mWidth, mHeight);
            }

            if (mWidgets.isEmpty()) {
                if ((theFlags.getFlags() & WidgetManager.WIDGETFLAGS_DRAW) != 0) {
                    draw(g);
                }
                return;
            }

            if ((theFlags.getFlags() & WidgetManager.WIDGETFLAGS_DRAW) != 0) {
                g.pushState();
                draw(g);
                g.popState();
            }

            for (int anIdx = 0; anIdx < mWidgets.size(); anIdx++) {
                Widget aWidget = mWidgets.get(anIdx);

                if (aWidget.mVisible) {
                    if (aWidget == mWidgetManager.mBaseModalWidget) {
                        theFlags.mIsOver = true;
                    }

                    Graphics aClipG = new Graphics(g);
                    aClipG.translate(aWidget.mX, aWidget.mY);
                    aWidget.drawAll(theFlags, aClipG);
                    aWidget.mDirty = false;
                }
            }
        }
    }

    // WidgetContainer::SysColorChanged (WidgetContainer.cpp:625)
    public void sysColorChanged() {
    }

    // WidgetContainer::SysColorChangedAll (WidgetContainer.cpp:629)
    public void sysColorChangedAll() {
        sysColorChanged();

        for (Widget aWidget : mWidgets) {
            aWidget.sysColorChangedAll();
        }
    }

    // WidgetContainer::DisableWidget / SetFocus (WidgetContainer.cpp:641, :642) — base no-ops.
    public void disableWidget(Widget theWidget) {
    }

    public void setFocus(Widget theWidget) {
    }

    // WidgetContainer::RemoveAllWidgets (WidgetContainer.cpp:42)
    public void removeAllWidgets(boolean doDelete, boolean recursive) {
        while (!mWidgets.isEmpty()) {
            Widget aWidget = mWidgets.get(0);
            removeWidget(aWidget);
            if (recursive) {
                aWidget.removeAllWidgets(doDelete, recursive);
            }

            if (doDelete) {
                aWidget.dispose();
            }
        }
    }
}
