package com.reborn.shinobiabilities.mobility;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Per-character on/off switches exposed through {@code /toggle}.
 * Default: everything enabled; the store persists only the DISABLED
 * set. (ShinobiCore's legacy enum was removed with the old mobility
 * system — this plugin owns its own storage now.)
 */
public enum MobilityActionSlot {
    NARUTO_RUN("Course Shinobi", Material.FEATHER,
            "Tape Accroupi pour activer la course."),
    DOUBLE_JUMP("Double Saut", Material.RABBIT_FOOT,
            "Espace en plein vol pour rebondir."),
    WALL_JUMP("Saut Mural", Material.SCAFFOLDING,
            "Espace contre un mur pour rebondir."),
    FLOOR_SHOCKWAVE("Onde de Choc", Material.ANVIL,
            "Accroupi 3s en l'air pour t'écraser au sol."),
    DASH("Dash Shinobi", Material.SUGAR,
            "Maintiens Accroupi au sol puis relâche."),
    CLIMB("Escalade", Material.LADDER,
            "Accroupi en l'air contre un mur pour t'agripper.");

    private final String displayName;
    private final Material icon;
    private final String hint;

    MobilityActionSlot(String displayName, Material icon, String hint) {
        this.displayName = displayName;
        this.icon = icon;
        this.hint = hint;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String hint() { return hint; }

    public static MobilityActionSlot from(String s) {
        if (s == null) return null;
        try { return MobilityActionSlot.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignore) { return null; }
    }
}
