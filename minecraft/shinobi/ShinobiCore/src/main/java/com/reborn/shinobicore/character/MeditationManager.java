package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.chakra.ChakraPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the {@code /meditation} command: pin the player to a spot, seat
 * them visually ON TOP of the block (not inside it) using an invisible marker
 * armor stand, regenerate chakra on a fixed interval, and cancel the session
 * on lateral move or damage.
 *
 * <p>The radius check only considers the HORIZONTAL distance from the anchor.
 * Mounting an armor stand changes the player's reported Y by up to a block,
 * which was the reason meditation used to auto-cancel the instant it started.
 */
public class MeditationManager implements Listener {

    /** How far below the player's feet to spawn the seat so they visually
     *  sit flush with the top of the block they're standing on. Was 0.2
     *  but that left the player's legs embedded in the block — zero lets
     *  the marker stand sit at exactly the block-top Y, putting the
     *  passenger's feet on the surface. Use a negative value (e.g. -0.05)
     *  if any residual embedding remains on specific block types. */
    private static final double SEAT_Y_OFFSET = 0.0;

    /** Grace window after start where we skip the radius check entirely,
     *  giving the mount position time to settle on the client. */
    private static final int GRACE_TICKS = 10;

    private final ShinobiCore plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public MeditationManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public boolean isMeditating(UUID id) {
        return sessions.containsKey(id);
    }

    public boolean start(Player p) {
        if (!plugin.getConfig().getBoolean("meditation.enabled", true)) {
            p.sendMessage(Component.text("La méditation est désactivée.", NamedTextColor.RED));
            return false;
        }
        if (sessions.containsKey(p.getUniqueId())) {
            stop(p, "Tu te relèves.");
            return false;
        }

        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) {
            p.sendMessage(Component.text("Sélectionne d'abord un personnage.", NamedTextColor.RED));
            return false;
        }

        // Capture horizontal anchor at the player's current spot.
        Location anchor = p.getLocation().clone();
        ArmorStand seat = null;
        if (plugin.getConfig().getBoolean("meditation.use-armor-stand-seat", true)) {
            seat = spawnSeat(anchor);
            if (seat != null) seat.addPassenger(p);
        }

        double perTick = plugin.getConfig().getDouble("meditation.chakra-per-tick", 100.0);
        int intervalSec = Math.max(1, plugin.getConfig().getInt("meditation.tick-interval-seconds", 5));
        long intervalTicks = intervalSec * 20L;
        double radius = plugin.getConfig().getDouble("meditation.cancel-move-radius", 0.4);
        double radiusSq = radius * radius;

        UUID id = p.getUniqueId();
        Session s = new Session(anchor, seat, radiusSq);
        sessions.put(id, s);

        // Chakra + particle ticker. Runs every 5 ticks.
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline()) { stopInternal(id, null); return; }

            s.age += 5;

            // Horizontal-only radius check. Skip during the grace window so
            // the seat-mount settle doesn't instantly break us.
            if (s.age > GRACE_TICKS) {
                Location loc = pl.getLocation();
                double dx = loc.getX() - s.anchor.getX();
                double dz = loc.getZ() - s.anchor.getZ();
                if (dx * dx + dz * dz > s.radiusSq) {
                    stop(pl, "You moved — meditation broken.");
                    return;
                }

                // If the player is no longer riding the seat (e.g., they shifted
                // off), end the session.
                if (s.seat != null && !s.seat.getPassengers().contains(pl)) {
                    stop(pl, "You stood up — meditation ended.");
                    return;
                }
            }

            pl.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    pl.getLocation().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0.0);

            s.ticksSinceRegen += 5;
            if (s.ticksSinceRegen >= intervalTicks) {
                s.ticksSinceRegen = 0;
                ShinobiCharacter ch = plugin.characters().getActive(id);
                if (ch != null) {
                    ChakraPool pool = ch.chakra();
                    double added = Math.min(perTick, pool.max() - pool.current());
                    if (added > 0) {
                        pool.regen(added);
                        pl.sendActionBar(Component.text(
                                "+" + (int) added + " chakra",
                                NamedTextColor.AQUA));
                    }
                }
            }
        }, 5L, 5L);

        p.sendMessage(Component.text(
                "You sit down and begin meditating. (+" + (int) perTick
                        + " chakra per " + intervalSec + "s; move to stand up)",
                NamedTextColor.GOLD));
        return true;
    }

    public void stop(Player p, String message) {
        stopInternal(p.getUniqueId(), message);
    }

    private void stopInternal(UUID id, String message) {
        Session s = sessions.remove(id);
        if (s == null) return;
        if (s.task != null) s.task.cancel();
        if (s.seat != null) {
            try {
                for (Entity e : s.seat.getPassengers()) s.seat.removePassenger(e);
            } catch (Exception ignore) { /* seat may already be dead */ }
            if (!s.seat.isDead()) s.seat.remove();
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline() && message != null) {
            p.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        }
    }

    public void shutdown() {
        for (UUID id : sessions.keySet().toArray(new UUID[0])) {
            stopInternal(id, null);
        }
    }

    /* ----------------------------------------------------- event handlers */

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (sessions.containsKey(p.getUniqueId())) {
            stop(p, "You took damage — meditation broken.");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (sessions.containsKey(e.getPlayer().getUniqueId())) {
            stop(e.getPlayer(), "You teleported — meditation broken.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopInternal(e.getPlayer().getUniqueId(), null);
    }

    /* --------------------------------------------------------- internals */

    /**
     * Spawns an invisible marker armor stand just below the player's feet so
     * the passenger mount lands the player flush with the block surface —
     * sitting ON the block, not inside it.
     */
    private ArmorStand spawnSeat(Location anchor) {
        Location spawnAt = anchor.clone().subtract(0, SEAT_Y_OFFSET, 0);
        ArmorStand stand = (ArmorStand) anchor.getWorld()
                .spawnEntity(spawnAt, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        // Marker stands have no hitbox, can't be hit, and — critically on
        // Paper 1.21 — can still carry a passenger.
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setSmall(false);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(false);
        stand.setCollidable(false);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.setVelocity(new Vector(0, 0, 0));
        return stand;
    }

    private static final class Session {
        final Location anchor;
        final ArmorStand seat;
        final double radiusSq;
        long ticksSinceRegen;
        int age;
        BukkitTask task;

        Session(Location anchor, ArmorStand seat, double radiusSq) {
            this.anchor = anchor;
            this.seat = seat;
            this.radiusSq = radiusSq;
        }
    }
}
