package com.reborn.shinobicore.skill;

/**
 * The outcome of a skill roll resolved by {@link RollService}.
 *
 * <p>{@code die == 0} means no die was rolled (the auto outcomes). For
 * {@link Outcome#AUTO_SUCCESS} / {@link Outcome#AUTO_FAIL}, {@code total}
 * equals the bare skill rating.
 */
public record RollResult(Outcome outcome, int die, int skill, int total, Integer dc) {

    public enum Outcome {
        AUTO_SUCCESS,   // skill >= DC — routine, no roll
        CRITICAL,       // beat DC by a lot / natural max
        SUCCESS,
        PARTIAL,        // success at a cost ("yes, but")
        FAILURE,
        AUTO_FAIL,      // DC beyond reach even on a max die
        OPEN            // no DC supplied — GM narrates from the total
    }

    /** True when an actual die was involved in the result. */
    public boolean rolled() {
        return outcome != Outcome.AUTO_SUCCESS && outcome != Outcome.AUTO_FAIL;
    }

    /** True for any successful degree (including partial / auto). */
    public boolean success() {
        return outcome == Outcome.AUTO_SUCCESS
                || outcome == Outcome.CRITICAL
                || outcome == Outcome.SUCCESS
                || outcome == Outcome.PARTIAL;
    }
}
