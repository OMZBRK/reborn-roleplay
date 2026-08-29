package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodic snapshot of every online player's active character so a
 * crash / SIGKILL / power loss doesn't roll a session back to the
 * last voluntary disconnect.
 *
 * <h2>What gets captured</h2>
 * For each online player with an active character:
 * <ul>
 *   <li>Current HP (from {@link Player#getHealth()}).</li>
 *   <li>Current location (world + x/y/z/yaw/pitch).</li>
 *   <li>Inventory snapshot (main + armor + offhand).</li>
 *   <li>Chakra pool — already live on the character object, so
 *       {@code save(c)} picks it up without an explicit capture.</li>
 * </ul>
 *
 * <h2>Interval</h2>
 * Configurable via {@code character.auto-save-seconds} in
 * {@code config.yml}; defaults to 60s. The task starts on
 * {@link #start()} and cancels on {@link #stop()}; a value of {@code <= 0}
 * disables it (voluntary-only saves).
 */
public class CharacterAutoSave {

    private static final long DEFAULT_SECONDS = 60L;

    private final ShinobiCore plugin;
    private BukkitTask task;

    public CharacterAutoSave(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long seconds = plugin.getConfig().getLong("character.auto-save-seconds", DEFAULT_SECONDS);
        if (seconds <= 0) {
            plugin.getLogger().info("Character auto-save disabled (character.auto-save-seconds <= 0).");
            return;
        }
        long ticks = seconds * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
        plugin.getLogger().info("Character auto-save running every " + seconds + "s.");
    }

    public void stop() {
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignore) {}
            task = null;
        }
    }

    /** Manually flush — used from {@code onDisable} so a graceful
     *  shutdown leaves every online player at their current state
     *  rather than their last periodic snapshot. */
    public void flushAll() {
        for (Player p : Bukkit.getOnlinePlayers()) snapshot(p);
    }

    /* ----------------------------------------------------------- internals */

    private void tick() {
        // TPS-aware deferral via the shared Tps helper — if the server
        // is already lagging, skip this round so we don't pile a YAML
        // serialise/dispatch onto an already-stressed tick. The next
        // periodic call catches up once TPS recovers; graceful
        // shutdown still flushes everything via flushAll().
        if (com.reborn.shinobicore.util.Tps.shouldDefer()) {
            plugin.getLogger().fine("Auto-save skipped this round (TPS="
                    + String.format("%.1f", com.reborn.shinobicore.util.Tps.current())
                    + ").");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                snapshot(p);
            } catch (Throwable ex) {
                plugin.getLogger().warning(
                        "Auto-save failed for " + p.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void snapshot(Player p) {
        if (p == null || !p.isOnline()) return;
        // Staff builders sit in creative with their RP inventory swapped
        // out; capturing here would overwrite the real inventory with
        // build junk. The snapshot is preserved in StaffBuildManager and
        // captured on force-exit (quit / KO / switch / /staff build).
        if (plugin.isStaffBuilding(p.getUniqueId())) return;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return;
        // The captures are diff-gated inside the setters; if nothing
        // actually changed since the last save (player parked, no combat,
        // no chakra movement), the record stays clean and we skip the
        // whole roster-file re-serialize for this player.
        c.setCurrentHp(p.getHealth());
        c.setLastLocation(p.getLocation());
        c.captureInventoryFrom(p);
        if (c.dirty()) plugin.characters().save(c);
    }
}
