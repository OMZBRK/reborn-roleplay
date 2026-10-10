package fr.reborn.hud.menu.character;

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

    public static boolean isPredefinedClan(String s) {
        if (s == null) return false;
        for (String c : CharacterCreateScreen.CLANS) if (!c.equals("Autre") && c.equalsIgnoreCase(s.trim())) return true;
        return false;
    }

    /** Village verrouillé par la candidature (le staff n'est jamais verrouillé). */
    public static boolean villageLocked(String v) {
        String cand = CharacterData.candidatureVillage();
        if (cand == null || CharacterData.staffExempt()) return false;
        return !v.equalsIgnoreCase(cand);
    }

    public static boolean clanLocked(String c) {
        if ("Autre".equals(c)) return false;
        String cand = CharacterData.candidatureClan();
        if (cand == null || CharacterData.staffExempt()) return false;
        return !c.equalsIgnoreCase(cand);
    }

    public static String slug(String s) { return CharacterCreateScreen.slug(s == null ? "" : s).toLowerCase(Locale.ROOT); }
}
