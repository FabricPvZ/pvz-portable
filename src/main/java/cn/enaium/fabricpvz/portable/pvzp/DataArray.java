/*
 * Ported from src/PvzpLib/DataArray.h:24-210 (template class DataArray<T>).
 *
 * The upstream container is a fixed-capacity slot array with a parallel array of
 * keys (`mItemIds`); a slot is live while its key bits are non-zero. Allocation
 * reuses freed slots through a free list and hands out a `(key << 16) | index`
 * handle that stays valid across deletions (see docs/research/03-save.md §6.6,
 * which fixes this exact Java mapping).
 *
 * Java has no placement new, so the value-initialisation of `DataArrayItem : T`
 * performed by the upstream `DataArrayResetItemAt` is modelled by a factory
 * supplied at construction time.
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Ported from src/PvzpLib/DataArray.h:24-210.
 *
 * <p>Fixed-capacity slot array with stable numeric handles, iteration over the live slots only
 * in ascending index order, and a free list so that {@link #dataArrayAlloc()} reuses the slots
 * released by {@link #dataArrayFree(Object)}. The id layout is byte-identical to upstream:
 * {@code id = (mNextKey << 16) | index}.
 *
 * <p>Unlike C++, {@code T} cannot be constructed in place; the {@code theFactory} supplier passed
 * to the constructor is invoked for every slot reset, exactly where upstream performs
 * {@code std::construct_at} (value-init).
 */
public class DataArray<T> implements Iterable<T> {

    /** Ported from src/PvzpLib/DataArray.h:26-33 (anonymous enum). */
    public static final int DATA_ARRAY_INDEX_MASK = 65535;

    /** Ported from src/PvzpLib/DataArray.h:26-33. */
    public static final int DATA_ARRAY_KEY_MASK = -65536;

    /** Ported from src/PvzpLib/DataArray.h:26-33. */
    public static final int DATA_ARRAY_KEY_SHIFT = 16;

    /** Ported from src/PvzpLib/DataArray.h:26-33. */
    public static final int DATA_ARRAY_MAX_SIZE = 65536;

    /** Ported from src/PvzpLib/DataArray.h:26-33. */
    public static final int DATA_ARRAY_KEY_FIRST = 1;

    private final Supplier<T> mFactory;

    /** Upstream `std::unique_ptr<DataArrayItem[]>`; null until {@link #dataArrayInitialize}. */
    private Object[] mItems;

    /** Upstream `std::unique_ptr<unsigned int[]>`; null until {@link #dataArrayInitialize}. */
    private int[] mItemIds;

    /** Ported from src/PvzpLib/DataArray.h:38. */
    public int mMaxUsedCount;

    /** Ported from src/PvzpLib/DataArray.h:39. */
    public int mMaxSize;

    /** Ported from src/PvzpLib/DataArray.h:40. */
    public int mFreeListHead;

    /** Ported from src/PvzpLib/DataArray.h:41. */
    public int mSize;

    /** Ported from src/PvzpLib/DataArray.h:42 (in-class initializer `= 1U`). */
    public int mNextKey = DATA_ARRAY_KEY_FIRST;

    /** Ported from src/PvzpLib/DataArray.h:43. */
    public String mName;

    /**
     * Creates an uninitialised array; call {@link #dataArrayInitialize(int, String)} before use
     * (mirrors the upstream default constructor, which leaves the array empty).
     *
     * @param theFactory creates the value-initialised item for a slot (upstream placement new).
     */
    public DataArray(Supplier<T> theFactory) {
        if (theFactory == null) {
            throw new NullPointerException("DataArray factory must not be null");
        }
        mFactory = theFactory;
    }

    /** Convenience constructor: {@code theFactory} plus an immediate initialisation. */
    public DataArray(Supplier<T> theFactory, int theMaxSize, String theName) {
        this(theFactory);
        dataArrayInitialize(theMaxSize, theName);
    }

    /**
     * Ported from src/PvzpLib/DataArray.h:56-64 (DataArrayInitialize).
     *
     * <p>Note the upstream re-seeds {@code mNextKey} to 1001 here (not to the in-class default 1).
     */
    public void dataArrayInitialize(int theMaxSize, String theName) {
        if (mItems != null) {
            throw new IllegalStateException("Data array already initialized: " + mName);
        }
        mItems = new Object[theMaxSize];
        mItemIds = new int[theMaxSize];
        mMaxSize = theMaxSize;
        mNextKey = 1001;
        mName = theName;
    }

    /** Ported from src/PvzpLib/DataArray.h:66-77 (DataArrayDispose). */
    public void dataArrayDispose() {
        dataArrayFreeAll();
        mItems = null;
        mItemIds = null;
        mMaxUsedCount = 0;
        mMaxSize = 0;
        mFreeListHead = 0;
        mSize = 0;
        mName = null;
    }

    /**
     * Ported from src/PvzpLib/DataArray.h:79-88 (DataArrayFree).
     *
     * <p>Upstream computes the slot index from the item address; the Java model locates the slot by
     * object identity instead (upstream asserts that the handle at that index resolves back to the
     * item, which is kept here).
     */
    public void dataArrayFree(T theItem) {
        int anIndex = indexOf(theItem);
        if (anIndex < 0 || dataArrayTryToGet(mItemIds[anIndex]) != theItem) {
            throw new IllegalStateException(
                    "Failed: dataArrayFree(" + theItem + ") in " + mName);
        }
        dataArrayResetItemAt(anIndex);
        mItemIds[anIndex] = mFreeListHead;
        mFreeListHead = anIndex;
        mSize--;
    }

    /** Ported from src/PvzpLib/DataArray.h:90-98 (DataArrayFreeAll). */
    public void dataArrayFreeAll() {
        for (T aItem : this) {
            dataArrayFree(aItem);
        }

        mFreeListHead = 0;
        mMaxUsedCount = 0;
    }

    /** Ported from src/PvzpLib/DataArray.h:100-111 (DataArrayGetID). */
    public int dataArrayGetID(T theItem) {
        int anIndex = indexOf(theItem);
        int anId = anIndex < 0 ? 0 : mItemIds[anIndex];
        if (dataArrayTryToGet(anId) != theItem) {
            throw new IllegalStateException(
                    "Failed: dataArrayGetID(" + theItem + ") for " + mName);
        }
        return anId;
    }

    /**
     * Ported from src/PvzpLib/DataArray.h:115-155 (DataArray::Iterator and begin/end).
     *
     * <p>Index-ascending, live-slots-only iteration; the end condition re-checks the live
     * {@code mMaxUsedCount} on every step and the item is resolved on dereference, so freeing the
     * yielded item mid-loop is safe.
     */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int mIndex = 0;

            {
                skipFreeSlots();
            }

            @Override
            public boolean hasNext() {
                return mIndex < mMaxUsedCount;
            }

            @Override
            public T next() {
                if (mIndex >= mMaxUsedCount) {
                    throw new NoSuchElementException();
                }
                T anItem = itemAt(mIndex);
                mIndex++;
                skipFreeSlots();
                return anItem;
            }

            private void skipFreeSlots() {
                while (mIndex < mMaxUsedCount && (mItemIds[mIndex] & DATA_ARRAY_KEY_MASK) == 0) {
                    mIndex++;
                }
            }
        };
    }

    /**
     * Ported from src/PvzpLib/DataArray.h:158-178 (DataArrayAlloc).
     *
     * <p>Returns a fresh factory instance and assigns it the next {@code (key << 16) | index}
     * handle.
     */
    public T dataArrayAlloc() {
        if (mSize >= mMaxSize) {
            throw new IllegalStateException("Data array full: " + mName);
        }
        if (mFreeListHead > mMaxUsedCount) {
            throw new IllegalStateException("DataArrayAlloc error in " + mName);
        }

        int aNext = mMaxUsedCount;
        if (mFreeListHead == mMaxUsedCount) {
            mFreeListHead = ++mMaxUsedCount;
        } else {
            aNext = mFreeListHead;
            mFreeListHead = mItemIds[mFreeListHead];
        }

        T aNewItem = dataArrayResetItemAt(aNext);
        mItemIds[aNext] = (mNextKey++ << DATA_ARRAY_KEY_SHIFT) | aNext;
        if (mNextKey == DATA_ARRAY_MAX_SIZE) {
            mNextKey = 1;
        }
        mSize++;

        return aNewItem;
    }

    /** Ported from src/PvzpLib/DataArray.h:180-188 (DataArrayTryToGet); null for freed/invalid ids. */
    public T dataArrayTryToGet(int theId) {
        if (theId == 0 || (theId & DATA_ARRAY_INDEX_MASK) >= mMaxSize) {
            return null;
        }

        int anIndex = theId & DATA_ARRAY_INDEX_MASK;
        return mItemIds[anIndex] == theId ? itemAt(anIndex) : null;
    }

    /** Ported from src/PvzpLib/DataArray.h:190-194 (DataArrayGet); throws where upstream asserts. */
    public T dataArrayGet(int theId) {
        T anItem = dataArrayTryToGet(theId);
        if (anItem == null) {
            throw new IllegalStateException(
                    "Failed: dataArrayGet(0x" + Integer.toHexString(theId) + ") for " + mName);
        }
        return anItem;
    }

    /** Ported from src/PvzpLib/DataArray.h:196-199 (DataArrayGetItemAt): raw slot, no liveness check. */
    public T dataArrayGetItemAt(int theIndex) {
        return itemAt(theIndex);
    }

    /**
     * Ported from src/PvzpLib/DataArray.h:201-206 (DataArrayResetItemAt): destroys and
     * value-initialises the slot (the factory supplies the fresh instance).
     */
    public T dataArrayResetItemAt(int theIndex) {
        T aNewItem = mFactory.get();
        if (aNewItem == null) {
            throw new IllegalStateException("DataArray factory returned null for " + mName);
        }
        mItems[theIndex] = aNewItem;
        return aNewItem;
    }

    /** Ported from src/PvzpLib/DataArray.h:208-211 (DataArrayGetIDAt). */
    public int dataArrayGetIDAt(int theIndex) {
        return mItemIds[theIndex];
    }

    /** Java setter for the upstream {@code unsigned int&} returned by DataArrayGetIDAt. */
    public void dataArraySetIDAt(int theIndex, int theId) {
        mItemIds[theIndex] = theId;
    }

    @SuppressWarnings("unchecked")
    private T itemAt(int theIndex) {
        return (T) mItems[theIndex];
    }

    private int indexOf(T theItem) {
        for (int i = 0; i < mMaxUsedCount; i++) {
            if (mItems[i] == theItem) {
                return i;
            }
        }
        return -1;
    }
}
