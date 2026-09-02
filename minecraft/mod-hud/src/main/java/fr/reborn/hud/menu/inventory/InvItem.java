package fr.reborn.hud.menu.inventory;

/**
 * Objet RP (données pures, PAS un {@code ItemStack} vanilla) : le sac est
 * data-driven façon Zenkai, donc entièrement custom visuellement. Un objet
 * porte son poids unitaire (système de poids), sa catégorie/rareté, un kanji-
 * repère et, s'il est cosmétique, l'emplacement 3D qu'il occupe.
 *
 * @param id           identifiant serveur stable (clé d'action C2S)
 * @param name         nom affiché (FR, police vanilla → accents OK)
 * @param desc         description (les « \n » sont des retours de ligne)
 * @param category     catégorie (onglet-filtre)
 * @param rarity       rareté (couleur du liseré)
 * @param weight       poids unitaire en kg
 * @param count        quantité dans la pile
 * @param glyph        1–2 caractères dessinés sur la case (kanji, police vanilla)
 * @param cosmeticSlot emplacement cosmétique équipable, ou {@code null}
 * @param bagTier      nom du tier de sac si c'est un objet-sac équipable, sinon {@code null}
 */
public record InvItem(
        String id,
        String name,
        String desc,
        ItemCategory category,
        Rarity rarity,
        double weight,
        int count,
        String glyph,
        CosmeticSlot cosmeticSlot,
        String bagTier
) {
    public boolean isCosmetic() {
        return cosmeticSlot != null;
    }

    public boolean isConsumable() {
        return category == ItemCategory.CONSOMMABLES;
    }

    /** Vrai si c'est un objet-sac (à équiper dans l'emplacement Sac). */
    public boolean isBag() {
        return bagTier != null && !bagTier.isEmpty();
    }

    public double totalWeight() {
        return weight * Math.max(1, count);
    }

    /** Copie avec une nouvelle quantité (piles immuables). */
    public InvItem withCount(int c) {
        return new InvItem(id, name, desc, category, rarity, weight, c, glyph, cosmeticSlot, bagTier);
    }
}
