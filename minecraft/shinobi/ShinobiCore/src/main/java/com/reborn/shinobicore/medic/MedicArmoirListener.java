package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Wires {@link MedicArmoir} into the world:
 * <ul>
 *   <li>{@link BlockPlaceEvent} — registers the placement when the
 *       placed item is the marker iron door.</li>
 *   <li>{@link PlayerInteractEvent} — right-click on a registered
 *       door cancels vanilla open/close and shows the armoir
 *       inventory instead.</li>
 *   <li>{@link BlockBreakEvent} — drops the marker item and
 *       unregisters the armoir.</li>
 *   <li>{@link InventoryClickEvent} — locks the encyclopedia slot
 *       so it can't be removed; clicking it gives the player a
 *       fresh copy of the book.</li>
 * </ul>
 */
public final class MedicArmoirListener implements Listener {

    private final ShinobiCore plugin;

    public MedicArmoirListener(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* --------------------------------------------------------- place */

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent ev) {
        ItemStack inHand = ev.getItemInHand();
        if (!MedicArmoirManager.isArmoirItem(plugin, inHand)) return;
        // Vanilla iron-door placement spawns two block halves; the
        // bottom block is the one Bukkit reports here. Register at
        // that location — right-click works on either half because
        // the listener walks up to the bottom.
        plugin.armoirs().register(ev.getBlockPlaced().getLocation());
        ev.getPlayer().sendMessage(Component.text(
                "Armoire à Pharmacie posée. Clique-droit pour l'ouvrir.",
                NamedTextColor.GRAY));
    }

    /* --------------------------------------------------------- interact */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (ev.getHand() != EquipmentSlot.HAND) return;
        Block clicked = ev.getClickedBlock();
        if (clicked == null) return;
        // Iron doors have two halves. Normalise to the bottom half.
        Block bottom = clicked;
        if (clicked.getBlockData() instanceof Bisected b
                && b.getHalf() == Bisected.Half.TOP) {
            bottom = clicked.getRelative(0, -1, 0);
        }
        MedicArmoir a = plugin.armoirs().atBlock(bottom.getLocation());
        if (a == null) return;
        ev.setCancelled(true); // suppress vanilla open/close
        plugin.armoirs().open(ev.getPlayer(), a);
    }

    /* --------------------------------------------------------- break */

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent ev) {
        Block broken = ev.getBlock();
        // Match either half.
        Block bottom = broken;
        if (broken.getBlockData() instanceof Bisected b
                && b.getHalf() == Bisected.Half.TOP) {
            bottom = broken.getRelative(0, -1, 0);
        }
        MedicArmoir a = plugin.armoirs().atBlock(bottom.getLocation());
        if (a == null) return;
        plugin.armoirs().unregister(a);
        // Replace vanilla iron-door drops with our marker item so the
        // medic can pick the armoir back up cleanly.
        ev.setDropItems(false);
        broken.getWorld().dropItemNaturally(bottom.getLocation(),
                MedicArmoirManager.armoirItem(plugin));
        ev.getPlayer().sendMessage(Component.text(
                "Armoire récupérée.", NamedTextColor.GRAY));
    }

    /* --------------------------------------------------------- inventory */

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getInventory().getHolder()
                instanceof MedicArmoirManager.ArmoirHolder)) return;
        // Lock the encyclopedia centre slot — book clicks hand a
        // fresh copy to the player without removing the in-armoir one.
        if (ev.getRawSlot() == MedicArmoirManager.ENCYCLOPEDIA_SLOT) {
            ev.setCancelled(true);
            if (ev.getWhoClicked() instanceof Player p) {
                p.getInventory().addItem(Encyclopedia.build());
                p.sendMessage(Component.text(
                        "Encyclopédie ajoutée à ton inventaire.",
                        NamedTextColor.GRAY));
            }
        }
    }
}
