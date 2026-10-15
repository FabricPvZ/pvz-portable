/*
 * Focused headless tests for Plant's Zen Garden branches:
 *   Plant::UpdateReanim  potted-plant scaling (src/Lawn/Plant.cpp:2798-2849)
 *   Plant::DrawShadow    main-garden early-out (src/Lawn/Plant.cpp:3817-3818)
 *
 * Uses the entity test double so the plant's body reanimation records the transform it is given.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

class PlantPottedZenGardenTest {

    private static PottedPlant smallPottedPlant() {
        PottedPlant aPottedPlant = new PottedPlant();
        aPottedPlant.initializePottedPlant(SeedType.SEED_PEASHOOTER);
        aPottedPlant.mPlantAge = PottedPlantAge.PLANTAGE_SMALL;
        aPottedPlant.mFacing = PottedPlant.FacingDirection.FACING_RIGHT;
        return aPottedPlant;
    }

    private static Plant newPottedPlant(TestLawnApp app, Board board) {
        ZenGarden zen = new ZenGarden();
        zen.mApp = app;
        zen.mBoard = board;
        zen.mGardenType = GardenType.GARDEN_MAIN;
        app.mZenGarden = zen;
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN;

        app.mPlayerInfo.mPottedPlant[0] = smallPottedPlant();

        Plant aPlant = new Plant();
        aPlant.mApp = app;
        aPlant.mBoard = board;
        aPlant.mSeedType = SeedType.SEED_PEASHOOTER;
        aPlant.mRow = 1;
        aPlant.mPlantCol = 1;
        aPlant.mStateCountdown = 0;
        aPlant.mBodyReanimID = 1; // the app double resolves every non-null id to its recording reanim
        aPlant.mPottedPlantIndex = 0;
        return aPlant;
    }

    @Test
    void freshPlantHasAlwaysValidInlineStorage() {
        LawnEntityTestSupport.newApp(); // Plant's ctors read LawnApp.gLawnApp (GameObject.mApp)

        Plant aPlant = new Plant();

        // Plant.h:182-183, 202: the rectangles and the magnet array are inline values, never null.
        assertNotNull(aPlant.mPlantRect);
        assertNotNull(aPlant.mPlantAttackRect);
        assertNotNull(aPlant.mMagnetItems);
        assertEquals(Plant.MAX_MAGNET_ITEMS, aPlant.mMagnetItems.length);
        for (MagnetItem anItem : aPlant.mMagnetItems) {
            assertNotNull(anItem);
        }
    }

    @Test
    void updateReanimAppliesTheSmallPottedPlantScale() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        Plant aPlant = newPottedPlant(app, board);

        aPlant.updateReanim();

        // Plant.cpp:2825-2826 (PLANTAGE_SMALL): aScaleStart = aScaleEnd = 0.5f, and with
        // mStateCountdown == 0 the curve lands on aScaleEnd, so both axes end at 0.5.
        assertEquals(0.5f, app.mReanim.mScaleX, 1e-6f);
        assertEquals(0.5f, app.mReanim.mScaleY, 1e-6f);
    }

    @Test
    void drawShadowReturnsEarlyInTheMainZenGarden() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        Plant aPlant = newPottedPlant(app, board);

        // Plant.cpp:3817-3818: in GAMEMODE_CHALLENGE_ZEN_GARDEN + GARDEN_MAIN DrawShadow returns
        // before touching the graphics; a bare Graphics (no target image) would fault if it did not.
        aPlant.drawShadow(new Graphics(), 0.0f, 0.0f);
    }
}
