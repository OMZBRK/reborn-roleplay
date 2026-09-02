package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shinobi Dash — hold sneak on the ground ≥ min-hold-seconds, release to
 * be propelled along a pitch-blended dash. Extra hold time scales the
 * magnitude up to max-multiplier. (The short sneak-TAP path belongs to
 * Naruto Run; the listener disambiguates by hold duration.)
 */
public final class ShinobiDash {

    public static final String COOLDOWN_ID = "shinobi_dash";

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ToggleStore toggles;
    private final CooldownTracker cooldowns;
    private final NarutoRun narutoRun;

    /** sneak-start timestamps for grounded holds. */
    private final Map<UUID, Long> holds = new HashMap<>();

    public ShinobiDash(JavaPlugin plugin, CoreServices core,
                       ToggleStore toggles, CooldownTracker cooldowns,
                       NarutoRun narutoRun) {
        this.plugin = plugin;
        this.core = core;
        this.toggles = toggles;
        this.cooldowns = cooldowns;
        this.narutoRun = narutoRun;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mobility.shinobi-dash.enabled", true);
    }

    public double minHoldSeconds() {
        return plugin.getConfig().getDouble("mobility.shinobi-dash.min-hold-seconds", 3.0);
    }

    public boolean isHolding(Player p) { return holds.containsKey(p.getUniqueId()); }

    /** Sneak pressed while grounded. */
    public void startHold(Player p) {
        if (!enabled()) return;
        holds.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Charge feedback, called per move tick while holding. */
    public void onMoveTick(Player p) {
        Long start = holds.get(p.getUniqueId());
        if (start == null) return;
        if (!p.isSneaking()) { holds.remove(p.getUniqueId()); return; }
        long held = System.currentTimeMillis() - start;
        long min = (long) (minHoldSeconds() * 1000);
        if (held >= 1000 && cooldownReady(p)) {
            if (held >= min) {
                p.sendActionBar(Component.text("Dash prêt — relâche !", NamedTextColor.GREEN));
            } else {
                int pct = (int) Math.min(100, held * 100 / min);
                p.sendActionBar(Component.text("Dash… " + pct + "%", NamedTextColor.YELLOW));
            }
        }
    }

    /** Sneak released. Returns the held duration in ms (0 if no hold). */
    public long endHold(Player p) {
        Long start = holds.remove(p.getUniqueId());
        return start == null ? 0L : System.currentTimeMillis() - start;
    }

    private boolean cooldownReady(Player p) {
        return !cooldowns.isOnCooldown(p.getUniqueId(), COOLDOWN_ID);
    }

    /** Attempt the dash after a release of {@code heldMs}, grounded. */
    public boolean tryDash(Player p, long heldMs) {
        if (!enabled()) return false;
        long min = (long) (minHoldSeconds() * 1000);
        if (heldMs < min) return false;

        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return false;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.DASH)) return false;
        if (!cooldownReady(p)) {
            p.sendActionBar(Component.text("Dash en recharge…", NamedTextColor.YELLOW));
            return false;
        }
        double cost = plugin.getConfig().getDouble("mobility.shinobi-dash.chakra-cost", 20.0);
        if (!c.chakra().has(cost)) {
            p.sendActionBar(Component.text("Chakra insuffisant.", NamedTextColor.AQUA));
            return false;
        }
        c.chakra().consume(cost);

        var cfg = plugin.getConfig();
        double[] hv = MobilityMath.pitchBlend(p.getLocation().getPitch(),
                cfg.getDouble("mobility.shinobi-dash.horizontal-forward", 3.0),
                cfg.getDouble("mobility.shinobi-dash.vertical-forward", 0.4),
                cfg.getDouble("mobility.shinobi-dash.horizontal-up", 0.8),
                cfg.getDouble("mobility.shinobi-dash.vertical-up", 3.0),
                cfg.getDouble("mobility.shinobi-dash.horizontal-down", 3.0),
                cfg.getDouble("mobility.shinobi-dash.vertical-down", 0.2));

        double bonus = cfg.getDouble("mobility.shinobi-dash.bonus-per-extra-second", 0.15);
        double cap = cfg.getDouble("mobility.shinobi-dash.max-multiplier", 1.5);
        double mult = Math.min(cap, 1.0 + ((heldMs - min) / 1000.0) * bonus);
        hv[0] *= mult;
        hv[1] *= mult;
        hv[0] *= narutoRun.hBoost(p);   // scales with the Naruto-Run speed ramp
        hv[1] *= narutoRun.vBoost(p);

        // Dash ADDS to momentum (keeps some rise/fall) for a burst that carries.
        MobilityMath.applyImpulse(p, MobilityMath.intentDir(p, 0.85), hv[0], hv[1],
                cfg.getDouble("mobility.shinobi-dash.preserve-h", 0.5),
                Math.max(cfg.getDouble("mobility.shinobi-dash.soft-cap-h", 1.8)
                        * Math.max(1.0, narutoRun.speedFactor(p)), hv[0] * 1.2),
                MobilityMath.VMode.ADD);
        cooldowns.set(p.getUniqueId(), COOLDOWN_ID,
                cfg.getLong("mobility.shinobi-dash.cooldown-ms", 4000L));

        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 24, 0.3, 0.15, 0.3, 0.08);
        p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 1, 0, 0, 0, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 0.9f);
        return true;
    }

    /**
     * Fire the dash immediately (no hold) — the double-tap-sneak path (§4.5).
     * Same chakra / cooldown / toggle gating as {@link #tryDash}, flat
     * magnitude (no charge bonus). Returns true on a successful dash.
     */
    public boolean dashNow(Player p) {
        if (!enabled() || !p.isOnGround()) return false;
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return false;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.DASH)) return false;
        if (!cooldownReady(p)) {
            p.sendActionBar(Component.text("Dash en recharge…", NamedTextColor.YELLOW));
            return false;
        }
        double cost = plugin.getConfig().getDouble("mobility.shinobi-dash.chakra-cost", 15.0);
        if (!c.chakra().has(cost)) {
            p.sendActionBar(Component.text("Chakra insuffisant.", NamedTextColor.AQUA));
            return false;
        }
        c.chakra().consume(cost);

        var cfg = plugin.getConfig();
        double[] hv = MobilityMath.pitchBlend(p.getLocation().getPitch(),
                cfg.getDouble("mobility.shinobi-dash.horizontal-forward", 3.0),
                cfg.getDouble("mobility.shinobi-dash.vertical-forward", 0.4),
                cfg.getDouble("mobility.shinobi-dash.horizontal-up", 0.8),
                cfg.getDouble("mobility.shinobi-dash.vertical-up", 3.0),
                cfg.getDouble("mobility.shinobi-dash.horizontal-down", 3.0),
                cfg.getDouble("mobility.shinobi-dash.vertical-down", 0.2));
        hv[0] *= narutoRun.hBoost(p);   // scales with the Naruto-Run speed ramp
        hv[1] *= narutoRun.vBoost(p);
        MobilityMath.applyImpulse(p, MobilityMath.intentDir(p, 0.85), hv[0], hv[1],
                cfg.getDouble("mobility.shinobi-dash.preserve-h", 0.5),
                Math.max(cfg.getDouble("mobility.shinobi-dash.soft-cap-h", 1.8)
                        * Math.max(1.0, narutoRun.speedFactor(p)), hv[0] * 1.2),
                MobilityMath.VMode.ADD);
        cooldowns.set(p.getUniqueId(), COOLDOWN_ID,
                cfg.getLong("mobility.shinobi-dash.cooldown-ms", 4000L));

        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 24, 0.3, 0.15, 0.3, 0.08);
        p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 1, 0, 0, 0, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 0.9f);
        return true;
    }

    public void clear(Player p) {
        holds.remove(p.getUniqueId());
    }
}
