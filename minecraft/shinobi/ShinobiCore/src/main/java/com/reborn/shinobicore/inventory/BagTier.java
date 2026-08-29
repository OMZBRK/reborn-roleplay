package com.reborn.shinobicore.inventory;

/**
 * Type de sac équipé — <b>c'est lui qui débloque l'espace</b>. Sans sac, le
 * personnage n'a que sa capacité « sur soi » ({@link RpBag#BASE_SLOTS} cases,
 * grosso modo la ceinture / les poches). Équiper une {@link #SACOCHE}, une
 * {@link #BANDOULIERE}, un {@link #SAC} ou un {@link #SAC_LOURD} ajoute des
 * cases <b>et</b> de la limite de poids. Objectif RP : pas d'incohérence (on ne
 * porte pas 1000 objets sans contenant), et amorce du système de <b>fouille</b>
 * INRP (si quelqu'un a un sac, il a de quoi transporter — et à fouiller).
 *
 * <p>Chaque tier (hors {@link #NONE}) correspond à un objet-sac physique
 * ({@link #itemId}) enregistré dans {@link RpItemType} et — côté serveur de prod
 * — à un item <b>Nexo</b> du même id pour l'affichage du modèle 3D. On équipe /
 * déséquipe le sac depuis la Sacoche (emplacement dédié), comme un cosmétique.
 */
public enum BagTier {
    /** Aucun sac : seulement la capacité de base (sur soi). */
    NONE("À la ceinture", null, 0, 0.0, "―"),
    SACOCHE("Sacoche", "sac_sacoche", 9, 8.0, "嚢"),
    BANDOULIERE("Bandoulière", "sac_bandouliere", 18, 16.0, "帯"),
    SAC("Sac ninja", "sac_dos", 27, 28.0, "袋"),
    SAC_LOURD("Sac lourd", "sac_lourd", 36, 45.0, "嚢");

    /** Nom affiché (FR). */
    public final String displayName;
    /** Id de l'objet-sac dans {@link RpItemType} (et de l'item Nexo), ou {@code null} pour {@link #NONE}. */
    public final String itemId;
    /** Cases ajoutées <b>au-dessus</b> de {@link RpBag#BASE_SLOTS}. */
    public final int extraSlots;
    /** Limite de poids ajoutée <b>au-dessus</b> de {@link RpBag#BASE_WEIGHT} (kg). */
    public final double extraWeight;
    /** Kanji-repère (affichage). */
    public final String glyph;

    BagTier(String displayName, String itemId, int extraSlots, double extraWeight, String glyph) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.extraSlots = extraSlots;
        this.extraWeight = extraWeight;
        this.glyph = glyph;
    }

    /** Résout le tier depuis l'id de l'objet-sac (ex. {@code "sac_dos"}), ou {@code null}. */
    public static BagTier fromItemId(String itemId) {
        if (itemId == null) return null;
        for (BagTier t : values()) {
            if (itemId.equals(t.itemId)) return t;
        }
        return null;
    }

    /** Résout le tier depuis son nom d'enum, tolérant ; {@link #NONE} par défaut. */
    public static BagTier fromName(String name) {
        if (name == null) return NONE;
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
