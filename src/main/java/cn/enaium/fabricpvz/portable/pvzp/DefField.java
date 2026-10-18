/*
 * Ported from src/PvzpLib/Definition.h:51-68 (class DefField).
 */
package cn.enaium.fabricpvz.portable.pvzp;

import java.lang.reflect.Field;

/**
 * Ported from src/PvzpLib/Definition.h:51-68 (class DefField).
 *
 * <p>Describes one member variable ({@code _MemVar}) of a class and its layout within the class:
 * {@link #mFieldName} is the name used by the XML/cache format, {@link #mFieldType} decides how the
 * value is read, and {@link #mExtraData} carries the pointee {@link DefMap} of a pointer member or the
 * {@link DefSymbol} array of a flags/enum member (upstream Definition.h:66-68).
 *
 * <p><b>Java mapping.</b> Upstream addresses the member through {@code mFieldOffset} from a raw
 * {@code void*}; this port keeps {@link #mFieldOffset} (it is part of the compiled-cache schema hash,
 * Definition.cpp:420-446) and additionally names the Java field in {@link #mMemberName}, which the
 * engine reads/writes reflectively. {@code mFieldOffset} is the byte offset of the member inside the
 * flat compiled struct image whose size is {@link DefMap#mDefSize}; the layout rules mirror the LP64
 * C++ layout and are documented on {@link DefFieldType#sizeOf(int)}.
 */
public final class DefField {
    /** Upstream {@code mFieldName} (Definition.h:58): name of {@code _MemVar} in the XML/cache format. */
    public final String mFieldName;

    /** Upstream {@code mFieldOffset} (Definition.h:59): byte offset inside the flat compiled struct image. */
    public final int mFieldOffset;

    /** Upstream {@code mFieldType} (Definition.h:60): storage type of {@code _MemVar}. */
    public final int mFieldType;

    /**
     * Upstream {@code mExtraData} (Definition.h:61): for a pointer member the {@link DefMap} of the
     * pointee's definition class, for a flags/enum member the {@link DefSymbol} array, otherwise null.
     */
    public final Object mExtraData;

    /**
     * Java-only companion of {@code mFieldName}: the public Java field (with the upstream {@code m}
     * prefix) that holds the member, e.g. {@code "mTransX"} for the upstream field {@code "x"}.
     */
    public final String mMemberName;

    private volatile Field mReflectedField;

    public DefField(String theFieldName, String theMemberName, int theFieldOffset, int theFieldType, Object theExtraData) {
        mFieldName = theFieldName;
        mMemberName = theMemberName;
        mFieldOffset = theFieldOffset;
        mFieldType = theFieldType;
        mExtraData = theExtraData;
    }

    /** The pointee definition map of a {@code DT_ARRAY} field (upstream {@code (const DefMap*)mExtraData}). */
    public DefMap arrayDefMap() {
        return (DefMap) mExtraData;
    }

    /** The symbol table of a {@code DT_ENUM}/{@code DT_FLAGS} field (upstream {@code (const DefSymbol*)mExtraData}). */
    public DefSymbol[] symbols() {
        return (DefSymbol[]) mExtraData;
    }

    /**
     * Resolves the Java field backing this member. Public fields keep the upstream {@code m} prefix
     * (AGENTS.md §7, item 2), so plain reflection by name is enough.
     */
    Field reflectedField(Object theDefinition) {
        Field aField = mReflectedField;
        if (aField == null) {
            try {
                aField = theDefinition.getClass().getField(mMemberName);
            } catch (NoSuchFieldException anException) {
                throw new IllegalStateException(
                        "definition class " + theDefinition.getClass().getName() + " has no public field '" + mMemberName
                                + "' for DefMap field '" + mFieldName + "'",
                        anException);
            }
            mReflectedField = aField;
        }
        return aField;
    }

    /** Reads the member value off {@code theDefinition} (upstream {@code *(T*)((char*)def + mFieldOffset)}). */
    public Object getMember(Object theDefinition) {
        try {
            return reflectedField(theDefinition).get(theDefinition);
        } catch (IllegalAccessException anException) {
            throw new IllegalStateException("cannot read field " + mMemberName, anException);
        }
    }

    /** Reads an {@code int} member (upstream {@code (int*)pVar}). */
    public int getIntMember(Object theDefinition) {
        try {
            return reflectedField(theDefinition).getInt(theDefinition);
        } catch (IllegalAccessException anException) {
            throw new IllegalStateException("cannot read int field " + mMemberName, anException);
        }
    }

    /** Writes an {@code int} member (upstream {@code (int*)pVar}). */
    public void setIntMember(Object theDefinition, int theValue) {
        try {
            reflectedField(theDefinition).setInt(theDefinition, theValue);
        } catch (IllegalAccessException anException) {
            throw new IllegalStateException("cannot write int field " + mMemberName, anException);
        }
    }

    /** Reads a {@code float} member (upstream {@code (float*)pVar}). */
    public float getFloatMember(Object theDefinition) {
        try {
            return reflectedField(theDefinition).getFloat(theDefinition);
        } catch (IllegalAccessException anException) {
            throw new IllegalStateException("cannot read float field " + mMemberName, anException);
        }
    }

    /** Writes a {@code float} member. */
    public void setFloatMember(Object theDefinition, float theValue) {
        try {
            reflectedField(theDefinition).setFloat(theDefinition, theValue);
        } catch (IllegalAccessException anException) {
            throw new IllegalStateException("cannot write float field " + mMemberName, anException);
        }
    }

    /** Writes a member value (upstream {@code *(T*)((char*)def + mFieldOffset) = value}). */
    public void setMember(Object theDefinition, Object theValue) {
        try {
            reflectedField(theDefinition).set(theDefinition, theValue);
        } catch (IllegalAccessException anException) {
            throw new IllegalStateException("cannot write field " + mMemberName, anException);
        }
    }

    /**
     * Upstream matches XML element names with {@code strcasecmp} (Definition.cpp:930), so the lookup is
     * case-insensitive.
     */
    public boolean nameMatches(String theElementName) {
        return mFieldName.equalsIgnoreCase(theElementName);
    }
}
