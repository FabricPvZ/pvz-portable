package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/PerfTimer.h:64.
 *
 * <p>Faithful port of {@code Sexy::SexyAutoPerf}: a scoped {@code StartTiming}/{@code StopTiming}
 * pair. Upstream stops in the destructor; the Java equivalent is {@link #close()} (or an explicit
 * {@link #stop()}), so try-with-resources matches the C++ scoped-lifetime idiom.
 */
public class SexyAutoPerf implements AutoCloseable {
    public String mName;
    public boolean mIsStarted;

    public SexyAutoPerf(String theName) {
        mName = theName;
        mIsStarted = true;
        SexyPerf.startTiming(theName);
    }

    public SexyAutoPerf(String theName, boolean doStart) {
        mName = theName;
        mIsStarted = doStart;
        if (doStart) {
            SexyPerf.startTiming(theName);
        }
    }

    // ~SexyAutoPerf()
    @Override
    public void close() {
        stop();
    }

    // Start
    public void start() {
        if (!mIsStarted) {
            mIsStarted = true;
            SexyPerf.startTiming(mName);
        }
    }

    // Stop
    public void stop() {
        if (mIsStarted) {
            SexyPerf.stopTiming(mName);
            mIsStarted = false;
        }
    }
}
