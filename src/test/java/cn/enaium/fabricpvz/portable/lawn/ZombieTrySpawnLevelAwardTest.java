/*
 * Focused headless test for the former Scary-Potter guard in Zombie::TrySpawnLevelAward
 * (src/Lawn/Zombie.cpp:7137-7276). The branch now queries Challenge::ScaryPotterIsCompleted
 * (src/Lawn/Challenge.cpp:3765-3777) instead of throwing.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

class ZombieTrySpawnLevelAwardTest {

    @Test
    void trySpawnLevelAwardDefersWhileScaryPotterPotsRemain() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_SCARY_POTTER_1;
        Board board = LawnEntityTestSupport.newBoard(app);

        Zombie z = new Zombie();
        z.mApp = app;
        z.mBoard = board;
        z.mFromWave = 0;
        z.mDead = false;

        // A live scary pot makes Challenge::ScaryPotterIsCompleted() return 0 (Challenge.cpp:3767-3773),
        // so TrySpawnLevelAward returns false before awarding (Zombie.cpp:7153-7157).
        GridItem pot = board.mGridItems.dataArrayAlloc();
        pot.mGridItemType = GridItemType.GRIDITEM_SCARY_POT;

        assertFalse(ZombieStatus.trySpawnLevelAward(z));
        assertFalse(board.mLevelAwardSpawned);
    }
}
