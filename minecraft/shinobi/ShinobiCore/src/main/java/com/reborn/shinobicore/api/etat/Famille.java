package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.Locale;

/** Famille d'un modificateur (§4.4) : pour dissiper, résister, afficher. */
@Stable
public enum Famille {
    CORPS, CHAKRA, ESPRIT, MOBILITE, DEFENSE, MODE, SOCIAL, DISCRETION, STAFF;

    public static Famille lire(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
