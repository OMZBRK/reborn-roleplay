package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Drives the « Carte des Pistes »: a 1 s actionbar read of the nearest trail
 * waypoint (role + cardinal direction + distance) while held, and a right-click
 * that re-aims the compass needle at it.
 */
public final class TrailMapListener implements Listener {

    private static final String[] DIRS = {"Nord", "Nord-Est", "Est", "Sud-Est",
            "Sud", "Sud-Ouest", "Ouest", "Nord-Ouest"};

    private final TrailManager trails;

    public TrailMapListener(JavaPlugin plugin, TrailManager trails) {
        this.trails = trails;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private void tick() {
        if (Tps.shouldDefer()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (trails.isRiding(p) || trails.isHotbarEditing(p.getUniqueId())) continue;
            if (!holdingMap(p)) continue;
            TrailManager.NearestPoint np = trails.nearest(p);
            if (np == null) {
                p.sendActionBar(Component.text("Aucune piste à proximité.", NamedTextColor.GRAY));
            } else {
                p.sendActionBar(Component.text("⇒ « " + np.trailName() + " » · " + np.role()
                        + " · " + direction(p.getLocation(), np.location())
                        + " · " + (int) np.distance() + "m", NamedTextColor.AQUA));
            }
        }
    }

    private boolean holdingMap(Player p) {
        return TrailMapItem.isTrailMap(p.getInventory().getItemInMainHand())
                || TrailMapItem.isTrailMap(p.getInventory().getItemInOffHand());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (!TrailMapItem.isTrailMap(item)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        TrailManager.NearestPoint np = trails.nearest(p);
        if (np == null) {
            p.sendActionBar(Component.text("Aucune piste à proximité.", NamedTextColor.GRAY));
            return;
        }
        TrailMapItem.pointTo(item, np.location());
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        p.sendMessage(Component.text("Aiguille orientée vers « " + np.trailName() + " » ("
                + np.role() + ", " + (int) np.distance() + "m).", NamedTextColor.AQUA));
    }

    private static String direction(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        if (bearing < 0) bearing += 360.0;
        return DIRS[(int) Math.round(bearing / 45.0) % 8];
    }
}
