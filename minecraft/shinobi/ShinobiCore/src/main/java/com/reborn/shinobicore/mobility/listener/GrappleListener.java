package com.reborn.shinobicore.mobility.listener;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.mobility.ability.GrappleAbility;
import com.reborn.shinobicore.mobility.ability.GrappleItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Routes left-click-with-Grappin events to {@link GrappleAbility#tryActivate}.
 *
 * <h2>Why left-click on a non-interactive item?</h2>
 * Vanilla right-click has a ~4.5 block interaction-reach cap that the
 * client enforces before the event ever reaches the server — aiming at
 * a distant wall produces an {@code AIR} action with no hit data, and
 * several Paper builds refuse to dispatch the "use item" packet beyond
 * that range for interactive items. Left-click fires {@code LEFT_CLICK_AIR}
 * /{@code LEFT_CLICK_BLOCK} on <em>every</em> swing regardless of what
 * the player is aiming at, so our manual ray-trace gets a chance to run
 * at the full configured range.
 *
 * <p>A stick has no vanilla behaviour on either click, so we don't have
 * to fight any in-built handler — we just cancel the event to suppress
 * the arm-swing-is-attacking side effects in creative mode, then fire
 * the ability.
 *
 * <p><b>Priority:</b> {@code HIGHEST} with {@code ignoreCancelled = false}.
 * Protection plugins may pre-cancel interact events; we still want a
 * plugin-issued item's activation to go through.
 */
public class GrappleListener implements Listener {

    private final ShinobiCore plugin;
    private final GrappleAbility ability;

    public GrappleListener(ShinobiCore plugin, GrappleAbility ability) {
        this.plugin = plugin;
        this.ability = ability;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (!GrappleItem.isGrappin(plugin, item)) return;

        Player p = e.getPlayer();
        Action action = e.getAction();
        EquipmentSlot hand = e.getHand();

        // Main-hand only — off-hand fires a duplicate for the same swing.
        if (hand != EquipmentSlot.HAND) return;
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        // Cancel the event so creative-mode insta-break, block-damage
        // progress, or any other vanilla left-click side effect doesn't
        // run alongside the grapple.
        e.setCancelled(true);
        ability.tryActivate(p);
    }
}
