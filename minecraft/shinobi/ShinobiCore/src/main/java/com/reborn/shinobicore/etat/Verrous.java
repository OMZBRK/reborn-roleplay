package com.reborn.shinobicore.etat;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.StunService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * <b>Verrous d'état</b> — né du lot de correctifs urgents (docs/technique/ETATS_JOUEUR.md §9 étape 0). Depuis le
 * 17/10 (étape 2), les refus <b>délèguent au service des états</b> ({@link EtatServiceImpl} : « peut-il X ? » d'après
 * la matrice d'{@code etats.yml}) ; les anciens tests restent en <b>repli</b> si le service n'est pas encore démarré.
 *
 * <p>Chaque méthode renvoie la <b>raison du refus</b> (phrase courte, tutoiement) ou {@code null} si l'action est
 * permise. Lecture seule : rien n'est écrit ici. Tout tourne sur le thread principal.
 */
public final class Verrous {

    private Verrous() { }

    private static ShinobiCore core() { return ShinobiCore.get(); }

    /** Le service des états, ou null (pas encore démarré, mode build). */
    static EtatServiceImpl svc() {
        ShinobiCore c = core();
        return c == null ? null : c.etats();
    }

    /** Raison du refus de la capacité selon la matrice, ou null (permis, ou service absent). */
    public static String refus(UUID id, com.reborn.shinobicore.api.etat.Capacite cap) {
        EtatServiceImpl s = svc();
        if (s == null || id == null) return null;
        String r = s.verifier(id, cap, null).refus();
        return r == null || r.isEmpty() ? null : r;
    }

    /**
     * Ce que l'acteur veut faire SUR la cible ({@code ETRE_PORTE}, {@code ETRE_EXECUTE}, {@code ETRE_FOUILLE}) : raison
     * du refus côté cible, ou null. Ignoré quand l'acteur est déjà le lien de la cible (passer de la gorge au portage).
     */
    public static String refusSur(UUID acteur, UUID cible, com.reborn.shinobicore.api.etat.Capacite cap) {
        EtatServiceImpl s = svc();
        if (s == null || cible == null) return null;
        var e = s.calculer(cible);
        if (e.lien() != null && acteur != null && acteur.equals(e.lien().source())) return null;
        String r = e.verdict(cap).refus();
        return r == null || r.isEmpty() ? null : r;
    }

    /* ================================================================ lectures */

    public static boolean ko(UUID id) {
        ShinobiCore c = core();
        return c != null && c.ko() != null && c.ko().isKo(id);
    }

    public static boolean douleur(UUID id) {
        ShinobiCore c = core();
        return c != null && c.douleur() != null && c.douleur().isInPain(id);
    }

    public static boolean ata(UUID id) {
        ShinobiCore c = core();
        return c != null && c.ata() != null && c.ata().isImpaired(id);
    }

    public static boolean mort(UUID id) {
        ShinobiCore c = core();
        return c != null && c.deathScene() != null && c.deathScene().isDead(id);
    }

    public static boolean gele(UUID id) {
        ShinobiCore c = core();
        return c != null && c.outilsStaff() != null && c.outilsStaff().gels().gele(id);
    }

    public static boolean horsJeu(UUID id) {
        ShinobiCore c = core();
        return c != null && c.outilsStaff() != null && c.outilsStaff().mode().horsJeu(id);
    }

    public static boolean cinematique(UUID id) {
        ShinobiCore c = core();
        Player p = Bukkit.getPlayer(id);
        return c != null && p != null && c.cinematics() != null && c.cinematics().isPlaying(p);
    }

    public static boolean porte(UUID id) {
        ShinobiCore c = core();
        return c != null && c.porter() != null && c.porter().carrierOf(id) != null;
    }

    public static boolean porteur(UUID id) {
        ShinobiCore c = core();
        return c != null && c.porter() != null && c.porter().isCarrying(id);
    }

    /** Tenu par quelqu'un (gorge, exécution en cours). */
    public static boolean tenu(UUID id) {
        ShinobiCore c = core();
        return c != null && c.corps() != null && c.corps().isHeld(id);
    }

    /** Tient quelqu'un (gorge) ou exécute. */
    public static boolean tient(UUID id) {
        ShinobiCore c = core();
        return c != null && c.corps() != null && c.corps().isHolding(id);
    }

    public static boolean enDuel(UUID id) {
        ShinobiCore c = core();
        return c != null && c.duels() != null && c.duels().inDuel(id);
    }

    public static boolean etourdi(UUID id) {
        StunService s = Bukkit.getServicesManager().load(StunService.class);
        return s != null && s.isStunned(id);
    }

    public static boolean enGarde(UUID id) {
        StunService s = Bukkit.getServicesManager().load(StunService.class);
        return s != null && s.isGuarding(id);
    }

    /* ================================================================ refus */

    /**
     * Le corps n'appartient plus au joueur : KO, mort RP en scène, gel du staff, cinématique, porté, tenu, ou il en
     * tient un autre (gorge, exécution).
     */
    public static String corpsIndisponible(UUID id) {
        if (id == null) return null;
        if (ko(id)) {
            ShinobiCore c = core();
            return c.ko().isDowned(id) ? "Tu es à terre." : "Tu es inconscient.";
        }
        if (mort(id)) return "Ton personnage est mort.";
        if (gele(id)) return "Tu es gelé par le staff.";
        if (cinematique(id)) return "Cinématique en cours.";
        if (porte(id)) return "On te porte.";
        if (tenu(id)) return "On te tient.";
        if (tient(id)) return "Tu tiens quelqu'un.";
        return null;
    }

    /**
     * Lancer une technique — en plus de ce que le lancer vérifie déjà (KO, ATA / douleur, duel, mode staff) : gel,
     * mort, cinématique, porté / tenu, <b>étourdi</b> et <b>garde</b> (décisions du 14/10 : étourdi = immobile total ;
     * garde = immobile, sans technique).
     */
    public static String technique(UUID id) {
        EtatServiceImpl s = svc();
        if (s != null && id != null) return s.verifier(id, com.reborn.shinobicore.api.etat.Capacite.TECHNIQUE, Boolean.TRUE).refus();
        String r = corpsIndisponible(id);
        if (r != null) return r;
        if (etourdi(id)) return "Étourdi !";
        if (enGarde(id)) return "En garde : pas de technique.";
        return null;
    }

    /** Kit de mobilité (ShinobiAbilities), grappin, tyrolienne, course ninja, Voie du Flux. */
    public static String mobilite(UUID id) {
        EtatServiceImpl s = svc();
        if (s != null && id != null) return refus(id, com.reborn.shinobicore.api.etat.Capacite.MOBILITE);
        String r = corpsIndisponible(id);
        if (r != null) return r;
        if (horsJeu(id)) return "Mode staff : aucune mécanique RP.";
        if (douleur(id) || ata(id)) return "Trop blessé.";
        if (etourdi(id)) return "Étourdi !";
        if (enGarde(id)) return "En garde.";
        if (porteur(id)) return "Tu portes quelqu'un.";
        return null;
    }

    /**
     * Changer de personnage ou rouvrir la sélection (qui téléporte à l'ancre) : refusé hors état « libre » — c'était
     * une évasion (gel, KO, combat, porté…).
     */
    public static String changementPerso(UUID id) {
        EtatServiceImpl s = svc();
        if (s != null && id != null) return refus(id, com.reborn.shinobicore.api.etat.Capacite.CHANGER_PERSO);
        String r = corpsIndisponible(id);
        if (r != null) return r;
        if (douleur(id)) return "Tu es à bout de forces.";
        if (porteur(id)) return "Dépose d'abord la personne que tu portes.";
        if (enDuel(id)) return "Tu es en duel.";
        long reste = EnCombat.resteMs(id);
        if (reste > 0) return "Tu es en combat (encore " + (reste / 1000 + 1) + " s).";
        return null;
    }

    /** Téléportation demandée par le joueur (départ d'un parcours d'entraînement…). */
    public static String teleportation(UUID id) {
        EtatServiceImpl s = svc();
        if (s != null && id != null) return refus(id, com.reborn.shinobicore.api.etat.Capacite.TELEPORTE_PAR_COMMANDE);
        String r = changementPerso(id);
        if (r != null) return r;
        if (ata(id)) return "Trop blessé.";
        if (etourdi(id)) return "Étourdi !";
        if (horsJeu(id)) return "Mode staff : aucune mécanique RP.";
        return null;
    }

    /* ================================================================ ciblage */

    /**
     * Entité à <b>ignorer</b> par les effets de zone (potions, poussées, révélation) : staff en mode staff, joueur
     * invisible par le vanish, en scène de mort, en cinématique, en sélection de personnage, invulnérable, en
     * créatif / spectateur ; support d'armure (faux corps, sièges, décor). Les dégâts, eux, passent déjà par les
     * écouteurs de dégâts (qui annulent pour ces cas).
     */
    public static boolean horsCible(Entity e) {
        if (e == null) return true;
        EtatServiceImpl s = svc();
        if (s != null) return s.horsCible(e);
        if (e instanceof ArmorStand) return true;
        if (!(e instanceof Player p)) return false;
        UUID id = p.getUniqueId();
        if (p.isInvulnerable()) return true;
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return true;
        ShinobiCore c = core();
        if (c == null) return false;
        if (horsJeu(id) || mort(id) || cinematique(id)) return true;
        if (c.vanish() != null && c.vanish().isVanished(id)) return true;
        return c.characterSelect() != null && c.characterSelect().isSelecting(id);
    }
}
