package com.reborn.shinobicore.character;

/**
 * Affinity multipliers scale the stat matching the character's affinity.
 * Brackets (per spec):
 *
 * <pre>
 *   Level 1-5    x1.10
 *   Level 6-10   x1.15
 *   Level 11-15  x1.20    (Strength: 1.175)
 *   Level 16+    x1.40    (Strength: 1.20)
 * </pre>
 *
 * Strength (HP) is slightly less steep than Intelligence (Chakra) and Agility
 * (Speed) at the top tier — this matches the numbers you specified.
 */
public final class AffinityMultipliers {

    private AffinityMultipliers() {}

    public static double multiplier(Affinity affinity, int level) {
        int lvl = LevelTable.clampLevel(level);
        return switch (affinity) {
            case STRENGTH      -> strengthMultiplier(lvl);
            case INTELLIGENCE  -> intelligenceMultiplier(lvl);
            case AGILITY       -> agilityMultiplier(lvl);
        };
    }

    public static double strengthMultiplier(int level) {
        if (level <= 5)  return 1.10;
        if (level <= 10) return 1.15;
        if (level <= 15) return 1.175;
        return 1.20;
    }

    public static double intelligenceMultiplier(int level) {
        if (level <= 5)  return 1.10;
        if (level <= 10) return 1.15;
        if (level <= 15) return 1.20;
        return 1.40;
    }

    public static double agilityMultiplier(int level) {
        if (level <= 5)  return 1.10;
        if (level <= 10) return 1.15;
        if (level <= 15) return 1.20;
        return 1.40;
    }
}
