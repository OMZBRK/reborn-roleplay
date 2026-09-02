package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.event.KoEnterEvent;
import com.reborn.shinobicore.util.Players;
import org.bukkit.Sound;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Predicate;

/**
 * Central jutsu event router.
 *
 * <h2>F-key ownership</h2>
 * This listener owns F at {@link EventPriority#LOWEST} with
 * {@code ignoreCancelled = false} so it beats the mobility handlers and
 * any other swap-hands consumer. Rules:
 * <ul>
 *   <li>Picker open → F closes it. F always wins when the picker is open.</li>
 *   <li>Sneak + F on a JutsuItem → edit GUI directly (skip picker).</li>
 *   <li>F on a JutsuItem → open picker (unless riding a trail — the
 *       mobility handler at NORMAL consumes that F as ride-cancel).</li>
 * </ul>
 *
 * <h2>Right-click suppression</h2>
 * JutsuItems are inert: right-click is denied for BOTH hands at LOWEST.
 * ENDER_EYE additionally gets an {@link EntitySpawnEvent} guard that
 * cancels {@link EnderSignal} spawns near a PUPILLES holder, because the
 * interact cancellation doesn't always reach the entity-spawn path.
 */
public final class JutsuListener implements Listener {

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final JutsuHotbarManager hotbar;
    private final JutsuExecutionManager execution;
    private final JutsuEditGui editGui;
    /** True when another system (trail ride) should own this F press. */
    private final Predicate<Player> fKeyDeferred;
    /** True while a learning minigame owns the clicks (no casting). */
    private final Predicate<Player> castBlocked;

    public JutsuListener(JavaPlugin plugin, CoreServices core,
                         JutsuHotbarManager hotbar,
                         JutsuExecutionManager execution,
                         JutsuEditGui editGui,
                         Predicate<Player> fKeyDeferred,
                         Predicate<Player> castBlocked) {
        this.plugin = plugin;
        this.core = core;
        this.hotbar = hotbar;
        this.execution = execution;
        this.editGui = editGui;
        this.fKeyDeferred = fKeyDeferred;
        this.castBlocked = castBlocked;
    }

    /* ------------------------------------------------------------- F key */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();

        // F always wins when the picker is open.
        if (hotbar.isOpen(p)) {
            event.setCancelled(true);
            hotbar.close(p, false);
            return;
        }

        ItemStack main = p.getInventory().getItemInMainHand();
        JutsuItemType type = JutsuItems.typeOf(main);
        if (type == null) return;

        // Riding a trail / grapple-style session: let the NORMAL-priority
        // mobility handler consume this F as its cancel. A learning
        // minigame also locks the picker out.
        if (fKeyDeferred.test(p) || castBlocked.test(p)) return;

        event.setCancelled(true);

        ShinobiCharacter c = Players.activeOrWarn(core.characters(), p);
        if (c == null) return;
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return;

        if (p.isSneaking()) {
            editGui.open(p, type, c);
        } else {
            hotbar.open(p, type, c.id());
        }
    }

    /* ----------------------------------------------- right-click handling */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        boolean pickerOpen = hotbar.isOpen(p);

        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            // While the picker is open every right-click is ours: deny the
            // vanilla use (icons like FIRE_CHARGE must never ignite) and
            // route the trigger once (main hand only — the event fires for
            // both hands).
            if (pickerOpen) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                if (event.getHand() == EquipmentSlot.HAND) {
                    execution.onRightClick(p);
                }
                return;
            }

            // Picker closed: JutsuItems are inert keys — suppress use for
            // BOTH hands (the ENDER_EYE would otherwise fire an EnderSignal,
            // the swords would interact with blocks, etc.).
            ItemStack inHand = event.getHand() == EquipmentSlot.OFF_HAND
                    ? p.getInventory().getItemInOffHand()
                    : p.getInventory().getItemInMainHand();
            if (JutsuItems.isJutsuItem(inHand) || JutsuItems.isPickerIcon(inHand)) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            }
        }
    }

    /** ENDER_EYE belt-and-braces: PaperInteract cancellation doesn't
     *  always reach the entity-spawn path, so kill stray EnderSignals
     *  near any PUPILLES holder. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof EnderSignal)) return;
        for (Player p : event.getEntity().getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(event.getLocation()) > 8 * 8) continue;
            if (JutsuItems.holdsType(p, JutsuItemType.PUPILLES)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /* ----------------------------------------------------- fire triggers */

    /** Left-click via PlayerAnimationEvent — fires reliably in the void,
     *  unlike PlayerInteractEvent LEFT_CLICK_AIR on inert items. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        if (castBlocked.test(event.getPlayer())) return; // minigame owns clicks
        execution.onLeftClick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneakForHold(PlayerToggleSneakEvent event) {
        execution.onSneak(event.getPlayer(), event.isSneaking());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHeldChange(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!hotbar.isOpen(p)) return;
        int slot = event.getNewSlot();
        if (slot >= 6) {              // filler — refuse the move
            event.setCancelled(true);
            return;
        }
        execution.onSlotChange(p);
        if (slot >= 1 && slot <= JutsuBindingStore.SLOTS) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.9f);
        }
    }

    /* --------------------------------------------- picker integrity guards */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (hotbar.isOpen(event.getPlayer())
                || JutsuItems.isPickerIcon(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (hotbar.isOpen(p)) {
            // Any shuffle could land real items in hotbar slots that the
            // close-restore will overwrite — block everything.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player p && hotbar.isOpen(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (hotbar.isOpen(event.getPlayer())
                || JutsuItems.isPickerIcon(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------- force closes */

    /** LOWEST so the hotbar is restored BEFORE ShinobiCore's lifecycle
     *  listener captures the quitting player's inventory. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        hotbar.close(p, true);
        execution.clearState(p);
    }

    /** Death while the picker is open: the drop list was built from the
     *  live (picker) hotbar. Scrub the icons out and inject the real
     *  snapshot items so nothing dupes or vanishes. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        JutsuHotbarManager.Session s = hotbar.removeSessionRaw(p);
        execution.clearState(p);
        if (s == null) return;
        event.getDrops().removeIf(JutsuItems::isPickerIcon);
        if (event.getKeepInventory()) {
            // Inventory is kept: put the real hotbar back directly.
            ItemStack[] snap = s.snapshotCopy();
            for (int i = 0; i < JutsuHotbarManager.HOTBAR_SIZE; i++) {
                p.getInventory().setItem(i, snap[i]);
            }
        } else {
            for (ItemStack it : s.snapshotCopy()) {
                if (it != null) event.getDrops().add(it);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCharacterSwitch(CharacterSwitchEvent event) {
        hotbar.close(event.player(), true);
        execution.clearState(event.player());
        editGui.closeFor(event.player());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onKoEnter(KoEnterEvent event) {
        hotbar.close(event.player(), true);
        execution.clearState(event.player());
        editGui.closeFor(event.player());
    }
}
