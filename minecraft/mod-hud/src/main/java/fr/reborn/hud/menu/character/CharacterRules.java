package fr.reborn.hud.menu.character;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Règles de création partagées (valeurs envoyées au serveur) : villages, clans par village, couleurs, textes,
 * verrouillage par la candidature whitelist. Source : les tables de {@link CharacterCreateScreen}.
 */
public final class CharacterRules {

    private CharacterRules() {}

    public static String[] villages() { return CharacterCreateScreen.VILLAGES; }

    public static String villageShort(int i) { return CharacterCreateScreen.V_SHORT[i]; }

    public static String villageDesc(int i) { return CharacterCreateScreen.V_DESC[i]; }

    /** Clé d'emblème (konoha, suna, kiri, kumo, iwa, ame) ou {@code null} (Déserteur : pas d'emblème). */
    public static String villageKey(int i) {
        return i >= 0 && i < 6 ? new String[]{"konoha", "suna", "kiri", "kumo", "iwa", "ame"}[i] : null;
    }

    public static int villageIndex(String village) {
        String[] vs = villages();
        for (int i = 0; i < vs.length; i++) if (vs[i].equalsIgnoreCase(village)) return i;
        return -1;
    }

    /** Couleur du chakra d'un village (braises sous le perso dans les scènes). */
    public static int villageColor(int i) {
        return switch (i) {
            case 0 -> 0xF2A548;   // Konoha : feu
            case 1 -> 0xE8C88A;   // Suna : sable
            case 2 -> 0x9ED8EA;   // Kiri : brume
            case 3 -> 0xF4EC8C;   // Kumo : foudre
            case 4 -> 0xD39B6A;   // Iwa : terre
            case 5 -> 0xA9B8DA;   // Ame : pluie
            case 6 -> 0xC85A5A;   // Déserteur
            default -> 0xD9A95E;
        };
    }

    public static String[] clansOf(int village) {
        return village < 0 ? new String[0] : CharacterCreateScreen.VILLAGE_CLANS[village];
    }

    public static int clanColor(String clan) {
        String[] all = CharacterCreateScreen.CLANS;
        for (int i = 0; i < all.length; i++) if (all[i].equalsIgnoreCase(clan)) return CharacterCreateScreen.C_COLOR[i];
        return 0xFF6A6A6A;
    }

    public static String clanDesc(String clan) {
        String[] all = CharacterCreateScreen.CLANS;
        for (int i = 0; i < all.length; i++) if (all[i].equalsIgnoreCase(clan)) return CharacterCreateScreen.C_DESC[i];
        return "";
    }

    /**
     * Nom d'un clan de la liste (« Autre » exclu), casse et accents ignorés : même règle que le serveur
     * ({@code ReglesCreation.clanDeSession}), pour que le joueur voie le refus avant l'envoi.
     */
    public static boolean isPredefinedClan(String s) {
        if (s == null) return false;
        String k = cleNom(s);
        if (k.isEmpty()) return false;
        for (String c : CharacterCreateScreen.CLANS) if (!c.equals("Autre") && cleNom(c).equals(k)) return true;
        return false;
    }

    /** Clé de comparaison d'un nom : sans accents ni casse (comme le serveur et l'API). */
    private static String cleNom(String s) {
        return Normalizer.normalize(s.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Village verrouillé par la candidature : premier personnage seulement (CANDIDATURE_ET_DOUANE.md §4.2) ; le staff
     * n'est jamais verrouillé ; un village de candidature hors liste n'impose rien.
     */
    public static boolean villageLocked(String v) {
        return villageCandidature() >= 0 && !v.equalsIgnoreCase(CharacterData.candidatureVillage());
    }

    /** Index du village imposé par la candidature (premier personnage), ou -1. */
    public static int villageCandidature() {
        String cand = CharacterData.candidatureVillage();
        if (cand == null || !CharacterData.candidaturePremier()) return -1;
        return villageIndex(cand);
    }

    /** Âge imposé par la candidature (premier personnage, entre 10 et 60), ou -1. */
    public static int ageCandidature() {
        Integer a = CharacterData.candidatureAge();
        if (a == null || !CharacterData.candidaturePremier() || a < 10 || a > 60) return -1;
        return a;
    }

    /**
     * Décision du 24/10 (Q4) : tous les clans de la liste se rejoignent en <b>session de clan</b>. Hors staff, seul
     * « Autre » (« Sans clan » + nom de famille) reste ouvert à la création ; le serveur refuse le reste.
     */
    public static boolean clanLocked(String c) {
        if ("Autre".equals(c)) return false;
        return !CharacterData.staffExempt();
    }

    /** Libellé du clan « Autre » : « Sans clan » hors staff (nom de famille libre), « Autre » pour le staff (clan libre). */
    public static String libelleAutre() { return CharacterData.staffExempt() ? "Autre" : "Sans clan"; }

    public static String slug(String s) { return CharacterCreateScreen.slug(s == null ? "" : s).toLowerCase(Locale.ROOT); }
}
