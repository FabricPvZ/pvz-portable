/*
 * Ported from src/PvzpLib/Definition.h:70-77 (class DefMap, the mConstructorFunc member).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:70-77 (class DefMap).
 *
 * <p>The {@code mMapFields} array of upstream ends with a {@link DefField} whose {@code mFieldName} is
 * the empty string; in Java the array end doubles as the terminator, so maps are declared without a
 * trailing terminator entry. The upstream {@code mConstructorFunc} is the constructor of the
 * definition class, called by {@code DefinitionLoadMap} (Definition.cpp:1063-1067) to give the freshly
 * allocated definition its default values.
 */
public interface DefConstructor {
    /**
     * Upstream {@code void* (*mConstructorFunc)(void*)} (Definition.h:75) with the preallocated
     * pointer replaced by a fresh instance: returns a new default-initialised definition, exactly the
     * state upstream's {@code <Type>Constructor} leaves behind (e.g. {@code ReanimatorDefinition}
     * defaults {@code mFPS} to 12).
     */
    Object construct();
}
