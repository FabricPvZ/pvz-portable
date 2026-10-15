/*
 * Ported from src/PvzpLib/PvzpList.h:22-75 (struct PvzpAllocator), implemented by
 * src/PvzpLib/PvzpList.cpp:22-137, with the global table from src/PvzpLib/PvzpCommon.cpp:1173-1201
 * (declared in src/PvzpLib/PvzpList.h:48-49 and src/PvzpLib/PvzpCommon.h:171-172).
 *
 * Upstream pools raw blocks of `mItemSize` bytes and threads a free list through the first
 * pointer-sized bytes of every item. In Java there is no raw memory, and the only payload the
 * game ever allocates through this interface is the PvzpListNode of a PvzpList, so the free list
 * is threaded through PvzpListNode.mNext and `mItemSize` is kept as the size key only (it is what
 * FindGlobalAllocator matches on, exactly as upstream does).
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from src/PvzpLib/PvzpList.h:22-75 and src/PvzpLib/PvzpList.cpp:22-137.
 *
 * <p>Fixed-size node pool for {@link PvzpList}: {@link #initialize(int, int)} pre-sets the grow
 * count, {@link #calloc(int)} hands out zeroed nodes, {@link #free(Object, int)} returns them, and
 * {@link #freeAll()} drops every block (so the next allocation grows again, as upstream does).
 *
 * <p>The static table mirrors the upstream globals: {@link #gGlobalAllocators} /
 * {@link #gNumGlobalAllocators} (src/PvzpLib/PvzpList.h:48-49), {@link #findGlobalAllocator(int)}
 * and {@link #freeGlobalAllocators()} (src/PvzpLib/PvzpCommon.cpp:1176-1201). They live here
 * because the header declaring the table is the one this class is ported from.
 */
public class PvzpAllocator {

    /** Ported from src/PvzpLib/PvzpList.h:20 ({@code MAX_GLOBAL_ALLOCATORS}). */
    public static final int MAX_GLOBAL_ALLOCATORS = 128;

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1173 ({@code gGlobalAllocators}). */
    public static final PvzpAllocator[] gGlobalAllocators = new PvzpAllocator[MAX_GLOBAL_ALLOCATORS];

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1174 ({@code gNumGlobalAllocators}). */
    public static int gNumGlobalAllocators = 0;

    /** Upstream {@code void* mFreeList}, threaded through {@code PvzpListNode.mNext}. */
    public PvzpListNode<Object> mFreeList;

    /** Upstream {@code void* mBlockList}; one entry per grow() block, holding that block's nodes. */
    public final List<Object[]> mBlockList = new ArrayList<>();

    /** Ported from src/PvzpLib/PvzpList.h:26. */
    public int mGrowCount;

    /** Ported from src/PvzpLib/PvzpList.h:27. */
    public int mTotalItems;

    /** Ported from src/PvzpLib/PvzpList.h:28. */
    public int mItemSize;

    /** Ported from src/PvzpLib/PvzpList.cpp:22-33 (PvzpAllocator::Initialize). */
    public void initialize(int theGrowCount, int theItemSize) {
        if (theItemSize < Integer.BYTES) {
            throw new IllegalArgumentException("PvzpAllocator item size too small: " + theItemSize);
        }

        mFreeList = null;
        mBlockList.clear();
        mGrowCount = theGrowCount;
        mTotalItems = 0;
        mItemSize = theItemSize;
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:35-38 (PvzpAllocator::Dispose). */
    public void dispose() {
        freeAll();
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:40-62 (PvzpAllocator::Grow). */
    public void grow() {
        if (mGrowCount <= 0) {
            throw new IllegalStateException("PvzpAllocator grow count is " + mGrowCount);
        }

        Object[] aBlock = new Object[mGrowCount];
        for (int i = 0; i < mGrowCount; i++) {
            PvzpListNode<Object> aNode = new PvzpListNode<>();
            aNode.mNext = mFreeList;
            aBlock[i] = aNode;
            mFreeList = aNode;
        }
        mBlockList.add(aBlock);
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:64-77 (PvzpAllocator::IsPointerFromAllocator). */
    public boolean isPointerFromAllocator(Object theItem) {
        for (Object[] aBlock : mBlockList) {
            for (Object aNode : aBlock) {
                if (aNode == theItem) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:79-85 (PvzpAllocator::IsPointerOnFreeList). */
    public boolean isPointerOnFreeList(Object theItem) {
        for (PvzpListNode<Object> aNode = mFreeList; aNode != null; aNode = aNode.mNext) {
            if (aNode == theItem) {
                return true;
            }
        }
        return false;
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:87-97 (PvzpAllocator::Alloc). */
    public Object alloc(int theItemSize) {
        mTotalItems++;
        if (mFreeList == null) {
            grow();
        }

        PvzpListNode<Object> anItem = mFreeList;
        mFreeList = anItem.mNext;
        return anItem;
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:99-105 (PvzpAllocator::Calloc): zeroed item. */
    @SuppressWarnings("unchecked")
    public Object calloc(int theItemSize) {
        PvzpListNode<Object> anItem = (PvzpListNode<Object>) alloc(theItemSize);
        anItem.mValue = null;
        anItem.mNext = null;
        anItem.mPrev = null;
        return anItem;
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:107-115 (PvzpAllocator::Free). */
    @SuppressWarnings("unchecked")
    public void free(Object theItem, int theItemSize) {
        mTotalItems--;
        if (!isPointerFromAllocator(theItem)) {
            throw new IllegalStateException("PvzpAllocator::Free on a foreign pointer");
        }
        if (isPointerOnFreeList(theItem)) {
            throw new IllegalStateException("PvzpAllocator::Free on a free pointer");
        }

        PvzpListNode<Object> aNode = (PvzpListNode<Object>) theItem;
        aNode.mNext = mFreeList;
        mFreeList = aNode;
    }

    /** Ported from src/PvzpLib/PvzpList.cpp:117-137 (PvzpAllocator::FreeAll). */
    public void freeAll() {
        mBlockList.clear();
        mFreeList = null;
        mTotalItems = 0;
    }

    /**
     * Ported from src/PvzpLib/PvzpCommon.cpp:1176-1191 (FindGlobalAllocator).
     *
     * <p>Returns the allocator keyed by {@code theSize}, creating one (grow count 16) on first use.
     */
    public static PvzpAllocator findGlobalAllocator(int theSize) {
        for (int i = 0; i < gNumGlobalAllocators; i++) {
            if (gGlobalAllocators[i].mItemSize == theSize) {
                return gGlobalAllocators[i];
            }
        }

        if (gNumGlobalAllocators >= MAX_GLOBAL_ALLOCATORS - 1) {
            throw new IllegalStateException("Too many global PvzpAllocators");
        }

        PvzpAllocator aAllocator = new PvzpAllocator();
        aAllocator.initialize(16, theSize);
        gGlobalAllocators[gNumGlobalAllocators++] = aAllocator;
        return aAllocator;
    }

    /** Ported from src/PvzpLib/PvzpCommon.cpp:1193-1201 (FreeGlobalAllocators). */
    public static void freeGlobalAllocators() {
        for (int i = 0; i < gNumGlobalAllocators; i++) {
            gGlobalAllocators[i].freeAll();
        }

        for (int i = 0; i < gNumGlobalAllocators; i++) {
            gGlobalAllocators[i] = null;
        }
        gNumGlobalAllocators = 0;
    }
}
