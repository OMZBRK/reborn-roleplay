package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Effects;
import com.reborn.shinobicore.util.Players;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Floor Shockwave — hold sneak ≥ charge-seconds while airborne, then
 * slam toward where you look. The slam velocity is re-applied every
 * move tick (drag must not soften the descent); impact deals ring
 * damage + knockback + a short stun. Air-token gated.
 */
public final class FloorShockwave {

    public static final String COOLDOWN_ID = "floor_shockwave";

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final AirTokenManager tokens;
    private final ToggleStore toggles;
    private final CooldownTracker cooldowns;

    /** sneak-start timestamps for airborne charging players. */
    private final Map<UUID, Long> charging = new HashMap<>();
    /** locked slam vectors, re-applied each move tick until landing. */
    private final Map<UUID, Vector> slamming = new HashMap<>();

    public FloorShockwave(JavaPlugin plugin, CoreServices core,
                          AirTokenManager tokens, ToggleStore toggles,
                          CooldownTracker cooldowns) {
        this.plugin = plugin;
        this.core = core;
        this.tokens = tokens;
        this.toggles = toggles;
        this.cooldowns = cooldowns;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("mobility.floor-shockwave.enabled", true);
    }

    public boolean isCharging(Player p) { return charging.containsKey(p.getUniqueId()); }
    public boolean isSlamming(Player p) { return slamming.containsKey(p.getUniqueId()); }

    /* ------------------------------------------------------------ triggers */

    /** Sneak pressed while airborne (and not wall-clinging). */
    public void startCharge(Player p) {
        if (!enabled()) return;
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.FLOOR_SHOCKWAVE)) return;
        if (cooldowns.isOnCooldown(p.getUniqueId(), COOLDOWN_ID)) return;
        if (!tokens.hasToken(p)) return;
        charging.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void cancelCharge(Player p) {
        charging.remove(p.getUniqueId());
    }

    /** Per move tick: progress the charge, trigger the slam, keep slam
     *  velocity pinned, and resolve the impact on landing. */
    public void onMoveTick(Player p, boolean onGround) {
        UUID id = p.getUniqueId();

        Vector slam = slamming.get(id);
        if (slam != null) {
            if (onGround) {
                slamming.remove(id);
                impact(p);
            } else {
                // Counter vanilla drag/gravity so deep slams don't float:
                // keep the horizontal pin and let the descent accelerate to a
                // terminal speed instead of decaying between move ticks.
                slam.setY(Math.max(slam.getY() - 0.5, -6.0));
                p.setVelocity(slam);
            }
            return;
        }

        Long start = charging.get(id);
        if (start == null) return;
        if (onGround || !p.isSneaking()) {
            charging.remove(id);
            return;
        }
        double chargeSec = plugin.getConfig()
                .getDouble("mobility.floor-shockwave.charge-seconds", 1.2);
        long held = System.currentTimeMillis() - start;
        if (held < (long) (chargeSec * 1000)) {
            int pct = (int) Math.min(100, held * 100 / (long) (chargeSec * 1000));
            p.sendActionBar(Component.text("Onde de Choc… " + pct + "%",
                    NamedTextColor.GOLD));
            return;
        }
        charging.remove(id);
        beginSlam(p);
    }

    private void beginSlam(Player p) {
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return;
        double cost = plugin.getConfig().getDouble("mobility.floor-shockwave.chakra-cost", 25.0);
        if (!c.chakra().has(cost)) {
            p.sendActionBar(Component.text("Chakra insuffisant.", NamedTextColor.AQUA));
            return;
        }
        int tokenCost = plugin.getConfig().getInt("mobility.floor-shockwave.air-token-cost", 1);
        if (!tokens.consume(p, tokenCost)) return;
        c.chakra().consume(cost);

        var cfg = plugin.getConfig();
        double[] hd = MobilityMath.pitchBlend(p.getLocation().getPitch(),
                cfg.getDouble("mobility.floor-shockwave.horizontal-forward", 3.0),
                cfg.getDouble("mobility.floor-shockwave.down-forward", 3.5),
                cfg.getDouble("mobility.floor-shockwave.horizontal-up", 0.8),
                cfg.getDouble("mobility.floor-shockwave.down-up", 4.2),
                cfg.getDouble("mobility.floor-shockwave.horizontal-down", 0.4),
                cfg.getDouble("mobility.floor-shockwave.down-down", 4.5));
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0e-4) dir = new Vector(0, 0, 1);
        // This is a FLOOR attack — Y is always forced downward.
        Vector slam = dir.normalize().multiply(hd[0]).setY(-Math.abs(hd[1]));
        slamming.put(p.getUniqueId(), slam);
        p.setVelocity(slam);
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 1f, 0.7f);
    }

    private void impact(Player p) {
        var cfg = plugin.getConfig();
        double radius = cfg.getDouble("mobility.floor-shockwave.radius-blocks", 6.0);
        double damage = cfg.getDouble("mobility.floor-shockwave.damage-hp", 5.0);
        double knockback = cfg.getDouble("mobility.floor-shockwave.knockback-blocks", 3.0);
        double victimStun = cfg.getDouble("mobility.floor-shockwave.victim-stun-seconds", 0.5);
        double userStun = cfg.getDouble("mobility.floor-shockwave.user-stun-seconds", 0.5);

        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 2, 0.5, 0.2, 0.5, 0);
        p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 1, 0, 0, 0, 0);
        Effects.spawnRing(p.getLocation(), Particle.LARGE_SMOKE, radius * 0.6, 30);
        Effects.spawnRing(p.getLocation(), Particle.CRIT, radius, 40);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);

        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius / 2, radius)) {
            if (!(e instanceof LivingEntity le) || e == p) continue;
            le.damage(damage, p);
            Vector away = le.getLocation().toVector().subtract(p.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0.4, 0, 0.4);
            le.setVelocity(away.normalize().multiply(knockback * 0.35).setY(0.35));
            int ticks = (int) Math.max(1, victimStun * 20);
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 5, false, false));
            le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false));
        }
        int userTicks = (int) Math.max(1, userStun * 20);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, userTicks, 3, false, false));

        cooldowns.set(p.getUniqueId(), COOLDOWN_ID,
                cfg.getLong("mobility.floor-shockwave.cooldown-ms", 10000L));
    }

    public void clear(Player p) {
        charging.remove(p.getUniqueId());
        slamming.remove(p.getUniqueId());
    }
}
