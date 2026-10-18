package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.EtatService;
import com.reborn.shinobicore.api.EtatService.FinSi;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.etat.Cadre;
import com.reborn.shinobicore.api.etat.Controle;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Vital;
import com.reborn.shinobicore.journal.Journal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * <b>Les actions longues</b> ({@code OCCUPE(action)}, docs/technique/ETATS_JOUEUR.md §4.4 {@code fin_si}, §9 étape 4) :
 * une seule mécanique d'interruption pour la fouille, le vol, la séance, le soin tenu, la concentration — et demain le
 * crochetage d'une serrure ou la casse d'une porte (« serrures et coffres »).
 *
 * <p>Pendant l'action, la ligne {@code mod.occupe} de la matrice refuse technique, coup, garde, course, ramassage,
 * interaction, changement de personnage. Le service l'interrompt — appel unique de {@code surInterruption(raison)} —
 * dès qu'une condition {@link FinSi} devient vraie (vérifiée chaque tick par {@link EtatSuivi} et sur les coups reçus),
 * à la déconnexion et au changement de personnage. Fin normale : {@link EtatService.Occupation#terminer()}.
 */
public final class Occupations implements Listener {

    /** Une action en cours. */
    final class Occ implements EtatService.Occupation {
        private final UUID compte;
        private final String action;
        private final UUID cible;
        private final Location depart;
        private final Location departCible;
        private final double tolerance;
        private final Set<FinSi> finSi;
        private final Consumer<String> rappel;
        private final Cadre cadre;
        private volatile boolean active = true;

        Occ(UUID compte, String action, Entity cible, double tolerance, Set<FinSi> finSi, Consumer<String> rappel,
            Location depart, Cadre cadre) {
            this.compte = compte;
            this.action = action;
            this.cible = cible != null ? cible.getUniqueId() : null;
            this.departCible = cible != null ? cible.getLocation().clone() : null;
            this.tolerance = tolerance;
            this.finSi = finSi;
            this.rappel = rappel;
            this.depart = depart;
            this.cadre = cadre;
        }

        @Override public String action() { return action; }

        @Override public boolean active() { return active; }

        @Override
        public void terminer() {
            if (!active) return;
            active = false;
            en.remove(compte, this);
            Player p = Bukkit.getPlayer(compte);
            if (p != null) Applicateur.reappliquer(plugin, p);
        }
    }

    private final ShinobiCore plugin;
    private final EtatServiceImpl service;
    private final Map<UUID, Occ> en = new ConcurrentHashMap<>();

    Occupations(ShinobiCore plugin, EtatServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    Occ de(UUID compte) {
        Occ o = compte == null ? null : en.get(compte);
        return o != null && o.active ? o : null;
    }

    EtatService.Occupation commencer(Player p, String action, Entity cible, double tolerance, Set<FinSi> finSi,
                                     Consumer<String> rappel) {
        if (p == null || action == null) return null;
        Consumer<String> cb = rappel != null ? rappel : r -> { };
        if (de(p.getUniqueId()) != null) {
            cb.accept("Tu es déjà occupé (" + de(p.getUniqueId()).action() + ").");
            return null;
        }
        Etat e = service.calculer(p.getUniqueId());
        if (e.ko() || e.vital() == Vital.MORT) {
            cb.accept(e.vital() == Vital.MORT ? "Ton personnage est mort." : "Tu es à terre.");
            return null;
        }
        Set<FinSi> fs = finSi == null || finSi.isEmpty() ? EnumSet.noneOf(FinSi.class) : EnumSet.copyOf(finSi);
        Occ o = new Occ(p.getUniqueId(), action, cible, Math.max(0.1, tolerance), fs, cb, p.getLocation().clone(), e.cadre());
        en.put(p.getUniqueId(), o);
        Applicateur.reappliquer(plugin, p);
        return o;
    }

    /** Vérifie les conditions de fin (appelé à chaque tick pour les joueurs occupés). */
    void verifier(Player p, Etat e) {
        Occ o = de(p.getUniqueId());
        if (o == null) return;
        String r = null;
        if (o.finSi.contains(FinSi.KO) && (e.ko() || e.vital() == Vital.MORT)) r = "Tu es tombé.";
        else if (o.finSi.contains(FinSi.DOULEUR) && e.vital() == Vital.DOULEUR) r = "Tu n'as plus la force.";
        else if (o.finSi.contains(FinSi.CONTROLE) && e.controle() != Controle.LIBRE && e.controle() != Controle.GARDE)
            r = "Tu ne contrôles plus ton corps.";
        else if (o.finSi.contains(FinSi.CADRE) && e.cadre() != o.cadre) r = "La situation a changé.";
        else if (o.finSi.contains(FinSi.BOUGE) && trop(p.getLocation(), o.depart, o.tolerance)) r = "Tu as bougé.";
        else if (o.finSi.contains(FinSi.CIBLE) && o.cible != null) {
            Entity c = Bukkit.getEntity(o.cible);
            if (c == null || !c.isValid()) r = "La cible n'est plus là.";
            else if (trop(c.getLocation(), o.departCible, o.tolerance) || trop(c.getLocation(), p.getLocation(), 6.0))
                r = "La cible s'est éloignée.";
        }
        if (r != null) interrompre(p, o, r);
    }

    private static boolean trop(Location a, Location b, double tol) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return true;
        return a.distanceSquared(b) > tol * tol;
    }

    void interrompre(Player p, Occ o, String raison) {
        if (!o.active) return;
        o.active = false;
        en.remove(o.compte, o);
        if (service.journalActif() && p != null && raison != null && !raison.isEmpty()) {
            Journal.entree(Categorie.ETAT, "OCCUPE_INTERROMPU").cible(p).detail(o.action + " · " + raison)
                    .lieu(p.getLocation()).ecrire();
        }
        try {
            o.rappel.accept(raison);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[etats] interruption de « " + o.action + " » : " + ex);
        }
        if (p != null) Applicateur.reappliquer(plugin, p);
    }

    /** Déconnexion, changement de personnage, arrêt : interruption silencieuse côté journal. */
    void interrompre(UUID compte, String raison) {
        Occ o = en.get(compte);
        if (o != null) interrompre(Bukkit.getPlayer(compte), o, raison);
    }

    void toutArreter(String raison) {
        for (UUID id : new ArrayList<>(en.keySet())) interrompre(id, raison);
    }

    /* ================================================================ événements */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrappe(EntityDamageByEntityEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        Occ o = de(p.getUniqueId());
        if (o == null || !o.finSi.contains(FinSi.FRAPPE)) return;
        Entity d = ev.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity s) d = s;
        if (d != null && d.equals(p)) return;
        interrompre(p, o, "Tu as été frappé.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent ev) {
        interrompre(ev.getPlayer().getUniqueId(), "");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwitch(com.reborn.shinobicore.api.event.CharacterSwitchEvent ev) {
        if (ev.previous() != null && ev.next() != null && ev.previous().id().equals(ev.next().id())) return;
        interrompre(ev.player().getUniqueId(), "");
    }
}
