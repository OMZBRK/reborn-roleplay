package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.util.Effects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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
import java.util.Locale;
import java.util.Map;

/**
 * Lambda-style effect registry. Keys come from
 * {@code abilities.yml → jutsu.effect}; unknown keys fall back to the
 * per-leaf generic ({@code generic_<categoryLeaf>}), then to a neutral
 * chakra puff so a typo never hard-fails a cast.
 *
 * <p>Damage scales with chakra cost via {@link #scaledDamage} —
 * 0.30-0.45 × cost, clamped to a sane HP range, credited to the caster
 * so the KO system attributes the hit.
 */
public final class JutsuEffectRegistry {

    /** One castable effect. */
    @FunctionalInterface
    public interface JutsuEffect {
        void play(JavaPlugin plugin, Player caster, Ability def);
    }

    private final Map<String, JutsuEffect> effects = new HashMap<>();

    public JutsuEffectRegistry() {
        registerHandCrafted();
        registerGenerics();
    }

    public void register(String key, JutsuEffect effect) {
        if (key == null || effect == null) return;
        effects.put(key.toLowerCase(Locale.ROOT), effect);
    }

    public boolean has(String key) {
        return key != null && effects.containsKey(key.toLowerCase(Locale.ROOT));
    }

    /** Resolve and play the effect for {@code def}. */
    public void dispatch(JavaPlugin plugin, Player caster, Ability def) {
        JutsuEffect e = null;
        if (def.jutsu() != null && def.jutsu().effectKey() != null) {
            e = effects.get(def.jutsu().effectKey().toLowerCase(Locale.ROOT));
        }
        if (e == null) e = effects.get("generic_" + def.categoryLeaf());
        if (e == null) e = effects.get("generic_fallback");
        e.play(plugin, caster, def);
    }

    /** Chakra-cost-scaled damage: cost × factor, clamped to [1, 16] HP. */
    public static double scaledDamage(Ability def, double factor) {
        double cost = def.jutsu() == null
                ? 50.0 : def.jutsu().chakraCost();
        return Math.max(1.0, Math.min(16.0, cost * factor));
    }

    /* ================================================== hand-crafted (11) */

    private void registerHandCrafted() {
        // 1. Katon — Boule de Feu : flame cone + burn.
        register("katon_gokakyu", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 9, Particle.FLAME, 140);
            Effects.forwardCone(caster, 9, Particle.LAVA, 16);
            Effects.damageCone(caster, 9, 3, scaledDamage(def, 0.40));
            igniteCone(caster, 9, 60);
            sound(caster, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.9f);
        });

        // 2. Suiton — Balle d'Eau : pressurised water bullet.
        register("suiton_teppodama", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 11, Particle.SPLASH, 160);
            Effects.forwardCone(caster, 11, Particle.BUBBLE_POP, 50);
            Effects.damageCone(caster, 11, 2.2, scaledDamage(def, 0.38));
            sound(caster, Sound.ENTITY_DOLPHIN_SPLASH, 1f, 0.8f);
        });

        // 3. Futon — Kamaitachi : cutting wind, light knockback.
        register("futon_kamaitachi", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 10, Particle.CLOUD, 110);
            Effects.forwardCone(caster, 10, Particle.SWEEP_ATTACK, 12);
            Effects.damageCone(caster, 10, 3, scaledDamage(def, 0.35));
            pushCone(caster, 10, 0.7, 0.15);
            sound(caster, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.4f);
        });

        // 4. Doton — Doryūheki : temporary earth wall.
        register("doton_doryuheki", (plugin, caster, def) -> {
            Effects.placeTemporaryWall(caster, 5, 12, plugin);
            Effects.spawnRing(caster.getLocation(), Particle.LARGE_SMOKE, 2.5, 26);
            sound(caster, Sound.BLOCK_ROOTED_DIRT_PLACE, 1f, 0.7f);
        });

        // 5. Raiton — Shiden : lightning lash + brief glow.
        register("raiton_shiden", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 8, Particle.ELECTRIC_SPARK, 150);
            Effects.damageCone(caster, 8, 2.5, scaledDamage(def, 0.42));
            affectCone(caster, 8, le -> le.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING, 40, 0, false, false)));
            sound(caster, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.6f);
        });

        // 6. Iryō — Shōsen : healing palm on self or aimed ally.
        register("iryo_shosen", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 4);
            LivingEntity healed = target != null ? target : caster;
            Effects.healWithCap(healed, Math.max(4.0, scaledDamage(def, 0.35)));
            healed.getWorld().spawnParticle(Particle.HEART,
                    healed.getLocation().add(0, 1.4, 0), 8, 0.4, 0.4, 0.4, 0);
            healed.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    healed.getLocation().add(0, 1.0, 0), 14, 0.5, 0.6, 0.5, 0);
            sound(caster, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        });

        // 7. Genjutsu — Kasumi : blind + disorient the nearest target.
        register("genjutsu_kasumi", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 9);
            if (target != null) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, 80, 0, false, false));
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, 120, 0, false, false));
                target.getWorld().spawnParticle(Particle.WITCH,
                        target.getEyeLocation(), 25, 0.4, 0.4, 0.4, 0);
            }
            Effects.spawnRing(caster.getLocation(), Particle.PORTAL, 1.8, 24);
            sound(caster, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.5f, 1.8f);
        });

        // 8. Taijutsu — Konoha Senpū : spinning kick, ring damage + knockback.
        register("taijutsu_konoha_senpu", (plugin, caster, def) -> {
            Effects.spawnRing(caster.getLocation().add(0, 1, 0),
                    Particle.SWEEP_ATTACK, 2.4, 14);
            Effects.damageRing(caster, 3.2, scaledDamage(def, 0.34));
            pushRing(caster, 3.2, 0.85, 0.25);
            sound(caster, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        });

        // 9. Kenjutsu — Iai : forward dash-cut.
        register("kenjutsu_iai", (plugin, caster, def) -> {
            Vector dir = caster.getLocation().getDirection().setY(0).normalize();
            caster.setVelocity(dir.clone().multiply(1.35).setY(0.12));
            Effects.forwardCone(caster, 5, Particle.CRIT, 60);
            Effects.forwardCone(caster, 5, Particle.SWEEP_ATTACK, 8);
            Effects.damageCone(caster, 5, 1.8, scaledDamage(def, 0.45));
            sound(caster, Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 1.3f);
        });

        // 10. Shunshin : flicker-step toward the look direction.
        register("shunshin", (plugin, caster, def) -> {
            Location from = caster.getLocation();
            Vector dir = from.getDirection().setY(0).normalize();
            caster.setVelocity(dir.multiply(2.2).setY(0.25));
            from.getWorld().spawnParticle(Particle.CLOUD, from, 30, 0.3, 0.6, 0.3, 0.02);
            sound(caster, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
        });

        // 11. Kawarimi : substitution — hop back + decoy puff.
        register("kawarimi", (plugin, caster, def) -> {
            Location at = caster.getLocation();
            Vector back = at.getDirection().setY(0).normalize().multiply(-1.6).setY(0.45);
            caster.setVelocity(back);
            at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 24, 0.3, 0.8, 0.3, 0.05);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 16, 0.3, 0.6, 0.3, 0.01);
            sound(caster, Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
            caster.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, 30, 1, false, false));
        });
    }

    /* ============================================ per-leaf generics (34) */

    private void registerGenerics() {
        // Elemental damage cones.
        cone("katon", Particle.FLAME, Sound.ENTITY_BLAZE_SHOOT, 0.36);
        cone("suiton", Particle.SPLASH, Sound.ENTITY_DOLPHIN_SPLASH, 0.34);
        cone("futon", Particle.CLOUD, Sound.ENTITY_BREEZE_SHOOT, 0.33);
        cone("raiton", Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.38);
        cone("shakuton", Particle.SOUL_FIRE_FLAME, Sound.ITEM_FIRECHARGE_USE, 0.40);
        cone("yoton", Particle.LAVA, Sound.BLOCK_LAVA_POP, 0.40);
        cone("ranton", Particle.GLOW, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.38);
        cone("shoton", Particle.ENCHANTED_HIT, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.36);
        cone("kenjutsu", Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.42);
        cone("kunaijutsu", Particle.CRIT, Sound.ENTITY_ARROW_SHOOT, 0.36);
        cone("shurikenjutsu", Particle.CRIT, Sound.ENTITY_ARROW_SHOOT, 0.34);
        cone("bojutsu", Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.36);
        cone("tessenjutsu", Particle.CLOUD, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.34);
        cone("fuma", Particle.SWEEP_ATTACK, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.40);

        // Doton — wall + small smoke ring (cover-creation identity).
        register("generic_doton", (plugin, caster, def) -> {
            Effects.placeTemporaryWall(caster, 3, 8, plugin);
            Effects.spawnRing(caster.getLocation(), Particle.LARGE_SMOKE, 2.0, 20);
            sound(caster, Sound.BLOCK_ROOTED_DIRT_PLACE, 1f, 0.75f);
        });

        // Hyōton — frost cone + slow.
        register("generic_hyoton", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 8, Particle.SNOWFLAKE, 110);
            Effects.damageCone(caster, 8, 2.6, scaledDamage(def, 0.34));
            affectCone(caster, 8, le -> le.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 70, 1, false, false)));
            sound(caster, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f);
        });

        // Futton — corrosive vapour, poison.
        register("generic_futton", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 7, Particle.SPORE_BLOSSOM_AIR, 120);
            Effects.damageCone(caster, 7, 2.6, scaledDamage(def, 0.30));
            affectCone(caster, 7, le -> le.addPotionEffect(new PotionEffect(
                    PotionEffectType.POISON, 80, 0, false, false)));
            sound(caster, Sound.BLOCK_BREWING_STAND_BREW, 1f, 0.7f);
        });

        // Bakuton — explosion ring.
        register("generic_bakuton", (plugin, caster, def) -> {
            Location c = caster.getLocation();
            c.getWorld().spawnParticle(Particle.EXPLOSION, c, 3, 1.2, 0.4, 1.2, 0);
            Effects.damageRing(caster, 4.0, scaledDamage(def, 0.40));
            pushRing(caster, 4.0, 0.9, 0.3);
            sound(caster, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        });

        // Jiton — magnetic drag: pull targets toward the caster.
        register("generic_jiton", (plugin, caster, def) -> {
            Effects.spawnRing(caster.getLocation(), Particle.CRIT, 5.0, 36);
            for (Entity e : caster.getWorld().getNearbyEntities(
                    caster.getLocation(), 6, 4, 6)) {
                if (!(e instanceof LivingEntity le) || e == caster) continue;
                Vector pull = caster.getLocation().toVector()
                        .subtract(le.getLocation().toVector()).normalize()
                        .multiply(0.8).setY(0.1);
                le.setVelocity(le.getVelocity().add(pull));
            }
            Effects.damageRing(caster, 3.0, scaledDamage(def, 0.28));
            sound(caster, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.5f);
        });

        // Mokuton — rooting growth.
        register("generic_mokuton", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 8, Particle.HAPPY_VILLAGER, 90);
            Effects.damageCone(caster, 8, 2.8, scaledDamage(def, 0.34));
            affectCone(caster, 8, le -> le.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 90, 2, false, false)));
            sound(caster, Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 0.7f);
        });

        // Iryō — generic heal pulse.
        register("generic_iryo", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 4);
            LivingEntity healed = target != null ? target : caster;
            Effects.healWithCap(healed, Math.max(3.0, scaledDamage(def, 0.30)));
            healed.getWorld().spawnParticle(Particle.HEART,
                    healed.getLocation().add(0, 1.4, 0), 6, 0.4, 0.4, 0.4, 0);
            sound(caster, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        });

        // Fūinjutsu — sealing root.
        register("generic_fuinjutsu", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 8);
            if (target != null) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 100, 4, false, false));
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, 100, 0, false, false));
                Effects.spawnRing(target.getLocation(), Particle.REVERSE_PORTAL, 1.4, 24);
            }
            sound(caster, Sound.BLOCK_CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1f, 0.8f);
        });

        // Kuchiyose — summoning burst: flair + launch the nearest foe.
        register("generic_kuchiyose", (plugin, caster, def) -> {
            Effects.spawnRing(caster.getLocation(), Particle.TOTEM_OF_UNDYING, 2.2, 30);
            caster.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                    caster.getLocation(), 30, 1.0, 0.4, 1.0, 0.01);
            LivingEntity target = Effects.nearestEntity(caster, 5);
            if (target != null && target != caster) {
                target.damage(scaledDamage(def, 0.32), caster);
                target.setVelocity(target.getVelocity().add(new Vector(0, 0.7, 0)));
            }
            sound(caster, Sound.ENTITY_FROG_LONG_JUMP, 1f, 0.6f);
        });

        // Genjutsu — generic illusion: blind nearest.
        register("generic_genjutsu", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 9);
            if (target != null) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, 70, 0, false, false));
                target.getWorld().spawnParticle(Particle.WITCH,
                        target.getEyeLocation(), 20, 0.4, 0.4, 0.4, 0);
            }
            sound(caster, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 0.45f, 1.9f);
        });

        // Kinjutsu — forbidden surge: strong dark cone, recoil damage.
        register("generic_kinjutsu", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 9, Particle.SOUL, 130);
            Effects.damageCone(caster, 9, 3.0, scaledDamage(def, 0.45));
            caster.damage(1.0); // forbidden arts bite their user
            sound(caster, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.7f);
        });

        // Juinjutsu — cursed seal: weakness + wither tick.
        register("generic_juinjutsu", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 8);
            if (target != null) {
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, 120, 1, false, false));
                target.damage(scaledDamage(def, 0.26), caster);
                Effects.spawnRing(target.getLocation(), Particle.WITCH, 1.2, 18);
            }
            sound(caster, Sound.ENTITY_VEX_CHARGE, 0.8f, 0.7f);
        });

        // Kugutsu — puppet strings: yank target toward the caster.
        register("generic_kugutsu", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 10);
            if (target != null) {
                Vector pull = caster.getLocation().toVector()
                        .subtract(target.getLocation().toVector())
                        .normalize().multiply(1.0).setY(0.25);
                target.setVelocity(pull);
                target.damage(scaledDamage(def, 0.28), caster);
                target.getWorld().spawnParticle(Particle.END_ROD,
                        target.getLocation().add(0, 1.2, 0), 12, 0.3, 0.5, 0.3, 0);
            }
            sound(caster, Sound.ITEM_LEAD_TIED, 1f, 1.2f);
        });

        // Senjutsu — nature-energy strikes.
        register("generic_crapaud", (plugin, caster, def) -> {
            Effects.forwardCone(caster, 8, Particle.FALLING_WATER, 100);
            Effects.damageCone(caster, 8, 3.0, scaledDamage(def, 0.38));
            pushCone(caster, 8, 0.8, 0.2);
            sound(caster, Sound.ENTITY_FROG_TONGUE, 1f, 0.8f);
        });
        register("generic_serpent", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 7);
            if (target != null) {
                target.damage(scaledDamage(def, 0.34), caster);
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.POISON, 90, 0, false, false));
                target.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR,
                        target.getEyeLocation(), 16, 0.3, 0.3, 0.3, 0);
            }
            sound(caster, Sound.ENTITY_SPIDER_AMBIENT, 0.9f, 0.7f);
        });

        // Dōjutsu — perception arts.
        register("generic_sharingan", (plugin, caster, def) ->
                dojutsuReveal(caster, 16, 200, Sound.ENTITY_ENDERMAN_AMBIENT));
        register("generic_byakugan", (plugin, caster, def) ->
                dojutsuReveal(caster, 28, 200, Sound.ENTITY_ENDER_EYE_DEATH));
        register("generic_rinnegan", (plugin, caster, def) -> {
            // Shinra-Tensei-flavoured repulsion.
            Effects.spawnRing(caster.getLocation(), Particle.REVERSE_PORTAL, 4.5, 40);
            Effects.damageRing(caster, 4.5, scaledDamage(def, 0.36));
            pushRing(caster, 4.5, 1.4, 0.45);
            sound(caster, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.4f);
        });

        // Taijutsu styles.
        register("generic_goken", (plugin, caster, def) -> {
            Effects.spawnRing(caster.getLocation().add(0, 1, 0),
                    Particle.SWEEP_ATTACK, 2.0, 10);
            Effects.damageRing(caster, 2.8, scaledDamage(def, 0.40));
            pushRing(caster, 2.8, 0.8, 0.3);
            sound(caster, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.9f);
        });
        register("generic_juken", (plugin, caster, def) -> {
            LivingEntity target = Effects.nearestEntity(caster, 3.5);
            if (target != null) {
                target.damage(scaledDamage(def, 0.36), caster);
                target.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, 80, 0, false, false));
                if (target instanceof Player tp) {
                    // Gentle Fist drains chakra-bearing foes' will to act.
                    tp.addPotionEffect(new PotionEffect(
                            PotionEffectType.MINING_FATIGUE, 80, 1, false, false));
                }
                target.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                        target.getLocation().add(0, 1.2, 0), 18, 0.3, 0.4, 0.3, 0);
            }
            sound(caster, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.4f);
        });

        // Last-resort fallback — neutral chakra puff.
        register("generic_fallback", (plugin, caster, def) -> {
            caster.getWorld().spawnParticle(Particle.CLOUD,
                    caster.getLocation().add(0, 1, 0), 16, 0.4, 0.5, 0.4, 0.02);
            caster.sendActionBar(Component.text(
                    "Effet introuvable — chakra dissipé.", NamedTextColor.GRAY));
            sound(caster, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.5f);
        });
    }

    /* ----------------------------------------------------------- helpers */

    /** Register a plain particle damage-cone generic for a leaf. */
    private void cone(String leaf, Particle particle, Sound sfx, double factor) {
        register("generic_" + leaf, (plugin, caster, def) -> {
            Effects.forwardCone(caster, 8, particle, 110);
            Effects.damageCone(caster, 8, 2.6, scaledDamage(def, factor));
            sound(caster, sfx, 0.9f, 1.0f);
        });
    }

    /** Glow every living entity within range — perception flair. */
    private static void dojutsuReveal(Player caster, double range,
                                      int durationTicks, Sound sfx) {
        for (Entity e : caster.getWorld().getNearbyEntities(
                caster.getLocation(), range, range / 2, range)) {
            if (e instanceof LivingEntity le && e != caster) {
                le.addPotionEffect(new PotionEffect(
                        PotionEffectType.GLOWING, durationTicks, 0, false, false));
            }
        }
        Effects.spawnRing(caster.getLocation(), Particle.END_ROD, 1.6, 20);
        caster.playSound(caster.getLocation(), sfx, 0.8f, 1.6f);
    }

    /** Apply {@code action} to every living entity in the forward cone. */
    private static void affectCone(Player caster, double length,
                                   java.util.function.Consumer<LivingEntity> action) {
        Location origin = caster.getEyeLocation();
        Vector dir = origin.getDirection().normalize();
        for (Entity e : caster.getWorld().getNearbyEntities(
                origin, length, length, length)) {
            if (!(e instanceof LivingEntity le) || e == caster) continue;
            Vector to = e.getLocation().toVector().subtract(origin.toVector());
            if (to.length() > length) continue;
            if (to.normalize().dot(dir) < 0.6) continue;
            action.accept(le);
        }
    }

    /** Knock entities in the cone along the caster's look direction. */
    private static void pushCone(Player caster, double length,
                                 double strength, double lift) {
        Vector dir = caster.getEyeLocation().getDirection().setY(0).normalize();
        affectCone(caster, length, le -> le.setVelocity(le.getVelocity()
                .add(dir.clone().multiply(strength).setY(lift))));
    }

    /** Knock entities in the ring away from the caster. */
    private static void pushRing(Player caster, double radius,
                                 double strength, double lift) {
        for (Entity e : caster.getWorld().getNearbyEntities(
                caster.getLocation(), radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e == caster) continue;
            Vector away = le.getLocation().toVector()
                    .subtract(caster.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() < 0.01) away = new Vector(0.5, 0, 0.5);
            le.setVelocity(le.getVelocity().add(
                    away.normalize().multiply(strength).setY(lift)));
        }
    }

    /** Ignite blocks-free: set brief fire ticks on cone victims. */
    private static void igniteCone(Player caster, double length, int fireTicks) {
        affectCone(caster, length, le -> le.setFireTicks(
                Math.max(le.getFireTicks(), fireTicks)));
    }

    private static void sound(Player caster, Sound s, float vol, float pitch) {
        caster.getWorld().playSound(caster.getLocation(), s, vol, pitch);
    }
}
