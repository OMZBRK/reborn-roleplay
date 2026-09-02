package com.reborn.shinobicore.sit;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "Sit on slabs and stairs" — right-click a bottom slab or a stair with an
 * empty hand to sit on it. Standing up (sneak / move) or logging out removes
 * the seat.
 *
 * <p>Implementation: an invisible marker armour stand is spawned as the seat
 * and the player is added as its passenger. The seat is marked with a PDC tag
 * and is non-persistent, so a crash can never leave orphaned seats on disk.
 *
 * <p>This lives in ShinobiCore (not the Rasengan fork) so it can coordinate
 * with the rest of the character/KO state: a KO'd or already-mounted player
 * can't sit, and {@link #isSeated(Player)} is exposed for the mobility system
 * to stand down while a player is seated.
 */
public final class SitListener implements Listener {

    private final ShinobiCore plugin;
    private final NamespacedKey seatKey;
    /** Players currently seated on a Rasengan seat (fast lookup for isSeated). */
    private final Set<UUID> seated = new HashSet<>();

    public SitListener(ShinobiCore plugin) {
        this.plugin = plugin;
        this.seatKey = new NamespacedKey(plugin, "sit_seat");
    }

    /** True while the player is sitting on a slab/stair seat. */
    public boolean isSeated(Player p) {
        return seated.contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("sit.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;          // main hand only

        Player p = event.getPlayer();
        if (p.isSneaking()) return;                                  // sneak-click = vanilla / build
        if (p.isInsideVehicle() || isSeated(p)) return;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        if (event.getItem() != null && !event.getItem().getType().isAir()) return; // empty hand
        if (plugin.ko() != null && plugin.ko().isKo(p.getUniqueId())) return;       // not while KO'd

        Block b = event.getClickedBlock();
        if (b == null || !isSittable(b)) return;

        // Need head-room above the seat so the player doesn't suffocate.
        if (b.getRelative(BlockFace.UP).getType().isSolid()) return;

        Location seatLoc = seatLocation(b);
        // One sitter per block — bail if a seat already exists here.
        for (Entity near : b.getWorld().getNearbyEntities(seatLoc, 0.4, 0.6, 0.4)) {
            if (isSeat(near)) return;
        }
        // Must be within reach of the block (anti-cheese).
        if (!p.getWorld().equals(b.getWorld())
                || p.getLocation().distanceSquared(seatLoc) > 25.0) return;

        event.setCancelled(true);
        mountSeat(p, seatLoc);
    }

    /**
     * Sit the player in place (used by {@code /assoir}). Spawns a seat at the
     * player's current feet location. Returns false if they can't sit now.
     */
    public boolean sitInPlace(Player p) {
        if (p.isInsideVehicle() || isSeated(p)) return false;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return false;
        if (plugin.ko() != null && plugin.ko().isKo(p.getUniqueId())) return false;
        Location loc = p.getLocation();
        double offset = plugin.getConfig().getDouble("sit.height-offset", -0.3);
        Location seatLoc = new Location(loc.getWorld(),
                Math.floor(loc.getX()) + 0.5, loc.getY() + offset,
                Math.floor(loc.getZ()) + 0.5, loc.getYaw(), 0f);
        return mountSeat(p, seatLoc);
    }

    private boolean mountSeat(Player p, Location seatLoc) {
        ArmorStand seat = seatLoc.getWorld().spawn(seatLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setBasePlate(false);
            as.setCollidable(false);
            as.setPersistent(false);    // never written to disk -> no orphans after a crash
            as.getPersistentDataContainer().set(seatKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (seat.addPassenger(p)) {
            seated.add(p.getUniqueId());
            return true;
        }
        seat.remove();
        return false;
    }

    @EventHandler(ignoreCancelled = false)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof ArmorStand as) || !isSeat(as)) return;
        if (event.getEntity() instanceof Player p) {
            seated.remove(p.getUniqueId());
        }
        // Remove the seat next tick — removing the vehicle inside the dismount
        // event itself is unsafe.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (as.isValid() && as.getPassengers().isEmpty()) as.remove();
        });
    }

    /* ----------------------------------------------------------- helpers */

    private boolean isSittable(Block b) {
        var data = b.getBlockData();
        if (data instanceof Stairs) return true;
        // Only bottom slabs (a sitter on a top slab would float).
        return data instanceof Slab slab && slab.getType() == Slab.Type.BOTTOM;
    }

    /** Centre of the block, lifted to a tunable seating height. */
    private Location seatLocation(Block b) {
        double offset = plugin.getConfig().getDouble("sit.height-offset", -0.3);
        return new Location(
                b.getWorld(),
                b.getX() + 0.5,
                b.getY() + 0.5 + offset,
                b.getZ() + 0.5,
                0f, 0f);
    }

    private boolean isSeat(Entity e) {
        return e instanceof ArmorStand
                && e.getPersistentDataContainer().has(seatKey, PersistentDataType.BYTE);
    }
}
