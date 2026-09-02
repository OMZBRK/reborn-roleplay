package com.reborn.shinobiabilities.mobility.training;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.reborn.shinobiabilities.util.Keys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes the parkour hotbar editor (tool clicks read by PDC tag, with the
 * left/right click forwarded) AND the three reaction-press inputs during a run:
 * sneak ({@link PlayerToggleSneakEvent}), space ({@link PlayerJumpEvent}) and
 * left-click ({@link PlayerAnimationEvent}). Normal mobility / quick-cast stand
 * down during a run via the {@code isRunning} hooks elsewhere; this listener
 * only reads the press and feeds it to {@link ParkourRunner#onPress}.
 */
public final class ParkourListener implements Listener {

    private final ParkourManager manager;
    private final ParkourRunner runner;

    public ParkourListener(ParkourManager manager, ParkourRunner runner) {
        this.manager = manager;
        this.runner = runner;
    }

    /* ----------------------------------------------- editor + interaction */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (manager.isEditing(p.getUniqueId())) {
            if (e.getHand() != EquipmentSlot.HAND) return;   // ignore off-hand duplicate
            ItemStack item = e.getItem();
            if (item == null) return;
            String action = Keys.getString(item, ParkourEditorSession.ACTION_KEY);
            if (action == null) return;
            e.setCancelled(true);
            boolean left = e.getAction() == Action.LEFT_CLICK_AIR
                    || e.getAction() == Action.LEFT_CLICK_BLOCK;
            ParkourEditorSession s = manager.editor(p.getUniqueId());
            if (s != null) s.handle(action, left, p);
            return;
        }
        if (runner.isRunning(p)) {
            e.setCancelled(true);   // no world interaction mid-run (press read via swing)
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof ParkourPickerGui.Holder h) {
            e.setCancelled(true);
            ItemStack it = e.getCurrentItem();
            if (it != null) ParkourPickerGui.handle(p, h, it);
            return;
        }
        if (manager.isEditing(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (manager.isEditing(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ParkourEditorSession s = manager.editor(e.getPlayer().getUniqueId());
        if (s != null) s.abort();
        runner.stop(e.getPlayer(), false);
    }

    /* ----------------------------------------------------- reaction presses */

    @EventHandler(priority = EventPriority.HIGH)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && runner.isRunning(e.getPlayer())) {
            runner.onPress(e.getPlayer(), ParkourAnchor.Key.SNEAK);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (runner.isRunning(p)) {
            runner.onPress(p, ParkourAnchor.Key.SPACE);
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        if (runner.isRunning(e.getPlayer())) {
            runner.onPress(e.getPlayer(), ParkourAnchor.Key.LEFT_CLICK);
        }
    }
}
