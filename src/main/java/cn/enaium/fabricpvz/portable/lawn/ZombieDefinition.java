/*
 * Ported from src/Lawn/Zombie.h:397-408.
 *
 * Upstream struct ZombieDefinition; public fields keep the upstream names.
 * Instances live in Zombie.gZombieDefs and are shared — callers must not mutate them.
 */
package cn.enaium.fabricpvz.portable.lawn;

public class ZombieDefinition {
    public int mZombieType;
    public int mReanimationType;
    public int mZombieValue;
    public int mStartingLevel;
    public int mFirstAllowedWave;
    public int mPickWeight;
    public String mZombieName;

    public ZombieDefinition(int theZombieType, int theReanimationType, int theZombieValue, int theStartingLevel,
            int theFirstAllowedWave, int thePickWeight, String theZombieName) {
        mZombieType = theZombieType;
        mReanimationType = theReanimationType;
        mZombieValue = theZombieValue;
        mStartingLevel = theStartingLevel;
        mFirstAllowedWave = theFirstAllowedWave;
        mPickWeight = thePickWeight;
        mZombieName = theZombieName;
    }
}
