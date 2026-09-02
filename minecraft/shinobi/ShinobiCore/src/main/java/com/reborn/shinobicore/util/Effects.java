package com.reborn.shinobicore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared particle, damage and area-of-effect helpers. Originally
 * lived as private static methods inside
 * the legacy JutsuEffectRegistry (now in ShinobiAbilities);
 * extracted so any system that needs cone projectiles, ring blasts,
 * temporary structures, or healing can reuse them — mobility
 * abilities (FloorShockwave / ShinobiDash particle trails), medic
 * jutsu (heal pulses), KO listener (death-burst particles), and any
 * future damage source.
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li><b>Forward cone</b> — origin at caster's eye, extends along
 *       look direction. Targets within {@code length} blocks of the
 *       cone axis whose normalised direction-from-caster has dot
 *       product ≥ 0.6 with the caster's look vector are hit.</li>
 *   <li><b>Ring</b> — flat circle in the XZ plane around the caster's
 *       feet (or a custom centre).</li>
 *   <li><b>Damage</b> — credited to the caster so kills count toward
 *       achievements / the KO system attributes the cause correctly.</li>
 * </ul>
 */
public final class Effects {

    private Effects() {}

    /* ----------------------------------------------------- particles */

    /** Spawn {@code count} particles inside a forward cone of
     *  {@code length} blocks ahead of {@code caster}'s eye. */
    public static void forwardCone(Player caster, double length,
                                   Particle particle, int count) {
        Vector dir = caster.getEyeLocation().getDirection();
        Location origin = caster.getEyeLocation();
        for (int i = 0; i < count; i++) {
            double t = (i / (double) count) * length;
            Location l = origin.clone().add(dir.clone().multiply(t));
            // Small jitter sideways for the cone shape.
            double jx = (Math.random() - 0.5) * 0.6;
            double jy = (Math.random() - 0.5) * 0.6;
            double jz = (Math.random() - 0.5) * 0.6;
            l.add(jx, jy, jz);
            l.getWorld().spawnParticle(particle, l, 1, 0, 0, 0, 0);
        }
    }

    /** Spawn a ring of particles at {@code center} with {@code radius}. */
    public static void spawnRing(Location center, Particle particle,
                                 double radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count;
            Location l = center.clone().add(
                    Math.cos(a) * radius, 0, Math.sin(a) * radius);
            l.getWorld().spawnParticle(particle, l, 1, 0, 0, 0, 0);
        }
    }

    /* -------------------------------------------------------- damage */

    /** Damage every living entity (other than the caster) inside a
     *  forward cone of {@code length} blocks and {@code radius} half-
     *  width. Damage is credited to the caster. */
    public static void damageCone(Player caster, double length,
                                  double radius, double damage) {
        Location origin = caster.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        for (Entity e : caster.getWorld().getNearbyEntities(
                origin, length, length, length)) {
            if (!(e instanceof LivingEntity le)
                    || e.getUniqueId().equals(caster.getUniqueId())) continue;
            Vector toEntity = e.getLocation().toVector()
                    .subtract(origin.toVector());
            double dist = toEntity.length();
            if (dist > length) continue;
            double dot = toEntity.normalize().dot(dir);
            if (dot < 0.6) continue;     // outside forward cone
            le.damage(damage, caster);
        }
    }

    /** Damage every living entity (other than the caster) within a
     *  flat ring of {@code radius} blocks of the caster's feet. */
    public static void damageRing(Player caster, double radius, double damage) {
        for (Entity e : caster.getWorld().getNearbyEntities(
                caster.getLocation(), radius, radius, radius)) {
            if (!(e instanceof LivingEntity le)
                    || e.getUniqueId().equals(caster.getUniqueId())) continue;
            le.damage(damage, caster);
        }
    }

    /* ------------------------------------------------------- targeting */

    /** Find the closest living entity within {@code range} of the caster,
     *  excluding the caster themselves. Returns null when no target. */
    public static LivingEntity nearestEntity(Player caster, double range) {
        LivingEntity nearest = null;
        double bestSq = range * range;
        for (Entity e : caster.getWorld().getNearbyEntities(
                caster.getLocation(), range, range, range)) {
            if (!(e instanceof LivingEntity le)
                    || e.getUniqueId().equals(caster.getUniqueId())) continue;
            double d = e.getLocation().distanceSquared(caster.getLocation());
            if (d < bestSq) { bestSq = d; nearest = le; }
        }
        return nearest;
    }

    /* --------------------------------------------------------- terrain */

    /** Place a temporary 3-tall stone wall in front of the caster.
     *  Reverts to AIR after {@code lifetimeSeconds}. Used by Doton
     *  jutsu and any future cover-creation effect. */
    public static void placeTemporaryWall(Player caster, int width,
                                          int lifetimeSeconds, Plugin plugin) {
        Vector dir = caster.getLocation().getDirection().setY(0).normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Location base = caster.getLocation().add(dir.multiply(2));
        List<Block> placed = new ArrayList<>();
        for (int w = -width / 2; w <= width / 2; w++) {
            for (int h = 0; h < 3; h++) {
                Location l = base.clone().add(right.clone().multiply(w))
                        .add(0, h, 0);
                Block b = l.getBlock();
                if (!b.getType().isAir()) continue;
                b.setType(Material.STONE, false);
                placed.add(b);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Block b : placed) {
                if (b.getType() == Material.STONE) {
                    b.setType(Material.AIR, false);
                }
            }
        }, 20L * lifetimeSeconds);
    }

    /* ---------------------------------------------------------- heal */

    /** Heal {@code target} up to its max HP attribute. No over-heal. */
    public static void healWithCap(LivingEntity target, double amount) {
        AttributeInstance maxAttr = target.getAttribute(
                Attribute.MAX_HEALTH);
        double max = (maxAttr != null) ? maxAttr.getValue() : 20.0;
        target.setHealth(Math.min(max, target.getHealth() + amount));
    }
}
