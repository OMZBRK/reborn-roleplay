package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gel staff <b>persistant</b> ({@code gels.yml}, par compte) : survit à la déconnexion et au redémarrage. Un joueur gelé
 * ne se déplace plus (la chute reste permise), n'attaque plus, n'interagit plus, ne lâche rien, ne casse ni ne pose de
 * bloc. Le tchat et les commandes restent ouverts (il doit pouvoir répondre au staff).
 */
public final class Gels implements Listener {

    public record Gel(String par, String motif, long le, String nom) { }

    private final ShinobiCore plugin;
    private final File fichier;
    private final Map<UUID, Gel> gels = new ConcurrentHashMap<>();

    public Gels(ShinobiCore plugin) {
        this.plugin = plugin;
        this.fichier = new File(plugin.getDataFolder(), "gels.yml");
        charger();
    }

    public boolean gele(UUID id) { return id != null && gels.containsKey(id); }

    public Gel gel(UUID id) { return id == null ? null : gels.get(id); }

    public Map<UUID, Gel> tous() { return Map.copyOf(gels); }

    /** Gèle (ou met à jour le motif). Renvoie false si déjà gelé. */
    public boolean geler(CommandSender staff, UUID cible, String nom, String motif) {
        boolean neuf = !gels.containsKey(cible);
        gels.put(cible, new Gel(staff.getName(), motif == null ? "" : motif, System.currentTimeMillis(), nom));
        sauver();
        Player t = Bukkit.getPlayer(cible);
        if (t != null) annoncer(t, motif);
        Journal.entree(Categorie.STAFF, "GEL").acteur(staff).cible(cible)
                .detail(motif == null || motif.isBlank() ? "" : "motif : " + motif)
                .lieu(t != null ? t.getLocation() : null).ecrire();
        return neuf;
    }

    public boolean degeler(CommandSender staff, UUID cible) {
        Gel g = gels.remove(cible);
        if (g == null) return false;
        sauver();
        Player t = Bukkit.getPlayer(cible);
        if (t != null) {
            t.clearTitle();
            t.sendMessage(Component.text("Tu peux de nouveau bouger.", NamedTextColor.GRAY));
        }
        Journal.entree(Categorie.STAFF, "DEGEL").acteur(staff).cible(cible)
                .detail("gelé depuis " + Math.max(1, (System.currentTimeMillis() - g.le()) / 60_000L) + " min par " + g.par())
                .ecrire();
        return true;
    }

    private void annoncer(Player t, String motif) {
        t.showTitle(Title.title(Component.text("Gelé", NamedTextColor.AQUA),
                Component.text(motif == null || motif.isBlank() ? "Un membre du staff t'a immobilisé." : motif,
                        NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));
    }

    /* ================================================================ effets */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent ev) {
        if (!gele(ev.getPlayer().getUniqueId())) return;
        var f = ev.getFrom();
        var t = ev.getTo();
        if (f.getX() != t.getX() || f.getZ() != t.getZ() || f.getY() < t.getY()) {
            var back = f.clone();
            back.setY(Math.min(f.getY(), t.getY()));
            back.setYaw(t.getYaw());
            back.setPitch(t.getPitch());
            ev.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        Player d = ev.getDamager() instanceof Player p ? p
                : ev.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        if (d != null && gele(d.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent ev) {
        if (gele(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent ev) {
        if (gele(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent ev) {
        if (gele(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent ev) {
        if (gele(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent ev) {
        if (gele(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent ev) {
        if (gele(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Gel g = gels.get(ev.getPlayer().getUniqueId());
        if (g == null) return;
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) annoncer(p, g.motif()); }, 40L);
        Journal.entree(Categorie.STAFF, "GEL_RECONNEXION").acteur(p).detail("toujours gelé (par " + g.par() + ")").ecrire();
    }

    /* ================================================================ disque */

    private void charger() {
        if (!fichier.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(fichier);
        ConfigurationSection s = y.getConfigurationSection("gels");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                gels.put(UUID.fromString(k), new Gel(s.getString(k + ".par", "?"), s.getString(k + ".motif", ""),
                        s.getLong(k + ".le"), s.getString(k + ".nom", "")));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void sauver() {
        YamlConfiguration y = new YamlConfiguration();
        gels.forEach((id, g) -> {
            String k = "gels." + id;
            y.set(k + ".par", g.par());
            y.set(k + ".motif", g.motif());
            y.set(k + ".le", g.le());
            y.set(k + ".nom", g.nom());
        });
        try {
            plugin.getDataFolder().mkdirs();
            y.save(fichier);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Échec d'écriture de gels.yml", e);
        }
    }
}
