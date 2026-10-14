package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.journal.Journal;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État « <b>en combat</b> » (décision du 14/10, docs/technique/ETATS_JOUEUR.md §11 n° 4) : un joueur qui porte ou reçoit
 * un coup d'un autre joueur est en combat pendant {@code combat.duree-secondes} (15 s par défaut) après le dernier coup.
 *
 * <ul>
 *   <li>Lu par {@link Verrous#changementPerso} et {@link Verrous#teleportation} : pas de changement de personnage ni de
 *       départ de parcours en plein combat (évasion).</li>
 *   <li><b>Déconnexion en combat</b> : journal ({@code COMBAT / DECO_EN_COMBAT}) et alerte au Poste de garde ; pas de
 *       sanction automatique.</li>
 * </ul>
 * Mémoire seulement (un redémarrage met fin aux combats).
 */
public final class EnCombat implements Listener {

    private static final Map<UUID, Long> dernierCoup = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> dernierAdversaire = new ConcurrentHashMap<>();
    private static volatile long dureeMs = 15_000L;

    private final ShinobiCore plugin;

    public EnCombat(ShinobiCore plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        dureeMs = Math.max(1, plugin.getConfig().getLong("combat.duree-secondes", 15)) * 1000L;
    }

    /** Temps de combat restant (ms), 0 hors combat. Règle pure : {@link #reste(long, long, long)}. */
    public static long resteMs(UUID id) {
        if (id == null) return 0;
        Long t = dernierCoup.get(id);
        return t == null ? 0 : reste(t, System.currentTimeMillis(), dureeMs);
    }

    public static boolean actif(UUID id) { return resteMs(id) > 0; }

    /** Règle pure : temps restant d'un combat dont le dernier coup date de {@code dernier}. */
    static long reste(long dernier, long maintenant, long duree) {
        return Math.max(0, dernier + duree - maintenant);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        if (!(ev.getEntity() instanceof Player victime)) return;
        Player auteur = auteur(ev.getDamager());
        if (auteur == null || auteur.equals(victime)) return;
        long now = System.currentTimeMillis();
        dernierCoup.put(victime.getUniqueId(), now);
        dernierCoup.put(auteur.getUniqueId(), now);
        dernierAdversaire.put(victime.getUniqueId(), auteur.getUniqueId());
        dernierAdversaire.put(auteur.getUniqueId(), victime.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        UUID id = p.getUniqueId();
        long reste = resteMs(id);
        UUID adv = dernierAdversaire.remove(id);
        dernierCoup.remove(id);
        if (reste <= 0) return;
        Player ap = adv != null ? Bukkit.getPlayer(adv) : null;
        String contre = ap != null ? ap.getName() : adv != null ? adv.toString() : "?";
        var e = Journal.entree(Categorie.COMBAT, "DECO_EN_COMBAT").acteur(p).lieu(p.getLocation())
                .detail("déconnecté " + ((dureeMs - reste) / 1000) + " s après le dernier coup · contre " + contre);
        if (adv != null) e.cible(adv);
        e.ecrire();
        if (plugin.staffPanel() != null) plugin.staffPanel().onDecoEnCombat(p, contre, p.getLocation());
    }

    private static Player auteur(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }
}
