/*
 * Ported from src/Lawn/Board.cpp:2902-4641, 5481-5629, 7547-8431.
 *
 * Mouse/keyboard input, cursor/tooltip/hover maintenance and the debug cheat key handler of
 * {@code Board}. Upstream these are Board member functions; here they are static helpers whose
 * first parameter is the owning {@link Board}, so Board can delegate to them.
 *
 * <p>Routing of {@code mouseDown}/{@code mouseUp}/{@code updateMousePosition}/{@code updateToolTip}
 * follows Board.cpp exactly: time-stop early return, Challenge first refusal, CutScene, coin pickup,
 * tool cursor, plant cursor, seed packet / plant / shovel glyph, then {@code updateCursor()}.
 *
 * <p>PORT-NOTE: every {@code "[TAG]"} string stays the raw upstream key. ToolTipWidget::SetLabel and
 * MessageWidget::SetMessage translate such keys themselves (ToolTipWidget.cpp:162, MessageWidget.cpp:106),
 * so passing them verbatim is what upstream does. // string key, resolved by LawnApp.getString
 *
 * <p>PORT-NOTE: {@code Widget::MouseMove/MouseDrag/MouseDown/MouseUp} are dropped from the bodies
 * below. Upstream only chains to the widget base bookkeeping (an empty button dispatch for Board),
 * and Board's own overrides already delegate here, so calling it back would recurse.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.system.PlayerInfo;
import cn.enaium.fabricpvz.portable.lawn.system.PottedPlant;
import cn.enaium.fabricpvz.portable.lawn.widget.ReportAchievement;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpDebug;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.sexy.Common;
import cn.enaium.fabricpvz.portable.sexy.KeyCode;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyAppBase;

final class BoardInput {

    private BoardInput() {
    }

    // Board.cpp:2902-3001 (Board::UpdateCursor)
    static void updateCursor(Board b, HitResult theHitResult) {
        int aMouseX = b.mApp.mWidgetManager.mLastMouseX - b.mX;
        int aMouseY = b.mApp.mWidgetManager.mLastMouseY - b.mY;
        boolean aShowFinger = false;
        boolean aShowDrag = false;
        boolean aHideCursor = false;

        if (b.mApp.mSeedChooserScreen != null && b.mApp.mSeedChooserScreen.contains(aMouseX + b.mX, aMouseY + b.mY)) {
            return;
        }

        if (b.mApp.getDialogCount() > 0) {
            return;
        }

        if (b.mPaused || b.mBoardFadeOutCounter >= 0 || b.mTimeStopCounter > 0
                || b.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            b.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
            return;
        }

        HitResult aLocalHitResult = null;
        if (theHitResult == null) {
            aLocalHitResult = new HitResult();
            b.mouseHitTest(aMouseX, aMouseY, aLocalHitResult);
            theHitResult = aLocalHitResult;
        }
        switch (theHitResult.mObjectType) {
        case GameObjectType.OBJECT_TYPE_MENU_BUTTON:
        case GameObjectType.OBJECT_TYPE_STORE_BUTTON:
        case GameObjectType.OBJECT_TYPE_SHOVEL:
        case GameObjectType.OBJECT_TYPE_WATERING_CAN:
        case GameObjectType.OBJECT_TYPE_FERTILIZER:
        case GameObjectType.OBJECT_TYPE_BUG_SPRAY:
        case GameObjectType.OBJECT_TYPE_PHONOGRAPH:
        case GameObjectType.OBJECT_TYPE_CHOCOLATE:
        case GameObjectType.OBJECT_TYPE_GLOVE:
        case GameObjectType.OBJECT_TYPE_MONEY_SIGN:
        case GameObjectType.OBJECT_TYPE_NEXT_GARDEN:
        case GameObjectType.OBJECT_TYPE_WHEELBARROW:
        case GameObjectType.OBJECT_TYPE_SLOT_MACHINE_HANDLE:
        case GameObjectType.OBJECT_TYPE_TREE_FOOD:
        case GameObjectType.OBJECT_TYPE_STINKY:
        case GameObjectType.OBJECT_TYPE_TREE_OF_WISDOM:
        case GameObjectType.OBJECT_TYPE_COIN:
        case GameObjectType.OBJECT_TYPE_PROJECTILE:
            aShowFinger = true;
            break;

        case GameObjectType.OBJECT_TYPE_SEEDPACKET:
            aShowFinger = ((SeedPacket) theHitResult.mObject).canPickUp();
            break;

        case GameObjectType.OBJECT_TYPE_SCARY_POT:
            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL) {
                aShowFinger = true;
            } else if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_HAMMER) {
                aHideCursor = true;
            }
            break;

        case GameObjectType.OBJECT_TYPE_PLANT:
            if ((b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                    || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) && !b.hasLevelAwardDropped()) {
                aShowFinger = true;
            }
            if (((Plant) theHitResult.mObject).mState == PlantState.STATE_COBCANNON_READY) {
                aShowFinger = true;
            }
            break;

        default:
            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_HAMMER) {
                aHideCursor = true;
            }
            break;
        }

        if (b.mChallenge.mBeghouledMouseCapture != 0 || aShowDrag) {
            b.mApp.setCursor(SexyAppBase.CURSOR_DRAGGING);
        } else if (aShowFinger) {
            b.mApp.setCursor(SexyAppBase.CURSOR_HAND);
        } else if (aHideCursor) {
            b.mApp.setCursor(SexyAppBase.CURSOR_NONE);
        } else {
            b.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        }
    }

    // Board.cpp:3003-3007 (Board::MouseMove)
    static void mouseMove(Board b, int x, int y) {
        b.mChallenge.mouseMove(x, y);
    }

    // Board.cpp:3009-3013 (Board::MouseDrag)
    static void mouseDrag(Board b, int x, int y) {
        b.mChallenge.mouseMove(x, y);
    }

    // Board.cpp:3015-3037 (Board::ZombieHitTest)
    static Zombie zombieHitTest(Board b, int theMouseX, int theMouseY) {
        Zombie aRecord = null;
        for (Zombie aZombie : b.mZombies) {
            if (aZombie.mDead || aZombie.isDeadOrDying()) {
                continue;
            }

            // ignore zombotany zombies shown during the level intro
            if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && Zombie.isZombotany(aZombie.mZombieType)) {
                continue;
            }

            if (aZombie.getZombieRect().contains(theMouseX, theMouseY)) {
                if (aRecord == null || aZombie.mY > aRecord.mY) {
                    aRecord = aZombie;
                }
            }
        }

        return aRecord;
    }

    // Board.cpp:3039-3060 (Board::IsPlantInGoldWateringCanRange)
    static boolean isPlantInGoldWateringCanRange(Board b, int theMouseX, int theMouseY, Plant thePlant) {
        /*
        Rect aRect = Rect(theMouseX - 70, theMouseY - 80, 160, 160);
        if (GetTopPlantAt(thePlant->mPlantCol, thePlant->mRow, PlantPriority::TOPPLANT_ZEN_TOOL_ORDER) == thePlant)
        {
            return aRect.Contains(thePlant->mX + 40, thePlant->mY + 40);
        }
        return false;
        */

        int aMinX = theMouseX - 70;
        int aMaxX = theMouseX + 90;
        int aMinY = theMouseY - 80;
        int aMaxY = theMouseY + 80;

        if (b.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow, PlantPriority.TOPPLANT_ZEN_TOOL_ORDER) == thePlant) {
            return thePlant.mX + 40 >= aMinX && thePlant.mX + 40 < aMaxX && thePlant.mY + 40 >= aMinY
                    && thePlant.mY + 40 < aMaxY;
        }
        return false;
    }

    // Board.cpp:3062-3097 (Board::HighlightPlantsForMouse)
    static void highlightPlantsForMouse(Board b, int theMouseX, int theMouseY, HitResult theHitResult) {
        if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WATERING_CAN
                && b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_GOLD_WATERINGCAN] != 0) {
            for (Plant aPlant : b.mPlants) {
                if (aPlant.mDead) {
                    continue;
                }
                if (isPlantInGoldWateringCanRange(b, theMouseX, theMouseY, aPlant)) {
                    aPlant.mHighlighted = true;
                    Plant aFlowerPot = b.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow,
                            PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
                    if (aFlowerPot != null) {
                        aFlowerPot.mHighlighted = true;
                    }
                }
            }
        } else {
            Plant aPlant = theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_PLANT
                    ? toolHitTestHelper(b, theHitResult) : null;
            if (aPlant != null) {
                aPlant.mHighlighted = true;
                if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
                    Plant aFlowerPot = b.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow,
                            PlantPriority.TOPPLANT_ONLY_UNDER_PLANT);
                    if (aFlowerPot != null) {
                        aFlowerPot.mHighlighted = true;
                    }
                }
            }
        }
    }

    // Board.cpp:3099-3193 (Board::UpdateMousePosition)
    static void updateMousePosition(Board b) {
        int aCursorSeedType = b.getSeedTypeInCursor();
        int aMouseX = b.mApp.mWidgetManager.mLastMouseX - b.mX;
        int aMouseY = b.mApp.mWidgetManager.mLastMouseY - b.mY;
        HitResult aHitResult = new HitResult();
        b.mouseHitTest(aMouseX, aMouseY, aHitResult);

        updateCursor(b, aHitResult);
        updateToolTip(b, aHitResult);
        for (Plant aPlant : b.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            aPlant.mHighlighted = false;
        }

        if (b.mApp.isScaryPotterLevel()) {
            for (GridItem aGridItem : b.mGridItems) {
                if (aGridItem.mDead) {
                    continue;
                }
                if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
                    aGridItem.mHighlighted = false;
                }
            }

            if (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_SCARY_POT) {
                GridItem aScaryPot = (GridItem) aHitResult.mObject;
                aScaryPot.mHighlighted = true;
                return;
            }
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            GridItem aStinky = b.mApp.mZenGarden.getStinky();
            if (aStinky != null) {
                aStinky.mHighlighted = aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_STINKY;
            }
        }

        if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_SHOVEL
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WATERING_CAN
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_FERTILIZER
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_BUG_SPRAY
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PHONOGRAPH
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_GLOVE
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_MONEY_SIGN
                || (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WHEEELBARROW
                        && !zenGardenPottedPlantInWheelbarrow(b))) {
            highlightPlantsForMouse(b, aMouseX, aMouseY, aHitResult);
            return;
        }

        // coffee bean and wall-nut first aid highlighting
        if (aCursorSeedType == SeedType.SEED_INSTANT_COFFEE) {
            int aGridX = b.plantingPixelToGridX(b.mApp.mWidgetManager.mLastMouseX,
                    b.mApp.mWidgetManager.mLastMouseY, aCursorSeedType);
            int aGridY = b.plantingPixelToGridY(b.mApp.mWidgetManager.mLastMouseX,
                    b.mApp.mWidgetManager.mLastMouseY, aCursorSeedType);

            Plant aPlant = b.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            if (aPlant != null && aPlant.mIsAsleep && b.canPlantAt(aGridX, aGridY,
                    SeedType.SEED_INSTANT_COFFEE) == PlantingReason.PLANTING_OK) {
                aPlant.mHighlighted = true;
            }
        } else if (aCursorSeedType == SeedType.SEED_WALLNUT || aCursorSeedType == SeedType.SEED_TALLNUT) {
            int aGridX = b.plantingPixelToGridX(b.mApp.mWidgetManager.mLastMouseX,
                    b.mApp.mWidgetManager.mLastMouseY, aCursorSeedType);
            int aGridY = b.plantingPixelToGridY(b.mApp.mWidgetManager.mLastMouseX,
                    b.mApp.mWidgetManager.mLastMouseY, aCursorSeedType);

            Plant aPlant = b.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ONLY_PUMPKIN);
            if (aPlant != null && aPlant.mSeedType == aCursorSeedType && b.canPlantAt(aGridX, aGridY,
                    aCursorSeedType) == PlantingReason.PLANTING_OK) {
                aPlant.mHighlighted = true;
            }
        } else if (aCursorSeedType == SeedType.SEED_PUMPKINSHELL) {
            int aGridX = b.plantingPixelToGridX(b.mApp.mWidgetManager.mLastMouseX,
                    b.mApp.mWidgetManager.mLastMouseY, aCursorSeedType);
            int aGridY = b.plantingPixelToGridY(b.mApp.mWidgetManager.mLastMouseX,
                    b.mApp.mWidgetManager.mLastMouseY, aCursorSeedType);

            Plant aPlant = b.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            if (aPlant != null && aPlant.mSeedType == SeedType.SEED_PUMPKINSHELL && b.canPlantAt(aGridX, aGridY,
                    SeedType.SEED_PUMPKINSHELL) == PlantingReason.PLANTING_OK) {
                aPlant.mHighlighted = true;
            }
        }
    }

    // Board.cpp:3147 (mApp->mZenGarden->GetPottedPlantInWheelbarrow()).
    private static boolean zenGardenPottedPlantInWheelbarrow(Board b) {
        return b.mApp.mZenGarden.getPottedPlantInWheelbarrow() != null;
    }

    // Board.cpp:3195-3562 (Board::UpdateToolTip)
    static void updateToolTip(Board b, HitResult theHitResult) {
        if (!b.mApp.mWidgetManager.mMouseIn || !b.mApp.mActive || b.mTimeStopCounter > 0
                || b.mApp.getDialogCount() > 0 || b.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            b.mToolTip.mVisible = false;
            return;
        }

        int aMouseX = b.mApp.mWidgetManager.mLastMouseX - b.mX;
        int aMouseY = b.mApp.mWidgetManager.mLastMouseY - b.mY;

        if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
            if (!b.mCutScene.mSeedChoosing) {
                b.mToolTip.mVisible = false;
                return;
            }

            if (b.mSeedBank.containsPoint(b.mWidgetManager.mLastMouseX, b.mWidgetManager.mLastMouseY)
                    || b.mApp.mSeedChooserScreen.mAlmanacButton.isMouseOver()
                    || b.mApp.mSeedChooserScreen.mStoreButton.isMouseOver()
                    || b.mApp.mSeedChooserScreen.mImitaterButton.isMouseOver()) {
                b.mToolTip.mVisible = false;
                return;
            }

            Zombie aZombie = zombieHitTest(b, aMouseX, aMouseY);
            if (aZombie == null || aZombie.mFromWave != Zombie.ZOMBIE_WAVE_CUTSCENE) {
                b.mToolTip.mVisible = false;
                return;
            }

            String aZombieName = "[" + Zombie.getZombieDefinition(aZombie.mZombieType).mZombieName + "]";
            b.mToolTip.setTitle(aZombieName);
            if (b.mApp.canShowAlmanac() && aZombie.mZombieType != ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                b.mToolTip.setLabel("[CLICK_TO_VIEW]");
            } else {
                b.mToolTip.setLabel("");
            }
            b.mToolTip.setWarningText("");

            Rect aRect = aZombie.getZombieRect();
            b.mToolTip.mX = aRect.mWidth / 2 + aRect.mX + 5;
            b.mToolTip.mY = aRect.mHeight + aRect.mY - 10;
            if (aZombie.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
                b.mToolTip.mY = aZombie.mY;
            }

            b.mToolTip.mVisible = true;
            b.mToolTip.mCenter = true;

            b.mToolTip.mMinLeft = Resources.getImageById(ImageID.IMAGE_SEEDCHOOSER_BACKGROUND).getWidth();
            if (b.mApp.mSeedChooserScreen.mAlmanacButton.mBtnNoDraw
                    && b.mApp.mSeedChooserScreen.mStoreButton.mBtnNoDraw) {
                b.mToolTip.mMaxBottom = 600;
            } else {
                b.mToolTip.mMaxBottom = 570;
            }
            if (!b.mApp.mSeedChooserScreen.mImitaterButton.mBtnNoDraw) {
                b.mToolTip.calculateSize();
                if (b.mX + b.mToolTip.mX - b.mToolTip.mWidth / 2 < 524) {
                    b.mToolTip.mMaxBottom = 503;
                }
            }

            return;
        }

        if (!canInteractWithBoardButtons(b)) {
            b.mToolTip.mVisible = false;
            return;
        }

        b.mToolTip.mMinLeft = 0;
        b.mToolTip.mMaxBottom = GameConstants.BOARD_HEIGHT;
        b.mToolTip.setTitle("");
        b.mToolTip.setLabel("");
        b.mToolTip.setWarningText("");
        b.mToolTip.mCenter = false;
        if (b.mChallenge.updateToolTip(aMouseX, aMouseY, theHitResult) != 0) {
            return;
        }

        HitResult aLocalHitResult = null;
        if (theHitResult == null) {
            aLocalHitResult = new HitResult();
            b.mouseHitTest(aMouseX, aMouseY, aLocalHitResult);
            theHitResult = aLocalHitResult;
        }

        // string key, resolved by LawnApp.getString
        switch (theHitResult.mObjectType) {
        case GameObjectType.OBJECT_TYPE_SHOVEL: {
            b.mToolTip.setLabel("[SHOVEL_TOOLTIP]");
            Rect aShovelButtonRect = b.getShovelButtonRect();
            b.mToolTip.mX = aShovelButtonRect.mX + 35;
            b.mToolTip.mY = aShovelButtonRect.mY + 72;
            b.mToolTip.mCenter = true;
            b.mToolTip.mVisible = true;
            return;
        }

        case GameObjectType.OBJECT_TYPE_NEXT_GARDEN: {
            b.mToolTip.setLabel("[NEXT_GARDEN_TOOLTIP]");
            Rect aButtonRect = b.getShovelButtonRect();
            b.mToolTip.mX = 599;
            b.mToolTip.mY = aButtonRect.mY + 52;
            b.mToolTip.mCenter = true;
            b.mToolTip.mVisible = true;
            return;
        }

        case GameObjectType.OBJECT_TYPE_WATERING_CAN:
            b.mToolTip.setLabel("[WATERING_CAN_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_FERTILIZER:
            b.mToolTip.setLabel("[FERTILIZER_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_BUG_SPRAY:
            b.mToolTip.setLabel("[BUG_SPRAY_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_PHONOGRAPH:
            b.mToolTip.setLabel("[PHONOGRAPH_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_CHOCOLATE:
            b.mToolTip.setLabel("[CHOCOLATE_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_GLOVE:
            b.mToolTip.setLabel("[GLOVE_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_MONEY_SIGN:
            b.mToolTip.setLabel("[MONEY_SIGN_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_WHEELBARROW:
            b.mToolTip.setLabel("[WHEELBARROW_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_TREE_FOOD:
            b.mToolTip.setLabel("[TREE_FERTILIZER_TOOLTIP]");
            break;
        case GameObjectType.OBJECT_TYPE_SEEDPACKET:
            break;
        default:
            b.mToolTip.mVisible = false;
            return;
        }

        if (theHitResult.mObjectType != GameObjectType.OBJECT_TYPE_SEEDPACKET) {
            Rect aButtonRect = b.getShovelButtonRect();
            b.getZenButtonRect(theHitResult.mObjectType, aButtonRect);
            b.mToolTip.mX = aButtonRect.mX + 35;
            b.mToolTip.mY = aButtonRect.mY + 72;
            b.mToolTip.mCenter = true;
            b.mToolTip.mVisible = true;
            return;
        }

        SeedPacket aSeedPacket = (SeedPacket) theHitResult.mObject;
        int aUseSeedType = aSeedPacket.mPacketType;
        if (aSeedPacket.mPacketType == SeedType.SEED_IMITATER && aSeedPacket.mImitaterType != SeedType.SEED_NONE) {
            aUseSeedType = aSeedPacket.mImitaterType;
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            if (aUseSeedType == SeedType.SEED_REPEATER) {
                b.mToolTip.setLabel("[BEGHOULED_REPEATER_UPGRADE_TOOLTIP]");
            } else if (aUseSeedType == SeedType.SEED_FUMESHROOM) {
                b.mToolTip.setLabel("[BEGHOULED_FUMESHROOM_UPGRADE_TOOLTIP]");
            } else if (aUseSeedType == SeedType.SEED_TALLNUT) {
                b.mToolTip.setLabel("[BEGHOULED_TALLNUT_UPGRADE_TOOLTIP]");
            } else if (aUseSeedType == SeedType.SEED_BEGHOULED_BUTTON_SHUFFLE) {
                b.mToolTip.setLabel("[BEGHOULED_SHUFFLE_TOOLTIP]");
            } else if (aUseSeedType == SeedType.SEED_BEGHOULED_BUTTON_CRATER) {
                b.mToolTip.setLabel("[BEGHOULED_CRATER_TOOLTIP]");
            }
        } else if (aUseSeedType == SeedType.SEED_SLOT_MACHINE_SUN) {
            b.mToolTip.setLabel("[SLOT_MACHINE_SUN_TOOLTIP]");
        } else if (aUseSeedType == SeedType.SEED_SLOT_MACHINE_DIAMOND) {
            b.mToolTip.setLabel("[SLOT_MACHINE_DIAMOND_TOOLTIP]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIQUARIUM_SNORKLE) {
            b.mToolTip.setLabel("[ZOMBIQUARIUM_SNORKEL_TOOLTIP]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIQUARIUM_TROPHY) {
            b.mToolTip.setLabel("[ZOMBIQUARIUM_TROPHY_TOOLTIP]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_NORMAL) {
            b.mToolTip.setLabel("[ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_TRAFFIC_CONE) {
            b.mToolTip.setLabel("[CONEHEAD_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_POLEVAULTER) {
            b.mToolTip.setLabel("[POLE_VAULTING_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_PAIL) {
            b.mToolTip.setLabel("[BUCKETHEAD_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_LADDER) {
            b.mToolTip.setLabel("[LADDER_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_DIGGER) {
            b.mToolTip.setLabel("[DIGGER_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_BUNGEE) {
            b.mToolTip.setLabel("[BUNGEE_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_FOOTBALL) {
            b.mToolTip.setLabel("[FOOTBALL_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_BALLOON) {
            b.mToolTip.setLabel("[BALLOON_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_SCREEN_DOOR) {
            b.mToolTip.setLabel("[SCREEN_DOOR_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBONI) {
            b.mToolTip.setLabel("[ZOMBONI]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_POGO) {
            b.mToolTip.setLabel("[POGO_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_DANCER) {
            b.mToolTip.setLabel("[DANCING_ZOMBIE]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_GARGANTUAR) {
            b.mToolTip.setLabel("[GARGANTUAR]");
        } else if (aUseSeedType == SeedType.SEED_ZOMBIE_IMP) {
            b.mToolTip.setLabel("[IMP]");
        } else {
            b.mToolTip.setLabel(Plant.getNameString(aSeedPacket.mPacketType, aSeedPacket.mImitaterType));
        }

        int aPlantCost = b.getCurrentPlantCost(aSeedPacket.mPacketType, aSeedPacket.mImitaterType);
        if (b.mApp.mEasyPlantingCheat) {
            b.mToolTip.setWarningText("FREE_PLANTING_CHEAT");
        } else if (!aSeedPacket.mActive && (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST)) {
            if (aSeedPacket.mPacketType == SeedType.SEED_BEGHOULED_BUTTON_CRATER) {
                b.mToolTip.setWarningText("[BEGHOULED_NO_CRATERS]");
            } else {
                b.mToolTip.setWarningText("[BEGHOULED_SEED_ALREADY_PURCHASED]");
            }
        } else if (!aSeedPacket.mActive) {
            b.mToolTip.setWarningText("[WAITING_FOR_SEED]");
        } else if (!b.canTakeSunMoney(aPlantCost) && !b.hasConveyorBeltSeedBank()
                && !b.mApp.isSlotMachineLevel()) {
            b.mToolTip.setWarningText("[NOT_ENOUGH_SUN]");
        } else if (aUseSeedType == SeedType.SEED_GATLINGPEA) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_REPEATER]");
            }
        } else if (aUseSeedType == SeedType.SEED_WINTERMELON) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_MELONPULT]");
            }
        } else if (aUseSeedType == SeedType.SEED_TWINSUNFLOWER) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_SUNFLOWER]");
            }
        } else if (aUseSeedType == SeedType.SEED_SPIKEROCK) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_SPIKEWEED]");
            }
        } else if (aUseSeedType == SeedType.SEED_COBCANNON) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_KERNELPULTS]");
            }
        } else if (aUseSeedType == SeedType.SEED_GOLD_MAGNET) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_MAGNETSHROOM]");
            }
        } else if (aUseSeedType == SeedType.SEED_GLOOMSHROOM) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_FUMESHROOM]");
            }
        } else if (aUseSeedType == SeedType.SEED_CATTAIL) {
            if (!b.plantingRequirementsMet(aUseSeedType)) {
                b.mToolTip.setWarningText("[REQUIRES_LILY_PAD]");
            }
        }

        b.mToolTip.mX = (GameConstants.SEED_PACKET_WIDTH - b.mToolTip.mWidth) / 2 + b.mSeedBank.mX
                + aSeedPacket.mOffsetX + aSeedPacket.mX;
        b.mToolTip.mY = b.mSeedBank.mY + aSeedPacket.mY + 70;
        b.mToolTip.mVisible = true;
    }

    // Board.cpp:3564-3583 (Board::MouseDownCobcannonFire)
    static void mouseDownCobcannonFire(Board b, int x, int y, int theClickCount) {
        if (theClickCount >= 0 && y >= 80) {
            if (b.mCobCannonCursorDelayCounter > 0
                    && PvzpCommon.distance2D(x, y, b.mCobCannonMouseX, b.mCobCannonMouseY) < 100.0f) {
                // misclick guard: ignore clicks within 100px of the crosshair while the 30cs fire delay is active
                return;
            }

            if (b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_PLANT_FROM_DUPLICATOR) {
                Plant aCobcannon = b.mPlants.dataArrayTryToGet(b.mCursorObject.mCobCannonPlantID);
                if (aCobcannon != null) {
                    aCobcannon.cobCannonFire(x, y);
                }
            }
        }
        clearCursor(b);
    }

    // Board.cpp:3585-3950 (Board::MouseDownWithPlant)
    static void mouseDownWithPlant(Board b, int x, int y, int theClickCount) {
        if (theClickCount < 0) {
            b.refreshSeedPacketFromCursor();
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            return;
        }

        if (b.mApp.isIZombieLevel()) {
            b.mChallenge.iZombieMouseDownWithZombie(x, y, theClickCount);
            return;
        }

        int aPlantingSeedType = b.getSeedTypeInCursor();
        int aGridX = b.plantingPixelToGridX(x, y, aPlantingSeedType);
        int aGridY = b.plantingPixelToGridY(x, y, aPlantingSeedType);

        if (aGridX < 0 || aGridX >= Board.MAX_GRID_SIZE_X || aGridY < 0 || aGridY > Board.MAX_GRID_SIZE_Y) {
            b.refreshSeedPacketFromCursor();
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            return;
        }

        int aReason = b.canPlantAt(aGridX, aGridY, aPlantingSeedType);
        if (aReason != PlantingReason.PLANTING_OK) {
            // string key, resolved by LawnApp.getString
            if (aReason == PlantingReason.PLANTING_ONLY_ON_GRAVES) {
                b.displayAdvice("[ADVICE_GRAVEBUSTERS_ON_GRAVES]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_PLANT_GRAVEBUSTERS_ON_GRAVES);
            } else if (aPlantingSeedType == SeedType.SEED_LILYPAD) {
                if (aReason == PlantingReason.PLANTING_ONLY_IN_POOL) {
                    b.displayAdvice("[ADVICE_LILYPAD_ON_WATER]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_LILYPAD_ON_WATER);
                }
            } else if (aPlantingSeedType == SeedType.SEED_TANGLEKELP) {
                if (aReason == PlantingReason.PLANTING_ONLY_IN_POOL) {
                    b.displayAdvice("[ADVICE_TANGLEKELP_ON_WATER]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_TANGLEKELP_ON_WATER);
                }
            } else if (aPlantingSeedType == SeedType.SEED_SEASHROOM) {
                if (aReason == PlantingReason.PLANTING_ONLY_IN_POOL) {
                    b.displayAdvice("[ADVICE_SEASHROOM_ON_WATER]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_SEASHROOM_ON_WATER);
                }
            } else if (aReason == PlantingReason.PLANTING_ONLY_ON_GROUND) {
                b.displayAdvice("[ADVICE_POTATO_MINE_ON_LILY]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_PLANT_POTATOE_MINE_ON_LILY);
            } else if (aReason == PlantingReason.PLANTING_NOT_PASSED_LINE) {
                b.displayAdvice("[ADVICE_NOT_PASSED_LINE]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_PLANT_NOT_PASSED_LINE);
            } else if (aReason == PlantingReason.PLANTING_NEEDS_UPGRADE) {
                switch (aPlantingSeedType) {
                case SeedType.SEED_GATLINGPEA:
                    b.displayAdvice("[ADVICE_ONLY_ON_REPEATERS]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_REPEATERS);
                    break;

                case SeedType.SEED_TWINSUNFLOWER:
                    b.displayAdvice("[ADVICE_ONLY_ON_SUNFLOWER]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_SUNFLOWER);
                    break;

                case SeedType.SEED_GLOOMSHROOM:
                    b.displayAdvice("[ADVICE_ONLY_ON_FUMESHROOM]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_FUMESHROOM);
                    break;

                case SeedType.SEED_CATTAIL:
                    b.displayAdvice("[ADVICE_ONLY_ON_LILYPAD]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_LILYPAD);
                    break;

                case SeedType.SEED_WINTERMELON:
                    b.displayAdvice("[ADVICE_ONLY_ON_MELONPULT]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_MELONPULT);
                    break;

                case SeedType.SEED_GOLD_MAGNET:
                    b.displayAdvice("[ADVICE_ONLY_ON_MAGNETSHROOM]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_MAGNETSHROOM);
                    break;

                case SeedType.SEED_SPIKEROCK:
                    b.displayAdvice("[ADVICE_ONLY_ON_SPIKEWEED]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_SPIKEWEED);
                    break;

                case SeedType.SEED_COBCANNON:
                    b.displayAdvice("[ADVICE_ONLY_ON_KERNELPULT]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_ONLY_ON_KERNELPULT);
                    break;
                default:
                    break;
                }
            } else if (aReason == PlantingReason.PLANTING_NOT_ON_ART) {
                String aSeedName = Plant.getNameString(b.mChallenge.getArtChallengeSeed(aGridX, aGridY),
                        SeedType.SEED_NONE);
                String aMessage = PvzpCommon.pvzpReplaceString("[ADVICE_WRONG_ART_TYPE]", "{SEED}", aSeedName);
                b.displayAdvice(aMessage, MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_PLANT_WRONG_ART_TYPE);
            } else if (aReason == PlantingReason.PLANTING_NEEDS_POT) {
                if (b.mApp.isFirstTimeAdventureMode() && b.mLevel == 41) {
                    b.displayAdvice("[ADVICE_PLANT_NEED_POT1]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_NEED_POT);
                } else {
                    b.displayAdvice("[ADVICE_PLANT_NEED_POT2]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_NEED_POT);
                }
            } else if (aReason == PlantingReason.PLANTING_NOT_ON_GRAVE) {
                b.displayAdvice("[ADVICE_PLANT_NOT_ON_GRAVE]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_PLANT_NOT_ON_GRAVE);
            } else if (aReason == PlantingReason.PLANTING_NOT_ON_CRATER) {
                if (b.isPoolSquare(aGridX, aGridY)) {
                    b.displayAdvice("[ADVICE_CANT_PLANT_THERE]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_CANT_PLANT_THERE);
                } else {
                    b.displayAdvice("[ADVICE_PLANT_NOT_ON_CRATER]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_NOT_ON_CRATER);
                }
            } else if (aReason == PlantingReason.PLANTING_NOT_ON_WATER) {
                if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                        && b.mApp.mZenGarden.mGardenType == GardenType.GARDEN_AQUARIUM) {
                    b.displayAdvice("[ZEN_ONLY_AQUATIC_PLANTS]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_NONE);
                } else if (aPlantingSeedType == SeedType.SEED_POTATOMINE) {
                    b.displayAdvice("[ADVICE_POTATO_MINE_ON_LILY]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_POTATOE_MINE_ON_LILY);
                } else {
                    b.displayAdvice("[ADVICE_PLANT_NOT_ON_WATER]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                            AdviceType.ADVICE_PLANT_NOT_ON_WATER);
                }
            } else if (aReason == PlantingReason.PLANTING_NEEDS_GROUND) {
                b.displayAdvice("[ADVICE_PLANTING_NEEDS_GROUND]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_PLANTING_NEEDS_GROUND);
            } else if (aReason == PlantingReason.PLANTING_NEEDS_SLEEPING) {
                b.displayAdvice("[ADVICE_PLANTING_NEED_SLEEPING]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_PLANTING_NEED_SLEEPING);
            }

            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE
                    || b.mApp.isWhackAZombieLevel()) {
                b.refreshSeedPacketFromCursor();
                b.mApp.playFoley(FoleyType.FOLEY_DROP);
            }
            return;
        }

        b.clearAdvice(AdviceType.ADVICE_PLANTING_NEED_SLEEPING);
        b.clearAdvice(AdviceType.ADVICE_CANT_PLANT_THERE);
        b.clearAdvice(AdviceType.ADVICE_PLANTING_NEEDS_GROUND);
        b.clearAdvice(AdviceType.ADVICE_PLANT_NOT_ON_WATER);
        b.clearAdvice(AdviceType.ADVICE_PLANT_NOT_ON_CRATER);
        b.clearAdvice(AdviceType.ADVICE_PLANT_NOT_ON_GRAVE);
        b.clearAdvice(AdviceType.ADVICE_PLANT_NEED_POT);
        b.clearAdvice(AdviceType.ADVICE_PLANT_WRONG_ART_TYPE);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_LILYPAD);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_MAGNETSHROOM);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_FUMESHROOM);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_KERNELPULT);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_SUNFLOWER);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_SPIKEWEED);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_MELONPULT);
        b.clearAdvice(AdviceType.ADVICE_PLANT_ONLY_ON_REPEATERS);
        b.clearAdvice(AdviceType.ADVICE_PLANT_NOT_PASSED_LINE);
        b.clearAdvice(AdviceType.ADVICE_PLANT_GRAVEBUSTERS_ON_GRAVES);
        b.clearAdvice(AdviceType.ADVICE_PLANT_LILYPAD_ON_WATER);
        b.clearAdvice(AdviceType.ADVICE_PLANT_TANGLEKELP_ON_WATER);
        b.clearAdvice(AdviceType.ADVICE_PLANT_SEASHROOM_ON_WATER);
        b.clearAdvice(AdviceType.ADVICE_PLANT_POTATOE_MINE_ON_LILY);
        b.clearAdvice(AdviceType.ADVICE_SURVIVE_FLAGS);

        if (!b.mApp.mEasyPlantingCheat && b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_BANK
                && !b.hasConveyorBeltSeedBank()) {
            if (!b.takeSunMoney(b.getCurrentPlantCost(aPlantingSeedType, SeedType.SEED_NONE))) {
                return;
            }
        }

        // upgrades and wall-nut first aid replace the original plant
        boolean aIsAwake = false;
        int aWakeUpCounter = 0;
        PlantsOnLawn aPlantOnLawn = new PlantsOnLawn();
        b.getPlantsOnLawn(aGridX, aGridY, aPlantOnLawn);
        Plant aNormalPlant = aPlantOnLawn.mNormalPlant;
        Plant aPumpkinPlant = aPlantOnLawn.mPumpkinPlant;
        if (aNormalPlant != null && aNormalPlant.isUpgradableTo(aPlantingSeedType)) {
            if (aPlantingSeedType == SeedType.SEED_GLOOMSHROOM) {
                aIsAwake = !aNormalPlant.mIsAsleep;
                aWakeUpCounter = aNormalPlant.mWakeUpCounter;
            }
            aNormalPlant.die();
        }
        if ((aPlantingSeedType == SeedType.SEED_WALLNUT || aPlantingSeedType == SeedType.SEED_TALLNUT)
                && aNormalPlant != null) {
            if (aNormalPlant.mSeedType == aPlantingSeedType) {
                aNormalPlant.die();
            }
        }
        if (aPlantingSeedType == SeedType.SEED_PUMPKINSHELL && aPumpkinPlant != null) {
            if (aPumpkinPlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                aPumpkinPlant.die();
            }
        }
        if (aPlantingSeedType == SeedType.SEED_COBCANNON) {
            Plant aRightPlant = b.getTopPlantAt(aGridX + 1, aGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            if (aRightPlant != null) {
                aRightPlant.die();
            }
        }
        if (aPlantingSeedType == SeedType.SEED_CATTAIL) {
            if (aPlantOnLawn.mUnderPlant != null) {
                aPlantOnLawn.mUnderPlant.die();
            }
            if (aNormalPlant != null) {
                aNormalPlant.die();
            }
        }

        if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_GLOVE) {
            b.mApp.mZenGarden.movePlant(b.mPlants.dataArrayTryToGet(b.mCursorObject.mGlovePlantID), aGridX, aGridY);
        } else if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_WHEEL_BARROW) {
            b.mApp.mZenGarden.mouseDownWithFullWheelBarrow(x, y);
        } else if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_USABLE_COIN) {
            b.addPlant(aGridX, aGridY, b.mCursorObject.mType, b.mCursorObject.mImitaterType);
            Coin aCoin = b.mCoins.dataArrayTryToGet(b.mCursorObject.mCoinID);
            b.mCursorObject.mCoinID = CoinID.COINID_NULL;
            aCoin.die();
        } else if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_PLANT_FROM_BANK) {
            Plant aPlant = b.addPlant(aGridX, aGridY, b.mCursorObject.mType, b.mCursorObject.mImitaterType);
            if (aIsAwake) {
                aPlant.setSleeping(false);
            } else {
                aPlant.mWakeUpCounter = aWakeUpCounter;
            }

            b.mSeedBank.mSeedPackets[b.mCursorObject.mSeedBankIndex].wasPlanted();
        } else {
            assert false;
        }

        // Column levels plant a whole column at once
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
                if (aRow == aGridY || b.canPlantAt(aGridX, aRow,
                        aPlantingSeedType) != PlantingReason.PLANTING_OK) {
                    continue;
                }

                if (aPlantingSeedType == SeedType.SEED_WALLNUT || aPlantingSeedType == SeedType.SEED_TALLNUT) {
                    aNormalPlant = b.getTopPlantAt(aGridX, aRow, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
                    if (aNormalPlant != null && aNormalPlant.mSeedType == aPlantingSeedType) {
                        aNormalPlant.die();
                    }
                }
                if (aPlantingSeedType == SeedType.SEED_PUMPKINSHELL) {
                    aPumpkinPlant = b.getTopPlantAt(aGridX, aRow, PlantPriority.TOPPLANT_ONLY_PUMPKIN);
                    if (aPumpkinPlant != null && aPumpkinPlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                        aPumpkinPlant.die();
                    }
                }
                b.addPlant(aGridX, aRow, b.mCursorObject.mType, b.mCursorObject.mImitaterType);
            }
        }

        if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER) {
            setTutorialState(b, b.mPlants.mSize >= 2 ? TutorialState.TUTORIAL_LEVEL_1_COMPLETED
                    : TutorialState.TUTORIAL_LEVEL_1_REFRESH_PEASHOOTER);
        } else if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_PLANT_SUNFLOWER) {
            int aSunFlowersCount = b.countSunFlowers();
            if (aPlantingSeedType == SeedType.SEED_SUNFLOWER && aSunFlowersCount == 2) {
                b.displayAdvice("[ADVICE_MORE_SUNFLOWERS]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2,
                        AdviceType.ADVICE_NONE);
                if (!b.mSeedBank.mSeedPackets[1].canPickUp()) {
                    setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER);
                } else {
                    setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER);
                }
            } else if (aSunFlowersCount >= 3) {
                setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_COMPLETED);
            } else if (!b.mSeedBank.mSeedPackets[1].canPickUp()) {
                setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER);
            } else {
                setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER);
            }
        } else if (b.mTutorialState == TutorialState.TUTORIAL_MORESUN_PLANT_SUNFLOWER) {
            if (b.countSunFlowers() >= 3) {
                setTutorialState(b, TutorialState.TUTORIAL_MORESUN_COMPLETED);
                b.displayAdvice("[ADVICE_PLANT_SUNFLOWER5]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER,
                        AdviceType.ADVICE_PLANT_SUNFLOWER5);
                b.mTutorialTimer = -1;
            } else if (!b.mSeedBank.mSeedPackets[1].canPickUp()) {
                setTutorialState(b, TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER);
            } else {
                setTutorialState(b, TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER);
            }
        }

        if (b.mApp.isWallnutBowlingLevel()) {
            b.mApp.playSample(Resources.SOUND_BOWLING_ID);
        }

        clearCursor(b);
    }

    // Board.cpp:3952-3956 (Board::ToolHitTestHelper)
    static Plant toolHitTestHelper(Board b, HitResult theHitResult) {
        Plant aPlant = (Plant) theHitResult.mObject;
        return (aPlant.mSeedType != SeedType.SEED_GRAVEBUSTER
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) ? aPlant : null;
    }

    // Board.cpp:3958-3967 (Board::ToolHitTest)
    static Plant toolHitTest(Board b, int theX, int theY) {
        HitResult aHitResult = new HitResult();
        mouseHitTest(b, theX, theY, aHitResult);
        if (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_PLANT) {
            return toolHitTestHelper(b, aHitResult);
        }
        return null;
    }

    // Board.cpp:3969-3974 (Board::TutorialArrowShow)
    static void tutorialArrowShow(Board b, int theX, int theY) {
        tutorialArrowRemove(b);
        PvzpParticleSystem aParticle = b.mApp.addPvzpParticle(theX, theY,
                Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0), ParticleEffect.PARTICLE_SEED_PACKET_PICK);
        b.mTutorialParticleID = b.mApp.particleGetID(aParticle);
    }

    // Board.cpp:3976-3980 (Board::TutorialArrowRemove)
    static void tutorialArrowRemove(Board b) {
        b.mApp.removeParticle(b.mTutorialParticleID);
        b.mTutorialParticleID = ParticleSystemID.PARTICLESYSTEMID_NULL;
    }

    // Board.cpp:3982-4024 (Board::MouseDownWithTool)
    static void mouseDownWithTool(Board b, int x, int y, int theClickCount, int theCursorType) {
        if (theClickCount < 0) {
            clearCursor(b);
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            return;
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            b.mApp.mZenGarden.mouseDownWithTool(x, y, theCursorType);
            return;
        }
        if (b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            b.mChallenge.treeOfWisdomTool(x, y);
            return;
        }

        Plant aPlant = toolHitTest(b, x, y);
        if (aPlant == null) {
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
        } else if (theCursorType == CursorType.CURSOR_TYPE_SHOVEL) {
            b.mApp.playFoley(FoleyType.FOLEY_USE_SHOVEL);
            b.mPlantsShoveled++;
            aPlant.die(Plant.PLANT_REMOVED_SHOVEL);

            if (aPlant.mSeedType == SeedType.SEED_CATTAIL && b.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow,
                    PlantPriority.TOPPLANT_ONLY_PUMPKIN) != null) {
                b.newPlant(aPlant.mPlantCol, aPlant.mRow, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
            }
            if (b.mTutorialState == TutorialState.TUTORIAL_SHOVEL_DIG
                    || b.mTutorialState == TutorialState.TUTORIAL_SHOVEL_KEEP_DIGGING) {
                setTutorialState(b, b.countPlantByType(SeedType.SEED_PEASHOOTER) == 0
                        ? TutorialState.TUTORIAL_SHOVEL_COMPLETED : TutorialState.TUTORIAL_SHOVEL_KEEP_DIGGING);
            }
        }

        clearCursor(b);
    }

    // Board.cpp:4026-4050 (Board::SpecialPlantHitTest)
    static Plant specialPlantHitTest(Board b, int x, int y) {
        for (Plant aPlant : b.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                float aMinDist = b.getTopPlantAt(aPlant.mPlantCol, aPlant.mRow,
                        PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION) != null ? 25 : 0;
                float aDistance = PvzpCommon.distance2D(x, y, aPlant.mX + 40, aPlant.mY + 40);
                if (aDistance >= aMinDist && aDistance <= 50 && y > aPlant.mY + 25) {
                    return aPlant;
                }
            } else if (Plant.isFlying(aPlant.mSeedType)) {
                if (PvzpCommon.distance2D(x, y, aPlant.mX + 40, aPlant.mY) < 15) {
                    return aPlant;
                }
            }
        }
        return null;
    }

    // Board.cpp:4052-4106 (Board::MouseHitTestPlant)
    static boolean mouseHitTestPlant(Board b, int x, int y, HitResult theHitResult) {
        if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_COBCANNON_TARGET
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_HAMMER) {
            return false;
        }

        Plant aPlant;
        aPlant = specialPlantHitTest(b, x, y);
        if (aPlant != null) {
            theHitResult.mObject = aPlant;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_PLANT;
            return true;
        }

        int aGridX = b.pixelToGridX(x, y);
        int aGridY = b.pixelToGridY(x, y);
        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            aPlant = b.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ZEN_TOOL_ORDER);
            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_WATERING_CAN
                    && (aPlant == null || !b.mApp.mZenGarden.plantCanBeWatered(aPlant))) {
                Plant aTopPlant = b.getTopPlantAt(b.pixelToGridX(x - 30, y - 20), b.pixelToGridY(x - 30, y - 20),
                        PlantPriority.TOPPLANT_ZEN_TOOL_ORDER);
                if (aTopPlant != null && b.mApp.mZenGarden.plantCanBeWatered(aTopPlant)) {
                    aPlant = aTopPlant;
                }
            }
        } else {
            aPlant = b.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_DIGGING_ORDER);
            if (aPlant != null && (aPlant.mSeedType == SeedType.SEED_LILYPAD
                    || aPlant.mSeedType == SeedType.SEED_FLOWERPOT)) {
                if (b.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ONLY_PUMPKIN) != null) {
                    return false;
                }
            }
        }

        if (aPlant == null) {
            return false;
        }
        if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE
                && !zenGardenPlantCanHaveChocolate(b, aPlant)) {
            theHitResult.mObject = null;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
            return false;
        }

        theHitResult.mObject = aPlant;
        theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_PLANT;
        return true;
    }

    // Board.cpp:4090 (mApp->mZenGarden->PlantCanHaveChocolate()).
    private static boolean zenGardenPlantCanHaveChocolate(Board b, Plant thePlant) {
        return b.mApp.mZenGarden.plantCanHaveChocolate(thePlant);
    }

    // Board.cpp:4108-4255 (Board::MouseHitTest)
    static boolean mouseHitTest(Board b, int x, int y, HitResult theHitResult) {
        if (b.mBoardFadeOutCounter >= 0 || b.isScaryPotterDaveTalking()) {
            theHitResult.mObject = null;
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
            return false;
        }

        if (b.mMenuButton.isMouseOver() && canInteractWithBoardButtons(b)) {
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_MENU_BUTTON;
            return true;
        } else if (b.mStoreButton != null && b.mStoreButton.isMouseOver() && canInteractWithBoardButtons(b)) {
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_STORE_BUTTON;
            return true;
        }

        Rect aShovelButtonRect = b.getShovelButtonRect();
        if (b.mSeedBank.mouseHitTest(x, y, theHitResult)) {
            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                    || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_COBCANNON_TARGET
                    || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_HAMMER) {
                return true;
            }
        }
        if (b.mShowShovel && aShovelButtonRect.contains(x, y) && canInteractWithBoardButtons(b)) {
            theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_SHOVEL;
            return true;
        }

        if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                || b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_HAMMER) {
            Coin aTopCoin = null;
            for (Coin aCoin : b.mCoins) {
                if (aCoin.mDead) {
                    continue;
                }
                HitResult aHitResultCoin = new HitResult();
                if (aCoin.mouseHitTest(x, y, aHitResultCoin)) {
                    aCoin = (Coin) aHitResultCoin.mObject;
                    if (aTopCoin == null || aCoin.mRenderOrder >= aTopCoin.mRenderOrder) {
                        theHitResult.mObjectType = aHitResultCoin.mObjectType;
                        theHitResult.mObject = aCoin;
                        aTopCoin = aCoin;
                    }
                }
            }
            if (aTopCoin != null) {
                return true;
            }
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            boolean aCanClick = false;
            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_CHOCOLATE
                    && !b.mApp.mZenGarden.isStinkyHighOnChocolate()) {
                aCanClick = true;
            } else if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                    && b.mApp.mZenGarden.isStinkySleeping()) {
                aCanClick = true;
            }

            GridItem aStinky = b.mApp.mZenGarden.getStinky();
            if (aCanClick && aStinky != null) {
                Rect aStinkyRect = new Rect((int) aStinky.mPosX - 6, (int) aStinky.mPosY - 10, 84, 90);
                if (aStinkyRect.contains(x, y)) {
                    theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_STINKY;
                    return true;
                }
            }
        }
        if (b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            if (b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_TREE_FOOD
                    && b.mChallenge.treeOfWisdomHitTest(x, y, theHitResult) != 0) {
                return true;
            }
        }
        if ((b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) && canInteractWithBoardButtons(b)) {
            for (int i = GameObjectType.OBJECT_TYPE_WATERING_CAN; i <= GameObjectType.OBJECT_TYPE_NEXT_GARDEN; i++) {
                int aTool = i;
                if (b.canUseGameObject(aTool) && (aTool != GameObjectType.OBJECT_TYPE_TREE_FOOD
                        || b.mChallenge.treeOfWisdomCanFeed() != 0)) {
                    Rect aButtonRect = b.getShovelButtonRect();
                    if (aTool == GameObjectType.OBJECT_TYPE_NEXT_GARDEN) {
                        aButtonRect.mX = 564;
                    } else {
                        b.getZenButtonRect(aTool, aButtonRect);
                    }

                    if (aButtonRect.contains(x, y)) {
                        theHitResult.mObjectType = aTool;
                        return true;
                    }
                }
            }
        }

        if (mouseHitTestPlant(b, x, y, theHitResult)) {
            return true;
        }

        if (b.mApp.isScaryPotterLevel()
                && b.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                && b.mChallenge.mChallengeState != ChallengeState.STATECHALLENGE_SCARY_POTTER_MALLETING
                && b.mApp.mGameScene == GameScenes.SCENE_PLAYING
                && b.mApp.getDialog(Dialogs.DIALOG_GAME_OVER) == null
                && b.mApp.getDialog(Dialogs.DIALOG_CONTINUE) == null) {
            GridItem aScaryPot = b.getGridItemAt(GridItemType.GRIDITEM_SCARY_POT, b.pixelToGridX(x, y),
                    b.pixelToGridY(x, y));
            if (aScaryPot != null) {
                theHitResult.mObject = aScaryPot;
                theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_SCARY_POT;
                return true;
            }
        }

        if (b.mApp.isSlotMachineLevel()) {
            Rect aSlotMachineHandleRect = b.mChallenge.slotMachineGetHandleRect();
            if (aSlotMachineHandleRect.contains(x, y)
                    && b.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_NORMAL
                    && !b.hasLevelAwardDropped()) {
                theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_SLOT_MACHINE_HANDLE;
                return true;
            }
        }

        theHitResult.mObject = null;
        theHitResult.mObjectType = GameObjectType.OBJECT_TYPE_NONE;
        return false;
    }

    // Board.cpp:4257-4361 (Board::PickUpTool)
    static void pickUpTool(Board b, int theObjectType) {
        if (b.mPaused || (b.mApp.mGameScene != GameScenes.SCENE_PLAYING && !b.mCutScene.isInShovelTutorial())) {
            return;
        }

        switch (theObjectType) {
        case GameObjectType.OBJECT_TYPE_SHOVEL:
            if (b.mTutorialState == TutorialState.TUTORIAL_SHOVEL_PICKUP) {
                setTutorialState(b, TutorialState.TUTORIAL_SHOVEL_DIG);
            }
            b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_SHOVEL;
            b.mApp.playFoley(FoleyType.FOLEY_SHOVEL);
            break;

        case GameObjectType.OBJECT_TYPE_WATERING_CAN:
            if (b.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_PICKUP_WATER) {
                b.mTutorialState = TutorialState.TUTORIAL_ZEN_GARDEN_WATER_PLANT;
                b.displayAdvice("[ADVICE_ZEN_GARDEN_WATER_PLANT]", MessageStyle.MESSAGE_STYLE_ZEN_GARDEN_LONG,
                        AdviceType.ADVICE_NONE);
                tutorialArrowRemove(b);
            }
            b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_WATERING_CAN;
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            break;

        case GameObjectType.OBJECT_TYPE_FERTILIZER:
            if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] > PlayerInfo.PURCHASE_COUNT_OFFSET) {
                b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_FERTILIZER;
                b.mApp.playFoley(FoleyType.FOLEY_DROP);
            } else {
                b.mApp.playSample(Resources.SOUND_BUZZER_ID);
            }
            break;

        case GameObjectType.OBJECT_TYPE_BUG_SPRAY:
            if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY] > PlayerInfo.PURCHASE_COUNT_OFFSET) {
                b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_BUG_SPRAY;
                b.mApp.playFoley(FoleyType.FOLEY_DROP);
            } else {
                b.mApp.playSample(Resources.SOUND_BUZZER_ID);
            }
            break;

        case GameObjectType.OBJECT_TYPE_PHONOGRAPH:
            b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_PHONOGRAPH;
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            break;

        case GameObjectType.OBJECT_TYPE_CHOCOLATE:
            if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] > PlayerInfo.PURCHASE_COUNT_OFFSET) {
                b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_CHOCOLATE;
                b.mApp.playFoley(FoleyType.FOLEY_DROP);
            } else {
                b.mApp.playSample(Resources.SOUND_BUZZER_ID);
            }
            break;

        case GameObjectType.OBJECT_TYPE_GLOVE:
            b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_GLOVE;
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            break;

        case GameObjectType.OBJECT_TYPE_MONEY_SIGN:
            b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_MONEY_SIGN;
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            break;

        case GameObjectType.OBJECT_TYPE_WHEELBARROW:
            b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_WHEEELBARROW;
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            break;

        case GameObjectType.OBJECT_TYPE_TREE_FOOD:
            if (b.mChallenge.treeOfWisdomCanFeed() != 0) {
                if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] > PlayerInfo.PURCHASE_COUNT_OFFSET) {
                    b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_TREE_FOOD;
                    b.mApp.playFoley(FoleyType.FOLEY_DROP);
                } else {
                    b.mApp.playSample(Resources.SOUND_BUZZER_ID);
                }
            }
            break;

        default:
            assert false;
            break;
        }

        b.mCursorObject.mType = SeedType.SEED_NONE;
    }

    // Board.cpp:4363-4495 (Board::MouseDown)
    static void mouseDown(Board b, int x, int y, int theClickCount) {
        updateMousePosition(b);
        b.mIgnoreMouseUp = !canInteractWithBoardButtons(b);
        if (b.mTimeStopCounter > 0) {
            return;
        }

        HitResult aHitResult = new HitResult();
        mouseHitTest(b, x, y, aHitResult);
        if (b.mChallenge.mouseDown(x, y, theClickCount, aHitResult) != 0) {
            return;
        }

        if (b.mMenuButton.isMouseOver() && canInteractWithBoardButtons(b) && theClickCount > 0) {
            b.mApp.playSample(Resources.SOUND_GRAVEBUTTON_ID);
        } else if (b.mStoreButton != null && b.mStoreButton.isMouseOver() && canInteractWithBoardButtons(b)
                && theClickCount > 0) {
            if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                    || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
                b.mApp.playSample(Resources.SOUND_TAP_ID);
            } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                    || b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
                b.mApp.playSample(Resources.SOUND_GRAVEBUTTON_ID);
            }
        }

        if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO && b.mApp.mSeedChooserScreen != null) {
            b.mApp.mSeedChooserScreen.cancelLawnView();
        }
        if (b.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON) {
            b.mCutScene.zombieWonClick();
            return;
        }
        if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
            b.mCutScene.mouseDown(x, y);
        }

        if (b.mApp.mCheatKeys && !b.mApp.isScaryPotterLevel() && b.mNextSurvivalStageCounter > 0) {
            b.mNextSurvivalStageCounter = 2;
            for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                b.mIceTimer[i] = Math.min(b.mIceTimer[i], 2);
            }
        }

        int aCursor = b.mCursorObject.mCursorType;
        if (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_NONE) {
            if (aCursor == CursorType.CURSOR_TYPE_COBCANNON_TARGET) {
                mouseDownCobcannonFire(b, x, y, theClickCount);
                updateCursor(b, null);
                return;
            }
        } else if (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_COIN && theClickCount >= 0) {
            Coin aCoin = (Coin) aHitResult.mObject;
            if (aCoin.mBoard != null) {
                aCoin.mouseDown(x, y, theClickCount);
            }
            updateCursor(b, null);
            return;
        }

        if (aCursor == CursorType.CURSOR_TYPE_SHOVEL
                || aCursor == CursorType.CURSOR_TYPE_WATERING_CAN
                || aCursor == CursorType.CURSOR_TYPE_FERTILIZER
                || aCursor == CursorType.CURSOR_TYPE_BUG_SPRAY
                || aCursor == CursorType.CURSOR_TYPE_PHONOGRAPH
                || aCursor == CursorType.CURSOR_TYPE_CHOCOLATE
                || aCursor == CursorType.CURSOR_TYPE_GLOVE
                || aCursor == CursorType.CURSOR_TYPE_MONEY_SIGN
                || aCursor == CursorType.CURSOR_TYPE_WHEEELBARROW
                || aCursor == CursorType.CURSOR_TYPE_TREE_FOOD) {
            mouseDownWithTool(b, x, y, theClickCount, aCursor);
        } else if (b.isPlantInCursor()) {
            mouseDownWithPlant(b, x, y, theClickCount);
        } else {
            switch (aHitResult.mObjectType) {
            case GameObjectType.OBJECT_TYPE_SEEDPACKET:
                if (!b.mPaused) {
                    ((SeedPacket) aHitResult.mObject).mouseDown(x, y, theClickCount);
                }
                break;
            case GameObjectType.OBJECT_TYPE_NEXT_GARDEN:
                if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
                    b.mApp.mZenGarden.gotoNextGarden();
                } else if (b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
                    b.mChallenge.treeOfWisdomNextGarden();
                }
                b.mApp.playSample(Resources.SOUND_TAP_ID);
                break;
            case GameObjectType.OBJECT_TYPE_SHOVEL:
            case GameObjectType.OBJECT_TYPE_WATERING_CAN:
            case GameObjectType.OBJECT_TYPE_FERTILIZER:
            case GameObjectType.OBJECT_TYPE_BUG_SPRAY:
            case GameObjectType.OBJECT_TYPE_PHONOGRAPH:
            case GameObjectType.OBJECT_TYPE_CHOCOLATE:
            case GameObjectType.OBJECT_TYPE_GLOVE:
            case GameObjectType.OBJECT_TYPE_MONEY_SIGN:
            case GameObjectType.OBJECT_TYPE_WHEELBARROW:
            case GameObjectType.OBJECT_TYPE_TREE_FOOD:
                pickUpTool(b, aHitResult.mObjectType);
                break;
            case GameObjectType.OBJECT_TYPE_PLANT:
                ((Plant) aHitResult.mObject).mouseDown(x, y, theClickCount);
                break;
            default:
                break;
            }
        }

        updateCursor(b, null);
    }

    // Board.cpp:4497-4559 (Board::ClearCursor)
    static void clearCursor(Board b) {
        if (b.mAdvice.mDuration > 0) {
            if (b.mHelpIndex == AdviceType.ADVICE_PLANT_GRAVEBUSTERS_ON_GRAVES
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_LILYPAD_ON_WATER
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_TANGLEKELP_ON_WATER
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_SEASHROOM_ON_WATER
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_POTATOE_MINE_ON_LILY
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_WRONG_ART_TYPE
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_NEED_POT
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_NOT_PASSED_LINE
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_ONLY_ON_REPEATERS
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_ONLY_ON_MELONPULT
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_ONLY_ON_SUNFLOWER
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_ONLY_ON_SPIKEWEED
                    || b.mHelpIndex == AdviceType.ADVICE_PLANT_ONLY_ON_KERNELPULT) {
                b.clearAdvice(b.mHelpIndex);
            }
        }

        b.mCursorObject.mType = SeedType.SEED_NONE;
        b.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_NORMAL;
        b.mCursorObject.mSeedBankIndex = -1;
        b.mCursorObject.mCoinID = CoinID.COINID_NULL;
        b.mCursorObject.mDuplicatorPlantID = PlantID.PLANTID_NULL;
        b.mCursorObject.mCobCannonPlantID = PlantID.PLANTID_NULL;
        b.mCursorObject.mGlovePlantID = PlantID.PLANTID_NULL;
        b.mApp.setCursor(SexyAppBase.CURSOR_POINTER);
        b.mChallenge.clearCursor();

        if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER) {
            setTutorialState(b, TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER);
        } else if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_PLANT_SUNFLOWER
                || b.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER) {
            if (!b.mSeedBank.mSeedPackets[1].canPickUp()) {
                setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER);
            } else {
                setTutorialState(b, TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER);
            }
        } else if (b.mTutorialState == TutorialState.TUTORIAL_MORESUN_PLANT_SUNFLOWER
                || b.mTutorialState == TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER) {
            if (!b.mSeedBank.mSeedPackets[1].canPickUp()) {
                setTutorialState(b, TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER);
            } else {
                setTutorialState(b, TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER);
            }
        } else if (b.mTutorialState == TutorialState.TUTORIAL_SHOVEL_DIG) {
            setTutorialState(b, TutorialState.TUTORIAL_SHOVEL_PICKUP);
        }
    }

    // Board.cpp:4561-4579 (Board::CanInteractWithBoardButtons)
    static boolean canInteractWithBoardButtons(Board b) {
        // during the process of obtaining a reward, should not interaction with the menu.
        if (b.mBoardFadeOutCounter >= 0) {
            return false;
        }

        if (b.mPaused || b.mApp.getDialogCount() > 0) {
            return false;
        }

        if (b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_NORMAL
                && b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_HAMMER
                && b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_COBCANNON_TARGET) {
            return false;
        }

        if (b.mChallenge.mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
            return false;
        }

        return b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL
                || b.mApp.mCrazyDaveState == CrazyDaveState.CRAZY_DAVE_OFF;
    }

    // Board.cpp:4581-4641 (Board::MouseUp)
    static void mouseUp(Board b, int x, int y, int theClickCount) {
        if (b.mIgnoreMouseUp) {
            b.mIgnoreMouseUp = false;
            return;
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED && b.mChallenge.mouseUp(x, y) != 0
                && theClickCount > 0) {
            return;
        }

        if (canInteractWithBoardButtons(b) && theClickCount > 0) {
            if (b.mMenuButton.isMouseOver() && b.mApp.getDialog(Dialogs.DIALOG_GAME_OVER) == null
                    && b.mApp.getDialog(Dialogs.DIALOG_LEVEL_COMPLETE) == null) {
                b.mMenuButton.mIsOver = false;
                b.mMenuButton.mIsDown = false;
                updateCursor(b, null);
                clearCursor(b);
                if (b.mTutorialState == TutorialState.TUTORIAL_ZEN_GARDEN_COMPLETED) {
                    b.mApp.finishZenGardenToturial();
                } else if (b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                        && b.mApp.mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM
                        && b.mApp.mGameMode != GameMode.GAMEMODE_UPSELL) {
                    b.mApp.playSample(Resources.SOUND_PAUSE_ID);
                    b.mApp.doNewOptions(false);
                } else {
                    b.mApp.mBoardResult = BoardResult.BOARDRESULT_QUIT;
                    b.mApp.doBackToMain();
                }
            } else if (b.mStoreButton != null && b.mStoreButton.isMouseOver()) {
                if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
                    b.mApp.mZenGarden.openStore();
                } else if (b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
                    b.mChallenge.treeOfWisdomOpenStore();
                } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
                    b.mChallenge.mChallengeState = ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT;
                    b.mStoreButton.mBtnNoDraw = true;
                    b.mStoreButton.mDisabled = true;
                    b.mZombieCountDown = 10;
                    b.mZombieCountDownStart = 10;
                } else if (b.mApp.mGameMode == GameMode.GAMEMODE_UPSELL) {
                    b.mApp.doBackToMain();
                }
            }
        }
    }

    // Board.cpp:5481-5526 (Board::UpdateTutorial)
    static void updateTutorial(Board b) {
        if (b.mTutorialTimer > 0) {
            b.mTutorialTimer--;
        }

        if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER && b.mTutorialTimer == 0) {
            b.displayAdvice("[ADVICE_CLICK_PEASHOOTER]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                    AdviceType.ADVICE_NONE);
            tutorialArrowShow(b, b.mSeedBank.mX + b.mSeedBank.mSeedPackets[0].mX,
                    b.mSeedBank.mY + b.mSeedBank.mSeedPackets[0].mY);
            b.mTutorialTimer = -1;
        } else if (b.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER
                || b.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_PLANT_SUNFLOWER
                || b.mTutorialState == TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER) {
            if (b.mTutorialTimer == 0) {
                b.displayAdvice("[ADVICE_PLANT_SUNFLOWER2]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2,
                        AdviceType.ADVICE_NONE);
                b.mTutorialTimer = -1;
            } else if (b.mZombieCountDown == 750 && b.mCurrentWave == 0) {
                b.displayAdvice("[ADVICE_PLANT_SUNFLOWER3]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL2,
                        AdviceType.ADVICE_NONE);
            }
        } else if (b.mTutorialState == TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER
                || b.mTutorialState == TutorialState.TUTORIAL_MORESUN_PLANT_SUNFLOWER
                || b.mTutorialState == TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER) {
            if (b.mTutorialTimer == 0) {
                b.displayAdvice("[ADVICE_PLANT_SUNFLOWER5]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER,
                        AdviceType.ADVICE_PLANT_SUNFLOWER5);
                b.mTutorialTimer = -1;
            }
        }

        if (b.mApp.isFirstTimeAdventureMode() && b.mLevel >= 3 && b.mLevel != 5 && b.mLevel <= 7
                && b.mTutorialState == TutorialState.TUTORIAL_OFF && b.mCurrentWave >= 5
                && !Board.gShownMoreSunTutorial && b.mSeedBank.mSeedPackets[1].canPickUp()
                && b.countPlantByType(SeedType.SEED_SUNFLOWER) < 3) {
            assert !b.chooseSeedsOnCurrentLevel();
            b.displayAdvice("[ADVICE_PLANT_SUNFLOWER4]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LATER_STAY,
                    AdviceType.ADVICE_NONE);
            Board.gShownMoreSunTutorial = true;
            setTutorialState(b, TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER);
            b.mTutorialTimer = 500;
        }
    }

    // Board.cpp:5528-5629 (Board::SetTutorialState)
    static void setTutorialState(Board b, int theTutorialState) {
        // string key, resolved by LawnApp.getString
        switch (theTutorialState) {
        case TutorialState.TUTORIAL_LEVEL_1_PICK_UP_PEASHOOTER:
            if (b.mPlants.mSize == 0) {
                float aPosX = b.mSeedBank.mX + b.mSeedBank.mSeedPackets[0].mX;
                float aPosY = b.mSeedBank.mY + b.mSeedBank.mSeedPackets[0].mY;
                tutorialArrowShow(b, (int) aPosX, (int) aPosY);
                b.displayAdvice("[ADVICE_CLICK_SEED_PACKET]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                        AdviceType.ADVICE_NONE);
            } else {
                b.displayAdvice("[ADVICE_ENOUGH_SUN]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                        AdviceType.ADVICE_NONE);
                b.mTutorialTimer = 400;
            }
            break;

        case TutorialState.TUTORIAL_LEVEL_1_PLANT_PEASHOOTER:
            b.mTutorialTimer = -1;
            tutorialArrowRemove(b);
            if (b.mPlants.mSize == 0) {
                b.displayAdvice("[ADVICE_CLICK_ON_GRASS]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                        AdviceType.ADVICE_NONE);
            } else {
                b.clearAdvice(AdviceType.ADVICE_NONE);
            }
            break;

        case TutorialState.TUTORIAL_LEVEL_1_REFRESH_PEASHOOTER:
            b.displayAdvice("[ADVICE_PLANTED_PEASHOOTER]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1_STAY,
                    AdviceType.ADVICE_NONE);
            b.mSunCountDown = 400;
            break;

        case TutorialState.TUTORIAL_LEVEL_1_COMPLETED:
            b.displayAdvice("[ADVICE_ZOMBIE_ONSLAUGHT]", MessageStyle.MESSAGE_STYLE_TUTORIAL_LEVEL1,
                    AdviceType.ADVICE_NONE);
            b.mZombieCountDown = 99;
            b.mZombieCountDownStart = b.mZombieCountDown;
            break;

        case TutorialState.TUTORIAL_LEVEL_2_PICK_UP_SUNFLOWER:
        case TutorialState.TUTORIAL_MORESUN_PICK_UP_SUNFLOWER:
            tutorialArrowShow(b, b.mSeedBank.mX + b.mSeedBank.mSeedPackets[1].mX,
                    b.mSeedBank.mY + b.mSeedBank.mSeedPackets[1].mY);
            break;

        case TutorialState.TUTORIAL_LEVEL_2_PLANT_SUNFLOWER:
        case TutorialState.TUTORIAL_LEVEL_2_REFRESH_SUNFLOWER:
        case TutorialState.TUTORIAL_MORESUN_PLANT_SUNFLOWER:
        case TutorialState.TUTORIAL_MORESUN_REFRESH_SUNFLOWER:
            tutorialArrowRemove(b);
            break;

        case TutorialState.TUTORIAL_LEVEL_2_COMPLETED:
            if (b.mCurrentWave == 0) {
                b.mZombieCountDown = 999;
                b.mZombieCountDownStart = b.mZombieCountDown;
            }
            break;

        case TutorialState.TUTORIAL_SLOT_MACHINE_PULL:
            b.displayAdvice("[ADVICE_SLOT_MACHINE_PULL]", MessageStyle.MESSAGE_STYLE_SLOT_MACHINE,
                    AdviceType.ADVICE_SLOT_MACHINE_PULL);
            break;

        case TutorialState.TUTORIAL_SLOT_MACHINE_COMPLETED:
            b.clearAdvice(AdviceType.ADVICE_SLOT_MACHINE_PULL);
            break;

        case TutorialState.TUTORIAL_SHOVEL_PICKUP: {
            b.displayAdvice("[ADVICE_CLICK_SHOVEL]", MessageStyle.MESSAGE_STYLE_HINT_STAY, AdviceType.ADVICE_NONE);
            Rect aShovelButtonRect = b.getShovelButtonRect();
            int aPosX = aShovelButtonRect.mX + aShovelButtonRect.mWidth / 2 - 25;
            int aPosY = aShovelButtonRect.mY + aShovelButtonRect.mHeight - 65;
            tutorialArrowShow(b, aPosX, aPosY);
            break;
        }

        case TutorialState.TUTORIAL_SHOVEL_DIG:
            b.displayAdvice("[ADVICE_CLICK_PLANT]", MessageStyle.MESSAGE_STYLE_HINT_STAY, AdviceType.ADVICE_NONE);
            tutorialArrowRemove(b);
            break;

        case TutorialState.TUTORIAL_SHOVEL_KEEP_DIGGING:
            b.displayAdvice("[ADVICE_KEEP_DIGGING]", MessageStyle.MESSAGE_STYLE_HINT_STAY, AdviceType.ADVICE_NONE);
            break;

        case TutorialState.TUTORIAL_SHOVEL_COMPLETED:
            b.clearAdvice(AdviceType.ADVICE_NONE);
            b.mCutScene.mCutsceneTime = 1500;
            b.mCutScene.mCrazyDaveDialogStart = 2410;
            break;

        default:
            break;
        }

        b.mTutorialState = theTutorialState;
    }

    // Board.cpp:7547-7626 (Board::DoTypingCheck)
    static void doTypingCheck(Board b, int theKey) {
        if (b.mApp.mKonamiCheck.check(theKey)) {
            b.mApp.playFoley(FoleyType.FOLEY_DROP);
            return;
        }
        if (b.mApp.mMustacheCheck.check(theKey) || b.mApp.mMoustacheCheck.check(theKey)) {
            b.setMustacheMode(!b.mMustacheMode);
            ReportAchievement.giveAchievement(b.mApp, AchievementId.MustacheMode, true);
            return;
        }
        if (b.mApp.mSuperMowerCheck.check(theKey) || b.mApp.mSuperMowerCheck2.check(theKey)) {
            b.setSuperMowerMode(!b.mSuperMowerMode);
            return;
        }
        if (b.mApp.mFutureCheck.check(theKey)) {
            b.setFutureMode(!b.mFutureMode);
            return;
        }
        if (b.mApp.mPinataCheck.check(theKey)) {
            if (b.mApp.canDoPinataMode()) {
                b.setPinataMode(!b.mPinataMode);
                return;
            } else {
                if (b.mApp.mGameScene == GameScenes.SCENE_PLAYING) {
                    b.displayAdvice("[CANT_USE_CODE]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST, AdviceType.ADVICE_NONE);
                }
                b.mApp.playSample(Resources.SOUND_BUZZER_ID);
                return;
            }
        }
        if (b.mApp.mDanceCheck.check(theKey)) {
            if (b.mApp.canDoDanceMode()) {
                b.setDanceMode(!b.mDanceMode);
                return;
            } else {
                if (b.mApp.mGameScene == GameScenes.SCENE_PLAYING) {
                    b.displayAdvice("[CANT_USE_CODE]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST, AdviceType.ADVICE_NONE);
                }
                b.mApp.playSample(Resources.SOUND_BUZZER_ID);
                return;
            }
        }
        if (b.mApp.mDaisyCheck.check(theKey)) {
            if (b.mApp.canDoDaisyMode()) {
                b.setDaisyMode(!b.mDaisyMode);
                return;
            } else {
                if (b.mApp.mGameScene == GameScenes.SCENE_PLAYING) {
                    b.displayAdvice("[CANT_USE_CODE]", MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST, AdviceType.ADVICE_NONE);
                }
                b.mApp.playSample(Resources.SOUND_BUZZER_ID);
                return;
            }
        }
        if (b.mApp.mSukhbirCheck.check(theKey)) {
            b.setSukhbirMode(!b.mSukhbirMode);
            return;
        }
    }

    // Board.cpp:7668-7671 (static PvzpCrash)
    private static void pvzpCrash() {
        assert false : "Crash!!!!";
    }

    // Board.cpp:7628-7666 (Board::KeyDown)
    static void keyDown(Board b, int theKey) {
        doTypingCheck(b, theKey);

        if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO
                && b.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                && b.mApp.mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM) {
            b.mCutScene.keyDown(theKey);
        } else if (theKey == KeyCode.KEYCODE_RETURN || theKey == KeyCode.KEYCODE_SPACE) {
            if (b.isScaryPotterDaveTalking() && b.mApp.mCrazyDaveMessageIndex != -1) {
                b.mChallenge.advanceCrazyDaveDialog();
            } else if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                    || b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
                b.mApp.mZenGarden.advanceCrazyDaveDialog();
            } else if (theKey == KeyCode.KEYCODE_SPACE && b.mApp.canPauseNow()) {
                b.mApp.playSample(Resources.SOUND_PAUSE_ID);
                b.mApp.doPauseDialog();
            }
        } else if (theKey == KeyCode.KEYCODE_ESCAPE) {
            if (b.mCursorObject.mCursorType != CursorType.CURSOR_TYPE_NORMAL) {
                b.refreshSeedPacketFromCursor();
            } else if (canInteractWithBoardButtons(b) && b.mApp.mGameScene != GameScenes.SCENE_ZOMBIES_WON) {
                b.mApp.doNewOptions(false);
            }
        }
    }

    // Board.cpp:7672-8431 (Board::KeyChar)
    static void keyChar(Board b, char theChar) {
        if (!b.mApp.mDebugKeysEnabled) {
            return;
        }

        PvzpDebug.pvzpLogLn("Board cheat key '{}'", theChar);

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            if (theChar == 'm') {
                if (!b.mApp.mZenGarden.isZenGardenFull(true)) {
                    PottedPlant aPottedPlant = new PottedPlant();
                    aPottedPlant.initializePottedPlant(SeedType.SEED_MARIGOLD);
                    aPottedPlant.mDrawVariation = PvzpCommon.randRangeInt(DrawVariation.VARIATION_MARIGOLD_WHITE,
                            DrawVariation.VARIATION_MARIGOLD_LIGHT_GREEN);
                    b.mApp.mZenGarden.addPottedPlant(aPottedPlant);
                }
                return;
            }

            if (theChar == '+') {
                if (!b.mApp.mZenGarden.isZenGardenFull(true)) {
                    PottedPlant aPottedPlant = new PottedPlant();
                    aPottedPlant.initializePottedPlant(b.mApp.mZenGarden.pickRandomSeedType());
                    b.mApp.mZenGarden.addPottedPlant(aPottedPlant);
                }
                return;
            }

            if (theChar == 'a') {
                if (!b.mApp.mZenGarden.isZenGardenFull(true)) {
                    PottedPlant aPottedPlant = new PottedPlant();
                    aPottedPlant.initializePottedPlant(b.mApp.mZenGarden.pickRandomSeedType());
                    aPottedPlant.mPlantAge = PottedPlantAge.PLANTAGE_FULL;
                    b.mApp.mZenGarden.addPottedPlant(aPottedPlant);
                }
                return;
            }

            if (theChar == 'f') {
                for (Plant aPlant : b.mPlants) {
                    if (aPlant.mDead) {
                        continue;
                    }
                    if (b.getZenToolAt(aPlant.mPlantCol, aPlant.mRow) == null && aPlant.mPottedPlantIndex >= 0) {
                        PottedPlant aPottedPlant = b.mApp.mZenGarden.pottedPlantFromIndex(aPlant.mPottedPlantIndex);
                        int aNeed = b.mApp.mZenGarden.getPlantsNeed(aPottedPlant);
                        if (aNeed == PottedPlantNeed.PLANTNEED_WATER) {
                            aPlant.mHighlighted = true;
                            b.mApp.mZenGarden.mouseDownWithFeedingTool(aPlant.mX, aPlant.mY,
                                    CursorType.CURSOR_TYPE_WATERING_CAN);
                            return;
                        } else if (aNeed == PottedPlantNeed.PLANTNEED_FERTILIZER) {
                            aPlant.mHighlighted = true;
                            if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] <= PlayerInfo.PURCHASE_COUNT_OFFSET) {
                                b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_FERTILIZER] = PlayerInfo.PURCHASE_COUNT_OFFSET + 1;
                            }
                            b.mApp.mZenGarden.mouseDownWithFeedingTool(aPlant.mX, aPlant.mY,
                                    CursorType.CURSOR_TYPE_FERTILIZER);
                            return;
                        } else if (aNeed == PottedPlantNeed.PLANTNEED_BUGSPRAY) {
                            aPlant.mHighlighted = true;
                            if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY] <= PlayerInfo.PURCHASE_COUNT_OFFSET) {
                                b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_BUG_SPRAY] = PlayerInfo.PURCHASE_COUNT_OFFSET + 1;
                            }
                            b.mApp.mZenGarden.mouseDownWithFeedingTool(aPlant.mX, aPlant.mY,
                                    CursorType.CURSOR_TYPE_BUG_SPRAY);
                            return;
                        } else if (aNeed == PottedPlantNeed.PLANTNEED_PHONOGRAPH) {
                            aPlant.mHighlighted = true;
                            b.mApp.mZenGarden.mouseDownWithFeedingTool(aPlant.mX, aPlant.mY,
                                    CursorType.CURSOR_TYPE_PHONOGRAPH);
                            return;
                        }
                    }
                }
                return;
            }

            if (theChar == 'r') {
                for (Plant aPlant : b.mPlants) {
                    if (aPlant.mDead) {
                        continue;
                    }
                    if (aPlant.mPottedPlantIndex >= 0) {
                        PottedPlant aPottedPlant = b.mApp.mPlayerInfo.mPottedPlant[aPlant.mPottedPlantIndex];
                        b.mApp.mZenGarden.resetPlantTimers(aPottedPlant);
                    }
                }
                return;
            }

            if (theChar == 's') {
                if (b.mApp.mZenGarden.isStinkySleeping()) {
                    b.mApp.mZenGarden.wakeStinky();
                } else {
                    b.mApp.mZenGarden.resetStinkyTimers();
                }
                return;
            }

            if (theChar == 'c') {
                if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] < PlayerInfo.PURCHASE_COUNT_OFFSET) {
                    b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE] = PlayerInfo.PURCHASE_COUNT_OFFSET + 1;
                } else {
                    b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_CHOCOLATE]++;
                }
                return;
            }

            if (theChar == ']') {
                PottedPlant aPottedPlant = b.mApp.mZenGarden.getPottedPlantInWheelbarrow();
                if (aPottedPlant != null) {
                    aPottedPlant.mSeedType = aPottedPlant.mSeedType + 1;
                    if (aPottedPlant.mSeedType == SeedType.SEED_GATLINGPEA) {
                        aPottedPlant.mSeedType = SeedType.SEED_PEASHOOTER;
                    }
                    if (aPottedPlant.mSeedType == SeedType.SEED_FLOWERPOT) {
                        aPottedPlant.mSeedType = SeedType.SEED_KERNELPULT;
                    }
                }
                return;
            }
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            if (theChar == 'f') {
                if (b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] <= PlayerInfo.PURCHASE_COUNT_OFFSET) {
                    b.mApp.mPlayerInfo.mPurchases[StoreItem.STORE_ITEM_TREE_FOOD] = PlayerInfo.PURCHASE_COUNT_OFFSET + 1;
                }
                b.mChallenge.treeOfWisdomFertilize();
            } else if (theChar == 'g') {
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == 'b') {
                b.mChallenge.mChallengeStateCounter = 1;
            } else if (theChar == '0') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 0;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '1') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 9;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '2') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 19;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '3') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 29;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '4') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 39;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '5') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 49;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '6') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 98;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '7') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 498;
                b.mChallenge.treeOfWisdomGrow();
            } else if (theChar == '8') {
                b.mApp.mPlayerInfo.mChallengeRecords[b.mApp.getCurrentChallengeIndex()] = 998;
                b.mChallenge.treeOfWisdomGrow();
            }

            return;
        }

        if (theChar == '<') {
            b.mApp.doNewOptions(false);
        } else if (theChar == 'l') {
            b.mApp.doCheatDialog();
        } else if (theChar == '#') {
            if (b.mApp.isSurvivalMode()) {
                if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
                    return;
                }

                b.mCurrentWave = b.mNumWaves;
                b.mChallenge.mSurvivalStage += 5;
                b.removeAllZombies();
                b.fadeOutLevel();
            }
        } else if (theChar == '!') {
            b.mApp.mBoardResult = BoardResult.BOARDRESULT_CHEAT;
            if (b.isLastStandStageWithRepick()) {
                if (b.mNextSurvivalStageCounter == 0) {
                    b.mCurrentWave = b.mNumWaves;
                    b.removeAllZombies();
                    b.fadeOutLevel();
                }
            } else if ((b.mApp.isScaryPotterLevel() && !b.isFinalScaryPotterStage())
                    || LawnApp.isEndlessIZombie(b.mApp.mGameMode)) {
                if (b.mNextSurvivalStageCounter == 0) {
                    b.removeAllZombies();
                    b.fadeOutLevel();
                }
            } else if (b.mApp.isSurvivalMode()) {
                if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
                    return;
                }

                b.mCurrentWave = b.mNumWaves;
                //if (!IsSurvivalStageWithRepick())
                //{
                    b.removeAllZombies();
                //}
                b.fadeOutLevel();
            } else {
                b.removeAllZombies();
                b.fadeOutLevel();
                b.mBoardFadeOutCounter = 200;
            }
        } else if (theChar == '+') {
            b.mApp.mBoardResult = BoardResult.BOARDRESULT_CHEAT;
            if (b.isLastStandStageWithRepick()) {
                if (b.mNextSurvivalStageCounter == 0) {
                    b.mCurrentWave = b.mNumWaves;
                    b.removeAllZombies();
                    b.fadeOutLevel();
                }
            } else if ((b.mApp.isScaryPotterLevel() && !b.isFinalScaryPotterStage())
                    || LawnApp.isEndlessIZombie(b.mApp.mGameMode)) {
                if (b.mNextSurvivalStageCounter == 0) {
                    b.removeAllZombies();
                    b.fadeOutLevel();
                }
            } else if (LawnApp.isSurvivalEndless(b.mApp.mGameMode)) {
                if (b.mApp.mGameScene == GameScenes.SCENE_LEVEL_INTRO) {
                    return;
                }

                b.mCurrentWave = b.mNumWaves;
                b.removeAllZombies();
                b.fadeOutLevel();
            } else if (b.mApp.isSurvivalMode()) {
                b.mChallenge.mSurvivalStage = 5;
                b.removeAllZombies();
                b.fadeOutLevel();
                b.mBoardFadeOutCounter = 200;
            } else {
                b.removeAllZombies();
                b.fadeOutLevel();
                b.mBoardFadeOutCounter = 200;
            }
        } else if (theChar == '8') {
            b.mApp.mEasyPlantingCheat = !b.mApp.mEasyPlantingCheat;
        } else if (theChar == '7') {
            b.mApp.toggleSlowMo();
        } else if (theChar == '6') {
            b.mApp.toggleFastMo();
        } else if (theChar == 'z') {
            b.mDebugTextMode = b.mDebugTextMode + 1;
            if (b.mDebugTextMode > DebugTextMode.DEBUG_TEXT_COLLISION) {
                b.mDebugTextMode = DebugTextMode.DEBUG_TEXT_NONE;
            }
        }

        if (b.mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            return;
        }

        Zombie aBossZombie = b.getBossZombie();
        if (aBossZombie != null && !aBossZombie.isDeadOrDying()) {
            if (theChar == 'b') {
                aBossZombie.mBossBungeeCounter = 0;
                return;
            }
            if (theChar == 'u') {
                aBossZombie.mSummonCounter = 0;
                return;
            }
            if (theChar == 's') {
                aBossZombie.mBossStompCounter = 0;
                return;
            }
            if (theChar == 'r') {
                aBossZombie.bossRVAttack();
                return;
            }
            if (theChar == 'h') {
                aBossZombie.mBossHeadCounter = 0;
                return;
            }
            if (theChar == 'd') {
                aBossZombie.takeDamage(10000, 0);
                return;
            }
        }

        if (b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS
                || b.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2) {
            if (theChar == 'w') {
                b.addZombie(ZombieType.ZOMBIE_WALLNUT_HEAD, Zombie.ZOMBIE_WAVE_DEBUG);
                return;
            }
            if (theChar == 't') {
                b.addZombie(ZombieType.ZOMBIE_TALLNUT_HEAD, Zombie.ZOMBIE_WAVE_DEBUG);
                return;
            }
            if (theChar == 'j') {
                b.addZombie(ZombieType.ZOMBIE_JALAPENO_HEAD, Zombie.ZOMBIE_WAVE_DEBUG);
                return;
            }
            if (theChar == 'g') {
                b.addZombie(ZombieType.ZOMBIE_GATLING_HEAD, Zombie.ZOMBIE_WAVE_DEBUG);
                return;
            }
            if (theChar == 's') {
                b.addZombie(ZombieType.ZOMBIE_SQUASH_HEAD, Zombie.ZOMBIE_WAVE_DEBUG);
                return;
            }
        }

        if (theChar == 'q') {
            if (LawnApp.isSurvivalEndless(b.mApp.mGameMode)) {
                b.mApp.mEasyPlantingCheat = true;
                for (int y = 0; y < Board.MAX_GRID_SIZE_X; y++) {
                    for (int x = 0; x < Board.MAX_GRID_SIZE_Y; x++) {
                        if (b.canPlantAt(x, y, SeedType.SEED_LILYPAD) == PlantingReason.PLANTING_OK) {
                            b.addPlant(x, y, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
                        }
                        if (b.canPlantAt(x, y, SeedType.SEED_PUMPKINSHELL) == PlantingReason.PLANTING_OK) {
                            if (x <= 6 || b.isPoolSquare(x, y)) {
                                b.addPlant(x, y, SeedType.SEED_PUMPKINSHELL, SeedType.SEED_NONE);
                            }
                        }
                        if (b.canPlantAt(x, y, SeedType.SEED_GATLINGPEA) == PlantingReason.PLANTING_OK) {
                            if (x < 5) {
                                b.addPlant(x, y, SeedType.SEED_GATLINGPEA, SeedType.SEED_NONE);
                            } else if (x == 5) {
                                b.addPlant(x, y, SeedType.SEED_TORCHWOOD, SeedType.SEED_NONE);
                            } else if (x == 6) {
                                b.addPlant(x, y, SeedType.SEED_SPLITPEA, SeedType.SEED_NONE);
                            } else if (y == 2 || y == 3) {
                                b.addPlant(x, y, SeedType.SEED_GLOOMSHROOM, SeedType.SEED_NONE);
                                if (b.canPlantAt(x, y, SeedType.SEED_INSTANT_COFFEE) == PlantingReason.PLANTING_OK) {
                                    b.addPlant(x, y, SeedType.SEED_INSTANT_COFFEE, SeedType.SEED_NONE);
                                }
                            }
                        }
                    }
                }
            } else if (b.mApp.isIZombieLevel()) {
                b.mApp.mEasyPlantingCheat = true;
                for (int i = 0; i < 5; i++) {
                    b.mChallenge.iZombiePlaceZombie(ZombieType.ZOMBIE_FOOTBALL, 6, i);
                }
            } else {
                b.mApp.mEasyPlantingCheat = true;
                for (int y = 0; y < Board.MAX_GRID_SIZE_Y; ++y) {
                    for (int x = 0; x < Board.MAX_GRID_SIZE_X; ++x) {
                        if (b.stageHasRoof() && b.canPlantAt(x, y, SeedType.SEED_FLOWERPOT) == PlantingReason.PLANTING_OK) {
                            b.addPlant(x, y, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
                        }
                        if (b.canPlantAt(x, y, SeedType.SEED_LILYPAD) == PlantingReason.PLANTING_OK) {
                            b.addPlant(x, y, SeedType.SEED_LILYPAD, SeedType.SEED_NONE);
                        }
                        if (b.canPlantAt(x, y, SeedType.SEED_THREEPEATER) == PlantingReason.PLANTING_OK) {
                            b.addPlant(x, y, SeedType.SEED_THREEPEATER, SeedType.SEED_NONE);
                        }
                    }
                }

                if (!b.mChallenge.updateZombieSpawning()) {
                    int aWavesRemaining = Math.min(b.mNumWaves - b.mCurrentWave, 20);
                    while (aWavesRemaining != 0) {
                        b.spawnZombieWave();
                        aWavesRemaining--;
                    }
                }

                if (b.mApp.isScaryPotterLevel()) {
                    for (GridItem aGridItem : b.mGridItems) {
                        if (aGridItem.mDead) {
                            continue;
                        }
                        if (aGridItem.mGridItemType == GridItemType.GRIDITEM_SCARY_POT) {
                            b.mChallenge.scaryPotterOpenPot(aGridItem);
                        }
                    }
                }
            }

            return;
        }

        if (theChar == 'O') {
            b.mApp.mEasyPlantingCheat = true;
            for (int y = 0; y < Board.MAX_GRID_SIZE_Y; y++) {
                for (int x = 0; x < 3; x++) {
                    if (b.canPlantAt(x, y, SeedType.SEED_FLOWERPOT) == PlantingReason.PLANTING_OK) {
                        b.addPlant(x, y, SeedType.SEED_FLOWERPOT, SeedType.SEED_NONE);
                    }
                }
            }
            return;
        }

        if (theChar == '?' || theChar == '/') {
            if (b.mHugeWaveCountDown > 0) {
                b.mHugeWaveCountDown = 1;
            } else {
                b.mZombieCountDown = 6;
            }
            return;
        }

        if (theChar == 'b') {
            b.addZombie(ZombieType.ZOMBIE_BUNGEE, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'o') {
            b.addZombie(ZombieType.ZOMBIE_FOOTBALL, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 's') {
            b.addZombie(ZombieType.ZOMBIE_DOOR, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'L') {
            b.addZombie(ZombieType.ZOMBIE_LADDER, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'y') {
            b.addZombie(ZombieType.ZOMBIE_YETI, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'a') {
            b.addZombie(ZombieType.ZOMBIE_FLAG, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'w') {
            b.addZombie(ZombieType.ZOMBIE_NEWSPAPER, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'F') {
            b.addZombie(ZombieType.ZOMBIE_BALLOON, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'n') {
            if (b.stageHasPool()) {
                b.addZombie(ZombieType.ZOMBIE_SNORKEL, Zombie.ZOMBIE_WAVE_DEBUG);
            }
        }
        if (theChar == 'c') {
            b.addZombie(ZombieType.ZOMBIE_TRAFFIC_CONE, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'm') {
            b.addZombie(ZombieType.ZOMBIE_DANCER, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'h') {
            b.addZombie(ZombieType.ZOMBIE_PAIL, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        //if (theChar == 'H'
        //{
        //	AddZombie(ZombieType::ZOMBIE_PAIL, Zombie::ZOMBIE_WAVE_DEBUG);
        //	AddZombie(ZombieType::ZOMBIE_PAIL, Zombie::ZOMBIE_WAVE_DEBUG);
        //	AddZombie(ZombieType::ZOMBIE_PAIL, Zombie::ZOMBIE_WAVE_DEBUG);
        //	AddZombie(ZombieType::ZOMBIE_PAIL, Zombie::ZOMBIE_WAVE_DEBUG);
        //	AddZombie(ZombieType::ZOMBIE_PAIL, Zombie::ZOMBIE_WAVE_DEBUG);
        //	return;
        //}
        if (theChar == 'D') {
            b.addZombie(ZombieType.ZOMBIE_DIGGER, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'p') {
            b.addZombie(ZombieType.ZOMBIE_POLEVAULTER, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'P') {
            b.addZombie(ZombieType.ZOMBIE_POGO, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'R') {
            if (b.stageHasPool()) {
                b.addZombie(ZombieType.ZOMBIE_DOLPHIN_RIDER, Zombie.ZOMBIE_WAVE_DEBUG);
            }
            return;
        } else if (theChar == 'j') {
            b.addZombie(ZombieType.ZOMBIE_JACK_IN_THE_BOX, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'g') {
            b.addZombie(ZombieType.ZOMBIE_GARGANTUAR, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'G') {
            b.addZombie(ZombieType.ZOMBIE_REDEYE_GARGANTUAR, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'i') {
            b.addZombie(ZombieType.ZOMBIE_ZAMBONI, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'C') {
            b.addZombie(ZombieType.ZOMBIE_CATAPULT, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == '1') {
            Plant aPlant = b.getTopPlantAt(0, 0, PlantPriority.TOPPLANT_ANY);
            if (aPlant != null) {
                aPlant.die();
                b.mChallenge.zombieAtePlant(aPlant);
                return;
            }
        }
        if (theChar == 'B') {
            b.mFogBlownCountDown = 2200;
            return;
        }
        if (theChar == 't') {
            if (!b.canAddBobSled()) {
                int aRow = Common.rand(5);
                int aPos = 400;
                if (b.stageHasPool()) {
                    aRow = Common.rand(2);
                } else if (b.stageHasRoof()) {
                    aPos = 500;
                }
                b.mIceTimer[aRow] = 3000;
                b.mIceMinX[aRow] = aPos;
            }

            b.addZombie(ZombieType.ZOMBIE_BOBSLED, Zombie.ZOMBIE_WAVE_DEBUG);
            return;
        }
        if (theChar == 'r') {
            b.spawnZombiesFromGraves();
            return;
        }
        if (theChar == '0') {
            b.addSunMoney(100);
            b.mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
            return;
        }
        if (theChar == '9') {
            b.addSunMoney(999999);
            b.mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
            return;
        }
        if (theChar == '$') {
            b.mApp.mPlayerInfo.addCoins(100);
            b.mApp.playSample(Resources.SOUND_BUTTONCLICK_ID);
            b.showCoinBank();
            return;
        }
        if (theChar == '-') {
            b.mSunMoney -= 100;
            b.mSunMoney = Math.max(b.mSunMoney, 0);
            return;
        }

        if (theChar == '%') {
            b.mApp.switchScreenMode(b.mApp.mIsWindowed, !b.mApp.is3DAccelerated(), false);
        }
        if (theChar == 'M') {
            b.mApp.mMusic.mBurstOverride -= 2 - (b.mApp.mMusic.mBurstOverride != 1 ? 1 : 0);
            return;
        }

        if (theChar == '\3' && b.mApp.mCtrlDown && b.mApp.mCheatKeys) {
            pvzpCrash();

            if (b.mHugeWaveCountDown > 0) {
                b.mHugeWaveCountDown = 1;
            } else {
                b.mZombieCountDown = 6;
            }
        }
    }
}
