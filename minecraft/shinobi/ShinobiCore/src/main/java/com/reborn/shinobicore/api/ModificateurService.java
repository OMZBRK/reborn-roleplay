package com.reborn.shinobicore.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * <b>Registre des modificateurs</b> (docs/technique/ETATS_JOUEUR.md §4.4) : un modificateur est un effet daté posé sur
 * le <b>personnage actif</b> d'un joueur par une source (plat, pilule, système, staff). Premier utilisateur : la
 * nourriture (GAMEPLAN §12) ; le futur {@code EtatService} s'appuiera sur ce même registre (pas un second).
 *
 * <p>Les <b>types</b> sont décrits dans la configuration ({@code modificateurs.types.*} : nom, famille, bonus ou malus,
 * icône, empilement, persistance, sort au KO, visibilité, journal, effets). Le code ne connaît que les règles générales :
 * <ul>
 *   <li>deux types différents se cumulent ; le même type suit son empilement (par défaut le plus fort remplace, la durée
 *       repart) ;</li>
 *   <li>un <b>effet</b> est un multiplicateur nommé ({@code regen}, {@code satiete_perte}, {@code endurance_regen},
 *       {@code meditation}…) : les multiplicateurs actifs se multiplient ;</li>
 *   <li>un type peut <b>refuser une capacité</b> ({@code SPRINTER}, {@code COURSE_NINJA}…) : le refus porte sa raison
 *       (le nom du modificateur) ;</li>
 *   <li>des <b>contributeurs</b> (états dérivés, comme la FAIM de la satiété) ajoutent leurs multiplicateurs et refus
 *       sans être stockés.</li>
 * </ul>
 * Résolu via le {@code ServicesManager} de Bukkit. Lecture rapide (aucune allocation) : appelable à chaque tick.
 */
@Stable
public interface ModificateurService {

    /** Produit des multiplicateurs {@code effet} actifs sur le personnage actif du compte (1 si aucun). */
    double multiplicateur(UUID compte, String effet);

    /** Raison du refus de la capacité ({@code "Fatigue"}, {@code "Faim"}…), ou {@code null} si elle est permise. */
    String raison(UUID compte, String capacite);

    default boolean refuse(UUID compte, String capacite) {
        return raison(compte, capacite) != null;
    }

    /**
     * Pose un modificateur sur le personnage actif.
     *
     * @param type      identifiant du type ({@code modificateurs.types.<type>})
     * @param intensite niveau (≥ 0) : la valeur des effets en dépend ({@code x}, {@code 1-x}, {@code 1+x})
     * @param dureeMs   durée ; ≤ 0 = celle du type
     * @param source    qui ou quoi (« Ramen shōyu », « Pilule du soldat », « staff Raiden »…)
     * @return vrai si posé ou renforcé, faux si refusé (type inconnu, plus faible, pas de personnage)
     */
    boolean poser(Player p, String type, double intensite, long dureeMs, String source);

    /** Retire un type du personnage actif (rien si absent). */
    void retirer(Player p, String type, String source);

    /** Modificateurs actifs du personnage actif (copie). */
    List<Vue> actifs(UUID compte);

    /** Vue immuable d'un modificateur actif. */
    record Vue(String type, String nom, String famille, boolean bonus, double intensite, long resteMs, long dureeMs,
               String source) {}

    /** Le service, ou null (ShinobiCore absent). Une recherche par appel : à mettre en cache dans une boucle. */
    static ModificateurService get() {
        return Bukkit.getServicesManager().load(ModificateurService.class);
    }

    /** Raccourci : raison du refus de {@code capacite} pour ce joueur, ou null (permis, ou service absent). */
    static String refusDe(Player p, String capacite) {
        ModificateurService s = get();
        return s == null || p == null ? null : s.raison(p.getUniqueId(), capacite);
    }
}
