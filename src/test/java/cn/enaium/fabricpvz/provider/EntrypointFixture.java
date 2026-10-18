package cn.enaium.fabricpvz.provider;

/**
 * Stand-in game entry point for {@code PvzGameProviderTest#launchCallsTheMainClassOfTheGame}: the
 * provider launches whatever class {@code -Dpvz.game.mainClass} names, which is how the port's own
 * tests replace the real entry point with a fixture.
 */
public final class EntrypointFixture {

    private static int calls;
    private static String[] arguments;

    private EntrypointFixture() {
    }

    public static void main(String[] args) {
        calls++;
        arguments = args;
    }

    static void reset() {
        calls = 0;
        arguments = null;
    }

    static int calls() {
        return calls;
    }

    static String[] arguments() {
        return arguments;
    }
}
