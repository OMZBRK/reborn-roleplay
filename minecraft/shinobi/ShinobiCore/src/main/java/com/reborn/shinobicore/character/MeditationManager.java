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
    /** Canal du halo de méditation (mod : {@code meditation/MeditationClient}). */
    public static final String CANAL_HALO = "reborn:meditation";
    /** Dernière liste envoyée à chaque joueur (signature) ; absent = rien ou liste vide. */
    private final Map<UUID, Long> haloEnvoye = new java.util.HashMap<>();

    public MeditationManager(ShinobiCore plugin) {
        this.plugin = plugin;
        // Halo de méditation (18/10) : qui médite, envoyé aux mods proches (canal reborn:meditation).
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL_HALO)) m.registerOutgoingPluginChannel(plugin, CANAL_HALO);
        Bukkit.getScheduler().runTaskTimer(plugin, this::synchroHalo, 20L, 10L);
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
        // États (ETATS_JOUEUR §2.2 f, étape 2) : ni à terre, ni porté, ni gelé, ni étourdi, ni entravé (CANALISER).
        // La douleur et l'ATA restent permises : méditer fait partie du repos qui en sort.
        String refus = com.reborn.shinobicore.etat.Verrous.refus(p.getUniqueId(),
                com.reborn.shinobicore.api.etat.Capacite.CANALISER);
        if (refus != null) {
            p.sendActionBar(Component.text(refus, NamedTextColor.GRAY));
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
        s.couleur = couleurHalo(c);
        sessions.put(id, s);
        synchroHalo();

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

            // Le halo du mod (reborn:meditation) remplace les particules du jeu ; repli réglable.
            if (plugin.getConfig().getBoolean("meditation.halo.particules-vanilla", false)) {
                pl.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        pl.getLocation().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0.0);
            }

            s.ticksSinceRegen += 5;
            if (s.ticksSinceRegen >= intervalTicks) {
                s.ticksSinceRegen = 0;
                ShinobiCharacter ch = plugin.characters().getActive(id);
                if (ch != null) {
                    ChakraPool pool = ch.chakra();
                    double amount = perTick;
                    if (plugin.getConfig().getBoolean("meditation.use-stats-regen", true)) {
                        // Stats-driven (SPEC_STATS_SERVICE §1.3): bigger reserve and
                        // better Contrôle meditate faster.
                        amount = com.reborn.shinobicore.stats.StatFormulas.chakraRegenPer10s(ch.stats())
                                * (intervalSec / 10.0)
                                * plugin.getConfig().getDouble("meditation.stats-multiplier", 3.0);
                    }
                    // Modificateurs (thé vert : méditation ×1,25 — nourriture, 14/10).
                    if (plugin.modificateurs() != null) amount *= plugin.modificateurs().multiplicateur(id, "meditation");
                    double added = Math.min(amount, pool.max() - pool.current());
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
                "Tu t'assois et commences à méditer (chakra toutes les " + intervalSec
                        + " s ; bouge pour te relever).",
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
        synchroHalo();
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

    /** Un KO (même par le chakra) arrête la méditation (ETATS_JOUEUR §2.2 f). */
    @EventHandler
    public void onKo(com.reborn.shinobicore.event.KoEnterEvent e) {
        if (sessions.containsKey(e.player().getUniqueId())) stop(e.player(), "Tu perds le fil de ta méditation.");
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
        haloEnvoye.remove(e.getPlayer().getUniqueId());
    }

    /* ------------------------------------------------- halo (reborn:meditation) */

    /**
     * Envoie à chaque joueur moddé la liste des méditants proches (lui compris) : octet 0, nombre (int), puis
     * identifiant réseau de l'entité (int) et couleur du halo (int RGB) — seulement quand elle change (signature),
     * au démarrage / à l'arrêt d'une méditation et toutes les 10 ticks (arrivées, départs, distance). Rien à faire
     * quand personne ne médite et que tout le monde a reçu la liste vide.
     */
    private void synchroHalo() {
        if (sessions.isEmpty() && haloEnvoye.isEmpty()) return;
        boolean actif = plugin.getConfig().getBoolean("meditation.halo.enabled", true);
        double portee = plugin.getConfig().getDouble("meditation.halo.portee", 48.0), p2 = portee * portee;
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (!v.getListeningPluginChannels().contains(CANAL_HALO)) continue;
            int n = 0;
            long sig = 17;
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(5 + 8 * sessions.size());
            b.put((byte) 0).putInt(0);
            if (actif) {
                for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
                    Player m = Bukkit.getPlayer(e.getKey());
                    if (m == null || m.getWorld() != v.getWorld()) continue;
                    if (m.getLocation().distanceSquared(v.getLocation()) > p2) continue;
                    if (!v.canSee(m)) continue;
                    b.putInt(m.getEntityId()).putInt(e.getValue().couleur);
                    sig = sig * 31 + m.getEntityId();
                    sig = sig * 31 + e.getValue().couleur;
                    n++;
                }
            }
            Long avant = haloEnvoye.get(v.getUniqueId());
            if (n == 0 && avant == null) continue;
            if (avant != null && avant == sig) continue;
            b.putInt(1, n);
            byte[] data = java.util.Arrays.copyOf(b.array(), 5 + 8 * n);
            try {
                v.sendPluginMessage(plugin, CANAL_HALO, data);
            } catch (Exception ignored) { /* client sans le mod */ }
            if (n == 0) haloEnvoye.remove(v.getUniqueId()); else haloEnvoye.put(v.getUniqueId(), sig);
        }
    }

    /**
     * Couleur du halo : {@code meditation.halo.couleur} (vert doux de la référence), ou celle de la nature de chakra
     * principale du personnage si {@code meditation.halo.par-nature} est vrai (teintes sobres en config).
     */
    private int couleurHalo(ShinobiCharacter c) {
        var cfg = plugin.getConfig();
        int defaut = rgb(cfg.getString("meditation.halo.couleur", "#86F59E"), 0x86F59E);
        if (c == null || !cfg.getBoolean("meditation.halo.par-nature", false)) return defaut;
        ChakraAffinity a = c.primaryChakraAffinity();
        if (a == null || a == ChakraAffinity.NONE) return defaut;
        return rgb(cfg.getString("meditation.halo.natures." + a.name().toLowerCase(java.util.Locale.ROOT)), defaut);
    }

    private static int rgb(String s, int defaut) {
        if (s == null) return defaut;
        String h = s.trim().replace("#", "");
        try {
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return defaut;
        }
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
        int couleur;
        BukkitTask task;

        Session(Location anchor, ArmorStand seat, double radiusSq) {
            this.anchor = anchor;
            this.seat = seat;
            this.radiusSq = radiusSq;
        }
    }
}
