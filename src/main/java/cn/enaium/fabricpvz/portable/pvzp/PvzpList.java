/*
 * Ported from src/PvzpLib/PvzpList.h:88-190 (template class PvzpList<T>).
 *
 * Intrusive doubly linked list. Nodes come from a PvzpAllocator: an explicitly installed one
 * (SetAllocator, used by the particle holder pools) or the process-wide allocator resolved by
 * FindGlobalAllocator(sizeof(PvzpListNode<T>)) on first insertion.
 *
 * Iteration is done over the public node chain (`mHead` ... `mNext`), exactly as upstream, so
 * removal during iteration keeps the upstream shape:
 *
 *   for (PvzpListNode<X> n = list.mHead; n != null; ) { PvzpListNode<X> next = n.mNext; ... n = next; }
 *
 * `PvzpListNode` is a reference type in Java, so `sizeof(PvzpListNode<T>)` has no per-T meaning;
 * NODE_SIZE is the single Java node size used as the global-allocator key.
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.util.Objects;

/**
 * Ported from src/PvzpLib/PvzpList.h:88-190.
 *
 * <p>Doubly linked list with head/tail insertion, head removal, node removal, value lookup and
 * bulk removal. {@link #getHead()} / {@link #getTail()} throw where upstream asserts that the list
 * is non-empty; {@link #removeHead()} dereferences the head node, so it fails on an empty list just
 * like the upstream code.
 *
 * @param <T> element type; upstream value handles ({@code uint32_t} ids) box
 */
public class PvzpList<T> {

    /**
     * Java node size used as the key for the automatic global allocator
     * (upstream {@code sizeof(PvzpListNode<T>)}): three references in the Java model.
     */
    public static final int NODE_SIZE = 24;

    /** Ported from src/PvzpLib/PvzpList.h:92. */
    public PvzpListNode<T> mHead;

    /** Ported from src/PvzpLib/PvzpList.h:93. */
    public PvzpListNode<T> mTail;

    /** Ported from src/PvzpLib/PvzpList.h:94. */
    public int mSize;

    /** Ported from src/PvzpLib/PvzpList.h:95. */
    public PvzpAllocator mpAllocator;

    /** Ported from src/PvzpLib/PvzpList.h:97-103 (PvzpList::PvzpList). */
    public PvzpList() {
        mHead = null;
        mTail = null;
        mSize = 0;
        mpAllocator = null;
    }

    /**
     * Ported from src/PvzpLib/PvzpList.h:105-109.
     *
     * <p>The C++ destructor frees every remaining node; Java has no deterministic destructor, so
     * {@link #removeAll()} must be called explicitly (it is what the ported callers do).
     */
    public void dispose() {
        removeAll();
    }

    /** Ported from src/PvzpLib/PvzpList.h:111-115 (PvzpList::GetHead). */
    public PvzpListNode<T> getHead() {
        if (mHead == null) {
            throw new IllegalStateException("PvzpList::GetHead on an empty list");
        }
        return mHead;
    }

    /** Ported from src/PvzpLib/PvzpList.h:117-121 (PvzpList::GetTail). */
    public PvzpListNode<T> getTail() {
        if (mTail == null) {
            throw new IllegalStateException("PvzpList::GetTail on an empty list");
        }
        return mTail;
    }

    /** Ported from src/PvzpLib/PvzpList.h:123-140 (PvzpList::AddHead). */
    public void addHead(T theHead) {
        PvzpListNode<T> aNode = allocateNode();
        aNode.mValue = theHead;
        aNode.mNext = mHead;
        aNode.mPrev = null;
        if (mHead != null) {
            mHead.mPrev = aNode;
        } else {
            mTail = aNode;
        }
        mSize++;
        mHead = aNode;
    }

    /** Ported from src/PvzpLib/PvzpList.h:142-159 (PvzpList::AddTail). */
    public void addTail(T theTail) {
        PvzpListNode<T> aNode = allocateNode();
        aNode.mValue = theTail;
        aNode.mNext = null;
        aNode.mPrev = mTail;
        if (mTail != null) {
            mTail.mNext = aNode;
        } else {
            mHead = aNode;
        }
        mSize++;
        mTail = aNode;
    }

    /** Ported from src/PvzpLib/PvzpList.h:161-176 (PvzpList::RemoveHead). */
    public T removeHead() {
        PvzpListNode<T> aHead = mHead;
        PvzpListNode<T> aSecNode = aHead.mNext;
        mHead = aSecNode;
        if (aSecNode != null) {
            aSecNode.mPrev = null;
        } else {
            mTail = null;
        }

        T aVal = aHead.mValue;
        mSize--;
        mpAllocator.free(aHead, NODE_SIZE);
        return aVal;
    }

    /** Ported from src/PvzpLib/PvzpList.h:178-193 (PvzpList::RemoveAt); returns the next node. */
    public PvzpListNode<T> removeAt(PvzpListNode<T> theNode) {
        PvzpListNode<T> aNext = theNode.mNext;
        if (theNode.mPrev != null) {
            theNode.mPrev.mNext = aNext;
        } else {
            mHead = aNext;
        }

        if (aNext != null) {
            aNext.mPrev = theNode.mPrev;
        } else {
            mTail = theNode.mPrev;
        }

        mSize--;
        mpAllocator.free(theNode, NODE_SIZE);
        return aNext;
    }

    /**
     * Ported from src/PvzpLib/PvzpList.h:195-203 (PvzpList::Find).
     *
     * <p>Upstream compares with {@code ==}; the Java element types are boxed value handles, so value
     * equality ({@link Objects#equals}) is used instead.
     */
    public PvzpListNode<T> find(T theItem) {
        for (PvzpListNode<T> aNode = mHead; aNode != null; aNode = aNode.mNext) {
            if (Objects.equals(aNode.mValue, theItem)) {
                return aNode;
            }
        }
        return null;
    }

    /** Ported from src/PvzpLib/PvzpList.h:205-218 (PvzpList::RemoveAll). */
    public void removeAll() {
        PvzpListNode<T> aNode = mHead;
        while (aNode != null) {
            PvzpListNode<T> temp = aNode;
            aNode = aNode.mNext;
            mpAllocator.free(temp, NODE_SIZE);
        }

        mSize = 0;
        mHead = null;
        mTail = null;
    }

    /** Ported from src/PvzpLib/PvzpList.h:220-224 (PvzpList::SetAllocator). */
    public void setAllocator(PvzpAllocator theAllocator) {
        if (mSize != 0) {
            throw new IllegalStateException("PvzpList::SetAllocator on a non-empty list");
        }
        mpAllocator = theAllocator;
    }

    /** Ported from src/PvzpLib/PvzpList.h:95-137: FindGlobalAllocator(sizeof(PvzpListNode<T>)) on first add. */
    @SuppressWarnings("unchecked")
    private PvzpListNode<T> allocateNode() {
        if (mpAllocator == null) {
            mpAllocator = PvzpAllocator.findGlobalAllocator(NODE_SIZE);
        }
        return (PvzpListNode<T>) mpAllocator.calloc(NODE_SIZE);
    }
}
