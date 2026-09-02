package com.reborn.shinobicore.technique;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.Locale;

/**
 * The seven specialized JutsuItem variants. Each opens a 5-slot picker
 * for one category branch and carries its own incantation sound.
 *
 * <p>Items are <b>universal keys</b> — no owner stamping. Bindings live
 * on the character, indexed by this enum.
 */
public enum JutsuItemType {

    /** Scrolls — the generalist channel. */
    ROULEAU(Material.PAPER, "Rouleau de Jutsu", Sound.ITEM_BUNDLE_INSERT),
    /** Dōjutsu eye techniques. */
    PUPILLES(Material.ENDER_EYE, "Pupilles", Sound.ENTITY_ENDERMAN_AMBIENT),
    /** Kenjutsu blade arts. */
    KATANA(Material.NETHERITE_SWORD, "Katana", Sound.ITEM_TRIDENT_RIPTIDE_1),
    /** Kunaijutsu. */
    KUNAI(Material.IRON_SWORD, "Kunai", Sound.ITEM_ARMOR_EQUIP_CHAIN),
    /** Shurikenjutsu. */
    SHURIKEN(Material.PRISMARINE_SHARD, "Shuriken", Sound.ENTITY_ARROW_SHOOT),
    /** Bōjutsu / tessenjutsu / fūma shuriken. */
    FUMA(Material.NETHER_STAR, "Fūma", Sound.ENTITY_PLAYER_ATTACK_SWEEP),
    /** Taijutsu. */
    POING(Material.LEATHER, "Poing", Sound.BLOCK_BAMBOO_HIT);

    private final Material material;
    private final String displayName;
    private final Sound incantationSound;

    JutsuItemType(Material material, String displayName, Sound incantationSound) {
        this.material = material;
        this.displayName = displayName;
        this.incantationSound = incantationSound;
    }

    public Material material() { return material; }
    public String displayName() { return displayName; }
    public Sound incantationSound() { return incantationSound; }

    /**
     * Category routing — which ability categories this item can bind.
     * <ul>
     *   <li>ROULEAU — ninjutsu/*, autres/*, senjutsu/*, kekkei/* except dojutsu</li>
     *   <li>PUPILLES — kekkei/dojutsu/*</li>
     *   <li>KATANA — bukijutsu/kenjutsu</li>
     *   <li>KUNAI — bukijutsu/kunaijutsu</li>
     *   <li>SHURIKEN — bukijutsu/shurikenjutsu</li>
     *   <li>FUMA — bukijutsu/bojutsu, /tessenjutsu, /fuma</li>
     *   <li>POING — taijutsu*</li>
     * </ul>
     */
    public boolean accepts(String category) {
        if (category == null) return false;
        String c = category.trim().toLowerCase(Locale.ROOT);
        return switch (this) {
            case ROULEAU -> (c.startsWith("ninjutsu/") || c.equals("ninjutsu")
                    || c.startsWith("autres/") || c.equals("autres")
                    || c.startsWith("senjutsu/") || c.equals("senjutsu")
                    || (c.startsWith("kekkei/") && !c.startsWith("kekkei/dojutsu")))
                    && !c.startsWith("kekkei/dojutsu");
            case PUPILLES -> c.startsWith("kekkei/dojutsu");
            case KATANA -> c.startsWith("bukijutsu/kenjutsu");
            case KUNAI -> c.startsWith("bukijutsu/kunaijutsu");
            case SHURIKEN -> c.startsWith("bukijutsu/shurikenjutsu");
            case FUMA -> c.startsWith("bukijutsu/bojutsu")
                    || c.startsWith("bukijutsu/tessenjutsu")
                    || c.startsWith("bukijutsu/fuma");
            case POING -> c.equals("taijutsu") || c.startsWith("taijutsu/");
        };
    }

    /** First type whose routing accepts {@code category}, else null. */
    public static JutsuItemType forCategory(String category) {
        for (JutsuItemType t : values()) {
            if (t.accepts(category)) return t;
        }
        return null;
    }

    public static JutsuItemType from(String s) {
        if (s == null) return null;
        try { return JutsuItemType.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return null; }
    }
}
