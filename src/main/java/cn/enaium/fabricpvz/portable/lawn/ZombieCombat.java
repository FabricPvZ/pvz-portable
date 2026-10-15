/*
 * Ported from src/Lawn/Zombie.cpp:3502-3931, 5797-5858, 6343-6497, 6555-6583, 7539-7998, 8081-8175, 8424-8458.
 *
 * Static combat/damage helpers for {@link Zombie}: body-part loss, damage indices, plant/zombie
 * targeting, squish handling and the flying→shield→helm→body damage pipeline. Names and constants
 * follow upstream; randomness flows through cn.enaium.fabricpvz.portable.pvzp.PvzpCommon / cn.enaium.fabricpvz.portable.sexy.Common.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.fx.PvzpParticleSystem;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

public final class ZombieCombat {

    private ZombieCombat() {
    }

    /** Ported from src/Lawn/Zombie.cpp:3502-3508 (Zombie::SetupReanimForLostHead). */
    public static void setupReanimForLostHead(Zombie z) {
        z.reanimShowPrefix("anim_head", Reanimation.RENDER_GROUP_HIDDEN);
        z.reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_HIDDEN);
        z.reanimShowPrefix("anim_tongue", Reanimation.RENDER_GROUP_HIDDEN);
    }

    /** Ported from src/Lawn/Zombie.cpp:3509-3694 (Zombie::DropHead). */
    public static void dropHead(Zombie z, int theDamageFlags) {
        if (!z.canLoseBodyParts() || !z.mHasHead) {
            return;
        }

        if (z.mButteredCounter > 0) {
            z.mButteredCounter = 0;
            z.updateAnimSpeed();
        }

        z.mHasHead = false;
        setupReanimForLostHead(z);
        if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
            return;
        }

        if (Zombie.isZombotany(z.mZombieType)) {
            z.mApp.reanimationGet(z.mSpecialHeadReanimID).reanimationDie();
            z.mSpecialHeadReanimID = ReanimationID.REANIMATIONID_NULL;
            return;
        }

        int aRenderOrder = z.mRenderOrder + 1;
        ZombieDrawPosition aDrawPos = z.getDrawPos();
        float[] aPos = {
            z.mPosX + aDrawPos.mImageOffsetX + aDrawPos.mHeadX + 11.0f,
            z.mPosY + aDrawPos.mImageOffsetY + aDrawPos.mHeadY + aDrawPos.mBodyY + 21.0f
        };
        if (z.mBodyReanimID != ReanimationID.REANIMATIONID_NULL) {
            z.getTrackPosition("anim_head1", aPos);
        }

        int aEffect = ParticleEffect.PARTICLE_ZOMBIE_HEAD;
        if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED) {
            aEffect = ParticleEffect.PARTICLE_MOWERED_ZOMBIE_HEAD;
        } else if (z.mInPool) {
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_HEAD_POOL;
        }
        if (z.mZombieType == ZombieType.ZOMBIE_DANCER) {
            aRenderOrder = z.mRenderOrder - 1;
        }
        if (z.mZombieType == ZombieType.ZOMBIE_NEWSPAPER) {
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_NEWSPAPER_HEAD;
        } else if (z.mZombieType == ZombieType.ZOMBIE_POGO) {
            z.pogoBreak(theDamageFlags);
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_POGO_HEAD;
        } else if (z.mZombieType == ZombieType.ZOMBIE_BALLOON) {
            z.reanimShowPrefix("anim_hat", Reanimation.RENDER_GROUP_HIDDEN);
            z.reanimShowPrefix("hat", Reanimation.RENDER_GROUP_HIDDEN);
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_BALLOON_HEAD;
        } else if (z.mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
            z.dropPole();
        } else if (z.mZombieType == ZombieType.ZOMBIE_FLAG) {
            z.dropFlag();
        }

        PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], aRenderOrder, aEffect);
        z.overrideParticleColor(aParticle);
        z.overrideParticleScale(aParticle);
        if (aParticle != null) {
            if (z.mZombieType == ZombieType.ZOMBIE_DANCER) {
                z.reanimShowPrefix("Zombie_disco_chops", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowPrefix("Zombie_disco_glasses", Reanimation.RENDER_GROUP_HIDDEN);
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEDANCERHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_BACKUP_DANCER) {
                z.reanimShowPrefix("Zombie_disco_chops", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowPrefix("Zombie_backup_stash", Reanimation.RENDER_GROUP_HIDDEN);
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEBACKUPDANCERHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEBOBSLEDHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_LADDER) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIELADDERHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_IMP) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEIMPHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_FOOTBALL) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEFOOTBALLHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_POLEVAULTER) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEPOLEVAULTERHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_SNORKEL) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_SNORKLE_HEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_DIGGER) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEDIGGERHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_DOLPHIN_RIDER) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEDOLPHINRIDERHEAD));
            } else if (z.mZombieType == ZombieType.ZOMBIE_YETI) {
                aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEYETIHEAD));
            }
        }

        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (z.mBoard.mMustacheMode && aBodyReanim.trackExists("Zombie_mustache")) {
            z.reanimShowPrefix("Zombie_mustache", Reanimation.RENDER_GROUP_HIDDEN);

            PvzpParticleSystem aMustacheParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], aRenderOrder,
                ParticleEffect.PARTICLE_ZOMBIE_MUSTACHE);
            z.overrideParticleColor(aMustacheParticle);
            z.overrideParticleScale(aMustacheParticle);

            Image aMustacheImage = aBodyReanim.getImageOverride("Zombie_mustache");
            if (aMustacheParticle != null && aMustacheImage != null) {
                aMustacheParticle.overrideImage(null, aMustacheImage);
            }
        }
        if (z.mBoard.mFutureMode) {
            Image aHeadImage = aBodyReanim.getImageOverride("anim_head1");
            int aFrame = -1;
            if (aHeadImage != null) {
                if (aHeadImage == Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES1)) {
                    aFrame = 0;
                } else if (aHeadImage == Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES2)) {
                    aFrame = 1;
                } else if (aHeadImage == Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES3)) {
                    aFrame = 2;
                } else if (aHeadImage == Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES4)) {
                    aFrame = 3;
                }
            }

            if (aFrame != -1) {
                PvzpParticleSystem aSunglassParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], aRenderOrder,
                    ParticleEffect.PARTICLE_ZOMBIE_SUNGLASS);
                z.overrideParticleColor(aSunglassParticle);
                z.overrideParticleScale(aSunglassParticle);
                if (aSunglassParticle != null) {
                    aSunglassParticle.overrideFrame(null, aFrame);
                }
            }
        }
        if (z.mBoard.mPinataMode && z.mZombiePhase != ZombiePhase.PHASE_ZOMBIE_MOWERED) {
            z.mApp.addPvzpParticle(aPos[0], aPos[1], aRenderOrder, ParticleEffect.PARTICLE_ZOMBIE_PINATA);
            z.overrideParticleScale(aParticle); // Weird, upstream TODO: test the Pinata Mode
        }

        z.mApp.playFoley(FoleyType.FOLEY_LIMBS_POP);
    }

    /** Ported from src/Lawn/Zombie.cpp:3695-3877 (Zombie::SetupReanimForLostArm). */
    public static void setupReanimForLostArm(Zombie z, int theDamageFlags) {
        switch (z.mZombieType) {
            case ZombieType.ZOMBIE_FOOTBALL:
                z.reanimShowPrefix("Zombie_football_leftarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowPrefix("Zombie_football_leftarm_hand", Reanimation.RENDER_GROUP_HIDDEN);
                break;
            case ZombieType.ZOMBIE_NEWSPAPER:
                z.reanimShowTrack("Zombie_paper_hands", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowTrack("Zombie_paper_leftarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                break;
            case ZombieType.ZOMBIE_POLEVAULTER:
                z.reanimShowTrack("Zombie_polevaulter_outerarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowTrack("Zombie_outerarm_hand", Reanimation.RENDER_GROUP_HIDDEN);
                break;
            case ZombieType.ZOMBIE_DANCER:
                z.reanimShowTrack("Zombie_disco_outerarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowTrack("Zombie_disco_outerhand_point", Reanimation.RENDER_GROUP_HIDDEN);
                break;
            case ZombieType.ZOMBIE_BACKUP_DANCER:
                z.reanimShowTrack("Zombie_disco_outerarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowTrack("Zombie_disco_outerhand", Reanimation.RENDER_GROUP_HIDDEN);
                break;
            default:
                z.reanimShowPrefix("Zombie_outerarm_lower", Reanimation.RENDER_GROUP_HIDDEN);
                z.reanimShowPrefix("Zombie_outerarm_hand", Reanimation.RENDER_GROUP_HIDDEN);
                break;
        }

        ZombieDrawPosition aDrawPos = z.getDrawPos();
        float[] aPos = {
            z.mPosX + aDrawPos.mImageOffsetX + 45.0f,
            z.mPosY + aDrawPos.mImageOffsetY + aDrawPos.mBodyY + 78.0f
        };
        if (z.isWalkingBackwards()) {
            aPos[0] += 36.0f;
        }

        Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
        if (aBodyReanim != null) {
            switch (z.mZombieType) {
                case ZombieType.ZOMBIE_FOOTBALL:
                    z.getTrackPosition("Zombie_football_leftarm_hand", aPos);
                    aBodyReanim.setImageOverride("Zombie_football_leftarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_NEWSPAPER:
                    z.getTrackPosition("Zombie_paper_leftarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_paper_leftarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_POLEVAULTER:
                    z.getTrackPosition("Zombie_polevaulter_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_polevaulter_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_POLEVAULTER_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_BALLOON:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BALLOON_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_IMP:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_imp_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_IMP_ARM1_BONE));
                    break;
                case ZombieType.ZOMBIE_DIGGER:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_digger_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_DIGGER_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_BOBSLED:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_dolphinrider_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_JACK_IN_THE_BOX:
                    z.getTrackPosition("Zombie_jackbox_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_jackbox_outerarm_lower",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_JACKBOX_OUTERARM_LOWER2));
                    break;
                case ZombieType.ZOMBIE_SNORKEL:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_snorkle_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_SNORKLE_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_DOLPHIN_RIDER:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_dolphinrider_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_DOLPHINRIDER_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_POGO:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_POGO_OUTERARM_UPPER2));
                    aBodyReanim.setImageOverride("Zombie_pogo_stickhands",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_POGO_STICKHANDS2));
                    aBodyReanim.setImageOverride("Zombie_pogo_stick",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE2));
                    aBodyReanim.setImageOverride("Zombie_pogo_stick2",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE2));
                    break;
                case ZombieType.ZOMBIE_FLAG: {
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_OUTERARM_UPPER2));

                    Reanimation aHeadReanim = z.mApp.reanimationTryToGet(z.mSpecialHeadReanimID);
                    if (aHeadReanim != null) {
                        aHeadReanim.setImageOverride("Zombie_flag",
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_FLAG3));
                    }
                    break;
                }
                case ZombieType.ZOMBIE_DANCER:
                    z.getTrackPosition("Zombie_disco_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_disco_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_BACKUP_DANCER:
                    z.getTrackPosition("Zombie_disco_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_disco_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BACKUP_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_LADDER:
                    z.getTrackPosition("Zombie_outerarm_hand", aPos);
                    aBodyReanim.setImageOverride("Zombie_ladder_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_LADDER_OUTERARM_UPPER2));
                    break;
                case ZombieType.ZOMBIE_YETI:
                    z.getTrackPosition("Zombie_outerarm_hand", aPos);
                    aBodyReanim.setImageOverride("Zombie_yeti_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_UPPER2));
                    break;
                default:
                    z.getTrackPosition("Zombie_outerarm_lower", aPos);
                    aBodyReanim.setImageOverride("Zombie_outerarm_upper",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_OUTERARM_UPPER2));
                    break;
            }
        }

        if (!z.mInPool && !PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
            int aEffect = ParticleEffect.PARTICLE_ZOMBIE_ARM;
            if (z.mZombiePhase == ZombiePhase.PHASE_ZOMBIE_MOWERED) {
                aEffect = ParticleEffect.PARTICLE_MOWERED_ZOMBIE_ARM;
            }

            PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], z.mRenderOrder + 1, aEffect);
            z.overrideParticleColor(aParticle);
            z.overrideParticleScale(aParticle);

            if (aParticle != null) {
                switch (z.mZombieType) {
                    case ZombieType.ZOMBIE_FOOTBALL:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_HAND));
                        break;
                    case ZombieType.ZOMBIE_NEWSPAPER:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_LOWER));
                        break;
                    case ZombieType.ZOMBIE_DANCER:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_HAND));
                        break;
                    case ZombieType.ZOMBIE_BACKUP_DANCER:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BACKUP_INNERARM_HAND));
                        break;
                    case ZombieType.ZOMBIE_BOBSLED:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_HAND));
                        break;
                    case ZombieType.ZOMBIE_IMP:
                        aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_IMP_ARM2));
                        break;
                    case ZombieType.ZOMBIE_YETI:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_HAND));
                        break;
                    case ZombieType.ZOMBIE_JACK_IN_THE_BOX:
                        aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEJACKBOXARM));
                        break;
                    case ZombieType.ZOMBIE_DIGGER:
                        aParticle.overrideImage(null, Resources.getImageById(ImageID.IMAGE_ZOMBIEDIGGERARM));
                        break;
                    case ZombieType.ZOMBIE_POLEVAULTER:
                    case ZombieType.ZOMBIE_BALLOON:
                    case ZombieType.ZOMBIE_DOLPHIN_RIDER:
                    case ZombieType.ZOMBIE_POGO:
                    case ZombieType.ZOMBIE_LADDER:
                        aParticle.overrideImage(null,
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_OUTERARM_HAND));
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:3878-3903 (Zombie::DropArm). */
    public static void dropArm(Zombie z, int theDamageFlags) {
        if (!z.canLoseBodyParts()) {
            return;
        }
        if (z.mShieldType == ShieldType.SHIELDTYPE_DOOR || z.mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER) {
            return;
        }
        if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_WALKING
            || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_RIDING
            || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP
            || z.mZombiePhase == ZombiePhase.PHASE_NEWSPAPER_READING) {
            return;
        }
        if (!z.mHasArm) {
            return;
        }

        z.mHasArm = false;
        setupReanimForLostArm(z, theDamageFlags);
        z.mApp.playFoley(FoleyType.FOLEY_LIMBS_POP);
    }

    /** Ported from src/Lawn/Zombie.cpp:3904-3931 (Zombie::UpdateDamageStates). */
    public static void updateDamageStates(Zombie z, int theDamageFlags) {
        if (!z.canLoseBodyParts()) {
            return;
        }

        if (z.mHasArm && z.mBodyHealth < 2 * z.mBodyMaxHealth / 3 && z.mBodyHealth > 0) {
            dropArm(z, theDamageFlags);
        }

        if (z.mHasHead && z.mBodyHealth < z.mBodyMaxHealth / 3) {
            dropHead(z, theDamageFlags);
            z.dropLoot();
            z.stopZombieSound();

            if (z.mBoard.hasLevelAwardDropped()) {
                z.playDeathAnim(theDamageFlags);
            }

            if (z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_WALKING_IN_POOL) {
                z.dieNoLoot();
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:5797-5811 (Zombie::GetHelmDamageIndex). */
    public static int getHelmDamageIndex(Zombie z) {
        if (z.mHelmHealth < z.mHelmMaxHealth / 3) {
            return 2;
        }

        if (z.mHelmHealth < z.mHelmMaxHealth * 2 / 3) {
            return 1;
        }

        return 0;
    }

    /** Ported from src/Lawn/Zombie.cpp:5812-5843 (Zombie::GetBodyDamageIndex). */
    public static int getBodyDamageIndex(Zombie z) {
        if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            if (z.mBodyHealth < z.mBodyMaxHealth / 2) {
                return 2;
            }

            if (z.mBodyHealth < z.mBodyMaxHealth * 4 / 5) {
                return 1;
            }

            return 0;
        } else {
            if (z.mBodyHealth < z.mBodyMaxHealth / 3) {
                return 2;
            }

            if (z.mBodyHealth < z.mBodyMaxHealth * 2 / 3) {
                return 1;
            }

            return 0;
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:5844-5858 (Zombie::GetShieldDamageIndex). */
    public static int getShieldDamageIndex(Zombie z) {
        if (z.mShieldHealth < z.mShieldMaxHealth / 3) {
            return 2;
        }

        if (z.mShieldHealth < z.mShieldMaxHealth * 2 / 3) {
            return 1;
        }

        return 0;
    }

    /** Ported from src/Lawn/Zombie.cpp:6343-6421 (Zombie::CanTargetPlant). */
    public static boolean canTargetPlant(Zombie z, Plant thePlant, int theAttackType) {
        if (z.mApp.isWallnutBowlingLevel() && theAttackType != ZombieAttackType.ATTACKTYPE_VAULT) {
            return false;
        }

        if (thePlant.notOnGround() || thePlant.mSeedType == SeedType.SEED_TANGLEKELP) {
            return false;
        }

        if (!z.mInPool && z.mBoard.isPoolSquare(thePlant.mPlantCol, thePlant.mRow)) {
            return false;
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            return thePlant.mSeedType == SeedType.SEED_POTATOMINE && thePlant.mState == PlantState.STATE_NOTREADY;
        }

        if (thePlant.isSpiky()) {
            return z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
                || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR
                || z.mZombieType == ZombieType.ZOMBIE_ZAMBONI
                || z.mBoard.isPoolSquare(thePlant.mPlantCol, thePlant.mRow)
                || z.mBoard.getFlowerPotAt(thePlant.mPlantCol, thePlant.mRow) != null;
        }

        if (theAttackType == ZombieAttackType.ATTACKTYPE_DRIVE_OVER) {
            if (thePlant.mSeedType == SeedType.SEED_CHERRYBOMB || thePlant.mSeedType == SeedType.SEED_JALAPENO
                || thePlant.mSeedType == SeedType.SEED_BLOVER || thePlant.mSeedType == SeedType.SEED_SQUASH) {
                return false;
            }
            if (thePlant.mSeedType == SeedType.SEED_DOOMSHROOM || thePlant.mSeedType == SeedType.SEED_ICESHROOM) {
                return thePlant.mIsAsleep;
            }
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_LADDER_CARRYING
            || z.mZombiePhase == ZombiePhase.PHASE_LADDER_PLACING) {
            boolean aPlaceLadder = false;
            if (thePlant.mSeedType == SeedType.SEED_WALLNUT || thePlant.mSeedType == SeedType.SEED_TALLNUT
                || thePlant.mSeedType == SeedType.SEED_PUMPKINSHELL) {
                aPlaceLadder = true;
            }

            if (z.mBoard.getLadderAt(thePlant.mPlantCol, thePlant.mRow) != null) {
                aPlaceLadder = false;
            }

            if ((theAttackType == ZombieAttackType.ATTACKTYPE_CHEW && aPlaceLadder)
                || (theAttackType == ZombieAttackType.ATTACKTYPE_LADDER && !aPlaceLadder)) {
                return false;
            }
        }

        if (theAttackType == ZombieAttackType.ATTACKTYPE_CHEW) {
            Plant aTopPlant = z.mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow,
                PlantPriority.TOPPLANT_EATING_ORDER);
            if (aTopPlant != thePlant && aTopPlant != null && canTargetPlant(z, aTopPlant, theAttackType)) {
                return false;
            }
        }

        if (theAttackType == ZombieAttackType.ATTACKTYPE_VAULT) {
            Plant aTopPlant = z.mBoard.getTopPlantAt(thePlant.mPlantCol, thePlant.mRow,
                PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
            if (aTopPlant != thePlant && aTopPlant != null && canTargetPlant(z, aTopPlant, theAttackType)) {
                return false;
            }
        }

        return true;
    }

    /** Ported from src/Lawn/Zombie.cpp:6422-6442 (Zombie::FindPlantTarget). */
    public static Plant findPlantTarget(Zombie z, int theAttackType) {
        Rect aAttackRect = z.getZombieAttackRect();

        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow == z.mRow) {
                Rect aPlantRect = aPlant.getPlantRect();
                if (Board.getRectOverlap(aAttackRect, aPlantRect) >= 20
                    && canTargetPlant(z, aPlant, theAttackType)) {
                    return aPlant;
                }
            }
        }

        return null;
    }

    /** Ported from src/Lawn/Zombie.cpp:6443-6475 (Zombie::FindZombieTarget). */
    public static Zombie findZombieTarget(Zombie z) {
        if (z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING) {
            return null;
        }

        Rect aAttackRect = z.getZombieAttackRect();

        for (Zombie aZombie : z.mBoard.mZombies) {
            if (aZombie.mDead) {
                continue;
            }
            if (z.mMindControlled != aZombie.mMindControlled
                && !aZombie.isFlying()
                && aZombie.mZombiePhase != ZombiePhase.PHASE_DIGGER_TUNNELING
                && aZombie.mZombiePhase != ZombiePhase.PHASE_BUNGEE_DIVING
                && aZombie.mZombiePhase != ZombiePhase.PHASE_BUNGEE_DIVING_SCREAMING
                && aZombie.mZombiePhase != ZombiePhase.PHASE_BUNGEE_RISING
                && aZombie.mZombieHeight != ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED
                && !aZombie.isDeadOrDying()
                && aZombie.mRow == z.mRow) {
                Rect aZombieRect = aZombie.getZombieRect();
                int aOverlap = Board.getRectOverlap(aAttackRect, aZombieRect);
                if (aOverlap >= 20 || (aOverlap >= 0 && aZombie.mIsEating)) {
                    return aZombie;
                }
            }
        }

        return null;
    }

    /** Ported from src/Lawn/Zombie.cpp:6476-6497 (Zombie::SquishAllInSquare). */
    public static void squishAllInSquare(Zombie z, int theX, int theY, int theAttackType) {
        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow == theY && aPlant.mPlantCol == theX) {
                if (theAttackType == ZombieAttackType.ATTACKTYPE_DRIVE_OVER && aPlant.isSpiky()) {
                    continue;
                }

                if (aPlant.mSeedType != SeedType.SEED_SPIKEROCK) {
                    z.mBoard.mPlantsEaten++;
                    aPlant.squish();
                }
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:6555-6583 (Zombie::CheckSquish). */
    public static void checkSquish(Zombie z, int theAttackType) {
        Rect aAttackRect = z.getZombieAttackRect();

        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mRow == z.mRow) {
                Rect aPlantRect = aPlant.getPlantRect();
                if (Board.getRectOverlap(aAttackRect, aPlantRect) >= 20
                    && canTargetPlant(z, aPlant, theAttackType) && !aPlant.isSpiky()) {
                    squishAllInSquare(z, aPlant.mPlantCol, aPlant.mRow, theAttackType);
                    break;
                }
            }
        }

        if (z.mApp.isIZombieLevel()) {
            GridItem aBrain = z.mBoard.mChallenge.iZombieGetBrainTarget(z);
            if (aBrain != null) {
                z.mBoard.mChallenge.iZombieSquishBrain(aBrain);
            }
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7539-7599 (Zombie::DropShield). */
    public static void dropShield(Zombie z, int theDamageFlags) {
        if (z.mShieldType == ShieldType.SHIELDTYPE_NONE) {
            return;
        }

        if (z.mShieldType == ShieldType.SHIELDTYPE_DOOR) {
            z.detachShield();
            if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
                float[] aPos = new float[2];
                z.getTrackPosition("anim_screendoor", aPos);
                PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZOMBIE_DOOR);
                z.overrideParticleScale(aParticle);
            }
        } else if (z.mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER) {
            z.stopEating();
            if (z.mYuckyFace) {
                z.showYuckyFace(false);
                z.mYuckyFace = false;
                z.mYuckyFaceCounter = 0;
            }

            z.mZombiePhase = ZombiePhase.PHASE_NEWSPAPER_MADDENING;
            z.playZombieReanim("anim_gasp", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 10, 8.0f);
            z.detachShield();

            if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
                float[] aPos = new float[2];
                z.getTrackPosition("Zombie_paper_paper", aPos);
                PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZOMBIE_NEWSPAPER);
                z.overrideParticleScale(aParticle);
            }

            if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)
                && !PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_BYPASSES_SHIELD)) {
                z.mApp.playFoley(FoleyType.FOLEY_NEWSPAPER_RIP);
                z.addAttachedReanim(-11, 0, ReanimationType.REANIM_ZOMBIE_SURPRISE);
            }
        } else if (z.mShieldType == ShieldType.SHIELDTYPE_LADDER) {
            z.detachShield();
            if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
                float aPosX = z.mPosX + 31.0f;
                float aPosY = z.mPosY + 80.0f;
                PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPosX, aPosY, z.mRenderOrder + 1,
                    ParticleEffect.PARTICLE_ZOMBIE_LADDER);
                z.overrideParticleScale(aParticle);
            }
        }

        z.mShieldType = ShieldType.SHIELDTYPE_NONE;
    }

    /** Ported from src/Lawn/Zombie.cpp:7600-7665 (Zombie::TakeShieldDamage). */
    public static int takeShieldDamage(Zombie z, int theDamage, int theDamageFlags) {
        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)) {
            z.mShieldJustGotShotCounter = 25;
            z.mJustGotShotCounter = Math.max(z.mJustGotShotCounter, 0);
        }

        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)
            && !PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY)) {
            z.mShieldRecoilCounter = 12;
            if (z.mShieldType == ShieldType.SHIELDTYPE_DOOR || z.mShieldType == ShieldType.SHIELDTYPE_LADDER) {
                z.mApp.playFoley(FoleyType.FOLEY_SHIELD_HIT);
            }
        }

        int aDamageIndexBeforeDamage = getShieldDamageIndex(z);
        int aDamageActual = Math.min(z.mShieldHealth, theDamage);
        int aDamageRemaining = theDamage - aDamageActual;
        z.mShieldHealth -= aDamageActual;
        if (z.mShieldHealth == 0) {
            dropShield(z, theDamageFlags);
            return aDamageRemaining;
        }

        int aDamageIndexAfterDamage = getShieldDamageIndex(z);
        if (aDamageIndexAfterDamage != aDamageIndexBeforeDamage) {
            Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
            if (z.mShieldType == ShieldType.SHIELDTYPE_DOOR && aDamageIndexAfterDamage == 1) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("anim_screendoor",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_SCREENDOOR2));
            } else if (z.mShieldType == ShieldType.SHIELDTYPE_DOOR && aDamageIndexAfterDamage == 2) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("anim_screendoor",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_SCREENDOOR3));
            } else if (z.mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER && aDamageIndexAfterDamage == 1) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("Zombie_paper_paper",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_PAPER_PAPER2));
            } else if (z.mShieldType == ShieldType.SHIELDTYPE_NEWSPAPER && aDamageIndexAfterDamage == 2) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("Zombie_paper_paper",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_PAPER_PAPER3));
            } else if (z.mShieldType == ShieldType.SHIELDTYPE_LADDER && aDamageIndexAfterDamage == 1) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("Zombie_ladder_1",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE1));
            } else if (z.mShieldType == ShieldType.SHIELDTYPE_LADDER && aDamageIndexAfterDamage == 2) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("Zombie_ladder_1",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE2));
            }
        }

        return aDamageRemaining;
    }

    /** Ported from src/Lawn/Zombie.cpp:7666-7716 (Zombie::DropHelm). */
    public static void dropHelm(Zombie z, int theDamageFlags) {
        if (z.mHelmType == HelmType.HELMTYPE_NONE) {
            return;
        }

        ZombieDrawPosition aDrawPos = z.getDrawPos();
        float[] aPos = {
            z.mPosX + aDrawPos.mImageOffsetX + aDrawPos.mHeadX + 14.0f,
            z.mPosY + aDrawPos.mImageOffsetY + aDrawPos.mHeadY + aDrawPos.mBodyY + 18.0f
        };
        int aEffect = ParticleEffect.PARTICLE_NONE;
        if (z.mHelmType == HelmType.HELMTYPE_TRAFFIC_CONE) {
            z.getTrackPosition("anim_cone", aPos);
            z.reanimShowPrefix("anim_cone", Reanimation.RENDER_GROUP_HIDDEN);
            z.reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_NORMAL);
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_TRAFFIC_CONE;
        } else if (z.mHelmType == HelmType.HELMTYPE_PAIL) {
            z.getTrackPosition("anim_bucket", aPos);
            z.reanimShowPrefix("anim_bucket", Reanimation.RENDER_GROUP_HIDDEN);
            z.reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_NORMAL);
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_PAIL;
        } else if (z.mHelmType == HelmType.HELMTYPE_FOOTBALL) {
            z.getTrackPosition("zombie_football_helmet", aPos);
            z.reanimShowPrefix("zombie_football_helmet", Reanimation.RENDER_GROUP_HIDDEN);
            z.reanimShowPrefix("anim_hair", Reanimation.RENDER_GROUP_NORMAL);
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_HELMET;
        } else if (z.mHelmType == HelmType.HELMTYPE_DIGGER) {
            z.getTrackPosition("Zombie_digger_hardhat", aPos);
            z.reanimShowTrack("Zombie_digger_hardhat", Reanimation.RENDER_GROUP_HIDDEN);
            aEffect = ParticleEffect.PARTICLE_ZOMBIE_HEADLIGHT;
        } else if (z.mHelmType == HelmType.HELMTYPE_BOBSLED
            && !PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)) {
            z.bobsledCrash();
        }

        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_LEAVE_BODY)
            && aEffect != ParticleEffect.PARTICLE_NONE) {
            PvzpParticleSystem aParticle = z.mApp.addPvzpParticle(aPos[0], aPos[1], z.mRenderOrder + 1, aEffect);
            z.overrideParticleScale(aParticle);
        }

        z.mHelmType = HelmType.HELMTYPE_NONE;
    }

    /** Ported from src/Lawn/Zombie.cpp:7717-7802 (Zombie::TakeHelmDamage). */
    public static int takeHelmDamage(Zombie z, int theDamage, int theDamageFlags) {
        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)) {
            z.mJustGotShotCounter = 25;
        }

        int aDamageIndexBeforeDamage = getHelmDamageIndex(z);
        int aDamageActual = Math.min(z.mHelmHealth, theDamage);
        int aDamageRemaining = theDamage - aDamageActual;
        z.mHelmHealth -= aDamageActual;
        if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_FREEZE)) {
            z.applyChill(false);
        }
        if (z.mHelmHealth == 0) {
            dropHelm(z, theDamageFlags);
            return aDamageRemaining;
        }

        int aDamageIndexAfterDamage = getHelmDamageIndex(z);
        if (aDamageIndexBeforeDamage != aDamageIndexAfterDamage) {
            Reanimation aBodyReanim = z.mApp.reanimationTryToGet(z.mBodyReanimID);
            if (z.mHelmType == HelmType.HELMTYPE_TRAFFIC_CONE && aDamageIndexAfterDamage == 1 && aBodyReanim != null) {
                aBodyReanim.setImageOverride("anim_cone",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CONE2));
            } else if (z.mHelmType == HelmType.HELMTYPE_TRAFFIC_CONE && aDamageIndexAfterDamage == 2
                && aBodyReanim != null) {
                aBodyReanim.setImageOverride("anim_cone",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CONE3));
            } else if (z.mHelmType == HelmType.HELMTYPE_PAIL && aDamageIndexAfterDamage == 1) {
                aBodyReanim.setImageOverride("anim_bucket",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BUCKET2));
            } else if (z.mHelmType == HelmType.HELMTYPE_PAIL && aDamageIndexAfterDamage == 2) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("anim_bucket",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BUCKET3));
            } else if (z.mHelmType == HelmType.HELMTYPE_DIGGER && aDamageIndexAfterDamage == 1) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("Zombie_digger_hardhat",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT2));
            } else if (z.mHelmType == HelmType.HELMTYPE_DIGGER && aDamageIndexAfterDamage == 2) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("Zombie_digger_hardhat",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT3));
            } else if (z.mHelmType == HelmType.HELMTYPE_FOOTBALL && aDamageIndexAfterDamage == 1) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("zombie_football_helmet",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET2));
            } else if (z.mHelmType == HelmType.HELMTYPE_FOOTBALL && aDamageIndexAfterDamage == 2) {
                assert aBodyReanim != null;
                aBodyReanim.setImageOverride("zombie_football_helmet",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET3));
            } else if (z.mHelmType == HelmType.HELMTYPE_WALLNUT && aDamageIndexAfterDamage == 1) {
                Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
                aHeadReanim.setImageOverride("anim_face", Resources.getImageById(ImageID.IMAGE_REANIM_WALLNUT_CRACKED1));
            } else if (z.mHelmType == HelmType.HELMTYPE_WALLNUT && aDamageIndexAfterDamage == 2) {
                Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
                aHeadReanim.setImageOverride("anim_face", Resources.getImageById(ImageID.IMAGE_REANIM_WALLNUT_CRACKED2));
            } else if (z.mHelmType == HelmType.HELMTYPE_TALLNUT && aDamageIndexAfterDamage == 1) {
                Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
                aHeadReanim.setImageOverride("anim_idle", Resources.getImageById(ImageID.IMAGE_REANIM_TALLNUT_CRACKED1));
            } else if (z.mHelmType == HelmType.HELMTYPE_TALLNUT && aDamageIndexAfterDamage == 2) {
                Reanimation aHeadReanim = z.mApp.reanimationGet(z.mSpecialHeadReanimID);
                aHeadReanim.setImageOverride("anim_idle", Resources.getImageById(ImageID.IMAGE_REANIM_TALLNUT_CRACKED2));
            }
        }
        return aDamageRemaining;
    }

    /** Ported from src/Lawn/Zombie.cpp:7803-7820 (Zombie::TakeFlyingDamage). */
    public static int takeFlyingDamage(Zombie z, int theDamage, int theDamageFlags) {
        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)) {
            z.mJustGotShotCounter = 25;
        }

        int aDamageActual = Math.min(z.mFlyingHealth, theDamage);
        int aDamageRemaining = theDamage - aDamageActual;
        z.mFlyingHealth -= aDamageActual;
        if (z.mFlyingHealth == 0) {
            z.landFlyer(theDamageFlags);
        }

        return aDamageRemaining;
    }

    /** Ported from src/Lawn/Zombie.cpp:7821-7969 (Zombie::TakeBodyDamage). */
    public static void takeBodyDamage(Zombie z, int theDamage, int theDamageFlags) {
        if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)) {
            z.mJustGotShotCounter = 25;
        }

        if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_FREEZE)) {
            z.applyChill(false);
        }

        int aBodyHealthOrigin = z.mBodyHealth;
        int aDamageIndexBeforeDamage = getBodyDamageIndex(z);
        z.mBodyHealth -= theDamage;
        int aDamageIndexAfterDamage = getBodyDamageIndex(z);
        if (z.mZombieType == ZombieType.ZOMBIE_ZAMBONI) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)) {
                z.mApp.playFoley(FoleyType.FOLEY_SHIELD_HIT);
            }

            if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_SPIKE)) {
                aBodyReanim.setImageOverride("Zombie_zamboni_1",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE2));
                aBodyReanim.setImageOverride("Zombie_zamboni_2",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE2));
                z.zamboniDeath(theDamageFlags);
            } else if (z.mBodyHealth <= 0) {
                z.zamboniDeath(theDamageFlags);
            } else if (aDamageIndexBeforeDamage != aDamageIndexAfterDamage) {
                if (aDamageIndexAfterDamage == 1) {
                    aBodyReanim.setImageOverride("Zombie_zamboni_1",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE1));
                    aBodyReanim.setImageOverride("Zombie_zamboni_2",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE1));
                } else if (aDamageIndexAfterDamage == 2) {
                    aBodyReanim.setImageOverride("Zombie_zamboni_1",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE2));
                    aBodyReanim.setImageOverride("Zombie_zamboni_2",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE2));
                    z.addAttachedParticle(27, 72, ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
                }
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_CATAPULT) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_SPIKE) || z.mBodyHealth <= 0) {
                aBodyReanim.setImageOverride("Zombie_catapult_siding",
                    Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CATAPULT_SIDING_DAMAGE));
                z.catapultDeath(theDamageFlags);
            } else if (aDamageIndexBeforeDamage != aDamageIndexAfterDamage) {
                if (aDamageIndexAfterDamage == 1) {
                    aBodyReanim.setImageOverride("Zombie_catapult_siding",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_CATAPULT_SIDING_DAMAGE));
                } else if (aDamageIndexAfterDamage == 2) {
                    z.addAttachedParticle(47, 77, ParticleEffect.PARTICLE_ZAMBONI_SMOKE);
                }
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_GARGANTUAR
            || z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aDamageIndexBeforeDamage != aDamageIndexAfterDamage) {
                if (aDamageIndexAfterDamage == 1) {
                    aBodyReanim.setImageOverride("Zombie_gargantua_body1",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_2));
                    aBodyReanim.setImageOverride("Zombie_gargantuar_outerarm_lower",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_OUTERARM_LOWER2));
                } else if (aDamageIndexAfterDamage == 2) {
                    aBodyReanim.setImageOverride("Zombie_gargantua_body1",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_3));
                    aBodyReanim.setImageOverride("Zombie_gargantuar_outerleg_foot",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_FOOT2));
                    aBodyReanim.setImageOverride("Zombie_gargantuar_outerarm_lower",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_OUTERARM_LOWER2));
                    if (z.mZombieType == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                        aBodyReanim.setImageOverride("anim_head1",
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_REDEYE));
                    } else {
                        aBodyReanim.setImageOverride("anim_head1",
                            Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2));
                    }
                }
            }
        } else if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            if (!PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_DOESNT_CAUSE_FLASH)) {
                z.mApp.playFoley(FoleyType.FOLEY_SHIELD_HIT);
            }

            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (aDamageIndexBeforeDamage != aDamageIndexAfterDamage) {
                if (aDamageIndexAfterDamage == 1) {
                    aBodyReanim.setImageOverride("Boss_head",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE1));
                    aBodyReanim.setImageOverride("Boss_jaw",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE1));
                    aBodyReanim.setImageOverride("Boss_outerarm_hand",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE1));
                    aBodyReanim.setImageOverride("Boss_outerarm_thumb2",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE1));
                    aBodyReanim.setImageOverride("Boss_innerleg_foot",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE1));
                } else if (aDamageIndexAfterDamage == 2) {
                    aBodyReanim.setImageOverride("Boss_head",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE2));
                    aBodyReanim.setImageOverride("Boss_jaw",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE2));
                    aBodyReanim.setImageOverride("Boss_outerarm_hand",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE2));
                    aBodyReanim.setImageOverride("Boss_outerarm_thumb2",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE2));
                    aBodyReanim.setImageOverride("Boss_outerleg_foot",
                        Resources.getImageById(ImageID.IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE2));
                    z.applyBossSmokeParticles(true);
                }
            }

            if (aBodyHealthOrigin >= z.mBodyMaxHealth / Zombie.BOSS_FLASH_HEALTH_FRACTION
                && z.mBodyHealth < z.mBodyMaxHealth / Zombie.BOSS_FLASH_HEALTH_FRACTION) {
                z.mApp.addPvzpParticle(770.0f, 260.0f,
                    Board.makeRenderOrder(RenderLayer.RENDER_LAYER_TOP, 0, 0),
                    ParticleEffect.PARTICLE_BOSS_EXPLOSION);
                z.mApp.playFoley(FoleyType.FOLEY_BOSS_EXPLOSION_SMALL);
                z.applyBossSmokeParticles(true);
            }

            if (z.mBodyHealth <= 0) {
                z.mBodyHealth = 1;
            }
        } else {
            updateDamageStates(z, theDamageFlags);
        }

        if (z.mBodyHealth <= 0) {
            z.mBodyHealth = 0;
            z.playDeathAnim(theDamageFlags);
            z.dropLoot();
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:7970-7998 (Zombie::TakeDamage). */
    public static void takeDamage(Zombie z, int theDamage, int theDamageFlags) {
        theDamage = cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry.dispatcher().onZombieDamage(z.mBoard, z,
            theDamage, theDamageFlags);

        if (z.mZombiePhase == ZombiePhase.PHASE_JACK_IN_THE_BOX_POPPING || z.isDeadOrDying()) {
            return;
        }

        int aDamageRemaining = theDamage;

        if (z.isFlying()) {
            aDamageRemaining = takeFlyingDamage(z, aDamageRemaining, theDamageFlags);
        }
        if (aDamageRemaining > 0 && z.mShieldType != ShieldType.SHIELDTYPE_NONE
            && !PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_BYPASSES_SHIELD)) {
            aDamageRemaining = takeShieldDamage(z, aDamageRemaining, theDamageFlags);
            if (PvzpCommon.testBit(theDamageFlags, DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY)) {
                aDamageRemaining = theDamage;
            }
        }
        if (aDamageRemaining > 0 && z.mHelmType != HelmType.HELMTYPE_NONE) {
            aDamageRemaining = takeHelmDamage(z, aDamageRemaining, theDamageFlags);
        }
        if (aDamageRemaining > 0) {
            takeBodyDamage(z, aDamageRemaining, theDamageFlags);
        }
    }

    /** Ported from src/Lawn/Zombie.cpp:8081-8175 (Zombie::EffectedByDamage). */
    public static boolean effectedByDamage(Zombie z, int theDamageRangeFlags) {
        if (!PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_DYING) && z.isDeadOrDying()) {
            return false;
        }

        if (PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_ONLY_MINDCONTROLLED)) {
            if (!z.mMindControlled) {
                return false;
            }
        } else if (z.mMindControlled) {
            return false;
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BUNGEE && z.mZombiePhase != ZombiePhase.PHASE_BUNGEE_AT_BOTTOM
            && z.mZombiePhase != ZombiePhase.PHASE_BUNGEE_GRABBING) {
            return false;  // bungee zombies can only be hit while staying at the bottom
        }

        if (z.mZombieHeight == ZombieHeight.HEIGHT_GETTING_BUNGEE_DROPPED) {
            return false;  // cannot be hit while being airdropped
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BOSS) {
            Reanimation aBodyReanim = z.mApp.reanimationGet(z.mBodyReanimID);
            if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_ENTER && aBodyReanim.mAnimTime < 0.5f) {
                return false;
            }
            if (z.mZombiePhase == ZombiePhase.PHASE_BOSS_HEAD_LEAVE && aBodyReanim.mAnimTime > 0.5f) {
                return false;
            }

            if (z.mZombiePhase != ZombiePhase.PHASE_BOSS_HEAD_IDLE_BEFORE_SPIT
                && z.mZombiePhase != ZombiePhase.PHASE_BOSS_HEAD_IDLE_AFTER_SPIT
                && z.mZombiePhase != ZombiePhase.PHASE_BOSS_HEAD_SPIT) {
                return false;  // the boss can only be hit while its head is down
            }
        }

        if (z.mZombieType == ZombieType.ZOMBIE_BOBSLED && z.getBobsledPosition() > 0) {
            return false;  // while the bobsled exists, only the lead zombie can be hit
        }

        if (z.mZombiePhase == ZombiePhase.PHASE_POLEVAULTER_IN_VAULT
            || z.mZombiePhase == ZombiePhase.PHASE_IMP_GETTING_THROWN
            || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISING
            || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING_PAUSE_WITHOUT_AXE
            || z.mZombiePhase == ZombiePhase.PHASE_DIGGER_RISE_WITHOUT_AXE
            || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_INTO_POOL
            || z.mZombiePhase == ZombiePhase.PHASE_DOLPHIN_IN_JUMP
            || z.mZombiePhase == ZombiePhase.PHASE_SNORKEL_INTO_POOL
            || z.mZombiePhase == ZombiePhase.PHASE_BALLOON_POPPING
            || z.mZombiePhase == ZombiePhase.PHASE_RISING_FROM_GRAVE
            || z.mZombiePhase == ZombiePhase.PHASE_BOBSLED_CRASHING
            || z.mZombiePhase == ZombiePhase.PHASE_DANCER_RISING) {
            return PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_OFF_GROUND);
        }

        if (z.mZombieType != ZombieType.ZOMBIE_BOBSLED && z.getZombieRect().mX > GameConstants.WIDE_BOARD_WIDTH) {
            return false;  // off-board zombies cannot be hit, except the bobsled team
        }

        boolean submerged = z.mZombieType == ZombieType.ZOMBIE_SNORKEL && z.mInPool && !z.mIsEating;
        if (PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_SUBMERGED) && submerged) {
            return true;
        }

        boolean underground = z.mZombiePhase == ZombiePhase.PHASE_DIGGER_TUNNELING;
        if (PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_UNDERGROUND) && underground) {
            return true;
        }

        if (PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_FLYING) && z.isFlying()) {
            return true;
        }

        return PvzpCommon.testBit(theDamageRangeFlags, DamageRangeFlags.DAMAGES_GROUND) && !z.isFlying()
            && !submerged && !underground;
    }

    /** Ported from src/Lawn/Zombie.cpp:8424-8441 (Zombie::IsTangleKelpTarget). */
    public static boolean isTangleKelpTarget(Zombie z) {
        if (z.mZombieHeight == ZombieHeight.HEIGHT_DRAGGED_UNDER) {
            return true;
        }

        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mSeedType == SeedType.SEED_TANGLEKELP
                && aPlant.mTargetZombieID == z.mBoard.zombieGetID(z)) {
                return true;
            }
        }

        return false;
    }

    /** Ported from src/Lawn/Zombie.cpp:8442-8458 (Zombie::IsSquashTarget). */
    public static boolean isSquashTarget(Zombie z, Plant theExcept) {
        int anId = z.mBoard.zombieGetID(z);

        for (Plant aPlant : z.mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant != theExcept && aPlant.mSeedType == SeedType.SEED_SQUASH && aPlant.mTargetZombieID == anId) {
                return true;
            }
        }

        return false;
    }
}
