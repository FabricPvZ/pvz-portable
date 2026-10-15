package cn.enaium.fabricpvz.portable.pvzp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Behaviour tests for {@link PvzpList}, covering the head/tail/node semantics of
 * src/PvzpLib/PvzpList.h that the particle system and the save code rely on (node iteration,
 * removal during iteration, allocator pooling).
 */
class PvzpListTest {

    private static List<Integer> values(PvzpList<Integer> theList) {
        List<Integer> aResult = new ArrayList<>();
        for (PvzpListNode<Integer> aNode = theList.mHead; aNode != null; aNode = aNode.mNext) {
            aResult.add(aNode.mValue);
        }
        return aResult;
    }

    @Test
    void addTailAppendsAndAddHeadPrepends() {
        PvzpList<Integer> aList = new PvzpList<>();

        aList.addTail(2);
        aList.addTail(3);
        aList.addHead(1);

        assertEquals(List.of(1, 2, 3), values(aList));
        assertEquals(3, aList.mSize);
        assertEquals(1, aList.mHead.mValue);
        assertEquals(3, aList.mTail.mValue);
        assertNull(aList.mHead.mPrev);
        assertNull(aList.mTail.mNext);
    }

    @Test
    void linksAreWiredInBothDirections() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);
        aList.addTail(2);

        assertSame(aList.mTail, aList.mHead.mNext);
        assertSame(aList.mHead, aList.mTail.mPrev);
    }

    @Test
    void removeHeadPopsFromTheFrontAndClearsTheTail() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);
        aList.addTail(2);

        assertEquals(1, aList.removeHead());
        assertEquals(1, aList.mSize);
        assertEquals(2, aList.mHead.mValue);
        assertNull(aList.mHead.mPrev);

        assertEquals(2, aList.removeHead());
        assertEquals(0, aList.mSize);
        assertNull(aList.mHead);
        assertNull(aList.mTail);
    }

    @Test
    void removeAtRelinksNeighboursAndReturnsTheNextNode() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);
        aList.addTail(2);
        aList.addTail(3);
        PvzpListNode<Integer> aMiddle = aList.mHead.mNext;

        PvzpListNode<Integer> aNext = aList.removeAt(aMiddle);

        assertSame(aList.mTail, aNext);
        assertEquals(List.of(1, 3), values(aList));
        assertEquals(2, aList.mSize);
        assertSame(aList.mHead, aList.mTail.mPrev);

        // Removing the tail moves mTail back; removing the head moves mHead forward.
        aList.removeAt(aList.mTail);
        assertEquals(1, aList.mTail.mValue);
        aList.removeAt(aList.mHead);
        assertEquals(0, aList.mSize);
        assertNull(aList.mHead);
        assertNull(aList.mTail);
    }

    @Test
    void nodesCanBeRemovedWhileIterating() {
        PvzpList<Integer> aList = new PvzpList<>();
        for (int i = 1; i <= 6; i++) {
            aList.addTail(i);
        }

        // The upstream removal-during-iteration shape: grab mNext before removing the node.
        for (PvzpListNode<Integer> aNode = aList.mHead; aNode != null; ) {
            PvzpListNode<Integer> aNext = aNode.mNext;
            if (aNode.mValue % 2 == 0) {
                aList.removeAt(aNode);
            }
            aNode = aNext;
        }

        assertEquals(List.of(1, 3, 5), values(aList));
        assertEquals(3, aList.mSize);
    }

    @Test
    void removingTheHeadWhileIteratingKeepsWalkingTheRest() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);
        aList.addTail(2);
        aList.addTail(3);

        List<Integer> aSeen = new ArrayList<>();
        PvzpListNode<Integer> aOriginalHead = aList.mHead;
        for (PvzpListNode<Integer> aNode = aList.mHead; aNode != null; ) {
            PvzpListNode<Integer> aNext = aNode.mNext;
            aSeen.add(aNode.mValue);
            if (aNode == aOriginalHead) {
                aList.removeAt(aNode);
            }
            aNode = aNext;
        }

        assertEquals(List.of(1, 2, 3), aSeen);
        assertEquals(List.of(2, 3), values(aList));
    }

    @Test
    void findUsesValueEquality() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);
        aList.addTail(1000);
        aList.addTail(1000);

        assertSame(aList.mHead.mNext, aList.find(1000)); // boxed values beyond the cache range
        assertSame(aList.mHead, aList.find(1));
        assertNull(aList.find(7));
    }

    @Test
    void removeAllDropsEveryNode() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);
        aList.addHead(0);

        aList.removeAll();

        assertEquals(0, aList.mSize);
        assertNull(aList.mHead);
        assertNull(aList.mTail);
        assertTrue(values(aList).isEmpty());
        // The list stays usable afterwards.
        aList.addTail(9);
        assertEquals(List.of(9), values(aList));
    }

    @Test
    void emptyListAccessorsFail() {
        PvzpList<Integer> aList = new PvzpList<>();

        assertThrows(IllegalStateException.class, aList::getHead);
        assertThrows(IllegalStateException.class, aList::getTail);
    }

    @Test
    void allocatorIsResolvedFromTheGlobalTableOnFirstAdd() {
        PvzpList<Integer> aList = new PvzpList<>();
        assertNull(aList.mpAllocator);

        aList.addTail(4);

        assertNotNull(aList.mpAllocator);
        assertSame(PvzpAllocator.findGlobalAllocator(PvzpList.NODE_SIZE), aList.mpAllocator);
        assertTrue(aList.mpAllocator.mTotalItems >= 1);
    }

    @Test
    void explicitAllocatorPoolsAndReusesNodes() {
        PvzpAllocator anAllocator = new PvzpAllocator();
        anAllocator.initialize(4, PvzpList.NODE_SIZE);

        PvzpList<Integer> aList = new PvzpList<>();
        aList.setAllocator(anAllocator);
        aList.addTail(1);
        aList.addTail(2);

        assertEquals(1, anAllocator.mBlockList.size()); // one grow() for four nodes
        assertEquals(2, anAllocator.mTotalItems);

        PvzpListNode<Integer> aNode = aList.mHead;
        aList.removeHead();
        assertEquals(1, anAllocator.mTotalItems); // the node went back to the pool
        assertTrue(anAllocator.isPointerFromAllocator(aNode));
        assertTrue(anAllocator.isPointerOnFreeList(aNode));

        aList.addHead(7);
        assertEquals(2, anAllocator.mTotalItems);
        assertSame(aNode, aList.mHead); // last-freed node is handed out first

        aList.removeAll();
        assertEquals(0, anAllocator.mTotalItems);
        assertEquals(1, anAllocator.mBlockList.size()); // RemoveAll does not drop the blocks
    }

    @Test
    void setAllocatorOnANonEmptyListIsRejected() {
        PvzpList<Integer> aList = new PvzpList<>();
        aList.addTail(1);

        assertThrows(IllegalStateException.class, () -> aList.setAllocator(new PvzpAllocator()));
    }

    @Test
    void growCountIsHonoured() {
        PvzpAllocator anAllocator = new PvzpAllocator();
        anAllocator.initialize(2, PvzpList.NODE_SIZE);
        PvzpList<Integer> aList = new PvzpList<>();
        aList.setAllocator(anAllocator);

        aList.addTail(1);
        aList.addTail(2);
        assertEquals(1, anAllocator.mBlockList.size());

        aList.addTail(3);
        assertEquals(2, anAllocator.mBlockList.size()); // grow() again once the pool is drained
    }
}
