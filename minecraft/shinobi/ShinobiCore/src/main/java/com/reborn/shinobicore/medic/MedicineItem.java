package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory + PDC helpers for {@link Medicine} {@link ItemStack}s.
 *
 * <p>Each medicine carries a single PDC marker — {@code medicine_kind}
 * holding the enum name — so {@link #typeOf} can identify a medicine
 * regardless of name, lore, or amount. We don't stamp a per-item UUID
 * because medicines are fungible (no inventory-per-pill bookkeeping).
 */
public final class MedicineItem {

    private static final String PDC_KEY = "medicine_kind";

    private MedicineItem() {}

    /** Build a fresh stack of {@code amount} of {@code medicine}. */
    public static ItemStack create(ShinobiCore plugin, Medicine medicine, int amount) {
        ItemStack it = new ItemStack(medicine.material(), Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(medicine.displayName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(medicine.tagline(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            for (String u : medicine.usage()) {
                lore.add(Component.text("  " + u, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
            m.getPersistentDataContainer().set(key(plugin),
                    PersistentDataType.STRING, medicine.name());
            it.setItemMeta(m);
        }
        return it;
    }

    /** Identify the medicine carried by {@code item}, or {@code null}
     *  when the stack isn't one of ours. */
    public static Medicine typeOf(ShinobiCore plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(key(plugin), PersistentDataType.STRING);
        if (raw == null) return null;
        try { return Medicine.valueOf(raw); }
        catch (IllegalArgumentException ex) { return null; }
    }

    public static boolean isMedicine(ShinobiCore plugin, ItemStack item) {
        return typeOf(plugin, item) != null;
    }

    private static NamespacedKey key(ShinobiCore plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }
}
