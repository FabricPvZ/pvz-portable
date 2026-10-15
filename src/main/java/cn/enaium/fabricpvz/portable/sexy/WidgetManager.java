package cn.enaium.fabricpvz.portable.sexy;

import java.util.ArrayList;
import java.util.List;

import cn.enaium.fabricpvz.portable.sexy.graphics.GLImage;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Ported from src/SexyAppFramework/widget/WidgetManager.h:64 (implementation
 * src/SexyAppFramework/widget/WidgetManager.cpp:35).
 *
 * <p>The top-level widget container: owns the screen image, focus, the sticky mouse capture and the
 * modal-flag traversal. Public fields keep the upstream names.
 *
 * <p>Constructor policy: the constructor only initialises fields (normative port rule); the screen
 * image is created by {@code SexyAppBase.init()} through {@link #resize(Rect, Rect)}.
 */
public class WidgetManager extends WidgetContainer {
    // WidgetManager.h:41-49
    public static final int WIDGETFLAGS_UPDATE = 1;
    public static final int WIDGETFLAGS_MARK_DIRTY = 2;
    public static final int WIDGETFLAGS_DRAW = 4;
    public static final int WIDGETFLAGS_CLIP = 8;
    public static final int WIDGETFLAGS_ALLOW_MOUSE = 16;
    public static final int WIDGETFLAGS_ALLOW_FOCUS = 32;

    // WidgetManager.h:51 (class PreModalInfo)
    public static class PreModalInfo {
        public Widget mBaseModalWidget;
        public Widget mPrevBaseModalWidget;
        public Widget mPrevFocusWidget;
        public FlagsMod mPrevBelowModalFlagsMod;
    }

    /** Upstream {@code DeferredOverlayVector = std::vector<std::pair<Widget*, int>>}. */
    public static class DeferredOverlay {
        public Widget mWidget;
        public int mPriority;

        public DeferredOverlay(Widget theWidget, int thePriority) {
            mWidget = theWidget;
            mPriority = thePriority;
        }
    }

    public Widget mDefaultTab;

    public Graphics mCurG;
    public SexyAppBase mApp;
    public Image mImage;
    public MemoryImage mTransientImage;
    public boolean mLastHadTransients;
    public Widget mPopupCommandWidget;
    public final List<DeferredOverlay> mDeferredOverlayWidgets = new ArrayList<>();
    public int mMinDeferredOverlayPriority;

    public boolean mHasFocus;
    public Widget mFocusWidget;
    public Widget mLastDownWidget;
    public Widget mOverWidget;
    public Widget mBaseModalWidget;
    public FlagsMod mLostFocusFlagsMod;
    public FlagsMod mBelowModalFlagsMod;
    public FlagsMod mDefaultBelowModalFlagsMod;
    public final List<PreModalInfo> mPreModalInfoList = new ArrayList<>();
    public Rect mMouseDestRect;
    public Rect mMouseSourceRect;
    public boolean mMouseIn;
    public int mLastMouseX;
    public int mLastMouseY;
    public int mDownButtons;
    public int mActualDownButtons;
    public final boolean[] mKeyDown = new boolean[0xFF];
    public int mLastDownButtonId;

    public int mWidgetFlags;

    // WidgetManager::WidgetManager (WidgetManager.cpp:37)
    public WidgetManager(SexyAppBase theApp) {
        mApp = theApp;

        mMinDeferredOverlayPriority = 0x7FFFFFFF;
        mWidgetManager = this;
        mMouseIn = false;
        mDefaultTab = null;
        mImage = null;
        mTransientImage = null;
        mCurG = null;
        mLastHadTransients = false;
        mPopupCommandWidget = null;
        mFocusWidget = null;
        mLastDownWidget = null;
        mOverWidget = null;
        mBaseModalWidget = null;
        mLostFocusFlagsMod = new FlagsMod();
        mBelowModalFlagsMod = new FlagsMod();
        mDefaultBelowModalFlagsMod = new FlagsMod();
        mDefaultBelowModalFlagsMod.mRemoveFlags = WIDGETFLAGS_ALLOW_MOUSE | WIDGETFLAGS_ALLOW_FOCUS;
        mMouseDestRect = new Rect();
        mMouseSourceRect = new Rect();
        mWidth = 0;
        mHeight = 0;
        mHasFocus = true;
        mUpdateCnt = 0;
        mLastMouseX = 0;
        mLastMouseY = 0;
        mLastDownButtonId = 0;
        mDownButtons = 0;
        mActualDownButtons = 0;
        mWidgetFlags = WIDGETFLAGS_UPDATE | WIDGETFLAGS_DRAW | WIDGETFLAGS_CLIP
                | WIDGETFLAGS_ALLOW_MOUSE | WIDGETFLAGS_ALLOW_FOCUS;

        for (int i = 0; i < 0xFF; i++) {
            mKeyDown[i] = false;
        }
    }

    // WidgetManager::~WidgetManager (WidgetManager.cpp:66)
    public void dispose() {
        freeResources();
    }

    // WidgetManager::FreeResources (WidgetManager.cpp:71)
    public void freeResources() {
    }

    // WidgetManager::DisableWidget (WidgetManager.cpp:77)
    @Override
    public void disableWidget(Widget theWidget) {
        if (mOverWidget == theWidget) {
            Widget aOverWidget = mOverWidget;
            mOverWidget = null;
            mouseLeave(aOverWidget);
        }

        if (mLastDownWidget == theWidget) {
            Widget aLastDownWidget = mLastDownWidget;
            mLastDownWidget = null;
            doMouseUps(aLastDownWidget, mDownButtons);
            mDownButtons = 0;
        }

        if (mFocusWidget == theWidget) {
            Widget aFocusWidget = mFocusWidget;
            mFocusWidget = null;
            aFocusWidget.lostFocus();
        }

        if (mBaseModalWidget == theWidget) {
            mBaseModalWidget = null;
        }
    }

    // WidgetManager::GetWidgetFlags (WidgetManager.cpp:105)
    protected int getWidgetFlags() {
        return mHasFocus ? mWidgetFlags : FlagsMod.getModFlags(mWidgetFlags, mLostFocusFlagsMod);
    }

    // WidgetManager::GetAnyWidgetAt (WidgetManager.cpp:110)
    public Widget getAnyWidgetAt(int x, int y, int[] theWidgetX, int[] theWidgetY) {
        boolean[] found = new boolean[1];
        return getWidgetAtHelper(x, y, getWidgetFlags(), found, theWidgetX, theWidgetY);
    }

    // WidgetManager::GetWidgetAt (WidgetManager.cpp:116)
    public Widget getWidgetAt(int x, int y, int[] theWidgetX, int[] theWidgetY) {
        Widget aWidget = getAnyWidgetAt(x, y, theWidgetX, theWidgetY);
        if ((aWidget != null) && aWidget.mDisabled) {
            aWidget = null;
        }
        return aWidget;
    }

    // WidgetManager::IsLeftButtonDown (WidgetManager.cpp:124)
    public boolean isLeftButtonDown() {
        return (mActualDownButtons & 1) != 0;
    }

    // WidgetManager::IsMiddleButtonDown (WidgetManager.cpp:129)
    public boolean isMiddleButtonDown() {
        return (mActualDownButtons & 4) != 0;
    }

    // WidgetManager::IsRightButtonDown (WidgetManager.cpp:134)
    public boolean isRightButtonDown() {
        return (mActualDownButtons & 2) != 0;
    }

    // WidgetManager::DoMouseUps (WidgetManager.cpp:139)
    public void doMouseUps() {
        if ((mLastDownWidget != null) && (mDownButtons != 0)) {
            doMouseUps(mLastDownWidget, mDownButtons);
            mDownButtons = 0;
            mLastDownWidget = null;
        }
    }

    // WidgetManager::DeferOverlay (WidgetManager.cpp:149)
    public void deferOverlay(Widget theWidget, int thePriority) {
        mDeferredOverlayWidgets.add(new DeferredOverlay(theWidget, thePriority));
        mMinDeferredOverlayPriority = Math.min(mMinDeferredOverlayPriority, thePriority);
    }

    // WidgetManager::FlushDeferredOverlayWidgets (WidgetManager.cpp:155)
    public void flushDeferredOverlayWidgets(int theMaxPriority) {
        for (;;) {
            int aNextMinPriority = 0x7FFFFFFF;

            for (DeferredOverlay anOverlay : mDeferredOverlayWidgets) {
                Widget aWidget = anOverlay.mWidget;
                if (aWidget != null) {
                    int aPriority = anOverlay.mPriority;

                    if (aPriority == mMinDeferredOverlayPriority) {
                        // Overlays don't get clipped
                        Graphics g = new Graphics(mCurG);
                        g.translate(-mMouseDestRect.mX, -mMouseDestRect.mY);
                        g.translate(aWidget.mX, aWidget.mY);
                        g.setFastStretch(!g.is3D());
                        g.setLinearBlend(g.is3D());

                        aWidget.drawOverlay(g, aPriority);
                        anOverlay.mWidget = null;
                    } else {
                        aNextMinPriority = Math.min(aNextMinPriority, aPriority);
                    }
                }
            }

            mMinDeferredOverlayPriority = aNextMinPriority;
            if (aNextMinPriority == 0x7FFFFFFF) {
                // No more widgets lined up for overlays, clear our vector
                mDeferredOverlayWidgets.clear();
                break;
            }

            // Lowest overlay priority is higher or equal to our current widget,
            //  so continue deferring
            if (aNextMinPriority >= theMaxPriority) {
                break;
            }
        }
    }

    // WidgetManager::DoMouseUps (WidgetManager.cpp:202)
    public void doMouseUps(Widget theWidget, int theDownCode) {
        int[] aClickCountTable = {1, -1, 3};
        for (int i = 0; i < 3; i++) {
            if ((theDownCode & (1 << i)) != 0) {
                theWidget.mIsDown = false;
                theWidget.mouseUp(mLastMouseX - theWidget.mX, mLastMouseY - theWidget.mY,
                        aClickCountTable[i]);
            }
        }
    }

    // WidgetManager::RemapMouse (WidgetManager.cpp:215)
    public void remapMouse(int[] theX, int[] theY) {
        theX[0] = (theX[0] - mMouseSourceRect.mX) * mMouseDestRect.mWidth / mMouseSourceRect.mWidth
                + mMouseDestRect.mX;
        theY[0] = (theY[0] - mMouseSourceRect.mY) * mMouseDestRect.mHeight / mMouseSourceRect.mHeight
                + mMouseDestRect.mY;
    }

    // WidgetManager::MouseEnter (WidgetManager.cpp:221)
    protected void mouseEnter(Widget theWidget) {
        theWidget.mIsOver = true;

        theWidget.mouseEnter();
        if (theWidget.mDoFinger) {
            theWidget.showFinger(true);
        }
    }

    // WidgetManager::MouseLeave (WidgetManager.cpp:230)
    protected void mouseLeave(Widget theWidget) {
        theWidget.mIsOver = false;

        theWidget.mouseLeave();
        if (theWidget.mDoFinger) {
            theWidget.showFinger(false);
        }
    }

    // WidgetManager::SetBaseModal (WidgetManager.cpp:239)
    protected void setBaseModal(Widget theWidget, FlagsMod theBelowFlagsMod) {
        mBaseModalWidget = theWidget;
        mBelowModalFlagsMod = theBelowFlagsMod;

        if ((mOverWidget != null) && ((mBelowModalFlagsMod.mRemoveFlags & WIDGETFLAGS_ALLOW_MOUSE) != 0)
                && isBelow(mOverWidget, mBaseModalWidget)) {
            Widget aWidget = mOverWidget;
            mOverWidget = null;
            mouseLeave(aWidget);
        }

        if ((mLastDownWidget != null)
                && ((mBelowModalFlagsMod.mRemoveFlags & WIDGETFLAGS_ALLOW_MOUSE) != 0)
                && isBelow(mLastDownWidget, mBaseModalWidget)) {
            Widget aWidget = mLastDownWidget;
            int aDownButtons = mDownButtons;
            mDownButtons = 0;
            mLastDownWidget = null;
            doMouseUps(aWidget, aDownButtons);
        }

        if ((mFocusWidget != null)
                && ((mBelowModalFlagsMod.mRemoveFlags & WIDGETFLAGS_ALLOW_FOCUS) != 0)
                && isBelow(mFocusWidget, mBaseModalWidget)) {
            Widget aWidget = mFocusWidget;
            mFocusWidget = null;
            aWidget.lostFocus();
        }
    }

    // WidgetManager::AddBaseModal (WidgetManager.cpp:271)
    public void addBaseModal(Widget theWidget, FlagsMod theBelowFlagsMod) {
        PreModalInfo aPreModalInfo = new PreModalInfo();
        aPreModalInfo.mBaseModalWidget = theWidget;
        aPreModalInfo.mPrevBaseModalWidget = mBaseModalWidget;
        aPreModalInfo.mPrevFocusWidget = mFocusWidget;
        aPreModalInfo.mPrevBelowModalFlagsMod = mBelowModalFlagsMod;
        mPreModalInfoList.add(aPreModalInfo);

        setBaseModal(theWidget, theBelowFlagsMod);
    }

    // WidgetManager::AddBaseModal (WidgetManager.cpp:284)
    public void addBaseModal(Widget theWidget) {
        addBaseModal(theWidget, mDefaultBelowModalFlagsMod);
    }

    // WidgetManager::RemoveBaseModal (WidgetManager.cpp:288)
    public void removeBaseModal(Widget theWidget) {
        boolean first = true;

        while (!mPreModalInfoList.isEmpty()) {
            PreModalInfo aPreModalInfo = mPreModalInfoList.get(mPreModalInfoList.size() - 1);

            if (first && (aPreModalInfo.mBaseModalWidget != theWidget)) {
                // We don't remove it yet, because we want to restore
                //  its keyboard focused widget and crap later
                return;
            }

            // If we removed a widget's self from pre-modal info before
            //  then that means the dialog got removed out-of-order but we
            //  deferred setting the state back until now
            boolean done = (aPreModalInfo.mPrevBaseModalWidget != null)
                    || (mPreModalInfoList.size() == 1);

            setBaseModal(aPreModalInfo.mPrevBaseModalWidget, aPreModalInfo.mPrevBelowModalFlagsMod);

            if (mFocusWidget == null) {
                mFocusWidget = aPreModalInfo.mPrevFocusWidget;
                if (mFocusWidget != null) {
                    mFocusWidget.gotFocus();
                }
            }

            mPreModalInfoList.remove(mPreModalInfoList.size() - 1);

            if (done) {
                break;
            }

            first = false;
        }
    }

    // WidgetManager::Resize (WidgetManager.cpp:330)
    public void resize(Rect theMouseDestRect, Rect theMouseSourceRect) {
        mWidth = theMouseDestRect.mWidth + 2 * theMouseDestRect.mX;
        mHeight = theMouseDestRect.mHeight + 2 * theMouseDestRect.mY;
        mMouseDestRect = theMouseDestRect;
        mMouseSourceRect = theMouseSourceRect;
    }

    // WidgetManager::SetFocus (WidgetManager.cpp:338)
    @Override
    public void setFocus(Widget aWidget) {
        if (aWidget == mFocusWidget) {
            return;
        }

        if (mFocusWidget != null) {
            mFocusWidget.lostFocus();
        }

        if ((aWidget != null) && (aWidget.mWidgetManager == this)) {
            mFocusWidget = aWidget;

            if (mHasFocus && (mFocusWidget != null)) {
                mFocusWidget.gotFocus();
            }
        } else {
            mFocusWidget = null;
        }
    }

    // WidgetManager::GotFocus (WidgetManager.cpp:357)
    public void gotFocus() {
        if (!mHasFocus) {
            mHasFocus = true;

            if (mFocusWidget != null) {
                mFocusWidget.gotFocus();
            }
        }
    }

    // WidgetManager::LostFocus (WidgetManager.cpp:368)
    public void lostFocus() {
        if (mHasFocus) {
            mActualDownButtons = 0;
            for (int aKeyNum = 0; aKeyNum < 0xFF; aKeyNum++) {
                if (mKeyDown[aKeyNum]) {
                    keyUp(aKeyNum);
                }
            }

            mHasFocus = false;

            if (mFocusWidget != null) {
                mFocusWidget.lostFocus();
            }
        }
    }

    // WidgetManager::InitModalFlags (WidgetManager.cpp:386)
    public void initModalFlags(ModalFlags theModalFlags) {
        theModalFlags.mIsOver = mBaseModalWidget == null;
        theModalFlags.mOverFlags = getWidgetFlags();
        theModalFlags.mUnderFlags = FlagsMod.getModFlags(theModalFlags.mOverFlags,
                mBelowModalFlagsMod);
    }

    // WidgetManager::DrawWidgetsTo (WidgetManager.cpp:395)
    public void drawWidgetsTo(Graphics g) {
        mCurG = g;

        ModalFlags aModalFlags = new ModalFlags();
        initModalFlags(aModalFlags);

        for (Widget aWidget : mWidgets) {
            if (aWidget.mVisible) {
                Graphics aClipG = new Graphics(g);
                aClipG.setFastStretch(true);
                aClipG.translate(aWidget.mX, aWidget.mY);
                aWidget.drawAll(aModalFlags, aClipG);
            }
        }

        mCurG = null;
    }

    // WidgetManager::DrawScreen (WidgetManager.cpp:419)
    public boolean drawScreen() {
        return drawScreen(new Graphics(mImage));
    }

    /**
     * Ported from src/SexyAppFramework/widget/WidgetManager.cpp:419.
     *
     * <p>Same body as the upstream no-argument {@code DrawScreen()}, but with the target graphics
     * passed in so callers/tests can supply the screen target explicitly (upstream always builds
     * {@code Graphics aScrG(mImage)}).
     */
    public boolean drawScreen(Graphics aScrG) {
        ModalFlags aModalFlags = new ModalFlags();
        initModalFlags(aModalFlags);

        boolean drewStuff = false;

        int aDirtyCount = 0;

        // Survey
        for (Widget aWidget : mWidgets) {
            if (aWidget.mDirty) {
                aDirtyCount++;
            }
        }

        mMinDeferredOverlayPriority = 0x7FFFFFFF;
        mDeferredOverlayWidgets.clear();

        mCurG = aScrG;

        if (aDirtyCount > 0) {
            Graphics g = new Graphics(aScrG);
            g.translate(-mMouseDestRect.mX, -mMouseDestRect.mY);
            boolean is3D = mApp.is3DAccelerated();

            for (Widget aWidget : mWidgets) {
                if (aWidget == mWidgetManager.mBaseModalWidget) {
                    aModalFlags.mIsOver = true;
                }

                if (aWidget.mDirty && aWidget.mVisible) {
                    Graphics aClipG = new Graphics(g);
                    aClipG.setFastStretch(!is3D);
                    aClipG.setLinearBlend(is3D);
                    aClipG.translate(aWidget.mX, aWidget.mY);
                    aWidget.drawAll(aModalFlags, aClipG);

                    aDirtyCount++;
                    drewStuff = true;
                    aWidget.mDirty = false;
                }
            }
        }

        flushDeferredOverlayWidgets(0x7FFFFFFF);

        mCurG = null;

        return drewStuff;
    }

    // WidgetManager::UpdateFrame (WidgetManager.cpp:484)
    public boolean updateFrame() {
        ModalFlags aModalFlags = new ModalFlags();
        initModalFlags(aModalFlags);

        // Keep us from having mLastWMUpdateCount interfere with our own updating
        mUpdateCnt++;
        mLastWMUpdateCount = mUpdateCnt;
        updateAll(aModalFlags);

        return mDirty;
    }

    // WidgetManager::UpdateFrameF (WidgetManager.cpp:499)
    public boolean updateFrameF(float theFrac) {
        ModalFlags aModalFlags = new ModalFlags();
        initModalFlags(aModalFlags);

        updateFAll(aModalFlags, theFrac);
        return mDirty;
    }

    // WidgetManager::SetPopupCommandWidget (WidgetManager.cpp:509)
    public void setPopupCommandWidget(Widget theList) {
        mPopupCommandWidget = theList;
        addWidget(mPopupCommandWidget);
    }

    // WidgetManager::RemovePopupCommandWidget (WidgetManager.cpp:515)
    public void removePopupCommandWidget() {
        if (mPopupCommandWidget != null) {
            Widget aWidget = mPopupCommandWidget;
            mPopupCommandWidget = null;
            removeWidget(aWidget);
        }
    }

    // WidgetManager::MousePosition (WidgetManager.cpp:526)
    public void mousePosition(int x, int y) {
        int aLastMouseX = mLastMouseX;
        int aLastMouseY = mLastMouseY;

        mLastMouseX = x;
        mLastMouseY = y;

        int[] aWidgetX = new int[1];
        int[] aWidgetY = new int[1];
        Widget aWidget = getWidgetAt(x, y, aWidgetX, aWidgetY);

        if (aWidget != mOverWidget) {
            Widget aLastOverWidget = mOverWidget;
            mOverWidget = null;

            if (aLastOverWidget != null) {
                mouseLeave(aLastOverWidget);
            }

            mOverWidget = aWidget;
            if (aWidget != null) {
                mouseEnter(aWidget);
                aWidget.mouseMove(aWidgetX[0], aWidgetY[0]);
            }
        } else if ((aLastMouseX != x) || (aLastMouseY != y)) {
            if (aWidget != null) {
                aWidget.mouseMove(aWidgetX[0], aWidgetY[0]);
            }
        }
    }

    // WidgetManager::RehupMouse (WidgetManager.cpp:561)
    public void rehupMouse() {
        if (mLastDownWidget != null) {
            if (mOverWidget != null) {
                Widget aWidgetOver = getWidgetAt(mLastMouseX, mLastMouseY, null, null);

                if (aWidgetOver != mLastDownWidget) {
                    Widget anOverWidget = mOverWidget;
                    mOverWidget = null;
                    mouseLeave(anOverWidget);
                }
            }
        } else if (mMouseIn) {
            mousePosition(mLastMouseX, mLastMouseY);
        }
    }

    // WidgetManager::MouseUp (WidgetManager.cpp:581)
    public boolean mouseUp(int x, int y, int theClickCount) {
        int aMask;

        if (theClickCount < 0) {
            aMask = 0x02;
        } else if (theClickCount == 3) {
            aMask = 0x04;
        } else {
            aMask = 0x01;
        }

        // Make sure that we thought this button was down anyway - possibly not, if we
        //  disabled the widget already or something
        mActualDownButtons &= ~aMask;
        if ((mLastDownWidget != null) && ((mDownButtons & aMask) != 0)) {
            Widget aLastDownWidget = mLastDownWidget;

            mDownButtons &= ~aMask;
            if (mDownButtons == 0) {
                mLastDownWidget = null;
            }

            aLastDownWidget.mIsDown = false;
            aLastDownWidget.mouseUp(x - aLastDownWidget.mX, y - aLastDownWidget.mY, theClickCount);
        } else {
            mDownButtons &= ~aMask;
        }

        mousePosition(x, y);

        return true;
    }

    // WidgetManager::MouseDown (WidgetManager.cpp:614)
    public boolean mouseDown(int x, int y, int theClickCount) {
        if (theClickCount < 0) {
            mActualDownButtons |= 0x02;
        } else if (theClickCount == 3) {
            mActualDownButtons |= 0x04;
        } else {
            mActualDownButtons |= 0x01;
        }

        mousePosition(x, y);

        if ((mPopupCommandWidget != null) && !mPopupCommandWidget.contains(x, y)) {
            removePopupCommandWidget();
        }

        int[] aWidgetX = new int[1];
        int[] aWidgetY = new int[1];
        Widget aWidget = getWidgetAt(x, y, aWidgetX, aWidgetY);

        // This code passes all button downs to the mLastDownWidget
        if (mLastDownWidget != null) {
            aWidget = mLastDownWidget;
        }

        if (theClickCount < 0) {
            mLastDownButtonId = -1;
            mDownButtons |= 0x02;
        } else if (theClickCount == 3) {
            mLastDownButtonId = 2;
            mDownButtons |= 0x04;
        } else {
            mLastDownButtonId = 1;
            mDownButtons |= 0x01;
        }

        mLastDownWidget = aWidget;
        if (aWidget != null) {
            if (aWidget.wantsFocus()) {
                setFocus(aWidget);
            }

            aWidget.mIsDown = true;
            aWidget.mouseDown(aWidgetX[0], aWidgetY[0], theClickCount);
        }

        return true;
    }

    // WidgetManager::MouseMove (WidgetManager.cpp:665)
    public boolean mouseMove(int x, int y) {
        if (mDownButtons != 0) {
            return mouseDrag(x, y);
        }

        mMouseIn = true;
        mousePosition(x, y);

        return true;
    }

    // WidgetManager::MouseDrag (WidgetManager.cpp:676)
    public boolean mouseDrag(int x, int y) {
        mMouseIn = true;
        mLastMouseX = x;
        mLastMouseY = y;

        if ((mOverWidget != null) && (mOverWidget != mLastDownWidget)) {
            Widget anOverWidget = mOverWidget;
            mOverWidget = null;
            mouseLeave(anOverWidget);
        }

        if (mLastDownWidget != null) {
            Point anAbsPos = mLastDownWidget.getAbsPos();

            int aWidgetX = x - anAbsPos.mX;
            int aWidgetY = y - anAbsPos.mY;
            mLastDownWidget.mouseDrag(aWidgetX, aWidgetY);

            Widget aWidgetOver = getWidgetAt(x, y, null, null);

            if ((aWidgetOver == mLastDownWidget) && (aWidgetOver != null)) {
                if (mOverWidget == null) {
                    mOverWidget = mLastDownWidget;
                    mouseEnter(mOverWidget);
                }
            } else {
                if (mOverWidget != null) {
                    Widget anOverWidget = mOverWidget;
                    mOverWidget = null;
                    mouseLeave(anOverWidget);
                }
            }
        }

        return true;
    }

    // WidgetManager::MouseExit (WidgetManager.cpp:721)
    public boolean mouseExit(int x, int y) {
        mMouseIn = false;

        if (mOverWidget != null) {
            mouseLeave(mOverWidget);
            mOverWidget = null;
        }

        return true;
    }

    // WidgetManager::MouseWheel (WidgetManager.cpp:734)
    public void mouseWheel(int theDelta) {
        if (mFocusWidget != null) {
            mFocusWidget.mouseWheel(theDelta);
        }
    }

    // WidgetManager::KeyChar (WidgetManager.cpp:740)
    public boolean keyChar(char theChar) {
        if (theChar == KeyCode.KEYCODE_TAB) {
            if (mKeyDown[KeyCode.KEYCODE_CONTROL]) {
                if (mDefaultTab != null) {
                    mDefaultTab.keyChar(theChar);
                }

                return true;
            }
        }

        if (mFocusWidget != null) {
            mFocusWidget.keyChar(theChar);
        }

        return true;
    }

    // WidgetManager::KeyText (WidgetManager.cpp:761)
    public boolean keyText(String theText) {
        if (mFocusWidget != null) {
            mFocusWidget.keyText(theText);
        }

        return true;
    }

    // WidgetManager::KeyDown (WidgetManager.cpp:769)
    public boolean keyDown(int key) {
        if ((key >= 0) && (key < 0xFF)) {
            mKeyDown[key] = true;
        }

        if (mFocusWidget != null) {
            mFocusWidget.keyDown(key);
        }

        return true;
    }

    // WidgetManager::KeyUp (WidgetManager.cpp:779)
    public boolean keyUp(int key) {
        if ((key >= 0) && (key < 0xFF)) {
            mKeyDown[key] = false;
        }

        if ((key == KeyCode.KEYCODE_TAB) && mKeyDown[KeyCode.KEYCODE_CONTROL]) {
            return true;
        }

        if (mFocusWidget != null) {
            mFocusWidget.keyUp(key);
        }

        return true;
    }

    /** Convenience alias used by the platform/input layer for {@code DrawScreen}'s screen target. */
    public void ensureScreenImage(int theWidth, int theHeight) {
        if (mImage == null) {
            mImage = GLImage.screen(theWidth, theHeight);
            if (mImage instanceof GLImage aScreenImage) {
                // Widget drawing goes into this image, so it must be a drawable texture target.
                aScreenImage.mRenderTarget = true;
            }
        }
    }
}
