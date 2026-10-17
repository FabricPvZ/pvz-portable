/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.SeedPacket.
 *
 * Covers the cost/cooldown state machine (SeedPacket.cpp:102-209, 1107-1145), the disabled-packet
 * rules (SeedPacket.cpp:686-724) and the click handling that puts a packet into the cursor
 * (SeedPacket.cpp:726-870), plus hit-testing (SeedPacket.cpp:898-917).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

class SeedPacketTest {

    private static SeedPacket newPacket(StubBoard board, int theSeedType) {
        SeedPacket packet = new SeedPacket();
        packet.mBoard = board;
        packet.mIndex = 4;
        packet.setPacketType(theSeedType, SeedType.SEED_NONE);
        return packet;
    }

    @Test
    void constructorMatchesUpstreamDefaults() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);

        SeedPacket packet = new SeedPacket();

        // SeedPacket.cpp:38-56
        assertEquals(SeedType.SEED_NONE, packet.mPacketType);
        assertEquals(SeedType.SEED_NONE, packet.mImitaterType);
        assertEquals(-1, packet.mIndex);
        assertEquals(0, packet.mOffsetX);
        assertEquals(0, packet.mRefreshCounter);
        assertEquals(0, packet.mRefreshTime);
        assertTrue(packet.mActive);
        assertFalse(packet.mRefreshing);
        assertEquals(0, packet.mTimesUsed);
        assertEquals(0, packet.mSlotMachineCountDown);
        assertEquals(0.0f, packet.mSlotMachiningPosition);
        assertEquals(50, packet.mWidth);
        assertEquals(70, packet.mHeight);
    }

    @Test
    void regularSeedsStartReady() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);

        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);

        // SeedPacket.cpp:1107-1145: non-upgrade seeds with a short refresh start active
        assertTrue(packet.mActive);
        assertFalse(packet.mRefreshing);
        assertEquals(0, packet.mRefreshTime);
    }

    @Test
    void upgradeSeedsStartOnCooldown() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);

        assertTrue(Plant.isUpgrade(SeedType.SEED_GATLINGPEA), "the fixture needs an upgrade seed");
        SeedPacket packet = newPacket(board, SeedType.SEED_GATLINGPEA);

        // SeedPacket.cpp:1136-1141
        assertEquals(3500, packet.mRefreshTime);
        assertTrue(packet.mRefreshing);
        assertFalse(packet.mActive);
    }

    @Test
    void challengeModesSkipTheRefreshScheduling() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_BEGHOULED;
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);

        SeedPacket packet = newPacket(board, SeedType.SEED_GATLINGPEA);

        // SeedPacket.cpp:1121-1125
        assertEquals(0, packet.mRefreshTime);
        assertFalse(packet.mRefreshing);
        assertTrue(packet.mActive);
    }

    @Test
    void refreshingPacketReactivatesAfterItsCooldown() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);
        packet.mActive = false;
        packet.mRefreshing = true;
        packet.mRefreshTime = 2;

        // SeedPacket.cpp:168-176: mRefreshCounter has to exceed mRefreshTime (strict >) before the
        // packet activates, so a third tick is needed for mRefreshTime = 2.
        packet.update();
        assertEquals(1, packet.mRefreshCounter);
        assertFalse(packet.mActive);

        packet.update();
        assertEquals(2, packet.mRefreshCounter);
        assertFalse(packet.mActive);

        packet.update();
        assertTrue(packet.mActive);
        assertFalse(packet.mRefreshing);
        assertEquals(0, packet.mRefreshCounter);
    }

    @Test
    void canPickUpNeedsAPlayingScene() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);
        board.mSunMoney = 100;

        assertTrue(packet.canPickUp());

        app.mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        assertFalse(packet.canPickUp());

        app.mGameScene = GameScenes.SCENE_PLAYING;
        board.mPaused = true;
        assertFalse(packet.canPickUp());
    }

    @Test
    void canPickUpNeedsEnoughSun() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = newPacket(board, SeedType.SEED_PEASHOOTER);
        int cost = Plant.getCost(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);

        board.mSunMoney = cost;
        assertTrue(packet.canPickUp());

        board.mSunMoney = cost - 1;
        assertFalse(packet.canPickUp());

        // a refreshing packet is never pickable
        board.mSunMoney = 9990;
        packet.mActive = false;
        assertFalse(packet.canPickUp());
    }

    @Test
    void clickingAPacketWithTooLittleSunSetsTheOutOfMoneyCounter() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mSunMoney = 0;
        SeedPacket packet = newPacket(board, SeedType.SEED_PEASHOOTER);

        packet.mouseDown(0, 0, 1);

        // SeedPacket.cpp:763-773
        assertEquals(70, board.mOutOfMoneyCounter);
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, board.mCursorObject.mCursorType);
        assertFalse(app.mSamplesPlayed.isEmpty(), "the buzzer sample must be played");
    }

    @Test
    void clickingARefreshingPacketIsRejected() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mSunMoney = 9990;
        SeedPacket packet = newPacket(board, SeedType.SEED_PEASHOOTER);
        packet.mActive = false;

        packet.mouseDown(0, 0, 1);

        // SeedPacket.cpp:746-757
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, board.mCursorObject.mCursorType);
        assertFalse(packet.mActive, "the packet stays disabled");
        assertEquals(0, board.mOutOfMoneyCounter);
    }

    @Test
    void clickingAPacketPutsItIntoTheBankCursor() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mSunMoney = 9990;
        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);
        packet.mImitaterType = SeedType.SEED_NONE;

        packet.mouseDown(0, 0, 1);

        // SeedPacket.cpp:800-806
        assertEquals(CursorType.CURSOR_TYPE_PLANT_FROM_BANK, board.mCursorObject.mCursorType);
        assertEquals(SeedType.SEED_SUNFLOWER, board.mCursorObject.mType);
        assertEquals(SeedType.SEED_NONE, board.mCursorObject.mImitaterType);
        assertEquals(packet.mIndex, board.mCursorObject.mSeedBankIndex);
        assertFalse(packet.mActive, "the packet leaves the bank once it is picked up");
        assertTrue(app.mSamplesPlayed.size() >= 1);
    }

    @Test
    void clickingAnImitaterPacketCarriesTheImitatedSeed() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        board.mSunMoney = 9990;
        SeedPacket packet = new SeedPacket();
        packet.mBoard = board;
        packet.mIndex = 2;
        packet.setPacketType(SeedType.SEED_IMITATER, SeedType.SEED_SUNFLOWER);

        packet.mouseDown(0, 0, 1);

        assertEquals(SeedType.SEED_IMITATER, board.mCursorObject.mType);
        assertEquals(SeedType.SEED_SUNFLOWER, board.mCursorObject.mImitaterType);
    }

    @Test
    void plantingAPacketStartsItsCooldown() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);

        packet.wasPlanted();

        // SeedPacket.cpp:872-896
        assertEquals(1, packet.mTimesUsed);
        assertTrue(packet.mRefreshing);
        assertEquals(Plant.getRefreshTime(SeedType.SEED_SUNFLOWER, SeedType.SEED_NONE), packet.mRefreshTime);
    }

    @Test
    void mouseHitTestHonoursThePacketOffset() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);
        packet.mX = 100;
        packet.mY = 10;
        packet.mOffsetX = 20;
        packet.mWidth = 50;
        packet.mHeight = 70;
        HitResult hit = new HitResult();

        // SeedPacket.cpp:898-917: the hit box is mX + mOffsetX .. + mWidth
        assertTrue(packet.mouseHitTest(120, 10, hit));
        assertEquals(GameObjectType.OBJECT_TYPE_SEEDPACKET, hit.mObjectType);
        assertEquals(packet, hit.mObject);
        assertFalse(packet.mouseHitTest(100, 10, hit));
        assertEquals(GameObjectType.OBJECT_TYPE_NONE, hit.mObjectType);

        // a spinning slot-machine packet cannot be clicked
        packet.mSlotMachineCountDown = 5;
        assertFalse(packet.mouseHitTest(120, 10, hit));
    }

    @Test
    void slotMachineSpinsUntilItsCountdownExpires() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = new SeedPacket();
        packet.mBoard = board;
        packet.mIndex = 0;
        // SEED_SLOT_MACHINE_SUN has no plant definition, so the packet type is set directly here.
        packet.mPacketType = SeedType.SEED_SLOT_MACHINE_SUN;

        packet.slotMachineStart();
        // SeedPacket.cpp:102-108: the countdown starts at 300 frames
        assertEquals(300, packet.mSlotMachineCountDown);
        assertTrue(packet.mSlotMachiningNextSeed != SeedType.SEED_NONE);

        for (int i = 0; i < 400; i++) {
            packet.update();
        }

        // SeedPacket.cpp:160-189: the reel settles on one of the slot seeds and activates
        assertEquals(0, packet.mSlotMachineCountDown);
        assertTrue(packet.mPacketType != SeedType.SEED_NONE);
        assertTrue(packet.mActive);
        assertEquals(0.0f, packet.mSlotMachiningPosition);
    }

    @Test
    void packetArtPlacementMatchesThePerSeedCases() {
        LawnEntityTestSupport.newApp();  // sets LawnApp.gLawnApp, which the placement reads

        // The three peashooter-family seeds the mod renders its icons from: the repeater uses the
        // default case and the gatling pea its own (SeedPacket.cpp:395-404).
        assertArrayEquals(new float[] {5.0f, 9.0f, 0.5f},
                SeedPacket.getPacketArtPlacement(SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE));
        assertArrayEquals(new float[] {5.0f, 9.0f, 0.5f},
                SeedPacket.getPacketArtPlacement(SeedType.SEED_REPEATER, SeedType.SEED_NONE));
        assertArrayEquals(new float[] {2.0f, 9.0f, 0.5f},
                SeedPacket.getPacketArtPlacement(SeedType.SEED_GATLINGPEA, SeedType.SEED_NONE));
        assertArrayEquals(new float[] {5.0f, 11.0f, 0.5f},
                SeedPacket.getPacketArtPlacement(SeedType.SEED_THREEPEATER, SeedType.SEED_NONE));

        // The vertical offset carries the upstream +1: the tallnut's 22 becomes 23.
        assertArrayEquals(new float[] {12.0f, 23.0f, 0.3f},
                SeedPacket.getPacketArtPlacement(SeedType.SEED_TALLNUT, SeedType.SEED_NONE));

        // An imitater resolves through the imitated type.
        assertArrayEquals(new float[] {2.0f, 9.0f, 0.5f},
                SeedPacket.getPacketArtPlacement(SeedType.SEED_IMITATER, SeedType.SEED_GATLINGPEA));
    }

    @Test
    void deactivateResetsTheCooldown() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedPacket packet = newPacket(board, SeedType.SEED_SUNFLOWER);
        packet.mRefreshTime = 500;
        packet.mRefreshCounter = 100;
        packet.mRefreshing = true;

        packet.deactivate();

        assertEquals(0, packet.mRefreshTime);
        assertEquals(0, packet.mRefreshCounter);
        assertFalse(packet.mRefreshing);
        assertFalse(packet.mActive);

        packet.setActivate(true);
        assertTrue(packet.mActive);
    }
}
