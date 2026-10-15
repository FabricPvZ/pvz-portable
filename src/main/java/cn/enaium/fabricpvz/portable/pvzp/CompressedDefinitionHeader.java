/*
 * Ported from src/PvzpLib/Definition.h:105-110 (class CompressedDefinitionHeader).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:105-110 (class CompressedDefinitionHeader).
 *
 * <p>Prepended to the zlib-compressed payload of a compiled definition cache; used to verify data
 * integrity when decompressing (Definition.cpp:534-558 {@code DefinitionUncompressCompiledBuffer},
 * Definition.cpp:1240-1248 {@code DefinitionCompressCompiledBuffer}). Both fields are little-endian
 * on disk.
 */
public final class CompressedDefinitionHeader {
    /** Upstream {@code mCookie} (Definition.h:108): compression validation cookie {@code 0xDEADFED4}. */
    public int mCookie;

    /** Upstream {@code mUncompressedSize} (Definition.h:109): length of the uncompressed payload. */
    public int mUncompressedSize;

    public CompressedDefinitionHeader() {
    }

    public CompressedDefinitionHeader(int theCookie, int theUncompressedSize) {
        mCookie = theCookie;
        mUncompressedSize = theUncompressedSize;
    }
}
