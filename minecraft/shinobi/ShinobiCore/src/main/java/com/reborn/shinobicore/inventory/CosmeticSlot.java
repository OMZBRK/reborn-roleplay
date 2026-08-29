package com.reborn.shinobicore.inventory;

/** Emplacements d'équipement cosmétique 3D — alignés sur l'enum client. */
public enum CosmeticSlot {
    // Legacy (référencés par des RpItemType existants — ne pas retirer).
    BANDEAU,
    MASQUE,
    MANTEAU,
    DOS,
    // Nouveaux emplacements (autour du perso). Additif : ne casse pas la persistance.
    CHAPEAU,
    BOUCLE,
    HAUT,
    TENUE,
    CEINTURE,
    BAS;

    public static CosmeticSlot fromName(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return CosmeticSlot.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
