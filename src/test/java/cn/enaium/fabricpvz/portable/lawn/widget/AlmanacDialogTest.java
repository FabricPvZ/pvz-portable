package cn.enaium.fabricpvz.portable.lawn.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.AlmanacPage;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpStringFile;
import cn.enaium.fabricpvz.portable.sexy.Point;
import cn.enaium.fabricpvz.portable.test.HeadlessTestApp;

/**
 * Headless verification of the almanac: page navigation rules, the fixed plant/zombie grids and
 * the stat/description lines built from the definition tables and LawnStrings keys.
 *
 * <p>Only the creating of the dialog needs {@code LawnApp}, resources and reanims, so these tests
 * cover the static rules and the definition-table driven strings (each annotated with its upstream
 * {@code AlmanacDialog.cpp} line).
 */
class AlmanacDialogTest {

    /**
     * Builds the app whose string properties the stat lines translate against, and banks the keys
     * the almanac substitutes ({@code AlmanacDialog.cpp:339-351} runs {@code {COST}}/{@code WAIT_TIME}
     * through {@code PvzpStringTranslate}), exactly as {@code Properties/LawnStrings.txt} does.
     */
    @BeforeAll
    static void installLawnStrings() {
        LawnApp anApp = new LawnApp();
        HeadlessTestApp.initialize(anApp);
        PvzpStringFile.readItems("[COST]\nCost\n\n[WAIT_TIME]\nWaiting time\n\n"
                + "[WAIT_TIME_SHORT]\nFast\n\n[WAIT_TIME_LONG]\nSlow\n\n"
                + "[WAIT_TIME_VERY_LONG]\nVery slow\n\n");
    }

    @Test
    void pageButtonsFollowTheIndexRule() {
        // index page: only the index button is hidden
        assertTrue(AlmanacDialog.indexButtonHidden(AlmanacPage.ALMANAC_PAGE_INDEX));
        assertFalse(AlmanacDialog.plantButtonHidden(AlmanacPage.ALMANAC_PAGE_INDEX));
        assertFalse(AlmanacDialog.zombieButtonHidden(AlmanacPage.ALMANAC_PAGE_INDEX));

        // content pages: the index button returns, the two content buttons are hidden
        for (int page : new int[] {AlmanacPage.ALMANAC_PAGE_PLANTS, AlmanacPage.ALMANAC_PAGE_ZOMBIES}) {
            assertFalse(AlmanacDialog.indexButtonHidden(page));
            assertTrue(AlmanacDialog.plantButtonHidden(page));
            assertTrue(AlmanacDialog.zombieButtonHidden(page));
        }
    }

    @Test
    void plantGridIsEightColumnsWithImitaterSlotInTheCorner() {
        Point p = new Point();

        AlmanacDialog.getSeedPosition(SeedType.SEED_PEASHOOTER, p);
        assertEquals(26, p.mX);
        assertEquals(92, p.mY);

        AlmanacDialog.getSeedPosition(7, p);
        assertEquals(7 * 52 + 26, p.mX);
        assertEquals(92, p.mY);

        AlmanacDialog.getSeedPosition(8, p);
        assertEquals(26, p.mX);
        assertEquals(78 + 92, p.mY);

        // the imitater is drawn at the fixed slot in the top-left corner
        AlmanacDialog.getSeedPosition(SeedType.SEED_IMITATER, p);
        assertEquals(20, p.mX);
        assertEquals(23, p.mY);
    }

    @Test
    void zombieGridIsFiveColumnsWithBossSlotAtTheBottom() {
        Point p = new Point();

        AlmanacDialog.getZombiePosition(ZombieType.ZOMBIE_NORMAL, p);
        assertEquals(22, p.mX);
        assertEquals(86, p.mY);

        AlmanacDialog.getZombiePosition(4, p);
        assertEquals(4 * 85 + 22, p.mX);
        assertEquals(86, p.mY);

        AlmanacDialog.getZombiePosition(5, p);
        assertEquals(22, p.mX);
        assertEquals(80 + 86, p.mY);

        AlmanacDialog.getZombiePosition(ZombieType.ZOMBIE_BOSS, p);
        assertEquals(192, p.mX);
        assertEquals(486, p.mY);
    }

    @Test
    void zombieIndexTableCoversTheAlmanacSlots() {
        for (int i = 0; i < AlmanacDialog.NUM_ALMANAC_ZOMBIES; i++) {
            assertEquals(i, AlmanacDialog.getZombieType(i));
        }
        // AlmanacDialog.cpp:252-255: the guard is NUM_ZOMBIE_TYPES, not the 26 almanac slots, so
        // slot 26 still maps to ZOMBIE_PEA_HEAD and only NUM_ZOMBIE_TYPES itself is invalid.
        assertEquals(ZombieType.ZOMBIE_PEA_HEAD, AlmanacDialog.getZombieType(AlmanacDialog.NUM_ALMANAC_ZOMBIES));
        assertEquals(ZombieType.ZOMBIE_INVALID, AlmanacDialog.getZombieType(ZombieType.NUM_ZOMBIE_TYPES));
    }

    @Test
    void plantStatStringsComeFromTheDefinitionTable() {
        PlantDefinition peashooter = Plant.getPlantDefinition(SeedType.SEED_PEASHOOTER);
        assertEquals("[PEASHOOTER_DESCRIPTION]", AlmanacDialog.getPlantDescriptionKey(peashooter));
        // AlmanacDialog.cpp:343: {COST} is replaced by the *translated* [COST] LawnStrings key, so the
        // stat line reads "Cost: 100" once installLawnStrings() has banked the localized value.
        assertEquals("{KEYWORD}Cost:{STAT} 100", AlmanacDialog.getPlantCostString(peashooter));
        assertEquals("{KEYWORD}Waiting time: {STAT}Fast",
                AlmanacDialog.getPlantRechargeString(peashooter));

        PlantDefinition wallnut = Plant.getPlantDefinition(SeedType.SEED_WALLNUT);
        assertEquals("[WALL_NUT_DESCRIPTION]", AlmanacDialog.getPlantDescriptionKey(wallnut));
        assertEquals("{KEYWORD}Cost:{STAT} 50", AlmanacDialog.getPlantCostString(wallnut));
        assertEquals("{KEYWORD}Waiting time: {STAT}Slow",
                AlmanacDialog.getPlantRechargeString(wallnut));

        PlantDefinition cherryBomb = Plant.getPlantDefinition(SeedType.SEED_CHERRYBOMB);
        assertEquals("[CHERRY_BOMB_DESCRIPTION]", AlmanacDialog.getPlantDescriptionKey(cherryBomb));
        assertEquals("{KEYWORD}Cost:{STAT} 150", AlmanacDialog.getPlantCostString(cherryBomb));
        assertEquals("{KEYWORD}Waiting time: {STAT}Very slow",
                AlmanacDialog.getPlantRechargeString(cherryBomb));
    }

    @Test
    void zombieNameAndDescriptionKeysComeFromTheDefinitionTable() {
        ZombieDefinition normal = Zombie.getZombieDefinition(ZombieType.ZOMBIE_NORMAL);
        assertEquals("[ZOMBIE_DESCRIPTION]", AlmanacDialog.getZombieDescriptionKey(normal));
        assertEquals("[ZOMBIE]", AlmanacDialog.getZombieNameString(normal, false));
        assertEquals("???", AlmanacDialog.getZombieNameString(normal, true));

        ZombieDefinition yeti = Zombie.getZombieDefinition(ZombieType.ZOMBIE_YETI);
        assertEquals("[ZOMBIE_YETI_DESCRIPTION]", AlmanacDialog.getZombieDescriptionKey(yeti));
        assertEquals("[ZOMBIE_YETI]", AlmanacDialog.getZombieNameString(yeti, false));
    }

    @Test
    void defeatedZombieBookkeeping() {
        AlmanacDialog.almanacInitForPlayer();
        assertFalse(AlmanacDialog.gZombieDefeated[ZombieType.ZOMBIE_NORMAL]);

        AlmanacDialog.almanacPlayerDefeatedZombie(ZombieType.ZOMBIE_NORMAL);
        assertTrue(AlmanacDialog.gZombieDefeated[ZombieType.ZOMBIE_NORMAL]);

        AlmanacDialog.almanacInitForPlayer();
        assertFalse(AlmanacDialog.gZombieDefeated[ZombieType.ZOMBIE_NORMAL]);
    }
}
