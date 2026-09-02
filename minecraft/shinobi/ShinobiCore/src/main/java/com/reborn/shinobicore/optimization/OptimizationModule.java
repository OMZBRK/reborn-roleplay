package com.reborn.shinobicore.optimization;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-wide vanilla-feature kill switches driven by
 * {@code config.yml → optimization:}.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li>Writes a configurable set of {@link GameRule}s onto every
 *       loaded world (and onto worlds that load later) — daylight,
 *       weather, fire spread, mob spawning, patrols, phantoms,
 *       wandering traders, random tick speed.</li>
 *   <li>Pins each world to a fixed time-of-day when
 *       {@code do-daylight-cycle = false}.</li>
 *   <li>Optionally cancels {@link CreatureSpawnEvent} for every
 *       <i>natural</i> spawn reason — leaves player-driven spawns
 *       (eggs, breeding, command, custom plugin spawns like
 *       DummyManager) untouched.</li>
 *   <li>Optionally cancels every {@link WeatherChangeEvent} that
 *       would turn rain on, and clears any rain currently falling
 *       on apply.</li>
 *   <li>Optionally pushes per-world view + simulation distance.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * Build once in {@code ShinobiCore.onEnable}, call {@link #apply()}
 * after the plugin's listeners are registered. Re-call from
 * {@code /sc reload} to pick up config edits without a restart.
 *
 * <h2>Why it lives in the plugin</h2>
 * Same effect could be achieved by editing server.properties +
 * paper-world-defaults.yml + each world's level.dat gamerules — but
 * those settings live across multiple files, won't be re-applied
 * after a fresh world load, and can drift apart on multi-server
 * deploys. Centralising them in one config block makes the RP
 * server's "no vanilla loop" stance reproducible from a single file.
 */
public final class OptimizationModule implements Listener {

    /** Spawn reasons we treat as "natural" — cancellable when
     *  {@code block-natural-mob-spawns} is on. Everything else
     *  (eggs, breeding, command, custom, dispense, breeding,
     *  cured villager, mount, etc.) passes through. */
    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL =
            EnumSet.of(
                    CreatureSpawnEvent.SpawnReason.NATURAL,
                    CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
                    CreatureSpawnEvent.SpawnReason.JOCKEY,
                    CreatureSpawnEvent.SpawnReason.REINFORCEMENTS,
                    CreatureSpawnEvent.SpawnReason.RAID,
                    CreatureSpawnEvent.SpawnReason.PATROL,
                    CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
                    CreatureSpawnEvent.SpawnReason.VILLAGE_DEFENSE,
                    CreatureSpawnEvent.SpawnReason.LIGHTNING,
                    CreatureSpawnEvent.SpawnReason.SLIME_SPLIT,
                    CreatureSpawnEvent.SpawnReason.DROWNED,
                    CreatureSpawnEvent.SpawnReason.NETHER_PORTAL,
                    CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER);

    private final ShinobiCore plugin;

    private boolean enabled;
    private boolean blockNaturalSpawns;
    private boolean blockWeather;
    private long    fixedTime;
    private int     viewDistance;        // -1 = leave alone
    private int     simulationDistance;  // -1 = leave alone

    /** Snapshot of which gamerules to write + what value, indexed by
     *  config key (e.g. {@code "do-daylight-cycle"}). Built on each
     *  {@link #apply()} call. */
    private final Map<String, Object> gamerules = new HashMap<>();

    public OptimizationModule(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* -------------------------------------------------------- apply */

    /** Load config, write gamerules to every loaded world, set the
     *  per-world view/simulation distance and time of day. Idempotent
     *  — safe to call from /sc reload. */
    public void apply() {
        ConfigurationSection root = plugin.getConfig()
                .getConfigurationSection("optimization");
        if (root == null) { enabled = false; return; }

        enabled            = root.getBoolean("enabled", true);
        if (!enabled) {
            plugin.getLogger().info("[Optimization] disabled — skipping.");
            return;
        }

        blockNaturalSpawns = root.getBoolean("events.block-natural-mob-spawns", true);
        blockWeather       = root.getBoolean("events.block-weather-changes", true);
        fixedTime          = root.getLong("fixed-time", 6000L);
        viewDistance       = root.getInt("view-distance", -1);
        simulationDistance = root.getInt("simulation-distance", -1);

        gamerules.clear();
        ConfigurationSection grSection = root.getConfigurationSection("gamerules");
        if (grSection != null) {
            for (String key : grSection.getKeys(false)) {
                Object v = grSection.get(key);
                if (v != null) gamerules.put(key, v);
            }
        }

        for (World w : plugin.getServer().getWorlds()) {
            applyToWorld(w);
        }
        plugin.getLogger().info("[Optimization] applied: "
                + gamerules.size() + " gamerule(s), "
                + (blockNaturalSpawns ? "spawn-block ON, " : "spawn-block off, ")
                + (blockWeather ? "weather-block ON, " : "weather-block off, ")
                + "view=" + viewDistance + ", sim=" + simulationDistance);
    }

    private void applyToWorld(World w) {
        for (Map.Entry<String, Object> e : gamerules.entrySet()) {
            applyGamerule(w, e.getKey(), e.getValue());
        }
        // Pin time-of-day if doDaylightCycle is locked off.
        Boolean daylight = w.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE);
        if (daylight != null && !daylight) {
            w.setTime(fixedTime);
        }
        // Clear any rain that's already falling so the no-weather
        // setting takes effect immediately (otherwise the storm runs
        // until vanilla decides to stop).
        if (blockWeather) {
            w.setStorm(false);
            w.setThundering(false);
            w.setWeatherDuration(Integer.MAX_VALUE);
        }
        // Per-world distances. -1 leaves server.properties value.
        if (viewDistance       > 0) w.setViewDistance(viewDistance);
        if (simulationDistance > 0) w.setSimulationDistance(simulationDistance);
    }

    /** Map a config key to the matching {@link GameRule} and write
     *  the value if its type matches. Unknown keys are logged at
     *  WARNING so config typos surface fast. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyGamerule(World w, String key, Object value) {
        GameRule rule = GameRule.getByName(key);
        if (rule == null) {
            plugin.getLogger().warning("[Optimization] unknown gamerule: " + key);
            return;
        }
        try {
            if (rule.getType() == Boolean.class && value instanceof Boolean b) {
                w.setGameRule(rule, b);
            } else if (rule.getType() == Integer.class && value instanceof Number n) {
                w.setGameRule(rule, n.intValue());
            } else {
                plugin.getLogger().warning("[Optimization] gamerule " + key
                        + " expects " + rule.getType().getSimpleName()
                        + " but config gave " + value.getClass().getSimpleName());
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("[Optimization] failed to set "
                    + key + ": " + ex.getMessage());
        }
    }

    /* ----------------------------------------------- event handlers */

    /** New worlds loaded after startup (multi-world plugins, end / nether
     *  unload-load cycles) get the same treatment. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent ev) {
        if (!enabled) return;
        applyToWorld(ev.getWorld());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent ev) {
        if (!enabled || !blockNaturalSpawns) return;
        if (NATURAL.contains(ev.getSpawnReason())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent ev) {
        if (!enabled || !blockWeather) return;
        // Cancel any change that would turn rain ON. Letting "stop
        // raining" through is fine and avoids a soft-lock if rain is
        // already falling at apply().
        if (ev.toWeatherState()) {
            ev.setCancelled(true);
        }
    }
}
