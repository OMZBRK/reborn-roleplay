package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Fabrique + identité du <b>Test de la Feuille</b> : une feuille (papier) sensible
 * au chakra. Clic droit → l'entraînement de concentration de la feuille (réf.
 * animé) : la feuille brûle/mouille/froisse/fend/effrite selon la nature.
 *
 * <p>{@code PAPER} n'a aucune interaction vanilla au clic droit → le packet
 * atteint le serveur intact. Seul un item produit par {@link #create} déclenche
 * le test ({@link #isLeafTest} lit un marqueur PDC).
 */
public final class LeafTestItem {

    private static final Material MATERIAL = Material.PAPER;
    private static final String KEY = "leaftest";

    private LeafTestItem() {}

    public static ItemStack create(ShinobiCore plugin) {
        ItemStack it = new ItemStack(MATERIAL);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Test de la Feuille", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            m.lore(List.of(
                    Component.text("Une feuille sensible au chakra.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Clic droit pour canaliser ton chakra", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("et révéler ta nature.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Elle brûle, se mouille, se froisse,", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.text("se fend ou s'effrite selon ta nature.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)
            ));
            m.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
        }
        return it;
    }

    public static boolean isLeafTest(ShinobiCore plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL) return false;
        ItemMeta m = item.getItemMeta();
        if (m == null) return false;
        return m.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    private static NamespacedKey key(ShinobiCore plugin) {
        return new NamespacedKey(plugin, KEY);
    }
}
