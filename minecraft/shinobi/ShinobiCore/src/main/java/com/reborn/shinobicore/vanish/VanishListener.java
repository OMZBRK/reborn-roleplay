package com.reborn.shinobicore.vanish;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.gui.GuiSounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.UUID;

/** GUI routing + visibility re-application for the vanish feature. */
public final class VanishListener implements Listener {

    private final ShinobiCore plugin;
    private final VanishManager vanish;

    public VanishListener(ShinobiCore plugin) {
        this.plugin = plugin;
        this.vanish = plugin.vanish();
    }

    /* ----------------------------------------------------------- GUI clicks */

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player viewer)) return;
        var holder = e.getInventory().getHolder();

        if (holder instanceof VanishGui gui) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !e.getClickedInventory().equals(gui.getInventory())) return;
            handleModeClick(viewer, gui, e.getSlot());
            return;
        }
        if (holder instanceof VanishPickerGui pick) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !e.getClickedInventory().equals(pick.getInventory())) return;
            handlePickerClick(viewer, pick, e.getSlot());
        }
    }

    private void handleModeClick(Player viewer, VanishGui gui, int slot) {
        if (gui.isClose(slot)) { GuiSounds.navigate(viewer); viewer.closeInventory(); return; }
        Player target = Bukkit.getPlayer(gui.target());
        if (target == null || !target.isOnline()) {
            viewer.sendMessage(Component.text("Ce joueur n'est plus connecté.", NamedTextColor.RED));
            viewer.closeInventory();
            return;
        }
        if (gui.isHideAll(slot)) {
            vanish.enable(target, VanishManager.Mode.HIDE_ALL, new HashSet<>());
            GuiSounds.accept(viewer);
            viewer.closeInventory();
            feedback(viewer, target, "invisible pour tous (sauf staff)");
        } else if (gui.isHideExcept(slot)) {
            GuiSounds.navigate(viewer);
            new VanishPickerGui(plugin, gui.target(), VanishManager.Mode.HIDE_EXCEPT).open(viewer);
        } else if (gui.isShowExcept(slot)) {
            GuiSounds.navigate(viewer);
            new VanishPickerGui(plugin, gui.target(), VanishManager.Mode.SHOW_EXCEPT).open(viewer);
        } else if (gui.isDisable(slot)) {
            vanish.disable(target);
            GuiSounds.accept(viewer);
            viewer.closeInventory();
            feedback(viewer, target, "de nouveau visible");
        }
    }

    private void handlePickerClick(Player viewer, VanishPickerGui pick, int slot) {
        if (pick.isClose(slot)) {
            GuiSounds.navigate(viewer);
            new VanishGui(plugin, pick.target()).open(viewer);   // back to mode chooser
            return;
        }
        if (pick.isConfirm(slot)) {
            Player target = Bukkit.getPlayer(pick.target());
            if (target == null || !target.isOnline()) {
                viewer.sendMessage(Component.text("Ce joueur n'est plus connecté.", NamedTextColor.RED));
                viewer.closeInventory();
                return;
            }
            vanish.enable(target, pick.mode(), pick.selected());
            GuiSounds.accept(viewer);
            viewer.closeInventory();
            String what = pick.mode() == VanishManager.Mode.HIDE_EXCEPT
                    ? "invisible sauf " + pick.selected().size() + " personnage(s)"
                    : "visible sauf " + pick.selected().size() + " personnage(s)";
            feedback(viewer, target, what);
            return;
        }
        UUID pl = pick.playerAt(slot);
        if (pl != null) {
            pick.toggle(pl);
            GuiSounds.select(viewer);
            pick.open(viewer);   // re-render with updated selection (same holder)
        }
    }

    private void feedback(Player viewer, Player target, String what) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            viewer.sendMessage(Component.text("Vanish : tu es " + what + ".", NamedTextColor.AQUA));
        } else {
            viewer.sendMessage(Component.text("Vanish appliqué à " + target.getName()
                    + " : " + what + ".", NamedTextColor.AQUA));
        }
    }

    /* ----------------------------------------------------- visibility upkeep */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // A fresh viewer must not see anyone currently vanished from them.
        vanish.applyForViewer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        vanish.clear(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        vanish.apply(p);            // re-hide p from those who can't see p
        vanish.applyForViewer(p);   // re-evaluate what p sees
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            vanish.apply(p);
            vanish.applyForViewer(p);
        });
    }
}
