package com.reborn.shinobicore.ko.action;

import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scène de mort (KO-7) : quand un personnage meurt, le joueur <b>reste</b> là où est son corps, allongé, et ne
 * peut plus rien faire (ni bouger, ni parler, ni commandes, ni interagir). Côté mod : les yeux se ferment, écran
 * noir ; au bout de {@code mort-rp.scene-min-secondes}, « Assister à votre réelle fin ? ». Non → il part
 * ({@code /corps quitter}). Oui → il regarde la suite en vue grisée, sans HUD, bandes de cinéma, et part quand il
 * veut.
 *
 * <p>Tant que le joueur assiste à la scène, <b>le corps, c'est lui</b> : les autres peuvent le porter, le déposer,
 * le fouiller ({@code /corps}). En partant, le corps reste au sol ({@code mort-rp.corps-minutes}) là où il a été
 * laissé : un mannequin que le mod habille de l'apparence Reborn du personnage ; puis la sélection de personnage.
 * Une déconnexion laisse aussi le corps.
 */
public final class DeathScene implements Listener {

    private record Scene(Location at, String name, long since, String look) {}
    private record Corpse(long until, Location at, String look) {}

    private final ShinobiCore plugin;
    private final Map<UUID, Scene> scenes = new ConcurrentHashMap<>();
    private final Map<UUID, Corpse> corpses = new ConcurrentHashMap<>();
    private BukkitTask task;

    public DeathScene(ShinobiCore plugin) { this.plugin = plugin; }

    public void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L); }

    public void stop() {
        if (task != null) task.cancel();
        for (UUID id : scenes.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) end(p, false);
        }
        for (UUID id : corpses.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        corpses.clear();
    }

    public boolean isDead(UUID player) { return scenes.containsKey(player); }

    /** Corps au sol près de {@code at} → apparence Reborn (pour que le mod l'habille). */
    public Map<UUID, String> corpsesNear(Location at, double r) {
        Map<UUID, String> out = new java.util.LinkedHashMap<>();
        corpses.forEach((id, c) -> {
            if (c.look().isBlank() || !c.at().getWorld().equals(at.getWorld())) return;
            if (c.at().distanceSquared(at) <= r * r) out.put(id, c.look());
        });
        return out;
    }

    /** Le corps a été déplacé (porté puis déposé) : la scène continue à cet endroit. */
    public void moveBody(UUID player, Location to) {
        if (to == null) return;
        scenes.computeIfPresent(player, (id, s) -> new Scene(to.clone(), s.name(), s.since(), s.look()));
    }

    private long minMillis() { return Math.max(0, plugin.getConfig().getLong("mort-rp.scene-min-secondes", 10)) * 1000L; }

    /** Le personnage vient de mourir : le joueur reste sur son corps, spectateur de la suite. */
    public void start(Player p, String name) {
        plugin.porter().releaseAllInvolving(p.getUniqueId());
        if (plugin.corps() != null) plugin.corps().release(p.getUniqueId());
        plugin.ko().forceClear(p.getUniqueId());
        Location at = p.getLocation().clone();
        var ch = plugin.characters().getActive(p.getUniqueId());
        scenes.put(p.getUniqueId(), new Scene(at, name, System.currentTimeMillis(), ch != null ? ch.appearance() : ""));
        p.setInvulnerable(true);
        p.closeInventory();
        p.setPose(Pose.SLEEPING, true);
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.5f);
        JsonObject o = new JsonObject();
        o.addProperty("t", "mort");
        o.addProperty("nom", name);
        o.addProperty("min", (int) (minMillis() / 1000L));
        boolean modded = p.getListeningPluginChannels().contains(CorpsService.CHANNEL);
        if (modded && plugin.corps() != null) plugin.corps().send(p, o);
        else p.showTitle(Title.title(Component.text("Tu es mort", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text(name, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(800), Duration.ofSeconds(5), Duration.ofMillis(1500))));
    }

    /** {@code /corps quitter} : fin de la scène si le minimum est passé. */
    public void leave(Player p) {
        Scene s = scenes.get(p.getUniqueId());
        if (s == null) return;
        long left = s.since() + minMillis() - System.currentTimeMillis();
        if (left > 0) {
            p.sendActionBar(Component.text("Encore " + (left / 1000 + 1) + " s.", NamedTextColor.GRAY));
            return;
        }
        end(p, true);
    }

    private void end(Player p, boolean select) {
        if (!scenes.containsKey(p.getUniqueId())) return;
        plugin.porter().releaseAllInvolving(p.getUniqueId());          // porté : déposé au sol d'abord
        Scene s = scenes.remove(p.getUniqueId());
        if (s == null) return;
        spawnCorpse(p, s);
        p.setInvulnerable(false);
        p.setPose(Pose.STANDING, false);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        JsonObject o = new JsonObject();
        o.addProperty("t", "fin");
        if (plugin.corps() != null && p.isOnline()) plugin.corps().send(p, o);
        if (select && plugin.characterSelect() != null && plugin.characterSelect().isEnabled()) {
            plugin.characterSelect().beginSelection(p);
        } else if (select) {
            p.sendMessage(Component.text("Choisis un autre personnage avec /character.", NamedTextColor.GRAY));
        }
    }

    private void spawnCorpse(Player p, Scene s) {
        long minutes = plugin.getConfig().getLong("mort-rp.corps-minutes", 15);
        if (minutes <= 0) return;
        try {
            Mannequin m = s.at().getWorld().spawn(s.at(), Mannequin.class, c -> {
                c.setProfile(ResolvableProfile.resolvableProfile(p.getPlayerProfile()));
                c.setImmovable(true);
                c.setInvulnerable(true);
                c.setPersistent(false);
                c.setDescription(Component.text(s.name(), NamedTextColor.DARK_GRAY));
                c.setPose(Pose.SLEEPING, true);
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    c.getEquipment().setItem(slot, p.getInventory().getItem(slot));
                }
            });
            corpses.put(m.getUniqueId(), new Corpse(System.currentTimeMillis() + minutes * 60_000L, s.at(), s.look()));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Corps de " + s.name() + " : mannequin impossible (" + ex.getMessage() + ").");
        }
    }

    private void tick() {
        for (Map.Entry<UUID, Scene> e : scenes.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            if (p.getVehicle() != null) continue;                         // porté : il suit le porteur
            Location at = e.getValue().at();
            Location cur = p.getLocation();
            if (!cur.getWorld().equals(at.getWorld()) || cur.distanceSquared(at) > 0.05) {
                Location to = at.clone();
                to.setYaw(cur.getYaw());
                to.setPitch(cur.getPitch());
                p.teleport(to);
            }
            if (p.getPose() != Pose.SLEEPING) p.setPose(Pose.SLEEPING, true);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, true, false, false));
        }
        if (Bukkit.getCurrentTick() % 200 == 0) {
            long now = System.currentTimeMillis();
            corpses.entrySet().removeIf(c -> {
                if (c.getValue().until() > now) return false;
                Entity ent = Bukkit.getEntity(c.getKey());
                if (ent != null) ent.remove();
                return true;
            });
        }
    }

    /* ================================================== plus rien à faire */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent ev) {
        if (!isDead(ev.getPlayer().getUniqueId())) return;
        ev.setCancelled(true);
        ev.getPlayer().sendActionBar(Component.text("Tu es mort.", NamedTextColor.DARK_GRAY));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        if (!isDead(ev.getPlayer().getUniqueId())) return;
        String m = ev.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (m.startsWith("/corps quitter")) return;
        ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent ev) { if (isDead(ev.getPlayer().getUniqueId())) ev.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent ev) { if (isDead(ev.getPlayer().getUniqueId())) ev.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent ev) { if (isDead(ev.getPlayer().getUniqueId())) ev.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent ev) { if (isDead(ev.getPlayer().getUniqueId())) ev.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (isDead(ev.getEntity().getUniqueId())) { ev.setCancelled(true); return; }
        if (ev instanceof EntityDamageByEntityEvent by && isDead(by.getDamager().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) { end(ev.getPlayer(), false); }
}
