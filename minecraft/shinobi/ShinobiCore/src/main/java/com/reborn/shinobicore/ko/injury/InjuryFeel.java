package com.reborn.shinobicore.ko.injury;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Vue <b>ressentie</b> des blessures (fiche shinobi, onglet État) : ce que le
 * personnage sent, sans origine ni délai, avec l'accord en genre
 * (« Hématome, fort », « Brûlure, légère »). Les blessures cachées
 * ({@link Injury#hidden()}) ne sont jamais montrées.
 */
public final class InjuryFeel {

    private InjuryFeel() {}

    /** Les 8 zones du modèle côté mod. */
    public enum Zone {
        TETE("TETE"), COU("COU"), TORSE("TORSE"), VENTRE("VENTRE"),
        BRAS_G("BRAS GAUCHE"), BRAS_D("BRAS DROIT"), JAMBE_G("JAMBE GAUCHE"), JAMBE_D("JAMBE DROITE");

        private final String label;
        Zone(String label) { this.label = label; }

        /** Libellé ArcadePix : majuscules sans accents. */
        public String label() { return label; }
    }

    /** Une zone ressentie : la pire blessure visible de la zone. */
    public record Felt(Zone zone, Injury worst, String txt, boolean forte) {}

    /** Nom de la sensation (le libellé du type). */
    public static String sensation(InjuryType t) {
        return t == null ? "Douleur" : t.label();
    }

    /** Hématome et Os cassé sont masculins ; Brûlure, Plaie, Infection féminins. */
    static boolean feminin(InjuryType t) {
        return t == InjuryType.BRULURE || t == InjuryType.PLAIE || t == InjuryType.INFECTION;
    }

    /** Intensité accordée : léger/légère, fort/forte, profond/profonde, insupportable. */
    public static String intensite(Severity s, boolean feminin) {
        if (s == null) s = Severity.FAIBLE;
        return switch (s) {
            case FAIBLE -> feminin ? "légère" : "léger";
            case MOYEN -> feminin ? "forte" : "fort";
            case IMPORTANT -> feminin ? "profonde" : "profond";
            case URGENT -> "insupportable";
        };
    }

    /** « Hématome, fort ». */
    public static String feel(Injury i) {
        return sensation(i.type()) + ", " + intensite(i.severity(), feminin(i.type()));
    }

    /** Sévérité MOYEN ou plus. */
    public static boolean forte(Injury i) {
        return i.severity() != null && i.severity().ordinal() >= Severity.MOYEN.ordinal();
    }

    public static Zone zoneOf(BodyPart p) {
        if (p == null) return Zone.TORSE;
        return switch (p) {
            case TETE_GAUCHE, TETE_DROIT -> Zone.TETE;
            case COU_GAUCHE, COU_DROIT -> Zone.COU;
            case BUSTE_GAUCHE, BUSTE_DROIT, EPAULE_GAUCHE, EPAULE_DROITE -> Zone.TORSE;
            case VENTRE_GAUCHE, VENTRE_DROIT -> Zone.VENTRE;
            case BRAS_GAUCHE -> Zone.BRAS_G;
            case BRAS_DROIT -> Zone.BRAS_D;
            case JAMBE_GAUCHE, PIED_GAUCHE -> Zone.JAMBE_G;
            case JAMBE_DROITE, PIED_DROIT -> Zone.JAMBE_D;
        };
    }

    /**
     * Blessures visibles regroupées par zone (la pire de chaque zone), la zone
     * la plus touchée en premier ; 8 au plus (une par zone).
     */
    public static List<Felt> grouped(List<Injury> injuries) {
        Map<Zone, Injury> worst = new EnumMap<>(Zone.class);
        if (injuries != null) {
            for (Injury i : injuries) {
                if (i == null || i.hidden()) continue;
                Zone z = zoneOf(i.bodyPart());
                Injury cur = worst.get(z);
                if (cur == null || rank(i) > rank(cur)) worst.put(z, i);
            }
        }
        List<Felt> out = new ArrayList<>();
        for (Map.Entry<Zone, Injury> e : worst.entrySet()) {
            out.add(new Felt(e.getKey(), e.getValue(), feel(e.getValue()), forte(e.getValue())));
        }
        // La pire d'abord ; à égalité, l'ordre des zones (tête → jambes).
        out.sort(Comparator.comparingInt((Felt f) -> -rank(f.worst())).thenComparingInt(f -> f.zone().ordinal()));
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    private static int rank(Injury i) {
        return i.severity() == null ? 0 : i.severity().ordinal();
    }
}
