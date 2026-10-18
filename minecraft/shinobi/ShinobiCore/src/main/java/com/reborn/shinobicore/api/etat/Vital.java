package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/** Couche Vital (§4.2) : l'état du corps. Source unique : la chaîne KO ({@code KoManager} + {@code DouleurManager}). */
@Stable
public enum Vital {
    /** Debout, au-dessus du seuil de douleur. */
    VALIDE,
    /** Debout, PV ≤ 5 % hors terrain / duel / construction. */
    DOULEUR,
    /** Agonie (45 s), on rampe. */
    A_TERRE,
    /** KO, 5 min puis {@code /hopital}. */
    INCONSCIENT,
    /** Mort RP (scène de mort). */
    MORT
}
