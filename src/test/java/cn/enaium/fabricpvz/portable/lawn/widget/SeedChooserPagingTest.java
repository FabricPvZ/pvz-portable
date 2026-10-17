package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.BackgroundType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChosenSeedState;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless verification of the chooser's pages: which page a seed type above the retail range lands
 * on, when the arrows are there at all, and what a seed of another page does when it comes back from
 * the bank.
 *
 * <p>The grid itself is upstream (page 0 is the retail one); the pages are the port's addition
 * ({@code SeedChooserScreen} "PORT-NOTE"), so what these tests pin down is that a registered seed
 * type is reachable without the retail squares moving, and that a game whose seed types all fit on
 * page 0 behaves exactly like the upstream chooser. The registration the chooser reads is the one the
 * game reads: a definition row in {@link Plant#gPlantDefs}, written the way a registrant writes it
 * before the game reads the table, plus a {@code LawnApp#hasSeedType} that answers for that seed type
 * (which is what the mod API answers through its own mixin in a game that has mods installed).
 */
class SeedChooserPagingTest {

    /** A seed type above the retail range, i.e. one a registrant can define (SeedType.MAX_SEED_TYPES). */
    private static final int TEST_SEED_TYPE = 100;

    @AfterEach
    void unregisterTheTestPlant() {
        Plant.gPlantDefs[TEST_SEED_TYPE] = null;
    }

    /**
     * A headless app whose {@code LawnApp::HasSeedType} answers for one seed type above the retail
     * range, the way a registration does in a game that has the seed type's definition.
     */
    static final class PagingLawnApp extends LawnApp {
        int mAvailableSeedType = SeedType.SEED_NONE;

        @Override
        public boolean hasSeedType(int theSeedType) {
            return theSeedType == mAvailableSeedType || super.hasSeedType(theSeedType);
        }
    }

    private static PagingLawnApp newApp() {
        PagingLawnApp app = new PagingLawnApp();
        HeadlessTestApp.initialize(app);
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        Board aBoard = new Board(app);
        aBoard.mLevel = 2;
        aBoard.mBackground = BackgroundType.BACKGROUND_1_DAY;
        aBoard.mNumWaves = 0;
        aBoard.mSeedBank.mNumPackets = 10; // a level's bank, so a pick may fly into it
        app.mBoard = aBoard;
        return app;
    }

    /**
     * The chooser of that board, built the way {@code CutScene::StartSeedChooser} builds it. The seed
     * type has to be available before this runs: the constructor is what puts it on a page.
     */
    private static SeedChooserScreen newChooser(PagingLawnApp app) {
        SeedChooserScreen aChooser = new SeedChooserScreen();
        app.mSeedChooserScreen = aChooser;
        app.mBoard.mCutScene.mSeedChoosing = true;
        return aChooser;
    }

    /**
     * Writes the test seed type's definition row into the table the game reads - what a registrant
     * does before the game reads {@link Plant#gPlantDefs} - and makes the app answer for it.
     */
    private static void registerTestPlant(PagingLawnApp app) {
        Plant.gPlantDefs[TEST_SEED_TYPE] = new PlantDefinition(TEST_SEED_TYPE, null,
                ReanimationType.REANIM_PEASHOOTER, 0, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "TEST_PLANT");
        app.mAvailableSeedType = TEST_SEED_TYPE;
    }

    @Test
    void aRegisteredSeedTypeGetsAPageAndTheRetailGridStaysWhereItIs() {
        PagingLawnApp app = newApp();
        registerTestPlant(app);
        SeedChooserScreen aChooser = newChooser(app);

        assertEquals(2, aChooser.mNumPages, "the retail page plus the page of the registered seed type");
        assertEquals(0, aChooser.mPage, "the chooser opens on the retail page");

        // Page 0 is the retail grid: slot n holds seed type n, at the upstream square of that slot.
        Point aPos = new Point();
        for (int aSeedType : new int[] {SeedType.SEED_PEASHOOTER, 7, 8, SeedType.SEED_IMITATER - 1}) {
            SeedChooserScreen.getSeedPositionInChooser(aSeedType, aChooser.has7Rows(), aPos);
            assertEquals(aPos.mX, aChooser.mChosenSeeds[aSeedType].mX,
                    "seed type " + aSeedType + " keeps its upstream square");
            assertEquals(aPos.mY, aChooser.mChosenSeeds[aSeedType].mY,
                    "seed type " + aSeedType + " keeps its upstream square");
        }

        // The registered seed type is on the page after it, in the first free slot, and joins the
        // chooser there (it is pickable, not a silhouette).
        assertEquals(ChosenSeedState.SEED_IN_CHOOSER, aChooser.mChosenSeeds[TEST_SEED_TYPE].mSeedState);
        SeedChooserScreen.getSeedPositionInChooser(0, aChooser.has7Rows(), aPos);
        assertFalse(aChooser.seedHitTest(aPos.mX + 4, aPos.mY + 4) == TEST_SEED_TYPE,
                "page 0 does not show it: its own page's square is not a square of page 0");

        aChooser.switchPage(1);

        assertEquals(1, aChooser.mPage);
        assertEquals(aPos.mX, aChooser.mChosenSeeds[TEST_SEED_TYPE].mX, "slot 0 of its page");
        assertEquals(aPos.mY, aChooser.mChosenSeeds[TEST_SEED_TYPE].mY, "slot 0 of its page");
        assertEquals(TEST_SEED_TYPE, aChooser.seedHitTest(aPos.mX + 4, aPos.mY + 4),
                "the square of the page on screen is clickable");
    }

    @Test
    void aSeedTypeThePlayerMayNotPickGetsNoPage() {
        PagingLawnApp app = newApp();
        // The definition is there, the availability is not: the chooser offers what the player may
        // pick, so the seed type is on no page and the grid stays a single page.
        Plant.gPlantDefs[TEST_SEED_TYPE] = new PlantDefinition(TEST_SEED_TYPE, null,
                ReanimationType.REANIM_PEASHOOTER, 0, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "TEST_PLANT");
        SeedChooserScreen aChooser = newChooser(app);

        assertEquals(1, aChooser.mNumPages);
        aChooser.switchPage(1);
        assertEquals(0, aChooser.mPage, "there is no page to switch to");
    }

    @Test
    void aGameWithoutTheApiHasASinglePage() {
        SeedChooserScreen aChooser = newChooser(newApp());

        assertEquals(1, aChooser.mNumPages, "the retail seed types are one page");
        assertEquals(0, aChooser.mPage);

        // The arrows are the page control of a grid that has more than one page; with one page they
        // are neither drawn (DrawPageButtons returns on a single page) nor clickable (MouseDown asks
        // for more than one page), so the screen stays the upstream one.
        assertTrue(SeedChooserScreen.pageButtonHit(SeedChooserScreen.PAGE_PREV_BUTTON_X,
                SeedChooserScreen.PAGE_PREV_BUTTON_X + 1, SeedChooserScreen.PAGE_BUTTON_Y + 1));
        assertFalse(SeedChooserScreen.pageButtonHit(SeedChooserScreen.PAGE_PREV_BUTTON_X,
                SeedChooserScreen.PAGE_PREV_BUTTON_X - 1, SeedChooserScreen.PAGE_BUTTON_Y + 1));
        assertFalse(SeedChooserScreen.pageButtonHit(SeedChooserScreen.PAGE_PREV_BUTTON_X,
                SeedChooserScreen.PAGE_PREV_BUTTON_X + 1, SeedChooserScreen.PAGE_BUTTON_Y - 1));
    }

    @Test
    void theArrowSquaresSitBesideLetsRockWithoutTouchingIt() {
        // "Let's Rock" is 154..310 by 545..587 (SeedChooserScreen.cpp:146-160 area).
        assertTrue(SeedChooserScreen.PAGE_PREV_BUTTON_X >= 0);
        assertTrue(SeedChooserScreen.PAGE_PREV_BUTTON_X + SeedChooserScreen.PAGE_BUTTON_WIDTH <= 154,
                "the left arrow stays left of the button");
        assertTrue(SeedChooserScreen.PAGE_NEXT_BUTTON_X >= 310,
                "the right arrow stays right of the button");
        assertTrue(SeedChooserScreen.PAGE_BUTTON_Y >= 545
                && SeedChooserScreen.PAGE_BUTTON_Y + SeedChooserScreen.PAGE_BUTTON_HEIGHT <= 587,
                "the arrows share the button's row");
    }

    @Test
    void aSeedOfAnotherPageIsStillInTheBankAndComesBackToItsOwnSquare() {
        PagingLawnApp app = newApp();
        registerTestPlant(app);
        SeedChooserScreen aChooser = newChooser(app);

        // Filled in on its own page, which is the page it can be picked on...
        aChooser.switchPage(1);
        aChooser.clickedSeedInChooser(aChooser.mChosenSeeds[TEST_SEED_TYPE]);
        ChosenSeed aChosenSeed = aChooser.mChosenSeeds[TEST_SEED_TYPE];
        assertEquals(ChosenSeedState.SEED_FLYING_TO_BANK, aChosenSeed.mSeedState);
        assertEquals(1, aChooser.mSeedsInBank);

        // ...and the bank keeps it while another page is on screen.
        aChooser.switchPage(0);
        assertEquals(0, aChooser.mPage, "the retail page is on screen");
        assertEquals(TEST_SEED_TYPE, aChooser.findSeedInBank(0), "the bank still knows the seed");

        // Clicking it in the bank sends it back to its own page's square, not to a square of the page
        // on screen (SeedChooserScreen.cpp:800-830).
        aChooser.clickedSeedInBank(aChosenSeed);

        Point aPos = new Point();
        SeedChooserScreen.getSeedPositionInChooser(0, aChooser.has7Rows(), aPos);
        assertEquals(aPos.mX, aChosenSeed.mEndX);
        assertEquals(aPos.mY, aChosenSeed.mEndY);
        assertEquals(0, aChooser.mSeedsInBank);
    }
}
