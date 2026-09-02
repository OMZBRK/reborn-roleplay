package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.util.Keys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes the trail hotbar editor: tool interactions (read by PDC tag, not
 * slot), inventory lock + drop-block while editing, and inventory restore on
 * disconnect.
 */
public final class TrailEditorListener implements Listener {

    private final TrailManager trails;

    public TrailEditorListener(TrailManager trails) {
        this.trails = trails;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!trails.isHotbarEditing(p.getUniqueId())) return;
        if (e.getHand() != EquipmentSlot.HAND) return;   // ignore off-hand duplicate fire
        ItemStack item = e.getItem();
        if (item == null) return;
        String action = Keys.getString(item, TrailEditorSession.ACTION_KEY);
        if (action == null) return;
        e.setCancelled(true);
        TrailEditorSession s = trails.editor(p.getUniqueId());
        if (s != null) s.handle(action, p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && trails.isHotbarEditing(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (trails.isHotbarEditing(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        TrailEditorSession s = trails.editor(e.getPlayer().getUniqueId());
        if (s != null) s.abort();
    }
}
