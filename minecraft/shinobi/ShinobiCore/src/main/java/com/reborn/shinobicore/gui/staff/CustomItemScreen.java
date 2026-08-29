package com.reborn.shinobicore.gui.staff;

import com.reborn.shinobicore.ItemGiveRegistry;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Paginated browser over {@link ItemGiveRegistry#tokens()} — every
 * custom item the plugin (and its addons) ship. A click reuses the
 * registry's own give path to hand the item to the viewer.
 *
 * <p>Gated on {@code shinobicore.staff}.
 */
public final class CustomItemScreen extends CoreScreen {

    private static final int GRID = 45; // rows 0..4, footer on row 5

    public CustomItemScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player viewer) {
        if (!viewer.hasPermission("shinobicore.staff")) {
            GuiSounds.error(viewer);
            return;
        }
        router.screens().open(viewer, this, Map.of());
    }

    private List<String> tokens() {
        return core().itemGive().tokens();
    }

    @Override
    public Component title(Player viewer, View view) {
        return Component.text("Items custom", NamedTextColor.GOLD);
    }

    @Override
    public int rows(View view) {
        return 6;
    }

    @Override
    public int pages(Player viewer, View view) {
        return Math.max(1, (tokens().size() + GRID - 1) / GRID);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        List<String> tokens = tokens();
        int from = view.page() * GRID;
        for (int i = 0; i < GRID; i++) {
            int idx = from + i;
            if (idx >= tokens.size()) break;
            String token = tokens.get(idx);
            inv.setItem(i, Ui.accent(Material.NAME_TAG, token, "give", token,
                    "&7Objet custom du plugin",
                    "&eClique pour recevoir"));
        }
        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&7" + tokens.size() + " objet(s) custom");
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!viewer.hasPermission("shinobicore.staff")) return;
        if (!"give".equals(action) || value == null) return;
        ItemGiveRegistry.GiveResult res = core().itemGive().give(viewer, value);
        if (res == null || res.item() == null) {
            GuiSounds.error(viewer);
            return;
        }
        var leftover = viewer.getInventory().addItem(res.item());
        for (ItemStack stuck : leftover.values()) {
            viewer.getWorld().dropItemNaturally(viewer.getLocation(), stuck);
        }
        viewer.sendMessage(Component.text(
                res.label() + " ajoute a ton inventaire.", NamedTextColor.GREEN));
        GuiSounds.select(viewer);
    }

    @Override
    public void onBack(Player viewer, View view) {
        router.openStaffPanel(viewer);
    }
}
