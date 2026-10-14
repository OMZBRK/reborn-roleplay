package com.reborn.shinobicore.modificateur;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un <b>type</b> de modificateur, lu dans {@code config.yml} ({@code modificateurs.types.<id>}) — docs/technique/
 * ETATS_JOUEUR.md §4.4. Le code ne contient aucune valeur de jeu : tout est ici.
 *
 * <p>Formule d'un effet : {@code "x"} (l'intensité), {@code "1+x"}, {@code "1-x"}, ou un nombre fixe ({@code 0.5}).
 */
public record TypeModificateur(
        String id,
        String nom,
        String famille,
        boolean bonus,
        String icone,
        Empilement empilement,
        double plafond,
        Persistance persistance,
        boolean perduAuKo,
        Visibilite visible,
        boolean journal,
        long dureeMs,
        String valeur,
        Map<String, String> effets,
        List<String> refuse,
        String ensuite) {

    public enum Empilement { REMPLACE_SI_PLUS_FORT, PROLONGE, CUMULE, IGNORE }

    /** À la déconnexion : {@code GELE} reprend où il en était, {@code CONTINUE} court en temps réel, {@code PERDU}. */
    public enum Persistance { GELE, CONTINUE, PERDU }

    /** Qui le voit : le joueur (icône + minuteur), le staff seul, personne. */
    public enum Visibilite { SOI, STAFF, AUCUN }

    static TypeModificateur lire(String id, ConfigurationSection s) {
        Map<String, String> eff = new LinkedHashMap<>();
        ConfigurationSection e = s.getConfigurationSection("effets");
        if (e != null) for (String k : e.getKeys(false)) eff.put(k, String.valueOf(e.get(k)).trim());
        List<String> ref = new ArrayList<>();
        for (String r : s.getStringList("refuse")) ref.add(r.trim().toUpperCase(Locale.ROOT));
        return new TypeModificateur(
                id,
                s.getString("nom", id),
                s.getString("famille", "CORPS").toUpperCase(Locale.ROOT),
                s.getBoolean("bonus", true),
                s.getString("icone", "pilule"),
                enumOr(Empilement.class, s.getString("empilement"), Empilement.REMPLACE_SI_PLUS_FORT),
                s.getDouble("plafond", 0),
                enumOr(Persistance.class, s.getString("persistance"), Persistance.GELE),
                !"GARDE".equalsIgnoreCase(s.getString("au-ko", s.getBoolean("bonus", true) ? "PERDU" : "GARDE")),
                enumOr(Visibilite.class, s.getString("visible"), Visibilite.SOI),
                s.getBoolean("journal", false),
                Math.max(0L, (long) (s.getDouble("duree-secondes", 60) * 1000L)),
                s.getString("valeur", ""),
                eff,
                ref,
                s.getString("ensuite", null));
    }

    private static <E extends Enum<E>> E enumOr(Class<E> c, String v, E def) {
        if (v == null) return def;
        try {
            return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    /** Valeur de l'effet pour cette intensité (1 si l'effet n'est pas porté par ce type). */
    public double effet(String nomEffet, double x) {
        String f = effets.get(nomEffet);
        if (f == null) return 1.0;
        return switch (f.replace(" ", "")) {
            case "x" -> x;
            case "1+x" -> 1.0 + x;
            case "1-x" -> Math.max(0.0, 1.0 - x);
            default -> {
                try { yield Double.parseDouble(f); } catch (NumberFormatException ex) { yield 1.0; }
            }
        };
    }

    /** Valeur « neutre » de l'intensité (sans effet) : 1 pour une formule {@code x}, 0 sinon. Sert à réduire de moitié. */
    public double neutre() {
        for (String f : effets.values()) if ("x".equals(f.replace(" ", ""))) return 1.0;
        return 0.0;
    }

    /** Texte court affiché à côté de l'icône : {@code ×2}, {@code −25 %}, {@code +20 %}… */
    public String valeurAffichee(double x) {
        if (valeur == null || valeur.isEmpty()) return "";
        String xs = fmt(x), ps = fmt(Math.abs(x) * 100.0);
        return valeur.replace("{x}", xs).replace("{p}", ps);
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.FRANCE, "%.2f", v).replaceAll("0+$", "").replaceAll(",$", "");
    }
}
