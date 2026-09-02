package com.reborn.shinobiabilities.mobility;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Bridge to the Rasengan-fork-only additions to the Bukkit API.
 *
 * <p>The Rasengan server exposes {@code Player#setMovementChecksExempt(boolean)}
 * so plugins can tell the server to accept plugin-driven velocity instead of
 * rubber-banding it. That method is <em>not</em> part of the stock Purpur/Paper
 * API we compile against, so we reach it reflectively: the lookup is cached once
 * and the call becomes a silent no-op on any server that lacks the method
 * (exactly the "no-op on non-Rasengan servers" contract the mobility code
 * relies on).
 */
final class RasenganCompat {

    /** Cached handle to {@code Player#setMovementChecksExempt(boolean)}, or null if the fork lacks it. */
    private static final Method SET_MOVEMENT_CHECKS_EXEMPT = resolve();

    private RasenganCompat() {
    }

    private static Method resolve() {
        try {
            return Player.class.getMethod("setMovementChecksExempt", boolean.class);
        } catch (NoSuchMethodException e) {
            return null; // non-Rasengan server: feature simply unavailable
        }
    }

    /**
     * Exempt (or un-exempt) a player from server-side movement checks.
     * No-op when the running server is not a Rasengan fork.
     */
    static void setMovementChecksExempt(Player p, boolean exempt) {
        if (SET_MOVEMENT_CHECKS_EXEMPT == null) return;
        try {
            SET_MOVEMENT_CHECKS_EXEMPT.invoke(p, exempt);
        } catch (ReflectiveOperationException ignored) {
            // Method vanished or threw — fall back to vanilla movement handling.
        }
    }
}
