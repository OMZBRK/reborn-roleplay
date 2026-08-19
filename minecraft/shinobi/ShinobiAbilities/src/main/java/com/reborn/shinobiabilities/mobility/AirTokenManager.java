package com.reborn.shinobiabilities.mobility;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The global "one air token per player" budget for airborne abilities
 * (Double Jump, Wall Jump, Floor Shockwave).
 *
 * <p>Refill rule (the anti-bounce-house gate): tokens come back ONLY
 * after <b>continuous</b> ground dwell ≥ {@code refill-grounded-ms}.
 * A single-tick ground flicker resets nothing — the dwell clock starts
 * over every time the player leaves the ground. The DJ lockout clears
 * on the same continuous-dwell condition, plus a minimum airtime since
 * the last DJ so same-tick ground glitches can't re-arm it.
 */
public final class AirTokenManager {

    private static final class State {
        int tokens;
        long groundedSince;     // 0 while airborne
        long lastAirborneUseAt; // last DJ/FS timestamp (NOT wall jumps)
        boolean djLockout;
        int wallJumpsUsed;      // independent per-airtime WJ counter
        int airborneStreak;     // consecutive airborne samples (false-blip grace)
    }

    private final JavaPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();

    public AirTokenManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private int max() {
        return Math.max(1, plugin.getConfig().getInt("mobility.air-tokens.max", 1));
    }

    private long refillMs() {
        return Math.max(0L, plugin.getConfig()
                .getLong("mobility.air-tokens.refill-grounded-ms", 100L));
    }

    private long lockoutMinAirMs() {
        return Math.max(0L, plugin.getConfig()
                .getLong("mobility.double-jump.lockout-min-air-ms", 400L));
    }

    private State state(UUID id) {
        return states.computeIfAbsent(id, k -> {
            State s = new State();
            s.tokens = max();
            return s;
        });
    }

    /** Drive the dwell clock — call once per move tick. */
    public void onMoveTick(Player p, boolean onGround) {
        State s = state(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (!onGround) {
            // Tolerate a single false-airborne sample (water edge, lag spike)
            // before resetting the dwell, so the leap economy survives jitter.
            if (++s.airborneStreak >= 2) s.groundedSince = 0L;
            return;
        }
        s.airborneStreak = 0;
        if (s.groundedSince == 0L) s.groundedSince = now;
        if (now - s.groundedSince >= refillMs()) {
            // Wall-jump budget is INDEPENDENT of the DJ token — it only
            // needs the dwell, never the DJ-use lockout window.
            s.wallJumpsUsed = 0;
            if (now - s.lastAirborneUseAt >= lockoutMinAirMs()) {
                s.tokens = max();
                s.djLockout = false;
            }
        }
    }

    /* -------------------------------------------- wall-jump budget (own) */

    /** Wall jumps don't touch the DJ air-token budget; they carry their
     *  own per-airtime counter, reset by the same continuous ground
     *  dwell. {@code maxPerAirtime <= 0} means unlimited. */
    public boolean canWallJump(Player p, int maxPerAirtime) {
        if (maxPerAirtime <= 0) return true;
        return state(p.getUniqueId()).wallJumpsUsed < maxPerAirtime;
    }

    /** Spend one wall jump. Deliberately does NOT set
     *  {@code lastAirborneUseAt} — a wall jump must never delay the DJ
     *  token refill on the next landing. */
    public void consumeWallJump(Player p) {
        state(p.getUniqueId()).wallJumpsUsed++;
    }

    public int wallJumpsUsed(Player p) {
        return state(p.getUniqueId()).wallJumpsUsed;
    }

    public boolean hasToken(Player p) {
        return state(p.getUniqueId()).tokens > 0;
    }

    public int tokens(Player p) {
        return state(p.getUniqueId()).tokens;
    }

    /** Spend {@code cost} tokens for an airborne manoeuvre. */
    public boolean consume(Player p, int cost) {
        State s = state(p.getUniqueId());
        if (s.tokens < cost) return false;
        s.tokens -= cost;
        s.lastAirborneUseAt = System.currentTimeMillis();
        return true;
    }

    /** Arm the DJ lockout (set on every double jump when
     *  {@code require-ground-touch-between} is on). */
    public void armDjLockout(Player p) {
        state(p.getUniqueId()).djLockout = true;
    }

    public boolean djLockedOut(Player p) {
        return state(p.getUniqueId()).djLockout;
    }

    /** Admin / debug: refill instantly. */
    public void refill(Player p) {
        State s = state(p.getUniqueId());
        s.tokens = max();
        s.djLockout = false;
    }

    public void clear(UUID id) {
        states.remove(id);
    }
}
