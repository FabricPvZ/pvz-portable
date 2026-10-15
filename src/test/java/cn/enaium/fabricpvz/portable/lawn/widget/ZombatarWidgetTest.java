/*
 * Tests for {@link ZombatarWidget} (ported from src/Lawn/Widget/ZombatarWidget.h:1-158 and
 * ZombatarWidget.cpp:1-1551).
 *
 * Headless, asset-free: {@link HeadlessTestApp} binds stub images/fonts for every resource id and a
 * profile, so the widget can be constructed from a real {@link GameSelector}. The record
 * serialisation (EncodeRecord/DecodeRecord), the draft defaults, the page cap/visibility rules and
 * the save/delete persistence paths are all exercised against the temp app-data directory, so no
 * user save data is touched.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Zombatar;
import cn.enaium.fabricpvz.portable.lawn.Zombatar.ZombatarPage;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class ZombatarWidgetTest {

    /**
     * A {@link LawnApp} whose reanimations look loaded, so {@link GameSelector}'s constructor can
     * index the zombie-hand track instances headlessly (same fixture as {@code GameSelectorTest}).
     */
    static final class LoadedReanimLawnApp extends LawnApp {
        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    @TempDir
    Path mTempDir;

    private String mPrevAppDataFolder;

    @BeforeEach
    void redirectAppData() {
        mPrevAppDataFolder = Common.getAppDataFolder();
        Common.setAppDataFolder(mTempDir.toString());
    }

    @AfterEach
    void restoreAppData() {
        Common.setAppDataFolder(mPrevAppDataFolder);
    }

    /** A widget wired into a real (headless) selector, exactly like GameSelector.cpp:347. */
    private ZombatarWidget newWidget() {
        LoadedReanimLawnApp anApp = new LoadedReanimLawnApp();
        HeadlessTestApp.initialize(anApp);
        GameSelector aSelector = new GameSelector(anApp);
        return aSelector.mZombatarWidget;
    }

    // ZombatarWidget.cpp:120-183 (SlotForPart / SlotForColor) — the record layout oracle.
    private static int slot(ZombatarPage thePage) {
        switch (thePage) {
            case SKIN: return Zombatar.ZOMBATAR_SLOT_SKIN_PART;
            case CLOTHES: return Zombatar.ZOMBATAR_SLOT_CLOTHES;
            case TIDBITS: return Zombatar.ZOMBATAR_SLOT_TIDBITS;
            case ACCESSORY: return Zombatar.ZOMBATAR_SLOT_ACCESSORY;
            case FACIAL_HAIR: return Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR;
            case HAIR: return Zombatar.ZOMBATAR_SLOT_HAIR;
            case EYEWEAR: return Zombatar.ZOMBATAR_SLOT_EYEWEAR;
            case HATS: return Zombatar.ZOMBATAR_SLOT_HATS;
            case BACKDROPS: return Zombatar.ZOMBATAR_SLOT_BACKGROUND;
            default: return Zombatar.ZOMBATAR_SLOT_SKIN_PART;
        }
    }

    private static int colorSlot(ZombatarPage thePage) {
        switch (thePage) {
            case SKIN: return Zombatar.ZOMBATAR_SLOT_SKIN_COLOR;
            case CLOTHES: return Zombatar.ZOMBATAR_SLOT_CLOTHES_COLOR;
            case TIDBITS: return Zombatar.ZOMBATAR_SLOT_TIDBITS_COLOR;
            case ACCESSORY: return Zombatar.ZOMBATAR_SLOT_ACCESSORY_COLOR;
            case FACIAL_HAIR: return Zombatar.ZOMBATAR_SLOT_FACIAL_HAIR_COLOR;
            case HAIR: return Zombatar.ZOMBATAR_SLOT_HAIR_COLOR;
            case EYEWEAR: return Zombatar.ZOMBATAR_SLOT_EYEWEAR_COLOR;
            case HATS: return Zombatar.ZOMBATAR_SLOT_HATS_COLOR;
            case BACKDROPS: return Zombatar.ZOMBATAR_SLOT_BACKGROUND_COLOR;
            default: return Zombatar.ZOMBATAR_SLOT_SKIN_COLOR;
        }
    }

    // ZombatarWidget.cpp:274-336 / :445-457
    @Test
    void constructorStartsInCreateStateWithTheDraftDefaults() {
        ZombatarWidget w = newWidget();

        assertEquals(ZombatarWidget.ZombatarWidgetState.CREATE, w.mState);
        assertEquals(ZombatarPage.SKIN, w.mPage);
        assertEquals(800, w.mX);
        assertEquals(GameConstants.BOARD_WIDTH, w.mWidth);
        assertEquals(GameConstants.BOARD_HEIGHT, w.mHeight);

        assertEquals(0, w.mPart[ZombatarPage.SKIN.ordinal()]);
        assertEquals(0, w.mColor[ZombatarPage.SKIN.ordinal()]);
        assertEquals(4, w.mPart[ZombatarPage.BACKDROPS.ordinal()]);
        assertEquals(47, w.mColor[ZombatarPage.BACKDROPS.ordinal()]);
        for (int i = ZombatarPage.HAIR.ordinal(); i < ZombatarPage.BACKDROPS.ordinal(); i++) {
            assertEquals(-1, w.mPart[i]);
            assertEquals(-1, w.mColor[i]);
        }
    }

    // ZombatarWidget.cpp:525-537 (EncodeRecord)
    @Test
    void encodeRecordWritesSlotsLittleEndianWithSkinPartMinusOne() {
        ZombatarWidget w = newWidget();
        w.resetDraft();
        w.mPart[ZombatarPage.HAIR.ordinal()] = 5;
        w.mColor[ZombatarPage.HAIR.ordinal()] = 15;

        byte[] aRecord = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        w.encodeRecord(aRecord);

        // skin part is always the "none" marker, skin color clamped to 0..11
        assertEquals(-1, Zombatar.readSignedRecordSlot(aRecord, Zombatar.ZOMBATAR_SLOT_SKIN_PART));
        assertEquals(0, Zombatar.readSignedRecordSlot(aRecord, Zombatar.ZOMBATAR_SLOT_SKIN_COLOR));
        assertEquals(5, Zombatar.readSignedRecordSlot(aRecord, slot(ZombatarPage.HAIR)));
        assertEquals(15, Zombatar.readSignedRecordSlot(aRecord, colorSlot(ZombatarPage.HAIR)));
    }

    // ZombatarWidget.cpp:491-523 (DecodeRecord) / :525-537 (EncodeRecord)
    @Test
    void encodeDecodeRoundTripPreservesSelectedPartsAndColors() {
        ZombatarWidget w = newWidget();
        w.resetDraft();
        w.mPart[ZombatarPage.HAIR.ordinal()] = 5;
        w.mColor[ZombatarPage.HAIR.ordinal()] = 15;
        w.mPart[ZombatarPage.TIDBITS.ordinal()] = 2;
        w.mColor[ZombatarPage.TIDBITS.ordinal()] = 31;
        w.mPart[ZombatarPage.CLOTHES.ordinal()] = 3;
        w.mColor[ZombatarPage.CLOTHES.ordinal()] = -1;
        w.mPart[ZombatarPage.BACKDROPS.ordinal()] = 1;
        w.mColor[ZombatarPage.BACKDROPS.ordinal()] = 40;

        byte[] aRecord = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        w.encodeRecord(aRecord);

        int[] aPart = new int[Zombatar.NUM_ZOMBATAR_PAGES];
        int[] aColor = new int[Zombatar.NUM_ZOMBATAR_PAGES];
        w.decodeRecord(aRecord, aPart, aColor);

        assertEquals(0, aPart[ZombatarPage.SKIN.ordinal()]);
        assertEquals(0, aColor[ZombatarPage.SKIN.ordinal()]);
        assertEquals(5, aPart[ZombatarPage.HAIR.ordinal()]);
        assertEquals(15, aColor[ZombatarPage.HAIR.ordinal()]);
        assertEquals(2, aPart[ZombatarPage.TIDBITS.ordinal()]);
        assertEquals(31, aColor[ZombatarPage.TIDBITS.ordinal()]);
        assertEquals(3, aPart[ZombatarPage.CLOTHES.ordinal()]);
        assertEquals(-1, aColor[ZombatarPage.CLOTHES.ordinal()]);
        assertEquals(1, aPart[ZombatarPage.BACKDROPS.ordinal()]);
        assertEquals(40, aColor[ZombatarPage.BACKDROPS.ordinal()]);
    }

    @Test
    void decodeRecordClampsOutOfRangePartsAndColors() {
        ZombatarWidget w = newWidget();
        byte[] aRecord = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        // HAIR has 16 items -> index 99 is out of range and decodes to "none".
        Zombatar.writeRecordSlot(aRecord, slot(ZombatarPage.HAIR), 99);
        Zombatar.writeRecordSlot(aRecord, colorSlot(ZombatarPage.HAIR), 250);
        // BACKDROPS has 5 items -> a part is clamped into [0,4] instead of folding to -1.
        Zombatar.writeRecordSlot(aRecord, slot(ZombatarPage.BACKDROPS), 99);
        Zombatar.writeRecordSlot(aRecord, colorSlot(ZombatarPage.BACKDROPS), 250);

        int[] aPart = new int[Zombatar.NUM_ZOMBATAR_PAGES];
        int[] aColor = new int[Zombatar.NUM_ZOMBATAR_PAGES];
        w.decodeRecord(aRecord, aPart, aColor);

        assertEquals(-1, aPart[ZombatarPage.HAIR.ordinal()]);
        assertEquals(47, aColor[ZombatarPage.HAIR.ordinal()]);
        assertEquals(4, aPart[ZombatarPage.BACKDROPS.ordinal()]);
        assertEquals(47, aColor[ZombatarPage.BACKDROPS.ordinal()]);
    }

    // ZombatarWidget.cpp:552-570 (SaveDraft)
    @Test
    void saveDraftAppendsRecordAndUpdatesProfile() {
        ZombatarWidget w = newWidget();
        PlayerInfo aProfile = w.mApp.mPlayerInfo;
        aProfile.mZombatarData = new byte[0];
        aProfile.mZombatarHeadCount = 0;

        w.resetDraft();
        w.mPart[ZombatarPage.HAIR.ordinal()] = 4;
        w.mColor[ZombatarPage.HAIR.ordinal()] = 13;
        assertTrue(w.saveDraft());

        assertEquals(1, aProfile.mZombatarHeadCount);
        assertEquals(PlayerInfo.ZOMBATAR_RECORD_SIZE, aProfile.mZombatarData.length);
        assertEquals(1, aProfile.mZombatarCreatedBefore);
        assertEquals(0, w.mCurrentIndex);
        assertEquals(4, Zombatar.readSignedRecordSlot(aProfile.mZombatarData,
                slot(ZombatarPage.HAIR)));

        // A second head appends and becomes current.
        w.resetDraft();
        w.mPart[ZombatarPage.HAIR.ordinal()] = 7;
        w.mColor[ZombatarPage.HAIR.ordinal()] = 20;
        assertTrue(w.saveDraft());

        assertEquals(2, aProfile.mZombatarHeadCount);
        assertEquals(2 * PlayerInfo.ZOMBATAR_RECORD_SIZE, aProfile.mZombatarData.length);
        assertEquals(1, w.mCurrentIndex);
    }

    // ZombatarWidget.cpp:572-602 (DeleteCurrent)
    @Test
    void deleteCurrentRemovesTheSelectedRecordAndKeepsTheOthers() {
        ZombatarWidget w = newWidget();
        PlayerInfo aProfile = w.mApp.mPlayerInfo;
        aProfile.mZombatarData = new byte[0];
        aProfile.mZombatarHeadCount = 0;

        w.resetDraft();
        w.mPart[ZombatarPage.HAIR.ordinal()] = 4;
        w.saveDraft();
        w.resetDraft();
        w.mPart[ZombatarPage.HAIR.ordinal()] = 7;
        w.saveDraft();
        assertEquals(1, w.mCurrentIndex);

        w.deleteCurrent();

        assertEquals(1, aProfile.mZombatarHeadCount);
        assertEquals(PlayerInfo.ZOMBATAR_RECORD_SIZE, aProfile.mZombatarData.length);
        // the surviving record is the first one (hair 4), now loaded back into the draft
        assertEquals(4, w.mPart[ZombatarPage.HAIR.ordinal()]);
        assertEquals(ZombatarWidget.ZombatarWidgetState.LIST, w.mState);
    }

    // ZombatarWidget.cpp:461-470 (GetHeadCount / CanSaveNewHead)
    @Test
    void canSaveNewHeadIsFalseAtTheHundredHeadCap() {
        ZombatarWidget w = newWidget();
        PlayerInfo aProfile = w.mApp.mPlayerInfo;

        aProfile.mZombatarData = new byte[99 * PlayerInfo.ZOMBATAR_RECORD_SIZE];
        assertEquals(99, w.getHeadCount());
        assertTrue(w.canSaveNewHead());

        aProfile.mZombatarData = new byte[PlayerInfo.MAX_ZOMBATAR_HEADS * PlayerInfo.ZOMBATAR_RECORD_SIZE];
        assertEquals(100, w.getHeadCount());
        assertFalse(w.canSaveNewHead());
        assertFalse(w.saveDraft());
    }

    // ZombatarWidget.cpp:644-669
    @Test
    void itemCountsAndPageGatesMatchUpstream() {
        ZombatarWidget w = newWidget();

        assertEquals(0, w.getTotalItemsForPage(ZombatarPage.SKIN));
        assertEquals(16, w.getTotalItemsForPage(ZombatarPage.HAIR));
        assertEquals(24, w.getTotalItemsForPage(ZombatarPage.FACIAL_HAIR));
        assertEquals(12, w.getTotalItemsForPage(ZombatarPage.CLOTHES));
        assertEquals(5, w.getTotalItemsForPage(ZombatarPage.BACKDROPS));

        w.mPage = ZombatarPage.SKIN;
        assertFalse(w.pageAllowsNone());
        assertTrue(w.pageAllowsColors());

        w.mPage = ZombatarPage.BACKDROPS;
        assertFalse(w.pageAllowsNone());

        w.mPage = ZombatarPage.HAIR;
        assertTrue(w.pageAllowsNone());
        w.mPart[ZombatarPage.HAIR.ordinal()] = 0; // mode 1 -> colour palette
        assertTrue(w.pageAllowsColors());
        w.mPart[ZombatarPage.HAIR.ordinal()] = 2; // mode NONE
        assertFalse(w.pageAllowsColors());
        w.mPart[ZombatarPage.HAIR.ordinal()] = -1; // nothing chosen
        assertFalse(w.pageAllowsColors());
    }

    @Test
    void subPageItemCountIsPagedBySeventeen() {
        ZombatarWidget w = newWidget();
        w.mPage = ZombatarPage.FACIAL_HAIR; // 24 items
        w.mSubPage = 0;
        assertEquals(17, w.getSubPageItemCount());
        w.mSubPage = 1;
        assertEquals(7, w.getSubPageItemCount());
    }

    // ZombatarWidget.cpp:1247-1279 (UpdateButtonState)
    @Test
    void buttonVisibilityFollowsTheStateAndHeadCount() {
        ZombatarWidget w = newWidget();
        PlayerInfo aProfile = w.mApp.mPlayerInfo;
        aProfile.mZombatarData = new byte[0];

        w.changeState(ZombatarWidget.ZombatarWidgetState.CREATE);
        assertTrue(w.mBackButton.mVisible);
        assertTrue(w.mFinishedButton.mVisible);
        assertFalse(w.mNewButton.mVisible);
        assertFalse(w.mViewButton.mVisible); // no heads yet

        w.resetDraft();
        w.saveDraft();
        w.changeState(ZombatarWidget.ZombatarWidgetState.CREATE);
        assertTrue(w.mViewButton.mVisible); // CREATE with >= 1 head

        w.changeState(ZombatarWidget.ZombatarWidgetState.LIST);
        assertTrue(w.mNewButton.mVisible);
        assertFalse(w.mViewButton.mVisible);
        assertFalse(w.mConfirmBackButton.mVisible);
        assertFalse(w.mPrevPortraitButton.mVisible); // only one head

        w.changeState(ZombatarWidget.ZombatarWidgetState.CONFIRM);
        assertTrue(w.mConfirmBackButton.mVisible);
    }
}
