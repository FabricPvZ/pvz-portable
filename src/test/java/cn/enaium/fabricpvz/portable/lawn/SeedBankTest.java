/*
 * Headless tests for cn.enaium.fabricpvz.portable.lawn.SeedBank.
 *
 * Covers the seed-bank container contract of SeedPacket.cpp:919-1218: construction, hit testing,
 * conveyor-belt insertion/removal and width/packet layout.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

class SeedBankTest {

    @Test
    void constructorAllocatesEveryPacketSlot() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);

        SeedBank bank = new SeedBank();

        // SeedPacket.cpp:919-928
        assertEquals(0, bank.mNumPackets);
        assertEquals(0, bank.mConveyorBeltCounter);
        assertEquals(255, bank.mCutSceneDarken);
        assertEquals(GameConstants.SEEDBANK_MAX, bank.mSeedPackets.length);
        for (int i = 0; i < GameConstants.SEEDBANK_MAX; i++) {
            assertNotNull(bank.mSeedPackets[i]);
            assertEquals(SeedType.SEED_NONE, bank.mSeedPackets[i].mPacketType);
        }
    }

    @Test
    void moveAndContainsPointUseTheBankRectangle() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = new SeedBank();
        bank.mWidth = 100;
        bank.mHeight = 40;

        bank.move(5, 7);

        assertTrue(bank.containsPoint(5, 7));
        assertTrue(bank.containsPoint(104, 46));
        assertFalse(bank.containsPoint(105, 46));
        assertFalse(bank.containsPoint(4, 7));
    }

    @Test
    void emptyBankNeverHitsAPacket() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = new SeedBank();
        HitResult hit = new HitResult();

        // SeedPacket.cpp:995-1011: with mNumPackets == 0 nothing can be hit
        assertFalse(bank.mouseHitTest(20, 20, hit));
        assertEquals(GameObjectType.OBJECT_TYPE_NONE, hit.mObjectType);
    }

    @Test
    void hitTestDelegatesToThePacketsInsideTheBank() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;
        bank.mNumPackets = 2;
        bank.mWidth = 500;
        bank.move(0, 0);
        SeedPacket packet = bank.mSeedPackets[0];
        packet.mPacketType = SeedType.SEED_SUNFLOWER;
        packet.mX = 10;
        packet.mY = 15;
        packet.mOffsetX = 0;
        HitResult hit = new HitResult();

        assertTrue(bank.mouseHitTest(20, 20, hit));
        assertEquals(packet, hit.mObject);
        assertFalse(bank.mouseHitTest(400, 20, hit));
    }

    @Test
    void addSeedLaysPacketsOutOnTheConveyorBelt() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;
        bank.mNumPackets = 3;
        // Board.cpp:8550-8554 (HasConveyorBeltSeedBank): an adventure mini-boss board is a conveyor-belt
        // level, so SeedPacket.cpp:1020-1021's PVZP_ASSERT(mBoard->HasConveyorBeltSeedBank()) holds.
        board.mLevel = 10;

        // SeedPacket.cpp:1018-1048
        bank.addSeed(SeedType.SEED_SUNFLOWER, false);
        bank.addSeed(SeedType.SEED_PEASHOOTER, false);

        assertEquals(2, bank.getNumSeedsOnConveyorBelt());
        assertEquals(1, bank.countOfTypeOnConveyorBelt(SeedType.SEED_SUNFLOWER));
        assertEquals(0, bank.countOfTypeOnConveyorBelt(SeedType.SEED_WALLNUT));
        assertEquals(515, bank.mSeedPackets[0].mOffsetX);
        // SeedPacket.cpp:1035-1047: belt slot 1 starts at 515-(w+1)=464, which is below packet 0, so
        // the clamp moves it to the previous packet's offset + 40.
        assertEquals(bank.mSeedPackets[0].mOffsetX + 40, bank.mSeedPackets[1].mOffsetX);
        assertTrue(bank.mSeedPackets[0].mActive);
        assertFalse(bank.mSeedPackets[0].mRefreshing);
        assertEquals(SeedType.SEED_PEASHOOTER, bank.mSeedPackets[1].mPacketType);
    }

    @Test
    void addSeedCanPlaceAPacketOnTheLeft() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;
        bank.mNumPackets = 3;
        // Board.cpp:8550-8554: mini-boss board ⇒ conveyor-belt seed bank (SeedPacket.cpp:1020).
        board.mLevel = 10;

        bank.addSeed(SeedType.SEED_WALLNUT, true);

        assertEquals(0, bank.mSeedPackets[0].mOffsetX);
    }

    @Test
    void removingAPacketShiftsTheBeltLeft() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;
        bank.mNumPackets = 3;
        // Board.cpp:8550-8554: mini-boss board ⇒ conveyor-belt seed bank (SeedPacket.cpp:1052).
        board.mLevel = 10;
        bank.addSeed(SeedType.SEED_SUNFLOWER, false);
        bank.addSeed(SeedType.SEED_PEASHOOTER, false);
        int secondOffset = bank.mSeedPackets[1].mOffsetX;

        // SeedPacket.cpp:1050-1080
        bank.removeSeed(0);

        assertEquals(1, bank.getNumSeedsOnConveyorBelt());
        assertEquals(SeedType.SEED_PEASHOOTER, bank.mSeedPackets[0].mPacketType);
        assertEquals(secondOffset + GameConstants.SEED_PACKET_WIDTH + 1, bank.mSeedPackets[0].mOffsetX);
        assertTrue(bank.mSeedPackets[0].mActive);
    }

    @Test
    void removingTheLastPacketClearsItsSlot() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;
        // SeedPacket.cpp:1063-1071: the slot-clearing branch only runs for the belt's last slot, so
        // the bank must hold exactly one packet to remove it there (a two-slot bank shifts instead).
        bank.mNumPackets = 1;
        // Board.cpp:8550-8554: mini-boss board ⇒ conveyor-belt seed bank (SeedPacket.cpp:1052).
        board.mLevel = 10;
        bank.addSeed(SeedType.SEED_SUNFLOWER, false);

        bank.removeSeed(0);

        assertEquals(SeedType.SEED_NONE, bank.mSeedPackets[0].mPacketType);
        assertEquals(0, bank.mSeedPackets[0].mOffsetX);
        assertEquals(0, bank.getNumSeedsOnConveyorBelt());
    }

    @Test
    void updateWidthFollowsTheBoardLayout() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;

        // SeedPacket.cpp:1165-1174
        bank.updateWidth();

        assertEquals(board.getNumSeedsInBank(), bank.mNumPackets);
        for (int i = 0; i < bank.mNumPackets; i++) {
            assertEquals(board.getSeedPacketPositionX(i), bank.mSeedPackets[i].mX);
        }
    }

    @Test
    void refreshAllPacketsActivatesRefreshingOnes() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        SeedBank bank = board.mSeedBank;
        bank.mNumPackets = 2;
        bank.mSeedPackets[0].mPacketType = SeedType.SEED_SUNFLOWER;
        bank.mSeedPackets[0].mRefreshing = true;
        bank.mSeedPackets[0].mActive = false;
        bank.mSeedPackets[0].mRefreshCounter = 500;
        bank.mSeedPackets[1].mPacketType = SeedType.SEED_NONE;

        // SeedPacket.cpp:1176-1193
        bank.refreshAllPackets();

        assertTrue(bank.mSeedPackets[0].mActive);
        assertFalse(bank.mSeedPackets[0].mRefreshing);
        assertEquals(0, bank.mSeedPackets[0].mRefreshCounter);
    }
}
