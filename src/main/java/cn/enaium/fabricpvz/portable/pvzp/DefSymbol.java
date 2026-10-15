/*
 * Ported from src/PvzpLib/Definition.h:42-49 (class DefSymbol).
 */
package cn.enaium.fabricpvz.portable.pvzp;

/**
 * Ported from src/PvzpLib/Definition.h:42-49 (class DefSymbol).
 *
 * <p>Records the value of one flag bit (or one enum entry) of a flags/enum field. Upstream stores
 * {@code const char*} names and marks the end of a symbol array with {@code mSymbolName == nullptr}
 * ({@code { .mSymbolValue = -1, .mSymbolName = nullptr }}); in Java the terminator is a
 * {@link DefSymbol} whose {@link #mSymbolName} is {@code null}. Symbol arrays are the
 * {@code mExtraData} of a {@link DefField} of type {@code DT_ENUM}/{@code DT_FLAGS}
 * (upstream Definition.h:66-67).
 */
public final class DefSymbol {
    /** Upstream {@code mSymbolValue} (Definition.h:45): value of the flag bit or enum entry. */
    public final int mSymbolValue;

    /** Upstream {@code mSymbolName} (Definition.h:46): symbol name; {@code null} marks the end. */
    public final String mSymbolName;

    /** Ported from the upstream aggregate initialiser ({@code DefSymbol{value, name}}). */
    public DefSymbol(int theSymbolValue, String theSymbolName) {
        mSymbolValue = theSymbolValue;
        mSymbolName = theSymbolName;
    }

    /**
     * Ported from src/PvzpLib/Definition.cpp:652-666 ({@code DefSymbolValueFromString}).
     *
     * <p>Case-insensitive lookup of {@code theName} in {@code theSymbolMap} (upstream
     * {@code strcasecmp}); returns the symbol value in {@code theResultValue[0]} when found.
     */
    public static boolean symbolValueFromString(DefSymbol[] theSymbolMap, String theName, int[] theResultValue) {
        if (theSymbolMap == null) {
            return false;
        }
        for (DefSymbol aSymbol : theSymbolMap) {
            if (aSymbol == null || aSymbol.mSymbolName == null) {
                break;
            }
            if (aSymbol.mSymbolName.equalsIgnoreCase(theName)) {
                theResultValue[0] = aSymbol.mSymbolValue;
                return true;
            }
        }
        return false;
    }

    /** Diagnostic rendering of a symbol map (used by tests and error messages). */
    public static String describe(DefSymbol[] theSymbolMap) {
        StringBuilder aBuilder = new StringBuilder("[");
        for (int i = 0; theSymbolMap != null && i < theSymbolMap.length; i++) {
            DefSymbol aSymbol = theSymbolMap[i];
            if (aSymbol == null || aSymbol.mSymbolName == null) {
                break;
            }
            if (aBuilder.length() > 1) {
                aBuilder.append(", ");
            }
            aBuilder.append(aSymbol.mSymbolName).append('=').append(aSymbol.mSymbolValue);
        }
        return aBuilder.append(']').toString();
    }
}
