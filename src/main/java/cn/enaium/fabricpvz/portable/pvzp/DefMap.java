/*
 * Ported from src/PvzpLib/Definition.h:70-77 (class DefMap).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:70-77 (class DefMap).
 *
 * <p>Describes the storage format of a definition class ({@code _DefClass}) and how to read it:
 * {@link #mMapFields} is one {@link DefField} per member, {@link #mDefSize} is the size of the flat
 * compiled struct image (upstream {@code sizeof(_DefClass)}, the initial read length), and
 * {@link #mConstructorFunc} builds a default-initialised instance.
 *
 * <p>Maps are declared by the classes that own the definition types (e.g. the {@code ReanimatorFormat}
 * maps built from {@link DefMapBuilder}); {@link Definition} drives them.
 */
public final class DefMap {
    /** Upstream {@code mMapFields} (Definition.h:73): one entry per member of {@code _DefClass}, in read order. */
    public final DefField[] mMapFields;

    /**
     * Upstream {@code mDefSize} (Definition.h:74): size of one instance, i.e. the length of the raw
     * struct image in the compiled cache. Every map must describe the same layout its writer uses, so
     * {@link DefMapBuilder} derives it from the field list.
     */
    public final int mDefSize;

    /** Upstream {@code mConstructorFunc} (Definition.h:75): builds the default definition instance. */
    public final DefConstructor mConstructorFunc;

    public DefMap(DefField[] theMapFields, int theDefSize, DefConstructor theConstructorFunc) {
        mMapFields = theMapFields;
        mDefSize = theDefSize;
        mConstructorFunc = theConstructorFunc;
    }

    /** Upstream {@code theDefMap->mConstructorFunc(theDefinition)}: a fresh default definition. */
    public Object newDefinition() {
        return mConstructorFunc.construct();
    }

    /**
     * Upstream walks the field list until the empty-named terminator (Definition.cpp:483). In Java the
     * array end is the terminator, so this returns the index one past the last usable field.
     */
    public int fieldCount() {
        int aCount = 0;
        while (aCount < mMapFields.length && mMapFields[aCount] != null && !mMapFields[aCount].mFieldName.isEmpty()) {
            aCount++;
        }
        return aCount;
    }

    /**
     * Case-insensitive field lookup by the name used in the XML/cache format (upstream
     * {@code strcasecmp}, Definition.cpp:930); returns null when the element is unknown.
     */
    public DefField findField(String theFieldName) {
        for (int i = 0; i < fieldCount(); i++) {
            if (mMapFields[i].nameMatches(theFieldName)) {
                return mMapFields[i];
            }
        }
        return null;
    }
}
