package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.BackgroundType;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ChosenSeedState;
import cn.enaium.fabricpvz.portable.lawn.Dialogs;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.sexy.Dialog;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless verification of the seed chooser rules: the chooser grid geometry, the click-toggle
 * selection state machine, the "Let's Rock" enable rule, Crazy Dave's weighted seed pick and the
 * repick-warning sequence behind the start button.
 *
 * <p>Most of the chooser is static and side-effect free (each such method is annotated with its
 * upstream {@code SeedChooserScreen.cpp} line); the "Let's Rock" sequence needs a constructed
 * screen, which the shared headless fixture provides (stub images/fonts and a live board, no retail
 * assets).
 */
class SeedChooserScreenTest {

    @Test
    void chooserGridIsEightColumnsWithFixedPitch() {
        Point p = new Point();

        SeedChooserScreen.getSeedPositionInChooser(0, true, p);
        assertEquals(22, p.mX);
        assertEquals(123, p.mY);

        SeedChooserScreen.getSeedPositionInChooser(0, false, p);
        assertEquals(22, p.mX);
        assertEquals(128, p.mY);

        // last column of the first row: 22 + 7*53
        SeedChooserScreen.getSeedPositionInChooser(7, true, p);
        assertEquals(393, p.mX);
        assertEquals(123, p.mY);

        // wraps to the next row (8 columns per row)
        SeedChooserScreen.getSeedPositionInChooser(8, true, p);
        assertEquals(22, p.mX);
        assertEquals(123 + 70, p.mY);

        SeedChooserScreen.getSeedPositionInChooser(8, false, p);
        assertEquals(22, p.mX);
        assertEquals(128 + 73, p.mY);

        // index 47 == row 5, column 7
        SeedChooserScreen.getSeedPositionInChooser(47, true, p);
        assertEquals(22 + 7 * 53, p.mX);
        assertEquals(123 + 5 * 70, p.mY);

        SeedChooserScreen.getSeedPositionInChooser(47, false, p);
        assertEquals(128 + 5 * 73, p.mY);
    }

    @Test
    void sevenRowRuleTable() {
        assertTrue(SeedChooserScreen.has7Rows(true, false, seed -> false),
                "finished adventure always uses seven rows");
        assertTrue(SeedChooserScreen.has7Rows(false, true, seed -> false),
                "the Gatling Pea purchase unlocks the seventh row");
        assertFalse(SeedChooserScreen.has7Rows(false, false, seed -> false));

        assertTrue(SeedChooserScreen.has7Rows(false, false, seed -> seed == SeedType.SEED_TWINSUNFLOWER),
                "the first upgrade seed unlocks the seventh row");
        assertTrue(SeedChooserScreen.has7Rows(false, false, seed -> seed == SeedType.SEED_SPIKEROCK - 1));
        assertFalse(SeedChooserScreen.has7Rows(false, false, seed -> seed == SeedType.SEED_SPIKEROCK),
                "SEED_SPIKEROCK alone does not unlock the seventh row");
        assertFalse(SeedChooserScreen.has7Rows(false, false, seed -> seed == SeedType.SEED_COBCANNON),
                "the scan range is [SEED_TWINSUNFLOWER, SEED_COBCANNON)");
    }

    @Test
    void startButtonEnableRuleTable() {
        // bank of ten packets: enabled exactly when all ten were picked
        assertFalse(SeedChooserScreen.startButtonEnabled(0, 10));
        assertFalse(SeedChooserScreen.startButtonEnabled(1, 10));
        assertFalse(SeedChooserScreen.startButtonEnabled(9, 10));
        assertTrue(SeedChooserScreen.startButtonEnabled(10, 10));

        // single-packet levels
        assertFalse(SeedChooserScreen.startButtonEnabled(0, 1));
        assertTrue(SeedChooserScreen.startButtonEnabled(1, 1));

        // pickers are refused further clicks once the bank is full (:834)
        assertTrue(SeedChooserScreen.canMoveChooserSeedToBank(0, 10));
        assertTrue(SeedChooserScreen.canMoveChooserSeedToBank(9, 10));
        assertFalse(SeedChooserScreen.canMoveChooserSeedToBank(10, 10));
    }

    @Test
    void chooserSeedTogglesBetweenChooserAndBank() {
        ChosenSeed seed = new ChosenSeed();
        seed.mSeedType = SeedType.SEED_PEASHOOTER;
        seed.mSeedState = ChosenSeedState.SEED_IN_CHOOSER;
        seed.mX = 22;
        seed.mY = 123;

        // chooser -> bank (:835-845): flies for 25 frames into the next free bank slot
        SeedChooserScreen.clickSeedInChooser(seed, 100, 0);
        assertEquals(ChosenSeedState.SEED_FLYING_TO_BANK, seed.mSeedState);
        assertEquals(0, seed.mSeedIndexInBank);
        assertEquals(100, seed.mTimeStartMotion);
        assertEquals(125, seed.mTimeEndMotion);
        assertEquals(22, seed.mStartX);
        assertEquals(123, seed.mStartY);

        seed.mEndX = 400;
        seed.mEndY = 545;
        assertTrue(SeedChooserScreen.landFlyingSeedState(seed));
        assertEquals(ChosenSeedState.SEED_IN_BANK, seed.mSeedState);
        assertEquals(400, seed.mX);
        assertEquals(545, seed.mY);
        assertEquals(0, seed.mTimeStartMotion);
        assertFalse(SeedChooserScreen.landFlyingSeedState(seed), "a landed seed is no longer flying");

        // bank -> chooser (:826-830): index reset, flies for 25 frames back into the chooser
        seed.mSeedIndexInBank = 3;
        SeedChooserScreen.clickSeedInBank(seed, 200);
        assertEquals(ChosenSeedState.SEED_FLYING_TO_CHOOSER, seed.mSeedState);
        assertEquals(0, seed.mSeedIndexInBank);
        assertEquals(200, seed.mTimeStartMotion);
        assertEquals(225, seed.mTimeEndMotion);

        seed.mEndX = 22;
        seed.mEndY = 123;
        assertTrue(SeedChooserScreen.landFlyingSeedState(seed));
        assertEquals(ChosenSeedState.SEED_IN_CHOOSER, seed.mSeedState);
        assertEquals(22, seed.mX);
        assertEquals(123, seed.mY);
    }

    @Test
    void imitaterSeedFlowStates() {
        ChosenSeed imitater = new ChosenSeed();
        imitater.mSeedType = SeedType.SEED_IMITATER;
        imitater.mSeedState = ChosenSeedState.SEED_PACKET_HIDDEN;
        imitater.mImitaterType = SeedType.SEED_NONE;

        // hidden in the chooser => the imitater button is enabled (:971)
        assertFalse(SeedChooserScreen.imitaterButtonDisabled(imitater.mSeedState));

        // the imitater dialog turns it into a visible copy of the picked plant
        ImitaterDialog.chooseImitaterTarget(imitater, SeedType.SEED_SUNFLOWER);
        assertEquals(ChosenSeedState.SEED_IN_CHOOSER, imitater.mSeedState);
        assertEquals(SeedType.SEED_SUNFLOWER, imitater.mImitaterType);
        assertTrue(SeedChooserScreen.imitaterButtonDisabled(imitater.mSeedState));

        // it is selected like any other seed and keeps its target in the bank
        SeedChooserScreen.clickSeedInChooser(imitater, 5, 0);
        assertEquals(ChosenSeedState.SEED_FLYING_TO_BANK, imitater.mSeedState);
        imitater.mEndX = 100;
        imitater.mEndY = 200;
        assertTrue(SeedChooserScreen.landFlyingSeedState(imitater));
        assertEquals(ChosenSeedState.SEED_IN_BANK, imitater.mSeedState);
        assertEquals(SeedType.SEED_SUNFLOWER, imitater.mImitaterType);

        // clicking it back out of the bank hides it again and clears the copied plant (:486-489)
        SeedChooserScreen.clickSeedInBank(imitater, 10);
        imitater.mEndX = 464;
        imitater.mEndY = 515;
        assertTrue(SeedChooserScreen.landFlyingSeedState(imitater));
        assertEquals(ChosenSeedState.SEED_PACKET_HIDDEN, imitater.mSeedState);
        assertEquals(SeedType.SEED_NONE, imitater.mImitaterType);
        assertFalse(SeedChooserScreen.imitaterButtonDisabled(imitater.mSeedState));
    }

    @Test
    void crazyDaveWeightedPickHonoursWeightsAndCount() {
        PvzpWeightedArray[] seeds = new PvzpWeightedArray[SeedType.NUM_SEED_TYPES];
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = new PvzpWeightedArray();
        }
        seeds[0].mItem = SeedType.SEED_PEASHOOTER;
        seeds[0].mWeight = 0;
        seeds[1].mItem = SeedType.SEED_SUNFLOWER;
        seeds[1].mWeight = 1;
        seeds[2].mItem = SeedType.SEED_CHERRYBOMB;
        seeds[2].mWeight = 0;

        MTRand rng = new MTRand(12345);
        assertEquals(SeedType.SEED_SUNFLOWER,
                SeedChooserScreen.pickFromWeightedArrayUsingSpecialRandSeed(seeds, 3, rng),
                "the only entry with a non-zero weight is always picked");

        // SEED_CHERRYBOMB has weight 99 but sits beyond theCount, so it must be ignored
        seeds[2].mWeight = 99;
        assertEquals(SeedType.SEED_SUNFLOWER,
                SeedChooserScreen.pickFromWeightedArrayUsingSpecialRandSeed(seeds, 2, rng));

        // a two-way split stays inside the weighted range
        seeds[0].mWeight = 1;
        seeds[1].mWeight = 1;
        int picked = SeedChooserScreen.pickFromWeightedArrayUsingSpecialRandSeed(seeds, 2, rng);
        assertTrue(picked == SeedType.SEED_PEASHOOTER || picked == SeedType.SEED_SUNFLOWER);
    }

    // -----------------------------------------------------------------------------------------
    // "Let's Rock" and the repick warnings (SeedChooserScreen.cpp:609-698)
    // -----------------------------------------------------------------------------------------

    /**
     * A headless app that records the warnings the chooser asks for, in order.
     *
     * <p>Upstream walks the warning conditions inline and blocks in each warning's
     * {@code Dialog::WaitForResult} (Dialog.cpp:410-441), resuming after the answer and returning
     * early when it is declined; this port's box delivers the answer to the chooser's continuation
     * instead, so the order of the questions is what these tests pin down.
     */
    static final class RecordingLawnApp extends LawnApp {
        final List<String> mMessages = new ArrayList<>();

        @Override
        public int lawnMessageBox(int theDialogId, String theHeaderName, String theLinesName,
                String theButton1Name, String theButton2Name, int theButtonMode, DialogAnswer theAnswer) {
            mMessages.add(theLinesName);
            return super.lawnMessageBox(theDialogId, theHeaderName, theLinesName, theButton1Name,
                    theButton2Name, theButtonMode, theAnswer);
        }
    }

    /** A headless app with a live board, whose level/background select which warnings fire. */
    private static RecordingLawnApp newApp(int theGameMode, int theBoardLevel, int theBoardBackground) {
        RecordingLawnApp app = new RecordingLawnApp();
        HeadlessTestApp.initialize(app);
        app.mGameMode = theGameMode;
        Board aBoard = new Board(app);
        aBoard.mLevel = theBoardLevel;
        aBoard.mBackground = theBoardBackground;
        aBoard.mNumWaves = 0; // "Flyers are coming" walks the wave table (SeedChooserScreen.cpp:567-582)
        app.mBoard = aBoard;
        return app;
    }

    /**
     * The chooser of that board, in the state {@code CutScene::StartSeedChooser} leaves it
     * (CutScene.cpp:1138 sets {@code mSeedChoosing}, which {@code CloseSeedChooser} clears).
     */
    private static SeedChooserScreen newChooser(RecordingLawnApp app) {
        SeedChooserScreen aChooser = new SeedChooserScreen();
        app.mSeedChooserScreen = aChooser; // CutScene::EndSeedChooser dereferences it (CutScene.cpp:1415-1420)
        app.mBoard.mCutScene.mSeedChoosing = true;
        return aChooser;
    }

    /** Puts a seed in the bank the way a click on it does (ClickedSeedInChooser). */
    private static void pick(SeedChooserScreen theChooser, int theSeedType) {
        theChooser.mChosenSeeds[theSeedType].mSeedState = ChosenSeedState.SEED_IN_BANK;
    }

    /** Depresses a warning's button; its answer runs the chooser's continuation. */
    private static void answer(RecordingLawnApp app, int theButton) {
        ((LawnDialog) app.getDialog(Dialogs.DIALOG_CHOOSER_WARNING)).buttonDepress(theButton);
    }

    /**
     * The regression this covers: {@code DisplayRepickWarningDialog} read the box's result on the
     * spot, which this port never answers (sexy/Dialog.java:506), so every warning was "declined" and
     * pressing "Let's Rock!" did nothing at all. The warnings are now asked one at a time and the
     * sequence resumes on "Let's Rock!" and stops on "pick again" — upstream's inline order.
     */
    @Test
    void theRepickWarningsAreAskedInOrderAndLetsRockStartsAfterTheLast() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_CHALLENGE_SEEING_STARS, 20, BackgroundType.BACKGROUND_1_DAY);
        SeedChooserScreen aChooser = newChooser(app);
        // The Seeing-Stars constructor hands the player Starfruit (SeedChooserScreen.cpp:163-172);
        // unpick it so the mode's own warning (SeedChooserScreen.cpp:611-615) fires.
        aChooser.mChosenSeeds[SeedType.SEED_STARFRUIT].mSeedState = ChosenSeedState.SEED_IN_CHOOSER;

        aChooser.onStartButton();

        assertEquals(List.of("[SEED_CHOOSER_SEEING_STARS_WARNING]"), app.mMessages,
                "the mode's warning is asked first");
        assertNotNull(app.getDialog(Dialogs.DIALOG_CHOOSER_WARNING), "the warning is up");
        assertTrue(app.mBoard.mCutScene.mSeedChoosing, "the level waits for the answer");

        answer(app, Dialog.ID_YES);

        assertEquals(List.of("[SEED_CHOOSER_SEEING_STARS_WARNING]", "[SEED_CHOOSER_SUN_WARNING]"),
                app.mMessages, "SeedChooserScreen.cpp:621-637 — the sun warning follows, in upstream's order");
        assertTrue(app.mBoard.mCutScene.mSeedChoosing, "the level still waits");

        answer(app, Dialog.ID_YES);

        assertNull(app.getDialog(Dialogs.DIALOG_CHOOSER_WARNING), "no warning is left");
        assertFalse(app.mBoard.mCutScene.mSeedChoosing, "the last answer starts the level");
    }

    @Test
    void decliningARepickWarningLeavesTheChooserWaiting() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE, 20, BackgroundType.BACKGROUND_1_DAY);
        SeedChooserScreen aChooser = newChooser(app);
        // Nothing picked: the "no sun producer" warning (SeedChooserScreen.cpp:621-637).
        aChooser.onStartButton();
        assertEquals(List.of("[SEED_CHOOSER_SUN_WARNING]"), app.mMessages);

        answer(app, Dialog.ID_NO);

        assertNull(app.getDialog(Dialogs.DIALOG_CHOOSER_WARNING), "the warning is dismissed");
        assertEquals(1, app.mMessages.size(), "a repick asks nothing further (upstream returns)");
        assertTrue(app.mBoard.mCutScene.mSeedChoosing, "the chooser is still up for another pick");
    }

    @Test
    void theSeedUpgradeWarningsJoinTheSameSequence() {
        RecordingLawnApp app = newApp(GameMode.GAMEMODE_ADVENTURE, 20, BackgroundType.BACKGROUND_1_DAY);
        SeedChooserScreen aChooser = newChooser(app);
        pick(aChooser, SeedType.SEED_SUNFLOWER); // no sun warning
        pick(aChooser, SeedType.SEED_GATLINGPEA); // picked without its upgrade source (SeedChooserScreen.cpp:599-607)

        aChooser.onStartButton();

        assertEquals(1, app.mMessages.size(),
                "the upgrade pair's warning is the only one left (SeedChooserScreen.cpp:685-687)");
        assertTrue(app.mBoard.mCutScene.mSeedChoosing);

        answer(app, Dialog.ID_YES);

        assertFalse(app.mBoard.mCutScene.mSeedChoosing, "the upgrade answer is the last one");

        // Control: the same pick set without the upgrade asks nothing at all and starts right away.
        RecordingLawnApp aSecondApp = newApp(GameMode.GAMEMODE_ADVENTURE, 20, BackgroundType.BACKGROUND_1_DAY);
        SeedChooserScreen aSecondChooser = newChooser(aSecondApp);
        pick(aSecondChooser, SeedType.SEED_SUNFLOWER);

        aSecondChooser.onStartButton();

        assertTrue(aSecondApp.mMessages.isEmpty(), "no warning is asked");
        assertFalse(aSecondApp.mBoard.mCutScene.mSeedChoosing, "the level starts with the first press");
    }
}
