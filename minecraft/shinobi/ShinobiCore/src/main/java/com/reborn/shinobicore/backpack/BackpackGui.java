package com.reborn.shinobicore.backpack;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Chest-like GUI rendering a {@link Backpack}'s contents.
 *
 * <p>Implemented as a real Bukkit {@link Inventory} so all vanilla
 * click behaviours (left/right-click, shift-click, drag, swap, double
 * click) work natively without per-event handling. The listener layer
 * just snapshots the live inventory back onto the {@link Backpack}
 * when the GUI closes (see {@code BackpackListener.onInventoryClose}).
 *
 * <p>The holder carries the backpack id so multiple players opening
 * the same placed backpack land in the same in-memory inventory and
 * can see each other's takes/places live.
 */
public class BackpackGui implements InventoryHolder {

    private final ShinobiCore plugin;
    private final Backpack backpack;
    private Inventory inventory;

    private BackpackGui(ShinobiCore plugin, Backpack backpack) {
        this.plugin = plugin;
        this.backpack = backpack;
    }

    public Backpack backpack() { return backpack; }

    @Override public Inventory getInventory() { return inventory; }

    /** Open the backpack contents for {@code viewer}. The inventory
     *  size matches {@link BackpackSize#slots()}. The title shows the
     *  backpack name + a 6-char prefix of its UUID so multi-bag
     *  inventories are visually disambiguated.
     *
     *  <p>If two players open the same physical backpack at once,
     *  they share an Inventory instance — clicks from either propagate
     *  in real time, same as a vanilla chest. */
    public static void open(Player viewer, ShinobiCore plugin, Backpack backpack) {
        BackpackGui holder = new BackpackGui(plugin, backpack);
        Component title = Component.text(backpack.size().displayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("  #" + backpack.id().toString().substring(0, 6),
                                NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, true));

        Inventory inv = Bukkit.createInventory(holder, backpack.size().slots(), title);
        holder.inventory = inv;
        // Seed the inventory from the backpack record. Using the same
        // ItemStack references means simple in-place edits (e.g. a
        // shift-click reduce) reflect into the backpack array.
        ItemStack[] src = backpack.contents();
        for (int i = 0; i < src.length && i < inv.getSize(); i++) inv.setItem(i, src[i]);
        viewer.openInventory(inv);
    }
}
