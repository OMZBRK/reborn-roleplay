package fr.reborn.hud.menu.inventory;

/**
 * Emplacements d'équipement cosmétique (modèles 3D portés sur le corps),
 * disposés <b>autour du personnage</b> dans la Sacoche (colonne gauche + droite).
 * Un objet dont {@code cosmeticSlot != null} peut être glissé sur la case du même
 * type pour l'équiper. L'ordre de déclaration = l'ordre d'affichage (haut→bas,
 * colonne gauche puis droite).
 *
 * <p>{@code side} : 0 = colonne gauche, 1 = colonne droite, 2 = onglet Équipement (étui). {@code icon} = texture
 * de fond du slot vide (les emplacements pas encore illustrés retombent sur
 * {@code cadre_reborn_none}, le cadre « à faire »). Case vide : picto de l'emplacement (sacoche/kanji.png, nom
 * historique ; aucun kanji depuis le 15/10).
 */
public enum CosmeticSlot {
    // ── Colonne GAUCHE (tête → torse) ──
    CHAPEAU("Chapeau", "cadre_reborn_none", 0),
    BANDEAU("Bandeau", "slot_bandeau", 0),
    MASQUE("Masque", "slot_masque", 0),
    BOUCLE("Boucle d'oreille", "cadre_reborn_none", 0),
    MANTEAU("Manteau", "slot_manteau", 0),
    // ── Colonne DROITE (torse → jambes) ──
    HAUT("Haut", "cadre_reborn_none", 1),
    TENUE("Tenue", "cadre_reborn_none", 1),
    CEINTURE("Ceinture", "cadre_reborn_none", 1),
    BAS("Bas", "cadre_reborn_none", 1),
    DOS("Dos", "slot_dos", 1),
    // ── Onglet ÉQUIPEMENT (pas un cosmétique) : l'ÉTUI. L'arme rangée ici se voit à la ceinture (diffusée comme un
    //    cosmétique sur reborn:cosmetics), on la dégaine dans la barre (touche Z) et on la rengaine (13/10). ──
    ARME("Arme", "cadre_reborn_none", 2);

    public final String label;
    public final String icon;
    public final int side;

    CosmeticSlot(String label, String icon, int side) {
        this.label = label;
        this.icon = icon;
        this.side = side;
    }

    /** Emplacement de l'onglet Cosmétique (colonnes gauche / droite) ; l'étui ({@link #ARME}) est dans Équipement. */
    public boolean cosmetique() {
        return side < 2;
    }

    public static CosmeticSlot fromName(String s) {
        if (s == null) return null;
        try {
            return CosmeticSlot.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }
}
