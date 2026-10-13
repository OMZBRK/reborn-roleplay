package com.reborn.shinobicore.ko.action;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.KoState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carry mechanic — a non-KO player can put a KO body on top of them
 * and walk around with it.
 *
 * <p>Mechanics:
 * <ul>
 *   <li>Pickup is initiated by the {@code Porter} button on
 *       {@link com.reborn.shinobicore.ko.gui.KoActionGui}; we then call
 *       {@link #carry(Player, Player)} which mounts the KO target on
 *       the carrier as a passenger (visible "on the shoulders").</li>
 *   <li>The carrier moves at slowness II while a body is on them, to
 *       balance the perk vs the price.</li>
 *   <li>Drop is triggered by sneak (hold shift) or by the carrier
 *       toggling the carry slot again. Disconnect / death of either
 *       party also drops the body.</li>
 *   <li>The KO state's {@code lockLocation} is updated to the
 *       carrier's location every tick, so when the body is dropped it
 *       stays where it landed instead of teleporting back.</li>
 * </ul>
 *
 * <p>Implements {@link Listener} for the sneak-drop hook only.
 */
public final class PorterManager implements Listener {

    private final ShinobiCore plugin;

    /** carrierId → carriedBodyId. We don't allow stacking — one carry
     *  per player at a time. */
    private final Map<UUID, UUID> byCarrier = new HashMap<>();

    /** Reverse index for quick lookup: carriedBodyId → carrierId. */
    private final Map<UUID, UUID> byCarried = new HashMap<>();

    private BukkitTask ticker;

    public PorterManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        // Drop everyone on shutdown so we don't leak passenger state.
        for (UUID carrierId : new HashMap<>(byCarrier).keySet()) {
            release(carrierId);
        }
    }

    /* ----------------------------------------------- public surface */

    /** Begin carrying {@code target}. The target must already be KO.
     *  {@code carrier} can't already be carrying someone else. Returns
     *  true on success. */
    public boolean carry(Player carrier, Player target) {
        if (carrier == null || target == null) return false;
        if (byCarrier.containsKey(carrier.getUniqueId())) return false;
        boolean dead = plugin.deathScene() != null && plugin.deathScene().isDead(target.getUniqueId());
        if (!plugin.ko().isKo(target.getUniqueId()) && !dead) return false;
        // Can't carry yourself; can't carry someone already on a mount.
        if (carrier.getUniqueId().equals(target.getUniqueId())) return false;
        if (target.getVehicle() != null) return false;

        // Mark the KO state as carried so the KoManager ticker stops
        // pinning the body to its old lock location.
        KoState st = plugin.ko().getKo(target.getUniqueId());
        if (st != null) st.setCarrierPlayerId(carrier.getUniqueId());

        carrier.addPassenger(target);
        carrier.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                Integer.MAX_VALUE, 1, true, false, false));

        byCarrier.put(carrier.getUniqueId(), target.getUniqueId());
        byCarried.put(target.getUniqueId(), carrier.getUniqueId());

        carrier.sendMessage(Component.text(
                "Tu portes la personne. Shift ou /sc drop pour la déposer.",
                NamedTextColor.GRAY));
        return true;
    }

    /** Release whatever {@code carrier} is holding. No-op when not
     *  carrying. */
    public void release(UUID carrierId) {
        UUID bodyId = byCarrier.remove(carrierId);
        if (bodyId == null) return;
        byCarried.remove(bodyId);

        Player carrier = Bukkit.getPlayer(carrierId);
        Player body    = Bukkit.getPlayer(bodyId);
        if (carrier != null) {
            carrier.removePotionEffect(PotionEffectType.SLOWNESS);
        }
        if (body != null && body.getVehicle() != null) {
            body.leaveVehicle();
        }
        // Le corps se pose au sol devant le porteur : à l'instant du dépôt il est encore sur ses épaules, et
        // l'épingler là le laissait flotter en l'air.
        Location spot = null;
        if (body != null) {
            spot = dropSpot(carrier != null ? carrier.getLocation() : body.getLocation(), 0.8);
            body.teleport(spot);
            body.setFallDistance(0f);
        }
        KoState st = plugin.ko().getKo(bodyId);
        if (st != null) {
            st.setCarrierPlayerId(null);
            if (spot != null) st.setLockLocation(spot.clone());
        }
        if (body != null && plugin.deathScene() != null) plugin.deathScene().moveBody(body.getUniqueId(), spot);
        if (carrier != null) {
            carrier.sendMessage(Component.text(
                    "Tu déposes la personne.", NamedTextColor.GRAY));
        }
    }

    /** Drop a carry that involves {@code playerId} — works whether
     *  they're the carrier or the carried. */
    public void releaseAllInvolving(UUID playerId) {
        if (byCarrier.containsKey(playerId)) {
            release(playerId);
        }
        UUID carrier = byCarried.remove(playerId);
        if (carrier != null) release(carrier);
    }

    public boolean isCarrying(UUID carrierId) { return byCarrier.containsKey(carrierId); }

    /**
     * Point au sol à {@code ahead} blocs devant {@code from} (horizontalement), posé sur le premier bloc solide en
     * dessous (6 blocs au plus), sinon {@code from}. Sert à déposer un corps porté ou tenu.
     */
    public static Location dropSpot(Location from, double ahead) {
        Vector dir = from.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        Location at = from.clone().add(dir.normalize().multiply(ahead));
        Location ground = ground(at);
        if (ground == null) ground = ground(from.clone());
        if (ground == null) return from.clone();
        ground.setYaw(from.getYaw());
        ground.setPitch(0f);
        return ground;
    }

    private static Location ground(Location at) {
        World w = at.getWorld();
        int x = at.getBlockX(), z = at.getBlockZ();
        int top = (int) Math.floor(at.getY() + 1.0);
        for (int y = top; y >= top - 7 && y > w.getMinHeight(); y--) {
            Block below = w.getBlockAt(x, y - 1, z);
            Block feet = w.getBlockAt(x, y, z);
            Block head = w.getBlockAt(x, y + 1, z);
            if (below.isSolid() && feet.isPassable() && head.isPassable()) {
                double surface = y - 1 + below.getBoundingBox().getHeight();
                return new Location(w, at.getX(), Math.max(surface, y - 1 + 0.01), at.getZ());
            }
        }
        return null;
    }
    public UUID    carriedBy(UUID bodyId)     { return byCarried.get(bodyId); }

    /* ----------------------------------------------- listeners */

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent ev) {
        if (!ev.isSneaking()) return;
        dropEverything(ev.getPlayer());
    }

    /** Drop whatever {@code carrier} is hauling — registered KO carry,
     *  raw passenger (e.g. a dummy mount that bypassed the registry),
     *  or both. Public so {@code /sc drop} can call it. */
    public void dropEverything(Player carrier) {
        if (carrier == null) return;
        if (byCarrier.containsKey(carrier.getUniqueId())) {
            release(carrier.getUniqueId());
        }
        // Defensive: if anything else is riding the carrier (a dummy
        // attached via direct addPassenger, or a stale leftover from
        // a previous session), pop it off.
        for (org.bukkit.entity.Entity p :
                new java.util.ArrayList<>(carrier.getPassengers())) {
            carrier.removePassenger(p);
        }
    }

    /* ----------------------------------------------- ticker */

    private void tick() {
        // Keep the KO lock location in sync with the carrier so the
        // body doesn't snap back when dropped.
        for (Map.Entry<UUID, UUID> e : byCarrier.entrySet()) {
            UUID carrierId = e.getKey();
            UUID bodyId    = e.getValue();
            Player carrier = Bukkit.getPlayer(carrierId);
            Player body    = Bukkit.getPlayer(bodyId);
            if (carrier == null || body == null) {
                releaseAllInvolving(carrierId);
                continue;
            }
            // If anything broke the passenger relationship (e.g.
            // teleport, vehicle eviction), drop cleanly.
            if (body.getVehicle() == null
                    || !body.getVehicle().getUniqueId().equals(carrier.getUniqueId())) {
                release(carrierId);
                continue;
            }
            KoState st = plugin.ko().getKo(bodyId);
            if (st != null) st.setLockLocation(carrier.getLocation().clone());
        }
    }
}
