package cn.enaium.fabricpvz.portable.sexy;

/**
 * Ported from src/SexyAppFramework/misc/Ratio.h:31 and src/SexyAppFramework/misc/Ratio.cpp:33.
 *
 * <p>Faithful port of {@code Sexy::Ratio}: a numerator/denominator pair kept in lowest terms.
 * {@link #set(int, int)} reproduces upstream's Euclid reduction exactly (including its
 * division-by-zero behaviour when both terms are zero).
 */
public class Ratio {
    public int mNumerator;
    public int mDenominator;

    public Ratio() {
        mNumerator = 1;
        mDenominator = 1;
    }

    public Ratio(int theNumerator, int theDenominator) {
        set(theNumerator, theDenominator);
    }

    // Set
    public void set(int theNumerator, int theDenominator) {
        // find the greatest-common-denominator of theNumerator and theDenominator.
        int t;
        int a = theNumerator;
        int b = theDenominator;
        while (b != 0) {
            t = b;
            b = a % b;
            a = t;
        }

        // divide by the g-c-d to reduce mNumerator/mDenominator to lowest terms.
        // PORT-NOTE: if both terms are 0, upstream divides by zero (undefined); Java throws.
        mNumerator = theNumerator / a;
        mDenominator = theDenominator / a;
    }

    // operator==
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ratio theRatio)) {
            return false;
        }
        return mNumerator == theRatio.mNumerator && mDenominator == theRatio.mDenominator;
    }

    @Override
    public int hashCode() {
        return 31 * mNumerator + mDenominator;
    }

    // operator!=
    public boolean notEquals(Ratio theRatio) {
        return !this.equals(theRatio);
    }

    // operator<
    // PORT-NOTE: upstream performs plain signed 32-bit multiplications here; Java int
    // arithmetic wraps identically, so the (quirky) comparison result is preserved.
    public boolean lessThan(Ratio theRatio) {
        return (mNumerator * theRatio.mDenominator / mDenominator < theRatio.mNumerator)
                || (mNumerator < theRatio.mNumerator * mDenominator / theRatio.mDenominator);
    }

    // operator*(int)
    public int times(int theInt) {
        return theInt * mNumerator / mDenominator;
    }

    // operator/(int)
    public int divide(int theInt) {
        return theInt * mDenominator / mNumerator;
    }

    // operator*(int, const Ratio&)
    public static int times(int theInt, Ratio theRatio) {
        return theInt * theRatio.mNumerator / theRatio.mDenominator;
    }

    // operator/(int, const Ratio&)
    public static int divide(int theInt, Ratio theRatio) {
        return theInt * theRatio.mDenominator / theRatio.mNumerator;
    }

    @Override
    public String toString() {
        return mNumerator + "/" + mDenominator;
    }
}
