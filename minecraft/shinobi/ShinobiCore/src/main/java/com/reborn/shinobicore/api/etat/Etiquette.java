package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Étiquettes d'une attaque (§6.2), confrontées aux défenses de la cible : c'est le « contourne X ». */
@Stable
public enum Etiquette {
    /* vecteur */
    MELEE, ARME, PROJECTILE, TECHNIQUE, ZONE, ENVIRONNEMENT, INTERNE,
    /* nature */
    KATON, SUITON, FUTON, DOTON, RAITON, YIN, YANG,
    /* propriétés */
    PHYSIQUE, CHAKRA, SENJUTSU, ANTI_CHAKRA, SCELLEMENT, MENTAL, PERCANT, IMPARABLE;

    public static Etiquette lire(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Lecture d'une liste ({@code [RAITON, CHAKRA]}), les inconnues ignorées. */
    public static Set<Etiquette> lireTout(Iterable<?> l) {
        Set<Etiquette> out = EnumSet.noneOf(Etiquette.class);
        if (l == null) return out;
        for (Object o : l) {
            Etiquette e = lire(o == null ? null : String.valueOf(o));
            if (e != null) out.add(e);
        }
        return out;
    }
}
