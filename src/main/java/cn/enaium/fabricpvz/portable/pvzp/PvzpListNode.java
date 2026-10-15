/*
 * Ported from src/PvzpLib/PvzpList.h:80-86 (template class PvzpListNode<T>).
 *
 * The upstream node is a plain struct with a value and raw prev/next pointers; the node is
 * allocated by a PvzpAllocator, so Java models it as a class whose instances are pooled by
 * PvzpAllocator (see that class for the pool).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/PvzpList.h:80-86.
 *
 * <p>Node of a {@link PvzpList}. Field names match upstream. Nodes are handed out by
 * {@link PvzpAllocator}; {@link PvzpList#find(Object)} compares {@code mValue} by value.
 *
 * @param <T> element type (upstream {@code uint32_t} handle types box)
 */
public class PvzpListNode<T> {

    /** Ported from src/PvzpLib/PvzpList.h:83. */
    public T mValue;

    /** Ported from src/PvzpLib/PvzpList.h:84. */
    public PvzpListNode<T> mNext;

    /** Ported from src/PvzpLib/PvzpList.h:85. */
    public PvzpListNode<T> mPrev;

    public PvzpListNode() {
    }

    public PvzpListNode(T theValue) {
        mValue = theValue;
    }
}
