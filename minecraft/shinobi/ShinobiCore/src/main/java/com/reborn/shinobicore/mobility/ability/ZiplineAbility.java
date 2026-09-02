package com.reborn.shinobicore.mobility.ability;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.chakra.ChakraManager;
import com.reborn.shinobicore.chakra.ChakraPool;
import com.reborn.shinobicore.mobility.cooldown.CooldownManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ninja Zipline — Pathfinder-ultimate-style deployable zipline.
 *
 * <h2>Placement</h2>
 * Left-click the Zipline de Ninja item. We ray-trace up to
 * {@code mobility.zipline.max-range} blocks along the player's aim; if
 * a solid block is hit we place two {@link Material#END_ROD} blocks —
 * one at the player's feet (the "departure" endpoint) and one on top of
 * the aimed-at block (the "arrival" endpoint) — and register the pair.
 * A particle line is drawn between the two rods every tick so the
 * zipline reads as a physical thing in the world.
 *
 * <h2>Activation</h2>
 * Walk within {@code activation-radius} of either endpoint and press F
 * (routed through {@code MobilityListener}'s swap-hands handler, which
 * checks {@link #tryActivateNear(Player)} before falling through to the
 * Naruto Run binding). The player is launched along a straight line
 * toward the opposite endpoint at fixed speed.
 *
 * <h2>Lifetime</h2>
 * Ziplines auto-despawn after {@code lifetime-ticks} (default 1200 =
 * 60 s). The two End Rod blocks revert to whatever block type was
 * there before placement, and the particle renderer drops the pair.
 * Placing a new zipline while an old one is still live replaces it —
 * one zipline per player at a time.
 *
 * <h2>HUD</h2>
 * Tagged {@link AbilityHudTag#COOLDOWN_SHOW} so the 30-second post-
 * placement cooldown surfaces in the Tableau Chakraique's Cooldowns
 * section.
 */
public class ZiplineAbility implements MobilityAbility {

    public static final String ID = "zipline";

    private final ShinobiCore plugin;
    private final ChakraManager chakra;
    private final CooldownManager cooldowns;

    /** Per-owner active zipline pair — max one live zipline per player. */
    private final Map<UUID, Pair> lines = new HashMap<>();

    /** Active travel sessions (player currently riding a zipline). */
    private final Map<UUID, Travel> traveling = new HashMap<>();

    /** Single scheduled task that renders particles for every live pair. */
    private BukkitTask renderTask;

    public ZiplineAbility(ShinobiCore plugin, ChakraManager chakra, CooldownManager cooldowns) {
        this.plugin = plugin;
        this.chakra = chakra;
        this.cooldowns = cooldowns;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Zipline"; }
    @Override public AbilityHudTag hudTag() { return AbilityHudTag.COOLDOWN_SHOW; }

    @Override public boolean isEnabled() {
        return plugin.getConfig().getBoolean("mobility.zipline.enabled", true);
    }

    /** Called from the module on enable to start the global renderer. */
    public void start() {
        if (renderTask != null) return;
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderAll, 5L, 5L);
    }

    public void shutdown() {
        if (renderTask != null) { renderTask.cancel(); renderTask = null; }
        // Snapshot and clear — removal mutates the map.
        for (UUID owner : new ArrayList<>(lines.keySet())) removeLineOf(owner);
        for (UUID traveler : new ArrayList<>(traveling.keySet())) {
            Travel t = traveling.remove(traveler);
            if (t != null && t.task != null) try { t.task.cancel(); } catch (Throwable ignore) {}
        }
    }

    /* ------------------------------------------------------------ placement */

    /**
     * Left-click entry. Ray-traces a target, verifies a zipline can be
     * stretched between there and the player's feet, places the two
     * End Rod blocks, and starts the lifetime timer.
     */
    @Override
    public boolean tryActivate(Player p) {
        UUID id = p.getUniqueId();
        if (!isEnabled()) {
            actionBar(p, "Zipline désactivée.", NamedTextColor.RED);
            return false;
        }
        if (!p.hasPermission("shinobicore.mobility.zipline")) {
            actionBar(p, "Tu n'as pas la permission d'utiliser la Zipline.", NamedTextColor.RED);
            return false;
        }
        long cdMs = cooldowns.remainingMillis(id, ID);
        if (cdMs > 0) {
            actionBar(p, "Zipline en recharge: " + String.format("%.1fs", cdMs / 1000.0),
                    NamedTextColor.YELLOW);
            return false;
        }

        double maxRange   = plugin.getConfig().getDouble("mobility.zipline.max-range", 90.0);
        double chakraCost = plugin.getConfig().getDouble("mobility.zipline.chakra-cost", 30.0);

        ChakraPool pc = chakra.get(p);
        if (!pc.has(chakraCost)) {
            actionBar(p, "Pas assez de chakra pour la Zipline (" + (int) chakraCost + " requis).",
                    NamedTextColor.RED);
            return false;
        }

        // Ray-trace the target, grabbing both the block and the face we
        // entered through. The face decides whether we fall back to a
        // horizontally-mounted arrival rod when the space above is
        // obstructed.
        RayHit hit = rayTraceHit(p, maxRange);
        if (hit == null) {
            actionBar(p, "Aucun bloc dans les " + (int) maxRange + " blocs.",
                    NamedTextColor.YELLOW);
            return false;
        }

        // Departure rod sits where the player is standing.
        Location pLoc = p.getLocation();
        Block departure = pLoc.getBlock();
        if (!isReplaceable(departure)) {
            actionBar(p, "L'emplacement de départ est obstrué.", NamedTextColor.RED);
            return false;
        }

        // Arrival placement — three passes, each stops at the first
        // replaceable slot:
        //   1. One block above the aimed block (typical floor-aim case).
        //   2. Up to +3 blocks above (walk around a small 1-3 block
        //      stack like a sapling, slab, or short column).
        //   3. Horizontal mount — attach the rod to the aimed block's
        //      hit-face, rod sitting in the air block one step out from
        //      that face. Lets you zipline to mid-wall targets.
        ArrivalPlacement arrival = findArrivalPlacement(hit);
        if (arrival == null) {
            actionBar(p, "Aucun emplacement libre près du bloc visé.",
                    NamedTextColor.RED);
            return false;
        }

        // Don't let the two rods share a block — a zero-length zipline is
        // nonsense and would divide by zero in the travel math.
        if (departure.getLocation().equals(arrival.block.getLocation())) {
            actionBar(p, "Vise plus loin pour poser une Zipline.",
                    NamedTextColor.YELLOW);
            return false;
        }

        if (!pc.consume(chakraCost)) return false;

        // Remove any existing zipline this player had — one live zipline
        // per player at a time.
        removeLineOf(id);

        Material prevA = departure.getType();
        Material prevB = arrival.block.getType();
        departure.setType(Material.END_ROD, false);
        setRodFacing(departure, BlockFace.UP);  // feet rod always stands up
        arrival.block.setType(Material.END_ROD, false);
        setRodFacing(arrival.block, arrival.facing);

        long lifeTicks = Math.max(20L,
                plugin.getConfig().getLong("mobility.zipline.lifetime-ticks", 1200L));
        Pair pair = new Pair(id, departure.getLocation(), arrival.block.getLocation(), prevA, prevB);
        lines.put(id, pair);

        // Lifetime task — auto-despawn when the timer runs out.
        pair.expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (lines.get(id) == pair) removeLineOf(id);
        }, lifeTicks);

        long cdOnPlace = plugin.getConfig().getLong("mobility.zipline.cooldown-ms", 30_000L);
        cooldowns.set(id, ID, cdOnPlace);

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 1.4f);
        actionBar(p, "Zipline posée — approche un End Rod et appuie sur F.",
                NamedTextColor.GREEN);
        return true;
    }

    /* ---------------------------------------------------------- activation */

    /**
     * Called from {@code MobilityListener.onSwapHands} before it routes
     * to the SWAP_HANDS binding. Three-stage lookup:
     *
     * <ol>
     *   <li>If the player is within {@code activation-radius} of an
     *       endpoint, travel toward the opposite endpoint. Same behaviour
     *       as the first deploy.</li>
     *   <li>Otherwise, if the player is within {@code activation-radius}
     *       of the zipline's line segment (i.e. somewhere mid-line —
     *       typical when they just cancelled a ride with F and want to
     *       re-hook), pick the target endpoint based on which way they're
     *       looking along the rope. Dot product of their look vector with
     *       the A→B axis: positive → head to B, negative → head to A.</li>
     *   <li>No match → false, the listener falls through to whatever
     *       SWAP_HANDS is bound to.</li>
     * </ol>
     */
    public boolean tryActivateNear(Player p) {
        UUID id = p.getUniqueId();
        if (traveling.containsKey(id)) return false;  // already riding

        double actRadius = plugin.getConfig().getDouble("mobility.zipline.activation-radius", 3.0);
        double r2 = actRadius * actRadius;
        Location pLoc = p.getLocation();

        for (Pair pair : lines.values()) {
            if (pair.rodA.getWorld() != pLoc.getWorld()) continue;

            // (1) Endpoint proximity — preferred when the player is AT a rod.
            Location centerA = centerOf(pair.rodA);
            Location centerB = centerOf(pair.rodB);
            double d2A = pLoc.distanceSquared(centerA);
            double d2B = pLoc.distanceSquared(centerB);
            if (d2A <= r2 || d2B <= r2) {
                Location far = (d2A <= d2B) ? pair.rodB : pair.rodA;
                startTravel(p, far);
                return true;
            }

            // (2) Line-proximity — re-hook after a mid-ride F cancel.
            // Project the player's position onto the segment A-B, find
            // the closest point, check distance. Within range → pick
            // the target end based on look direction along the rope.
            Vector a = centerA.toVector();
            Vector b = centerB.toVector();
            Vector ab = b.clone().subtract(a);
            double abLenSq = ab.lengthSquared();
            if (abLenSq < 1e-6) continue;  // degenerate zipline (shouldn't happen)
            Vector ap = pLoc.toVector().subtract(a);
            double t = Math.max(0.0, Math.min(1.0, ap.dot(ab) / abLenSq));
            Vector closest = a.clone().add(ab.clone().multiply(t));
            double d2line = pLoc.toVector().distanceSquared(closest);
            if (d2line > r2) continue;

            // Player is on the rope. Which way are they heading?
            Vector look = p.getLocation().getDirection();
            double alongRope = look.dot(ab.normalize());
            Location far = alongRope >= 0.0 ? centerB : centerA;
            startTravel(p, far);
            return true;
        }
        return false;
    }

    private void startTravel(Player p, Location far) {
        UUID id = p.getUniqueId();
        double speed = plugin.getConfig().getDouble("mobility.zipline.travel-speed", 1.5);
        long maxTicks = Math.max(20L,
                plugin.getConfig().getLong("mobility.zipline.max-travel-ticks", 200L));

        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.5f);

        Travel t = new Travel(far);
        traveling.put(id, t);
        t.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickTravel(p, t, speed, maxTicks), 1L, 1L);
    }

    private void tickTravel(Player p, Travel t, double speed, long maxTicks) {
        UUID id = p.getUniqueId();
        if (!p.isOnline() || p.isDead()) { cancelTravel(id); return; }
        if (traveling.get(id) != t) { if (t.task != null) t.task.cancel(); return; }
        if (++t.ticks > maxTicks)  { cancelTravel(id); return; }

        Vector toEnd = centerOf(t.farEnd).toVector().subtract(p.getLocation().toVector());
        double dist = toEnd.length();
        if (dist < 1.5) { cancelTravel(id); return; }

        // Straight-line motion: velocity = unit-direction × speed.
        // Fixed speed (no momentum blending) is what gives the
        // Pathfinder-zipline its glide feel — predictable, mounted-on-
        // rail rather than physical rope.
        Vector v = toEnd.multiply(1.0 / dist).multiply(speed);
        p.setVelocity(v);
        p.setFallDistance(0f);

        // Sparkle trail behind the traveling player for visual feedback.
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.0, 0),
                2, 0.05, 0.05, 0.05, 0);
    }

    /**
     * Cancel an active ride if the player is in one. Public so the F-key
     * handler in {@code MobilityListener} can invoke it directly — a
     * first F-press during a ride cancels; a second F-press near the
     * rope (handled by {@link #tryActivateNear}) re-attaches in whatever
     * direction the player is now looking.
     *
     * @return true if there was an active ride to cancel.
     */
    public boolean cancelTravel(UUID id) {
        Travel t = traveling.remove(id);
        if (t == null) return false;
        if (t.task != null) try { t.task.cancel(); } catch (Throwable ignore) {}
        return true;
    }

    /** True while the given player is actively riding a zipline. */
    public boolean isTraveling(UUID id) { return traveling.containsKey(id); }

    /* --------------------------------------------------------- lifecycle */

    /** Cleanly remove a player's zipline — restore the two block
     *  positions, cancel the expire task, drop the pair from the map. */
    public void removeLineOf(UUID owner) {
        Pair pair = lines.remove(owner);
        if (pair == null) return;
        if (pair.expireTask != null) try { pair.expireTask.cancel(); } catch (Throwable ignore) {}
        Block a = pair.rodA.getBlock();
        Block b = pair.rodB.getBlock();
        // Only revert if the block is still the End Rod we placed — if
        // someone broke it or replaced it, leave their change alone.
        if (a.getType() == Material.END_ROD) a.setType(pair.prevA, false);
        if (b.getType() == Material.END_ROD) b.setType(pair.prevB, false);
    }

    /* ------------------------------------------------------------ render */

    private void renderAll() {
        if (lines.isEmpty()) return;
        for (Pair pair : lines.values()) {
            World w = pair.rodA.getWorld();
            if (w == null || w != pair.rodB.getWorld()) continue;
            Location a = centerOf(pair.rodA);
            Location b = centerOf(pair.rodB);
            Vector diff = b.toVector().subtract(a.toVector());
            double len = diff.length();
            if (len < 0.01) continue;
            // Sample points along the line every 0.75 blocks.
            int steps = Math.max(2, (int) (len / 0.75));
            Vector step = diff.multiply(1.0 / steps);
            for (int i = 1; i < steps; i++) {
                Location pt = a.clone().add(step.clone().multiply(i));
                w.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0);
            }
        }
    }

    /* ------------------------------------------------------------ helpers */

    /**
     * Pick where the arrival End Rod should go.
     *
     * <p>Three-stage fallback:
     * <ol>
     *   <li>Look at {@code UP+1, UP+2, UP+3} above the aimed block. The
     *       first replaceable slot wins — an upward-facing rod sitting
     *       on top of whatever stack is there (handles floor aim, a
     *       single-block obstruction, a 2-3-block column).</li>
     *   <li>If all three above are occupied, attach horizontally on the
     *       block's hit face: the rod sits in the air block one step
     *       out from that face, with its facing set to the hit face so
     *       it protrudes away from the wall.</li>
     *   <li>Still no room → {@code null}. Caller rejects the deploy.</li>
     * </ol>
     */
    private ArrivalPlacement findArrivalPlacement(RayHit hit) {
        Block aimed = hit.block;

        // (1) Try up-stack, 1 → 3 blocks above the aimed block.
        for (int up = 1; up <= 3; up++) {
            Block candidate = aimed.getRelative(0, up, 0);
            if (isReplaceable(candidate)) {
                return new ArrivalPlacement(candidate, BlockFace.UP);
            }
        }

        // (2) Horizontal mount — on the hit face. Skip UP/DOWN: vertical
        // hit-faces are the "I aimed up at a ceiling or down at a floor"
        // cases, already covered by the up-stack pass for UP; DOWN would
        // leave the rod awkwardly hanging. For those, we bail to (3).
        BlockFace face = hit.face;
        if (face != BlockFace.UP && face != BlockFace.DOWN) {
            Block outward = aimed.getRelative(face);
            if (isReplaceable(outward)) {
                return new ArrivalPlacement(outward, face);
            }
        }

        return null;
    }

    /** Stamp the correct {@link Directional#setFacing} on an End Rod
     *  block we just placed. Paper's BlockData for End Rod is a
     *  {@link Directional} wrapper; silent no-op if the cast fails (a
     *  future Material change or data-pack override). */
    private static void setRodFacing(Block rodBlock, BlockFace facing) {
        BlockData data = rodBlock.getBlockData();
        if (data instanceof Directional dir) {
            dir.setFacing(facing);
            rodBlock.setBlockData(dir, false);
        }
    }

    /** Target block for the arrival rod + the face its rod should
     *  point along. Facing = UP for stacked placements, or the hit
     *  face for horizontal mounts. */
    private record ArrivalPlacement(Block block, BlockFace facing) {}

    /**
     * Walk the player's look vector in 0.25-block steps to find the first
     * solid, non-passable block. Also tracks the last passable block the
     * ray was in, so we can compute which face it entered through
     * ({@code hitBlock.getFace(previousAirBlock)}). That face is what we
     * attach a horizontally-oriented End Rod to if the space above the
     * aimed block is obstructed.
     *
     * <p>Same step-by-step approach as the grapple ray-trace — sidesteps
     * Paper interaction-reach quirks and guarantees the configured range.
     */
    private RayHit rayTraceHit(Player p, double range) {
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection();
        if (dir.lengthSquared() < 1e-6) return null;
        dir.normalize();

        double step = 0.25;
        int maxSteps = (int) Math.ceil(range / step);
        Block lastPassable = null;
        for (int i = 1; i <= maxSteps; i++) {
            Location pt = start.clone().add(dir.clone().multiply(i * step));
            Block block = pt.getBlock();
            if (block == null) continue;
            Material mat = block.getType();
            if (mat.isAir() || block.isPassable()) {
                lastPassable = block;
                continue;
            }
            // Entered a solid block — the face is the side we came in
            // from. getFace returns null for non-adjacent blocks (e.g.
            // when the step skipped diagonally) — fall back to UP in
            // that case so the caller always has something to attach to.
            BlockFace face = lastPassable != null ? block.getFace(lastPassable) : null;
            if (face == null) face = BlockFace.UP;
            return new RayHit(block, face);
        }
        return null;
    }

    /** Bundle of a hit block + the face the ray entered through. */
    private record RayHit(Block block, BlockFace face) {}

    /** True if the given block is safe to overwrite with an End Rod — air
     *  or other replaceable blocks like tall grass, light, water, snow. */
    private static boolean isReplaceable(Block b) {
        Material t = b.getType();
        return t.isAir() || b.isReplaceable();
    }

    /** The centre of the End Rod block (offset 0.5 on all axes) — used for
     *  travel targeting + activation-radius checks so the endpoint is the
     *  rod's visual centre, not its block corner. */
    private static Location centerOf(Location rodBlockLocation) {
        return rodBlockLocation.clone().add(0.5, 0.5, 0.5);
    }

    private static void actionBar(Player p, String text, NamedTextColor colour) {
        if (p == null || !p.isOnline()) return;
        p.sendActionBar(Component.text(text, colour));
    }

    /* --------------------------------------------------------- inner types */

    private static final class Pair {
        final UUID owner;
        final Location rodA, rodB;
        final Material prevA, prevB;
        BukkitTask expireTask;
        Pair(UUID owner, Location rodA, Location rodB,
             Material prevA, Material prevB) {
            this.owner = owner;
            this.rodA = rodA; this.rodB = rodB;
            this.prevA = prevA; this.prevB = prevB;
        }
    }

    private static final class Travel {
        final Location farEnd;
        BukkitTask task;
        long ticks = 0L;
        Travel(Location farEnd) { this.farEnd = farEnd; }
    }
}
