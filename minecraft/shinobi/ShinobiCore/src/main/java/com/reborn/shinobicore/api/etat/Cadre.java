package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/** Couche Cadre (§4.2) : les règles du lieu ou de la situation. L'ordre est la priorité (plus grand = gagne). */
@Stable
public enum Cadre {
    MONDE,
    /** Terrain d'entraînement : défaite sans séquelle. */
    ENTRAINEMENT,
    /** Duel consenti (compte à rebours, combat, grâce) : défaite, PV d'avant rendus. */
    DUEL,
    /** Détenu (peine en temps réel, zone de la prison) : KO normal, pas de technique. */
    PRISON,
    /** Mode construction du staff : invulnérable, aucune action de jeu. */
    CONSTRUCTION,
    /** Pas de personnage actif : sélection, création, chargement, cinématique. */
    HORS_JEU,
    /** Mode staff : invisible, vol, aucune mécanique RP. */
    STAFF;

    public int priorite() { return ordinal(); }
}
