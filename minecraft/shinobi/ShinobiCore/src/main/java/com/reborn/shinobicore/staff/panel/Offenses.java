package com.reborn.shinobicore.staff.panel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des infractions RP et leur escalade par strikes (Poste de garde).
 * Chaque palier : {@code WARN}, {@code MUTE:<minutes>}, {@code BAN:<minutes>} ou {@code BAN:-1} (définitif).
 * Le palier appliqué = celui du nombre de strikes déjà reçus pour cette infraction (90 derniers jours).
 */
public final class Offenses {

    public record Offense(String id, String name, List<String> ladder) { }

    /** Sanction concrète d'un palier. */
    public record Step(String kind, long minutes) {
        public static Step parse(String s) {
            String[] p = s.split(":");
            return new Step(p[0].toUpperCase(), p.length > 1 ? Long.parseLong(p[1]) : 0L);
        }

        /** Grade minimum pour l'appliquer. */
        public int requiredGrade() {
            return switch (kind) {
                case "WARN" -> StaffGrades.HELPER;
                case "MUTE", "KICK" -> StaffGrades.MODO;
                case "BAN" -> minutes > 0 && minutes <= 1440 ? StaffGrades.MODO : StaffGrades.ADMIN;
                default -> StaffGrades.OWNER;
            };
        }

        public String label() {
            return switch (kind) {
                case "WARN" -> "Avertissement";
                case "MUTE" -> "Mute " + duration(minutes);
                case "KICK" -> "Expulsion";
                case "BAN" -> minutes < 0 ? "Ban définitif" : "Ban " + duration(minutes);
                default -> kind;
            };
        }

        /** Catégorie d'affichage (couleur de pastille côté mod). */
        public String tone() {
            return switch (kind) {
                case "WARN" -> "warn";
                case "MUTE", "KICK" -> "mute";
                default -> "ban";
            };
        }
    }

    public static String duration(long minutes) {
        if (minutes < 60) return minutes + " min";
        if (minutes < 1440) return (minutes / 60) + " h";
        long d = minutes / 1440;
        return d + (d > 1 ? " jours" : " jour");
    }

    public static final Map<String, Offense> ALL = new LinkedHashMap<>();

    static {
        add("hrp", "HRP", "WARN", "MUTE:60", "MUTE:360", "BAN:1440");
        add("metagaming", "Metagaming", "WARN", "MUTE:360", "BAN:1440", "BAN:4320");
        add("powergaming", "Powergaming", "WARN", "BAN:1440", "BAN:4320");
        add("combat_arene", "Combat d'arène", "WARN", "BAN:1440", "BAN:4320", "BAN:10080");
        add("nlr", "NLR non respecté", "WARN", "MUTE:60", "BAN:1440");
        add("fear_pain", "Fear / Pain RP ignoré", "WARN", "MUTE:60", "BAN:1440");
        add("insultes", "Insultes", "MUTE:60", "MUTE:1440", "BAN:1440");
        add("spam", "Spam", "MUTE:15", "MUTE:60", "MUTE:1440");
        add("triche", "Triche", "BAN:43200", "BAN:-1");
    }

    private static void add(String id, String name, String... ladder) {
        ALL.put(id, new Offense(id, name, List.of(ladder)));
    }

    private Offenses() {}

    public static Step stepFor(Offense o, int strikes) {
        return Step.parse(o.ladder().get(Math.min(strikes, o.ladder().size() - 1)));
    }
}
