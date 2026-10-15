/*
 * Ported from src/Lawn/Zombie.cpp (the zombie render slice).
 *
 * Covers: DrawZombiePart (5104), UpdateReanim (5277), DrawBobsledReanim
 * (5386), DrawBungeeReanim (5536), DrawBungeeTarget (5581), DrawDancerReanim (5605), DrawReanim
 * (5651), DrawBungeeCord (5859), GetDrawPos (5895), DrawIceTrap (6198), DrawButter (6240),
 * HasShadow (9297), SetupDrawZombieWon (9336), DrawShadow (9365), DrawBossFireBall (9712),
 * DrawBossBackArm (9729) and DrawBossPart (10526).
 *
 * NOT ported: Zombie::DrawZombie (5021), Zombie::DrawZombieWithParts (5247) and
 * Zombie::DrawZombieHead (5211). All three live inside block comments in src/Lawn/Zombie.cpp and are
 * never declared in src/Lawn/Zombie.h (Zombie.h:177-180), so upstream never compiles or calls them;
 * they are also not compilable — they draw from an atlas global {@code IMAGE_ZOMBIE} that
 * src/Resources.h does not declare and reference {@code ZombieParts::PARTS_BODY}, which is not a
 * member of the enum (Zombie.h:45-59). Porting them would be dead, undefined code.
 *
 * All methods are static with the {@link Zombie} instance as the first parameter; Zombie.java
 * exposes the upstream-named delegators. Zombie state is read/written directly through the public
 * fields (upstream member names).
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Color;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.SexyMatrix3;
import cn.enaium.fabricpvz.portable.sexy.SexyTransform2D;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public final class ZombieRender {

    private ZombieRender() {
    }

    /**
     * Ported from src/Lawn/Zombie.cpp:5104 (Zombie::DrawZombiePart).
     *
     * <p>Kept for completeness: upstream marks it "normally never called" (its only callers are the
     * commented-out DrawZombieHead / DrawZombieWithParts bodies).
     */
    public static void drawZombiePart(Zombie z, Graphics g, Image theImage, int theFrame, int theRow,
            ZombieDrawPosition theDrawPos) {
        int aCelWidth = theImage.getCelWidth();
        int aCelHeight = theImage.getCelHeight();
        float anOffsetX = theDrawPos.mImageOffsetX;
        float anOffsetY = theDrawPos.mImageOffsetY + theDrawPos.mBodyY;
        if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT) {
            anOffsetX -= 120.0f;
            anOffsetY -= 120.0f;
        }
        if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            anOffsetY += 50.0f;
        }
        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            anOffsetY -= 19.0f;
        }

        float aDrawHeight = aCelHeight;
        if (theDrawPos.mClipHeight > Zombie.CLIP_HEIGHT_LIMIT) {
            aDrawHeight = Math.clamp(aCelHeight - theDrawPos.mClipHeight, 0.0f, (float) aCelHeight);
        }

        int anAlpha = 255;
        if (z.mZombieFade >= 0) {
            anAlpha = Math.clamp(255 * z.mZombieFade / 10, 0, 255);
            g.setColorizeImages(true);
            g.setColor(new Color(255, 255, 255, anAlpha));
        }

        boolean aMirror = false;
        if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_IN || z.mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_LEFT) {
            int aFrame = z.getDancerFrame();
            if (!z.mIsEating && (aFrame == 12 || aFrame == 13 || aFrame == 14 || aFrame == 18 || aFrame == 19 || aFrame == 20)) {
                aMirror = true;
                anOffsetX -= 30.0f;
            }
        }
        if (aMirror) {
            anOffsetX = -anOffsetX;
        }

        Rect aSrcRect = new Rect(theFrame * aCelWidth, theRow * aCelHeight, aCelWidth, (int) aDrawHeight);
        Rect aDestRect = new Rect((int) anOffsetX, (int) anOffsetY, aCelWidth, (int) aDrawHeight);
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
            if (z.mMindControlled) {
                aMirror = true;
            }

            g.setColorizeImages(true);
            g.setColor(Color.Black);
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);
        } else if (z.mMindControlled) {
            aMirror = true;
            g.setColorizeImages(true);
            Color aMincontrolledColor = new Color(Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mRed,
                    Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mGreen, Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mBlue,
                    Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mAlpha);
            aMincontrolledColor.mAlpha = anAlpha;
            g.setColor(aMincontrolledColor);
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);

            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        } else if (z.mChilledCounter > 0 || z.mIceTrapCounter > 0) {
            g.setColorizeImages(true);
            g.setColor(new Color(75, 75, 255, anAlpha));
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);

            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        } else {
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);
        }

        if (z.mJustGotShotCounter > 0) {
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.setColorizeImages(true);
            int aGrayness = z.mJustGotShotCounter * 10;
            g.setColor(new Color(aGrayness, aGrayness, aGrayness, 255));
            g.drawImageMirror(theImage, aDestRect, aSrcRect, aMirror);
            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        }

        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Zombie.cpp:5277 (Zombie::UpdateReanim). */
    public static void updateReanim(Zombie z) {
        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (aBodyReanim == null || aBodyReanim.mDead) {
            return;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            if (z.getBodyDamageIndex() == 2 || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING) {
                Reanimation aReanim = z.mApp.reanimationGet(z.mBodyReanimID);
                if (z.mSummonCounter != 0) {
                    // Non-zero means the catapult still carries balls.
                    aReanim.setImageOverride("Zombie_catapult_pole", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE_WITHBALL));
                } else {
                    aReanim.setImageOverride("Zombie_catapult_pole", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE));
                }
            } else if (z.mSummonCounter == 0) {
                aBodyReanim.setImageOverride("Zombie_catapult_pole", Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CATAPULT_POLE));
            }
        }

        ZombieDrawPosition aDrawPos = getDrawPos(z);
        float anOffsetX = aDrawPos.mImageOffsetX + 15.0f;
        float anOffsetY = aDrawPos.mImageOffsetY + aDrawPos.mBodyY - 28.0f + 20.0f;
        if ((z.mZombieType == ZombieType.ZOMBIE_ZAMBONI || z.mZombieType == ZombieType.ZOMBIE_CATAPULT)
                && z.mZombiePhase != ZombiePhase.PHASE_ZOMBIE_BURNED) {
            if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING) {
                float aShakeRange = PvzpCommon.pvzpAnimateCurveFloatTime(0.7f, 1.0f, aBodyReanim.mAnimTime, 0.0f, 1.0f,
                        PvzpCurves.CURVE_EASE_OUT);
                anOffsetX += PvzpCommon.randRangeFloat(-aShakeRange, aShakeRange);
                anOffsetY += PvzpCommon.randRangeFloat(-aShakeRange, aShakeRange);
            } else if (z.mBodyHealth < 200) {
                anOffsetX += PvzpCommon.randRangeFloat(-1.0f, 1.0f);
                anOffsetY += PvzpCommon.randRangeFloat(-1.0f, 1.0f);
            }
        }
        if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL && z.mScaleZombie < 1.0f) {
            anOffsetY += 20.0f - z.mScaleZombie * 20.0f;
        }

        boolean anOpposite = false;
        if (z.isWalkingBackwards()) {
            anOpposite = true;
        }
        if (z.mZombieType == ZombieType.ZOMBIE_DANCER || z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
            anOpposite = false;

            if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_DANCING_IN) {
                if (!z.mIsEating) {
                    anOpposite = true;
                }
            }

            if (z.mMindControlled) {
                anOpposite = !anOpposite;
            }
        }
        if (anOpposite) {
            anOffsetX += 90.0f * z.mScaleZombie;
        }

        aBodyReanim.mOverlayMatrix.m10 = 0.0f;
        aBodyReanim.mOverlayMatrix.m20 = 0.0f;
        aBodyReanim.mOverlayMatrix.m11 = 0.0f;
        aBodyReanim.mOverlayMatrix.m21 = 0.0f;
        aBodyReanim.overrideScale(z.mScaleZombie, z.mScaleZombie);
        aBodyReanim.setPosition(anOffsetX + 30.0f - z.mScaleZombie * 30.0f, anOffsetY + 120.0f - z.mScaleZombie * 120.0f);
        if (anOpposite) {
            aBodyReanim.mOverlayMatrix.m00 = -z.mScaleZombie;
        }

        Reanimation aMoweredReanim = z.mApp.reanimationTryToGet(z.mMoweredReanimID);
        if (aMoweredReanim != null) {
            aMoweredReanim.update();

            SexyTransform2D aOverlayMatrix = new SexyTransform2D();
            aMoweredReanim.getAttachmentOverlayMatrix(0, aOverlayMatrix);
            aOverlayMatrix.m00 *= aBodyReanim.mOverlayMatrix.m00;
            aOverlayMatrix.m10 *= aBodyReanim.mOverlayMatrix.m00;
            aOverlayMatrix.m01 *= aBodyReanim.mOverlayMatrix.m11;
            aOverlayMatrix.m11 *= aBodyReanim.mOverlayMatrix.m11;
            aOverlayMatrix.m02 *= aBodyReanim.mOverlayMatrix.m00;
            aOverlayMatrix.m12 *= aBodyReanim.mOverlayMatrix.m11;
            aOverlayMatrix.m02 += aBodyReanim.mOverlayMatrix.m11;
            aOverlayMatrix.m12 += aBodyReanim.mOverlayMatrix.m12;
            aBodyReanim.mOverlayMatrix = aOverlayMatrix;
        }

        aBodyReanim.update();
        aBodyReanim.propogateColorToAttachments();
    }

    /** Ported from src/Lawn/Zombie.cpp:5386 (Zombie::DrawBobsledReanim). */
    public static void drawBobsledReanim(Zombie z, Graphics g, ZombieDrawPosition theDrawPos, boolean theBeforeZombie) {
        int aPosition = z.getBobsledPosition();
        boolean aDrawFront = false;
        boolean aDrawBack = false;
        Zombie aZombieLeader;
        if (z.mFromWave == Zombie.ZOMBIE_WAVE_CUTSCENE) {
            aZombieLeader = z;
        } else {
            if (aPosition == -1) {
                return;
            }
            if (aPosition == 0) {
                aZombieLeader = z;
            } else {
                aZombieLeader = z.mBoard.zombieGet(z.mRelatedZombieID);
            }
        }

        if (z.mFromWave == Zombie.ZOMBIE_WAVE_CUTSCENE) {
            if (theBeforeZombie) {
                // seed-picker cutscene: draw sled back, zombie, then sled front
                aDrawBack = true;
            } else {
                aDrawFront = true;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_CRASHING) {
            if (aPosition == 0 && !theBeforeZombie) {
                // crashed: draw sled back and front after the leader zombie
                aDrawFront = true;
                aDrawBack = true;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_SLIDING || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
            if (aPosition == 2 && theBeforeZombie) {
                // sliding or burned: draw sled back and front before the 2nd zombie
                aDrawFront = true;
                aDrawBack = true;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_BOARDING) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aBodyReanim.mAnimTime < 0.5f) {
                // while airborne: draw sled back and front before the 2nd zombie
                if (aPosition == 2 && theBeforeZombie) {
                    aDrawFront = true;
                    aDrawBack = true;
                }
            } else if (aPosition == 0 && !theBeforeZombie) {
                // once boarded: draw sled front after the leader zombie
                aDrawFront = true;
            } else if (aPosition == 3 && theBeforeZombie) {
                // once boarded: draw sled back before the last zombie
                aDrawBack = true;
            }
        }

        float aOffsetX = aZombieLeader.mPosX + theDrawPos.mImageOffsetX - z.mPosX - 76.0f;
        float aOffsetY = 15.0f;
        int aBobsledDamageStatus;
        if (z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_CRASHING) {
            aBobsledDamageStatus = 3;
            int aAlpha = PvzpCommon.pvzpAnimateCurve(30, 0, z.mPhaseCounter, 255, 0, PvzpCurves.CURVE_LINEAR);
            // rewind to the position where the crash started
            aOffsetX += (Zombie.BOBSLED_CRASH_TIME - z.mPhaseCounter) * z.mVelX / Zombie.ZOMBIE_LIMP_SPEED_FACTOR;
            // horizontal slide from the sled's momentum
            aOffsetX -= PvzpCommon.pvzpAnimateCurveFloat(Zombie.BOBSLED_CRASH_TIME, 0, z.mPhaseCounter, 0.0f, 50.0f,
                    PvzpCurves.CURVE_EASE_OUT);
            aOffsetY += PvzpCommon.pvzpAnimateCurveFloat(Zombie.BOBSLED_CRASH_TIME, 75, z.mPhaseCounter, 5.0f, 10.0f,
                    PvzpCurves.CURVE_LINEAR);
            if (aAlpha != 255) {
                g.setColorizeImages(true);
                g.setColor(new Color(255, 255, 255, aAlpha));
            }
        } else {
            aBobsledDamageStatus = aZombieLeader.getHelmDamageIndex();
        }

        Image aImage;
        if (aBobsledDamageStatus == 0) {
            aImage = Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOBSLED1);
        } else if (aBobsledDamageStatus == 1) {
            aImage = Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOBSLED2);
        } else if (aBobsledDamageStatus == 2) {
            aImage = Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOBSLED3);
        } else {
            aImage = Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOBSLED4);
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
            g.setColorizeImages(true);
            g.setColor(Color.Black);
        }

        if (aDrawBack && aBobsledDamageStatus != 3) {
            g.drawImageF(Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOBSLED_INSIDE), aOffsetX, aOffsetY);
        }
        if (aDrawFront) {
            g.drawImageF(aImage, aOffsetX, aOffsetY);
        }

        if (aZombieLeader.mJustGotShotCounter > 0) {
            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            g.setColorizeImages(true);
            int aGrayness = aZombieLeader.mJustGotShotCounter * 10;
            g.setColor(new Color(aGrayness, aGrayness, aGrayness, 255));

            if (aDrawBack && aBobsledDamageStatus != 3) {
                g.drawImageF(Resources.getImageById(ImageID.IMAGE_ZOMBIE_BOBSLED_INSIDE), aOffsetX, aOffsetY);
            }
            if (aDrawFront) {
                g.drawImageF(aImage, aOffsetX, aOffsetY);
            }

            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
        }

        g.setColorizeImages(false);
    }

    /** Ported from src/Lawn/Zombie.cpp:5536 (Zombie::DrawBungeeReanim). */
    public static void drawBungeeReanim(Zombie z, Graphics g) {
        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        drawBungeeCord(z, g, -22);
        aBodyReanim.draw(g);

        Zombie aDroppedZombie = z.mBoard.zombieTryToGet(z.mRelatedZombieID);
        if (aDroppedZombie != null) {
            Graphics aDropGraphics = new Graphics(g);
            aDropGraphics.mTransY -= z.mAltitude;
            aDropGraphics.mTransX += aDroppedZombie.mPosX - z.mPosX;

            ZombieDrawPosition aDroppedDrawPos = getDrawPos(aDroppedZombie);
            drawReanim(aDroppedZombie, aDropGraphics, aDroppedDrawPos, Reanimation.RENDER_GROUP_NORMAL);
        } else {
            Plant aPlant = z.mBoard.mPlants.dataArrayTryToGet(z.mTargetPlantID);
            if (aPlant != null) {
                Graphics aPlantGraphics = new Graphics(g);
                aPlantGraphics.mTransY += 30.0f - z.mAltitude;
                if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_RISING) {
                    if (aPlant.mSeedType == SeedType.SEED_SPIKEWEED || aPlant.mSeedType == SeedType.SEED_SPIKEROCK) {
                        aPlantGraphics.mTransY -= 34.0f;
                    }
                }
                if (aPlant.mPlantCol <= 4 && z.mBoard.stageHasRoof()) {
                    aPlantGraphics.mTransY += 10;
                }

                aPlant.draw(aPlantGraphics);
            }
        }

        aBodyReanim.drawRenderGroup(g, Zombie.RENDER_GROUP_ARMS);
    }

    /** Ported from src/Lawn/Zombie.cpp:5581 (Zombie::DrawBungeeTarget). */
    public static void drawBungeeTarget(Zombie z, Graphics g) {
        if (!z.isOnBoard() || z.mApp.isFinalBossLevel()) {
            return;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_HIT_OUCHY || z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_RISING) {
            return;
        }

        if (z.mRelatedZombieID != ZombieID.ZOMBIEID_NULL) {
            return;
        }

        ZombieDrawPosition aDrawPos = getDrawPos(z);
        float aTargetX = z.mX + 10.0f;
        float aTargetY = z.mY + 60.0f + aDrawPos.mBodyY + aDrawPos.mImageOffsetY;
        if (z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_DIVING || z.mZombiePhase == ZombiePhase.PHASE_BUNGEE_DIVING_SCREAMING) {
            aTargetX += PvzpCommon.pvzpAnimateCurveFloat(Zombie.BUNGEE_ZOMBIE_HEIGHT, Zombie.BUNGEE_ZOMBIE_HEIGHT - 400,
                    (int) z.mAltitude, 30.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
            aTargetY += PvzpCommon.pvzpAnimateCurveFloat(Zombie.BUNGEE_ZOMBIE_HEIGHT, Zombie.BUNGEE_ZOMBIE_HEIGHT - 400,
                    (int) z.mAltitude, -600.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
        }

        g.drawImageF(Resources.getImageById(ImageID.IMAGE_BUNGEETARGET), aTargetX, aTargetY + z.mAltitude);
    }

    /** Ported from src/Lawn/Zombie.cpp:5605 (Zombie::DrawDancerReanim). */
    public static void drawDancerReanim(Zombie z, Graphics g) {
        Color aSpotLightColor = null;
        boolean aDrawSpotLight = false;
        if (z.mZombiePhase != ZombiePhase.PHASE_DANCER_DANCING_IN && z.mZombiePhase != ZombiePhase.PHASE_DANCER_SNAPPING_FINGERS
                && z.mZombiePhase != ZombiePhase.PHASE_ZOMBIE_NORMAL && z.mZombiePhase != ZombiePhase.PHASE_ZOMBIE_DYING
                && z.mApp.mGameScene != GameScenes.SCENE_ZOMBIES_WON) {
            aDrawSpotLight = true;

            switch (z.mZombieAge >= 700 ? z.mZombieAge / 100 * 7 % 5 : 0) {
            case 0:
                aSpotLightColor = new Color(250, 250, 160);
                break;
            case 1:
                aSpotLightColor = new Color(114, 234, 170);
                break;
            case 2:
                aSpotLightColor = new Color(216, 126, 202);
                break;
            case 3:
                aSpotLightColor = new Color(90, 110, 140);
                break;
            case 4:
                aSpotLightColor = new Color(240, 90, 130);
                break;
            default:
                break;
            }

            g.setColorizeImages(true);
            g.setColor(aSpotLightColor);
            drawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_SPOTLIGHT2), -30.0f, 60.0f, 4.0f, 4.0f);
            g.setColorizeImages(false);
        }

        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        aBodyReanim.draw(g);

        if (aDrawSpotLight) {
            g.setColorizeImages(true);
            g.setColor(aSpotLightColor);
            drawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_SPOTLIGHT), -30.0f, -480.0f, 4.0f, 4.0f);
            g.setColorizeImages(false);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:5651 (Zombie::DrawReanim). */
    public static void drawReanim(Zombie z, Graphics g, ZombieDrawPosition theDrawPos, int theBaseRenderGroup) {
        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        if (aBodyReanim == null) {
            return;
        }

        if (theDrawPos.mClipHeight > Zombie.CLIP_HEIGHT_LIMIT) {
            float aDrawHeight = 120.0f - theDrawPos.mClipHeight + 71.0f;
            g.setClipRect((int) (theDrawPos.mImageOffsetX - 200.0f),
                    (int) (theDrawPos.mImageOffsetY + theDrawPos.mBodyY - 78.0f), 520, (int) aDrawHeight);
        }

        int aFadeAlpha = 255;
        if (z.mZombieFade >= 0) {
            aFadeAlpha = Math.clamp(255 * z.mZombieFade / 10, 0, 255);
        }

        Color aColorOverride = new Color(255, 255, 255, aFadeAlpha);
        Color aExtraAdditiveColor = Color.Black;
        boolean aEnableExtraAdditiveDraw = false;
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
            aColorOverride = new Color(0, 0, 0, aFadeAlpha);
            aExtraAdditiveColor = Color.Black;
            aEnableExtraAdditiveDraw = false;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BOSS && z.mZombiePhase != ZombiePhase.PHASE_ZOMBIE_DYING
                && z.mBodyHealth < z.mBodyMaxHealth / Zombie.BOSS_FLASH_HEALTH_FRACTION) {
            int aGrayness = PvzpCommon.pvzpAnimateCurve(0, 39, z.mBoard.mMainCounter % 40, 155, 255, PvzpCurves.CURVE_BOUNCE);
            if (z.mChilledCounter > 0 || z.mIceTrapCounter > 0) {
                int aColdColor = PvzpCommon.pvzpAnimateCurve(0, 39, z.mBoard.mMainCounter % 40, 65, 75, PvzpCurves.CURVE_BOUNCE);
                aColorOverride = new Color(aColdColor, aColdColor, aGrayness, aFadeAlpha);
            } else {
                aColorOverride = new Color(aGrayness, aGrayness, aGrayness, aFadeAlpha);
            }

            aExtraAdditiveColor = Color.Black;
            aEnableExtraAdditiveDraw = false;
        } else if (z.mMindControlled) {
            aColorOverride = new Color(Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mRed, Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mGreen,
                    Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mBlue, Zombie.ZOMBIE_MINDCONTROLLED_COLOR.mAlpha);
            aColorOverride.mAlpha = aFadeAlpha;
            aExtraAdditiveColor = aColorOverride;
            aEnableExtraAdditiveDraw = true;
        } else if (z.mChilledCounter > 0 || z.mIceTrapCounter > 0) {
            aColorOverride = new Color(75, 75, 255, aFadeAlpha);
            aExtraAdditiveColor = aColorOverride;
            aEnableExtraAdditiveDraw = true;
        } else if (z.mZombieHeight == ZombieHeight.HEIGHT_ZOMBIQUARIUM && z.mBodyHealth < 100) {
            aColorOverride = new Color(100, 150, 25, aFadeAlpha);
            aExtraAdditiveColor = aColorOverride;
            aEnableExtraAdditiveDraw = true;
        }
        if (z.mJustGotShotCounter > 0 && !z.isBobsledTeamWithSled()) {
            int aGrayness = z.mJustGotShotCounter * 10;
            Color aHighlightColor = new Color(aGrayness, aGrayness, aGrayness, 255);
            aExtraAdditiveColor = PvzpCommon.colorAdd(aHighlightColor, aExtraAdditiveColor);
            aEnableExtraAdditiveDraw = true;
        }
        aBodyReanim.mColorOverride = aColorOverride;
        aBodyReanim.mExtraAdditiveColor = aExtraAdditiveColor;
        aBodyReanim.mEnableExtraAdditiveDraw = aEnableExtraAdditiveDraw;

        if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED) {
            drawBobsledReanim(z, g, theDrawPos, true);
            aBodyReanim.drawRenderGroup(g, theBaseRenderGroup);
            drawBobsledReanim(z, g, theDrawPos, false);
        } else if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
            drawBungeeReanim(z, g);
        } else if (z.mZombieType == ZombieType.ZOMBIE_DANCER) {
            drawDancerReanim(z, g);
        } else {
            aBodyReanim.drawRenderGroup(g, theBaseRenderGroup);
        }

        if (z.mShieldType != ShieldType.SHIELDTYPE_NONE) {
            if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_BURNED) {
                aBodyReanim.mColorOverride = new Color(0, 0, 0, aFadeAlpha);
                aBodyReanim.mExtraAdditiveColor = Color.Black;
                aBodyReanim.mEnableExtraAdditiveDraw = false;
            } else if (z.mShieldJustGotShotCounter > 0) {
                int aGrayness = z.mShieldJustGotShotCounter * 10;
                aBodyReanim.mColorOverride = new Color(aGrayness, aGrayness, aGrayness, aFadeAlpha);
                aBodyReanim.mExtraAdditiveColor = Color.White;
                aBodyReanim.mEnableExtraAdditiveDraw = true;
            } else {
                aBodyReanim.mColorOverride = new Color(255, 255, 255, aFadeAlpha);
                aBodyReanim.mExtraAdditiveColor = Color.Black;
                aBodyReanim.mEnableExtraAdditiveDraw = false;
            }

            float aShieldHitOffset = 0.0f;
            if (z.mShieldRecoilCounter > 0) {
                aShieldHitOffset = PvzpCommon.pvzpAnimateCurveFloat(12, 0, z.mShieldRecoilCounter, 3.0f, 0.0f, PvzpCurves.CURVE_LINEAR);
            }

            g.mTransX += aShieldHitOffset;
            aBodyReanim.drawRenderGroup(g, Zombie.RENDER_GROUP_SHIELD);
            g.mTransX -= aShieldHitOffset;
        }

        if (z.mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER || z.mShieldType == ShieldType.SHIELDTYPE_DOOR
                || z.mShieldType == ShieldType.SHIELDTYPE_LADDER) {
            aBodyReanim.mColorOverride = aColorOverride;
            aBodyReanim.mExtraAdditiveColor = aExtraAdditiveColor;
            aBodyReanim.mEnableExtraAdditiveDraw = aEnableExtraAdditiveDraw;
            aBodyReanim.drawRenderGroup(g, Zombie.RENDER_GROUP_OVER_SHIELD);
        }

        if (z.mZombatarHeadReanimID != ReanimationID.REANIMATIONID_NULL) {
            aBodyReanim.drawRenderGroup(g, Zombie.RENDER_GROUP_ZOMBATAR_HEAD);
        }

        g.clearClipRect();
    }

    /** Ported from src/Lawn/Zombie.cpp:5859 (Zombie::DrawBungeeCord). */
    public static void drawBungeeCord(Zombie z, Graphics g, int theOffsetX) {
        int aCordCelHeight = (int) (Resources.getImageById(ImageID.IMAGE_BUNGEECORD).getCelHeight() * z.mScaleZombie);
        float[] aPos = new float[2];
        z.getTrackPosition("Zombie_bungi_body", aPos);
        float aPosX = aPos[0];
        float aPosY = aPos[1];

        boolean aSetClip = false;
        if (z.isOnBoard() && z.mApp.isFinalBossLevel()) {
            Zombie aBoss = z.mBoard.getBossZombie();

            int aClipAmount = 55;
            if (aBoss.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_LEAVE) {
                Reanimation aBossReanim = z.mApp.reanimationGet(aBoss.mBodyReanimID);
                aClipAmount = (int) PvzpCommon.pvzpAnimateCurveFloatTime(0.0f, 0.2f, aBossReanim.mAnimTime, 55.0f, 0.0f,
                        PvzpCurves.CURVE_LINEAR);
            }

            if (z.mTargetCol > aBoss.mTargetCol) {
                g.setClipRect(new Rect((int) (-g.getTransX()), (int) (aClipAmount - g.getTransY()),
                        GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT));
                aSetClip = true;
            }
        }

        for (float y = aPosY - aCordCelHeight; y > -aCordCelHeight; y -= aCordCelHeight) {
            drawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_BUNGEECORD), theOffsetX + 61.0f - 4.0f / z.mScaleZombie,
                    y - z.mPosY, z.mScaleZombie, z.mScaleZombie);
        }

        if (aSetClip) {
            g.clearClipRect();
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:5895 (Zombie::GetDrawPos). */
    public static ZombieDrawPosition getDrawPos(Zombie z) {
        ZombieDrawPosition theDrawPos = new ZombieDrawPosition();
        theDrawPos.mImageOffsetX = z.mPosX - z.mX;
        theDrawPos.mImageOffsetY = z.mPosY - z.mY;

        if (z.mIsEating) {
            theDrawPos.mHeadX = 47;
            theDrawPos.mHeadY = 4;
        } else {
            switch (z.mFrame) {
            case 0:
                theDrawPos.mHeadX = 50;
                theDrawPos.mHeadY = 2;
                break;
            case 1:
                theDrawPos.mHeadX = 49;
                theDrawPos.mHeadY = 1;
                break;
            case 2:
                theDrawPos.mHeadX = 49;
                theDrawPos.mHeadY = 2;
                break;
            case 3:
                theDrawPos.mHeadX = 48;
                theDrawPos.mHeadY = 4;
                break;
            case 4:
                theDrawPos.mHeadX = 48;
                theDrawPos.mHeadY = 5;
                break;
            case 5:
                theDrawPos.mHeadX = 48;
                theDrawPos.mHeadY = 4;
                break;
            case 6:
                theDrawPos.mHeadX = 48;
                theDrawPos.mHeadY = 2;
                break;
            case 7:
                theDrawPos.mHeadX = 49;
                theDrawPos.mHeadY = 1;
                break;
            case 8:
                theDrawPos.mHeadX = 49;
                theDrawPos.mHeadY = 2;
                break;
            case 9:
                theDrawPos.mHeadX = 50;
                theDrawPos.mHeadY = 4;
                break;
            case 10:
                theDrawPos.mHeadX = 50;
                theDrawPos.mHeadY = 5;
                break;
            default:
                theDrawPos.mHeadX = 50;
                theDrawPos.mHeadY = 4;
                break;
            }
        }

        theDrawPos.mArmY = theDrawPos.mHeadY / 2;

        switch (z.mZombieType) {
        case ZombieType.ZOMBIE_FOOTBALL:
            theDrawPos.mImageOffsetY -= 16.0f;
            break;
        case ZombieType.ZOMBIE_YETI:
            theDrawPos.mImageOffsetY -= 20.0f;
            break;
        case ZombieType.ZOMBIE_CATAPULT:
            theDrawPos.mImageOffsetX -= 25.0f;
            theDrawPos.mImageOffsetY -= 18.0f;
            break;
        case ZombieType.ZOMBIE_POGO:
            theDrawPos.mImageOffsetY += 16.0f;
            break;
        case ZombieType.ZOMBIE_BALLOON:
            theDrawPos.mImageOffsetY += 17.0f;
            break;
        case ZombieType.ZOMBIE_POLEVAULTER:
            theDrawPos.mImageOffsetX -= 6.0f;
            theDrawPos.mImageOffsetY -= 11.0f;
            break;
        case ZombieType.ZOMBIE_ZAMBONI:
            theDrawPos.mImageOffsetX += 68.0f;
            theDrawPos.mImageOffsetY -= 23.0f;
            break;
        case ZombieType.ZOMBIE_GARGANTUAR:
        case ZombieType.ZOMBIE_REDEYE_GARGANTUAR:
            theDrawPos.mImageOffsetY -= 8.0f;
            break;
        case ZombieType.ZOMBIE_BOBSLED:
            theDrawPos.mImageOffsetY -= 12.0f;
            break;
        default:
            break;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE) {
            theDrawPos.mBodyY = -z.mAltitude;

            if (z.mInPool) {
                theDrawPos.mClipHeight = theDrawPos.mBodyY;
            } else {
                float aHeightLimit = Math.min(z.mPhaseCounter, 40);
                theDrawPos.mClipHeight = theDrawPos.mBodyY + aHeightLimit;
            }

            if (z.isOnHighGround()) {
                theDrawPos.mBodyY -= GameConstants.HIGH_GROUND_HEIGHT;
            }

            return theDrawPos;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER) {
            theDrawPos.mBodyY = -z.mAltitude;
            theDrawPos.mClipHeight = Zombie.CLIP_HEIGHT_OFF;

            if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL) {
                Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);

                if (aBodyReanim.mAnimTime >= 0.56f && aBodyReanim.mAnimTime <= 0.65f) {
                    // jumping onto the dolphin: takeoff
                    theDrawPos.mClipHeight = 0.0f;
                } else if (aBodyReanim.mAnimTime >= 0.75f) {
                    // jumping onto the dolphin: falling
                    theDrawPos.mClipHeight = -z.mAltitude - 10.0f;
                }
            } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING) {
                // compensates the mPosX -= 70.0f applied when mounting the dolphin
                theDrawPos.mImageOffsetX += 70.0f;

                if (z.mZombieHeight == ZombieHeight.HEIGHT_DRAGGED_UNDER) {
                    theDrawPos.mClipHeight = -z.mAltitude - 15.0f;
                } else {
                    theDrawPos.mClipHeight = -z.mAltitude - 10.0f;
                }
            } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP) {
                theDrawPos.mImageOffsetX += 70.0f + z.mAltitude;

                Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
                if (aBodyReanim.mAnimTime <= 0.06f) {
                    // before jumping out of the water
                    theDrawPos.mClipHeight = -z.mAltitude - 10.0f;
                } else if (aBodyReanim.mAnimTime >= 0.5f && aBodyReanim.mAnimTime <= 0.76f) {
                    // mid-jump (after leaving the water, before re-entering)
                    theDrawPos.mClipHeight = -13.0f;
                }
            } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_IN_POOL
                    || z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING) {
                // compensates for the distance advanced during the jump
                theDrawPos.mImageOffsetY += 50.0f;

                if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING) {
                    theDrawPos.mClipHeight = -z.mAltitude + 44.0f;
                } else if (z.mZombieHeight == ZombieHeight.HEIGHT_DRAGGED_UNDER) {
                    theDrawPos.mClipHeight = -z.mAltitude + 36.0f;
                }
            } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING && z.mZombieHeight == ZombieHeight.HEIGHT_OUT_OF_POOL) {
                theDrawPos.mClipHeight = -z.mAltitude;
            } else if (z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING_WITHOUT_DOLPHIN
                    && z.mZombieHeight == ZombieHeight.HEIGHT_OUT_OF_POOL) {
                theDrawPos.mClipHeight = -z.mAltitude;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
            theDrawPos.mBodyY = -z.mAltitude;
            theDrawPos.mClipHeight = Zombie.CLIP_HEIGHT_OFF;

            if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL) {
                Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
                if (aBodyReanim.mAnimTime >= 0.8f) {
                    // after entering the water
                    theDrawPos.mClipHeight = -10.0f;
                }
            } else if (z.mInPool) {
                theDrawPos.mClipHeight = -z.mAltitude - 5.0f;
                theDrawPos.mClipHeight += 20.0f - 20.0f * z.mScaleZombie;
            }
        } else if (z.mInPool) {
            theDrawPos.mBodyY = -z.mAltitude;
            theDrawPos.mClipHeight = -z.mAltitude - 7.0f;
            theDrawPos.mClipHeight += 10.0f - 10.0f * z.mScaleZombie;

            if (z.mIsEating) {
                theDrawPos.mClipHeight += 7.0f;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING) {
            theDrawPos.mBodyY = -z.mAltitude;
            theDrawPos.mClipHeight = -z.mAltitude;

            if (z.isOnHighGround()) {
                theDrawPos.mBodyY -= GameConstants.HIGH_GROUND_HEIGHT;
            }
        } else if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE) {
            theDrawPos.mBodyY = -z.mAltitude;

            if (z.mPhaseCounter > 20) {
                theDrawPos.mClipHeight = -z.mAltitude;
            } else {
                theDrawPos.mClipHeight = Zombie.CLIP_HEIGHT_OFF;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
            theDrawPos.mBodyY = -z.mAltitude;
            theDrawPos.mImageOffsetX -= 18.0f;

            if (z.isOnHighGround()) {
                theDrawPos.mBodyY -= GameConstants.HIGH_GROUND_HEIGHT;
            }

            theDrawPos.mClipHeight = Zombie.CLIP_HEIGHT_OFF;
        } else {
            theDrawPos.mBodyY = -z.mAltitude;
            theDrawPos.mClipHeight = Zombie.CLIP_HEIGHT_OFF;
        }

        return theDrawPos;
    }

    /** Ported from src/Lawn/Zombie.cpp:6198 (Zombie::DrawIceTrap). */
    public static void drawIceTrap(Zombie z, Graphics g, ZombieDrawPosition theDrawPos, boolean theFront) {
        if (z.mInPool || z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            return;
        }

        float aOffsetX = 46.0f;
        float aOffsetY = theDrawPos.mBodyY + 92.0f;
        float aScale = 1.0f;
        switch (z.mZombieType) {
        case ZombieType.ZOMBIE_POGO:
            aOffsetX -= 10.0f;
            aOffsetY += 20.0f;
            break;
        case ZombieType.ZOMBIE_GARGANTUAR:
        case ZombieType.ZOMBIE_REDEYE_GARGANTUAR:
            aOffsetX -= 20.0f;
            aOffsetY -= 7.0f;
            aScale = 1.6f;
            break;
        case ZombieType.ZOMBIE_BUNGEE:
            aOffsetX -= 45.0f;
            aOffsetY -= 23.0f;
            aScale = 1.2f;
            break;
        case ZombieType.ZOMBIE_DIGGER:
            aOffsetX -= 27.0f;
            break;
        case ZombieType.ZOMBIE_CATAPULT:
            aOffsetX += 32.0f;
            break;
        case ZombieType.ZOMBIE_BALLOON:
            aOffsetX -= 9.0f;
            aOffsetY += 27.0f;
            break;
        default:
            break;
        }

        drawImageScaledF(g, Resources.getImageById(theFront ? ImageID.IMAGE_ICETRAP : ImageID.IMAGE_ICETRAP2), aOffsetX, aOffsetY,
                aScale, aScale);
    }

    /** Ported from src/Lawn/Zombie.cpp:6240 (Zombie::DrawButter). */
    public static void drawButter(Zombie z, Graphics g, ZombieDrawPosition theDrawPos) {
        float aOffsetX = z.mPosX + theDrawPos.mImageOffsetX + theDrawPos.mHeadX + 11.0f;
        float aOffsetY = z.mPosY + theDrawPos.mImageOffsetY + theDrawPos.mHeadY + theDrawPos.mBodyY + 21.0f;
        float aScale = 1.0f;
        float[] aTrackPos = {aOffsetX, aOffsetY};
        if (z.mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_MADDENING) {
            z.getTrackPosition("anim_head_look", aTrackPos);
        } else if (z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            z.getTrackPosition("Zombie_catapult_driver_head", aTrackPos);
        } else if (z.mBodyReanimID != ReanimationID.REANIMATIONID_NULL) {
            z.getTrackPosition("anim_head1", aTrackPos);
        }
        aOffsetX = aTrackPos[0];
        aOffsetY = aTrackPos[1];
        aOffsetX -= z.mPosX + 29.0f;
        aOffsetY -= z.mPosY + 36.0f;

        switch (z.mZombieType) {
        case ZombieType.ZOMBIE_POGO:
            aOffsetY -= 5.0f;
            break;
        case ZombieType.ZOMBIE_GARGANTUAR:
        case ZombieType.ZOMBIE_REDEYE_GARGANTUAR:
            aOffsetX -= 5.0f;
            aOffsetY -= 15.0f;
            aScale = 1.2f;
            break;
        case ZombieType.ZOMBIE_SQUASH_HEAD:
            aOffsetX += 6.0f;
            aOffsetY -= 9.0f;
            break;
        case ZombieType.ZOMBIE_WALLNUT_HEAD:
            aOffsetX -= 6.0f;
            aOffsetY -= 1.0f;
            break;
        case ZombieType.ZOMBIE_TALLNUT_HEAD:
            aOffsetX -= 24.0f;
            aOffsetY -= 39.0f;
            break;
        default:
            break;
        }

        drawImageScaledF(g, Resources.getImageById(ImageID.IMAGE_REANIM_CORNPULT_BUTTER_SPLAT), aOffsetX, aOffsetY, aScale, aScale);
    }

    /** Ported from src/Lawn/Zombie.cpp:9297 (Zombie::HasShadow). */
    public static boolean hasShadow(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_DYING
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
                || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING
                || z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
                || z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING
                || z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_BOARDING
                || z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
                || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL
                || z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL) {
            return false;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI || z.mZombieType == ZombieType.ZOMBIE_CATAPULT
                || z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            return false;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
            if (!z.isOnBoard() || z.mHitUmbrella) {
                return false;
            }
        }

        if (z.mZombieHeight == ZombieHeight.HEIGHT_DRAGGED_UNDER || z.mZombieHeight == ZombieHeight.HEIGHT_IN_TO_CHIMNEY
                || z.mZombieHeight == ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED) {
            return false;
        }

        if (z.mInPool) {
            return false;
        }

        return z.mApp.mGameMode != GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL || z.mFromWave == Zombie.ZOMBIE_WAVE_UI;
    }

    /** Ported from src/Lawn/Zombie.cpp:9336 (Zombie::SetupDrawZombieWon). */
    public static boolean setupDrawZombieWon(Zombie z, Graphics g) {
        if (z.mFromWave != Zombie.ZOMBIE_WAVE_WINNER) {
            return true;
        }

        if (!z.mBoard.mCutScene.showZombieWalking()) {
            return false;
        }

        switch (z.mBoard.mBackground) {
        case BackgroundType.BACKGROUND_1_DAY:
        case BackgroundType.BACKGROUND_2_NIGHT:
            g.clipRect(-123 - z.mX, -z.mY, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
            break;
        case BackgroundType.BACKGROUND_3_POOL:
        case BackgroundType.BACKGROUND_4_FOG:
            g.clipRect(-172 - z.mX, -z.mY, GameConstants.BOARD_WIDTH, GameConstants.BOARD_HEIGHT);
            break;
        case BackgroundType.BACKGROUND_5_ROOF:
        case BackgroundType.BACKGROUND_6_BOSS:
            g.clipRect(-220 - z.mX, -z.mY, GameConstants.BOARD_WIDTH, 187);
            break;
        default:
            break;
        }

        return true;
    }

    /** Ported from src/Lawn/Zombie.cpp:9365 (Zombie::DrawShadow). */
    public static void drawShadow(Zombie z, Graphics g) {
        ZombieDrawPosition aDrawPos = getDrawPos(z);
        if (z.mApp.mGameScene == GameScenes.SCENE_ZOMBIES_WON && !setupDrawZombieWon(z, g)) {
            return;
        }

        // shadows look really dumb in Zombiquarium
        if (z.mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            return;
        }

        int aShadowType = 0;
        float aShadowOffsetX = aDrawPos.mImageOffsetX;
        float aShadowOffsetY = aDrawPos.mImageOffsetY + aDrawPos.mBodyY;
        float aScale = z.mScaleZombie;
        aShadowOffsetX += z.mScaleZombie * 20.0f - 20.0f;
        if (z.isOnBoard() && z.mBoard.stageIsNight()) {
            aShadowType = 1;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
            if (z.isWalkingBackwards()) {
                aShadowOffsetX -= 11.0f * z.mScaleZombie;
            } else {
                aShadowOffsetX += 20.0f + 21.0f * z.mScaleZombie;
            }
            aShadowOffsetY += 16.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_NEWSPAPER) {
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 5.0f;
            } else {
                aShadowOffsetX += 29.0f;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += -5.0f;
            } else {
                aShadowOffsetX += 36.0f;
            }
            aShadowOffsetY += 11.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED) {
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 13.0f;
            } else {
                aShadowOffsetX += 20.0f;
            }
            aShadowOffsetY += 13.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_IMP) {
            aScale *= 0.6f;
            aShadowOffsetY += 7.0f;
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 13.0f;
            } else {
                aShadowOffsetX += 25.0f;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_DIGGER) {
            aShadowOffsetY += 5.0f;
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 14.0f;
            } else {
                aShadowOffsetX += 17.0f;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
            aShadowOffsetY += 5.0f;
            if (z.isWalkingBackwards()) {
                aShadowOffsetX -= 2.0f;
            } else {
                aShadowOffsetX += 35.0f;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER) {
            aShadowOffsetY += 11.0f;
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 15.0f;
            } else {
                aShadowOffsetX += 19.0f;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_YETI) {
            aShadowOffsetY += 20.0f;
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 20.0f;
            } else {
                aShadowOffsetX += 3.0f;
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
            aScale *= 1.5f;
            aShadowOffsetX += 27.0f;
            aShadowOffsetY += 7.0f;
        } else if (z.mApp.reanimationTryToGet(z.mBodyReanimID) != null) {
            if (z.isWalkingBackwards()) {
                aShadowOffsetX += 11.0f;
            } else {
                aShadowOffsetX += 23.0f;
            }
        } else {
            if (z.isWalkingBackwards()) {
                aShadowOffsetX -= 2.0f;
            } else {
                aShadowOffsetX += 35.0f;
            }
        }

        if (z.mZombieType == ZombieType.ZOMBIE_NEWSPAPER) {
            aShadowOffsetY += 4.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            aShadowOffsetY += 13.0f;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE) {
            aShadowOffsetX -= 12.0f;
            aScale = PvzpCommon.pvzpAnimateCurveFloat(Zombie.BUNGEE_ZOMBIE_HEIGHT - 1000, 100, (int) z.mAltitude, 0.1f, 1.5f,
                    PvzpCurves.CURVE_LINEAR);
        }

        if (z.mZombieHeight == ZombieHeight.HEIGHT_UP_LADDER || z.mZombieHeight == ZombieHeight.HEIGHT_FALLING
                || z.mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN || z.mZombieType == ZombieType.ZOMBIE_BUNGEE
                || z.isBouncingPogo() || z.isFlying()) {
            aShadowOffsetY += z.mAltitude;
            if (z.mOnHighGround) {
                aShadowOffsetY -= GameConstants.HIGH_GROUND_HEIGHT;
            }
        }

        if (z.mInPool) {
            drawImageCenterScaledF(g, Resources.getImageById(ImageID.IMAGE_WHITEWATER_SHADOW), aShadowOffsetX, aShadowOffsetY + 67.0f,
                    aScale, aScale);
        } else {
            drawImageCenterScaledF(g, Resources.getImageById(aShadowType == 0 ? ImageID.IMAGE_PLANTSHADOW : ImageID.IMAGE_PLANTSHADOW2),
                    aShadowOffsetX, aShadowOffsetY + 92.0f, aScale, aScale);
        }

        g.clearClipRect();
    }

    /** Ported from src/Lawn/Zombie.cpp:9712 (Zombie::DrawBossFireBall). */
    public static void drawBossFireBall(Zombie z, Graphics g) {
        z.makeParentGraphicsFrame(g);

        Reanimation aFireBallReanim = z.mApp.reanimationTryToGet(z.mBossFireBallReanimID);
        if (aFireBallReanim != null) {
            aFireBallReanim.drawRenderGroup(g, Reanimation.RENDER_GROUP_NORMAL);

            g.setDrawMode(Graphics.DRAWMODE_ADDITIVE);
            aFireBallReanim.drawRenderGroup(g, Zombie.RENDER_GROUP_BOSS_FIREBALL_ADDITIVE);

            g.setDrawMode(Graphics.DRAWMODE_NORMAL);
            aFireBallReanim.drawRenderGroup(g, Zombie.RENDER_GROUP_BOSS_FIREBALL_TOP);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:9729 (Zombie::DrawBossBackArm). */
    public static void drawBossBackArm(Zombie z, Graphics g, ZombieDrawPosition theDrawPos) {
        float aImageOffsetX = 0.0f;
        float aImageOffsetY = 0.0f;
        if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_DROP_RV) {
            aImageOffsetY = (z.mTargetRow - 1) * 85.0f - z.mTargetCol * 20.0f;
            aImageOffsetX = z.mTargetCol * 80.0f;
        } else if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_ENTER || z.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_DROP
                || z.mZombiePhase == ZombiePhase.PHASE_BOSS_BUNGEES_LEAVE) {
            aImageOffsetX = z.mTargetCol * 80.0f - 23.0f;
        }

        Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
        aBodyReanim.mOverlayMatrix.m02 += aImageOffsetX;
        aBodyReanim.mOverlayMatrix.m12 += aImageOffsetY;
        drawReanim(z, g, theDrawPos, Zombie.RENDER_GROUP_BOSS_BACK_ARM);
        aBodyReanim.mOverlayMatrix.m02 -= aImageOffsetX;
        aBodyReanim.mOverlayMatrix.m12 -= aImageOffsetY;
    }

    /** Ported from src/Lawn/Zombie.cpp:10526 (Zombie::DrawBossPart). */
    public static void drawBossPart(Zombie z, Graphics g, int theBossPart) {
        ZombieDrawPosition aDrawPos = getDrawPos(z);

        switch (theBossPart) {
        case BossPart.BOSS_PART_BACK_LEG:
            drawReanim(z, g, aDrawPos, Zombie.RENDER_GROUP_BOSS_BACK_LEG);
            break;
        case BossPart.BOSS_PART_FRONT_LEG:
            drawReanim(z, g, aDrawPos, Zombie.RENDER_GROUP_BOSS_FRONT_LEG);
            break;
        case BossPart.BOSS_PART_MAIN:
            drawReanim(z, g, aDrawPos, Reanimation.RENDER_GROUP_NORMAL);
            break;
        case BossPart.BOSS_PART_BACK_ARM:
            drawBossBackArm(z, g, aDrawPos);
            break;
        case BossPart.BOSS_PART_FIREBALL:
            drawBossFireBall(z, g);
            break;
        default:
            break;
        }
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:768 (PvzpDrawImageScaledF).
     *
     * <p>Upstream builds a scale matrix (translation included, i.e. {@code mTransX}/{@code mTransY}
     * folded into {@code m02}/{@code m12}) and hands it to {@code PvzpBltMatrix}, which blts with a
     * zero pixel offset. {@link Graphics#drawImageMatrix(Image, SexyMatrix3, Rect)} itself adds
     * {@code mTransX}/{@code mTransY} at blt time, so the translation is left out of the matrix here
     * to keep the total identical.
     */
    private static void drawImageScaledF(Graphics g, Image theImage, float thePosX, float thePosY, float theScaleX,
            float theScaleY) {
        if (theScaleX == 1.0f && theScaleY == 1.0f) {
            g.drawImageF(theImage, thePosX, thePosY);
            return;
        }

        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);

        SexyMatrix3 aTransform = new SexyMatrix3();
        aTransform.m00 = theScaleX;
        aTransform.m10 = 0.0f;
        aTransform.m20 = 0.0f;
        aTransform.m01 = 0.0f;
        aTransform.m11 = theScaleY;
        aTransform.m21 = 0.0f;
        aTransform.m02 = theImage.mWidth * 0.5f * theScaleX + thePosX;
        aTransform.m12 = theImage.mHeight * 0.5f * theScaleY + thePosY;
        aTransform.m22 = 1.0f;

        g.drawImageMatrix(theImage, aTransform, aSrcRect);
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:795 (PvzpDrawImageCenterScaledF).
     *
     * <p>Same blt as {@link #drawImageScaledF} except the anchor is the unscaled cel centre.
     */
    private static void drawImageCenterScaledF(Graphics g, Image theImage, float thePosX, float thePosY, float theScaleX,
            float theScaleY) {
        if (theScaleX == 1.0f && theScaleY == 1.0f) {
            g.drawImageF(theImage, thePosX, thePosY);
            return;
        }

        Rect aSrcRect = new Rect(0, 0, theImage.mWidth, theImage.mHeight);

        SexyMatrix3 aTransform = new SexyMatrix3();
        aTransform.m00 = theScaleX;
        aTransform.m10 = 0.0f;
        aTransform.m20 = 0.0f;
        aTransform.m01 = 0.0f;
        aTransform.m11 = theScaleY;
        aTransform.m21 = 0.0f;
        aTransform.m02 = theImage.mWidth * 0.5f + thePosX;
        aTransform.m12 = theImage.mHeight * 0.5f + thePosY;
        aTransform.m22 = 1.0f;

        g.drawImageMatrix(theImage, aTransform, aSrcRect);
    }
}
