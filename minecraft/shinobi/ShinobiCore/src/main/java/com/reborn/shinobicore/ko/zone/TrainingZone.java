package com.reborn.shinobicore.ko.zone;

import org.bukkit.Location;

import java.util.Locale;

/**
 * Zone RP : un pavé de blocs (bornes incluses) dans un monde.
 * <ul>
 *   <li>{@link Kind#ENTRAINEMENT} — combat sans séquelles, voir {@link TrainingZones} ;</li>
 *   <li>{@link Kind#REPOS} — restaurant, onsen, auberge : s'y asseoir ou s'y
 *       allonger fait progresser le repos qui lève l'ATA (voir {@code AtaManager}) ;</li>
 *   <li>{@link Kind#HOPITAL} — zone de repos où se réveillent les blessés rapatriés de son
 *       {@code village}, sur l'un de ses lits ({@code HospitalBeds}).</li>
 * </ul>
 */
public record TrainingZone(String id, Kind kind, String world,
                           int minX, int minY, int minZ,
                           int maxX, int maxY, int maxZ, String village) {

    public enum Kind {
        ENTRAINEMENT, REPOS, HOPITAL;

        public String label() { return name().toLowerCase(Locale.ROOT); }

        public static Kind parse(String s) {
            if (s == null) return null;
            for (Kind k : values()) if (k.label().equalsIgnoreCase(s)) return k;
            return null;
        }
    }

    /** Construit la zone à partir de deux coins dans n'importe quel ordre. */
    public static TrainingZone of(String id, Kind kind, Location a, Location b) {
        return of(id, kind, a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), "");
    }

    /** Zone à partir de deux coins quelconques (bornes remises dans l'ordre). */
    public static TrainingZone of(String id, Kind kind, String world, int x1, int y1, int z1,
                                  int x2, int y2, int z2, String village) {
        return new TrainingZone(id, kind, world,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), village == null ? "" : village);
    }

    /** Une zone de repos au sens de l'ATA (repos ou hôpital). */
    public boolean restful() { return kind == Kind.REPOS || kind == Kind.HOPITAL; }

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
