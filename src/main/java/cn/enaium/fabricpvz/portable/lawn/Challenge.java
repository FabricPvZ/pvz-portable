/*
 * Ported from src/Lawn/Challenge.h:23-249 and src/Lawn/Challenge.cpp:1-5336.
 *
 * The upstream class holds the remaining ~40% of the game logic that Board does not own: the
 * challenge/level DATA (gZombieWaves, gZombieAllowedLevels, gChallengeDefs, gArtChallenge*) and the
 * per-mode mini-game state machines. In this port the data tables and the mode-agnostic helpers
 * (adventure/survival wave setup, art-challenge grid, planting/grid rules) live here, and every
 * mini-game state machine (Beghouled + Beghouled Twist, Slot Machine, Zombiquarium, Scary Potter /
 * I,Zombie, Squirrel, Tree of Wisdom, Last Stand, Portal Combat, Whack-a-Zombie, the weather modes)
 * is a full upstream-faithful implementation, split for readability into the Challenge<Mode> helper
 * classes under this package; the public entry points below delegate to them.
 */
package cn.enaium.fabricpvz.portable.lawn;

import cn.enaium.fabricpvz.portable.lawn.widget.GameButton;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedArray;
import cn.enaium.fabricpvz.portable.pvzp.PvzpWeightedGridArray;
import cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimator;
import cn.enaium.fabricpvz.portable.sexy.MTRand;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Ported from src/Lawn/Challenge.h:61-249 and src/Lawn/Challenge.cpp (5336 lines).
 *
 * <p>All public fields keep their upstream names (Challenge.h:64-87); every upstream enum-typed
 * field/parameter/return value is an {@code int} holding this port's enum-constant value
 * (AGENTS.md §7, item 2).
 *
 * <p>The data tables are reproduced verbatim from the C++ source, with the upstream {@code line}
 * recorded per row so a future diff is mechanical:
 * <ul>
 *   <li>{@link #gZombieWaves} - Challenge.h:259-265</li>
 *   <li>{@link #gZombieAllowedLevels} - Challenge.h:267-485 (33 entries x 50 flags)</li>
 *   <li>{@link #gChallengeDefs} - Widget/ChallengeScreen.cpp:39-215 (72 entries)</li>
 *   <li>{@link #gArtChallengeWallnut} / {@link #gArtChallengeSunFlower} /
 *       {@link #gArtChallengeStarFruit} - Challenge.h:487-515</li>
 * </ul>
 *
 * <p>The per-mode mini-game state machines are implemented in sibling helper classes under this
 * package ({@link ChallengeBeghouled}, {@link ChallengeScaryPotter}, {@link ChallengeIZombie},
 * {@link ChallengeWeather}, {@link ChallengePortalCombat}, {@link ChallengeSlotMachine},
 * {@link ChallengeWhackAZombie}, {@link ChallengeZombiquarium}, {@link ChallengeSquirrel},
 * {@link ChallengeTreeOfWisdom}); the mode-agnostic entry points ({@link #update()},
 * {@link #mouseDown}, {@link #startLevel()}, {@link #initLevel()}, {@link #drawBackdrop}, ...) keep
 * the upstream dispatch and delegate the mode-specific bodies.
 */
public class Challenge {

    // ---------------------------------------------------------------------------------------------
    // Constants (Challenge.h:32-35, Challenge.cpp:59-66)
    // ---------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/Challenge.h:32 ({@code BEGHOULED_MAX_GRIDSIZEX}). */
    public static final int BEGHOULED_MAX_GRIDSIZEX = 8;

    /** Ported from src/Lawn/Challenge.h:33 ({@code BEGHOULED_MAX_GRIDSIZEY}). */
    public static final int BEGHOULED_MAX_GRIDSIZEY = 5;

    /** Ported from src/Lawn/Challenge.h:34 ({@code ART_CHALLEGE_SIZE_X = MAX_GRID_SIZE_X}). */
    public static final int ART_CHALLEGE_SIZE_X = Board.MAX_GRID_SIZE_X;

    /** Ported from src/Lawn/Challenge.h:35 ({@code MAX_PICK_GRID_SIZE}). */
    public static final int MAX_PICK_GRID_SIZE = 50;

    /** Ported from src/Lawn/Challenge.cpp:59 ({@code BEGHOULED_WINNING_SCORE}). */
    public static final int BEGHOULED_WINNING_SCORE = 75;

    /** Ported from src/Lawn/Challenge.cpp:60 ({@code SLOT_MACHINE_WINNING_SCORE}). */
    public static final int SLOT_MACHINE_WINNING_SCORE = 2000;

    /** Ported from src/Lawn/Challenge.cpp:61 ({@code ZOMBIQUARIUM_WINNING_SCORE}). */
    public static final int ZOMBIQUARIUM_WINNING_SCORE = 1000;

    /** Ported from src/Lawn/Challenge.cpp:62 ({@code I_ZOMBIE_WINNING_SCORE}). */
    public static final int I_ZOMBIE_WINNING_SCORE = 5;

    /** Ported from src/Lawn/Challenge.cpp:63 ({@code MAX_PORTALS}). */
    public static final int MAX_PORTALS = 4;

    /** Ported from src/Lawn/Challenge.cpp:64 ({@code MAX_SQUIRRELS}). */
    public static final int MAX_SQUIRRELS = 7;

    /** Ported from src/Lawn/Challenge.cpp:65 ({@code MAX_SCARY_POTS}). */
    public static final int MAX_SCARY_POTS = 54;

    /** Ported from src/Lawn/Challenge.cpp:66 ({@code STORM_FLASH_TIME}). */
    public static final int STORM_FLASH_TIME = 150;

    // ---------------------------------------------------------------------------------------------
    // gZombieWaves - Challenge.h:259-265
    // ---------------------------------------------------------------------------------------------

    /**
     * Ported from src/Lawn/Challenge.h:259-265 ({@code gZombieWaves[NUM_LEVELS]}).
     *
     * <p>Number of waves of each first-time adventure level; index = {@code level - 1}. Repeat play
     * derives its wave count elsewhere (BoardWaves), this table is adventure-only.
     */
    public static final int[] gZombieWaves = {
            4, 6, 8, 10, 8, 10, 20, 10, 20, 20, // levels 1-10
            10, 20, 10, 20, 10, 10, 20, 10, 20, 20, // levels 11-20
            10, 20, 20, 30, 20, 20, 30, 20, 30, 30, // levels 21-30
            10, 20, 10, 20, 20, 10, 20, 10, 20, 20, // levels 31-40
            10, 20, 20, 30, 20, 20, 30, 20, 30, 30, // levels 41-50
    };

    // ---------------------------------------------------------------------------------------------
    // gZombieAllowedLevels - Challenge.h:267-485 (verbatim, one javadoc per upstream row)
    // ---------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/Challenge.h:267-485 ({@code gZombieAllowedLevels[NUM_ZOMBIE_TYPES]}). */
    public static final ZombieAllowedLevels[] gZombieAllowedLevels = {
            // {@code ZOMBIE_NORMAL}, Challenge.h:268-275: allowed on all 50 levels.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_NORMAL, new int[] {
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            }),
            // {@code ZOMBIE_FLAG}, Challenge.h:277-284: allowed on all 50 levels.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_FLAG, new int[] {
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            }),
            // {@code ZOMBIE_TRAFFIC_CONE}, Challenge.h:286-293.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_TRAFFIC_CONE, new int[] {
                    0, 0, 1, 1, 1, 1, 1, 1, 1, 1,
                    0, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            }),
            // {@code ZOMBIE_POLEVAULTER}, Challenge.h:295-302.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_POLEVAULTER, new int[] {
                    0, 0, 0, 0, 0, 1, 1, 0, 1, 1,
                    0, 0, 0, 1, 1, 0, 0, 0, 0, 0,
                    0, 0, 0, 1, 0, 0, 0, 0, 1, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_PAIL}, Challenge.h:304-311.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_PAIL, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                    0, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                    0, 1, 0, 1, 0, 0, 1, 0, 1, 1,
                    0, 0, 0, 0, 0, 0, 1, 0, 1, 1,
                    0, 1, 0, 0, 1, 0, 0, 0, 1, 1,
            }),
            // {@code ZOMBIE_NEWSPAPER}, Challenge.h:313-320.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_NEWSPAPER, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                    0, 1, 0, 1, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_DOOR}, Challenge.h:322-329.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_DOOR, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 1, 1, 0, 0, 1, 0, 1, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_FOOTBALL}, Challenge.h:331-338.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_FOOTBALL, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 1, 0, 0, 1,
                    0, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                    0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_DANCER}, Challenge.h:340-347: levels 18-20 (mStartingLevel 18, Zombie.cpp:89).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_DANCER, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_BACKUP_DANCER}, Challenge.h:349-356: levels 18-20 (mStartingLevel 18, Zombie.cpp:90).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_BACKUP_DANCER, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_DUCKY_TUBE}, Challenge.h:358: upstream writes {@code { 0 }} = all zero.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_DUCKY_TUBE, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_SNORKEL}, Challenge.h:359-366.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_SNORKEL, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 1, 1, 1, 0, 1, 0, 0, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_ZAMBONI}, Challenge.h:368-375.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_ZAMBONI, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 1, 0, 1, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_BOBSLED}, Challenge.h:377-384.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_BOBSLED, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 1, 0, 1, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_DOLPHIN_RIDER}, Challenge.h:386-393.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_DOLPHIN_RIDER, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                    0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_JACK_IN_THE_BOX}, Challenge.h:395-402.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_JACK_IN_THE_BOX, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 0, 0, 0, 0, 1, 0, 0, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 1, 1,
            }),
            // {@code ZOMBIE_BALLOON}, Challenge.h:404-411.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_BALLOON, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 1, 1, 0, 0, 0, 0, 1, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_DIGGER}, Challenge.h:413-420.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_DIGGER, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 1, 0, 0, 1,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_POGO}, Challenge.h:422-429.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_POGO, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
                    0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_YETI}, Challenge.h:431: {@code {0}} (spawned via its own special rule).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_YETI, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            }),
            // {@code ZOMBIE_BUNGEE}, Challenge.h:432-439.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_BUNGEE, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 0, 0, 0, 0, 1, 0, 1, 1,
            }),
            // {@code ZOMBIE_LADDER}, Challenge.h:441-448.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_LADDER, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 1, 1, 1, 0, 1, 0, 1, 1,
            }),
            // {@code ZOMBIE_CATAPULT}, Challenge.h:450-457.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_CATAPULT, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 1, 1, 0, 1, 1,
            }),
            // {@code ZOMBIE_GARGANTUAR}, Challenge.h:459-466.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_GARGANTUAR, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
            }),
            // {@code ZOMBIE_IMP}, Challenge.h:468-475.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_IMP, new int[] {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 1, 1, 1,
            }),
            // {@code ZOMBIE_BOSS}, Challenge.h:477: {@code {0}}.
            new ZombieAllowedLevels(ZombieType.ZOMBIE_BOSS, allZeroAllowedLevels()),
            // {@code ZOMBIE_PEA_HEAD}, Challenge.h:478: {@code {0}} (War and Peas only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_PEA_HEAD, allZeroAllowedLevels()),
            // {@code ZOMBIE_WALLNUT_HEAD}, Challenge.h:479: {@code {0}} (War and Peas only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_WALLNUT_HEAD, allZeroAllowedLevels()),
            // {@code ZOMBIE_JALAPENO_HEAD}, Challenge.h:480: {@code {0}} (War and Peas 2 only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_JALAPENO_HEAD, allZeroAllowedLevels()),
            // {@code ZOMBIE_GATLING_HEAD}, Challenge.h:481: {@code {0}} (War and Peas 2 only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_GATLING_HEAD, allZeroAllowedLevels()),
            // {@code ZOMBIE_SQUASH_HEAD}, Challenge.h:482: {@code {0}} (War and Peas 2 only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_SQUASH_HEAD, allZeroAllowedLevels()),
            // {@code ZOMBIE_TALLNUT_HEAD}, Challenge.h:483: {@code {0}} (War and Peas 2 only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_TALLNUT_HEAD, allZeroAllowedLevels()),
            // {@code ZOMBIE_REDEYE_GARGANTUAR}, Challenge.h:484: {@code {0}} (survival-only).
            new ZombieAllowedLevels(ZombieType.ZOMBIE_REDEYE_GARGANTUAR, allZeroAllowedLevels()),
    };

    /** Shared all-zero vector for the upstream {@code {0}} entries (Challenge.h:358, 431, 477-484). */
    private static int[] allZeroAllowedLevels() {
        return new int[GameConstants.NUM_LEVELS];
    }

    // ---------------------------------------------------------------------------------------------
    // gChallengeDefs - Widget/ChallengeScreen.cpp:39-215 (verbatim, one row per upstream line)
    // ---------------------------------------------------------------------------------------------

    /**
     * Ported from src/Lawn/Widget/ChallengeScreen.cpp:39-215 ({@code gChallengeDefs}).
     *
     * <p>72 rows ordered by the upstream {@code GameMode} value, so
     * {@code gChallengeDefs[mode - GAMEMODE_SURVIVAL_NORMAL_STAGE_1].mChallengeMode == mode} (asserted
     * upstream by {@code GetChallengeDefinition}). Page totals: SURVIVAL 11, LIMBO 21, CHALLENGE 20,
     * PUZZLE 20.
     */
    public static final ChallengeDefinition[] gChallengeDefs = {
            // ChallengeScreen.cpp:40-49 - survival normal stage 1-5, hard stage 1-5
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_1, 0, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 0, "[SURVIVAL_DAY_NORMAL]"), // :40
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_2, 1, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 1, "[SURVIVAL_NIGHT_NORMAL]"), // :41
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_3, 2, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 2, "[SURVIVAL_POOL_NORMAL]"), // :42
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_4, 3, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 3, "[SURVIVAL_FOG_NORMAL]"), // :43
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_NORMAL_STAGE_5, 4, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 0, 4, "[SURVIVAL_ROOF_NORMAL]"), // :44
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_1, 5, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 0, "[SURVIVAL_DAY_HARD]"), // :45
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_2, 6, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 1, "[SURVIVAL_NIGHT_HARD]"), // :46
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_3, 7, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 2, "[SURVIVAL_POOL_HARD]"), // :47
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_4, 8, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 3, "[SURVIVAL_FOG_HARD]"), // :48
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_HARD_STAGE_5, 9, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 1, 4, "[SURVIVAL_ROOF_HARD]"), // :49
            // ChallengeScreen.cpp:50-54 - survival endless stage 1-5 (stage 3 sits on the SURVIVAL page)
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_1, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 0, "[SURVIVAL_DAY_ENDLESS]"), // :50
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_2, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 1, "[SURVIVAL_NIGHT_ENDLESS]"), // :51
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_3, 10, ChallengePage.CHALLENGE_PAGE_SURVIVAL, 2, 2, "[SURVIVAL_POOL_ENDLESS]"), // :52
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_4, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 2, "[SURVIVAL_FOG_ENDLESS]"), // :53
            new ChallengeDefinition(GameMode.GAMEMODE_SURVIVAL_ENDLESS_STAGE_5, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 3, "[SURVIVAL_ROOF_ENDLESS]"), // :54
            // ChallengeScreen.cpp:55-74 - the 20 CHALLENGE-page mini-games
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS, 0, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 0, "[WAR_AND_PEAS]"), // :55
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING, 6, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 1, "[WALL_NUT_BOWLING]"), // :56
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE, 2, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 2, "[SLOT_MACHINE]"), // :57
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS, 3, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 3, "[ITS_RAINING_SEEDS]"), // :58
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BEGHOULED, 1, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 0, 4, "[BEGHOULED]"), // :59
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL, 8, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 0, "[INVISIGHOUL]"), // :60
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SEEING_STARS, 5, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 1, "[SEEING_STARS]"), // :61
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM, 7, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 2, "[ZOMBIQUARIUM]"), // :62
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST, 20, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 3, "[BEGHOULED_TWIST]"), // :63
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_LITTLE_TROUBLE, 12, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 1, 4, "[LITTLE_TROUBLE]"), // :64
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, 15, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 0, "[PORTAL_COMBAT]"), // :65
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_COLUMN, 4, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 1, "[COLUMN_AS_YOU_SEE_EM]"), // :66
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA, 17, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 2, "[BOBSLED_BONANZA]"), // :67
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SPEED, 18, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 3, "[ZOMBIES_ON_SPEED]"), // :68
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WHACK_A_ZOMBIE, 16, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 2, 4, "[WHACK_A_ZOMBIE]"), // :69
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_LAST_STAND, 21, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 0, "[LAST_STAND]"), // :70
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2, 0, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 1, "[WAR_AND_PEAS_2]"), // :71
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2, 6, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 2, "[WALL_NUT_BOWLING_EXTREME]"), // :72
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_POGO_PARTY, 14, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 3, "[POGO_PARTY]"), // :73
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_FINAL_BOSS, 19, ChallengePage.CHALLENGE_PAGE_CHALLENGE, 3, 4, "[FINAL_BOSS]"), // :74
            // ChallengeScreen.cpp:75-89 - 17 LIMBO entries (15 here + endless 1/2/4/5 above and upsell/intro below)
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT, 0, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 0, "[ART_CHALLENGE_WALL_NUT]"), // :75
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY, 1, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 1, "[SUNNY_DAY]"), // :76
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_RESODDED, 2, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 2, "[UNSODDED]"), // :77
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BIG_TIME, 3, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 3, "[BIG_TIME]"), // :78
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER, 4, ChallengePage.CHALLENGE_PAGE_LIMBO, 0, 4, "[ART_CHALLENGE_SUNFLOWER]"), // :79
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, 5, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 0, "[AIR_RAID]"), // :80
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ICE, 6, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 1, "[ICE_LEVEL]"), // :81
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN, 7, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 2, "[ZEN_GARDEN]"), // :82
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY, 8, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 3, "[HIGH_GRAVITY]"), // :83
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER, 11, ChallengePage.CHALLENGE_PAGE_LIMBO, 1, 4, "[GRAVE_DANGER]"), // :84
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SHOVEL, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 0, "[CAN_YOU_DIG_IT]"), // :85
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_STORMY_NIGHT, 13, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 1, "[DARK_STORMY_NIGHT]"), // :86
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_BUNGEE_BLITZ, 9, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 2, "[BUNGEE_BLITZ]"), // :87
            new ChallengeDefinition(GameMode.GAMEMODE_CHALLENGE_SQUIRREL, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 3, "Squirrel"), // :88
            new ChallengeDefinition(GameMode.GAMEMODE_TREE_OF_WISDOM, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 4, "Tree of Wisdom"), // :89
            // ChallengeScreen.cpp:90-99 - Scary Potter 1-9 + endless
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_1, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 0, "[SCARY_POTTER_1]"), // :90
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_2, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 1, "[SCARY_POTTER_2]"), // :91
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_3, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 2, "[SCARY_POTTER_3]"), // :92
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_4, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 3, "[SCARY_POTTER_4]"), // :93
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_5, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 0, 4, "[SCARY_POTTER_5]"), // :94
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_6, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 0, "[SCARY_POTTER_6]"), // :95
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_7, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 1, "[SCARY_POTTER_7]"), // :96
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_8, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 2, "[SCARY_POTTER_8]"), // :97
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_9, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 3, "[SCARY_POTTER_9]"), // :98
            new ChallengeDefinition(GameMode.GAMEMODE_SCARY_POTTER_ENDLESS, 10, ChallengePage.CHALLENGE_PAGE_PUZZLE, 1, 4, "[SCARY_POTTER_ENDLESS]"), // :99
            // ChallengeScreen.cpp:100-109 - I, Zombie 1-9 + endless
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 0, "[I_ZOMBIE_1]"), // :100
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 1, "[I_ZOMBIE_2]"), // :101
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 2, "[I_ZOMBIE_3]"), // :102
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 3, "[I_ZOMBIE_4]"), // :103
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 2, 4, "[I_ZOMBIE_5]"), // :104
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 0, "[I_ZOMBIE_6]"), // :105
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 1, "[I_ZOMBIE_7]"), // :106
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 2, "[I_ZOMBIE_8]"), // :107
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 3, "[I_ZOMBIE_9]"), // :108
            new ChallengeDefinition(GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS, 11, ChallengePage.CHALLENGE_PAGE_PUZZLE, 3, 4, "[I_ZOMBIE_ENDLESS]"), // :109
            // ChallengeScreen.cpp:110-111 - the two non-challenge LIMBO rows
            new ChallengeDefinition(GameMode.GAMEMODE_UPSELL, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 3, 4, "Upsell"), // :110
            new ChallengeDefinition(GameMode.GAMEMODE_INTRO, 10, ChallengePage.CHALLENGE_PAGE_LIMBO, 2, 3, "Intro"), // :111
    };

    /**
     * Ported from src/Lawn/Widget/ChallengeScreen.cpp:213-222 ({@code GetChallengeDefinition}).
     *
     * <p>Upstream asserts {@code theChallengeMode} is in range and that the table is ordered by game
     * mode; the Java port throws {@link IndexOutOfBoundsException} for an out-of-range mode instead of
     * relying on the assertion.
     */
    public static ChallengeDefinition getChallengeDefinition(int theChallengeMode) {
        if (theChallengeMode < 0 || theChallengeMode >= gChallengeDefs.length) {
            throw new IndexOutOfBoundsException(
                    "Challenge.getChallengeDefinition: challenge mode " + theChallengeMode + " out of range");
        }
        return gChallengeDefs[theChallengeMode];
    }

    // ---------------------------------------------------------------------------------------------
    // gArtChallenge* - Challenge.h:487-515 (verbatim; upstream is [gridY][gridX])
    // ---------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/Challenge.h:487-494 ({@code gArtChallengeWallnut[MAX_GRID_SIZE_Y][MAX_GRID_SIZE_X]}). */
    public static final int[][] gArtChallengeWallnut = {
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_WALLNUT, SeedType.SEED_WALLNUT, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_WALLNUT, SeedType.SEED_WALLNUT, SeedType.SEED_WALLNUT, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
    };

    /** Ported from src/Lawn/Challenge.h:496-503 ({@code gArtChallengeSunFlower[MAX_GRID_SIZE_Y][MAX_GRID_SIZE_X]}). */
    public static final int[][] gArtChallengeSunFlower = {
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_WALLNUT, SeedType.SEED_WALLNUT, SeedType.SEED_WALLNUT, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_UMBRELLA, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_UMBRELLA, SeedType.SEED_UMBRELLA, SeedType.SEED_UMBRELLA, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
    };

    /** Ported from src/Lawn/Challenge.h:505-512 ({@code gArtChallengeStarFruit[MAX_GRID_SIZE_Y][MAX_GRID_SIZE_X]}). */
    public static final int[][] gArtChallengeStarFruit = {
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_STARFRUIT, SeedType.SEED_NONE, SeedType.SEED_NONE },
            { SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE, SeedType.SEED_NONE },
    };

    // ---------------------------------------------------------------------------------------------
    // Instance fields - Challenge.h:64-87
    // ---------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/Challenge.h:64 ({@code mApp}). Assigned by Board after construction. */
    public LawnApp mApp;

    /** Ported from src/Lawn/Challenge.h:65 ({@code mBoard}). Assigned by Board after construction. */
    public Board mBoard;

    /** Ported from src/Lawn/Challenge.h:66 ({@code mBeghouledMouseCapture}). */
    public int mBeghouledMouseCapture;

    /** Ported from src/Lawn/Challenge.h:67 ({@code mBeghouledMouseDownX}). */
    public int mBeghouledMouseDownX;

    /** Ported from src/Lawn/Challenge.h:68 ({@code mBeghouledMouseDownY}). */
    public int mBeghouledMouseDownY;

    /** Ported from src/Lawn/Challenge.h:69 ({@code mBeghouledEated[9][6]}). */
    public int[][] mBeghouledEated = new int[Board.MAX_GRID_SIZE_X][Board.MAX_GRID_SIZE_Y];

    /** Ported from src/Lawn/Challenge.h:70 ({@code mBeghouledPurcasedUpgrade[NUM_BEGHOULED_UPGRADES]}). */
    public int[] mBeghouledPurcasedUpgrade = new int[BeghouledUpgrade.NUM_BEGHOULED_UPGRADES];

    /** Ported from src/Lawn/Challenge.h:71 ({@code mBeghouledMatchesThisMove}). */
    public int mBeghouledMatchesThisMove;

    /** Ported from src/Lawn/Challenge.h:72 ({@code mChallengeState}, a {@link ChallengeState} value). */
    public int mChallengeState;

    /** Ported from src/Lawn/Challenge.h:73 ({@code mChallengeStateCounter}). */
    public int mChallengeStateCounter;

    /** Ported from src/Lawn/Challenge.h:74 ({@code mConveyorBeltCounter}). */
    public int mConveyorBeltCounter;

    /** Ported from src/Lawn/Challenge.h:75 ({@code mChallengeScore}). */
    public int mChallengeScore;

    /** Ported from src/Lawn/Challenge.h:76 ({@code mShowBowlingLine}). */
    public int mShowBowlingLine;

    /** Ported from src/Lawn/Challenge.h:77 ({@code mLastConveyorSeedType}, a {@link SeedType} value). */
    public int mLastConveyorSeedType;

    /** Ported from src/Lawn/Challenge.h:78 ({@code mSurvivalStage}). */
    public int mSurvivalStage;

    /** Ported from src/Lawn/Challenge.h:79 ({@code mSlotMachineRollCount}). */
    public int mSlotMachineRollCount;

    /** Ported from src/Lawn/Challenge.h:80 ({@code mReanimChallenge}, a {@link ReanimationID} value). */
    public int mReanimChallenge;

    /** Ported from src/Lawn/Challenge.h:81 ({@code mReanimClouds[6]}). */
    public int[] mReanimClouds = new int[6];

    /** Ported from src/Lawn/Challenge.h:82 ({@code mCloudsCounter[6]}). */
    public int[] mCloudsCounter = new int[6];

    /** Ported from src/Lawn/Challenge.h:83 ({@code mChallengeGridX}). */
    public int mChallengeGridX;

    /** Ported from src/Lawn/Challenge.h:84 ({@code mChallengeGridY}). */
    public int mChallengeGridY;

    /** Ported from src/Lawn/Challenge.h:85 ({@code mScaryPotterPots}). */
    public int mScaryPotterPots;

    /** Ported from src/Lawn/Challenge.h:86 ({@code mRainCounter}). */
    public int mRainCounter;

    /** Ported from src/Lawn/Challenge.h:87 ({@code mTreeOfWisdomTalkIndex}). */
    public int mTreeOfWisdomTalkIndex;

    // ---------------------------------------------------------------------------------------------
    // Constructor - Challenge.cpp:72-105
    // ---------------------------------------------------------------------------------------------

    /**
     * Ported from {@code Challenge::Challenge()} (Challenge.cpp:68-127): upstream reads the globals
     * first ({@code mApp = (LawnApp*)gSexyAppBase; mBoard = mApp->mBoard;}) and only then initialises
     * the mode-dependent state, so the slot-machine handle branch sees a live app and board. The port
     * takes the board as a parameter and keeps that order. {@code mBeghouledEated} is zero-filled and
     * {@code mBeghouledPurcasedUpgrade} is filled with {@code false} (0) exactly like upstream's
     * {@code memset}.
     */
    public Challenge(Board theBoard) {
        mBoard = theBoard;
        mApp = theBoard != null ? theBoard.mApp : null;
        mBeghouledMouseCapture = 0;
        mBeghouledMouseDownX = 0;
        mBeghouledMouseDownY = 0;
        mChallengeStateCounter = 0;
        mConveyorBeltCounter = 0;
        mChallengeScore = 0;
        mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        mShowBowlingLine = 0;
        mLastConveyorSeedType = SeedType.SEED_NONE;
        mSurvivalStage = 0;
        mSlotMachineRollCount = 0;
        mReanimChallenge = ReanimationID.REANIMATIONID_NULL;
        mChallengeGridX = 0;
        mChallengeGridY = 0;
        mScaryPotterPots = 0;
        mBeghouledMatchesThisMove = 0;
        mRainCounter = 0;
        mTreeOfWisdomTalkIndex = 0;
        for (int i = 0; i < 6; i++) {
            mReanimClouds[i] = ReanimationID.REANIMATIONID_NULL;
        }
        // Challenge.cpp:91: memset(mBeghouledEated, 0, ...) - int[][] is already zero-filled.
        for (int i = 0; i < BeghouledUpgrade.NUM_BEGHOULED_UPGRADES; i++) {
            mBeghouledPurcasedUpgrade[i] = 0;
        }

        if (mApp != null && mApp.mBoard != null && mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE) {
            // Challenge.cpp:95-104
            Rect aHandleRect = slotMachineGetHandleRect();
            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_SLOT_MACHINE_HANDLE, true);
            Reanimation aHandleReanim = mApp.addReanimation(aHandleRect.mX - 377, aHandleRect.mY - 20, 0,
                    ReanimationType.REANIM_SLOT_MACHINE_HANDLE);
            aHandleReanim.mIsAttachment = true;
            aHandleReanim.mAnimRate = 0;
            mReanimChallenge = mApp.reanimationGetID(aHandleReanim);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Supported mode-agnostic ports
    // ---------------------------------------------------------------------------------------------

    /**
     * Ported from src/Lawn/Challenge.cpp:107-116 ({@code Challenge::LoadBeghouledBoardState}).
     */
    public void loadBeghouledBoardState(BeghouledBoardState theBoardState) {
        for (int i = 0; i < Board.MAX_GRID_SIZE_X; i++) {
            for (int j = 0; j < Board.MAX_GRID_SIZE_Y; j++) {
                theBoardState.mSeedType[i][j] = SeedType.SEED_NONE;
            }
        }

        for (Plant aPlant : mBoard.mPlants) {
            if (!aPlant.mDead) {
                theBoardState.mSeedType[aPlant.mPlantCol][aPlant.mRow] = aPlant.mSeedType;
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:118-183 ({@code Challenge::InitLevel}).
     *
     * <p>Mode-agnostic dispatch: the adventure/survival branches (first-time adventure level 5 pea
     * shooters, final-boss and column conveyor presets, invisighoul seeds, storm/rain counters) run for
     * real; the mini-game initialisers are the throwing bodies.
     */
    public void initLevel() {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
            mChallengeStateCounter = 100;
            mApp.playFoley(FoleyType.FOLEY_RAIN);
        }
        if (mApp.isStormyNightLevel()) {
            mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_2;
            mChallengeStateCounter = 100;
            mApp.playFoley(FoleyType.FOLEY_RAIN);
        }
        if (mApp.isFinalBossLevel()) {
            mBoard.mSeedBank.addSeed(SeedType.SEED_CABBAGEPULT, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_JALAPENO, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_CABBAGEPULT, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_ICESHROOM, false);
            mConveyorBeltCounter = 1000;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            // Challenge.cpp:139-143
            mApp.mZenGarden.mGardenType = GardenType.GARDEN_MAIN;
            mApp.mZenGarden.zenGardenInitLevel();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            mBoard.mSeedBank.addSeed(SeedType.SEED_POTATOMINE, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_TALLNUT, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_MELONPULT, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_MAGNETSHROOM, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_INSTANT_COFFEE, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_MELONPULT, false);
            mConveyorBeltCounter = 1000;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
            mBoard.mSeedBank.addSeed(SeedType.SEED_PEASHOOTER, false);
            mBoard.mSeedBank.addSeed(SeedType.SEED_ICESHROOM, false);
            mConveyorBeltCounter = 1000;
        }
        if (mApp.isIZombieLevel()) {
            iZombieInitLevel();
        }
        if (mApp.isScaryPotterLevel()) {
            scaryPotterPopulate();
        }
        if (mApp.isFirstTimeAdventureMode() && mBoard.mLevel == 5) {
            // Challenge.cpp:150-155
            mBoard.newPlant(5, 1, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
            mBoard.newPlant(7, 2, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
            mBoard.newPlant(6, 3, SeedType.SEED_PEASHOOTER, SeedType.SEED_NONE);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            mChallengeGridX = -1;
            mChallengeGridY = -1;
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            treeOfWisdomInit();
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:185-323 ({@code Challenge::StartLevel}).
     *
     * <p>Mode-agnostic dispatch; the science/roof/survival/wallnut-bowling/bookkeeping paths run for
     * real, the mini-game starters ({@code BeghouledMakeStartBoard}, {@code PortalStart},
     * {@code ScaryPotterStart}, {@code IZombieStart}, {@code SquirrelStart},
     * {@code ShovelAddWallnuts}, {@code ZombiquariumSpawnSnorkle}) are the throwing bodies.
     */
    public void startLevel() {
        if (mApp.isWhackAZombieLevel()) {
            // Challenge.cpp:187-196: hammer cursor + the REANIM_HAMMER cursor attachment.
            mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_HAMMER;
            mBoard.mZombieCountDown = 200;
            mBoard.mZombieCountDownStart = mBoard.mZombieCountDown;

            Reanimator.reanimatorEnsureDefinitionLoaded(ReanimationType.REANIM_HAMMER, true);
            mApp.removeReanimation(mBoard.mCursorObject.mReanimCursorID);
            Reanimation aHammerReanim = mApp.addReanimation(-25.0f, 16.0f, 0, ReanimationType.REANIM_HAMMER);
            aHammerReanim.mIsAttachment = true;
            aHammerReanim.playReanim("anim_whack_zombie", ReanimLoopType.REANIM_PLAY_ONCE_AND_HOLD, 0, 24.0f);
            aHammerReanim.mAnimTime = 1.0f;
            mBoard.mCursorObject.mReanimCursorID = mApp.reanimationGetID(aHammerReanim);
        }
        if (mApp.isStormyNightLevel()) {
            mChallengeState = ChallengeState.STATECHALLENGE_STORM_FLASH_1;
            mChallengeStateCounter = 400;
        }
        int aGameMode = mApp.mGameMode;
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA) {
            for (int i = 0; i < Board.MAX_GRID_SIZE_Y; i++) {
                if (mBoard.mPlantRow[i] != PlantRowType.PLANTROW_POOL) {
                    mBoard.mIceMinX[i] = 400;
                    mBoard.mIceTimer[i] = Integer.MAX_VALUE; // Challenge.cpp:203: 0x7FFFFFFF
                }
            }
        }
        if (mApp.isWallnutBowlingLevel()) {
            mBoard.mZombieCountDown = 200;
            mBoard.mZombieCountDownStart = mBoard.mZombieCountDown;
            mBoard.mSeedBank.addSeed(SeedType.SEED_WALLNUT, false);
            mConveyorBeltCounter = 400;
            mShowBowlingLine = 1;
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SHOVEL || aGameMode == GameMode.GAMEMODE_CHALLENGE_SQUIRREL) {
            shovelAddWallnuts();
        }
        if (mApp.isScaryPotterLevel()) {
            scaryPotterStart();
        }
        if (mApp.isLittleTroubleLevel() || mApp.isStormyNightLevel() || mApp.isBungeeBlitzLevel()
                || aGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
            mBoard.mZombieCountDown = 200;
            mBoard.mZombieCountDownStart = mBoard.mZombieCountDown;
            mConveyorBeltCounter = 200;
        }
        if (mApp.isSurvivalMode() && mSurvivalStage == 0) {
            String aMessage = mApp.isSurvivalNormal(aGameMode)
                    ? PvzpCommon.pvzpReplaceNumberString("[ADVICE_SURVIVE_FLAGS]", "{FLAGS}", GameConstants.SURVIVAL_NORMAL_FLAGS)
                    : mApp.isSurvivalHard(aGameMode)
                            ? PvzpCommon.pvzpReplaceNumberString("[ADVICE_SURVIVE_FLAGS]", "{FLAGS}", GameConstants.SURVIVAL_HARD_FLAGS)
                            : "[ADVICE_SURVIVE_ENDLESS]";
            mBoard.displayAdvice(aMessage, MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_SURVIVE_FLAGS);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND && mSurvivalStage == 0) {
            mBoard.displayAdvice(
                    PvzpCommon.pvzpReplaceNumberString("[ADVICE_SURVIVE_FLAGS]", "{FLAGS}", GameConstants.LAST_STAND_FLAGS),
                    MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST, AdviceType.ADVICE_SURVIVE_FLAGS);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT) {
            mBoard.displayAdvice("[ADVICE_FILL_IN_WALLNUTS]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER) {
            mBoard.displayAdvice("[ADVICE_FILL_IN_SPACES]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SEEING_STARS) {
            mBoard.displayAdvice("[ADVICE_FILL_IN_STARFRUIT]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
        }
        if (mApp.isSlotMachineLevel()) {
            mBoard.setTutorialState(TutorialState.TUTORIAL_SLOT_MACHINE_PULL);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED || aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            mBoard.mZombieCountDown = 200;
            mBoard.mZombieCountDownStart = mBoard.mZombieCountDown;
            mChallengeStateCounter = 1500;
            beghouledMakeStartBoard();
            beghouledUpdateCraters();

            if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED) {
                mBoard.displayAdvice("[ADVICE_BEGHOULED_DRAG_TO_MATCH_3]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
            } else {
                mBoard.displayAdvice("[ADVICE_BEGHOULED_TWIST_TO_MATCH_3]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
            }
        }
        if (mApp.isMiniBossLevel()) {
            mBoard.mZombieCountDown = 100;
            mBoard.mZombieCountDownStart = mBoard.mZombieCountDown;
            mConveyorBeltCounter = 200;
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            portalStart();
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            mBoard.mCurrentWave = 9;
            mBoard.mZombieCountDown = 2400;
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_AIR_RAID || aGameMode == GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA) {
            mBoard.mZombieCountDown = 4500;
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_POGO_PARTY) {
            mBoard.mZombieCountDown = 5500;
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            mBoard.displayAdvice("[ADVICE_ZOMBIQUARIUM_CLICK_TO_FEED]", MessageStyle.MESSAGE_STYLE_HINT_TALL_FAST, AdviceType.ADVICE_ZOMBIQUARIUM_CLICK_TO_FEED);
            zombiquariumSpawnSnorkle();
            zombiquariumSpawnSnorkle();
        }
        if (mApp.isIZombieLevel()) {
            iZombieStart();
        }
        if (mApp.isSquirrelLevel()) {
            squirrelStart();
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1888-1986 ({@code Challenge::Update}).
     *
     * <p>Dispatch order is upstream-exact, so an adventure level only ever reaches
     * {@link #updateConveyorBelt()} (adventure 50 is a final-boss conveyor level) and the trailing
     * challenge-reanimation refresh.
     */
    public void update() {
        if (mApp.isStormyNightLevel()) {
            updateStormyNight();
        }

        if (mBoard.mPaused) {
            if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
                mChallengeGridX = -1;
                mChallengeGridY = -1;
            }
            return;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS || mApp.isStormyNightLevel()) {
            updateRain();
        }

        if (mApp.mGameScene != GameScenes.SCENE_PLAYING && mApp.mGameMode != GameMode.GAMEMODE_TREE_OF_WISDOM) {
            return;
        }

        if (mBoard.hasConveyorBeltSeedBank()) {
            updateConveyorBelt();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            updateBeghouled();
        }
        if (mApp.isScaryPotterLevel()) {
            scaryPotterUpdate();
        }
        if ((mApp.isScaryPotterLevel() || mApp.isWhackAZombieLevel()) && mBoard.mSeedBank.mY < 0) {
            // Challenge.cpp:1921-1930: slides the seed bank into place for the two puzzle modes.
            if (mBoard.mSunMoney + mBoard.countSunBeingCollected() > 0
                    || mBoard.mSeedBank.mY > Resources.getImageById(ImageID.IMAGE_SEEDBANK).getHeight()) {
                mBoard.mSeedBank.mY += 2;
                if (mBoard.mSeedBank.mY > 0) {
                    mBoard.mSeedBank.mY = 0;
                }
            }
        }

        if (mApp.isWhackAZombieLevel()) {
            whackAZombieUpdate();
        }
        if (mApp.isIZombieLevel()) {
            iZombieUpdate();
        }
        if (mApp.isSlotMachineLevel()) {
            updateSlotMachine();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_SPEED) {
            mBoard.updateGame();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
            updateRainingSeeds();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            updatePortalCombat();
        }
        if (mApp.isSquirrelLevel()) {
            squirrelUpdate();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            zombiquariumUpdate();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            treeOfWisdomUpdate();
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE && mBoard.mMainCounter == 3000) {
            mApp.playFoley(FoleyType.FOLEY_FLOOP);
            mApp.playSample(SoundType.SOUND_LOSEMUSIC);
        }
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            lastStandUpdate();
        }

        Reanimation aReanim = mApp.reanimationTryToGet(mReanimChallenge);
        if (aReanim != null && aReanim.mIsAttachment) {
            aReanim.update();
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1988-2000 ({@code Challenge::GetArtChallengeSeed}).
     *
     * <p>Returns the {@link SeedType} the art-challenge layout expects on that square, or
     * {@link SeedType#SEED_NONE}. Rows outside the grid ({@code theGridY >= 6}) always return
     * {@code SEED_NONE}, matching upstream's early-out (the upstream assert on the grid X range is not
     * reproduced).
     */
    public int getArtChallengeSeed(int theGridX, int theGridY) {
        if (theGridY < 6) {
            int aGameMode = mApp.mGameMode;
            if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT) {
                return gArtChallengeWallnut[theGridY][theGridX];
            }
            if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_SUNFLOWER) {
                return gArtChallengeSunFlower[theGridY][theGridX];
            }
            if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SEEING_STARS) {
                return gArtChallengeStarFruit[theGridY][theGridX];
            }
        }
        return SeedType.SEED_NONE;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2002-2035 ({@code Challenge::SpawnLevelAward}).
     */
    public void spawnLevelAward(int theGridX, int theGridY) {
        if (mBoard.hasLevelAwardDropped()) {
            return;
        }

        float aPosX = mBoard.gridToPixelX(theGridX, theGridY) + 40;
        float aPosY = mBoard.gridToPixelY(theGridX, theGridY) + 40;
        int aCoinType = mApp.isFirstTimeAdventureMode() ? CoinType.COIN_FINAL_SEED_PACKET
                : mApp.isAdventureMode() || mApp.hasBeatenChallenge(mApp.mGameMode) ? CoinType.COIN_AWARD_MONEY_BAG
                        : CoinType.COIN_TROPHY;

        mBoard.mLevelAwardSpawned = true;
        mApp.mBoardResult = BoardResult.BOARDRESULT_WON;
        mApp.playFoley(FoleyType.FOLEY_SPAWN_SUN);
        Coin aCoin = mBoard.addCoin((int) aPosX, (int) aPosY, aCoinType, CoinMotion.COIN_MOTION_COIN);
        mApp.addPvzpParticle(GameConstants.BOARD_WIDTH / 2.0f, GameConstants.BOARD_HEIGHT / 2.0f,
                RenderLayer.RENDER_LAYER_TOP, ParticleEffect.PARTICLE_SCREEN_FLASH);

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            aCoin.collect();
        } else if (!mApp.isIZombieLevel()) {
            for (Zombie aZombie : mBoard.mZombies) {
                if (aZombie.mDead) {
                    continue;
                }
                if (!aZombie.isDeadOrDying()) {
                    aZombie.takeDamage(1800, 0);
                }
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2037-2058 ({@code Challenge::CheckForCompleteArtChallenge}).
     */
    public void checkForCompleteArtChallenge(int theGridX, int theGridY) {
        if (mBoard.hasLevelAwardDropped()) {
            return;
        }

        for (int aGridY = 0; aGridY < Board.MAX_GRID_SIZE_Y; aGridY++) {
            for (int aGridX = 0; aGridX < Board.MAX_GRID_SIZE_X; aGridX++) {
                int aSeedType = getArtChallengeSeed(aGridX, aGridY);
                if (aSeedType != SeedType.SEED_NONE) {
                    Plant aPlant = mBoard.getTopPlantAt(aGridX, aGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
                    if (aPlant == null || aPlant.mSeedType != aSeedType) {
                        return;
                    }
                }
            }
        }
        spawnLevelAward(theGridX, theGridY);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2182-2194 ({@code Challenge::PlantAdded}).
     */
    public void plantAdded(Plant thePlant) {
        if (mApp.isArtChallenge()) {
            int anArtSeed = getArtChallengeSeed(thePlant.mPlantCol, thePlant.mRow);
            if (anArtSeed != SeedType.SEED_NONE && anArtSeed == thePlant.mSeedType) {
                mApp.playFoley(FoleyType.FOLEY_ART_CHALLENGE);
                mApp.addPvzpParticle(thePlant.mX + 40, thePlant.mY + 40, RenderLayer.RENDER_LAYER_TOP,
                        ParticleEffect.PARTICLE_PRESENT_PICKUP);
                checkForCompleteArtChallenge(thePlant.mPlantCol, thePlant.mRow);
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2196-2246 ({@code Challenge::CanPlantAt}).
     *
     * <p>Returns a {@link PlantingReason} value. Every upstream branch is reproduced: wall-nut
     * bowling's "not passed line", the I,Zombie column limit (6 default / 4 for I,Zombie 1-5 / 5 for
     * I,Zombie 6-8 and endless) with the bungee exception, the art-challenge occupied-square rule
     * (lily pad and pumpkin shell exempt) plus the wall-nut art-challenge blocked squares, and the
     * final-boss {@code theGridX >= 8} restriction.
     */
    public int canPlantAt(int theGridX, int theGridY, int theSeedType) {
        if (mApp.isWallnutBowlingLevel()) {
            return theGridX > 2 ? PlantingReason.PLANTING_NOT_PASSED_LINE : PlantingReason.PLANTING_OK;
        } else if (mApp.isIZombieLevel()) {
            int aLimit = 6;
            if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2
                    || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4
                    || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5) {
                aLimit = 4;
            }
            if (mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7
                    || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8 || mApp.mGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS) {
                aLimit = 5;
            }

            if (theSeedType == SeedType.SEED_ZOMBIE_BUNGEE) {
                return theGridX < aLimit ? PlantingReason.PLANTING_OK : PlantingReason.PLANTING_NOT_HERE;
            } else if (isZombieSeedType(theSeedType) != 0) {
                return theGridX >= aLimit ? PlantingReason.PLANTING_OK : PlantingReason.PLANTING_NOT_HERE;
            }
        } else if (mApp.isArtChallenge()) {
            int anArtSeed = getArtChallengeSeed(theGridX, theGridY);
            if (anArtSeed != SeedType.SEED_NONE && anArtSeed != theSeedType && theSeedType != SeedType.SEED_LILYPAD
                    && theSeedType != SeedType.SEED_PUMPKINSHELL) {
                return PlantingReason.PLANTING_NOT_ON_ART;
            }
            if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ART_CHALLENGE_WALLNUT) {
                if ((theGridX == 4 || theGridX == 6) && theGridY == 1) {
                    return PlantingReason.PLANTING_NOT_HERE;
                }
            }
        } else if (mApp.isFinalBossLevel() && theGridX >= 8) {
            return PlantingReason.PLANTING_NOT_HERE;
        }

        return PlantingReason.PLANTING_OK;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2248-2279 ({@code Challenge::InitZombieWavesSurvival}).
     *
     * <p>Picks the survival stage's additional zombie types deterministically from
     * {@link Board#getLevelRandSeed()} using {@link MTRand} only (upstream {@code MTRand aLevelRNG}).
     */
    public void initZombieWavesSurvival() {
        mBoard.mZombieAllowed[ZombieType.ZOMBIE_NORMAL] = true;
        MTRand aLevelRNG = new MTRand(mBoard.getLevelRandSeed());

        if (aLevelRNG.next(5) == 0) {
            mBoard.mZombieAllowed[ZombieType.ZOMBIE_NEWSPAPER] = true;
        } else {
            mBoard.mZombieAllowed[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
        }

        int aCapacity = Math.min(mSurvivalStage + 1, 9);
        while (aCapacity > 0) {
            int aRandZombie = (int) aLevelRNG.next(ZombieType.NUM_ZOMBIE_TYPES);
            if (mBoard.mZombieAllowed[aRandZombie]) {
                continue;
            }
            if (Board.isZombieTypePoolOnly(aRandZombie) && !mBoard.stageHasPool()) {
                continue;
            }
            if (mBoard.stageHasRoof() && (aRandZombie == ZombieType.ZOMBIE_DIGGER || aRandZombie == ZombieType.ZOMBIE_DANCER)) {
                continue;
            }
            if (mBoard.stageHasGraveStones() && aRandZombie == ZombieType.ZOMBIE_ZAMBONI) {
                continue;
            }
            if (!mBoard.stageHasRoof() && !mApp.isSurvivalEndless(mApp.mGameMode) && aRandZombie == ZombieType.ZOMBIE_BUNGEE) {
                continue;
            }
            if (mBoard.getSurvivalFlagsCompleted() < 10 && aRandZombie == ZombieType.ZOMBIE_REDEYE_GARGANTUAR) {
                continue;
            }
            if (mApp.isSurvivalNormal(mApp.mGameMode) && aRandZombie > ZombieType.ZOMBIE_SNORKEL) {
                continue;
            }
            if (Board.isZombieTypeSpawnedOnly(aRandZombie) || Zombie.isZombotany(aRandZombie)
                    || aRandZombie == ZombieType.ZOMBIE_DUCKY_TUBE || aRandZombie == ZombieType.ZOMBIE_YETI) {
                continue;
            }

            mBoard.mZombieAllowed[aRandZombie] = true;
            aCapacity--;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2281-2287 ({@code Challenge::InitZombieWavesFromList}).
     *
     * @param theZombieList upstream {@code ZombieType*} - {@link ZombieType} constants
     * @param theListLength number of leading entries to mark allowed
     */
    public void initZombieWavesFromList(int[] theZombieList, int theListLength) {
        for (int i = 0; i < theListLength; i++) {
            mBoard.mZombieAllowed[theZombieList[i]] = true;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2289-2489 ({@code Challenge::InitZombieWaves}).
     *
     * <p>Fills {@link Board#mZombieAllowed} for the current mode. Every upstream mode branch is
     * reproduced verbatim (survival, speed, pogo party, bungee blitz, sunny day, portal combat, little
     * trouble, big time, raining seeds, high gravity, whack-a-zombie, bobsled bonanza, air raid,
     * beghouled/-twist, last stand, column, invisighoul, war and peas 1/2, shovel, wall-nut bowling
     * 1/2, stormy night, and the adventure/default cone+pail set), plus the trailing yeti rule.
     */
    public void initZombieWaves() {
        int aGameMode = mApp.mGameMode;
        boolean[] aList = mBoard.mZombieAllowed;
        if (mApp.isSurvivalMode()) {
            if (mSurvivalStage == 0) {
                if (mApp.isSurvivalNormal(mApp.mGameMode)) {
                    aList[ZombieType.ZOMBIE_NORMAL] = true;
                    aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
                } else {
                    aList[ZombieType.ZOMBIE_NORMAL] = true;
                    aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
                    aList[ZombieType.ZOMBIE_PAIL] = true;
                }
            } else {
                initZombieWavesSurvival();
            }
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SPEED) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_DOLPHIN_RIDER] = true;
            aList[ZombieType.ZOMBIE_POLEVAULTER] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_POGO_PARTY) {
            aList[ZombieType.ZOMBIE_POGO] = true;
        } else if (mApp.isBungeeBlitzLevel()) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_LADDER] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_POLEVAULTER] = true;
            aList[ZombieType.ZOMBIE_JACK_IN_THE_BOX] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_BALLOON] = true;
        } else if (mApp.isLittleTroubleLevel()) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_SNORKEL] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BIG_TIME) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_DOOR] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_JACK_IN_THE_BOX] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_DOOR] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_NEWSPAPER] = true;
            aList[ZombieType.ZOMBIE_JACK_IN_THE_BOX] = true;
            aList[ZombieType.ZOMBIE_BUNGEE] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_HIGH_GRAVITY) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_DOOR] = true;
            aList[ZombieType.ZOMBIE_BALLOON] = true;
        } else if (mApp.isWhackAZombieLevel()) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BOBSLED_BONANZA) {
            aList[ZombieType.ZOMBIE_BOBSLED] = true;
            aList[ZombieType.ZOMBIE_ZAMBONI] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_AIR_RAID) {
            aList[ZombieType.ZOMBIE_BALLOON] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED || aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_DOOR] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_NEWSPAPER] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_DOOR] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
            aList[ZombieType.ZOMBIE_NEWSPAPER] = true;
            aList[ZombieType.ZOMBIE_JACK_IN_THE_BOX] = true;
            aList[ZombieType.ZOMBIE_POLEVAULTER] = true;
            aList[ZombieType.ZOMBIE_DOLPHIN_RIDER] = true;
            aList[ZombieType.ZOMBIE_LADDER] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_FOOTBALL] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
            aList[ZombieType.ZOMBIE_DOLPHIN_RIDER] = true;
            aList[ZombieType.ZOMBIE_ZAMBONI] = true;
            aList[ZombieType.ZOMBIE_JACK_IN_THE_BOX] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS) {
            aList[ZombieType.ZOMBIE_PEA_HEAD] = true;
            aList[ZombieType.ZOMBIE_WALLNUT_HEAD] = true;
        } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_WAR_AND_PEAS_2) {
            aList[ZombieType.ZOMBIE_PEA_HEAD] = true;
            aList[ZombieType.ZOMBIE_WALLNUT_HEAD] = true;
            aList[ZombieType.ZOMBIE_JALAPENO_HEAD] = true;
            aList[ZombieType.ZOMBIE_GATLING_HEAD] = true;
            aList[ZombieType.ZOMBIE_SQUASH_HEAD] = true;
            aList[ZombieType.ZOMBIE_TALLNUT_HEAD] = true;
        } else if (mApp.isShovelLevel()) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
        } else if (mApp.isWallnutBowlingLevel()) {
            if (aGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING || mApp.isAdventureMode()) {
                aList[ZombieType.ZOMBIE_NORMAL] = true;
                aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
                aList[ZombieType.ZOMBIE_PAIL] = true;
                aList[ZombieType.ZOMBIE_POLEVAULTER] = true;
                aList[ZombieType.ZOMBIE_NEWSPAPER] = true;
            } else if (aGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2) {
                aList[ZombieType.ZOMBIE_NORMAL] = true;
                aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
                aList[ZombieType.ZOMBIE_PAIL] = true;
                aList[ZombieType.ZOMBIE_POLEVAULTER] = true;
                aList[ZombieType.ZOMBIE_NEWSPAPER] = true;
                aList[ZombieType.ZOMBIE_DANCER] = true;
                aList[ZombieType.ZOMBIE_DOOR] = true;
            }
        } else if (mApp.isStormyNightLevel()) {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_DOLPHIN_RIDER] = true;
            aList[ZombieType.ZOMBIE_BALLOON] = true;
        } else {
            aList[ZombieType.ZOMBIE_NORMAL] = true;
            aList[ZombieType.ZOMBIE_TRAFFIC_CONE] = true;
            aList[ZombieType.ZOMBIE_PAIL] = true;
        }

        if (mApp.canSpawnYetis() && !mApp.isWhackAZombieLevel() && !mApp.isLittleTroubleLevel()) {
            aList[ZombieType.ZOMBIE_YETI] = true;
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2534-2649 ({@code Challenge::WhackAZombieSpawning}).
     *
     * <p>Whack-a-Zombie's wave loop: raises grave stones before each wave, picks the per-phase
     * double/triple/pail/cone chances and rises the zombies out of the picked graves. Uses
     * {@link PvzpCommon#randRangeInt}/{@link PvzpCommon#randRangeFloat} (upstream {@code Rand}) and
     * {@link PvzpCommon#pvzpPickArrayItemFromWeightedArray} for the weighted grave pick.
     */
    public void whackAZombieSpawning() {
        if (mBoard.mCurrentWave == mBoard.mNumWaves && mBoard.mZombieCountDown == 0) {
            return;
        }

        mBoard.mZombieCountDown--;
        if (mBoard.mZombieCountDown == 100 && mBoard.mCurrentWave > 0) {
            int aNumGraves = 5 - mBoard.getGraveStonesCount();
            whackAZombiePlaceGraves(Math.max(1, aNumGraves));
        }
        if (mBoard.mZombieCountDown == 5) {
            mBoard.nextWaveComing();
        }
        if (mBoard.mZombieCountDown == 0) {
            mBoard.mZombieCountDown = 2000;
            mBoard.mZombieCountDownStart = mBoard.mZombieCountDown;
            if (mBoard.mCurrentWave < mBoard.mNumWaves) {
                mBoard.mCurrentWave++;
            }
            mChallengeStateCounter = mBoard.mCurrentWave == mBoard.mNumWaves ? 300 : 1;
        } else if (mBoard.mZombieCountDown < 300) {
            return;
        }

        if (--mChallengeStateCounter == 0) {
            // Challenge.cpp:2562: std::clamp((mCurrentWave - 1) * 6 / 12, 0, 5) - integer division.
            int aPhase = Math.max(0, Math.min(5, (mBoard.mCurrentWave - 1) * 6 / 12));
            // Per-phase weights for cone/pail zombies and for 2- or 3-zombie groups (Challenge.cpp:2563-2566)
            final int[] aDoubleChance = { 0, 30, 10, 10, 15, 18 };
            final int[] aTripleChance = { 0, 0, 0, 0, 10, 13 };
            final int[] aPailChance = { 0, 0, 0, 10, 15, 15 };
            final int[] aConeChance = { 0, 0, 30, 30, 30, 30 };
            int aZombieCount = 1;
            int aZombieType = ZombieType.ZOMBIE_NORMAL;
            int aNumHit = PvzpCommon.randRangeInt(0, 99);
            int aTypeHit = PvzpCommon.randRangeInt(0, 99);
            boolean aIsFinalWave = mBoard.mCurrentWave == mBoard.mNumWaves;

            if (aIsFinalWave) {
                aZombieCount = 20;
            } else if (aNumHit < aTripleChance[aPhase]) {
                aZombieCount = 3;
            } else if (aNumHit < aTripleChance[aPhase] + aDoubleChance[aPhase]) {
                aZombieCount = 2;
            }

            if (aTypeHit < aPailChance[aPhase] && aZombieCount < 3) {
                aZombieType = ZombieType.ZOMBIE_PAIL;
            } else if (aTypeHit < aPailChance[aPhase] + aConeChance[aPhase]) {
                aZombieType = ZombieType.ZOMBIE_TRAFFIC_CONE;
            }

            PvzpWeightedArray[] aGridPicks = new PvzpWeightedArray[Board.MAX_GRAVE_STONES];
            int aGridPicksCount = 0;

            for (GridItem aGridItem : mBoard.mGridItems) {
                if (aGridItem.mDead) {
                    continue;
                }
                if (aGridItem.mGridItemType == GridItemType.GRIDITEM_GRAVESTONE) {
                    Plant aPlant = mBoard.getTopPlantAt(aGridItem.mGridX, aGridItem.mGridY, PlantPriority.TOPPLANT_ONLY_NORMAL_POSITION);
                    if (aPlant == null || aPlant.mSeedType != SeedType.SEED_GRAVEBUSTER) {
                        // Upstream stores the raw GridItem* in the weighted entry; the port stores the
                        // DataArray key of the grid item instead and resolves it with dataArrayGet.
                        aGridPicks[aGridPicksCount] = new PvzpWeightedArray(mBoard.mGridItems.dataArrayGetID(aGridItem), 1);
                        aGridPicksCount++;
                    }
                }
            }
            float aMaxSpeed = PvzpCommon.pvzpAnimateCurveFloat(1, 12, mBoard.mCurrentWave, 1.0f, 3.0f, PvzpCurves.CURVE_EASE_IN);
            aZombieCount = Math.min(aZombieCount, aGridPicksCount);

            for (int i = 0; i < aZombieCount; i++) {
                PvzpWeightedArray aGrid = PvzpCommon.pvzpPickArrayItemFromWeightedArray(aGridPicks, aGridPicksCount);
                GridItem aGraveStone = mBoard.mGridItems.dataArrayGet((int) aGrid.mItem);
                aGrid.mWeight = 0;

                if (aIsFinalWave) {
                    aZombieType = PvzpCommon.randRangeInt(0, 1) == 0 ? ZombieType.ZOMBIE_TRAFFIC_CONE : ZombieType.ZOMBIE_PAIL;
                    aMaxSpeed = 2;
                }

                Zombie aZombie = mBoard.addZombie(aZombieType, mBoard.mCurrentWave);
                if (aZombie == null) {
                    break;
                }

                aZombie.riseFromGrave(aGraveStone.mGridX, aGraveStone.mGridY);
                aZombie.mPhaseCounter = 50;
                aZombie.mVelX = PvzpCommon.randRangeFloat(0.5f, aMaxSpeed);
                aZombie.updateAnimSpeed();
            }

            int aStateCounterMin = PvzpCommon.pvzpAnimateCurve(1, 12, mBoard.mCurrentWave, 100, 30, PvzpCurves.CURVE_LINEAR);
            int aStateCounterMax = PvzpCommon.pvzpAnimateCurve(1, 12, mBoard.mCurrentWave, 200, 60, PvzpCurves.CURVE_LINEAR);
            mChallengeStateCounter = PvzpCommon.randRangeInt(aStateCounterMin, aStateCounterMax);
            if (aIsFinalWave) {
                mBoard.mZombieCountDown = 0;
                mChallengeStateCounter = 0;
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2651-2669 ({@code Challenge::UpdateZombieSpawning}).
     *
     * <p>Returns true when the caller (Board) must suppress its normal spawning for this tick.
     */
    public boolean updateZombieSpawning() {
        if (mApp.isWhackAZombieLevel()) {
            whackAZombieSpawning();
            // Upstream comment: need to return truthy to prevent normal spawning.
            return true;
        }
        return mApp.isFinalBossLevel()
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ICE
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN
                || mApp.mGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM
                || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM
                || mApp.isIZombieLevel()
                || mApp.isSquirrelLevel()
                || mApp.isScaryPotterLevel()
                || (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_LAST_STAND
                        && mChallengeState != ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2671-2689 ({@code Challenge::GraveDangerSpawnGraveAt}).
     */
    public void graveDangerSpawnGraveAt(int theGridX, int theGridY) {
        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mPlantCol == theGridX && aPlant.mRow == theGridY) {
                aPlant.die();
            }
        }

        mBoard.mEnableGraveStones = true;
        GridItem aGraveStone = mBoard.addAGraveStone(theGridX, theGridY);
        if (aGraveStone != null) {
            aGraveStone.addGraveStoneParticles();
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2691-2716 ({@code Challenge::GraveDangerSpawnRandomGrave}).
     *
     * <p>Weighted grave placement: an occupied square weighs 1, an empty one 100000 (upstream comment
     * cites the Chinese PvZ wiki's gravestone strategy page).
     */
    public void graveDangerSpawnRandomGrave() {
        int aPickCount = 0;
        PvzpWeightedGridArray[] aPicks = new PvzpWeightedGridArray[30];

        for (int aCol = 4; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y; aRow++) {
                if (mBoard.canAddGraveStoneAt(aCol, aRow)) {
                    PvzpWeightedGridArray aPick = new PvzpWeightedGridArray();
                    aPick.mWeight = mBoard.getTopPlantAt(aCol, aRow, PlantPriority.TOPPLANT_ANY) != null ? 1 : 100000;
                    aPick.mX = aCol;
                    aPick.mY = aRow;
                    aPicks[aPickCount] = aPick;
                    aPickCount++;
                }
            }
        }

        if (aPickCount != 0) {
            PvzpWeightedGridArray aPick = PvzpCommon.pvzpPickFromWeightedGridArray(aPicks, aPickCount);
            graveDangerSpawnGraveAt(aPick.mX, aPick.mY);
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2718-2762 ({@code Challenge::SpawnZombieWave}).
     *
     * <p>Continuous challenges reuse the last wave (flag zombies in it become normal); Grave Danger
     * raises graves on its flag waves and after wave 5; the survival-night stage-1-4 graves rule and
     * the bungee-blitz advice are reproduced with their upstream conditions.
     */
    public void spawnZombieWave() {
        if (mApp.isContinuousChallenge() && mBoard.mCurrentWave == mBoard.mNumWaves) {
            mBoard.mCurrentWave = mBoard.mNumWaves - 1;
            for (int i = 0; i < Board.MAX_ZOMBIES_IN_WAVE; i++) {
                int aWaveZombie = mBoard.mZombiesInWave[mBoard.mCurrentWave][i];
                if (aWaveZombie == ZombieType.ZOMBIE_INVALID) {
                    break;
                }
                if (aWaveZombie == ZombieType.ZOMBIE_FLAG) {
                    mBoard.mZombiesInWave[mBoard.mCurrentWave][i] = ZombieType.ZOMBIE_NORMAL;
                }
            }
        }

        boolean aIsFlagWave = mBoard.isFlagWave(mBoard.mCurrentWave);
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER && mBoard.mCurrentWave != mBoard.mNumWaves - 1) {
            if (aIsFlagWave) {
                mBoard.spawnZombiesFromGraves();
            } else if (mBoard.mCurrentWave > 5) {
                graveDangerSpawnRandomGrave();
            }
        }
        if (mApp.isSurvivalMode() && mBoard.mBackground == BackgroundType.BACKGROUND_2_NIGHT
                && mBoard.mCurrentWave == mBoard.mNumWaves - 1) {
            int aNumGraves = mBoard.getGraveStonesCount();
            int aGraveLimit = mApp.isSurvivalNormal(mApp.mGameMode) ? 8 : 12;
            if (aNumGraves < aGraveLimit) {
                graveDangerSpawnRandomGrave();
            }
        }
        if (mApp.isBungeeBlitzLevel() && aIsFlagWave) {
            mBoard.displayAdvice("[ADVICE_BUNGEES_INCOMING]", MessageStyle.MESSAGE_STYLE_HINT_FAST, AdviceType.ADVICE_NONE);
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:993-1011 ({@code Challenge::AdvanceCrazyDaveDialog}).
     *
     * <p>Advances the Scary Potter / I,Zombie Dave dialog; when he hands over more vases (message
     * index 2702 / 2801) the pot population is rebuilt via {@link ChallengeScaryPotter}.
     */
    public void advanceCrazyDaveDialog() {
        if (!mBoard.isScaryPotterDaveTalking() || mApp.mCrazyDaveMessageIndex == -1) {
            return;
        }

        if (!mApp.advanceCrazyDaveText()) {
            mApp.crazyDaveLeave();
            return;
        }

        // "Here, I'll give you more vases." || "This should be their last wave."
        if (mApp.mCrazyDaveMessageIndex == 2702 || mApp.mCrazyDaveMessageIndex == 2801) {
            scaryPotterPopulate();
            mApp.playFoley(FoleyType.FOLEY_PLANT);
            mBoard.placeRake();
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3096-3106 ({@code Challenge::PortalCombatRowSpawnWeight}).
     *
     * <p>Portal Combat row weight used by the zombie row picker (BoardWaves).
     */
    public float portalCombatRowSpawnWeight(int theGridY) {
        if (getPortalDistanceToMower(theGridY) < 5) {
            return 0.01f;
        }

        for (GridItem aGridItem : mBoard.mGridItems) {
            if (!aGridItem.mDead && aGridItem.isOpenPortal() && aGridItem.mGridY == theGridY) {
                return 1.0f;
            }
        }

        return 0.2f;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3128-3152 ({@code Challenge::GetPortalDistanceToMower}).
     *
     * <p>Follows the portal chain leftwards from column 10 to the house; upstream asserts the paired
     * portal exists, which is reproduced as an {@link IllegalStateException}.
     */
    public int getPortalDistanceToMower(int theGridY) {
        int aGridX = 10;
        int aGridY = theGridY;
        int aDistance = 0;

        while (aDistance < 40) {
            GridItem aPortal = getPortalToLeft(aGridX, aGridY);
            if (aPortal == null) {
                aDistance += aGridX;
                break;
            }

            GridItem aOtherPortal = getOtherPortal(aPortal);
            if (aOtherPortal == null) {
                throw new IllegalStateException("Challenge.getPortalDistanceToMower: portal without a pair");
            }

            aDistance += aGridX - aPortal.mGridX;
            aGridX = aOtherPortal.mGridX;
            aGridY = aOtherPortal.mGridY;
        }

        return aDistance;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3108-3126 ({@code Challenge::GetPortalToLeft}): the right-most
     * open portal left of {@code theGridX} on the same row, or null.
     */
    public GridItem getPortalToLeft(int theGridX, int theGridY) {
        GridItem aGridItemRecord = null;

        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.isOpenPortal() && aGridItem.mGridX < theGridX && aGridItem.mGridY == theGridY) {
                if (aGridItemRecord == null || aGridItemRecord.mGridX < aGridItem.mGridX) {
                    aGridItemRecord = aGridItem;
                }
            }
        }

        return aGridItemRecord;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3154-3173 ({@code Challenge::GetPortalToRight}): the left-most
     * open portal right of {@code theGridX} on the same row, or null.
     */
    public GridItem getPortalToRight(int theGridX, int theGridY) {
        GridItem aGridItemRecord = null;

        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem.isOpenPortal() && aGridItem.mGridX > theGridX && aGridItem.mGridY == theGridY) {
                if (aGridItemRecord == null || aGridItemRecord.mGridX > aGridItem.mGridX) {
                    aGridItemRecord = aGridItem;
                }
            }
        }

        return aGridItemRecord;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3175-3204 ({@code Challenge::GetPortalLeftRight}); upstream
     * takes {@code int theToLeft} (its default is {@code true}). A portal in the same column is
     * skipped, the direction test is the upstream XOR ({@code aIsDir}) and the closest portal on that
     * side wins. Upstream reads {@code aGridItemRecord->mGridX} before its null check (undefined
     * behaviour on the first candidate); the port evaluates the null case first, which yields the same
     * assignment.
     */
    public GridItem getPortalLeftRight(int theGridX, int theGridY, int theToLeft) {
        GridItem aGridItemRecord = null;

        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            int aPortalX = aGridItem.mGridX;
            if (aPortalX == theGridX) {
                continue; // skip portals in the same column as theGridX
            }

            boolean aIsDir = (aPortalX > theGridX) ^ (theToLeft != 0);
            if (aGridItem.isOpenPortal() && aIsDir && aGridItem.mGridY == theGridY) {
                boolean aIsCls = aGridItemRecord == null || ((aGridItemRecord.mGridX > aPortalX) ^ (theToLeft != 0));
                if (aGridItemRecord == null || aIsCls) {
                    aGridItemRecord = aGridItem;
                }
            }
        }

        return aGridItemRecord;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2986-2999 ({@code Challenge::GetOtherPortal}): the first live
     * grid item that is not {@code thePortal} itself and shares its grid-item type.
     */
    public GridItem getOtherPortal(GridItem thePortal) {
        for (GridItem aGridItem : mBoard.mGridItems) {
            if (aGridItem.mDead) {
                continue;
            }
            if (aGridItem != thePortal && aGridItem.mGridItemType == thePortal.mGridItemType) {
                return aGridItem;
            }
        }
        return null;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1378-1679 ({@code Challenge::UpdateConveyorBelt}).
     *
     * <p>The conveyor-belt seed picker (adventure 50's final boss, mini-boss levels, shovel, wall-nut
     * bowling, little trouble, stormy night, bungee blitz, portal combat, column and invisighoul). The
     * per-level and per-mode weight tables are verbatim; the upstream {@code PVZP_ASSERT(false)}
     * fallback becomes an {@link IllegalStateException} (upstream {@code PVZP_ASSERT(false)}).
     */
    public void updateConveyorBelt() {
        if (mBoard.hasLevelAwardDropped()) {
            return;
        }

        mBoard.mSeedBank.updateConveyorBelt();
        mConveyorBeltCounter--;
        if (mConveyorBeltCounter > 0) {
            return;
        }

        float aConveyorSpeedMultiplier = 1;
        if (mApp.isFinalBossLevel()) {
            aConveyorSpeedMultiplier = 0.875f;
        } else if (mApp.isShovelLevel() || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            aConveyorSpeedMultiplier = 1.5f;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
            aConveyorSpeedMultiplier = 2.0f;
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            aConveyorSpeedMultiplier = 3.0f;
        }
        int aNumSeedsOnConveyor = mBoard.mSeedBank.getNumSeedsOnConveyorBelt();
        mConveyorBeltCounter = (int) (aConveyorSpeedMultiplier
                * (aNumSeedsOnConveyor > 8 ? 1000 : aNumSeedsOnConveyor > 6 ? 500 : aNumSeedsOnConveyor > 4 ? 425 : 400));

        PvzpWeightedArray[] aSeedPickArray = new PvzpWeightedArray[20];
        int aSeedPickCount = 0;
        if (mBoard.mLevel == 10) {
            aSeedPickCount = 7;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_PEASHOOTER, 20);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_CHERRYBOMB, 20);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_WALLNUT, 15);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_REPEATER, 20);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_SNOWPEA, 10);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_CHOMPER, 5);
            aSeedPickArray[6] = new PvzpWeightedArray(SeedType.SEED_POTATOMINE, 10);
        } else if (mBoard.mLevel == 20) {
            aSeedPickCount = 7;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_GRAVEBUSTER, 20);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_ICESHROOM, 15);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_DOOMSHROOM, 15);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_HYPNOSHROOM, 10);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_SCAREDYSHROOM, 15);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_FUMESHROOM, 15);
            aSeedPickArray[6] = new PvzpWeightedArray(SeedType.SEED_PUFFSHROOM, 10);
        } else if (mBoard.mLevel == 30) {
            aSeedPickCount = 8;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_LILYPAD, 25);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_SQUASH, 5);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_THREEPEATER, 25);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_TANGLEKELP, 5);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_JALAPENO, 10);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_SPIKEWEED, 10);
            aSeedPickArray[6] = new PvzpWeightedArray(SeedType.SEED_TORCHWOOD, 10);
            aSeedPickArray[7] = new PvzpWeightedArray(SeedType.SEED_TALLNUT, 10);
        } else if (mBoard.mLevel == 40) {
            aSeedPickCount = 8;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_LILYPAD, 25);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_SEASHROOM, 10);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_MAGNETSHROOM, 5);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_BLOVER, 5);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_CACTUS, 15);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_STARFRUIT, 25);
            aSeedPickArray[6] = new PvzpWeightedArray(SeedType.SEED_SPLITPEA, 5);
            aSeedPickArray[7] = new PvzpWeightedArray(SeedType.SEED_PUMPKINSHELL, 10);
        } else if (mApp.isFinalBossLevel()) {
            aSeedPickCount = 6;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_FLOWERPOT, 55);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_MELONPULT, 10);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_JALAPENO, 12);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_CABBAGEPULT, 10);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_KERNELPULT, 5);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_ICESHROOM, 8);
        } else if (mApp.isShovelLevel()) {
            aSeedPickCount = 1;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_PEASHOOTER, 100);
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_WALLNUT_BOWLING_2) {
            aSeedPickCount = 3;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_WALLNUT, 85);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_EXPLODE_O_NUT, 15);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_GIANT_WALLNUT, 15);
        } else if (mApp.isWallnutBowlingLevel()) {
            aSeedPickCount = 2;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_WALLNUT, 85);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_EXPLODE_O_NUT, 15);
        } else if (mApp.isLittleTroubleLevel()) {
            aSeedPickCount = 4;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_LILYPAD, 25);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_WALLNUT, 15);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_PEASHOOTER, 25);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_CHERRYBOMB, 35);
        } else if (mApp.isStormyNightLevel()) {
            aSeedPickCount = 5;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_LILYPAD, 30);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_CACTUS, 10);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_PEASHOOTER, 20);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_PUFFSHROOM, 15);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_CHERRYBOMB, 25);
        } else if (mApp.isBungeeBlitzLevel()) {
            aSeedPickCount = 4;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_FLOWERPOT, 50);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_CHOMPER, 25);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_PUMPKINSHELL, 15);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_CHERRYBOMB, 10);
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT) {
            aSeedPickCount = 6;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_PEASHOOTER, 25);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_REPEATER, 20);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_TORCHWOOD, 10);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_CACTUS, 15);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_WALLNUT, 15);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_CHERRYBOMB, 15);
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN) {
            aSeedPickCount = 6;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_FLOWERPOT, 155);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_MELONPULT, 5);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_CHOMPER, 5);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_PUMPKINSHELL, 15);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_JALAPENO, 10);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_SQUASH, 10);
        } else if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL) {
            aSeedPickCount = 6;
            aSeedPickArray[0] = new PvzpWeightedArray(SeedType.SEED_PEASHOOTER, 25);
            aSeedPickArray[1] = new PvzpWeightedArray(SeedType.SEED_WALLNUT, 15);
            aSeedPickArray[2] = new PvzpWeightedArray(SeedType.SEED_KERNELPULT, 5);
            aSeedPickArray[3] = new PvzpWeightedArray(SeedType.SEED_SQUASH, 15);
            aSeedPickArray[4] = new PvzpWeightedArray(SeedType.SEED_LILYPAD, 30);
            aSeedPickArray[5] = new PvzpWeightedArray(SeedType.SEED_ICESHROOM, 10);
        } else {
            // Challenge.cpp:1679: PVZP_ASSERT(false) - unreachable for any conveyor-belt mode.
            throw new IllegalStateException("Challenge.updateConveyorBelt: no conveyor-belt seed table "
                    + "for this mode");
        }

        for (int i = 0; i < aSeedPickCount; i++) {
            PvzpWeightedArray aSeedPick = aSeedPickArray[i];
            int aSeedType = (int) aSeedPick.mItem;
            int aCountInBank = mBoard.mSeedBank.countOfTypeOnConveyorBelt(aSeedType);
            int aTotalCount = mBoard.countPlantByType(aSeedType) + aCountInBank;

            if (aSeedType == SeedType.SEED_GRAVEBUSTER) {
                if (mBoard.getGraveStonesCount() <= aTotalCount) {
                    aSeedPick.mWeight = 0;
                    continue;
                }
            } else if (aSeedType == SeedType.SEED_LILYPAD) {
                aSeedPick.mWeight = PvzpCommon.pvzpAnimateCurve(0, 18, aTotalCount, aSeedPick.mWeight, 1, PvzpCurves.CURVE_LINEAR);
            } else if (aSeedType == SeedType.SEED_FLOWERPOT) {
                aSeedPick.mWeight = PvzpCommon.pvzpAnimateCurve(0,
                        mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_COLUMN ? 45 : 35, aTotalCount, aSeedPick.mWeight, 1,
                        PvzpCurves.CURVE_LINEAR);
            }

            if (mApp.isFinalBossLevel()) {
                if (aSeedType == SeedType.SEED_MELONPULT || aSeedType == SeedType.SEED_KERNELPULT
                        || aSeedType == SeedType.SEED_CABBAGEPULT) {
                    int aEmptyPots = mBoard.countEmptyPotsOrLilies(SeedType.SEED_FLOWERPOT);
                    if (aEmptyPots <= 2) {
                        aSeedPick.mWeight /= 5;
                    } else if (aEmptyPots <= 5) {
                        aSeedPick.mWeight /= 3;
                    }
                }

                if (aSeedType == SeedType.SEED_FLOWERPOT
                        && mBoard.getBossZombie().mZombiePhase == ZombiePhase.PHASE_BOSS_DROP_RV) {
                    aSeedPick.mWeight = 500;
                }
            }

            if (aSeedPickCount > 2) {
                if (aCountInBank >= 4) {
                    aSeedPick.mWeight = 1;
                } else if (aCountInBank >= 3) {
                    aSeedPick.mWeight = 5;
                } else if (aSeedType == mLastConveyorSeedType) {
                    aSeedPick.mWeight /= 2;
                }
            }
        }

        int aSeedType = (int) PvzpCommon.pvzpPickFromWeightedArray(aSeedPickArray, aSeedPickCount);
        mBoard.mSeedBank.addSeed(aSeedType, false);
        mLastConveyorSeedType = aSeedType;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2865-2868 ({@code Challenge::PlayBossEnter}).
     */
    public void playBossEnter() {
        mBoard.addZombie(ZombieType.ZOMBIE_BOSS, 0);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4471-4491 ({@code Challenge::IsZombieSeedType}).
     *
     * <p>Upstream returns {@code int} (a bool-as-int); the same 17 zombie seed types are recognised.
     */
    public static int isZombieSeedType(int theSeedType) {
        return (theSeedType == SeedType.SEED_ZOMBIQUARIUM_SNORKLE
                || theSeedType == SeedType.SEED_ZOMBIQUARIUM_TROPHY
                || theSeedType == SeedType.SEED_ZOMBIE_NORMAL
                || theSeedType == SeedType.SEED_ZOMBIE_TRAFFIC_CONE
                || theSeedType == SeedType.SEED_ZOMBIE_POLEVAULTER
                || theSeedType == SeedType.SEED_ZOMBIE_PAIL
                || theSeedType == SeedType.SEED_ZOMBIE_LADDER
                || theSeedType == SeedType.SEED_ZOMBIE_DIGGER
                || theSeedType == SeedType.SEED_ZOMBIE_BUNGEE
                || theSeedType == SeedType.SEED_ZOMBIE_FOOTBALL
                || theSeedType == SeedType.SEED_ZOMBIE_BALLOON
                || theSeedType == SeedType.SEED_ZOMBIE_SCREEN_DOOR
                || theSeedType == SeedType.SEED_ZOMBONI
                || theSeedType == SeedType.SEED_ZOMBIE_POGO
                || theSeedType == SeedType.SEED_ZOMBIE_DANCER
                || theSeedType == SeedType.SEED_ZOMBIE_GARGANTUAR
                || theSeedType == SeedType.SEED_ZOMBIE_IMP) ? 1 : 0;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4001-4024 ({@code Challenge::IZombieSeedTypeToZombieType}).
     *
     * <p>Upstream hits {@code PVZP_ASSERT(false); unreachable();} for a non-zombie seed; the port
     * throws {@link IllegalArgumentException} with the same condition.
     */
    public static int iZombieSeedTypeToZombieType(int theSeedType) {
        switch (theSeedType) {
        case SeedType.SEED_ZOMBIE_NORMAL:
            return ZombieType.ZOMBIE_NORMAL;
        case SeedType.SEED_ZOMBIE_TRAFFIC_CONE:
            return ZombieType.ZOMBIE_TRAFFIC_CONE;
        case SeedType.SEED_ZOMBIE_POLEVAULTER:
            return ZombieType.ZOMBIE_POLEVAULTER;
        case SeedType.SEED_ZOMBIE_PAIL:
            return ZombieType.ZOMBIE_PAIL;
        case SeedType.SEED_ZOMBIE_LADDER:
            return ZombieType.ZOMBIE_LADDER;
        case SeedType.SEED_ZOMBIE_DIGGER:
            return ZombieType.ZOMBIE_DIGGER;
        case SeedType.SEED_ZOMBIE_BUNGEE:
            return ZombieType.ZOMBIE_BUNGEE;
        case SeedType.SEED_ZOMBIE_FOOTBALL:
            return ZombieType.ZOMBIE_FOOTBALL;
        case SeedType.SEED_ZOMBIE_BALLOON:
            return ZombieType.ZOMBIE_BALLOON;
        case SeedType.SEED_ZOMBIE_SCREEN_DOOR:
            return ZombieType.ZOMBIE_DOOR;
        case SeedType.SEED_ZOMBONI:
            return ZombieType.ZOMBIE_ZAMBONI;
        case SeedType.SEED_ZOMBIE_POGO:
            return ZombieType.ZOMBIE_POGO;
        case SeedType.SEED_ZOMBIE_DANCER:
            return ZombieType.ZOMBIE_DANCER;
        case SeedType.SEED_ZOMBIE_GARGANTUAR:
            return ZombieType.ZOMBIE_GARGANTUAR;
        case SeedType.SEED_ZOMBIE_IMP:
            return ZombieType.ZOMBIE_IMP;
        default:
            // Challenge.cpp:4021: PVZP_ASSERT(false); then unreachable().
            throw new IllegalArgumentException(
                    "Challenge.iZombieSeedTypeToZombieType: seed type " + theSeedType + " is not an I,Zombie zombie seed");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mouse / cursor dispatch - Challenge.cpp:891-1131
    // ---------------------------------------------------------------------------------------------

    /** Ported from src/Lawn/Challenge.h:102 ({@code BeghouledDragCancel} inline body). */
    public void beghouledDragCancel() {
        mBeghouledMouseCapture = 0;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:891-914 ({@code Challenge::MouseMove}).
     *
     * <p>Beghouled's drag/hover handling is the only mode-specific part; outside Beghouled this only
     * revives a fading Zen Garden, exactly like upstream.
     */
    public int mouseMove(int x, int y) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED && !mBoard.hasLevelAwardDropped()) {
            if (mBeghouledMouseCapture != 0) {
                beghouledDragUpdate(x, y);
                return 1;
            }

            HitResult aHitResult = new HitResult();
            mBoard.mouseHitTest(x, y, aHitResult);
            if (aHitResult.mObjectType == GameObjectType.OBJECT_TYPE_PLANT) {
                return 1;
            }
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN && mApp.mActive) {
            if (mChallengeState == ChallengeState.STATECHALLENGE_ZEN_FADING) {
                mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
            }
            mChallengeStateCounter = 3000;
        }
        return 0;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:916-943 ({@code Challenge::UpdateToolTip}).
     *
     * <p>Only the slot-machine handle produces a tooltip upstream; the body lives in
     * {@link ChallengeSlotMachine#updateToolTip(int, int, HitResult)}.
     */
    public int updateToolTip(int theX, int theY, HitResult theHitResult) {
        return mSlotMachine.updateToolTip(theX, theY, theHitResult);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1013-1098 ({@code Challenge::MouseDown}).
     *
     * <p>Mode dispatch. The Dave-dialog advance, the Beghouled normal-state guard and the
     * whack-a-zombie hammer branch keep the upstream control flow; the Zen Garden, slot machine,
     * zombiquarium and Scary Potter interactions route to their throwing bodies.
     */
    public int mouseDown(int x, int y, int theClickCount, HitResult theHitResult) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            // Challenge.cpp:1015-1018
            return mApp.mZenGarden.mouseDownZenGarden(x, y, theClickCount, theHitResult) ? 1 : 0;
        }

        if (mApp.mGameScene != GameScenes.SCENE_PLAYING) {
            return 0;
        }

        if (mBoard.isScaryPotterDaveTalking() && mApp.mCrazyDaveMessageIndex != -1) {
            advanceCrazyDaveDialog();
            return 1;
        }

        if (theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_COIN && theClickCount >= 0) {
            return 0;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED) {
            if (mChallengeState != ChallengeState.STATECHALLENGE_NORMAL) {
                return 0;
            }

            if (theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_PLANT) {
                beghouledDragStart(x, y);
                return 1;
            }
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            if (mChallengeState != ChallengeState.STATECHALLENGE_NORMAL) {
                return 0;
            }

            beghouledTwistMouseDown(x, y);
        }

        if (mApp.isSlotMachineLevel() && theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_SLOT_MACHINE_HANDLE
                && mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_NORMAL
                && mChallengeState == ChallengeState.STATECHALLENGE_NORMAL) {
            // Challenge.cpp:1047-1066
            mSlotMachine.mouseDownPull();
            return 1;
        }

        if (mApp.isWhackAZombieLevel() && theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_NONE
                && mBoard.mCursorObject.mCursorType == CursorType.CURSOR_TYPE_HAMMER && theClickCount >= 0) {
            mouseDownWhackAZombie(x, y);
            return 1;
        }

        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_ZOMBIQUARIUM) {
            if (theClickCount > 0) {
                zombiquariumMouseDown(x, y);
                return 0;
            } else {
                mApp.playSample(SoundType.SOUND_TAPGLASS);
                return 1;
            }
        }

        if (mApp.isScaryPotterLevel() && theHitResult.mObjectType == GameObjectType.OBJECT_TYPE_SCARY_POT) {
            scaryPotterMalletPot((GridItem) theHitResult.mObject);
            return 1;
        }

        return 0;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1100-1103 ({@code Challenge::SlotMachineGetHandleRect}).
     */
    public Rect slotMachineGetHandleRect() {
        return new Rect(mBoard.mSeedBank.mX + 473, mBoard.mSeedBank.mY, 55, 80);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1105-1118 ({@code Challenge::MouseUp}).
     */
    public int mouseUp(int x, int y) {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED) {
            // Upstream: !mBoard->mAdvice->IsBeingDisplayed() (Challenge.cpp:1109); the port's
            // MessageWidget has not grown the accessor yet (upstream MessageWidget.h:65 is
            // `mDuration != 0`), so the equivalent public-field test is inlined here.
            if (mBeghouledMouseCapture != 0 && mBoard.mAdvice.mDuration == 0 && mChallengeScore == 0) {
                mBoard.displayAdvice("[ADVICE_BEGHOULED_DRAG_TO_MATCH_3]", MessageStyle.MESSAGE_STYLE_HINT_FAST,
                        AdviceType.ADVICE_BEGHOULED_DRAG_TO_MATCH_3);
            }

            beghouledDragCancel();
        }

        return 0;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:1120-1131 ({@code Challenge::ClearCursor}).
     */
    public void clearCursor() {
        if (mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED) {
            beghouledDragCancel();
        }

        if (mApp.isWhackAZombieLevel() && !mBoard.hasLevelAwardDropped()) {
            mBoard.mCursorObject.mCursorType = CursorType.CURSOR_TYPE_HAMMER;
        }
    }

    /**
     * Ported from src/Lawn/Board.cpp:1278-1286 ({@code Board::GetShovelButtonRect}).
     *
     * <p>Board delegates the source rect to the Challenge (upstream {@code Board::GetShovelButtonRect}
     * lives in Board.cpp but is pure Challenge seed-bank geometry); {@code Board.getShovelButtonRect()}
     * then replaces the width/height with the IMAGE_SHOVELBANK size. Returns the upstream
     * {@code (GetSeedBankExtraWidth() + 456, 0)} origin, {@code x = 600} for slot-machine and squirrel
     * levels.
     */
    public Rect getShovelButtonRect() {
        Rect aRect = new Rect(mBoard.getSeedBankExtraWidth() + 456, 0, 0, 0);
        if (mApp.isSlotMachineLevel() || mApp.isSquirrelLevel()) {
            aRect.mX = 600;
        }
        return aRect;
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2137-2180 ({@code Challenge::DrawBackdrop}).
     *
     * <p>Draws only the mode-independent backdrop stripes (wall-nut bowling line, the I,Zombie
     * brain-row stripes) and routes every mode-specific painter to its throwing body.
     */
    public void drawBackdrop(Graphics g) {
        int aGameMode = mApp.mGameMode;
        if (mApp.isArtChallenge()) {
            drawArtChallenge(g);
        }
        if (aGameMode == GameMode.GAMEMODE_TREE_OF_WISDOM) {
            treeOfWisdomDraw(g);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED || aGameMode == GameMode.GAMEMODE_CHALLENGE_BEGHOULED_TWIST) {
            drawBeghouled(g);
        }

        if (mApp.isWallnutBowlingLevel() && mShowBowlingLine != 0) {
            g.drawImage(mApp.getImage(Resources.IMAGE_WALLNUT_BOWLINGSTRIPE_ID), 268, 77);
        }
        if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_1 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_2
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_3 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_4
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_5) {
            g.drawImage(mApp.getImage(Resources.IMAGE_WALLNUT_BOWLINGSTRIPE_ID), 352, 73);
        }
        if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_6 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_7
                || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_8 || aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_ENDLESS) {
            g.drawImage(mApp.getImage(Resources.IMAGE_WALLNUT_BOWLINGSTRIPE_ID), 432, 73);
        }
        if (aGameMode == GameMode.GAMEMODE_PUZZLE_I_ZOMBIE_9) {
            g.drawImage(mApp.getImage(Resources.IMAGE_WALLNUT_BOWLINGSTRIPE_ID), 512, 73);
        }

        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_SLOT_MACHINE) {
            drawSlotMachine(g);
        }
        if (aGameMode == GameMode.GAMEMODE_CHALLENGE_ZEN_GARDEN) {
            // Challenge.cpp:2177-2179
            mApp.mZenGarden.drawBackdrop(g);
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:2775-2782 ({@code Challenge::DrawWeather}).
     *
     * <p>Adventure and survival never match either condition, so this is a no-op for them.
     */
    public void drawWeather(Graphics g) {
        if (mApp.isStormyNightLevel() || mApp.mGameMode == GameMode.GAMEMODE_CHALLENGE_RAINING_SEEDS) {
            drawRain(g);
        }

        if (mApp.isStormyNightLevel()) {
            drawStormNight(g);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mini-game helper state (this port splits the upstream Challenge.cpp bodies per mode)
    // ---------------------------------------------------------------------------------------------

    /** Beghouled / Beghouled Twist (Challenge.cpp:324-1376, 1854-1886, 2085-2118, 3235-3305). */
    public final ChallengeBeghouled mBeghouled = new ChallengeBeghouled(this);

    /** Scary Potter / vase-breaker + art-challenge overlay (Challenge.cpp:2060-2083, 3456-3980). */
    public final ChallengeScaryPotter mScaryPotter = new ChallengeScaryPotter(this);

    /** I,Zombie puzzle (Challenge.cpp:4001-4622). */
    public final ChallengeIZombie mIZombie = new ChallengeIZombie(this);

    /** Raining Seeds / Stormy Night weather (Challenge.cpp:1681-1760, 2764-2863, 4825-4853). */
    public final ChallengeWeather mWeather = new ChallengeWeather(this);

    /** Portal Combat (Challenge.cpp:2870-3094, 3204-3233). */
    public final ChallengePortalCombat mPortalCombat = new ChallengePortalCombat(this);

    /** Slot Machine (Challenge.cpp:916-943, 1047-1066, 1762-1852, 2120-2135). */
    public final ChallengeSlotMachine mSlotMachine = new ChallengeSlotMachine(this);

    /** Whack-a-Zombie mallet (Challenge.cpp:945-991, 2491-2532, 4905-4921). */
    public final ChallengeWhackAZombie mWhackAZombie = new ChallengeWhackAZombie(this);

    /** Zombiquarium (Challenge.cpp:3307-3443). */
    public final ChallengeZombiquarium mZombiquarium = new ChallengeZombiquarium(this);

    /** Squirrel (Challenge.cpp:4624-4823). */
    public final ChallengeSquirrel mSquirrel = new ChallengeSquirrel(this);

    /** Tree of Wisdom (Challenge.cpp:4923-5335). */
    public final ChallengeTreeOfWisdom mTreeOfWisdom = new ChallengeTreeOfWisdom(this);

    // Mini-game bodies, delegated to the helpers above (upstream Challenge.cpp declarations).

    /** Ported from src/Lawn/Challenge.cpp:815-830 ({@code Challenge::BeghouledPopulateBoard}). */
    public void beghouledPopulateBoard() {
        mBeghouled.beghouledPopulateBoard();
    }

    /** Ported from src/Lawn/Challenge.cpp:736-781 ({@code Challenge::BeghouledPickSeed}). */
    public int beghouledPickSeed(int theGridX, int theGridY, BeghouledBoardState theBoardState, int theAllowMatches) {
        return mBeghouled.beghouledPickSeed(theGridX, theGridY, theBoardState, theAllowMatches);
    }

    /** Ported from src/Lawn/Challenge.cpp:722-734 ({@code Challenge::BeghouledBoardHasMatch}). */
    public int beghouledBoardHasMatch(BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledBoardHasMatch(theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:503-509 ({@code Challenge::BeghouledGetPlantAt}). */
    public int beghouledGetPlantAt(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledGetPlantAt(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:710-720 ({@code Challenge::BeghouledVerticalMatchLength}). */
    public int beghouledVerticalMatchLength(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledVerticalMatchLength(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:698-708 ({@code Challenge::BeghouledHorizontalMatchLength}). */
    public int beghouledHorizontalMatchLength(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledHorizontalMatchLength(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:881-889 ({@code Challenge::BeghouledDragStart}). */
    public void beghouledDragStart(int x, int y) {
        mBeghouled.beghouledDragStart(x, y);
    }

    /** Ported from src/Lawn/Challenge.cpp:442-501 ({@code Challenge::BeghouledDragUpdate}). */
    public void beghouledDragUpdate(int x, int y) {
        mBeghouled.beghouledDragUpdate(x, y);
    }

    /** Ported from src/Lawn/Challenge.cpp:511-524 ({@code Challenge::BeghouledRemoveHorizontalMatch}). */
    public void beghouledRemoveHorizontalMatch(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        mBeghouled.beghouledRemoveHorizontalMatch(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:526-538 ({@code Challenge::BeghouledRemoveVerticalMatch}). */
    public void beghouledRemoveVerticalMatch(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        mBeghouled.beghouledRemoveVerticalMatch(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:675-696 ({@code Challenge::BeghouledRemoveMatches}). */
    public void beghouledRemoveMatches(BeghouledBoardState theBoardState) {
        mBeghouled.beghouledRemoveMatches(theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:1296-1376 ({@code Challenge::UpdateBeghouled}). */
    public void updateBeghouled() {
        mBeghouled.updateBeghouled();
    }

    /** Ported from src/Lawn/Challenge.cpp:1133-1173 ({@code Challenge::UpdateBeghouledPlant}). */
    public int updateBeghouledPlant(Plant thePlant) {
        return mBeghouled.updateBeghouledPlant(thePlant);
    }

    /** Ported from src/Lawn/Challenge.cpp:540-558 ({@code Challenge::BeghouledFallIntoSquare}). */
    public void beghouledFallIntoSquare(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        mBeghouled.beghouledFallIntoSquare(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:560-572 ({@code Challenge::BeghouledMakePlantsFall}). */
    public void beghouledMakePlantsFall(BeghouledBoardState theBoardState) {
        mBeghouled.beghouledMakePlantsFall(theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:1869-1886 ({@code Challenge::ZombieAtePlant}). */
    public void zombieAtePlant(Plant thePlant) {
        mBeghouled.zombieAtePlant(thePlant);
    }

    /** Ported from src/Lawn/Challenge.cpp:2060-2083 ({@code Challenge::DrawArtChallenge}). */
    public void drawArtChallenge(Graphics g) {
        mScaryPotter.drawArtChallenge(g);
    }

    /** Ported from src/Lawn/Challenge.cpp:2085-2118 ({@code Challenge::DrawBeghouled}). */
    public void drawBeghouled(Graphics g) {
        mBeghouled.drawBeghouled(g);
    }

    /** Ported from src/Lawn/Challenge.cpp:419-440 ({@code Challenge::BeghouledIsValidMove}). */
    public int beghouledIsValidMove(int theFromX, int theFromY, int theToX, int theToY, BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledIsValidMove(theFromX, theFromY, theToX, theToY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:849-879 ({@code Challenge::BeghouledCheckForPossibleMoves}). */
    public int beghouledCheckForPossibleMoves(BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledCheckForPossibleMoves(theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:1854-1867 ({@code Challenge::BeghouledCheckStuckState}). */
    public void beghouledCheckStuckState() {
        mBeghouled.beghouledCheckStuckState();
    }

    /** Ported from src/Lawn/Challenge.cpp:1762-1852 ({@code Challenge::UpdateSlotMachine}). */
    public void updateSlotMachine() {
        mSlotMachine.updateSlotMachine();
    }

    /** Ported from src/Lawn/Challenge.cpp:2120-2135 ({@code Challenge::DrawSlotMachine}). */
    public void drawSlotMachine(Graphics g) {
        mSlotMachine.drawSlotMachine(g);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:945-991 ({@code Challenge::MouseDownWhackAZombie}).
     *
     * <p>Not in the supported set: the mallet swing, helm damage and loot drop belong to the
     * Whack-a-Zombie interaction (its wave spawning, {@link #whackAZombieSpawning()}, IS ported).
     */
    public void mouseDownWhackAZombie(int theX, int theY) {
        mWhackAZombie.mouseDownWhackAZombie(theX, theY);
    }

    /** Ported from src/Lawn/Challenge.cpp:4905-4921 ({@code Challenge::WhackAZombieUpdate}). */
    public void whackAZombieUpdate() {
        mWhackAZombie.whackAZombieUpdate();
    }

    /** Ported from src/Lawn/Challenge.cpp:2491-2532 ({@code Challenge::WhackAZombiePlaceGraves}). */
    public void whackAZombiePlaceGraves(int theGraveCount) {
        mWhackAZombie.whackAZombiePlaceGraves(theGraveCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:2764-2773 ({@code Challenge::DrawStormFlash}). */
    public void drawStormFlash(Graphics g, int theTime, int theMaxAmount) {
        mWeather.drawStormFlash(g, theTime, theMaxAmount);
    }

    /** Ported from src/Lawn/Challenge.cpp:1710-1760 ({@code Challenge::UpdateStormyNight}). */
    public void updateStormyNight() {
        mWeather.updateStormyNight();
    }

    /** Ported from src/Lawn/Challenge.cpp:2834-2863 ({@code Challenge::DrawStormNight}). */
    public void drawStormNight(Graphics g) {
        mWeather.drawStormNight(g);
    }

    /** Ported from src/Lawn/Challenge.cpp:2784-2832 ({@code Challenge::DrawRain}). */
    public void drawRain(Graphics g) {
        mWeather.drawRain(g);
    }

    /** Ported from src/Lawn/Challenge.cpp:4825-4853 ({@code Challenge::UpdateRain}). */
    public void updateRain() {
        mWeather.updateRain();
    }

    /** Ported from src/Lawn/Challenge.cpp:1681-1708 ({@code Challenge::UpdateRainingSeeds}). */
    public void updateRainingSeeds() {
        mWeather.updateRainingSeeds();
    }

    /** Ported from src/Lawn/Challenge.cpp:2870-2906 ({@code Challenge::PortalStart}). */
    public void portalStart() {
        mPortalCombat.portalStart();
    }

    /** Ported from src/Lawn/Challenge.cpp:3064-3094 ({@code Challenge::UpdatePortalCombat}). */
    public void updatePortalCombat() {
        mPortalCombat.updatePortalCombat();
    }

    /** Ported from src/Lawn/Challenge.cpp:2908-2984 ({@code Challenge::UpdatePortal}). */
    public void updatePortal(GridItem thePortal) {
        mPortalCombat.updatePortal(thePortal);
    }

    /** Ported from src/Lawn/Challenge.cpp:3000-3012 ({@code Challenge::GetPortalAt}). */
    public GridItem getPortalAt(int theGridX, int theGridY) {
        return mPortalCombat.getPortalAt(theGridX, theGridY);
    }

    /** Ported from src/Lawn/Challenge.cpp:3014-3062 ({@code Challenge::MoveAPortal}). */
    public void moveAPortal() {
        mPortalCombat.moveAPortal();
    }

    /** Ported from src/Lawn/Challenge.cpp:3204-3233 ({@code Challenge::CanTargetZombieWithPortals}). */
    public int canTargetZombieWithPortals(Plant thePlant, Zombie theZombie) {
        return mPortalCombat.canTargetZombieWithPortals(thePlant, theZombie);
    }

    /** Ported from src/Lawn/Challenge.cpp:3235-3275 ({@code Challenge::BeghouledPacketClicked}). */
    public void beghouledPacketClicked(SeedPacket theSeedPacket) {
        mBeghouled.beghouledPacketClicked(theSeedPacket);
    }

    /** Ported from src/Lawn/Challenge.cpp:3277-3289 ({@code Challenge::BeghouledShuffle}). */
    public void beghouledShuffle() {
        mBeghouled.beghouledShuffle();
    }

    /** Ported from src/Lawn/Challenge.cpp:3291-3305 ({@code Challenge::BeghouledCanClearCrater}). */
    public int beghouledCanClearCrater() {
        return mBeghouled.beghouledCanClearCrater();
    }

    /** Ported from src/Lawn/Challenge.cpp:574-582 ({@code Challenge::BeghouledUpdateCraters}). */
    public void beghouledUpdateCraters() {
        mBeghouled.beghouledUpdateCraters();
    }

    /** Ported from src/Lawn/Challenge.cpp:584-604 ({@code Challenge::BeghouledClearCrater}). */
    public void beghouledClearCrater(int theCount) {
        mBeghouled.beghouledClearCrater(theCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:3307-3313 ({@code Challenge::ZombiquariumSpawnSnorkle}). */
    public Zombie zombiquariumSpawnSnorkle() {
        return mZombiquarium.zombiquariumSpawnSnorkle();
    }

    /** Ported from src/Lawn/Challenge.cpp:3315-3344 ({@code Challenge::ZombiquariumPacketClicked}). */
    public void zombiquariumPacketClicked(SeedPacket theSeedPacket) {
        mZombiquarium.zombiquariumPacketClicked(theSeedPacket);
    }

    /** Ported from src/Lawn/Challenge.cpp:3360-3379 ({@code Challenge::ZombiquariumMouseDown}). */
    public void zombiquariumMouseDown(int x, int y) {
        mZombiquarium.zombiquariumMouseDown(x, y);
    }

    /** Ported from src/Lawn/Challenge.cpp:3346-3358 ({@code Challenge::ZombiquariumDropBrain}). */
    public void zombiquariumDropBrain(int x, int y) {
        mZombiquarium.zombiquariumDropBrain(x, y);
    }

    /** Ported from src/Lawn/Challenge.cpp:3381-3443 ({@code Challenge::ZombiquariumUpdate}). */
    public void zombiquariumUpdate() {
        mZombiquarium.zombiquariumUpdate();
    }

    /** Ported from src/Lawn/Challenge.cpp:3445-3453 ({@code Challenge::ShovelAddWallnuts}). */
    public void shovelAddWallnuts() {
        for (int aCol = 0; aCol < Board.MAX_GRID_SIZE_X; aCol++) {
            for (int aRow = 0; aRow < Board.MAX_GRID_SIZE_Y - 1; aRow++) {
                mBoard.addPlant(aCol, aRow, SeedType.SEED_WALLNUT, SeedType.SEED_NONE);
            }
        }
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3481-3502 ({@code Challenge::ScaryPotterPlacePot}).
     *
     * <p>{@code theScaryPotType} is a {@link ScaryPotType} value, {@code theZombieType} a
     * {@link ZombieType} value and {@code theSeedType} a {@link SeedType} value.
     */
    public void scaryPotterPlacePot(int theScaryPotType, int theZombieType, int theSeedType, int theCount,
            PvzpWeightedGridArray[] theGridArray, int theGridArrayCount) {
        mScaryPotter.scaryPotterPlacePot(theScaryPotType, theZombieType, theSeedType, theCount, theGridArray, theGridArrayCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:3757-3763 ({@code Challenge::ScaryPotterStart}). */
    public void scaryPotterStart() {
        mScaryPotter.scaryPotterStart();
    }

    /** Ported from src/Lawn/Challenge.cpp:3780-3800 ({@code Challenge::ScaryPotterUpdate}). */
    public void scaryPotterUpdate() {
        mScaryPotter.scaryPotterUpdate();
    }

    /** Ported from src/Lawn/Challenge.cpp:3873-3924 ({@code Challenge::ScaryPotterOpenPot}). */
    public void scaryPotterOpenPot(GridItem theScaryPot) {
        mScaryPotter.scaryPotterOpenPot(theScaryPot);
    }

    /** Ported from src/Lawn/Challenge.cpp:3926-3940 ({@code Challenge::ScaryPotterJackExplode}). */
    public void scaryPotterJackExplode(int thePosX, int thePosY) {
        mScaryPotter.scaryPotterJackExplode(thePosX, thePosY);
    }

    /** Ported from src/Lawn/Challenge.cpp:3802-3813 ({@code Challenge::ScaryPotterIsCompleted}). */
    public int scaryPotterIsCompleted() {
        return mScaryPotter.scaryPotterIsCompleted();
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:3504-3532 ({@code Challenge::ScaryPotterChangePotType});
     * {@code thePotType} is a {@link GridItemState} value.
     */
    public void scaryPotterChangePotType(int thePotType, int theCount) {
        mScaryPotter.scaryPotterChangePotType(thePotType, theCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:3534-3755 ({@code Challenge::ScaryPotterPopulate}). */
    public void scaryPotterPopulate() {
        mScaryPotter.scaryPotterPopulate();
    }

    /** Ported from src/Lawn/Challenge.cpp:3456-3465 ({@code Challenge::ScaryPotterDontPlaceInCol}). */
    public void scaryPotterDontPlaceInCol(int theCol, PvzpWeightedGridArray[] theGridArray, int theGridArrayCount) {
        mScaryPotter.scaryPotterDontPlaceInCol(theCol, theGridArray, theGridArrayCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:3467-3479 ({@code Challenge::ScaryPotterFillColumnWithPlant}). */
    public void scaryPotterFillColumnWithPlant(int theCol, int theSeedType, PvzpWeightedGridArray[] theGridArray,
            int theGridArrayCount) {
        mScaryPotter.scaryPotterFillColumnWithPlant(theCol, theSeedType, theGridArray, theGridArrayCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:3802-3813, 3815-3828 ({@code Challenge::ScaryPotterMalletPot}). */
    public void scaryPotterMalletPot(GridItem theScaryPot) {
        mScaryPotter.scaryPotterMalletPot(theScaryPot);
    }

    /** Ported from src/Lawn/Challenge.cpp:3868-3871 ({@code Challenge::ScaryPotterCountSunInPot}). */
    public int scaryPotterCountSunInPot(GridItem theScaryPot) {
        return mScaryPotter.scaryPotterCountSunInPot(theScaryPot);
    }

    /** Ported from src/Lawn/Challenge.cpp:3815-3828 ({@code Challenge::ScaryPotterCountPots}). */
    public int scaryPotterCountPots() {
        return mScaryPotter.scaryPotterCountPots();
    }

    /** Ported from src/Lawn/Challenge.cpp:3830-3837 ({@code Challenge::PuzzleIsAwardStage}). */
    public int puzzleIsAwardStage() {
        return mScaryPotter.puzzleIsAwardStage();
    }

    /** Ported from src/Lawn/Challenge.cpp:3839-3866 ({@code Challenge::PuzzlePhaseComplete}). */
    public void puzzlePhaseComplete(int theGridX, int theGridY) {
        mScaryPotter.puzzlePhaseComplete(theGridX, theGridY);
    }

    /** Ported from src/Lawn/Challenge.cpp:3942-3980 ({@code Challenge::PuzzleNextStageClear}). */
    public void puzzleNextStageClear() {
        mScaryPotter.puzzleNextStageClear();
    }

    /** Ported from src/Lawn/Challenge.cpp:4043-4085 ({@code Challenge::IZombieMouseDownWithZombie}). */
    public void iZombieMouseDownWithZombie(int theX, int theY, int theClickCount) {
        mIZombie.iZombieMouseDownWithZombie(theX, theY, theClickCount);
    }

    /** Ported from src/Lawn/Challenge.cpp:4403-4406 ({@code Challenge::IZombieStart}). */
    public void iZombieStart() {
        mIZombie.iZombieStart();
    }

    /** Ported from src/Lawn/Challenge.cpp:4116-4173 ({@code Challenge::IZombiePlacePlants}). */
    public void iZombiePlacePlants(int theSeedType, int theCount, int theGridY) {
        mIZombie.iZombiePlacePlants(theSeedType, theCount, theGridY);
    }

    /** Ported from src/Lawn/Challenge.cpp:4408-4469 ({@code Challenge::IZombieUpdate}). */
    public void iZombieUpdate() {
        mIZombie.iZombieUpdate();
    }

    /** Ported from src/Lawn/Challenge.cpp:4505-4539 ({@code Challenge::IZombieDrawPlant}). */
    public void iZombieDrawPlant(Graphics g, Plant thePlant) {
        mIZombie.iZombieDrawPlant(g, thePlant);
    }

    /** Ported from src/Lawn/Challenge.cpp:4493-4503 ({@code Challenge::IZombieSetPlantFilterEffect}). */
    public void iZombieSetPlantFilterEffect(Plant thePlant, int theFilterEffect) {
        mIZombie.iZombieSetPlantFilterEffect(thePlant, theFilterEffect);
    }

    /** Ported from src/Lawn/Challenge.cpp:4175-4401 ({@code Challenge::IZombieInitLevel}). */
    public void iZombieInitLevel() {
        mIZombie.iZombieInitLevel();
    }

    /** Ported from src/Lawn/Challenge.cpp:4087-4106 ({@code Challenge::IZombieSetupPlant}). */
    public void iZombieSetupPlant(Plant thePlant) {
        mIZombie.iZombieSetupPlant(thePlant);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:4586-4601 ({@code Challenge::IZombieEatBrain}).
     *
     * <p>Upstream returns {@code int} 1/0 for "ate the brain"; the port returns {@code boolean} because
     * the only caller (Zombie.java:2460) uses it as a condition, matching the sibling
     * {@code Zombie.isZombotany} / {@code Board.isFlagWave} normalisation.
     */
    public boolean iZombieEatBrain(Zombie theZombie) {
        return mIZombie.iZombieEatBrain(theZombie);
    }

    /** Ported from src/Lawn/Challenge.cpp:4541-4561 ({@code Challenge::IZombieGetBrainTarget}). */
    public GridItem iZombieGetBrainTarget(Zombie theZombie) {
        return mIZombie.iZombieGetBrainTarget(theZombie);
    }

    /** Ported from src/Lawn/Challenge.cpp:4108-4114 ({@code Challenge::IZombiePlacePlantInSquare}). */
    public void iZombiePlacePlantInSquare(int theSeedType, int theGridX, int theGridY) {
        mIZombie.iZombiePlacePlantInSquare(theSeedType, theGridX, theGridY);
    }

    /** Ported from src/Lawn/Challenge.cpp:4603-4613 ({@code Challenge::IZombiePlantDropRemainingSun}). */
    public void iZombiePlantDropRemainingSun(Plant thePlant) {
        mIZombie.iZombiePlantDropRemainingSun(thePlant);
    }

    /** Ported from src/Lawn/Challenge.cpp:4615-4622 ({@code Challenge::IZombieSquishBrain}). */
    public void iZombieSquishBrain(GridItem theBrain) {
        mIZombie.iZombieSquishBrain(theBrain);
    }

    /** Ported from src/Lawn/Challenge.cpp:4563-4584 ({@code Challenge::IZombieScoreBrain}). */
    public void iZombieScoreBrain(GridItem theBrain) {
        mIZombie.iZombieScoreBrain(theBrain);
    }

    /** Ported from src/Lawn/Challenge.cpp:4026-4041 ({@code Challenge::IZombiePlaceZombie}). */
    public void iZombiePlaceZombie(int theZombieType, int theGridX, int theGridY) {
        mIZombie.iZombiePlaceZombie(theZombieType, theGridX, theGridY);
    }

    /** Ported from src/Lawn/Challenge.cpp:4624-4638 ({@code Challenge::SquirrelCountUncaught}). */
    public int squirrelCountUncaught() {
        return mSquirrel.squirrelCountUncaught();
    }

    /** Ported from src/Lawn/Challenge.cpp:4640-4682 ({@code Challenge::SquirrelStart}). */
    public void squirrelStart() {
        mSquirrel.squirrelStart();
    }

    /** Ported from src/Lawn/Challenge.cpp:4684-4698 ({@code Challenge::SquirrelChew}). */
    public void squirrelChew(GridItem theSquirrel) {
        mSquirrel.squirrelChew(theSquirrel);
    }

    /** Ported from src/Lawn/Challenge.cpp:4700-4704 ({@code Challenge::SquirrelPeek}). */
    public void squirrelPeek(GridItem theSquirrel) {
        mSquirrel.squirrelPeek(theSquirrel);
    }

    /** Ported from src/Lawn/Challenge.cpp:4706-4769 ({@code Challenge::SquirrelFound}). */
    public void squirrelFound(GridItem theSquirrel) {
        mSquirrel.squirrelFound(theSquirrel);
    }

    /** Ported from src/Lawn/Challenge.cpp:4771-4809 ({@code Challenge::SquirrelUpdateOne}). */
    public void squirrelUpdateOne(GridItem theSquirrel) {
        mSquirrel.squirrelUpdateOne(theSquirrel);
    }

    /** Ported from src/Lawn/Challenge.cpp:4811-4823 ({@code Challenge::SquirrelUpdate}). */
    public void squirrelUpdate() {
        mSquirrel.squirrelUpdate();
    }

    /** Ported from src/Lawn/Challenge.cpp:4855-4877 ({@code Challenge::LastStandUpdate}). */
    public void lastStandUpdate() {
        if (mBoard.mNextSurvivalStageCounter == 0 && mChallengeState == ChallengeState.STATECHALLENGE_NORMAL
                && mBoard.mStoreButton.mBtnNoDraw) {
            GameButton aButton = mBoard.mStoreButton;
            aButton.mBtnNoDraw = false;
            aButton.mDisabled = false;

            if (mSurvivalStage == 0) {
                aButton.setLabel("[START_ONSLAUGHT]");
                aButton.resize(300, 559, 210, 46);
            } else {
                aButton.setLabel("[CONTINUE_ONSLAUGHT]");
                aButton.resize(270, 559, 257, 46);
            }
        }

        if (mChallengeState == ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT
                && mApp.mGameScene == GameScenes.SCENE_PLAYING) {
            mChallengeStateCounter++;
        }
    }

    /** Ported from src/Lawn/Challenge.cpp:4879-4903 ({@code Challenge::LastStandCompletedStage}). */
    public void lastStandCompletedStage() {
        mApp.playSample(SoundType.SOUND_HUGE_WAVE);
        mChallengeState = ChallengeState.STATECHALLENGE_NORMAL;
        mBoard.mSeedBank.refreshAllPackets();

        for (Plant aPlant : mBoard.mPlants) {
            if (aPlant.mDead) {
                continue;
            }
            if (aPlant.mState == PlantState.STATE_CHOMPER_DIGESTING || aPlant.mState == PlantState.STATE_COBCANNON_ARMING
                    || aPlant.mState == PlantState.STATE_MAGNETSHROOM_SUCKING
                    || aPlant.mState == PlantState.STATE_MAGNETSHROOM_CHARGING) {
                aPlant.mStateCountdown = Math.min(aPlant.mStateCountdown, 200);
            }
        }

        String aFlagStr = LawnApp.pluralize(mBoard.getSurvivalFlagsCompleted(), "[ONE_FLAG]", "[COUNT_FLAGS]");
        String aMsg = PvzpCommon.pvzpReplaceString("[SUCCESSFULLY_DEFENDED]", "{FLAGS}", aFlagStr);
        mBoard.displayAdvice(aMsg, MessageStyle.MESSAGE_STYLE_BIG_MIDDLE_FAST, AdviceType.ADVICE_NONE);

        mSurvivalStage++;
        mBoard.mLevelComplete = false;
        mBoard.initZombieWaves();
    }

    /** Ported from src/Lawn/Challenge.cpp:5197-5254 ({@code Challenge::TreeOfWisdomUpdate}). */
    public void treeOfWisdomUpdate() {
        mTreeOfWisdom.treeOfWisdomUpdate();
    }

    /** Ported from src/Lawn/Challenge.cpp:5091-5109 ({@code Challenge::TreeOfWisdomFertilize}). */
    public void treeOfWisdomFertilize() {
        mTreeOfWisdom.treeOfWisdomFertilize();
    }

    /** Ported from src/Lawn/Challenge.cpp:5020-5068 ({@code Challenge::TreeOfWisdomInit}). */
    public void treeOfWisdomInit() {
        mTreeOfWisdom.treeOfWisdomInit();
    }

    /** Ported from src/Lawn/Challenge.cpp:4923-4928 ({@code Challenge::TreeOfWisdomMouseOn}). */
    public int treeOfWisdomMouseOn(int theX, int theY) {
        return mTreeOfWisdom.treeOfWisdomMouseOn(theX, theY);
    }

    /** Ported from src/Lawn/Challenge.cpp:4930-4933 ({@code Challenge::TreeOfWisdomGetSize}). */
    public int treeOfWisdomGetSize() {
        return mTreeOfWisdom.treeOfWisdomGetSize();
    }

    /** Ported from src/Lawn/Challenge.cpp:4935-5018 ({@code Challenge::TreeOfWisdomDraw}). */
    public void treeOfWisdomDraw(Graphics g) {
        mTreeOfWisdom.treeOfWisdomDraw(g);
    }

    /** Ported from src/Lawn/Challenge.cpp:5270-5275 ({@code Challenge::TreeOfWisdomNextGarden}). */
    public void treeOfWisdomNextGarden() {
        mTreeOfWisdom.treeOfWisdomNextGarden();
    }

    /** Ported from src/Lawn/Challenge.cpp:5187-5195 ({@code Challenge::TreeOfWisdomToolUpdate}). */
    public void treeOfWisdomToolUpdate(GridItem theZenTool) {
        mTreeOfWisdom.treeOfWisdomToolUpdate(theZenTool);
    }

    /** Ported from src/Lawn/Challenge.cpp:5277-5285 ({@code Challenge::TreeOfWisdomOpenStore}). */
    public void treeOfWisdomOpenStore() {
        mTreeOfWisdom.treeOfWisdomOpenStore();
    }

    /** Ported from src/Lawn/Challenge.cpp:5256-5268 ({@code Challenge::TreeOfWisdomLeave}). */
    public void treeOfWisdomLeave() {
        mTreeOfWisdom.treeOfWisdomLeave();
    }

    /** Ported from src/Lawn/Challenge.cpp:5070-5089 ({@code Challenge::TreeOfWisdomGrow}). */
    public void treeOfWisdomGrow() {
        mTreeOfWisdom.treeOfWisdomGrow();
    }

    /** Ported from src/Lawn/Challenge.cpp:5287-5295 ({@code Challenge::TreeOfWisdomTool}). */
    public void treeOfWisdomTool(int theMouseX, int theMouseY) {
        mTreeOfWisdom.treeOfWisdomTool(theMouseX, theMouseY);
    }

    /** Ported from src/Lawn/Challenge.cpp:5297-5318 ({@code Challenge::TreeOfWisdomHitTest}). */
    public int treeOfWisdomHitTest(int theX, int theY, HitResult theHitResult) {
        return mTreeOfWisdom.treeOfWisdomHitTest(theX, theY, theHitResult);
    }

    /** Ported from src/Lawn/Challenge.cpp:5111-5134 ({@code Challenge::TreeOfWisdomBabble}). */
    public void treeOfWisdomBabble() {
        mTreeOfWisdom.treeOfWisdomBabble();
    }

    /** Ported from src/Lawn/Challenge.cpp:5136-5162 ({@code Challenge::TreeOfWisdomGiveWisdom}). */
    public void treeOfWisdomGiveWisdom() {
        mTreeOfWisdom.treeOfWisdomGiveWisdom();
    }

    /** Ported from src/Lawn/Challenge.cpp:5164-5185 ({@code Challenge::TreeOfWisdomSayRepeat}). */
    public void treeOfWisdomSayRepeat() {
        mTreeOfWisdom.treeOfWisdomSayRepeat();
    }

    /** Ported from src/Lawn/Challenge.cpp:5320-5335 ({@code Challenge::TreeOfWisdomCanFeed}). */
    public int treeOfWisdomCanFeed() {
        return mTreeOfWisdom.treeOfWisdomCanFeed();
    }

    /** Ported from src/Lawn/Challenge.cpp:1175-1191 ({@code Challenge::BeghouledFlashPlant}). */
    public void beghouledFlashPlant(int theFlashX, int theFlashY, int theFromX, int theFromY, int theToX, int theToY) {
        mBeghouled.beghouledFlashPlant(theFlashX, theFlashY, theFromX, theFromY, theToX, theToY);
    }

    /** Ported from src/Lawn/Challenge.cpp:1265-1294 ({@code Challenge::BeghouledFlashAMatch}). */
    public void beghouledFlashAMatch() {
        mBeghouled.beghouledFlashAMatch();
    }

    /** Ported from src/Lawn/Challenge.cpp:1209-1252 ({@code Challenge::BeghouledFlashFromBoardState}). */
    public int beghouledFlashFromBoardState(BeghouledBoardState theBoardState, int theFromX, int theFromY, int theToX,
            int theToY) {
        return mBeghouled.beghouledFlashFromBoardState(theBoardState, theFromX, theFromY, theToX, theToY);
    }

    /** Ported from src/Lawn/Challenge.cpp:606-673 ({@code Challenge::BeghouledScore}). */
    public void beghouledScore(int theGridX, int theGridY, int theNumPlants, int theIsHorizontal) {
        mBeghouled.beghouledScore(theGridX, theGridY, theNumPlants, theIsHorizontal);
    }

    /** Ported from src/Lawn/Challenge.cpp:783-795 ({@code Challenge::BeghouledFillHoles}). */
    public void beghouledFillHoles(BeghouledBoardState theBoardState, int theAllowMatches) {
        mBeghouled.beghouledFillHoles(theBoardState, theAllowMatches);
    }

    /** Ported from src/Lawn/Challenge.cpp:797-813 ({@code Challenge::BeghouledCreatePlants}). */
    public void beghouledCreatePlants(BeghouledBoardState theOldBoardState, BeghouledBoardState theNewBoardState) {
        mBeghouled.beghouledCreatePlants(theOldBoardState, theNewBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:815-830 ({@code Challenge::BeghouledMakeStartBoard}). */
    public void beghouledMakeStartBoard() {
        mBeghouled.beghouledMakeStartBoard();
    }

    /** Ported from src/Lawn/Challenge.cpp:412-417 ({@code Challenge::BeghouledStartFalling}). */
    public void beghouledStartFalling(int theState) {
        mBeghouled.beghouledStartFalling(theState);
    }

    /** Ported from src/Lawn/Challenge.cpp:1254-1263 ({@code Challenge::BeghouledCancelMatchFlashing}). */
    public void beghouledCancelMatchFlashing() {
        mBeghouled.beghouledCancelMatchFlashing();
    }

    /** Ported from src/Lawn/Challenge.cpp:374-410 ({@code Challenge::BeghouledTwistMouseDown}). */
    public void beghouledTwistMouseDown(int x, int y) {
        mBeghouled.beghouledTwistMouseDown(x, y);
    }

    /** Ported from src/Lawn/Challenge.cpp:324-334 ({@code Challenge::BeghouledTwistValidMove}). */
    public int beghouledTwistValidMove(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledTwistValidMove(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:336-359 ({@code Challenge::BeghouledTwistMoveCausesMatch}). */
    public int beghouledTwistMoveCausesMatch(int theGridX, int theGridY, BeghouledBoardState theBoardState) {
        return mBeghouled.beghouledTwistMoveCausesMatch(theGridX, theGridY, theBoardState);
    }

    /** Ported from src/Lawn/Challenge.cpp:1193-1207 ({@code Challenge::BeghouledTwistFlashMatch}). */
    public int beghouledTwistFlashMatch(BeghouledBoardState theBoardState, int theGridX, int theGridY) {
        return mBeghouled.beghouledTwistFlashMatch(theBoardState, theGridX, theGridY);
    }

    /**
     * Ported from src/Lawn/Challenge.cpp:361-372 ({@code Challenge::BeghouledTwistSquareFromMouse}).
     *
     * <p>Upstream's out-parameters {@code int& theGridX, int& theGridY} become the two-element
     * {@code theGridXY} array.
     */
    public int beghouledTwistSquareFromMouse(int theX, int theY, int[] theGridXY) {
        return mBeghouled.beghouledTwistSquareFromMouse(theX, theY, theGridXY);
    }
}
