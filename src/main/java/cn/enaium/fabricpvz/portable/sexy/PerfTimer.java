package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/PerfTimer.h:33 and src/SexyAppFramework/misc/PerfTimer.cpp:47.
 *
 * <p>Faithful port of {@code Sexy::PerfTimer}. Upstream measures with
 * {@code SDL_GetPerformanceCounter()/SDL_GetPerformanceFrequency()}; the Java port uses
 * {@code System.nanoTime()} (frequency 1e9), keeping the same millisecond-duration formulas.
 */
public class PerfTimer {
    protected long mStart;
    protected double mDuration;
    protected boolean mRunning;

    private static long counter() {
        return System.nanoTime();
    }

    private static long frequency() {
        return 1_000_000_000L;
    }

    public PerfTimer() {
        mDuration = 0;
        mStart = 0;
        mRunning = false;
    }

    // CalcDuration
    protected void calcDuration() {
        long anEnd = counter();
        long aFreq = frequency();
        mDuration = ((anEnd - mStart) * 1000) / (double) aFreq;
    }

    // Start
    public void start() {
        mRunning = true;
        mStart = counter();
    }

    // SetStartTime
    public void setStartTime(int theTimeMillisecondsAgo) {
        mStart = counter() - (long) theTimeMillisecondsAgo * frequency() / 1000;
        mRunning = true;
    }

    // Stop
    public void stop() {
        if (mRunning) {
            calcDuration();
            mRunning = false;
        }
    }

    // GetDuration
    public double getDuration() {
        if (mRunning) {
            calcDuration();
        }

        return mDuration;
    }
}
