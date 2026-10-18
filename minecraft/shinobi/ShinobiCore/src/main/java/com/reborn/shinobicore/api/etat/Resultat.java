package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/** Issue d'une transition demandée au service. */
@Stable
public enum Resultat {
    /** Appliquée (journalisée, événements envoyés). */
    APPLIQUEE,
    /** Déjà dans cette valeur. */
    INCHANGEE,
    /** Refusée par une règle de cohérence (§5.2 : un seul détenteur de la couche Contrôle, vital qui prime…). */
    REFUSEE,
    /** Refusée par un plugin ({@code EtatChangeEvent} annulé : sauvetage du bijū…). */
    VETO;

    public boolean ok() { return this == APPLIQUEE || this == INCHANGEE; }
}
