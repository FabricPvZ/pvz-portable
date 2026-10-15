package cn.enaium.fabricpvz.portable.pvzp;

import cn.enaium.fabricpvz.portable.sexy.ResourceManager;

/**
 * Ported from src/Resources.h:1858, 1905 (the function-pointer hook
 * {@code gExtractResourcesByName} installed by main.cpp:96).
 *
 * <p>The framework's {@link ResourceManager} does not know the game's resource ids; after a group
 * has been loaded, the game binds the loaded objects into its own {@code gResources} table through
 * this hook. It is invoked from {@link PvzpResourceManager#pvzpLoadResources}.
 */
@FunctionalInterface
public interface ResourceExtractor {

    /** Upstream {@code Sexy::ExtractResourcesByName(ResourceManager*, const char*)}. */
    boolean extract(ResourceManager theResourceManager, String theName);
}
