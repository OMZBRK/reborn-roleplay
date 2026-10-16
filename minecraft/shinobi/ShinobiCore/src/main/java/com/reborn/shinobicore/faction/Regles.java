package com.reborn.shinobicore.faction;

import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;

/**
 * Règles pures du système Faction (sans Bukkit) : qui voit quoi (FACTION.md §4) et la taxe (§8). Tout le filtrage
 * part d'ici côté serveur : rien n'est envoyé au client qu'il n'a pas le droit de voir.
 */
public final class Regles {

    private Regles() {}

    /** Ce que le lecteur est dans sa faction (un seul profil, le plus haut). */
    public enum Profil { DEHORS, JOUEUR, MEMBRE, GRADE, CHEF_SECTION, CHEF_FACTION }

    /** Le lecteur, résumé : sa faction, sa section, son grade du village, ses rôles. */
    public record Lecteur(String faction, String section, Rank rang, boolean chefFaction, boolean chefSection,
                          boolean chargeSections, boolean chargeCaisses) {}

    public static Profil profil(Lecteur l, Faction f) {
        if (l == null || f == null || !f.tag.equals(l.faction())) return Profil.DEHORS;
        if (l.chefFaction()) return Profil.CHEF_FACTION;
        if (l.chefSection()) return Profil.CHEF_SECTION;
        if (voitSections(l, f)) return Profil.GRADE;
        return l.section() == null || l.section().isEmpty() ? Profil.JOUEUR : Profil.MEMBRE;
    }

    /** Onglet Sections : chef de faction, charge « sections », ou grade du village au moins au seuil. */
    public static boolean voitSections(Lecteur l, Faction f) {
        if (l == null || f == null || !f.tag.equals(l.faction())) return false;
        if (l.chefFaction() || l.chargeSections()) return true;
        if (!f.village()) return false;
        Rank seuil = f.seuils.getOrDefault("voir_sections", Rank.CHUNIN);
        return l.rang() != null && l.rang().ordinal() >= seuil.ordinal();
    }

    /** Droit du village par seuil (le chef de faction a tout). */
    public static boolean seuil(Lecteur l, Faction f, String droit) {
        if (l == null || f == null || !f.tag.equals(l.faction())) return false;
        if (l.chefFaction()) return true;
        Rank s = f.seuils.getOrDefault(droit, Droits.defaut(droit));
        return s != null && l.rang() != null && l.rang().ordinal() >= s.ordinal();
    }

    /**
     * Sonner la cloche sans aucun délai (ni par cloche, ni par personne, ni limite « danger ») : chef de faction (le
     * Kage) toujours, sinon grade du village au moins au seuil « Sonner sans délai » (CLOCHE_ET_MISSIVES.md §2.5).
     */
    public static boolean sansDelai(Lecteur l, Faction f) { return seuil(l, f, Droits.SANS_DELAI); }

    /**
     * Une section est-elle lisible (nom, chef, effectif) ? Une section cachée ne l'est que pour ses membres, le chef de
     * faction, une charge « sections » et le staff ; pour les autres gradés c'est un « ? » et rien d'autre.
     */
    public static boolean lisible(Section s, Lecteur l, boolean staff) {
        if (s == null) return false;
        if (staff) return true;
        if (l == null || !s.faction.equals(l.faction())) return false;
        if (!s.cachee) return true;
        return s.id.equals(l.section()) || l.chefFaction() || l.chargeSections();
    }

    /** La caisse d'une section (en chiffres) : son chef, un grade « voir la caisse », le chef de faction, une charge « caisses ». */
    public static boolean voitCaisse(Section s, Lecteur l, boolean droitGrade) {
        if (s == null || l == null || !s.faction.equals(l.faction())) return false;
        if (l.chefFaction() || l.chargeCaisses()) return true;
        return s.id.equals(l.section()) && (l.chefSection() || droitGrade);
    }

    /** Le chef de section voit tous les noms de sa section (son registre), même ceux qu'il n'a pas rencontrés. */
    public static boolean registre(Section s, Lecteur l) {
        return s != null && l != null && s.id.equals(l.section()) && l.chefSection();
    }

    /**
     * Le nom sous lequel le lecteur voit {@code m} : le sien, celui qu'il a appris, le vrai nom si c'est une figure
     * publique (chef de faction) ou s'il tient le registre ; sinon {@code null} (inconnu : compté, jamais nommé).
     */
    public static String nom(Membre m, java.util.UUID moi, String appris, boolean publique, boolean registre) {
        if (m == null) return null;
        if (m.perso.equals(moi)) return m.nom;
        if (appris != null && !appris.isBlank()) return appris;
        if (publique || registre) return m.nom;
        return null;
    }

    /** « Inconnu » ou « Inconnue » (on sait qu'il existe, pas qui il est). */
    public static String inconnu(Membre m) { return m != null && m.feminin() ? "Inconnue" : "Inconnu"; }

    /* ================================================================ taxe */

    /** Taux borné : 0 au plafond (le plafond lui-même borné à 0..100). */
    public static int borner(int taux, int plafond) {
        int p = Math.max(0, Math.min(100, plafond));
        return Math.max(0, Math.min(p, taux));
    }

    /**
     * Une entrée dans une caisse de section : {@code [net, taxe]}. La taxe est arrondie à l'unité inférieure ; les
     * dotations du village ne sont jamais taxées (FACTION.md §8, Q4).
     */
    public static long[] taxer(long montant, int taux, boolean dotation) {
        if (montant <= 0) return new long[]{Math.max(0, montant), 0};
        if (dotation || taux <= 0) return new long[]{montant, 0};
        long t = montant * Math.min(100, taux) / 100;
        return new long[]{montant - t, t};
    }

    /* ================================================================ grades de section */

    /**
     * Peut-on agir (monter, descendre, exclure) sur un membre de rang {@code rangCible} quand on est au rang
     * {@code rangActeur} ? Seulement strictement sous son propre grade ; le chef de section (rang 0) et le chef de faction
     * peuvent tout sauf toucher au chef.
     */
    public static boolean sous(int rangActeur, int rangCible) { return rangCible > rangActeur; }
}
