package com.reborn.shinobicore.character;

/**
 * Base (pre-affinity) HP and Chakra per level, 1 through {@link #MAX_LEVEL}.
 *
 * Curve shape matches the spec's anchors:
 * <pre>
 *   L1  :   100 HP /     300 chakra
 *   L2  :   125 HP /     600 chakra
 *   L3  :   150 HP /     900 chakra
 *   L4  :   300 HP /   1 800 chakra   (big-slope level)
 *   ...
 *   L17 : 2 000 HP / 100 000 chakra   (capstone)
 * </pre>
 *
 * Inside each 3-level tier the slope is small (+25 HP / +300 Chakra). Every
 * third level (4, 7, 10, 13, 16) jumps to a new tier. Level 17 gets an extra
 * capstone bump to land on the declared endpoint.
 */
public final class LevelTable {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 17;

    /** index 0 = level 1, index 16 = level 17. */
    private static final double[] HP = {
            100, 125, 150,           // L1-L3
            300, 325, 350,           // L4-L6   (big slope at L4)
            500, 525, 550,           // L7-L9
            800, 825, 850,           // L10-L12
            1200, 1225, 1250,        // L13-L15
            1700,                    // L16
            2000                     // L17 capstone
    };

    private static final double[] CHAKRA = {
            300, 600, 900,           // L1-L3
            1800, 2100, 2400,        // L4-L6
            5000, 5300, 5600,        // L7-L9
            12000, 12300, 12600,     // L10-L12
            30000, 30300, 30600,     // L13-L15
            60000,                   // L16
            100000                   // L17 capstone
    };

    private LevelTable() {}

    public static int clampLevel(int level) {
        if (level < MIN_LEVEL) return MIN_LEVEL;
        if (level > MAX_LEVEL) return MAX_LEVEL;
        return level;
    }

    public static double baseHp(int level) {
        return HP[clampLevel(level) - 1];
    }

    public static double baseChakra(int level) {
        return CHAKRA[clampLevel(level) - 1];
    }
}
