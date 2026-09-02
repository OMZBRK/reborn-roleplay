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
 * Factory + identity-check for the <b>Grappin de Ninja</b>.
 *
 * <p><b>Why a non-interactive material?</b> The original version used a
 * fishing rod, which has vanilla right-click behaviour that Minecraft
 * caps at ~4.5 blocks of interaction reach — the client silently refuses
 * to send a "use item" packet past that distance when aimed at air. A
 * stick has no vanilla right- or left-click behaviour, so every click
 * reaches the server intact, and we can trigger the grapple on
 * <em>left</em>-click without fighting any vanilla handlers.
 *
 * <p>A plain stick must never trigger the grappling hook — only an item
 * produced by {@link #create} does, because
 * {@link #isGrappin(ShinobiCore, ItemStack)} reads a
 * {@link org.bukkit.persistence.PersistentDataContainer PDC} marker we
 * stamp at creation time.
 */
public final class GrappleItem {

    /** Material chosen for its lack of vanilla interactions. */
    private static final Material MATERIAL = Material.STICK;

    /** PDC key used to mark items as the Grappin. */
    private static final String KEY = "grappin";

    private GrappleItem() {}

    /** Create a fresh Grappin de Ninja item. */
    public static ItemStack create(ShinobiCore plugin) {
        ItemStack it = new ItemStack(MATERIAL);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("Grappin de Ninja", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            m.lore(List.of(
                    Component.text("Clic gauche pour lancer une corde de chakra", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("vers la surface visée.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Clic gauche à nouveau pour décrocher.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)
            ));
            m.setUnbreakable(true);
            // Mark it — any item without this PDC byte is NOT a Grappin.
            m.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
        }
        return it;
    }

    /** True iff {@code item} is a Grappin produced by {@link #create}. */
    public static boolean isGrappin(ShinobiCore plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL) return false;
        ItemMeta m = item.getItemMeta();
        if (m == null) return false;
        return m.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    private static NamespacedKey key(ShinobiCore plugin) {
        return new NamespacedKey(plugin, KEY);
    }
}
