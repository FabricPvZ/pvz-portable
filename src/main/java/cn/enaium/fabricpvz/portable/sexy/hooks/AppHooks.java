package cn.enaium.fabricpvz.portable.sexy.hooks;

/**
 * Application lifecycle hooks fired by the framework at the call points named in
 * docs/research/01-framework-core.md §2.1.
 *
 * <p>Gameplay hooks live in {@code cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks}; this one covers the
 * application/session boundary so the mod API can expose Fabric-style
 * {@code GameLifecycleEvents.INITIALIZED}/{@code EXITING}. Every method has a no-op default, and the
 * registry returns a shared no-op dispatcher while nothing is registered.
 */
public interface AppHooks {

    /** Fired at the end of {@code SexyAppBase::Init} (SexyAppBase.cpp:3506), after resources loaded. */
    default void onGameInitialized() {
    }

    /** Fired at the start of {@code SexyAppBase::Shutdown} (SexyAppBase.cpp:1571-1615). */
    default void onGameExiting() {
    }
}
