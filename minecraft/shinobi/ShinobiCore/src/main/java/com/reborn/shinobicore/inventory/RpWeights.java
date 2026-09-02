package com.reborn.shinobicore.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Estimation du <b>poids RP</b> d'un vrai {@link ItemStack}. Depuis le passage
 * « base = hotbar vanilla », le contenu du sac n'est plus des objets-données mais
 * de vrais items MC/Nexo — le poids se déduit donc du {@link Material} (barème
 * grossier mais cohérent). À affiner plus tard via un vrai registre par id Nexo.
 */
public final class RpWeights {

    private RpWeights() {}

    /** Poids total d'une pile (unitaire × quantité), 0 si vide. */
    public static double of(ItemStack s) {
        if (s == null || s.getType() == Material.AIR) return 0.0;
        return unit(s.getType()) * Math.max(1, s.getAmount());
    }

    /** Poids unitaire estimé par famille de matériau (kg). */
    private static double unit(Material m) {
        String n = m.name();
        if (n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")) return 3.5;
        if (n.endsWith("_HELMET") || n.endsWith("_BOOTS")) return 2.5;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE")
            || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) return 2.0;
        if (n.contains("BLOCK") || n.contains("_ORE") || n.contains("INGOT")) return 1.5;
        if (n.contains("POTION")) return 0.5;
        try { if (m.isEdible()) return 0.3; } catch (Throwable ignored) { }
        if (m.isBlock()) return 1.0;
        return 0.4; // objets divers (parchemins, kunai-like, matériaux)
    }
}
