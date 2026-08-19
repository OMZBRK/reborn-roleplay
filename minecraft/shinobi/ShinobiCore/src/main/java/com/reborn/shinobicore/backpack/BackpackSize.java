package com.reborn.shinobicore.backpack;

/**
 * Backpack size tier — determines capacity (slot count) and the chest
 * GUI layout used when the backpack contents are opened.
 *
 * <h2>Sizes</h2>
 * <ul>
 *   <li>{@link #SMALL} — 18 slots ({@code Sac}). Two-row chest GUI.</li>
 *   <li>{@link #LARGE} — 36 slots ({@code Sac Large}). Four-row chest GUI.</li>
 * </ul>
 *
 * <p>Capacity is multiples of 9 so the backpack chest GUI maps 1:1 onto
 * vanilla chest sizes — no padding rows, no awkward slot math.
 */
public enum BackpackSize {
    SMALL(18, "Sac",        "sac"),
    LARGE(36, "Sac Large",  "sac_large");

    private final int slots;
    private final String displayName;
    private final String configKey;

    BackpackSize(int slots, String displayName, String configKey) {
        this.slots = slots;
        this.displayName = displayName;
        this.configKey = configKey;
    }

    public int slots()         { return slots; }
    public String displayName(){ return displayName; }
    public String configKey()  { return configKey; }

    /** Number of chest GUI rows needed for this size. */
    public int rows() { return slots / 9; }

    /** Lenient parse — matches enum name, display name, or config key. */
    public static BackpackSize parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        for (BackpackSize sz : values()) {
            if (sz.name().equalsIgnoreCase(s)) return sz;
            if (sz.displayName.equalsIgnoreCase(raw.trim())) return sz;
            if (sz.configKey.equalsIgnoreCase(s)) return sz;
        }
        // "large" / "petit" / "small" friendly aliases.
        if (s.equals("large") || s.equals("grand") || s.equals("xl")) return LARGE;
        if (s.equals("small") || s.equals("petit") || s.equals("normal")) return SMALL;
        return null;
    }
}
