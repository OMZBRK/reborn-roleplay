package com.reborn.shinobicore.staff.outils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import com.reborn.shinobicore.vanish.VanishManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Mode staff</b> ({@code /modestaff}, état « Mode staff » du Game Plan §1.5) : invisible aux joueurs et au staff hors
 * mode staff (les staffs en mode staff se voient entre eux), vol, invulnérable, aucune action de jeu (ni coup donné ni
 * reçu, ni ramassage, ni lâcher). Chaque seconde, les <b>plaques staff</b> des joueurs proches partent sur
 * {@code reborn:plaques} (PLUGINS_REFERENCE §4 bis.5). Le mode tombe à la déconnexion. Entrée et sortie journalisées.
 */
public final class ModeStaff implements Listener {

    public static final String CANAL_PLAQUES = "reborn:plaques";
    private static final double RAYON = 64.0;

    private record Avant(boolean vol, boolean enVol, boolean invulnerable, boolean collision, boolean vanish) { }

    private final ShinobiCore plugin;
    private final Map<UUID, Avant> actifs = new ConcurrentHashMap<>();
    private BukkitTask tache;

    public ModeStaff(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL_PLAQUES)) m.registerOutgoingPluginChannel(plugin, CANAL_PLAQUES);
    }

    public void demarrer() {
        tache = Bukkit.getScheduler().runTaskTimer(plugin, this::plaques, 20L, 20L);
    }

    public void arreter() {
        if (tache != null) tache.cancel();
        for (UUID id : List.copyOf(actifs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) sortir(p, false);
        }
    }

    public boolean actif(UUID id) { return id != null && actifs.containsKey(id); }

    public Set<UUID> actifs() { return Set.copyOf(actifs.keySet()); }

    /** Bascule ; renvoie le nouvel état. */
    public boolean basculer(Player p) {
        if (actif(p.getUniqueId())) { sortir(p, true); return false; }
        entrer(p);
        return true;
    }

    private void entrer(Player p) {
        VanishManager v = plugin.vanish();
        boolean dejaVanish = v != null && v.isVanished(p.getUniqueId());
        actifs.put(p.getUniqueId(), new Avant(p.getAllowFlight(), p.isFlying(), p.isInvulnerable(), p.isCollidable(), dejaVanish));
        if (v != null) {
            v.setStaffMode(p.getUniqueId(), true);
            v.enable(p, VanishManager.Mode.HIDE_ALL, Set.of());
            v.applyForViewer(p);
        }
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvulnerable(true);
        p.setCollidable(false);
        p.sendActionBar(Component.text("Mode staff : invisible, aucune action de jeu.", NamedTextColor.GOLD));
        Journal.entree(Categorie.STAFF, "MODE_STAFF").acteur(p).detail("entrée").lieu(p.getLocation()).ecrire();
    }

    private void sortir(Player p, boolean journal) {
        Avant a = actifs.remove(p.getUniqueId());
        if (a == null) return;
        VanishManager v = plugin.vanish();
        if (v != null) {
            v.setStaffMode(p.getUniqueId(), false);
            if (a.vanish()) v.enable(p, VanishManager.Mode.HIDE_ALL, Set.of()); else v.disable(p);
            v.applyForViewer(p);
        }
        p.setFlying(a.enVol() && a.vol());
        p.setAllowFlight(a.vol() || p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR);
        p.setInvulnerable(a.invulnerable());
        p.setCollidable(a.collision());
        if (p.getListeningPluginChannels().contains(CANAL_PLAQUES)) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "plaques");
            o.addProperty("on", false);
            p.sendPluginMessage(plugin, CANAL_PLAQUES, o.toString().getBytes(StandardCharsets.UTF_8));
        }
        p.sendActionBar(Component.text("Mode staff terminé.", NamedTextColor.GRAY));
        if (journal) Journal.entree(Categorie.STAFF, "MODE_STAFF").acteur(p).detail("sortie").lieu(p.getLocation()).ecrire();
    }

    /* ================================================================ plaques */

    private void plaques() {
        if (actifs.isEmpty()) return;
        for (UUID id : actifs.keySet()) {
            Player staff = Bukkit.getPlayer(id);
            if (staff == null || !staff.getListeningPluginChannels().contains(CANAL_PLAQUES)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("t", "plaques");
            o.addProperty("on", true);
            JsonArray arr = new JsonArray();
            for (Player p : staff.getWorld().getPlayers()) {
                if (p.equals(staff) || p.getLocation().distanceSquared(staff.getLocation()) > RAYON * RAYON) continue;
                arr.add(plaque(p));
            }
            o.add("joueurs", arr);
            staff.sendPluginMessage(plugin, CANAL_PLAQUES, o.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private JsonObject plaque(Player p) {
        JsonObject j = new JsonObject();
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        j.addProperty("uuid", p.getUniqueId().toString());
        j.addProperty("pid", c != null && c.hasPlayerId() ? PlayerIdRegistry.format(c.playerId()) : "");
        j.addProperty("perso", c != null ? c.name() : "");
        j.addProperty("pseudo", p.getName());
        j.addProperty("etat", etat(p, c));
        j.addProperty("pv", Math.round(p.getHealth()));
        var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        j.addProperty("pvMax", Math.round(max != null ? max.getValue() : 20));
        j.addProperty("staff", StaffGrades.of(p) > StaffGrades.NONE);
        OutilsStaff o = plugin.outilsStaff();
        j.addProperty("gele", o != null && o.gels().gele(p.getUniqueId()));
        j.addProperty("modeStaff", actif(p.getUniqueId()));
        return j;
    }

    /** État court (KO, ATA, duel, gelé, écran noir) — sert aussi au Poste de garde. */
    public String etat(Player p, ShinobiCharacter c) {
        List<String> parts = new ArrayList<>();
        KoState ko = plugin.ko() != null ? plugin.ko().getKo(p.getUniqueId()) : null;
        if (ko != null) parts.add(ko.isDowned() ? "À terre" : "Inconscient");
        if (c != null && plugin.ata() != null && plugin.ata().view(c.id()) != null) parts.add("ATA");
        if (plugin.duels() != null && plugin.duels().inDuel(p.getUniqueId())) parts.add("Duel");
        OutilsStaff o = plugin.outilsStaff();
        if (o != null && o.gels().gele(p.getUniqueId())) parts.add("Gelé");
        if (o != null && o.effets().enNoir(p.getUniqueId())) parts.add("Écran noir");
        if (c == null) parts.add("Sans personnage");
        return String.join(" · ", parts);
    }

    /* ================================================================ aucune action de jeu */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        Player d = ev.getDamager() instanceof Player p ? p
                : ev.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        if (d != null && actif(d.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p && actif(p.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent ev) {
        if (ev.getEntity() instanceof Player p && actif(p.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent ev) {
        if (actif(ev.getPlayer().getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent ev) {
        if (ev.getTarget() instanceof Player p && actif(p.getUniqueId())) ev.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        if (actif(ev.getPlayer().getUniqueId())) sortir(ev.getPlayer(), true);
    }
}
