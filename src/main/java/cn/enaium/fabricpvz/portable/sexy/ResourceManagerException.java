package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/ResourceManager.h:231-235.
 *
 * <p>Thrown by {@link ResourceManager#getImageThrow}, {@link ResourceManager#getSoundThrow} and
 * {@link ResourceManager#getFontThrow} when the requested id is missing. The upstream type derives
 * from {@code std::exception} and stores the manager's error text; in Java it is an unchecked
 * {@link RuntimeException} carrying the same message.
 */
public class ResourceManagerException extends RuntimeException {

    public ResourceManagerException(String theWhat) {
        super(theWhat);
    }
}
