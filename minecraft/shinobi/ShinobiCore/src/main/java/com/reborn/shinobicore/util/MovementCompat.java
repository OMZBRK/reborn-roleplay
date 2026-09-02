package com.reborn.shinobicore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Reflective bridge to Rasengan's {@code Player#setMovementChecksExempt(boolean)}.
 *
 * <p>On the Rasengan server fork, toggling this makes the server <em>accept</em>
 * plugin-driven movement (dashes, grapple pulls, ziplines, air control) instead
 * of rubber-banding it with the "moved too quickly" correction or the flight
 * kick. On any other server (vanilla Paper / upstream Purpur) the method is
 * absent and every call is a silent no-op, so the plugins still run unchanged.
 *
 * <p>Resolution is done once per process and cached, mirroring
 * {@code TabListManager#setListed}.
 */
public final class MovementCompat {

    private static volatile Method SET_EXEMPT;
    private static volatile boolean RESOLVED;
    private static volatile boolean WARNED;

    private MovementCompat() {}

    /**
     * Exempt (or un-exempt) a player from the server's movement-speed / flight
     * checks. No-op on servers that aren't Rasengan.
     */
    public static void setExempt(Player p, boolean exempt) {
        Method m = resolve();
        if (m == null || p == null) return;
        try {
            m.invoke(p, exempt);
        } catch (ReflectiveOperationException ex) {
            if (!WARNED) {
                WARNED = true;
                Bukkit.getLogger().warning(
                        "[ShinobiCore] setMovementChecksExempt failed: " + ex.getMessage());
            }
        }
    }

    /** True when running on a server that exposes the Rasengan movement API. */
    public static boolean available() {
        return resolve() != null;
    }

    private static Method resolve() {
        if (!RESOLVED) {
            synchronized (MovementCompat.class) {
                if (!RESOLVED) {
                    try {
                        SET_EXEMPT = Player.class.getMethod("setMovementChecksExempt", boolean.class);
                    } catch (NoSuchMethodException ignore) {
                        SET_EXEMPT = null;   // not Rasengan
                    }
                    RESOLVED = true;
                }
            }
        }
        return SET_EXEMPT;
    }
}
