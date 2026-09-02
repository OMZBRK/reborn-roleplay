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
import org.bukkit.util.Vector;

/**
 * Wall Jump — Space while airborne next to a wall. Same pitch-blend as
 * the double jump but the horizontal component always pushes AWAY from
 * the wall face. Spends an air token; deliberately does NOT refill
 * tokens or clear the DJ lockout (config-locked — re-enabling those
 * flags re-opens the infinite wall↔DJ flight exploit).
 */
public final class WallJump {

    public static final String COOLDOWN_ID = "wall_jump";

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final AirTokenManager tokens;
    private final ToggleStore toggles;
    private final CooldownTracker cooldowns;
    private final NarutoRun narutoRun;

    public WallJump(JavaPlugin plugin, CoreServices core, AirTokenManager tokens,
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
        return plugin.getConfig().getBoolean("mobility.wall-jump.enabled", true);
    }

    /** WJ budget per airtime — independent of the DJ air token.
     *  0 or negative = unlimited. */
    private int maxPerAirtime() {
        return plugin.getConfig().getInt("mobility.wall-jump.max-per-airtime", 3);
    }

    /** Pre-check for the allowFlight arming logic. Note: NO air-token
     *  check — wall jumps run on their own per-airtime counter, so a
     *  spent double jump never blocks a wall jump (and vice-versa). */
    public boolean couldFire(Player p, ShinobiCharacter c) {
        if (!enabled() || c == null) return false;
        if (!toggles.isEnabled(c.id(), MobilityActionSlot.WALL_JUMP)) return false;
        if (!tokens.canWallJump(p, maxPerAirtime())) return false;
        if (cooldowns.isOnCooldown(p.getUniqueId(), COOLDOWN_ID)) return false;
        return MobilityMath.nearWall(p);
    }

    public boolean tryActivate(Player p) {
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (!couldFire(p, c)) return false;
        Vector away = MobilityMath.awayFromWall(p);
        if (away == null) return false;

        double cost = plugin.getConfig().getDouble("mobility.wall-jump.chakra-cost", 8.0);
        if (!c.chakra().has(cost)) {
            p.sendActionBar(Component.text("Chakra insuffisant.", NamedTextColor.AQUA));
            return false;
        }
        tokens.consumeWallJump(p);   // own budget — the DJ token is untouched
        c.chakra().consume(cost);

        var cfg = plugin.getConfig();
        double[] hv = MobilityMath.pitchBlend(p.getLocation().getPitch(),
                cfg.getDouble("mobility.wall-jump.horizontal-forward", 1.8),
                cfg.getDouble("mobility.wall-jump.vertical-forward", 0.75),
                cfg.getDouble("mobility.wall-jump.horizontal-up", 0.55),
                cfg.getDouble("mobility.wall-jump.vertical-up", 2.0),
                cfg.getDouble("mobility.wall-jump.horizontal-down", 1.8),
                cfg.getDouble("mobility.wall-jump.vertical-down", 0.45));
        hv[0] *= narutoRun.hBoost(p);
        hv[1] *= narutoRun.vBoost(p);

        // Kick mostly off the wall, with a little intent steer, preserving
        // travel so corner-to-corner wall chaining flows.
        Vector dir = away.clone().multiply(0.7)
                .add(MobilityMath.intentDir(p, 0.5).multiply(0.3));
        if (dir.lengthSquared() < 1.0e-6) dir = away;
        MobilityMath.applyImpulse(p, dir, hv[0], hv[1],
                cfg.getDouble("mobility.wall-jump.preserve-h", 0.45),
                Math.max(cfg.getDouble("mobility.wall-jump.soft-cap-h", 1.4)
                        * Math.max(1.0, narutoRun.speedFactor(p)), hv[0] * 1.2),
                MobilityMath.VMode.REPLACE);

        if (cfg.getBoolean("mobility.wall-jump.refill-tokens-on-use", false)) {
            tokens.refill(p);
        }
        // clears-double-jump-lockout intentionally read but defaulted off.
        cooldowns.set(p.getUniqueId(), COOLDOWN_ID,
                cfg.getLong("mobility.wall-jump.cooldown-ms", 350L));

        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0),
                10, 0.2, 0.3, 0.2, 0.05);
        p.playSound(p.getLocation(), Sound.BLOCK_STONE_HIT, 0.9f, 1.4f);
        return true;
    }
}
