package com.reborn.shinobicore.chakra;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Shows each online player's current chakra in the vanilla XP bar.
 *
 * <p>We repurpose:
 * <ul>
 *   <li>{@code player.setLevel((int) current)} — the big number is the
 *       integer amount of chakra remaining.</li>
 *   <li>{@code player.setExp(current / max)} — the green bar is a stable
 *       PERCENTAGE: empty bar when chakra is 0, full bar when chakra is max.
 *       Because we write the fill as a ratio (not the fractional part), the
 *       bar doesn't flicker as chakra drops between integer values.</li>
 * </ul>
 *
 * <p>When the player has no active character (no chakra pool), the XP bar is
 * hidden by setting level = 0 and exp = 0.
 *
 * <p>Toggle globally via config {@code chakra.display-as-xp}.
 */
public class ChakraDisplay {

    /** Avoid tiny updates so the bar doesn't jitter. */
    private static final float EXP_EPS = 0.01f;

    private final ShinobiCore plugin;
    private BukkitTask task;

    public ChakraDisplay(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("chakra.display-as-xp", true)) return;
        // Every 4 ticks is plenty: the bar is now a percentage that changes
        // only when chakra meaningfully changes.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 4L, 4L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void reloadConfig() { start(); }

    private void tick() {
        if (com.reborn.shinobicore.util.Tps.shouldDefer()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            ChakraPool pool = plugin.chakra().get(p);
            if (pool == null || pool == ChakraPool.EMPTY || pool.max() <= 0.0) {
                if (p.getLevel() != 0) p.setLevel(0);
                if (p.getExp() != 0f) p.setExp(0f);
                continue;
            }
            double cur = Math.max(0.0, Math.min(pool.max(), pool.current()));
            int whole = (int) Math.floor(cur);
            float ratio = (float) (cur / pool.max());
            if (ratio < 0f) ratio = 0f;
            if (ratio > 1f) ratio = 1f;
            // Some clients glitch at *exactly* 1.0; nudge it just under.
            if (ratio >= 1f) ratio = 0.9999f;

            if (p.getLevel() != whole) p.setLevel(whole);
            if (Math.abs(p.getExp() - ratio) > EXP_EPS) p.setExp(ratio);
        }
    }
}
