package com.reborn.shinobicore.character.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Themed {@link ItemStack} factories for ShinobiCore GUIs. Tier colours
 * and chrome panels come from the active
 * {@link com.reborn.shinobicore.gui.GuiTheme} (see
 * {@link com.reborn.shinobicore.gui.Themes}); the defaults below
 * describe the built-in Naruto theme.
 *
 * <h2>Color tiers</h2>
 * Every clickable icon picks a colour from a small palette so the UI
 * reads at a glance:
 * <ul>
 *   <li>{@link #primary primary}   — <b>green</b>, the "do the thing" button.</li>
 *   <li>{@link #secondary secondary} — <b>yellow</b>, alternate / soft action.</li>
 *   <li>{@link #destructive destructive} — <b>red</b>, close / delete / decline.</li>
 *   <li>{@link #nav nav}         — <b>gray</b>, back / neutral navigation.</li>
 *   <li>{@link #info info}       — <b>aqua</b>, display-only panels.</li>
 *   <li>{@link #accent accent}   — <b>gold</b>, headers / character identity.</li>
 * </ul>
 *
 * <p>Lore lines accept the legacy {@code &a / &e / &c / &b / &7} prefix
 * encoding used across the GUI layer so existing GUIs can switch over one
 * method at a time without re-wording every string.
 */
public final class GuiIcons {

    private GuiIcons() {}

    /* ------------------------------------------------------- color tiers */

    public static ItemStack primary(Material mat, String name, String... lore) {
        return themed(mat, name, com.reborn.shinobicore.gui.Themes.current().primary(), lore);
    }

    public static ItemStack secondary(Material mat, String name, String... lore) {
        return themed(mat, name, com.reborn.shinobicore.gui.Themes.current().secondary(), lore);
    }

    public static ItemStack destructive(Material mat, String name, String... lore) {
        return themed(mat, name, com.reborn.shinobicore.gui.Themes.current().destructive(), lore);
    }

    public static ItemStack nav(Material mat, String name, String... lore) {
        return themed(mat, name, com.reborn.shinobicore.gui.Themes.current().nav(), lore);
    }

    public static ItemStack info(Material mat, String name, String... lore) {
        return themed(mat, name, com.reborn.shinobicore.gui.Themes.current().info(), lore);
    }

    public static ItemStack accent(Material mat, String name, String... lore) {
        return themed(mat, name, com.reborn.shinobicore.gui.Themes.current().accent(), lore);
    }

    /** Display-only panel with a specific colour — used for items that
     *  already have a themed colour (clan icons, element icons, rank
     *  icons) so the palette can still carry their own tint. */
    public static ItemStack coloured(Material mat, String name, NamedTextColor colour, String... lore) {
        return themed(mat, name, colour, lore);
    }

    /** Player head with a character's name in gold. */
    public static ItemStack head(OfflinePlayer owner, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(owner);
            sm.displayName(displayName(name, com.reborn.shinobicore.gui.Themes.current().accent(), /*bold=*/true));
            sm.lore(loreLines(lore));
            it.setItemMeta(sm);
        }
        return it;
    }

    /* -------------------------------------------- panels + standard buttons */

    /** Black panel used to paint the decorative perimeter of a chest. */
    public static ItemStack border() {
        return panel(com.reborn.shinobicore.gui.Themes.current().borderPanel());
    }

    /** Gray panel used to fill otherwise-empty interior slots. */
    public static ItemStack filler() {
        return panel(com.reborn.shinobicore.gui.Themes.current().fillerPanel());
    }

    /** Standard back arrow, gray. */
    public static ItemStack backButton() {
        return nav(Material.ARROW, "Retour");
    }

    /** Standard close button, red. */
    public static ItemStack closeButton() {
        return destructive(Material.BARRIER, "Fermer");
    }

    /* ------------------------------------------------------------- helpers */

    private static ItemStack panel(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(" "));
            m.lore(List.of());
            it.setItemMeta(m);
        }
        return it;
    }

    private static ItemStack themed(Material mat, String name, NamedTextColor colour, String[] lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(displayName(name, colour, /*bold=*/true));
            m.lore(loreLines(lore));
            it.setItemMeta(m);
        }
        return it;
    }

    private static Component displayName(String text, NamedTextColor colour, boolean bold) {
        return Component.text(text, colour)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, false);
    }

    /** Convert the legacy {@code &a}/{@code &e}/{@code &c}/{@code &b}/{@code &7}
     *  lore prefixes into coloured Adventure components. Unknown prefixes
     *  (or plain lines) fall back to gray. */
    private static List<Component> loreLines(String[] raw) {
        if (raw == null || raw.length == 0) return List.of();
        List<Component> out = new ArrayList<>(raw.length);
        for (String line : raw) {
            if (line == null) continue;
            NamedTextColor col = NamedTextColor.GRAY;
            String content = line;
            if      (content.startsWith("&a")) { col = NamedTextColor.GREEN;        content = content.substring(2); }
            else if (content.startsWith("&e")) { col = NamedTextColor.YELLOW;       content = content.substring(2); }
            else if (content.startsWith("&c")) { col = NamedTextColor.RED;          content = content.substring(2); }
            else if (content.startsWith("&b")) { col = NamedTextColor.AQUA;         content = content.substring(2); }
            else if (content.startsWith("&6")) { col = NamedTextColor.GOLD;         content = content.substring(2); }
            else if (content.startsWith("&d")) { col = NamedTextColor.LIGHT_PURPLE; content = content.substring(2); }
            else if (content.startsWith("&7")) { col = NamedTextColor.GRAY;         content = content.substring(2); }
            else if (content.startsWith("&8")) { col = NamedTextColor.DARK_GRAY;    content = content.substring(2); }
            out.add(Component.text(content, col)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }
}
