/*
 * Ported from src/PvzpLib/Definition.h:94-103 (class DefinitionArrayDef).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:94-103 (class DefinitionArrayDef).
 *
 * <p>Corresponds to the pointee type of a pointer member, i.e. an "array pointer + count" pair in a
 * definition class: upstream puns {@code mEmitterDefs}/{@code mEmitterDefCount},
 * {@code mParticleFields}/{@code count}, {@code mTracks}/{@code count} and friends as this layout
 * (Definition.cpp:521, 826). {@link Definition} reads and writes such members through this class.
 *
 * <p>In Java the raw pointer becomes an {@code Object[]} of the pointee definitions (element type is
 * the array's component type, e.g. {@code ReanimatorTrack[]}); the array is empty - never null - when
 * {@link #mArrayCount} is 0, and the two are always in sync.
 */
public class DefinitionArrayDef {
    /** Upstream {@code mArrayData} (Definition.h:97) - the elements, {@code new T[0]} when empty. */
    public Object[] mArrayData;

    /** Upstream {@code mArrayCount} (Definition.h:98) - number of array elements. */
    public int mArrayCount;

    public DefinitionArrayDef() {
        mArrayData = new Object[0];
        mArrayCount = 0;
    }

    /**
     * Adopts {@code theData} as the element storage; the count follows the array length, mirroring
     * upstream's {@code mArrayData}/{@code mArrayCount} pair.
     */
    public DefinitionArrayDef(Object[] theData) {
        mArrayData = theData != null ? theData : new Object[0];
        mArrayCount = mArrayData.length;
    }
}
