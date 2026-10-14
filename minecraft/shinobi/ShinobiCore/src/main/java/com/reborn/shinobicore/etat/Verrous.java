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
 * <b>Verrous d'état</b> — le lot de correctifs urgents (docs/technique/ETATS_JOUEUR.md §9 étape 0) : un seul endroit
 * qui dit « non » aux actions qui servaient d'évasion ou d'exploit (grappin d'un KO, sélection de personnage qui
 * téléporte un gelé, technique d'un étourdi…), en attendant le service d'états de l'étape 1, qui remplacera ces
 * méthodes par {@code peut(joueur, capacité)}.
 *
 * <p>Chaque méthode renvoie la <b>raison du refus</b> (phrase courte, tutoiement) ou {@code null} si l'action est
 * permise. Lecture seule : rien n'est écrit ici. Tout tourne sur le thread principal.
 */
public final class Verrous {

    private Verrous() { }

    private static ShinobiCore core() { return ShinobiCore.get(); }

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
        String r = corpsIndisponible(id);
        if (r != null) return r;
        if (etourdi(id)) return "Étourdi !";
        if (enGarde(id)) return "En garde : pas de technique.";
        return null;
    }

    /** Kit de mobilité (ShinobiAbilities), grappin, tyrolienne, course ninja, Voie du Flux. */
    public static String mobilite(UUID id) {
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
