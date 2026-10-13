package fr.reborn.hud.sol;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * Rareté d'un objet, cinq niveaux (décision du 2026-10-13), alignée sur ShinobiCore {@code inventory.RpRarity} et sur
 * les rangs des parchemins (D → S). Couleurs des maquettes (Maquettes/gameplan/02_inventaire) : aura au sol, liseré des
 * cases, nom dans les notifications.
 *
 * <p>Lecture côté client ({@link #of}) : clé {@code reborn:rarete} posée par le serveur (données Bukkit de l'objet,
 * {@code custom_data.PublicBukkitValues}) → modèle de parchemin {@code reborn:parchemin_<rang>} → rareté vanilla →
 * Commun. (La table {@code rarete.par-id} reste côté serveur : il pose la clé sur tout objet qui apparaît au sol.)
 */
public enum Rarete {
    COMMUN("Commun", 0xCFC8B8, 0.28f),
    PEU_COMMUN("Peu commun", 0x6FBF8E, 0.62f),
    RARE("Rare", 0x5E95E0, 0.72f),
    EPIQUE("Épique", 0xA97BE0, 0.78f),
    LEGENDAIRE("Légendaire", 0xE9BC5C, 0.85f);

    public final String label;
    public final int rgb;
    /** Force de l'aura au sol (Commun : presque invisible). */
    public final float force;

    Rarete(String label, int rgb, float force) {
        this.label = label;
        this.rgb = rgb;
        this.force = force;
    }

    public int argb() {
        return 0xFF000000 | rgb;
    }

    /** Nom d'enum ou libellé (« Rare », « Épique », « Peu commun »…) ; {@code null} si inconnu. */
    public static Rarete parse(String s) {
        if (s == null || s.isBlank()) return null;
        String n = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_').replace('É', 'E').replace('È', 'E');
        if (n.equals("COMMUNE")) return COMMUN;
        try {
            return valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Rarete parseOr(String s, Rarete def) {
        Rarete r = parse(s);
        return r != null ? r : def;
    }

    /** Rareté d'un objet du monde (voir l'en-tête de classe). */
    public static Rarete of(ItemStack s) {
        if (s == null || s.isEmpty()) return COMMUN;
        Rarete r = parse(bukkit(s, "reborn:rarete"));
        if (r != null) return r;
        Identifier model = s.get(DataComponents.ITEM_MODEL);
        if (model != null) {
            String p = model.getPath();
            int slash = p.lastIndexOf('/');
            if (slash >= 0) p = p.substring(slash + 1);
            if (p.startsWith("parchemin_") && p.length() == "parchemin_".length() + 1) {
                switch (Character.toUpperCase(p.charAt(p.length() - 1))) {
                    case 'C': return PEU_COMMUN;
                    case 'B': return RARE;
                    case 'A': return EPIQUE;
                    case 'S': return LEGENDAIRE;
                    default: return COMMUN;
                }
            }
        }
        net.minecraft.world.item.Rarity v = s.get(DataComponents.RARITY);
        if (v != null) {
            return switch (v) {
                case UNCOMMON -> PEU_COMMUN;
                case RARE -> RARE;
                case EPIC -> EPIQUE;
                default -> COMMUN;
            };
        }
        return COMMUN;
    }

    /** Valeur texte des données Bukkit ({@code PublicBukkitValues}) de l'objet, ou {@code null}. */
    public static String bukkit(ItemStack s, String cle) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return null;
        CompoundTag t = cd.copyTag().getCompoundOrEmpty("PublicBukkitValues");
        return t.getString(cle).orElse(null);
    }

    /** Valeur numérique des données Bukkit, ou {@code def}. */
    public static double bukkitNombre(ItemStack s, String cle, double def) {
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return def;
        return cd.copyTag().getCompoundOrEmpty("PublicBukkitValues").getDoubleOr(cle, def);
    }
}
