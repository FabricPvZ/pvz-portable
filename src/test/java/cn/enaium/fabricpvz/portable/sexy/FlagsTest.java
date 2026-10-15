package cn.enaium.fabricpvz.portable.sexy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Flags}, {@link FlagsMod}, {@link ModalFlags} and {@link AutoModalFlags},
 * covering the add/remove/has semantics of the integer mask helpers and the set-based generic
 * flag container.
 */
class FlagsTest {

    @Test
    void genericFlagsAddHasRemove() {
        Flags<String> flags = new Flags<>();
        assertTrue(flags.isEmpty());

        flags.addFlags("A");
        flags.addFlags("B");
        assertEquals(2, flags.size());
        assertTrue(flags.hasFlags("A"));
        assertTrue(flags.hasFlags(List.of("A", "B")));
        assertFalse(flags.hasFlags(List.of("A", "C")));
        assertTrue(flags.hasAnyFlags(List.of("C", "B")));
        assertFalse(flags.hasAnyFlags(List.of("C", "D")));

        flags.removeFlags("A");
        assertFalse(flags.hasFlags("A"));
        assertEquals(1, flags.size());

        flags.addFlags(List.of("X", "Y"));
        assertTrue(flags.hasFlags(List.of("B", "X", "Y")));

        flags.removeFlags(List.of("X", "Y"));
        assertEquals(1, flags.size());

        Set<String> view = flags.getFlags();
        assertEquals(Set.of("B"), view);
        assertThrows(UnsupportedOperationException.class, () -> view.add("Z"));

        flags.clear();
        assertTrue(flags.isEmpty());
    }

    @Test
    void genericFlagsConstructorSeedsTheSet() {
        Flags<Integer> flags = new Flags<>(List.of(1, 2, 3));
        assertTrue(flags.hasFlags(List.of(1, 2, 3)));
        assertEquals(3, flags.size());
    }

    @Test
    void flagsModAppliesAddThenRemove() {
        FlagsMod mod = new FlagsMod(0b0101, 0b0010);

        // (1010 | 0101) & ~0010 == 1111 & 1101 == 1101
        assertEquals(0b1101, mod.modFlags(0b1010));
        // The removing mask wins over the adding mask.
        assertEquals(0b0101, mod.modFlags(0b0010));
    }

    @Test
    void getModFlagsMatchesInstanceForm() {
        FlagsMod mod = new FlagsMod(0b0101, 0b0010);
        assertEquals(mod.modFlags(0b1010), FlagsMod.getModFlags(0b1010, mod));

        FlagsMod empty = new FlagsMod();
        assertEquals(0x1234, FlagsMod.getModFlags(0x1234, empty));
    }

    @Test
    void modalFlagsApplyToBothWordsAndSelectByIsOver() {
        ModalFlags modal = new ModalFlags();
        modal.mOverFlags = 0b1000;
        modal.mUnderFlags = 0b0001;

        modal.mIsOver = true;
        assertEquals(0b1000, modal.getFlags());

        modal.modFlags(new FlagsMod(0b0101, 0b0010));
        assertEquals(0b1101, modal.mOverFlags);   // (1000|0101)&~0010
        assertEquals(0b0101, modal.mUnderFlags);  // (0001|0101)&~0010
        assertEquals(0b1101, modal.getFlags());

        modal.mIsOver = false;
        assertEquals(0b0101, modal.getFlags());
    }

    @Test
    void autoModalFlagsAppliesOnConstructionAndRestoresOnClose() {
        ModalFlags modal = new ModalFlags();
        modal.mOverFlags = 0b0000;
        modal.mUnderFlags = 0b1111;

        try (AutoModalFlags auto = new AutoModalFlags(modal, new FlagsMod(0b0110, 0b0001))) {
            assertEquals(0b0110, modal.mOverFlags);
            assertEquals(0b1110, modal.mUnderFlags);
        }

        assertEquals(0b0000, modal.mOverFlags);
        assertEquals(0b1111, modal.mUnderFlags);
    }
}
