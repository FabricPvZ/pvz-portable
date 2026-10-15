/*
 * Ported from src/Resources.h:1-1909 and src/Resources.cpp:1-3267.
 *
 * The upstream enum class ResourceId (Resources.h:35-916) is flattened into
 * public static final int constants (values are the implicit enum ordinals, 0..878).
 * The id -> (name, type, group) mapping and the ExtractResourcesByName group
 * dispatch (Resources.cpp:31-61 and the 29 Extract*Resources functions) are
 * expressed as data tables instead of the upstream switch/if chains, and the
 * resource-list helpers (Resources.cpp:3201-3267) are mirrored below.
 */
package cn.enaium.fabricpvz.portable.lawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cn.enaium.fabricpvz.portable.sexy.ResourceManager;
import cn.enaium.fabricpvz.portable.sexy.ResourceManagerException;
import cn.enaium.fabricpvz.portable.sexy.graphics.Font;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Ported from src/Resources.h and src/Resources.cpp.
 *
 * <p>Resource id registry for the game: the {@code ResourceId} enum constants, the
 * id &rarr; resource-name/type/group table and the {@code ExtractResourcesByName}
 * loader dispatch that binds loaded resources into {@link #gResources}.
 */
public final class Resources {

    private Resources() {
    }

    /** Ported from src/Resources.h:26 (extern bool gNeedRecalcVariableToIdMap). */
    public static boolean gNeedRecalcVariableToIdMap = false;

    /** Ported from src/Resources.h:916 (the terminating ResourceId::RESOURCE_ID_MAX). */
    public static final int RESOURCE_ID_MAX = 879;

    // =========================================================================
    // ResourceId constants (Resources.h enum class ResourceId, verbatim order)
    // =========================================================================

    // Init Resources
    public static final int IMAGE_BLANK_ID = 0;
    public static final int IMAGE_POPCAP_LOGO_ID = 1;
    public static final int IMAGE_PARTNER_LOGO_ID = 2;

    // LoaderBar Resources
    public static final int IMAGE_TITLESCREEN_ID = 3;
    public static final int IMAGE_LOADBAR_DIRT_ID = 4;
    public static final int IMAGE_LOADBAR_GRASS_ID = 5;
    public static final int IMAGE_PVZ_LOGO_ID = 6;
    public static final int IMAGE_REANIM_SODROLLCAP_ID = 7;
    public static final int FONT_BRIANNETOD16_ID = 8;
    public static final int SOUND_BUTTONCLICK_ID = 9;
    public static final int SOUND_LOADINGBAR_FLOWER_ID = 10;
    public static final int SOUND_LOADINGBAR_ZOMBIE_ID = 11;

    // LoadingFonts Resources
    public static final int FONT_IMAGE_HOUSEOFTERROR28_ID = 12;
    public static final int FONT_HOUSEOFTERROR28_ID = 13;
    public static final int FONT_HOUSEOFTERROR20_ID = 14;
    public static final int FONT_HOUSEOFTERROR16_ID = 15;
    public static final int FONT_TINYBOLD_ID = 16;
    public static final int FONT_CONTINUUMBOLD14_ID = 17;
    public static final int FONT_CONTINUUMBOLD14OUTLINE_ID = 18;
    public static final int FONT_DWARVENTODCRAFT12_ID = 19;
    public static final int FONT_DWARVENTODCRAFT15_ID = 20;
    public static final int FONT_DWARVENTODCRAFT18_ID = 21;
    public static final int FONT_DWARVENTODCRAFT18BRIGHTGREENINSET_ID = 22;
    public static final int FONT_DWARVENTODCRAFT18GREENINSET_ID = 23;
    public static final int FONT_DWARVENTODCRAFT18YELLOW_ID = 24;
    public static final int FONT_DWARVENTODCRAFT24_ID = 25;
    public static final int FONT_DWARVENTODCRAFT36BRIGHTGREENINSET_ID = 26;
    public static final int FONT_DWARVENTODCRAFT36GREENINSET_ID = 27;
    public static final int FONT_PICO129_ID = 28;
    public static final int FONT_BRIANNETOD12_ID = 29;

    // LoadingImages Resources
    public static final int IMAGE_EDITBOX_ID = 30;
    public static final int IMAGE_DIALOG_TOPLEFT_ID = 31;
    public static final int IMAGE_DIALOG_TOPMIDDLE_ID = 32;
    public static final int IMAGE_DIALOG_TOPRIGHT_ID = 33;
    public static final int IMAGE_DIALOG_CENTERLEFT_ID = 34;
    public static final int IMAGE_DIALOG_CENTERMIDDLE_ID = 35;
    public static final int IMAGE_DIALOG_CENTERRIGHT_ID = 36;
    public static final int IMAGE_DIALOG_BOTTOMLEFT_ID = 37;
    public static final int IMAGE_DIALOG_BOTTOMMIDDLE_ID = 38;
    public static final int IMAGE_DIALOG_BOTTOMRIGHT_ID = 39;
    public static final int IMAGE_DIALOG_BIGBOTTOMLEFT_ID = 40;
    public static final int IMAGE_DIALOG_BIGBOTTOMMIDDLE_ID = 41;
    public static final int IMAGE_DIALOG_BIGBOTTOMRIGHT_ID = 42;
    public static final int IMAGE_DIALOG_HEADER_ID = 43;
    public static final int IMAGE_BUTTON_LEFT_ID = 44;
    public static final int IMAGE_BUTTON_MIDDLE_ID = 45;
    public static final int IMAGE_BUTTON_RIGHT_ID = 46;
    public static final int IMAGE_BUTTON_DOWN_LEFT_ID = 47;
    public static final int IMAGE_BUTTON_DOWN_MIDDLE_ID = 48;
    public static final int IMAGE_BUTTON_DOWN_RIGHT_ID = 49;
    public static final int IMAGE_SEEDCHOOSER_BACKGROUND_ID = 50;
    public static final int IMAGE_SEEDCHOOSER_BUTTON_ID = 51;
    public static final int IMAGE_SEEDCHOOSER_BUTTON_DISABLED_ID = 52;
    public static final int IMAGE_SEEDCHOOSER_BUTTON_GLOW_ID = 53;
    public static final int IMAGE_SEEDCHOOSER_BUTTON2_ID = 54;
    public static final int IMAGE_SEEDCHOOSER_BUTTON2_GLOW_ID = 55;
    public static final int IMAGE_SEEDCHOOSER_IMITATERADDON_ID = 56;
    public static final int IMAGE_SEEDS_ID = 57;
    public static final int IMAGE_SEEDPACKET_LARGER_ID = 58;
    public static final int IMAGE_SEEDPACKETSILHOUETTE_ID = 59;
    public static final int IMAGE_FLAGMETER_ID = 60;
    public static final int IMAGE_FLAGMETERLEVELPROGRESS_ID = 61;
    public static final int IMAGE_FLAGMETERPARTS_ID = 62;
    public static final int IMAGE_TROPHY_ID = 63;
    public static final int IMAGE_TROPHY_HI_RES_ID = 64;
    public static final int IMAGE_MINIGAME_TROPHY_ID = 65;
    public static final int IMAGE_ZOMBIE_NOTE_SMALL_ID = 66;
    public static final int IMAGE_TACO_ID = 67;
    public static final int IMAGE_CARKEYS_ID = 68;
    public static final int IMAGE_ALMANAC_ID = 69;
    public static final int IMAGE_ICON_POOLCLEANER_ID = 70;
    public static final int IMAGE_ICON_ROOFCLEANER_ID = 71;
    public static final int IMAGE_ICON_RAKE_ID = 72;
    public static final int IMAGE_BRAIN_ID = 73;
    public static final int IMAGE_MONEYBAG_ID = 74;
    public static final int IMAGE_MONEYBAG_HI_RES_ID = 75;
    public static final int IMAGE_CHOCOLATE_ID = 76;
    public static final int IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID = 77;
    public static final int IMAGE_OPTIONS_BACKTOGAMEBUTTON2_ID = 78;
    public static final int IMAGE_OPTIONS_CHECKBOX0_ID = 79;
    public static final int IMAGE_OPTIONS_CHECKBOX1_ID = 80;
    public static final int IMAGE_OPTIONS_MENUBACK_ID = 81;
    public static final int IMAGE_OPTIONS_SLIDERKNOB2_ID = 82;
    public static final int IMAGE_OPTIONS_SLIDERSLOT_ID = 83;
    public static final int IMAGE_SELECTORSCREEN_HELP1_ID = 84;
    public static final int IMAGE_SELECTORSCREEN_HELP2_ID = 85;
    public static final int IMAGE_SELECTORSCREEN_OPTIONS1_ID = 86;
    public static final int IMAGE_SELECTORSCREEN_OPTIONS2_ID = 87;
    public static final int IMAGE_SELECTORSCREEN_QUIT1_ID = 88;
    public static final int IMAGE_SELECTORSCREEN_QUIT2_ID = 89;
    public static final int IMAGE_SELECTORSCREEN_ALMANAC_ID = 90;
    public static final int IMAGE_SELECTORSCREEN_ALMANACHIGHLIGHT_ID = 91;
    public static final int IMAGE_SELECTORSCREEN_STORE_ID = 92;
    public static final int IMAGE_SELECTORSCREEN_STOREHIGHLIGHT_ID = 93;
    public static final int IMAGE_SELECTORSCREEN_LEVELNUMBERS_ID = 94;
    public static final int IMAGE_SELECTORSCREEN_ZENGARDEN_ID = 95;
    public static final int IMAGE_SELECTORSCREEN_ZENGARDENHIGHLIGHT_ID = 96;
    public static final int IMAGE_STORE_SPEECHBUBBLE_ID = 97;
    public static final int IMAGE_STORE_SPEECHBUBBLE2_ID = 98;
    public static final int IMAGE_SELECTORSCREEN_ACHIEVEMENTS_BG_ID = 99;
    public static final int IMAGE_SELECTORSCREEN_MOREWAYSTOPLAY_BG_ID = 100;
    public static final int IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_ID = 101;
    public static final int IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_PRESS_ID = 102;
    public static final int IMAGE_ACHEESEMENTS_BEJEWELED_ID = 103;
    public static final int IMAGE_ACHEESEMENTS_BOOKWORM_ID = 104;
    public static final int IMAGE_ACHEESEMENTS_CHINA_ID = 105;
    public static final int IMAGE_ACHEESEMENTS_CHUZZLE_ID = 106;
    public static final int IMAGE_ACHEESEMENTS_HOLE_TILE_ID = 107;
    public static final int IMAGE_ACHEESEMENTS_PEGGLE_ID = 108;
    public static final int IMAGE_ACHEESEMENTS_PIPE_ID = 109;
    public static final int IMAGE_ACHEESEMENTS_ZUMA_ID = 110;
    public static final int IMAGE_ACHEESEMENTS_ICONS_ID = 111;
    public static final int IMAGE_ACHEESEMENTS_BACK_HIGHLIGHT_ID = 112;
    public static final int IMAGE_QUICKPLAY_MINIGAMES_BUTTON_ID = 113;
    public static final int IMAGE_QUICKPLAY_MINIGAMES_BUTTON_HIGHLIGHT_ID = 114;
    public static final int IMAGE_QUICKPLAY_MINIGAMES_CLOUD_ID = 115;
    public static final int IMAGE_QUICKPLAY_PUZZLES_BUTTON_ID = 116;
    public static final int IMAGE_QUICKPLAY_PUZZLES_BUTTON_HIGHLIGHT_ID = 117;
    public static final int IMAGE_QUICKPLAY_PUZZLES_CLOUD_ID = 118;
    public static final int IMAGE_QUICKPLAY_SURVIVAL_BUTTON_ID = 119;
    public static final int IMAGE_QUICKPLAY_SURVIVAL_BUTTON_HIGHLIGHT_ID = 120;
    public static final int IMAGE_QUICKPLAY_SURVIVAL_CLOUD_ID = 121;
    public static final int IMAGE_QUICKPLAY_BACK_BUTTON_ID = 122;
    public static final int IMAGE_QUICKPLAY_BACK_BUTTON_HIGHLIGHT_ID = 123;
    public static final int IMAGE_ACHEESEMENTS_MORE_ROCK_ID = 124;
    public static final int IMAGE_ACHEESEMENTS_MORE_BUTTON_ID = 125;
    public static final int IMAGE_ACHEESEMENTS_MORE_BUTTON_HIGHLIGHT_ID = 126;
    public static final int IMAGE_ACHEESEMENTS_TOP_BUTTON_ID = 127;
    public static final int IMAGE_ACHEESEMENTS_TOP_BUTTON_HIGHLIGHT_ID = 128;
    public static final int IMAGE_SHOVEL_ID = 129;
    public static final int IMAGE_SHOVEL_HI_RES_ID = 130;
    public static final int IMAGE_TOMBSTONES_ID = 131;
    public static final int IMAGE_TOMBSTONE_MOUNDS_ID = 132;
    public static final int IMAGE_NIGHT_GRAVE_GRAPHIC_ID = 133;
    public static final int IMAGE_CRATER_ID = 134;
    public static final int IMAGE_CRATER_FADING_ID = 135;
    public static final int IMAGE_CRATER_ROOF_CENTER_ID = 136;
    public static final int IMAGE_CRATER_ROOF_LEFT_ID = 137;
    public static final int IMAGE_CRATER_WATER_DAY_ID = 138;
    public static final int IMAGE_CRATER_WATER_NIGHT_ID = 139;
    public static final int IMAGE_COBCANNON_TARGET_ID = 140;
    public static final int IMAGE_COBCANNON_POPCORN_ID = 141;
    public static final int IMAGE_FERTILIZER_ID = 142;
    public static final int IMAGE_WATERINGCAN_ID = 143;
    public static final int IMAGE_WATERINGCANGOLD_ID = 144;
    public static final int IMAGE_PHONOGRAPH_ID = 145;
    public static final int IMAGE_BEGHOULED_TWIST_OVERLAY_ID = 146;
    public static final int IMAGE_ZEN_GOLDTOOLRETICLE_ID = 147;
    public static final int IMAGE_ZEN_GARDENGLOVE_ID = 148;
    public static final int IMAGE_ZEN_MONEYSIGN_ID = 149;
    public static final int IMAGE_ZEN_NEXTGARDEN_ID = 150;
    public static final int IMAGE_ZEN_WHEELBARROW_ID = 151;
    public static final int IMAGE_ZENSHOPBUTTON_ID = 152;
    public static final int IMAGE_ZENSHOPBUTTON_HIGHLIGHT_ID = 153;
    public static final int IMAGE_PRESENT_ID = 154;
    public static final int IMAGE_PRESENTOPEN_ID = 155;
    public static final int IMAGE_SUNFLOWER_TROPHY_ID = 156;
    public static final int IMAGE_SLOTMACHINE_OVERLAY_ID = 157;
    public static final int IMAGE_SCARY_POT_ID = 158;
    public static final int IMAGE_BUG_SPRAY_ID = 159;
    public static final int IMAGE_PLANTSPEECHBUBBLE_ID = 160;
    public static final int IMAGE_WATERDROP_ID = 161;
    public static final int IMAGE_TREEFOOD_ID = 162;
    public static final int IMAGE_PACKET_PLANTS_ID = 163;
    public static final int IMAGE_ZEN_NEED_ICONS_ID = 164;
    public static final int IMAGE_PROJECTILEPEA_ID = 165;
    public static final int IMAGE_PROJECTILESNOWPEA_ID = 166;
    public static final int IMAGE_PROJECTILECACTUS_ID = 167;
    public static final int IMAGE_DIRTSMALL_ID = 168;
    public static final int IMAGE_DIRTBIG_ID = 169;
    public static final int IMAGE_ROCKSMALL_ID = 170;
    public static final int IMAGE_WATERPARTICLE_ID = 171;
    public static final int IMAGE_WHITEWATER_ID = 172;
    public static final int IMAGE_WHITEWATER_SHADOW_ID = 173;
    public static final int IMAGE_MELONPULT_PARTICLES_ID = 174;
    public static final int IMAGE_WINTERMELON_PARTICLES_ID = 175;
    public static final int IMAGE_PROJECTILE_STAR_ID = 176;
    public static final int IMAGE_SEEDBANK_ID = 177;
    public static final int IMAGE_CONVEYORBELT_BACKDROP_ID = 178;
    public static final int IMAGE_CONVEYORBELT_ID = 179;
    public static final int IMAGE_SHOVELBANK_ID = 180;
    public static final int IMAGE_SUNBANK_ID = 181;
    public static final int IMAGE_COINBANK_ID = 182;
    public static final int IMAGE_POOL_ID = 183;
    public static final int IMAGE_POOL_NIGHT_ID = 184;
    public static final int IMAGE_POOL_BASE_ID = 185;
    public static final int IMAGE_POOL_BASE_NIGHT_ID = 186;
    public static final int IMAGE_POOL_SHADING_ID = 187;
    public static final int IMAGE_POOL_SHADING_NIGHT_ID = 188;
    public static final int IMAGE_POOL_CAUSTIC_EFFECT_ID = 189;
    public static final int IMAGE_PLANTSHADOW_ID = 190;
    public static final int IMAGE_PLANTSHADOW2_ID = 191;
    public static final int IMAGE_PEA_SHADOWS_ID = 192;
    public static final int IMAGE_WALLNUT_BOWLINGSTRIPE_ID = 193;
    public static final int IMAGE_ICE_ID = 194;
    public static final int IMAGE_ICE_CAP_ID = 195;
    public static final int IMAGE_ICE_SPARKLES_ID = 196;
    public static final int IMAGE_IMITATERSEED_ID = 197;
    public static final int IMAGE_ALMANAC_IMITATER_ID = 198;
    public static final int IMAGE_IMITATERSEEDDISABLED_ID = 199;
    public static final int IMAGE_ICETRAP_ID = 200;
    public static final int IMAGE_ICETRAP2_ID = 201;
    public static final int IMAGE_ICETRAP_PARTICLES_ID = 202;
    public static final int IMAGE_ZOMBIE_BOBSLED1_ID = 203;
    public static final int IMAGE_ZOMBIE_BOBSLED2_ID = 204;
    public static final int IMAGE_ZOMBIE_BOBSLED3_ID = 205;
    public static final int IMAGE_ZOMBIE_BOBSLED4_ID = 206;
    public static final int IMAGE_ZOMBIE_BOBSLED_INSIDE_ID = 207;
    public static final int IMAGE_BUNGEECORD_ID = 208;
    public static final int IMAGE_BUNGEETARGET_ID = 209;
    public static final int IMAGE_SPOTLIGHT_ID = 210;
    public static final int IMAGE_SPOTLIGHT2_ID = 211;
    public static final int IMAGE_WHITEPIXEL_ID = 212;
    public static final int IMAGE_ZOMBIEPOLEVAULTERHEAD_ID = 213;
    public static final int IMAGE_ZOMBIEFOOTBALLHEAD_ID = 214;
    public static final int IMAGE_POOLSPARKLY_ID = 215;
    public static final int IMAGE_WALLNUTPARTICLESSMALL_ID = 216;
    public static final int IMAGE_WALLNUTPARTICLESLARGE_ID = 217;
    public static final int IMAGE_PEA_SPLATS_ID = 218;
    public static final int IMAGE_STAR_PARTICLES_ID = 219;
    public static final int IMAGE_STAR_SPLATS_ID = 220;
    public static final int IMAGE_PEA_PARTICLES_ID = 221;
    public static final int IMAGE_SNOWPEA_SPLATS_ID = 222;
    public static final int IMAGE_SNOWPEA_PARTICLES_ID = 223;
    public static final int IMAGE_SNOWPEA_PUFF_ID = 224;
    public static final int IMAGE_SNOWFLAKES_ID = 225;
    public static final int IMAGE_POTATOMINE_PARTICLES_ID = 226;
    public static final int IMAGE_PUFFSHROOM_PUFF1_ID = 227;
    public static final int IMAGE_ZAMBONISMOKE_ID = 228;
    public static final int IMAGE_ZOMBIEDANCERHEAD_ID = 229;
    public static final int IMAGE_ZOMBIEBALLOONHEAD_ID = 230;
    public static final int IMAGE_ZOMBIEIMPHEAD_ID = 231;
    public static final int IMAGE_ZOMBIEDIGGERHEAD_ID = 232;
    public static final int IMAGE_ZOMBIEDIGGERARM_ID = 233;
    public static final int IMAGE_ZOMBIEDOLPHINRIDERHEAD_ID = 234;
    public static final int IMAGE_ZOMBIEPOGO_ID = 235;
    public static final int IMAGE_ZOMBIEBACKUPDANCERHEAD_ID = 236;
    public static final int IMAGE_ZOMBIEBOBSLEDHEAD_ID = 237;
    public static final int IMAGE_ZOMBIELADDERHEAD_ID = 238;
    public static final int IMAGE_ZOMBIEYETIHEAD_ID = 239;
    public static final int IMAGE_SEEDPACKETFLASH_ID = 240;
    public static final int IMAGE_ZOMBIEJACKBOXARM_ID = 241;
    public static final int IMAGE_IMITATERCLOUDS_ID = 242;
    public static final int IMAGE_IMITATERPUFFS_ID = 243;
    public static final int IMAGE_ZOMBIE_BOSS_FIREBALL_PARTICLES_ID = 244;
    public static final int IMAGE_ZOMBIE_BOSS_ICEBALL_PARTICLES_ID = 245;
    public static final int IMAGE_ZOMBIE_BOSS_FIREBALL_GROUNDPARTICLES_ID = 246;
    public static final int IMAGE_ZOMBIE_BOSS_ICEBALL_GROUNDPARTICLES_ID = 247;
    public static final int IMAGE_DOOMSHROOM_EXPLOSION_BASE_ID = 248;
    public static final int IMAGE_RAIN_ID = 249;
    public static final int IMAGE_VASE_CHUNKS_ID = 250;
    public static final int IMAGE_ZOMBOSS_PARTICLES_ID = 251;
    public static final int IMAGE_AWARDPICKUPGLOW_ID = 252;
    public static final int IMAGE_ZOMBIE_SEAWEED_ID = 253;
    public static final int IMAGE_PINATA_ID = 254;
    public static final int IMAGE_ZOMBIEFUTUREGLASSES_ID = 255;
    public static final int IMAGE_DUST_PUFFS_ID = 256;
    public static final int IMAGE_REANIM_WALLNUT_BODY_ID = 257;
    public static final int IMAGE_REANIM_WALLNUT_CRACKED1_ID = 258;
    public static final int IMAGE_REANIM_WALLNUT_CRACKED2_ID = 259;
    public static final int IMAGE_REANIM_TALLNUT_CRACKED1_ID = 260;
    public static final int IMAGE_REANIM_TALLNUT_CRACKED2_ID = 261;
    public static final int IMAGE_REANIM_PUMPKIN_DAMAGE1_ID = 262;
    public static final int IMAGE_REANIM_PUMPKIN_DAMAGE3_ID = 263;
    public static final int IMAGE_REANIM_ZOMBIE_CONE1_ID = 264;
    public static final int IMAGE_REANIM_ZOMBIE_CONE2_ID = 265;
    public static final int IMAGE_REANIM_ZOMBIE_CONE3_ID = 266;
    public static final int IMAGE_REANIM_ZOMBIE_BUCKET1_ID = 267;
    public static final int IMAGE_REANIM_ZOMBIE_BUCKET2_ID = 268;
    public static final int IMAGE_REANIM_ZOMBIE_BUCKET3_ID = 269;
    public static final int IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT_ID = 270;
    public static final int IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT2_ID = 271;
    public static final int IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT3_ID = 272;
    public static final int IMAGE_REANIM_ZOMBIE_SCREENDOOR1_ID = 273;
    public static final int IMAGE_REANIM_ZOMBIE_SCREENDOOR2_ID = 274;
    public static final int IMAGE_REANIM_ZOMBIE_SCREENDOOR3_ID = 275;
    public static final int IMAGE_REANIM_ZOMBIE_FLAG1_ID = 276;
    public static final int IMAGE_REANIM_ZOMBIE_FLAG3_ID = 277;
    public static final int IMAGE_REANIM_ZOMBIE_OUTERARM_UPPER2_ID = 278;
    public static final int IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET_ID = 279;
    public static final int IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET2_ID = 280;
    public static final int IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET3_ID = 281;
    public static final int IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_HAND_ID = 282;
    public static final int IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_UPPER2_ID = 283;
    public static final int IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_UPPER2_ID = 284;
    public static final int IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_LOWER_ID = 285;
    public static final int IMAGE_REANIM_ZOMBIE_PAPER_PAPER2_ID = 286;
    public static final int IMAGE_REANIM_ZOMBIE_PAPER_PAPER3_ID = 287;
    public static final int IMAGE_REANIM_ZOMBIE_PAPER_MADHEAD_ID = 288;
    public static final int IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE1_ID = 289;
    public static final int IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE2_ID = 290;
    public static final int IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE1_ID = 291;
    public static final int IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE2_ID = 292;
    public static final int IMAGE_REANIM_ZOMBIE_ZAMBONI_WHEEL_FLAT_ID = 293;
    public static final int IMAGE_REANIM_COIN_SILVER_DOLLAR_ID = 294;
    public static final int IMAGE_REANIM_COIN_GOLD_DOLLAR_ID = 295;
    public static final int IMAGE_REANIM_DIAMOND_ID = 296;
    public static final int IMAGE_REANIM_COINGLOW_ID = 297;
    public static final int IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_HAND_ID = 298;
    public static final int IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_UPPER2_ID = 299;
    public static final int IMAGE_REANIM_ZOMBIE_BACKUP_INNERARM_HAND_ID = 300;
    public static final int IMAGE_REANIM_ZOMBIE_BACKUP_OUTERARM_UPPER2_ID = 301;
    public static final int IMAGE_REANIM_ZOMBIE_POLEVAULTER_OUTERARM_UPPER2_ID = 302;
    public static final int IMAGE_REANIM_ZOMBIE_OUTERARM_HAND_ID = 303;
    public static final int IMAGE_REANIM_ZOMBIE_BALLOON_OUTERARM_UPPER2_ID = 304;
    public static final int IMAGE_REANIM_ZOMBIE_IMP_ARM1_BONE_ID = 305;
    public static final int IMAGE_REANIM_ZOMBIE_IMP_ARM2_ID = 306;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_2_ID = 307;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_3_ID = 308;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_DUCKXING_ID = 309;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_ZOMBIE_ID = 310;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_FOOT2_ID = 311;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_ID = 312;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_OUTERARM_LOWER2_ID = 313;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_REDEYE_ID = 314;
    public static final int IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD_REDEYE_ID = 315;
    public static final int IMAGE_REANIM_ZOMBIE_DIGGER_OUTERARM_UPPER2_ID = 316;
    public static final int IMAGE_REANIM_ZOMBIE_DOLPHINRIDER_OUTERARM_UPPER2_ID = 317;
    public static final int IMAGE_REANIM_ZOMBIE_POGO_OUTERARM_UPPER2_ID = 318;
    public static final int IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE1_ID = 319;
    public static final int IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE2_ID = 320;
    public static final int IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE1_ID = 321;
    public static final int IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE2_ID = 322;
    public static final int IMAGE_REANIM_ZOMBIE_POGO_STICKHANDS2_ID = 323;
    public static final int IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_UPPER2_ID = 324;
    public static final int IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_HAND_ID = 325;
    public static final int IMAGE_REANIM_ZOMBIE_JACKBOX_BOX_ID = 326;
    public static final int IMAGE_REANIM_ZOMBIE_JACKBOX_OUTERARM_LOWER2_ID = 327;
    public static final int IMAGE_REANIM_ZOMBIE_SNORKLE_OUTERARM_UPPER2_ID = 328;
    public static final int IMAGE_REANIM_ZOMBIE_BUNGI_HEAD_SCARED_ID = 329;
    public static final int IMAGE_REANIM_ZOMBIE_CATAPULT_BASKETBALL_ID = 330;
    public static final int IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_ID = 331;
    public static final int IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE_ID = 332;
    public static final int IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_WITHBALL_ID = 333;
    public static final int IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE_WITHBALL_ID = 334;
    public static final int IMAGE_REANIM_ZOMBIE_CATAPULT_SIDING_DAMAGE_ID = 335;
    public static final int IMAGE_REANIM_ZOMBIE_LADDER_1_ID = 336;
    public static final int IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE1_ID = 337;
    public static final int IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE2_ID = 338;
    public static final int IMAGE_REANIM_ZOMBIE_LADDER_5_ID = 339;
    public static final int IMAGE_REANIM_ZOMBIE_LADDER_OUTERARM_UPPER2_ID = 340;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_ICEBALL_ID = 341;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE1_ID = 342;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE2_ID = 343;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE1_ID = 344;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE2_ID = 345;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE1_ID = 346;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE2_ID = 347;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE1_ID = 348;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE2_ID = 349;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE1_ID = 350;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE2_ID = 351;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_EYEGLOW_BLUE_ID = 352;
    public static final int IMAGE_REANIM_ZOMBIE_BOSS_MOUTHGLOW_BLUE_ID = 353;
    public static final int IMAGE_REANIM_CABBAGEPULT_CABBAGE_ID = 354;
    public static final int IMAGE_REANIM_CORNPULT_KERNAL_ID = 355;
    public static final int IMAGE_REANIM_CORNPULT_BUTTER_ID = 356;
    public static final int IMAGE_REANIM_CORNPULT_BUTTER_SPLAT_ID = 357;
    public static final int IMAGE_REANIM_MELONPULT_MELON_ID = 358;
    public static final int IMAGE_REANIM_WINTERMELON_PROJECTILE_ID = 359;
    public static final int IMAGE_REANIM_ZOMBIE_DIGGER_PICKAXE_ID = 360;
    public static final int IMAGE_REANIM_ZOMBIE_HEAD_GROSSOUT_ID = 361;
    public static final int IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES1_ID = 362;
    public static final int IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES2_ID = 363;
    public static final int IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES3_ID = 364;
    public static final int IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES4_ID = 365;
    public static final int IMAGE_REANIM_GARLIC_BODY2_ID = 366;
    public static final int IMAGE_REANIM_GARLIC_BODY3_ID = 367;
    public static final int IMAGE_REANIM_COBCANNON_COB_ID = 368;
    public static final int IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_UPPER2_ID = 369;
    public static final int IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_HAND_ID = 370;
    public static final int IMAGE_REANIM_ZOMBIE_SNORKLE_HEAD_ID = 371;
    public static final int IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_BUTTON_ID = 372;
    public static final int IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT_ID = 373;
    public static final int IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_BUTTON_ID = 374;
    public static final int IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_HIGHLIGHT_ID = 375;
    public static final int IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_BUTTON_ID = 376;
    public static final int IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_HIGHLIGHT_ID = 377;
    public static final int IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_BUTTON_ID = 378;
    public static final int IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_HIGHLIGHT_ID = 379;
    public static final int IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_BUTTON_ID = 380;
    public static final int IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_HIGHLIGHT_ID = 381;
    public static final int IMAGE_REANIM_SELECTORSCREEN_WOODSIGN2_PRESS_ID = 382;
    public static final int IMAGE_REANIM_SELECTORSCREEN_WOODSIGN3_PRESS_ID = 383;
    public static final int IMAGE_REANIM_SELECTORSCREEN_BG_ID = 384;
    public static final int IMAGE_REANIM_SELECTORSCREEN_ALMANAC_SHADOW_ID = 385;
    public static final int IMAGE_REANIM_SELECTORSCREEN_KEY_SHADOW_ID = 386;
    public static final int IMAGE_REANIM_TREE_BG_ID = 387;
    public static final int IMAGE_REANIM_POT_TOP_DARK_ID = 388;
    public static final int IMAGE_REANIM_ZENGARDEN_BUGSPRAY_BOTTLE_ID = 389;
    public static final int IMAGE_REANIM_STINKY_TURN3_ID = 390;
    public static final int IMAGE_REANIM_CRAZYDAVE_MOUTH1_ID = 391;
    public static final int IMAGE_REANIM_CRAZYDAVE_MOUTH4_ID = 392;
    public static final int IMAGE_REANIM_CRAZYDAVE_MOUTH5_ID = 393;
    public static final int IMAGE_REANIM_CRAZYDAVE_MOUTH6_ID = 394;
    public static final int IMAGE_REANIM_ZOMBIE_MUSTACHE2_ID = 395;
    public static final int IMAGE_REANIM_ZOMBIE_MUSTACHE3_ID = 396;

    // LoadingSounds Resources
    public static final int SOUND_ACHIEVEMENT_ID = 397;
    public static final int SOUND_AWOOGA_ID = 398;
    public static final int SOUND_BLEEP_ID = 399;
    public static final int SOUND_BUZZER_ID = 400;
    public static final int SOUND_CHOMP_ID = 401;
    public static final int SOUND_CHOMP2_ID = 402;
    public static final int SOUND_CHOMPSOFT_ID = 403;
    public static final int SOUND_EVILLAUGH_ID = 404;
    public static final int SOUND_FLOOP_ID = 405;
    public static final int SOUND_FROZEN_ID = 406;
    public static final int SOUND_GULP_ID = 407;
    public static final int SOUND_GROAN_ID = 408;
    public static final int SOUND_GROAN2_ID = 409;
    public static final int SOUND_GROAN3_ID = 410;
    public static final int SOUND_GROAN4_ID = 411;
    public static final int SOUND_GROAN5_ID = 412;
    public static final int SOUND_GROAN6_ID = 413;
    public static final int SOUND_LOSEMUSIC_ID = 414;
    public static final int SOUND_MINDCONTROLLED_ID = 415;
    public static final int SOUND_PAUSE_ID = 416;
    public static final int SOUND_PLANT_ID = 417;
    public static final int SOUND_PLANT2_ID = 418;
    public static final int SOUND_POINTS_ID = 419;
    public static final int SOUND_SEEDLIFT_ID = 420;
    public static final int SOUND_SIREN_ID = 421;
    public static final int SOUND_SLURP_ID = 422;
    public static final int SOUND_SPLAT_ID = 423;
    public static final int SOUND_SPLAT2_ID = 424;
    public static final int SOUND_SPLAT3_ID = 425;
    public static final int SOUND_SUKHBIR_ID = 426;
    public static final int SOUND_SUKHBIR2_ID = 427;
    public static final int SOUND_SUKHBIR3_ID = 428;
    public static final int SOUND_SUKHBIR4_ID = 429;
    public static final int SOUND_SUKHBIR5_ID = 430;
    public static final int SOUND_SUKHBIR6_ID = 431;
    public static final int SOUND_TAP_ID = 432;
    public static final int SOUND_TAP2_ID = 433;
    public static final int SOUND_THROW_ID = 434;
    public static final int SOUND_THROW2_ID = 435;
    public static final int SOUND_BLOVER_ID = 436;
    public static final int SOUND_WINMUSIC_ID = 437;
    public static final int SOUND_LAWNMOWER_ID = 438;
    public static final int SOUND_BOING_ID = 439;
    public static final int SOUND_JACKINTHEBOX_ID = 440;
    public static final int SOUND_DIAMOND_ID = 441;
    public static final int SOUND_DOLPHIN_APPEARS_ID = 442;
    public static final int SOUND_DOLPHIN_BEFORE_JUMPING_ID = 443;
    public static final int SOUND_POTATO_MINE_ID = 444;
    public static final int SOUND_ZAMBONI_ID = 445;
    public static final int SOUND_BALLOON_POP_ID = 446;
    public static final int SOUND_THUNDER_ID = 447;
    public static final int SOUND_ZOMBIESPLASH_ID = 448;
    public static final int SOUND_BOWLING_ID = 449;
    public static final int SOUND_BOWLINGIMPACT_ID = 450;
    public static final int SOUND_BOWLINGIMPACT2_ID = 451;
    public static final int SOUND_GRAVEBUSTERCHOMP_ID = 452;
    public static final int SOUND_GRAVEBUTTON_ID = 453;
    public static final int SOUND_LIMBS_POP_ID = 454;
    public static final int SOUND_PLANTERN_ID = 455;
    public static final int SOUND_POGO_ZOMBIE_ID = 456;
    public static final int SOUND_SNOW_PEA_SPARKLES_ID = 457;
    public static final int SOUND_PLANT_WATER_ID = 458;
    public static final int SOUND_ZOMBIE_ENTERING_WATER_ID = 459;
    public static final int SOUND_ZOMBIE_FALLING_1_ID = 460;
    public static final int SOUND_ZOMBIE_FALLING_2_ID = 461;
    public static final int SOUND_PUFF_ID = 462;
    public static final int SOUND_FUME_ID = 463;
    public static final int SOUND_HUGE_WAVE_ID = 464;
    public static final int SOUND_SLOT_MACHINE_ID = 465;
    public static final int SOUND_COIN_ID = 466;
    public static final int SOUND_ROLL_IN_ID = 467;
    public static final int SOUND_DIGGER_ZOMBIE_ID = 468;
    public static final int SOUND_HATCHBACK_CLOSE_ID = 469;
    public static final int SOUND_HATCHBACK_OPEN_ID = 470;
    public static final int SOUND_KERNELPULT_ID = 471;
    public static final int SOUND_KERNELPULT2_ID = 472;
    public static final int SOUND_ZOMBAQUARIUM_DIE_ID = 473;
    public static final int SOUND_BUNGEE_SCREAM_ID = 474;
    public static final int SOUND_BUNGEE_SCREAM2_ID = 475;
    public static final int SOUND_BUNGEE_SCREAM3_ID = 476;
    public static final int SOUND_BUTTER_ID = 477;
    public static final int SOUND_JACK_SURPRISE_ID = 478;
    public static final int SOUND_JACK_SURPRISE2_ID = 479;
    public static final int SOUND_NEWSPAPER_RARRGH_ID = 480;
    public static final int SOUND_NEWSPAPER_RARRGH2_ID = 481;
    public static final int SOUND_NEWSPAPER_RIP_ID = 482;
    public static final int SOUND_SQUASH_HMM_ID = 483;
    public static final int SOUND_SQUASH_HMM2_ID = 484;
    public static final int SOUND_VASE_BREAKING_ID = 485;
    public static final int SOUND_POOL_CLEANER_ID = 486;
    public static final int SOUND_MAGNETSHROOM_ID = 487;
    public static final int SOUND_LADDER_ZOMBIE_ID = 488;
    public static final int SOUND_GARGANTUAR_THUMP_ID = 489;
    public static final int SOUND_BASKETBALL_ID = 490;
    public static final int SOUND_FIREPEA_ID = 491;
    public static final int SOUND_IGNITE_ID = 492;
    public static final int SOUND_IGNITE2_ID = 493;
    public static final int SOUND_READYSETPLANT_ID = 494;
    public static final int SOUND_DOOMSHROOM_ID = 495;
    public static final int SOUND_EXPLOSION_ID = 496;
    public static final int SOUND_FINALWAVE_ID = 497;
    public static final int SOUND_REVERSE_EXPLOSION_ID = 498;
    public static final int SOUND_RVTHROW_ID = 499;
    public static final int SOUND_SHIELDHIT_ID = 500;
    public static final int SOUND_SHIELDHIT2_ID = 501;
    public static final int SOUND_BOSSEXPLOSION_ID = 502;
    public static final int SOUND_CHERRYBOMB_ID = 503;
    public static final int SOUND_BONK_ID = 504;
    public static final int SOUND_SWING_ID = 505;
    public static final int SOUND_RAIN_ID = 506;
    public static final int SOUND_LIGHTFILL_ID = 507;
    public static final int SOUND_PLASTICHIT_ID = 508;
    public static final int SOUND_PLASTICHIT2_ID = 509;
    public static final int SOUND_JALAPENO_ID = 510;
    public static final int SOUND_BALLOONINFLATE_ID = 511;
    public static final int SOUND_BIGCHOMP_ID = 512;
    public static final int SOUND_MELONIMPACT_ID = 513;
    public static final int SOUND_MELONIMPACT2_ID = 514;
    public static final int SOUND_PLANTGROW_ID = 515;
    public static final int SOUND_SHOOP_ID = 516;
    public static final int SOUND_TAPGLASS_ID = 517;
    public static final int SOUND_JUICY_ID = 518;
    public static final int SOUND_COFFEE_ID = 519;
    public static final int SOUND_WAKEUP_ID = 520;
    public static final int SOUND_LOWGROAN_ID = 521;
    public static final int SOUND_LOWGROAN2_ID = 522;
    public static final int SOUND_PRIZE_ID = 523;
    public static final int SOUND_YUCK_ID = 524;
    public static final int SOUND_YUCK2_ID = 525;
    public static final int SOUND_GRASSSTEP_ID = 526;
    public static final int SOUND_SHOVEL_ID = 527;
    public static final int SOUND_COBLAUNCH_ID = 528;
    public static final int SOUND_WATERING_ID = 529;
    public static final int SOUND_POLEVAULT_ID = 530;
    public static final int SOUND_GRAVESTONE_RUMBLE_ID = 531;
    public static final int SOUND_DIRT_RISE_ID = 532;
    public static final int SOUND_FERTILIZER_ID = 533;
    public static final int SOUND_PORTAL_ID = 534;
    public static final int SOUND_BUGSPRAY_ID = 535;
    public static final int SOUND_SCREAM_ID = 536;
    public static final int SOUND_PAPER_ID = 537;
    public static final int SOUND_MONEYFALLS_ID = 538;
    public static final int SOUND_IMP_ID = 539;
    public static final int SOUND_IMP2_ID = 540;
    public static final int SOUND_HYDRAULIC_SHORT_ID = 541;
    public static final int SOUND_HYDRAULIC_ID = 542;
    public static final int SOUND_GARGANTUDEATH_ID = 543;
    public static final int SOUND_CERAMIC_ID = 544;
    public static final int SOUND_BOSSBOULDERATTACK_ID = 545;
    public static final int SOUND_CHIME_ID = 546;
    public static final int SOUND_CRAZYDAVESHORT1_ID = 547;
    public static final int SOUND_CRAZYDAVESHORT2_ID = 548;
    public static final int SOUND_CRAZYDAVESHORT3_ID = 549;
    public static final int SOUND_CRAZYDAVELONG1_ID = 550;
    public static final int SOUND_CRAZYDAVELONG2_ID = 551;
    public static final int SOUND_CRAZYDAVELONG3_ID = 552;
    public static final int SOUND_CRAZYDAVEEXTRALONG1_ID = 553;
    public static final int SOUND_CRAZYDAVEEXTRALONG2_ID = 554;
    public static final int SOUND_CRAZYDAVEEXTRALONG3_ID = 555;
    public static final int SOUND_CRAZYDAVECRAZY_ID = 556;
    public static final int SOUND_PHONOGRAPH_ID = 557;
    public static final int SOUND_DANCER_ID = 558;
    public static final int SOUND_FINALFANFARE_ID = 559;
    public static final int SOUND_CRAZYDAVESCREAM_ID = 560;
    public static final int SOUND_CRAZYDAVESCREAM2_ID = 561;

    // DelayLoad_Background1 Resources
    public static final int IMAGE_BACKGROUND1_ID = 562;
    public static final int IMAGE_BACKGROUND1_GAMEOVER_INTERIOR_OVERLAY_ID = 563;
    public static final int IMAGE_BACKGROUND1_GAMEOVER_MASK_ID = 564;

    // DelayLoad_BackgroundUnsodded Resources
    public static final int IMAGE_BACKGROUND1UNSODDED_ID = 565;
    public static final int IMAGE_SOD1ROW_ID = 566;
    public static final int IMAGE_SOD3ROW_ID = 567;

    // DelayLoad_Background2 Resources
    public static final int IMAGE_BACKGROUND2_ID = 568;
    public static final int IMAGE_BACKGROUND2_GAMEOVER_INTERIOR_OVERLAY_ID = 569;
    public static final int IMAGE_BACKGROUND2_GAMEOVER_MASK_ID = 570;

    // DelayLoad_Background3 Resources
    public static final int IMAGE_BACKGROUND3_ID = 571;
    public static final int IMAGE_BACKGROUND3_GAMEOVER_INTERIOR_OVERLAY_ID = 572;
    public static final int IMAGE_BACKGROUND3_GAMEOVER_MASK_ID = 573;

    // DelayLoad_Background4 Resources
    public static final int IMAGE_BACKGROUND4_ID = 574;
    public static final int IMAGE_BACKGROUND4_GAMEOVER_INTERIOR_OVERLAY_ID = 575;
    public static final int IMAGE_BACKGROUND4_GAMEOVER_MASK_ID = 576;
    public static final int IMAGE_FOG_ID = 577;
    public static final int IMAGE_FOG_SOFTWARE_ID = 578;

    // DelayLoad_Background5 Resources
    public static final int IMAGE_BACKGROUND5_ID = 579;
    public static final int IMAGE_BACKGROUND5_GAMEOVER_MASK_ID = 580;

    // DelayLoad_Background6 Resources
    public static final int IMAGE_BACKGROUND6BOSS_ID = 581;
    public static final int IMAGE_BACKGROUND6_GAMEOVER_MASK_ID = 582;

    // DelayLoad_MushroomGarden Resources
    public static final int IMAGE_BACKGROUND_MUSHROOMGARDEN_ID = 583;

    // DelayLoad_GreenHouseGarden Resources
    public static final int IMAGE_BACKGROUND_GREENHOUSE_ID = 584;

    // DelayLoad_GreenHouseOverlay Resources
    public static final int IMAGE_BACKGROUND_GREENHOUSE_OVERLAY_ID = 585;

    // DelayLoad_Zombiquarium Resources
    public static final int IMAGE_AQUARIUM1_ID = 586;
    public static final int IMAGE_WAVECENTER_ID = 587;
    public static final int IMAGE_WAVESIDE_ID = 588;

    // DelayLoad_AwardScreen Resources
    public static final int IMAGE_AWARDSCREEN_BACK_ID = 589;

    // DelayLoad_ChallengeScreen Resources
    public static final int IMAGE_CHALLENGE_BACKGROUND_ID = 590;
    public static final int IMAGE_CHALLENGE_WINDOW_ID = 591;
    public static final int IMAGE_CHALLENGE_WINDOW_HIGHLIGHT_ID = 592;
    public static final int IMAGE_CHALLENGE_BLANK_ID = 593;
    public static final int IMAGE_CHALLENGE_THUMBNAILS_ID = 594;
    public static final int IMAGE_SURVIVAL_THUMBNAILS_ID = 595;
    public static final int IMAGE_LOCK_ID = 596;
    public static final int IMAGE_LOCK_OPEN_ID = 597;

    // DelayLoad_Almanac Resources
    public static final int IMAGE_ALMANAC_INDEXBACK_ID = 598;
    public static final int IMAGE_ALMANAC_PLANTBACK_ID = 599;
    public static final int IMAGE_ALMANAC_ZOMBIEBACK_ID = 600;
    public static final int IMAGE_ALMANAC_PLANTCARD_ID = 601;
    public static final int IMAGE_ALMANAC_ZOMBIECARD_ID = 602;
    public static final int IMAGE_ALMANAC_ZOMBIEWINDOW_ID = 603;
    public static final int IMAGE_ALMANAC_ZOMBIEWINDOW2_ID = 604;
    public static final int IMAGE_ALMANAC_ZOMBIEBLANK_ID = 605;
    public static final int IMAGE_ALMANAC_GROUNDDAY_ID = 606;
    public static final int IMAGE_ALMANAC_GROUNDNIGHT_ID = 607;
    public static final int IMAGE_ALMANAC_GROUNDPOOL_ID = 608;
    public static final int IMAGE_ALMANAC_GROUNDNIGHTPOOL_ID = 609;
    public static final int IMAGE_ALMANAC_GROUNDROOF_ID = 610;
    public static final int IMAGE_ALMANAC_GROUNDICE_ID = 611;
    public static final int IMAGE_ALMANAC_CLOSEBUTTON_ID = 612;
    public static final int IMAGE_ALMANAC_CLOSEBUTTONHIGHLIGHT_ID = 613;
    public static final int IMAGE_ALMANAC_INDEXBUTTON_ID = 614;
    public static final int IMAGE_ALMANAC_INDEXBUTTONHIGHLIGHT_ID = 615;

    // DelayLoad_Store Resources
    public static final int IMAGE_STORE_BACKGROUND_ID = 616;
    public static final int IMAGE_STORE_BACKGROUNDNIGHT_ID = 617;
    public static final int IMAGE_STORE_CAR_ID = 618;
    public static final int IMAGE_STORE_CAR_NIGHT_ID = 619;
    public static final int IMAGE_STORE_CARCLOSED_ID = 620;
    public static final int IMAGE_STORE_CARCLOSED_NIGHT_ID = 621;
    public static final int IMAGE_STORE_HATCHBACKOPEN_ID = 622;
    public static final int IMAGE_STORE_SIGN_ID = 623;
    public static final int IMAGE_STORE_MAINMENUBUTTON_ID = 624;
    public static final int IMAGE_STORE_MAINMENUBUTTONDOWN_ID = 625;
    public static final int IMAGE_STORE_MAINMENUBUTTONHIGHLIGHT_ID = 626;
    public static final int IMAGE_STORE_NEXTBUTTON_ID = 627;
    public static final int IMAGE_STORE_NEXTBUTTONHIGHLIGHT_ID = 628;
    public static final int IMAGE_STORE_NEXTBUTTONDISABLED_ID = 629;
    public static final int IMAGE_STORE_PREVBUTTON_ID = 630;
    public static final int IMAGE_STORE_PREVBUTTONHIGHLIGHT_ID = 631;
    public static final int IMAGE_STORE_PREVBUTTONDISABLED_ID = 632;
    public static final int IMAGE_STORE_PRICETAG_ID = 633;
    public static final int IMAGE_STORE_PACKETUPGRADE_ID = 634;
    public static final int IMAGE_STORE_MUSHROOMGARDENICON_ID = 635;
    public static final int IMAGE_STORE_AQUARIUMGARDENICON_ID = 636;
    public static final int IMAGE_STORE_TREEOFWISDOMICON_ID = 637;
    public static final int IMAGE_STORE_FIRSTAIDWALLNUTICON_ID = 638;
    public static final int IMAGE_STORE_PVZICON_ID = 639;

    // DelayLoad_ZombieNote Resources
    public static final int IMAGE_ZOMBIE_NOTE_ID = 640;

    // DelayLoad_ZombieNote1 Resources
    public static final int IMAGE_ZOMBIE_NOTE1_ID = 641;

    // DelayLoad_ZombieNote2 Resources
    public static final int IMAGE_ZOMBIE_NOTE2_ID = 642;

    // DelayLoad_ZombieNote3 Resources
    public static final int IMAGE_ZOMBIE_NOTE3_ID = 643;

    // DelayLoad_ZombieNote4 Resources
    public static final int IMAGE_ZOMBIE_NOTE4_ID = 644;

    // DelayLoad_ZombieFinalNote Resources
    public static final int IMAGE_ZOMBIE_FINAL_NOTE_ID = 645;

    // DelayLoad_ZombieNoteHelp Resources
    public static final int IMAGE_ZOMBIE_NOTE_HELP_ID = 646;

    // DelayLoad_Credits Resources
    public static final int FONT_BRIANNETOD32_ID = 647;
    public static final int FONT_BRIANNETOD32BLACK_ID = 648;
    public static final int IMAGE_REANIM_SUNFLOWER_HEAD_SING1_ID = 649;
    public static final int IMAGE_REANIM_SUNFLOWER_HEAD_SING2_ID = 650;
    public static final int IMAGE_REANIM_SUNFLOWER_HEAD_SING3_ID = 651;
    public static final int IMAGE_REANIM_SUNFLOWER_HEAD_SING4_ID = 652;
    public static final int IMAGE_REANIM_SUNFLOWER_HEAD_SING5_ID = 653;
    public static final int IMAGE_REANIM_SUNFLOWER_HEAD_WINK_ID = 654;
    public static final int IMAGE_REANIM_CREDITS_DISCOLIGHTS_ID = 655;
    public static final int IMAGE_REANIM_CREDITS_FOGMACHINE_ID = 656;
    public static final int IMAGE_CREDITS_ZOMBIENOTE_ID = 657;
    public static final int IMAGE_CREDITS_PLAYBUTTON_ID = 658;

    // DelayLoad_Zombatar Resources
    public static final int IMAGE_ZOMBATAR_ACCESSORY_BG_ID = 659;
    public static final int IMAGE_ZOMBATAR_PREV_BUTTON_ID = 660;
    public static final int IMAGE_ZOMBATAR_PREV_BUTTON_HIGHLIGHT_ID = 661;
    public static final int IMAGE_ZOMBATAR_NEXT_BUTTON_ID = 662;
    public static final int IMAGE_ZOMBATAR_NEXT_BUTTON_HIGHLIGHT_ID = 663;
    public static final int IMAGE_ZOMBATAR_ZOMBIE_BLANK_ID = 664;
    public static final int IMAGE_ZOMBATAR_ZOMBIE_BLANK_SKIN_ID = 665;
    public static final int IMAGE_ZOMBATAR_BACKGROUND_CRAZYDAVE_ID = 666;
    public static final int IMAGE_ZOMBATAR_BACKGROUND_MENU_ID = 667;
    public static final int IMAGE_ZOMBATAR_BACKGROUND_MENU_DOS_ID = 668;
    public static final int IMAGE_ZOMBATAR_BACKGROUND_ROOF_ID = 669;
    public static final int IMAGE_ZOMBATAR_WIDGET_BG_ID = 670;
    public static final int IMAGE_ZOMBATAR_WIDGET_INNER_BG_ID = 671;
    public static final int IMAGE_ZOMBATAR_COLORS_BG_ID = 672;
    public static final int IMAGE_ZOMBATAR_LOGO_ID = 673;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_BUTTON_ID = 674;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_BUTTON_HIGHLIGHT_ID = 675;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_BUTTON_OVER_ID = 676;
    public static final int IMAGE_ZOMBATAR_BACKDROPS_BUTTON_ID = 677;
    public static final int IMAGE_ZOMBATAR_BACKDROPS_BUTTON_HIGHLIGHT_ID = 678;
    public static final int IMAGE_ZOMBATAR_BACKDROPS_BUTTON_OVER_ID = 679;
    public static final int IMAGE_ZOMBATAR_CLOTHES_BUTTON_ID = 680;
    public static final int IMAGE_ZOMBATAR_CLOTHES_BUTTON_HIGHLIGHT_ID = 681;
    public static final int IMAGE_ZOMBATAR_CLOTHES_BUTTON_OVER_ID = 682;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_BUTTON_ID = 683;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_BUTTON_HIGHLIGHT_ID = 684;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_BUTTON_OVER_ID = 685;
    public static final int IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_ID = 686;
    public static final int IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_HIGHLIGHT_ID = 687;
    public static final int IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_OVER_ID = 688;
    public static final int IMAGE_ZOMBATAR_HAIR_BUTTON_ID = 689;
    public static final int IMAGE_ZOMBATAR_HAIR_BUTTON_HIGHLIGHT_ID = 690;
    public static final int IMAGE_ZOMBATAR_HAIR_BUTTON_OVER_ID = 691;
    public static final int IMAGE_ZOMBATAR_HATS_BUTTON_ID = 692;
    public static final int IMAGE_ZOMBATAR_HATS_BUTTON_HIGHLIGHT_ID = 693;
    public static final int IMAGE_ZOMBATAR_HATS_BUTTON_OVER_ID = 694;
    public static final int IMAGE_ZOMBATAR_SKIN_BUTTON_ID = 695;
    public static final int IMAGE_ZOMBATAR_SKIN_BUTTON_HIGHLIGHT_ID = 696;
    public static final int IMAGE_ZOMBATAR_TIDBITS_BUTTON_ID = 697;
    public static final int IMAGE_ZOMBATAR_TIDBITS_BUTTON_HIGHLIGHT_ID = 698;
    public static final int IMAGE_ZOMBATAR_TIDBITS_BUTTON_OVER_ID = 699;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_BG_HIGHLIGHT_ID = 700;
    public static final int IMAGE_ZOMBATAR_TRIPTYCH_ID = 701;
    public static final int IMAGE_ZOMBATAR_COLORPICKER_ID = 702;
    public static final int IMAGE_ZOMBATAR_COLORPICKER_HIGHLIGHT_ID = 703;
    public static final int IMAGE_ZOMBATAR_COLORPICKER_NONE_ID = 704;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_BG_NONE_ID = 705;
    public static final int IMAGE_ZOMBATAR_COLORS_BG_SMALL_ID = 706;
    public static final int IMAGE_ZOMBATAR_CLOTHES_1_ID = 707;
    public static final int IMAGE_ZOMBATAR_CLOTHES_2_ID = 708;
    public static final int IMAGE_ZOMBATAR_CLOTHES_3_ID = 709;
    public static final int IMAGE_ZOMBATAR_CLOTHES_4_ID = 710;
    public static final int IMAGE_ZOMBATAR_CLOTHES_5_ID = 711;
    public static final int IMAGE_ZOMBATAR_CLOTHES_6_ID = 712;
    public static final int IMAGE_ZOMBATAR_CLOTHES_7_ID = 713;
    public static final int IMAGE_ZOMBATAR_CLOTHES_8_ID = 714;
    public static final int IMAGE_ZOMBATAR_CLOTHES_9_ID = 715;
    public static final int IMAGE_ZOMBATAR_CLOTHES_10_ID = 716;
    public static final int IMAGE_ZOMBATAR_CLOTHES_11_ID = 717;
    public static final int IMAGE_ZOMBATAR_CLOTHES_12_ID = 718;
    public static final int IMAGE_ZOMBATAR_HATS_1_ID = 719;
    public static final int IMAGE_ZOMBATAR_HATS_2_ID = 720;
    public static final int IMAGE_ZOMBATAR_HATS_3_ID = 721;
    public static final int IMAGE_ZOMBATAR_HATS_4_ID = 722;
    public static final int IMAGE_ZOMBATAR_HATS_5_ID = 723;
    public static final int IMAGE_ZOMBATAR_HATS_6_ID = 724;
    public static final int IMAGE_ZOMBATAR_HATS_7_ID = 725;
    public static final int IMAGE_ZOMBATAR_HATS_8_ID = 726;
    public static final int IMAGE_ZOMBATAR_HATS_9_ID = 727;
    public static final int IMAGE_ZOMBATAR_HATS_10_ID = 728;
    public static final int IMAGE_ZOMBATAR_HATS_11_ID = 729;
    public static final int IMAGE_ZOMBATAR_HATS_12_ID = 730;
    public static final int IMAGE_ZOMBATAR_HATS_13_ID = 731;
    public static final int IMAGE_ZOMBATAR_HATS_14_ID = 732;
    public static final int IMAGE_ZOMBATAR_HAIR_1_ID = 733;
    public static final int IMAGE_ZOMBATAR_HAIR_2_ID = 734;
    public static final int IMAGE_ZOMBATAR_HAIR_3_ID = 735;
    public static final int IMAGE_ZOMBATAR_HAIR_4_ID = 736;
    public static final int IMAGE_ZOMBATAR_HAIR_5_ID = 737;
    public static final int IMAGE_ZOMBATAR_HAIR_6_ID = 738;
    public static final int IMAGE_ZOMBATAR_HAIR_7_ID = 739;
    public static final int IMAGE_ZOMBATAR_HAIR_8_ID = 740;
    public static final int IMAGE_ZOMBATAR_HAIR_9_ID = 741;
    public static final int IMAGE_ZOMBATAR_HAIR_10_ID = 742;
    public static final int IMAGE_ZOMBATAR_HAIR_11_ID = 743;
    public static final int IMAGE_ZOMBATAR_HAIR_12_ID = 744;
    public static final int IMAGE_ZOMBATAR_HAIR_13_ID = 745;
    public static final int IMAGE_ZOMBATAR_HAIR_14_ID = 746;
    public static final int IMAGE_ZOMBATAR_HAIR_15_ID = 747;
    public static final int IMAGE_ZOMBATAR_HAIR_16_ID = 748;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_1_ID = 749;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_2_ID = 750;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_3_ID = 751;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_4_ID = 752;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_5_ID = 753;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_6_ID = 754;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_7_ID = 755;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_8_ID = 756;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_9_ID = 757;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_10_ID = 758;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_11_ID = 759;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_12_ID = 760;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_13_ID = 761;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_14_ID = 762;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_15_ID = 763;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_16_ID = 764;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_1_ID = 765;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_2_ID = 766;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_3_ID = 767;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_4_ID = 768;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_5_ID = 769;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_6_ID = 770;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_7_ID = 771;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_8_ID = 772;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_9_ID = 773;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_10_ID = 774;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_11_ID = 775;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_12_ID = 776;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_13_ID = 777;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_14_ID = 778;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_15_ID = 779;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_16_ID = 780;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_17_ID = 781;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_18_ID = 782;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_19_ID = 783;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_20_ID = 784;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_21_ID = 785;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_22_ID = 786;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_23_ID = 787;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_24_ID = 788;
    public static final int IMAGE_ZOMBATAR_TIDBITS_1_ID = 789;
    public static final int IMAGE_ZOMBATAR_TIDBITS_2_ID = 790;
    public static final int IMAGE_ZOMBATAR_TIDBITS_3_ID = 791;
    public static final int IMAGE_ZOMBATAR_TIDBITS_4_ID = 792;
    public static final int IMAGE_ZOMBATAR_TIDBITS_5_ID = 793;
    public static final int IMAGE_ZOMBATAR_TIDBITS_6_ID = 794;
    public static final int IMAGE_ZOMBATAR_TIDBITS_7_ID = 795;
    public static final int IMAGE_ZOMBATAR_TIDBITS_8_ID = 796;
    public static final int IMAGE_ZOMBATAR_TIDBITS_9_ID = 797;
    public static final int IMAGE_ZOMBATAR_TIDBITS_10_ID = 798;
    public static final int IMAGE_ZOMBATAR_TIDBITS_11_ID = 799;
    public static final int IMAGE_ZOMBATAR_TIDBITS_12_ID = 800;
    public static final int IMAGE_ZOMBATAR_TIDBITS_13_ID = 801;
    public static final int IMAGE_ZOMBATAR_TIDBITS_14_ID = 802;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_1_ID = 803;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_2_ID = 804;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_3_ID = 805;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_4_ID = 806;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_5_ID = 807;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_6_ID = 808;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_7_ID = 809;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_8_ID = 810;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_9_ID = 811;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_10_ID = 812;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_11_ID = 813;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_12_ID = 814;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_13_ID = 815;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_14_ID = 816;
    public static final int IMAGE_ZOMBATAR_HATS_1_MASK_ID = 817;
    public static final int IMAGE_ZOMBATAR_HAIR_1_MASK_ID = 818;
    public static final int IMAGE_ZOMBATAR_HAIR_2_MASK_ID = 819;
    public static final int IMAGE_ZOMBATAR_HAIR_11_MASK_ID = 820;
    public static final int IMAGE_ZOMBATAR_HAIR_12_MASK_ID = 821;
    public static final int IMAGE_ZOMBATAR_HAIR_13_MASK_ID = 822;
    public static final int IMAGE_ZOMBATAR_HAIR_14_MASK_ID = 823;
    public static final int IMAGE_ZOMBATAR_HAIR_15_MASK_ID = 824;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_1_MASK_ID = 825;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_4_MASK_ID = 826;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_8_MASK_ID = 827;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_9_MASK_ID = 828;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_10_MASK_ID = 829;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_11_MASK_ID = 830;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_12_MASK_ID = 831;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_14_MASK_ID = 832;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_15_MASK_ID = 833;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_16_MASK_ID = 834;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_18_MASK_ID = 835;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_21_MASK_ID = 836;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_22_MASK_ID = 837;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_23_MASK_ID = 838;
    public static final int IMAGE_ZOMBATAR_FACIALHAIR_24_MASK_ID = 839;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_1_MASK_ID = 840;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_2_MASK_ID = 841;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_3_MASK_ID = 842;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_4_MASK_ID = 843;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_5_MASK_ID = 844;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_6_MASK_ID = 845;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_7_MASK_ID = 846;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_8_MASK_ID = 847;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_9_MASK_ID = 848;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_10_MASK_ID = 849;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_11_MASK_ID = 850;
    public static final int IMAGE_ZOMBATAR_EYEWEAR_12_MASK_ID = 851;
    public static final int IMAGE_ZOMBATAR_HATS_3_MASK_ID = 852;
    public static final int IMAGE_ZOMBATAR_HATS_6_MASK_ID = 853;
    public static final int IMAGE_ZOMBATAR_HATS_7_MASK_ID = 854;
    public static final int IMAGE_ZOMBATAR_HATS_8_MASK_ID = 855;
    public static final int IMAGE_ZOMBATAR_HATS_9_MASK_ID = 856;
    public static final int IMAGE_ZOMBATAR_HATS_11_MASK_ID = 857;
    public static final int IMAGE_ZOMBATAR_BACKGROUND_BLANK_ID = 858;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_15_ID = 859;
    public static final int IMAGE_ZOMBATAR_ACCESSORY_16_ID = 860;
    public static final int IMAGE_ZOMBATAR_ACCEPT_BUTTON_ID = 861;
    public static final int IMAGE_ZOMBATAR_ACCEPT_BUTTON_HIGHLIGHT_ID = 862;
    public static final int IMAGE_ZOMBATAR_FINISHED_BUTTON_ID = 863;
    public static final int IMAGE_ZOMBATAR_FINISHED_BUTTON_HIGHLIGHT_ID = 864;
    public static final int IMAGE_ZOMBATAR_MAIN_BG_ID = 865;
    public static final int IMAGE_ZOMBATAR_BACK_BUTTON_ID = 866;
    public static final int IMAGE_ZOMBATAR_BACK_BUTTON_HIGHLIGHT_ID = 867;
    public static final int IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_ID = 868;
    public static final int IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_HIGHLIGHT_ID = 869;
    public static final int IMAGE_ZOMBATAR_TOS_ARROW_ID = 870;
    public static final int IMAGE_ZOMBATAR_BEGIN_BUTTON_ID = 871;
    public static final int IMAGE_ZOMBATAR_BEGIN_BUTTON_HIGHLIGHT_ID = 872;
    public static final int IMAGE_ZOMBATAR_TOS_SLIDER_ID = 873;
    public static final int IMAGE_ZOMBATAR_TOS_SLIDER_THUMB_ID = 874;
    public static final int IMAGE_ZOMBATAR_DISPLAY_WINDOW_ID = 875;
    public static final int IMAGE_ZOMBATAR_MAINMENUBACK_HIGHLIGHT_ID = 876;
    public static final int IMAGE_ZOMBATAR_VIEW_BUTTON_ID = 877;
    public static final int IMAGE_ZOMBATAR_VIEW_BUTTON_HIGHLIGHT_ID = 878;

    /**
     * Resource kind, mirrors Sexy::ResType.
     *
     * <p>Ported from src/SexyAppFramework/misc/ResourceManager.h:54-59.
     */
    public enum ResourceType {
        IMAGE,
        SOUND,
        FONT
    }

    /**
     * One row of the id &rarr; resource table: upstream {@code <name>} string key,
     * kind and the {@code Extract*Resources} group that loads it.
     *
     * <p>Ported from src/Resources.h:35-916 and src/Resources.cpp:66-1970.
     */
    public record ResourceEntry(int id, String name, ResourceType type, String group) {
    }

    /**
     * The id &rarr; entry table, indexed by id. Generated mechanically from the
     * upstream enum order and the 29 Extract*Resources functions.
     */
    public static final ResourceEntry[] ENTRIES = {
        // Init
        new ResourceEntry(IMAGE_BLANK_ID, "IMAGE_BLANK", ResourceType.IMAGE, "Init"),
        new ResourceEntry(IMAGE_POPCAP_LOGO_ID, "IMAGE_POPCAP_LOGO", ResourceType.IMAGE, "Init"),
        new ResourceEntry(IMAGE_PARTNER_LOGO_ID, "IMAGE_PARTNER_LOGO", ResourceType.IMAGE, "Init"),
        // LoaderBar
        new ResourceEntry(IMAGE_TITLESCREEN_ID, "IMAGE_TITLESCREEN", ResourceType.IMAGE, "LoaderBar"),
        new ResourceEntry(IMAGE_LOADBAR_DIRT_ID, "IMAGE_LOADBAR_DIRT", ResourceType.IMAGE, "LoaderBar"),
        new ResourceEntry(IMAGE_LOADBAR_GRASS_ID, "IMAGE_LOADBAR_GRASS", ResourceType.IMAGE, "LoaderBar"),
        new ResourceEntry(IMAGE_PVZ_LOGO_ID, "IMAGE_PVZ_LOGO", ResourceType.IMAGE, "LoaderBar"),
        new ResourceEntry(IMAGE_REANIM_SODROLLCAP_ID, "IMAGE_REANIM_SODROLLCAP", ResourceType.IMAGE, "LoaderBar"),
        new ResourceEntry(FONT_BRIANNETOD16_ID, "FONT_BRIANNETOD16", ResourceType.FONT, "LoaderBar"),
        new ResourceEntry(SOUND_BUTTONCLICK_ID, "SOUND_BUTTONCLICK", ResourceType.SOUND, "LoaderBar"),
        new ResourceEntry(SOUND_LOADINGBAR_FLOWER_ID, "SOUND_LOADINGBAR_FLOWER", ResourceType.SOUND, "LoaderBar"),
        new ResourceEntry(SOUND_LOADINGBAR_ZOMBIE_ID, "SOUND_LOADINGBAR_ZOMBIE", ResourceType.SOUND, "LoaderBar"),
        // LoadingFonts
        new ResourceEntry(FONT_IMAGE_HOUSEOFTERROR28_ID, "FONT_IMAGE_HOUSEOFTERROR28", ResourceType.IMAGE, "LoadingFonts"),
        new ResourceEntry(FONT_HOUSEOFTERROR28_ID, "FONT_HOUSEOFTERROR28", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_HOUSEOFTERROR20_ID, "FONT_HOUSEOFTERROR20", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_HOUSEOFTERROR16_ID, "FONT_HOUSEOFTERROR16", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_TINYBOLD_ID, "FONT_TINYBOLD", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_CONTINUUMBOLD14_ID, "FONT_CONTINUUMBOLD14", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_CONTINUUMBOLD14OUTLINE_ID, "FONT_CONTINUUMBOLD14OUTLINE", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT12_ID, "FONT_DWARVENTODCRAFT12", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT15_ID, "FONT_DWARVENTODCRAFT15", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT18_ID, "FONT_DWARVENTODCRAFT18", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT18BRIGHTGREENINSET_ID, "FONT_DWARVENTODCRAFT18BRIGHTGREENINSET", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT18GREENINSET_ID, "FONT_DWARVENTODCRAFT18GREENINSET", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT18YELLOW_ID, "FONT_DWARVENTODCRAFT18YELLOW", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT24_ID, "FONT_DWARVENTODCRAFT24", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT36BRIGHTGREENINSET_ID, "FONT_DWARVENTODCRAFT36BRIGHTGREENINSET", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_DWARVENTODCRAFT36GREENINSET_ID, "FONT_DWARVENTODCRAFT36GREENINSET", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_PICO129_ID, "FONT_PICO129", ResourceType.FONT, "LoadingFonts"),
        new ResourceEntry(FONT_BRIANNETOD12_ID, "FONT_BRIANNETOD12", ResourceType.FONT, "LoadingFonts"),
        // LoadingImages
        new ResourceEntry(IMAGE_EDITBOX_ID, "IMAGE_EDITBOX", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_TOPLEFT_ID, "IMAGE_DIALOG_TOPLEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_TOPMIDDLE_ID, "IMAGE_DIALOG_TOPMIDDLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_TOPRIGHT_ID, "IMAGE_DIALOG_TOPRIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_CENTERLEFT_ID, "IMAGE_DIALOG_CENTERLEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_CENTERMIDDLE_ID, "IMAGE_DIALOG_CENTERMIDDLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_CENTERRIGHT_ID, "IMAGE_DIALOG_CENTERRIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_BOTTOMLEFT_ID, "IMAGE_DIALOG_BOTTOMLEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_BOTTOMMIDDLE_ID, "IMAGE_DIALOG_BOTTOMMIDDLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_BOTTOMRIGHT_ID, "IMAGE_DIALOG_BOTTOMRIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_BIGBOTTOMLEFT_ID, "IMAGE_DIALOG_BIGBOTTOMLEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_BIGBOTTOMMIDDLE_ID, "IMAGE_DIALOG_BIGBOTTOMMIDDLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_BIGBOTTOMRIGHT_ID, "IMAGE_DIALOG_BIGBOTTOMRIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIALOG_HEADER_ID, "IMAGE_DIALOG_HEADER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUTTON_LEFT_ID, "IMAGE_BUTTON_LEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUTTON_MIDDLE_ID, "IMAGE_BUTTON_MIDDLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUTTON_RIGHT_ID, "IMAGE_BUTTON_RIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUTTON_DOWN_LEFT_ID, "IMAGE_BUTTON_DOWN_LEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUTTON_DOWN_MIDDLE_ID, "IMAGE_BUTTON_DOWN_MIDDLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUTTON_DOWN_RIGHT_ID, "IMAGE_BUTTON_DOWN_RIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_BACKGROUND_ID, "IMAGE_SEEDCHOOSER_BACKGROUND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_BUTTON_ID, "IMAGE_SEEDCHOOSER_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_BUTTON_DISABLED_ID, "IMAGE_SEEDCHOOSER_BUTTON_DISABLED", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_BUTTON_GLOW_ID, "IMAGE_SEEDCHOOSER_BUTTON_GLOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_BUTTON2_ID, "IMAGE_SEEDCHOOSER_BUTTON2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_BUTTON2_GLOW_ID, "IMAGE_SEEDCHOOSER_BUTTON2_GLOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDCHOOSER_IMITATERADDON_ID, "IMAGE_SEEDCHOOSER_IMITATERADDON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDS_ID, "IMAGE_SEEDS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDPACKET_LARGER_ID, "IMAGE_SEEDPACKET_LARGER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDPACKETSILHOUETTE_ID, "IMAGE_SEEDPACKETSILHOUETTE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_FLAGMETER_ID, "IMAGE_FLAGMETER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_FLAGMETERLEVELPROGRESS_ID, "IMAGE_FLAGMETERLEVELPROGRESS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_FLAGMETERPARTS_ID, "IMAGE_FLAGMETERPARTS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_TROPHY_ID, "IMAGE_TROPHY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_TROPHY_HI_RES_ID, "IMAGE_TROPHY_HI_RES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_MINIGAME_TROPHY_ID, "IMAGE_MINIGAME_TROPHY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_NOTE_SMALL_ID, "IMAGE_ZOMBIE_NOTE_SMALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_TACO_ID, "IMAGE_TACO", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CARKEYS_ID, "IMAGE_CARKEYS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ALMANAC_ID, "IMAGE_ALMANAC", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICON_POOLCLEANER_ID, "IMAGE_ICON_POOLCLEANER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICON_ROOFCLEANER_ID, "IMAGE_ICON_ROOFCLEANER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICON_RAKE_ID, "IMAGE_ICON_RAKE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BRAIN_ID, "IMAGE_BRAIN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_MONEYBAG_ID, "IMAGE_MONEYBAG", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_MONEYBAG_HI_RES_ID, "IMAGE_MONEYBAG_HI_RES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CHOCOLATE_ID, "IMAGE_CHOCOLATE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_BACKTOGAMEBUTTON0_ID, "IMAGE_OPTIONS_BACKTOGAMEBUTTON0", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_BACKTOGAMEBUTTON2_ID, "IMAGE_OPTIONS_BACKTOGAMEBUTTON2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_CHECKBOX0_ID, "IMAGE_OPTIONS_CHECKBOX0", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_CHECKBOX1_ID, "IMAGE_OPTIONS_CHECKBOX1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_MENUBACK_ID, "IMAGE_OPTIONS_MENUBACK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_SLIDERKNOB2_ID, "IMAGE_OPTIONS_SLIDERKNOB2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_OPTIONS_SLIDERSLOT_ID, "IMAGE_OPTIONS_SLIDERSLOT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_HELP1_ID, "IMAGE_SELECTORSCREEN_HELP1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_HELP2_ID, "IMAGE_SELECTORSCREEN_HELP2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_OPTIONS1_ID, "IMAGE_SELECTORSCREEN_OPTIONS1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_OPTIONS2_ID, "IMAGE_SELECTORSCREEN_OPTIONS2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_QUIT1_ID, "IMAGE_SELECTORSCREEN_QUIT1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_QUIT2_ID, "IMAGE_SELECTORSCREEN_QUIT2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ALMANAC_ID, "IMAGE_SELECTORSCREEN_ALMANAC", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ALMANACHIGHLIGHT_ID, "IMAGE_SELECTORSCREEN_ALMANACHIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_STORE_ID, "IMAGE_SELECTORSCREEN_STORE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_STOREHIGHLIGHT_ID, "IMAGE_SELECTORSCREEN_STOREHIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_LEVELNUMBERS_ID, "IMAGE_SELECTORSCREEN_LEVELNUMBERS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ZENGARDEN_ID, "IMAGE_SELECTORSCREEN_ZENGARDEN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ZENGARDENHIGHLIGHT_ID, "IMAGE_SELECTORSCREEN_ZENGARDENHIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_STORE_SPEECHBUBBLE_ID, "IMAGE_STORE_SPEECHBUBBLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_STORE_SPEECHBUBBLE2_ID, "IMAGE_STORE_SPEECHBUBBLE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ACHIEVEMENTS_BG_ID, "IMAGE_SELECTORSCREEN_ACHIEVEMENTS_BG", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_MOREWAYSTOPLAY_BG_ID, "IMAGE_SELECTORSCREEN_MOREWAYSTOPLAY_BG", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_ID, "IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_PRESS_ID, "IMAGE_SELECTORSCREEN_ACHIEVEMENTS_PEDESTAL_PRESS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_BEJEWELED_ID, "IMAGE_ACHEESEMENTS_BEJEWELED", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_BOOKWORM_ID, "IMAGE_ACHEESEMENTS_BOOKWORM", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_CHINA_ID, "IMAGE_ACHEESEMENTS_CHINA", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_CHUZZLE_ID, "IMAGE_ACHEESEMENTS_CHUZZLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_HOLE_TILE_ID, "IMAGE_ACHEESEMENTS_HOLE_TILE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_PEGGLE_ID, "IMAGE_ACHEESEMENTS_PEGGLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_PIPE_ID, "IMAGE_ACHEESEMENTS_PIPE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_ZUMA_ID, "IMAGE_ACHEESEMENTS_ZUMA", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_ICONS_ID, "IMAGE_ACHEESEMENTS_ICONS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_BACK_HIGHLIGHT_ID, "IMAGE_ACHEESEMENTS_BACK_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_MINIGAMES_BUTTON_ID, "IMAGE_QUICKPLAY_MINIGAMES_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_MINIGAMES_BUTTON_HIGHLIGHT_ID, "IMAGE_QUICKPLAY_MINIGAMES_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_MINIGAMES_CLOUD_ID, "IMAGE_QUICKPLAY_MINIGAMES_CLOUD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_PUZZLES_BUTTON_ID, "IMAGE_QUICKPLAY_PUZZLES_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_PUZZLES_BUTTON_HIGHLIGHT_ID, "IMAGE_QUICKPLAY_PUZZLES_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_PUZZLES_CLOUD_ID, "IMAGE_QUICKPLAY_PUZZLES_CLOUD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_SURVIVAL_BUTTON_ID, "IMAGE_QUICKPLAY_SURVIVAL_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_SURVIVAL_BUTTON_HIGHLIGHT_ID, "IMAGE_QUICKPLAY_SURVIVAL_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_SURVIVAL_CLOUD_ID, "IMAGE_QUICKPLAY_SURVIVAL_CLOUD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_BACK_BUTTON_ID, "IMAGE_QUICKPLAY_BACK_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_QUICKPLAY_BACK_BUTTON_HIGHLIGHT_ID, "IMAGE_QUICKPLAY_BACK_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_MORE_ROCK_ID, "IMAGE_ACHEESEMENTS_MORE_ROCK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_MORE_BUTTON_ID, "IMAGE_ACHEESEMENTS_MORE_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_MORE_BUTTON_HIGHLIGHT_ID, "IMAGE_ACHEESEMENTS_MORE_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_TOP_BUTTON_ID, "IMAGE_ACHEESEMENTS_TOP_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ACHEESEMENTS_TOP_BUTTON_HIGHLIGHT_ID, "IMAGE_ACHEESEMENTS_TOP_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SHOVEL_ID, "IMAGE_SHOVEL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SHOVEL_HI_RES_ID, "IMAGE_SHOVEL_HI_RES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_TOMBSTONES_ID, "IMAGE_TOMBSTONES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_TOMBSTONE_MOUNDS_ID, "IMAGE_TOMBSTONE_MOUNDS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_NIGHT_GRAVE_GRAPHIC_ID, "IMAGE_NIGHT_GRAVE_GRAPHIC", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CRATER_ID, "IMAGE_CRATER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CRATER_FADING_ID, "IMAGE_CRATER_FADING", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CRATER_ROOF_CENTER_ID, "IMAGE_CRATER_ROOF_CENTER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CRATER_ROOF_LEFT_ID, "IMAGE_CRATER_ROOF_LEFT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CRATER_WATER_DAY_ID, "IMAGE_CRATER_WATER_DAY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CRATER_WATER_NIGHT_ID, "IMAGE_CRATER_WATER_NIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_COBCANNON_TARGET_ID, "IMAGE_COBCANNON_TARGET", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_COBCANNON_POPCORN_ID, "IMAGE_COBCANNON_POPCORN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_FERTILIZER_ID, "IMAGE_FERTILIZER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WATERINGCAN_ID, "IMAGE_WATERINGCAN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WATERINGCANGOLD_ID, "IMAGE_WATERINGCANGOLD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PHONOGRAPH_ID, "IMAGE_PHONOGRAPH", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BEGHOULED_TWIST_OVERLAY_ID, "IMAGE_BEGHOULED_TWIST_OVERLAY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZEN_GOLDTOOLRETICLE_ID, "IMAGE_ZEN_GOLDTOOLRETICLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZEN_GARDENGLOVE_ID, "IMAGE_ZEN_GARDENGLOVE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZEN_MONEYSIGN_ID, "IMAGE_ZEN_MONEYSIGN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZEN_NEXTGARDEN_ID, "IMAGE_ZEN_NEXTGARDEN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZEN_WHEELBARROW_ID, "IMAGE_ZEN_WHEELBARROW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZENSHOPBUTTON_ID, "IMAGE_ZENSHOPBUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZENSHOPBUTTON_HIGHLIGHT_ID, "IMAGE_ZENSHOPBUTTON_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PRESENT_ID, "IMAGE_PRESENT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PRESENTOPEN_ID, "IMAGE_PRESENTOPEN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SUNFLOWER_TROPHY_ID, "IMAGE_SUNFLOWER_TROPHY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SLOTMACHINE_OVERLAY_ID, "IMAGE_SLOTMACHINE_OVERLAY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SCARY_POT_ID, "IMAGE_SCARY_POT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUG_SPRAY_ID, "IMAGE_BUG_SPRAY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PLANTSPEECHBUBBLE_ID, "IMAGE_PLANTSPEECHBUBBLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WATERDROP_ID, "IMAGE_WATERDROP", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_TREEFOOD_ID, "IMAGE_TREEFOOD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PACKET_PLANTS_ID, "IMAGE_PACKET_PLANTS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZEN_NEED_ICONS_ID, "IMAGE_ZEN_NEED_ICONS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PROJECTILEPEA_ID, "IMAGE_PROJECTILEPEA", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PROJECTILESNOWPEA_ID, "IMAGE_PROJECTILESNOWPEA", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PROJECTILECACTUS_ID, "IMAGE_PROJECTILECACTUS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIRTSMALL_ID, "IMAGE_DIRTSMALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DIRTBIG_ID, "IMAGE_DIRTBIG", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ROCKSMALL_ID, "IMAGE_ROCKSMALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WATERPARTICLE_ID, "IMAGE_WATERPARTICLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WHITEWATER_ID, "IMAGE_WHITEWATER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WHITEWATER_SHADOW_ID, "IMAGE_WHITEWATER_SHADOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_MELONPULT_PARTICLES_ID, "IMAGE_MELONPULT_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WINTERMELON_PARTICLES_ID, "IMAGE_WINTERMELON_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PROJECTILE_STAR_ID, "IMAGE_PROJECTILE_STAR", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDBANK_ID, "IMAGE_SEEDBANK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CONVEYORBELT_BACKDROP_ID, "IMAGE_CONVEYORBELT_BACKDROP", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_CONVEYORBELT_ID, "IMAGE_CONVEYORBELT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SHOVELBANK_ID, "IMAGE_SHOVELBANK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SUNBANK_ID, "IMAGE_SUNBANK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_COINBANK_ID, "IMAGE_COINBANK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_ID, "IMAGE_POOL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_NIGHT_ID, "IMAGE_POOL_NIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_BASE_ID, "IMAGE_POOL_BASE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_BASE_NIGHT_ID, "IMAGE_POOL_BASE_NIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_SHADING_ID, "IMAGE_POOL_SHADING", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_SHADING_NIGHT_ID, "IMAGE_POOL_SHADING_NIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOL_CAUSTIC_EFFECT_ID, "IMAGE_POOL_CAUSTIC_EFFECT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PLANTSHADOW_ID, "IMAGE_PLANTSHADOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PLANTSHADOW2_ID, "IMAGE_PLANTSHADOW2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PEA_SHADOWS_ID, "IMAGE_PEA_SHADOWS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WALLNUT_BOWLINGSTRIPE_ID, "IMAGE_WALLNUT_BOWLINGSTRIPE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICE_ID, "IMAGE_ICE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICE_CAP_ID, "IMAGE_ICE_CAP", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICE_SPARKLES_ID, "IMAGE_ICE_SPARKLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_IMITATERSEED_ID, "IMAGE_IMITATERSEED", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ALMANAC_IMITATER_ID, "IMAGE_ALMANAC_IMITATER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_IMITATERSEEDDISABLED_ID, "IMAGE_IMITATERSEEDDISABLED", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICETRAP_ID, "IMAGE_ICETRAP", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICETRAP2_ID, "IMAGE_ICETRAP2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ICETRAP_PARTICLES_ID, "IMAGE_ICETRAP_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOBSLED1_ID, "IMAGE_ZOMBIE_BOBSLED1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOBSLED2_ID, "IMAGE_ZOMBIE_BOBSLED2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOBSLED3_ID, "IMAGE_ZOMBIE_BOBSLED3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOBSLED4_ID, "IMAGE_ZOMBIE_BOBSLED4", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOBSLED_INSIDE_ID, "IMAGE_ZOMBIE_BOBSLED_INSIDE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUNGEECORD_ID, "IMAGE_BUNGEECORD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_BUNGEETARGET_ID, "IMAGE_BUNGEETARGET", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SPOTLIGHT_ID, "IMAGE_SPOTLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SPOTLIGHT2_ID, "IMAGE_SPOTLIGHT2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WHITEPIXEL_ID, "IMAGE_WHITEPIXEL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEPOLEVAULTERHEAD_ID, "IMAGE_ZOMBIEPOLEVAULTERHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEFOOTBALLHEAD_ID, "IMAGE_ZOMBIEFOOTBALLHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POOLSPARKLY_ID, "IMAGE_POOLSPARKLY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WALLNUTPARTICLESSMALL_ID, "IMAGE_WALLNUTPARTICLESSMALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_WALLNUTPARTICLESLARGE_ID, "IMAGE_WALLNUTPARTICLESLARGE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PEA_SPLATS_ID, "IMAGE_PEA_SPLATS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_STAR_PARTICLES_ID, "IMAGE_STAR_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_STAR_SPLATS_ID, "IMAGE_STAR_SPLATS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PEA_PARTICLES_ID, "IMAGE_PEA_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SNOWPEA_SPLATS_ID, "IMAGE_SNOWPEA_SPLATS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SNOWPEA_PARTICLES_ID, "IMAGE_SNOWPEA_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SNOWPEA_PUFF_ID, "IMAGE_SNOWPEA_PUFF", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SNOWFLAKES_ID, "IMAGE_SNOWFLAKES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_POTATOMINE_PARTICLES_ID, "IMAGE_POTATOMINE_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PUFFSHROOM_PUFF1_ID, "IMAGE_PUFFSHROOM_PUFF1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZAMBONISMOKE_ID, "IMAGE_ZAMBONISMOKE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEDANCERHEAD_ID, "IMAGE_ZOMBIEDANCERHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEBALLOONHEAD_ID, "IMAGE_ZOMBIEBALLOONHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEIMPHEAD_ID, "IMAGE_ZOMBIEIMPHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEDIGGERHEAD_ID, "IMAGE_ZOMBIEDIGGERHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEDIGGERARM_ID, "IMAGE_ZOMBIEDIGGERARM", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEDOLPHINRIDERHEAD_ID, "IMAGE_ZOMBIEDOLPHINRIDERHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEPOGO_ID, "IMAGE_ZOMBIEPOGO", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEBACKUPDANCERHEAD_ID, "IMAGE_ZOMBIEBACKUPDANCERHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEBOBSLEDHEAD_ID, "IMAGE_ZOMBIEBOBSLEDHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIELADDERHEAD_ID, "IMAGE_ZOMBIELADDERHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEYETIHEAD_ID, "IMAGE_ZOMBIEYETIHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_SEEDPACKETFLASH_ID, "IMAGE_SEEDPACKETFLASH", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEJACKBOXARM_ID, "IMAGE_ZOMBIEJACKBOXARM", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_IMITATERCLOUDS_ID, "IMAGE_IMITATERCLOUDS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_IMITATERPUFFS_ID, "IMAGE_IMITATERPUFFS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOSS_FIREBALL_PARTICLES_ID, "IMAGE_ZOMBIE_BOSS_FIREBALL_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOSS_ICEBALL_PARTICLES_ID, "IMAGE_ZOMBIE_BOSS_ICEBALL_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOSS_FIREBALL_GROUNDPARTICLES_ID, "IMAGE_ZOMBIE_BOSS_FIREBALL_GROUNDPARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_BOSS_ICEBALL_GROUNDPARTICLES_ID, "IMAGE_ZOMBIE_BOSS_ICEBALL_GROUNDPARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DOOMSHROOM_EXPLOSION_BASE_ID, "IMAGE_DOOMSHROOM_EXPLOSION_BASE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_RAIN_ID, "IMAGE_RAIN", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_VASE_CHUNKS_ID, "IMAGE_VASE_CHUNKS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBOSS_PARTICLES_ID, "IMAGE_ZOMBOSS_PARTICLES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_AWARDPICKUPGLOW_ID, "IMAGE_AWARDPICKUPGLOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIE_SEAWEED_ID, "IMAGE_ZOMBIE_SEAWEED", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_PINATA_ID, "IMAGE_PINATA", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_ZOMBIEFUTUREGLASSES_ID, "IMAGE_ZOMBIEFUTUREGLASSES", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_DUST_PUFFS_ID, "IMAGE_DUST_PUFFS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_WALLNUT_BODY_ID, "IMAGE_REANIM_WALLNUT_BODY", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_WALLNUT_CRACKED1_ID, "IMAGE_REANIM_WALLNUT_CRACKED1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_WALLNUT_CRACKED2_ID, "IMAGE_REANIM_WALLNUT_CRACKED2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_TALLNUT_CRACKED1_ID, "IMAGE_REANIM_TALLNUT_CRACKED1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_TALLNUT_CRACKED2_ID, "IMAGE_REANIM_TALLNUT_CRACKED2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_PUMPKIN_DAMAGE1_ID, "IMAGE_REANIM_PUMPKIN_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_PUMPKIN_DAMAGE3_ID, "IMAGE_REANIM_PUMPKIN_DAMAGE3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CONE1_ID, "IMAGE_REANIM_ZOMBIE_CONE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CONE2_ID, "IMAGE_REANIM_ZOMBIE_CONE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CONE3_ID, "IMAGE_REANIM_ZOMBIE_CONE3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BUCKET1_ID, "IMAGE_REANIM_ZOMBIE_BUCKET1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BUCKET2_ID, "IMAGE_REANIM_ZOMBIE_BUCKET2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BUCKET3_ID, "IMAGE_REANIM_ZOMBIE_BUCKET3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT_ID, "IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT2_ID, "IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT3_ID, "IMAGE_REANIM_ZOMBIE_DIGGER_HARDHAT3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_SCREENDOOR1_ID, "IMAGE_REANIM_ZOMBIE_SCREENDOOR1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_SCREENDOOR2_ID, "IMAGE_REANIM_ZOMBIE_SCREENDOOR2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_SCREENDOOR3_ID, "IMAGE_REANIM_ZOMBIE_SCREENDOOR3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FLAG1_ID, "IMAGE_REANIM_ZOMBIE_FLAG1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FLAG3_ID, "IMAGE_REANIM_ZOMBIE_FLAG3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET_ID, "IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET2_ID, "IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET3_ID, "IMAGE_REANIM_ZOMBIE_FOOTBALL_HELMET3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_HAND_ID, "IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_HAND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_FOOTBALL_LEFTARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_LOWER_ID, "IMAGE_REANIM_ZOMBIE_PAPER_LEFTARM_LOWER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_PAPER_PAPER2_ID, "IMAGE_REANIM_ZOMBIE_PAPER_PAPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_PAPER_PAPER3_ID, "IMAGE_REANIM_ZOMBIE_PAPER_PAPER3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_PAPER_MADHEAD_ID, "IMAGE_REANIM_ZOMBIE_PAPER_MADHEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_ZAMBONI_1_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_ZAMBONI_2_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_ZAMBONI_WHEEL_FLAT_ID, "IMAGE_REANIM_ZOMBIE_ZAMBONI_WHEEL_FLAT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_COIN_SILVER_DOLLAR_ID, "IMAGE_REANIM_COIN_SILVER_DOLLAR", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_COIN_GOLD_DOLLAR_ID, "IMAGE_REANIM_COIN_GOLD_DOLLAR", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_DIAMOND_ID, "IMAGE_REANIM_DIAMOND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_COINGLOW_ID, "IMAGE_REANIM_COINGLOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_HAND_ID, "IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_HAND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_DISCO_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BACKUP_INNERARM_HAND_ID, "IMAGE_REANIM_ZOMBIE_BACKUP_INNERARM_HAND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BACKUP_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_BACKUP_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POLEVAULTER_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_POLEVAULTER_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_OUTERARM_HAND_ID, "IMAGE_REANIM_ZOMBIE_OUTERARM_HAND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BALLOON_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_BALLOON_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_IMP_ARM1_BONE_ID, "IMAGE_REANIM_ZOMBIE_IMP_ARM1_BONE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_IMP_ARM2_ID, "IMAGE_REANIM_ZOMBIE_IMP_ARM2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_2_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_3_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_BODY1_3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_DUCKXING_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_DUCKXING", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_ZOMBIE_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_ZOMBIE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_FOOT2_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_FOOT2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_OUTERARM_LOWER2_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_OUTERARM_LOWER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_REDEYE_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD2_REDEYE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD_REDEYE_ID, "IMAGE_REANIM_ZOMBIE_GARGANTUAR_HEAD_REDEYE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DIGGER_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_DIGGER_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DOLPHINRIDER_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_DOLPHINRIDER_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POGO_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_POGO_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_POGO_STICKDAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_POGO_STICK2DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_POGO_STICKHANDS2_ID, "IMAGE_REANIM_ZOMBIE_POGO_STICKHANDS2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_HAND_ID, "IMAGE_REANIM_ZOMBIE_BOBSLED_OUTERARM_HAND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_JACKBOX_BOX_ID, "IMAGE_REANIM_ZOMBIE_JACKBOX_BOX", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_JACKBOX_OUTERARM_LOWER2_ID, "IMAGE_REANIM_ZOMBIE_JACKBOX_OUTERARM_LOWER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_SNORKLE_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_SNORKLE_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BUNGI_HEAD_SCARED_ID, "IMAGE_REANIM_ZOMBIE_BUNGI_HEAD_SCARED", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CATAPULT_BASKETBALL_ID, "IMAGE_REANIM_ZOMBIE_CATAPULT_BASKETBALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_ID, "IMAGE_REANIM_ZOMBIE_CATAPULT_POLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE_ID, "IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_WITHBALL_ID, "IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_WITHBALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE_WITHBALL_ID, "IMAGE_REANIM_ZOMBIE_CATAPULT_POLE_DAMAGE_WITHBALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_CATAPULT_SIDING_DAMAGE_ID, "IMAGE_REANIM_ZOMBIE_CATAPULT_SIDING_DAMAGE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_LADDER_1_ID, "IMAGE_REANIM_ZOMBIE_LADDER_1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_LADDER_1_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_LADDER_5_ID, "IMAGE_REANIM_ZOMBIE_LADDER_5", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_LADDER_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_LADDER_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_ICEBALL_ID, "IMAGE_REANIM_ZOMBIE_BOSS_ICEBALL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_BOSS_HEAD_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_BOSS_JAW_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_HAND_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_BOSS_OUTERARM_THUMB_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE1_ID, "IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE2_ID, "IMAGE_REANIM_ZOMBIE_BOSS_FOOT_DAMAGE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_EYEGLOW_BLUE_ID, "IMAGE_REANIM_ZOMBIE_BOSS_EYEGLOW_BLUE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_BOSS_MOUTHGLOW_BLUE_ID, "IMAGE_REANIM_ZOMBIE_BOSS_MOUTHGLOW_BLUE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CABBAGEPULT_CABBAGE_ID, "IMAGE_REANIM_CABBAGEPULT_CABBAGE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CORNPULT_KERNAL_ID, "IMAGE_REANIM_CORNPULT_KERNAL", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CORNPULT_BUTTER_ID, "IMAGE_REANIM_CORNPULT_BUTTER", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CORNPULT_BUTTER_SPLAT_ID, "IMAGE_REANIM_CORNPULT_BUTTER_SPLAT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_MELONPULT_MELON_ID, "IMAGE_REANIM_MELONPULT_MELON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_WINTERMELON_PROJECTILE_ID, "IMAGE_REANIM_WINTERMELON_PROJECTILE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_DIGGER_PICKAXE_ID, "IMAGE_REANIM_ZOMBIE_DIGGER_PICKAXE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_HEAD_GROSSOUT_ID, "IMAGE_REANIM_ZOMBIE_HEAD_GROSSOUT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES1_ID, "IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES2_ID, "IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES3_ID, "IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES4_ID, "IMAGE_REANIM_ZOMBIE_HEAD_SUNGLASSES4", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_GARLIC_BODY2_ID, "IMAGE_REANIM_GARLIC_BODY2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_GARLIC_BODY3_ID, "IMAGE_REANIM_GARLIC_BODY3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_COBCANNON_COB_ID, "IMAGE_REANIM_COBCANNON_COB", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_UPPER2_ID, "IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_UPPER2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_HAND_ID, "IMAGE_REANIM_ZOMBIE_YETI_OUTERARM_HAND", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_SNORKLE_HEAD_ID, "IMAGE_REANIM_ZOMBIE_SNORKLE_HEAD", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_BUTTON_ID, "IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT_ID, "IMAGE_REANIM_SELECTORSCREEN_ADVENTURE_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_BUTTON_ID, "IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_HIGHLIGHT_ID, "IMAGE_REANIM_SELECTORSCREEN_STARTADVENTURE_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_BUTTON_ID, "IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_HIGHLIGHT_ID, "IMAGE_REANIM_SELECTORSCREEN_SURVIVAL_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_BUTTON_ID, "IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_HIGHLIGHT_ID, "IMAGE_REANIM_SELECTORSCREEN_CHALLENGES_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_BUTTON_ID, "IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_BUTTON", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_HIGHLIGHT_ID, "IMAGE_REANIM_SELECTORSCREEN_VASEBREAKER_HIGHLIGHT", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_WOODSIGN2_PRESS_ID, "IMAGE_REANIM_SELECTORSCREEN_WOODSIGN2_PRESS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_WOODSIGN3_PRESS_ID, "IMAGE_REANIM_SELECTORSCREEN_WOODSIGN3_PRESS", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_BG_ID, "IMAGE_REANIM_SELECTORSCREEN_BG", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_ALMANAC_SHADOW_ID, "IMAGE_REANIM_SELECTORSCREEN_ALMANAC_SHADOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_SELECTORSCREEN_KEY_SHADOW_ID, "IMAGE_REANIM_SELECTORSCREEN_KEY_SHADOW", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_TREE_BG_ID, "IMAGE_REANIM_TREE_BG", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_POT_TOP_DARK_ID, "IMAGE_REANIM_POT_TOP_DARK", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZENGARDEN_BUGSPRAY_BOTTLE_ID, "IMAGE_REANIM_ZENGARDEN_BUGSPRAY_BOTTLE", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_STINKY_TURN3_ID, "IMAGE_REANIM_STINKY_TURN3", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CRAZYDAVE_MOUTH1_ID, "IMAGE_REANIM_CRAZYDAVE_MOUTH1", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CRAZYDAVE_MOUTH4_ID, "IMAGE_REANIM_CRAZYDAVE_MOUTH4", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CRAZYDAVE_MOUTH5_ID, "IMAGE_REANIM_CRAZYDAVE_MOUTH5", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_CRAZYDAVE_MOUTH6_ID, "IMAGE_REANIM_CRAZYDAVE_MOUTH6", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_MUSTACHE2_ID, "IMAGE_REANIM_ZOMBIE_MUSTACHE2", ResourceType.IMAGE, "LoadingImages"),
        new ResourceEntry(IMAGE_REANIM_ZOMBIE_MUSTACHE3_ID, "IMAGE_REANIM_ZOMBIE_MUSTACHE3", ResourceType.IMAGE, "LoadingImages"),
        // LoadingSounds
        new ResourceEntry(SOUND_ACHIEVEMENT_ID, "SOUND_ACHIEVEMENT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_AWOOGA_ID, "SOUND_AWOOGA", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BLEEP_ID, "SOUND_BLEEP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BUZZER_ID, "SOUND_BUZZER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CHOMP_ID, "SOUND_CHOMP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CHOMP2_ID, "SOUND_CHOMP2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CHOMPSOFT_ID, "SOUND_CHOMPSOFT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_EVILLAUGH_ID, "SOUND_EVILLAUGH", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FLOOP_ID, "SOUND_FLOOP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FROZEN_ID, "SOUND_FROZEN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GULP_ID, "SOUND_GULP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GROAN_ID, "SOUND_GROAN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GROAN2_ID, "SOUND_GROAN2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GROAN3_ID, "SOUND_GROAN3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GROAN4_ID, "SOUND_GROAN4", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GROAN5_ID, "SOUND_GROAN5", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GROAN6_ID, "SOUND_GROAN6", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LOSEMUSIC_ID, "SOUND_LOSEMUSIC", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_MINDCONTROLLED_ID, "SOUND_MINDCONTROLLED", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PAUSE_ID, "SOUND_PAUSE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLANT_ID, "SOUND_PLANT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLANT2_ID, "SOUND_PLANT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_POINTS_ID, "SOUND_POINTS", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SEEDLIFT_ID, "SOUND_SEEDLIFT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SIREN_ID, "SOUND_SIREN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SLURP_ID, "SOUND_SLURP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SPLAT_ID, "SOUND_SPLAT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SPLAT2_ID, "SOUND_SPLAT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SPLAT3_ID, "SOUND_SPLAT3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SUKHBIR_ID, "SOUND_SUKHBIR", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SUKHBIR2_ID, "SOUND_SUKHBIR2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SUKHBIR3_ID, "SOUND_SUKHBIR3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SUKHBIR4_ID, "SOUND_SUKHBIR4", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SUKHBIR5_ID, "SOUND_SUKHBIR5", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SUKHBIR6_ID, "SOUND_SUKHBIR6", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_TAP_ID, "SOUND_TAP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_TAP2_ID, "SOUND_TAP2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_THROW_ID, "SOUND_THROW", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_THROW2_ID, "SOUND_THROW2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BLOVER_ID, "SOUND_BLOVER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_WINMUSIC_ID, "SOUND_WINMUSIC", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LAWNMOWER_ID, "SOUND_LAWNMOWER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BOING_ID, "SOUND_BOING", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_JACKINTHEBOX_ID, "SOUND_JACKINTHEBOX", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DIAMOND_ID, "SOUND_DIAMOND", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DOLPHIN_APPEARS_ID, "SOUND_DOLPHIN_APPEARS", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DOLPHIN_BEFORE_JUMPING_ID, "SOUND_DOLPHIN_BEFORE_JUMPING", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_POTATO_MINE_ID, "SOUND_POTATO_MINE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ZAMBONI_ID, "SOUND_ZAMBONI", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BALLOON_POP_ID, "SOUND_BALLOON_POP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_THUNDER_ID, "SOUND_THUNDER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ZOMBIESPLASH_ID, "SOUND_ZOMBIESPLASH", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BOWLING_ID, "SOUND_BOWLING", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BOWLINGIMPACT_ID, "SOUND_BOWLINGIMPACT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BOWLINGIMPACT2_ID, "SOUND_BOWLINGIMPACT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GRAVEBUSTERCHOMP_ID, "SOUND_GRAVEBUSTERCHOMP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GRAVEBUTTON_ID, "SOUND_GRAVEBUTTON", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LIMBS_POP_ID, "SOUND_LIMBS_POP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLANTERN_ID, "SOUND_PLANTERN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_POGO_ZOMBIE_ID, "SOUND_POGO_ZOMBIE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SNOW_PEA_SPARKLES_ID, "SOUND_SNOW_PEA_SPARKLES", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLANT_WATER_ID, "SOUND_PLANT_WATER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ZOMBIE_ENTERING_WATER_ID, "SOUND_ZOMBIE_ENTERING_WATER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ZOMBIE_FALLING_1_ID, "SOUND_ZOMBIE_FALLING_1", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ZOMBIE_FALLING_2_ID, "SOUND_ZOMBIE_FALLING_2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PUFF_ID, "SOUND_PUFF", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FUME_ID, "SOUND_FUME", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_HUGE_WAVE_ID, "SOUND_HUGE_WAVE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SLOT_MACHINE_ID, "SOUND_SLOT_MACHINE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_COIN_ID, "SOUND_COIN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ROLL_IN_ID, "SOUND_ROLL_IN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DIGGER_ZOMBIE_ID, "SOUND_DIGGER_ZOMBIE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_HATCHBACK_CLOSE_ID, "SOUND_HATCHBACK_CLOSE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_HATCHBACK_OPEN_ID, "SOUND_HATCHBACK_OPEN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_KERNELPULT_ID, "SOUND_KERNELPULT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_KERNELPULT2_ID, "SOUND_KERNELPULT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_ZOMBAQUARIUM_DIE_ID, "SOUND_ZOMBAQUARIUM_DIE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BUNGEE_SCREAM_ID, "SOUND_BUNGEE_SCREAM", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BUNGEE_SCREAM2_ID, "SOUND_BUNGEE_SCREAM2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BUNGEE_SCREAM3_ID, "SOUND_BUNGEE_SCREAM3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BUTTER_ID, "SOUND_BUTTER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_JACK_SURPRISE_ID, "SOUND_JACK_SURPRISE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_JACK_SURPRISE2_ID, "SOUND_JACK_SURPRISE2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_NEWSPAPER_RARRGH_ID, "SOUND_NEWSPAPER_RARRGH", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_NEWSPAPER_RARRGH2_ID, "SOUND_NEWSPAPER_RARRGH2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_NEWSPAPER_RIP_ID, "SOUND_NEWSPAPER_RIP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SQUASH_HMM_ID, "SOUND_SQUASH_HMM", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SQUASH_HMM2_ID, "SOUND_SQUASH_HMM2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_VASE_BREAKING_ID, "SOUND_VASE_BREAKING", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_POOL_CLEANER_ID, "SOUND_POOL_CLEANER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_MAGNETSHROOM_ID, "SOUND_MAGNETSHROOM", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LADDER_ZOMBIE_ID, "SOUND_LADDER_ZOMBIE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GARGANTUAR_THUMP_ID, "SOUND_GARGANTUAR_THUMP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BASKETBALL_ID, "SOUND_BASKETBALL", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FIREPEA_ID, "SOUND_FIREPEA", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_IGNITE_ID, "SOUND_IGNITE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_IGNITE2_ID, "SOUND_IGNITE2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_READYSETPLANT_ID, "SOUND_READYSETPLANT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DOOMSHROOM_ID, "SOUND_DOOMSHROOM", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_EXPLOSION_ID, "SOUND_EXPLOSION", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FINALWAVE_ID, "SOUND_FINALWAVE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_REVERSE_EXPLOSION_ID, "SOUND_REVERSE_EXPLOSION", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_RVTHROW_ID, "SOUND_RVTHROW", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SHIELDHIT_ID, "SOUND_SHIELDHIT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SHIELDHIT2_ID, "SOUND_SHIELDHIT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BOSSEXPLOSION_ID, "SOUND_BOSSEXPLOSION", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CHERRYBOMB_ID, "SOUND_CHERRYBOMB", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BONK_ID, "SOUND_BONK", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SWING_ID, "SOUND_SWING", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_RAIN_ID, "SOUND_RAIN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LIGHTFILL_ID, "SOUND_LIGHTFILL", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLASTICHIT_ID, "SOUND_PLASTICHIT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLASTICHIT2_ID, "SOUND_PLASTICHIT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_JALAPENO_ID, "SOUND_JALAPENO", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BALLOONINFLATE_ID, "SOUND_BALLOONINFLATE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BIGCHOMP_ID, "SOUND_BIGCHOMP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_MELONIMPACT_ID, "SOUND_MELONIMPACT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_MELONIMPACT2_ID, "SOUND_MELONIMPACT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PLANTGROW_ID, "SOUND_PLANTGROW", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SHOOP_ID, "SOUND_SHOOP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_TAPGLASS_ID, "SOUND_TAPGLASS", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_JUICY_ID, "SOUND_JUICY", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_COFFEE_ID, "SOUND_COFFEE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_WAKEUP_ID, "SOUND_WAKEUP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LOWGROAN_ID, "SOUND_LOWGROAN", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_LOWGROAN2_ID, "SOUND_LOWGROAN2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PRIZE_ID, "SOUND_PRIZE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_YUCK_ID, "SOUND_YUCK", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_YUCK2_ID, "SOUND_YUCK2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GRASSSTEP_ID, "SOUND_GRASSSTEP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SHOVEL_ID, "SOUND_SHOVEL", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_COBLAUNCH_ID, "SOUND_COBLAUNCH", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_WATERING_ID, "SOUND_WATERING", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_POLEVAULT_ID, "SOUND_POLEVAULT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GRAVESTONE_RUMBLE_ID, "SOUND_GRAVESTONE_RUMBLE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DIRT_RISE_ID, "SOUND_DIRT_RISE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FERTILIZER_ID, "SOUND_FERTILIZER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PORTAL_ID, "SOUND_PORTAL", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BUGSPRAY_ID, "SOUND_BUGSPRAY", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_SCREAM_ID, "SOUND_SCREAM", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PAPER_ID, "SOUND_PAPER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_MONEYFALLS_ID, "SOUND_MONEYFALLS", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_IMP_ID, "SOUND_IMP", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_IMP2_ID, "SOUND_IMP2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_HYDRAULIC_SHORT_ID, "SOUND_HYDRAULIC_SHORT", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_HYDRAULIC_ID, "SOUND_HYDRAULIC", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_GARGANTUDEATH_ID, "SOUND_GARGANTUDEATH", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CERAMIC_ID, "SOUND_CERAMIC", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_BOSSBOULDERATTACK_ID, "SOUND_BOSSBOULDERATTACK", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CHIME_ID, "SOUND_CHIME", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVESHORT1_ID, "SOUND_CRAZYDAVESHORT1", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVESHORT2_ID, "SOUND_CRAZYDAVESHORT2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVESHORT3_ID, "SOUND_CRAZYDAVESHORT3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVELONG1_ID, "SOUND_CRAZYDAVELONG1", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVELONG2_ID, "SOUND_CRAZYDAVELONG2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVELONG3_ID, "SOUND_CRAZYDAVELONG3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVEEXTRALONG1_ID, "SOUND_CRAZYDAVEEXTRALONG1", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVEEXTRALONG2_ID, "SOUND_CRAZYDAVEEXTRALONG2", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVEEXTRALONG3_ID, "SOUND_CRAZYDAVEEXTRALONG3", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVECRAZY_ID, "SOUND_CRAZYDAVECRAZY", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_PHONOGRAPH_ID, "SOUND_PHONOGRAPH", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_DANCER_ID, "SOUND_DANCER", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_FINALFANFARE_ID, "SOUND_FINALFANFARE", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVESCREAM_ID, "SOUND_CRAZYDAVESCREAM", ResourceType.SOUND, "LoadingSounds"),
        new ResourceEntry(SOUND_CRAZYDAVESCREAM2_ID, "SOUND_CRAZYDAVESCREAM2", ResourceType.SOUND, "LoadingSounds"),
        // DelayLoad_Background1
        new ResourceEntry(IMAGE_BACKGROUND1_ID, "IMAGE_BACKGROUND1", ResourceType.IMAGE, "DelayLoad_Background1"),
        new ResourceEntry(IMAGE_BACKGROUND1_GAMEOVER_INTERIOR_OVERLAY_ID, "IMAGE_BACKGROUND1_GAMEOVER_INTERIOR_OVERLAY", ResourceType.IMAGE, "DelayLoad_Background1"),
        new ResourceEntry(IMAGE_BACKGROUND1_GAMEOVER_MASK_ID, "IMAGE_BACKGROUND1_GAMEOVER_MASK", ResourceType.IMAGE, "DelayLoad_Background1"),
        // DelayLoad_BackgroundUnsodded
        new ResourceEntry(IMAGE_BACKGROUND1UNSODDED_ID, "IMAGE_BACKGROUND1UNSODDED", ResourceType.IMAGE, "DelayLoad_BackgroundUnsodded"),
        new ResourceEntry(IMAGE_SOD1ROW_ID, "IMAGE_SOD1ROW", ResourceType.IMAGE, "DelayLoad_BackgroundUnsodded"),
        new ResourceEntry(IMAGE_SOD3ROW_ID, "IMAGE_SOD3ROW", ResourceType.IMAGE, "DelayLoad_BackgroundUnsodded"),
        // DelayLoad_Background2
        new ResourceEntry(IMAGE_BACKGROUND2_ID, "IMAGE_BACKGROUND2", ResourceType.IMAGE, "DelayLoad_Background2"),
        new ResourceEntry(IMAGE_BACKGROUND2_GAMEOVER_INTERIOR_OVERLAY_ID, "IMAGE_BACKGROUND2_GAMEOVER_INTERIOR_OVERLAY", ResourceType.IMAGE, "DelayLoad_Background2"),
        new ResourceEntry(IMAGE_BACKGROUND2_GAMEOVER_MASK_ID, "IMAGE_BACKGROUND2_GAMEOVER_MASK", ResourceType.IMAGE, "DelayLoad_Background2"),
        // DelayLoad_Background3
        new ResourceEntry(IMAGE_BACKGROUND3_ID, "IMAGE_BACKGROUND3", ResourceType.IMAGE, "DelayLoad_Background3"),
        new ResourceEntry(IMAGE_BACKGROUND3_GAMEOVER_INTERIOR_OVERLAY_ID, "IMAGE_BACKGROUND3_GAMEOVER_INTERIOR_OVERLAY", ResourceType.IMAGE, "DelayLoad_Background3"),
        new ResourceEntry(IMAGE_BACKGROUND3_GAMEOVER_MASK_ID, "IMAGE_BACKGROUND3_GAMEOVER_MASK", ResourceType.IMAGE, "DelayLoad_Background3"),
        // DelayLoad_Background4
        new ResourceEntry(IMAGE_BACKGROUND4_ID, "IMAGE_BACKGROUND4", ResourceType.IMAGE, "DelayLoad_Background4"),
        new ResourceEntry(IMAGE_BACKGROUND4_GAMEOVER_INTERIOR_OVERLAY_ID, "IMAGE_BACKGROUND4_GAMEOVER_INTERIOR_OVERLAY", ResourceType.IMAGE, "DelayLoad_Background4"),
        new ResourceEntry(IMAGE_BACKGROUND4_GAMEOVER_MASK_ID, "IMAGE_BACKGROUND4_GAMEOVER_MASK", ResourceType.IMAGE, "DelayLoad_Background4"),
        new ResourceEntry(IMAGE_FOG_ID, "IMAGE_FOG", ResourceType.IMAGE, "DelayLoad_Background4"),
        new ResourceEntry(IMAGE_FOG_SOFTWARE_ID, "IMAGE_FOG_SOFTWARE", ResourceType.IMAGE, "DelayLoad_Background4"),
        // DelayLoad_Background5
        new ResourceEntry(IMAGE_BACKGROUND5_ID, "IMAGE_BACKGROUND5", ResourceType.IMAGE, "DelayLoad_Background5"),
        new ResourceEntry(IMAGE_BACKGROUND5_GAMEOVER_MASK_ID, "IMAGE_BACKGROUND5_GAMEOVER_MASK", ResourceType.IMAGE, "DelayLoad_Background5"),
        // DelayLoad_Background6
        new ResourceEntry(IMAGE_BACKGROUND6BOSS_ID, "IMAGE_BACKGROUND6BOSS", ResourceType.IMAGE, "DelayLoad_Background6"),
        new ResourceEntry(IMAGE_BACKGROUND6_GAMEOVER_MASK_ID, "IMAGE_BACKGROUND6_GAMEOVER_MASK", ResourceType.IMAGE, "DelayLoad_Background6"),
        // DelayLoad_MushroomGarden
        new ResourceEntry(IMAGE_BACKGROUND_MUSHROOMGARDEN_ID, "IMAGE_BACKGROUND_MUSHROOMGARDEN", ResourceType.IMAGE, "DelayLoad_MushroomGarden"),
        // DelayLoad_GreenHouseGarden
        new ResourceEntry(IMAGE_BACKGROUND_GREENHOUSE_ID, "IMAGE_BACKGROUND_GREENHOUSE", ResourceType.IMAGE, "DelayLoad_GreenHouseGarden"),
        // DelayLoad_GreenHouseOverlay
        new ResourceEntry(IMAGE_BACKGROUND_GREENHOUSE_OVERLAY_ID, "IMAGE_BACKGROUND_GREENHOUSE_OVERLAY", ResourceType.IMAGE, "DelayLoad_GreenHouseOverlay"),
        // DelayLoad_Zombiquarium
        new ResourceEntry(IMAGE_AQUARIUM1_ID, "IMAGE_AQUARIUM1", ResourceType.IMAGE, "DelayLoad_Zombiquarium"),
        new ResourceEntry(IMAGE_WAVECENTER_ID, "IMAGE_WAVECENTER", ResourceType.IMAGE, "DelayLoad_Zombiquarium"),
        new ResourceEntry(IMAGE_WAVESIDE_ID, "IMAGE_WAVESIDE", ResourceType.IMAGE, "DelayLoad_Zombiquarium"),
        // DelayLoad_AwardScreen
        new ResourceEntry(IMAGE_AWARDSCREEN_BACK_ID, "IMAGE_AWARDSCREEN_BACK", ResourceType.IMAGE, "DelayLoad_AwardScreen"),
        // DelayLoad_ChallengeScreen
        new ResourceEntry(IMAGE_CHALLENGE_BACKGROUND_ID, "IMAGE_CHALLENGE_BACKGROUND", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_CHALLENGE_WINDOW_ID, "IMAGE_CHALLENGE_WINDOW", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_CHALLENGE_WINDOW_HIGHLIGHT_ID, "IMAGE_CHALLENGE_WINDOW_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_CHALLENGE_BLANK_ID, "IMAGE_CHALLENGE_BLANK", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_CHALLENGE_THUMBNAILS_ID, "IMAGE_CHALLENGE_THUMBNAILS", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_SURVIVAL_THUMBNAILS_ID, "IMAGE_SURVIVAL_THUMBNAILS", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_LOCK_ID, "IMAGE_LOCK", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        new ResourceEntry(IMAGE_LOCK_OPEN_ID, "IMAGE_LOCK_OPEN", ResourceType.IMAGE, "DelayLoad_ChallengeScreen"),
        // DelayLoad_Almanac
        new ResourceEntry(IMAGE_ALMANAC_INDEXBACK_ID, "IMAGE_ALMANAC_INDEXBACK", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_PLANTBACK_ID, "IMAGE_ALMANAC_PLANTBACK", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_ZOMBIEBACK_ID, "IMAGE_ALMANAC_ZOMBIEBACK", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_PLANTCARD_ID, "IMAGE_ALMANAC_PLANTCARD", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_ZOMBIECARD_ID, "IMAGE_ALMANAC_ZOMBIECARD", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_ZOMBIEWINDOW_ID, "IMAGE_ALMANAC_ZOMBIEWINDOW", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_ZOMBIEWINDOW2_ID, "IMAGE_ALMANAC_ZOMBIEWINDOW2", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_ZOMBIEBLANK_ID, "IMAGE_ALMANAC_ZOMBIEBLANK", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_GROUNDDAY_ID, "IMAGE_ALMANAC_GROUNDDAY", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_GROUNDNIGHT_ID, "IMAGE_ALMANAC_GROUNDNIGHT", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_GROUNDPOOL_ID, "IMAGE_ALMANAC_GROUNDPOOL", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_GROUNDNIGHTPOOL_ID, "IMAGE_ALMANAC_GROUNDNIGHTPOOL", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_GROUNDROOF_ID, "IMAGE_ALMANAC_GROUNDROOF", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_GROUNDICE_ID, "IMAGE_ALMANAC_GROUNDICE", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_CLOSEBUTTON_ID, "IMAGE_ALMANAC_CLOSEBUTTON", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_CLOSEBUTTONHIGHLIGHT_ID, "IMAGE_ALMANAC_CLOSEBUTTONHIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_INDEXBUTTON_ID, "IMAGE_ALMANAC_INDEXBUTTON", ResourceType.IMAGE, "DelayLoad_Almanac"),
        new ResourceEntry(IMAGE_ALMANAC_INDEXBUTTONHIGHLIGHT_ID, "IMAGE_ALMANAC_INDEXBUTTONHIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Almanac"),
        // DelayLoad_Store
        new ResourceEntry(IMAGE_STORE_BACKGROUND_ID, "IMAGE_STORE_BACKGROUND", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_BACKGROUNDNIGHT_ID, "IMAGE_STORE_BACKGROUNDNIGHT", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_CAR_ID, "IMAGE_STORE_CAR", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_CAR_NIGHT_ID, "IMAGE_STORE_CAR_NIGHT", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_CARCLOSED_ID, "IMAGE_STORE_CARCLOSED", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_CARCLOSED_NIGHT_ID, "IMAGE_STORE_CARCLOSED_NIGHT", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_HATCHBACKOPEN_ID, "IMAGE_STORE_HATCHBACKOPEN", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_SIGN_ID, "IMAGE_STORE_SIGN", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_MAINMENUBUTTON_ID, "IMAGE_STORE_MAINMENUBUTTON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_MAINMENUBUTTONDOWN_ID, "IMAGE_STORE_MAINMENUBUTTONDOWN", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_MAINMENUBUTTONHIGHLIGHT_ID, "IMAGE_STORE_MAINMENUBUTTONHIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_NEXTBUTTON_ID, "IMAGE_STORE_NEXTBUTTON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_NEXTBUTTONHIGHLIGHT_ID, "IMAGE_STORE_NEXTBUTTONHIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_NEXTBUTTONDISABLED_ID, "IMAGE_STORE_NEXTBUTTONDISABLED", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_PREVBUTTON_ID, "IMAGE_STORE_PREVBUTTON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_PREVBUTTONHIGHLIGHT_ID, "IMAGE_STORE_PREVBUTTONHIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_PREVBUTTONDISABLED_ID, "IMAGE_STORE_PREVBUTTONDISABLED", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_PRICETAG_ID, "IMAGE_STORE_PRICETAG", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_PACKETUPGRADE_ID, "IMAGE_STORE_PACKETUPGRADE", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_MUSHROOMGARDENICON_ID, "IMAGE_STORE_MUSHROOMGARDENICON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_AQUARIUMGARDENICON_ID, "IMAGE_STORE_AQUARIUMGARDENICON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_TREEOFWISDOMICON_ID, "IMAGE_STORE_TREEOFWISDOMICON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_FIRSTAIDWALLNUTICON_ID, "IMAGE_STORE_FIRSTAIDWALLNUTICON", ResourceType.IMAGE, "DelayLoad_Store"),
        new ResourceEntry(IMAGE_STORE_PVZICON_ID, "IMAGE_STORE_PVZICON", ResourceType.IMAGE, "DelayLoad_Store"),
        // DelayLoad_ZombieNote
        new ResourceEntry(IMAGE_ZOMBIE_NOTE_ID, "IMAGE_ZOMBIE_NOTE", ResourceType.IMAGE, "DelayLoad_ZombieNote"),
        // DelayLoad_ZombieNote1
        new ResourceEntry(IMAGE_ZOMBIE_NOTE1_ID, "IMAGE_ZOMBIE_NOTE1", ResourceType.IMAGE, "DelayLoad_ZombieNote1"),
        // DelayLoad_ZombieNote2
        new ResourceEntry(IMAGE_ZOMBIE_NOTE2_ID, "IMAGE_ZOMBIE_NOTE2", ResourceType.IMAGE, "DelayLoad_ZombieNote2"),
        // DelayLoad_ZombieNote3
        new ResourceEntry(IMAGE_ZOMBIE_NOTE3_ID, "IMAGE_ZOMBIE_NOTE3", ResourceType.IMAGE, "DelayLoad_ZombieNote3"),
        // DelayLoad_ZombieNote4
        new ResourceEntry(IMAGE_ZOMBIE_NOTE4_ID, "IMAGE_ZOMBIE_NOTE4", ResourceType.IMAGE, "DelayLoad_ZombieNote4"),
        // DelayLoad_ZombieFinalNote
        new ResourceEntry(IMAGE_ZOMBIE_FINAL_NOTE_ID, "IMAGE_ZOMBIE_FINAL_NOTE", ResourceType.IMAGE, "DelayLoad_ZombieFinalNote"),
        // DelayLoad_ZombieNoteHelp
        new ResourceEntry(IMAGE_ZOMBIE_NOTE_HELP_ID, "IMAGE_ZOMBIE_NOTE_HELP", ResourceType.IMAGE, "DelayLoad_ZombieNoteHelp"),
        // DelayLoad_Credits
        new ResourceEntry(FONT_BRIANNETOD32_ID, "FONT_BRIANNETOD32", ResourceType.FONT, "DelayLoad_Credits"),
        new ResourceEntry(FONT_BRIANNETOD32BLACK_ID, "FONT_BRIANNETOD32BLACK", ResourceType.FONT, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_SUNFLOWER_HEAD_SING1_ID, "IMAGE_REANIM_SUNFLOWER_HEAD_SING1", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_SUNFLOWER_HEAD_SING2_ID, "IMAGE_REANIM_SUNFLOWER_HEAD_SING2", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_SUNFLOWER_HEAD_SING3_ID, "IMAGE_REANIM_SUNFLOWER_HEAD_SING3", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_SUNFLOWER_HEAD_SING4_ID, "IMAGE_REANIM_SUNFLOWER_HEAD_SING4", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_SUNFLOWER_HEAD_SING5_ID, "IMAGE_REANIM_SUNFLOWER_HEAD_SING5", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_SUNFLOWER_HEAD_WINK_ID, "IMAGE_REANIM_SUNFLOWER_HEAD_WINK", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_CREDITS_DISCOLIGHTS_ID, "IMAGE_REANIM_CREDITS_DISCOLIGHTS", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_REANIM_CREDITS_FOGMACHINE_ID, "IMAGE_REANIM_CREDITS_FOGMACHINE", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_CREDITS_ZOMBIENOTE_ID, "IMAGE_CREDITS_ZOMBIENOTE", ResourceType.IMAGE, "DelayLoad_Credits"),
        new ResourceEntry(IMAGE_CREDITS_PLAYBUTTON_ID, "IMAGE_CREDITS_PLAYBUTTON", ResourceType.IMAGE, "DelayLoad_Credits"),
        // DelayLoad_Zombatar
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_BG_ID, "IMAGE_ZOMBATAR_ACCESSORY_BG", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_PREV_BUTTON_ID, "IMAGE_ZOMBATAR_PREV_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_PREV_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_PREV_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_NEXT_BUTTON_ID, "IMAGE_ZOMBATAR_NEXT_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_NEXT_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_NEXT_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ZOMBIE_BLANK_ID, "IMAGE_ZOMBATAR_ZOMBIE_BLANK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ZOMBIE_BLANK_SKIN_ID, "IMAGE_ZOMBATAR_ZOMBIE_BLANK_SKIN", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKGROUND_CRAZYDAVE_ID, "IMAGE_ZOMBATAR_BACKGROUND_CRAZYDAVE", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKGROUND_MENU_ID, "IMAGE_ZOMBATAR_BACKGROUND_MENU", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKGROUND_MENU_DOS_ID, "IMAGE_ZOMBATAR_BACKGROUND_MENU_DOS", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKGROUND_ROOF_ID, "IMAGE_ZOMBATAR_BACKGROUND_ROOF", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_WIDGET_BG_ID, "IMAGE_ZOMBATAR_WIDGET_BG", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_WIDGET_INNER_BG_ID, "IMAGE_ZOMBATAR_WIDGET_INNER_BG", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_COLORS_BG_ID, "IMAGE_ZOMBATAR_COLORS_BG", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_LOGO_ID, "IMAGE_ZOMBATAR_LOGO", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_BUTTON_ID, "IMAGE_ZOMBATAR_ACCESSORY_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_ACCESSORY_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_ACCESSORY_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKDROPS_BUTTON_ID, "IMAGE_ZOMBATAR_BACKDROPS_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKDROPS_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_BACKDROPS_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKDROPS_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_BACKDROPS_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_BUTTON_ID, "IMAGE_ZOMBATAR_CLOTHES_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_CLOTHES_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_CLOTHES_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_BUTTON_ID, "IMAGE_ZOMBATAR_EYEWEAR_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_EYEWEAR_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_EYEWEAR_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_ID, "IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_FACIAL_HAIR_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_BUTTON_ID, "IMAGE_ZOMBATAR_HAIR_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_HAIR_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_HAIR_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_BUTTON_ID, "IMAGE_ZOMBATAR_HATS_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_HATS_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_HATS_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_SKIN_BUTTON_ID, "IMAGE_ZOMBATAR_SKIN_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_SKIN_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_SKIN_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_BUTTON_ID, "IMAGE_ZOMBATAR_TIDBITS_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_TIDBITS_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_BUTTON_OVER_ID, "IMAGE_ZOMBATAR_TIDBITS_BUTTON_OVER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_BG_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_ACCESSORY_BG_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TRIPTYCH_ID, "IMAGE_ZOMBATAR_TRIPTYCH", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_COLORPICKER_ID, "IMAGE_ZOMBATAR_COLORPICKER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_COLORPICKER_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_COLORPICKER_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_COLORPICKER_NONE_ID, "IMAGE_ZOMBATAR_COLORPICKER_NONE", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_BG_NONE_ID, "IMAGE_ZOMBATAR_ACCESSORY_BG_NONE", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_COLORS_BG_SMALL_ID, "IMAGE_ZOMBATAR_COLORS_BG_SMALL", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_1_ID, "IMAGE_ZOMBATAR_CLOTHES_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_2_ID, "IMAGE_ZOMBATAR_CLOTHES_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_3_ID, "IMAGE_ZOMBATAR_CLOTHES_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_4_ID, "IMAGE_ZOMBATAR_CLOTHES_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_5_ID, "IMAGE_ZOMBATAR_CLOTHES_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_6_ID, "IMAGE_ZOMBATAR_CLOTHES_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_7_ID, "IMAGE_ZOMBATAR_CLOTHES_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_8_ID, "IMAGE_ZOMBATAR_CLOTHES_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_9_ID, "IMAGE_ZOMBATAR_CLOTHES_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_10_ID, "IMAGE_ZOMBATAR_CLOTHES_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_11_ID, "IMAGE_ZOMBATAR_CLOTHES_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_CLOTHES_12_ID, "IMAGE_ZOMBATAR_CLOTHES_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_1_ID, "IMAGE_ZOMBATAR_HATS_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_2_ID, "IMAGE_ZOMBATAR_HATS_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_3_ID, "IMAGE_ZOMBATAR_HATS_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_4_ID, "IMAGE_ZOMBATAR_HATS_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_5_ID, "IMAGE_ZOMBATAR_HATS_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_6_ID, "IMAGE_ZOMBATAR_HATS_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_7_ID, "IMAGE_ZOMBATAR_HATS_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_8_ID, "IMAGE_ZOMBATAR_HATS_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_9_ID, "IMAGE_ZOMBATAR_HATS_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_10_ID, "IMAGE_ZOMBATAR_HATS_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_11_ID, "IMAGE_ZOMBATAR_HATS_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_12_ID, "IMAGE_ZOMBATAR_HATS_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_13_ID, "IMAGE_ZOMBATAR_HATS_13", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_14_ID, "IMAGE_ZOMBATAR_HATS_14", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_1_ID, "IMAGE_ZOMBATAR_HAIR_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_2_ID, "IMAGE_ZOMBATAR_HAIR_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_3_ID, "IMAGE_ZOMBATAR_HAIR_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_4_ID, "IMAGE_ZOMBATAR_HAIR_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_5_ID, "IMAGE_ZOMBATAR_HAIR_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_6_ID, "IMAGE_ZOMBATAR_HAIR_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_7_ID, "IMAGE_ZOMBATAR_HAIR_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_8_ID, "IMAGE_ZOMBATAR_HAIR_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_9_ID, "IMAGE_ZOMBATAR_HAIR_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_10_ID, "IMAGE_ZOMBATAR_HAIR_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_11_ID, "IMAGE_ZOMBATAR_HAIR_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_12_ID, "IMAGE_ZOMBATAR_HAIR_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_13_ID, "IMAGE_ZOMBATAR_HAIR_13", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_14_ID, "IMAGE_ZOMBATAR_HAIR_14", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_15_ID, "IMAGE_ZOMBATAR_HAIR_15", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_16_ID, "IMAGE_ZOMBATAR_HAIR_16", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_1_ID, "IMAGE_ZOMBATAR_EYEWEAR_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_2_ID, "IMAGE_ZOMBATAR_EYEWEAR_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_3_ID, "IMAGE_ZOMBATAR_EYEWEAR_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_4_ID, "IMAGE_ZOMBATAR_EYEWEAR_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_5_ID, "IMAGE_ZOMBATAR_EYEWEAR_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_6_ID, "IMAGE_ZOMBATAR_EYEWEAR_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_7_ID, "IMAGE_ZOMBATAR_EYEWEAR_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_8_ID, "IMAGE_ZOMBATAR_EYEWEAR_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_9_ID, "IMAGE_ZOMBATAR_EYEWEAR_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_10_ID, "IMAGE_ZOMBATAR_EYEWEAR_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_11_ID, "IMAGE_ZOMBATAR_EYEWEAR_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_12_ID, "IMAGE_ZOMBATAR_EYEWEAR_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_13_ID, "IMAGE_ZOMBATAR_EYEWEAR_13", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_14_ID, "IMAGE_ZOMBATAR_EYEWEAR_14", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_15_ID, "IMAGE_ZOMBATAR_EYEWEAR_15", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_16_ID, "IMAGE_ZOMBATAR_EYEWEAR_16", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_1_ID, "IMAGE_ZOMBATAR_FACIALHAIR_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_2_ID, "IMAGE_ZOMBATAR_FACIALHAIR_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_3_ID, "IMAGE_ZOMBATAR_FACIALHAIR_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_4_ID, "IMAGE_ZOMBATAR_FACIALHAIR_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_5_ID, "IMAGE_ZOMBATAR_FACIALHAIR_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_6_ID, "IMAGE_ZOMBATAR_FACIALHAIR_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_7_ID, "IMAGE_ZOMBATAR_FACIALHAIR_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_8_ID, "IMAGE_ZOMBATAR_FACIALHAIR_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_9_ID, "IMAGE_ZOMBATAR_FACIALHAIR_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_10_ID, "IMAGE_ZOMBATAR_FACIALHAIR_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_11_ID, "IMAGE_ZOMBATAR_FACIALHAIR_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_12_ID, "IMAGE_ZOMBATAR_FACIALHAIR_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_13_ID, "IMAGE_ZOMBATAR_FACIALHAIR_13", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_14_ID, "IMAGE_ZOMBATAR_FACIALHAIR_14", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_15_ID, "IMAGE_ZOMBATAR_FACIALHAIR_15", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_16_ID, "IMAGE_ZOMBATAR_FACIALHAIR_16", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_17_ID, "IMAGE_ZOMBATAR_FACIALHAIR_17", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_18_ID, "IMAGE_ZOMBATAR_FACIALHAIR_18", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_19_ID, "IMAGE_ZOMBATAR_FACIALHAIR_19", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_20_ID, "IMAGE_ZOMBATAR_FACIALHAIR_20", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_21_ID, "IMAGE_ZOMBATAR_FACIALHAIR_21", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_22_ID, "IMAGE_ZOMBATAR_FACIALHAIR_22", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_23_ID, "IMAGE_ZOMBATAR_FACIALHAIR_23", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_24_ID, "IMAGE_ZOMBATAR_FACIALHAIR_24", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_1_ID, "IMAGE_ZOMBATAR_TIDBITS_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_2_ID, "IMAGE_ZOMBATAR_TIDBITS_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_3_ID, "IMAGE_ZOMBATAR_TIDBITS_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_4_ID, "IMAGE_ZOMBATAR_TIDBITS_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_5_ID, "IMAGE_ZOMBATAR_TIDBITS_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_6_ID, "IMAGE_ZOMBATAR_TIDBITS_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_7_ID, "IMAGE_ZOMBATAR_TIDBITS_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_8_ID, "IMAGE_ZOMBATAR_TIDBITS_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_9_ID, "IMAGE_ZOMBATAR_TIDBITS_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_10_ID, "IMAGE_ZOMBATAR_TIDBITS_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_11_ID, "IMAGE_ZOMBATAR_TIDBITS_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_12_ID, "IMAGE_ZOMBATAR_TIDBITS_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_13_ID, "IMAGE_ZOMBATAR_TIDBITS_13", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TIDBITS_14_ID, "IMAGE_ZOMBATAR_TIDBITS_14", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_1_ID, "IMAGE_ZOMBATAR_ACCESSORY_1", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_2_ID, "IMAGE_ZOMBATAR_ACCESSORY_2", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_3_ID, "IMAGE_ZOMBATAR_ACCESSORY_3", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_4_ID, "IMAGE_ZOMBATAR_ACCESSORY_4", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_5_ID, "IMAGE_ZOMBATAR_ACCESSORY_5", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_6_ID, "IMAGE_ZOMBATAR_ACCESSORY_6", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_7_ID, "IMAGE_ZOMBATAR_ACCESSORY_7", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_8_ID, "IMAGE_ZOMBATAR_ACCESSORY_8", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_9_ID, "IMAGE_ZOMBATAR_ACCESSORY_9", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_10_ID, "IMAGE_ZOMBATAR_ACCESSORY_10", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_11_ID, "IMAGE_ZOMBATAR_ACCESSORY_11", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_12_ID, "IMAGE_ZOMBATAR_ACCESSORY_12", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_13_ID, "IMAGE_ZOMBATAR_ACCESSORY_13", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_14_ID, "IMAGE_ZOMBATAR_ACCESSORY_14", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_1_MASK_ID, "IMAGE_ZOMBATAR_HATS_1_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_1_MASK_ID, "IMAGE_ZOMBATAR_HAIR_1_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_2_MASK_ID, "IMAGE_ZOMBATAR_HAIR_2_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_11_MASK_ID, "IMAGE_ZOMBATAR_HAIR_11_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_12_MASK_ID, "IMAGE_ZOMBATAR_HAIR_12_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_13_MASK_ID, "IMAGE_ZOMBATAR_HAIR_13_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_14_MASK_ID, "IMAGE_ZOMBATAR_HAIR_14_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HAIR_15_MASK_ID, "IMAGE_ZOMBATAR_HAIR_15_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_1_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_1_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_4_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_4_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_8_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_8_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_9_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_9_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_10_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_10_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_11_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_11_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_12_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_12_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_14_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_14_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_15_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_15_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_16_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_16_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_18_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_18_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_21_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_21_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_22_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_22_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_23_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_23_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FACIALHAIR_24_MASK_ID, "IMAGE_ZOMBATAR_FACIALHAIR_24_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_1_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_1_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_2_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_2_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_3_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_3_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_4_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_4_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_5_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_5_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_6_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_6_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_7_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_7_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_8_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_8_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_9_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_9_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_10_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_10_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_11_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_11_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_EYEWEAR_12_MASK_ID, "IMAGE_ZOMBATAR_EYEWEAR_12_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_3_MASK_ID, "IMAGE_ZOMBATAR_HATS_3_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_6_MASK_ID, "IMAGE_ZOMBATAR_HATS_6_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_7_MASK_ID, "IMAGE_ZOMBATAR_HATS_7_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_8_MASK_ID, "IMAGE_ZOMBATAR_HATS_8_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_9_MASK_ID, "IMAGE_ZOMBATAR_HATS_9_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_HATS_11_MASK_ID, "IMAGE_ZOMBATAR_HATS_11_MASK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACKGROUND_BLANK_ID, "IMAGE_ZOMBATAR_BACKGROUND_BLANK", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_15_ID, "IMAGE_ZOMBATAR_ACCESSORY_15", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCESSORY_16_ID, "IMAGE_ZOMBATAR_ACCESSORY_16", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCEPT_BUTTON_ID, "IMAGE_ZOMBATAR_ACCEPT_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_ACCEPT_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_ACCEPT_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FINISHED_BUTTON_ID, "IMAGE_ZOMBATAR_FINISHED_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_FINISHED_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_FINISHED_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_MAIN_BG_ID, "IMAGE_ZOMBATAR_MAIN_BG", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACK_BUTTON_ID, "IMAGE_ZOMBATAR_BACK_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BACK_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_BACK_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_ID, "IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_NEWZOMBIE_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TOS_ARROW_ID, "IMAGE_ZOMBATAR_TOS_ARROW", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BEGIN_BUTTON_ID, "IMAGE_ZOMBATAR_BEGIN_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_BEGIN_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_BEGIN_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TOS_SLIDER_ID, "IMAGE_ZOMBATAR_TOS_SLIDER", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_TOS_SLIDER_THUMB_ID, "IMAGE_ZOMBATAR_TOS_SLIDER_THUMB", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_DISPLAY_WINDOW_ID, "IMAGE_ZOMBATAR_DISPLAY_WINDOW", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_MAINMENUBACK_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_MAINMENUBACK_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_VIEW_BUTTON_ID, "IMAGE_ZOMBATAR_VIEW_BUTTON", ResourceType.IMAGE, "DelayLoad_Zombatar"),
        new ResourceEntry(IMAGE_ZOMBATAR_VIEW_BUTTON_HIGHLIGHT_ID, "IMAGE_ZOMBATAR_VIEW_BUTTON_HIGHLIGHT", ResourceType.IMAGE, "DelayLoad_Zombatar"),
    };

    /** Group name &rarr; entries in id order; group order follows resources.xml / the enum. */
    public static final Map<String, List<ResourceEntry>> GROUPS;

    static {
        Map<String, List<ResourceEntry>> groups = new LinkedHashMap<>();
        for (ResourceEntry entry : ENTRIES) {
            groups.computeIfAbsent(entry.group(), key -> new ArrayList<>()).add(entry);
        }
        Map<String, List<ResourceEntry>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<ResourceEntry>> e : groups.entrySet()) {
            immutable.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        GROUPS = Collections.unmodifiableMap(immutable);
    }

    /**
     * Ported from src/Resources.cpp:2319-2321 (void* gResources[RESOURCE_ID_MAX]).
     *
     * <p>Java has no pointer-to-variable; the loaded objects themselves are stored
     * at their id index (sounds as boxed {@link Long}).
     */
    public static final Object[] gResources = new Object[RESOURCE_ID_MAX];

    // =====================================================================
    // Table helpers (operate purely on the tables above)
    // =====================================================================

    /** Name string of the resource with the given id, or {@code null} when out of range. */
    public static String getName(int theId) {
        return theId >= 0 && theId < RESOURCE_ID_MAX ? ENTRIES[theId].name() : null;
    }

    /** Kind of the resource with the given id, or {@code null} when out of range. */
    public static ResourceType getType(int theId) {
        return theId >= 0 && theId < RESOURCE_ID_MAX ? ENTRIES[theId].type() : null;
    }

    /** Extraction group of the resource with the given id, or {@code null} when out of range. */
    public static String getGroup(int theId) {
        return theId >= 0 && theId < RESOURCE_ID_MAX ? ENTRIES[theId].group() : null;
    }

    /** Reverse lookup: id for a resource name, or {@link #RESOURCE_ID_MAX} when unknown. */
    public static int getIdByName(String theName) {
        if (theName != null) {
            for (ResourceEntry entry : ENTRIES) {
                if (entry.name().equals(theName)) {
                    return entry.id();
                }
            }
        }
        return RESOURCE_ID_MAX;
    }

    public static boolean isImage(int theId) {
        return getType(theId) == ResourceType.IMAGE;
    }

    public static boolean isSound(int theId) {
        return getType(theId) == ResourceType.SOUND;
    }

    public static boolean isFont(int theId) {
        return getType(theId) == ResourceType.FONT;
    }

    /** Group names in resources.xml order. */
    public static List<String> getGroupNames() {
        return List.copyOf(GROUPS.keySet());
    }

    /** Entries of a group in id order, or {@code null} when the group is unknown. */
    public static List<ResourceEntry> getGroupEntries(String theGroup) {
        return GROUPS.get(theGroup);
    }

    /**
     * Ported from ResourceManager::GetNumResources semantics: number of resources
     * bound to a group, or 0 when the group is unknown.
     */
    public static int getNumResources(String theGroup) {
        List<ResourceEntry> entries = GROUPS.get(theGroup);
        return entries == null ? 0 : entries.size();
    }

    /** Number of resources of a kind in the whole table. */
    public static int getNumResources(ResourceType theType) {
        int count = 0;
        for (ResourceEntry entry : ENTRIES) {
            if (entry.type() == theType) {
                count++;
            }
        }
        return count;
    }

    // =====================================================================
    // Loader dispatch (Resources.cpp:31-61, ExtractResourcesByName and friends)
    // =====================================================================

    /**
     * Loads every resource of a group and binds the results into {@link #gResources}.
     *
     * <p>Collapses the 29 upstream {@code Extract<Group>Resources} functions into a
     * single table-driven dispatch. Returns false when a resource is missing,
     * mirroring the upstream {@code catch (ResourceManagerException&) { return false; }}.
     *
     * @throws AssertionError for an unknown group name (upstream {@code unreachable()})
     */
    public static boolean extractResourcesByName(ResourceManager theResourceManager, String theName) {
        List<ResourceEntry> entries = GROUPS.get(theName);
        if (entries == null) {
            throw new AssertionError("unreachable: unknown resource group " + theName);
        }

        gNeedRecalcVariableToIdMap = true;
        try {
            for (ResourceEntry entry : entries) {
                switch (entry.type()) {
                    case IMAGE -> gResources[entry.id()] =
                            theResourceManager.getImageThrow(entry.name()).get();
                    case SOUND -> gResources[entry.id()] = theResourceManager.getSoundThrow(entry.name());
                    case FONT -> gResources[entry.id()] = theResourceManager.getFontThrow(entry.name());
                }
            }
        } catch (ResourceManagerException e) {
            return false;
        }
        return true;
    }

    // =====================================================================
    // Bound-resource accessors (Resources.cpp:3204-3231, *ById / *RefById)
    // =====================================================================

    /** Ported from src/Resources.cpp:3204 (GetImageById). */
    public static Image getImageById(int theId) {
        return (Image) gResources[theId];
    }

    /** Ported from src/Resources.cpp:3209 (GetFontById). */
    public static Font getFontById(int theId) {
        return (Font) gResources[theId];
    }

    /** Ported from src/Resources.cpp:3214 (GetSoundById). */
    public static long getSoundById(int theId) {
        Object value = gResources[theId];
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * Ported from src/Resources.cpp:3219 (GetImageRefById). Upstream returns a
     * reference that callers assign through; Java returns the value, use
     * {@link #setImageById} to assign.
     */
    public static Image getImageRefById(int theId) {
        return getImageById(theId);
    }

    /** Ported from src/Resources.cpp:3224 (GetFontRefById); see {@link #getImageRefById}. */
    public static Font getFontRefById(int theId) {
        return getFontById(theId);
    }

    /** Ported from src/Resources.cpp:3229 (GetSoundRefById); see {@link #getImageRefById}. */
    public static long getSoundRefById(int theId) {
        return getSoundById(theId);
    }

    /** Assignment counterpart of GetImageRefById for Java. */
    public static void setImageById(int theId, Image theImage) {
        gResources[theId] = theImage;
    }

    /** Assignment counterpart of GetFontRefById for Java. */
    public static void setFontById(int theId, Font theFont) {
        gResources[theId] = theFont;
    }

    /** Assignment counterpart of GetSoundRefById for Java. */
    public static void setSoundById(int theId, long theSound) {
        gResources[theId] = theSound;
    }

    // =====================================================================
    // Reverse lookups (Resources.cpp:3236-3273, GetIdBy* / GetIdByVariable)
    // =====================================================================

    private static Map<Object, Integer> sVariableToIdMap;
    private static Map<Long, Integer> sSoundToIdMap;

    /** Object identity for images/fonts and value equality for sounds (boxed ids). */
    private static void rebuildVariableToIdMap() {
        Map<Object, Integer> variables = new IdentityHashMap<>();
        Map<Long, Integer> sounds = new LinkedHashMap<>();
        for (ResourceEntry entry : ENTRIES) {
            Object value = gResources[entry.id()];
            if (value == null) {
                continue;
            }
            if (entry.type() == ResourceType.SOUND) {
                sounds.putIfAbsent((Long) value, entry.id());
            } else {
                variables.put(value, entry.id());
            }
        }
        sVariableToIdMap = variables;
        sSoundToIdMap = sounds;
        gNeedRecalcVariableToIdMap = false;
    }

    /** Ported from src/Resources.cpp:3249 (GetIdByVariable). */
    public static int getIdByVariable(Object theVariable) {
        if (theVariable == null) {
            return RESOURCE_ID_MAX;
        }
        if (gNeedRecalcVariableToIdMap || sVariableToIdMap == null) {
            rebuildVariableToIdMap();
        }
        if (theVariable instanceof Long sound) {
            Integer id = sSoundToIdMap.get(sound);
            return id == null ? RESOURCE_ID_MAX : id;
        }
        Integer id = sVariableToIdMap.get(theVariable);
        return id == null ? RESOURCE_ID_MAX : id;
    }

    /** Ported from src/Resources.cpp:3236 (GetIdByImage). */
    public static int getIdByImage(Image theImage) {
        return getIdByVariable(theImage);
    }

    /** Ported from src/Resources.cpp:3241 (GetIdByFont). */
    public static int getIdByFont(Font theFont) {
        return getIdByVariable(theFont);
    }

    /** Ported from src/Resources.cpp:3246 (GetIdBySound). */
    public static int getIdBySound(long theSound) {
        return getIdByVariable(theSound);
    }
}

