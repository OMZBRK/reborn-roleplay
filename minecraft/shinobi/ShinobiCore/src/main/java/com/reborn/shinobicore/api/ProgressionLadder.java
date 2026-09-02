package com.reborn.shinobicore.api;

import java.util.List;

/**
 * The world's progression ladder — an ordered list of rungs whose
 * labels are world data, not engine code. The Naruto pack's rungs are
 * Academy Student → … → Kage.
 *
 * <p><b>Interface-first stub (Phase 2).</b> The current backing is the
 * legacy {@code Rank} enum (pure display metadata, persisted by name);
 * moving the rung list into world config is a planned follow-up — the
 * persisted names already match what a data-driven ladder would read.
 */
@Stable
public interface ProgressionLadder {

    /** One rung of the ladder. */
    interface Rung {
        /** Stable id, as persisted on characters (e.g. {@code "GENIN"}). */
        String id();
        /** Player-facing label (e.g. {@code "Genin"}). */
        String displayName();
        /** Position from the bottom, 0-based. */
        int ordinal();
    }

    /** All rungs, lowest first. */
    List<? extends Rung> rungs();

    /** Rung by persisted id (tolerant, case-insensitive), or null. */
    Rung byId(String id);
}
