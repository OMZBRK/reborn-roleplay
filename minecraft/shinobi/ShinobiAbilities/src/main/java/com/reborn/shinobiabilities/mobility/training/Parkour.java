package com.reborn.shinobiabilities.mobility.training;

import com.reborn.shinobiabilities.mobility.MobilityActionSlot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A named training course: an ordered chain of {@link ParkourAnchor}s in one
 * world, plus the set of mobility abilities a runner UNLOCKS by completing it.
 *
 * <p>Index 0 is the start (no reaction is played there — it's just the launch
 * pad); every subsequent anchor plays its reaction mini-game on landing. The
 * course is "done" once the runner clears the reaction at the final anchor.
 */
public final class Parkour {

    private final String name;
    private String world;
    private final List<ParkourAnchor> anchors = new ArrayList<>();
    private final EnumSet<MobilityActionSlot> rewards =
            EnumSet.noneOf(MobilityActionSlot.class);

    public Parkour(String name, String world) {
        this.name = name;
        this.world = world;
    }

    public String name() { return name; }

    public String world() { return world; }
    public void setWorld(String world) { this.world = world; }

    public List<ParkourAnchor> anchors() { return anchors; }
    public int size() { return anchors.size(); }

    /** Abilities granted on completion (mutable — the editor edits this set). */
    public EnumSet<MobilityActionSlot> rewards() { return rewards; }

    /** A course needs a start + at least one reaction station to be runnable. */
    public boolean runnable() { return anchors.size() >= 2; }

    /** Deep copy — lets the editor modify a saved course without mutating it
     *  until the editor commits on finish (cancel reverts cleanly). */
    public Parkour copy() {
        Parkour c = new Parkour(name, world);
        for (ParkourAnchor a : anchors) c.anchors.add(a.copy());
        c.rewards.addAll(rewards);
        return c;
    }
}
