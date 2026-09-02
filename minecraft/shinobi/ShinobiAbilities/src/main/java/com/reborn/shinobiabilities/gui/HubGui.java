package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiLayout;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.util.Players;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * ◆ Menu Shinobi ◆ — the {@code /menu} hub. Every ability screen hangs
 * off this one; staff/admin entries appear only with the permission.
 */
public final class HubGui extends AbilityScreen {

    private final AbilityRegistry registry;

    public HubGui(GuiRouter router, AbilityRegistry registry) {
        super(router);
        this.registry = registry;
    }

    public void open(Player p, ShinobiCharacter c) {
        router.screens().open(p, this, Map.of());
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Menu Shinobi",
                Players.active(router.core().characters(), viewer));
    }

    @Override
    public int rows(View view) { return 5; }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter c = Players.active(router.core().characters(), viewer);
        int known = 0;
        if (c != null) {
            for (Ability a : registry.all().values()) {
                if (c.knowsAbility(a.id())) known++;
            }
        }

        Ui.frame(inv);
        int[] r1 = GuiLayout.triple(1);
        inv.setItem(r1[0], Ui.coloured(Material.KNOWLEDGE_BOOK, "Mes Techniques",
                net.kyori.adventure.text.format.NamedTextColor.AQUA, "known", null,
                "&b" + known + " technique(s) connue(s)",
                "&7Liste détaillée de tes acquis.",
                "&eClique pour ouvrir"));
        // Catalogue is a STAFF distribution tool, not a player encyclopedia.
        if (viewer.hasPermission("shinobiabilities.staff")) {
            inv.setItem(r1[1], Ui.accent(Material.CHISELED_BOOKSHELF, "Catalogue (staff)",
                    "catalogue", null,
                    "&7Toutes les voies et leurs techniques.",
                    "&7Clic : prends le parchemin. Maj+clic :",
                    "&7remets-le à un personnage connecté.",
                    "&eRéservé au staff"));
        }
        inv.setItem(r1[2], Ui.primary(Material.NAME_TAG, "Mes Liaisons",
                "bindings", null,
                "&7Assigne tes jutsu aux 5 slots de",
                "&7chaque canal (Rouleau, Katana…).",
                "&eClique pour éditer"));

        int[] r2 = GuiLayout.triple(2);
        // Mobilité menu removed — mobility abilities are always enabled.
        // Encyclopédie removed from the menu — it's now a given item
        // (academy-level book), not a menu entry.
        if (viewer.hasPermission("shinobiabilities.staff")) {
            inv.setItem(r2[2], Ui.accent(Material.BELL, "Validations (Suivi)",
                    "validations", null,
                    "&7Les apprentissages en attente",
                    "&7d'une validation du staff.",
                    "&eClique pour traiter"));
        }
        if (viewer.hasPermission("shinobiabilities.admin")) {
            inv.setItem(GuiLayout.center(3), Ui.destructive(Material.COMMAND_BLOCK,
                    "Administration", "admin", null,
                    "&7Gérer les techniques, liaisons et",
                    "&7cooldowns de n'importe quel personnage.",
                    "&eClique pour ouvrir"));
        }

        Ui.footer(inv, false, 0, 1);
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter c = router.activeOrWarn(viewer);
        if (c == null) { viewer.closeInventory(); return; }

        switch (action) {
            case "known" -> { GuiSounds.navigate(viewer); router.openKnown(viewer); }
            case "catalogue" -> {
                if (viewer.hasPermission("shinobiabilities.staff")) {
                    GuiSounds.navigate(viewer);
                    router.openCatalogueBrowse(viewer);
                }
            }
            case "bindings" -> { GuiSounds.navigate(viewer); router.openChannelPicker(viewer, c); }
            case "validations" -> {
                if (viewer.hasPermission("shinobiabilities.staff")) {
                    GuiSounds.navigate(viewer);
                    router.openStaffValidation(viewer);
                }
            }
            case "admin" -> {
                if (viewer.hasPermission("shinobiabilities.admin")) {
                    GuiSounds.navigate(viewer);
                    router.openAdmin(viewer);
                }
            }
            default -> { }
        }
    }
}
