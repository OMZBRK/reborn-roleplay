package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.CastAttribution;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.projectiles.ProjectileSource;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stat scaling for damage ShinobiCore does not deal itself (see
 * {@link CastAttribution}): MagicSpells, MythicMobs (and the ModelEngine
 * models MythicMobs drives), internal Java effects — all end up as a
 * {@link EntityDamageByEntityEvent} whose damager is the caster, one of their
 * projectiles, or a MythicMobs summon they own.
 *
 * <h2>Melee vs technique</h2>
 * A MythicMobs {@code damage} mechanic or a MagicSpells {@code pain} cast by a
 * player looks exactly like a punch ({@code ENTITY_ATTACK}, damager = player).
 * The only reliable tell is Paper's {@code PrePlayerAttackEntityEvent}, fired at
 * the start of a <em>real</em> swing: it leaves a one-shot token for
 * (attacker, victim, tick), consumed by the first matching damage event. That
 * event is genuine melee (ShinobiCombat's M1 — {@link #isGenuineMelee}); any
 * other hit during a window belongs to the technique. Without the Paper event
 * (API drift), every direct hit is treated as melee — the pre-stats behaviour.
 */
public final class CastAttributionImpl implements CastAttribution, Listener {

    private record Window(Kind kind, String techniqueId, double multiplier,
                          ChakraAffinity nature, int tier, long until) {}

    /** {@code used}: the main hit already claimed it (sweeps of that tick still ride on it). */
    private record SwingToken(UUID victim, int tick, boolean used) {}

    private final ShinobiCore plugin;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Map<UUID, SwingToken> swings = new ConcurrentHashMap<>();
    /** Damage events already scaled at the source, or classified as genuine melee. */
    private final Map<Event, Boolean> scaled = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Event, Boolean> genuine = Collections.synchronizedMap(new WeakHashMap<>());
    private boolean swingTracking;

    // MythicMobs owner lookup (reflection, optional).
    private boolean mmTried;
    private Object mmMobManager;
    private Method mmGetActiveMob, mmGetOwner;

    public CastAttributionImpl(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** Register the listener, plus the swing tracker when Paper exposes it. */
    @SuppressWarnings("unchecked")
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        try {
            Class<? extends Event> pre = (Class<? extends Event>)
                    Class.forName("io.papermc.paper.event.player.PrePlayerAttackEntityEvent");
            Method getPlayer = pre.getMethod("getPlayer");
            Method getAttacked = pre.getMethod("getAttacked");
            EventExecutor exec = (l, ev) -> {
                if (!pre.isInstance(ev)) return;
                try {
                    Player p = (Player) getPlayer.invoke(ev);
                    Entity target = (Entity) getAttacked.invoke(ev);
                    swings.put(p.getUniqueId(), new SwingToken(target.getUniqueId(), Bukkit.getCurrentTick(), false));
                } catch (Throwable ignored) { }
            };
            Bukkit.getPluginManager().registerEvent(pre, this, EventPriority.MONITOR, exec, plugin, true);
            swingTracking = true;
        } catch (Throwable t) {
            swingTracking = false;
            plugin.getLogger().warning("[stats] PrePlayerAttackEntityEvent absent — les coups directs "
                    + "d'un lanceur sont tous traités comme du corps-à-corps.");
        }
    }

    /* -------------------------------------------------------------- api */

    @Override
    public void beginTechnique(Player caster, String techniqueId, double multiplier,
                               ChakraAffinity nature, int rankTier, long windowMillis) {
        long w = windowMillis > 0 ? windowMillis : StatFormulas.levers().techniqueWindowMillis();
        windows.put(caster.getUniqueId(), new Window(Kind.TECHNIQUE, techniqueId, Math.max(0.0, multiplier),
                nature == null ? ChakraAffinity.NONE : nature, rankTier, System.currentTimeMillis() + w));
    }

    @Override
    public void beginMelee(Player caster, double multiplier, long windowMillis) {
        long w = windowMillis > 0 ? windowMillis : StatFormulas.levers().meleeWindowMillis();
        windows.put(caster.getUniqueId(), new Window(Kind.MELEE, null, Math.max(0.0, multiplier),
                ChakraAffinity.NONE, 0, System.currentTimeMillis() + w));
    }

    @Override
    public void end(Player caster) { windows.remove(caster.getUniqueId()); }

    @Override
    public void markScaled(EntityDamageEvent event) { if (event != null) scaled.put(event, Boolean.TRUE); }

    /** True when this hit is a real swing (or when swings can't be told apart). */
    @Override
    public boolean isGenuineMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player)) return false;
        DamageCause c = e.getCause();
        if (c != DamageCause.ENTITY_ATTACK && c != DamageCause.ENTITY_SWEEP_ATTACK) return false;
        if (!swingTracking) return true;
        return Boolean.TRUE.equals(genuine.get(e));
    }

    private Window live(UUID caster) {
        Window w = windows.get(caster);
        if (w == null) return null;
        if (System.currentTimeMillis() > w.until()) { windows.remove(caster); return null; }
        return w;
    }

    /* -------------------------------------------------------- listeners */

    /** Classify first, before anyone edits or cancels the hit. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void classify(EntityDamageByEntityEvent e) {
        if (!swingTracking || !(e.getDamager() instanceof Player p)) return;
        DamageCause c = e.getCause();
        if (c == DamageCause.ENTITY_SWEEP_ATTACK) {
            // Sweeps ride on a real swing of the same tick.
            SwingToken t = swings.get(p.getUniqueId());
            if (t != null && t.tick() == Bukkit.getCurrentTick()) genuine.put(e, Boolean.TRUE);
            return;
        }
        if (c != DamageCause.ENTITY_ATTACK) return;
        SwingToken t = swings.get(p.getUniqueId());
        if (t != null && !t.used() && t.tick() == Bukkit.getCurrentTick()
                && t.victim().equals(e.getEntity().getUniqueId())) {
            swings.put(p.getUniqueId(), new SwingToken(t.victim(), t.tick(), true)); // one swing, one hit
            genuine.put(e, Boolean.TRUE);
        }
    }

    /**
     * Scale. NORMAL: after ShinobiCombat's M1 (LOW), before the KO pipeline
     * (HIGH) so the lethality check sees the final number.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void scale(EntityDamageByEntityEvent e) {
        if (scaled.containsKey(e) || isGenuineMelee(e)) return;
        Player caster = resolveCaster(e.getDamager());
        if (caster == null || caster.equals(e.getEntity())) return;
        Window w = live(caster.getUniqueId());
        if (w == null) return;

        double mult = w.multiplier();
        if (w.kind() == Kind.TECHNIQUE) {
            // Nature wheel — only when the victim is mid-technique too.
            if (e.getEntity() instanceof Player victim) {
                Window vw = live(victim.getUniqueId());
                if (vw != null && vw.kind() == Kind.TECHNIQUE) {
                    double n = StatFormulas.natureMultiplier(w.nature(), w.tier(), vw.nature(), vw.tier());
                    if (n > 0.0) mult *= n; // a 0 (cancellation) is a clash of techniques, not of a hit
                }
            }
            ShinobiCharacter c = plugin.characters().getActive(caster.getUniqueId());
            if (c != null && ThreadLocalRandom.current().nextDouble() < StatFormulas.critChance(c.stats())) {
                mult *= StatFormulas.levers().critMultiplier();
                e.getEntity().getWorld().spawnParticle(Particle.CRIT,
                        e.getEntity().getLocation().add(0, 1.2, 0), 12, 0.3, 0.4, 0.3, 0.15);
            }
        }
        e.setDamage(e.getDamage() * mult);
        scaled.put(e, Boolean.TRUE);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        windows.remove(e.getPlayer().getUniqueId());
        swings.remove(e.getPlayer().getUniqueId());
    }

    /* ---------------------------------------------------------- helpers */

    /** The player behind a hit: themselves, their projectile, their pet or MythicMobs summon. */
    private Player resolveCaster(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
            if (src instanceof Entity shooter) return resolveCaster(shooter);
            return null;
        }
        if (damager instanceof Tameable t && t.getOwner() instanceof Player p) return p;
        UUID owner = mythicOwner(damager);
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    /** {@code MythicBukkit.inst().getMobManager().getActiveMob(uuid).getOwner()} — reflection, optional. */
    private UUID mythicOwner(Entity e) {
        if (!mmTried) {
            mmTried = true;
            try {
                if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
                    Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                    Object inst = mb.getMethod("inst").invoke(null);
                    mmMobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
                    mmGetActiveMob = mmMobManager.getClass().getMethod("getActiveMob", UUID.class);
                    mmGetOwner = Class.forName("io.lumine.mythic.core.mobs.ActiveMob").getMethod("getOwner");
                }
            } catch (Throwable t) {
                mmMobManager = null;
                plugin.getLogger().info("[stats] API MythicMobs non résolue — les invocations ne "
                        + "sont pas rattachées à leur lanceur (" + t.getClass().getSimpleName() + ").");
            }
        }
        if (mmMobManager == null) return null;
        try {
            Object opt = mmGetActiveMob.invoke(mmMobManager, e.getUniqueId());
            if (!(opt instanceof Optional<?> o) || o.isEmpty()) return null;
            Object owner = mmGetOwner.invoke(o.get());
            if (owner instanceof Optional<?> ow && ow.isPresent() && ow.get() instanceof UUID u) return u;
        } catch (Throwable ignored) { }
        return null;
    }
}
