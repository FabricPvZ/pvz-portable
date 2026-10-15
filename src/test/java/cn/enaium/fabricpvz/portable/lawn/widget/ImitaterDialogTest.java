package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.ChosenSeedState;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.sexy.Point;

/**
 * Headless verification of the imitater dialog: the seed grid it lays out and the duplicate
 * selection flow it hands back to the seed chooser.
 */
class ImitaterDialogTest {

    @Test
    void dialogGridIsEightColumnsCentredInTheDialog() {
        Point p = new Point();

        // x = col*(50+1) + width/2 - 210, y = row*(70+1) + 112
        ImitaterDialog.getSeedPosition(0, ImitaterDialog.IMITATER_DIALOG_WIDTH, p);
        assertEquals(ImitaterDialog.IMITATER_DIALOG_WIDTH / 2 - 210, p.mX);
        assertEquals(112, p.mY);

        ImitaterDialog.getSeedPosition(7, ImitaterDialog.IMITATER_DIALOG_WIDTH, p);
        assertEquals(7 * (GameConstants.SEED_PACKET_WIDTH + 1) + ImitaterDialog.IMITATER_DIALOG_WIDTH / 2 - 210, p.mX);
        assertEquals(112, p.mY);

        ImitaterDialog.getSeedPosition(8, ImitaterDialog.IMITATER_DIALOG_WIDTH, p);
        assertEquals(ImitaterDialog.IMITATER_DIALOG_WIDTH / 2 - 210, p.mX);
        assertEquals(112 + GameConstants.SEED_PACKET_HEIGHT + 1, p.mY);

        // last selectable seed (SEED_GATLINGPEA - 1) is row 4, column 7
        ImitaterDialog.getSeedPosition(SeedType.SEED_GATLINGPEA - 1, ImitaterDialog.IMITATER_DIALOG_WIDTH, p);
        assertEquals(7 * (GameConstants.SEED_PACKET_WIDTH + 1) + ImitaterDialog.IMITATER_DIALOG_WIDTH / 2 - 210, p.mX);
        assertEquals(4 * (GameConstants.SEED_PACKET_HEIGHT + 1) + 112, p.mY);
    }

    @Test
    void choosingATargetPlacesTheImitaterIntoTheChooser() {
        ChosenSeed imitater = new ChosenSeed();
        imitater.mSeedType = SeedType.SEED_IMITATER;
        imitater.mSeedState = ChosenSeedState.SEED_PACKET_HIDDEN;
        imitater.mImitaterType = SeedType.SEED_NONE;
        imitater.mX = SeedChooserScreen.IMITATER_BUTTON_X;
        imitater.mY = SeedChooserScreen.IMITATER_BUTTON_Y;

        ImitaterDialog.chooseImitaterTarget(imitater, SeedType.SEED_REPEATER);

        assertEquals(ChosenSeedState.SEED_IN_CHOOSER, imitater.mSeedState);
        assertEquals(SeedType.SEED_REPEATER, imitater.mImitaterType);
        // chooseImitaterTarget only sets the state; the caller keeps it on the imitater button
        assertEquals(SeedChooserScreen.IMITATER_BUTTON_X, imitater.mX);
        assertEquals(SeedChooserScreen.IMITATER_BUTTON_Y, imitater.mY);
    }

    @Test
    void imitaterFlowKeepsTheCopiedPlantUntilItLeavesTheBank() {
        ChosenSeed imitater = new ChosenSeed();
        imitater.mSeedType = SeedType.SEED_IMITATER;
        imitater.mSeedState = ChosenSeedState.SEED_PACKET_HIDDEN;
        imitater.mImitaterType = SeedType.SEED_NONE;

        ImitaterDialog.chooseImitaterTarget(imitater, SeedType.SEED_TORCHWOOD);

        // selected: flies into the bank as slot 0 and keeps the copied plant while there
        SeedChooserScreen.clickSeedInChooser(imitater, 1, 0);
        assertEquals(ChosenSeedState.SEED_FLYING_TO_BANK, imitater.mSeedState);
        assertEquals(0, imitater.mSeedIndexInBank);
        imitater.mEndX = 500;
        imitater.mEndY = 516;
        assertTrue(SeedChooserScreen.landFlyingSeedState(imitater));
        assertEquals(ChosenSeedState.SEED_IN_BANK, imitater.mSeedState);
        assertEquals(SeedType.SEED_TORCHWOOD, imitater.mImitaterType);

        // deselected: only the landing in the chooser clears the copied plant
        SeedChooserScreen.clickSeedInBank(imitater, 2);
        assertEquals(ChosenSeedState.SEED_FLYING_TO_CHOOSER, imitater.mSeedState);
        assertEquals(SeedType.SEED_TORCHWOOD, imitater.mImitaterType,
                "the copied plant survives the flight out of the bank");
        imitater.mEndX = SeedChooserScreen.IMITATER_BUTTON_X;
        imitater.mEndY = SeedChooserScreen.IMITATER_BUTTON_Y;
        assertTrue(SeedChooserScreen.landFlyingSeedState(imitater));
        assertEquals(ChosenSeedState.SEED_PACKET_HIDDEN, imitater.mSeedState);
        assertEquals(SeedType.SEED_NONE, imitater.mImitaterType);
    }
}
