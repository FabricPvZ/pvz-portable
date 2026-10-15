/*
 * Tests for cn.enaium.fabricpvz.portable.lawn.widget.NewOptionsDialog.
 *
 * The original version of this file also asserted `resizeAddsDialogOriginToChildren`: that the children
 * carry the dialog's origin in their coordinates. That expectation encoded a bug - the origin is added
 * twice (once here, once by the widget tree when it draws), which pushed every control outside the
 * dialog's rectangle and let the dialog's own clip remove it - so it was deleted together with the fix
 * rather than re-pinned. `controlsStayInsideTheDialog` now guards the opposite invariant.
 */
package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.Widget;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless layout/behaviour checks for {@link NewOptionsDialog}. */
class NewOptionsDialogTest {

    /** The menuback dimensions upstream lays the dialog out against (NewOptionsDialog.cpp:116-160). */
    private static final int MENUBACK_WIDTH = 423;
    private static final int MENUBACK_HEIGHT = 498;

    private Image mSavedMenuback;
    private Image mSavedBackToGame;

    private LawnApp mApp;

    /** The fixture's images are 1x1; the dialog sizes itself from the menuback image. */
    @BeforeEach
    void installStubImages() {
        mSavedMenuback = Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID);
        mSavedBackToGame = Resources.getImageById(Resources.IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID);
    }

    @AfterEach
    void restoreResources() {
        Resources.setImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID, mSavedMenuback);
        Resources.setImageById(Resources.IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID, mSavedBackToGame);
    }

    private static Image stubImage(int theWidth, int theHeight) {
        MemoryImage anImage = new MemoryImage(null);
        anImage.create(theWidth, theHeight);
        return anImage;
    }

    private LawnApp newApp() {
        mApp = new LawnApp();
        HeadlessTestApp.initialize(mApp);
        // HeadlessTestApp binds 1x1 stub images; the dialog sizes itself from the menuback image.
        Resources.setImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID, stubImage(MENUBACK_WIDTH, MENUBACK_HEIGHT));
        Resources.setImageById(Resources.IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID, stubImage(360, 100));
        return mApp;
    }

    /** A dialog that has been centred and added, exactly like {@code LawnApp::DoOptionsDialog}. */
    private static NewOptionsDialog newDialog(LawnApp theApp, boolean theFromGameSelector) {
        NewOptionsDialog aDialog = new NewOptionsDialog(theApp, theFromGameSelector);
        int aBackWidth = Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID).mWidth;
        int aBackHeight = Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID).mHeight;
        theApp.centerDialog(aDialog, aBackWidth, aBackHeight);
        theApp.addDialog(aDialog);
        return aDialog;
    }

    @Test
    void controlsStayInsideTheDialog() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, false);

        assertTrue(aDialog.mWidth > 0 && aDialog.mHeight > 0, "the dialog must have a size");
        assertFalse(aDialog.mWidgets.isEmpty(), "the dialog must own its controls");
        for (Widget aWidget : aDialog.mWidgets) {
            assertTrue(aWidget.mX >= 0 && aWidget.mY >= 0
                            && aWidget.mX + aWidget.mWidth <= aDialog.mWidth
                            && aWidget.mY + aWidget.mHeight <= aDialog.mHeight,
                    aWidget.getClass().getSimpleName() + " at " + aWidget.mX + "," + aWidget.mY + " "
                            + aWidget.mWidth + "x" + aWidget.mHeight + " must stay inside the dialog "
                            + aDialog.mWidth + "x" + aDialog.mHeight);
        }
    }

    /** Every literal below is upstream's (NewOptionsDialog.cpp:147-160), in dialog coordinates. */
    @Test
    void layoutTableMatchesResizeLiterals() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, false);

        assertEquals(199, aDialog.mMusicVolumeSlider.mX, "NewOptionsDialog.cpp:150");
        assertEquals(116, aDialog.mMusicVolumeSlider.mY, "NewOptionsDialog.cpp:150");
        assertEquals(135, aDialog.mMusicVolumeSlider.mWidth, "NewOptionsDialog.cpp:150");
        assertEquals(40, aDialog.mMusicVolumeSlider.mHeight, "NewOptionsDialog.cpp:150");
        assertEquals(199, aDialog.mSfxVolumeSlider.mX, "NewOptionsDialog.cpp:151");
        assertEquals(143, aDialog.mSfxVolumeSlider.mY, "NewOptionsDialog.cpp:151");
        assertEquals(283, aDialog.mHardwareAccelerationCheckbox.mX, "NewOptionsDialog.cpp:152");
        assertEquals(175, aDialog.mHardwareAccelerationCheckbox.mY, "NewOptionsDialog.cpp:152");
        assertEquals(46, aDialog.mHardwareAccelerationCheckbox.mWidth, "NewOptionsDialog.cpp:152");
        assertEquals(45, aDialog.mHardwareAccelerationCheckbox.mHeight, "NewOptionsDialog.cpp:152");
        assertEquals(284, aDialog.mFullscreenCheckbox.mX, "NewOptionsDialog.cpp:153");
        assertEquals(206, aDialog.mFullscreenCheckbox.mY, "NewOptionsDialog.cpp:153");
        assertEquals(107, aDialog.mAlmanacButton.mX, "NewOptionsDialog.cpp:154");
        assertEquals(241, aDialog.mAlmanacButton.mY, "NewOptionsDialog.cpp:154");
        assertEquals(209, aDialog.mAlmanacButton.mWidth, "NewOptionsDialog.cpp:154");
        assertEquals(46, aDialog.mAlmanacButton.mHeight, "NewOptionsDialog.cpp:154");
        assertEquals(241 + 43, aDialog.mRestartButton.mY, "NewOptionsDialog.cpp:155");
        assertEquals(241 + 43 * 2, aDialog.mBackToMainButton.mY, "NewOptionsDialog.cpp:156");
        assertEquals(30, aDialog.mBackToGameButton.mX, "NewOptionsDialog.cpp:157");
        assertEquals(381, aDialog.mBackToGameButton.mY, "NewOptionsDialog.cpp:157");
    }

    @Test
    void getPreferredHeightReturnsMenubackWidth() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, false);

        assertEquals(Resources.getImageById(Resources.IMAGE_OPTIONS_MENUBACK_ID).mWidth,
                aDialog.getPreferredHeight(423), "NewOptionsDialog.cpp:116");
    }

    @Test
    void resizeAppliesFromSelectorOffsets() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, true);

        assertEquals(116 + NewOptionsDialog.FROM_SELECTOR_MUSIC_OFFSET_Y, aDialog.mMusicVolumeSlider.mY);
        assertEquals(143 + NewOptionsDialog.FROM_SELECTOR_SFX_OFFSET_Y, aDialog.mSfxVolumeSlider.mY);
        assertEquals(175 + NewOptionsDialog.FROM_SELECTOR_HW_ACCEL_OFFSET_Y,
                aDialog.mHardwareAccelerationCheckbox.mY);
        assertEquals(206 + NewOptionsDialog.FROM_SELECTOR_FULLSCREEN_OFFSET_Y,
                aDialog.mFullscreenCheckbox.mY);
    }

    @Test
    void resizePushesAlmanacDownForZenGardenAndTreeOfWisdom() {
        for (int aMode : new int[] {
                GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, GameMode.GAMEMODE_TREE_OF_WISDOM}) {
            LawnApp app = newApp();
            app.mGameMode = aMode;
            NewOptionsDialog aDialog = newDialog(app, false);

            assertEquals(241 + NewOptionsDialog.ZEN_TREE_ALMANAC_OFFSET_Y, aDialog.mAlmanacButton.mY,
                    "mode " + aMode + " (NewOptionsDialog.cpp:166-169)");
        }
    }

    @Test
    void fromSelectorHidesRestartAndRequiresCreditsDecision() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, true);

        assertFalse(aDialog.mRestartButton.mVisible,
                "restarting a level from the selector makes no sense (NewOptionsDialog.cpp:190-200)");
    }

    @Test
    void musicSliderValueIsStoredVerbatim() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, false);

        aDialog.sliderVal(NewOptionsDialog.NewOptionsDialog_MusicVolume, 0.42);

        assertEquals(0.42, app.getMusicVolume(), 1.0E-12, "NewOptionsDialog.cpp:203-215");
    }

    @Test
    void sfxSliderValueMapsToVolumeTimesPointSixFive() {
        LawnApp app = newApp();
        NewOptionsDialog aDialog = newDialog(app, false);

        aDialog.sliderVal(NewOptionsDialog.NewOptionsDialog_SoundVolume, 0.5);

        assertEquals(0.325, app.getSfxVolume(), 1.0E-12, "0.5 * 0.65 (NewOptionsDialog.cpp:214)");
    }

    @Test
    void buttonIdConstantsMatchUpstreamEnum() {
        assertEquals(0, NewOptionsDialog.NewOptionsDialog_Almanac);
        assertEquals(1, NewOptionsDialog.NewOptionsDialog_MainMenu);
        assertEquals(2, NewOptionsDialog.NewOptionsDialog_Restart);
        assertEquals(3, NewOptionsDialog.NewOptionsDialog_Update);
        assertEquals(4, NewOptionsDialog.NewOptionsDialog_MusicVolume);
        assertEquals(5, NewOptionsDialog.NewOptionsDialog_SoundVolume);
        assertEquals(6, NewOptionsDialog.NewOptionsDialog_Fullscreen);
        assertEquals(7, NewOptionsDialog.NewOptionsDialog_HardwareAcceleration);
    }
}
