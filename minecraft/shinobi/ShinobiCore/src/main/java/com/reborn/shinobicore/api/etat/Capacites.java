package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Résultat immuable de la matrice (§4.5) pour un instantané : niveau de chaque {@link Capacite}, la raison et la ligne de
 * chaque limite / refus, et les effets physiques agrégés (multiplicateurs de vitesse et de saut, faim tenue).
 */
@Stable
public final class Capacites {

    private static final Capacite[] TOUTES = Capacite.values();

    private final byte[] niveaux;
    private final String[] raisons;
    private final String[] lignes;
    private final double vitesse;
    private final double saut;
    private final boolean faimTenue;

    public Capacites(byte[] niveaux, String[] raisons, String[] lignes, double vitesse, double saut, boolean faimTenue) {
        this.niveaux = Arrays.copyOf(niveaux, TOUTES.length);
        this.raisons = Arrays.copyOf(raisons, TOUTES.length);
        this.lignes = Arrays.copyOf(lignes, TOUTES.length);
        this.vitesse = vitesse;
        this.saut = saut;
        this.faimTenue = faimTenue;
    }

    /** Tout permis (capacités ordinaires), spéciales refusées, aucun effet : le résultat d'aucune ligne. */
    public static Capacites libre() {
        byte[] n = new byte[TOUTES.length];
        for (Capacite c : TOUTES) n[c.ordinal()] = (byte) (c.speciale() ? 0 : 2);
        return new Capacites(n, new String[TOUTES.length], new String[TOUTES.length], 1.0, 1.0, false);
    }

    public int niveau(Capacite c) { return niveaux[c.ordinal()]; }

    public boolean peut(Capacite c) { return niveaux[c.ordinal()] > 0; }

    public Verdict verdict(Capacite c) {
        int n = niveaux[c.ordinal()];
        return n >= 2 ? Verdict.PERMIS : new Verdict(n, raisons[c.ordinal()], lignes[c.ordinal()]);
    }

    /** Raison du refus, ou null si permis (même limité). */
    public String refus(Capacite c) { return niveaux[c.ordinal()] > 0 ? null : raisons[c.ordinal()]; }

    /** Multiplicateur de vitesse agrégé (produit des lignes, avec plancher ; 0 = immobile). */
    public double vitesse() { return vitesse; }

    /** Multiplicateur de saut agrégé (0 = pas de saut). */
    public double saut() { return saut; }

    /** Faim tenue à 6 (pas de sprint vanilla côté client) : douleur, ATA (une seule règle, §7.5). */
    public boolean faimTenue() { return faimTenue; }

    /** Les refus et limites, capacité → raison (pour {@code /etats} et le Poste de garde). */
    public Map<Capacite, String> refus() {
        Map<Capacite, String> m = new EnumMap<>(Capacite.class);
        for (Capacite c : TOUTES) {
            if (c.speciale()) continue;
            if (niveaux[c.ordinal()] < 2) m.put(c, (niveaux[c.ordinal()] == 0 ? "" : "◐ ") + raisons[c.ordinal()]);
        }
        return Collections.unmodifiableMap(m);
    }

    /** Les spéciales accordées. */
    public Map<Capacite, String> accordees() {
        Map<Capacite, String> m = new EnumMap<>(Capacite.class);
        for (Capacite c : TOUTES) if (c.speciale() && niveaux[c.ordinal()] > 0) m.put(c, lignes[c.ordinal()]);
        return Collections.unmodifiableMap(m);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Capacites x && Arrays.equals(niveaux, x.niveaux) && Arrays.equals(raisons, x.raisons)
                && Double.compare(vitesse, x.vitesse) == 0 && Double.compare(saut, x.saut) == 0 && faimTenue == x.faimTenue;
    }

    @Override
    public int hashCode() { return Arrays.hashCode(niveaux) * 31 + Arrays.hashCode(raisons); }
}
