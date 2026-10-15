package cn.enaium.fabricpvz.portable.lawn.hooks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for {@link LawnHooks} implementations.
 *
 * <p>The mod API registers one implementation that fans out to all mod listeners; multiple
 * registrations are chained in registration order (each hook's return value feeds the next one).
 * The game calls {@link #dispatcher()} once per hook site; when nothing is registered the returned
 * dispatcher is the shared no-op instance, so the overhead is a single virtual call.
 */
public final class LawnHooksRegistry {

    private static final LawnHooks NONE = new LawnHooks() {
    };

    private static final List<LawnHooks> HOOKS = new CopyOnWriteArrayList<>();

    /** Chained dispatcher over the registered hooks; always non-null. */
    private static final LawnHooks DISPATCHER = new LawnHooks() {
        @Override
        public void onLevelStart(cn.enaium.fabricpvz.portable.lawn.Board board, int level, int gameMode, int background) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onLevelStart(board, level, gameMode, background);
            }
        }

        @Override
        public void onLevelEnd(cn.enaium.fabricpvz.portable.lawn.Board board, int result) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onLevelEnd(board, result);
            }
        }

        @Override
        public boolean onWaveStart(cn.enaium.fabricpvz.portable.lawn.Board board, int waveIndex, int[] zombieTypes) {
            boolean cancelled = false;
            for (LawnHooks hooks : HOOKS) {
                cancelled |= hooks.onWaveStart(board, waveIndex, zombieTypes);
            }
            return cancelled;
        }

        @Override
        public boolean onZombieSpawn(cn.enaium.fabricpvz.portable.lawn.Board board, int zombieType, int row, boolean fromWave) {
            boolean cancelled = false;
            for (LawnHooks hooks : HOOKS) {
                cancelled |= hooks.onZombieSpawn(board, zombieType, row, fromWave);
            }
            return cancelled;
        }

        @Override
        public void onZombieDeath(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.lawn.Zombie zombie) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onZombieDeath(board, zombie);
            }
        }

        @Override
        public int onZombieDamage(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.lawn.Zombie zombie, int amount, int damageFlags) {
            int value = amount;
            for (LawnHooks hooks : HOOKS) {
                value = hooks.onZombieDamage(board, zombie, value, damageFlags);
            }
            return value;
        }

        @Override
        public void onPlantPlaced(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.lawn.Plant plant, int gridX, int gridY) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onPlantPlaced(board, plant, gridX, gridY);
            }
        }

        @Override
        public void onPlantRemoved(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.lawn.Plant plant, int cause) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onPlantRemoved(board, plant, cause);
            }
        }

        @Override
        public int onPlantDamage(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.lawn.Plant plant, int amount) {
            int value = amount;
            for (LawnHooks hooks : HOOKS) {
                value = hooks.onPlantDamage(board, plant, value);
            }
            return value;
        }

        @Override
        public int onSunCollected(cn.enaium.fabricpvz.portable.lawn.Board board, int amount, int source) {
            int value = amount;
            for (LawnHooks hooks : HOOKS) {
                value = hooks.onSunCollected(board, value, source);
            }
            return value;
        }

        @Override
        public int onPlantingValidated(cn.enaium.fabricpvz.portable.lawn.Board board, int gridX, int gridY, int seedType, int reason) {
            int value = reason;
            for (LawnHooks hooks : HOOKS) {
                value = hooks.onPlantingValidated(board, gridX, gridY, seedType, value);
            }
            return value;
        }

        @Override
        public void onTick(cn.enaium.fabricpvz.portable.lawn.Board board, int tick) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onTick(board, tick);
            }
        }

        @Override
        public void onPreDraw(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.sexy.graphics.Graphics graphics) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onPreDraw(board, graphics);
            }
        }

        @Override
        public void onPostDraw(cn.enaium.fabricpvz.portable.lawn.Board board, cn.enaium.fabricpvz.portable.sexy.graphics.Graphics graphics) {
            for (LawnHooks hooks : HOOKS) {
                hooks.onPostDraw(board, graphics);
            }
        }
    };

    private LawnHooksRegistry() {
    }

    /** The dispatcher used by the game at every hook site. */
    public static LawnHooks dispatcher() {
        return HOOKS.isEmpty() ? NONE : DISPATCHER;
    }

    public static void add(LawnHooks hooks) {
        HOOKS.add(hooks);
    }

    public static boolean remove(LawnHooks hooks) {
        return HOOKS.remove(hooks);
    }

    public static int count() {
        return HOOKS.size();
    }
}
