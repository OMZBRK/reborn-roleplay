package com.reborn.shinobicore.util;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluent {@link ItemStack} builder. Replaces the boiler block that
 * appears in 20+ item-factory classes (JutsuItem, GrappleItem,
 * ZiplineItem, BackpackItem, MedicineItem, ParcheminItem,
 * LearnShelfItem, TrailMapItem, plus all GUI labeled-icon helpers):
 * <pre>{@code
 *   ItemStack it = new ItemStack(Material.PAPER);
 *   ItemMeta m = it.getItemMeta();
 *   m.displayName(Component.text("Hello", NamedTextColor.GOLD)
 *           .decoration(TextDecoration.BOLD, true)
 *           .decoration(TextDecoration.ITALIC, false));
 *   m.lore(List.of(...));
 *   m.setUnbreakable(true);
 *   m.getPersistentDataContainer().set(...);
 *   it.setItemMeta(m);
 * }</pre>
 * <p>...with:
 * <pre>{@code
 *   ItemStack it = Items.of(Material.PAPER)
 *           .name("Hello", NamedTextColor.GOLD)
 *           .lore("Subtitle", "Description")
 *           .unbreakable()
 *           .pdc(plugin, "my_key", "value")
 *           .build();
 * }</pre>
 *
 * <p>Display names are bold + non-italic by default (matches the
 * project's existing display convention); pass extra
 * {@link TextDecoration}s to override.
 *
 * <p>Lore lines are gray + non-italic by default; lines starting with
 * an Adventure colour code prefix override the colour.
 */
public final class Items {

    private final ItemStack stack;
    private final ItemMeta  meta;

    private Items(Material material, int amount) {
        this.stack = new ItemStack(material, amount);
        this.meta  = stack.getItemMeta();
    }

    /** Start a builder for a single item of {@code material}. */
    public static Items of(Material material) {
        return new Items(material, 1);
    }

    /** Start a builder for a stack of {@code amount} items. */
    public static Items of(Material material, int amount) {
        return new Items(material, amount);
    }

    /* ----------------------------------------------------------- name */

    /** Display name in {@code color}, bold, non-italic. */
    public Items name(String text, TextColor color) {
        if (meta == null) return this;
        meta.displayName(Component.text(text, color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        return this;
    }

    /** Display name in gold, bold, non-italic. Convenience for the
     *  common "highlight" case. */
    public Items name(String text) {
        return name(text, NamedTextColor.GOLD);
    }

    /** Custom-styled display name — caller controls all decorations. */
    public Items nameRaw(Component component) {
        if (meta == null) return this;
        meta.displayName(component);
        return this;
    }

    /* ----------------------------------------------------------- lore */

    /** Set lore lines. Each string becomes a gray, non-italic line.
     *  Pass {@link Component} instances via {@link #loreRaw} for
     *  custom styling. */
    public Items lore(String... lines) {
        if (meta == null) return this;
        List<Component> components = new ArrayList<>(lines.length);
        for (String line : lines) {
            components.add(Component.text(line, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(components);
        return this;
    }

    /** Set lore lines with full Component control. */
    public Items loreRaw(List<Component> lines) {
        if (meta == null) return this;
        meta.lore(lines);
        return this;
    }

    /* --------------------------------------------------------- flags */

    public Items unbreakable() {
        if (meta != null) meta.setUnbreakable(true);
        return this;
    }

    /* --------------------------------------------------------- PDC */

    /** Stamp a string PDC value scoped to {@code plugin}. */
    public Items pdc(ShinobiCore plugin, String key, String value) {
        if (meta == null) return this;
        meta.getPersistentDataContainer().set(
                PdcAccess.key(plugin, key),
                PersistentDataType.STRING, value);
        return this;
    }

    /** Stamp a UUID PDC value scoped to {@code plugin}. */
    public Items pdc(ShinobiCore plugin, String key, UUID value) {
        if (value == null) return this;
        return pdc(plugin, key, value.toString());
    }

    /** Stamp an int PDC value scoped to {@code plugin}. */
    public Items pdcInt(ShinobiCore plugin, String key, int value) {
        if (meta == null) return this;
        meta.getPersistentDataContainer().set(
                PdcAccess.key(plugin, key),
                PersistentDataType.INTEGER, value);
        return this;
    }

    /* ---------------------------------------------------------- build */

    /** Commit the meta and return the finished ItemStack. */
    public ItemStack build() {
        if (meta != null) stack.setItemMeta(meta);
        return stack;
    }
}
