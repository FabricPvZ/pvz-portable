package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for {@link DataArray}, covering the slot/free-list/handle semantics of
 * src/PvzpLib/DataArray.h that the board relies on (stable ids across deletions, live-slot-only
 * iteration, unused slots).
 */
class DataArrayTest {

    /** Stand-in for an upstream entity (DataArrayItem : T with value initialisation). */
    static final class Item {
        int mValue;
    }

    private static DataArray<Item> newArray(int theMaxSize) {
        return new DataArray<>(Item::new, theMaxSize, "test");
    }

    private static List<Item> live(DataArray<Item> theArray) {
        List<Item> aResult = new ArrayList<>();
        for (Item anItem : theArray) {
            aResult.add(anItem);
        }
        return aResult;
    }

    @Test
    void allocAssignsIndexAscendingHandles() {
        DataArray<Item> anArray = newArray(4);

        Item a = anArray.dataArrayAlloc();
        Item b = anArray.dataArrayAlloc();

        assertEquals(2, anArray.mSize);
        assertEquals(2, anArray.mMaxUsedCount);
        assertEquals(4, anArray.mMaxSize);
        assertEquals("test", anArray.mName);

        // mNextKey is re-seeded to 1001 by DataArrayInitialize (DataArray.h:52).
        int aIdA = (1001 << DataArray.DATA_ARRAY_KEY_SHIFT);
        int aIdB = (1002 << DataArray.DATA_ARRAY_KEY_SHIFT) | 1;
        assertEquals(aIdA, anArray.dataArrayGetID(a));
        assertEquals(aIdB, anArray.dataArrayGetID(b));
        assertSame(a, anArray.dataArrayGet(aIdA));
        assertSame(b, anArray.dataArrayTryToGet(aIdB));
        assertSame(a, anArray.dataArrayGetItemAt(0));
        assertEquals(aIdA, anArray.dataArrayGetIDAt(0));

        // Id 0 is the null handle; an unused index resolves to null.
        assertNull(anArray.dataArrayTryToGet(0));
        assertNull(anArray.dataArrayTryToGet((2000 << DataArray.DATA_ARRAY_KEY_SHIFT) | 3));
    }

    @Test
    void freeKeepsHandleSlotsStableAndReusesThemLastInFirstOut() {
        DataArray<Item> anArray = newArray(4);
        Item a = anArray.dataArrayAlloc();
        Item b = anArray.dataArrayAlloc();
        Item c = anArray.dataArrayAlloc();
        int aIdA = anArray.dataArrayGetID(a);
        int aIdB = anArray.dataArrayGetID(b);

        anArray.dataArrayFree(a);

        assertEquals(2, anArray.mSize);
        assertEquals(3, anArray.mMaxUsedCount); // freeing never shrinks the used prefix
        assertEquals(0, anArray.mFreeListHead);
        assertNull(anArray.dataArrayTryToGet(aIdA));
        // The other handles stay valid across the deletion.
        assertSame(b, anArray.dataArrayTryToGet(aIdB));

        Item d = anArray.dataArrayAlloc();
        assertSame(d, anArray.dataArrayGetItemAt(0)); // the freed slot is reused
        assertNotSame(a, d);
        assertEquals(3, anArray.mMaxUsedCount);
        assertEquals(3, anArray.mSize);
        assertSame(c, anArray.dataArrayGetItemAt(2));

        // The free list is LIFO: freeing b then c reuses c's slot first.
        anArray.dataArrayFree(b);
        anArray.dataArrayFree(c);
        assertSame(anArray.dataArrayAlloc(), anArray.dataArrayGetItemAt(2));
        assertSame(anArray.dataArrayAlloc(), anArray.dataArrayGetItemAt(1));
    }

    @Test
    void iterationIsIndexAscendingAndSkipsEveryUnusedSlot() {
        DataArray<Item> anArray = newArray(4);
        Item a = anArray.dataArrayAlloc();
        Item b = anArray.dataArrayAlloc();
        Item c = anArray.dataArrayAlloc();
        Item d = anArray.dataArrayAlloc();

        anArray.dataArrayFree(b);
        anArray.dataArrayFree(d);

        assertEquals(List.of(a, c), live(anArray));
        assertEquals(2, anArray.mSize);

        // The end condition re-checks the live mMaxUsedCount.
        Iterator<Item> anIterator = anArray.iterator();
        assertTrue(anIterator.hasNext());
        assertSame(a, anIterator.next());
        assertSame(c, anIterator.next());
        assertFalse(anIterator.hasNext());
    }

    @Test
    void currentItemCanBeFreedDuringIteration() {
        DataArray<Item> anArray = newArray(4);
        anArray.dataArrayAlloc();
        anArray.dataArrayAlloc();
        anArray.dataArrayAlloc();

        for (Item anItem : anArray) {
            anArray.dataArrayFree(anItem);
        }

        assertEquals(0, anArray.mSize);
        assertEquals(3, anArray.mMaxUsedCount); // slots stay part of the used prefix
        assertTrue(live(anArray).isEmpty());
        assertEquals(2, anArray.mFreeListHead);
    }

    @Test
    void freeingEverythingDuringIterationMakesFreedIndicesAllocatableAgain() {
        DataArray<Item> anArray = newArray(8);
        Item a = anArray.dataArrayAlloc();
        anArray.dataArrayAlloc();
        Item c = anArray.dataArrayAlloc();

        for (Item anItem : anArray) {
            if (anItem != c) {
                anArray.dataArrayFree(anItem);
            }
        }

        assertEquals(1, anArray.mSize);
        assertNotSame(a, anArray.dataArrayGetItemAt(0));
        assertSame(c, anArray.dataArrayGetItemAt(2));
        // The freed slot at index 1 is handed out next (free list head).
        Item d = anArray.dataArrayAlloc();
        assertSame(d, anArray.dataArrayGetItemAt(1));
    }

    @Test
    void freeAllDropsEveryLiveSlot() {
        DataArray<Item> anArray = newArray(4);
        Item a = anArray.dataArrayAlloc();
        int aStaleId = anArray.dataArrayGetID(a);
        anArray.dataArrayAlloc();
        int aNextKeyBefore = anArray.mNextKey;

        anArray.dataArrayFreeAll();

        assertEquals(0, anArray.mSize);
        assertEquals(0, anArray.mMaxUsedCount);
        assertEquals(0, anArray.mFreeListHead);
        assertTrue(live(anArray).isEmpty());
        // DataArrayFreeAll does not reset mNextKey, so generations keep advancing.
        assertEquals(aNextKeyBefore, anArray.mNextKey);
        assertNull(anArray.dataArrayTryToGet(aStaleId));

        Item aFresh = anArray.dataArrayAlloc();
        assertSame(aFresh, anArray.dataArrayGetItemAt(0));
        assertEquals(1, anArray.mMaxUsedCount);
        // The recycled slot never aliases the stale handle.
        assertNotEquals(aStaleId, anArray.dataArrayGetID(aFresh));
        assertNull(anArray.dataArrayTryToGet(aStaleId));
    }

    @Test
    void disposeResetsTheArray() {
        DataArray<Item> anArray = newArray(4);
        Item a = anArray.dataArrayAlloc();
        int anId = anArray.dataArrayGetID(a);

        anArray.dataArrayDispose();

        assertEquals(0, anArray.mSize);
        assertEquals(0, anArray.mMaxSize);
        assertNull(anArray.mName);
        assertNull(anArray.dataArrayTryToGet(anId));
        anArray.dataArrayInitialize(2, "again");
        assertEquals(2, anArray.mMaxSize);
    }

    @Test
    void allocThrowsWhenTheArrayIsFull() {
        DataArray<Item> anArray = newArray(2);
        anArray.dataArrayAlloc();
        anArray.dataArrayAlloc();

        assertThrows(IllegalStateException.class, anArray::dataArrayAlloc);
    }

    @Test
    void invalidIdsAreRejected() {
        DataArray<Item> anArray = newArray(2);

        assertThrows(IllegalStateException.class, () -> anArray.dataArrayGet(0));
        assertThrows(IllegalStateException.class, () -> anArray.dataArrayGet(12345));
        // Out of the allocated range.
        assertNull(anArray.dataArrayTryToGet((1001 << DataArray.DATA_ARRAY_KEY_SHIFT) | 5));
    }

    @Test
    void foreignItemsAreRejected() {
        DataArray<Item> anArray = newArray(2);
        anArray.dataArrayAlloc();

        assertThrows(IllegalStateException.class, () -> anArray.dataArrayFree(new Item()));
        assertThrows(IllegalStateException.class, () -> anArray.dataArrayGetID(new Item()));
    }

    @Test
    void freedSlotIsRefilledByTheFactory() {
        DataArray<Item> anArray = newArray(2);
        Item a = anArray.dataArrayAlloc();
        a.mValue = 7;

        anArray.dataArrayFree(a);

        // dataArrayFree value-initialises the slot (upstream destroy + construct_at).
        Item aFresh = anArray.dataArrayGetItemAt(0);
        assertNotSame(a, aFresh);
        assertEquals(0, aFresh.mValue);
    }

    @Test
    void resetItemAtReplacesTheSlotContents() {
        DataArray<Item> anArray = newArray(2);
        Item a = anArray.dataArrayAlloc();

        Item aReset = anArray.dataArrayResetItemAt(0);

        assertNotSame(a, aReset);
        assertSame(aReset, anArray.dataArrayGetItemAt(0));
        assertSame(aReset, anArray.dataArrayTryToGet(anArray.dataArrayGetIDAt(0)));
    }

    @Test
    void keyWrapsAroundAtTheArrayCapacity() {
        DataArray<Item> anArray = newArray(4);
        anArray.mNextKey = DataArray.DATA_ARRAY_MAX_SIZE - 1;

        anArray.dataArrayAlloc();
        assertEquals(DataArray.DATA_ARRAY_KEY_FIRST, anArray.mNextKey); // wraps to 1
        Item b = anArray.dataArrayAlloc();
        assertEquals(DataArray.DATA_ARRAY_KEY_FIRST << DataArray.DATA_ARRAY_KEY_SHIFT | 1,
                anArray.dataArrayGetID(b));

        // Keys live in the high 16 bits; the index in the low 16.
        int anId = anArray.dataArrayGetID(b);
        assertTrue((anId & DataArray.DATA_ARRAY_KEY_MASK) != 0);
        assertEquals(1, anId & DataArray.DATA_ARRAY_INDEX_MASK);
    }

    @Test
    void unusedSlotsOfAFreshArrayAreInvisible() {
        DataArray<Item> anArray = newArray(10);

        assertTrue(live(anArray).isEmpty());
        assertEquals(0, anArray.mSize);
        assertEquals(0, anArray.mMaxUsedCount);
        assertEquals(0, anArray.mFreeListHead);
    }
}
