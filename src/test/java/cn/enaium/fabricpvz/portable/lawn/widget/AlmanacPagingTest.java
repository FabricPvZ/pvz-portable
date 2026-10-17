package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.AlmanacPage;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless verification of the almanac's pages: which registered content gets a page behind the retail
 * grid, what a page's slots map to, and what the selection does when the page on screen changes.
 *
 * <p>The grid itself is upstream (page 0 is the retail one); the pages are the port's addition, so what
 * these tests pin down is that a registered plant/zombie is reachable in the almanac without the retail
 * squares, their texts or a single-page almanac changing. The registration the almanac reads is the one
 * the game reads: a definition row in {@link Plant#gPlantDefs} / {@link Zombie#gZombieDefs}, written the
 * way a registrant writes it before the game reads the table, plus - for a plant - a
 * {@code LawnApp#hasSeedType} that answers for that seed type (which is what the mod API answers through
 * its own mixin in a game that has mods installed).
 */
class AlmanacPagingTest {

    /** A seed type above the retail range, i.e. one a registrant can define (SeedType.MAX_SEED_TYPES). */
    private static final int TEST_SEED_TYPE = 100;
    /** A second one, so a page can be checked to hold its seed types in id order. */
    private static final int SECOND_SEED_TYPE = 101;
    /** A zombie type above the retail zombie range (ZombieType.MAX_ZOMBIE_TYPES). */
    private static final int TEST_ZOMBIE_TYPE = 64;

    @AfterEach
    void unregisterTheTestContent() {
        Plant.gPlantDefs[TEST_SEED_TYPE] = null;
        Plant.gPlantDefs[SECOND_SEED_TYPE] = null;
        Zombie.gZombieDefs[TEST_ZOMBIE_TYPE] = null;
    }

    /**
     * A headless app whose {@code LawnApp::HasSeedType} answers for the seed types above the retail
     * range this test registered, the way a registration does in a game that has their definitions.
     */
    static final class PagingLawnApp extends LawnApp {
        int mAvailableSeedType = SeedType.SEED_NONE;
        int mSecondAvailableSeedType = SeedType.SEED_NONE;

        @Override
        public boolean hasSeedType(int theSeedType) {
            return theSeedType == mAvailableSeedType || theSeedType == mSecondAvailableSeedType
                    || super.hasSeedType(theSeedType);
        }
    }

    private static PagingLawnApp newApp() {
        PagingLawnApp app = new PagingLawnApp();
        HeadlessTestApp.initialize(app);
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        // A player mid-adventure: LawnApp::HasSeedType answers for the seeds the levels up to this one
        // awarded, so the retail grid has entries to hit-test.
        app.mPlayerInfo.mLevel = 11;
        return app;
    }

    /**
     * Writes the test seed type's definition row into the table the game reads - what a registrant does
     * before the game reads {@link Plant#gPlantDefs} - and makes the app answer for it.
     */
    private static void registerTestPlant(PagingLawnApp app, int theSeedType) {
        Plant.gPlantDefs[theSeedType] = new PlantDefinition(theSeedType, null, ReanimationType.REANIM_PEASHOOTER,
                0, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "TEST_PLANT");
        if (app.mAvailableSeedType == SeedType.SEED_NONE) {
            app.mAvailableSeedType = theSeedType;
        } else {
            app.mSecondAvailableSeedType = theSeedType;
        }
    }

    /** The zombie half of {@link #registerTestPlant(PagingLawnApp, int)}. */
    private static void registerTestZombie() {
        Zombie.gZombieDefs[TEST_ZOMBIE_TYPE] = new ZombieDefinition(TEST_ZOMBIE_TYPE,
                ReanimationType.REANIM_ZOMBIE, 1, 1, 1, 4000, "TEST_ZOMBIE");
    }

    /** The almanac of that app, built the way the game opens it ({@code LawnApp::DoAlmanacDialog}). */
    private static AlmanacDialog newDialog(PagingLawnApp app) {
        return app.doAlmanacDialog(SeedType.SEED_NONE, ZombieType.ZOMBIE_INVALID);
    }

    /** The square of one slot of the plant grid on screen. */
    private static Point plantSlotPosition(AlmanacDialog theDialog, int theSlot) {
        Point aPoint = new Point();
        theDialog.getSeedSlotPosition(theSlot, aPoint);
        return aPoint;
    }

    @Test
    void aRegisteredPlantGetsItsOwnPageAndTheRetailGridStaysWhereItIs() {
        PagingLawnApp app = newApp();
        registerTestPlant(app, TEST_SEED_TYPE);
        AlmanacDialog aDialog = newDialog(app);

        assertEquals(2, aDialog.mPlantNumPages, "the retail page plus the page of the registered plant");
        assertEquals(0, aDialog.mPlantPage, "the almanac opens on the retail page");
        assertEquals(1, aDialog.mZombieNumPages, "no zombie is registered");
        // A single-page grid draws no arrows at all (RefreshPageButtons); the retail page would
        // otherwise not be upstream's.
        assertTrue(aDialog.mPrevPageButton.mBtnNoDraw);
        assertTrue(aDialog.mNextPageButton.mBtnNoDraw);

        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);

        // Page 0 is the retail grid: slot n is seed type n, the imitater keeping its own slot.
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.plantSlotSeedType(0));
        assertEquals(SeedType.SEED_WALLNUT, aDialog.plantSlotSeedType(SeedType.SEED_WALLNUT));
        assertEquals(SeedType.SEED_IMITATER, aDialog.plantSlotSeedType(SeedType.SEED_IMITATER),
                "the retail grid's last slot is the imitater's own");
        assertEquals(SeedType.SEED_NONE, aDialog.plantSlotSeedType(AlmanacDialog.NUM_ALMANAC_SEEDS),
                "the retail page has no slot past the imitater's");

        // The retail squares and the texts the right-hand card shows are untouched.
        Point aWallnut = plantSlotPosition(aDialog, SeedType.SEED_WALLNUT);
        assertEquals(SeedType.SEED_WALLNUT, aDialog.seedHitTest(aWallnut.mX + 4, aWallnut.mY + 4));
        PlantDefinition aPeashooter = Plant.getPlantDefinition(SeedType.SEED_PEASHOOTER);
        assertEquals("[PEASHOOTER_DESCRIPTION]",
                AlmanacDialog.getPlantDescriptionText(SeedType.SEED_PEASHOOTER, aPeashooter));

        // The registered plant is not on page 0 - it shares its first slot's square with the
        // peashooter - but on the page behind it, where that square is its own.
        Point aSlotZero = plantSlotPosition(aDialog, 0);
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.seedHitTest(aSlotZero.mX + 4, aSlotZero.mY + 4));

        aDialog.switchPlantPage(1);

        assertEquals(1, aDialog.mPlantPage);
        assertEquals(TEST_SEED_TYPE, aDialog.plantSlotSeedType(0), "the first slot of the new page");
        assertEquals(SeedType.SEED_NONE, aDialog.plantSlotSeedType(AlmanacDialog.ALMANAC_PLANT_PAGE_SIZE),
                "a page after the retail one holds the non-special squares only");
        assertEquals(TEST_SEED_TYPE, aDialog.seedHitTest(aSlotZero.mX + 4, aSlotZero.mY + 4),
                "the square of the page on screen is clickable");
        assertFalse(aDialog.mPrevPageButton.mBtnNoDraw, "two pages, so the arrows are drawn");
        assertFalse(aDialog.mNextPageButton.mBtnNoDraw);
        assertFalse(aDialog.mPrevPageButton.mDisabled, "the first page does not disable the left arrow");
        assertTrue(aDialog.mNextPageButton.mDisabled, "the last page disables the right arrow");
    }

    @Test
    void aPageHoldsItsSeedTypesInIdOrder() {
        PagingLawnApp app = newApp();
        registerTestPlant(app, SECOND_SEED_TYPE);
        registerTestPlant(app, TEST_SEED_TYPE); // registered second, i.e. the higher id
        AlmanacDialog aDialog = newDialog(app);

        assertEquals(2, aDialog.mPlantNumPages, "both seed types fit on one page");
        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);
        aDialog.switchPlantPage(1);

        assertEquals(TEST_SEED_TYPE, aDialog.plantSlotSeedType(0), "the lower id comes first");
        assertEquals(SECOND_SEED_TYPE, aDialog.plantSlotSeedType(1));

        Point aSlotZero = plantSlotPosition(aDialog, 0);
        Point aSlotOne = plantSlotPosition(aDialog, 1);
        assertNotEquals(aSlotZero.mX, aSlotOne.mX, "two slots of the grid, two squares");
        assertEquals(TEST_SEED_TYPE, aDialog.seedHitTest(aSlotZero.mX + 4, aSlotZero.mY + 4));
        assertEquals(SECOND_SEED_TYPE, aDialog.seedHitTest(aSlotOne.mX + 4, aSlotOne.mY + 4));
    }

    @Test
    void aSeedTypeWithoutADefinitionOrWithoutAvailabilityGetsNoPage() {
        PagingLawnApp app = newApp();

        // The definition is there, the availability is not: the almanac lists what the player owns,
        // so the seed type is on no page and the grid stays a single page.
        Plant.gPlantDefs[TEST_SEED_TYPE] = new PlantDefinition(TEST_SEED_TYPE, null,
                ReanimationType.REANIM_PEASHOOTER, 0, 175, 750, PlantSubClass.SUBCLASS_SHOOTER, 150, "TEST_PLANT");
        AlmanacDialog aDialog = newDialog(app);
        assertEquals(1, aDialog.mPlantNumPages);

        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);
        aDialog.switchPlantPage(1);
        assertEquals(0, aDialog.mPlantPage, "there is no page to switch to");

        // The other way round: available, but nobody defined it.
        app.mAvailableSeedType = SECOND_SEED_TYPE;
        aDialog.rebuildPlantPage();
        assertEquals(1, aDialog.mPlantNumPages, "an undefined seed type has no entry to draw");
    }

    @Test
    void aGameWithoutRegisteredContentHasOnePagePerGrid() {
        AlmanacDialog aDialog = newDialog(newApp());

        assertEquals(1, aDialog.mPlantNumPages, "the retail seed types are one page");
        assertEquals(1, aDialog.mZombieNumPages, "so are the retail zombie types");
        assertEquals(0, aDialog.mPlantPage);
        assertEquals(0, aDialog.mZombiePage);

        // The arrows of a one-page grid are not drawn (DrawPageCounter and RefreshPageButtons both
        // return), which is what keeps the unmodded almanac exactly upstream's.
        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);
        assertTrue(aDialog.mPrevPageButton.mBtnNoDraw);
        assertTrue(aDialog.mNextPageButton.mBtnNoDraw);
        assertFalse(aDialog.mPrevPageButton.isMouseOver());

        aDialog.switchPlantPage(1);
        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_ZOMBIES);
        aDialog.switchZombiePage(1);
        assertEquals(0, aDialog.mPlantPage);
        assertEquals(0, aDialog.mZombiePage);
    }

    @Test
    void aRegisteredZombieGetsItsOwnPage() {
        PagingLawnApp app = newApp();
        registerTestZombie();
        AlmanacDialog aDialog = newDialog(app);

        assertEquals(2, aDialog.mZombieNumPages, "the retail page plus the page of the registered zombie");
        assertEquals(1, aDialog.mPlantNumPages, "no plant is registered");

        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_ZOMBIES);
        assertEquals(ZombieType.ZOMBIE_NORMAL, aDialog.zombieSlotZombieType(0));
        assertEquals(ZombieType.ZOMBIE_BOSS, aDialog.zombieSlotZombieType(AlmanacDialog.NUM_ALMANAC_ZOMBIES - 1),
                "the retail grid ends on the boss's own slot");
        assertEquals(ZombieType.ZOMBIE_INVALID,
                aDialog.zombieSlotZombieType(AlmanacDialog.NUM_ALMANAC_ZOMBIES), "no slot past the boss");

        aDialog.switchZombiePage(1);

        assertEquals(1, aDialog.mZombiePage);
        assertEquals(TEST_ZOMBIE_TYPE, aDialog.zombieSlotZombieType(0), "the first slot of the new page");
        Point aSlotZero = new Point();
        aDialog.getZombieSlotPosition(0, aSlotZero);
        assertEquals(TEST_ZOMBIE_TYPE, aDialog.zombieHitTest(aSlotZero.mX + 4, aSlotZero.mY + 4));
    }

    @Test
    void switchingThePageKeepsASelectionOfAnotherPage() {
        PagingLawnApp app = newApp();
        registerTestPlant(app, TEST_SEED_TYPE);
        AlmanacDialog aDialog = newDialog(app);
        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);

        // The almanac opens on the peashooter; its plant is what the card draws.
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.mSelectedSeed);
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.mPlant.mSeedType);

        aDialog.switchPlantPage(1);
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.mSelectedSeed,
                "the page on screen does not move the selection");
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.mPlant.mSeedType,
                "the card keeps drawing the selection of the other page");

        // Clicking the registered plant on its page selects it, and going back keeps it selected.
        Point aSlotZero = plantSlotPosition(aDialog, 0);
        aDialog.mouseDown(aSlotZero.mX + 4, aSlotZero.mY + 4, 1);
        assertEquals(TEST_SEED_TYPE, aDialog.mSelectedSeed);

        aDialog.switchPlantPage(0);
        assertEquals(TEST_SEED_TYPE, aDialog.mSelectedSeed, "the retail page does not drop it either");
        assertEquals(TEST_SEED_TYPE, aDialog.mPlant.mSeedType, "and the card draws it");
    }

    @Test
    void anUnreachableSelectionFallsBackToThePageOnScreen() {
        PagingLawnApp app = newApp();
        registerTestPlant(app, TEST_SEED_TYPE);
        AlmanacDialog aDialog = newDialog(app);
        aDialog.setPage(AlmanacPage.ALMANAC_PAGE_PLANTS);
        aDialog.switchPlantPage(1);

        Point aSlotZero = plantSlotPosition(aDialog, 0);
        aDialog.mouseDown(aSlotZero.mX + 4, aSlotZero.mY + 4, 1);
        assertEquals(TEST_SEED_TYPE, aDialog.mSelectedSeed);

        // The registration goes away while the dialog is open (an API that unregisters content does
        // that), so the selected plant is on no page any more.
        Plant.gPlantDefs[TEST_SEED_TYPE] = null;
        app.mAvailableSeedType = SeedType.SEED_NONE;
        aDialog.switchPlantPage(0);

        assertEquals(1, aDialog.mPlantNumPages, "the page of the unregistered plant is gone");
        assertEquals(0, aDialog.mPlantPage);
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.mSelectedSeed,
                "the selection falls back to the first slot of the page on screen");
        assertEquals(SeedType.SEED_PEASHOOTER, aDialog.mPlant.mSeedType, "and the card is rebuilt for it");
    }

    @Test
    void thePageArrowsSitInTheFreeStretchOfTheBottomButtonRow() {
        // The bottom row holds the index button (32..196 by 567..593) and the close button
        // (676..765 by 567..593); the arrows share the row without touching either.
        int aRight = AlmanacDialog.ALMANAC_PAGE_NEXT_BUTTON_X + AlmanacDialog.ALMANAC_PAGE_BUTTON_WIDTH;
        assertTrue(AlmanacDialog.ALMANAC_PAGE_PREV_BUTTON_X >= 32 + 164,
                "the left arrow stays right of the index button");
        assertTrue(aRight <= 676, "the right arrow stays left of the close button");
        assertTrue(AlmanacDialog.ALMANAC_PAGE_COUNTER_X > AlmanacDialog.ALMANAC_PAGE_PREV_BUTTON_X
                && AlmanacDialog.ALMANAC_PAGE_COUNTER_X < aRight, "the counter sits between the arrows");
        assertTrue(AlmanacDialog.ALMANAC_PAGE_BUTTON_Y >= 567
                && AlmanacDialog.ALMANAC_PAGE_BUTTON_Y + AlmanacDialog.ALMANAC_PAGE_BUTTON_HEIGHT <= 593,
                "the arrows share the row of the two buttons");
    }
}
