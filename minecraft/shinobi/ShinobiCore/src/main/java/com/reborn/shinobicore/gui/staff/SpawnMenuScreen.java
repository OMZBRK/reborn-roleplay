package com.reborn.shinobicore.gui.staff;

import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GMod-style block/item spawn palette for staff. Every obtainable
 * {@link Material} is browsable by category tab, paginated 36 per page;
 * a click hands the material to the viewer's inventory (a normal give —
 * it never touches gamemode or the character snapshot). A chat-prompt
 * search narrows the palette to matching material names.
 *
 * <p>Gated on {@code shinobicore.staff}.
 */
public final class SpawnMenuScreen extends CoreScreen {

    private static final String S_CAT    = "cat";     // Cat.name()
    private static final String S_FILTER = "filter";  // lowercase substring ("" = none)
    private static final int GRID = 36;               // rows 1..4
    private static final int CONTENT_START = 9;

    /** Coarse, robust buckets. A material lands in the FIRST bucket it
     *  matches (see {@link #categoryOf}); tabs render in this order. */
    enum Cat {
        BLOCS("Blocs", Material.GRASS_BLOCK),
        DECO("Deco & Redstone", Material.REDSTONE),
        OUTILS("Outils & Armes", Material.IRON_SWORD),
        NOURRITURE("Nourriture", Material.COOKED_BEEF),
        DIVERS("Divers", Material.ENDER_PEARL);

        final String label;
        final Material icon;
        Cat(String label, Material icon) { this.label = label; this.icon = icon; }
    }

    /** All givable materials, computed once. */
    private static volatile List<Material> ITEMS;

    public SpawnMenuScreen(CoreGuiRouter router) {
        super(router);
    }

    /* --------------------------------------------------------------- open */

    public void open(Player viewer) {
        if (!viewer.hasPermission("shinobicore.staff")) {
            GuiSounds.error(viewer);
            return;
        }
        router.screens().open(viewer, this, Map.of(S_CAT, Cat.BLOCS.name()));
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        Cat cat = currentCat(view);
        String filter = filterOf(view);
        String suffix = filter.isEmpty() ? "" : " · \"" + filter + "\"";
        return Component.text("Spawn — " + cat.label + suffix, NamedTextColor.DARK_AQUA);
    }

    @Override
    public int rows(View view) {
        return 6;
    }

    @Override
    public int pages(Player viewer, View view) {
        int n = filtered(currentCat(view), filterOf(view)).size();
        return Math.max(1, (n + GRID - 1) / GRID);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Cat cat = currentCat(view);
        String filter = filterOf(view);
        List<Material> list = filtered(cat, filter);

        renderTabs(inv, cat, filter);

        int from = view.page() * GRID;
        for (int i = 0; i < GRID; i++) {
            int idx = from + i;
            if (idx >= list.size()) break;
            ItemStack icon = icon(list.get(idx));
            if (icon != null) inv.setItem(CONTENT_START + i, icon);
        }

        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&7" + list.size() + " objet(s)");
        Ui.fillEmpty(inv);
    }

    private void renderTabs(Inventory inv, Cat active, String filter) {
        Cat[] cats = Cat.values();
        for (int i = 0; i < cats.length && i < 5; i++) {
            Cat c = cats[i];
            ItemStack tab = Ui.secondary(c.icon, c.label, "cat", c.name(),
                    c == active ? "&aOnglet actif" : "&7Clique pour ouvrir");
            if (c == active) Ui.glint(tab, true);
            inv.setItem(i, tab);
        }
        // Search (col 6) + reset (col 7).
        inv.setItem(6, Ui.accent(Material.COMPASS, "Rechercher", "search", null,
                filter.isEmpty() ? "&7Aucun filtre actif"
                        : "&eFiltre : &f" + filter,
                "&7Tape un mot en chat pour",
                "&7filtrer la liste."));
        if (!filter.isEmpty()) {
            inv.setItem(7, Ui.destructive(Material.STRUCTURE_VOID,
                    "Reinitialiser le filtre", "reset", null,
                    "&7Retire la recherche active."));
        }
    }

    /* ------------------------------------------------------------- clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!viewer.hasPermission("shinobicore.staff")) return;
        switch (action) {
            case "cat" -> {
                if (value == null) return;
                view.set(S_CAT, value);
                view.setPage(0);
                GuiSounds.navigate(viewer);
                refresh(viewer, view);
            }
            case "reset" -> {
                view.set(S_FILTER, null);
                view.setPage(0);
                GuiSounds.navigate(viewer);
                refresh(viewer, view);
            }
            case "search" -> {
                final Cat cat = currentCat(view);
                core().chatInputs().prompt(viewer,
                        "Recherche d'objet — tape un mot (ex: diamond, wool, sword).",
                        input -> {
                            String f = input == null ? "" : input.trim().toLowerCase();
                            router.screens().open(viewer, this,
                                    Map.of(S_CAT, cat.name(), S_FILTER, f));
                        },
                        () -> router.screens().open(viewer, this,
                                Map.of(S_CAT, cat.name())));
            }
            case "give" -> giveMaterial(viewer, value);
            default -> { }
        }
    }

    private void giveMaterial(Player viewer, String value) {
        if (value == null) return;
        Material m;
        try { m = Material.valueOf(value); }
        catch (IllegalArgumentException ex) { return; }
        if (!m.isItem() || m.isAir()) return;
        int amt = Math.min(Math.max(1, m.getMaxStackSize()), m.isBlock() ? 64 : 1);
        ItemStack give;
        try { give = new ItemStack(m, amt); }
        catch (RuntimeException ex) { GuiSounds.error(viewer); return; }
        var leftover = viewer.getInventory().addItem(give);
        for (ItemStack stuck : leftover.values()) {
            viewer.getWorld().dropItemNaturally(viewer.getLocation(), stuck);
        }
        GuiSounds.select(viewer);
    }

    @Override
    public void onBack(Player viewer, View view) {
        router.openStaffPanel(viewer);
    }

    /* ------------------------------------------------------------ helpers */

    private static Cat currentCat(View view) {
        String s = view.string(S_CAT);
        if (s == null) return Cat.BLOCS;
        try { return Cat.valueOf(s); }
        catch (IllegalArgumentException ex) { return Cat.BLOCS; }
    }

    private static String filterOf(View view) {
        String s = view.string(S_FILTER);
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static List<Material> items() {
        List<Material> cached = ITEMS;
        if (cached != null) return cached;
        List<Material> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || m.isAir() || !m.isItem()) continue;
            out.add(m);
        }
        ITEMS = out;
        return out;
    }

    private static List<Material> filtered(Cat cat, String filter) {
        String q = filter == null ? "" : filter.trim().toLowerCase();
        String qUnderscore = q.replace(' ', '_');
        List<Material> out = new ArrayList<>();
        for (Material m : items()) {
            if (categoryOf(m) != cat) continue;
            if (!q.isEmpty()
                    && !m.name().toLowerCase().contains(qUnderscore)) continue;
            out.add(m);
        }
        return out;
    }

    private static Cat categoryOf(Material m) {
        if (isToolWeaponArmor(m)) return Cat.OUTILS;
        if (isEdibleSafe(m)) return Cat.NOURRITURE;
        if (m.isBlock()) {
            return isDecoOrRedstone(m) ? Cat.DECO : Cat.BLOCS;
        }
        return Cat.DIVERS;
    }

    private static boolean isEdibleSafe(Material m) {
        try { return m.isEdible(); }
        catch (Throwable t) { return false; }
    }

    private static final String[] TOOL_KEYS = {
            "SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE", "BOW", "CROSSBOW",
            "TRIDENT", "SHIELD", "SHEARS", "FISHING_ROD", "FLINT_AND_STEEL",
            "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ELYTRA", "MACE",
            "_HORSE_ARMOR", "TURTLE_HELMET", "CARROT_ON_A_STICK",
            "WARPED_FUNGUS_ON_A_STICK", "BRUSH", "SPYGLASS"
    };

    private static boolean isToolWeaponArmor(Material m) {
        String n = m.name();
        for (String k : TOOL_KEYS) {
            if (n.contains(k)) return true;
        }
        return false;
    }

    private static final String[] DECO_KEYS = {
            "REDSTONE", "REPEATER", "COMPARATOR", "PISTON", "OBSERVER",
            "DISPENSER", "DROPPER", "HOPPER", "LEVER", "BUTTON",
            "PRESSURE_PLATE", "RAIL", "TRIPWIRE", "TARGET", "DAYLIGHT",
            "NOTE_BLOCK", "TORCH", "LANTERN", "CANDLE", "BANNER", "CARPET",
            "GLASS_PANE", "_PANE", "FLOWER", "SAPLING", "LEAVES", "VINE",
            "LADDER", "SIGN", "PAINTING", "FRAME", "_POT", "BED", "CHAIN",
            "_BARS", "PANE", "SCAFFOLDING", "SEA_PICKLE", "AMETHYST_CLUSTER",
            "CORAL", "MUSHROOM", "SPORE", "LICHEN"
    };

    private static boolean isDecoOrRedstone(Material m) {
        String n = m.name();
        for (String k : DECO_KEYS) {
            if (n.contains(k)) return true;
        }
        // Non-solid blocks (torches, wires, plants, rails) read as deco.
        try { return !m.isSolid(); }
        catch (Throwable t) { return false; }
    }

    /** Real material icon carrying the give action + a hint lore. */
    private static ItemStack icon(Material m) {
        try {
            ItemStack icon = new ItemStack(m);
            Ui.action(icon, "give", m.name());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.lore(List.of(Component.text("Clique pour recevoir", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                icon.setItemMeta(meta);
            }
            return icon;
        } catch (RuntimeException ex) {
            return null; // unsafe material — skip it silently
        }
    }
}
