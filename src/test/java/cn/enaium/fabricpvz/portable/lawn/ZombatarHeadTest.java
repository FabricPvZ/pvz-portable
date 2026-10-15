/*
 * Focused headless tests for the Zombatar head application now implemented in ZombieStatus:
 *   Zombie::ApplyZombatarHead        (src/Lawn/Zombie.cpp:3385-3463)
 *   Zombie::SetupZombatarFlagReanim  (src/Lawn/Zombie.cpp:3465-3477)
 *
 * The game module ships no retail .reanim data, so the reanimation definition has no named tracks;
 * the assertions target the state ApplyZombatarHead sets that does not depend on the track table
 * (the anim_head1 image override, the body base pose and the created head reanimation).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.ReanimatorTrackInstance;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

class ZombatarHeadTest {

    /** Headless app whose reanimations look loaded (same double as ZombieTest). */
    private static final class LoadedReanimApp extends LawnApp {
        @Override
        public Reanimation addReanimation(float theX, float theY, int theRenderOrder, int theReanimationType) {
            Reanimation aReanim = super.addReanimation(theX, theY, theRenderOrder, theReanimationType);
            HeadlessTestApp.stubLoadedTracks(aReanim);
            return aReanim;
        }
    }

    private static LawnApp newApp() {
        LawnApp app = new LoadedReanimApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mGameScene = GameScenes.SCENE_PLAYING;
        HeadlessTestApp.initialize(app);
        return app;
    }

    private static Zombie newZombieWithBody(LawnApp app, Board board) {
        Zombie z = new Zombie();
        z.mApp = app;
        z.mBoard = board;
        z.mZombieType = ZombieType.ZOMBIE_FLAG;
        z.mZombiePhase = ZombiePhase.PHASE_ZOMBIE_NORMAL;
        z.mBodyReanimID = app.reanimationGetID(app.addReanimation(0.0f, 0.0f, 0, ReanimationType.REANIM_ZOMBIE));
        z.mZombatarHeadReanimID = ReanimationID.REANIMATIONID_NULL;
        return z;
    }

    @Test
    void applyZombatarHeadOverridesTheHeadAndCreatesTheHeadReanim() {
        LawnApp app = newApp();
        Board board = new Board(app);
        Zombie z = newZombieWithBody(app, board);
        Reanimation body = app.reanimationGet(z.mBodyReanimID);

        ZombieStatus.applyZombatarHead(z, new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE]);

        // Zombie.cpp:3392-3393: anim_head1's image override becomes IMAGE_BLANK.
        ReanimatorTrackInstance head1 = body.getTrackInstanceByName("anim_head1");
        assertNotNull(head1.mImageOverride);
        // Zombie.cpp:3396: aBodyReanim->mFrameBasePose = 0;
        assertEquals(0, body.mFrameBasePose);
        // Zombie.cpp:3398-3404: a REANIM_ZOMBATAR_HEAD reanimation is created and stored.
        assertNotEquals(ReanimationID.REANIMATIONID_NULL, z.mZombatarHeadReanimID);
        assertNotNull(app.reanimationTryToGet(z.mZombatarHeadReanimID));
    }

    @Test
    void setupZombatarFlagReanimIgnoresMissingRecords() {
        LawnApp app = newApp();
        Board board = new Board(app);
        Zombie z = newZombieWithBody(app, board);

        app.mPlayerInfo.mZombatarData = new byte[0];
        ZombieStatus.setupZombatarFlagReanim(z, 0);
        assertEquals(ReanimationID.REANIMATIONID_NULL, z.mZombatarHeadReanimID); // Zombie.cpp:3471-3472

        ZombieStatus.setupZombatarFlagReanim(z, -1);
        assertEquals(ReanimationID.REANIMATIONID_NULL, z.mZombatarHeadReanimID); // Zombie.cpp:3467-3468
    }

    @Test
    void setupZombatarFlagReanimAppliesTheSelectedRecord() {
        LawnApp app = newApp();
        Board board = new Board(app);
        Zombie z = newZombieWithBody(app, board);

        app.mPlayerInfo.mZombatarData = new byte[PlayerInfo.ZOMBATAR_RECORD_SIZE];
        ZombieStatus.setupZombatarFlagReanim(z, 0);

        // Zombie.cpp:3474-3475: the selected 0x48-byte record is handed to ApplyZombatarHead.
        assertNotEquals(ReanimationID.REANIMATIONID_NULL, z.mZombatarHeadReanimID);
    }
}
