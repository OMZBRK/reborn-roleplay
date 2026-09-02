package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Trails — staff-built chains of invisible jump anchors. A player
 * standing near either ENDPOINT anchor (first or last) presses F (with
 * a non-JutsuItem hand) to ride toward the far end — forward from the
 * first anchor, reverse from the last. F again drops mid-ride.
 *
 * <p>Editing is command-driven (/trail create|add|finish|…), persisted
 * in {@code trails.yml}.
 */
public final class TrailManager {

    /** A saved trail: world + ordered anchor points. */
    public record Trail(String name, String worldName, List<Vector> points) {
        public Location pointLocation(int idx) {
            World w = Bukkit.getWorld(worldName);
            if (w == null || idx < 0 || idx >= points.size()) return null;
            Vector v = points.get(idx);
            return new Location(w, v.getX(), v.getY(), v.getZ());
        }
    }

    private static final class EditSession {
        String name;
        String worldName;
        final List<Vector> points = new ArrayList<>();
    }

    /** One rider's state. The ride is a feedforward state machine:
     *  each hop is a symmetric parabola from anchor to anchor, computed
     *  ONCE at hop start and then followed by teleport interpolation —
     *  no per-tick steering toward a target, which is what used to
     *  rubber-band. Config is cached here at ride start (inline cfg
     *  keys, live-tunable per new ride without a config regen). */
    private static final class Ride {
        Trail trail;
        int dir;             // +1 = forward (from first anchor), -1 = reverse (from last)
        int anchorIdx;       // origin anchor of the current hop
        int tick;            // ticks into the current hop arc / dwell
        boolean dwelling;    // true while paused on an anchor between hops
        int arcTicks;        // precomputed duration T of the current hop
        Vector from, to;     // current hop endpoints
        double lift;         // apex lift so the peak sits apexHeight above the HIGHER endpoint
        int totalTicks;      // whole-ride safety counter

        // cached config (read once at ride start)
        int maxRideTicks;
        double apexHeight;
        double baseTicks;
        double ticksPerBlock;
        int minTicks;
        int maxTicksClamp;
        int dwellTicks;
        double ramp;
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Trail> trails = new LinkedHashMap<>();
    private final Map<UUID, EditSession> editing = new HashMap<>();
    private final Map<UUID, TrailEditorSession> editors = new HashMap<>();
    private final Map<UUID, Ride> riding = new HashMap<>();
    private BukkitTask rideTask;

    public TrailManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trails.yml");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mobility.trail.enabled", true);
    }

    /* ------------------------------------------------------------ storage */

    public void load() {
        trails.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String name : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(name);
            if (s == null) continue;
            String world = s.getString("world");
            List<Vector> pts = new ArrayList<>();
            for (String raw : s.getStringList("points")) {
                String[] parts = raw.split(",");
                if (parts.length != 3) continue;
                try {
                    pts.add(new Vector(Double.parseDouble(parts[0]),
                            Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2])));
                } catch (NumberFormatException ignore) { }
            }
            if (world != null && pts.size() >= 2) {
                trails.put(name.toLowerCase(Locale.ROOT),
                        new Trail(name, world, pts));
            }
        }
        plugin.getLogger().info(trails.size() + " piste(s) chargée(s).");
    }

    public void saveSync() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Trail t : trails.values()) {
            List<String> pts = new ArrayList<>(t.points().size());
            for (Vector v : t.points()) {
                pts.add(v.getX() + "," + v.getY() + "," + v.getZ());
            }
            yml.set(t.name() + ".world", t.worldName());
            yml.set(t.name() + ".points", pts);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(file.toPath(), yml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Écriture trails.yml impossible: " + ex.getMessage());
        }
    }

    /* ------------------------------------------------------------ editing */

    public boolean editCreate(Player p, String name) {
        if (trails.containsKey(name.toLowerCase(Locale.ROOT))) return false;
        EditSession s = new EditSession();
        s.name = name;
        s.worldName = p.getWorld().getName();
        editing.put(p.getUniqueId(), s);
        return true;
    }

    public int editAdd(Player p) {
        EditSession s = editing.get(p.getUniqueId());
        if (s == null) return -1;
        if (!p.getWorld().getName().equals(s.worldName)) return -2;
        s.points.add(p.getLocation().toVector());
        return s.points.size();
    }

    public boolean editFinish(Player p) {
        EditSession s = editing.remove(p.getUniqueId());
        if (s == null || s.points.size() < 2) return false;
        trails.put(s.name.toLowerCase(Locale.ROOT),
                new Trail(s.name, s.worldName, List.copyOf(s.points)));
        saveSync();
        return true;
    }

    public boolean editCancel(Player p) {
        return editing.remove(p.getUniqueId()) != null;
    }

    public boolean isEditing(Player p) { return editing.containsKey(p.getUniqueId()); }

    /** Remove the last captured point; returns the new count, -1 if no
     *  session, or 0 when there was nothing to undo. */
    public int editUndo(Player p) {
        EditSession s = editing.get(p.getUniqueId());
        if (s == null) return -1;
        if (s.points.isEmpty()) return 0;
        s.points.remove(s.points.size() - 1);
        return s.points.size();
    }

    /** Captured-point count for the editing player, or -1 if no session. */
    public int editPointCount(Player p) {
        EditSession s = editing.get(p.getUniqueId());
        return s == null ? -1 : s.points.size();
    }

    /** A copy of the captured points (for the editor preview). */
    public List<Vector> editPoints(Player p) {
        EditSession s = editing.get(p.getUniqueId());
        return s == null ? List.of() : List.copyOf(s.points);
    }

    /* ------------------------------------------------- hotbar editor sessions */

    public boolean isHotbarEditing(UUID id) { return editors.containsKey(id); }
    public TrailEditorSession editor(UUID id) { return editors.get(id); }
    public void putEditor(UUID id, TrailEditorSession s) { editors.put(id, s); }
    public void removeEditor(UUID id) { editors.remove(id); }

    public boolean delete(String name) {
        Trail removedTrail = trails.remove(name.toLowerCase(Locale.ROOT));
        if (removedTrail == null) return false;
        // Drop anyone still riding the deleted trail safely where they
        // are — never leave a ghost ride on a trail that no longer exists.
        for (UUID id : riding.keySet().toArray(new UUID[0])) {
            Ride r = riding.get(id);
            if (r == null || r.trail != removedTrail) continue;
            Player rider = plugin.getServer().getPlayer(id);
            if (rider != null) stopRide(rider, true);
            else riding.remove(id);
        }
        saveSync();
        return true;
    }

    public Trail byName(String name) {
        return name == null ? null : trails.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Trail t : trails.values()) out.add(t.name());
        return out;
    }

    /* --------------------------------------------------------- navigation */

    /** Closest waypoint of any trail in the player's world, role-labelled
     *  (Départ = first, Arrivée = last, Étape = middle); {@code null} if none. */
    public NearestPoint nearest(Player p) {
        Location from = p.getLocation();
        World w = from.getWorld();
        if (w == null) return null;
        NearestPoint best = null;
        double bestSq = Double.MAX_VALUE;
        for (Trail t : trails.values()) {
            if (!t.worldName().equalsIgnoreCase(w.getName())) continue;
            List<Vector> pts = t.points();
            for (int i = 0; i < pts.size(); i++) {
                Vector v = pts.get(i);
                double dx = v.getX() - from.getX();
                double dy = v.getY() - from.getY();
                double dz = v.getZ() - from.getZ();
                double sq = dx * dx + dy * dy + dz * dz;
                if (sq < bestSq) {
                    bestSq = sq;
                    String role = i == 0 ? "Départ"
                            : (i == pts.size() - 1 ? "Arrivée" : "Étape");
                    best = new NearestPoint(t.name(), role,
                            new Location(w, v.getX(), v.getY(), v.getZ()), Math.sqrt(sq));
                }
            }
        }
        return best;
    }

    /** Result of {@link #nearest(Player)}: trail name, waypoint role, the
     *  location, and the distance from the query point. */
    public record NearestPoint(String trailName, String role,
                               Location location, double distance) {}

    /* -------------------------------------------------------------- riding */

    public boolean isRiding(Player p) {
        return riding.containsKey(p.getUniqueId());
    }

    /** F near an ENDPOINT anchor (first or last) → start riding toward
     *  the far end. Returns true on start. */
    public boolean tryStartRide(Player p) {
        if (!enabled() || isRiding(p) || isHotbarEditing(p.getUniqueId())) return false;
        double radius = plugin.getConfig()
                .getDouble("mobility.trail.activation-radius", 3.0);
        double r2 = radius * radius;
        for (Trail t : trails.values()) {
            Location first = t.pointLocation(0);
            Location last = t.pointLocation(t.points().size() - 1);
            if (first == null || last == null
                    || !first.getWorld().equals(p.getWorld())) continue;
            double dFirst = first.distanceSquared(p.getLocation());
            double dLast = last.distanceSquared(p.getLocation());
            boolean nearFirst = dFirst <= r2;
            boolean nearLast = dLast <= r2;
            if (!nearFirst && !nearLast) continue;
            // Both in range (short trail): the closer endpoint wins.
            boolean forward = nearFirst && (!nearLast || dFirst <= dLast);
            Ride r = new Ride();
            r.trail = t;
            r.dir = forward ? 1 : -1;
            r.anchorIdx = forward ? 0 : t.points().size() - 1;
            // Cache the ride config once — inline cfg keys (absent from the
            // shipped config) so they are live-tunable on the next ride.
            var cfg = plugin.getConfig();
            r.maxRideTicks  = cfg.getInt("mobility.trail.max-ride-ticks", 600);
            r.apexHeight    = cfg.getDouble("mobility.trail.arc-apex-height", 3.0);
            r.baseTicks     = cfg.getDouble("mobility.trail.arc-base-ticks", 6.0);
            r.ticksPerBlock = cfg.getDouble("mobility.trail.arc-ticks-per-block", 1.5);
            r.minTicks      = cfg.getInt("mobility.trail.arc-min-ticks", 8);
            r.maxTicksClamp = cfg.getInt("mobility.trail.arc-max-ticks", 24);
            r.dwellTicks    = cfg.getInt("mobility.trail.dwell-ticks", 6);
            // Ease-in strength 0..1: 0 = constant pace, higher = softer
            // launch out of the anchor (the hop accelerates into the arc).
            r.ramp          = Math.max(0.0, Math.min(1.0,
                    cfg.getDouble("mobility.trail.arc-ramp", 0.35)));
            beginHop(r);
            riding.put(p.getUniqueId(), r);
            ensureRideTask();
            p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.7f, 1.4f);
            p.sendActionBar(Component.text("Piste « " + t.name() + " » — F pour lâcher",
                    NamedTextColor.AQUA));
            return true;
        }
        return false;
    }

    /** F mid-ride / force paths. */
    public void stopRide(Player p, boolean feedback) {
        if (riding.remove(p.getUniqueId()) == null) return;
        p.setFallDistance(0f);
        if (feedback && p.isOnline()) {
            p.sendActionBar(Component.text("Tu lâches la piste.", NamedTextColor.GRAY));
            p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.5f, 0.8f);
        }
    }

    public void stopAll() {
        for (UUID id : riding.keySet().toArray(new UUID[0])) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) stopRide(p, false);
            else riding.remove(id);
        }
        if (rideTask != null) { rideTask.cancel(); rideTask = null; }
    }

    /** Prepare the next hop from {@code r.anchorIdx} toward the ride
     *  direction: precompute the endpoints, the duration (scaled by
     *  horizontal distance so speed feels consistent across gaps) and
     *  the apex lift. Called once per hop — the arc itself is then
     *  followed open-loop, never re-steered. */
    private void beginHop(Ride r) {
        List<Vector> pts = r.trail.points();
        int next = r.anchorIdx + r.dir;
        r.from = pts.get(r.anchorIdx).clone();
        r.to = pts.get(next).clone();
        double dx = r.to.getX() - r.from.getX();
        double dz = r.to.getZ() - r.from.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        r.arcTicks = Math.max(r.minTicks, Math.min(r.maxTicksClamp,
                (int) Math.round(r.baseTicks + horiz * r.ticksPerBlock)));
        // lerp(A.y,B.y,t) peaks at the AVERAGE of the endpoints; adding
        // |dy|/2 puts the apex exactly apexHeight above the HIGHER
        // endpoint, so an upward step is always cleared.
        r.lift = r.apexHeight + Math.abs(r.to.getY() - r.from.getY()) / 2.0;
        r.tick = 0;
    }

    private void ensureRideTask() {
        if (rideTask != null) return;
        rideTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (riding.isEmpty()) { rideTask.cancel(); rideTask = null; return; }

            for (UUID id : riding.keySet().toArray(new UUID[0])) {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline()) { riding.remove(id); continue; }
                Ride r = riding.get(id);
                if (r == null) continue;
                // A dead rider must never be dragged along the arc.
                if (p.isDead()) { stopRide(p, false); continue; }
                if (++r.totalTicks > r.maxRideTicks) { stopRide(p, true); continue; }

                World w = Bukkit.getWorld(r.trail.worldName());
                int n = r.trail.points().size();
                if (w == null || !p.getWorld().equals(w) || n < 2) { stopRide(p, true); continue; }

                // Dwell beat: pinned on the anchor top between hops —
                // held by teleport each tick so there is zero drift and
                // input can't slide the rider off a floating pillar.
                if (r.dwelling) {
                    Location hold = new Location(w, r.to.getX(), r.to.getY(), r.to.getZ());
                    rideTeleport(p, hold);
                    p.setFallDistance(0f);
                    if (++r.tick >= r.dwellTicks) {
                        r.dwelling = false;
                        beginHop(r);
                    }
                    continue;
                }

                // Feedforward arc: advance the hop clock, sample the
                // precomputed parabola, and MOVE the player there. No
                // correction toward the anchor, no velocity writes — the
                // trajectory IS the ride, which is what kills the
                // overshoot/rollback of the old per-tick steering.
                r.tick++;
                double t = Math.min(1.0, r.tick / (double) r.arcTicks);
                // Velocity ramp: warp the parameter with an ease-in blend
                // (tw(0)=0, tw(1)=1, monotonic) so each hop launches
                // gently out of the anchor and gathers pace along the
                // SAME spatial parabola — the curve shape is unchanged.
                double tw = (1.0 - r.ramp) * t + r.ramp * t * t;
                double x = r.from.getX() + (r.to.getX() - r.from.getX()) * tw;
                double z = r.from.getZ() + (r.to.getZ() - r.from.getZ()) * tw;
                double y = r.from.getY() + (r.to.getY() - r.from.getY()) * tw
                        + r.lift * 4.0 * tw * (1.0 - tw);
                Location step = new Location(w, x, y, z);

                // Obstruction fail-safe: never tunnel through blocks — if
                // the sampled point (feet or head) is inside something
                // solid, abort the hop and drop safely where we are.
                if (!step.getBlock().isPassable()
                        || !step.clone().add(0, 1, 0).getBlock().isPassable()) {
                    stopRide(p, true);
                    continue;
                }

                rideTeleport(p, step);
                p.setFallDistance(0f);
                if (!Tps.shouldDefer()) {
                    w.spawnParticle(Particle.CLOUD, step, 2, 0.1, 0.05, 0.1, 0.01);
                }

                if (t >= 1.0) {
                    // Landed exactly on the anchor. Terminal → dismount;
                    // otherwise a soft landing beat, then the dwell pause
                    // before the next hop.
                    r.anchorIdx += r.dir;
                    boolean terminal = r.dir > 0 ? r.anchorIdx >= n - 1 : r.anchorIdx <= 0;
                    if (terminal) { finishRide(p); continue; }
                    p.playSound(step, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.5f, 1.2f);
                    if (r.dwellTicks > 0) {
                        r.dwelling = true;
                        r.tick = 0;
                    } else {
                        beginHop(r);
                    }
                }
            }
        }, 1L, 1L);
    }

    /** Riders currently being moved BY the ride ticker this instant —
     *  lets {@code MobilityListener.onTeleport} distinguish the ride's
     *  own interpolation teleports (exempt) from external teleports
     *  (which rightly cancel the ride). */
    private final java.util.Set<UUID> rideMoves = new java.util.HashSet<>();

    /** True when {@code id}'s current teleport was issued by the ride. */
    public boolean isRideMove(UUID id) {
        return rideMoves.contains(id);
    }

    private void rideTeleport(Player p, Location to) {
        rideMoves.add(p.getUniqueId());
        try {
            // RELATIVE yaw/pitch (zero delta): the position is authoritative
            // but the client keeps full control of its own camera — an
            // absolute teleport every tick would snap the view back to the
            // server's stale rotation and read as a locked camera.
            p.teleport(to, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN,
                    io.papermc.paper.entity.TeleportFlag.Relative.YAW,
                    io.papermc.paper.entity.TeleportFlag.Relative.PITCH);
        } finally {
            rideMoves.remove(p.getUniqueId());
        }
    }
    /** Dismount at the end of a trail with a small forward hop. */
    private void finishRide(Player p) {
        Vector hop = p.getLocation().getDirection().setY(0.25).multiply(0.6);
        p.setVelocity(hop);
        stopRide(p, false);
        p.sendActionBar(Component.text("Fin de la piste.", NamedTextColor.AQUA));
    }
}
