package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Climb — airborne wall-cling. Sneak in the air next to a wall to
 * freeze in place (this overrides Floor Shockwave's airborne-sneak when
 * a wall is adjacent); release sneak for an upward leap.
 *
 * <p>Paced by a charge economy: {@code max-charges} cling+leap cycles,
 * refilled after {@code recovery-seconds} of CUMULATIVE ground contact
 * (airborne time pauses the accumulator, it never resets it).
 */
public final class Climb {

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ToggleStore toggles;

    private final Map<UUID, Integer> charges = new HashMap<>();
    /** Cumulative grounded millis toward the refill. */
    private final Map<UUID, Long> recovery = new HashMap<>();
    private final Map<UUID, Long> lastGroundSample = new HashMap<>();
    private final Set<UUID> clinging = new HashSet<>();
    /** Ticks spent in the current cling — drives the scale→slide transition. */
    private final Map<UUID, Integer> clingTicks = new HashMap<>();

    public Climb(JavaPlugin plugin, CoreServices core, ToggleStore toggles) {
        this.plugin = plugin;
        this.core = core;
        this.toggles = toggles;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mobility.climb.enabled", true);
    }

    private int maxCharges() {
        return Math.max(1, plugin.getConfig().getInt("mobility.climb.max-charges", 2));
    }

    private long recoveryMs() {
        return (long) (plugin.getConfig()
                .getDouble("mobility.climb.recovery-seconds", 5.0) * 1000);
    }

    public int charges(Player p) {
        return charges.getOrDefault(p.getUniqueId(), maxCharges());
    }

    public int maxChargesPublic() { return maxCharges(); }

    public boolean isClinging(Player p) {
        return clinging.contains(p.getUniqueId());
    }

    /** Sneak pressed airborne next to a wall. Returns true when the
     *  cling started (caller then skips the shockwave charge). */
    public boolean tryCling(Player p) {
        if (!enabled()) return false;
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return false;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.CLIMB)) return false;
        if (!MobilityMath.nearWall(p)) return false;
        if (charges(p) <= 0) {
            p.sendActionBar(Component.text("Escalade épuisée — repose-toi au sol.",
                    NamedTextColor.YELLOW));
            return false;
        }
        charges.put(p.getUniqueId(), charges(p) - 1);
        clinging.add(p.getUniqueId());
        clingTicks.remove(p.getUniqueId());
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
        p.playSound(p.getLocation(), Sound.BLOCK_LADDER_STEP, 0.8f, 0.8f);
        return true;
    }

    /** Per move tick while clinging: pin in place, drop when the wall
     *  vanished or the ground arrived. */
    public void onMoveTick(Player p, boolean onGround) {
        UUID id = p.getUniqueId();
        if (clinging.contains(id)) {
            if (onGround || !p.isSneaking() || !MobilityMath.nearWall(p)) {
                // The un-sneak path goes through releaseCling; this is
                // the wall-gone / ground-touch fallback.
                if (onGround || !MobilityMath.nearWall(p)) { clinging.remove(id); clingTicks.remove(id); }
                return;
            }
            // Fluid grip (rework §4.7): SCALE up the wall for scale-ticks, then
            // slow-slide — not a dead freeze. Only the vertical drifts; the
            // horizontal stays pinned so you stay stuck to the wall.
            var cfg = plugin.getConfig();
            int used = clingTicks.merge(id, 1, Integer::sum);
            double vy = (used <= cfg.getInt("mobility.climb.scale-ticks", 14))
                    ? cfg.getDouble("mobility.climb.scale-speed", 0.30)
                    : -cfg.getDouble("mobility.climb.slide-speed", 0.08);
            p.setVelocity(new Vector(0, vy, 0));
            p.setFallDistance(0f);
            if ((p.getTicksLived() / 5) % 2 == 0) {
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0),
                        1, 0.1, 0.2, 0.1, 0);
            }
            return;
        }

        // Charge recovery — cumulative grounded time.
        if (onGround && charges(p) < maxCharges()) {
            long now = System.currentTimeMillis();
            Long last = lastGroundSample.get(id);
            if (last != null) {
                long acc = recovery.getOrDefault(id, 0L) + Math.min(250L, now - last);
                if (acc >= recoveryMs()) {
                    charges.put(id, maxCharges());
                    recovery.remove(id);
                    p.sendActionBar(Component.text("Escalade rechargée.",
                            NamedTextColor.GREEN));
                } else {
                    recovery.put(id, acc);
                }
            }
            lastGroundSample.put(id, now);
        } else {
            lastGroundSample.remove(id);
        }
    }

    /** Sneak released while clinging → upward leap. */
    public boolean releaseCling(Player p) {
        if (!clinging.remove(p.getUniqueId())) return false;
        clingTicks.remove(p.getUniqueId());
        var cfg = plugin.getConfig();
        double leap = cfg.getDouble("mobility.climb.leap-velocity", 0.75);
        // Real kick-off: away-from-wall + intent + up, preserving travel — you
        // launch where you look instead of straight up from a dead stop.
        Vector away = MobilityMath.awayFromWall(p);
        Vector dir = MobilityMath.intentDir(p, 0.6);
        if (away != null) dir = away.multiply(0.5).add(dir.multiply(0.5));
        MobilityMath.applyImpulse(p, dir,
                cfg.getDouble("mobility.climb.leap-push-h", 0.5), leap,
                cfg.getDouble("mobility.climb.leap-preserve-h", 0.4),
                cfg.getDouble("mobility.climb.leap-soft-cap-h", 1.2),
                MobilityMath.VMode.REPLACE);
        p.setFallDistance(0f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.15, 0.1, 0.15, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 0.7f, 1.3f);
        return true;
    }

    public void clear(Player p) {
        UUID id = p.getUniqueId();
        clinging.remove(id);
        clingTicks.remove(id);
        recovery.remove(id);
        lastGroundSample.remove(id);
        charges.remove(id);
    }
}
