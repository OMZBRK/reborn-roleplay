package com.reborn.shinobicore.mobility.ability;

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
 * Factory + identity-check for the <b>Zipline de Ninja</b>.
 *
 * <p>Material is {@link Material#END_ROD} — thematic (the zipline itself
 * places End Rod blocks as endpoints) and instantly recognisable in the
 * hotbar. Identity is carried by a PDC byte so a plain End Rod block
 * can't trigger the ability.
 *
 * <p>Vanilla End Rod has right-click-to-place behaviour which would
 * clutter the world with decorative rods if players accidentally
 * right-click during a deployment attempt — the listener handles that
 * by cancelling right-click interactions with the tagged item.
 */
public final class ZiplineItem {

    private static final Material MATERIAL = Material.END_ROD;
    private static final String KEY = "zipline";

    private ZiplineItem() {}

    public static ItemStack create(ShinobiCore plugin) {
        ItemStack it = new ItemStack(MATERIAL);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Zipline de Ninja", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            m.lore(List.of(
                    Component.text("Clic gauche sur un bloc jusqu'à 90 blocs", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("pour tendre une tyrolienne de chakra", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("entre ce bloc et ta position.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Place-toi près d'une extrémité et appuie sur F", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.text("pour glisser vers l'autre côté.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)
            ));
            m.setUnbreakable(true);
            m.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
        }
        return it;
    }

    public static boolean isZipline(ShinobiCore plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL) return false;
        ItemMeta m = item.getItemMeta();
        if (m == null) return false;
        return m.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    private static NamespacedKey key(ShinobiCore plugin) {
        return new NamespacedKey(plugin, KEY);
    }
}
