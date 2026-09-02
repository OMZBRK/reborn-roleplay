package com.reborn.shinobicore.sit;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RP postures driven by a forced player pose:
 * <ul>
 *   <li>{@code /allonger} — lie down ({@link Pose#SLEEPING})</li>
 *   <li>{@code /ramper}   — crawl ({@link Pose#SWIMMING})</li>
 * </ul>
 * Each command toggles; sneaking, teleporting, or logging out ends the posture.
 * Sitting in place ({@code /assoir}) is handled by {@link SitListener} (seat
 * entity), not here.
 */
public final class PostureManager implements Listener {

    private final ShinobiCore plugin;
    private final Map<UUID, Pose> posture = new HashMap<>();

    public PostureManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public boolean isPosed(Player p) {
        return posture.containsKey(p.getUniqueId());
    }

    /** Toggle lying down. Returns true if now lying, false if stood up / refused. */
    public boolean toggleLay(Player p) {
        return toggle(p, Pose.SLEEPING);
    }

    /** Toggle crawling. Returns true if now crawling, false if stood up / refused. */
    public boolean toggleCrawl(Player p) {
        return toggle(p, Pose.SWIMMING);
    }

    private boolean toggle(Player p, Pose pose) {
        UUID id = p.getUniqueId();
        if (pose.equals(posture.get(id))) {   // same posture again -> stand up
            clear(p);
            return false;
        }
        if (p.isInsideVehicle()) return false;
        if (plugin.ko() != null && plugin.ko().isKo(id)) return false;
        posture.put(id, pose);
        p.setPose(pose, true);                // fixed = stays until released
        return true;
    }

    /** End any active posture and let vanilla recompute the pose. */
    public void clear(Player p) {
        if (posture.remove(p.getUniqueId()) != null && p.isOnline()) {
            p.setPose(Pose.STANDING, false);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) clear(e.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        clear(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        posture.remove(e.getPlayer().getUniqueId());
    }
}
