package com.reborn.shinobicore.chakra;

/**
 * A chakra pool. Owned by a {@code ShinobiCharacter}, not a player, so
 * different characters on the same account have independent pools.
 *
 * The {@code max} value is re-derived every time the character level or
 * affinity changes (see {@code ShinobiCharacter.recomputeStats()}).
 *
 * <p>A sentinel {@link #EMPTY} instance is returned by the chakra manager
 * when a player has no active character; it always refuses to consume.
 *
 * <p><b>Overdraw economy.</b> {@link #consume} is the polite gate (it refuses
 * when you can't afford the cost). {@link #overdraw} is the "no hard gates"
 * path: it always spends, pushing any shortfall into {@link #debt}. While
 * {@code debt > 0} the pool is {@link #inDeficit() in deficit} and the
 * ExhaustionManager runs the exhaustion ladder. {@link #regen} pays the debt
 * down first, then refills — so meditation / a medic pulls a host back from
 * collapse.
 */
public final class ChakraPool implements com.reborn.shinobicore.api.ResourcePool {

    /** No-op pool used when there's no active character selected. */
    public static final ChakraPool EMPTY = new ChakraPool(0.0);

    private double current;
    /** Bumped on every mutation; the owning character compares it against
     *  the value recorded at its last save to derive its dirty flag. */
    private transient long mutations;
    private double max;
    private double debt;          // >= 0: chakra owed from an overdraw
    private long lastUseMillis;

    public ChakraPool(double max) {
        this.max = Math.max(0.0, max);
        this.current = this.max;
        this.lastUseMillis = 0L;
    }

    /** Monotonic mutation counter for dirty tracking. */
    public long mutationCount() { return mutations; }

    public double current() { return current; }
    public double max() { return max; }
    public long lastUseMillis() { return lastUseMillis; }

    /** Outstanding chakra debt from an overdraw (0 when healthy). */
    public double debt() { return debt; }

    /** True when the pool has been overdrawn and not yet recovered. */
    public boolean inDeficit() { return this != EMPTY && debt > 0.0; }

    public void setCurrent(double value) {
        this.current = Math.max(0.0, Math.min(max, value));
        this.debt = 0.0;          // an explicit set clears any exhaustion debt
        mutations++;
    }

    public void setMax(double newMax) {
        this.max = Math.max(0.0, newMax);
        if (current > max) current = max;
        mutations++;
    }

    public boolean has(double amount) { return current >= amount; }

    public boolean consume(double amount) {
        if (this == EMPTY) return false;
        if (amount <= 0) return true;
        if (current < amount) return false;
        current -= amount;
        lastUseMillis = System.currentTimeMillis();
        mutations++;
        return true;
    }

    /**
     * Spend {@code amount} even if it exceeds {@link #current}: the shortfall
     * becomes {@link #debt} (an overdraw), capped at one full pool. This is the
     * "no hard gates" path — a caster can always attempt a costly jutsu and
     * pay the price in exhaustion. Returns the debt incurred by this call.
     */
    public double overdraw(double amount) {
        if (this == EMPTY || amount <= 0) return 0.0;
        double incurred = 0.0;
        if (current >= amount) {
            current -= amount;
        } else {
            incurred = amount - current;
            current = 0.0;
            debt += incurred;
            if (debt > max) debt = max;   // floor the deficit at -max
        }
        lastUseMillis = System.currentTimeMillis();
        mutations++;
        return incurred;
    }

    public void regen(double amount) {
        if (this == EMPTY || amount <= 0) return;
        if (debt > 0.0) {
            double pay = Math.min(debt, amount);
            debt -= pay;
            amount -= pay;
        }
        if (amount > 0.0) current = Math.min(max, current + amount);
        mutations++;
    }

    public void fill() { current = max; debt = 0.0; mutations++; }
}
