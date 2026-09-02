package fr.reborn.hud.menu.inventory;

/**
 * Emplacements d'équipement cosmétique (modèles 3D portés sur le corps),
 * disposés <b>autour du personnage</b> dans la Sacoche (colonne gauche + droite).
 * Un objet dont {@code cosmeticSlot != null} peut être glissé sur la case du même
 * type pour l'équiper. L'ordre de déclaration = l'ordre d'affichage (haut→bas,
 * colonne gauche puis droite).
 *
 * <p>{@code side} : 0 = colonne gauche, 1 = colonne droite. {@code icon} = texture
 * de fond du slot vide (les emplacements pas encore illustrés retombent sur
 * {@code cadre_reborn_none}, le cadre « à faire »).
 */
public enum CosmeticSlot {
    // ── Colonne GAUCHE (tête → torse) ──
    CHAPEAU("Chapeau", "笠", "cadre_reborn_none", 0),
    BANDEAU("Bandeau", "額", "slot_bandeau", 0),
    MASQUE("Masque", "面", "slot_masque", 0),
    BOUCLE("Boucle d'oreille", "耳", "cadre_reborn_none", 0),
    MANTEAU("Manteau", "衣", "slot_manteau", 0),
    // ── Colonne DROITE (torse → jambes) ──
    HAUT("Haut", "上", "cadre_reborn_none", 1),
    TENUE("Tenue", "装", "cadre_reborn_none", 1),
    CEINTURE("Ceinture", "帯", "cadre_reborn_none", 1),
    BAS("Bas", "下", "cadre_reborn_none", 1),
    DOS("Dos", "背", "slot_dos", 1);

    public final String label;
    public final String kanji;
    public final String icon;
    public final int side;

    CosmeticSlot(String label, String kanji, String icon, int side) {
        this.label = label;
        this.kanji = kanji;
        this.icon = icon;
        this.side = side;
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
