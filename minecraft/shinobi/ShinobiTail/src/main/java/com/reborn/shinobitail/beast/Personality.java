package com.reborn.shinobitail.beast;

import org.bukkit.configuration.ConfigurationSection;

/**
 * A personality archetype shared between beasts (beasts.yml →
 * {@code personalities}). Pure data; biases are flat percentage points
 * added to the relevant chance rolls, the multiplier scales passive
 * rage growth.
 */
public record Personality(String id,
                          double rageMultiplier,
                          double helpBias,
                          double takeoverBias,
                          double resistBias) {

    public static final Personality NEUTRAL =
            new Personality("neutral", 1.0, 0.0, 0.0, 0.0);

    public static Personality load(String id, ConfigurationSection sec) {
        if (sec == null) return NEUTRAL;
        return new Personality(
                id,
                sec.getDouble("rage-multiplier", 1.0),
                sec.getDouble("help-bias", 0.0),
                sec.getDouble("takeover-bias", 0.0),
                sec.getDouble("resist-bias", 0.0));
    }
}
