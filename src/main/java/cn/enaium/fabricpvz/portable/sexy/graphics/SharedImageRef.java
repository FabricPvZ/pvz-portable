package cn.enaium.fabricpvz.portable.sexy.graphics;

/**
 * Ported from src/SexyAppFramework/graphics/SharedImage.h (class {@code SharedImageRef}).
 *
 * <p>The upstream framework hands images out as reference-counted smart pointers so that
 * {@code ResourceManager} can release images that nobody holds any more. The Java port keeps the
 * same API shape (a nullable {@link Image} handle with explicit release) but relies on the garbage
 * collector instead of a refcount, so {@link #release()} simply clears the reference.
 *
 * <p>{@code ResourceManager.getImage/getImageThrow} and
 * {@code SexyAppBase.getSharedImage(..)/setSharedImage(..)} return and store this type; gameplay code
 * unwraps it with {@link #get()}.
 */
public final class SharedImageRef {

    private Image mImage;

    /** Upstream default constructor: holds no image. */
    public SharedImageRef() {
    }

    public SharedImageRef(Image image) {
        this.mImage = image;
    }

    /** Upstream {@code operator->()}/{@code GetImage()}: the held image, or {@code null}. */
    public Image get() {
        return mImage;
    }

    /** Upstream assignment: replaces the held image. */
    public void set(Image image) {
        this.mImage = image;
    }

    /** Upstream {@code operator bool() == false} check used all over the framework. */
    public boolean isNull() {
        return mImage == null;
    }

    public boolean isNotNull() {
        return mImage != null;
    }

    /** Drops the reference (the Java equivalent of the upstream refcount release). */
    public void release() {
        mImage = null;
    }

    @Override
    public String toString() {
        return "SharedImageRef[" + (mImage == null ? "null" : mImage.mFilePath) + "]";
    }
}
