/*
 * Headless tests for the Board/BoardInput/BoardRender/MessageWidget arms that previously threw
 * UnsupportedOperationException and now run the upstream Zen-Garden / mode logic.
 *
 * These cover the newly-implemented branches called out by the porting tickets:
 *  - Board::CanUseGameObject / GetZenButtonRect           (Board.cpp:1288, :9386)
 *  - Board::CanPlantAt zen branch                          (Board.cpp:2699-2712)
 *  - Board::PixelToGridX/Y, GridToPixelX/Y zen delegation  (Board.cpp:8782-8890)
 *  - Board::LeftFogColumn                                  (Board.cpp:8693-8702)
 *  - Board::PickBackground zen arm                         (Board.cpp:975-978)
 *  - BoardInput::MouseDownWithTool zen routing             (Board.cpp:3993-3997)
 *  - MessageWidget::GetFont style table                    (MessageWidget.cpp:330-358)
 */
package cn.enaium.fabricpvz.portable.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.LawnEntityTestSupport.TestLawnApp;
import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Rect;

class BoardUiGapsTest {

    private static TestLawnApp newZenApp() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN;
        return app;
    }

    /** Builds a board already in the Zen-Garden mode with the garden wired back to it. */
    private static Board newZenBoard(TestLawnApp app) {
        Board board = new Board(app);
        board.mLevel = 0;
        board.mBackground = BackgroundType.BACKGROUND_GREENHOUSE;
        app.mBoard = board;
        app.mZenGarden.mBoard = board;
        app.mZenGarden.mGardenType = GardenType.GARDEN_MAIN;
        return board;
    }

    // -------------------------------------------------------------------------------------
    // Board::CanUseGameObject (Board.cpp:9386)
    // -------------------------------------------------------------------------------------

    @Test
    void canUseGameObjectTreeOfWisdomOnlyAllowsTreeFoodAndNextGarden() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_TREE_OF_WISDOM;
        Board board = new Board(app);

        assertTrue(board.canUseGameObject(GameObjectType.OBJECT_TYPE_TREE_FOOD));
        assertTrue(board.canUseGameObject(GameObjectType.OBJECT_TYPE_NEXT_GARDEN));
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_WATERING_CAN));
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_FERTILIZER));
    }

    @Test
    void canUseGameObjectOutsideZenIsAlwaysFalse() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        Board board = new Board(app);

        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_WATERING_CAN));
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_NEXT_GARDEN));
    }

    @Test
    void canUseGameObjectZenGatesOnPurchases() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);

        // Watering can is always usable in the garden (Board.cpp:9404).
        assertTrue(board.canUseGameObject(GameObjectType.OBJECT_TYPE_WATERING_CAN));
        // Tree food is not usable in the garden (Board.cpp:9419).
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_TREE_FOOD));
        // Next garden needs one of the three garden purchases (Board.cpp:9406-9409).
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_NEXT_GARDEN));
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_MUSHROOM_GARDEN] = 1;
        assertTrue(board.canUseGameObject(GameObjectType.OBJECT_TYPE_NEXT_GARDEN));
        // Fertilizer uses the charge count (Board.cpp:9410-9411).
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_FERTILIZER));
        app.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 3;
        assertTrue(board.canUseGameObject(GameObjectType.OBJECT_TYPE_FERTILIZER));
        // Money sign needs the finished adventure (Board.cpp:9418).
        assertFalse(board.canUseGameObject(GameObjectType.OBJECT_TYPE_MONEY_SIGN));
    }

    // -------------------------------------------------------------------------------------
    // Board::GetZenButtonRect (Board.cpp:1288)
    // -------------------------------------------------------------------------------------

    @Test
    void getZenButtonRectReturnsEarlyForTreeOfWisdom() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_TREE_OF_WISDOM;
        Board board = new Board(app);
        Rect rect = new Rect(999, 12, 3, 4);

        board.getZenButtonRect(GameObjectType.OBJECT_TYPE_WATERING_CAN, rect);

        // Board.cpp:1293-1296: only the X is reset, then it returns for Tree of Wisdom.
        assertEquals(30, rect.mX);
        assertEquals(12, rect.mY);
    }

    @Test
    void getZenButtonRectLaysOutUsedToolsLeftToRight() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);
        // Only the watering can is available: the "all usable" shortcut must not apply,
        // and the first tool lands at X = 0 (Board.cpp:1298-1305).
        Rect rect = new Rect(999, 0, 0, 0);
        board.getZenButtonRect(GameObjectType.OBJECT_TYPE_WATERING_CAN, rect);
        assertEquals(30, rect.mX);

        int shovelWidth = Resources.getImageById(ImageID.IMAGE_SHOVELBANK).getWidth();
        // The wheelbarrow button sits after each usable tool before it (only the can here).
        Rect wheelbarrowRect = new Rect(999, 0, 0, 0);
        board.getZenButtonRect(GameObjectType.OBJECT_TYPE_WHEELBARROW, wheelbarrowRect);
        assertEquals(30 + shovelWidth, wheelbarrowRect.mX);
    }

    // -------------------------------------------------------------------------------------
    // Board::CanPlantAt zen branch (Board.cpp:2699-2712)
    // -------------------------------------------------------------------------------------

    @Test
    void canPlantAtZenAcceptsEmptyGardenSpot() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);

        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(2, 2, SeedType.SEED_PEASHOOTER));
    }

    @Test
    void canPlantAtZenAquariumRejectsLandPlant() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);
        app.mZenGarden.mGardenType = GardenType.GARDEN_AQUARIUM;

        assertEquals(PlantingReason.PLANTING_NOT_ON_WATER, board.canPlantAt(2, 2, SeedType.SEED_PEASHOOTER));
        // Aquatic plants are accepted (Plant::IsAquatic).
        assertEquals(PlantingReason.PLANTING_OK, board.canPlantAt(2, 2, SeedType.SEED_LILYPAD));
    }

    // -------------------------------------------------------------------------------------
    // Board::PixelToGridX/Y + GridToPixelX/Y zen delegation (Board.cpp:8782-8890)
    // -------------------------------------------------------------------------------------

    @Test
    void zenPixelGridMappingDelegatesToTheGarden() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);

        ZenGarden.SpecialGridPlacement placement = app.mZenGarden.getSpecialGridPlacements()[0];
        int pixelX = placement.mPixelX;
        int pixelY = placement.mPixelY;

        assertEquals(app.mZenGarden.pixelToGridX(pixelX + 1, pixelY + 1), board.pixelToGridX(pixelX + 1, pixelY + 1));
        assertEquals(app.mZenGarden.pixelToGridY(pixelX + 1, pixelY + 1), board.pixelToGridY(pixelX + 1, pixelY + 1));
        assertEquals(app.mZenGarden.gridToPixelX(placement.mGridX, placement.mGridY),
                board.gridToPixelX(placement.mGridX, placement.mGridY));
        assertEquals(app.mZenGarden.gridToPixelY(placement.mGridX, placement.mGridY),
                board.gridToPixelY(placement.mGridX, placement.mGridY));
        // Round trip through the garden placement: a point inside the cell maps back to its grid.
        assertEquals(placement.mGridX, board.pixelToGridX(pixelX + 1, pixelY + 1));
        assertEquals(placement.mGridY, board.pixelToGridY(pixelX + 1, pixelY + 1));
    }

    @Test
    void nonZenPixelGridMappingUsesTheLinearLawnFormula() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        Board board = new Board(app);
        board.mBackground = BackgroundType.BACKGROUND_1_DAY;

        // Board.cpp:8791-8797 for X, :8896-8908 for Y (dirt row spacing 100).
        int x = GameConstants.LAWN_XMIN + 3 * 80 + 5;
        assertEquals(3, board.pixelToGridX(x, 0));
        int y = GameConstants.LAWN_YMIN + 2 * 100 + 5;
        assertEquals(2, board.pixelToGridY(x, y));
        assertEquals(3 * 80 + GameConstants.LAWN_XMIN, board.gridToPixelX(3, 2));
        assertEquals(2 * 100 + GameConstants.LAWN_YMIN, board.gridToPixelY(3, 2));
    }

    // -------------------------------------------------------------------------------------
    // Board::LeftFogColumn (Board.cpp:8693-8702)
    // -------------------------------------------------------------------------------------

    @Test
    void leftFogColumnMatchesTheUpstreamTable() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        app.mGameMode = GameMode.GAMEMODE_ADVENTURE;
        Board board = new Board(app);

        board.mLevel = 31;
        assertEquals(6, board.leftFogColumn());
        board.mLevel = 34;
        assertEquals(5, board.leftFogColumn());
        board.mLevel = 38;
        assertEquals(4, board.leftFogColumn());

        // Air-raid is always column 6, before the adventure checks (Board.cpp:8695).
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_AIR_RAID;
        board.mLevel = 0;
        assertEquals(6, board.leftFogColumn());

        // Any non-adventure mode defaults to 5 (Board.cpp:8696).
        app.mGameMode = GameMode.GAMEMODE_CHALLENGE_COLUMN;
        assertEquals(5, board.leftFogColumn());
    }

    // -------------------------------------------------------------------------------------
    // Board::PickBackground zen arm (Board.cpp:975-978)
    // -------------------------------------------------------------------------------------

    @Test
    void pickBackgroundZenGivesGreenhouse() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);
        board.mBackground = BackgroundType.BACKGROUND_1_DAY;

        board.pickBackground();

        assertEquals(BackgroundType.BACKGROUND_GREENHOUSE, board.mBackground);
    }

    // -------------------------------------------------------------------------------------
    // BoardInput::MouseDownWithTool zen routing (Board.cpp:3993-3997)
    // -------------------------------------------------------------------------------------

    @Test
    void mouseDownWithToolInZenGardenRoutesToTheGarden() {
        TestLawnApp app = newZenApp();
        Board board = newZenBoard(app);
        board.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_SHOVEL;

        // Previously threw "ZenGarden is not ported"; now the empty garden just drops the tool.
        BoardInput.mouseDownWithTool(board, 100, 100, 1, CursorType.CURSOR_TYPE_SHOVEL);

        assertTrue(app.mFoleysPlayed.contains(FoleyType.FOLEY_DROP));
        assertEquals(CursorType.CURSOR_TYPE_NORMAL, board.mCursorObject.mCursorType);
    }

    // -------------------------------------------------------------------------------------
    // MessageWidget::GetFont style table (MessageWidget.cpp:330-358)
    // -------------------------------------------------------------------------------------

    @Test
    void messageWidgetFontTableMatchesTheUpstreamStyles() {
        TestLawnApp app = LawnEntityTestSupport.newApp();
        Board board = LawnEntityTestSupport.newBoard(app);
        app.mBoard = board;
        MessageWidget widget = new MessageWidget(app);

        int[] bigStyles = {
                MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1,
                MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2,
                MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER,
                MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER_STAY,
                MessageStyle.MESSAGE_STYLE_HINT_LONG,
                MessageStyle.MESSAGE_STYLE_HINT_FAST,
                MessageStyle.MESSAGE_STYLE_HINT_STAY,
                MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST,
                MessageStyle.MESSAGE_STYLE_HINT_TALL_UNLOCKMESSAGE,
                MessageStyle.MESSAGE_STYLE_HINT_TALL_LONG,
                MessageStyle.MESSAGE_STYLE_BIG_MIDDLE,
                MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST,
                MessageStyle.MESSAGE_STYLE_HOUSE_NAME,
                MessageStyle.MESSAGE_STYLE_HUGE_WAVE,
                MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG,
                MessageStyle.MESSAGE_STYLE_ACHIEVEMENT,
        };
        for (int style : bigStyles) {
            widget.mMessageStyle = style;
            assertSame(Resources.getFontById(Resources.FONT_HOUSEOFTERROR28_ID), widget.getFont(),
                    "style " + style);
        }

        widget.mMessageStyle = MessageStyle.MESSAGE_STYLE_SLOT_MACHINE;
        assertSame(Resources.getFontById(Resources.FONT_HOUSEOFTERROR16_ID), widget.getFont());
    }
}
