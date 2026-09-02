package com.reborn.shinobicore.mobility.listener;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.mobility.ability.ZiplineAbility;
import com.reborn.shinobicore.mobility.ability.ZiplineItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes clicks on the Zipline de Ninja item to {@link ZiplineAbility}.
 *
 * <ul>
 *   <li><b>Left-click</b> (air or block) with the tagged End Rod item →
 *       {@code tryActivate} (place the zipline between player and target).</li>
 *   <li><b>Right-click</b> is cancelled so players don't accidentally
 *       place a decorative End Rod block into the world when trying to
 *       deploy — the vanilla End Rod has right-click-to-place behaviour
 *       we explicitly don't want to inherit.</li>
 * </ul>
 *
 * <p>F-press activation (ride the zipline) is handled by
 * {@code MobilityListener.onSwapHands} via
 * {@link ZiplineAbility#tryActivateNear} so it shares the grapple-cancel
 * chain already wired there. Only placement lives in this listener.
 */
public class ZiplineListener implements Listener {

    private final ShinobiCore plugin;
    private final ZiplineAbility ability;

    public ZiplineListener(ShinobiCore plugin, ZiplineAbility ability) {
        this.plugin = plugin;
        this.ability = ability;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (!ZiplineItem.isZipline(plugin, item)) return;

        if (e.getHand() != EquipmentSlot.HAND) return;
        Action action = e.getAction();

        // Cancel vanilla End Rod right-click-place — players should only
        // deploy ziplines via our ability, not scatter decorative rods
        // around the world.
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            return;
        }

        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        e.setCancelled(true);
        ability.tryActivate(e.getPlayer());
    }
}
