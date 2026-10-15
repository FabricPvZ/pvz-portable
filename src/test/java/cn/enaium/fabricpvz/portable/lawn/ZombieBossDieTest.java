/*
 * Focused headless test for Zombie::BossDie (src/Lawn/Zombie.cpp:10475-10503).
 *
 * Upstream fades the music out (Zombie.cpp:10490, FadeOut(200)) and then kills every living
 * follower before removing the boss's cold effects. Asserted values are quoted from those lines.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubBoard;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.StubZombie;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;

class ZombieBossDieTest {

    @Test
    void bossDieFadesTheMusicAndKillsTheLivingFollowers() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        StubBoard board = LawnEntityTestSupport.newStubBoard(app);
        // Music::FadeOut only arms the counters while a tune is playing (Music.cpp:378-384).
        app.mMusic.mCurMusicTune = MusicTune.MUSIC_TUNE_DAY_GRASSWALK;

        StubZombie boss = board.addStubZombie(2, 100, 100);
        boss.mBossFireBallReanimID = ReanimationID.REANIMATIONID_NULL;
        StubZombie follower = board.addStubZombie(3, 200, 100);
        StubZombie dead = board.addStubZombie(4, 300, 100);
        dead.mDead = true;

        ZombieBoss.die(boss);

        // Zombie.cpp:10490: mApp->mMusic->FadeOut(200).
        assertEquals(200, app.mMusic.mFadeOutDuration);
        assertEquals(200, app.mMusic.mFadeOutCounter);

        // Zombie.cpp:10492-10501: every living follower is killed, the dead ones are skipped.
        assertTrue(follower.mDiedWithLoot);
        assertFalse(dead.mDiedWithLoot);
    }
}
