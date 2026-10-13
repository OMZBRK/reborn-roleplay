package com.reborn.shinobicore.medic.yeux;

import java.util.Locale;

/** Côté d'un œil. {@link #code} est la forme courte du canal {@code reborn:yeux} ({@code g}/{@code d}). */
public enum Cote {
    GAUCHE("g", "gauche"),
    DROIT("d", "droit");

    public final String code;
    public final String nom;

    Cote(String code, String nom) {
        this.code = code;
        this.nom = nom;
    }

    /** {@code gauche}, {@code g}, {@code left}… ; {@code null} si inconnu. */
    public static Cote parse(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "g", "gauche", "left", "l" -> GAUCHE;
            case "d", "droit", "droite", "right", "r" -> DROIT;
            default -> null;
        };
    }
}
