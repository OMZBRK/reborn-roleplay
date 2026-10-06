package com.reborn.shinobicore.ko.zone;

import org.bukkit.Location;

import java.util.Locale;

/**
 * Zone RP : un pavé de blocs (bornes incluses) dans un monde.
 * <ul>
 *   <li>{@link Kind#ENTRAINEMENT} — combat sans séquelles, voir {@link TrainingZones} ;</li>
 *   <li>{@link Kind#REPOS} — restaurant, onsen, auberge, hôpital : s'y asseoir ou s'y
 *       allonger fait progresser le repos qui lève l'ATA (voir {@code AtaManager}).</li>
 * </ul>
 */
public record TrainingZone(String id, Kind kind, String world,
                           int minX, int minY, int minZ,
                           int maxX, int maxY, int maxZ) {

    public enum Kind {
        ENTRAINEMENT, REPOS;

        public String label() { return name().toLowerCase(Locale.ROOT); }

        public static Kind parse(String s) {
            if (s == null) return null;
            for (Kind k : values()) if (k.label().equalsIgnoreCase(s)) return k;
            return null;
        }
    }

    /** Construit la zone à partir de deux coins dans n'importe quel ordre. */
    public static TrainingZone of(String id, Kind kind, Location a, Location b) {
        return new TrainingZone(id, kind, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()),
                Math.min(a.getBlockY(), b.getBlockY()),
                Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()),
                Math.max(a.getBlockY(), b.getBlockY()),
                Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public String describe() {
        return id + " §8[" + kind.label() + "] §7(" + world + " " + minX + "," + minY + "," + minZ
                + " → " + maxX + "," + maxY + "," + maxZ + ")";
    }
}
