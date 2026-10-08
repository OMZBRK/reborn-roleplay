package com.reborn.shinobicore.character;

import java.util.Locale;

/**
 * Grade shinobi du village, du plus bas au plus haut. Le passage de grade est <b>RP</b> (décision ou examen) ;
 * un joueur gradé ou le staff l'exécute avec {@code /grade}, dans les limites de
 * {@link com.reborn.shinobicore.stats.GradeRules} (docs/GRADES.md).
 *
 * <p>Persisté par nom : {@link #from} relit aussi les anciens noms (ACADEMY, SPECIAL_JONIN, ANBU, SANNIN).
 * Les fonctions RP (ANBU, bras droit du Kage, conseil…) ne sont <b>pas</b> des grades.
 *
 * <p>Effet mécanique : chaque passage donne des points de stats ({@link #statTier()}) et des bonus passifs
 * (PV, chakra, endurance, stats — {@link com.reborn.shinobicore.stats.GradeRules}).
 */
public enum Rank {
    ACADEMICIEN      ("Académicien"),
    GENIN            ("Genin"),
    GENIN_CONFIRME   ("Genin confirmé"),
    CHUNIN           ("Chūnin"),
    KONIN            ("Konin"),
    TOKUBETSU_JONIN  ("Tokubetsu Jōnin"),
    JONIN            ("Jōnin"),
    COMMANDANT_JONIN ("Commandant Jōnin"),
    KAGE             ("Kage");

    private final String displayName;

    Rank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /** Passages de grade accomplis, pour les points de stats : Académicien 0 → Kage 8. */
    public int statTier() { return ordinal(); }

    /** Grade suivant, en revenant au début après le plus haut (outil admin). */
    public Rank cycle() {
        Rank[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Lecture tolérante ; anciens noms convertis, repli sur {@link #GENIN} si inconnu. */
    public static Rank from(String s) {
        if (s == null) return GENIN;
        String n = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_')
                .replace('É', 'E').replace('Ō', 'O').replace('Ū', 'U');
        switch (n) {
            case "ACADEMY", "ACADEMIE", "ACADEMICIEN" -> { return ACADEMICIEN; }
            case "SPECIAL_JONIN", "JONIN_SPECIAL" -> { return TOKUBETSU_JONIN; }
            // ANBU et Sannin ne sont plus des grades : la fonction se rattache au design Faction.
            case "ANBU", "SANNIN" -> { return JONIN; }
            default -> { }
        }
        try {
            return Rank.valueOf(n);
        } catch (IllegalArgumentException ignore) {
            return GENIN;
        }
    }
}
