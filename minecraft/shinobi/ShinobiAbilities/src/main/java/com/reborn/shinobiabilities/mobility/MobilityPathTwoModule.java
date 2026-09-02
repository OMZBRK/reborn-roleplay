package com.reborn.shinobiabilities.mobility;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.util.Tps;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>Voie du Flux</b> — Path Two. A momentum / free-running model inspired by
 * Hytale's MovementConfig, reproduced server-side: client-predicted attributes
 * (movement-speed, jump-strength, gravity, step-height) for the smooth parts,
 * a Quake-style wish-direction air-strafe for air control, momentum-directional
 * leaps, plus slide and roll. Active only when the active character knows
 * {@link MobilityPaths.Path#TWO}.
 *
 * <h2>Run gate (Naruto Run lives here now)</h2>
 * The speed / jump / air-control boosts only apply while the player's
 * <em>Naruto Run</em> is active (sneak-tap to toggle). With the run OFF the
 * player walks vanilla and a <b>double-tap of space</b> performs a leap. With
 * the run ON, the speed boost is live and a <b>single space</b> leaps. Either
 * way the leap goes in the direction you are actually <em>moving</em> (back =
 * back, strafe = sideways) and gains height when you look up; if you are not in
 * motion, space is just a normal Minecraft jump.
 */
public final class MobilityPathTwoModule implements Listener {

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final MobilityPaths paths;
    private final NarutoRun narutoRun;

    private final NamespacedKey speedKey;
    private final NamespacedKey jumpKey;
    private final NamespacedKey gravityKey;
    private final NamespacedKey stepKey;

    private final Map<UUID, Double> momentum = new HashMap<>();
    private final Set<UUID> applied = new HashSet<>();
    private final Map<UUID, Long> sliding = new HashMap<>();
    /** Players who've used their mid-air leap (reset on ground). */
    private final Set<UUID> djUsed = new HashSet<>();
    /** Ground sneak-press timestamps — tap (toggle run) vs hold (crouch). */
    private final Map<UUID, Long> sneakPressAt = new HashMap<>();
    /** Short window after a leap where air-strafe stands down so the leap's
     *  motion direction carries instead of being re-aimed toward the look. */
    private final Map<UUID, Long> leapLock = new HashMap<>();
    /** Real horizontal movement direction+speed (from position deltas, since
     *  {@code getVelocity()} is unreliable for WASD-driven players). */
    private final Map<UUID, Vector> moveDir = new HashMap<>();
    private final Map<UUID, Long> moveAt = new HashMap<>();
    /** Vertical Δv still purchasable this air session — the arc budget
     *  behind the look-up rise. Granted on a real landing, drains with
     *  airtime, spent by {@link #rise}; empty = gravity wins. */
    private final Map<UUID, Double> riseBudget = new HashMap<>();
    /** Players currently in a look-up climb (for the pump-cut edge). */
    private final Set<UUID> rising = new HashSet<>();
    /** Consecutive airborne ticks — landing detection, the momentum
     *  grace clock, and ledge-lip isOnGround-flicker grace. */
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    /** Consecutive grounded ticks — a contact only counts as SOLID
     *  ground (grace reset, budget refill) after a few of these. */
    private final Map<UUID, Integer> groundTicks = new HashMap<>();
    /** Players in a committed look-down dive — decides plant vs flow
     *  at ground contact. */
    private final Set<UUID> diveCommitted = new HashSet<>();
    /** Recent committed-landing timestamps — {@link #onFall} skips the
     *  roll carry for a plant. */
    private final Map<UUID, Long> plantedAt = new HashMap<>();

    private BukkitTask ticker;

    public MobilityPathTwoModule(JavaPlugin plugin, CoreServices core,
                                 MobilityPaths paths, NarutoRun narutoRun) {
        this.plugin = plugin;
        this.core = core;
        this.paths = paths;
        this.narutoRun = narutoRun;
        this.speedKey = new NamespacedKey(plugin, "flux_speed");
        this.jumpKey = new NamespacedKey(plugin, "flux_jump");
        this.gravityKey = new NamespacedKey(plugin, "flux_gravity");
        this.stepKey = new NamespacedKey(plugin, "flux_step");
    }

    private double cfg(String k, double def) { return plugin.getConfig().getDouble("mobility.path-two." + k, def); }

    public void start() {
        stop();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }
    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (UUID id : new HashSet<>(applied)) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) reset(p);
        }
    }

    private boolean active(Player p) {
        if (p == null || !p.isOnline()) return false;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return false;
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return false;
        if (p.isInWater()) return false;   // Voie du Flux & Naruto Run stand down in water
        return paths.pathOf(p) == MobilityPaths.Path.TWO;
    }

    private boolean running(Player p) { return narutoRun != null && narutoRun.isActive(p); }

    /** Track real WASD movement from position deltas — {@code getVelocity()} is
     *  near-zero/stale for players, so it can't tell us which way they're going. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        Vector d = e.getTo().toVector().subtract(e.getFrom().toVector()).setY(0);
        if (d.lengthSquared() < 0.0009) return;   // <0.03/tick — look-only or near-stationary
        moveDir.put(p.getUniqueId(), d);
        moveAt.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Recent horizontal movement (direction + per-tick speed); zero if stale. */
    private Vector motionH(Player p) {
        UUID id = p.getUniqueId();
        Long at = moveAt.get(id);
        Vector d = moveDir.get(id);
        if (d == null || at == null || System.currentTimeMillis() - at > 300L) return new Vector(0, 0, 0);
        return d.clone();
    }

    /* -------------------------------------------------------------- ticker */

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (active(p)) {
                if (!Tps.shouldDefer()) applyFlux(p);
            } else if (applied.contains(p.getUniqueId())) {
                reset(p);
            }
        }
    }

    private void applyFlux(Player p) {
        UUID id = p.getUniqueId();
        applied.add(id);
        boolean onGround = p.isOnGround();
        boolean run = running(p);

        Vector v = p.getVelocity();
        Vector h = new Vector(v.getX(), 0, v.getZ());
        double hspeed = h.length();

        // --- momentum ramp ---
        // Ground BUILDS momentum (Naruto-run + real motion, gated on
        // position deltas — getVelocity() is near-zero for WASD players)
        // and drains it by friction when the run/motion stops. Air NEVER
        // hard-stops it any more: it bleeds gently (momentum-decay-per-
        // tick), and the EFFECTIVE value below never drops under the
        // floor while the run is live — so a re-jump straight after
        // landing still has real pop instead of a dead hop.
        double m = momentum.getOrDefault(id, 0.0);
        double realSpeed = motionH(p).length();
        // Option A grace: the clock is one continuous airborne stretch
        // (airTicks), reset on every SOLID ground contact below — so a
        // jump→land→jump rhythm never drains; only the cliff case does.
        boolean overGrace = !onGround && airTicks.getOrDefault(id, 0)
                > (int) (cfg("momentum-grace-ms", 3500) / 50L);
        if (onGround) {
            if (run && realSpeed > cfg("momentum-min-motion", 0.03)) {
                m = Math.min(1.0, m + cfg("accel-per-tick", 0.08));
            } else {
                m = Math.max(0.0, m - cfg("friction-per-tick", 0.06));
            }
        } else if (overGrace) {
            // Past the grace window (one continuous long air-time — the
            // cliff case): drain hard, PAST the floor, toward zero, and
            // let gravity take the descent. No float off a cliff.
            m = Math.max(0.0, m - cfg("over-grace-decay-per-tick", 0.04));
        } else {
            m = Math.max(0.0, m - cfg("momentum-decay-per-tick", 0.008));
        }
        momentum.put(id, m);
        // Effective momentum: what speed/jump/air-cap/dash actually read.
        // The floor is an EFFECTIVE clamp, not a raw write — raw momentum
        // still decays underneath it (the over-grace drain reads raw).
        double eff = effMomentum(p);

        if (run) {
            // --- directional speed (inferred from velocity vs look) ---
            double dirMult = 1.0;
            if (hspeed > 0.06) {
                Vector look = p.getLocation().getDirection().setY(0);
                if (look.lengthSquared() > 1.0e-4) {
                    double dot = h.clone().normalize().dot(look.normalize());
                    if (dot < -0.3) dirMult = cfg("backward-mult", 0.7);
                    else if (dot < 0.5) dirMult = cfg("strafe-mult", 0.85);
                }
            }
            // --- movement speed: ONE knob. run-speed-target is the
            // sustained total speed bonus at full momentum (midpoint of
            // the pre-rework 0.99 and the rework 1.11); the base share
            // (standing start) is run-base-fraction of it and the
            // momentum ramp supplies the rest. ---
            double runTarget = cfg("run-speed-target", 1.05);
            double baseFrac = cfg("run-base-fraction", 0.33);
            double speedScalar = runTarget * (baseFrac + (1.0 - baseFrac) * eff) * dirMult;
            setMod(p, Attribute.MOVEMENT_SPEED, speedKey, speedScalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            // --- big jumps (base + momentum), client-predicted jump_strength ---
            double jumpAdd = cfg("base-jump-bonus", 0.12) + eff * cfg("momentum-jump-bonus", 0.25);
            setMod(p, Attribute.JUMP_STRENGTH, jumpKey, jumpAdd, AttributeModifier.Operation.ADD_NUMBER);
            // --- auto-jump up 1-block steps ---
            setMod(p, Attribute.STEP_HEIGHT, stepKey, cfg("step-height-bonus", 0.45), AttributeModifier.Operation.ADD_NUMBER);
            // --- floaty arcs while rising ---
            double gravityScalar = (!onGround && v.getY() > 0.0) ? -Math.abs(cfg("ascent-gravity-reduce", 0.18)) : 0.0;
            setMod(p, Attribute.GRAVITY, gravityKey, gravityScalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        } else {
            clearMods(p);   // run OFF → vanilla movement
        }

        // --- accept server-driven motion while airborne / sliding ---
        RasenganCompat.setMovementChecksExempt(p, !onGround || sliding.containsKey(id));

        // --- leap input arming ---
        // Run OFF  → double-tap space leaps, so arm allowFlight while airborne.
        // Run ON   → single space leaps (PlayerJumpEvent), so never arm here.
        if (onGround) {
            djUsed.remove(id);
            if (p.getAllowFlight()) p.setAllowFlight(false);
        } else if (!run && !djUsed.contains(id) && !p.getAllowFlight()) {
            p.setAllowFlight(true);
        } else if (run && p.getAllowFlight()) {
            p.setAllowFlight(false);
        }

        // --- air / ground state (one coherent resolution per tick) ---
        if (!onGround) {
            airTicks.merge(id, 1, Integer::sum);
            groundTicks.put(id, 0);
            // The rise budget is the arc you have LEFT: it drains with
            // airtime, so an early pull climbs long and a late one only
            // buys a short last-moment lift.
            riseBudget.computeIfPresent(id, (k, b) ->
                    Math.max(0.0, b - cfg("up-budget-decay", 0.012)));
            if (run) airTick(p, eff);
        } else {
            int gt = groundTicks.merge(id, 1, Integer::sum);
            int grace = (int) cfg("ground-grace-ticks", 3);
            // Landing (plant vs flow) still fires on FIRST contact so a
            // committed dive plants without a skate window…
            boolean landed = gt == 1 && airTicks.getOrDefault(id, 0) >= grace;
            if (landed) onLanding(p);
            // …but the momentum-grace clock and the rise budget re-arm
            // only after SOLID ground (>= grace consecutive ticks): a
            // ledge-lip flicker can neither reset the grace timer nor
            // refill the climb mid-flight.
            if (gt >= grace) {
                airTicks.put(id, 0);
                riseBudget.put(id, cfg("up-max-rise", 1.1));
            } else if (!riseBudget.containsKey(id)) {
                riseBudget.put(id, cfg("up-max-rise", 1.1));
            }
            rising.remove(id);
        }

        // --- slide upkeep ---
        Long until = sliding.get(id);
        if (until != null && (System.currentTimeMillis() > until || hspeed < cfg("slide-exit-speed", 0.14))) {
            sliding.remove(id);
            if (p.isSwimming()) p.setSwimming(false);
        }
    }

    /**
     * Preserve-and-steer air control. Air is where momentum is SPENT,
     * never minted: each tick the horizontal velocity is rotated toward
     * the look wish-direction at a turn rate, with its magnitude
     * preserved — never increased. Speed above the momentum-derived cap
     * (a leap burst) bleeds gently back down instead of compounding, so
     * you can never exceed in the air what you built on the ground.
     * The old additive Quake accel (flat air-max-speed, wish inversion)
     * is gone — it minted unbounded speed and sustained wall-reversed
     * motion.
     */
    private void airStrafe(Player p, double m) {
        Long lock = leapLock.get(p.getUniqueId());
        if (lock != null && System.currentTimeMillis() < lock) return;  // let the leap's direction carry first
        Vector vel = p.getVelocity();
        Vector h = new Vector(vel.getX(), 0, vel.getZ());
        double hs = h.length();
        if (hs < 1.0e-3) return;                   // nothing to steer

        Vector wish = p.getLocation().getDirection().setY(0);
        if (wish.lengthSquared() < 1.0e-4) return;
        wish.normalize();

        // Rotate the travel direction toward the wish at the turn rate —
        // a DIRECTION change only. Magnitude is handled separately below.
        double turn = Math.max(0.0, Math.min(1.0,
                cfg("air-turn-rate", 0.10) * (1.0 + m * cfg("air-turn-momentum", 0.5))));
        Vector dir = h.clone().normalize();
        Vector ndir = dir.multiply(1.0 - turn).add(wish.multiply(turn));
        if (ndir.lengthSquared() < 1.0e-6) ndir = wish.clone(); // 180° degenerate blend
        ndir.normalize();

        // Magnitude: preserved, capped by what the GROUND momentum is
        // worth. Overspeed (leap bursts) decays gently toward the cap.
        double cap = momentumAirCap(m);
        double speed = hs > cap
                ? Math.max(cap, hs - cfg("air-overspeed-bleed", 0.012))
                : hs;
        Vector nh = ndir.multiply(speed);
        p.setVelocity(new Vector(nh.getX(), vel.getY(), nh.getZ()));
    }

    /** One coherent air resolution per tick: horizontal steer always
     *  (air control never loses authority), then the pitch axis picks
     *  ONE vertical behaviour — rise above the up-threshold, otherwise
     *  flow (cutting any climb in progress: the pump). */
    private void airTick(Player p, double m) {
        airStrafe(p, m);
        float pitch = p.getLocation().getPitch();   // -90 up .. +90 down
        double upThr = cfg("up-look-threshold", 25.0);
        if (-pitch >= upThr) {
            rise(p, -pitch, upThr);
        } else {
            if (pitch >= cfg("down-look-threshold", 35.0)) dive(p, pitch);
            pumpCut(p);
        }
    }

    /**
     * Committed dive: ease the whole velocity toward the LOOK vector
     * (a redirect of what you have) and add downward speed along it —
     * you steer the fall onto the block you aim at. Nothing pushes into
     * the floor pre-contact: the stick fires only on real ground
     * contact ({@link #onLanding}).
     */
    private void dive(Player p, float pitch) {
        UUID id = p.getUniqueId();
        double thr = cfg("down-look-threshold", 35.0);
        double frac = Math.min(1.0, (pitch - thr) / (90.0 - thr));
        Vector aim = p.getLocation().getDirection();
        if (aim.lengthSquared() < 1.0e-4 || aim.getY() >= 0.0) return;
        aim.normalize();
        Vector v = p.getVelocity();
        double speed = v.length();
        double ease = Math.max(0.0, Math.min(1.0,
                cfg("dive-ease", 0.18) * (0.5 + 0.5 * frac)));
        Vector nv = v.clone().multiply(1.0 - ease)
                .add(aim.clone().multiply(speed * ease))
                .add(aim.clone().multiply(cfg("dive-accel", 0.05) * frac));
        double cap = cfg("dive-max-speed", 1.8);
        if (nv.length() > cap) nv = nv.normalize().multiply(cap);
        p.setVelocity(nv);
        if (diveCommitted.add(id)) {
            p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.5f, 0.9f);
        }
    }

    /** Real (grace-filtered) air→ground contact. Pitch AT CONTACT
     *  decides the landing: a committed look-down dive PLANTS (hard
     *  horizontal damping — you land where you aimed, no skate); at or
     *  above horizon the flow is preserved (roll + bunnyhop chain). */
    private void onLanding(Player p) {
        UUID id = p.getUniqueId();
        boolean committed = diveCommitted.remove(id)
                && p.getLocation().getPitch() >= cfg("down-look-threshold", 35.0);
        if (!committed) return;
        Vector v = p.getVelocity();
        double damp = cfg("land-damping", 0.15);
        p.setVelocity(new Vector(v.getX() * damp, v.getY(), v.getZ() * damp));
        // The plant costs momentum too — a stuck landing is a hard stop.
        momentum.computeIfPresent(id, (k, cur) ->
                cur * cfg("land-momentum-keep", 0.4));
        plantedAt.put(id, System.currentTimeMillis());
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_LAND, 0.6f, 1.0f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.2, 0.05, 0.2, 0.02);
    }

    /**
     * Look-up rise: an EASED REDIRECT of the current momentum toward
     * vertical — the velocity vector is rotated upward with its
     * magnitude preserved (the climb is paid for out of horizontal
     * speed), so no energy is ever added and it can never become
     * flight: the air-session budget bounds the total climb and drains
     * with airtime. Steeper look = stronger tilt.
     */
    private void rise(Player p, double upDeg, double thr) {
        UUID id = p.getUniqueId();
        double budget = riseBudget.getOrDefault(id, 0.0);
        if (budget <= 0.0) { pumpCut(p); return; }
        Vector v = p.getVelocity();
        double hs = Math.hypot(v.getX(), v.getZ());
        double frac = Math.min(1.0, (upDeg - thr) / (90.0 - thr));
        double dv = Math.min(budget,
                frac * (hs * cfg("up-tilt-ease", 0.10) + cfg("up-base-lift", 0.02)));
        if (dv <= 1.0e-4) return;
        double total = Math.sqrt(hs * hs + v.getY() * v.getY());
        double vy = Math.min(v.getY() + dv, cfg("up-max-vy", 0.9));
        double hsNew = Math.sqrt(Math.max(0.0, total * total - vy * vy));
        Vector nh = hs > 1.0e-3
                ? new Vector(v.getX(), 0, v.getZ()).multiply(hsNew / hs)
                : new Vector(0, 0, 0);
        p.setVelocity(new Vector(nh.getX(), vy, nh.getZ()));
        riseBudget.put(id, budget - dv);
        if (rising.add(id)) {
            p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 0.45f, 1.5f);
        }
    }

    /** Leaving a rise (view back to horizon) cuts the climb immediately
     *  and the arc resumes — working the view up/level is the pump. */
    private void pumpCut(Player p) {
        if (!rising.remove(p.getUniqueId())) return;
        Vector v = p.getVelocity();
        if (v.getY() > 0.0) {
            p.setVelocity(new Vector(v.getX(),
                    v.getY() * cfg("up-cancel-damp", 0.35), v.getZ()));
        }
    }

    /** Effective momentum — raw with the floor applied while the run is
     *  live and within grace. The ONE number that speed, jump, the
     *  air-strafe cap and the dash anchor all read. */
    private double effMomentum(Player p) {
        UUID id = p.getUniqueId();
        double m = momentum.getOrDefault(id, 0.0);
        boolean overGrace = !p.isOnGround() && airTicks.getOrDefault(id, 0)
                > (int) (cfg("momentum-grace-ms", 3500) / 50L);
        return (running(p) && !overGrace)
                ? Math.max(m, cfg("momentum-floor", 0.35)) : m;
    }

    /** The most horizontal speed the air will sustain at momentum
     *  {@code m} — the air budget IS the ground-built speed. At full
     *  momentum this now MATCHES the sustained run pace (~0.58 b/t at
     *  run-speed-target 1.05), so a dash launched at the cap travels at
     *  exactly run speed and air-strafe carries it without bleed. */
    private double momentumAirCap(double m) {
        return cfg("air-cap-base", 0.30) + m * cfg("air-cap-momentum", 0.28);
    }

    /**
     * Leap along the player's INPUT intent: the raw motion sample is
     * laundered through {@link #inputWish} — projected onto the look
     * frame and quantized to the eight WASD directions with magnitude
     * preserved. A collision rebound can flip or skew the raw position
     * delta; quantizing keeps the coarse intent (held S still leaps
     * backward, a strafe still leaps sideways) while the junk angle is
     * discarded — and because every leap is now steerable (the
     * committed no-air-control dash is retired) and air speed is
     * momentum-capped, even a junk-seeded launch is instantly
     * recoverable: the old wall-rebound backward-leap loop has nothing
     * left to feed on. Vertical grows the more you look up; horizontal
     * is cut as you aim up. With no motion it's a pure vertical pop.
     *
     * @param motionH raw horizontal motion sample at trigger time
     */
    private void doLeap(Player p, Vector motionH, double upBase, double upLook,
                        double power, double hcut) {
        Vector v = p.getVelocity();
        Vector intent = inputWish(p, motionH);
        double hspeed = intent.length();

        float pitch = p.getLocation().getPitch();             // -90 up .. +90 down
        double upFrac = Math.max(0.0, Math.min(1.0, -pitch / 90.0));
        // Reach lives in the ARC, not the speed: the dash's horizontal
        // launch is pinned at run pace (Phase 1), so the ground it
        // covers per input is speed × air-time — and dash-reach
        // stretches the air-time (the flat-look vertical base) for the
        // running dash. leap-max-h and the rise/air-time rules still
        // bound everything; no flight.
        double reach = running(p) ? cfg("dash-reach", 1.8) : 1.0;
        double up = upBase * reach + upFrac * upLook;

        boolean moving = hspeed >= cfg("leap-min-speed", 0.08);
        Vector nh = new Vector(v.getX(), 0, v.getZ());
        UUID id = p.getUniqueId();
        if (moving) {
            Vector dir = intent.clone().normalize();          // launch along INTENT
            double launch;
            if (running(p)) {
                // DASH: anchored to run speed — the launch derives from
                // the same momentum cap that bounds air-strafe and
                // tracks run-speed-target (one source of truth), but it
                // BURSTS above it by dash-speed-scale: the leap fires
                // hot (~old leap punch at 1.6) and the air-strafe
                // overspeed bleed eases it back to run pace through the
                // arc — burst-and-carry, no permanent runaway. Aiming
                // up still trades horizontal for vertical.
                launch = momentumAirCap(effMomentum(p))
                        * cfg("dash-speed-scale", 2.0)
                        * (1.0 - hcut * upFrac);
            } else {
                // Run-OFF double-jump keeps its stronger standalone
                // impulse (no flux attributes backing it).
                launch = hspeed + power * (1.0 - hcut * upFrac);
            }
            nh = dir.multiply(launch);
            double cap = cfg("leap-max-h", 1.5);
            if (nh.length() > cap) nh = nh.normalize().multiply(cap);
        }
        // Debounce EVERY leap (the old dash branch used to clear this,
        // which let a wall contact rapid-refire launches).
        leapLock.put(id, System.currentTimeMillis() + (long) cfg("leap-air-lock-ms", 350));
        p.setVelocity(new Vector(nh.getX(), Math.max(v.getY(), up), nh.getZ()));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.25, 0.05, 0.25, 0.03);
        p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.3f);

        if (plugin.getConfig().getBoolean("mobility.path-two.debug-leap", false)) {
            Vector look = p.getLocation().getDirection();
            plugin.getLogger().info(String.format(
                    "[FluxLeap] %s motion=(%.2f,%.2f) look=(%.2f,%.2f) pitch=%.0f up=%.2f -> vel=(%.2f,%.2f)",
                    p.getName(), motionH.getX(), motionH.getZ(),
                    look.getX(), look.getZ(), pitch, up, nh.getX(), nh.getZ()));
        }
    }

    /** Launder a raw motion sample into a clean input intent: project
     *  onto the look frame (forward/right) and quantize to the eight
     *  WASD directions, preserving the magnitude. A component counts as
     *  "held" when it carries at least tan(22.5°) ≈ 0.383 of the
     *  magnitude — the standard eight-sector split. */
    private Vector inputWish(Player p, Vector motion) {
        double len = motion.length();
        if (len < 1.0e-4) return new Vector(0, 0, 0);
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0e-4) return motion.clone();
        look.normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        double f = motion.dot(look);
        double r = motion.dot(right);
        double fSign = Math.abs(f) / len >= 0.383 ? Math.signum(f) : 0.0;
        double rSign = Math.abs(r) / len >= 0.383 ? Math.signum(r) : 0.0;
        if (fSign == 0.0 && rSign == 0.0) fSign = Math.signum(f);
        Vector dir = look.multiply(fSign).add(right.multiply(rSign));
        if (dir.lengthSquared() < 1.0e-6) return new Vector(0, 0, 0);
        return dir.normalize().multiply(len);
    }

    /* --------------------------------------------------------------- events */

    /** Double-tap space → leap. Only when the run is OFF (run uses single space). */
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        e.setCancelled(true);
        p.setFlying(false);
        UUID id = p.getUniqueId();
        if (running(p)) { p.setAllowFlight(false); return; }   // run ON → single-space leap, not this
        if (djUsed.contains(id)) { p.setAllowFlight(false); return; }
        djUsed.add(id);
        // Stronger than the running leap (no speed/jump attributes backing it) and
        // directional along real movement; a standing double-jump is a tall pop.
        Vector mh = motionH(p);
        doLeap(p, mh, cfg("dj-up-base", 0.6), cfg("dj-up-look", 0.6),
                cfg("dj-power", 0.6), cfg("leap-up-hcut", 0.6));
        p.setAllowFlight(false);
    }

    /** Running jump (single space) leaps along motion. Standing still → plain jump. */
    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (!active(p) || !running(p)) return;
        // Capture REAL movement NOW (at the jump), before air-strafe can re-aim it.
        Vector mh = motionH(p);
        if (mh.length() < cfg("leap-min-speed", 0.08)) return;  // not in motion → vanilla jump
        // Apply next tick so the vanilla jump's vertical velocity is already in.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && !p.isOnGround())
                doLeap(p, mh, cfg("leap-up-base", 0.42), cfg("leap-up-look", 0.55),
                        cfg("leap-power", 0.45), cfg("leap-up-hcut", 0.6));
        });
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!active(p)) return;
        UUID id = p.getUniqueId();

        if (e.isSneaking()) {                                  // ---- PRESS ----
            // Slide if running fast with momentum on the ground.
            if (running(p) && p.isOnGround()) {
                double m = momentum.getOrDefault(id, 0.0);
                if (m >= cfg("slide-momentum-threshold", 0.6)) { doSlide(p); return; }
            }
            sneakPressAt.put(id, System.currentTimeMillis());
            return;
        }

        // ---- RELEASE ----
        // La course chakraïque n'est PLUS déclenchée par le sneak-tap : elle
        // passe UNIQUEMENT par la touche du mod client (canal reborn:run →
        // RunChannelListener → narutoRun.toggle). Le sneak reste crouch/slide.
        sneakPressAt.remove(id);
    }

    private void doSlide(Player p) {
        Vector v = p.getVelocity();
        Vector h = new Vector(v.getX(), 0, v.getZ());
        if (h.lengthSquared() < 1.0e-4) h = p.getLocation().getDirection().setY(0);
        double target = h.length() + cfg("slide-boost", 0.3);
        h.normalize().multiply(target);
        p.setVelocity(new Vector(h.getX(), v.getY(), h.getZ()));
        sliding.put(p.getUniqueId(), System.currentTimeMillis() + (long) cfg("slide-duration-ms", 800));
        p.setSwimming(true);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 6, 0.2, 0.05, 0.2, 0.02);
    }

    /** Logout teardown — without this the UUID-keyed state maps leak
     *  until a lucky rejoin (the tick loop only sees online players). */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        reset(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p) || !active(p)) return;
        e.setCancelled(true);   // Voie du Flux never takes fall damage
        // Cosmetic roll + small forward carry on a hard landing — FLOW
        // landings only: a committed plant (look-down dive) sticks
        // instead of rolling.
        Long planted = plantedAt.get(p.getUniqueId());
        boolean justPlanted = planted != null
                && System.currentTimeMillis() - planted < 250L;
        if (!justPlanted && p.getFallDistance() >= cfg("roll-min-fall-distance", 4.0)) {
            Vector dir = motionH(p);
            if (dir.lengthSquared() < 1.0e-4) dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() > 1.0e-4) {
                dir.normalize().multiply(cfg("roll-forward-speed", 0.55));
                p.setVelocity(new Vector(dir.getX(), p.getVelocity().getY(), dir.getZ()));
            }
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.25, 0.1, 0.25, 0.03);
        }
        p.setFallDistance(0f);
    }

    /* ------------------------------------------------------------- attrs */

    private void setMod(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        removeMod(inst, key);
        if (amount == 0.0) return;
        inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    private void removeMod(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier mod : inst.getModifiers().toArray(new AttributeModifier[0])) {
            if (key.equals(mod.getKey())) inst.removeModifier(mod);
        }
    }

    private void clearMods(Player p) {
        clear(p, Attribute.MOVEMENT_SPEED, speedKey);
        clear(p, Attribute.JUMP_STRENGTH, jumpKey);
        clear(p, Attribute.GRAVITY, gravityKey);
        clear(p, Attribute.STEP_HEIGHT, stepKey);
    }

    public void reset(Player p) {
        UUID id = p.getUniqueId();
        applied.remove(id);
        momentum.remove(id);
        sliding.remove(id);
        djUsed.remove(id);
        riseBudget.remove(id);
        rising.remove(id);
        airTicks.remove(id);
        groundTicks.remove(id);
        diveCommitted.remove(id);
        plantedAt.remove(id);
        sneakPressAt.remove(id);
        leapLock.remove(id);
        moveDir.remove(id);
        moveAt.remove(id);
        if (narutoRun != null && narutoRun.isActive(p)) narutoRun.stop(p, false);
        clearMods(p);
        if (p.isOnline()) {
            RasenganCompat.setMovementChecksExempt(p, false);
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) p.setAllowFlight(false);
        }
    }

    private void clear(Player p, Attribute attr, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) removeMod(inst, key);
    }
}
