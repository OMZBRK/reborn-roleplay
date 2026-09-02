package com.reborn.shinobicore.util;

import org.bukkit.Bukkit;

/**
 * Server load helpers — wrap {@link Bukkit#getTPS()} so periodic
 * tickers can cheaply skip themselves when the server is already
 * lagging.
 *
 * <p>Use from any timer that does heavy work (disk I/O, scoreboard
 * rebuild, particle fan-out, AI recomputation, etc.):
 * <pre>{@code
 *   if (Tps.shouldDefer()) return;
 *   doExpensiveWork();
 * }</pre>
 *
 * <p>Threshold is 18.0 by default — TPS sits at 20 on a healthy
 * server, drops below 18 only when a tick has visibly stuttered, so
 * deferring there avoids piling more work onto an already-stressed
 * tick without being so eager that it skips during normal noise.
 */
public final class Tps {

    /** TPS at or below this triggers deferral. */
    public static final double DEFER_THRESHOLD = 18.0;

    private Tps() {}

    /** True when the 1-minute TPS is below {@link #DEFER_THRESHOLD}.
     *  Pair with an early-return at the top of any periodic task that
     *  can safely skip a round. */
    public static boolean shouldDefer() {
        return current() < DEFER_THRESHOLD;
    }

    /** Current 1-minute TPS, capped at the server's normal max (20). */
    public static double current() {
        return Bukkit.getTPS()[0];
    }
}
