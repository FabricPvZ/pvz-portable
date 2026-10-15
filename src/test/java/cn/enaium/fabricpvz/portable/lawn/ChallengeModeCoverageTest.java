package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.widget.ChallengeDefinition;
import cn.enaium.fabricpvz.portable.lawn.widget.ChallengeScreen;
import cn.enaium.fabricpvz.portable.pvzp.TestAssets;

/**
 * Starts every one of the 72 {@link ChallengeScreen#gChallengeDefs} game modes plus the non-table
 * modes (adventure, zen garden, tree of wisdom, upsell, intro) in a single session and runs the
 * frame loop for each.
 *
 * <p>This is the completeness gate for the port: every mode must build its board, construct its
 * {@link Challenge} state machine and tick without an unhandled path. Runs headless through SDL's
 * dummy video driver with the software renderer, like {@code GameBootSmokeTest}.
 */
class ChallengeModeCoverageTest {

    @TempDir
    Path tmp;

    private static void tick(LawnApp app, int frames) {
        for (int i = 0; i < frames; i++) {
            app.updateFrames();
        }
    }

    private static LawnApp boot(Path saveDir, Path resourceRoot) throws IOException {
        // The entry point installs the resource extractor (upstream main.cpp); do the same here.
        cn.enaium.fabricpvz.portable.lawn.Main.installHooks();
        LawnApp app = new LawnApp();
        LawnApp.gLawnApp = app;
        app.setArgs(new String[] {
            "pvz-portable", "-resdir", resourceRoot.toString(), "-savedir", saveDir.toString(),
        });
        app.init();
        app.startLoadingThread();

        long deadline = System.nanoTime() + 180_000_000_000L;
        while (!app.mLoadingThreadCompleted.get() && System.nanoTime() < deadline) {
            app.updateFrames();
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(app.mLoadingThreadCompleted.get(), "resource loading must complete");
        PlayerInfo aProfile = app.mProfileMgr.addProfile("ModeCoverage");
        app.mPlayerInfo = aProfile;
        return app;
    }

    @Test
    void everyGameModeStartsAndRuns() throws IOException {
        Optional<Path> resourceRoot = TestAssets.resourceRoot();
        Assumptions.assumeTrue(resourceRoot.isPresent(), "no real PvZ assets available");

        Path saveDir = Files.createDirectories(tmp.resolve("userdata"));
        LawnApp app = boot(saveDir, resourceRoot.get());
        List<String> failures = new ArrayList<>();
        int ranModes = 0;
        try {
            Set<Integer> modes = new LinkedHashSet<>();
            for (ChallengeDefinition aDef : ChallengeScreen.gChallengeDefs) {
                modes.add(aDef.mChallengeMode);
            }
            // Non-table modes that own a board (Board.java:340-380 dispatches them all).
            modes.add(GameMode.GAMEMODE_ADVENTURE);
            modes.add(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN);

            for (int aMode : modes) {
                String aName = modeName(aMode);
                try {
                    app.mGameMode = aMode;
                    app.killBoard();
                    app.preNewGame(aMode, false);
                    Board aBoard = app.mBoard;
                    assertNotNull(aBoard, aName + ": preNewGame must create a board");
                    assertEquals(aMode, app.mGameMode, aName + ": mode must stick");

                    app.startPlaying();
                    assertEquals(GameScenes.SCENE_PLAYING, app.mGameScene, aName + ": must enter SCENE_PLAYING");

                    tick(app, 150);

                    Board aAfter = app.mBoard;
                    assertNotNull(aAfter, aName + ": board must survive 150 ticks");
                    assertNotNull(aAfter.mChallenge, aName + ": challenge state machine must exist");
                    assertTrue(aAfter.mSunMoney >= 0, aName + ": sun must not go negative");
                    assertEquals(BoardResult.BOARDRESULT_NONE, app.mBoardResult,
                            aName + ": no mode may finish from idling for 1.5s");
                    assertTrue(app.mWidgetManager.mWidgets.size() > 0, aName + ": widget tree must stay alive");
                    assertModeSpecific(app, aAfter, aMode, aName);
                    ranModes++;
                } catch (Throwable e) {
                    failures.add(aName + " (mode " + aMode + "): " + e);
                }
            }
            System.out.println("[coverage] started and ran " + ranModes + " game modes");
            assertTrue(failures.isEmpty(), () -> "modes that failed to run:\n  " + String.join("\n  ", failures));
            assertEquals(modes.size(), ranModes, "every table mode plus adventure/zen must have run");
        } finally {
            app.shutdown();
            LawnApp.gLawnApp = null;
        }
    }

    /**
     * Every mode's own subsystem must produce the objects upstream creates for it - this is what
     * proves the formerly unimplemented branches (Zen Garden, Beghouled, Scary Potter, I,Zombie,
     * Portal Combat, Slot Machine, Whack-a-Zombie, Zombiquarium, Squirrel, Tree of Wisdom) really
     * run inside the board update loop.
     */
    private static void assertModeSpecific(LawnApp app, Board theBoard, int theMode, String theName) {
        if (theMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || theMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            assertTrue(theBoard.mPlants.mSize > 0, theName + ": the beghouled board must be filled with plants");
        } else if (theMode >= GameMode.GAMEMODE_SCARY_POTTER_1 && theMode <= GameMode.GAMEMODE_SCARY_POTTER_ENDLESS) {
            assertTrue(countGridItems(theBoard, GridItemType.GRIDITEM_SCARY_POT) > 0,
                    theName + ": scary potter must place pots");
        } else if (theMode >= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1
                && theMode <= GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS) {
            assertTrue(theBoard.mPlants.mSize > 0, theName + ": I,Zombie must pre-place the level's plants");
        } else if (theMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            assertEquals(4, countGridItems(theBoard, GridItemType.GRIDITEM_PORTAL_CIRCLE)
                    + countGridItems(theBoard, GridItemType.GRIDITEM_PORTAL_SQUARE),
                    theName + ": portal combat must place its four portals");
        } else if (theMode == GameMode.GAMEMODE_CHALLENGE_SQUIRREL) {
            assertTrue(countGridItems(theBoard, GridItemType.GRIDITEM_SQUIRREL) >= 7,
                    theName + ": squirrel must place the waiting squirrels");
        } else if (theMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            assertTrue(countZombies(theBoard, ZombieType.ZOMBIE_SNORKEL) > 0,
                    theName + ": zombiquarium must spawn its snorkel fish");
        } else if (theMode == GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE) {
            assertTrue(countGridItems(theBoard, GridItemType.GRIDITEM_GRAVESTONE) > 0,
                    theName + ": whack-a-zombie must raise grave stones");
        } else if (theMode == GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE) {
            int aHandle = theBoard.mChallenge.mReanimChallenge;
            assertNotEquals(ReanimationID.REANIMATIONID_NULL, aHandle,
                    theName + ": the slot machine handle must be created");
            assertNotNull(app.reanimationTryToGet(aHandle), theName + ": the handle reanimation must be alive");
        } else if (theMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            int aCloud = theBoard.mChallenge.mReanimClouds[0];
            assertNotEquals(ReanimationID.REANIMATIONID_NULL, aCloud,
                    theName + ": the tree of wisdom must be constructed");
            assertNotNull(app.reanimationTryToGet(aCloud), theName + ": the tree clouds must be alive");
        } else if (theMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            assertEquals(theBoard, app.mZenGarden.mBoard, theName + ": the garden must adopt the board");
        } else if (theMode == GameMode.GAMEMODE_ADVENTURE) {
            assertTrue(theBoard.mNumWaves > 0, theName + ": adventure level 1 must define waves");
        }
    }

    private static int countGridItems(Board theBoard, int theType) {
        int aCount = 0;
        for (GridItem aItem : theBoard.mGridItems) {
            if (aItem.mGridItemType == theType) {
                aCount++;
            }
        }
        return aCount;
    }

    private static int countZombies(Board theBoard, int theType) {
        int aCount = 0;
        for (Zombie aZombie : theBoard.mZombies) {
            if (aZombie.mZombieType == theType) {
                aCount++;
            }
        }
        return aCount;
    }

    private static String modeName(int theMode) {
        for (ChallengeDefinition aDef : ChallengeScreen.gChallengeDefs) {
            if (aDef.mChallengeMode == theMode) {
                return aDef.mChallengeName;
            }
        }
        return switch (theMode) {
            case GameMode.GAMEMODE_ADVENTURE -> "Adventure";
            case GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN -> "Zen Garden";
            case GameMode.GAMEMODE_TREE_OF_WISDOM -> "Tree of Wisdom";
            default -> "mode " + theMode;
        };
    }
}
