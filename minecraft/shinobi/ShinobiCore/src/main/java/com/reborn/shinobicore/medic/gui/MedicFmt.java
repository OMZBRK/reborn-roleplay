package com.reborn.shinobicore.medic.gui;

import com.reborn.shinobicore.ko.injury.Severity;
import org.bukkit.Material;

/**
 * Shared presentation helpers for the medic screens — severity →
 * icon material, and the French remaining-cooldown wording (mirrors
 * {@code TreatmentApplier} so chat + GUI use the same phrasing).
 * Was duplicated across the two legacy GUIs; consolidated with the
 * framework migration.
 */
public final class MedicFmt {

    private MedicFmt() {}

    public static Material materialFor(Severity s) {
        return switch (s) {
            case URGENT    -> Material.RED_DYE;
            case IMPORTANT -> Material.GOLD_NUGGET;
            case MOYEN     -> Material.YELLOW_DYE;
            case FAIBLE    -> Material.GREEN_DYE;
        };
    }

    /** "3 h 22 min" / "27 min" / "14 s". */
    public static String formatRemaining(long millis) {
        long secs = millis / 1000L;
        if (secs >= 3600) {
            long h  = secs / 3600;
            long mm = (secs % 3600) / 60;
            return mm == 0 ? h + " h" : h + " h " + mm + " min";
        }
        if (secs >= 60) {
            long mm = secs / 60;
            long ss = secs % 60;
            return ss == 0 ? mm + " min" : mm + " min " + ss + " s";
        }
        return secs + " s";
    }
}
