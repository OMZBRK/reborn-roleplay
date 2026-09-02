package com.reborn.shinobicore.ko.injury;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.scheduler.BukkitTask;

/**
 * Vestigial helper kept for binary compatibility.
 *
 * <p>Originally hosted the time-based auto-decay ticker that
 * downgraded soft + medium injuries on its own clock. The design
 * later moved to "{@code /soigner} is the only thing that clears
 * injuries" — see {@link com.reborn.shinobicore.medic.TreatmentApplier}
 * — so the ticker is no longer scheduled and the apply-iryō shortcut
 * is a no-op.
 *
 * <p>The class is still constructed in {@link ShinobiCore#onEnable()}
 * + saved in a field so external callers (and any hooks I haven't
 * spotted yet) keep linking. If we want to remove it entirely we'd
 * also need to drop the field + accessor in {@code ShinobiCore}.
 */
public final class InjuryHealer {

    private final ShinobiCore plugin;
    private BukkitTask task;

    public InjuryHealer(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** No-op — the ticker is intentionally never scheduled. Cancels
     *  any orphan task from a hot-reload, just to be defensive. */
    public void start() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    /** No-op kept for binary compat. Injuries are cleared only by
     *  {@code /soigner}. */
    @Deprecated
    public Injury applyIryo(ShinobiCharacter character) {
        return null;
    }
}
