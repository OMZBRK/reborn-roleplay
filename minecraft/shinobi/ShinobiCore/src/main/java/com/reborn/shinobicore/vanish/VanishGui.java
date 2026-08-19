package com.reborn.shinobicore.vanish;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiBuilder;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiLayout;
import com.reborn.shinobicore.character.gui.GuiTitles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * The vanish mode chooser. Opened by {@code /vanish} (self) or
 * {@code /vanish <character>} (on someone). Carries the TARGET player whose
 * vanish is being configured; {@link CinematicListener}-style routing lives in
 * {@link VanishListener}.
 */
public final class VanishGui implements GuiBuilder.HolderInventoryBound {

    private final ShinobiCore plugin;
    private final UUID target;
    private Inventory inventory;

    private int hideAllSlot = -1;
    private int hideExceptSlot = -1;
    private int showExceptSlot = -1;
    private int disableSlot = -1;
    private int closeSlot = -1;

    public VanishGui(ShinobiCore plugin, UUID target) {
        this.plugin = plugin;
        this.target = target;
    }

    @Override public Inventory getInventory() { return inventory; }
    @Override public void bindInventory(Inventory inv) { this.inventory = inv; }

    public UUID target() { return target; }
    public boolean isHideAll(int s) { return s == hideAllSlot; }
    public boolean isHideExcept(int s) { return s == hideExceptSlot; }
    public boolean isShowExcept(int s) { return s == showExceptSlot; }
    public boolean isDisable(int s) { return disableSlot >= 0 && s == disableSlot; }
    public boolean isClose(int s) { return s == closeSlot; }

    public void open(Player viewer) {
        boolean self = viewer.getUniqueId().equals(target);
        boolean vanished = plugin.vanish().isVanished(target);
        String title = self ? "Vanish" : "Vanish : " + targetName();

        GuiBuilder b = GuiBuilder.of(this, 3)
                .title(GuiTitles.framed(title))
                .border();

        int[] row = GuiLayout.triple(1);
        hideAllSlot = row[0];
        hideExceptSlot = row[1];
        showExceptSlot = row[2];

        b.item(hideAllSlot, GuiIcons.destructive(Material.BARRIER,
                "Personne ne peut me voir",
                "&7Invisible pour tout le monde,",
                "&7sauf le staff.",
                vanished && plugin.vanish().modeOf(target) == VanishManager.Mode.HIDE_ALL
                        ? "&a● actif" : "&eCliquer pour activer"));
        b.item(hideExceptSlot, GuiIcons.info(Material.ENDER_EYE,
                "Personne, sauf…",
                "&7Invisible, sauf les",
                "&7personnages que tu choisis.",
                "&eCliquer pour choisir"));
        b.item(showExceptSlot, GuiIcons.primary(Material.PLAYER_HEAD,
                "Tout le monde, sauf…",
                "&7Visible par tous, sauf les",
                "&7personnages que tu choisis.",
                "&eCliquer pour choisir"));

        if (vanished) {
            disableSlot = GuiLayout.home(3);
            b.item(disableSlot, GuiIcons.accent(Material.LIME_DYE,
                    "Désactiver le vanish",
                    "&7Redevenir visible normalement."));
        } else {
            disableSlot = -1;
        }

        closeSlot = GuiLayout.close(3);
        b.item(closeSlot, GuiIcons.closeButton());
        b.fillEmpty();
        b.open(viewer);
    }

    private String targetName() {
        ShinobiCharacter c = plugin.characters().getActive(target);
        if (c != null) return c.name();
        Player p = Bukkit.getPlayer(target);
        return p != null ? p.getName() : "?";
    }
}
