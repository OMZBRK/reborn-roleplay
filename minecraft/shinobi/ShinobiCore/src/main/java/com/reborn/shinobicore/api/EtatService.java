package com.reborn.shinobicore.api;

import com.reborn.shinobicore.api.etat.Capacite;
import com.reborn.shinobicore.api.etat.Couche;
import com.reborn.shinobicore.api.etat.Etat;
import com.reborn.shinobicore.api.etat.Etiquette;
import com.reborn.shinobicore.api.etat.Famille;
import com.reborn.shinobicore.api.etat.Lien;
import com.reborn.shinobicore.api.etat.ModificateurSpec;
import com.reborn.shinobicore.api.etat.Resultat;
import com.reborn.shinobicore.api.etat.Source;
import com.reborn.shinobicore.api.etat.Verdict;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * <b>Le service des états du joueur</b> (docs/technique/ETATS_JOUEUR.md §8) : chaque personnage a, à tout instant, un
 * état tenu par le serveur seul, que tous les systèmes interrogent avant d'agir par une seule question — « peut-il
 * faire X ? » — dont la réponse vient d'une matrice unique ({@code etats.yml}).
 *
 * <p>Trois couches (Vital, Contrôle, Cadre), un état principal résolu, des modificateurs cumulables, des capacités
 * calculées. Lecture : {@link #etat}, {@link #peut}, {@link #verifier}, {@link #peutCibler}. Écriture : {@link #transition}
 * (avec veto possible par {@code EtatChangeEvent}), {@link #poser} / {@link #retirer} / {@link #dissiper} (registre des
 * modificateurs), {@link #facteur} (vitesse / saut d'un add-on), {@link #occuper} (actions longues interrompues par un
 * coup, un état, un mouvement).
 *
 * <p>Résolu par le {@code ServicesManager} ; tout tourne sur le thread principal. Un add-on garde un repli sur l'ancien
 * test quand le service est absent ({@link #get} renvoie null).
 */
@Stable
public interface EtatService {

    /* ================================================================ lecture */

    /** Instantané immuable de l'état du joueur (calculé à la demande). */
    Etat etat(UUID compte);

    default Etat etat(Player p) { return etat(p.getUniqueId()); }

    default boolean peut(UUID compte, Capacite c) { return etat(compte).peut(c); }

    default boolean peut(Player p, Capacite c) { return peut(p.getUniqueId(), c); }

    default Verdict verifier(UUID compte, Capacite c) { return etat(compte).verdict(c); }

    default Verdict verifier(Player p, Capacite c) { return verifier(p.getUniqueId(), c); }

    /**
     * Vérification paramétrée : {@code TECHNIQUE} + {@code Boolean mudras} (une technique sans mudra passe une entrave),
     * {@code MOBILITE} + nom du mouvement… Sans règle propre au paramètre, identique à {@link #verifier(UUID, Capacite)}.
     */
    Verdict verifier(UUID compte, Capacite c, Object param);

    /** Raison du refus (texte prêt à afficher) ou null si permis. */
    default String refus(UUID compte, Capacite c) { return verifier(compte, c).refus(); }

    /**
     * La source peut-elle viser cette cible (effets de zone, soins, révélation, onde au sol) ? Faux pour un staff en mode
     * staff, un invisible, un mort en scène, une cinématique, une sélection de personnage, un monde intérieur, un faux
     * corps / support d'armure, un créatif / spectateur.
     */
    boolean peutCibler(Entity source, Entity cible, Set<Etiquette> etiquettes);

    /* ================================================================ écriture */

    /**
     * Demande une transition de couche. Couche Contrôle : {@code ENTRAVE}, {@code CONTROLE}, {@code CORPS_VIDE},
     * {@code ETOURDI}, {@code GARDE} sont <b>tenues par le service</b> (avec lien et durée) ; {@code LIBRE} les lève.
     * Couches Vital et Cadre : la chaîne KO, les duels, les zones restent les détenteurs — l'appel <b>annonce</b> le
     * changement (veto possible par {@code EtatChangeEvent}, ex. le bijū qui sauve son hôte) avant qu'ils l'appliquent.
     * Variante d'une valeur : {@code "CORPS_VIDE.MONDE_INTERIEUR"} ; lever <b>seulement</b> sa propre valeur :
     * {@code "-GARDE"} (ne touche pas un étourdi posé par un autre système).
     *
     * @param dureeMs durée d'une valeur tenue ({@code ≤ 0} = jusqu'à {@code LIBRE} ou la chute du lien)
     */
    Resultat transition(Player p, Couche couche, String valeur, Source source, Lien lien, long dureeMs);

    default Resultat transition(Player p, Couche couche, String valeur, Source source, Lien lien) {
        return transition(p, couche, valeur, source, lien, 0);
    }

    /** Pose un modificateur du registre ({@code modificateurs.types}) : immunités, empilement, journal. */
    boolean poser(Player p, ModificateurSpec spec, Source source);

    void retirer(Player p, String id, Source source);

    /** Retire jusqu'à {@code puissance} modificateurs hostiles de la famille (Kai, antidote, sceau). Renvoie le nombre. */
    int dissiper(Player p, Famille famille, int puissance, Source source);

    /**
     * Multiplicateurs physiques d'un add-on (vitesse, saut) composés par l'applicateur unique (§7.5) avec ceux des
     * états : manteau du bijū, mode, pilule… {@code 1, 1} retire le facteur.
     */
    void facteur(Player p, String cle, double vitesse, double saut);

    /**
     * Vrai si le service pose lui-même vitesse, saut, faim tenue et invulnérabilité (applicateur unique, étape 5) : un
     * add-on qui figeait un joueur par un modificateur d'attribut (étourdi, garde, monde intérieur) s'en abstient et
     * annonce seulement l'état. Faux = repli, l'add-on garde son ancien effet.
     */
    default boolean appliqueEffetsPhysiques() { return false; }

    /* ================================================================ actions longues */

    /** Ce qui interrompt une action longue (§4.4 {@code fin_si}). */
    enum FinSi {
        /** S'éloigne de plus que la tolérance (blocs) de son point de départ. */
        BOUGE,
        /** Reçoit un coup (dégât d'une entité). */
        FRAPPE,
        /** Le vital quitte VALIDE / DOULEUR (à terre, inconscient, mort). */
        KO,
        /** Entre en douleur. */
        DOULEUR,
        /** La couche Contrôle quitte LIBRE (étourdi, entravé, porté…). */
        CONTROLE,
        /** La couche Cadre change (duel, prison, mode staff…). */
        CADRE,
        /** La cible (s'il y en a une) s'éloigne de plus que la tolérance, part ou change d'état. */
        CIBLE
    }

    /** Une action longue en cours ({@code OCCUPE(action)}). */
    interface Occupation {
        String action();

        /** Encore en cours ? */
        boolean active();

        /** Fin normale (sans appel de l'interruption). */
        void terminer();
    }

    /**
     * Commence une action longue : le joueur est {@code OCCUPE(action)} (pas de technique, ni de coup, ni de course…)
     * jusqu'à {@link Occupation#terminer} ; le service l'<b>interrompt</b> (appel de {@code surInterruption} avec la
     * raison, une seule fois) dès qu'une condition {@code finSi} est vraie, ainsi qu'à la déconnexion et au changement
     * de personnage. Point d'accroche des fouilles, vols, séances, soins tenus — et du futur crochetage / casse de porte.
     *
     * @param cible           l'autre personne de l'action (fouillé, soigné), ou null
     * @param toleranceBlocs  distance tolérée pour {@link FinSi#BOUGE} et {@link FinSi#CIBLE}
     * @return l'occupation, ou null si refusée (déjà occupé, ou {@code OCCUPE} refusé par l'état : la raison est envoyée
     *         à {@code surInterruption})
     */
    Occupation occuper(Player p, String action, Entity cible, double toleranceBlocs, Set<FinSi> finSi,
                       Consumer<String> surInterruption);

    /** L'action longue en cours du joueur, ou null. */
    Occupation occupation(UUID compte);

    /* ================================================================ prison */

    /** Peine de prison d'un personnage (temps réel). */
    record Peine(UUID personnage, long finEpochMs, String zone, String motif, String par) {
        public long resteMs() { return Math.max(0, finEpochMs - System.currentTimeMillis()); }
    }

    /** Emprisonne le personnage actif du joueur (cadre {@code PRISON}) pour {@code dureeMs} (temps réel). */
    boolean emprisonner(Player p, long dureeMs, String zone, String motif, Source source);

    /** Libère (fin de peine anticipée). */
    boolean liberer(UUID compte, Source source);

    /** Peine en cours du personnage actif, ou null. */
    Peine peine(UUID compte);

    /* ================================================================ accès */

    /** Le service, ou null (ShinobiCore absent ou trop ancien). */
    static EtatService get() {
        try {
            return Bukkit.getServicesManager().load(EtatService.class);
        } catch (Throwable t) {
            return null;
        }
    }
}
