package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Flags.h:45 (add/remove bit semantics; see also
 * {@link FlagsMod}, {@link ModalFlags}, {@link AutoModalFlags}).
 *
 * <p>Java generic form of the framework's multi-value flag handling. Upstream expresses
 * "add flags" as a bitwise OR with {@code mAddFlags} and "remove flags" as a bitwise AND
 * with the complement of {@code mRemoveFlags} ({@code (theFlags | add) & ~remove}); this
 * class exposes the same add/remove/has granularity over an arbitrary flag value type.
 */
public class Flags<T> {
    private final java.util.Set<T> mFlags = new java.util.LinkedHashSet<>();

    public Flags() {
    }

    public Flags(java.util.Collection<? extends T> theFlags) {
        mFlags.addAll(theFlags);
    }

    // AddFlags (single)
    public void addFlags(T theFlag) {
        mFlags.add(theFlag);
    }

    // AddFlags (multiple)
    public void addFlags(java.util.Collection<? extends T> theFlags) {
        mFlags.addAll(theFlags);
    }

    // RemoveFlags (single)
    public void removeFlags(T theFlag) {
        mFlags.remove(theFlag);
    }

    // RemoveFlags (multiple)
    public void removeFlags(java.util.Collection<? extends T> theFlags) {
        mFlags.removeAll(theFlags);
    }

    // HasFlags (all of the given flags)
    public boolean hasFlags(T theFlag) {
        return mFlags.contains(theFlag);
    }

    // HasFlags (all of the given flags)
    public boolean hasFlags(java.util.Collection<? extends T> theFlags) {
        return mFlags.containsAll(theFlags);
    }

    // HasAnyFlags
    public boolean hasAnyFlags(java.util.Collection<? extends T> theFlags) {
        for (T aFlag : theFlags) {
            if (mFlags.contains(aFlag)) {
                return true;
            }
        }
        return false;
    }

    // GetFlags: live, unmodifiable view (kept in insertion order).
    public java.util.Set<T> getFlags() {
        return java.util.Collections.unmodifiableSet(mFlags);
    }

    public boolean isEmpty() {
        return mFlags.isEmpty();
    }

    public int size() {
        return mFlags.size();
    }

    // Clear
    public void clear() {
        mFlags.clear();
    }
}
