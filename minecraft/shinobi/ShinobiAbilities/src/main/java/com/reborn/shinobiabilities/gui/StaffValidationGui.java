package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobiabilities.techniques.LearningMinigame;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Validations (Suivi) — the staff queue. Each pending learner renders
 * as a head: left-click validates, right-click refuses. The /sa
 * valider|refuser commands stay as console fallback.
 */
public final class StaffValidationGui extends AbilityScreen {

    private final LearningMinigame minigame;

    public StaffValidationGui(GuiRouter router, LearningMinigame minigame) {
        super(router);
        this.minigame = minigame;
    }

    public void open(Player p) {
        router.screens().open(p, this, Map.of());
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed("Validations — Suivi");
    }

    @Override
    public int rows(View view) { return 6; }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        List<LearningMinigame.PendingSuivi> pending = minigame.pendingValidations();
        if (pending.isEmpty()) {
            inv.setItem(22, GuiIcons.info(Material.BELL,
                    "Aucune validation en attente",
                    "&7Les demandes de Suivi apparaîtront ici",
                    "&7dès qu'un joueur cliquera son parchemin."));
        } else {
            int slot = 0;
            for (LearningMinigame.PendingSuivi req : pending) {
                if (slot >= 45) break;
                ItemStack head = GuiIcons.head(
                        Bukkit.getOfflinePlayer(req.playerId()),
                        req.playerName(),
                        "&7veut apprendre :",
                        "&b" + req.abilityName(),
                        "",
                        "&aClic gauche : valider",
                        "&cClic droit : refuser");
                inv.setItem(slot++, Ui.action(head, "suivi", req.playerId().toString()));
            }
        }
        Ui.footer(inv, true, 0, 1);
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!action.equals("suivi")) return;
        if (!viewer.hasPermission("shinobiabilities.staff")) return;

        UUID targetId;
        try { targetId = UUID.fromString(value); }
        catch (IllegalArgumentException ex) { return; }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            GuiSounds.error(viewer);
            refresh(viewer, view); // the learner logged off
            return;
        }
        boolean approve = event.isLeftClick();
        boolean handled = approve
                ? minigame.validate(target)
                : minigame.refuse(target);
        if (handled) {
            if (approve) GuiSounds.accept(viewer); else GuiSounds.destructive(viewer);
        } else {
            GuiSounds.error(viewer);
        }
        refresh(viewer, view);
    }
}
