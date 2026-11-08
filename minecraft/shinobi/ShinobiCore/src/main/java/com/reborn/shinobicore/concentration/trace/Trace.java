package com.reborn.shinobicore.concentration.trace;

import java.util.UUID;

/** Un pas posé (mémoire seule). {@link #marcheur} ne quitte jamais le serveur. */
final class Trace {

    final int id;
    final UUID marcheur;
    final String monde;
    final double x, y, z;
    /** Angle du pied (degrés, 0 à 359), drapeaux ({@code TraceRegles.F_*}), échelle (pour cent). */
    final int angle, drapeaux, k;
    final long nee;
    /** Durée de vie (secondes). */
    final int vie;
    boolean retiree;
    /** Voisins dans l'ordre d'arrivée ({@link TraceStore}). */
    Trace avant, apres;

    Trace(int id, UUID marcheur, String monde, double x, double y, double z, int angle, int drapeaux, int k, long nee, int vie) {
        this.id = id;
        this.marcheur = marcheur;
        this.monde = monde;
        this.x = x;
        this.y = y;
        this.z = z;
        this.angle = angle;
        this.drapeaux = drapeaux;
        this.k = k;
        this.nee = nee;
        this.vie = vie;
    }

    boolean expiree(long now) { return now - nee >= vie * 1000L; }

    int ageSecondes(long now) { return (int) Math.max(0L, (now - nee) / 1000L); }
}
