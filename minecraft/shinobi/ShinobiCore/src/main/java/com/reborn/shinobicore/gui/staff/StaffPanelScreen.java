package com.reborn.shinobicore.gui.staff;

import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiLayout;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /staff} hub. One button per section; a button is hidden when
 * the viewer lacks its permission. The spawn menu + custom-item browser
 * are the centrepiece; the remaining sections are thin
 * {@code performCommand} shortcuts into existing subsystems, kept simple
 * on purpose. A small "Mécaniques" sub-section holds command shortcuts.
 *
 * <p>Gated on {@code shinobicore.staff}.
 */
public final class StaffPanelScreen extends CoreScreen {

    private static final String S_SECTION = "section"; // null = root, "meca"

    public StaffPanelScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player viewer) {
        if (!viewer.hasPermission("shinobicore.staff")) {
            GuiSounds.error(viewer);
            return;
        }
        router.screens().open(viewer, this, Map.of());
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        return "meca".equals(view.string(S_SECTION))
                ? Component.text("Staff — Mecaniques", NamedTextColor.RED)
                : Component.text("Panneau Staff", NamedTextColor.RED);
    }

    @Override
    public int rows(View view) {
        return "meca".equals(view.string(S_SECTION)) ? 3 : 5;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        if ("meca".equals(view.string(S_SECTION))) renderMeca(viewer, inv);
        else renderRoot(viewer, inv);
    }

    private void renderRoot(Player viewer, Inventory inv) {
        Ui.frame(inv);
        inv.setItem(4, GuiIcons.accent(Material.NETHER_STAR, "Panneau Staff",
                "&7Outils de gestion in-game."));

        List<ItemStack> buttons = new ArrayList<>();
        if (viewer.hasPermission("shinobicore.staff")) {
            buttons.add(Ui.primary(Material.CHEST, "Blocs & Items", "spawn", null,
                    "&7Palette de spawn facon GMod.",
                    "&eClique pour ouvrir"));
            buttons.add(Ui.secondary(Material.NAME_TAG, "Items custom", "customitems", null,
                    "&7Objets custom du plugin.",
                    "&eClique pour ouvrir"));
        }
        if (viewer.hasPermission("shinobicore.character.admin")) {
            buttons.add(Ui.accent(Material.PLAYER_HEAD, "Persos", "persos", null,
                    "&7Liste tes personnages (/character list).",
                    "&eClique pour lancer"));
        }
        if (viewer.hasPermission("shinobicore.staff")) {
            buttons.add(Ui.secondary(Material.REDSTONE, "Mecaniques", "meca", null,
                    "&7Raccourcis de commandes.",
                    "&eClique pour ouvrir"));
        }
        if (viewer.hasPermission("shinobicore.vanish")) {
            buttons.add(Ui.accent(Material.ENDER_EYE, "Moderation", "moderation", null,
                    "&7Bascule l'invisibilite (/vanish).",
                    "&eClique pour lancer"));
        }
        if (viewer.hasPermission("shinobicore.cinematic")) {
            buttons.add(Ui.secondary(Material.FILLED_MAP, "Monde", "monde", null,
                    "&7Cinematiques (/cinematic list).",
                    "&eClique pour lancer"));
        }
        if (viewer.hasPermission("shinobicore.staff")) {
            buttons.add(Ui.primary(Material.DIAMOND_PICKAXE, "Mode construction", "build", null,
                    "&7Bascule le mode creatif builder.",
                    "&7Ton inventaire RP est protege.",
                    "&eClique pour basculer"));
        }

        placeCentered(inv, buttons);
        Ui.footer(inv, false, 0, 1);
        Ui.fillEmpty(inv);
    }

    /** Center the buttons across rows 1-2 (one row if it fits). */
    private void placeCentered(Inventory inv, List<ItemStack> buttons) {
        if (buttons.isEmpty()) return;
        if (buttons.size() <= 7) {
            int[] slots = GuiLayout.centeredRow(2, buttons.size());
            for (int i = 0; i < slots.length; i++) inv.setItem(slots[i], buttons.get(i));
            return;
        }
        int half = (buttons.size() + 1) / 2;
        int[] r1 = GuiLayout.centeredRow(1, half);
        int[] r2 = GuiLayout.centeredRow(2, buttons.size() - half);
        for (int i = 0; i < r1.length; i++) inv.setItem(r1[i], buttons.get(i));
        for (int i = 0; i < r2.length; i++) inv.setItem(r2[i], buttons.get(half + i));
    }

    private void renderMeca(Player viewer, Inventory inv) {
        Ui.frame(inv);
        List<ItemStack> buttons = new ArrayList<>();
        buttons.add(Ui.secondary(Material.ARMOR_STAND, "Cibles d'entrainement", "cmd", "dummy list",
                "&7Ouvre /dummy list.",
                "&eClique pour lancer"));
        if (viewer.hasPermission("shinobicore.admin")) {
            buttons.add(Ui.secondary(Material.COMPARATOR, "Recharger la config", "cmd", "sc reload",
                    "&7Ouvre /sc reload.",
                    "&eClique pour lancer"));
        }
        int[] slots = GuiLayout.centeredRow(1, buttons.size());
        for (int i = 0; i < slots.length; i++) inv.setItem(slots[i], buttons.get(i));
        Ui.footer(inv, true, 0, 1);
        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------- clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!viewer.hasPermission("shinobicore.staff")) return;
        switch (action) {
            case "spawn" -> {
                GuiSounds.navigate(viewer);
                router.openSpawnMenu(viewer);
            }
            case "customitems" -> {
                GuiSounds.navigate(viewer);
                router.openCustomItems(viewer);
            }
            case "persos" -> {
                if (!viewer.hasPermission("shinobicore.character.admin")) return;
                viewer.closeInventory();
                viewer.performCommand("character list");
            }
            case "moderation" -> {
                if (!viewer.hasPermission("shinobicore.vanish")) return;
                viewer.closeInventory();
                viewer.performCommand("vanish");
            }
            case "monde" -> {
                if (!viewer.hasPermission("shinobicore.cinematic")) return;
                viewer.closeInventory();
                viewer.performCommand("cinematic list");
            }
            case "build" -> {
                viewer.closeInventory();
                if (core().staffBuild() != null) core().staffBuild().toggle(viewer);
            }
            case "meca" -> {
                view.set(S_SECTION, "meca");
                view.setPage(0);
                GuiSounds.navigate(viewer);
                refresh(viewer, view);
            }
            case "cmd" -> {
                if (value == null) return;
                viewer.closeInventory();
                viewer.performCommand(value);
            }
            default -> { }
        }
    }

    @Override
    public void onBack(Player viewer, View view) {
        if ("meca".equals(view.string(S_SECTION))) {
            view.set(S_SECTION, null);
            view.setPage(0);
            refresh(viewer, view);
        } else {
            viewer.closeInventory();
        }
    }
}
