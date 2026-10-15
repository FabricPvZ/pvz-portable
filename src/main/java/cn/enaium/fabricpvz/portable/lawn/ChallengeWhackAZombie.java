package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/** Ported from src/Lawn/Challenge.cpp:945-991, 2491-2532, 4905-4921 (Challenge::MouseDownWhackAZombie, Challenge::WhackAZombiePlaceGraves, Challenge::WhackAZombieUpdate). */
public class ChallengeWhackAZombie {
    public final Challenge c;

    public ChallengeWhackAZombie(Challenge theChallenge) {
        this.c = theChallenge;
    }

    /** Ported from src/Lawn/Challenge.cpp:945-991 ({@code Challenge::MouseDownWhackAZombie}). */
    public void mouseDownWhackAZombie(int theX, int theY) {
        c.mApp.reanimationTryToGet(c.mBoard.mCursorObject.mReanimCursorID).mAnimTime = 0.2f;
        c.mApp.playFoley(FoleyType.FOLEY_SWING);

        Zombie aTopZombie = null;
        for (Zombie aZombie : c.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (!aZombie.isDeadOrDying()) {
                Rect aZombieRect = aZombie.getZombieRect();
                if (Board.getCircleRectOverlap(theX, theY - 20, 45, aZombieRect)) {
                    if (aTopZombie == null || aZombie.mRenderOrder >= aTopZombie.mRenderOrder) {
                        aTopZombie = aZombie;
                    }
                }
            }
        }

        if (aTopZombie != null) {
            if (aTopZombie.mHelmType != HelmType.HELMTYPE_NONE) {
                if (aTopZombie.mHelmType == HelmType.HELMTYPE_PAIL) {
                    c.mApp.playFoley(FoleyType.FOLEY_SHIELD_HIT);
                } else if (aTopZombie.mHelmType == HelmType.HELMTYPE_TRAFFIC_CONE) {
                    c.mApp.playFoley(FoleyType.FOLEY_PLASTIC_HIT);
                }

                aTopZombie.takeHelmDamage(900, 0);
            } else {
                c.mApp.playFoley(FoleyType.FOLEY_BONK);
                c.mApp.addPvzpParticle(theX - 3, theY + 9, RenderLayer.RENDER_LAYER_ABOVE_UI, ParticleEffect.PARTICLE_POW);
                aTopZombie.dieWithLoot();
                c.mBoard.clearCursor();
            }
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:2491-2532 ({@code Challenge::WhackAZombiePlaceGraves}). */
    public void whackAZombiePlaceGraves(int theGraveCount) {
        int aPickCount = 0;
        PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[36];

        for (int aCol = 3; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
                if (!c.mBoard.canAddGraveStoneAt(aCol, aRow)) {
                    continue;
                }

                PvzpWeightedGridArray aPick = new PvzpWeightedGridArray();
                aPick.mWeight = c.mBoard.getTopPlantAt(aCol, aRow, PlantPriority.TOPPLANT_ANY) != null ? 1 : 100000;
                aPick.mX = aCol;
                aPick.mY = aRow;
                aPicks[aPickCount++] = aPick;
            }
        }

        theGraveCount = Math.min(theGraveCount, aPickCount);

        if (aPickCount == 0 || theGraveCount <= 0) {
            return;
        }

        for (int i = 0; i < theGraveCount; i++) {
            PvzpWeightedGridArray aPick = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPickCount);

            for (Plant aPlant : c.mBoard.mPlants) {
                if (aPlant.mDead) {
                    continue;
                }
                if (aPlant.mPlantCol == aPick.mX && aPlant.mRow == aPick.mY) {
                    aPlant.die();
                }
            }

            c.mBoard.addAGraveStone(aPick.mX, aPick.mY);
            aPick.mWeight = 0;
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:4905-4921 ({@code Challenge::WhackAZombieUpdate}). */
    public void whackAZombieUpdate() {
        if (c.mBoard.mSunMoney > 0 && c.mBoard.mTutorialState == TutorialState.TUTORIAL_OFF) {
            c.mBoard.setTutorialState(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_BEFORE_PICK_SEED);
            c.mBoard.mTutorialTimer = 1500;
        }
        if (c.mBoard.mTutorialState == TutorialState.TUTORIAL_WHACK_A_ZOMBIE_BEFORE_PICK_SEED && c.mBoard.mTutorialTimer == 0) {
            c.mBoard.setTutorialState(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_PICK_SEED);
            c.mBoard.mTutorialTimer = 400;
        }
        if (c.mBoard.mTutorialState == TutorialState.TUTORIAL_WHACK_A_ZOMBIE_PICK_SEED && c.mBoard.mTutorialTimer == 0) {
            c.mBoard.setTutorialState(TutorialState.TUTORIAL_WHACK_A_ZOMBIE_COMPLETED);
        }
    }
}
