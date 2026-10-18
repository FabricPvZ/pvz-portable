/*
 * Tests for LawnApp.preNewGame (LawnApp.cpp:337-353).
 *
 * Port-specific guard: this port cannot decode the legacy raw-C++-memory-image save format
 * (SaveGame.LegacySaveFormatException; ../verification/VERIFICATION.md §7), so a legacy game<id>_<mode>.dat
 * that fails to load must NOT be erased by preNewGame - otherwise the user's only copy is destroyed
 * with nothing migrated. The legacy file is erased only after a successful migration inside
 * tryLoadGame (LawnApp.cpp:396-410).
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/** Headless checks for {@code LawnApp.preNewGame}'s save-file handling. */
class LawnAppPreNewGameTest {

    /** Records the file operations preNewGame performs instead of touching the filesystem. */
    private static final class TestApp extends LawnApp {
        final List<String> mErased = new ArrayList<>();
        boolean mLoadSucceeds;
        int mTryLoadGameCount;
        int mNewGameCount;

        @Override
        public boolean tryLoadGame() {
            mTryLoadGameCount++;
            return mLoadSucceeds;
        }

        @Override
        public boolean eraseFile(String theFileName) {
            mErased.add(theFileName);
            return true;
        }

        @Override
        public void newGame() {
            mNewGameCount++;
        }
    }

    private static TestApp newApp() {
        TestApp app = new TestApp();
        HeadlessTestApp.initialize(app);
        return app;
    }

    @Test
    void preNewGameErasesOnlyV4WhenLegacyLoadFails() {
        TestApp app = newApp();
        String aSave = LawnCommon.getSavedGameName(GameMode.GAMEMODE_ADVENTURE, app.mPlayerInfo.mId);
        String aLegacy = LawnCommon.getLegacySavedGameName(GameMode.GAMEMODE_ADVENTURE, app.mPlayerInfo.mId);
        assertFalse(aSave.equals(aLegacy));

        app.preNewGame(GameMode.GAMEMODE_ADVENTURE, true);

        assertTrue(app.mErased.contains(aSave), "the current .v4 save must be erased");
        assertFalse(app.mErased.contains(aLegacy), "the undecodable legacy .dat must be preserved");
        assertEquals(1, app.mNewGameCount);
    }

    @Test
    void preNewGameWithoutLookupStillPreservesLegacy() {
        TestApp app = newApp();
        String aLegacy = LawnCommon.getLegacySavedGameName(GameMode.GAMEMODE_ADVENTURE, app.mPlayerInfo.mId);

        app.preNewGame(GameMode.GAMEMODE_ADVENTURE, false);

        assertEquals(0, app.mTryLoadGameCount);
        assertFalse(app.mErased.contains(aLegacy));
        assertEquals(1, app.mNewGameCount);
    }

    @Test
    void preNewGameErasesNothingWhenLoadSucceeds() {
        TestApp app = newApp();
        app.mLoadSucceeds = true;

        app.preNewGame(GameMode.GAMEMODE_ADVENTURE, true);

        assertTrue(app.mErased.isEmpty());
        assertEquals(0, app.mNewGameCount);
        assertEquals(GameMode.GAMEMODE_ADVENTURE, app.mGameMode);
    }

    @Test
    void legacyNameIsDatAndV4NameIsV4() {
        TestApp app = newApp();
        assertNotNull(app.mPlayerInfo);

        assertTrue(LawnCommon.getSavedGameName(0, app.mPlayerInfo.mId).endsWith(".v4"));
        assertTrue(LawnCommon.getLegacySavedGameName(0, app.mPlayerInfo.mId).endsWith(".dat"));
    }
}
