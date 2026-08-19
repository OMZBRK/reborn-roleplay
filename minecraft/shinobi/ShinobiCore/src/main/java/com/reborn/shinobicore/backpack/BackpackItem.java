package com.reborn.shinobicore.backpack;

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
import java.util.UUID;

/**
 * Factory + identity check for backpack {@link ItemStack}s. Each
 * backpack carries:
 * <ul>
 *   <li>a unique {@link UUID} in PDC under {@link #idKey} — keys the
 *       backpack's inventory in {@link BackpackManager};</li>
 *   <li>a size byte (0 = SMALL, 1 = LARGE) under {@link #sizeKey};</li>
 *   <li>a marker byte under {@link #markerKey} so we can detect any
 *       backpack item without parsing its display name.</li>
 * </ul>
 *
 * <p>Material is {@link Material#LEATHER} for SMALL and
 * {@link Material#NETHERITE_INGOT} for LARGE — distinct visuals
 * without needing custom textures or resource packs. Display name and
 * lore are themed (gold + brown), and the lore tells the player how to
 * equip and use it.
 */
public final class BackpackItem {

    private static final String MARKER_KEY  = "backpack";
    private static final String ID_KEY      = "backpack_id";
    private static final String SIZE_KEY    = "backpack_size";

    private BackpackItem() {}

    // Keys depend only on the (singleton, reload-stable) plugin namespace, so
    // cache them once instead of re-allocating on every PDC read/write.
    private static NamespacedKey cachedMarker;
    private static NamespacedKey cachedId;
    private static NamespacedKey cachedSize;

    public static NamespacedKey markerKey(ShinobiCore plugin) {
        if (cachedMarker == null) cachedMarker = new NamespacedKey(plugin, MARKER_KEY);
        return cachedMarker;
    }
    public static NamespacedKey idKey(ShinobiCore plugin) {
        if (cachedId == null) cachedId = new NamespacedKey(plugin, ID_KEY);
        return cachedId;
    }
    public static NamespacedKey sizeKey(ShinobiCore plugin) {
        if (cachedSize == null) cachedSize = new NamespacedKey(plugin, SIZE_KEY);
        return cachedSize;
    }

    /** Build the physical {@link ItemStack} for a backpack with the
     *  given id + size. The id is stamped on the PDC so the same item
     *  always identifies the same backpack record. Caller is
     *  responsible for ensuring the {@link BackpackManager} has a
     *  record for this id. */
    public static ItemStack create(ShinobiCore plugin, UUID id, BackpackSize size) {
        Material mat = (size == BackpackSize.LARGE) ? Material.NETHERITE_INGOT : Material.LEATHER;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(size.displayName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(
                    Component.text(size.slots() + " emplacements de stockage",
                                    NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Place-le dans l'emplacement de plastron", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("pour l'équiper. Clic sur l'icône au", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("centre de l'inventaire pour l'ouvrir.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Clic droit sur un bloc en main",
                                    NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.text("pour le poser au sol.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, true)
            ));
            m.getPersistentDataContainer().set(markerKey(plugin),
                    PersistentDataType.BYTE, (byte) 1);
            m.getPersistentDataContainer().set(idKey(plugin),
                    PersistentDataType.STRING, id.toString());
            m.getPersistentDataContainer().set(sizeKey(plugin),
                    PersistentDataType.BYTE, (byte) (size == BackpackSize.LARGE ? 1 : 0));
            m.setUnbreakable(true);
            it.setItemMeta(m);
        }
        return it;
    }

    /** True iff the item is one of our backpacks (any size). Cheap —
     *  just checks the marker byte. */
    public static boolean isBackpack(ShinobiCore plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(markerKey(plugin), PersistentDataType.BYTE);
    }

    /** The {@link UUID} stamped on this backpack item, or {@code null}
     *  when the item isn't a backpack (or the id is malformed). */
    public static UUID idOf(ShinobiCore plugin, ItemStack item) {
        if (!isBackpack(plugin, item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(idKey(plugin), PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }

    /** The size of this backpack item, or {@code null} when the item
     *  isn't a backpack. */
    public static BackpackSize sizeOf(ShinobiCore plugin, ItemStack item) {
        if (!isBackpack(plugin, item)) return null;
        Byte b = item.getItemMeta().getPersistentDataContainer()
                .get(sizeKey(plugin), PersistentDataType.BYTE);
        if (b == null) return null;
        return b == 1 ? BackpackSize.LARGE : BackpackSize.SMALL;
    }
}
