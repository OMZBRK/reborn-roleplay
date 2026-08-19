package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.event.KoEnterEvent;
import com.reborn.shinobicore.util.Players;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Central mobility event dispatcher. Carries the seven hard-won fixes:
 *
 * <ol>
 *   <li>Vanilla creative-flight is killed every move tick when
 *       {@code isFlying()} on a ground-game player.</li>
 *   <li>{@code allowFlight} is armed ONLY when an airborne ability
 *       could actually fire — always-on causes client prediction lift
 *       on every space tap.</li>
 *   <li>Double-tap-Space speculative lift is cancelled by zeroing the
 *       velocity in onToggleFlight when we cancel without firing.</li>
 *   <li>DJ lockout / token refill only on CONTINUOUS ground dwell
 *       (handled in {@link AirTokenManager}).</li>
 *   <li>Attribute modifiers are keyed by NamespacedKey (see
 *       {@link NarutoRun}) — name-based removal silently fails.</li>
 *   <li>Fall damage globally disabled at HIGHEST for ground-game
 *       players.</li>
 *   <li>F-key priority: the jutsu listener owns F at LOWEST; this
 *       class registers at NORMAL (ignoreCancelled) for trail-cancel,
 *       so the picker always wins.</li>
 * </ol>
 */
public final class MobilityListener implements Listener {

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final MobilityModule mobility;
    private final MobilityPaths paths;
    /** True while the jutsu picker hijacks the hotbar — mobility sneak
     *  semantics are suspended (HOLD_SNEAK belongs to the picker). */
    private final Predicate<Player> pickerOpen;
    /** True while a learning minigame (PUSHUP reps) owns the sneak key. */
    private final Predicate<Player> minigameActive;

    /** Air-control leap windows: player → expiry epoch millis. */
    private final Map<UUID, Long> leapWindow = new HashMap<>();
    /** Ground sneak-press timestamps (tap vs hold disambiguation). */
    private final Map<UUID, Long> sneakPressAt = new HashMap<>();
    /** Deferred Naruto-Run toggle per player — cancelled by a 2nd tap (dash). */
    private final Map<UUID, BukkitTask> pendingRunToggle = new HashMap<>();

    public MobilityListener(JavaPlugin plugin, CoreServices core,
                            MobilityModule mobility, MobilityPaths paths,
                            Predicate<Player> pickerOpen,
                            Predicate<Player> minigameActive) {
        this.plugin = plugin;
        this.core = core;
        this.mobility = mobility;
        this.paths = paths;
        this.pickerOpen = pickerOpen;
        this.minigameActive = minigameActive;
    }

    /** Survival/adventure player with an active, conscious character. */
    private ShinobiCharacter groundGame(Player p) {
        if (mobility.training() != null && mobility.training().isRunning(p)) return null;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return null;
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return null;
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return null;
        // Path gate — the Voie du Chakra kit only works once Path One is learned
        // (a fresh character knows no mobility at all).
        if (paths.get(c.id()) != MobilityPaths.Path.ONE) return null;
        return c;
    }

    /** Survival/adventure player with an active, conscious character
     *  and ANY mastered mobility path — the eligibility for the utility
     *  rides (zipline + trail). Mirrors {@link #groundGame} minus the
     *  Path-One restriction: a path is "mastered" when it isn't NONE
     *  (paths are mutually exclusive, no separate mastery flag). */
    private boolean rideEligible(Player p) {
        if (mobility.training() != null && mobility.training().isRunning(p)) return false;
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return false;
        ShinobiCharacter c = Players.active(core.characters(), p);
        if (c == null) return false;
        if (core.ko() != null && core.ko().isKo(p.getUniqueId())) return false;
        return paths.get(c.id()) != MobilityPaths.Path.NONE;
    }

    /** True while a ShinobiCore movement session (grapple swing or
     *  zipline ride) owns the player's velocity. Our flight arming and
     *  space-tap handling must stand down completely — zeroing velocity
     *  mid-swing is what "breaks" the grapple. */
    private boolean coreSessionActive(Player p) {
        if (mobility.training() != null && mobility.training().isRunning(p)) return true;
        // A trail ride owns the body too: the ride task drives velocity every
        // tick, so the whole mobility engine (air-control steering AND the
        // space/flight-tap handler) must stand down — otherwise space fights the
        // ride and it stutters "forward again and again". Space is vanilla here.
        if (mobility.trails() != null && mobility.trails().isRiding(p)) return true;
        var module = core.mobility();
        if (module == null) return false;
        UUID id = p.getUniqueId();
        return (module.grapple() != null && module.grapple().isGrappling(id))
                || (module.zipline() != null && module.zipline().isTraveling(id));
    }

    /* ------------------------------------------------------------ movement */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        ShinobiCharacter c = groundGame(p);
        if (c == null) {
            // Voie du Flux (Path Two) manages its own flight + movement exemption —
            // don't clobber it here.
            if (paths.pathOf(p) == MobilityPaths.Path.TWO) return;
            // Not in the ground game — make sure we never leave residual
            // allowFlight on someone who switched mode / lost character.
            if (p.getGameMode() == GameMode.SURVIVAL
                    || p.getGameMode() == GameMode.ADVENTURE) {
                if (p.getAllowFlight()) p.setAllowFlight(false);
            }
            RasenganCompat.setMovementChecksExempt(p, false); // Rasengan - normal vanilla movement
            return;
        }

        boolean onGround = p.isOnGround();
        // Rasengan: while airborne or in a mobility session (grapple/zipline/trail),
        // tell the server to accept our velocity changes instead of rubber-banding
        // them (no-op on non-Rasengan servers).
        RasenganCompat.setMovementChecksExempt(p, !onGround || coreSessionActive(p)
                || mobility.narutoRun().isActive(p));

        // Fix #1 — vanilla creative-flight must die every move tick.
        if (p.isFlying()) p.setFlying(false);

        mobility.airTokens().onMoveTick(p, onGround);
        mobility.climb().onMoveTick(p, onGround);
        mobility.shockwave().onMoveTick(p, onGround);
        mobility.dash().onMoveTick(p);
        mobility.narutoRun().rampTick(p);
        airControlTick(p, onGround);

        // Fix #2 (deterministic, rework §4.1) — arm allowFlight whenever the
        // player is airborne in the ground game and not in a special state, so
        // the space tap fires the SAME every time. The DJ/WJ gating (tokens /
        // cooldown / chakra) happens in onToggleFlight, not here — the old
        // per-tick couldFire toggle is what made the jump inconsistent. Still
        // disarmed on landing (onGround) / core session / cling / slam / picker.
        boolean arm = !onGround
                && !pickerOpen.test(p)
                && !coreSessionActive(p)
                && !mobility.climb().isClinging(p)
                && !mobility.shockwave().isSlamming(p)
                // Rasengan - only arm when a jump could actually fire, so a stray
                // space in mid-air stays vanilla (no brake) instead of toggling.
                && (mobility.doubleJump().couldFire(p, c)
                    || mobility.wallJump().couldFire(p, c));
        if (p.getAllowFlight() != arm) p.setAllowFlight(arm);
    }

    /** Continuous, momentum-aware air steering (fluidity rework §4.1): always
     *  available while airborne (weak by default); the leap window only RAISES
     *  the cap. Never brakes speed above the cap — a dash / big leap carries and
     *  bleeds via vanilla drag — and never touches the vertical axis. */
    private void airControlTick(Player p, boolean onGround) {
        UUID id = p.getUniqueId();
        if (onGround) { leapWindow.remove(id); return; }
        if (coreSessionActive(p)) return;                 // never fight a swing/ride
        if (mobility.climb().isClinging(p) || mobility.shockwave().isSlamming(p)) return;
        if (mobility.doubleJump().isCurving(p)) return;   // the DJ curve owns velocity

        var cfg = plugin.getConfig();
        // Master switch — OFF by default: no air-control steering/slowdown at
        // all (pure vanilla air physics + the double-jump curve).
        if (!cfg.getBoolean("mobility.air-control.enabled", false)) return;
        Long until = leapWindow.get(id);
        boolean inWindow = until != null && System.currentTimeMillis() <= until;
        if (until != null && !inWindow) leapWindow.remove(id);
        if (!cfg.getBoolean("mobility.air-control.always-on", false) && !inWindow) return;

        double cap = inWindow
                ? cfg.getDouble("mobility.air-control.leap-cap-h", 1.30)
                : cfg.getDouble("mobility.air-control.base-cap-h", 0.55);
        if (mobility.narutoRun().isActive(p)) {
            cap *= cfg.getDouble("mobility.air-control.run-cap-bonus", 1.15);
        }
        double accel = cfg.getDouble("mobility.air-control.accel-per-tick", 0.06);
        double minSpeed = cfg.getDouble("mobility.air-control.min-speed", 0.08);

        Vector v = p.getVelocity();
        Vector cur = new Vector(v.getX(), 0, v.getZ());
        double speed = cur.length();
        // Air control only REDIRECTS existing horizontal momentum — it never
        // creates or amplifies it. So a jump in place (speed ~0) gets NO forward
        // push, and a dash / big leap (speed > cap) is left for vanilla physics
        // to carry + bleed. Only a moving player curves toward their look.
        if (speed < minSpeed || speed > cap) return;

        Vector dir = MobilityMath.intentDir(p,
                cfg.getDouble("mobility.feel.dir-look-weight", 0.7));
        Vector step = dir.clone().multiply(speed).subtract(cur);
        if (step.length() > accel) step = step.normalize().multiply(accel);
        Vector h = cur.add(step);
        if (h.length() > speed) h = h.normalize().multiply(speed);
        p.setVelocity(new Vector(h.getX(), v.getY(), h.getZ()));
    }

    private void openLeapWindow(Player p) {
        long window = plugin.getConfig()
                .getLong("mobility.air-control.leap-window-ms", 1500L);
        leapWindow.put(p.getUniqueId(), System.currentTimeMillis() + window);
    }

    /** Drop deferred sneak state (cancels a pending run-toggle task). */
    private void cancelSneakState(UUID id) {
        sneakPressAt.remove(id);
        BukkitTask t = pendingRunToggle.remove(id);
        if (t != null) t.cancel();
    }

    /* -------------------------------------------------------------- flight */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        GameMode gm = p.getGameMode();
        if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) return;
        // Voie du Flux owns the double-jump for Path Two — leave its toggle alone.
        if (paths.pathOf(p) == MobilityPaths.Path.TWO) return;

        event.setCancelled(true);
        p.setFlying(false);

        // A grapple swing / zipline ride owns the velocity — swallow the
        // toggle without touching motion (no DJ, no zeroing).
        if (coreSessionActive(p)) {
            p.setAllowFlight(false);
            return;
        }

        ShinobiCharacter c = groundGame(p);
        boolean fired = false;
        if (c != null && !pickerOpen.test(p)) {
            if (mobility.wallJump().couldFire(p, c)) {
                fired = mobility.wallJump().tryActivate(p);
            }
            if (!fired) fired = mobility.doubleJump().tryActivate(p);
        }
        if (fired) {
            openLeapWindow(p);
        }
        // Rasengan - no artificial brake on a no-fire toggle: let vanilla air
        // physics carry (regular jump feel). Arming now only happens when a
        // DJ/WJ can actually fire, so a no-fire toggle is rare anyway.
        p.setAllowFlight(false);
    }

    /* --------------------------------------------------------------- sneak */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player p = event.getPlayer();
        if (minigameActive.test(p)) return;   // PUSHUP reps own the key
        if (pickerOpen.test(p)) return;       // HOLD_SNEAK belongs to jutsu
        if (coreSessionActive(p)) return;     // swing/ride owns the body
        ShinobiCharacter c = groundGame(p);
        if (c == null) return;

        UUID id = p.getUniqueId();
        boolean onGround = p.isOnGround();

        if (event.isSneaking()) {                       // ---- PRESS ----
            if (!onGround) {
                // Climb overrides the shockwave when a wall is adjacent.
                if (mobility.climb().tryCling(p)) return;
                mobility.shockwave().startCharge(p);
                return;
            }
            // Ground press: a 2nd tap within the window dashes (§4.5).
            BukkitTask pending = pendingRunToggle.remove(id);
            if (pending != null) {
                pending.cancel();
                if (mobility.dash().dashNow(p)) openLeapWindow(p);
                return;
            }
            sneakPressAt.put(id, System.currentTimeMillis());
            return;
        }

        // ---- RELEASE ----
        Long pressAt = sneakPressAt.remove(id);
        if (mobility.climb().releaseCling(p)) return;
        mobility.shockwave().cancelCharge(p);
        if (pressAt == null || !onGround) return;       // air release / no ground tap
        long held = System.currentTimeMillis() - pressAt;
        if (held > mobility.narutoRun().tapMaxMs()) return;   // long hold = normal crouch
        // A tap: open a short window so a quick 2nd tap dashes (§4.5). Naruto Run
        // no longer lives on Path One (it moved to Voie du Flux / Path Two), so a
        // lone tap does nothing now — the window only exists to catch the dash.
        long gapTicks = Math.max(1L,
                plugin.getConfig().getLong("mobility.shinobi-dash.double-tap-ms", 250L) / 50L);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                pendingRunToggle.remove(id), gapTicks);
        pendingRunToggle.put(id, task);
    }

    /* ------------------------------------------------------------ fall dmg */

    /** Fix #6 — fall damage globally off for ground-game players. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (groundGame(p) != null || mobility.trails().isRiding(p)) {
            event.setCancelled(true);
        }
    }

    /* --------------------------------------------------------------- F key */

    /** NORMAL + ignoreCancelled: the jutsu picker (LOWEST) already
     *  consumed F when it owned it. What reaches us is a free F press.
     *
     *  <p>This is the legacy core-MobilityListener F-chain, rebuilt here
     *  (core's own copy was deleted with the mobility migration):
     *  zipline-ride cancel → grapple cancel → trail cancel → zipline
     *  ride/re-attach → trail start. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        var module = core.mobility();

        // 1. F during a zipline ride → drop off.
        if (module != null && module.zipline() != null
                && module.zipline().cancelTravel(p.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // 2. F during a grapple swing → release the rope.
        if (module != null && module.grapple() != null
                && module.grapple().isGrappling(p.getUniqueId())) {
            event.setCancelled(true);
            module.grapple().end(p, /*armCooldown=*/true, "manual");
            return;
        }
        // 3. F during a trail ride → drop off.
        if (mobility.trails().isRiding(p)) {
            event.setCancelled(true);
            mobility.trails().stopRide(p, true);
            return;
        }
        // Utility rides (zipline + trail) unlock with ANY mastered
        // mobility path — Path Two included. The Path One KIT (dash,
        // double-jump, …) stays gated by groundGame(); only the ride
        // starts moved out from behind it.
        if (!rideEligible(p)) return;
        // 4. F near a zipline endpoint / line → ride (or re-attach).
        if (module != null && module.zipline() != null
                && module.zipline().tryActivateNear(p)) {
            event.setCancelled(true);
            return;
        }
        // 5. F near a trail's first anchor → ride.
        if (mobility.trails().tryStartRide(p)) {
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------------ lifecycle */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // Stale-state sweep: a crash may have left the speed modifier or
        // allowFlight behind. Remove by key; arm logic will re-set later.
        mobility.narutoRun().removeModifier(p);
        if (p.getGameMode() == GameMode.SURVIVAL
                || p.getGameMode() == GameMode.ADVENTURE) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        mobility.narutoRun().stop(p, false);
        mobility.shockwave().clear(p);
        mobility.dash().clear(p);
        mobility.climb().clear(p);
        mobility.trails().stopRide(p, false);
        mobility.airTokens().clear(p.getUniqueId());
        leapWindow.remove(p.getUniqueId());
        cancelSneakState(p.getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        // The trail ride moves its rider BY teleport interpolation every
        // tick — those must not cancel the very ride issuing them. Only
        // external teleports (plugins, /tp, portals) end the ride.
        if (mobility.trails().isRideMove(p.getUniqueId())) return;
        mobility.trails().stopRide(p, false);
        mobility.shockwave().clear(p);
        if (mobility.climb().isClinging(p)) mobility.climb().releaseCling(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCharacterSwitch(CharacterSwitchEvent event) {
        Player p = event.player();
        // Fix #5 hygiene — the modifier must never survive a swap (the
        // new character re-derives its own stats from ShinobiCore).
        mobility.narutoRun().stop(p, false);
        mobility.shockwave().clear(p);
        mobility.dash().clear(p);
        mobility.climb().clear(p);
        mobility.trails().stopRide(p, false);
        leapWindow.remove(p.getUniqueId());
        cancelSneakState(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKoEnter(KoEnterEvent event) {
        Player p = event.player();
        mobility.narutoRun().stop(p, false);
        mobility.shockwave().clear(p);
        mobility.dash().clear(p);
        mobility.climb().clear(p);
        mobility.trails().stopRide(p, false);
        leapWindow.remove(p.getUniqueId());
        cancelSneakState(p.getUniqueId());
        p.setAllowFlight(false);
    }
}
