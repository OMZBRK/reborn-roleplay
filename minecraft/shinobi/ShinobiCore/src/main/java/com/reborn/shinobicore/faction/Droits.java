package com.reborn.shinobicore.faction;

import com.reborn.shinobicore.character.Rank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les droits du système Faction (docs/gameplay/FACTION.md §6) : trois familles qui ne se mélangent pas.
 * <ul>
 *   <li><b>Seuils du village</b> : à partir de quel grade du village ({@link Rank}, {@code /grade}) on a un droit ; réglés
 *       par le chef de faction ;</li>
 *   <li><b>Droits de grade de section</b> : cases cochées par le chef de section sur chacun de ses grades ;</li>
 *   <li><b>Droits de charge</b> : cases d'une charge du village (Conseiller, Intendant…), créée par le chef de faction.</li>
 * </ul>
 * Aucun droit ne donne de stat. Les modèles de section (ANBU, Police…) préremplissent insigne, grades et case « cachée ».
 */
public final class Droits {

    private Droits() {}

    /* ================================================================ seuils du village */

    /** {id, libellé, seuil par défaut}. La cloche et les volières s'y brancheront (CLOCHE_ET_MISSIVES.md). */
    public static final Object[][] SEUILS = {
            {"voir_sections", "Voir les sections", Rank.CHUNIN},
            {"cloche_village", "Sonner la cloche du village", Rank.JONIN},
            {"voliere_palais", "Écrire à la volière du palais", Rank.CHUNIN},
            {"alertes_palais", "Recevoir les alertes du palais", Rank.JONIN},
            {"prendre_voliere", "Prendre à une volière", Rank.GENIN}};

    public static Map<String, Rank> seuilsParDefaut() {
        Map<String, Rank> m = new LinkedHashMap<>();
        for (Object[] s : SEUILS) m.put((String) s[0], (Rank) s[2]);
        return m;
    }

    public static boolean seuilConnu(String id) {
        for (Object[] s : SEUILS) if (s[0].equals(id)) return true;
        return false;
    }

    /* ================================================================ grades de section */

    public static final String INVITER = "inviter", INVITER_DISTANCE = "inviter_distance", EXCLURE = "exclure",
            MONTER = "monter", VOIR_CAISSE = "voir_caisse", DEPENSER = "depenser", COFFRE_VOIR = "coffre_voir",
            COFFRE_RETIRER = "coffre_retirer", CLOCHE_SECTION = "cloche_section", JOURNAL = "journal";

    /** {id, libellé} dans l'ordre de la maquette 04. */
    public static final String[][] SECTION = {
            {INVITER, "Inviter face à face et depuis le panneau"},
            {INVITER_DISTANCE, "Inviter à distance (en ligne)"},
            {EXCLURE, "Exclure (grades inférieurs)"},
            {MONTER, "Monter et descendre (sous son grade)"},
            {VOIR_CAISSE, "Voir la caisse en chiffres"},
            {DEPENSER, "Dépenser la caisse (guichet)"},
            {COFFRE_VOIR, "Coffres de la section : voir, déposer"},
            {COFFRE_RETIRER, "Coffres de la section : retirer"},
            {CLOCHE_SECTION, "Annoncer à la section"},
            {JOURNAL, "Lire le journal de la section"}};

    public static Set<String> tousSection() {
        Set<String> s = new LinkedHashSet<>();
        for (String[] d : SECTION) s.add(d[0]);
        return s;
    }

    public static boolean droitSection(String id) {
        for (String[] d : SECTION) if (d[0].equals(id)) return true;
        return false;
    }

    /* ================================================================ charges du village */

    public static final String C_SECTIONS = "sections", C_TAXE = "taxe", C_CAISSES = "caisses", C_JOURNAL = "journal",
            C_ANNONCES = "annonces", C_DOTER = "doter", C_ALERTES = "alertes_palais", C_ORGA = "inviter_org";

    public static final String[][] CHARGE = {
            {C_SECTIONS, "Créer et cacher des sections, nommer leurs chefs"},
            {C_TAXE, "Régler la taxe et les seuils"},
            {C_CAISSES, "Voir les caisses des sections"},
            {C_DOTER, "Doter une section depuis le trésor (guichet)"},
            {C_JOURNAL, "Lire le journal de la faction"},
            {C_ANNONCES, "Annoncer au village"},
            {C_ALERTES, "Recevoir les alertes du palais"},
            {C_ORGA, "Inviter dans l'organisation"}};

    public static boolean droitCharge(String id) {
        for (String[] d : CHARGE) if (d[0].equals(id)) return true;
        return false;
    }

    /* ================================================================ insignes et modèles */

    /** Insignes dessinés au trait (atlas {@code faction/insignes.png} du mod), sans écriture. */
    public static final String[] INSIGNES = {"anbu", "police", "medical", "academie", "missions", "garde", "conseil",
            "bandeau", "lune"};

    public static String insigne(String s) {
        for (String i : INSIGNES) if (i.equals(s)) return i;
        return "missions";
    }

    /** Emblèmes d'organisation (les villages ont leur emblème réel). */
    public static final String[] EMBLEMES_ORGA = {"lune", "garde", "missions", "conseil", "bandeau"};

    /** Un modèle de section : nom, insigne, cachée, grades (du chef au dernier) et droits par grade. */
    public record Modele(String id, String nom, String insigne, boolean cachee, String[] grades, List<Set<String>> droits) {}

    private static Set<String> set(String... s) { return new LinkedHashSet<>(List.of(s)); }

    /** Droits de départ : le chef a tout, le second presque tout, puis de moins en moins. */
    private static List<Set<String>> droitsDepart(int n) {
        List<Set<String>> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (i == 0) l.add(tousSection());
            else if (i == 1 && n > 2) l.add(set(INVITER, EXCLURE, MONTER, VOIR_CAISSE, COFFRE_VOIR, CLOCHE_SECTION, JOURNAL));
            else if (i < n - 1) l.add(set(COFFRE_VOIR, JOURNAL));
            else l.add(set());
        }
        return l;
    }

    private static Modele m(String id, String nom, String insigne, boolean cachee, String... grades) {
        return new Modele(id, nom, insigne, cachee, grades, droitsDepart(grades.length));
    }

    public static final Modele[] MODELES = {
            m("anbu", "ANBU", "anbu", true, "Capitaine", "Chef d'escouade", "Agent", "Recrue"),
            m("police", "Police", "police", false, "Commissaire", "Inspecteur", "Agent", "Recrue"),
            m("medical", "Corps médical", "medical", false, "Médecin-chef", "Médecin", "Infirmier", "Recrue"),
            m("academie", "Académie", "academie", false, "Directeur", "Professeur", "Assistant"),
            m("missions", "Bureau des missions", "missions", false, "Responsable", "Agent de mission", "Commis"),
            m("garde", "Garde des portes", "garde", false, "Capitaine", "Garde", "Recrue")};

    /** Modèle « libre » : deux grades, le chef et les membres. */
    public static Modele libre(String nom, String insigne, boolean cachee) {
        return new Modele("libre", nom, insigne(insigne), cachee, new String[]{"Chef", "Membre"}, droitsDepart(2));
    }

    public static Modele modele(String id) {
        for (Modele m : MODELES) if (m.id().equals(id)) return m;
        return null;
    }

    /* ================================================================ grades du village */

    public static Rank rang(String s) {
        if (s == null || s.isBlank()) return null;
        String n = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        for (Rank r : Rank.values()) if (r.name().equals(n)) return r;
        return null;
    }
}
