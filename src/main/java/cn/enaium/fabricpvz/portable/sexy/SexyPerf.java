package cn.enaium.fabricpvz.portable.sexy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ported from src/SexyAppFramework/misc/PerfTimer.h:51 and src/SexyAppFramework/misc/PerfTimer.cpp:82.
 *
 * <p>Faithful port of {@code Sexy::SexyPerf}. Upstream orders the per-name records with
 * {@code strcasecmp}, reproduced here with {@link String#CASE_INSENSITIVE_ORDER}; timing uses
 * {@link System#nanoTime()} in place of {@code SDL_GetPerformanceCounter()} (frequency 1e9).
 * The {@link #getResults()} line formats are kept byte-for-byte with upstream's
 * {@code std::format} calls (including its stray literal {@code %} before the time percentage).
 */
public class SexyPerf {
    private static final class PerfInfo {
        final String mPerfName;
        long mStartTime;
        long mDuration;
        double mMillisecondDuration;
        double mLongestCall;
        int mStartCount;
        int mCallCount;

        PerfInfo(String theName) {
            mPerfName = theName;
            mStartTime = 0;
            mDuration = 0;
            mLongestCall = 0;
            mStartCount = 0;
            mCallCount = 0;
        }
    }

    private static final class PerfRecord {
        final String mName;
        final long mTime;
        final boolean mStart;

        PerfRecord(String theName, boolean start) {
            mName = theName;
            mStart = start;
            mTime = counter();
        }
    }

    private static final TreeMap<String, PerfInfo> gPerfInfoSet = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final List<PerfRecord> gPerfRecordVector = new ArrayList<>();
    private static boolean gPerfOn = false;
    private static long gStartTime;
    private static long gCollateTime;
    private static double gDuration = 0;
    private static int gStartCount = 0;
    private static int gPerfRecordTop = 0;

    private static long counter() {
        return System.nanoTime();
    }

    private static long frequency() {
        return 1_000_000_000L;
    }

    private static void insertPerfRecord(PerfRecord theRecord) {
        if (theRecord.mStart) {
            PerfInfo anInfo = gPerfInfoSet.get(theRecord.mName);
            if (anInfo == null) {
                anInfo = new PerfInfo(theRecord.mName);
                gPerfInfoSet.put(theRecord.mName, anInfo);
            }
            anInfo.mCallCount++;

            if (++anInfo.mStartCount == 1) {
                anInfo.mStartTime = theRecord.mTime;
            }
        } else {
            PerfInfo anInfo = gPerfInfoSet.get(theRecord.mName);
            if (anInfo != null) {
                if (--anInfo.mStartCount == 0) {
                    long aDuration = theRecord.mTime - anInfo.mStartTime;
                    anInfo.mDuration += aDuration;

                    if (aDuration > anInfo.mLongestCall) {
                        anInfo.mLongestCall = (double) aDuration;
                    }
                }
            }
        }
    }

    private static void collatePerfRecords() {
        long aTime1;
        long aTime2;
        aTime1 = counter();

        for (int i = 0; i < gPerfRecordTop; i++) {
            insertPerfRecord(gPerfRecordVector.get(i));
        }

        gPerfRecordTop = 0;
        aTime2 = counter();

        gCollateTime += aTime2 - aTime1;
    }

    private static void pushPerfRecord(PerfRecord theRecord) {
        if (gPerfRecordTop >= gPerfRecordVector.size()) {
            gPerfRecordVector.add(theRecord);
        } else {
            gPerfRecordVector.set(gPerfRecordTop, theRecord);
        }

        ++gPerfRecordTop;
    }

    // IsPerfOn
    public static boolean isPerfOn() {
        return gPerfOn;
    }

    // BeginPerf
    public static void beginPerf(boolean measurePerfOverhead) {
        gPerfInfoSet.clear();
        gPerfRecordTop = 0;
        gStartCount = 0;
        gCollateTime = 0;

        if (!measurePerfOverhead) {
            gPerfOn = true;
        }

        gStartTime = counter();
    }

    public static void beginPerf() {
        beginPerf(false);
    }

    // EndPerf
    public static void endPerf() {
        long anEndTime;
        anEndTime = counter();

        collatePerfRecords();

        gPerfOn = false;

        long aFreq = frequency();

        gDuration = ((double) (anEndTime - gStartTime - gCollateTime)) * 1000 / aFreq;

        for (Map.Entry<String, PerfInfo> anEntry : gPerfInfoSet.entrySet()) {
            PerfInfo anInfo = anEntry.getValue();
            anInfo.mMillisecondDuration = (double) anInfo.mDuration * 1000 / aFreq;
            anInfo.mLongestCall = anInfo.mLongestCall * 1000 / aFreq;
        }
    }

    // StartTiming
    public static void startTiming(String theName) {
        if (gPerfOn) {
            ++gStartCount;
            pushPerfRecord(new PerfRecord(theName, true));
        }
    }

    // StopTiming
    public static void stopTiming(String theName) {
        if (gPerfOn) {
            pushPerfRecord(new PerfRecord(theName, false));
            if (--gStartCount == 0) {
                collatePerfRecords();
            }
        }
    }

    // GetResults
    public static String getResults() {
        StringBuilder aResult = new StringBuilder();

        aResult.append(String.format(Locale.ROOT, "Total Time: %.2f\n", gDuration));
        for (Map.Entry<String, PerfInfo> anEntry : gPerfInfoSet.entrySet()) {
            PerfInfo anInfo = anEntry.getValue();
            aResult.append(String.format(Locale.ROOT,
                    "%s (%d calls, %%%.2f time): %.2f (%.2f avg, %.2f longest)\n",
                    anInfo.mPerfName,
                    anInfo.mCallCount,
                    anInfo.mMillisecondDuration / gDuration * 100,
                    anInfo.mMillisecondDuration,
                    anInfo.mMillisecondDuration / anInfo.mCallCount,
                    anInfo.mLongestCall));
        }

        return aResult.toString();
    }
}
