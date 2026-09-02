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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Grappin de Ninja — Pathfinder-style grappling hook.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Left-click with the Grappin item → ray-trace up to
 *       {@code mobility.grapple.max-range} blocks along the player's
 *       aim. If a solid block is hit, that block's impact point becomes
 *       the anchor.</li>
 *   <li>Each tick the player's velocity is blended toward the anchor:
 *       {@code next = current * drag + dir * pull}, clamped to
 *       {@code max-speed}. Gravity still applies, so short horizontal
 *       grapples swing naturally; vertical anchors pull straight up.</li>
 *   <li>A particle-rope line is drawn from the player's eye to the
 *       anchor every tick so the grapple has a visual trail.</li>
 *   <li>The session ends on reach (within {@code reach-distance} blocks),
 *       timeout, or manual re-click. On end, a {@code cooldown-ms} CD is
 *       armed on {@link CooldownManager} — per the design spec the CD
 *       starts when the grapple ENDS, not when it fires.</li>
 * </ol>
 *
 * <h2>Trigger — left-click on a non-interactive item</h2>
 * The Grappin is a reskinned {@link org.bukkit.Material#STICK}: no
 * vanilla use-behaviour, no interaction-reach cap. Left-click fires
 * {@code PlayerInteractEvent} regardless of what's under the crosshair,
 * so our ray-trace gets the full configured range. Dispatch lives in
 * {@code GrappleListener} (this class stays focused on physics +
 * cooldown + HUD metadata).
 */
public class GrappleAbility implements MobilityAbility {

    public static final String ID = "grapple";

    private final ShinobiCore plugin;
    private final ChakraManager chakra;
    private final CooldownManager cooldowns;

    /** One session per player. */
    private final Map<UUID, Session> active = new HashMap<>();

    public GrappleAbility(ShinobiCore plugin, ChakraManager chakra,
                          CooldownManager cooldowns) {
        this.plugin = plugin;
        this.chakra = chakra;
        this.cooldowns = cooldowns;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Grappin"; }
    @Override public AbilityHudTag hudTag() { return AbilityHudTag.COOLDOWN_SHOW; }

    @Override public boolean isEnabled() {
        return plugin.getConfig().getBoolean("mobility.grapple.enabled", true);
    }

    /** True if {@code p} currently has a live grapple session. */
    public boolean isGrappling(UUID id) { return active.containsKey(id); }

    /* -------------------------------------------------------------- fire */

    /**
     * Left-click entry point. Re-clicks during an active grapple <em>end</em>
     * the session (manual detach); otherwise tries to fire a new one.
     * @return true if either a grapple started OR an active one was ended.
     */
    @Override
    public boolean tryActivate(Player p) {
        UUID id = p.getUniqueId();

        // Re-click during active grapple → detach manually.
        if (active.containsKey(id)) {
            end(p, /*armCooldown=*/true, /*reason=*/"manual");
            return true;
        }

        if (!isEnabled()) {
            actionBar(p, "Grappin désactivé.", NamedTextColor.RED);
            return false;
        }
        if (!p.hasPermission("shinobicore.mobility.grapple")) {
            actionBar(p, "Tu n'as pas la permission d'utiliser le Grappin.", NamedTextColor.RED);
            return false;
        }
        long cdRemaining = cooldowns.remainingMillis(id, ID);
        if (cdRemaining > 0) {
            actionBar(p, "Grappin en recharge: " + String.format("%.1fs", cdRemaining / 1000.0),
                    NamedTextColor.YELLOW);
            return false;
        }

        double maxRange   = plugin.getConfig().getDouble("mobility.grapple.max-range", 60.0);
        double chakraCost = plugin.getConfig().getDouble("mobility.grapple.chakra-cost", 15.0);

        ChakraPool pc = chakra.get(p);
        if (!pc.has(chakraCost)) {
            actionBar(p, "Pas assez de chakra pour le Grappin (" + (int) chakraCost + " requis).",
                    NamedTextColor.RED);
            return false;
        }

        Location anchor = rayTraceAnchor(p, maxRange);
        if (anchor == null) {
            // Fire-into-the-void: no anchor. Don't consume chakra or arm a
            // cooldown — the hook visibly "misses" and the player can retry.
            actionBar(p, "Aucune cible dans les " + (int) maxRange + " blocs.",
                    NamedTextColor.YELLOW);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 1.2f);
            return false;
        }

        if (!pc.consume(chakraCost)) return false;

        // Fire-time player→anchor vector — reused for rope length,
        // initial kick (applied AFTER the latch delay), and the
        // overshoot-detach heuristic.
        Vector toAnchor = anchor.toVector().subtract(p.getLocation().toVector());
        double ropeLen = toAnchor.length();
        Vector fireDir = ropeLen > 1e-6 ? toAnchor.clone().multiply(1.0 / ropeLen) : new Vector(0, 1, 0);

        int latchDelay = Math.max(0,
                plugin.getConfig().getInt("mobility.grapple.latch-delay-ticks", 4));

        // Note: we deliberately do NOT apply the initial velocity kick
        // here. It's deferred until the latch delay expires so the
        // "hook landed, about to yank" beat reads as intentional — the
        // player sees the rope stick to the block for a few frames, THEN
        // gets launched. Kick application lives in tick() guarded by the
        // Session.kicked flag.

        Session s = new Session(anchor, ropeLen, fireDir, latchDelay);
        active.put(id, s);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.3f);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(p, s), 1L, 1L);
        return true;
    }

    /* -------------------------------------------------------------- tick */

    /**
     * Pendulum-rope physics. The anchor acts like a pin, the player is
     * held on the end of an invisible fixed-length rope, and gravity is
     * the only restoring force — so the player swings naturally, can
     * loop all the way round with enough speed, and steers around
     * obstacles as the rope's circular constraint carries them.
     *
     * <h3>Constraint model</h3>
     * Let {@code ropeDir} be the unit vector player → anchor. Split the
     * player's current velocity into the component <em>along</em> the
     * rope ({@code radial}) and the component <em>perpendicular</em> to
     * it ({@code tangent}).
     * <ul>
     *   <li>If {@code dist <= ropeLength} — rope is slack, the player
     *       moves freely; we only apply an optional reel-in force
     *       ({@code pull-strength}, defaults to 0 for pure pendulum
     *       behaviour) and a tiny drag.</li>
     *   <li>If {@code dist > ropeLength} — rope is taut. We zero out the
     *       outward radial velocity (the rope physically can't stretch)
     *       and add a gentle correction pull back toward the sphere
     *       surface so positional drift doesn't accumulate. Tangential
     *       velocity is preserved, which is what gives the pendulum its
     *       swing and lets momentum carry the player around loops.</li>
     * </ul>
     * Server-side gravity keeps applying normally between our
     * {@code setVelocity} calls, so the pendulum decelerates on the way
     * up and accelerates on the way down — exactly like a vine swing.
     */
    private void tick(Player p, Session s) {
        UUID id = p.getUniqueId();
        if (!p.isOnline() || p.isDead()) { end(p, false, "offline"); return; }
        if (active.get(id) != s) { cancelTask(s); return; }

        long   maxT      = plugin.getConfig().getLong("mobility.grapple.max-ticks", 100L);
        double pull      = plugin.getConfig().getDouble("mobility.grapple.pull-strength", 0.25);
        double drag      = plugin.getConfig().getDouble("mobility.grapple.drag", 0.99);
        double maxSpd    = plugin.getConfig().getDouble("mobility.grapple.max-speed", 4.0);
        double correct   = plugin.getConfig().getDouble("mobility.grapple.correction-strength", 0.12);
        double reach     = plugin.getConfig().getDouble("mobility.grapple.reach-distance", 2.0);
        double camBias   = plugin.getConfig().getDouble("mobility.grapple.camera-bias", 0.5);
        double airCtrl   = plugin.getConfig().getDouble("mobility.grapple.air-control", 0.06);
        double align     = plugin.getConfig().getDouble("mobility.grapple.pull-alignment", 0.7);
        boolean detachPast = plugin.getConfig().getBoolean("mobility.grapple.detach-on-overshoot", true);

        // ---- Latch delay
        //
        // During the first N ticks after fire we show the rope but apply
        // NO forces: the "hook has landed, but the winch hasn't engaged
        // yet" beat. Timeout counts, but the initial kick and every
        // other physics step sit this out. On the tick right after the
        // delay expires, we apply the deferred initial kick once.
        if (s.ticks < s.latchDelayTicks) {
            s.ticks++;
            spawnRope(p, s.anchor);
            return;
        }
        if (!s.kicked) {
            double boost = plugin.getConfig().getDouble("mobility.grapple.initial-boost", 2.0);
            Vector kickDir = s.anchor.toVector().subtract(p.getLocation().toVector());
            if (kickDir.lengthSquared() > 1e-6) {
                kickDir.normalize().multiply(boost);
                p.setVelocity(p.getVelocity().add(kickDir));
            }
            s.kicked = true;
        }

        if (++s.ticks > maxT) { end(p, true, "timeout"); return; }

        Vector playerVec = p.getLocation().toVector();
        Vector anchorVec = s.anchor.toVector();
        Vector toAnchor  = anchorVec.clone().subtract(playerVec);
        double dist = toAnchor.length();
        if (dist < reach) { end(p, true, "arrived"); return; }

        // "Past the anchor" detach (Pathfinder dissection): once the
        // projection of (anchor − player) onto the original fire
        // direction flips negative, the player has overshot the anchor
        // plane. In Apex this triggers release automatically. Controlled
        // by detach-on-overshoot; default on.
        if (detachPast && toAnchor.dot(s.fireDirection) < 0.0) {
            end(p, true, "past anchor");
            return;
        }

        Vector ropeDir = toAnchor.clone().multiply(1.0 / dist);  // unit player→anchor
        Vector vel = p.getVelocity().clone();

        // ---- Pull vector: blend of (player→anchor) and (camera forward)
        //
        // This is the headline insight from the Pathfinder grapple
        // dissection: the pull isn't pure player→anchor, it's a weighted
        // mix of that and where the camera is aiming. Looking to the
        // right while grappling forward makes the pull curve right, so
        // you can carve around an obstacle instead of slamming into it.
        //
        // camera-bias = 0.0 → pure "pull to anchor" (rigid zip-line feel)
        // camera-bias = 1.0 → pure "pull where I look" (very steerable,
        //                     loose feel — you can fight the anchor)
        // 0.5 is the Apex-style sweet spot.
        //
        // The pull MAGNITUDE is aim-gated by pull-alignment: full when
        // you're looking at the anchor, scaled down as you look away,
        // so firing at the floor and then looking up lets you break out
        // of the pull with whatever momentum you've already built up.
        // This is the "engage approach" feel — commit while aiming,
        // coast once you've disengaged your aim.
        if (pull > 0.0) {
            Vector cameraDir = p.getLocation().getDirection();
            Vector cameraUnit = cameraDir.lengthSquared() > 1e-6
                    ? cameraDir.clone().normalize() : null;
            Vector pullDir;
            if (cameraUnit == null || camBias <= 0.0) {
                pullDir = ropeDir;
            } else if (camBias >= 1.0) {
                pullDir = cameraUnit;
            } else {
                pullDir = ropeDir.clone().multiply(1.0 - camBias)
                        .add(cameraUnit.clone().multiply(camBias));
                if (pullDir.lengthSquared() < 1e-6) pullDir = ropeDir;
                else pullDir.normalize();
            }

            // Aim-gating: alignment weights how much "looking at the
            // anchor" is required for full pull. alignment=0 → always
            // full pull (classic). alignment=1 → pull scales from 0
            // (perpendicular or away) to full (looking straight at
            // anchor). Default 0.7 keeps a baseline pull while giving
            // the player real agency to break off by looking away.
            double aimDot = cameraUnit != null ? Math.max(0.0, cameraUnit.dot(ropeDir)) : 1.0;
            double effectivePull = pull * ((1.0 - align) + align * aimDot);
            if (effectivePull > 0.0) {
                vel.add(pullDir.multiply(effectivePull));
            }
        }

        // ---- Air control: tangential slide along the sphere surface
        //
        // Project the look direction onto the plane perpendicular to
        // the rope. The remaining "tangent look" is a unit vector
        // sitting on the sphere's surface in the direction the player
        // is aiming. Pushing velocity along it lets the player slide
        // around the sphere's curve — which is what "carve around a
        // wall on the right" feels like. Because this vector is
        // guaranteed to be perpendicular to the rope, it can't fight
        // the pull: it only adds tangential motion.
        if (airCtrl > 0.0) {
            Vector look = p.getLocation().getDirection();
            double lookRadial = look.dot(ropeDir);
            Vector tangentLook = look.clone().subtract(ropeDir.clone().multiply(lookRadial));
            double tLen = tangentLook.length();
            if (tLen > 1e-4) {
                // Normalize + scale in one step — avoids a second sqrt.
                vel.add(tangentLook.multiply(airCtrl / tLen));
            }
        }

        if (dist > s.ropeLength) {
            // Rope taut — enforce the constraint. Note this uses the
            // actual player→anchor direction, NOT the blended pull
            // direction: the rope is a physical constraint so it
            // operates on geometry, not on where the camera is aiming.
            double radialSpeed = vel.dot(ropeDir); // + toward anchor, − away
            if (radialSpeed < 0.0) {
                // Moving AWAY: subtract the radial component so the
                // player can't physically drift further out. Tangential
                // velocity is untouched, which is what produces the
                // swing.
                vel.subtract(ropeDir.clone().multiply(radialSpeed));
            }
            // Positional correction — pull back toward the sphere
            // surface proportional to overshoot. Keeps small numerical
            // drift from accumulating into a stretched rope.
            double overshoot = dist - s.ropeLength;
            vel.add(ropeDir.clone().multiply(overshoot * correct));
        }

        // Mild drag — preserves most momentum so a fast swing can 360.
        vel.multiply(drag);
        // Safety cap — keeps wild edge cases from launching the player
        // into the void if the correction ever over-corrects.
        if (vel.length() > maxSpd) vel = vel.normalize().multiply(maxSpd);

        p.setVelocity(vel);
        p.setFallDistance(0f);

        spawnRope(p, s.anchor);
    }

    /* -------------------------------------------------------------- end */

    /**
     * End the session, optionally arming the cooldown. Callers pass
     * {@code armCooldown=false} for non-grapple-relevant terminations
     * (player logged out, server shutting down) so the player isn't
     * penalised for circumstances they didn't control.
     */
    public void end(Player p, boolean armCooldown, String reason) {
        UUID id = p.getUniqueId();
        Session s = active.remove(id);
        if (s == null) return;
        cancelTask(s);
        if (!armCooldown) return;
        long cdMs = plugin.getConfig().getLong("mobility.grapple.cooldown-ms", 10_000L);
        cooldowns.set(id, ID, cdMs);
        if (p != null && p.isOnline()) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 1.4f);
        }
    }

    private void cancelTask(Session s) {
        if (s.task != null) {
            try { s.task.cancel(); } catch (Throwable ignore) {}
            s.task = null;
        }
    }

    /** Clean shutdown called from {@code ShinobiMobilityModule.disable()}. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(id);
            end(p, /*armCooldown=*/false, "shutdown");
        }
    }

    /* ---------------------------------------------------------- helpers */

    /**
     * Manual stepped ray-trace along the player's look vector.
     *
     * <p><b>Why not {@code p.rayTraceBlocks}?</b> Across a handful of
     * Paper builds the entity-scoped overload has been observed to
     * soft-cap on something close to vanilla interaction reach (~4.5
     * blocks), so a 30-block call returns null for anything past the
     * vanilla distance. We iterate blocks ourselves in small steps — same
     * cost as a hundred-odd lookups on the block hashtable, runs only
     * once per grapple fire (not per tick), and is guaranteed to cover
     * the configured range.
     *
     * <p>Stop conditions per step:
     * <ul>
     *   <li>{@link Material#isAir()} → skip (continue ray).</li>
     *   <li>{@link Block#isPassable()} → skip (tall grass, flowers,
     *       torches — things the player can physically walk through).</li>
     *   <li>Otherwise → hit; return the exact ray position (not the
     *       block centre) so the grapple pulls the player to where the
     *       rope visually strikes.</li>
     * </ul>
     */
    private Location rayTraceAnchor(Player p, double range) {
        Location start = p.getEyeLocation();
        Vector dir = start.getDirection();
        if (dir.lengthSquared() < 1e-6) return null;
        dir.normalize();

        // 0.25-block steps give sub-block resolution — fine enough to
        // catch slabs / stairs without iterating forever on long shots.
        double step = 0.25;
        int maxSteps = (int) Math.ceil(range / step);

        for (int i = 1; i <= maxSteps; i++) {
            double t = i * step;
            Location point = start.clone().add(dir.clone().multiply(t));
            Block block = point.getBlock();
            if (block == null) continue;
            Material mat = block.getType();
            if (mat.isAir()) continue;
            if (block.isPassable()) continue;
            return point;
        }
        return null;
    }

    /** Short action-bar feedback — used for every silent-fail path so the
     *  player always knows why a click didn't latch. Null-safe against
     *  the rare case where the player is somehow offline mid-call. */
    private static void actionBar(Player p, String text, NamedTextColor colour) {
        if (p == null || !p.isOnline()) return;
        p.sendActionBar(Component.text(text, colour));
    }

    /**
     * Draw a line of particles from the player's eye to the anchor.
     * END_ROD is bright and readable against most terrain; step size is
     * half a block so short grapples still look like a rope rather than
     * discrete dots.
     */
    private void spawnRope(Player p, Location anchor) {
        Location start = p.getEyeLocation();
        Vector diff = anchor.toVector().subtract(start.toVector());
        double length = diff.length();
        if (length < 0.01) return;
        int steps = Math.max(2, (int) (length / 0.5));
        Vector step = diff.multiply(1.0 / steps);
        for (int i = 1; i < steps; i++) {  // skip i=0 (player's eye) and i=steps (anchor)
            Location point = start.clone().add(step.clone().multiply(i));
            p.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }

    /* --------------------------------------------------------- session */

    /** Per-player grapple state. Kept package-private for clarity — only
     *  this ability reads it.
     *
     *  <p>Captured once at fire-time:
     *  <ul>
     *    <li>{@code ropeLength} — the fixed-length rope-constraint
     *        radius. Acts like a real rope pinned to the anchor.</li>
     *    <li>{@code fireDirection} — unit vector from the firing player
     *        toward the anchor, used by the overshoot-detach heuristic
     *        from the Pathfinder dissection video: once the projection of
     *        (anchor − player) onto this direction flips negative the
     *        player has swung past the anchor plane and we release.</li>
     *    <li>{@code latchDelayTicks} — how many ticks of "hook has
     *        landed but the pull hasn't started yet". During this window
     *        the rope visual draws but no forces are applied — a deliberate
     *        beat between impact and yank.</li>
     *  </ul>
     *  <p>{@code kicked} flips true the first tick AFTER the latch
     *  delay: that's the tick on which the initial velocity boost is
     *  applied and regular pull physics take over.
     */
    private static final class Session {
        final Location anchor;
        final double ropeLength;
        final Vector fireDirection;
        final int latchDelayTicks;
        BukkitTask task;
        long ticks = 0L;
        boolean kicked = false;
        Session(Location anchor, double ropeLength, Vector fireDirection, int latchDelayTicks) {
            this.anchor = anchor;
            this.ropeLength = ropeLength;
            this.fireDirection = fireDirection;
            this.latchDelayTicks = latchDelayTicks;
        }
    }
}
