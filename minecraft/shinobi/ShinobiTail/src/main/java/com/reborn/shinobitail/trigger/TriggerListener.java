package com.reborn.shinobitail.trigger;

import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.event.KoEnterEvent;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import com.reborn.shinobitail.inner.FakeBody;
import com.reborn.shinobitail.inner.InnerWorldSession;
import com.reborn.shinobitail.transform.ActiveTransformation;
import com.reborn.shinobitail.transform.TransformationManager;
import com.reborn.shinobitail.util.Chances;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All event wiring:
 * <ul>
 *   <li>HP-threshold transformation rolls on damage (MONITOR — after
 *       ShinobiCore's KO cap has had its say)</li>
 *   <li>combat → rage feeding</li>
 *   <li>KO-save: the beast refuses to let its host drop</li>
 *   <li>session hygiene: character switch, quit, join sweep, death</li>
 *   <li>Inner World freeze (movement + command lock)</li>
 *   <li>fake-body protection + orphan cleanup on chunk load</li>
 * </ul>
 */
public final class TriggerListener implements Listener {

    private final ShinobiTail plugin;
    /** last automatic roll per player (cooldown gate). */
    private final Map<UUID, Long> lastRoll = new ConcurrentHashMap<>();

    public TriggerListener(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    /* ----------------------------------------------------- damage → rolls */

    /** Stage resistance — runs EARLY (LOW) so ShinobiCore's KO cap at
     *  HIGH already sees the reduced damage. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageResist(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        ActiveTransformation t = plugin.transformations().get(p.getUniqueId());
        if (t == null) return;
        double resist = t.beast().stage(t.stage()).resistancePercent();
        if (resist <= 0) return;
        ev.setDamage(ev.getDamage() * Math.max(0.0, 1.0 - resist / 100.0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;

        if (plugin.transformations().isTransformed(p.getUniqueId())) {
            plugin.transformations().onDamageTaken(p, ev.getFinalDamage());
            return;
        }
        if (plugin.innerWorld().inSession(p.getUniqueId())) return;
        if (plugin.ko() == null || plugin.ko().isKo(p.getUniqueId())) return;

        JinchurikiData data = plugin.jinchuriki().ofActive(p);
        if (data == null || data.finalReleaseReached()
                || data.sealRemoved()) return;
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) return;

        // Micro-surge: every hit carries a tiny configurable chance of
        // waking the beast outright, whatever the HP level.
        double surge = plugin.getConfig()
                .getDouble("triggers.any-damage-chance", 0.01);
        if (surge > 0 && Chances.roll(surge)) {
            lastRoll.put(p.getUniqueId(), System.currentTimeMillis());
            plugin.transformations().begin(p, data, beast, 1,
                    ActiveTransformation.Cause.TAKEOVER);
            return;
        }

        double after = Math.max(0.0, p.getHealth() - ev.getFinalDamage());
        double pct = after / Math.max(1.0, p.getMaxHealth()) * 100.0;
        if (!underAnyThreshold(pct)) return;
        if (!cooldownReady(p.getUniqueId())) return;
        lastRoll.put(p.getUniqueId(), System.currentTimeMillis());

        ConfigurationSection triggers =
                plugin.getConfig().getConfigurationSection("triggers");
        if (triggers == null) return;

        double help = Chances.helpChance(triggers.getConfigurationSection("help"),
                beast, data, 100.0 - pct, 0.0);
        double takeover = Chances.takeoverChance(
                triggers.getConfigurationSection("takeover"), beast, data);

        // The kinder outcome gets rolled first: a beast that wants to help
        // doesn't need to seize the wheel.
        if (Chances.roll(help)) {
            plugin.transformations().begin(p, data, beast, 1,
                    ActiveTransformation.Cause.HELP);
        } else if (Chances.roll(takeover)) {
            plugin.transformations().begin(p, data, beast, 1,
                    ActiveTransformation.Cause.TAKEOVER);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent ev) {
        Player damager = null;
        if (ev.getDamager() instanceof Player p) damager = p;
        else if (ev.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) damager = p;
        if (damager == null) return;
        plugin.transformations().onDamageDealt(damager, ev.getFinalDamage());
    }

    /**
     * Called every 2 s by the plugin: chakra exhaustion can crack the
     * seal open (config {@code triggers.chakra-threshold}, default
     * "under 1% of max chakra").
     */
    public void tickChakraTrigger() {
        ConfigurationSection cfg = plugin.getConfig()
                .getConfigurationSection("triggers.chakra-threshold");
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;
        ConfigurationSection triggers =
                plugin.getConfig().getConfigurationSection("triggers");
        if (triggers == null || plugin.ko() == null) return;
        double pct = cfg.getDouble("percent", 1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.transformations().isTransformed(p.getUniqueId())) continue;
            if (plugin.innerWorld().inSession(p.getUniqueId())) continue;
            if (plugin.ko().isKo(p.getUniqueId())) continue;
            JinchurikiData data = plugin.jinchuriki().ofActive(p);
            if (data == null || data.finalReleaseReached()
                    || data.sealRemoved()) continue;
            BeastDefinition beast = plugin.beasts().byId(data.beastId());
            if (beast == null) continue;
            var pool = plugin.resources().pool(p);
            if (pool.max() <= 0
                    || pool.current() / pool.max() * 100.0 > pct) continue;
            if (!cooldownReady(p.getUniqueId())) continue;
            lastRoll.put(p.getUniqueId(), System.currentTimeMillis());

            double help = Chances.helpChance(
                    triggers.getConfigurationSection("help"),
                    beast, data, 100.0, 0.0);
            double takeover = Chances.takeoverChance(
                    triggers.getConfigurationSection("takeover"), beast, data);
            if (Chances.roll(help)) {
                plugin.transformations().begin(p, data, beast, 1,
                        ActiveTransformation.Cause.HELP);
            } else if (Chances.roll(takeover)) {
                plugin.transformations().begin(p, data, beast, 1,
                        ActiveTransformation.Cause.TAKEOVER);
            }
        }
    }

    private boolean underAnyThreshold(double hpPercent) {
        for (int t : plugin.getConfig().getIntegerList("triggers.hp-thresholds")) {
            if (hpPercent <= t) return true;
        }
        return false;
    }

    private boolean cooldownReady(UUID id) {
        long cd = 1000L * plugin.getConfig()
                .getLong("triggers.check-cooldown-seconds", 20);
        Long last = lastRoll.get(id);
        return last == null || System.currentTimeMillis() - last >= cd;
    }

    /* ------------------------------------------------------------ KO save */

    @EventHandler
    public void onKoEnter(KoEnterEvent ev) {
        Player p = ev.player();

        // The body gives out — a KO always ends the transformation.
        if (plugin.transformations().isTransformed(p.getUniqueId())) {
            plugin.transformations().stop(p, TransformationManager.StopReason.KO);
            return;
        }

        if (ev.cause() != KoState.Cause.HP) return;
        ConfigurationSection cfg = plugin.getConfig()
                .getConfigurationSection("triggers.on-ko");
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;

        JinchurikiData data = plugin.jinchuriki().ofActive(p);
        if (data == null || data.finalReleaseReached()) return;
        BeastDefinition beast = plugin.beasts().byId(data.beastId());
        if (beast == null) return;

        // United pair: the beast ALWAYS catches its partner — the host
        // wakes straight into Union instead of a rage stage.
        if (data.sealRemoved()) {
            double healPct = cfg.getDouble("heal-percent", 35);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || plugin.ko() == null) return;
                if (plugin.ko().isKo(p.getUniqueId())) {
                    plugin.ko().revive(p.getUniqueId(),
                            p.getMaxHealth() * healPct / 100.0, 0);
                }
                plugin.transformations().startUnion(p, data, beast);
            });
            return;
        }

        double chance = Chances.helpChance(
                plugin.getConfig().getConfigurationSection("triggers.help"),
                beast, data, 100.0, cfg.getDouble("save-chance-bonus", 15));
        if (!Chances.roll(chance)) return;

        double healPct = cfg.getDouble("heal-percent", 35);
        // One tick later — let the KO state machine finish writing first,
        // then yank the host back to their feet, transformed.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (plugin.ko() == null) return;
            if (plugin.ko().isKo(p.getUniqueId())) {
                plugin.ko().revive(p.getUniqueId(),
                        p.getMaxHealth() * healPct / 100.0, 0);
            }
            plugin.transformations().begin(p, data, beast, 1,
                    ActiveTransformation.Cause.KO_SAVE);
        });
    }

    /* --------------------------------------------------- session hygiene */

    @EventHandler
    public void onCharacterSwitch(CharacterSwitchEvent ev) {
        Player p = ev.player();
        if (plugin.innerWorld().inSession(p.getUniqueId())) {
            plugin.innerWorld().abort(p);
        }
        if (plugin.transformations().isTransformed(p.getUniqueId())) {
            plugin.transformations().stop(p, TransformationManager.StopReason.SWITCH);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        if (plugin.innerWorld().inSession(p.getUniqueId())) {
            plugin.innerWorld().abort(p);
        }
        if (plugin.transformations().isTransformed(p.getUniqueId())) {
            plugin.transformations().stop(p, TransformationManager.StopReason.QUIT);
        }
        lastRoll.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        // Crash safety: strip any leftover ShinobiTail attribute modifier.
        plugin.transformations().clearAllModifiers(ev.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent ev) {
        Player p = ev.getEntity();
        if (plugin.innerWorld().inSession(p.getUniqueId())) {
            plugin.innerWorld().abort(p);
        }
        if (plugin.transformations().isTransformed(p.getUniqueId())) {
            plugin.transformations().stop(p, TransformationManager.StopReason.KO);
        }
    }

    /* ------------------------------------------------- inner world freeze */

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent ev) {
        InnerWorldSession s = plugin.innerWorld().session(ev.getPlayer().getUniqueId());
        if (s == null) return;
        Location from = ev.getFrom();
        Location to = ev.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY()
                || from.getZ() != to.getZ()) {
            // Head stays free — the body doesn't.
            Location lock = from.clone();
            lock.setYaw(to.getYaw());
            lock.setPitch(to.getPitch());
            ev.setTo(lock);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        if (!plugin.getConfig().getBoolean("inner-world.block-commands", true)) return;
        if (!plugin.innerWorld().inSession(ev.getPlayer().getUniqueId())) return;
        String msg = ev.getMessage().toLowerCase();
        if (msg.startsWith("/tail")) return;
        ev.setCancelled(true);
        ev.getPlayer().sendMessage(Component.text(
                "Ton esprit est ailleurs… (commande bloquée dans le Monde Intérieur)",
                NamedTextColor.DARK_PURPLE));
    }

    /* ----------------------------------------------------------- fake body */

    @EventHandler(ignoreCancelled = true)
    public void onFakeBodyDamage(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof ArmorStand
                && FakeBody.isFakeBody(plugin, ev.getEntity())
                && plugin.getConfig().getBoolean("fake-body.invulnerable", true)) {
            ev.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFakeBodyManipulate(
            org.bukkit.event.player.PlayerArmorStandManipulateEvent ev) {
        if (FakeBody.isFakeBody(plugin, ev.getRightClicked())) {
            ev.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent ev) {
        // Orphaned stand-ins from a crash get swept as their chunk loads,
        // but never while their owner is still inside the Inner World.
        for (var entity : ev.getChunk().getEntities()) {
            if (!FakeBody.isFakeBody(plugin, entity)) continue;
            boolean owned = false;
            for (UUID id : activeSessionBodies()) {
                if (entity.getUniqueId().equals(id)) { owned = true; break; }
            }
            if (!owned) entity.remove();
        }
    }

    private java.util.List<UUID> activeSessionBodies() {
        java.util.List<UUID> out = new java.util.ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            InnerWorldSession s = plugin.innerWorld().session(p.getUniqueId());
            if (s != null && s.fakeBodyId() != null) out.add(s.fakeBodyId());
        }
        return out;
    }
}
