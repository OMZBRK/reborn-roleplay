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

    /** Clé d'emblème (konoha, suna, kiri, kumo, iwa) ou {@code null} (Ame, Déserteur : pas d'emblème). */
    public static String villageKey(int i) {
        return i >= 0 && i < 5 ? new String[]{"konoha", "suna", "kiri", "kumo", "iwa"}[i] : null;
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
