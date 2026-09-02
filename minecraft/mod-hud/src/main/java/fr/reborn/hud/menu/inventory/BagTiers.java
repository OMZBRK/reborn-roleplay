package fr.reborn.hud.menu.inventory;

import java.util.Locale;

/**
 * Miroir client de {@code com.reborn.shinobicore.inventory.BagTier}. Sert
 * uniquement à la <b>MAJ optimiste</b> d'un changement de sac (calcul de la
 * nouvelle capacité côté client, pour un retour instantané et le test solo). Le
 * serveur reste autoritaire : sur serveur, il repousse le vrai snapshot juste
 * après. <b>Garder les valeurs synchronisées avec la classe serveur.</b>
 */
public enum BagTiers {
    NONE("À la ceinture", null, 0, 0.0),
    SACOCHE("Sacoche", "sac_sacoche", 9, 8.0),
    BANDOULIERE("Bandoulière", "sac_bandouliere", 18, 16.0),
    SAC("Sac ninja", "sac_dos", 27, 28.0),
    SAC_LOURD("Sac lourd", "sac_lourd", 36, 45.0);

    public static final int BASE_SLOTS = 9;
    public static final double BASE_WEIGHT = 12.0;

    public final String displayName;
    public final String itemId;
    public final int extraSlots;
    public final double extraWeight;

    BagTiers(String displayName, String itemId, int extraSlots, double extraWeight) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.extraSlots = extraSlots;
        this.extraWeight = extraWeight;
    }

    public int totalSlots() { return BASE_SLOTS + extraSlots; }
    public double totalWeight() { return BASE_WEIGHT + extraWeight; }

    public static BagTiers fromName(String name) {
        if (name == null) return NONE;
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** Tier depuis l'id d'objet-sac ("sac_sacoche"…), ou null. */
    public static BagTiers fromItemId(String itemId) {
        if (itemId == null) return null;
        for (BagTiers t : values()) {
            if (itemId.equals(t.itemId)) return t;
        }
        return null;
    }

    /** Tier depuis un item_model ("nexo:sac_sacoche" / "sac_sacoche"), ou null. */
    public static BagTiers fromModel(String model) {
        if (model == null) return null;
        int c = model.indexOf(':');
        String path = c >= 0 ? model.substring(c + 1) : model;
        return fromItemId(path);
    }
}
