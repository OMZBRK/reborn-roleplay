package com.reborn.shinobicore.gui.framework;

import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiLayout;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Button factory + chrome for the screen framework. Everything clickable
 * carries a PDC action tag (and optional value); the
 * {@link ScreenManager} reads those tags and routes — no screen ever
 * touches raw slot numbers for navigation again.
 *
 * <h2>Reserved actions</h2>
 * {@code close}, {@code back}, {@code page:prev}, {@code page:next} are
 * handled by the manager itself. Anything else lands in
 * {@code Screen.onAction}.
 *
 * <p>Visual language is ShinobiCore's: {@link GuiIcons} tiers,
 * {@link GuiLayout} geometry — so every screen reads like the core
 * character GUIs.
 */
public final class Ui {

    /** PDC key: which action this button fires. */
    public static final String PDC_ACTION = "ui_action";
    /** PDC key: optional payload (ability id, character uuid, …). */
    public static final String PDC_VALUE = "ui_value";

    /* GUI action tags are transient (buttons are rebuilt on every open,
     * never persisted on real items), so they live under the neutral
     * "reborn" namespace — no plugin instance needed. Real-item PDC
     * stays with each plugin's own helper/namespace. */
    private static final org.bukkit.NamespacedKey ACTION_KEY =
            new org.bukkit.NamespacedKey("reborn", PDC_ACTION);
    private static final org.bukkit.NamespacedKey VALUE_KEY =
            new org.bukkit.NamespacedKey("reborn", PDC_VALUE);

    private static void tag(ItemMeta meta, org.bukkit.NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key,
                org.bukkit.persistence.PersistentDataType.STRING, value);
    }

    private static String tagOf(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(key, org.bukkit.persistence.PersistentDataType.STRING);
    }

    public static final String ACTION_CLOSE = "close";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_PAGE_PREV = "page:prev";
    public static final String ACTION_PAGE_NEXT = "page:next";

    private Ui() {}

    /* ------------------------------------------------------------ tagging */

    /** Tag any prebuilt item as an action button. Returns the item. */
    public static ItemStack action(ItemStack item, String action) {
        return action(item, action, null);
    }

    /** Tag any prebuilt item as an action button with a payload. */
    public static ItemStack action(ItemStack item, String action, String value) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        tag(meta, ACTION_KEY, action);
        if (value != null) tag(meta, VALUE_KEY, value);
        item.setItemMeta(meta);
        return item;
    }

    public static String actionOf(ItemStack item) {
        return tagOf(item, ACTION_KEY);
    }

    public static String valueOf(ItemStack item) {
        return tagOf(item, VALUE_KEY);
    }

    /* ------------------------------------------------ tier button factory */

    public static ItemStack primary(Material mat, String name, String action,
                                    String value, String... lore) {
        return flat(action(GuiIcons.primary(mat, name, lore), action, value));
    }

    public static ItemStack secondary(Material mat, String name, String action,
                                      String value, String... lore) {
        return flat(action(GuiIcons.secondary(mat, name, lore), action, value));
    }

    public static ItemStack destructive(Material mat, String name, String action,
                                        String value, String... lore) {
        return flat(action(GuiIcons.destructive(mat, name, lore), action, value));
    }

    public static ItemStack accent(Material mat, String name, String action,
                                   String value, String... lore) {
        return flat(action(GuiIcons.accent(mat, name, lore), action, value));
    }

    public static ItemStack info(Material mat, String name, String... lore) {
        return flat(GuiIcons.info(mat, name, lore));
    }

    public static ItemStack coloured(Material mat, String name, NamedTextColor colour,
                                     String action, String value, String... lore) {
        return flat(action(GuiIcons.coloured(mat, name, colour, lore), action, value));
    }

    /** Hide weapon/armor tooltips on icon items (swords as icons…). */
    public static ItemStack flat(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Enchant glint toggle on any built item. */
    public static ItemStack glint(ItemStack item, boolean on) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(on);
            item.setItemMeta(meta);
        }
        return item;
    }

    /* -------------------------------------------------------------- chrome */

    /** Paint the decorative perimeter. */
    public static void frame(Inventory inv) {
        int rows = inv.getSize() / GuiLayout.ROW;
        ItemStack border = GuiIcons.border();
        for (int s : GuiLayout.border(rows)) inv.setItem(s, border);
    }

    /**
     * Standard bottom strip: border + back (col 0) + pagination
     * (cols 3/4/5, shown only when {@code pages > 1}) + close (col 8).
     * {@code pageInfoLore} feeds the centre indicator.
     */
    public static void footer(Inventory inv, boolean withBack,
                              int page, int pages, String... pageInfoLore) {
        int size = inv.getSize();
        int start = size - GuiLayout.ROW;
        ItemStack border = GuiIcons.border();
        for (int s = start; s < size; s++) inv.setItem(s, border);

        if (withBack) {
            inv.setItem(start, action(GuiIcons.backButton(), ACTION_BACK));
        }
        if (pages > 1) {
            if (page > 0) {
                inv.setItem(start + 3, action(
                        GuiIcons.secondary(Material.ARROW, "← Page " + page),
                        ACTION_PAGE_PREV));
            }
            inv.setItem(start + 4, GuiIcons.info(Material.PAPER,
                    "Page " + (page + 1) + " / " + pages, pageInfoLore));
            if (page < pages - 1) {
                inv.setItem(start + 5, action(
                        GuiIcons.secondary(Material.ARROW, "Page " + (page + 2) + " →"),
                        ACTION_PAGE_NEXT));
            }
        }
        inv.setItem(size - 1, action(GuiIcons.closeButton(), ACTION_CLOSE));
    }

    /** Fill remaining nulls with the neutral filler panel. */
    public static void fillEmpty(Inventory inv) {
        ItemStack filler = GuiIcons.filler();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }
}
