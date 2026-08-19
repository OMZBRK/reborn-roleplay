package com.reborn.shinobiabilities.mobility.training;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * One station in a training parkour: a world position plus the reaction
 * mini-game parameters played when the runner lands on it. On a successful
 * reaction the runner leaps to the next anchor; on a miss/timeout the leap is
 * too weak and they fall off.
 */
public final class ParkourAnchor {

    /** The key the player presses to "stop" the sweeping cursor. */
    public enum Key {
        SNEAK, SPACE, LEFT_CLICK;
        public static Key from(String s) {
            if (s == null) return LEFT_CLICK;
            try { return Key.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { return LEFT_CLICK; }
        }
        public String pretty() {
            return switch (this) {
                case SNEAK -> "Accroupi";
                case SPACE -> "Saut (Espace)";
                case LEFT_CLICK -> "Clic gauche";
            };
        }
    }

    /** Where the success window sits on the timing bar. */
    public enum Zone {
        RANDOM, LEFT, MIDDLE, RIGHT;
        public static Zone from(String s) {
            if (s == null) return MIDDLE;
            try { return Zone.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { return MIDDLE; }
        }
        public String pretty() {
            return switch (this) {
                case RANDOM -> "Aléatoire";
                case LEFT -> "Gauche";
                case MIDDLE -> "Milieu";
                case RIGHT -> "Droite";
            };
        }
    }

    private double x, y, z;
    private Key key = Key.LEFT_CLICK;
    private Zone zone = Zone.MIDDLE;
    /** How many full cursor sweeps before the reaction auto-fails. */
    private int loops = 3;
    /** Ticks for one full sweep of the bar (lower = faster). */
    private int loopTicks = 30;

    public ParkourAnchor(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }

    public ParkourAnchor(double x, double y, double z,
                         Key key, Zone zone, int loops, int loopTicks) {
        this.x = x; this.y = y; this.z = z;
        this.key = key; this.zone = zone;
        this.loops = Math.max(1, loops);
        this.loopTicks = Math.max(4, loopTicks);
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }

    public Key key() { return key; }
    public void setKey(Key key) { this.key = key; }

    public Zone zone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }

    public int loops() { return loops; }
    public void setLoops(int loops) { this.loops = Math.max(1, loops); }

    public int loopTicks() { return loopTicks; }
    public void setLoopTicks(int loopTicks) { this.loopTicks = Math.max(4, loopTicks); }

    public Vector toVector() { return new Vector(x, y, z); }
    public Location toLocation(World world) { return new Location(world, x, y, z); }

    /** Center-of-block landing target (anchors are stored at block-ish coords). */
    public Location standLocation(World world) {
        return new Location(world, x, y, z);
    }

    /** Deep copy (so the editor can edit without touching the saved anchor). */
    public ParkourAnchor copy() {
        return new ParkourAnchor(x, y, z, key, zone, loops, loopTicks);
    }
}
