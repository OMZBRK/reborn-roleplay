package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.LevelTable;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Naruto Run — sneak-TAP toggle. While active: movement-speed attribute
 * multiplier, periodic chakra drain, wind SFX loop, subtle trail, and a
 * boost multiplier applied to DJ/WJ velocities.
 *
 * <h2>Attribute hygiene (the fix-#5 lesson)</h2>
 * The modifier is keyed by a stable {@link org.bukkit.NamespacedKey} —
 * removal matches by key, never by display name. Cleanup runs on
 * toggle-off, character switch, KO, quit, join (stale-state sweep) and
 * plugin disable.
 */
public final class NarutoRun {

    private static final String KEY_NAME = "naruto_run_speed";

    /** Verrou de ré-activation après une interruption (dégâts) — empêche de
     *  relancer la course instantanément. Tunable ici. */
    private static final long INTERRUPT_LOCKOUT_MS = 30_000L;

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ToggleStore toggles;
    private final Set<UUID> active = new HashSet<>();
    /** Dernière interruption (dégâts) par joueur — base du verrou de relance. */
    private final Map<UUID, Long> lastInterruptMs = new HashMap<>();
    /** Live speed multiplier per running player (base → max while moving). */
    private final Map<UUID, Double> currentMult = new HashMap<>();
    /** Accumulated MOVING time (ms) — the ramp only advances while moving. */
    private final Map<UUID, Long> rampAccumMs = new HashMap<>();
    /** Consecutive not-moving time (ms); the ramp resets after the grace. */
    private final Map<UUID, Long> stoppedMs = new HashMap<>();
    private BukkitTask ticker;
    private int tickCounter;
    /** Path registry (injected after construction). Voie du Flux (Path Two)
     *  owns its own movement speed, so for a Path-Two runner we skip BOTH the
     *  per-slot unlock gate and our own speed attribute — here Naruto Run is
     *  just the run-state (chakra drain, SFX, particles) for the Flux module. */
    private MobilityPaths paths;

    public NarutoRun(JavaPlugin plugin, CoreServices core, ToggleStore toggles) {
        this.plugin = plugin;
        this.core = core;
        this.toggles = toggles;
    }

    /** Wire the path registry once it exists (built after MobilityModule). */
    public void setPaths(MobilityPaths paths) { this.paths = paths; }

    /** True when this player's active character runs the Voie du Flux. */
    private boolean isFlux(Player p) {
        return paths != null && paths.pathOf(p) == MobilityPaths.Path.TWO;
    }

    /* ------------------------------------------------------------- config */

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mobility.naruto-run.enabled", true);
    }

    private double speedMultiplier() {
        return plugin.getConfig().getDouble("mobility.naruto-run.speed-multiplier", 1.6);
    }

    /** Top speed multiplier reached at the end of the accel ramp ("speed 20"). */
    private double maxSpeedMultiplier() {
        return plugin.getConfig().getDouble("mobility.naruto-run.max-speed-multiplier", 10.0);
    }

    /** Live movement-speed multiplier (1.0 when not running) — other mobility
     *  abilities scale their velocity by this so they speed up with the run. */
    public double speedFactor(Player p) {
        return p == null ? 1.0 : currentMult.getOrDefault(p.getUniqueId(), 1.0);
    }

    private double costPerSecond() {
        return plugin.getConfig().getDouble("mobility.naruto-run.chakra-cost-per-second", 3.0);
    }

    private double minChakraToStart() {
        return plugin.getConfig().getDouble("mobility.naruto-run.min-chakra-to-start", 5.0);
    }

    public long tapMaxMs() {
        return plugin.getConfig().getLong("mobility.naruto-run.toggle-tap-max-ms", 300L);
    }

    /** Horizontal boost for other mobility abilities — scales with the live
     *  run-speed ramp, so dashes/leaps get faster as the run accelerates. */
    public double hBoost(Player p) {
        if (!isActive(p)) return 1.0;
        double max = plugin.getConfig().getDouble("mobility.naruto-run.boost.max-horizontal", 2.5);
        return 1.0 + (max - 1.0) * boostFrac(p.getUniqueId());
    }

    /** Vertical boost for other mobility abilities — ramps with moving time. */
    public double vBoost(Player p) {
        if (!isActive(p)) return 1.0;
        double max = plugin.getConfig().getDouble("mobility.naruto-run.boost.max-vertical", 2.5);
        return 1.0 + (max - 1.0) * boostFrac(p.getUniqueId());
    }

    /** Ability-boost ramp 0..1 — reaches full over {@code boost.ramp-ms} of
     *  moving time (2 min by default), separate from the speed ramp. */
    private double boostFrac(UUID id) {
        long rampMs = Math.max(1L,
                plugin.getConfig().getLong("mobility.naruto-run.boost.ramp-ms", 120000L));
        return Math.min(1.0, rampAccumMs.getOrDefault(id, 0L) / (double) rampMs);
    }

    /* -------------------------------------------------------------- state */

    public boolean isActive(Player p) {
        return p != null && active.contains(p.getUniqueId());
    }

    /** Sneak-tap entry point. Returns true when the toggle flipped. */
    public boolean toggle(Player p) {
        if (isActive(p)) { stop(p, true); return true; }
        if (!enabled()) return false;

        // Verrou post-interruption : après un coup reçu, la course est bloquée
        // pendant INTERRUPT_LOCKOUT_MS pour éviter une relance instantanée.
        long sinceInterrupt = System.currentTimeMillis()
                - lastInterruptMs.getOrDefault(p.getUniqueId(), 0L);
        if (sinceInterrupt < INTERRUPT_LOCKOUT_MS) {
            p.sendActionBar(Component.text("Trop tôt pour repartir — reprends ton souffle.",
                    NamedTextColor.RED));
            return false;
        }

        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return false;
        // Path Two: the run IS the path — no separate per-slot unlock needed.
        if (!isFlux(p) && !toggles.isEnabled(c.id(), MobilityActionSlot.NARUTO_RUN)) return false;
        if (c.chakra().current() < minChakraToStart()) {
            p.sendActionBar(Component.text("Chakra insuffisant pour courir.",
                    NamedTextColor.AQUA));
            return false;
        }

        UUID id = p.getUniqueId();
        active.add(id);
        double base = speedMultiplier();
        currentMult.put(id, base);
        rampAccumMs.put(id, 0L);
        stoppedMs.put(id, 0L);
        applyModifierAt(p, base);             // start at base run speed; ramp advances only while moving
        ensureTicker();
        // Small launch kick when starting from a near-standstill so the run
        // "kicks" in instead of easing from a dead stop.
        double launch = plugin.getConfig().getDouble("mobility.naruto-run.launch-impulse", 0.25);
        if (launch > 0 && p.getVelocity().clone().setY(0).lengthSquared() < 0.02) {
            MobilityMath.applyImpulse(p, MobilityMath.intentDir(p, 0.85), launch, 0.0,
                    0.6, 1.0, MobilityMath.VMode.ADD);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 0.6f, 1.4f);
        p.sendActionBar(Component.text("Course Shinobi activée", NamedTextColor.GREEN));
        return true;
    }

    /** Stop the run and remove the modifier. Always safe to call. */
    public void stop(Player p, boolean feedback) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        boolean was = active.remove(id);
        currentMult.remove(id);
        rampAccumMs.remove(id);
        stoppedMs.remove(id);
        removeModifier(p);
        if (was && feedback && p.isOnline()) {
            p.sendActionBar(Component.text("Course Shinobi désactivée", NamedTextColor.GRAY));
            p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_LAND, 0.5f, 1.0f);
        }
    }

    /** Interruption par un coup reçu : stoppe la course (avec feedback) et
     *  arme le verrou de relance ({@link #INTERRUPT_LOCKOUT_MS}). */
    public void interrupt(Player p) {
        if (p == null) return;
        lastInterruptMs.put(p.getUniqueId(), System.currentTimeMillis());
        stop(p, true);
    }

    public void stopAll() {
        for (UUID id : active.toArray(new UUID[0])) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) stop(p, false);
            else active.remove(id);
        }
        if (ticker != null) { ticker.cancel(); ticker = null; }
    }

    /* ----------------------------------------------------------- attribute */

    /** Apply the speed modifier for the current ramp multiplier (≤1.0 = none).
     *  Removes by key first — fix #5. */
    private void applyModifierAt(Player p, double mult) {
        AttributeInstance inst = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;
        removeModifier(p); // never stack
        // Voie du Flux owns movement speed via its own (momentum-scaled) modifier;
        // applying ours on top would double the speed. Stay state-only here.
        if (isFlux(p)) return;
        double scalar = Math.max(0.0, mult - 1.0);
        if (scalar <= 0.0) return;
        inst.addTransientModifier(new AttributeModifier(
                Keys.key(KEY_NAME), scalar,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY));
    }

    /** Ease the run speed up to the target over {@code accel-ramp-ms}; driven
     *  from {@code MobilityListener.onMove} so acceleration is smooth (§4.2). */
    /** Deprecated: the ramp is now driven by the movement-gated ticker (so it
     *  pauses when the player stops and resets after a grace period). Kept as a
     *  no-op so the move listener still compiles. */
    public void rampTick(Player p) {
        // intentionally empty — see ensureTicker()
    }

    /** Ramp progress 0..1 (1 = at full speed) for feedback scaling. */
    private double rampFrac(UUID id) {
        double base = speedMultiplier();
        double span = Math.max(0.001, maxSpeedMultiplier() - base);
        return Math.min(1.0, (currentMult.getOrDefault(id, base) - base) / span);
    }

    /** Remove by KEY — name-based removal silently fails on Paper 1.21. */
    public void removeModifier(Player p) {
        AttributeInstance inst = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) return;
        var key = Keys.key(KEY_NAME);
        for (AttributeModifier m : inst.getModifiers().toArray(new AttributeModifier[0])) {
            if (key.equals(m.getKey())) inst.removeModifier(m);
        }
    }

    /* -------------------------------------------------------------- ticker */

    private void ensureTicker() {
        if (ticker != null) return;
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (active.isEmpty()) { ticker.cancel(); ticker = null; return; }
            if (Tps.shouldDefer()) return;
            tickCounter++;
            long windLoop = Math.max(10L, plugin.getConfig()
                    .getLong("mobility.naruto-run.wind-loop-ticks", 20L));
            boolean playWind = (tickCounter * 10L) % windLoop < 10L;

            for (UUID id : active.toArray(new UUID[0])) {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline()) { active.remove(id); continue; }
                ShinobiCharacter c = Players.active(core.characters(), p);
                if (c == null) { stop(p, false); continue; }

                // Drain — task runs every 10 ticks = half the per-second cost.
                double drain = costPerSecond() / 2.0;
                if (!c.chakra().consume(drain)) {
                    stop(p, true);
                    continue;
                }

                // Movement-gated ramp: advance only while moving; pause when
                // stopped; reset to base after the stop grace (default 5 s).
                long rampMs = Math.max(1L, plugin.getConfig()
                        .getLong("mobility.naruto-run.accel-ramp-ms", 600000L));
                long grace = Math.max(0L, plugin.getConfig()
                        .getLong("mobility.naruto-run.stop-reset-ms", 5000L));
                double base = speedMultiplier();
                // Vitesse FIXE dès l'activation (« speed 3 » = base 1.6 = +60 %, cf.
                // config speed-multiplier) — plus de montée en régime lente ni de
                // plafond scalé au niveau : la course donne la même vitesse forte
                // pour tous, tout de suite. (Ajuster via mobility.naruto-run.speed-multiplier.)
                double target = base;
                boolean moving = p.getVelocity().clone().setY(0).lengthSquared() > 0.0025;
                if (moving) {
                    stoppedMs.put(id, 0L);
                    long accum = Math.min(rampMs, rampAccumMs.getOrDefault(id, 0L) + 500L);
                    rampAccumMs.put(id, accum);
                    double mult = base + (target - base) * (accum / (double) rampMs);
                    currentMult.put(id, mult);
                    applyModifierAt(p, mult);
                } else {
                    long stopped = stoppedMs.getOrDefault(id, 0L) + 500L;
                    stoppedMs.put(id, stopped);
                    if (stopped >= grace) {            // standing too long -> reset to base
                        rampAccumMs.put(id, 0L);
                        currentMult.put(id, base);
                        applyModifierAt(p, base);
                    }
                    // else: pause — keep the current multiplier
                }

                if (playWind) {
                    try {
                        Sound s = Sound.valueOf(plugin.getConfig()
                                .getString("mobility.naruto-run.wind-sound", "ITEM_ELYTRA_FLYING"));
                        p.playSound(p.getLocation(), s,
                                (float) plugin.getConfig().getDouble("mobility.naruto-run.wind-volume", 0.35),
                                (float) plugin.getConfig().getDouble("mobility.naruto-run.wind-pitch", 1.2));
                    } catch (IllegalArgumentException ignore) { }
                }
                if (plugin.getConfig().getBoolean("mobility.naruto-run.particles", true)
                        && p.getVelocity().lengthSquared() > 0.01) {
                    int pc = 2 + (int) Math.round(4 * rampFrac(id));   // denser with speed
                    p.getWorld().spawnParticle(Particle.CLOUD,
                            p.getLocation().add(0, 0.15, 0), pc, 0.08, 0.02, 0.08, 0.005);
                }
            }
        }, 10L, 10L);
    }
}
