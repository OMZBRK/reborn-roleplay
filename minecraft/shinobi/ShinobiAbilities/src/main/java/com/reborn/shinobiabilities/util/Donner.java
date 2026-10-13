package com.reborn.shinobiabilities.util;

import com.reborn.shinobicore.api.InventoryService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Donner un objet à un joueur <b>par la sacoche RP</b> (ShinobiCore {@link InventoryService#give}) : barre 1-9, puis
 * sac, puis sol — jamais dans les cases cachées 9-35 de Minecraft, que la sacoche n'affiche pas (inventaire du
 * 2026-10-13). Sans le service (mode construction), repli sur l'inventaire vanilla.
 */
public final class Donner {

    private Donner() {}

    public static void a(Player p, ItemStack it) {
        if (p == null || it == null || it.getType().isAir()) return;
        InventoryService inv = Bukkit.getServicesManager().load(InventoryService.class);
        if (inv != null) {
            inv.give(p, it);
            return;
        }
        p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItemNaturally(p.getLocation(), r));
    }
}
