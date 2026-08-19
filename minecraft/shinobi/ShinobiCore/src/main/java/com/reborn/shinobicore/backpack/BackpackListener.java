package com.reborn.shinobicore.backpack;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;

/**
 * Single entry point for every backpack-system event.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>Locks the player's main inventory slots (9–35) with black
 *       glass when no backpack is equipped, except slot {@link #CENTER_SLOT}
 *       which holds either a {@code BARRIER} ("Vous n'avez pas de Sac")
 *       or — when a backpack is equipped — the backpack icon. Hotbar,
 *       helmet, leggings, boots, and offhand stay fully usable.</li>
 *   <li>Allows backpack items into the chestplate armor slot. On
 *       equip, slot 22 swaps to the backpack icon. On unequip the
 *       icon reverts to the BARRIER.</li>
 *   <li>Click on the centre icon while a backpack is equipped opens
 *       the {@link BackpackGui} chest GUI for the equipped bag.</li>
 *   <li>Right-click a block while holding a backpack drops the bag
 *       on the ground via {@link BackpackEntityManager#place}.</li>
 *   <li>Right-click a placed backpack picks it up to the hotbar
 *       (no-op if the hotbar is full). Sneak + right-click opens
 *       the contents instead.</li>
 *   <li>Closing a backpack chest GUI snapshots its contents back to
 *       the {@link Backpack} record.</li>
 * </ol>
 */
public class BackpackListener implements Listener {

    /** Slot index of the centre cell of the main inventory grid (row 1, col 4 of
     *  the 3-row × 9-col block at slots 9–35; absolute index 22). */
    public static final int CENTER_SLOT = 22;

    /** Range of locked main-inventory slots (inclusive 9, exclusive 36). */
    private static final int LOCK_START = 9;
    private static final int LOCK_END   = 36;

    /** Chestplate armor slot in {@link PlayerInventory} indices. */
    private static final int CHEST_SLOT = 38;

    private final ShinobiCore plugin;
    /** PDC byte stamped onto every glass-pane / barrier filler item so
     *  click handling can quickly recognise lockdown items without
     *  inspecting display names. */
    private final NamespacedKey lockKey;
    private BukkitTask lockdownTicker;

    public BackpackListener(ShinobiCore plugin) {
        this.plugin = plugin;
        this.lockKey = new NamespacedKey(plugin, "backpack_lock");
    }

    /* ============================================================ lifecycle */

    public void start() {
        // Re-pin the lockdown items every second — defence in depth
        // against other plugins or vanilla actions clearing them.
        lockdownTicker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (com.reborn.shinobicore.util.Tps.shouldDefer()) return;
            for (Player p : Bukkit.getOnlinePlayers()) syncLockdown(p);
        }, 20L, 20L);
        // Initial sync for any players already online when the plugin
        // (re-)enables — typical of /reload scenarios.
        for (Player p : Bukkit.getOnlinePlayers()) syncLockdown(p);
    }

    public void stop() {
        if (lockdownTicker != null) {
            try { lockdownTicker.cancel(); } catch (Throwable ignore) {}
            lockdownTicker = null;
        }
        // Clear lockdown items so the inventory looks clean if the
        // plugin is being uninstalled / disabled. We do this best-
        // effort — don't block shutdown on it.
        for (Player p : Bukkit.getOnlinePlayers()) clearLockdown(p);
    }

    /* ============================================================== JOIN  */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // One-tick delay so vanilla join logic finishes settling
        // their inventory before we drop our overlay in.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.getPlayer().isOnline()) syncLockdown(e.getPlayer());
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Strip our filler items so they don't end up persisted on
        // the player NBT (the lockdown is re-applied on join anyway).
        clearLockdown(e.getPlayer());
    }

    /* ====================================================== INVENTORY VIEW */

    /**
     * Re-paint the lockdown overlay onto the player's inventory.
     *
     * <p>Empty locked slots get a black-glass-pane filler. The centre
     * slot gets either:
     * <ul>
     *   <li>the equipped backpack's display item (Sac / Sac Large) when
     *       the chestplate slot holds a backpack, or</li>
     *   <li>a BARRIER block with the "Vous n'avez pas de Sac" lore
     *       otherwise.</li>
     * </ul>
     *
     * <p>Slots that already contain non-lockdown items are left alone —
     * they're "real" items the player put there before lockdown was
     * applied (legacy save data); the click guard prevents them from
     * being moved or extended, but we don't actively destroy them.
     */
    public void syncLockdown(Player p) {
        if (p == null || !p.isOnline()) return;
        PlayerInventory inv = p.getInventory();

        ItemStack chest = inv.getItem(CHEST_SLOT);
        boolean hasBackpack = BackpackItem.isBackpack(plugin, chest);

        for (int slot = LOCK_START; slot < LOCK_END; slot++) {
            ItemStack current = inv.getItem(slot);
            if (slot == CENTER_SLOT) {
                ItemStack want = hasBackpack
                        ? backpackCenterIcon(chest)
                        : barrierIcon();
                if (!isLockdownItem(current) || !sameKind(current, want)) {
                    inv.setItem(slot, want);
                }
                continue;
            }
            // Empty (or already a lockdown filler) → drop the glass.
            if (current == null || current.getType() == Material.AIR
                    || isLockdownItem(current)) {
                inv.setItem(slot, glassPaneFiller());
            }
        }
    }

    /** Strip every lockdown filler item from the player's main inv.
     *  Real items in those slots are left alone so we don't destroy
     *  data the user might want to keep. */
    public void clearLockdown(Player p) {
        if (p == null) return;
        PlayerInventory inv = p.getInventory();
        for (int slot = LOCK_START; slot < LOCK_END; slot++) {
            ItemStack s = inv.getItem(slot);
            if (isLockdownItem(s)) inv.setItem(slot, null);
        }
    }

    /* -------------------------- factories for the lockdown items */

    private ItemStack glassPaneFiller() {
        return tagAsLockdown(plain(Material.BLACK_STAINED_GLASS_PANE,
                Component.text(" ")));
    }

    private ItemStack barrierIcon() {
        return tagAsLockdown(plain(Material.BARRIER,
                Component.text("Vous n'avez pas de Sac",
                                NamedTextColor.RED, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false)));
    }

    /** Center-slot visual when a backpack is equipped. Cloned from
     *  the chestplate item so display name + size match, then re-
     *  tagged so the click guard treats it as lockdown UI. */
    private ItemStack backpackCenterIcon(ItemStack chestItem) {
        ItemStack copy = chestItem.clone();
        ItemMeta m = copy.getItemMeta();
        if (m != null) {
            m.lore(java.util.List.of(
                    Component.text("Clique pour ouvrir le sac.",
                                    NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)));
            copy.setItemMeta(m);
        }
        return tagAsLockdown(copy);
    }

    private ItemStack plain(Material mat, Component name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(name.decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack tagAsLockdown(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(lockKey,
                    PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(m);
        }
        return it;
    }

    private boolean isLockdownItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer()
                .has(lockKey, PersistentDataType.BYTE);
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        return a.getType() == b.getType();
    }

    /* ============================================ INVENTORY CLICK GUARD */

    /**
     * The locked-slot guard. Fires on every inventory click — handles
     * three things:
     * <ol>
     *   <li>Click on slot 22 (centre) while wearing a backpack →
     *       open the backpack chest GUI.</li>
     *   <li>Click on any other locked slot (9–35 except 22) → cancel
     *       so the player can't move items in or out.</li>
     *   <li>Click on chest armor slot 38 swapping a backpack in/out →
     *       allow + schedule a +1 tick re-sync of the centre icon.</li>
     * </ol>
     *
     * <p>Drag events get the same treatment via {@link #onDrag}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;

        // Equip via chest armor slot: vanilla Minecraft only accepts
        // chestplate-typed items in slot 38, so a backpack item
        // (LEATHER / NETHERITE_INGOT) gets rejected without our help.
        // Intercept any click on the chest slot where the cursor or
        // current item is a backpack, and do the swap manually.
        if (e.getClickedInventory().getType() == InventoryType.PLAYER
                && e.getSlot() == CHEST_SLOT) {
            ItemStack cursor = e.getCursor();
            ItemStack current = e.getCurrentItem();
            boolean cursorIsBag  = BackpackItem.isBackpack(plugin, cursor);
            boolean currentIsBag = BackpackItem.isBackpack(plugin, current);
            if (cursorIsBag || currentIsBag) {
                e.setCancelled(true);
                // Swap: cursor ↔ chest slot.
                ItemStack newCursor = current == null
                        || current.getType() == Material.AIR ? null : current;
                ItemStack newChest  = cursor == null
                        || cursor.getType() == Material.AIR ? null : cursor;
                p.getInventory().setItem(CHEST_SLOT, newChest);
                p.setItemOnCursor(newCursor);
                scheduleResync(p);
                return;
            }
        }

        // Shift-click a backpack inside the player inventory.
        if ((e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT)
                && e.getClickedInventory().getType() == InventoryType.PLAYER
                && BackpackItem.isBackpack(plugin, e.getCurrentItem())) {

            // From the chest slot → unequip to first free hotbar slot.
            // Matches the spec: "If the player remove the backpack
            // for the chest, it end up in the hotbar".
            if (e.getSlot() == CHEST_SLOT) {
                int free = firstFreeHotbarSlot(p);
                e.setCancelled(true);
                if (free >= 0) {
                    ItemStack bag = e.getCurrentItem();
                    p.getInventory().setItem(CHEST_SLOT, null);
                    p.getInventory().setItem(free, bag);
                }
                // No-op when hotbar full — bag stays equipped, vanilla
                // shift would have tried main inv which our lockdown
                // refuses anyway.
                scheduleResync(p);
                return;
            }

            // From the hotbar (or anywhere outside the chest slot) →
            // shift-click equips into the chest slot if free. Vanilla
            // would try to push it into main inv which our lockdown
            // refuses.
            ItemStack chest = p.getInventory().getItem(CHEST_SLOT);
            if (chest == null || chest.getType() == Material.AIR) {
                e.setCancelled(true);
                p.getInventory().setItem(CHEST_SLOT, e.getCurrentItem());
                p.getInventory().setItem(e.getSlot(), null);
                scheduleResync(p);
                return;
            }
        }

        // Centre-slot click: open backpack GUI if equipped.
        if (e.getClickedInventory().getType() == InventoryType.PLAYER
                && e.getSlot() == CENTER_SLOT) {
            ItemStack chest = p.getInventory().getItem(CHEST_SLOT);
            if (BackpackItem.isBackpack(plugin, chest)) {
                e.setCancelled(true);
                Backpack bp = plugin.backpacks().getOrAdopt(chest);
                if (bp != null) BackpackGui.open(p, plugin, bp);
            } else {
                // No backpack — just block the click so the BARRIER
                // doesn't get picked up.
                e.setCancelled(true);
            }
            return;
        }

        // Lockdown filler items can't be moved, full stop.
        if (isLockdownItem(e.getCurrentItem()) || isLockdownItem(e.getCursor())) {
            e.setCancelled(true);
            return;
        }

        // Block any modification of the locked main-inventory range
        // when the click lands inside the player inventory.
        if (e.getClickedInventory().getType() == InventoryType.PLAYER) {
            int slot = e.getSlot();
            if (slot >= LOCK_START && slot < LOCK_END) {
                e.setCancelled(true);
                return;
            }
        }

        // Shift-click from elsewhere into the player inventory:
        // refuse if the destination would be a locked slot.
        // (Shift-click selects the first available main-inv slot,
        // which would normally be slot 9.)
        if (e.getClick() == ClickType.SHIFT_LEFT
                || e.getClick() == ClickType.SHIFT_RIGHT
                || e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // If the clicked inventory is OUTSIDE the player inv
            // (e.g. an open chest) and the item being shift-clicked
            // would move into the player's main slots, allow it
            // ONLY into hotbar or armor — vanilla shift-click order
            // already prefers hotbar for armor pieces, but we need
            // to refuse main-storage destinations.
            // Simplest implementation: tag the click for a follow-up
            // re-sync that strips any items now sitting in locked
            // slots. The guard above already cancels direct clicks.
            scheduleResync(p);
        }

        // Equip / unequip detection: any click that touches the chest
        // slot might add/remove a backpack — re-sync the centre icon
        // on the next tick.
        if (e.getSlot() == CHEST_SLOT
                || e.getHotbarButton() != -1
                || e.getAction() == InventoryAction.HOTBAR_SWAP) {
            scheduleResync(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Set<Integer> slots = e.getRawSlots();
        // The raw slot index for the player inventory while it's the
        // bottom inventory of an InventoryView differs from the
        // PlayerInventory absolute index — use getInventorySlots and
        // check via the converted view.
        // Pragmatic check: drop the drag if any target lands in the
        // locked range when mapped through the InventoryView.
        for (int raw : slots) {
            int converted = e.getView().convertSlot(raw);
            if (e.getView().getInventory(raw) instanceof PlayerInventory) {
                if (converted >= LOCK_START && converted < LOCK_END) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        scheduleResync(p);
    }

    private void scheduleResync(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) syncLockdown(p);
        });
    }

    /* ============================================== BACKPACK GUI CLOSE */

    /** Snapshot the BackpackGui's live inventory back onto the
     *  {@link Backpack} record when the viewer closes the chest. The
     *  GUI uses the same ItemStack references as the backpack array
     *  so most edits are reflected live, but shift-take and similar
     *  paths replace slot contents — we re-snapshot here for safety. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BackpackGui gui)) return;
        Inventory inv = e.getInventory();
        ItemStack[] dst = gui.backpack().contents();
        int limit = Math.min(dst.length, inv.getSize());
        for (int i = 0; i < limit; i++) dst[i] = inv.getItem(i);
        for (int i = limit; i < dst.length; i++) dst[i] = null;
        plugin.backpacks().save();
    }

    /* =================================================== BLOCK INTERACT */

    /**
     * Right-click block while holding a backpack → drop it on the
     * floor at the clicked face. Sets up the ItemDisplay + Interaction
     * via {@link BackpackEntityManager#place}, removes one backpack
     * item from the player's hand, and refreshes the lockdown overlay
     * (since the chest slot may have just emptied).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlaceFromHand(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack inHand = e.getItem();
        if (!BackpackItem.isBackpack(plugin, inHand)) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Backpack pack = plugin.backpacks().getOrAdopt(inHand);
        if (pack == null) return;

        e.setCancelled(true);
        // Place on top of the clicked face; if the player aimed at
        // the side of a block, getRelative gives us the adjacent
        // slab/air position on that face.
        var loc = clicked.getRelative(e.getBlockFace()).getLocation();
        plugin.backpackEntities().place(pack, loc, inHand);

        // Consume one bag from the held stack — backpacks always
        // stack to 1 in practice but be defensive.
        if (inHand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        scheduleResync(p);
    }

    /* =================================================== ENTITY INTERACT */

    /**
     * Right-click on a placed backpack's Interaction entity. Without
     * sneak: pick the bag up into the player's hotbar. With sneak:
     * open the contents GUI.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity ent = e.getRightClicked();
        Byte tagged = ent.getPersistentDataContainer()
                .get(plugin.backpackEntities().interactionTagKey(),
                        PersistentDataType.BYTE);
        if (tagged == null) return;
        String idRaw = ent.getPersistentDataContainer()
                .get(plugin.backpackEntities().backpackIdKey(),
                        PersistentDataType.STRING);
        if (idRaw == null) return;
        UUID backpackId;
        try { backpackId = UUID.fromString(idRaw); }
        catch (IllegalArgumentException ex) { return; }

        e.setCancelled(true);
        Player p = e.getPlayer();
        Backpack pack = plugin.backpacks().get(backpackId);
        if (pack == null) {
            p.sendActionBar(Component.text("Sac introuvable.", NamedTextColor.RED));
            plugin.backpackEntities().remove(backpackId);
            return;
        }

        if (p.isSneaking()) {
            // Open the contents in shared GUI mode — multiple players
            // sneak-clicking the same bag get the same Inventory.
            BackpackGui.open(p, plugin, pack);
            return;
        }

        // Plain right-click: pickup. Refuse if hotbar is full.
        int free = firstFreeHotbarSlot(p);
        if (free < 0) {
            p.sendActionBar(Component.text(
                    "Hotbar pleine — libère un emplacement avant de prendre le sac.",
                    NamedTextColor.YELLOW));
            return;
        }
        ItemStack item = BackpackItem.create(plugin, pack.id(), pack.size());
        p.getInventory().setItem(free, item);
        plugin.backpackEntities().remove(backpackId);
        p.sendActionBar(Component.text(
                pack.size().displayName() + " ramassé.", NamedTextColor.GREEN));
        scheduleResync(p);
    }

    /** First empty hotbar slot (0..8), or -1 if all are occupied. */
    private static int firstFreeHotbarSlot(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType() == Material.AIR) return i;
        }
        return -1;
    }
}
