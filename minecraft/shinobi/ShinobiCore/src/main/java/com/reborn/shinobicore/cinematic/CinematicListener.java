package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.util.PdcAccess;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One listener behind the whole cinematic feature: management-GUI clicks,
 * colour-sub-GUI clicks, the editor hotbar interactions, the freeze
 * enforcement (move/interact/drop/damage while a cinematic plays), cleanup
 * on quit / character-switch, and the first-play intro hook.
 */
public final class CinematicListener implements Listener {

    private final ShinobiCore plugin;
    private final CinematicManager manager;
    private final CinematicPlayback playback;

    public CinematicListener(ShinobiCore plugin) {
        this.plugin = plugin;
        this.manager = plugin.cinematics();
        this.playback = manager.playback();
    }

    /* ----------------------------------------------------------- GUI clicks */

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        var holder = e.getInventory().getHolder();

        if (holder instanceof CinematicGui gui) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !e.getClickedInventory().equals(gui.getInventory())) return;
            handleGuiClick(p, gui, e.getSlot(), e.getClick());
            return;
        }
        if (holder instanceof CinematicColorGui cg) {
            e.setCancelled(true);
            if (e.getClickedInventory() == null
                    || !e.getClickedInventory().equals(cg.getInventory())) return;
            handleColorClick(p, cg, e.getSlot());
            return;
        }
        // Lock the player's own inventory while editing / frozen.
        if (manager.isEditing(p.getUniqueId()) || playback.isFrozen(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        var holder = e.getInventory().getHolder();
        if (holder instanceof CinematicGui || holder instanceof CinematicColorGui) {
            e.setCancelled(true);
            return;
        }
        if (e.getWhoClicked() instanceof Player p
                && (manager.isEditing(p.getUniqueId()) || playback.isFrozen(p.getUniqueId()))) {
            e.setCancelled(true);
        }
    }

    private void handleGuiClick(Player p, CinematicGui gui, int slot, ClickType click) {
        String name = gui.cinematicName();
        Cinematic cine = manager.get(name);
        if (cine == null) { p.closeInventory(); return; }

        if (gui.isClose(slot)) { GuiSounds.navigate(p); p.closeInventory(); return; }
        if (gui.isAdd(slot)) {
            CinematicAnchor a = cine.addAnchor();
            manager.save();
            GuiSounds.accept(p);
            beginEdit(p, name, cine.size() - 1, a);
            return;
        }
        if (gui.isTest(slot)) {
            if (cine.isEmpty()) { GuiSounds.error(p); return; }
            p.closeInventory();
            manager.play(p, cine);
            return;
        }

        Integer idx = gui.anchorAt(slot);
        if (idx == null) return;
        switch (click) {
            case LEFT -> {
                CinematicAnchor a = cine.anchor(idx);
                if (a != null) beginEdit(p, name, idx, a);
            }
            case SHIFT_LEFT -> {
                cine.removeAnchor(idx);
                manager.save();
                GuiSounds.destructive(p);
                new CinematicGui(plugin, name).open(p);
            }
            case RIGHT -> {
                if (cine.moveDown(idx)) { manager.save(); GuiSounds.navigate(p); }
                new CinematicGui(plugin, name).open(p);
            }
            case SHIFT_RIGHT -> {
                if (cine.moveUp(idx)) { manager.save(); GuiSounds.navigate(p); }
                new CinematicGui(plugin, name).open(p);
            }
            default -> { }
        }
    }

    private void handleColorClick(Player p, CinematicColorGui cg, int slot) {
        CinematicEditorSession s = manager.editor(cg.playerId());
        if (s == null) { p.closeInventory(); return; }

        if (cg.isBack(slot)) {
            GuiSounds.navigate(p);
            p.closeInventory();
            s.refresh(p);
            return;
        }
        int target = cg.targetAt(slot);
        if (target >= 0) {
            s.setColorTarget(target);
            GuiSounds.navigate(p);
            new CinematicColorGui(plugin, p.getUniqueId()).open(p, s);
            return;
        }
        int ci = cg.colorAt(slot);
        if (ci >= 0) {
            s.applyColor(CinematicColorGui.PALETTE[ci]);
            GuiSounds.accept(p);
            new CinematicColorGui(plugin, p.getUniqueId()).open(p, s);
        }
    }

    private void beginEdit(Player p, String name, int idx, CinematicAnchor anchor) {
        // If an editor is somehow already open, restore the real inventory
        // first so the new session never stashes the temporary tools.
        CinematicEditorSession existing = manager.editor(p.getUniqueId());
        if (existing != null) existing.abort();
        CinematicEditorSession s =
                new CinematicEditorSession(plugin, p.getUniqueId(), name, idx, anchor);
        manager.putEditor(p.getUniqueId(), s);
        s.enter(p);
    }

    /* ----------------------------------------------------- editor hotbar */

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        if (playback.isFrozen(id)) { e.setCancelled(true); return; }
        if (!manager.isEditing(id)) return;

        // Ignore the off-hand duplicate fire so an action runs once.
        if (e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);

        ItemStack item = e.getItem();
        if (item == null) return;
        String action = PdcAccess.getString(item, plugin, CinematicEditorSession.ACTION_KEY);
        if (action == null) return;
        CinematicEditorSession s = manager.editor(id);
        if (s != null) s.handle(action, p);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (manager.isEditing(id) || playback.isFrozen(id)) e.setCancelled(true);
    }

    /* -------------------------------------------------------- freeze guard */

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!playback.isFrozen(id)) return;
        Location lock = playback.lockLocation(id);
        Location to = e.getTo();
        if (lock == null || to == null) return;
        if (to.getX() != lock.getX() || to.getY() != lock.getY() || to.getZ() != lock.getZ()) {
            // Pin position; the per-tick engine teleport re-asserts rotation.
            e.setTo(new Location(lock.getWorld(), lock.getX(), lock.getY(), lock.getZ(),
                    to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && playback.isFrozen(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /* ----------------------------------------------------------- lifecycle */

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        CinematicEditorSession s = manager.editor(id);
        if (s != null) s.abort();
        if (playback.isFrozen(id)) playback.stop(e.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Safety net: a crash / forced stop mid-edit can leave editor tools in
        // a saved inventory. The player can't be editing on join, so strip any
        // lingering tool items (identified by their PDC tag).
        Player p = e.getPlayer();
        if (manager.isEditing(p.getUniqueId())) return;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && PdcAccess.getString(it, plugin,
                    CinematicEditorSession.ACTION_KEY) != null) {
                p.getInventory().setItem(i, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent e) {
        Player p = e.player();
        UUID id = p.getUniqueId();

        // Release any active edit / playback cleanly before the swap settles.
        CinematicEditorSession s = manager.editor(id);
        if (s != null) s.abort();
        if (playback.isFrozen(id)) playback.stop(p, false);

        // First-play intro.
        ShinobiCharacter next = e.next();
        if (next == null || next.introSeen()) return;
        Cinematic intro = manager.introCinematic();
        if (intro == null || intro.isEmpty()) return;
        // Defer so it fires AFTER setActive's stat/inventory/position swap.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || next.introSeen()) return;
            if (playback.isFrozen(id)) return;
            manager.play(p, intro, true, next);
        }, 5L);
    }
}
