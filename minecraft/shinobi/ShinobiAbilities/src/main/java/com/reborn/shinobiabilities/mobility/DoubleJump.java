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

/**
 * Double Jump — Space mid-air, spends one air token. Pitch-curved
 * velocity: look up for height, level/down for distance. Lockout until
 * a continuous ground dwell (see {@link AirTokenManager}).
 */
public final class DoubleJump {

    public static final String COOLDOWN_ID = "double_jump";

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final AirTokenManager tokens;
    private final ToggleStore toggles;
    private final CooldownTracker cooldowns;
    private final NarutoRun narutoRun;
    /** Players mid double-jump curve — air control stands down for them. */
    private final java.util.Set<java.util.UUID> curving =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DoubleJump(JavaPlugin plugin, CoreServices core, AirTokenManager tokens,
                      ToggleStore toggles, CooldownTracker cooldowns,
                      NarutoRun narutoRun) {
        this.plugin = plugin;
        this.core = core;
        this.tokens = tokens;
        this.toggles = toggles;
        this.cooldowns = cooldowns;
        this.narutoRun = narutoRun;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mobility.double-jump.enabled", true);
    }

    /** True while the player is in the accelerating phase of a double jump. */
    public boolean isCurving(Player p) {
        return p != null && curving.contains(p.getUniqueId());
    }

    /** Cheap pre-check used by the allowFlight arming logic. */
    public boolean couldFire(Player p, ShinobiCharacter c) {
        if (!enabled() || c == null) return false;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.DOUBLE_JUMP)) return false;
        if (!tokens.hasToken(p)) return false;
        if (plugin.getConfig().getBoolean("mobility.double-jump.require-ground-touch-between", true)
                && tokens.djLockedOut(p)) return false;
        return !cooldowns.isOnCooldown(p.getUniqueId(), COOLDOWN_ID);
    }

    /** Fire from the flight-toggle hook. Returns true on success. */
    public boolean tryActivate(Player p) {
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (!couldFire(p, c)) return false;

        double cost = plugin.getConfig().getDouble("mobility.double-jump.chakra-cost", 10.0);
        if (!c.chakra().has(cost)) {
            p.sendActionBar(Component.text("Chakra insuffisant.", NamedTextColor.AQUA));
            return false;
        }
        int tokenCost = plugin.getConfig().getInt("mobility.double-jump.air-token-cost", 1);
        if (!tokens.consume(p, tokenCost)) return false;
        c.chakra().consume(cost);

        var cfg = plugin.getConfig();
        double[] hv = MobilityMath.pitchBlend(p.getLocation().getPitch(),
                cfg.getDouble("mobility.double-jump.horizontal-forward", 1.8),
                cfg.getDouble("mobility.double-jump.vertical-forward", 0.75),
                cfg.getDouble("mobility.double-jump.horizontal-up", 0.55),
                cfg.getDouble("mobility.double-jump.vertical-up", 2.0),
                cfg.getDouble("mobility.double-jump.horizontal-down", 1.8),
                cfg.getDouble("mobility.double-jump.vertical-down", 0.45));
        hv[0] *= narutoRun.hBoost(p);
        hv[1] *= narutoRun.vBoost(p);

        // Fluid curved leap: pop up, then accelerate SMOOTHLY and never below the
        // player's current speed (so a moving leap is never braked). The push is
        // additive (constant linear accel) and follows the player's travel with a
        // light steer toward intent, so it doesn't feel robotic.
        double lookW = cfg.getDouble("mobility.feel.dir-look-weight", 0.7);
        final org.bukkit.util.Vector dir = MobilityMath.intentDir(p, lookW);
        final double hMag = hv[0];
        final double vMag = hv[1];
        final int curveTicks = Math.max(1, cfg.getInt("mobility.double-jump.curve-ticks", 6));
        final double startFrac = cfg.getDouble("mobility.double-jump.curve-start-frac", 0.7);

        org.bukkit.util.Vector v0 = p.getVelocity();
        org.bukkit.util.Vector h0 = new org.bukkit.util.Vector(v0.getX(), 0, v0.getZ());
        double cur0 = h0.length();
        // Direction: blend current travel toward the leap dir (smooth redirect).
        org.bukkit.util.Vector startDir = cur0 > 0.05
                ? h0.clone().normalize().multiply(0.4).add(dir.clone().multiply(0.6))
                : dir.clone();
        if (startDir.lengthSquared() < 1.0e-6) startDir = dir.clone();
        startDir.normalize();
        // Start at the FASTER of current momentum or the gentle leap start.
        final double startSpeed = Math.max(cur0, hMag * startFrac);
        p.setVelocity(new org.bukkit.util.Vector(
                startDir.getX() * startSpeed, vMag, startDir.getZ() * startSpeed));

        final double accelPerTick = Math.max(0.0, hMag - startSpeed) / curveTicks;
        final java.util.UUID id = p.getUniqueId();
        curving.add(id);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (t > curveTicks || !p.isOnline() || p.isOnGround()) {
                    curving.remove(id);
                    cancel();
                    return;
                }
                org.bukkit.util.Vector cv = p.getVelocity();
                org.bukkit.util.Vector ch = new org.bukkit.util.Vector(cv.getX(), 0, cv.getZ());
                double cs = ch.length();
                double ns = Math.min(Math.max(hMag, cs), cs + accelPerTick); // smooth, never slows
                org.bukkit.util.Vector nd = cs > 0.05 ? ch.clone().normalize() : dir.clone();
                nd = nd.multiply(0.85).add(dir.clone().multiply(0.15));       // light steer
                if (nd.lengthSquared() < 1.0e-6) nd = dir.clone();
                nd.normalize().multiply(ns);
                p.setVelocity(new org.bukkit.util.Vector(nd.getX(), cv.getY(), nd.getZ()));
            }
        }.runTaskTimer(plugin, 1L, 1L);

        if (cfg.getBoolean("mobility.double-jump.require-ground-touch-between", true)) {
            tokens.armDjLockout(p);
        }
        cooldowns.set(p.getUniqueId(), COOLDOWN_ID,
                cfg.getLong("mobility.double-jump.cooldown-ms", 500L));

        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 14, 0.25, 0.1, 0.25, 0.04);
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);
        return true;
    }
}
