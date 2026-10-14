package com.reborn.shinobicore.staff.outils;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Mode animation</b> ({@code /modeanim}, ou {@code /modestaff anim}) — décision du 14/10 : l'animateur ou le MJ
 * incarne un personnage pour un event. À l'inverse du mode staff, il est <b>visible des joueurs</b> et les
 * <b>mécaniques RP sont permises</b> (coups, techniques, menu d'interaction, objets…). En échange, tout est
 * <b>journalisé</b> en plus du journal habituel : entrée et sortie ({@code STAFF / MODE_ANIMATION}), chaque commande
 * tapée, chaque message, chaque coup porté ({@code STAFF / ANIMATION}). Indicateur discret : « Animation » dans l'état
 * du joueur (Poste de garde, plaques du mode staff) et ligne fine en haut à droite de son propre écran (mod, message
 * {@code mode} du canal {@code reborn:staff}).
 *
 * <p>Permission dédiée {@code reborn.staff.animation} (OP par défaut) : un animateur n'a pas besoin d'un grade staff.
 * Exclusif avec le mode staff (entrer dans l'un fait sortir de l'autre). Tombe à la déconnexion.
 */
public final class ModeAnimation implements Listener {

    public static final String PERMISSION = "reborn.staff.animation";

    private final ShinobiCore plugin;
    private final Map<UUID, Long> actifs = new ConcurrentHashMap<>();

    public ModeAnimation(ShinobiCore plugin) { this.plugin = plugin; }

    public boolean actif(UUID id) { return id != null && actifs.containsKey(id); }

    public Set<UUID> actifs() { return Set.copyOf(actifs.keySet()); }

    public static boolean permis(Player p) { return p.hasPermission(PERMISSION); }

    /** Bascule ; renvoie le nouvel état (false aussi si la permission manque). */
    public boolean basculer(Player p) {
        if (actif(p.getUniqueId())) { sortir(p, true); return false; }
        if (!permis(p)) {
            p.sendMessage(Component.text("Réservé aux animateurs et MJ (permission " + PERMISSION + ").", NamedTextColor.RED));
            return false;
        }
        entrer(p);
        return true;
    }

    private void entrer(Player p) {
        ModeStaff ms = plugin.outilsStaff().mode();
        if (ms.actif(p.getUniqueId())) ms.basculer(p);   // exclusifs
        actifs.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendActionBar(Component.text("Mode animation : tu joues pour l'event, tout est journalisé.", NamedTextColor.LIGHT_PURPLE));
        Journal.entree(Categorie.STAFF, "MODE_ANIMATION").acteur(p).detail("entrée").lieu(p.getLocation()).ecrire();
        if (plugin.staffPanel() != null) {
            plugin.staffPanel().envoyerMode(p);
            plugin.staffPanel().system(p.getName() + " passe en mode animation.");
        }
    }

    void sortir(Player p, boolean journal) {
        Long depuis = actifs.remove(p.getUniqueId());
        if (depuis == null) return;
        long min = (System.currentTimeMillis() - depuis) / 60_000L;
        if (p.isOnline()) p.sendActionBar(Component.text("Mode animation terminé.", NamedTextColor.GRAY));
        if (journal) Journal.entree(Categorie.STAFF, "MODE_ANIMATION").acteur(p).detail("sortie après " + min + " min")
                .lieu(p.getLocation()).ecrire();
        if (plugin.staffPanel() != null && p.isOnline()) plugin.staffPanel().envoyerMode(p);
    }

    public void arreter() {
        for (UUID id : List.copyOf(actifs.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) sortir(p, true);
        }
    }

    /* ================================================================ tout est journalisé */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        Player p = ev.getPlayer();
        if (!actif(p.getUniqueId())) return;
        String m = ev.getMessage();
        if (m.toLowerCase(Locale.ROOT).startsWith("/modeanim")) return;
        Journal.entree(Categorie.STAFF, "ANIMATION").acteur(p).detail("commande " + (m.length() > 200 ? m.substring(0, 200) + "…" : m))
                .lieu(p.getLocation()).ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent ev) {
        Player p = ev.getPlayer();
        if (!actif(p.getUniqueId())) return;
        String t = PlainTextComponentSerializer.plainText().serialize(ev.message());
        Bukkit.getScheduler().runTask(plugin, () -> Journal.entree(Categorie.STAFF, "ANIMATION").acteur(p)
                .detail("tchat « " + (t.length() > 200 ? t.substring(0, 200) + "…" : t) + " »").lieu(p.getLocation()).ecrire());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        Player d = ev.getDamager() instanceof Player p ? p
                : ev.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p ? p : null;
        if (d == null || !actif(d.getUniqueId())) return;
        var e = Journal.entree(Categorie.STAFF, "ANIMATION").acteur(d)
                .detail(String.format(Locale.FRANCE, "coup %.1f PV → %s", ev.getFinalDamage(), ev.getEntity().getName()))
                .lieu(ev.getEntity().getLocation());
        if (ev.getEntity() instanceof Player v) e = e.cible(v);
        e.ecrire();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        if (actif(ev.getPlayer().getUniqueId())) sortir(ev.getPlayer(), true);
    }
}
