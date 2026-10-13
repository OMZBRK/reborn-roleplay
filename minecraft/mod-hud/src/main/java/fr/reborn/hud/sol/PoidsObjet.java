package fr.reborn.hud.sol;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

import java.util.Locale;

/**
 * Poids RP d'un objet, <b>copie côté client</b> du barème de ShinobiCore ({@code inventory.RpWeights}) : poids propre
 * {@code reborn:rp_weight} s'il est posé, sinon estimation par famille de matériau. Ne sert qu'à l'invite de
 * ramassage (« trop lourd ») : le serveur décide.
 */
public final class PoidsObjet {

    private PoidsObjet() {}

    /** Poids d'un exemplaire (kg). */
    public static double unite(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        double propre = Rarete.bukkitNombre(s, "reborn:rp_weight", -1);
        if (propre >= 0) return propre;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        String n = id == null ? "" : id.getPath().toUpperCase(Locale.ROOT);
        if (n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")) return 3.5;
        if (n.endsWith("_HELMET") || n.endsWith("_BOOTS")) return 2.5;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")
            || n.endsWith("_HOE")) return 2.0;
        if (n.contains("BLOCK") || n.contains("_ORE") || n.contains("INGOT")) return 1.5;
        if (n.contains("POTION")) return 0.5;
        if (s.has(DataComponents.FOOD)) return 0.3;
        if (s.getItem() instanceof BlockItem) return 1.0;
        return 0.4;
    }
}
