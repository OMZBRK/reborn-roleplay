package com.reborn.shinobicore.api.etat;

import com.reborn.shinobicore.api.Stable;

/**
 * Les trois questions indépendantes sur un personnage (docs/technique/ETATS_JOUEUR.md §4.1) : son <b>corps</b>
 * ({@link #VITAL}), <b>qui le pilote</b> ({@link #CONTROLE}), <b>les règles du lieu</b> ({@link #CADRE}). Une seule
 * valeur par couche à la fois.
 */
@Stable
public enum Couche { VITAL, CONTROLE, CADRE }
