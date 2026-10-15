/*
 * Regression test for the Wall-nut Bowling (adventure 1-5) crash: Crazy Dave's speech bubble is
 * drawn inside {@code SexyAppBase::DrawDirtyStuff}'s {@code MTAutoDisallowRand} guard, and the
 * {SHAKE} jitter must therefore not call {@code MTRand}.
 *
 * <p>Upstream shakes the bubble with libc {@code rand() % 2} (LawnApp.cpp:2917-2918) — a stream that
 * {@code MTRand::SetRandAllowed} does not guard (MTRand.cpp:75-88) and that is seeded separately
 * with {@code srand(SDL_GetTicks())} (SexyAppBase.cpp:3454). Routing that through
 * {@code Common.rand}/{@code Sexy::Rand} (MTRand) trips {@code DBG_ASSERT(gRandAllowed==0)}
 * (MTRand.cpp:140) on the first drawn frame whose text carries {SHAKE} — the bowling shovel
 * tutorial's "Because I'm CRAAAZY!!!!!" line.
 *
 * <p>Every value asserted here is quoted from the upstream source line noted next to it, or from
 * the retail text in LawnStrings.txt. The suite is headless (no window, no retail assets): stub
 * images/fonts come from {@link cn.enaium.fabricpvz.portable.test.HeadlessTestApp}, and the
 * app double records audio/particles.
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

class CrazyDaveShakeDrawTest {

    /**
     * Wall-nut Bowling's "because I'm crazy" line, quoted from LawnStrings.txt
     * ({@code [CRAZY_DAVE_2414] = {SHAKE}Because I'm CRAAAZY!!!!!{MOUTH_BIG_SMILE}}).
     */
    private static final String CRAZY_DAVE_2414 = "{SHAKE}Because I'm CRAAAZY!!!!!{MOUTH_BIG_SMILE}";

    /** Adventure 1-5 is Wall-nut Bowling ({@code LawnApp::IsWallnutBowlingLevel}, LawnApp.java:1884). */
    private static final int BOWLING_LEVEL = 5;

    /**
     * Headless adventure app that resolves Dave's lines from the retail strings the way
     * {@code LawnApp::GetCrazyDaveText} would (LawnApp.cpp:2374); no asset pak is loaded in a test.
     */
    private static final class BowlingApp extends LawnEntityTestSupport.TestLawnApp {
        @Override
        public String getCrazyDaveText(int theMessageIndex) {
            if (theMessageIndex == 2414) {
                return CRAZY_DAVE_2414;
            }
            return "CRAZY_DAVE_" + theMessageIndex;
        }
    }

    /** First-play-through adventure 1-5 app + board: the retail shovel-tutorial board. */
    private static BowlingApp newBowlingApp() {
        Common.sRand(0x5EED);
        BowlingApp app = new BowlingApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        app.mGameScene = GameScenes.SCENE_LEVEL_INTRO;
        app.mAppRandSeed = 0x5EED;
        // Everything LawnApp::Init() and the loading thread would create (profile, effect system,
        // audio facades, the definition tables and stub images/fonts).
        cn.enaium.fabricpvz.portable.test.HeadlessTestApp.initialize(app);
        app.mPlayerInfo.mLevel = BOWLING_LEVEL;
        app.mPlayerInfo.mFinishedAdventure = 0;
        Board board = new Board(app);
        board.initLevel();
        return app;
    }

    /** Runs {@code theDraw} exactly the way {@code SexyAppBase::DrawDirtyStuff} does (SexyAppBase.cpp:1778). */
    private static void drawInsideTheRandGuard(Runnable theDraw) {
        MTRand.setRandAllowed(false);
        try {
            theDraw.run();
        } finally {
            MTRand.setRandAllowed(true);
        }
    }

    /** The grid centres of every live peashooter, as pixel coordinates for the shovel tool. */
    private static List<int[]> peashooterCentres(Board theBoard) {
        List<int[]> aCentres = new ArrayList<>();
        for (Plant aPlant : theBoard.mPlants) {
            if (!aPlant.mDead && aPlant.mSeedType == SeedType.SEED_PEASHOOTER) {
                aCentres.add(new int[] {
                    theBoard.gridToPixelX(aPlant.mPlantCol, aPlant.mRow) + 40,
                    theBoard.gridToPixelY(aPlant.mPlantCol, aPlant.mRow) + 40,
                });
            }
        }
        return aCentres;
    }

    // -------------------------------------------------------------------------------------
    // The reported crash: bowling -> shovel the three peashooters -> Dave's {SHAKE} line
    // -------------------------------------------------------------------------------------

    /**
     * The exact reported repro. First-time adventure 1-5 (Wall-nut Bowling) starts with three
     * pre-planted peashooters (Challenge.cpp:170-172 {@code NewPlant(5,1)/(7,2)/(6,3)}); the shovel
     * tutorial ends when the last one is dug up (Board.cpp:4019), which resumes Dave's dialog at
     * 2410 (Board.cpp:5615-5617) and reaches the {SHAKE} line 2414. That frame is drawn by
     * {@code BoardRender::drawUITop} -> {@code LawnApp::DrawCrazyDave} (BoardRender.java:1381)
     * inside the rand guard, which is where the assertion fired.
     */
    @Test
    void bowlingShovelTutorialDrawsDavesShakeBubbleInsideTheRandGuard() {
        BowlingApp app = newBowlingApp();
        Board board = app.mBoard;

        // Challenge.cpp:170-172: the three peashooters the tutorial asks the player to shovel.
        assertEquals(3, board.countPlantByType(SeedType.SEED_PEASHOOTER),
                "1-5 begins with three peashooters (Challenge.cpp:170-172)");
        List<int[]> aCentres = peashooterCentres(board);

        // Dave is on screen for the whole intro (LawnApp::CrazyDaveEnter) and line 2414 is the
        // message whose bubble shakes.
        app.crazyDaveEnter();
        board.setTutorialState(TutorialState.TUTORIAL_SHOVEL_PICKUP);
        BoardInput.pickUpTool(board, GameObjectType.OBJECT_TYPE_SHOVEL);
        assertEquals(TutorialState.TUTORIAL_SHOVEL_DIG, board.mTutorialState,
                "picking the shovel starts the digging step (Board.cpp:4265-4266)");

        for (int[] aCentre : aCentres) {
            BoardInput.mouseDownWithTool(board, aCentre[0], aCentre[1], 1, CursorType.CURSOR_TYPE_SHOVEL);
        }

        assertEquals(0, board.countPlantByType(SeedType.SEED_PEASHOOTER), "all three were dug up");
        assertEquals(TutorialState.TUTORIAL_SHOVEL_COMPLETED, board.mTutorialState,
                "the last shovel completes the tutorial (Board.cpp:4019)");
        assertEquals(2410, board.mCutScene.mCrazyDaveDialogStart,
                "completion resumes Dave's dialog at 2410 (Board.cpp:5616)");
        assertEquals(3, board.mPlantsShoveled);

        // Dave keeps talking while the level intro is drawn: 2411 -> 2414 is "We're going BOWLING!"
        // .. "{SHAKE}Because I'm CRAAAZY!!!!!".
        app.crazyDaveTalkIndex(2414);
        assertEquals(CRAZY_DAVE_2414, app.mCrazyDaveMessageText);
        app.mCrazyDaveState = CrazyDaveState.CRAZY_DAVE_TALKING;

        // The frame that crashed: the board's draw pass runs inside the rand guard.
        Graphics aGraphics = new Graphics();
        assertDoesNotThrow(() -> drawInsideTheRandGuard(() -> board.draw(aGraphics)),
                "Board::Draw -> Board::DrawUITop -> LawnApp::DrawCrazyDave must not call MTRand "
                        + "while the draw pass disallows it (MTRand.cpp:140 / MTRand.java:160)");
    }

    // -------------------------------------------------------------------------------------
    // Why it crashed: the bubble jitter is not part of the gameplay MTRand stream
    // -------------------------------------------------------------------------------------

    /**
     * The draw-time jitter must neither assert nor advance the shared MTRand stream: upstream's
     * libc {@code rand()} is a different generator, so the port cannot use the MTRand stream for it
     * without changing every later gameplay random draw.
     */
    @Test
    void shakeBubbleDrawDoesNotConsumeTheGameplayRandStream() {
        BowlingApp app = newBowlingApp();
        app.mBoard.setTutorialState(TutorialState.TUTORIAL_SHOVEL_COMPLETED);
        app.crazyDaveEnter();
        app.crazyDaveTalkIndex(2414);
        assertTrue(app.mCrazyDaveMessageText.contains("{SHAKE}"));

        Common.sRand(0x5EED);
        MTRand aReference = new MTRand(0x5EED);
        assertEquals(aReference.nextNoAssert(), Common.rand(), "both streams start from the same seed");

        Graphics aGraphics = new Graphics();
        assertDoesNotThrow(() -> drawInsideTheRandGuard(() -> app.drawCrazyDave(aGraphics)));

        // The next gameplay value is still the reference stream's second one: drawing consumed none.
        assertEquals(aReference.nextNoAssert(), Common.rand(),
                "drawing the {SHAKE} bubble must not consume the shared MTRand stream "
                        + "(upstream used libc rand(), LawnApp.cpp:2917-2918)");
    }

    // -------------------------------------------------------------------------------------
    // The substitute stream itself
    // -------------------------------------------------------------------------------------

    /**
     * The libc-rand stand-in keeps upstream's {@code rand() % 2} contract (LawnApp.cpp:2917-2918):
     * one bit, so the bubble shifts by 0 or 1 pixel, and it is usable while MTRand is disallowed.
     */
    @Test
    void libcRandStandInReturnsUpstreamsZeroOrOneJitterInsideTheGuard() {
        List<Integer> aValues = new ArrayList<>();
        drawInsideTheRandGuard(() -> {
            for (int i = 0; i < 64; i++) {
                aValues.add(Common.libcRand(2));
            }
        });

        assertEquals(64, aValues.size());
        for (int aValue : aValues) {
            assertTrue(aValue == 0 || aValue == 1, "rand() % 2 is 0 or 1, got " + aValue);
        }
        assertTrue(aValues.contains(0) && aValues.contains(1), "both offsets occur: " + aValues);
    }
}
