package com.reborn.shinobicore.api;

/**
 * A named depletable resource pool with an overdraw/exhaustion policy.
 * World-agnostic: the Naruto world instantiates it as chakra; another
 * world may map it to cursed energy, stamina, etc.
 *
 * <p>The two spending verbs encode the engine's balance model:
 * {@link #consume(double)} refuses when short, {@link #overdraw(double)}
 * always spends and pushes the pool into debt — debt is what the
 * exhaustion ladder punishes.
 */
@Stable
public interface ResourcePool {

    double current();

    double max();

    /** Outstanding overdraw debt (0 when healthy). */
    double debt();

    /** True while the pool carries overdraw debt. */
    boolean inDeficit();

    /** True when {@code current >= amount}. */
    boolean has(double amount);

    /** Spend if affordable; refuse (return false) otherwise. */
    boolean consume(double amount);

    /**
     * Always spend; whatever exceeds {@link #current()} becomes debt.
     * Returns the debt created by this call (0 if fully covered).
     */
    double overdraw(double amount);

    void setCurrent(double value);

    /** Restore up to {@code amount}: pays down debt first, then refills. */
    void regen(double amount);

    /** Restore to full and clear debt. */
    void fill();
}
