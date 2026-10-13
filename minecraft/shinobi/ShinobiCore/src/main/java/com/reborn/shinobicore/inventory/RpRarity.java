package com.reborn.shinobicore.inventory;

import java.util.Locale;

/**
 * Rareté d'un objet RP — cinq niveaux (décision du 2026-10-13), alignés sur les rangs des parchemins (D → S) et sur
 * l'enum client (mod-hud {@code ground/Rarete}). La couleur sert à l'aura au sol, au liseré des cases et au nom dans
 * les notifications. Résolution : {@link RareteItems#of}.
 */
public enum RpRarity {
    COMMUN("Commun", "CFC8B8", 'D'),
    PEU_COMMUN("Peu commun", "6FBF8E", 'C'),
    RARE("Rare", "5E95E0", 'B'),
    EPIQUE("Épique", "A97BE0", 'A'),
    LEGENDAIRE("Légendaire", "E9BC5C", 'S');

    /** Nom affiché (FR). */
    public final String label;
    /** Couleur RGB hexadécimale (sans #). */
    public final String hex;
    /** Rang de parchemin équivalent. */
    public final char rang;

    RpRarity(String label, String hex, char rang) {
        this.label = label;
        this.hex = hex;
        this.rang = rang;
    }

    /** Nom d'enum tolérant (casse, accents usuels, « peu commun ») ; {@code null} si inconnu. */
    public static RpRarity parse(String s) {
        if (s == null || s.isBlank()) return null;
        String n = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_')
            .replace('É', 'E').replace('È', 'E');
        try {
            return valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Rang de parchemin (D, C, B, A, S ; E compte comme D) → rareté ; {@code null} si inconnu. */
    public static RpRarity fromRang(char r) {
        return switch (Character.toUpperCase(r)) {
            case 'D', 'E' -> COMMUN;
            case 'C' -> PEU_COMMUN;
            case 'B' -> RARE;
            case 'A' -> EPIQUE;
            case 'S' -> LEGENDAIRE;
            default -> null;
        };
    }

    public boolean auMoins(RpRarity o) {
        return o == null || ordinal() >= o.ordinal();
    }
}
