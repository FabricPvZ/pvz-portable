/*
 * Focused headless test for the SCARY_POT sun read used by GridItem::DrawScaryPot
 * (src/Lawn/GridItem.cpp:360-388) through Challenge::ScaryPotterCountSunInPot
 * (src/Lawn/Challenge.cpp:3868-3871).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GridItemScaryPotSunTest {

    @Test
    void scaryPotterCountSunInPotReturnsThePotSunCount() {
        Board board = LawnEntityTestSupport.newBoard();

        GridItem pot = board.mGridItems.dataArrayAlloc();
        pot.mGridItemType = GridItemType.GRIDITEM_SCARY_POT;
        pot.mScaryPotType = ScaryPotType.SCARYPOT_SUN;
        pot.mSunCount = 3;

        // Challenge.cpp:3868-3871: return theScaryPot->mSunCount;
        assertEquals(3, board.mChallenge.scaryPotterCountSunInPot(pot));
    }
}
