package cn.enaium.fabricpvz.portable.sexy.hooks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry for {@link AppHooks} implementations (see {@code LawnHooksRegistry} for the pattern). */
public final class AppHooksRegistry {

    private static final AppHooks NONE = new AppHooks() {
    };

    private static final List<AppHooks> HOOKS = new CopyOnWriteArrayList<>();

    private static final AppHooks DISPATCHER = new AppHooks() {
        @Override
        public void onGameInitialized() {
            for (AppHooks hooks : HOOKS) {
                hooks.onGameInitialized();
            }
        }

        @Override
        public void onGameExiting() {
            for (AppHooks hooks : HOOKS) {
                hooks.onGameExiting();
            }
        }
    };

    private AppHooksRegistry() {
    }

    public static AppHooks dispatcher() {
        return HOOKS.isEmpty() ? NONE : DISPATCHER;
    }

    public static void add(AppHooks hooks) {
        HOOKS.add(hooks);
    }

    public static boolean remove(AppHooks hooks) {
        return HOOKS.remove(hooks);
    }

    public static int count() {
        return HOOKS.size();
    }
}
